package org.z2six.rpgtimeline.chronicle.server;

import org.z2six.rpgtimeline.chronicle.ChronicleEntryType;
import org.z2six.rpgtimeline.chronicle.ChronicleScope;

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
