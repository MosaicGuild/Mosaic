package org.mosaicmc.client.screen.settings;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.mosaicmc.client.render.RoundedRectRenderState;

/**
 * Enum selector row. Left-click cycles forward, right-click cycles backward.
 *
 * <p>Internal demo only.
 */
public final class EnumSettingComponent implements SettingComponent {
    private static final int ROW_HEIGHT = 26;
    private static final int BUTTON_W = 110;
    private static final int BUTTON_H = 20;

    private final SettingEntry<String> entry;
    private final List<String> options;

    public EnumSettingComponent(SettingEntry<String> entry, List<String> options) {
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("options must not be empty");
        }
        this.entry = entry;
        this.options = new ArrayList<>(options);
        if (!this.options.contains(entry.get())) {
            entry.set(this.options.getFirst());
        }
    }

    public SettingEntry<String> entry() {
        return entry;
    }

    public List<String> options() {
        return List.copyOf(options);
    }

    @Override
    public int preferredHeight() {
        return ROW_HEIGHT;
    }

    private void cycle(int direction) {
        int current = options.indexOf(entry.get());
        if (current < 0) {
            current = 0;
        }
        int next = (current + direction + options.size()) % options.size();
        entry.set(options.get(next));
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, Font font,
                       int x, int y, int width, int height,
                       int mouseX, int mouseY) {
        boolean hovered = SettingComponent.contains(mouseX, mouseY, x, y, width, height);
        if (hovered) {
            RoundedRectRenderState.fill(graphics, x, y, x + width, y + height, 4, SettingsTheme.ROW_HOVER);
        }

        int buttonW = Math.clamp(width / 2, 60, BUTTON_W);
        int buttonX = x + width - buttonW - 4;
        int buttonY = y + (height - BUTTON_H) / 2;

        int labelMax = Math.max(0, buttonX - (x + 4) - 6);
        String label = entry.label();
        if (labelMax > 10 && font.width(label) > labelMax) {
            label = font.plainSubstrByWidth(label, labelMax - 3) + "...";
        }
        graphics.text(font, label, x + 4, y + (height - font.lineHeight) / 2, SettingsTheme.TEXT_PRIMARY);

        boolean buttonHovered = SettingComponent.contains(mouseX, mouseY, buttonX, buttonY, buttonW, BUTTON_H) || hovered;
        int bg = buttonHovered ? SettingsTheme.CONTROL_BG_HOVER : SettingsTheme.CONTROL_BG;
        RoundedRectRenderState.fill(graphics, buttonX, buttonY, buttonX + buttonW, buttonY + BUTTON_H, 4, bg);

        String text = entry.get();
        int maxText = buttonW - 12;
        if (font.width(text) > maxText) {
            text = font.plainSubstrByWidth(text, Math.max(0, maxText - 3)) + "...";
        }
        SettingComponent.drawCenteredText(graphics, font, text, buttonX, buttonY, buttonW, BUTTON_H,
                SettingsTheme.TEXT_PRIMARY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int x, int y, int width, int height) {
        if (!SettingComponent.contains(mouseX, mouseY, x, y, width, height)) {
            return false;
        }
        if (SettingComponent.isLeftClick(button)) {
            cycle(1);
            return true;
        }
        if (SettingComponent.isRightClick(button)) {
            cycle(-1);
            return true;
        }
        return false;
    }
}
