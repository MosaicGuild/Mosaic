package org.mosaicmc.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.mosaicmc.api.EventRegistration;

/**
 * Owner-keyed storage for one managed event's listeners. Snapshot iteration
 * makes self-unregistration from inside a callback safe.
 */
final class ManagedEvent<T> {
    private record Entry<T>(Object owner, T listener) {
    }

    private final CopyOnWriteArrayList<Entry<T>> entries = new CopyOnWriteArrayList<>();

    EventRegistration add(Object owner, T listener) {
        Entry<T> entry = new Entry<>(owner, listener);
        entries.add(entry);
        return () -> entries.remove(entry);
    }

    void clear(Object owner) {
        entries.removeIf(entry -> entry.owner() == owner);
    }

    void clearAll() {
        entries.clear();
    }

    List<T> snapshot() {
        List<T> out = new ArrayList<>(entries.size());
        for (Entry<T> entry : entries) {
            out.add(entry.listener());
        }
        return out;
    }
}
