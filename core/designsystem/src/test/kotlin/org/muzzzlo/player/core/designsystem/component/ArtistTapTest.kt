package org.muzzzlo.player.core.designsystem.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ArtistTapTest {
    @Test
    fun `no artists do nothing`() = assertEquals(ArtistTap.Nothing, ArtistTap.of(emptyList()))

    @Test
    fun `one artist opens the card at once`() = assertEquals(ArtistTap.Open("A"), ArtistTap.of(listOf("A")))

    @Test
    fun `several artists ask which one in tag order`() =
        assertEquals(ArtistTap.Choose(listOf("B", "A", "C")), ArtistTap.of(listOf("B", "A", "C")))

    @Test
    fun `two artists with the same name still ask`() =
        assertEquals(ArtistTap.Choose(listOf("A", "A")), ArtistTap.of(listOf("A", "A")))
}
