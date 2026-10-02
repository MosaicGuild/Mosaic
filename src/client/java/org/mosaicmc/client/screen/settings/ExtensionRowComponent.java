package org.mosaicmc.client.screen.settings;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.mosaicmc.client.render.RoundedRectRenderState;
import org.mosaicmc.extension.ExtensionManager;

/**
 * One discovered extension with a live enable/disable toggle.
 *
 * <p>Unlike the former mock entries, this row drives the real lifecycle:
 * clicking calls {@link ExtensionManager#enable} /
 * {@link ExtensionManager#disable}, and the switch renders
 * {@link ExtensionManager#isEnabled}.
 */
public final class ExtensionRowComponent implements SettingComponent {
    private static final int ROW_HEIGHT = 38;
    private static final int TRACK_W = 32;
    private static final int TRACK_H = 16;

    private final String extensionId;
    private final String name;
    private final String version;
    private final String description;

    public ExtensionRowComponent(String extensionId, String name, String version, String description) {
        this.extensionId = extensionId;
        this.name = name;
        this.version = version;
        this.description = description;
    }

    public String extensionId() {
        return extensionId;
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
        boolean enabled = ExtensionManager.isEnabled(extensionId);

        int textLeft = x + 4;
        int toggleSpace = TRACK_W + 10;

        int trackX = x + width - TRACK_W - 4;
        int trackY = y + (height - TRACK_H) / 2;

        // State label sits left of the toggle, vertically centered with it.
        String state = enabled ? "Enabled" : "Disabled";
        int stateColor = enabled ? SettingsTheme.VIOLET : SettingsTheme.TEXT_DIM;
        int stateW = font.width(state);
        int stateH = font.lineHeight;
        int stateX = trackX - 6 - stateW;
        int stateY = trackY + (TRACK_H - stateH) / 2;

        int titleMax = Math.max(0, stateX - textLeft - 6);
        String title = name + "  v" + version;
        if (titleMax > 10 && font.width(title) > titleMax) {
            title = font.plainSubstrByWidth(title, titleMax - 3) + "...";
        }
        graphics.text(font, title, textLeft, y + 4, SettingsTheme.TEXT_PRIMARY);
        if (stateX > textLeft) {
            SettingComponent.drawCenteredText(graphics, font, state,
                    stateX, stateY, stateW, stateH, stateColor);
        }

        if (description != null) {
            int maxDesc = Math.max(0, width - toggleSpace - 8);
            if (maxDesc > 20) {
                String desc = description;
                if (font.width(desc) > maxDesc) {
                    desc = font.plainSubstrByWidth(desc, maxDesc - 3) + "...";
                }
                graphics.text(font, desc, textLeft, y + 4 + font.lineHeight + 2, SettingsTheme.TEXT_DIM);
            }
        }

        int trackColor = enabled ? SettingsTheme.VIOLET : SettingsTheme.TRACK_OFF;
        if (hovered) {
            trackColor = enabled ? SettingsTheme.VIOLET_HOVER : SettingsTheme.CONTROL_BG_HOVER;
        }
        RoundedRectRenderState.fill(graphics, trackX, trackY, trackX + TRACK_W, trackY + TRACK_H, 8, trackColor);

        int knobSize = 12;
        int knobX = enabled ? trackX + TRACK_W - knobSize - 2 : trackX + 2;
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
        if (ExtensionManager.isEnabled(extensionId)) {
            ExtensionManager.disable(extensionId);
        } else {
            ExtensionManager.enable(extensionId);
        }
        return true;
    }
}
