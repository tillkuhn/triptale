# 60 Send the WordPress export as a draft via the API

Today the WordPress export (todo 44) is copy & paste into the block editor's Code editor.
This todo sends it straight to the blog as a draft, and updates that same draft on later
exports instead of creating a new one each time. Split out of todo 50 ("optional stage 2").

## What is known

- Verified 2026-10-10: app "TripTale" (id 150174) at developer.wordpress.com, OAuth2 token,
  `POST https://public-api.wordpress.com/wp/v2/sites/timafe.wordpress.com/posts` with
  `status=draft` creates a draft. See todo 50 / `docs/44_wordpress_export.md` (option 3).
- Images need no upload: the export already hotlinks the CDN copies of `attachments/`, so the
  post content is exactly the markup `WordPressExporter` produces today.

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
2. **Client:** new `wordpress` package (no JavaFX), `java.net.http` like `sync.S3Client`:
   create post, update post (`POST …/posts/<id>`), get post (status, `modified_gmt`, `link`).
3. **Dialog:** "Send to WordPress" next to Copy in Export Diary, with a title field (pre-filled
   from an existing post's title). Afterwards it shows a link to open the draft in the editor.
4. **Settings:** site, client ID/secret, token, in ⚙ Edit Settings next to the S3 settings.

## Tracking the draft (no new draft per export)

`.state.yml` is the wrong place (local, gitignored). Posts usually cover a range of days
(todo 44 decision), so the post ID lives **on each entry**, e.g. frontmatter `wp_post: 12345`
(alphabetical order, committed via Smart Sync like any save → `addPending`).

On export of a range, collect the IDs of its days:

- none → create a draft, write its ID into every day of the range;
- all the same → update that post;
- mixed (range changed, or spans two posts) → ask: update one of them or create a new one.

Before an update, GET the post:

- 404 / trashed → offer to create a new draft;
- already published → confirm before overwriting;
- edited in WordPress since our last send (images added in the editor, wording fixes) → an
  update would wipe that, so warn. Needs the post's `modified_gmt` from our last send stored
  too (second key, or a combined value — to decide).

## Open questions

- Key names and shape (`wp_post` alone vs. plus a last-sent timestamp).
- Should the post title come from somewhere persistent (first/last day titles, trip name)?
- Keep Copy as the fallback (offline, no token) — yes, presumably.
