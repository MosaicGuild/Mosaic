package org.mosaicmc.client.screen.settings;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.mosaicmc.client.render.RoundedRectRenderState;

/**
 * Demonstration action button row. Runs a callback; never pretends to persist.
 *
 * <p>Internal demo only.
 */
public final class ButtonSettingComponent implements SettingComponent {
    private static final int ROW_HEIGHT = 28;
    private static final int BUTTON_H = 20;

    private final String label;
    private final String buttonText;
    private final Runnable action;
    private int clicks;

    public ButtonSettingComponent(String label, String buttonText, Runnable action) {
        this.label = label;
        this.buttonText = buttonText;
        this.action = action;
    }

    public int clicks() {
        return clicks;
    }

    @Override
    public int preferredHeight() {
        return ROW_HEIGHT;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, Font font,
                       int x, int y, int width, int height,
                       int mouseX, int mouseY) {
        boolean hovered = SettingComponent.contains(mouseX, mouseY, x, y, width, height);
        if (hovered) {
            RoundedRectRenderState.fill(graphics, x, y, x + width, y + height, 4, SettingsTheme.ROW_HOVER);
        }

        String text = clicks > 0 ? buttonText + " (" + clicks + ")" : buttonText;
        int buttonW = Math.max(70, Math.min(150, font.width(text) + 20));
        buttonW = Math.min(buttonW, Math.max(60, width / 2));
        int buttonX = x + width - buttonW - 4;
        int buttonY = y + (height - BUTTON_H) / 2;

        int labelMax = Math.max(0, buttonX - (x + 4) - 6);
        String rowLabel = label;
        if (labelMax > 10 && font.width(rowLabel) > labelMax) {
            rowLabel = font.plainSubstrByWidth(rowLabel, labelMax - 3) + "...";
        }
        graphics.text(font, rowLabel, x + 4, y + (height - font.lineHeight) / 2, SettingsTheme.TEXT_PRIMARY);

        boolean buttonHovered = SettingComponent.contains(mouseX, mouseY, buttonX, buttonY, buttonW, BUTTON_H);
        int bg = buttonHovered ? SettingsTheme.VIOLET_HOVER : SettingsTheme.VIOLET;
        RoundedRectRenderState.fill(graphics, buttonX, buttonY, buttonX + buttonW, buttonY + BUTTON_H, 4, bg);

        // Always centered in the pill; truncate the text rather than shifting it.
        String drawn = text;
        int maxText = buttonW - 12;
        if (maxText > 0 && font.width(drawn) > maxText) {
            drawn = font.plainSubstrByWidth(drawn, Math.max(0, maxText - 3)) + "...";
        }
        SettingComponent.drawCenteredText(graphics, font, drawn, buttonX, buttonY, buttonW, BUTTON_H,
                SettingsTheme.TEXT_PRIMARY);
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
        clicks++;
        if (action != null) {
            action.run();
        }
        return true;
    }
}
