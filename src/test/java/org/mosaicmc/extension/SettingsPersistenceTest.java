package org.mosaicmc.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
import org.mosaicmc.api.settings.BooleanSetting;
import org.mosaicmc.api.settings.EnumSetting;
import org.mosaicmc.api.settings.IntSetting;
import org.mosaicmc.internal.MosaicCoreSettings;
import org.mosaicmc.internal.SettingsStore;

/**
 * Persistence for the Settings API: save/reload round-trips, load ordering
 * before {@code onEnable}, validation of stored data, unknown-data
 * preservation, corruption safety, and survival across lifecycle events.
 *
 * <p>Every test uses a store pointed at a JUnit temporary directory —
 * never the developer's real configuration directory.
 */
class SettingsPersistenceTest {

    enum Probe {
        A,
        B
    }

    @TempDir
    Path tempDir;

    @BeforeEach
    void clearRegistry() {
        ExtensionManager.resetForTesting();
    }

    @Test
    void saveReloadBoolean() throws Exception {
        Path file = settingsFile();
        PersistExtension extension = initWaypoints();
        extension.flag.set(false);
        assertTrue(store(file).save());

        PersistExtension restarted = restart(file);

        assertEquals(false, restarted.flag.get());
    }

    @Test
    void saveReloadInt() throws Exception {
        Path file = settingsFile();
        PersistExtension extension = initWaypoints();
        extension.level.set(9);
        assertTrue(store(file).save());

        PersistExtension restarted = restart(file);

        assertEquals(9, restarted.level.get());
    }

    @Test
    void saveReloadEnum() throws Exception {
        Path file = settingsFile();
        PersistExtension extension = initWaypoints();
        extension.mode.set(Probe.B);
        assertTrue(store(file).save());

        PersistExtension restarted = restart(file);

        assertEquals(Probe.B, restarted.mode.get());
    }

    @Test
    void missingFileUsesDefaults() {
        PersistExtension extension = initWaypoints();
        SettingsStore store = store(settingsFile());

        store.load();

        assertEquals(true, extension.flag.get());
        assertEquals(5, extension.level.get());
        assertEquals(Probe.A, extension.mode.get());
        assertFalse(Files.exists(settingsFile()), "load must not create the file");
    }

    @Test
    void missingSettingUsesDefault() throws Exception {
        writeFile("""
                {"version": 1, "extensions": {"waypoints": {"flag": false}}}
                """);
        PersistExtension extension = initWaypoints();
        store(settingsFile()).load();

        assertEquals(false, extension.flag.get());
        assertEquals(5, extension.level.get(), "absent setting keeps its default");
        assertEquals(Probe.A, extension.mode.get(), "absent setting keeps its default");
    }

    @Test
    void extensionIdsIsolateValues() throws Exception {
        PersistExtension alpha = initExtension("alpha");
        PersistExtension beta = initExtension("beta");
        alpha.flag.set(false);
        beta.flag.set(true);
        Path file = settingsFile();
        assertTrue(store(file).save());

        ExtensionManager.resetForTesting();
        PersistExtension restartedAlpha = initExtension("alpha");
        PersistExtension restartedBeta = initExtension("beta");
        store(file).load();

        assertEquals(false, restartedAlpha.flag.get());
        assertEquals(true, restartedBeta.flag.get());
    }

    @Test
    void savedValuesLoadAfterDeclarationBeforeEnable() throws Exception {
        writeFile("""
                {"version": 1, "extensions": {"waypoints": {"flag": false, "level": 9, "mode": "B"}}}
                """);
        PersistExtension extension = initWaypoints();
        store(settingsFile()).load();

        ExtensionManager.enable("waypoints");

        assertEquals(false, extension.seenFlag, "onEnable must see the restored value, not the default");
        assertEquals(9, extension.seenLevel);
        assertEquals(Probe.B, extension.seenMode);
    }

