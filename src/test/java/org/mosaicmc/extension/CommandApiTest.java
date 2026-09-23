package org.mosaicmc.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mosaicmc.api.ExtensionMetadata;
import org.mosaicmc.api.command.Command;
import org.mosaicmc.api.command.CommandArgument;
import org.mosaicmc.api.command.CommandArguments;
import org.mosaicmc.api.command.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import org.mosaicmc.internal.ClientCommandAdapter;
import org.mosaicmc.internal.CommandTree;
import org.mosaicmc.internal.MosaicCommands;

/**
 * Proves the command tree: validation, nested dispatch, arguments, usage
 * errors, completion, unregistration, per-extension lifecycle, and Mosaic's
 * own commands. Dispatch and completion run through pure-Java seams with a
 * collecting sink; no Minecraft runtime needed.
 */
class CommandApiTest {

    @BeforeEach
    void clearState() {
        ExtensionManager.resetForTesting();
    }

    @Test
    void rejectsBlankName() {
        assertThrows(IllegalArgumentException.class, () -> CommandTree.addRoot(null, leaf("")));
        assertThrows(IllegalArgumentException.class, () -> CommandTree.addRoot(null, leaf("  ")));
    }

    @Test
    void rejectsSpacedOrUppercaseName() {
        assertThrows(IllegalArgumentException.class, () -> CommandTree.addRoot(null, leaf("two words")));
        assertThrows(IllegalArgumentException.class, () -> CommandTree.addRoot(null, leaf("Shouty")));
    }

    @Test
    void rejectsDuplicateRoot() {
        CommandTree.addRoot(null, leaf("ping", ctx -> {
        }));
        assertThrows(IllegalArgumentException.class,
                () -> CommandTree.addRoot(null, leaf("ping", ctx -> {
                })));
    }

    @Test
    void rejectsDuplicateChildren() {
        Command group = group("root",
                List.of(leaf("same", ctx -> {
                }), leaf("same", ctx -> {
                })));
        assertThrows(IllegalArgumentException.class, () -> CommandTree.addRoot(null, group));
    }

    @Test
    void rejectsChildrenMixedWithArguments() {
        Command mixed = new Command() {
            @Override
            public String name() {
                return "mixed";
            }

            @Override
            public List<Command> children() {
                return List.of(leaf("sub", ctx -> {
                }));
            }

            @Override
            public List<CommandArgument<?>> arguments() {
                return List.of(CommandArguments.string("arg"));
            }

            @Override
            public void execute(CommandContext context) {
            }
        };
        assertThrows(IllegalArgumentException.class, () -> CommandTree.addRoot(null, mixed));
    }

    @Test
    void rejectsNullCommand() {
        assertThrows(NullPointerException.class, () -> CommandTree.addRoot(null, null));
    }

    @Test
    void nestedDispatchReachesLeafWithArgs() {
        List<String> replies = new ArrayList<>();
        CommandArgument<String> target = CommandArguments.string("target");
        Command root = group("greet", List.of(new Command() {
            @Override
            public String name() {
                return "hello";
            }

            @Override
            public List<CommandArgument<?>> arguments() {
                return List.of(target);
            }

            @Override
            public void execute(CommandContext context) {
                replies.add("hi " + context.<String>arg(target));
            }
        }));
        CommandTree.addRoot(null, root);

        CommandTree.dispatch("greet hello world", replies::add);

        assertEquals(List.of("hi world"), replies);
    }

    @Test
    void missingArgSendsUsage() {
        List<String> replies = new ArrayList<>();
        CommandTree.addRoot(null, group("greet", List.of(withArgs("hello", CommandArguments.string("target")))));

        CommandTree.dispatch("greet hello", replies::add);

        assertEquals(1, replies.size());
        assertTrue(replies.get(0).startsWith("Usage: /mosaic greet hello <target>"),
                "was: " + replies.get(0));
    }

    @Test
    void extraTokensSendUsage() {
        List<String> replies = new ArrayList<>();
        CommandTree.addRoot(null, group("greet", List.of(withArgs("hello", CommandArguments.string("target")))));

        CommandTree.dispatch("greet hello a b", replies::add);

        assertEquals(1, replies.size());
        assertTrue(replies.get(0).startsWith("Usage:"), "was: " + replies.get(0));
    }

