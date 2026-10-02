package org.mosaicmc.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mosaicmc.api.ExtensionMetadata;
import org.mosaicmc.api.command.Command;
import org.mosaicmc.api.command.CommandContext;
import org.mosaicmc.api.settings.BooleanSetting;
import org.mosaicmc.internal.ClientTickRegistry;
import org.mosaicmc.internal.CommandTree;

/**
 * End-to-end lifecycle invariants: repeated toggles, failure rollback,
 * cross-extension isolation, settings survival, load-failure cleanup, and
 * concurrent enable. Each extension registers a command and a tick
 * listener on enable (and a setting on load), so leaks and duplicates are
 * observable through dispatch and tick snapshots.
 */
class ExtensionLifecycleTest {

    @BeforeEach
    void clearRegistry() {
        ExtensionManager.resetForTesting();
    }

    @Test
    void enableTwiceRunsOnEnableOnceAndRegistersOnce() {
        ScriptedExtension extension = new ScriptedExtension("waypoints", "ping");
        extension.registerOnEnable = true;
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", extension))));

        ExtensionManager.enable("waypoints");
        ExtensionManager.enable("waypoints");

        assertEquals(1, extension.enables.get());
        assertTrue(ExtensionManager.isEnabled("waypoints"));
        dispatchExecutes("ping");
        assertEquals(1, extension.commandExecutions.get());
        fireTick();
        assertEquals(1, extension.tickFires.get());
    }

    @Test
    void disableTwiceRunsOnDisableOnce() {
        ScriptedExtension extension = new ScriptedExtension("waypoints", "ping");
        extension.registerOnEnable = true;
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", extension))));

        ExtensionManager.enable("waypoints");
        ExtensionManager.disable("waypoints");
        ExtensionManager.disable("waypoints");

        assertEquals(1, extension.disables.get());
        assertFalse(ExtensionManager.isEnabled("waypoints"));
    }

    @Test
    void enableDisableEnableRegistersExactlyOnce() {
        ScriptedExtension extension = new ScriptedExtension("waypoints", "ping");
        extension.registerOnEnable = true;
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", extension))));

        ExtensionManager.enable("waypoints");
        ExtensionManager.disable("waypoints");
        ExtensionManager.enable("waypoints");

        assertEquals(2, extension.enables.get());
        assertEquals(1, extension.disables.get());
        assertTrue(ExtensionManager.isEnabled("waypoints"));

        fireTick();
        assertEquals(1, extension.tickFires.get(), "re-enable must not duplicate listeners");
        dispatchExecutes("ping");
        assertEquals(1, extension.commandExecutions.get(), "re-enable must not duplicate commands");
    }

    @Test
    void onEnableThrowsBeforeRegisteringLeavesNothingBehind() {
        ScriptedExtension extension = new ScriptedExtension("waypoints", "ping");
        extension.throwOnEnable = true;
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", extension))));

        ExtensionManager.enable("waypoints");

        assertEquals(1, extension.enables.get());
        assertFalse(ExtensionManager.isEnabled("waypoints"));
        assertTrue(ClientTickRegistry.endTickListeners().isEmpty());
        assertTrue(dispatchReplies("ping").get(0).startsWith("Unknown command."));

        extension.throwOnEnable = false;
        ExtensionManager.enable("waypoints");

        assertTrue(ExtensionManager.isEnabled("waypoints"));
        assertEquals(2, extension.enables.get(), "retry after failure must run again");
    }

    @Test
    void onEnableRegistersThenThrowsRollsBack() {
        ScriptedExtension extension = new ScriptedExtension("waypoints", "ping");
        extension.registerOnEnable = true;
        extension.throwOnEnable = true;
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", extension))));

        ExtensionManager.enable("waypoints");
        fireTick();

        assertFalse(ExtensionManager.isEnabled("waypoints"));
        assertEquals(0, extension.tickFires.get(), "half-registered listener must not survive");
        assertTrue(dispatchReplies("ping").get(0).startsWith("Unknown command."),
                "half-registered command must not survive");
        assertTrue(CommandTree.suggest("").stream().noneMatch("ping"::equals));

        extension.throwOnEnable = false;
        ExtensionManager.enable("waypoints");
        fireTick();

        assertEquals(1, extension.tickFires.get(), "retry must register cleanly");
        dispatchExecutes("ping");
    }

