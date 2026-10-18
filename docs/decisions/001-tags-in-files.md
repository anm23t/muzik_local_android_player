# 001. Tags live in the files, the database is a cache

Status: accepted

Title, artists, album, cover and lyrics are read from the audio files and written back to them.
The database only caches these values for search, sorting and lists, and can be rebuilt by a rescan.
Data that does not belong in the files (statistics, ratings, favourites, artist aliases, compound names)
lives only in the database and goes into the JSON backup. Writing to a file always goes through the
safe operation described in ARCHITECTURE.md, section 5.
