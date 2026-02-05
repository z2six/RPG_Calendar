package org.z2six.rpgtimeline.network;

import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;
import org.z2six.rpgtimeline.Constants;
import org.z2six.rpgtimeline.chronicle.ChronicleDetail;
import org.z2six.rpgtimeline.chronicle.ChronicleEntry;
import org.z2six.rpgtimeline.chronicle.ChronicleEntryType;
import org.z2six.rpgtimeline.chronicle.ChronicleScope;
import org.z2six.rpgtimeline.chronicle.ChronicleTimeframe;
import org.z2six.rpgtimeline.chronicle.HallOfFameEntry;
import org.z2six.rpgtimeline.chronicle.server.ChronicleService;
import org.z2six.rpgtimeline.chronicle.server.ChronicleTimeframeService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Chronicle timeline payloads (Forge 1.20.1).
 */
public final class ChroniclePayloads {

    private static final Logger LOG = LogUtils.getLogger();

    private static final String PROTOCOL_VERSION = "4";

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Constants.MOD_ID, "chronicle"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static boolean REGISTERED = false;

    private ChroniclePayloads() {
        // no-op
    }

    // ---------------------------------------------------------------------
    // Registration hook
    // ---------------------------------------------------------------------

    public static void register() {
        if (REGISTERED) {
            return;
        }
        try {
            int id = 0;
            CHANNEL.registerMessage(id++, RequestChroniclePayload.class,
                    ChroniclePayloads::encodeRequestChronicle,
                    ChroniclePayloads::decodeRequestChronicle,
                    ChroniclePayloads::handleRequestChronicle);

            CHANNEL.registerMessage(id++, ChronicleSyncPayload.class,
                    ChroniclePayloads::encodeChronicleSync,
                    ChroniclePayloads::decodeChronicleSync,
                    ChroniclePayloads::handleChronicleSync);

            CHANNEL.registerMessage(id++, AddAdminEventPayload.class,
                    ChroniclePayloads::encodeAddAdminEvent,
                    ChroniclePayloads::decodeAddAdminEvent,
                    ChroniclePayloads::handleAddAdminEvent);

            CHANNEL.registerMessage(id++, RequestHallOfFamePayload.class,
                    ChroniclePayloads::encodeRequestHallOfFame,
                    ChroniclePayloads::decodeRequestHallOfFame,
                    ChroniclePayloads::handleRequestHallOfFame);

            CHANNEL.registerMessage(id++, HallOfFameSyncPayload.class,
                    ChroniclePayloads::encodeHallOfFameSync,
                    ChroniclePayloads::decodeHallOfFameSync,
                    ChroniclePayloads::handleHallOfFameSync);

            CHANNEL.registerMessage(id++, RequestHallOfFameDetailPayload.class,
                    ChroniclePayloads::encodeRequestHallOfFameDetail,
                    ChroniclePayloads::decodeRequestHallOfFameDetail,
                    ChroniclePayloads::handleRequestHallOfFameDetail);

            CHANNEL.registerMessage(id++, HallOfFameDetailPayload.class,
                    ChroniclePayloads::encodeHallOfFameDetail,
                    ChroniclePayloads::decodeHallOfFameDetail,
                    ChroniclePayloads::handleHallOfFameDetail);

            REGISTERED = true;
            LOG.debug("[ChroniclePayloads] Registered chronicle payloads OK (protocol={})", PROTOCOL_VERSION);
        } catch (Throwable t) {
            LOG.error("[ChroniclePayloads] register() failed safely", t);
        }
    }

    public static void sendToServer(Object payload) {
        CHANNEL.sendToServer(payload);
    }

    // ---------------------------------------------------------------------
    // Client-side cache
    // ---------------------------------------------------------------------

    public static final class ClientState {
        private static volatile boolean hasSynced = false;
        private static List<ChronicleEntry> serverEntries = List.of();
        private static List<ChronicleEntry> personalEntries = List.of();
        private static List<ChronicleTimeframe> serverTimeframes = List.of();
        private static List<ChronicleTimeframe> personalTimeframes = List.of();
        private static List<HallOfFameEntry> hallOfFameEntries = List.of();
        private static final java.util.Map<String, List<ChronicleEntry>> hallOfFameDetails = new java.util.HashMap<>();

        private ClientState() {
            // no-op
        }

        public static boolean hasSynced() {
            return hasSynced;
        }

        public static List<ChronicleEntry> getServerEntries() {
            return serverEntries;
        }

        public static List<ChronicleEntry> getPersonalEntries() {
            return personalEntries;
        }

        public static List<ChronicleTimeframe> getServerTimeframes() {
            return serverTimeframes;
        }

