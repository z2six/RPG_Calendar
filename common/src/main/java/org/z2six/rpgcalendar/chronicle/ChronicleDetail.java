package org.z2six.rpgcalendar.chronicle;

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
