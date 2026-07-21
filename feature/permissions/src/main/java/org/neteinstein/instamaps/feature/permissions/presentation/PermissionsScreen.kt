package org.neteinstein.instamaps.feature.permissions.presentation

import android.Manifest
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.neteinstein.instamaps.core.designsystem.component.PrimaryButton
import org.neteinstein.instamaps.core.designsystem.theme.MapsGreen
import org.neteinstein.instamaps.core.permissions.RuntimePermissionStatus
import org.neteinstein.instamaps.feature.permissions.R

/**
 * The mandatory gate `MainActivity` shows in place of `feature:share`'s main screen whenever
 * [AppReadiness.isReady] is `false` - on first launch (nothing configured yet), and again any
 * time it regresses (a permission revoked from system Settings, the API key cleared). There is
 * deliberately no back/skip action here: the only way past this screen is to actually resolve
 * every requirement below, at which point `MainActivity`'s own readiness check flips it back to
 * `feature:share`'s `ShareRoute` on its own.
 *
 * The [VideoToMapAnimation] hero doubles as the "what does this app do" explanation the requester
 * asked for: it loops continuously (this screen has no timer of its own - it waits on real user
 * action) rather than playing once, since a user might spend a while here granting things.
 */
@Composable
fun PermissionsScreen(
    readiness: AppReadiness,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
        ) {
            VideoToMapAnimation(modifier = Modifier.fillMaxWidth())

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.permissions_headline),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.permissions_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.permissions_section_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RequirementCard(
                    icon = Icons.Default.Lock,
                    title = stringResource(R.string.permissions_api_key_title),
                    explanation = stringResource(R.string.permissions_api_key_explanation),
                    isSatisfied = readiness.hasGeminiApiKey == true,
                    satisfiedLabel = stringResource(R.string.permissions_api_key_status_added),
                    unsatisfiedLabel = stringResource(R.string.permissions_api_key_status_missing),
                    actionLabel = stringResource(R.string.permissions_api_key_cta),
                    onActionClick = onOpenSettings,
                )

                readiness.permissionStates.forEach { permissionState ->
                    RequirementCard(
                        icon = Icons.Default.Notifications,
                        title = permissionTitle(permissionState.permission),
                        explanation = permissionExplanation(permissionState.permission),
                        isSatisfied = permissionState.status == RuntimePermissionStatus.GRANTED,
                        satisfiedLabel = stringResource(R.string.permissions_status_granted),
                        unsatisfiedLabel = permissionStatusLabel(permissionState.status),
                        actionLabel = permissionActionLabel(permissionState.status),
                        onActionClick = permissionState.requestOrOpenSettings,
                    )
                }
            }
        }
    }
}

/**
 * One requirement: what it is, why InstaMaps needs it (right above the button that resolves it,
 * per the requester's ask), and its live status. The CTA disappears once [isSatisfied] - there's
 * nothing left to do for that item - leaving just a status readout.
 */
@Composable
private fun RequirementCard(
    icon: ImageVector,
    title: String,
    explanation: String,
    isSatisfied: Boolean,
    satisfiedLabel: String,
    unsatisfiedLabel: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                StatusPill(isSatisfied = isSatisfied, satisfiedLabel = satisfiedLabel, unsatisfiedLabel = unsatisfiedLabel)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!isSatisfied) {
                Spacer(modifier = Modifier.height(12.dp))
                PrimaryButton(text = actionLabel, onClick = onActionClick)
            }
        }
    }
}

@Composable
private fun StatusPill(
    isSatisfied: Boolean,
    satisfiedLabel: String,
    unsatisfiedLabel: String,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (isSatisfied) MapsGreen.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isSatisfied) MapsGreen else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = if (isSatisfied) satisfiedLabel else unsatisfiedLabel,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun permissionTitle(permission: String): String =
    when (permission) {
        Manifest.permission.POST_NOTIFICATIONS -> stringResource(R.string.permissions_notifications_title)
        else -> stringResource(R.string.permissions_generic_title)
    }

@Composable
private fun permissionExplanation(permission: String): String =
    when (permission) {
        Manifest.permission.POST_NOTIFICATIONS -> stringResource(R.string.permissions_notifications_explanation)
        else -> stringResource(R.string.permissions_generic_explanation)
    }

@Composable
private fun permissionStatusLabel(status: RuntimePermissionStatus): String =
    when (status) {
        RuntimePermissionStatus.GRANTED -> stringResource(R.string.permissions_status_granted)
        RuntimePermissionStatus.DENIED -> stringResource(R.string.permissions_status_not_granted)
        RuntimePermissionStatus.PERMANENTLY_DENIED -> stringResource(R.string.permissions_status_blocked)
    }

