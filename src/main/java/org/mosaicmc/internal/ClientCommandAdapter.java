package org.mosaicmc.internal;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import org.mosaicmc.Mosaic;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The single Fabric hook for {@code /mosaic}. Registers one permanent root
 * with a greedy tail exactly once; parsing, completion, and execution all
 * route into {@link CommandTree}, which never changes Brigadier structure
 * afterwards. Only Brigadier and Fabric API types appear here, so this lives
 * in the common source set; it is installed from the client initializer and
 * never touched on a server.
 */
public final class ClientCommandAdapter {
    private static volatile boolean installed;

    private ClientCommandAdapter() {
    }

    public static void installOnce() {
        if (!installed) {
            synchronized (ClientCommandAdapter.class) {
                if (!installed) {
                    ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
                        var mosaic = ClientCommands.literal("mosaic")
                                .executes(context -> run(context, ""))
                                .then(ClientCommands.argument("rest", StringArgumentType.greedyString())
                                        .suggests(ClientCommandAdapter::suggest)
                                        .executes(context -> run(context, restOf(context))));
                        dispatcher.register(mosaic);
                    });
                    installed = true;
                }
            }
        }
    }

    private static String restOf(CommandContext<FabricClientCommandSource> context) {
        try {
            return context.getArgument("rest", String.class);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static int run(CommandContext<FabricClientCommandSource> context, String rest) {
        MessageSink sink = message ->
                context.getSource().sendFeedback(Component.literal(message));
        try {
            CommandTree.dispatch(rest, sink);
        } catch (RuntimeException e) {
            Mosaic.LOGGER.error("[Mosaic] command failed for input '{}'", rest, e);
            sink.send("§cAn internal error occurred while running that command.");
        }
        return 1;
    }

    private static CompletableFuture<Suggestions> suggest(
            CommandContext<FabricClientCommandSource> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        List<String> candidates;
        try {
            candidates = CommandTree.suggest(remaining);
        } catch (RuntimeException e) {
            Mosaic.LOGGER.error("[Mosaic] suggester failed", e);
            candidates = List.of();
        }
        return complete(builder, remaining, candidates);
    }

    /**
     * Applies per-token candidates with a range narrowed to the partial
     * token only. The greedy tail spans everything after {@code /mosaic}, so
     * suggesting on the original builder would replace all of it; the offset
     * builder mirrors one Brigadier node per token instead, showing the full
     * candidate ({@code disable}) while replacing just the partial
     * ({@code en}).
     */
    public static CompletableFuture<Suggestions> complete(
            SuggestionsBuilder builder, String remaining, List<String> candidates) {
        String text = remaining == null ? "" : remaining;
        SuggestionsBuilder offset =
                builder.createOffset(builder.getStart() + text.length() - partialOf(text).length());
        for (String candidate : candidates) {
            offset.suggest(candidate);
        }
        builder.add(offset);
        return builder.buildFuture();
    }

    private static String partialOf(String text) {
        int cut = text.lastIndexOf(' ');
        return cut < 0 ? text : text.substring(cut + 1);
    }
}
