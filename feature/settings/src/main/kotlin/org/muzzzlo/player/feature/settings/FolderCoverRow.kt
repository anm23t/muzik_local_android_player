package org.muzzzlo.player.feature.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.muzzzlo.player.core.designsystem.component.SwitchRow

/**
 * The "Covers from folders" switch (task 2.0.5). Turning it on asks for the permission to read images first
 * (Android 13 and newer); a refusal leaves it off. A permission taken back in the system settings shows the switch
 * off, and the library then uses tag covers only. Folders are known from Android 10, so older systems have no row.
 */
@Composable
internal fun FolderCoverRow(
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.canReadImages()) }
    LifecycleResumeEffect(Unit) {
        granted = context.canReadImages()
        onPauseOrDispose {}
    }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { given ->
            granted = given
            if (given) onChange(true)
        }
    SwitchRow(
        text = stringResource(R.string.settings_folder_covers),
        checked = enabled && granted,
        onCheckedChange = { on ->
            when {
                !on -> onChange(false)
                granted -> onChange(true)
                else -> launcher.launch(IMAGES_PERMISSION)
            }
        },
        secondary = stringResource(R.string.settings_folder_covers_hint),
    )
}

private val IMAGES_PERMISSION =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

private fun Context.canReadImages(): Boolean =
    checkSelfPermission(IMAGES_PERMISSION) == PackageManager.PERMISSION_GRANTED
