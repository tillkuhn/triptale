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
- Image URL mapping: local impressions path → public attachment URL, via a new
  `publicAttachmentBaseUrl` setting (see **Image hosting** below) — keeping it a plain
  configured prefix makes every hosting variant the same code. Images not present at the
  target must be reported (status line), not silently dropped.
- Note the images in the HTML export come from `impressionsFilePattern` (the local photo
  folder resolved by `ImpressionsResolver`), **not** from `attachments/` — so whatever hosts
  the post images has to be fed from that folder, or the two sources have to be reconciled
  first. Open point before implementation.
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

## Image hosting (decided 2026-10-08, to be trialled)

The attachments bucket is private and stays private (`aws_s3_bucket_public_access_block`,
all four flags true). WordPress does not care which host serves the images — `<img src>` is
fetched by the reader's browser — but it must be **public HTTPS with a browser-trusted
certificate** (the blog is HTTPS, so `http://` images are mixed content and get blocked), with
no auth, no `Referer`-based hotlink protection, and a correct `Content-Type` (`image/jpeg`,
not `application/octet-stream`).

Variants considered:

| Variant | Cost | Notes |
| --- | --- | --- |
| **CloudFront + OAC over the private bucket** | €0 in practice | Always-free tier covers 1 TB egress / 10M requests a month. Free auto-generated hostname (`d….cloudfront.net`) **with** a managed TLS cert — no domain purchase needed; a custom `img.…` CNAME is optional (ACM cert free). Bucket stays fully private. |
| Public-read on one bucket prefix | ~€0 | Bucket policy granting `s3:GetObject` on `…/public/*` only; requires relaxing the public access block. No CDN, exposes the bucket name. |
| Own webserver + `rclone sync` | €0 | Stable self-owned URLs, no AWS egress. **A git clone is not enough** — `attachments/` is gitignored outside `git` sync mode, and post images come from `impressionsFilePattern` anyway. |
| WordPress media library | €0 (6 GB on the Personal plan) | Drag the day's photos into a Gallery block after pasting the text; no infrastructure, but manual per post. |

**Decision: try CloudFront + OAC first**, provisioned in the existing `terraform/` OpenTofu
setup (`make plan` / `make apply` there) alongside the bucket and the app IAM user. Reasons:
the infra is already code, the free tier should make it cost nothing, and it is the variant
that keeps the bucket private. Watch the actual bill for the first weeks; if it misbehaves or
costs real money, `tofu destroy` just that distribution and fall back to the own-webserver
variant — nothing in the app changes, since the exporter only ever sees
`publicAttachmentBaseUrl`.

Terraform sketch (new file in `terraform/`, guarded by a `enable_cdn` variable so it can be
turned off without deleting code):

- `aws_cloudfront_origin_access_control` (signing `sigv4`, always sign)
- `aws_cloudfront_distribution` with the bucket's regional domain as origin, `PriceClass_100`
  (Europe/North America — cheapest), default cache behaviour GET/HEAD only,
  `viewer_protocol_policy = "redirect-to-https"`, the managed `CachingOptimized` policy
- `aws_s3_bucket_policy` allowing `s3:GetObject` to `cloudfront.amazonaws.com` restricted by
  `AWS:SourceArn` = the distribution ARN (works with the public access block left on)
- output `cdn_domain_name`, to be pasted into `publicAttachmentBaseUrl` in `settings.yml`

**Provisioned 2026-10-10** (`terraform/cdn.tf`, `enable_cdn = true`), with two tightenings over
the sketch above:

- **Origin path `/attachments`**: the CDN root maps to `attachments/` in the bucket, so other
  top-level keys (`radio/`, future backups) can't be reached through the CDN at all. URLs are
  `https://<cdn>/<year>/<slug>/<day>/<file>.jpg`.
- **Images only**: the bucket policy grants `s3:GetObject` on `attachments/*.{jpg,jpeg,png,webp}`
  (lower and upper case) only, so GPX and PDF files answer 403.

To expose another path later, add a second origin (same bucket, other or no origin path) plus
an ordered cache behaviour such as `/radio/*`, and extend the bucket policy. Existing image URLs
and their cache entries stay valid. Don't drop the origin path instead: that would add
`attachments/` to every image URL and break links in published posts.

Verified with curl right after deployment: image 200 `image/jpeg` (second request
`Hit from cloudfront`), GPX 403, missing key 403, `http://` 301 to `https://`, a probe object at
the bucket root unreachable (`/../` and `%2e%2e` give 400, direct 403), direct S3 URL 403.

### Scope of the experiment

The question to answer here is narrow: **can CloudFront serve the bucket's *current* content
publicly over HTTPS, well enough to hand-craft a WordPress post against those URLs?** If yes,
hosting is solved and the rest is app work. If not — cost surprises, OAC friction, broken
content types — destroy it and evaluate the own-webserver variant instead.

How images get into the bucket in the first place is explicitly **out of scope**: that is an
app feature, tracked as todo 47 (`docs/47_impressions_attachments_refactor.md`). Validate the
CDN with whatever is in the bucket today; a handcrafted post is a perfectly good test subject.

## Open questions

1. Post granularity: one post per trip, or one per day/entry?
2. Which WordPress.com plan? (Affects API auth for option 3; media storage for the fallback.)

Resolved: post images are the synced `attachments/`, not the local impressions folder — the
pipeline that gets them there is todo 47.

## Plan

1. **Now:** provision the CloudFront distribution in `terraform/` behind `enable_cdn`, verify
   that an object already in the bucket loads over `https://d….cloudfront.net/…` in a browser,
   hand-craft one WordPress post against those URLs, and watch Cost Explorer for a few weeks.
   Decision point: keep it, or destroy it and use the own webserver.
2. Implement option 2 (block markup → clipboard) behind a new export menu item, with
   `publicAttachmentBaseUrl` in `settings.yml`. Depends on todo 47 for the images.
3. Revisit option 3 if posts become frequent; reuse the option 2 generator.
