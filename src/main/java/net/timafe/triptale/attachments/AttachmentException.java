package net.timafe.triptale.attachments;

/** Unchecked failure while syncing attachments (network, S3 error response, local I/O). */
public class AttachmentException extends RuntimeException {
    public AttachmentException(String message) {
        super(message);
    }

    public AttachmentException(String message, Throwable cause) {
        super(message, cause);
    }
}
