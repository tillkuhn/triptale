package net.timafe.triptale.ui;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
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
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCombination;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import net.timafe.triptale.attachments.AttachmentSyncer;
import net.timafe.triptale.attachments.AttachmentsDir;
import net.timafe.triptale.radio.RadioLibrary;
import net.timafe.triptale.attachments.S3Client;
import net.timafe.triptale.impressions.ImpressionSource;
import net.timafe.triptale.impressions.ImpressionsService;
import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.BucketUrl;
import net.timafe.triptale.config.TripTaleProperties;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.domain.TripRef;
import net.timafe.triptale.export.DiaryExporter;
import net.timafe.triptale.export.ExportTempFiles;
import net.timafe.triptale.git.GitService;
import net.timafe.triptale.storage.ExifReader;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.storage.SettingsStore;
import net.timafe.triptale.ui.dialog.AboutDialog;
import net.timafe.triptale.ui.dialog.CoordinatesDialog;
import net.timafe.triptale.ui.dialog.EditSettingsDialog;
import net.timafe.triptale.ui.dialog.ExportDiaryDialog;
import net.timafe.triptale.ui.dialog.ImageViewerDialog;
import net.timafe.triptale.ui.dialog.NewTripDialog;
import net.timafe.triptale.ui.dialog.RemoteInfoDialog;
import net.timafe.triptale.ui.dialog.SmartSyncDialog;
import net.timafe.triptale.ui.dialog.TripDetailsDialog;
import net.timafe.triptale.ui.dialog.TripMapDialog;
import net.timafe.triptale.ui.dialog.ViewSourceDialog;
import net.timafe.triptale.util.CommitMessage;
import net.timafe.triptale.util.Coordinates;
import net.timafe.triptale.util.GpxImport;
import net.timafe.triptale.util.Greetings;
import net.timafe.triptale.util.Greetings.Greeting;
import net.timafe.triptale.util.Markdown;
import net.timafe.triptale.util.RelativeTime;
import net.timafe.triptale.util.SaveTarget;
import net.timafe.triptale.util.Slugs;
import net.timafe.triptale.util.TextStats;
import net.timafe.triptale.util.TravelWisdoms;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

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
    @FXML private Button tripMapButton;
    @FXML private Button impressionsButton;
    @FXML private TextArea talesArea;
    @FXML private VBox taleEmptyOverlay;
    @FXML private Hyperlink createTaleLink;
    @FXML private Label talesLabel;
    @FXML private Label statusLabel;
    @FXML private Label statusIcon;
    @FXML private Label versionLabel;
    @FXML private HBox statusRow;
    @FXML private Label tourDayLabel;
    @FXML private Label titleLabel;
    @FXML private Button exportTaleButton;
    @FXML private Button saveButton;
    @FXML private Button syncButton;
    @FXML private Button prevDayButton;
    @FXML private Button nextDayButton;
    @FXML private Button firstDayButton;
    @FXML private Button todayButton;
    @FXML private Button lastDayButton;
    @FXML private Button connectivityButton;
    @FXML private Button radioButton;
    @FXML private MenuItem pushMenuItem;
    @FXML private MenuItem pullMenuItem;
    @FXML private MenuItem pushAttachmentsMenuItem;
    @FXML private MenuItem pullAttachmentsMenuItem;
    @FXML private MenuItem addAttachmentsMenuItem;
    @FXML private MenuItem syncMenuItem;
    @FXML private MenuItem commitMenuItem;
    @FXML private MenuItem viewSourceMenuItem;
    @FXML private MenuItem importGpxMenuItem;
    @FXML private MenuItem firstDayMenuItem;
    @FXML private MenuItem prevDayMenuItem;
    @FXML private MenuItem nextDayMenuItem;
    @FXML private MenuItem todayMenuItem;
    @FXML private MenuItem impressionsMenuItem;
    @FXML private MenuItem exportTaleMenuItem;
    @FXML private MenuItem saveMenuItem;
    @FXML private MenuItem copyMenuItem;
    @FXML private MenuItem deleteEntryMenuItem;
    @FXML private Menu appMenu;
    @FXML private Menu radioMenu;
    @FXML private MenuItem aboutMenuItem;
    @FXML private MenuItem quitMenuItem;

    private static final String CREATE = "Create";
    private static final String UPDATE = "Update";
    private static final String DELETE = "Delete";
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
    private Double baselineStopLat;
    private Double baselineStopLon;
    private String baselineTales = "";
    private Double startLat;
    private Double startLon;
    private Double stopLat;
    private Double stopLon;
    private boolean entryExists;
    /** True once the user has clicked through the "here be dragons" placeholder for a new entry. */
    private boolean taleUnlocked;
    private Instant talesUpdatedAt;
    private int taleFontSizePx = TALE_FONT_SIZE_DEFAULT_PX;

    /** Guard flag to prevent listener re-entrancy when reverting a navigation on Cancel. */
    private boolean navigating = false;

    /** Tri-state: null = checking/unknown, true = online, false = offline */
    private Boolean connected = null;

    private final Map<String, String> pending = new LinkedHashMap<>();

    /** Last Smart Sync hit a rebase conflict; kept as a status bar marker until a sync succeeds (todo 32, D4). */
    private boolean syncConflict;

    private final MarkdownStore store;
    private final GitService gitService;
    private final SettingsStore settingsStore;
    private final ImpressionsService impressions;
    private final ConnectivityService connectivityService;
    private final ExportTempFiles exportTempFiles;
    private final BrowserLauncher browser;
    private final String appName;
    private final AttachmentsDir attachmentsDir;
    private boolean transferringAttachments;
    private final RadioLibrary radioLibrary;
    /** Read once at startup from settings; toggling the setting needs a restart (todo 46). */
    private boolean radioEnabled;
    /** Created on first use, so javafx.media stays unloaded until the radio is touched (todo 46). */
    private RadioPlayer radio;
    private EqualizerIcon equalizerIcon;
    private final RecentFolder recentFolder = new RecentFolder();

    // Window title: greeting + travel quote, re-rolled on trip switch (todo 45).
    private final Greetings greetings = Greetings.load();
    private final TravelWisdoms wisdoms = TravelWisdoms.load();
    private final ReadOnlyStringWrapper windowTitle = new ReadOnlyStringWrapper();
    private Greeting greeting;
    private String wisdom;
    private final String version;

    // Dialogs whose own dependencies this controller has no other use for.
    private final ExportDiaryDialog exportDiaryDialog;
    private final ImageViewerDialog imageViewerDialog;
    private final AboutDialog aboutDialog;
    private final TripMapDialog tripMapDialog;

    public MainController(MarkdownStore store, GitService gitService, SettingsStore settingsStore,
                          DiaryExporter diaryExporter, ImpressionsService impressions,
                          ExifReader exifReader,
                          ConnectivityService connectivityService,
                          ExportTempFiles exportTempFiles,
                          AttachmentsDir attachmentsDir,
                          RadioLibrary radioLibrary,
                          TripTaleProperties tripTaleProperties,
                          ObjectProvider<BuildProperties> buildPropertiesProvider,
                          ObjectProvider<HostServices> hostServicesProvider) {
        this.store = store;
        this.gitService = gitService;
        this.settingsStore = settingsStore;
        this.impressions = impressions;
        this.connectivityService = connectivityService;
        this.exportTempFiles = exportTempFiles;
        this.attachmentsDir = attachmentsDir;
        this.radioLibrary = radioLibrary;
        this.appName = tripTaleProperties.getAppName();
        this.browser = new BrowserLauncher(hostServicesProvider.getIfAvailable());
        this.exportDiaryDialog =
                new ExportDiaryDialog(diaryExporter, store, impressions, exportTempFiles, browser, this);
        this.imageViewerDialog = new ImageViewerDialog(impressions, exifReader, browser, this, recentFolder);
        BuildProperties buildProperties = buildPropertiesProvider.getIfAvailable();
        this.version = buildProperties != null ? buildProperties.getVersion() : "dev";
        this.aboutDialog = new AboutDialog(appName, buildProperties, browser);
        this.tripMapDialog = new TripMapDialog(new MapboxService(settingsStore));
    }

    private static final DateTimeFormatter DATE_DISPLAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE", Locale.ENGLISH);

    @FXML
    public void initialize() {
        appMenu.setText(appName);
        aboutMenuItem.setText("ⓘ About " + appName);
        quitMenuItem.setText("⏻ Quit " + appName);
        versionLabel.setText(version);
        rollWindowTitle();
        loadTaleFontSize();
        applyTaleFontSize();
        statusLabel.maxWidthProperty().bind(statusRow.widthProperty().multiply(0.5));
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
                if (!empty && item != null && trip != null && trip.endDate() != null
                        && item.isAfter(trip.endDate())) {
                    setDisable(true);
                    setStyle("-fx-background-color: #f0f0f0;");
                }
            }
        });
        exportTempFiles.sweep();
        radioEnabled = settingsStore.load().isRadioEnabled();
        radioMenu.setVisible(radioEnabled);
        radioButton.setVisible(radioEnabled);
        radioButton.setManaged(radioEnabled);
        boolean ready = performStartupChecks();
        radioButton.hoverProperty().addListener((obs, was, is) -> updateRadioButton());
        if (ready) {
            syncAttachmentsGitFiles();
            if (radioEnabled) syncRadioGitFiles();
        }
        yearCombo.valueProperty().addListener((obs, old, sel) -> {
            if (navigating || sel == null) return;
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
            if (old != null) rollWindowTitle();
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
            if (sel != null && trip != null && trip.endDate() != null
                    && sel.isAfter(trip.endDate())) {
                datePicker.setValue(trip.endDate());
                status("Snapped to last day (" + trip.endDate() + ")");
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
        // Keyboard shortcuts are menu item accelerators in main.fxml; they only fire while the item is enabled.
        installShortcutTooltip(prevDayButton, "Previous day", prevDayMenuItem);
        installShortcutTooltip(nextDayButton, "Next day", nextDayMenuItem);
        installShortcutTooltip(saveButton, "Save tale", saveMenuItem);
        installShortcutTooltip(syncButton, "Smart Sync", syncMenuItem);
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

    /**
     * Refreshes years and the current year's trips after external changes (pull/sync), preserving
     * selection. Reselection is silent (navigating), so the combo listeners don't treat the reloaded
     * Trip instance as a newly picked trip and jump to its last entry; callers reload the entry.
     */
    private void reloadAll() {
        Trip previouslySelected = tripCombo.getValue();
        Trip toReselect = null;
        navigating = true;
        try {
            reloadYears();
            Integer year = yearCombo.getValue();
            if (year != null) reloadTrips(year);
            if (previouslySelected != null) {
                toReselect = findTrip(previouslySelected.slug());
                if (toReselect != null) tripCombo.getSelectionModel().select(toReselect);
            }
        } finally {
            navigating = false;
        }
        // The trip vanished (e.g. deleted remotely): fall back to a regular selection.
        if (previouslySelected != null && toReselect == null && !tripCombo.getItems().isEmpty()) {
            tripCombo.getSelectionModel().select(0);
        }
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
        store.saveTrip(new Trip(year, slug, name, start, null, spec.get().description()));
        addPending(ref.path(), CREATE);
        spec.get().firstEntry().ifPresent(fe -> {
            DiaryEntry entry = DiaryEntry.builder(start)
                    .title(fe.title())
                    .startLat(fe.lat())
                    .startLon(fe.lon())
                    .stopLat(fe.stopLat())
                    .stopLon(fe.stopLon())
                    .distance(fe.distanceKm())
                    .altitudeMeters(fe.altitudeGainM())
                    .build();
            store.saveEntry(ref, entry);
            addPending(ref.path() + "/" + start, CREATE);
            if (fe.gpxFile() != null) {
                attachmentsDir.addFiles(ref, start, List.of(fe.gpxFile().toPath()));
            }
        });
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

    /** Exports just the current entry, saving pending edits first so the export sees them. */
    @FXML
    public void onExportTale() {
        Trip trip = tripCombo.getValue();
        LocalDate date = datePicker.getValue();
        if (trip == null || date == null || !saveIfDirty() || !entryExists) return;
        exportDiaryDialog.show(trip, date, date);
    }

    @FXML
    public void onViewSource() {
        Trip trip = tripCombo.getValue();
        LocalDate date = datePicker.getValue();
        if (trip == null || date == null) { error("No entry selected"); return; }
        new ViewSourceDialog(store, this).show(trip.ref(), date);
    }

    @FXML
    public void onImportGpx() {
        Trip trip = tripCombo.getValue();
        if (trip == null) { error("No trip selected"); return; }
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("GPX files", "*.gpx"));
        File file = chooser.showOpenDialog(null);
        if (file == null) return;
        Optional<GpxImport.Parsed> parsed = GpxImport.parse(file);
        if (parsed.isEmpty()) {
            error("Couldn't parse a track name/point from this file.");
            return;
        }
        GpxImport.Parsed p = parsed.get();
        if (trip.startDate() != null && p.date().isBefore(trip.startDate())
                || trip.endDate() != null && p.date().isAfter(trip.endDate())) {
            error("GPX date " + p.date() + " is outside " + trip.ref().path() + "'s date range");
            return;
        }
        if (store.entryExists(trip.ref(), p.date())) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Entry for " + p.date() + " already exists. Overwrite title and geo track data from GPX?",
                    ButtonType.YES, ButtonType.NO);
            confirm.setTitle(appName);
            confirm.setHeaderText("Overwrite entry");
            Dialogs.applyStylesheet(confirm.getDialogPane());
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.YES) return;
        }
        Path attachmentCopy;
        try {
            attachmentCopy = attachmentsDir.addFiles(trip.ref(), p.date(), List.of(file.toPath())).getFirst();
        } catch (RuntimeException e) {
            error(UiText.describe(e));
            return;
        }
        datePicker.setValue(p.date());
        titleField.setText(p.name());
        startLat = p.lat();
        startLon = p.lon();
        stopLat = p.stopLat();
        stopLon = p.stopLon();
        distanceField.setText(String.valueOf(p.distanceKm()));
        if (p.altitudeGainM() != null) {
            altField.setText(String.valueOf(p.altitudeGainM()));
        }
        updateCoordinatesButton();
        updateDirty();
        status("Imported \"" + p.name() + "\" for " + p.date() + ", GPX saved to "
                + UiText.homeRelative(attachmentCopy));
    }

    @FXML
    public void onFirstDay() {
        Trip trip = tripCombo.getValue();
        if (trip == null || trip.startDate() == null) return;
        datePicker.setValue(trip.startDate());
        status("Snapped to day 1 (" + trip.startDate() + ")");
    }

    @FXML
    public void onLastDay() {
        Trip trip = tripCombo.getValue();
        if (trip == null || trip.endDate() == null) return;
        datePicker.setValue(trip.endDate());
        status("Snapped to last day (" + trip.endDate() + ")");
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
        zoomTaleFont(TALE_FONT_SIZE_STEP_PX);
    }

    @FXML
    public void onZoomOut() {
        zoomTaleFont(-TALE_FONT_SIZE_STEP_PX);
    }

    /** Changes the tale text size and reports the result, so a no-op at the min/max limit is still visible. */
    private void zoomTaleFont(int deltaPx) {
        setTaleFontSize(taleFontSizePx + deltaPx);
        String note;
        if (taleFontSizePx == TALE_FONT_SIZE_MAX_PX) note = "maximum";
        else if (taleFontSizePx == TALE_FONT_SIZE_MIN_PX) note = "minimum";
        else if (taleFontSizePx == TALE_FONT_SIZE_DEFAULT_PX) note = "default";
        else note = "default " + TALE_FONT_SIZE_DEFAULT_PX + "px";
        status("Tale text size: " + taleFontSizePx + "px (" + note + ")");
    }

    /** Advertises {@code menuItem}'s accelerator on {@code button}, so each shortcut is defined only once (in main.fxml). */
    private static void installShortcutTooltip(Button button, String text, MenuItem menuItem) {
        KeyCombination accelerator = menuItem.getAccelerator();
        String suffix = accelerator == null ? "" : " (" + accelerator.getDisplayText() + ")";
        button.setTooltip(new Tooltip(text + suffix));
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
        if (importGpxMenuItem != null) importGpxMenuItem.setDisable(trip == null || date == null);
        if (addAttachmentsMenuItem != null) addAttachmentsMenuItem.setDisable(trip == null || date == null);
        if (trip == null || date == null) {
            updateViewSourceMenuItem(false);
            updateDeleteEntryMenuItem(false);
            return;
        }
        entryExists = store.entryExists(trip.ref(), date);
        taleUnlocked = entryExists;
        updateViewSourceMenuItem(entryExists);
        updateDeleteEntryMenuItem(entryExists);
        DiaryEntry e = store.loadEntry(trip.ref(), date);
        distanceField.setText(e.distance() == null ? "" : e.distance().toString());
        altField.setText(e.altitudeMeters() == null ? "" : e.altitudeMeters().toString());
        titleField.setText(!entryExists ? "" : (e.title() == null ? DiaryEntry.DEFAULT_TITLE : e.title()));
        trackUrlField.setText(e.trackUrl() == null ? "" : e.trackUrl());
        startLat = e.startLat();
        startLon = e.startLon();
        stopLat = e.stopLat();
        stopLon = e.stopLon();
        updateCoordinatesButton();
        talesArea.setText(e.tales() == null ? "" : e.tales());
        talesUpdatedAt = readTalesLastModified(trip, date);
        updateTalesLabel();
        updateEmptyState();
        updateImpressionsButton(trip, date);
        snapshotBaseline();
        updateDirty();
    }

    /**
     * Toggles the "here be dragons" placeholder for a date with no entry file yet, so it reads as
     * genuinely absent rather than as an existing-but-incomplete entry.
     */
    private void updateEmptyState() {
        boolean showEmpty = !entryExists && !taleUnlocked;
        if (taleEmptyOverlay != null) {
            taleEmptyOverlay.setVisible(showEmpty);
            taleEmptyOverlay.setManaged(showEmpty);
        }
        talesArea.setDisable(showEmpty);
        titleField.setDisable(showEmpty);
        distanceField.setDisable(showEmpty);
        altField.setDisable(showEmpty);
        trackUrlField.setDisable(showEmpty);
        if (showEmpty) {
            if (!talesArea.getStyleClass().contains("tale-text-empty")) {
                talesArea.getStyleClass().add("tale-text-empty");
            }
        } else {
            talesArea.getStyleClass().remove("tale-text-empty");
        }
    }

    @FXML
    public void onCreateTaleLink() {
        taleUnlocked = true;
        updateEmptyState();
        updateTalesLabel();
        LocalDate date = datePicker.getValue();
        if (date != null && titleField.getText().isBlank()) {
            titleField.setText(date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        }
        titleField.requestFocus();
        titleField.selectAll();
    }

    /**
     * Counts the day's images in the photo library and the attachments (base filter only, no
     * fave filter). The viewer opens even when both are empty — it can also pick a folder.
     */
    private void updateImpressionsButton(Trip trip, LocalDate date) {
        if (impressionsButton == null) return;
        boolean noDay = trip == null || date == null;
        int photoLib = noDay ? 0 : impressions.images(ImpressionSource.PHOTO_LIBRARY, trip, date).size();
        int attached = noDay ? 0 : impressions.images(ImpressionSource.TRIP_ATTACHMENTS, trip, date).size();
        impressionsButton.setText(photoLib == 0 && attached == 0
                ? "📷 Impressions: none – pick folder ›"
                : "📷 Impressions " + photoLib + " 🗂 / " + attached + " 🎒 ›");
        impressionsButton.setDisable(noDay);
        if (impressionsMenuItem != null) impressionsMenuItem.setDisable(noDay);
    }

    private void updateCoordinatesButton() {
        boolean set = startLat != null && startLon != null;
        boolean stopSet = set && stopLat != null && stopLon != null;
        if (coordinatesButton != null) {
            coordinatesButton.setText(set
                    ? "📍 " + Coordinates.toDdm(startLat, startLon) + (stopSet ? " ⇢ 🏁" : "")
                    : "📍 Uncharted");
            coordinatesButton.setTooltip(new Tooltip(stopSet
                    ? "Start " + Coordinates.toDdm(startLat, startLon) + " · End " + Coordinates.toDdm(stopLat, stopLon)
                    : "Set start and end point"));
        }
        if (openCoordinatesButton != null) {
            openCoordinatesButton.setDisable(!set);
            openCoordinatesButton.setTooltip(new Tooltip(stopSet
                    ? "Show route start → end on Google Maps"
                    : "Show start point on Google Maps"));
        }
    }

    @FXML
    public void onOpenCoordinates() {
        if (startLat == null || startLon == null) return;
        Coordinates.LatLon stop = stopLat == null || stopLon == null ? null : new Coordinates.LatLon(stopLat, stopLon);
        browser.open(Coordinates.googleMapsUrl(new Coordinates.LatLon(startLat, startLon), stop));
    }

    @FXML
    public void onShowTripMap() {
        Trip trip = tripCombo.getValue();
        if (trip == null) {
            status("Select a trip first");
            return;
        }
        List<LocalDate> dates = store.listEntryDates(trip.ref());
        List<double[]> startPoints = new ArrayList<>();
        double[] endPoint = null;
        for (int i = 0; i < dates.size(); i++) {
            DiaryEntry entry = store.loadEntry(trip.ref(), dates.get(i));
            if (entry.startLat() != null && entry.startLon() != null) {
                startPoints.add(new double[]{entry.startLon(), entry.startLat()});
            }
            // Only the latest day's stop point — a multi-day trip's daily stop is usually the
            // next day's start (redundant); a one-way trip's final destination otherwise never
            // appears on the map at all.
            if (i == dates.size() - 1 && entry.stopLat() != null && entry.stopLon() != null) {
                endPoint = new double[]{entry.stopLon(), entry.stopLat()};
            }
        }
        tripMapDialog.show(trip.name(), startPoints, endPoint);
    }

    private void updateViewSourceMenuItem(boolean exists) {
        if (viewSourceMenuItem == null) return;
        viewSourceMenuItem.setDisable(!exists);
    }

    private void updateDeleteEntryMenuItem(boolean exists) {
        if (deleteEntryMenuItem == null) return;
        deleteEntryMenuItem.setDisable(!exists);
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
        if (!entryExists && !taleUnlocked) {
            talesLabel.setText("🐉 Tales");
            return;
        }
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
        baselineStopLat = stopLat;
        baselineStopLon = stopLon;
        baselineTales = talesArea.getText();
    }

    private boolean isDirty() {
        return !Objects.equals(distanceField.getText(), baselineDistance)
                || !Objects.equals(altField.getText(), baselineAlt)
                || !Objects.equals(titleField.getText(), baselineTitle)
                || !Objects.equals(trackUrlField.getText(), baselineTrackUrl)
                || !Objects.equals(startLat, baselineStartLat)
                || !Objects.equals(startLon, baselineStartLon)
                || !Objects.equals(stopLat, baselineStopLat)
                || !Objects.equals(stopLon, baselineStopLon)
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
        String saveLabel = "💾 Save Tale";
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
        // Export Tale saves pending edits first, so a savable new entry counts as exportable.
        boolean exportable = tripCombo.getValue() != null && datePicker.getValue() != null
                && (entryExists || saveEnabled);
        if (exportTaleButton != null) exportTaleButton.setDisable(!exportable);
        if (exportTaleMenuItem != null) exportTaleMenuItem.setDisable(!exportable);
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

    /** Menu-only power-user Commit; saves unsaved edits first (todo 32, D3). */
    private void updateCommitButton() {
        if (commitMenuItem != null) {
            commitMenuItem.setText("📦 Commit (" + pending.size() + ")");
            commitMenuItem.setDisable(pending.isEmpty() && !isDirty());
        }
    }

    /**
     * Saves the form first if it has unsaved edits (todo 32, D3), through the normal save path
     * with its validation alerts. Returns false if there were edits and they didn't get saved.
     */
    private boolean saveIfDirty() {
        if (!isDirty()) return true;
        Trip trip = tripCombo.getValue();
        LocalDate date = datePicker.getValue();
        if (trip == null || date == null || !isValidEntry()) {
            error("The current entry has unsaved edits that can't be saved yet "
                    + "(a tale needs a title and some text). Complete or discard them first.");
            return false;
        }
        try {
            doSave(trip.ref(), date);
        } catch (RuntimeException e) {
            error(UiText.describe(e));
            return false;
        }
        return !isDirty();
    }

    private String buildCommitMessage() {
        int total = pending.size();
        if (total <= COMMIT_MSG_INLINE_LIMIT) {
            List<String> creates = new ArrayList<>();
            List<String> updates = new ArrayList<>();
            List<String> deletes = new ArrayList<>();
            pending.forEach((label, action) -> {
                if (CREATE.equals(action)) creates.add(label);
                else if (DELETE.equals(action)) deletes.add(label);
                else updates.add(label);
            });
            StringBuilder sb = new StringBuilder();
            if (!creates.isEmpty()) sb.append(CREATE).append(": ").append(String.join(", ", creates));
            if (!updates.isEmpty()) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append(UPDATE).append(": ").append(String.join(", ", updates));
            }
            if (!deletes.isEmpty()) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append(DELETE).append(": ").append(String.join(", ", deletes));
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
        boolean nextDisabled;
        if (trip == null || trip.endDate() == null || date == null) {
            nextDisabled = false;
        } else {
            nextDisabled = !date.isBefore(trip.endDate());
        }
        if (nextDayButton != null) nextDayButton.setDisable(nextDisabled);
        if (lastDayButton != null) lastDayButton.setDisable(trip == null || trip.endDate() == null);
    }

    private void updateTourDay(Trip trip, LocalDate date) {
        if (tourDayLabel == null) return;
        Long day = trip == null ? null : trip.dayNumber(date);
        if (day == null) {
            tourDayLabel.setText("");
            tourDayLabel.setTooltip(null);
            if (titleLabel != null) titleLabel.setText("Title:");
            return;
        }
        Long total = trip.totalDays();
        tourDayLabel.setText("Day " + day + " / " + (total == null ? "∞" : total)
                + " (" + relativeDayLabel(trip, date, day) + ")");
        tourDayLabel.setTooltip(new Tooltip(total == null
                ? "Day " + day + " — no end date set (edit in Trip Details)"
                : "Day " + day + " of " + total));
        if (titleLabel != null) titleLabel.setText("Title Day " + day + ":");
    }

    private static String relativeDayLabel(Trip trip, LocalDate date, long tourDay) {
        if (trip.endDate() != null && date.equals(trip.endDate())) return "last day";
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
                .stopLat(stopLat)
                .stopLon(stopLon)
                .tales(talesArea.getText())
                .build();
        boolean wasNew = !entryExists;
        store.saveEntry(trip, entry);
        entryExists = true;
        taleUnlocked = true;
        updateViewSourceMenuItem(true);
        talesUpdatedAt = Instant.now();
        updateTalesLabel();
        updateEmptyState();
        addPending(trip.path() + "/" + date, wasNew ? CREATE : UPDATE);
        talesArea.setText(Markdown.demoteH1(talesArea.getText()));
        snapshotBaseline();
        updateDirty();
        status("Saved " + trip.path() + "/" + date);
    }

    @FXML
    public void onDeleteTaleEntry() {
        Trip trip = tripCombo.getValue();
        LocalDate date = datePicker.getValue();
        if (trip == null || date == null || !entryExists) return;

        String title = titleField.getText();
        String titleLine = title == null || title.isBlank() || title.equals(DiaryEntry.DEFAULT_TITLE)
                ? ""
                : "\n\"" + title + "\"";
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete the Tale Entry for " + date + "?" + titleLine
                        + "\n\nThis cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle(appName);
        confirm.setHeaderText("Delete Tale Entry");
        Dialogs.applyStylesheet(confirm.getDialogPane());
        confirm.getDialogPane().setMinWidth(420);
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.YES) return;

        TripRef ref = trip.ref();
        Path file = store.entryFile(ref, date);
        String label = ref.path() + "/" + date;
        store.deleteEntry(ref, date);
        if (CREATE.equals(pending.remove(label))) {
            // Never committed — git never tracked it, nothing to unstage.
        } else {
            gitService.remove(file);
            pending.put(label, DELETE);
        }
        updateCommitButton();

        loadEntry();
        status("Deleted entry " + label);
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
        if (!saveIfDirty()) return;
        String sha;
        int n;
        try {
            List<String> dirty = gitService.dirtyFiles();
            n = dirty.size();
            String pendingMessage = pending.isEmpty() ? "" : buildCommitMessage();
            sha = gitService.commitAll(CommitMessage.compose(pendingMessage,
                    CommitMessage.external(pending.keySet(), dirty)));
        } catch (RuntimeException e) {
            error(UiText.describe(e));
            return;
        }
        pending.clear();
        updateCommitButton();
        status(sha == null ? "Nothing to commit"
                : "Committed " + n + " file" + (n == 1 ? "" : "s") + " (" + sha + ")");
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
        // Smart Sync stays enabled offline: it still commits locally (todo 32, D8).
        boolean cloud = settingsStore.load().getAttachments().getSync() == AppSettings.AttachmentSync.CLOUD;
        boolean attachmentsDisabled = !Boolean.TRUE.equals(connected) || !cloud || transferringAttachments;
        if (pushAttachmentsMenuItem != null) pushAttachmentsMenuItem.setDisable(attachmentsDisabled);
        if (pullAttachmentsMenuItem != null) pullAttachmentsMenuItem.setDisable(attachmentsDisabled);
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
    public void onPushAttachments() {
        transferAttachments("Push", "push-attachments", (syncer, root, prefix) -> {
            AttachmentSyncer.Result r = syncer.push(root, prefix, (done, total, file) ->
                    Platform.runLater(() -> status("Pushing backpack " + (done + 1) + "/" + total + ": " + file)));
            return "Backpack pushed: " + r.uploaded() + " uploaded, " + r.unchanged() + " unchanged";
        });
    }

    @FXML
    public void onPullAttachments() {
        transferAttachments("Pull", "pull-attachments", (syncer, root, prefix) -> {
            int n = syncer.pull(root, prefix, (done, total, file) ->
                    Platform.runLater(() -> status("Pulling backpack " + (done + 1) + "/" + total + ": " + file)));
            return "Backpack pulled: " + n + " downloaded";
        });
    }

    /** One attachment transfer on a worker thread; returns the status message on success. */
    @FunctionalInterface
    private interface AttachmentTransfer {
        String run(AttachmentSyncer syncer, Path root, String keyPrefix);
    }

    private void transferAttachments(String verb, String threadName, AttachmentTransfer transfer) {
        AppSettings.Attachments settings = settingsStore.load().getAttachments();
        Path root;
        AttachmentSyncer syncer;
        String keyPrefix;
        try {
            root = attachmentsDir.root();
            syncer = new AttachmentSyncer(S3Client.from(settings));
            keyPrefix = AttachmentSyncer.keyPrefix(BucketUrl.parse(settings.getBucketUrl()));
        } catch (RuntimeException e) {
            error(UiText.describe(e));
            return;
        }
        Task<String> task = new Task<>() {
            @Override protected String call() {
                return transfer.run(syncer, root, keyPrefix);
            }
        };
        task.setOnSucceeded(e -> {
            setTransferringAttachments(false);
            status(task.getValue());
        });
        task.setOnFailed(e -> {
            setTransferringAttachments(false);
            error(verb + " Backpack failed: " + UiText.describe(task.getException()));
        });
        setTransferringAttachments(true);
        status(verb + "ing backpack…");
        Thread thread = new Thread(task, threadName);
        thread.setDaemon(true);
        thread.start();
    }

    private void setTransferringAttachments(boolean running) {
        transferringAttachments = running;
        applyConnectivityState();
    }

    @FXML
    public void onAddAttachments() {
        Trip trip = tripCombo.getValue();
        LocalDate date = datePicker.getValue();
        if (trip == null || date == null) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Add to Backpack for " + DATE_DISPLAY.format(date));
        chooser.setInitialDirectory(recentFolder.initial());
        List<File> files = chooser.showOpenMultipleDialog(null);
        if (files == null || files.isEmpty()) return;
        recentFolder.remember(files.getFirst().getParentFile());
        try {
            List<Path> copies = attachmentsDir.addFiles(trip.ref(), date, files.stream().map(File::toPath).toList());
            status("Added " + copies.size() + " file" + (copies.size() == 1 ? "" : "s") + " to "
                    + UiText.homeRelative(copies.getFirst().getParent()));
            updateImpressionsButton(trip, date);
        } catch (RuntimeException e) {
            error(UiText.describe(e));
        }
    }

    /**
     * Rewrites attachments/.gitignore (for the current sync mode) and .gitattributes; each changed
     * file becomes a pending commit.
     */
    private void syncAttachmentsGitFiles() {
        try {
            attachmentsDir.ensureManagedFiles(settingsStore.load().getAttachments().getSync())
                    .forEach(label -> addPending(label, UPDATE));
        } catch (RuntimeException e) {
            log.warn("Could not update {}/ git files: {}", AttachmentsDir.DIR_NAME, e.getMessage());
        }
    }

    private void syncRadioGitFiles() {
        try {
            if (radioLibrary.ensureManagedFiles()) addPending(RadioLibrary.GITIGNORE_LABEL, UPDATE);
        } catch (RuntimeException e) {
            log.warn("Could not update {}/ git files: {}", RadioLibrary.DIR_NAME, e.getMessage());
        }
    }

    // ── Radio (todo 46) ──────────────────────────────────────

    private RadioPlayer radio() {
        if (radio == null) radio = new RadioPlayer(radioLibrary, this, this::updateRadioButton);
        return radio;
    }

    @FXML
    public void onRadioToggle() {
        if (!radioEnabled) return;
        radio().togglePause();
    }

    @FXML
    public void onRadioRandom() {
        if (!radioEnabled) return;
        radio().playRandom();
    }

    @FXML
    public void onRadioStop() {
        if (!radioEnabled) return;
        if (radio != null) radio.stop();
        status("🎵 Radio stopped");
    }

    @FXML
    public void onOpenRadioFolder() {
        if (!radioEnabled) return;
        radioLibrary.ensureManagedFiles();
        browser.open(radioLibrary.root().toUri().toString());
    }

    private void updateRadioButton() {
        boolean playing = radio != null && radio.isPlaying();
        if (playing) {
            if (equalizerIcon == null) equalizerIcon = new EqualizerIcon();
            equalizerIcon.start();
        } else if (equalizerIcon != null) {
            equalizerIcon.stop();
        }
        // While playing, the bars bounce; hovering reveals what a click does (pause).
        if (playing && !radioButton.isHover()) {
            radioButton.setText("");
            radioButton.setGraphic(equalizerIcon);
        } else {
            radioButton.setGraphic(null);
            radioButton.setText(playing ? "⏸" : radio != null && radio.isLoaded() ? "▶" : "🎵");
        }
        if (radio == null || !radio.isLoaded()) {
            radioButton.setTooltip(new Tooltip("Radio — play a random track from radio/"));
        } else if (playing) {
            radioButton.setTooltip(new Tooltip("Now playing: " + radio.label() + " — click to pause"));
        } else {
            radioButton.setTooltip(new Tooltip("Paused: " + radio.label() + " — click to resume"));
        }
    }

    @FXML
    public void onSync() {
        if (!saveIfDirty()) return;
        var request = new SmartSyncDialog.Request(
                pending.isEmpty() ? "" : buildCommitMessage(), List.copyOf(pending.keySet()));
        new SmartSyncDialog(gitService, settingsStore, attachmentsDir, connectivityService, browser)
                .show(request, this::onSyncFinished);
    }

    private void onSyncFinished(SmartSyncDialog.Outcome outcome) {
        if (outcome.commitSettled()) {
            pending.clear();
            updateCommitButton();
        }
        switch (outcome.remote()) {
            case OK -> {
                setSyncConflict(false);
                reloadAll();
                loadEntry();
            }
            case CONFLICT -> setSyncConflict(true);
            default -> { }
        }
        List<String> parts = new ArrayList<>();
        if (outcome.sha() != null) parts.add("committed " + outcome.sha());
        switch (outcome.remote()) {
            case OK -> parts.add("in sync with origin");
            case CONFLICT -> parts.add("⚠ rebase conflict, unresolved");
            case FAILED -> parts.add("remote sync failed");
            case NOT_RUN -> { }
        }
        status("Smart Sync: " + (parts.isEmpty() ? "done" : String.join(", ", parts)));
    }

    private void setSyncConflict(boolean conflict) {
        syncConflict = conflict;
        if (statusIcon == null) return;
        statusIcon.setText(conflict ? "⚠" : "\u24D8");
        statusIcon.setTooltip(conflict
                ? new Tooltip("Last Smart Sync hit a rebase conflict. Resolve it manually, then sync again.")
                : null);
        statusIcon.getStyleClass().remove("sync-conflict");
        if (conflict) statusIcon.getStyleClass().add("sync-conflict");
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
        int unpushed = unpushedCommits();
        if (unpushed > 0) {
            body.append("\nand ").append(unpushed).append(" commit").append(unpushed == 1 ? "" : "s")
                    .append(" not yet pushed");
        }
        if (syncConflict) body.append("\n\nThe last Smart Sync hit an unresolved rebase conflict");
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
        // Exit stays cheap and local (todo 32, D6): no implicit save here, so no commit while dirty.
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

    /** Local commits not on origin, from the last fetch — no network (todo 32, D6). */
    private int unpushedCommits() {
        try {
            return gitService.aheadBehind().map(GitService.AheadBehind::ahead).orElse(0);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Bound to the stage title by {@code TripTaleApplication}. */
    public ReadOnlyStringProperty windowTitleProperty() {
        return windowTitle.getReadOnlyProperty();
    }

    /** Picks a new greeting and quote, never the same one twice in a row. */
    private void rollWindowTitle() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        greeting = greetings.random(rnd, greeting).orElse(null);
        wisdom = wisdoms.random(rnd, wisdom).orElse(null);
        String title = "🐉 " + (greeting != null ? greeting.text(appName) : appName);
        if (wisdom != null) title += " · " + wisdom;
        windowTitle.set(title);
    }

    @FXML
    public void onAbout() {
        aboutDialog.show(greeting);
    }

    @FXML
    public void onOpenTrackUrl() {
        String url = trackUrlField.getText();
        if (UiText.isValidHttpUrl(url)) browser.open(url.trim());
    }

    @FXML
    public void onCoordinates() {
        Optional<CoordinatesDialog.Result> result =
                new CoordinatesDialog().showAndWait(startLat, startLon, stopLat, stopLon);
        if (result.isEmpty()) return;
        Coordinates.LatLon start = result.get().start();
        Coordinates.LatLon stop = result.get().stop();
        startLat = start == null ? null : start.lat();
        startLon = start == null ? null : start.lon();
        stopLat = stop == null ? null : stop.lat();
        stopLon = stop == null ? null : stop.lon();
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
        syncAttachmentsGitFiles();
        applyConnectivityState();

        updateImpressionsButton(tripCombo.getValue(), datePicker.getValue());

        if (!edited.get().getDataDir().equals(previousDataDir)) {
            status("Settings saved — restart " + appName + " for the new data directory to take effect");
        } else {
            status("Settings saved");
        }
    }

    @FXML
    public void onShowImpressions() {
        Trip trip = tripCombo.getValue();
        LocalDate date = datePicker.getValue();
        if (trip == null || date == null) return;
        imageViewerDialog.show(trip, date, () -> updateImpressionsButton(trip, date));
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
