package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.util.MiscUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * BAR's formation placement for buildings. With a building selected for placement:
 * <ul>
 *   <li><b>Shift + drag</b>: a line of buildings, one block apart (walls of windmills, rows of defences);</li>
 *   <li><b>Shift + Alt + drag</b>: a grid filling the dragged rectangle, one block apart each way (BAR's "stack":
 *       lovish's clip - "shift alt should let me make a stack like in BAR").</li>
 * </ul>
 * The first building is placed by the normal Shift-click; while the mouse is held, ghosts preview the rest, and
 * releasing queues every valid one on the selected workers (each its own placement, paid as normal). Invalid slots
 * are skipped, so dragging across a ridge or another building just leaves gaps.
 */
public class BuildingLineClientEvents {
    static final Minecraft MC = Minecraft.getInstance();
    static final int MAX_IN_LINE = 24;
    static final int MAX_IN_GRID = 48;   // 7x7 minus the clicked one; each ghost is validated every frame
    static final int GAP = 1;

    static BlockPos startCentre = null;   // cursor block where the drag began (the first building's centre)

    static boolean active() {
        return startCentre != null && BuildingClientEvents.getBuildingToPlace() != null
            && Keybindings.shiftMod.isDown() && OrthoviewClientEvents.isEnabled() && MC.level != null;
    }

    /** True while a Shift(+Alt)-drag formation is being laid, so the camera's Alt+LMB grab stays out of it. */
    public static boolean isDragging() {
        return active();
    }

    @SubscribeEvent
    public static void onMousePress(ScreenEvent.MouseButtonPressed.Post evt) {
        if (evt.getButton() != GLFW.GLFW_MOUSE_BUTTON_1 || BuildingClientEvents.getBuildingToPlace() == null
                || !Keybindings.shiftMod.isDown() || HudClientEvents.isMouseOverAnyButtonOrHud())
            return;
        startCentre = CursorClientEvents.getPreselectedBlockPos();
    }

    /** Ground block (the origin convention) at column (x, z). */
    static BlockPos ground(int x, int z) {
        return MiscUtil.getHighestNonAirBlock(MC.level, new BlockPos(x, 0, z), true);
    }

    /** The footprint's extent along world x and z for the current rotation (a 90-degree turn swaps them). */
    static int[] footprint() {
        Vec3i dims = BuildingClientEvents.getBuildingDimensions();
        Rotation rot = BuildingClientEvents.getBuildingRotation();
        boolean swapped = rot == Rotation.CLOCKWISE_90 || rot == Rotation.COUNTERCLOCKWISE_90;
        int sx = Math.max(1, swapped ? dims.getZ() : dims.getX());
        int sz = Math.max(1, swapped ? dims.getX() : dims.getZ());
        return new int[] { sx, sz };
    }

    /** Building origins of the formation, excluding the first (already placed by the normal click). */
    static List<BlockPos> formationOrigins() {
        List<BlockPos> out = new ArrayList<>();
        BlockPos end = CursorClientEvents.getPreselectedBlockPos();
        Building building = BuildingClientEvents.getBuildingToPlace();
        if (end == null || building == null)
            return out;
        Vec3i dims = BuildingClientEvents.getBuildingDimensions();
        Rotation rot = BuildingClientEvents.getBuildingRotation();
        boolean bridge = BuildingUtils.isBridge(building);
        int[] fp = footprint();
        if (Keybindings.altMod.isDown()) {
            // grid: every cell of the rectangle between the first building and the cursor, stepping one footprint
            // (+ gap) along each axis, row by row away from the first so the workers walk it in order
            int stepX = fp[0] + GAP, stepZ = fp[1] + GAP;
            int dx = end.getX() - startCentre.getX(), dz = end.getZ() - startCentre.getZ();
            int nx = Math.abs(dx) / stepX, nz = Math.abs(dz) / stepZ;
            int sgnX = dx < 0 ? -1 : 1, sgnZ = dz < 0 ? -1 : 1;
            for (int j = 0; j <= nz && out.size() < MAX_IN_GRID; j++)
                for (int i = 0; i <= nx && out.size() < MAX_IN_GRID; i++) {
                    if (i == 0 && j == 0)
                        continue;
                    int cx = startCentre.getX() + sgnX * i * stepX;
                    int cz = startCentre.getZ() + sgnZ * j * stepZ;
                    out.add(BuildingUtils.getBuildingOriginPos(ground(cx, cz), bridge, rot, dims));
                }
            return out;
        }
        // line: along the drag, one footprint (+ gap) apart
        int size = Math.max(fp[0], fp[1]);
        double dx = end.getX() - startCentre.getX(), dz = end.getZ() - startCentre.getZ();
        double len = Math.hypot(dx, dz);
        int step = size + GAP;
        int n = (int) Math.min(MAX_IN_LINE, Math.floor(len / step));
        for (int i = 1; i <= n; i++) {
            int cx = startCentre.getX() + (int) Math.round(dx / len * step * i);
            int cz = startCentre.getZ() + (int) Math.round(dz / len * step * i);
            out.add(BuildingUtils.getBuildingOriginPos(ground(cx, cz), bridge, rot, dims));
        }
        return out;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !active())
            return;
        for (BlockPos origin : formationOrigins())
            BuildingClientEvents.drawBuildingToPlace(evt.getPoseStack(), origin, 0);
    }

    @SubscribeEvent
    public static void onMouseRelease(ScreenEvent.MouseButtonReleased.Pre evt) {
        if (evt.getButton() != GLFW.GLFW_MOUSE_BUTTON_1)
            return;
        try {
            if (!active() || MC.player == null)
                return;
            Building building = BuildingClientEvents.getBuildingToPlace();
            String owner = MC.player.getName().getString();
            List<Integer> builderIds = new ArrayList<>();
            for (LivingEntity le : UnitClientEvents.getSelectedUnits())
                if (le instanceof WorkerUnit)
                    builderIds.add(le.getId());
            int[] ids = builderIds.stream().mapToInt(Integer::intValue).toArray();
            boolean grid = Keybindings.altMod.isDown();
            int queued = 0;
            for (BlockPos origin : formationOrigins()) {
                if (BuildingValidators.getPlacementValidityError(MC.level, building, origin, owner,
                        BuildingClientEvents.getBuildingRotation(), false, false, true) != null)
                    continue;
                BuildingServerboundPacket.placeAndQueueBuilding(building, origin,
                    BuildingClientEvents.getBuildingRotation(), owner, ids, false);
                queued++;
            }
            if (queued > 0)
                HudClientEvents.showTemporaryMessage("Queued " + (queued + 1) + (grid ? " in a grid" : " in a line"));
        } finally {
            startCentre = null;
        }
    }
}
