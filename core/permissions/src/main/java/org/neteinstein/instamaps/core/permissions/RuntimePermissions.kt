package org.neteinstein.instamaps.core.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * Runtime (dangerous) permissions InstaMaps needs, gated by API level. Currently just
 * `POST_NOTIFICATIONS` on API 33+ (required to show the result notification - see
 * `ShareNotifier.hasNotificationPermission`); extend this list, not scattered SDK_INT checks
 * elsewhere, as more permissions are needed.
 *
 * Lives in `core:permissions` (not a feature module) so both `feature:permissions`'s onboarding
 * gate and `feature:settings`'s status display can depend on it without depending on each other -
 * feature modules never depend on each other directly, see `agents.md`.
 */
fun requiredRuntimePermissions(): List<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        emptyList()
    }

enum class RuntimePermissionStatus {
    GRANTED,

    /** Not granted, but the system will still show the request dialog if asked again. */
    DENIED,

    /** Not granted and the system won't show the dialog again - only the App Settings page can grant it now. */
    PERMANENTLY_DENIED,
}

data class RuntimePermissionState(
    val permission: String,
    val status: RuntimePermissionStatus,
    val requestOrOpenSettings: () -> Unit,
)

/**
 * Tracks [permission]'s status, re-checking on [Lifecycle.Event.ON_RESUME] so returning from the
 * system permission dialog - or from the App Settings page - updates it without an explicit
 * refresh call. [RuntimePermissionState.requestOrOpenSettings] shows the normal system request
 * dialog, or, once the system will no longer show that dialog after a prior denial, deep-links
 * into the app's system Settings page instead, since that's the only remaining way to grant it.
 */
@Composable
fun rememberRuntimePermissionState(permission: String): RuntimePermissionState {
    val context = LocalContext.current
    var hasRequestedBefore by remember { mutableStateOf(hasRequestedPermissionBefore(context, permission)) }
    var status by remember { mutableStateOf(currentPermissionStatus(context, permission, hasRequestedBefore)) }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            markPermissionRequested(context, permission)
            hasRequestedBefore = true
            status = if (granted) RuntimePermissionStatus.GRANTED else currentPermissionStatus(context, permission, true)
        }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        status = currentPermissionStatus(context, permission, hasRequestedBefore)
    }

    return RuntimePermissionState(
        permission = permission,
        status = status,
        requestOrOpenSettings = {
            if (status == RuntimePermissionStatus.PERMANENTLY_DENIED) {
                context.openAppSettings()
            } else {
                launcher.launch(permission)
            }
        },
    )
}

private fun currentPermissionStatus(
    context: Context,
    permission: String,
    hasRequestedBefore: Boolean,
): RuntimePermissionStatus {
    val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    if (granted) return RuntimePermissionStatus.GRANTED

    val activity = context.findActivity()
    val canShowRationale = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    return if (!hasRequestedBefore || canShowRationale) {
        RuntimePermissionStatus.DENIED
    } else {
        RuntimePermissionStatus.PERMANENTLY_DENIED
    }
}

private fun hasRequestedPermissionBefore(
    context: Context,
    permission: String,
): Boolean = context.permissionRequestPrefs().getBoolean(permission, false)

private fun markPermissionRequested(
    context: Context,
    permission: String,
) {
    context.permissionRequestPrefs().edit { putBoolean(permission, true) }
}

// Deliberately a plain SharedPreferences file, separate from `core:settings`'s DataStore - this
// flag is ephemeral UI-flow bookkeeping ("have we ever asked for this permission"), not a
// user-facing setting, so it has no business being backed up/synced like the Gemini API key.
private fun Context.permissionRequestPrefs() = getSharedPreferences("runtime_permission_requests", Context.MODE_PRIVATE)

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun Context.openAppSettings() {
    val intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
    startActivity(intent)
}
