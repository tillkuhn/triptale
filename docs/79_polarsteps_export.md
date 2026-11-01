# 79 — Post diary entries to Polarsteps via API

## Question

Could TripTale export/post diary entries (or parts of them: text, photos, coordinates) directly
to a user's Polarsteps trip, the way `export.DiaryExporter` already produces Markdown/HTML/
WordPress output? Prompted by an unofficial GitHub project that looked possibly abandoned.

## Findings

Polarsteps has **no official public API** — nothing documented, no OAuth app registration, no
versioned contract. Anything that talks to Polarsteps programmatically is reverse-engineered
from the official web/mobile app's network traffic.

Searched GitHub for existing wrappers (as of 2026-11):

- **`remuzel/polarsteps-api`** — Python, 15 stars, 4 forks, last commit ~Jul 2025, with an open
  issue from Oct 2026 ("Polarsteps API now requires an additional version header") — so it's
  maintained, not abandoned, just small. Auth is via the `remember_token` browser cookie (no
  OAuth). Exposes exactly two calls:
  - `get_user_by_username` — profile, trip list, stats
  - `get_trip` — trip details by ID
  Both are **read-only** GETs. No create/update/upload endpoint for steps, photos, or text.
- `maxime/polarstepsToGpx` — reads a trip and converts to GPX, also read-only.
- `remuzel/polarsteps-mcp` — MCP server wrapping the same read-only client for LLM tool use.
- No repo found anywhere exposing a working "create step" / "post entry" call.

## Why not pursued

1. **No write endpoint exists in any known project.** Building one means starting from zero:
   capturing the official mobile app's create-step request (headers, auth, multipart photo
   upload shape, trip/step JSON schema) via a proxy, with no guarantee it's stable or that the
   session/device fingerprinting Polarsteps' app may do would even accept a non-app client.
2. **Cookie-only auth** (`remember_token`), not a scoped token — a TripTale integration would
   need the user to extract their session cookie manually, which is fragile and a bad UX/security
   fit compared to the app's existing git-based sync model.
3. **ToS / stability risk** — explicitly "personal use only" in the existing wrapper's own
   disclaimer; Polarsteps can change or block this at any time, and the account doing it risks
   suspension. Not something to build a feature around.
4. Doesn't fit the existing export architecture cleanly either: `export.DiaryExporter`'s
   exporters are pure local-file generators (Markdown/HTML/WordPress) with no network/auth
   concerns; a Polarsteps pusher would be a different kind of component (network client +
   credential storage) bolted onto a system that otherwise treats git push/pull as the only
   "goes over the network" concern (`sync.BucketSyncer`/`S3Client`, `GitService`).

## Revisit conditions

Worth reopening only if:

- A reverse-engineered **write** endpoint shows up in a maintained project (not just read), or
- Polarsteps ships an official public API with proper OAuth/token scoping.

Until then, exporting to Polarsteps is out of scope.
