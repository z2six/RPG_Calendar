package org.z2six.rpgtimeline.chronicle.server;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
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
            NeoForge.EVENT_BUS.addListener(ChronicleServerEvents::onAdvancementEarned);
            NeoForge.EVENT_BUS.addListener(ChronicleServerEvents::onBlockBroken);
            NeoForge.EVENT_BUS.addListener(ChronicleServerEvents::onMobKilled);
            NeoForge.EVENT_BUS.addListener(ChronicleServerEvents::onItemPickedUp);
            NeoForge.EVENT_BUS.addListener(ChronicleServerEvents::onItemCrafted);
            NeoForge.EVENT_BUS.addListener(ChronicleServerEvents::onItemSmelted);
            NeoForge.EVENT_BUS.addListener(ChronicleServerEvents::onServerStarted);
            REGISTERED = true;
            LOG.debug("[ChronicleServerEvents] Registered on NeoForge EVENT_BUS");
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

    private static void onItemPickedUp(ItemEntityPickupEvent.Post event) {
        try {
            if (event == null || event.getPlayer() == null || event.getOriginalStack() == null) {
                return;
            }
            if (!(event.getPlayer() instanceof net.minecraft.server.level.ServerPlayer sp)) {
                return;
            }
            net.minecraft.world.item.ItemStack original = event.getOriginalStack();
            net.minecraft.world.item.ItemStack current = event.getCurrentStack();
            int picked = original.getCount();
            if (current != null && !current.isEmpty() && current.getItem() == original.getItem()) {
                picked = Math.max(0, original.getCount() - current.getCount());
            }
            if (picked <= 0) {
                return;
            }
            ChronicleGoalService.handleItemGain(sp, new net.minecraft.world.item.ItemStack(original.getItem(), picked));
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

    private static void onServerStarted(ServerStartedEvent event) {
        try {
            if (event == null || event.getServer() == null) {
                return;
            }
            ChronicleRetroactiveAdvancementImporter.runAutoImport(event.getServer());
        } catch (Throwable t) {
            LOG.error("[ChronicleServerEvents] onServerStarted failed safely", t);
        }
    }
}
