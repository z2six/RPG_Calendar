package org.z2six.rpgtimeline.server;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.z2six.rpgtimeline.config.RPGTimelineConfig;
import org.z2six.rpgtimeline.network.RPGTimelinePayloads;
import org.z2six.rpgtimeline.server.RPGTimelineCalendarSavedData;

import java.util.List;
import java.util.Objects;

/**
 * GAME bus hook that syncs server settings to clients on login and when configs change.
 */
public final class RPGTimelineServerSyncEvents {

    private static final Logger LOG = LogUtils.getLogger();

    private static volatile boolean REGISTERED = false;

    private static int tickCounter = 0;
    private static int lastSettingsHash = 0;
    private static boolean lastInitialized = false;

    private RPGTimelineServerSyncEvents() {
        // no-op
    }

    public static void registerGameBus() {
        if (REGISTERED) {
            LOG.debug("[RPGTimelineServerSyncEvents] registerGameBus(): already registered, skipping");
            return;
        }
        try {
            NeoForge.EVENT_BUS.addListener(RPGTimelineServerSyncEvents::onServerTick);
            NeoForge.EVENT_BUS.addListener(RPGTimelineServerSyncEvents::onPlayerLoggedIn);
            REGISTERED = true;
            LOG.debug("[RPGTimelineServerSyncEvents] Registered on NeoForge EVENT_BUS");
        } catch (Throwable t) {
            LOG.error("[RPGTimelineServerSyncEvents] registerGameBus failed safely", t);
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

            RPGTimelinePayloads.sendSettingsToPlayer(level, sp);
        } catch (Throwable t) {
            LOG.error("[RPGTimelineServerSyncEvents] onPlayerLoggedIn failed safely", t);
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

            List<String> monthNames = RPGTimelineConfig.getMonthNamesList();
            List<String> monthAbbreviations = RPGTimelineConfig.getMonthAbbreviationsList();
            String yearSuffix = RPGTimelineConfig.getYearSuffix();
            int daysPerMonth = RPGTimelineConfig.getDaysPerMonth();
            int ticksPerDay = RPGTimelineConfig.TICKS_PER_DAY;
            boolean useCustomFontNow = RPGTimelineConfig.getUseCustomFont();
            boolean useSereneSeasons = RPGTimelineConfig.getUseSereneSeasons();
            org.z2six.rpgtimeline.calendar.SeasonMonthMapping mapping = RPGTimelineConfig.getSeasonMonthMapping();
            long calendarDayOffsetDays = RPGTimelineCalendarSavedData.get(server).getDayOffsetDays();

            int settingsHash = Objects.hash(
                    monthNames,
                    monthAbbreviations,
                    yearSuffix,
                    daysPerMonth,
                    ticksPerDay,
                    calendarDayOffsetDays,
                    useCustomFontNow,
                    useSereneSeasons,
                    mapping.spring(),
                    mapping.summer(),
                    mapping.autumn(),
                    mapping.winter()
            );

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

            RPGTimelinePayloads.broadcastSettings(overworld);

            if (LOG.isDebugEnabled()) {
                LOG.debug("[RPGTimelineServerSyncEvents] Broadcast settings due to config-change (useCustomFont={})",
                        useCustomFontNow);
            }
        } catch (Throwable t) {
            LOG.error("[RPGTimelineServerSyncEvents] onServerTick failed safely", t);
        }
    }
}
