package org.mosaicmc.api;

import org.mosaicmc.api.command.CommandManager;
import org.mosaicmc.api.settings.Settings;
import org.mosaicmc.api.storage.ExtensionStorage;

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

    /**
     * Provides access to the extension's commands under {@code /mosaic}.
     *
     * @return the command manager, never {@code null}
     */
    CommandManager getCommands();

    /**
     * Provides access to the extension's settings declarations.
     *
     * <p>Setting ids are scoped to the calling extension.
     *
     * @return the settings facade, never {@code null}
     */
    Settings getSettings();

    /**
     * Provides access to the extension's private string store, for small
     * custom data that does not fit typed settings declarations.
     *
     * <p>Storage keys are scoped to the calling extension, like setting ids.
     *
     * @return the storage facade, never {@code null}
     */
    ExtensionStorage getStorage();
}
