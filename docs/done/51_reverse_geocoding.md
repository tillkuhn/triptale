# 51 Reverse geocoding: place name from entry coordinates

Look up a place name ("Soest, DE") for an entry's start and end point from inside the
Coordinator dialog, and store it in the entry's frontmatter. The service choice (public
Nominatim) is settled in [ADR-0003](../adr/0003-reverse-geocoding-service.md).

## Status

Done 2026-10-13. Lookups are logged at DEBUG (request URI, status, raw JSON, mapped result).
At `zoom=10` Nominatim answers with the municipality, e.g. a point near Möhnesee gives
"Warstein, DE (town)".

## Decisions (grill session 2026-10-13)

### Data

- New frontmatter keys `start_place` / `stop_place` (snake_case, kept in alphabetical order
  in `MarkdownStore.saveEntry`), carried on `DiaryEntry` as `startPlace` / `stopPlace`.
- One free-text string per point, e.g. `"Frankfurt, DE"`: name + `", "` + upper-case ISO
  country code. No state/county fields. The country code is not enforced, so the user can
  type anything. Blank means absent.
- A place may be stored **without** coordinates (e.g. typed by hand, no GPS fix).

### Lookup service (`map.PlaceLookup`, no JavaFX)

- `GET https://nominatim.openstreetmap.org/reverse?lat=..&lon=..&format=jsonv2&zoom=10&accept-language=en`
  via `java.net.http`; parse with Jackson (already on the classpath).
- The base URL is a **constant**, not a setting: switching to Photon needs a different
  response mapping anyway.
- Name: first of `address.city` → `town` → `village` → `name`; country from
  `address.country_code`, upper-cased. No country code means just the name. No name means a
  failure ("no place found").
- User-Agent `TripTale/<version> (+https://github.com/tillkuhn/triptale)`. The version comes
  from `BuildProperties` and falls back to `dev`.
- At most 1 req/s: remember the last request time and sleep out the rest of the second. That
  way Start and End clicked back to back both succeed. No Bucket4j.
- Timeout about 5 s. Errors are returned as a result/exception with a short, human message,
  never shown as a modal alert.
- Credit "© OpenStreetMap contributors" in the About dialog.

### Coordinator dialog (`ui/dialog/CoordinatesDialog`)

- Shrink the lat/lon fields to about the DDM column's width. Add a **Place** column after
  DDM, then a 🔍 button before 🔗 / 🗑:
  `📍 Start: [51.478536] [8.336688]  51°29′N 8°20′E  [Soest, DE    ] 🔍 🔗 🗑`
  Widen the dialog a little only if needed. A long name can overflow.
- 🔍 button states (the feedback lives on the button and its tooltip, per row):

  | State | Icon | Clickable | Tooltip |
  |---|---|---|---|
  | idle | 🔍 | if point set + online | "Look up place name (OpenStreetMap)" |
  | running | ⏳ | no | "Looking up…" |
  | success | 🔍 | yes | "Found Soest, DE (town) via OpenStreetMap" |
  | error | ⚠️ | yes (retry) | the actual error, e.g. "Timed out after 5 s" |

- The lookup runs off the FX thread. A success **overwrites** the place field without asking,
  since the user clicked the button and Cmd + Z can undo it.
- Editing or clearing the row's point resets the button to idle (no stale ⚠️).
- Online state: `MainController` passes its current connectivity state when it opens the
  dialog. That's enough because the dialog is modal: to get online later, the user re-checks
  connectivity (top-right toolbar button) and reopens. When offline, 🔍 is disabled with the
  tooltip "Offline — re-check connectivity (top right) and reopen".
- 🗑 clears lat, lon **and** place. Parse → Start/End and hand edits of lat/lon leave the
  place alone. There is never an automatic lookup (ADR: no lookups while typing).
- `Result` gains the two place strings.

### Main window

- The Coordinator button label stays `🧭 Coordinator: 📍 ✓  🏁 –` (✓ = point set). Only the
  tooltip gets the place: `Start Soest, DE (51°29′N 8°20′E) · End Hagen, DE (…)`, or
  `Start Soest, DE (no coordinates)` for a place without coordinates.
- Entry dirty tracking covers both place fields.

## Out of scope / ideas for later

- Show the place in exports → **todo 58** (`export.EntryFacts`: Markdown byline, HTML ticket stub,
  WordPress columns), e.g. "Soest, DE → Hagen, DE". Open questions: replace or append to the
  coordinates, show the country code or not. Licensing: storing the names is fine under ODbL
  (attribution in About covers the app). Once a published export shows them, a small
  "Place names © OpenStreetMap contributors" line in that export is the safe way to attribute.
- Suggest a title like "Vechta → Bremen" instead of "Untitled".
- Run the lookup implicitly on Coordinator OK / GPX import.
- Native place names (`accept-language` per trip language), and an offline GeoNames fallback
  (see ADR-0003).

## Relevant files

- `src/main/java/net/timafe/triptale/ui/dialog/CoordinatesDialog.java`
- `src/main/java/net/timafe/triptale/ui/MainController.java` (Coordinator button text/tooltip, dialog call)
- `src/main/java/net/timafe/triptale/domain/DiaryEntry.java`
- `src/main/java/net/timafe/triptale/storage/MarkdownStore.java` (frontmatter keys)
- `src/main/java/net/timafe/triptale/ui/dialog/AboutDialog.java` (OSM credit)
- `src/main/java/net/timafe/triptale/sync/S3Client.java` (existing `java.net.http` style)
- Tests: JSON → place mapping as a pure function; HTTP via the JDK `com.sun.net.httpserver`
  in a unit test (no real network).
