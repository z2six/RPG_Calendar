package org.z2six.rpgtimeline.chronicle.server;

import java.util.Arrays;
import java.util.List;

/**
 * Built-in milestone definitions for server timeline aggregation.
 */
public final class ChronicleMilestones {

    public record MilestoneDefinition(
            String key,
            String title,
            List<String> advancementIds,
            String iconItemId
    ) {
        public boolean matches(String advancementId) {
            if (advancementId == null || advancementId.isEmpty()) {
                return false;
            }
            for (String id : advancementIds) {
                if (advancementId.equals(id)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static final List<MilestoneDefinition> DEFAULTS = List.of(
            new MilestoneDefinition(
                    "enter_nether",
                    "Entered the Nether",
                    Arrays.asList("minecraft:story/enter_the_nether"),
                    "minecraft:netherrack"
            ),
            new MilestoneDefinition(
                    "enter_end",
                    "Entered the End",
                    Arrays.asList("minecraft:story/enter_the_end"),
                    "minecraft:end_stone"
            ),
            new MilestoneDefinition(
                    "kill_dragon",
                    "Slain the Ender Dragon",
                    Arrays.asList("minecraft:end/kill_dragon"),
                    "minecraft:dragon_egg"
            ),
            new MilestoneDefinition(
                    "obtain_netherite",
                    "Obtained Netherite",
                    Arrays.asList(
                            "minecraft:nether/obtain_ancient_debris",
                            "minecraft:nether/netherite_armor"
                    ),
                    "minecraft:netherite_ingot"
            )
    );

    private ChronicleMilestones() {
        // no-op
    }
}
