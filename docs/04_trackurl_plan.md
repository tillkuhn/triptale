# 04 — Track URL field: implementation plan

## Goal (from TODO.md)

> Show a track-url input with label **"Track URL"** to be stored in the YAML
> frontmatter. This should be a URL to an external tracking tool. If filled,
> there should be an icon behind it to open the URL in the system's browser.
> It should be on the same line as kilometers and altitude.

The Track URL is an **opaque string** stored in entry frontmatter, handled
exactly like the existing `route` field (null/blank-guarded, no parsing).

## Touch points

| File | Change |
|---|---|
| `domain/DiaryEntry.java` | Add `trackUrl` record component, builder field/setter, thread through `build()` |
| `storage/MarkdownStore.java` | Write `trackUrl` frontmatter key; read it back in `parseEntry` |
| `ui/MainController.java` | New `@FXML trackUrlField`, baseline, dirty listener, load/save, `onOpenTrackUrl()` |
| `resources/fxml/main.fxml` | Add Label + TextField + open-icon Button to the metrics GridPane row 0 |
| `export/DiaryExporter.java` | New `ENTRY_TRACK` constant + render block (optional, see open question) |
| `resources/export/entry-track.md` | New template `Track: {{trackUrl}}` (optional) |
| `resources/fxml/triptale.css` | Optional style for the open-link button |
| `test/.../MarkdownStoreTest.java` | Extend round-trip test for `trackUrl` |

## 1. Domain — `DiaryEntry` (`domain/DiaryEntry.java`)

- Add `String trackUrl` to the record components (lines 5–11).
- Add a `trackUrl` field + setter to the `Builder` (lines 17–33). Follow the
  `route` pattern, but coalesce blank → `null` (so blank URLs are not persisted),
  rather than the `DEFAULT_ROUTE` sentinel pattern.
- Pass `trackUrl` into the canonical constructor in `build()`.

## 2. Storage — `MarkdownStore`

- **Write** (`saveEntry`, frontmatter map lines 129–133): after the `route` line
  add, guarded by null/blank:
  ```java
  if (entry.trackUrl() != null && !entry.trackUrl().isBlank()) fm.put("trackUrl", entry.trackUrl());
  ```
- **Read** (`parseEntry`, builder lines 215–220): add
  ```java
  .trackUrl(asString(data.get("trackUrl")))
  ```
- YAML uses `MINIMIZE_QUOTES` (line 44); a URL containing `:` and `//` will be
  quoted by Jackson when needed — verify in the round-trip test.

## 3. UI — `MainController`

- **@FXML field** (near lines 63–66): `@FXML private TextField trackUrlField;`
  plus optionally `@FXML private Button openTrackUrlButton;`.
- **Baseline** (lines 91–94): add `private String baselineTrackUrl = "";`.
- **Dirty listener** (`initialize`, lines 191–194): register
  `trackUrlField.textProperty().addListener(...)`.
- **`loadEntry`** (lines 416–419): `trackUrlField.setText(e.trackUrl() == null ? "" : e.trackUrl());`
- **`snapshotBaseline`** (lines 424–429): `baselineTrackUrl = trackUrlField.getText();`
- **`isDirty`** (lines 431–436): add a comparison clause for the track URL.
- **`onSave`** (entry build, lines 570–575): `.trackUrl(trackUrlField.getText())`.
- **Open in browser**: add
  ```java
  @FXML public void onOpenTrackUrl() {
      String url = trackUrlField.getText();
      if (url != null && !url.isBlank()) openInBrowser(url);
  }
  ```
  reusing the existing `openInBrowser(...)` (lines 846–856).
- Optionally enable/disable the open button based on whether the field is blank
  (mirror the `copyButton` logic in `updateDirty()`).

## 4. FXML — `resources/fxml/main.fxml`

Metrics row is the `GridPane` at lines 84–93 (row 0: km in cols 0/1, altitude in
cols 2/3). Extend row 0:

```xml
<Label text="Track URL:" GridPane.rowIndex="0" GridPane.columnIndex="4"/>
<TextField fx:id="trackUrlField" prefWidth="200" GridPane.rowIndex="0" GridPane.columnIndex="5"/>
<Button fx:id="openTrackUrlButton" text="🔗" onAction="#onOpenTrackUrl" GridPane.rowIndex="0" GridPane.columnIndex="6">
    <tooltip><Tooltip text="Open track URL in browser"/></tooltip>
</Button>
```

- Icons in this app are Unicode glyphs as button text (no image assets) — follow
  the connectivity/toolbar button pattern. Candidate glyphs: `🔗`, `↗`, `◉`.
- `routeField` on row 1 spans columns 1–3 (`columnSpan="3"`); check alignment
  once row 0 grows to columns 4–6, and widen the span/width if it looks off.

## 5. Export (optional) — `DiaryExporter`

The TODO does not mention export. If we want Track URL in the exported diary:

- Add constant `ENTRY_TRACK = "/export/entry-track.md"` (lines 24–28).
- In `renderEntry` (lines 89–101), after the altitude block, append:
  ```java
  if (e.trackUrl() != null && !e.trackUrl().isBlank()) {
      if (stats.length() > 0) stats.append("\n");
      stats.append(substitute(load(ENTRY_TRACK),
              Map.of("trackUrl", e.trackUrl())).stripTrailing());
  }
  ```
- New file `src/main/resources/export/entry-track.md` → `Track: {{trackUrl}}`.

## 6. Tests

- Extend `MarkdownStoreTest` round-trip (lines 83–117) to set a `trackUrl`,
  save, reload, and assert equality — including verifying YAML quoting of the URL.
- Optionally add a `DiaryEntry.Builder` test for blank → null coalescing.

## Open questions to resolve before implementing

1. **Export**: include Track URL in the exported Markdown diary, or UI-only?
2. **Validation**: validate that the input is a well-formed URL/`http(s)://`
   scheme, or accept any string? (Affects whether the open button is enabled.)
3. **Blank handling**: confirm blank → `null` (not persisted) is desired, matching
   distance/altitude rather than the `route` default-sentinel behavior.
4. **Glyph choice** for the open button (`🔗` vs `↗` vs an existing style class).
