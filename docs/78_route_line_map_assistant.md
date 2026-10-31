# 78 Dotted route line on Map Assistant

Adds an optional dotted line connecting the pins Map Assistant already plots, so a trip's
day-by-day path becomes visible instead of just a scatter of dots. `docs/54_map_assistant.md`
explicitly said "No route line, pins only" when it shipped — this revisits that call now that
photo timestamps make a meaningful ordering possible.

## Decisions

- **Scope — one continuous path, not per-day segments.** The line runs through the whole
  exported range: `start₁ → photos₁ (time-sorted) → stop₁ → start₂ → photos₂ → stop₂ → …`.
  Rejected per-day-only segments (no line between one day's stop and the next day's start) as
  needlessly fragmented — a trip is one journey, and the line is already dotted/muted so it
  doesn't read as an asserted GPS track.
- **Photo ordering — by EXIF capture time, untimestamped photos skipped from the line.**
  `PhotoLocations.locations(...)` currently only extracts `LatLon` and preserves whatever
  order `images()` returned (folder/filename order), ignoring `ExifReader.captureTime()`
  entirely. It needs to pair each location with its capture time, sort by it, and drop photos
  where `captureTime()` returns null (blank/unparseable `DateTimeOriginal`) **from the line
  only** — they still render as yellow pins, just not connected.
- **Full point set for the line, independent of pin thinning.** `MapPoints.withPhotos()` thins
  photos to `OsmMapRenderer.MAX_PINS` (150) via `sampleEvenly` for legibility/perf of the drawn
  pins. The line should use the full, untrimmed chronological point set — lines are cheap to
  draw (one `Graphics2D.draw(Path2D)` call) and thinning the line would create visible,
  misleading jumps between sampled points.
- **New checkbox, off by default.** Mirrors the existing `📷 Photo Locations from Backpack`
  checkbox pattern (`MapAssistantDialog`, `ButtonData.LEFT`, triggers `changed()` /
  re-render-is-stale on toggle). Suggested label: `〰 Connect with Route Line` or similar; not
  persisted across sessions (same as the photos checkbox).
- **Independent of the photos checkbox.** If route-line is on but photos is off, still draw
  start→stop per day (in range order) — the two checkboxes are orthogonal, not gated on each
  other.
- **Style — neutral grey dotted line, drawn under the pins.** Distinct from all three pin
  colors (`START_COLOR` red, `STOP_COLOR` teal, `PHOTO_COLOR` yellow) so it doesn't compete with
  them or imply it belongs to one kind of point. Drawn before `drawPin` calls so pins sit on top
  of the line at each vertex.

## Implementation sketch

- `map.PhotoLocations`: extend `locations(List<Path> images)` (or add a sibling method) to
  return points paired with `ExifInfo.captureTime()` (needs an accessor if `ExifInfo` doesn't
  already expose one — `ExifReader.captureTime(Metadata)` already computes it), sorted
  ascending, nulls dropped. Decide whether the existing method's signature/return type changes
  or a new type wraps `(LatLon, LocalDateTime)` — keep `MapPoints`'s existing `List<LatLon>`
  photos field for pins; the line needs its own ordered sequence.
- `map.MapPoints`: add a routeLine point list (or compute it in `OsmMapRenderer` from the
  existing per-day start/stop + a newly time-sorted photo list) representing the full ordered
  path. Needs the per-entry grouping (which start/stop belongs to which day) since stops are
  currently only kept for the *last* entry — route-line needs every day's start **and** stop,
  not just the trip-level start list + single stop MapPoints currently exposes. This likely
  means `MapPoints` needs a new field like `List<DayPoints>` (start+stop per entry) for the line,
  independent of the thinned `starts`/`stop` used for pins.
- `map.OsmMapRenderer`: add `drawPath(Graphics2D, List<LatLon>, ...)` using
  `WebMercator.pixelX/pixelY` (same projection as `drawPin`), a dashed `BasicStroke` (e.g.
  `new BasicStroke(2f, CAP_ROUND, JOIN_ROUND, 0, new float[]{4,6}, 0)`), neutral grey
  (e.g. `0x666666` at reduced alpha). Call it before the pin-drawing loop in `renderImage`.
- `ui.dialog.MapAssistantDialog`: new `CheckBox routeLineCheck`, added to the button bar next to
  `photosCheck`, wired into `changed()` the same way, read in the render task alongside
  `wantPhotos`.
- `docs/54_map_assistant.md`: update the "What's plotted" / dialog mockup sections once shipped
  — replace "No route line, pins only" and add the new checkbox to the ASCII mockup and bullet
  list.

## Open questions for implementation time

- Exact checkbox label/emoji (needs a short, clear glyph — `〰` dotted-line-ish, or reuse `🧵`/
  `📍`-adjacent icon; pick during implementation, not a blocking design decision).
- Whether `MapPoints` grows a new field or a parallel helper type carries the per-day
  start+stop+sorted-photos sequence used only for the line — an implementation detail to settle
  against the existing unit tests for `MapPoints`.
