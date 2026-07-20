package org.neteinstein.instamaps.feature.share.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.neteinstein.instamaps.core.common.AppError
import org.neteinstein.instamaps.core.instagramauth.domain.ClearInstagramSessionUseCase
import org.neteinstein.instamaps.feature.share.domain.ProcessSharedUrlUseCase
import org.neteinstein.instamaps.feature.share.domain.ShareProcessingProgress

/**
 * Runs [ProcessSharedUrlUseCase] as expedited background work so the pipeline survives the user
 * backgrounding the app right after sharing - the most likely flow, since they were just in
 * Instagram/TikTok. Depends on Koin's global context directly via [KoinComponent] rather than a
 * custom [androidx.work.WorkerFactory]/`Configuration.Provider`: WorkManager's default factory
 * only needs a public `(Context, WorkerParameters)` constructor, and this app always starts Koin
 * before any work can be enqueued, so the global context [inject] pulls from is guaranteed ready.
 */
class ProcessSharedUrlWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {
    private val processSharedUrlUseCase: ProcessSharedUrlUseCase by inject()
    private val notifier: ShareNotifier by inject()
    private val clearInstagramSessionUseCase: ClearInstagramSessionUseCase by inject()

    override suspend fun doWork(): Result {
        val url =
            inputData.getString(KEY_URL)
                ?: return Result.failure(workDataOf(KEY_ERROR_MESSAGE to "Missing shared URL"))

        setForeground(getForegroundInfo())

        return try {
            var latestProgress: ShareProcessingProgress = ShareProcessingProgress.CheckingDescription
            processSharedUrlUseCase(url).collect { progress ->
                latestProgress = progress
                setProgress(progress.toWorkData())
            }
            latestProgress.toWorkResult(url)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            // The pipeline's own steps already translate failures into a terminal
            // ShareProcessingProgress.Failed value (see ExtractLocationCandidatesUseCase), so
            // reaching here means something threw outside that safety net entirely. Still notify
            // rather than let WorkManager silently mark the job failed with no user-visible signal.
            val message = throwable.message ?: "Something went wrong"
            notifier.notifyFailed(message)
            Result.failure(workDataOf(KEY_ERROR_MESSAGE to message))
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = notifier.buildProcessingForegroundInfo()

    private suspend fun ShareProcessingProgress.toWorkResult(url: String): Result =
        when (this) {
            is ShareProcessingProgress.Found -> {
                notifier.notifyFound(destination, displayName)
                Result.success(
                    workDataOf(
                        KEY_MAPS_QUERY to destination.query,
                        KEY_PLACE_ID to destination.placeId,
                        KEY_DISPLAY_NAME to displayName,
                    ),
                )
            }
            is ShareProcessingProgress.NotFound -> {
                notifier.notifyNotFound(message)
                Result.failure(workDataOf(KEY_ERROR_MESSAGE to message, KEY_NOT_FOUND to true))
            }
            is ShareProcessingProgress.Failed -> Result.failure(error.toFailureWorkData(url))
            else -> {
                // The pipeline Flow always terminates in Found/NotFound/Failed - anything else
                // reaching here means it completed without emitting a terminal state.
                val message = "Processing ended unexpectedly"
                notifier.notifyFailed(message)
                Result.failure(workDataOf(KEY_ERROR_MESSAGE to message))
            }
        }

    /**
     * [AppError.AuthenticationRequired] gets its own branch: the stale session is cleared right
     * away (so a retry doesn't just attach the same rejected cookie again) and [KEY_URL] is
     * echoed back so `ShareViewModel` can automatically resume processing the same [url] once the
     * user logs in again - see `ShareUiState.AuthRequired`. Every other failure keeps the existing
     * plain-error shape.
     */
    private suspend fun Throwable.toFailureWorkData(url: String): Data {
        val message = message ?: "Something went wrong"
        return if (this is AppError.AuthenticationRequired) {
            clearInstagramSessionUseCase()
            notifier.notifyFailed(message)
            workDataOf(KEY_ERROR_MESSAGE to message, KEY_AUTH_REQUIRED to true, KEY_URL to url)
        } else {
            notifier.notifyFailed(message)
            workDataOf(KEY_ERROR_MESSAGE to message)
        }
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_STAGE = "stage"
        const val KEY_FRAME_INDEX = "frame_index"
        const val KEY_ERROR_MESSAGE = "error_message"
        const val KEY_NOT_FOUND = "not_found"
        const val KEY_AUTH_REQUIRED = "auth_required"
        const val KEY_MAPS_QUERY = "maps_query"
        const val KEY_PLACE_ID = "place_id"
        const val KEY_DISPLAY_NAME = "display_name"

        const val STAGE_CHECKING_DESCRIPTION = "CHECKING_DESCRIPTION"
        const val STAGE_DOWNLOADING = "DOWNLOADING"
        const val STAGE_EXTRACTING_FRAMES = "EXTRACTING_FRAMES"
        const val STAGE_ANALYZING_FRAME = "ANALYZING_FRAME"
        const val STAGE_GEOCODING = "GEOCODING"

        fun buildRequest(url: String): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<ProcessSharedUrlWorker>()
                .setInputData(workDataOf(KEY_URL to url))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

        /**
         * Enqueued as plain (non-unique) work rather than `enqueueUniqueWork`: each share is
         * independently tracked by its own [OneTimeWorkRequest.getId] (see `ShareViewModel`), so
         * a second share arriving before the first finishes should run concurrently and produce
         * its own notification, not silently replace or queue behind the first.
         */
        fun enqueue(
            context: Context,
            url: String,
        ): OneTimeWorkRequest {
            val request = buildRequest(url)
            WorkManager.getInstance(context).enqueue(request)
            return request
        }
    }
}

private fun ShareProcessingProgress.toWorkData(): Data =
    when (this) {
        is ShareProcessingProgress.CheckingDescription ->
            workDataOf(ProcessSharedUrlWorker.KEY_STAGE to ProcessSharedUrlWorker.STAGE_CHECKING_DESCRIPTION)
        is ShareProcessingProgress.Downloading ->
            workDataOf(ProcessSharedUrlWorker.KEY_STAGE to ProcessSharedUrlWorker.STAGE_DOWNLOADING)
        is ShareProcessingProgress.ExtractingFrames ->
            workDataOf(ProcessSharedUrlWorker.KEY_STAGE to ProcessSharedUrlWorker.STAGE_EXTRACTING_FRAMES)
        is ShareProcessingProgress.AnalyzingFrame ->
            workDataOf(
                ProcessSharedUrlWorker.KEY_STAGE to ProcessSharedUrlWorker.STAGE_ANALYZING_FRAME,
                ProcessSharedUrlWorker.KEY_FRAME_INDEX to frameIndex,
            )
        is ShareProcessingProgress.Geocoding ->
            workDataOf(ProcessSharedUrlWorker.KEY_STAGE to ProcessSharedUrlWorker.STAGE_GEOCODING)
        // Found/NotFound/Failed are terminal - they're reported via the Worker's Result, not
        // setProgress, so there's nothing new to report here beyond the last real stage.
        is ShareProcessingProgress.Found, is ShareProcessingProgress.NotFound, is ShareProcessingProgress.Failed ->
            workDataOf()
    }
