package org.mosaicmc.core;

import java.util.Objects;

public abstract class Extension {

    private ExtensionContext context;

    /**
     * This provides all the metadata of the extension.
     */
    public abstract ExtensionMetadata getMetadata();

    /**
     * Provides access to this extension's context.
     *
     * @throws IllegalStateException if the extension has not been given a context yet
     */
    public final ExtensionContext getContext() {
        if (context == null) {
            throw new IllegalStateException(getClass().getName() + " has no context yet (not registered via ExtensionManager?)");
        }
        return context;
    }

    /**
     * Internal hook used by {@link ExtensionManager} to inject the context.
     * Not part of the public extension API.
     */
    final void setContext(ExtensionContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    /**
     * This runs once the extension has been enabled by the user.
     * You may use this to register your changes.
     */
    public abstract void onEnable();

    /**
     * This runs once the extension has been disabled by the user.
     * You shall unload any listeners that you have registered via the API here.
     */
    public abstract void onDisable();

    /**
     * This runs once the extension has been loaded by the extension manager.
     * Use this to add the extension menus and entry in the GUI.
     */
    public abstract void onLoad();
}
