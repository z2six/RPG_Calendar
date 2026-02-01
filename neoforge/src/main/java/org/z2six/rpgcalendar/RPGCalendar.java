package org.z2six.rpgcalendar;


import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.z2six.rpgcalendar.config.RPGCalendarConfig;
import org.z2six.rpgcalendar.network.RPGCalendarPayloads;
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
            RPGCalendarPayloads.register(eventBus);
            Constants.LOG.debug("[RPGCalendar] Registered RPGCalendarPayloads");
        } catch (Throwable t) {
            Constants.LOG.error("[RPGCalendar] RPGCalendarPayloads.register() failed", t);
        }

        try {
            RPGCalendarServerSyncEvents.registerGameBus();
            Constants.LOG.debug("[RPGCalendar] Registered RPGCalendarServerSyncEvents");
        } catch (Throwable t) {
            Constants.LOG.error("[RPGCalendar] RPGCalendarServerSyncEvents.registerGameBus() failed", t);
        }

    }
}
