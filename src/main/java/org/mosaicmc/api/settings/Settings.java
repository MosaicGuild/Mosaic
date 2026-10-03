package org.mosaicmc.api.settings;

import java.util.List;
import java.util.Optional;

/**
 * Declares and reads one extension's settings.
 *
 * <p>Obtain the instance from the extension context; never pass extension
 * ids by hand:
 *
 * <pre>{@code
 * public enum RadarMode { COMPACT, DETAILED, SILENT }
 *
 * private BooleanSetting announcements;
 * private IntSetting radius;
 * private EnumSetting<RadarMode> radarMode;
 *
 * @Override
 * public void onLoad() {
 *     Settings settings = getContext().getSettings();
 *     announcements = settings.registerBoolean(
 *             "announcements", "Enable announcements",
 *             "Sends a chat message when a waypoint is reached.", true);
 *     radius = settings.registerInt(
 *             "radar_radius", "Radar radius",
 *             "How far (in blocks) the radar scans.", 100, 10, 500);
 *     radarMode = settings.registerEnum(
 *             "radar_mode", "Radar mode", null, RadarMode.DETAILED);
 * }
 *
 * @Override
 * public void onEnable() {
 *     if (announcements.get()) {
 *         LOGGER.info("Radar: radius={} mode={}", radius.get(), radarMode.get());
 *     }
 * }
 * }</pre>
 *
 * <p>Setting ids are scoped to the owning extension: two extensions may
 * each use {@code "enabled"} without colliding. Within one extension,
 * registering the same id twice is an error.
 *
 * <p>Settings are declarations, not listeners: disabling an extension does
 * not delete its settings or reset their values. Values are persisted by
 * Mosaic and restored on the next startup.
 *
 * <p>No settings section is created automatically. To show this
 * extension's settings in Mosaic's settings screen, ask for a section:
 *
 * <pre>{@code
 * settings.registerSection("Radar");
 * }</pre>
 */
public interface Settings {

    /**
     * Asks for a section showing this extension's settings in Mosaic's
     * settings screen, under the given title. Without this call the
     * extension's settings stay programmatic-only: no sidebar entry is
     * created for them.
     *
     * @param title the section title shown in the UI, must not be {@code null} or blank
     * @throws NullPointerException if {@code title} is {@code null}
     * @throws IllegalArgumentException if {@code title} is blank, or this
     * extension already registered a section
     */
    void registerSection(String title);


    /**
     * Declares a boolean setting.
     *
     * @param id the extension-scoped id, must not be {@code null} or blank
     * @param displayName the name shown in the UI, must not be {@code null} or blank
     * @param description an optional explanation, may be {@code null}
     * @param defaultValue the initial value
     * @return the registered setting, never {@code null}
     * @throws NullPointerException if {@code id} or {@code displayName} is {@code null}
     * @throws IllegalArgumentException if {@code id} or {@code displayName} is blank,
     * or this extension already registered {@code id}
     */
    BooleanSetting registerBoolean(String id, String displayName, String description, boolean defaultValue);

    /**
     * Declares a bounded integer setting.
     *
     * @param id the extension-scoped id, must not be {@code null} or blank
     * @param displayName the name shown in the UI, must not be {@code null} or blank
     * @param description an optional explanation, may be {@code null}
     * @param defaultValue the initial value, must be within {@code [min, max]}
     * @param min the smallest accepted value
     * @param max the largest accepted value, must be {@code >= min}
     * @return the registered setting, never {@code null}
     * @throws NullPointerException if {@code id} or {@code displayName} is {@code null}
     * @throws IllegalArgumentException if {@code id} or {@code displayName} is blank,
     * this extension already registered {@code id}, {@code min > max},
     * or {@code defaultValue} is outside {@code [min, max]}
     */
    IntSetting registerInt(String id, String displayName, String description,
            int defaultValue, int min, int max);

    /**
     * Declares an enum setting whose choices are the constants of
     * {@code defaultValue}'s enum type, in declaration order.
     *
     * @param <E> the enum type
     * @param id the extension-scoped id, must not be {@code null} or blank
     * @param displayName the name shown in the UI, must not be {@code null} or blank
     * @param description an optional explanation, may be {@code null}
     * @param defaultValue the initial value, must not be {@code null}
     * @return the registered setting, never {@code null}
     * @throws NullPointerException if {@code id}, {@code displayName}, or
     * {@code defaultValue} is {@code null}
     * @throws IllegalArgumentException if {@code id} or {@code displayName} is blank,
     * or this extension already registered {@code id}
     */
    <E extends Enum<E>> EnumSetting<E> registerEnum(String id, String displayName,
            String description, E defaultValue);

    /**
     * Looks up one of this extension's settings by its extension-scoped id.
     *
     * @param id the setting id, must not be {@code null}
     * @return the setting, or {@link Optional#empty()} when unknown
     * @throws NullPointerException if {@code id} is {@code null}
     */
    Optional<Setting<?>> get(String id);

    /**
     * Every setting this extension registered, in registration order.
     *
     * @return an unmodifiable snapshot, never {@code null}
     */
    List<Setting<?>> all();
}
