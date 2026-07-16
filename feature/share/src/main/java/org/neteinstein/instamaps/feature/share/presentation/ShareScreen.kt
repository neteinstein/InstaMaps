package org.neteinstein.instamaps.feature.share.presentation

import android.Manifest
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.neteinstein.instamaps.core.designsystem.component.PrimaryButton
import org.neteinstein.instamaps.core.designsystem.component.WarningBanner
import org.neteinstein.instamaps.core.designsystem.theme.MapsGreen
import org.neteinstein.instamaps.feature.maps.MapsLauncher
import org.neteinstein.instamaps.feature.maps.domain.MapsDestination
import org.neteinstein.instamaps.feature.share.R

/**
 * Stateful entry point: parses the incoming share text once ready, then renders [ShareScreen]
 * driven by [ShareViewModel]'s [ShareUiState]. [sharedText] is `null` for a plain launcher open;
 * either way, this always renders (never bypasses) so main-screen readiness warnings - missing
 * API key, missing permissions - are computed and shown in one place regardless of how the
 * screen was reached. Processing only starts once [isReady]: a video shared while something is
 * missing leaves the main screen showing those warnings instead of starting the pipeline, and
 * automatically proceeds the moment the user resolves them (grants the permission, saves a key).
 */
@Composable
fun ShareRoute(
    sharedText: String?,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShareViewModel = koinViewModel(),
    mapsLauncher: MapsLauncher = koinInject(),
) {
    val hasPlacesApiKey by viewModel.hasPlacesApiKey.collectAsStateWithLifecycle()
    val permissionStates = requiredRuntimePermissions().map { rememberRuntimePermissionState(it) }
    val missingPermissions = permissionStates.filter { it.status != RuntimePermissionStatus.GRANTED }
    val isReady = hasPlacesApiKey == true && missingPermissions.isEmpty()

    LaunchedEffect(sharedText, isReady) {
        if (sharedText != null && isReady) {
            viewModel.onSharedTextReceived(sharedText)
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShareScreen(
        uiState = uiState,
        onOpenMaps = { mapsLauncher.launch(it) },
        showApiKeyWarning = hasPlacesApiKey == false,
        missingPermissions = missingPermissions,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
    )
}

/**
 * Stateless, preview/test-friendly screen. Crossfades+scales between the 5 [ShareUiState] shapes;
 * [contentKey] is keyed on the state's class (not the full data class) so [ShareUiState.Processing]
 * updating its `stage` field recomposes [ProcessingContent] in place rather than replaying the
 * whole-screen transition on every pipeline progress tick - only the stage icon/label/dots animate.
 *
 * When [uiState] becomes [ShareUiState.Found], automatically opens Google Maps after a short delay
 * so the user gets to see the "found it" moment before being handed off - the button remains as an
 * immediate/manual alternative.
 *
 * [showApiKeyWarning]/[missingPermissions]/[onOpenSettings] only affect the [ShareUiState.Idle]
 * branch - the main screen - since that's the only state a user can be looking at while
 * something required is missing (processing/found/etc. only happen once [ShareRoute] has already
 * gated on readiness).
 */
@Composable
fun ShareScreen(
    uiState: ShareUiState,
    onOpenMaps: (MapsDestination) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    showApiKeyWarning: Boolean = false,
    missingPermissions: List<RuntimePermissionState> = emptyList(),
) {
    LaunchedEffect(uiState) {
        if (uiState is ShareUiState.Found) {
            delay(AUTO_OPEN_MAPS_DELAY_MS)
            onOpenMaps(uiState.destination)
        }
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = uiState,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                (fadeIn(tween(400)) + scaleIn(initialScale = 0.94f, animationSpec = tween(400)))
                    .togetherWith(fadeOut(tween(200)))
            },
            contentAlignment = Alignment.Center,
            contentKey = { it::class },
            label = "share_ui_state",
        ) { state ->
            when (state) {
                is ShareUiState.Idle ->
                    IdleContent(
                        showApiKeyWarning = showApiKeyWarning,
                        missingPermissions = missingPermissions,
                        onOpenSettings = onOpenSettings,
                    )
                is ShareUiState.Processing -> ProcessingContent(stage = state.stage)
                is ShareUiState.Found ->
                    FoundContent(
                        displayName = state.displayName,
                        onOpenMaps = { onOpenMaps(state.destination) },
                    )
                is ShareUiState.NotFound ->
                    ResultMessageContent(
                        icon = Icons.Default.Place,
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = stringResource(R.string.share_not_found_title),
                        message = state.message,
                    )
                is ShareUiState.Error ->
                    ResultMessageContent(
                        icon = Icons.Default.Warning,
                        iconTint = MaterialTheme.colorScheme.error,
                        title = stringResource(R.string.share_error_title),
                        message = state.message,
                    )
            }
        }
    }
}

