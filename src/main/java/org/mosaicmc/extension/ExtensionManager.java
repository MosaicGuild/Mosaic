package org.mosaicmc.extension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import org.mosaicmc.Mosaic;
import org.mosaicmc.api.ExtensionScheduler;
import org.mosaicmc.api.settings.Setting;
import org.mosaicmc.internal.ClientTickRegistry;
import org.mosaicmc.internal.CommandTree;
import org.mosaicmc.internal.ExtensionCommandManager;
import org.mosaicmc.internal.ExtensionContextImpl;
import org.mosaicmc.internal.ExtensionEventsImpl;
import org.mosaicmc.internal.ExtensionSettingsManager;
import org.slf4j.Logger;

public class ExtensionManager {
    private static final Logger LOGGER = Mosaic.LOGGER;
    private static final Map<String, Extension> EXTENSIONS = new LinkedHashMap<>();
    private static final Map<String, ExtensionEventsImpl> EVENT_BRIDGES = new LinkedHashMap<>();
    private static final Map<String, ExtensionCommandManager> COMMAND_FACADES = new LinkedHashMap<>();
    private static final Map<String, ExtensionSettingsManager> SETTINGS = new LinkedHashMap<>();
    /**
     * Lifecycle states. Guarded by {@link #STATE_LOCK}, which is held only
     * for state reads and transitions — never while running extension
     * callbacks, so a misbehaving extension cannot deadlock the manager.
     */
    private static final Map<String, LifecycleState> STATES = new LinkedHashMap<>();
    private static final Object STATE_LOCK = new Object();
    private static volatile ExtensionScheduler scheduler = Runnable::run;

    /**
     * Minimal per-extension lifecycle. Transient states exist so concurrent
     * enable/disable calls resolve atomically: exactly one caller wins the
     * transition and runs the callback, the losers return without effect.
     */
    private enum LifecycleState {
        DISABLED,
        ENABLING,
        ENABLED,
        DISABLING
    }

    /** Must only be called while holding {@link #STATE_LOCK}. */
    private static LifecycleState stateLocked(String id) {
        LifecycleState state = STATES.get(id);
        return state == null ? LifecycleState.DISABLED : state;
    }

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
            ExtensionSettingsManager settings = new ExtensionSettingsManager();

            try {
                extension.setContext(new ExtensionContextImpl(scheduler, events, commands, settings));
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
            settings.setOwner(id);
            SETTINGS.put(id, settings);

            try {
                extension.onLoad();
            } catch (Exception e) {
                // A failed load must not leave a half-initialized extension
                // behind: drop its bridges (clearing anything it managed to
                // register first) and skip it like any other discovery
                // failure, so it can never be enabled.
                LOGGER.error("Extension {} failed onLoad, unregistering", id, e);
                events.clear();
                commands.clear();
                EXTENSIONS.remove(id);
                EVENT_BRIDGES.remove(id);
                COMMAND_FACADES.remove(id);
                SETTINGS.remove(id);
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
        SETTINGS.clear();
        synchronized (STATE_LOCK) {
            STATES.clear();
        }
        ClientTickRegistry.clearAll();
        CommandTree.clearAll();
        scheduler = Runnable::run;
    }

    /**
     * Replaces the scheduler used for newly created extension contexts and
     * refreshes the context of already registered extensions.
     * The client initializer installs the real client-thread scheduler here;
     * the default simply runs tasks inline(safe for unit tests / servers).
     * Each extension keeps its events bridge, command facade, and settings
     * manager, so registrations and values survive the swap.
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
                ExtensionSettingsManager settings = SETTINGS.computeIfAbsent(
                        entry.getKey(), ExtensionSettingsManager::new);
                entry.getValue().setContext(new ExtensionContextImpl(scheduler, events, commands, settings));
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

    /**
     * Whether the given extension is currently enabled: true only after a
     * successful {@link #enable} without a later {@link #disable}.
     * Unknown ids report false, never throw.
     *
     * @param id the extension id, must not be {@code null}
     * @return true when enabled
     */
    public static boolean isEnabled(String id) {
        synchronized (STATE_LOCK) {
            return stateLocked(id) == LifecycleState.ENABLED;
        }
    }

    /**
     * Every setting the given extension registered, in registration order.
     *
     * <p>Settings are declarations, not listeners: they survive
     * {@link #disable} and are only removed when the extension itself is
     * forgotten (currently only in tests via reset).
     *
     * @param id the extension id, must not be {@code null}
     * @return an unmodifiable snapshot, or an empty list for unknown ids
     */
    public static List<Setting<?>> getSettings(String id) {
        ExtensionSettingsManager settings = SETTINGS.get(id);
        if (settings == null) {
            return List.of();
        }
        return settings.all();
    }

    /**
     * The settings-section title the given extension asked for, if any.
     * Extensions get no sidebar section unless they call
     * {@code getContext().getSettings().registerSection(...)}.
     *
     * @param id the extension id, must not be {@code null}
     * @return the title, or {@link Optional#empty()} for unknown ids or
     * extensions that never asked for a section
     */
    public static Optional<String> getSettingsSection(String id) {
        ExtensionSettingsManager settings = SETTINGS.get(id);
        if (settings == null) {
            return Optional.empty();
        }
        return settings.sectionTitle();
    }

    /**
     * Enables an extension: transitions {@code DISABLED -> ENABLING}, runs
     * {@code onEnable()} outside the state lock, then marks
     * {@code ENABLED}. Calls that lose the transition (already enabled,
     * enabling, or disabling) return without invoking the callback, so
     * repeated or concurrent enables can never double-register.
     *
     * <p>If {@code onEnable()} throws, the error is logged, the commands
     * and events registered during that call are rolled back, and the
     * extension stays disabled so a later enable retries cleanly. Settings
     * are declarations, not enable-scoped resources, and are left alone.
     */
    public static void enable(String id) {
        Extension extension = EXTENSIONS.get(id);

        if (extension == null) {
            LOGGER.error("Cannot enable unknown extension {}", id);
            return;
        }

        synchronized (STATE_LOCK) {
            if (stateLocked(id) != LifecycleState.DISABLED) {
                return;
            }
            STATES.put(id, LifecycleState.ENABLING);
        }

        try {
            extension.onEnable();
        } catch (Exception e) {
            LOGGER.error("Extension {} failed onEnable", id, e);
            clearOwned(id);
            synchronized (STATE_LOCK) {
                STATES.put(id, LifecycleState.DISABLED);
            }
            return;
        }
        synchronized (STATE_LOCK) {
            STATES.put(id, LifecycleState.ENABLED);
        }
    }

    /**
     * Disables an enabled extension: transitions
     * {@code ENABLED -> DISABLING}, runs {@code onDisable()} outside the
     * state lock, then always removes its commands and events and marks
     * {@code DISABLED} — even when {@code onDisable()} throws. Calls that
     * lose the transition return without invoking the callback.
     *
     * <p>Settings survive disable by design.
     */
    public static void disable(String id) {
        Extension extension = EXTENSIONS.get(id);

        if (extension == null) {
            LOGGER.error("Cannot disable unknown extension {}", id);
            return;
        }

        synchronized (STATE_LOCK) {
            if (stateLocked(id) != LifecycleState.ENABLED) {
                return;
            }
            STATES.put(id, LifecycleState.DISABLING);
        }

        try {
            extension.onDisable();
        } catch (Exception e) {
            LOGGER.error("Extension {} failed onDisable", id, e);
        } finally {
            clearOwned(id);
            synchronized (STATE_LOCK) {
                STATES.put(id, LifecycleState.DISABLED);
            }
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
