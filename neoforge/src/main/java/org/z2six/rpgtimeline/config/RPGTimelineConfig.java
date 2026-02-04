package org.z2six.rpgtimeline.config;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.slf4j.Logger;
import org.z2six.rpgtimeline.calendar.CalendarDefinition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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
public final class RPGTimelineConfig {

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

    public static final List<String> DEFAULT_MONTH_ABBREVIATIONS = List.of();

    public static final List<String> DEFAULT_CUSTOM_GOALS = List.of(
            "break_block|minecraft:stone|1&100&1000&10000",
            "break_block|minecraft:oak_log|1&100&1000&10000",
            "break_block|minecraft:coal_ore|1&100&1000&10000",
            "break_block|minecraft:iron_ore|1&100&1000&10000",
            "break_block|minecraft:diamond_ore|1&100&1000&10000",
            "kill_mob|minecraft:zombie|1&100&1000&10000",
            "kill_mob|minecraft:skeleton|1&100&1000&10000",
            "kill_mob|minecraft:creeper|1&100&1000&10000",
            "kill_mob|minecraft:enderman|1&100&1000&10000",
            "get_item|minecraft:iron_ingot|1&100&1000&10000",
            "get_item|minecraft:diamond|1&100&1000&10000",
            "get_item|minecraft:ender_pearl|1&100&1000&10000"
    );
    public static final String DEFAULT_CUSTOM_GOALS_TEXT = String.join("\n", DEFAULT_CUSTOM_GOALS);

    public static final List<String> DEFAULT_EVENT_TIMEFRAMES = List.of(
            "# Entity overrides (render 3D models instead of category background)",
            "priority=100;days=4;alpha=0.45;layer=entity;render=entity;type=advancement;id=minecraft:end/kill_dragon;entity=minecraft:ender_dragon",
            "priority=95;days=4;alpha=0.45;layer=entity;render=entity;type=advancement;id=minecraft:nether/summon_wither;entity=minecraft:wither",
            "priority=90;days=3;alpha=0.45;layer=entity;render=entity;type=advancement;id=minecraft:nether/get_wither_skull;entity=minecraft:wither_skeleton",
            "priority=85;days=3;alpha=0.45;layer=entity;render=entity;type=advancement;keywords=elder guardian;entity=minecraft:elder_guardian",
            "priority=80;days=3;alpha=0.45;layer=entity;render=entity;type=advancement;keywords=raid;entity=minecraft:ravager",
            "priority=75;days=3;alpha=0.45;layer=entity;render=entity;type=advancement;keywords=warden;entity=minecraft:warden"
    );
    public static final String DEFAULT_EVENT_TIMEFRAMES_TEXT = String.join("\n", DEFAULT_EVENT_TIMEFRAMES);

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
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MONTH_ABBREVIATIONS;
    public static final ModConfigSpec.ConfigValue<String> YEAR_SUFFIX;
    public static final ModConfigSpec.IntValue DAYS_PER_MONTH;
    public static final ModConfigSpec.BooleanValue USE_CUSTOM_FONT;
    public static final ModConfigSpec.ConfigValue<String> CUSTOM_GOALS;
    public static final ModConfigSpec.ConfigValue<String> EVENT_TIMEFRAMES;

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

        MONTH_ABBREVIATIONS = builder
                .comment(
                        "Optional month abbreviations to use in compact timeline labels.",
                        "Each entry must match an existing month name and provide its abbreviation.",
                        "Format: MonthName=Abbrev",
                        "Examples:",
                        "Dawnroot=Daw",
                        "Shadowmere=Shd",
                        "If empty or invalid, the mod falls back to the first 3 letters."
                )
                .defineList(
                        "monthAbbreviations",
                        DEFAULT_MONTH_ABBREVIATIONS,
                        o -> (o instanceof String s)
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
                        "- Perfect sync with Serene Seasons:",
                        "  1) Set Serene Seasons config sub_season_duration = 16",
                        "  2) Set daysPerMonth below to 24",
                        "",
                        "Valid range: 1..365"
                )
                .defineInRange("daysPerMonth", DEFAULT_DAYS_PER_MONTH, 1, 365);

