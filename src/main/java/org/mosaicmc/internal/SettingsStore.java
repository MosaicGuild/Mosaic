package org.mosaicmc.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import org.mosaicmc.Mosaic;
import org.mosaicmc.api.settings.BooleanSetting;
import org.mosaicmc.api.settings.EnumSetting;
import org.mosaicmc.api.settings.IntSetting;
import org.mosaicmc.api.settings.Setting;
import org.mosaicmc.extension.ExtensionManager;

/**
 * Internal session persistence for the Settings API.
 *
 * <p>Single file, human-readable JSON with a version field:
 *
 * <pre>{@code
 * {
 *   "version": 1,
 *   "extensions": {
 *     "mosaic": { "notifications": true, "ui_scale": 100, "theme": "DARK" },
 *     "waypoints": { "enabled": true, "radius": 120 }
 *   },
 *   "storage": {
 *     "waypoints": { "waypoints": "home|12|64|-45|minecraft:overworld\n..." }
 *   },
 *   "enabled": ["waypoints"]
 * }</pre>
 *
 * <p>Keys are stable extension and setting ids only — never display names
 * or class names. Booleans persist as JSON booleans, integers as JSON
 * integers, enums as constant names (never ordinals). The top-level
 * {@code enabled} array lists enabled extension ids; it is optional and a
 * missing field means everything starts disabled. Unknown ids there are
 * preserved verbatim so a temporarily unavailable extension keeps its
 * lifecycle state.
 *
 * <p>Writes go to a temporary file first and replace the previous file
 * only after a complete write, so a failed write never destroys the last
 * known-good configuration. Nothing here is exposed through the public
 * Settings API; extensions keep using {@code get()} / {@code set()}.
 */
public final class SettingsStore {
    /** Schema version written by {@link #save()}. */
    public static final int CURRENT_VERSION = 1;
    /** Owner id used for Mosaic's own core settings section. */
    public static final String CORE_OWNER = "mosaic";
    /** Default minimum gap between dirty-gated saves. */
    public static final long DEFAULT_MIN_SAVE_INTERVAL_MILLIS = 10_000;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final long minIntervalMillis;
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private volatile long lastSaveMillis;
    private volatile boolean frozen;
    /** Last-loaded raw sections, kept to preserve unknown owners/settings across saves. */
    private final Map<String, JsonObject> rawSections = new LinkedHashMap<>();
    /** Last-loaded raw storage sections, kept to preserve unknown owners/values across saves. */
    private final Map<String, JsonObject> rawStorage = new LinkedHashMap<>();
    /** Enabled ids exactly as listed in the file, including unknown ones, for verbatim preservation. */
    private final Set<String> rawEnabledIds = new LinkedHashSet<>();
    /** Enabled ids filtered to currently registered extensions; applied by the startup sequence. */
    private volatile List<String> loadedEnabledIds = List.of();

    public SettingsStore(Path file) {
        this(file, DEFAULT_MIN_SAVE_INTERVAL_MILLIS);
    }

    /**
     * @param file the settings file, must not be {@code null}
     * @param minIntervalMillis minimum gap between {@link #saveIfDirty()}
     * writes; {@code 0} writes on every dirty check (useful in tests)
     */
    public SettingsStore(Path file, long minIntervalMillis) {
        this.file = Objects.requireNonNull(file, "file");
        if (minIntervalMillis < 0) {
            throw new IllegalArgumentException("minIntervalMillis must not be negative");
        }
        this.minIntervalMillis = minIntervalMillis;
    }

    /** Marks pending changes. Called internally whenever a setting value changes. */
    public void markDirty() {
        if (!frozen) {
            dirty.set(true);
        }
    }

    /** Whether unsaved changes are pending. */
    public boolean isDirty() {
        return dirty.get();
    }

    /**
     * Extension ids listed as enabled that are currently registered.
     * Snapshot from the last {@link #load()}; the startup sequence enables
     * them after settings values are restored.
     *
     * @return an unmodifiable snapshot, never {@code null}
     */
    public List<String> loadedEnabledIds() {
        return loadedEnabledIds;
    }

