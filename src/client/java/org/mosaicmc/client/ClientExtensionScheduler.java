package org.mosaicmc.client;

import java.util.Objects;

import net.minecraft.client.Minecraft;
import org.mosaicmc.api.ExtensionScheduler;

/**
 * {@link ExtensionScheduler} that hops onto the Minecraft client thread.
 */
public final class ClientExtensionScheduler implements ExtensionScheduler {

    public static final ClientExtensionScheduler INSTANCE = new ClientExtensionScheduler();

    private ClientExtensionScheduler() {
    }

    @Override
    public void execute(Runnable task) {
        Objects.requireNonNull(task, "task");
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            task.run();
            return;
        }
        if (minecraft.isSameThread()) {
            task.run();
        } else {
            minecraft.execute(task);
        }
    }
}
