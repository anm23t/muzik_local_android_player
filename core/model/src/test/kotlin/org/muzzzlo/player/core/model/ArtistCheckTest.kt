package org.muzzzlo.player.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArtistCheckTest {
    private fun a(
        id: Long,
        name: String,
        tracks: Int = 1,
    ) = ArtistVariant(id, name, tracks)

    private fun names(groups: List<VariantGroup>) = groups.map { g -> g.variants.map { it.name } }

    @Test
    fun `case accents and punctuation do not matter`() {
        val cases =
            listOf(
                "Beyoncé" to "Beyonce",
                "P!nk" to "Pnk",
                "AC/DC" to "ACDC",
                "Jay-Z" to "Jay Z",
                "Simon & Garfunkel" to "Simon and Garfunkel",
                "  eminem " to "EMINEM",
            )
        cases.forEach { (x, y) ->
            val groups = ArtistCheck.groups(listOf(a(1, x), a(2, y)))
            assertEquals(1, groups.size, "$x / $y")
            assertEquals(2, groups.single().variants.size)
        }
    }

    @Test
    fun `different names stay apart`() {
        assertTrue(ArtistCheck.groups(listOf(a(1, "Anna"), a(2, "Anne"), a(3, "Tyler"))).isEmpty())
    }

    @Test
    fun `names without letters are never grouped`() {
        assertTrue(ArtistCheck.groups(listOf(a(1, "!!!"), a(2, "???"), a(3, ""))).isEmpty())
    }

    @Test
    fun `non latin names are compared too`() {
        assertEquals(1, ArtistCheck.groups(listOf(a(1, "Кино"), a(2, "КИНО"))).size)
        assertTrue(ArtistCheck.groups(listOf(a(1, "Кино"), a(2, "Алиса"))).isEmpty())
    }

    @Test
    fun `most frequent spelling is suggested first then the longer then the lower id`() {
        val byCount = ArtistCheck.groups(listOf(a(1, "Beyoncé", 2), a(2, "Beyonce", 9)))
        assertEquals("Beyonce", byCount.single().suggested.name)

        val byLength = ArtistCheck.groups(listOf(a(1, "Pnk", 3), a(2, "P!nk", 3)))
        assertEquals("P!nk", byLength.single().suggested.name)

        val byId = ArtistCheck.groups(listOf(a(7, "eminem", 3), a(4, "Eminem", 3)))
        assertEquals(4L, byId.single().suggested.artistId)
    }

    @Test
    fun `several groups are ordered by name`() {
        val groups = ArtistCheck.groups(listOf(a(1, "Zed"), a(2, "ZED"), a(3, "Abba"), a(4, "ABBA"), a(5, "Solo")))
        assertEquals(listOf(listOf("Abba", "ABBA"), listOf("Zed", "ZED")), names(groups))
    }

    @Test
    fun `three variants form one group`() {
        val group = ArtistCheck.groups(listOf(a(1, "Sigur Rós"), a(2, "Sigur Ros"), a(3, "sigur ros")))
        assertEquals(3, group.single().variants.size)
    }
}
