package org.muzzzlo.player.feature.library

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.BottomNav
import org.muzzzlo.player.core.designsystem.component.BottomNavItem
import org.muzzzlo.player.core.designsystem.component.PillButton
import org.muzzzlo.player.core.designsystem.component.RoundIconButton
import org.muzzzlo.player.core.designsystem.component.ScreenFrame
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.model.GeneratedKind
import org.muzzzlo.player.core.designsystem.R as DesignR

private val audioPermission: String
    get() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

/**
 * Home screen (28:620): the greeting, "Playlists for you" (generated playlists, task 2.5.7), "Continue listening",
 * the mini player and the bottom menu.
 *
 * @param onOpenGenerated opens the card of today's generated playlist of this kind.
 * @param onOpenSummary opens the summary slides of the period with this `CalendarPeriod.key` (task 2.5.13).
 * @param miniPlayer the mini player of `feature:player`, passed in by the app.
 */
@Composable
fun HomeScreen(
    onOpenArtists: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenTracks: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenGenerated: (GeneratedKind) -> Unit,
    onOpenSummary: (String) -> Unit,
    miniPlayer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var granted by rememberSaveable {
        mutableStateOf(context.checkSelfPermission(audioPermission) == PackageManager.PERMISSION_GRANTED)
    }
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            granted = it
            denied = !it
        }
    // The permission can be given in the system settings while the app is in the background.
    LifecycleResumeEffect(Unit) {
        granted = context.checkSelfPermission(audioPermission) == PackageManager.PERMISSION_GRANTED
        onPauseOrDispose {}
    }
    LaunchedEffect(granted) { if (granted) viewModel.onScanRequested() }

    ScreenFrame(modifier, spacing = MuzzzloDimens.SectionGap, outlined = false) {
        HomeHeader(onOpenSearch, onOpenSettings)
        // Not in the mockup: the list of all tracks has no other entry point (DESIGN.md, section 7).
        if (granted && state.hasMusic) {
            PillButton(text = stringResource(R.string.home_all_songs), onClick = onOpenTracks)
        }
        when {
            !granted ->
                PermissionRequest(blocked = denied && !context.canAskAgain()) {
                    if (denied && !context.canAskAgain()) {
                        context.openAppSettings()
                    } else {
                        launcher.launch(
                            audioPermission,
                        )
                    }
                }
            state.scanFailed && !state.hasMusic -> ScanFailed(viewModel::onScanRequested)
            state.isScanning && !state.hasMusic ->
                Notice(
                    stringResource(R.string.library_scanning),
                    Modifier.weight(1f),
                )
            !state.hasMusic -> Notice(stringResource(R.string.library_empty), Modifier.weight(1f))
            else -> {
                PlaylistsForYou(state.generated, onOpenGenerated)
                YourSummaries(state.summaries, onOpenSummary)
                ContinueListening(state.continueItems, onOpenAlbum, onOpenPlaylist, onOpenGenerated)
            }
        }
        Spacer(Modifier.weight(1f))
        miniPlayer()
        BottomNav(homeNavItems(onOpenArtists, onOpenAlbums, onOpenPlaylists, onOpenStats))
    }
}

@Composable
private fun HomeHeader(
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.ContentGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_welcome),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineLarge,
            color = MuzzzloColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Not in the mockup (task 1.2.4, DESIGN.md, section 7): the same round button as the gear.
        RoundIconButton(
            icon = DesignR.drawable.ic_search,
            contentDescription = stringResource(R.string.home_search),
            onClick = onOpenSearch,
        )
        RoundIconButton(
            icon = DesignR.drawable.ic_settings,
            contentDescription = stringResource(R.string.home_settings),
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun ColumnScope.PermissionRequest(
    blocked: Boolean,
    onRequest: () -> Unit,
) {
    Column(
        Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.SectionGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text =
                stringResource(
                    if (blocked) R.string.library_permission_blocked else R.string.library_permission_rationale,
                ),
            style = MaterialTheme.typography.bodyLarge,
            color = MuzzzloColors.TextSecondary,
        )
        PillButton(
            text =
                stringResource(
                    if (blocked) R.string.library_permission_settings else R.string.library_permission_grant,
                ),
            onClick = onRequest,
        )
    }
}

@Composable
private fun ColumnScope.ScanFailed(onRetry: () -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.SectionGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.library_scan_failed),
            style = MaterialTheme.typography.bodyLarge,
            color = MuzzzloColors.TextSecondary,
        )
        PillButton(text = stringResource(R.string.library_scan_retry), onClick = onRetry)
    }
}

/** False when the system will not show the permission dialog any more ("don't ask again"). */
private fun Context.canAskAgain(): Boolean =
    (findActivity()?.shouldShowRequestPermissionRationale(audioPermission)) ?: true

private fun Context.openAppSettings() =
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/** Artists, albums, playlists (1.1), statistics (0.6), as in 28:697. */
@Composable
private fun homeNavItems(
    onOpenArtists: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenStats: () -> Unit,
): List<BottomNavItem> =
    listOf(
        BottomNavItem(DesignR.drawable.ic_users, stringResource(R.string.nav_artists), onOpenArtists),
        BottomNavItem(DesignR.drawable.ic_disc, stringResource(R.string.nav_albums), onOpenAlbums),
        BottomNavItem(DesignR.drawable.ic_list_music, stringResource(R.string.nav_playlists), onOpenPlaylists),
        BottomNavItem(
            DesignR.drawable.ic_chart_no_axes_column,
            stringResource(R.string.nav_statistics),
            onOpenStats,
        ),
    )

/** Albums and playlists have their own ids; the key keeps them apart. */
