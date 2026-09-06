# AGENTS.md

Agent quick-start for TripTale. Every item here answers: "Would an agent miss this without help?"

See `CLAUDE.md` for the full architectural narrative. This file is the compressed checklist version.

---

## Commands

```bash
make run          # launch the JavaFX app  (mvnd javafx:run)
make build        # jar, skip tests        (mvnd -DskipTests package)
make package      # jar + tests            (mvnd package)
make test         # tests only             (mvnd test)
make clean        # remove target/
make deps         # print dependency tree

# Single test
mvn test -Dtest=ClassName#method
# e.g.
mvn test -Dtest=SlugsTest#toSlug_stripsAccents

# Override any triptale.* config property at launch
mvnd javafx:run -Dtriptale.data-dir=/path/to/data

# Run the built jar directly (no Maven)
java --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -jar target/triptale.jar
```

`make` uses `mvnd` (Maven daemon) by default if it's on `PATH`, otherwise falls back to plain `mvn` automatically; CI uses plain `mvn`. Force one explicitly: `make run MVN=mvn`.

`make format` is declared but has no recipe — it does nothing.

---

## Boot sequence (unusual — read this)

1. `main()` calls `Application.launch(TripTaleApplication.class, args)` — **not** `SpringApplication.run(...)`.
2. JavaFX calls `init()` before `start()`. Spring is booted inside `init()` with `web-application-type: none`.
3. `HostServices` is registered as a Spring singleton manually in `init()` (not a `@Bean`) — before context refresh.
4. `start()` loads FXML with `loader.setControllerFactory(spring::getBean)`, making `MainController` a real Spring bean.

**To add a new FXML controller:** annotate `@Component`, reference via `fx:controller=` in FXML. The factory resolves it from Spring automatically.

---

## Package boundary rule

JavaFX imports are **forbidden** in `storage`, `git`, `config`, `export`, and `domain` packages. Only `ui` and the bootstrap class may touch JavaFX.

---

## Dependency direction

```
ui.MainController ──► storage.MarkdownStore ──► config.TripTaleProperties
                  ├── git.GitService ───────────────────┘
                  ├── export.DiaryExporter ──► MarkdownStore
                  └── ui.ConnectivityService
```

---

## Conventions

- **Constructor injection only** — no field `@Autowired` anywhere.
- **No `@SpringBootTest`** — all tests are pure unit tests; classes are instantiated directly with `new`.
- **No Spring context in tests** — `TripTaleProperties` is wired manually in each test class.
- **`@TempDir`** for all tests touching the filesystem — no fixtures on disk.
- **`Locale.ROOT`** for all number formatting — `String.format(Locale.ROOT, ...)` everywhere.
- **`Double` null means "not recorded"** — `0.0` is a real measurement. Do not coerce null to zero.
- **Errors from `storage`/`git` are unchecked** — `StorageException` / `GitException`. The UI catches `RuntimeException` at action boundaries and shows an alert; don't add checked exceptions to those layers.
- **Build entries via `DiaryEntry.Builder`** (`DiaryEntry.builder(date)...build()`) rather than the record constructor directly; `DiaryEntry.empty(date)` gives a blank entry for a given date.

---

## Storage model

```
<data-dir>/              # also a git repo; default: ~/Pictures/triptale-data (from application.yml)
├── .gitignore           # contains "prefs.yml"
├── prefs.yml            # gitignored; lastTripSlug only
├── trip.md              # Tolaria type definition ("Trip"), created by GitService.initOnStartup()
├── tale.md              # Tolaria type definition ("Tale"), created by GitService.initOnStartup()
├── type.md              # Tolaria's self-referential "Type" meta-type, created by GitService.initOnStartup()
└── trips/<slug>/
    ├── trip.yml         # name, startDate (ISO string), description
    └── entries/
        └── YYYY-MM-DD_Weekday.md   # weekday in English locale, e.g. 2026-06-04_Thursday.md
```

`MarkdownStore.dataDir()` lazily creates the root and `trips/` on first access — callers can rely on both directories existing after calling it.

