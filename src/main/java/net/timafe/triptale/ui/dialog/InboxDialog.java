package net.timafe.triptale.ui.dialog;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import net.timafe.triptale.inbox.InboxItem;
import net.timafe.triptale.ui.Dialogs;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Lists pending {@code inbox/} captures (todo 68): one row per item with a date picker
 * (defaulted from the message's own timestamp) and an Attach/Discard action. This dialog is the
 * "separate inbox to pick and distribute from" — no extra staging layer needed on top.
 *
 * <p>Unlike most dialogs in {@code ui/dialog}, this one does not return a result for the caller
 * to apply — each row commits immediately through {@link ItemHandler}, since Attach may need to
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

    public void show(List<InboxItem> items, ItemHandler handler) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("📥 Mobile Inbox");
        dialog.setHeaderText(items.isEmpty() ? "No pending captures" : items.size() + " pending capture(s)");

        VBox list = new VBox(10);
        list.setPadding(new Insets(4));
        if (items.isEmpty()) {
            list.getChildren().add(new Label("Nothing captured yet."));
        } else {
            for (InboxItem item : items) list.getChildren().add(row(item, handler, list, dialog));
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

    private VBox row(InboxItem item, ItemHandler handler, VBox list, Dialog<Void> dialog) {
        Label meta = new Label(TIMESTAMP.format(item.timestamp()));
        meta.getStyleClass().add("hint-label");
        Label text = new Label(item.text());
        text.setWrapText(true);
        DatePicker datePicker = new DatePicker(item.timestamp().toLocalDate());
        datePicker.setConverter(Dialogs.isoDateConverter());
        Button attach = new Button("✅ Attach");
        Button discard = new Button("🗑 Discard");
        Label error = new Label();
        error.getStyleClass().add("form-error");

        VBox box = new VBox(6, meta, text, new HBox(10, datePicker, attach, discard), error);
        box.setPadding(new Insets(8));
        box.getStyleClass().add("card");

        attach.setOnAction(e -> {
            LocalDate date = datePicker.getValue();
            if (date == null) { error.setText("Pick a date first."); return; }
            if (handler.handle(item, Action.ATTACH, date)) remove(box, list, dialog);
            else error.setText("Couldn't attach automatically — check the main window.");
        });
        discard.setOnAction(e -> {
            if (handler.handle(item, Action.DISCARD, datePicker.getValue())) remove(box, list, dialog);
        });
        return box;
    }

    private void remove(VBox box, VBox list, Dialog<Void> dialog) {
        list.getChildren().remove(box);
        if (list.getChildren().isEmpty()) list.getChildren().add(new Label("All caught up!"));
        if (dialog.getDialogPane().getScene() != null) {
            dialog.getDialogPane().getScene().getWindow().sizeToScene();
        }
    }
}
