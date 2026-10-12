# 48 Private sections: hide internal notes from exports

A tale can end with a private section, e.g. the original distance/route notes or anything
else that should stay in the diary but never reach a blog, PDF or clipboard export.

```markdown
Rode along the coast, headwind all day ...

## Internal

Komoot said 87 km, bike computer 92 km. Hotel paid in cash, 45 €.
```

## Marker

A heading is private if its text **starts with one of the keywords** `internal`, `private`,
`intern`, `privat` (case-insensitive), **followed by the end of the heading, whitespace or
`:`**. Any ATX heading level (`#` … `######`) counts; setext headings (`===`/`---` underlines)
are not recognized.

| Heading                                                  | Private? |
|----------------------------------------------------------|----------|
| `## Internal`, `### privat:`, `## Private: costs`         | yes      |
| `## Internal notes`, `## Intern: Strecke`                 | yes      |
| `## Private beach at Kalamaki`                            | yes      |
| `## Private-Strand`, `## Internals`, `## Privatstrand`    | no       |
| `## Private!` (other punctuation right after the keyword) | no       |

The German keywords are a safety net: a missed marker leaks private notes, a false positive
only leaves a gap in an export. Headings are rare in tales, so the broad "starts with" rule
was preferred over an exact-word rule (which would have let `## Internal notes` through).

## Block

The marker heading and everything after it, up to the next heading of the **same or higher**
level (subheadings stay inside the block), or to the end of the tale. There is no closing
marker — the intended use is a single private section at the end of the tale, with no public
text after it.

Lines inside fenced code blocks (```` ``` ```` / `~~~`) are never treated as headings, neither
as markers nor as block ends.

## Where it applies

Stripping lives in a new `util.Markdown.stripPrivate(String)` and is called **before**
`Markdown.shiftHeadings` in both exporters:

- `export.DiaryExporter` (Markdown, HTML preview, ⇧ Export Tale)
- `export.WordPressExporter` — has its own tale path and does not go through `DiaryExporter`.
  Its `tales().isBlank()` check must run on the stripped text.

A tale that is blank after stripping is treated as an empty tale (no tales block); the day's
heading, stats and links are still exported.

Unchanged — private sections stay visible/included in: the editor, the word count, 👁 View
Source, the entry files on disk, git (incl. the remote) and the Tolaria vault. "Private" means
"not exported", not "not stored".

## 📋 Copy Tale removed

The Tale Entries menu item "📋 Copy Tale" (`copyMenuItem`, `onCopyTale`) copied title + raw
tale and would have been the one remaining leak path. It is removed instead of patched:

- clean copy: ⇧ Export Tale → Copy (full entry: heading, stats, tale, links; private stripped)
- raw copy: Cmd + A, Cmd + C in the editor (includes private sections)

## 🔒 Add Private Section

New Tale Entries menu item, in the slot Copy Tale leaves free (after 💾 Save Tale, before
⇧ Export Tale). No accelerator. Makes the feature discoverable without reading the docs.

- Appends `## Internal` and the placeholder `Private notes, not exported.` at the end of the
  tale, separated by one blank line (no leading blank line if the tale is empty).
- Focuses the editor and selects the placeholder, so typing replaces it.
- If the tale already has a private section, nothing is added: the caret moves to the existing
  marker heading and the status line says "Tale already has a private section".
- A plain text edit: the entry gets dirty and is saved/committed through the normal
  Save → `addPending(...)` → Smart Sync flow.
- Enabled whenever the editor is editable (same condition as 💾 Save Tale).

## Tests

`MarkdownTest` (pure unit tests):

- marker matching: every row of the table above, case-insensitivity, all heading levels
- block end: next heading of same/higher level ends it, deeper subheadings stay inside
- private section at the end of the tale; private section followed by a public heading
- `#` lines in fenced code blocks (inside and outside a private block)
- a tale that is entirely private → blank

Exporter tests: a private section is absent from `DiaryExporter` and `WordPressExporter` output;
an all-private tale produces no tales block.
