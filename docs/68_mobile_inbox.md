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
   package per the MVP decisions below.

## MVP decisions (first real build)

Trimmed further than the spike plan above so there's something to actually test on-trip, not
just a design that's complete on paper. Deliberately skips the `SmartSyncDialog`/`SyncRow`
polish and the frontmatter-key question below — both are fast-follows once the basic loop has
proven itself over a few real days of use.

- **Settings** — `telegramBotToken` / `telegramChatId` on `AppSettings` (blank = feature off,
  same "optional until filled in" pattern as the WordPress fields), surfaced in Edit Settings.
  Single chat ID only; messages from any other chat are ignored (logged, not surfaced as an
  error) — no multi-sender allowlist in v1.
- **Offset persistence** — one new field (`lastTelegramUpdateId` or similar) next to
  `lastTripPath`/`lastEntryDate` in `.state.yml`, loaded/saved via `MarkdownStore`, so a restart
  doesn't re-fetch history.
- **`inbox.TelegramInboxClient`** (no JavaFX, same boundary rule as `sync`/`storage`) — a single
  `poll()` call hits `getUpdates?offset=...` once via `java.net.http` + Jackson (no background
  thread, no timer), filters to the configured chat ID, and writes each new text message as a
  timestamped file into `<dataDir>/inbox/` (gitignored, like `attachments/`).
- **Trigger: manual only.** A "📥 Check Inbox" menu/toolbar action runs `poll()` on a daemon
  thread (`Task`, same shape as `ConnectivityService.checkTask`) when the user asks for it — no
  auto-check on launch, no `init()`/`start()` sequencing to get right. The inbox is simply stale
  until the user clicks, same tolerance as git fetch/pull today.
- **`ui/dialog/InboxDialog`** — a plain `ListView` over files in `inbox/`, one shared date picker
  defaulted from the selected item's timestamp, and two per-item actions: **Attach** (appends
  the text under a `## Inbox` heading at the end of that day's body, then
  `saveTheDayIfPossible()`) or **Discard**. This already *is* the "separate inbox to pick and
  distribute from" — no extra staging layer needed on top.
- **Processed files are moved, not deleted**, for both actions (`inbox/processed/`,
  `inbox/discarded/` or similar) until the loop has proven itself in practice — cheap insurance
  against a wrong attach or a dialog bug losing a message during initial real-world testing.
  Revisit (actually delete) once the feature is trusted.
- **Landing spot is appended body text, not a frontmatter key** for v1 — simplest to implement,
  visible and editable like any other tale content, survives exports unless wrapped in a
  private section (todo 48). A structured frontmatter field remains a real option for a later
  iteration once it's clear what shape repeated use actually wants.

## Non-goals (v1)

- Coordinates/location messages — fast-follow once text works end-to-end.
- Photos — fast-follow; will need to reuse `attachments.AttachmentsDir` once it lands. Done in
  todo 74, see `docs/74_telegram_inbox_photos.md`.
- Any transport other than Telegram — explicitly deferred until the spike either succeeds or
  fails.
- Webhooks/always-on server — polling only, matching "TripTale is not meant to run as an app";
  the inbox is simply stale between launches, same tolerance as git fetch/pull today.

## Todo 73 — raw payload capture

v1 kept only `text` from a Telegram update; nothing else (sender, message id, and — crucially —
the full shape of non-text updates like photos/locations) survived `poll()`. That made it
impossible to debug a failed capture after the fact, and impossible to design the photo/location
fast-follows above against real data instead of guessing Telegram's JSON shape from docs.

Options considered: (A) a same-stem `.json` sidecar next to the `.txt` for items that already
become pending; (B) replacing `.txt` with `.json` as the primary format; (C) also capturing raw
JSON for updates that don't become pending items at all (non-text content from the configured
chat, currently silently dropped before anything is written).

**Decision: A + C**, least invasive to the working Attach/Discard flow (`InboxItem`/`InboxDialog`
untouched; `listPending()` already globs `*.txt` only, so sidecars are naturally invisible) while
still covering both the debugging and the "explore future data" goals:

- `InboxDir.writeItem(timestamp, text, rawJson)` writes the raw Telegram `update` node as a
  same-stem `.json` sidecar next to the `.txt`, pretty-printed, best-effort (never fails the
  poll if serialization breaks).
- `InboxDir.writeRawCapture(timestamp, updateId, rawJson)` captures non-text updates from the
  *configured chat only* (photos, locations, …) into `inbox/raw/`, named by timestamp +
  Telegram's `update_id` (several can share a second). Updates from other chats, or non-`message`
  updates (`edited_message`/`channel_post`), are still just skipped — not relevant, and
  potentially other people's unrelated chat content.
