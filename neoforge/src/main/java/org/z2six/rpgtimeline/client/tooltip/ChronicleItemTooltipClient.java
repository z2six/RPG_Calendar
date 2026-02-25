package org.z2six.rpgtimeline.client.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.z2six.rpgtimeline.tooltip.ChronicleItemTooltip;

public final class ChronicleItemTooltipClient implements ClientTooltipComponent {

    private static final int ICON_SIZE = 16;
    private static final int ICON_SPACING = 4;
    private static volatile int expectedWidth = 0;

    private final ItemStack stack;

    public ChronicleItemTooltipClient(ChronicleItemTooltip tooltip) {
        this.stack = tooltip.stack();
    }

    @Override
    public int getHeight(Font font) {
        return ICON_SIZE + ICON_SPACING;
    }

    @Override
    public int getWidth(Font font) {
        int width = expectedWidth > 0 ? expectedWidth : ICON_SIZE;
        return Math.max(width, ICON_SIZE);
    }

    @Override
    public void renderImage(Font font, int x, int y, int width, int height, GuiGraphics g) {
        int componentWidth = getWidth(font);
        int iconX = x + (componentWidth - ICON_SIZE) / 2;
        g.renderItem(stack, iconX, y + 1);
    }

    public static void setExpectedWidth(int width) {
        expectedWidth = Math.max(0, width);
    }

    public static void clearExpectedWidth() {
        expectedWidth = 0;
    }
}
