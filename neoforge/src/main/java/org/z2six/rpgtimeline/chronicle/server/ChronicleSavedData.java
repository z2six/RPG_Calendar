package org.z2six.rpgtimeline.chronicle.server;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import org.z2six.rpgtimeline.chronicle.ChronicleEntryType;
import org.z2six.rpgtimeline.chronicle.ChronicleScope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ChronicleSavedData extends SavedData {

    private static final Logger LOG = LogUtils.getLogger();
    private static final String DATA_NAME = "rpgtimeline_chronicle";

    private final List<ChronicleEvent> events = new ArrayList<>();
    private final java.util.Map<String, java.util.Map<String, Integer>> goalProgress = new java.util.HashMap<>();

    private ChronicleSavedData() {
    }

    public static ChronicleSavedData get(MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(ChronicleSavedData::new, ChronicleSavedData::load), DATA_NAME);
    }

    public List<ChronicleEvent> getEvents() {
        return Collections.unmodifiableList(events);
    }

    public void addEvent(ChronicleEvent event) {
        events.add(event);
        setDirty();
    }

    public int getGoalProgress(String goalId, String playerUuid) {
        if (goalId == null || playerUuid == null) {
            return 0;
        }
        java.util.Map<String, Integer> perGoal = goalProgress.get(goalId);
        if (perGoal == null) {
            return 0;
        }
        return perGoal.getOrDefault(playerUuid, 0);
    }

    public int addGoalProgress(String goalId, String playerUuid, int delta) {
        if (goalId == null || playerUuid == null || delta <= 0) {
            return getGoalProgress(goalId, playerUuid);
        }
        java.util.Map<String, Integer> perGoal = goalProgress.computeIfAbsent(goalId, k -> new java.util.HashMap<>());
        int next = perGoal.getOrDefault(playerUuid, 0) + delta;
        perGoal.put(playerUuid, next);
        setDirty();
        return next;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (ChronicleEvent event : events) {
            CompoundTag e = new CompoundTag();
            e.putString("Id", safe(event.id()));
            e.putString("Type", event.type().name());
            e.putString("Scope", event.scope().name());
            e.putLong("DayIndex", event.dayIndex());
            e.putString("Title", safe(event.title()));
            e.putString("Details", safe(event.details()));
            e.putString("ActorName", safe(event.actorName()));
            e.putString("ActorUuid", safe(event.actorUuid()));
            e.putString("SourceId", safe(event.sourceId()));
            e.putString("IconItemId", safe(event.iconItemId()));
            list.add(e);
        }
        tag.put("Events", list);

        ListTag progressList = new ListTag();
        for (java.util.Map.Entry<String, java.util.Map<String, Integer>> entry : goalProgress.entrySet()) {
            CompoundTag goalTag = new CompoundTag();
            goalTag.putString("GoalId", safe(entry.getKey()));
            ListTag playerList = new ListTag();
            for (java.util.Map.Entry<String, Integer> playerEntry : entry.getValue().entrySet()) {
                CompoundTag playerTag = new CompoundTag();
                playerTag.putString("Player", safe(playerEntry.getKey()));
                playerTag.putInt("Count", playerEntry.getValue());
                playerList.add(playerTag);
            }
            goalTag.put("Players", playerList);
            progressList.add(goalTag);
        }
        tag.put("GoalProgress", progressList);
        return tag;
    }

    private static ChronicleSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ChronicleSavedData data = new ChronicleSavedData();
        try {
            ListTag list = tag.getList("Events", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                ChronicleEntryType type = parseEntryType(e.getString("Type"));
                ChronicleScope scope = parseScope(e.getString("Scope"));
                ChronicleEvent event = new ChronicleEvent(
                        e.getString("Id"),
                        type,
                        scope,
                        e.getLong("DayIndex"),
                        e.getString("Title"),
                        e.getString("Details"),
                        e.getString("ActorName"),
                        e.getString("ActorUuid"),
                        e.getString("SourceId"),
                        e.getString("IconItemId")
                );
                data.events.add(event);
            }

            ListTag progressList = tag.getList("GoalProgress", Tag.TAG_COMPOUND);
            for (int i = 0; i < progressList.size(); i++) {
                CompoundTag goalTag = progressList.getCompound(i);
                String goalId = goalTag.getString("GoalId");
                ListTag playerList = goalTag.getList("Players", Tag.TAG_COMPOUND);
                java.util.Map<String, Integer> perGoal = data.goalProgress.computeIfAbsent(goalId, k -> new java.util.HashMap<>());
                for (int j = 0; j < playerList.size(); j++) {
                    CompoundTag playerTag = playerList.getCompound(j);
                    String player = playerTag.getString("Player");
                    int count = playerTag.getInt("Count");
                    if (!player.isBlank()) {
                        perGoal.put(player, count);
                    }
                }
            }
        } catch (Throwable t) {
            LOG.error("[ChronicleSavedData] Failed to load events; starting empty", t);
        }
        return data;
    }

    private static ChronicleEntryType parseEntryType(String raw) {
        try {
            return ChronicleEntryType.valueOf(raw);
        } catch (Throwable t) {
            return ChronicleEntryType.ADVANCEMENT;
        }
    }

    private static ChronicleScope parseScope(String raw) {
        try {
            return ChronicleScope.valueOf(raw);
        } catch (Throwable t) {
            return ChronicleScope.SERVER;
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
