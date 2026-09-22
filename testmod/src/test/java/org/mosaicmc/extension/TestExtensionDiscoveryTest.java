package org.mosaicmc.extension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mosaicmc.test.TestExtension;

/**
 * Proves the real testmod passes {@link ExtensionManager} validation.
 *
 * <p>Lives in {@code org.mosaicmc.extension} (instead of {@code org.mosaicmc.test})
 * to reach the package-private {@code init(FabricLoader)} seam without
 * widening the public API.
 */
class TestExtensionDiscoveryTest {

    @BeforeEach
    void clearRegistry() {
        ExtensionManager.resetForTesting();
    }

    @Test
    void realTestExtensionIsDiscoveredAndLoaded() {
        TestExtension extension = new TestExtension();
        FabricLoader loader = fake(FabricLoader.class,
                Map.of("getEntrypointContainers", List.of(entrypoint("mosaic-testmod", extension))));

        ExtensionManager.init(loader);

        assertTrue(ExtensionManager.get("mosaic-testmod").isPresent(),
                "real testmod must survive strict id validation and register");
    }

    @Test
    void modJsonDeclaresMosaicEntrypoint() throws Exception {
        String json;

        try (var in = TestExtensionDiscoveryTest.class.getResourceAsStream("/fabric.mod.json")) {
            assertNotNull(in, "fabric.mod.json must be on the test classpath");
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(json.contains("\"mosaic\""),
                "fabric.mod.json must declare the mosaic entrypoint or discovery silently ignores the mod");
        assertTrue(json.contains("org.mosaicmc.test.TestExtension"),
                "fabric.mod.json mosaic entrypoint must point at TestExtension");
    }

    @Test
    void modJsonDeclaresClientDemo() throws Exception {
        String json;

        try (var in = TestExtensionDiscoveryTest.class.getResourceAsStream("/fabric.mod.json")) {
            assertNotNull(in, "fabric.mod.json must be on the test classpath");
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(json.contains("\"client\""),
                "fabric.mod.json must declare the client entrypoint for the in-game scheduler demo");
        assertTrue(json.contains("org.mosaicmc.test.TestExtensionClient"),
                "fabric.mod.json client entrypoint must point at TestExtensionClient");
    }

    @Test
    void contextIsInjectedAndSchedulerRunsInline() {
        TestExtension extension = new TestExtension();
        ExtensionManager.init(fakeLoader(List.of(entrypoint("mosaic-testmod", extension))));

        var registered = ExtensionManager.get("mosaic-testmod").orElseThrow();
        assertNotNull(registered.getContext(), "ExtensionManager must inject a context");
        assertNotNull(registered.getContext().getScheduler(), "context must provide a scheduler");

        var ran = new java.util.concurrent.atomic.AtomicBoolean(false);
        registered.getContext().getScheduler().execute(() -> ran.set(true));
        assertTrue(ran.get(), "default scheduler must run the task (inline in tests)");
    }

    @Test
    void schedulerHopFromBackgroundThread() throws Exception {
        TestExtension extension = new TestExtension();
        ExtensionManager.init(fakeLoader(List.of(entrypoint("mosaic-testmod", extension))));

        var registered = ExtensionManager.get("mosaic-testmod").orElseThrow();
        var latch = new java.util.concurrent.CountDownLatch(1);
        var runner = new java.util.concurrent.atomic.AtomicReference<String>();

        Thread bg = new Thread(() -> registered.getContext().getScheduler()
                .execute(() -> {
                    runner.set(Thread.currentThread().getName());
                    latch.countDown();
                }), "test-bg");
        bg.start();

        assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS),
                "scheduler task must complete");
        assertNotNull(runner.get());
    }

    private static EntrypointContainer<Extension> entrypoint(String modId, Extension extension) {
        return fake(EntrypointContainer.class, Map.of(
                "getProvider", fake(ModContainer.class, Map.of(
                        "getMetadata", fake(ModMetadata.class, Map.of("getId", modId)))),
                "getEntrypoint", extension));
    }

    private static FabricLoader fakeLoader(List<EntrypointContainer<Extension>> containers) {
        return fake(FabricLoader.class, Map.of("getEntrypointContainers", containers));
    }

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, Map<String, Object> stubs) {
        Map<String, Object> values = new HashMap<>(stubs);
        return (T) Proxy.newProxyInstance(
                TestExtensionDiscoveryTest.class.getClassLoader(),
                new Class<?>[] { type },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "toString" -> {
                            return "fake(" + type.getSimpleName() + ")";
                        }
                        case "hashCode" -> {
                            return System.identityHashCode(proxy);
                        }
                        case "equals" -> {
                            return proxy == args[0];
                        }
                    }
                    if (values.containsKey(method.getName())) {
                        return values.get(method.getName());
                    }
                    return null;
                });
    }
}
