# Backpack 🎒

The backpack holds the big stuff that doesn't belong in Markdown: GPX tracks, PDFs, tickets, photos. **🎒 Add to Backpack…** (Tale Entries menu) copies files into a folder for the current day: `attachments/<year>/<slug>/<day>/`. How they are synced is set by **Backpack sync** in Settings:

| Mode | What happens |
|---|---|
| `off` (default) | Files stay on this machine only; `attachments/` is git-ignored so it doesn't weigh down the repo. |
| `git` | Files are committed and synced with your Markdown files. Fine for a few small files. |
| `cloud` | Files stay out of git and are synced with an **S3 bucket** — via Smart Sync or ⛅ Push / ⛅ Pull Backpack. |

Cloud sync copies both ways and never deletes: pull downloads what's missing locally (never overwriting a local file), push uploads what's new or changed (by size and MD5). Object keys mirror the local layout (`<prefix>/attachments/<year>/<slug>/<day>/<file>`), so the bucket stays browsable.

For `cloud` mode you need a bucket URL (`s3://bucket/optional-prefix/`), region, and an access key ID + secret. Use a dedicated IAM user restricted to that bucket (list, get, put) rather than your own credentials. TripTale talks to S3 directly (no AWS SDK, no extra tools). The keys are stored in `settings.yml`, which lives outside the data repo and is never committed. Background and design notes: [docs/33a_attachments_mvp.md](../33a_attachments_mvp.md).

---

Back to the [guides](README.md) · [project README](../../README.md)
