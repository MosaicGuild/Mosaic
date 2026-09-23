package org.mosaicmc.internal;

import java.util.Objects;

import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.mosaicmc.api.EventRegistration;
import org.mosaicmc.api.ExtensionEvents;

/**
 * One extension's events bridge. The manager reuses the bridge (not the
 * context) across scheduler swaps, so registrations survive them.
 */
public final class ExtensionEventsImpl implements ExtensionEvents {

    @Override
    public EventRegistration onClientTick(ClientTickEvents.EndTick listener) {
        Objects.requireNonNull(listener, "listener");
        requireClientTick();
        return ClientTickRegistry.END_CLIENT.add(this, listener);
    }

    @Override
    public EventRegistration onClientTickStart(ClientTickEvents.StartTick listener) {
        Objects.requireNonNull(listener, "listener");
        requireClientTick();
        return ClientTickRegistry.START_CLIENT.add(this, listener);
    }

    @Override
    public EventRegistration onLevelTickStart(ClientTickEvents.StartLevelTick listener) {
        Objects.requireNonNull(listener, "listener");
        requireClientTick();
        return ClientTickRegistry.START_LEVEL.add(this, listener);
    }

    @Override
    public EventRegistration onLevelTickEnd(ClientTickEvents.EndLevelTick listener) {
        Objects.requireNonNull(listener, "listener");
        requireClientTick();
        return ClientTickRegistry.END_LEVEL.add(this, listener);
    }

    /**
     * Removes all of this extension's registrations. Called by the manager
     * on disable; Mosaic-internal.
     */
    public void clear() {
        ClientTickRegistry.clear(this);
    }

    private static void requireClientTick() {
        EnvType env;
        try {
            env = FabricLoader.getInstance().getEnvironmentType();
        } catch (RuntimeException e) {
            return;
        }
        if (env != EnvType.CLIENT) {
            throw new IllegalStateException("onClientTick requires the Minecraft client environment");
        }
    }
}