        USE_CUSTOM_FONT = builder
                .comment(
                        "If true, use the RPG Timeline custom font for the day popup.",
                        "If false, use Minecraft's default font.",
                        "",
                        "This is server-authoritative and hot-reloadable."
                )
                .define("useCustomFont", DEFAULT_USE_CUSTOM_FONT);

        builder.pop();

        // -----------------------
        // chronicle
        // -----------------------
        builder.push("chronicle");

        CUSTOM_GOALS = builder
                .comment(
                        "Custom chronicle goals (server-authoritative, hot-reloadable).",
                        "Format: type|target|count|title|description|icon",
                        "type: break_block, kill_mob, get_item",
                        "target: registry id (block/item/mob), e.g. minecraft:stone",
                        "count: number required (optional, defaults to 1). Multiple values can be separated with '&'.",
                        "title/description/icon: optional overrides (icon is an item id)",
                        "Each line is one entry.",
                        "Examples:",
                        "break_block|minecraft:stone|100|Stone Miner|Broke 100 stone blocks.|minecraft:stone",
                        "kill_mob|minecraft:zombie|20|Zombie Exterminator|Killed 20 zombies.|minecraft:iron_sword",
                        "get_item|minecraft:diamond|5|Diamond Cache|Obtained 5 diamonds.|minecraft:diamond"
                )
                .define("customGoals", DEFAULT_CUSTOM_GOALS_TEXT);

        EVENT_TIMEFRAMES = builder
                .comment(
                        "Chronicle timeline entity overrides (server-authoritative, hot-reloadable).",
                        "Each line is a semicolon-separated list of key=value pairs.",
                        "Required keys: days and render=entity.",
                        "Keys:",
                        " - priority: higher wins when multiple rules match",
                        " - days: total span width in days",
                        " - alpha: 0..1 opacity",
                        " - layer: entity",
                        " - render: entity",
                        " - entity: entity id for entity render",
                        " - useTarget: true/false (use goal target as render id)",
                        " - type: advancement, custom, any",
                        " - worldFirst: true/false (advancement only)",
                        " - id: exact advancement id match",
                        " - idPattern: glob pattern for advancement ids, e.g. minecraft:nether/*",
                        " - namespace: glob pattern for namespace, e.g. minecraft or *",
                        " - keywords: comma-separated list of keywords to match title/details/id",
                        " - goalType: break_block, kill_mob, get_item (custom goals)",
                        " - target: registry id (custom goals)",
                        " - minCount/maxCount: numeric thresholds (custom goals)",
                        "",
                        "Examples:",
                        "priority=100;days=4;layer=entity;render=entity;type=advancement;id=minecraft:end/kill_dragon;entity=minecraft:ender_dragon",
                        "priority=30;days=3;layer=entity;render=entity;useTarget=true;type=custom;goalType=kill_mob;target=minecraft:zombie"
                )
                .define("eventTimeframes", DEFAULT_EVENT_TIMEFRAMES_TEXT);

        builder.pop();

        SERVER_SPEC = builder.build();

