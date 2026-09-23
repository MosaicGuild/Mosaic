package org.mosaicmc.api.command;

/**
 * What an executing command can do: reply and read its parsed arguments.
 */
public interface CommandContext {

    /**
     * Sends a chat message to the executing player. {@code §}-codes render.
     *
     * @param message the message, must not be {@code null}
     */
    void sendMessage(String message);

    /**
     * Reads a declared argument's parsed value.
     *
     * @param argument one of the executing command's declared arguments
     * @return the parsed value, never {@code null}
     * @throws NullPointerException if {@code argument} is {@code null}
     * @throws IllegalArgumentException if {@code argument} was not declared
     */
    <T> T arg(CommandArgument<T> argument);

    /**
     * @return the usage line for the matched command, never {@code null}
     */
    String usage();
}
