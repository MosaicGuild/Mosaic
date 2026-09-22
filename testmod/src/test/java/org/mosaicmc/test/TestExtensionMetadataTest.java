package org.mosaicmc.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mosaicmc.core.ExtensionMetadata;

/**
 * Guards the test extension metadata against drift from its
 * {@code fabric.mod.json} declaration.
 */
class TestExtensionMetadataTest {

    @Test
    void metadataMatchesModJson() {
        TestExtension extension = new TestExtension();
        ExtensionMetadata metadata = extension.getMetadata();

        assertNotNull(metadata);
        assertEquals("mosaic-testmod", metadata.getId());
        assertTrue(metadata.getName() != null && !metadata.getName().isBlank());
        assertTrue(metadata.getDescription() != null && !metadata.getDescription().isBlank());
        assertTrue(metadata.getVersion() != null && !metadata.getVersion().isBlank());
        assertTrue(metadata.getAuthors() != null && !metadata.getAuthors().isBlank());
    }

    @Test
    void lifecycleDoesNotThrow() {
        TestExtension extension = new TestExtension();

        extension.onLoad();
        extension.onEnable();
        extension.onDisable();
    }
}
