package com.solegendary.reignofnether.environment.wind;

import com.solegendary.reignofnether.config.DisplaySettings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Client policy for the branch wind (design/branch_wind.md section 2): which loaded sway limbs the renderer draws
 * and which the chunk mesh keeps. Every {@link #POLICY_TICKS} ticks it walks the registry of loaded
 * {@link SwayBlockEntity}s (filled by their onLoad / setRemoved) and, for each whose answer changed, flips
 * {@link SwayBlockEntity#meshHidden}, refreshes its model data and marks its section for a rebuild:
 * <ul>
 *   <li>{@link #OFF}: nothing is ours - every limb is an ordinary block in the mesh;</li>
 *   <li>{@link #NEAR} (default): limbs within {@link BranchWindRenderer#SWAY_RANGE} of the camera are ours;</li>
 *   <li>{@link #ALL}: every loaded limb is ours, no distance cut.</li>
 * </ul>
 * Rebuilds are bounded by the sections that hold limbs (a few dozen on a 30-tree map), never by the terrain between.
 * Registered on the Forge bus by ClientEventRegistrar.
 */
public class BranchWindClient {

    public static final int OFF = 0, NEAR = 1, ALL = 2;
    public static final String[] MODE_NAMES = { "Off", "Near", "All" };

    static final int POLICY_TICKS = 4;
    /** A limb is handed to the renderer a little beyond the sway range so a pan never catches it in the mesh mid-ramp. */
    static final double HAND_OVER_MARGIN = 2.0;

    private static final Minecraft MC = Minecraft.getInstance();
    private static final Set<SwayBlockEntity> LOADED = new LinkedHashSet<>();
    private static ClientLevel level = null;
    private static int tick = 0;
    /** Sections marked dirty by the last policy pass (an eyes / debug number). */
    public static int lastDirtied = 0;

    /** The player's setting: OFF, NEAR or ALL. */
    public static int mode() {
        return DisplaySettings.branchWind();
    }

    static void register(SwayBlockEntity be) {
        LOADED.add(be);
        // decide now, so a freshly loaded section is meshed right the first time where we can
        be.meshHidden = wantsRenderer(be, MC.gameRenderer.getMainCamera().getPosition());
        be.requestModelDataUpdate();
    }

    static void unregister(SwayBlockEntity be) {
        LOADED.remove(be);
    }

    /** How many sway limbs are loaded on this client. */
    public static int loadedCount() {
        return LOADED.size();
    }

    private static boolean wantsRenderer(SwayBlockEntity be, Vec3 cam) {
        int mode = mode();
        if (mode == OFF)
            return false;
        if (mode == ALL)
            return true;
        BlockPos p = be.getBlockPos();
        double r = BranchWindRenderer.SWAY_RANGE + HAND_OVER_MARGIN;
        return cam.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) < r * r;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        if (MC.level != level) {       // a new world: the old entities are gone with it
            level = MC.level;
            LOADED.clear();
            return;
        }
        if (level == null || LOADED.isEmpty() || ++tick % POLICY_TICKS != 0)
            return;
        Vec3 cam = MC.gameRenderer.getMainCamera().getPosition();
        List<SwayBlockEntity> flipped = null;
        for (Iterator<SwayBlockEntity> it = LOADED.iterator(); it.hasNext(); ) {
            SwayBlockEntity be = it.next();
            if (be.isRemoved() || be.getLevel() != level) {
                it.remove();
                continue;
            }
            boolean want = wantsRenderer(be, cam);
            if (want != be.meshHidden) {
                be.meshHidden = want;
                if (flipped == null)
                    flipped = new ArrayList<>();
                flipped.add(be);
            }
        }
        lastDirtied = 0;
        if (flipped == null)
            return;
        for (SwayBlockEntity be : flipped) {
            be.requestModelDataUpdate();
            BlockPos p = be.getBlockPos();
            MC.levelRenderer.setBlocksDirty(p.getX(), p.getY(), p.getZ(), p.getX(), p.getY(), p.getZ());
        }
        lastDirtied = flipped.size();
    }
}
