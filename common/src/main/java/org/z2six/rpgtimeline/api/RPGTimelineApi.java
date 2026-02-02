package org.z2six.rpgtimeline.api;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.z2six.rpgtimeline.calendar.CalendarDate;
import org.z2six.rpgtimeline.calendar.CalendarDefinition;
import org.z2six.rpgtimeline.calendar.RPGTimelineMath;
import org.z2six.rpgtimeline.platform.Services;

/**
 * Public API entry point for RPG Calendar.
 */
public final class RPGTimelineApi {

    private static final Logger LOG = LogUtils.getLogger();

    private RPGTimelineApi() {
        // no-op
    }

    /**
     * Returns the active calendar definition. On NeoForge this comes from the server config.
     */
    public static @NotNull CalendarDefinition getCalendarDefinition() {
        try {
            return Services.PLATFORM.getCalendarDefinition();
        } catch (Throwable t) {
            LOG.error("[RPGTimelineApi] getCalendarDefinition failed; using default", t);
            return CalendarDefinition.defaultDefinition();
        }
    }

    /**
     * Converts world time into a CalendarDate using the active definition.
     */
    public static @NotNull CalendarDate fromGameTime(long gameTime) {
        return RPGTimelineMath.fromGameTime(gameTime, getCalendarDefinition());
    }

    /**
     * Formats a date string using the active definition and raw world time.
     * This uses the CalendarDate year numbering (0-based year).
     */
    public static @NotNull String formatDate(long gameTime) {
        return RPGTimelineMath.formatDate(gameTime, getCalendarDefinition());
    }

    /**
     * Converts world time into a 0-based day index.
     */
    public static long getDayIndexForGameTime(long gameTime) {
        CalendarDefinition def = getCalendarDefinition();
        long ticksPerDay = def.getTicksPerDay();
        if (ticksPerDay <= 0L) {
            LOG.warn("[RPGTimelineApi] ticksPerDay <= 0 ({}), using 24000 fallback", ticksPerDay);
            ticksPerDay = 24000L;
        }
        return Math.floorDiv(gameTime, ticksPerDay);
    }

    /**
     * Builds the display string for a given day index using the active definition.
     * Day index 0 -> "Day 1 of Dawnroot, 1 A.N." (with defaults).
     */
    public static @NotNull Component buildDateMessage(long dayIndex) {
        try {
            CalendarDefinition def = getCalendarDefinition();
            int daysPerMonth = def.getDaysPerMonth();
            int monthsPerYear = def.getMonthCount();

            if (daysPerMonth <= 0) {
                LOG.warn("[RPGTimelineApi] buildDateMessage: daysPerMonth <= 0 ({}), falling back to 28", daysPerMonth);
                daysPerMonth = 28;
            }
            if (monthsPerYear <= 0) {
                LOG.warn("[RPGTimelineApi] buildDateMessage: monthsPerYear <= 0 ({}), falling back to 8", monthsPerYear);
                monthsPerYear = 8;
            }

            long totalDaysPerYear = (long) daysPerMonth * (long) monthsPerYear;
            if (totalDaysPerYear <= 0L) {
                LOG.warn("[RPGTimelineApi] buildDateMessage: totalDaysPerYear <= 0, forcing safe fallback");
                totalDaysPerYear = (long) daysPerMonth * 8L;
            }

            if (dayIndex < 0L) {
                LOG.warn("[RPGTimelineApi] buildDateMessage: dayIndex < 0 ({}), clamping to 0", dayIndex);
                dayIndex = 0L;
            }

            long yearIndex = dayIndex / totalDaysPerYear; // 0-based
            int yearNumber = (int) (yearIndex + 1);       // 1-based display (preserved behavior)

            int dayOfYear = (int) (dayIndex % totalDaysPerYear); // 0..(totalDaysPerYear-1)
            int monthIndex = dayOfYear / daysPerMonth;           // 0..monthsPerYear-1
            int dayOfMonth = (dayOfYear % daysPerMonth) + 1;     // 1..daysPerMonth

            if (monthIndex < 0) {
                monthIndex = 0;
            } else if (monthIndex >= monthsPerYear) {
                monthIndex = monthsPerYear - 1;
            }

            String monthName = def.getMonthName(monthIndex);
            String suffix = def.getYearSuffix();

            String text = "Day " + dayOfMonth + " of " + monthName + ", " + yearNumber + " " + suffix;
            return Component.literal(text);
        } catch (Throwable t) {
            LOG.error("[RPGTimelineApi] buildDateMessage failed, using fallback text", t);
            return Component.literal("A New Day Dawns");
        }
    }

    public static @NotNull Component buildDateMessageFromGameTime(long gameTime) {
        return buildDateMessage(getDayIndexForGameTime(gameTime));
    }

    public static @NotNull String buildDateString(long dayIndex) {
        return buildDateMessage(dayIndex).getString();
    }

    public static @NotNull String buildDateStringFromGameTime(long gameTime) {
        return buildDateMessageFromGameTime(gameTime).getString();
    }
}
