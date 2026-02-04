package org.z2six.rpgtimeline.client.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.z2six.rpgtimeline.platform.Services;

import java.lang.reflect.Method;

public final class SereneSeasonsCompat {

    private static final Logger LOG = LogUtils.getLogger();
    private static final String MOD_ID = "sereneseasons";
    private static boolean checked = false;
    private static boolean available = false;
    private static Method getSeasonState;
    private static Method getSeasonDuration;
    private static Method getCycleDuration;
    private static Method getSeasonCycleTicks;
    private static Method getDayDuration;

    private SereneSeasonsCompat() {
    }

    public static SeasonSnapshot getSeasonSnapshot(Level level) {
        if (level == null || !ensureAvailable()) {
            return null;
        }
        try {
            Object state = getSeasonState.invoke(null, level);
            if (state == null) {
                return null;
            }
            int seasonDuration = (int) getSeasonDuration.invoke(state);
            int cycleDuration = (int) getCycleDuration.invoke(state);
            int cycleTicks = (int) getSeasonCycleTicks.invoke(state);
            int dayDuration = (int) getDayDuration.invoke(state);
            return new SeasonSnapshot(seasonDuration, cycleDuration, cycleTicks, dayDuration);
        } catch (Throwable t) {
            LOG.debug("Serene Seasons snapshot failed.", t);
            return null;
        }
    }

    private static boolean ensureAvailable() {
        if (checked) {
            return available;
        }
        checked = true;
        if (!Services.PLATFORM.isModLoaded(MOD_ID)) {
            return false;
        }
        try {
            Class<?> helper = Class.forName("sereneseasons.api.season.SeasonHelper");
            Class<?> state = Class.forName("sereneseasons.api.season.ISeasonState");
            getSeasonState = helper.getMethod("getSeasonState", Level.class);
            getSeasonDuration = state.getMethod("getSeasonDuration");
            getCycleDuration = state.getMethod("getCycleDuration");
            getSeasonCycleTicks = state.getMethod("getSeasonCycleTicks");
            getDayDuration = state.getMethod("getDayDuration");
            available = true;
        } catch (Throwable t) {
            LOG.debug("Serene Seasons integration not available.", t);
            available = false;
        }
        return available;
    }

    public record SeasonSnapshot(int seasonDuration, int cycleDuration, int cycleTicks, int dayDuration) {
    }
}
