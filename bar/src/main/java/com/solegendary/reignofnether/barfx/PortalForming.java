package com.solegendary.reignofnether.barfx;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.gameender.GameEnder;
import com.solegendary.reignofnether.faction.FactionTraits;

import net.minecraft.server.level.ServerLevel;
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
        BarFx.forming(level, base, half, height, style, faction);
    }
}
