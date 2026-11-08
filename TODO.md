# ToDos for this app

## Inbox

* New Activities: Swim, Shave
* New Emojis: Music
* Checkout Liste für Weblinks (title for separate Paragraph), deutsch: "Checkout Liste"
* Replace dark "Notes" emoji in Weblinks label, not enough contrast
* Coordinator: Where is the elevation for weather?

## Next Todo: 91

## 88 Share trip form between New Trip and Trip Details

Extract a TripForm helper in ui/dialog/ owning the shared controls (name, default activity, default post tags, description): grid rows, getters, changed-vs-trip check, change listener. NewTripDialog and TripDetailsDialog embed it and add their own rows (start date, GPX import, first entry / slug, year, end date, stats, entries table). Keep two dialogs, no creating-mode flags. Adding a field then becomes a one-place change.

## 81 General repo housekeeping task

One mechanism (invokable on demand or periodically) that sweep/cleanup-style tasks register
with, instead of each feature bolting on its own one-off sweep: orphaned `.exif-cache`/
`.tile-cache` entries, local `git gc`, and whatever else turns up over time. Design (scheduling,
persistence of last-run time, trigger UI) still open.
Absorbs former todo 31 ("Run Housekeeping" in the Repository menu, `git gc` with its output shown).

[Details](docs/81_housekeeping.md)

## 67 Private images

Mark individual backpack images as private so they're kept but never exported, via a per-day
`private.txt` sidecar listing excluded filenames (not dot-prefixed, so CLOUD sync keeps it).
- New `attachments.PrivateImages` helper; filter in `DiaryExporter`, `WordPressExporter`,
  `map.PhotoLocations`. Private wins over the fave marker.
- `ImageViewerDialog` gets a per-image toggle that writes through immediately.

[Details](docs/adr/0005-private-images.md)

## 63 WordPress Post leftovers: delete Posts in-app, slug style

Left over from todo 62. Posts can't be deleted from the app yet (only by hand or in Tolaria);
a delete would also need to decide what happens to the WordPress draft. `MarkdownStore.postSlug`
gives camelCase (`WeekendTrip`) while the one hand-made example from todo 60 is kebab-case —
pick one style.

[Background](docs/62_wordpress_publish_dialog.md)

## 49 Bulk import a whole trip's photo-lib impressions as attachments

Split out of todo 47 (deferred there). Trip menu action that copies every day's photo-library
images (base filter applied, optionally faves only) into the matching attachment day folders,
with a confirmation showing counts per day and total size. Best after todo 33c, so a mistaken
bulk import can be deleted everywhere.

## 48 Private sections: hide internal notes from exports

A heading starting with `internal`, `private`, `intern` or `privat` (any level,
case-insensitive, followed by end/whitespace/`:`) marks a private block up to the next heading
of the same or higher level, or the end of the tale. Stripped via `Markdown.stripPrivate` in
both `DiaryExporter` and `WordPressExporter`; editor, word count and files stay unchanged.

- "📋 Copy Tale" is removed (raw copy: Cmd + A, Cmd + C; clean copy: Export Tale → Copy).
- New "🔒 Add Private Section" menu item appends `## Internal` + placeholder at the end.

[Details](docs/48_private_sections.md)

## 46 Radio: play favorite tracks from a radio/ pool

A "radio": a reusable pool of favourite tracks in `<data-dir>/radio/` (not in git), playable
from the app while writing. Branded Tailwind FM, gated behind a setting while in alpha.
- Done: random playback, play/pause/next/stop, Tailwind FM window with track list (todo 86).
- Stage 2: cloud sync of `radio/` via its own S3 prefix, see todo 52.
- Stage 3: wikilinks to tracks in tales, trip soundtrack, insert current track, export.
- Open: Linux playback depends on a supported system FFmpeg, test the release jar.

[Details](docs/46_radio.md)

## 43 Spring Boot AOT Optimizations and other performance helpers

Goals: Improve startup time, lower memory footprint.
As triptale app becomes bigger and bigger, check what could help to speed up startup time / lower memory footprints on smaller devices, e.g. https://docs.spring.io/spring-framework/reference/core/aot.html and  https://www.baeldung.com/spring-boot-startup-speed (may be outdated since it's spring 2.x, contains Tips like -Xnoverify which apparently is also deprecated)

Status (2026-10-04), details and measurements in `docs/43_startup_performance.md`:

- Done: JDK 25 AOT cache via `make run-fast` (trains automatically, Spring 1.5 s → 0.55 s,
  process ~4.2 s → ~2.8 s). Needed a new plain `Launcher` main class.
- Done: dropped the hard-coded `win` JavaFX classifier — jars now carry only the build host's
  natives (37 → 28 MB); CI still builds the Linux release jar.
- Not worth it: lazy init, GC/heap flags (no RSS change), Spring AOT, native-image.
- Open: start Spring in parallel with the JavaFX toolkit; analyse the ~650 MB RSS
  (NMT, `-Xmx`).

## 42 support refactor trip slug to rename folders and references

trip slug is currently immutable since the path may be used, e.g. in tolaria style wiki links, or in belongs_to frontmatter references.
add a button "rename" behind the slug in the trip edit screen, that should allow to rename the slug (that still has to adhere to our naming conventions e.g. no blanks etc),
and walks through all trip YYYY subdirectories and update references to reflect the new path, including tolaria style wikilinks "[[path/file]]. 
Report back with "x references updated" or similar.
If cloud attachments is active, those pathes have to be renamed and synced as well, check if this can be cheap rename operation on remote side, or has to be delete and re-upload, I remember in s3 path is only object prefix, not a real folder  
Depends on todo 33c (remote deletes); S3 rename = copy + delete.

