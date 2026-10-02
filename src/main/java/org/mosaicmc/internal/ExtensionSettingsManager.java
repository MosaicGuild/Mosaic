package org.mosaicmc.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.mosaicmc.api.settings.BooleanSetting;
import org.mosaicmc.api.settings.EnumSetting;
import org.mosaicmc.api.settings.IntSetting;
import org.mosaicmc.api.settings.Setting;
import org.mosaicmc.api.settings.Settings;
import org.mosaicmc.extension.ExtensionManager;

/**
 * One extension's settings facade. Settings are keyed by extension-scoped
 * id in registration order; this facade remembers which settings are yours
 * so another extension can never collide with them. Reused across context
 * refreshes like the events bridge, so values survive scheduler swaps.
 */
public final class ExtensionSettingsManager implements Settings {
    private final Map<String, Setting<?>> settings = new LinkedHashMap<>();
    private volatile String owner = "unknown";
    private String sectionTitle;

    /**
     * Load epochs. Every load opens a new one; every change stamps the
     * current one on its setting. A load then applies a file value only to
     * settings untouched during its own epoch, so a change racing the load
     * keeps both its value and its dirty mark. Epoch 0 predates all loads
     * and can never collide with a real load window.
     */
    private static final AtomicLong LOAD_EPOCH = new AtomicLong();

    /** Current load epoch. Mosaic-internal; sampled by setting implementations on every change. */
    static long currentLoadEpoch() {
        return LOAD_EPOCH.get();
    }

    /** Starts a new load window. Mosaic-internal; called by the settings store before applying values. */
    static long nextLoadEpoch() {
        return LOAD_EPOCH.incrementAndGet();
    }

    public ExtensionSettingsManager() {
    }

    public ExtensionSettingsManager(String owner) {
        setOwner(owner);
    }

    /**
     * Records which extension owns this facade. Called by the extension
     * manager once the extension id is known; Mosaic-internal.
     *
     * @param owner the extension id, must not be {@code null} or blank
     */
    public void setOwner(String owner) {
        Objects.requireNonNull(owner, "owner");
        if (owner.isBlank()) {
            throw new IllegalArgumentException("owner must not be blank");
        }
        this.owner = owner;
    }

    @Override
    public synchronized void registerSection(String title) {
        Objects.requireNonNull(title, "title");
        if (title.isBlank()) {
            throw new IllegalArgumentException("section title must not be blank");
        }
        if (sectionTitle != null) {
            throw new IllegalArgumentException(
                    "Extension '" + owner + "' already registered a section");
        }
        sectionTitle = title;
    }

    /**
     * The requested settings-section title, if the extension asked for one.
     *
     * @return the title, or {@link Optional#empty()} when the extension
     * wants no sidebar section
     */
    public synchronized Optional<String> sectionTitle() {
        return Optional.ofNullable(sectionTitle);
    }

    @Override
    public synchronized BooleanSetting registerBoolean(String id, String displayName,
            String description, boolean defaultValue) {
        BooleanSettingImpl setting = new BooleanSettingImpl(
                requireId(id), requireName(displayName), description, defaultValue);
        put(setting);
        return setting;
    }

