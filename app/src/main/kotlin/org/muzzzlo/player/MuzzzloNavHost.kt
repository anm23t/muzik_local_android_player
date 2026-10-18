package org.muzzzlo.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.muzzzlo.player.feature.library.LibraryScreen
import org.muzzzlo.player.feature.player.MiniPlayer
import org.muzzzlo.player.feature.player.PlayerScreen
import org.muzzzlo.player.feature.player.QueueScreen

private const val ROUTE_LIBRARY = "library"
private const val ROUTE_PLAYER = "player"
private const val ROUTE_QUEUE = "queue"

/** Wires the feature screens together; features do not know about each other. */
@Composable
fun MuzzzloNavHost() {
    val navController = rememberNavController()
    Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
        NavHost(navController, startDestination = ROUTE_LIBRARY) {
            composable(ROUTE_LIBRARY) {
                Column {
                    LibraryScreen(Modifier.weight(1f))
                    MiniPlayer(onOpen = { navController.navigate(ROUTE_PLAYER) })
                }
            }
            composable(ROUTE_PLAYER) { PlayerScreen(onOpenQueue = { navController.navigate(ROUTE_QUEUE) }) }
            composable(ROUTE_QUEUE) { QueueScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
