package org.z2six.rpgtimeline.chronicle.server;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class ChronicleGoalService {

    private ChronicleGoalService() {
        // no-op
    }

    public static void handleBlockBreak(ServerPlayer player, ResourceLocation blockId) {
        handleGoalProgress(player, ChronicleGoalType.BREAK_BLOCK, blockId, 1);
    }

    public static void handleMobKill(ServerPlayer player, ResourceLocation mobId) {
        handleGoalProgress(player, ChronicleGoalType.KILL_MOB, mobId, 1);
    }

    public static void handleItemGain(ServerPlayer player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) {
            return;
        }
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        handleGoalProgress(player, ChronicleGoalType.GET_ITEM, itemId, stack.getCount());
    }

    private static void handleGoalProgress(ServerPlayer player, ChronicleGoalType type, ResourceLocation targetId, int delta) {
        if (player == null || targetId == null || delta <= 0) {
            return;
        }
        List<ChronicleGoalDefinition> goals = ChronicleGoalRegistry.getGoalsForTarget(type, targetId);
        if (goals.isEmpty()) {
            return;
        }

        ChronicleSavedData data = ChronicleSavedData.get(player.getServer());
        String playerUuid = player.getUUID().toString();

        for (ChronicleGoalDefinition goal : goals) {
            int current = data.getGoalProgress(goal.id(), playerUuid);
            if (current >= goal.count()) {
                continue;
            }
            int updated = data.addGoalProgress(goal.id(), playerUuid, delta);
            if (updated >= goal.count()) {
                ChronicleService.recordCustomGoal(player, goal);
            }
        }
    }
}
