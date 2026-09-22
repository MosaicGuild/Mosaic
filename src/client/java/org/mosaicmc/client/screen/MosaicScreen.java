package org.mosaicmc.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.awt.*;

public class MosaicScreen extends Screen {

    int panelWidth = Math.min(800, width - 80);
    int panelHeight = Math.min(500, height - 80);

    int left = (width - panelWidth) / 2;
    int top = (height - panelHeight) / 2;
    int right = left + panelWidth;
    int bottom = top + panelHeight;

    public MosaicScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor ctx,
            int mouseX,
            int mouseY,
            float delta
    ) {
        ctx.fill(
                left,
                top,
                right,
                bottom,
                Color.GRAY.getRGB()
        );
    }
}