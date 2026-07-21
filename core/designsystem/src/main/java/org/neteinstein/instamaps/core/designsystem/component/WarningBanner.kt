package org.neteinstein.instamaps.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * How serious a [WarningBanner] is. [WARNING] (the original/default styling) is for something
 * that blocks InstaMaps from working at all; [INFO] is for a purely optional improvement - see
 * `feature:share`'s Instagram-connect nudge, which uses [INFO] specifically so it doesn't read as
 * an error like a missing API key or permission would.
 */
enum class BannerTone {
    WARNING,
    INFO,
}

/**
 * A readiness nudge paired with a single action button that resolves it (open Settings, connect
 * Instagram, ...). [tone] drives both the color scheme and the icon - see [BannerTone].
 */
@Composable
fun WarningBanner(
    message: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.WARNING,
) {
    val containerColor: Color
    val contentColor: Color
    val icon: ImageVector
    when (tone) {
        BannerTone.WARNING -> {
            containerColor = MaterialTheme.colorScheme.errorContainer
            contentColor = MaterialTheme.colorScheme.onErrorContainer
            icon = Icons.Filled.Warning
        }
        BannerTone.INFO -> {
            containerColor = MaterialTheme.colorScheme.secondaryContainer
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            icon = Icons.Filled.Info
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            TextButton(
                onClick = onActionClick,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(text = actionLabel)
            }
        }
    }
}
