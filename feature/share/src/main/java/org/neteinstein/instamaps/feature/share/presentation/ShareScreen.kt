package org.neteinstein.instamaps.feature.share.presentation

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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.neteinstein.instamaps.core.designsystem.component.BannerTone
import org.neteinstein.instamaps.core.designsystem.component.PrimaryButton
import org.neteinstein.instamaps.core.designsystem.component.WarningBanner
import org.neteinstein.instamaps.core.designsystem.theme.MapsGreen
import org.neteinstein.instamaps.feature.geocoding.domain.ResolvedLocation
import org.neteinstein.instamaps.feature.maps.MapsLauncher
import org.neteinstein.instamaps.feature.maps.domain.MapsDestination
import org.neteinstein.instamaps.feature.share.R

/**
 * Stateful entry point: parses the incoming share text once composed, then renders [ShareScreen]
 * driven by [ShareViewModel]'s [ShareUiState]. [sharedText] is `null` for a plain launcher open.
 * Unlike earlier revisions, this composable no longer gates on API-key/permission readiness
 * itself - `MainActivity` only composes [ShareRoute] once `rememberAppReadiness().isReady` is
 * true (see `feature:permissions`'s `PermissionsScreen`, which is what's shown instead while
 * something's missing), so by the time this runs it can always start processing immediately.
 *
 * Instagram login is a separate, deliberately optional concern - unlike the API key/permissions,
 * it's a reliability boost (see `YtDlpVideoDownloadRepository`), not a hard requirement, so it's
 * never part of the app-wide readiness gate. A missing session only shows as a dismissible nudge
 * on this idle screen, and only actually interrupts a share reactively, if yt-dlp itself reports
 * the specific video needs a login (see [ShareUiState.AuthRequired]).
 */
@Composable
fun ShareRoute(
    sharedText: String?,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onNeedsInstagramLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShareViewModel = koinViewModel(),
    mapsLauncher: MapsLauncher = koinInject(),
) {
    val isInstagramAuthenticated by viewModel.isInstagramAuthenticated.collectAsStateWithLifecycle()

    LaunchedEffect(sharedText) {
        if (sharedText != null) {
            viewModel.onSharedTextReceived(sharedText)
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShareScreen(
        uiState = uiState,
        onOpenMaps = { mapsLauncher.launch(it) },
        showInstagramConnectWarning = isInstagramAuthenticated == false,
        onOpenSettings = onOpenSettings,
        onOpenHistory = onOpenHistory,
        onNeedsInstagramLogin = onNeedsInstagramLogin,
        onDismissAuthRequired = viewModel::dismissAuthRequired,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

/**
 * Stateless, preview/test-friendly screen. Crossfades+scales between the 6 [ShareUiState] shapes;
 * [contentKey] is keyed on the state's class (not the full data class) so [ShareUiState.Processing]
 * updating its `stage` field recomposes [ProcessingContent] in place rather than replaying the
 * whole-screen transition on every pipeline progress tick - only the stage icon/label/dots animate.
 *
 * [ShareUiState.Found] never auto-opens Google Maps: Gemini can return more than one candidate
 * place (see [ResolvedLocation]), so [FoundContent] always lists every one of them and leaves the
 * choice - and the CTA tap that actually launches Maps - to the user, even when there's only one.
 *
 * [showInstagramConnectWarning]/[onOpenSettings]/[onOpenHistory] only affect the [ShareUiState.Idle]
 * branch - the main screen. [ShareUiState.AuthRequired] uses [onNeedsInstagramLogin] too, but
 * reactively - see [ShareRoute]'s doc for why that state exists independently of the Idle nudge.
 * [onRetry] resumes the same video that just failed - offered on [ShareUiState.NotFound] always,
 * and on [ShareUiState.Error] whenever it carries a `url`.
 */
@Composable
fun ShareScreen(
    uiState: ShareUiState,
    onOpenMaps: (MapsDestination) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onNeedsInstagramLogin: () -> Unit,
    modifier: Modifier = Modifier,
    showInstagramConnectWarning: Boolean = false,
    onDismissAuthRequired: () -> Unit = {},
    onRetry: (String) -> Unit = {},
) {
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
                        showInstagramConnectWarning = showInstagramConnectWarning,
                        onOpenSettings = onOpenSettings,
                        onOpenHistory = onOpenHistory,
                        onConnectInstagram = onNeedsInstagramLogin,
                    )
                is ShareUiState.Processing -> ProcessingContent(stage = state.stage)
                is ShareUiState.Found -> FoundContent(locations = state.locations, onOpenMaps = onOpenMaps)
                is ShareUiState.NotFound ->
                    ResultMessageContent(
                        icon = Icons.Default.Place,
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        title = stringResource(R.string.share_not_found_title),
                        message = state.message,
                        onRetry = { onRetry(state.url) },
                    )
                is ShareUiState.AuthRequired ->
                    AuthRequiredContent(
                        message = state.message,
                        onLogin = onNeedsInstagramLogin,
                        onDismiss = onDismissAuthRequired,
                    )
                is ShareUiState.Error ->
                    ResultMessageContent(
                        icon = Icons.Default.Warning,
                        iconTint = MaterialTheme.colorScheme.error,
                        title = stringResource(R.string.share_error_title),
                        message = state.message,
                        onRetry = state.url?.let { url -> { onRetry(url) } },
                    )
            }
        }
    }
}

