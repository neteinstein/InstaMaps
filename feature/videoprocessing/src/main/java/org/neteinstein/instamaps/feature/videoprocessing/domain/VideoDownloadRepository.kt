package org.neteinstein.instamaps.feature.videoprocessing.domain

/**
 * Downloads the video payload behind a shared social media URL. Implementations are expected to
 * cap resolution/quality (see [org.neteinstein.instamaps.feature.videoprocessing.data.YtDlpVideoDownloadRepository])
 * since this app only ever needs the file for on-device text recognition, not playback quality.
 */
interface VideoDownloadRepository {
    suspend fun download(url: String): Result<DownloadedVideo>

    /** Deletes a previously downloaded video's cache directory. Safe to call even if it's already gone. */
    suspend fun delete(video: DownloadedVideo)
}
