package org.mosaicmc.extension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import org.mosaicmc.Mosaic;
import org.mosaicmc.api.ExtensionScheduler;
import org.mosaicmc.internal.ClientTickRegistry;
import org.mosaicmc.internal.CommandTree;
import org.mosaicmc.internal.ExtensionCommandManager;
import org.mosaicmc.internal.ExtensionContextImpl;
import org.mosaicmc.internal.ExtensionEventsImpl;
import org.slf4j.Logger;

public class ExtensionManager {
    private static final Logger LOGGER = Mosaic.LOGGER;
    private static final Map<String, Extension> EXTENSIONS = new LinkedHashMap<>();
    private static final Map<String, ExtensionEventsImpl> EVENT_BRIDGES = new LinkedHashMap<>();
    private static final Map<String, ExtensionCommandManager> COMMAND_FACADES = new LinkedHashMap<>();
    private static final Map<String, Boolean> ENABLED = new LinkedHashMap<>();
    private static volatile ExtensionScheduler scheduler = Runnable::run;

    // For the future me; This thing called init is for extension discovery
    public static void init() {
        init(FabricLoader.getInstance());
    }

    static void init(FabricLoader loader) {
        for (EntrypointContainer<Extension> container : loader.getEntrypointContainers("mosaic", Extension.class)) {
            String modId;

            try {
                modId = container.getProvider().getMetadata().getId();
            } catch (Exception e) {
                LOGGER.error("Failed to read mod id for mosaic extension", e);
                continue;
            }

            Extension extension;

            try {
                extension = container.getEntrypoint();
            } catch (Exception e) {
                LOGGER.error("Failed to instantiate extension from {}", modId, e);
                continue;
            }

            ExtensionEventsImpl events = new ExtensionEventsImpl();
            ExtensionCommandManager commands = new ExtensionCommandManager();

            try {
                extension.setContext(new ExtensionContextImpl(scheduler, events, commands));
            } catch (Exception e) {
                LOGGER.error("Failed to inject context into extension from {}", modId, e);
                continue;
            }

            String id;

            try {
                id = extension.getMetadata().getId();
            } catch (Exception e) {
                LOGGER.error("Extension from {} failed getMetadata(), skipping", modId, e);
                continue;
            }

            if (id == null || id.isBlank()) {
                LOGGER.error("Extension from {} returned invalid metadata id, skipping", modId);
                continue;
            }

            if (EXTENSIONS.containsKey(id)) {
                LOGGER.error("Duplicate extension id {} from {}, skipping", id, modId);
                continue;
            }

            EXTENSIONS.put(id, extension);
            EVENT_BRIDGES.put(id, events);
            COMMAND_FACADES.put(id, commands);

            try {
                extension.onLoad();
            } catch (Exception e) {
                LOGGER.error("Extension {} failed onLoad", id, e);
            }
        }
    }

    public static List<Extension> getExtensions() {
        return List.copyOf(EXTENSIONS.values());
    }

    static void resetForTesting() {
        EXTENSIONS.clear();
        EVENT_BRIDGES.clear();
        COMMAND_FACADES.clear();
        ENABLED.clear();
        ClientTickRegistry.clearAll();
        CommandTree.clearAll();
        scheduler = Runnable::run;
    }

    /**
     * Replaces the scheduler used for newly created extension contexts and
     * refreshes the context of already registered extensions.
     * The client initializer installs the real client-thread scheduler here;
     * the default simply runs tasks inline(safe for unit tests / servers).
     * Each extension keeps its events bridge and command facade, so
     * registrations survive the swap.
     */
    public static void setScheduler(ExtensionScheduler scheduler) {
        if (scheduler == null) {
            throw new IllegalArgumentException("scheduler must not be null");
        }
        ExtensionManager.scheduler = scheduler;
        int refreshed = 0;
        for (Map.Entry<String, Extension> entry : EXTENSIONS.entrySet()) {
            try {
                ExtensionEventsImpl events = EVENT_BRIDGES.computeIfAbsent(
                        entry.getKey(), key -> new ExtensionEventsImpl());
                ExtensionCommandManager commands = COMMAND_FACADES.computeIfAbsent(
                        entry.getKey(), key -> new ExtensionCommandManager());
                entry.getValue().setContext(new ExtensionContextImpl(scheduler, events, commands));
                refreshed++;
            } catch (Exception e) {
                LOGGER.error("Failed to refresh context", e);
            }
        }
        LOGGER.info("[Mosaic] scheduler set to {} (refreshed {} extension contexts)",
                scheduler.getClass().getSimpleName(), refreshed);
    }

    static ExtensionScheduler getScheduler() {
        return scheduler;
    }

    public static Optional<Extension> get(String id) {
        return Optional.ofNullable(EXTENSIONS.get(id));
    }

    public static void enable(String id) {
        Extension extension = EXTENSIONS.get(id);

        if (extension == null) {
            LOGGER.error("Cannot enable unknown extension {}", id);
            return;
        }

        if (Boolean.TRUE.equals(ENABLED.get(id))) {
            return;
        }

        try {
            extension.onEnable();
            ENABLED.put(id, true);
        } catch (Exception e) {
            LOGGER.error("Extension {} failed onEnable", id, e);
            clearOwned(id);
        }
    }

    public static void disable(String id) {
        Extension extension = EXTENSIONS.get(id);

        if (extension == null) {
            LOGGER.error("Cannot disable unknown extension {}", id);
            return;
        }

        if (!Boolean.TRUE.equals(ENABLED.get(id))) {
            return;
        }

        try {
            extension.onDisable();
        } catch (Exception e) {
            LOGGER.error("Extension {} failed onDisable", id, e);
        } finally {
            clearOwned(id);
            ENABLED.put(id, false);
        }
    }

    /**
     * Removes an extension's owned registrations (events and commands),
     * used both as the post-disable safety net and as rollback for a failed
     * enable that registered halfway before throwing.
     */
    private static void clearOwned(String id) {
        ExtensionEventsImpl events = EVENT_BRIDGES.get(id);
        if (events != null) {
            events.clear();
        }
        ExtensionCommandManager commands = COMMAND_FACADES.get(id);
        if (commands != null) {
            commands.clear();
        }
    }
}
