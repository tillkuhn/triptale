# Todo 33c — Attachment delete propagation (sync base)

Design decided in [ADR-0002](adr/0002-attachment-sync.md); implemented as described below.
Follows 33a (push) and 33b (pull) of [todo 33](33_object_storage_for_trip_attachments.md).

## Problem

Cloud attachment sync was "copy both ways, never delete" (`sync.BucketSyncer`). A locally
deleted attachment was downloaded again by the next pull, and a stale local copy could overwrite
a newer remote edit. Since todo 47 lets the impressions viewer import many images at once (and
todo 61 renders trip maps into the same tree), mistaken adds need a real delete that sticks.

## Implementation

1. **`ObjectStore.delete(key)`** + `S3Client` implementation (`DELETE /<key>`, SigV4).
2. **`sync.SyncBase`** (immutable record: `bucketUrl`, `keyPrefix`, `files: {relative path: md5
   hex}`) + **`sync.SyncBaseStore`** (Jackson YAML load/save). A gitignored file per synced
   folder in the data dir root — `.attachments-sync.yml` and `.radio-sync.yml` (`GitService`'s
   `GITIGNORE_ENTRIES`) — so both `attachments/` (this todo) and `radio/` (todo 52, Sync Tracks)
   get delete propagation from the same engine. Missing, unreadable, or a different
   bucket/prefix all load as `SyncBase.empty()`, which can never cause a delete.
3. **`BucketSyncer.plan(root, keyPrefix, base)`** classifies every relative path (union of
   local/remote/base) into one `Action`: `DOWNLOAD`, `UPLOAD`, `DELETE_LOCAL`, `DELETE_REMOTE`,
   `DROP_BASE` (gone on both sides), or `NOOP` (unchanged, or newly in sync — still (re)records
   its MD5 so the base catches up). Exactly the ADR-0002 table. Replaced the old `pull`/`push`
   methods entirely — `sync(root, keyPrefix, base, plan, trash, progress, checkpoint)` executes a
   prepared plan in one pass.
4. **`sync.Trash`** interface + **`sync.DesktopTrash`** (`java.awt.Desktop.moveToTrash`, falling
   back to a plain delete with a log warning where unsupported) — kept behind the interface so
   `BucketSyncerTest` uses a fake instead of touching AWT.
5. **Base checkpointing**: `sync()` takes a `Consumer<SyncBase>` called after every finished file
   (transfer, delete, or base-only update), so an exception partway through a run still leaves
   the base reflecting exactly what finished.
6. **UI** (`ui/dialog/BucketSyncStep`, shared by `SmartSyncDialog`'s Backpack row,
   `SyncTracksDialog`'s Tracks row, and the new `SyncAttachmentsDialog`): shows "↓ n ↑ n ✕ n local
   ✕ n remote". A plan whose `Plan.exceedsDeleteThreshold()` (> 20 files or > 25% of the tree,
   total across both directions) adds an explicit "Yes, delete N files" checkbox under the row;
   the dialog's Sync button stays disabled until it's ticked.
7. **Menu**: the old one-directional "⛅ Push/Pull Backpack" items (which couldn't express a
   delete — pull-only or push-only can't classify against a base that needs both sides) were
   replaced by a single "⛅ Sync Backpack…" item opening `SyncAttachmentsDialog`, modeled on
   `SyncTracksDialog`.

Deliberately **not** done in this pass (deferred, see "Open questions"):
- `ImageViewerDialog`'s "🗑 Delete Selected" action — the motivating pain point (re-rendered
  `map-*.png` trip files) isn't a day-folder image the viewer shows anyway; a Finder/trash delete
  already propagates correctly through the sync engine above. Revisit if a viewer-side delete
  turns out to be worth it for day-folder attachments specifically.
- Bucket versioning + lifecycle in `terraform/`. Local deletes already go to the OS trash;
  revisit if an accidental remote delete needs a safety net too.

## Tests

`BucketSyncerTest`: every row of the ADR table (no base: new/new/identical/differing; with base:
deleted-local, deleted-remote, edit-beats-delete both ways, edited-one-side both ways,
edited-both-sides local-wins, deleted-both-sides drops from base, unchanged is a no-op),
plus hidden files skipped, delete-threshold by count, `sync()` executing a mixed plan and
checkpointing the base. `SyncBaseStoreTest`: missing/corrupt/foreign-bucket all load empty,
round-trip, parent dir creation.

## Open questions

- Should "edited on both sides" keep a `name (conflict).ext` copy instead of local-wins?
  Deferred in the ADR, revisit if it bites.
- Rename support for todo 42: reuse copy + delete through the same base, or a dedicated
  rename that updates the base keys in one go?
- Worth adding `ImageViewerDialog`'s delete action later for day-folder attachments (not maps)?
- Bucket versioning/lifecycle in `terraform/`, if a remote delete ever needs undoing.
