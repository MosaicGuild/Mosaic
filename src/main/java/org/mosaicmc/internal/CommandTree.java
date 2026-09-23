package org.mosaicmc.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.mosaicmc.Mosaic;
import org.mosaicmc.api.command.Command;
import org.mosaicmc.api.command.CommandArgument;
import org.mosaicmc.api.command.CommandContext;

/**
 * Mosaic's command tree: the single static routing table under
 * {@code /mosaic}. Children and arguments are snapshotted at registration,
 * so later mutation by the implementor cannot desync routing.
 *
 * <p>Pure Java and free of Mojang client references (signatures only
 * reference API types), so dispatch and completion are directly unit
 * testable. Brigadier only ever sees one permanent root with a greedy tail;
 * everything below it is routed here, which is what makes removal honest.
 */
public final class CommandTree {
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9_-]+");

    private record Node(String name, Map<String, Node> children,
            List<CommandArgument<?>> arguments, Command executor) {
    }

    private record RootEntry(Object owner, Node node) {
    }

    private static final Map<String, RootEntry> ROOTS = new LinkedHashMap<>();

    private CommandTree() {
    }

    /**
     * Adds a root. System roots pass a {@code null} owner and are never
     * auto-cleared.
     */
    public static synchronized void addRoot(Object owner, Command command) {
        Objects.requireNonNull(command, "command");
        Node node = freeze(command);
        if (ROOTS.containsKey(node.name())) {
            throw new IllegalArgumentException("Duplicate command root '" + node.name() + "'");
        }
        ROOTS.put(node.name(), new RootEntry(owner, node));
    }

    /**
     * Removes a root by name. Silent if absent.
     */
    public static synchronized void removeRoot(String name) {
        ROOTS.remove(name);
    }

    /**
     * Removes every root owned by {@code owner}.
     */
    public static synchronized void clearOwner(Object owner) {
        ROOTS.values().removeIf(entry -> entry.owner() == owner);
    }

    /**
     * Removes every root. Reset only; never called in normal game operation.
     */
    public static synchronized void clearAll() {
        ROOTS.clear();
    }

    /**
     * Routes already-tokenized input (everything after {@code /mosaic}).
     */
    public static void dispatch(String rest, MessageSink sink) {
        Objects.requireNonNull(sink, "sink");
        List<String> tokens = tokenize(rest == null ? "" : rest);
        Node node = null;
        Map<String, Node> level = rootLevel();
        List<String> path = new ArrayList<>();
        path.add("mosaic");
        int i = 0;
        while (i < tokens.size()) {
            Node next = level.get(tokens.get(i));
            if (next == null) {
                break;
            }
            node = next;
            path.add(node.name());
            level = node.children();
            i++;
        }
        List<String> remaining = tokens.subList(i, tokens.size());

        if (node == null) {
            sink.send("Unknown command. " + topUsage());
            return;
        }
        if (!remaining.isEmpty() && !node.children().isEmpty()) {
            sink.send("Unknown subcommand '" + remaining.get(0) + "'. " + usage(path, node));
            return;
        }
        if (remaining.size() != node.arguments().size()) {
            sink.send(usage(path, node));
            return;
        }
        Map<CommandArgument<?>, Object> parsed = new LinkedHashMap<>();
        for (int a = 0; a < remaining.size(); a++) {
            CommandArgument<?> argument = node.arguments().get(a);
            String raw = remaining.get(a);
            try {
                Object value = argument.parse(raw);
                if (value == null) {
                    throw new IllegalArgumentException("parsed null");
                }
                parsed.put(argument, value);
            } catch (IllegalArgumentException e) {
                sink.send("Invalid value for <" + argument.name() + ">: '" + raw + "'. "
                        + usage(path, node));
                return;
            }
        }
        String line = usage(path, node);
        CommandContext context = new CommandContextImpl(sink, parsed, line);
        try {
            node.executor().execute(context);
        } catch (RuntimeException e) {
            Mosaic.LOGGER.error("[Mosaic] command /{} failed", String.join(" ", path), e);
            sink.send("§cAn internal error occurred while running that command.");
        }
    }

    /**
     * Completions for the text after {@code /mosaic} (partial last token
     * included). Returns literal child names or argument candidates.
     */
    public static List<String> suggest(String rest) {
        String[] parts = (rest == null ? "" : rest).split(" ", -1);
        String partial = parts[parts.length - 1];
        List<String> complete = new ArrayList<>();
        for (int t = 0; t < parts.length - 1; t++) {
            if (!parts[t].isEmpty()) {
                complete.add(parts[t]);
            }
        }

        Node node = null;
        Map<String, Node> level = rootLevel();
        int i = 0;
        while (i < complete.size()) {
            Node next = level.get(complete.get(i));
            if (next == null) {
                return List.of();
            }
            node = next;
            level = node.children();
            i++;
        }
        int leftovers = complete.size() - i;

        if (node == null) {
            return startingWith(level.keySet(), partial);
        }
        if (!node.children().isEmpty()) {
            if (leftovers != 0) {
                return List.of();
            }
            return startingWith(node.children().keySet(), partial);
        }
        if (leftovers >= node.arguments().size()) {
            return List.of();
        }
        try {
            List<String> suggestions = node.arguments().get(leftovers).suggest(partial);
            return suggestions == null ? List.of() : suggestions;
        } catch (RuntimeException e) {
            Mosaic.LOGGER.error("[Mosaic] suggester failed", e);
            return List.of();
        }
    }

    private static Map<String, Node> rootLevel() {
        Map<String, Node> level = new LinkedHashMap<>();
        for (Map.Entry<String, RootEntry> entry : ROOTS.entrySet()) {
            level.put(entry.getKey(), entry.getValue().node());
        }
        return level;
    }

    private static Node freeze(Command command) {
        String name = token(command.name(), "command");
        List<Command> children = command.children();
        List<CommandArgument<?>> arguments = command.arguments();
        Objects.requireNonNull(children, "children of '" + name + "'");
        Objects.requireNonNull(arguments, "arguments of '" + name + "'");
        if (!children.isEmpty() && !arguments.isEmpty()) {
            throw new IllegalArgumentException(
                    "Command '" + name + "' mixes children with arguments; pick one");
        }
        Map<String, Node> frozen = new LinkedHashMap<>();
        for (Command child : children) {
            Objects.requireNonNull(child, "child of '" + name + "'");
            Node node = freeze(child);
            if (frozen.containsKey(node.name())) {
                throw new IllegalArgumentException(
                        "Duplicate subcommand '" + node.name() + "' under '" + name + "'");
            }
            frozen.put(node.name(), node);
        }
        List<CommandArgument<?>> frozenArgs = List.copyOf(arguments);
        java.util.Set<String> argNames = new java.util.HashSet<>();
        for (CommandArgument<?> argument : frozenArgs) {
            Objects.requireNonNull(argument, "argument of '" + name + "'");
            token(argument.name(), "argument of '" + name + "'");
            if (!argNames.add(argument.name())) {
                throw new IllegalArgumentException(
                        "Duplicate argument '" + argument.name() + "' on '" + name + "'");
            }
        }
        return new Node(name, java.util.Collections.unmodifiableMap(frozen), frozenArgs, command);
    }

    private static String token(String value, String what) {
        if (value == null || !TOKEN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Invalid " + what + " name '" + value + "': lowercase letters, digits, '-' and '_'");
        }
        return value;
    }

    private static List<String> tokenize(String rest) {
        String trimmed = rest.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        return List.of(trimmed.split("\\s+"));
    }

    private static List<String> startingWith(Iterable<String> names, String partial) {
        List<String> out = new ArrayList<>();
        for (String name : names) {
            if (name.startsWith(partial)) {
                out.add(name);
            }
        }
        return out;
    }

    private static String usage(List<String> path, Node node) {
        StringBuilder line = new StringBuilder("Usage: /").append(String.join(" ", path));
        for (CommandArgument<?> argument : node.arguments()) {
            line.append(" <").append(argument.name()).append('>');
        }
        if (!node.children().isEmpty()) {
            line.append(" <").append(String.join("|", node.children().keySet())).append('>');
        }
        return line.toString();
    }

    private static String topUsage() {
        if (ROOTS.isEmpty()) {
            return "Usage: /mosaic (no commands registered)";
        }
        return "Usage: /mosaic <" + String.join("|", ROOTS.keySet()) + ">";
    }
}
