package org.z2six.rpgtimeline.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class ChronicleTooltipItem extends Item {

    private final Component description;

    public ChronicleTooltipItem(Properties properties, Component description) {
        super(properties);
        this.description = description;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (description != null) {
            tooltip.add(description.copy().withStyle(ChatFormatting.GRAY));
        }
    }
}
