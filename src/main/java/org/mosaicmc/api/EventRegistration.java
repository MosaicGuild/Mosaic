package org.mosaicmc.api;

/**
 * A handle for a listener registered through {@link ExtensionEvents}.
 * Calling {@link #unregister()} removes the listener.
 */
public interface EventRegistration {

    /**
     * Removes the listener. Idempotent, never throws, and safe to call from
     * inside the listener callback itself.
     */
    void unregister();
}
