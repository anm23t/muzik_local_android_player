package org.muzzzlo.player.core.library

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.muzzzlo.player.core.model.CoverGradients
import org.muzzzlo.player.core.model.GeneratedInput
import org.muzzzlo.player.core.model.GeneratedKind

class GeneratedSetBuilderTest {
    private val builder = GeneratedSetBuilder()
    private val all = GeneratedKind.entries

    @Test
    fun `the first run ever builds every kind`() {
        assertEquals(all, builder.staleKinds(emptyMap(), emptySet(), today = 10))
    }

    @Test
    fun `sets of today are kept during the day`() {
        val stored = all.associate { it.key to 10L }
        assertEquals(emptyList<GeneratedKind>(), builder.staleKinds(stored, emptySet(), today = 10))
    }

    @Test
    fun `a new day rebuilds every set`() {
        val stored = all.associate { it.key to 10L }
        assertEquals(all, builder.staleKinds(stored, emptySet(), today = 11))
    }

    @Test
    fun `an empty set of today is made again`() {
        val stored = all.associate { it.key to 10L }
        val empty = setOf(GeneratedKind.BEST_RATED.key)
        assertEquals(listOf(GeneratedKind.BEST_RATED), builder.staleKinds(stored, empty, today = 10))
    }

    @Test
    fun `a built set carries the gradient of its kind`() {
        val input = GeneratedInput(emptyList(), emptyMap(), nowMs = 0, epochDay = 1)
        all.forEach { assertEquals(CoverGradients.forKind(it, 1), builder.build(it, input).gradient) }
    }
}
