# 81 — General repo housekeeping task

## Problem

Several independent pieces of "janitorial" work accumulate in a data dir over time, each currently
either unimplemented or deferred ad hoc rather than tracked centrally:

- Orphaned `.exif-cache/` entries for images that were deleted/moved (deferred from todo 80 — see
  `docs/80_exif_cache.md`, "No eviction/sweep in this todo").
- `.tile-cache/` likewise grows unbounded ("never evicted here", per `OsmTileClient`'s own
  comment) — same category of problem, different cache.
- Git repo bloat in the data dir over a long project lifetime (loose objects, no local `git gc`)
  — the app never runs it itself today (`git/GitService` only does init/add/commit/status via
  JGit, and push/pull via the OS `git` binary).
  Former todo 31 ("Git Housekeeping", merged into this one) asked for exactly this as a
  "Run Housekeeping" action in the Repository menu that runs `git gc` on the data repo and
  **shows its output** to the user (e.g. `Writing objects: 100% (1027/1027), done.` /
  `Total 1027 (delta 453), reused 972 (delta 415), pack-reused 0`). Showing the result is a
  requirement for the on-demand trigger; it argues for shelling out to `git gc` (like push/pull)
  over JGit's `Git.gc()`, which returns statistics rather than that text.
- Likely more of the same kind of thing will turn up over time (e.g. `ExportTempFiles.sweep()`
  already exists as a narrow precedent — one-off, not part of a general mechanism).

Rather than bolting one-off sweep logic onto each feature (tile cache, EXIF cache, exports, ...)
as it's built, this todo is to design **one general housekeeping mechanism** that such tasks
register with.

## Requirements (from user)

- Invokable **on demand** (e.g. a menu item) **and** periodically based on time since last run
  (so it doesn't run on every single launch, but also doesn't require the user to remember it).
- Extensible: adding a new housekeeping task later (a new sweep, `git gc`, whatever else turns up)
  should not require re-plumbing the trigger/scheduling mechanism — each task just has a
  description, no mention of task completion anywhere since this file is an ADR.

## Open questions for implementation (unresolved — needs a follow-up grill-me pass)

- Where does "last run" get persisted? `.state.yml` (already gitignored, already has
  `lastTripPath`/`lastEntryDate`) is a natural fit.
- What's the default periodicity, and is it configurable (`settings.yml`)?
- Does it run synchronously with a progress dialog, or fire-and-forget in the background with a
  status-line message when done (`ui.StatusSink`, the existing pattern for background work)?
- Task list to register on day one: `.exif-cache` orphan sweep (todo 80), `.tile-cache` orphan
  sweep, local `git gc` (JGit's `Git.gc()` or shell out like push/pull already do).
- Where does the registry/runner live architecturally — a new top-level package (no obvious
  existing home; not `git`, not `storage` alone since it spans both caches and git), constructed
  in `MainController` like other services.

This doc exists to hold the problem statement and requirement; the design itself still needs a
grill-me pass before implementation.
</content>
