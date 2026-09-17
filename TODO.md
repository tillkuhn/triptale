# ToDos for this app

## Next Todo: 22

## 21 introduce h1 title, derive from route in frontmatter

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


## 19 DatePicker has no quick year navigation

The DatePicker popup (New Trip dialog, main date picker, anywhere else it's used) only lets you
page month-by-month via the `<`/`>` arrows next to the month/year header — there's no direct
year jump. Picking a date a year or more away (e.g. backfilling a 2025 trip while today is
2026) means clicking through many months one at a time. JavaFX's DatePicker doesn't expose a
year spinner natively; investigate a day-cell-factory-based or header-replacement workaround.
Affects every DatePicker instance in the app, not just one dialog — worth checking all call
sites for a consistent fix rather than patching one.

## 18 new top level directories

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

## 15 store optional start point coordinates per trip entry

Add optional start-point coordinates to a trip entry's frontmatter, alongside the existing
`altitude`/`distance`/`route`/`trackurl` fields. Keep frontmatter flat and simple (no nested
geojson-style object) — two independent lowercase scalar keys, `startlat`/`startlon`, each a
`Double` (null = not recorded, following the existing `altitude` convention). Keep frontmatter
keys alphabetical per `MarkdownStore.saveEntry`. Needs: fields on `DiaryEntry`/`DiaryEntry.Builder`,
read/write in `MarkdownStore`, UI inputs (decimal, accepts `.`/`,`), and export template support.

## 14 template variables for settings paths

Generalize `ImpressionsResolver`'s existing `${HOME}`/`${DATE}` placeholder substitution into
a shared, reusable resolver, add `${TRIP_SLUG}` (and rename/extend `${DATE}` towards a
`${TALE_DATE}`-style name if it helps clarity), and apply it beyond impressions patterns to
other settings paths (e.g. `dataDir`). Expansion happens at runtime when the value is actually
used (writing an entry file, locating images), not at settings-save time. Follow-up to todo 13
— see docs/13_settings_handling.md non-goals section.

## DONE 13 settings handling

See docs/13_settings_handling.md for the full design (from a grill-me session covering the
settings.yml/.state.yml split, settings directory location, startup warning/init-prompt flow,
and settings dialog rework).

## DONE 12 Add Remote Sync all-in-one operation

Add a new sync operation to the Remote menu that performs all git operations necessary to sync the remote menu. Only active if there's connectivity. Suggest it performs a commit of outstanding changes first (even if none are store in memory, there may be changed performed outside the app), followed by a rebase from remote, followed by a push. But suggest better workflows if you can think of improvements.
also switch to emojis in menu (like "export diary") since the current icons for pull, push etc. are hard to distinguish. Last but not least, add the sync button to the buttom panel right behind commit

## 11 supports faves & impressions

extend the impressions filter, support a new editable optional prefereces impressionsFaveFilePattern besides impressionsFilePattern.
also add a new bnutton displaying "x Faves" next to the existing "X Impressions" Button. this feature is used to display favourite images,
it should re-use the samve image viewer.


## DONE 10 Impressions Feature

see docs/done/10_impressions_feature.md

## 09 New concept for storing links

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
