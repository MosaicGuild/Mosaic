package org.mosaicmc.client.screen.settings;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.mosaicmc.client.render.RoundedRectRenderState;

/**
 * Integer slider row with click-and-drag support.
 *
 * <p>Internal demo only. Values are clamped to [min, max].
 */
public final class SliderSettingComponent implements SettingComponent {
    private static final int ROW_HEIGHT = 40;

    private final SettingEntry<Integer> entry;
    private final int min;
    private final int max;

    public SliderSettingComponent(SettingEntry<Integer> entry, int min, int max) {
        this.entry = entry;
        this.min = min;
        this.max = max;
        this.entry.set(clamp(this.entry.get()));
    }

    public SettingEntry<Integer> entry() {
        return entry;
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    public boolean isLeftClick(int button) {
        return SettingComponent.isLeftClick(button);
    }

    @Override
    public int preferredHeight() {
        return ROW_HEIGHT + (entry.description() == null ? 0 : 10);
    }

    private int clamp(int value) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, Font font,
                       int x, int y, int width, int height,
                       int mouseX, int mouseY) {
        boolean hovered = SettingComponent.contains(mouseX, mouseY, x, y, width, height);
        if (hovered) {
            RoundedRectRenderState.fill(graphics, x, y, x + width, y + height, 4, SettingsTheme.ROW_HOVER);
        }

        String valueText = String.valueOf(entry.get());
        graphics.text(font, entry.label(), x + 4, y + 4, SettingsTheme.TEXT_PRIMARY);
        int valueWidth = font.width(valueText);
        graphics.text(font, valueText, x + width - 4 - valueWidth, y + 4, SettingsTheme.VIOLET);
        if (entry.description() != null) {
            graphics.text(font, entry.description(), x + 4, y + 4 + font.lineHeight + 1,
                    SettingsTheme.TEXT_DIM);
        }

        int trackLeft = x + 4;
        int trackRight = x + width - 4;
        int trackY = y + height - 14;
        int trackH = 4;
        int trackWidth = trackRight - trackLeft;
        if (trackWidth <= 1) {
            return;
        }
        RoundedRectRenderState.fill(graphics, trackLeft, trackY, trackRight, trackY + trackH, 2, SettingsTheme.TRACK_OFF);

        double fraction = max == min ? 0.0 : (double) (entry.get() - min) / (double) (max - min);
        fraction = Math.clamp(fraction, 0.0, 1.0);
        int filled = (int) Math.round(trackWidth * fraction);
        if (filled > 0) {
            int fillColor = hovered ? SettingsTheme.VIOLET_HOVER : SettingsTheme.VIOLET;
            RoundedRectRenderState.fill(graphics, trackLeft, trackY, trackLeft + filled, trackY + trackH, 2, fillColor);
        }

        int thumbW = 8;
        int thumbH = 12;
        int thumbCenterX = trackLeft + filled;
        int thumbX = Math.max(trackLeft, Math.min(trackRight - thumbW, thumbCenterX - thumbW / 2));
        int thumbY = trackY + (trackH - thumbH) / 2;
        RoundedRectRenderState.fill(graphics, thumbX, thumbY, thumbX + thumbW, thumbY + thumbH, 3, SettingsTheme.TEXT_PRIMARY);
    }

    private boolean onTrack(double mouseX, double mouseY, int x, int y, int width, int height) {
        int trackLeft = x + 4;
        int trackRight = x + width - 4;
        int trackY = y + height - 14;
        return mouseX >= trackLeft - 2 && mouseX < trackRight + 2
                && mouseY >= trackY - 8 && mouseY < trackY + 12;
    }

    private void setFromMouse(double mouseX, int x, int width) {
        int trackLeft = x + 4;
        int trackRight = x + width - 4;
        int trackWidth = trackRight - trackLeft;
        if (trackWidth <= 1) {
            return;
        }
        double fraction = (mouseX - trackLeft) / (double) trackWidth;
        fraction = Math.clamp(fraction, 0.0, 1.0);
        int value = (int) Math.round(min + fraction * (max - min));
        entry.set(clamp(value));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int x, int y, int width, int height) {
        if (!SettingComponent.isLeftClick(button)) {
            return false;
        }
        if (!onTrack(mouseX, mouseY, x, y, width, height)) {
            return false;
        }
        setFromMouse(mouseX, x, width);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                int x, int y, int width, int height) {
        if (!SettingComponent.isLeftClick(button)) {
            return false;
        }
        setFromMouse(mouseX, x, width);
        return true;
    }
}