    @Test
    void onDisableThrowsButCleanupStillOccurs() {
        ScriptedExtension extension = new ScriptedExtension("waypoints", "ping");
        extension.registerOnEnable = true;
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", extension))));
        ExtensionManager.enable("waypoints");

        extension.throwOnDisable = true;
        ExtensionManager.disable("waypoints");

        assertEquals(1, extension.disables.get());
        assertFalse(ExtensionManager.isEnabled("waypoints"),
                "throwing onDisable must not leave a misleading enabled state");
        fireTick();
        assertEquals(0, extension.tickFires.get(), "listeners must be gone despite the throw");
        assertTrue(dispatchReplies("ping").get(0).startsWith("Unknown command."),
                "commands must be gone despite the throw");
    }

    @Test
    void twoExtensionsHaveNoCrossCleanup() {
        ScriptedExtension alpha = new ScriptedExtension("alpha", "pinga");
        alpha.registerOnEnable = true;
        ScriptedExtension beta = new ScriptedExtension("beta", "pingb");
        beta.registerOnEnable = true;
        ExtensionManager.init(fakeLoader(List.of(
                entrypoint("mod-a", alpha), entrypoint("mod-b", beta))));
        ExtensionManager.enable("alpha");
        ExtensionManager.enable("beta");

        ExtensionManager.disable("alpha");
        fireTick();

        assertEquals(0, alpha.tickFires.get());
        assertEquals(1, beta.tickFires.get(), "disabling alpha must not touch beta's listeners");
        assertTrue(dispatchReplies("pinga").get(0).startsWith("Unknown command."));
        dispatchExecutes("pingb");
        assertEquals(1, beta.commandExecutions.get());

        ExtensionManager.enable("alpha");
        fireTick();

        assertEquals(1, alpha.tickFires.get(), "re-enable restores exactly alpha's listener");
        assertEquals(2, beta.tickFires.get(), "beta's listener fires once per tick, never duplicated");
    }

    @Test
    void settingsPersistAcrossEnableDisableEnable() {
        ScriptedExtension extension = new ScriptedExtension("waypoints", "ping");
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", extension))));
        BooleanSetting flag = extension.flag();
        flag.set(true);

        ExtensionManager.enable("waypoints");
        ExtensionManager.disable("waypoints");
        ExtensionManager.enable("waypoints");

        assertEquals(true, flag.get(), "disable/enable cycles must not reset values");
        assertSame(flag, ExtensionManager.getSettings("waypoints").get(0),
                "setting identity must survive cycles");
    }

    @Test
    void settingsSurviveSchedulerRefresh() {
        ScriptedExtension extension = new ScriptedExtension("waypoints", "ping");
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", extension))));
        BooleanSetting flag = extension.flag();
        flag.set(true);

        ExtensionManager.setScheduler(Runnable::run);

        assertEquals(true, flag.get());
        assertSame(flag, ExtensionManager.getSettings("waypoints").get(0),
                "context refresh must keep the settings manager");
    }

    @Test
    void onLoadFailureUnregistersExtensionWithoutLeaks() {
        ScriptedExtension broken = new ScriptedExtension("broken", "loadcmd");
        broken.registerOnLoad = true;
        broken.throwOnLoad = true;
        ScriptedExtension healthy = new ScriptedExtension("healthy", "ping");
        healthy.registerOnEnable = true;
        ExtensionManager.init(fakeLoader(List.of(
                entrypoint("broken-mod", broken),
                entrypoint("healthy-mod", healthy))));

        assertEquals(1, broken.loads.get(), "onLoad ran once before failing");
        assertTrue(ExtensionManager.get("broken").isEmpty(),
                "failed load must not leave the extension registered");
        assertTrue(ExtensionManager.getSettings("broken").isEmpty());
        assertTrue(CommandTree.suggest("").stream().noneMatch("loadcmd"::equals),
                "load-time command must not leak");
        assertEquals(List.of(healthy), ExtensionManager.getExtensions(),
                "one failure must not block the other extension");

        ExtensionManager.enable("healthy");
        fireTick();

        assertEquals(1, healthy.tickFires.get(), "healthy extension works normally");
        dispatchExecutes("ping");
    }

