package net.timafe.triptale.util;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Default Smart Sync commit message (todo 32, D2): the app's own pending-save labels, plus the
 * dirty files git reports that none of those labels covers ("external" changes, e.g. edits made
 * in Tolaria or a text editor).
 */
public final class CommitMessage {

    private static final int INLINE_LIMIT = 5;
    private static final Pattern ENDS_WITH_DATE = Pattern.compile(".*\\d{4}-\\d{2}-\\d{2}$");

    private CommitMessage() {
    }

    /**
     * Dirty files not covered by a pending label. A label covers a file when the file path starts
     * with it, followed by nothing, {@code /} or {@code .}, or by {@code -} for an entry label
     * ending in a date — so trip label {@code 2026/norway} covers {@code 2026/norway/README.md}
     * (but not {@code 2026/norway-two/…}) and entry label {@code 2026/norway/2026-06-04} covers
     * {@code 2026/norway/2026-06-04-Thursday.md}.
     */
    public static List<String> external(Collection<String> pendingLabels, List<String> dirtyFiles) {
        return dirtyFiles.stream()
                .filter(file -> pendingLabels.stream().noneMatch(label -> covers(label, file)))
                .toList();
    }

    private static boolean covers(String label, String file) {
        if (!file.startsWith(label)) return false;
        if (file.length() == label.length()) return true;
        char next = file.charAt(label.length());
        return next == '/' || next == '.' || (next == '-' && ENDS_WITH_DATE.matcher(label).matches());
    }

    /** {@code pendingMessage} (may be blank) combined with a summary of {@code external} files. */
    public static String compose(String pendingMessage, List<String> external) {
        boolean hasPending = pendingMessage != null && !pendingMessage.isBlank();
        if (external.isEmpty()) return hasPending ? pendingMessage : "Sync";
        String count = external.size() + " external change" + (external.size() == 1 ? "" : "s");
        if (hasPending) return pendingMessage + " | + " + count;
        String files = external.size() <= INLINE_LIMIT
                ? String.join(", ", external)
                : String.join(", ", external.subList(0, INLINE_LIMIT))
                        + ", ... and " + (external.size() - INLINE_LIMIT) + " more";
        return "Sync: " + count + ": " + files;
    }
}
