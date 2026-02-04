package org.z2six.rpgtimeline.chronicle;

/**
 * A time slice used to tint the timeline background between start/end days.
 */
public record ChronicleTimeframe(
        float startDay,
        float endDay,
        int priority,
        int layer,
        int renderMode,
        String renderId,
        float alpha
) {
}
