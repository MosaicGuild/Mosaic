package org.mosaicmc.internal;

import java.util.Objects;

import org.mosaicmc.api.ExtensionContext;
import org.mosaicmc.api.ExtensionEvents;
import org.mosaicmc.api.ExtensionScheduler;

public final class ExtensionContextImpl implements ExtensionContext {

    private final ExtensionScheduler scheduler;
    private final ExtensionEvents events;

    public ExtensionContextImpl(ExtensionScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler);
        this.events = new ExtensionEventsImpl();
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
