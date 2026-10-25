package com.solegendary.reignofnether.unit.support;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.building.addon.RangeIndicatorAddon;
import com.solegendary.reignofnether.building.buildings.brass.RailBattery;
import com.solegendary.reignofnether.building.buildings.defence.DefenceTower;
import com.solegendary.reignofnether.building.buildings.tide.LighthouseBattery;
import com.solegendary.reignofnether.building.buildings.verdant.StormOak;
import com.solegendary.reignofnether.unit.Concealment;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.command.CommandKind;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.SupportUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * How a support unit reads an order (claude/healer-ai-oct21.md, A) - the one branch UnitActionItem gives it, server
 * side, before its own switch. The dispatch used to treat a unit that cannot attack as "follow the enemy" on an attack
 * order and as a plain walk on Fight, so healers walked into melee and died (healer-ai root cause 1). Now:
 * <ul>
 *   <li><b>Attack</b> on an enemy unit: a unit with an area harm ({@link SupportUnit#hasSupportDamage}, the Field
 *       Medic) holds its cast-range standoff from it and casts the harm whenever no friend is near it
 *       (SupportBrain.cast); a pure healer
 *       escorts the nearest fighter of the selection, staying behind it - with no fighter selected it holds the same
 *       standoff from the target;</li>
 *   <li><b>Fight</b> (attack-move): the support units of the order take the rear rank, reach - 1.5 blocks short of the
 *       click (at most 6.5), spread across the move; on the way, whenever our front is in a fight they hold that far behind
 *       it;</li>
 *   <li><b>Attack building</b>: outside the building's own guns (a tower's range + 2, or just clear of its walls);</li>
 *   <li>an area attack is a Fight for them (AreaCommands).</li>
 * </ul>
 * Every other order (Stop, Move, Hold, Follow, an ability click...) ends the support order and runs as before; a
 * unit's own autocast and a state toggle do not. The client keeps its old prediction (the order lines).
 */
public final class SupportOrders {
    private SupportOrders() { }

    /** Support units of one Fight order stand this far apart across the move. */
    public static final double REAR_SPACING = 2.0;
    /** An attacked building's guns are kept this much further off than their range. */
    public static final double GUN_MARGIN = 2.0;
    /** A building that does not shoot: stand this far outside its footprint. */
    public static final double WALL_MARGIN = 2.5;

    private static final double[] P = new double[2];

    /**
     * Called by UnitActionItem for each support unit an order reaches. True when this took the order (the dispatch then
     * skips its own case for the unit).
     */
    public static boolean order(Level level, SupportUnit unit, UnitAction action, int targetId, BlockPos pos,
                                List<Unit> selection, boolean autocast) {
        if (level.isClientSide() || autocast || CommandKind.isStateCommand(action))
            return false;
        unit.getSupportState().endOrder();
        return switch (action) {
            case ATTACK -> attack(level, unit, targetId, selection);
            case ATTACK_MOVE -> fight(unit, pos, selection);
            case ATTACK_BUILDING -> attackBuilding(unit, pos);
            default -> false;
        };
    }

    static boolean attack(Level level, SupportUnit unit, int targetId, List<Unit> selection) {
        Entity e = targetId >= 0 ? level.getEntity(targetId) : null;
        if (!(e instanceof LivingEntity target) || !(e instanceof Unit) || !target.isAlive())
            return false;   // an animal or nothing: the old reading (follow) is harmless there
        if (UnitServerEvents.getUnitToEntityRelationship(unit, target) == Relationship.FRIENDLY)
            return false;   // an "attack" on a friend: follow it, as before
        if (Concealment.isHiddenFrom(target, unit.getOwnerName()))
            return false;   // the dispatch's rule for a hidden target: a move to where it was seen
        LivingEntity self = (LivingEntity) unit;
        LivingEntity anchor = unit.hasSupportDamage() ? null : nearestFighter(self, selection);
        SupportState s = unit.getSupportState();
        if (anchor != null)
            s.escort(target, anchor, fighterIds(self, selection));
        else
            s.standoffFrom(target);
        return true;
    }

