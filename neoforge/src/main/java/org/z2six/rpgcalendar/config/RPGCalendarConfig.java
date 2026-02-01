package org.z2six.rpgcalendar.config;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;
import org.z2six.rpgcalendar.calendar.CalendarDefinition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * NeoForge-side SERVER config for RPG Calendar.
 *
 * Sections:
 * - [calendar]
 *   * monthNames (list of month names)
 *   * yearSuffix
 *   * daysPerMonth (server-authoritative)
 *   * useCustomFont (server-authoritative)
 */
public final class RPGCalendarConfig {

    private static final Logger LOG = LogUtils.getLogger();

    // ---------------------------------------------------------------------
    // Defaults (also used as fallback if config is invalid)
    // ---------------------------------------------------------------------

    public static final String[] DEFAULT_MONTH_NAMES = new String[]{
            "Dawnroot",
            "Blossomwake",
            "Greengale",
            "Suncrest",
            "Stormfall",
            "Shadowmere",
            "Frostveil",
            "Darkrest"
    };

    public static final String DEFAULT_YEAR_SUFFIX = "A.N.";

    /**
     * Default days-per-month. Historically this was 28.
     */
    public static final int DEFAULT_DAYS_PER_MONTH = 28;

    /**
     * Default for whether the custom calendar font is used on clients.
     */
    public static final boolean DEFAULT_USE_CUSTOM_FONT = true;

    /**
     * Not configurable in this mod; used for dayIndex calculation everywhere.
     */
    public static final int TICKS_PER_DAY = 24000;

    // ---------------------------------------------------------------------
    // Spec + entries
    // ---------------------------------------------------------------------

    public static final ModConfigSpec SERVER_SPEC;

    // calendar
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MONTH_NAMES;
    public static final ModConfigSpec.ConfigValue<String> YEAR_SUFFIX;
    public static final ModConfigSpec.IntValue DAYS_PER_MONTH;
    public static final ModConfigSpec.BooleanValue USE_CUSTOM_FONT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        // -----------------------
        // calendar
        // -----------------------
        builder.push("calendar");

        MONTH_NAMES = builder
                .comment(
                        "Names of the calendar months, in order.",
                        "Any number of months is allowed (>= 1).",
                        "If the list is missing or empty, the mod will fall back to its built-in defaults."
                )
                .defineList(
                        "monthNames",
                        Arrays.asList(DEFAULT_MONTH_NAMES),
                        o -> (o instanceof String s) && !s.isBlank()
                );

        YEAR_SUFFIX = builder
                .comment(
                        "Year suffix string, e.g. \"A.N.\" for \"After Notch\".",
                        "Used when displaying dates like: Day 17 of Dawnroot, 112 A.N."
                )
                .define("yearSuffix", DEFAULT_YEAR_SUFFIX);

        DAYS_PER_MONTH = builder
                .comment(
                        "How many in-game days each month lasts before progressing to the next month.",
                        "This is server-authoritative and synced to clients.",
                        "",
                        "Examples:",
                        "- Vanilla-ish fantasy default: 28",
                        "- Perfect sync with Serene Seasons sub_season_duration=16: set this to 24",
                        "",
                        "Valid range: 1..365"
                )
                .defineInRange("daysPerMonth", DEFAULT_DAYS_PER_MONTH, 1, 365);

        USE_CUSTOM_FONT = builder
                .comment(
                        "If true, use the RPG Calendar custom font for the day popup.",
                        "If false, use Minecraft's default font.",
                        "",
                        "This is server-authoritative and hot-reloadable."
                )
                .define("useCustomFont", DEFAULT_USE_CUSTOM_FONT);

        builder.pop();

        SERVER_SPEC = builder.build();

