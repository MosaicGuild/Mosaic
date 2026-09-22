package org.mosaicmc.extension;

import java.util.Objects;

import org.mosaicmc.api.ExtensionContext;
import org.mosaicmc.api.ExtensionMetadata;

/**
 * A Mosaic extension.
 *
 * <p>Lifecycle, in order:
 * <ol>
 *   <li>The extension is instantiated via its {@code mosaic} entrypoint.</li>
 *   <li>Mosaic injects its {@link ExtensionContext}.</li>
 *   <li>{@link #onLoad()} runs once at discovery time.</li>
 *   <li>{@link #onEnable()} / {@link #onDisable()} run each time the user
 *   toggles the extension. Each may run multiple times, and {@code onLoad}
 *   always runs before the first {@code onEnable}.</li>
 * </ol>
 *
 * <p>The context is only available from {@code onLoad} onwards. Do not call
 * {@link #getContext()} from a constructor or field initializer; it throws
 * {@link IllegalStateException} until injection has happened. Always call
 * {@code getContext()} fresh rather than caching the returned object: Mosaic
 * may re-issue contexts (for example when the scheduler is swapped in), and a
 * cached reference can go stale.
 *
 * <p>If a lifecycle method throws, the exception is caught and logged by
 * Mosaic; it never propagates to the caller that toggled the extension.
 */
public abstract class Extension {

    private ExtensionContext context;

    /**
     * This provides all the metadata of the extension.
     *
     * <p>Must never return {@code null}. If the metadata itself is broken
     * ({@code null}, or an id that is {@code null} or blank), the extension
     * is skipped at discovery time and {@code onLoad} never runs.
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
     * <p>Note: in API v1, listeners registered via
     * {@link org.mosaicmc.api.ExtensionEvents#register} cannot be
     * unregistered (registration returns no handle and lives for the
     * session), so there is nothing to unload yet. This method exists for
     * forward compatibility: once handles exist, cleanup will belong here.
     */
    public abstract void onDisable();

    /**
     * This runs once the extension has been loaded by the extension manager.
     * Use this to add the extension menus and entry in the GUI.
     */
    public abstract void onLoad();
}
