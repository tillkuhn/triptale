package net.timafe.triptale.ui.dialog;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
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
import javafx.stage.Screen;
import javafx.stage.Window;
import net.timafe.triptale.attachments.AttachmentsDir;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.map.MapPoints;
import net.timafe.triptale.map.OsmMapRenderer;
import net.timafe.triptale.map.OsmTileClient;
import net.timafe.triptale.map.PhotoLocations;
import net.timafe.triptale.map.TileSource;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.ui.ConnectivityService;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.ui.StatusSink;
import net.timafe.triptale.util.Coordinates.LatLon;

import javafx.util.Duration;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Map Assistant (todo 54): plots a range of a trip's entries on a self-rendered OSM tile map
 * (todo 61) — a start pin per day, the last day's stop pin and, optionally, the locations of the
 * backpack images. Nothing is fetched until "On Screen" is clicked; changing the range afterwards
 * marks the shown map as stale. Every render is saved to the trip's attachments folder
 * ({@link AttachmentsDir#mapFile}), overwriting any earlier render of the same range, so exports
 * can reuse it. See {@link OsmMapRenderer} for the tile stitching/drawing.
 */
public final class MapAssistantDialog {

    /** Default/maximum map size (also its 3:2 aspect ratio). */
    private static final int DEFAULT_WIDTH = 960;
    private static final int DEFAULT_HEIGHT = 640;
    /** Never shrink the map below this, even on a tiny screen — it'd stop being useful. */
    private static final int MIN_WIDTH = 480;
    private static final int MIN_HEIGHT = 320;
    /** Space reserved outside the map area for the dialog chrome, header, range row and button bar. */
    private static final int RESERVED_WIDTH = 80;
    private static final int RESERVED_HEIGHT = 240;
    /** Resting opacity of the teal viewscreen frame; it pulses up to 1.0 while a map renders. */
    private static final double FRAME_OPACITY = 0.3;
    private static final String AWAITING_ORDERS = "Awaiting your orders, Captain.";
    private static final String CHOOSE_RANGE = "Choose a range, then put it on screen.";
    private static final String ON_SCREEN = "🖥 On Screen";
    private static final double STALE_OPACITY = 0.35;

    private final MarkdownStore store;
    private final String appVersion;
    private final AttachmentsDir attachmentsDir;
    private final PhotoLocations photoLocations;
    private final ConnectivityService connectivityService;
    private final StatusSink statusSink;

    public MapAssistantDialog(MarkdownStore store, String appVersion, AttachmentsDir attachmentsDir,
                              PhotoLocations photoLocations, ConnectivityService connectivityService, StatusSink statusSink) {
        this.store = store;
        this.appVersion = appVersion;
        this.attachmentsDir = attachmentsDir;
        this.photoLocations = photoLocations;
        this.connectivityService = connectivityService;
        this.statusSink = statusSink;
    }

    /** The whole trip. */
    public void show(Trip trip) {
        show(trip, null, null);
    }

    /** Just one day: {@code date}'s entry, or the nearest one if that day has none. */
    public void showDay(Trip trip, LocalDate date) {
        LocalDate day = date == null ? null : nearest(store.listEntryDates(trip.ref()), date);
        show(trip, day, day);
    }

    /** The entries {@code from}..{@code to}; null means the first/last entry. */
    public void show(Trip trip, LocalDate from, LocalDate to) {
        List<DiaryEntry> entries;
        try {
            entries = store.listEntryDates(trip.ref()).stream()
                    .map(d -> store.loadEntry(trip.ref(), d))
                    .toList();
        } catch (RuntimeException e) {
            statusSink.error("Sgt. Mapper failed: " + e.getMessage());
            return;
        }
        // Built here, not at app start (MainController): store.dataDir() throws until the data
        // dir is configured, and by this point loading the entries above already proved it is.
        OsmMapRenderer mapRenderer = new OsmMapRenderer(new OsmTileClient(store.dataDir().resolve(".tile-cache"), appVersion));
        new Session(trip, entries, from, to, mapRenderer).show();
    }

    /** One opened dialog and its state. */
    private final class Session {

        private final Trip trip;
        private final List<DiaryEntry> entries;
        private final OsmMapRenderer mapRenderer;
        private final EntryRangePicker range;
        private final CheckBox photosCheck = new CheckBox("📷 Photo Locations from Backpack");
        private final ComboBox<TileSource> sourceCombo = new ComboBox<>();
        private final ComboBox<OsmMapRenderer.ZoomLevel> zoomCombo = new ComboBox<>();
        private final ImageView imageView = new ImageView();
        private final Label message = new Label();
        private final Label icon = new Label("🗺");
        private final Label heading = new Label();
        private final VBox placeholder = new VBox(14, icon, heading, message);
        /** Teal frame on top of the map; pulses while a map renders. Only its opacity animates. */
        private final Region frame = new Region();
        private final FadeTransition pulse = new FadeTransition(Duration.millis(700), frame);
        private final Dialog<Void> dlg = new Dialog<>();
        private Button onScreenButton;
        /** Bumped on every range/checkbox change, so a slow render can tell it's outdated. */
        private int generation;
        /** The map size actually used, shrunk from the default to fit the current screen. */
        private final int width;
        private final int height;

        Session(Trip trip, List<DiaryEntry> entries, LocalDate from, LocalDate to, OsmMapRenderer mapRenderer) {
            this.trip = trip;
            this.entries = entries;
            this.mapRenderer = mapRenderer;
            this.range = new EntryRangePicker(trip, entries, from, to);
            int[] size = mapSize();
            this.width = size[0];
            this.height = size[1];
        }

        void show() {
            dlg.setTitle("🗺 Sgt. Mapper");
            photosCheck.setSelected(true);
            photosCheck.setTooltip(new Tooltip("Locations of the backpack images of these days, from their EXIF GPS"));
            sourceCombo.getItems().setAll(TileSource.values());
            sourceCombo.setValue(lastOrDefaultTileSource());
            sourceCombo.setTooltip(new Tooltip("Tile style the map is rendered from"));
            zoomCombo.getItems().setAll(OsmMapRenderer.ZoomLevel.values());
            zoomCombo.setValue(OsmMapRenderer.DEFAULT_ZOOM);
            zoomCombo.setTooltip(new Tooltip("How far to zoom out around the plotted pins"));

            imageView.setFitWidth(width);
            imageView.setFitHeight(height);
            imageView.setPreserveRatio(true);
            message.setWrapText(true);
            message.setMaxWidth(width - 80);
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
            StackPane mapArea = new StackPane(imageView, placeholder, frame);
            mapArea.getStyleClass().add("map-screen");
            mapArea.setPrefSize(width, height);
            mapArea.setMinSize(width, height);

            dlg.getDialogPane().setContent(new VBox(8, range.node(), mapArea));
            dlg.setResizable(true);
            dlg.setOnShown(e -> fitToScreen(dlg.getDialogPane().getScene().getWindow()));

            // APPLY sits next to Close; OTHER would float it to the middle of the button bar.
            ButtonType onScreenType = new ButtonType(ON_SCREEN, ButtonBar.ButtonData.APPLY);
            dlg.getDialogPane().getButtonTypes().setAll(onScreenType, ButtonType.CLOSE);
            onScreenButton = (Button) dlg.getDialogPane().lookupButton(onScreenType);
            onScreenButton.setDefaultButton(true);
            onScreenButton.setTooltip(new Tooltip("Engage! Put the chosen range on screen (Enter)"));
            // The only option shares the button bar, on the left. Added after setAll(): the pane
            // rebuilds the bar's buttons whenever the button types change.
            ButtonBar buttonBar = (ButtonBar) dlg.getDialogPane().lookup(".button-bar");
            ButtonBar.setButtonData(photosCheck, ButtonBar.ButtonData.LEFT);
            ButtonBar.setButtonUniformSize(photosCheck, false);
            buttonBar.getButtons().add(photosCheck);
            HBox sourceBox = new HBox(6, new Label("Style:"), sourceCombo);
            sourceBox.setAlignment(Pos.CENTER_LEFT);
            ButtonBar.setButtonData(sourceBox, ButtonBar.ButtonData.LEFT);
            ButtonBar.setButtonUniformSize(sourceBox, false);
            buttonBar.getButtons().add(sourceBox);
            HBox zoomBox = new HBox(6, new Label("Zoom:"), zoomCombo);
            zoomBox.setAlignment(Pos.CENTER_LEFT);
            ButtonBar.setButtonData(zoomBox, ButtonBar.ButtonData.LEFT);
            ButtonBar.setButtonUniformSize(zoomBox, false);
            buttonBar.getButtons().add(zoomBox);
            // consume() keeps the dialog open: putting a map on screen is repeatable, not a result.
            onScreenButton.addEventFilter(ActionEvent.ACTION, ev -> {
                onScreen();
                ev.consume();
            });
            onScreenButton.setDisable(entries.isEmpty());

            range.onChange(this::changed);
            photosCheck.selectedProperty().addListener((obs, old, sel) -> changed());
            sourceCombo.valueProperty().addListener((obs, old, sel) -> changed());
            zoomCombo.valueProperty().addListener((obs, old, sel) -> changed());
            updateHeader(dayPoints(), false);
            if (entries.isEmpty()) {
                setMessage("Nothing on sensors", "No entries in this trip yet.");
            } else {
                setMessage(AWAITING_ORDERS, CHOOSE_RANGE);
            }

            Dialogs.applyStylesheet(dlg.getDialogPane());
            dlg.showAndWait();
            pulse.stop();
            store.saveLastMapStyle(sourceCombo.getValue().id());
        }

        /** The style picked last time Map Assistant was closed (any trip), or {@link TileSource#OPEN_TOPO_MAP}
         *  if none was saved yet or the saved id no longer matches a known source (e.g. after one was removed). */
        private TileSource lastOrDefaultTileSource() {
            return store.loadLastMapStyle()
                    .flatMap(id -> Arrays.stream(TileSource.values()).filter(s -> s.id().equals(id)).findFirst())
                    .orElse(TileSource.OPEN_TOPO_MAP);
        }

        /** Range or option changed: the shown map, if any, no longer matches. */
        private void changed() {
            generation++;
            updateHeader(dayPoints(), false);
            if (imageView.getImage() != null) {
                imageView.setOpacity(STALE_OPACITY);
                setMessage("Range changed — " + ON_SCREEN + " to update.");
            } else if (!entries.isEmpty()) {
                setMessage(AWAITING_ORDERS, CHOOSE_RANGE);
            }
        }

        private void onScreen() {
            onScreenButton.setDisable(true);
            setMessage("Checking connectivity…");
            TileSource source = sourceCombo.getValue();
            Task<Boolean> connCheck = connectivityService.checkTask(source.connectivityUrl());
            connCheck.setOnSucceeded(e -> {
                if (Boolean.TRUE.equals(connCheck.getValue())) {
                    renderAndDisplay();
                } else {
                    onScreenButton.setDisable(false);
                    setMessage("Viewscreen offline", "No network connection — Sgt. Mapper needs internet access to reach " + source + ".");
                }
            });
            connCheck.setOnFailed(e -> {
                onScreenButton.setDisable(false);
                setMessage("Viewscreen offline", "No network connection — Sgt. Mapper needs internet access to reach " + source + ".");
            });
            Thread checker = new Thread(connCheck, "map-assistant-connectivity");
            checker.setDaemon(true);
            checker.start();
        }

        /** The outcome of one background render: the points actually plotted and, unless empty, the saved file. */
        private record RenderResult(MapPoints points, boolean withPhotos, Path file) {}

        private void renderAndDisplay() {
            MapPoints days = dayPoints();
            int requested = generation;
            LocalDate from = range.from();
            LocalDate to = range.to();
            TileSource source = sourceCombo.getValue();
            OsmMapRenderer.ZoomLevel zoom = zoomCombo.getValue();
            boolean wantPhotos = photosCheck.isSelected();
            Task<RenderResult> task = new Task<>() {
                @Override protected RenderResult call() {
                    MapPoints points = days;
                    if (wantPhotos) {
                        List<LatLon> locations = photoLocations.locations(photoLocations.images(trip, from, to));
                        points = days.withPhotos(locations, OsmMapRenderer.MAX_PINS - days.dayPinCount());
                    }
                    if (points.isEmpty()) return new RenderResult(points, wantPhotos, null);
                    Path target = attachmentsDir.mapFile(trip.ref(), from, to);
                    mapRenderer.render(points, source, zoom, width, height, target);
                    return new RenderResult(points, wantPhotos, target);
                }
            };
            task.setOnSucceeded(e -> {
                onScreenButton.setDisable(false);
                stopPulse();
                display(task.getValue());
                if (requested != generation) changed();
            });
            task.setOnFailed(e -> {
                onScreenButton.setDisable(false);
                stopPulse();
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
            updateHeader(points, result.withPhotos());
            if (points.isEmpty()) {
                imageView.setImage(null);
                String hint = "";
                if (!result.withPhotos()) {
                    int images = photoLocations.images(trip, range.from(), range.to()).size();
                    if (images > 0) {
                        hint = "\nTick 📷 Photo Locations from Backpack to look for the locations of its "
                                + images + (images == 1 ? " backpack image." : " backpack images.");
                    }
                }
                setMessage("Nothing on sensors", "Nothing to put on screen for this range." + hint);
                return;
            }
            imageView.setImage(new Image(result.file().toUri().toString()));
            imageView.setOpacity(1.0);
            setMessage(null);
            statusSink.status("🖖 Engage! On screen: " + describe(points, result.withPhotos())
                    + " — saved to " + attachmentsDir.root().relativize(result.file()));
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

        private void updateHeader(MapPoints points, boolean withPhotos) {
            dlg.setHeaderText(trip.name() + "\n" + describe(points, withPhotos));
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
        int n = points.starts().size();
        String s = "📍 " + (n == 0 ? "No" : n) + " start point" + (n == 1 ? "" : "s")
                + " · " + (points.stop() == null ? "🏁 no stop point" : "🏁 stop point");
        if (withPhotos) {
            int shown = points.photos().size();
            int found = points.photosFound();
            s += " · 📷 " + (found == 0 ? "No" : shown < found ? shown + " of " + found : found)
                    + " photo location" + (found == 1 ? "" : "s");
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

    /**
     * Shrinks the default 960×640 map (keeping its 3:2 aspect ratio) so the whole dialog — map,
     * header, range row and button bar — fits the current screen's usable area, down to a
     * {@value MIN_WIDTH}×{@value MIN_HEIGHT} floor. Returns {@code {width, height}}.
     */
    static int[] mapSize() {
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        double maxWidth = bounds.getWidth() - RESERVED_WIDTH;
        double maxHeight = bounds.getHeight() - RESERVED_HEIGHT;
        double scale = Math.min(1.0, Math.min(maxWidth / DEFAULT_WIDTH, maxHeight / DEFAULT_HEIGHT));
        int width = Math.max(MIN_WIDTH, (int) Math.round(DEFAULT_WIDTH * scale));
        int height = Math.max(MIN_HEIGHT, (int) Math.round(DEFAULT_HEIGHT * scale));
        return new int[] {width, height};
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
