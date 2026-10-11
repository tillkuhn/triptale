package net.timafe.triptale.map;

import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.util.Coordinates.LatLon;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * What Map Assistant puts on screen for a range of a trip's entries (todo 54): one start point
 * per day, the last day's stop point, and optionally photo locations. {@code photosFound} is how
 * many photo locations there were before {@link #withPhotos thinning}, so the header can say
 * "60 of 212".
 *
 * <p>No JavaFX imports (package boundary rule).
 */
public record MapPoints(List<LatLon> starts, LatLon stop, List<LatLon> photos, int photosFound) {

    public MapPoints {
        starts = List.copyOf(starts);
        photos = List.copyOf(photos);
    }

    /**
     * Start points of the entries (in date order) plus the stop point of the last one only — a
     * daily stop is usually the next day's start, so a single-day range gives start → stop.
     */
    public static MapPoints of(List<DiaryEntry> entries) {
        List<LatLon> starts = new ArrayList<>();
        for (DiaryEntry e : entries) {
            if (e.startLat() != null && e.startLon() != null) starts.add(new LatLon(e.startLat(), e.startLon()));
        }
        LatLon stop = null;
        if (!entries.isEmpty()) {
            DiaryEntry last = entries.getLast();
            if (last.stopLat() != null && last.stopLon() != null) stop = new LatLon(last.stopLat(), last.stopLon());
        }
        return new MapPoints(starts, stop, List.of(), 0);
    }

    /** These day points plus the photo locations, sampled evenly down to {@code maxPhotos}. */
    public MapPoints withPhotos(List<LatLon> photoLocations, int maxPhotos) {
        return new MapPoints(starts, stop, sampleEvenly(photoLocations, maxPhotos), photoLocations.size());
    }

    /** Start pins plus the stop pin; these are always plotted, photos only fill what's left. */
    public int dayPinCount() {
        return starts.size() + (stop == null ? 0 : 1);
    }

    public boolean isEmpty() {
        return dayPinCount() == 0 && photos.isEmpty();
    }

    /** Every point, photos first, then starts, then the stop (later ones are drawn on top). */
    public List<LatLon> all() {
        return Stream.concat(Stream.concat(photos.stream(), starts.stream()), Stream.ofNullable(stop)).toList();
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
