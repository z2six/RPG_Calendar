package org.z2six.rpgtimeline.compat;

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
    private static Method getSubSeasonDuration;
    private static Method getSeasonDuration;
    private static Method getCycleDuration;
    private static Method getSeasonCycleTicks;
    private static Method getDayDuration;
    private static Method getDay;
    private static Method getSeason;
    private static Method getSubSeason;

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
            int subSeasonDuration = (int) getSubSeasonDuration.invoke(state);
            int seasonDuration = (int) getSeasonDuration.invoke(state);
            int cycleDuration = (int) getCycleDuration.invoke(state);
            int cycleTicks = (int) getSeasonCycleTicks.invoke(state);
            int dayDuration = (int) getDayDuration.invoke(state);
            int day = (int) getDay.invoke(state);
            String season = safeEnumName(getSeason.invoke(state));
            String subSeason = safeEnumName(getSubSeason.invoke(state));
            return new SeasonSnapshot(
                    seasonDuration,
                    cycleDuration,
                    cycleTicks,
                    dayDuration,
                    subSeasonDuration,
                    day,
                    season,
                    subSeason
            );
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
            getSubSeasonDuration = state.getMethod("getSubSeasonDuration");
            getSeasonDuration = state.getMethod("getSeasonDuration");
            getCycleDuration = state.getMethod("getCycleDuration");
            getSeasonCycleTicks = state.getMethod("getSeasonCycleTicks");
            getDayDuration = state.getMethod("getDayDuration");
            getDay = state.getMethod("getDay");
            getSeason = state.getMethod("getSeason");
            getSubSeason = state.getMethod("getSubSeason");
            available = true;
        } catch (Throwable t) {
            LOG.debug("Serene Seasons integration not available.", t);
            available = false;
        }
        return available;
    }

    private static String safeEnumName(Object value) {
        if (value == null) {
            return "";
        }
        try {
            if (value instanceof Enum<?> e) {
                return e.name();
            }
        } catch (Throwable ignored) {
            // no-op
        }
        return value.toString();
    }

    public record SeasonSnapshot(
            int seasonDuration,
            int cycleDuration,
            int cycleTicks,
            int dayDuration,
            int subSeasonDuration,
            int day,
            String season,
            String subSeason
    ) {
    }
}
