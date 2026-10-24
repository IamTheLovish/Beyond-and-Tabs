package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.units.tide.MortarGalleyUnit;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * IndirectFire (mortars, bombards, rocket batteries) in UnitBowAttackGoal: lobbed shots need no line of sight, and a
 * target inside the dead zone makes the unit back off to fire. tidewrought_t2_units_attack went red twice on Oct 21:
 * its Mortar Galley walked from 14 blocks to 6-7 (inside its 8-block dead zone) and never fired.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class ArtilleryGameTests {

    static MortarGalleyUnit galley(GameTestHelper helper, String owner, double x, double z) {
        MortarGalleyUnit g = EntityRegistrar.MORTAR_GALLEY_UNIT.get().create(helper.getLevel());
        if (g == null)
            return null;
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        g.moveTo(o.getX() + x, o.getY(), o.getZ() + z, -90, 0);
        g.setOwnerName(owner);
        helper.getLevel().addFreshEntity(g);
        // the bow it "holds" so UnitBowAttackGoal runs (given on production spawn, not by create): without it the
        // attack goal never starts, the galley never fires and never backs off (CI on #55: "aggressive false")
        g.setupEquipmentAndUpgradesServer();
        return g;
    }

    static VillagerUnit foe(GameTestHelper helper, String owner, double x, double z) {
        VillagerUnit v = EntityRegistrar.VILLAGER_UNIT.get().create(helper.getLevel());
        if (v == null)
            return null;
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        v.moveTo(o.getX() + x, o.getY(), o.getZ() + z, 90, 0);
        v.setOwnerName(owner);
        helper.getLevel().addFreshEntity(v);
        return v;
    }

    /** What the galley is doing, for a failure message (CI shows only the message). */
    static String state(MortarGalleyUnit g) {
        var nav = g.getNavigation();
        var path = nav.getPath();
        return "target " + (g.getTarget() == null ? "none" : g.getTarget().getType().toShortString())
            + ", goal target " + (g.getTargetGoal().getTarget() == null ? "none" : "set")
            + ", nav done " + nav.isDone() + ", path " + (path == null ? "none" : path.getTarget().toShortString()
                + " reached " + path.isDone()) + ", move target " + g.getMoveGoal().getMoveTarget()
            + ", bow " + g.getMainHandItem().is(net.minecraft.world.item.Items.BOW)
            + ", goals [" + g.runningGoalsForTest() + "], using item " + g.isUsingItem()
            + ", wants move " + g.getMoveControl().hasWanted()
            + ", motion " + String.format("%.3f", g.getDeltaMovement().horizontalDistance())
            + ", yaw " + Math.round(net.minecraft.util.Mth.wrapDegrees(g.getYRot())) + " body "
            + Math.round(net.minecraft.util.Mth.wrapDegrees(g.yBodyRot))
            + ", at " + g.blockPosition().toShortString() + ", path index "
            + (nav.getPath() == null ? -1 : nav.getPath().getNextNodeIndex() + "/" + nav.getPath().getNodeCount())
            + ", hold " + g.getHoldPosition() + ", aggressive " + g.isAggressive()
            + ", on ground " + g.onGround() + ", speed attr " + g.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
    }

    static double flat(net.minecraft.world.entity.Entity a, net.minecraft.world.entity.Entity b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** A wall between the galley and its target: it shells over the wall from where it stands, it doesn't walk in. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 420)
    public static void artillery_fires_over_a_wall_without_walking_in(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_arty_wall_" + helper.absolutePos(BlockPos.ZERO).asLong();
        for (int z = 4; z <= 12; z++)
            for (int y = 2; y <= 5; y++)
                helper.setBlock(new BlockPos(7, y, z), Blocks.STONE);
        MortarGalleyUnit g = galley(helper, owner, 1.5, 8.5);
        VillagerUnit v = foe(helper, owner + "_foe", 15.5, 8.5);
        if (g == null || v == null) {
            helper.fail("could not create the units");
            return;
        }
        // walking IN is the bug; stepping back (Kite keeps artillery away from a foe inside 55% of its range) is fine
        double startDist = flat(g, v);
        double[] walked = { 0 };
        float full = v.getMaxHealth();
        helper.runAfterDelay(3, () -> ((AttackerUnit) g).setUnitAttackTarget(v));
        helper.succeedWhen(() -> {
            if (g.getTargetGoal().getTarget() != v && v.isAlive())
                ((AttackerUnit) g).setUnitAttackTarget(v);   // a neighbouring test's unit must not pull it away
            walked[0] = Math.max(walked[0], startDist - flat(g, v));
            if (walked[0] > 3)
                throw new GameTestAssertException("the galley walked " + Math.round(walked[0])
                    + " blocks toward a target it can shell over the wall (now " + Math.round(flat(g, v)) + " away)");
            if (v.isAlive() && v.getHealth() >= full)
                throw new GameTestAssertException("no shell has landed on the target yet");
            g.discard();
            v.discard();
            for (int z = 4; z <= 12; z++)
                for (int y = 2; y <= 5; y++)
                    helper.setBlock(new BlockPos(7, y, z), Blocks.AIR);
        });
    }

    /** A target inside the dead zone: the galley backs out past MIN_RANGE and then hits it. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 500)
    public static void artillery_backs_out_of_its_dead_zone(GameTestHelper helper) {
        String owner = "gametest_arty_dead_" + helper.absolutePos(BlockPos.ZERO).asLong();
        MortarGalleyUnit g = galley(helper, owner, 7.5, 8.5);
        VillagerUnit v = foe(helper, owner + "_foe", 11.5, 8.5);
        if (g == null || v == null) {
            helper.fail("could not create the units");
            return;
        }
        float full = v.getMaxHealth();
        double sx = g.getX(), sz = g.getZ();
        double[] moved = { 0 };
        helper.runAfterDelay(3, () -> ((AttackerUnit) g).setUnitAttackTarget(v));
        helper.succeedWhen(() -> {
            if (g.getTargetGoal().getTarget() != v && v.isAlive())
                ((AttackerUnit) g).setUnitAttackTarget(v);
            moved[0] = Math.max(moved[0], Math.hypot(g.getX() - sx, g.getZ() - sz));
            if (v.isAlive() && v.getHealth() >= full)
                throw new GameTestAssertException("never hit the target (moved at most "
                    + String.format("%.1f", moved[0]) + ") " + String.format("%.1f", flat(g, v))
                    + " blocks away (dead zone " + MortarGalleyUnit.MIN_RANGE + "); galley: " + state(g));
            g.discard();
            v.discard();
        });
    }

    /** The same dead zone with the Bombard Crew (a Pillager body, not Alex's Mobs' turtle): it also backs out. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 500)
    public static void bombard_backs_out_of_its_dead_zone(GameTestHelper helper) {
        String owner = "gametest_bombard_dead_" + helper.absolutePos(BlockPos.ZERO).asLong();
        com.solegendary.reignofnether.unit.units.tide.BombardCrewUnit b =
            EntityRegistrar.BOMBARD_CREW_UNIT.get().create(helper.getLevel());
        VillagerUnit v = foe(helper, owner + "_foe", 11.5, 8.5);
        if (b == null || v == null) {
            helper.fail("could not create the units");
            return;
        }
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        b.moveTo(o.getX() + 8.5, o.getY(), o.getZ() + 8.5, -90, 0);
        b.setOwnerName(owner);
        helper.getLevel().addFreshEntity(b);
        b.setupEquipmentAndUpgradesServer();
        double sx = b.getX(), sz = b.getZ();
        double[] moved = { 0 };
        float full = v.getMaxHealth();
        helper.runAfterDelay(3, () -> ((AttackerUnit) b).setUnitAttackTarget(v));
        helper.succeedWhen(() -> {
            if (b.getTargetGoal().getTarget() != v && v.isAlive())
                ((AttackerUnit) b).setUnitAttackTarget(v);
            moved[0] = Math.max(moved[0], Math.hypot(b.getX() - sx, b.getZ() - sz));
            if (v.isAlive() && v.getHealth() >= full)
                throw new GameTestAssertException("the bombard never hit the target (moved at most "
                    + String.format("%.1f", moved[0]) + ", now " + String.format("%.1f", flat(b, v))
                    + " away, dead zone " + com.solegendary.reignofnether.unit.units.tide.BombardCrewUnit.MIN_RANGE
                    + ", nav done " + b.getNavigation().isDone() + ", aggressive " + b.isAggressive() + ")");
            b.discard();
            v.discard();
        });
    }

    // ------------------------------------------------------------------ the artillery nerf (lovish, Oct 24)
    // "massed shell artillery forms an unapproachable death ball": the four BombardShell units (Bombard Crew, Mortar
    // Crawler, Rocket Battery, Mortar Galley) cannot hit air and need 2 s of standing still after moving before they
    // fire. SET_UP is lovish's 2 s written out, so the tests do not read the number back from the code they check.

    static final int SET_UP = 40;

    static com.solegendary.reignofnether.unit.units.tide.BombardCrewUnit bombard(GameTestHelper helper, String owner,
                                                                                 double x, double z) {
        var b = EntityRegistrar.BOMBARD_CREW_UNIT.get().create(helper.getLevel());
        if (b == null)
            return null;
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        b.moveTo(o.getX() + x, o.getY(), o.getZ() + z, -90, 0);
        b.setOwnerName(owner);
        helper.getLevel().addFreshEntity(b);
        b.setupEquipmentAndUpgradesServer();   // the bow UnitBowAttackGoal needs (see galley())
        return b;
    }

    static void hold(net.minecraft.world.entity.Mob m, double x, double y, double z) {
        m.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        m.moveTo(x, y, z, m.getYRot(), 0);
    }

    static boolean flyer(net.minecraft.world.entity.Entity e) {
        return e instanceof com.solegendary.reignofnether.unit.interfaces.Unit u && u.isFlyingUnit();
    }

    /** Shells in flight that this unit fired. */
    static int shellsOf(net.minecraft.world.entity.Mob shooter) {
        return shooter.level().getEntitiesOfClass(com.solegendary.reignofnether.unit.units.tide.BombardShell.class,
            shooter.getBoundingBox().inflate(40), s -> s.getOwner() == shooter).size();
    }

    /**
     * Cannot hit air: with only an enemy flyer in reach a Bombard Crew never takes it as a target - not by itself, not
     * in retaliation when the flyer hurts it, not on a direct attack order - and never fires; the other three shell
     * units refuse the direct order too. A shell passes through a flyer in its path, and a burst right beside a flyer
     * leaves it unhurt while a ground enemy in the same splash is hit.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 200)
    public static void artillery_never_targets_or_hurts_a_flyer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_arty_air_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        var b = bombard(helper, owner, 1.5, 8.5);
        var g = EntityRegistrar.GULL_SPOTTER_UNIT.get().create(level);
        if (b == null || g == null) {
            if (b != null) b.discard();
            helper.fail("could not create the Bombard Crew or the Gull Spotter");
            return;
        }
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        double gx = o.getX() + 13.5, gy = o.getY() + 2.5, gz = o.getZ() + 8.5;
        g.moveTo(gx, gy, gz, 90, 0);
        g.setOwnerName(foeOwner);
        level.addFreshEntity(g);
        float gullFull = g.getMaxHealth();
        // the other three shell units, each given the same direct order on the gull
        java.util.List<net.minecraft.world.entity.Mob> all = new java.util.ArrayList<>();
        all.add(b);
        int row = 0;
        for (var type : java.util.List.of(EntityRegistrar.MORTAR_CRAWLER_UNIT.get(),
                EntityRegistrar.ROCKET_BATTERY_UNIT.get(), EntityRegistrar.MORTAR_GALLEY_UNIT.get())) {
            var m = type.create(level);
            if (m == null)
                continue;
            BlockPos at = helper.absolutePos(new BlockPos(4, 2, 1 + row * 5));
            m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, -90, 0);
            ((com.solegendary.reignofnether.unit.interfaces.Unit) m).setOwnerName(owner);
            level.addFreshEntity(m);
            ((com.solegendary.reignofnether.unit.interfaces.Unit) m).setupEquipmentAndUpgradesServer();
            all.add(m);
            row++;
        }
        Runnable cleanUp = () -> {
            for (var m : all)
                m.discard();
            g.discard();
        };
        boolean[] groundTarget = { false };
        for (int t = 1; t < 140; t++) {
            int tick = t;
            helper.runAfterDelay(t, () -> {
                hold(g, gx, gy, gz);
                if (tick == 5)    // the gull "hurts" the crew: retaliation must not pick it
                    b.hurt(level.damageSources().indirectMagic(g, g), 1f);
                if (tick == 10)   // a direct attack order on the gull, for all four
                    for (var m : all)
                        ((AttackerUnit) m).setUnitAttackTargetForced(g);
                for (var m : all) {
                    var goalTarget = ((com.solegendary.reignofnether.unit.interfaces.Unit) m).getTargetGoal().getTarget();
                    if (flyer(m.getTarget()) || flyer(goalTarget)) {
                        cleanUp.run();
                        throw new GameTestAssertException(m.getType().toShortString() + " took the flyer as its target"
                            + " at tick " + tick + (tick >= 10 ? " (after a direct attack order)" : tick >= 5
                                ? " (in retaliation)" : " (by itself)"));
                    }
                    if (m == b && m.getTarget() != null)
                        groundTarget[0] = true;   // a neighbouring test's ground unit came into reach: a shot is fair
                }
                if (!groundTarget[0] && shellsOf(b) > 0) {
                    cleanUp.run();
                    throw new GameTestAssertException("the Bombard Crew fired with only a flyer in reach (tick " + tick + ")");
                }
            });
        }
        helper.runAfterDelay(140, () -> {
            if (g.getHealth() < gullFull) {
                cleanUp.run();
                throw new GameTestAssertException("the gull was hurt while the artillery stood by: " + g.getHealth());
            }
            // a shell flying straight through the gull: it passes through and does not burst on it
            var through = new com.solegendary.reignofnether.unit.units.tide.BombardShell(level, b, 9f);
            through.setPos(gx - 1.5, gy + g.getBbHeight() / 2, gz);
            through.setDeltaMovement(0.5, 0, 0);
            level.addFreshEntity(through);
        });
        helper.runAfterDelay(150, () -> {
            try {
                hold(g, gx, gy, gz);
                if (g.getHealth() < gullFull)
                    throw new GameTestAssertException("a shell burst on the gull it flew through: health "
                        + g.getHealth() + "/" + gullFull);
                // a burst right beside the gull, a ground enemy in the same splash: the walker is hit, the gull is not
                VillagerUnit v = foe(helper, foeOwner, 13.5, 6.5);
                if (v == null)
                    throw new GameTestAssertException("could not create the ground foe");
                all.add(v);
                float vFull = v.getHealth();
                var shell = new com.solegendary.reignofnether.unit.units.tide.BombardShell(level, b, 9f);
                shell.setPos(gx, gy - 1.0, gz - 1.0);
                level.addFreshEntity(shell);
                shell.burst();
                if (g.getHealth() < gullFull)
                    throw new GameTestAssertException("a shell bursting beside the gull hurt it: " + g.getHealth() + "/" + gullFull);
                if (v.getHealth() >= vFull)
                    throw new GameTestAssertException("the same burst missed the ground enemy a block away - the test proves nothing");
                cleanUp.run();
                helper.succeed();
            } catch (GameTestAssertException e) {
                cleanUp.run();
                throw e;
            }
        });
    }

    /**
     * Shared by the two set-up tests: a Bombard Crew walks ~3 blocks and stops, then gets {@code order} with its
     * cooldown cleared, so only the set-up can hold the shot. It must not fire in the first SET_UP ticks after it
     * stopped, and must fire soon after. Slack of 2 ticks: the test sees "stopped" a tick or two after the last step.
     */
    static void setUpTest(GameTestHelper helper, String tag, java.util.function.BiConsumer<
            com.solegendary.reignofnether.unit.units.tide.BombardCrewUnit, VillagerUnit> order, boolean withFoe) {
        String owner = "gametest_arty_setup_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        var b = bombard(helper, owner, 1.5, 8.5);
        VillagerUnit v = withFoe ? foe(helper, owner + "_foe", 15.5, 8.5) : null;
        if (b == null || (withFoe && v == null)) {
            if (b != null) b.discard();
            if (v != null) v.discard();
            helper.fail("could not create the units");
            return;
        }
        boolean[] done = { false };
        Runnable cleanUp = () -> {
            done[0] = true;
            b.discard();
            if (v != null)
                v.discard();
        };
        double sx = b.getX(), sz = b.getZ();
        BlockPos dest = helper.absolutePos(new BlockPos(4, 2, 8));
        double vx = v == null ? 0 : v.getX(), vy = v == null ? 0 : v.getY(), vz = v == null ? 0 : v.getZ();
        helper.runAfterDelay(3, () -> b.setMoveTarget(dest));
        int[] stoppedAt = { -1 };
        int[] tick = { 0 };
        double[] last = { sx, sz };
        int slack = 2;
        helper.onEachTick(() -> {
            if (done[0])
                return;
            tick[0]++;
            if (v != null)
                hold(v, vx, vy, vz);
            double step = Math.hypot(b.getX() - last[0], b.getZ() - last[1]);
            last[0] = b.getX();
            last[1] = b.getZ();
            int shells = shellsOf(b);
            if (stoppedAt[0] < 0) {
                if (shells > 0) {
                    cleanUp.run();
                    throw new GameTestAssertException("fired before it had even stopped walking (tick " + tick[0] + ")");
                }
                if (tick[0] > 5 && Math.hypot(b.getX() - sx, b.getZ() - sz) > 1.5 && b.getNavigation().isDone()
                        && step < 0.01) {
                    stoppedAt[0] = tick[0];
                    order.accept(b, v);
                    ((com.solegendary.reignofnether.unit.goals.UnitBowAttackGoal<?>) b.getAttackGoal()).resetCooldown();
                }
                if (tick[0] > 160) {
                    cleanUp.run();
                    throw new GameTestAssertException("the crew never walked to its move target and stopped");
                }
                return;
            }
            int since = tick[0] - stoppedAt[0];
            if (step > 0.05) {
                cleanUp.run();
                throw new GameTestAssertException("the crew moved again " + since + " ticks after it stopped - the test needs it standing");
            }
            if (shells > 0 && since < SET_UP - slack) {
                cleanUp.run();
                throw new GameTestAssertException("fired " + since + " ticks after it stopped walking: it needs "
                    + SET_UP + " ticks to set up");
            }
            if (shells > 0) {
                cleanUp.run();
                helper.succeed();
                return;
            }
            if (since > SET_UP + 40) {
                cleanUp.run();
                throw new GameTestAssertException("still had not fired " + since + " ticks after it stopped (set-up is "
                    + SET_UP + "); target " + (b.getTarget() == null ? "none" : "set") + ", using item " + b.isUsingItem());
            }
        });
    }

    /** Set-up time on an attack order: a crew that just stopped walking holds fire 2 s, then shells the target. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 300)
    public static void artillery_needs_two_seconds_to_set_up_after_moving(GameTestHelper helper) {
        setUpTest(helper, "atk", (b, v) -> ((AttackerUnit) b).setUnitAttackTarget(v), true);
    }

    /** Set-up time on Attack Ground: the same 2 s before the first shell goes at the ordered spot. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 300)
    public static void attack_ground_waits_for_the_set_up(GameTestHelper helper) {
        setUpTest(helper, "ground", (b, v) -> b.getRangedAttackGroundGoal().setGroundTarget(
            b.blockPosition().offset(10, -1, 0)), false);
    }
}
