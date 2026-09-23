package org.mosaicmc.api;

/**
 * Services Mosaic provides to one extension.
 *
 * <p>Do not implement this interface; Mosaic injects the instance. Call
 * {@code getContext()} fresh instead of caching it: Mosaic may re-issue
 * contexts, and a cached reference can go stale.
 */
public interface ExtensionContext {

    /**
     * Provides access to the extension scheduler.
     *
     * @return the scheduler, never {@code null}
     */
    ExtensionScheduler getScheduler();

    /**
     * Provides access to the extension events.
     *
     * @return the events bridge, never {@code null}
     */
    ExtensionEvents getEvents();
}
