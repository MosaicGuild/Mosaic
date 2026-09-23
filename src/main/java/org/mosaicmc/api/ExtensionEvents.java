package org.mosaicmc.api;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Event registrations with working unregistration.
 *
 * <p>Every method here returns an {@link EventRegistration}; calling
 * {@link EventRegistration#unregister()} removes the listener. Only the
 * methods listed here carry that guarantee.
 *
 * <p>Example:
 * <pre>{@code
 * EventRegistration ticking = getContext().getEvents().onClientTick(client -> {
 *     if (client.player != null) {
 *         // actual extension functionality
 *     }
 * });
 * ...
 * ticking.unregister();
 * }</pre>
 */
public interface ExtensionEvents {

    /**
     * Registers a listener for the client end-tick, running on the client
     * thread after each client tick.
     *
     * <p>Client-only: throws {@link IllegalStateException} on a dedicated
     * server. Disabling the extension removes its remaining registrations
     * automatically, but explicit {@code unregister()} is still preferred.
     *
     * @param listener the tick listener, must not be {@code null}
     * @return a working registration handle, never {@code null}
     * @throws NullPointerException if {@code listener} is {@code null}
     * @throws IllegalStateException if called where no client tick exists
     */
    EventRegistration onClientTick(ClientTickEvents.EndTick listener);

    /**
     * Registers a listener for the client start-tick, running on the client
     * thread before each client tick.
     *
     * <p>Same lifecycle and error contract as {@link #onClientTick}.
     *
     * @param listener the tick listener, must not be {@code null}
     * @return a working registration handle, never {@code null}
     * @throws NullPointerException if {@code listener} is {@code null}
     * @throws IllegalStateException if called where no client tick exists
     */
    EventRegistration onClientTickStart(ClientTickEvents.StartTick listener);

    /**
     * Registers a listener for the client level start-tick, running on the
     * client thread before each tick of the loaded level.
     *
     * <p>Same lifecycle and error contract as {@link #onClientTick}.
     *
     * @param listener the level tick listener, must not be {@code null}
     * @return a working registration handle, never {@code null}
     * @throws NullPointerException if {@code listener} is {@code null}
     * @throws IllegalStateException if called where no client tick exists
     */
    EventRegistration onLevelTickStart(ClientTickEvents.StartLevelTick listener);

    /**
     * Registers a listener for the client level end-tick, running on the
     * client thread after each tick of the loaded level.
     *
     * <p>Same lifecycle and error contract as {@link #onClientTick}.
     *
     * @param listener the level tick listener, must not be {@code null}
     * @return a working registration handle, never {@code null}
     * @throws NullPointerException if {@code listener} is {@code null}
     * @throws IllegalStateException if called where no client tick exists
     */
    EventRegistration onLevelTickEnd(ClientTickEvents.EndLevelTick listener);
}