    @Test
    void concurrentEnableRunsOnEnableOnce() throws Exception {
        ScriptedExtension extension = new ScriptedExtension("waypoints", "ping");
        extension.registerOnEnable = true;
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", extension))));

        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    ExtensionManager.enable("waypoints");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            thread.setDaemon(true);
            thread.start();
        }
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "all enable calls must return");

        assertEquals(1, extension.enables.get(), "concurrent enables must run onEnable once");
        assertTrue(ExtensionManager.isEnabled("waypoints"));
        fireTick();
        assertEquals(1, extension.tickFires.get(), "no duplicate listener from the race");
    }

    @Test
    void unknownIdsHandledConsistently() {
        ExtensionManager.init(fakeLoader(List.of()));

        ExtensionManager.enable("nope");
        ExtensionManager.disable("nope");

        assertFalse(ExtensionManager.isEnabled("nope"));
        assertTrue(ExtensionManager.getSettings("nope").isEmpty());
        assertTrue(ExtensionManager.getSettingsSection("nope").isEmpty());
    }

    private static void dispatchExecutes(String input) {
        // A successful leaf sends no chat reply; execution is observed
        // through the extension's own counter, so an empty reply list means
        // the command routed and ran.
        assertTrue(dispatchReplies(input).isEmpty(), "expected '" + input + "' to execute");
    }

    private static List<String> dispatchReplies(String input) {
        List<String> replies = new ArrayList<>();
        CommandTree.dispatch(input, replies::add);
        return replies;
    }

    private static void fireTick() {
        for (var listener : ClientTickRegistry.endTickListeners()) {
            listener.onEndTick(null);
        }
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
                ExtensionLifecycleTest.class.getClassLoader(),
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

    /**
     * Configurable extension: registers a command plus a tick listener on
     * enable, a boolean setting on load, and can throw at any callback.
     * Counters are atomic so concurrent-enable races are observable.
     */
    private static final class ScriptedExtension extends Extension {
        private final String id;
        private final String commandName;
        final AtomicInteger loads = new AtomicInteger();
        final AtomicInteger enables = new AtomicInteger();
        final AtomicInteger disables = new AtomicInteger();
        final AtomicInteger tickFires = new AtomicInteger();
        final AtomicInteger commandExecutions = new AtomicInteger();
        volatile boolean registerOnEnable;
        volatile boolean registerOnLoad;
        volatile boolean throwOnLoad;
        volatile boolean throwOnEnable;
        volatile boolean throwOnDisable;
        private volatile BooleanSetting flag;

        ScriptedExtension(String id, String commandName) {
            this.id = id;
            this.commandName = commandName;
        }

        BooleanSetting flag() {
            BooleanSetting current = flag;
            if (current == null) {
                throw new IllegalStateException("setting not registered (onLoad never ran?)");
            }
            return current;
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
                    return "Scripted " + id;
                }

                @Override
                public String getDescription() {
                    return "Lifecycle test extension.";
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
            loads.incrementAndGet();
            if (registerOnLoad) {
                getContext().getCommands().register(leaf(commandName));
                getContext().getEvents().onClientTick(client -> tickFires.incrementAndGet());
                flag = getContext().getSettings()
                        .registerBoolean("flag", "Flag", null, false);
            } else {
                flag = getContext().getSettings()
                        .registerBoolean("flag", "Flag", null, false);
            }
            if (throwOnLoad) {
                throw new IllegalStateException("broken onLoad");
            }
        }

        @Override
        public void onEnable() {
            enables.incrementAndGet();
            if (registerOnEnable) {
                getContext().getCommands().register(leaf(commandName));
                getContext().getEvents().onClientTick(client -> tickFires.incrementAndGet());
            }
            if (throwOnEnable) {
                throw new IllegalStateException("broken onEnable");
            }
        }

        @Override
        public void onDisable() {
            disables.incrementAndGet();
            if (throwOnDisable) {
                throw new IllegalStateException("broken onDisable");
            }
        }

        private Command leaf(String name) {
            return new Command() {
                @Override
                public String name() {
                    return name;
                }

                @Override
                public void execute(CommandContext context) {
                    commandExecutions.incrementAndGet();
                }
            };
        }
    }
}
