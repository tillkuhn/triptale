# AGENTS.md

Agent quick-start for TripTale. Every item here answers: "Would an agent miss this without help?"

See `CLAUDE.md` for the full architectural narrative. This file is the compressed checklist version.

---

## Commands

```bash
make run          # launch the JavaFX app  (mvnd javafx:run)
make run-jar      # run the built fat jar with plain java
make run-fast     # run with the JDK 25 AOT cache (~1.5 s faster); extracts + trains on demand
make frun         # alias for run-fast
make aot-train    # force a fresh AOT training run (window opens and closes by itself)
make build        # jar, skip tests        (mvnd -DskipTests package)
make package      # jar + tests            (mvnd package)
make test         # tests only             (mvnd test)
make clean        # remove target/
make deps         # print dependency tree

# Single test
mvn test -Dtest=ClassName#method
# e.g.
mvn test -Dtest=SlugsTest#toSlug_stripsAccents

# Use a different settings dir (where settings.yml, incl. dataDir, is read from)
mvnd javafx:run -Dtriptale.settings-dir=/path/to/settings   # or TRIPTALE_SETTINGS_DIR=...

# Run the built jar directly (no Maven)
java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -jar target/triptale.jar
```

`make` uses `mvnd` (Maven daemon) by default if it's on `PATH`, otherwise falls back to plain `mvn` automatically; CI uses plain `mvn`. Force one explicitly: `make run MVN=mvn`.

`make format` is declared but has no recipe — it does nothing.

---

## Visual verification (screenshots, UI review) — not possible for an agent

You **can** launch the app (`make run` in the background) to confirm it boots and check logs. But this is a native macOS JavaFX desktop app — it does **not** run in a browser or Electron, so there is no Playwright/xvfb-style driver available.

Driving the UI (clicking menus, hovering, taking screenshots) requires macOS Accessibility permission for the calling process (System Events automation), which is not granted in the agent's environment. `osascript ... tell process "java" ...` fails with `-1728` ("not permitted to send Apple events"). This has been tried and confirmed broken — don't re-attempt it as a first move next session.

**For any task involving visual UI review (contrast, layout, highlight colors, etc.): launch the app if useful for logs/compilation, but ask the user to check the actual screen and describe/screenshot what they see.** Iterate based on their feedback rather than trying to self-verify.

This may be revisited later (e.g. granting Accessibility access to the terminal/agent process), but treat it as unresolved for now.

---

## Boot sequence (unusual — read this)

1. The jar's main class is `Launcher` (a plain class, **not** an `Application` subclass — otherwise classpath launches such as the extracted jar used by `run-fast` fail with "JavaFX runtime components are missing"). `Launcher.main()` calls `Application.launch(TripTaleApplication.class, args)` — **not** `SpringApplication.run(...)`.
2. JavaFX calls `init()` before `start()`. Spring is booted inside `init()` with `web-application-type: none`.
3. `HostServices` is registered as a Spring singleton manually in `init()` (not a `@Bean`) — before context refresh.
4. `start()` loads FXML with `loader.setControllerFactory(spring::getBean)`, making `MainController` a real Spring bean.

**To add a new FXML controller:** annotate `@Component`, reference via `fx:controller=` in FXML. The factory resolves it from Spring automatically.

---

## Package boundary rule

JavaFX imports are **forbidden** in `storage`, `git`, `config`, `export`, `i18n`, `attachments`, `impressions`, `map`, `radio`, `sync`, and `domain` packages. Only `ui` and the bootstrap class may touch JavaFX.

---

## Dependency direction

```
ui.MainController ──► storage.MarkdownStore ──► storage.SettingsStore ──► config.TripTaleProperties
                  ├── git.GitService ──────────────────────┘
                  ├── export.DiaryExporter ──► MarkdownStore, impressions.ImpressionsService
                  ├── impressions.ImpressionsService ──► storage.ImpressionsResolver, attachments.AttachmentsDir
                  ├── export.ExportTempFiles
                  ├── attachments.AttachmentsDir ──► MarkdownStore
                  ├── sync.BucketSyncer ──► sync.S3Client (SigV4, java.net.http)   (for attachments/ and radio/)
                  ├── ui.ConnectivityService
                  ├── map.PhotoLocations ──► ImpressionsService, storage.ExifReader   (for ui.dialog.MapAssistantDialog)
                  ├── map.PlaceLookup (Nominatim reverse + forward geocoding, java.net.http)   (for ui.dialog.CoordinatesDialog)
                  ├── radio.RadioLibrary ──► MarkdownStore   (played by ui.RadioPlayer, created lazily)
                  └── ui.dialog.* ──► the services each dialog needs
```

