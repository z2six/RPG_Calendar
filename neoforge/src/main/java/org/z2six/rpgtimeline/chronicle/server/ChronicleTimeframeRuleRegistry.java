package org.z2six.rpgtimeline.chronicle.server;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.z2six.rpgtimeline.config.RPGTimelineConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

final class ChronicleTimeframeRuleRegistry {

    private static final Logger LOG = LogUtils.getLogger();
    private static volatile int cachedHash = 0;
    private static volatile List<ChronicleTimeframeRule> cachedRules = List.of();

    private ChronicleTimeframeRuleRegistry() {
        // no-op
    }

    static List<ChronicleTimeframeRule> getRules() {
        List<String> raw = RPGTimelineConfig.getEventTimeframeEntries();
        int hash = raw.hashCode();
        if (hash == cachedHash) {
            return cachedRules;
        }

        List<ChronicleTimeframeRule> parsed = new ArrayList<>();
        int order = 0;
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            ChronicleTimeframeRule rule = parseEntry(entry, order++);
            if (rule != null) {
                parsed.add(rule);
            }
        }

        cachedRules = Collections.unmodifiableList(parsed);
        cachedHash = hash;

        if (LOG.isDebugEnabled()) {
            LOG.debug("[ChronicleTimeframeRuleRegistry] Loaded {} timeframe rules", cachedRules.size());
        }

