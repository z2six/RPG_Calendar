package org.z2six.rpgtimeline.calendar;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.z2six.rpgtimeline.Constants;

import java.util.Arrays;

/**
 * CalendarDefinition
 *
 * Immutable, loader-agnostic description of the custom in-game calendar:
 *
 *  - Month names (ordered)
 *  - Year suffix string (e.g. "AN")
 *  - Days per month
 *  - Ticks per day (how many Minecraft ticks correspond to a single in-game day)
 *
 * This is pure data; no NeoForge / Fabric types here.
 * The NeoForge config (RPGTimelineConfig) creates instances of this class on the server.
 *
 * NOTE:
 *  - The constructor is PUBLIC so that platform-specific code can create instances.
 *  - All validation is defensive; invalid arguments are clamped / replaced
 *    with safe defaults and logged instead of crashing.
 *
 * Also exposes:
 *  - defaultDefinition(): common default used when no server config is available.
 *  - getEraSuffix(): legacy alias for getYearSuffix(), to keep older code compiling.
 */
public final class CalendarDefinition {

    private static final Logger LOG = LogUtils.getLogger();

    /**
     * Default month names used if something goes badly wrong when constructing
     * a CalendarDefinition. These are only hard fallbacks; normal defaults are
     * supplied by the platform config (RPGTimelineConfig).
     */
    private static final String[] HARD_DEFAULT_MONTHS = new String[]{
            "Dawnroot",
            "Blossomwake",
            "Greengale",
            "Suncrest",
            "Stormfall",
            "Shadowmere",
            "Frostveil",
            "Darkrest"
    };

    private static final String HARD_DEFAULT_YEAR_SUFFIX = "A.N.";
    private static final int HARD_DEFAULT_DAYS_PER_MONTH = 28;
    private static final long HARD_DEFAULT_TICKS_PER_DAY = 24000L;

    private final String[] monthNames;
    private final String[] monthAbbreviations;
    private final String yearSuffix;
    private final int daysPerMonth;
    private final long ticksPerDay;

    /**
     * Public constructor used by platform-specific code (e.g. RPGTimelineConfig on NeoForge).
     *
     * @param monthNames   ordered list of month names; must have length >= 1
     * @param yearSuffix   suffix string like "AN"
     * @param daysPerMonth number of days in each month (>= 1)
     * @param ticksPerDay  number of Minecraft ticks per in-game day (>= 1)
     */
    public CalendarDefinition(
            @NotNull String[] monthNames,
            @NotNull String yearSuffix,
            int daysPerMonth,
            long ticksPerDay
    ) {
        this(monthNames, null, yearSuffix, daysPerMonth, ticksPerDay);
    }

    /**
     * Constructor with optional month abbreviations.
     *
     * @param monthNames ordered list of month names; must have length >= 1
     * @param monthAbbreviations optional abbreviations aligned to monthNames; can be null
     * @param yearSuffix suffix string like "AN"
     * @param daysPerMonth number of days in each month (>= 1)
     * @param ticksPerDay number of Minecraft ticks per in-game day (>= 1)
     */
    public CalendarDefinition(
            @NotNull String[] monthNames,
            String @Nullable [] monthAbbreviations,
            @NotNull String yearSuffix,
            int daysPerMonth,
            long ticksPerDay
    ) {
        String[] validatedMonths = validateMonths(monthNames);
        String validatedSuffix = validateYearSuffix(yearSuffix);
        int validatedDaysPerMonth = validateDaysPerMonth(daysPerMonth);
        long validatedTicksPerDay = validateTicksPerDay(ticksPerDay);
        String[] validatedAbbrevs = validateMonthAbbreviations(monthAbbreviations, validatedMonths);

        this.monthNames = validatedMonths;
        this.monthAbbreviations = validatedAbbrevs;
        this.yearSuffix = validatedSuffix;
        this.daysPerMonth = validatedDaysPerMonth;
        this.ticksPerDay = validatedTicksPerDay;

        LOG.debug(
                "[CalendarDefinition] Created with months={} daysPerMonth={} suffix='{}' ticksPerDay={}",
                Arrays.toString(this.monthNames),
                this.daysPerMonth,
                this.yearSuffix,
                this.ticksPerDay
        );
    }

    // ---------------------------------------------------------------------
    // Static defaults / legacy helpers
    // ---------------------------------------------------------------------

