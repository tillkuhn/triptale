# Todo 33 — Object storage for trip attachments

Status: **decisions D1–D6 made, AWS resources provisioned (D5), storage settings in the app (D6) — sync not implemented yet.** Next step is a
grill-me session on the open questions below.

## Decisions

**D1 — Offline-first, remote works like the git remote (2026-09-28).** Offline storage is a
must. While you work in the app, attachments are ordinary **local files**, like a checked-out
repo. The remote storage plays the same role for attachments that the GitHub remote plays for
the `*.md` files: local and remote are reconciled **only during sync**, in both directions
(the local changes go up and the remote changes come down, like push and pull).

What this means:
- The app reads and writes only the local attachment dir. Nothing in the normal editing flow
  touches the network.
- Syncing is **two-way** and happens inside the app, alongside the git sync (current Sync
  action, or Smart Sync from todo 32). Connectivity checks and offline handling should follow
  the same rules as git push/pull.
- **Option C as originally described is effectively out:** an app that works directly against
  cloud storage and needs its own cache. D1 makes the local folder the real working copy
  instead. This is different from an **in-app sync client** (our own S3 client that only
  reconciles the local folder with the bucket during Sync). That is option B done inside the
  app instead of by an external binary, and it fits D1 perfectly (see D2).
- **Option A (sync client's folder) still fits "local files", but doesn't fit "sync happens in
  our Sync step".** A client like Drive for desktop syncs whenever it wants, outside the app's
  control. It stays useful as a zero-code interim solution, but it is not the target design.
- **Option B (sync step, either external tool or in-app) is the natural fit.** Sync becomes
  one more step of the Sync action, next to the git pull and push.

**D2 — Must run on a small Linux notebook, so we build our own S3 client inside the app, use R2
or S3 Standard as storage, and drop Google Drive (2026-09-28, confirmed).** TripTale also runs on a tiny Linux
notebook with **1 GB RAM in total**. That changes the trade-off:
- **Google Drive for desktop has no Linux version**, so option A with Drive is out: not
  cross-platform, not even as a stopgap. Linux workarounds (rclone mount,
  google-drive-ocamlfuse, the paid Insync, GNOME Online Accounts) all bring their own setup or
  footprint. **With that, Google Drive loses its main advantage**, and it was the main reason to
  prefer rclone.
- **rclone footprint:** the binary is ~86 MB on disk (measured on macOS; all backends built
  in). While running it's a separate process next to the JVM. Its memory use grows with
  parallel transfers and read-ahead buffers (by default several transfers, each with a
  buffer), so tens of MB up to ~100 MB. It can be reduced with flags (`--transfers 1
  --checkers 2 --buffer-size 0`). A slim build with only the S3 backend is possible but means
  compiling it yourself. Feasible, but noticeable on 1 GB.
- **Our own S3 client** runs inside the already-running JVM: no extra process, no install, no
  extra disk. Using `HttpRequest.BodyPublishers.ofFile(...)` for uploads and
  `HttpResponse.BodyHandlers.ofFile(...)` for downloads keeps memory flat, since files are
  streamed rather than loaded, so photos don't add to memory. Only the file listing is held in
  memory (a few hundred entries per trip, negligible).
- Storage behind it: **Cloudflare R2** (10 GB free forever, free downloads) or **S3 Standard**
  (existing AWS subscription, a few cents a month). Both work with the same client, only the
  settings differ.

**D3 — Attachments are linked to entries by file name (2026-09-28).** *Replaced by todo 33a:
one folder per day under `attachments/` in the data dir, see
[33a](33a_attachments_mvp.md).* Like impressions: every
file in the trip's attachment dir whose name starts with the entry's date belongs to that day,
e.g. `2026-06-04-track.gpx`. No new frontmatter key and no `trackurl` reuse, so the `.md`
files and the attachment dir can't drift apart. The prefix uses `YYYY-MM-DD` (same as the
entry filenames `YYYY-MM-DD-Weekday.md`), not the `yyyyMMdd` of the impressions `${DATE}`
variable. Files without a date prefix belong to the trip as a whole.

**D4 — "Import GPX…" always copies the GPX file into the attachment dir (2026-09-28).** The
copy is named `<YYYY-MM-DD>-<original file name>.gpx`, using the date the import already reads
from the GPX (`util.GpxImport`). An existing file with the same name is overwritten, since
re-importing the same track is the usual reason for a clash and the file is only local until
the next sync. No checkbox and no separate "Add attachment" step for GPX.