@Composable
private fun permissionActionLabel(status: RuntimePermissionStatus): String =
    if (status == RuntimePermissionStatus.PERMANENTLY_DENIED) {
        stringResource(R.string.permissions_open_app_settings_cta)
    } else {
        stringResource(R.string.permissions_grant_cta)
    }

/**
 * The looping "videos in, map out" hero visual - see [PermissionsScreen]'s doc for why it loops.
 * [cycle] is bumped once per loop and used as a [key] around the animated content: this forces a
 * brand new set of `animate*AsState` instances each time around, which is what makes every loop
 * genuinely restart from the "nothing has arrived yet" [AnimationStage.RESET] look rather than
 * animating backwards from wherever [AnimationStage.HOLDING] left off.
 */
private enum class AnimationStage { RESET, VIDEOS_APPROACHING, ENTERING_BOX, PROCESSING, MAP_EMERGING, HOLDING }

@Composable
private fun VideoToMapAnimation(modifier: Modifier = Modifier) {
    var stage by remember { mutableStateOf(AnimationStage.RESET) }
    var cycle by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            cycle++
            stage = AnimationStage.RESET
            delay(50)
            stage = AnimationStage.VIDEOS_APPROACHING
            delay(900)
            stage = AnimationStage.ENTERING_BOX
            delay(450)
            stage = AnimationStage.PROCESSING
            delay(750)
            stage = AnimationStage.MAP_EMERGING
            delay(600)
            stage = AnimationStage.HOLDING
            delay(1300)
        }
    }

    key(cycle) {
        VideoToMapAnimationContent(stage = stage, modifier = modifier)
    }
}

@Composable
private fun VideoToMapAnimationContent(
    stage: AnimationStage,
    modifier: Modifier = Modifier,
) {
    val chipOffsetX by animateDpAsState(
        targetValue = if (stage == AnimationStage.RESET) (-56).dp else 0.dp,
        animationSpec = tween(if (stage == AnimationStage.VIDEOS_APPROACHING) 900 else 50, easing = FastOutSlowInEasing),
        label = "chip_offset_x",
    )
    val chipAlpha by animateFloatAsState(
        targetValue = if (stage == AnimationStage.VIDEOS_APPROACHING) 1f else 0f,
        animationSpec = tween(if (stage == AnimationStage.VIDEOS_APPROACHING) 500 else 300),
        label = "chip_alpha",
    )
    // A quick "impact" bump when the chips arrive (ENTERING_BOX), settling back to 1f during
    // PROCESSING - two consecutive stage-driven tweens, not a single spec, is what gives this the
    // shape of a bump rather than a held value.
    val boxImpactScale by animateFloatAsState(
        targetValue = if (stage == AnimationStage.ENTERING_BOX) 1.15f else 1f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "box_impact_scale",
    )
    val mapVisible = stage == AnimationStage.MAP_EMERGING || stage == AnimationStage.HOLDING
    val mapScale by animateFloatAsState(
        targetValue = if (mapVisible) 1f else 0f,
        animationSpec =
            if (mapVisible) {
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            } else {
                tween(200)
            },
        label = "map_scale",
    )
    val mapOffsetX by animateDpAsState(
        targetValue = if (mapVisible) 72.dp else 0.dp,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "map_offset_x",
    )

    // Continuous ambient "breathing" so the box still feels alive while PROCESSING holds - layered
    // multiplicatively with boxImpactScale rather than replacing it.
    val ambientTransition = rememberInfiniteTransition(label = "box_ambient")
    val ambientScale by ambientTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "box_ambient_scale",
    )

    Box(modifier = modifier.height(160.dp), contentAlignment = Alignment.Center) {
        VideoChip(modifier = Modifier.offset(x = chipOffsetX, y = (-28).dp).alpha(chipAlpha))
        VideoChip(modifier = Modifier.offset(x = chipOffsetX, y = 28.dp).alpha(chipAlpha))

        Box(
            modifier =
                Modifier
                    .size(64.dp)
                    .scale(boxImpactScale * ambientScale)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(18.dp)),
        )

        Icon(
            imageVector = Icons.Default.Place,
            contentDescription = null,
            tint = MapsGreen,
            modifier =
                Modifier
                    .offset(x = mapOffsetX)
                    .scale(mapScale)
                    .size(40.dp),
        )
    }
}

/** A small "video clip" chip - see [VideoToMapAnimationContent]. */
@Composable
private fun VideoChip(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(22.dp),
        )
    }
}