@Composable
private fun IdleContent(
    showApiKeyWarning: Boolean,
    missingPermissions: List<RuntimePermissionState>,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        CenteredContent(Modifier.fillMaxSize()) {
            IconBadge(icon = Icons.Default.Place, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.share_idle_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.share_idle_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (showApiKeyWarning || missingPermissions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (showApiKeyWarning) {
                        WarningBanner(
                            message = stringResource(R.string.share_warning_missing_api_key),
                            actionLabel = stringResource(R.string.share_warning_open_settings),
                            onActionClick = onOpenSettings,
                        )
                    }
                    missingPermissions.forEach { permissionState ->
                        WarningBanner(
                            message = permissionWarningMessage(permissionState.permission),
                            actionLabel = permissionActionLabel(permissionState.status),
                            onActionClick = permissionState.requestOrOpenSettings,
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.share_settings_button),
            )
        }
    }
}

@Composable
private fun permissionWarningMessage(permission: String): String =
    when (permission) {
        Manifest.permission.POST_NOTIFICATIONS -> stringResource(R.string.share_warning_missing_notifications_permission)
        else -> stringResource(R.string.share_warning_missing_permission_generic)
    }

@Composable
private fun permissionActionLabel(status: RuntimePermissionStatus): String =
    if (status == RuntimePermissionStatus.PERMANENTLY_DENIED) {
        stringResource(R.string.share_warning_open_app_settings)
    } else {
        stringResource(R.string.share_warning_grant_permission)
    }

@Composable
private fun ProcessingContent(
    stage: ProcessingStage,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pin_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.12f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulse_scale",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulse_alpha",
    )

    CenteredContent(modifier) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(140.dp), strokeWidth = 3.dp)
            Box(
                modifier =
                    Modifier
                        .size(96.dp)
                        .scale(pulseScale)
                        .alpha(pulseAlpha)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = stage == ProcessingStage.GEOCODING,
                    transitionSpec = { fadeIn(tween(300)).togetherWith(fadeOut(tween(150))) },
                    label = "stage_icon",
                ) { isSearching ->
                    Icon(
                        imageVector = if (isSearching) Icons.Default.Search else Icons.Default.Place,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                (fadeIn(tween(300)) + scaleIn(initialScale = 0.9f, animationSpec = tween(300)))
                    .togetherWith(fadeOut(tween(150)))
            },
            label = "stage_label",
        ) { animatedStage ->
            Text(
                text = stageLabel(animatedStage),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        StageDots(currentStage = stage)
    }
}

@Composable
private fun FoundContent(
    displayName: String,
    onOpenMaps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var badgeVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Let the pin "land" first, then pop the success badge in a beat later.
        delay(150)
        badgeVisible = true
    }
    val badgeScale by animateFloatAsState(
        targetValue = if (badgeVisible) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "badge_scale",
    )

    CenteredContent(modifier) {
        Box(contentAlignment = Alignment.BottomEnd) {
            IconBadge(icon = Icons.Default.Place, tint = MapsGreen)
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MapsGreen,
                modifier =
                    Modifier
                        .size(28.dp)
                        .scale(badgeScale)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.share_notification_found_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = displayName,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))
        PrimaryButton(text = stringResource(R.string.share_found_open_maps), onClick = onOpenMaps)
    }
}

@Composable
private fun ResultMessageContent(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    CenteredContent(modifier) {
        IconBadge(icon = icon, tint = iconTint)
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(96.dp).background(tint.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = tint)
    }
}

@Composable
private fun StageDots(
    currentStage: ProcessingStage,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ProcessingStage.entries.forEach { stage ->
            val isActive = stage.ordinal <= currentStage.ordinal
            val size by animateDpAsState(targetValue = if (isActive) 10.dp else 7.dp, label = "dot_size")
            val color by animateColorAsState(
                targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                label = "dot_color",
            )
            Box(modifier = Modifier.size(size).background(color, CircleShape))
        }
    }
}

@Composable
private fun CenteredContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
private fun stageLabel(stage: ProcessingStage): String =
    when (stage) {
        ProcessingStage.CHECKING_DESCRIPTION -> stringResource(R.string.share_stage_checking_description)
        ProcessingStage.DOWNLOADING -> stringResource(R.string.share_stage_downloading)
        ProcessingStage.EXTRACTING_FRAMES -> stringResource(R.string.share_stage_extracting_frames)
        ProcessingStage.ANALYZING_FRAME -> stringResource(R.string.share_stage_analyzing_frame)
        ProcessingStage.GEOCODING -> stringResource(R.string.share_stage_geocoding)
    }

private const val AUTO_OPEN_MAPS_DELAY_MS = 1300L
