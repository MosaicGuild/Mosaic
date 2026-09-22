package org.mosaicmc.api;

import net.fabricmc.fabric.api.event.Event;

/**
 * Thin bridge over Fabric's event model.
 *
 * <p>Pass any Fabric {@link Event} (for example
 * {@code ClientTickEvents.END_CLIENT_TICK}) together with its listener; see
 * the Fabric documentation for the available events and their listener
 * types. Example:
 * <pre>{@code
 * getContext().getEvents().register(
 *     ClientTickEvents.END_CLIENT_TICK,
 *     client -> {
 *         if (client.player != null) {
 *             // actual extension functionality
 *         }
 *     });
 * }</pre>
 *
 * <p>Deliberate v1 limitation: registration returns no handle and cannot be
 * undone, so a registered listener lives for the rest of the session. A
 * future version will return a handle for cleanup in
 * {@code Extension.onDisable()}.
 */
public interface ExtensionEvents {

    /**
     * Registers a listener on a Fabric event.
     *
     * @param event the Fabric event to listen on, must not be {@code null}
     * @param listener the listener to register, must not be {@code null}
     * @param <T> the listener type
     * @throws NullPointerException if {@code event} or {@code listener} is {@code null}
     */
    <T> void register(Event<T> event, T listener);
}
