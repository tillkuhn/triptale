# 07 — HTML preview for markdown export: feasibility & plan

## Goal (from TODO.md)

> Check feasibility of HTML Preview for markdown export. I understand that
> it's a considerable overhead to render markdown in the export, but would it
> be possible if we assume we only have very simple markdown to write a
> temporary HTML file and have it opened by the default system browser?
> Suggest efforts and create a plan if reasonable.

## Feasibility verdict

**Yes, low effort.** The exported markdown is intentionally simple
(headings, a stats list, distance/altitude/track lines, free-text prose) —
no images, no tables, no nested lists. Converting it to HTML and opening it
in the system browser is a small, additive change with no architectural
risk.

## Current state (as-is)

- `DiaryExporter.exportTrip(Trip)` (`export/DiaryExporter.java:41`) returns a
  single combined **markdown `String`** for the whole trip — in-memory only,
  nothing is written to disk.
- Templates live in `src/main/resources/export/*.md`, substituted via a
  hand-rolled `{{var}}` replace (`DiaryExporter.substitute`, line 142) — no
  real templating engine, no markdown parser anywhere in the project.
- UI trigger: `MainController.onExportDiary()` (`ui/MainController.java:346`)
  shows the markdown in a read-only monospace `TextArea` inside a `Dialog`,
  with **Copy** and **Close** buttons only. No file chooser, no rendering.
- `pom.xml` has no markdown parser (no commonmark/flexmark) and no
  `javafx-web` — `WebView` is not used anywhere in the codebase.
- Existing precedent for opening things externally: `HostServices`
  registered as a manual Spring singleton in `TripTaleApplication.init()`,
  injected into `MainController` via `ObjectProvider<HostServices>`
  (`MainController.java:114-126`), used today by `openInBrowser(String url)`
  (`MainController.java:880`) for the Track URL "open in browser" button.
  `hostServices.showDocument(url)` also accepts `file://` URIs, so it can
  open a local temp HTML file exactly the same way.

## Decisions (resolved)

| Question | Decision |
|---|---|
| Preview surface | System browser via `HostServices.showDocument(file://...)` — no in-app `WebView`, no new `javafx-web` dependency, reuses the existing pattern |
| Markdown → HTML | Add `org.commonmark:commonmark` (small, zero transitive deps) rather than a hand-rolled regex converter |
| UI integration | Add a **"Preview in Browser"** button to the existing Export Diary dialog, alongside Copy/Close — additive, not a replacement |
| HTML styling | Minimal inline CSS, light/print-friendly (serif/sans font, comfortable margins) — mirrors the "written tale" light-background preference from todo 05, not the app's dark theme |
| Temp file location | `Files.createTempFile("triptale-export-", ".html")` in the OS temp dir, `toFile().deleteOnExit()` — no manual cleanup needed for a preview file |

## Touch points

| File | Change |
|---|---|
| `pom.xml` | Add `org.commonmark:commonmark` dependency |
| `export/DiaryExporter.java` | New method `exportTripAsHtml(Trip trip)` (or a small companion class, see open question) that renders the existing markdown output through commonmark and wraps it in an HTML shell |
| `resources/export/html-shell.html` (new) | HTML document template with `<style>` block (inline CSS) and a `{{body}}` placeholder for the rendered markdown |
| `ui/MainController.java` | In `onExportDiary()` (line 346), add a "Preview in Browser" `ButtonType`; on click, write temp file + `openInBrowser(file.toUri().toString())` |
| `test/.../export/DiaryExporterTest.java` (new or extended) | Unit tests for markdown → HTML conversion (headings, bold/italic, paragraphs round-trip correctly) |

## Implementation sketch

1. **Dependency** — add to `pom.xml`:
   ```xml
   <dependency>
       <groupId>org.commonmark</groupId>
       <artifactId>commonmark</artifactId>
       <version>0.22.0</version> <!-- verify latest -->
   </dependency>
   ```

2. **`DiaryExporter`** — new method reusing the existing `exportTrip(trip)`
   markdown output as input:
   ```java
   public String exportTripAsHtml(Trip trip) {
       String markdown = exportTrip(trip);
       Node document = Parser.builder().build().parse(markdown);
       String bodyHtml = HtmlRenderer.builder().build().render(document);
       return substitute(load(HTML_SHELL), Map.of(
               "title", trip.name() == null ? "" : trip.name(),
               "body", bodyHtml));
   }
   ```
   `HTML_SHELL = "/export/html-shell.html"` constant alongside the existing
   template constants (lines 24-29).

3. **`html-shell.html`** template — minimal print-friendly styling:
   ```html
   <!DOCTYPE html>
   <html>
   <head>
       <meta charset="UTF-8">
       <title>{{title}}</title>
       <style>
           body { font-family: Georgia, 'Times New Roman', serif;
                  background: #faf6ee; color: #1a1a2e;
                  max-width: 800px; margin: 2rem auto; padding: 0 1rem;
                  line-height: 1.6; }
           h1, h2 { font-family: sans-serif; }
       </style>
   </head>
   <body>
   {{body}}
   </body>
   </html>
   ```

4. **`MainController.onExportDiary()`** — add a third button:
   ```java
   ButtonType previewType = new ButtonType("Preview in Browser", ButtonBar.ButtonData.OTHER);
   dlg.getDialogPane().getButtonTypes().setAll(copyType, previewType, ButtonType.CLOSE);

   Button previewBtn = (Button) dlg.getDialogPane().lookupButton(previewType);
   previewBtn.addEventFilter(ActionEvent.ACTION, ev -> {
       try {
           String html = diaryExporter.exportTripAsHtml(trip);
           Path tmp = Files.createTempFile("triptale-export-", ".html");
           Files.writeString(tmp, html, StandardCharsets.UTF_8);
           tmp.toFile().deleteOnExit();
           openInBrowser(tmp.toUri().toString());
       } catch (IOException ex) {
           error("Preview failed: " + ex.getMessage());
       }
       ev.consume();
   });
   ```
   Reuses the existing private `openInBrowser(String url)` (line 880) as-is —
   `HostServices.showDocument` accepts `file://` URIs.

5. **Tests** — parse a small fixed markdown sample through the same
   commonmark pipeline and assert the rendered HTML contains expected tags
   (`<h1>`, `<h2>`, `<p>`, `<strong>`/`<em>` if used in tales text). No need
   to test the full trip export end-to-end beyond the existing
   `DiaryExporterTest` (if any) — just the new HTML conversion path.

## Effort estimate

Small — single focused PR:
- 1 new dependency
- 1 new method + 1 new template file
- 1 new button + handler in `MainController`
- A couple of unit tests for the markdown → HTML conversion

## Open questions to resolve before implementing

1. **Class placement**: add `exportTripAsHtml` directly to `DiaryExporter`,
   or extract a small dedicated `HtmlExporter` component depending on it?
   (`DiaryExporter` is already the natural home; a split is only worth it if
   HTML export grows significantly more complex later.)
2. **Exact commonmark version** to pin in `pom.xml` (check latest stable at
   implementation time).
3. **Title casing/escaping**: trip name is inserted into `<title>` and an
   `<h1>` (via the markdown `# {{tripName}}` heading) — commonmark handles
   HTML-escaping the body, but the `{{title}}` substitution in the shell
   template is a raw `.replace()`, so a trip name containing `<`/`&` would
   need manual escaping if ever a concern (unlikely given trip names are
   free text but short).
4. **Button label wording**: "Preview in Browser" vs "Open as HTML" vs
   "HTML Preview" — cosmetic, pick at implementation time.
