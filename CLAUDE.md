# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

The `Makefile` wraps Maven. Common targets:

- `make run` — launch the JavaFX app (`mvn javafx:run`)
- `make build` — package without running tests (`mvn -DskipTests package`)
- `make package` — full package with tests (`mvn package`)
- `make test` — run unit tests (`mvn test`)
- `make compile` / `make clean` / `make deps`

Single test (no test sources exist yet, but the convention is): `mvn test -Dtest=ClassName#method`.

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

### Git integration (`GitService`)

- `@PostConstruct initOnStartup()` calls `git init` on the data dir if `.git` is missing, and writes the `origin` remote from `triptale.git.remote` if set.
- `commitAll(msg)` stages everything (`git add .`), is a no-op when the tree is clean, and uses `triptale.git.author-{name,email}` if both are non-blank — otherwise JGit falls back to the system git config.
- `push()` / `pull()` throw `GitException` when no remote is configured. The UI surfaces those messages via `MainController.error(...)`.
- Commits are manual: `MainController` tracks saved-but-uncommitted entries in an in-memory pending map and exposes a Commit button (Cmd+K) that calls `commitAll(...)` with an aggregated message. New write operations should follow this pattern (save first, add to the pending map via `addPending(...)`) rather than committing from inside the storage layer.

### Dependency direction

```
ui.MainController ──► storage.MarkdownStore ──► config.TripTaleProperties
                  └► git.GitService ─────────┘
```

Keep JavaFX imports out of `storage`, `git`, `config`, and `domain`. The `domain` records (`Trip`, `DiaryEntry`) are plain Java records used by both layers.

## Conventions

- Java 25, records for domain types, constructor injection (no field `@Autowired`).
- Errors from the storage/git layer throw `StorageException` / `GitException` (unchecked). The UI catches `RuntimeException` at action boundaries and shows an alert.
- Decimal inputs in the UI accept both `.` and `,` as separators (`MainController.parseDouble`).
