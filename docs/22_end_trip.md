# Todo 22 — End Trip

## Problem

Trips only have a start date. There's no way to record that a trip is over, no way to see
that at a glance while navigating entries, and no way to jump to the last day the way you can
jump to the first. Separately, `startDate` is the last frontmatter key still in camelCase —
every other multiword key (`belongs_to`) is snake_case, and this is the moment to align it
before more consumers depend on the current spelling.

## Key decisions (from grill-me session)

1. **Frontmatter key naming:** `start_date` / `end_date` (snake_case), matching the `belongs_to`
   precedent for genuinely two-word keys. `trackurl` is treated as the outlier (reads as one
   compound noun) rather than the rule. Java accessors stay camelCase: `Trip.startDate()` /
   `Trip.endDate()` — only the serialized YAML key changes.

2. **Hard breaking rename, no in-app fallback.** `loadTrip` does not read the old `startDate`
   key as a fallback. Matches the todo 20/21 precedent: migrate the real repo once, outside the
   app, rather than carrying dual-key read support in the codebase. Migration is a `sed` pass
   over `~/Pictures/triptale-data/**/README.md`: `s/^startDate:/start_date:/`. Nothing is needed
   for `end_date` on old files — an absent key already means "not finished."

3. **`end_date` is written conditionally, never as a null value.** `saveTrip` must use
   `if (trip.endDate() != null) fm.put("end_date", trip.endDate().toString());` — the same
   pattern `saveEntry` already uses for `altitude`/`distance`/`trackurl` — not the unconditional
   null-ternary `fm.put` that `startDate` currently uses at `MarkdownStore.saveTrip`. (The
   current unconditional form never actually emits `null:` today because `startDate` is always
   set in practice, but copying it verbatim for `end_date` would emit a literal `end_date: null`
   line for every unfinished trip, violating "don't record null values.")

4. **Validation lives in `MainController`, not storage.** Rejects `endDate.isBefore(startDate)`
   or `endDate.isBefore(maxEntryDate)` (max of `store.listEntryDates(trip.ref())`), evaluated at
   save time in the Trip Details dialog. Equality is allowed (`>=`, not strict `>`) — required
   for the End Trip button to work at all on a single-day trip or when ending exactly on the
   last logged entry. `storage`/`domain` stay free of domain validation, per existing
   `StorageException`-only convention.

5. **End date is directly editable**, not button-only. A `DatePicker` for end date sits next to
   the start-date display in the Trip Details dialog (same line, per the todo — there's room).
   It supports manual prolongation to any valid future date, and clearing it back to empty
   un-ends the trip (`endDate` → `null`, `end_date` key dropped on next save). The **End Trip**
   button is a convenience that fills this picker from the last entry's date
   (`listEntryDates(trip.ref())` max, existence-only — not content-aware). The button is
   disabled when the trip has zero entries. No confirmation dialog on click — the picker value
   is staged behind the dialog's existing OK/Cancel, same as name/description edits.

6. **Forward navigation mirrors the existing backward navigation exactly.**
   - Add `fx:id="nextDayButton"` to the existing `▶` button in `main.fxml` (currently has none).
   - Fold its disable logic into the existing `updatePrevButtonState()` — no rename, despite the
     method now covering both directions. Minimizes diff.
   - Add a new `⏭` button (`lastDayButton`) immediately after `todayButton` in the
     `nav-button-group` HBox, wired to a new `onLastDay()` that sets
     `datePicker.setValue(trip.endDate())`. Enabled only when `trip.endDate() != null` — this is
     trip-scoped, not date-scoped, but its state is still recomputed inside
     `updatePrevButtonState()` for simplicity, since that already runs on every date/trip change.
   - `onNextDay()` / `onLastDay()` get **no internal guard** against crossing `endDate` — purely
     disable-driven, matching the existing unguarded `onPrevDay()` / `onFirstDay()`.

7. **"Last day" label:** in `relativeDayLabel()`, checking `date.equals(trip.endDate())` takes
   priority over every other branch (today/yesterday/tomorrow/first day/N days ago/in N days).
   This is the same mechanism that already produces "first day" (`tourDay == 1` fallback when
   `delta` is very negative) — "last day" is checked first and wins ties, e.g. a single-day trip
   viewed today shows "last day", not "today".

8. **`DatePicker` symmetry.** The existing day-cell-factory greys out dates before `startDate`
   (`main.fxml` date picker, `MainController` day-cell factory) and the value-listener snaps
   picks before `startDate` back to `startDate`. Both get a symmetric counterpart for
   `endDate`: grey out dates after `endDate` in the popup, and snap any manually
   typed/picked date after `endDate` back to `endDate`. Without this, a user could bypass the
   disabled `▶`/`⏭` buttons entirely by typing a later date directly into the picker.

## Non-goals

- No migration support inside the app for the `startDate` → `start_date` rename — external
  `sed` pass only, once, on the real data dir.
- No content-aware "last entry" detection for the End Trip button — existence via
  `listEntryDates` is sufficient, matching how "does this day have an entry" is determined
  everywhere else in the codebase.