    @Test
    void unknownRootSendsTopUsage() {
        List<String> replies = new ArrayList<>();
        CommandTree.addRoot(null, leaf("ping", ctx -> {
        }));

        CommandTree.dispatch("nope", replies::add);

        assertEquals(List.of("Unknown command. Usage: /mosaic <ping>"), replies);
    }

    @Test
    void unknownSubcommandNamesItself() {
        List<String> replies = new ArrayList<>();
        CommandTree.addRoot(null, group("root", List.of(leaf("known", ctx -> {
        }))));

        CommandTree.dispatch("root bogus", replies::add);

        assertEquals(1, replies.size());
        assertTrue(replies.get(0).contains("'bogus'"), "was: " + replies.get(0));
    }

    @Test
    void bareGroupSendsItsUsage() {
        List<String> replies = new ArrayList<>();
        CommandTree.addRoot(null, group("root", List.of(leaf("known", ctx -> {
        }))));

        CommandTree.dispatch("root", replies::add);

        assertEquals(List.of("Usage: /mosaic root <known>"), replies);
    }

    @Test
    void throwingExecutorBecomesErrorMessage() {
        List<String> replies = new ArrayList<>();
        CommandTree.addRoot(null, new Command() {
            @Override
            public String name() {
                return "boom";
            }

            @Override
            public void execute(CommandContext context) {
                throw new IllegalStateException("broken executor");
            }
        });

        CommandTree.dispatch("boom", replies::add);

        assertEquals(1, replies.size());
        assertTrue(replies.get(0).contains("internal error"), "was: " + replies.get(0));
    }

    @Test
    void invalidArgValueSendsUsage() {
        List<String> replies = new ArrayList<>();
        CommandArgument<String> number = new CommandArgument<>() {
            @Override
            public String name() {
                return "number";
            }

            @Override
            public String parse(String raw) {
                try {
                    Integer.parseInt(raw);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("not a number: " + raw);
                }
                return raw;
            }
        };
        CommandTree.addRoot(null, group("calc", List.of(withArgs("double", number))));

        CommandTree.dispatch("calc double abc", replies::add);

        assertEquals(1, replies.size());
        assertTrue(replies.get(0).contains("Invalid value for <number>: 'abc'"),
                "was: " + replies.get(0));
        assertTrue(replies.get(0).contains("Usage:"), "was: " + replies.get(0));
    }

    @Test
    void unregisterRemovesRoot() {
        List<String> replies = new ArrayList<>();
        Command ping = leaf("ping", ctx -> replies.add("pong"));
        CommandTree.addRoot(null, ping);

        CommandTree.dispatch("ping", replies::add);
        assertEquals(List.of("pong"), replies);

        CommandTree.removeRoot("ping");
        CommandTree.dispatch("ping", replies::add);

        assertEquals(List.of("pong", "Unknown command. Usage: /mosaic (no commands registered)"),
                replies);
    }

    @Test
    void unregisterAbsentIsSilent() {
        CommandTree.removeRoot("never-there");
    }

    @Test
    void unregisterOneRootLeavesOther() {
        List<String> replies = new ArrayList<>();
        CommandTree.addRoot(null, leaf("aaa", ctx -> replies.add("A")));
        CommandTree.addRoot(null, leaf("bbb", ctx -> replies.add("B")));

        CommandTree.removeRoot("aaa");
        CommandTree.dispatch("bbb", replies::add);

        assertEquals(List.of("B"), replies);
    }

    @Test
    void suggestsRootsAndFiltersByPrefix() {
        CommandTree.addRoot(null, leaf("aaa", ctx -> {
        }));
        CommandTree.addRoot(null, leaf("aab", ctx -> {
        }));
        CommandTree.addRoot(null, leaf("bbb", ctx -> {
        }));

        assertEquals(List.of("aaa", "aab", "bbb"), CommandTree.suggest(""));
        assertEquals(List.of("aaa", "aab"), CommandTree.suggest("aa"));
        assertEquals(List.of(), CommandTree.suggest("zzz"));
    }

