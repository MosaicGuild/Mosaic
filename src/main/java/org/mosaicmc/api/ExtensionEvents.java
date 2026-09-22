package org.mosaicmc.api;

import net.fabricmc.fabric.api.event.Event;

public interface ExtensionEvents {

    /**
     * Registers a listener on a Fabric event.
     *
     * @param event the Fabric event to listen on
     * @param listener the listener to register
     * @param <T> the listener type
     */
    <T> void register(Event<T> event, T listener);
}
