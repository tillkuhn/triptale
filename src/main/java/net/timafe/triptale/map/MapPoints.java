package net.timafe.triptale.map;

import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.map.PhotoLocations.Photo;
import net.timafe.triptale.util.Coordinates.LatLon;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * What Sgt. Mapper puts on screen for a range of a trip's entries (todo 54): one start point
 * per day, the last day's stop point, optionally photo locations and optionally a route line
 * (todo 78). {@code photosFound} is how many photo locations there were before the renderer
 * thinned them ({@link OsmMapRenderer}), so the header can say "60 of 212".
 *
 * <p>{@code route} is the chronological path through the range: each day's start, its photos
 * with a capture time (in capture order), its stop, then the next day. Empty when no line is
 * wanted. Unlike the pins it includes every day's stop, not just the last one.
 *
 * <p>No JavaFX imports (package boundary rule).
 */
public record MapPoints(List<LatLon> starts, LatLon stop, List<LatLon> photos, int photosFound, List<LatLon> route) {

    public MapPoints {
        starts = List.copyOf(starts);
        photos = List.copyOf(photos);
        route = List.copyOf(route);
    }

    /**
     * Start points of the entries (in date order) plus the stop point of the last one only — a
     * daily stop is usually the next day's start, so a single-day range gives start → stop.
     */
    public static MapPoints of(List<DiaryEntry> entries) {
        return of(entries, Map.of(), false);
    }

    /**
     * The day points of {@code entries} plus the photos of {@code photosByDay} (each day already
     * sorted by capture time, see {@link PhotoLocations#photosByDay}) and, if {@code withRoute},
     * the route line through all of them.
     */
    public static MapPoints of(List<DiaryEntry> entries, Map<LocalDate, List<Photo>> photosByDay, boolean withRoute) {
        List<LatLon> starts = new ArrayList<>();
        Map<LocalDate, DiaryEntry> byDate = new TreeMap<>();
        for (DiaryEntry e : entries) {
            LatLon start = start(e);
            if (start != null) starts.add(start);
            byDate.put(e.date(), e);
        }
        LatLon stop = entries.isEmpty() ? null : stop(entries.getLast());

        List<LatLon> photos = new ArrayList<>();
        new TreeMap<>(photosByDay).values().forEach(day -> day.forEach(p -> photos.add(p.location())));

        List<LatLon> route = new ArrayList<>();
        if (withRoute) {
            TreeSet<LocalDate> days = new TreeSet<>(byDate.keySet());
            days.addAll(photosByDay.keySet());
            for (LocalDate d : days) {
                DiaryEntry e = byDate.get(d);
                if (e != null && start(e) != null) route.add(start(e));
                for (Photo p : photosByDay.getOrDefault(d, List.of())) {
                    if (p.captureTime() != null) route.add(p.location());
                }
                if (e != null && stop(e) != null) route.add(stop(e));
            }
        }
        return new MapPoints(starts, stop, photos, photos.size(), route);
    }

    private static LatLon start(DiaryEntry e) {
        return e.startLat() != null && e.startLon() != null ? new LatLon(e.startLat(), e.startLon()) : null;
    }

    private static LatLon stop(DiaryEntry e) {
        return e.stopLat() != null && e.stopLon() != null ? new LatLon(e.stopLat(), e.stopLon()) : null;
    }

    /** The same points with only {@code shown} of the photos left, as the renderer plotted them. */
    public MapPoints withPhotosShown(List<LatLon> shown) {
        return new MapPoints(starts, stop, shown, photosFound, route);
    }

    /** Start pins plus the stop pin; these are always plotted, photos only fill what's left. */
    public int dayPinCount() {
        return starts.size() + (stop == null ? 0 : 1);
    }

    public boolean isEmpty() {
        return dayPinCount() == 0 && photos.isEmpty();
    }

    /** Start pins plus the stop pin, in drawing order (the stop on top). */
    public List<LatLon> dayPins() {
        return Stream.concat(starts.stream(), Stream.ofNullable(stop)).toList();
    }

    /**
     * Every point the map must frame: photos, starts, the stop and the route (which adds the
     * intermediate days' stops).
     */
    public List<LatLon> all() {
        return Stream.of(photos.stream(), dayPins().stream(), route.stream()).flatMap(s -> s).toList();
    }

    /**
     * Up to {@code max} items spread evenly over the list (first item included), so every part
     * of an ordered list keeps some coverage; the list itself if it's short enough.
     */
    static <T> List<T> sampleEvenly(List<T> items, int max) {
        int n = items.size();
        if (n <= max) return items;
        if (max <= 0) return List.of();
        return IntStream.range(0, max).mapToObj(i -> items.get((int) ((long) i * n / max))).toList();
    }
}
