# Todo 15 — Start Point Coordinates

## Problem

A trip entry currently records `distance`/`altitude`/`trackurl` but not *where* the day
started. Add optional `startlat`/`startlon` coordinates per entry, editable through a small
popup next to the Track URL field, with free-text parsing from common formats (Google Maps
URL, GPX `<trkpt>`, GeoJSON array) so the user doesn't have to hand-convert coordinates.

## Key decisions (from grill-me session)

1. **Frontmatter stays flat.** Two independent lowercase scalar keys, `startlat`/`startlon`,
   each a nullable `Double`, following the existing `altitude`/`distance` convention (no
   nested geojson-style object). Alphabetical slot in `MarkdownStore.saveEntry`'s
   `LinkedHashMap` insertion order: `altitude`, `belongs_to`, `date`, `distance`, `startlat`,
   `startlon`, `trackurl`, `type`.

2. **GeoJSON coordinate order follows the real GeoJSON spec: `[lon, lat]`.** The original
   todo text's own worked example ("[125.6, 10.1] for lat=125.6 and lon=10.1") contradicted
   its own parenthetical ("lon first, lat 2nd") — the parenthetical is correct and the example
   was simply wrong. The parser treats the first array element as longitude, second as
   latitude.

3. **Coordinates are a pair, not independently nullable.** Unlike `altitude`/`distance`
   (each independently meaningful), a lone latitude or longitude can't be rendered as DDM or
   plotted, so the popup's Save button is enabled only when **both** fields parse to valid
   numbers in range (lat ∈ [-90, 90], lon ∈ [-180, 180]). In storage, `startLat`/`startLon` are
   still two separate nullable `Double` fields — nothing enforces the pairing at the
   `DiaryEntry`/`MarkdownStore` level, only the popup's UI validation.

4. **Button placement:** same row as Track URL, appended inside the existing
   `HBox` that already holds the `trackUrlField` + its 🔗 open-in-browser button (from todo
   04) — not a new grid row. Label: `🗺️ Uncharted` when unset, or the DDM string
   (1-decimal-minute precision, e.g. `51° 29.3' N 0° 0.8' W`) when set. A second 🔗 button
   sits right after it, mirroring the Track URL row's own open-in-browser button: disabled
   unless both coordinates are set, opens `https://www.google.com/maps?q=<lat>,<lon>` via
   `openInBrowser(...)` when clicked.

5. **Popup fields are in-memory only, exactly like every other entry field.** Clicking
   Save/Clear in the coordinates popup updates `MainController`'s in-memory `startLat`/
   `startLon` (and the button label) the same way typing in the altitude field updates its
   `TextField` — nothing touches disk until the user does the normal entry Save. This also
   means the popup's own dirty-tracking piggybacks on the existing `isDirty()`/
   `snapshotBaseline()` machinery: `startLat`/`startLon` get baseline fields just like
   `baselineDistance`/`baselineAlt`.

6. **Popup layout and buttons**, built in code (`Dialog<ButtonType>`, `GridPane` content,
   `applyStylesheet(...)`) the same way `onNewTrip`/`onEditSettings` are — no new FXML dialog:
   - `DDM:` — read-only `Label`, recomputed live from the Latitude/Longitude fields via a
     text-property listener (not tied to the stored value — reflects in-progress edits).
   - `Latitude:` / `Longitude:` — plain decimal `TextField`s, defaulted from the current
     `startLat`/`startLon` (same `.`/`,` decimal parsing as `parseDouble`, but *silent* — a
     dedicated non-alerting parse helper, since this fires on every keystroke).
   - `Parse from:` — a `TextArea` for pasting one of the three supported formats.
   - **Clear** (`ButtonBar.ButtonData.LEFT`) — unsets `startLat`/`startLon`, closes popup.
   - **Save** (`ButtonBar.ButtonData.OK_DONE`) — takes the current Latitude/Longitude field
     values, closes popup. Disabled unless both fields are valid (see #3), refreshed via the
     same `Runnable refreshX` + field-listener pattern used in `onNewTrip`.
   - **Parse** (`ButtonBar.ButtonData.OTHER`) — parses the textarea via the new
     `Coordinates.tryParse(String)`, fills Latitude/Longitude on success, shows a small inline
     hint label on failure. Consumes its own `ActionEvent` so the dialog stays open (same
     event-filter technique already used for image-viewer key navigation).
   - **Cancel** (`ButtonType.CANCEL`) — added after implementation review: dismisses the
     popup without applying any change (same no-op path as any other unrecognized result).
     Also required to re-enable the dialog window's native close (X) button — JavaFX disables
     it unless a `CANCEL_CLOSE`-data button is present, which was initially missing and left
     the traffic-light close button inert.
   - **Clear**/**Parse** both use `ButtonBar.ButtonData.OTHER` (not `LEFT`/`RIGHT`) so all
     four buttons cluster together instead of `ButtonBar`'s platform-convention layout
     spreading a `LEFT`-tagged Clear to the opposite edge from `Save`.

7. **Google Maps URL parsing covers the common variants**, not just the exact
   `@lat,lon,zoom` shape from the todo's example: `@lat,lon` (with or without a trailing
   `,<zoom>z`) and `?q=lat,lon` / `&q=lat,lon` query parameters.

8. **GPX `<trkpt lat="…" lon="…">` and GeoJSON `[lon, lat]`** round out the three supported
   `Parse` formats, tried in this order (most-to-least unambiguous): GPX attributes → Google
   Maps URL → GeoJSON array. First match wins.

9. **Parse failure is a quiet inline hint, not a modal `Alert`.** Unrecognized input just
   shows a small label under the textarea (e.g. "Couldn't parse coordinates from input.") and
   leaves Latitude/Longitude untouched; the user can edit the text and hit Parse again.

10. **New pure-util class `net.timafe.triptale.util.Coordinates`** (no JavaFX imports, styled
    like `Slugs`: `public final class`, private constructor, static methods), owning:
    - `toDdm(double lat, double lon)` — the display formatter, `%.1f` minutes,
      `Locale.ROOT`, hemisphere letters derived from sign (`N`/`S`, `E`/`W`; `0.0` treated as
      the positive hemisphere for both axes).
    - `tryParse(String text)` — returns `Optional<LatLon>` (a small `record LatLon(double
      lat, double lon)`), trying GPX → Google Maps → GeoJSON in order.
    - Range validation (lat/lon bounds from #3), used both by the parser and by the popup's
      Save-button validity check.

11. **Export support:** a new `entry-startpoint.md` template (`Start: {{startpoint}}`,
    mirroring `entry-distance.md`/`entry-altitude.md`/`entry-track.md`), substituted in
    `DiaryExporter.renderEntry` right after altitude and before the track line, only when
    *both* `startLat`/`startLon` are set — value is `Coordinates.toDdm(...)`.

## Non-goals / explicitly out of scope

- No migration of existing entries — this is a new optional field, nothing to backfill.
- No map preview/embed in the popup — DDM text only.
- No support for formats beyond the three listed (e.g. DMS with seconds, MGRS, Plus Codes).
- No validation that a parsed/entered coordinate is anywhere near the trip's other days —
  it's just stored as given.
