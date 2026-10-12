package com.solegendary.reignofnether.resources;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The placement ghost's tree line (TreeClearing): "Clears N trees: +X energy" beside the cursor while a building
 * that would fell trees is being placed. The scan runs on the client's own copy of the world, at most every
 * {@link #RESCAN_MS} and only when the ghost moved, rotated or changed - never per frame.
 */
public class TreeClearingClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final long RESCAN_MS = 120;

    private static Building lastBuilding = null;
    private static BlockPos lastOrigin = null;
    private static Rotation lastRotation = null;
    private static long lastScanAt = 0;
    private static int trees = 0;
    private static int energy = 0;

    @SubscribeEvent
    public static void onDrawScreen(ScreenEvent.Render.Post evt) {
        Building building = BuildingClientEvents.getBuildingToPlace();
        if (building == null || MC.level == null || !OrthoviewClientEvents.isEnabled()) {
            lastBuilding = null;
            return;
        }
        Rotation rot = BuildingClientEvents.getBuildingRotation();
        Vec3i dims = BuildingClientEvents.getBuildingDimensions();
        BlockPos origin = BuildingUtils.getBuildingOriginPos(CursorClientEvents.getPreselectedBlockPos(),
            BuildingUtils.isBridge(building), rot, dims);
        long now = System.currentTimeMillis();
        boolean moved = building != lastBuilding || rot != lastRotation || !origin.equals(lastOrigin);
        if (moved && now - lastScanAt >= RESCAN_MS) {
            lastBuilding = building;
            lastRotation = rot;
            lastOrigin = origin;
            lastScanAt = now;
            if (BuildingUtils.isBridge(building)) {
                trees = 0;
                energy = 0;
            } else {
                BlockPos far = origin.offset(new BlockPos(Math.max(0, dims.getX() - 1), 0, Math.max(0, dims.getZ() - 1)).rotate(rot));
                BlockPos min = new BlockPos(Math.min(origin.getX(), far.getX()), origin.getY(), Math.min(origin.getZ(), far.getZ()));
                BlockPos max = new BlockPos(Math.max(origin.getX(), far.getX()), origin.getY(), Math.max(origin.getZ(), far.getZ()));
                TreeClearing.Scan scan = TreeClearing.scanFootprint(MC.level, min, max);
                trees = scan.trees.size();
                energy = Math.round(scan.energy());
            }
        }
        if (trees <= 0)
            return;
        GuiGraphics g = evt.getGuiGraphics();
        String line = I18n.get("hud.reignofnether.clears_trees", trees, energy);
        int x = evt.getMouseX() + 14, y = evt.getMouseY() + 14;
        int w = MC.font.width(line);
        if (x + w + 4 > g.guiWidth())
            x = evt.getMouseX() - w - 10;
        g.fill(x - 3, y - 3, x + w + 3, y + 11, 0xC0101216);
        g.drawString(MC.font, line, x, y, 0xFFE8D27A, false);
    }
}