@Composable
private fun IdleContent(
    showInstagramConnectWarning: Boolean,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onConnectInstagram: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val idleTransition = rememberInfiniteTransition(label = "idle_pulse")
    val idleIconScale by idleTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "idle_icon_scale",
    )

    Box(modifier = modifier.fillMaxSize()) {
        CenteredContent(Modifier.fillMaxSize()) {
            IconBadge(
                icon = Icons.Default.Place,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.scale(idleIconScale),
            )
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

            if (showInstagramConnectWarning) {
                Spacer(modifier = Modifier.height(24.dp))
                WarningBanner(
                    message = stringResource(R.string.share_warning_instagram_not_connected),
                    actionLabel = stringResource(R.string.share_warning_connect_instagram),
                    onActionClick = onConnectInstagram,
                    tone = BannerTone.INFO,
                )
            }
        }

        Row(
            // statusBarsPadding() first: MainActivity's enableEdgeToEdge() draws this Box behind
            // the status bar, so without it these buttons (and their clip/ripple targets) would
            // sit under the status bar/clock instead of just below it.
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
        ) {
            IconButton(onClick = onOpenHistory) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = stringResource(R.string.share_history_button),
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.share_settings_button),
                )
            }
        }
    }
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
    // A tiny, slow wobble on top of the pulse so the processing screen still feels alive during
    // the (sometimes lengthy) download/OCR/geocode wait, rather than just breathing in place.
    val pulseRotation by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(1900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulse_rotation",
    )

    CenteredContent(modifier) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(140.dp), strokeWidth = 3.dp)
            Box(
                modifier =
                    Modifier
                        .size(96.dp)
                        .scale(pulseScale)
                        .rotate(pulseRotation)
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

        Spacer(modifier = Modifier.height(8.dp))
        FlavorLine(stage = stage)

        Spacer(modifier = Modifier.height(24.dp))
        StageDots(currentStage = stage)
    }
}

/**
 * Rotating pool of short, in-context "what it's actually doing" lines shown under the main stage
 * label - the app's take on the same kind of flavor text Copilot's own CLI shows while it works,
 * so the (sometimes lengthy) download/OCR/geocode wait has something to read instead of a static
 * screen. Resets to the first line of the new stage's pool whenever [stage] changes, so a short
 * stage is never caught mid-cycle showing a line from the stage before it.
 */
