package org.muzzzlo.player.core.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.model.CompoundNames
import org.muzzzlo.player.core.model.CompoundRules

class CompoundRecalculationTest {
    private val none = CompoundNames.None
    private val tyler = CompoundRules.compoundNames(listOf("tyler|the creator"))

    private fun changed(
        raw: Map<Long, List<String>>,
        before: CompoundNames,
        after: CompoundNames,
    ) = CompoundRecalculation.changedTracks(raw, before, after)

    @Test
    fun `adding a rule glues the parts of the tracks that hold the name`() {
        val raw =
            mapOf(
                1L to listOf("Tyler, The Creator"),
                2L to listOf("Tyler, The Creator, Kanye West"),
                3L to listOf("Kanye West, Pusha T"),
                4L to listOf("Tyler, The Creator; Kanye West"),
                5L to listOf("Tyler", "The Creator"),
                6L to listOf("Tyler"),
            )

        assertEquals(
            listOf(
                RecutTrack(1, listOf("Tyler, The Creator")),
                RecutTrack(2, listOf("Tyler, The Creator", "Kanye West")),
            ),
            changed(raw, none, tyler),
        )
    }

    @Test
    fun `removing a rule splits the name again`() {
        val raw = mapOf(1L to listOf("Tyler, The Creator, Kanye West"))
        assertEquals(listOf(RecutTrack(1, listOf("Tyler", "The Creator", "Kanye West"))), changed(raw, tyler, none))
    }

    @Test
    fun `nothing changes when the rule matches no track or the rules are the same`() {
        val raw = mapOf(1L to listOf("Kanye West, Pusha T"), 2L to emptyList())
        assertEquals(emptyList<RecutTrack>(), changed(raw, none, tyler))
        assertEquals(emptyList<RecutTrack>(), changed(mapOf(1L to listOf("Tyler, The Creator")), tyler, tyler))
        assertEquals(emptyList<RecutTrack>(), changed(emptyMap(), none, tyler))
    }
}
