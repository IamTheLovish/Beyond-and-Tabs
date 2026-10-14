package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.blocks.ThicketCover;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.brass.DrillBurrowerUnit;
import com.solegendary.reignofnether.unit.units.verdant.ShadeRangerUnit;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * One server-side answer to "can this player's side see / target that unit?" for the ways a unit hides from its
 * enemies: the Shade Ranger's cloak ({@link ShadeRangerUnit#isCloaked}), cover ({@link ThicketCover#isHidden}: a
 * Verdant thicket or a Tidewrought Fog Bank) and a Brassworks Drill Burrower under the ground (DrillBurrowerUnit.isBurrowed). The server is the authority here - the client also refuses to hover / select a unit
 * that is invisible to the local player (UnitClientEvents), but a modified client could still send its id, so orders
 * (UnitActionItem), area attack (AreaCommands) and the per-tick target sync (Unit.tick) all ask this.
 * <p>
 * Hidden is not invulnerable: nothing here touches damage, so splash / AoE still hits a hidden unit (a burrowed Drill
 * Burrower is the exception, by its own isInvulnerableTo: it is under the ground).
 * <p>
 * Cheap enough for per-unit-per-tick use: the concealed test is a flag read plus an empty-map check in the common
 * case, and the alliance lookup only runs for a unit that actually is concealed.
 */
public final class Concealment {
    private Concealment() { }

    /** Is this entity hiding right now (from its enemies)? Server side. */
    public static boolean isConcealed(Entity e) {
        return ShadeRangerUnit.isCloaked(e) || ThicketCover.isHidden(e)
            || DrillBurrowerUnit.isBurrowed(e);   // under the ground (Brassworks)
    }

    /**
     * Is the target hidden from the side of {@code viewerOwner}? A concealed unit is hidden from everyone but its
     * owner and the owner's allies; an ownerless viewer (neutral creeps, an unowned attacker) doesn't see it either.
     */
    public static boolean isHiddenFrom(Entity target, String viewerOwner) {
        if (!(target instanceof Unit u) || !isConcealed(target))
            return false;
        if (viewerOwner == null || viewerOwner.isBlank())
            return true;
        String o = u.getOwnerName();
        return !(viewerOwner.equals(o) || AlliancesServerEvents.isAllied(viewerOwner, o));
    }

    /**
     * The building twin of {@link #isHiddenFrom}: a trap (design/defences.md) that has not fired yet is hidden from
     * everyone but its owner's side - FogOfWarServerEvents never sends it to them, and it can't be attacked.
     */
    public static boolean isBuildingHiddenFrom(com.solegendary.reignofnether.building.BuildingPlacement b, String viewerOwner) {
        return b instanceof com.solegendary.reignofnether.building.buildings.defence.Trap.TrapPlacement t && t.isHiddenFrom(viewerOwner);
    }

    /**
     * Whether placement {@code b} must not be sent to the player {@code viewer} (FogOfWarServerEvents
     * .canPlayerSeeBuilding): a hidden trap, for an RTS player of another side - and for a Hero-role player who serves
     * none of the trap's side. A Hero-role player has no base, so they are not an RTS player and were let through like
     * a spectator: a free trap scout for the enemy (design/code_review.md R2-05, the CR-06 family). Spectators still
     * see every trap.
     */
    public static boolean isBuildingHiddenFromPlayer(com.solegendary.reignofnether.building.BuildingPlacement b,
                                                     String viewer, boolean viewerIsRtsPlayer) {
        if (!isBuildingHiddenFrom(b, viewer))
            return false;
        if (viewerIsRtsPlayer)
            return true;
        if (!com.solegendary.reignofnether.hero.HeroRoles.isHeroRole(viewer))
            return false;
        for (String commander : com.solegendary.reignofnether.hero.HeroRoles.commandersOf(viewer))
            if (!isBuildingHiddenFrom(b, commander))
                return false;   // the hero's own team's trap
        return true;
    }

    /**
     * Strips a unit of its sight cover for {@code ticks} (Holy Bell): it can't re-cloak or re-hide in a thicket or a
     * Fog Bank until the time is up, so the bell's glow and fog reveal actually let the enemy shoot at it. A burrowed
     * Drill Burrower is NOT surfaced: it is under the ground, not hidden by sight (open design question, see
     * claude/code-quality-log.md Oct 14).
     */
    public static void revealFor(LivingEntity le, int ticks) {
        ThicketCover.revealFor(le, ticks);
        if (le instanceof ShadeRangerUnit sr)
            sr.revealFor(ticks);
    }
}