    @Test
    void programmaticSetIsPersisted() throws Exception {
        PersistExtension extension = initWaypoints();
        SettingsStore store = new SettingsStore(settingsFile(), 0);
        ExtensionManager.setSettingsStore(store);

        assertFalse(store.isDirty(), "registration alone must not mark dirty");
        extension.flag.set(false);

        assertTrue(store.isDirty(), "set() must report through the setting model");
        assertTrue(store.saveIfDirty());
        assertFalse(store.isDirty());

        PersistExtension restarted = restart(settingsFile());
        assertEquals(false, restarted.flag.get());
    }

    @Test
    void intBoundsAppliedOnLoad() throws Exception {
        PersistExtension extension = initWaypoints();

        writeFile("""
                {"version": 1, "extensions": {"waypoints": {"level": 999}}}
                """);
        store(settingsFile()).load();
        assertEquals(10, extension.level.get(), "over-maximum value clamps to max");

        writeFile("""
                {"version": 1, "extensions": {"waypoints": {"level": -5}}}
                """);
        store(settingsFile()).load();
        assertEquals(0, extension.level.get(), "under-minimum value clamps to min");
    }

    @Test
    void invalidEnumFallsBackWithoutBlockingOthers() throws Exception {
        writeFile("""
                {"version": 1, "extensions": {"waypoints": {"flag": false, "mode": "NOPE"}}}
                """);
        PersistExtension extension = initWaypoints();
        store(settingsFile()).load();

        assertEquals(Probe.A, extension.mode.get(), "unknown constant falls back to default");
        assertEquals(false, extension.flag.get(), "valid sibling still applies");
    }

    @Test
    void wrongJsonTypesDoNotBlockSiblings() throws Exception {
        writeFile("""
                {"version": 1, "extensions": {"waypoints": {"flag": false, "level": true, "mode": 3}}}
                """);
        PersistExtension extension = initWaypoints();
        store(settingsFile()).load();

        assertEquals(5, extension.level.get(), "boolean where an integer belongs keeps default");
        assertEquals(Probe.A, extension.mode.get(), "number where a string belongs keeps default");
        assertEquals(false, extension.flag.get(), "valid sibling still applies");
    }

    @Test
    void unknownExtensionAndSettingDataPreserved() throws Exception {
        writeFile("""
                {"version": 1, "extensions": {
                  "ghost": {"x": 1},
                  "waypoints": {"flag": false, "future": "kept"}
                }}
                """);
        PersistExtension extension = initWaypoints();
        SettingsStore store = store(settingsFile());
        store.load();

        assertEquals(false, extension.flag.get());
        extension.flag.set(true);
        assertTrue(store.save());

        JsonObject root = JsonParser.parseString(readFile()).getAsJsonObject();
        JsonObject owners = root.getAsJsonObject("extensions");
        assertEquals(1, owners.getAsJsonObject("ghost").get("x").getAsInt(),
                "temporarily unavailable extension data must survive");
        assertEquals("kept", owners.getAsJsonObject("waypoints").get("future").getAsString(),
                "unknown setting ids must survive");
        assertEquals(true, owners.getAsJsonObject("waypoints").get("flag").getAsBoolean());
    }

    @Test
    void malformedJsonKeepsDefaultsAndFile() throws Exception {
        writeFile("{not valid json!!!");
        byte[] before = Files.readAllBytes(settingsFile());
        PersistExtension extension = initWaypoints();
        SettingsStore store = new SettingsStore(settingsFile(), 0);
        ExtensionManager.setSettingsStore(store);

        store.load();

        assertEquals(true, extension.flag.get());
        assertEquals(before.length, Files.readAllBytes(settingsFile()).length);
        assertEquals(new String(before, StandardCharsets.UTF_8), readFile());
        assertFalse(store.saveIfDirty(), "malformed load must never mark dirty");
        assertEquals(new String(before, StandardCharsets.UTF_8), readFile(),
                "original file must be byte-identical");
    }

