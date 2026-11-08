package net.timafe.triptale.ui.dialog;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.util.ByteSizes;
import net.timafe.triptale.ui.UiText;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Lists recorded tours found for the entry's date (todo 90) and lets the user pick one, or
 * choose "Browse…" to pick any other file. Returns its outcome as data; never touches the
 * controller.
 */
public final class GpxCandidatesDialog {

    /** {@code file} is the picked candidate; {@code null} means "Browse…" (open the file chooser). */
    public record Choice(Path file) {}

    private GpxCandidatesDialog() {}

    private static String sizeOf(Path p) {
        try {
            return ByteSizes.humanize(java.nio.file.Files.size(p));
        } catch (java.io.IOException e) {
            return "?";
        }
    }

    /** Empty when cancelled. The first candidate is pre-selected. */
    public static Optional<Choice> showAndWait(LocalDate date, List<Path> candidates) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("🦎 GPX Gecko");
        dialog.setHeaderText(candidates.size() == 1
                ? "Recorded tour found for " + date
                : candidates.size() + " recorded tours found for " + date);
        java.util.Map<Path, String> sizes = new java.util.HashMap<>();
        candidates.forEach(c -> sizes.put(c, sizeOf(c)));
        ListView<Path> list = new ListView<>();
        list.getItems().setAll(candidates);
        list.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(Path p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : p.getFileName() + "  ·  " + sizes.get(p));
            }
        });
        list.getSelectionModel().selectFirst();
        list.setPrefHeight(Math.min(220, 40 + candidates.size() * 28));
        list.setPrefWidth(560);
        Path folder = candidates.getFirst().getParent();
        Label folderLabel = new Label("📂 " + (folder == null ? "" : UiText.homeRelative(folder)));
        folderLabel.setWrapText(true);
        VBox box = new VBox(8, folderLabel, list, new Label("Or choose another file with Browse…"));
        box.setPadding(new Insets(10));

        ButtonType browse = new ButtonType("Browse…", ButtonBar.ButtonData.LEFT);
        ButtonType importType = new ButtonType("Import", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().addAll(browse, ButtonType.CANCEL, importType);
        Dialogs.applyStylesheet(dialog.getDialogPane());
        javafx.scene.Node importButton = dialog.getDialogPane().lookupButton(importType);
        importButton.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());
        list.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && list.getSelectionModel().getSelectedItem() != null) {
                ((javafx.scene.control.Button) importButton).fire();
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty()) return Optional.empty();
        if (result.get() == browse) return Optional.of(new Choice(null));
        if (result.get() == importType) return Optional.of(new Choice(list.getSelectionModel().getSelectedItem()));
        return Optional.empty();
    }
}
