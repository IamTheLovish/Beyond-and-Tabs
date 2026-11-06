package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.shared.SiegeTuning;
import com.solegendary.reignofnether.unit.units.storm.RocketBatteryUnit;
import com.solegendary.reignofnether.unit.units.storm.RocketLaunchPose;
import com.solegendary.reignofnether.unit.units.storm.RocketShell;
import com.solegendary.reignofnether.unit.units.tide.BombardShell;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The Rocket Battery fires its own projectile (lovish, Nov 5: "I want to change the rocket bots motor shots to another
 * animation and projectile"). Until then four artillery units shared ONE entity, {@link BombardShell}, so changing the
 * rocket bot's shot changed the others'. The promises held here:
 * <ol>
 * <li>the battery fires {@link RocketShell}s - never a BombardShell - and every other shell unit (Bombard Crew, Mortar
 * Galley, Mortar Crawler, the siege lobber) still fires exactly a BombardShell with the flight time, damage and splash
 * it had;</li>
 * <li>a rocket flies faster and flatter than the mortar lob to the same spot and lands on it;</li>
 * <li>the damage per target class is still one shared rule: a rocket takes 5 off a unit at the centre of its splash
 * (0.8 of that a block out) and 5 x 1.5 x 2 off a building, exactly what a BombardShell of the same damage does;</li>
 * <li>the battery's launch animation starts with each volley and runs out.</li>
 * </ol>
 * Every unit here has its AI off: nothing fires but the test, and no neighbour's unit is shot at.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class RocketShellGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "rocket_shell";

    @FunctionalInterface
    interface Fire {
        boolean at(double x, double y, double z);
    }

    static String owner(GameTestHelper helper, String tag) {
        return "gametest_rocketshell_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    static <T extends Mob> T spawn(GameTestHelper helper, EntityType<T> type, String owner, double x, double z) {
        T m = type.create(helper.getLevel());
        if (m == null)
            return null;
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        m.moveTo(o.getX() + x, o.getY(), o.getZ() + z, -90, 0);
        ((Unit) m).setOwnerName(owner);
        helper.getLevel().addFreshEntity(m);
        ((Unit) m).setupEquipmentAndUpgradesServer();
        m.setNoAi(true);
        return m;
    }

    /** Shells in flight that this unit fired, rockets included (a RocketShell is a BombardShell). */
    static List<BombardShell> shellsOf(Mob shooter) {
        return shooter.level().getEntitiesOfClass(BombardShell.class, shooter.getBoundingBox().inflate(64),
            s -> s.getOwner() == shooter);
    }

    static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    static void discardAll(List<? extends Entity> list) {
        for (Entity e : list)
            if (e != null && !e.isRemoved())
                e.discard();
    }

    /**
     * A unit added to the level only ticks once its chunk is entity-ticking, and a shell added in a chunk that is not yet
     * visible is not found by an entity query (the last arena of a batch on a cold CI server: the guard test found "no
     * shell" from all four of its guns in 3 of 8 stress jobs, run 42114941995, after the suite's arenas moved further
     * apart). These tests count their ticks from the moment their units are ticking (see ArenaReady), so they measure
     * the code, not the server's chunk loading. True while a unit still has to tick; fails with where the units are
     * if they never do.
     */
    static boolean notTicking(GameTestHelper helper, Entity... units) {
        boolean ready = true;
        for (Entity e : units)
            if (e.isRemoved() || e.tickCount < ArenaReady.TICKS) {
                ready = false;
                break;
            }
        if (ready)
            return false;
        if (helper.getTick() >= READY_WAIT)
            throw new GameTestAssertException("the units were not ticking after " + READY_WAIT + " test ticks: "
                + ArenaReady.describe(helper.getLevel(), List.of(units)));
        return true;
    }

    /** Most test ticks a test waits for its units to start ticking (every test here has at least 60). */
    static final int READY_WAIT = 30;

    static String fmt(double d) {
        return String.format(Locale.ROOT, "%.2f", d);
    }

    // ------------------------------------------------------------------------------------------------ 1. which entity

    /**
     * A volley from a Rocket Battery is four rockets, three ticks apart, and every one is a RocketShell of the rocket
     * entity type - none a BombardShell.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void rocket_shell_battery_fires_rocket_shells_not_bombard_shells(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "type");
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        RocketBatteryUnit b = spawn(helper, EntityRegistrar.ROCKET_BATTERY_UNIT.get(), owner, 1.5, 8.5);
        if (b == null) {
            helper.fail("could not create the Rocket Battery");
            return;
        }
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        double tx = o.getX() + 14.5, ty = o.getY(), tz = o.getZ() + 8.5;
        Map<UUID, BombardShell> seen = new LinkedHashMap<>();
        int[] tick = { 0 };
        helper.onEachTick(() -> {
            if (notTicking(helper, b))
                return;
            tick[0]++;
            if (tick[0] == 3 && !b.fireVolley(tx, ty, tz)) {
                b.discard();
                throw new GameTestAssertException("the Rocket Battery refused a volley at a spot 13 blocks away");
            }
            if (tick[0] < 3)
                return;
            for (BombardShell s : shellsOf(b))
                seen.putIfAbsent(s.getUUID(), s);
            if (tick[0] < 3 + 3 * RocketLaunchPose.GAP + 8)
                return;
            List<String> problems = new ArrayList<>();
            if (seen.size() != 4)
                problems.add("a volley should be 4 rockets, the battery fired " + seen.size() + " shells");
            for (BombardShell s : seen.values()) {
                if (s.getClass() != RocketShell.class)
                    problems.add("a rocket was a " + s.getClass().getSimpleName() + ", not a RocketShell");
                if (s.getType() != EntityRegistrar.ROCKET_SHELL.get())
                    problems.add("a rocket was entity type " + s.getType().toShortString() + ", not rocket_shell");
            }
            discardAll(new ArrayList<>(seen.values()));
            b.discard();
            finish(helper, problems);
        });
    }

    // ---------------------------------------------------------------------------------------- 2. the others unchanged

    /**
     * Bombard Crew, Mortar Galley, Mortar Crawler and the siege lobber each fire a plain BombardShell (the class itself
     * and the bombard_shell entity type) with the damage and splash they had and the flight time they had - 20 ticks
     * plus the flat distance for the mortars (34 over 14 blocks), the lobber's fixed 40 - read from the speed the shell
     * leaves with, and a plain shell's burst still takes 0.8 of its damage off a unit a block from the centre. A guard
     * that passes before and after the rocket's split by design: the split must not move these.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = BATCH)
    public static void rocket_shell_other_artillery_still_fires_plain_bombard_shells(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "others"), foeOwner = owner + "_foe";
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        List<Entity> made = new ArrayList<>();
        var crew = ArtilleryGameTests.bombard(helper, owner, 1.5, 2.5);
        var galley = ArtilleryGameTests.galley(helper, owner, 1.5, 5.5);
        var crawler = spawn(helper, EntityRegistrar.MORTAR_CRAWLER_UNIT.get(), owner, 1.5, 8.5);
        var lobber = spawn(helper, EntityRegistrar.STEAM_LOBBER_UNIT.get(), owner, 1.5, 11.5);
        VillagerUnit foe = ArtilleryGameTests.foe(helper, foeOwner, 10.5, 14.5);
        for (Mob m : new Mob[] { crew, galley, crawler, lobber, foe })
            if (m != null) {
                made.add(m);
                m.setNoAi(true);
            }
        if (crew == null || galley == null || crawler == null || lobber == null || foe == null) {
            discardAll(made);
            helper.fail("could not create the four shell units and the target");
            return;
        }
        ArenaReady.whenTicking(helper, made, READY_WAIT, tickNow -> {
            List<String> problems = new ArrayList<>();
            try {
                double tx = o.getX() + 15.5, ty = o.getY();
                checkGun(problems, made, "Bombard Crew", crew, crew::fireShell, tx, ty, 34, 2.5f);
                checkGun(problems, made, "Mortar Galley", galley, galley::fireShell, tx, ty, 34, 2.5f);
                checkGun(problems, made, "Mortar Crawler", crawler, crawler::fireVolley, tx, ty, 34, 2.5f);
                checkGun(problems, made, "siege lobber", lobber, lobber::fireShell, tx, ty, SiegeTuning.FLIGHT_TICKS,
                    SiegeTuning.SPLASH_RADIUS);
                // the burst itself: full damage at the centre falls to 0.8 a block out (half at the 2.5 rim)
                float before = foe.getHealth();
                BombardShell plain = new BombardShell(level, crew, 9f);
                plain.setPos(foe.getX() + 1.0, foe.getY() + 0.5, foe.getZ());
                level.addFreshEntity(plain);
                made.add(plain);
                int hurt = plain.burst();
                float lost = before - foe.getHealth();
                if (hurt != 1 || Math.abs(lost - 9f * 0.8f) > 0.3f)
                    problems.add("a plain shell of 9 hurt " + hurt + " unit(s) and took " + fmt(lost) + " off one a block from its centre, expected 7.20");
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                discardAll(made);
            }
            finish(helper, problems);
        });
    }

    /** One gun fires at (tx, ty) along its own row; checks the shell it fired is a plain BombardShell with its old numbers. */
    static void checkGun(List<String> problems, List<Entity> made, String name, Mob unit, Fire fire, double tx, double ty,
                         int expectTicks, float expectSplash) {
        double tz = unit.getZ();
        if (!fire.at(tx, ty, tz)) {
            problems.add(name + " refused the shot");
            return;
        }
        List<BombardShell> shells = shellsOf(unit);
        made.addAll(shells);
        if (shells.isEmpty()) {
            problems.add(name + " fired no shell");
            return;
        }
        BombardShell s = shells.get(0);
        if (s.getClass() != BombardShell.class || s.getType() != EntityRegistrar.BOMBARD_SHELL.get())
            problems.add(name + " fired a " + s.getClass().getSimpleName() + " (" + s.getType().toShortString() + "), not a plain BombardShell");
        float damage = ((AttackerUnit) unit).getUnitAttackDamage();
        if (Math.abs(s.getDamage() - damage) > 1e-4f)
            problems.add(name + "'s shell carries " + s.getDamage() + " damage, the unit's is " + damage);
        if (Math.abs(s.getSplash() - expectSplash) > 1e-4f)
            problems.add(name + "'s shell splashes " + s.getSplash() + " blocks, expected " + expectSplash);
        // flight time from the launch speed: each tick moves the shell then scales the speed by 0.99, so a horizontal
        // speed v covers v * (1 - 0.99^t) / 0.01 in t ticks; solve for t over the distance to the aim
        double flat = Math.hypot(tx - s.getX(), tz - s.getZ());
        Vec3 v = s.getDeltaMovement();
        double vh = Math.hypot(v.x, v.z);
        double k = 1 - 0.01 * flat / vh;
        double t = k > 0 ? Math.log(k) / Math.log(0.99) : Double.NaN;
        if (!(Math.abs(t - expectTicks) < 0.6))
            problems.add(name + "'s shell flies " + fmt(t) + " ticks over " + fmt(flat) + " blocks, expected " + expectTicks);
    }

    // ------------------------------------------------------------------------------------------ 3. the stated damage

    /**
     * A rocket of the battery's own volley bursts on a unit a block from its centre for 5 x 0.8 = 4, and on a built
     * building for 5 x 1.5 x 2 = 15 building damage - exactly the blocks a BombardShell of the same damage takes off an
     * identical building next to it. The shared artillery rules (falloff, building multiple, the global building
     * multiplier) stay at one entry, so the balance does not fork.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = BATCH)
    public static void rocket_shell_hits_a_unit_and_a_building_for_the_stated_damage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "dmg"), foeOwner = owner + "_foe";
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        List<Entity> made = new ArrayList<>();
        List<BuildingPlacement> placed = new ArrayList<>();
        RocketBatteryUnit b = spawn(helper, EntityRegistrar.ROCKET_BATTERY_UNIT.get(), owner, 1.5, 13.5);
        VillagerUnit foe = ArtilleryGameTests.foe(helper, foeOwner, 10.5, 13.5);
        if (b != null)
            made.add(b);
        if (foe != null) {
            foe.setNoAi(true);
            made.add(foe);
        }
        BuildingPlacement rocketTarget = stand(level, helper, Buildings.ENERGY_CONVERTER_VILLAGERS, 3, 3, foeOwner);
        BuildingPlacement controlTarget = stand(level, helper, Buildings.ENERGY_CONVERTER_VILLAGERS, 10, 3, foeOwner);
        if (rocketTarget != null)
            placed.add(rocketTarget);
        if (controlTarget != null)
            placed.add(controlTarget);
        if (b == null || foe == null || rocketTarget == null || controlTarget == null) {
            cleanUp(level, made, placed);
            helper.fail("could not create the battery, the target and the two buildings");
            return;
        }
        ArenaReady.whenTicking(helper, made, READY_WAIT, tickNow -> {
            List<String> problems = new ArrayList<>();
            try {
                if (b.getUnitAttackDamage() != 5f)
                    problems.add("the battery's damage per rocket is " + b.getUnitAttackDamage() + ", the design says 5");
                // a unit a block from the burst's centre
                RocketShell onUnit = rocketFrom(b, level, o.getX() + 10.5, o.getY(), o.getZ() + 13.5, made, problems, "at the unit");
                if (onUnit != null) {
                    float before = foe.getHealth();
                    onUnit.setPos(foe.getX() + 1.0, foe.getY() + 0.5, foe.getZ());
                    onUnit.setDeltaMovement(Vec3.ZERO);
                    int hurt = onUnit.burst();
                    float lost = before - foe.getHealth();
                    if (hurt != 1 || Math.abs(lost - 4.0f) > 0.3f)
                        problems.add("a rocket hurt " + hurt + " unit(s) and took " + fmt(lost) + " off one a block from its centre, expected 4.00 (5 x 0.8)");
                }
                // a building, and the same hit from a plain shell on an identical one
                RocketShell onBuilding = rocketFrom(b, level, rocketTarget.centrePos.getX() + 0.5, o.getY(), rocketTarget.centrePos.getZ() + 0.5,
                    made, problems, "at the building");
                if (onBuilding != null) {
                    int rocketLost = burstOn(level, onBuilding, rocketTarget);
                    BombardShell plain = new BombardShell(level, b, onBuilding.getDamage());
                    level.addFreshEntity(plain);
                    made.add(plain);
                    int plainLost = burstOn(level, plain, controlTarget);
                    double blockHealth = rocketTarget.getHealthPerBlock() / 2d;
                    double expected = com.solegendary.reignofnether.building.BuildingDamage.unit(5f * BombardShell.BUILDING_DAMAGE_MULT) / blockHealth;
                    if (rocketLost < Math.floor(expected) || rocketLost > Math.floor(expected) + 1)
                        problems.add("a rocket took " + rocketLost + " blocks off a building, expected " + fmt(expected)
                            + " (5 x 1.5 x 2 = 15 damage at " + fmt(blockHealth) + " a block)");
                    if (Math.abs(rocketLost - plainLost) > 1)
                        problems.add("a rocket took " + rocketLost + " blocks off a building, a plain shell of the same damage took " + plainLost);
                    if (rocketLost < 1)
                        problems.add("the rocket dented nothing - the test proves nothing");
                }
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                cleanUp(level, made, placed);
            }
            finish(helper, problems);
        });
    }

    /** The battery fires one rocket at (x, y, z) and the RocketShell it made, or null (with the reason in problems). */
    static RocketShell rocketFrom(RocketBatteryUnit b, ServerLevel level, double x, double y, double z, List<Entity> made,
                                  List<String> problems, String what) {
        b.fireRocket(x, y, z, true);
        RocketShell found = null;
        for (BombardShell s : shellsOf(b)) {
            if (!made.contains(s))
                made.add(s);
            if (s instanceof RocketShell r && found == null)
                found = r;
        }
        if (found == null)
            problems.add("the battery's rocket " + what + " was not a RocketShell");
        return found;
    }

    /** A built, standing placement (the raid tests' own recipe) in the arena: its blocks in the world, in the building list. */
    static BuildingPlacement stand(ServerLevel level, GameTestHelper helper, Building b, int x, int z, String owner) {
        BlockPos origin = helper.absolutePos(new BlockPos(x, 1, z));
        BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(b, level, origin, Rotation.NONE, owner, false);
        if (bp == null)
            return null;
        for (BuildingBlock bb : bp.getBlocks())
            if (!bb.getBlockState().isAir())
                level.setBlock(bb.getBlockPos(), bb.getBlockState(), 2);
        bp.initPlacedBlocks();
        bp.isBuilt = true;
        BuildingServerEvents.getBuildings().add(bp);
        return bp;
    }

    static int blocksStanding(ServerLevel level, BuildingPlacement bp) {
        int n = 0;
        for (BuildingBlock bb : bp.getBlocks())
            if (bb.isPlaced(level))
                n++;
        return n;
    }

    /** Bursts the shell inside the building and returns how many of its blocks that took out of the world. */
    static int burstOn(ServerLevel level, BombardShell shell, BuildingPlacement bp) {
        int before = blocksStanding(level, bp);
        shell.setPos(bp.minCorner.getX() + 1.0, bp.minCorner.getY() + 1.0, bp.minCorner.getZ() + 1.0);
        shell.setDeltaMovement(Vec3.ZERO);
        shell.burst();
        return before - blocksStanding(level, bp);
    }

    static void cleanUp(ServerLevel level, List<Entity> made, List<BuildingPlacement> placed) {
        discardAll(made);
        for (BuildingPlacement bp : placed)
            if (BuildingServerEvents.getBuildings().remove(bp))
                bp.forceChunk(false);
    }

    // ------------------------------------------------------------------------------------------ 4. how it flies

    /**
     * A rocket and a Bombard Crew's shell aimed at the same distance on the same ground: the rocket is in the air less
     * than two thirds as long, peaks lower than 0.7 of the mortar's arc yet still arcs (a boost, not a laser), and lands
     * within two blocks of the aim. The mortar's flight (20 + 13 = 33 ticks) is the control in the same run.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void rocket_shell_flies_faster_and_flatter_than_a_bombard_shell_to_the_same_spot(GameTestHelper helper) {
        String owner = owner(helper, "flight");
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        List<Entity> made = new ArrayList<>();
        RocketBatteryUnit b = spawn(helper, EntityRegistrar.ROCKET_BATTERY_UNIT.get(), owner, 1.5, 4.5);
        var crew = ArtilleryGameTests.bombard(helper, owner, 1.5, 11.5);
        if (crew != null) {
            crew.setNoAi(true);
            made.add(crew);
        }
        if (b != null)
            made.add(b);
        if (b == null || crew == null) {
            discardAll(made);
            helper.fail("could not create the battery and the crew");
            return;
        }
        double tx = o.getX() + 14.5, ty = o.getY();
        BombardShell[] shell = new BombardShell[2];   // 0 rocket, 1 mortar
        double[] apex = { -1e9, -1e9 }, lastX = new double[2], lastY = new double[2], lastZ = new double[2];
        int[] ended = { -1, -1 };
        double[] aimZ = { b.getZ(), crew.getZ() };
        int[] tick = { 0 };
        helper.onEachTick(() -> {
            if (notTicking(helper, b, crew))
                return;
            tick[0]++;
            if (tick[0] == 3) {
                b.fireRocket(tx, ty, aimZ[0], true);
                crew.fireShell(tx, ty, aimZ[1]);
                List<BombardShell> r = shellsOf(b), m = shellsOf(crew);
                made.addAll(r);
                made.addAll(m);
                shell[0] = r.isEmpty() ? null : r.get(0);
                shell[1] = m.isEmpty() ? null : m.get(0);
                if (shell[0] == null || shell[1] == null) {
                    discardAll(made);
                    throw new GameTestAssertException("a shell was not fired: rocket " + (shell[0] != null) + ", mortar " + (shell[1] != null));
                }
            }
            if (tick[0] < 3)
                return;
            for (int i = 0; i < 2; i++) {
                if (ended[i] >= 0)
                    continue;
                if (shell[i].isRemoved() || !shell[i].isAlive()) {
                    ended[i] = tick[0] - 3;
                    continue;
                }
                apex[i] = Math.max(apex[i], shell[i].getY());
                lastX[i] = shell[i].getX();
                lastY[i] = shell[i].getY();
                lastZ[i] = shell[i].getZ();
            }
            if (ended[0] < 0 || ended[1] < 0) {
                if (tick[0] > 80) {
                    discardAll(made);
                    throw new GameTestAssertException("a shell was still flying after 77 ticks: rocket " + ended[0] + ", mortar " + ended[1]);
                }
                return;
            }
            List<String> problems = new ArrayList<>();
            double rocketApex = apex[0] - ty, mortarApex = apex[1] - ty;
            if (shell[0].getClass() != RocketShell.class)
                problems.add("the battery's rocket is a " + shell[0].getClass().getSimpleName());
            if (Math.abs(ended[1] - 33) > 3)
                problems.add("the mortar shell (the control) flew " + ended[1] + " ticks over 13 blocks, expected about 33");
            if (Math.abs(ended[0] - 18) > 3)
                problems.add("the rocket flew " + ended[0] + " ticks over 13 blocks, expected about 18");
            if (ended[0] > ended[1] * 0.66)
                problems.add("the rocket flew " + ended[0] + " ticks, the mortar shell " + ended[1] + ": not 2/3 or less");
            if (!(rocketApex < mortarApex * 0.7))
                problems.add("the rocket peaked " + fmt(rocketApex) + " above the ground, the mortar shell " + fmt(mortarApex) + ": not flatter by 30%");
            if (!(rocketApex > 0.8))
                problems.add("the rocket peaked only " + fmt(rocketApex) + " above the ground: a line, not a boost arc");
            double miss = Math.hypot(lastX[0] - tx, lastZ[0] - aimZ[0]);
            if (miss > 2.0)
                problems.add("the rocket's last position was " + fmt(miss) + " blocks off the aim (y " + fmt(lastY[0] - ty) + " above the ground)");
            discardAll(made);
            finish(helper, problems);
        });
    }

    // ------------------------------------------------------------------------------------------ 5. the launch animation

    /**
     * Each volley starts the launch animation (its ticks left start at the full length), it runs down as the ticks pass,
     * it is gone when the length has gone by, and a volley the battery refuses (inside its dead zone) starts nothing.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void rocket_shell_battery_runs_its_launch_animation_for_each_volley(GameTestHelper helper) {
        String owner = owner(helper, "anim");
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        BlockPos o = helper.absolutePos(new BlockPos(0, 2, 0));
        RocketBatteryUnit b = spawn(helper, EntityRegistrar.ROCKET_BATTERY_UNIT.get(), owner, 1.5, 8.5);
        if (b == null) {
            helper.fail("could not create the Rocket Battery");
            return;
        }
        double ty = o.getY();
        List<String> problems = new ArrayList<>();
        int[] tick = { 0 };
        int[] launchedAt = { -1 };
        helper.onEachTick(() -> {
            if (notTicking(helper, b))
                return;
            tick[0]++;
            int total = RocketLaunchPose.TOTAL_TICKS;
            if (tick[0] == 3) {
                if (b.fireVolley(o.getX() + 4.5, ty, o.getZ() + 8.5))
                    problems.add("the battery fired a volley 3 blocks away, inside its dead zone");
                if (b.getLaunchTicks() != 0)
                    problems.add("a refused volley started the launch animation (" + b.getLaunchTicks() + " ticks left)");
            }
            if (tick[0] == 5) {
                if (!b.fireVolley(o.getX() + 14.5, ty, o.getZ() + 8.5))
                    problems.add("the battery refused a volley 13 blocks away");
                launchedAt[0] = tick[0];
                if (b.getLaunchTicks() != total)
                    problems.add("a volley left " + b.getLaunchTicks() + " animation ticks, expected the full " + total);
            }
            if (launchedAt[0] > 0 && tick[0] == launchedAt[0] + 6) {
                int left = b.getLaunchTicks();
                if (Math.abs(left - (total - 6)) > 1)
                    problems.add("6 ticks into a volley the animation had " + left + " ticks left, expected " + (total - 6));
            }
            if (launchedAt[0] > 0 && tick[0] == launchedAt[0] + total + 3) {
                if (b.getLaunchTicks() != 0)
                    problems.add("the launch animation was still running " + (total + 3) + " ticks after the volley: " + b.getLaunchTicks());
                discardAll(new ArrayList<>(shellsOf(b)));
                b.discard();
                finish(helper, problems);
            }
        });
    }
}
