package org.mosaicmc.client.screen.settings;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.mosaicmc.client.render.RoundedRectRenderState;

/**
 * Boolean toggle row. Clicking anywhere on the row toggles the value.
 *
 * <p>Internal demo only.
 */
public final class BooleanSettingComponent implements SettingComponent {
    private static final int ROW_HEIGHT = 26;
    private static final int TRACK_W = 32;
    private static final int TRACK_H = 16;

    private final SettingEntry<Boolean> entry;

    public BooleanSettingComponent(SettingEntry<Boolean> entry) {
        this.entry = entry;
    }

    public SettingEntry<Boolean> entry() {
        return entry;
    }

    @Override
    public int preferredHeight() {
        return entry.description() == null ? ROW_HEIGHT : ROW_HEIGHT + 10;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, Font font,
                       int x, int y, int width, int height,
                       int mouseX, int mouseY) {
        boolean hovered = SettingComponent.contains(mouseX, mouseY, x, y, width, height);
        if (hovered) {
            RoundedRectRenderState.fill(graphics, x, y, x + width, y + height, 4, SettingsTheme.ROW_HOVER);
        }

        boolean on = Boolean.TRUE.equals(entry.get());
        int labelY = y + (height - font.lineHeight) / 2;
        if (entry.description() != null) {
            labelY = y + 3;
        }
        graphics.text(font, entry.label(), x + 4, labelY, SettingsTheme.TEXT_PRIMARY);
        if (entry.description() != null) {
            graphics.text(font, entry.description(), x + 4, y + 3 + font.lineHeight + 1, SettingsTheme.TEXT_DIM);
        }

        int trackX = x + width - TRACK_W - 4;
        int trackY = y + (height - TRACK_H) / 2;
        int trackColor = on ? SettingsTheme.VIOLET : SettingsTheme.TRACK_OFF;
        if (hovered) {
            trackColor = on ? SettingsTheme.VIOLET_HOVER : SettingsTheme.CONTROL_BG_HOVER;
        }
        RoundedRectRenderState.fill(graphics, trackX, trackY, trackX + TRACK_W, trackY + TRACK_H, 8, trackColor);

        int knobSize = 12;
        int knobX = on ? trackX + TRACK_W - knobSize - 2 : trackX + 2;
        int knobY = trackY + (TRACK_H - knobSize) / 2;
        RoundedRectRenderState.fill(graphics, knobX, knobY, knobX + knobSize, knobY + knobSize, 6, SettingsTheme.TEXT_PRIMARY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int x, int y, int width, int height) {
        if (!SettingComponent.isLeftClick(button)) {
            return false;
        }
        if (!SettingComponent.contains(mouseX, mouseY, x, y, width, height)) {
            return false;
        }
        entry.set(!Boolean.TRUE.equals(entry.get()));
        return true;
    }
}