Dialogs never depend back on `MainController` — they take a `ui.StatusSink` (the status line)
and return their result as data. See **UI structure** below.

---

## Conventions

- **Constructor injection only** — no field `@Autowired` anywhere.
- **No `@SpringBootTest`** — all tests are pure unit tests; classes are instantiated directly with `new`.
- **No Spring context in tests** — `TripTaleProperties` is wired manually in each test class.
- **`@TempDir`** for all tests touching the filesystem — no fixtures on disk.
- **`Locale.ROOT`** for all number formatting — `String.format(Locale.ROOT, ...)` everywhere. Only exception: text that goes into an export uses the trip language's locale via `i18n.ExportTexts` (`12,5 km` in German); frontmatter always stays `Locale.ROOT`.
- **`Double` null means "not recorded"** — `0.0` is a real measurement. Do not coerce null to zero.
- **Errors from `storage`/`git` are unchecked** — `StorageException` / `GitException`. The UI catches `RuntimeException` at action boundaries and shows an alert; don't add checked exceptions to those layers.
- **Build entries via `DiaryEntry.Builder`** (`DiaryEntry.builder(date)...build()`) rather than the record constructor directly; `DiaryEntry.empty(date)` gives a blank entry for a given date.

---

## Storage model

```
<data-dir>/              # also a git repo; set as dataDir in settings.yml (no default)
├── .gitignore           # contains ".state.yml"
├── .state.yml           # gitignored; internal state: lastTripPath ("<year>/<slug>", on trip selection), lastEntryDate (on close only)
├── trip.md              # Tolaria type definition ("Trip"), created by GitService.initRepo()
├── tale.md              # Tolaria type definition ("Tale"), created by GitService.initRepo()
├── type.md              # Tolaria's self-referential "Type" meta-type, created by GitService.initRepo()
└── <year>/<slug>/
    ├── README.md                   # frontmatter: startDate (ISO string), type: Trip; body: "# {name}" heading + description
    └── YYYY-MM-DD-Weekday.md       # entries live directly in the trip folder (no entries/ subfolder); weekday in English locale, e.g. 2026-06-04-Thursday.md
```

Trip directories are nested one level under a 4-digit year directory at the data-dir root
(`<year>/<slug>/`, not `trips/<slug>/` — that intermediate `trips/` directory was removed, see
`docs/18_year_top_level_dirs.md`). `<year>` is fixed at creation time from the trip's
`startDate` and is **not** recomputed if `startDate` is edited later — the directory the trip
was loaded from is the authoritative year, carried as `Trip.year()` / `TripRef.year()`. Slugs
are unique only **within** a year, not globally — `TripRef(year, slug)` is a trip's real
identity; `MarkdownStore` methods that used to take a bare `String slug` now take `TripRef`.

`MarkdownStore.dataDir()` lazily creates the root on first access — callers can rely on it
existing after calling it. `listYears()` scans the data-dir root for 4-digit-named
directories; `listTrips(int year)` scans one year directory (both ignore non-matching entries,
e.g. `trip.md`/`type.md`/`.git`).

