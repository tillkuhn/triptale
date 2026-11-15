# 42 Trip slug rename

## Problem
Trip slugs were immutable because the path is referenced: `belongs_to: "[[<year>/<slug>/README]]"`,
Tolaria wikilinks `[[path/file]]`, attachment folders and inline image paths. Users want to rename
a slug, and to choose a slug independent of the title, also when creating a trip.

## Decisions
- **Cloud attachments:** rename only the local `attachments/<year>/<slug>/` folder. `BucketSyncer`
  sees delete + upload on the next sync (todo 33c propagates deletes). No S3 CopyObject: S3Client
  has only list/put/get/delete, and a rename would otherwise need partial-failure handling and
  online access. Cost: a re-upload; the delete threshold prompt (20 files / 25%) may fire.
- **Preconditions:** refuse while the day is dirty or git has uncommitted changes (ask for Smart
  Sync first), so the rename is one clean, revertable commit.
- **Reference rewrite:** every `.md` under all `<year>/` dirs plus root files. Exact path-prefix
  match (`<year>/<slug>/` so `trip` does not match `trip-2`). Rewrites `[[<year>/<slug>/...]]`
  (keeping `|alias`, `#anchor`), `belongs_to`, attachment paths and `![](...)` inline images.
- **Other devices:** documented only. Git carries the folder move; attachments follow via sync.
- **Slug input:** editable field, separate from the title, strict validation (equals the
  `Slugs.toSlug` form: lowercase kebab, no blanks/accents; unique within the year; differs from the
  current one), inline error, no silent normalization. A "Generate from title" button fills it.
  New Trip uses the same field, pre-filled live from the title until edited by hand. Trip Details
  gets a "Rename" button behind the slug.
- **Finish:** save to disk, `addPending("Renamed trip X to Y")`, update `lastTripPath`, reselect
  the trip under the new `TripRef`, report "N references updated". No auto-commit.
- **Failure:** build the full plan first (dirs to move, files to rewrite, target free), then
  execute. On a mid-way failure report and leave git to revert (clean state is required).

## Files
- new `storage/TripRenamer` (no JavaFX)
- `storage/MarkdownStore`, `util/Slugs`
- `ui/dialog/NewTripDialog`, `ui/dialog/TripDetailsDialog`, `ui/MainController`
- `attachments/AttachmentsDir`, `.triptale/state.yml` handling

## Open
- Pending-map keys and `.triptale/sync/` bases that hold the old path.
