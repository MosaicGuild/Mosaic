package org.mosaicmc.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mosaicmc.api.ExtensionMetadata;
import org.mosaicmc.api.storage.ExtensionStorage;

/**
 * Unit tests for the public storage API: defaults, reads/writes, removal,
 * validation, ordering, and per-extension isolation.
 */
class StorageApiTest {

    @BeforeEach
    void clearRegistry() {
        ExtensionManager.resetForTesting();
    }

    @Test
    void putGetAndDefault() {
        ExtensionStorage storage = storageOf("waypoints");

        assertEquals("fallback", storage.get("missing", "fallback"));
        assertEquals(null, storage.get("missing", null));

        storage.put("nickname", "home");
        assertEquals("home", storage.get("nickname", "fallback"));
    }

    @Test
    void overwriteReplaces() {
        ExtensionStorage storage = storageOf("waypoints");

        storage.put("key", "first");
        storage.put("key", "second");

        assertEquals("second", storage.get("key", "fallback"));
        assertEquals(List.of("key"), storage.keys(), "overwrite must not duplicate the key");
    }

    @Test
    void nullAndBlankKeysRejected() {
        ExtensionStorage storage = storageOf("waypoints");

        assertThrows(NullPointerException.class, () -> storage.get(null, "d"));
        assertThrows(NullPointerException.class, () -> storage.put(null, "v"));
        assertThrows(NullPointerException.class, () -> storage.contains(null));
        assertThrows(NullPointerException.class, () -> storage.remove(null));

        assertThrows(IllegalArgumentException.class, () -> storage.get("  ", "d"));
        assertThrows(IllegalArgumentException.class, () -> storage.put("", "v"));
        assertThrows(IllegalArgumentException.class, () -> storage.contains("  "));
        assertThrows(IllegalArgumentException.class, () -> storage.remove(""));
    }

    @Test
    void nullValueRejected() {
        ExtensionStorage storage = storageOf("waypoints");

        assertThrows(NullPointerException.class, () -> storage.put("key", null));
    }

    @Test
    void emptyStringValueAllowed() {
        ExtensionStorage storage = storageOf("waypoints");

        storage.put("key", "");

        assertTrue(storage.contains("key"));
        assertEquals("", storage.get("key", "fallback"));
    }

    @Test
    void containsRemoveClear() {
        ExtensionStorage storage = storageOf("waypoints");

        assertFalse(storage.contains("key"));
        assertFalse(storage.remove("key"), "removing an absent key is silent");

        storage.put("key", "value");
        assertTrue(storage.contains("key"));
        assertTrue(storage.remove("key"));
        assertFalse(storage.contains("key"));
        assertFalse(storage.remove("key"), "double remove stays silent");

        storage.put("a", "1");
        storage.put("b", "2");
        storage.clear();
        assertEquals(List.of(), storage.keys());
        assertFalse(storage.contains("a"));
        storage.clear();
    }

    @Test
    void keysInInsertionOrderAndSnapshot() {
        ExtensionStorage storage = storageOf("waypoints");

        storage.put("b", "2");
        storage.put("a", "1");
        List<String> snapshot = storage.keys();

        assertEquals(List.of("b", "a"), snapshot);
        storage.put("c", "3");
        assertEquals(List.of("b", "a"), snapshot, "earlier snapshots must not change");
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add("x"));
    }

    @Test
    void sameKeyInDifferentExtensionsIsIsolated() {
        ExtensionStorage first = storageOf("alpha");
        initExtension("beta");
        ExtensionStorage second = ExtensionManager.get("beta").orElseThrow().getContext().getStorage();

        first.put("selected", "home");
        assertEquals("home", first.get("selected", "none"));
        assertEquals("none", second.get("selected", "none"), "second extension keeps its own state");

        second.put("selected", "portal");
        assertEquals("home", first.get("selected", "none"), "writes do not leak across extensions");
        assertEquals("portal", second.get("selected", "none"));
    }

    @Test
    void storageSurvivesDisableAndSchedulerSwap() {
        DummyExtension extension = initExtension("waypoints");
        ExtensionStorage storage = extension.getContext().getStorage();
        storage.put("selected", "home");

        ExtensionManager.enable("waypoints");
        ExtensionManager.disable("waypoints");

        assertEquals("home", storage.get("selected", "none"), "disable must not delete storage");

        ExtensionManager.setScheduler(Runnable::run);

        ExtensionStorage refreshed = extension.getContext().getStorage();
        assertSame(storage, refreshed, "context refresh must reuse the storage manager");
        assertEquals("home", refreshed.get("selected", "none"));
    }

    @Test
    void ownersTrackedForUnknownIds() {
        initExtension("waypoints");

        assertEquals(List.of("waypoints"), ExtensionManager.storageOwnerIds());
        assertTrue(ExtensionManager.getStorageEntries("unknown-id").isEmpty());
        assertTrue(ExtensionManager.getStorageLoadedKeys("unknown-id").isEmpty());
    }

    // Helpers: one shared discovery per test; each test resets first.

    private static ExtensionStorage storageOf(String id) {
        return initExtension(id).getContext().getStorage();
    }

    private static DummyExtension initExtension(String id) {
        DummyExtension extension = new DummyExtension(id);
        ExtensionManager.init(fakeLoader(List.of(entrypoint("mod-" + id, extension))));
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
                StorageApiTest.class.getClassLoader(),
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

    private static final class DummyExtension extends Extension {
        private final String id;

        DummyExtension(String id) {
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
                    return "Dummy " + id;
                }

                @Override
                public String getDescription() {
                    return "Dummy extension for tests.";
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
        public void onEnable() {
        }

        @Override
        public void onDisable() {
        }

        @Override
        public void onLoad() {
        }
    }
}
