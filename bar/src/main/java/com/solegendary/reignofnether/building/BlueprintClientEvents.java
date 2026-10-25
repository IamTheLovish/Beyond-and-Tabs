package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.orthoview.StrategicViewClientEvents;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.util.MyRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * The client's blueprints (Blueprints, BlueprintClientboundPacket): the ghosts of sites ordered by the player and their
 * allies that no worker has started yet. Pure drawing and bookkeeping - no block is placed, nothing is registered as a
 * building, so a ghost blocks neither the cursor nor pathing; the only thing it does is reserve its footprint against
 * the same side's own overlapping placements (BuildingValidators). An enemy client never receives one.
 *
 * <p>Drawn the way the placing ghost and the fog-queued ghost are (green, translucent, BuildingClientEvents.drawBuilding),
 * from the blocks the finished placement would have. Cost: the {@link #MAX_FULL} nearest ghosts within
 * {@link #FULL_DISTANCE} draw their blocks; the rest, and every ghost in strategic zoom, draw a footprint box in the
 * owner's colour.
 */
public class BlueprintClientEvents {

    private static final List<BuildingPlacement> GHOSTS = new ArrayList<>();

    private static final int MAX_FULL = 12;
    private static final double FULL_DISTANCE = 120;
    /** A placement's blocks sit one above its origin; the ghost draws them 1 lower, as the placing ghost does (see drawBuilding). */
    private static final BlockPos DRAW_OFFSET = new BlockPos(0, -1, 0);

    public static List<BuildingPlacement> getGhosts() {
        return GHOSTS;
    }

    public static void add(@Nullable Building building, BlockPos origin, Rotation rotation, String owner, boolean diagonal) {
        Minecraft mc = Minecraft.getInstance();
        if (building == null || mc.level == null)
            return;
        remove(origin);
        BuildingPlacement ghost = BuildingUtils.getNewBuildingPlacement(building, mc.level, origin, rotation, owner, diagonal);
        if (ghost == null)
            return;
        GHOSTS.add(ghost);
        // the client-only ghost a fog-queued order drew for this same site (BuildingClientEvents) is the server's now
        BuildingClientEvents.removeFogQueuedBuilding(origin);
    }

    public static void remove(BlockPos origin) {
        GHOSTS.removeIf(g -> g.originPos.equals(origin));
    }

    public static void clear() {
        GHOSTS.clear();
    }

    private static boolean sameSide(String player, String owner) {
        return player.equals(owner) || AlliancesClient.isAllied(player, owner);
    }

    /** Server rule mirrored (Blueprints.reserves): does a ghost of {@code owner}'s side overlap the box min..max? */
    public static boolean reserves(String owner, BlockPos min, BlockPos max) {
        for (int i = 0, n = GHOSTS.size(); i < n; i++) {
            BuildingPlacement g = GHOSTS.get(i);
            if (sameSide(owner, g.ownerName) && Blueprints.overlaps(g.minCorner, g.maxCorner, min, max))
                return true;
        }
        return false;
    }

    /** Is the column of block {@code c} inside some ghost (a metal patch a queued extractor already covers)? */
    public static boolean covers(BlockPos c) {
        for (int i = 0, n = GHOSTS.size(); i < n; i++) {
            BuildingPlacement g = GHOSTS.get(i);
            if (g.minCorner.getX() <= c.getX() && g.maxCorner.getX() >= c.getX()
                    && g.minCorner.getZ() <= c.getZ() && g.maxCorner.getZ() >= c.getZ())
                return true;
        }
        return false;
    }

    /** Is the player's own ghost under {@code pos}? (right-clicking it with a worker sends the worker to build it) */
    public static boolean isOwnGhostAt(@Nullable BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (pos == null || mc.player == null)
            return false;
        String me = mc.player.getName().getString();
        for (int i = 0, n = GHOSTS.size(); i < n; i++) {
            BuildingPlacement g = GHOSTS.get(i);
            if (g.ownerName.equals(me) && (g.originPos.equals(pos) || g.isPosInsideBuilding(pos)))
                return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || GHOSTS.isEmpty())
            return;
        if (!OrthoviewClientEvents.isEnabled())
            return;
        Minecraft mc = Minecraft.getInstance();
        Entity cam = mc.cameraEntity;
        if (mc.level == null || cam == null)
            return;
        // a ghost whose building has risen goes at once (the server's remove packet normally says so first)
        GHOSTS.removeIf(g -> BuildingGrid.of(true).hasOrigin(g.originPos));

        boolean strategic = StrategicViewClientEvents.isStrategicView();
        double fullSqr = FULL_DISTANCE * FULL_DISTANCE;
        List<BuildingPlacement> near = new ArrayList<>();
        for (BuildingPlacement g : GHOSTS) {
            double dx = g.centrePos.getX() - cam.getX(), dz = g.centrePos.getZ() - cam.getZ();
            if (!strategic && dx * dx + dz * dz <= fullSqr)
                near.add(g);
            else
                drawFootprint(evt, g);
        }
        near.sort((a, b) -> Double.compare(distSqr(a, cam), distSqr(b, cam)));
        for (int i = 0; i < near.size(); i++) {
            if (i < MAX_FULL)
                BuildingClientEvents.drawBuilding(near.get(i).getBlocks(), evt.getPoseStack(), DRAW_OFFSET, 1);
            else
                drawFootprint(evt, near.get(i));
        }
    }

    private static double distSqr(BuildingPlacement g, Entity cam) {
        double dx = g.centrePos.getX() - cam.getX(), dz = g.centrePos.getZ() - cam.getZ();
        return dx * dx + dz * dz;
    }

    private static void drawFootprint(RenderLevelStageEvent evt, BuildingPlacement g) {
        Color c = new Color(PlayerColors.getPlayerDisplayColorHex(g.ownerName));
        AABB box = new AABB(g.minCorner, g.maxCorner.offset(1, 1, 1));
        MyRenderer.drawLineBox(evt.getPoseStack(), box, c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, 0.7f);
    }
}
