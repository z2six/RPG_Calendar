package org.z2six.rpgcalendar.chronicle.server;

import com.mojang.logging.LogUtils;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.z2six.rpgcalendar.api.RPGCalendarApi;
import org.z2six.rpgcalendar.chronicle.ChronicleDetail;
import org.z2six.rpgcalendar.chronicle.ChronicleEntry;
import org.z2six.rpgcalendar.chronicle.ChronicleEntryType;
import org.z2six.rpgcalendar.chronicle.ChronicleScope;
import org.z2six.rpgcalendar.network.ChroniclePayloads;

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
            if (display.isHidden() || !display.shouldShowToast()) {
                return;
            }

            String title = display.getTitle().getString();
            String details = display.getDescription().getString();
            String actorName = player.getGameProfile().getName();
            String actorUuid = player.getUUID().toString();
            String sourceId = advancement.id().toString();
            String iconItemId = getItemId(display.getIcon());
            long dayIndex = RPGCalendarApi.getDayIndexForGameTime(player.serverLevel().getDayTime());

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

        } catch (Throwable t) {
            LOG.error("[ChronicleService] recordAdvancement failed safely", t);
        }
    }

    public static void addAdminEvent(ServerPlayer admin, ChronicleScope scope, String title, String details) {
        long dayIndex = RPGCalendarApi.getDayIndexForGameTime(admin.serverLevel().getDayTime());
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
            String title = "World First: " + first.title();
            results.add(toEntry(
                    first,
                    ChronicleEntryType.WORLD_FIRST,
                    true,
                    false,
                    List.of(),
                    title,
                    first.actorName()
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
                safe(event.actorName()),
                safe(event.actorUuid()),
                highlight,
                summary,
                safe(event.iconItemId()),
                drilldown
        );
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
}
