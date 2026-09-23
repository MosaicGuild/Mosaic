package org.mosaicmc.extension;

import java.util.Objects;

import org.mosaicmc.api.ExtensionContext;
import org.mosaicmc.api.ExtensionMetadata;

/**
 * A Mosaic extension.
 *
 * <p>Lifecycle, in order:
 * <ol>
 *   <li>Instantiated via the {@code mosaic} entrypoint.</li>
 *   <li>Mosaic injects the {@link ExtensionContext}.</li>
 *   <li>{@link #onLoad()} runs once at discovery time.</li>
 *   <li>{@link #onEnable()} / {@link #onDisable()} run on every user toggle;
 *   each may run multiple times, and {@code onLoad} always runs first.</li>
 * </ol>
 *
 * <p>The context is only available from {@code onLoad} onwards -- never in a
 * constructor or field initializer. Call {@code getContext()} fresh instead
 * of caching it; re-issued contexts can leave a cached reference stale.
 *
 * <p>A throwing lifecycle method is caught and logged by Mosaic; it never
 * propagates to the toggling caller.
 */
public abstract class Extension {

    private ExtensionContext context;

    /**
     * The extension's metadata. Never {@code null}; broken metadata (an id
     * that is {@code null} or blank) skips the extension at discovery time.
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
     *
     * <p>Prefer explicit {@code unregister()} here. As a safety net, Mosaic
     * also removes the extension's remaining managed registrations after
     * this method returns, so no callback is left behind either way.
     */
    public abstract void onDisable();

    /**
     * This runs once the extension has been loaded by the extension manager.
     * Use this to add the extension menus and entry in the GUI.
     */
    public abstract void onLoad();
}
