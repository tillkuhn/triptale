# 61 Self-rendered trip maps from OSM tiles

## Goal

Trip maps that can be **saved and published on the blog** (WordPress export, todo 60), and
ideally a nature-oriented look (hiking/cycling) rather than a street map. Today Map Assistant
(todo 54, `ui/dialog/MapAssistantDialog`) shows a Mapbox static map loaded straight from the
URL built by `ui.MapboxService.staticImageUrl`; nothing is kept.

## Why not Mapbox

Mapbox Product Terms (2026-07-21) rule out publishing a saved Mapbox image, attribution or not:

- 1.9 (v): by default no "export, download, cache or store" of Licensed Map Content; static map
  images are Licensed Map Content (3.32).
- 2.8.1: only an on-device cache of at most 30 days, and no distributing maps "by using a
  screenshot or other static image instead of accessing Licensed Map Content directly from the
  Mapping APIs".
- 1.7: saved PNG/JPG/PDF count as printed media, allowed only for app promotion, 100 copies via
  Studio, or purchased print rights.

Hotlinking the API URL in the blog would be allowed, but exposes a token to every reader and
bills each page view. Rejected: the blog goal needs images we may store.

## Decision: stitch OSM tiles and draw the pins ourselves

Rendered OSM tiles are CC BY-SA (data ODbL): storing and publishing is fine with attribution.
Tile servers' usage policies are about load (proper User-Agent, cache tiles, no bulk
download); one map per user action with a disk cache stays well within them.

Plain JDK, no new dependency, in the `map` package (AWT `java.desktop` is not JavaFX, so the
package boundary rule holds):

1. Fit: bounding box of the pins → highest zoom where they fit the target size with a margin
   (Web Mercator slippy-map math).
2. Fetch the covering 256 px tiles via `java.net.http`, cached on disk (OSM asks ≥ 7 days).
3. Draw on a `BufferedImage` with `Graphics2D` (antialiased): tiles, optional line through the
   days, start/stop/photo pins as shapes or bundled PNG icons (AWT can't draw colour emoji
   reliably), attribution of the tile source bottom right.
4. `ImageIO.write(img, "png", file)`.

Map Assistant then shows the same image. Mapbox is removed entirely as part of this todo:
`MapboxService`, the `mapboxToken` setting/field, and its Edit Settings UI go away; "On Screen"
switches straight to the OSM renderer. There's a feature branch for this, so reverting is cheap
if that turns out to be premature — no fallback/dual-renderer path is kept around for it.

## Tile sources

Each source is a URL template plus its own attribution string; check each one's usage policy
before adding it.

| Source | Look | Key | Attribution |
|---|---|---|---|
| OpenTopoMap | contours, hillshading, forests, paths; max zoom 17 | no | © OpenStreetMap contributors, SRTM · style © OpenTopoMap (CC-BY-SA) |
| CyclOSM | cycling: bike infrastructure, surfaces, hillshading | no | © OpenStreetMap contributors · CyclOSM |
| Waymarked Trails | transparent hiking/cycling route overlay on a base layer | no | CC BY-SA |
| OSM standard | general street map | no | © OpenStreetMap contributors |
| Thunderforest Outdoors / OpenCycleMap | polished outdoor styles | free hobby key | CC-BY-SA 4.0, storing/publishing OK — see Decisions |

OpenTopoMap is the preferred default for a nature look.

## Decisions

- **Save destination**: auto-saved to `attachments/map-<from>-<to>.png` in the trip's
  attachments folder on every "On Screen" click (overwriting the same file per range) —
  synced by `BucketSyncer`, directly reusable by exports/WordPress without a separate "Save
  as…" step.
- **Tile source scope**: a dropdown in Map Assistant, remembered across sessions (not per-trip)
  via `.state.yml`'s `lastMapStyle` (`MarkdownStore.saveLastMapStyle`/`loadLastMapStyle`, a plain
  string id — `storage` must not depend on `map`'s `TileSource`), not `settings.yml` or per-trip
  frontmatter. Falls back to OpenTopoMap if nothing saved yet or the saved id no longer matches
  a known source. Wires up **OpenTopoMap, OSM Standard and CyclOSM**
  (all no-key, single-layer raster sources); Waymarked Trails (overlay-only) is a separate future
  todo once that extra plumbing is worth it.
