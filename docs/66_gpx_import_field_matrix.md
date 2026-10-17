# Todo 66 — Per-field GPX import matrix

## Problem

Todo 24's GPX import for a Tale Entry (`MainController.onImportGpx`) derives five fields from
the GPX file — title, start coords, stop coords, distance, altitude gain — and, when an entry
already exists on disk for that date, asks a single all-or-nothing question: "Overwrite title
and geo track data from GPX?" (YES/NO). Saying yes clobbers every one of those fields at once,
even ones the user had already hand-edited (e.g. a custom title) and never wanted touched.
There's no way to take just the track's distance/altitude while keeping a manually-written
title, or vice versa.

## Key decisions (from grill-me session)

1. **Fields in the matrix: all 5 derived fields**, each its own row — title, start coords
   (lat+lon as one row), stop coords (lat+lon as one row), distance, altitude gain. The
   altitude row is omitted entirely when the GPX has no elevation data
   (`GpxImport.Parsed.altitudeGainM() == null`), matching the existing `if (p.altitudeGainM()
   != null)` guard. A sixth row, **"copy GPX file to attachments"**, is added alongside the
   frontmatter fields since it's the same kind of overwrite-or-skip decision and the dialog is
   already there — it defaults checked regardless of the other rows' state (there's no "old
   value" for it, it's just convenient to bundle).

2. **"Old value" always comes from `store.loadEntry(trip.ref(), parsed.date())`** — disk state —
   never from the currently-displayed form. This matters because the GPX's date can differ
   from whatever day is on screen, and the form may hold unsaved edits for a *different* day;
   reading disk state keeps this independent of both, consistent with todo 24's "confirmation
   trigger is disk state only, not form dirty state" rule.

3. **Default checkbox state = checked when the old value is empty/unset:**
   - `Double` fields (`distance`, `altitudeMeters`, `startLat`/`startLon`, `stopLat`/`stopLon`):
     checked when `null`.
   - Title: checked when blank **or** equal to `DiaryEntry.DEFAULT_TITLE` ("Untitled") — a
     saved placeholder title doesn't count as a real value worth protecting.
   - Attachment-copy row: always defaults checked, independent of the other rows.

   Non-empty old values default **unchecked** ("keep"), inverting today's behavior where
   overwriting required an explicit YES but then clobbered everything.

4. **Dialog shown on every import, not just when `entryExists`.** For a brand-new entry every
   row's old value is empty, so every checkbox defaults checked — functionally identical to
   today's silent all-or-nothing import, just now visible and still cancellable. This replaces
   the `store.entryExists(...)` branch that previously gated the confirmation `Alert` entirely.

5. **Dialog structure:** new `ui/dialog/GpxImportDialog.java`, following the existing
   checkbox-driven `GridPane` pattern (`SyncRow` / `SmartSyncDialog` / `SyncTracksDialog`) rather
   than an ad-hoc `Alert`. One row per field: name | old value | new value | checkbox. Returns
   `Optional<Selection>` (a small record of booleans, one per field), following the
   `TripDetailsDialog`-style "empty means cancelled" convention — dialogs don't call back into
   `MainController`, they just return data for the caller to apply.

6. **Cancel = abort entirely** — no navigation, no field changes, no attachment copy, nothing
   saved. Same as today's "No" on the old `Alert`. There is no separate "OK with zero boxes
   checked" case to special-case: it behaves like OK with a no-op field patch, but navigation
   (and, if its row was unchecked, no attachment copy) still happens — the user explicitly
   chose to land on that day.

7. **On accept:** `datePicker.setValue(parsed.date())` runs unconditionally (same as today —
   this triggers the existing `confirmNavigateAway` guard for whatever day was previously
   displayed, then `loadEntry()` for the new date, which populates every field from disk as
   normal). Only *after* `loadEntry()` finishes are the checked fields overlaid on top,
   mirroring todo 24's original two-step "navigate, then patch" sequence — just per-field now
   instead of always patching all five.

8. **Attachment copy**: `attachmentsDir.addFiles(...)` only runs if the user clicked OK *and*
   the copy-attachment row is checked. On Cancel, no file is copied at all (matches the
   already-existing order where the copy happened after the old confirmation in
   `onImportGpx`).

9. **Coordinate display format**: `Coordinates.toDdm(lat, lon)` (already used by
   `updateCoordinatesButton`'s tooltip), e.g. `51°29′N 0°01′W`, for both old and new coordinate
   cells. Distance/altitude use the existing `Lengths.formatDistance`/`formatAltitude`. Empty
   old-value cells render as `(none)`.

## Non-goals

- No change to `GpxImport.java`'s parsing rules (still first `trk`/first `trkseg`/first
  `trkpt` only, still UTC calendar date, still all-or-nothing on parse failure — a GPX that
  fails to parse still aborts before any dialog is shown).
- No trip-date-range validation changes (todo 24's existing check still runs before the
  dialog, unchanged).
- No new frontmatter keys — same five fields as todo 24, just independently toggleable.
- No auto-save — accepting the dialog only prefills checked fields; the entry is left dirty for
  review/Save same as any other edit.
