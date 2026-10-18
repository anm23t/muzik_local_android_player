# 008. Rating 0.0-10.0 in steps of 0.1, stored as integer tenths

Status: accepted

A track rating is a number from 0.0 to 10.0 with one decimal place. It is stored in
`track.rating_tenths` as an integer from 0 to 100 (67 means 6.7); NULL means not rated.
Integers avoid floating point rounding in sorting, filters and export. The rating lives only in the
database and the JSON backup, it is never written into the audio files.
