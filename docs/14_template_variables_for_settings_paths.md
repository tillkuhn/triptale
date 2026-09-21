# Todo 14 — Template Variables for Settings Paths

## Problem

`ImpressionsResolver` substitutes `${HOME}`/`${DATE}` into a configured pattern
(`impressionsFilePattern` / `impressionsFaveFilePattern`), then globs only the filename (last
path segment) against an existing, exactly-named directory. `${DATE}` alone is no longer
enough: images are organized in year/month subdirectories that differ across machines, and the
substitution/glob logic is impressions-specific even though future settings (e.g. GPX file
locations) will need the same kind of path templating.

## Key decisions (from grill-me session)

1. **New variables:** `${TRIP_SLUG}` (active trip's slug), `${TRIP_YEAR}` (4-digit year, e.g.
   `2026`), `${TRIP_MONTH}` (2-digit month, e.g. `06`). Both are sourced from the trip's
   `startDate` — **not** the individual entry's date — so every entry in a trip resolves to the
   same directory, even for entries that fall in a different calendar month/year than the
   trip's start. Consistent with `Trip.year()` already being a fixed, trip-level fact (todo 18)
   rather than something recomputed per entry.
2. **Directory segments can glob, not just the filename.** The substituted pattern is split on
   literal `/` into segments; each segment is matched one-to-one against one directory level
   (`FileSystems.getDefault().getPathMatcher("glob:...")`, same syntax already used for
   filenames today). Example: `${HOME}/Pictures/${TRIP_YEAR}/${TRIP_MONTH}_??_${TRIP_SLUG}/00_Faves/output/${DATE}*.jpg`
   walks `Pictures` → `${TRIP_YEAR}` (literal after substitution) → `${TRIP_MONTH}_??_${TRIP_SLUG}`
   (glob, since `??` remains after substitution) → `00_Faves` → `output`, then globs the
   filename against `${DATE}*.jpg` as before.
3. **Single-segment globs only — no recursive `**` descent.** Each `*`/`?` matches within one
   path component; there's no requirement to match an unknown-depth subtree for this todo.
   Keeps the walk a simple level-by-level directory listing + glob-match, no recursive scan.
4. **Ambiguous directory match → pick the first match (sorted order), log a warning.** If a
   wildcard segment matches more than one existing directory, don't fail closed — deterministically
   pick the first candidate by sorted filename and continue resolution with it, but always
   `log.warn` naming the segment and all candidates so a real collision is visible in the logs.
   Confirmed explicitly after a call-out that this differs from the fail-closed behavior used
   elsewhere in the resolver (missing directory, no trip in scope, blank pattern all return
   empty) — the user preferred "first + warn" over "no match" for this specific case.
5. **Directory resolution is cached per (pattern, trip), for the app session.** The rationale:
   for a given trip, the *directory* half of the pattern (everything through the last
   `TRIP_*`-bearing segment) is fixed for that trip's lifetime — it's either one shared central
   folder for all trips, or one folder per trip, but it doesn't change mid-trip. Cache key is
   `(pattern string, TripRef)` → resolved `Path` (or "not found"); only the final filename glob
   re-runs per date/navigation. No manual refresh/invalidation UI — if the underlying folder is
   reorganized mid-session, restarting the app is an acceptable escape hatch. Cache lives only
   in memory on the resolver's Spring singleton, not persisted.
6. **Extract a generic, reusable resolver now**, even though impressions is still the only real
   consumer today — the todo explicitly asks for reuse beyond impressions (e.g. GPX paths
   later), and the variable-substitution + segment-glob-walk logic has no impressions-specific
   behavior in it. Proposed shape: a `storage.PathPatternResolver` (or similar name) taking a
   pattern and a `Map<String, String>` of variable values, returning the resolved/matched
   `Path` list or an `Optional<Path>` directory result; `ImpressionsResolver` becomes a thin
   caller that builds the variable map (`HOME`, `DATE`, `TRIP_SLUG`, `TRIP_YEAR`, `TRIP_MONTH`)
   from a `Trip` + `LocalDate` and owns the per-(pattern, trip) directory cache.
7. **All existing call sites gain a `Trip`/`TripRef` parameter.** Today `resolve(pattern, date)`
   is called from `MainController.updateImpressionsButton`/`updateFavesButton` (date-only) and
   `DiaryExporter`'s per-entry export loop (which already has the `Trip` in scope). All of these
   move to passing the trip, not just the date — in scope for this todo, not deferred.
8. **No trip in scope when a pattern uses `${TRIP_*}` → return an empty list**, mirroring
   today's null-date/blank-pattern behavior, rather than throwing. Consistent fail-closed
   behavior throughout the resolver rather than a special case.
9. **No caching-avoidance/complexity beyond #5.** No pre-emptive invalidation logic, no
   filesystem-watch, no TTL — the session-lifetime cache is considered sufficient per the
   answer above.

## Assumption explicitly challenged

The todo asked to review the assumption that "the pattern should preferably resolve to a single
directory" as a performance optimization. On inspection, **no such optimization exists in the
current code** — `ImpressionsResolver` today requires an exact, literal directory path only
because it never globs directories at all (glob is filename-only). The perceived "performance
logic" was really just a side effect of that simplicity, not a deliberate optimization. Once
directory segments can glob (#2), the real cost concern is repeated directory scans on every
date-navigation tick — addressed not by keeping resolution single-directory, but by caching the
resolved directory per trip (#5), since the directory is stable for a trip's whole lifetime
regardless of how many segments were globbed to find it. The same cache also absorbs the cost
of an ambiguous match (#4) recomputing its "first match" pick on every navigation.

## Non-goals / explicitly out of scope

- Recursive (`**`) directory matching.
- A UI affordance to manually re-resolve/invalidate the cached directory mid-session.
- Actually wiring `${TRIP_*}` variables into any GPX-related setting — no such setting exists
  yet; this todo only makes the resolver reusable for when one is added.
- Migrating existing `impressionsFilePattern`/`impressionsFaveFilePattern` values in
  `settings.yml` — old patterns using only `${HOME}`/`${DATE}` continue to resolve identically
  since the new variables are additive.
