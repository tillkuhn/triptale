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
import net.timafe.triptale.sync.BucketSyncer;
import net.timafe.triptale.attachments.AttachmentsDir;
import net.timafe.triptale.radio.RadioLibrary;
import net.timafe.triptale.radio.Station;
import net.timafe.triptale.sync.S3Client;
import net.timafe.triptale.impressions.ImpressionSource;
import net.timafe.triptale.impressions.ImpressionsService;
import net.timafe.triptale.inbox.InboxDir;
import net.timafe.triptale.inbox.InboxItem;
import net.timafe.triptale.inbox.InboxNotes;
import net.timafe.triptale.inbox.TelegramInboxClient;
import net.timafe.triptale.map.PhotoLocations;
import net.timafe.triptale.map.PlaceLookup;
import net.timafe.triptale.config.AppSettings;
import net.timafe.triptale.config.BucketUrl;
import net.timafe.triptale.config.TripTaleProperties;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Link;
import net.timafe.triptale.domain.LinkKind;
import net.timafe.triptale.domain.Trip;
import net.timafe.triptale.domain.TripRef;
import net.timafe.triptale.domain.Weather;
import net.timafe.triptale.domain.WeatherCode;
import net.timafe.triptale.export.DiaryExporter;
import net.timafe.triptale.export.ExportTempFiles;
import net.timafe.triptale.export.SocialMediaExporter;
import net.timafe.triptale.export.WordPressExporter;
import net.timafe.triptale.git.GitService;
import net.timafe.triptale.storage.ExifReader;
import net.timafe.triptale.storage.MarkdownStore;
import net.timafe.triptale.storage.SettingsStore;
import net.timafe.triptale.ui.dialog.AboutDialog;
import net.timafe.triptale.ui.dialog.CoordinatesDialog;
import net.timafe.triptale.ui.dialog.EditSettingsDialog;
import net.timafe.triptale.ui.dialog.EmojiPickerDialog;
import net.timafe.triptale.ui.dialog.ExportDiaryDialog;
import net.timafe.triptale.ui.dialog.GpxImportDialog;
import net.timafe.triptale.ui.dialog.ImageViewerDialog;
import net.timafe.triptale.ui.dialog.InboxDialog;
import net.timafe.triptale.ui.dialog.LinksDialog;
import net.timafe.triptale.ui.dialog.MapAssistantDialog;
import net.timafe.triptale.ui.dialog.NewTripDialog;
import net.timafe.triptale.ui.dialog.RemoteInfoDialog;
import net.timafe.triptale.ui.dialog.SmartSyncDialog;
import net.timafe.triptale.ui.dialog.SyncTracksDialog;
import net.timafe.triptale.ui.dialog.TripDetailsDialog;
import net.timafe.triptale.ui.dialog.ViewSourceDialog;
import net.timafe.triptale.util.CommitMessage;
import net.timafe.triptale.util.Coordinates;
import net.timafe.triptale.util.Emojis;
import net.timafe.triptale.util.GpxImport;
import net.timafe.triptale.util.Greetings;
import net.timafe.triptale.util.Greetings.Greeting;
import net.timafe.triptale.util.Lengths;
import net.timafe.triptale.util.Markdown;
import net.timafe.triptale.util.RelativeTime;
import net.timafe.triptale.util.SaveTarget;
import net.timafe.triptale.util.Slugs;
import net.timafe.triptale.util.TextStats;
import net.timafe.triptale.util.TravelWisdoms;
import net.timafe.triptale.weather.OpenMeteoClient;
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
import java.util.SequencedMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

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
    @FXML private ComboBox<String> activityCombo;
    @FXML private TextField titleField;
    @FXML private Button linksButton;
    @FXML private Button coordinatesButton;
    @FXML private Button impressionsButton;
    @FXML private TextArea talesArea;
    @FXML private VBox taleEmptyOverlay;
    @FXML private Hyperlink createTaleLink;
    @FXML private Label talesLabel;
    @FXML private Label statusLabel;
    @FXML private Label statusIcon;
    @FXML private Label versionLabel;
    @FXML private HBox statusRow;
    @FXML private HBox statusBar;
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
    @FXML private Button inboxButton;
    @FXML private MenuItem pushMenuItem;
    @FXML private MenuItem pullMenuItem;
    @FXML private MenuItem pushAttachmentsMenuItem;
    @FXML private MenuItem pullAttachmentsMenuItem;
    @FXML private MenuItem syncTracksMenuItem;
    @FXML private MenuItem addAttachmentsMenuItem;
    @FXML private Button backpackButton;
    @FXML private MenuItem syncMenuItem;
    @FXML private MenuItem commitMenuItem;
    @FXML private MenuItem viewSourceMenuItem;
    @FXML private MenuItem importGpxMenuItem;
    @FXML private MenuItem inboxMenuItem;
    @FXML private MenuItem firstDayMenuItem;
    @FXML private MenuItem prevDayMenuItem;
    @FXML private MenuItem nextDayMenuItem;
    @FXML private MenuItem todayMenuItem;
    @FXML private MenuItem impressionsMenuItem;
    @FXML private MenuItem exportTaleMenuItem;
    @FXML private MenuItem saveMenuItem;
    @FXML private MenuItem addPrivateMenuItem;
    @FXML private MenuItem deleteEntryMenuItem;
    @FXML private Menu appMenu;
    @FXML private Menu radioMenu;
    @FXML private MenuItem aboutMenuItem;
    @FXML private MenuItem quitMenuItem;

    private static final String CREATE = "Create";
    private static final String UPDATE = "Update";
    private static final String DELETE = "Delete";
    private static final int COMMIT_MSG_INLINE_LIMIT = 5;

    /** Placeholder body of a section added by 🔒 Add Private Section (todo 48). */
    private static final String PRIVATE_PLACEHOLDER = "Private notes, not exported.";
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
    private String baselineActivity;
    private SequencedMap<String, Link> baselineLinks = new LinkedHashMap<>();
    private Double baselineStartLat;
    private Double baselineStartLon;
    private Double baselineStopLat;
    private Double baselineStopLon;
    private String baselineStartPlace;
    private String baselineStopPlace;
    private Weather baselineWeather;
    private String baselineTales = "";
    /** The form's links; replaced (never mutated) by the Links dialog. */
    private SequencedMap<String, Link> links = baselineLinks;
    private Double startLat;
    private Double startLon;
    private Double stopLat;
    private Double stopLon;
    private String startPlace;
    private String stopPlace;
    /** Fetched automatically (todo 65), never user-edited; carried through saves so a manual
     * save of other fields doesn't wipe it out. */
    private Weather weather;
    private boolean entryExists;
    /** True once the user has clicked through the "here be dragons" placeholder for a new entry. */
    private boolean taleUnlocked;
    private Instant talesUpdatedAt;
    private int taleFontSizePx = TALE_FONT_SIZE_DEFAULT_PX;

    /** Guard flag to prevent listener re-entrancy when reverting a navigation on Cancel. */
    private boolean navigating = false;

    /** Entry date open at the last close; consumed by the first trip selection after startup. */
    private LocalDate restoreEntryDate;

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
    private final InboxDir inboxDir;
    private final TelegramInboxClient telegramInboxClient;
    private final RadioLibrary radioLibrary;
    /** Read once at startup from settings; toggling the setting needs a restart (todo 46). */
    private boolean radioEnabled;
    /** Created on first use, so javafx.media stays unloaded until the radio is touched (todo 46). */
    private RadioPlayer radio;
    private EqualizerIcon equalizerIcon;
    private SpeakerIcon speakerIcon;
    private final RecentFolder recentFolder = new RecentFolder();

    // Window title: greeting + travel quote, re-rolled on trip switch (todo 45).
    private final Greetings greetings = Greetings.load();
    private final TravelWisdoms wisdoms = TravelWisdoms.load();
    private final Emojis emojis = Emojis.load();
    private final ReadOnlyStringWrapper windowTitle = new ReadOnlyStringWrapper();
    private Greeting greeting;
    private String wisdom;
    private final String version;

    // Dialogs whose own dependencies this controller has no other use for.
    private final ExportDiaryDialog exportDiaryDialog;
    private final ImageViewerDialog imageViewerDialog;
    private final AboutDialog aboutDialog;
    /** One instance, so its 1 req/s limit spans all Coordinator dialogs (todo 51). */
    private final PlaceLookup placeLookup;
    /** No shared rate limit needed, unlike {@link #placeLookup} — Open-Meteo has no per-second policy. */
    private final OpenMeteoClient weatherClient;
    private final MapAssistantDialog mapAssistantDialog;

    public MainController(MarkdownStore store, GitService gitService, SettingsStore settingsStore,
                          DiaryExporter diaryExporter, WordPressExporter wordPressExporter,
                          SocialMediaExporter socialMediaExporter,
                          ImpressionsService impressions,
                          ExifReader exifReader,
                          ConnectivityService connectivityService,
                          ExportTempFiles exportTempFiles,
                          AttachmentsDir attachmentsDir,
                          InboxDir inboxDir,
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
        this.inboxDir = inboxDir;
        this.telegramInboxClient = new TelegramInboxClient(settingsStore, inboxDir);
        this.radioLibrary = radioLibrary;
        this.appName = tripTaleProperties.getAppName();
        this.browser = new BrowserLauncher(hostServicesProvider.getIfAvailable());
        this.exportDiaryDialog =
                new ExportDiaryDialog(diaryExporter, wordPressExporter, socialMediaExporter, store, settingsStore, impressions,
                        exportTempFiles, browser, this, this::addPending);
        this.imageViewerDialog = new ImageViewerDialog(impressions, exifReader, browser, this, recentFolder);
        BuildProperties buildProperties = buildPropertiesProvider.getIfAvailable();
        this.version = buildProperties != null ? buildProperties.getVersion() : "dev";
        this.aboutDialog = new AboutDialog(appName, buildProperties, browser);
        this.placeLookup = new PlaceLookup(version);
        this.weatherClient = new OpenMeteoClient(version);
        this.mapAssistantDialog = new MapAssistantDialog(store, new MapboxService(settingsStore),
                new PhotoLocations(impressions, exifReader), connectivityService, this);
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
        // Fixed share of the row, so the bordered status box doesn't grow and shrink with each
        // message; long messages ellipsize inside it.
        var statusWidth = statusRow.widthProperty().multiply(0.55);
        statusBar.minWidthProperty().bind(statusWidth);
        statusBar.prefWidthProperty().bind(statusWidth);
        statusBar.maxWidthProperty().bind(statusWidth);
        datePicker.setConverter(new StringConverter<>() {
            @Override public String toString(LocalDate d) {
                return d == null ? "" : DATE_DISPLAY.format(d) + dayCounter(tripCombo.getValue(), d);
            }
            @Override public LocalDate fromString(String s) {
                if (s == null || s.isBlank()) return null;
                // Drop the " (2/14)" day counter that toString() appends.
                String date = s.replaceFirst("\\s*\\(.*\\)\\s*$", "").trim();
                try { return LocalDate.parse(date, DATE_DISPLAY); } catch (Exception e) { return null; }
            }
        });
        tripCombo.setConverter(new StringConverter<>() {
            @Override public String toString(Trip t) { return t == null ? "" : t.name(); }
            @Override public Trip fromString(String s) { return null; }
        });
        ActivityChoices.install(activityCombo);
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
        if (radioEnabled) updateRadioButton();
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
            LocalDate restore = restoreEntryDate;
            restoreEntryDate = null;
            if (restore != null && dates.contains(restore)) {
                target = restore;
                reason = "Reopened entry from last session (" + target + ")";
            } else if (!dates.isEmpty()) {
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
        activityCombo.valueProperty().addListener((o, a, b) -> updateDirty());
        normalizeOnFocusLost(distanceField, Lengths::parseDistanceKm, Lengths::formatDistance);
        normalizeOnFocusLost(altField, Lengths::parseAltitudeM, Lengths::formatAltitude);
        titleField.textProperty().addListener((o, a, b) -> updateDirty());
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
            if (toSelect.ref().equals(last)) restoreEntryDate = store.loadLastEntryDate().orElse(null);
            tripCombo.getSelectionModel().select(toSelect);
        }
        restoreEntryDate = null;
    }

    /**
     * Remembers the open entry's date in .state.yml so the next launch reopens it. Called once
     * from {@code TripTaleApplication.stop()}, i.e. on every normal shutdown (Quit, window close,
     * Cmd+Q) — never on navigation. The trip itself is already saved on selection.
     */
    public void saveSessionState() {
        log.info("Normal close — saving session state");
        if (!gitService.isConfigured()) return;
        Trip trip = tripCombo.getValue();
        LocalDate date = datePicker.getValue();
        if (trip == null || date == null) return;
        try {
            store.saveLastEntryDate(date);
        } catch (RuntimeException e) {
            log.warn("Could not save session state: {}", e.getMessage());
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
        Optional<NewTripDialog.Spec> spec = new NewTripDialog(store).showAndWait();
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
        store.saveTrip(new Trip(year, slug, name, start, null, spec.get().description(), spec.get().activity()));
        addPending(ref.path(), CREATE);
        spec.get().firstEntry().ifPresent(fe -> {
            DiaryEntry entry = DiaryEntry.builder(start)
                    .activity(spec.get().activity())
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
    public void onEmojiPicker() {
        // Captured before showAndWait() steals focus, see docs/69_emoji_picker.md.
        boolean taleFocused = talesArea.isFocused() && !talesArea.isDisabled();
        int selStart = taleFocused ? talesArea.getSelection().getStart() : -1;
        int selEnd = taleFocused ? talesArea.getSelection().getEnd() : -1;

        Optional<String> picked = EmojiPickerDialog.showAndWait(emojis);
        if (picked.isEmpty()) return;
        String glyph = picked.get();

        if (taleFocused) {
            talesArea.requestFocus();
            talesArea.replaceText(selStart, selEnd, glyph);
            talesArea.positionCaret(selStart + glyph.length());
        } else {
            Clipboards.putString(glyph);
            status(glyph + " copied to clipboard");
        }
    }

    @FXML
    public void onImportGpx() {
        Trip trip = tripCombo.getValue();
        if (trip == null) { error("No trip selected"); return; }
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("GPX files", "*.gpx"));
        store.loadLastGpxFolder().ifPresent(folder -> chooser.setInitialDirectory(folder.toFile()));
        File file = chooser.showOpenDialog(null);
        if (file == null) return;
        store.saveLastGpxFolder(file.getParentFile().toPath());
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
        DiaryEntry existing = store.loadEntry(trip.ref(), p.date());
        Optional<GpxImportDialog.Selection> selection = GpxImportDialog.showAndWait(p, existing, file.getName());
        if (selection.isEmpty()) return;
        GpxImportDialog.Selection sel = selection.get();

        String attachmentStatus = "";
        if (sel.copyAttachment()) {
            try {
                Path attachmentCopy = attachmentsDir.addFiles(trip.ref(), p.date(), List.of(file.toPath())).getFirst();
                attachmentStatus = ", GPX saved to " + UiText.homeRelative(attachmentCopy);
            } catch (RuntimeException e) {
                error(UiText.describe(e));
                return;
            }
        }
        datePicker.setValue(p.date());
        if (sel.title()) titleField.setText(p.name());
        if (sel.startCoords()) {
            startLat = p.lat();
            startLon = p.lon();
        }
        if (sel.stopCoords()) {
            stopLat = p.stopLat();
            stopLon = p.stopLon();
        }
        if (sel.distance()) distanceField.setText(Lengths.formatDistance(p.distanceKm()));
        if (sel.altitude() && p.altitudeGainM() != null) {
            altField.setText(Lengths.formatAltitude(p.altitudeGainM()));
        }
        if (sel.activity() && p.activity() != null) {
            activityCombo.setValue(p.activity());
        }
        updateCoordinatesButton();
        updateDirty();
        status("Imported \"" + p.name() + "\" for " + p.date() + attachmentStatus);
    }

    @FXML
    public void onCheckInbox() {
        Trip trip = tripCombo.getValue();
        if (trip == null) { error("No trip selected"); return; }
        if (!telegramInboxClient.configured()) {
            status("Octobot not configured — showing already-captured items");
            openInboxDialog(trip, "offline — Octobot not configured");
            return;
        }
        long offset = store.loadTelegramOffset().orElse(0L);
        status("Checking inbox…");
        Task<TelegramInboxClient.PollResult> task = new Task<>() {
            @Override protected TelegramInboxClient.PollResult call() {
                return telegramInboxClient.poll(offset);
            }
        };
        task.setOnSucceeded(e -> {
            TelegramInboxClient.PollResult result = task.getValue();
            store.saveTelegramOffset(result.newOffset());
            String summary = result.newItems() == 0 ? "no new items" : result.newItems() + " new item(s)";
            status("Octobot: " + summary);
            openInboxDialog(trip, summary);
        });
        task.setOnFailed(e -> {
            status("Octobot: offline — couldn't reach Telegram (" + UiText.describe(task.getException()) + ")");
            openInboxDialog(trip, "offline — couldn't reach Telegram");
        });
        Thread thread = new Thread(task, "inbox-check");
        thread.setDaemon(true);
        thread.start();
    }

    /** Entries loaded the same way {@code MapAssistantDialog.show} does, for the per-row day combo. */
    private void openInboxDialog(Trip trip, String statusLine) {
        List<InboxItem> items = inboxDir.listPending();
        List<DiaryEntry> entries;
        try {
            entries = store.listEntryDates(trip.ref()).stream().map(d -> store.loadEntry(trip.ref(), d)).toList();
        } catch (RuntimeException e) {
            error("Could not load entries for the inbox: " + UiText.describe(e));
            entries = List.of();
        }
        new InboxDialog(browser).show(items, entries, statusLine, this::handleInboxItem);
    }

    /**
     * Attaches or discards one captured inbox item (todo 68). Attach navigates the main window
     * to the chosen date (the existing {@code datePicker} listener guards unsaved edits and
     * loads the entry, same as a manual date change). A photo item (todo 74) is copied into the
     * day's attachment folder (never part of the git pending-commit system, same as any other
     * attachment); if present, the caption is appended under the entry's shared "## Inbox"
     * heading and the day is saved, same as a text item. A caption-less photo needs no save —
     * there's nothing to persist in the entry itself.
     */
    private boolean handleInboxItem(InboxItem item, InboxDialog.Action action, LocalDate date) {
        if (action == InboxDialog.Action.DISCARD) {
            inboxDir.markDiscarded(item.file());
            return true;
        }
        Trip trip = tripCombo.getValue();
        if (trip == null) { error("No trip selected"); return false; }
        if (date == null) { error("Pick a date first"); return false; }
        if ((trip.startDate() != null && date.isBefore(trip.startDate()))
                || (trip.endDate() != null && date.isAfter(trip.endDate()))) {
            error(date + " is outside " + trip.ref().path() + "'s date range — switch trips first");
            return false;
        }
        if (!date.equals(datePicker.getValue())) {
            datePicker.setValue(date);
            if (!date.equals(datePicker.getValue())) return false; // user cancelled the navigation prompt
        }
        if (item.photo().isPresent()) {
            try {
                impressions.importToAttachments(trip, date, List.of(item.photo().get()));
                updateImpressionsButton(trip, date);
            } catch (RuntimeException e) {
                error("Couldn't import the photo: " + UiText.describe(e));
                return false;
            }
        }
        if (!item.text().isBlank()) {
            talesArea.setText(InboxNotes.append(talesArea.getText(), item.text()));
            updateDirty();
            if (!saveTheDayIfPossible()) {
                error("Attached to the form but couldn't save automatically (needs a title) — save manually, then run Check Inbox again");
                return false;
            }
        }
        inboxDir.markProcessed(item.file());
        return true;
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
        if (inboxMenuItem != null) inboxMenuItem.setDisable(trip == null);
        if (inboxButton != null) inboxButton.setDisable(trip == null);
        if (addAttachmentsMenuItem != null) addAttachmentsMenuItem.setDisable(trip == null || date == null);
        if (backpackButton != null) backpackButton.setDisable(trip == null || date == null);
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
        distanceField.setText(Lengths.formatDistance(e.distance()));
        altField.setText(Lengths.formatAltitude(e.altitudeMeters()));
        // A new day starts with the trip's default activity (todo 57); it's stored on first save.
        ActivityChoices.show(activityCombo, entryExists ? e.activity() : trip.activity());
        titleField.setText(!entryExists ? "" : (e.title() == null ? DiaryEntry.DEFAULT_TITLE : e.title()));
        links = e.links();
        updateLinksButton();
        startLat = e.startLat();
        startLon = e.startLon();
        stopLat = e.stopLat();
        stopLon = e.stopLon();
        startPlace = e.startPlace();
        stopPlace = e.stopPlace();
        weather = e.weather();
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
        activityCombo.setDisable(showEmpty);
        if (linksButton != null) linksButton.setDisable(showEmpty);
        if (addPrivateMenuItem != null) {
            addPrivateMenuItem.setDisable(showEmpty || tripCombo.getValue() == null || datePicker.getValue() == null);
        }
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
                ? "📷 No Impressions yet"
                : "📷 Impressions: 🗂 " + photoLib + "  🎒 " + attached);
        impressionsButton.setDisable(noDay);
        if (impressionsMenuItem != null) impressionsMenuItem.setDisable(noDay);
    }

    /** "🌐 Coordinator: 📍 ✓  🏁 –  🌞 22°" once either point is set; places and coordinates go in the tooltip. */
    private void updateCoordinatesButton() {
        boolean startSet = startLat != null && startLon != null;
        boolean stopSet = stopLat != null && stopLon != null;
        if (coordinatesButton != null) {
            String weatherBadge = weather == null ? "" : "  " + weatherBadge(weather);
            coordinatesButton.setText((startSet || stopSet
                    ? "🌐 Coordinator: 📍 " + (startSet ? "✓" : "–") + "  🏁 " + (stopSet ? "✓" : "–")
                    : "🌐 Coordinator: Uncharted") + weatherBadge);
            boolean anything = startSet || stopSet || startPlace != null || stopPlace != null;
            coordinatesButton.setTooltip(new Tooltip((anything
                    ? "Start " + pointSummary(startPlace, startSet ? Coordinates.toDdm(startLat, startLon) : null)
                            + " · End " + pointSummary(stopPlace, stopSet ? Coordinates.toDdm(stopLat, stopLon) : null)
                    : "Set start and end point") + (weather == null ? "" : " · " + weatherTooltip(weather))));
        }
    }

    /** "🌞 22°" (just the max, the button is a fixed width) for the Coordinator button text. */
    private static String weatherBadge(Weather w) {
        return WeatherCode.fromWmo(w.code()).emoji() + " " + w.tempMax() + "°";
    }

    /** "🌞 15–22°C" (full range, min–max collapsed to one value when equal), with elevation appended when recorded (todo 72), for the tooltip. */
    private static String weatherTooltip(Weather w) {
        String range = w.tempMin() == w.tempMax() ? w.tempMin() + "°C" : w.tempMin() + "–" + w.tempMax() + "°C";
        String base = WeatherCode.fromWmo(w.code()).emoji() + " " + range;
        return w.elevation() == null ? base : base + " · " + w.elevation() + " m a.s.l.";
    }

    /** "Soest, DE (51°29′N 8°20′E)", "Soest, DE (no coordinates)", "51°29′N 8°20′E" or "not set". */
    private static String pointSummary(String place, String ddm) {
        if (place == null) return ddm != null ? ddm : "not set";
        return place + " (" + (ddm != null ? ddm : "no coordinates") + ")";
    }

    /** Map Assistant on the current day (entry form button). */
    @FXML
    public void onMapAssistant() {
        Trip trip = tripCombo.getValue();
        if (trip == null) {
            status("Select a trip first");
            return;
        }
        mapAssistantDialog.showDay(trip, datePicker.getValue());
    }

    /** Map Assistant on the whole trip (My Trips menu). */
    @FXML
    public void onMapAssistantTrip() {
        Trip trip = tripCombo.getValue();
        if (trip == null) {
            status("Select a trip first");
            return;
        }
        mapAssistantDialog.show(trip);
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
        baselineActivity = ActivityChoices.value(activityCombo);
        baselineLinks = links;
        baselineStartLat = startLat;
        baselineStartLon = startLon;
        baselineStopLat = stopLat;
        baselineStopLon = stopLon;
        baselineStartPlace = startPlace;
        baselineStopPlace = stopPlace;
        baselineWeather = weather;
        baselineTales = talesArea.getText();
    }

    private boolean isDirty() {
        return !sameLength(distanceField.getText(), baselineDistance, Lengths::parseDistanceKm)
                || !sameLength(altField.getText(), baselineAlt, Lengths::parseAltitudeM)
                || !Objects.equals(titleField.getText(), baselineTitle)
                || !Objects.equals(ActivityChoices.value(activityCombo), baselineActivity)
                || !Objects.equals(links, baselineLinks)
                || !Objects.equals(startLat, baselineStartLat)
                || !Objects.equals(startLon, baselineStartLon)
                || !Objects.equals(stopLat, baselineStopLat)
                || !Objects.equals(stopLon, baselineStopLon)
                || !Objects.equals(startPlace, baselineStartPlace)
                || !Objects.equals(stopPlace, baselineStopPlace)
                || !Objects.equals(weather, baselineWeather)
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
        ButtonType save    = new ButtonType(Dialogs.SAVE_THE_DAY);
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
        if (saveButton != null) saveButton.setDisable(!saveEnabled);
        if (saveMenuItem != null) saveMenuItem.setDisable(!saveEnabled);
        // Export Tale saves pending edits first, so a savable new entry counts as exportable.
        boolean exportable = tripCombo.getValue() != null && datePicker.getValue() != null
                && (entryExists || saveEnabled);
        if (exportTaleButton != null) exportTaleButton.setDisable(!exportable);
        if (exportTaleMenuItem != null) exportTaleMenuItem.setDisable(!exportable);
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
            // Outside the trip, Today would only snap to its first/last day, which those buttons already do
            LocalDate today = LocalDate.now();
            boolean outsideTrip = trip != null
                    && ((trip.startDate() != null && today.isBefore(trip.startDate()))
                        || (trip.endDate() != null && today.isAfter(trip.endDate())));
            boolean todayDisabled = date == null || date.equals(today) || outsideTrip;
            if (todayButton != null) {
                // Hidden rather than greyed out, so it doesn't clutter the nav block — but always
                // managed (reserves its slot) so toggling it doesn't shift the buttons after it.
                todayButton.setVisible(!todayDisabled);
            }
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
        // The trip may have changed while the date stayed put — re-render the day counter.
        datePicker.getEditor().setText(datePicker.getConverter().toString(date));
        Long day = trip == null || date == null ? null : trip.dayNumber(date);
        if (day == null) {
            datePicker.setTooltip(null);
            if (titleLabel != null) titleLabel.setText("Title:");
            return;
        }
        Long total = trip.totalDays();
        datePicker.setTooltip(new Tooltip((total == null
                ? "Day " + day + " — no end date set (set it in the 📝 Trip Planner)"
                : "Day " + day + " of " + total) + ", " + relativeDayLabel(date)));
        if (titleLabel != null) titleLabel.setText("Title Day " + day + ":");
    }

    /** {@code " (2/14)"}, or {@code " (2/∞)"} without an end date; empty outside the trip. */
    private static String dayCounter(Trip trip, LocalDate date) {
        Long day = trip == null ? null : trip.dayNumber(date);
        if (day == null) return "";
        Long total = trip.totalDays();
        return " (" + day + "/" + (total == null ? "∞" : total) + ")";
    }

    private static String relativeDayLabel(LocalDate date) {
        long delta = ChronoUnit.DAYS.between(LocalDate.now(), date);
        if (delta == 0) return "today";
        if (delta == -1) return "yesterday";
        if (delta == 1) return "tomorrow";
        if (delta < -1) return (-delta) + " days ago";
        return "in " + delta + " days";
    }

    /**
     * Saves the day straight from a frontmatter dialog (Weblinks, Coordinator, image viewer) when
     * it is savable, so the edit doesn't need a second save on the main window. Returns true if
     * the day was saved; otherwise the change stays in the form as unsaved.
     */
    private boolean saveTheDayIfPossible() {
        Trip trip = tripCombo.getValue();
        LocalDate date = datePicker.getValue();
        if (trip == null || date == null || !isValidEntry() || !isDirty()) return false;
        doSave(trip.ref(), date);
        return !isDirty();
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
        Double distance;
        Double alt;
        try {
            distance = parseLength(distanceField.getText(), "distance", Lengths::parseDistanceKm);
            alt = parseLength(altField.getText(), "altitude", Lengths::parseAltitudeM);
        } catch (IllegalArgumentException e) {
            return;
        }
        DiaryEntry entry = DiaryEntry.builder(date)
                .activity(ActivityChoices.value(activityCombo))
                .distance(distance)
                .altitudeMeters(alt)
                .title(titleField.getText())
                .links(links)
                .startLat(startLat)
                .startLon(startLon)
                .stopLat(stopLat)
                .stopLon(stopLon)
                .startPlace(startPlace)
                .stopPlace(stopPlace)
                .weather(weather)
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
        distanceField.setText(Lengths.formatDistance(distance));
        altField.setText(Lengths.formatAltitude(alt));
        snapshotBaseline();
        updateDirty();
        status("Saved " + trip.path() + "/" + date);
        maybeFetchWeather(trip, entry);
    }

    /**
     * Kicks off an async, silent weather fetch (todo 65) when the just-saved entry has a start
     * point but no weather yet. Never overwrites an existing value, never blocks the FX thread,
     * never shows an alert on failure (weather is background enrichment, not load-bearing).
     */
    private void maybeFetchWeather(TripRef trip, DiaryEntry entry) {
        if (entry.weather() != null || entry.startLat() == null || entry.startLon() == null) return;
        double lat = entry.startLat();
        double lon = entry.startLon();
        LocalDate date = entry.date();
        String place = entry.startPlace();
        Task<Weather> task = new Task<>() {
            @Override
            protected Weather call() {
                return weatherClient.fetch(lat, lon, date);
            }
        };
        task.setOnSucceeded(ev -> Platform.runLater(() -> applyFetchedWeather(trip, date, task.getValue(), place)));
        task.setOnFailed(ev -> log.debug("Weather fetch failed for {}/{}: {}", trip.path(), date,
                task.getException() != null ? task.getException().getMessage() : "unknown error"));
        Thread thread = new Thread(task, "weather-fetch");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Applies a background weather fetch result once it arrives. Reloads the entry from disk
     * right before writing (not the possibly-stale in-memory copy) so a save made in the
     * meantime isn't clobbered; bails out if something already set weather meanwhile.
     */
    private void applyFetchedWeather(TripRef trip, LocalDate date, Weather fetched, String place) {
        if (fetched == null) return;
        DiaryEntry current = store.loadEntry(trip, date);
        if (current.weather() != null) return;
        Weather withPlace = place == null ? fetched
                : new Weather(fetched.tempMin(), fetched.tempMax(), fetched.code(), fetched.elevation(), place);
        DiaryEntry updated = DiaryEntry.builder(date)
                .activity(current.activity())
                .distance(current.distance())
                .altitudeMeters(current.altitudeMeters())
                .title(current.title())
                .links(current.links())
                .startLat(current.startLat())
                .startLon(current.startLon())
                .stopLat(current.stopLat())
                .stopLon(current.stopLon())
                .startPlace(current.startPlace())
                .stopPlace(current.stopPlace())
                .weather(withPlace)
                .tales(current.tales())
                .build();
        store.saveEntry(trip, updated);
        addPending(trip.path() + "/" + date, UPDATE);
        log.debug("Weather saved for {}/{}: {}", trip.path(), date, withPlace.toRaw());
        Trip selectedTrip = tripCombo.getValue();
        LocalDate selectedDate = datePicker.getValue();
        if (selectedTrip != null && selectedTrip.ref().equals(trip) && date.equals(selectedDate)) {
            // Already persisted above, so the baseline moves too — otherwise isDirty() would
            // report an unsaved change for a field the user never touched.
            weather = withPlace;
            baselineWeather = withPlace;
            updateCoordinatesButton();
            status("Weather fetched for " + date);
        }
        updateDirty();
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
    public void onAddPrivateSection() {
        if (talesArea.isDisabled()) return;
        String text = talesArea.getText();
        int existing = Markdown.privateHeadingOffset(text);
        talesArea.requestFocus();
        if (existing >= 0) {
            talesArea.positionCaret(existing);
            status("Tale already has a private section");
            return;
        }
        // replaceText (not setText) keeps the insert undoable
        int end = text.stripTrailing().length();
        String insert = (end == 0 ? "" : "\n\n") + "## Internal\n\n" + PRIVATE_PLACEHOLDER;
        talesArea.replaceText(end, text.length(), insert);
        int start = end + insert.length() - PRIVATE_PLACEHOLDER.length();
        talesArea.selectRange(start, start + PRIVATE_PLACEHOLDER.length());
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
        // Sync Tracks checks the bucket itself, so only the mode matters (todo 52).
        if (syncTracksMenuItem != null) syncTracksMenuItem.setDisable(!cloud);
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
            BucketSyncer.Result r = syncer.push(root, prefix, (done, total, file) ->
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
        String run(BucketSyncer syncer, Path root, String keyPrefix);
    }

    private void transferAttachments(String verb, String threadName, AttachmentTransfer transfer) {
        AppSettings.Attachments settings = settingsStore.load().getAttachments();
        Path root;
        BucketSyncer syncer;
        String keyPrefix;
        try {
            root = attachmentsDir.root();
            syncer = new BucketSyncer(S3Client.from(settings));
            keyPrefix = BucketSyncer.keyPrefix(BucketUrl.parse(settings.getBucketUrl()), AttachmentsDir.DIR_NAME);
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
        status("📻 " + Station.NAME + " is off air");
    }

    @FXML
    public void onOpenRadioFolder() {
        if (!radioEnabled) return;
        radioLibrary.ensureManagedFiles();
        browser.open(radioLibrary.root().toUri().toString());
    }

    @FXML
    public void onSyncTracks() {
        if (!radioEnabled) return;
        new SyncTracksDialog(settingsStore, radioLibrary).show();
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
            boolean idle = !playing && (radio == null || !radio.isLoaded());
            if (idle && speakerIcon == null) speakerIcon = new SpeakerIcon();
            radioButton.setGraphic(idle ? speakerIcon : null);
            radioButton.setText(idle ? "" : playing ? "⏸" : "▶");
        }
        if (radio == null || !radio.isLoaded()) {
            radioButton.setTooltip(new Tooltip(Station.NAME + " — play a random track from radio/"));
        } else if (playing) {
            radioButton.setTooltip(new Tooltip("On air: " + radio.label() + " — click to pause"));
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

    /**
     * Force-overwrites trip.md/tale.md/type.md/post.md with the app's current built-in templates
     * (todo 60: post.md didn't exist in data dirs set up before it was added). Any hand edits to
     * these files are lost — warn first; the files stay git-trackable like everything else, so
     * the change is reviewable (Smart Sync's diff) and revertable before a commit.
     */
    @FXML
    public void onUpdateTypeDefinitions() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "This overwrites " + String.join(", ", GitService.MANAGED_TYPE_DEFINITIONS)
                        + " at the data dir root with " + appName + "'s current built-in templates.\n\n"
                        + "Any manual edits to these files will be lost. The change is written to disk "
                        + "immediately; review it (e.g. via Smart Sync's diff) before committing.",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle(appName);
        confirm.setHeaderText("Update Tolaria type definitions");
        Dialogs.applyStylesheet(confirm.getDialogPane());
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.YES) return;
        try {
            gitService.updateTypeDefinitions();
            GitService.MANAGED_TYPE_DEFINITIONS.forEach(label -> addPending(label, UPDATE));
            status("Type definitions updated — review and Smart Sync to commit");
        } catch (RuntimeException e) {
            error("Update failed: " + e.getMessage());
        }
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
    public void onLinks() {
        Optional<SequencedMap<String, Link>> result =
                new LinksDialog(browser).showAndWait(links, isValidEntry());
        if (result.isEmpty()) return;
        links = result.get();
        updateLinksButton();
        updateDirty();
        saveTheDayIfPossible();
    }

    /** "🕸 Weblinks: 🗺 🍲" (🔗 for unknown verbs, ⚠ if a value isn't a URL), or "🕸 No Weblinks yet". */
    private void updateLinksButton() {
        if (linksButton == null) return;
        if (links.isEmpty()) {
            linksButton.setText("🕸 No Weblinks yet");
            return;
        }
        // En spaces: a plain space next to a color emoji renders almost zero-width in JavaFX
        StringBuilder label = new StringBuilder("🕸 Weblinks:");
        links.keySet().forEach(verb -> label.append('\u2002').append(LinkKind.emojiFor(verb)));
        if (links.values().stream().anyMatch(l -> !l.valid())) label.append("\u2002⚠");
        linksButton.setText(label.toString());
    }

    @FXML
    public void onCoordinates() {
        Optional<CoordinatesDialog.Result> result =
                new CoordinatesDialog(browser, placeLookup, weatherClient, Boolean.TRUE.equals(connected))
                        .showAndWait(startLat, startLon, stopLat, stopLon, startPlace, stopPlace, weather,
                                datePicker.getValue(), isValidEntry());
        if (result.isEmpty()) return;
        Coordinates.LatLon start = result.get().start();
        Coordinates.LatLon stop = result.get().stop();
        startLat = start == null ? null : start.lat();
        startLon = start == null ? null : start.lon();
        stopLat = stop == null ? null : stop.lat();
        stopLon = stop == null ? null : stop.lon();
        startPlace = result.get().startPlace();
        stopPlace = result.get().stopPlace();
        weather = result.get().weather();
        updateCoordinatesButton();
        updateDirty();
        saveTheDayIfPossible();
    }

    @FXML
    public void onEditSettings() {
        AppSettings settings = settingsStore.load();
        String previousDataDir = settings.getDataDir();
        Optional<AppSettings> edited =
                new EditSettingsDialog(browser).showAndWait(settings, settingsStore.settingsFile());
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
        imageViewerDialog.show(trip, date, () -> updateImpressionsButton(trip, date), new ImageViewerDialog.RoutePoints() {
            @Override
            public Coordinates.LatLon get(ImageViewerDialog.RoutePoint point) {
                Double lat = point == ImageViewerDialog.RoutePoint.START ? startLat : stopLat;
                Double lon = point == ImageViewerDialog.RoutePoint.START ? startLon : stopLon;
                return lat == null || lon == null ? null : new Coordinates.LatLon(lat, lon);
            }

            @Override
            public boolean set(ImageViewerDialog.RoutePoint point, Coordinates.LatLon value) {
                if (point == ImageViewerDialog.RoutePoint.START) {
                    startLat = value.lat();
                    startLon = value.lon();
                } else {
                    stopLat = value.lat();
                    stopLon = value.lon();
                }
                updateCoordinatesButton();
                updateDirty();
                return saveTheDayIfPossible();
            }
        });
    }

    /** Parses a length field, showing an error alert (and rethrowing) if the text is invalid. */
    private Double parseLength(String s, String field, Function<String, Double> parser) {
        try {
            return parser.apply(s);
        } catch (IllegalArgumentException e) {
            error("Invalid " + field + ": " + s.strip() + " (use a number, optionally followed by km or m)");
            throw e;
        }
    }

    /** Same value if both parse (so "101.64" equals "101.64 km"), otherwise same text. */
    private static boolean sameLength(String a, String b, Function<String, Double> parser) {
        try {
            return Objects.equals(parser.apply(a), parser.apply(b));
        } catch (IllegalArgumentException e) {
            return Objects.equals(a, b);
        }
    }

    /** Rewrites valid input as "value unit" when the field loses focus; invalid text is left for save-time validation. */
    private static void normalizeOnFocusLost(TextField field, Function<String, Double> parser,
                                             Function<Double, String> formatter) {
        field.focusedProperty().addListener((o, was, focused) -> {
            if (focused) return;
            try {
                String formatted = formatter.apply(parser.apply(field.getText()));
                if (!formatted.equals(field.getText())) field.setText(formatted);
            } catch (IllegalArgumentException ignored) {
                // keep what the user typed; doSave reports it
            }
        });
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
