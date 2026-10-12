package net.timafe.triptale.storage;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Plain DTO holding the subset of EXIF metadata the UI cares about for the impressions
 * viewer: camera model, aperture, exposure time, GPS coordinates, pixel dimensions, and the
 * capture time ({@code DateTimeOriginal}, camera-local wall-clock time, no zone).
 *
 * <p>Any field may be {@code null} when the source image doesn't carry that tag (or has no
 * EXIF data at all). No JavaFX dependency — kept in the {@code storage} package per the
 * project's package boundary rule.
 */
public record ExifInfo(String cameraModel, String aperture, String exposureTime, String iso,
                        Double latitude, Double longitude, String dimensions,
                        LocalDateTime captureTime) {

    private static final ExifInfo EMPTY = new ExifInfo(null, null, null, null, null, null, null, null);

    public static ExifInfo empty() {
        return EMPTY;
    }

    public boolean hasCameraData() {
        return cameraModel != null || aperture != null || exposureTime != null || iso != null;
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }

    /** Google Maps pin-drop URL for the coordinates, or {@code null} if no location is available. */
    public String mapsUrl() {
        if (!hasLocation()) return null;
        return String.format(Locale.ROOT, "https://www.google.com/maps?q=%s,%s", latitude, longitude);
    }
}