## 40 add "Re-init Repository" action to Repository menu

`GitService.initRepo()` is currently only invoked on startup (`MainController.performStartupChecks()`),
either silently (repo already exists) or after a confirmation prompt (no repo found yet). Add a new
menu item in the Repository menu group, e.g. "Re-init Repository", that lets the user re-run
`initRepo()` on demand without restarting the app. Useful after manually deleting one of the managed
files (`trip.md`/`tale.md`/`type.md`/`.gitignore` entry) to have it recreated immediately, or after
todo 39 changes the managed file contents on disk. Since `ensureFile()` only writes files that don't
already exist, re-running it won't overwrite files that are already present — clarify in the UI
(status line message / tooltip) that it only fills in what's missing, it does not refresh existing
content.

## 39 move managed markdown templates (trip.md/tale.md/type.md) out of GitService into files

`GitService.java` currently hardcodes the content of `trip.md`, `tale.md` and `type.md` as Java text
block constants (`TRIP_MD`, `TALE_MD`, `TYPE_MD`). Move these to actual template files under the
project (e.g. `src/main/resources/git/` or similar, loaded via `getResourceAsStream` like the export
templates in `export/`) so they're easier to review/edit outside a Java file. Keep `.gitignore`
handling (`ensureGitignore`/`GITIGNORE_ENTRY`) as-is/inline — that one is fine since users may
legitimately want to add their own entries beyond `.state.yml`, so it isn't a good fit for a static
template file. Keep `ensureFile()`'s "only write if missing" semantics — this todo is only about
where the content is authored, not about syncing existing data dirs when the templates change (see
todo 40 for a related manual re-run trigger).

## 36 improved event / log dialogue in bottom left corner

currently it's only a limited single most recent message with limited space.
goal is to have a stack of messages (start with limit 50), so if you click on a magnifying glass icon you'll get a scrollable popup with the X most recent UI messages, showing severity, time (not date) and message

## 33a Object storage4trip attachments (track files & media) MVP

First minimal attachments implementation against real S3: local `attachments/` folder in the
data dir (one folder per day, created on demand), `attachments.sync` setting (`off`/`git`/`cloud`)
with an app-managed `.gitignore`/`.gitattributes`, and a "Push Attachments" menu item.

Status 2026-10-03: implemented, GPX upload to the real bucket works. Still to check: a second
push reports "0 uploaded, N unchanged"; optionally map `.gpx` to `application/gpx+xml`.

[Plan](docs/33a_attachments_mvp.md)

## 33 Object storage for trip attachments 

I would like to use some kind of cloud storage for stuff that I don't want to store in the git repo (which should continue focus on versioned *.md files), for example to import images that match our impressions pattern, or store gpx files along with a trip entry. First thing that came to my mind was AWS S3, I also have a subscription, but the Java SDK Is very fat and the integration seems a bit overkill. Google Drive would be also nice since we're already using it to keep other travel files, but not sure if it can be used trough an API. Other suggestions welcome as long as they are fee for say ~1-2GB, since I don't want another subscription besides AWS.


[Plan](docs/33_object_storage_for_trip_attachments.md)

## 29 implement delete (entire) trip

we already have delete tale, but should be also possible to delete entire trip, which requires recursive delete of the folder in the git repo.
add a button to the edit trip dialogue, and let the user confirm before performing the action, preferably with some stats like "You are about to delete one trip with X entries, are you sure?"

## 28 Check for updates

Add a "Check for updates" function to the TripTale (app) menu. It queries the public GitHub
repo for the most recent release. If that's equal to or older than the running version (older
is possible, a local build can be ahead), show that the app is up to date; if newer (all tags
are semver), show the new version and a hyperlink to download it.
While checking the remote, show the Smart Sync style dialog with spinner; if that isn't reusable
yet, make it so now (more future use cases will talk to remote pages verbosely).
The About version part is done already (`f8427f4`: shows the closest git tag, not the Maven version).

## 26 Show no of objects and repo size

