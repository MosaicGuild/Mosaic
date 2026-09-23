package org.mosaicmc.api.command;

/**
 * Registers command roots under {@code /mosaic}. V1 is scoped to that
 * namespace only: third-party roots ({@code /waypoints ...}) are deferred,
 * because each permanent Brigadier node would weaken the unregister
 * guarantee this interface makes.
 */
public interface CommandManager {

    /**
     * Registers a root command (available as {@code /mosaic <name> ...}).
     *
     * @param command the root, must not be {@code null}
     * @throws NullPointerException if {@code command} is {@code null}
     * @throws IllegalArgumentException if the tree is invalid (bad token,
     * duplicate names, children mixed with arguments) or the name is taken
     */
    void register(Command command);

    /**
     * Removes a root by name. Silent if absent: idempotent, never throws.
     *
     * @param command the root to remove, must not be {@code null}
     * @throws NullPointerException if {@code command} is {@code null}
     */
    void unregister(Command command);
}
