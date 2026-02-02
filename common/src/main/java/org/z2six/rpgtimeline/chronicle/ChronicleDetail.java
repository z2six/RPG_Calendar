package org.z2six.rpgtimeline.chronicle;

/**
 * Drill-down detail row for a timeline entry.
 */
public record ChronicleDetail(
        String title,
        String subtitle,
        long dayIndex,
        String actorUuid,
        String actorName
) {
}
