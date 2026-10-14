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

1. **Auth (OAuth2, once). ✅ Done**, see Progress below.
2. **Storage:** `domain.Post` (title, intro, entry dates, `wpId`, `wpModified`), loaded/listed
   by `MarkdownStore`; frontmatter keys in alphabetical order; `wp_id` accepted quoted or bare.
3. **Client:** new `wordpress` package (no JavaFX), `java.net.http` like `sync.S3Client`:
   create post, update post (`POST …/posts/<id>`), get post (`status`, `modified_gmt`, `link`).
   (The package already exists with the OAuth2 client — this is the REST-call part.)
4. **Dialog:** Export Diary gets a Post source; with a Post selected, "Send to WordPress" next
   to Copy. Afterwards it shows a link to open the post in the editor. Copy stays as the
   offline/no-token fallback.
5. **Settings: ✅ Done**, see Progress below.

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

## Progress

- **Settings piece done (2026-10-14):** `AppSettings.WordPress` (site, clientId, clientSecret,
  accessToken) added alongside `Attachments`, with a `validationError()` that only complains
  about a *partially* filled site/clientId/clientSecret (accessToken is excluded — it's written
  by the OAuth2 flow, not typed in, so blank is the normal "not connected yet" state). Wired into
  ⚙ Edit Settings as a new "WordPress" tab (site, client ID, client secret fields + a read-only
  "Connected"/"Not connected" status label); OK is blocked like the Backpack tab on an invalid
  combination.
- **OAuth2 flow done and verified live (2026-10-14):** new `wordpress` package (no JavaFX,
  `java.net.http` + JDK's built-in `com.sun.net.httpserver.HttpServer`, same style as
  `sync.S3Client`): `WordPressAuthClient.connect(settings, openUrl, timeout)` runs the full
  Authorization Code Flow — starts the loopback listener, hands the authorize URL to a
  `Consumer<String>` (the caller opens it via `ui.BrowserLauncher`), blocks for the redirect
  (with CSRF `state` check), exchanges the code for a token. `WordPressException` is the
  unchecked failure type (denied/timeout/HTTP error/state mismatch all map to it, with the
  specific cause message). `EditSettingsDialog`'s WordPress tab got a "🔗 Connect to WordPress"
  button that runs this on a daemon thread (`javafx.concurrent.Task`, same pattern as
  `ConnectivityService`) and updates the status label on success/failure; the obtained
  `accessToken` is written into `settings.yml` on the dialog's own OK (stored as plain text,
  same as the S3 secret).
- **Live end-to-end test (2026-10-14):** registered the WordPress.com app (id 150174, type
  "Native") with Redirect URL `http://127.0.0.1:8765/callback` (exact match required — the
  loopback port is hard-coded in `WordPressAuthClient` for this reason). Clicking Connect opened
  the browser, user approved, callback landed, token exchange succeeded, `accessToken` appeared
  in `settings.yml`. This resolves both open questions below.
- **Pieces 2–4 done (2026-10-14):** `domain.Post` (slug, title, intro, entryDates, wpId,
  wpModified); lives at `<trip>/posts/<slug>.md` (a dedicated subfolder — simpler than filtering
  entry-dir listings by `type:`, and keeps every file in that folder unambiguously a Post).
  `MarkdownStore.listPosts/loadPost/savePost` resolve `has:` wikilinks back to dates, dropping a
  link to a missing entry file with a warning (never throws). `DiaryExporter`/`WordPressExporter`
  generalized to take an explicit `List<LocalDate>` internally, with `exportPost`/`exportPostAsHtml`
  added alongside the existing range-based methods (public signatures unchanged, all existing
  tests pass unmodified). New `wordpress.WordPressApiClient` (`createDraft`/`updatePost`/`getPost`,
  `java.net.http`, same style as `S3Client`) implements the send rules from this doc.
  `ExportDiaryDialog` gained a "Source: 📅 Date range / 📰 <Post title>" combo and a
  "📡 Send to WordPress" button, enabled when a Post + WordPress format are selected; the actual
  send runs synchronously on the FX thread (acceptable for a one-off user action; a background
  `Task` like the OAuth connect button would be a nice follow-up, not done here). `GitService`
  gained `updateTypeDefinitions()` (force-overwrite trip.md/tale.md/type.md/post.md, unlike
  `initRepo`'s create-if-missing) plus a "🧩 Update Type Definitions…" Repository-menu action with
  a confirm dialog, since `post.md` didn't exist in data dirs set up before this todo.
- **Scope bug found and fixed in live testing (2026-10-14):** the first real send hit
  `HTTP 403 {"code":"unauthorized","message":"That API call is not allowed for this account.
  Required scope: \`global\`. Granted scope(s): \`posts\`."}`. The `wp/v2` REST namespace (used
  here) needs the broader `global` OAuth scope, unlike the older `v1.1` API which accepts a
  narrower `posts` scope. Fixed in `WordPressAuthClient.SCOPE`; anyone who connected before this
  fix needs to reconnect (Edit Settings → WordPress → Connect again) to get a token with the
  right scope — no in-app detection/prompt for this yet, it just fails with the same 403 again.

## Open questions

- ~~Auth verification~~ — **resolved 2026-10-14**, see Progress above: WordPress.com's "Native"
  app type accepts a `http://127.0.0.1:<port>/<path>` redirect URI (exact string match, as the
  docs warn) and the Authorization Code Flow works end-to-end from a desktop app with a loopback
  listener. Access token lifetime for this flow still isn't documented anywhere found — only the
  Implicit Flow's 2-week expiry is documented — so Piece 3 (the API client) should still treat a
  401 as "token expired/revoked, reconnect" rather than assume indefinite validity.
- A full live send (create + update + conflict path) with the corrected `global` scope is still
  pending as of this writing — the 403 above happened before the scope fix; next step is to
  reconnect and retry.
