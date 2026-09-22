package org.mosaicmc.test;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.mosaicmc.extension.ExtensionManager;
import org.mosaicmc.api.ExtensionScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-game proof for {@code Extension -> getContext() -> getScheduler() -> execute()}.
 *
 * <p>Waits until the player is in a world, then from a background thread hops
 * through the extension scheduler back onto the client thread and prints the
 * thread names both to the log and to in-game chat. If the hop did not land on
 * the client thread, the chat message says so explicitly.
 */
@Environment(EnvType.CLIENT)
public final class TestExtensionClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("mosaic-testmod-client");

    private int ticksInWorld;
    private boolean announced;

    @Override
    public void onInitializeClient() {
        var extension = ExtensionManager.get("mosaic-testmod").orElse(null);
        if (extension == null) {
            LOGGER.warn("[Mosaic Test] extension not registered yet, registering tick directly");
            ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
            return;
        }
        var events = extension.getContext().getEvents();
        LOGGER.info("[Mosaic Test] client demo armed via context events={} (waiting for world join)",
                events.getClass().getSimpleName());
        events.register(ClientTickEvents.END_CLIENT_TICK, this::onEndTick);
    }

    private void onEndTick(Minecraft client) {
        if (client.player == null || client.level == null) {
            ticksInWorld = 0;
            announced = false;
            return;
        }
        if (announced) {
            return;
        }
        if (++ticksInWorld < 40) {
            return;
        }
        announced = true;
        runSchedulerDemo(client, "tick-join");
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
