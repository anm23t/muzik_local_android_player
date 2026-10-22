package org.muzzzlo.player.feature.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.ActionRow
import org.muzzzlo.player.core.designsystem.component.MuzzzloDialog
import org.muzzzlo.player.core.designsystem.component.PillButton
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.R as DesignR

/**
 * The windows of the update check, drawn over the current screen (no mockup, DESIGN.md, section 7): the question
 * "Check for updates?" with "Yes" and "No", and the announcement of a new release with a button to its page.
 * The page opens in the browser; the app downloads and installs nothing.
 */
@Composable
fun UpdatePrompts(viewModel: UpdateViewModel = hiltViewModel()) {
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()
    when (val shown = prompt) {
        UpdatePrompt.None -> Unit
        UpdatePrompt.AskConsent ->
            MuzzzloDialog(
                title = stringResource(R.string.update_ask_title),
                onDismiss = { viewModel.onConsent(false) },
            ) {
                DialogText(stringResource(R.string.update_ask_text))
                PillButton(
                    text = stringResource(R.string.update_ask_yes),
                    onClick = { viewModel.onConsent(true) },
                    wide = true,
                )
                ActionRow(
                    icon = DesignR.drawable.ic_close,
                    text = stringResource(R.string.update_ask_no),
                    onClick = { viewModel.onConsent(false) },
                )
            }
        is UpdatePrompt.Available -> {
            val context = LocalContext.current
            MuzzzloDialog(
                title = stringResource(R.string.update_available_title),
                onDismiss = viewModel::onAnnouncementClosed,
            ) {
                DialogText(stringResource(R.string.update_available_text, shown.release.tag))
                PillButton(
                    text = stringResource(R.string.update_open_page),
                    onClick = {
                        viewModel.onAnnouncementClosed()
                        context.openPage(shown.release.pageUrl)
                    },
                    wide = true,
                )
            }
        }
    }
}

@Composable
private fun DialogText(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MuzzzloColors.TextPrimary)
}

private fun android.content.Context.openPage(url: String) {
    val view = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(view)
    } catch (_: ActivityNotFoundException) {
        // A phone without a browser: the dialog was only a notice.
    }
}
