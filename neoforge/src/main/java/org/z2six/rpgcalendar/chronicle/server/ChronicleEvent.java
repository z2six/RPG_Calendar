package org.z2six.rpgcalendar.chronicle.server;

import org.z2six.rpgcalendar.chronicle.ChronicleEntryType;
import org.z2six.rpgcalendar.chronicle.ChronicleScope;

/**
 * Raw chronicle event stored on the server.
 */
public record ChronicleEvent(
        String id,
        ChronicleEntryType type,
        ChronicleScope scope,
        long dayIndex,
        String title,
        String details,
        String actorName,
        String actorUuid,
        String sourceId,
        String iconItemId
) {
}