    @Test
    void suggestsChildrenThenArgValues() {
        CommandArgument<String> id = CommandArguments.string("id",
                prefix -> List.of("alpha", "alpine", "beta").stream()
                        .filter(s -> s.startsWith(prefix))
                        .toList());
        CommandTree.addRoot(null, group("root", List.of(withArgs("leaf", id))));

        assertEquals(List.of("root"), CommandTree.suggest(""));
        assertEquals(List.of("root"), CommandTree.suggest("r"));
        assertEquals(List.of("leaf"), CommandTree.suggest("root "));
        assertEquals(List.of("leaf"), CommandTree.suggest("root le"));
        assertEquals(List.of("alpha", "alpine", "beta"), CommandTree.suggest("root leaf "));
        assertEquals(List.of("alpha", "alpine"), CommandTree.suggest("root leaf al"));
        assertEquals(List.of(), CommandTree.suggest("root leaf alpha "));
        assertEquals(List.of(), CommandTree.suggest("root bogus "));
    }

    @Test
    void extensionLifecycleThroughFacade() {
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", new DummyExtension()))));
        var commands = ExtensionManager.get("waypoints").orElseThrow().getContext().getCommands();

        List<String> replies = new ArrayList<>();
        Command ping = leaf("ping", ctx -> replies.add("pong"));
        commands.register(ping);
        CommandTree.dispatch("ping", replies::add);
        assertEquals(List.of("pong"), replies);

        commands.unregister(ping);
        CommandTree.dispatch("ping", replies::add);
        assertEquals(2, replies.size());
        assertTrue(replies.get(1).startsWith("Unknown command."), "was: " + replies.get(1));
    }

    @Test
    void unregisterTwiceIsSilent() {
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", new DummyExtension()))));
        var commands = ExtensionManager.get("waypoints").orElseThrow().getContext().getCommands();

        Command ping = leaf("ping", ctx -> {
        });
        commands.register(ping);
        commands.unregister(ping);
        commands.unregister(ping);
    }

    @Test
    void facadeCannotRemoveForeignRoots() {
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", new DummyExtension()))));
        var commands = ExtensionManager.get("waypoints").orElseThrow().getContext().getCommands();

        List<String> replies = new ArrayList<>();
        CommandTree.addRoot(null, leaf("system", ctx -> replies.add("S")));
        commands.unregister(leaf("system", ctx -> {
        }));

        CommandTree.dispatch("system", replies::add);
        assertEquals(List.of("S"), replies);
    }

    @Test
    void disableRemovesExtensionCommands() {
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", new DummyExtension()))));
        var commands = ExtensionManager.get("waypoints").orElseThrow().getContext().getCommands();

        List<String> replies = new ArrayList<>();
        commands.register(leaf("ping", ctx -> replies.add("pong")));

        ExtensionManager.enable("waypoints");
        ExtensionManager.disable("waypoints");
        CommandTree.dispatch("ping", replies::add);

        assertEquals(1, replies.size());
        assertTrue(replies.get(0).startsWith("Unknown command."), "was: " + replies.get(0));
    }

    @Test
    void failedEnableRollsBackCommands() {
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", new RegisteringExtension()))));

        ExtensionManager.enable("waypoints");

        List<String> replies = new ArrayList<>();
        CommandTree.dispatch("ping", replies::add);

        assertEquals(1, replies.size());
        assertTrue(replies.get(0).startsWith("Unknown command."),
                "half-registered root must not survive a failed enable, was: " + replies.get(0));
    }

    @Test
    void commandsSurviveSchedulerSwap() {
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", new DummyExtension()))));
        var commands = ExtensionManager.get("waypoints").orElseThrow().getContext().getCommands();

        List<String> replies = new ArrayList<>();
        commands.register(leaf("ping", ctx -> replies.add("pong")));

        ExtensionManager.setScheduler(Runnable::run);
        CommandTree.dispatch("ping", replies::add);

        assertEquals(List.of("pong"), replies);
    }

