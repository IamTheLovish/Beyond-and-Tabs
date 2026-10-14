package com.solegendary.reignofnether.ability;

import com.solegendary.reignofnether.mana.ManaServerEvents;
import com.solegendary.reignofnether.mana.SealServerEvents;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.Concealment;
import com.solegendary.reignofnether.unit.EngagementStance;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.goals.GenericTargetedSpellGoal;
import com.solegendary.reignofnether.unit.goals.GenericUntargetedSpellGoal;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.List;

/**
 * Units cast their abilities by themselves (the developer's rule: "units should be able to use abilities on their own -
 * unless they are a hero"). Once a second each unit with abilities gets one look - the units are spread over the 20
 * ticks by id ({@code id % 20}), and a tick stops early past {@link #BUDGET_NANOS} (the rest wait a second) - and for
 * every ability that is switched on ({@link AutocastState}), off cooldown, affordable and whose {@link AutoRule} finds
 * a target, the unit gives itself the order a click would: {@link UnitActionItem#action} in its own name, so the
 * cooldown, charges, mana, the Seal, concealment and the behaviour reset are exactly a click's. One cast per unit per
 * look. Targets come from {@link UnitGrid} (no scan of every unit).
 * <p>
 * Never for a hero, a commander or a possessed unit ({@link AutocastState#isHero}); never for an ability the unit's
 * own goal casts ({@link Ability#autocastByUnit}); never on a friend for an enemy rule or on an enemy for an ally rule;
 * a unit on hold fire never casts at enemies by itself and one on return fire only at the unit it is already fighting;
 * never while stunned, frozen, uncontrollable, sealed, garrisoned, mid-cast or walking a move order (the cast's reset
 * would stop it). Energy spells cast only while the owner's pool keeps {@link #ENERGY_RESERVE} over the cost.
 * Server thread only; hooked from UnitServerEvents.onWorldTick.
 */
public final class AutoCaster {
    private AutoCaster() { }

    /** Time one tick's slot may spend; past it the remaining units of the slot wait for their next second. */
    public static final long BUDGET_NANOS = 1_500_000L;
    /** The 3 s in which a hit counts as "in combat" for SELF_IN_COMBAT / SUMMON_WHEN_FREE. */
    public static final int HURT_RECENTLY_TICKS = 60;
    /** ALLY_HURT heals a friend under this fraction of its health. */
    public static final float ALLY_HURT_FRACTION = 0.7f;
    /** CROWD needs this many enemies in the radius. */
    public static final int CROWD_MIN = 3;
    /** The radius CROWD counts in when the ability has none of its own, and the reach a rangeless ability targets in. */
    public static final float DEFAULT_RADIUS = 4f, DEFAULT_REACH = 6f;
    /** Energy an autocast leaves in the pool (a Heaven's Judgement: the commander's D-gun always comes first). */
    public static final float ENERGY_RESERVE = 150f;
    private static final int SLOTS = 20;

    private static final List<LivingEntity> SCRATCH = new ArrayList<>(), SCRATCH2 = new ArrayList<>();

    // the last pass, for the stress line and the game tests
    public static long lastPassNanos = 0;
    public static int lastPassUnits = 0, lastPassCasts = 0, lastPassSkipped = 0;

    /** One tick: the units whose id falls in this tick's slot. */
    public static void tick(ServerLevel level) {
        pass(level, (int) Math.floorMod(level.getGameTime(), SLOTS), BUDGET_NANOS);
    }

    /**
     * One look at every unit in {@code slot} ({@code id % 20}; -1 = every unit, the tests' full pass), within
     * {@code budgetNanos}. Returns how many casts it gave.
     */
    public static int pass(ServerLevel level, int slot, long budgetNanos) {
        long start = System.nanoTime();
        int units = 0, casts = 0, skipped = 0;
        List<LivingEntity> all = UnitServerEvents.getAllUnits();
        for (int i = 0, n = all.size(); i < n; i++) {
            LivingEntity le = all.get(i);
            if (slot >= 0 && Math.floorMod(le.getId(), SLOTS) != slot)
                continue;
            if (!(le instanceof Unit unit) || le.level() != level || !le.isAlive() || unit.getAbilities().isEmpty())
                continue;
            if (System.nanoTime() - start > budgetNanos) {
                skipped++;
                continue;
            }
            units++;
            if (look(level, le, unit))
                casts++;
        }
        lastPassNanos = System.nanoTime() - start;
        lastPassUnits = units;
        lastPassCasts = casts;
        lastPassSkipped = skipped;
        return casts;
    }

