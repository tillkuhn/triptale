package net.timafe.triptale.ui;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.Node;
import javafx.util.StringConverter;
import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.TripTaleProperties;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.domain.TripRef;
import net.timafe.triptale.export.DiaryExporter;
import net.timafe.triptale.export.ExportTempFiles;
import net.timafe.triptale.git.GitService;
import net.timafe.triptale.storage.ExifReader;
import net.timafe.triptale.storage.ImpressionsResolver;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.storage.SettingsStore;
import net.timafe.triptale.ui.dialog.AboutDialog;
import net.timafe.triptale.ui.dialog.CoordinatesDialog;
import net.timafe.triptale.ui.dialog.EditSettingsDialog;
import net.timafe.triptale.ui.dialog.ExportDiaryDialog;
import net.timafe.triptale.ui.dialog.ImageViewerDialog;
import net.timafe.triptale.ui.dialog.NewTripDialog;
import net.timafe.triptale.ui.dialog.RemoteInfoDialog;
import net.timafe.triptale.ui.dialog.SyncProgressDialog;
import net.timafe.triptale.ui.dialog.TripDetailsDialog;
import net.timafe.triptale.ui.dialog.ViewSourceDialog;
import net.timafe.triptale.util.Coordinates;
import net.timafe.triptale.util.Markdown;
import net.timafe.triptale.util.RelativeTime;
import net.timafe.triptale.util.SaveTarget;
import net.timafe.triptale.util.Slugs;
import net.timafe.triptale.util.TextStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Controller for the single main window: trip/date navigation, the entry form and its dirty
 * tracking, and the git actions on the toolbar and menu bar.
 * <p>
 * Modal dialogs live in {@link net.timafe.triptale.ui.dialog} — each returns its result as data
 * rather than reaching back into this controller's fields.
 */
@Component
public class MainController implements StatusSink {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    @FXML private ComboBox<Integer> yearCombo;
    @FXML private ComboBox<Trip> tripCombo;
    @FXML private DatePicker datePicker;
    @FXML private TextField distanceField;
    @FXML private TextField altField;
    @FXML private TextField titleField;
    @FXML private TextField trackUrlField;
    @FXML private Button openTrackUrlButton;
    @FXML private Button coordinatesButton;
    @FXML private Button openCoordinatesButton;
    @FXML private Button impressionsButton;
    @FXML private Button favesButton;
    @FXML private TextArea talesArea;
    @FXML private Label talesLabel;
    @FXML private Label statusLabel;
    @FXML private Label tourDayLabel;
    @FXML private Button copyButton;
    @FXML private Button saveButton;
    @FXML private Button commitButton;
    @FXML private Button syncButton;
    @FXML private Button prevDayButton;
    @FXML private Button firstDayButton;
    @FXML private Button todayButton;
    @FXML private Button connectivityButton;
    @FXML private MenuItem pushMenuItem;
    @FXML private MenuItem pullMenuItem;
    @FXML private MenuItem syncMenuItem;
    @FXML private MenuItem commitMenuItem;
    @FXML private MenuItem viewSourceMenuItem;
    @FXML private MenuItem firstDayMenuItem;
    @FXML private MenuItem prevDayMenuItem;
    @FXML private MenuItem todayMenuItem;
    @FXML private MenuItem impressionsMenuItem;
    @FXML private MenuItem favesMenuItem;
    @FXML private MenuItem saveMenuItem;
    @FXML private MenuItem copyMenuItem;
    @FXML private Menu appMenu;
    @FXML private MenuItem aboutMenuItem;
    @FXML private MenuItem quitMenuItem;

    private static final String CREATE = "Create";
    private static final String UPDATE = "Update";
    private static final int COMMIT_MSG_INLINE_LIMIT = 5;

    private static final String TALE_FONT_SIZE_STATE_KEY = "taleFontSizePx";
    private static final int TALE_FONT_SIZE_DEFAULT_PX = 13;
    private static final int TALE_FONT_SIZE_MIN_PX = 10;
    private static final int TALE_FONT_SIZE_MAX_PX = 28;
    private static final int TALE_FONT_SIZE_STEP_PX = 1;

    // Connectivity button style classes
    private static final String CONN_CHECKING     = "connectivity-checking";
    private static final String CONN_CONNECTED    = "connectivity-connected";
    private static final String CONN_DISCONNECTED = "connectivity-disconnected";
    private static final String CONN_ICON_CHECKING     = "⟳";
    private static final String CONN_ICON_CONNECTED    = "📶";
    private static final String CONN_ICON_DISCONNECTED = "📵";

    private String baselineDistance = "";
    private String baselineAlt = "";
    private String baselineTitle = "";
    private String baselineTrackUrl = "";
    private Double baselineStartLat;
    private Double baselineStartLon;
    private String baselineTales = "";
    private Double startLat;
    private Double startLon;
    private boolean entryExists;
    private Instant talesUpdatedAt;
    private int taleFontSizePx = TALE_FONT_SIZE_DEFAULT_PX;

    /** Guard flag to prevent listener re-entrancy when reverting a navigation on Cancel. */
    private boolean navigating = false;