    @Test
    void mosaicListShowsRegisteredIds() {
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", new DummyExtension()))));
        MosaicCommands.registerDefaults();

        List<String> replies = new ArrayList<>();
        CommandTree.dispatch("extension list", replies::add);

        assertEquals(2, replies.size());
        assertTrue(replies.get(0).contains("(1)"), "was: " + replies.get(0));
        assertTrue(replies.get(1).contains("waypoints"), "was: " + replies.get(1));
    }

    @Test
    void mosaicListEmpty() {
        MosaicCommands.registerDefaults();

        List<String> replies = new ArrayList<>();
        CommandTree.dispatch("extension list", replies::add);

        assertEquals(List.of("§7No extensions registered."), replies);
    }

    @Test
    void mosaicEnableUnknownErrors() {
        MosaicCommands.registerDefaults();
        DummyExtension extension = new DummyExtension();
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", extension))));

        List<String> replies = new ArrayList<>();
        CommandTree.dispatch("extension enable nope", replies::add);

        assertEquals(1, replies.size());
        assertTrue(replies.get(0).contains("Unknown extension 'nope'"), "was: " + replies.get(0));
        assertEquals(0, extension.enables);
    }

    @Test
    void mosaicEnableKnownConfirmsAndRunsLifecycle() {
        MosaicCommands.registerDefaults();
        DummyExtension extension = new DummyExtension();
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", extension))));

        List<String> replies = new ArrayList<>();
        CommandTree.dispatch("extension enable waypoints", replies::add);

        assertEquals(List.of("§aEnabled waypoints."), replies);
        assertEquals(1, extension.enables);
    }

    @Test
    void mosaicDisableRunsLifecycleAndRemovesCommands() {
        MosaicCommands.registerDefaults();
        DummyExtension extension = new DummyExtension();
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", extension))));
        var commands = ExtensionManager.get("waypoints").orElseThrow().getContext().getCommands();

        List<String> replies = new ArrayList<>();
        commands.register(leaf("ping", ctx -> replies.add("pong")));

        CommandTree.dispatch("extension enable waypoints", replies::add);
        CommandTree.dispatch("extension disable waypoints", replies::add);

        assertEquals(1, extension.disables);
        assertEquals("§aEnabled waypoints.", replies.get(0));
        assertTrue(replies.get(1).contains("Disabled waypoints."), "was: " + replies.get(1));

        CommandTree.dispatch("ping", replies::add);
        assertTrue(replies.get(2).startsWith("Unknown command."), "was: " + replies.get(2));
    }

    @Test
    void idCompletionListsRegisteredIds() {
        MosaicCommands.registerDefaults();
        ExtensionManager.init(fakeLoader(List.of(
                entrypoint("mod-a", new DummyExtension("alpha")),
                entrypoint("mod-b", new DummyExtension("alpine")),
                entrypoint("mod-c", new DummyExtension("beta")))));

        assertEquals(List.of("alpha", "alpine", "beta"),
                CommandTree.suggest("extension enable "));
        assertEquals(List.of("alpha", "alpine"), CommandTree.suggest("extension enable al"));
        assertEquals(List.of("beta"), CommandTree.suggest("extension enable b"));
    }

    @Test
    void narrowedSuggestionsCoverOnlyThePartialToken() {
        // "/mosaic extension en", greedy tail starts at 8, partial "en" at 19.
        SuggestionsBuilder builder = new SuggestionsBuilder("/mosaic extension en", 8);

        Suggestions suggestions = ClientCommandAdapter
                .complete(builder, "extension en", List.of("enable"))
                .join();

        assertEquals(1, suggestions.getList().size());
        var suggestion = suggestions.getList().get(0);
        assertEquals("enable", suggestion.getText());
        assertEquals(18, suggestion.getRange().getStart());
        assertEquals(20, suggestion.getRange().getEnd());
        assertEquals("/mosaic extension enable",
                suggestion.apply("/mosaic extension en"));
    }

