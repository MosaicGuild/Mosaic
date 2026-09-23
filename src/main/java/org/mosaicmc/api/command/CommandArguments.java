package org.mosaicmc.api.command;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Factories for the built-in argument kinds.
 */
public final class CommandArguments {
    private CommandArguments() {
    }

    /**
     * A plain string argument (V1's only kind).
     *
     * @param name argument name, same token rules as {@link Command#name()}
     */
    public static CommandArgument<String> string(String name) {
        return string(name, prefix -> List.of());
    }

    /**
     * A plain string argument with tab-completion.
     *
     * @param name argument name, same token rules as {@link Command#name()}
     * @param suggester candidates matching a partial token, never
     * {@code null} and never returning {@code null}
     */
    public static CommandArgument<String> string(
            String name, Function<String, List<String>> suggester) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(suggester, "suggester");
        return new CommandArgument<>() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String parse(String raw) {
                Objects.requireNonNull(raw, "raw");
                return raw;
            }

            @Override
            public List<String> suggest(String prefix) {
                List<String> suggestions = suggester.apply(prefix);
                return suggestions == null ? List.of() : suggestions;
            }
        };
    }
}