in repo info dialogue, show output of `git count-objects -H` e.g. 359 objects, 1.62 MiB`.

## 19 DatePicker has no quick year navigation

The DatePicker popup (New Trip dialog, main date picker, anywhere else it's used) only lets you
page month-by-month via the `<`/`>` arrows next to the month/year header — there's no direct
year jump. Picking a date a year or more away (e.g. backfilling a 2025 trip while today is
2026) means clicking through many months one at a time. JavaFX's DatePicker doesn't expose a
year spinner natively; investigate a day-cell-factory-based or header-replacement workaround.
Affects every DatePicker instance in the app, not just one dialog — worth checking all call
sites for a consistent fix rather than patching one.

---

## DONE 90 External track suggestions for Import GPX

The shared external track app backup folder (`tour_recorded`, ~500 `YYYY-MM-DD_<tourId>_<slug>.gpx` files,
date = recording date) makes "Import GPX…" a scroll through everything. Match tours by the
entry's date instead: (A) the import offers date-matched candidates first, (B) the entry
shows that an unimported tour exists. Folder comes from a new `gpxFilePattern` setting (same resolver as impressions); A + B in the first cut; B is a small always-visible GPX button in the Distance/Alt row (📥 candidate found, ✅ imported, ❓ none), branded GPX Gecko.

[Details](docs/90_external_track_suggestions.md)

## DONE 89 Splash screen and dragon logo

Preloader splash with the TripTale dragon logo while Spring boots (min. 2 s, 0 in dev targets), dragon image in the About dialog and the empty state instead of the emoji.

[Details](docs/89_splash_screen_and_dragon_logo.md)

## DONE 87 Trip details: tabs with entries overview table

Make the Trip details dialog (TripDetailsDialog) wider and add a TabPane: tab 'Details' (existing form) and tab 'Overview' (read-only TableView of all entries: day, title, activity, start place). Entry form in the main window stays focused on the trip details, no overview there. Try on a branch.

## DONE 86 Tailwind FM: foldable track list, tape-style buttons, DJ line fix

Extend TailwindFmDialog (width stays 400 + padding, no wider):
- Foldable (collapsed by default) track table like the backpack section: beautified name (Track.title()), human-friendly size, sortable by name and by size (sorted by bytes).
- Click a row plays that track, then continues with random next as today.
- Buttons: symbols only (play/pause, stop, next) like a tape recorder.
- Bug: long title wraps to 2 lines and clips the buttons. Fix: single-line title and DJ line, cut with ellipsis. Keep the last DJ line visible until the next announced one.
Filename-derived titles only, no ID3 reading (too costly).

[Details](docs/86_tailwind_fm_foldable_track_list_tape_style_buttons_dj_line_f.md)

## DONE 85 Post Office: posts as first-class chapters

Posts become first-class chapters: a trip's date range (from/to) with title + intro, optionally WordPress-linked. New Post Office dialog (Trip menu, 📮) lists posts (start day, end day, title, WP status), highlights the one covering the current entry, and has Add/Delete/Edit. Export dialog replaces 'all days' with [post selector][from][to] (Ad Hoc / Entire Trip / real posts); WordPress export requires a real post and the 2nd step goes away. Details in doc.

[Details](docs/85_post_office_posts_as_first_class_chapters.md)

## DONE 41 justified-row impressions gallery for HTML/WordPress export

Exports and the curator's "Gallery Layout" preview share `ImpressionsService.galleryRows`
(same-orientation pairs, odd landscape last, rows cropped to one height). Time order is no
longer kept. Cropping in the HTML export is a known trade-off; images link to the full size.

[Details](docs/41_justified_row_impressions_gallery.md)


## DONE 56 Open the backpack from the main window

The 🎒 Backpack button (and the renamed "🎒 Open Backpack…" menu item) opens a modal dialog for
the current day: "🎒 Your backpack for Day N: Title", an explorer-style table (name, kind,
humanized size, changed) with Add, Open, Reveal in Finder, Copy Path and single-file Delete
(confirmed; cloud sync propagates it via the 33c sync base). The button shows the count,
`🎒 Backpack (3)`.

Done 2026-11-04: visually checked; the label sits in the title bar, tables got a dark theme.

[Details](docs/56_backpack_dialog.md)

## DONE 84 Export images open in original size

Impression images in exports are shown small (grid/gallery) with no way to see them full size.
- HTML export: wrap each image in `<a href=… target="triptale-image">`, a named target, so all
  clicks reuse one browser tab showing the original file.
- WordPress export: enable Gutenberg's lightbox ("Expand on click", WP 6.4+,
  `"lightbox":{"enabled":true}`) on each gallery image, so readers stay on the post.

## DONE 78 Dotted route line and photo pin thinning on Map Assistant

Optional `〰 Route Line` through each day's start, its photos by EXIF capture time and its stop;
photo pins thinned by pixel distance at the rendered zoom; pins restyled (dark red start,
near-black stop, smaller light red photos); all Sgt. Mapper options remembered as
`lastMapOptions` in `.triptale/state.yml`.

[Details](docs/78_route_line_map_assistant.md)

## DONE 83 One gitignored dir for local state and caches

The data dir root collects one gitignore line per local file: `.state.yml`, `.tile-cache/`,
`.exif-cache/`, `.attachments-sync.yml`, `.radio-sync.yml` (see `GitService.GITIGNORE_ENTRIES`).
Move them all under one dir, ignored with a single line. Proposal: `.triptale/` with
`state.yml`, `sync/<dir>.yml` and `cache/{tiles,exif}/`, so "throw-away" (`cache/`, safe to
`rm -rf`, todo 81 sweeps it) stays apart from per-device state that must survive.
Decided: name `.triptale/` (not `.cache/`, since not all of it is disposable). No migration code
in the app: after implementation, a one-time ad hoc script moves existing files and removes the
old per-file lines from `.gitignore`.

## DONE 82 Sgt. Mapper: format, temp renders, save to entry folder

- Trip name moves into the window title (`🗺 Sgt. Mapper · <trip>`); options row: Style · Zoom ·
  Format (3:2 default, 1:1, 2:3) · `📷 Add Photo POIs`.
- Renders go to a temp location, not the trip folder. A new action row has the target-entry
  dropdown (entries in range, active entry by default), `💾 Save` (as `<day dir>/tripmap.png`),
  `Save as…`, Close and On Screen.
- An existing `tripmap.png` is loaded from disk on open (works offline). The header says
  cached or temporary, and the main `🗺 Sgt. Mapper` button shows a ✓ without changing width.
- Follow-up, not part of this todo: conditional inclusion of the map in export galleries.

[Details](docs/82_sgt_mapper_save_and_format.md)

## DONE 24 Support import gpx for Trip Entries

see docs/24_gpx_import_tale_entry.md for the full design (from a grill-me session covering the
menu placement/enablement, all-or-nothing overwrite confirmation gated on disk state (not form
dirty state), reuse of the existing datePicker navigate-away guard, prefill-only/no-auto-save
semantics, and the todo 25 delete-capability gap it surfaced).

similar to todo 23 it should be possible to fill values for a trip tale entry via gpx.
Trigger: Menu link in "Tale Entries" Group ".
Let's skip a dedicated button in the UI, since it is fixed to particular day, and the import derives the day from the first trkpt element
Similar to todo 23, the import should prefill title, and determine the date and start pos from the gpx data.
If a day entry already exists, it should prompt if user wants to overwrite (but if yes overwrite only the field that can be derived, i.e. do not empty existing other fields    

## DONE 50 WordPress export follow-ups

Leftovers from todo 44 (stage 1 shipped: Format → WordPress in Export Diary, CDN images).

- Day heading wording for blog posts (currently the Markdown export's `2026-05-29 Friday
  Day 1: …`).
- Watch the CloudFront bill in Cost Explorer for a few weeks; if it isn't ~€0, set
  `enable_cdn = false` and host images on our own webserver (the blog is a Simple site, no SFTP).
- Stage 2 (draft via the API) moved to todo 60.

[Background](docs/44_wordpress_export.md)

## DONE 80 Cache EXIF reads (disk cache)

`ExifReader.read()` has no caching — `PhotoLocations.locations()` re-reads EXIF for every backpack
image on every "🖥 On Screen" click in Map Assistant (200+ images on a real trip), and
`ImageViewerDialog`'s own ad-hoc per-dialog cache has no invalidation check at all. Add a
persistent disk cache in `ExifReader` itself: one `.properties` file per image under
`<data-dir>/.exif-cache/`, mirroring the attachment's path relative to the data dir, keyed by
file size (like `OsmTileClient`'s `.tile-cache`, gitignored via `GitService.GITIGNORE_ENTRIES`).
Removes `ImageViewerDialog`'s local cache in favor of the shared one.

[Details](docs/80_exif_cache.md)

## DONE 33c Attachment delete propagation (sync base)

Cloud attachment sync was never deletes: a locally deleted attachment came back with the next
pull, and a stale copy could overwrite a newer remote edit. Implemented as a three-way merge
against a per-folder sync base (no base, no deletes), shared by `attachments/` and `radio/`
(Sync Tracks). See [ADR-0002](docs/adr/0002-attachment-sync.md).

[Details](docs/33c_attachment_delete_propagation.md)

## DONE 61 Self-rendered trip maps from OSM tiles


Mapbox static maps replaced with self-rendered OSM tiles (OpenTopoMap, OSM Standard, CyclOSM,
Thunderforest Outdoors/OpenCycleMap behind a `thunderforestApiKey` setting, hidden from the
Style dropdown when blank). Added two more zoom levels (`SNUG`, `WIDER`) for finer-grained
steps between Close/Normal/Wide/Very wide.

[Details](docs/61_osm_trip_maps.md)

## DONE 79 Post diary entries to Polarsteps via API — won't do

Investigated posting parts of diary entries to Polarsteps. No official API exists; the only
notable unofficial wrapper (`remuzel/polarsteps-api`, actively maintained, cookie-auth via
`remember_token`) exposes **read-only** endpoints (`get_user_by_username`, `get_trip`) — no
step/entry creation. Writing would require reverse-engineering the mobile app's own create-step
calls from scratch, with no stable contract and ToS/account-suspension risk. Not pursuing;
revisit only if a write endpoint becomes documented somewhere.

[Details](docs/79_polarsteps_export.md)

## DONE 77 Octobot inbox: convert a detected link into a Weblink

Added an optional "Create Weblink" checkbox to each `InboxDialog` row (after Add/Dump),
pre-checked whenever a URL is detected, disabled otherwise. On Add, if ticked, opens a small
follow-up dialog (verb combo limited to that day's untaken verbs, editable url/name) and adds
the result to the day's links. The private-notes bullet now names the verb too, e.g. "added and
eat-linked by Octobot 🐙". Also fixed the barely-visible `➕` plus glyph on the Add button.

[Details](docs/77_inbox_create_weblink.md)

## DONE 76 "Add Private Notes" + bullet-style Octobot inbox landing

Renamed "🔒 Add Private Section" to "🔒 Add Private Notes"; default heading is now
`## Private Notes 🔒` (old `internal`/`private`/`intern`/`privat` keyword rule still applies,
still only one private section per tale). Merged the mobile/Telegram inbox (todo 68, previously
its own `## Inbox` heading) into the private section — Octobot captures are private by default,
landing in the existing or a newly created private block, one bullet per item, newlines
collapsed to a single line, suffixed `— added by Octobot 🐙`.