    @Test
    void narrowedSuggestionsAtFreshTokenCoverNothingTyped() {
        // "/mosaic extension " is 18 chars; the empty partial token yields a
        // zero-width range at the end, and accepting inserts the full text.
        SuggestionsBuilder builder = new SuggestionsBuilder("/mosaic extension ", 8);

        Suggestions suggestions = ClientCommandAdapter
                .complete(builder, "extension ", List.of("enable", "disable"))
                .join();

        assertEquals(2, suggestions.getList().size());
        var byText = new java.util.HashMap<String, String>();
        for (var suggestion : suggestions.getList()) {
            assertEquals(18, suggestion.getRange().getStart());
            assertEquals(18, suggestion.getRange().getEnd());
            byText.put(suggestion.getText(),
                    suggestion.apply("/mosaic extension "));
        }
        assertEquals("/mosaic extension disable",
                byText.get("disable"));
        assertEquals("/mosaic extension enable",
                byText.get("enable"));
    }

    private static Command leaf(String name) {
        return leaf(name, ctx -> {
        });
    }

    private static Command leaf(String name, java.util.function.Consumer<CommandContext> run) {
        return new Command() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void execute(CommandContext context) {
                run.accept(context);
            }
        };
    }

    private static Command group(String name, List<Command> children) {
        return new Command() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public List<Command> children() {
                return children;
            }
        };
    }

    private static Command withArgs(String name, CommandArgument<?>... args) {
        return new Command() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public List<CommandArgument<?>> arguments() {
                return List.of(args);
            }

            @Override
            public void execute(CommandContext context) {
            }
        };
    }

    private static FabricLoader fakeLoader(List<EntrypointContainer<Extension>> containers) {
        return fake(FabricLoader.class, Map.of("getEntrypointContainers", containers));
    }

    private static EntrypointContainer<Extension> entrypoint(String modId, Extension extension) {
        return fake(EntrypointContainer.class, Map.of(
                "getProvider", fake(ModContainer.class, Map.of(
                        "getMetadata", fake(ModMetadata.class, Map.of("getId", modId)))),
                "getEntrypoint", extension));
    }

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, Map<String, Object> stubs) {
        return (T) Proxy.newProxyInstance(
                CommandApiTest.class.getClassLoader(),
                new Class<?>[] { type },
                (proxy, method, args) -> {
                    if (stubs.containsKey(method.getName())) {
                        return stubs.get(method.getName());
                    }
                    return null;
                });
    }

    /**
     * Registers a root, then throws: proves a failed enable rolls back
     * partial command registrations instead of leaking them.
     */
    private static final class RegisteringExtension extends Extension {
        @Override
        public ExtensionMetadata getMetadata() {
            return new ExtensionMetadata() {
                @Override
                public String getId() {
                    return "waypoints";
                }

                @Override
                public String getName() {
                    return "Dummy";
                }

                @Override
                public String getDescription() {
                    return "Dummy.";
                }

                @Override
                public String getVersion() {
                    return "0.0.0-test";
                }

                @Override
                public String getAuthors() {
                    return "tests";
                }

                @Override
                public String getWebsite() {
                    return "";
                }
            };
        }

        @Override
        public void onEnable() {
            getContext().getCommands().register(leaf("ping", ctx -> {
            }));
            throw new IllegalStateException("broken second step");
        }

        @Override
        public void onDisable() {
        }

        @Override
        public void onLoad() {
        }
    }

    private static final class DummyExtension extends Extension {
        private final String id;
        private int enables;
        private int disables;

        DummyExtension() {
            this("waypoints");
        }

        DummyExtension(String id) {
            this.id = id;
        }

        @Override
        public ExtensionMetadata getMetadata() {
            return new ExtensionMetadata() {
                @Override
                public String getId() {
                    return id;
                }

                @Override
                public String getName() {
                    return "Dummy";
                }

                @Override
                public String getDescription() {
                    return "Dummy.";
                }

                @Override
                public String getVersion() {
                    return "0.0.0-test";
                }

                @Override
                public String getAuthors() {
                    return "tests";
                }

                @Override
                public String getWebsite() {
                    return "";
                }
            };
        }

        @Override
        public void onEnable() {
            enables++;
        }

        @Override
        public void onDisable() {
            disables++;
        }

        @Override
        public void onLoad() {
        }
    }
}
