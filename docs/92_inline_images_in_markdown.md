# 92 Inline images in Markdown

Images so far only appear in the per-day gallery (backpack, `ImpressionsService.galleryRows`).
This adds `![name](attachments/...)` between paragraphs of the trip `README.md`, Post intro text
and daily entry files.

## Syntax and validity

- Always `![name](location)`, location relative **from the data-dir root**, so it starts with
  `attachments/`. This keeps links resolvable in Tolaria and independent of the file's own folder.
- Only objects under `attachments/` are valid, not necessarily the same trip (`tripmap.png`
  included). No `..` escapes, no http(s) URLs.
- An inline image is a **block**: the reference alone on its line, an empty line before and after
  (or start/end of the text). Anywhere else (e.g. inside a sentence) it stays plain text, no error.
- Invalid references (missing file, outside `attachments/`, not a block) are never silently
  dropped; the text is left untouched.

## Exporters

- Evaluated mainly by the **HTML/PDF** and **WordPress** exporters; plain Markdown and social
  exports pass the line through unchanged.
- HTML/PDF: `<figure>` with `<img>` and `<figcaption>`, from the local file (like the gallery's
  file URI), click opens the original in the named tab.
- WordPress: core image block (`size-large`) pointing at `attachments.publicBaseUrl` + `urlPath`,
  exactly like the gallery images (no media-library upload). The file must be synced to the bucket.
- Every exporter runs `Markdown.stripPrivate` first, so images in private sections disappear
  with them.
- Name = alt text = visible caption. Derived from the file name by
  `WordPressExporter.altText` (strip extension, timestamp prefix, `_mini`/`+` suffixes, `_`/`-`
  to blanks, split camelCase): `20260604_154300_TillBikeVidovaGora+.jpg` becomes
  `Till Bike Vidova Gora`. Move it to a shared helper; the HTML gallery images get the same alt.
- File only on the bucket, not on disk: skip the image and report it (no download during export).
- Problems are reported at export time ("3 inline images skipped: ..."), no live checking while
  typing, no trip-wide checker.

## Gallery overlap

Preferred: an image referenced inline is removed from that day's gallery. Needs the tale text
parsed before the layout step. If that gets complex, postpone and keep both (the author decides).

## Impression Curator

- New button `📋 Copy Link` copies the **bare line**
  `![Till Bike Vidova Gora](attachments/<year>/<slug>/<date>/<file>)` (no blank lines).
- Enabled only for an image that is in the backpack. Library or picked-folder images have no
  `attachments/` path yet: disabled, tooltip "Put into 🎒 first". No auto-put.
- `－ Unpack from 🎒`: before deleting the backpack copy, scan the trip's README, entries and
  Posts for references to it. When found, the existing warning also lists those files
  (Cancel stays the default).

## Implementation

- `attachments.InlineImages` (pure Java): block detection (fence-aware), `resolve`, `altText`,
  `markdownLine`, `referenced`. `attachments.ImageReferences` scans README, entries and Posts for the
  Unpack warning and builds the Copy Link line.
- HTML: `DiaryExporter` replaces blocks in the trip description/Post intro and in each tale with a
  `<figure class="inline-image">` raw HTML block before commonmark. WordPress: a paragraph that is
  just one `attachments/` image becomes a `wp:image` block (AST based, so lists and quotes work too).
- Gallery overlap is done, per day: only the day's own tale counts (an image used in the README or
  a Post intro still shows in its day's gallery).
- Problems are collected per export (`lastInlineImageProblems()` on both exporters) and shown in the
  status line by the Diary Composer and the WordPress send. A missing file or missing public base URL
  skips the image; a foreign path or an image inside a sentence stays as written.
- Names with spaces or parentheses are written as `%20`, `%28`, `%29` in the link and decoded again.

## Open / not yet decided

- Rendered size and layout of an inline image (assumed: full column width, `max-width: 100%`).
- What WordPress does when `attachments.publicBaseUrl` is blank (assumed: same as the gallery).
- Whether the app should ever preview inline images in the editor (assumed: no).