[Details](docs/76_private_notes_octobot_inbox.md)

## DONE 75 Inbox: show sender and Telegram message id, tighter card layout

Prep work for more than one family member capturing into the same inbox. `InboxItem` now reads
`message_id`/`from.first_name [last_name]` back out of the same-stem `.json` sidecar (todo 73)
that was already being written but never read; the Inbox dialog shows them on the date line
("2026-10-26 17:45 · #23 · Tima Fe", date stays first for the visual day/time mapping). Also
tightened the card layout (less padding/spacing, the error label no longer reserves space when
empty) so more pending items fit on screen at once. See `docs/68_mobile_inbox.md` for how a
shared Telegram group lets several family members capture today without further code changes,
and what a true multi-sender allowlist would need.

[Details](docs/68_mobile_inbox.md)

## DONE 74 Telegram inbox: receive photos, not just text

Fast-follow from todo 68, whose v1 was explicitly text-only. A photo sent to the configured bot
is downloaded eagerly during `poll()` (largest `PhotoSize`, Telegram already compresses) into a
same-stem `.jpg` sidecar next to the caption's `.txt`. On Attach, the photo goes into the day's
`attachments/` folder via `ImpressionsService.importToAttachments` (never git-pended, matches
every other attachment import); a non-blank caption is also appended under `## Inbox` and saved,
same as a text item. No media-group/album support, no location messages.

[Details](docs/74_telegram_inbox_photos.md)

## DONE 73 Mobile inbox: capture the raw Telegram payload too