**D5 — S3 Standard on AWS, provisioned with OpenTofu, dedicated bucket-only key (2026-10-02).**
The storage behind our own S3 client (D2) is **AWS S3** for now, not R2:
- Bucket **`triptale-attachments`** (since D7: **`triptale-backpack`**) in **`eu-central-1`**: private (all public access blocked),
  SSE-S3 encryption, no versioning, Standard storage class.
- The app gets its **own IAM user `triptale-app`**, not the owner's admin credentials. Its
  inline policy allows only `ListBucket`/`ListBucketMultipartUploads`/`GetBucketLocation` on
  the bucket and `GetObject`/`PutObject`/`DeleteObject`/multipart actions on its objects.
  Nothing else in the account.
- All of this lives in **`terraform/`** (OpenTofu, see `terraform/README.md`). The repo stays
  generic: account-specific values (region, admin profile, state bucket) go in a gitignored
  `*.auto.tfvars`, and the bucket and user names derive from `app` (default `triptale`).
- State is remote in the existing state bucket under `triptale/terraform.tfstate`, with S3
  native locking. **The state holds the secret access key**, so `make key` in `terraform/`
  is how you get the key ID and secret back to fill in the app settings.
- Client consequences: endpoint `https://s3.eu-central-1.amazonaws.com`, signing region
  `eu-central-1`, no `x-amz-storage-class` header.

**D6 — Storage settings live in `settings.yml`, edited in the Settings dialog (2026-10-02).**
A small first step so the provisioned credentials can be stored before any sync code exists.
Nothing reads them yet.
- The Settings dialog has an attachments section with four fields: **bucket URL**
  (`s3://bucket` or `s3://bucket/prefix`), **region**, **access key ID** and **secret access
  key** (masked `PasswordField`, no reveal toggle; `make key` in `terraform/` shows it again).
- **AWS only, no provider selector.** SigV4 needs the region, which an `s3://` URL doesn't
  carry, so region is its own field. It defaults to `eu-central-1` (the D5 bucket). R2 can
  later add an optional endpoint field; "endpoint set" then means non-AWS, still without a
  selector.
- Stored as a nested group in `settings.yml`: `attachments: {bucketUrl, region, accessKeyId,
  secretAccessKey}`. The URL is stored as typed. `config.BucketUrl` parses it into bucket and
  prefix (scheme case-insensitive, slashes around the prefix dropped, S3 bucket naming rules).
- **Validation:** a malformed bucket URL or region keeps the dialog open with an inline error.
  Blank values are allowed, so the section can be filled in step by step. A blank bucket URL
  will mean attachment sync is off.
- **The secret is plain text in `settings.yml`**, which is machine-local and outside the git
  data dir. `SettingsStore` keeps the file owner-only (`rw-------`) on POSIX systems, like
  `~/.aws/credentials`. An OS keychain was rejected: platform-specific and not small.
  An AWS profile in `~/.aws/credentials` was rejected too: it assumes the AWS CLI setup, and
  the settings dialog is where the rest of the configuration already lives.

**D7 — "Backpack" in the UI, bucket `triptale-backpack` (2026-10-10).** "Attachments" sounded like
mail/office, so the user-facing name is now **Backpack**, with 🎒 instead of 📎: the menus (🎒 Add to
Backpack…, ☁ Push / Pull Backpack), the Settings labels, the Smart Sync row, the image viewer source
"Trip Backpack" and the README. The Impressions button uses 📷 instead of 🖼, to set it apart.
**Only the labels changed.** The data-dir folder `attachments/`, the S3 key prefix `attachments/`, the
`attachments:` key in `settings.yml` and the Java package keep their names, so no device needs a
migration. The bucket moved from `triptale-attachments` to `triptale-backpack` (`<app>-backpack` in
`terraform/`). Plain `triptale` is taken by another AWS account. S3 can't rename buckets, so the move
was done by hand: tofu forgot the old bucket (`removed { destroy = false }`) and created the new one,
the objects were copied with `aws s3 sync` and the admin profile, then the old bucket was deleted
and the `removed` blocks were dropped again. A full rebrand of the folder and prefix was rejected:
it needs a migration on every device, and `triptale-backpack/backpack/...` would bring back the
redundancy this change removed.

