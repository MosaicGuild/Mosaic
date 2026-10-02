package org.mosaicmc.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mosaicmc.api.ExtensionMetadata;
import org.mosaicmc.api.settings.BooleanSetting;
import org.mosaicmc.api.settings.EnumSetting;
import org.mosaicmc.api.settings.IntSetting;
import org.mosaicmc.api.settings.Settings;

/**
 * Unit tests for the public Settings API: defaults, reads/writes, bounds,
 * enums, duplicates, ownership, invalid registrations, and isolation.
 */
class SettingsApiTest {

    enum Mode {
        COMPACT,
        DETAILED,
        SILENT
    }

    @BeforeEach
    void clearRegistry() {
        ExtensionManager.resetForTesting();
    }

    @Test
    void booleanDefaultAndReadWrite() {
        Settings settings = settingsOf("waypoints");

        BooleanSetting enabled = settings.registerBoolean(
                "enabled", "Enabled", "Master switch.", true);

        assertEquals(true, enabled.get());
        assertEquals(true, enabled.defaultValue());
        enabled.set(false);
        assertEquals(false, enabled.get());
    }

    @Test
    void intDefaultBoundsAndClampOnSet() {
        Settings settings = settingsOf("waypoints");

        IntSetting radius = settings.registerInt(
                "radius", "Radius", null, 100, 10, 500);

        assertEquals(100, radius.get());
        assertEquals(10, radius.min());
        assertEquals(500, radius.max());

        radius.set(1000);
        assertEquals(500, radius.get(), "set() above max must clamp");
        radius.set(-5);
        assertEquals(10, radius.get(), "set() below min must clamp");
        radius.set(250);
        assertEquals(250, radius.get());
    }

    @Test
    void intRejectsMinAboveMax() {
        Settings settings = settingsOf("waypoints");

        assertThrows(IllegalArgumentException.class, () ->
                settings.registerInt("bad", "Bad", null, 5, 10, 1));
    }

    @Test
    void intRejectsDefaultOutsideBounds() {
        Settings settings = settingsOf("waypoints");

        assertThrows(IllegalArgumentException.class, () ->
                settings.registerInt("low", "Low", null, 1, 10, 500));
        assertThrows(IllegalArgumentException.class, () ->
                settings.registerInt("high", "High", null, 999, 10, 500));
    }

    @Test
    void intAcceptsBoundaryDefaults() {
        Settings settings = settingsOf("waypoints");

        assertEquals(10, settings.registerInt("lo", "Lo", null, 10, 10, 500).get());
        assertEquals(500, settings.registerInt("hi", "Hi", null, 500, 10, 500).get());
    }

    @Test
    void enumDefaultOptionsAndReadWrite() {
        Settings settings = settingsOf("waypoints");

        EnumSetting<Mode> mode = settings.registerEnum(
                "mode", "Mode", null, Mode.DETAILED);

        assertEquals(Mode.DETAILED, mode.get());
        assertEquals(Mode.DETAILED, mode.defaultValue());
        assertEquals(Mode.class, mode.type());
        assertEquals(List.of(Mode.COMPACT, Mode.DETAILED, Mode.SILENT), mode.options());

        mode.set(Mode.SILENT);
        assertEquals(Mode.SILENT, mode.get());
    }

    @Test
    void nullValuesRejected() {
        Settings settings = settingsOf("waypoints");

        BooleanSetting flag = settings.registerBoolean("flag", "Flag", null, true);
        assertThrows(NullPointerException.class, () -> flag.set(null));

        IntSetting number = settings.registerInt("number", "Number", null, 1, 0, 10);
        assertThrows(NullPointerException.class, () -> number.set(null));

        EnumSetting<Mode> mode = settings.registerEnum("mode", "Mode", null, Mode.COMPACT);
        assertThrows(NullPointerException.class, () -> mode.set(null));
        assertThrows(NullPointerException.class, () ->
                settings.<Mode>registerEnum("bad", "Bad", null, null));
    }

