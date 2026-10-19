package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CompoundRulesTest {
    @Test
    fun `match key joins normalised parts`() {
        val cases =
            mapOf(
                "Tyler, The Creator" to "tyler|the creator",
                "  tyler ,  THE   creator " to "tyler|the creator",
                "Earth, Wind & Fire" to "earth|wind & fire",
                "Earth, Wind and Fire" to "earth|wind & fire",
                "Crosby, Stills, Nash & Young" to "crosby|stills|nash & young",
                "Tyler,, The Creator," to "tyler|the creator",
                "Solo" to "solo",
                "" to "",
            )
        cases.forEach { (name, key) -> assertEquals(key, CompoundRules.matchKey(name), name) }
    }

    @Test
    fun `fragments of a key are the inverse`() {
        assertEquals(listOf("tyler", "the creator"), CompoundRules.fragmentsOfKey("tyler|the creator"))
        assertEquals(CompoundRules.fragments("A, B"), CompoundRules.fragmentsOfKey(CompoundRules.matchKey("A, B")))
    }

    @Test
    fun `stored keys make the splitter glue the parts`() {
        val compounds = CompoundRules.compoundNames(listOf("tyler|the creator", "single", ""))

        assertEquals(
            listOf("Tyler, The Creator", "Kanye West"),
            ArtistSplitter.split(listOf("Tyler, The Creator, Kanye West"), compounds),
        )
        assertEquals(listOf("Tyler", "The Creator"), ArtistSplitter.split(listOf("Tyler, The Creator")))
    }

    @Test
    fun `the longest rule wins over a shorter one inside it`() {
        val compounds = CompoundRules.compoundNames(listOf("crosby|stills & nash", "crosby|stills|nash & young"))
        val split = ArtistSplitter.split(listOf("Crosby, Stills, Nash & Young"), compounds)
        assertEquals(listOf("Crosby, Stills, Nash & Young"), split)
    }

    @Test
    fun `a name is accepted only when it is the selected artists in order`() {
        val selected = listOf("Tyler", "The Creator")
        assertNull(CompoundRules.problemWith("Tyler, The Creator", selected))
        assertNull(CompoundRules.problemWith("tyler,the creator", selected))
        assertEquals(CompoundNameProblem.NotSelectedParts, CompoundRules.problemWith("The Creator, Tyler", selected))
        assertEquals(CompoundNameProblem.NotSelectedParts, CompoundRules.problemWith("Tyler, The Creators", selected))
        assertEquals(CompoundNameProblem.NotSelectedParts, CompoundRules.problemWith("Tyler, The Creator, X", selected))
        assertEquals(CompoundNameProblem.TooShort, CompoundRules.problemWith("Tyler The Creator", selected))
        assertEquals(CompoundNameProblem.TooShort, CompoundRules.problemWith("", selected))
    }

    @Test
    fun `suggested name follows the selection order`() {
        assertEquals("The Creator, Tyler", CompoundRules.suggestedName(listOf(" The Creator", "Tyler ")))
    }
}
