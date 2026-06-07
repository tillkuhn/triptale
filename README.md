# TripTale 🏔️🚴

> An offline-first diary for long cycling and hiking trips — your notes live as plain Markdown in a git repo you own.

TripTale is a small JavaFX app for keeping a day-by-day journal of a trip: kilometres ridden, altitude climbed, and whatever notes you want to scribble. The twist: there is no cloud, no account, no database. Every entry is a plain Markdown file on disk, and the whole thing is a git repository — so you can sync between machines, hack it from the command line, or never touch the app again and just edit `.md` files in your favourite editor.

> ⚠️ **Status:** personal hobby project / early PoC. Works for me on macOS. No roadmap, no promises — but contributions and ideas are welcome.

## Why? 🤔

I wanted a trip diary that:

- Works **without internet**, on a campsite, on a ferry, halfway up an Alpine pass.
- Stores my notes in a format I'll still be able to read in 20 years (plain text > proprietary blobs).
- Syncs between my laptop and desktop without me logging into yet another service.
- Lets me edit entries from *anywhere* — the app on my laptop, `vim` on the road, Obsidian when I get home.

Git + Markdown turned out to be the answer. TripTale is just a friendly UI on top of that.

## Screenshot 📸

<!-- TODO: drop a real screenshot here -->
```
+----------------------------------------------+
|  TripTale — Alps 2026                        |
|  ┌────────────────────────────────────────┐  |
|  │  2026-06-04  ▼     km: 87   alt: 1840m │  |
|  ├────────────────────────────────────────┤  |
|  │  Crossed the Gotthard pass today.      │  |
|  │  Strong headwind from the south, but   │  |
|  │  the descent into Airolo was worth it. │  |
|  └────────────────────────────────────────┘  |
|  [ Save ]  [ Pull ]  [ Push ]                |
+----------------------------------------------+
```

## How it stores your data 💾

Everything lives under one directory (default `~/.triptale`), which is itself a git repo:

```
~/.triptale/                          # single git repo
└── trips/
    └── alps-2026/
        ├── trip.yml                  # name, start date, description
        └── entries/
            └── 2026-06-04_Thursday.md
```

A diary entry is just Markdown with a YAML frontmatter block — structured fields on top, free notes below:

```markdown
---
distance: 87.0
altitude: 1840.0
---

Crossed the Gotthard pass today. Strong headwind from the south,
but the descent into Airolo was worth it.
```

That's it. No database, no schema migrations. If TripTale disappears tomorrow, your trips remain a perfectly readable folder of Markdown.

## The app-free workflow ✍️

Because everything is just files, **you don't actually need the app to use TripTale.** A perfectly valid workflow is:

```bash
cd ~/.triptale/trips/alps-2026/entries
vim 2026-06-04_Thursday.md          # write your day
cd ~/.triptale
git add . && git commit -m "Day 4"
git push                            # if you've set a remote
```

The next time you open the JavaFX app, your entries are right there. Use the app when you want a nicer editing surface; use any editor when you don't. Both round-trip cleanly through git.

This also means you can keep writing offline for weeks — every save is a local git commit — and push the whole batch the moment you find Wi-Fi.

## Run it 🛠️

Requires **Java 25** and Maven.

```bash
make run        # or: mvn javafx:run
make build      # package without tests
make test       # run unit tests
```

Point at a different data directory (e.g. a USB stick for travel):

```bash
mvn javafx:run -Dtriptale.data-dir=/Volumes/USB/triptale
```

## Configuration ⚙️

Override defaults via `application.yml`, env vars, or `-D` flags:

| Property                     | Default       | Notes                                       |
|------------------------------|---------------|---------------------------------------------|
| `triptale.data-dir`          | `~/.triptale` | Root directory; also the git repo root      |
| `triptale.git.auto-commit`   | `true`        | Commit on every save                        |
| `triptale.git.remote`        | *(blank)*     | Optional remote URL for push/pull           |
| `triptale.git.author-name`   | *(blank)*     | Falls back to system git config             |
| `triptale.git.author-email`  | *(blank)*     | Falls back to system git config             |

## Tech stack 🧰

- **Java 25** with records for the domain types
- **Spring Boot** (headless — `web-application-type: none`) for DI, config binding, and lifecycle
- **JavaFX** for the UI, with FXML controllers resolved as Spring beans
- **JGit** for in-process git operations (init, commit, push, pull)
- **Maven** as the build system; a thin `Makefile` wraps the common targets

The interesting bit architecturally is that JavaFX's `Application.init()` boots Spring *before* `start()`, and the FXML loader uses `spring::getBean` as its controller factory — so controllers are real Spring beans with constructor-injected services. See `CLAUDE.md` for more on the layering.

## Contributing 🤝

This is a hobby project, but PRs, issues, and ideas are welcome. A few ground rules:

- Keep JavaFX imports out of the `storage`, `git`, `config`, and `domain` packages.
- Domain types are records — keep them plain.
- Constructor injection only, no field `@Autowired`.

If you're thinking of something larger than a small fix, open an issue first so we can talk about it.

## License 📜

Apache License 2.0 — see [LICENSE](LICENSE).
