package org.mosaicmc.api.command;

import java.util.List;

/**
 * One command node: a single literal token, optional subcommands or
 * positional arguments, and an executor.
 *
 * <p>A node has either subcommands (a group, like {@code extension}) or
 * arguments (a leaf, like {@code enable <id>}), never both. A group needs no
 * executor: the default sends its usage line.
 */
public interface Command {

    /**
     * @return the literal token, lowercase letters/digits/{@code -}/{@code _}
     */
    String name();

    /**
     * @return subcommands, snapshotted at registration; default none
     */
    default List<Command> children() {
        return List.of();
    }

    /**
     * @return trailing positional arguments, snapshotted at registration;
     * default none
     */
    default List<CommandArgument<?>> arguments() {
        return List.of();
    }

    /**
     * Runs the command. The default sends the usage line, which is all a
     * group node needs.
     *
     * @param context execution context, never {@code null}
     */
    default void execute(CommandContext context) {
        context.sendMessage(context.usage());
    }
}
