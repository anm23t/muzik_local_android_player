package org.muzzzlo.player.core.playback

import androidx.media3.common.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.model.ListenEnd

class ListenEndTest {
    @Test
    fun `automatic moves and repeats finish the track, the rest is the user`() {
        assertEquals(ListenEnd.COMPLETED, listenEndOf(Player.MEDIA_ITEM_TRANSITION_REASON_AUTO))
        assertEquals(ListenEnd.COMPLETED, listenEndOf(Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT))
        assertEquals(ListenEnd.MANUAL, listenEndOf(Player.MEDIA_ITEM_TRANSITION_REASON_SEEK))
        assertEquals(ListenEnd.MANUAL, listenEndOf(Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED))
    }
}
