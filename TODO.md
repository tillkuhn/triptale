# ToDos for this app

## Next Todo numer: 11

## 10 Impressions Feature

The export for html should support images per day entry, suggest to make this optional with a boolean flag.
To support this we need a new preference in prefs.yaml `impressionsFilePattern` that points to a file matching pattern that should support common file globs and predefined variables. One would be $HOME for user HOME, one would be $DATE for YYYYMMDD date.  to be able to edit the pattern, it's about time to introduce a simple edit prefs dialogue. 
For example entry ${HOME}/Pictures/00_Faves/output/${DATE}*.jpg would match 20260807_204352_Fireshow+_mini.jpg if the date of the current entry is  20260807! There should also be a new button behin the route input field (which is too wide anyway) that shows "No Impressions" if either the property is not set, or if no matching impressions (image files) could be found. If >0 images could be found, it should show "1 Impression>", "2 Impressions" etc. The link should open a small popup that allows to scroll thru the images, maybe same navigation as entry navigator (move to first, move forward, move backward, move to end). Make the discovery smart to limit the perfomance penalty when navigating. For example, in the sample pattern above all files are in a single directory, so no need for recursive search, it only has to look for matching files in that dir. If too complex, skip the glob support for now so we simply assume a pattern that points to files within a directory.
In html export, the images should be shown as a grid with 2 columnes, probably easiest to use html table. Not sure if we can do the same for markdown, pls advise. to changes in a feat branch. this is a complex change so apply grill-me.


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
