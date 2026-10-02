package org.mosaicmc.api.settings;

import java.util.List;

/**
 * A setting with a fixed set of choices backed by a Java enum, shown as a
 * cycling selector in the settings screen.
 *
 * <p>Do not implement this interface; obtain instances from
 * {@link Settings#registerEnum}.
 *
 * @param <E> the enum type holding the choices
 */
public interface EnumSetting<E extends Enum<E>> extends Setting<E> {

    /**
     * The enum type holding the choices.
     *
     * @return the enum class, never {@code null}
     */
    Class<E> type();

    /**
     * Every accepted value, in declaration order.
     *
     * @return an unmodifiable snapshot of the enum constants, never empty
     */
    List<E> options();
}
