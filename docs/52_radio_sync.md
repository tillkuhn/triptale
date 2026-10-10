# Todo 52 — Sync Tracks: cloud sync for radio/

Status: **implemented 2026-10-10** on `feature/52-radio-sync`, pending a real run against the
bucket. Stage 2 of [todo 46](46_radio.md).

## Goal

The radio pool (`<data-dir>/radio/`) is never versioned in git, so tracks stayed on the
device they were added on. Sync them through the attachments bucket, the same way Smart Sync's
Backpack row syncs `attachments/` — but as a separate action, so music never slows down or
clutters a diary sync.

## Decisions

- **Separate action:** Radio menu → "☁ Sync Tracks…" (`ui/dialog/SyncTracksDialog`), not a
  Smart Sync row.
- **Only in attachment sync mode `cloud`.** Same bucket and credentials (`attachments:` in
  `settings.yml`); the menu item is disabled in other modes, and the dialog says why.
- **Key prefix** `[<bucket url prefix>/]radio/`, mirroring the data dir like `attachments/`.
- **Copy both ways, never delete** for now (pull missing, then push new or changed; local wins
  on conflicts). Once todo 33c's sync base exists, it is reused per folder, so radio gets
  delete propagation too.
- **All non-hidden files** are synced, not just playable formats (cover art, playlists, …).
  Dot files such as `radio/.gitignore` and macOS `._*` stay local.
- **Full MD5 comparison** as for attachments. Multi-GB pools may take seconds to hash on each
  open; measure before optimizing (hash cache or size-only).
- **No separate connectivity row:** the bucket listing itself fails fast when offline, and the
  git host Smart Sync checks says nothing about S3 anyway.

## Implementation

- `sync` package (no JavaFX): `BucketSyncer` (was `attachments.AttachmentSyncer`), `ObjectStore`,
  `S3Client`, `SigV4`, `SyncException`. `BucketSyncer.keyPrefix(url, dirName)`.
- `ui/dialog/SyncRow` (one step row, was `SmartSyncDialog.Row`) and `ui/dialog/BucketSyncStep`
  (configure → plan on a daemon thread → pull + push with progress), shared by Smart Sync's
  Backpack row and Sync Tracks.
- `SyncTracksDialog`: one "Tracks" row; Sync is enabled once the plan finds something to
  transfer. The dialog can't be closed while a transfer runs.

## Open

- Streaming unsynced tracks from presigned URLs instead of downloading (needs presigning in
  `SigV4`), see todo 46.
