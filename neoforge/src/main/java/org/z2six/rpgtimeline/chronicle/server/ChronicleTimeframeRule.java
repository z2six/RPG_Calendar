package org.z2six.rpgtimeline.chronicle.server;

import net.minecraft.resources.ResourceLocation;
import org.z2six.rpgtimeline.chronicle.ChronicleEntryType;

import java.util.List;
import java.util.Locale;

final class ChronicleTimeframeRule {

    enum MatchType {
        ANY,
        ADVANCEMENT,
        CUSTOM,
        NOTE
    }

    enum Layer {
        BASE,
        ITEM,
        ENTITY
    }

    enum RenderMode {
        TILE,
        ITEM,
        ENTITY
    }

    enum TargetMode {
        AUTO,
        BLOCK,
        ITEM,
        ENTITY
    }

    final int priority;
    final float days;
    final float alpha;
    final Layer layer;
    final RenderMode renderMode;
    final String renderId;
    final boolean useTarget;
    final boolean useIcon;
    final TargetMode targetMode;
    final MatchType matchType;
    final Boolean worldFirst;
    final String id;
    final String idPattern;
    final String namespacePattern;
    final List<String> keywords;
    final String frameType;
    final ChronicleGoalType goalType;
    final ResourceLocation goalTarget;
    final Integer minCount;
    final Integer maxCount;
    final int order;

    ChronicleTimeframeRule(
            int priority,
            float days,
            float alpha,
            Layer layer,
            RenderMode renderMode,
            String renderId,
            boolean useTarget,
            boolean useIcon,
            TargetMode targetMode,
            MatchType matchType,
            Boolean worldFirst,
            String id,
            String idPattern,
            String namespacePattern,
            List<String> keywords,
            String frameType,
            ChronicleGoalType goalType,
            ResourceLocation goalTarget,
            Integer minCount,
            Integer maxCount,
            int order
    ) {
        this.priority = priority;
        this.days = days;
        this.alpha = alpha;
        this.renderMode = renderMode == null ? RenderMode.TILE : renderMode;
        this.layer = layer == null ? defaultLayer(this.renderMode) : layer;
        this.renderId = normalize(renderId);
        this.useTarget = useTarget;
        this.useIcon = useIcon;
        this.targetMode = targetMode == null ? TargetMode.AUTO : targetMode;
        this.matchType = matchType;
        this.worldFirst = worldFirst;
        this.id = normalize(id);
        this.idPattern = normalize(idPattern);
        this.namespacePattern = normalize(namespacePattern);
        this.keywords = keywords == null ? List.of() : keywords;
        this.frameType = normalize(frameType);
        this.goalType = goalType;
        this.goalTarget = goalTarget;
        this.minCount = minCount;
        this.maxCount = maxCount;
        this.order = order;
    }

    boolean matches(ChronicleEvent event, boolean isWorldFirst, ChronicleTimeframeService.MatchContext ctx) {
        if (event == null) {
            return false;
        }

        boolean isCustom = ctx.goalKey != null;
        if (!matchesType(event.type(), isCustom)) {
            return false;
        }

        if (worldFirst != null && worldFirst.booleanValue() != isWorldFirst) {
            return false;
        }

        if (id != null && !id.isEmpty()) {
            if (ctx.lowerSourceId.isEmpty() || !ctx.lowerSourceId.equals(id)) {
                return false;
            }
        }

        if (idPattern != null && !idPattern.isEmpty()) {
            if (ctx.lowerSourceId.isEmpty() || !ChronicleTimeframeService.matchesGlob(ctx.lowerSourceId, idPattern)) {
                return false;
            }
        }

        if (namespacePattern != null && !namespacePattern.isEmpty()) {
            String ns = ctx.sourceId != null ? ctx.sourceId.getNamespace().toLowerCase(Locale.ROOT) : "";
            if (ns.isEmpty() || !ChronicleTimeframeService.matchesGlob(ns, namespacePattern)) {
                return false;
            }
        }

        if (!keywords.isEmpty()) {
            if (!matchesKeywords(ctx)) {
                return false;
            }
        }

        if (frameType != null && !frameType.isEmpty()) {
            if (ctx.frameType.isEmpty() || !ctx.frameType.equals(frameType)) {
                return false;
            }
        }

        if (goalType != null || goalTarget != null || minCount != null || maxCount != null) {
            if (ctx.goalKey == null) {
                return false;
            }
            if (goalType != null && ctx.goalKey.type() != goalType) {
                return false;
            }
            if (goalTarget != null && !goalTarget.equals(ctx.goalKey.target())) {
                return false;
            }
            if (minCount != null && ctx.goalKey.count() < minCount) {
                return false;
            }
            if (maxCount != null && ctx.goalKey.count() > maxCount) {
                return false;
            }
        }

        return true;
    }

    private boolean matchesType(ChronicleEntryType entryType, boolean isCustom) {
        return switch (matchType) {
            case ANY -> true;
            case NOTE -> entryType == ChronicleEntryType.ADMIN_NOTE;
            case CUSTOM -> entryType == ChronicleEntryType.ADVANCEMENT && isCustom;
            case ADVANCEMENT -> entryType == ChronicleEntryType.ADVANCEMENT && !isCustom;
        };
    }

    private boolean matchesKeywords(ChronicleTimeframeService.MatchContext ctx) {
        if (keywords.isEmpty()) {
            return true;
        }
        for (String keyword : keywords) {
            if (keyword == null || keyword.isEmpty()) {
                continue;
            }
            if (ctx.lowerTitle.contains(keyword)
                    || ctx.lowerDetails.contains(keyword)
                    || ctx.lowerSourceId.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static Layer defaultLayer(RenderMode mode) {
        if (mode == RenderMode.ENTITY) {
            return Layer.ENTITY;
        }
        return Layer.BASE;
    }
}
