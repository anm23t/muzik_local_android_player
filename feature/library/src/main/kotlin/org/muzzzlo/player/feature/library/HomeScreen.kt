package org.muzzzlo.player.feature.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.muzzzlo.player.core.designsystem.component.BottomNav
import org.muzzzlo.player.core.designsystem.component.BottomNavItem
import org.muzzzlo.player.core.designsystem.component.CoverImage
import org.muzzzlo.player.core.designsystem.component.CoverSource
import org.muzzzlo.player.core.designsystem.component.CoverTile
import org.muzzzlo.player.core.designsystem.component.PillButton
import org.muzzzlo.player.core.designsystem.component.RoundIconButton
import org.muzzzlo.player.core.designsystem.component.ScreenFrame
import org.muzzzlo.player.core.designsystem.component.SectionTitle
import org.muzzzlo.player.core.designsystem.theme.MuzzzloColors
import org.muzzzlo.player.core.designsystem.theme.MuzzzloDimens
import org.muzzzlo.player.core.model.Album
import org.muzzzlo.player.core.designsystem.R as DesignR

private val audioPermission: String
    get() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

/** Tiles in one row of the home screen (28:620). */
private const val TILES_PER_ROW = 3

/**
 * Home screen (28:620). Only the parts that need neither statistics nor smart playlists: the greeting,
 * "Continue listening", the mini player and the bottom menu. Playlists, statistics and settings
 * are shown disabled until their tasks.
 *
 * @param miniPlayer the mini player of `feature:player`, passed in by the app.
 */
@Composable
fun HomeScreen(
    onOpenArtists: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenTracks: () -> Unit,
    miniPlayer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var granted by rememberSaveable {
        mutableStateOf(context.checkSelfPermission(audioPermission) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(granted) { if (granted) viewModel.onScanRequested() }

    ScreenFrame(modifier, spacing = MuzzzloDimens.SectionGap, outlined = false) {
        HomeHeader()
        // Not in the mockup: the list of all tracks has no other entry point (DESIGN.md, section 7).
        if (granted && state.hasMusic) {
            PillButton(text = stringResource(R.string.home_all_songs), onClick = onOpenTracks)
        }
        when {
            !granted -> PermissionRequest { launcher.launch(audioPermission) }
            state.isScanning && !state.hasMusic ->
                Notice(
                    stringResource(R.string.library_scanning),
                    Modifier.weight(1f),
                )
            !state.hasMusic -> Notice(stringResource(R.string.library_empty), Modifier.weight(1f))
            else -> ContinueListening(state.continueAlbum, onOpenAlbum)
        }
        Spacer(Modifier.weight(1f))
        miniPlayer()
        BottomNav(homeNavItems(onOpenArtists, onOpenAlbums))
    }
}

@Composable
private fun HomeHeader() {
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
        // Settings arrive in 0.5.1.
        RoundIconButton(
            icon = DesignR.drawable.ic_settings,
            contentDescription = stringResource(R.string.home_settings),
            onClick = {},
            enabled = false,
        )
    }
}

@Composable
private fun ContinueListening(
    album: Album?,
    onOpenAlbum: (Long) -> Unit,
) {
    if (album == null) return
    Column(verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.ContentGap)) {
        SectionTitle(stringResource(R.string.home_continue_listening), maxLines = 2)
        Row(horizontalArrangement = Arrangement.spacedBy(MuzzzloDimens.SectionGap)) {
            CoverTile(
                title = album.title,
                onClick = { onOpenAlbum(album.id) },
                modifier = Modifier.weight(1f),
                cover = { CoverImage(CoverSource.Album(album.id), it, MuzzzloDimens.TileShape) },
            )
            repeat(TILES_PER_ROW - 1) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun ColumnScope.PermissionRequest(onRequest: () -> Unit) {
    Column(
        Modifier.weight(1f).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MuzzzloDimens.SectionGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.library_permission_rationale),
            style = MaterialTheme.typography.bodyLarge,
            color = MuzzzloColors.TextSecondary,
        )
        PillButton(text = stringResource(R.string.library_permission_grant), onClick = onRequest)
    }
}

/** Artists, albums, playlists (1.1), statistics (0.6), as in 28:697. */
@Composable
private fun homeNavItems(
    onOpenArtists: () -> Unit,
    onOpenAlbums: () -> Unit,
): List<BottomNavItem> =
    listOf(
        BottomNavItem(DesignR.drawable.ic_users, stringResource(R.string.nav_artists), onOpenArtists),
        BottomNavItem(DesignR.drawable.ic_disc, stringResource(R.string.nav_albums), onOpenAlbums),
        BottomNavItem(DesignR.drawable.ic_list_music, stringResource(R.string.nav_playlists), {}, enabled = false),
        BottomNavItem(
            DesignR.drawable.ic_chart_no_axes_column,
            stringResource(R.string.nav_statistics),
            {},
            enabled = false,
        ),
    )