**Data dir & settings:** there is **no default data dir**. It's `dataDir` in `settings.yml`
(`config.AppSettings`, loaded/saved by `storage.SettingsStore`), edited at runtime in
⚙ Edit Settings… and only applied after a restart. `settings.yml` lives in
`$HOME/.config/triptale/` (macOS/Linux) or `%APPDATA%\triptale\` (Windows); the only Spring
property left is `triptale.settings-dir` (`TripTaleProperties`) to move that directory. While
`dataDir` is unset, `GitService.isConfigured()` is false and the storage/git layer stays inert
(no dir creation, no `git init`); `MainController` warns on every launch. An empty/missing data
dir is only initialized (`GitService.needsInit()` → `initRepo()`) after a yes/no prompt. See
`docs/13_settings_handling.md`.

**YAML frontmatter keys** (exact strings — do not camelCase):
- Entries: `activity`, `altitude`, `belongs_to`, `date`, `distance`, `start_lat`, `start_lon`, `start_place`, `stop_lat`, `stop_lon`, `stop_place`, `type`, `url_<verb>`
- `start_place`/`stop_place` (todo 51) are free text like `"Soest, DE"`, filled by the Coordinator's 🔍 reverse lookup (`map.PlaceLookup`, public Nominatim, ≤ 1 req/s, see ADR-0003) or typed by hand; allowed without coordinates. With a place but no coordinates, 🔍 runs a forward lookup (todo 59), fills in the place's centre point and appends the hit's country code to the typed name (`"Krung Thep Aphiwat, TH"`). See `docs/done/51_reverse_geocoding.md`.
- Trips (`README.md`): `activity`, `end_date`, `start_date`, `type`
- Single-word keys stay flat lowercase (`altitude`, `date`); multi-word keys use snake_case (`start_lat`, `belongs_to`, `start_date`). Coordinate keys were `startlat`/`startlon` (flat) before 2026-09; existing data dirs were migrated ad hoc with a one-time script, not shipped in the app.
- Entry links are flat `url_<verb>` keys (`url_track`, `url_eat`, …), each holding a bare URL or `"[Name](url)"` (`domain.Link`). The curated verbs, with their emoji and label, live in `domain.LinkKind`; links are listed and exported alphabetically by verb (`LinkKind.ORDER`), unknown verbs included. Keys with any other verb are **preserved** on load and save, and values that aren't URLs are kept and flagged rather than dropped. `trackurl` was renamed to `url_track` in 2026-10 (migrated ad hoc, no fallback in the app). See `docs/done/09_entry_links.md`.
- `activity` (todo 55) holds one `domain.Activity` key (lowercase) or a tolerated unknown value; absent = not set. On a trip it's only the default that **new** entries are pre-filled with (todo 57) — copied on first save, never inherited at read time. See `docs/done/55_activity_per_entry.md`, `docs/done/57_trip_default_activity.md`.
- `MarkdownStore.saveEntry`/`saveTrip` write these keys in **alphabetical order** — keep them alphabetical when adding new ones, so serialized YAML stays diff-stable.
- Every entry is written with `type: Tale` (see `MarkdownStore.ENTRY_TYPE`); every trip `README.md` is written with `type: Trip` (see `MarkdownStore.TRIP_TYPE`). These are [Tolaria](https://github.com/refactoringhq/tolaria) note-type tags: they let the data dir double as a Tolaria vault. `trip.md`/`tale.md` at the data-dir root are the corresponding Tolaria type definitions (`type: Type`); `type.md` is Tolaria's self-referential meta-type definition for `Type` itself. `GitService.initRepo()` creates all three if missing, alongside `.gitignore` setup.
- Trip `name` is **not** in frontmatter — it's the first `# Heading` line of the README.md body (Tolaria/most Markdown viewers title a note from its first `#` heading, or the filename otherwise; since every trip file is named `README.md`, the name must live in the heading so it doesn't just show up as "README"). `MarkdownStore.loadTrip` parses it back out of that heading; `description` is everything after it.
- Older data dirs may still have `trips/<slug>/trip.yml` from before the README.md migration — the app no longer reads it; migrate ad hoc (`name` becomes a `# {name}` heading, `startDate` into frontmatter, `description` as the body after the heading, add `type: Trip`, delete the old file).

**Trip slugs are immutable** — derived once from the name via `Slugs.toSlug()` (NFD-normalize, strip diacritics, kebab-case). Renaming a trip does not rename the directory. There is no migration path.

---

## Git integration

