package org.z2six.rpgtimeline.platform.services;

import org.z2six.rpgtimeline.calendar.CalendarDefinition;
import org.z2six.rpgtimeline.chronicle.ChronicleScope;
import net.minecraft.advancements.Advancement;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public interface IPlatformHelper {

    /**
     * Gets the name of the current platform
     *
     * @return The name of the current platform.
     */
    String getPlatformName();

    /**
     * Checks if a mod with the given id is loaded.
     *
     * @param modId The mod to check if it is loaded.
     * @return True if the mod is loaded, false otherwise.
     */
    boolean isModLoaded(String modId);

    /**
     * Check if the game is currently in a development environment.
     *
     * @return True if in a development environment, false otherwise.
     */
    boolean isDevelopmentEnvironment();

    /**
     * Gets the name of the environment type as a string.
     *
     * @return The name of the environment type.
     */
    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }

    /**
     * Returns the active calendar definition for this runtime.
     */
    CalendarDefinition getCalendarDefinition();

    /**
     * Returns a day offset (in whole days) applied when converting world day indices into
     * calendar dates (month/day/year). This is intended for re-aligning the Timeline calendar
     * with other date systems without changing Minecraft world time.
     *
     * <p>Server-authoritative; clients receive the value via settings sync.</p>
     */
    default long getCalendarDayOffsetDays() {
        return 0L;
    }

    /**
     * Adds a chronicle note on the server.
     */
    void addChronicleNote(ServerPlayer player, ChronicleScope scope, String title, String details, long dayIndex);

    /**
     * Records a real advancement on the timeline (server side).
     */
    void recordAdvancement(ServerPlayer player, Advancement advancement);

    /**
     * Records an external/custom advancement-like entry on the timeline.
     */
    void recordExternalAdvancement(
            ServerPlayer player,
            String sourceId,
            Component title,
            Component description,
            String iconItemId
    );
}