        public static List<ChronicleTimeframe> getPersonalTimeframes() {
            return personalTimeframes;
        }

        public static List<HallOfFameEntry> getHallOfFameEntries() {
            return hallOfFameEntries;
        }

        public static List<ChronicleEntry> getHallOfFameDetails(String playerUuid) {
            if (playerUuid == null) {
                return List.of();
            }
            return hallOfFameDetails.getOrDefault(playerUuid, List.of());
        }

        private static void applyFromServer(
                List<ChronicleEntry> server,
                List<ChronicleEntry> personal,
                List<ChronicleTimeframe> serverFrames,
                List<ChronicleTimeframe> personalFrames
        ) {
            serverEntries = server == null ? List.of() : server;
            personalEntries = personal == null ? List.of() : personal;
            serverTimeframes = serverFrames == null ? List.of() : serverFrames;
            personalTimeframes = personalFrames == null ? List.of() : personalFrames;
            hasSynced = true;
            LOG.debug("[ChroniclePayloads.ClientState] Applied chronicle sync (server={}, personal={})",
                    serverEntries.size(), personalEntries.size());
        }

        private static void applyHallOfFame(List<HallOfFameEntry> entries) {
            hallOfFameEntries = entries == null ? List.of() : entries;
            LOG.debug("[ChroniclePayloads.ClientState] Applied hall of fame sync (entries={})", hallOfFameEntries.size());
        }

        private static void applyHallOfFameDetail(String playerUuid, List<ChronicleEntry> entries) {
            if (playerUuid == null) {
                return;
            }
            hallOfFameDetails.put(playerUuid, entries == null ? List.of() : entries);
            LOG.debug("[ChroniclePayloads.ClientState] Applied hall of fame detail sync (player={})", playerUuid);
        }

        public static void clear() {
            hasSynced = false;
            serverEntries = List.of();
            personalEntries = List.of();
            serverTimeframes = List.of();
            personalTimeframes = List.of();
            hallOfFameEntries = List.of();
            hallOfFameDetails.clear();
            LOG.debug("[ChroniclePayloads.ClientState] Cleared client cache");
        }
    }

    // ---------------------------------------------------------------------
    // Payload definitions
    // ---------------------------------------------------------------------

    public record RequestChroniclePayload() {
    }

    public record ChronicleSyncPayload(
            List<ChronicleEntry> serverEntries,
            List<ChronicleEntry> personalEntries,
            List<ChronicleTimeframe> serverTimeframes,
            List<ChronicleTimeframe> personalTimeframes
    ) {
    }

    public record RequestHallOfFamePayload() {
    }

    public record HallOfFameSyncPayload(List<HallOfFameEntry> entries) {
    }

    public record RequestHallOfFameDetailPayload(String playerUuid) {
    }

    public record HallOfFameDetailPayload(String playerUuid, List<ChronicleEntry> entries) {
    }

    public record AddAdminEventPayload(String scope, String title, String details, long dayIndex) {
    }

    // ---------------------------------------------------------------------
    // Server-side send helpers
    // ---------------------------------------------------------------------

