# ToDos for this app

## Next Todo: 51

## 50 WordPress export follow-ups

Leftovers from todo 44 (stage 1 shipped: Format → WordPress in Export Diary, CDN images).

- Day heading wording for blog posts (currently the Markdown export's `2026-05-29 Friday
  Day 1: …`).
- Watch the CloudFront bill in Cost Explorer for a few weeks; if it isn't ~€0, set
  `enable_cdn = false` and host images on our own webserver (the blog is a Simple site, no SFTP).
- Optional stage 2: create the post as a draft via the API. **Works** (2026-10-10): app
  "TripTale" (id 150174) at developer.wordpress.com, OAuth2 token, `POST
  public-api.wordpress.com/wp/v2/sites/timafe.wordpress.com/posts` with `status=draft`. Open:
  browser login (no stored password) vs. password grant.

[Background](docs/done/44_wordpress_export.md)

## 49 Bulk import a whole trip's photo-lib impressions as attachments

Split out of todo 47 (deferred there). Trip menu action that copies every day's photo-library
images (base filter applied, optionally faves only) into the matching attachment day folders,
with a confirmation showing counts per day and total size. Best after todo 33c, so a mistaken
bulk import can be deleted everywhere.

## 33c Attachment delete propagation (sync base)

Cloud attachment sync never deletes: a locally deleted attachment comes back with the next
pull, and a stale copy can overwrite a newer remote edit. Since todo 47's bulk import, mistaken
imports need a real delete. Sync as a three-way merge against a per-device sync base (no base,
no deletes), see [ADR-0002](docs/adr/0002-attachment-sync.md).

- `ObjectStore.delete`, `SyncBase` file, three-way `plan()`, local deletes to the OS trash.
- Smart Sync shows deletes and stops on mass deletes; viewer gets "🗑 Delete Selected".
- Bucket versioning + 30-day noncurrent expiry in `terraform/`. Prerequisite for todo 42's remote side.

[Details](docs/33c_attachment_delete_propagation.md)

## 48 Internal paragraphs: hide private notes from exports

A tale heading `internal` or `private` (any level, case-insensitive, exact word, optional
trailing `:`) marks a private block, e.g. `## internal` followed by original distance/route
notes. On export, drop the heading and everything up to the next heading of the **same or
higher** level (subheadings stay inside the block), or to the end of the tale.

- Strip centrally in `DiaryExporter` before `Markdown.shiftHeadings`, so every export format
  is covered; the editor and the word count stay unchanged.
- Ignore `#` lines inside fenced code blocks; a tale that ends up blank is treated as empty.
- Unit tests: nesting, end of tale, `Private:` vs. `Private beach`, code fences.

## 47 Impressions / exporter refactor: images as attachments

Impressions come from a selectable source (Local Photo Lib / Trip Attachments / Pick Folder),
reduced by `impressionsBaseFilter` and optionally `impressionsFaveFilter` (filename globs).
The viewer imports selected images into the day's attachments; the Faves button is gone. Export
gets a From/To entry range, an image source + "Faves only", and a per-entry "⇧ Export Tale".
Status: implemented on `feature/47-impressions-refactor`, pending visual review. Deferred:
resize on import, public URLs (todo 44), bulk import of a whole trip (todo 49).

[Details](docs/47_impressions_attachments_refactor.md)

## 46 Radio: play favorite tracks from a radio/ pool

I listen to a lot of music on bike trips and often quote a small tracklist in a tale. Add a
"radio": a reusable pool of favourite tracks in `<data-dir>/radio/` (not versioned in git,
separate from attachments), playable from the app while writing.

- Stage 1 (in progress, branch `feature/radio`): `javafx-media` playback, `radio.RadioLibrary`
  + `ui.RadioPlayer`, Radio menu + 🎵 toolbar button: random track, play/pause, auto-next, stop,
  open radio folder. Managed `radio/.gitignore`. Gated off by default behind a new "Enable Radio
  (restart required)" setting until it's out of alpha.
- Stage 2: cloud sync of `radio/` via its own S3 prefix (generalize `AttachmentSyncer`).
- Stage 3: Tolaria-style wikilinks to tracks in tales (`Track` note type?), "Soundtrack of this
  trip" playlist, 📌 insert `[[current track]]` at the cursor, export rendering.
- Open: Linux playback depends on a supported system FFmpeg — test the release jar.

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

## 41 justified-row (Flickr/Google Photos-style) impressions gallery for HTML export

Upgrade the HTML export's impressions gallery from the CSS `column-count` masonry layout
(quick win, no gaps, but loses left-to-right photo order) to a justified-row gallery: full-width
rows with one shared row height per row, zero gaps, chronological order preserved.
Needs a packing algorithm, numeric image dimensions (`ExifReader.dimensions()` refactor),
a static-HTML sizing strategy and a fallback for non-JPEG images.

[Details](docs/41_justified_row_impressions_gallery.md)

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

## 31 Git Housekeeping

Add a new action to the repository menu group called "Run Housekeeping" or similar, it should run "git gc" on the data repo and show the results
Example (...)
Writing objects: 100% (1027/1027), done.
Total 1027 (delta 453), reused 972 (delta 415), pack-reused 0

## 30 Localization for Trip Language

The single language for the application (e.g. menu entries, labels etc.) will remain english.
But since the contributed trip tales are potentially in a different language, and the exporter adds some words such as "Distance, Day etc." we should add support for i18n as far as derived trip entry content is affected. 
Add a new setting defaultTripLanguage, possible values for locales are right now  German (de) and English (en), default is English.
Add support for properties to translate certain values, but should always default to english. 
Current content for german translation:

Day = Tag
Distance = Distanz
Altitude = Höhenmeter

In Export Diary, use these values instead of the current hardcoded english versions when template content is written, let me know if I forgot anything

## 29 implement delete (entire) trip

we already have delete tale, but should be also possible to delete entire trip, which requires recursive delete of the folder in the git repo.
add a button to the edit trip dialogue, and let the user confirm before performing the action, preferably with some stats like "You are about to delete one trip with X entries, are you sure?"

## 28 store git revsion and add update check

Triptale -> About currently only shows the maven version which we don't maintain. It should show the git revision instead, derived from latest tag. 
Add "Check for updates" function in Triptale left menu. Should to the github source (which is already in the system and public) and check for most recent release. If equal or older to current (older is possible since a local version could be ahead), show that the app is up2date.
If there is a newer version (all should be semver), show a text with the new version and a hyperlink where to download.
Use the dynamic dialogue from "Sync" while checking the remote with the spinner. If that is not a reusable dialogue yet, do it now since we have further future use cases that should show verbosely that there is interaction with a remote page

## 26 Show no of objects and repo size, optionally run gc

in repo info dialogue, show output of `git count-objects -H` e.g. 359 objects, 1.62 MiB`. also maybe add housekeeping task that calls "git gc" and capture output

## 24 Support import gpx for Trip Entries

see docs/24_gpx_import_tale_entry.md for the full design (from a grill-me session covering the
menu placement/enablement, all-or-nothing overwrite confirmation gated on disk state (not form
dirty state), reuse of the existing datePicker navigate-away guard, prefill-only/no-auto-save
semantics, and the todo 25 delete-capability gap it surfaced).

similar to todo 23 it should be possible to fill values for a trip tale entry via gpx.
Trigger: Menu link in "Tale Entries" Group ".
Let's skip a dedicated button in the UI, since it is fixed to particular day, and the import derives the day from the first trkpt element
Similar to todo 23, the import should prefill title, and determine the date and start pos from the gpx data.
If a day entry already exists, it should prompt if user wants to overwrite (but if yes overwrite only the field that can be derived, i.e. do not empty existing other fields    

## 19 DatePicker has no quick year navigation

The DatePicker popup (New Trip dialog, main date picker, anywhere else it's used) only lets you
page month-by-month via the `<`/`>` arrows next to the month/year header — there's no direct
year jump. Picking a date a year or more away (e.g. backfilling a 2025 trip while today is
2026) means clicking through many months one at a time. JavaFX's DatePicker doesn't expose a
year spinner natively; investigate a day-cell-factory-based or header-replacement workaround.
Affects every DatePicker instance in the app, not just one dialog — worth checking all call
sites for a consistent fix rather than patching one.

---

## DONE 44 Support export trip as WordPress.com post (markup code)

see docs/done/44_wordpress_export.md

## DONE 34 support html export for trip entries

Similar to entire trip, but only for current day

Covered by todo 47: "⇧ Export Tale" opens the export dialog for the current day (Preview in Browser renders HTML).

## DONE 45 Random travel wisdom and greeting in the window title bar

see docs/done/45_travel_wisdom_title.md

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

see docs/done/37_zoom_shortcuts.md

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

## DONE (WON'T DO) 09 New concept for storing links

we need a flexible way to store multiple links. introduce new "links" array in frontmatter for trip entry.
the actual link should have a mandatory "url" property. kind is optional and should allow any string value, but for the UI
we should enforve an enumerated value, suggest to use short very. Initial list "drink, eat, sleep, hike, bike, dive" (list should sort alphabetically). title is just an optional string


```
---
date: 2026-07-24
route: 'Essen → München'
links:
  - url: https://muc1.com/
    kind: hike
    title: Nice GPX routed for Muc 
  - url: https://bar.com/
    kind: drink
    title: best bar in town
  - url: https://random.com/
```

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

see docs/done/10_impressions_feature.md

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

see docs/done/07_html-preview-export.md

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
