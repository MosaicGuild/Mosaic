package org.mosaicmc.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mosaicmc.api.ExtensionMetadata;
import org.mosaicmc.api.storage.ExtensionStorage;
import org.mosaicmc.internal.SettingsStore;

/**
 * Persistence for the storage API: save/reload round-trips, dirty marking,
 * deletion survival, unknown-data preservation, and lifecycle behavior.
 *
 * <p>Every test uses a store pointed at a JUnit temporary directory —
 * never the developer's real configuration directory.
 */
class StoragePersistenceTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void clearRegistry() {
        ExtensionManager.resetForTesting();
    }

    @Test
    void saveReloadRoundTrip() throws Exception {
        Path file = settingsFile();
        StorageExtension extension = initWaypoints();
        ExtensionStorage storage = extension.getContext().getStorage();
        storage.put("nickname", "home");
        storage.put("waypoints", "home|1|64|2|minecraft:overworld");
        assertTrue(store(file).save());

        StorageExtension restarted = restart(file);
        ExtensionStorage reloaded = restarted.getContext().getStorage();

        assertEquals("home", reloaded.get("nickname", "none"));
        assertEquals("home|1|64|2|minecraft:overworld", reloaded.get("waypoints", "none"));
    }

    @Test
    void missingFileLoadsEmpty() {
        StorageExtension extension = initWaypoints();
        SettingsStore store = store(settingsFile());

        store.load();

        assertEquals("none", extension.getContext().getStorage().get("nickname", "none"));
        assertEquals(List.of("probe"), extension.getContext().getStorage().keys(),
                "only the onLoad write exists; the missing file adds nothing");
        assertFalse(Files.exists(settingsFile()), "load must not create the file");
    }

    @Test
    void putMarksDirtyAndSaveIfDirtyWrites() throws Exception {
        StorageExtension extension = initWaypoints();
        SettingsStore store = new SettingsStore(settingsFile(), 0);
        ExtensionManager.setSettingsStore(store);

        assertFalse(store.isDirty(), "registration alone must not mark dirty");
        extension.getContext().getStorage().put("nickname", "home");

        assertTrue(store.isDirty(), "put() must report through the storage model");
        assertTrue(store.saveIfDirty());
        assertFalse(store.isDirty());

        StorageExtension restarted = restart(settingsFile());
        assertEquals("home", restarted.getContext().getStorage().get("nickname", "none"));
    }

    @Test
    void removePersistsDeletion() throws Exception {
        Path file = settingsFile();
        StorageExtension extension = initWaypoints();
        ExtensionStorage storage = extension.getContext().getStorage();
        storage.put("keep", "yes");
        storage.put("drop", "gone");
        SettingsStore saved = store(file);
        assertTrue(saved.save());

        // Reload through the same store so the save below must merge
        // against preserved file state, not just live entries.
        saved.load();
        assertTrue(storage.remove("drop"));
        assertTrue(saved.save());

        StorageExtension restarted = restart(file);
        ExtensionStorage reloaded = restarted.getContext().getStorage();
        assertEquals("yes", reloaded.get("keep", "none"));
        assertEquals("none", reloaded.get("drop", "none"),
                "a key deleted after the load must stay deleted");
    }

    @Test
    void unknownOwnerPreserved() throws Exception {
        writeFile("""
                {"version": 1, "storage": {
                  "ghost": {"x": "1"},
                  "waypoints": {"nickname": "home"}
                }}
                """);
        StorageExtension extension = initWaypoints();
        SettingsStore store = store(settingsFile());
        store.load();

        assertEquals("home", extension.getContext().getStorage().get("nickname", "none"));
        extension.getContext().getStorage().put("nickname", "portal");
        assertTrue(store.save());

        JsonObject storage = JsonParser.parseString(readFile()).getAsJsonObject()
                .getAsJsonObject("storage");
        assertEquals("1", storage.getAsJsonObject("ghost").get("x").getAsString(),
                "temporarily unavailable extension data must survive");
        assertEquals("portal", storage.getAsJsonObject("waypoints").get("nickname").getAsString());
    }

    @Test
    void nonStringAndBlankKeysSkippedButPreserved() throws Exception {
        writeFile("""
                {"version": 1, "storage": {
                  "waypoints": {"ok": "v", "num": 42, "": "blank"}
                }}
                """);
        StorageExtension extension = initWaypoints();
        SettingsStore store = store(settingsFile());
        store.load();
        ExtensionStorage storage = extension.getContext().getStorage();

        assertEquals("v", storage.get("ok", "none"), "valid sibling still applies");
        assertEquals("none", storage.get("num", "none"), "non-string values are unreadable");
        assertThrows(IllegalArgumentException.class, () -> storage.contains(""),
                "blank keys are rejected at the API level, never stored");

        assertTrue(store.save());
        JsonObject section = JsonParser.parseString(readFile()).getAsJsonObject()
                .getAsJsonObject("storage").getAsJsonObject("waypoints");
        assertEquals(42, section.get("num").getAsInt(), "unreadable values must survive verbatim");
        assertEquals("blank", section.get("").getAsString(), "blank keys must survive verbatim");
        assertEquals("v", section.get("ok").getAsString());
    }

    @Test
    void nonObjectOwnerSectionDroppedWithoutBlockingOthers() throws Exception {
        writeFile("""
                {"version": 1, "storage": {
                  "broken": "oops",
                  "waypoints": {"ok": "v"}
                }}
                """);
        StorageExtension extension = initWaypoints();
        SettingsStore store = store(settingsFile());
        store.load();

        assertEquals("v", extension.getContext().getStorage().get("ok", "none"),
                "valid sibling still applies");

        assertTrue(store.save());
        JsonObject storage = JsonParser.parseString(readFile()).getAsJsonObject()
                .getAsJsonObject("storage");
        assertTrue(storage.getAsJsonObject("waypoints").has("ok"));
        assertFalse(storage.has("broken"), "malformed sections cannot round-trip");
    }

    @Test
    void nonObjectStorageFieldIgnoredAndBackedUp() throws Exception {
        writeFile("""
                {"version": 1, "storage": [], "extensions": {}}
                """);
        StorageExtension extension = initWaypoints();
        SettingsStore store = store(settingsFile());
        store.load();

        assertEquals(List.of("probe"), extension.getContext().getStorage().keys(),
                "only the onLoad write exists; the bad field adds nothing");
        assertTrue(Files.exists(tempDir.resolve("mosaic/settings.json.corrupt.bak")),
                "original preserved before the bad field can be replaced");
    }

    @Test
    void oldFileWithoutStorageLoadsFine() throws Exception {
        writeFile("""
                {"version": 1, "extensions": {}}
                """);
        StorageExtension extension = initWaypoints();
        SettingsStore store = store(settingsFile());
        store.load();

        assertEquals("none", extension.getContext().getStorage().get("nickname", "none"));

        assertTrue(store.save());
        JsonObject storage = JsonParser.parseString(readFile()).getAsJsonObject()
                .getAsJsonObject("storage");
        assertEquals("onload", storage.getAsJsonObject("waypoints").get("probe").getAsString(),
                "saves gain the additive storage section");
    }

    @Test
    void storageLoadsBeforeEnable() throws Exception {
        writeFile("""
                {"version": 1, "storage": {"waypoints": {"nickname": "restored"}}}
                """);
        StorageExtension extension = initWaypoints();
        store(settingsFile()).load();

        ExtensionManager.enable("waypoints");

        assertEquals("restored", extension.seenNickname,
                "onEnable must see the restored value, not the default");
    }

    @Test
    void preLoadPutLosesToFileButStaysConsistent() throws Exception {
        // Boundary semantics, mirroring settings: onLoad runs before the
        // startup load, so values written there are older than the persisted
        // state and the file wins — deterministically.
        writeFile("""
                {"version": 1, "storage": {"waypoints": {"probe": "file"}}}
                """);
        StorageExtension extension = initWaypoints();
        SettingsStore store = new SettingsStore(settingsFile(), 0);
        ExtensionManager.setSettingsStore(store);

        assertEquals("onload", extension.getContext().getStorage().get("probe", "none"),
                "onLoad write is visible before the load");

        store.load();

        assertEquals("file", extension.getContext().getStorage().get("probe", "none"),
                "startup restore wins over pre-load writes");
    }

    @Test
    void repeatedLoadSaveIsStable() throws Exception {
        writeFile("""
                {"version": 1, "storage": {
                  "ghost": {"x": "1"},
                  "waypoints": {"nickname": "home", "num": 42}
                }}
                """);
        initWaypoints();
        Path file = settingsFile();
        SettingsStore stable = store(file);
        stable.load();

        assertTrue(stable.save());
        byte[] first = Files.readAllBytes(file);
        stable.load();
        assertTrue(stable.save());
        byte[] second = Files.readAllBytes(file);

        assertEquals(new String(first, StandardCharsets.UTF_8), new String(second, StandardCharsets.UTF_8));
        JsonObject storage = JsonParser.parseString(new String(second, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("storage");
        assertEquals("1", storage.getAsJsonObject("ghost").get("x").getAsString());
        assertEquals(42, storage.getAsJsonObject("waypoints").get("num").getAsInt());
    }

    @Test
    void valuesSurviveDisableAndIsolation() throws Exception {
        StorageExtension alpha = initExtension("alpha");
        StorageExtension beta = initExtension("beta");
        alpha.getContext().getStorage().put("shared", "a");
        beta.getContext().getStorage().put("shared", "b");
        Path file = settingsFile();
        assertTrue(store(file).save());

        ExtensionManager.enable("alpha");
        ExtensionManager.disable("alpha");

        assertEquals("a", alpha.getContext().getStorage().get("shared", "none"),
                "disable must not delete storage");

        ExtensionManager.resetForTesting();
        StorageExtension restartedAlpha = initExtension("alpha");
        initExtension("beta");
        store(file).load();

        assertEquals("a", restartedAlpha.getContext().getStorage().get("shared", "none"));
        assertEquals("b", ExtensionManager.get("beta").orElseThrow().getContext()
                .getStorage().get("shared", "none"));
    }

    private Path settingsFile() {
        return tempDir.resolve("mosaic/settings.json");
    }

    private SettingsStore store(Path file) {
        SettingsStore store = new SettingsStore(file, 0);
        ExtensionManager.setSettingsStore(store);
        return store;
    }

    private void writeFile(String content) throws IOException {
        Path file = settingsFile();
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private String readFile() throws IOException {
        return Files.readString(settingsFile(), StandardCharsets.UTF_8);
    }

    private StorageExtension initWaypoints() {
        return initExtension("waypoints");
    }

    private StorageExtension initExtension(String id) {
        StorageExtension extension = new StorageExtension(id);
        ExtensionManager.init(fakeLoader(List.of(entrypoint("mod-" + id, extension))));
        return extension;
    }

    private StorageExtension restart(Path file) {
        ExtensionManager.resetForTesting();
        StorageExtension extension = new StorageExtension("waypoints");
        ExtensionManager.init(fakeLoader(List.of(entrypoint("mod", extension))));
        store(file).load();
        return extension;
    }

    private static FabricLoader fakeLoader(List<EntrypointContainer<Extension>> containers) {
        return fake(FabricLoader.class, Map.of("getEntrypointContainers", containers));
    }

    private static EntrypointContainer<Extension> entrypoint(String modId, Extension extension) {
        return fake(EntrypointContainer.class, Map.of(
                "getProvider", fake(ModContainer.class, Map.of(
                        "getMetadata", fake(ModMetadata.class, Map.of("getId", modId)))),
                "getEntrypoint", extension));
    }

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, Map<String, Object> stubs) {
        Map<String, Object> values = new HashMap<>(stubs);
        return (T) Proxy.newProxyInstance(
                StoragePersistenceTest.class.getClassLoader(),
                new Class<?>[] { type },
                (proxy, method, args) -> {
                    if (method.getName().equals("toString")) {
                        return "fake(" + type.getSimpleName() + ")";
                    }
                    if (method.getName().equals("hashCode")) {
                        return System.identityHashCode(proxy);
                    }
                    if (method.getName().equals("equals")) {
                        return proxy == args[0];
                    }
                    if (values.containsKey(method.getName())) {
                        return values.get(method.getName());
                    }
                    return null;
                });
    }

    private static final class StorageExtension extends Extension {
        private final String id;
        volatile String seenNickname;

        StorageExtension(String id) {
            this.id = id;
        }

        @Override
        public ExtensionMetadata getMetadata() {
            return new ExtensionMetadata() {
                @Override
                public String getId() {
                    return id;
                }

                @Override
                public String getName() {
                    return "Storage " + id;
                }

                @Override
                public String getDescription() {
                    return "Storage test extension.";
                }

                @Override
                public String getVersion() {
                    return "0.0.0-test";
                }

                @Override
                public String getAuthors() {
                    return "tests";
                }

                @Override
                public String getWebsite() {
                    return "";
                }
            };
        }

        @Override
        public void onLoad() {
            // Pre-load write: proves the startup load deterministically
            // overwrites values older than the persisted state.
            getContext().getStorage().put("probe", "onload");
        }

        @Override
        public void onEnable() {
            seenNickname = getContext().getStorage().get("nickname", "none");
        }

        @Override
        public void onDisable() {
        }
    }
}
