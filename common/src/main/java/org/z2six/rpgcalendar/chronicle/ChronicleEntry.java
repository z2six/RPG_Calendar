package org.z2six.rpgcalendar.chronicle;

import java.util.List;

/**
 * A single timeline entry as rendered on the client.
 */
public record ChronicleEntry(
        String id,
        ChronicleEntryType type,
        ChronicleScope scope,
        long dayIndex,
        String title,
        String details,
        String actorName,
        String actorUuid,
        boolean highlight,
        boolean summary,
        String iconItemId,
        List<ChronicleDetail> drilldown
) {
}
