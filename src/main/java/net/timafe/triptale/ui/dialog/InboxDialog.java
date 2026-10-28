package net.timafe.triptale.ui.dialog;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.StringConverter;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.inbox.InboxItem;
import net.timafe.triptale.ui.BrowserLauncher;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.ui.UiText;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lists pending {@code inbox/} captures (todo 68): one row per item with a day combo (every
 * saved entry in the trip, newest first, labelled relative to that item's own capture date —
 * "Same Day: …", "+2 days: …", "-1 day: …", same "Day N: Title" lookup {@link EntryRangePicker}
 * uses but with a relative-day prefix instead of "Day N", since each row's reference point is
 * its own capture date, not the trip start) and an action row below it (Add/Dump today, room for
 * more later). A photo item (todo 74) shows a thumbnail above its caption (or a "📷 Photo"
 * placeholder if there's no caption); any URL inside the caption becomes a clickable
 * {@link Hyperlink} so the user can check it before deciding to Add or Dump. This dialog is the
 * "separate inbox to pick and distribute from" — no extra staging layer needed on top.
 *
 * <p>Unlike most dialogs in {@code ui/dialog}, this one does not return a result for the caller
 * to apply — each row commits immediately through {@link ItemHandler}, since Add may need to
 * navigate the main window to a different day and save it before the row can be considered done.
 * Same exception to the "dialogs never mutate controller state" rule as {@link SmartSyncDialog}.
 */
public final class InboxDialog {

    public enum Action { ATTACH, DISCARD }

    public interface ItemHandler {
        /** Performs the action for one item; returns true if it succeeded (the row is then removed). */
        boolean handle(InboxItem item, Action action, LocalDate date);
    }

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);
    private static final double THUMBNAIL_WIDTH = 220;
    /** Splits caption text into word-or-whitespace runs so URL tokens can become hyperlinks. */
    private static final Pattern TOKENS = Pattern.compile("\\S+|\\s+");

    private final BrowserLauncher browser;

    public InboxDialog(BrowserLauncher browser) {
        this.browser = browser;
    }

    /** {@code entries} is every saved entry of the current trip (none need exist yet). */
    public void show(List<InboxItem> items, List<DiaryEntry> entries, String statusLine, ItemHandler handler) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("🐙 Octobot's Inbox");
        String header = items.isEmpty() ? "No pending captures" : items.size() + " pending capture(s)";
        if (statusLine != null && !statusLine.isBlank()) header += " — " + statusLine;
        dialog.setHeaderText(header);

        Map<LocalDate, DiaryEntry> byDate = new HashMap<>();
        for (DiaryEntry e : entries) byDate.put(e.date(), e);
        // Newest first: there are usually more past entries than future ones, so the most
        // recent day — the one most likely to be the right destination — should come first.
        List<LocalDate> dates = entries.stream().map(DiaryEntry::date).sorted(Comparator.reverseOrder()).toList();

        VBox list = new VBox(10);
        list.setPadding(new Insets(4));
        if (items.isEmpty()) {
            list.getChildren().add(new Label("Nothing captured yet."));
        } else {
            for (InboxItem item : items) list.getChildren().add(row(item, dates, byDate, handler, list, dialog));
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().setPrefWidth(560);
        dialog.getDialogPane().setPrefHeight(480);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Dialogs.applyStylesheet(dialog.getDialogPane());

        dialog.showAndWait();
    }

    private VBox row(InboxItem item, List<LocalDate> dates, Map<LocalDate, DiaryEntry> byDate,
                      ItemHandler handler, VBox list, Dialog<Void> dialog) {
        Label meta = new Label(TIMESTAMP.format(item.timestamp()));
        meta.getStyleClass().add("hint-label");
        LocalDate itemDate = item.timestamp().toLocalDate();

        ComboBox<LocalDate> dayCombo = new ComboBox<>();
        dayCombo.getItems().setAll(dates);
        dayCombo.setConverter(new StringConverter<>() {
            @Override public String toString(LocalDate d) {
                if (d == null) return "";
                DiaryEntry e = byDate.get(d);
                return e == null ? d.toString() : relativeLabel(itemDate, e);
            }
            @Override public LocalDate fromString(String s) { return null; }
        });
        dayCombo.setMaxWidth(Double.MAX_VALUE);
        boolean noDays = dates.isEmpty();
        dayCombo.setDisable(noDays);
        if (!noDays) dayCombo.setValue(dates.contains(itemDate) ? itemDate : MapAssistantDialog.nearest(dates, itemDate));

        Button attach = new Button("➕ Add");
        Button discard = new Button("🗑 Dump");
        if (noDays) {
            attach.setDisable(true);
            attach.setTooltip(new Tooltip("Save at least one day in this trip first"));
        }
        Label error = new Label();
        error.getStyleClass().add("form-error");

        VBox box = new VBox(6, meta);
        box.setPadding(new Insets(8));
        box.getStyleClass().add("card");

        item.photo().ifPresent(photo -> {
            ImageView preview = new ImageView(new Image(photo.toUri().toString(), THUMBNAIL_WIDTH, 0, true, true, true));
            box.getChildren().add(preview);
        });
        if (!item.text().isBlank()) {
            box.getChildren().add(textFlow(item.text()));
        } else if (item.photo().isPresent()) {
            Label placeholder = new Label("📷 Photo");
            placeholder.getStyleClass().add("hint-label");
            box.getChildren().add(placeholder);
        }

        HBox actions = new HBox(8, attach, discard);
        box.getChildren().addAll(dayCombo, actions, error);

        attach.setOnAction(e -> {
            LocalDate date = dayCombo.getValue();
            if (date == null) { error.setText("Pick a day first."); return; }
            if (handler.handle(item, Action.ATTACH, date)) remove(box, list, dialog);
            else error.setText("Couldn't attach automatically — check the main window.");
        });
        discard.setOnAction(e -> {
            if (handler.handle(item, Action.DISCARD, dayCombo.getValue())) remove(box, list, dialog);
        });
        return box;
    }

    /** Wraps the caption text, turning any URL word into a clickable {@link Hyperlink}. */
    private TextFlow textFlow(String caption) {
        TextFlow flow = new TextFlow();
        Matcher m = TOKENS.matcher(caption);
        while (m.find()) {
            String token = m.group();
            if (UiText.isValidHttpUrl(token)) {
                Hyperlink link = new Hyperlink(token);
                link.setOnAction(e -> browser.open(token));
                flow.getChildren().add(link);
            } else {
                Text text = new Text(token);
                text.getStyleClass().add("inbox-caption-text");
                flow.getChildren().add(text);
            }
        }
        return flow;
    }

    /** "Same Day: Title" / "+2 days: Title" / "-1 day: Title", relative to {@code itemDate}. */
    private static String relativeLabel(LocalDate itemDate, DiaryEntry e) {
        long diff = ChronoUnit.DAYS.between(itemDate, e.date());
        String prefix;
        if (diff == 0) {
            prefix = "Same Day";
        } else {
            long abs = Math.abs(diff);
            String unit = abs == 1 ? "day" : "days";
            prefix = (diff > 0 ? "+" : "-") + abs + " " + unit;
        }
        String title = e.title() == null || e.title().isBlank() || e.title().equals(DiaryEntry.DEFAULT_TITLE)
                ? UiText.friendlyDate(e.date()) : e.title();
        return prefix + ": " + title;
    }

    private void remove(VBox box, VBox list, Dialog<Void> dialog) {
        list.getChildren().remove(box);
        if (list.getChildren().isEmpty()) list.getChildren().add(new Label("All caught up!"));
        if (dialog.getDialogPane().getScene() != null) {
            dialog.getDialogPane().getScene().getWindow().sizeToScene();
        }
    }
}
