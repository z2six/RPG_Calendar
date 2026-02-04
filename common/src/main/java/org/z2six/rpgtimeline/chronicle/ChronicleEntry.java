package org.z2six.rpgtimeline.chronicle;

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
        String sourceId,
        String actorName,
        String actorUuid,
        boolean highlight,
        boolean summary,
        String iconItemId,
        List<ChronicleDetail> drilldown
) {
}
