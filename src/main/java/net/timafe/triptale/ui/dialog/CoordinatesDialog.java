package net.timafe.triptale.ui.dialog;

import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.ui.UiText;
import net.timafe.triptale.util.Coordinates;

import java.util.Optional;

/** Edits an entry's start point: manual lat/lon entry, or parsed from a pasted URL/GPX/GeoJSON. */
public final class CoordinatesDialog {

    /**
     * Outcome of the dialog. A {@code null} {@link #coords()} means the user pressed Clear and
     * the entry's start point should be unset; Cancel is reported as an empty {@link Optional}
     * from {@link #showAndWait} instead.
     */
    public record Result(Coordinates.LatLon coords) {
        public boolean isCleared() {
            return coords == null;
        }
    }

    /** Empty when the user cancelled or dismissed the dialog. */
    public Optional<Result> showAndWait(Double initialLat, Double initialLon) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Start Point Coordinates");
        dlg.setHeaderText("Start point coordinates");

        TextField latField = new TextField(initialLat == null ? "" : initialLat.toString());
        TextField lonField = new TextField(initialLon == null ? "" : initialLon.toString());
        Label ddmLabel = new Label("—");
        TextArea parseArea = new TextArea();
        parseArea.setPromptText("Paste a Google Maps URL, GPX <trkpt>, or GeoJSON [lon, lat] array");
        parseArea.setPrefRowCount(3);
        parseArea.setWrapText(true);
        Label parseHintLabel = new Label();
        parseHintLabel.getStyleClass().add("hint-label");

        GridPane grid = Dialogs.formGrid();
        grid.add(new Label("DDM:"), 0, 0);
        grid.add(ddmLabel, 1, 0);
        grid.add(new Label("Latitude:"), 0, 1);
        grid.add(latField, 1, 1);
        grid.add(new Label("Longitude:"), 0, 2);
        grid.add(lonField, 1, 2);
        grid.add(new Label("Parse from:"), 0, 3);
        grid.add(parseArea, 1, 3);
        grid.add(parseHintLabel, 1, 4);

        ButtonType clearType = new ButtonType("Clear", ButtonBar.ButtonData.OTHER);
        ButtonType parseType = new ButtonType("Parse", ButtonBar.ButtonData.OTHER);
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().setContent(grid);
        // ButtonType.CANCEL also re-enables the dialog window's native close (X) button —
        // JavaFX disables it unless a CANCEL_CLOSE-data button is present.
        dlg.getDialogPane().getButtonTypes().addAll(clearType, parseType, ButtonType.CANCEL, saveType);
        Dialogs.applyStylesheet(dlg.getDialogPane());

        Node saveButtonNode = dlg.getDialogPane().lookupButton(saveType);
        Runnable refresh = () -> {
            Coordinates.LatLon parsed = currentInput(latField, lonField);
            ddmLabel.setText(parsed == null ? "—" : Coordinates.toDdm(parsed.lat(), parsed.lon()));
            saveButtonNode.setDisable(parsed == null);
        };
        latField.textProperty().addListener((o, a, b) -> refresh.run());
        lonField.textProperty().addListener((o, a, b) -> refresh.run());
        refresh.run();

        Node parseButtonNode = dlg.getDialogPane().lookupButton(parseType);
        parseButtonNode.addEventFilter(ActionEvent.ACTION, ev -> {
            Optional<Coordinates.LatLon> parsed = Coordinates.tryParse(parseArea.getText());
            if (parsed.isPresent()) {
                latField.setText(Double.toString(parsed.get().lat()));
                lonField.setText(Double.toString(parsed.get().lon()));
                parseHintLabel.setText("");
            } else {
                parseHintLabel.setText("Couldn't parse coordinates from input.");
            }
            ev.consume();
        });

        Optional<ButtonType> result = dlg.showAndWait();
        if (result.isEmpty()) return Optional.empty();
        if (result.get() == clearType) return Optional.of(new Result(null));
        if (result.get() == saveType) return Optional.of(new Result(currentInput(latField, lonField)));
        return Optional.empty();
    }

    /** The two fields as a valid coordinate pair, or null if either is unparseable/out of range. */
    private static Coordinates.LatLon currentInput(TextField latField, TextField lonField) {
        Double lat = UiText.parseDecimal(latField.getText());
        Double lon = UiText.parseDecimal(lonField.getText());
        if (lat == null || lon == null || !Coordinates.isValid(lat, lon)) return null;
        return new Coordinates.LatLon(lat, lon);
    }
}
