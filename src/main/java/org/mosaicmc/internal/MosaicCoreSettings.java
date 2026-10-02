package org.mosaicmc.internal;

import java.util.List;

import org.mosaicmc.api.settings.BooleanSetting;
import org.mosaicmc.api.settings.EnumSetting;
import org.mosaicmc.api.settings.IntSetting;
import org.mosaicmc.api.settings.Setting;

/**
 * Mosaic's own settings, declared with the same public API extensions use.
 *
 * <p>Owner is {@code "mosaic"}. Values
 * are session-scoped like every other setting in this version;  and changing a value does not by itself rewire
 * rendering or performance behavior — that wiring is future work.
 */
public final class MosaicCoreSettings {

    /** Color-scheme choices for Mosaic interface elements. */
    public enum Theme {
        DARK,
        MIDNIGHT,
        SYSTEM
    }

    private static final ExtensionSettingsManager SETTINGS = new ExtensionSettingsManager("mosaic");

    private static final BooleanSetting NOTIFICATIONS = SETTINGS.registerBoolean(
            "notifications", "Enable notifications",
            "Shows toast messages for Mosaic events.", true);
    private static final IntSetting UI_SCALE = SETTINGS.registerInt(
            "ui_scale", "UI scale",
            "Scales Mosaic interface elements (percent).", 100, 50, 150);
    private static final EnumSetting<Theme> THEME = SETTINGS.registerEnum(
            "theme", "Theme",
            "Color scheme for Mosaic interface elements.", Theme.DARK);

    private MosaicCoreSettings() {
    }

    public static BooleanSetting notifications() {
        return NOTIFICATIONS;
    }

    public static IntSetting uiScale() {
        return UI_SCALE;
    }

    public static EnumSetting<Theme> theme() {
        return THEME;
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
