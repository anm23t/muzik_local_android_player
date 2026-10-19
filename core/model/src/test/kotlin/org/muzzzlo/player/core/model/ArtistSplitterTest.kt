package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class ArtistSplitterTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun split(
        name: String,
        values: List<String>,
        expected: List<String>,
    ) {
        assertEquals(expected, ArtistSplitter.split(values, compounds))
    }

    @ParameterizedTest(name = "no compounds: {0}")
    @MethodSource("plainCases")
    fun splitWithoutCompounds(
        name: String,
        values: List<String>,
        expected: List<String>,
    ) {
        assertEquals(expected, ArtistSplitter.split(values))
    }

    companion object {
        private val compounds =
            CompoundNames {
                setOf(
                    listOf("tyler", "the creator"),
                    listOf("crosby", "stills & nash"),
                    listOf("crosby", "stills", "nash & young"),
                    listOf("earth", "wind & fire"),
                )
            }

        private fun case(
            name: String,
            values: List<String>,
            vararg expected: String,
        ) = Arguments.of(name, values, expected.toList())

        @JvmStatic
        fun cases(): Stream<Arguments> =
            Stream.of(
                case("empty list", emptyList()),
                case("blank value", listOf("  "), *emptyArray()),
                case("single name", listOf("Radiohead"), "Radiohead"),
                case("compound alone", listOf("Tyler, The Creator"), "Tyler, The Creator"),
                case(
                    "compound then artist",
                    listOf("Tyler, The Creator, Kanye West"),
                    "Tyler, The Creator",
                    "Kanye West",
                ),
                case(
                    "artist then compound",
                    listOf("Kanye West, Tyler, The Creator"),
                    "Kanye West",
                    "Tyler, The Creator",
                ),
                case("compound case and spaces", listOf("tyler ,  the CREATOR"), "tyler, the CREATOR"),
                case(
                    "semicolon keeps commas",
                    listOf("Tyler, The Creator; Kanye West"),
                    "Tyler, The Creator",
                    "Kanye West",
                ),
                case("semicolon without compounds listed", listOf("A, B; C"), "A, B", "C"),
                case(
                    "several values are not split",
                    listOf("Tyler, The Creator", "A; B"),
                    "Tyler, The Creator",
                    "A; B",
                ),
                case("several values, blanks dropped", listOf("A", " ", "B"), "A", "B"),
                case("ampersand stays", listOf("Simon & Garfunkel"), "Simon & Garfunkel"),
                case("slash stays", listOf("AC/DC"), "AC/DC"),
                case("feat stays", listOf("A feat. B"), "A feat. B"),
                case("plain commas", listOf("A, B, C"), "A", "B", "C"),
                case("empty comma parts", listOf("A,, B,"), "A", "B"),
                case("empty semicolon parts", listOf("A , B ; ;"), "A , B"),
                case("trailing semicolon", listOf("A; B;"), "A", "B"),
                case("repeats removed", listOf("A, a, B"), "A", "B"),
                case("repeats removed in values", listOf("A", "a"), "A"),
                case(
                    "two compounds in a row",
                    listOf("Earth, Wind & Fire, Tyler, The Creator"),
                    "Earth, Wind & Fire",
                    "Tyler, The Creator",
                ),
                case("longest compound wins", listOf("Crosby, Stills, Nash & Young"), "Crosby, Stills, Nash & Young"),
                case("shorter compound", listOf("Crosby, Stills & Nash"), "Crosby, Stills & Nash"),
                case("and equals ampersand", listOf("Earth, Wind and Fire"), "Earth, Wind and Fire"),
                case("partial match is not glued", listOf("Tyler, Someone"), "Tyler", "Someone"),
                case("only the second fragment", listOf("The Creator, Tyler"), "The Creator", "Tyler"),
            )

        @JvmStatic
        fun plainCases(): Stream<Arguments> =
            Stream.of(
                case("compound splits when unknown", listOf("Tyler, The Creator"), "Tyler", "The Creator"),
                case("semicolon still works", listOf("A; B"), "A", "B"),
            )
    }
}
