# 003. No separate roles (composer, producer, lyricist)

Status: accepted

The app knows only the track artist and the album artist. Composer, producer, lyricist and similar
roles are deliberately not supported: they are not read into the database, not shown and not edited.
This keeps the artist model, the tag editor and the statistics simple. Such fields stay untouched
in the files when tags are written.