## Problem

Some trip files shouldn't go in the git data dir, which stays focused on versioned `*.md`
files. Examples: imported images that match the impressions pattern, and GPX files stored with
a tale entry. These should live in some kind of cloud storage.

Constraints from the todo:

- AWS S3 is the first idea (there's an existing AWS subscription), but the Java SDK is very fat
  and the integration feels like overkill.
- Google Drive would be nice because other travel files are already there, but it's unclear
  whether its API is usable.
- Other suggestions are welcome, as long as ~1–2 GB is **free** — no new subscription besides
  AWS.

## Current state (as of 2026-09-28)

- **Impressions** are read from local disk only. `storage.ImpressionsResolver` resolves the
  `impressionsFilePattern` / `impressionsFaveFilePattern` settings (`config.AppSettings`) with
  the help of `storage.PathPatternResolver`. The variables are `${HOME}`, `${DATE}`
  (`yyyyMMdd`), `${TRIP_SLUG}`, `${TRIP_YEAR}` and `${TRIP_MONTH}`. Example:
  `${HOME}/Pictures/${TRIP_YEAR}/${TRIP_MONTH}_??_${TRIP_SLUG}/00_Faves/output/${DATE}*.jpg`.
  The resolved directory is cached per `(pattern, trip)`.
- **GPX files** are parsed by `util.GpxImport` (todos 23/24, see `docs/23_gpx_import.md` and
  `docs/24_gpx_import_tale_entry.md`). The derived data (name, date, start/stop coordinates) is
  kept, and the source file is also copied into the day's attachment folder (D4, see
  `docs/33a_attachments_mvp.md`).
- **Entries already have a `trackurl` frontmatter key** (see `docs/04_trackurl_plan.md`). A
  cloud link to a GPX file could reuse it.
- **We already shell out to an OS binary:** `git push` / `git pull` run through `ProcessBuilder`
  with a 120 s timeout (`git.GitService`). This is a precedent for delegating sync to an
  external tool.
- The package boundary rule applies. Any storage/sync code belongs in a non-UI package
  (`storage` or a new `attachments` package) with no JavaFX imports.

## Key point: stay local-first, whatever the provider

The app is often used while travelling and offline. So the app should always read attachments
from a **local folder**, and syncing that folder to the cloud should be a separate step. The
real architectural choice is who does the syncing:

| Option | Who syncs | Code in TripTale | Works offline |
|---|---|---|---|
| **A. A sync client's folder** | Google Drive for desktop, iCloud Drive, Dropbox… | ~none (only a path pattern) | yes |
| **B. Call an external tool** | `rclone` or `aws s3 sync` binary via `ProcessBuilder` | small, same pattern as `git push` | yes, syncs when back online |
| **C. Built-in API client** | TripTale itself (SDK or REST) | large: auth, retries, local cache, conflicts | only with its own cache |

## Provider comparison (1–2 GB, free or near-free)

| Provider | Free tier | API / integration | Notes |
|---|---|---|---|
| **Google Drive** | 15 GB (shared with Gmail/Photos) | Drive API v3 with OAuth2. The Java client (`google-api-services-drive` + oauth libs) is a few MB; plain REST over `java.net.http` + Jackson (already a dependency) is also possible. | See the Drive API catch below. Drive for desktop also mounts Drive at `~/Library/CloudStorage/GoogleDrive-<account>/My Drive`, which gives option A with zero code. |
| **AWS S3** | 12-month free tier for new accounts only; after that ~$0.023/GB/month (≈ $0.05/month for 2 GB), plus download fees | Full AWS SDK v2 is heavy. Lighter paths: call `aws s3 sync` (option B), or hand-roll SigV4 request signing over `java.net.http` (~150 lines). | Existing subscription, so the cost is cents. Presigned URLs could fill `trackurl` or export links. |
| **Cloudflare R2** | 10 GB forever, no download fees | Same API as S3 (same client code as S3) | Free account. The best free option that speaks the S3 API. |
| **Backblaze B2** | 10 GB | Same API as S3 | Similar to R2. |
| **OneDrive** | 5 GB | Microsoft Graph API | No clear advantage over Drive here. |
| **Dropbox** | 2 GB | Dropbox API | Tight on space. |
| **Git LFS (GitHub)** | Limited free quota (check current numbers) | `git lfs` | Only pointer files end up in the repo, but it still ties attachments to git and GitHub's quota. The todo wants to avoid git for these. |
| **iCloud Drive** | 5 GB | No usable public API from Java | Option A only (local folder under `~/Library/Mobile Documents/...`). |