`TelegramInboxClient` only ever extracted `text` from a Telegram update, discarding everything
else — no way to debug a failed capture, and no real samples to design the photo/location
fast-follows from (todo 68). Each pending item now gets a same-stem `.json` sidecar with the raw
Telegram `update` (`InboxDir.writeItem`); non-text updates from the configured chat (photos,
locations, …) don't become pending items yet but are captured into `inbox/raw/` for the same
reason (`InboxDir.writeRawCapture`). Sidecars travel with their `.txt` on Attach/Discard.

[Details](docs/68_mobile_inbox.md)

## DONE 72 Elevation alongside weather

Open-Meteo's forecast/archive response already returns the point's elevation at the JSON
root — free, no extra request. Captured on `domain.Weather`, shown as a muted line below
the weather fact in exports (`"124 m a.s.l."` / German `"üNN"`), in the Coordinator's weather
row tooltip, and in the toolbar weather tooltip. Breaking change to the packed
`weather` frontmatter value (new `elevation` field inserted after `code`, before `place`);
`scripts/migrate_weather_elevation.py` reformatted the real data dir's 32 legacy entries.

[Details](docs/72_weather_elevation.md)

## DONE 68 Mobile inbox: capture on the phone, organize later in TripTale

v1, text only: Telegram Bot API long-polling (`inbox.TelegramInboxClient`) picks up messages
into a local inbox folder (`inbox.InboxDir`/`InboxItem`/`InboxNotes`), reviewed via
`ui/dialog/InboxDialog` (attach to an entry or discard) from a new "Check Inbox" action.
Coordinates/photos remain a fast-follow, not part of this cut.

[Details](docs/68_mobile_inbox.md)

## DONE 71 Remember last GPX import folder across restarts

`MarkdownStore.saveLastGpxFolder`/`loadLastGpxFolder` persist the last-used folder in
`.state.yml`; both `MainController.onImportGpx` and `NewTripDialog`'s "Import GPX…" set it as
the chooser's `initialDirectory` and save it right after a file is picked.

## DONE 70 Derive activity from a GPX track's Komoot `<type>`

`GpxImport.activityFromKomootType` maps Komoot's `<trk><type>` to a `domain.Activity`
(including a new `SNOWRIDING` for `snowboard`), surfaced as an Activity row in
`GpxImportDialog` (omitted when there's no mapping).

[Details](docs/70_gpx_import_activity_from_type.md)

## DONE 66 Per-field GPX import matrix

`ui/dialog/GpxImportDialog` replaced the all-or-nothing confirmation: one row per field
(title, start/stop coords, distance, altitude, activity), each with its own overwrite
checkbox, old values always read from disk via `store.loadEntry`.

[Details](docs/66_gpx_import_field_matrix.md)

## DONE 47 Impressions / exporter refactor: images as attachments

Impressions come from a selectable source (Local Photo Lib / Trip Attachments / Pick Folder),
reduced by `impressionsBaseFilter` and optionally `impressionsFaveFilter` (filename globs).
The viewer imports selected images into the day's attachments; the Faves button is gone. Export
gets a From/To entry range, an image source + "Faves only", and a per-entry "⇧ Export Tale".
`feature/47-impressions-refactor` is merged into `main`. Deferred: resize on import, public
URLs (`DONE 44`), bulk import of a whole trip (todo 49).

[Details](docs/47_impressions_attachments_refactor.md)

## DONE 69 Emoji Picker

A searchable popup (menu + Shortcut+E) over a curated travel emoji list: inserts at the tale
editor's caret when it has focus (replacing any selection), otherwise copies to the clipboard.

[Details](docs/69_emoji_picker.md)

## DONE 64 Cosmetic findings and small features during DUS->ATH Flight

Added Diving (ferryhopping already existed) as a new `Activity`. Map Assistant now checks
connectivity to `api.mapbox.com` via `ConnectivityService` before requesting a static map,
showing a "Viewscreen offline" message instead of letting the image load attempt fail with an
uncaught `UnknownHostException`.

## DONE 65 Weather per day

Store a day's weather (temp min/max, condition) fetched automatically from Open-Meteo (free,
keyless, non-commercial) once an entry has a start point, and show it in exports alongside the
other entry facts (todo 53/58).

- New `weather` frontmatter key, packed semicolon string `"min;max;code[;place]"`.
- `weather.OpenMeteoClient` (no JavaFX): forecast API, falling back to the historical archive
  API when the forecast endpoint has no data for the date — `fetch()` tries forecast first,
  archive only on failure (a date-based cutoff proved unreliable); silent on failure.
- Auto-fetch on save when `start_lat`/`start_lon` present and `weather` absent; never overwrites.
- New `domain.WeatherCode` enum (WMO code buckets, emoji) joins `export.EntryFacts`.
- Coordinator dialog shows/updates/clears it by hand (🔄/🗑), dated so it reads as history;
  min/max temperature fields display as `"8°"`, normalized on focus loss like the distance/
  altitude fields.

[Details](docs/65_weather_per_day.md)

## DONE 62 Polish "Send to WordPress" and add in-app Post management

The Diary Composer has no Source combo any more; "📡 Send to WordPress" opens a WordPress
dialog to pick or create a Post for the range, edit title/intro and send (background Tasks,
result shown inline, Post file written only after a successful send). Leftovers: todo 63.

[Details](docs/62_wordpress_publish_dialog.md)

## DONE 60 Send the WordPress export as a draft via the API

OAuth2 login (browser + loopback redirect, `global`-scope token in `settings.yml`), a `Post`
Tolaria type (`<trip>/posts/<slug>.md`, title/intro/`has` day links) as the export source, and
create/update via `public-api.wordpress.com/wp/v2` — all live-verified (create + update, 404 and
scope-error handling). Rough edges and in-app Post management tracked as todo 62.

