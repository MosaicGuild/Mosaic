package org.mosaicmc.internal;

import java.util.Objects;

import net.fabricmc.fabric.api.event.Event;
import org.mosaicmc.api.ExtensionEvents;

final class ExtensionEventsImpl implements ExtensionEvents {

    @Override
    public <T> void register(Event<T> event, T listener) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(listener, "listener");

        event.register(listener);
    }
}
