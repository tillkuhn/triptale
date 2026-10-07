# ADR-0001: Display coordinates in DDM, not DMS or raw decimal degrees

Status: Accepted

## Context

Entries can record a start/end coordinate pair (`start_lat`/`start_lon`, `stop_lat`/
`stop_lon`), stored as plain decimal `Double`s in frontmatter (see
[docs/15_start_point_coordinates.md](../15_start_point_coordinates.md)). Decimal degrees
(e.g. `48.8537`) are the right *storage* format — compact, unambiguous, trivial to parse and
to feed to a map URL — but they're not how most people read a coordinate back. We needed a
human-facing display format for the coordinates popup/dialog preview, the Google Maps
deep-link label, and the trip-diary export.

The realistic candidates were:

- **Decimal degrees (DD)** — what's already stored; technically sufficient but unfamiliar to
  read at a glance (is `48.8537` close to `48.8528`? not obvious without doing the subtraction).
- **Degrees, Decimal Minutes (DDM)** — e.g. `48° 51.2' N`. The format most car/marine GPS units
  and hiking GPS devices default to.
- **Degrees, Minutes, Seconds (DMS)** — e.g. `48° 51' 14" N`. Common in surveying, aviation,
  and some older atlases.

## Decision

Display coordinates in **DDM**, one decimal minute of precision (`Coordinates.toDdm`,
`%.1f` minutes, `Locale.ROOT`, hemisphere letter from sign). Storage stays decimal degrees;
DDM is purely a read-only rendering, computed live from whatever is in the decimal lat/lon
fields.

Reasoning:

- DDM matches the format most consumer GPS devices show, so it reads as familiar rather than
  technical.
- One decimal minute (~1.8 m at the equator) is already finer than the precision a casual
  trip-log entry actually has; DMS's extra seconds field implies precision the data doesn't
  back up and adds a field to parse/misread for no benefit at this scale.
- DD alone is fine for storage/computation but doesn't read naturally as "where" to a human —
  it's the thing DDM is derived from, not a replacement for it.

No support was added for DMS, MGRS, Plus Codes, or any other format — see
[docs/15_start_point_coordinates.md](../15_start_point_coordinates.md)'s non-goals.

## Consequences

- Easier: coordinates in the UI and in exported diary entries read the way a GPS device would
  show them; no seconds-precision math to get wrong.
- Harder: if a future use case genuinely needs arcsecond precision (unlikely for a travel
  diary), DMS support would need to be added from scratch — nothing here anticipates it.
- `Coordinates.toDdm` is the only formatter; any new coordinate-consuming surface (e.g. a
  future map embed) should reuse it rather than growing a second ad hoc format.
