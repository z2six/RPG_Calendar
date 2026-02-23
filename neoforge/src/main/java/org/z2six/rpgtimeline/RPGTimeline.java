package org.z2six.rpgtimeline;


import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.z2six.rpgtimeline.chronicle.server.ChronicleServerEvents;
import org.z2six.rpgtimeline.client.ChronicleClientSyncEvents;
import org.z2six.rpgtimeline.client.ChronicleKeyBindings;
import org.z2six.rpgtimeline.config.RPGTimelineClientConfig;
import org.z2six.rpgtimeline.config.RPGTimelineConfig;
import org.z2six.rpgtimeline.network.ChroniclePayloads;
import org.z2six.rpgtimeline.network.RPGTimelinePayloads;
import org.z2six.rpgtimeline.registry.RPGTimelineItems;
import org.z2six.rpgtimeline.server.RPGTimelineCalendarCommands;
import org.z2six.rpgtimeline.server.RPGTimelineServerSyncEvents;

@Mod(Constants.MOD_ID)
public class RPGTimeline {

    public RPGTimeline(IEventBus eventBus) {

        try {
            CommonClass.init();
        } catch (Throwable t) {
            Constants.LOG.error("[RPGTimeline] CommonClass.init() failed", t);
        }

        try {
            RPGTimelineConfig.register();
            Constants.LOG.debug("[RPGTimeline] Registered RPGTimelineConfig (SERVER)");
        } catch (Throwable t) {
            Constants.LOG.error("[RPGTimeline] RPGTimelineConfig.register() failed", t);
        }

        try {
            RPGTimelineClientConfig.register();
            Constants.LOG.debug("[RPGTimeline] Registered RPGTimelineClientConfig (CLIENT)");
        } catch (Throwable t) {
            Constants.LOG.error("[RPGTimeline] RPGTimelineClientConfig.register() failed", t);
        }

        try {
            RPGTimelineItems.register(eventBus);
            Constants.LOG.debug("[RPGTimeline] Registered RPGTimelineItems");
        } catch (Throwable t) {
            Constants.LOG.error("[RPGTimeline] RPGTimelineItems.register() failed", t);
        }

        try {
            RPGTimelinePayloads.register(eventBus);
            Constants.LOG.debug("[RPGTimeline] Registered RPGTimelinePayloads");
        } catch (Throwable t) {
            Constants.LOG.error("[RPGTimeline] RPGTimelinePayloads.register() failed", t);
        }

        try {
            ChroniclePayloads.register(eventBus);
            Constants.LOG.debug("[RPGTimeline] Registered ChroniclePayloads");
        } catch (Throwable t) {
            Constants.LOG.error("[RPGTimeline] ChroniclePayloads.register() failed", t);
        }

        try {
            ChronicleServerEvents.registerGameBus();
            Constants.LOG.debug("[RPGTimeline] Registered ChronicleServerEvents");
        } catch (Throwable t) {
            Constants.LOG.error("[RPGTimeline] ChronicleServerEvents.registerGameBus() failed", t);
        }

        try {
            org.z2six.rpgtimeline.chronicle.server.ChronicleDebugCommands.registerGameBus();
            Constants.LOG.debug("[RPGTimeline] Registered ChronicleDebugCommands");
        } catch (Throwable t) {
            Constants.LOG.error("[RPGTimeline] ChronicleDebugCommands.registerGameBus() failed", t);
        }

        try {
            org.z2six.rpgtimeline.chronicle.server.ChronicleRetroImportCommands.registerGameBus();
            Constants.LOG.debug("[RPGTimeline] Registered ChronicleRetroImportCommands");
        } catch (Throwable t) {
            Constants.LOG.error("[RPGTimeline] ChronicleRetroImportCommands.registerGameBus() failed", t);
        }

        try {
            RPGTimelineServerSyncEvents.registerGameBus();
            Constants.LOG.debug("[RPGTimeline] Registered RPGTimelineServerSyncEvents");
        } catch (Throwable t) {
            Constants.LOG.error("[RPGTimeline] RPGTimelineServerSyncEvents.registerGameBus() failed", t);
        }

        try {
            RPGTimelineCalendarCommands.registerGameBus();
            Constants.LOG.debug("[RPGTimeline] Registered RPGTimelineCalendarCommands");
        } catch (Throwable t) {
            Constants.LOG.error("[RPGTimeline] RPGTimelineCalendarCommands.registerGameBus() failed", t);
        }

        try {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ChronicleClientSyncEvents.registerGameBus();
                ChronicleKeyBindings.register(eventBus);
                Constants.LOG.debug("[RPGTimeline] Registered Chronicle client events + keybinds");
            }
        } catch (Throwable t) {
            Constants.LOG.error("[RPGTimeline] Chronicle client registration failed", t);
        }

    }
}