    /** One unit's look: the first ability whose rule is met is cast. True if it cast. */
    public static boolean look(ServerLevel level, LivingEntity le, Unit unit) {
        String owner = unit.getOwnerName();
        if (owner == null || owner.isBlank() || AutocastState.isHero(le))
            return false;
        if (le.hasEffect(MobEffectRegistrar.STUN.get()) || le.hasEffect(MobEffectRegistrar.FREEZE.get())
                || le.hasEffect(MobEffectRegistrar.UNCONTROLLABLE.get()))
            return false;
        if (SealServerEvents.isSealed(le) || unit.ignoreNonStopCommands() || unit.isGarrisoned() || unit.isCasting()
                || spellGoalBusy(le))
            return false;
        // a unit walking a move order (or tailing a friend) is left to it: the cast's behaviour reset would stop it
        if (unit.getFollowTarget() != null || (unit.getMoveGoal() != null && unit.getMoveGoal().getMoveTarget() != null))
            return false;
        for (Ability ability : unit.getAbilities().get()) {
            if (ability.autocastByUnit || ability.action == null)
                continue;
            AutoRule rule = ability.getAutoRule();
            if (rule == AutoRule.MANUAL || !AutocastState.isOn(unit, ability))
                continue;
            if (!ability.isOffCooldown(unit) && !ability.canBypassCooldown(unit))
                continue;
            if (ability.autocastEnergy > 0 && !canAffordEnergy(owner, ability.autocastEnergy))
                continue;
            if (!ability.canAutocastNow(level, unit))
                continue;
            if (cast(level, le, unit, ability, rule))
                return true;
        }
        return false;
    }

    /** Works out the target for {@code rule} and gives the order. True if an order was given. */
    static boolean cast(ServerLevel level, LivingEntity self, Unit unit, Ability ability, AutoRule rule) {
        switch (rule) {
            case SELF_IN_COMBAT -> {
                if (!inCombat(self, unit))
                    return false;
                return order(level, self, unit, ability, -1, self.blockPosition());
            }
            case SUMMON_WHEN_FREE -> {
                if (!inCombat(self, unit) || ability.liveSummons(level, unit) >= ability.summonCap())
                    return false;
                return order(level, self, unit, ability, -1, self.blockPosition());
            }
            case ENEMY_IN_RANGE -> {
                LivingEntity enemy = bestEnemy(level, self, unit, ability, reach(ability));
                if (enemy == null)
                    return false;
                return orderAt(level, self, unit, ability, enemy);
            }
            case CROWD -> {
                float radius = ability.radius > 0 ? ability.radius : DEFAULT_RADIUS;
                if (ability.range > 0) {
                    LivingEntity centre = bestEnemy(level, self, unit, ability, ability.range);
                    if (centre == null || enemiesAround(level, unit, centre.getX(), centre.getZ(), radius, centre) < CROWD_MIN)
                        return false;
                    return orderAt(level, self, unit, ability, centre);
                }
                if (enemiesAround(level, unit, self.getX(), self.getZ(), radius, null) < CROWD_MIN)
                    return false;
                return order(level, self, unit, ability, -1, self.blockPosition());
            }
            case ALLY_HURT -> {
                LivingEntity ally = mostHurtAlly(level, self, unit, ability, reach(ability));
                if (ally == null)
                    return false;
                if (ability.canTargetEntities)
                    return order(level, self, unit, ability, ally.getId(), ally.blockPosition());
                // a self-centred heal is cast where the healer stands; an aimed one at the friend's feet
                return order(level, self, unit, ability, -1, ability.range > 0 ? ally.blockPosition() : self.blockPosition());
            }
            default -> {
                return false;
            }
        }
    }

    private static boolean orderAt(ServerLevel level, LivingEntity self, Unit unit, Ability ability, LivingEntity target) {
        // a click on a unit for a ground-aimed ability is the ground under it (UnitActionItem does the same for a hidden one)
        return order(level, self, unit, ability, ability.canTargetEntities ? target.getId() : -1, target.blockPosition());
    }

    /** The order itself: a click's path (UnitActionItem) in the unit's own name, flagged so it spares a patrol or guard. */
    private static boolean order(ServerLevel level, LivingEntity self, Unit unit, Ability ability, int targetId, BlockPos pos) {
        float before = unit.getCooldown(ability);
        int chargesBefore = ability.usesCharges() ? unit.getAbilityCharges(ability) : 0;
        new UnitActionItem(unit.getOwnerName(), ability.action, targetId, new int[] { self.getId() }, pos, BlockPos.ZERO)
            .asAutocast()
            .action(level);
        // an ability that refused (no wreck, no spot, no energy) sets no cooldown: it is not a cast, the next ability may
        // try. One cast through a spell goal (the warden's boom, the evoker's fangs) sets its cooldown when the goal
        // fires, so a goal that now holds a target counts too
        return unit.getCooldown(ability) > before || (ability.usesCharges() && unit.getAbilityCharges(ability) < chargesBefore)
            || ability.isCasting(unit) || spellGoalBusy(self);
    }

