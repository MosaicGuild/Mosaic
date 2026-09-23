package org.mosaicmc.test;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.mosaicmc.api.EventRegistration;
import org.mosaicmc.api.ExtensionScheduler;
import org.mosaicmc.extension.ExtensionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-game proof for the managed events API plus the scheduler.
 *
 * <p>End-tick carries the main demo: a ticker that waits 40 in-world ticks,
 * runs the scheduler demo, then unregisters itself, and a watcher that keeps
 * counting to in-world tick 80. If the ticker fires exactly once,
 * unregistration works; any repeat firing is reported as a failure in chat.
 *
 * <p>The remaining tick types each get a one-shot example (log their first
 * fire with the runner thread, then unregister), plus one persistent
 * level-tick counter showing ongoing use.
 */
@Environment(EnvType.CLIENT)
public final class TestExtensionClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("mosaic-testmod-client");

    private EventRegistration ticker;
    private EventRegistration watcher;
    private int ticksInWorld;
    private int totalTicksInWorld;
    private int demoFires;
    private boolean stopFailureLogged;

    @Override
    public void onInitializeClient() {
        var extension = ExtensionManager.get("mosaic-testmod").orElse(null);
        if (extension == null) {
            LOGGER.error("[Mosaic Test] events demo FAILED: extension not registered");
            return;
        }
        var events = extension.getContext().getEvents();
        LOGGER.info("[Mosaic Test] client demo armed via context events={} (waiting for world join)",
                events.getClass().getSimpleName());
        ticker = events.onClientTick(this::onEndTick);
        watcher = events.onClientTick(client -> {
            if (client.player == null || client.level == null) {
                return;
            }
            if (++totalTicksInWorld == 80) {
                reportStopResult(client);
            }
        });
        proveOneShotTick(events);
    }

    /**
     * Examples for the remaining tick types. Each one-shot logs its first
     * fire with the runner thread, announces level ticks in chat (those only
     * fire with a world loaded), then removes itself. The persistent counter
     * shows ongoing use: it logs every 600 in-world level end-ticks.
     */
    private static void proveOneShotTick(org.mosaicmc.api.ExtensionEvents events) {
        var startTickFired = new java.util.concurrent.atomic.AtomicBoolean(false);
        var startTickReg = new java.util.concurrent.atomic.AtomicReference<EventRegistration>();
        startTickReg.set(events.onClientTickStart(client -> {
            if (startTickFired.compareAndSet(false, true)) {
                LOGGER.info("[Mosaic Test] start-tick example: first fire on thread {}, unregistering",
                        Thread.currentThread().getName());
                startTickReg.get().unregister();
            }
        }));

        var startLevelFired = new java.util.concurrent.atomic.AtomicBoolean(false);
        var startLevelReg = new java.util.concurrent.atomic.AtomicReference<EventRegistration>();
        startLevelReg.set(events.onLevelTickStart(level -> {
            if (startLevelFired.compareAndSet(false, true)) {
                String msg = "[Mosaic Test] start-level-tick example: first fire on thread "
                        + Thread.currentThread().getName();
                LOGGER.info("{}", msg);
                sendChat(Minecraft.getInstance(), "§a" + msg);
                startLevelReg.get().unregister();
            }
        }));

        var endLevelFired = new java.util.concurrent.atomic.AtomicBoolean(false);
        var endLevelReg = new java.util.concurrent.atomic.AtomicReference<EventRegistration>();
        endLevelReg.set(events.onLevelTickEnd(level -> {
            if (endLevelFired.compareAndSet(false, true)) {
                String msg = "[Mosaic Test] end-level-tick example: first fire on thread "
                        + Thread.currentThread().getName();
                LOGGER.info("{}", msg);
                sendChat(Minecraft.getInstance(), "§a" + msg);
                endLevelReg.get().unregister();
            }
        }));

        var levelTicks = new java.util.concurrent.atomic.AtomicInteger();
        events.onLevelTickEnd(level -> {
            if (levelTicks.incrementAndGet() % 600 == 0) {
                LOGGER.info("[Mosaic Test] end-level-tick example: still alive, {} level ticks seen",
                        levelTicks.get());
            }
        });
    }

    private void onEndTick(Minecraft client) {
        if (client.player == null || client.level == null) {
            ticksInWorld = 0;
            return;
        }
        if (++ticksInWorld < 40) {
            return;
        }
        demoFires++;
        if (demoFires > 1) {
            if (!stopFailureLogged) {
                stopFailureLogged = true;
                LOGGER.error("[Mosaic Test] events FAILED: ticker fired {} times, unregister did not stop it",
                        demoFires);
                sendChat(client, "§c[Mosaic Test] events FAILED: ticker fired again (fire #"
                        + demoFires + "), unregister did not stop it");
            }
            ticker.unregister();
            return;
        }
        runSchedulerDemo(client, "tick-join");
        ticker.unregister();
        LOGGER.info("[Mosaic Test] ticker unregistered itself after fire #1 (watcher will verify silence)");
    }

    private void reportStopResult(Minecraft client) {
        if (demoFires == 1 && !stopFailureLogged) {
            String msg = "[Mosaic Test] events OK: demo fired once, ticker silent for 40 more ticks";
            LOGGER.info("{}", msg);
            sendChat(client, "§a" + msg);
        } else {
            String msg = "[Mosaic Test] events FAILED: demo fired " + demoFires + " times, expected exactly once";
            LOGGER.error("{}", msg);
            sendChat(client, "§c" + msg);
        }
        watcher.unregister();
    }

    private static void runSchedulerDemo(Minecraft client, String reason) {
        var extension = ExtensionManager.get("mosaic-testmod").orElse(null);
        if (extension == null) {
            LOGGER.error("[Mosaic Test] demo FAILED ({}): extension not registered", reason);
            return;
        }

        var scheduler = extension.getContext().getScheduler();
        LOGGER.info("[Mosaic Test] demo start ({}): scheduler={} on thread {}",
                reason, scheduler.getClass().getSimpleName(), Thread.currentThread().getName());

        scheduler.execute(() -> {
            boolean onClientThread = client.isSameThread();
            String msg = "[Mosaic Test] direct execute: runner=" + Thread.currentThread().getName()
                    + " isClientThread=" + onClientThread;
            LOGGER.info("{}", msg);
            sendChat(client, "§a" + msg + (onClientThread ? " §7(direct OK)" : " §c(direct FAILED)"));
        });

        Thread bg = createBg(client, reason, scheduler);
        bg.start();
    }

    private static @NonNull Thread createBg(Minecraft client, String reason, ExtensionScheduler scheduler) {
        Thread bg = new Thread(() -> {
            String bgThread = Thread.currentThread().getName();
            scheduler.execute(() -> {
                String runner = Thread.currentThread().getName();
                boolean onClientThread = client.isSameThread();
                String msg = "[Mosaic Test] hop OK (" + reason + "): bg=" + bgThread
                        + " -> client=" + runner + " isClientThread=" + onClientThread;
                LOGGER.info("{}", msg);
                sendChat(client, "§a" + msg + (onClientThread ? " §7(hop OK)" : " §c(hop FAILED)"));
                if (client.player != null) {
                    sendChat(client, "§7[Mosaic Test] player=" + client.player.getName().getString()
                            + " scheduler=" + scheduler.getClass().getSimpleName());
                }
            });
        }, "mosaic-test-bg");
        bg.setDaemon(true);
        return bg;
    }

    private static void sendChat(Minecraft client, String text) {
        try {
            client.gui.hud.getChat().addClientSystemMessage(Component.literal(text));
        } catch (Exception e) {
            LOGGER.error("[Mosaic Test] failed to send chat message", e);
        }
    }
}
