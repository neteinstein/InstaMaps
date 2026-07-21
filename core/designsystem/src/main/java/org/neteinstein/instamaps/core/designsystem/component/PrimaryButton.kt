package org.neteinstein.instamaps.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.neteinstein.instamaps.core.designsystem.theme.MapsGreen

/**
 * How a [PrimaryButton] is colored. [DEFAULT] is the theme's standard button styling; [SUCCESS]/
 * [ERROR] recolor it to confirm or reject an outcome the button itself just triggered - e.g.
 * `feature:settings`'s Save button turning green/red once a just-entered Gemini API key has been
 * checked against the real Gemini API. Kept at full-strength color even while disabled (see
 * [PrimaryButton]'s use of [ButtonDefaults.buttonColors]'s `disabled*` parameters below) since
 * that's exactly the state a just-saved key ends up in - nothing left to click until the user
 * edits the field again, but the color still needs to read as a definite outcome, not fade to the
 * theme's generic disabled grey. Same "tone drives color" shape as [WarningBanner]'s `BannerTone`.
 */
enum class ButtonTone {
    DEFAULT,
    SUCCESS,
    ERROR,
}

/**
 * Standard call-to-action button, e.g. "Open in Google Maps". Full-width by default; pass
 * [fillWidth] = false for a compact button sized to its content, e.g. one placed inline next to
 * a text field. [loading] swaps the label for a small spinner and forces the button
 * non-clickable (regardless of [enabled]) while some background check/action it triggered is
 * still running.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillWidth: Boolean = true,
    loading: Boolean = false,
    tone: ButtonTone = ButtonTone.DEFAULT,
) {
    val colors =
        when (tone) {
            ButtonTone.DEFAULT -> ButtonDefaults.buttonColors()
            ButtonTone.SUCCESS ->
                ButtonDefaults.buttonColors(
                    containerColor = MapsGreen,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MapsGreen,
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary,
                )
            ButtonTone.ERROR ->
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    disabledContainerColor = MaterialTheme.colorScheme.error,
                    disabledContentColor = MaterialTheme.colorScheme.onError,
                )
        }
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        colors = colors,
        modifier = if (fillWidth) modifier.fillMaxWidth() else modifier,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
            )
        } else {
            Text(text = text)
        }
    }
}
