# Impressions & Faves 📷

Each entry has a **📷 Impressions 12 🗂 / 3 🎒** button: that day's images in your local photo
library (🗂) and in the day's backpack (🎒). It opens the **Impression Curator**, where you choose the
source — **Trip Backpack**, **Local Photo Lib**, or **Pick Folder** (any folder, e.g. a camera
card) — narrow it to faves, tick images and **🎒 Import Selected** them into that day's backpack.
Imports copy the originals (EXIF included); a same-named file is overwritten. The backpack
travels with the data dir (git or S3), so unlike the photo library it's available on every
machine.

Three settings shape what counts as an impression (Settings dialog or `settings.yml`):

- `impressionsBaseFilter` — space-separated filename globs, default `*.jpg *.jpeg *.png`; applied
  to every source first, so GPX tracks, PDFs etc. never show up.
- `impressionsFaveFilter` — space-separated globs marking faves, e.g. `*+.*` for a `+` at the end
  of the basename; enables the **Faves only** checkbox in the viewer and the export. Both filters
  match the filename only and ignore case.
- `impressionsFilePattern` — where the local photo library lives, see below.

The photo library pattern is a filesystem path containing `${VAR}` placeholders:

| Variable         | Expands to                                  | Example              |
|------------------|----------------------------------------------|-----------------------|
| `${HOME}`        | user home directory                          | `/Users/alice`        |
| `${USER}`        | OS user name (also on Windows)               | `alice`               |
| `${TRIP_SLUG}`   | the active trip's slug                       | `iceland-roadtrip`    |
| `${TRIP_YEAR}`   | the active trip's start-date year            | `2026`                |
| `${TRIP_MONTH}`  | the active trip's start-date month, 2-digit  | `06`                  |
| `${TRIP_DAY}`    | the active trip's start-date day, 2-digit    | `04`                  |
| `${ENTRY_YEAR}`  | the entry's year                             | `2026`                |
| `${ENTRY_MONTH}` | the entry's month, 2-digit                   | `08`                  |
| `${ENTRY_DAY}`   | the entry's day of month, 2-digit            | `07`                  |

After substitution, the pattern is split on `/` and matched one directory level at a time —
**every** segment may contain wildcards, not just the filename:

- `*` matches any run of characters, `?` matches exactly one — both only within a single
  segment, never across a `/`.
- A segment with no wildcard must match an existing file/directory exactly.
- A segment with a wildcard is matched against that directory's children; if more than one
  matches, the first one (sorted by name) is used and a warning is logged — it doesn't fail.
- Resolved directories are cached for the session. If the directory part only uses trip
  variables, that's one lookup per trip and only the final filename glob is re-evaluated as
  you navigate between days; `${ENTRY_*}` in a directory segment makes it one lookup per day.
  Missing folders are cached too, so a folder you create while the app runs needs a restart.

Expansion happens at the moment images are actually looked up, not when settings are saved —
so pointing at a USB drive or NAS that isn't always mounted just yields a count of 0
rather than an error.

Examples, from simplest to most specific:

```
${HOME}/Pictures/00_Faves/output/${ENTRY_YEAR}${ENTRY_MONTH}${ENTRY_DAY}*.jpg
```
One shared folder for every trip; photos are matched purely by date.

```
${HOME}/Pictures/${TRIP_YEAR}/${TRIP_MONTH}_??_${TRIP_SLUG}/output/${ENTRY_YEAR}${ENTRY_MONTH}${ENTRY_DAY}*.jpg
```
A per-trip folder, e.g. `Pictures/2026/06_ab_iceland-roadtrip/output/` — the `??` absorbs two
extra characters you typed by hand between month and slug when naming the folder.

```
${HOME}/Pictures/${TRIP_YEAR}_${TRIP_MONTH}_??_${TRIP_SLUG}/output/${ENTRY_YEAR}${ENTRY_MONTH}${ENTRY_DAY}*.jpg
```
Same idea, but year/month/slug combined into one directory name instead of nested folders,
e.g. `Pictures/2026_06_ab_iceland-roadtrip/output/`.

```
${HOME}/Pictures/${TRIP_YEAR}_${TRIP_MONTH}_*/${ENTRY_YEAR}_${ENTRY_MONTH}_${ENTRY_DAY}/*.jpg
```
One sub-folder per day inside the trip folder, e.g. `Pictures/2026_06_iceland/2026_08_07/`.

---

Back to the [guides](README.md) · [project README](../../README.md)
