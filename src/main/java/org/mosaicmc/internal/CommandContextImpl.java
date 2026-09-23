package org.mosaicmc.internal;

import java.util.Map;
import java.util.Objects;

import org.mosaicmc.api.command.CommandArgument;
import org.mosaicmc.api.command.CommandContext;

/**
 * {@link CommandContext} over one matched node: a message sink, pre-parsed
 * arguments keyed by their declared instances, and the usage line.
 */
public final class CommandContextImpl implements CommandContext {
    private final MessageSink sink;
    private final Map<CommandArgument<?>, Object> parsed;
    private final String usage;

    public CommandContextImpl(MessageSink sink, Map<CommandArgument<?>, Object> parsed, String usage) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.parsed = Map.copyOf(Objects.requireNonNull(parsed, "parsed"));
        this.usage = Objects.requireNonNull(usage, "usage");
    }

    @Override
    public void sendMessage(String message) {
        Objects.requireNonNull(message, "message");
        sink.send(message);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T arg(CommandArgument<T> argument) {
        Objects.requireNonNull(argument, "argument");
        if (!parsed.containsKey(argument)) {
            throw new IllegalArgumentException("Unknown argument '" + argument.name() + "'");
        }
        return (T) parsed.get(argument);
    }

    @Override
    public String usage() {
        return usage;
    }
}
