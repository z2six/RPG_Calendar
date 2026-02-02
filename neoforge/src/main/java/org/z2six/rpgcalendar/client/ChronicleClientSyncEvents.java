package org.z2six.rpgcalendar.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import org.z2six.rpgcalendar.network.ChroniclePayloads;

/**
 * Client-side chronicle sync initiator.
 */
public final class ChronicleClientSyncEvents {

    private static final Logger LOG = LogUtils.getLogger();
    private static volatile boolean REGISTERED = false;
    private static volatile boolean REQUEST_PENDING = false;
    private static volatile boolean REQUEST_SENT_THIS_SESSION = false;

    private ChronicleClientSyncEvents() {
        // no-op
    }

    public static void registerGameBus() {
        try {
            if (REGISTERED) {
                LOG.debug("[ChronicleClientSyncEvents] already registered; skipping");
                return;
            }
            NeoForge.EVENT_BUS.register(ChronicleClientSyncEvents.class);
            REGISTERED = true;
            LOG.debug("[ChronicleClientSyncEvents] Registered on NeoForge EVENT_BUS");
        } catch (Throwable t) {
            LOG.error("[ChronicleClientSyncEvents] registerGameBus failed safely", t);
        }
    }

    @SubscribeEvent
    public static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        try {
            REQUEST_PENDING = true;
            REQUEST_SENT_THIS_SESSION = false;
            ChroniclePayloads.ClientState.clear();
            LOG.debug("[ChronicleClientSyncEvents] LoggingIn: marked chronicle request pending");
        } catch (Throwable t) {
            LOG.error("[ChronicleClientSyncEvents] onClientLoggingIn failed safely", t);
        }
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        try {
            REQUEST_PENDING = false;
            REQUEST_SENT_THIS_SESSION = false;
            ChroniclePayloads.ClientState.clear();
            LOG.debug("[ChronicleClientSyncEvents] LoggingOut: cleared pending flags + cache");
        } catch (Throwable t) {
            LOG.error("[ChronicleClientSyncEvents] onClientLoggingOut failed safely", t);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        try {
            if (!REQUEST_PENDING || REQUEST_SENT_THIS_SESSION) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;

            Player player = mc.player;
            if (player == null) return;
            if (mc.level == null) return;
            if (mc.getConnection() == null) return;

            PacketDistributor.sendToServer(new ChroniclePayloads.RequestChroniclePayload());

            REQUEST_SENT_THIS_SESSION = true;
            REQUEST_PENDING = false;

            LOG.debug("[ChronicleClientSyncEvents] Sent RequestChroniclePayload (safe tick)");

        } catch (Throwable t) {
            LOG.error("[ChronicleClientSyncEvents] onClientTick failed safely; will retry next tick", t);
        }
    }
}
