package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArtistEditTest {
    @Test
    fun `rename keeps the position`() {
        val edit = ArtistEdit.rename("b", "Bee")
        assertEquals(listOf("A", "Bee", "C"), edit.applyTo(listOf("A", "B", "C")))
    }

    @Test
    fun `split puts the parts in the place of the artist`() {
        val edit = ArtistEdit.split("Kanye West & JAY-Z", listOf("Kanye West", "JAY-Z"))
        assertEquals(listOf("X", "Kanye West", "JAY-Z", "Y"), edit.applyTo(listOf("X", "Kanye West & JAY-Z", "Y")))
    }

    @Test
    fun `merge into an artist already on the track does not repeat it`() {
        val edit = ArtistEdit.merge("Jay Z", "JAY-Z")
        assertEquals(listOf("JAY-Z"), edit.applyTo(listOf("JAY-Z", "Jay Z")))
        assertEquals(listOf("JAY-Z", "Kanye"), edit.applyTo(listOf("Jay Z", "JAY-Z", "Kanye")))
    }

    @Test
    fun `split part that is already on the track keeps its first place`() {
        val edit = ArtistEdit.split("A & B", listOf("A", "B"))
        assertEquals(listOf("B", "A"), edit.applyTo(listOf("B", "A & B")))
    }

    @Test
    fun `target is found by key`() {
        assertEquals(listOf("New"), ArtistEdit.rename("  OLD name ", "New").applyTo(listOf("old   Name")))
    }

    @Test
    fun `list without the target is unchanged`() {
        val names = listOf("A", "B")
        assertEquals(names, ArtistEdit.rename("Z", "Y").applyTo(names))
        assertFalse(ArtistEdit.rename("Z", "Y").changes(names))
    }

    @Test
    fun `replacement is cleaned`() {
        val edit = ArtistEdit.split("X", listOf(" A ", "", "a", "B"))
        assertEquals(listOf("A", "B"), edit.replacement)
    }

    @Test
    fun `rename to the same spelling is no change but a case fix is`() {
        assertFalse(ArtistEdit.rename("Abba", "Abba").changes(listOf("Abba")))
        assertTrue(ArtistEdit.rename("abba", "ABBA").changes(listOf("abba")))
    }

    @Test
    fun `empty replacement is refused`() {
        assertThrows(IllegalArgumentException::class.java) { ArtistEdit.split("A", listOf(" ", "")) }
    }
}
