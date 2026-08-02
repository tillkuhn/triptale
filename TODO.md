# ToDos for this app

## Next Todo numer: 08

## DONE 07 Check feasability of HTML Preview for markdown export

see docs/done/07_html-preview-export.md

## 06 Enhance "Tales" Label

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
