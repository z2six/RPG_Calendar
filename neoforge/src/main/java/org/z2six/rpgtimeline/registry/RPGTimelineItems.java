package org.z2six.rpgtimeline.registry;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.z2six.rpgtimeline.Constants;
import org.z2six.rpgtimeline.item.ChronicleTooltipItem;

public final class RPGTimelineItems {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Constants.MOD_ID);

    public static final DeferredItem<Item> CHRONICLE_NOTE = ITEMS.register(
            "chronicle_note",
            () -> new ChronicleTooltipItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.COMMON),
                    Component.translatable("item.rpgtimeline.chronicle_note.desc")
            )
    );

    public static final DeferredItem<Item> CHRONICLE_WORLD_FIRST = ITEMS.register(
            "chronicle_world_first",
            () -> new ChronicleTooltipItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    Component.translatable("item.rpgtimeline.chronicle_world_first.desc")
            )
    );

    public static final DeferredItem<Item> CHRONICLE_ADVANCEMENT = ITEMS.register(
            "chronicle_advancement",
            () -> new ChronicleTooltipItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON),
                    Component.translatable("item.rpgtimeline.chronicle_advancement.desc")
            )
    );

    private RPGTimelineItems() {
        // no-op
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