    @Test
    void duplicateIdWithinExtensionRejected() {
        Settings settings = settingsOf("waypoints");

        settings.registerBoolean("enabled", "Enabled", null, true);

        IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class, () ->
                settings.registerBoolean("enabled", "Enabled again", null, false));
        assertTrue(duplicate.getMessage().contains("enabled"));

        // The original registration is untouched.
        assertEquals(true, settings.get("enabled").orElseThrow().get());
    }

    @Test
    void sameIdInDifferentExtensionsIsIsolated() {
        Settings first = settingsOf("alpha");
        Settings second = settingsOf("beta");

        BooleanSetting a = first.registerBoolean("enabled", "Enabled", null, true);
        BooleanSetting b = second.registerBoolean("enabled", "Enabled", null, false);

        a.set(false);
        assertEquals(false, a.get());
        assertEquals(false, b.get(), "second extension keeps its own default");

        b.set(true);
        assertEquals(false, a.get(), "writes do not leak across extensions");
        assertEquals(true, b.get());
    }

    @Test
    void ownershipAndRetrieval() {
        DummyExtension alpha = new DummyExtension("alpha");
        DummyExtension beta = new DummyExtension("beta");
        ExtensionManager.init(fakeLoader(List.of(
                entrypoint("mod-a", alpha), entrypoint("mod-b", beta))));
        alpha.getContext().getSettings().registerBoolean("enabled", "Enabled", null, true);

        assertEquals(1, ExtensionManager.getSettings("alpha").size());
        assertEquals("enabled", ExtensionManager.getSettings("alpha").get(0).id());
        assertTrue(ExtensionManager.getSettings("beta").isEmpty());
        assertTrue(ExtensionManager.getSettings("unknown-id").isEmpty());
    }

    @Test
    void registrationIdentityIsStable() {
        Settings settings = settingsOf("waypoints");

        BooleanSetting registered = settings.registerBoolean("enabled", "Enabled", null, true);

        assertSame(registered, settings.get("enabled").orElseThrow());
        assertSame(registered, settings.all().get(0));
        assertTrue(settings.get("missing").isEmpty());
        assertThrows(NullPointerException.class, () -> settings.get(null));
    }

    @Test
    void invalidRegistrationsReported() {
        Settings settings = settingsOf("waypoints");

        assertThrows(NullPointerException.class, () ->
                settings.registerBoolean(null, "Name", null, true));
        assertThrows(NullPointerException.class, () ->
                settings.registerBoolean("id", null, null, true));
        assertThrows(IllegalArgumentException.class, () ->
                settings.registerBoolean("  ", "Name", null, true));
        assertThrows(IllegalArgumentException.class, () ->
                settings.registerBoolean("id", "   ", null, true));
        assertThrows(NullPointerException.class, () ->
                settings.registerInt(null, "Name", null, 1, 0, 10));
        assertThrows(IllegalArgumentException.class, () ->
                settings.registerInt("id", "", null, 1, 0, 10));
    }

    @Test
    void nullDescriptionMeansAbsent() {
        Settings settings = settingsOf("waypoints");

        assertNull(settings.registerBoolean("a", "A", null, true).description());
        assertEquals("hi", settings.registerBoolean("b", "B", "hi", true).description());
    }

    @Test
    void settingsSurviveDisable() {
        DummyExtension extension = new DummyExtension("waypoints");
        ExtensionManager.init(fakeLoader(List.of(entrypoint("some-mod", extension))));
        Settings settings = extension.getContext().getSettings();
        BooleanSetting enabled = settings.registerBoolean("enabled", "Enabled", null, true);
        enabled.set(false);

        ExtensionManager.enable("waypoints");
        ExtensionManager.disable("waypoints");

        assertEquals(false, enabled.get(), "disable must not reset values");
        assertEquals(1, ExtensionManager.getSettings("waypoints").size(),
                "disable must not delete settings");
    }

    @Test
    void noSectionByDefault() {
        settingsOf("waypoints");

        assertTrue(ExtensionManager.getSettingsSection("waypoints").isEmpty());
        assertTrue(ExtensionManager.getSettingsSection("unknown-id").isEmpty());
    }

    @Test
    void sectionRegistrationAndRetrieval() {
        Settings settings = settingsOf("waypoints");
        settings.registerSection("Waypoints");

        assertEquals(Optional.of("Waypoints"),
                ExtensionManager.getSettingsSection("waypoints"));
    }

    @Test
    void duplicateSectionRejected() {
        Settings settings = settingsOf("waypoints");
        settings.registerSection("Waypoints");

        assertThrows(IllegalArgumentException.class, () -> settings.registerSection("Again"));
        assertEquals(Optional.of("Waypoints"),
                ExtensionManager.getSettingsSection("waypoints"),
                "failed re-registration must keep the original title");
    }

    @Test
    void invalidSectionRejected() {
        Settings settings = settingsOf("waypoints");

        assertThrows(NullPointerException.class, () -> settings.registerSection(null));
        assertThrows(IllegalArgumentException.class, () -> settings.registerSection("  "));
        assertTrue(ExtensionManager.getSettingsSection("waypoints").isEmpty());
    }

    @Test
    void sectionsAreIsolatedPerExtension() {
        DummyExtension alpha = new DummyExtension("alpha");
        DummyExtension beta = new DummyExtension("beta");
        ExtensionManager.init(fakeLoader(List.of(
                entrypoint("mod-a", alpha), entrypoint("mod-b", beta))));
        alpha.getContext().getSettings().registerSection("Alpha");

        assertEquals(Optional.of("Alpha"), ExtensionManager.getSettingsSection("alpha"));
        assertTrue(ExtensionManager.getSettingsSection("beta").isEmpty());
    }

    // Helpers: one shared discovery per test class usage; each test resets first.

    private static Settings settingsOf(String id) {
        return contextOf(id).getSettings();
    }

    private static DummyHolder contextOf(String id) {
        DummyExtension extension = new DummyExtension(id);
        ExtensionManager.init(fakeLoader(List.of(entrypoint("mod-" + id, extension))));
        return new DummyHolder(extension);
    }

    private record DummyHolder(DummyExtension extension) {
        Settings getSettings() {
            return extension.getContext().getSettings();
        }
    }

    private static FabricLoader fakeLoader(List<EntrypointContainer<Extension>> containers) {
        return fake(FabricLoader.class, Map.of("getEntrypointContainers", containers));
    }

    private static EntrypointContainer<Extension> entrypoint(String modId, Extension extension) {
        return fake(EntrypointContainer.class, Map.of(
                "getProvider", fake(ModContainer.class, Map.of(
                        "getMetadata", fake(ModMetadata.class, Map.of("getId", modId)))),
                "getEntrypoint", extension));
    }

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, Map<String, Object> stubs) {
        Map<String, Object> values = new HashMap<>(stubs);
        return (T) Proxy.newProxyInstance(
                SettingsApiTest.class.getClassLoader(),
                new Class<?>[] { type },
                (proxy, method, args) -> {
                    if (method.getName().equals("toString")) {
                        return "fake(" + type.getSimpleName() + ")";
                    }
                    if (method.getName().equals("hashCode")) {
                        return System.identityHashCode(proxy);
                    }
                    if (method.getName().equals("equals")) {
                        return proxy == args[0];
                    }
                    if (values.containsKey(method.getName())) {
                        return values.get(method.getName());
                    }
                    return null;
                });
    }

    private static final class DummyExtension extends Extension {
        private final String id;

        DummyExtension(String id) {
            this.id = id;
        }

        @Override
        public ExtensionMetadata getMetadata() {
            return new ExtensionMetadata() {
                @Override
                public String getId() {
                    return id;
                }

                @Override
                public String getName() {
                    return "Dummy " + id;
                }

                @Override
                public String getDescription() {
                    return "Dummy extension for tests.";
                }

                @Override
                public String getVersion() {
                    return "0.0.0-test";
                }

                @Override
                public String getAuthors() {
                    return "tests";
                }

                @Override
                public String getWebsite() {
                    return "";
                }
            };
        }

        @Override
        public void onEnable() {
        }

        @Override
        public void onDisable() {
        }

        @Override
        public void onLoad() {
        }
    }
}
