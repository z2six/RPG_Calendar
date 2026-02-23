package org.z2six.rpgtimeline.server;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

/**
 * Stores per-world calendar state that should persist across restarts.
 *
 * Currently this is just a day-offset used to align the Timeline calendar to
 * external season/date systems (e.g. Serene Seasons) without changing world time.
 */
public final class RPGTimelineCalendarSavedData extends SavedData {

    private static final Logger LOG = LogUtils.getLogger();
    private static final String DATA_NAME = "rpgtimeline_calendar";

    private static final String KEY_DAY_OFFSET_DAYS = "DayOffsetDays";

    private long dayOffsetDays = 0L;

    private RPGTimelineCalendarSavedData() {
    }

    public static RPGTimelineCalendarSavedData get(MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(RPGTimelineCalendarSavedData::new, RPGTimelineCalendarSavedData::load), DATA_NAME);
    }

    public long getDayOffsetDays() {
        return dayOffsetDays;
    }

    public long setDayOffsetDays(long newOffsetDays) {
        long old = this.dayOffsetDays;
        this.dayOffsetDays = newOffsetDays;
        setDirty();
        if (LOG.isDebugEnabled()) {
            LOG.debug("[RPGTimelineCalendarSavedData] Updated dayOffsetDays {} -> {}", old, newOffsetDays);
        }
        return old;
    }

    public long addDayOffsetDays(long deltaDays) {
        return setDayOffsetDays(this.dayOffsetDays + deltaDays);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLong(KEY_DAY_OFFSET_DAYS, dayOffsetDays);
        return tag;
    }

    private static RPGTimelineCalendarSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RPGTimelineCalendarSavedData data = new RPGTimelineCalendarSavedData();
        try {
            if (tag != null && tag.contains(KEY_DAY_OFFSET_DAYS)) {
                data.dayOffsetDays = tag.getLong(KEY_DAY_OFFSET_DAYS);
            }
        } catch (Throwable t) {
            LOG.error("[RPGTimelineCalendarSavedData] Failed to load; using defaults", t);
        }
        return data;
    }
}

