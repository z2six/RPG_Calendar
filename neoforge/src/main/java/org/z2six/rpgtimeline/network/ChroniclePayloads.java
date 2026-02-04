package org.z2six.rpgtimeline.network;

import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
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

/**
 * Chronicle timeline payloads.
 */
public final class ChroniclePayloads {

    private static final Logger LOG = LogUtils.getLogger();

    private static final String PROTOCOL_VERSION = "4";

    private ChroniclePayloads() {
        // no-op
    }

    // ---------------------------------------------------------------------
    // Registration hook
    // ---------------------------------------------------------------------

    public static void register(IEventBus modBus) {
        try {
            modBus.addListener(ChroniclePayloads::onRegisterPayloadHandlers);
            LOG.debug("[ChroniclePayloads] Hooked RegisterPayloadHandlersEvent listener");
        } catch (Throwable t) {
            LOG.error("[ChroniclePayloads] register() failed safely", t);
        }
    }

    private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        try {
            PayloadRegistrar registrar = event.registrar(Constants.MOD_ID).versioned(PROTOCOL_VERSION);

            registrar.playToServer(
                    RequestChroniclePayload.TYPE,
                    RequestChroniclePayload.STREAM_CODEC,
                    ChroniclePayloads::handleRequestChronicle
            );

            registrar.playToClient(
                    ChronicleSyncPayload.TYPE,
                    ChronicleSyncPayload.STREAM_CODEC,
                    ChroniclePayloads::handleChronicleSync
            );

            registrar.playToServer(
                    AddAdminEventPayload.TYPE,
                    AddAdminEventPayload.STREAM_CODEC,
                    ChroniclePayloads::handleAddAdminEvent
            );

            registrar.playToServer(
                    RequestHallOfFamePayload.TYPE,
                    RequestHallOfFamePayload.STREAM_CODEC,
                    ChroniclePayloads::handleRequestHallOfFame
            );

            registrar.playToClient(
                    HallOfFameSyncPayload.TYPE,
                    HallOfFameSyncPayload.STREAM_CODEC,
                    ChroniclePayloads::handleHallOfFameSync
            );

            registrar.playToServer(
                    RequestHallOfFameDetailPayload.TYPE,
                    RequestHallOfFameDetailPayload.STREAM_CODEC,
                    ChroniclePayloads::handleRequestHallOfFameDetail
            );

            registrar.playToClient(
                    HallOfFameDetailPayload.TYPE,
                    HallOfFameDetailPayload.STREAM_CODEC,
                    ChroniclePayloads::handleHallOfFameDetail
            );

            LOG.debug("[ChroniclePayloads] Registered chronicle payloads OK (protocol={})", PROTOCOL_VERSION);
        } catch (Throwable t) {
            LOG.error("[ChroniclePayloads] onRegisterPayloadHandlers failed safely", t);
        }
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

