package com.solegendary.reignofnether.resources;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Clientside copy of every metal patch centre on the map, synced from the server; read by the minimap. */
public final class MetalPatchesClient {
    private MetalPatchesClient() { }

    private static final List<BlockPos> patches = new CopyOnWriteArrayList<>();
    /** Non-standard patch values (pre-made maps: 1.5 rich, 2.0 centre) by centre (BlockPos.asLong). */
    private static final Map<Long, Float> values = new ConcurrentHashMap<>();

    public static List<BlockPos> getPatches() {
        return patches;
    }

    public static void sync(List<BlockPos> newPatches) {
        sync(newPatches, null);
    }

    public static void sync(List<BlockPos> newPatches, List<Float> newValues) {
        patches.clear();
        patches.addAll(newPatches);
        values.clear();
        if (newValues != null)
            for (int i = 0; i < newPatches.size() && i < newValues.size(); i++)
                if (newValues.get(i) != 1f)
                    values.put(newPatches.get(i).asLong(), newValues.get(i));
    }

    /** The value of the patch centred at {@code centre} (1 for a standard patch). */
    public static float valueOf(BlockPos centre) {
        return values.isEmpty() ? 1f : values.getOrDefault(centre.asLong(), 1f);
    }

    /** As MetalPatches.valueNear, on the synced copy (portrait income of an extractor). */
    public static float valueNear(int x, int y, int z) {
        if (values.isEmpty())
            return 1f;
        for (BlockPos c : patches)
            if (Math.abs(c.getX() - x) <= 3 && Math.abs(c.getZ() - z) <= 3 && Math.abs(c.getY() - y) <= 8)
                return valueOf(c);
        return 1f;
    }

    public static void clear() {
        patches.clear();
        values.clear();
    }
}
