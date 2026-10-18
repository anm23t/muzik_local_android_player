package org.muzzzlo.player.feature.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.muzzzlo.player.core.designsystem.component.TrackAction
import org.muzzzlo.player.core.model.Track

class PlayerViewModelTest {
    private val track = Track(1, "content://1", "Title", listOf("A", "B"), null, null, 1_000, null)

    @ParameterizedTest
    @EnumSource(TrackAction::class)
    fun `only queue actions reach the player`(action: TrackAction) {
        val player = FakePlayerController()
        PlayerViewModel(player).onTrackAction(track, action)

        val expectedNext = if (action == TrackAction.PlayNext) listOf(track) else emptyList()
        val expectedQueue = if (action == TrackAction.AddToQueue) listOf(track) else emptyList()
        assertEquals(expectedNext, player.playedNext)
        assertEquals(expectedQueue, player.addedToQueue)
    }
}
