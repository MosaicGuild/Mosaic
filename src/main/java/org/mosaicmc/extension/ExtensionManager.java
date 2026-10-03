package org.mosaicmc.extension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

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
import org.mosaicmc.internal.ExtensionStorageManager;
import org.mosaicmc.internal.SettingsStore;
import org.slf4j.Logger;

public class ExtensionManager {
    private static final Logger LOGGER = Mosaic.LOGGER;
    private static final Map<String, Extension> EXTENSIONS = new LinkedHashMap<>();
    private static final Map<String, ExtensionEventsImpl> EVENT_BRIDGES = new LinkedHashMap<>();
    private static final Map<String, ExtensionCommandManager> COMMAND_FACADES = new LinkedHashMap<>();
    private static final Map<String, ExtensionSettingsManager> SETTINGS = new LinkedHashMap<>();
    private static final Map<String, ExtensionStorageManager> STORAGES = new LinkedHashMap<>();
    /**
     * Lifecycle states. Guarded by {@link #STATE_LOCK}, which is held only
     * for state reads and transitions — never while running extension
     * callbacks, so a misbehaving extension cannot deadlock the manager.
     */
    private static final Map<String, LifecycleState> STATES = new LinkedHashMap<>();
    private static final Object STATE_LOCK = new Object();
    private static volatile ExtensionScheduler scheduler = Runnable::run;
    private static volatile SettingsStore settingsStore;

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
            ExtensionStorageManager storage = new ExtensionStorageManager();

            try {
                extension.setContext(new ExtensionContextImpl(scheduler, events, commands, settings, storage));
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
            STORAGES.put(id, storage);

            try {
                extension.onLoad();
            } catch (Exception e) {
                LOGGER.error("Extension {} failed onLoad, unregistering", id, e);
                events.clear();
                commands.clear();
                EXTENSIONS.remove(id);
                EVENT_BRIDGES.remove(id);
                COMMAND_FACADES.remove(id);
                SETTINGS.remove(id);
                STORAGES.remove(id);
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
        STORAGES.clear();
        settingsStore = null;
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
                ExtensionStorageManager storage = STORAGES.computeIfAbsent(
                        entry.getKey(), key -> new ExtensionStorageManager());
                entry.getValue().setContext(new ExtensionContextImpl(scheduler, events, commands, settings, storage));
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
     * Owner ids with attached settings managers, in discovery order.
     * Mosaic-internal: used by the settings store to enumerate persistence.
     *
     * @return an unmodifiable snapshot, never {@code null}
     */
    public static List<String> settingOwnerIds() {
        return List.copyOf(SETTINGS.keySet());
    }

    /**
     * Owner ids with attached storage managers, in discovery order.
     * Mosaic-internal: used by the settings store to enumerate persistence.
     *
     * @return an unmodifiable snapshot, never {@code null}
     */
    public static List<String> storageOwnerIds() {
        return List.copyOf(STORAGES.keySet());
    }

    /**
     * Live storage entries for one extension, for persistence.
     * Mosaic-internal: used by the settings store when saving.
     *
     * @param id the extension id, must not be {@code null}
     * @return a mutable copy in insertion order, or an empty map for
     * unknown ids, never {@code null}
     */
    public static Map<String, String> getStorageEntries(String id) {
        ExtensionStorageManager storage = STORAGES.get(id);
        if (storage == null) {
            return new LinkedHashMap<>();
        }
        return storage.snapshot();
    }

    /**
     * Keys applied by the last startup load, for save merging.
     * Mosaic-internal: used by the settings store when saving.
     *
     * @param id the extension id, must not be {@code null}
     * @return a mutable copy, or an empty set for unknown ids, never
     * {@code null}
     */
    public static Set<String> getStorageLoadedKeys(String id) {
        ExtensionStorageManager storage = STORAGES.get(id);
        if (storage == null) {
            return new LinkedHashSet<>();
        }
        return storage.loadedKeys();
    }

    /**
     * Replaces one extension's storage with file values.
     * Mosaic-internal: called by the settings store during the startup load.
     * Unknown ids are ignored.
     *
     * @param id the extension id, must not be {@code null}
     * @param loaded file values, must not be {@code null}
     */
    public static void applyStorageLoaded(String id, Map<String, String> loaded) {
        ExtensionStorageManager storage = STORAGES.get(id);
        if (storage != null) {
            storage.applyLoaded(loaded);
        }
    }

    /**
     * Attaches the settings store that setting changes are reported to.
     * Mosaic-internal: wired once during startup, cleared by test resets.
     *
     * @param store the store, or {@code null} to detach
     */
    public static void setSettingsStore(SettingsStore store) {
        settingsStore = store;
    }

    /**
     * Records a setting change for later persistence. Mosaic-internal:
     * called by setting implementations on every {@code set()}, covering
     * GUI and programmatic writes alike. Never throws.
     */
    public static void markSettingsDirty() {
        SettingsStore store = settingsStore;
        if (store != null) {
            store.markDirty();
        }
    }

    /**
     * Writes pending setting changes, honoring the store's save interval.
     * Safe to call every tick: returns immediately when nothing is dirty.
     *
     * @return true when the file was replaced
     */
    public static boolean saveSettingsIfDirty() {
        SettingsStore store = settingsStore;
        return store != null && store.saveIfDirty();
    }

    /**
     * Writes all settings immediately, for shutdown flushes.
     *
     * @return true when the file was replaced
     */
    public static boolean saveSettings() {
        SettingsStore store = settingsStore;
        return store != null && store.save();
    }

    /**
     * Ids of currently enabled extensions, in discovery order.
     * Mosaic-internal: used by the settings store to persist lifecycle state.
     *
     * @return an unmodifiable snapshot, never {@code null}
     */
    public static List<String> enabledExtensionIds() {
        List<String> enabled = new ArrayList<>();
        synchronized (STATE_LOCK) {
            for (String id : EXTENSIONS.keySet()) {
                if (stateLocked(id) == LifecycleState.ENABLED) {
                    enabled.add(id);
                }
            }
        }
        return enabled;
    }

    /**
     * Enables every listed extension that is currently registered.
     * Mosaic-internal: called once at startup after persisted settings are
     * restored. Unknown ids are skipped; a failing extension stays disabled
     * per the usual enable semantics.
     *
     * @param ids extension ids to enable, must not be {@code null}
     */
    public static void restoreEnabledState(List<String> ids) {
        Objects.requireNonNull(ids, "ids");
        for (String id : ids) {
            if (id != null && get(id).isPresent()) {
                enable(id);
            }
        }
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
        markSettingsDirty();
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
            markSettingsDirty();
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
