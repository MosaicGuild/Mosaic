package org.mosaicmc.internal;

import java.util.List;

import org.mosaicmc.api.command.Command;
import org.mosaicmc.api.command.CommandArgument;
import org.mosaicmc.api.command.CommandArguments;
import org.mosaicmc.api.command.CommandContext;
import org.mosaicmc.extension.Extension;
import org.mosaicmc.extension.ExtensionManager;

/**
 * Mosaic's own commands under {@code /mosaic}: extension list/enable/disable.
 * Registered once at startup through the system path (no owner, never
 * auto-cleared); the first real consumer of the command tree, and the
 * in-game trigger for enable/disable.
 */
public final class MosaicCommands {
    static final CommandArgument<String> ID =
            CommandArguments.string("id", MosaicCommands::suggestIds);

    private MosaicCommands() {
    }

    public static void registerDefaults() {
        CommandTree.removeRoot("extension");
        CommandTree.addRoot(null, extension());
    }

    public static Command extension() {
        return group("extension", List.of(list(), enable(), disable()));
    }

    static Command group(String name, List<Command> children) {
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

    private static Command list() {
        return new Command() {
            @Override
            public String name() {
                return "list";
            }

            @Override
            public void execute(CommandContext context) {
                var extensions = ExtensionManager.getExtensions();
                if (extensions.isEmpty()) {
                    context.sendMessage("§7No extensions registered.");
                    return;
                }
                context.sendMessage("§7Registered extensions (" + extensions.size() + "):");
                for (Extension extension : extensions) {
                    String id;
                    String detail;
                    try {
                        var metadata = extension.getMetadata();
                        id = metadata.getId();
                        detail = metadata.getName() + " v" + metadata.getVersion();
                    } catch (RuntimeException e) {
                        id = "?";
                        detail = "broken metadata";
                    }
                    context.sendMessage("§a  " + id + " §7- " + detail);
                }
            }
        };
    }

    private static Command enable() {
        return new Command() {
            @Override
            public String name() {
                return "enable";
            }

            @Override
            public List<CommandArgument<?>> arguments() {
                return List.of(ID);
            }

            @Override
            public void execute(CommandContext context) {
                String id = context.arg(ID);
                if (ExtensionManager.get(id).isEmpty()) {
                    context.sendMessage("§cUnknown extension '" + id + "'. " + context.usage());
                    return;
                }
                ExtensionManager.enable(id);
                context.sendMessage("§aEnabled " + id + ".");
            }
        };
    }

    private static Command disable() {
        return new Command() {
            @Override
            public String name() {
                return "disable";
            }

            @Override
            public List<CommandArgument<?>> arguments() {
                return List.of(ID);
            }

            @Override
            public void execute(CommandContext context) {
                String id = context.arg(ID);
                if (ExtensionManager.get(id).isEmpty()) {
                    context.sendMessage("§cUnknown extension '" + id + "'. " + context.usage());
                    return;
                }
                ExtensionManager.disable(id);
                context.sendMessage("§aDisabled " + id + ". Its commands were removed.");
            }
        };
    }

    private static List<String> suggestIds(String prefix) {
        return ExtensionManager.getExtensions().stream()
                .map(extension -> {
                    try {
                        return extension.getMetadata().getId();
                    } catch (RuntimeException e) {
                        return null;
                    }
                })
                .filter(id -> id != null && id.startsWith(prefix))
                .sorted()
                .toList();
    }
}
