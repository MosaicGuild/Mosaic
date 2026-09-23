package org.mosaicmc.internal;

/**
 * Where a dispatched command's reply goes. The adapter feeds Brigadier chat
 * output through this; tests collect into a list.
 */
@FunctionalInterface
public interface MessageSink {
    void send(String message);
}
