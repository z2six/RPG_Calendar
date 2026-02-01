package org.z2six.rpgcalendar.platform;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import org.slf4j.Logger;
import org.z2six.rpgcalendar.calendar.CalendarDefinition;
import org.z2six.rpgcalendar.config.RPGCalendarConfig;
import org.z2six.rpgcalendar.network.RPGCalendarPayloads;
import org.z2six.rpgcalendar.platform.services.IPlatformHelper;

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
            if (FMLEnvironment.dist == Dist.CLIENT && RPGCalendarPayloads.ClientState.hasSynced()) {
                return RPGCalendarPayloads.ClientState.getCalendarDefinition();
            }
            return RPGCalendarConfig.getCalendarDefinition();
        } catch (Throwable t) {
            LOG.error("[NeoForgePlatformHelper] getCalendarDefinition() failed", t);
            return CalendarDefinition.defaultDefinition();
        }
    }
}