    /**
     * Common-side default definition used when no server config has been
     * synced yet.
     */
    public static @NotNull CalendarDefinition defaultDefinition() {
        try {
            return new CalendarDefinition(
                    HARD_DEFAULT_MONTHS.clone(),
                    buildFallbackAbbreviations(HARD_DEFAULT_MONTHS),
                    HARD_DEFAULT_YEAR_SUFFIX,
                    HARD_DEFAULT_DAYS_PER_MONTH,
                    HARD_DEFAULT_TICKS_PER_DAY
            );
        } catch (Throwable t) {
            LOG.error("[CalendarDefinition] defaultDefinition() failed; using ultra-safe fallback", t);
            // Last-resort fallback, should basically never happen.
            return new CalendarDefinition(
                    new String[]{"Month1"},
                    buildFallbackAbbreviations(new String[]{"Month1"}),
                    "A.N.",
                    28,
                    24000L
            );
        }
    }

    /**
     * Legacy alias kept so existing code that calls getEraSuffix() still compiles.
     * Internally just returns the year suffix (e.g. "AN").
     */
    public @NotNull String getEraSuffix() {
        return getYearSuffix();
    }

    // ---------------------------------------------------------------------
    // Validation helpers
    // ---------------------------------------------------------------------

    private static String @NotNull [] validateMonths(String @NotNull [] input) {
        try {
            if (input.length == 0) {
                LOG.warn("[CalendarDefinition] monthNames was empty; using hard default months");
                return HARD_DEFAULT_MONTHS.clone();
            }

            String[] copy = new String[input.length];
            for (int i = 0; i < input.length; i++) {
                String raw = input[i];
                if (raw == null || raw.isBlank()) {
                    LOG.warn("[CalendarDefinition] monthNames[{}] was blank/null; replacing with 'Month{}'", i, i + 1);
                    copy[i] = "Month" + (i + 1);
                } else {
                    copy[i] = raw;
                }
            }
            return copy;
        } catch (Throwable t) {
            LOG.error("[CalendarDefinition] validateMonths failed; using hard default months", t);
            return HARD_DEFAULT_MONTHS.clone();
        }
    }

    private static @NotNull String validateYearSuffix(@NotNull String input) {
        try {
            if (input.isBlank()) {
                LOG.warn("[CalendarDefinition] yearSuffix was blank; using hard default '{}'", HARD_DEFAULT_YEAR_SUFFIX);
                return HARD_DEFAULT_YEAR_SUFFIX;
            }
            return input;
        } catch (Throwable t) {
            LOG.error("[CalendarDefinition] validateYearSuffix failed; using hard default '{}'", HARD_DEFAULT_YEAR_SUFFIX, t);
            return HARD_DEFAULT_YEAR_SUFFIX;
        }
    }

    private static String @NotNull [] validateMonthAbbreviations(
            String @Nullable [] input,
            String @NotNull [] monthNames
    ) {
        try {
            if (input == null || input.length == 0) {
                return buildFallbackAbbreviations(monthNames);
            }
            if (input.length != monthNames.length) {
                LOG.warn("[CalendarDefinition] monthAbbreviations length {} did not match monthNames length {}; using fallback",
                        input.length, monthNames.length);
                return buildFallbackAbbreviations(monthNames);
            }
            String[] copy = new String[input.length];
            for (int i = 0; i < input.length; i++) {
                String raw = input[i];
                if (raw == null || raw.isBlank()) {
                    copy[i] = buildFallbackAbbreviation(monthNames[i]);
                } else {
                    copy[i] = raw;
                }
            }
            return copy;
        } catch (Throwable t) {
            LOG.error("[CalendarDefinition] validateMonthAbbreviations failed; using fallback abbreviations", t);
            return buildFallbackAbbreviations(monthNames);
        }
    }

    private static String @NotNull [] buildFallbackAbbreviations(String @NotNull [] monthNames) {
        String[] result = new String[monthNames.length];
        for (int i = 0; i < monthNames.length; i++) {
            result[i] = buildFallbackAbbreviation(monthNames[i]);
        }
        return result;
    }

    private static @NotNull String buildFallbackAbbreviation(String monthName) {
        if (monthName == null || monthName.isBlank()) {
            return "Mon";
        }
        String trimmed = monthName.trim();
        if (trimmed.length() <= 3) {
            return trimmed;
        }
        return trimmed.substring(0, 3);
    }

