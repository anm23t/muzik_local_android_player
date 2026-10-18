package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class NameNormalizerTest {
    @ParameterizedTest
    @CsvSource(
        "Eminem, eminem",
        "'  Eminem  ', eminem",
        "'Simon  &   Garfunkel', simon & garfunkel",
        "Simon and Garfunkel, simon & garfunkel",
        "Simon AND Garfunkel, simon & garfunkel",
        "AC/DC, ac/dc",
        "'', ''",
    )
    fun `key ignores case, spaces and and-sign`(
        input: String,
        expected: String,
    ) {
        assertEquals(expected, NameNormalizer.key(input))
    }

    @Test
    fun `words containing and are not touched`() {
        assertEquals("sandra", NameNormalizer.key("Sandra"))
        assertNotEquals("s&ra", NameNormalizer.key("Sandra"))
    }
}
