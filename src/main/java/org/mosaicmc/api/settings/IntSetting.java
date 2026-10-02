package org.mosaicmc.api.settings;

/**
 * A bounded integer setting, shown as a slider in the settings screen.
 *
 * <p>Values are always within {@code [min, max]}: an out-of-range default
 * is rejected at registration, while {@link #set} clamps. This holds no
 * matter who supplies the value (extension code, the settings screen, or
 * a future persistence layer).
 *
 * <p>Do not implement this interface; obtain instances from
 * {@link Settings#registerInt}.
 */
public interface IntSetting extends Setting<Integer> {

    /**
     * The smallest accepted value.
     *
     * @return the minimum, always {@code <= max()}
     */
    int min();

    /**
     * The largest accepted value.
     *
     * @return the maximum, always {@code >= min()}
     */
    int max();
}
