package net.timafe.triptale.sync;

/** Unchecked failure while syncing a folder with the bucket (network, S3 error response, local I/O). */
public class SyncException extends RuntimeException {
    public SyncException(String message) {
        super(message);
    }

    public SyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
