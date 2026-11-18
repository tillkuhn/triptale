# Trips & totals 🚴

A trip has a **default activity** (cycling, bikepacking, hiking, …). New days start with it, and each day can still override it, e.g. for a train or ferry day.

## Track Totals

Plain totals mean little on a mixed trip: two bike days of 50 km plus one 1000 km train ride add up to 1100 km. Tick **Track Totals** next to the default activity (New Trip or Trip Details) and totals count **only the days with that activity**.

The checkbox is disabled while no activity is set. If you clear the activity later, your choice is remembered and applies again once you pick one.

Where the totals show up:

- **Editor:** above the tale, after the word count, e.g. `Σ 110 km of Bikepacking so far (140 m up)`. It sums the default-activity days up to the selected day, and only shows on days with that activity.
- **Trip Details:** one line, e.g. `Total (Bikepacking): 412.6 km, 4605 m (avg 68.8 km / active day, 6 days)`, or a hint while tracking is off.
- **Export:** the trip header reads `Distance: 412.6 km Bikepacking, 4605 m climbed` (`Distanz: 412,6 km Bikepacking, 4605 m bergauf` in German). A tracked trip with no recorded distance shows `0 km`, as a reminder to enter numbers or switch tracking off. Without tracking, the header sums all days as before.

It's stored as `track_totals: true` in the trip's `README.md`. Design notes: [docs/96_trip_totals_track_totals_per_default_activity.md](../96_trip_totals_track_totals_per_default_activity.md).

---

Back to the [guides](README.md) · [project README](../../README.md)