    static boolean fight(SupportUnit unit, @Nullable BlockPos pos, List<Unit> selection) {
        if (pos == null || pos.equals(BlockPos.ZERO))
            return false;
        LivingEntity self = (LivingEntity) unit;
        double gx = 0, gz = 0;
        int g = 0;
        for (Unit u : selection)
            if (u instanceof LivingEntity le) {
                gx += le.getX();
                gz += le.getZ();
                g++;
            }
        if (g == 0)
            return false;
        gx /= g;
        gz /= g;
        double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5;
        // this unit's place in the rear rank: the order's support units, in the order they stand across the move
        double mine = SupportMath.across(gx, gz, cx, cz, self.getX(), self.getZ());
        int index = 0, count = 0;
        for (Unit u : selection)
            if (u instanceof SupportUnit && u instanceof LivingEntity le) {
                count++;
                double a = SupportMath.across(gx, gz, cx, cz, le.getX(), le.getZ());
                if (a < mine || (a == mine && le.getId() < self.getId()))
                    index++;
            }
        SupportMath.rearRank(gx, gz, cx, cz, SupportBrain.standoff(unit.getSupportReach()), index, count, REAR_SPACING, P);
        BlockPos dest = new BlockPos(Mth.floor(P[0]), pos.getY(), Mth.floor(P[1]));
        unit.getSupportState().fight(dest);
        unit.getMoveGoal().setMoveTarget(dest);   // on its way now; the brain takes over at its next look
        return true;
    }

    static boolean attackBuilding(SupportUnit unit, @Nullable BlockPos pos) {
        BuildingPlacement b = pos == null ? null : BuildingUtils.findBuilding(false, pos);
        if (b == null || b.isDestroyedServerside)
            return false;
        double half = Math.max(b.maxCorner.getX() - b.minCorner.getX(), b.maxCorner.getZ() - b.minCorner.getZ()) / 2.0;
        double keepOut = Math.max(gunRange(b) + GUN_MARGIN, half + WALL_MARGIN);
        unit.getSupportState().standoffBuilding(pos, b.centrePos.getX() + 0.5, b.centrePos.getZ() + 0.5, keepOut);
        return true;
    }

    /**
     * How far a building shoots at ground units: a defence tower's range (not the anti-air ones), the T2 faction
     * towers' (Storm Oak, Lighthouse Battery, Rail Battery), or its garrison's while someone is inside; else 0.
     */
    static int gunRange(BuildingPlacement b) {
        Building building = b.getBuilding();
        int range = 0;
        if (building instanceof DefenceTower tower && !tower.kind.airOnly)
            range = tower.kind.range;
        else if ((building instanceof StormOak || building instanceof LighthouseBattery || building instanceof RailBattery)
                && building instanceof RangeIndicatorAddon ria)
            range = ria.getRange(b);
        GarrisonableBuildingAddon garrison = building.getActiveAddon(GarrisonableBuildingAddon.class);
        if (garrison != null && GarrisonableBuildingAddon.getNumOccupants(b) > 0)
            range = Math.max(range, garrison.getAttackRange());
        return range;
    }

    @Nullable
    static LivingEntity nearestFighter(LivingEntity self, List<Unit> selection) {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (Unit u : selection)
            if (u instanceof AttackerUnit && !(u instanceof SupportUnit) && u instanceof LivingEntity le
                    && le != self && le.isAlive()) {
                double d = le.distanceToSqr(self);
                if (d < bestD) {
                    bestD = d;
                    best = le;
                }
            }
        return best;
    }

    static int[] fighterIds(LivingEntity self, List<Unit> selection) {
        int n = 0;
        for (Unit u : selection)
            if (u instanceof AttackerUnit && !(u instanceof SupportUnit) && u instanceof LivingEntity le && le != self)
                n++;
        int[] ids = new int[n];
        int i = 0;
        for (Unit u : selection)
            if (u instanceof AttackerUnit && !(u instanceof SupportUnit) && u instanceof LivingEntity le && le != self)
                ids[i++] = le.getId();
        return ids;
    }
}
