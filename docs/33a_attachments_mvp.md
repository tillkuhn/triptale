# Todo 33a — Attachments MVP (push to S3)

Status: **agreed and implemented 2026-10-03; first GPX upload to the real bucket works.**
Not yet verified: a second push skips unchanged files. First minimal version of todo 33
([plan](33_object_storage_for_trip_attachments.md)) to test against the real bucket.

## Decisions

**Local layout.** Attachments live inside the data dir under `attachments/`, like Tolaria's
[files and media](https://tolaria.md/concepts/files-and-media). One folder per day, named like
the day's entry file without `.md`:
`2026/AltenbekenMoehneWerlRuhr/2026-09-26-Saturday.md` →
`attachments/2026/AltenbekenMoehneWerlRuhr/2026-09-26-Saturday/`. Day folders are created only
on demand. Replaces D3 of todo 33 (date prefix in a trip-level folder).

**Sync mode setting** (`attachments.sync` in `settings.yml`, Settings dialog), default `off`:

| Mode | `attachments/.gitignore` | Push Attachments |
|---|---|---|
| `off` (local only) | `*` + `!.gitignore` — files stay local, don't weigh down the repo | disabled |
| `git` (versioned with the `.md` files) | a comment only, everything is committed | disabled (git sync covers it) |
| `cloud` (S3; R2 later) | same as `off` | enabled while online |

- The app owns `attachments/` and its `.gitignore`: (re)written at startup and when settings
  are saved. When the content changes it goes into the pending commit (save → `addPending` →
  Commit). The `.gitignore` itself is committed, so a fresh clone ignores local files too.
- Switching `git` → `off` doesn't untrack files already committed; `git rm --cached` them by hand.
- `cloud` makes bucket URL, region, access key ID and secret required in the Settings dialog.

**Adding files.** "📎 Add Attachments…" (Tale Entries menu) opens a file chooser
(multi-select) and copies the picked files into the current day's folder, created on demand. A
file with the same name is overwritten (like D4). The chooser remembers the last source folder
for the session. Enabled when a trip and date are selected, in every mode, also for days
without a saved tale. GPX import doesn't copy files yet (D4 later). *(First tried: an "Open
Attachments" item that opened the day folder in Finder to drop files into. Rejected after
testing: not discoverable, and selecting a file in that window just opens it.)*

**Push Attachments** (Repository menu; Push/Pull become "Push Git"/"Pull Git"):
- Scope: the whole `attachments/` tree. Push only, nothing is downloaded or deleted.
- Object keys mirror the data dir: `<prefix>/attachments/<year>/<slug>/<day>/<file>`, where
  `<prefix>` is the optional path of the bucket URL. Keeping `attachments/` in the key leaves
  room for other top-level keys (e.g. `backup/`) and keeps the bucket name irrelevant.
- Incremental: one `ListObjectsV2` of the `attachments/` key prefix. Different size → upload.
  Same size → MD5 of the local file (streamed) vs the ETag (= MD5 for single-part PUTs, which
  is all we do) → upload if different.
- Hidden files (`.gitignore`, `.DS_Store`, …) and hidden dirs are skipped.
- Enabled only in `cloud` mode and while the connectivity check says online (it's the app's
  "are we online?" signal for now, though it checks the git host).
- Runs in a background task, one file at a time (1 GB RAM notebook), reports progress and the
  result in the status line; errors show as an alert.

**S3 client.** Own SigV4 signing over `java.net.http`, no AWS SDK (todo 33, D2). Virtual-hosted
URLs (`https://<bucket>.s3.<region>.amazonaws.com/<key>`), single `PUT` streamed from disk with
`UNSIGNED-PAYLOAD` and a `Content-MD5` header so S3 rejects corrupted uploads, no
storage-class header. Bucket names with dots would break TLS on virtual-hosted URLs; not
supported in the MVP.

## Follow-ups

- Verify the second push reports "0 uploaded, N unchanged".
- `.gpx` is uploaded as `application/octet-stream` (no OS mapping on macOS); map it to
  `application/gpx+xml` if downloads or links need it.

## Code

- `attachments.AttachmentsDir` — paths, day folders, `.gitignore` per mode
- `attachments.SigV4` — request signing; `attachments.S3Client` — `ListObjectsV2`, `PutObject`
- `attachments.AttachmentPusher` — the diff + upload loop, behind the small `ObjectStore`
  interface so it's unit-testable without network
- `config.AppSettings.Attachments` — `sync` mode, validation for `cloud`
