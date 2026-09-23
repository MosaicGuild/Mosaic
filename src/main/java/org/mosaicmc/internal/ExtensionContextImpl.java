package org.mosaicmc.internal;

import java.util.Objects;

import org.mosaicmc.api.ExtensionContext;
import org.mosaicmc.api.ExtensionEvents;
import org.mosaicmc.api.ExtensionScheduler;

public final class ExtensionContextImpl implements ExtensionContext {

    private final ExtensionScheduler scheduler;
    private final ExtensionEvents events;

    public ExtensionContextImpl(ExtensionScheduler scheduler) {
        this(scheduler, new ExtensionEventsImpl());
    }

    public ExtensionContextImpl(ExtensionScheduler scheduler, ExtensionEvents events) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.events = Objects.requireNonNull(events, "events");
    }

    @Override
    public ExtensionScheduler getScheduler() {
        return scheduler;
    }

    @Override
    public ExtensionEvents getEvents() {
        return events;
    }
}
