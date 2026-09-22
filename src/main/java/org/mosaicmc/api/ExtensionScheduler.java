package org.mosaicmc.api;

public interface ExtensionScheduler {

    /**
     * Executes a task on the Minecraft client thread.
     *
     * <p>Threading contract:
     * <ul>
     *   <li>If called from the client thread, the task runs immediately
     *   (inline) on the calling thread.</li>
     *   <li>If called from any other thread, the task is handed to the
     *   client thread and runs there; ordering between tasks scheduled from
     *   the same thread is preserved.</li>
     *   <li>If no client is available (for example before the client has
     *   booted, or on a dedicated server where there is no client thread),
     *   the task runs inline on the calling thread as a fallback.</li>
     * </ul>
     *
     * <p>Exceptions thrown by the task propagate to the caller when run
     * inline, or to the client-thread loop when scheduled; they are never
     * silently retried.
     *
     * @param task the task to execute, must not be {@code null}
     * @throws NullPointerException if {@code task} is {@code null}
     */
    void execute(Runnable task);
}