    public static void sendFullSyncToPlayer(ServerPlayer player) {
        try {
            if (player == null) return;
            ServerLevel level = player.serverLevel();
            if (level == null) return;

            List<ChronicleEntry> serverEntries = ChronicleService.buildServerTimeline(level.getServer());
            List<ChronicleEntry> personalEntries = ChronicleService.buildPersonalTimeline(level.getServer(), player.getUUID().toString());
            List<ChronicleTimeframe> serverFrames = ChronicleTimeframeService.buildServerTimeframes(level.getServer());
            List<ChronicleTimeframe> personalFrames = ChronicleTimeframeService.buildPersonalTimeframes(level.getServer(), player.getUUID().toString());

            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new ChronicleSyncPayload(serverEntries, personalEntries, serverFrames, personalFrames));
        } catch (Throwable t) {
            LOG.error("[ChroniclePayloads] sendFullSyncToPlayer failed safely", t);
        }
    }

    public static void broadcastFullSync(MinecraftServer server) {
        try {
            if (server == null) return;
            List<ChronicleEntry> serverEntries = ChronicleService.buildServerTimeline(server);
            List<ChronicleTimeframe> serverFrames = ChronicleTimeframeService.buildServerTimeframes(server);

            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            for (ServerPlayer sp : players) {
                List<ChronicleEntry> personalEntries = ChronicleService.buildPersonalTimeline(server, sp.getUUID().toString());
                List<ChronicleTimeframe> personalFrames = ChronicleTimeframeService.buildPersonalTimeframes(server, sp.getUUID().toString());
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                        new ChronicleSyncPayload(serverEntries, personalEntries, serverFrames, personalFrames));
            }
        } catch (Throwable t) {
            LOG.error("[ChroniclePayloads] broadcastFullSync failed safely", t);
        }
    }

    public static void sendHallOfFameToPlayer(ServerPlayer player) {
        try {
            if (player == null) return;
            List<HallOfFameEntry> entries = ChronicleService.buildHallOfFame(player.getServer());
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new HallOfFameSyncPayload(entries));
        } catch (Throwable t) {
            LOG.error("[ChroniclePayloads] sendHallOfFameToPlayer failed safely", t);
        }
    }

    public static void broadcastHallOfFame(MinecraftServer server) {
        try {
            if (server == null) return;
            List<HallOfFameEntry> entries = ChronicleService.buildHallOfFame(server);
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new HallOfFameSyncPayload(entries));
            }
        } catch (Throwable t) {
            LOG.error("[ChroniclePayloads] broadcastHallOfFame failed safely", t);
        }
    }

    // ---------------------------------------------------------------------
    // Handlers
    // ---------------------------------------------------------------------

    private static void handleRequestChronicle(RequestChroniclePayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context ctx = contextSupplier.get();
        ctx.enqueueWork(() -> {
            try {
                ServerPlayer sp = ctx.getSender();
                if (sp == null) {
                    return;
                }
                sendFullSyncToPlayer(sp);
            } catch (Throwable t) {
                LOG.error("[ChroniclePayloads] handleRequestChronicle work failed safely", t);
            }
        });
        ctx.setPacketHandled(true);
    }

    private static void handleChronicleSync(ChronicleSyncPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context ctx = contextSupplier.get();
        ctx.enqueueWork(() -> {
            try {
                ClientState.applyFromServer(
                        payload.serverEntries(),
                        payload.personalEntries(),
                        payload.serverTimeframes(),
                        payload.personalTimeframes()
                );
            } catch (Throwable t) {
                LOG.error("[ChroniclePayloads] handleChronicleSync work failed safely", t);
            }
        });
        ctx.setPacketHandled(true);
    }

    private static void handleAddAdminEvent(AddAdminEventPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context ctx = contextSupplier.get();
        ctx.enqueueWork(() -> {
            try {
                ServerPlayer sp = ctx.getSender();
                if (sp == null) {
                    return;
                }
                if (!sp.hasPermissions(4)) {
                    return;
                }
                ChronicleScope scope = ChronicleScope.SERVER;
                try {
                    scope = ChronicleScope.valueOf(payload.scope());
                } catch (Throwable ignored) {
                }
                ChronicleService.addAdminEvent(sp, scope, payload.title(), payload.details(), payload.dayIndex());
            } catch (Throwable t) {
                LOG.error("[ChroniclePayloads] handleAddAdminEvent work failed safely", t);
            }
        });
        ctx.setPacketHandled(true);
    }

    private static void handleRequestHallOfFame(RequestHallOfFamePayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context ctx = contextSupplier.get();
        ctx.enqueueWork(() -> {
            try {
                ServerPlayer sp = ctx.getSender();
                if (sp == null) {
                    return;
                }
                sendHallOfFameToPlayer(sp);
            } catch (Throwable t) {
                LOG.error("[ChroniclePayloads] handleRequestHallOfFame work failed safely", t);
            }
        });
        ctx.setPacketHandled(true);
    }

    private static void handleHallOfFameSync(HallOfFameSyncPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context ctx = contextSupplier.get();
        ctx.enqueueWork(() -> {
            try {
                ClientState.applyHallOfFame(payload.entries());
            } catch (Throwable t) {
                LOG.error("[ChroniclePayloads] handleHallOfFameSync work failed safely", t);
            }
        });
        ctx.setPacketHandled(true);
    }

    private static void handleRequestHallOfFameDetail(RequestHallOfFameDetailPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context ctx = contextSupplier.get();
        ctx.enqueueWork(() -> {
            try {
                ServerPlayer sp = ctx.getSender();
                if (sp == null) {
                    return;
                }
                List<ChronicleEntry> entries = ChronicleService.buildWorldFirstsForPlayer(
                        sp.getServer(),
                        payload.playerUuid()
                );
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new HallOfFameDetailPayload(payload.playerUuid(), entries));
            } catch (Throwable t) {
                LOG.error("[ChroniclePayloads] handleRequestHallOfFameDetail work failed safely", t);
            }
        });
        ctx.setPacketHandled(true);
    }

    private static void handleHallOfFameDetail(HallOfFameDetailPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context ctx = contextSupplier.get();
        ctx.enqueueWork(() -> {
            try {
                ClientState.applyHallOfFameDetail(payload.playerUuid(), payload.entries());
            } catch (Throwable t) {
                LOG.error("[ChroniclePayloads] handleHallOfFameDetail work failed safely", t);
            }
        });
        ctx.setPacketHandled(true);
    }

    // ---------------------------------------------------------------------
    // Encoding/decoding
    // ---------------------------------------------------------------------

    private static void encodeRequestChronicle(RequestChroniclePayload payload, FriendlyByteBuf buf) {
        // no-op
    }

    private static RequestChroniclePayload decodeRequestChronicle(FriendlyByteBuf buf) {
        return new RequestChroniclePayload();
    }

    private static void encodeChronicleSync(ChronicleSyncPayload payload, FriendlyByteBuf buf) {
        writeList(buf, payload.serverEntries(), ChroniclePayloads::writeChronicleEntry);
        writeList(buf, payload.personalEntries(), ChroniclePayloads::writeChronicleEntry);
        writeList(buf, payload.serverTimeframes(), ChroniclePayloads::writeChronicleTimeframe);
        writeList(buf, payload.personalTimeframes(), ChroniclePayloads::writeChronicleTimeframe);
    }

    private static ChronicleSyncPayload decodeChronicleSync(FriendlyByteBuf buf) {
        List<ChronicleEntry> serverEntries = readList(buf, ChroniclePayloads::readChronicleEntry);
        List<ChronicleEntry> personalEntries = readList(buf, ChroniclePayloads::readChronicleEntry);
        List<ChronicleTimeframe> serverFrames = readList(buf, ChroniclePayloads::readChronicleTimeframe);
        List<ChronicleTimeframe> personalFrames = readList(buf, ChroniclePayloads::readChronicleTimeframe);
        return new ChronicleSyncPayload(serverEntries, personalEntries, serverFrames, personalFrames);
    }

    private static void encodeAddAdminEvent(AddAdminEventPayload payload, FriendlyByteBuf buf) {
        buf.writeUtf(safe(payload.scope()));
        buf.writeUtf(safe(payload.title()));
        buf.writeUtf(safe(payload.details()));
        buf.writeLong(payload.dayIndex());
    }

    private static AddAdminEventPayload decodeAddAdminEvent(FriendlyByteBuf buf) {
        String scope = buf.readUtf();
        String title = buf.readUtf();
        String details = buf.readUtf();
        long dayIndex = buf.readLong();
        return new AddAdminEventPayload(scope, title, details, dayIndex);
    }

    private static void encodeRequestHallOfFame(RequestHallOfFamePayload payload, FriendlyByteBuf buf) {
        // no-op
    }

    private static RequestHallOfFamePayload decodeRequestHallOfFame(FriendlyByteBuf buf) {
        return new RequestHallOfFamePayload();
    }

    private static void encodeHallOfFameSync(HallOfFameSyncPayload payload, FriendlyByteBuf buf) {
        writeList(buf, payload.entries(), ChroniclePayloads::writeHallOfFameEntry);
    }

    private static HallOfFameSyncPayload decodeHallOfFameSync(FriendlyByteBuf buf) {
        List<HallOfFameEntry> entries = readList(buf, ChroniclePayloads::readHallOfFameEntry);
        return new HallOfFameSyncPayload(entries);
    }

    private static void encodeRequestHallOfFameDetail(RequestHallOfFameDetailPayload payload, FriendlyByteBuf buf) {
        buf.writeUtf(safe(payload.playerUuid()));
    }

    private static RequestHallOfFameDetailPayload decodeRequestHallOfFameDetail(FriendlyByteBuf buf) {
        return new RequestHallOfFameDetailPayload(buf.readUtf());
    }

    private static void encodeHallOfFameDetail(HallOfFameDetailPayload payload, FriendlyByteBuf buf) {
        buf.writeUtf(safe(payload.playerUuid()));
        writeList(buf, payload.entries(), ChroniclePayloads::writeChronicleEntry);
    }

    private static HallOfFameDetailPayload decodeHallOfFameDetail(FriendlyByteBuf buf) {
        String playerUuid = buf.readUtf();
        List<ChronicleEntry> entries = readList(buf, ChroniclePayloads::readChronicleEntry);
        return new HallOfFameDetailPayload(playerUuid, entries);
    }

    private static void writeChronicleEntry(FriendlyByteBuf buf, ChronicleEntry entry) {
        buf.writeUtf(safe(entry.id()));
        buf.writeUtf(safe(entry.type().name()));
        buf.writeUtf(safe(entry.scope().name()));
        buf.writeLong(entry.dayIndex());
        buf.writeUtf(safe(entry.title()));
        buf.writeUtf(safe(entry.details()));
        buf.writeUtf(safe(entry.sourceId()));
        buf.writeUtf(safe(entry.actorName()));
        buf.writeUtf(safe(entry.actorUuid()));
        buf.writeBoolean(entry.highlight());
        buf.writeBoolean(entry.summary());
        buf.writeUtf(safe(entry.iconItemId()));
        writeList(buf, entry.drilldown(), ChroniclePayloads::writeChronicleDetail);
    }

    private static ChronicleEntry readChronicleEntry(FriendlyByteBuf buf) {
        String id = buf.readUtf();
        ChronicleEntryType type = ChronicleEntryType.valueOf(buf.readUtf());
        ChronicleScope scope = ChronicleScope.valueOf(buf.readUtf());
        long dayIndex = buf.readLong();
        String title = buf.readUtf();
        String details = buf.readUtf();
        String sourceId = buf.readUtf();
        String actorName = buf.readUtf();
        String actorUuid = buf.readUtf();
        boolean highlight = buf.readBoolean();
        boolean summary = buf.readBoolean();
        String iconItemId = buf.readUtf();
        List<ChronicleDetail> drilldown = readList(buf, ChroniclePayloads::readChronicleDetail);
        return new ChronicleEntry(
                id,
                type,
                scope,
                dayIndex,
                title,
                details,
                sourceId,
                actorName,
                actorUuid,
                highlight,
                summary,
                iconItemId,
                drilldown
        );
    }

    private static void writeChronicleDetail(FriendlyByteBuf buf, ChronicleDetail detail) {
        buf.writeUtf(safe(detail.title()));
        buf.writeUtf(safe(detail.description()));
        buf.writeUtf(safe(detail.iconItemId()));
        buf.writeUtf(safe(detail.sourceId()));
        buf.writeLong(detail.dayIndex());
        buf.writeUtf(safe(detail.actorUuid()));
        buf.writeUtf(safe(detail.actorName()));
    }

    private static ChronicleDetail readChronicleDetail(FriendlyByteBuf buf) {
        String title = buf.readUtf();
        String description = buf.readUtf();
        String iconItemId = buf.readUtf();
        String sourceId = buf.readUtf();
        long dayIndex = buf.readLong();
        String actorUuid = buf.readUtf();
        String actorName = buf.readUtf();
        return new ChronicleDetail(title, description, iconItemId, sourceId, dayIndex, actorUuid, actorName);
    }

    private static void writeChronicleTimeframe(FriendlyByteBuf buf, ChronicleTimeframe timeframe) {
        buf.writeFloat(timeframe.startDay());
        buf.writeFloat(timeframe.endDay());
        buf.writeInt(timeframe.priority());
        buf.writeInt(timeframe.layer());
        buf.writeInt(timeframe.renderMode());
        buf.writeUtf(safe(timeframe.renderId()));
        buf.writeFloat(timeframe.alpha());
    }

    private static ChronicleTimeframe readChronicleTimeframe(FriendlyByteBuf buf) {
        float start = buf.readFloat();
        float end = buf.readFloat();
        int priority = buf.readInt();
        int layer = buf.readInt();
        int renderMode = buf.readInt();
        String renderId = buf.readUtf();
        float alpha = buf.readFloat();
        return new ChronicleTimeframe(start, end, priority, layer, renderMode, renderId, alpha);
    }

    private static void writeHallOfFameEntry(FriendlyByteBuf buf, HallOfFameEntry entry) {
        buf.writeUtf(safe(entry.playerUuid()));
        buf.writeUtf(safe(entry.playerName()));
        buf.writeInt(entry.worldFirstCount());
    }

    private static HallOfFameEntry readHallOfFameEntry(FriendlyByteBuf buf) {
        String uuid = buf.readUtf();
        String name = buf.readUtf();
        int count = buf.readInt();
        return new HallOfFameEntry(uuid, name, count);
    }

    private static <T> void writeList(FriendlyByteBuf buf, List<T> list, BiConsumer<FriendlyByteBuf, T> writer) {
        if (list == null) {
            buf.writeVarInt(0);
            return;
        }
        buf.writeVarInt(list.size());
        for (T value : list) {
            writer.accept(buf, value);
        }
    }

    private static <T> List<T> readList(FriendlyByteBuf buf, Function<FriendlyByteBuf, T> reader) {
        int size = buf.readVarInt();
        if (size <= 0) {
            return Collections.emptyList();
        }
        List<T> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(reader.apply(buf));
        }
        return list;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
