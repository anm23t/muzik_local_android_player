package org.muzzzlo.player.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens

/** The end of the settings: the app name with its version (`versionName` of the package) and the developer. */
@Composable
internal fun AppFooter(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember(context) { installedVersionName(context) }
    Column(modifier.fillMaxWidth().padding(vertical = MuzzzloDimens.ContentGap)) {
        FooterLine(stringResource(R.string.settings_footer_version, version))
        FooterLine(stringResource(R.string.settings_footer_developer))
    }
}

@Composable
private fun FooterLine(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = MuzzzloColors.TextSecondary,
        textAlign = TextAlign.Center,
    )
}

/** Empty when the package info is unavailable, which does not happen for an installed app. */
internal fun installedVersionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
