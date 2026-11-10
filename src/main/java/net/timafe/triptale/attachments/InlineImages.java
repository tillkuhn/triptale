package net.timafe.triptale.attachments;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/**
 * Images embedded between paragraphs of a Markdown text (todo 92): {@code ![name](attachments/...)},
 * the path relative to the data dir root, alone on its line with an empty line before and after.
 * Anything else, such as an image inside a sentence, stays plain text. Only files below
 * {@code attachments/} are valid. No JavaFX imports (package boundary rule).
 */
public final class InlineImages {

    private static final String PREFIX = AttachmentsDir.DIR_NAME + "/";
    private static final Pattern BLOCK = Pattern.compile("^!\\[([^\\]]*)]\\(([^)\\s]+)\\)$");
    private static final Pattern ANY = Pattern.compile("!\\[[^\\]]*]\\(" + PREFIX + "[^)\\s]*\\)");
    // 20260530_091312_SaveAussicht+_mini.jpg: camera timestamp prefix, fave marker, size suffix
    private static final Pattern TIMESTAMP_PREFIX = Pattern.compile("^\\d{8}_\\d{6}_");
    private static final Pattern NAME_SUFFIX = Pattern.compile("(_mini|\\+)+$");
    private static final Pattern WORD_BOUNDARY = Pattern.compile(
            "(?<=\\p{Ll})(?=\\p{Lu})|(?<=\\p{L})(?=\\p{N})|(?<=\\p{N})(?=\\p{L})|(?<=\\p{Lu})(?=\\p{Lu}\\p{Ll})");

    private InlineImages() {
    }

    /** The text with every renderable block replaced, and what was wrong with the others. */
    public record Result(String text, List<String> problems) {
    }

    /** {@code 20260529_142327_OoopsICE1011FaelltAus+_mini.jpg} → "Ooops ICE 1011 Faellt Aus". */
    public static String altText(Path image) {
        String name = image.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        name = NAME_SUFFIX.matcher(TIMESTAMP_PREFIX.matcher(name).replaceFirst("")).replaceFirst("");
        name = WORD_BOUNDARY.matcher(name.replace('_', ' ').replace('-', ' ')).replaceAll(" ");
        return name.replaceAll("\\s+", " ").strip();
    }

    /** The line to paste into a text for {@code file} (below {@code dataDir}), e.g. {@code ![Name](attachments/...)}. */
    public static String markdownLine(Path dataDir, Path file) {
        List<String> segments = new ArrayList<>();
        for (Path segment : dataDir.relativize(file)) {
            segments.add(segment.toString().replace(" ", "%20").replace("(", "%28").replace(")", "%29"));
        }
        return "![" + altText(file).replace("[", "").replace("]", "") + "](" + String.join("/", segments) + ")";
    }

    /** True if {@code ref} (as written in the Markdown) points below {@code attachments/}. */
    public static boolean isAttachmentRef(String ref) {
        return ref != null && ref.startsWith(PREFIX);
    }

    /**
     * The existing file {@code ref} points to, empty if it is not below the data dir's
     * {@code attachments/} (no {@code ..} escapes) or does not exist on this machine.
     */
    public static Optional<Path> resolve(Path dataDir, String ref) {
        if (!isAttachmentRef(ref)) return Optional.empty();
        String decoded = ref.replace("%20", " ").replace("%28", "(").replace("%29", ")");
        Path root = dataDir.resolve(AttachmentsDir.DIR_NAME).normalize();
        Path file = dataDir.resolve(decoded).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) return Optional.empty();
        return Optional.of(file);
    }

    /** The files the valid image blocks of {@code markdown} point to. */
    public static Set<Path> referenced(String markdown, Path dataDir) {
        Set<Path> files = new HashSet<>();
        renderBlocks(markdown, dataDir, (alt, file) -> {
            files.add(file);
            return "";
        });
        return files;
    }

    /**
     * Replaces each valid image block with {@code renderer(alt, file)} (alt falls back to the
     * file name). A block whose file is missing is dropped, one that is not below
     * {@code attachments/} and any image that is not a block stay as written; every one of them
     * adds an entry to {@link Result#problems()}. Fenced code is left alone.
     */
    public static Result renderBlocks(String markdown, Path dataDir, BiFunction<String, Path, String> renderer) {
        List<String> problems = new ArrayList<>();
        if (markdown == null || markdown.isEmpty()) return new Result(markdown, problems);
        String[] lines = markdown.split("\n", -1);
        List<String> out = new ArrayList<>(lines.length);
        boolean fenced = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String stripped = line.strip();
            if (stripped.startsWith("```") || stripped.startsWith("~~~")) fenced = !fenced;
            if (fenced || !ANY.matcher(line).find() && !BLOCK.matcher(stripped).matches()) {
                out.add(line);
                continue;
            }
            var m = BLOCK.matcher(stripped);
            boolean alone = (i == 0 || lines[i - 1].isBlank()) && (i == lines.length - 1 || lines[i + 1].isBlank());
            if (!m.matches() || !alone) {
                if (ANY.matcher(line).find()) problems.add("not on its own line: " + stripped);
                out.add(line);
            } else if (!isAttachmentRef(m.group(2))) {
                problems.add("not below attachments/: " + m.group(2));
                out.add(line);
            } else {
                Optional<Path> file = resolve(dataDir, m.group(2));
                if (file.isEmpty()) {
                    problems.add("file not found: " + m.group(2));
                    continue;
                }
                String alt = m.group(1).isBlank() ? altText(file.get()) : m.group(1);
                out.add(renderer.apply(alt, file.get()));
            }
        }
        return new Result(String.join("\n", out), problems);
    }
}
