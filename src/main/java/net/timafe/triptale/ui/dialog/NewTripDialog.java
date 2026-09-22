package net.timafe.triptale.ui.dialog;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Dialog;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.util.GpxImport;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Collects the fields for a new trip. Returns the raw user input — slug derivation, the
 * already-exists check and persistence stay with the caller.
 */
public final class NewTripDialog {

    public record Spec(String name, LocalDate startDate, String description, Optional<FirstEntry> firstEntry) {}

    /** {@code lat}/{@code lon} are null unless the trip was created from a GPX import. */
    public record FirstEntry(String title, Double lat, Double lon) {}

    /** Empty when the user cancelled. */
    public Optional<Spec> showAndWait() {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("New Trip");
        dlg.setHeaderText("Create a new trip");

        TextField nameField = new TextField();
        nameField.setPromptText("Trip name");
        nameField.setPrefColumnCount(28);
        DatePicker startField = new DatePicker(LocalDate.now());
        startField.setConverter(Dialogs.isoDateConverter());
        TextArea descArea = new TextArea();
        descArea.setPromptText("Optional description");
        descArea.setPrefRowCount(3);
        descArea.setPrefColumnCount(28);
        descArea.setWrapText(true);

        Label importHintLabel = new Label();
        importHintLabel.getStyleClass().add("hint-label");

        CheckBox initFirstEntryCheck = new CheckBox("Init first Tale Entry on trip creation");

        // GPX-derived title/coords, kept separate from the (possibly later edited) Name field.
        String[] gpxName = new String[1];
        Double[] gpxLat = new Double[1];
        Double[] gpxLon = new Double[1];

        GridPane grid = Dialogs.formGrid();
        grid.add(new Label("Name:"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("Start date:"), 0, 1);
        grid.add(startField, 1, 1);
        grid.add(new Label("Description:"), 0, 2);
        grid.add(descArea, 1, 2);
        grid.add(initFirstEntryCheck, 1, 3);
        grid.add(importHintLabel, 1, 4);

        dlg.getDialogPane().setContent(grid);
        ButtonType importType = new ButtonType("Import GPX…", ButtonBar.ButtonData.LEFT);
        dlg.getDialogPane().getButtonTypes().addAll(importType, ButtonType.OK, ButtonType.CANCEL);
        Dialogs.applyStylesheet(dlg.getDialogPane());

        Node importButtonNode = dlg.getDialogPane().lookupButton(importType);
        importButtonNode.addEventFilter(ActionEvent.ACTION, ev -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Import GPX");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("GPX files", "*.gpx"));
            var file = chooser.showOpenDialog(dlg.getDialogPane().getScene().getWindow());
            if (file != null) {
                Optional<GpxImport.Parsed> parsed = GpxImport.parse(file);
                if (parsed.isEmpty()) {
                    importHintLabel.setText("Couldn't parse a track name/point from this file.");
                } else {
                    GpxImport.Parsed p = parsed.get();
                    nameField.setText(p.name());
                    startField.setValue(p.date());
                    gpxName[0] = p.name();
                    gpxLat[0] = p.lat();
                    gpxLon[0] = p.lon();
                    initFirstEntryCheck.setSelected(true);
                    importHintLabel.setText("Imported \"" + p.name() + "\"");
                }
            }
            ev.consume();
        });

        Node okButton = dlg.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setDisable(true);
        Runnable refreshOk = () -> okButton.setDisable(
                nameField.getText().isBlank() || startField.getValue() == null);
        nameField.textProperty().addListener((o, a, b) -> refreshOk.run());
        startField.valueProperty().addListener((o, a, b) -> refreshOk.run());

        Optional<ButtonType> result = dlg.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return Optional.empty();

        String name = nameField.getText().trim();
        Optional<FirstEntry> firstEntry = Optional.empty();
        if (initFirstEntryCheck.isSelected()) {
            firstEntry = Optional.of(gpxName[0] != null
                    ? new FirstEntry(gpxName[0], gpxLat[0], gpxLon[0])
                    : new FirstEntry(name, null, null));
        }
        return Optional.of(new Spec(name, startField.getValue(), descArea.getText(), firstEntry));
    }
}
