package org.z2six.rpgtimeline.chronicle.server;

import net.minecraft.resources.ResourceLocation;

public record ChronicleGoalDefinition(
        String id,
        ChronicleGoalType type,
        ResourceLocation targetId,
        int count,
        String title,
        String description,
        String iconItemId
) {
    public String sourceId() {
        return ChronicleGoalRegistry.CUSTOM_SOURCE_PREFIX + id;
    }
}
