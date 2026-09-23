package org.mosaicmc.internal;

import java.util.Objects;

import org.mosaicmc.api.ExtensionContext;
import org.mosaicmc.api.ExtensionEvents;
import org.mosaicmc.api.ExtensionScheduler;
import org.mosaicmc.api.command.CommandManager;

public final class ExtensionContextImpl implements ExtensionContext {

    private final ExtensionScheduler scheduler;
    private final ExtensionEvents events;
    private final CommandManager commands;

    public ExtensionContextImpl(ExtensionScheduler scheduler) {
        this(scheduler, new ExtensionEventsImpl(), new ExtensionCommandManager());
    }

    public ExtensionContextImpl(ExtensionScheduler scheduler, ExtensionEvents events) {
        this(scheduler, events, new ExtensionCommandManager());
    }

    public ExtensionContextImpl(ExtensionScheduler scheduler, ExtensionEvents events,
            CommandManager commands) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.events = Objects.requireNonNull(events, "events");
        this.commands = Objects.requireNonNull(commands, "commands");
    }

    @Override
    public ExtensionScheduler getScheduler() {
        return scheduler;
    }

    @Override
    public ExtensionEvents getEvents() {
        return events;
    }

    @Override
    public CommandManager getCommands() {
        return commands;
    }
}
