package org.mosaicmc.api.storage;

import java.util.List;

/**
 * One extension's private string store, for small custom data that does not
 * fit the typed {@link org.mosaicmc.api.settings.Settings} declarations:
 * last-used values, caches, or serialized collections (for example a
 * waypoint list kept as one multi-line value).
 *
 * <p>Obtain the instance from the extension context; never pass extension
 * ids by hand:
 *
 * <pre>{@code
 * public void onLoad() {
 *     ExtensionStorage storage = getContext().getStorage();
 *     String blob = storage.get("waypoints", "");
 *     if (!blob.isBlank()) {
 *         store.loadFromLines(Arrays.asList(blob.split("\n")));
 *     }
 * }
 *
 * private void persist() {
 *     ExtensionStorage storage = getContext().getStorage();
 *     if (store.isEmpty()) {
 *         storage.remove("waypoints");
 *     } else {
 *         storage.put("waypoints", String.join("\n", store.saveToLines()));
 *     }
 * }
 * }</pre>
 *
 * <p>Keys are scoped to the owning extension: two extensions may each use
 * {@code "selected"} without colliding. Unlike settings, storage needs no
 * declarations: {@code put} creates or overwrites, {@code remove} deletes.
 *
 * <p>Values are persisted by Mosaic to its settings file and survive game
 * restarts. Writes are collected and flushed automatically (periodically and
 * on shutdown); values written before the startup load are overwritten by
 * the file, values written after survive. Disabling an extension never
 * deletes its storage.
 *
 * <p>Do not implement this interface; Mosaic injects the instance.
 * All methods are thread-safe.
 */
public interface ExtensionStorage {

    /**
     * Reads a value.
     *
     * @param key the extension-scoped key, must not be {@code null} or blank
     * @param defaultValue returned when absent, may be {@code null}
     * @return the stored value, or {@code defaultValue} when absent,
     * never {@code null} unless {@code defaultValue} is {@code null}
     * @throws NullPointerException if {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code key} is blank
     */
    String get(String key, String defaultValue);

    /**
     * Stores a value, creating or overwriting the key.
     *
     * @param key the extension-scoped key, must not be {@code null} or blank
     * @param value the value, must not be {@code null} (use
     * {@link #remove} to delete instead)
     * @throws NullPointerException if {@code key} or {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code key} is blank
     */
    void put(String key, String value);

    /**
     * Whether a value is stored under this key.
     *
     * @param key the extension-scoped key, must not be {@code null} or blank
     * @return true when present
     * @throws NullPointerException if {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code key} is blank
     */
    boolean contains(String key);

    /**
     * Deletes a value. Silent if absent: idempotent, never throws for a
     * missing key.
     *
     * @param key the extension-scoped key, must not be {@code null} or blank
     * @return true when something was removed
     * @throws NullPointerException if {@code key} is {@code null}
     * @throws IllegalArgumentException if {@code key} is blank
     */
    boolean remove(String key);

    /**
     * Every stored key, in insertion order.
     *
     * @return an unmodifiable snapshot, never {@code null}
     */
    List<String> keys();

    /**
     * Deletes every stored value. Settings are unaffected: this only clears
     * storage keys.
     */
    void clear();
}
