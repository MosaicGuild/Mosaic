package org.mosaicmc.core;

public interface ExtensionContext {

    /**
     * Provides access to the extension scheduler.
     */
    ExtensionScheduler getScheduler();
}
