# 60 Send the WordPress export as a draft via the API

Today the WordPress export (todo 44) is copy & paste into the block editor's Code editor.
This todo sends it straight to the blog as a draft, and updates that same post on later
exports instead of creating a new one each time. Split out of todo 50 ("optional stage 2").

## What is known

- Verified 2026-10-10: app "TripTale" (id 150174) at developer.wordpress.com, OAuth2 token,
  `POST https://public-api.wordpress.com/wp/v2/sites/timafe.wordpress.com/posts` with
  `status=draft` creates a draft. See todo 50 / `docs/44_wordpress_export.md` (option 3).
- Images need no upload: the export already hotlinks the CDN copies of `attachments/`, so the
  post content is exactly the markup `WordPressExporter` produces today.

## Decision: a `Post` note type groups the days (2026-10-13)

The first draft stored the WordPress post ID on every entry (`wp_post`). Rejected: a post
usually covers a range of days (rarely one day, rarely the whole trip), so the ID would be
copied into several files, adding/removing a day would rewrite entries, and ranges spanning
two posts needed a "mixed IDs" resolution.

Instead a new Tolaria note type **Post** lives in the trip folder and links its days:

```markdown
---
belongs_to: "[[2025/TestTwentyFive/README]]"
has:
  - "[[2025/TestTwentyFive/2025-09-15-Monday]]"
  - "[[2025/TestTwentyFive/2025-09-16-Tuesday]]"
type: Post
wp_id: 12345677
wp_modified: 2026-10-13T14:02:11Z
---
# 2 Random Days Post

This was a nice short weekend trip
```

- **Filename:** slug of the title (`Slugs.toSlug()`), fixed once like trip slugs. It doesn't
  start with a date, so `MarkdownStore.listEntryDates` already skips it.
- **Title / intro:** the `# Heading` is the post title (WordPress title, export heading); the
  body after it is the intro, rendered before the days. Runs through `Markdown.stripPrivate`
  like tales.
- **`has`:** vault-relative wikilinks to entries (`<year>/<slug>/<entry file stem>`), stable
  because trip slugs are immutable and entry filenames derive from the date. On export the
  days are **sorted by date**, not list order; a link to a missing file is skipped with a
  warning. Gaps are allowed (it's a list, not a range).
- **`belongs_to`:** the trip README, same as entries.
- **Type definition:** `post.md` at the data-dir root (`type: Type`), created by
  `GitService.initRepo()` alongside `trip.md`/`tale.md`/`type.md`.
- Entries stay untouched; a day can be in several posts.

### Scope of the first step

- **The app only reads Posts** (lists them per trip by `type: Post`, exports them) and writes
  back only `wp_id`/`wp_modified` (saved → `addPending`, committed via Smart Sync). Posts
  are created and edited in Tolaria or by hand. Creating/editing in the app ("Save as Post"
  from a range, editing title/intro/days) is a follow-up todo.
- **A Post is a general export source**, not WordPress-only: the Export Diary dialog offers
  "Post: …" next to the existing date range, for Markdown/HTML/WordPress alike.

## Pieces

1. **Auth (OAuth2, once).**
   - Preferred: authorization code flow. The app opens the WordPress.com authorize page in the
     browser; the redirect lands on a short-lived loopback listener (`http://127.0.0.1:<port>/…`);
     the app exchanges the code for a token. No password stored, only the token in `settings.yml`
     (like the S3 secret).
   - Fallback: password grant (client secret + username + application password). Simpler, but
     `settings.yml` holds a password.
   - **Verify first:** does WordPress.com accept a localhost redirect URI for the app? How long
     do its tokens live (believed not to expire unless revoked, not confirmed)?
2. **Storage:** `domain.Post` (title, intro, entry dates, `wpId`, `wpModified`), loaded/listed
   by `MarkdownStore`; frontmatter keys in alphabetical order; `wp_id` accepted quoted or bare.
3. **Client:** new `wordpress` package (no JavaFX), `java.net.http` like `sync.S3Client`:
   create post, update post (`POST …/posts/<id>`), get post (`status`, `modified_gmt`, `link`).
4. **Dialog:** Export Diary gets a Post source; with a Post selected, "Send to WordPress" next
   to Copy. Afterwards it shows a link to open the post in the editor. Copy stays as the
   offline/no-token fallback.
5. **Settings:** site, client ID/secret, token, in ⚙ Edit Settings next to the S3 settings.

## Send rules

Frontmatter after a send: `wp_id` (post ID) and `wp_modified` (the `modified_gmt` WordPress
returned for our send). No `wp_link`/`wp_status` — they'd go stale and the pre-update GET
returns them anyway.

- **No `wp_id`** → create with `status=draft`, write `wp_id` + `wp_modified`.
- **`wp_id` set** → GET the post first:
  - 404 / trashed → offer to create a new draft (replaces `wp_id`);
  - `modified_gmt` newer than `wp_modified` (edited in WordPress: images, wording) → warn,
    Overwrite / Create new draft / Cancel;
  - published → confirm before overwriting (goes live immediately);
  - then update **without sending `status`** — WordPress keeps draft/scheduled/published.
    Publishing always happens in WordPress, never from TripTale.

## Open questions

- Auth verification (localhost redirect, token lifetime), see Pieces 1.
