package org.neteinstein.instamaps.feature.settings.presentation

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.neteinstein.instamaps.core.designsystem.component.ButtonTone
import org.neteinstein.instamaps.core.designsystem.component.PrimaryButton
import org.neteinstein.instamaps.core.designsystem.theme.MapsGreen
import org.neteinstein.instamaps.core.permissions.RuntimePermissionState
import org.neteinstein.instamaps.core.permissions.RuntimePermissionStatus
import org.neteinstein.instamaps.core.permissions.rememberRuntimePermissionState
import org.neteinstein.instamaps.core.permissions.requiredRuntimePermissions
import org.neteinstein.instamaps.feature.settings.R

/**
 * Stateful entry point: wires [SettingsViewModel] to [SettingsScreen]. Live runtime permission
 * status (see [rememberRuntimePermissionState]) is collected here rather than through
 * [SettingsUiState] - it's read straight from the Android system/Activity, not from a repository
 * the ViewModel could observe, so it has no business living in the ViewModel's state.
 */
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionStates = requiredRuntimePermissions().map { rememberRuntimePermissionState(it) }
    SettingsScreen(
        uiState = uiState,
        permissionStates = permissionStates,
        onApiKeyChanged = viewModel::onApiKeyChanged,
        onSaveClicked = viewModel::onSaveClicked,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Stateless, preview/test-friendly screen: title bar up top, a scrollable body with the API key
 * field and the Save button side by side in one row (rather than a separate `bottomBar`, since
 * the action is now local to the field it acts on), followed by a live [permissionStates] status
 * list. Save is only enabled once [SettingsUiState.hasUnsavedChanges] is true - i.e. the field's
 * text differs from the last known-persisted value - so there's nothing to tap until the user
 * actually edits the key. Tapping it always persists the key, then checks it against the real
 * Gemini API: the button shows a spinner while that check is in flight
 * ([ApiKeyValidationStatus.VALIDATING]) and settles into green/red
 * ([ApiKeyValidationStatus.VALID]/[ApiKeyValidationStatus.INVALID]) once it resolves - see
 * [SettingsViewModel.onSaveClicked].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onApiKeyChanged: (String) -> Unit,
    onSaveClicked: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    permissionStates: List<RuntimePermissionState> = emptyList(),
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = uiState.apiKeyInput,
                    onValueChange = onApiKeyChanged,
                    modifier = Modifier.weight(1f),
                    label = { Text(text = stringResource(R.string.settings_api_key_label)) },
                    supportingText = {
                        Text(
                            text = apiKeySupportingText(uiState.validationStatus),
                            color = apiKeySupportingTextColor(uiState.validationStatus),
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done),
                )
                PrimaryButton(
                    text = stringResource(R.string.settings_save),
                    onClick = onSaveClicked,
                    enabled = uiState.hasUnsavedChanges,
                    fillWidth = false,
                    loading = uiState.validationStatus == ApiKeyValidationStatus.VALIDATING,
                    tone = apiKeySaveButtonTone(uiState.validationStatus),
                )
            }

            if (permissionStates.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.settings_permissions_section_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    permissionStates.forEach { permissionState ->
                        PermissionStatusRow(permissionState = permissionState)
                    }
                }
            }
        }
    }
}

@Composable
private fun apiKeySupportingText(validationStatus: ApiKeyValidationStatus): String =
    when (validationStatus) {
        ApiKeyValidationStatus.IDLE -> stringResource(R.string.settings_api_key_supporting_text)
        ApiKeyValidationStatus.VALIDATING -> stringResource(R.string.settings_api_key_validating)
        ApiKeyValidationStatus.VALID -> stringResource(R.string.settings_api_key_valid)
        ApiKeyValidationStatus.INVALID -> stringResource(R.string.settings_api_key_invalid)
        ApiKeyValidationStatus.UNKNOWN -> stringResource(R.string.settings_api_key_validation_unknown)
    }

/** [Color.Unspecified] keeps [OutlinedTextField]'s own default supporting-text color. */
@Composable
private fun apiKeySupportingTextColor(validationStatus: ApiKeyValidationStatus): Color =
    when (validationStatus) {
        ApiKeyValidationStatus.VALID -> MapsGreen
        ApiKeyValidationStatus.INVALID -> MaterialTheme.colorScheme.error
        else -> Color.Unspecified
    }

private fun apiKeySaveButtonTone(validationStatus: ApiKeyValidationStatus): ButtonTone =
    when (validationStatus) {
        ApiKeyValidationStatus.VALID -> ButtonTone.SUCCESS
        ApiKeyValidationStatus.INVALID -> ButtonTone.ERROR
        ApiKeyValidationStatus.IDLE, ApiKeyValidationStatus.VALIDATING, ApiKeyValidationStatus.UNKNOWN -> ButtonTone.DEFAULT
    }

/**
 * One permission: its name, live status pill, and - only while unresolved - a small action that
 * either shows the system request dialog or jumps to the app's System Settings page (see
 * [RuntimePermissionState.requestOrOpenSettings]). Deliberately a standalone, simpler copy of
 * `feature:permissions`'s `RequirementCard`/`StatusPill` rather than a shared component: feature
 * modules never depend on each other directly (see `agents.md`), and this screen doesn't need the
 * explanatory copy the onboarding gate shows - just the current status.
 */
@Composable
private fun PermissionStatusRow(
    permissionState: RuntimePermissionState,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = permissionLabel(permissionState.permission),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            PermissionStatusPill(status = permissionState.status)
            if (permissionState.status != RuntimePermissionStatus.GRANTED) {
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = permissionState.requestOrOpenSettings) {
                    Text(text = permissionActionLabel(permissionState.status))
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusPill(
    status: RuntimePermissionStatus,
    modifier: Modifier = Modifier,
) {
    val isGranted = status == RuntimePermissionStatus.GRANTED
    val containerColor = if (isGranted) MapsGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isGranted) MapsGreen else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = permissionStatusLabel(status),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun permissionLabel(permission: String): String =
    when (permission) {
        Manifest.permission.POST_NOTIFICATIONS -> stringResource(R.string.settings_permission_notifications_label)
        else -> stringResource(R.string.settings_permission_generic_label)
    }

@Composable
private fun permissionStatusLabel(status: RuntimePermissionStatus): String =
    when (status) {
        RuntimePermissionStatus.GRANTED -> stringResource(R.string.settings_permission_status_granted)
        RuntimePermissionStatus.DENIED -> stringResource(R.string.settings_permission_status_not_granted)
        RuntimePermissionStatus.PERMANENTLY_DENIED -> stringResource(R.string.settings_permission_status_blocked)
    }

@Composable
private fun permissionActionLabel(status: RuntimePermissionStatus): String =
    if (status == RuntimePermissionStatus.PERMANENTLY_DENIED) {
        stringResource(R.string.settings_permission_open_app_settings_cta)
    } else {
        stringResource(R.string.settings_permission_grant_cta)
    }
