package com.solegendary.reignofnether.worldborder;

import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class WorldBorderClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();

    public static final int OUTSIDE_WORLD_BORDER_TINT = 0x252933;

    private static double lastCenterX = 0D;
    private static double lastCenterZ = 0D;
    private static double lastSize = 0D;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;

        if (MC.level == null)
            return;

        WorldBorder border = MC.level.getWorldBorder();
        double centerX = border.getCenterX();
        double centerZ = border.getCenterZ();
        double size = border.getSize();

        if (centerX != lastCenterX ||
            centerZ != lastCenterZ ||
            size != lastSize)
            FogOfWarClientEvents.resetFogChunks();

        lastCenterX = centerX;
        lastCenterZ = centerZ;
        lastSize = size;
    }

    // asked from meshing threads (BiomeColorsMixin.applyTint): read the level once - the render thread can clear it
    // between two reads when leaving a world
    public static boolean isOutsideWorldBorder(BlockPos bp) {
        ClientLevel level = MC.level;
        return level != null && !level.getWorldBorder().isWithinBounds(bp);
    }
}
