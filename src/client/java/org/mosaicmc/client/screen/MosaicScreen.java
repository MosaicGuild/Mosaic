package org.mosaicmc.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;
import org.mosaicmc.Mosaic;
import org.mosaicmc.client.render.RoundedRectRenderState;

import java.awt.Color;

public class MosaicScreen extends Screen {

    private final Identifier ICON = Mosaic.id("icon");

    private int centerHeight;
    private int centerWidth;

    public MosaicScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        centerHeight = height / 2;
        centerWidth = width / 2;
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int gap = 5;
        int cornerRadius = 4;

        int baseColor = new Color(10, 10, 10).getRGB();

        int baseLeft = centerWidth - 200;
        int baseRight = centerWidth + 200;
        int baseTop = centerHeight - 100;
        int baseBottom = centerHeight + 100;

        int sideBarRight = baseLeft - gap;
        int sideBarLeft = sideBarRight - 25;

        int topBarBottom = baseTop - gap;
        int topBarTop = topBarBottom - 25;

        base(graphics, baseLeft, baseTop, baseRight, baseBottom, cornerRadius, baseColor);
        sideBar(graphics, sideBarLeft, baseTop, sideBarRight, baseBottom, cornerRadius, baseColor);
        topBar(graphics, baseLeft, topBarTop, baseRight, topBarBottom, cornerRadius, baseColor);
        intersectionOfBars(graphics, sideBarLeft, topBarTop, sideBarRight, topBarBottom, cornerRadius, baseColor);
    }

    private void base(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int radius, int color) {
        RoundedRectRenderState.fill(graphics, left, top, right, bottom, radius, color);
    }

    private void sideBar(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int radius, int color) {
        RoundedRectRenderState.fill(graphics, left, top, right, bottom, radius, color);
    }

    private void topBar(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int radius, int color) {
        RoundedRectRenderState.fill(graphics, left, top, right, bottom, radius, color);
    }

    private void intersectionOfBars(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int radius, int color) {
        int iconSize = 16;
        int iconX = left + (right - left - iconSize) / 2;
        int iconY = top + (bottom - top - iconSize) / 2;

        RoundedRectRenderState.fill(graphics, left, top, right, bottom, radius, color);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ICON, iconX, iconY, iconSize, iconSize);
    }
}