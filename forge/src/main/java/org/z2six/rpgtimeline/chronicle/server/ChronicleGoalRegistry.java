package org.z2six.rpgtimeline.chronicle.server;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.z2six.rpgtimeline.config.RPGTimelineConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ChronicleGoalRegistry {

    public static final String CUSTOM_SOURCE_PREFIX = "custom:";

    private static final Logger LOG = LogUtils.getLogger();
    private static volatile int cachedHash = 0;
    private static volatile List<ChronicleGoalDefinition> cachedGoals = List.of();
    private static volatile Map<String, List<ChronicleGoalDefinition>> cachedByKey = Map.of();
    private static volatile Set<String> cachedGoalIds = Set.of();

    private ChronicleGoalRegistry() {
        // no-op
    }

    public static List<ChronicleGoalDefinition> getGoals() {
        List<String> raw = RPGTimelineConfig.getCustomGoalEntries();
        int hash = raw.hashCode();
        if (hash == cachedHash) {
            return cachedGoals;
        }

        List<ChronicleGoalDefinition> parsed = new ArrayList<>();
        Map<String, List<ChronicleGoalDefinition>> byKey = new HashMap<>();
        Set<String> ids = new HashSet<>();

        for (String entry : raw) {
            List<ChronicleGoalDefinition> defs = ChronicleGoalParser.parseAll(entry);
            if (defs.isEmpty()) {
                continue;
            }
            for (ChronicleGoalDefinition def : defs) {
                parsed.add(def);
                ids.add(def.id());
                String key = buildKey(def.type(), def.targetId());
                byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(def);
            }
        }

        cachedGoals = Collections.unmodifiableList(parsed);
        cachedByKey = Collections.unmodifiableMap(byKey);
        cachedGoalIds = Collections.unmodifiableSet(ids);
        cachedHash = hash;

        if (LOG.isDebugEnabled()) {
            LOG.debug("[ChronicleGoalRegistry] Loaded {} custom goals", cachedGoals.size());
        }

        return cachedGoals;
    }

    public static List<ChronicleGoalDefinition> getGoalsForTarget(ChronicleGoalType type, ResourceLocation targetId) {
        if (type == null || targetId == null) {
            return List.of();
        }
        getGoals();
        String key = buildKey(type, targetId);
        List<ChronicleGoalDefinition> goals = cachedByKey.get(key);
        return goals == null ? List.of() : goals;
    }

    public static boolean isCustomGoalActive(String sourceId) {
        if (sourceId == null || !sourceId.startsWith(CUSTOM_SOURCE_PREFIX)) {
            return true;
        }
        String id = sourceId.substring(CUSTOM_SOURCE_PREFIX.length());
        getGoals();
        return cachedGoalIds.contains(id);
    }

    private static String buildKey(ChronicleGoalType type, ResourceLocation targetId) {
        return type.name() + "|" + targetId;
    }
}
