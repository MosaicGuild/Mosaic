package org.mosaicmc.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mosaicmc.api.ExtensionMetadata;
import org.mosaicmc.internal.ClientTickRegistry;

/**
 * Proves the managed tick lifecycle: register, fire, unregister, and
 * disable-cleanup. Ticks are driven through the registry snapshots with
 * {@code null} clients and levels (listeners under test ignore them); the
 * test source set sees client classes, so invoking the Fabric listener
 * types compiles.
 */
class ExtensionEventsTest {

    @BeforeEach
    void clearRegistry() {
        ExtensionManager.resetForTesting();
    }

    @Test
    void contextProvidesSchedulerAndEvents() {
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", new DummyExtension()))));

        var registered = ExtensionManager.get("waypoints").orElseThrow();
        assertNotNull(registered.getContext().getScheduler());
        assertNotNull(registered.getContext().getEvents());
    }

    @Test
    void registerRejectsNull() {
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", new DummyExtension()))));
        var events = ExtensionManager.get("waypoints").orElseThrow().getContext().getEvents();

        assertThrows(NullPointerException.class, () -> events.onClientTick(null));
        assertThrows(NullPointerException.class, () -> events.onClientTickStart(null));
        assertThrows(NullPointerException.class, () -> events.onLevelTickStart(null));
        assertThrows(NullPointerException.class, () -> events.onLevelTickEnd(null));
    }

    @Test
    void registeredListenerFires() {
        var events = registeredEvents();

        var fires = new AtomicInteger();
        events.onClientTick(client -> fires.incrementAndGet());

        fireTick();

        assertEquals(1, fires.get());
    }

    @Test
    void twoListenersBothFire() {
        var events = registeredEvents();

        var first = new AtomicInteger();
        var second = new AtomicInteger();
        events.onClientTick(client -> first.incrementAndGet());
        events.onClientTick(client -> second.incrementAndGet());

        fireTick();

        assertEquals(1, first.get());
        assertEquals(1, second.get());
    }

    @Test
    void unregisterOneLeavesTheOther() {
        var events = registeredEvents();

        var first = new AtomicInteger();
        var second = new AtomicInteger();
        var registration = events.onClientTick(client -> first.incrementAndGet());
        events.onClientTick(client -> second.incrementAndGet());

        registration.unregister();
        fireTick();

        assertEquals(0, first.get());
        assertEquals(1, second.get());
    }

    @Test
    void unregisterTwiceDoesNotThrow() {
        var events = registeredEvents();

        var fires = new AtomicInteger();
        var registration = events.onClientTick(client -> fires.incrementAndGet());

        registration.unregister();
        registration.unregister();

        fireTick();

        assertEquals(0, fires.get());
    }

    @Test
    void unregisteredListenerStops() {
        var events = registeredEvents();

        var fires = new AtomicInteger();
        var registration = events.onClientTick(client -> fires.incrementAndGet());

        fireTick();
        assertEquals(1, fires.get());

        registration.unregister();
        fireTick();

        assertEquals(1, fires.get(), "listener must not fire after unregister");
    }

    @Test
    void disableRemovesRegistrations() {
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", new DummyExtension()))));
        var events = ExtensionManager.get("waypoints").orElseThrow().getContext().getEvents();

        var fires = new AtomicInteger();
        events.onClientTick(client -> fires.incrementAndGet());

        ExtensionManager.enable("waypoints");
        ExtensionManager.disable("waypoints");
        fireTick();

        assertEquals(0, fires.get(), "disable must leave no callback behind");
    }

    @Test
    void disableRemovesAllTickTypes() {
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", new DummyExtension()))));
        var events = ExtensionManager.get("waypoints").orElseThrow().getContext().getEvents();

        var fires = new AtomicInteger();
        events.onClientTick(client -> fires.incrementAndGet());
        events.onClientTickStart(client -> fires.incrementAndGet());
        events.onLevelTickStart(level -> fires.incrementAndGet());
        events.onLevelTickEnd(level -> fires.incrementAndGet());

        ExtensionManager.enable("waypoints");
        ExtensionManager.disable("waypoints");
        fireAllTicks();

        assertEquals(0, fires.get(), "disable must clear every tick type");
    }

