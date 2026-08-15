package net.timafe.triptale.export;

/**
 * Controls which (if any) per-day image grid is embedded when exporting a trip as HTML.
 *
 * <ul>
 *     <li>{@link #NONE} — no images, no markers.</li>
 *     <li>{@link #FAVES} — images resolved from {@code impressionsFaveFilePattern}.</li>
 *     <li>{@link #ALL} — images resolved from {@code impressionsFilePattern}.</li>
 * </ul>
 *
 * <p>If the relevant pattern preference isn't configured (or matches nothing), the grid is
 * simply omitted for that day — this is treated as "no images", not an error.
 */
public enum ImpressionsMode {
    NONE,
    FAVES,
    ALL
}
