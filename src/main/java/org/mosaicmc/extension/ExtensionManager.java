package org.mosaicmc.extension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import org.mosaicmc.Mosaic;
import org.mosaicmc.api.ExtensionScheduler;
import org.mosaicmc.internal.ExtensionContextImpl;
import org.slf4j.Logger;

public class ExtensionManager {
    private static final Logger LOGGER = Mosaic.LOGGER;
    private static final Map<String, Extension> EXTENSIONS = new LinkedHashMap<>();
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

            try {
                extension.setContext(new ExtensionContextImpl(scheduler));
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
        scheduler = Runnable::run;
    }

    /**
     * Replaces the scheduler used for newly created extension contexts and
     * refreshes the context of already registered extensions.
     * The client initializer installs the real client-thread scheduler here;
     * the default simply runs tasks inline (safe for unit tests / servers).
     */
    public static void setScheduler(ExtensionScheduler scheduler) {
        if (scheduler == null) {
            throw new IllegalArgumentException("scheduler must not be null");
        }
        ExtensionManager.scheduler = scheduler;
        int refreshed = 0;
        for (Extension extension : EXTENSIONS.values()) {
            try {
                extension.setContext(new ExtensionContextImpl(scheduler));
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

        try {
            extension.onEnable();
        } catch (Exception e) {
            LOGGER.error("Extension {} failed onEnable", id, e);
        }
    }

    public static void disable(String id) {
        Extension extension = EXTENSIONS.get(id);

        if (extension == null) {
            LOGGER.error("Cannot disable unknown extension {}", id);
            return;
        }

        try {
            extension.onDisable();
        } catch (Exception e) {
            LOGGER.error("Extension {} failed onDisable", id, e);
        }
    }
}
