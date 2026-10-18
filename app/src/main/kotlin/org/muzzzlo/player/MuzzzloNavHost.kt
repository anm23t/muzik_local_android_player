package org.muzzzlo.player

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
import org.muzzzlo.player.feature.library.AlbumListScreen
import org.muzzzlo.player.feature.library.AlbumScreen
import org.muzzzlo.player.feature.library.HomeScreen
import org.muzzzlo.player.feature.library.TracksScreen
import org.muzzzlo.player.feature.player.MiniPlayer
import org.muzzzlo.player.feature.player.PlayerScreen
import org.muzzzlo.player.feature.player.QueueScreen

private const val ROUTE_HOME = "home"
private const val ROUTE_ALBUMS = "albums"
private const val ROUTE_ALBUM = "album/{$ALBUM_ID_ARG}"
private const val ROUTE_TRACKS = "tracks"
private const val ROUTE_PLAYER = "player"
private const val ROUTE_QUEUE = "queue"

private fun albumRoute(albumId: Long) = "album/$albumId"

/** Every home button goes back to the home screen instead of stacking a new one (DESIGN.md, section 3). */
private fun NavHostController.goHome() {
    popBackStack(ROUTE_HOME, inclusive = false)
}

/** Wires the feature screens together; features do not know about each other. */
@Composable
fun MuzzzloNavHost() {
    val navController = rememberNavController()
    val miniPlayer: @Composable () -> Unit = {
        MiniPlayer(
            onOpen = { navController.navigate(ROUTE_PLAYER) },
            onOpenQueue = { navController.navigate(ROUTE_QUEUE) },
        )
    }
    Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
        NavHost(navController, startDestination = ROUTE_HOME) {
            composable(ROUTE_HOME) {
                HomeScreen(
                    onOpenAlbums = { navController.navigate(ROUTE_ALBUMS) },
                    onOpenAlbum = { navController.navigate(albumRoute(it)) },
                    onOpenTracks = { navController.navigate(ROUTE_TRACKS) },
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
            composable(ROUTE_ALBUM, arguments = listOf(navArgument(ALBUM_ID_ARG) { type = NavType.LongType })) {
                AlbumScreen(
                    onBack = { navController.popBackStack() },
                    onHome = { navController.goHome() },
                    onOpenQueue = { navController.navigate(ROUTE_QUEUE) },
                    miniPlayer = miniPlayer,
                )
            }
            composable(ROUTE_TRACKS) {
                TracksScreen(
                    onBack = { navController.popBackStack() },
                    onOpenQueue = { navController.navigate(ROUTE_QUEUE) },
                    miniPlayer = miniPlayer,
                )
            }
            composable(ROUTE_PLAYER) {
                PlayerScreen(
                    onBack = { navController.popBackStack() },
                    onOpenQueue = { navController.navigate(ROUTE_QUEUE) },
                    onOpenAlbums = { navController.navigate(ROUTE_ALBUMS) },
                    onHome = { navController.goHome() },
                )
            }
            composable(ROUTE_QUEUE) { QueueScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
