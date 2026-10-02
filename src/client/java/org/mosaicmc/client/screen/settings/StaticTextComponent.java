package org.mosaicmc.client.screen.settings;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A non-interactive text row for empty states (for example, an extension
 * that registered no settings). Never consumes input.
 */
public final class StaticTextComponent implements SettingComponent {
    private final List<String> lines;

    public StaticTextComponent(String... lines) {
        this(List.of(lines));
    }

    public StaticTextComponent(List<String> lines) {
        this.lines = List.copyOf(lines);
    }

    @Override
    public int preferredHeight() {
        return 10 + Math.max(1, lines.size()) * 10;
    }

    @Override
    public void render(GuiGraphicsExtractor graphics, Font font,
                       int x, int y, int width, int height,
                       int mouseX, int mouseY) {
        if (lines.isEmpty()) {
            return;
        }
        int lineY = y + 4;
        for (String line : lines) {
            String drawn = line;
            int maxText = width - 8;
            if (maxText > 10 && font.width(drawn) > maxText) {
                drawn = font.plainSubstrByWidth(drawn, maxText - 3) + "...";
            }
            graphics.text(font, drawn, x + 4, lineY, SettingsTheme.TEXT_DIM);
            lineY += font.lineHeight + 1;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button,
                                int x, int y, int width, int height) {
        return false;
    }
}