        LOG.debug("[RPGTimelineConfig] Built SERVER config spec (calendar)");
    }

    // ---------------------------------------------------------------------
    // Registration (NeoForge idiom)
    // ---------------------------------------------------------------------

    public static void register() {
        try {
            ModLoadingContext.get()
                    .getActiveContainer()
                    .registerConfig(ModConfig.Type.SERVER, SERVER_SPEC);

            LOG.debug("[RPGTimelineConfig] Registered SERVER config with active ModContainer");
        } catch (Throwable t) {
            LOG.error("[RPGTimelineConfig] Failed to register SERVER config", t);
        }
    }

    // ---------------------------------------------------------------------
    // Safe accessors (server-side use; client reads synced values)
    // ---------------------------------------------------------------------

    public static String getMonthName(int index) {
        try {
            List<String> names = getMonthNamesList();
            if (names.isEmpty()) {
                LOG.warn("[RPGTimelineConfig] getMonthName: month list empty, using placeholder");
                return "Month" + index;
            }

            int clamped = index;
            if (clamped < 0 || clamped >= names.size()) {
                LOG.warn("[RPGTimelineConfig] getMonthName: index {} out of range (0..{}); clamping", index, names.size() - 1);
                clamped = Math.max(0, Math.min(clamped, names.size() - 1));
            }

            String value = names.get(clamped);
            if (value == null || value.isBlank()) {
                LOG.warn("[RPGTimelineConfig] monthNames[{}] is blank/null, using placeholder", clamped);
                return "Month" + (clamped + 1);
            }

            return value;
        } catch (Throwable t) {
            LOG.error("[RPGTimelineConfig] getMonthName failed for index {}, using safe fallback", index, t);
            return "Month" + index;
        }
    }

    public static String getYearSuffix() {
        try {
            String suffix = YEAR_SUFFIX.get();
            if (suffix == null || suffix.isBlank()) {
                LOG.warn("[RPGTimelineConfig] yearSuffix is blank/null, using default '{}'", DEFAULT_YEAR_SUFFIX);
                return DEFAULT_YEAR_SUFFIX;
            }
            return suffix;
        } catch (Throwable t) {
            LOG.error("[RPGTimelineConfig] getYearSuffix failed, using default '{}'", DEFAULT_YEAR_SUFFIX, t);
            return DEFAULT_YEAR_SUFFIX;
        }
    }

    public static int getDaysPerMonth() {
        try {
            int v = DAYS_PER_MONTH.get();
            if (v <= 0) {
                LOG.warn("[RPGTimelineConfig] daysPerMonth <= 0 ({}), using default {}", v, DEFAULT_DAYS_PER_MONTH);
                return DEFAULT_DAYS_PER_MONTH;
            }
            if (v > 365) {
                LOG.warn("[RPGTimelineConfig] daysPerMonth > 365 ({}), clamping to 365", v);
                return 365;
            }
            return v;
        } catch (Throwable t) {
            LOG.error("[RPGTimelineConfig] getDaysPerMonth failed, using default {}", DEFAULT_DAYS_PER_MONTH, t);
            return DEFAULT_DAYS_PER_MONTH;
        }
    }

    public static boolean getUseCustomFont() {
        try {
            return USE_CUSTOM_FONT.get();
        } catch (Throwable t) {
            LOG.error("[RPGTimelineConfig] getUseCustomFont failed, using default {}", DEFAULT_USE_CUSTOM_FONT, t);
            return DEFAULT_USE_CUSTOM_FONT;
        }
    }

    public static CalendarDefinition getCalendarDefinition() {
        try {
            List<String> monthNamesList = getMonthNamesList();
            List<String> monthAbbreviationsList = getMonthAbbreviationsList();
            String suffix = getYearSuffix();
            int daysPerMonth = getDaysPerMonth();

            String[] namesArray = monthNamesList.toArray(new String[0]);
            String[] abbrevArray = monthAbbreviationsList.toArray(new String[0]);

            CalendarDefinition def = new CalendarDefinition(
                    namesArray,
                    abbrevArray,
                    suffix,
                    daysPerMonth,
                    (long) TICKS_PER_DAY
            );

            LOG.debug(
                    "[RPGTimelineConfig] Built CalendarDefinition: months={}, daysPerMonth={}, suffix='{}', ticksPerDay={}",
                    namesArray.length,
                    daysPerMonth,
                    suffix,
                    TICKS_PER_DAY
            );

            return def;
        } catch (Throwable t) {
            LOG.error("[RPGTimelineConfig] getCalendarDefinition failed; falling back to hard-coded defaults", t);

            return new CalendarDefinition(
                    DEFAULT_MONTH_NAMES,
                    CalendarDefinition.defaultDefinition().getMonthAbbreviations(),
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
                LOG.warn("[RPGTimelineConfig] monthNames config missing/empty; using defaults");
                return Arrays.asList(DEFAULT_MONTH_NAMES);
            }

            List<String> cleaned = new ArrayList<>(names.size());
            for (int i = 0; i < names.size(); i++) {
                String raw = names.get(i);
                if (raw == null || raw.isBlank()) {
                    LOG.warn("[RPGTimelineConfig] monthNames[{}] is blank/null, using placeholder", i);
                    cleaned.add("Month" + (i + 1));
                } else {
                    cleaned.add(raw);
                }
            }

            if (cleaned.isEmpty()) {
                LOG.warn("[RPGTimelineConfig] monthNames resolved empty; using defaults");
                return Arrays.asList(DEFAULT_MONTH_NAMES);
            }

            return cleaned;
        } catch (Throwable t) {
            LOG.error("[RPGTimelineConfig] getMonthNamesList failed; using defaults", t);
            return Arrays.asList(DEFAULT_MONTH_NAMES);
        }
    }

    public static List<String> getMonthAbbreviationsList() {
        try {
            List<String> monthNames = getMonthNamesList();
            List<? extends String> raw = MONTH_ABBREVIATIONS.get();
            java.util.Map<String, String> map = new java.util.HashMap<>();
            if (raw != null) {
                for (String entry : raw) {
                    if (entry == null) {
                        continue;
                    }
                    String trimmed = entry.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    String[] parts = trimmed.split("=", 2);
                    if (parts.length != 2) {
                        LOG.warn("[RPGTimelineConfig] monthAbbreviations entry '{}' is invalid; expected Month=Abbrev", trimmed);
                        continue;
                    }
                    String name = parts[0].trim();
                    String abbr = parts[1].trim();
                    if (name.isEmpty() || abbr.isEmpty()) {
                        LOG.warn("[RPGTimelineConfig] monthAbbreviations entry '{}' is invalid; blank name/abbrev", trimmed);
                        continue;
                    }
                    map.put(normalizeMonthKey(name), abbr);
                }
            }

            List<String> resolved = new ArrayList<>(monthNames.size());
            for (String monthName : monthNames) {
                String key = normalizeMonthKey(monthName);
                String abbr = map.get(key);
                if (abbr == null || abbr.isBlank()) {
                    resolved.add(fallbackAbbreviation(monthName));
                } else {
                    resolved.add(abbr);
                }
            }
            return resolved;
        } catch (Throwable t) {
            LOG.error("[RPGTimelineConfig] getMonthAbbreviationsList failed; using fallbacks", t);
            List<String> monthNames = getMonthNamesList();
            List<String> resolved = new ArrayList<>(monthNames.size());
            for (String name : monthNames) {
                resolved.add(fallbackAbbreviation(name));
            }
            return resolved;
        }
    }

    private static String normalizeMonthKey(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String fallbackAbbreviation(String value) {
        if (value == null || value.isBlank()) {
            return "Mon";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 3 ? trimmed : trimmed.substring(0, 3);
    }

    public static List<String> getCustomGoalEntries() {
        try {
            String raw = CUSTOM_GOALS.get();
            if (raw == null || raw.isBlank()) {
                return DEFAULT_CUSTOM_GOALS;
            }
            String[] lines = raw.split("\\r?\\n");
            List<String> cleaned = new ArrayList<>(lines.length);
            for (String line : lines) {
                if (line == null) {
                    continue;
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                cleaned.add(trimmed);
            }
            return cleaned.isEmpty() ? DEFAULT_CUSTOM_GOALS : cleaned;
        } catch (Throwable t) {
            LOG.error("[RPGTimelineConfig] getCustomGoalEntries failed; using defaults", t);
            return DEFAULT_CUSTOM_GOALS;
        }
    }

    public static List<String> getEventTimeframeEntries() {
        try {
            String raw = EVENT_TIMEFRAMES.get();
            if (raw == null || raw.isBlank()) {
                return DEFAULT_EVENT_TIMEFRAMES;
            }
            String[] lines = raw.split("\\r?\\n");
            List<String> cleaned = new ArrayList<>(lines.length);
            for (String line : lines) {
                if (line == null) {
                    continue;
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                cleaned.add(trimmed);
            }
            return cleaned.isEmpty() ? DEFAULT_EVENT_TIMEFRAMES : cleaned;
        } catch (Throwable t) {
            LOG.error("[RPGTimelineConfig] getEventTimeframeEntries failed; using defaults", t);
            return DEFAULT_EVENT_TIMEFRAMES;
        }
    }

    private RPGTimelineConfig() {
        // no-op
    }
}
