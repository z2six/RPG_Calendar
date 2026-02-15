package org.z2six.chunk_fixer.core;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public final class ChunkForceLoader {

    private ChunkForceLoader() {
        // no-op
    }

    public static void forceSelection(ServerLevel level, ChunkSelection selection, boolean forced) {
        if (level == null || selection == null || !selection.isComplete()) {
            return;
        }
        if (selection.getDimension() != null && !selection.getDimension().equals(level.dimension())) {
            return;
        }

        ChunkPos min = selection.getMin();
        ChunkPos max = selection.getMax();
        if (min == null || max == null) {
            return;
        }

        for (int cx = min.x; cx <= max.x; cx++) {
            for (int cz = min.z; cz <= max.z; cz++) {
                level.setChunkForced(cx, cz, forced);
            }
        }
    }
}
