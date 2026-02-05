package org.z2six.rpgtimeline.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.Level;
import org.z2six.rpgtimeline.tooltip.ChronicleItemTooltip;

import java.util.List;
import java.util.Optional;

public class ChronicleTooltipItem extends Item {

    private final Component description;

    public ChronicleTooltipItem(Properties properties, Component description) {
        super(properties);
        this.description = description;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        if (description != null) {
            tooltip.add(description.copy().withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return Optional.of(new ChronicleItemTooltip(stack));
    }
}