### The Google Drive API catch

- **Narrow permission (`drive.file` scope):** doesn't need Google's review, but the app can only
  see files it created itself (or that the user opened through a Google Picker). Existing travel
  files already in Drive would be **invisible** to it.
- **Full Drive access (`drive` / `drive.readonly`):** these are restricted scopes. A published
  app would need Google's verification.
- **Staying in "Testing" mode:** refresh tokens expire after **7 days**, so a weekly
  re-login.
- None of this applies with option A (Drive for desktop) or with rclone (option B), which uses
  its own registered client and handles the login itself.

### rclone (worth a closer look)

- One binary, one integration that covers Drive, S3, R2, B2, OneDrive, Dropbox and more.
- Login and credentials are handled by `rclone config` (stored outside TripTale).
- TripTale would only run e.g. `rclone copy <localAttachmentDir> <remote>:<path>` through
  `ProcessBuilder`, like `git push`. It adds no Java dependencies.
- You can switch providers later just by changing the remote name in settings.
- Downside: one more external binary that has to be installed and configured. Note it in the
  README, and degrade gracefully when it's missing, like the git binary.

## Recommendation (updated after D2)

1. **Separate "where the files live" from "how they are synced."** Add a per-trip attachment
   directory outside the git repo, set by a path pattern like the impressions settings. For
   example, a new `attachmentsDirPattern` setting:
   `${HOME}/Pictures/triptale-attachments/${TRIP_YEAR}/${TRIP_SLUG}` (reuse
   `PathPatternResolver`).
2. **Target design — our own small S3 client inside the app,** wired into the Sync action
   (todo 32 Smart Sync could show it as another row next to Pull and Push). It uses
   `java.net.http` plus hand-written SigV4 signing and needs no new dependencies. Settings:
   endpoint, region, bucket, access key, secret key. Storage: **R2** (free) or **S3 Standard**.
3. **rclone stays the fallback** if Google Drive or other non-S3 providers are ever needed.
   The sync step could hide behind a small interface so a second implementation that calls
   rclone can be added without touching the UI.
4. **No stopgap via Google Drive for desktop:** it doesn't exist on Linux (D2).
5. **Option C (app works directly against the cloud, with a cache) is out** (see D1).

### rclone: license, supported OS, footprint

- **License:** MIT. Using it is fine, and so is bundling the binary with the app, as long as
  the license notice is included.
- **Supported OS:** macOS (Intel and Apple Silicon), Linux, Windows, plus the BSDs. Same
  command-line interface on every platform.
- **Footprint:** one self-contained Go binary with no runtime dependencies. It's fairly large
  because every storage backend is compiled in: a download of a few tens of MB, and a bit more
  once unpacked (check current release sizes). Much smaller than the AWS CLI, which ships its
  own Python.
- **Install:** `brew install rclone`, the official install script or zip download, `winget` /
  `scoop` / `choco` on Windows. Linux distro packages are often outdated, so the official
  download is better there.
- **Maturity:** active since 2012 and widely used.
- **Configuration:** stored by default in `~/.config/rclone/rclone.conf` (`%APPDATA%\rclone`
  on Windows). Remotes can also be defined entirely through environment variables
  (`RCLONE_CONFIG_<NAME>_TYPE=s3`, …) or a separate file via `--config`. TripTale could
  therefore keep its own remote settings and pass them to rclone, without users ever running
  `rclone config` — except for Google Drive, whose browser login still has to go through
  rclone.

### Keeping the external-tool burden low

Unlike git, rclone usually isn't installed yet, so it's an extra setup step. Ways to reduce
it:
- Make attachment sync **optional**: the feature is off (the Sync step is simply skipped) until
  an rclone remote is configured in settings.
- Look for `rclone` on `PATH`, and add an optional setting for the binary's full path (useful
  for GUI launches on macOS, where the `PATH` often lacks `/opt/homebrew/bin`).
