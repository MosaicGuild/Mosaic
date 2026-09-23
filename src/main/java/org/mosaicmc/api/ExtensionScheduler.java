package org.mosaicmc.api;

public interface ExtensionScheduler {

    /**
     * Executes a task on the Minecraft client thread.
     *
     * <p>Already on the client thread: runs inline. Otherwise handed to the
     * client thread, preserving order per submitting thread. With no client
     * available (not yet booted, or dedicated server), runs inline as a
     * fallback.
     *
     * <p>Task exceptions propagate to the caller when run inline, or to the
     * client-thread loop when scheduled; never silently retried.
     *
     * @param task the task to execute, must not be {@code null}
     * @throws NullPointerException if {@code task} is {@code null}
     */
    void execute(Runnable task);
}
