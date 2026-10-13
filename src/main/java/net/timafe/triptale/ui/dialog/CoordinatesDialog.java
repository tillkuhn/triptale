package net.timafe.triptale.ui.dialog;

import javafx.application.Platform;
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
import net.timafe.triptale.map.PlaceLookup;
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
 *
 * <p>Each row also has a free-text place ("Soest, DE", todo 51), filled by the 🔍 reverse lookup
 * or typed by hand. The lookup's progress and outcome live on the 🔍 button and its tooltip:
 * ⏳ while running, back to 🔍 on success, ⚠️ with the error otherwise (still clickable to retry).
 */
public final class CoordinatesDialog {

    /**
     * Outcome of the dialog: a {@code null} point means it should be unset on the entry, a
     * {@code null} place likewise. Cancel is reported as an empty {@link Optional} from
     * {@link #showAndWait} instead.
     */
    public record Result(Coordinates.LatLon start, Coordinates.LatLon stop, String startPlace, String stopPlace) {}

    private static final int COORD_COLUMNS = 7;
    private static final int PLACE_COLUMNS = 12;
    private static final String LOOKUP_IDLE = "🔍";
    private static final String LOOKUP_BUSY = "⏳";
    private static final String LOOKUP_FAILED = "⚠️";
    private static final String LOOKUP_HINT = "Look up place name (OpenStreetMap)";

    /** One Start/End row: lat/lon fields, DDM preview, place field and the lookup, open-in-maps and clear buttons. */
    private static final class Row {
        final String name;
        final TextField lat;
        final TextField lon;
        final Label ddm = new Label("—");
        final TextField place;
        final Button lookup = new Button(LOOKUP_IDLE);
        final Button open = new Button("🔗");
        final Button clearButton = new Button("🗑");
        boolean busy;

        Row(String name, Double initialLat, Double initialLon, String initialPlace) {
            this.name = name;
            lat = new TextField(initialLat == null ? "" : initialLat.toString());
            lon = new TextField(initialLon == null ? "" : initialLon.toString());
            place = new TextField(initialPlace == null ? "" : initialPlace);
            lat.setPrefColumnCount(COORD_COLUMNS);
            lon.setPrefColumnCount(COORD_COLUMNS);
            place.setPrefColumnCount(PLACE_COLUMNS);
            place.setPromptText("Place, CC");
        }

        boolean isEmpty() {
            return lat.getText().isBlank() && lon.getText().isBlank() && place.getText().isBlank();
        }

        boolean hasCoordinates() {
            return !lat.getText().isBlank() || !lon.getText().isBlank();
        }

        /** Both fields as a valid coordinate pair, or null if either is blank/unparseable/out of range. */
        Coordinates.LatLon value() {
            Double la = UiText.parseDecimal(lat.getText());
            Double lo = UiText.parseDecimal(lon.getText());
            if (la == null || lo == null || !Coordinates.isValid(la, lo)) return null;
            return new Coordinates.LatLon(la, lo);
        }

        String placeValue() {
            return place.getText().isBlank() ? null : place.getText().trim();
        }

        void set(Coordinates.LatLon coords) {
            lat.setText(Double.toString(coords.lat()));
            lon.setText(Double.toString(coords.lon()));
        }

        /** Clears the point and its place: a place belongs to its point. */
        void clear() {
            lat.clear();
            lon.clear();
            place.clear();
        }

        void lookupState(String icon, String tooltip) {
            lookup.setText(icon);
            lookup.setTooltip(new Tooltip(tooltip));
        }
    }

    private final BrowserLauncher browser;
    private final PlaceLookup placeLookup;
    private final boolean online;

    /** {@code online} is the main window's connectivity state; the dialog is modal, so it can't change meanwhile. */
    public CoordinatesDialog(BrowserLauncher browser, PlaceLookup placeLookup, boolean online) {
        this.browser = browser;
        this.placeLookup = placeLookup;
        this.online = online;
    }

    /** Empty when the user cancelled or dismissed the dialog. */
    public Optional<Result> showAndWait(Double startLat, Double startLon, Double stopLat, Double stopLon,
                                        String startPlace, String stopPlace) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("🧭 Coordinator");
        dlg.setHeaderText("Start and end point");