    @Override
    public synchronized IntSetting registerInt(String id, String displayName,
            String description, int defaultValue, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException(
                    "min (" + min + ") must not exceed max (" + max + ") for setting '" + id + "'");
        }
        if (defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException("default value " + defaultValue
                    + " is outside [" + min + ", " + max + "] for setting '" + id + "'");
        }
        IntSettingImpl setting = new IntSettingImpl(
                requireId(id), requireName(displayName), description, defaultValue, min, max);
        put(setting);
        return setting;
    }

    @Override
    public synchronized <E extends Enum<E>> EnumSetting<E> registerEnum(String id,
            String displayName, String description, E defaultValue) {
        Objects.requireNonNull(defaultValue, "defaultValue");
        EnumSettingImpl<E> setting = new EnumSettingImpl<>(
                requireId(id), requireName(displayName), description, defaultValue);
        put(setting);
        return setting;
    }

    @Override
    public synchronized Optional<Setting<?>> get(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(settings.get(id));
    }

    @Override
    public synchronized List<Setting<?>> all() {
        return List.copyOf(settings.values());
    }

    private void put(Setting<?> setting) {
        if (settings.containsKey(setting.id())) {
            throw new IllegalArgumentException("Extension '" + owner
                    + "' already registered setting '" + setting.id() + "'");
        }
        settings.put(setting.id(), setting);
    }

    private static String requireId(String id) {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("setting id must not be blank");
        }
        return id;
    }

    private static String requireName(String displayName) {
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        return displayName;
    }

    private abstract static class BaseSetting<T> implements Setting<T> {
        private final String id;
        private final String displayName;
        private final String description;
        private final T defaultValue;

        BaseSetting(String id, String displayName, String description, T defaultValue) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.defaultValue = defaultValue;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String displayName() {
            return displayName;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public T defaultValue() {
            return defaultValue;
        }
    }

    static final class BooleanSettingImpl extends BaseSetting<Boolean> implements BooleanSetting {
        private volatile boolean value;
        private long touchedEpoch;

        BooleanSettingImpl(String id, String displayName, String description, boolean defaultValue) {
            super(id, displayName, description, defaultValue);
            this.value = defaultValue;
        }

        @Override
        public Boolean get() {
            return value;
        }

        @Override
        public void set(Boolean value) {
            Objects.requireNonNull(value, "value");
            synchronized (this) {
                this.value = value;
                this.touchedEpoch = currentLoadEpoch();
            }
            ExtensionManager.markSettingsDirty();
        }

        /**
         * Assigns a loaded value unless the setting changed during the
         * given load epoch. The check and the assignment are atomic, so a
         * concurrent change can neither be overwritten nor lose its dirty
         * mark.
         *
         * @return false when skipped because of a concurrent change
         */
        boolean assignLoadedUnlessTouched(boolean value, long epoch) {
            synchronized (this) {
                if (touchedEpoch == epoch) {
                    return false;
                }
                this.value = value;
                return true;
            }
        }
    }

    static final class IntSettingImpl extends BaseSetting<Integer> implements IntSetting {
        private final int min;
        private final int max;
        private volatile int value;
        private long touchedEpoch;

        IntSettingImpl(String id, String displayName, String description,
                int defaultValue, int min, int max) {
            super(id, displayName, description, defaultValue);
            this.min = min;
            this.max = max;
            this.value = defaultValue;
        }

        @Override
        public int min() {
            return min;
        }

        @Override
        public int max() {
            return max;
        }

        @Override
        public Integer get() {
            return value;
        }

        @Override
        public void set(Integer value) {
            Objects.requireNonNull(value, "value");
            synchronized (this) {
                this.value = Math.max(min, Math.min(max, value));
                this.touchedEpoch = currentLoadEpoch();
            }
            ExtensionManager.markSettingsDirty();
        }

        /**
         * Assigns a loaded value unless the setting changed during the
         * given load epoch. The check and the assignment are atomic, so a
         * concurrent change can neither be overwritten nor lose its dirty
         * mark.
         *
         * @return false when skipped because of a concurrent change
         */
        boolean assignLoadedUnlessTouched(int value, long epoch) {
            synchronized (this) {
                if (touchedEpoch == epoch) {
                    return false;
                }
                this.value = Math.max(min, Math.min(max, value));
                return true;
            }
        }
    }

    static final class EnumSettingImpl<E extends Enum<E>> extends BaseSetting<E> implements EnumSetting<E> {
        private final Class<E> type;
        private final List<E> options;
        private volatile E value;
        private long touchedEpoch; // guarded by synchronizing on this

        EnumSettingImpl(String id, String displayName, String description, E defaultValue) {
            super(id, displayName, description, defaultValue);
            this.type = defaultValue.getDeclaringClass();
            this.options = List.of(type.getEnumConstants());
            this.value = defaultValue;
        }

        @Override
        public Class<E> type() {
            return type;
        }

        @Override
        public List<E> options() {
            return options;
        }

        @Override
        public E get() {
            return value;
        }

        @Override
        public void set(E value) {
            Objects.requireNonNull(value, "value");
            synchronized (this) {
                this.value = value;
                this.touchedEpoch = currentLoadEpoch();
            }
            ExtensionManager.markSettingsDirty();
        }

        /**
         * Assigns a loaded value unless the setting changed during the
         * given load epoch. The check and the assignment are atomic, so a
         * concurrent change can neither be overwritten nor lose its dirty
         * mark.
         *
         * @return false when skipped because of a concurrent change
         */
        boolean assignLoadedUnlessTouched(E value, long epoch) {
            Objects.requireNonNull(value, "value");
            synchronized (this) {
                if (touchedEpoch == epoch) {
                    return false;
                }
                this.value = value;
                return true;
            }
        }
    }
}
