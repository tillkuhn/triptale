package net.timafe.triptale.impressions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Arranges a day's images into gallery rows (todo 41): full rows of {@code columns} images of the
 * same orientation (landscape with landscape, portrait with portrait) so cropping to one row
 * height stays small, followed by the leftovers. Strict time order is not kept; rows keep their
 * rough order by the earliest image they contain.
 *
 * <p>Leftovers: a landscape and a portrait remainder that fit into one row share a mixed row;
 * otherwise portrait rows come first and an odd landscape comes last, alone on a full-width row.
 *
 * <p>Pure computation on aspect ratios (width / height as displayed, {@code null} = unknown, treated
 * as 3:2). No JavaFX imports (package boundary rule); the curator's preview draws the result.
 */
public final class GalleryLayout {

    /** Assumed for images whose dimensions are unknown. */
    static final double DEFAULT_ASPECT = 1.5;

    /** One image in a row: its index in the input list and its aspect ratio. */
    public record Cell(int index, double aspect) {}

    /**
     * One row. {@code heightRatio} is the row height relative to the gallery width, for a row
     * cropped to one common height (the mean aspect of its cells decides).
     */
    public record Row(List<Cell> cells, double heightRatio) {}

    private GalleryLayout() {}

    public static List<Row> plan(List<Double> aspects, int columns) {
        int cols = Math.max(1, columns);
        List<Cell> landscape = new ArrayList<>();
        List<Cell> portrait = new ArrayList<>();
        for (int i = 0; i < aspects.size(); i++) {
            Double a = aspects.get(i);
            Cell cell = new Cell(i, a == null || a <= 0 ? DEFAULT_ASPECT : a);
            (cell.aspect() >= 1 ? landscape : portrait).add(cell);
        }

        List<List<Cell>> full = new ArrayList<>();
        List<Cell> landscapeLeft = chunk(landscape, cols, full);
        List<Cell> portraitLeft = chunk(portrait, cols, full);
        full.sort(Comparator.comparingInt(r -> r.getFirst().index()));

        List<List<Cell>> rows = new ArrayList<>(full);
        if (!landscapeLeft.isEmpty() && !portraitLeft.isEmpty()
                && landscapeLeft.size() + portraitLeft.size() <= cols) {
            List<Cell> mixed = new ArrayList<>(portraitLeft);
            mixed.addAll(landscapeLeft);
            mixed.sort(Comparator.comparingInt(Cell::index));
            rows.add(mixed);
        } else {
            if (!portraitLeft.isEmpty()) rows.add(portraitLeft);
            if (!landscapeLeft.isEmpty()) rows.add(landscapeLeft);
        }
        return rows.stream().map(GalleryLayout::row).toList();
    }

    /** Moves complete chunks of {@code size} into {@code out}; returns the remainder (< size). */
    private static List<Cell> chunk(List<Cell> cells, int size, List<List<Cell>> out) {
        int i = 0;
        for (; i + size <= cells.size(); i += size) out.add(List.copyOf(cells.subList(i, i + size)));
        return List.copyOf(cells.subList(i, cells.size()));
    }

    private static Row row(List<Cell> cells) {
        double meanAspect = cells.stream().mapToDouble(Cell::aspect).average().orElse(DEFAULT_ASPECT);
        return new Row(cells, 1.0 / (cells.size() * meanAspect));
    }
}
