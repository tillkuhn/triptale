package net.timafe.triptale.map;

import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.impressions.ImageFilter;
import net.timafe.triptale.impressions.ImpressionSource;
import net.timafe.triptale.impressions.ImpressionsService;
import net.timafe.triptale.storage.ExifInfo;
import net.timafe.triptale.storage.ExifReader;
import net.timafe.triptale.util.Coordinates;
import net.timafe.triptale.util.Coordinates.LatLon;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Photo locations for Map Assistant (todo 54): the EXIF GPS of the backpack faves of a range of
 * days. Only the backpack, never the photo library, and only faves — without a fave filter there
 * are no photo locations at all.
 *
 * <p>No JavaFX imports (package boundary rule).
 */
public class PhotoLocations {

    private final ImpressionsService impressions;
    private final ExifReader exifReader;

    public PhotoLocations(ImpressionsService impressions, ExifReader exifReader) {
        this.impressions = impressions;
        this.exifReader = exifReader;
    }

    /** True if a fave filter is configured, i.e. there can be photo locations at all. */
    public boolean available() {
        return !impressions.faveFilter().isEmpty();
    }

    /** The backpack faves of the days {@code from}..{@code to}, in date order. Cheap: no EXIF reads. */
    public List<Path> faves(Trip trip, LocalDate from, LocalDate to) {
        ImageFilter faves = impressions.faveFilter();
        if (faves.isEmpty() || trip == null || from == null || to == null) return List.of();
        List<Path> result = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            result.addAll(faves.apply(impressions.images(ImpressionSource.TRIP_ATTACHMENTS, trip, d)));
        }
        return result;
    }

    /**
     * The GPS locations of the images, in their order; images without (or with nonsense) GPS are
     * skipped. Reads EXIF for every image, so keep it off the FX thread.
     */
    public List<LatLon> locations(List<Path> images) {
        return images.stream()
                .map(exifReader::read)
                .map(PhotoLocations::location)
                .filter(Objects::nonNull)
                .toList();
    }

    private static LatLon location(ExifInfo info) {
        if (!info.hasLocation()) return null;
        double lat = info.latitude();
        double lon = info.longitude();
        // 0,0 ("Null Island") is what a camera without a fix writes, not a real location.
        if (!Coordinates.isValid(lat, lon) || (lat == 0.0 && lon == 0.0)) return null;
        return Coordinates.rounded(lat, lon);
    }
}
