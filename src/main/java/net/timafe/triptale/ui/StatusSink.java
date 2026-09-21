package net.timafe.triptale.ui;

/**
 * Narrow view of the main window's status line, handed to dialog classes so they can report
 * outcomes ("Diary copied to clipboard") without a back-reference to {@code MainController}.
 */
public interface StatusSink {

    /** Logs and shows an informational message in the status bar. */
    void status(String msg);

    /** Logs the message, shows it in the status bar, and blocks on a modal error alert. */
    void error(String msg);
}
