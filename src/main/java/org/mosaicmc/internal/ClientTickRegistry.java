package org.mosaicmc.internal;

import java.util.List;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Listener storage for Mosaic-managed client ticks. Lives in the common
 * source set, where Mojang client classes are invisible, so it only
 * <em>declares and stores</em> listener references: implementing or invoking
 * a listener happens in the client source set (the global sink, with the
 * real client or level) or in tests.
 *
 * <p>Entries are owned by the registering events bridge, so disabling an
 * extension removes exactly its own listeners.
 */
public final class ClientTickRegistry {
    static final ManagedEvent<ClientTickEvents.EndTick> END_CLIENT = new ManagedEvent<>();
    static final ManagedEvent<ClientTickEvents.StartTick> START_CLIENT = new ManagedEvent<>();
    static final ManagedEvent<ClientTickEvents.StartLevelTick> START_LEVEL = new ManagedEvent<>();
    static final ManagedEvent<ClientTickEvents.EndLevelTick> END_LEVEL = new ManagedEvent<>();

    private ClientTickRegistry() {
    }

    static void clear(Object owner) {
        END_CLIENT.clear(owner);
        START_CLIENT.clear(owner);
        START_LEVEL.clear(owner);
        END_LEVEL.clear(owner);
    }

    /**
     * Removes every registration. Reset only; never called in normal game
     * operation.
     */
    public static void clearAll() {
        END_CLIENT.clearAll();
        START_CLIENT.clearAll();
        START_LEVEL.clearAll();
        END_LEVEL.clearAll();
    }

    /**
     * Point-in-time copies of every listener, in registration order. Drained
     * each tick by the client source set; driven directly by tests.
     */
    public static List<ClientTickEvents.EndTick> endTickListeners() {
        return END_CLIENT.snapshot();
    }

    /**
     * Point-in-time copies of every listener, in registration order. Drained
     * each tick by the client source set; driven directly by tests.
     */
    public static List<ClientTickEvents.StartTick> startTickListeners() {
        return START_CLIENT.snapshot();
    }

    /**
     * Point-in-time copies of every listener, in registration order. Drained
     * each tick by the client source set; driven directly by tests.
     */
    public static List<ClientTickEvents.StartLevelTick> startLevelTickListeners() {
        return START_LEVEL.snapshot();
    }

    /**
     * Point-in-time copies of every listener, in registration order. Drained
     * each tick by the client source set; driven directly by tests.
     */
    public static List<ClientTickEvents.EndLevelTick> endLevelTickListeners() {
        return END_LEVEL.snapshot();
    }
}
