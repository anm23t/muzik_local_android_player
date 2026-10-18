package org.muzzzlo.player.core.playback

import org.muzzzlo.player.core.model.RepeatMode

/** Playback state kept between app runs. */
internal data class SavedPlayback(
    val trackIds: List<Long>,
    val index: Int,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: RepeatMode,
)

internal fun encodeIds(ids: List<Long>): String = ids.joinToString(",")

/** Parses [encodeIds] output, broken entries are skipped. */
internal fun decodeIds(text: String): List<Long> = text.split(',').mapNotNull { it.trim().toLongOrNull() }

/**
 * Finds where the saved current track is in the restored queue.
 * Missing tracks shift positions, so the index is found by the track id.
 * Returns null when the saved track is gone, the caller then starts from the first item.
 */
internal fun restoredIndex(
    saved: SavedPlayback,
    restoredIds: List<Long>,
): Int? {
    val savedId = saved.trackIds.getOrNull(saved.index) ?: return null
    return restoredIds.indexOf(savedId).takeIf { it >= 0 }
}