    /** A spell goal of the unit holds a target or is casting: an order it took and will carry out. */
    static boolean spellGoalBusy(LivingEntity self) {
        if (!(self instanceof Mob mob))
            return false;
        for (net.minecraft.world.entity.ai.goal.WrappedGoal wg : mob.goalSelector.getAvailableGoals()) {
            net.minecraft.world.entity.ai.goal.Goal goal = wg.getGoal();
            if (goal instanceof GenericTargetedSpellGoal g && (g.isCasting() || g.getTargetEntity() != null || g.getCastTarget() != null))
                return true;
            if (goal instanceof GenericUntargetedSpellGoal g && g.isCasting())
                return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ the rules' pieces

    static boolean inCombat(LivingEntity self, Unit unit) {
        if (unit.hasLivingTarget())
            return true;
        return self.getLastHurtByMob() != null && self.tickCount - self.getLastHurtByMobTimestamp() <= HURT_RECENTLY_TICKS;
    }

    static float reach(Ability ability) {
        if (ability.range > 0)
            return ability.range;
        return ability.radius > 0 ? ability.radius : DEFAULT_REACH;
    }

    static boolean enemy(Unit unit, LivingEntity self, LivingEntity le) {
        return le != self && le.isAlive() && le instanceof Unit && !(le instanceof Mob m && m.isDeadOrDying())
            && UnitServerEvents.getUnitToEntityRelationship(unit, le) == Relationship.HOSTILE
            && !Concealment.isHiddenFrom(le, unit.getOwnerName());
    }

    static boolean friend(Unit unit, LivingEntity self, LivingEntity le) {
        return le.isAlive() && le instanceof Unit
            && UnitServerEvents.getUnitToEntityRelationship(unit, le) == Relationship.FRIENDLY;
    }

    static boolean within(LivingEntity self, LivingEntity le, float reach) {
        double dx = le.getX() - self.getX(), dz = le.getZ() - self.getZ();
        return dx * dx + dz * dz <= (reach + le.getBbWidth() / 2) * (reach + le.getBbWidth() / 2) && Math.abs(le.getY() - self.getY()) <= 6;
    }

    /**
     * The enemy to cast on: the unit's current target when it is in reach and allowed, else the nearest hostile unit
     * in reach that the ability accepts. A unit on hold fire picks none; one on return fire only its current target.
     */
    static LivingEntity bestEnemy(ServerLevel level, LivingEntity self, Unit unit, Ability ability, float reach) {
        if (!EngagementStance.mayRetaliate(self))
            return null;
        LivingEntity target = self instanceof Mob m ? m.getTarget() : null;
        if (target != null && enemy(unit, self, target) && within(self, target, reach) && ability.autocastTargetOk(unit, target))
            return target;
        if (!EngagementStance.mayAutoAcquire(self))
            return null;
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity le : UnitGrid.near(level, self.getX(), self.getZ(), reach + 1, SCRATCH)) {
            if (!enemy(unit, self, le) || !within(self, le, reach) || !ability.autocastTargetOk(unit, le))
                continue;
            double d = le.distanceToSqr(self);
            if (d < bestD) {
                bestD = d;
                best = le;
            }
        }
        return best;
    }

    /** Hostile units within {@code radius} of (x, z), {@code centre} itself counted when given. */
    static int enemiesAround(ServerLevel level, Unit unit, double x, double z, float radius, LivingEntity centre) {
        int n = 0;
        LivingEntity self = (LivingEntity) unit;
        for (LivingEntity le : UnitGrid.near(level, x, z, radius + 1, SCRATCH2)) {
            if (le != centre && !enemy(unit, self, le))
                continue;
            double dx = le.getX() - x, dz = le.getZ() - z;
            if (dx * dx + dz * dz <= radius * radius)
                n++;
        }
        return n;
    }

    /** The most hurt friendly unit in reach under {@link #ALLY_HURT_FRACTION}; the caster itself only for a self-centred area. */
    static LivingEntity mostHurtAlly(ServerLevel level, LivingEntity self, Unit unit, Ability ability, float reach) {
        LivingEntity best = null;
        float bestFrac = ALLY_HURT_FRACTION;
        for (LivingEntity le : UnitGrid.near(level, self.getX(), self.getZ(), reach + 1, SCRATCH)) {
            if (le == self ? ability.canTargetEntities || ability.range > 0 : !friend(unit, self, le))
                continue;
            if (le.getMaxHealth() <= 0 || !within(self, le, reach) || !ability.autocastTargetOk(unit, le))
                continue;
            float frac = le.getHealth() / le.getMaxHealth();
            if (frac < bestFrac) {
                bestFrac = frac;
                best = le;
            }
        }
        return best;
    }

    /** The owner's pool holds the cost (x the Overcharge multiplier) plus the reserve. Owners without a pool never autocast a spell. */
    static boolean canAffordEnergy(String owner, float cost) {
        Resources res = null;
        for (Resources r : ResourcesServerEvents.resourcesList)
            if (r.ownerName.equals(owner)) {
                res = r;
                break;
            }
        return res != null && res.getEnergy() >= cost * ManaServerEvents.spellCostMultiplier(owner) + ENERGY_RESERVE;
    }
}