- Later, optionally ship rclone inside the macOS app bundle (see
  `docs/05_macos-app-bundle.md`). The MIT license allows it, but it means one binary per
  architecture, code signing, and a bigger download.

### Alternative with no external tool: our own small S3 client

If S3-compatible storage is enough (**S3, R2, B2**, not Google Drive), a small client inside
TripTale is feasible without the AWS SDK:
- Use `java.net.http` plus hand-written SigV4 request signing (~150 lines) for
  `PUT`/`GET`/`ListObjectsV2`.
- The diff logic is ours to write: compare the file listing (size, modified time, ETag) with
  the local folder, then copy what's missing or newer. For the copy-both-ways approach this is
  a few hundred lines.
- Pros: no external binary, pure Java, unit-testable in `storage`.
- Cons: only S3-compatible storage (Drive would need a separate OAuth client), and we
  maintain the sync logic and edge cases (big files, retries, special characters in file names)
  ourselves.

#### S3 vs R2 compatibility (for our own client)

- R2 implements the **S3 API**. Every call a copy-both-ways sync needs is supported:
  `PutObject`, `GetObject`, `HeadObject`, `ListObjectsV2`, `DeleteObject`, multipart upload.
  Same SigV4 signing.
- Only the connection settings differ:

  | | AWS S3 | Cloudflare R2 |
  |---|---|---|
  | Endpoint | `https://s3.<region>.amazonaws.com` (or derived from the region) | `https://<account_id>.r2.cloudflarestorage.com` |
  | Region used in signing | real region, e.g. `eu-central-1` | `auto` |
  | Credentials | IAM access key + secret | R2 API token → access key ID + secret |

- So one client with the settings **endpoint, region, bucket, access key, secret key** covers
  S3, R2, Backblaze B2 and MinIO.
- Features we don't need (and that don't all exist in R2): ACLs, storage classes, bucket
  versioning, and parts of the lifecycle and replication features.
- The ETag of a single-part upload is the file's MD5 in both. Multipart ETags aren't, so don't
  rely on ETag alone for change detection; use size + modified time, or our own checksum stored
  as object metadata.
- **R2 free tier:** 10 GB-month of storage, 1 M write/list operations and 10 M read operations
  per month, and downloads are always free. Beyond that about $0.015/GB-month. Enabling R2
  requires a payment method on the Cloudflare account, even on the free tier, and there's no
  hard spending cap. At 1–2 GB with a handful of syncs per day it stays well inside the free
  tier (every `ListObjectsV2` counts as a write/list operation).

#### Storage class

**S3 Standard** (and on R2, its Standard class). At 1–2 GB it costs about $0.05/month, and a
cheaper class would save only cents while adding extra charges that hit exactly our usage
pattern:
- **Standard-IA / One Zone-IA:** each object is billed as at least 128 KB (a problem for small
  GPX files), billed for at least 30 days even if deleted sooner, plus a fee per GB retrieved.
  Every sync pull that downloads files pays that fee.
- **Glacier Instant Retrieval:** same charges, with a 90-day minimum.
- **Glacier Flexible / Deep Archive:** files can't be downloaded directly (they have to be
  restored first), which breaks sync.
- **Intelligent-Tiering:** a small monitoring fee per object, and objects under 128 KB never
  move to cheaper tiers. No real benefit at this size.
- **R2:** the free tier only applies to Standard; R2's Infrequent Access class adds retrieval
  fees and a 30-day minimum.

Client consequence: don't send an `x-amz-storage-class` header, since the default is Standard.
Moving old trips to a cheaper class later could be a bucket lifecycle rule, configured outside
the app. It's not worth it at this size.

**Rule of thumb:** want Google Drive, or the freedom to switch providers → rclone. S3 or R2
is enough and no extra install matters more → our own S3 client.

### Two-way sync — first thoughts (applies to rclone and to our own S3 client)

- **`rclone bisync`** is rclone's true two-way sync: it tracks state between runs and detects
  deletes and conflicts. It needs a one-time `--resync` for the first run, and its state files
  live under rclone's cache dir, not in our data.
- **Simpler alternative:** attachments (photos, GPX) are mostly **added, rarely changed or
  deleted**. `rclone copy local remote` followed by `rclone copy remote local` never deletes,
  and with `--update` (newer file wins) it covers the common case without bisync's state
  handling. The cost: deletes don't propagate. You'd have to delete the file on both sides, or
  add an explicit "delete attachment" action later.