[Details](docs/60_wordpress_api_draft.md)

## DONE 58 Show start/stop place in exports

The location fact in all three exports shows `start_place`/`stop_place` instead of
coordinates where set; HTML/WordPress add the coordinates behind them as a small line. Exports
with any place end with one "Place names © OpenStreetMap contributors" line (English, ODbL).
Bigger fact emojis in HTML; 🗓️ instead of 📅 on WordPress. Country flags deferred.

see docs/58_places_in_exports.md

## DONE 59 Forward place lookup (place name → coordinates)

The reverse of todo 51: type `"Bangkok, TH"` in the Coordinator's Place field and let 🔍 fill
in lat/lon (city centre) via Nominatim `/search` (`q=`, `countrycodes=` from a trailing
two-letter code, `limit=1`). Same `map.PlaceLookup`, rate limiter and User-Agent; button only,
never on typing (usage policy). 🔍 picks the direction from the row: coordinates set → reverse,
only place set → forward. Decided: take the first hit and name its country in the tooltip
("approx. city centre"), store no "approximate" flag. Revised: the typed name is kept, but the
hit's country code is appended as an uppercase `", XX"` (`"Krung Thep Aphiwat"` →
`"Krung Thep Aphiwat, TH"`, `"ulm,de"` → `"ulm, DE"`), so a wrong pick (Hamburg, US) is visible.

## DONE 51 Reverse geocoding: place name and country from entry coordinates

see docs/51_reverse_geocoding.md

## DONE 53 Entry fact header in exports

see docs/53_entry_fact_strip.md

## DONE 57 Trip default activity

see docs/57_trip_default_activity.md

## DONE 55 Activity per entry

see docs/55_activity_per_entry.md

## DONE 54 Map Assistant

see docs/54_map_assistant.md

## DONE 30 Localization and i18n for Trip Language

see docs/30_trip_language_i18n.md

## DONE 09 Links per entry (`url_<verb>`)

see docs/09_entry_links.md

## DONE 52 Sync Tracks: cloud sync for radio/

see docs/52_radio_sync.md

## DONE 44 Support export trip as WordPress.com post (markup code)

see docs/44_wordpress_export.md

## DONE 34 support html export for trip entries

Similar to entire trip, but only for current day

Covered by todo 47: "⇧ Export Tale" opens the export dialog for the current day (Preview in Browser renders HTML).

## DONE 45 Random travel wisdom and greeting in the window title bar

see docs/45_travel_wisdom_title.md

## DONE 33b Attachment pull (download from cloud)

Split out of todo 32 (D5): add `ObjectStore.get` and pull attachments, so the Smart Sync Attachments row covers both directions.

Status 2026-10-04: implemented as "copy both ways, never delete": pull downloads only objects with no local file (never overwrites), push uploads new/changed local files (so local wins on content conflicts). Smart Sync's Attachments row runs pull, then push; "☁ Pull Attachments" added to the Repository menu. Downloads go to a hidden `.part` file first and are MD5-checked against the ETag. Still open (see todo 33): explicit delete of an attachment on both sides. Tested against the real bucket 2026-10-04: 6 cloud-only files downloaded, second sync reports up to date.

## DONE 32 Smart Sync for repository operations

Replace Sync with an interactive **Smart Sync** dialog (`Cmd+K` → Enter): rows for Connectivity, Commit (editable message, decided by `git status`), Git Remote (fetch + rebase + push, with ahead/behind counts instead of a pull timer) and Attachments push (cloud mode only). Implicit save before the dialog opens, rebase conflicts skip the push but not attachments, Exit stays local. Commit button removed (menu only).

[Plan](docs/32_smart_sync.md)

## DONE 35 visualize end coordinates and make them editable

we have then in the frontmatter since they'll be grapped from gpx imports, but there's no way to set them manually yet, and the only way to visualize them is via "view source"

## DONE 23 Support import gpx

New trip should support import of info from gpx file
Either import button in the existing new trip dialogue, 
or new menu item that launches a file picket first, and then opens the new trip dialogue with prefilled values, check what's better.
Either way, new trip is still the entry point that actually saves the trip and user can overwrite preset values.
in case of gpx import, name shall be derived from gpx->trk->name element 
and the date yyyy-mm-dd can be derived from the time of the first `trkpt` entry in the first `trkseg` segment

