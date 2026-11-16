package org.muzzzlo.player.core.library

import org.muzzzlo.player.core.model.CoverGradients
import org.muzzzlo.player.core.model.GeneratedInput
import org.muzzzlo.player.core.model.GeneratedKind
import org.muzzzlo.player.core.model.GeneratedSets
import javax.inject.Inject

/** One built set: its tracks in order and its gradient cover. */
internal data class BuiltSet(
    val trackIds: List<Long>,
    val gradient: Int,
)

/** Picks the rules and the gradient of each kind of generated playlist (task 2.5.7). */
internal class GeneratedSetBuilder
    @Inject
    constructor() {
        /** The kinds built every day, in the order of the home screen. */
        val kinds: List<GeneratedKind> = GeneratedKind.entries

        fun build(
            kind: GeneratedKind,
            input: GeneratedInput,
        ): BuiltSet = BuiltSet(GeneratedSets.build(kind, input), CoverGradients.forKind(kind))

        /**
         * The kinds to build now: those without a set of [today] ([storedDays] maps a kind key to the day of its
         * set) and those whose set of today came out empty ([emptyKinds]), because the first run of a day may build
         * before the first scan ends. A full set of today is kept, so the set does not change during the day.
         */
        fun staleKinds(
            storedDays: Map<String, Long>,
            emptyKinds: Set<String>,
            today: Long,
        ): List<GeneratedKind> = kinds.filter { storedDays[it.key] != today || it.key in emptyKinds }

        companion object {
            const val ON_REPEAT_MS = GeneratedSets.ON_REPEAT_DAYS * 24L * 60 * 60 * 1000
        }
    }
