package org.muzzzlo.player

import android.net.Uri
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.CancellationException
import org.muzzzlo.player.feature.library.ALBUM_ID_ARG
import org.muzzzlo.player.feature.library.ARTIST_ID_ARG
import org.muzzzlo.player.feature.library.ARTIST_NAME_ARG
import org.muzzzlo.player.feature.library.AlbumListScreen
import org.muzzzlo.player.feature.library.AlbumScreen
import org.muzzzlo.player.feature.library.ArtistCheckScreen
import org.muzzzlo.player.feature.library.ArtistListScreen
import org.muzzzlo.player.feature.library.ArtistScreen
import org.muzzzlo.player.feature.library.CompoundNamesScreen
import org.muzzzlo.player.feature.library.HomeScreen
import org.muzzzlo.player.feature.library.NO_ARTIST
import org.muzzzlo.player.feature.library.TracksScreen
import org.muzzzlo.player.feature.player.MiniPlayer
import org.muzzzlo.player.feature.player.PlayerScreen
import org.muzzzlo.player.feature.player.QueueScreen
import org.muzzzlo.player.feature.playlists.PLAYLIST_ID_ARG
import org.muzzzlo.player.feature.playlists.PlaylistListScreen
import org.muzzzlo.player.feature.playlists.PlaylistScreen
import org.muzzzlo.player.feature.settings.FoldersScreen
import org.muzzzlo.player.feature.settings.QuarantineScreen
import org.muzzzlo.player.feature.settings.SettingsScreen
import org.muzzzlo.player.feature.stats.StatsScreen
import org.muzzzlo.player.feature.tageditor.ArtistProfileScreen
import org.muzzzlo.player.feature.tageditor.BATCH_ALBUM_ID_ARG
import org.muzzzlo.player.feature.tageditor.BatchTagEditorScreen
import org.muzzzlo.player.feature.tageditor.PROFILE_ARTIST_ID_ARG
import org.muzzzlo.player.feature.tageditor.TRACK_ID_ARG
import org.muzzzlo.player.feature.tageditor.TagEditorScreen

private const val ROUTE_HOME = "home"
private const val ROUTE_ALBUMS = "albums"
private const val ROUTE_ALBUM = "album/{$ALBUM_ID_ARG}"
private const val ROUTE_TRACKS = "tracks?$ARTIST_ID_ARG={$ARTIST_ID_ARG}"
private const val ROUTE_ARTISTS = "artists"
private const val ROUTE_ARTIST_CHECK = "artistCheck"
private const val ROUTE_COMPOUND_NAMES = "compoundNames"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_STATS = "stats"
private const val ROUTE_SETTINGS_FOLDERS = "settingsFolders"
private const val ROUTE_SETTINGS_QUARANTINE = "settingsQuarantine"
private const val ROUTE_ARTIST = "artist?$ARTIST_ID_ARG={$ARTIST_ID_ARG}&$ARTIST_NAME_ARG={$ARTIST_NAME_ARG}"
private const val ROUTE_QUEUE = "queue"
private const val ROUTE_PLAYLISTS = "playlists"
private const val ROUTE_PLAYLIST = "playlist/{$PLAYLIST_ID_ARG}"
private const val ROUTE_TAG_EDITOR = "tagEditor/{$TRACK_ID_ARG}"
private const val ROUTE_BATCH_TAG_EDITOR = "tagEditorAlbum/{$BATCH_ALBUM_ID_ARG}"
private const val ROUTE_ARTIST_PROFILE = "artistProfile/{$PROFILE_ARTIST_ID_ARG}"

private fun albumRoute(albumId: Long) = "album/$albumId"

private fun tagEditorRoute(trackId: Long) = "tagEditor/$trackId"

private fun batchTagEditorRoute(albumId: Long) = "tagEditorAlbum/$albumId"

private fun artistProfileRoute(artistId: Long) = "artistProfile/$artistId"

private fun tracksRoute(artistId: Long = NO_ARTIST) = "tracks?$ARTIST_ID_ARG=$artistId"

private fun artistRoute(artistId: Long) = "artist?$ARTIST_ID_ARG=$artistId"

/** The player and the album card know artists by name only; the card finds the artist by it. */
private fun artistRoute(name: String) = "artist?$ARTIST_NAME_ARG=${Uri.encode(name)}"

private val artistArguments =
    listOf(
        navArgument(ARTIST_ID_ARG) {
            type = NavType.LongType
            defaultValue = NO_ARTIST
        },
        navArgument(ARTIST_NAME_ARG) {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        },
    )

/** Every home button goes back to the home screen instead of stacking a new one (DESIGN.md, section 3). */
private fun NavHostController.goHome() {
    popBackStack(ROUTE_HOME, inclusive = false)
}

