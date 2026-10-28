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

    /**
     * A possessed artillery click the dead zone refuses costs no cooldown (the set-up reds on main, Oct 28: one refused
     * click right after set-up burnt the Bombard's 68-tick cooldown, so it "set up but never fired"). An enemy 3 blocks
     * in front of the crosshair - inside the 6-block MIN_RANGE - refuses the click; once it is gone the next click fires.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 200)
    public static void a_possessed_shot_refused_for_the_dead_zone_costs_no_cooldown(GameTestHelper helper) {
        String owner = "gametest_arty_possess_deadzone_" + helper.absolutePos(BlockPos.ZERO).asLong();
        java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(owner.getBytes());
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        var b = bombard(helper, owner, 1.5, 8.5);
        VillagerUnit v = foe(helper, owner + "_foe", 4.5, 8.5);
        if (b == null || v == null) {
            if (b != null) b.discard();
            if (v != null) v.discard();
            helper.fail("could not create the units");
            return;
        }
        double vx = v.getX(), vy = v.getY(), vz = v.getZ();
        boolean[] done = { false }, active = { false };
        Runnable cleanUp = () -> {
            done[0] = true;
            com.solegendary.reignofnether.hero.PossessionServer.release(uuid, null);
            b.discard();
            v.discard();
        };
        helper.runAfterDelay(3, () -> {
            String why = com.solegendary.reignofnether.hero.PossessionServer.claim(helper.getLevel(), owner, uuid,
                b.getId(), true);
            if (why != null) {
                cleanUp.run();
                helper.fail("could not possess the bombard: " + why);
                return;
            }
            active[0] = true;
        });
        int clickAt = 5, clearAt = 6, deadline = 20;
        int[] tick = { 0 };
        helper.onEachTick(() -> {
            if (done[0] || !active[0])
                return;
            tick[0]++;
            int t = tick[0];
            if (t < clearAt)
                hold(v, vx, vy, vz);   // the enemy stands in front of the crosshair, inside the dead zone
            else if (t == clearAt)
                v.discard();           // ...and is gone: the aim is clear
            if (t < clickAt)
                return;
            if (!com.solegendary.reignofnether.unit.SetUp.isSetUp(b)) {
                cleanUp.run();
                throw new GameTestAssertException("the standing crew was not set up - the test proves nothing");
            }
            com.solegendary.reignofnether.hero.PossessionServer.attack(uuid, b.getId(), -90, 10);
            if (t == clickAt && shellsOf(b) > 0) {
                cleanUp.run();
                throw new GameTestAssertException("fired at an enemy 3 blocks away, inside the 6-block dead zone");
            }
            if (t > clickAt && shellsOf(b) > 0) {
                cleanUp.run();
                helper.succeed();
            } else if (t >= deadline) {
                cleanUp.run();
                throw new GameTestAssertException((t - clickAt) + " ticks after a click the dead zone refused, with the"
                    + " aim clear, it still had not fired: the refused click cost its cooldown");
            }
        });
    }

    /** A wall between the galley and its target: it shells over the wall from where it stands, it doesn't walk in. */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 420)
    public static void artillery_fires_over_a_wall_without_walking_in(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_arty_wall_" + helper.absolutePos(BlockPos.ZERO).asLong();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
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
                throw new GameTestAssertException("no shell has landed on the target yet (" + String.format("%.1f",
                    flat(g, v)) + " away, target at " + v.blockPosition().toShortString() + ", set-up left "
                    + com.solegendary.reignofnether.unit.SetUp.ticksLeft(g) + ", shells " + shellsOf(g) + "); galley: "
                    + state(g));
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
        for (int t = 1; t < 160; t++) {
            int tick = t;
            helper.runAfterDelay(t, () -> {
                hold(g, gx, gy, gz);   // the gull hovers on its spot for the whole test
                if (tick >= 140)
                    return;
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
            through.setDeltaMovement(0.3, 0, 0);
            level.addFreshEntity(through);
        });
        helper.runAfterDelay(150, () -> {
            try {
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
     * Artillery body-blocked on a move and hit by a flyer keeps its move (Itachi 2's review of #222, issue #35): the
     * contact rule's retaliation stopped the move and then asked for the flyer as its target, which artillery refuses,
     * so the crew stood with no order and no target and died where it stood. It cannot hit the flyer; it must at least
     * keep trying to walk out.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 300)
    public static void blocked_artillery_hit_by_a_flyer_keeps_moving(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_arty_blocked_air_" + helper.absolutePos(BlockPos.ZERO).asLong();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        var b = bombard(helper, owner, 1.5, 8.5);
        var g = EntityRegistrar.GULL_SPOTTER_UNIT.get().create(level);
        if (b == null || g == null) {
            if (b != null) b.discard();
            helper.fail("could not create the Bombard Crew or the Gull Spotter");
            return;
        }
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        double gx = o.getX() + 10.5, gy = o.getY() + 2.5, gz = o.getZ() + 8.5;
        g.moveTo(gx, gy, gz, 90, 0);
        g.setOwnerName(owner + "_foe");
        level.addFreshEntity(g);
        BlockPos dest = helper.absolutePos(new BlockPos(14, 2, 8));
        double bx = b.getX(), by = b.getY(), bz = b.getZ();
        boolean[] done = { false };
        int[] tick = { 0 }, hitAt = { -1 };
        Runnable cleanUp = () -> {
            done[0] = true;
            b.discard();
            g.discard();
        };
        helper.runAfterDelay(3, () -> b.setMoveTarget(dest));
        helper.onEachTick(() -> {
            if (done[0])
                return;
            tick[0]++;
            hold(g, gx, gy, gz);   // the gull hovers in front of it
            // body-blocked: held where it stands, its path stays live but it makes no headway (a wall would not do -
            // the walk ends at the wall and the move with it, which is not blocked)
            hold(b, bx, by, bz);
            if (hitAt[0] < 0) {
                if (tick[0] > 5 && AttackerUnit.blockedOnMove(b)) {
                    hitAt[0] = tick[0];
                    b.hurt(level.damageSources().indirectMagic(g, g), 1f);   // the gull hits the blocked crew
                } else if (tick[0] > 90) {
                    cleanUp.run();
                    throw new GameTestAssertException("the held crew never counted as blocked on its move - the "
                        + "test proves nothing (move target " + b.getMoveGoal().getMoveTarget() + ")");
                }
                return;
            }
            if (flyer(b.getTarget()) || flyer(b.getTargetGoal().getTarget())) {
                cleanUp.run();
                throw new GameTestAssertException("the crew took the flyer as its target");
            }
            // a ground enemy in weapon reach (a neighbouring test's) is a fair pick for the contact rule: fighting it is
            // responding. Dropping the move with nothing to fight is the bug
            var goalTarget = b.getTargetGoal().getTarget();
            if (b.getMoveGoal().getMoveTarget() == null && goalTarget == null && b.getTarget() == null) {
                cleanUp.run();
                throw new GameTestAssertException((tick[0] - hitAt[0]) + " ticks after the flyer hit it the blocked "
                    + "crew had dropped its move, with no target: it stands and dies");
            }
            if (goalTarget != null) {
                cleanUp.run();
                helper.succeed();   // it took a ground enemy instead: it fights back
                return;
            }
            if (tick[0] - hitAt[0] >= 5) {
                cleanUp.run();
                helper.succeed();
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

    /**
     * Set-up time when possessed: a player walking the crew by hand (navigation idle, so only the keys move it) a
     * single block - under the 1.5-block drift - and clicking every tick from then on gets no shell in the first
     * SET_UP ticks after it comes to rest, and one soon after (a click refused for the set-up costs no cooldown).
     * Driven by the keys alone (a headless possession).
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 300)
    public static void possessed_artillery_needs_two_seconds_to_set_up_after_moving(GameTestHelper helper) {
        possessedSetUpTest(helper, "keys", false, false);
    }

    /**
     * The same, driven the way a real client drives it: a MOVE every tick with the keys and an honest claim of where
     * its prediction put the unit (PossessionServer.applyMove), the path that moves the unit by the claim and leaves
     * its zza/xxa at 0.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 300)
    public static void possessed_artillery_driven_by_a_client_needs_two_seconds_to_set_up(GameTestHelper helper) {
        possessedSetUpTest(helper, "claims", true, false);
    }

    /**
     * Knocked back while its player stands still (lovish, Oct 27: "only my walking" costs the set-up, as for the AI
     * crew): a hit 10 ticks after it stopped, pushing it back toward where it came from, does not restart the 2 s - it
     * still fires on the same schedule. Keys-driven: the server moves it.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 300)
    public static void a_possessed_crew_knocked_back_while_standing_keeps_its_set_up(GameTestHelper helper) {
        possessedSetUpTest(helper, "knock", false, true);
    }

    /**
     * The same from a real client: the server's hit (its shove opens the claims' allowance) reaches the client, whose
     * prediction carries the knockback, so the claims slide it with the keys up.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 300)
    public static void a_crew_possessed_from_a_client_knocked_back_keeps_its_set_up(GameTestHelper helper) {
        possessedSetUpTest(helper, "knockclaims", true, true);
    }

    /**
     * A lying client cannot carry its crew off by hops (round-4 review of #222): position claims that hop it ~3 blocks
     * east with no walk key held are a move like any walk - the 2 s set-up starts again where it lands. Only a shove
     * the server gave it (knockback) may fly it through the air for free.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 300)
    public static void a_crew_hopped_by_keyless_client_claims_must_set_up_again(GameTestHelper helper) {
        keylessHopTest(helper, "hop", false, false);
    }

    /**
     * The same after one sprint-jump claim with no step (round-5 review of #222): the client's own sprint and jump
     * flags widen what a claim may cover, but they are no shove the server gave - the hops still cost the set-up.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 300)
    public static void a_crew_hopped_after_a_faked_sprint_jump_must_set_up_again(GameTestHelper helper) {
        keylessHopTest(helper, "sjhop", true, false);
    }

    /**
     * The same after a real run with the keys and one claim with no step (round-6 review of #222): the keys run's own
     * momentum is no shove the server gave, so the keyless hops that follow still cost the set-up.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 300)
    public static void a_crew_hopped_after_a_keys_run_must_set_up_again(GameTestHelper helper) {
        keylessHopTest(helper, "keyshop", false, true);
    }

    /** Why a possessed crew's clicks fired nothing, for the failure message (CI shows only the message). */
    static String possessedFireState(net.minecraft.world.entity.Mob b, java.util.UUID uuid, double x0, double z0) {
        var p = com.solegendary.reignofnether.hero.PossessionServer.of(uuid);
        StringBuilder near = new StringBuilder();
        for (var le : b.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                b.getBoundingBox().inflate(7), e -> e != b))
            near.append(' ').append(le.getType().toShortString()).append(String.format("@%.1f", flat(b, le)));
        String aim = "?";
        if (p != null) {
            var at = p.aimPoint(b);
            var bp = net.minecraft.core.BlockPos.containing(at.x - 0.01 * Math.signum(at.x - b.getX()), at.y - 0.01,
                at.z);
            aim = String.format("%.2f away, %.2f below the feet, at %s (%s), eye %.2f, pose %s, yaw %.0f, pitch %.1f, "
                    + "ground ahead [%s ]",
                Math.hypot(at.x - b.getX(), at.z - b.getZ()), b.getY() - at.y, bp.toShortString(),
                b.level().getBlockState(bp).getBlock().getName().getString(), b.getEyeHeight(), b.getPose(),
                b.getYRot(), p.aimPitch(), groundAhead(b, p));
        }
        return String.format("set-up left %d, cooldown %s, on ground %b, %.2f from the spot, y %.2f, nearby [%s ], "
                + "shells %d, aim %s",
            com.solegendary.reignofnether.unit.SetUp.ticksLeft(b), p == null ? "no possession" : p.attackCooldownLeft(),
            b.onGround(), Math.hypot(b.getX() - x0, b.getZ() - z0), b.getY(), near, shellsOf(b), aim);
    }

    /**
     * A knockback is no free reposition (the #222 follow-up, lovish's option A): while a hit carries a possessed crew
     * with its keys resting, its moves keep the set-up, so the client's claims may move it only where the knockback
     * does. A lying client that claims it 1.4 blocks sideways (north, across a push west) during the knockback - under
     * the 1.5-block drift, so the set-up alone would never notice - must leave it within a few tenths of the push's line.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 200)
    public static void a_knocked_back_crew_is_not_moved_sideways_by_keyless_claims(GameTestHelper helper) {
        String owner = "gametest_arty_possess_knockside_" + helper.absolutePos(BlockPos.ZERO).asLong();
        java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(owner.getBytes());
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        var b = bombard(helper, owner, 7.5, 8.5);
        if (b == null) {
            helper.fail("could not create the bombard");
            return;
        }
        boolean[] done = { false }, active = { false };
        Runnable cleanUp = () -> {
            done[0] = true;
            com.solegendary.reignofnether.hero.PossessionServer.release(uuid, null);
            b.discard();
        };
        helper.runAfterDelay(3, () -> {
            String why = com.solegendary.reignofnether.hero.PossessionServer.claim(helper.getLevel(), owner, uuid,
                b.getId(), true);
            if (why != null) {
                cleanUp.run();
                helper.fail("could not possess the bombard: " + why);
                return;
            }
            active[0] = true;
        });
        int knockAt = 10, sideTicks = 7, checkAt = knockAt + sideTicks + 3;
        double side = 0.2;
        int[] tick = { 0 }, seq = { 0 };
        double[] hit = new double[2];
        helper.onEachTick(() -> {
            if (done[0] || !active[0])
                return;
            tick[0]++;
            int t = tick[0];
            if (t == knockAt) {
                hit[0] = b.getX();
                hit[1] = b.getZ();
                b.knockback(0.4, 1, 0);   // a hit from the east: pushed west
            }
            if (t >= knockAt && t < knockAt + sideTicks)   // the lie: no push west, 0.2 a tick north, keys at rest
                com.solegendary.reignofnether.hero.PossessionServer.applyMove(uuid, b.getId(), 0, 0, -90, 10, false,
                    false, hit[0], b.getY(), hit[1] - side * (t - knockAt + 1), ++seq[0]);
            else   // a real client claims every tick, standing still too
                com.solegendary.reignofnether.hero.PossessionServer.applyMove(uuid, b.getId(), 0, 0, -90, 10, false,
                    false, b.getX(), b.getY(), b.getZ(), ++seq[0]);
            if (t < checkAt)
                return;
            double sideways = Math.abs(b.getZ() - hit[1]);
            cleanUp.run();
            if (sideways > 0.5)
                throw new GameTestAssertException(String.format("keyless claims moved the knocked-back crew %.2f "
                    + "blocks sideways (claimed %.2f): the knockback only pushed it west", sideways, side * sideTicks));
            helper.succeed();
        });
    }

    /**
     * The cap never corrects an honest client (round 1 of #307's review): a knocked-back crew whose client lags 3 ticks
     * each way, hit from the east and two ticks later from the south, flies where its prediction carries it - first
     * west, then north from wherever the first push had got it - with no correction.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 200)
    public static void a_lagging_client_knocked_back_twice_is_never_corrected(GameTestHelper helper) {
        honestKnockTest(helper, "knock2", 3, -1, new double[][] { { 0, 1, 0 }, { 2, 0, 1 } });
    }

    /**
     * The same for a hit at 45 degrees into a wall: the prediction is stopped on the blocked axis and slides along the
     * wall on the other, well off the push's own line - vanilla collision, so no correction either.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 200)
    public static void a_crew_knocked_into_a_wall_at_an_angle_slides_without_correction(GameTestHelper helper) {
        honestKnockTest(helper, "knockwall", 2, 9, new double[][] { { 0, -1, 1 } });
    }

    /**
     * An honest client of a possessed Bombard Crew standing at (8.5, 8.5), simulated in the test: the server's hits
     * ({@code hits}: {tick after the first, x, z} as for LivingEntity.knockback(0.4, x, z)) reach its prediction
     * {@code lag} ticks late as the velocity the server gave (the motion packet sets it), the prediction flies at air
     * drag (0.91 a tick, the farthest a push carries it), stopped on x at a wall at {@code wallX} (relative; -1: none),
     * and each tick's claim reaches the server {@code lag} ticks after it was taken. Not one may be corrected.
     */
    static void honestKnockTest(GameTestHelper helper, String tag, int lag, int wallX, double[][] hits) {
        String owner = "gametest_arty_possess_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
        java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(owner.getBytes());
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        if (wallX >= 0)
            for (int z = 0; z <= 15; z++)
                for (int y = 2; y <= 4; y++)
                    helper.setBlock(new BlockPos(wallX, y, z), Blocks.STONE);
        var b = bombard(helper, owner, 8.5, 8.5);
        if (b == null) {
            helper.fail("could not create the bombard");
            return;
        }
        boolean[] done = { false }, active = { false };
        Runnable cleanUp = () -> {
            done[0] = true;
            com.solegendary.reignofnether.hero.PossessionServer.release(uuid, null);
            b.discard();
        };
        // the client's prediction: position and velocity; a wall's west face, less the crew's half width
        double[] c = new double[4];
        double wallFace = wallX < 0 ? Double.MAX_VALUE : helper.absolutePos(new BlockPos(wallX, 0, 0)).getX() - b.getBbWidth() / 2;
        helper.runAfterDelay(3, () -> {
            String why = com.solegendary.reignofnether.hero.PossessionServer.claim(helper.getLevel(), owner, uuid,
                b.getId(), true);
            if (why != null) {
                cleanUp.run();
                helper.fail("could not possess the bombard: " + why);
                return;
            }
            c[0] = b.getX();
            c[1] = b.getZ();
            active[0] = true;
        });
        int knockAt = 5, endAt = knockAt + 28;   // well inside HOVER_TICKS of claims
        java.util.ArrayDeque<double[]> inFlight = new java.util.ArrayDeque<>();   // {due tick, x, z, seq}
        double[][] given = new double[hits.length][];
        int[] tick = { 0 }, seq = { 0 }, before = { 0 };
        double[] start = new double[2];
        helper.onEachTick(() -> {
            if (done[0] || !active[0])
                return;
            tick[0]++;
            int t = tick[0];
            var p = com.solegendary.reignofnether.hero.PossessionServer.of(uuid);
            if (p == null) {
                cleanUp.run();
                throw new GameTestAssertException("the possession ended");
            }
            if (t == knockAt) {
                before[0] = p.corrections;
                start[0] = b.getX();
                start[1] = b.getZ();
            }
            for (int i = 0; i < hits.length; i++) {
                if (t == knockAt + (int) hits[i][0]) {
                    b.knockback(0.4, hits[i][1], hits[i][2]);
                    given[i] = new double[] { b.getDeltaMovement().x, b.getDeltaMovement().z };
                }
                if (given[i] != null && t == knockAt + (int) hits[i][0] + lag) {   // the motion packet arrives
                    c[2] = given[i][0];
                    c[3] = given[i][1];
                }
            }
            // the prediction's tick: move, stopped on x by the wall, then air drag
            c[0] += c[2];
            if (c[0] > wallFace) {
                c[0] = wallFace;
                c[2] = 0;
            }
            c[1] += c[3];
            c[2] *= 0.91;
            c[3] *= 0.91;
            if (t <= endAt)
                inFlight.add(new double[] { t + lag, c[0], c[1], ++seq[0] });
            while (!inFlight.isEmpty() && inFlight.peek()[0] <= t) {
                double[] m = inFlight.poll();
                com.solegendary.reignofnether.hero.PossessionServer.applyMove(uuid, b.getId(), 0, 0, -90, 10, false,
                    false, m[1], b.getY(), m[2], (int) m[3]);
            }
            if (t < endAt + lag + 2)
                return;
            int corrected = p.corrections - before[0];
            double moved = Math.hypot(b.getX() - start[0], b.getZ() - start[1]);
            double off = Math.hypot(b.getX() - c[0], b.getZ() - c[1]);
            cleanUp.run();
            if (moved < 1.0)
                throw new GameTestAssertException(String.format("the knockback moved the crew only %.2f blocks: the "
                    + "test proves nothing", moved));
            if (corrected > 0 || off > 0.35)
                throw new GameTestAssertException(String.format("an honest client was corrected %d times (it ended "
                    + "%.2f blocks from its prediction, after %.2f blocks of knockback)", corrected, off, moved));
            helper.succeed();
        });
    }

    /** The top block at each whole block along the look direction (1-10 out), height over the feet and its name. */
    static String groundAhead(net.minecraft.world.entity.Mob b,
            com.solegendary.reignofnether.hero.PossessionServer.Possession p) {
        var dir = net.minecraft.world.phys.Vec3.directionFromRotation(0, b.getYRot());
        StringBuilder out = new StringBuilder();
        int feet = net.minecraft.util.Mth.floor(b.getY());
        for (int d = 1; d <= 10; d++) {
            int x = net.minecraft.util.Mth.floor(b.getX() + dir.x * d), z = net.minecraft.util.Mth.floor(b.getZ() + dir.z * d);
            String top = "-";
            for (int y = feet + 3; y >= feet - 2; y--) {
                var st = b.level().getBlockState(new BlockPos(x, y, z));
                if (!st.isAir()) {
                    top = (y - feet) + st.getBlock().getName().getString().substring(0, 2);
                    break;
                }
            }
            out.append(' ').append(top);
        }
        return out.toString();
    }

    /** Falling blocks over the arena, each logged once: the tick, where it started and where it is (relative). */
    static void watchFalling(GameTestHelper helper, java.util.Set<Integer> seen, StringBuilder log, int tick) {
        var box = new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(-2, 0, -2)),
            helper.absolutePos(new BlockPos(18, 40, 18)));
        for (var f : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class, box))
            if (seen.add(f.getId()))
                log.append(String.format(" t%d %s from %s at %s", tick, f.getBlockState().getBlock().getName().getString(),
                    helper.relativePos(f.getStartPos()).toShortString(),
                    helper.relativePos(f.blockPosition()).toShortString()));
    }

    static void keylessHopTest(GameTestHelper helper, String tag, boolean sprintJumpFirst, boolean keysRunFirst) {
        String owner = "gametest_arty_possess_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
        java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(owner.getBytes());
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        var b = bombard(helper, owner, 1.5, 8.5);
        if (b == null) {
            helper.fail("could not create the bombard");
            return;
        }
        boolean[] done = { false }, active = { false };
        Runnable cleanUp = () -> {
            done[0] = true;
            com.solegendary.reignofnether.hero.PossessionServer.release(uuid, null);
            b.discard();
        };
        double[] base = new double[3];
        helper.runAfterDelay(3, () -> {
            String why = com.solegendary.reignofnether.hero.PossessionServer.claim(helper.getLevel(), owner, uuid,
                b.getId(), true);
            if (why != null) {
                cleanUp.run();
                helper.fail("could not possess the bombard: " + why);
                return;
            }
            base[0] = b.getX();
            base[1] = b.getY();
            base[2] = b.getZ();
            active[0] = true;
        });
        // three keyless hops east, 8 ticks each: up, then down to the ground, 0.12 a tick sideways (a walk's pace)
        // the last claim lands a little below the ground, so the move ends on it (on the ground, as a real landing)
        double[] arc = { 0.35, 0.6, 0.75, 0.8, 0.75, 0.6, 0.35, -0.1 };
        // with sprintJumpFirst each hop is led by one forward + sprint + jump claim where it stands (no step) and
        // followed by 6 ticks with no claim, in which the server lands it (the boost needs the unit on the ground)
        int lead = sprintJumpFirst ? 1 : 0, rest = sprintJumpFirst ? 6 : 0, cycle = lead + arc.length + rest;
        // with keysRunFirst it first runs east by its keys (sprinting, no claims) and the hops start from where one
        // claim with no step stops it
        // (a sprint-jump at its end: the most momentum keys can give it); one hop of 0.2 a tick, past the 1.5-block
        // drift, fits in what that momentum would open
        int hops = keysRunFirst ? 1 : 3, hopFrom = keysRunFirst ? 16 : 10, hopEnd = hopFrom + hops * cycle - rest;
        double pace = keysRunFirst ? 0.2 : 0.12, minMoved = keysRunFirst ? 1.5 : 2.0;
        java.util.Set<Integer> fallSeen = new java.util.HashSet<>();
        StringBuilder fallLog = new StringBuilder();
        int[] tick = { 0 }, seq = { 0 };
        helper.onEachTick(() -> {
            if (done[0] || !active[0])
                return;
            tick[0]++;
            int t = tick[0];
            watchFalling(helper, fallSeen, fallLog, t);
            if (keysRunFirst && t < hopFrom - 1) {
                com.solegendary.reignofnether.hero.PossessionServer.applyInput(uuid, b.getId(), 1, 0, -90, 10,
                    t == hopFrom - 3, false, true);
                return;
            }
            if (keysRunFirst && t == hopFrom - 1) {   // one claim with no step: where the run has carried it
                base[0] = b.getX();
                base[1] = b.getY();
                base[2] = b.getZ();
                com.solegendary.reignofnether.hero.PossessionServer.applyMove(uuid, b.getId(), 0, 0, -90, 10, false,
                    false, base[0], base[1], base[2], ++seq[0]);
                return;
            }
            if (t >= hopFrom && t < hopEnd) {
                int hop = (t - hopFrom) / cycle, phase = (t - hopFrom) % cycle - lead;
                if (phase >= arc.length)
                    return;
                double x = base[0] + pace * (hop * arc.length + phase + 1);
                if (phase < 0)
                    com.solegendary.reignofnether.hero.PossessionServer.applyMove(uuid, b.getId(), 1, 0, -90, 10,
                        true, false, true, x, base[1] - 0.1, base[2], ++seq[0]);
                else
                    com.solegendary.reignofnether.hero.PossessionServer.applyMove(uuid, b.getId(), 0, 0, -90, 10,
                        false, false, x, base[1] + arc[phase], base[2], ++seq[0]);
                return;
            }
            if (t < hopEnd)
                return;
            int since = t - hopEnd;
            if (since == 0) {
                double moved = Math.hypot(b.getX() - base[0], b.getZ() - base[2]);
                if (moved < minMoved) {
                    cleanUp.run();
                    throw new GameTestAssertException(String.format("the hops were refused (moved %.2f): the test no "
                        + "longer checks a hop past the 1.5-block drift", moved));
                }
            }
            com.solegendary.reignofnether.hero.PossessionServer.attack(uuid, b.getId(), -90, 10);
            if (shellsOf(b) > 0) {
                cleanUp.run();
                if (since < SET_UP - 2)
                    throw new GameTestAssertException("fired " + since + " ticks after hopping "
                        + String.format("%.2f", Math.hypot(b.getX() - base[0], b.getZ() - base[2]))
                        + " blocks with no keys: it needs " + SET_UP + " ticks to set up again");
                helper.succeed();
            } else if (since > SET_UP + 10) {
                String why = possessedFireState(b, uuid, base[0], base[2]);
                cleanUp.run();
                throw new GameTestAssertException("set up but never fired (" + since + " ticks after the hops; " + why
                    + "; falling blocks [" + fallLog + " ])");
            }
        });
    }

    static void possessedSetUpTest(GameTestHelper helper, String tag, boolean claims, boolean knock) {
        String owner = "gametest_arty_possess_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
        java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(owner.getBytes());
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        var b = bombard(helper, owner, 1.5, 8.5);
        if (b == null) {
            helper.fail("could not create the bombard");
            return;
        }
        boolean[] done = { false };
        Runnable cleanUp = () -> {
            done[0] = true;
            com.solegendary.reignofnether.hero.PossessionServer.release(uuid, null);
            b.discard();
        };
        boolean[] active = { false };
        double[] start = new double[2];
        helper.runAfterDelay(3, () -> {
            String why = com.solegendary.reignofnether.hero.PossessionServer.claim(helper.getLevel(), owner, uuid,
                b.getId(), true);
            if (why != null) {
                cleanUp.run();
                helper.fail("could not possess the bombard: " + why);
                return;
            }
            start[0] = b.getX();
            start[1] = b.getZ();
            active[0] = true;
        });
        // keys held east (yaw -90) until it has gone 0.8 blocks, then let go
        int[] tick = { 0 }, stoppedAt = { -1 }, seq = { 0 };
        double[] last = { b.getX(), b.getZ() };
        helper.onEachTick(() -> {
            if (done[0] || !active[0])
                return;
            tick[0]++;
            double moved = Math.hypot(b.getX() - start[0], b.getZ() - start[1]);
            if (stoppedAt[0] < 0) {
                float fwd = moved < 0.8 ? 1 : 0;
                if (claims)   // the client's prediction: a walking step east, or where it stands
                    com.solegendary.reignofnether.hero.PossessionServer.applyMove(uuid, b.getId(), fwd, 0, -90, 10,
                        false, false, b.getX() + 0.15 * fwd, b.getY(), b.getZ(), ++seq[0]);
                else
                    com.solegendary.reignofnether.hero.PossessionServer.applyInput(uuid, b.getId(), fwd, 0, -90, 10,
                        false, false, false);
            }
            double step = Math.hypot(b.getX() - last[0], b.getZ() - last[1]);
            last[0] = b.getX();
            last[1] = b.getZ();
            int shells = shellsOf(b);
            if (stoppedAt[0] < 0) {
                if (shells > 0) {
                    cleanUp.run();
                    throw new GameTestAssertException("fired before it had even stopped walking (tick " + tick[0] + ")");
                }
                if (tick[0] > 4 && moved >= 0.8 && step < 0.01)
                    stoppedAt[0] = tick[0];
                else if (tick[0] > 80) {
                    cleanUp.run();
                    throw new GameTestAssertException(String.format("the possessed crew never walked a block and "
                        + "stopped (moved %.2f)", moved));
                }
                return;
            }
            if (moved >= 1.5) {
                cleanUp.run();
                throw new GameTestAssertException(String.format("walked %.2f blocks: past the 1.5-block drift, so "
                    + "the test no longer checks a short possessed walk", moved));
            }
            int since = tick[0] - stoppedAt[0];
            if (knock && since == 10)
                b.knockback(0.4, 1, 0);   // a hit from the east: pushed back west, toward where it started
            if (knock && claims && since >= 10 && since < 14) {   // the client's prediction of that knockback
                double[] slide = { 0.30, 0.16, 0.09, 0.05 };
                com.solegendary.reignofnether.hero.PossessionServer.applyMove(uuid, b.getId(), 0, 0, -90, 10, false,
                    false, b.getX() - slide[since - 10], b.getY(), b.getZ(), ++seq[0]);
            } else if (claims)   // a real client claims every tick, standing still too
                com.solegendary.reignofnether.hero.PossessionServer.applyMove(uuid, b.getId(), 0, 0, -90, 10, false,
                    false, b.getX(), b.getY(), b.getZ(), ++seq[0]);
            com.solegendary.reignofnether.hero.PossessionServer.attack(uuid, b.getId(), -90, 10);
            if (shells > 0) {
                cleanUp.run();
                if (since < SET_UP - 2)
                    throw new GameTestAssertException("fired " + since + " ticks after it stopped walking: it needs "
                        + SET_UP + " ticks to set up");
                helper.succeed();
            } else if (since > SET_UP + 10) {
                String why = possessedFireState(b, uuid, last[0], last[1]);
                cleanUp.run();
                throw new GameTestAssertException("set up but never fired (" + since + " ticks after it stopped; " + why + ")");
            }
        });
    }
}
