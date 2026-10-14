package com.solegendary.reignofnether.unit.reactions;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.AutocastState;
import com.solegendary.reignofnether.ability.abilities.KiteAbility;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.Engage;
import com.solegendary.reignofnether.unit.EngagementStance;
import com.solegendary.reignofnether.unit.Terrain;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.VeterancyServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.unit.pathfinding.MobilityClass;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Kite (claude/combat-reactions.md): a ranged unit with the switch on steps back from a melee enemy that has it as
 * its target, or stands within {@link #CLOSE_FRACTION} of its range, out to {@link #STEP_FRACTION} of its range from
 * that enemy, and keeps shooting - the ranged goals let a step finish before their in-range stop. One step per
 * {@link #STEP_COOLDOWN} ticks ({@link #STEP_COOLDOWN_VETERAN} at rank 3); never from an enemy at least
 * {@link #FASTER} times its speed (a slow death - fight instead), never into water, never when no walkable cell lies
 * behind it (cornered: stand and shoot), never on Hold Fire or hold position, never while garrisoned or riding.
 * Server side, from Unit.tick, one check every {@link #CHECK_INTERVAL} ticks per unit (staggered by id), and the
 * search is one UnitGrid box, so a line of 60 levies costs 12 boxes a tick.
 */
public final class Kite {
    private Kite() { }

    public static final int CHECK_INTERVAL = 5;
    public static final double CLOSE_FRACTION = 0.55;
    public static final double STEP_FRACTION = 0.85;
    public static final int STEP_COOLDOWN = 40;
    public static final int STEP_COOLDOWN_VETERAN = 30;
    /** A step is let walk this long before the goal's in-range stop applies again. */
    public static final int STEP_TICKS = 30;
    public static final double FASTER = 1.15;
    /** The longest one step is (blocks); the rest of the way back is the next step. */
    public static final double MAX_STEP = 6.0;
    /** A cornered unit is not re-checked for this long. */
    static final int CORNERED_RETRY = 20;

    private static final Map<Mob, Long> STEP_UNTIL = new WeakHashMap<>();
    private static final Map<Mob, Long> LAST_STEP = new WeakHashMap<>();
    private static final Map<Mob, Integer> STEPS = new WeakHashMap<>();   // for the game test
    private static final List<LivingEntity> scratch = new ArrayList<>();

    /** Whether {@code mob} has the reaction at all (the pilot's ranged units carry the ability). */
    public static boolean has(Unit unit) {
        for (var a : unit.getAbilities().get())
            if (a == KiteAbility.INSTANCE)
                return true;
        return false;
    }

    /** A step is in progress: the ranged goals must not stop the navigation. */
    public static boolean stepping(Mob mob) {
        Long until = STEP_UNTIL.get(mob);
        if (until == null)
            return false;
        if (mob.level().getGameTime() > until || mob.getNavigation().isDone()) {
            STEP_UNTIL.remove(mob);
            return false;
        }
        return true;
    }

    /** For tests: how many steps this unit has taken. */
    public static int steps(Mob mob) {
        return STEPS.getOrDefault(mob, 0);
    }

    /** Server side, every tick from Unit.tick. */
    public static void tick(Mob mob, Unit unit) {
        if ((mob.tickCount + mob.getId()) % CHECK_INTERVAL != 0 || !(mob.level() instanceof ServerLevel level))
            return;
        if (!(unit instanceof RangedAttackerUnit) || unit instanceof WorkerUnit || !(unit instanceof AttackerUnit au))
            return;
        if (!has(unit) || !AutocastState.isOn(unit, KiteAbility.INSTANCE))
            return;
        if (mob.isPassenger() || unit.getHoldPosition() || EngagementStance.of(mob) == EngagementStance.HOLD_FIRE
                || GarrisonableBuildingAddon.getGarrison(unit) != null)
            return;
        if (mob.getTarget() == null || !mob.getTarget().isAlive() || stepping(mob))
            return;
        long now = level.getGameTime();
        Long last = LAST_STEP.get(mob);
        int cooldown = VeterancyServerEvents.getRank(mob) >= VeterancyServerEvents.MAX_RANK ? STEP_COOLDOWN_VETERAN : STEP_COOLDOWN;
        if (last != null && now - last < cooldown)
            return;
        float range = au.getAttackRange();
        double close = Math.max(2.0, CLOSE_FRACTION * range);
        LivingEntity threat = threat(level, mob, unit, close);
        if (threat == null)
            return;
        BlockPos cell = stepCell(level, mob, unit, threat, range);
        if (cell == null) {
            LAST_STEP.put(mob, now - cooldown + CORNERED_RETRY);   // cornered: stand and shoot, look again shortly
            return;
        }
        Path path = mob.getNavigation().createPath(cell, 0);
        if (path == null || !path.canReach()) {
            LAST_STEP.put(mob, now - cooldown + CORNERED_RETRY);
            return;
        }
        mob.getNavigation().moveTo(path, 1.0);
        STEP_UNTIL.put(mob, now + STEP_TICKS);
        LAST_STEP.put(mob, now);
        STEPS.merge(mob, 1, Integer::sum);
        ReignOfNether.LOGGER.debug("[Kite] {}#{} steps to {} from {}", mob.getType().toShortString(), mob.getId(),
            cell.toShortString(), threat.getType().toShortString());
    }

    /** The nearest melee enemy that is coming for this unit (its target) or already inside {@code close} blocks. */
    @Nullable
    static LivingEntity threat(ServerLevel level, Mob mob, Unit unit, double close) {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        // its base speed, not the value: the crossbow's charging slow (-20%) would make an equal-speed ghoul look faster
        float mySpeed = unit.getBaseMovementSpeed();
        for (LivingEntity le : UnitGrid.near(level, mob.getX(), mob.getZ(), close + 1, scratch)) {
            if (le == mob || !le.isAlive() || !(le instanceof AttackerUnit) || le instanceof RangedAttackerUnit
                    || le instanceof WorkerUnit || !(le instanceof Unit lu) || !(le instanceof Mob foe))
                continue;
            if (UnitServerEvents.getUnitToEntityRelationship(unit, le) != Relationship.HOSTILE)
                continue;
            double d = mob.distanceToSqr(le);
            boolean comingForMe = foe.getTarget() == mob;
            if (!comingForMe && d > close * close)
                continue;
            if (lu.getMovementSpeed() >= FASTER * mySpeed)
                continue;   // kiting a faster unit is a slow death
            if (d < bestD) {
                bestD = d;
                best = le;
            }
        }
        scratch.clear();
        return best;
    }

    /**
     * The cell to step to: on the line from the threat through this unit, {@link #STEP_FRACTION} of the range from
     * the threat (at most {@link #MAX_STEP} from here), then two shorter tries; dry, standable, not where it stands.
     */
    @Nullable
    static BlockPos stepCell(ServerLevel level, Mob mob, Unit unit, LivingEntity threat, float range) {
        double dx = mob.getX() - threat.getX(), dz = mob.getZ() - threat.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01) {
            dx = 1;
            dz = 0;
            len = 1;
        }
        dx /= len;
        dz /= len;
        double want = STEP_FRACTION * range - len;   // how much further back than here
        double step = Math.min(MAX_STEP, Math.max(2.0, want));
        MobilityClass mobility = MobilityClass.of(unit);
        BlockPos here = BlockPos.containing(mob.getX(), mob.getY() + 0.01, mob.getZ());
        for (double f : new double[] { 1.0, 0.66, 0.4 }) {
            double s = step * f;
            if (s < 1.0)
                break;
            BlockPos cell = Engage.standCell(level, BlockPos.containing(mob.getX() + dx * s, mob.getY() + 0.01, mob.getZ() + dz * s), mobility);
            if (cell == null || cell.equals(here) || !Terrain.isLand(level, cell))
                continue;
            return cell;
        }
        return null;
    }
}
