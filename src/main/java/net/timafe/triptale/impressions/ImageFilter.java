package net.timafe.triptale.impressions;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * A space-separated list of filename globs such as {@code "*.jpg *.jpeg *.png"} (the
 * {@code impressionsBaseFilter} / {@code impressionsFaveFilter} settings). Matches the filename
 * only, case-insensitively — {@code *.jpg} also matches {@code IMG_0431.JPG}.
 */
public final class ImageFilter {

    private final String spec;
    private final List<PathMatcher> matchers;

    private ImageFilter(String spec, List<PathMatcher> matchers) {
        this.spec = spec;
        this.matchers = matchers;
    }

    /** Parses a space-separated glob list; null or blank gives an {@link #isEmpty() empty} filter. */
    public static ImageFilter parse(String spec) {
        String s = spec == null ? "" : spec.strip();
        List<PathMatcher> matchers = s.isEmpty() ? List.of() : Arrays.stream(s.split("\\s+"))
                .map(glob -> FileSystems.getDefault().getPathMatcher("glob:" + glob.toLowerCase(Locale.ROOT)))
                .toList();
        return new ImageFilter(s, matchers);
    }

    /** True if no glob is configured. */
    public boolean isEmpty() {
        return matchers.isEmpty();
    }

    /** True if the filename matches any glob; an empty filter matches everything. */
    public boolean matches(Path file) {
        if (matchers.isEmpty()) return true;
        Path name = Path.of(file.getFileName().toString().toLowerCase(Locale.ROOT));
        return matchers.stream().anyMatch(m -> m.matches(name));
    }

    /** The matching files, in the given order. */
    public List<Path> apply(List<Path> files) {
        return matchers.isEmpty() ? files : files.stream().filter(this::matches).toList();
    }

    @Override
    public String toString() {
        return spec;
    }
}
