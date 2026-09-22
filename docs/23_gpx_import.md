# Todo 23 — Support import GPX

## Problem

New Trip only supports typing name/date/description by hand. Trips created from a recorded
ride/hike (the common case — komoot, Strava, etc. all export GPX) require the user to
manually look up the date and re-type a name that's already sitting in the file. Separately,
there's no way to seed the first day's entry with a title and start coordinates at trip-creation
time — that currently requires a second trip through Trip Details / the Coordinates dialog.

## Key decisions (from grill-me session)

1. **Entry point: import button inside `NewTripDialog`**, not a separate menu item. "Import
   GPX…" opens a `FileChooser`, parses the file, and prefills the existing Name/Start date
   fields in place. One dialog, one code path — New Trip remains the sole place a trip is
   actually persisted; the user can still edit any prefilled field before saving.

2. **New optional first-entry field, modeled as `Optional`, not a separate boolean.**

   ```java
   record Spec(String name, LocalDate startDate, String description, Optional<FirstEntry> firstEntry) {}
   record FirstEntry(String title, Double lat, Double lon) {}
   ```

   `firstEntry` empty means no day-1 entry is created — this replaces a separate
   `initFirstEntryOnCreation` boolean, since "checkbox unchecked" and "nothing to create" are
   the same state and a boolean+nullable-fields combination would allow a checked-but-empty
   state that can't happen in practice. `lat`/`lon` inside `FirstEntry` are plain nullable
   `Double` (manual entry never has coordinates; GPX import always does, given decision 5).

3. **The "init first entry" checkbox is always visible** in `NewTripDialog`, regardless of
   manual vs. import. Default is **off** for manual entry (matches today's behavior — New Trip
   creates no entries) and **on** immediately after a successful GPX import (the likely case
   when the user already has a recorded track for day 1). The checkbox is a plain toggle the
   user can still flip either way after import.

4. **`NewTripDialog` decides the `FirstEntry` title/coords, not `MainController`.** The dialog
   tracks the GPX-derived title (raw `<trk><name>`) and lat/lon separately from the live Name
   field, so that editing the Name after import doesn't change what gets stamped onto day 1:
   - Manual entry + checkbox on → `FirstEntry(currentName, null, null)`.
   - GPX import + checkbox on → `FirstEntry(gpxTrackName, gpxLat, gpxLon)` — using the
     original imported name even if the user has since edited the Name field.

   `MainController.onNewTrip()` stays dumb: after `store.saveTrip(...)`, if
   `spec.firstEntry()` is present, build a `DiaryEntry` via the builder
   (`.title(...).startLat(...).startLon(...)`, date = trip start date) and
   `store.saveEntry(ref, entry)`, then `addPending(...)` for the entry file same as the trip.

5. **GPX parsing: new `net.timafe.triptale.util.GpxImport`, JDK `DocumentBuilder` (no new
   dependency).** Distinct from `Coordinates.tryParse`, which regex-matches a single pasted
   `<trkpt>` snippet — this needs real document structure: first `<trk>/<name>`, and the first
   `<trkpt>` of the first `<trkseg>` of that first `<trk>`, in document order (multi-track/
   multi-segment files are common; only the first matters per the todo). Lives in `util/`
   alongside `Coordinates`/`Slugs` — no JavaFX involved, no package-boundary concern.

   ```java
   public record Parsed(String name, LocalDate date, double lat, double lon) {}
   public static Parsed parse(Path file) // throws GpxImportException on any failure
   ```

6. **Date derivation: UTC calendar date of the first trkpt's `<time>`, taken as-is** — parse
   `<time>` as `Instant`, format with `DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC)`
   (or equivalent), no conversion to the system default zone. Simple and deterministic; a
   midnight-UTC edge case shifting the local date by one is an acceptable trade for not having
   to reason about which zone "should" apply to a recorded track.

7. **All-or-nothing import.** If the file has no `<trk>/<name>`, no `<trk>/<trkseg>/<trkpt>`,
   or the first trkpt's `<time>` doesn't parse, the whole import fails: nothing in
   `NewTripDialog`'s fields changes, and an inline hint label reports the failure — same
   pattern `CoordinatesDialog` already uses for its own parse-failure case (a label in the
   dialog, not a separate `Alert`). No partial prefill (e.g. name filled in but date left blank).

8. **Name used verbatim, no emoji/hashtag stripping.** The imported `<trk><name>` (which may
   contain emoji, `#hashtag`-style tokens, etc. — see sample file) goes straight into the Name
   field exactly as if the user had typed it. The user reviews/edits it in the dialog before
   Save creates the slug, same opportunity they'd have with a manually-typed emoji-laden name.

## Sample file

`~/tmp/rheinrauf.gpx` (komoot export) — `<trk><name>` has emoji + long descriptive text,
multiple `<trkseg>` points, `<time>` as UTC `Z`-suffixed ISO instants.

## Non-goals

- No migration or handling for GPX route files without track points (`<rte>`/`<rtept>`) — only
  `<trk>/<trkseg>/<trkpt>` is read.
- No support for picking a different track/segment within a multi-track file — always the
  first `<trk>`, first `<trkseg>`, first `<trkpt>`.
- No name cleanup (emoji/hashtag stripping) — deferred to manual user edit if wanted.
