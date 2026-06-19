package net.timafe.triptale.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StorageExceptionTest {

    @Test
    void storesMessageAndCause() {
        Throwable cause = new RuntimeException("root");
        StorageException ex = new StorageException("failed", cause);
        assertEquals("failed", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void acceptsNullCause() {
        StorageException ex = new StorageException("no cause", null);
        assertEquals("no cause", ex.getMessage());
        assertNull(ex.getCause());
    }
}
