# ToDos for this app

## Next Todo: 28

## 32 Smart Sync

We currently have 3 operations to persist trip enty data: Save Tale (to local disk), commit (git commit for saved unstaged data) and Sync (kind of all in one for commit, pull / rebase and push) which could be confusing since you typcially don't want to bother with Git Operations.
Suggested Improvement:
- Keep Save as cheap local "store to file system" operations
- Remove Commit as a button (but keep it in the menu for "power users") and rebrand Sync as "Smart Sync" for the new interactive operation to interact with the repo
- Store lastRemotePull date in .state.yml (but only if successful) 
- Introduce new setting suggestedPullIntervalMins to store the number of minutes since last pull after which the smart sync dialogue should suggest a pull (default checked)

Smart Sync Dialogue Details row by row (as opposed to current sync, it will require user interaction):
Connectivity: Show either "Not connected" or "Online (github.com or whatever is the remote host)"  
Commit: Show and input field 2 lines (but expandable) with the default commit message (based on pending changes as currently) but allow user to overwride. To be discussed: There could bee a case were the system is not aware of pending changes but still files were changed on the file system e.g. by another app, so for git it's dirty. Discuss how to deal with this
Remote Pull: [x] Boolean if sync should inclue a pull, defaults to true if the time since last sync is higher than suggestedPullIntervalMins. always display relative time of last sync behind (e.g. 5m, 10h). If not connected, it is false and read only since pull is not possible
Remote Push: [x] Boolean if data is pushed, enabled by default if online, otherwise same as remote pull (read only false) with a remark (Offline)
Fixed spaced for in progress area showing spinner, current action and result of interaction. 

Buttons should be only Close (close window w/o acttion() or Sync. When sync is used, update the progress area. Leave the window open so the user can see the results, and need to close manually

Since Sync will now also handle local commits, the button in the main window needs to be active even if offline.  


## 31 Git Housekeeping

Add a new action to the repository menu group called "Run Housekeeping" or similar, it should run "git gc" on the data repo and show the results
Example (...)
Writing objects: 100% (1027/1027), done.
Total 1027 (delta 453), reused 972 (delta 415), pack-reused 0


## 30 Localization for Trip Language

The single language for the application (e.g. menu entries, labels etc.) will remain english.
But since the contributed trip tales are potentially in a different language and the exporter adds some words such as "Distance, Day etc." we should add support for i18n. 
Add a new setting defaultTripLanguage, possible values fpr locales are German and English, default is English.
Add support for properties to translate certain values, but should always default to english. 
Current content for german translation:

Day = Tag
Distance = Distanz
Altitude = Höhenmeter

also the Date formats should honor this settings, e.g. currently the current day displays as 20xx-xx-xx Saturday, and if trip language is german it should be "Samstag" (if necessary we need to add those weekdays to the translation).
In Export Diary, use these values instead of the current hardcoded english versions, let me know if I forgot anything

## 29 implement delete (entire) trip

we already have delete tale, but should be also possible to delete entire trip, which requires recursive delete of the folder in the git repo.
add a button to the edit trip dialogue, and let the user confirm before performing the action, preferably with some stats like "You are about to delete one trip with X entries, are you sure?"

## 28 store git revsion and add update check

Triptale -> About currently only shows the maven version which we don't maintain. It should show the git revision instead, derived from latest tag. 
Add "Check for updates" function in Triptale left menu. Should to the github source (which is already in the system and public) and check for most recent release. If equal or older to current (older is possible since a local version could be ahead), show that the app is up2date.
If there is a newer version (all should be semver), show a text with the new version and a hyperlink where to download.
Use the dynamic dialogue from "Sync" while checking the remote with the spinner. If that is not a reusable dialogue yet, do it now since we have further future use cases that should show verbosely that there is interaction with a remote page

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

## 26 Show no of objects and repo size, optionally run gc

in repo info dialogue, show output of `git count-objects -H` e.g. 359 objects, 1.62 MiB`.
also maybe add housekeeping task that calls "git gc" and capture output

## DONE 25 Delete Tale Entry / Trip

see docs/25_delete_tale_entry.md for the full design (from a grill-me session covering scope
(entry-only, delete-trip deferred), the JGit `git rm`/staging gotcha, the pending-commit DELETE
action and its collision with an uncommitted CREATE, and reusing the existing empty-state
`loadEntry()` path for post-delete UI reset).

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

## 23 Support import gpx

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

## DONE 22 End Trip

see docs/22_end_trip.md for the full design (from a grill-me session covering the
start_date/end_date frontmatter rename, validation boundaries, the End Trip button, and
forward-navigation/last-day UI mirroring the existing first-day logic).

## DONE 15 store optional start point coordinates per trip entry

see docs/15_start_point_coordinates.md for the full design (from a grill-me session covering
the GeoJSON coordinate-order fix, button placement, Save-validation pairing, popup
persistence model, Google Maps URL parsing scope, and parse-failure UX).

## DONE 14 template variables for settings paths

see docs/14_template_variables_for_settings_paths.md for the full design (from a grill-me session
covering directory-level (not just filename) globbing, the ambiguous-match/"first + warn" policy,
TRIP_MONTH/TRIP_YEAR sourced from trip startDate vs. entry date, the per-(pattern, trip) session
cache, and extracting a generic reusable resolver now).

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

## TODO 19 DatePicker has no quick year navigation

The DatePicker popup (New Trip dialog, main date picker, anywhere else it's used) only lets you
page month-by-month via the `<`/`>` arrows next to the month/year header — there's no direct
year jump. Picking a date a year or more away (e.g. backfilling a 2025 trip while today is
2026) means clicking through many months one at a time. JavaFX's DatePicker doesn't expose a
year spinner natively; investigate a day-cell-factory-based or header-replacement workaround.
Affects every DatePicker instance in the app, not just one dialog — worth checking all call
sites for a consistent fix rather than patching one.

## DONE 18 new top level directories

see docs/18_year_top_level_dirs.md for the full design (from a grill-me session covering the
TripRef identity type, per-year slug uniqueness, year/trip dropdown UX, and the lastTripPath
cache format).

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

## DONE 11 supports faves & impressions

extend the impressions filter, support a new editable optional prefereces impressionsFaveFilePattern besides impressionsFilePattern.
also add a new bnutton displaying "x Faves" next to the existing "X Impressions" Button. this feature is used to display favourite images, it should re-use the samve image viewer.

## DONE 10 Impressions Feature

see docs/done/10_impressions_feature.md

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

## DONE 08 Fix Save Bug when navigating away from unsaved entry

When navigating from an unsaved entry to the next day, the system will show a confirmation that allows to discard changes or save them.
But the app apparently already points to the next day, so the updated tale will be saved along with the wrong entry

## DONE 07 Check feasability of HTML Preview for markdown export

see docs/done/07_html-preview-export.md

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

## DONE 05 Tale testbox light background

I like the overall darkmode look and we should keep it, but for the actual tale text I still prefer light background with dark font since it's easier to read.
Maybe not white but a very light beige as background, and darkblue as foreground so it looks more like a written tale?
Make suggestions

## DONE 04 show track-url input with label "Track URL" to be stored in yaml frontmatter

this should be an url to an external tracking tool. if filled, there should be an icon bwhind it to open the URL in the System's browser
Should be on the same line as kilometers and altitude

## DONE 03 show current memory consumption in about -> info

use whatever runtime memory feedback is appropriate for current usage, but I want only a single figure

## DONE 02 evaluate potential for native image

see docs/01_native-image-eval.md

## DONE 01 Fix unsaved changes when navigating thru entries

otherwise changes will be lost
