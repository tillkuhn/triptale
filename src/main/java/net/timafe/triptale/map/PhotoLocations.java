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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Photo locations for Map Assistant (todo 54): the EXIF GPS of the backpack images of a range of
 * days. Only the backpack, never the photo library; the backpack is already a curated pick, so
 * the fave filter doesn't apply (backpack copies like {@code _mini.jpg} rarely match it anyway).
 *
 * <p>No JavaFX imports (package boundary rule).
 */
public class PhotoLocations {

    /** A photo's location and its EXIF capture time ({@code null} if the image has none). */
    public record Photo(LatLon location, LocalDateTime captureTime) {}

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

    /**
     * The located backpack photos of the days {@code from}..{@code to}, per day in date order,
     * each day sorted by capture time (todo 78). Days without located photos are left out. Reads
     * EXIF for every image (cached), so keep it off the FX thread.
     */
    public Map<LocalDate, List<Photo>> photosByDay(Trip trip, LocalDate from, LocalDate to) {
        Map<LocalDate, List<Photo>> result = new LinkedHashMap<>();
        if (trip == null || from == null || to == null) return result;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            List<Photo> photos = photos(impressions.images(ImpressionSource.TRIP_ATTACHMENTS, trip, d));
            if (!photos.isEmpty()) result.put(d, photos);
        }
        return result;
    }

    /** The located photos among {@code images}, sorted by {@link #byCaptureTime}. */
    public List<Photo> photos(List<Path> images) {
        List<Photo> photos = new ArrayList<>();
        for (Path image : images) {
            ExifInfo info = exifReader.read(image);
            LatLon location = location(info);
            if (location != null) photos.add(new Photo(location, info.captureTime()));
        }
        return byCaptureTime(photos);
    }

    /**
     * Stable sort by capture time, photos without one last: the input order (filename order, as
     * {@code ImpressionsService.images} returns it) breaks ties, e.g. a burst within one second.
     */
    static List<Photo> byCaptureTime(List<Photo> photos) {
        return photos.stream()
                .sorted(Comparator.comparing(Photo::captureTime, Comparator.nullsLast(Comparator.naturalOrder())))
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