    @Test
    void unsupportedVersionKeepsDefaultsAndDisablesSaves() throws Exception {
        writeFile("""
                {"version": 99, "extensions": {"waypoints": {"flag": false}}}
                """);
        byte[] before = Files.readAllBytes(settingsFile());
        PersistExtension extension = initWaypoints();
        SettingsStore store = new SettingsStore(settingsFile(), 0);
        ExtensionManager.setSettingsStore(store);
        store.load();

        assertEquals(true, extension.flag.get(), "unknown schema keeps in-memory defaults");
        extension.flag.set(false);
        assertFalse(store.saveIfDirty(), "unknown schema must refuse to write");
        assertFalse(store.save(), "unknown schema must refuse to write");
        assertEquals(new String(before, StandardCharsets.UTF_8), readFile(),
                "newer file must never be clobbered");
    }

    @Test
    void failedWritePreservesGoodFile() throws Exception {
        Path file = settingsFile();
        PersistExtension extension = initWaypoints();
        extension.flag.set(false);
        assertTrue(store(file).save());
        byte[] good = Files.readAllBytes(file);

        Path blocker = tempDir.resolve("blocker");
        Files.writeString(blocker, "x", StandardCharsets.UTF_8);
        SettingsStore broken = new SettingsStore(blocker.resolve("settings.json"), 0);
        ExtensionManager.setSettingsStore(broken);

        assertFalse(broken.save(), "write into a non-directory parent must fail, not throw");
        assertEquals(new String(good, StandardCharsets.UTF_8),
                Files.readString(file, StandardCharsets.UTF_8),
                "last known-good file must be byte-identical");
        try (Stream<Path> entries = Files.list(tempDir)) {
            assertTrue(entries.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")),
                    "no temp file may linger");
        }
    }

    @Test
    void repeatedLoadSaveIsStable() throws Exception {
        writeFile("""
                {"version": 1, "extensions": {
                  "ghost": {"x": 1},
                  "waypoints": {"flag": false, "level": 7, "mode": "B"}
                }}
                """);
        initWaypoints();
        Path file = settingsFile();

        assertTrue(store(file).save());
        byte[] first = Files.readAllBytes(file);
        store(file).load();
        assertTrue(store(file).save());
        byte[] second = Files.readAllBytes(file);

        assertEquals(new String(first, StandardCharsets.UTF_8), new String(second, StandardCharsets.UTF_8));
    }

    @Test
    void valuesSurviveDisableEnableAndRefresh() {
        PersistExtension extension = initWaypoints();
        extension.flag.set(false);

        ExtensionManager.enable("waypoints");
        ExtensionManager.disable("waypoints");
        ExtensionManager.enable("waypoints");

        assertEquals(false, extension.flag.get());
        assertSame(extension.flag, ExtensionManager.getSettings("waypoints").get(0));

        ExtensionManager.setScheduler(Runnable::run);

        assertEquals(false, extension.flag.get(), "context refresh must not reset values");
        assertSame(extension.flag, ExtensionManager.getSettings("waypoints").get(0));
    }

    @Test
    void failedOnLoadLeavesNoActiveSettings() throws Exception {
        writeFile("""
                {"version": 1, "extensions": {"broken": {"flag": false}}}
                """);
        FailingLoadExtension broken = new FailingLoadExtension();
        ExtensionManager.init(fakeLoader(List.of(entrypoint("broken-mod", broken))));
        SettingsStore store = store(settingsFile());
        store.load();

        assertTrue(ExtensionManager.get("broken").isEmpty());
        assertTrue(ExtensionManager.getSettings("broken").isEmpty(),
                "failed onLoad must not leave active declarations");

        broken.flag.set(true);
        assertTrue(store.save(), "save still works for the rest of the registry");
        JsonObject owners = JsonParser.parseString(readFile()).getAsJsonObject()
                .getAsJsonObject("extensions");
        assertEquals(false, owners.getAsJsonObject("broken").get("flag").getAsBoolean(),
                "detached in-memory values must not overwrite the preserved section");
    }

