package org.z2six.rpgtimeline.chronicle.server;

import com.mojang.logging.LogUtils;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.slf4j.Logger;
import org.z2six.rpgtimeline.api.RPGTimelineApi;
import org.z2six.rpgtimeline.chronicle.ChronicleDetail;
import org.z2six.rpgtimeline.chronicle.ChronicleEntry;
import org.z2six.rpgtimeline.chronicle.ChronicleEntryType;
import org.z2six.rpgtimeline.chronicle.ChronicleScope;
import org.z2six.rpgtimeline.chronicle.HallOfFameEntry;
import org.z2six.rpgtimeline.config.RPGTimelineConfig;
import org.z2six.rpgtimeline.network.ChroniclePayloads;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ChronicleService {

    private static final Logger LOG = LogUtils.getLogger();
    private static final int MAX_DETAILS_PER_ENTRY = 50;
    private static final AnnouncementRateLimiter ANNOUNCEMENT_RATE_LIMITER = new AnnouncementRateLimiter();

    private ChronicleService() {
        // no-op
    }

    public static void recordAdvancement(ServerPlayer player, AdvancementHolder advancement) {
        try {
            if (player == null || advancement == null) {
                return;
            }

            Advancement adv = advancement.value();
            DisplayInfo display = adv.display().orElse(null);
            if (display == null) {
                return;
            }
            if (display.isHidden()) {
                return;
            }

            Component titleComponent = display.getTitle();
            Component detailsComponent = display.getDescription();
            var provider = player.serverLevel().registryAccess();
            String title = Component.Serializer.toJson(titleComponent, provider);
            String details = Component.Serializer.toJson(detailsComponent, provider);
            String actorName = player.getGameProfile().getName();
            String actorUuid = player.getUUID().toString();
            String sourceId = advancement.id().toString();
            String iconItemId = getItemId(display.getIcon());
            long dayIndex = RPGTimelineApi.getDayIndexForGameTime(player.serverLevel().getDayTime());

            boolean worldFirst = isWorldFirst(player.getServer(), sourceId);

            ChronicleEvent event = new ChronicleEvent(
                    UUID.randomUUID().toString(),
                    ChronicleEntryType.ADVANCEMENT,
                    ChronicleScope.PERSONAL,
                    dayIndex,
                    title,
                    details,
                    actorName,
                    actorUuid,
                    sourceId,
                    iconItemId
            );

            ChronicleSavedData.get(player.getServer()).addEvent(event);

            ChroniclePayloads.broadcastFullSync(player.getServer());
            if (worldFirst) {
                announceWorldFirst(player.getServer(), actorName, actorUuid, titleComponent);
                ChroniclePayloads.broadcastHallOfFame(player.getServer());
            }

        } catch (Throwable t) {
            LOG.error("[ChronicleService] recordAdvancement failed safely", t);
        }
    }

    public static void recordExternalAdvancement(
            ServerPlayer player,
            String sourceId,
            Component title,
            Component description,
            String iconItemId
    ) {
        try {
            if (player == null) {
                return;
            }

            Component titleComponent = title == null ? Component.empty() : title;
            Component detailsComponent = description == null ? Component.empty() : description;
            var provider = player.serverLevel().registryAccess();
            String titleJson = Component.Serializer.toJson(titleComponent, provider);
            String detailsJson = Component.Serializer.toJson(detailsComponent, provider);
            String actorName = player.getGameProfile().getName();
            String actorUuid = player.getUUID().toString();
            String resolvedSourceId = safe(sourceId);
            String resolvedIcon = safe(iconItemId);
            if (resolvedIcon.isBlank()) {
                resolvedIcon = "minecraft:paper";
            }
            long dayIndex = RPGTimelineApi.getDayIndexForGameTime(player.serverLevel().getDayTime());

            boolean worldFirst = !resolvedSourceId.isBlank() && isWorldFirst(player.getServer(), resolvedSourceId);

            ChronicleEvent event = new ChronicleEvent(
                    UUID.randomUUID().toString(),
                    ChronicleEntryType.ADVANCEMENT,
                    ChronicleScope.PERSONAL,
                    dayIndex,
                    titleJson,
                    detailsJson,
                    actorName,
                    actorUuid,
                    resolvedSourceId,
                    resolvedIcon
            );

            ChronicleSavedData.get(player.getServer()).addEvent(event);
            ChroniclePayloads.broadcastFullSync(player.getServer());

            if (worldFirst) {
                announceWorldFirst(player.getServer(), actorName, actorUuid, titleComponent);
                ChroniclePayloads.broadcastHallOfFame(player.getServer());
            }

        } catch (Throwable t) {
            LOG.error("[ChronicleService] recordExternalAdvancement failed safely", t);
        }
    }

    public static void recordCustomGoal(ServerPlayer player, ChronicleGoalDefinition goal) {
        try {
            if (player == null || goal == null) {
                return;
            }
            String title = safe(goal.title());
            String details = safe(goal.description());
            String actorName = player.getGameProfile().getName();
            String actorUuid = player.getUUID().toString();
            String sourceId = goal.sourceId();
            String iconItemId = safe(goal.iconItemId());
            long dayIndex = RPGTimelineApi.getDayIndexForGameTime(player.serverLevel().getDayTime());

            boolean worldFirst = isWorldFirst(player.getServer(), sourceId);

            ChronicleEvent event = new ChronicleEvent(
                    UUID.randomUUID().toString(),
                    ChronicleEntryType.ADVANCEMENT,
                    ChronicleScope.PERSONAL,
                    dayIndex,
                    title,
                    details,
                    actorName,
                    actorUuid,
                    sourceId,
                    iconItemId
            );

            ChronicleSavedData.get(player.getServer()).addEvent(event);
            ChroniclePayloads.broadcastFullSync(player.getServer());

            if (worldFirst) {
                announceWorldFirst(player.getServer(), actorName, actorUuid, Component.literal(title));
                ChroniclePayloads.broadcastHallOfFame(player.getServer());
            }
        } catch (Throwable t) {
            LOG.error("[ChronicleService] recordCustomGoal failed safely", t);
        }
    }

    public static void addAdminEvent(ServerPlayer admin, ChronicleScope scope, String title, String details) {
        long dayIndex = RPGTimelineApi.getDayIndexForGameTime(admin.serverLevel().getDayTime());
        addAdminEvent(admin, scope, title, details, dayIndex);
    }

    public static void addAdminEvent(ServerPlayer admin, ChronicleScope scope, String title, String details, long dayIndex) {
        try {
            if (admin == null || scope == null) {
                return;
            }

            String actorName = admin.getGameProfile().getName();
            String actorUuid = admin.getUUID().toString();
            String iconItemId = getItemId(admin.getMainHandItem());
            long clampedDayIndex = Math.max(0L, dayIndex);

            ChronicleEvent event = new ChronicleEvent(
                    UUID.randomUUID().toString(),
                    ChronicleEntryType.ADMIN_NOTE,
                    scope,
                    clampedDayIndex,
                    safe(title),
                    safe(details),
                    actorName,
                    actorUuid,
                    "",
                    iconItemId
            );

            ChronicleSavedData.get(admin.getServer()).addEvent(event);
            ChroniclePayloads.broadcastFullSync(admin.getServer());

        } catch (Throwable t) {
            LOG.error("[ChronicleService] addAdminEvent failed safely", t);
        }
    }

    public static List<ChronicleEntry> buildPersonalTimeline(MinecraftServer server, String playerUuid) {
        List<ChronicleEntry> results = new ArrayList<>();
        if (server == null || playerUuid == null || playerUuid.isBlank()) {
            return results;
        }

        List<ChronicleEvent> events = ChronicleSavedData.get(server).getEvents();
        for (ChronicleEvent event : events) {
            if (event.scope() != ChronicleScope.PERSONAL) {
                continue;
            }
            if (!playerUuid.equals(event.actorUuid())) {
                continue;
            }
            if (!isEventVisible(event)) {
                continue;
            }

            results.add(toEntry(event, event.type(), false, false, List.of(), event.title(), event.details()));
        }

        results.sort(Comparator.comparingLong(ChronicleEntry::dayIndex));
        return results;
    }

    public static List<ChronicleEntry> buildServerTimeline(MinecraftServer server) {
        List<ChronicleEntry> results = new ArrayList<>();
        if (server == null) {
            return results;
        }

        List<ChronicleEvent> events = ChronicleSavedData.get(server).getEvents();

        // Admin notes (server scope)
        for (ChronicleEvent event : events) {
            if (event.scope() != ChronicleScope.SERVER) {
                continue;
            }
            results.add(toEntry(event, ChronicleEntryType.ADMIN_NOTE, false, false, List.of(), event.title(), event.details()));
        }

        // World-first per advancement, plus raw advancement events (non-world-first).
        List<ChronicleEvent> advancements = new ArrayList<>();
        for (ChronicleEvent event : events) {
            if (event.type() == ChronicleEntryType.ADVANCEMENT) {
                if (!isEventVisible(event)) {
                    continue;
                }
                advancements.add(event);
            }
        }

        Map<String, ChronicleEvent> firstByAdvancement = new HashMap<>();
        for (ChronicleEvent event : advancements) {
            if (event.sourceId() == null || event.sourceId().isBlank()) {
                continue;
            }
            firstByAdvancement.merge(
                    event.sourceId(),
                    event,
                    (a, b) -> a.dayIndex() <= b.dayIndex() ? a : b
            );
        }

        Set<String> worldFirstEventIds = new HashSet<>();
        for (ChronicleEvent first : firstByAdvancement.values()) {
            results.add(toEntry(
                    first,
                    ChronicleEntryType.WORLD_FIRST,
                    true,
                    false,
                    List.of(),
                    first.title(),
                    first.details()
            ));
            worldFirstEventIds.add(first.id());
        }

        for (ChronicleEvent event : advancements) {
            if (worldFirstEventIds.contains(event.id())) {
                continue;
            }
            results.add(toEntry(event, ChronicleEntryType.ADVANCEMENT, false, false, List.of(), event.title(), event.details()));
        }

        results.sort(Comparator.comparingLong(ChronicleEntry::dayIndex));
        return results;
    }

    public static List<HallOfFameEntry> buildHallOfFame(MinecraftServer server) {
        List<HallOfFameEntry> results = new ArrayList<>();
        if (server == null) {
            return results;
        }

        List<ChronicleEvent> advancements = getVisibleAdvancementEvents(server);
        Map<String, ChronicleEvent> firstByAdvancement = findWorldFirsts(advancements);

        Map<String, Integer> counts = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        for (ChronicleEvent first : firstByAdvancement.values()) {
            if (first.actorUuid() == null || first.actorUuid().isBlank()) {
                continue;
            }
            counts.merge(first.actorUuid(), 1, Integer::sum);
            if (first.actorName() != null && !first.actorName().isBlank()) {
                names.put(first.actorUuid(), first.actorName());
            }
        }

        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            String uuid = entry.getKey();
            String name = names.getOrDefault(uuid, "Player");
            results.add(new HallOfFameEntry(uuid, name, entry.getValue()));
        }

        results.sort(Comparator
                .comparingInt(HallOfFameEntry::worldFirstCount).reversed()
                .thenComparing(HallOfFameEntry::playerName));
        return results;
    }

    public static List<ChronicleEntry> buildWorldFirstsForPlayer(MinecraftServer server, String playerUuid) {
        List<ChronicleEntry> results = new ArrayList<>();
        if (server == null || playerUuid == null || playerUuid.isBlank()) {
            return results;
        }

        List<ChronicleEvent> advancements = getVisibleAdvancementEvents(server);
        Map<String, ChronicleEvent> firstByAdvancement = findWorldFirsts(advancements);
        for (ChronicleEvent first : firstByAdvancement.values()) {
            if (!playerUuid.equals(first.actorUuid())) {
                continue;
            }
            results.add(toEntry(
                    first,
                    ChronicleEntryType.WORLD_FIRST,
                    true,
                    false,
                    List.of(),
                    first.title(),
                    first.details()
            ));
        }

        results.sort(Comparator.comparingLong(ChronicleEntry::dayIndex));
        return results;
    }

    private static ChronicleEntry toEntry(
            ChronicleEvent event,
            ChronicleEntryType type,
            boolean highlight,
            boolean summary,
            List<ChronicleDetail> drilldown,
            String titleOverride,
            String detailsOverride
    ) {
        return new ChronicleEntry(
                event.id(),
                type,
                event.scope(),
                event.dayIndex(),
                safe(titleOverride),
                safe(detailsOverride),
                safe(event.sourceId()),
                safe(event.actorName()),
                safe(event.actorUuid()),
                highlight,
                summary,
                safe(event.iconItemId()),
                drilldown
        );
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

    private static List<ChronicleEvent> getVisibleAdvancementEvents(MinecraftServer server) {
        List<ChronicleEvent> advancements = new ArrayList<>();
        List<ChronicleEvent> events = ChronicleSavedData.get(server).getEvents();
        for (ChronicleEvent event : events) {
            if (event.type() == ChronicleEntryType.ADVANCEMENT && isEventVisible(event)) {
                advancements.add(event);
            }
        }
        return advancements;
    }

    private static Map<String, ChronicleEvent> findWorldFirsts(List<ChronicleEvent> advancements) {
        Map<String, ChronicleEvent> firstByAdvancement = new HashMap<>();
        for (ChronicleEvent event : advancements) {
            if (event.sourceId() == null || event.sourceId().isBlank()) {
                continue;
            }
            firstByAdvancement.merge(
                    event.sourceId(),
                    event,
                    (a, b) -> a.dayIndex() <= b.dayIndex() ? a : b
            );
        }
        return firstByAdvancement;
    }

    private static boolean isWorldFirst(MinecraftServer server, String sourceId) {
        if (server == null || sourceId == null || sourceId.isBlank()) {
            return false;
        }
        List<ChronicleEvent> events = ChronicleSavedData.get(server).getEvents();
        for (ChronicleEvent event : events) {
            if (event.type() != ChronicleEntryType.ADVANCEMENT) {
                continue;
            }
            if (sourceId.equals(event.sourceId())) {
                return false;
            }
        }
        return true;
    }

    private static void announceWorldFirst(MinecraftServer server, String actorName, String actorUuid, Component title) {
        if (server == null) {
            return;
        }
        if (!ANNOUNCEMENT_RATE_LIMITER.allow(server, actorUuid)) {
            LOG.debug("[ChronicleService] Suppressed world-first chat announcement by rate limit (actorUuid={})", actorUuid);
            return;
        }
        String who = actorName == null || actorName.isBlank()
                ? Component.translatable("chat.rpgtimeline.someone").getString()
                : actorName;
        Component titleComponent = title == null ? Component.empty() : title;
        Component message = Component.translatable("chat.rpgtimeline.world_first.prefix")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.translatable("chat.rpgtimeline.world_first.body", who, titleComponent).withStyle(ChatFormatting.YELLOW));
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    private static String getItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "minecraft:paper";
        }
        return Objects.toString(BuiltInRegistries.ITEM.getKey(stack.getItem()), "minecraft:paper");
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static final class AnnouncementRateLimiter {
        private final Map<String, ArrayDeque<Long>> perActorAnnouncementTicks = new HashMap<>();
        private final ArrayDeque<Long> globalAnnouncementTicks = new ArrayDeque<>();

        private synchronized boolean allow(MinecraftServer server, String actorUuid) {
            int perPlayerMax = RPGTimelineConfig.getAdvancementAnnouncementPlayerMaxCount();
            int perPlayerWindow = RPGTimelineConfig.getAdvancementAnnouncementPlayerWindowTicks();
            int globalMax = RPGTimelineConfig.getAdvancementAnnouncementGlobalMaxCount();
            int globalWindow = RPGTimelineConfig.getAdvancementAnnouncementGlobalWindowTicks();

            if (perPlayerMax <= 0 && globalMax <= 0) {
                return true;
            }

            long nowTick = getServerTick(server);
            if (globalMax > 0) {
                prune(globalAnnouncementTicks, nowTick, globalWindow);
                if (globalAnnouncementTicks.size() >= globalMax) {
                    return false;
                }
            }

            String actorKey = actorUuid == null ? "" : actorUuid.trim();
            ArrayDeque<Long> actorTicks = null;
            if (perPlayerMax > 0 && !actorKey.isEmpty()) {
                actorTicks = perActorAnnouncementTicks.computeIfAbsent(actorKey, ignored -> new ArrayDeque<>());
                prune(actorTicks, nowTick, perPlayerWindow);
                if (actorTicks.size() >= perPlayerMax) {
                    if (actorTicks.isEmpty()) {
                        perActorAnnouncementTicks.remove(actorKey);
                    }
                    return false;
                }
            }

            if (globalMax > 0) {
                globalAnnouncementTicks.addLast(nowTick);
            }
            if (actorTicks != null) {
                actorTicks.addLast(nowTick);
            }
            cleanupStaleActors(nowTick, perPlayerWindow);
            return true;
        }

        private static void prune(ArrayDeque<Long> queue, long nowTick, int windowTicks) {
            while (!queue.isEmpty()) {
                long tick = queue.peekFirst();
                if (nowTick - tick >= windowTicks) {
                    queue.removeFirst();
                } else {
                    break;
                }
            }
        }

        private void cleanupStaleActors(long nowTick, int windowTicks) {
            if (perActorAnnouncementTicks.isEmpty()) {
                return;
            }
            java.util.Iterator<Map.Entry<String, ArrayDeque<Long>>> it = perActorAnnouncementTicks.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, ArrayDeque<Long>> entry = it.next();
                ArrayDeque<Long> ticks = entry.getValue();
                prune(ticks, nowTick, windowTicks);
                if (ticks.isEmpty()) {
                    it.remove();
                }
            }
        }

        private static long getServerTick(MinecraftServer server) {
            if (server == null || server.overworld() == null) {
                return 0L;
            }
            return server.overworld().getGameTime();
        }
    }
}
