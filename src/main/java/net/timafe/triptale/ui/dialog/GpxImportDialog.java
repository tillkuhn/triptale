package net.timafe.triptale.ui.dialog;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import net.timafe.triptale.domain.Activity;
import net.timafe.triptale.domain.DiaryEntry;
import net.timafe.triptale.domain.Link;
import net.timafe.triptale.domain.LinkKind;
import net.timafe.triptale.ui.Dialogs;
import net.timafe.triptale.util.Coordinates;
import net.timafe.triptale.util.GpxImport;
import net.timafe.triptale.util.Lengths;

import java.util.Optional;

/**
 * Per-field overwrite matrix shown on every GPX import for a Tale Entry (todo 66), replacing
 * todo 24's all-or-nothing confirmation. Each row has its own checkbox so the user can take just
 * the distance/altitude from a track while keeping a hand-written title, or vice versa. Old
 * values always come from disk ({@code store.loadEntry} via the caller), never the live
 * (possibly dirty, possibly different-day) form.
 */
public final class GpxImportDialog {

    /**
     * One checkbox's answer per field; {@code altitude} is irrelevant when the GPX had none,
     * {@code tale} (append the sidecar's text, todo 94) when there was no text,
     * {@code link} (the sidecar's {@code web_url} as {@code url_track}) when there was none.
     */
    public record Selection(boolean title, boolean startCoords, boolean stopCoords,
                             boolean distance, boolean altitude, boolean activity, boolean copyAttachment,
                             boolean tale, boolean link) {}

    private GpxImportDialog() {}

    /**
     * Empty when cancelled. {@code existing} is the on-disk entry for {@code parsed.date()}
     * (empty fields for a brand-new day), {@code fileName} labels the "copy to attachments" row,
     * {@code tourText} is the JSON sidecar's description/comment or {@code null} if there is none,
     * {@code tourLink} its {@code web_url} link or {@code null}.
     */
    public static Optional<Selection> showAndWait(GpxImport.Parsed parsed, DiaryEntry existing, String fileName,
                                                  String tourText, Link tourLink) {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(8);
        grid.setPadding(new Insets(14));
        ColumnConstraints nameCol = new ColumnConstraints(110);
        ColumnConstraints valueCol = new ColumnConstraints(170);
        valueCol.setHgrow(Priority.ALWAYS);
        ColumnConstraints checkCol = new ColumnConstraints();
        grid.getColumnConstraints().addAll(nameCol, valueCol, valueCol, checkCol);

        int row = 0;
        grid.add(bold("Field"), 0, row);
        grid.add(bold("Current"), 1, row);
        grid.add(bold("From GPX"), 2, row);
        grid.add(bold("Overwrite"), 3, row);
        row++;

        boolean titleEmpty = existing.title() == null || existing.title().isBlank()
                || existing.title().equals(DiaryEntry.DEFAULT_TITLE);
        CheckBox titleCheck = addRow(grid, row++, "Title",
                titleEmpty ? "(none)" : existing.title(), parsed.name(), titleEmpty);

        CheckBox activityCheck = null;
        if (parsed.activity() != null) {
            boolean activityEmpty = existing.activity() == null;
            activityCheck = addRow(grid, row++, "Activity",
                    activityEmpty ? "(none)" : activityLabel(existing.activity()),
                    activityLabel(parsed.activity()), activityEmpty);
        }

        boolean startEmpty = existing.startLat() == null || existing.startLon() == null;
        CheckBox startCheck = addRow(grid, row++, "Start coords",
                startEmpty ? "(none)" : Coordinates.toDdm(existing.startLat(), existing.startLon()),
                Coordinates.toDdm(parsed.lat(), parsed.lon()), startEmpty);

        boolean stopEmpty = existing.stopLat() == null || existing.stopLon() == null;
        CheckBox stopCheck = addRow(grid, row++, "Stop coords",
                stopEmpty ? "(none)" : Coordinates.toDdm(existing.stopLat(), existing.stopLon()),
                Coordinates.toDdm(parsed.stopLat(), parsed.stopLon()), stopEmpty);

        boolean distanceEmpty = existing.distance() == null;
        CheckBox distanceCheck = addRow(grid, row++, "Distance",
                distanceEmpty ? "(none)" : Lengths.formatDistance(existing.distance()),
                Lengths.formatDistance(parsed.distanceKm()), distanceEmpty);

        CheckBox altitudeCheck = null;
        if (parsed.altitudeGainM() != null) {
            boolean altitudeEmpty = existing.altitudeMeters() == null;
            altitudeCheck = addRow(grid, row++, "Altitude",
                    altitudeEmpty ? "(none)" : Lengths.formatAltitude(existing.altitudeMeters()),
                    Lengths.formatAltitude(parsed.altitudeGainM()), altitudeEmpty);
        }

        CheckBox taleCheck = null;
        if (tourText != null) {
            boolean taleEmpty = existing.tales() == null || existing.tales().isBlank();
            taleCheck = addRow(grid, row++, taleEmpty ? "Text" : "Text (append)",
                    taleEmpty ? "(none)" : preview(existing.tales()), preview(tourText), taleEmpty);
        }

        CheckBox linkCheck = null;
        if (tourLink != null) {
            Link current = existing.links() == null ? null : existing.links().get(LinkKind.TRACK.key());
            linkCheck = addRow(grid, row++, "Track link",
                    current == null ? "(none)" : current.displayText(), tourLink.displayText(), current == null);
        }

        CheckBox copyCheck = addRow(grid, row, "GPX attachment", "(not copied)", fileName, true);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("🦎 GPX Gecko");
        dialog.setHeaderText("Imported \"" + parsed.name() + "\" for " + parsed.date()
                + " — choose which fields to overwrite");
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        Dialogs.applyStylesheet(dialog.getDialogPane());

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return Optional.empty();
        CheckBox finalAltitudeCheck = altitudeCheck;
        CheckBox finalActivityCheck = activityCheck;
        CheckBox finalTaleCheck = taleCheck;
        CheckBox finalLinkCheck = linkCheck;
        return Optional.of(new Selection(
                titleCheck.isSelected(),
                startCheck.isSelected(),
                stopCheck.isSelected(),
                distanceCheck.isSelected(),
                finalAltitudeCheck != null && finalAltitudeCheck.isSelected(),
                finalActivityCheck != null && finalActivityCheck.isSelected(),
                copyCheck.isSelected(),
                finalTaleCheck != null && finalTaleCheck.isSelected(),
                finalLinkCheck != null && finalLinkCheck.isSelected()));
    }

    private static String preview(String text) {
        String oneLine = text.strip().replaceAll("\\s+", " ");
        return oneLine.length() <= 40 ? oneLine : oneLine.substring(0, 39) + "…";
    }

    private static String activityLabel(String activity) {
        return Activity.emojiFor(activity) + " " + Activity.labelFor(activity);
    }

    private static CheckBox addRow(GridPane grid, int row, String name, String oldValue, String newValue,
                                    boolean defaultChecked) {
        grid.add(new Label(name), 0, row);
        grid.add(new Label(oldValue), 1, row);
        grid.add(new Label(newValue), 2, row);
        CheckBox check = new CheckBox();
        check.setSelected(defaultChecked);
        grid.add(check, 3, row);
        return check;
    }

    private static Label bold(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }
}
