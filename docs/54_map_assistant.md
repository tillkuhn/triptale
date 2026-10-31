# 54 Map Assistant

Replaces the "🗺 Trip Map" button and its "🗺 Cartographer" dialog. "Cartographer" sounded too
technical; the button, menu item and dialog title are now all **🗺 Map Assistant**.

## Why

The old dialog always plotted every start point of the whole trip. For a trip that starts on
another continent (flight from home, then the actual journey) the map zooms out to half the
globe and the interesting part is a cluster of overlapping pins (see the Thailand/Malaysia
example: one pin in the Netherlands, eight squeezed together in Southeast Asia).

## Entry points

| Where                                      | Default range                     |
|--------------------------------------------|-----------------------------------|
| `🗺 Map Assistant` button in the entry form | the current day only              |
| My Trips → `🗺 Map Assistant…` (new)        | the whole trip (first to last day) |

Same pattern as Export Tale / Export Diary. If the current day has no saved entry, the button
falls back to the nearest saved entry. A trip without entries still opens the dialog; it just
has nothing to put on screen.

## Dialog

```
🗺 Map Assistant
┌──────────────────────────────────────────────────────────────┐
│ Thailand Malaysia Sumatra Railtrip '26                        │
│ 📍 3 start points · 🏁 stop point · 📷 12 photo locations      │
├──────────────────────────────────────────────────────────────┤
│ From: [Day 4: Bukittinggi ▾]  To: [Day 6: … ▾]               │
│ ┌──────────────────────────────────────────────────────────┐ │
│ │                                                          │ │
│ │     placeholder until "On Screen" is clicked, then       │ │
│ │     the self-rendered OSM tile map (960×640)             │ │
│ │                                                          │ │
│ └──────────────────────────────────────────────────────────┘ │
│ [ ] 📷 Photo Locations from Backpack  Style: [OpenTopoMap ▾]  Zoom: [Normal ▾]  [Close] [🖥 On Screen] │
└──────────────────────────────────────────────────────────────┘
```

- **From / To** combos as in Export Diary (`Day N: Title` labels, capped at 300 px); moving
  one end past the other drags the other along. Range above the map; the photos checkbox, the
  tile style combo and the zoom combo sit on the left of the button bar (`ButtonData.LEFT`, in
  that order), On Screen (`ButtonData.APPLY`) next to Close on the right.
- **On demand:** opening the dialog makes no network request. The map area shows a placeholder
  ("Choose a range, then put it on screen") until **🖥 On Screen** (a Star Trek nod) is
  clicked. Changing the range, the checkbox, the tile style or the zoom level afterwards marks
  the shown map as stale (the placeholder text returns over a dimmed map) until the next On
  Screen. The button is a repeatable action (event filter + `consume()`), it doesn't close the
  dialog.
- **Size:** fixed 960×640 map area, rendered at native 256px tile resolution (no retina
  supersampling — an accepted trade-off of moving off Mapbox, see `docs/61_osm_trip_maps.md`).
- **Saved, not just shown (todo 61):** every "On Screen" render is written to
  `attachments/<year>/<slug>/map-<from>-<to>.png` (`AttachmentsDir.mapFile`), overwriting any
  earlier render of the same range, so exports/WordPress can reuse the file without a separate
  "Save as…" step.
- **Tile style:** `Style: [OpenTopoMap ▾]` combo (`map.TileSource`) — a per-session choice, not
  persisted. Ships OpenTopoMap (nature look: contours, hillshading, forests, paths), OSM
  Standard (plain street map) and CyclOSM (cycling-oriented, flatter nature look); overlay-only
  or API-key sources (Waymarked Trails, Thunderforest) are future todos.
