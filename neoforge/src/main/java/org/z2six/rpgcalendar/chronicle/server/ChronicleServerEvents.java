package org.z2six.rpgcalendar.chronicle.server;

import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import org.slf4j.Logger;

/**
 * Server-side chronicle event hooks.
 */
public final class ChronicleServerEvents {

    private static final Logger LOG = LogUtils.getLogger();
    private static volatile boolean REGISTERED = false;

    private ChronicleServerEvents() {
        // no-op
    }

    public static void registerGameBus() {
        if (REGISTERED) {
            LOG.debug("[ChronicleServerEvents] already registered; skipping");
            return;
        }
        try {
            NeoForge.EVENT_BUS.addListener(ChronicleServerEvents::onAdvancementEarned);
            REGISTERED = true;
            LOG.debug("[ChronicleServerEvents] Registered on NeoForge EVENT_BUS");
        } catch (Throwable t) {
            LOG.error("[ChronicleServerEvents] registerGameBus failed safely", t);
        }
    }

    private static void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        try {
            if (event == null || event.getEntity() == null) {
                return;
            }
            if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp)) {
                return;
            }
            ChronicleService.recordAdvancement(sp, event.getAdvancement());
        } catch (Throwable t) {
            LOG.error("[ChronicleServerEvents] onAdvancementEarned failed safely", t);
        }
    }
}
