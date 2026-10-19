# Todo 68 — Mobile inbox: capture on the phone, organize later in TripTale

## Problem

TripTale is explicitly not meant to run as a phone app, but while traveling the moment to
capture a thought, a photo, or a spot worth remembering happens on the phone, not at the
laptop. Today that means either remembering it until the laptop is open, or jotting it
somewhere else and manually copying it in later — both lossy. The goal is a lightweight
**capture → inbox → organize** split: push a quick text (coordinates/photos are explicitly
out of scope for v1, see Non-goals) from Android into some inbox, and let TripTale show pending
items for the user to attach to the day they belong to (usually, but not always, "today") or to
a different entry entirely — without building and maintaining a native Android app.

## Hard constraint

**No app install for the family.** This isn't the user's personal tool only — family members
capturing notes on a trip must not need to install anything new. Whatever the mechanism, it has
to ride on an app that's already on everyone's phone.

## Options considered

All four options below satisfy the "no new app" constraint. They differ in how much needs to be
built on the receiving side, in capture UX quality, and in long-term stability.

| | Receiving mechanism | Build effort | Capture UX | Risk |
|---|---|---|---|---|
| **Telegram** | Official Bot API, plain long-polling (`getUpdates`) | Smallest — a `java.net.http` client in the same style as `weather.OpenMeteoClient`/`map.PlaceLookup`, no public endpoint needed | Best: text, photo, live-location all native share targets in one bot chat | Low — official, stable, free API |
| **WhatsApp** | Official Cloud API, **webhook only** (no polling option) | Needs a public-facing receiver for the webhook (e.g. a Cloudflare Worker forwarding into the existing S3 bucket) — more moving parts than Telegram | Good, same share targets as Telegram, but it's a 1:1-with-a-business-number model, not "listen in on the existing family group" | Low on paper (official), but dev-mode without Meta App Review caps you at 5 test numbers — fine for a family, but a hard ceiling |
| **Signal** | No official bot API — `signal-cli` (unofficial but mature, implements the real protocol via libsignal), linked as a secondary device, run as a local JSON-RPC daemon | Heaviest — a native libsignal (Rust/JNI) dependency and a long-running local process, not just an HTTP call | Good (text/photo/location via normal Signal UX) | Low misuse risk, but it's an unofficial client and a real runtime dependency to maintain |
| **Google stuff** | Forms→Sheets (official API) for text, Photos shared album (official Library API) for pics | Two separate integrations instead of one inbox; OAuth/service-account setup for both | Splits across two tools — a form isn't a chat, a shared album isn't a form; clunkiest UX of the four | Lowest platform risk, enterprise-grade stable API |

Also considered and rejected up front:

- **Syncthing + a plain-text note app (e.g. Markor)** — no third-party server at all, most
  aligned with "own your data," but it *does* require installing two new apps (Syncthing, a
  note app) on every family member's phone, which violates the hard constraint above. Worth
  keeping in mind as the privacy-maximal fallback if the family ever asks for it specifically.
- **PWA + presigned S3 PUT to the existing bucket** — reuses `sync.S3Client`/`ObjectStore`
  infra, but Android's Web Share Target support for photos/location is inconsistent across
  browsers, and "add to home screen" is still a step family members have to be walked through.
- **Termux + Termux:Share** — scriptable but not remotely family-friendly.

## Decision

**Try Telegram first.** It's the cheapest to prototype by a wide margin (one polling HTTP
client, no public endpoint, no App Review, no native dependency) and the capture UX is the best
of the four. If a hands-on spike shows it's genuinely as simple as it looks, ship it; if the
family balks at a bot contact or anything about it turns out more annoying in practice than on
paper, fall back to WhatsApp's dev-mode Cloud API (same family, same 5-tester cap is plenty,
extra cost is a small webhook receiver) without having sunk much effort.

**Leave the other options open** — this doc is not a one-way door. WhatsApp, Signal, and the
Google Forms/Photos combo all remain viable if Telegram doesn't pan out; none of them require
throwing away the "inbox folder + pending-items dialog" half of the design below, only the
capture transport changes.

## v1 scope (text only)

Coordinates and photos are real future needs but add real complexity (location-message parsing,
media download, attachment-folder wiring) that aren't needed to validate the core idea. **v1 is
text messages only**: send the bot a thought, it shows up as a pending inbox item. Everything
else is a fast-follow once the basic loop is proven out.

## Spike plan (throwaway-friendly)

1. Create a bot via @BotFather (a few minutes, no cost). Message it from a phone to get a chat
   ID.
2. A small standalone `main()` (not wired into Spring yet) that polls
   `https://api.telegram.org/bot<token>/getUpdates?offset=<n>` with `java.net.http`, parses the
   JSON (Jackson, already a dependency), and prints incoming text messages with their timestamp
   and chat ID.
3. If that round-trip feels as trivial as it looks on paper, promote it into a real `inbox`
   package (no JavaFX imports, same boundary rule as `sync`/`storage`):
   - `inbox.TelegramInboxClient` — polls `getUpdates`, tracks the last-seen `update_id` (persist
     it somewhere small, e.g. alongside `.state.yml`, so a restart doesn't re-fetch history),
     writes each new text message as a file into a new `<dataDir>/inbox/` folder (gitignored,
     like `attachments/`), named by timestamp.
   - A pending-items dialog (`ui/dialog/InboxDialog`, following the `SmartSyncDialog`/`SyncRow`
     pattern) lists files in `inbox/`, each defaulting to "attach to the day matching its
     timestamp" with an override to pick a different date/entry, and a per-item
     accept/dismiss action. Confirms via the existing `Dialogs.addSaveDayButtons` idiom and
     calls `saveTheDayIfPossible()` on accept, consistent with every other frontmatter-editing
     dialog.
   - Where an accepted note lands in the entry (its own frontmatter key vs. appended to the
     body) is an open question for the real implementation — resolve it when this is promoted,
     not in the spike.

## Non-goals (v1)

- Coordinates/location messages — fast-follow once text works end-to-end.
- Photos — fast-follow; will need to reuse `attachments.AttachmentsDir` once it lands.
- Any transport other than Telegram — explicitly deferred until the spike either succeeds or
  fails.
- Webhooks/always-on server — polling only, matching "TripTale is not meant to run as an app";
  the inbox is simply stale between launches, same tolerance as git fetch/pull today.