        LOG.debug("[RPGCalendarConfig] Built SERVER config spec (calendar)");
    }

    // ---------------------------------------------------------------------
    // Registration (NeoForge idiom)
    // ---------------------------------------------------------------------

    public static void register() {
        try {
            ModLoadingContext.get()
                    .getActiveContainer()
                    .registerConfig(ModConfig.Type.SERVER, SERVER_SPEC);

            LOG.debug("[RPGCalendarConfig] Registered SERVER config with active ModContainer");
        } catch (Throwable t) {
            LOG.error("[RPGCalendarConfig] Failed to register SERVER config", t);
        }
    }

    // ---------------------------------------------------------------------
    // Safe accessors (server-side use; client reads synced values)
    // ---------------------------------------------------------------------

    public static String getMonthName(int index) {
        try {
            List<String> names = getMonthNamesList();
            if (names.isEmpty()) {
                LOG.warn("[RPGCalendarConfig] getMonthName: month list empty, using placeholder");
                return "Month" + index;
            }

            int clamped = index;
            if (clamped < 0 || clamped >= names.size()) {
                LOG.warn("[RPGCalendarConfig] getMonthName: index {} out of range (0..{}); clamping", index, names.size() - 1);
                clamped = Math.max(0, Math.min(clamped, names.size() - 1));
            }

            String value = names.get(clamped);
            if (value == null || value.isBlank()) {
                LOG.warn("[RPGCalendarConfig] monthNames[{}] is blank/null, using placeholder", clamped);
                return "Month" + (clamped + 1);
            }

            return value;
        } catch (Throwable t) {
            LOG.error("[RPGCalendarConfig] getMonthName failed for index {}, using safe fallback", index, t);
            return "Month" + index;
        }
    }

    public static String getYearSuffix() {
        try {
            String suffix = YEAR_SUFFIX.get();
            if (suffix == null || suffix.isBlank()) {
                LOG.warn("[RPGCalendarConfig] yearSuffix is blank/null, using default '{}'", DEFAULT_YEAR_SUFFIX);
                return DEFAULT_YEAR_SUFFIX;
            }
            return suffix;
        } catch (Throwable t) {
            LOG.error("[RPGCalendarConfig] getYearSuffix failed, using default '{}'", DEFAULT_YEAR_SUFFIX, t);
            return DEFAULT_YEAR_SUFFIX;
        }
    }

    public static int getDaysPerMonth() {
        try {
            int v = DAYS_PER_MONTH.get();
            if (v <= 0) {
                LOG.warn("[RPGCalendarConfig] daysPerMonth <= 0 ({}), using default {}", v, DEFAULT_DAYS_PER_MONTH);
                return DEFAULT_DAYS_PER_MONTH;
            }
            if (v > 365) {
                LOG.warn("[RPGCalendarConfig] daysPerMonth > 365 ({}), clamping to 365", v);
                return 365;
            }
            return v;
        } catch (Throwable t) {
            LOG.error("[RPGCalendarConfig] getDaysPerMonth failed, using default {}", DEFAULT_DAYS_PER_MONTH, t);
            return DEFAULT_DAYS_PER_MONTH;
        }
    }

    public static boolean getUseCustomFont() {
        try {
            return USE_CUSTOM_FONT.get();
        } catch (Throwable t) {
            LOG.error("[RPGCalendarConfig] getUseCustomFont failed, using default {}", DEFAULT_USE_CUSTOM_FONT, t);
            return DEFAULT_USE_CUSTOM_FONT;
        }
    }

    public static CalendarDefinition getCalendarDefinition() {
        try {
            List<String> monthNamesList = getMonthNamesList();
            String suffix = getYearSuffix();
            int daysPerMonth = getDaysPerMonth();

            String[] namesArray = monthNamesList.toArray(new String[0]);

            CalendarDefinition def = new CalendarDefinition(
                    namesArray,
                    suffix,
                    daysPerMonth,
                    (long) TICKS_PER_DAY
            );

            LOG.debug(
                    "[RPGCalendarConfig] Built CalendarDefinition: months={}, daysPerMonth={}, suffix='{}', ticksPerDay={}",
                    namesArray.length,
                    daysPerMonth,
                    suffix,
                    TICKS_PER_DAY
            );

            return def;
        } catch (Throwable t) {
            LOG.error("[RPGCalendarConfig] getCalendarDefinition failed; falling back to hard-coded defaults", t);

            return new CalendarDefinition(
                    DEFAULT_MONTH_NAMES,
                    DEFAULT_YEAR_SUFFIX,
                    DEFAULT_DAYS_PER_MONTH,
                    (long) TICKS_PER_DAY
            );
        }
    }

    public static List<String> getMonthNamesList() {
        try {
            List<? extends String> names = MONTH_NAMES.get();
            if (names == null || names.isEmpty()) {
                LOG.warn("[RPGCalendarConfig] monthNames config missing/empty; using defaults");
                return Arrays.asList(DEFAULT_MONTH_NAMES);
            }

            List<String> cleaned = new ArrayList<>(names.size());
            for (int i = 0; i < names.size(); i++) {
                String raw = names.get(i);
                if (raw == null || raw.isBlank()) {
                    LOG.warn("[RPGCalendarConfig] monthNames[{}] is blank/null, using placeholder", i);
                    cleaned.add("Month" + (i + 1));
                } else {
                    cleaned.add(raw);
                }
            }

            if (cleaned.isEmpty()) {
                LOG.warn("[RPGCalendarConfig] monthNames resolved empty; using defaults");
                return Arrays.asList(DEFAULT_MONTH_NAMES);
            }

            return cleaned;
        } catch (Throwable t) {
            LOG.error("[RPGCalendarConfig] getMonthNamesList failed; using defaults", t);
            return Arrays.asList(DEFAULT_MONTH_NAMES);
        }
    }

    private RPGCalendarConfig() {
        // no-op
    }
}
