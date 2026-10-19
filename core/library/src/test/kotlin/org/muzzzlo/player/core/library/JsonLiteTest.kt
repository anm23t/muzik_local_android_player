package org.muzzzlo.player.core.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class JsonLiteTest {
    @Test
    fun `strings round trip through jsonArray`() {
        val cases =
            listOf(
                emptyList(),
                listOf("A"),
                listOf("Tyler, The Creator", "Kanye West"),
                listOf("say \"hi\"", "back\\slash", "tab\there", "line\nbreak", "ctl\u0001x", "Кино", "Beyoncé"),
            )
        cases.forEach { assertEquals(it, JsonLite.strings(jsonArray(it)), it.toString()) }
    }

    @Test
    fun `anything that is not an array of strings gives no strings`() {
        listOf(
            "",
            "null",
            "{}",
            "\"A\"",
            "not json",
        ).forEach { assertEquals(emptyList<String>(), JsonLite.strings(it)) }
        assertEquals(listOf("A", "B"), JsonLite.strings("  [ \"A\" , \"B\" ]  "))
        assertEquals(listOf("é"), JsonLite.strings("[\"\\u00e9\"]"))
    }

    @Test
    fun `built-in list is read in file order`() {
        val json =
            """{ "version": 2, "artists": [
              { "id": "a", "name": "Tyler, The Creator" },
              { "id": "b", "name": "Earth, Wind & Fire" } ] }"""
        assertEquals(
            listOf("a" to "Tyler, The Creator", "b" to "Earth, Wind & Fire"),
            JsonLite.builtInEntries(json),
        )
    }

    @Test
    fun `the shipped built-in list parses and every name has a comma`() {
        val file = File("src/main/assets/compound_artists.json")
        val entries = JsonLite.builtInEntries(file.readText())

        assertEquals(7, entries.size)
        assertEquals(entries.size, entries.map { it.first }.toSet().size)
        entries.forEach { (_, name) -> assertEquals(true, ',' in name, name) }
    }
}
