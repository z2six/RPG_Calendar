package org.z2six.rpgtimeline.chronicle.server;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import org.slf4j.Logger;

/**
 * Server-side chronicle event hooks.
 */
public final class ChronicleServerEvents {

    private static final Logger LOG = LogUtils.getLogger();
    private static volatile boolean REGISTERED = false;

    private ChronicleServerEvents() {
        // no-op
    }

    public static void registerGameBus() {
        if (REGISTERED) {
            LOG.debug("[ChronicleServerEvents] already registered; skipping");
            return;
        }
        try {
            MinecraftForge.EVENT_BUS.addListener(ChronicleServerEvents::onAdvancementEarned);
            MinecraftForge.EVENT_BUS.addListener(ChronicleServerEvents::onBlockBroken);
            MinecraftForge.EVENT_BUS.addListener(ChronicleServerEvents::onMobKilled);
            MinecraftForge.EVENT_BUS.addListener(ChronicleServerEvents::onItemPickedUp);
            MinecraftForge.EVENT_BUS.addListener(ChronicleServerEvents::onItemCrafted);
            MinecraftForge.EVENT_BUS.addListener(ChronicleServerEvents::onItemSmelted);
            REGISTERED = true;
            LOG.debug("[ChronicleServerEvents] Registered on Forge EVENT_BUS");
        } catch (Throwable t) {
            LOG.error("[ChronicleServerEvents] registerGameBus failed safely", t);
        }
    }

    private static void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        try {
            if (event == null || event.getEntity() == null) {
                return;
            }
            if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) {
                return;
            }
            ChronicleService.recordAdvancement(sp, event.getAdvancement());
        } catch (Throwable t) {
            LOG.error("[ChronicleServerEvents] onAdvancementEarned failed safely", t);
        }
    }

    private static void onBlockBroken(BlockEvent.BreakEvent event) {
        try {
            if (event == null || event.getPlayer() == null) {
                return;
            }
            if (!(event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer sp)) {
                return;
            }
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock());
            ChronicleGoalService.handleBlockBreak(sp, blockId);
        } catch (Throwable t) {
            LOG.error("[ChronicleServerEvents] onBlockBroken failed safely", t);
        }
    }

    private static void onMobKilled(LivingDeathEvent event) {
        try {
            if (event == null || event.getSource() == null) {
                return;
            }
            if (!(event.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) {
                return;
            }
            ResourceLocation mobId = BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType());
            ChronicleGoalService.handleMobKill(sp, mobId);
        } catch (Throwable t) {
            LOG.error("[ChronicleServerEvents] onMobKilled failed safely", t);
        }
    }

    private static void onItemPickedUp(EntityItemPickupEvent event) {
        try {
            if (event == null || event.getEntity() == null || event.getItem() == null) {
                return;
            }
            if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) {
                return;
            }
            net.minecraft.world.item.ItemStack pickedStack = event.getItem().getItem();
            if (pickedStack == null || pickedStack.isEmpty()) {
                return;
            }
            ChronicleGoalService.handleItemGain(sp, pickedStack.copy());
        } catch (Throwable t) {
            LOG.error("[ChronicleServerEvents] onItemPickedUp failed safely", t);
        }
    }

    private static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        try {
            if (event == null || event.getEntity() == null || event.getCrafting() == null) {
                return;
            }
            if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) {
                return;
            }
            ChronicleGoalService.handleItemGain(sp, event.getCrafting());
        } catch (Throwable t) {
            LOG.error("[ChronicleServerEvents] onItemCrafted failed safely", t);
        }
    }

    private static void onItemSmelted(PlayerEvent.ItemSmeltedEvent event) {
        try {
            if (event == null || event.getEntity() == null || event.getSmelting() == null) {
                return;
            }
            if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) {
                return;
            }
            ChronicleGoalService.handleItemGain(sp, event.getSmelting());
        } catch (Throwable t) {
            LOG.error("[ChronicleServerEvents] onItemSmelted failed safely", t);
        }
    }
}
