package net.timafe.triptale.ui.dialog;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.util.StringConverter;
import net.timafe.triptale.attachments.AttachmentsDir;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.export.ExportTempFiles;
import net.timafe.triptale.map.MapFormat;
import net.timafe.triptale.map.MapPoints;
import net.timafe.triptale.map.OsmMapRenderer;
import net.timafe.triptale.map.OsmTileClient;
import net.timafe.triptale.map.PhotoLocations;
import net.timafe.triptale.map.PngText;
import net.timafe.triptale.map.TileSource;
import net.timafe.triptale.map.TripMapSettings;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.storage.SettingsStore;
import net.timafe.triptale.ui.ConnectivityService;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.ui.StatusSink;
import net.timafe.triptale.ui.UiText;
import net.timafe.triptale.util.Coordinates.LatLon;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Sgt. Mapper (todo 54, todo 82): plots a range of a trip's entries on a self-rendered OSM tile
 * map (todo 61) — a start pin per day, the last day's stop pin and, optionally, the locations of
 * the backpack images. Nothing is fetched until "On Screen" is clicked; changing the range
 * afterwards marks the shown map as stale, but changing the tile style, zoom level or format
 * re-engages automatically once a map is on screen (tiles and EXIF reads are disk-cached, so this
 * is cheap — see {@link #styleOrZoomChanged()}).
 *
 * <p>Renders are temporary ({@link ExportTempFiles}) until "💾 Save" copies the one on screen to
 * an entry of its range as {@link AttachmentsDir#tripMapFile tripmap.png}; "Save as…" writes it
 * anywhere. Every render carries its settings as PNG text ({@link TripMapSettings}), so opening
 * Sgt. Mapper on a day with a saved map shows it straight from disk — offline too — with the
 * controls restored to match. See {@code docs/82_sgt_mapper_save_and_format.md}.
 */
public final class MapAssistantDialog {

    /** Space reserved outside the map area for the dialog chrome, header, range, options and button rows. */
    private static final int RESERVED_WIDTH = 80;
    private static final int RESERVED_HEIGHT = 270;
    /** Resting opacity of the teal viewscreen frame; it pulses up to 1.0 while a map renders. */
    private static final double FRAME_OPACITY = 0.3;
    private static final String AWAITING_ORDERS = "Awaiting your orders, Captain.";
    private static final String CHOOSE_RANGE = "Choose a range, then put it on screen.";
    private static final String ON_SCREEN = "🖥 On Screen";
    private static final double STALE_OPACITY = 0.35;
    /** Caps the save-target combo, like the range combos; longer labels are ellipsized. */
    private static final double TARGET_COMBO_WIDTH = 240;

    private final MarkdownStore store;
    private final String appVersion;
    private final AttachmentsDir attachmentsDir;
    private final PhotoLocations photoLocations;
    private final ConnectivityService connectivityService;
    private final StatusSink statusSink;
    private final SettingsStore settingsStore;
    private final ExportTempFiles tempFiles;

    public MapAssistantDialog(MarkdownStore store, String appVersion, AttachmentsDir attachmentsDir,
                              PhotoLocations photoLocations, ConnectivityService connectivityService, StatusSink statusSink,
                              SettingsStore settingsStore, ExportTempFiles tempFiles) {
        this.store = store;
        this.appVersion = appVersion;
        this.attachmentsDir = attachmentsDir;
        this.photoLocations = photoLocations;
        this.connectivityService = connectivityService;
        this.statusSink = statusSink;
        this.settingsStore = settingsStore;
        this.tempFiles = tempFiles;
    }

    /**
     * The whole trip; {@code activeDate} (may be null) is only the default save target. Never
     * loads a saved map (D2).
     *
     * @return the days whose {@code tripmap.png} was saved
     */
    public Set<LocalDate> showTrip(Trip trip, LocalDate activeDate) {
        return open(trip, null, null, activeDate, false);
    }

    /**
     * Just one day: {@code date}'s entry, or the nearest one if that day has none. Shows that
     * day's saved {@code tripmap.png}, if any.
     *
     * @return the days whose {@code tripmap.png} was saved
     */
    public Set<LocalDate> showDay(Trip trip, LocalDate date) {
        LocalDate day = date == null ? null : nearest(store.listEntryDates(trip.ref()), date);
        return open(trip, day, day, day, true);
    }

    private Set<LocalDate> open(Trip trip, LocalDate from, LocalDate to, LocalDate activeDate, boolean loadSaved) {
        List<DiaryEntry> entries;
        try {
            entries = store.listEntryDates(trip.ref()).stream()
                    .map(d -> store.loadEntry(trip.ref(), d))
                    .toList();
        } catch (RuntimeException e) {
            statusSink.error("Sgt. Mapper failed: " + e.getMessage());
            return Set.of();
        }
        // Built here, not at app start (MainController): store.dataDir() throws until the data
        // dir is configured, and by this point loading the entries above already proved it is.
        String thunderforestApiKey = settingsStore.load().getThunderforestApiKey();
        OsmMapRenderer mapRenderer = new OsmMapRenderer(
                new OsmTileClient(store.dataDir().resolve(".tile-cache"), appVersion, thunderforestApiKey));
        Path savedMap = null;
        if (loadSaved && activeDate != null) {
            Path file = attachmentsDir.tripMapFile(trip.ref(), activeDate);
            if (Files.isRegularFile(file)) savedMap = file;
        }
        return new Session(trip, entries, from, to, activeDate, savedMap, mapRenderer, thunderforestApiKey).show();
    }

    /** The settings stored in a saved map, if it has readable ones. */
    private static Optional<TripMapSettings> readSettings(Path file) {
        try {
            return TripMapSettings.fromText(PngText.read(file));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** One opened dialog and its state. */
    private final class Session {

        private final Trip trip;
        private final List<DiaryEntry> entries;
        private final Map<LocalDate, String> entryLabels = new HashMap<>();
        private final LocalDate activeDate;
        private final OsmMapRenderer mapRenderer;
        private final boolean hasThunderforestKey;
        private final EntryRangePicker range;
        private final CheckBox photosCheck = new CheckBox("📷 Add Photo POIs");
        private final ComboBox<TileSource> sourceCombo = new ComboBox<>();
        private final ComboBox<OsmMapRenderer.ZoomLevel> zoomCombo = new ComboBox<>();
        private final ComboBox<MapFormat> formatCombo = new ComboBox<>();
        private final ComboBox<LocalDate> targetCombo = new ComboBox<>();
        private final Button saveButton = new Button("💾 Save");
        private final Button saveAsButton = new Button("Save as…");
        private final ImageView imageView = new ImageView();
        private final Label message = new Label();
        private final Label icon = new Label("🗺");
        private final Label heading = new Label();
        private final VBox placeholder = new VBox(14, icon, heading, message);
        /** Teal frame on top of the map; pulses while a map renders. Only its opacity animates. */
        private final Region frame = new Region();
        private final StackPane mapArea = new StackPane(imageView, placeholder, frame);
        private final FadeTransition pulse = new FadeTransition(Duration.millis(700), frame);
        private final Dialog<Void> dlg = new Dialog<>();
        private Button onScreenButton;
        /** Bumped on every range/checkbox change, so a slow render can tell it's outdated. */
        private int generation;
        /** Set when style/zoom/format changes while a render is already in flight; picked up by
         *  {@link #renderAndDisplay()}'s completion handler to re-engage once, instead of stacking tasks. */
        private boolean autoReloadPending;
        /** A connectivity check or render is running. */
        private boolean busy;
        /** The map size actually used, fitted to the screen for the current format (D4). */
        private int width;
        private int height;
        private final double availableWidth;
        private final double availableHeight;

        /** The file behind the map on screen (a temp render or a saved tripmap.png), null if none. */
        private Path shownFile;
        /** What the map on screen was made with; null if none, or a saved map without readable settings. */
        private TripMapSettings shownSettings;
        /** The day whose tripmap.png is on screen, unchanged; null while a temp render is shown. */
        private LocalDate shownSavedDay;
        /** The range was changed since the map on screen was rendered. */
        private boolean stale;
        /** Days whose tripmap.png this session loaded or wrote: overwritten without asking (D5). */
        private final Set<LocalDate> ownMaps = new HashSet<>();
        /** Days whose tripmap.png was saved, the dialog's result. */
        private final Set<LocalDate> saved = new LinkedHashSet<>();
        private final List<Path> tempRenders = new ArrayList<>();

        Session(Trip trip, List<DiaryEntry> entries, LocalDate from, LocalDate to, LocalDate activeDate,
                Path savedMap, OsmMapRenderer mapRenderer, String thunderforestApiKey) {
            this.trip = trip;
            this.entries = entries;
            for (DiaryEntry e : entries) entryLabels.put(e.date(), EntryRangePicker.entryLabel(trip, e));
            this.activeDate = activeDate;
            this.mapRenderer = mapRenderer;
            this.hasThunderforestKey = thunderforestApiKey != null && !thunderforestApiKey.isBlank();
            if (savedMap != null) {
                shownFile = savedMap;
                shownSavedDay = activeDate;
                ownMaps.add(activeDate);
                shownSettings = readSettings(savedMap).orElse(null);
                if (shownSettings != null) {
                    from = shownSettings.from();
                    to = shownSettings.to();
                }
            }
            this.range = new EntryRangePicker(trip, entries, from, to);
            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
            this.availableWidth = bounds.getWidth() - RESERVED_WIDTH;
            this.availableHeight = bounds.getHeight() - RESERVED_HEIGHT;
        }

        Set<LocalDate> show() {
            dlg.setTitle("🗺 Sgt. Mapper · " + trip.name());
            photosCheck.setSelected(shownSettings == null || shownSettings.photos());
            photosCheck.setTooltip(new Tooltip("Locations of the backpack images of these days, from their EXIF GPS"));
            sourceCombo.getItems().setAll(availableTileSources());
            sourceCombo.setValue(shownSettings != null && sourceCombo.getItems().contains(shownSettings.source())
                    ? shownSettings.source() : lastOrDefaultTileSource());
            sourceCombo.setTooltip(new Tooltip("Tile style the map is rendered from"));
            zoomCombo.getItems().setAll(OsmMapRenderer.ZoomLevel.values());
            zoomCombo.setValue(shownSettings != null ? shownSettings.zoom() : OsmMapRenderer.DEFAULT_ZOOM);
            zoomCombo.setTooltip(new Tooltip("How far to zoom out around the plotted pins"));
            formatCombo.getItems().setAll(MapFormat.values());
            formatCombo.setValue(shownSettings != null ? shownSettings.format() : MapFormat.DEFAULT);
            formatCombo.setTooltip(new Tooltip("Aspect ratio of the map — portrait suits a trip that runs north–south"));
            targetCombo.setConverter(new StringConverter<>() {
                @Override public String toString(LocalDate d) { return d == null ? "" : entryLabels.getOrDefault(d, d.toString()); }
                @Override public LocalDate fromString(String s) { return null; }
            });
            targetCombo.setPrefWidth(TARGET_COMBO_WIDTH);
            targetCombo.setMaxWidth(TARGET_COMBO_WIDTH);
            targetCombo.setTooltip(new Tooltip("The day whose backpack the map is saved to, as " + AttachmentsDir.TRIP_MAP_FILE));
            saveButton.setTooltip(new Tooltip("Save the map on screen to the chosen day as " + AttachmentsDir.TRIP_MAP_FILE
                    + ", so it becomes part of its gallery"));
            saveAsButton.setTooltip(new Tooltip("Save the map on screen anywhere"));

            imageView.setPreserveRatio(true);
            message.setWrapText(true);
            message.setTextAlignment(TextAlignment.CENTER);
            message.setAlignment(Pos.CENTER);
            message.getStyleClass().add("map-screen-message");
            icon.getStyleClass().add("map-screen-icon");
            heading.getStyleClass().add("map-screen-heading");
            placeholder.setAlignment(Pos.CENTER);
            placeholder.setMouseTransparent(true);
            frame.getStyleClass().add("map-screen-frame");
            frame.setMouseTransparent(true);
            frame.setOpacity(FRAME_OPACITY);
            pulse.setFromValue(FRAME_OPACITY);
            pulse.setToValue(1.0);
            pulse.setAutoReverse(true);
            pulse.setCycleCount(Animation.INDEFINITE);
            mapArea.getStyleClass().add("map-screen");
            applyFormatSize();

            HBox options = new HBox(14, labeled("Style:", sourceCombo), labeled("Zoom:", zoomCombo),
                    labeled("Format:", formatCombo), photosCheck);
            options.setAlignment(Pos.CENTER_LEFT);
            VBox content = new VBox(8, range.node(), mapArea, options);
            // Keeps a narrower (portrait) map centered under the range row
            content.setAlignment(Pos.TOP_CENTER);
            dlg.getDialogPane().setContent(content);
            dlg.setResizable(true);
            dlg.setOnShown(e -> fitToScreen(dlg.getDialogPane().getScene().getWindow()));

            // APPLY sits next to Close; OTHER would float it to the middle of the button bar.
            ButtonType onScreenType = new ButtonType(ON_SCREEN, ButtonBar.ButtonData.APPLY);
            dlg.getDialogPane().getButtonTypes().setAll(onScreenType, ButtonType.CLOSE);
            onScreenButton = (Button) dlg.getDialogPane().lookupButton(onScreenType);
            onScreenButton.setDefaultButton(true);
            onScreenButton.setTooltip(new Tooltip("Engage! Put the chosen range on screen (Enter)"));
            // Save target and buttons share the button bar, on the left. Added after setAll(): the
            // pane rebuilds the bar's buttons whenever the button types change.
            ButtonBar buttonBar = (ButtonBar) dlg.getDialogPane().lookup(".button-bar");
            for (Node n : List.of(labeled("Save to:", targetCombo), saveButton, saveAsButton)) {
                ButtonBar.setButtonData(n, ButtonBar.ButtonData.LEFT);
                ButtonBar.setButtonUniformSize(n, false);
                buttonBar.getButtons().add(n);
            }
            // consume() keeps the dialog open: putting a map on screen is repeatable, not a result.
            onScreenButton.addEventFilter(ActionEvent.ACTION, ev -> {
                onScreen();
                ev.consume();
            });
            onScreenButton.setDisable(entries.isEmpty());
            saveButton.setOnAction(e -> save());
            saveAsButton.setOnAction(e -> saveAs());

            range.onChange(this::changed);
            photosCheck.selectedProperty().addListener((obs, old, sel) -> changed());
            sourceCombo.valueProperty().addListener((obs, old, sel) -> styleOrZoomChanged());
            zoomCombo.valueProperty().addListener((obs, old, sel) -> styleOrZoomChanged());
            formatCombo.valueProperty().addListener((obs, old, sel) -> formatChanged());
            targetCombo.valueProperty().addListener((obs, old, sel) -> updateSaveButtons());

            if (shownFile != null) {
                imageView.setImage(new Image(shownFile.toUri().toString()));
                setMessage(null);
            } else if (entries.isEmpty()) {
                setMessage("Nothing on sensors", "No entries in this trip yet.");
            } else {
                setMessage(AWAITING_ORDERS, CHOOSE_RANGE);
            }
            updateTargets();
            updateHeader();
            updateSaveButtons();

            Dialogs.applyStylesheet(dlg.getDialogPane());
            dlg.showAndWait();
            pulse.stop();
            store.saveLastMapStyle(sourceCombo.getValue().id());
            tempRenders.forEach(f -> {
                try {
                    Files.deleteIfExists(f);
                } catch (IOException e) {
                    // swept at the next startup anyway
                }
            });
            return saved;
        }

        private static HBox labeled(String text, Node control) {
            HBox box = new HBox(6, new Label(text), control);
            box.setAlignment(Pos.CENTER_LEFT);
            return box;
        }

        /** The sources shown in the Style dropdown — Thunderforest's key-gated styles are hidden
         *  entirely (rather than shown disabled) when no {@code thunderforestApiKey} is configured. */
        private List<TileSource> availableTileSources() {
            return Arrays.stream(TileSource.values())
                    .filter(s -> !s.requiresApiKey() || hasThunderforestKey)
                    .toList();
        }

        /** The style picked last time Sgt. Mapper was closed (any trip), or {@link TileSource#OPEN_TOPO_MAP}
         *  if none was saved yet or the saved id no longer matches a known, available source (e.g. after one
         *  was removed, or a Thunderforest style was saved while a key was configured and it no longer is). */
        private TileSource lastOrDefaultTileSource() {
            return store.loadLastMapStyle()
                    .flatMap(id -> availableTileSources().stream().filter(s -> s.id().equals(id)).findFirst())
                    .orElse(TileSource.OPEN_TOPO_MAP);
        }

        /** Sizes the map area for the current format (D4) and, once shown, the window around it. */
        private void applyFormatSize() {
            int[] size = formatCombo.getValue().fit(availableWidth, availableHeight);
            width = size[0];
            height = size[1];
            imageView.setFitWidth(width);
            imageView.setFitHeight(height);
            message.setMaxWidth(width - 80);
            mapArea.setPrefSize(width, height);
            mapArea.setMinSize(width, height);
            mapArea.setMaxSize(width, height);
            Window window = dlg.getDialogPane().getScene() == null ? null : dlg.getDialogPane().getScene().getWindow();
            if (window != null && window.isShowing()) {
                window.sizeToScene();
                fitToScreen(window);
            }
        }

        /** Range or option changed: the shown map, if any, no longer matches. */
        private void changed() {
            generation++;
            if (imageView.getImage() != null) {
                stale = true;
                imageView.setOpacity(STALE_OPACITY);
                setMessage("Range changed — " + ON_SCREEN + " to update.");
            } else if (!entries.isEmpty()) {
                setMessage(AWAITING_ORDERS, CHOOSE_RANGE);
            }
            updateHeader();
            updateSaveButtons();
        }

        private void formatChanged() {
            applyFormatSize();
            styleOrZoomChanged();
        }

        /**
         * Style, zoom or format changed: unlike a range/photos change, this re-engages
         * automatically if a map is already on screen — tiles and EXIF reads are cached, so
         * re-rendering is cheap. If a render is already in flight, the reload is deferred until
         * it completes (see {@link #renderAndDisplay()}) rather than stacking a second background task.
         */
        private void styleOrZoomChanged() {
            generation++;
            if (imageView.getImage() == null) {
                if (!entries.isEmpty()) setMessage(AWAITING_ORDERS, CHOOSE_RANGE);
                return;
            }
            if (busy) {
                autoReloadPending = true;
            } else {
                onScreen();
            }
        }

        private void setBusy(boolean busy) {
            this.busy = busy;
            onScreenButton.setDisable(busy || entries.isEmpty());
            updateSaveButtons();
        }

        private void onScreen() {
            setBusy(true);
            setMessage("Checking connectivity…");
            TileSource source = sourceCombo.getValue();
            Task<Boolean> connCheck = connectivityService.checkTask(source.connectivityUrl());
            connCheck.setOnSucceeded(e -> {
                if (Boolean.TRUE.equals(connCheck.getValue())) {
                    renderAndDisplay();
                } else {
                    offline(source);
                }
            });
            connCheck.setOnFailed(e -> offline(source));
            Thread checker = new Thread(connCheck, "map-assistant-connectivity");
            checker.setDaemon(true);
            checker.start();
        }

        private void offline(TileSource source) {
            setBusy(false);
            setMessage("Viewscreen offline", "No network connection — Sgt. Mapper needs internet access to reach " + source + ".");
        }

        /** The outcome of one background render: the points actually plotted and, unless empty, the temp file. */
        private record RenderResult(MapPoints points, boolean withPhotos, Path file, TripMapSettings settings) {}

        private void renderAndDisplay() {
            MapPoints days = dayPoints();
            int requested = generation;
            LocalDate from = range.from();
            LocalDate to = range.to();
            TileSource source = sourceCombo.getValue();
            OsmMapRenderer.ZoomLevel zoom = zoomCombo.getValue();
            MapFormat format = formatCombo.getValue();
            int w = width;
            int h = height;
            boolean wantPhotos = photosCheck.isSelected();
            Task<RenderResult> task = new Task<>() {
                @Override protected RenderResult call() throws IOException {
                    MapPoints points = days;
                    if (wantPhotos) {
                        List<LatLon> locations = photoLocations.locations(photoLocations.images(trip, from, to));
                        points = days.withPhotos(locations, OsmMapRenderer.MAX_PINS - days.dayPinCount());
                    }
                    if (points.isEmpty()) return new RenderResult(points, wantPhotos, null, null);
                    TripMapSettings settings = TripMapSettings.of(from, to, source, zoom, format, wantPhotos, points);
                    Path target = tempFiles.newFile("tripmap-", ".png");
                    mapRenderer.render(points, source, zoom, w, h, settings.toText(), target);
                    return new RenderResult(points, wantPhotos, target, settings);
                }
            };
            task.setOnSucceeded(e -> {
                stopPulse();
                display(task.getValue());
                setBusy(false);
                if (autoReloadPending) {
                    autoReloadPending = false;
                    onScreen();
                } else if (requested != generation) {
                    changed();
                }
            });
            task.setOnFailed(e -> {
                stopPulse();
                setBusy(false);
                Throwable ex = task.getException();
                setMessage("Failed to render map" + (ex != null && ex.getMessage() != null ? ": " + ex.getMessage() : "."));
            });
            setMessage(wantPhotos ? "Reading photo locations…" : "Rendering map…");
            pulse.playFromStart();
            Thread worker = new Thread(task, "map-assistant-render");
            worker.setDaemon(true);
            worker.start();
        }

        private void display(RenderResult result) {
            MapPoints points = result.points();
            stale = false;
            if (points.isEmpty()) {
                imageView.setImage(null);
                shownFile = null;
                shownSettings = null;
                shownSavedDay = null;
                updateTargets();
                updateHeader();
                String hint = "";
                if (!result.withPhotos()) {
                    int images = photoLocations.images(trip, range.from(), range.to()).size();
                    if (images > 0) {
                        hint = "\nTick " + photosCheck.getText() + " to look for the locations of its "
                                + images + (images == 1 ? " backpack image." : " backpack images.");
                    }
                }
                setMessage("Nothing on sensors", "Nothing to put on screen for this range." + hint);
                return;
            }
            tempRenders.add(result.file());
            shownFile = result.file();
            shownSettings = result.settings();
            shownSavedDay = null;
            imageView.setImage(new Image(result.file().toUri().toString()));
            imageView.setOpacity(1.0);
            setMessage(null);
            updateTargets();
            updateHeader();
            statusSink.status("🖖 Engage! On screen: " + describe(points, result.withPhotos())
                    + " — temporary, 💾 Save to keep it");
        }

        /** The save targets: the entries of the shown map's range; the active day is the default. */
        private void updateTargets() {
            LocalDate from = shownSettings != null ? shownSettings.from() : range.from();
            LocalDate to = shownSettings != null ? shownSettings.to() : range.to();
            List<LocalDate> dates = from == null || to == null ? List.of() : entries.stream()
                    .map(DiaryEntry::date)
                    .filter(d -> !d.isBefore(from) && !d.isAfter(to))
                    .toList();
            LocalDate previous = targetCombo.getValue();
            targetCombo.getItems().setAll(dates);
            if (shownSavedDay != null && dates.contains(shownSavedDay)) {
                targetCombo.setValue(shownSavedDay);
            } else if (previous != null && dates.contains(previous)) {
                targetCombo.setValue(previous);
            } else if (activeDate != null && dates.contains(activeDate)) {
                targetCombo.setValue(activeDate);
            } else {
                targetCombo.setValue(dates.isEmpty() ? null : dates.getFirst());
            }
        }

        /**
         * Save needs an up-to-date map and a target other than the day whose unchanged
         * tripmap.png is already on screen (D6); Save as only an up-to-date map.
         */
        private void updateSaveButtons() {
            boolean upToDate = shownFile != null && imageView.getImage() != null && !stale && !busy;
            LocalDate target = targetCombo.getValue();
            targetCombo.setDisable(!upToDate || targetCombo.getItems().isEmpty());
            saveButton.setDisable(!upToDate || target == null || target.equals(shownSavedDay));
            saveAsButton.setDisable(!upToDate);
        }

        private void save() {
            LocalDate target = targetCombo.getValue();
            if (target == null || shownFile == null) return;
            Path dest = attachmentsDir.tripMapFile(trip.ref(), target);
            if (Files.exists(dest) && !ownMaps.contains(target) && !confirmReplace(target)) return;
            try {
                attachmentsDir.saveTripMap(trip.ref(), target, shownFile);
            } catch (RuntimeException e) {
                statusSink.error("Could not save the map: " + UiText.describe(e));
                return;
            }
            ownMaps.add(target);
            saved.add(target);
            shownFile = dest;
            shownSavedDay = target;
            updateHeader();
            updateSaveButtons();
            statusSink.status("💾 Map saved to " + attachmentsDir.root().relativize(dest));
        }

        private boolean confirmReplace(LocalDate target) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "It already has a map. Replace it with the one on screen?", ButtonType.YES, ButtonType.CANCEL);
            confirm.initOwner(dlg.getDialogPane().getScene().getWindow());
            confirm.setTitle("Replace Map");
            confirm.setHeaderText("Replace the " + AttachmentsDir.TRIP_MAP_FILE + " of "
                    + entryLabels.getOrDefault(target, target.toString()) + "?");
            Dialogs.applyStylesheet(confirm.getDialogPane());
            return confirm.showAndWait().filter(ButtonType.YES::equals).isPresent();
        }

        private void saveAs() {
            if (shownFile == null) return;
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Save Map As");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG image", "*.png"));
            chooser.setInitialFileName(saveAsName());
            File file = chooser.showSaveDialog(dlg.getDialogPane().getScene().getWindow());
            if (file == null) return;
            try {
                Files.copy(shownFile, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                statusSink.status("💾 Map saved to " + UiText.homeRelative(file.toPath()));
            } catch (IOException e) {
                statusSink.error("Could not save the map: " + UiText.describe(e));
            }
        }

        /** "my-trip-2026-09-20-2026-09-23.png", or "my-trip-2026-09-20.png" for one day. */
        private String saveAsName() {
            if (shownSettings == null) return trip.ref().slug() + "-map.png";
            LocalDate from = shownSettings.from();
            LocalDate to = shownSettings.to();
            return trip.ref().slug() + "-" + from + (to.equals(from) ? "" : "-" + to) + ".png";
        }

        private void stopPulse() {
            pulse.stop();
            frame.setOpacity(FRAME_OPACITY);
        }

        private MapPoints dayPoints() {
            LocalDate from = range.from();
            LocalDate to = range.to();
            if (from == null || to == null) return MapPoints.of(List.of());
            return MapPoints.of(entries.stream()
                    .filter(e -> !e.date().isBefore(from) && !e.date().isAfter(to))
                    .toList());
        }

        /**
         * The points of the map on screen (or of the chosen range, if there's none or it's stale),
         * plus whether that map is a saved tripmap.png or a temporary render.
         */
        private void updateHeader() {
            boolean showing = imageView.getImage() != null || shownFile != null;
            String points = showing && !stale && shownSettings != null
                    ? describe(shownSettings)
                    : describe(dayPoints(), false);
            String state = !showing ? ""
                    : shownSavedDay != null ? " · 💾 cached " + AttachmentsDir.TRIP_MAP_FILE : " · ⏳ temporary";
            dlg.setHeaderText(points + state);
        }

        /** A null or blank text hides the message. */
        private void setMessage(String text) {
            setMessage(null, text);
        }

        /**
         * The big 🗺 shows only while there's no map on screen, so a stale map isn't covered by it;
         * a null or blank heading or text hides that line.
         */
        private void setMessage(String headingText, String text) {
            Image image = imageView.getImage();
            show(icon, image == null);
            heading.setText(headingText != null ? headingText : "");
            show(heading, headingText != null && !headingText.isBlank());
            message.setText(text != null ? text : "");
            show(message, text != null && !text.isBlank());
            show(placeholder, icon.isVisible() || heading.isVisible() || message.isVisible());
        }

        private static void show(Node node, boolean visible) {
            node.setVisible(visible);
            node.setManaged(visible);
        }
    }

    /** E.g. "📍 3 start points · 🏁 stop point · 📷 60 of 212 photo locations". */
    static String describe(MapPoints points, boolean withPhotos) {
        return describe(points.starts().size(), points.stop() != null, withPhotos, points.photos().size(), points.photosFound());
    }

    /** Same as {@link #describe(MapPoints, boolean)}, from the counts a saved map carries. */
    static String describe(TripMapSettings s) {
        return describe(s.starts(), s.stop(), s.photos(), s.photosShown(), s.photosFound());
    }

    static String describe(int starts, boolean stop, boolean withPhotos, int photosShown, int photosFound) {
        String s = "📍 " + (starts == 0 ? "No" : starts) + " start point" + (starts == 1 ? "" : "s")
                + " · " + (stop ? "🏁 stop point" : "🏁 no stop point");
        if (withPhotos) {
            s += " · 📷 " + (photosFound == 0 ? "No" : photosShown < photosFound ? photosShown + " of " + photosFound : photosFound)
                    + " photo location" + (photosFound == 1 ? "" : "s");
        }
        return s;
    }

    /** The date closest to {@code date} (the earlier one on a tie), or null if there are none. */
    static LocalDate nearest(List<LocalDate> dates, LocalDate date) {
        return dates.stream()
                .min(Comparator.comparingLong((LocalDate d) -> Math.abs(ChronoUnit.DAYS.between(d, date)))
                        .thenComparing(Comparator.naturalOrder()))
                .orElse(null);
    }

    /** Shrinks the window to the screen's usable area and keeps it on screen (as in EditSettingsDialog). */
    private static void fitToScreen(Window window) {
        Rectangle2D bounds = Screen.getScreensForRectangle(window.getX(), window.getY(), window.getWidth(), window.getHeight())
                .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
        if (window.getHeight() > bounds.getHeight()) window.setHeight(bounds.getHeight());
        if (window.getWidth() > bounds.getWidth()) window.setWidth(bounds.getWidth());
        window.setY(Math.max(bounds.getMinY(), Math.min(window.getY(), bounds.getMaxY() - window.getHeight())));
        window.setX(Math.max(bounds.getMinX(), Math.min(window.getX(), bounds.getMaxX() - window.getWidth())));
    }
}