/** Wires the feature screens together; features do not know about each other. */
@Composable
fun MuzzzloNavHost() {
    val navController = rememberNavController()
    val openTagEditor: (Long) -> Unit = { navController.navigate(tagEditorRoute(it)) }
    val openArtist: (Long) -> Unit = { navController.navigate(artistRoute(it)) }
    val sheetScope = rememberCoroutineScope()
    val sheet = remember { PlayerSheetState(sheetScope) }
    // The screen the player was raised over, when the player sent the user to another screen: coming
    // back to it raises the player again. `left` turns true once another screen has been shown.
    var parked by remember { mutableStateOf<ParkedPlayer?>(null) }
    val goHome: () -> Unit = {
        parked = null
        sheet.close()
        navController.goHome()
    }
    // A screen opened from the player slides in over the screen under it, so the layer steps aside.
    val fromPlayer: (String) -> Unit = { route ->
        parked = ParkedPlayer(navController.currentBackStackEntry?.id)
        sheet.hide()
        navController.navigate(route)
    }
    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { entry ->
            val waiting = parked ?: return@collect
            if (entry.id != waiting.entryId) {
                waiting.left = true
            } else if (waiting.left) {
                parked = null
                sheet.open()
            }
        }
    }
    val miniPlayer: @Composable () -> Unit = {
        MiniPlayer(
            onOpen = sheet::open,
            onSheetDrag = sheet::dragBy,
            onSheetRelease = sheet::release,
            onOpenQueue = { navController.navigate(ROUTE_QUEUE) },
            onEditTags = openTagEditor,
            onOpenArtist = openArtist,
        )
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().onSizeChanged { sheet.heightPx = it.height.toFloat() }) {
            NavHost(
                navController,
                startDestination = ROUTE_HOME,
                enterTransition = ScreenTransitions.enter,
                exitTransition = ScreenTransitions.exit,
                popEnterTransition = ScreenTransitions.popEnter,
                popExitTransition = ScreenTransitions.popExit,
            ) {
                composable(ROUTE_HOME) {
                    HomeScreen(
                        onOpenArtists = { navController.navigate(ROUTE_ARTISTS) },
                        onOpenAlbums = { navController.navigate(ROUTE_ALBUMS) },
                        onOpenAlbum = { navController.navigate(albumRoute(it)) },
                        onOpenTracks = { navController.navigate(tracksRoute()) },
                        onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                        onOpenStats = { navController.navigate(ROUTE_STATS) },
                        onOpenPlaylists = { navController.navigate(ROUTE_PLAYLISTS) },
                        onOpenPlaylist = { navController.navigate("playlist/$it") },
                        miniPlayer = miniPlayer,
                    )
                }
                composable(ROUTE_PLAYLISTS) {
                    PlaylistListScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPlaylist = { navController.navigate("playlist/$it") },
                        miniPlayer = miniPlayer,
                    )
                }
                composable(
                    ROUTE_PLAYLIST,
                    arguments = listOf(navArgument(PLAYLIST_ID_ARG) { type = NavType.LongType }),
                ) {
                    PlaylistScreen(
                        onBack = { navController.popBackStack() },
                        onHome = { navController.goHome() },
                        onOpenQueue = { navController.navigate(ROUTE_QUEUE) },
                        onEditTags = openTagEditor,
                        onOpenArtist = openArtist,
                        miniPlayer = miniPlayer,
                    )
                }
                composable(ROUTE_STATS) {
                    StatsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenAlbum = { navController.navigate(albumRoute(it)) },
                        onOpenArtist = { id, name ->
                            navController.navigate(if (id != null) artistRoute(id) else artistRoute(name))
                        },
                    )
                }
                composable(ROUTE_ALBUMS) {
                    AlbumListScreen(
                        onBack = { navController.popBackStack() },
                        onOpenAlbum = { navController.navigate(albumRoute(it)) },
                        miniPlayer = miniPlayer,
                    )
                }
                composable(ROUTE_ARTISTS) {
                    ArtistListScreen(
                        onBack = { navController.popBackStack() },
                        onOpenArtist = { navController.navigate(artistRoute(it)) },
                        onCheck = { navController.navigate(ROUTE_ARTIST_CHECK) },
                        miniPlayer = miniPlayer,
                    )
                }
                composable(ROUTE_ARTIST_CHECK) {
                    ArtistCheckScreen(
                        onBack = { navController.popBackStack() },
                        onOpenCompoundNames = { navController.navigate(ROUTE_COMPOUND_NAMES) },
                    )
                }
                composable(ROUTE_COMPOUND_NAMES) {
                    CompoundNamesScreen(onBack = { navController.popBackStack() })
                }
                composable(ROUTE_SETTINGS) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenFolders = { navController.navigate(ROUTE_SETTINGS_FOLDERS) },
                        onOpenQuarantine = { navController.navigate(ROUTE_SETTINGS_QUARANTINE) },
                        onOpenCompoundNames = { navController.navigate(ROUTE_COMPOUND_NAMES) },
                    )
                }
                composable(ROUTE_SETTINGS_FOLDERS) { FoldersScreen(onBack = { navController.popBackStack() }) }
                composable(ROUTE_SETTINGS_QUARANTINE) { QuarantineScreen(onBack = { navController.popBackStack() }) }
                composable(ROUTE_ARTIST, arguments = artistArguments) {
                    ArtistScreen(
                        onBack = { navController.popBackStack() },
                        onHome = { goHome() },
                        onOpenAlbum = { navController.navigate(albumRoute(it)) },
                        onOpenSongs = { navController.navigate(tracksRoute(it)) },
                        onEditArtist = { navController.navigate(artistProfileRoute(it)) },
                        miniPlayer = miniPlayer,
                    )
                }
                composable(ROUTE_ALBUM, arguments = listOf(navArgument(ALBUM_ID_ARG) { type = NavType.LongType })) {
                    AlbumScreen(
                        onBack = { navController.popBackStack() },
                        onHome = { goHome() },
                        onOpenArtist = { navController.navigate(artistRoute(it)) },
                        onOpenArtistById = openArtist,
                        onOpenQueue = { navController.navigate(ROUTE_QUEUE) },
                        onEditTags = openTagEditor,
                        onEditAlbumTags = { navController.navigate(batchTagEditorRoute(it)) },
                        miniPlayer = miniPlayer,
                    )
                }
                composable(ROUTE_TRACKS, arguments = artistArguments.take(1)) {
                    TracksScreen(
                        onBack = { navController.popBackStack() },
                        onOpenQueue = { navController.navigate(ROUTE_QUEUE) },
                        onEditTags = openTagEditor,
                        onOpenArtist = openArtist,
                        miniPlayer = miniPlayer,
                    )
                }
                composable(ROUTE_QUEUE) {
                    QueueScreen(
                        onBack = { navController.popBackStack() },
                        onEditTags = openTagEditor,
                        onOpenArtist = openArtist,
                    )
                }
                composable(
                    ROUTE_TAG_EDITOR,
                    arguments = listOf(navArgument(TRACK_ID_ARG) { type = NavType.LongType }),
                ) {
                    TagEditorScreen(onClose = { navController.popBackStack() })
                }
                composable(
                    ROUTE_BATCH_TAG_EDITOR,
                    arguments = listOf(navArgument(BATCH_ALBUM_ID_ARG) { type = NavType.LongType }),
                ) {
                    BatchTagEditorScreen(onClose = { navController.popBackStack() })
                }
                composable(
                    ROUTE_ARTIST_PROFILE,
                    arguments = listOf(navArgument(PROFILE_ARTIST_ID_ARG) { type = NavType.LongType }),
                ) {
                    ArtistProfileScreen(
                        onClose = { navController.popBackStack() },
                        // The old artist card is gone after a rename or merge: replace it with the new one.
                        onSaved = { artistId ->
                            navController.popBackStack()
                            navController.popBackStack()
                            artistId?.let { navController.navigate(artistRoute(it)) }
                        },
                    )
                }
            }
            PlayerLayer(sheet, fromPlayer, goHome)
        }
    }
}

