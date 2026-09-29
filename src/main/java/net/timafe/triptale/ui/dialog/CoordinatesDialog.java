package net.timafe.triptale.ui.dialog;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.ui.UiText;
import net.timafe.triptale.util.Coordinates;

import java.util.Optional;
import java.util.function.Function;

/**
 * Edits an entry's start and end point: manual lat/lon entry per row, or parsed from a pasted
 * URL/GPX/GeoJSON into either row. A start alone is a complete answer (a day spent in one
 * place); an end requires a start.
 */
public final class CoordinatesDialog {

    /**
     * Outcome of the dialog: a {@code null} point means it should be unset on the entry.
     * Cancel is reported as an empty {@link Optional} from {@link #showAndWait} instead.
     */
    public record Result(Coordinates.LatLon start, Coordinates.LatLon stop) {}

    private static final int COORD_COLUMNS = 11;

    /** One Start/End row: its two fields and the DDM preview label. */
    private record Row(TextField lat, TextField lon, Label ddm) {

        static Row of(Double initialLat, Double initialLon) {
            Row row = new Row(new TextField(initialLat == null ? "" : initialLat.toString()),
                    new TextField(initialLon == null ? "" : initialLon.toString()), new Label("—"));
            row.lat.setPrefColumnCount(COORD_COLUMNS);
            row.lon.setPrefColumnCount(COORD_COLUMNS);
            return row;
        }

        boolean isEmpty() {
            return lat.getText().isBlank() && lon.getText().isBlank();
        }

        /** Both fields as a valid coordinate pair, or null if either is blank/unparseable/out of range. */
        Coordinates.LatLon value() {
            Double la = UiText.parseDecimal(lat.getText());
            Double lo = UiText.parseDecimal(lon.getText());
            if (la == null || lo == null || !Coordinates.isValid(la, lo)) return null;
            return new Coordinates.LatLon(la, lo);
        }

        void set(Coordinates.LatLon coords) {
            lat.setText(Double.toString(coords.lat()));
            lon.setText(Double.toString(coords.lon()));
        }

        void clear() {
            lat.clear();
            lon.clear();
        }
    }

    /** Empty when the user cancelled or dismissed the dialog. */
    public Optional<Result> showAndWait(Double startLat, Double startLon, Double stopLat, Double stopLon) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Coordinates");
        dlg.setHeaderText("Start and end point");

        Row start = Row.of(startLat, startLon);
        Row stop = Row.of(stopLat, stopLon);
        TextArea parseArea = new TextArea();
        parseArea.setPromptText("Paste a Google Maps URL, GPX <trkpt>, or GeoJSON [lon, lat] array");
        parseArea.setPrefRowCount(3);
        parseArea.setWrapText(true);
        Label hintLabel = new Label();
        hintLabel.getStyleClass().add("hint-label");

        GridPane grid = Dialogs.formGrid();
        grid.add(new Label("Latitude"), 1, 0);
        grid.add(new Label("Longitude"), 2, 0);
        grid.add(new Label("DDM"), 3, 0);
        // Same icons as the main window's coordinates button ("📍 … ⇢ 🏁").
        addRow(grid, 1, "📍", "Start", start);
        addRow(grid, 2, "🏁", "End", stop);
        grid.add(new Label("Parse from:"), 0, 3);
        grid.add(parseArea, 1, 3, 4, 1);
        Button parseStart = new Button("Parse → Start");
        Button parseStop = new Button("Parse → End");
        grid.add(new HBox(8, parseStart, parseStop), 1, 4, 4, 1);
        grid.add(hintLabel, 1, 5, 4, 1);

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().setContent(grid);
        // ButtonType.CANCEL also re-enables the dialog window's native close (X) button —
        // JavaFX disables it unless a CANCEL_CLOSE-data button is present.
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, saveType);
        Dialogs.applyStylesheet(dlg.getDialogPane());

        Node saveButtonNode = dlg.getDialogPane().lookupButton(saveType);
        Runnable refresh = () -> {
            String problem = null;
            for (Row row : new Row[] {start, stop}) {
                Coordinates.LatLon value = row.value();
                row.ddm.setText(value == null ? "—" : Coordinates.toDdm(value.lat(), value.lon()));
                if (value == null && !row.isEmpty()) {
                    problem = (row == start ? "Start" : "End") + " needs a valid latitude and longitude.";
                }
            }
            if (problem == null && start.isEmpty() && !stop.isEmpty()) problem = "Set a start point first.";
            hintLabel.setText(problem == null ? "" : problem);
            saveButtonNode.setDisable(problem != null);
        };
        for (Row row : new Row[] {start, stop}) {
            row.lat.textProperty().addListener((o, a, b) -> refresh.run());
            row.lon.textProperty().addListener((o, a, b) -> refresh.run());
        }
        refresh.run();

        wireParse(parseStart, parseArea, hintLabel, refresh, start, Coordinates::tryParse);
        wireParse(parseStop, parseArea, hintLabel, refresh, stop, Coordinates::tryParseLast);

        Optional<ButtonType> result = dlg.showAndWait();
        if (result.isEmpty() || result.get() != saveType) return Optional.empty();
        return Optional.of(new Result(start.value(), stop.value()));
    }

    private static void addRow(GridPane grid, int rowIndex, String icon, String name, Row row) {
        Button clear = new Button("✕");
        clear.setTooltip(new Tooltip("Clear " + name.toLowerCase() + " point"));
        clear.setOnAction(e -> row.clear());
        grid.add(new Label(icon + " " + name + ":"), 0, rowIndex);
        grid.add(row.lat, 1, rowIndex);
        grid.add(row.lon, 2, rowIndex);
        grid.add(row.ddm, 3, rowIndex);
        grid.add(clear, 4, rowIndex);
    }

    private static void wireParse(Button button, TextArea source, Label hintLabel, Runnable refresh,
                                  Row target, Function<String, Optional<Coordinates.LatLon>> parser) {
        button.setOnAction(e -> parser.apply(source.getText()).ifPresentOrElse(
                coords -> {
                    target.set(coords);
                    refresh.run();
                },
                () -> hintLabel.setText("Couldn't parse coordinates from input.")));
    }
}
