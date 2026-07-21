package org.neteinstein.instamaps.feature.permissions.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.neteinstein.instamaps.core.permissions.RuntimePermissionState
import org.neteinstein.instamaps.core.permissions.RuntimePermissionStatus
import org.neteinstein.instamaps.core.permissions.rememberRuntimePermissionState
import org.neteinstein.instamaps.core.permissions.requiredRuntimePermissions

/**
 * Everything needed to answer "can InstaMaps actually process a shared video right now" in one
 * place: a saved Gemini API key, and every runtime permission [requiredRuntimePermissions] lists.
 * [permissionStates] deliberately holds *every* required permission (not just the missing ones)
 * so the Permissions screen can render a live status per item, not just what's left; [isReady]/
 * [missingPermissions] are what callers that only care about the gate (like `MainActivity`) use.
 */
data class AppReadiness(
    val hasGeminiApiKey: Boolean?,
    val permissionStates: List<RuntimePermissionState>,
) {
    val missingPermissions: List<RuntimePermissionState>
        get() = permissionStates.filter { it.status != RuntimePermissionStatus.GRANTED }

    val isReady: Boolean
        get() = hasGeminiApiKey == true && missingPermissions.isEmpty()
}

/**
 * Computed fresh on every call site's recomposition - including on [androidx.lifecycle.Lifecycle.Event.ON_RESUME]
 * (see [rememberRuntimePermissionState]) - so both `MainActivity`'s Share-vs-Permissions routing
 * decision and the Permissions screen itself always reflect the current, real system/Settings
 * state: granting a permission, saving an API key, or revoking a permission from system Settings
 * and coming back to the app all flow through here without any explicit refresh call.
 */
@Composable
fun rememberAppReadiness(viewModel: PermissionsViewModel = koinViewModel()): AppReadiness {
    val hasGeminiApiKey by viewModel.hasGeminiApiKey.collectAsStateWithLifecycle()
    val permissionStates = requiredRuntimePermissions().map { rememberRuntimePermissionState(it) }
    return AppReadiness(hasGeminiApiKey = hasGeminiApiKey, permissionStates = permissionStates)
}
