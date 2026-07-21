package org.neteinstein.instamaps.feature.share.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import org.neteinstein.instamaps.feature.geocoding.domain.ResolvedLocation
import org.neteinstein.instamaps.feature.share.R

/**
 * Owns every notification [org.neteinstein.instamaps.feature.share.work.ProcessSharedUrlWorker]
 * posts: the ongoing "processing" foreground notification required for expedited work, and the
 * terminal result notification (found / not found / failed) whose tap deep-links back into the
 * app via [ShareDeepLink] so a `MainActivity` trampoline can hand off to `MapsLauncher`.
 */
class ShareNotifier(
    private val context: Context,
) {
    fun buildProcessingForegroundInfo(): ForegroundInfo {
        ensureChannel()
        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.share_notification_processing_title))
                .setSmallIcon(R.drawable.ic_notification_place)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(0, 0, true)
                .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PROCESSING_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(PROCESSING_NOTIFICATION_ID, notification)
        }
    }

    /**
     * Tapping the notification trampolines straight into Maps for [locations]' top-ranked pick,
     * even when Gemini returned more than one candidate: rebuilding a full in-app picker from a
     * cold, killed-process start (the scenario this notification exists for in the first place -
     * see the class doc) would need its own navigation/state-restoration plumbing for what's a
     * rare edge case, whereas the in-app results screen (still shown if the app is/was in the
     * foreground) always lists every candidate. The notification text says so explicitly when
     * there's more than one, so nothing is silently hidden from the user.
     */
    fun notifyFound(locations: List<ResolvedLocation>) {
        val topPick = locations.firstOrNull() ?: return
        val intent =
            Intent(ShareDeepLink.ACTION_OPEN_MAPS_DESTINATION).apply {
                setPackage(context.packageName)
                putExtra(ShareDeepLink.EXTRA_MAPS_QUERY, topPick.destination.query)
                putExtra(ShareDeepLink.EXTRA_PLACE_ID, topPick.destination.placeId)
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                RESULT_NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val extraCount = locations.size - 1
        val text =
            if (extraCount > 0) {
                context.getString(R.string.share_notification_found_multiple_text, topPick.name, extraCount)
            } else {
                topPick.name
            }

        postResultNotification(
            title = context.getString(R.string.share_notification_found_title),
            text = text,
            pendingIntent = pendingIntent,
        )
    }

    fun notifyNotFound(message: String) {
        postResultNotification(
            title = context.getString(R.string.share_notification_not_found_title),
            text = message,
            pendingIntent = null,
        )
    }

    fun notifyFailed(message: String) {
        postResultNotification(
            title = context.getString(R.string.share_notification_failed_title),
            text = message,
            pendingIntent = null,
        )
    }

    private fun postResultNotification(
        title: String,
        text: String,
        pendingIntent: PendingIntent?,
    ) {
        if (!hasNotificationPermission()) return

        ensureChannel()
        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_notification_place)
                .setAutoCancel(true)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .apply { pendingIntent?.let(::setContentIntent) }
                .build()

        NotificationManagerCompat.from(context).notify(RESULT_NOTIFICATION_ID, notification)
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel() {
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.share_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "share_processing"
        const val PROCESSING_NOTIFICATION_ID = 1001
        const val RESULT_NOTIFICATION_ID = 1002
    }
}
