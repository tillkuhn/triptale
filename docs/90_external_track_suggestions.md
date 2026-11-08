# 90 External track suggestions for Import GPX

Status: design discussed, nothing built. Decisions marked **open** are still to make.

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
- **B. Indicator on the entry.** A hint/button appears when a tour exists for the entry's date.
  Should be hidden when the entry already has a track attachment or a `url_track`.
- **C. Trip-level bulk import.** Match every day of the trip's date range at once. Biggest
  feature; only worth it if whole trips are imported in one go. Not planned.
- **D. Just a friendlier chooser.** Rejected: a native `FileChooser` can't filter by date.

Chosen direction: **A and B**, sharing one date-matching class.

## Design

### Matching

- New JavaFX-free class, e.g. `util.GpxFolderScan`:
  `List<Path> findForDate(Path folder, LocalDate date)`, regex `^<date>_.*\\.gpx$`.
  Plain `Files.list`, no GPX parsing, fast enough to run on every date change.
  Unit-tested with `@TempDir` like the other util classes.
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

### B: indicator

- Shown next to the date when `findForDate` is non-empty and the entry has no track yet.
- Check runs off the FX thread (or is cheap enough not to matter, to be measured) on
  trip/date change. Clicking it starts the same flow as A.

## Open decisions

1. **Folder source**: reuse `lastGpxFolder` (no new setting, but only works after the folder was
   picked once), or an optional `gpxBackupDir` in `settings.yml` edited in Edit Settings.
   Leaning towards the setting for B, since B has to work without a prior import.
2. **Scope of the first cut**: A alone, or A + B together.
3. **B's "already has a track" rule**: which signals count (GPX attachment, `url_track`, both).
4. Keep docs/UI wording in English; the folder path is device-local, so if it becomes a setting
   check whether it belongs in `settings.yml` or in `.triptale/state.yml`.
