package org.z2six.rpgtimeline.chronicle.server;

import com.mojang.logging.LogUtils;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.z2six.rpgtimeline.api.RPGTimelineApi;
import org.z2six.rpgtimeline.chronicle.ChronicleEntryType;
import org.z2six.rpgtimeline.chronicle.ChronicleScope;
import org.z2six.rpgtimeline.chronicle.ChronicleTimeframe;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ChronicleTimeframeService {

    private static final Logger LOG = LogUtils.getLogger();
    private static final float DEFAULT_ALPHA = 0.25f;
    private static final float BASE_DAYS = 3.0f;
    private static final float BASE_ALPHA = 0.25f;
    private static final int BASE_PRIORITY = 60;
    private static final float ENTITY_DAYS = 3.0f;
    private static final float ENTITY_ALPHA = 0.45f;
    private static final int ENTITY_PRIORITY = 80;
    private static final float NOTE_DAYS = 2.0f;
    private static final float NOTE_ALPHA = 0.25f;
    private static final int NOTE_PRIORITY = 70;
    private static final String NOTE_TEXTURE = "minecraft:textures/block/bookshelf.png";
    private static final String DEFAULT_ADVANCEMENT_BG = "minecraft:textures/gui/advancements/backgrounds/stone.png";
    private static final String DEFAULT_CUSTOM_TEXTURE = "minecraft:textures/block/stone.png";

    private ChronicleTimeframeService() {
        // no-op.
    }

    public static List<ChronicleTimeframe> buildServerTimeframes(MinecraftServer server) {
        if (server == null) {
            return List.of();
        }
        List<ChronicleEvent> events = ChronicleSavedData.get(server).getEvents();
        List<ChronicleEvent> visibleAdvancements = getVisibleAdvancementEvents(events);
        Map<String, ChronicleEvent> worldFirsts = findWorldFirsts(visibleAdvancements);
        long maxDay = resolveMaxDay(server, events);

        List<ChronicleEvent> eligible = new ArrayList<>();
        for (ChronicleEvent event : events) {
            if (event == null) {
                continue;
            }
            if (event.type() == ChronicleEntryType.ADMIN_NOTE) {
                if (event.scope() != ChronicleScope.SERVER) {
                    continue;
                }
                eligible.add(event);
                continue;
            }
            if (event.type() == ChronicleEntryType.ADVANCEMENT) {
                if (!isEventVisible(event)) {
                    continue;
                }
                eligible.add(event);
            }
        }

        return buildTimeframes(server, eligible, worldFirsts, maxDay);
    }

    public static List<ChronicleTimeframe> buildPersonalTimeframes(MinecraftServer server, String playerUuid) {
        if (server == null || playerUuid == null || playerUuid.isBlank()) {
            return List.of();
        }
        List<ChronicleEvent> events = ChronicleSavedData.get(server).getEvents();
        List<ChronicleEvent> visibleAdvancements = getVisibleAdvancementEvents(events);
        Map<String, ChronicleEvent> worldFirsts = findWorldFirsts(visibleAdvancements);
        long maxDay = resolveMaxDay(server, events);

        List<ChronicleEvent> eligible = new ArrayList<>();
        for (ChronicleEvent event : events) {
            if (event == null) {
                continue;
            }
            if (event.scope() != ChronicleScope.PERSONAL) {
                continue;
            }
            if (!playerUuid.equals(event.actorUuid())) {
                continue;
            }
            if (event.type() == ChronicleEntryType.ADVANCEMENT && !isEventVisible(event)) {
                continue;
            }
            eligible.add(event);
        }

        return buildTimeframes(server, eligible, worldFirsts, maxDay);
    }

    private static List<ChronicleTimeframe> buildTimeframes(
            MinecraftServer server,
            List<ChronicleEvent> events,
            Map<String, ChronicleEvent> worldFirsts,
            long maxDay
    ) {
        List<ChronicleTimeframeRule> rules = ChronicleTimeframeRuleRegistry.getRules();
        if (events == null || events.isEmpty()) {
            return List.of();
        }

        List<ChronicleTimeframeRule> entityRules = new ArrayList<>();
        for (ChronicleTimeframeRule rule : rules) {
            if (rule.renderMode == ChronicleTimeframeRule.RenderMode.ENTITY) {
                entityRules.add(rule);
            }
        }

        List<ChronicleTimeframe> baseFrames = new ArrayList<>();
        List<ChronicleTimeframe> entityFrames = new ArrayList<>();
        for (ChronicleEvent event : events) {
            if (event == null) {
                continue;
            }
            long dayIndex = Math.max(0L, event.dayIndex());
            boolean worldFirst = isWorldFirst(event, worldFirsts);
            MatchContext ctx = MatchContext.from(server, event);

            if (event.type() == ChronicleEntryType.ADMIN_NOTE) {
                ChronicleTimeframe noteFrame = buildDirectFrame(dayIndex, maxDay, NOTE_PRIORITY, NOTE_DAYS, NOTE_ALPHA,
                        0, 0, NOTE_TEXTURE);
                if (noteFrame != null) {
                    baseFrames.add(noteFrame);
                }
                continue;
            }

            if (event.type() != ChronicleEntryType.ADVANCEMENT || ctx == null) {
                continue;
            }

            ChronicleTimeframe override = resolveEntityOverride(entityRules, event, worldFirst, ctx, dayIndex, maxDay);
            if (override != null) {
                entityFrames.add(override);
                continue;
            }

            if (ctx.goalKey != null) {
                if (ctx.goalKey.type() == ChronicleGoalType.KILL_MOB && ctx.goalKey.target() != null) {
                    ChronicleTimeframe entityFrame = buildDirectFrame(dayIndex, maxDay, ENTITY_PRIORITY, ENTITY_DAYS, ENTITY_ALPHA,
                            2, 2, ctx.goalKey.target().toString());
                    if (entityFrame != null) {
                        entityFrames.add(entityFrame);
                        continue;
                    }
                }
                String texture = resolveTileTextureFromTarget(ctx.goalKey.target(), ChronicleTimeframeRule.TargetMode.AUTO);
                if (texture == null || texture.isBlank()) {
                    texture = DEFAULT_CUSTOM_TEXTURE;
                }
                ChronicleTimeframe baseFrame = buildDirectFrame(dayIndex, maxDay, BASE_PRIORITY, BASE_DAYS, BASE_ALPHA,
                        0, 0, texture);
                if (baseFrame != null) {
                    baseFrames.add(baseFrame);
                }
                continue;
            }

            String texture = resolveAdvancementBackgroundTexture(server, ctx);
            if (texture == null || texture.isBlank()) {
                texture = DEFAULT_ADVANCEMENT_BG;
            }
            ChronicleTimeframe baseFrame = buildDirectFrame(dayIndex, maxDay, BASE_PRIORITY, BASE_DAYS, BASE_ALPHA,
                    0, 0, texture);
            if (baseFrame != null) {
                baseFrames.add(baseFrame);
            }
        }

        List<ChronicleTimeframe> baseNonOverlap = applyNonOverlap(baseFrames, true);
        List<ChronicleTimeframe> entityNonOverlap = applyNonOverlap(entityFrames, false);
        List<ChronicleTimeframe> results = new ArrayList<>();
        results.addAll(mergeTimeframes(baseNonOverlap));
        results.addAll(mergeTimeframes(entityNonOverlap));
        return results;
    }

    private static boolean isWorldFirst(ChronicleEvent event, Map<String, ChronicleEvent> worldFirsts) {
        if (event == null || event.type() != ChronicleEntryType.ADVANCEMENT) {
            return false;
        }
        String sourceId = event.sourceId();
        if (sourceId == null || sourceId.isBlank()) {
            return false;
        }
        ChronicleEvent first = worldFirsts.get(sourceId);
        return first != null && event.id().equals(first.id());
    }

    private static int compareRule(ChronicleTimeframeRule a, ChronicleTimeframeRule b) {
        if (a.priority != b.priority) {
            return Integer.compare(a.priority, b.priority);
        }
        return Integer.compare(b.order, a.order);
    }

    private static long resolveMaxDay(MinecraftServer server, List<ChronicleEvent> events) {
        long maxDay = 0L;
        if (server != null && server.overworld() != null) {
            long dayIndex = RPGTimelineApi.getDayIndexForGameTime(server.overworld().getDayTime());
            if (dayIndex > maxDay) {
                maxDay = dayIndex;
            }
        }
        if (events != null) {
            for (ChronicleEvent event : events) {
                if (event != null && event.dayIndex() > maxDay) {
                    maxDay = event.dayIndex();
                }
            }
        }
        return Math.max(0L, maxDay);
    }

    private static List<ChronicleEvent> getVisibleAdvancementEvents(List<ChronicleEvent> events) {
        List<ChronicleEvent> advancements = new ArrayList<>();
        if (events == null) {
            return advancements;
        }
        for (ChronicleEvent event : events) {
            if (event == null) {
                continue;
            }
            if (event.type() != ChronicleEntryType.ADVANCEMENT) {
                continue;
            }
            if (!isEventVisible(event)) {
                continue;
            }
            advancements.add(event);
        }
        return advancements;
    }

    private static Map<String, ChronicleEvent> findWorldFirsts(List<ChronicleEvent> advancements) {
        Map<String, ChronicleEvent> firstBy = new HashMap<>();
        for (ChronicleEvent event : advancements) {
            if (event == null) {
                continue;
            }
            String sourceId = event.sourceId();
            if (sourceId == null || sourceId.isBlank()) {
                continue;
            }
            firstBy.merge(
                    sourceId,
                    event,
                    (a, b) -> a.dayIndex() <= b.dayIndex() ? a : b
            );
        }
        return firstBy;
    }

    private static boolean isEventVisible(ChronicleEvent event) {
        if (event == null || event.type() != ChronicleEntryType.ADVANCEMENT) {
            return true;
        }
        String sourceId = event.sourceId();
        if (sourceId == null || sourceId.isBlank()) {
            return true;
        }
        return ChronicleGoalRegistry.isCustomGoalActive(sourceId);
    }

    private static ResolvedRender resolveRender(ChronicleTimeframeRule rule, MatchContext ctx) {
        if (rule == null) {
            return null;
        }
        String renderId = rule.renderId;
        int renderMode = renderModeId(rule.renderMode);
        int layer = layerId(rule.layer);

        if ((renderId == null || renderId.isBlank()) && rule.useTarget && ctx.goalKey != null) {
            renderId = resolveRenderIdFromTarget(rule, ctx.goalKey);
        }

        if ((renderId == null || renderId.isBlank()) && rule.useIcon) {
            renderId = resolveRenderIdFromIcon(rule, ctx);
        }

        if ((renderId == null || renderId.isBlank()) && rule.renderMode == ChronicleTimeframeRule.RenderMode.ENTITY) {
            renderId = resolveEntityFromText(ctx);
        }

        if (rule.renderMode == ChronicleTimeframeRule.RenderMode.ITEM) {
            return null;
        }

        if (renderId == null || renderId.isBlank()) {
            return null;
        }
        return new ResolvedRender(layer, renderMode, renderId);
    }

    private static int layerId(ChronicleTimeframeRule.Layer layer) {
        if (layer == ChronicleTimeframeRule.Layer.ENTITY) {
            return 2;
        }
        return 0;
    }

    private static ChronicleTimeframe buildFrame(long dayIndex, long maxDay, ChronicleTimeframeRule rule, ResolvedRender resolved) {
        if (rule == null || resolved == null) {
            return null;
        }
        float span = Math.max(0.25f, rule.days);
        float half = span / 2.0f;
        float start = Math.max(0.0f, dayIndex - half);
        float end = Math.min((float) maxDay, (float) dayIndex + half);
        if (end < start) {
            float tmp = start;
            start = end;
            end = tmp;
        }
        float alpha = rule.alpha <= 0.0f ? DEFAULT_ALPHA : rule.alpha;
        return new ChronicleTimeframe(
                start,
                end,
                rule.priority,
                resolved.layer,
                resolved.renderMode,
                resolved.renderId,
                alpha
        );
    }

    private static ChronicleTimeframe buildDirectFrame(long dayIndex, long maxDay, int priority, float days, float alpha,
                                                       int layer, int renderMode, String renderId) {
        if (renderId == null || renderId.isBlank()) {
            return null;
        }
        float span = Math.max(0.25f, days);
        float half = span / 2.0f;
        float start = Math.max(0.0f, dayIndex - half);
        float end = Math.min((float) maxDay, (float) dayIndex + half);
        if (end < start) {
            float tmp = start;
            start = end;
            end = tmp;
        }
        float resolvedAlpha = alpha <= 0.0f ? DEFAULT_ALPHA : alpha;
        return new ChronicleTimeframe(
                start,
                end,
                priority,
                layer,
                renderMode,
                renderId,
                resolvedAlpha
        );
    }

    private static ChronicleTimeframe resolveEntityOverride(List<ChronicleTimeframeRule> rules,
                                                            ChronicleEvent event,
                                                            boolean worldFirst,
                                                            MatchContext ctx,
                                                            long dayIndex,
                                                            long maxDay) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        ChronicleTimeframeRule bestRule = null;
        ResolvedRender bestRender = null;
        for (ChronicleTimeframeRule rule : rules) {
            if (!rule.matches(event, worldFirst, ctx)) {
                continue;
            }
            ResolvedRender resolved = resolveRender(rule, ctx);
            if (resolved == null || resolved.renderId.isBlank()) {
                continue;
            }
            if (bestRule == null || compareRule(rule, bestRule) > 0) {
                bestRule = rule;
                bestRender = resolved;
            }
        }
        if (bestRule == null || bestRender == null) {
            return null;
        }
        return buildFrame(dayIndex, maxDay, bestRule, bestRender);
    }


    private static String resolveRenderIdFromTarget(ChronicleTimeframeRule rule, CustomGoalKey goalKey) {
        if (goalKey == null || rule == null) {
            return "";
        }
        ResourceLocation target = goalKey.target();
        if (target == null) {
            return "";
        }
        return switch (rule.renderMode) {
            case ENTITY -> target.toString();
            case TILE -> resolveTileTextureFromTarget(target, rule.targetMode);
            case ITEM -> "";
        };
    }

    private static String resolveRenderIdFromIcon(ChronicleTimeframeRule rule, MatchContext ctx) {
        if (rule == null || ctx == null || ctx.iconItemId == null) {
            return "";
        }
        return switch (rule.renderMode) {
            case ENTITY -> "";
            case TILE -> resolveTextureFromItem(ctx.iconItemId, ctx.iconIsBlock, rule.targetMode);
            case ITEM -> "";
        };
    }

    private static String resolveAdvancementBackgroundTexture(MinecraftServer server, MatchContext ctx) {
        if (server == null || ctx == null || ctx.sourceId == null) {
            return "";
        }
        ResourceLocation current = ctx.sourceId;
        Set<ResourceLocation> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            AdvancementHolder holder = server.getAdvancements().get(current);
            if (holder == null) {
                break;
            }
            Advancement advancement = holder.value();
            if (advancement.display().isPresent()) {
                DisplayInfo display = advancement.display().get();
                if (display.getBackground().isPresent()) {
                    return display.getBackground().get().toString();
                }
            }
            current = advancement.parent().orElse(null);
        }
        if (ctx.iconItemId != null) {
            String texture = resolveTextureFromItem(ctx.iconItemId, ctx.iconIsBlock, ChronicleTimeframeRule.TargetMode.AUTO);
            if (!texture.isBlank()) {
                return texture;
            }
        }
        return "";
    }

    private static String resolveTileTextureFromTarget(ResourceLocation target, ChronicleTimeframeRule.TargetMode mode) {
        if (target == null) {
            return "";
        }
        Block block = BuiltInRegistries.BLOCK.getValue(target);
        boolean hasBlock = block != null && block != Blocks.AIR;
        boolean hasItem = BuiltInRegistries.ITEM.containsKey(target);

        ChronicleTimeframeRule.TargetMode effective = mode;
        if (effective == ChronicleTimeframeRule.TargetMode.AUTO) {
            effective = hasBlock ? ChronicleTimeframeRule.TargetMode.BLOCK : ChronicleTimeframeRule.TargetMode.ITEM;
        }

        if (effective == ChronicleTimeframeRule.TargetMode.BLOCK && hasBlock) {
            return target.getNamespace() + ":textures/block/" + target.getPath() + ".png";
        }
        if (hasItem) {
            return resolveBlockTextureFromItem(target);
        }
        return "";
    }

    private static String resolveTextureFromItem(ResourceLocation itemId, boolean iconIsBlock, ChronicleTimeframeRule.TargetMode mode) {
        if (itemId == null) {
            return "";
        }
        Block block = BuiltInRegistries.BLOCK.getValue(itemId);
        boolean hasBlock = block != null && block != Blocks.AIR;

        ChronicleTimeframeRule.TargetMode effective = mode;
        if (effective == ChronicleTimeframeRule.TargetMode.AUTO) {
            effective = (iconIsBlock || hasBlock) ? ChronicleTimeframeRule.TargetMode.BLOCK : ChronicleTimeframeRule.TargetMode.ITEM;
        }

        if (effective == ChronicleTimeframeRule.TargetMode.BLOCK && hasBlock) {
            return itemId.getNamespace() + ":textures/block/" + itemId.getPath() + ".png";
        }
        return resolveBlockTextureFromItem(itemId);
    }

    private static String resolveBlockTextureFromItem(ResourceLocation itemId) {
        if (itemId == null) {
            return "";
        }
        if (isValidBlock(itemId)) {
            return itemId.getNamespace() + ":textures/block/" + itemId.getPath() + ".png";
        }
        String path = itemId.getPath();
        String namespace = itemId.getNamespace();
        List<ResourceLocation> candidates = new ArrayList<>();
        if (path.endsWith("_ingot")) {
            String base = path.substring(0, path.length() - "_ingot".length());
            candidates.add(ResourceLocation.fromNamespaceAndPath(namespace, base + "_block"));
        }
        if (!path.endsWith("_block")) {
            candidates.add(ResourceLocation.fromNamespaceAndPath(namespace, path + "_block"));
        }
        String material = stripMaterial(path);
        if (!material.isBlank()) {
            candidates.add(ResourceLocation.fromNamespaceAndPath(namespace, material + "_block"));
            if ("minecraft".equals(namespace)) {
                if ("wooden".equals(material) || "wood".equals(material)) {
                    return "minecraft:textures/block/oak_planks.png";
                }
                if ("stone".equals(material)) {
                    return "minecraft:textures/block/stone.png";
                }
            }
        }
        for (ResourceLocation candidate : candidates) {
            if (isValidBlock(candidate)) {
                return candidate.getNamespace() + ":textures/block/" + candidate.getPath() + ".png";
            }
        }
        return "";
    }

    private static String stripMaterial(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String[] suffixes = {
                "_sword", "_pickaxe", "_axe", "_shovel", "_hoe",
                "_helmet", "_chestplate", "_leggings", "_boots",
                "_tool", "_gear"
        };
        for (String suffix : suffixes) {
            if (path.endsWith(suffix)) {
                return path.substring(0, path.length() - suffix.length());
            }
        }
        return "";
    }

    private static boolean isValidBlock(ResourceLocation id) {
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        return block != null && block != Blocks.AIR;
    }

    private static String resolveEntityFromText(MatchContext ctx) {
        if (ctx == null) {
            return "";
        }
        String iconText = ctx.iconItemId == null ? "" : ctx.iconItemId.toString().toLowerCase(Locale.ROOT);
        String haystack = ctx.lowerTitle + " " + ctx.lowerDetails + " " + ctx.lowerSourceId + " " + iconText;
        String normalized = haystack.replace('_', ' ').replace('-', ' ');
        String compact = normalized.replace(" ", "");
        if (haystack.isBlank()) {
            return "";
        }
        for (ResourceLocation id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
            String path = id.getPath().toLowerCase(Locale.ROOT);
            if (path.isBlank()) {
                continue;
            }
            String pathSpaced = path.replace('_', ' ');
            String pathCompact = path.replace("_", "");
            String fullId = id.toString().toLowerCase(Locale.ROOT);
            if (haystack.contains(path)
                    || haystack.contains(pathSpaced)
                    || compact.contains(pathCompact)
                    || haystack.contains(fullId)
                    || normalized.contains(fullId.replace('_', ' '))) {
                return id.toString();
            }
        }
        return "";
    }

    private static int renderModeId(ChronicleTimeframeRule.RenderMode mode) {
        if (mode == ChronicleTimeframeRule.RenderMode.ENTITY) {
            return 2;
        }
        return 0;
    }

    private static List<ChronicleTimeframe> mergeTimeframes(List<ChronicleTimeframe> frames) {
        if (frames == null || frames.isEmpty()) {
            return List.of();
        }
        List<ChronicleTimeframe> sorted = new ArrayList<>(frames);
        sorted.sort(Comparator
                .comparingDouble(ChronicleTimeframe::startDay)
                .thenComparingDouble(ChronicleTimeframe::endDay));

        List<ChronicleTimeframe> merged = new ArrayList<>();
        ChronicleTimeframe current = null;
        for (ChronicleTimeframe next : sorted) {
            if (current == null) {
                current = next;
                continue;
            }
            if (canMerge(current, next)) {
                float start = Math.min(current.startDay(), next.startDay());
                float end = Math.max(current.endDay(), next.endDay());
                current = new ChronicleTimeframe(
                        start,
                        end,
                        current.priority(),
                        current.layer(),
                        current.renderMode(),
                        current.renderId(),
                        current.alpha()
                );
                continue;
            }
            merged.add(current);
            current = next;
        }
        if (current != null) {
            merged.add(current);
        }
        return merged;
    }

    private static List<ChronicleTimeframe> applyNonOverlap(List<ChronicleTimeframe> frames, boolean allowSameRenderId) {
        if (frames == null || frames.isEmpty()) {
            return List.of();
        }
        List<ChronicleTimeframe> sorted = new ArrayList<>(frames);
        sorted.sort(Comparator
                .comparingInt(ChronicleTimeframe::priority).reversed()
                .thenComparingDouble(ChronicleTimeframe::startDay));

        List<OccupiedRange> occupied = new ArrayList<>();
        List<ChronicleTimeframe> result = new ArrayList<>();
        for (ChronicleTimeframe frame : sorted) {
            if (frame.endDay() <= frame.startDay()) {
                continue;
            }
            List<Range> blockers = new ArrayList<>();
            for (OccupiedRange range : occupied) {
                if (allowSameRenderId && safe(frame.renderId()).equals(safe(range.renderId))) {
                    continue;
                }
                blockers.add(new Range(range.start, range.end));
            }
            List<Range> available = subtractRanges(frame.startDay(), frame.endDay(), blockers);
            for (Range range : available) {
                ChronicleTimeframe clipped = new ChronicleTimeframe(
                        range.start,
                        range.end,
                        frame.priority(),
                        frame.layer(),
                        frame.renderMode(),
                        frame.renderId(),
                        frame.alpha()
                );
                result.add(clipped);
                insertOccupiedRange(occupied, new OccupiedRange(range.start, range.end, frame.renderId()));
            }
        }
        return result;
    }

    private static List<Range> subtractRanges(float start, float end, List<Range> occupied) {
        List<Range> segments = new ArrayList<>();
        if (end <= start) {
            return segments;
        }
        float cursor = start;
        for (Range range : occupied) {
            if (range.end <= cursor) {
                continue;
            }
            if (range.start >= end) {
                break;
            }
            if (range.start > cursor) {
                segments.add(new Range(cursor, Math.min(range.start, end)));
            }
            cursor = Math.max(cursor, range.end);
            if (cursor >= end) {
                break;
            }
        }
        if (cursor < end) {
            segments.add(new Range(cursor, end));
        }
        return segments;
    }

    private static void insertOccupiedRange(List<OccupiedRange> occupied, OccupiedRange range) {
        float start = range.start;
        float end = range.end;
        String renderId = safe(range.renderId);
        int index = 0;
        while (index < occupied.size() && occupied.get(index).end < start) {
            index++;
        }
        while (index < occupied.size() && occupied.get(index).start <= end) {
            OccupiedRange existing = occupied.get(index);
            if (!safe(existing.renderId).equals(renderId)) {
                index++;
                continue;
            }
            occupied.remove(index);
            start = Math.min(start, existing.start);
            end = Math.max(end, existing.end);
        }
        occupied.add(index, new OccupiedRange(start, end, renderId));
    }

    private static boolean canMerge(ChronicleTimeframe a, ChronicleTimeframe b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.priority() != b.priority()) {
            return false;
        }
        if (a.layer() != b.layer()) {
            return false;
        }
        if (a.renderMode() != b.renderMode()) {
            return false;
        }
        if (!safe(a.renderId()).equals(safe(b.renderId()))) {
            return false;
        }
        if (Math.abs(a.alpha() - b.alpha()) > 0.001f) {
            return false;
        }
        return b.startDay() <= a.endDay() + 0.01f;
    }

    static boolean matchesGlob(String text, String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return text == null || text.isEmpty();
        }
        String regex = pattern
                .replace(".", "\\.")
                .replace("?", ".")
                .replace("*", ".*");
        return text != null && text.matches(regex);
    }

    static final class MatchContext {
        final String lowerTitle;
        final String lowerDetails;
        final String lowerSourceId;
        final ResourceLocation sourceId;
        final CustomGoalKey goalKey;
        final String frameType;
        final ResourceLocation iconItemId;
        final boolean iconIsBlock;

        private MatchContext(String lowerTitle,
                             String lowerDetails,
                             String lowerSourceId,
                             ResourceLocation sourceId,
                             CustomGoalKey goalKey,
                             String frameType,
                             ResourceLocation iconItemId,
                             boolean iconIsBlock) {
            this.lowerTitle = lowerTitle;
            this.lowerDetails = lowerDetails;
            this.lowerSourceId = lowerSourceId;
            this.sourceId = sourceId;
            this.goalKey = goalKey;
            this.frameType = frameType;
            this.iconItemId = iconItemId;
            this.iconIsBlock = iconIsBlock;
        }

        static MatchContext from(MinecraftServer server, ChronicleEvent event) {
            String title = safeLower(event.title());
            String details = safeLower(event.details());
            String sourceRaw = event.sourceId() == null ? "" : event.sourceId().trim();
            String lowerSource = sourceRaw.toLowerCase(Locale.ROOT);
            ResourceLocation sourceId = ResourceLocation.tryParse(sourceRaw);
            CustomGoalKey goalKey = parseCustomGoalKey(sourceRaw);
            AdvancementInfo info = resolveAdvancementInfo(server, sourceId);
            return new MatchContext(title, details, lowerSource, sourceId, goalKey, info.frameType(), info.iconItemId(), info.iconIsBlock());
        }
    }

    private static CustomGoalKey parseCustomGoalKey(String sourceId) {
        if (sourceId == null || !sourceId.startsWith(ChronicleGoalRegistry.CUSTOM_SOURCE_PREFIX)) {
            return null;
        }
        String raw = sourceId.substring(ChronicleGoalRegistry.CUSTOM_SOURCE_PREFIX.length());
        String[] parts = raw.split(":");
        if (parts.length < 4) {
            return null;
        }
        ChronicleGoalType type = parseGoalType(parts[0]);
        if (type == null) {
            return null;
        }
        String namespace = parts[1];
        String path = parts[2];
        String countRaw = parts[3];
        ResourceLocation target = ResourceLocation.tryParse(namespace + ":" + path);
        if (target == null) {
            return null;
        }
        int count;
        try {
            count = Integer.parseInt(countRaw);
        } catch (NumberFormatException ex) {
            return null;
        }
        return new CustomGoalKey(type, target, count);
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

    private static AdvancementInfo resolveAdvancementInfo(MinecraftServer server, ResourceLocation sourceId) {
        if (server == null || sourceId == null) {
            return AdvancementInfo.EMPTY;
        }
        try {
            Object manager = server.getAdvancements();
            AdvancementHolder holder = resolveAdvancementHolder(manager, sourceId);
            if (holder == null) {
                return AdvancementInfo.EMPTY;
            }
            DisplayInfo display = holder.value().display().orElse(null);
            if (display == null) {
                return AdvancementInfo.EMPTY;
            }
            Object frame = invokeFrame(display, "getFrame");
            if (frame == null) {
                frame = invokeFrame(display, "frame");
            }
            String frameType = frame == null ? "" : frame.toString().toLowerCase(Locale.ROOT);

            ItemStack iconStack = resolveDisplayIcon(display);
            ResourceLocation iconId = null;
            boolean iconIsBlock = false;
            if (iconStack != null && !iconStack.isEmpty()) {
                iconId = BuiltInRegistries.ITEM.getKey(iconStack.getItem());
                iconIsBlock = iconStack.getItem() instanceof BlockItem;
            }
            return new AdvancementInfo(frameType, iconId, iconIsBlock);
        } catch (Throwable t) {
            LOG.debug("[ChronicleTimeframeService] Failed to resolve advancement info for {}", sourceId, t);
            return AdvancementInfo.EMPTY;
        }
    }

    private static ItemStack resolveDisplayIcon(DisplayInfo display) {
        if (display == null) {
            return ItemStack.EMPTY;
        }
        try {
            Method method = display.getClass().getMethod("getIcon");
            Object result = method.invoke(display);
            if (result instanceof ItemStack stack) {
                return stack;
            }
        } catch (Throwable ignored) {
        }
        try {
            Method method = display.getClass().getMethod("icon");
            Object result = method.invoke(display);
            if (result instanceof ItemStack stack) {
                return stack;
            }
        } catch (Throwable ignored) {
        }
        return ItemStack.EMPTY;
    }

    private static Object invokeFrame(DisplayInfo display, String methodName) {
        try {
            Method method = display.getClass().getMethod(methodName);
            return method.invoke(display);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String safeLower(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static AdvancementHolder resolveAdvancementHolder(Object manager, ResourceLocation sourceId) {
        if (manager == null || sourceId == null) {
            return null;
        }
        try {
            Method method = manager.getClass().getMethod("getAdvancement", ResourceLocation.class);
            Object result = method.invoke(manager, sourceId);
            if (result instanceof AdvancementHolder holder) {
                return holder;
            }
        } catch (Throwable ignored) {
        }
        try {
            Method method = manager.getClass().getMethod("get", ResourceLocation.class);
            Object result = method.invoke(manager, sourceId);
            if (result instanceof AdvancementHolder holder) {
                return holder;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private record AdvancementInfo(String frameType, ResourceLocation iconItemId, boolean iconIsBlock) {
        private static final AdvancementInfo EMPTY = new AdvancementInfo("", null, false);
    }

    record CustomGoalKey(ChronicleGoalType type, ResourceLocation target, int count) {
    }

    private static final class ResolvedRender {
        private final int layer;
        private final int renderMode;
        private final String renderId;

        private ResolvedRender(int layer, int renderMode, String renderId) {
            this.layer = layer;
            this.renderMode = renderMode;
            this.renderId = renderId == null ? "" : renderId;
        }
    }

    private static final class Range {
        private final float start;
        private final float end;

        private Range(float start, float end) {
            this.start = start;
            this.end = end;
        }
    }

    private static final class OccupiedRange {
        private final float start;
        private final float end;
        private final String renderId;

        private OccupiedRange(float start, float end, String renderId) {
            this.start = start;
            this.end = end;
            this.renderId = renderId == null ? "" : renderId;
        }
    }
}