- **Zoom:** `Zoom: [Close|Normal|Wide|Very wide]` combo, defaulting to Normal
  (`OsmMapRenderer.DEFAULT_ZOOM`). Each level is a margin fraction of the image's smallest side
  (2% / 12% / 28% / 45%, see `OsmMapRenderer.ZoomLevel`) used by the fit-to-bounds math
  (`WebMercator.fitZoom`) for multiple points, or an explicit zoom (16 / 12 / 9 / 5) for the
  single-point case, where fitting a zero-size bounding box would otherwise zoom all the way to
  the tile source's max zoom.

## What's plotted

| Kind  | Source                                              | Pin                          |
|-------|-----------------------------------------------------|------------------------------|
| Start | `start_lat`/`start_lon` of every entry in range      | red filled circle            |
| Stop  | `stop_lat`/`stop_lon` of the **last** entry in range | teal filled circle           |
| Photo | EXIF GPS of backpack images of the days in range     | yellow filled circle         |

- **Stop:** only the last day's stop, as before — a daily stop is usually the next day's
  start. With a single-day range this gives exactly start → stop for that day.
- **No route line**, pins only.
- **Photos:** off by default (`📷 Photo Locations from Backpack` checkbox, not remembered). All
  backpack images (`attachments/<year>/<slug>/<date>/`) that pass the base filter and carry
  EXIF GPS. No photo-library scan. The fave filter does **not** apply: the backpack is already
  a curated pick, and its copies (e.g. `…_mini.jpg`) rarely match a pattern like `*+.jpg`
  (originally faves only, changed 2026-10-12).
- **Nothing to plot:** whatever points exist are rendered, photos alone are enough. With no
  points at all the placeholder says "Nothing to put on screen for this range", plus a hint to
  tick the checkbox when it's unticked and the range has backpack images (counted without reading EXIF,
  so they may turn out to have no GPS).

## Too many pins

Unlike Mapbox's static-image API, there's no URL length limit here — the cap
(`OsmMapRenderer.MAX_PINS`, 150) is purely to keep the image legible and rendering fast. Day
pins (starts + stop) are always kept. Photo pins are thinned to the remaining budget by
sampling evenly across the date-ordered list (so every part of the range keeps some coverage),
and the header says so: `📷 60 of 212 photo locations`.

## Code

- `map.MapPoints` (package `map`, no JavaFX): the points of a range — starts, last stop,
  and optional photo locations — and thins photos to a budget. Unit-tested.
- `map.PhotoLocations`: the backpack images of a date range (`images`, no EXIF reads) and their
  EXIF GPS locations (`ImpressionsService` + `ExifReader`). Created in `MainController`, passed
  to the dialog.
- `map.TileSource` (todo 61): a tile server's URL template, attribution and max zoom —
  `OPEN_TOPO_MAP` only for now.
- `map.WebMercator` (todo 61): pure Web Mercator slippy-map tile math (`pixelX`/`pixelY`,
  `fitZoom`), no I/O, unit-tested independently of rendering.
- `map.OsmTileClient` (todo 61): fetches tiles over `java.net.http`, cached on disk under
  `<data-dir>/.tile-cache/<source>/<z>/<x>/<y>.png`; identifying User-Agent per OSM's tile usage
  policy.
- `map.OsmMapRenderer` (todo 61): stitches the tiles and draws pins + attribution with AWT
  `Graphics2D`, saves PNG via `ImageIO`. `ZoomLevel` (Close/Normal/Wide/Very wide) controls the
  margin fraction / single-point zoom, same shape as the old `MapboxService.ZoomLevel` it
  replaced. See `docs/61_osm_trip_maps.md` for why Mapbox was replaced and the design decisions.
- `ui.dialog.MapAssistantDialog`: owns the range combos, checkbox, tile style/zoom combos,
  placeholder and On Screen button; loads entries itself (like `ExportDiaryDialog`); the render
  (tile fetch + stitch + save) runs in a background `Task`, off the FX thread.
- `MainController`: `onShowTripMap` → `onMapAssistant` (current day) plus
  `onMapAssistantTrip` (whole trip, menu).

