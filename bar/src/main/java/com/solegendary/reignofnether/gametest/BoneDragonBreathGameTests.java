package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.entities.GhastUnitFireball;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.EngagementStance;
import com.solegendary.reignofnether.unit.FireBreath;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.BoneDragonUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Bone Dragon's blue fire breath, through the real attack path (an attack order, UnitBowAttackGoal, the breath)
 * - lovish, Nov 5: "the bone dragon should get a blue fire breath attack from the air not the current ghast sound
 * effects and projectiles", "the ground effect is very choppy and currently weak it was barely able to hit anything
 * without bugging out", "bone dragon can use this fire breath on the ground as well".
 *
 * A breath burns for seconds, not one tick: what stands in it loses health in EVERY second of the breath, landed (a
 * forward cone at ground level) and airborne (a cone from the jaws down onto its aim). What stands outside the cone - a
 * unit level with the flying dragon, one above the landed dragon's line of fire, one behind it, one off to the side,
 * and any friend - is never burnt. A building it attacks burns the same way, and no ghast fireball is ever thrown.
 *
 * The foes are workers with their AI off (they would run from the fire) and 1000 health (so every burn can be
 * measured); the dragon is ordered to attack once it is in place. Every callback is scheduled from the test's own body
 * (DragonDiveGameTests.everyTick): a runAfterDelay from inside a callback corrupts vanilla's tick map.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BoneDragonBreathGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** "Every second of the breath": the health a burnt foe must lose in each 20-tick window from its first burn on
     *  (the old breath's wither alone takes 1 every 40 ticks; one burn is 10, 13 at night). */
    static final float LOSS_PER_SECOND = 3f;
    /** How many such windows: the breath's first two seconds. */
    static final int WINDOWS = 2;
    /** The flying dragon's height over the ground: FlyingMoveToTargetGoal's cruising height. */
    static final double AIR_UP = 10;

    private static String owner(GameTestHelper helper, String tag) {
        return "gametest_breath_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    /** A worker that neither flees nor fights (AI off) with 1000 health, standing at (x, y, z) over the arena floor. */
    private static Mob foe(GameTestHelper helper, String owner, double x, double y, double z) {
        ServerLevel level = helper.getLevel();
        Mob m = EntityRegistrar.VILLAGER_UNIT.get().create(level);
        if (m == null)
            return null;
        BlockPos floor = helper.absolutePos(new BlockPos(0, 2, 0));
        m.moveTo(floor.getX() + x, floor.getY() + y, floor.getZ() + z, 0, 0);
        if (m instanceof Unit u)
            u.setOwnerName(owner);
        m.setNoAi(true);
        level.addFreshEntity(m);
        AttributeInstance hp = m.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null)
            hp.setBaseValue(1000);
        m.setHealth(1000);
        return m;
    }

    /** The dragon at (x, z), {@code up} over the arena floor, its body turned to {@code yaw}. */
    private static BoneDragonUnit dragon(GameTestHelper helper, String owner, double x, double up, double z, float yaw) {
        ServerLevel level = helper.getLevel();
        BoneDragonUnit d = EntityRegistrar.BONE_DRAGON_UNIT.get().create(level);
        if (d == null)
            return null;
        BlockPos floor = helper.absolutePos(new BlockPos(0, 2, 0));
        d.moveTo(floor.getX() + x, floor.getY() + up, floor.getZ() + z, yaw, 0);
        d.setYBodyRot(yaw);
        d.setYHeadRot(yaw);
        d.setOwnerName(owner);
        BoneDragonUnit.breathTrace = true;
        level.addFreshEntity(d);
        return d;
    }

    /**
     * {@link DragonDiveGameTests#finish}, with - on a failure - each dragon's breath state and last breath events
     * (BoneDragonUnit.breathDebug) and the ids of this test's units, so a flake says what the breath did.
     */
    private static boolean done(GameTestHelper helper, List<String> problems, Runnable clean, List<Mob> units) {
        if (!problems.isEmpty()) {
            List<String> said = new ArrayList<>(problems);
            StringBuilder ids = new StringBuilder();
            for (Mob m : units) {
                if (m instanceof BoneDragonUnit d)
                    said.add("dragon " + d.breathDebug());
                else
                    ids.append(ids.length() == 0 ? "" : ", ").append(m.getId())
                        .append(m instanceof Unit u && u.getOwnerName() != null && u.getOwnerName().endsWith("_foe") ? " foe" : " friend")
                        .append(String.format(Locale.ROOT, " %.0f hp", m.getHealth()));
            }
            said.add("this test's units: " + ids + "; the foes' floor at y " + helper.absolutePos(new BlockPos(0, 2, 0)).getY());
            problems = said;
        }
        return DragonDiveGameTests.finish(helper, problems, clean);
    }

    /** A stone column at (x, z) from the floor up to (not including) floor + top: a perch for a foe over the ground. */
    private static void pillar(GameTestHelper helper, TestCleanup.BlockUndo undo, int x, int z, int top) {
        for (int y = 2; y < 2 + top; y++)
            undo.set(helper.absolutePos(new BlockPos(x, y, z)), Blocks.STONE.defaultBlockState());
    }

    /**
     * Watches one burnt foe: finds the tick of its first burn (from tick {@code from} on), then the health it loses in
     * each of the next {@link #WINDOWS} 20-tick windows. {@link #done} once all are read.
     */
    static final class Burns {
        final Mob mob;
        final String name;
        float last = Float.NaN;
        int first = -1;
        final float[] at = new float[WINDOWS + 1];

        Burns(Mob mob, String name) {
            this.mob = mob;
            this.name = name;
        }

        void tick(int tick, int from) {
            float hp = mob.getHealth();
            if (first < 0 && tick >= from && !Float.isNaN(last) && hp < last - 0.01f) {
                first = tick;
                at[0] = hp;
            } else if (first >= 0 && (tick - first) % 20 == 0 && (tick - first) / 20 <= WINDOWS) {
                at[(tick - first) / 20] = hp;
            }
            last = hp;
        }

        boolean done(int tick) {
            return first >= 0 && tick >= first + 20 * WINDOWS;
        }

        /** Each window that lost less than LOSS_PER_SECOND, said. */
        void judge(List<String> problems) {
            if (first < 0) {
                problems.add(name + " was never burnt");
                return;
            }
            for (int w = 0; w < WINDOWS; w++) {
                float lost = at[w] - at[w + 1];
                if (lost < LOSS_PER_SECOND)
                    problems.add(String.format(Locale.ROOT, "%s lost %.1f in second %d after its first burn (want >= %.0f:"
                        + " a breath burns every second, not once)", name, lost, w + 1, LOSS_PER_SECOND));
            }
        }
    }

    /** Each of {@code spared} that lost health since {@code before}, said with {@code why}. */
    private static void spared(List<String> problems, Mob[] spared, float[] before, String[] why) {
        for (int i = 0; i < spared.length; i++)
            if (spared[i].getHealth() < before[i] - 0.01f)
                problems.add(String.format(Locale.ROOT, "%s was burnt (lost %.1f): it stands outside the breath's cone",
                    why[i], before[i] - spared[i].getHealth()));
    }

    // ---- landed ----------------------------------------------------------------------------------------------------

    /**
     * Landed (a Dive on its own spot - it is down the next tick), the dragon breathes a forward cone along the ground at
     * a foe ~8 blocks in front of it (diagonally, so the 12-block cone stays in the arena): the foe and a second foe
     * just behind it lose health in every second of the breath, and the dragon stays down through it.
     */
    @GameTest(template = ARENA, timeoutTicks = 260)
    public static void bone_dragon_landed_breath_burns_what_is_in_front_every_second(GameTestHelper helper) {
        String owner = owner(helper, "landed"), foeOwner = owner + "_foe";
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        BoneDragonUnit dragon = dragon(helper, owner, 2.5, 0, 2.5, -45f);
        Mob target = foe(helper, foeOwner, 8.5, 0, 8.5), behind = foe(helper, foeOwner, 10.5, 0, 10.0);
        List<Mob> all = new ArrayList<>();
        for (Mob m : new Mob[] { dragon, target, behind })
            if (m != null)
                all.add(m);
        Runnable clean = TestCleanup.atEnd(helper, () -> all.forEach(Mob::discard));
        if (all.size() != 3) {
            clean.run();
            helper.fail("could not create the landed breath test units");
            return;
        }
        Burns[] burns = { new Burns(target, "the foe in front of the landed dragon"), new Burns(behind, "the foe behind it, in the cone") };
        int[] landedAt = { -1 };
        DragonDiveGameTests.everyTick(helper, 250, tick -> {
            List<String> problems = new ArrayList<>();
            if (tick == 2 && !dragon.startDive(dragon.position()))
                problems.add("the dive onto its own spot was refused");
            if (landedAt[0] < 0 && dragon.isLanded()) {
                landedAt[0] = tick;
                ((AttackerUnit) dragon).setUnitAttackTargetForced(target);
            }
            if (!problems.isEmpty())
                return done(helper, problems, clean, all);
            if (landedAt[0] < 0) {
                if (tick > 20)
                    return done(helper, List.of("the dragon never landed"), clean, all);
                return false;
            }
            if ((tick - landedAt[0]) % 20 == 10 && dragon.getTarget() != target)
                ((AttackerUnit) dragon).setUnitAttackTargetForced(target);   // a neighbouring test's unit must not pull it away
            for (Burns b : burns)
                b.tick(tick, landedAt[0] + 1);
            if (burns[0].first >= 0 && !dragon.isLanded())
                problems.add("the dragon took off during the breath (it should stay down " + BoneDragonUnit.LAND_TICKS + " ticks)");
            if (burns[0].done(tick) && burns[1].done(tick) || tick >= 245) {
                for (Burns b : burns)
                    b.judge(problems);
                return done(helper, problems, clean, all);
            }
            return !problems.isEmpty() && done(helper, problems, clean, all);
        });
    }

    /**
     * Landed, the cone is at ground level and in front: while the foe in front burns (so the breath really ran), a foe on
     * a 6-block tower just beside the line of fire, a foe at the dragon's flank, one behind it and a friend in the cone
     * are never burnt.
     */
    @GameTest(template = ARENA, timeoutTicks = 260)
    public static void bone_dragon_landed_breath_spares_what_is_outside_its_cone(GameTestHelper helper) {
        String owner = owner(helper, "landed_spare"), foeOwner = owner + "_foe";
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        TestCleanup.BlockUndo undo = TestCleanup.blocks(helper);
        pillar(helper, undo, 9, 6, 6);
        BoneDragonUnit dragon = dragon(helper, owner, 2.5, 0, 2.5, -45f);
        Mob target = foe(helper, foeOwner, 8.5, 0, 8.5);
        Mob tower = foe(helper, foeOwner, 9.5, 6, 6.5), flank = foe(helper, foeOwner, 1.0, 0, 8.5);
        Mob back = foe(helper, foeOwner, 0.6, 0, 0.6), friend = foe(helper, owner, 7.5, 0, 7.5);
        List<Mob> all = new ArrayList<>();
        for (Mob m : new Mob[] { dragon, target, tower, flank, back, friend })
            if (m != null)
                all.add(m);
        Runnable clean = TestCleanup.atEnd(helper, () -> all.forEach(Mob::discard));
        if (all.size() != 6) {
            clean.run();
            helper.fail("could not create the landed cone test units");
            return;
        }
        // ordered fire only (Hold fire still obeys an order): RoN turns an idle or unordered unit on the NEAREST
        // enemy, and the spared foes here are nearer the dragon than its target
        EngagementStance.set(dragon, EngagementStance.HOLD_FIRE);
        Mob[] spared = { tower, flank, back, friend };
        String[] why = { "a foe on a tower 6 up beside the landed dragon's line of fire", "a foe at the dragon's flank",
            "a foe behind the dragon", "a friend in the cone" };
        float[] before = new float[spared.length];
        Burns burn = new Burns(target, "the foe in front of the landed dragon");
        int[] landedAt = { -1 };
        DragonDiveGameTests.everyTick(helper, 250, tick -> {
            List<String> problems = new ArrayList<>();
            if (tick == 2 && !dragon.startDive(dragon.position()))
                return done(helper, List.of("the dive onto its own spot was refused"), clean, all);
            if (landedAt[0] < 0 && dragon.isLanded()) {
                landedAt[0] = tick;
                for (int i = 0; i < spared.length; i++)
                    before[i] = spared[i].getHealth();
                ((AttackerUnit) dragon).setUnitAttackTargetForced(target);
            }
            if (landedAt[0] < 0) {
                if (tick > 20)
                    return done(helper, List.of("the dragon never landed"), clean, all);
                return false;
            }
            if ((tick - landedAt[0]) % 20 == 10 && dragon.getTarget() != target)
                ((AttackerUnit) dragon).setUnitAttackTargetForced(target);
            burn.tick(tick, landedAt[0] + 1);
            spared(problems, spared, before, why);
            if (!problems.isEmpty() || burn.done(tick) || tick >= 245) {
                if (problems.isEmpty())   // a spared foe burnt already fails it; its unread seconds are not "lost 0.0"
                    burn.judge(problems);
                return done(helper, problems, clean, all);
            }
            return false;
        });
    }

    // ---- airborne --------------------------------------------------------------------------------------------------

    /**
     * Airborne at its fighting height (10 over the ground, holding its position), the dragon breathes down onto a foe
     * 5 blocks ahead on the ground: the foe and a second foe 1.5 blocks beside it - both under the cone - lose health in
     * every second of the breath.
     */
    @GameTest(template = ARENA, timeoutTicks = 220)
    public static void bone_dragon_air_breath_burns_the_ground_under_it_every_second(GameTestHelper helper) {
        String owner = owner(helper, "air"), foeOwner = owner + "_foe";
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        BoneDragonUnit dragon = dragon(helper, owner, 2.5, AIR_UP, 7.5, -90f);
        Mob target = foe(helper, foeOwner, 7.5, 0, 7.5), beside = foe(helper, foeOwner, 7.5, 0, 9.0);
        List<Mob> all = new ArrayList<>();
        for (Mob m : new Mob[] { dragon, target, beside })
            if (m != null)
                all.add(m);
        Runnable clean = TestCleanup.atEnd(helper, () -> all.forEach(Mob::discard));
        if (all.size() != 3) {
            clean.run();
            helper.fail("could not create the airborne breath test units");
            return;
        }
        Burns[] burns = { new Burns(target, "the foe under the flying dragon's aim"), new Burns(beside, "the foe 1.5 beside it") };
        double floorY = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
        DragonDiveGameTests.everyTick(helper, 210, tick -> {
            List<String> problems = new ArrayList<>();
            if (tick == 2) {
                dragon.setHoldPosition(true);
                ((AttackerUnit) dragon).setUnitAttackTargetForced(target);
            }
            if (tick < 2)
                return false;
            if (tick % 20 == 12 && dragon.getTarget() != target)
                ((AttackerUnit) dragon).setUnitAttackTargetForced(target);
            for (Burns b : burns)
                b.tick(tick, 3);
            if (burns[0].first == tick && (!dragon.isFlyingUnit() || dragon.getY() - floorY < AIR_UP - 2))
                problems.add(String.format(Locale.ROOT, "the dragon breathed from %.1f over the ground (flyer: %s), not"
                    + " from the air", dragon.getY() - floorY, dragon.isFlyingUnit()));
            if (burns[0].done(tick) && burns[1].done(tick) || tick >= 205) {
                for (Burns b : burns)
                    b.judge(problems);
                return done(helper, problems, clean, all);
            }
            return !problems.isEmpty() && done(helper, problems, clean, all);
        });
    }

    /** This test's own sky floor: 11 or more from every other pad (ArenaReady.pad), nothing over it but sky. */
    static final int THIN_PAD_Y = 312;

    /**
     * Over a floor one block thick - a bridge, a ledge over a cave - the flying dragon still breathes: only the void
     * round a map ("Can't attack while out of bounds") disarms it. The Ghast's rule it inherits read the column from
     * the sky down and disarmed it whenever the block two under the top one was air: stress 42070433882 found the
     * dragon disarmed over the game-test arena's thin ground ("cooldown 66666456", "t10 disarmed") and never burning.
     */
    @GameTest(template = ARENA, timeoutTicks = 220)
    public static void bone_dragon_air_breath_burns_over_a_thin_floor(GameTestHelper helper) {
        String owner = owner(helper, "thin"), foeOwner = owner + "_foe";
        BlockPos stand = ArenaReady.pad(helper, THIN_PAD_Y);
        double up = stand.getY() - helper.absolutePos(new BlockPos(0, 2, 0)).getY();
        BoneDragonUnit dragon = dragon(helper, owner, 2.5, up + AIR_UP, 7.5, -90f);
        Mob target = foe(helper, foeOwner, 7.5, up, 7.5);
        List<Mob> all = new ArrayList<>();
        for (Mob m : new Mob[] { dragon, target })
            if (m != null)
                all.add(m);
        Runnable clean = TestCleanup.atEnd(helper, () -> all.forEach(Mob::discard));
        if (all.size() != 2) {
            clean.run();
            helper.fail("could not create the thin-floor breath test units");
            return;
        }
        EngagementStance.set(dragon, EngagementStance.HOLD_FIRE);
        Burns burns = new Burns(target, "the foe on the thin floor under the flying dragon's aim");
        DragonDiveGameTests.everyTick(helper, 210, tick -> {
            List<String> problems = new ArrayList<>();
            if (tick == 2) {
                dragon.setHoldPosition(true);
                ((AttackerUnit) dragon).setUnitAttackTargetForced(target);
            }
            if (tick < 2)
                return false;
            if (tick % 20 == 12 && dragon.getTarget() != target)
                ((AttackerUnit) dragon).setUnitAttackTargetForced(target);
            if (tick == 30 && dragon.hasEffect(MobEffectRegistrar.DISARM.get()))
                problems.add("the dragon was disarmed over a floor (\"out of bounds\"), the ground one block under its aim");
            burns.tick(tick, 3);
            if (burns.done(tick) || tick >= 205) {
                if (problems.isEmpty())
                    burns.judge(problems);
                return done(helper, problems, clean, all);
            }
            return !problems.isEmpty() && done(helper, problems, clean, all);
        });
    }

    /**
     * Airborne, the cone runs from the jaws DOWN to its aim: while the foe under it burns, a foe on a tower level with
     * the flying dragon (in front of it), a foe on the ground 7 blocks off the line of fire, one behind the dragon and a
     * friend under the cone are never burnt.
     */
    @GameTest(template = ARENA, timeoutTicks = 220)
    public static void bone_dragon_air_breath_spares_what_is_outside_its_cone(GameTestHelper helper) {
        String owner = owner(helper, "air_spare"), foeOwner = owner + "_foe";
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        TestCleanup.BlockUndo undo = TestCleanup.blocks(helper);
        pillar(helper, undo, 10, 7, (int) AIR_UP);
        BoneDragonUnit dragon = dragon(helper, owner, 2.5, AIR_UP, 7.5, -90f);
        Mob target = foe(helper, foeOwner, 7.5, 0, 7.5);
        Mob tower = foe(helper, foeOwner, 10.5, AIR_UP, 7.5), off = foe(helper, foeOwner, 7.5, 0, 14.5);
        Mob back = foe(helper, foeOwner, 0.5, 0, 7.5), friend = foe(helper, owner, 7.5, 0, 6.5);
        List<Mob> all = new ArrayList<>();
        for (Mob m : new Mob[] { dragon, target, tower, off, back, friend })
            if (m != null)
                all.add(m);
        Runnable clean = TestCleanup.atEnd(helper, () -> all.forEach(Mob::discard));
        if (all.size() != 6) {
            clean.run();
            helper.fail("could not create the airborne cone test units");
            return;
        }
        // ordered fire only (Hold fire still obeys an order): RoN turns an idle or unordered unit on the NEAREST
        // enemy, and the spared foes here are nearer the dragon than its target
        EngagementStance.set(dragon, EngagementStance.HOLD_FIRE);
        Mob[] spared = { tower, off, back, friend };
        String[] why = { "a foe on a tower level with the flying dragon", "a foe 7 blocks off the line of fire",
            "a foe behind the dragon", "a friend under the cone" };
        float[] before = new float[spared.length];
        Burns burn = new Burns(target, "the foe under the flying dragon's aim");
        DragonDiveGameTests.everyTick(helper, 210, tick -> {
            List<String> problems = new ArrayList<>();
            if (tick == 2) {
                for (int i = 0; i < spared.length; i++)
                    before[i] = spared[i].getHealth();
                dragon.setHoldPosition(true);
                ((AttackerUnit) dragon).setUnitAttackTargetForced(target);
            }
            if (tick < 2)
                return false;
            if (tick % 20 == 12 && dragon.getTarget() != target)
                ((AttackerUnit) dragon).setUnitAttackTargetForced(target);
            burn.tick(tick, 3);
            spared(problems, spared, before, why);
            if (!problems.isEmpty() || burn.done(tick) || tick >= 205) {
                if (problems.isEmpty())   // a spared foe burnt already fails it; its unread seconds are not "lost 0.0"
                    burn.judge(problems);
                return done(helper, problems, clean, all);
            }
            return false;
        });
    }

    /** How much health a foe must lose on a burn tick to count as burnt by it (a burn is 10, the wither's tick 1). */
    static final float BURN_MIN = 3f;

    /**
     * Airborne over a squad (the eyes stills' layout: the target, one beside it on the far side and one behind it), the
     * strafe burns the far side of the squad on EVERY burn of the breath, from the first. The fire used to land
     * {@link FireBreath#AIR_SWEEP} short of its target when the breath began and sweep to as far past (eyes 42083382498
     * and 42172015718: the splash 3-4 blocks short of the squad), so the first two of its five burns fell between the
     * dragon and the squad and only its target caught them.
     */
    @GameTest(template = ARENA, timeoutTicks = 220)
    public static void bone_dragon_air_breath_burns_the_far_side_of_the_squad_from_the_first_burn(GameTestHelper helper) {
        String owner = owner(helper, "air_squad"), foeOwner = owner + "_foe";
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        BoneDragonUnit dragon = dragon(helper, owner, 2.5, AIR_UP, 7.5, -90f);
        Mob target = foe(helper, foeOwner, 7.5, 0, 7.5);
        Mob beside = foe(helper, foeOwner, 8.9, 0, 8.6), behind = foe(helper, foeOwner, 10.3, 0, 7.5);
        List<Mob> all = new ArrayList<>();
        for (Mob m : new Mob[] { dragon, target, beside, behind })
            if (m != null)
                all.add(m);
        Runnable clean = TestCleanup.atEnd(helper, () -> all.forEach(Mob::discard));
        if (all.size() != 4) {
            clean.run();
            helper.fail("could not create the airborne squad test units");
            return;
        }
        EngagementStance.set(dragon, EngagementStance.HOLD_FIRE);   // ordered fire only: the target, not the nearest
        Mob[] far = { beside, behind };
        String[] name = { "the foe beside the target on the far side (1.4 past, 1.1 off)", "the foe 2.8 behind the target" };
        float[] last = new float[far.length];
        float[] targetLast = { Float.NaN };
        int[] first = { -1 };
        int burns = FireBreath.TICKS / FireBreath.PULSE_TICKS;
        List<String> missed = new ArrayList<>();
        DragonDiveGameTests.everyTick(helper, 210, tick -> {
            List<String> problems = new ArrayList<>();
            if (tick == 2) {
                dragon.setHoldPosition(true);
                ((AttackerUnit) dragon).setUnitAttackTargetForced(target);
            }
            if (tick < 2)
                return false;
            if (tick % 20 == 12 && dragon.getTarget() != target)
                ((AttackerUnit) dragon).setUnitAttackTargetForced(target);
            float hp = target.getHealth();
            // the breath's first burn: the tick its target first loses a burn's worth
            if (first[0] < 0 && tick >= 3 && !Float.isNaN(targetLast[0]) && hp < targetLast[0] - BURN_MIN)
                first[0] = tick;
            targetLast[0] = hp;
            int since = first[0] < 0 ? -1 : tick - first[0];
            if (since >= 0 && since % FireBreath.PULSE_TICKS == 0 && since / FireBreath.PULSE_TICKS < burns) {
                int n = since / FireBreath.PULSE_TICKS + 1;
                for (int i = 0; i < far.length; i++)
                    if (far[i].getHealth() > last[i] - BURN_MIN)
                        missed.add(String.format(Locale.ROOT, "%s was not burnt by burn %d of %d (lost %.1f)", name[i], n,
                            burns, last[i] - far[i].getHealth()));
            }
            for (int i = 0; i < far.length; i++)
                last[i] = far[i].getHealth();
            if (since >= FireBreath.PULSE_TICKS * (burns - 1) || tick >= 205) {
                if (first[0] < 0)
                    problems.add("the target was never burnt");
                if (!missed.isEmpty())
                    problems.add("the fire landed short of the squad: " + String.join("; ", missed));
                return done(helper, problems, clean, all);
            }
            return false;
        });
    }

    // ---- orders ------------------------------------------------------------------------------------------------------

    /** Ticks from a new order to the first burn on the new target: the breath's next burn, one second at most. */
    static final int TURN_TICKS = 20;

    /**
     * Breathing on one foe, the dragon is ordered onto another 8 blocks to the side: the running breath swings to it and
     * burns it within {@link #TURN_TICKS} ticks of the order - the player's order is obeyed now, not after the old
     * breath's 2.5 s have burnt out on the old target.
     */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void bone_dragon_breath_turns_to_a_new_order_at_once(GameTestHelper helper) {
        String owner = owner(helper, "turn"), foeOwner = owner + "_foe";
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        BoneDragonUnit dragon = dragon(helper, owner, 2.5, AIR_UP, 7.5, -90f);
        Mob first = foe(helper, foeOwner, 7.5, 0, 3.5), second = foe(helper, foeOwner, 7.5, 0, 11.5);
        List<Mob> all = new ArrayList<>();
        for (Mob m : new Mob[] { dragon, first, second })
            if (m != null)
                all.add(m);
        Runnable clean = TestCleanup.atEnd(helper, () -> all.forEach(Mob::discard));
        if (all.size() != 3) {
            clean.run();
            helper.fail("could not create the order test units");
            return;
        }
        EngagementStance.set(dragon, EngagementStance.HOLD_FIRE);   // only what it is ordered onto
        // the breath starts once the dragon is ticking (ArenaReady.TICKS of its own): green 42212877775 had one run
        // start it on a dragon still at tickCount 0 - its chunk not entity-ticking yet - so the breath sat at its tick 0
        // and burnt neither foe ("t0 start", "breathing true tick 0"). The clock below is the breath's, not the test's
        final int[] start = { -1 };
        final int orderAfter = 6;   // between the breath's first burn (its tick 0) and its second (tick 10)
        float[] before = new float[1];
        DragonDiveGameTests.everyTick(helper, 110, tick -> {
            if (start[0] < 0) {
                if (dragon.tickCount < ArenaReady.TICKS) {
                    if (tick >= 60)
                        return done(helper, List.of("the dragon was not ticking after 60 test ticks: "
                            + ArenaReady.describe(helper.getLevel(), all)), clean, all);
                    return false;
                }
                start[0] = tick;
                dragon.setHoldPosition(true);
                ((AttackerUnit) dragon).setUnitAttackTargetForced(first);
                if (!dragon.isBreathing() && !dragon.startBreath(first, first.position()))
                    return done(helper, List.of("the breath at the first foe did not start"), clean, all);
            }
            int order = start[0] + orderAfter;
            if (tick == order) {
                if (!dragon.isBreathing())
                    return done(helper, List.of("the breath at the first foe ended before the order"), clean, all);
                before[0] = second.getHealth();
                ((AttackerUnit) dragon).setUnitAttackTargetForced(second);
            }
            if (tick > order && second.getHealth() < before[0] - 0.01f)
                return done(helper, List.of(), clean, all);
            if (tick >= order + TURN_TICKS)
                return done(helper, List.of(String.format(Locale.ROOT, "ordered onto a second foe"
                    + " mid-breath, the dragon had not burnt it %d ticks later (breathing: %s): the fire stayed on the"
                    + " first foe", TURN_TICKS, dragon.isBreathing())), clean, all);
            return false;
        });
    }

    // ---- buildings ---------------------------------------------------------------------------------------------------

    /**
     * Ordered onto an enemy windmill, the flying dragon burns it with its breath - the building loses health on at least
     * three separate ticks within two seconds of the first - and throws no ghast fireball at any point.
     */
    @GameTest(template = ARENA, timeoutTicks = 420)
    public static void bone_dragon_burns_a_building_with_breath_not_a_ghast_fireball(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "building"), foeOwner = owner + "_foe";
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        BuildingPlacement mill = BuildingUtils.getNewBuildingPlacement(Buildings.WIND_GENERATOR_VILLAGERS, level,
            helper.absolutePos(new BlockPos(9, 1, 7)), Rotation.NONE, foeOwner, false);
        if (mill == null) {
            helper.fail("could not place the windmill");
            return;
        }
        for (var b : mill.getBlocks())
            if (!b.getBlockState().isAir())
                level.setBlock(b.getBlockPos(), b.getBlockState(), 3);
        mill.initPlacedBlocks();
        mill.isBuilt = true;
        BuildingServerEvents.getBuildings().add(mill);
        BoneDragonUnit dragon = dragon(helper, owner, 2.5, AIR_UP, 3.5, -90f);
        Runnable clean = TestCleanup.atEnd(helper, () -> {
            if (dragon != null)
                dragon.discard();
            BuildingServerEvents.getBuildings().remove(mill);
        });
        if (dragon == null) {
            clean.run();
            helper.fail("could not create the dragon");
            return;
        }
        List<Mob> all = List.of(dragon);
        AABB arena = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(24, 32, 24).move(8, 0, 8);
        int[] firstHit = { -1 }, drops = { 0 };
        float[] last = { mill.getHealth() };
        DragonDiveGameTests.everyTick(helper, 410, tick -> {
            List<String> problems = new ArrayList<>();
            if (tick == 2)
                ((AttackerUnit) dragon).setAttackBuildingTarget(mill.centrePos);
            if (tick < 2)
                return false;
            if (!level.getEntitiesOfClass(GhastUnitFireball.class, arena, f -> f.getOwner() == dragon).isEmpty())
                problems.add("the dragon threw a ghast fireball at the building (lovish: no ghast projectiles)");
            float hp = mill.getHealth();
            if (hp < last[0] - 0.01f) {
                if (firstHit[0] < 0)
                    firstHit[0] = tick;
                if (tick <= firstHit[0] + 40)
                    drops[0]++;
            }
            last[0] = hp;
            if (!problems.isEmpty())
                return done(helper, problems, clean, all);
            if (firstHit[0] >= 0 && tick >= firstHit[0] + 40 || tick >= 400) {
                if (firstHit[0] < 0)
                    problems.add("the windmill was never burnt (" + mill.getHealth() + " health)");
                else if (drops[0] < 3)
                    problems.add("the windmill lost health on " + drops[0] + " ticks in the 2 s from the first hit (want"
                        + " >= 3: a breath burns all along, not one blast)");
                return done(helper, problems, clean, all);
            }
            return false;
        });
    }
}