/** Remembers the screen under the player while another screen opened from the player is shown. */
private class ParkedPlayer(val entryId: String?) {
    var left = false
}

/** The player over the `NavHost`: follows the finger, closes with "back" and hands navigation to the screens. */
@Composable
private fun PlayerLayer(
    sheet: PlayerSheetState,
    fromPlayer: (String) -> Unit,
    goHome: () -> Unit,
) {
    PredictiveBackHandler(enabled = sheet.progress > 0f) { backEvents ->
        try {
            backEvents.collect { sheet.moveTo(1f - it.progress) }
            sheet.close()
        } catch (cancelled: CancellationException) {
            sheet.open()
            throw cancelled
        }
    }
    if (sheet.progress <= 0f) return
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = (1f - sheet.progress) * sheet.heightPx }
            .background(MaterialTheme.colorScheme.background)
            // Blocks touches from reaching the screen below while the layer is on top.
            .pointerInput(Unit) {},
    ) {
        PlayerScreen(
            onBack = sheet::close,
            onOpenQueue = { fromPlayer(ROUTE_QUEUE) },
            onOpenArtists = { fromPlayer(ROUTE_ARTISTS) },
            onOpenAlbums = { fromPlayer(ROUTE_ALBUMS) },
            onOpenArtist = { fromPlayer(artistRoute(it)) },
            onHome = goHome,
            onEditTags = { fromPlayer(tagEditorRoute(it)) },
            onSheetDrag = sheet::dragBy,
            onSheetRelease = sheet::release,
        )
    }
}
