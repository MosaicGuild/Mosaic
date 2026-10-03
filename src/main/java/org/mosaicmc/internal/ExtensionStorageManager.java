package org.mosaicmc.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.mosaicmc.api.storage.ExtensionStorage;
import org.mosaicmc.extension.ExtensionManager;

/**
 * One extension's storage facade. Entries live in insertion order; this
 * facade remembers nothing about other extensions, so keys can never
 * collide across them. Reused across context refreshes like the events
 * bridge and the settings manager, so values survive scheduler swaps.
 */
public final class ExtensionStorageManager implements ExtensionStorage {

    private final Map<String, String> entries = new LinkedHashMap<>();
    /**
     * Keys applied by the last startup load. A save starts from the
     * preserved file section, drops exactly these keys, then writes the live
     * entries — so keys deleted after the load stay deleted, while file data
     * Mosaic does not understand (non-string values, newer keys) survives
     * verbatim.
     */
    private final Set<String> loadedKeys = new LinkedHashSet<>();

    @Override
    public synchronized String get(String key, String defaultValue) {
        return entries.getOrDefault(requireKey(key), defaultValue);
    }

    @Override
    public synchronized void put(String key, String value) {
        Objects.requireNonNull(value, "value");
        entries.put(requireKey(key), value);
        ExtensionManager.markSettingsDirty();
    }

    @Override
    public synchronized boolean contains(String key) {
        return entries.containsKey(requireKey(key));
    }

    @Override
    public synchronized boolean remove(String key) {
        boolean removed = entries.remove(requireKey(key)) != null;
        if (removed) {
            ExtensionManager.markSettingsDirty();
        }
        return removed;
    }

    @Override
    public synchronized List<String> keys() {
        return List.copyOf(entries.keySet());
    }

    @Override
    public synchronized void clear() {
        if (!entries.isEmpty()) {
            entries.clear();
            ExtensionManager.markSettingsDirty();
        }
    }

    /**
     * Live entries for the settings store to persist.
     * Mosaic-internal; called by the extension manager when saving.
     *
     * @return a mutable copy in insertion order, never {@code null}
     */
    public synchronized Map<String, String> snapshot() {
        return new LinkedHashMap<>(entries);
    }

    /**
     * Keys applied by the last startup load, for save merging.
     * Mosaic-internal; called by the extension manager when saving.
     *
     * @return a mutable copy, never {@code null}
     */
    public synchronized Set<String> loadedKeys() {
        return new LinkedHashSet<>(loadedKeys);
    }

    /**
     * Replaces all contents with file values. Mosaic-internal; called once
     * by the settings store during the startup load, after extensions
     * declared everything in {@code onLoad} and before anything is enabled.
     * Startup restore wins: values written before the load (for example in
     * {@code onLoad}) are overwritten by the file.
     *
     * @param loaded file values, must not be {@code null}
     */
    public synchronized void applyLoaded(Map<String, String> loaded) {
        Objects.requireNonNull(loaded, "loaded");
        entries.clear();
        entries.putAll(loaded);
        loadedKeys.clear();
        loadedKeys.addAll(loaded.keySet());
    }

    /**
     * Merges live entries over a preserved file section for one save.
     * Starts from the raw section (keeps non-string values and keys Mosaic
     * never applied), drops every key the last load applied (a delete-after-
     * load must stay deleted), then writes every live entry.
     *
     * @param live current entries, must not be {@code null}
     * @param loadedKeys keys the last load applied, must not be {@code null}
     * @param raw preserved file section, may be {@code null}
     * @return the section to write, never {@code null}
     */
    static JsonObject sectionFor(Map<String, String> live, Set<String> loadedKeys,
            JsonObject raw) {
        Objects.requireNonNull(live, "live");
        Objects.requireNonNull(loadedKeys, "loadedKeys");
        JsonObject section = raw == null ? new JsonObject() : raw.deepCopy();
        for (String key : loadedKeys) {
            if (!live.containsKey(key)) {
                section.remove(key);
            }
        }
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : section.entrySet()) {
            if (live.containsKey(entry.getKey())) {
                stale.add(entry.getKey());
            }
        }
        for (String key : stale) {
            section.remove(key);
        }
        for (Map.Entry<String, String> entry : live.entrySet()) {
            section.addProperty(entry.getKey(), entry.getValue());
        }
        return section;
    }

    private static String requireKey(String key) {
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("storage key must not be blank");
        }
        return key;
    }
}
