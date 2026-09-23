package org.mosaicmc.api.command;

import java.util.List;

/**
 * One typed positional argument, declared by a {@link Command} and filled by
 * Mosaic's parser before {@link Command#execute} runs.
 *
 * <p>V1 arguments are single whitespace-delimited tokens; quoting is not
 * honored. Types beyond strings arrive later without breaking implementors:
 * a new argument kind is a new class, not a changed interface.
 */
public interface CommandArgument<T> {

    /**
     * @return the argument name, same token rules as {@link Command#name()}
     */
    String name();

    /**
     * Parses one raw token.
     *
     * @param raw the token, never {@code null}
     * @return the parsed value, never {@code null}
     * @throws IllegalArgumentException if the token is not a valid value
     */
    T parse(String raw);

    /**
     * Completions for a partial token. Return candidates matching
     * {@code prefix}; Mosaic passes them to Brigadier unfiltered.
     *
     * @param prefix the partial token, never {@code null}
     * @return candidates, never {@code null}
     */
    default List<String> suggest(String prefix) {
        return List.of();
    }
}