    @Test
    void shutdownFlushSavesPendingChanges() throws Exception {
        PersistExtension extension = initWaypoints();
        SettingsStore store = store(settingsFile());
        extension.flag.set(false);

        assertTrue(store.save(), "unconditional save is what shutdown calls");
        assertFalse(store.isDirty());

        PersistExtension restarted = restart(settingsFile());
        assertEquals(false, restarted.flag.get());
    }

    @Test
    void coreSectionRoundTrips() throws Exception {
        boolean originalFlag = MosaicCoreSettings.notifications().get();
        int originalScale = MosaicCoreSettings.uiScale().get();
        MosaicCoreSettings.Theme originalTheme = MosaicCoreSettings.theme().get();
        try {
            MosaicCoreSettings.notifications().set(false);
            MosaicCoreSettings.uiScale().set(150);
            MosaicCoreSettings.theme().set(MosaicCoreSettings.Theme.SYSTEM);
            Path file = settingsFile();
            assertTrue(store(file).save());

            MosaicCoreSettings.notifications().set(true);
            MosaicCoreSettings.uiScale().set(50);
            MosaicCoreSettings.theme().set(MosaicCoreSettings.Theme.MIDNIGHT);
            store(file).load();

            assertEquals(false, MosaicCoreSettings.notifications().get());
            assertEquals(150, MosaicCoreSettings.uiScale().get());
            assertEquals(MosaicCoreSettings.Theme.SYSTEM, MosaicCoreSettings.theme().get());
        } finally {
            MosaicCoreSettings.notifications().set(originalFlag);
            MosaicCoreSettings.uiScale().set(originalScale);
            MosaicCoreSettings.theme().set(originalTheme);
        }
    }

    @Test
    void concurrentChangeDuringLoadWinsAndStaysDirty() throws Exception {
        // Deterministic ordering without timing luck: the main thread holds
        // the setting's monitor, so the loader thread is guaranteed to block
        // inside the guarded apply. The change is then made while the load
        // is provably mid-flight (load began -> change -> dirty ->
        // load completes -> dirty still correct).
        writeFile("""
                {"version": 1, "extensions": {"waypoints": {"flag": true}}}
                """);
        PersistExtension extension = initWaypoints();
        SettingsStore store = new SettingsStore(settingsFile(), 0);
        ExtensionManager.setSettingsStore(store);

        Thread loader = new Thread(store::load, "test-settings-loader");
        loader.setDaemon(true);
        synchronized (extension.flag) {
            loader.start();
            awaitBlocked(loader);
            extension.flag.set(false);
        }
        loader.join(10_000);
        assertFalse(loader.isAlive(), "load must complete");

        assertEquals(false, extension.flag.get(), "racing change must not be overwritten by the load");
        assertTrue(store.isDirty(), "racing change must keep its dirty mark");
        assertTrue(store.save(), "racing change must be saveable");
        JsonObject owners = JsonParser.parseString(readFile()).getAsJsonObject()
                .getAsJsonObject("extensions");
        assertEquals(false, owners.getAsJsonObject("waypoints").get("flag").getAsBoolean(),
                "racing change must reach the file");
    }

    @Test
    void preLoadChangeLosesToFileButStaysConsistent() throws Exception {
        // Boundary semantics: a change completed before the load began is
        // older than the persisted state, so startup restore wins for the
        // value — but the dirty mark is never cleared by loading, so memory
        // and file converge on the next save instead of diverging silently.
        writeFile("""
                {"version": 1, "extensions": {"waypoints": {"flag": true}}}
                """);
        PersistExtension extension = initWaypoints();
        SettingsStore store = new SettingsStore(settingsFile(), 0);
        ExtensionManager.setSettingsStore(store);
        extension.flag.set(false);

        store.load();

        assertEquals(true, extension.flag.get(), "startup restore wins over pre-load changes");
        assertTrue(store.isDirty(), "load must never clear dirty state");
        assertTrue(store.saveIfDirty());
        assertEquals(true, JsonParser.parseString(readFile()).getAsJsonObject()
                .getAsJsonObject("extensions").getAsJsonObject("waypoints").get("flag").getAsBoolean());
    }