**Default data dir:** `application.yml` sets `~/Pictures/triptale-data`. `TripTaleProperties` Java field defaults to `~/.triptale` but is overridden at runtime. The `application.yml` value wins.

**YAML frontmatter keys** (exact strings — do not camelCase):
- `altitude`, `date`, `distance`, `route`, `trackurl`, `type` (all lowercase)
- `MarkdownStore.saveEntry` writes these keys in **alphabetical order** — keep them alphabetical when adding new ones, so serialized YAML stays diff-stable.
- Every entry is written with `type: Tale` (see `MarkdownStore.ENTRY_TYPE`). This is a [Tolaria](https://github.com/refactoringhq/tolaria) note-type tag: it lets the data dir double as a Tolaria vault. `trip.md`/`tale.md` at the data-dir root are the corresponding Tolaria type definitions (`type: Type`); `type.md` is Tolaria's self-referential meta-type definition for `Type` itself. `GitService.initOnStartup()` creates all three if missing, alongside `.gitignore` setup. `trip.yml` itself is **not** tagged with a type field (only entries are, for now).

**Trip slugs are immutable** — derived once from the name via `Slugs.toSlug()` (NFD-normalize, strip diacritics, kebab-case). Renaming a trip does not rename the directory. There is no migration path.

---

## Git integration

- JGit for `init`, `add`, `commit`, `status`.
- OS `git` binary via `ProcessBuilder` for `push` and `pull` (120 s timeout; requires `git` on PATH).
- **Never commit from inside `MarkdownStore` or `GitService` after a save.** Saves go to disk immediately. `MainController` accumulates pending saves in a `Map<String, String>` and commits them in batch via the Commit button (`Cmd+K` / `Ctrl+K`). Follow this pattern for new write operations: save → `addPending(...)` → user triggers commit.

---

## Connectivity check (`ConnectivityService`)

- `checkTask(remoteUrl)` returns a JavaFX `Task<Boolean>` that opens a 3-second TCP connection to port 443 of the remote git host — run it on a daemon thread, never on the FX thread.
- `resolveHost(remoteUrl)` handles both HTTPS and SCP-style (`git@host:repo`) remote URLs; falls back to `github.com` if the remote is blank or unparseable.
- `MainController` updates the toolbar button style class (`connectivity-connected` / `connectivity-disconnected` / `connectivity-checking`) and enables/disables the push/pull menu items based on the result.

---

## Export templates

Five Mustache-style `{{var}}` templates in `src/main/resources/export/`. Loaded via `getResourceAsStream` at runtime — missing template = `IllegalStateException`. Number formats: distance `%.1f`, altitude `%.0f`, both with `Locale.ROOT`.

`DiaryEntry.DEFAULT_ROUTE` (`"From → To"`) is a UI placeholder. Export headings suppress it; `routeSegment()` returns `""` for this value.

---

## UI gotchas

- Every new `Alert` or `Dialog` must call `applyStylesheet(dialogPane)` to inherit the dark theme.
- `BuildProperties` (version/build-date in About) and JavaFX `HostServices` (opening URLs in the system browser) are both `@Autowired(required = false)` on `MainController`. `BuildProperties` is absent unless the `spring-boot-maven-plugin:build-info` goal has run (happens during `mvn package`/`verify`, not `compile`).
- Decimal input (`parseDouble`) accepts both `.` and `,` as separators.

---

## CI

- **`build.yml`** — triggers on push/PR to `main`; runs `mvn -B -ntp verify` on `ubuntu-latest` with Temurin 25.
- **`release.yml`** — triggers on `v*` tags; builds jar with `-DskipTests` on `ubuntu-latest` only (macOS runner was hanging with no available runner), publishes `triptale-linux.jar` as a GitHub Release asset.

---

## JaCoCo

Coverage instrumented only for `net.timafe.triptale.*`. Excluded: `ui/**` and `TripTaleApplication.class` (headless-incompatible). Reports generated at `test` phase (HTML + XML in `target/site/jacoco/`).