    /** Tri-state: null = checking/unknown, true = online, false = offline */
    private Boolean connected = null;

    private final Map<String, String> pending = new LinkedHashMap<>();

    private final MarkdownStore store;
    private final GitService gitService;
    private final SettingsStore settingsStore;
    private final ImpressionsResolver impressionsResolver;
    private final ConnectivityService connectivityService;
    private final ExportTempFiles exportTempFiles;
    private final BrowserLauncher browser;
    private final String appName;

    // Dialogs whose own dependencies this controller has no other use for.
    private final ExportDiaryDialog exportDiaryDialog;
    private final ImageViewerDialog imageViewerDialog;
    private final AboutDialog aboutDialog;

    public MainController(MarkdownStore store, GitService gitService, SettingsStore settingsStore,
                          DiaryExporter diaryExporter, ImpressionsResolver impressionsResolver,
                          ExifReader exifReader,
                          ConnectivityService connectivityService,
                          ExportTempFiles exportTempFiles,
                          TripTaleProperties tripTaleProperties,
                          ObjectProvider<BuildProperties> buildPropertiesProvider,
                          ObjectProvider<HostServices> hostServicesProvider) {
        this.store = store;
        this.gitService = gitService;
        this.settingsStore = settingsStore;
        this.impressionsResolver = impressionsResolver;
        this.connectivityService = connectivityService;
        this.exportTempFiles = exportTempFiles;
        this.appName = tripTaleProperties.getAppName();
        this.browser = new BrowserLauncher(hostServicesProvider.getIfAvailable());
        this.exportDiaryDialog =
                new ExportDiaryDialog(diaryExporter, settingsStore, exportTempFiles, browser, this);
        this.imageViewerDialog = new ImageViewerDialog(exifReader, browser, this);
        this.aboutDialog =
                new AboutDialog(appName, buildPropertiesProvider.getIfAvailable(), browser);
    }

    private static final DateTimeFormatter DATE_DISPLAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE", Locale.ENGLISH);

