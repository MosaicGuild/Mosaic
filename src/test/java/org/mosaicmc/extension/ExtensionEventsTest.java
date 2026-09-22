package org.mosaicmc.extension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mosaicmc.api.ExtensionMetadata;

/**
 * Proves the context exposes a working events bridge over Fabric's
 * {@link Event} model.
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
    void registerDelegatesToFabricEvent() {
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", new DummyExtension()))));
        var events = ExtensionManager.get("waypoints").orElseThrow().getContext().getEvents();

        var captured = new AtomicReference<Object>();
        Event<Runnable> event = fakeEvent(captured);

        Runnable listener = () -> {
        };
        events.register(event, listener);

        assertSame(listener, captured.get(), "listener must be passed straight to Event.register");
    }

    @Test
    void registerRejectsNulls() {
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", new DummyExtension()))));
        var events = ExtensionManager.get("waypoints").orElseThrow().getContext().getEvents();
        Event<Runnable> event = fakeEvent(new AtomicReference<>());

        assertThrows(NullPointerException.class, () -> events.<Runnable>register(null, () -> {
        }));
        assertThrows(NullPointerException.class, () -> events.<Runnable>register(event, null));
    }

    @Test
    void registeredExtensionExposesEvents() {
        DummyExtension extension = new DummyExtension();
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", extension))));

        var registered = ExtensionManager.get("waypoints").orElseThrow();
        assertNotNull(registered.getContext().getEvents());
    }

    @Test
    void registeredExtensionEventsCanRegister() {
        DummyExtension extension = new DummyExtension();
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", extension))));

        var captured = new AtomicReference<Object>();
        @SuppressWarnings("unchecked")
        Event<Runnable> event = fakeEvent(captured);

        Runnable listener = () -> {
        };
        ExtensionManager.get("waypoints").orElseThrow().getContext().getEvents().register(event, listener);

        assertTrue(captured.get() == listener);
    }

    private static <T> Event<T> fakeEvent(AtomicReference<Object> captured) {
        return new Event<>() {
            @Override
            public void register(T listener) {
                captured.set(listener);
            }
        };
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
