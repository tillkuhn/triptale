# Todo 33c — Attachment delete propagation (sync base)

Status: **planned 2026-10-09**, not started. Design decided in
[ADR-0002](adr/0002-attachment-sync.md); this doc is the implementation plan. Follows 33a (push)
and 33b (pull) of [todo 33](33_object_storage_for_trip_attachments.md).

## Problem

Cloud attachment sync is "copy both ways, never delete" (`sync.BucketSyncer`). A
locally deleted attachment is downloaded again by the next pull, and a stale local copy
overwrites a newer remote edit. Since todo 47 lets the impressions viewer import many images at
once, mistaken imports need a real delete.

## Plan

1. **`ObjectStore.delete(key)`** + `S3Client` implementation (`DELETE /<key>`, SigV4). The IAM user
   already has `s3:DeleteObject`.
2. **Sync base store** (`attachments.SyncBase`, no JavaFX): load/save a gitignored file in the
   data dir, e.g. `.attachments-sync.yml`, holding `bucketUrl`, `keyPrefix` and `files: {relative
   path: md5}`. Missing, unreadable or different bucket/prefix → empty base. Add the file to
   the data dir's `.gitignore` handling next to `.state.yml`. Since todo 52 the same syncer
   (`sync.BucketSyncer`) also mirrors `radio/`, so make the base per folder (e.g.
   `.<dir>-sync.yml`, `sync.SyncBase`) and let Sync Tracks use it too.
3. **`BucketSyncer.plan()`** becomes the three-way classification from the ADR table and
   returns per-file actions: download, upload, delete local, delete remote, drop from base. The
   `Plan` record gains `deleteLocal`/`deleteRemote` counts. `pull`/`push` run off that plan
   (pull first, then push, as today).
4. **Local deletes go to the OS trash** (`java.awt.Desktop.moveToTrash`, falling back to a plain
   delete with a log warning where unsupported). AWT isn't JavaFX, so the package rule allows
   it, but hide it behind a small `Trash` interface so headless tests don't touch AWT, and
   check that `Desktop` works alongside JavaFX on macOS.
5. **Base updates** per finished file (upload/download/delete), written at the end and also on
   failure, so an interrupted sync keeps what's done.
6. **Smart Sync UI** (`ui/dialog/SmartSyncDialog`): the Attachments row shows "↓ n ↑ n ✕ n local
   ✕ n remote". Above a threshold (20 files or 25 % of the tree) the row needs an explicit
   confirmation before it runs. The ☁ Push / ☁ Pull menu items: decide whether they stay
   one-directional (no deletes) or become "☁ Sync Attachments".
7. **Viewer Delete action**: in `ImageViewerDialog` with source Trip Attachments, "🗑 Delete
   Selected" (with confirmation) moves the selected files to the trash; the next sync
   propagates it. Refresh counts via the existing `onImported`-style callback (rename it to
   `onChanged`).
8. **Bucket versioning** in `terraform/main.tf`: `aws_s3_bucket_versioning` (Enabled) plus an
   `aws_s3_bucket_lifecycle_configuration` expiring noncurrent versions after 30 days. Apply
   with `tofu apply`; note it in `terraform/README.md`.

## Tests

Unit tests against an in-memory `ObjectStore` for every row of the ADR table, plus: empty
base never deletes; foreign bucket resets the base; interrupted sync (exception mid-way) keeps
a consistent base; mass-delete threshold reported in the plan; hidden files still skipped.

## Open questions

- Scope of the threshold: per sync run or per side? (Suggest: total deletes per run.)
- Should "edited on both sides" keep a `name (conflict).ext` copy instead of local-wins?
  Deferred in the ADR, revisit if it bites.
- Rename support for todo 42: reuse copy + delete through the same base, or a dedicated
  rename that updates the base keys in one go?
