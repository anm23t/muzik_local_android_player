package org.muzzzlo.player.core.designsystem.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ArtistTapTest {
    private val a = ArtistChoice(1, "A", "a.jpg")
    private val b = ArtistChoice(2, "B")
    private val c = ArtistChoice(3, "C")

    @Test
    fun `no artists do nothing`() = assertEquals(ArtistTap.Nothing, ArtistTap.of(emptyList()))

    @Test
    fun `one artist opens the card at once`() = assertEquals(ArtistTap.Open(a), ArtistTap.of(listOf(a)))

    @Test
    fun `several artists ask which one in tag order`() =
        assertEquals(ArtistTap.Choose(listOf(b, a, c)), ArtistTap.of(listOf(b, a, c)))

    @Test
    fun `two artists with the same name but different ids still ask`() {
        val twin = ArtistChoice(4, "A")
        assertEquals(ArtistTap.Choose(listOf(a, twin)), ArtistTap.of(listOf(a, twin)))
    }
}
