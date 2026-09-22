package org.mosaicmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the {@code IdentifierException} crash caused by an
 * invalid namespace in {@link Mosaic#MOD_ID}.
 */
class MosaicIdTest {

    @Test
    void modIdIsValidNamespace() {
        // Identifier namespaces only allow [a-z0-9_.-]; anything else throws
        // IdentifierException at runtime (the "org/mosaicmc" crash).
        assertTrue(Mosaic.MOD_ID.matches("[a-z0-9_.-]+"),
                "MOD_ID must be a valid identifier namespace, was: " + Mosaic.MOD_ID);
    }

    @Test
    void idUsesModIdAsNamespaceAndKeepsPath() {
        Identifier identifier = Mosaic.id("mosaic");

        assertEquals("mosaic", identifier.getNamespace());
        assertEquals("mosaic", identifier.getPath());
    }
}
