package org.z2six.rpgcalendar.server;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.z2six.rpgcalendar.config.RPGCalendarConfig;
import org.z2six.rpgcalendar.network.RPGCalendarPayloads;

import java.util.List;
import java.util.Objects;

/**
 * GAME bus hook that syncs server settings to clients on login and when configs change.
 */
public final class RPGCalendarServerSyncEvents {

    private static final Logger LOG = LogUtils.getLogger();

    private static volatile boolean REGISTERED = false;

    private static int tickCounter = 0;
    private static int lastSettingsHash = 0;
    private static boolean lastInitialized = false;

    private RPGCalendarServerSyncEvents() {
        // no-op
    }

    public static void registerGameBus() {
        if (REGISTERED) {
            LOG.debug("[RPGCalendarServerSyncEvents] registerGameBus(): already registered, skipping");
            return;
        }
        try {
            NeoForge.EVENT_BUS.addListener(RPGCalendarServerSyncEvents::onServerTick);
            NeoForge.EVENT_BUS.addListener(RPGCalendarServerSyncEvents::onPlayerLoggedIn);
            REGISTERED = true;
            LOG.debug("[RPGCalendarServerSyncEvents] Registered on NeoForge EVENT_BUS");
        } catch (Throwable t) {
            LOG.error("[RPGCalendarServerSyncEvents] registerGameBus failed safely", t);
        }
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        try {
            if (!(event.getEntity() instanceof ServerPlayer sp)) {
                return;
            }

            ServerLevel level = sp.serverLevel();
            if (level == null) {
                return;
            }

            RPGCalendarPayloads.sendSettingsToPlayer(level, sp);
        } catch (Throwable t) {
            LOG.error("[RPGCalendarServerSyncEvents] onPlayerLoggedIn failed safely", t);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        try {
            MinecraftServer server = event.getServer();
            if (server == null) return;

            tickCounter++;
            if ((tickCounter % 20) != 0) {
                return; // check once per second
            }

            List<String> monthNames = RPGCalendarConfig.getMonthNamesList();
            String yearSuffix = RPGCalendarConfig.getYearSuffix();
            int daysPerMonth = RPGCalendarConfig.getDaysPerMonth();
            int ticksPerDay = RPGCalendarConfig.TICKS_PER_DAY;
            boolean useCustomFontNow = RPGCalendarConfig.getUseCustomFont();

            int settingsHash = Objects.hash(monthNames, yearSuffix, daysPerMonth, ticksPerDay, useCustomFontNow);

            boolean changed = false;
            if (!lastInitialized) {
                lastInitialized = true;
                lastSettingsHash = settingsHash;
            } else if (lastSettingsHash != settingsHash) {
                lastSettingsHash = settingsHash;
                changed = true;
            }

            if (!changed) {
                return;
            }

            ServerLevel overworld = server.overworld();
            if (overworld == null) {
                return;
            }

            RPGCalendarPayloads.broadcastSettings(overworld);

            if (LOG.isDebugEnabled()) {
                LOG.debug("[RPGCalendarServerSyncEvents] Broadcast settings due to config-change (useCustomFont={})",
                        useCustomFontNow);
            }
        } catch (Throwable t) {
            LOG.error("[RPGCalendarServerSyncEvents] onServerTick failed safely", t);
        }
    }
}
