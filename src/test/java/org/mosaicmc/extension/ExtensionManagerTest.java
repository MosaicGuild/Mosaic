package org.mosaicmc.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mosaicmc.api.ExtensionMetadata;

/**
 * Unit tests for {@link ExtensionManager} discovery and lifecycle control.
 *
 * <p>Fabric loader types are faked with dynamic proxies so no Minecraft
 * runtime and no mocking framework is needed.
 */
class ExtensionManagerTest {

    @BeforeEach
    void clearRegistry() {
        ExtensionManager.resetForTesting();
    }

    @Test
    void registersValidExtensionAndCallsOnLoadButNotOnEnable() {
        DummyExtension extension = new DummyExtension("waypoints");

        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", extension))));

        assertEquals(List.of(extension), ExtensionManager.getExtensions());
        assertEquals(1, extension.loads);
        assertEquals(0, extension.enables);
    }

    @Test
    void skipsNullMetadataId() {
        DummyExtension extension = new DummyExtension((String) null);

        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", extension))));

        assertTrue(ExtensionManager.getExtensions().isEmpty());
        assertEquals(0, extension.loads);
    }

    @Test
    void skipsBlankMetadataId() {
        DummyExtension extension = new DummyExtension("  ");

        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", extension))));

        assertTrue(ExtensionManager.getExtensions().isEmpty());
        assertEquals(0, extension.loads);
    }

    @Test
    void skipsBrokenMetadataButLoadsOtherExtensions() {
        DummyExtension broken = new DummyExtension(new IllegalStateException("broken metadata"));
        DummyExtension healthy = new DummyExtension("healthy");

        ExtensionManager.init(fakeLoader(List.of(
                entrypoint("broken-mod", broken),
                entrypoint("healthy-mod", healthy))));

        assertEquals(List.of(healthy), ExtensionManager.getExtensions());
        assertEquals(1, healthy.loads);
    }

    @Test
    void skipsThrowingEntrypointButLoadsOtherExtensions() {
        DummyExtension healthy = new DummyExtension("healthy");

        ExtensionManager.init(fakeLoader(List.of(
                throwingEntrypoint("broken-mod", new IllegalStateException("broken constructor")),
                entrypoint("healthy-mod", healthy))));

        assertEquals(List.of(healthy), ExtensionManager.getExtensions());
    }

    @Test
    void skipsUnreadableProviderMetadata() {
        DummyExtension extension = new DummyExtension("waypoints");

        ExtensionManager.init(fakeLoader(List.of(entrypointWithBrokenProvider(extension))));

        assertTrue(ExtensionManager.getExtensions().isEmpty());
    }

    @Test
    void duplicateIdKeepsFirstRegistration() {
        DummyExtension first = new DummyExtension("waypoints");
        DummyExtension second = new DummyExtension("waypoints");

        ExtensionManager.init(fakeLoader(List.of(
                entrypoint("mod-a", first),
                entrypoint("mod-b", second))));

        assertEquals(List.of(first), ExtensionManager.getExtensions());
        assertEquals(Optional.of(first), ExtensionManager.get("waypoints"));
        assertEquals(0, second.loads);
    }

    @Test
    void enableUnknownIdDoesNotThrow() {
        ExtensionManager.init(fakeLoader(List.of()));

        // Must log an error, not throw.
        ExtensionManager.enable("nope");
        ExtensionManager.disable("nope");
    }

    @Test
    void enableAndDisableCallLifecycleOnce() {
        DummyExtension extension = new DummyExtension("waypoints");
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", extension))));

        ExtensionManager.enable("waypoints");
        ExtensionManager.disable("waypoints");

        assertEquals(1, extension.enables);
        assertEquals(1, extension.disables);
    }

    @Test
    void enableDoesNotPropagateThrowingOnEnable() {
        DummyExtension extension = new DummyExtension("waypoints");
        extension.failOnEnable = new IllegalStateException("broken enable");
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", extension))));

        // Must log an error, not throw.
        ExtensionManager.enable("waypoints");
    }