```
<?xml version='1.0' encoding='UTF-8'?>
<gpx>
  <trk>
    <name>🍷🚵 RheinRaufTour #1</name>
    <type>touring_bicycle</type>
    <trkseg>
      <trkpt lat="50.352114" lon="7.589085">
        <ele>116.469940</ele>
        <time>2025-04-18T09:57:50.374Z</time>
      </trkpt>
(...)
```
Since user would typcially use the gpx file of the first segment to init the trip, we could optionally also open the first entry (day 1) from the new trip dialogue,
irrespective of whether the trip was entered manually or via import.
Suggest to create a new boolean field named "Init first Tale Entry on trip creation".
In case of manual entry, we can derive name and date from the corresponding trip fields (name -> 1st day entry title, startDate -> 1st day date).
In case of import, we can use the coordinates of the first trkprt for startlat / startlon, date is the same as stardate, and title should be the name mentioned in the gpx file (not the one of the trip that might've been overwritten by the user to a more generic context).
Use ~/tmp/rheinrauf.gpx as sample file 

## DONE 38 show day of total days, add day suffix to tile

Current day display on to right corner shows : Day X (Y days ago). We should add the number of total days. If the trip has an end date, we can simply say Day 5/10 (day 5 of 10 total), everything else should remain the same. If there's no end date, replace the number by the infinity character. Any other suffixes like (Y days ago, first day, last day) can remain as is. ALso the title field should emphasize more that this is the title for a particular day. So instead of plain "Title:" it should be "Title Day X:"
But to preserve space, only show the day number without slash and remarks in brackets.

## DONE 37 shortcuts for zoom in / zoom out in View Menu group

see docs/37_zoom_shortcuts.md

## DONE 25 Delete Tale Entry / Trip

see docs/25_delete_tale_entry.md for the full design (from a grill-me session covering scope
(entry-only, delete-trip deferred), the JGit `git rm`/staging gotcha, the pending-commit DELETE
action and its collision with an uncommitted CREATE, and reusing the existing empty-state
`loadEntry()` path for post-delete UI reset).

## DONE 27 Capture distance and altitude (Höhenmeter) when importing GPX

Todos 23/24 import title/date/start-coordinates from a GPX file but leave `distance` and
`altitude` untouched. Both are derivable from the full track and should be filled in too.

Extend `GpxImport` to walk every `trkpt` in every `trkseg` (not just the first point) and add
two fields to `Parsed`: `distanceKm` (total haversine distance across consecutive points) and
`altitudeGainM` (total climbed, i.e. "Höhenmeter" as cyclists/hikers use the term — total
ascent, NOT net elevation change; 500m up + 490m down is 500m of altitude, not 10m).

Elevation gain algorithm (pin this down, don't leave it to implementation-time guessing):
maintain a running "last significant elevation" reference starting at the first trkpt's `ele`.
For each subsequent point, compute delta against that reference; if `abs(delta) >= 1.0m`, add
the positive deltas to the gain total and move the reference to the new point's elevation
(discard sub-1m deltas as GPS noise, don't accumulate them). The 1.0m threshold is a fixed
constant in `GpxImport`, not a configurable property.

Missing `<ele>` on any point → treat as if the file were unparseable for altitude purposes
(matches `GpxImport`'s existing all-or-nothing failure style); distance can presumably still be
computed independently since it doesn't need `ele`. Sample file: `~/tmp/rheinrauf.gpx`
(~73.5 km, ~474 m gain, computed by rough script during a prior conversation).

Affects both call sites:

1. **New Trip import (todo 23), "Init first Tale Entry" checkbox** — the first entry's
   `distance`/`altitudeMeters` should be set from the GPX totals, same as `title`/`startLat`/
   `startLon` are today (`MainController.onNewTrip`, `NewTripDialog.FirstEntry`). One GPX file
   is assumed to cover that one day's stage — same assumption todo 23 already makes for
   title/date.

2. **Direct Tale Entry import (todo 24), `onImportGpx`** — after `loadEntry()`, also patch
   `distanceField`/`altField` from the GPX totals, in addition to `titleField`/`startLat`/
   `startLon`. This is a scope change from todo 24 decision 6, which left distance/altitude
   untouched on overwrite — now they're overwritten too, gated by the same existing
   confirmation prompt. Update the confirmation wording to describe the fields as a group
   (e.g. "geo track data") rather than enumerating title/coordinates/distance/altitude
   individually, so the copy doesn't need to keep changing as fields are added.

Non-goals: no UI to preview computed values before confirming overwrite (computing stats twice
— once for the confirm dialog, once to apply — isn't worth it for a yes/no prompt); no change
to `GpxImport`'s first-trkpt-only date/name/coords logic, only additive fields.

## DONE 22 End Trip

see docs/22_end_trip.md for the full design (from a grill-me session covering the
start_date/end_date frontmatter rename, validation boundaries, the End Trip button, and
forward-navigation/last-day UI mirroring the existing first-day logic).

## DONE 14 template variables for settings paths

see docs/14_template_variables_for_settings_paths.md for the full design (from a grill-me session
covering directory-level (not just filename) globbing, the ambiguous-match/"first + warn" policy,
TRIP_MONTH/TRIP_YEAR sourced from trip startDate vs. entry date, the per-(pattern, trip) session
cache, and extracting a generic reusable resolver now).

Added later (2026-10-04): `${TRIP_DAY}` (trip's start-date day, zero-padded), sourced the same
way as `TRIP_MONTH`/`TRIP_YEAR`.

Changed 2026-10-10: `${DATE}` (`yyyyMMdd`) replaced by `${ENTRY_YEAR}`/`${ENTRY_MONTH}`/`${ENTRY_DAY}`,
usable in directory segments too (e.g. a per-day `2026_08_07/` folder). The directory cache is now
keyed by the substituted directory pattern, so it's per day when those appear in the directory part.
Misses stay cached on purpose: no folder for a day is common, and sessions are short.

## DONE 15 store optional start point coordinates per trip entry

see docs/15_start_point_coordinates.md for the full design (from a grill-me session covering
the GeoJSON coordinate-order fix, button placement, Save-validation pairing, popup
persistence model, Google Maps URL parsing scope, and parse-failure UX).

## DONE 21 introduce h1 title, derive from route in frontmatter

to align the md layout more with tolaria, we want to introduce a strong "title" per markdown and store as h1 headline on top of the md file.
currently the "route" property in the yaml frontmatter acts as a kind of title, so we can retire it and derive the title from it

* change label in UI from "Route" to "Title" but keep as separat element
* store internally not in frontmatter but as first headline of the markdown payload, start with # (h1) similar to the README.md in trip type
* since there should be only one "# title" in md, if the users also uses "# " in the md document they should be converted as "##" h2 headers during save, there should be alreay logic in the code
* do a one time conversion of the current ~/Pictures/triptale-data repo once all is clarified. not migration support in the app required

Example tale md file before the new change:
```
---
date: 2026-06-19
route: From  Vechta to  Bremen
---

# first part 
tough climb ...

## 2nd part
much better ...

```

After the change:
```
---
date: 2026-06-19
---
# From  Vechta to  Bremen

## first part 
tough climb ...

## 2nd part
much better ...

```

## DONE 18 new top level directories

see docs/18_year_top_level_dirs.md for the full design (from a grill-me session covering the
TripRef identity type, per-year slug uniqueness, year/trip dropdown UX, and the lastTripPath
cache format).

## DONE 11 supports faves & impressions

extend the impressions filter, support a new editable optional prefereces impressionsFaveFilePattern besides impressionsFilePattern.
also add a new bnutton displaying "x Faves" next to the existing "X Impressions" Button. this feature is used to display favourite images, it should re-use the samve image viewer.


## DONE 20 Support Tolaria belongs_to references

We are promoting tolaria as a drop in editor replacement. Tolaria supports references between notes.
This fits our relationships perfectly since tales have an n:1 relationship with notes )belongs_to field in frontmatter)
Example: a note 2026/HollywoodParty_Na/2026-06-21-Sunday.md refers to the trip's readme:


```
---
altitude: 400.0
date: 2026-06-21
distance: 119.0
route: From  Vechta → To Ganderkesee / Bremen
trackurl: https://www.komoot.com/de-de/tour/3055021333
type: Tale
belongs_to: "[[2026/HollywoodParty_Na/README]]"
---
Tale goes here
```

Goal: Store this field on save using the path to the trip readme (w/o md).
Do a one time migration (not part of the app) in ~/Pictures/triptale-data/  

## DONE 17 Add view source

add a new menu item, either existing menu group or new group (view?) to open a window that shows the source of the current markdown file .
If easier use the state currently saved on disk. Goal is to see the frontmatter any potential optimizations triggered during the save save.
maybe we can re-use the markdow viewer used in export diary.

Add new view menu group with action "View Source"

## DONE 16 menu refactoring

Add a new menu group at the very left called TripTale (or whatever the app is named if we have a config or property for the app name)
Move Exit and Edit Settings from File to this group, and move About from the Help group there as well. Remove the Help group which is now empty
Rename "Exit" to "Quit TripTale" (same app name as menu group )
Rename "About" to "About TripTale"
New Trip and Export Diary remain in File 
Rename "Remote" Group to "Repository"

## DONE 13 settings handling

See docs/13_settings_handling.md for the full design (from a grill-me session covering the
settings.yml/.state.yml split, settings directory location, startup warning/init-prompt flow,
and settings dialog rework).

## DONE 12 Add Remote Sync all-in-one operation

Add a new sync operation to the Remote menu that performs all git operations necessary to sync the remote menu. Only active if there's connectivity. Suggest it performs a commit of outstanding changes first (even if none are store in memory, there may be changed performed outside the app), followed by a rebase from remote, followed by a push. But suggest better workflows if you can think of improvements.
also switch to emojis in menu (like "export diary") since the current icons for pull, push etc. are hard to distinguish. Last but not least, add the sync button to the buttom panel right behind commit

## DONE 10 Impressions Feature

see docs/10_impressions_feature.md

## DONE 08 Fix Save Bug when navigating away from unsaved entry

When navigating from an unsaved entry to the next day, the system will show a confirmation that allows to discard changes or save them.
But the app apparently already points to the next day, so the updated tale will be saved along with the wrong entry

## DONE 06 Enhance "Tales" Label

Replace the static "Tales" label with a single dynamic label (same `.section-label` style):

* Empty state (tales text blank/whitespace-only, regardless of whether the entry file exists): `🐉 Tales · here be dragons`
* Content state: `🐉 Tales · {n} words updated {relativeTime}`, e.g. `🐉 Tales · 24 words updated 2 days ago` (single · divider, no second divider before "updated")

Word count: `text.trim().split("\\s+")`, count non-empty tokens.

Timestamp source: `Files.getLastModifiedTime()` on entry load; on save, use `Instant.now()` directly (no re-read from disk).

Relative time buckets (singular/plural correct, no upper cap):
* <10s → "just now"
* <60s → "N seconds ago"
* <60min → "N minute(s) ago"
* <24h → "N hour(s) ago"
* ≥1 day → "N day(s) ago"

Refresh points: recompute only on entry load/switch and immediately after successful save — no periodic timer (label doesn't update while typing).

New code: pure util classes, no JavaFX imports, unit-testable (like `Slugs`/`SlugsTest`):
* `net.timafe.triptale.util.TextStats` (word count)
* `net.timafe.triptale.util.RelativeTime` (bucket formatting)

Wiring in `MainController`: update label at entry load and in `onSave()` alongside `snapshotBaseline()`.

## DONE 07 Check feasability of HTML Preview for markdown export

see docs/07_html-preview-export.md

## DONE 05 Tale testbox light background

I like the overall darkmode look and we should keep it, but for the actual tale text I still prefer light background with dark font since it's easier to read.
Maybe not white but a very light beige as background, and darkblue as foreground so it looks more like a written tale?
Make suggestions

## DONE 04 show track-url input with label "Track URL" to be stored in yaml frontmatter

this should be an url to an external tracking tool. if filled, there should be an icon bwhind it to open the URL in the System's browser
Should be on the same line as kilometers and altitude

## DONE 02 evaluate potential for native image

see docs/01_native-image-eval.md

## DONE 01 Fix unsaved changes when navigating thru entries

otherwise changes will be lost

## DONE 03 show current memory consumption in about -> info

use whatever runtime memory feedback is appropriate for current usage, but I want only a single figure
