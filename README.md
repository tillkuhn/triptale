# TripTale

Offline-first cycling and hiking trip diary with git-backed sync. Java 25, Spring Boot, JavaFX.

## Data model

One repo holds all trips. Each trip lives in `trips/<slug>/`, with one markdown
file per day in `entries/YYYY-MM-DD.md`. Markdown files use YAML frontmatter
for structured fields (`distance`, `altitude`) and freeform notes in the body.

```
~/.triptale/                          # single git repo (configurable)
├── trips/
│   └── alps-2026/
│       ├── trip.yml
│       └── entries/
│           └── 2026-06-04.md
```

## Run

```bash
mvn javafx:run
```

## Build

```bash
mvn clean package
```

## Configuration

Override defaults via `application.yml`, env vars, or CLI flags:

| Property                     | Default        | Notes                                          |
|------------------------------|----------------|------------------------------------------------|
| `triptale.data-dir`          | `~/.triptale`  | Root directory; single git repo                |
| `triptale.git.auto-commit`   | `true`         | Commit on every save                           |
| `triptale.git.remote`        | *(blank)*      | Optional remote URL for push/pull              |
| `triptale.git.author-name`   | *(blank)*      | Falls back to system git config                |
| `triptale.git.author-email`  | *(blank)*      | Falls back to system git config                |

Example: `mvn javafx:run -Dtriptale.data-dir=/Volumes/USB/triptale`
