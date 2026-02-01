package org.z2six.rpgcalendar.network;

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
import org.z2six.rpgcalendar.Constants;
import org.z2six.rpgcalendar.config.RPGCalendarConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings sync payloads for RPG Calendar.
 */
public final class RPGCalendarPayloads {

    private static final Logger LOG = LogUtils.getLogger();

    /**
     * Bump if you change payload shapes. Must match client + server.
     */
    private static final String PROTOCOL_VERSION = "1";

    private RPGCalendarPayloads() {
        // no-op
    }

    // ---------------------------------------------------------------------
    // Registration hook
    // ---------------------------------------------------------------------

    public static void register(IEventBus modBus) {
        try {
            modBus.addListener(RPGCalendarPayloads::onRegisterPayloadHandlers);
            LOG.debug("[RPGCalendarPayloads] Hooked RegisterPayloadHandlersEvent listener");
        } catch (Throwable t) {
            LOG.error("[RPGCalendarPayloads] register() failed safely", t);
        }
    }

    private static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        try {
            PayloadRegistrar registrar = event.registrar(Constants.MOD_ID).versioned(PROTOCOL_VERSION);

            registrar.playToClient(
                    ServerCalendarSettingsPayload.TYPE,
                    ServerCalendarSettingsPayload.STREAM_CODEC,
                    RPGCalendarPayloads::handleServerSettingsSync
            );

            LOG.debug("[RPGCalendarPayloads] Registered settings payloads OK (protocol={})", PROTOCOL_VERSION);
        } catch (Throwable t) {
            LOG.error("[RPGCalendarPayloads] onRegisterPayloadHandlers failed safely", t);
        }
    }

    // ---------------------------------------------------------------------
    // Client-side cache
    // ---------------------------------------------------------------------

    public static final class ClientState {
        private static volatile boolean hasSynced = false;
        private static volatile boolean useCustomFont = RPGCalendarConfig.DEFAULT_USE_CUSTOM_FONT;
        private static volatile org.z2six.rpgcalendar.calendar.CalendarDefinition calendarDefinition = null;

        private ClientState() {
            // no-op
        }

        public static boolean hasSynced() {
            return hasSynced;
        }

        public static boolean useCustomFont() {
            return useCustomFont;
        }

        public static org.z2six.rpgcalendar.calendar.CalendarDefinition getCalendarDefinition() {
            if (calendarDefinition != null) {
                return calendarDefinition;
            }
            return org.z2six.rpgcalendar.calendar.CalendarDefinition.defaultDefinition();
        }

        private static void applyFromServer(
                List<String> monthNames,
                String yearSuffix,
                int daysPerMonth,
                int ticksPerDay,
                boolean newUseCustomFont
        ) {
            useCustomFont = newUseCustomFont;
            try {
                String[] namesArray = monthNames.toArray(new String[0]);
                calendarDefinition = new org.z2six.rpgcalendar.calendar.CalendarDefinition(
                        namesArray,
                        yearSuffix,
                        daysPerMonth,
                        (long) ticksPerDay
                );
            } catch (Throwable t) {
                LOG.error("[RPGCalendarPayloads.ClientState] Failed to build CalendarDefinition from server data", t);
                calendarDefinition = org.z2six.rpgcalendar.calendar.CalendarDefinition.defaultDefinition();
            }
            hasSynced = true;

            LOG.debug("[RPGCalendarPayloads.ClientState] Applied server settings: useCustomFont={}", newUseCustomFont);
        }

        public static void clear() {
            hasSynced = false;
            useCustomFont = RPGCalendarConfig.DEFAULT_USE_CUSTOM_FONT;
            calendarDefinition = null;
            LOG.debug("[RPGCalendarPayloads.ClientState] Cleared client cache");
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
            String yearSuffix,
            int daysPerMonth,
            int ticksPerDay,
            boolean useCustomFont
    )
            implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {

        public static final ResourceLocation ID =
                ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "calendar_settings_v1");

        public static final Type<ServerCalendarSettingsPayload> TYPE = new Type<>(ID);

        public static final StreamCodec<RegistryFriendlyByteBuf, ServerCalendarSettingsPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), ServerCalendarSettingsPayload::monthNames,
                        ByteBufCodecs.STRING_UTF8, ServerCalendarSettingsPayload::yearSuffix,
                        ByteBufCodecs.INT, ServerCalendarSettingsPayload::daysPerMonth,
                        ByteBufCodecs.INT, ServerCalendarSettingsPayload::ticksPerDay,
                        ByteBufCodecs.BOOL, ServerCalendarSettingsPayload::useCustomFont,
                        ServerCalendarSettingsPayload::new
                );

        @Override
        public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---------------------------------------------------------------------
    // Server-side send helpers
    // ---------------------------------------------------------------------

    public static void sendSettingsToPlayer(ServerLevel level, ServerPlayer player) {
        try {
            if (level == null || player == null) {
                LOG.warn("[RPGCalendarPayloads] sendSettingsToPlayer: null args; skipping");
                return;
            }

            boolean useCustomFontValue = RPGCalendarConfig.getUseCustomFont();
            List<String> monthNames = new ArrayList<>(RPGCalendarConfig.getMonthNamesList());
            String yearSuffix = RPGCalendarConfig.getYearSuffix();
            int daysPerMonth = RPGCalendarConfig.getDaysPerMonth();
            int ticksPerDay = RPGCalendarConfig.TICKS_PER_DAY;

            ServerCalendarSettingsPayload msg = new ServerCalendarSettingsPayload(
                    monthNames,
                    yearSuffix,
                    daysPerMonth,
                    ticksPerDay,
                    useCustomFontValue
            );
            PacketDistributor.sendToPlayer(player, msg);

            LOG.debug("[RPGCalendarPayloads] Sent settings to {}: useCustomFont={}",
                    player.getGameProfile().getName(), useCustomFontValue);

        } catch (Throwable t) {
            LOG.error("[RPGCalendarPayloads] sendSettingsToPlayer failed safely", t);
        }
    }

    public static void broadcastSettings(ServerLevel level) {
        try {
            if (level == null || level.getServer() == null) {
                LOG.warn("[RPGCalendarPayloads] broadcastSettings: null level/server; skipping");
                return;
            }

            List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
            for (ServerPlayer sp : players) {
                try {
                    sendSettingsToPlayer(level, sp);
                } catch (Throwable t) {
                    LOG.warn("[RPGCalendarPayloads] broadcastSettings: failed sending to {}: {}",
                            sp.getGameProfile().getName(), t.toString());
                }
            }
        } catch (Throwable t) {
            LOG.error("[RPGCalendarPayloads] broadcastSettings failed safely", t);
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
                            payload.yearSuffix(),
                            payload.daysPerMonth(),
                            payload.ticksPerDay(),
                            payload.useCustomFont()
                    );
                } catch (Throwable t) {
                    LOG.error("[RPGCalendarPayloads] handleServerSettingsSync work failed safely", t);
                }
            });
        } catch (Throwable t) {
            LOG.error("[RPGCalendarPayloads] handleServerSettingsSync failed safely", t);
        }
    }
}
