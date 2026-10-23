package net.timafe.triptale.ui.dialog;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.Callback;
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
 * <p>A weather row (todo 65) holds min/max temperature, a condition dropdown (the 8
 * {@link WeatherCode} buckets, not the raw WMO code) and a free-text place, laid out in the same
 * grid columns as the Start/End rows — editable by hand so a day's weather still works offline or
 * when fetching fails, not just as a read-only fetch result. All three of min/max/condition must
 * be set together (or all blank); place is optional either way. 🔄 fetches (or re-fetches,
 * overwriting without asking, like 🔍) for the start point's <em>current</em> field values, not
 * necessarily what's saved — forecast or archive, picked automatically by {@link OpenMeteoClient#fetch}
 * depending on how old the day is, so any past date works, not just the last ~92 days; 🔗 opens
 * the Open-Meteo historical archive's raw JSON for the same point and date, to check the source
 * behind a recorded or about-to-be-typed value (only needs a start point and date, not recorded
 * weather); 🗑 clears all four fields. A manually
 * picked condition loses the raw WMO code's finer granularity (e.g. "light drizzle" vs "moderate
 * drizzle") down to the bucket's representative code — harmless, since every display/export path
 * already only ever shows the bucket, never the raw code, regardless of source.
 *
 * <p>Elevation (todo 72) rides along on {@code Weather} but has no grid column of its own — the
 * row is already tight on space — so it isn't directly user-editable: it's whatever the last
 * fetch returned (carried through 🔄, preserved across edits to the other fields, cleared by
 * 🗑), surfaced as a tooltip on the "🌦️ Weather:" label when present.
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

    /**
     * The weather row: min/max temp, a condition dropdown and a free-text place, plus
     * fetch/open-source/clear buttons — same shape as {@link Row} so it lines up in the same
     * grid columns. All-or-nothing: min, max and condition must all be set (or all blank) to
     * save; place is optional either way.
     */
    private static final class WeatherRow {
        private static final String IDLE = "🔄";
        private static final String BUSY = "⏳";
        // Half the old 4-column width: the "min"/"max" label next to each field now carries the
        // meaning the wider field + "Min/Max °C" prompt text used to.
        private static final int TEMP_COLUMNS = 2;

        final TextField min = new TextField();
        final TextField max = new TextField();
        final Label minLabel = new Label("min");
        final Label maxLabel = new Label("max");
        final HBox minBox = new HBox(4, min, minLabel);
        final HBox maxBox = new HBox(4, max, maxLabel);
        final ComboBox<WeatherCode> condition = new ComboBox<>(FXCollections.observableArrayList(WeatherCode.values()));
        final TextField place = new TextField();
        final Button update = new Button(IDLE);
        final Button open = new Button("🔗");
        final Button clearButton = new Button("🗑");
        boolean busy;
        /**
         * Meters above sea level (todo 72), carried through from the last fetch — not directly
         * user-editable (no spare grid column for it; see the class doc), preserved across manual
         * edits to the other fields, surfaced as a tooltip (see {@link #showAndWait}).
         */
        Integer elevation;

        WeatherRow(Weather initial, ExportTexts texts) {
            min.setPrefColumnCount(TEMP_COLUMNS);
            max.setPrefColumnCount(TEMP_COLUMNS);
            min.setPromptText("°C");
            max.setPromptText("°C");
            minLabel.getStyleClass().add("weather-unit-label");
            maxLabel.getStyleClass().add("weather-unit-label");
            minBox.setAlignment(Pos.CENTER_LEFT);
            maxBox.setAlignment(Pos.CENTER_LEFT);
            normalizeOnFocusLost(min);
            normalizeOnFocusLost(max);
            place.setPrefColumnCount(PLACE_COLUMNS);
            place.setPromptText("Place, CC");
            condition.setPromptText("Condition");
            Callback<ListView<WeatherCode>, ListCell<WeatherCode>> cellFactory = lv -> new ListCell<>() {
                @Override
                protected void updateItem(WeatherCode item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.emoji() + " " + texts.weatherLabel(item.wmoCode()));
                }
            };
            condition.setCellFactory(cellFactory);
            condition.setButtonCell(cellFactory.call(null));
            condition.setStyle("-fx-pref-width: 11em;");
            for (Button b : new Button[] {update, open, clearButton}) b.setMinWidth(Region.USE_PREF_SIZE);
            set(initial);
        }

        /** Populates all four fields from a {@link Weather}, or blanks them for {@code null}. */
        void set(Weather w) {
            min.setText(w == null ? "" : formatTemp(w.tempMin()));
            max.setText(w == null ? "" : formatTemp(w.tempMax()));
            condition.setValue(w == null ? null : WeatherCode.fromWmo(w.code()));
            place.setText(w == null || w.place() == null ? "" : w.place());
            elevation = w == null ? null : w.elevation();
        }

        void clear() { set(null); }

        boolean isBlank() {
            return min.getText().isBlank() && max.getText().isBlank()
                    && condition.getValue() == null && place.getText().isBlank();
        }

        /** Any field touched at all — including a lone place, which alone isn't a valid weather entry. */
        boolean anySet() {
            return !min.getText().isBlank() || !max.getText().isBlank()
                    || condition.getValue() != null || !place.getText().isBlank();
        }

        boolean complete() {
            return minValue() != null && maxValue() != null && condition.getValue() != null;
        }

        Integer minValue() { return parseTemp(min.getText()); }
        Integer maxValue() { return parseTemp(max.getText()); }

        private static Integer parseTemp(String text) {
            if (text.isBlank()) return null;
            String stripped = text.strip();
            if (stripped.endsWith("°")) stripped = stripped.substring(0, stripped.length() - 1).strip();
            Double d = UiText.parseDecimal(stripped);
            return d == null ? null : (int) Math.round(d);
        }

        /** {@code "8°"}, or {@code ""} for {@code null} — matches the "value unit" style of the distance/altitude fields. */
        private static String formatTemp(Integer value) {
            return value == null ? "" : value + "°";
        }

        /** Rewrites valid input as "value°" when the field loses focus, like distance/altitude; invalid text is left for save-time validation. */
        private static void normalizeOnFocusLost(TextField field) {
            field.focusedProperty().addListener((o, was, focused) -> {
                if (focused) return;
                Integer value = parseTemp(field.getText());
                if (value == null) return;
                String formatted = formatTemp(value);
                if (!formatted.equals(field.getText())) field.setText(formatted);
            });
        }

        /** {@code null} if blank or complete; otherwise what's wrong, for the shared hint label. */
        String problem() {
            if (!anySet()) return null;
            if (min.getText().isBlank() || max.getText().isBlank() || condition.getValue() == null) {
                return "Weather needs a min, max and condition — or clear all three.";
            }
            if (minValue() == null || maxValue() == null) {
                return "Weather min/max must be a number.";
            }
            return null;
        }

        /**
         * The recorded weather, or {@code null} while blank/incomplete. A manually picked
         * condition loses the raw WMO code's finer granularity down to the bucket's
         * representative code — harmless, since every display/export path already only ever
         * shows the bucket, never the raw code, regardless of source.
         */
        Weather value() {
            if (!complete()) return null;
            String p = place.getText().isBlank() ? null : place.getText().trim();
            return new Weather(minValue(), maxValue(), condition.getValue().wmoCode(), elevation, p);
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

        // Weather row (todo 65): editable (not just a fetch result), so it still works offline
        // or when the API fails; see WeatherRow for the all-or-nothing validation.
        ExportTexts weatherTexts = ExportTexts.of(TripLanguage.EN);
        WeatherRow weatherRow = new WeatherRow(weather, weatherTexts);
        // 🔄's action needs to re-validate/re-enable everything (built further below, once the
        // Start/End rows exist too) — forward reference, like currentWeather used to be.
        Runnable[] refreshRef = new Runnable[1];
        weatherRow.update.setOnAction(e -> {
            Coordinates.LatLon point = start.value();
            if (point == null || date == null || weatherRow.busy) return;
            String place = start.placeValue();
            weatherRow.busy = true;
            Thread.ofVirtual().name("weather-fetch").start(() -> {
                Weather fetched = null;
                String error = null;
                try {
                    Weather result = weatherClient.fetch(point.lat(), point.lon(), date);
                    fetched = place == null ? result
                            : new Weather(result.tempMin(), result.tempMax(), result.code(), result.elevation(), place);
                } catch (RuntimeException ex) {
                    error = ex instanceof OpenMeteoClient.WeatherException ? ex.getMessage() : UiText.describe(ex);
                }
                Weather result = fetched;
                String message = error;
                Platform.runLater(() -> {
                    weatherRow.busy = false;
                    if (result != null) {
                        weatherRow.set(result);
                    } else {
                        hintLabel.setText(message);
                    }
                    refreshRef[0].run();
                });
            });
            refreshRef[0].run();
        });
        weatherRow.open.setOnAction(e -> {
            Coordinates.LatLon point = start.value();
            if (point == null || date == null) return;
            browser.open(OpenMeteoClient.archiveUrl(point.lat(), point.lon(), date));
        });
        weatherRow.clearButton.setOnAction(e -> {
            weatherRow.clear();
            refreshRef[0].run();
        });

        GridPane grid = Dialogs.formGrid();
        grid.add(new Label("Latitude"), 1, 0);
        grid.add(new Label("Longitude"), 2, 0);
        grid.add(new Label("DDM"), 3, 0);
        grid.add(new Label("Place"), 4, 0);
        // Same icons as the main window's coordinates button ("📍 ✓  🏁 –").
        addRow(grid, 1, "📍", "Start", start);
        addRow(grid, 2, "🏁", "End", stop);
        Label weatherRowLabel = new Label("🌦️ Weather:");
        grid.addRow(3, weatherRowLabel, weatherRow.minBox, weatherRow.maxBox, weatherRow.condition,
                weatherRow.place, weatherRow.update, weatherRow.open, weatherRow.clearButton);
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

            boolean canFetchWeather = online && date != null && start.value() != null && !weatherRow.busy;
            weatherRow.update.setDisable(!canFetchWeather);
            weatherRow.update.setText(weatherRow.busy ? WeatherRow.BUSY : WeatherRow.IDLE);
            weatherRow.update.setTooltip(new Tooltip(!online ? "Offline — re-check connectivity (top right) and reopen"
                    : date == null ? "No date selected"
                    : start.value() == null ? "Set a start point first"
                    : "Fetch weather for the start point (Open-Meteo); overwrites without asking, like 🔍"));
            boolean canOpenWeather = date != null && start.value() != null;
            weatherRow.open.setDisable(!canOpenWeather);
            weatherRow.open.setTooltip(new Tooltip(!canOpenWeather
                    ? "Set a start point first"
                    : "Check the source data for the start point and date (Open-Meteo historical archive)"));
            weatherRow.clearButton.setDisable(weatherRow.isBlank() || weatherRow.busy);
            for (Node n : new Node[] {weatherRow.min, weatherRow.max, weatherRow.condition, weatherRow.place}) {
                n.setDisable(weatherRow.busy);
            }
            weatherRowLabel.setTooltip(weatherRow.elevation == null ? null
                    : new Tooltip(weatherTexts.elevation(weatherRow.elevation)));
            if (problem == null) problem = weatherRow.problem();

            hintLabel.setText(problem == null ? "" : problem);
            saveButtonNode.setDisable(problem != null);
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
        weatherRow.min.textProperty().addListener((o, a, b) -> refresh.run());
        weatherRow.max.textProperty().addListener((o, a, b) -> refresh.run());
        weatherRow.place.textProperty().addListener((o, a, b) -> refresh.run());
        weatherRow.condition.valueProperty().addListener((o, a, b) -> refresh.run());
        refreshRef[0] = refresh;
        refresh.run();

        wireParse(parseStart, parseArea, hintLabel, refresh, start, Coordinates::tryParse);
        wireParse(parseStop, parseArea, hintLabel, refresh, stop, Coordinates::tryParseLast);

        Optional<ButtonType> result = dlg.showAndWait();
        if (result.isEmpty() || result.get() != saveType) return Optional.empty();
        return Optional.of(new Result(start.value(), stop.value(), start.placeValue(), stop.placeValue(),
                weatherRow.value()));
    }

    /** Whole km with unit, e.g. {@code "12 km"} — pastes straight into the Distance field; no decimals, since a straight-line distance is only ever approximate. */
    private static String formatKm(double km) {
        return BigDecimal.valueOf(km).setScale(0, RoundingMode.HALF_UP).toPlainString() + " km";
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
