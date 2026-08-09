# Todo 10 — Impressions Feature

## Problem

Export (HTML preview) should optionally show per-entry images ("impressions") discovered on
disk via a configurable filename pattern, without persisting the match results into entry
frontmatter.

## Key decisions (from grill-me session)

1. **Storage location:** new prefs go into `prefs.yml` (per-machine, gitignored), not
   `application.yml`/`TripTaleProperties`, since photo folder paths differ per machine.
   - `impressionsFilePattern` (string) — e.g. `${HOME}/Pictures/00_Faves/output/${DATE}*.jpg`
   - `impressionsGridColumns` (int, default `2`)
2. **`MarkdownStore` prefs API generalized:** replace the single-purpose
   `saveLastTripSlug`/`loadLastTripSlug` pair with a generic read-whole-map/write-whole-map
   (`loadPrefs()`/`savePrefs(Map)`, read-merge-write), then add typed accessors on top:
   `getLastTripSlug/setLastTripSlug`, `getImpressionsFilePattern/setImpressionsFilePattern`,
   `getImpressionsGridColumns/setImpressionsGridColumns`.
3. **Pattern variables:** only `${HOME}` (user home) and `${DATE}` (`yyyyMMdd`, entry's date).
   No `${DATE_ISO}` or others for now.
4. **Directory/glob split:** split the substituted pattern at the **last `/`** — everything
   before is the directory to scan (non-recursively via `Files.list`), everything after is a
   glob matched with `java.nio.file.PathMatcher` (`glob:` syntax) against filenames in that dir.
5. **New resolver class:** `net.timafe.triptale.storage.ImpressionsResolver` (no JavaFX
   imports, package boundary rule applies). Signature roughly:
   `List<Path> resolve(String pattern, LocalDate date)`. No caching — fresh `Files.list()` scan
   every call (directory scope is small/non-recursive, so this is cheap).
6. **Not persisted to frontmatter/domain:** `DiaryEntry` gets **no** new field. Images are
   computed live from the global prefs pattern + the entry's date, every time (UI load,
   export).
7. **UI — main view:** new `Button` in the GridPane row 1 (route row), to the right of the
   route field (its own column, not squeezed into an HBox with the route field). Label text:
   `"No Impressions"` when pattern unset or 0 matches; `"N Impressions ›"` when N > 0.
   Recomputed on entry navigation/load (same timing as other per-entry field refreshes).
8. **UI — image popup:** built **programmatically** as a `Dialog<Void>` in `MainController`
   (same style as the existing `onNewTrip` dialog), not a separate FXML/controller. Contains an
   `ImageView` plus First ⏮ / Prev ◀ / Next ▶ / Last ⏭ buttons and an `"N / total"` counter
   label. Images lazy-loaded one at a time from disk on navigation (no preloading all images).
9. **UI — preferences dialog:** new `MenuItem "⚙ Edit Preferences…"` in the `File` menu,
   placed directly after `"📤 Export Diary…"`. Opens a single small `Dialog` (GridPane, like
   `onNewTrip`) with two fields: "Impressions file pattern" (TextField) and "Impressions grid
   columns" (numeric TextField/Spinner, default 2). Saves via the generalized prefs API.
10. **Export — markdown:** `exportTrip(Trip)` stays as-is, no images, no markers — this is what
    the "Copy" button in the export dialog uses (local absolute file paths aren't portable/
    git-friendly, so they stay out of committed markdown).
11. **Export — HTML:** `exportTripAsHtml(Trip trip, boolean includeImpressions)` — when true,
    builds markdown with an embedded per-entry marker (`<!--IMPRESSIONS:yyyy-MM-dd-->`, passed
    through commonmark untouched as a raw HTML block), converts to HTML via commonmark, then
    post-processes the resulting HTML string, replacing each marker with an actual `<table>`
    image grid (`<img>` per cell, N columns from `getImpressionsGridColumns()`, N configurable
    via prefs, default 2). No new template file needed for markdown; the HTML grid is built in
    Java in `DiaryExporter`.
12. **Export dialog checkbox:** new checkbox "Include impressions (images)" in the existing
    export `Dialog` (`onExportDiary`). Only affects the "Preview in Browser" button (which
    calls `exportTripAsHtml(trip, checked)`); does not affect "Copy" / the markdown TextArea
    content. Checkbox pre-checked and enabled only when `impressionsFilePattern` is configured.
13. **Feature branch:** `feature/impressions` (this doc lives on that branch).

## Open follow-ups / non-goals

- No recursive directory search, no full glob support across path segments (only the last
  path segment after the last `/` is treated as a glob; everything before must resolve to a
  literal existing directory after variable substitution).
- No caching of directory scans across multiple entries during a single export run.
- No persistence of matched image paths into `trip.yml`/entry frontmatter — always recomputed.