    @FXML
    public void initialize() {
        appMenu.setText(appName);
        aboutMenuItem.setText("ⓘ About " + appName);
        quitMenuItem.setText("⏻ Quit " + appName);
        loadTaleFontSize();
        applyTaleFontSize();
        datePicker.setConverter(new StringConverter<>() {
            @Override public String toString(LocalDate d) { return d == null ? "" : DATE_DISPLAY.format(d); }
            @Override public LocalDate fromString(String s) {
                if (s == null || s.isBlank()) return null;
                try { return LocalDate.parse(s.trim(), DATE_DISPLAY); } catch (Exception e) { return null; }
            }
        });
        tripCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Trip t) { return t == null ? "" : t.name(); }
            @Override public Trip fromString(String s) { return null; }
        });
        yearCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Integer y) { return y == null ? "" : y.toString(); }
            @Override public Integer fromString(String s) { return null; }
        });
        datePicker.setDayCellFactory(dp -> new DateCell() {
            @Override
            public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                Trip trip = tripCombo.getValue();
                if (!empty && item != null && trip != null && trip.startDate() != null
                        && item.isBefore(trip.startDate())) {
                    setDisable(true);
                    setStyle("-fx-background-color: #f0f0f0;");
                }
            }
        });
        exportTempFiles.sweep();
        boolean ready = performStartupChecks();
        yearCombo.valueProperty().addListener((obs, old, sel) -> {
            if (sel == null) return;
            reloadTrips(sel);
            if (!tripCombo.getItems().isEmpty()) {
                tripCombo.getSelectionModel().select(0);
            } else {
                tripCombo.setValue(null);
                datePicker.setValue(null);
                loadEntry();
            }
        });
        tripCombo.valueProperty().addListener((obs, old, sel) -> {
            if (navigating) return;
            if (sel == null) return;
            SaveTarget saveTarget = old != null
                    ? SaveTarget.forTripChange(old.ref(), datePicker.getValue())
                    : null;
            if (!confirmNavigateAway(saveTarget, () -> tripCombo.setValue(old))) return;
            store.saveLastTripPath(sel.ref());
            List<LocalDate> dates = store.listEntryDates(sel.ref());
            LocalDate target;
            String reason;
            if (!dates.isEmpty()) {
                target = dates.get(dates.size() - 1);
                reason = "Opened last entry (" + target + ")";
            } else if (sel.startDate() != null) {
                target = sel.startDate();
                reason = "No entries — starting at day 1 (" + target + ")";
            } else {
                target = LocalDate.now();
                reason = "No entries — defaulting to today (" + target + ")";
            }
            datePicker.setValue(target);
            loadEntry();
            updatePrevButtonState();
            status(reason);
        });
        datePicker.valueProperty().addListener((obs, old, sel) -> {
            if (navigating) return;
            Trip trip = tripCombo.getValue();
            if (sel != null && trip != null && trip.startDate() != null
                    && sel.isBefore(trip.startDate())) {
                datePicker.setValue(trip.startDate());
                status("Snapped to day 1 (" + trip.startDate() + ")");
                return;
            }
            SaveTarget saveTarget = trip != null && old != null
                    ? SaveTarget.forDateChange(trip.ref(), old)
                    : null;
            if (!confirmNavigateAway(saveTarget, () -> datePicker.setValue(old))) return;
            loadEntry();
            updatePrevButtonState();
        });
        distanceField.textProperty().addListener((o, a, b) -> updateDirty());
        altField.textProperty().addListener((o, a, b) -> updateDirty());
        titleField.textProperty().addListener((o, a, b) -> updateDirty());
        trackUrlField.textProperty().addListener((o, a, b) -> updateDirty());
        talesArea.textProperty().addListener((o, a, b) -> updateDirty());
        if (ready) {
            restoreLastSelection();
        }
        updateDirty();
        updatePrevButtonState();
        saveButton.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN),
                        () -> { if (isDirty()) onSave(); });
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.K, KeyCombination.SHORTCUT_DOWN),
                        () -> { if (canCommit()) onCommit(); });
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.LEFT, KeyCombination.ALT_DOWN),
                        this::onPrevDay);
                newScene.getAccelerators().put(
                        new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.ALT_DOWN),
                        this::onNextDay);
            }
        });
        updateCommitButton();
        if (ready) {
            status("Data dir: " + settingsStore.load().resolvedDataDir()
                    .map(Path::toString).orElse("(not configured)"));
            // Kick off a non-blocking connectivity check on startup
            triggerConnectivityCheck();
        }
    }

    /**
     * Runs before any trip/entry data is loaded. Returns {@code true} if it's safe to proceed
     * with {@link #restoreLastSelection()} and the rest of startup, {@code false} if the app
     * should stay in an empty-but-functional state this session (e.g. unconfigured data dir, or
     * user declined to initialize a new repo).
     * <p>
     * Runs before {@code stage.show()} (this method is called from {@code initialize()}, which
     * {@code FXMLLoader.load()} invokes synchronously in {@code TripTaleApplication.start()}) —
     * JavaFX allows showing a {@link Dialog}/{@link Alert} here even though the primary stage
     * isn't visible yet.
     */
    private boolean performStartupChecks() {
        if (!gitService.isConfigured()) {
            Alert warn = new Alert(Alert.AlertType.WARNING,
                    "No data directory is configured yet. Open " + appName + " → Edit Settings… to set one.",
                    ButtonType.OK);
            warn.setTitle(appName);
            warn.setHeaderText("Data directory not configured");
            Dialogs.applyStylesheet(warn.getDialogPane());
            warn.showAndWait();
            return false;
        }
        if (gitService.needsInit()) {
            Path dataDir = settingsStore.load().resolvedDataDir().orElseThrow();
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "No git repository found at " + dataDir + ". Initialize one now?",
                    ButtonType.YES, ButtonType.NO);
            confirm.setTitle(appName);
            confirm.setHeaderText("Initialize git repository");
            Dialogs.applyStylesheet(confirm.getDialogPane());
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.YES) {
                // Leave as-is; the directory is still empty so we'll re-prompt next launch.
                return false;
            }
            gitService.initRepo();
        } else {
            // .git already exists — idempotent, no prompt needed (matches old silent behavior).
            gitService.initRepo();
        }
        return true;
    }

    /** Years from disk, always including the current calendar year, descending. */
    private void reloadYears() {
        Integer previouslySelected = yearCombo.getValue();
        List<Integer> years = new ArrayList<>(store.listYears());
        int currentYear = LocalDate.now().getYear();
        if (!years.contains(currentYear)) years.add(currentYear);
        years.sort(Comparator.reverseOrder());
        yearCombo.setItems(FXCollections.observableArrayList(years));
        if (previouslySelected != null && years.contains(previouslySelected)) {
            yearCombo.setValue(previouslySelected);
        }
    }

    /** Trips for the given year (empty list disables the combo). */
    private void reloadTrips(int year) {
        tripCombo.setItems(FXCollections.observableArrayList(store.listTrips(year)));
        tripCombo.setDisable(tripCombo.getItems().isEmpty());
    }

    /** Refreshes years and the current year's trips after external changes (pull/sync), preserving selection. */
    private void reloadAll() {
        reloadYears();
        Integer year = yearCombo.getValue();
        if (year != null) reloadTrips(year);
    }

    /** Restores the cached (year, trip) selection on startup, defaulting to the current year. */
    private void restoreLastSelection() {
        reloadYears();
        TripRef last = store.loadLastTripPath().orElse(null);
        int year = last != null && yearCombo.getItems().contains(last.year())
                ? last.year()
                : LocalDate.now().getYear();
        yearCombo.setValue(year);
        reloadTrips(year);
        Trip toSelect = last != null ? findTrip(last.slug()) : null;
        if (toSelect == null && !tripCombo.getItems().isEmpty()) {
            toSelect = tripCombo.getItems().get(0);
        }
        if (toSelect != null) {
            tripCombo.getSelectionModel().select(toSelect);
        }
    }

    /** The loaded trip with this slug in the currently selected year, or null. */
    private Trip findTrip(String slug) {
        return tripCombo.getItems().stream()
                .filter(t -> t.slug().equals(slug))
                .findFirst().orElse(null);
    }

    @FXML
    public void onNewTrip() {
        Optional<NewTripDialog.Spec> spec = new NewTripDialog().showAndWait();
        if (spec.isEmpty()) return;
        String name = spec.get().name();
        LocalDate start = spec.get().startDate();
        String slug = Slugs.toSlug(name);
        int year = start.getYear();
        TripRef ref = new TripRef(year, slug);
        if (store.tripExists(ref)) {
            error("A trip named \"" + slug + "\" already exists for " + year);
            return;
        }
        store.saveTrip(new Trip(year, slug, name, start, spec.get().description()));
        addPending(ref.path(), CREATE);
        reloadYears();
        yearCombo.setValue(year);
        reloadTrips(year);
        tripCombo.getSelectionModel().select(findTrip(slug));
        status("Created trip " + ref.path());
    }

    @FXML
    public void onTripDetails() {
        Trip trip = tripCombo.getValue();
        if (trip == null) { error("No trip selected"); return; }
        Optional<Trip> updated = new TripDetailsDialog(store).showAndWait(trip);
        if (updated.isEmpty()) return;
        store.saveTrip(updated.get());
        addPending(trip.ref().path(), UPDATE);
        reloadTrips(trip.year());
        tripCombo.getSelectionModel().select(findTrip(trip.slug()));
        status("Updated trip " + trip.ref().path());
    }

    @FXML
    public void onExportDiary() {
        Trip trip = tripCombo.getValue();
        if (trip == null) { error("No trip selected"); return; }
        exportDiaryDialog.show(trip);
    }

    @FXML
    public void onViewSource() {
        Trip trip = tripCombo.getValue();
        LocalDate date = datePicker.getValue();
        if (trip == null || date == null) { error("No entry selected"); return; }
        new ViewSourceDialog(store, this).show(trip.ref(), date);
    }

    @FXML
    public void onFirstDay() {
        Trip trip = tripCombo.getValue();
        if (trip == null || trip.startDate() == null) return;
        datePicker.setValue(trip.startDate());
        status("Snapped to day 1 (" + trip.startDate() + ")");
    }

    @FXML
    public void onPrevDay() {
        if (datePicker.getValue() != null) datePicker.setValue(datePicker.getValue().minusDays(1));
    }

    @FXML
    public void onNextDay() {
        if (datePicker.getValue() != null) datePicker.setValue(datePicker.getValue().plusDays(1));
    }

    @FXML
    public void onToday() {
        LocalDate today = LocalDate.now();
        datePicker.setValue(today);
        if (today.equals(datePicker.getValue())) {
            status("Snapped to today (" + today + ")");
        }
    }

    @FXML
    public void onZoomIn() {
        setTaleFontSize(taleFontSizePx + TALE_FONT_SIZE_STEP_PX);
    }

    @FXML
    public void onZoomOut() {
        setTaleFontSize(taleFontSizePx - TALE_FONT_SIZE_STEP_PX);
    }

    /** Resets all View-menu display settings to their defaults. Currently just tale text zoom; extend here as more are added. */
    @FXML
    public void onResetView() {
        setTaleFontSize(TALE_FONT_SIZE_DEFAULT_PX);
        status("View reset to defaults");
    }

    /** Reads the persisted tale text font size from .state.yml, falling back to the default if unset or unconfigured. */
    private void loadTaleFontSize() {
        try {
            Object stored = store.loadState().get(TALE_FONT_SIZE_STATE_KEY);
            if (stored instanceof Number n) {
                taleFontSizePx = clampTaleFontSize(n.intValue());
            }
        } catch (RuntimeException e) {
            // Data dir not configured yet — keep the default size for this session.
        }
    }

    private void setTaleFontSize(int px) {
        taleFontSizePx = clampTaleFontSize(px);
        applyTaleFontSize();
        try {
            store.saveState(TALE_FONT_SIZE_STATE_KEY, taleFontSizePx);
        } catch (RuntimeException e) {
            // Data dir not configured yet — zoom still works for this session, just isn't persisted.
        }
    }

    private static int clampTaleFontSize(int px) {
        return Math.max(TALE_FONT_SIZE_MIN_PX, Math.min(TALE_FONT_SIZE_MAX_PX, px));
    }

    private void applyTaleFontSize() {
        talesArea.setStyle("-fx-font-size: " + taleFontSizePx + "px;");
    }

    private void loadEntry() {
        Trip trip = tripCombo.getValue();
        LocalDate date = datePicker.getValue();
        updateTourDay(trip, date);
        if (trip == null || date == null) {
            updateViewSourceMenuItem(false);
            return;
        }
        entryExists = store.entryExists(trip.ref(), date);
        updateViewSourceMenuItem(entryExists);
        DiaryEntry e = store.loadEntry(trip.ref(), date);
        distanceField.setText(e.distance() == null ? "" : e.distance().toString());
        altField.setText(e.altitudeMeters() == null ? "" : e.altitudeMeters().toString());
        titleField.setText(e.title() == null ? DiaryEntry.DEFAULT_TITLE : e.title());
        trackUrlField.setText(e.trackUrl() == null ? "" : e.trackUrl());
        startLat = e.startLat();
        startLon = e.startLon();
        updateCoordinatesButton();
        talesArea.setText(e.tales() == null ? "" : e.tales());
        talesUpdatedAt = readTalesLastModified(trip, date);
        updateTalesLabel();
        updateImpressionsButton(trip, date);
        updateFavesButton(trip, date);
        snapshotBaseline();
        updateDirty();
    }

    private void updateImpressionsButton(Trip trip, LocalDate date) {
        if (impressionsButton == null) return;
        int count = resolveImpressions(settingsStore.load().getImpressionsFilePattern(), trip, date).size();
        impressionsButton.setText(count == 0
                ? "No Impressions"
                : "🖼 " + count + " Impression" + (count == 1 ? "" : "s") + " ›");
        impressionsButton.setDisable(count == 0);
        if (impressionsMenuItem != null) impressionsMenuItem.setDisable(count == 0);
    }

    private void updateFavesButton(Trip trip, LocalDate date) {
        if (favesButton == null) return;
        int count = resolveImpressions(settingsStore.load().getImpressionsFaveFilePattern(), trip, date).size();
        favesButton.setText(count == 0
                ? "No Faves"
                : "🖼 " + count + " Fave" + (count == 1 ? "" : "s") + " ›");
        favesButton.setDisable(count == 0);
        if (favesMenuItem != null) favesMenuItem.setDisable(count == 0);
    }

    /** Images matching a configured pattern for this trip/date; empty when anything is missing. */
    private List<Path> resolveImpressions(String pattern, Trip trip, LocalDate date) {
        if (pattern == null || pattern.isBlank() || trip == null || date == null) return List.of();
        return impressionsResolver.resolve(pattern, trip, date);
    }

    private void updateCoordinatesButton() {
        boolean set = startLat != null && startLon != null;
        if (coordinatesButton != null) {
            coordinatesButton.setText(set ? "📍 " + Coordinates.toDdm(startLat, startLon) : "📍 Uncharted");
        }
        if (openCoordinatesButton != null) {
            openCoordinatesButton.setDisable(!set);
        }
    }

    @FXML
    public void onOpenCoordinates() {
        if (startLat == null || startLon == null) return;
        browser.open("https://www.google.com/maps?q=" + startLat + "," + startLon);
    }

    private void updateViewSourceMenuItem(boolean exists) {
        if (viewSourceMenuItem == null) return;
        viewSourceMenuItem.setDisable(!exists);
    }

    private Instant readTalesLastModified(Trip trip, LocalDate date) {
        try {
            return Files.getLastModifiedTime(store.entryFile(trip.ref(), date)).toInstant();
        } catch (IOException ex) {
            return null;
        }
    }

    private void updateTalesLabel() {
        if (talesLabel == null) return;
        String text = talesArea.getText();
        if (text == null || text.isBlank()) {
            talesLabel.setText("🐉 Tales · here be dragons");
            return;
        }
        int words = TextStats.wordCount(text);
        String ago = talesUpdatedAt == null ? "just now" : RelativeTime.ago(talesUpdatedAt, Instant.now());
        talesLabel.setText("🐉 Tales · " + words + " word" + (words == 1 ? "" : "s") + " updated " + ago);
    }

    private void snapshotBaseline() {
        baselineDistance = distanceField.getText();
        baselineAlt = altField.getText();
        baselineTitle = titleField.getText();
        baselineTrackUrl = trackUrlField.getText();
        baselineStartLat = startLat;
        baselineStartLon = startLon;
        baselineTales = talesArea.getText();
    }

    private boolean isDirty() {
        return !Objects.equals(distanceField.getText(), baselineDistance)
                || !Objects.equals(altField.getText(), baselineAlt)
                || !Objects.equals(titleField.getText(), baselineTitle)
                || !Objects.equals(trackUrlField.getText(), baselineTrackUrl)
                || !Objects.equals(startLat, baselineStartLat)
                || !Objects.equals(startLon, baselineStartLon)
                || !Objects.equals(talesArea.getText(), baselineTales);
    }

    /**
     * If the form is dirty, shows a Save / Discard / Cancel dialog.
     * <ul>
     *   <li>Save — persists to {@code target} (the trip/date the unsaved edits actually belong to,
     *       <em>not</em> whatever the combo/picker controls currently report — see {@link SaveTarget})
     *       then returns {@code true} (navigation proceeds).</li>
     *   <li>Discard — returns {@code true} (navigation proceeds, changes are lost).</li>
     *   <li>Cancel — invokes {@code revert} to undo the navigation, returns {@code false}.</li>
     * </ul>
     * Returns {@code true} immediately when the form is not dirty.
     */
    private boolean confirmNavigateAway(SaveTarget target, Runnable revert) {
        if (!isDirty()) return true;
        String dateLabel = target != null && target.date() != null ? target.date().toString() : "current entry";
        ButtonType save    = new ButtonType("Save");
        ButtonType discard = new ButtonType("Discard");
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "You have unsaved changes for " + dateLabel + ". What would you like to do?",
                save, discard, ButtonType.CANCEL);
        alert.setTitle("Unsaved Changes");
        alert.setHeaderText("Unsaved changes");
        Dialogs.applyStylesheet(alert.getDialogPane());
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isEmpty() || result.get() == ButtonType.CANCEL) {
            navigating = true;
            try { revert.run(); } finally { navigating = false; }
            return false;
        }
        if (result.get() == save && target != null && isValidEntry()) {
            doSave(target.trip(), target.date());
        }
        return true;
    }

    /** An entry needs at least a title and some tale text before it can be written. */
    private boolean isValidEntry() {
        return !titleField.getText().isBlank() && !talesArea.getText().isBlank();
    }

    private void updateDirty() {
        boolean saveEnabled = isDirty() && isValidEntry();
        String saveLabel = entryExists ? "💾 Save Tale" : "📝 Create Tale";
        if (saveButton != null) {
            saveButton.setDisable(!saveEnabled);
            saveButton.setText(saveLabel);
        }
        if (saveMenuItem != null) {
            saveMenuItem.setDisable(!saveEnabled);
            saveMenuItem.setText(saveLabel);
        }
        boolean hasContent = talesArea != null && !talesArea.getText().isBlank()
                && tripCombo.getValue() != null && datePicker.getValue() != null;
        if (copyButton != null) {
            copyButton.setDisable(!hasContent);
        }
        if (copyMenuItem != null) {
            copyMenuItem.setDisable(!hasContent);
        }
        if (openTrackUrlButton != null) {
            openTrackUrlButton.setDisable(!UiText.isValidHttpUrl(trackUrlField.getText()));
        }
        updateCommitButton();
    }

    private void addPending(String label, String action) {
        pending.putIfAbsent(label, action);
        updateCommitButton();
    }

    private boolean canCommit() {
        return !pending.isEmpty() && !isDirty();
    }

    private void updateCommitButton() {
        String label = "📦 Commit (" + pending.size() + ")";
        boolean disable = !canCommit();
        if (commitButton != null) {
            commitButton.setText(label);
            commitButton.setDisable(disable);
        }
        if (commitMenuItem != null) {
            commitMenuItem.setText(label);
            commitMenuItem.setDisable(disable);
        }
    }

    private String buildCommitMessage() {
        int total = pending.size();
        if (total <= COMMIT_MSG_INLINE_LIMIT) {
            List<String> creates = new ArrayList<>();
            List<String> updates = new ArrayList<>();
            pending.forEach((label, action) ->
                    (CREATE.equals(action) ? creates : updates).add(label));
            StringBuilder sb = new StringBuilder();
            if (!creates.isEmpty()) sb.append(CREATE).append(": ").append(String.join(", ", creates));
            if (!updates.isEmpty()) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append(UPDATE).append(": ").append(String.join(", ", updates));
            }
            return sb.toString();
        }
        List<String> labels = new ArrayList<>(pending.keySet());
        String head = String.join(", ", labels.subList(0, COMMIT_MSG_INLINE_LIMIT));
        int more = total - COMMIT_MSG_INLINE_LIMIT;
        return UPDATE + ": " + head + ", ... and " + more + " more (" + total + " total)";
    }

    private void updatePrevButtonState() {
        Trip trip = tripCombo.getValue();
        LocalDate date = datePicker.getValue();
        boolean prevDisabled;
        if (trip == null || trip.startDate() == null || date == null) {
            prevDisabled = false;
        } else {
            prevDisabled = !date.isAfter(trip.startDate());
        }
        if (prevDayButton != null) prevDayButton.setDisable(prevDisabled);
        if (firstDayButton != null) firstDayButton.setDisable(prevDisabled);
        if (prevDayMenuItem != null) prevDayMenuItem.setDisable(prevDisabled);
        if (firstDayMenuItem != null) firstDayMenuItem.setDisable(prevDisabled);
        if (todayButton != null || todayMenuItem != null) {
            LocalDate target = LocalDate.now();
            if (trip != null && trip.startDate() != null && target.isBefore(trip.startDate())) {
                target = trip.startDate();
            }
            boolean todayDisabled = date == null || date.equals(target);
            if (todayButton != null) todayButton.setDisable(todayDisabled);
            if (todayMenuItem != null) todayMenuItem.setDisable(todayDisabled);
        }
    }

    private void updateTourDay(Trip trip, LocalDate date) {
        if (tourDayLabel == null) return;
        if (trip == null || trip.startDate() == null || date == null) {
            tourDayLabel.setText("");
            return;
        }
        long day = ChronoUnit.DAYS.between(trip.startDate(), date) + 1;
        tourDayLabel.setText("Day " + day + " (" + relativeDayLabel(date, day) + ")");
    }

    private static String relativeDayLabel(LocalDate date, long tourDay) {
        long delta = ChronoUnit.DAYS.between(LocalDate.now(), date);
        if (delta == 0) return "today";
        if (delta == -1) return "yesterday";
        if (delta == 1) return "tomorrow";
        if (delta < -1) return tourDay == 1 ? "first day" : (-delta) + " days ago";
        return "in " + delta + " days";
    }

    @FXML
    public void onSave() {
        Trip trip = tripCombo.getValue();
        LocalDate date = datePicker.getValue();
        if (trip == null) { error("No trip selected"); return; }
        if (date == null) { error("No date selected"); return; }
        doSave(trip.ref(), date);
    }

    /**
     * Persists the current form field contents to {@code trip}/{@code date}.
     * <p>
     * Callers must pass the trip/date the form fields actually belong to explicitly rather than
     * re-reading {@code tripCombo.getValue()}/{@code datePicker.getValue()} — those controls may
     * already have advanced to a new selection (see {@link SaveTarget} and {@link #confirmNavigateAway}).
     */
    private void doSave(TripRef trip, LocalDate date) {
        Double distance = parseDouble(distanceField.getText(), "distance");
        Double alt = parseDouble(altField.getText(), "altitude");
        if (distance == null && !distanceField.getText().isBlank()) return;
        if (alt == null && !altField.getText().isBlank()) return;
        DiaryEntry entry = DiaryEntry.builder(date)
                .distance(distance)
                .altitudeMeters(alt)
                .title(titleField.getText())
                .trackUrl(trackUrlField.getText())
                .startLat(startLat)
                .startLon(startLon)
                .tales(talesArea.getText())
                .build();
        boolean wasNew = !entryExists;
        store.saveEntry(trip, entry);
        entryExists = true;
        updateViewSourceMenuItem(true);
        talesUpdatedAt = Instant.now();
        updateTalesLabel();
        addPending(trip.path() + "/" + date, wasNew ? CREATE : UPDATE);
        talesArea.setText(Markdown.demoteH1(talesArea.getText()));
        snapshotBaseline();
        updateDirty();
        status("Saved " + trip.path() + "/" + date);
    }

    @FXML
    public void onCopyTale() {
        String title = titleField.getText();
        String tales = talesArea.getText();
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank() && !title.equals(DiaryEntry.DEFAULT_TITLE)) {
            sb.append(title).append("\n\n");
        }
        if (tales != null) sb.append(tales);
        Clipboards.putString(sb.toString());
    }

    @FXML
    public void onCommit() {
        if (!canCommit()) return;
        String message = buildCommitMessage();
        String sha;
        try {
            sha = gitService.commitAll(message);
        } catch (RuntimeException e) {
            error(e.getMessage());
            return;
        }
        int n = pending.size();
        pending.clear();
        updateCommitButton();
        String suffix = sha != null ? " (" + sha + ")" : "";
        status("Committed " + n + " change" + (n == 1 ? "" : "s") + suffix);
    }

    @FXML
    public void onCheckConnectivity() {
        triggerConnectivityCheck();
    }

    private void triggerConnectivityCheck() {
        // Show "checking" state immediately on the FX thread
        connected = null;
        applyConnectivityState();

        String remoteUrl;
        try {
            remoteUrl = gitService.remoteUrl();
        } catch (RuntimeException e) {
            remoteUrl = "";
        }

        Task<Boolean> task = connectivityService.checkTask(remoteUrl);
        task.setOnSucceeded(e -> Platform.runLater(() -> {
            connected = task.getValue();
            applyConnectivityState();
        }));
        task.setOnFailed(e -> Platform.runLater(() -> {
            connected = false;
            applyConnectivityState();
        }));
        Thread thread = new Thread(task, "connectivity-check");
        thread.setDaemon(true);
        thread.start();
    }

    private void applyConnectivityState() {
        boolean hasRemote = hasRemoteConfigured();
        if (connectivityButton != null) {
            connectivityButton.getStyleClass().removeAll(CONN_CHECKING, CONN_CONNECTED, CONN_DISCONNECTED);
            if (connected == null) {
                connectivityButton.getStyleClass().add(CONN_CHECKING);
                connectivityButton.setText(CONN_ICON_CHECKING);
                connectivityButton.setTooltip(new Tooltip("Checking connectivity…"));
            } else if (connected) {
                connectivityButton.getStyleClass().add(CONN_CONNECTED);
                connectivityButton.setText(CONN_ICON_CONNECTED);
                connectivityButton.setTooltip(new Tooltip("Connected — click to recheck"));
            } else {
                connectivityButton.getStyleClass().add(CONN_DISCONNECTED);
                connectivityButton.setText(CONN_ICON_DISCONNECTED);
                connectivityButton.setTooltip(new Tooltip("Offline — click to recheck"));
            }
        }
        // Gray out push/pull when offline or no remote configured
        boolean remoteEnabled = Boolean.TRUE.equals(connected) && hasRemote;
        if (pushMenuItem != null) pushMenuItem.setDisable(!remoteEnabled);
        if (pullMenuItem != null) pullMenuItem.setDisable(!remoteEnabled);
        if (syncMenuItem != null) syncMenuItem.setDisable(!remoteEnabled);
        if (syncButton != null) syncButton.setDisable(!remoteEnabled);
    }

    private boolean hasRemoteConfigured() {
        try {
            return !gitService.remoteUrl().isBlank();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @FXML
    public void onPull() {
        if (!pending.isEmpty()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "You have " + pending.size() + " uncommitted save"
                            + (pending.size() == 1 ? "" : "s")
                            + ". Pull may fail or create a merge. Continue?",
                    ButtonType.OK, ButtonType.CANCEL);
            confirm.setTitle("Pull");
            confirm.setHeaderText("Uncommitted changes");
            Dialogs.applyStylesheet(confirm.getDialogPane());
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) return;
        }
        try {
            gitService.pull();
            reloadAll();
            loadEntry();
            status("Pulled from remote");
        } catch (RuntimeException e) {
            error(UiText.describe(e));
        }
    }

    @FXML
    public void onPush() {
        try {
            gitService.push();
            status("Pushed to remote");
        } catch (RuntimeException e) {
            error(UiText.describe(e));
        }
    }

    @FXML
    public void onSync() {
        String message = pending.isEmpty() ? "Sync: external changes" : buildCommitMessage();
        new SyncProgressDialog(gitService, settingsStore).start(message, sha -> {
            if (sha != null) {
                pending.clear();
                updateCommitButton();
            }
            reloadAll();
            loadEntry();
            status("Synced with remote" + (sha != null ? " (committed " + sha + ")" : ""));
        });
    }

    @FXML
    public void onRemoteInfo() {
        new RemoteInfoDialog(gitService, settingsStore).show();
    }

    @FXML
    public void onExit() {
        boolean dirty = isDirty();
        boolean hasPending = !pending.isEmpty();
        if (!dirty && !hasPending) {
            Platform.exit();
            return;
        }
        StringBuilder body = new StringBuilder();
        if (dirty) body.append("You have unsaved edits to the current entry");
        if (dirty && hasPending) body.append("\nand ");
        if (hasPending) {
            body.append(pending.size())
                    .append(" saved change")
                    .append(pending.size() == 1 ? "" : "s")
                    .append(" not yet committed");
        }
        body.append(".");

        ButtonType commitAndExit = new ButtonType("Commit & Exit");
        ButtonType exitAnyway = new ButtonType("Exit anyway");
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, body.toString(),
                commitAndExit, exitAnyway, ButtonType.CANCEL);
        confirm.setTitle("Exit " + appName);
        Dialogs.applyStylesheet(confirm.getDialogPane());
        confirm.setHeaderText(dirty && hasPending
                ? "Unsaved and uncommitted changes"
                : (dirty ? "Unsaved changes" : "Uncommitted changes"));
        // Same rule as the main Commit button: cannot commit while form is dirty.
        Node commitBtn = confirm.getDialogPane().lookupButton(commitAndExit);
        if (commitBtn != null) commitBtn.setDisable(dirty || !hasPending);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() == ButtonType.CANCEL) return;
        if (result.get() == commitAndExit) {
            try {
                gitService.commitAll(buildCommitMessage());
            } catch (RuntimeException e) {
                error(e.getMessage());
                return;
            }
        }
        Platform.exit();
    }

    @FXML
    public void onAbout() {
        aboutDialog.show();
    }

    @FXML
    public void onOpenTrackUrl() {
        String url = trackUrlField.getText();
        if (UiText.isValidHttpUrl(url)) browser.open(url.trim());
    }

    @FXML
    public void onCoordinates() {
        Optional<CoordinatesDialog.Result> result =
                new CoordinatesDialog().showAndWait(startLat, startLon);
        if (result.isEmpty()) return;
        Coordinates.LatLon coords = result.get().coords();
        startLat = coords == null ? null : coords.lat();
        startLon = coords == null ? null : coords.lon();
        updateCoordinatesButton();
        updateDirty();
    }

    @FXML
    public void onEditSettings() {
        AppSettings settings = settingsStore.load();
        String previousDataDir = settings.getDataDir();
        Optional<AppSettings> edited =
                new EditSettingsDialog().showAndWait(settings, settingsStore.settingsFile());
        if (edited.isEmpty()) return;
        settingsStore.save(edited.get());

        updateImpressionsButton(tripCombo.getValue(), datePicker.getValue());
        updateFavesButton(tripCombo.getValue(), datePicker.getValue());

        if (!edited.get().getDataDir().equals(previousDataDir)) {
            status("Settings saved — restart " + appName + " for the new data directory to take effect");
        } else {
            status("Settings saved");
        }
    }

    @FXML
    public void onShowImpressions() {
        showImpressions("Impressions", settingsStore.load().getImpressionsFilePattern());
    }

    @FXML
    public void onShowFaves() {
        showImpressions("Faves", settingsStore.load().getImpressionsFaveFilePattern());
    }

    private void showImpressions(String title, String pattern) {
        LocalDate date = datePicker.getValue();
        List<Path> images = resolveImpressions(pattern, tripCombo.getValue(), date);
        if (images.isEmpty()) return;
        imageViewerDialog.show(title, images, date);
    }

    private Double parseDouble(String s, String field) {
        if (s == null || s.isBlank()) return null;
        Double value = UiText.parseDecimal(s);
        if (value == null) error("Invalid " + field + ": " + s);
        return value;
    }

    @Override
    public void status(String msg) {
        log.info(msg);
        if (statusLabel != null) statusLabel.setText(msg);
    }

    @Override
    public void error(String msg) {
        log.warn(msg);
        if (statusLabel != null) statusLabel.setText(msg);
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        Dialogs.applyStylesheet(a.getDialogPane());
        a.showAndWait();
    }
}
