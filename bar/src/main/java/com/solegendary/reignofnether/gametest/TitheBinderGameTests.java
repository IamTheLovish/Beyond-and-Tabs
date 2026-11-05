package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.EntanglingRoots;
import com.solegendary.reignofnether.ability.abilities.GraveHook;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.essence.EssenceServerEvents;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.resources.WreckServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.UnitTier;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.TitheBinderUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The Gravebound <b>Tithe Binder</b> (design sheet .scratch/unit1/design-sheet.md 5.3-5.7 and 7.1, lovish's brief Nov 5
 * 2026, with the lead's corrections: no population; Grave Hook's heavies are max health >= 150 or a hitbox >= 1.4 wide).
 * Written before the rules (TitheLedger is a stub on the tests-first commit: the count stays 1, a volley is the hook
 * alone, Grave Hook does nothing). The numbers are the sheet's, written here:
 * <ul>
 *   <li>trained at the Graveyard (T1) for 60 food / 20 wood / 45 ore = 85 metal / 80 energy, 26 s; 35 hp, speed 0.25,
 *       range 10; a fresh Binder carries 1 weapon (his hook) and no half bind;</li>
 *   <li>a volley at N weapons deals 6 + 3 (N - 1), one weapon per hit tick, two ticks apart, after a 6 + 0.4 d flight;</li>
 *   <li>a last hit binds one more weapon, an assist half (two assists = one), a summon nothing; the count stops at 10;
 *       a mass kill binds one weapon per 20 ticks;</li>
 *   <li>weapons 6-10 leave one by one after 20 s without damage (then every 15 s), down to 5 - driven here by the
 *       Binder's test-scoped clock (TitheBinderUnit.setClockForTest), which changes no rule;</li>
 *   <li>Grave Hook (range 12, 25 s) drags a light unit 4 blocks toward the Binder and stuns it, roots a heavy one and
 *       pulls the Binder 1 block toward it.</li>
 * </ul>
 * Every test waits until its units are ticking (ArenaReady.TICKS of their own), then runs one tick callback registered
 * from the test body; early ticks record, the last one judges and cleans up in a finally. The Binders and their
 * targets have no AI and the Binders' autocast is off, so only the test fires. Kill and bind tests run in their own
 * batch (KILLS), apart from the rest.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class TitheBinderGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "tithe_binder";
    static final String KILLS = "tithe_binder_kills";
    /** Test ticks a test waits for its units to tick (its timeoutTicks carries this much more). */
    static final int READY_WAIT = 90;

    // design sheet 7.1 (pop dropped: no population in this game)
    static final int METAL = 85, ENERGY = 80, SECONDS = 26;
    static final float HEALTH = 35f, SPEED = 0.25f, RANGE = 10f;

    private static String owner(GameTestHelper helper, String what) {
        return "gametest_tithe_" + what + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    private static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /** A unit that stands where it is put (no AI), added to {@code made} for the cleanup. */
    private static Mob still(GameTestHelper helper, EntityType<? extends Mob> type, String owner, int x, int z, List<Entity> made) {
        Mob m = HeroGameTests.spawnUnit(helper, type, owner, x, z);
        if (m != null) {
            m.setNoAi(true);
            made.add(m);
        }
        return m;
    }

    /** A Binder with no AI and its autocast off (only the test fires and casts). */
    private static TitheBinderUnit binder(GameTestHelper helper, String owner, int x, int z, List<Entity> made) {
        Mob m = still(helper, EntityRegistrar.TITHE_BINDER_UNIT.get(), owner, x, z, made);
        if (!(m instanceof TitheBinderUnit b))
            return null;
        for (Ability a : b.getAbilities().get())
            a.setAutocast(false, b);
        return b;
    }

    /** A target that takes whole volleys: its max health raised to {@code hp}, at full health. */
    private static void toughen(Mob m, float hp) {
        if (m.getAttribute(Attributes.MAX_HEALTH) != null)
            m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
        m.setHealth(hp);
    }

    /** A blow from the Binder as the killer, the way his weapons hurt (his projectile): the test's "last hit". */
    private static void lastHitBy(TitheBinderUnit b, LivingEntity victim) {
        victim.hurt(b.damageSources().mobProjectile(b, b), 1000f);
    }

    private static GraveHook graveHook(TitheBinderUnit b) {
        for (Ability a : b.getAbilities().get())
            if (a instanceof GraveHook g)
                return g;
        return null;
    }

    /** The order a click on a unit sends. */
    private static void castAt(GameTestHelper helper, Mob caster, UnitAction action, LivingEntity target) {
        new UnitActionItem(((Unit) caster).getOwnerName(), action, target.getId(), new int[] { caster.getId() },
            target.blockPosition(), BlockPos.ZERO).action(helper.getLevel());
    }

    private static int effectTicks(LivingEntity e, MobEffect effect) {
        MobEffectInstance mei = e.getEffect(effect);
        return mei == null ? 0 : mei.getDuration();
    }

    private static double flat(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static String f(double v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    /** Discards the test's units, then the wisps and wrecks the dead left at {@code spots}. */
    private static void cleanUp(List<Entity> made, List<Vec3> spots) {
        for (Entity e : made) {
            if (e instanceof TitheBinderUnit b)
                b.setClockForTest(null);
            if (!e.isRemoved())
                e.discard();
        }
        for (Vec3 at : spots) {
            for (Entity w : EssenceServerEvents.getWisps())
                if (!w.isRemoved() && w.position().distanceToSqr(at) < 2.5 * 2.5)
                    w.discard();
            for (Entity w : WreckServerEvents.getWrecks())
                if (!w.isRemoved() && w.position().distanceToSqr(at) < 2.5 * 2.5)
                    w.discard();
        }
    }

    interface Step {
        /** One test tick, {@code t} ticks after the units were ticking; true when the test has judged (done). */
        boolean at(int t);
    }

    /**
     * Runs {@code step} every tick from the tick all the living entities of {@code made} have run ArenaReady.TICKS ticks
     * of their own (a unit added at tick 0 can be missing from the level for a few ticks: GameTestChunks
     * .whenEntitiesReady); fails, cleaned up, if they are not ticking after READY_WAIT test ticks. One callback,
     * registered from the test body. A step that throws is cleaned up and failed with the exception.
     */
    private static void script(GameTestHelper helper, List<Entity> made, Runnable cleanUp, Step step) {
        List<Entity> units = new ArrayList<>();
        for (Entity e : made)
            if (e instanceof LivingEntity)
                units.add(e);
        long[] start = { -1 };
        boolean[] done = { false };
        helper.onEachTick(() -> {
            if (done[0])
                return;
            if (start[0] < 0) {
                boolean ready = true;
                for (Entity e : units)
                    if (e.isRemoved() || e.tickCount < ArenaReady.TICKS) {
                        ready = false;
                        break;
                    }
                if (!ready) {
                    if (helper.getTick() < READY_WAIT)
                        return;
                    done[0] = true;
                    String why = ArenaReady.describe(helper.getLevel(), units);
                    cleanUp.run();
                    helper.fail("the units were not ticking after " + READY_WAIT + " test ticks: " + why);
                    return;
                }
                start[0] = helper.getTick();
            }
            boolean finished;
            try {
                finished = step.at((int) (helper.getTick() - start[0]));
            } catch (GameTestAssertException ex) {
                done[0] = true;
                throw ex;
            } catch (RuntimeException ex) {
                done[0] = true;
                cleanUp.run();
                helper.fail("threw " + ex);
                return;
            }
            if (finished)
                done[0] = true;
        });
    }

    // ------------------------------------------------------------------------------------------------ the unit

    /**
     * The Graveyard (the Gravebound T1 army lab) trains the Tithe Binder with no Tier 2: its item names an entity of
     * the Gravebound, it costs the sheet's price and completing it (the real onComplete -> produceUnit path) makes an
     * owned tier-1 ground unit with the sheet's stats, Grave Hook, one weapon and no half bind.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = BATCH)
    public static void tithe_binder_trains_at_the_graveyard_with_one_weapon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        List<Entity> made = new ArrayList<>();
        String owner = owner(helper, "train");
        Resources pool = new Resources(owner, 0, 0, 0);
        ResourcesServerEvents.resourcesList.add(pool);
        try {
            if (!Buildings.GRAVEYARD.productions.get().contains(ProductionItems.TITHE_BINDER))
                problems.add("the Graveyard does not train the Tithe Binder");
            if (!Factions.MONSTERS.key.equals(Factions.ENTITY_FACTION.get(EntityType.getKey(EntityRegistrar.TITHE_BINDER_UNIT.get()))))
                problems.add("the Binder's entity is not registered to the Gravebound");
            if (EntityRegistrar.getEntityType(ProductionItems.TITHE_BINDER.getItemName()) != EntityRegistrar.TITHE_BINDER_UNIT.get())
                problems.add("the Binder's item name does not resolve to its entity type");
            ResourceCost c = ResourceCosts.TITHE_BINDER;
            if (c.metal() != METAL || c.energy() != ENERGY || c.ticks != SECONDS * ResourceCost.TICKS_PER_SECOND)
                problems.add("the Binder costs " + c.metal() + " metal / " + c.energy() + " energy / " + c.ticks
                    + " ticks, the sheet says " + METAL + " / " + ENERGY + " / " + SECONDS * ResourceCost.TICKS_PER_SECOND);
            BlockPos corner = helper.absolutePos(BlockPos.ZERO);
            BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(Buildings.GRAVEYARD, level,
                new BlockPos(corner.getX() + 2, corner.getY() + 1, corner.getZ() + 3), Rotation.NONE, owner, false);
            if (!(bp instanceof ProductionPlacement pp)) {
                problems.add("the Graveyard is not a production placement");
            } else {
                pp.isBuilt = true;
                if (!pp.startProductionItem(ProductionItems.TITHE_BINDER))
                    problems.add("the Graveyard refused the Binder: " + ProductionItems.TITHE_BINDER.getProduceErrorMsg(pp));
                pp.productionQueue.clear();
                ProductionItems.TITHE_BINDER.onComplete.accept(level, pp);
                Mob unit = CombatEngineerGameTests.findProduced(level, pp, EntityRegistrar.TITHE_BINDER_UNIT.get(), owner);
                if (unit == null) {
                    problems.add("completing the item produced no Binder");
                } else {
                    made.add(unit);
                    if (!(unit instanceof TitheBinderUnit b)) {
                        problems.add("the produced unit is a " + unit.getClass().getSimpleName());
                    } else {
                        if (!owner.equals(b.getOwnerName()))
                            problems.add("the produced Binder is not the Graveyard owner's");
                        if (b.getCost() != ResourceCosts.TITHE_BINDER)
                            problems.add("the Binder's getCost is not ResourceCosts.TITHE_BINDER");
                        if (UnitTier.of(b) != 1)
                            problems.add("the Binder is tier " + UnitTier.of(b) + ", expected 1");
                        if (Math.abs(b.getMaxHealth() - HEALTH) > 0.01f)
                            problems.add("the Binder has " + b.getMaxHealth() + " max health, the sheet says " + HEALTH);
                        if (Math.abs(b.getBaseMovementSpeed() - SPEED) > 0.001f)
                            problems.add("the Binder's speed is " + b.getBaseMovementSpeed() + ", the sheet says " + SPEED);
                        if (Math.abs(b.getAttackRange() - RANGE) > 0.01f)
                            problems.add("the Binder's range is " + b.getAttackRange() + ", the sheet says " + RANGE);
                        if (b.isFlyingUnit())
                            problems.add("the Binder is a flyer; it should path on the ground (its body hovers)");
                        if (graveHook(b) == null)
                            problems.add("no Grave Hook");
                        if (b.getWeapons() != 1 || b.hasHalfBind())
                            problems.add("a fresh Binder has " + b.getWeapons() + " weapons (half bind " + b.hasHalfBind() + "), expected 1 and none");
                    }
                }
            }
        } catch (RuntimeException ex) {
            problems.add("threw " + ex);
        } finally {
            cleanUp(made, List.of());
            ResourcesServerEvents.resourcesList.remove(pool);
            ResearchServerEvents.removeAllResearchFor(owner);
        }
        finish(helper, problems);
    }

    // ------------------------------------------------------------------------------------------------ the volley

    /**
     * Three Binders at 1, 4 and 10 weapons each fire one volley at their own dummy 6 blocks away (a villager with 200
     * health: no armour, no resist, enough to take a whole volley): each deals exactly 6 + 3 x (N - 1) = 6, 15 and 33.
     * The ten-weapon volley lands on ten ticks, two apart, the first after its 6 + 0.4 x 6 = 8-tick flight: weapons are
     * thrown one by one, not one blow.
     */
    @GameTest(template = ARENA, timeoutTicks = READY_WAIT + 160, batch = BATCH)
    public static void tithe_binder_volley_deals_six_plus_three_per_bound_weapon(GameTestHelper helper) {
        List<Entity> made = new ArrayList<>();
        String a = owner(helper, "volleya"), b = owner(helper, "volleyb");
        int[] counts = { 1, 4, 10 };
        TitheBinderUnit[] binders = new TitheBinderUnit[3];
        Mob[] dummies = new Mob[3];
        for (int i = 0; i < 3; i++) {
            binders[i] = binder(helper, a, 2 + 5 * i, 2, made);
            dummies[i] = still(helper, EntityRegistrar.VILLAGER_UNIT.get(), b, 2 + 5 * i, 8, made);
        }
        for (int i = 0; i < 3; i++)
            if (binders[i] == null || dummies[i] == null) {
                cleanUp(made, List.of());
                helper.fail("could not spawn the Binders and their dummies");
                return;
            }
        Runnable sun = ArenaReady.noSunlight(a, b);
        Runnable clean = () -> {
            cleanUp(made, List.of());
            sun.run();
        };
        List<String> problems = new ArrayList<>();
        float[] before = new float[3], last = new float[3];
        List<List<Integer>> hitTicks = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        int[] settled = { -1 };
        script(helper, made, clean, t -> {
            if (t == 0) {
                for (int i = 0; i < 3; i++) {
                    toughen(dummies[i], 200f);
                    binders[i].setWeapons(counts[i]);
                    if (binders[i].getWeapons() != counts[i])
                        problems.add("setup: setWeapons(" + counts[i] + ") left the Binder at " + binders[i].getWeapons() + " weapons");
                    before[i] = last[i] = dummies[i].getHealth();
                    if (Math.abs(before[i] - 200f) > 0.01f)
                        problems.add("setup: a dummy has " + before[i] + " health, expected 200");
                }
                for (int i = 0; i < 3; i++)
                    binders[i].performUnitRangedAttack(dummies[i], 20f);
                return false;
            }
            boolean all = true;
            for (int i = 0; i < 3; i++) {
                float hp = dummies[i].getHealth();
                if (hp < last[i] - 0.001f) {
                    hitTicks.get(i).add(t);
                    last[i] = hp;
                }
                if (before[i] - hp < 6f + 3f * (counts[i] - 1) - 0.01f)
                    all = false;
            }
            if (settled[0] < 0 && (all || t >= 60))
                settled[0] = t;
            if (settled[0] < 0 || t < settled[0] + 20)   // 20 more ticks: no weapon may land after the expected total
                return false;
            try {
                for (int i = 0; i < 3; i++) {
                    int n = counts[i];
                    float expected = 6f + 3f * (n - 1);
                    float dealt = before[i] - dummies[i].getHealth();
                    if (Math.abs(dealt - expected) > 0.01f)
                        problems.add("a volley at " + n + " weapons dealt " + dealt + ", expected 6 + 3 x " + (n - 1) + " = " + expected);
                }
                List<Integer> ten = hitTicks.get(2);
                if (ten.size() != 10) {
                    problems.add("the ten-weapon volley landed on " + ten.size() + " ticks " + ten + ", expected 10 (one per weapon)");
                } else {
                    for (int k = 1; k < 10; k++)
                        if (ten.get(k) - ten.get(k - 1) != 2) {
                            problems.add("weapons " + k + " and " + (k + 1) + " landed " + (ten.get(k) - ten.get(k - 1))
                                + " ticks apart, expected 2 (" + ten + ")");
                            break;
                        }
                    if (ten.get(0) < 7 || ten.get(0) > 9)
                        problems.add("the first weapon landed " + ten.get(0) + " ticks after the release, expected its 8-tick flight over 6 blocks");
                }
            } finally {
                clean.run();
            }
            finish(helper, problems);
            return true;
        });
    }

    // ------------------------------------------------------------------------------------------------ restless weapons

    /**
     * A Binder at 10 weapons that deals no damage: the top weapon leaves at 20 s (400 ticks), then one every 15 s, down
     * to 5, and none below. The ledger's clock is this unit's test-scoped clock (setClockForTest), stepped by the test to
     * just before and at each due time; game rules and numbers are untouched.
     */
    @GameTest(template = ARENA, timeoutTicks = READY_WAIT + 80, batch = BATCH)
    public static void tithe_binder_restless_weapons_leave_one_by_one_down_to_five(GameTestHelper helper) {
        List<Entity> made = new ArrayList<>();
        String a = owner(helper, "restless");
        TitheBinderUnit b = binder(helper, a, 7, 7, made);
        if (b == null) {
            cleanUp(made, List.of());
            helper.fail("could not spawn the Binder");
            return;
        }
        Runnable sun = ArenaReady.noSunlight(a);
        Runnable clean = () -> {
            cleanUp(made, List.of());
            sun.run();
        };
        final long base = 100_000L;
        long[] clock = { base };
        // (ticks of the injected clock after the count was set, weapons expected then)
        int[][] checks = { { 399, 10 }, { 400, 9 }, { 699, 9 }, { 700, 8 }, { 1000, 7 }, { 1300, 6 }, { 1600, 5 },
            { 1900, 5 }, { 100_000, 5 } };
        List<String> problems = new ArrayList<>();
        script(helper, made, clean, t -> {
            if (t == 0) {
                b.setClockForTest(() -> clock[0]);
                b.setWeapons(10);
                if (b.getWeapons() != 10)
                    problems.add("setup: setWeapons(10) left the Binder at " + b.getWeapons() + " weapons");
                return false;
            }
            int i = (t - 1) / 3, phase = (t - 1) % 3;
            if (i < checks.length) {
                if (phase == 0)
                    clock[0] = base + checks[i][0];
                else if (phase == 2 && b.getWeapons() != checks[i][1])
                    problems.add(checks[i][0] + " ticks idle: " + b.getWeapons() + " weapons, expected " + checks[i][1]);
                return false;
            }
            try {
                if (!b.isAlive())
                    problems.add("the Binder died during the test");
            } finally {
                clean.run();
            }
            finish(helper, problems);
            return true;
        });
    }

    // ------------------------------------------------------------------------------------------------ Grave Hook

    /**
     * Grave Hook through the order a click sends: on a light enemy (a Vindicator, 75 health, 0.6 wide) 8 blocks away it
     * drags it 4 blocks toward the Binder and leaves it stunned about 1.5 s more; on a heavy one (a Vindicator raised to
     * 150 max health: heavy by health at the boundary) it moves nothing but roots it and pulls the Binder 1 block toward
     * it. Both casts start the 25 s cooldown.
     */
    @GameTest(template = ARENA, timeoutTicks = READY_WAIT + 120, batch = BATCH)
    public static void tithe_binder_grave_hook_drags_a_light_unit_and_roots_a_heavy_one(GameTestHelper helper) {
        List<Entity> made = new ArrayList<>();
        String a = owner(helper, "hooka"), b = owner(helper, "hookb");
        TitheBinderUnit bl = binder(helper, a, 2, 2, made);
        TitheBinderUnit bh = binder(helper, a, 12, 2, made);
        Mob light = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 2, 10, made);
        Mob heavy = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 12, 10, made);
        if (bl == null || bh == null || light == null || heavy == null) {
            cleanUp(made, List.of());
            helper.fail("could not spawn the Binders and their targets");
            return;
        }
        Runnable sun = ArenaReady.noSunlight(a, b);
        Runnable clean = () -> {
            cleanUp(made, List.of());
            sun.run();
        };
        List<String> problems = new ArrayList<>();
        double[] d0 = new double[2];
        Vec3[] at0 = new Vec3[2];
        boolean[] rootSeen = { false };
        script(helper, made, clean, t -> {
            if (t == 0) {
                toughen(heavy, 150f);
                if (light.getMaxHealth() >= 150 || light.getBbWidth() >= 1.4f)
                    problems.add("setup: the light Vindicator is heavy (" + light.getMaxHealth() + " health, " + light.getBbWidth() + " wide)");
                if (heavy.getMaxHealth() < 150)
                    problems.add("setup: the heavy Vindicator has only " + heavy.getMaxHealth() + " max health");
                d0[0] = flat(bl.position(), light.position());
                d0[1] = flat(bh.position(), heavy.position());
                at0[0] = heavy.position();
                at0[1] = bh.position();
                castAt(helper, bl, UnitAction.GRAVE_HOOK, light);
                castAt(helper, bh, UnitAction.GRAVE_HOOK, heavy);
                for (TitheBinderUnit u : new TitheBinderUnit[] { bl, bh }) {
                    GraveHook g = graveHook(u);
                    float cd = g == null ? 0 : u.getCooldown(g);
                    if (Math.abs(cd - GraveHook.CD_SECONDS * 20) > 2)
                        problems.add("a cast set a " + cd + "-tick cooldown, expected 500 (25 s): the hook was not thrown");
                }
                return false;
            }
            MobEffectInstance root = heavy.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            if (root != null && root.getAmplifier() >= EntanglingRoots.ROOT_AMPLIFIER)
                rootSeen[0] = true;
            boolean out = bl.isHooking() || bh.isHooking();
            if (out && t < 80)
                return false;
            try {
                if (out)
                    problems.add("a hook was still out after 80 ticks");
                double dragged = d0[0] - flat(bl.position(), light.position());
                if (Math.abs(dragged - 4.0) > 0.4)
                    problems.add("the light Vindicator was dragged " + f(dragged) + " blocks toward the Binder, expected 4");
                int stun = effectTicks(light, MobEffectRegistrar.STUN.get());
                if (stun < 20)
                    problems.add("the light Vindicator has " + stun + " ticks of stun left when the drag ends, expected about 30 (1.5 s)");
                double heavyMoved = flat(at0[0], heavy.position());
                if (heavyMoved > 0.3)
                    problems.add("the heavy Vindicator was moved " + f(heavyMoved) + " blocks: it should be rooted, not dragged");
                if (!rootSeen[0])
                    problems.add("the heavy Vindicator was never rooted (Slowness " + (EntanglingRoots.ROOT_AMPLIFIER + 1) + ")");
                if (heavy.hasEffect(MobEffectRegistrar.STUN.get()))
                    problems.add("the heavy Vindicator was stunned: a heavy is rooted (it still fights)");
                double pulled = d0[1] - flat(bh.position(), heavy.position());
                if (Math.abs(pulled - 1.0) > 0.3)
                    problems.add("the Binder was pulled " + f(pulled) + " blocks toward the heavy one, expected 1");
            } finally {
                clean.run();
            }
            finish(helper, problems);
            return true;
        });
    }

    // ------------------------------------------------------------------------------------------------ kills and binds

    /** A volley's hook lands the last hit on a 1-health enemy: the Binder binds exactly one more weapon, a full one. */
    @GameTest(template = ARENA, timeoutTicks = READY_WAIT + 140, batch = KILLS)
    public static void tithe_binder_last_hit_kill_binds_one_more_weapon(GameTestHelper helper) {
        List<Entity> made = new ArrayList<>();
        List<Vec3> spots = new ArrayList<>();
        String a = owner(helper, "killa"), b = owner(helper, "killb");
        TitheBinderUnit binder = binder(helper, a, 3, 3, made);
        Mob victim = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 3, 9, made);
        if (binder == null || victim == null) {
            cleanUp(made, spots);
            helper.fail("could not spawn the Binder and its victim");
            return;
        }
        Runnable sun = ArenaReady.noSunlight(a, b);
        Runnable clean = () -> {
            cleanUp(made, spots);
            sun.run();
        };
        List<String> problems = new ArrayList<>();
        int[] died = { -1 }, bound = { -1 };
        script(helper, made, clean, t -> {
            if (t == 0) {
                spots.add(victim.position());
                victim.setHealth(1f);
                if (binder.getWeapons() != 1)
                    problems.add("setup: a fresh Binder has " + binder.getWeapons() + " weapons");
                binder.performUnitRangedAttack(victim, 20f);
                return false;
            }
            if (died[0] < 0 && !victim.isAlive())
                died[0] = t;
            if (bound[0] < 0 && binder.getWeapons() >= 2)
                bound[0] = t;
            boolean judge = died[0] < 0 ? t >= 40 : t >= died[0] + 60;
            if (!judge)
                return false;
            try {
                if (died[0] < 0)
                    problems.add("the volley's hook did not kill a 1-health Vindicator in 40 ticks");
                else {
                    if (binder.getWeapons() != 2)
                        problems.add("a last-hit kill left the Binder at " + binder.getWeapons() + " weapons, expected 2");
                    else if (bound[0] - died[0] > 25)
                        problems.add("the bind came " + (bound[0] - died[0]) + " ticks after the kill, expected within 25");
                    if (binder.hasHalfBind())
                        problems.add("a last hit left a half bind waiting (it is a full bind)");
                }
            } finally {
                clean.run();
            }
            finish(helper, problems);
            return true;
        });
    }

    /**
     * The Binder's volley hurts an enemy and something else finishes it within 3 s: half a bind, a ghost in the next
     * slot, still 1 weapon. A second such assist completes it: 2 weapons, no half left.
     */
    @GameTest(template = ARENA, timeoutTicks = READY_WAIT + 220, batch = KILLS)
    public static void tithe_binder_two_assists_bind_one_weapon(GameTestHelper helper) {
        List<Entity> made = new ArrayList<>();
        List<Vec3> spots = new ArrayList<>();
        String a = owner(helper, "assista"), b = owner(helper, "assistb");
        TitheBinderUnit binder = binder(helper, a, 3, 3, made);
        Mob v1 = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 3, 9, made);
        Mob v2 = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 9, 9, made);
        if (binder == null || v1 == null || v2 == null) {
            cleanUp(made, spots);
            helper.fail("could not spawn the Binder and its victims");
            return;
        }
        Runnable sun = ArenaReady.noSunlight(a, b);
        Runnable clean = () -> {
            cleanUp(made, spots);
            sun.run();
        };
        List<String> problems = new ArrayList<>();
        // phase 0: first volley out; 1: v1 killed (by no one), waiting 25 ticks; 2: second volley out; 3: v2 killed
        int[] phase = { 0 }, mark = { 0 };
        script(helper, made, clean, t -> {
            if (t == 0) {
                spots.add(v1.position());
                spots.add(v2.position());
                toughen(v1, 200f);
                toughen(v2, 200f);
                binder.performUnitRangedAttack(v1, 20f);
                return false;
            }
            boolean judge = false;
            switch (phase[0]) {
                case 0 -> {
                    if (v1.getHealth() < 200f) {
                        v1.kill();   // the last hit is not the Binder's: an assist
                        phase[0] = 1;
                        mark[0] = t;
                    } else if (t >= 40) {
                        problems.add("the first volley never hurt its target");
                        judge = true;
                    }
                }
                case 1 -> {
                    if (t >= mark[0] + 25) {
                        if (v1.isAlive())
                            problems.add("setup: the first victim did not die");
                        if (binder.getWeapons() != 1 || !binder.hasHalfBind())
                            problems.add("after one assist the Binder has " + binder.getWeapons() + " weapons and half bind "
                                + binder.hasHalfBind() + ", expected 1 weapon and a half bind waiting");
                        binder.performUnitRangedAttack(v2, 20f);
                        phase[0] = 2;
                        mark[0] = t;
                    }
                }
                case 2 -> {
                    if (v2.getHealth() < 200f) {
                        v2.kill();
                        phase[0] = 3;
                        mark[0] = t;
                    } else if (t >= mark[0] + 40) {
                        problems.add("the second volley never hurt its target");
                        judge = true;
                    }
                }
                default ->   // the bind lands within a tick or two; 30 more ticks show it stays one weapon
                    judge = t >= mark[0] + 45 || (binder.getWeapons() >= 2 && t >= mark[0] + 30);
            }
            if (!judge)
                return false;
            try {
                if (phase[0] == 3) {
                    if (binder.getWeapons() != 2)
                        problems.add("two assists left the Binder at " + binder.getWeapons() + " weapons, expected 2");
                    if (binder.hasHalfBind())
                        problems.add("two halves made a weapon but a half bind is still waiting");
                }
            } finally {
                clean.run();
            }
            finish(helper, problems);
            return true;
        });
    }

    /**
     * A summon gives nothing (the anti-farm rule of veterancy and essence): a Binder's hook kills a marked summon and
     * stays at 1 weapon with no half bind, while beside it another Binder's identical kill of a plain enemy binds one -
     * so the zero is the summon rule, not a broken credit path.
     */
    @GameTest(template = ARENA, timeoutTicks = READY_WAIT + 140, batch = KILLS)
    public static void tithe_binder_summon_kill_binds_nothing(GameTestHelper helper) {
        List<Entity> made = new ArrayList<>();
        List<Vec3> spots = new ArrayList<>();
        String a = owner(helper, "summona"), b = owner(helper, "summonb");
        TitheBinderUnit b1 = binder(helper, a, 3, 3, made);
        TitheBinderUnit b2 = binder(helper, a, 12, 3, made);
        Mob summon = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 3, 9, made);
        Mob control = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 12, 9, made);
        if (b1 == null || b2 == null || summon == null || control == null) {
            cleanUp(made, spots);
            helper.fail("could not spawn the Binders and their victims");
            return;
        }
        Runnable sun = ArenaReady.noSunlight(a, b);
        Runnable clean = () -> {
            cleanUp(made, spots);
            sun.run();
        };
        List<String> problems = new ArrayList<>();
        int[] died = { -1 };
        script(helper, made, clean, t -> {
            if (t == 0) {
                spots.add(summon.position());
                spots.add(control.position());
                Essence.markSummoned(summon);
                if (!Essence.isSummoned(summon) || Essence.isSummoned(control))
                    problems.add("setup: the marked Vindicator is summoned " + Essence.isSummoned(summon)
                        + ", the control " + Essence.isSummoned(control));
                summon.setHealth(1f);
                control.setHealth(1f);
                b1.performUnitRangedAttack(summon, 20f);
                b2.performUnitRangedAttack(control, 20f);
                return false;
            }
            if (died[0] < 0 && !summon.isAlive() && !control.isAlive())
                died[0] = t;
            boolean judge = died[0] < 0 ? t >= 40 : t >= died[0] + 50;
            if (!judge)
                return false;
            try {
                if (died[0] < 0)
                    problems.add("the hooks did not kill both 1-health Vindicators in 40 ticks (summon alive "
                        + summon.isAlive() + ", control alive " + control.isAlive() + ")");
                else {
                    if (b2.getWeapons() != 2)
                        problems.add("the control kill of a plain enemy left its Binder at " + b2.getWeapons()
                            + " weapons, expected 2 (the credit path itself is broken)");
                    if (b1.getWeapons() != 1 || b1.hasHalfBind())
                        problems.add("a summon kill gave its Binder " + b1.getWeapons() + " weapons and half bind "
                            + b1.hasHalfBind() + ", expected 1 and none");
                }
            } finally {
                clean.run();
            }
            finish(helper, problems);
            return true;
        });
    }

    /** A Binder at 9 weapons lands the last hit on three enemies at once: it ends at 10, never 11, with no half left. */
    @GameTest(template = ARENA, timeoutTicks = READY_WAIT + 160, batch = KILLS)
    public static void tithe_binder_count_caps_at_ten(GameTestHelper helper) {
        List<Entity> made = new ArrayList<>();
        List<Vec3> spots = new ArrayList<>();
        String a = owner(helper, "capa"), b = owner(helper, "capb");
        TitheBinderUnit binder = binder(helper, a, 7, 3, made);
        List<Mob> victims = new ArrayList<>();
        for (int x : new int[] { 3, 7, 11 }) {
            Mob v = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, x, 10, made);
            if (v != null)
                victims.add(v);
        }
        if (binder == null || victims.size() < 3) {
            cleanUp(made, spots);
            helper.fail("could not spawn the Binder and its victims");
            return;
        }
        Runnable sun = ArenaReady.noSunlight(a, b);
        Runnable clean = () -> {
            cleanUp(made, spots);
            sun.run();
        };
        List<String> problems = new ArrayList<>();
        int[] most = { 0 };
        script(helper, made, clean, t -> {
            if (t == 0) {
                binder.setWeapons(9);
                if (binder.getWeapons() != 9)
                    problems.add("setup: setWeapons(9) left the Binder at " + binder.getWeapons() + " weapons");
                for (Mob v : victims) {
                    spots.add(v.position());
                    v.setHealth(1f);
                    lastHitBy(binder, v);
                }
                for (Mob v : victims)
                    if (v.isAlive())
                        problems.add("setup: a last hit did not kill a 1-health Vindicator");
                return false;
            }
            most[0] = Math.max(most[0], binder.getWeapons());
            if (t < 100)   // three binds' worth of time (3 x 20) and more: a count past 10 would show by now
                return false;
            try {
                if (binder.getWeapons() != 10)
                    problems.add("three kills at 9 weapons left the Binder at " + binder.getWeapons() + ", expected 10");
                if (most[0] > 10)
                    problems.add("the count reached " + most[0] + ", past the cap of 10");
                if (binder.hasHalfBind())
                    problems.add("a half bind waits at the cap (there is no eleventh slot)");
            } finally {
                clean.run();
            }
            finish(helper, problems);
            return true;
        });
    }

    /**
     * Five enemies fall to the Binder's last hits in the same tick: the five binds land one by one, each 20 ticks after
     * the one before (every bind plays its 32-tick clip; a mass death never jumps the count in a frame), the first at
     * once.
     */
    @GameTest(template = ARENA, timeoutTicks = READY_WAIT + 200, batch = KILLS)
    public static void tithe_binder_mass_kill_binds_one_weapon_per_20_ticks(GameTestHelper helper) {
        List<Entity> made = new ArrayList<>();
        List<Vec3> spots = new ArrayList<>();
        String a = owner(helper, "massa"), b = owner(helper, "massb");
        TitheBinderUnit binder = binder(helper, a, 7, 3, made);
        List<Mob> victims = new ArrayList<>();
        for (int x : new int[] { 2, 5, 8, 11, 14 }) {
            Mob v = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, x, 10, made);
            if (v != null)
                victims.add(v);
        }
        if (binder == null || victims.size() < 5) {
            cleanUp(made, spots);
            helper.fail("could not spawn the Binder and its victims");
            return;
        }
        Runnable sun = ArenaReady.noSunlight(a, b);
        Runnable clean = () -> {
            cleanUp(made, spots);
            sun.run();
        };
        List<String> problems = new ArrayList<>();
        List<Integer> binds = new ArrayList<>();
        int[] seen = { 1 }, most = { 1 };
        script(helper, made, clean, t -> {
            if (t == 0) {
                if (binder.getWeapons() != 1)
                    problems.add("setup: a fresh Binder has " + binder.getWeapons() + " weapons");
                for (Mob v : victims) {
                    spots.add(v.position());
                    v.setHealth(1f);
                    lastHitBy(binder, v);
                }
                for (Mob v : victims)
                    if (v.isAlive())
                        problems.add("setup: a last hit did not kill a 1-health Vindicator");
            }
            int w = binder.getWeapons();
            for (int k = seen[0]; k < w; k++)
                binds.add(t);
            seen[0] = Math.max(seen[0], w);
            most[0] = Math.max(most[0], w);
            boolean all = w >= 6 && t >= binds.get(binds.size() - 1) + 25;
            if (!all && t < 160)
                return false;
            try {
                if (w != 6)
                    problems.add("five kills left the Binder at " + w + " weapons, expected 6 (binds at " + binds + ")");
                if (most[0] > 6)
                    problems.add("the count reached " + most[0]);
                if (!binds.isEmpty() && binds.get(0) > 3)
                    problems.add("the first bind came " + binds.get(0) + " ticks after the kills, expected at once");
                for (int k = 1; k < binds.size(); k++) {
                    int gap = binds.get(k) - binds.get(k - 1);
                    if (gap < 19 || gap > 22) {
                        problems.add("binds " + k + " and " + (k + 1) + " came " + gap + " ticks apart, expected 20 (binds at " + binds + ")");
                        break;
                    }
                }
            } finally {
                clean.run();
            }
            finish(helper, problems);
            return true;
        });
    }
}