- **Thunderforest (key-gated sources, not yet implemented)**: Thunderforest's terms (checked
  2026-11, thunderforest.com/terms) license individual map images under **CC-BY-SA 4.0** — unlike
  Mapbox, storing and publishing tiles is explicitly fine. Required attribution text:
  `Maps © Thunderforest, Data © OpenStreetMap contributors` with working links to
  thunderforest.com and openstreetmap.org/copyright (a plain-text variant exists for printed
  media). Their "no caching proxies" clause is aimed at redistribution services, not a single
  user's local disk cache — our existing `.tile-cache/<source>/<z>/<x>/<y>.png` pattern should be
  fine, same as the on-device caching they explicitly allow. Free plan is "at our discretion" and
  can be throttled/withdrawn — an accepted risk, not a blocker.
  - **Scope**: wire up both **Outdoors** and **OpenCycleMap** as two more `TileSource` entries
    sharing one API key.
  - **Key storage**: a new `thunderforestApiKey` field in `AppSettings`/`settings.yml`, same flat
    string/default-`""` pattern as `telegramBotToken`; edited via a masked `PasswordField` in
    `EditSettingsDialog`, same pattern as the Telegram token field.
  - **No-key behavior**: hide the Thunderforest-based options from the Map Assistant dropdown
    entirely when `thunderforestApiKey` is blank, rather than showing them disabled.
  - **Plumbing needed**: `TileSource.tileUrl()` has no access to runtime settings today — the key
    needs to be threaded through `OsmTileClient`'s constructor/call site (built in
    `MapAssistantDialog`, which currently has no `AppSettings`/`SettingsStore` dependency) rather
    than baked into the enum. Appending `?apikey=...` to the URL is otherwise a one-line change
    per new `TileSource` constant; `OsmTileClient` treats the URL as opaque so needs no changes.
  - **Free tier quota (150,000 tiles/month)**: plenty for personal use *because of* the existing
    disk cache (`OsmTileClient.tile()`, never evicted) — a tile is only fetched once per
    `source/z/x/y` ever, so re-rendering the same trip/zoom costs 0 fresh requests. A single
    960×640 render is ~12–20 tiles worst-case (first view of a new region/zoom); heavy real-world
    use lands in the hundreds to low thousands per month, nowhere near the quota.
- **Attribution**: drawn into the PNG itself (bottom-right), not duplicated as export text —
  exports already end with an OSM place credit (todo 58), this is the map's own credit line.
- **Pins**: plain vector shapes via `Graphics2D` (antialiased), same colors as today's Mapbox
  pins (red start, teal stop, yellow camera) — no bundled icon assets.
- **Resolution**: native 256px tiles at the existing 960×640 default size (3:2, same shrink-to-
  screen logic as today). Labels will look smaller than Mapbox's `@2x` — an accepted trade-off,
  not solved with supersampling.
- **Mapbox removal**: happens in this same todo (see above), not staged as a follow-up.
- **Tile disk cache**: `<data-dir>/.tile-cache/<source>/<z>/<x>/<y>.png`, gitignored alongside
  `.state.yml`.
- **Zoom picker**: Map Assistant keeps the existing Close/Normal/Wide/Very wide combo; each
  level maps to a margin-around-bbox fraction for the fit-to-bounds math, replacing Mapbox's
  padding/zoom request params.
- **User-Agent**: outgoing tile requests send `TripTale/<version> (+https://github.com/<org>/
  triptale)`, version from `BuildProperties`, per OSM's tile usage policy.

## Alternatives considered

- Gluon Maps (JavaFX map view): interactive, but saving needs a node snapshot.
- JXMapViewer2 (Swing): can paint to an image, but more than we need.
- Mapsforge: true offline rendering from `.map` files (hundreds of MB per region), any style;
  only worth it if offline matters.
- Own tile server (PostGIS + Mapnik): overkill.

## Trade-offs

- Standard raster tiles are 256 px and not retina-optimized: at 1920×1280 labels look smaller
  than Mapbox's `@2x` (accepted, see Decisions above).
- Pins won't be emoji.
