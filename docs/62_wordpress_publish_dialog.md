# 62 WordPress publish dialog

Follow-up to todo 60 ([send a Post as a draft](60_wordpress_api_draft.md)). Todo 60's first
cut put a "Source: 📅 Date range / 📰 <Post>" combo into the Diary Composer: picking a Post
replaced the From/To range, and "📡 Send to WordPress" only worked with a Post selected. That
worked, but it was clumsy: two ways to choose days, Posts only creatable by hand, network calls
blocking the FX thread, results only in the status line.

## Decisions (grill session 2026-10-15)

1. **The Composer has no Source any more.** It always shows a date range (From/To/All days)
   and the format (Markdown / WordPress). Consequence: Markdown/HTML export of a Post (with
   gaps) is gone; a Post is a WordPress-only concept now. `DiaryExporter.exportPost` /
   `exportPostAsHtml` were removed with it.
2. **"📡 Send to WordPress"** is enabled with WordPress format. Without an access token it
   shows "Connect WordPress in ⚙ Edit Settings first" instead of opening the dialog (planned
   was disabled + tooltip, but JavaFX 23 shows no tooltips on disabled controls). It opens
   `ui/dialog/WordPressPublishDialog` for the Composer's range and its Images/Faves choice.
3. **Post dropdown** in that dialog: the trip's Posts plus "-- create new post --" (the only
   item when the trip has none). Label: `Day 1–2: Nice weekend · wp 1234432` or `· not sent`;
   gappy Posts from before this todo show their runs, e.g. `Day 1–2, 4`.
4. **Preselection:** a Post whose days equal the range; else the one sharing the most days
   (ties: first in the list, which is sorted by title); no overlap → create new.
5. **The Composer's range wins.** Sending rewrites the Post's `has:` list to the range's entry
   days; when that changes them, the dialog warns (`Days change: Day 1–2 → Day 1–3`).
6. **Title + Intro are editable** in the dialog, prefilled from the Post; for a new Post the
   title is `<trip name>: Day 1–2` and the intro empty. A title edit renames the post on
   WordPress too; the file slug never changes (immutable, like trip slugs).
7. **Drafts only, as in todo 60.** Create sends `status=draft`, update never sends `status`.
   The button says "Send", not "Publish". The send-rule prompts stay (404/trashed → offer new
   draft; edited on WordPress since last send → Overwrite / Create New Draft / Cancel;
   published → confirm), shown as alerts on top of the dialog.
8. **One write, after a successful send.** Only then is the Post file saved (title, intro,
   days, `wp_id`, `wp_modified`) and `addPending(...)`'d for Smart Sync. On failure nothing is
   written; the dialog keeps the edits so Send can be retried.
9. **The dialog stays open** and shows the result: `✅ Created draft 1234432` /
   `✅ Updated 1234432` with an "Open in WordPress" editor link
   (`https://wordpress.com/post/<site>/<id>`), or `❌ <error>`. The dropdown is refreshed so
   the label shows the new `wp_id`. Close closes it.
10. **Network calls run in background `Task`s** (GET for the conflict check, then
    create/update); the buttons are disabled while one runs, the result line says "Sending…".
11. **New Post slugs** come from the title via `MarkdownStore.postSlug` (`Slugs.toSlug`,
    camelCase like trip slugs); a slug already taken in the trip gets `-2`, `-3`, ….

## Files

- `wordpress/PostPicker` — JavaFX-free: preselection, labels, day runs, default title,
  unique slug, editor URL. Unit-tested in `PostPickerTest`.
- `ui/dialog/WordPressPublishDialog` — the dialog; owns its worker threads like
  `SmartSyncDialog`.
- `ui/dialog/ExportDiaryDialog` — Source combo removed; Send opens the publish dialog.
- `export/WordPressExporter.exportPost` — renders a Post (intro + days, no title heading:
  WordPress shows the title from its own field). `wordpress-preview.html` shows the title as h1.

## Left for later

- Deleting a Post from the app (still by hand / in Tolaria).
- Slug style: `postSlug` gives camelCase (`WeekendTrip`); the one hand-made example file from
  todo 60 was kebab-case. Left as is, existing files keep their names.
