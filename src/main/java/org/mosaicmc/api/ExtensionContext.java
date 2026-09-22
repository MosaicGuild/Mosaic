package org.mosaicmc.api;

/**
 * Services Mosaic provides to one extension.
 *
 * <p>Do not implement this interface yourself; Mosaic injects the instance
 * and hands it out via {@code Extension.getContext()}. Always call
 * {@code getContext()} fresh instead of caching the context: Mosaic may
 * re-issue context objects over an extension's lifetime, and a cached
 * reference can serve a stale scheduler.
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
