package org.mosaicmc.client.screen.settings;

/**
 * Internal mutable holder for one demo setting value.
 *
 * <p>Temporary and UI-local only: no persistence, no public API.
 *
 * <p>Subclasses may override {@link #get}, {@link #set}, and {@link #reset}
 * to present a live view of an externally owned value (used to bind the
 * public Settings API without copying values).
 *
 * @param <T> value type
 */
public class SettingEntry<T> {
    private final String label;
    private final String description;
    private T value;
    private final T defaultValue;

    public SettingEntry(String label, T defaultValue) {
        this(label, null, defaultValue);
    }

    public SettingEntry(String label, String description, T defaultValue) {
        this.label = label;
        this.description = description;
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        this.value = value;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public void reset() {
        this.value = defaultValue;
    }
}
