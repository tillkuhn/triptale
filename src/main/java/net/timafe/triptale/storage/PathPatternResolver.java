package net.timafe.triptale.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Generic {@code ${VAR}} placeholder substitution and glob-based directory resolution for
 * settings paths (impressions patterns today, other file-locating settings such as GPX paths
 * potentially in the future).
 *
 * <p>{@link #resolveDirectory(String, Map)} substitutes the given variables into a directory
 * pattern (no trailing filename segment), then walks it one path segment at a time: a segment
 * with no glob metacharacters after substitution must match an existing directory exactly; a
 * segment containing {@code *}/{@code ?}/{@code [...]} after substitution is matched against the
 * existing directory's children (see {@link PathMatcher}, {@code glob:} syntax). Matching is
 * single-segment only — there is no recursive/{@code **} descent. An ambiguous glob match (more
 * than one existing directory matches the same segment) is resolved by taking the first
 * candidate in filename-sorted order and logging a warning, rather than failing.
 *
 * <p>No JavaFX dependency — kept in the {@code storage} package per the project's package
 * boundary rule so it stays independently unit-testable.
 */
@Component
public class PathPatternResolver {

    private static final Logger log = LoggerFactory.getLogger(PathPatternResolver.class);

    /** Substitutes {@code ${KEY}} for each entry in {@code vars}; unknown placeholders are left as-is. */
    public static String substitute(String pattern, Map<String, String> vars) {
        String out = pattern;
        for (Map.Entry<String, String> v : vars.entrySet()) {
            out = out.replace("${" + v.getKey() + "}", v.getValue());
        }
        return out;
    }

    /**
     * Resolves a directory pattern (after variable substitution) to an existing directory,
     * walking one path segment at a time and glob-matching segments that still contain wildcard
     * metacharacters after substitution. Returns {@link Optional#empty()} if the pattern is
     * blank or any segment fails to resolve to an existing directory.
     */
    public Optional<Path> resolveDirectory(String directoryPattern, Map<String, String> vars) {
        if (directoryPattern == null || directoryPattern.isBlank()) return Optional.empty();

        String substituted = substitute(directoryPattern, vars);
        String[] segments = substituted.split("/", -1);

        Path current = substituted.startsWith("/") ? Paths.get("/") : Paths.get("");
        for (String segment : segments) {
            if (segment.isEmpty()) continue;
            Path next = resolveSegment(current, segment);
            if (next == null) return Optional.empty();
            current = next;
        }
        return Files.isDirectory(current) ? Optional.of(current) : Optional.empty();
    }

    private static boolean isGlob(String segment) {
        return segment.indexOf('*') >= 0 || segment.indexOf('?') >= 0 || segment.indexOf('[') >= 0;
    }

    private Path resolveSegment(Path parent, String segment) {
        if (!isGlob(segment)) {
            Path candidate = parent.resolve(segment);
            return Files.isDirectory(candidate) ? candidate : null;
        }
        if (!Files.isDirectory(parent)) return null;

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + segment);
        try (Stream<Path> s = Files.list(parent)) {
            List<Path> matches = s.filter(Files::isDirectory)
                    .filter(p -> matcher.matches(p.getFileName()))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            if (matches.isEmpty()) return null;
            if (matches.size() > 1) {
                log.warn("Ambiguous directory glob '{}' under {} matched {} candidates ({}) — using the first",
                        segment, parent, matches.size(), matches);
            }
            return matches.get(0);
        } catch (IOException e) {
            log.warn("Failed to scan directory {} for glob '{}': {}", parent, segment, e.getMessage());
            return null;
        }
    }
}
