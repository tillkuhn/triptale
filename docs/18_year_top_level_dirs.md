# Todo 18 — Year Top-Level Directories

## Problem

All trips currently live flatly under `trips/<slug>/`. As old trips get imported, this
directory will grow large and hard to navigate from outside the app (file explorers, git
history). Trip slugs are also only ever checked for uniqueness by accident (there is no
collision check at all today — creating a trip whose name slugifies to an existing slug
silently overwrites that trip's `README.md`).

## Key decisions (from grill-me session)

1. **New layout:** `trips/<year>/<slug>/`, where `<year>` is the 4-digit start year of the
   trip. Clean cutover — the app only ever reads/writes this layout. No dual-layout support
   for the old flat `trips/<slug>/` structure. Existing data is migrated manually outside the
   app (`mv trips/foo trips/2025/foo`).
2. **Year is fixed at trip-creation time**, exactly like slugs are immutable today. Editing a
   trip's `startDate` afterward does **not** move its directory — directory year and displayed
   `startDate` are allowed to silently diverge (same tradeoff already accepted for slugs vs.
   trip name). No move/rename logic.
3. **`Trip.year()` is sourced from the directory it was loaded from, never recomputed from
   `startDate`.** This avoids a subtle desync: if `year()` were `startDate.getYear()`, editing
   `startDate` in memory would make `year()` silently point at a directory that doesn't match
   reality. Instead, the year is a fact carried by whatever `TripRef` was used to load the
   trip — the filesystem path is authoritative, `startDate` is purely display data.
4. **New `record TripRef(int year, String slug)`** is the trip identity type, replacing bare
   `String slug` everywhere a trip is located: `MarkdownStore` path/IO methods, `Trip` (gains a
   `year()` accessor sourced per #3), `SaveTarget` (`tripSlug` → `trip: TripRef`), and
   `MainController`'s pending-saves map key. A raw `Path`/URI was considered and rejected —
   `TripRef`'s two named fields keep year and slug independently usable (collision checks need
   slug-within-year, the year dropdown needs year alone) and are self-documenting, whereas a
   path would need re-parsing at every such call site.
5. **Slug uniqueness is per-year, not global.** `trips/2024/iceland/` and `trips/2026/iceland/`
   may coexist. `onNewTrip()` gains a collision check that didn't exist before: if
   `trips/<year>/<slug>/` already exists, block creation with an error alert (styled per the
   dialog conventions in AGENTS.md) rather than silently overwriting `README.md`. In scope for
   this todo since the per-year uniqueness change touches the same code path.
6. **New trip's year is derived from the New Trip dialog's own `startDate` field**, not from
   whatever year happens to be selected in the main year dropdown. The year dropdown is a
   browsing filter only, not an input to trip creation — after creation both dropdowns refresh
   from disk and select the new trip (see #10), so the dropdown just follows along.
7. **`MarkdownStore.listTrips()` becomes `listTrips(int year)`, scoped to one year.** No
   cross-year "list all trips" method is added — nothing in the app needs a global trip list
   today. Add one later if a real need (e.g. search) comes up.
8. **New `MarkdownStore.listYears()`:** scans `trips/*`, keeps directory names matching
   `^\d{4}$` exactly, parses to `int`. No additional numeric range sanity check (e.g. rejecting
   `0000`/`9999`) — the TODO's own "4 digit match should be enough" is taken literally.
9. **Year dropdown UI:**
   - Contents = `listYears()` result **∪ current calendar year**, always including the current
     year even if `trips/<currentYear>/` doesn't exist yet on disk, so the default/no-cache
     case is always selectable on a fresh data dir.
   - Sorted descending (newest first).
   - When the selected year has zero trips, the trip dropdown is empty and disabled (no
     placeholder text) — user creates a trip via the existing New Trip action.
10. **Trip dropdown UI:** scoped to the selected year, sorted by `startDate` descending (most
    recent trip first) — a change from today's alphabetical-by-slug sort, since slug is no
    longer shown in the dropdown label anyway (per commit `d7e71d9`) and chronological order is
    more natural for a travel diary. Label stays name-only, no year echo — the adjacent year
    dropdown already provides that context.
11. **Refresh-after-create needs no special-casing.** After `onNewTrip()` succeeds, re-run
    `listYears()` and `listTrips(year)` and select the new trip. Since both are fresh directory
    scans, a backfilled trip for an arbitrary past/future year (not "current year", not
    previously listed) is picked up automatically — no explicit "add year to dropdown" step
    needed.
12. **Last-selected-trip cache:** `.state.yml`'s `lastTripSlug` key is renamed to
    `lastTripPath`, storing a single string `"<year>/<slug>"` (e.g. `"2026/IcelandRoadtrip"`)
    rather than two separate keys. Chosen so the cache format doesn't need to change again if
    the directory partitioning scheme changes further in the future — only the parsing would.
    `MarkdownStore` owns the string ↔ `TripRef` conversion: `saveLastTripPath(TripRef)` /
    `loadLastTripPath(): Optional<TripRef>` replace `saveLastTripSlug`/`loadLastTripSlug`.
    Only written on actual trip selection (unchanged from today's behavior) — browsing the year
    dropdown without picking a trip is not persisted. If the cached path doesn't resolve to a
    real trip (deleted externally, malformed), fall back to the same default as the no-cache
    case: current year selected, first trip in that year selected (or empty/disabled if none).

## Ripple effects confirmed via codebase inventory

- **`GitService` needs no changes.** It commits the whole repo root via JGit
  `addFilepattern(".")` and has no path-depth or `trips/<slug>` string assumptions anywhere —
  confirmed by inspection of `commitAll` and all other methods.
- **`DiaryExporter` needs no signature changes.** Its public methods already take `Trip`, not a
  bare slug; it only calls `trip.slug()` internally when delegating to `MarkdownStore`, and
  those call sites get updated to build/pass a `TripRef` instead.
- **`MainController.onViewSource()`** (todo 17) calls `store.readEntrySource(trip.slug(), date)`
  and `store.entryFile(trip.slug(), date)` — both need to pass a `TripRef` instead.
- **`SaveTarget`** (`util/SaveTarget.java`, todo 8's unsaved-changes-navigation fix) changes
  from `record SaveTarget(String tripSlug, LocalDate date)` to
  `record SaveTarget(TripRef trip, LocalDate date)`, for the same year-disambiguation
  correctness reason as the pending-saves map, even though a cross-year mid-edit race is
  unlikely in practice.
- **Test churn expected** in `MarkdownStoreTest`, `DiaryExporterTest`, `GitServiceTest`, and
  `SaveTargetTest` — all currently construct `Trip`/`SaveTarget` with bare slug strings.
  `SlugsTest` is unaffected (`Slugs.toSlug` is a name→slug string transform, unrelated to trip
  identity).

## Non-goals / explicitly out of scope

- Any migration tooling or in-app support for reading the old flat `trips/<slug>/` layout.
  Existing data dirs are migrated by hand.
- Moving a trip's directory when `startDate` is edited after creation.
- A cross-year "all trips" listing/search method.
- Any change to `GitService`, `DiaryExporter`'s public API, or the export templates.
