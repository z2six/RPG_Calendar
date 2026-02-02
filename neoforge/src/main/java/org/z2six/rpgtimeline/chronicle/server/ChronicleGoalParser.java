package org.z2six.rpgtimeline.chronicle.server;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.Locale;

final class ChronicleGoalParser {

    private static final Logger LOG = LogUtils.getLogger();

    private ChronicleGoalParser() {
        // no-op
    }

    static java.util.List<ChronicleGoalDefinition> parseAll(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.util.List.of();
        }

        String[] parts = raw.split("\\|", -1);
        if (parts.length < 2) {
            LOG.warn("[ChronicleGoalParser] Skipping goal entry (need type|target): '{}'", raw);
            return java.util.List.of();
        }

        ChronicleGoalType type = parseType(parts[0]);
        if (type == null) {
            LOG.warn("[ChronicleGoalParser] Skipping goal entry (bad type): '{}'", raw);
            return java.util.List.of();
        }

        ResourceLocation target = ResourceLocation.tryParse(parts[1].trim());
        if (target == null) {
            LOG.warn("[ChronicleGoalParser] Skipping goal entry (bad target): '{}'", raw);
            return java.util.List.of();
        }

        java.util.List<Integer> counts = parseCounts(parts.length >= 3 ? parts[2] : "");
        if (counts.isEmpty()) {
            counts = java.util.List.of(1);
        }

        String titleRaw = parts.length >= 4 ? parts[3].trim() : "";
        String descriptionRaw = parts.length >= 5 ? parts[4].trim() : "";
        String iconRaw = parts.length >= 6 ? parts[5].trim() : "";

        java.util.List<ChronicleGoalDefinition> results = new java.util.ArrayList<>();
        for (Integer count : counts) {
            if (count == null || count <= 0) {
                continue;
            }
            String title = titleRaw;
            String description = descriptionRaw;
            String icon = iconRaw;

            if (title.isBlank()) {
                title = defaultTitle(type, target, count);
            } else {
                title = applyCountTokens(title, count);
            }

            if (description.isBlank()) {
                description = defaultDescription(type, target, count);
            } else {
                description = applyCountTokens(description, count);
            }

            if (icon.isBlank()) {
                icon = defaultIcon(type, target);
            }

            String id = buildId(type, target, count);
            results.add(new ChronicleGoalDefinition(id, type, target, count, title, description, icon));
        }

        return results;
    }

    private static ChronicleGoalType parseType(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        return switch (normalized) {
            case "BREAK", "BREAK_BLOCK", "BREAKBLOCK" -> ChronicleGoalType.BREAK_BLOCK;
            case "KILL", "KILL_MOB", "KILLMOB" -> ChronicleGoalType.KILL_MOB;
            case "GET", "GET_ITEM", "GETITEM", "OBTAIN", "OBTAIN_ITEM" -> ChronicleGoalType.GET_ITEM;
            default -> null;
        };
    }

    private static Integer parseInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static java.util.List<Integer> parseCounts(String raw) {
        if (raw == null || raw.isBlank()) {
            return java.util.List.of();
        }
        String[] parts = raw.split("[&,]");
        java.util.LinkedHashSet<Integer> values = new java.util.LinkedHashSet<>();
        for (String part : parts) {
            Integer parsed = parseInt(part);
            if (parsed != null && parsed > 0) {
                values.add(parsed);
            }
        }
        return new java.util.ArrayList<>(values);
    }

    private static String applyCountTokens(String raw, int count) {
        String replaced = raw.replace("{count}", Integer.toString(count));
        return replaced.replace("{count_x}", count + "x");
    }

    private static String buildId(ChronicleGoalType type, ResourceLocation target, int count) {
        return type.name().toLowerCase(Locale.ROOT) + ":" + target + ":" + count;
    }

    private static String defaultTitle(ChronicleGoalType type, ResourceLocation target, int count) {
        String name = formatTargetName(target);
        if (count <= 1) {
            return switch (type) {
                case BREAK_BLOCK -> "Broke " + name;
                case KILL_MOB -> "Killed " + name;
                case GET_ITEM -> "Obtained " + name;
            };
        }
        return switch (type) {
            case BREAK_BLOCK -> "Broke " + count + " " + name;
            case KILL_MOB -> "Killed " + count + " " + pluralize(name);
            case GET_ITEM -> "Obtained " + count + " " + pluralize(name);
        };
    }

    private static String defaultDescription(ChronicleGoalType type, ResourceLocation target, int count) {
        String name = formatTargetName(target);
        if (count <= 1) {
            return switch (type) {
                case BREAK_BLOCK -> "Broke a " + name.toLowerCase(Locale.ROOT) + ".";
                case KILL_MOB -> "Defeated a " + name.toLowerCase(Locale.ROOT) + ".";
                case GET_ITEM -> "Obtained a " + name.toLowerCase(Locale.ROOT) + ".";
            };
        }
        return switch (type) {
            case BREAK_BLOCK -> "Broke " + count + " " + name.toLowerCase(Locale.ROOT) + ".";
            case KILL_MOB -> "Defeated " + count + " " + pluralize(name.toLowerCase(Locale.ROOT)) + ".";
            case GET_ITEM -> "Obtained " + count + " " + pluralize(name.toLowerCase(Locale.ROOT)) + ".";
        };
    }

    private static String defaultIcon(ChronicleGoalType type, ResourceLocation target) {
        if (type == ChronicleGoalType.KILL_MOB) {
            return "minecraft:bone";
        }
        return target.toString();
    }

    private static String formatTargetName(ResourceLocation id) {
        String path = id.getPath().replace('_', ' ');
        String[] parts = path.split(" ");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            out.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1))
                    .append(' ');
        }
        return out.toString().trim();
    }

    private static String pluralize(String name) {
        if (name.endsWith("s")) {
            return name;
        }
        return name + "s";
    }
}
