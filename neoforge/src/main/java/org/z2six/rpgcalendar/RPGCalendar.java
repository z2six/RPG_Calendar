package org.z2six.rpgcalendar;


import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.z2six.rpgcalendar.chronicle.server.ChronicleServerEvents;
import org.z2six.rpgcalendar.client.ChronicleClientSyncEvents;
import org.z2six.rpgcalendar.client.ChronicleKeyBindings;
import org.z2six.rpgcalendar.config.RPGCalendarConfig;
import org.z2six.rpgcalendar.network.ChroniclePayloads;
import org.z2six.rpgcalendar.network.RPGCalendarPayloads;
import org.z2six.rpgcalendar.registry.RPGCalendarItems;
import org.z2six.rpgcalendar.server.RPGCalendarServerSyncEvents;

@Mod(Constants.MOD_ID)
public class RPGCalendar {

    public RPGCalendar(IEventBus eventBus) {

        try {
            CommonClass.init();
        } catch (Throwable t) {
            Constants.LOG.error("[RPGCalendar] CommonClass.init() failed", t);
        }

        try {
            RPGCalendarConfig.register();
            Constants.LOG.debug("[RPGCalendar] Registered RPGCalendarConfig (SERVER)");
        } catch (Throwable t) {
            Constants.LOG.error("[RPGCalendar] RPGCalendarConfig.register() failed", t);
        }

        try {
            RPGCalendarItems.register(eventBus);
            Constants.LOG.debug("[RPGCalendar] Registered RPGCalendarItems");
        } catch (Throwable t) {
            Constants.LOG.error("[RPGCalendar] RPGCalendarItems.register() failed", t);
        }

        try {
            RPGCalendarPayloads.register(eventBus);
            Constants.LOG.debug("[RPGCalendar] Registered RPGCalendarPayloads");
        } catch (Throwable t) {
            Constants.LOG.error("[RPGCalendar] RPGCalendarPayloads.register() failed", t);
        }

        try {
            ChroniclePayloads.register(eventBus);
            Constants.LOG.debug("[RPGCalendar] Registered ChroniclePayloads");
        } catch (Throwable t) {
            Constants.LOG.error("[RPGCalendar] ChroniclePayloads.register() failed", t);
        }

        try {
            ChronicleServerEvents.registerGameBus();
            Constants.LOG.debug("[RPGCalendar] Registered ChronicleServerEvents");
        } catch (Throwable t) {
            Constants.LOG.error("[RPGCalendar] ChronicleServerEvents.registerGameBus() failed", t);
        }

        try {
            RPGCalendarServerSyncEvents.registerGameBus();
            Constants.LOG.debug("[RPGCalendar] Registered RPGCalendarServerSyncEvents");
        } catch (Throwable t) {
            Constants.LOG.error("[RPGCalendar] RPGCalendarServerSyncEvents.registerGameBus() failed", t);
        }

        try {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ChronicleClientSyncEvents.registerGameBus();
                ChronicleKeyBindings.register(eventBus);
                Constants.LOG.debug("[RPGCalendar] Registered Chronicle client events + keybinds");
            }
        } catch (Throwable t) {
            Constants.LOG.error("[RPGCalendar] Chronicle client registration failed", t);
        }

    }
}
