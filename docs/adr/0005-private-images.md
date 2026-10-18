# ADR-0005: Mark individual images private with a per-day sidecar file, not EXIF

Status: Proposed.

## Context

Images in the backpack (`attachments/<year>/<slug>/<day>/`) are treated as a whole folder at
export time — `DiaryExporter`'s HTML preview and `WordPressExporter`'s gallery both include
every image in a day (optionally narrowed to "faves" via a filename-glob convention,
`ImageFilter`). There is no way to exclude a single photo from export while keeping it in the
backpack for personal reference (e.g. a photo of a hotel receipt, a passport, a private moment).

This mirrors todo 48 ("private sections" in tale text), which solved the same problem for text
via a magic heading convention computed at export time — no separate tracking file, nothing
persisted outside the Markdown body itself.

Two paths were considered for images:

1. **Write the flag into the image itself** (EXIF `UserComment` or an XMP keyword). Closest in
   spirit to todo 48 — the flag travels with the file, survives folder moves, no separate state
   to keep in sync. But `storage.ExifReader` (the project's only EXIF code, via
   `metadata-extractor`) is **read-only**; metadata-extractor has no write support. Adding
   EXIF/XMP write support means a new dependency and a new write-capable class, for exactly one
   feature, in a layer (`storage`/image handling) that is otherwise read-only by design. Also
   risks touching the original image bytes on every toggle, which feels wrong for files
   considered "originals" to be reused elsewhere.
2. **A small sidecar file**, analogous to the `.gitignore`/`.gitattributes` control files
   `attachments.AttachmentsDir` already writes and manages per day folder.

(2) was chosen, but it must actually survive multi-device sync. `attachments.sync` has three
modes (ADR-0002): `off` (local only), `git` (committed like entries), `cloud` (S3, via
`sync.BucketSyncer`). `BucketSyncer.isHidden()` (push) and `localTarget()` (pull) both
explicitly skip any path segment starting with `.`, by design — to avoid syncing OS/git
housekeeping files (`.DS_Store`, `.gitignore`, `.gitattributes`). A sidecar named with a leading
dot would therefore sync for free in `git` mode (plain `git add .`, no filename filtering) but
be **silently dropped** in `cloud` mode on both push and pull — the worst failure: it looks like
it works until a second device proves otherwise.

## Decision

Track private images with a **plain-named sidecar file per day folder**, e.g.
`private.txt` (not dot-prefixed), listing the private image filenames (one per line), living
next to the entry's own attachments in `attachments/<year>/<slug>/<day>/`.

- A non-dot-prefixed name means it is treated like any other attachment by every sync mode and
  by `GitService`'s blanket `git add .` — no changes needed to `BucketSyncer`'s hidden-file
  filtering, no new S3/git special-casing.
- A new small helper (e.g. `attachments.PrivateImages`, next to `AttachmentsDir`) owns reading
  and writing this file: `load(dayDir) -> Set<String>`, `markPrivate`/`unmarkPrivate`.
- The exporters (`DiaryExporter.injectImpressions`, `WordPressExporter.gallery`) and
  `map.PhotoLocations` filter images against this set, the same way they already filter against
  the faves glob (`ImageFilter`). Private wins over fave: a private image is never shown
  regardless of the `+` fave marker.
- `ui/dialog/ImageViewerDialog` gets a per-image toggle (next to the existing "🎒 In backpack"
  badge and faves checkbox) that writes through to `private.txt` immediately — it is filesystem
  state, not frontmatter, so it does not go through "📅 Save the Day".
- The file is committed/synced like any attachment: visible in git history, travels with the
  repo, consistent across every sync mode. This is a deliberate change from `.state.yml`
  (gitignored, device-local) — "this photo must never be published" is a durable, shared
  decision, not local UI state.

## Consequences

- Easier: no new dependency, no write-capable EXIF/XMP code, no change to `BucketSyncer` or
  `GitService`. The sidecar behaves exactly like any other attachment file in all three sync
  modes.
- Easier: toggling private/non-private from the UI is a cheap text-file rewrite, no re-encoding
  image bytes.
- Harder: there is now a small file in the backpack whose name isn't a photo/GPX/ticket — minor
  noise in Finder/`ls` and in git history diffs.
- Given up: the flag does not survive if an image is copied out of the backpack by hand (e.g.
  dragged to Finder) — unlike an embedded EXIF tag, it only has meaning inside TripTale's own
  folder structure. Acceptable: the backpack itself is already the only place this matters
  (exports and Map Assistant only ever read from the backpack, never from an arbitrary Finder
  copy).
- Unchanged: `off` mode still never syncs attachments at all, `git` mode already carried every
  non-excluded file for free.
</content>