        Row start = new Row("Start", startLat, startLon, startPlace);
        Row stop = new Row("End", stopLat, stopLon, stopPlace);
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
        grid.add(new Label("Place"), 4, 0);
        // Same icons as the main window's coordinates button ("📍 ✓  🏁 –").
        addRow(grid, 1, "📍", "Start", start);
        addRow(grid, 2, "🏁", "End", stop);
        grid.add(new Label("Parse from:"), 0, 3);
        grid.add(parseArea, 1, 3, 7, 1);
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
        grid.add(new HBox(8, parseStart, parseStop, spacer, route), 1, 4, 7, 1);
        grid.add(hintLabel, 1, 5, 7, 1);

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
                refreshLookup(row);
                if (value == null && row.hasCoordinates()) {
                    problem = row.name + " needs a valid latitude and longitude.";
                }
            }
            if (problem == null && !start.hasCoordinates() && stop.hasCoordinates()) problem = "Set a start point first.";
            route.setDisable(start.value() == null || stop.value() == null);
            hintLabel.setText(problem == null ? "" : problem);
            saveButtonNode.setDisable(problem != null);
        };
        for (Row row : new Row[] {start, stop}) {
            row.lat.textProperty().addListener((o, a, b) -> refresh.run());
            row.lon.textProperty().addListener((o, a, b) -> refresh.run());
            row.place.textProperty().addListener((o, a, b) -> row.clearButton.setDisable(row.isEmpty()));
            // A changed point makes the last lookup's outcome stale.
            Runnable resetLookup = () -> { if (!row.busy) row.lookupState(LOOKUP_IDLE, LOOKUP_HINT); };
            row.lat.textProperty().addListener((o, a, b) -> resetLookup.run());
            row.lon.textProperty().addListener((o, a, b) -> resetLookup.run());
            row.lookup.setOnAction(e -> lookUp(row));
        }
        refresh.run();

        wireParse(parseStart, parseArea, hintLabel, refresh, start, Coordinates::tryParse);
        wireParse(parseStop, parseArea, hintLabel, refresh, stop, Coordinates::tryParseLast);

        Optional<ButtonType> result = dlg.showAndWait();
        if (result.isEmpty() || result.get() != saveType) return Optional.empty();
        return Optional.of(new Result(start.value(), stop.value(), start.placeValue(), stop.placeValue()));
    }

    private void addRow(GridPane grid, int rowIndex, String icon, String name, Row row) {
        row.open.setTooltip(new Tooltip("Show " + name.toLowerCase() + " point on Google Maps"));
        row.open.setOnAction(e -> {
            Coordinates.LatLon value = row.value();
            if (value != null) browser.open(Coordinates.googleMapsUrl(value, null));
        });
        row.clearButton.setTooltip(new Tooltip("Clear " + name.toLowerCase() + " point"));
        row.clearButton.setOnAction(e -> row.clear());
        row.lookupState(LOOKUP_IDLE, LOOKUP_HINT);
        grid.addRow(rowIndex, new Label(icon + " " + name + ":"), row.lat, row.lon, row.ddm, row.place,
                row.lookup, row.open, row.clearButton);
    }

    /** Enables 🔍 only with a valid point, while online and not already looking up. */
    private void refreshLookup(Row row) {
        if (!online) {
            row.lookup.setDisable(true);
            row.lookupState(LOOKUP_IDLE, "Offline — re-check connectivity (top right) and reopen");
        } else {
            row.lookup.setDisable(row.busy || row.value() == null);
        }
    }

    /** Runs the lookup off the FX thread; a result for a point edited meanwhile is dropped. */
    private void lookUp(Row row) {
        Coordinates.LatLon point = row.value();
        if (point == null || row.busy) return;
        row.busy = true;
        row.lookupState(LOOKUP_BUSY, "Looking up " + row.name.toLowerCase() + "…");
        refreshLookup(row);
        Thread.ofVirtual().name("place-lookup").start(() -> {
            String error = null;
            PlaceLookup.Place found = null;
            try {
                found = placeLookup.lookup(point.lat(), point.lon());
            } catch (RuntimeException e) {
                error = e instanceof PlaceLookup.LookupException ? e.getMessage() : UiText.describe(e);
            }
            PlaceLookup.Place place = found;
            String message = error;
            Platform.runLater(() -> {
                row.busy = false;
                if (!point.equals(row.value())) {
                    row.lookupState(LOOKUP_IDLE, LOOKUP_HINT);
                } else if (place != null) {
                    row.place.setText(place.label());
                    row.lookupState(LOOKUP_IDLE, "Found " + place.label() + " (" + place.kind() + ") via OpenStreetMap");
                } else {
                    row.lookupState(LOOKUP_FAILED, message);
                }
                refreshLookup(row);
            });
        });
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
