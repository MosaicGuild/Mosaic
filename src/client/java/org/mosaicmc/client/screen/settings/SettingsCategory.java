package org.mosaicmc.client.screen.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One sidebar category and the demo controls it owns.
 *
 * <p>Categories and their components live for the lifetime of the screen,
 * so navigating never resets edited values.
 *
 * <p>Internal demo only: not part of Mosaic's public Settings API.
 */
public final class SettingsCategory {
    private final String id;
    private final String title;
    private final List<SettingComponent> components;

    public SettingsCategory(String id, String title, List<SettingComponent> components) {
        this.id = id;
        this.title = title;
        this.components = new ArrayList<>(components);
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public List<SettingComponent> components() {
        return Collections.unmodifiableList(components);
    }

    int contentHeight() {
        int total = 0;
        for (int i = 0; i < components.size(); i++) {
            total += components.get(i).preferredHeight();
            if (i < components.size() - 1) {
                total += SettingsTheme.ROW_GAP;
            }
        }
        return total;
    }
}
