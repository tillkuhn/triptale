package net.timafe.triptale.ui;

import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;
import net.timafe.triptale.domain.Activity;

import java.util.ArrayList;
import java.util.List;

/**
 * The activity picker shared by the entry form and the trip dialogs (todos 55, 57): "not set",
 * the curated {@link Activity} values, and an unknown value as an extra item so it's never dropped.
 */
public final class ActivityChoices {

    /** Item for "not set"; a null combo value is awkward to render. {@link #value} maps it back to null. */
    private static final String NONE = "";

    private ActivityChoices() {}

    public static ComboBox<String> newComboBox(String activity) {
        ComboBox<String> combo = new ComboBox<>();
        install(combo);
        show(combo, activity);
        return combo;
    }

    public static void install(ComboBox<String> combo) {
        combo.setConverter(new StringConverter<>() {
            @Override public String toString(String a) {
                return a == null || a.isEmpty() ? "😴 No activity yet" : Activity.emojiFor(a) + " " + Activity.labelFor(a);
            }
            @Override public String fromString(String s) { return null; }
        });
        show(combo, null);
    }

    public static void show(ComboBox<String> combo, String activity) {
        List<String> items = new ArrayList<>();
        items.add(NONE);
        for (Activity a : Activity.values()) items.add(a.key());
        if (activity != null && Activity.of(activity).isEmpty()) items.add(activity);
        combo.getItems().setAll(items);
        combo.setValue(activity == null ? NONE : activity);
    }

    /** The selected activity, null for "not set". */
    public static String value(ComboBox<String> combo) {
        return Activity.normalize(combo.getValue());
    }
}
