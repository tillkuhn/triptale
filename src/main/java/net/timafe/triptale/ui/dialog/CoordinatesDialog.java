package net.timafe.triptale.ui.dialog;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
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
import net.timafe.triptale.domain.Weather;
import net.timafe.triptale.domain.WeatherCode;
import net.timafe.triptale.i18n.ExportTexts;
import net.timafe.triptale.i18n.TripLanguage;
import net.timafe.triptale.map.PlaceLookup;
import net.timafe.triptale.ui.BrowserLauncher;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.ui.UiText;
import net.timafe.triptale.util.Coordinates;
import net.timafe.triptale.weather.OpenMeteoClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Edits an entry's start and end point: manual lat/lon entry per row, or parsed from a pasted
 * URL/GPX/GeoJSON into either row. A start alone is a complete answer (a day spent in one
 * place); an end requires a start. Each row can be opened in Google Maps (🔗) or cleared (🗑),
 * like the rows in {@link LinksDialog}; with both points set, the route start → end can be opened too,
 * next to a read-only straight-line distance (haversine, {@link Coordinates#distanceMeters}) shown
 * rounded to the whole km — not the entry's travel {@code distance} field, which the user fills in
 * separately and may differ (actual route vs. as-the-crow-flies).
 *
 * <p>Each row also has a free-text place ("Soest, DE", todo 51), filled by the 🔍 reverse lookup
 * or typed by hand. 🔍 works the other way too (todo 59): with a place but no coordinates it
 * fills in the place's centre point and appends the hit's country code to the typed name. The
 * lookup's progress and outcome live on the 🔍 button and its tooltip: ⏳ while running, back to 🔍 on success, ⚠️ with the error otherwise (still
 * clickable to retry). Editing the row resets it.
 *
 * <p>A weather row (todo 65) shows the day's recorded weather, named with its date and the place
 * it was fetched for, so it reads as history for a specific point, not today's forecast
 * somewhere vague, e.g. "15–22° partly cloudy on October 23rd 2026 @ Rome, IT". 🔄 fetches (or
 * re-fetches, overwriting without asking, like 🔍) for the start point's <em>current</em> field
 * values, not necessarily what's saved; 🔗 opens the Open-Meteo historical archive's raw JSON
 * for the same point and date, to check the source behind the recorded min/max/code (unlike the
 * live forecast fetch, the archive stays valid for any past date, not just ~92 days); 🗑 clears
 * the recorded weather. All three are disabled without a start point or recorded weather, or
 * offline, same gating as the place lookups. Before a first fetch, the empty-state message
 * previews the current Start row so it's clear what 🔄 would fetch for.
 */
public final class CoordinatesDialog {

    /**
     * Outcome of the dialog: a {@code null} point means it should be unset on the entry, a
     * {@code null} place likewise. {@code weather} (todo 65) is whatever the weather row held at
     * confirm time — unchanged if the user never touched it, {@code null} after 🗑. Cancel is
     * reported as an empty {@link Optional} from {@link #showAndWait} instead.
     */
    public record Result(Coordinates.LatLon start, Coordinates.LatLon stop, String startPlace, String stopPlace,
                          Weather weather) {}

    private static final int COORD_COLUMNS = 7;
    private static final int PLACE_COLUMNS = 16;
    private static final String LOOKUP_IDLE = "🔍";
    private static final String LOOKUP_BUSY = "⏳";
    private static final String LOOKUP_FAILED = "⚠️";

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
        /** Icon and tooltip of the last lookup's outcome; null while idle (the hint follows the row). */
        String outcomeIcon;
        String outcomeText;

        Row(String name, Double initialLat, Double initialLon, String initialPlace) {
            this.name = name;
            lat = new TextField(initialLat == null ? "" : initialLat.toString());
            lon = new TextField(initialLon == null ? "" : initialLon.toString());
            place = new TextField(initialPlace == null ? "" : initialPlace);
            lat.setPrefColumnCount(COORD_COLUMNS);
            lon.setPrefColumnCount(COORD_COLUMNS);
            place.setPrefColumnCount(PLACE_COLUMNS);
            place.setPromptText("Place, CC");
            // The dialog is sized when it opens; a DDM that appears later (lookup, parse) must
            // not squeeze the icon buttons to "…". 8.5em fits the widest, "90°00′S 180°00′W".
            ddm.setStyle("-fx-min-width: 8.5em;");
            for (Button b : new Button[] {lookup, open, clearButton}) b.setMinWidth(Region.USE_PREF_SIZE);
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

        /** Forward lookup applies: a place to search for and no coordinates to keep. */
        boolean canSearch() {
            return !hasCoordinates() && placeValue() != null;
        }

        String lookupHint() {
            if (value() != null) return "Look up the place name for this point (OpenStreetMap)";
            if (canSearch()) return "Find coordinates for “" + placeValue() + "” (OpenStreetMap)";
            return "Enter a point, or a place like “Bangkok, TH”, to look up";
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

        void outcome(String icon, String tooltip) {
            outcomeIcon = icon;
            outcomeText = tooltip;
        }
    }

    private final BrowserLauncher browser;
    private final PlaceLookup placeLookup;
    private final OpenMeteoClient weatherClient;
    private final boolean online;

    /** {@code online} is the main window's connectivity state; the dialog is modal, so it can't change meanwhile. */
    public CoordinatesDialog(BrowserLauncher browser, PlaceLookup placeLookup, OpenMeteoClient weatherClient,
                             boolean online) {
        this.browser = browser;
        this.placeLookup = placeLookup;
        this.weatherClient = weatherClient;
        this.online = online;
    }

    /**
     * Empty when the user cancelled or dismissed the dialog. {@code canSaveDay} only picks the
     * confirm button's label (see {@link Dialogs#addSaveDayButtons}). {@code date} names the
     * weather row and is the day {@code weather} was recorded for; {@code date == null} disables
     * weather fetching entirely (no day to fetch for).
     */
    public Optional<Result> showAndWait(Double startLat, Double startLon, Double stopLat, Double stopLon,
                                        String startPlace, String stopPlace, Weather weather,
                                        LocalDate date, boolean canSaveDay) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("🌐 Coordinator");
        dlg.setHeaderText("Start and end point");

        Row start = new Row("Start", startLat, startLon, startPlace);
        Row stop = new Row("End", stopLat, stopLon, stopPlace);
        TextArea parseArea = new TextArea();
        parseArea.setPromptText("Paste a Google Maps URL, GPX <trkpt>, or GeoJSON [lon, lat] array");
        parseArea.setPrefRowCount(3);
        parseArea.setWrapText(true);
        Label hintLabel = new Label();
        hintLabel.getStyleClass().add("hint-label");

        // Weather row (todo 65): named by date so it reads as history, not today's forecast.
        ExportTexts weatherTexts = ExportTexts.of(TripLanguage.EN);
        Label weatherLabel = new Label();
        weatherLabel.setWrapText(true);
        Button weatherUpdate = new Button("🔄");
        Button weatherOpen = new Button("🔗");
        Button weatherClear = new Button("🗑");
        for (Button b : new Button[] {weatherUpdate, weatherOpen, weatherClear}) b.setMinWidth(Region.USE_PREF_SIZE);
        Weather[] currentWeather = {weather};
        boolean[] weatherBusy = {false};
        Runnable renderWeather = () -> {
            Weather w = currentWeather[0];
            String livePlace = wherePreview(start);
            if (date == null) {
                weatherLabel.setText("No date selected.");
            } else if (weatherBusy[0]) {
                weatherLabel.setText("Fetching weather for " + weatherTexts.longDate(date)
                        + (livePlace == null ? "" : " (" + livePlace + ")") + "…");
            } else if (w == null) {
                weatherLabel.setText("Not recorded for " + weatherTexts.longDate(date) + " yet"
                        + (livePlace == null ? "" : " — fetch for " + livePlace) + ".");
            } else {
                String value = w.tempMin() == w.tempMax() ? w.tempMin() + "°" : w.tempMin() + "–" + w.tempMax() + "°";
                weatherLabel.setText(WeatherCode.fromWmo(w.code()).emoji() + " " + value + " "
                        + weatherTexts.weatherLabel(w.code()) + " on " + weatherTexts.longDate(date)
                        + (w.place() == null ? "" : " @ " + w.place()));
            }
            weatherClear.setDisable(w == null || weatherBusy[0]);
            boolean canFetch = online && date != null && start.value() != null && !weatherBusy[0];
            weatherUpdate.setDisable(!canFetch);
            weatherUpdate.setTooltip(new Tooltip(!online ? "Offline — re-check connectivity (top right) and reopen"
                    : date == null ? "No date selected"
                    : start.value() == null ? "Set a start point first"
                    : "Fetch weather for the start point (Open-Meteo); overwrites without asking, like 🔍"));
            boolean canOpen = w != null && date != null && start.value() != null;
            weatherOpen.setDisable(!canOpen);
            weatherOpen.setTooltip(new Tooltip(!canOpen
                    ? "Fetch weather first to look up the source data"
                    : "Check the source data for the start point and date (Open-Meteo historical archive)"));
        };
        weatherUpdate.setOnAction(e -> {
            Coordinates.LatLon point = start.value();
            if (point == null || date == null || weatherBusy[0]) return;
            String place = start.placeValue();
            weatherBusy[0] = true;
            renderWeather.run();
            Thread.ofVirtual().name("weather-fetch").start(() -> {
                Weather fetched = null;
                String error = null;
                try {
                    Weather result = weatherClient.forecast(point.lat(), point.lon(), date);
                    fetched = place == null ? result : new Weather(result.tempMin(), result.tempMax(), result.code(), place);
                } catch (RuntimeException ex) {
                    error = ex instanceof OpenMeteoClient.WeatherException ? ex.getMessage() : UiText.describe(ex);
                }
                Weather result = fetched;
                String message = error;
                Platform.runLater(() -> {
                    weatherBusy[0] = false;
                    if (result != null) {
                        currentWeather[0] = result;
                    } else {
                        hintLabel.setText(message);
                    }
                    renderWeather.run();
                });
            });
        });
        weatherOpen.setOnAction(e -> {
            Coordinates.LatLon point = start.value();
            if (currentWeather[0] == null || point == null || date == null) return;
            browser.open(OpenMeteoClient.archiveUrl(point.lat(), point.lon(), date));
        });
        weatherClear.setOnAction(e -> {
            currentWeather[0] = null;
            renderWeather.run();
        });

        GridPane grid = Dialogs.formGrid();
        grid.add(new Label("Latitude"), 1, 0);
        grid.add(new Label("Longitude"), 2, 0);
        grid.add(new Label("DDM"), 3, 0);
        grid.add(new Label("Place"), 4, 0);
        // Same icons as the main window's coordinates button ("📍 ✓  🏁 –").
        addRow(grid, 1, "📍", "Start", start);
        addRow(grid, 2, "🏁", "End", stop);
        grid.add(new Label("🌦️ Weather:"), 0, 3);
        Region weatherSpacer = new Region();
        HBox.setHgrow(weatherSpacer, Priority.ALWAYS);
        HBox weatherRow = new HBox(8, weatherLabel, weatherSpacer, weatherUpdate, weatherOpen, weatherClear);
        weatherRow.setAlignment(Pos.CENTER_LEFT);
        grid.add(weatherRow, 1, 3, 7, 1);
        grid.add(new Label("Parse from:"), 0, 4);
        grid.add(parseArea, 1, 4, 7, 1);
        Button parseStart = new Button("Parse → Start");
        Button parseStop = new Button("Parse → End");
        TextField distanceField = new TextField();
        distanceField.setEditable(false);
        distanceField.setFocusTraversable(false);
        distanceField.setPrefColumnCount(7);
        distanceField.setStyle("-fx-min-width: 6em;");
        distanceField.setTooltip(new Tooltip(
                "As the crow flies, rounded (not the travel route); copy into the Distance field if useful"));
        Button route = new Button("🔗 Route");
        route.setTooltip(new Tooltip("Show route start → end on Google Maps"));
        route.setOnAction(e -> {
            if (start.value() != null && stop.value() != null) {
                browser.open(Coordinates.googleMapsUrl(start.value(), stop.value()));
            }
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        grid.add(new HBox(8, parseStart, parseStop, spacer, distanceField, route), 1, 5, 7, 1);
        grid.add(hintLabel, 1, 6, 7, 1);

        dlg.getDialogPane().setContent(grid);
        ButtonType saveType = Dialogs.addSaveDayButtons(dlg.getDialogPane(), canSaveDay);
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
            boolean haveBoth = start.value() != null && stop.value() != null;
            route.setDisable(!haveBoth);
            distanceField.setText(haveBoth
                    ? formatKm(Coordinates.distanceMeters(start.value(), stop.value()) / 1000.0) : "");
            hintLabel.setText(problem == null ? "" : problem);
            saveButtonNode.setDisable(problem != null);
            renderWeather.run();
        };
        for (Row row : new Row[] {start, stop}) {
            // Any edit makes the last lookup's outcome stale.
            Runnable changed = () -> {
                if (!row.busy) row.outcome(null, null);
                refresh.run();
            };
            row.lat.textProperty().addListener((o, a, b) -> changed.run());
            row.lon.textProperty().addListener((o, a, b) -> changed.run());
            row.place.textProperty().addListener((o, a, b) -> changed.run());
            row.lookup.setOnAction(e -> lookUp(row));
        }
        refresh.run();

        wireParse(parseStart, parseArea, hintLabel, refresh, start, Coordinates::tryParse);
        wireParse(parseStop, parseArea, hintLabel, refresh, stop, Coordinates::tryParseLast);

        Optional<ButtonType> result = dlg.showAndWait();
        if (result.isEmpty() || result.get() != saveType) return Optional.empty();
        return Optional.of(new Result(start.value(), stop.value(), start.placeValue(), stop.placeValue(),
                currentWeather[0]));
    }

    /** Whole km with unit, e.g. {@code "12 km"} — pastes straight into the Distance field; no decimals, since a straight-line distance is only ever approximate. */
    private static String formatKm(double km) {
        return BigDecimal.valueOf(km).setScale(0, RoundingMode.HALF_UP).toPlainString() + " km";
    }

    /** The Start row's current place, else its coordinates, else null — what 🔄 would fetch weather for right now. */
    private static String wherePreview(Row start) {
        if (start.placeValue() != null) return start.placeValue();
        Coordinates.LatLon point = start.value();
        return point == null ? null : Coordinates.toDdm(point.lat(), point.lon());
    }

    private void addRow(GridPane grid, int rowIndex, String icon, String name, Row row) {
        row.open.setTooltip(new Tooltip("Show " + name.toLowerCase() + " point on Google Maps"));
        row.open.setOnAction(e -> {
            Coordinates.LatLon value = row.value();
            if (value != null) browser.open(Coordinates.googleMapsUrl(value, null));
        });
        row.clearButton.setTooltip(new Tooltip("Clear " + name.toLowerCase() + " point"));
        row.clearButton.setOnAction(e -> row.clear());
        row.lookup.setTooltip(new Tooltip());
        grid.addRow(rowIndex, new Label(icon + " " + name + ":"), row.lat, row.lon, row.ddm, row.place,
                row.lookup, row.open, row.clearButton);
    }

    /**
     * Shows the lookup state on 🔍: enabled with a valid point (reverse) or a place without
     * coordinates (forward), while online and not already looking up.
     */
    private void refreshLookup(Row row) {
        String icon;
        String tooltip;
        if (!online) {
            icon = LOOKUP_IDLE;
            tooltip = "Offline — re-check connectivity (top right) and reopen";
            row.lookup.setDisable(true);
        } else {
            if (row.busy) {
                icon = LOOKUP_BUSY;
                tooltip = "Looking up " + row.name.toLowerCase() + "…";
            } else if (row.outcomeIcon != null) {
                icon = row.outcomeIcon;
                tooltip = row.outcomeText;
            } else {
                icon = LOOKUP_IDLE;
                tooltip = row.lookupHint();
            }
            row.lookup.setDisable(row.busy || (row.value() == null && !row.canSearch()));
        }
        row.lookup.setText(icon);
        row.lookup.getTooltip().setText(tooltip);
    }

    /** Reverse lookup for a valid point, otherwise forward lookup for the place. */
    private void lookUp(Row row) {
        if (row.busy) return;
        if (row.value() != null) {
            reverse(row);
        } else if (row.canSearch()) {
            forward(row);
        }
    }

    /** Runs the reverse lookup off the FX thread; a result for a point edited meanwhile is dropped. */
    private void reverse(Row row) {
        Coordinates.LatLon point = row.value();
        runLookup(row, () -> placeLookup.lookup(point.lat(), point.lon()), () -> point.equals(row.value()), place -> {
            row.place.setText(place.label());
            row.outcome(LOOKUP_IDLE, "Found " + place.label() + " (" + place.kind() + ") via OpenStreetMap");
        });
    }

    /** Runs the forward lookup off the FX thread; dropped if the place was edited or coordinates typed meanwhile. */
    private void forward(Row row) {
        String query = row.placeValue();
        runLookup(row, () -> placeLookup.search(query),
                () -> row.canSearch() && query.equals(row.placeValue()), match -> {
            row.set(new Coordinates.LatLon(match.lat(), match.lon()));
            row.place.setText(PlaceLookup.labelFor(query, match.place()));
            row.outcome(LOOKUP_IDLE, "Found " + match.place().label() + " (" + match.place().kind()
                    + ") via OpenStreetMap: approx. centre, refine if needed");
        });
    }

    /**
     * Runs {@code call} on a virtual thread and hands its result to {@code onSuccess} on the
     * FX thread; a failure shows ⚠️ with the error. Either is dropped (back to idle) when
     * {@code stillCurrent} says the row was edited meanwhile.
     */
    private <T> void runLookup(Row row, Supplier<T> call, BooleanSupplier stillCurrent, Consumer<T> onSuccess) {
        row.busy = true;
        row.outcome(null, null);
        refreshLookup(row);
        Thread.ofVirtual().name("place-lookup").start(() -> {
            T found = null;
            String error = null;
            try {
                found = call.get();
            } catch (RuntimeException e) {
                error = e instanceof PlaceLookup.LookupException ? e.getMessage() : UiText.describe(e);
            }
            T result = found;
            String message = error;
            Platform.runLater(() -> {
                row.busy = false;
                if (!stillCurrent.getAsBoolean()) {
                    row.outcome(null, null);
                } else if (result != null) {
                    onSuccess.accept(result);
                } else {
                    row.outcome(LOOKUP_FAILED, message);
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
