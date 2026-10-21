package net.timafe.triptale.ui.dialog;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.util.Emojis;
import net.timafe.triptale.util.Emojis.Entry;

import java.util.Optional;

/**
 * A small searchable popup over the curated travel emoji (todo 69, see
 * {@code docs/69_emoji_picker.md}). Picking one closes the dialog immediately with that glyph as
 * the result; Cancel/Escape/the window close button yield an empty result.
 */
public final class EmojiPickerDialog {

    private EmojiPickerDialog() {
    }

    public static Optional<String> showAndWait(Emojis emojis) {
        Dialog<String> dlg = new Dialog<>();
        dlg.setTitle("Emoji Picker");
        dlg.setResizable(true);

        TextField search = new TextField();
        search.setPromptText("Search e.g. \"hike\"…");

        FlowPane grid = new FlowPane(6, 6);
        grid.setPadding(new Insets(4));

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(360);
        scroll.setPrefViewportHeight(260);

        Runnable rebuild = () -> {
            grid.getChildren().clear();
            for (Entry entry : emojis.search(search.getText())) {
                Button btn = new Button(entry.glyph());
                btn.setStyle("-fx-font-size: 18px; -fx-padding: 4 8 4 8;");
                if (!entry.keywords().isEmpty()) {
                    btn.setTooltip(new Tooltip(String.join(", ", entry.keywords())));
                }
                btn.setOnAction(e -> {
                    dlg.setResult(entry.glyph());
                    dlg.close();
                });
                grid.getChildren().add(btn);
            }
        };
        search.textProperty().addListener((o, a, b) -> rebuild.run());
        rebuild.run();

        VBox content = new VBox(8, search, scroll);
        content.setPadding(new Insets(10));
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL);
        Dialogs.applyStylesheet(dlg.getDialogPane());

        dlg.setOnShown(e -> search.requestFocus());
        return dlg.showAndWait();
    }
}