    @Test
    void getExtensionsIsUnmodifiableSnapshot() {
        DummyExtension extension = new DummyExtension("waypoints");
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", extension))));

        List<Extension> snapshot = ExtensionManager.getExtensions();

        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(extension));

        ExtensionManager.init(fakeLoader(List.of(entrypoint("other-mod", new DummyExtension("other")))));

        // The earlier snapshot must not observe later registrations.
        assertEquals(1, snapshot.size());
        assertEquals(2, ExtensionManager.getExtensions().size());
    }

    @Test
    void getReturnsEmptyForUnknownId() {
        ExtensionManager.init(fakeLoader(List.of()));

        assertEquals(Optional.empty(), ExtensionManager.get("nope"));
    }

    // Fakes below. Loader API interfaces are implemented with dynamic proxies;
    // only the methods ExtensionManager touches are stubbed, everything else
    // gets a benign default.

    private static FabricLoader fakeLoader(List<EntrypointContainer<Extension>> containers) {
        return fake(FabricLoader.class, Map.of("getEntrypointContainers", containers));
    }

    private static EntrypointContainer<Extension> entrypoint(String modId, Extension extension) {
        return fake(EntrypointContainer.class, Map.of(
                "getProvider", fake(ModContainer.class, Map.of(
                        "getMetadata", fake(ModMetadata.class, Map.of("getId", modId)))),
                "getEntrypoint", extension));
    }

    private static EntrypointContainer<Extension> throwingEntrypoint(String modId, RuntimeException failure) {
        return fake(EntrypointContainer.class, Map.of(
                "getProvider", fake(ModContainer.class, Map.of(
                        "getMetadata", fake(ModMetadata.class, Map.of("getId", modId)))),
                "getEntrypoint", failure));
    }

    private static EntrypointContainer<Extension> entrypointWithBrokenProvider(Extension extension) {
        return fake(EntrypointContainer.class, Map.of(
                "getProvider", (RuntimeException) new IllegalStateException("broken provider"),
                "getEntrypoint", extension));
    }

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, Map<String, Object> stubs) {
        Map<String, Object> values = new HashMap<>(stubs);
        return (T) Proxy.newProxyInstance(
                ExtensionManagerTest.class.getClassLoader(),
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
                        Object value = values.get(method.getName());
                        if (value instanceof RuntimeException failure) {
                            throw failure;
                        }
                        return value;
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == Optional.class) {
            return Optional.empty();
        }
        if (type == List.class) {
            return List.of();
        }
        return null;
    }

    private static final class DummyExtension extends Extension {
        private final ExtensionMetadata metadata;
        private final List<String> calls = new ArrayList<>();
        private int loads;
        private int enables;
        private int disables;
        private RuntimeException failOnEnable;

        DummyExtension(String id) {
            this.metadata = new FakeMetadata(id);
        }

        DummyExtension(RuntimeException metadataFailure) {
            this.metadata = new FakeMetadata(metadataFailure);
        }

        @Override
        public ExtensionMetadata getMetadata() {
            return metadata;
        }

        @Override
        public void onEnable() {
            enables++;
            calls.add("enable");
            if (failOnEnable != null) {
                throw failOnEnable;
            }
        }

        @Override
        public void onDisable() {
            disables++;
            calls.add("disable");
        }

        @Override
        public void onLoad() {
            loads++;
            calls.add("load");
        }
    }

    private static final class FakeMetadata implements ExtensionMetadata {
        private final String id;
        private final RuntimeException failure;

        FakeMetadata(String id) {
            this.id = id;
            this.failure = null;
        }

        FakeMetadata(RuntimeException failure) {
            this.id = null;
            this.failure = failure;
        }

        @Override
        public String getId() {
            if (failure != null) {
                throw failure;
            }
            return id;
        }

        @Override
        public String getName() {
            return "Dummy";
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
    }
}
