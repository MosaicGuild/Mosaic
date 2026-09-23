package org.mosaicmc.internal;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.mosaicmc.api.command.Command;
import org.mosaicmc.api.command.CommandManager;

/**
 * One extension's command facade. Roots are name-keyed in the shared tree;
 * this facade remembers which names are yours so disable can remove exactly
 * them. Reused across context refreshes like the events bridge.
 */
public final class ExtensionCommandManager implements CommandManager {
    private final Set<String> owned = ConcurrentHashMap.newKeySet();

    @Override
    public void register(Command command) {
        Objects.requireNonNull(command, "command");
        CommandTree.addRoot(this, command);
        owned.add(command.name());
    }

    @Override
    public void unregister(Command command) {
        Objects.requireNonNull(command, "command");
        // Only your own roots: never Mosaic's, never another extension's.
        // Anything else (including never-registered names) is a silent no-op.
        if (owned.remove(command.name())) {
            CommandTree.removeRoot(command.name());
        }
    }

    /**
     * Removes all of this extension's roots. Called by the extension manager
     * when the extension is disabled; Mosaic-internal.
     */
    public void clear() {
        for (String name : Set.copyOf(owned)) {
            CommandTree.removeRoot(name);
        }
        owned.clear();
    }
}
