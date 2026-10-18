# 002. Artist is an entity, linked to tracks many-to-many with order

Status: accepted

Each artist is a separate `artist` row. Tracks and albums link to artists through `track_artist` and
`album_artist` with a `position` column that keeps the order from the tag. This lets one track have
several artists, counts every artist separately in statistics and gives each artist its own page,
photo and aliases. The raw tag values are kept in `track.artist_tag_values`, so the split can be
recomputed without reading the files (DATABASE.md).
