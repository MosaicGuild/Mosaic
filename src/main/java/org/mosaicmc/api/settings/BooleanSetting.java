package org.mosaicmc.api.settings;

/**
 * A boolean setting, shown as a toggle in the settings screen.
 *
 * <p>Do not implement this interface; obtain instances from
 * {@link Settings#registerBoolean}.
 */
public interface BooleanSetting extends Setting<Boolean> {
}