    @Test
    void periodicSaveLifecycle() throws Exception {
        Path file = settingsFile();
        SettingsStore store = new SettingsStore(file, 10_000);
        ExtensionManager.setSettingsStore(store);
        PersistExtension extension = initWaypoints();
        long tick = 1_000_000L;

        assertFalse(store.saveIfDirty(tick), "clean settings must not write");
        assertFalse(Files.exists(file), "clean settings must not create the file");

        extension.flag.set(false);
        assertTrue(store.saveIfDirty(tick + 5_000), "first dirty save writes immediately");
        assertTrue(Files.exists(file));

        extension.flag.set(true);
        assertFalse(store.saveIfDirty(tick + 8_000), "interval not elapsed since last write");
        assertTrue(store.saveIfDirty(tick + 15_000), "dirty change is eventually written");
        assertFalse(store.isDirty());
        assertFalse(store.saveIfDirty(tick + 30_000), "clean again: no further writes");
        assertEquals(true, JsonParser.parseString(readFile()).getAsJsonObject()
                .getAsJsonObject("extensions").getAsJsonObject("waypoints").get("flag").getAsBoolean());
    }

    @Test
    void failedPeriodicSaveKeepsDirtyAndRetrySucceeds() throws Exception {
        Path blocker = tempDir.resolve("blocker");
        Files.writeString(blocker, "x", StandardCharsets.UTF_8);
        Path badFile = blocker.resolve("settings.json");
        SettingsStore store = new SettingsStore(badFile, 0);
        ExtensionManager.setSettingsStore(store);
        PersistExtension extension = initWaypoints();
        extension.flag.set(false);

        assertFalse(store.saveIfDirty(1_000_000L), "failed write must report failure, not throw");
        assertTrue(store.isDirty(), "failed write must keep dirty for a later retry");

        Files.delete(blocker);
        Files.createDirectory(blocker);

        assertTrue(store.saveIfDirty(2_000_000L), "retry after failure must succeed");
        assertFalse(store.isDirty());
        assertEquals(false, JsonParser.parseString(
                        Files.readString(badFile, StandardCharsets.UTF_8)).getAsJsonObject()
                .getAsJsonObject("extensions").getAsJsonObject("waypoints").get("flag").getAsBoolean(),
                "retried change must reach the file");
    }

    @Test
    void malformedLoadBacksUpOriginal() throws Exception {
        writeFile("{broken json!!!");
        byte[] original = Files.readAllBytes(settingsFile());
        initWaypoints();
        store(settingsFile()).load();

        Path backup = tempDir.resolve("mosaic/settings.json.corrupt.bak");
        assertTrue(Files.exists(backup), "malformed file must be preserved before any later replace");
        assertEquals(new String(original, StandardCharsets.UTF_8),
                Files.readString(backup, StandardCharsets.UTF_8));
        assertEquals(new String(original, StandardCharsets.UTF_8), readFile(),
                "original must be byte-identical");
    }

