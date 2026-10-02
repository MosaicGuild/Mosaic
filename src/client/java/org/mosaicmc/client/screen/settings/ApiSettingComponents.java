package org.mosaicmc.client.screen.settings;

import java.util.List;
import org.mosaicmc.api.settings.BooleanSetting;
import org.mosaicmc.api.settings.EnumSetting;
import org.mosaicmc.api.settings.IntSetting;
import org.mosaicmc.api.settings.Setting;

/**
 * Bridges public API settings to the existing UI components.
 *
 * <p>Each component wraps a live view of the underlying setting: rendering
 * reads {@code get()} every frame and interaction writes through
 * {@code set()}, so the extension always observes the value the user
 * picked. Components never copy values and never depend on the
 * registration implementation, only on the public interfaces.
 */
public final class ApiSettingComponents {
    private ApiSettingComponents() {
    }

    /**
     * Adapts any supported public setting to a UI row.
     *
     * @param setting the registered setting, must not be {@code null}
     * @return a component rendering the correct control, never {@code null}
     * @throws NullPointerException if {@code setting} is {@code null}
     * @throws IllegalArgumentException if the setting type has no control
     */
    public static SettingComponent fromSetting(Setting<?> setting) {
        if (setting instanceof BooleanSetting booleanSetting) {
            return fromBoolean(booleanSetting);
        }
        if (setting instanceof IntSetting intSetting) {
            return fromInt(intSetting);
        }
        if (setting instanceof EnumSetting<?> enumSetting) {
            return fromEnum(enumSetting);
        }
        throw new IllegalArgumentException(
                "Unsupported setting type: " + setting.getClass().getName());
    }

    /**
     * Adapts a public boolean setting to a toggle row.
     */
    public static BooleanSettingComponent fromBoolean(BooleanSetting setting) {
        SettingEntry<Boolean> view = new SettingEntry<>(
                setting.displayName(), setting.description(), setting.defaultValue()) {
            @Override
            public Boolean get() {
                return setting.get();
            }

            @Override
            public void set(Boolean value) {
                setting.set(value);
            }

            @Override
            public void reset() {
                setting.set(setting.defaultValue());
            }
        };
        return new BooleanSettingComponent(view);
    }

    /**
     * Adapts a public bounded integer setting to a slider row.
     */
    public static SliderSettingComponent fromInt(IntSetting setting) {
        SettingEntry<Integer> view = new SettingEntry<>(
                setting.displayName(), setting.description(), setting.defaultValue()) {
            @Override
            public Integer get() {
                return setting.get();
            }

            @Override
            public void set(Integer value) {
                setting.set(value);
            }

            @Override
            public void reset() {
                setting.set(setting.defaultValue());
            }
        };
        return new SliderSettingComponent(view, setting.min(), setting.max());
    }

    /**
     * Adapts a public enum setting to a cycling selector row. Options are
     * the enum constant names, in declaration order.
     */
    public static EnumSettingComponent fromEnum(EnumSetting<?> setting) {
        return fromEnumTyped(setting);
    }

    private static <E extends Enum<E>> EnumSettingComponent fromEnumTyped(EnumSetting<E> setting) {
        List<String> options = setting.options().stream().map(Enum::name).toList();
        SettingEntry<String> view = new SettingEntry<>(
                setting.displayName(), setting.description(), setting.defaultValue().name()) {
            @Override
            public String get() {
                return setting.get().name();
            }

            @Override
            public void set(String name) {
                setting.set(Enum.valueOf(setting.type(), name));
            }

            @Override
            public void reset() {
                setting.set(setting.defaultValue());
            }
        };
        return new EnumSettingComponent(view, options);
    }
}
