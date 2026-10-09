# ADR-0002: Sync attachments as a three-way merge against a per-device sync base

Status: Accepted. The copy-both-ways part is implemented (todos 33a/33b); the sync base that
propagates deletes is implemented by todo 33c.

## Context

Trip attachments (GPX tracks, photos imported as impressions, …) live in the data dir under
`attachments/<year>/<slug>/<day>/`, one folder per entry day
([docs/33a_attachments_mvp.md](../33a_attachments_mvp.md)). The data dir itself is a git
repo, but binaries don't belong in it, so `attachments.sync` offers three modes:

- `off`: local only, `attachments/` is gitignored.
- `git`: committed with the `.md` files. Git already handles adds, edits and deletes.
- `cloud`: gitignored and synced with an S3 bucket. This ADR is about that mode.

Constraints:

- **No AWS SDK** (too heavy for the app): a small hand-written SigV4 client
  (`attachments.S3Client`, `java.net.http`) behind the `ObjectStore` interface, so R2 or another
  S3-compatible store can be swapped in.
- **Several devices, often offline**, each with its own copy of `attachments/`. A device may be
  brand new with an empty folder, or may not have synced for months.
- **Low effort per sync:** one `ListObjectsV2` for the whole tree, size + MD5 against the ETag
  (single-part PUTs only, so the ETag is the MD5), one file at a time (1 GB RAM notebook).
- Attachments are mostly **added**, rarely edited, and until todo 47 rarely deleted. Importing
  impressions from the viewer makes bulk adds easy, and so it makes mistaken adds that need
  deleting much more likely.

The first version (33b) is **copy both ways, never delete**: pull downloads objects with no local
file and never overwrites; push uploads local files that are new or differ (local wins). It's
simple and can't lose data, but has two flaws:

1. **Deletes don't propagate, they come back.** Deleting `b.jpg` locally doesn't remove it from
   the bucket, and the next pull downloads it again. The only way to delete is on every
   device and in the bucket by hand.
2. **Remote edits are overwritten.** If device A edits a file and pushes, device B (still holding
   the old version) sees "differs" and pushes its stale copy over A's edit.

A two-way comparison (local vs. remote) can't fix either. It can't tell "deleted here" from
"never had it", or "edited there" from "edited here". Using the absence of files as a delete
signal is dangerous: a fresh device with an empty folder would wipe the bucket.

## Decision

Sync as a **three-way merge** against a **sync base**: per device, the set of files
(key → MD5) that were identical locally and remotely at the end of this device's last
successful sync. The approach of Unison and `rclone bisync`.

| Base | Local | Remote | Meaning | Action |
|---|---|---|---|---|
| – | ✓ | – | new here | upload |
| – | – | ✓ | new elsewhere | download |
| – | ✓ | ✓ (differs) | added on both sides | local wins: upload (as today) |
| ✓ | – | ✓ unchanged | deleted here | delete remote |
| ✓ | ✓ unchanged | – | deleted elsewhere | delete local |
| ✓ | – | ✓ changed | deleted here, edited elsewhere | download (edit beats delete) |
| ✓ | ✓ changed | – | edited here, deleted elsewhere | upload (edit beats delete) |
| ✓ | ✓ unchanged | ✓ changed | edited elsewhere | download (fixes flaw 2) |
| ✓ | ✓ changed | ✓ unchanged | edited here | upload |
| ✓ | ✓ changed | ✓ changed | edited on both sides | local wins: upload (as today) |
| ✓ | – | – | deleted on both sides | drop from base |

"Changed" means the MD5 differs from the base entry.

Rules that make it safe:

- **No base means no deletes.** The base is a gitignored, per-device file in the data dir (like
  `.state.yml`), never synced. A missing, unreadable or foreign base (it records the bucket URL
  and key prefix; a different bucket invalidates it) is treated as empty. The sync then falls
  back to today's union copy. A fresh device, a corrupt state file or a switched bucket can
  never cause a delete.
- **The base is updated per file, after each successful transfer or delete**, and written at the
  end of the sync. An interrupted sync leaves a base that's merely out of date, which is safe:
  unfinished files are classified again next time.
- **Deletes are visible and gated.** The Smart Sync plan shows "delete N local / N remote" next
  to the download/upload counts. A sync that would delete more than a threshold (e.g. 20 files
  or 25 % of the tree) stops and asks for confirmation. That catches a deleted or mis-mounted
  `attachments/` folder or a wrong data dir.
- **Deletes are recoverable on both sides.** Local deletes (sync and the viewer's Delete action)
  move files to the OS trash. The bucket gets versioning plus a lifecycle rule that expires
  noncurrent versions after ~30 days (OpenTofu, `terraform/`), so a remote delete only adds a
  delete marker.

## Consequences

- Easier: delete a wrongly imported attachment once, on any device, and the delete reaches the
  others with their next sync. Edits made elsewhere are no longer overwritten by stale copies.
- Easier: the same mechanism gives todo 42 (slug rename) a working remote side. S3 has no
  folders, so a rename is copy + delete, and the delete now propagates.
- Harder: sync has state. The base file and its edge cases (interrupted syncs, bucket switch,
  clock-free MD5 comparison) need careful tests. It's per device, so debugging "why did this
  file disappear" means looking at that device's base.
- Harder: the IAM user already has `s3:DeleteObject`, but `ObjectStore` gains a `delete`, and
  versioning adds a little storage cost for noncurrent versions until they expire.
- Given up: conflict handling stays simple. "Edited on both sides" still means local wins, with
  no "keep both" copy. Fine for photos and GPX tracks; revisit if it bites.
- Unchanged: `off` and `git` modes; hidden files and folders are still skipped in both
  directions.
