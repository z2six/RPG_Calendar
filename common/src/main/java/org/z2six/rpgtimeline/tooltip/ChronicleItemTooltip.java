package org.z2six.rpgtimeline.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

public record ChronicleItemTooltip(ItemStack stack) implements TooltipComponent {
}