    private static void awaitBlocked(Thread thread) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (thread.getState() != Thread.State.BLOCKED) {
            if (System.currentTimeMillis() > deadline) {
                fail("loader thread never blocked; test cannot control the ordering");
            }
            Thread.yield();
        }
    }

    @Test
    void enabledStateRestoresOnRestart() throws Exception {
        PersistExtension extension = initWaypoints();
        ExtensionManager.enable("waypoints");
        Path file = settingsFile();
        assertTrue(store(file).save());

        JsonObject root = JsonParser.parseString(readFile()).getAsJsonObject();
        assertEquals(1, root.getAsJsonArray("enabled").size());
        assertEquals("waypoints", root.getAsJsonArray("enabled").get(0).getAsString());

        ExtensionManager.resetForTesting();
        PersistExtension restarted = initWaypoints();
        SettingsStore store = store(file);
        store.load();
        ExtensionManager.restoreEnabledState(store.loadedEnabledIds());

        assertTrue(ExtensionManager.isEnabled("waypoints"));
        assertEquals(true, restarted.seenFlag, "restored extension must actually run onEnable");
    }

    @Test
    void disabledStateRestoresOnRestart() throws Exception {
        PersistExtension extension = initWaypoints();
        ExtensionManager.enable("waypoints");
        Path file = settingsFile();
        SettingsStore store = store(file);
        assertTrue(store.save());
        ExtensionManager.disable("waypoints");
        assertTrue(store.save());

        ExtensionManager.resetForTesting();
        PersistExtension restarted = initWaypoints();
        SettingsStore restored = store(file);
        restored.load();
        ExtensionManager.restoreEnabledState(restored.loadedEnabledIds());

        assertFalse(ExtensionManager.isEnabled("waypoints"));
        assertTrue(restarted.seenFlag == null, "onEnable must not run for a disabled extension");
        assertEquals(0, JsonParser.parseString(readFile()).getAsJsonObject()
                .getAsJsonArray("enabled").size());
    }

    @Test
    void lifecycleToggleMarksDirtyForPeriodicSave() throws Exception {
        Path file = settingsFile();
        SettingsStore store = new SettingsStore(file, 10_000);
        ExtensionManager.setSettingsStore(store);
        initWaypoints();
        long tick = 1_000_000L;

        ExtensionManager.enable("waypoints");
        assertTrue(store.isDirty(), "enable must report for persistence");
        assertTrue(store.saveIfDirty(tick));

        ExtensionManager.disable("waypoints");
        assertTrue(store.isDirty(), "disable must report for persistence");
        assertFalse(store.saveIfDirty(tick + 5_000), "interval not elapsed");
        assertTrue(store.saveIfDirty(tick + 15_000));
        assertEquals(0, JsonParser.parseString(readFile()).getAsJsonObject()
                .getAsJsonArray("enabled").size());
    }

    @Test
    void unknownEnabledIdPreserved() throws Exception {
        writeFile("""
                {"version": 1, "enabled": ["ghost"], "extensions": {
                  "ghost": {"x": 1},
                  "waypoints": {"flag": false}
                }}
                """);
        PersistExtension extension = initWaypoints();
        SettingsStore store = store(settingsFile());
        store.load();

        assertTrue(store.loadedEnabledIds().isEmpty(), "unknown ids are never auto-enabled");
        assertEquals(false, extension.flag.get());
        extension.flag.set(true);
        assertTrue(store.save());

        JsonObject root = JsonParser.parseString(readFile()).getAsJsonObject();
        assertEquals("ghost", root.getAsJsonArray("enabled").get(0).getAsString(),
                "temporarily unavailable extension keeps its lifecycle state");
        assertEquals(1, root.getAsJsonObject("extensions").getAsJsonObject("ghost")
                .get("x").getAsInt());
        assertEquals(true, root.getAsJsonObject("extensions").getAsJsonObject("waypoints")
                .get("flag").getAsBoolean());
    }

    @Test
    void malformedEnabledFieldHandled() throws Exception {
        writeFile("""
                {"version": 1, "enabled": {"x": 1}, "extensions": {"waypoints": {"flag": false}}}
                """);
        PersistExtension extension = initWaypoints();
        SettingsStore store = store(settingsFile());
        store.load();

        assertEquals(false, extension.flag.get(), "settings still apply with a bad enabled field");
        assertTrue(store.loadedEnabledIds().isEmpty());
        assertTrue(Files.exists(tempDir.resolve("mosaic/settings.json.corrupt.bak")),
                "original preserved before the bad field can be replaced");

        writeFile("""
                {"version": 1, "enabled": ["waypoints", 42, ""], "extensions": {}}
                """);
        store.load();
        assertEquals(List.of("waypoints"), store.loadedEnabledIds(),
                "non-string and blank entries are skipped");
    }

    @Test
    void failedLoadExtensionNotAutoEnabledButPreserved() throws Exception {
        writeFile("""
                {"version": 1, "enabled": ["broken"], "extensions": {"broken": {"flag": false}}}
                """);
        FailingLoadExtension broken = new FailingLoadExtension();
        ExtensionManager.init(fakeLoader(List.of(entrypoint("broken-mod", broken))));
        SettingsStore store = store(settingsFile());
        store.load();

        assertTrue(store.loadedEnabledIds().isEmpty(), "failed-load extension must not auto-enable");
        ExtensionManager.restoreEnabledState(store.loadedEnabledIds());
        assertFalse(ExtensionManager.isEnabled("broken"));

        broken.flag.set(true);
        assertTrue(store.save(), "save still works for the rest of the registry");
        JsonObject root = JsonParser.parseString(readFile()).getAsJsonObject();
        assertEquals("broken", root.getAsJsonArray("enabled").get(0).getAsString());
        assertEquals(false, root.getAsJsonObject("extensions").getAsJsonObject("broken")
                .get("flag").getAsBoolean(),
                "detached in-memory values must not overwrite the preserved section");
    }

    @Test
    void missingEnabledFieldMeansAllDisabled() throws Exception {
        writeFile("""
                {"version": 1, "extensions": {"waypoints": {"flag": false}}}
                """);
        PersistExtension extension = initWaypoints();
        SettingsStore store = store(settingsFile());
        store.load();

        assertTrue(store.loadedEnabledIds().isEmpty(), "v1 files without the field start disabled");
        assertEquals(false, extension.flag.get());
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

    private PersistExtension initWaypoints() {
        return initExtension("waypoints");
    }

    private PersistExtension initExtension(String id) {
        PersistExtension extension = new PersistExtension(id);
        ExtensionManager.init(fakeLoader(List.of(entrypoint("mod-" + id, extension))));
        return extension;
    }

    private PersistExtension restart(Path file) {
        ExtensionManager.resetForTesting();
        PersistExtension extension = new PersistExtension("waypoints");
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
                SettingsPersistenceTest.class.getClassLoader(),
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

    private static final class PersistExtension extends Extension {
        private final String id;
        BooleanSetting flag;
        IntSetting level;
        EnumSetting<Probe> mode;
        volatile Boolean seenFlag;
        volatile Integer seenLevel;
        volatile Probe seenMode;

        PersistExtension(String id) {
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
                    return "Persist " + id;
                }

                @Override
                public String getDescription() {
                    return "Persistence test extension.";
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
            var settings = getContext().getSettings();
            flag = settings.registerBoolean("flag", "Flag", "Flag.", true);
            level = settings.registerInt("level", "Level", "Level.", 5, 0, 10);
            mode = settings.registerEnum("mode", "Mode", null, Probe.A);
        }

        @Override
        public void onEnable() {
            seenFlag = flag.get();
            seenLevel = level.get();
            seenMode = mode.get();
        }

        @Override
        public void onDisable() {
        }
    }

    private static final class FailingLoadExtension extends Extension {
        volatile BooleanSetting flag;

        @Override
        public ExtensionMetadata getMetadata() {
            return new ExtensionMetadata() {
                @Override
                public String getId() {
                    return "broken";
                }

                @Override
                public String getName() {
                    return "Broken";
                }

                @Override
                public String getDescription() {
                    return "Fails onLoad.";
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
            flag = getContext().getSettings().registerBoolean("flag", "Flag", null, true);
            throw new IllegalStateException("broken onLoad");
        }

        @Override
        public void onEnable() {
        }

        @Override
        public void onDisable() {
        }
    }
}
