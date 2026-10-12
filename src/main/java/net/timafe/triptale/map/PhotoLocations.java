package net.timafe.triptale.map;

import net.timafe.triptale.domain.Trip;
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
 * Photo locations for Map Assistant (todo 54): the EXIF GPS of the backpack images of a range of
 * days. Only the backpack, never the photo library; the backpack is already a curated pick, so
 * the fave filter doesn't apply (backpack copies like {@code _mini.jpg} rarely match it anyway).
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

    /** The backpack images of the days {@code from}..{@code to}, in date order. Cheap: no EXIF reads. */
    public List<Path> images(Trip trip, LocalDate from, LocalDate to) {
        if (trip == null || from == null || to == null) return List.of();
        List<Path> result = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            result.addAll(impressions.images(ImpressionSource.TRIP_ATTACHMENTS, trip, d));
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
