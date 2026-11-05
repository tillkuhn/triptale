# 41 Justified-row (Flickr/Google Photos-style) impressions gallery for HTML export

The HTML diary export's impressions gallery (`DiaryExporter.renderImpressionsTable`,
`table.impressions` in `html-shell.html`) was originally a fixed N-column `<table>`: each row's
height is forced to its tallest cell, so mixing portrait and landscape images (impressions are
usually 2:3 or 3:2, not a uniform ratio) left visible blank gaps below shorter images, and an odd
image count left a half-empty trailing row. This was fixed as a quick win by switching to a CSS
`column-count` masonry layout (images flow into whichever column is currently shortest, each kept
at its natural aspect ratio via `break-inside: avoid`) — no Java changes, gaps gone. Trade-off:
reading order goes top-to-bottom per column then jumps to the next column, so strict
left-to-right/chronological photo order within a day is lost.

Longer term, consider upgrading to a true "justified row" gallery (Flickr/Google Photos-style):
pack images into full-width rows where every image in a row is scaled to one shared row height,
so rows always come out flush with zero gaps *and* chronological left-to-right order is
preserved — this looks the most polished of the options discussed. It needs:

- A packing algorithm (Java, in `DiaryExporter` or a new helper) that greedily fills a row with
  images up to the target width (scaling each to a common row height from its aspect ratio), then
  starts a new row, with a tolerance for the last row of a day (it won't exactly fill the width).
- Real pixel dimensions per image to compute aspect ratio. `ExifReader`/`metadata-extractor`
  already reads JPEG dimensions via the SOF marker (`ExifReader.dimensions()`) for the impressions
  viewer, so that capability exists — but it currently returns a formatted `"W×H"` string for
  display, not raw ints suitable for layout math; would need a small refactor (or a new method) to
  expose width/height as numbers.
- Decide how to render the computed layout in static HTML with no JS: either inline `style="width:
  Npx; height: Npx"` per `<img>` computed server-side (simplest, but pixel-exact widths don't
  reflow if the viewer resizes the window/zooms), or `flex-basis`/`flex-grow` percentages per row
  (reflows better, more CSS work to get right).
- Non-JPEG impressions (HEIC, PNG, etc., if the configured `impressionsFilePattern` ever matches
  those) won't have a JPEG SOF marker — decide a fallback (skip dimension-based sizing and fall
  back to the masonry/table layout for that image, or read dimensions via `ImageIO` instead of the
  JPEG-specific path).

## Update 2026-11: findings, WordPress first

Seen in a real export: the masonry columns leave a ragged bottom (one column ends lower), and
the last image doesn't really fit. Counting odd/even doesn't help with `column-count`, the
problem is uneven column *heights*, not the image count.

Changed priorities since the text above:

- **WordPress is the real target**, the HTML export is mainly a preview, but it's still used for
  PDF (family diary mail).
- **Strict time order is not important** any more, so the packing may reorder images.

Consequences:

- CSS-only fixes (justified rows, `column-span: all`) don't carry over to WordPress: post content
  can't rely on its own CSS (the block editor flags changed gallery markup, inline styles may be
  stripped). We have to live with the core gallery block (`columns-2 is-cropped`): images in a row
  are cropped to one row height, and an odd last image is stretched over the full row (to be
  verified on the actual theme).
- So the lever is the **order**, computed in Java before both exporters:
  1. Orientation per image from the header plus EXIF orientation (5–8 swap width/height), ideally
     cached next to the EXIF cache.
  2. Pair landscape with landscape, portrait with portrait (little cropping); pairs keep rough
     time order.
  3. Odd landscape count: one landscape last, full width — preferably the day's map.
     Odd portrait count: one mixed pair, still end on a landscape. Only-portrait odd count: accept
     a full-width portrait (rare).
- HTML/PDF should then mirror the WordPress layout (flex rows, cropped, odd last image full
  width, same order) instead of masonry. Bonus for PDF: `break-inside: avoid` per row makes page
  breaks fall between rows; multi-column layouts split badly across pages.
- Uniform cropped grid (square/3:2 for all) and `grid-auto-flow: dense` with portrait spanning two
  rows were rejected: too much cropping of portraits, and 2:3 vs. two 3:2 rows don't add up
  (1.5 vs. 1.33 widths tall).

Not followed up for now, low priority.

## Update 2026-11: curator gallery preview and Put/Unpack

Decided together with a rework of the Impression Curator (`ui/dialog/ImageViewerDialog`):

- **Gallery preview.** A "▦ Gallery" toggle in the curator replaces the image with a schematic
  (plain rectangles, numbered, current image highlighted, click jumps to the image) of the day's
  *backpack* images laid out as the exports would, honouring "Faves only". Layout comes from
  `impressions.GalleryLayout` (pure Java): full rows of N same-orientation images, a landscape
  and a portrait remainder share one row if they fit, otherwise portrait rows then an odd
  landscape last and full width. Row height follows the mean aspect (cropped rows). The exporters
  do **not** use it yet; making WordPress/HTML consume the same plan is the remaining step, so the
  preview can't drift from the export.
- **Orientation.** `ExifInfo` carries the EXIF orientation; `displayAspect()` swaps width and
  height for 5-8. The exif cache entries got a `version` key (entries without it are re-read).
- **Put / Unpack instead of Select + Import.** The select checkbox, Select All and Import
  Selected are gone. `＋ Put into 🎒` (Space) copies the shown image, `－ Unpack from 🎒`
  (Cmd/Ctrl+Backspace) deletes the backpack copy. Two buttons at fixed places rather than one
  toggle, so a habitual click never flips between add and delete.
- **Safe unpack.** "In the backpack" is name + size, independent of the viewed source. Unpacking is
  silent when a copy outside the backpack exists (viewing another source, or the library / picked
  folder has the same name and size). Only when the backpack holds the only copy there is a
  warning (Cancel is the default). Deletion is permanent, no OS trash; the bucket copy goes at the
  next backpack sync (delete propagation, ADR-0002).
