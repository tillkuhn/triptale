# 44 Export trip as WordPress.com post

Goal: get a trip (tales + impressions) into a wordpress.com post with minimal manual work.
Full automation is not required; the pain point is the wordpress.com rich-text editor.

## Status quo

- `DiaryExporter` renders Markdown (`exportTrip`) and standalone HTML (`exportTripAsHtml`).
- The HTML impressions grid points at local `file://` URIs (`renderImpressionsTable`), which
  are useless outside the local machine — images are the actual problem.
- Attachments are already synced to S3 (`attachments.AttachmentSyncer`, `config.BucketUrl`),
  so a public `https://` URL per image is within reach if the bucket/prefix is readable
  (or fronted by a CDN).

## Options

### 1. Paste into the block editor (no code)

- The block editor converts pasted **Markdown** into blocks (headings, lists, bold, links).
  The existing Markdown export can be pasted as-is; image grids are lost.
- Pasted **HTML** is converted as well; `<img src="https://…">` becomes an Image block,
  but only if the URL is publicly reachable.
- WordPress.com (Jetpack) also offers a **Markdown block** for keeping text as Markdown.

### 2. Gutenberg block markup export (recommended first step)

New export target that emits serialized block markup, copied to the clipboard and pasted
into the editor's **Code editor** (⋮ menu → Code editor, or Cmd + Opt + Shift + M):

```html
<!-- wp:heading -->
<h2 class="wp-block-heading">Day 3 · From → To</h2>
<!-- /wp:heading -->

<!-- wp:paragraph -->
<p>…tale text…</p>
<!-- /wp:paragraph -->

<!-- wp:gallery {"columns":3,"linkTo":"none"} -->
<figure class="wp-block-gallery has-nested-images columns-3">
<!-- wp:image -->
<figure class="wp-block-image"><img src="https://…/img1.jpg" alt=""/></figure>
<!-- /wp:image -->
</figure>
<!-- /wp:gallery -->
```

- Reuse `DiaryExporter.buildMarkdown` / commonmark for the text, the impressions resolver for
  the per-day images (FAVES/ALL mode as in the HTML export), gallery columns from
  `impressionsGridColumns`.
- Image URL mapping: local impressions path → public attachment URL. Needs a configurable
  public base URL (setting) or a derivation from the bucket URL; images not present in the
  bucket must be reported (status line), not silently dropped.
- WordPress hotlinks those images; the Image block's "Upload to Media Library" toolbar action
  moves individual images into WordPress media when wanted.
- Pure string generation → unit-testable with `@TempDir` like the other exporters.
- Drop `DiaryEntry.DEFAULT_ROUTE` from headings as in the existing export.

### 3. WordPress.com REST API — create a draft (full automation)

- Endpoints: `https://public-api.wordpress.com/wp/v2/sites/<site>/media` and `.../posts`
  (`status: draft`). Upload images first, then reference the returned media IDs/URLs in the
  gallery blocks from option 2 (same generator, different URL source).
- Auth is the hard part: a desktop app needs OAuth2 (app registered at
  developer.wordpress.com, redirect handling) — check whether application passwords work for
  our plan/site before building.
- Resize images before upload (phone originals are 5–10 MB).
- Fits the `java.net.http` style of `S3Client`; would be a new `ui/dialog/` dialog
  ("Publish draft to WordPress…") with credentials in `settings.yml`.

### 4. External publishing editor

MarsEdit, Ulysses, iA Writer publish Markdown to WordPress.com and upload local images
themselves. TripTale would only need a Markdown export with `![](/local/path.jpg)` image
links (the current Markdown export has none). Cheap, but adds a (possibly paid) tool.

## Open questions

1. Can attachment URLs be publicly readable (bucket policy / CDN)?
2. Which WordPress.com plan? (Plugin-capable plans may simplify API auth.)
3. Post granularity: one post per trip, or one per day/entry?

## Plan

1. Implement option 2 (block markup → clipboard), behind a new export menu item.
2. Revisit option 3 if posts become frequent; reuse the option 2 generator.