- JGit for `init`, `add`, `commit`, `status`.
- OS `git` binary via `ProcessBuilder` for `push` and `pull` (120 s timeout; requires `git` on PATH).
- **Never commit from inside `MarkdownStore` or `GitService` after a save.** Saves go to disk immediately. `MainController` accumulates pending saves in a `Map<String, String>`; they are committed by Smart Sync (`Cmd+K` / `Ctrl+K`, `ui/dialog/SmartSyncDialog`) or the menu-only Commit. The pending map only shapes the commit message — whether there is anything to commit is decided by `GitService.dirtyFiles()` (git status), so external edits are caught too. Follow this pattern for new write operations: save → `addPending(...)` → user triggers sync/commit. See `docs/32_smart_sync.md`.

---

## Connectivity check (`ConnectivityService`)

- `checkTask(remoteUrl)` returns a JavaFX `Task<Boolean>` that opens a 3-second TCP connection to port 443 of the remote git host — run it on a daemon thread, never on the FX thread.
- `resolveHost(remoteUrl)` handles both HTTPS and SCP-style (`git@host:repo`) remote URLs; falls back to `github.com` if the remote is blank or unparseable.
- `MainController` updates the toolbar button style class (`connectivity-connected` / `connectivity-disconnected` / `connectivity-checking`) and enables/disables the push/pull menu items based on the result.

---

## Export templates

Mustache-style `{{var}}` templates in `src/main/resources/export/`. Loaded via `getResourceAsStream` at runtime — missing template = `IllegalStateException`. Number formats: distance `%.1f`, altitude `%.0f`, both in the trip locale (`ExportTexts.distance` / `altitude`).

**Trip language (todo 30):** the app UI stays English; only words the exports add (Day, Distance,
link labels, weekdays, numbers) follow `defaultTripLanguage` in `settings.yml` (`en`|`de`, read at
export time). Templates reference words as `{{t.<key>}}`, resolved from
`src/main/resources/i18n/export.properties` (English base, every key) and `export_de.properties`
(may omit keys). `link.<verb>` keys exist only per language — English labels come from `LinkKind`.
`ExportTexts` loads the bundle with a no-fallback `ResourceBundle.Control`; without it a missing
`export_en.properties` would fall back to the JVM default locale. Entry filenames stay English.
A future per-trip override will be the `lang` key in the trip `README.md`.

**Private sections (todo 48):** a tale heading starting with `internal`/`private`/`intern`/`privat`
(case-insensitive, then end/whitespace/`:`) hides itself and everything up to the next heading of the
same or higher level. Every exporter must run tales through `Markdown.stripPrivate` before
`shiftHeadings` — `WordPressExporter` has its own tale path, so a new exporter needs it too. See
`docs/48_private_sections.md`.

**Entry facts (todo 53):** an entry heading is its title only (`DiaryEntry.DEFAULT_TITLE` / blank
→ "Day N", or the date without a trip start). Date, activity + distance/climb and start/stop
point come from `export.EntryFacts`: an italic byline in Markdown, a "ticket stub" HTML block
(styled in `html-shell.html`) in the HTML export, a `wp:columns` block on WordPress. Readers
never see an ISO date. See `docs/done/53_entry_fact_strip.md`.

---

## UI structure

`MainController` owns only the single main window: trip/year/date navigation, the entry form
and its dirty tracking, and the toolbar/menu git actions. **Every modal dialog lives in its own
class under `ui/dialog/`** — put new ones there rather than growing the controller.

A dialog class is a plain object (not a Spring bean) constructed with the services it needs;
`MainController` news it up in the handler, or holds it as a field when the controller has no
other use for its dependencies (`ExportDiaryDialog`, `ImageViewerDialog`, `AboutDialog`).

The contract: **a dialog returns its outcome as data and never mutates controller state.**
`NewTripDialog` → `Optional<Spec>`, `TripDetailsDialog` → `Optional<Trip>`,
`CoordinatesDialog` → `Optional<Result>` (`start()`/`stop()`, a null point means "unset";
`Optional.empty()` means Cancel). Persisting, `addPending(...)`, and combo reselection stay in
the controller. **One save per day:** a dialog that edits the day's frontmatter (Weblinks, Coordinator, the
image viewer's "Set as Start/End") confirms with `Dialogs.addSaveDayButtons` — "📅 Save the Day"
(Cmd/Ctrl+S too) when the entry is savable, "Apply" otherwise. On confirm the controller applies
the result and calls `saveTheDayIfPossible()`, which saves the whole entry, main-window edits
included. New frontmatter dialogs should do the same rather than leave the day dirty.
`SmartSyncDialog` inverts this — it owns its worker threads and reports an
`Outcome` through a callback that runs on the FX thread.

