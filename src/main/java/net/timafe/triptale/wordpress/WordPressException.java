package net.timafe.triptale.wordpress;

/** Unchecked failure during the WordPress.com OAuth2 flow or a REST API call (todo 60). */
public class WordPressException extends RuntimeException {
    public WordPressException(String message) {
        super(message);
    }

    public WordPressException(String message, Throwable cause) {
        super(message, cause);
    }
}
