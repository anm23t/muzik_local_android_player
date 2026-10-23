package org.muzzzlo.player.core.stats

import org.muzzzlo.player.core.database.dao.ArtistPlaysRow
import org.muzzzlo.player.core.database.dao.OrphanPlayRow
import org.muzzzlo.player.core.model.NameNormalizer
import org.muzzzlo.player.core.model.TopArtist

private val quoted = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
private val escape = Regex("\\\\(.)")

/** The names of a JSON array of strings (`listen_event.artists_snapshot`); anything else gives an empty list. */
internal fun snapshotNames(json: String): List<String> {
    val text = json.trim()
    if (!text.startsWith("[") || !text.endsWith("]")) return emptyList()
    return quoted.findAll(text).map { match ->
        escape.replace(match.groupValues[1]) { esc ->
            when (val c = esc.groupValues[1].single()) {
                'n' -> "\n"
                'r' -> "\r"
                't' -> "\t"
                else -> c.toString()
            }
        }
    }.toList()
}

/**
 * Joins the artists of tracks that are in the library ([linked]) with the artists of deleted tracks ([orphans], known
 * only by name). An orphan name equal to a library artist by [NameNormalizer.key] adds to that artist, so the same
 * artist is one row. Several names of one snapshot count one listen each, a name repeated in one snapshot counts once.
 * The order is plays, played time, name.
 */
internal fun mergeTopArtists(
    linked: List<ArtistPlaysRow>,
    orphans: List<OrphanPlayRow>,
    limit: Int,
): List<TopArtist> {
    val byKey = LinkedHashMap<String, TopArtist>()
    for (row in linked) {
        val key = NameNormalizer.key(row.name)
        val known = byKey[key]
        byKey[key] =
            if (known == null) {
                TopArtist(row.artistId, row.name, row.plays, row.playedMs, row.photoPath)
            } else {
                known.copy(plays = known.plays + row.plays, playedMs = known.playedMs + row.playedMs)
            }
    }
    for (row in orphans) {
        val names = snapshotNames(row.artistsSnapshot).distinctBy { NameNormalizer.key(it) }
        for (name in names) {
            val key = NameNormalizer.key(name)
            if (key.isEmpty()) continue
            val known = byKey[key]
            byKey[key] =
                if (known == null) {
                    TopArtist(null, name, 1, row.playedMs)
                } else {
                    known.copy(plays = known.plays + 1, playedMs = known.playedMs + row.playedMs)
                }
        }
    }
    return byKey.values
        .sortedWith(
            compareByDescending<TopArtist> { it.plays }
                .thenByDescending { it.playedMs }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
        ).take(limit)
}