Shared UI helpers, all in `ui/`:

- `Dialogs` — `applyStylesheet`, `formGrid()`, `infoGrid()`, `showInfo(...)`, `addSaveDayButtons(...)`
- `UiText` — `homeRelative`, `friendlyDate`, `describe` (flattens a cause chain), `isValidHttpUrl`, `parseDecimal`
- `Clipboards.putString`, `BrowserLauncher.open`, `StatusSink`

## UI gotchas

- Button look lives in `src/main/resources/fxml/triptale.css` under `/* ── Buttons ── */`. Buttons are deliberately "raised" (lighter gradient fill, 1px top highlight via a two-layer background, soft drop shadow) and inputs "sunken" (darker than the surface), so buttons don't read as bordered text. To tune: the gradient stops on `.button` / `.button:hover` and the shadow alpha. The teal filled button is `:default` (`defaultButton="true"`, e.g. 📅 Save the Day — Enter fires it unless the focused control consumes Enter, like a TextArea). `.nav-button-group` carries one shared shadow; its child buttons and the date-picker popup's spinner arrows have `-fx-effect: none`.
- Every new `Alert` or `Dialog` must call `Dialogs.applyStylesheet(dialogPane)` to inherit the dark theme. Going through `Dialogs.showInfo(...)` instead of `new Alert(...)` makes that impossible to forget.
- `BuildProperties` (version/build-date in About) and JavaFX `HostServices` (opening URLs in the system browser) are both resolved via `ObjectProvider` on `MainController` and may be null. `BuildProperties` is absent only if the data dir has no reachable git tag at all — `spring-boot-maven-plugin:build-info`'s `build-info` goal actually binds to the `generate-resources` phase (not `prepare-package`, despite what its own docs/javadoc imply), so it already runs on `mvn compile`/`process-classes`, and therefore on `make run`/`run-fast` too (`javafx:run` forks the build up through `process-classes`, which is after `generate-resources`) — not just `package`/`verify`. Its `version` is populated from `git-commit-id-maven-plugin`'s `git.closest.tag.name` (most recent reachable tag, e.g. `v0.9.0` — no commit-distance suffix, no dirty marker), overriding the Maven coordinate (`0.1.0-SNAPSHOT`), which is never bumped. `HostServices` is wrapped in `ui.BrowserLauncher`, which degrades to a log warning when absent.
- Decimal input (`UiText.parseDecimal`) accepts both `.` and `,` as separators. The distance/altitude fields go through `util.Lengths` instead: a non-negative number with optional `km`/`m` suffix (bare = km for distance, m for altitude), converted and rounded (2 decimals km, whole m), shown as `"101.64 km"` / `"493 m"` (normalized on focus loss and save). Frontmatter stays a plain number. Dirty tracking compares the parsed values, not the text.
- Export previews go through `export.ExportTempFiles` (`newFile` / `sweep`), not `Files.createTempFile` directly — `sweep()` runs once from `initialize()` to clear files left by crashed sessions.

---

## CI

- **`build.yml`** — triggers on push/PR to `main`; runs `mvn -B -ntp verify` on `ubuntu-latest` with Temurin 25.
- **`release.yml`** — triggers on `v*` tags; builds jar with `-DskipTests` on `ubuntu-latest` only (macOS runner was hanging with no available runner), publishes `triptale-linux.jar` as a GitHub Release asset.
- **JavaFX natives follow the build host.** The JavaFX deps in `pom.xml` deliberately have no `<classifier>`; openjfx's parent pom picks the natives for the OS/arch Maven runs on. A jar runs only on the OS it was built on — the Linux release jar must therefore keep being built on a Linux runner. Don't re-add a hard-coded classifier (it used to bundle a second, foreign set). See `docs/43_startup_performance.md`.

---

## JaCoCo

Coverage instrumented only for `net.timafe.triptale.*`. Excluded: `ui/**`, `TripTaleApplication.class` and `Launcher.class` (headless-incompatible). Reports generated at `test` phase (HTML + XML in `target/site/jacoco/`).
