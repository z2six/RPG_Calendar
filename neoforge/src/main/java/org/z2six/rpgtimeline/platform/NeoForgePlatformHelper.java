package org.z2six.rpgtimeline.platform;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import org.slf4j.Logger;
import org.z2six.rpgtimeline.calendar.CalendarDefinition;
import org.z2six.rpgtimeline.config.RPGTimelineConfig;
import org.z2six.rpgtimeline.network.RPGTimelinePayloads;
import org.z2six.rpgtimeline.platform.services.IPlatformHelper;

public class NeoForgePlatformHelper implements IPlatformHelper {

    private static final Logger LOG = LogUtils.getLogger();

    @Override
    public String getPlatformName() {
        return "NeoForge";
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
}