- **Order in the Sync step, to mirror git:** pull (remote → local) first, then push
  (local → remote).
- **Scope per sync:** the whole attachment root (simple) or only the current trip (faster,
  smaller blast radius)?
- **Checking the tool is there:** detect it like the `git` binary. If `rclone` is missing or
  the remote isn't configured, skip the step with a status message instead of failing the
  whole sync.

## Open questions (for grill-me)

- ~~**What should the app do?** Upload only, or also download? Is it bidirectional?~~ → D1:
  two-way, during sync only.
- ~~**Delete semantics:** use `bisync` (deletes propagate) or copy-both-ways (deletes don't)?~~
  → for now (33b, 2026-10-04): **copy both ways, never delete.** A file deleted locally comes
  back on the next pull. Still open: an explicit "delete attachment" action that removes the
  object from the bucket too. → 2026-10-09: superseded by a three-way sync against a per-device
  sync base, see [ADR-0002](adr/0002-attachment-sync.md); implemented by
  [todo 33c](33c_attachment_delete_propagation.md).
- ~~**Conflicts:** same file changed on both sides — newer wins, keep both, or ask?~~ → for now
  (33b): **local wins.** Pull only downloads files missing locally; push uploads any local file
  that differs from the bucket. Revisit if it bites (e.g. the same photo edited on two machines).
- **Sync scope:** the whole attachment root, or only the active trip?
- ~~**How are attachments linked to an entry?**~~ → D3: by file-name date prefix. Original
  options, for reference:
  - By naming convention, e.g. `YYYY-MM-DD*.gpx` in the trip's attachment dir (like
    impressions), or
  - by an explicit frontmatter key, e.g. `gpx_file` (keep keys alphabetical, snake_case), or
  - by reusing `trackurl` for a cloud link.
- ~~**GPX import:** should "Import GPX…" (todos 23/24) also copy the source file into the
  attachment dir?~~ → D4: yes, always.
- **NEXT — Imported images:** paused here on 2026-09-28. Proposed options: (1, recommended)
  an explicit "Import Faves" action that copies only the faves-pattern matches into the
  attachment dir with the date prefix, and the photo viewer also reads from the attachment dir
  so it works on the Linux notebook; (2) copy all impressions, which is too much data and disk
  for the small notebook; (3) don't copy photos at all. Follow-up if (1): copy as they are
  (recommended; faves already come from a processed `output/` folder) or downscale on import.
  Original question: copied into the attachment dir, or just referenced where they are? The
  impressions pattern already points at `~/Pictures/...`, so maybe the photos are already stored
  locally and only need syncing.
- **Links in exports:** should exported pages (`export.DiaryExporter`) link to the cloud copy
  (presigned URL / share link) instead of a local path?
- ~~**Sync trigger:** manual menu item, part of Smart Sync (todo 32), or on save?~~ → D1: part
  of the Sync action. Still open: a separate checkbox/row in Smart Sync, or always included?
- ~~**Where the app reads the access key from:**~~ → D6: `settings.yml`, edited in the
  Settings dialog.
- ~~**Data-dir location:**~~ → 33a: `attachments/` inside the data dir, its `.gitignore`
  managed per sync mode. Original question: could the attachment dir simply be a gitignored subfolder of the data
  dir (like `.state.yml`), or must it be completely separate?

## Relevant files

- `src/main/java/net/timafe/triptale/storage/ImpressionsResolver.java`
- `src/main/java/net/timafe/triptale/storage/PathPatternResolver.java`
- `src/main/java/net/timafe/triptale/config/AppSettings.java`,
  `src/main/java/net/timafe/triptale/storage/SettingsStore.java`,
  `src/main/java/net/timafe/triptale/ui/dialog/EditSettingsDialog.java`
- `src/main/java/net/timafe/triptale/util/GpxImport.java`
- `src/main/java/net/timafe/triptale/git/GitService.java` (`ProcessBuilder` precedent)
- `src/main/java/net/timafe/triptale/storage/MarkdownStore.java` (frontmatter keys, `trackurl`)
- `terraform/` (bucket + IAM user, D5)
- `src/main/java/net/timafe/triptale/config/BucketUrl.java` (D6)
