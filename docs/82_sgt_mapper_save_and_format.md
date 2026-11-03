# 82 Sgt. Mapper: format, temp renders, save to entry folder

Follow-up to todo 54 (Map Assistant) and todo 61 (self-rendered OSM maps). Main code:
`ui/dialog/MapAssistantDialog.java`, `map/OsmMapRenderer.java`,
`attachments/AttachmentsDir.java` (`mapFile`, `dayDir`), `ui/MainController.java`
(`onMapAssistant`, the `🗺 Sgt. Mapper` button in `fxml/main.fxml`).

## Problem

- Every "🖥 On Screen" render is written straight to
  `attachments/<year>/<slug>/map-<from>-<to>.png` (`AttachmentsDir.mapFile`). Most renders are
  ad hoc experiments, so the trip folder fills up with maps nobody wants.
- The header spends a whole line on the trip name, which never changes while the dialog is open.
- The bottom row packs the photo checkbox, style, zoom and the buttons into one button bar; adding
  more options won't fit.
- The map is always 3:2 landscape, which wastes space on a trip that runs north–south.

## Requested changes

1. **Title.** Window title becomes `🗺 Sgt. Mapper · <trip name>`, and the trip-name line goes
   from the header. The header keeps the points line (`📍 1 start point · 🏁 stop point · 📷 2
   photo locations`).
2. **Options row** (below the map): `Style:` · `Zoom:` · `Format:` · `📷 Add Photo POIs`
   checkbox last (renamed; the backpack is implied as the source).
3. **Format dropdown**: `3:2 Landscape` (default, as today), `1:1 Square`, `2:3 Portrait`.
4. **Renders are temporary.** "On Screen" renders to a temp location rather than the attachments
   folder, so nothing lands in the trip until the user saves.
5. **Action row** (own line, below the options): target-entry dropdown · `💾 Save` · `Save as…`
   · … · `Close` · `🖥 On Screen`. All on one line.
   - The dropdown lists the entries in the map's from..to range: one entry for a single-day
     range, every entry for e.g. days 10–13. The default is the active entry (the day the dialog
     was opened on).
   - `Save` copies the on-screen temp image to that entry's attachment folder
     (`AttachmentsDir.dayDir`) as the fixed name **`tripmap.png`**, so there's at most one map per
     entry and it shows up in the entry's gallery.
   - `Save as…` opens a file chooser and writes the current image anywhere on disk.
6. **Button checkmark.** When the current entry has a `tripmap.png`, the main window's
   `🗺 Sgt. Mapper` button shows a ✓ like the Coordinator button does. The button must be the same
   width with or without the ✓.
7. **Cached map on open.** If the entry already has a `tripmap.png` when Sgt. Mapper opens, show
   it straight from disk, which also works offline. The header gets a status segment:
   `· 💾 cached tripmap.png` for a map loaded from disk, `· ⏳ temporary` for a render that hasn't
   been saved. With no cached map the dialog behaves as it does today (it waits for On Screen).

Out of scope (separate follow-up): making `tripmap.png` an optional/conditional part of the
gallery in trip exports. Until then it's exported like any other day image.

## Decisions

- **D1 Cached map restores its settings.** On Save, the range (from/to), style, zoom, format and
  photos flag are embedded as PNG `tEXt` metadata in `tripmap.png` (`javax.imageio`, no sidecar).
  On open, the dialog reads them back and sets the range picker, dropdowns and checkbox, so the
  header and the target-entry dropdown match the shown map. A file without readable metadata is
  still shown, and the controls fall back to "this day" with the defaults.
- **D2 Cached map only from the day button.** The `🗺 Sgt. Mapper` entry-form button opens on the
  current day and loads that day's `tripmap.png`. The My Trips menu entry (whole trip) starts
  empty, as it does today.
- **D3 Temp renders** go through `export.ExportTempFiles.newFile`: one temp file per render,
  swept at startup. Nothing is written under `attachments/` until Save. (Decided without asking,
  since it's the existing pattern.)
- **D4 Per-format size.** Displayed size = file size, as today. Each format is fitted to the
  screen's usable area separately (the reserved height grows by the new action row, about
  50 px), with the long edge capped at 960 px. That gives 3:2 → 960×640, 1:1 → up to 960×960,
  2:3 → up to 640×960 (for example about 780×780 / 520×780 on a 14" MacBook), down to the
  existing minimum size. Switching format resizes the map area and the dialog. Rejected: one
  fixed 960×640 frame (portrait only 427 px wide) and a high-res file scaled down for display
  (more tiles, labels look smaller).
- **D5 Overwrite.** Save overwrites the target's `tripmap.png` silently when it's the map the
  dialog was opened with (open cached → tweak → save). A confirmation ("Replace the existing
  tripmap.png of Day 12?") appears only when saving to a different entry that already has a map.
- **D6 Save enablement.** `💾 Save` is disabled while the map is stale (range/photos changed
  without re-rendering), while a render is in flight, and when the target is the day whose
  unchanged `tripmap.png` is already on screen (saving it onto itself would do nothing). Picking
  another day still lets a cached map be copied there. `Save as…` works for any map on screen
  that isn't stale, cached ones included.
- **D7 After Save.** The header switches to `💾 cached tripmap.png`. When the dialog closes it
  returns the saved days (`Set<LocalDate>`), and `MainController` refreshes the ✓ and the
  Impressions count. No `addPending(...)`: 🎒 Backpack adds don't call it either, and in git
  attachment-sync mode Smart Sync picks the file up from `git status` anyway.
- **D8 Format** re-engages automatically like Style/Zoom. It is **not** remembered across
  sessions: the best format depends on the trip's shape, so it always starts at 3:2. A cached
  map restores its own format (D1).
- **D9 Button width.** The `🗺 Sgt. Mapper` button keeps a fixed width; the ✓ takes reserved
  space, so the layout doesn't shift whether or not a map exists.
- **D10 Old renders.** The app stops writing `attachments/<year>/<slug>/map-<from>-<to>.png`, and
  `AttachmentsDir.mapFile` is removed (`dayDir` + `tripmap.png` replaces it). Existing files are
  deleted once by an ad hoc script outside the app, the same way earlier data migrations were done.

## Implementation notes

- `map.MapFormat`: the format enum and its `fit(availableWidth, availableHeight)` sizing (D4).
- `map.PngText`: reads and writes PNG `tEXt` chunks with `javax.imageio`.
  `map.TripMapSettings` holds the settings record, with keys prefixed `triptale:` (`from`, `to`,
  `style`, `zoom`, `format`, `photos`, plus the point counts `starts`, `stop`, `photosShown`,
  `photosFound`, so the header of a cached map is right without re-reading EXIF). Unknown
  style/zoom/format values fall back to the defaults; a missing or reversed range means "no
  settings".
- `OsmMapRenderer.render(..., Map<String,String> text, Path target)` writes the settings into
  every render, so Save is a plain file copy.
- `AttachmentsDir.tripMapFile(ref, date)` and `saveTripMap(ref, date, source)` replace `mapFile`.
- `MapAssistantDialog.showDay(trip, date)` (loads the cached map) and `showTrip(trip, activeDate)`
  (never does) both return the saved days. Temp renders come from `ExportTempFiles.newFile` and
  are deleted when the dialog closes; the startup sweep catches any left behind.
- `MainController.updateMapperButton` sets `🗺 Sgt. Mapper ✓`. The button keeps its fixed
  `.half-width-button` width (130 px, left-aligned), so the ✓ doesn't shift the layout.

## Future ideas (not this todo)

- Trip-level maps, e.g. one for the trip intro/README in exports.
