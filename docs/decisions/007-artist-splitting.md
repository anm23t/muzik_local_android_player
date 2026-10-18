# 007. Artist splitting: by `;`, otherwise by `,`, with compound names as exceptions

Status: accepted

- Several values in the file (Vorbis/FLAC, ID3v2.4) are taken as they are.
- A single string with `;` is split only by `;`.
- A single string without `;` is split by `,`, then adjacent fragments that match an entry in the
  compound names table (`artist_compound`, such as "Tyler, The Creator") are joined back.
- No other separators (`&`, `/`, `feat.`, `and`, `x`), so names like "AC/DC" stay whole.
  Anything else is split by hand in the tag editor.

The compound names table holds a built-in list (`compound_artists.json`, entries can be disabled)
and user entries. Separators are not configurable. Details in ARCHITECTURE.md, section 4.
