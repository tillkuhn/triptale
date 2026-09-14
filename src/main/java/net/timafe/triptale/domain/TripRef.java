package net.timafe.triptale.domain;

/**
 * Identifies a trip's location on disk: {@code trips/<year>/<slug>/}. Slugs are only unique
 * within a year, so both fields are needed to locate a trip.
 */
public record TripRef(int year, String slug) {

    public String path() {
        return year + "/" + slug;
    }

    public static TripRef parse(String path) {
        int i = path.indexOf('/');
        if (i < 0) throw new IllegalArgumentException("Malformed trip path: " + path);
        return new TripRef(Integer.parseInt(path.substring(0, i)), path.substring(i + 1));
    }
}