        return cachedRules;
    }

    private static ChronicleTimeframeRule parseEntry(String entry, int order) {
        Map<String, String> pairs = new HashMap<>();
        String[] tokens = entry.split(";");
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            String[] kv = token.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = kv[0].trim().toLowerCase(Locale.ROOT);
            String value = kv[1].trim();
            if (!key.isEmpty()) {
                pairs.put(key, value);
            }
        }

        ChronicleTimeframeRule.RenderMode renderMode = parseRenderMode(pairs.get("render"));
        if (renderMode == null) {
            LOG.warn("[ChronicleTimeframeRuleRegistry] Unsupported render mode in rule: {}", entry);
            return null;
        }
        ChronicleTimeframeRule.Layer layer = parseLayer(pairs.get("layer"), renderMode);
        if (layer == null) {
            LOG.warn("[ChronicleTimeframeRuleRegistry] Unsupported layer in rule: {}", entry);
            return null;
        }
        ChronicleTimeframeRule.TargetMode targetMode = parseTargetMode(pairs.get("targetmode"));
        boolean useTarget = parseBoolean(pairs.getOrDefault("usetarget", pairs.getOrDefault("use_target", "false")));
        boolean useIcon = parseBoolean(pairs.getOrDefault("useicon", pairs.getOrDefault("use_icon", "false")));

        String renderId = "";
        if (renderMode == ChronicleTimeframeRule.RenderMode.TILE) {
            renderId = pairs.getOrDefault("texture", "");
        } else if (renderMode == ChronicleTimeframeRule.RenderMode.ENTITY) {
            renderId = pairs.getOrDefault("entity", pairs.getOrDefault("texture", ""));
        }
        if (renderMode == ChronicleTimeframeRule.RenderMode.TILE && renderId.contains("/item/")) {
            LOG.warn("[ChronicleTimeframeRuleRegistry] Item textures are not supported for timeframes: {}", entry);
            return null;
        }

        float days = parseFloat(pairs.getOrDefault("days", "3"), 3.0f);
        if (days <= 0.0f) {
            days = 1.0f;
        }

        int priority = parseInt(pairs.getOrDefault("priority", "0"), 0);
        float alpha = parseFloat(pairs.getOrDefault("alpha", "0.25"), 0.25f);
        alpha = clamp(alpha, 0.0f, 1.0f);

        ChronicleTimeframeRule.MatchType matchType = parseMatchType(pairs.get("type"));
        Boolean worldFirst = parseBooleanNullable(pairs.get("worldfirst"));
        if (worldFirst == null) {
            worldFirst = parseBooleanNullable(pairs.get("world_first"));
        }

        String id = pairs.getOrDefault("id", "");
        String idPattern = pairs.getOrDefault("idpattern", pairs.getOrDefault("pattern", ""));
        String namespace = pairs.getOrDefault("namespace", pairs.getOrDefault("mod", ""));
        String frameType = pairs.getOrDefault("frame", "");
        List<String> keywords = parseKeywords(pairs.getOrDefault("keywords", ""));

        ChronicleGoalType goalType = parseGoalType(pairs.get("goaltype"));
        ResourceLocation goalTarget = parseResource(pairs.getOrDefault("target", ""));
        Integer minCount = parseIntNullable(pairs.get("mincount"));
        Integer maxCount = parseIntNullable(pairs.get("maxcount"));

        if ((renderId == null || renderId.isBlank())
                && !useTarget
                && !useIcon
                && renderMode != ChronicleTimeframeRule.RenderMode.ENTITY) {
            LOG.warn("[ChronicleTimeframeRuleRegistry] Skipping timeframe rule without render id: {}", entry);
            return null;
        }

        return new ChronicleTimeframeRule(
                priority,
                days,
                alpha,
                layer,
                renderMode,
                renderId,
                useTarget,
                useIcon,
                targetMode,
                matchType,
                worldFirst,
                id,
                idPattern,
                namespace,
                keywords,
                frameType,
                goalType,
                goalTarget,
                minCount,
                maxCount,
                order
        );
    }

    private static ChronicleTimeframeRule.MatchType parseMatchType(String raw) {
        if (raw == null || raw.isBlank()) {
            return ChronicleTimeframeRule.MatchType.ANY;
        }
        String norm = raw.trim().toLowerCase(Locale.ROOT);
        return switch (norm) {
            case "note", "admin", "admin_note" -> ChronicleTimeframeRule.MatchType.NOTE;
            case "custom", "custom_goal" -> ChronicleTimeframeRule.MatchType.CUSTOM;
            case "adv", "advancement" -> ChronicleTimeframeRule.MatchType.ADVANCEMENT;
            case "any", "*" -> ChronicleTimeframeRule.MatchType.ANY;
            default -> ChronicleTimeframeRule.MatchType.ANY;
        };
    }

    private static ChronicleGoalType parseGoalType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String norm = raw.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        return switch (norm) {
            case "BREAK", "BREAK_BLOCK", "BREAKBLOCK" -> ChronicleGoalType.BREAK_BLOCK;
            case "KILL", "KILL_MOB", "KILLMOB" -> ChronicleGoalType.KILL_MOB;
            case "GET", "GET_ITEM", "GETITEM", "OBTAIN", "OBTAIN_ITEM" -> ChronicleGoalType.GET_ITEM;
            default -> null;
        };
    }

    private static ChronicleTimeframeRule.RenderMode parseRenderMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return ChronicleTimeframeRule.RenderMode.TILE;
        }
        String norm = raw.trim().toLowerCase(Locale.ROOT);
        return switch (norm) {
            case "item", "icon" -> null;
            case "entity", "mob" -> ChronicleTimeframeRule.RenderMode.ENTITY;
            default -> ChronicleTimeframeRule.RenderMode.TILE;
        };
    }

    private static ChronicleTimeframeRule.Layer parseLayer(String raw, ChronicleTimeframeRule.RenderMode renderMode) {
        if (raw == null || raw.isBlank()) {
            return defaultLayer(renderMode);
        }
        String norm = raw.trim().toLowerCase(Locale.ROOT);
        return switch (norm) {
            case "item", "items" -> null;
            case "entity", "mob" -> ChronicleTimeframeRule.Layer.ENTITY;
            case "overlay", "top" -> renderMode == ChronicleTimeframeRule.RenderMode.ENTITY
                    ? ChronicleTimeframeRule.Layer.ENTITY
                    : ChronicleTimeframeRule.Layer.BASE;
            case "texture", "tile", "base" -> ChronicleTimeframeRule.Layer.BASE;
            default -> defaultLayer(renderMode);
        };
    }

    private static ChronicleTimeframeRule.Layer defaultLayer(ChronicleTimeframeRule.RenderMode renderMode) {
        if (renderMode == ChronicleTimeframeRule.RenderMode.ENTITY) {
            return ChronicleTimeframeRule.Layer.ENTITY;
        }
        return ChronicleTimeframeRule.Layer.BASE;
    }

    private static ChronicleTimeframeRule.TargetMode parseTargetMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return ChronicleTimeframeRule.TargetMode.AUTO;
        }
        String norm = raw.trim().toLowerCase(Locale.ROOT);
        return switch (norm) {
            case "block" -> ChronicleTimeframeRule.TargetMode.BLOCK;
            case "item" -> ChronicleTimeframeRule.TargetMode.ITEM;
            case "entity", "mob" -> ChronicleTimeframeRule.TargetMode.ENTITY;
            default -> ChronicleTimeframeRule.TargetMode.AUTO;
        };
    }

    private static boolean parseBoolean(String raw) {
        Boolean value = parseBooleanNullable(raw);
        return value != null && value;
    }

    private static List<String> parseKeywords(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String[] parts = raw.split("[,|]");
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            out.add(part.trim().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static ResourceLocation parseResource(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return ResourceLocation.tryParse(raw.trim());
    }

    private static Boolean parseBooleanNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String norm = raw.trim().toLowerCase(Locale.ROOT);
        if (norm.equals("true") || norm.equals("yes") || norm.equals("1")) {
            return Boolean.TRUE;
        }
        if (norm.equals("false") || norm.equals("no") || norm.equals("0")) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static Integer parseIntNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static float parseFloat(String raw, float fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Float.parseFloat(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
