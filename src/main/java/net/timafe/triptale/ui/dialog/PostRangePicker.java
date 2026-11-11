package net.timafe.triptale.ui.dialog;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Post;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.wordpress.PostPicker;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Diary Composer's range (todo 85): {@code [post selector][from][to]}. The selector offers
 * "Ad Hoc Post" (free from/to), "Entire Trip" (the whole range, read-only) and the trip's real
 * Posts (their range, read-only). From/to list only days that have an entry; for a Post they show
 * its range clamped to those days. A 📮 button opens the Post Office on the selected Post.
 */
final class PostRangePicker {

    private static final double COMBO_WIDTH = 300;
    private static final double POST_WIDTH = 170;

    private enum Kind { AD_HOC, ENTIRE, POST }

    /** One selector entry; {@code post} is set for {@link Kind#POST} only. */
    private record Choice(Kind kind, Post post) {}

    private final Trip trip;
    private final List<LocalDate> dates;
    private final ComboBox<Choice> postCombo = new ComboBox<>();
    private final ComboBox<LocalDate> fromCombo = new ComboBox<>();
    private final ComboBox<LocalDate> toCombo = new ComboBox<>();
    private final Button officeBtn = new Button("📮");
    private final HBox node;
    private boolean updating;

    /** {@code entries} in date order; {@code current} picks the default Post (the one containing it). */
    PostRangePicker(Trip trip, List<DiaryEntry> entries, List<Post> posts, LocalDate current,
                    LocalDate from, LocalDate to) {
        this.trip = trip;
        this.dates = entries.stream().map(DiaryEntry::date).toList();
        Map<LocalDate, String> labels = new HashMap<>();
        for (DiaryEntry e : entries) labels.put(e.date(), EntryRangePicker.entryLabel(trip, e));
        StringConverter<LocalDate> dateConverter = new StringConverter<>() {
            @Override public String toString(LocalDate d) { return d == null ? "" : labels.getOrDefault(d, d.toString()); }
            @Override public LocalDate fromString(String s) { return null; }
        };
        for (ComboBox<LocalDate> c : List.of(fromCombo, toCombo)) {
            c.getItems().setAll(dates);
            c.setConverter(dateConverter);
            c.setPrefWidth(COMBO_WIDTH);
            c.setMaxWidth(COMBO_WIDTH);
        }
        postCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Choice c) {
                if (c == null) return "";
                return switch (c.kind()) {
                    case AD_HOC -> "Ad Hoc Post";
                    case ENTIRE -> "Entire Trip";
                    case POST -> PostPicker.label(trip, c.post());
                };
            }
            @Override public Choice fromString(String s) { return null; }
        });
        postCombo.setPrefWidth(POST_WIDTH);
        postCombo.setMaxWidth(POST_WIDTH);
        fromCombo.valueProperty().addListener((obs, old, d) -> {
            if (!updating && d != null && toCombo.getValue() != null && d.isAfter(toCombo.getValue())) toCombo.setValue(d);
        });
        toCombo.valueProperty().addListener((obs, old, d) -> {
            if (!updating && d != null && fromCombo.getValue() != null && d.isBefore(fromCombo.getValue())) fromCombo.setValue(d);
        });
        postCombo.valueProperty().addListener((obs, old, c) -> apply(c));
        officeBtn.setTooltip(new Tooltip("Open the Post Office for the selected post"));

        reload(posts, current, from, to);
        node = new HBox(8, new Label("Post:"), postCombo, officeBtn, new Label("From:"), fromCombo, new Label("To:"), toCombo);
        node.setAlignment(Pos.CENTER_LEFT);
    }

    /**
     * Refills the selector after the Post Office changed the Posts. Keeps the selected Post (by slug) if it still
     * exists, else the Post containing {@code current}, else Ad Hoc with {@code from}/{@code to} (null = unbounded).
     */
    void reload(List<Post> posts, LocalDate current, LocalDate from, LocalDate to) {
        Post previous = selectedPost();
        List<Choice> items = new ArrayList<>();
        items.add(new Choice(Kind.AD_HOC, null));
        items.add(new Choice(Kind.ENTIRE, null));
        posts.forEach(p -> items.add(new Choice(Kind.POST, p)));
        postCombo.getItems().setAll(items);
        Post wanted = previous == null ? null
                : posts.stream().filter(p -> p.slug().equals(previous.slug())).findFirst().orElse(null);
        if (wanted == null) wanted = PostPicker.preselect(posts, current).orElse(null);
        Choice pick = items.getFirst();
        for (Choice c : items) {
            if (wanted != null && c.post() != null && c.post().slug().equals(wanted.slug())) pick = c;
        }
        // Re-selecting the same value must still re-apply it, the Post's range may have changed
        postCombo.setValue(null);
        postCombo.setValue(pick);
        if (pick.kind() == Kind.AD_HOC) setRange(from, to);
    }

    private void apply(Choice c) {
        if (c == null) return;
        boolean adHoc = c.kind() == Kind.AD_HOC;
        fromCombo.setDisable(!adHoc || dates.isEmpty());
        toCombo.setDisable(!adHoc || dates.isEmpty());
        officeBtn.setDisable(c.kind() == Kind.ENTIRE);
        switch (c.kind()) {
            case ENTIRE -> setRange(first(), last());
            case POST -> {
                List<LocalDate> in = c.post().datesIn(dates);
                setRange(in.isEmpty() ? null : in.getFirst(), in.isEmpty() ? null : in.getLast());
            }
            default -> { }
        }
    }

    /** Sets both ends silently; a null or unknown end means the first/last entry day (an empty Post: none). */
    private void setRange(LocalDate from, LocalDate to) {
        boolean emptyPost = selectedPost() != null && selectedPost().datesIn(dates).isEmpty();
        updating = true;
        try {
            fromCombo.setValue(emptyPost ? null : from != null && dates.contains(from) ? from : first());
            toCombo.setValue(emptyPost ? null : to != null && dates.contains(to) ? to : last());
        } finally {
            updating = false;
        }
    }

    private LocalDate first() { return dates.isEmpty() ? null : dates.getFirst(); }
    private LocalDate last() { return dates.isEmpty() ? null : dates.getLast(); }

    HBox node() { return node; }

    /** The selected real Post, or null for Ad Hoc / Entire Trip. */
    Post selectedPost() {
        Choice c = postCombo.getValue();
        return c == null ? null : c.post();
    }

    /** Selects the real Post with this slug; false if there is none. */
    boolean select(String slug) {
        for (Choice c : postCombo.getItems()) {
            if (c.post() != null && c.post().slug().equals(slug)) {
                postCombo.setValue(c);
                return true;
            }
        }
        return false;
    }

    LocalDate from() { return fromCombo.getValue(); }
    LocalDate to() { return toCombo.getValue(); }

    /** True if there is nothing to export: no entries at all, or a Post whose range has none. */
    boolean isEmpty() { return dates.isEmpty() || (selectedPost() != null && selectedPost().datesIn(dates).isEmpty()); }

    /** The entry days in the current range. */
    List<LocalDate> rangeDates() {
        if (isEmpty() || from() == null || to() == null) return List.of();
        return dates.stream().filter(d -> !d.isBefore(from()) && !d.isAfter(to())).toList();
    }

    /** Runs the action on every change of the selection or either end. */
    void onChange(Runnable action) {
        postCombo.valueProperty().addListener((obs, old, c) -> action.run());
        fromCombo.valueProperty().addListener((obs, old, d) -> { if (!updating) action.run(); });
        toCombo.valueProperty().addListener((obs, old, d) -> { if (!updating) action.run(); });
    }

    /** Called when 📮 is pressed. */
    void onOpenPostOffice(Runnable action) {
        officeBtn.setOnAction(e -> action.run());
    }
}
