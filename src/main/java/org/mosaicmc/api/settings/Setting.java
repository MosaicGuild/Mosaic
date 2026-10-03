package org.mosaicmc.api.settings;

/**
 * A single typed setting declared by an extension.
 *
 * <p>Settings are configuration declarations: they hold the current value
 * for the running session and are shown in Mosaic's settings screen.
 * Values are persisted by Mosaic to its settings file, so they survive
 * game restarts; disabling an extension never deletes or resets them.
 * The declaration and read API stays persistence-agnostic, so the backend
 * can evolve without changing extensions.
 *
 * <p>Do not implement this interface; obtain instances from
 * {@link Settings}. Setting identity is stable for the session: repeated
 * lookups of the same id return the same instance.
 *
 * @param <T> the value type
 */
public interface Setting<T> {

    /**
     * The setting id, unique within the owning extension (not globally).
     *
     * @return the id, never {@code null} or blank
     */
    String id();

    /**
     * The display name shown in the settings screen.
     *
     * @return the display name, never {@code null} or blank
     */
    String displayName();

    /**
     * An optional longer explanation shown under the display name.
     *
     * @return the description, or {@code null} when absent
     */
    String description();

    /**
     * The value used before any change.
     *
     * @return the default value, never {@code null}
     */
    T defaultValue();

    /**
     * The current value.
     *
     * @return the current value, never {@code null}
     */
    T get();

    /**
     * Updates the current value. Bounds and nullability are validated the
     * same way no matter who calls: the settings screen, extension code, or
     * a future persistence layer.
     *
     * @param value the new value, must not be {@code null}
     * @throws NullPointerException if {@code value} is {@code null}
     */
    void set(T value);
}
