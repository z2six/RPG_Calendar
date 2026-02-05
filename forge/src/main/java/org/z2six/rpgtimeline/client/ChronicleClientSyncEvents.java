package org.z2six.rpgtimeline.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;
import org.z2six.rpgtimeline.network.ChroniclePayloads;

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
            MinecraftForge.EVENT_BUS.register(ChronicleClientSyncEvents.class);
            REGISTERED = true;
            LOG.debug("[ChronicleClientSyncEvents] Registered on Forge EVENT_BUS");
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
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        try {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            if (!REQUEST_PENDING || REQUEST_SENT_THIS_SESSION) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;

            Player player = mc.player;
            if (player == null) return;
            if (mc.level == null) return;
            if (mc.getConnection() == null) return;

            ChroniclePayloads.sendToServer(new ChroniclePayloads.RequestChroniclePayload());

            REQUEST_SENT_THIS_SESSION = true;
            REQUEST_PENDING = false;

            LOG.debug("[ChronicleClientSyncEvents] Sent RequestChroniclePayload (safe tick)");

        } catch (Throwable t) {
            LOG.error("[ChronicleClientSyncEvents] onClientTick failed safely; will retry next tick", t);
        }
    }
}
