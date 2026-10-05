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
