# 98 Tailwind FM: radio_normalize.py script renames radio/ tracks to 'Title - Artist' (S)

# 98 Tailwind FM: radio_normalize.py

## Goal

Tracks in `<dataDir>/radio/` should be named like the user's main music library, which
`~/git/tillkuhn/toolbox/91radio/main.go` produces: `Title - Artist.ext`, built from the tags.
Standalone script `scripts/radio_normalize.py`, not part of the app (no app code changes).

## Decisions

- **Tags via `ffprobe`** (ships with ffmpeg, also on Windows). No pip dependency. Exit with a
  clear hint if `ffprobe` isn't on PATH. Reads `title` and `artist` from the format tags.
- **Name**: `"<title> - <artist><ext>"`, sanitized like `sanitizeFilename` in main.go: strip
  `< > : " / \ | ? *`, trim, title-case each word (Go `strings.Title`: first letter of every
  word upper-cased, the rest untouched). Differences to the Go tool: the original extension is
  kept (lower-cased) instead of forcing `.mp3`; blanks stay blanks (today's files use `_`).
- **Rename in place**, no letter sub-folders. radio/ is scanned recursively like
  `RadioLibrary.listTracks` (same extensions: mp3, m4a, aac, wav, aif, aiff; skip hidden files).
- **Skip and report** files with no usable name and files whose target name already
  exists (never overwrite). Files already correctly named are left alone.
- **Filename fallback**: without a title/artist tag, a name with exactly one `-` is read as
  `title-artist` (`_` become blanks) and sanitized the same way. Otherwise the file is skipped.
  The output table has a BASED ON column (`[tag]` / `[filename]`).
- **Output is a table** (ACTION, BASED ON, CURRENT, NEW NAME / REASON); long names are cut in
  the middle to fit the terminal width.
- **Dry-run is the default**: prints `old -> new`; `-a` / `--apply` performs the renames.
- **Data dir from settings.yml**: the shared helper `scripts/triptale_settings.py` finds
  `settings.yml` (`TRIPTALE_SETTINGS_DIR` env, else `$HOME/.config/triptale` or
  `%APPDATA%\triptale`) and reads `dataDir` with a tiny line parser (no PyYAML, stdlib only),
  expanding `${HOME}` / `${USER}` style placeholders (`USERNAME`/`USERPROFILE` fallback on
  Windows). A positional argument or `--data-dir` in the script may override.
- **Settings helper is a reusable module**, since future scripts will need it too. Stdlib only,
  no side effects on import (no CLI parsing, no printing, no `sys.exit`), type-hinted, raising
  `SettingsError` (a plain `Exception`) instead of exiting so callers decide what to do.
  Public API:
  - `settings_dir() -> Path`: `TRIPTALE_SETTINGS_DIR`, else the platform default.
  - `settings_file() -> Path`: `settings_dir() / "settings.yml"`.
  - `load_settings() -> dict[str, str]`: top-level scalar keys only (`dataDir`,
    `radioEnabled`, ...), unquoted, placeholders expanded. Nested blocks (`git:`,
    `attachments:`) are not parsed; add a `nested` reader only when a script needs one.
  - `data_dir() -> Path`: `dataDir` expanded and resolved, `SettingsError` if unset
    (mirrors the app: no default data dir).
  Scripts use it via `sys.path` neighbours, i.e. `from triptale_settings import data_dir`
  (scripts live flat in `scripts/`, so no package needed). A small unit-testable pure function
  `parse_scalar_settings(text) -> dict` does the parsing, so tests need no files.

## Sync impact

The bucket sync sees a rename as delete + upload (path based, three-way merge), so each renamed
track is re-uploaded once; acceptable for a small pool. Sync all machines first, apply the
rename on one machine, then sync it. Other machines then delete the old names and download
the new ones. Blanks in key names are handled by SigV4 / `toUri()` (verified by reading the code).

## Relevant files

- `~/git/tillkuhn/toolbox/91radio/main.go` (`scanFile`, `sanitizeFilename`)
- `src/main/java/net/timafe/triptale/radio/RadioLibrary.java` (extensions, recursion)
- `src/main/java/net/timafe/triptale/sync/` (S3 sync)
