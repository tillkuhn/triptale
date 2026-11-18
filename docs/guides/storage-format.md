# Data storage format 💾

Everything lives under one **data directory**, which is itself a git repo. You choose its location on first start (⚙ Edit Settings…); until then the app stays inert and reminds you on every launch.

```
triptale-data/                         # git repo
├── .gitignore                         # ignores .triptale/
├── .triptale/                         # local to this device, never committed: app state, sync bases, caches
├── trip.md  tale.md  type.md          # Tolaria type definitions
├── 2026/                              # year of the trip's start date
│   └── alps-2026/                     # trip slug, fixed at creation
│       ├── README.md                  # the trip: name, dates, description
│       ├── 2026-06-04-Thursday.md     # one tale per day
│       └── 2026-06-05-Friday.md
├── attachments/                       # the backpack (optional), see "Backpack"
│   └── 2026/alps-2026/2026-06-04-Thursday/
│       └── gotthard.gpx
└── radio/                             # music pool for Tailwind FM (optional), never in git
    └── some-artist - some-song.mp3
```

A tale is just Markdown with a YAML frontmatter block — structured fields on top, the day's title as the first heading, free notes below:

```markdown
---
altitude: 1840.0
belongs_to: "[[2026/alps-2026/README]]"
date: 2026-06-04
distance: 87.0
start_lat: 46.6356
start_lon: 8.5939
stop_lat: 46.5287
stop_lon: 8.6097
type: Tale
url_sleep: "[Hotel Lepontina](https://lepontina.ch/)"
url_track: https://www.strava.com/activities/123456
---

# Andermatt → Airolo

Crossed the Gotthard pass today. Strong headwind from the south,
but the descent into Airolo was worth it.
```

The trip's `README.md` works the same way: `start_date`, `end_date`, optionally a default `activity`, and `type: Trip` in the frontmatter, the trip name as `# Heading`, the description below.

- Frontmatter keys are always written in alphabetical order, so saving never produces a noisy diff.
- Other keys you may see: `activity`, `start_place` / `stop_place` (free text such as `Soest, DE`), `weather` (`min;max;code[;place]`) and, on trips, `post_tags` and `track_totals`.
- Fields you didn't fill in are left out; `0` is a real measurement, a missing key means "not recorded".
- `url_<verb>` keys hold a few links per day, at most one each for `checkout`, `chill`, `dive`, `drink`, `eat`, `explore`, `listen`, `rent`, `sleep`, `track`, `travel`, `visit` and `watch`: either a bare URL or `"[Name](url)"`. You edit them in the 🕸️ Weblinks Manager, and the exports list them at the end of each day (e.g. `🛏️ Sleep at: Hotel Lepontina`). `url_*` keys with any other verb are kept as they are.
- `type: Tale` / `type: Trip`, the `belongs_to` wiki link and the `trip.md`/`tale.md`/`type.md` definitions make the data directory a valid [Tolaria](https://github.com/refactoringhq/tolaria) vault, so you can browse and edit your trips there too.
- A trip's slug (its directory name) is derived from its name once and never changes, even if you rename the trip later.

That's it. No database, no schema migrations. If TripTale disappears tomorrow, your trips remain a perfectly readable folder of Markdown.

## The app-free workflow ✍️
Because everything is just files, **you don't actually need the app to use TripTale.** A perfectly valid workflow is:

```bash
cd ~/triptale-data/2026/alps-2026
vim 2026-06-04-Thursday.md          # write your day
git add . && git commit -m "Day 4"
git push                            # if you've set a remote
```

The next time you open the app, your entries are right there. Use the app when you want a nicer editing surface; use any editor when you don't. Both round-trip cleanly through git — Smart Sync picks up files you changed outside the app, too.

---

Back to the [guides](README.md) · [project README](../../README.md)
