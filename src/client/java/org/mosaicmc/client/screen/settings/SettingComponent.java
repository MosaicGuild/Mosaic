package org.mosaicmc.client.screen.settings;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * One interactive row inside a settings category.
 *
 * <p>Bounds are always supplied by the owning screen for both rendering and
 * input, so rendering and hitboxes share the same layout and cannot drift.
 * Components hold their own values for the lifetime of the screen.
 *
 * <p>Internal demo only: not part of Mosaic's public Settings API.
 */
public interface SettingComponent {
    /** Preferred row height in pixels, excluding inter-row gaps. */
    int preferredHeight();

    void render(GuiGraphicsExtractor graphics, Font font,
                int x, int y, int width, int height,
                int mouseX, int mouseY);

    /**
     * @param button vanilla button index ({@code MOUSE_BUTTON_LEFT}/{@code MOUSE_BUTTON_RIGHT})
     * @return true if the click was consumed
     */
    boolean mouseClicked(double mouseX, double mouseY, int button,
                         int x, int y, int width, int height);

    default boolean mouseDragged(double mouseX, double mouseY, int button,
                                 int x, int y, int width, int height) {
        return false;
    }

    default void mouseReleased(double mouseX, double mouseY, int button,
                               int x, int y, int width, int height) {
    }

    static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    static boolean isLeftClick(int button) {
        return button == InputConstants.MOUSE_BUTTON_LEFT;
    }

    static boolean isRightClick(int button) {
        return button == InputConstants.MOUSE_BUTTON_RIGHT;
    }

    /**
     * Draw text centered on both axes inside the given box. The box position
     * and size are the pill (or other control) bounds, so the text cannot
     * drift away from its background.
     */
    static void drawCenteredText(GuiGraphicsExtractor graphics, Font font, String text,
                                 int boxX, int boxY, int boxW, int boxH, int color) {
        int textW = font.width(text);
        int textX = boxX + (boxW - textW) / 2;
        int textY = boxY + (boxH - font.lineHeight) / 2;
        graphics.text(font, text, textX, textY, color);
    }
}
