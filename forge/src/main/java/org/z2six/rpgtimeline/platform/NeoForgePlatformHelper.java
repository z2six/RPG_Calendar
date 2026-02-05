package org.z2six.rpgtimeline.platform;

import com.mojang.logging.LogUtils;
import net.minecraft.advancements.Advancement;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import org.slf4j.Logger;
import org.z2six.rpgtimeline.calendar.CalendarDefinition;
import org.z2six.rpgtimeline.chronicle.ChronicleScope;
import org.z2six.rpgtimeline.chronicle.server.ChronicleService;
import org.z2six.rpgtimeline.config.RPGTimelineConfig;
import org.z2six.rpgtimeline.network.RPGTimelinePayloads;
import org.z2six.rpgtimeline.platform.services.IPlatformHelper;

public class NeoForgePlatformHelper implements IPlatformHelper {

    private static final Logger LOG = LogUtils.getLogger();

    @Override
    public String getPlatformName() {
        return "Forge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }

    @Override
    public CalendarDefinition getCalendarDefinition() {
        try {
            if (FMLEnvironment.dist == Dist.CLIENT && RPGTimelinePayloads.ClientState.hasSynced()) {
                return RPGTimelinePayloads.ClientState.getCalendarDefinition();
            }
            return RPGTimelineConfig.getCalendarDefinition();
        } catch (Throwable t) {
            LOG.error("[NeoForgePlatformHelper] getCalendarDefinition() failed", t);
            return CalendarDefinition.defaultDefinition();
        }
    }

    @Override
    public void addChronicleNote(ServerPlayer player, ChronicleScope scope, String title, String details, long dayIndex) {
        if (player == null || scope == null) {
            return;
        }
        if (dayIndex < 0L) {
            ChronicleService.addAdminEvent(player, scope, title, details);
        } else {
            ChronicleService.addAdminEvent(player, scope, title, details, dayIndex);
        }
    }

    @Override
    public void recordAdvancement(ServerPlayer player, Advancement advancement) {
        ChronicleService.recordAdvancement(player, advancement);
    }

    @Override
    public void recordExternalAdvancement(
            ServerPlayer player,
            String sourceId,
            Component title,
            Component description,
            String iconItemId
    ) {
        ChronicleService.recordExternalAdvancement(player, sourceId, title, description, iconItemId);
    }
}
