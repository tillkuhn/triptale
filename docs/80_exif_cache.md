# 80 — Disk cache for EXIF reads

## Problem

`ExifReader.read(Path)` (storage package, wraps drewnoakes `metadata-extractor`) is called fresh,
with zero caching, from two places:

- `map.PhotoLocations.locations(List<Path>)` — every time "🖥 On Screen" is clicked in
  `MapAssistantDialog` with "📷 Photo Locations from Backpack" on, it re-reads EXIF for every
  image in the selected date range. `docs/54_map_assistant.md` shows a real trip with 212 backpack
  images — that's 212 full file reads/parses per click, repeated every click, every dialog
  re-open, for images that essentially never change.
- `ui.dialog.ImageViewerDialog` — already has an ad-hoc `Map<Path, ExifInfo> exifCache` field, but
  it's scoped to one dialog-open (not shared, not persisted) and has no invalidation check at all
  (stale forever if the file at that path changes).

Both reads run off the FX thread already (background `Thread`/`Task`), so the fix is about
avoiding repeated *work*, not about threading.

Todo 78 (dotted route line) will add a second EXIF field read (`captureTime()`) to the same
`PhotoLocations.locations()` path, making the lack of caching more costly once implemented.
This todo is independent of and precedes 78; 78 needs no changes to benefit from this cache.

## Decisions

- **Cache lives in `ExifReader` itself**, not in `PhotoLocations` — it's the shared choke point
  for both callers. `ImageViewerDialog`'s local `exifCache` field and its
  `computeIfAbsent(p, exifReader::read)` call are **removed**; it just calls `exifReader.read(path)`
  directly and relies on the shared cache underneath.
- **Persistent disk cache**, not just in-memory-per-session — survives app restarts.
- **Layout**: mirrored per-file cache under `<data-dir>/.exif-cache/`, same pattern as
  `OsmTileClient`'s `<data-dir>/.tile-cache/<source>/<z>/<x>/<y>.png` (one small file per image,
  no shared index file to lock/rewrite, a single corrupt/missing entry can't break the rest).
- **Path**: the attachment's path **relative to `MarkdownStore.dataDir()`** is mirrored under
  `.exif-cache/` (e.g. `attachments/2026/some-trip/2026-09-26-Saturday/img1.jpg` →
  `.exif-cache/attachments/2026/some-trip/2026-09-26-Saturday/img1.jpg.properties`) — so the cache
  survives the data dir being moved/renamed/synced to a different absolute path on another
  machine.
- **Invalidation key**: file **size** only (`Files.size(path)`), stored inside the cache entry and
  compared against the current file size on every `read()` call. Mirrors the same-name+same-size
  "already there" identity check `ImpressionsService.alreadyImported` already uses elsewhere in
  this codebase. No mtime check, no TTL — entries live as long as path+size are unchanged.
- **Format**: `java.util.Properties` store/load, one entry file per image, holding `size` plus the
  8 `ExifInfo` scalar fields (`cameraModel`, `aperture`, `exposureTime`, `iso`, `latitude`,
  `longitude`, `dimensions`, `captureTime`). No new dependency; human-readable/greppable; trivial
  to extend when todo 78 starts consuming `captureTime`.
- **No eviction/sweep in this todo.** Orphaned entries (source image deleted/moved) are left in
  place — negligible disk cost (a few hundred bytes each), same tradeoff `OsmTileClient` already
  made for tiles ("never evicted here"). Cleanup is deferred to the new general housekeeping todo
  (see TODO.md #81) instead of being bolted onto this one.
- **Unconfigured data dir**: if `MarkdownStore`/`GitService` report no data dir configured yet
  (`isConfigured() == false`), `ExifReader.read()` skips the cache step entirely and reads EXIF
  live every time — same as today's behavior. This only matters pre-setup, before any data dir
  exists to put `.exif-cache/` under.
- **`.gitignore`**: add `".exif-cache"` as a third entry to `GitService.GITIGNORE_ENTRIES`
  (alongside `".state.yml"`, `".tile-cache"`) — the existing `ensureGitignore()` retrofit path
  (`GitService.java:180-193`) already patches both new and pre-existing data dirs, so no extra
  code is needed beyond the array entry.

## Implementation sketch

- `ExifReader` gains a constructor dependency on `storage.MarkdownStore` (same package, no
  circular dependency — `MarkdownStore` does not reference `ExifReader`) to call `dataDir()`.
- `read(Path path)`:
  1. If path null or data dir unconfigured → today's direct-read path, unchanged.
  2. Compute `long size = Files.size(path)`; relativize `path` against `dataDir()`; build the
     mirrored cache file path under `.exif-cache/`.
  3. If the cache file exists and its stored `size` matches → load `Properties`, build `ExifInfo`
     from them, return (no metadata-extractor call at all).
  4. Otherwise → do the real `ImageMetadataReader.readMetadata(...)` read as today, then write
     a fresh `Properties` file (creating parent dirs as needed) with `size` + the 8 fields before
     returning the result. Write failures (e.g. read-only disk) are logged at `debug` and otherwise
     swallowed — caching is an optimization, never a hard requirement.
- `MapAssistantDialog` / `PhotoLocations` / `ImageViewerDialog` call sites are unchanged apart from
  removing `ImageViewerDialog`'s local `exifCache` field and its `computeIfAbsent` wrapper.

## Files touched

- `storage/ExifReader.java` — add `MarkdownStore` dependency, cache read/write logic.
- `storage/ExifInfo.java` — no field changes expected (already has all 8 cached fields).
- `git/GitService.java` — add `.exif-cache` to `GITIGNORE_ENTRIES`.
- `ui/dialog/ImageViewerDialog.java` — remove the local `exifCache` field/`computeIfAbsent` call.
- New test: `storage/ExifReaderTest.java` (or extend an existing storage test) — cache hit (second
  `read()` of an unchanged file doesn't re-invoke metadata-extractor, verified e.g. via a
  `.exif-cache` entry file existing and/or by corrupting the source image while keeping size+cache
  unchanged and asserting the cached values still come back), cache miss on size change
  (re-reads and overwrites the cache entry), and that `.exif-cache/...properties` is actually
  written to disk with the expected relative path.
</content>
