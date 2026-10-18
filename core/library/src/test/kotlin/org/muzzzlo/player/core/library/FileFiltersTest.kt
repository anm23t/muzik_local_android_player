package org.muzzzlo.player.core.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class FileFiltersTest {
    @ParameterizedTest
    @CsvSource("book.m4b, true", "BOOK.M4B, true", "song.m4a, false", "m4b, false", "song.mp3, false")
    fun `m4b files are audiobooks`(
        name: String,
        expected: Boolean,
    ) {
        assertEquals(expected, isAudiobookFile(name))
    }

    @Test
    fun `missing name is not an audiobook`() {
        assertEquals(false, isAudiobookFile(null))
    }

    @Test
    fun `json array escapes special characters`() {
        assertEquals("[]", jsonArray(emptyList()))
        assertEquals("[\"a\",\"b\"]", jsonArray(listOf("a", "b")))
        assertEquals("[\"say \\\"hi\\\"\"]", jsonArray(listOf("say \"hi\"")))
        assertEquals("[\"a\\\\b\"]", jsonArray(listOf("a\\b")))
        assertEquals("[\"line\\nbreak\"]", jsonArray(listOf("line\nbreak")))
    }
}
