package org.mosaicmc.internal;

import java.util.List;

import org.mosaicmc.api.settings.BooleanSetting;
import org.mosaicmc.api.settings.EnumSetting;
import org.mosaicmc.api.settings.IntSetting;
import org.mosaicmc.api.settings.Setting;

/**
 * Mosaic's own settings, declared with the same public API extensions use.
 *
 * <p>Owner is {@code "mosaic"}. Values persist like every other setting;
 * changing a value does not by itself rewire rendering or performance
 * behavior — that wiring is future work.
 */
public final class MosaicCoreSettings {

    private static final ExtensionSettingsManager SETTINGS = new ExtensionSettingsManager("mosaic");

    private MosaicCoreSettings() {
    }

    /**
     * Every core setting, in registration order.
     *
     * @return an unmodifiable snapshot, never {@code null}
     */
    public static List<Setting<?>> all() {
        return SETTINGS.all();
    }
}
