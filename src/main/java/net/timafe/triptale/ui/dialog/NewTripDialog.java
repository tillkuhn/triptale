package net.timafe.triptale.ui.dialog;

import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;
import net.timafe.triptale.ui.Dialogs;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * Collects the fields for a new trip. Returns the raw user input — slug derivation, the
 * already-exists check and persistence stay with the caller.
 */
public final class NewTripDialog {

    /** Start date is rendered as {@code yyyy-MM-dd} rather than the locale default. */
    private static final DateTimeFormatter DATE_ISO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH);

    public record Spec(String name, LocalDate startDate, String description) {}

    /** Empty when the user cancelled. */
    public Optional<Spec> showAndWait() {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("New Trip");
        dlg.setHeaderText("Create a new trip");

        TextField nameField = new TextField();
        nameField.setPromptText("Trip name");
        nameField.setPrefColumnCount(28);
        DatePicker startField = new DatePicker(LocalDate.now());
        startField.setConverter(new StringConverter<>() {
            @Override public String toString(LocalDate d) { return d == null ? "" : DATE_ISO.format(d); }
            @Override public LocalDate fromString(String s) {
                if (s == null || s.isBlank()) return null;
                try { return LocalDate.parse(s.trim(), DATE_ISO); } catch (Exception e) { return null; }
            }
        });
        TextArea descArea = new TextArea();
        descArea.setPromptText("Optional description");
        descArea.setPrefRowCount(3);
        descArea.setPrefColumnCount(28);
        descArea.setWrapText(true);

        GridPane grid = Dialogs.formGrid();
        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Start date:"), 0, 1);
        grid.add(startField, 1, 1);
        grid.add(new Label("Description:"), 0, 2);
        grid.add(descArea, 1, 2);

        dlg.getDialogPane().setContent(grid);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Dialogs.applyStylesheet(dlg.getDialogPane());

        Node okButton = dlg.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setDisable(true);
        Runnable refreshOk = () -> okButton.setDisable(
                nameField.getText().isBlank() || startField.getValue() == null);
        nameField.textProperty().addListener((o, a, b) -> refreshOk.run());
        startField.valueProperty().addListener((o, a, b) -> refreshOk.run());

        Optional<ButtonType> result = dlg.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return Optional.empty();
        return Optional.of(new Spec(
                nameField.getText().trim(), startField.getValue(), descArea.getText()));
    }
}
