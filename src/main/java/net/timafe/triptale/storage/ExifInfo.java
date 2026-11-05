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
                        LocalDateTime captureTime, Integer orientation) {

    private static final ExifInfo EMPTY = new ExifInfo(null, null, null, null, null, null, null, null, null);

    public static ExifInfo empty() {
        return EMPTY;
    }

    public boolean hasCameraData() {
        return cameraModel != null || aperture != null || exposureTime != null || iso != null;
    }

    /**
     * Width / height as displayed, i.e. with the EXIF orientation applied (values 5-8 are rotated
     * by 90 degrees, so width and height swap), or {@code null} when the dimensions are unknown.
     */
    public Double displayAspect() {
        if (dimensions == null) return null;
        String[] wh = dimensions.split("×");
        if (wh.length != 2) return null;
        try {
            double w = Integer.parseInt(wh[0].trim());
            double h = Integer.parseInt(wh[1].trim());
            if (w <= 0 || h <= 0) return null;
            boolean rotated = orientation != null && orientation >= 5 && orientation <= 8;
            return rotated ? h / w : w / h;
        } catch (NumberFormatException e) {
            return null;
        }
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
