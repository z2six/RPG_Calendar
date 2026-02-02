package org.z2six.rpgcalendar.chronicle.server;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import org.z2six.rpgcalendar.chronicle.ChronicleEntryType;
import org.z2six.rpgcalendar.chronicle.ChronicleScope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ChronicleSavedData extends SavedData {

    private static final Logger LOG = LogUtils.getLogger();
    private static final String DATA_NAME = "rpgcalendar_chronicle";

    private final List<ChronicleEvent> events = new ArrayList<>();

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