@Composable
private fun FlavorLine(
    stage: ProcessingStage,
    modifier: Modifier = Modifier,
) {
    val lines = flavorLines(stage)
    var lineIndex by remember(stage) { mutableIntStateOf(0) }

    LaunchedEffect(stage, lines) {
        if (lines.size <= 1) return@LaunchedEffect
        while (true) {
            delay(FLAVOR_LINE_INTERVAL_MS)
            lineIndex = (lineIndex + 1) % lines.size
        }
    }

    AnimatedContent(
        targetState = lines.getOrNull(lineIndex),
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(300)) + slideInVertically(animationSpec = tween(300)) { it / 3 })
                .togetherWith(fadeOut(tween(200)) + slideOutVertically(animationSpec = tween(200)) { -it / 3 })
        },
        label = "flavor_line",
    ) { line ->
        Text(
            text = line.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Lists every place Gemini identified, ranked most-to-least likely (see [ResolvedLocation]),
 * inside a [LazyColumn] so all of them stay reachable by scrolling regardless of how many there
 * are. Nothing here opens Google Maps automatically - not even for a single result - the CTA on
 * [LocationResultCard] is the only way, so the user always confirms which place is right before
 * being handed off.
 */
@Composable
private fun FoundContent(
    locations: List<ResolvedLocation>,
    onOpenMaps: (MapsDestination) -> Unit,
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

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, start = 24.dp, end = 24.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text =
                    if (locations.size > 1) {
                        stringResource(R.string.share_found_multiple_title, locations.size)
                    } else {
                        stringResource(R.string.share_found_title)
                    },
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            if (locations.size > 1) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.share_found_multiple_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(locations) { location ->
                LocationResultCard(location = location, onOpenMaps = { onOpenMaps(location.destination) })
            }
        }
    }
}

/** One candidate place: name, its address (when Gemini provided one), and the CTA to open it. */
@Composable
private fun LocationResultCard(
    location: ResolvedLocation,
    onOpenMaps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = location.name, style = MaterialTheme.typography.titleMedium)
            val address = location.address
            if (!address.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryButton(text = stringResource(R.string.share_found_open_maps), onClick = onOpenMaps)
        }
    }
}

@Composable
private fun ResultMessageContent(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
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
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(32.dp))
            PrimaryButton(text = stringResource(R.string.share_retry_button), onClick = onRetry)
        }
    }
}

/**
 * Shown when [ShareUiState.AuthRequired] - yt-dlp reported Instagram is demanding a (re-)login for
 * the video that's still pending. [onLogin] hands off to `feature:instagramauth`'s WebView screen;
 * [ShareViewModel] auto-resumes this same video the moment that login succeeds, so there's no
 * separate manual "retry" action here. [onDismiss] is the escape hatch for a user who doesn't want
 * to log in right now - it just returns to the idle screen without discarding the saved session.
 */
@Composable
private fun AuthRequiredContent(
    message: String,
    onLogin: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenteredContent(modifier) {
        IconBadge(icon = Icons.Default.Warning, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.share_auth_required_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(32.dp))
        PrimaryButton(text = stringResource(R.string.share_auth_required_login), onClick = onLogin)
        TextButton(onClick = onDismiss) {
            Text(text = stringResource(R.string.share_auth_required_dismiss))
        }
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

/** The [FlavorLine] pool for a given stage - see [share_flavor_lines_checking_description] & co. */
@Composable
private fun flavorLines(stage: ProcessingStage): List<String> =
    when (stage) {
        ProcessingStage.CHECKING_DESCRIPTION ->
            stringArrayResource(R.array.share_flavor_lines_checking_description)
        ProcessingStage.DOWNLOADING -> stringArrayResource(R.array.share_flavor_lines_downloading)
        ProcessingStage.EXTRACTING_FRAMES -> stringArrayResource(R.array.share_flavor_lines_extracting_frames)
        ProcessingStage.ANALYZING_FRAME -> stringArrayResource(R.array.share_flavor_lines_analyzing_frame)
        ProcessingStage.GEOCODING -> stringArrayResource(R.array.share_flavor_lines_geocoding)
    }.toList()

/** How long each [FlavorLine] stays on screen before rotating to the next one in the pool. */
private const val FLAVOR_LINE_INTERVAL_MS = 2200L
