# Todo 24 — Support import GPX for Tale Entries

## Problem

Todo 23 lets a GPX file prefill a new *trip*'s name/date. The same convenience is missing for
an individual *tale entry* (a single day within an existing trip): title and start
coordinates currently require manual typing / the Coordinates dialog, even though a GPX file
for that day already has both.

## Key decisions (from grill-me session)

1. **Entry point: new menu item "📥 Import GPX…" in the "Tale Entries" menu group**, placed
   right after "👁 View Source…" (before the day-navigation separator). No dedicated button —
   the todo explicitly rules that out since the action isn't tied to a fixed UI slot, it derives
   its own day. Disabled under the same condition as `viewSourceMenuItem`
   (`tripCombo.getValue() == null || datePicker.getValue() == null`), wired in `loadEntry()`
   alongside `updateViewSourceMenuItem(...)`.

2. **Reuses `util.GpxImport` verbatim — no changes to that class.** It already returns exactly
   `(name, date, lat, lon)` from the first `trk/trkseg/trkpt`, which is exactly what this needs.
   Todo 24 is new call-site/UI wiring only, no new parsing logic.

3. **Target trip: always the currently selected trip (`tripCombo.getValue()`).** The user
   picked the trip; a business-logic range check (rejecting a GPX date that's technically
   "wrong" for this trip) was deliberately ruled out as scope creep the todo doesn't ask for.

   However, implementation surfaced a **mechanical** reason a range check is required anyway:
   `datePicker`'s existing value-change listener (`MainController.java:287-308`) silently
   *snaps* any out-of-range date to the trip's start/end day (with a generic "Snapped to day 1"
   status message) before the navigate-away guard even runs. Calling
   `datePicker.setValue(parsed.date())` for a date outside `trip.startDate()`/`endDate()` would
   therefore silently import into the wrong day with no indication GPX was involved. So
   `onImportGpx` checks `p.date()` against `trip.startDate()`/`endDate()` itself and aborts
   with a clear error *before* touching `datePicker` — not a validation policy choice, a
   guard against an existing UI side effect.

   Separately, this surfaced a real, independent gap: there is **no delete capability
   anywhere in the app** today (not for trips, not for entries), so an import landing on the
   wrong day/trip (e.g. wrong trip selected, not caught by the range check) has no in-app undo
   once saved. Filed separately as **todo 25 — Delete Tale Entry / Trip**, not part of this
   todo's scope.

4. **Full sequence:**
   1. User clicks "Import GPX…" → `FileChooser` (extension filter `*.gpx`, same as
      `NewTripDialog`'s importer; owner window `null`, matching every other dialog/alert in
      `MainController` — none of them wire an explicit owner today).
   2. `GpxImport.parse(file)` runs. Empty result → `status("Couldn't parse a track name/point
      from this file.")`, abort. No dialog, no navigation, no field changes.
   3. On success, check `store.entryExists(trip.ref(), parsed.date())` for the **currently
      selected trip** — disk state, not form/dirty state.
      - If an entry is already saved on disk for that date → confirmation
        `Alert(CONFIRMATION, "Entry for {date} already exists. Overwrite title and start
        coordinates from GPX?", YES, NO)`, inline in `MainController` (same pattern as the
        existing confirm at line ~368), not a new `ui/dialog/` class — there's no independent
        form/state here, just a boolean answer.
      - **No → abort completely.** No navigation, no field changes, nothing saved. Matches
        todo 23's all-or-nothing rule: partial effects are worse than no effect.
   4. On proceed (no existing entry, or user said Yes): `datePicker.setValue(parsed.date())`.
      This is the *only* navigation step, and it's a plain value-set on the existing control —
      the existing value-change listener already runs `confirmNavigateAway` for whatever day
      was previously displayed (guarding *that* day's unsaved changes, Save/Discard/Cancel),
      then calls `loadEntry()` for the new date, which populates every field
      (`distance`/`altitude`/`title`/`trackUrl`/`startLat`/`startLon`/`tales`) from disk exactly
      as normal, and calls `snapshotBaseline()`.
   5. **After** `loadEntry()` finishes, patch just the two derived fields on top:
      ```java
      titleField.setText(parsed.name());
      startLat = parsed.lat();
      startLon = parsed.lon();
      updateCoordinatesButton();
      updateDirty();
      ```
      `titleField` already has a text-listener calling `updateDirty()`; `startLat`/`startLon`
      are plain fields with no listener, so `updateDirty()` must be called explicitly — same
      pattern as `onCoordinates()` (`MainController.java:1107-1108`).
   6. `distance`/`altitude`/`trackUrl`/`tales` are never touched — they were already populated
      by `loadEntry()` from whatever's on disk and the import code never assigns them. This is
      what satisfies "do not empty existing other fields": there is no field-clearing logic to
      write, only two fields are ever assigned.
   7. **No auto-save.** The entry is left dirty; user reviews (title text field and the
      coordinates button both reflect the new values) and hits Save/Cmd+S same as any other
      edit. This matches every other field in the form — nothing else auto-persists either.

5. **Confirmation trigger is disk state only, not form dirty state.** Even if the form
   currently shows unsaved edits for the exact derived date, the confirmation only fires if
   `entryExists` is true for that date — i.e. it protects saved file content specifically, not
   in-memory edits (which the user can always lose by navigating away without saving, same as
   today).

6. **Errors and success both report via the existing `status(...)` status line** (bottom of
   the window, same mechanism `doSave` uses for "Saved trip/date") — no new hint `Label`, no
   blocking `Alert` for the non-confirmation cases. `NewTripDialog`'s inline hint label isn't
   reusable here since there's no dialog window to put it in.

## Non-goals

- No trip-date-range validation for the derived date (see decision 3).
- No delete/undo capability — split out to todo 25.
- No changes to `GpxImport.java` or its parsing rules (still first `trk`/first `trkseg`/first
  `trkpt` only, still UTC calendar date, still all-or-nothing on parse failure).
- No auto-save — import only prefills, same review-before-persist model as manual edits.
- No FileChooser initial-directory memory (matches `NewTripDialog`'s importer, which also
  doesn't remember a directory).
