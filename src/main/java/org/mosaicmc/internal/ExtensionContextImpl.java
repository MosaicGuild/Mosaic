package org.mosaicmc.internal;

import java.util.Objects;

import org.mosaicmc.api.ExtensionContext;
import org.mosaicmc.api.ExtensionEvents;
import org.mosaicmc.api.ExtensionScheduler;
import org.mosaicmc.api.command.CommandManager;
import org.mosaicmc.api.settings.Settings;
import org.mosaicmc.api.storage.ExtensionStorage;

public final class ExtensionContextImpl implements ExtensionContext {

    private final ExtensionScheduler scheduler;
    private final ExtensionEvents events;
    private final CommandManager commands;
    private final Settings settings;
    private final ExtensionStorage storage;

    public ExtensionContextImpl(ExtensionScheduler scheduler) {
        this(scheduler, new ExtensionEventsImpl(), new ExtensionCommandManager(),
                new ExtensionSettingsManager());
    }

    public ExtensionContextImpl(ExtensionScheduler scheduler, ExtensionEvents events) {
        this(scheduler, events, new ExtensionCommandManager(), new ExtensionSettingsManager());
    }

    public ExtensionContextImpl(ExtensionScheduler scheduler, ExtensionEvents events,
            CommandManager commands) {
        this(scheduler, events, commands, new ExtensionSettingsManager());
    }

    public ExtensionContextImpl(ExtensionScheduler scheduler, ExtensionEvents events,
            CommandManager commands, Settings settings) {
        this(scheduler, events, commands, settings, new ExtensionStorageManager());
    }

    public ExtensionContextImpl(ExtensionScheduler scheduler, ExtensionEvents events,
            CommandManager commands, Settings settings, ExtensionStorage storage) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.events = Objects.requireNonNull(events, "events");
        this.commands = Objects.requireNonNull(commands, "commands");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.storage = Objects.requireNonNull(storage, "storage");
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

    @Override
    public Settings getSettings() {
        return settings;
    }

    @Override
    public ExtensionStorage getStorage() {
        return storage;
    }
}
