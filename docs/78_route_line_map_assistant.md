# 78 Dotted route line and photo pin thinning on Map Assistant

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
- **EXIF time is free: reuse the exif cache, no second read (decided 2026-11-04).** Sorting by
  filename was considered as the cheap option (phone/camera files are usually timestamp-prefixed),
  but it isn't actually cheaper: `locations()` already calls `ExifReader.read()` per photo for
  lat/lon, and the `.triptale/cache/exif/<path>.properties` entry (todo 80) has carried
  `captureTime` since the cache was introduced, so there are no stale entries without it. The
  sort uses the same `ExifInfo` that supplies the location: one cache hit, zero extra I/O. EXIF
  time also works for files that aren't timestamp-named (`IMG_1234.jpg`, renamed, mixed cameras).
  Filename order stays as the **tie-breaker**: `ImpressionsService.images()` already returns each
  day's images sorted by filename, so a stable sort by `captureTime` keeps filename order for
  photos with the same second (bursts). Sort per day (the day's start/stop frame each block), not
  across the whole range, so a wrong camera clock can't pull a photo into another day.
- **Line points: full chronological set, collapsed in pixel space — not `sampleEvenly`.**
  `MapPoints.withPhotos()` thins photos to `OsmMapRenderer.MAX_PINS` (150) via `sampleEvenly`;
  feeding that into the line would create visible, misleading jumps between sampled points. The
  line starts from the full time-sorted sequence and only drops a point that lies within the
  pin-overlap threshold (see "Photo pin thinning" below) of the **previous kept line point** —
  never of any earlier one, so a later return to the same spot (hotel → day out → hotel) keeps
  its return leg. That removes the sub-pin-sized segments that wouldn't be visible anyway.
- **Photo pin thinning by pixel distance (added 2026-11-04).** See the section below.
- **Pin restyling: one red family + size hierarchy (added 2026-11-04).** See "Pin styling" below.
- **New checkbox, off by default.** Mirrors the existing `📷 Photo Locations from Backpack`
  checkbox pattern (`MapAssistantDialog`, `ButtonData.LEFT`, triggers `changed()` /
  re-render-is-stale on toggle). Suggested label: `〰 Connect with Route Line` or similar.
  Remembered across sessions since all options are (see "As implemented").
- **Independent of the photos checkbox.** If route-line is on but photos is off, still draw
  start→stop per day (in range order) — the two checkboxes are orthogonal, not gated on each
  other.
- **Style — neutral grey dotted line, drawn under the pins.** Distinct from the red-family pin
  colors (see "Pin styling") so it doesn't compete with them or imply it belongs to one kind of
  point; a red line would fight the pins. Drawn before `drawPin` calls so pins sit on top
  of the line at each vertex.

## Pin styling

The original red/teal/yellow pins (`OsmMapRenderer` `START_COLOR`/`STOP_COLOR`/`PHOTO_COLOR`)
are three equally loud hues, and yellow is the brightest of them, so the most numerous and
least important pins (photos) grab the eye first and the map reads as a colour mix.

- **Hierarchy by lightness and size, not hue.** One red family plus black. Using lightness
  instead of different hues also survives every type of colour blindness. Dark fills give
  better contrast on the (mostly light) tile styles; red also reads well on blue sea and green
  terrain.

  | Pin | Fill | Radius | Role |
  |---|---|---|---|
  | Start (one per day) | dark red `#B71C1C` | 6 | main marker, carries the trip |
  | Stop (last entry only) | near-black `#212121` | 6 | end point; black inside the white ring reads as a target |
  | Photo | light, slightly muted red `#E57373` | 5, thinner outline | same family, recedes, smaller clusters |

- **Size makes the start/photo split reliable.** Dark vs light red alone is clear side by side
  but weaker for a lone pin; the smaller photo pin also covers less of OSM Carto's salmon/pink
  roads, which a light red could otherwise blend into (the dark outline helps too).
- **Day pins smaller than before (radius 8 → 6, outer 11 → 9 px):** first look on screen
  showed the big ones hiding the town names they sit on; still a step bigger than photos.
- **Draw order unchanged:** photos, then starts, then stop on top.
- **`drawPin` takes a radius** (or a small pin-style record: fill, radius, outline width)
  instead of the fixed `PIN_RADIUS`.
- **Exact hex values to be tuned on screen** — an agent can't view the rendered map, so the
  user checks and the values get adjusted from that.

## Photo pin thinning

Photos are often taken within metres of each other, so on a trip-wide map their pins pile up into
yellow clusters (and, with the route line, into tangles of tiny segments). Thinning removes pins
that would be hidden under another pin anyway.

- **Threshold in pixels, not km.** The map is a static PNG at one zoom chosen by
  `OsmMapRenderer.fitZoom` *before* any pin is drawn. A pixel threshold at that zoom is
  automatically a zoom-dependent ground distance (several km on Very wide, metres on Close) — no
  km table per zoom level needed. With the smaller photo pins (see "Pin styling") the threshold
  is the sum of the two pins' outer radii: photo vs photo ≈ 14 px, photo vs start/stop ≈ 16 px,
  so more lone photos survive than with a flat one-full-pin-diameter (22 px) rule; tune at
  implementation time.
- **Start/stop pins are never thinned.** They're placed first; photo pins then go greedily
  (chronological order once todo 78's sort exists, else current order) and a photo pin is
  dropped if it lies within the threshold of **any** already kept pin, start/stop included.
  Pins dedupe globally — unlike the line, which only collapses consecutive points.
- **Happens in `OsmMapRenderer`, after `fitZoom`**, because it needs the zoom. Zoom and centre are
  still computed from the full point set; dropped pins are within one pin width of a kept one,
  so the framing doesn't shift.
- **Replaces `sampleEvenly` as the main reduction.** `sampleEvenly` thins before the zoom is known
  and ignores geography (can drop a lone photo while keeping ten stacked in one café).
  `MAX_PINS` stays only as a safety cap applied after pixel thinning. `MapPoints` then carries
  the full photo list; `MapAssistantDialog` (currently `withPhotos(locations, MAX_PINS - …)`)
  changes accordingly.
- **Always on, no checkbox** — the dropped pins are visually covered, so there's nothing to opt
  out of.
- **Status text:** the dialog's photo count should say how many pins were actually drawn, e.g.
  "42 of 180 photos shown" — the renderer needs to report the kept count back (return value or
  a small result record next to the image).
- **Tests:** pure pixel-space helper (points + zoom + topLeft → kept points), unit-testable
  without tiles: overlapping photos collapse, a photo on top of a start pin is dropped, far
  apart photos all survive, consecutive-only collapse for the line keeps an A→B→A return.

## Implementation sketch

- `map.PhotoLocations`: extend `locations(List<Path> images)` (or add a sibling method) to
  return points paired with `ExifInfo.captureTime()` (already a record component, and cached),
  stable-sorted ascending per day, nulls dropped from the line. Must keep going through
  `ExifReader.read()` (cached), never `ImageMetadataReader` directly. Decide whether the existing method's signature/return type changes
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

## As implemented

- **Checkbox:** `〰 Route Line`, off by default, but restored from a saved map: `TripMapSettings`
  gained a `route` flag (`triptale:route` PNG text; absent in older maps = off).
- **All options remembered (added 2026-11-04):** the old `lastMapStyle` (style only) in
  `.triptale/state.yml` became `lastMapOptions`, a nested YAML map (`style`, `zoom`, `format`,
  `photos`, `route`) written on close (`MarkdownStore.saveLastMapOptions`, plain strings since
  `storage` must not depend on `map`; `map.MapOptions` parses them, each unknown/missing value
  falling back on its own). A saved map's own settings still win when it's shown. A pre-78
  `lastMapStyle` is read once as the style and removed on the next save. A `;`-packed string
  was rejected: state.yml is YAML already, a nested map needs no parser.
- **`PhotoLocations.photosByDay(trip, from, to)`** returns `Map<LocalDate, List<Photo>>`,
  `Photo(LatLon location, LocalDateTime captureTime)`, each day stable-sorted by
  `byCaptureTime` (untimed photos last). `locations(...)` stays for the plain list.
- **`MapPoints`** grew a `route` field and `of(entries, photosByDay, withRoute)`; `withPhotos` /
  up-front `sampleEvenly` thinning is gone. `all()` (used for framing) includes the route, so
  intermediate days' stops, which have no pin, stay inside the map.
- **`OsmMapRenderer`**: `PinStyle(fill, radius, outline)`, `thinPhotos(dayPins, photos, z)` and
  `collapseRoute(route, z)` work in world pixels at zoom `z` (no topLeft needed, distances
  don't depend on it). Thresholds come from `PinStyle.outerRadius()`: photo vs photo 14 px,
  photo vs day pin 16 px, route collapse 14 px. Route stroke: 2 px, dash 5/5, `#444` at
  alpha 150 (first tried 2.5 px, 6/6, alpha 200: too heavy over labels). A white line with a
  dark casing (Polarsteps-like) was considered; plain white vanishes on the light tile styles. `render(...)` now returns the plotted `MapPoints` and takes the PNG text as a
  function of them, so the saved `photosShown` count is the one actually drawn.
