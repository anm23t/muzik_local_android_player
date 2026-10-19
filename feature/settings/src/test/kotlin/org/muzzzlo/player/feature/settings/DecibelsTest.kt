package org.muzzzlo.player.feature.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.Locale

class DecibelsTest {
    @ParameterizedTest(name = "{0} dB in {1} -> {2}")
    @CsvSource(
        "0, en, 0",
        "-0, en, 0",
        "1.5, en, +1.5",
        "15, en, +15",
        "-6, en, −6",
        "-6.5, en, −6.5",
        "-6.5, ru, '−6,5'",
        "0.5, ru, '+0,5'",
        "-15, ru, −15",
    )
    fun `formats decibels with sign and local separator`(
        db: Float,
        language: String,
        expected: String,
    ) {
        assertEquals(expected, formatDecibels(db, Locale.forLanguageTag(language)))
    }
}
