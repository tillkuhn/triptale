# 90 External track suggestions for Import GPX

## Problem

A separate tool backs up recorded tours from an external track app into a shared folder
(`tour_recorded`). "Import GPX…" (`MainController.onImportGpx`)
opens a plain `FileChooser` in `lastGpxFolder` (todo 71), which shows all ~500 tours.

## What the folder looks like (checked 2026-11-08)

- 511 files, all `.gpx`, all named `YYYY-MM-DD_<tourId>_<slug>.gpx`.
- The prefix is the **recording date**, i.e. the same date `GpxImport.Parsed.date()` yields,
  so it can be matched without parsing any file.
- Some dates occur more than once (e.g. 2020-09-25, 2020-09-26, 2022-02-26/27), so a date
  can yield 0, 1 or several candidates.

## Approaches considered

- **A. Smarter import.** "Import GPX…" first looks for tours matching the current entry's date.
- **B. Indicator on the entry.** A small always-visible GPX button in the Distance/Alt/Activity row
  whose look shows the state (see B below).
- **C. Trip-level bulk import.** Match every day of the trip's date range at once. Biggest
  feature; only worth it if whole trips are imported in one go. Not planned.
- **D. Just a friendlier chooser.** Rejected: a native `FileChooser` can't filter by date.

Chosen direction: **A and B**, sharing one date-matching step (first cut covers both).

## Design

### Matching

- **Reuse `storage.ImpressionsResolver.resolve(pattern, trip, date)`** (`${HOME}`, `${ENTRY_YEAR}`,
  `${ENTRY_MONTH}`, `${ENTRY_DAY}`, directory cached, last segment a filename glob). No new scan
  class. The date format of the export tool's filenames is thereby configurable, not hard-coded.
- Matching is on the filename only, no GPX parsing, so it is cheap enough to run on every date change.
- Match on the filename date only. It can differ from the GPX's first `<time>` near midnight
  or across time zones; the dialog shows the parsed date and the existing date-range check
  still catches real mismatches.

### A: flow inside Import GPX

1. Entry has a date and the folder has matches: show a small candidate dialog (in `ui/dialog/`,
   returns `Optional<File>`, no controller state). One match is pre-selected; several are listed
   by slug.
2. The dialog has a **Browse…** button opening today's `FileChooser`, so a file from another
   folder or with a different name (or a different recording day than the entry) can still be
   picked.
3. No matches (or no date / no folder): skip the dialog, open the `FileChooser` exactly as today.
4. Refactor the part after `chooser.showOpenDialog` into `importGpx(File)`. Chooser path and
   candidate path share the date-range check, `GpxImportDialog` overwrite matrix and attachment
   copy. Nothing there changes.
5. Possible later addition: a text filter and the whole folder below the date matches.

### B: GPX button

A small standalone **GPX** button in the Distance / Alt / Activity row, between the activity
combo and the Coordinator button (which stays where it is). The Altitude label becomes "Alt:" and the
distance/altitude fields get a little narrower to make room. The button is never hidden and always
clickable, so any file from any folder can be imported. Only its emphasis changes:

| State | Look | Click |
|---|---|---|
| Entry has a GPX attachment | `✅ GPX`, muted text | Chooser to re-import (existing overwrite matrix) |
| Matching file(s) in the folder, not imported | `📥 GPX`, filled teal; tooltip names the file (or count) | Candidate dialog from A |
| Nothing found / no pattern | `❓ GPX`, normal | `FileChooser` as today |

- An imported entry shows ✓ even if a matching file still sits in the folder; the highlight
  means "importable and not yet imported".
- The check runs on trip/date change (cached directory, one listing), preferably off the FX thread.
- The Import GPX menu item stays and runs the same flow.

## Decisions

1. **Folder and pattern**: a new optional `gpxFilePattern` in `settings.yml` (`AppSettings`),
   edited in Edit Settings next to the impressions pattern and resolved the same way, e.g.
   `${HOME}/Backup/tour_recorded/${ENTRY_YEAR}-${ENTRY_MONTH}-${ENTRY_DAY}_*.gpx`. Blank = both
   A's suggestions and B's indicator are off and Import GPX behaves as before. `lastGpxFolder`
   stays only the start folder of the Browse… chooser. Device-local path in the synced-nowhere
   `settings.yml` is fine: settings.yml is per device anyway.
2. **Scope of the first cut**: A and B together.
3. **B's "imported" rule**: only a GPX attachment counts (shows ✓). A `url_track` alone does not
   (it may point at another tool).
4. **Several matches**: single selection, one import per invocation.
5. Docs/UI wording in English.

## Implementation notes

- The feature is branded **🦎 GPX Gecko** (menu item "Import with GPX Gecko…", titles of the
  candidate and overwrite dialogs). The button keeps the 📥/✅/❓ icons, which carry the state;
  plain `✓`/`?`/`⬇` were too faint or rendered tiny. The button has a fixed width so the row doesn't shift.
- The candidate dialog shows the folder (home-relative) and each file's size (to spot the biggest
  of several). Sizes use the shared `util.ByteSizes.humanize`.
- `${USER}` (`user.name`) was added to the pattern variables (and `dataDir`).
- Code: `impressions.GpxCandidates` (find + hasImported), `ui.dialog.GpxCandidatesDialog`,
  `MainController.updateGpxButton`/`importGpx`.

## Open points (resolved)

- How a GPX attachment is recognised for the ✓ state (same lookup as the Backpack button).
- Whether the FX-thread check needs to move to a background thread (measure).

## Follow-up: places on save

After a GPX import the start/stop points were set and weather was fetched silently on save,
but `start_place`/`stop_place` stayed empty. The silent post-save enrichment in
`MainController.maybeFetchWeather` now also runs the reverse lookup (`PlaceLookup`) for a start/stop
point that has coordinates but no place, one request after the other (Nominatim rate limit), and the
weather's place echoes the start place. Existing values are never overwritten; failures are only
logged, so a save retries the missing lookups.
