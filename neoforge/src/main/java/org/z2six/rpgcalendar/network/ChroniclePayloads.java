package org.z2six.rpgcalendar.network;

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
import org.z2six.rpgcalendar.Constants;
import org.z2six.rpgcalendar.chronicle.ChronicleDetail;
import org.z2six.rpgcalendar.chronicle.ChronicleEntry;
import org.z2six.rpgcalendar.chronicle.ChronicleEntryType;
import org.z2six.rpgcalendar.chronicle.ChronicleScope;
import org.z2six.rpgcalendar.chronicle.server.ChronicleService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Chronicle timeline payloads.
 */
public final class ChroniclePayloads {

    private static final Logger LOG = LogUtils.getLogger();

    private static final String PROTOCOL_VERSION = "1";

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

        private static void applyFromServer(List<ChronicleEntry> server, List<ChronicleEntry> personal) {
            serverEntries = server == null ? List.of() : server;
            personalEntries = personal == null ? List.of() : personal;
            hasSynced = true;
            LOG.debug("[ChroniclePayloads.ClientState] Applied chronicle sync (server={}, personal={})",
                    serverEntries.size(), personalEntries.size());
        }

        public static void clear() {
            hasSynced = false;
            serverEntries = List.of();
            personalEntries = List.of();
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

    public record ChronicleSyncPayload(List<ChronicleEntry> serverEntries, List<ChronicleEntry> personalEntries)
            implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final ResourceLocation ID =
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "chronicle_sync_v1");
        public static final Type<ChronicleSyncPayload> TYPE = new Type<>(ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, ChronicleSyncPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.collection(ArrayList::new, ChronicleEntryCodec.STREAM_CODEC), ChronicleSyncPayload::serverEntries,
                        ByteBufCodecs.collection(ArrayList::new, ChronicleEntryCodec.STREAM_CODEC), ChronicleSyncPayload::personalEntries,
                        ChronicleSyncPayload::new
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

            PacketDistributor.sendToPlayer(player, new ChronicleSyncPayload(serverEntries, personalEntries));
        } catch (Throwable t) {
            LOG.error("[ChroniclePayloads] sendFullSyncToPlayer failed safely", t);
        }
    }

    public static void broadcastFullSync(MinecraftServer server) {
        try {
            if (server == null) return;
            List<ChronicleEntry> serverEntries = ChronicleService.buildServerTimeline(server);

            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            for (ServerPlayer sp : players) {
                List<ChronicleEntry> personalEntries = ChronicleService.buildPersonalTimeline(server, sp.getUUID().toString());
                PacketDistributor.sendToPlayer(sp, new ChronicleSyncPayload(serverEntries, personalEntries));
            }
        } catch (Throwable t) {
            LOG.error("[ChroniclePayloads] broadcastFullSync failed safely", t);
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
                    ClientState.applyFromServer(payload.serverEntries(), payload.personalEntries());
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

    // ---------------------------------------------------------------------
    // Codecs
    // ---------------------------------------------------------------------

    private static final class ChronicleEntryCodec {
        private static final StreamCodec<RegistryFriendlyByteBuf, ChronicleDetail> DETAIL_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, ChronicleDetail::title,
                        ByteBufCodecs.STRING_UTF8, ChronicleDetail::subtitle,
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
}
