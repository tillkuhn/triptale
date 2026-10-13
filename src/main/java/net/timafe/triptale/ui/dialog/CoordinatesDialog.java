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
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import net.timafe.triptale.ui.BrowserLauncher;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.ui.UiText;
import net.timafe.triptale.util.Coordinates;

import java.util.Optional;
import java.util.function.Function;

/**
 * Edits an entry's start and end point: manual lat/lon entry per row, or parsed from a pasted
 * URL/GPX/GeoJSON into either row. A start alone is a complete answer (a day spent in one
 * place); an end requires a start. Each row can be opened in Google Maps (🔗) or cleared (🗑),
 * like the rows in {@link LinksDialog}; with both points set, the route start → end can be opened too.
 */
public final class CoordinatesDialog {

    /**
     * Outcome of the dialog: a {@code null} point means it should be unset on the entry.
     * Cancel is reported as an empty {@link Optional} from {@link #showAndWait} instead.
     */
    public record Result(Coordinates.LatLon start, Coordinates.LatLon stop) {}

    private static final int COORD_COLUMNS = 11;

    /** One Start/End row: its two fields, the DDM preview label and the open-in-maps and clear buttons. */
    private record Row(TextField lat, TextField lon, Label ddm, Button open, Button clearButton) {

        static Row of(Double initialLat, Double initialLon) {
            Row row = new Row(new TextField(initialLat == null ? "" : initialLat.toString()),
                    new TextField(initialLon == null ? "" : initialLon.toString()), new Label("—"),
                    new Button("🔗"), new Button("🗑"));
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

    private final BrowserLauncher browser;

    public CoordinatesDialog(BrowserLauncher browser) {
        this.browser = browser;
    }

    /** Empty when the user cancelled or dismissed the dialog. */
    public Optional<Result> showAndWait(Double startLat, Double startLon, Double stopLat, Double stopLon) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("🧭 Coordinator");
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
        // Same icons as the main window's coordinates button ("📍 ✓  🏁 –").
        addRow(grid, 1, "📍", "Start", start);
        addRow(grid, 2, "🏁", "End", stop);
        grid.add(new Label("Parse from:"), 0, 3);
        grid.add(parseArea, 1, 3, 5, 1);
        Button parseStart = new Button("Parse → Start");
        Button parseStop = new Button("Parse → End");
        Button route = new Button("🔗 Route");
        route.setTooltip(new Tooltip("Show route start → end on Google Maps"));
        route.setOnAction(e -> {
            if (start.value() != null && stop.value() != null) {
                browser.open(Coordinates.googleMapsUrl(start.value(), stop.value()));
            }
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        grid.add(new HBox(8, parseStart, parseStop, spacer, route), 1, 4, 5, 1);
        grid.add(hintLabel, 1, 5, 5, 1);

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
                row.open.setDisable(value == null);
                row.clearButton.setDisable(row.isEmpty());
                if (value == null && !row.isEmpty()) {
                    problem = (row == start ? "Start" : "End") + " needs a valid latitude and longitude.";
                }
            }
            if (problem == null && start.isEmpty() && !stop.isEmpty()) problem = "Set a start point first.";
            route.setDisable(start.value() == null || stop.value() == null);
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

    private void addRow(GridPane grid, int rowIndex, String icon, String name, Row row) {
        row.open.setTooltip(new Tooltip("Show " + name.toLowerCase() + " point on Google Maps"));
        row.open.setOnAction(e -> {
            Coordinates.LatLon value = row.value();
            if (value != null) browser.open(Coordinates.googleMapsUrl(value, null));
        });
        row.clearButton.setTooltip(new Tooltip("Clear " + name.toLowerCase() + " point"));
        row.clearButton.setOnAction(e -> row.clear());
        grid.addRow(rowIndex, new Label(icon + " " + name + ":"), row.lat, row.lon, row.ddm, row.open, row.clearButton);
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
