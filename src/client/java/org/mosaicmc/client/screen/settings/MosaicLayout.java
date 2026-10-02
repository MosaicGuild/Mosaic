package org.mosaicmc.client.screen.settings;

/**
 * Centered panel geometry shared by rendering and input.
 *
 * <p>All positions derive from the current screen size; nothing is a
 * hardcoded absolute coordinate. The layout consists of the main panel,
 * the left sidebar for category navigation, and the top bar showing the
 * selected category title.
 */
public record MosaicLayout(
        int mainLeft,
        int mainTop,
        int mainRight,
        int mainBottom,
        int sideLeft,
        int sideTop,
        int sideRight,
        int sideBottom,
        int topLeft,
        int topTop,
        int topRight,
        int topBottom
) {
    public int mainWidth() {
        return mainRight - mainLeft;
    }

    public int mainHeight() {
        return mainBottom - mainTop;
    }

    public int sideWidth() {
        return sideRight - sideLeft;
    }

    public int contentLeft() {
        return mainLeft + SettingsTheme.CONTENT_PAD;
    }

    public int contentRight() {
        return mainRight - SettingsTheme.CONTENT_PAD;
    }

    public int contentTop() {
        return mainTop + SettingsTheme.CONTENT_PAD;
    }

    public int contentBottom() {
        return mainBottom - SettingsTheme.CONTENT_PAD;
    }

    public int contentWidth() {
        return Math.max(0, contentRight() - contentLeft());
    }

    public int contentHeight() {
        return Math.max(0, contentBottom() - contentTop());
    }

    /**
     * Compute a centered layout that fits inside the current screen,
     * shrinking the main panel first on small windows.
     */
    public static MosaicLayout compute(int screenWidth, int screenHeight) {
        int gap = SettingsTheme.GAP;

        int desiredMainW = 400;
        int desiredMainH = 220;
        int desiredSideW = 120;
        int desiredBarH = 28;

        int mainW = desiredMainW;
        int mainH = desiredMainH;
        int sideW = desiredSideW;
        int barH = desiredBarH;

        int margin = 12;
        int maxW = Math.max(160, screenWidth - margin);
        int maxH = Math.max(120, screenHeight - margin);

        int totalW = mainW + gap + sideW;
        if (totalW > maxW) {
            int excess = totalW - maxW;
            int mainShrink = Math.min(excess, Math.max(0, mainW - 180));
            mainW -= mainShrink;
            excess -= mainShrink;
            if (excess > 0) {
                int sideShrink = Math.min(excess, Math.max(0, sideW - 84));
                sideW -= sideShrink;
                excess -= sideShrink;
            }
            if (excess > 0) {
                mainW = Math.max(120, mainW - excess);
            }
        }

        int totalH = mainH + gap + barH;
        if (totalH > maxH) {
            int excess = totalH - maxH;
            int mainShrink = Math.min(excess, Math.max(0, mainH - 110));
            mainH -= mainShrink;
            excess -= mainShrink;
            if (excess > 0) {
                int barShrink = Math.min(excess, Math.max(0, barH - 22));
                barH -= barShrink;
                excess -= barShrink;
            }
            if (excess > 0) {
                mainH = Math.max(80, mainH - excess);
            }
        }

        totalW = mainW + gap + sideW;
        totalH = mainH + gap + barH;
        int totalLeft = (screenWidth - totalW) / 2;
        int totalTop = (screenHeight - totalH) / 2;

        int mainLeft = totalLeft + sideW + gap;
        int mainTop = totalTop + barH + gap;

        int sideLeft = totalLeft;
        int sideRight = sideLeft + sideW;
        int topLeft = mainLeft;

        return new MosaicLayout(
                mainLeft, mainTop, mainLeft + mainW, mainTop + mainH,
                sideLeft, totalTop, sideRight, mainTop + mainH,
                topLeft, totalTop, topLeft + mainW, totalTop + barH
        );
    }
}
