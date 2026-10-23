package com.solegendary.reignofnether.building;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.building.buildings.shared.MetalExtractor;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.resources.MetalPatchesClient;
import com.solegendary.reignofnether.util.MiscUtil;
import com.solegendary.reignofnether.util.MyRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the placement grid while a building is being placed (PlacementGrid has the rules and numbers): gold dots on
 * the block grid near the cursor, red where nothing can stand; a rune circle around the footprint, gold if the
 * placement is valid, red if not; and for a Metal Extractor, a cyan ring and a light column on every free patch nearby
 * (grey when taken). Nothing is drawn when no building is being placed.
 */
public class PlacementGridClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final List<int[]> CELLS = PlacementGrid.cells();

    // the dots are worked out again only when the cursor moves to another block, or every 10 frames for new buildings
    private static BlockPos cachedFor = null;
    private static int framesSinceCache = 0;
    /** x, y (top of the ground block), z, buildable (1/0), and the distance to the cursor * 100. */
    private static final List<int[]> dots = new ArrayList<>();

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        int fp = com.solegendary.reignofnether.perf.FrameParts.begin(com.solegendary.reignofnether.perf.FrameParts.WORLD_MARKERS);
        try {
            if (com.solegendary.reignofnether.postfx.ShaderPacks.skipOverlays()) return;   // shader pack shadow map: overlays cast no shadow
            if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !OrthoviewClientEvents.isEnabled()
                    || MC.level == null || BuildingClientEvents.getBuildingToPlace() == null) {
                cachedFor = null;
                return;
            }
            BlockPos centre = BuildingClientEvents.ghostCursor();
            if (centre == null)
                return;
            PoseStack pose = evt.getPoseStack();
            drawDots(pose, centre);
            drawRuneCircle(pose, centre, BuildingClientEvents.lastGhostValid);
            if (BuildingClientEvents.getBuildingToPlace() instanceof MetalExtractor)
                drawPatches(pose, centre);
        } finally {
            com.solegendary.reignofnether.perf.FrameParts.end(fp);
        }
    }

    private static void drawDots(PoseStack pose, BlockPos centre) {
        if (!centre.equals(cachedFor) || ++framesSinceCache > 10) {
            cachedFor = centre;
            framesSinceCache = 0;
            dots.clear();
            for (int[] c : CELLS) {
                BlockPos surface = surfaceAt(centre.getX() + c[0], centre.getZ() + c[1]);
                dots.add(new int[]{ surface.getX(), surface.getY() + 1, surface.getZ(), buildable(surface) ? 1 : 0,
                    (int) Math.round(100 * Math.sqrt(c[0] * c[0] + c[1] * c[1])) });
            }
        }
        for (int[] d : dots) {
            float a = PlacementGrid.dotAlpha(d[4] / 100.0);
            if (a <= 0.01f)
                continue;
            double x = d[0] + 0.5, y = d[1] + 0.02, z = d[2] + 0.5, s = 0.16;
            float r = 1.0f, g = d[3] == 1 ? 0.82f : 0.2f, b = d[3] == 1 ? 0.35f : 0.2f;   // gold, or red where nothing can stand
            MyRenderer.drawLine(pose, x - s, y, z, x + s, y, z, r, g, b, a);
            MyRenderer.drawLine(pose, x, y, z - s, x, y, z + s, r, g, b, a);
        }
    }

    /**
     * The top solid (or liquid) block of a column. MiscUtil.getHighestNonAirBlock returns the block one BELOW it (it
     * steps down once more after the match), which is the origin convention the building code relies on, so it is
     * corrected here: the first virtual-screen check drew every dot and the rune circle inside the grass.
     */
    private static BlockPos surfaceAt(int x, int z) {
        return MiscUtil.getHighestNonAirBlock(MC.level, new BlockPos(x, 0, z), true).above();
    }

    /** A building can stand here: solid ground, no liquid, open above, not inside another building. */
    private static boolean buildable(BlockPos surface) {
        BlockState s = MC.level.getBlockState(surface);
        if (s.isAir() || !s.getFluidState().isEmpty())
            return false;
        if (!MC.level.getBlockState(surface.above()).getFluidState().isEmpty())
            return false;
        BlockPos above = surface.above();
        for (BuildingPlacement bp : BuildingClientEvents.getBuildings())
            if (!bp.isDestroyedServerside && bp.isPosInsideBuilding(above))
                return false;
        return true;
    }

    private static void drawRuneCircle(PoseStack pose, BlockPos centre, boolean valid) {
        int[] fp = BuildingLineClientEvents.footprint();
        double outer = PlacementGrid.runeRadius(fp[0], fp[1]);
        double inner = outer - PlacementGrid.runeGap(outer);
        BlockPos surface = surfaceAt(centre.getX(), centre.getZ());
        double cx = centre.getX() + (fp[0] % 2 == 0 ? 0.0 : 0.5), cz = centre.getZ() + (fp[1] % 2 == 0 ? 0.0 : 0.5);
        double y = surface.getY() + 1.05;
        float r = 1.0f, g = valid ? 0.82f : 0.2f, b = valid ? 0.35f : 0.2f;
        ring(pose, cx, y, cz, outer, r, g, b, 0.75f);
        ring(pose, cx, y, cz, inner, r, g, b, 0.5f);
        int n = PlacementGrid.glyphCount(outer);
        double mid = (outer + inner) / 2, half = (outer - inner) * 0.35;
        for (int i = 0; i < n; i++) {
            double ang = 2 * Math.PI * i / n, ca = Math.cos(ang), sa = Math.sin(ang);
            // alternate a radial stroke and a small chevron: glyph marks, not a clock face
            if (i % 2 == 0) {
                MyRenderer.drawLine(pose, cx + ca * (inner + 0.04), y, cz + sa * (inner + 0.04),
                    cx + ca * (outer - 0.04), y, cz + sa * (outer - 0.04), r, g, b, 0.6f);
            } else {
                double tx = -sa * half, tz = ca * half;
                MyRenderer.drawLine(pose, cx + ca * mid - tx, y, cz + sa * mid - tz, cx + ca * (mid + half), y, cz + sa * (mid + half), r, g, b, 0.6f);
                MyRenderer.drawLine(pose, cx + ca * (mid + half), y, cz + sa * (mid + half), cx + ca * mid + tx, y, cz + sa * mid + tz, r, g, b, 0.6f);
            }
        }
    }

    private static void drawPatches(PoseStack pose, BlockPos cursor) {
        long range2 = (long) PlacementGrid.PATCH_RANGE * PlacementGrid.PATCH_RANGE;
        for (BlockPos c : MetalPatchesClient.getPatches()) {
            long dx = c.getX() - cursor.getX(), dz = c.getZ() - cursor.getZ();
            if (dx * dx + dz * dz > range2)
                continue;
            boolean taken = BuildingClientEvents.isPatchTaken(c);
            double x = c.getX() + 0.5, y = c.getY() + 1.05, z = c.getZ() + 0.5;
            if (taken) {
                ring(pose, x, y, z, 2.6, 0.55f, 0.55f, 0.55f, 0.35f);
                continue;
            }
            ring(pose, x, y, z, 2.6, 0.35f, 0.9f, 1.0f, 0.6f);
            ring(pose, x, y, z, 2.2, 0.35f, 0.9f, 1.0f, 0.3f);
            // a short light column, fading upward
            for (int k = 0; k < 4; k++)
                MyRenderer.drawLine(pose, x, y + k, z, x, y + k + 1, z, 0.55f, 0.95f, 1.0f, 0.55f - k * 0.12f);
        }
    }

    private static void ring(PoseStack pose, double cx, double y, double cz, double radius, float r, float g, float b, float a) {
        int segments = Math.max(24, (int) (radius * 12));
        double px = cx + radius, pz = cz;
        for (int i = 1; i <= segments; i++) {
            double ang = 2 * Math.PI * i / segments;
            double nx = cx + Math.cos(ang) * radius, nz = cz + Math.sin(ang) * radius;
            MyRenderer.drawLine(pose, px, y, pz, nx, y, nz, r, g, b, a);
            px = nx;
            pz = nz;
        }
    }
}
