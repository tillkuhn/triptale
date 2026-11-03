package net.timafe.triptale.map;

/**
 * Aspect ratio of a Sgt. Mapper render (todo 82). The rendered file is exactly the size shown
 * on screen, so {@link #fit} decides both: each format takes as much of the available area as
 * it can, its long edge capped at {@link #MAX_LONG_EDGE}.
 *
 * <p>No JavaFX imports (package boundary rule).
 */
public enum MapFormat {
    LANDSCAPE("3:2 Landscape", 3, 2),
    SQUARE("1:1 Square", 1, 1),
    PORTRAIT("2:3 Portrait", 2, 3);

    public static final MapFormat DEFAULT = LANDSCAPE;
    /** The long edge of a map when the screen has room for it (as before todo 82: 960×640). */
    public static final int MAX_LONG_EDGE = 960;
    /** Never shrink below half the full size, even on a tiny screen — it'd stop being useful. */
    private static final double MIN_SCALE = 0.5;

    private final String label;
    private final int ratioWidth;
    private final int ratioHeight;

    MapFormat(String label, int ratioWidth, int ratioHeight) {
        this.label = label;
        this.ratioWidth = ratioWidth;
        this.ratioHeight = ratioHeight;
    }

    /** The full size: the long edge is {@link #MAX_LONG_EDGE}. Returns {@code {width, height}}. */
    public int[] fullSize() {
        int longRatio = Math.max(ratioWidth, ratioHeight);
        return new int[] {MAX_LONG_EDGE * ratioWidth / longRatio, MAX_LONG_EDGE * ratioHeight / longRatio};
    }

    /**
     * The largest size in this format that fits {@code availableWidth}×{@code availableHeight},
     * capped at {@link #fullSize()} and floored at half of it. Returns {@code {width, height}}.
     */
    public int[] fit(double availableWidth, double availableHeight) {
        int[] full = fullSize();
        double scale = Math.max(MIN_SCALE, Math.min(1.0, Math.min(availableWidth / full[0], availableHeight / full[1])));
        return new int[] {(int) Math.round(full[0] * scale), (int) Math.round(full[1] * scale)};
    }

    @Override public String toString() {
        return label;
    }
}
