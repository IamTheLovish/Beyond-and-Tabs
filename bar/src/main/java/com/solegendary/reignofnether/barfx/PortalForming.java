package com.solegendary.reignofnether.barfx;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.gameender.GameEnder;
import com.solegendary.reignofnether.faction.FactionTraits;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The forming moment's server side (lovish, Nov 5: "our own version" - a portal-type building does not switch on at
 * once, it forms). The one shared entry is {@link BuildingPlacement#onBuilt}: for a building whose
 * {@link com.solegendary.reignofnether.building.Building#formsWhenBuilt} is on (every portal, every game ender) it
 * sends one BarFx FORMING event, which the per-tick queue gives each fogged player only when their fog shows part of
 * the site; the client plays the sight (FormingFx) and the sound (SfxClient) from that one event. Nothing here ticks:
 * the whole cost is one event per activation.
 */
public final class PortalForming {
    private PortalForming() { }

    /**
     * A building finished younger than this (ticks since its placement appeared) was finished the moment it appeared -
     * a map's neutral portal, a scripted base - and has no moment to show (the same guard as the client's built motif).
     */
    public static final int MIN_AGE_TICKS = 40;

    /** Called when a placement completes on the server. True if it formed (once per activation). */
    public static boolean onBuilt(BuildingPlacement bp) {
        if (bp == null || bp.formingShown || !(bp.getLevel() instanceof ServerLevel level) || bp.getBuilding() == null
                || !bp.getBuilding().formsWhenBuilt() || bp.tickAge < MIN_AGE_TICKS)
            return false;
        bp.formingShown = true;
        send(level, bp);
        return true;
    }

    /**
     * The FORMING event for a placement's footprint, with no once-only or age guard: {@link #onBuilt} after its
     * checks, and the eyes scene 20_forming, which replays one site to photograph each phase.
     */
    public static void send(ServerLevel level, BuildingPlacement bp) {
        // the footprint: half its larger horizontal side, its height, the centre of its floor
        float half = Math.max(bp.maxCorner.getX() - bp.minCorner.getX(), bp.maxCorner.getZ() - bp.minCorner.getZ()) / 2f + .5f;
        float height = bp.maxCorner.getY() - bp.minCorner.getY() + 1;
        Vec3 base = new Vec3((bp.minCorner.getX() + bp.maxCorner.getX() + 1) / 2.0, bp.minCorner.getY(),
            (bp.minCorner.getZ() + bp.maxCorner.getZ() + 1) / 2.0);
        byte style = bp.getBuilding() instanceof GameEnder ? BarFx.FS_ENDER : BarFx.FS_PORTAL;
        byte faction = BarFx.F_NONE;
        try {
            faction = FactionTraits.of(bp.getFaction()).deathDebris;
        } catch (Exception ignored) { }
        BarFx.forming(level, base, half, height, style, faction, style == BarFx.FS_PORTAL ? doorway(bp, base) : 0);
    }

    /*
     * The doorway, so the client traces the real frame (Fable critic round 2: "the spark along the frame is not on the
     * frame" - the footprint alone is the whole plinth). It is the box of the placement's NETHER_PORTAL blocks, packed
     * into the event's free float in half blocks relative to the footprint's centre and floor; 0 = none (the client
     * then makes a doorway of its own). Bits: 0-3 the width, 4-8 the along-offset x2 +16, 9-13 the across-offset x2
     * +16, 14-17 the bottom, 18-21 the top, 22 the door runs along x; plus 1, so 0 stays "none". Under 2^24: exact.
     */
    static float doorway(BuildingPlacement bp, Vec3 base) {
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y0 = Integer.MAX_VALUE, y1 = Integer.MIN_VALUE,
            z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
        try {
            for (var b : bp.getBlocks()) {
                if (b.getBlockState().getBlock() != Blocks.NETHER_PORTAL)
                    continue;
                var p = b.getBlockPos();
                x0 = Math.min(x0, p.getX()); x1 = Math.max(x1, p.getX());
                y0 = Math.min(y0, p.getY()); y1 = Math.max(y1, p.getY());
                z0 = Math.min(z0, p.getZ()); z1 = Math.max(z1, p.getZ());
            }
        } catch (Exception e) {
            return 0;
        }
        if (x0 == Integer.MAX_VALUE)
            return 0;
        boolean alongX = x1 - x0 >= z1 - z0;
        double cx = (x0 + x1 + 1) / 2.0, cz = (z0 + z1 + 1) / 2.0;
        int width = clamp(alongX ? x1 - x0 + 1 : z1 - z0 + 1, 1, 15);
        int along = clamp((int) Math.round((alongX ? cx - base.x : cz - base.z) * 2) + 16, 0, 31);
        int across = clamp((int) Math.round((alongX ? cz - base.z : cx - base.x) * 2) + 16, 0, 31);
        int floor = (int) Math.floor(base.y);
        int bottom = clamp(y0 - floor, 0, 15), top = clamp(y1 - floor, 0, 15);
        return 1 + (width | along << 4 | across << 9 | bottom << 14 | top << 18 | (alongX ? 1 << 22 : 0));
    }

    /** {@link #doorway}'s fields: { along x (1/0), half-width, along-offset, across-offset, bottom, top (exclusive) }, or null. */
    static float[] doorwayOf(float code) {
        if (code < 1)
            return null;
        int c = (int) code - 1;
        return new float[] { (c >> 22) & 1, (c & 15) / 2f, (((c >> 4) & 31) - 16) / 2f, (((c >> 9) & 31) - 16) / 2f,
            (c >> 14) & 15, ((c >> 18) & 15) + 1 };
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
