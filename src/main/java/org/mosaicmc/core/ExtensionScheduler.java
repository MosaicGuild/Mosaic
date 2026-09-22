package org.mosaicmc.core;

public interface ExtensionScheduler {

    /**
     * Executes a task on the Minecraft client thread.
     *
     * @param task the task to execute
     */
    void execute(Runnable task);
}