    @Test
    void startTickFiresAndStops() {
        var events = registeredEvents();

        var fires = new AtomicInteger();
        var registration = events.onClientTickStart(client -> fires.incrementAndGet());

        fireStartTick();
        assertEquals(1, fires.get());

        registration.unregister();
        fireStartTick();

        assertEquals(1, fires.get(), "listener must not fire after unregister");
    }

    @Test
    void levelTicksFireAndStop() {
        var events = registeredEvents();

        var fires = new AtomicInteger();
        var start = events.onLevelTickStart(level -> fires.incrementAndGet());
        var end = events.onLevelTickEnd(level -> fires.incrementAndGet());

        fireLevelTicks();
        assertEquals(2, fires.get());

        start.unregister();
        end.unregister();
        fireLevelTicks();

        assertEquals(2, fires.get(), "listeners must not fire after unregister");
    }

    @Test
    void failedEnableRollsBackRegistrations() {
        FailableExtension extension = new FailableExtension();
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", extension))));

        ExtensionManager.enable("waypoints");
        fireTick();

        assertEquals(1, extension.attempts);
        assertEquals(0, extension.fires.get(),
                "half-registered listeners must not survive a failed enable");
    }

    @Test
    void failedEnableAllowsRetry() {
        FailableExtension extension = new FailableExtension();
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", extension))));

        ExtensionManager.enable("waypoints");
        extension.fail = false;
        ExtensionManager.enable("waypoints");
        fireTick();

        assertEquals(2, extension.attempts);
        assertEquals(2, extension.fires.get(), "retry after failure must register cleanly");
    }

    @Test
    void registrationsSurviveSchedulerSwap() {
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", new DummyExtension()))));
        var events = ExtensionManager.get("waypoints").orElseThrow().getContext().getEvents();

        var fires = new AtomicInteger();
        events.onClientTick(client -> fires.incrementAndGet());

        ExtensionManager.setScheduler(Runnable::run);
        fireTick();

        assertEquals(1, fires.get(), "context refresh must keep the events bridge");
    }

    private static org.mosaicmc.api.ExtensionEvents registeredEvents() {
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", new DummyExtension()))));
        return ExtensionManager.get("waypoints").orElseThrow().getContext().getEvents();
    }

    private static void fireTick() {
        for (var listener : ClientTickRegistry.endTickListeners()) {
            listener.onEndTick(null);
        }
    }

    private static void fireStartTick() {
        for (var listener : ClientTickRegistry.startTickListeners()) {
            listener.onStartTick(null);
        }
    }

    private static void fireLevelTicks() {
        for (var listener : ClientTickRegistry.startLevelTickListeners()) {
            listener.onStartTick(null);
        }
        for (var listener : ClientTickRegistry.endLevelTickListeners()) {
            listener.onEndTick(null);
        }
    }

    private static void fireAllTicks() {
        fireTick();
        fireStartTick();
        fireLevelTicks();
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
        return (T) Proxy.newProxyInstance(
                ExtensionEventsTest.class.getClassLoader(),
                new Class<?>[] { type },
                (proxy, method, args) -> {
                    if (stubs.containsKey(method.getName())) {
                        return stubs.get(method.getName());
                    }
                    return null;
                });
    }

    /**
     * Registers two listeners, then throws: proves a failed enable rolls
     * back partial registrations instead of leaking them.
     */
    private static final class FailableExtension extends Extension {
        private int attempts;
        private boolean fail = true;
        private final AtomicInteger fires = new AtomicInteger();

        @Override
        public ExtensionMetadata getMetadata() {
            return metadata("waypoints");
        }

        @Override
        public void onEnable() {
            attempts++;
            getContext().getEvents().onClientTick(client -> fires.incrementAndGet());
            getContext().getEvents().onClientTick(client -> fires.incrementAndGet());
            if (fail) {
                throw new IllegalStateException("broken third step");
            }
        }

        @Override
        public void onDisable() {
        }

        @Override
        public void onLoad() {
        }
    }

    private static ExtensionMetadata metadata(String id) {
        return new ExtensionMetadata() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public String getName() {
                return "Dummy";
            }

            @Override
            public String getDescription() {
                return "Dummy.";
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

    private static final class DummyExtension extends Extension {
        @Override
        public ExtensionMetadata getMetadata() {
            return new ExtensionMetadata() {
                @Override
                public String getId() {
                    return "waypoints";
                }

                @Override
                public String getName() {
                    return "Dummy";
                }

                @Override
                public String getDescription() {
                    return "Dummy.";
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
