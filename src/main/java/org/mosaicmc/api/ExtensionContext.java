package org.mosaicmc.api;

public interface ExtensionContext {

    /**
     * Provides access to the extension scheduler.
     */
    ExtensionScheduler getScheduler();

    /**
     * Provides access to the extension events.
     */
    ExtensionEvents getEvents();
}
