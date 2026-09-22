package org.mosaicmc.core;

import java.util.Objects;

final class ExtensionContextImpl implements ExtensionContext {

    private final ExtensionScheduler scheduler;

    ExtensionContextImpl(ExtensionScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler);
    }

    @Override
    public ExtensionScheduler getScheduler() {
        return scheduler;
    }
}