- `InboxDir.markProcessed`/`markDiscarded` move the sidecar along with its `.txt` so the raw
  payload a message was attached/discarded from stays correlated with its outcome.
- `inbox/raw/` is covered by the same unconditional `inbox/*` gitignore as everything else —
  no separate retention policy yet; revisit once it's clear how much accumulates in practice.

## Todo 75 — sender metadata + possibilities for more than one family member

Up to here, `telegramChatId` is a single chat and every captured item is implicitly "from the
family." Todo 75 made the sender visible — `InboxItem` now reads `message_id` and
`from.first_name [last_name]` back out of the `.json` sidecar (todo 73 captured it but nothing
read it back), shown in the Inbox dialog as e.g. `2026-10-26 17:45 · #23 · Tima Fe` — but it
doesn't yet change *who is allowed to send*. That's still exactly one chat id, same as v1. Two
ways to actually get more than one person capturing, not decided between yet:

### A. A shared Telegram group (works today, no code change)

1. Create (or reuse) a Telegram group and add the bot to it alongside the family members who
   should be able to capture.
2. **Disable the bot's Privacy Mode** in @BotFather first (`/mybots` → bot → *Bot Settings* →
   *Group Privacy* → *Turn off*) — by default a bot in a group only receives `/commands` and
   `@mentions`, not plain text, which would silently break captures.
3. Set `telegramChatId` to the **group's** chat id instead of a personal one — groups/supergroups
   have a negative id (e.g. `-1001234567890`). `TelegramInboxClient.poll()` compares chat ids as
   plain strings (`msgChatId.equals(chatId.trim())`), so a negative group id works exactly like a
   positive personal one; no code change needed.
4. From then on, anyone in that group just sends text/photos in their own Telegram app as usual.
   The sender metadata from todo 75 is what tells them apart afterwards.

Trade-off: it's one shared inbox for the whole family, no per-person settings, no allowlist to
maintain — but anyone in the group can see everyone else's captures as normal group chat history
(acceptable for "it's family," possibly not for a looser group of travelers).

**Privacy mode, what it actually changes:** turning it off only changes what *your own bot*
receives from Telegram — every group member already sees every message regardless, so nothing is
exposed to a new party. The real privacy boundary in this design is **Attach vs. Discard**, not
the Telegram toggle:

- Every captured message (and any non-text update, via `inbox/raw/`) lands in `<dataDir>/inbox/`
  on the laptop running TripTale only — `InboxDir`'s `.gitignore` covers the whole folder
  unconditionally, so pending/processed/discarded/raw content is never committed or pushed,
  privacy mode on or off.
- Only **➕ Add** moves a specific item's text into that day's tracked markdown body (and a photo
  into `attachments/`, which *is* subject to sync/export) — that's the only path from "captured"
  to "part of the shared/exported diary." **🗑 Dump** just moves the files to
  `inbox/discarded/`, still local, still gitignored, not instant deletion but never synced either.

What *does* change with privacy mode off, worth being deliberate about:

- **Noise/scope creep on disk** — every message in the group, trip-related or not, becomes a
  plaintext file under `inbox/` (even if immediately Dumped). Mitigation: use a **dedicated
  capture-only group**, not an existing general family chat, so only trip-related chatter ever
  reaches the bot.
- **Sender identity travels with every capture** (todo 75) — first/last name and Telegram user id
  are written to disk for discarded items too, with no retention/cleanup policy yet for
  `inbox/raw/` (see todo 73 above).
- **The bot token is a bearer credential** — whoever has it can read everything sent to that bot.
  It lives in `settings.yml` under the settings dir (outside the git-tracked data dir, so never
  pushed), but it's still plaintext on disk — treat it like a password.
- **No per-member filtering** — the Bot API can't let only some group members post; to exclude
  someone, remove them from the group rather than relying on bot config.

### B. Per-person 1:1 chats with the bot (needs a code change, not built)

Each family member messaging the bot privately gets their own distinct `chat.id` with the bot.
`TelegramInboxClient.poll()` only accepts one configured `telegramChatId` today — a message from
any other chat is logged and dropped, matching "no multi-sender allowlist in v1" from the MVP
decisions above. Supporting this would mean turning `telegramChatId` into a small allowlist
(comma-separated ids, most likely) and keeping the per-message `from` name (todo 75 already
captures it) as the only way to tell captures apart, since there'd be no shared chat history to
fall back on. More private than option A, more to build and maintain.

**Not decided yet** — option A needs no further work and is the natural next thing to try with
the family; option B stays here as the documented fallback if group visibility turns out to be
unwanted.