    private static int validateDaysPerMonth(int value) {
        try {
            if (value <= 0) {
                LOG.warn("[CalendarDefinition] daysPerMonth={} invalid; using hard default {}", value, HARD_DEFAULT_DAYS_PER_MONTH);
                return HARD_DEFAULT_DAYS_PER_MONTH;
            }
            return value;
        } catch (Throwable t) {
            LOG.error("[CalendarDefinition] validateDaysPerMonth failed; using hard default {}", HARD_DEFAULT_DAYS_PER_MONTH, t);
            return HARD_DEFAULT_DAYS_PER_MONTH;
        }
    }

    private static long validateTicksPerDay(long value) {
        try {
            if (value <= 0L) {
                LOG.warn("[CalendarDefinition] ticksPerDay={} invalid; using hard default {}", value, HARD_DEFAULT_TICKS_PER_DAY);
                return HARD_DEFAULT_TICKS_PER_DAY;
            }
            return value;
        } catch (Throwable t) {
            LOG.error("[CalendarDefinition] validateTicksPerDay failed; using hard default {}", HARD_DEFAULT_TICKS_PER_DAY, t);
            return HARD_DEFAULT_TICKS_PER_DAY;
        }
    }

    // ---------------------------------------------------------------------
    // Basic accessors
    // ---------------------------------------------------------------------

    public int getMonthCount() {
        return monthNames.length;
    }

    public @NotNull String[] getMonthNames() {
        // Defensive copy to keep immutability.
        return monthNames.clone();
    }

    public @NotNull String[] getMonthAbbreviations() {
        return monthAbbreviations.clone();
    }

    public @NotNull String getMonthName(int index) {
        if (index < 0 || index >= monthNames.length) {
            LOG.warn("[CalendarDefinition] getMonthName: index {} out of range (0..{}); clamping", index, monthNames.length - 1);
            int clamped = Math.max(0, Math.min(index, monthNames.length - 1));
            return monthNames[clamped];
        }
        return monthNames[index];
    }

    public @NotNull String getMonthAbbreviation(int index, boolean withDot) {
        int clamped = index;
        if (clamped < 0 || clamped >= monthAbbreviations.length) {
            LOG.warn("[CalendarDefinition] getMonthAbbreviation: index {} out of range (0..{}); clamping", index, monthAbbreviations.length - 1);
            clamped = Math.max(0, Math.min(index, monthAbbreviations.length - 1));
        }
        String value = monthAbbreviations[clamped];
        if (value == null || value.isBlank()) {
            value = buildFallbackAbbreviation(getMonthName(clamped));
        }
        if (withDot && !value.endsWith(".")) {
            return value + ".";
        }
        return value;
    }

    public @NotNull String getYearSuffix() {
        return yearSuffix;
    }

    public int getDaysPerMonth() {
        return daysPerMonth;
    }

    public long getTicksPerDay() {
        return ticksPerDay;
    }

    public int getDaysPerYear() {
        return daysPerMonth * monthNames.length;
    }

    public long getTicksPerYear() {
        return ticksPerDay * getDaysPerYear();
    }

    // ---------------------------------------------------------------------
    // Convenience helpers
    // ---------------------------------------------------------------------

    /**
     * Formats a date like "Day 17 of Dawnroot, 112 AN".
     *
     * @param dayOfMonth 1-based day within month
     * @param monthIndex 0-based month index
     * @param year       absolute year number
     */
    public @NotNull String formatDate(int dayOfMonth, int monthIndex, int year) {
        String month = getMonthName(monthIndex);
        int safeDay = Math.max(1, Math.min(dayOfMonth, daysPerMonth));
        return "Day " + safeDay + " of " + month + ", " + year + " " + yearSuffix;
    }

    @Override
    public String toString() {
        return "CalendarDefinition{" +
                "months=" + Arrays.toString(monthNames) +
                ", monthAbbreviations=" + Arrays.toString(monthAbbreviations) +
                ", yearSuffix='" + yearSuffix + '\'' +
                ", daysPerMonth=" + daysPerMonth +
                ", ticksPerDay=" + ticksPerDay +
                '}';
    }

    static {
        // Tiny debug to confirm class load (once per JVM).
        Constants.LOG.debug("[CalendarDefinition] Class loaded");
    }
}
