package org.mosaicmc.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.mosaicmc.api.settings.Setting;
import org.mosaicmc.client.render.RoundedRectRenderState;
import org.mosaicmc.client.screen.settings.ApiSettingComponents;
import org.mosaicmc.client.screen.settings.ExtensionRowComponent;
import org.mosaicmc.client.screen.settings.MosaicLayout;
import org.mosaicmc.client.screen.settings.SettingComponent;
import org.mosaicmc.client.screen.settings.SettingsCategory;
import org.mosaicmc.client.screen.settings.SettingsTheme;
import org.mosaicmc.client.screen.settings.SliderSettingComponent;
import org.mosaicmc.client.screen.settings.StaticTextComponent;
import org.mosaicmc.extension.Extension;
import org.mosaicmc.extension.ExtensionManager;
import org.mosaicmc.internal.MosaicCoreSettings;

public class MosaicScreen extends Screen {

    private List<SettingsCategory> categories;
    private int selectedIndex;
    private double mainScroll;
    private double sidebarScroll;
    private SliderSettingComponent draggingSlider;

    public MosaicScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        if (categories == null) {
            categories = buildCategories();
            selectedIndex = 0;
            mainScroll = 0;
            sidebarScroll = 0;
        }
        draggingSlider = null;
        clampScrolls();
    }

    @Override
    protected void repositionElements() {
        draggingSlider = null;
        clampScrolls();
    }

    private void clampScrolls() {
        if (categories == null || categories.isEmpty()) {
            mainScroll = 0;
            sidebarScroll = 0;
            return;
        }
        MosaicLayout layout = MosaicLayout.compute(width, height);
        mainScroll = clampScroll(mainScroll, contentHeight(selectedCategory()), layout.contentHeight());
        sidebarScroll = clampScroll(sidebarScroll, sidebarContentHeight(), sidebarVisibleHeight(layout));
    }

    private static double clampScroll(double scroll, int content, int visible) {
        if (visible <= 0) {
            return 0;
        }
        double max = Math.max(0, content - visible);
        return Math.max(0, Math.min(scroll, max));
    }

    private int clampedSelectedIndex() {
        if (categories == null || categories.isEmpty()) {
            return 0;
        }
        return Math.max(0, Math.min(selectedIndex, categories.size() - 1));
    }

    private SettingsCategory selectedCategory() {
        if (categories == null || categories.isEmpty()) {
            return null;
        }
        return categories.get(clampedSelectedIndex());
    }

    private static List<SettingsCategory> buildCategories() {
        List<SettingsCategory> result = new ArrayList<>();
        result.add(buildGeneralCategory());
        result.add(buildExtensionsCategory());

        for (Extension extension : ExtensionManager.getExtensions()) {
            String extensionId;
            try {
                extensionId = extension.getMetadata().getId();
            } catch (Exception e) {
                continue;
            }
            if (extensionId == null || extensionId.isBlank()) {
                continue;
            }
            // Opt-in only: no sidebar section unless the extension asked for one.
            String title = ExtensionManager.getSettingsSection(extensionId).orElse(null);
            if (title == null) {
                continue;
            }
            List<Setting<?>> registered = ExtensionManager.getSettings(extensionId);
            List<SettingComponent> rows = new ArrayList<>();
            for (Setting<?> setting : registered) {
                rows.add(ApiSettingComponents.fromSetting(setting));
            }
            if (rows.isEmpty()) {
                rows.add(new StaticTextComponent("No settings registered by this extension."));
            }
            result.add(new SettingsCategory("ext:" + extensionId, title, rows));
        }
        return result;
    }

    private static SettingsCategory buildGeneralCategory() {
        List<SettingComponent> rows = new ArrayList<>();
        int extensionCount = ExtensionManager.getExtensions().size();
        int settingCount = MosaicCoreSettings.all().size();
        for (Extension extension : ExtensionManager.getExtensions()) {
            try {
                settingCount += ExtensionManager.getSettings(extension.getMetadata().getId()).size();
            } catch (Exception ignored) {
            }
        }
        rows.add(new StaticTextComponent(
                "Mosaic — modular, client-first modding.",
                extensionCount + (extensionCount == 1 ? " extension" : " extensions")
                        + " · " + settingCount + " settings registered."));
        for (Setting<?> setting : MosaicCoreSettings.all()) {
            rows.add(ApiSettingComponents.fromSetting(setting));
        }
        return new SettingsCategory("general", "General", rows);
    }

    private static SettingsCategory buildExtensionsCategory() {
        List<Extension> extensions = ExtensionManager.getExtensions();
        if (extensions.isEmpty()) {
            return new SettingsCategory("extensions", "Extensions",
                    List.of(new StaticTextComponent("No extensions registered.")));
        }
        List<SettingComponent> rows = new ArrayList<>();
        for (Extension extension : extensions) {
            String extensionId;
            String name;
            String version;
            String description;
            try {
                extensionId = extension.getMetadata().getId();
                name = extension.getMetadata().getName();
                version = extension.getMetadata().getVersion();
                description = extension.getMetadata().getDescription();
            } catch (Exception e) {
                continue;
            }
            if (extensionId == null || extensionId.isBlank()) {
                continue;
            }
            if (name == null || name.isBlank()) {
                name = extensionId;
            }
            if (version == null) {
                version = "";
            }
            rows.add(new ExtensionRowComponent(extensionId, name, version, description));
        }
        if (rows.isEmpty()) {
            rows.add(new StaticTextComponent("No extensions registered."));
        }
        return new SettingsCategory("extensions", "Extensions", rows);
    }

    private record ContentRow(SettingComponent component, int x, int y, int width, int height) {
    }

    private record SidebarRow(int index, int x, int y, int width, int height) {
    }

    private int contentHeight(SettingsCategory category) {
        if (category == null) {
            return 0;
        }
        int total = 0;
        List<SettingComponent> components = category.components();
        for (int i = 0; i < components.size(); i++) {
            total += components.get(i).preferredHeight();
            if (i < components.size() - 1) {
                total += SettingsTheme.ROW_GAP;
            }
        }
        return total;
    }

    private int sidebarContentHeight() {
        if (categories == null) {
            return 0;
        }
        int pad = SettingsTheme.SIDEBAR_PAD;
        int rows = categories.size() * SettingsTheme.SIDEBAR_ROW_HEIGHT
                + Math.max(0, categories.size() - 1) * SettingsTheme.SIDEBAR_ROW_GAP;
        return pad * 2 + rows;
    }

    private int sidebarVisibleHeight(MosaicLayout layout) {
        return Math.max(0, layout.sideBottom() - layout.sideTop());
    }

    private List<ContentRow> contentRows(MosaicLayout layout, double scroll) {
        List<ContentRow> rows = new ArrayList<>();
        SettingsCategory category = selectedCategory();
        if (category == null) {
            return rows;
        }
        int w = layout.contentWidth();
        int y = (int) Math.round(layout.contentTop() - scroll);
        for (SettingComponent component : category.components()) {
            int h = component.preferredHeight();
            rows.add(new ContentRow(component, layout.contentLeft(), y, w, h));
            y += h + SettingsTheme.ROW_GAP;
        }
        return rows;
    }

    private List<SidebarRow> sidebarRows(MosaicLayout layout, double scroll) {
        List<SidebarRow> rows = new ArrayList<>();
        if (categories == null) {
            return rows;
        }
        int pad = SettingsTheme.SIDEBAR_PAD;
        int rowH = SettingsTheme.SIDEBAR_ROW_HEIGHT;
        int gap = SettingsTheme.SIDEBAR_ROW_GAP;
        int w = layout.sideRight() - layout.sideLeft() - pad * 2;
        int y = (int) Math.round(layout.sideTop() + pad - scroll);
        for (int i = 0; i < categories.size(); i++) {
            rows.add(new SidebarRow(i, layout.sideLeft() + pad, y, w, rowH));
            y += rowH + gap;
        }
        return rows;
    }

    private static boolean visibleInContent(ContentRow row, MosaicLayout layout) {
        return row.y() + row.height() > layout.contentTop() && row.y() < layout.contentBottom();
    }

    private static boolean visibleInSidebar(SidebarRow row, MosaicLayout layout) {
        return row.y() + row.height() > layout.sideTop() && row.y() < layout.sideBottom();
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (categories == null) {
            categories = buildCategories();
        }
        MosaicLayout layout = MosaicLayout.compute(width, height);
        double effectiveMain = clampScroll(mainScroll, contentHeight(selectedCategory()), layout.contentHeight());
        double effectiveSide = clampScroll(sidebarScroll, sidebarContentHeight(), sidebarVisibleHeight(layout));

        int cornerRadius = SettingsTheme.CORNER_RADIUS;
        int baseColor = 0xFF0A0A0A;

        base(graphics, layout.mainLeft(), layout.mainTop(), layout.mainRight(), layout.mainBottom(), cornerRadius, baseColor);
        sideBar(graphics, layout.sideLeft(), layout.sideTop(), layout.sideRight(), layout.sideBottom(), cornerRadius, baseColor);
        topBar(graphics, layout.topLeft(), layout.topTop(), layout.topRight(), layout.topBottom(), cornerRadius, baseColor);

        renderSidebar(graphics, layout, effectiveSide, mouseX, mouseY);
        renderTopBarTitle(graphics, layout);
        renderContent(graphics, layout, effectiveMain, mouseX, mouseY);
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

    private void renderSidebar(GuiGraphicsExtractor graphics, MosaicLayout layout, double scroll, int mouseX, int mouseY) {
        if (categories == null) {
            return;
        }
        graphics.enableScissor(layout.sideLeft(), layout.sideTop(), layout.sideRight(), layout.sideBottom());
        try {
            int activeIndex = clampedSelectedIndex();
            for (SidebarRow row : sidebarRows(layout, scroll)) {
                if (!visibleInSidebar(row, layout)) {
                    continue;
                }
                boolean active = row.index() == activeIndex;
                boolean hovered = mouseX >= row.x() && mouseX < row.x() + row.width()
                        && mouseY >= row.y() && mouseY < row.y() + row.height();
                if (active) {
                    RoundedRectRenderState.fill(graphics, row.x(), row.y(),
                            row.x() + row.width(), row.y() + row.height(), 4, SettingsTheme.VIOLET);
                } else if (hovered) {
                    RoundedRectRenderState.fill(graphics, row.x(), row.y(),
                            row.x() + row.width(), row.y() + row.height(), 4, SettingsTheme.ROW_HOVER);
                }
                String title = categories.get(row.index()).title();
                int maxText = row.width() - 12;
                if (maxText > 10 && font.width(title) > maxText) {
                    title = font.plainSubstrByWidth(title, maxText - 3) + "...";
                }
                int color = active ? SettingsTheme.TEXT_PRIMARY
                        : hovered ? SettingsTheme.TEXT_PRIMARY : SettingsTheme.TEXT_SECONDARY;
                int textW = font.width(title);
                int textX = row.x() + 6;
                int textY = row.y() + (row.height() - font.lineHeight) / 2;
                graphics.text(font, title, textX, textY, color);
                @SuppressWarnings("unused")
                int ignored = textW;
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private void renderTopBarTitle(GuiGraphicsExtractor graphics, MosaicLayout layout) {
        SettingsCategory category = selectedCategory();
        String title = category == null ? "Mosaic" : "Mosaic — " + category.title();
        int maxW = layout.topRight() - layout.topLeft() - 16;
        if (maxW > 20 && font.width(title) > maxW) {
            title = font.plainSubstrByWidth(title, maxW - 3) + "...";
        }
        int barH = layout.topBottom() - layout.topTop();
        graphics.text(font, title, layout.topLeft() + 10,
                layout.topTop() + (barH - font.lineHeight) / 2, SettingsTheme.TEXT_PRIMARY);
    }

    private void renderContent(GuiGraphicsExtractor graphics, MosaicLayout layout, double scroll, int mouseX, int mouseY) {
        List<ContentRow> rows = contentRows(layout, scroll);
        if (rows.isEmpty()) {
            return;
        }
        graphics.enableScissor(layout.contentLeft(), layout.contentTop(), layout.contentRight(), layout.contentBottom());
        try {
            for (ContentRow row : rows) {
                if (!visibleInContent(row, layout)) {
                    continue;
                }
                row.component().render(graphics, font, row.x(), row.y(), row.width(), row.height(), mouseX, mouseY);
            }
        } finally {
            graphics.disableScissor();
        }

        int total = contentHeight(selectedCategory());
        int visible = layout.contentHeight();
        if (total > visible && visible > 20) {
            double max = total - visible;
            double fraction = max <= 0 ? 0 : scroll / max;
            int barW = 4;
            int barX = layout.mainRight() - 6;
            int trackTop = layout.contentTop();
            int trackBottom = layout.contentBottom();
            int trackH = trackBottom - trackTop;
            int thumbH = Math.max(16, (int) (trackH * ((double) visible / total)));
            int thumbY = trackTop + (int) ((trackH - thumbH) * Math.max(0, Math.min(1, fraction)));
            RoundedRectRenderState.fill(graphics, barX, trackTop, barX + barW, trackBottom, 2, SettingsTheme.SCROLLBAR_BG);
            RoundedRectRenderState.fill(graphics, barX, thumbY, barX + barW, thumbY + thumbH, 2, SettingsTheme.SCROLLBAR_THUMB);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (categories == null) {
            return super.mouseClicked(event, doubleClick);
        }

        double mx = event.x();
        double my = event.y();
        int button = event.button();
        MosaicLayout layout = MosaicLayout.compute(width, height);

        double effectiveSide = clampScroll(sidebarScroll, sidebarContentHeight(), sidebarVisibleHeight(layout));
        for (SidebarRow row : sidebarRows(layout, effectiveSide)) {
            if (!visibleInSidebar(row, layout)) {
                continue;
            }
            if (mx >= row.x() && mx < row.x() + row.width() && my >= row.y() && my < row.y() + row.height()) {
                if (row.index() != clampedSelectedIndex()) {
                    selectedIndex = row.index();
                    mainScroll = 0;
                }
                return true;
            }
        }

        double effectiveMain = clampScroll(mainScroll, contentHeight(selectedCategory()), layout.contentHeight());
        for (ContentRow row : contentRows(layout, effectiveMain)) {
            if (!visibleInContent(row, layout)) {
                continue;
            }
            if (mx >= row.x() && mx < row.x() + row.width() && my >= row.y() && my < row.y() + row.height()) {
                boolean consumed = row.component().mouseClicked(mx, my, button, row.x(), row.y(), row.width(), row.height());
                if (consumed) {
                    if (row.component() instanceof SliderSettingComponent slider && slider.isLeftClick(button)) {
                        draggingSlider = slider;
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingSlider != null) {
            MosaicLayout layout = MosaicLayout.compute(width, height);
            double effectiveMain = clampScroll(mainScroll, contentHeight(selectedCategory()), layout.contentHeight());
            for (ContentRow row : contentRows(layout, effectiveMain)) {
                if (row.component() == draggingSlider) {
                    row.component().mouseDragged(event.x(), event.y(), event.button(),
                            row.x(), row.y(), row.width(), row.height());
                    return true;
                }
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingSlider != null) {
            draggingSlider = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        MosaicLayout layout = MosaicLayout.compute(width, height);
        boolean overMain = mouseX >= layout.mainLeft() && mouseX < layout.mainRight()
                && mouseY >= layout.mainTop() && mouseY < layout.mainBottom();
        if (overMain) {
            int total = contentHeight(selectedCategory());
            int visible = layout.contentHeight();
            if (total > visible) {
                mainScroll = clampScroll(mainScroll - scrollY * 12, total, visible);
                return true;
            }
            return false;
        }
        boolean overSide = mouseX >= layout.sideLeft() && mouseX < layout.sideRight()
                && mouseY >= layout.sideTop() && mouseY < layout.sideBottom();
        if (overSide) {
            int total = sidebarContentHeight();
            int visible = sidebarVisibleHeight(layout);
            if (total > visible) {
                sidebarScroll = clampScroll(sidebarScroll - scrollY * 12, total, visible);
                return true;
            }
            return false;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            return super.keyPressed(event);
        }
        if (categories == null || categories.isEmpty()) {
            return super.keyPressed(event);
        }
        if (event.isUp()) {
            selectedIndex = (clampedSelectedIndex() - 1 + categories.size()) % categories.size();
            mainScroll = 0;
            return true;
        }
        if (event.isDown()) {
            selectedIndex = (clampedSelectedIndex() + 1) % categories.size();
            mainScroll = 0;
            return true;
        }
        return super.keyPressed(event);
    }
}
