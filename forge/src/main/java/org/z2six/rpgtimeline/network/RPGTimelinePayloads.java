package org.z2six.rpgtimeline.network;

import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;
import org.z2six.rpgtimeline.Constants;
import org.z2six.rpgtimeline.calendar.SeasonMonthMapping;
import org.z2six.rpgtimeline.config.RPGTimelineConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Settings sync payloads for RPG Timeline (Forge 1.20.1).
 */
public final class RPGTimelinePayloads {

    private static final Logger LOG = LogUtils.getLogger();

    /**
     * Bump if you change payload shapes. Must match client + server.
     */
    private static final String PROTOCOL_VERSION = "3";

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Constants.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static boolean REGISTERED = false;

    private RPGTimelinePayloads() {
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
            CHANNEL.registerMessage(
                    id++,
                    ServerCalendarSettingsPayload.class,
                    RPGTimelinePayloads::encodeServerSettings,
                    RPGTimelinePayloads::decodeServerSettings,
                    RPGTimelinePayloads::handleServerSettingsSync
            );
            REGISTERED = true;
            LOG.debug("[RPGTimelinePayloads] Registered settings payloads OK (protocol={})", PROTOCOL_VERSION);
        } catch (Throwable t) {
            LOG.error("[RPGTimelinePayloads] register() failed safely", t);
        }
    }

    // ---------------------------------------------------------------------
    // Client-side cache
    // ---------------------------------------------------------------------

    public static final class ClientState {
        private static volatile boolean hasSynced = false;
        private static volatile boolean useCustomFont = RPGTimelineConfig.DEFAULT_USE_CUSTOM_FONT;
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
                boolean newUseCustomFont,
                boolean newUseSereneSeasons,
                List<Integer> springMonths,
                List<Integer> summerMonths,
                List<Integer> autumnMonths,
                List<Integer> winterMonths
        ) {
            useCustomFont = newUseCustomFont;
            useSereneSeasons = newUseSereneSeasons;
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
            calendarDefinition = null;
            seasonMonthMapping = null;
            useSereneSeasons = RPGTimelineConfig.DEFAULT_USE_SERENE_SEASONS;
            LOG.debug("[RPGTimelinePayloads.ClientState] Cleared client cache");
        }
    }

    // ---------------------------------------------------------------------
    // Payload definitions
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
            boolean useCustomFont,
            boolean useSereneSeasons,
            List<Integer> springMonths,
            List<Integer> summerMonths,
            List<Integer> autumnMonths,
            List<Integer> winterMonths
    ) {
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

            ServerCalendarSettingsPayload msg = new ServerCalendarSettingsPayload(
                    monthNames,
                    monthAbbreviations,
                    yearSuffix,
                    daysPerMonth,
                    ticksPerDay,
                    useCustomFontValue,
                    useSereneSeasons,
                    new ArrayList<>(mapping.spring()),
                    new ArrayList<>(mapping.summer()),
                    new ArrayList<>(mapping.autumn()),
                    new ArrayList<>(mapping.winter())
            );
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);

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
    // Encoding/decoding
    // ---------------------------------------------------------------------

    private static void encodeServerSettings(ServerCalendarSettingsPayload payload, FriendlyByteBuf buf) {
        writeStringList(buf, payload.monthNames());
        writeStringList(buf, payload.monthAbbreviations());
        buf.writeUtf(payload.yearSuffix());
        buf.writeInt(payload.daysPerMonth());
        buf.writeInt(payload.ticksPerDay());
        buf.writeBoolean(payload.useCustomFont());
        buf.writeBoolean(payload.useSereneSeasons());
        writeIntList(buf, payload.springMonths());
        writeIntList(buf, payload.summerMonths());
        writeIntList(buf, payload.autumnMonths());
        writeIntList(buf, payload.winterMonths());
    }

    private static ServerCalendarSettingsPayload decodeServerSettings(FriendlyByteBuf buf) {
        List<String> monthNames = readStringList(buf);
        List<String> monthAbbreviations = readStringList(buf);
        String yearSuffix = buf.readUtf();
        int daysPerMonth = buf.readInt();
        int ticksPerDay = buf.readInt();
        boolean useCustomFont = buf.readBoolean();
        boolean useSereneSeasons = buf.readBoolean();
        List<Integer> springMonths = readIntList(buf);
        List<Integer> summerMonths = readIntList(buf);
        List<Integer> autumnMonths = readIntList(buf);
        List<Integer> winterMonths = readIntList(buf);
        return new ServerCalendarSettingsPayload(
                monthNames,
                monthAbbreviations,
                yearSuffix,
                daysPerMonth,
                ticksPerDay,
                useCustomFont,
                useSereneSeasons,
                springMonths,
                summerMonths,
                autumnMonths,
                winterMonths
        );
    }

    private static void handleServerSettingsSync(ServerCalendarSettingsPayload payload, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context ctx = contextSupplier.get();
        ctx.enqueueWork(() -> {
            try {
                ClientState.applyFromServer(
                        payload.monthNames(),
                        payload.monthAbbreviations(),
                        payload.yearSuffix(),
                        payload.daysPerMonth(),
                        payload.ticksPerDay(),
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
        ctx.setPacketHandled(true);
    }

    private static void writeStringList(FriendlyByteBuf buf, List<String> list) {
        if (list == null) {
            buf.writeVarInt(0);
            return;
        }
        buf.writeVarInt(list.size());
        for (String value : list) {
            buf.writeUtf(value == null ? "" : value);
        }
    }

    private static List<String> readStringList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(buf.readUtf());
        }
        return list;
    }

    private static void writeIntList(FriendlyByteBuf buf, List<Integer> list) {
        if (list == null) {
            buf.writeVarInt(0);
            return;
        }
        buf.writeVarInt(list.size());
        for (Integer value : list) {
            buf.writeInt(value == null ? 0 : value);
        }
    }

    private static List<Integer> readIntList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<Integer> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(buf.readInt());
        }
        return list;
    }
}
