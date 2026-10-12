package net.timafe.triptale.ui.dialog;

import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.map.MapPoints;
import net.timafe.triptale.map.PhotoLocations;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.ui.MapboxService;
import net.timafe.triptale.ui.StatusSink;
import net.timafe.triptale.util.Coordinates.LatLon;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Map Assistant (todo 54): plots a range of a trip's entries on a Mapbox static map — a start
 * pin per day, the last day's stop pin and, optionally, the locations of the backpack images.
 * Nothing is requested from Mapbox until "On Screen" is clicked; changing the range afterwards
 * marks the shown map as stale. See {@link MapboxService} for the token setup.
 */
public final class MapAssistantDialog {

    private static final int WIDTH = 960;
    private static final int HEIGHT = 640;
    private static final String ON_SCREEN = "🖥 On Screen";
    private static final double STALE_OPACITY = 0.35;

    private final MarkdownStore store;
    private final MapboxService mapboxService;
    private final PhotoLocations photoLocations;
    private final StatusSink statusSink;

    public MapAssistantDialog(MarkdownStore store, MapboxService mapboxService, PhotoLocations photoLocations,
                              StatusSink statusSink) {
        this.store = store;
        this.mapboxService = mapboxService;
        this.photoLocations = photoLocations;
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
            statusSink.error("Map Assistant failed: " + e.getMessage());
            return;
        }
        new Session(trip, entries, from, to).show();
    }

    /** One opened dialog and its state. */
    private final class Session {

        private final Trip trip;
        private final List<DiaryEntry> entries;
        private final EntryRangePicker range;
        private final CheckBox photosCheck = new CheckBox("📷 Photo Locations from Backpack");
        private final ImageView imageView = new ImageView();
        private final Label message = new Label();
        private final Dialog<Void> dlg = new Dialog<>();
        private Button onScreenButton;
        /** Bumped on every range/checkbox change, so a slow photo scan can tell it's outdated. */
        private int generation;

        Session(Trip trip, List<DiaryEntry> entries, LocalDate from, LocalDate to) {
            this.trip = trip;
            this.entries = entries;
            this.range = new EntryRangePicker(trip, entries, from, to);
        }

        void show() {
            dlg.setTitle("🗺 Map Assistant");
            photosCheck.setTooltip(new Tooltip("Locations of the backpack images of these days, from their EXIF GPS"));

            imageView.setFitWidth(WIDTH);
            imageView.setFitHeight(HEIGHT);
            imageView.setPreserveRatio(true);
            message.setWrapText(true);
            message.setMaxWidth(WIDTH - 80);
            message.setTextAlignment(TextAlignment.CENTER);
            message.setAlignment(Pos.CENTER);
            StackPane mapArea = new StackPane(imageView, message);
            mapArea.setPrefSize(WIDTH, HEIGHT);
            mapArea.setMinSize(WIDTH, HEIGHT);

            dlg.getDialogPane().setContent(new VBox(8, range.node(), mapArea));

            // APPLY sits next to Close; OTHER would float it to the middle of the button bar.
            ButtonType onScreenType = new ButtonType(ON_SCREEN, ButtonBar.ButtonData.APPLY);
            dlg.getDialogPane().getButtonTypes().setAll(onScreenType, ButtonType.CLOSE);
            onScreenButton = (Button) dlg.getDialogPane().lookupButton(onScreenType);
            // The only option shares the button bar, on the left. Added after setAll(): the pane
            // rebuilds the bar's buttons whenever the button types change.
            ButtonBar buttonBar = (ButtonBar) dlg.getDialogPane().lookup(".button-bar");
            ButtonBar.setButtonData(photosCheck, ButtonBar.ButtonData.LEFT);
            ButtonBar.setButtonUniformSize(photosCheck, false);
            buttonBar.getButtons().add(photosCheck);
            // consume() keeps the dialog open: putting a map on screen is repeatable, not a result.
            onScreenButton.addEventFilter(ActionEvent.ACTION, ev -> {
                onScreen();
                ev.consume();
            });
            onScreenButton.setDisable(entries.isEmpty() || !mapboxService.hasToken());

            range.onChange(this::changed);
            photosCheck.selectedProperty().addListener((obs, old, sel) -> changed());
            updateHeader(dayPoints(), false);
            if (!mapboxService.hasToken()) {
                setMessage("No Mapbox token configured — set one in Edit Settings.");
            } else if (entries.isEmpty()) {
                setMessage("No entries in this trip yet.");
            } else {
                setMessage("Choose a range, then put it on screen.");
            }

            Dialogs.applyStylesheet(dlg.getDialogPane());
            dlg.showAndWait();
        }

        /** Range or checkbox changed: the shown map, if any, no longer matches. */
        private void changed() {
            generation++;
            updateHeader(dayPoints(), false);
            if (imageView.getImage() != null) {
                imageView.setOpacity(STALE_OPACITY);
                setMessage("Range changed — " + ON_SCREEN + " to update.");
            } else if (mapboxService.hasToken() && !entries.isEmpty()) {
                setMessage("Choose a range, then put it on screen.");
            }
        }

        private void onScreen() {
            MapPoints days = dayPoints();
            if (!photosCheck.isSelected()) {
                display(days, false);
                return;
            }
            int requested = generation;
            LocalDate from = range.from();
            LocalDate to = range.to();
            Task<List<LatLon>> task = new Task<>() {
                @Override protected List<LatLon> call() {
                    return photoLocations.locations(photoLocations.images(trip, from, to));
                }
            };
            task.setOnSucceeded(e -> {
                onScreenButton.setDisable(false);
                display(days.withPhotos(task.getValue(), MapboxService.MAX_PINS - days.dayPinCount()), true);
                if (requested != generation) changed();
            });
            task.setOnFailed(e -> {
                onScreenButton.setDisable(false);
                Throwable ex = task.getException();
                setMessage("Could not read photo locations" + (ex != null ? ": " + ex.getMessage() : "."));
            });
            onScreenButton.setDisable(true);
            setMessage("Reading photo locations…");
            Thread worker = new Thread(task, "map-assistant-photos");
            worker.setDaemon(true);
            worker.start();
        }

        private void display(MapPoints points, boolean withPhotos) {
            updateHeader(points, withPhotos);
            if (points.isEmpty()) {
                imageView.setImage(null);
                String hint = "";
                if (!withPhotos) {
                    int images = photoLocations.images(trip, range.from(), range.to()).size();
                    if (images > 0) {
                        hint = "\nTick 📷 Photo Locations from Backpack to look for the locations of its "
                                + images + (images == 1 ? " backpack image." : " backpack images.");
                    }
                }
                setMessage("Nothing to put on screen for this range." + hint);
                return;
            }
            Image image = new Image(mapboxService.staticImageUrl(points, WIDTH, HEIGHT), true);
            imageView.setImage(image);
            imageView.setOpacity(1.0);
            setMessage("Loading map…");
            // Guard on the current image: an older, slower request must not touch the label.
            image.errorProperty().addListener((obs, wasError, isError) -> {
                if (isError && imageView.getImage() == image) {
                    Throwable ex = image.getException();
                    setMessage("Failed to load map"
                            + (ex != null && ex.getMessage() != null ? ": " + MapboxService.redact(ex.getMessage()) : "."));
                }
            });
            image.progressProperty().addListener((obs, oldV, newV) -> {
                if (newV.doubleValue() >= 1.0 && !image.isError() && imageView.getImage() == image
                        && imageView.getOpacity() == 1.0) {
                    setMessage(null);
                }
            });
            if (image.getProgress() >= 1.0 && !image.isError()) setMessage(null);
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
            boolean visible = text != null && !text.isBlank();
            message.setText(visible ? text : "");
            message.setVisible(visible);
            message.setManaged(visible);
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
}