    /**
     * Loads persisted values into the currently registered settings.
     * Runs after declarations exist (extension {@code onLoad}) and before
     * behavior depends on values. Never throws: every failure mode keeps
     * declared defaults and is logged with file context.
     *
     * <p>Concurrency: the load opens a new epoch and applies each file
     * value only to settings untouched during that epoch, atomically per
     * setting. A change racing the load therefore keeps the user's value
     * and its dirty mark, and a later save persists it. Changes made
     * <em>before</em> the load began are deterministically overwritten by
     * the file (startup restore wins). Loading never marks or clears dirty.
     *
     * <p>Malformed files are backed up ({@code <name>.corrupt.bak}) and
     * otherwise left alone.
     */
    public synchronized void load() {
        frozen = false;
        rawSections.clear();
        rawStorage.clear();
        rawEnabledIds.clear();
        loadedEnabledIds = List.of();
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            Mosaic.LOGGER.debug("[Mosaic] no settings file at {}, using defaults", file);
            return;
        } catch (IOException e) {
            Mosaic.LOGGER.warn("[Mosaic] cannot read settings file {}, using defaults", file, e);
            return;
        }
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(content);
        } catch (JsonSyntaxException e) {
            Mosaic.LOGGER.error("[Mosaic] malformed settings file {}, keeping defaults", file, e);
            backupCorruptFile();
            return;
        }
        if (!parsed.isJsonObject()) {
            Mosaic.LOGGER.error("[Mosaic] settings file {} is not a JSON object, keeping defaults", file);
            backupCorruptFile();
            return;
        }
        JsonObject root = parsed.getAsJsonObject();
        int version = CURRENT_VERSION;
        if (root.has("version")) {
            JsonElement rawVersion = root.get("version");
            Integer parsedVersion = parseVersion(rawVersion);
            if (parsedVersion == null) {
                Mosaic.LOGGER.error("[Mosaic] settings file {} has a non-numeric version, keeping defaults",
                        file);
                backupCorruptFile();
                return;
            }
            version = parsedVersion;
        }
        if (version != CURRENT_VERSION) {
            Mosaic.LOGGER.warn(
                    "[Mosaic] settings file {} uses unsupported schema version {}, keeping defaults and disabling saves",
                    file, version);
            frozen = true;
            return;
        }
        JsonObject owners;
        if (!root.has("extensions")) {
            owners = new JsonObject();
        } else if (root.get("extensions").isJsonObject()) {
            owners = root.getAsJsonObject("extensions");
        } else {
            Mosaic.LOGGER.error("[Mosaic] settings file {} has a non-object 'extensions' section, keeping defaults",
                    file);
            backupCorruptFile();
            return;
        }
        if (root.has("enabled")) {
            JsonElement rawEnabled = root.get("enabled");
            if (!rawEnabled.isJsonArray()) {
                Mosaic.LOGGER.error("[Mosaic] settings file {} has a non-array 'enabled' section, ignoring it",
                        file);
                backupCorruptFile();
            } else {
                List<String> present = new ArrayList<>();
                for (String id : parseEnabledIds(rawEnabled.getAsJsonArray())) {
                    rawEnabledIds.add(id);
                    if (ExtensionManager.get(id).isPresent()) {
                        present.add(id);
                    }
                }
                loadedEnabledIds = List.copyOf(present);
            }
        }
        long epoch = ExtensionSettingsManager.nextLoadEpoch();
        for (Map.Entry<String, JsonElement> ownerEntry : owners.entrySet()) {
            String owner = ownerEntry.getKey();
            if (!ownerEntry.getValue().isJsonObject()) {
                Mosaic.LOGGER.warn("[Mosaic] ignoring non-object section for '{}' in {}", owner, file);
                continue;
            }
            JsonObject section = ownerEntry.getValue().getAsJsonObject();
            rawSections.put(owner, section.deepCopy());
            Map<String, Setting<?>> declared = indexById(declaredSettings(owner));
            if (declared.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, JsonElement> valueEntry : section.entrySet()) {
                Setting<?> setting = declared.get(valueEntry.getKey());
                if (setting == null) {
                    continue;
                }
                applyValue(owner, setting, valueEntry.getValue(), epoch);
            }
        }
        loadStorage(root);
    }

    /**
     * Loads the top-level {@code storage} object into the registered
     * extensions' storage managers. The section is optional: older files
     * without it load as empty storage. Non-string values and blank keys are
     * skipped with a warning but preserved verbatim for the next save.
     * Like settings, loading never marks or clears dirty, and startup
     * restore wins over values written before the load.
     */
    private void loadStorage(JsonObject root) {
        if (!root.has("storage")) {
            return;
        }
        if (!root.get("storage").isJsonObject()) {
            Mosaic.LOGGER.error("[Mosaic] settings file {} has a non-object 'storage' section, ignoring it",
                    file);
            backupCorruptFile();
            return;
        }
        JsonObject owners = root.getAsJsonObject("storage");
        for (Map.Entry<String, JsonElement> ownerEntry : owners.entrySet()) {
            String owner = ownerEntry.getKey();
            if (!ownerEntry.getValue().isJsonObject()) {
                Mosaic.LOGGER.warn("[Mosaic] ignoring non-object storage section for '{}' in {}", owner, file);
                continue;
            }
            JsonObject section = ownerEntry.getValue().getAsJsonObject();
            rawStorage.put(owner, section.deepCopy());
            if (!ExtensionManager.storageOwnerIds().contains(owner)) {
                continue;
            }
            Map<String, String> loaded = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> valueEntry : section.entrySet()) {
                String key = valueEntry.getKey();
                JsonElement raw = valueEntry.getValue();
                if (key.isBlank()) {
                    Mosaic.LOGGER.warn("[Mosaic] ignoring blank storage key for '{}' in {}", owner, file);
                    continue;
                }
                if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isString()) {
                    Mosaic.LOGGER.warn("[Mosaic] storage '{}.{}' in {} is not a JSON string, skipping it",
                            owner, key, file);
                    continue;
                }
                loaded.put(key, raw.getAsString());
            }
            ExtensionManager.applyStorageLoaded(owner, loaded);
        }
    }

    /**
     * Writes all current values plus preserved unknown data, atomically.
     *
     * @return true when the file was replaced
     */
    public synchronized boolean save() {
        if (frozen) {
            Mosaic.LOGGER.debug("[Mosaic] skipping settings save (unsupported schema version)");
            return false;
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", CURRENT_VERSION);
        JsonObject owners = new JsonObject();
        root.add("extensions", owners);
        owners.add(CORE_OWNER, sectionFor(MosaicCoreSettings.all(), rawSections.get(CORE_OWNER)));
        for (String owner : ExtensionManager.settingOwnerIds()) {
            owners.add(owner, sectionFor(ExtensionManager.getSettings(owner), rawSections.get(owner)));
        }
        for (Map.Entry<String, JsonObject> unknown : rawSections.entrySet()) {
            if (!unknown.getKey().equals(CORE_OWNER)
                    && !ExtensionManager.settingOwnerIds().contains(unknown.getKey())) {
                owners.add(unknown.getKey(), unknown.getValue().deepCopy());
            }
        }
        JsonObject storageOwners = new JsonObject();
        root.add("storage", storageOwners);
        for (String owner : ExtensionManager.storageOwnerIds()) {
            storageOwners.add(owner, ExtensionStorageManager.sectionFor(
                    ExtensionManager.getStorageEntries(owner),
                    ExtensionManager.getStorageLoadedKeys(owner),
                    rawStorage.get(owner)));
        }
        for (Map.Entry<String, JsonObject> unknown : rawStorage.entrySet()) {
            if (!ExtensionManager.storageOwnerIds().contains(unknown.getKey())) {
                storageOwners.add(unknown.getKey(), unknown.getValue().deepCopy());
            }
        }
        Set<String> enabled = new LinkedHashSet<>(ExtensionManager.enabledExtensionIds());
        for (String id : rawEnabledIds) {
            if (ExtensionManager.get(id).isEmpty()) {
                enabled.add(id);
            }
        }
        JsonArray enabledArray = new JsonArray();
        for (String id : enabled) {
            enabledArray.add(id);
        }
        root.add("enabled", enabledArray);
        String text = GSON.toJson(root);
        try {
            writeAtomically(file, text);
        } catch (IOException | RuntimeException e) {
            Mosaic.LOGGER.warn("[Mosaic] failed to save settings to {}, keeping last known-good file",
                    file, e);
            return false;
        }
        dirty.set(false);
        lastSaveMillis = System.currentTimeMillis();
        return true;
    }

    /**
     * Writes pending changes, at most once per the configured interval.
     *
     * @return true when the file was replaced
     */
    public boolean saveIfDirty() {
        return saveIfDirty(System.currentTimeMillis());
    }

    /**
     * Deterministic overload of {@link #saveIfDirty()} for tests.
     *
     * @param nowMillis the current time in milliseconds
     * @return true when the file was replaced
     */
    public synchronized boolean saveIfDirty(long nowMillis) {
        if (!dirty.get()) {
            return false;
        }
        if (nowMillis - lastSaveMillis < minIntervalMillis) {
            return false;
        }
        boolean written = save();
        if (written) {
            lastSaveMillis = nowMillis;
        }
        return written;
    }

    private static List<Setting<?>> declaredSettings(String owner) {
        if (owner.equals(CORE_OWNER)) {
            return MosaicCoreSettings.all();
        }
        return ExtensionManager.getSettings(owner);
    }

    private static Map<String, Setting<?>> indexById(List<Setting<?>> settings) {
        Map<String, Setting<?>> byId = new LinkedHashMap<>();
        for (Setting<?> setting : settings) {
            byId.put(setting.id(), setting);
        }
        return byId;
    }

    private void applyValue(String owner, Setting<?> setting, JsonElement raw, long epoch) {
        if (setting instanceof ExtensionSettingsManager.BooleanSettingImpl booleanSetting) {
            if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isBoolean()) {
                Mosaic.LOGGER.warn("[Mosaic] setting '{}.{}' in {} is not a JSON boolean, keeping default",
                        owner, setting.id(), file);
                return;
            }
            booleanSetting.assignLoadedUnlessTouched(raw.getAsBoolean(), epoch);
        } else if (setting instanceof ExtensionSettingsManager.IntSettingImpl intSetting) {
            Integer value = parseInteger(raw);
            if (value == null) {
                Mosaic.LOGGER.warn("[Mosaic] setting '{}.{}' in {} is not an integral number, keeping default",
                        owner, setting.id(), file);
                return;
            }
            intSetting.assignLoadedUnlessTouched(value, epoch);
        } else if (setting instanceof ExtensionSettingsManager.EnumSettingImpl<?> enumSetting) {
            applyEnumValue(owner, enumSetting, raw, epoch);
        } else {
            Mosaic.LOGGER.warn("[Mosaic] setting '{}.{}' in {} has an unsupported type {}, keeping default",
                    owner, setting.id(), file, setting.getClass().getName());
        }
    }

    private <E extends Enum<E>> void applyEnumValue(String owner,
            ExtensionSettingsManager.EnumSettingImpl<E> setting, JsonElement raw, long epoch) {
        if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isString()) {
            Mosaic.LOGGER.warn("[Mosaic] setting '{}.{}' in {} is not a JSON string, keeping default",
                    owner, setting.id(), file);
            return;
        }
        String name = raw.getAsString();
        E value;
        try {
            value = Enum.valueOf(setting.type(), name);
        } catch (IllegalArgumentException e) {
            Mosaic.LOGGER.warn("[Mosaic] setting '{}.{}' in {} has unknown enum constant '{}', keeping default '{}'",
                    owner, setting.id(), file, name, setting.defaultValue());
            return;
        }
        setting.assignLoadedUnlessTouched(value, epoch);
    }

    /**
     * Reads the top-level {@code enabled} array, skipping non-string and
     * blank entries with a warning and de-duplicating the rest in order.
     */
    private List<String> parseEnabledIds(JsonArray array) {
        List<String> ids = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                Mosaic.LOGGER.warn("[Mosaic] ignoring non-string entry in 'enabled' section of {}", file);
                continue;
            }
            String id = element.getAsString();
            if (id.isBlank()) {
                Mosaic.LOGGER.warn("[Mosaic] ignoring blank entry in 'enabled' section of {}", file);
                continue;
            }
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static Integer parseVersion(JsonElement raw) {
        if (!raw.isJsonPrimitive() || !raw.getAsJsonPrimitive().isNumber()) {
            return null;
        }
        return parseInteger(raw);
    }

    private static Integer parseInteger(JsonElement raw) {
        double parsed;
        try {
            parsed = raw.getAsDouble();
        } catch (RuntimeException e) {
            return null;
        }
        if (parsed != Math.rint(parsed) || parsed < Long.MIN_VALUE || parsed > Long.MAX_VALUE) {
            return null;
        }
        long value = (long) parsed;
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    private static JsonObject sectionFor(List<Setting<?>> declared, JsonObject raw) {
        JsonObject section = raw == null ? new JsonObject() : raw.deepCopy();
        for (Setting<?> setting : declared) {
            section.remove(setting.id());
            section.add(setting.id(), toJson(setting));
        }
        return section;
    }

    private static JsonElement toJson(Setting<?> setting) {
        if (setting instanceof BooleanSetting booleanSetting) {
            return new JsonPrimitive(booleanSetting.get());
        }
        if (setting instanceof IntSetting intSetting) {
            return new JsonPrimitive(intSetting.get());
        }
        if (setting instanceof EnumSetting<?> enumSetting) {
            return new JsonPrimitive(enumSetting.get().name());
        }
        throw new IllegalArgumentException("Unsupported setting type: " + setting.getClass().getName());
    }

    private static void writeAtomically(Path file, String content) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path targetDir = parent == null ? Path.of(".") : parent;
        Path tmp = targetDir.resolve(file.getFileName() + ".tmp");
        try {
            Files.writeString(tmp, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException | RuntimeException ignored) {
            }
            throw e;
        }
    }

    /**
     * Preserves a malformed settings file under {@code <name>.corrupt.bak}
     * so no later save can silently replace the original with defaults.
     * Best effort: failures are logged and loading continues with defaults.
     */
    private void backupCorruptFile() {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent == null) {
                return;
            }
            Path backup = parent.resolve(file.getFileName() + ".corrupt.bak");
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
            Mosaic.LOGGER.warn("[Mosaic] preserved malformed settings file {} as {}", file, backup);
        } catch (IOException | RuntimeException e) {
            Mosaic.LOGGER.warn("[Mosaic] could not preserve malformed settings file {}", file, e);
        }
    }
}
