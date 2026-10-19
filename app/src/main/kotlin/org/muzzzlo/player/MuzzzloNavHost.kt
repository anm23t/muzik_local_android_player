package org.muzzzlo.player

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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
private const val ROUTE_ARTIST = "artist?$ARTIST_ID_ARG={$ARTIST_ID_ARG}&$ARTIST_NAME_ARG={$ARTIST_NAME_ARG}"
private const val ROUTE_PLAYER = "player"
private const val ROUTE_QUEUE = "queue"
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
    val openArtist: (String) -> Unit = { navController.navigate(artistRoute(it)) }
    val miniPlayer: @Composable () -> Unit = {
        MiniPlayer(
            onOpen = { navController.navigate(ROUTE_PLAYER) },
            onOpenQueue = { navController.navigate(ROUTE_QUEUE) },
            onEditTags = openTagEditor,
            onOpenArtist = openArtist,
        )
    }
    Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
        NavHost(navController, startDestination = ROUTE_HOME) {
            composable(ROUTE_HOME) {
                HomeScreen(
                    onOpenArtists = { navController.navigate(ROUTE_ARTISTS) },
                    onOpenAlbums = { navController.navigate(ROUTE_ALBUMS) },
                    onOpenAlbum = { navController.navigate(albumRoute(it)) },
                    onOpenTracks = { navController.navigate(tracksRoute()) },
                    miniPlayer = miniPlayer,
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
            composable(ROUTE_ARTIST, arguments = artistArguments) {
                ArtistScreen(
                    onBack = { navController.popBackStack() },
                    onHome = { navController.goHome() },
                    onOpenAlbum = { navController.navigate(albumRoute(it)) },
                    onOpenSongs = { navController.navigate(tracksRoute(it)) },
                    onEditArtist = { navController.navigate(artistProfileRoute(it)) },
                    miniPlayer = miniPlayer,
                )
            }
            composable(ROUTE_ALBUM, arguments = listOf(navArgument(ALBUM_ID_ARG) { type = NavType.LongType })) {
                AlbumScreen(
                    onBack = { navController.popBackStack() },
                    onHome = { navController.goHome() },
                    onOpenArtist = { navController.navigate(artistRoute(it)) },
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
            composable(ROUTE_PLAYER) {
                PlayerScreen(
                    onBack = { navController.popBackStack() },
                    onOpenQueue = { navController.navigate(ROUTE_QUEUE) },
                    onOpenArtists = { navController.navigate(ROUTE_ARTISTS) },
                    onOpenAlbums = { navController.navigate(ROUTE_ALBUMS) },
                    onOpenArtist = openArtist,
                    onHome = { navController.goHome() },
                    onEditTags = openTagEditor,
                )
            }
            composable(ROUTE_QUEUE) {
                QueueScreen(
                    onBack = { navController.popBackStack() },
                    onEditTags = openTagEditor,
                    onOpenArtist = openArtist,
                )
            }
            composable(ROUTE_TAG_EDITOR, arguments = listOf(navArgument(TRACK_ID_ARG) { type = NavType.LongType })) {
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
    }
}
