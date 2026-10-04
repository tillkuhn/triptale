package net.timafe.triptale;

import javafx.application.Application;

import java.util.Locale;

/**
 * Real entry point. Deliberately does <b>not</b> extend {@link Application}: when the main class
 * is an {@code Application} subclass and JavaFX sits on the classpath (extracted jar, AOT cache
 * runs), the JDK launcher aborts with "JavaFX runtime components are missing". A plain main
 * class sidesteps that check, so the app runs the same from the fat jar, the extracted jar
 * ({@code make run-fast}) and {@code javafx:run}. See docs/43_startup_performance.md.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        // All our labels are English, so JavaFX's built-in texts (Cancel, Yes, ...) must be too.
        // Keeping the system region preserves regional conventions like the first day of week.
        Locale.setDefault(new Locale.Builder()
                .setLanguage("en")
                .setRegion(Locale.getDefault().getCountry())
                .build());
        Application.launch(TripTaleApplication.class, args);
    }
}
