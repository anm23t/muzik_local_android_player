package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class AppSettingsTest {
    @Test
    fun `defaults match ARCHITECTURE section 9`() {
        val settings = AppSettings()
        assertEquals(emptySet<String>(), settings.excludedFolders)
        assertEquals(30, settings.minTrackSeconds)
        assertEquals(4, settings.id3v2VersionForNewTags)
        assertEquals(true, settings.confirmBeforeWrite)
        assertEquals(AppLanguage.SYSTEM, settings.language)
        assertEquals(true, settings.dynamicAccent)
    }

    @ParameterizedTest
    @CsvSource("-5, 0", "0, 0", "30, 30", "600, 600", "601, 600", "100000, 600")
    fun `minimum length is clamped`(
        input: Int,
        expected: Int,
    ) {
        assertEquals(expected, AppSettings.clampMinTrackSeconds(input))
    }
}