    public record RequestChroniclePayload() implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final ResourceLocation ID =
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "request_chronicle_v1");
        public static final Type<RequestChroniclePayload> TYPE = new Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestChroniclePayload> STREAM_CODEC =
                StreamCodec.unit(new RequestChroniclePayload());

        @Override
        public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ChronicleSyncPayload(
            List<ChronicleEntry> serverEntries,
            List<ChronicleEntry> personalEntries,
            List<ChronicleTimeframe> serverTimeframes,
            List<ChronicleTimeframe> personalTimeframes
    )
            implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final ResourceLocation ID =
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "chronicle_sync_v1");
        public static final Type<ChronicleSyncPayload> TYPE = new Type<>(ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, ChronicleSyncPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.collection(ArrayList::new, ChronicleEntryCodec.STREAM_CODEC), ChronicleSyncPayload::serverEntries,
                        ByteBufCodecs.collection(ArrayList::new, ChronicleEntryCodec.STREAM_CODEC), ChronicleSyncPayload::personalEntries,
                        ByteBufCodecs.collection(ArrayList::new, ChronicleTimeframeCodec.STREAM_CODEC), ChronicleSyncPayload::serverTimeframes,
                        ByteBufCodecs.collection(ArrayList::new, ChronicleTimeframeCodec.STREAM_CODEC), ChronicleSyncPayload::personalTimeframes,
                        ChronicleSyncPayload::new
                );

        @Override
        public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RequestHallOfFamePayload() implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final ResourceLocation ID =
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "request_hall_of_fame_v1");
        public static final Type<RequestHallOfFamePayload> TYPE = new Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestHallOfFamePayload> STREAM_CODEC =
                StreamCodec.unit(new RequestHallOfFamePayload());

        @Override
        public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record HallOfFameSyncPayload(List<HallOfFameEntry> entries)
            implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final ResourceLocation ID =
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "hall_of_fame_sync_v1");
        public static final Type<HallOfFameSyncPayload> TYPE = new Type<>(ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, HallOfFameSyncPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.collection(ArrayList::new, HallOfFameEntryCodec.STREAM_CODEC), HallOfFameSyncPayload::entries,
                        HallOfFameSyncPayload::new
                );

        @Override
        public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record RequestHallOfFameDetailPayload(String playerUuid)
            implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final ResourceLocation ID =
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "request_hall_of_fame_detail_v1");
        public static final Type<RequestHallOfFameDetailPayload> TYPE = new Type<>(ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, RequestHallOfFameDetailPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, RequestHallOfFameDetailPayload::playerUuid,
                        RequestHallOfFameDetailPayload::new
                );

        @Override
        public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record HallOfFameDetailPayload(String playerUuid, List<ChronicleEntry> entries)
            implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final ResourceLocation ID =
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "hall_of_fame_detail_v1");
        public static final Type<HallOfFameDetailPayload> TYPE = new Type<>(ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, HallOfFameDetailPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, HallOfFameDetailPayload::playerUuid,
                        ByteBufCodecs.collection(ArrayList::new, ChronicleEntryCodec.STREAM_CODEC), HallOfFameDetailPayload::entries,
                        HallOfFameDetailPayload::new
                );

        @Override
        public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record AddAdminEventPayload(String scope, String title, String details, long dayIndex)
            implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final ResourceLocation ID =
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "add_admin_event_v1");
        public static final Type<AddAdminEventPayload> TYPE = new Type<>(ID);
        public static final StreamCodec<RegistryFriendlyByteBuf, AddAdminEventPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, AddAdminEventPayload::scope,
                        ByteBufCodecs.STRING_UTF8, AddAdminEventPayload::title,
                        ByteBufCodecs.STRING_UTF8, AddAdminEventPayload::details,
                        ByteBufCodecs.VAR_LONG, AddAdminEventPayload::dayIndex,
                        AddAdminEventPayload::new
                );

        @Override
        public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
            return TYPE;
        }
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

            PacketDistributor.sendToPlayer(player, new ChronicleSyncPayload(serverEntries, personalEntries, serverFrames, personalFrames));
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
                PacketDistributor.sendToPlayer(sp, new ChronicleSyncPayload(serverEntries, personalEntries, serverFrames, personalFrames));
            }
        } catch (Throwable t) {
            LOG.error("[ChroniclePayloads] broadcastFullSync failed safely", t);
        }
    }

    public static void sendHallOfFameToPlayer(ServerPlayer player) {
        try {
            if (player == null) return;
            List<HallOfFameEntry> entries = ChronicleService.buildHallOfFame(player.getServer());
            PacketDistributor.sendToPlayer(player, new HallOfFameSyncPayload(entries));
        } catch (Throwable t) {
            LOG.error("[ChroniclePayloads] sendHallOfFameToPlayer failed safely", t);
        }
    }

    public static void broadcastHallOfFame(MinecraftServer server) {
        try {
            if (server == null) return;
            List<HallOfFameEntry> entries = ChronicleService.buildHallOfFame(server);
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                PacketDistributor.sendToPlayer(sp, new HallOfFameSyncPayload(entries));
            }
        } catch (Throwable t) {
            LOG.error("[ChroniclePayloads] broadcastHallOfFame failed safely", t);
        }
    }

    // ---------------------------------------------------------------------
    // Handlers
    // ---------------------------------------------------------------------

    private static void handleRequestChronicle(RequestChroniclePayload payload, IPayloadContext context) {
        try {
            context.enqueueWork(() -> {
                try {
                    if (!(context.player() instanceof ServerPlayer sp)) {
                        return;
                    }
                    sendFullSyncToPlayer(sp);
                } catch (Throwable t) {
                    LOG.error("[ChroniclePayloads] handleRequestChronicle work failed safely", t);
                }
            });
        } catch (Throwable t) {
            LOG.error("[ChroniclePayloads] handleRequestChronicle failed safely", t);
        }
    }

    private static void handleChronicleSync(ChronicleSyncPayload payload, IPayloadContext context) {
        try {
            context.enqueueWork(() -> {
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
        } catch (Throwable t) {
            LOG.error("[ChroniclePayloads] handleChronicleSync failed safely", t);
        }
    }

    private static void handleAddAdminEvent(AddAdminEventPayload payload, IPayloadContext context) {
        try {
            context.enqueueWork(() -> {
                try {
                    if (!(context.player() instanceof ServerPlayer sp)) {
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
        } catch (Throwable t) {
            LOG.error("[ChroniclePayloads] handleAddAdminEvent failed safely", t);
        }
    }

    private static void handleRequestHallOfFame(RequestHallOfFamePayload payload, IPayloadContext context) {
        try {
            context.enqueueWork(() -> {
                try {
                    if (!(context.player() instanceof ServerPlayer sp)) {
                        return;
                    }
                    sendHallOfFameToPlayer(sp);
                } catch (Throwable t) {
                    LOG.error("[ChroniclePayloads] handleRequestHallOfFame work failed safely", t);
                }
            });
        } catch (Throwable t) {
            LOG.error("[ChroniclePayloads] handleRequestHallOfFame failed safely", t);
        }
    }

    private static void handleHallOfFameSync(HallOfFameSyncPayload payload, IPayloadContext context) {
        try {
            context.enqueueWork(() -> {
                try {
                    ClientState.applyHallOfFame(payload.entries());
                } catch (Throwable t) {
                    LOG.error("[ChroniclePayloads] handleHallOfFameSync work failed safely", t);
                }
            });
        } catch (Throwable t) {
            LOG.error("[ChroniclePayloads] handleHallOfFameSync failed safely", t);
        }
    }

    private static void handleRequestHallOfFameDetail(RequestHallOfFameDetailPayload payload, IPayloadContext context) {
        try {
            context.enqueueWork(() -> {
                try {
                    if (!(context.player() instanceof ServerPlayer sp)) {
                        return;
                    }
                    List<ChronicleEntry> entries = ChronicleService.buildWorldFirstsForPlayer(
                            sp.getServer(),
                            payload.playerUuid()
                    );
                    PacketDistributor.sendToPlayer(sp, new HallOfFameDetailPayload(payload.playerUuid(), entries));
                } catch (Throwable t) {
                    LOG.error("[ChroniclePayloads] handleRequestHallOfFameDetail work failed safely", t);
                }
            });
        } catch (Throwable t) {
            LOG.error("[ChroniclePayloads] handleRequestHallOfFameDetail failed safely", t);
        }
    }

    private static void handleHallOfFameDetail(HallOfFameDetailPayload payload, IPayloadContext context) {
        try {
            context.enqueueWork(() -> {
                try {
                    ClientState.applyHallOfFameDetail(payload.playerUuid(), payload.entries());
                } catch (Throwable t) {
                    LOG.error("[ChroniclePayloads] handleHallOfFameDetail work failed safely", t);
                }
            });
        } catch (Throwable t) {
            LOG.error("[ChroniclePayloads] handleHallOfFameDetail failed safely", t);
        }
    }

    // ---------------------------------------------------------------------
    // Codecs
    // ---------------------------------------------------------------------

    private static final class ChronicleEntryCodec {
        private static final StreamCodec<RegistryFriendlyByteBuf, ChronicleDetail> DETAIL_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, ChronicleDetail::title,
                        ByteBufCodecs.STRING_UTF8, ChronicleDetail::description,
                        ByteBufCodecs.STRING_UTF8, ChronicleDetail::iconItemId,
                        ByteBufCodecs.VAR_LONG, ChronicleDetail::dayIndex,
                        ByteBufCodecs.STRING_UTF8, ChronicleDetail::actorUuid,
                        ByteBufCodecs.STRING_UTF8, ChronicleDetail::actorName,
                        ChronicleDetail::new
                );
        private static final StreamCodec<RegistryFriendlyByteBuf, List<ChronicleDetail>> DETAIL_LIST_CODEC =
                DETAIL_CODEC.apply(ByteBufCodecs.list());

        private static final StreamCodec<RegistryFriendlyByteBuf, ChronicleEntry> STREAM_CODEC =
                StreamCodec.of(
                        ChronicleEntryCodec::encodeEntry,
                        ChronicleEntryCodec::decodeEntry
                );

        private static void encodeEntry(RegistryFriendlyByteBuf buf, ChronicleEntry entry) {
            ByteBufCodecs.STRING_UTF8.encode(buf, safe(entry.id()));
            ByteBufCodecs.STRING_UTF8.encode(buf, safe(entry.type().name()));
            ByteBufCodecs.STRING_UTF8.encode(buf, safe(entry.scope().name()));
            ByteBufCodecs.VAR_LONG.encode(buf, entry.dayIndex());
            ByteBufCodecs.STRING_UTF8.encode(buf, safe(entry.title()));
            ByteBufCodecs.STRING_UTF8.encode(buf, safe(entry.details()));
            ByteBufCodecs.STRING_UTF8.encode(buf, safe(entry.actorName()));
            ByteBufCodecs.STRING_UTF8.encode(buf, safe(entry.actorUuid()));
            ByteBufCodecs.BOOL.encode(buf, entry.highlight());
            ByteBufCodecs.BOOL.encode(buf, entry.summary());
            ByteBufCodecs.STRING_UTF8.encode(buf, safe(entry.iconItemId()));
            List<ChronicleDetail> drilldown = entry.drilldown();
            if (drilldown == null) {
                drilldown = List.of();
            }
            DETAIL_LIST_CODEC.encode(buf, drilldown);
        }

        private static ChronicleEntry decodeEntry(RegistryFriendlyByteBuf buf) {
            String id = ByteBufCodecs.STRING_UTF8.decode(buf);
            String type = ByteBufCodecs.STRING_UTF8.decode(buf);
            String scope = ByteBufCodecs.STRING_UTF8.decode(buf);
            long dayIndex = ByteBufCodecs.VAR_LONG.decode(buf);
            String title = ByteBufCodecs.STRING_UTF8.decode(buf);
            String details = ByteBufCodecs.STRING_UTF8.decode(buf);
            String actorName = ByteBufCodecs.STRING_UTF8.decode(buf);
            String actorUuid = ByteBufCodecs.STRING_UTF8.decode(buf);
            boolean highlight = ByteBufCodecs.BOOL.decode(buf);
            boolean summary = ByteBufCodecs.BOOL.decode(buf);
            String iconItemId = ByteBufCodecs.STRING_UTF8.decode(buf);
            List<ChronicleDetail> drilldown = DETAIL_LIST_CODEC.decode(buf);
            return decodeEntry(
                    id,
                    type,
                    scope,
                    dayIndex,
                    title,
                    details,
                    actorName,
                    actorUuid,
                    highlight,
                    summary,
                    iconItemId,
                    drilldown
            );
        }

        private static ChronicleEntry decodeEntry(
                String id,
                String type,
                String scope,
                long dayIndex,
                String title,
                String details,
                String actorName,
                String actorUuid,
                boolean highlight,
                boolean summary,
                String iconItemId,
                List<ChronicleDetail> drilldown
        ) {
            ChronicleEntryType entryType;
            ChronicleScope entryScope;
            try {
                entryType = ChronicleEntryType.valueOf(type);
            } catch (Throwable t) {
                entryType = ChronicleEntryType.ADVANCEMENT;
            }
            try {
                entryScope = ChronicleScope.valueOf(scope);
            } catch (Throwable t) {
                entryScope = ChronicleScope.SERVER;
            }
            return new ChronicleEntry(
                    id,
                    entryType,
                    entryScope,
                    dayIndex,
                    title,
                    details,
                    actorName,
                    actorUuid,
                    highlight,
                    summary,
                    iconItemId,
                    drilldown == null ? Collections.emptyList() : drilldown
            );
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }
    }

    private static final class ChronicleTimeframeCodec {
        private static final StreamCodec<RegistryFriendlyByteBuf, ChronicleTimeframe> STREAM_CODEC =
                StreamCodec.of(
                        ChronicleTimeframeCodec::encode,
                        ChronicleTimeframeCodec::decode
                );

        private static void encode(RegistryFriendlyByteBuf buf, ChronicleTimeframe frame) {
            ByteBufCodecs.VAR_INT.encode(buf, Float.floatToIntBits(frame.startDay()));
            ByteBufCodecs.VAR_INT.encode(buf, Float.floatToIntBits(frame.endDay()));
            ByteBufCodecs.VAR_INT.encode(buf, frame.priority());
            ByteBufCodecs.VAR_INT.encode(buf, frame.layer());
            ByteBufCodecs.VAR_INT.encode(buf, frame.renderMode());
            ByteBufCodecs.STRING_UTF8.encode(buf, safe(frame.renderId()));
            ByteBufCodecs.VAR_INT.encode(buf, Float.floatToIntBits(frame.alpha()));
        }

        private static ChronicleTimeframe decode(RegistryFriendlyByteBuf buf) {
            float start = Float.intBitsToFloat(ByteBufCodecs.VAR_INT.decode(buf));
            float end = Float.intBitsToFloat(ByteBufCodecs.VAR_INT.decode(buf));
            int priority = ByteBufCodecs.VAR_INT.decode(buf);
            int layer = ByteBufCodecs.VAR_INT.decode(buf);
            int renderMode = ByteBufCodecs.VAR_INT.decode(buf);
            String renderId = ByteBufCodecs.STRING_UTF8.decode(buf);
            float alpha = Float.intBitsToFloat(ByteBufCodecs.VAR_INT.decode(buf));
            return new ChronicleTimeframe(start, end, priority, layer, renderMode, renderId, alpha);
        }

        private static String safe(String value) {
            return value == null ? "" : value;
        }
    }

    private static final class HallOfFameEntryCodec {
        private static final StreamCodec<RegistryFriendlyByteBuf, HallOfFameEntry> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, HallOfFameEntry::playerUuid,
                        ByteBufCodecs.STRING_UTF8, HallOfFameEntry::playerName,
                        ByteBufCodecs.VAR_INT, HallOfFameEntry::worldFirstCount,
                        HallOfFameEntry::new
                );
    }
}
