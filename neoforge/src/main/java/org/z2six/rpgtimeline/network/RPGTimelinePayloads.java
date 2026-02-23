package org.z2six.rpgtimeline.network;

import com.mojang.logging.LogUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.z2six.rpgtimeline.Constants;
import org.z2six.rpgtimeline.calendar.SeasonMonthMapping;
import org.z2six.rpgtimeline.config.RPGTimelineConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings sync payloads for RPG Calendar.
 */
public final class RPGTimelinePayloads {

    private static final Logger LOG = LogUtils.getLogger();

    /**
     * Bump if you change payload shapes. Must match client + server.
     */
    private static final String PROTOCOL_VERSION = "4";

    private RPGTimelinePayloads() {
        // no-op
    }

    // ---------------------------------------------------------------------
    // Registration hook
    // ---------------------------------------------------------------------

    public static void register(IEventBus modBus) {
        try {
            modBus.addListener(RPGTimelinePayloads::onRegisterPayloadHandlers);
            LOG.debug("[RPGTimelinePayloads] Hooked RegisterPayloadHandlersEvent listener");
        } catch (Throwable t) {
            LOG.error("[RPGTimelinePayloads] register() failed safely", t);
        }
    }

    private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        try {
            PayloadRegistrar registrar = event.registrar(Constants.MOD_ID).versioned(PROTOCOL_VERSION);

            registrar.playToClient(
                    ServerCalendarSettingsPayload.TYPE,
                    ServerCalendarSettingsPayload.STREAM_CODEC,
                    RPGTimelinePayloads::handleServerSettingsSync
            );

            LOG.debug("[RPGTimelinePayloads] Registered settings payloads OK (protocol={})", PROTOCOL_VERSION);
        } catch (Throwable t) {
            LOG.error("[RPGTimelinePayloads] onRegisterPayloadHandlers failed safely", t);
        }
    }

    // ---------------------------------------------------------------------
    // Client-side cache
    // ---------------------------------------------------------------------

    public static final class ClientState {
        private static volatile boolean hasSynced = false;
        private static volatile boolean useCustomFont = RPGTimelineConfig.DEFAULT_USE_CUSTOM_FONT;
        private static volatile long calendarDayOffsetDays = 0L;
        private static volatile org.z2six.rpgtimeline.calendar.CalendarDefinition calendarDefinition = null;
        private static volatile SeasonMonthMapping seasonMonthMapping = null;
        private static volatile boolean useSereneSeasons = RPGTimelineConfig.DEFAULT_USE_SERENE_SEASONS;

        private ClientState() {
            // no-op
        }

        public static boolean hasSynced() {
            return hasSynced;
        }

        public static boolean useCustomFont() {
            return useCustomFont;
        }

        public static long getCalendarDayOffsetDays() {
            return calendarDayOffsetDays;
        }

        public static org.z2six.rpgtimeline.calendar.CalendarDefinition getCalendarDefinition() {
            if (calendarDefinition != null) {
                return calendarDefinition;
            }
            return org.z2six.rpgtimeline.calendar.CalendarDefinition.defaultDefinition();
        }

        public static SeasonMonthMapping getSeasonMonthMapping(int monthCount) {
            if (seasonMonthMapping != null) {
                return seasonMonthMapping;
            }
            return SeasonMonthMapping.defaultForMonthCount(monthCount);
        }

        public static boolean useSereneSeasons() {
            return useSereneSeasons;
        }

        private static void applyFromServer(
                List<String> monthNames,
                List<String> monthAbbreviations,
                String yearSuffix,
                int daysPerMonth,
                int ticksPerDay,
                long newCalendarDayOffsetDays,
                boolean newUseCustomFont,
                boolean newUseSereneSeasons,
                List<Integer> springMonths,
                List<Integer> summerMonths,
                List<Integer> autumnMonths,
                List<Integer> winterMonths
        ) {
            useCustomFont = newUseCustomFont;
            useSereneSeasons = newUseSereneSeasons;
            calendarDayOffsetDays = newCalendarDayOffsetDays;
            try {
                String[] namesArray = monthNames.toArray(new String[0]);
                String[] abbrevArray = monthAbbreviations.toArray(new String[0]);
                calendarDefinition = new org.z2six.rpgtimeline.calendar.CalendarDefinition(
                        namesArray,
                        abbrevArray,
                        yearSuffix,
                        daysPerMonth,
                        (long) ticksPerDay
                );
            } catch (Throwable t) {
                LOG.error("[RPGTimelinePayloads.ClientState] Failed to build CalendarDefinition from server data", t);
                calendarDefinition = org.z2six.rpgtimeline.calendar.CalendarDefinition.defaultDefinition();
            }
            seasonMonthMapping = new SeasonMonthMapping(
                    springMonths == null ? List.of() : springMonths,
                    summerMonths == null ? List.of() : summerMonths,
                    autumnMonths == null ? List.of() : autumnMonths,
                    winterMonths == null ? List.of() : winterMonths
            );
            hasSynced = true;

            LOG.debug("[RPGTimelinePayloads.ClientState] Applied server settings: useCustomFont={}", newUseCustomFont);
        }

        public static void clear() {
            hasSynced = false;
            useCustomFont = RPGTimelineConfig.DEFAULT_USE_CUSTOM_FONT;
            calendarDayOffsetDays = 0L;
            calendarDefinition = null;
            seasonMonthMapping = null;
            useSereneSeasons = RPGTimelineConfig.DEFAULT_USE_SERENE_SEASONS;
            LOG.debug("[RPGTimelinePayloads.ClientState] Cleared client cache");
        }
    }

    // ---------------------------------------------------------------------
    // Payload definitions (ONLY ONCE PER ID!)
    // ---------------------------------------------------------------------

    /**
     * Server -> Client: settings snapshot.
     */
    public record ServerCalendarSettingsPayload(
            List<String> monthNames,
            List<String> monthAbbreviations,
            String yearSuffix,
            int daysPerMonth,
            int ticksPerDay,
            long calendarDayOffsetDays,
            boolean useCustomFont,
            boolean useSereneSeasons,
            List<Integer> springMonths,
            List<Integer> summerMonths,
            List<Integer> autumnMonths,
            List<Integer> winterMonths
    )
            implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

        public static final ResourceLocation ID =
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "calendar_settings_v1");

        public static final Type<ServerCalendarSettingsPayload> TYPE = new Type<>(ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, ServerCalendarSettingsPayload> STREAM_CODEC =
                StreamCodec.of(ServerCalendarSettingsPayload::encode, ServerCalendarSettingsPayload::decode);

        @Override
        public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
            return TYPE;
        }

        private static void encode(RegistryFriendlyByteBuf buf, ServerCalendarSettingsPayload payload) {
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8).encode(buf, new ArrayList<>(payload.monthNames()));
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8).encode(buf, new ArrayList<>(payload.monthAbbreviations()));
            ByteBufCodecs.STRING_UTF8.encode(buf, payload.yearSuffix());
            ByteBufCodecs.INT.encode(buf, payload.daysPerMonth());
            ByteBufCodecs.INT.encode(buf, payload.ticksPerDay());
            ByteBufCodecs.VAR_LONG.encode(buf, payload.calendarDayOffsetDays());
            ByteBufCodecs.BOOL.encode(buf, payload.useCustomFont());
            ByteBufCodecs.BOOL.encode(buf, payload.useSereneSeasons());
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.INT).encode(buf, new ArrayList<>(payload.springMonths()));
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.INT).encode(buf, new ArrayList<>(payload.summerMonths()));
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.INT).encode(buf, new ArrayList<>(payload.autumnMonths()));
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.INT).encode(buf, new ArrayList<>(payload.winterMonths()));
        }

        private static ServerCalendarSettingsPayload decode(RegistryFriendlyByteBuf buf) {
            List<String> monthNames = ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8).decode(buf);
            List<String> monthAbbreviations = ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8).decode(buf);
            String yearSuffix = ByteBufCodecs.STRING_UTF8.decode(buf);
            int daysPerMonth = ByteBufCodecs.INT.decode(buf);
            int ticksPerDay = ByteBufCodecs.INT.decode(buf);
            long calendarDayOffsetDays = ByteBufCodecs.VAR_LONG.decode(buf);
            boolean useCustomFont = ByteBufCodecs.BOOL.decode(buf);
            boolean useSereneSeasons = ByteBufCodecs.BOOL.decode(buf);
            List<Integer> springMonths = ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.INT).decode(buf);
            List<Integer> summerMonths = ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.INT).decode(buf);
            List<Integer> autumnMonths = ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.INT).decode(buf);
            List<Integer> winterMonths = ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.INT).decode(buf);
            return new ServerCalendarSettingsPayload(
                    monthNames,
                    monthAbbreviations,
                    yearSuffix,
                    daysPerMonth,
                    ticksPerDay,
                    calendarDayOffsetDays,
                    useCustomFont,
                    useSereneSeasons,
                    springMonths,
                    summerMonths,
                    autumnMonths,
                    winterMonths
            );
        }
    }

    // ---------------------------------------------------------------------
    // Server-side send helpers
    // ---------------------------------------------------------------------

    public static void sendSettingsToPlayer(ServerLevel level, ServerPlayer player) {
        try {
            if (level == null || player == null) {
                LOG.warn("[RPGTimelinePayloads] sendSettingsToPlayer: null args; skipping");
                return;
            }

            boolean useCustomFontValue = RPGTimelineConfig.getUseCustomFont();
            List<String> monthNames = new ArrayList<>(RPGTimelineConfig.getMonthNamesList());
            List<String> monthAbbreviations = new ArrayList<>(RPGTimelineConfig.getMonthAbbreviationsList());
            String yearSuffix = RPGTimelineConfig.getYearSuffix();
            int daysPerMonth = RPGTimelineConfig.getDaysPerMonth();
            int ticksPerDay = RPGTimelineConfig.TICKS_PER_DAY;
            boolean useSereneSeasons = RPGTimelineConfig.getUseSereneSeasons();
            SeasonMonthMapping mapping = RPGTimelineConfig.getSeasonMonthMapping();
            long dayOffsetDays = 0L;
            if (level.getServer() != null) {
                dayOffsetDays = org.z2six.rpgtimeline.server.RPGTimelineCalendarSavedData.get(level.getServer()).getDayOffsetDays();
            }

            ServerCalendarSettingsPayload msg = new ServerCalendarSettingsPayload(
                    monthNames,
                    monthAbbreviations,
                    yearSuffix,
                    daysPerMonth,
                    ticksPerDay,
                    dayOffsetDays,
                    useCustomFontValue,
                    useSereneSeasons,
                    new ArrayList<>(mapping.spring()),
                    new ArrayList<>(mapping.summer()),
                    new ArrayList<>(mapping.autumn()),
                    new ArrayList<>(mapping.winter())
            );
            PacketDistributor.sendToPlayer(player, msg);

            LOG.debug("[RPGTimelinePayloads] Sent settings to {}: useCustomFont={}",
                    player.getGameProfile().getName(), useCustomFontValue);

        } catch (Throwable t) {
            LOG.error("[RPGTimelinePayloads] sendSettingsToPlayer failed safely", t);
        }
    }

    public static void broadcastSettings(ServerLevel level) {
        try {
            if (level == null || level.getServer() == null) {
                LOG.warn("[RPGTimelinePayloads] broadcastSettings: null level/server; skipping");
                return;
            }

            List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
            for (ServerPlayer sp : players) {
                try {
                    sendSettingsToPlayer(level, sp);
                } catch (Throwable t) {
                    LOG.warn("[RPGTimelinePayloads] broadcastSettings: failed sending to {}: {}",
                            sp.getGameProfile().getName(), t.toString());
                }
            }
        } catch (Throwable t) {
            LOG.error("[RPGTimelinePayloads] broadcastSettings failed safely", t);
        }
    }

    // ---------------------------------------------------------------------
    // Handlers
    // ---------------------------------------------------------------------

    private static void handleServerSettingsSync(ServerCalendarSettingsPayload payload, IPayloadContext context) {
        try {
            context.enqueueWork(() -> {
                try {
                    ClientState.applyFromServer(
                            payload.monthNames(),
                            payload.monthAbbreviations(),
                            payload.yearSuffix(),
                            payload.daysPerMonth(),
                            payload.ticksPerDay(),
                            payload.calendarDayOffsetDays(),
                            payload.useCustomFont(),
                            payload.useSereneSeasons(),
                            payload.springMonths(),
                            payload.summerMonths(),
                            payload.autumnMonths(),
                            payload.winterMonths()
                    );
                } catch (Throwable t) {
                    LOG.error("[RPGTimelinePayloads] handleServerSettingsSync work failed safely", t);
                }
            });
        } catch (Throwable t) {
            LOG.error("[RPGTimelinePayloads] handleServerSettingsSync failed safely", t);
        }
    }
}
