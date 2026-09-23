package org.mosaicmc.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.mosaicmc.internal.ClientTickRegistry;

/**
 * Mosaic's single hook onto Fabric's client tick events. Each game tick it
 * drains the registry snapshots and forwards the real client or level to
 * every extension listener Mosaic manages.
 *
 * <p>Installed once by {@link MosaicClient}. Per-extension bookkeeping
 * (registration, unregistration, disable cleanup) lives in the common
 * registry; this class only bridges the ticks into it.
 */
final class ManagedClientTick implements ClientTickEvents.StartTick, ClientTickEvents.EndTick,
        ClientTickEvents.StartLevelTick, ClientTickEvents.EndLevelTick {
    private static volatile boolean installed;

    static void installOnce() {
        if (!installed) {
            synchronized (ManagedClientTick.class) {
                if (!installed) {
                    ManagedClientTick sink = new ManagedClientTick();
                    ClientTickEvents.START_CLIENT_TICK.register(sink);
                    ClientTickEvents.END_CLIENT_TICK.register(sink);
                    ClientTickEvents.START_LEVEL_TICK.register(sink);
                    ClientTickEvents.END_LEVEL_TICK.register(sink);
                    installed = true;
                }
            }
        }
    }

    @Override
    public void onStartTick(Minecraft client) {
        for (ClientTickEvents.StartTick listener : ClientTickRegistry.startTickListeners()) {
            listener.onStartTick(client);
        }
    }

    @Override
    public void onEndTick(Minecraft client) {
        for (ClientTickEvents.EndTick listener : ClientTickRegistry.endTickListeners()) {
            listener.onEndTick(client);
        }
    }

    @Override
    public void onStartTick(ClientLevel level) {
        for (ClientTickEvents.StartLevelTick listener : ClientTickRegistry.startLevelTickListeners()) {
            listener.onStartTick(level);
        }
    }

    @Override
    public void onEndTick(ClientLevel level) {
        for (ClientTickEvents.EndLevelTick listener : ClientTickRegistry.endLevelTickListeners()) {
            listener.onEndTick(level);
        }
    }
}
