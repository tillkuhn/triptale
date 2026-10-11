# 54 Map Assistant

Replaces the "🗺 Trip Map" button and its "🗺 Cartographer" dialog. "Cartographer" sounded too
technical; the button, menu item and dialog title are now all **🗺 Map Assistant**.

## Why

The old dialog always plotted every start point of the whole trip. For a trip that starts on
another continent (flight from home, then the actual journey) the map zooms out to half the
globe and the interesting part is a cluster of overlapping pins (see the Thailand/Malaysia
example: one pin in the Netherlands, eight squeezed together in Southeast Asia).

## Entry points

| Where                                      | Default range                     |
|--------------------------------------------|-----------------------------------|
| `🗺 Map Assistant` button in the entry form | the current day only              |
| My Trips → `🗺 Map Assistant…` (new)        | the whole trip (first to last day) |

Same pattern as Export Tale / Export Diary. If the current day has no saved entry, the button
falls back to the nearest saved entry. A trip without entries still opens the dialog; it just
has nothing to put on screen.

## Dialog

```
🗺 Map Assistant
┌──────────────────────────────────────────────────────────────┐
│ Thailand Malaysia Sumatra Railtrip '26                        │
│ 📍 3 start points · 🏁 stop point · 📷 12 photo locations      │
├──────────────────────────────────────────────────────────────┤
│ From: [Day 4: Bukittinggi ▾]  To: [Day 6: … ▾]               │
│ [ ] 📷 Include photo locations                                │
│ ┌──────────────────────────────────────────────────────────┐ │
│ │                                                          │ │
│ │     placeholder until "On Screen" is clicked, then       │ │
│ │     the Mapbox static map (960×640)                      │ │
│ │                                                          │ │
│ └──────────────────────────────────────────────────────────┘ │
│                                     [🖥 On Screen]  [Close]   │
└──────────────────────────────────────────────────────────────┘
```

- **From / To** combos as in Export Diary (`Day N: Title` labels); moving one end past the
  other drags the other along.
- **On demand:** opening the dialog makes no Mapbox request. The map area shows a placeholder
  ("Choose a range, then put it on screen") until **🖥 On Screen** (a Star Trek nod) is
  clicked. Changing the range or the checkbox afterwards marks the shown map as stale (the
  placeholder text returns over a dimmed map) until the next On Screen. The button is a
  repeatable action (event filter + `consume()`), it doesn't close the dialog.
- **Size:** fixed 960×640 map area (was 640×480), still requested `@2x`.

## What's plotted

| Kind  | Source                                              | Pin                          |
|-------|-----------------------------------------------------|------------------------------|
| Start | `start_lat`/`start_lon` of every entry in range      | red `pin-s+f74e4e`           |
| Stop  | `stop_lat`/`stop_lon` of the **last** entry in range | teal `pin-s+3bb2d0`          |
| Photo | EXIF GPS of backpack faves of the days in range      | yellow `pin-s-camera+f5c242` |

- **Stop:** only the last day's stop, as before — a daily stop is usually the next day's
  start. With a single-day range this gives exactly start → stop for that day.
- **No route line**, pins only.
- **Photos:** off by default (`📷 Include photo locations` checkbox, not remembered). Only
  backpack images (`attachments/<year>/<slug>/<date>/`) that pass the base filter **and** the
  fave filter (`impressionsFaveFilter`), and carry EXIF GPS. No photo-library scan. If no fave
  filter is configured, the checkbox is disabled with "(no fave pattern defined)", like Export
  Diary's "Faves only".
- **Nothing to plot:** whatever points exist are rendered, photos alone are enough. With no
  points at all the placeholder says "Nothing to put on screen for this range", plus a hint to
  tick the checkbox when it's unticked and the range has faves (counted without reading EXIF,
  so they may turn out to have no GPS).

## Too many pins

A Mapbox static URL must stay under ~8 KB (≈ 150 small pins). Day pins (starts + stop) are
always kept. Photo pins are thinned to the remaining budget by sampling evenly across the
date-ordered list (so every part of the range keeps some coverage), and the header says so:
`📷 60 of 212 photo locations`.

## Code

- `map.MapPoints` (new package `map`, no JavaFX): collects the points for a trip and range —
  starts, last stop, and (optionally) fave photo locations via `ImpressionsService` +
  `ExifReader` — and thins photos to a budget. Unit-tested.
- `ui.MapboxService`: takes the three point lists; adds the camera pin type.
- `ui.dialog.TripMapDialog` → `ui.dialog.MapAssistantDialog`: owns the range combos,
  checkbox, placeholder and On Screen button; loads entries itself (like `ExportDiaryDialog`).
  EXIF reads run off the FX thread.
- `MainController`: `onShowTripMap` → `onMapAssistant` (current day) plus
  `onMapAssistantTrip` (whole trip, menu).
