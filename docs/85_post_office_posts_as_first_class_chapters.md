# 85 Post Office: posts as first-class chapters

# 85 Post Office: posts as first-class chapters

## Problem
Updating an existing WordPress post means going through the exporter + date range + a second
dialog (todo 62), and there is no overview of a trip's posts. Posts (todo 60) should be chapters
that hold a date range together with their own intro, independent of WordPress.

## Decisions (from grill-me)
- **Range model:** a Post is a contiguous range `from`/`to` (frontmatter keys, alphabetical). Skipped-day
  exceptions ("3-10 except 4,5") are a later extension, not in v1.
- **Overlaps/gaps:** anything goes, no validation.
- **Migration:** one-time ad hoc script from `has:` wikilinks to from/to (min..max); app reads only the new format.
- **Post Office dialog** (`ui/dialog/`, menu "Trip > 📮 Post Office"): single dialog. Top: table (start day,
  end day, title, WP status; ~3 rows typical). Row closest to the current entry (first post whose range
  contains its date) is preselected/highlighted. Below: editor for title, intro, from, to. Actions: Add, Delete
  (warns: WordPress post is NOT cleaned up), Edit via row selection. Confirms with `Dialogs.addSaveDayButtons`-style contract: returns data, controller persists.
- **No WordPress send in the Post Office.** Local content only (title, intro, range).
- **Export dialog:** `[post selector][from][to]` replaces `[from][to][all days]`. Selector values: *Ad Hoc Post*
  (default when no post matches the current entry; from/to editable), *Entire Trip* (replaces "all days"; read-only
  full range), then each real Post (default when its range contains the current entry; from/to read-only).
  Ad Hoc / Entire Trip are never persisted. A small 📮 button next to the selector jumps into the Post Office on that post.
- **All exporters** (Markdown, HTML, WordPress) prefix a selected real Post's title + intro as chapter heading/lead.
- **WordPress export** requires a real Post; with Ad Hoc / Entire Trip the button is disabled with a hint
  "create a post in the Post Office first". The 2nd publish step (create-new/edit title+intro in `WordPressPublishDialog`) is removed;
  it goes straight to the send/confirm path using the Post's wp_id/wp_modified.

## Relevant code
`domain.Post`, `storage.MarkdownStore` (listPosts/loadPost/savePost, `has:` handling), `wordpress.PostPicker`
(preselect/days/uniqueSlug, likely simplified), `ui.dialog.ExportDiaryDialog`, `ui.dialog.WordPressPublishDialog`,
`export.DiaryExporter`, `export.WordPressExporter`, `MainController` menu. Earlier docs: 60 and 62.

## Resolved (second round)
- **Ad Hoc from/to** list only days that have an entry; an invalid combination (to < from) auto-corrects the
  other selector. A trip without entries disables export.
- **Post from/to are stored as plain dates**, not required to match entry files (trip in progress, deleted entry).
  Days without an entry are skipped on export; a post whose range has zero entries gets a warning mark in the table.
- **`PostPicker.daysChange` and the "range wins on send" logic are removed**: the range comes from the Post and
  is only editable in the Post Office. `preselect` becomes "first post whose range contains the date".
