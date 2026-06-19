# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

The `Makefile` wraps Maven (uses `mvnd` by default). Common targets:

- `make run` — launch the JavaFX app (`mvnd javafx:run`)
- `make build` — package without running tests (`mvnd -DskipTests package`)
- `make package` — full package with tests (`mvnd package`)
- `make test` — run unit tests (`mvnd test`)
- `make compile` / `make clean` / `make deps`

Single test: `mvn test -Dtest=ClassName#method` (e.g. `mvn test -Dtest=SlugsTest#toSlug_stripsAccents`).

Override config at launch: `mvn javafx:run -Dtriptale.data-dir=/path` (any `triptale.*` property from `application.yml` works as a `-D` flag or env var).

## Architecture

TripTale is a **Spring Boot + JavaFX hybrid**. The unusual part is how the two frameworks bootstrap:

1. `TripTaleApplication` extends `javafx.application.Application`. `main()` calls `Application.launch(...)`.
2. JavaFX calls `init()` *before* `start()`. We use that hook to boot Spring (`SpringApplication.run(...)`) and stash the `ConfigurableApplicationContext`.
3. In `start()`, the FXML loader gets `spring::getBean` as its `controllerFactory`, so controllers like `MainController` are real Spring beans with constructor-injected services.
4. `spring.main.web-application-type: none` keeps Spring headless — no embedded server.

If you add a new controller, register it as `@Component` and reference it via `fx:controller=` in FXML; the controller factory will resolve it from the Spring context.

### Storage model (`MarkdownStore`)

All data lives under a single root (`triptale.data-dir`, default `~/.triptale`), which is **also a single git repo**. Layout:

```
<data-dir>/
└── trips/<slug>/
    ├── trip.yml                            # name, startDate, description
    └── entries/YYYY-MM-DD_Weekday.md       # YAML frontmatter (distance in km, altitude) + free-text body
```

- Trip slugs are derived via `Slugs.toSlug(name)` (NFD-normalize, strip diacritics, kebab-case) and are immutable — they identify the trip on disk.
- Entry files are markdown with a `---`-delimited YAML frontmatter. `MarkdownStore` writes/parses both halves; do not introduce a separate Markdown parser unless you need the body structure (today it is opaque text).
- `MarkdownStore.dataDir()` lazily creates the root and `trips/` on first access; callers can rely on directories existing after the call.
- Local preferences (last-used trip slug) are stored in `prefs.yml` at the data-dir root and are gitignored.

### Git integration (`GitService`)

- `@PostConstruct initOnStartup()` calls `git init` on the data dir if `.git` is missing, and ensures `prefs.yml` is listed in `.gitignore`.
- `commitAll(msg)` stages everything (`git add .`), is a no-op when the tree is clean, and uses `triptale.git.author-{name,email}` if both are non-blank — otherwise JGit falls back to the system git config.
- `push()` / `pull()` spawn the OS `git` binary via `ProcessBuilder` (120 s timeout) and throw `GitException` when no remote is configured. The UI surfaces those messages via `MainController.error(...)`.
- Commits are manual: `MainController` tracks saved-but-uncommitted entries in an in-memory pending map and exposes a Commit button (Cmd+K) that calls `commitAll(...)` with an aggregated message. New write operations should follow this pattern (save first, add to the pending map via `addPending(...)`) rather than committing from inside the storage layer.

### Export (`DiaryExporter`)

- `DiaryExporter` renders a full trip to a single Markdown document using five Mustache-style `{{var}}` templates loaded from `src/main/resources/export/`.
- `exportTrip(Trip)` loads all entry dates from `MarkdownStore`, sums distance/altitude totals, renders per-entry sections, then stitches everything through the top-level `diary-template.md`.
- The default route value `"From → To"` is intentionally suppressed in export headings.
- All number formatting uses `Locale.ROOT` to avoid locale-specific separators.

### Connectivity check (`ConnectivityService`)

- `ConnectivityService.checkTask(remoteUrl)` returns a JavaFX `Task<Boolean>` that opens a 3-second TCP connection to port 443 of the remote git host.
- `resolveHost(remoteUrl)` handles both HTTPS and SCP-style (`git@host:repo`) remote URLs; falls back to `github.com` if the remote is blank or unparseable.
- `MainController` runs the task on a daemon thread and updates the toolbar button style class (`connectivity-connected` / `connectivity-disconnected` / `connectivity-checking`) and enables/disables the push/pull menu items.

### Dependency direction

```
ui.MainController ──► storage.MarkdownStore ──► config.TripTaleProperties
                  ├── git.GitService ────────────────┘
                  ├── export.DiaryExporter ──► MarkdownStore
                  └── ui.ConnectivityService
```

Keep JavaFX imports out of `storage`, `git`, `config`, `export`, and `domain`. The `domain` records (`Trip`, `DiaryEntry`) are plain Java records used by both layers.

`MainController` is optionally injected with Spring Boot's `BuildProperties` (for version/build-date display in About) and JavaFX's `HostServices` (for opening URLs in the system browser); both are `@Autowired(required = false)`.

## Conventions

- Java 25, records for domain types, constructor injection (no field `@Autowired`).
- Errors from the storage/git layer throw `StorageException` / `GitException` (unchecked). The UI catches `RuntimeException` at action boundaries and shows an alert.
- Decimal inputs in the UI accept both `.` and `,` as separators (`MainController.parseDouble`).
- The `DiaryEntry.Builder` inner class is the preferred way to construct entries in tests and the UI; `DiaryEntry.empty(date)` provides a blank entry for a given date.
