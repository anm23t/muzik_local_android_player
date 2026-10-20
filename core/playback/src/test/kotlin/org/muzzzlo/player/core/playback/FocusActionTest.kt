package org.muzzzlo.player.core.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.muzzzlo.player.core.model.TransientFocusAction

internal class FocusActionTest {
    @ParameterizedTest
    @CsvSource(
        "DUCK, DUCK, DUCK",
        "DUCK, PAUSE, PAUSE",
        "PAUSE, DUCK, PAUSE",
        "PAUSE, PAUSE, PAUSE",
        "LOSS, PAUSE, LOSS",
        "GAIN, PAUSE, GAIN",
        "GAIN, DUCK, GAIN",
    )
    fun `only a duck becomes a pause`(
        change: FocusChange,
        action: TransientFocusAction,
        expected: FocusChange,
    ) {
        assertEquals(expected, change.under(action))
    }
}
