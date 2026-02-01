package org.z2six.rpgcalendar;

import org.z2six.rpgcalendar.platform.Services;

/**
 * Common init hook shared by all loader targets.
 */
public final class CommonClass {

    private CommonClass() {
        // no-op
    }

    public static void init() {
        Constants.LOG.info(
                "RPG Calendar init on {} ({})",
                Services.PLATFORM.getPlatformName(),
                Services.PLATFORM.getEnvironmentName()
        );
    }
}
