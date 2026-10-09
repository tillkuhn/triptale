# 47 Impressions / exporter refactor: images as attachments

Today's post images come from `impressionsFilePattern` (`storage.ImpressionsResolver`), a
machine-local glob like `${HOME}/Pictures/${TRIP_YEAR}*${TRIP_MONTH}*${TRIP_DAY}*/${DATE}*.jpg`. That means the HTML
export only produces usable output on the one machine that holds the photo library, and no
export can reference an image by URL, because the files exist nowhere else.

## Goals
Be able to **import images as attachments by selecting them**, and give the exporters a
"source" switch between the local photo library filesystem location and the attachments of the current entry.
In addition, it should be possible to import impressions from an arbitrary directory picked ad hoc.

Attachments already fit: `attachments/<year>/<slug>/<date>-Weekday/` is one folder per entry
day (`attachments.AttachmentsDir`), which maps one-to-one onto "one gallery block per day",
and `attachments.AttachmentSyncer` already pushes them to S3 — so attachment-backed images are
the ones that can get a public URL (see todo 44).


Drop "Faves" as a separate source concept. Rather make it a subset of selected impressions based on patterns, 
which fits current workflow of adding "+" to the basename. Suggest to add a filter mechanism that works 
on top of the existing source (works for photo library source or attachments or whatever we might introduce).
Idea: impressionsBaseFilter, define in settings, would simply act as a filter for image extensions within a directory,
to exclude txt files, pdf, gpx etc.! Should be a lit, but I think space separated string should be ok (challenge of need be).
Example: "*.jpg *.jpeg *.png"
That base filter would implicitly apply first to reduce the set of matching files.
In addition we can have impressionsFaveFilter (also new setting) for example "*+.???" that could be applied optionally in exporter and viewer.
Example: "a.jpg, b.jpg, c+.jpg, d.txt": Base filter would reduce to the 3 jpg. files, and if fave filter is applied on top, it would reduce the result to c+.jpg

## Changes to the the main entry screen:

Drop the faves button and keep impressions with dynamic value only. 
Pattern: "🖼️ Impressions <no-of-photolib-expressions> 🗂️ / <no-of-photolib-expressions> <paperlclip-attachment-icon>
Ignore fave filter for count, but apply the base filter so we focus on images only. 
If neither is >0, still activate the button since we also add possibility to pick from arbitrary directory but maybe make it explicit in the label that both predefined sources are empty.
When impressions dialogue is openened, there will be new interaction possibility in the lower section above the actual buttons.
One is "Asset Source" which is "Local Photo Lib" (as per existing impressionsFilePattern ), "Trip Attachments" (the new attachments folder)" and "Pick Folder" to choose an arbitrary folder. right to that selector should be the resolved directory resp. resolved  (using ~for home if part of the path), followed by an icon to to pick a different directory which is disabled for all sources expect "Pick Folder". 
 Default for selection should be: If >0 image attachment, pick attachment (it's usually lower compared with local photo lib since we cherry pick images). If image attachments is 0 but photolib has at least one match, pick photo lib. if both are zero, it's pick directory which will enable the file chooser icon mentioned above to pick a directory. 
 When the source changes, obviously the set of images to scroll to changes. 
 Also there will be a flag "Faves only (<no-of-faves>/no-of-totals)", enabled if there's at least one matching fave, showing "no pattern defined" if it's empty in the settings and "zero matching faves" or similar if no file matches in current set. If enabled and true, the active set will shrink and fave filter is applied.
 Also each image shall have a checkbox to select / unselect is. And there shall be a new toggle button label "Select All" "Unselect All" depending on current state with a checkbox icon, Action obvious. Add another button "Import Selected" with the attachmment paperclip icon that is enabled once at least in impression is selected, and that would copied the selected files to the attachment folder

## Changes to Exporter

Exporter should also use Source Selector (to decide attachments vs photo lib), and optional faves filter (maybe we can share that part somehow), but not the adhoc "Pick Folder" option as this is mainly for importing files as image attachments, and exporter should only generate export content, not modify entries or add attachments.
It would be also beneficial to have exporter on Trip AND Trip Entry level. Suggest to introduce and entry range where one could pick start and end entry date that should be covered in the export. If invoked from trip menu (the current way to do it), it should default to ´first and last available end date. 
If invoked from Entry (new button besides save ), it should have from and to set to the the active entry data so it only covers that day.
Still, both should be dropdowns sorted by start_date ascending . e.g. "Day 2: Frankfurt to Mannheim" -> "Day 4: Stay at Bodensee"
We can get rid of the Copy ("to markdown" button), we will add this to exporter later so copy to blipcboard will just be another target option.
 

## Decisions (grilled 2026-10-09)

Supersedes the original "design points" list; the ideas above are the input, these are binding.

**Model**
- New package `impressions` (no JavaFX — added to the package boundary rule). It can't live in
  `storage`: the attachments source needs `attachments.AttachmentsDir`, which depends on
  `storage.MarkdownStore` (cycle).
- `ImpressionSource` is a sealed interface of value records — `PhotoLibrary`, `TripAttachments`,
  `Folder(Path)` — resolved by one `ImpressionsService` (directory + file list per trip/date).
  Values, not strategy objects, so they sit directly in combo boxes. `Folder` is viewer-only.
- `ImpressionsMode` (NONE/FAVES/ALL) is removed. Exporter takes a nullable source (null = no
  images) plus a `favesOnly` flag.

**Filters / settings**
- `impressionsBaseFilter` (default `*.jpg *.jpeg *.png`, the formats JavaFX can display) and
  `impressionsFaveFilter` (default empty = "no pattern defined"): space-separated globs, matched
  against the **filename only**, **case-insensitive**. Base filter always applies first (also on
  top of `impressionsFilePattern`'s own filename glob); fave filter is an optional subset.
- `impressionsFaveFilePattern` is dropped. `AppSettings` ignores unknown keys, so old
  `settings.yml` files still load (the mapper used to fail on unknown properties).

**Main screen**
- Faves button + "Show Faves" menu item removed. One button:
  `🖼 Impressions <photolib> 🗂 / <attachments> 📎 ›`, counts with base filter, without fave
  filter; `🖼 Impressions: none – pick folder ›` when both are 0. Enabled whenever a trip+date
  is selected.
- Main-screen "📋 Copy Tale" button removed (menu item stays). Its spot gets "⇧ Export Tale",
  which runs `saveIfDirty()` and opens the export dialog with From = To = current entry;
  disabled while the day has no entry file.

**Impressions viewer**
- Lower section: Source combo (Trip Attachments / Photo Library / Pick Folder), resolved
  directory (home-relative), 📂 button (enabled only for Pick Folder); then "Faves only (n/total)"
  checkbox (disabled with "no pattern defined" / "no matching faves"), Select All ⇄ Unselect
  All, "📎 Import Selected (n)".
- Default source: attachments if >0 images, else photo lib if >0, else Pick Folder.
- Pick Folder with no folder yet opens the DirectoryChooser immediately; initial dir = last
  folder used this session (shared with Add Attachments), else `~/Pictures`, else `$HOME`.
- Selection: "Select" checkbox for the current image (Space toggles), keyed by path. Select
  All acts on the visible set (after faves filter). Toggling Faves only keeps the selection;
  switching source clears it.
- Import is disabled for the Trip Attachments source. Import copies originals 1:1 (EXIF kept)
  via `AttachmentsDir.addFiles` — same-name files are **overwritten**, like Add Attachments.
  No `addPending` (consistent with Add Attachments; git-mode files are caught by
  `dirtyFiles()`). Dialog stays open, clears the selection, reports via status and a callback
  that refreshes the main button counts.
- Empty active set: placeholder text in the fixed 640x480 box; nav/select/maps disabled.

**Exporter**
- From/To combos of the trip's entries ("Day 2: Frankfurt to Mannheim"), ascending. Trip menu
  defaults to first/last entry; Export Tale to the current day. From > To is auto-corrected.
- Header totals (dates, days, entry count, distance, altitude) cover the range; "Day N" stays
  relative to the trip start.
- Images: None / Photo Library / Trip Attachments + "Faves only". Default: attachments if any
  image attachment in range, else photo lib if a pattern is configured, else None.
- Export dialog keeps its own Copy button; clipboard becomes an export target later.

**Deferred**
- Resize on import → todo 44 stage 2 (ImageIO would drop EXIF incl. GPS).
- Public S3/CDN URLs in exports (`publicAttachmentBaseUrl` doesn't exist yet) → todo 44.
- One-off bulk "import the whole trip's photo lib impressions" action — per-day import in the
  viewer covers migration for now.

## Status (2026-10-09)

First implementation committed on branch `feature/47-impressions-refactor` (not merged). Tests
green (278), app boots; user's first look: "very cool at a glance". Still open:

- **Visual review by the user** (an agent can't drive the UI): main entry row width with the new
  impressions label + "⇧ Export Tale"; viewer's lower two rows (dir label ellipsis, dialog
  height); Pick Folder chooser auto-opening on a day with no images; export From/To combos and
  From > To auto-correction.
- Mark todo 47 done after review (move doc to `docs/done/`).

Where things live:
- `impressions/ImpressionSource` (sealed records), `ImpressionsService` (directory, images with
  base filter, fave filter, import), `ImageFilter` (space-separated case-insensitive globs).
- `storage/ImpressionsResolver.resolveDirectory(pattern, trip)` — now public, used for the
  photo library's directory label.
- `ui/dialog/ImageViewerDialog` — rewritten around an inner `Session` class holding the state;
  `show(trip, date, onImported)`.
- `ui/dialog/ExportDiaryDialog` — `show(trip)` / `show(trip, from, to)`; image choice is a
  private `Images` enum (None / Photo Lib / Attachments).
- `export/DiaryExporter` — `exportTrip(trip, from, to)`, `exportTripAsHtml(trip, from, to,
  source, favesOnly)`; a range starting at the first entry keeps the trip's own start date.
- `ui/RecentFolder` — session folder memory shared by Add Attachments and Pick Folder (defaults
  to `~/Pictures`, else `$HOME`; Add Attachments used the OS default before).
- `AppSettings` is `@JsonIgnoreProperties(ignoreUnknown = true)`; new keys
  `impressionsBaseFilter`, `impressionsFaveFilter`.

## Relation to todo 44

Separate concerns. Todo 44 only needs to prove that *some* public HTTPS host can serve images
to a handcrafted WordPress post; how the images get there is this todo. Once both land, the
WordPress export can emit gallery blocks pointing at the CDN automatically.
