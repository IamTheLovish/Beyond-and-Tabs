package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.BuildingValidators;
import com.solegendary.reignofnether.building.Blueprints;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.defence.Defences;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.network.Audience;
import com.solegendary.reignofnether.network.Audience.Policy;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Blueprints (building/Blueprints; lovish, Oct 22): a building ordered from a worker is only an order and a ghost until
 * a worker reaches the site, and an enemy is never sent the ghost. Pinned down here:
 * <ul>
 *   <li>nothing in the world changes, nothing is registered and nothing is paid while no worker is there;</li>
 *   <li>a pending footprint does not block walking, and an enemy may build on it;</li>
 *   <li>construction starts when the worker arrives;</li>
 *   <li>a site that became invalid by then is cancelled, one with enemy units on it waits and then gives up;</li>
 *   <li>cancelling a never-started blueprint costs and refunds nothing, and one nobody holds any more goes by itself;</li>
 *   <li>the ghost is sent to the owner's side and spectators only (the packet-level proof: the policy each send used,
 *       and who that policy reaches), and the owner's side keeps its own footprints free;</li>
 *   <li>a bot that has ordered its capitol does not order a second.</li>
 * </ul>
 * Their own holder next to the others (same arena template): a stone pad in the sky over each test's own arena, unique
 * owners, and everything - blueprints, buildings, units, pools, the pad - undone in the same callback. The workers
 * that must not move yet are frozen (NoAI) until the test lets them go.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BlueprintGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final int PAD_Y = 200, PAD_R = 10, PAD_H = 12;
    static final String OVERLAP = "building.reignofnether.overlaps_blueprint";

    /** One test's world: the pad, the owner and the foe, the units and pools to undo, and the blueprint once ordered. */
    static final class Scene {
        final ServerLevel level;
        final String owner, foe;
        final int cx, cz;
        final List<Entity> made = new ArrayList<>();
        final List<Resources> pools = new ArrayList<>();
        final List<BuildingPlacement> registered = new ArrayList<>();
        final Resources pool;
        Mob worker;
        BuildingPlacement bp;
        BlockPos lo, hi;
        List<BlockState> before;

        Scene(GameTestHelper helper, String tag) {
            level = helper.getLevel();
            owner = "gametest_bp_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
            foe = owner + "_foe";
            BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
            cx = base.getX();
            cz = base.getZ();
            CombatEngineerGameTests.skyPad(level, cx, PAD_Y, cz, PAD_R, PAD_H, false);
            pool = new Resources(owner, 0, 2000, 2000);
            ResourcesServerEvents.resourcesList.add(pool);
            pools.add(pool);
        }

        BlockPos origin() {
            return new BlockPos(cx - 1, PAD_Y, cz - 1);
        }

        BlockPos near() {
            return new BlockPos(cx - 4, PAD_Y + 1, cz);
        }

        BlockPos far() {
            return new BlockPos(cx - 8, PAD_Y + 1, cz - 8);
        }

        /** The placement a villager worker is ordered to raise: its faction's anti-raid tower, via the real packet path. */
        boolean order(BlockPos workerAt, boolean frozen) {
            BuildingPlacement probe = BuildingUtils.getNewBuildingPlacement(Defences.VILLAGERS.raidTower(), level, origin(),
                Rotation.NONE, owner, false);
            if (probe == null)
                return false;
            lo = probe.minCorner.offset(-1, -2, -1);
            hi = probe.maxCorner.offset(1, 1, 1);
            before = snapshot();
            worker = CombatEngineerGameTests.spawn(level, EntityRegistrar.VILLAGER_UNIT.get(), owner, workerAt, made);
            if (worker == null)
                return false;
            worker.setNoAi(frozen);
            bp = BuildingServerEvents.placeBuilding(Defences.VILLAGERS.raidTower(), origin(), Rotation.NONE, owner,
                new int[] { worker.getId() }, false, false, false, true);
            return bp != null;
        }

        List<BlockState> snapshot() {
            List<BlockState> out = new ArrayList<>();
            for (int x = lo.getX(); x <= hi.getX(); x++)
                for (int y = lo.getY(); y <= hi.getY(); y++)
                    for (int z = lo.getZ(); z <= hi.getZ(); z++)
                        out.add(level.getBlockState(new BlockPos(x, y, z)));
            return out;
        }

        /** How many blocks in and round the footprint differ from the world as it was before the order. */
        int changedBlocks() {
            List<BlockState> now = snapshot();
            int n = 0;
            for (int i = 0; i < now.size(); i++)
                if (now.get(i) != before.get(i))
                    n++;
            return n;
        }

        /** Registers a foe building at once (command-placed: no worker) and remembers it for the undo. */
        BuildingPlacement foeBuilds(BlockPos at) {
            BuildingPlacement f = BuildingServerEvents.placeBuilding(Defences.PIGLINS.raidTower(), at, Rotation.NONE, foe,
                new int[0], false, false, true, true);
            if (f != null)
                registered.add(f);
            return f;
        }

        boolean isRegistered(BuildingPlacement b) {
            return b != null && BuildingServerEvents.getBuildings().contains(b);
        }

        void undo() {
            if (bp != null) {
                Blueprints.cancel(bp, null);
                if (BuildingServerEvents.getBuildings().remove(bp))
                    bp.forceChunk(false);
            }
            for (BuildingPlacement b : registered)
                if (BuildingServerEvents.getBuildings().remove(b))
                    b.forceChunk(false);
            CombatEngineerGameTests.cleanUp(made, pools, List.of(owner, foe));
            CombatEngineerGameTests.skyPad(level, cx, PAD_Y, cz, PAD_R, PAD_H, true);
        }
    }

    static void finish(GameTestHelper helper, Scene s, List<String> problems) {
        try {
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        } finally {
            s.undo();
        }
    }

    /** Checks every 5 ticks (for up to {@code max}) until {@code done}, then runs {@code then}; {@code timeout} if it never was. */
    static void poll(GameTestHelper helper, int waited, int max, BooleanSupplier done, Runnable then, Runnable timeout) {
        helper.runAfterDelay(5, () -> {
            if (done.getAsBoolean())
                then.run();
            else if (waited + 5 >= max)
                timeout.run();
            else
                poll(helper, waited + 5, max, done, then, timeout);
        });
    }

    static void expectUntouched(Scene s, List<String> problems, String when) {
        BuildingPlacement bp = s.bp;
        if (!Blueprints.isPending(bp))
            problems.add(when + ": the blueprint is no longer pending");
        if (s.isRegistered(bp))
            problems.add(when + ": the blueprint is already a registered building (blocks pathing, seen by the enemy)");
        if (BuildingUtils.isPosInsideAnyBuilding(false, bp.centrePos) || BuildingUtils.isPosInsideAnyBuilding(false, bp.minCorner))
            problems.add(when + ": the footprint is inside the building grid");
        int changed = s.changedBlocks();
        if (changed != 0)
            problems.add(when + ": " + changed + " blocks in and round the footprint changed before any worker arrived");
        if (bp.getBlocksPlaced() != 0 || bp.buildProgress != 0)
            problems.add(when + ": construction progressed with no worker (" + bp.getBlocksPlaced() + " blocks, "
                + bp.buildProgress + ")");
        if (s.pool.getMetal() != 2000 || s.pool.getEnergy() != 2000)
            problems.add(when + ": the pool was charged (" + s.pool.getMetal() + " metal, " + s.pool.getEnergy() + " energy)");
    }

    // ------------------------------------------------------------------------------------------ before the worker

    /**
     * The order is placed with a worker on its way (frozen 15 blocks off, so it cannot arrive): the placement is
     * accepted and handed to the worker, but nothing is registered, no block of the site or under it changes, no block
     * is queued or placed and nothing is paid - right away and 2.5 s later, past the sweep that drops unheld orders.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void blueprint_leaves_the_world_alone_until_a_worker_arrives(GameTestHelper helper) {
        Scene s = new Scene(helper, "world");
        List<String> problems = new ArrayList<>();
        if (!s.order(s.far(), true)) {
            s.undo();
            helper.fail("the order was refused");
            return;
        }
        if (((WorkerUnit) s.worker).getBuildRepairGoal().getBuildingTarget() != s.bp)
            problems.add("the worker was not sent to the blueprint");
        expectUntouched(s, problems, "at once");
        helper.runAfterDelay(50, () -> {
            expectUntouched(s, problems, "after 2.5 s");
            finish(helper, s, problems);
        });
    }

    /**
     * A pending footprint is not in the building grid, so it neither pushes units out nor blocks them: an enemy unit
     * walks from one side of it to the other in a straight line (the order's worker is frozen far away, so the site
     * is still a ghost the whole way), and the enemy may build on the same ground.
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void a_unit_walks_through_a_pending_footprint(GameTestHelper helper) {
        Scene s = new Scene(helper, "walk");
        List<String> problems = new ArrayList<>();
        if (!s.order(s.far(), true)) {
            s.undo();
            helper.fail("the order was refused");
            return;
        }
        int midZ = (s.bp.minCorner.getZ() + s.bp.maxCorner.getZ()) / 2;
        BlockPos from = new BlockPos(s.bp.minCorner.getX() - 3, PAD_Y + 1, midZ);
        BlockPos to = new BlockPos(s.bp.maxCorner.getX() + 3, PAD_Y + 1, midZ);
        if (Math.abs(to.getX() - s.cx) > PAD_R || Math.abs(from.getX() - s.cx) > PAD_R) {
            s.undo();
            helper.fail("(test setup) the tower is wider than the pad: " + s.bp.minCorner.toShortString() + " .. " + s.bp.maxCorner.toShortString());
            return;
        }
        Mob walker = CombatEngineerGameTests.spawn(s.level, EntityRegistrar.VILLAGER_UNIT.get(), s.foe, from, s.made);
        if (walker == null) {
            s.undo();
            helper.fail("could not create the walker");
            return;
        }
        for (BlockPos p : List.of(s.bp.minCorner, s.bp.maxCorner, s.bp.centrePos, s.bp.originPos))
            if (BuildingUtils.isPosInsideAnyBuilding(false, p))
                problems.add(p.toShortString() + " of the pending footprint counts as inside a building");
        ((Unit) walker).setMoveTarget(to);
        poll(helper, 0, 260,
            () -> walker.blockPosition().getX() >= to.getX() - 1 && Math.abs(walker.blockPosition().getZ() - midZ) <= 3,
            () -> {
                if (!Blueprints.isPending(s.bp))
                    problems.add("the blueprint stopped being pending while the unit crossed it");
                // and the ground is free for the enemy to claim: an overlapping placement of theirs is not refused for it
                String err = BuildingValidators.getPlacementValidityError(s.level, Defences.PIGLINS.raidTower(),
                    s.origin().offset(1, 0, 1), s.foe, Rotation.NONE, false, false, true);
                if (OVERLAP.equals(err))
                    problems.add("an enemy was refused the ground under a ghost");
                finish(helper, s, problems);
            },
            () -> {
                problems.add("the unit never crossed the pending footprint: at " + walker.blockPosition().toShortString()
                    + ", wanted " + to.toShortString());
                finish(helper, s, problems);
            });
    }

    // ------------------------------------------------------------------------------------------ the worker arrives

    /**
     * With the worker three blocks off it, the order stays a ghost only until it arrives: then the site is registered
     * (an ordinary building from there), is no longer pending and its construction advances (blocks placed or paid
     * progress).
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void construction_starts_when_the_worker_arrives(GameTestHelper helper) {
        Scene s = new Scene(helper, "arrive");
        List<String> problems = new ArrayList<>();
        if (!s.order(s.near(), false)) {
            s.undo();
            helper.fail("the order was refused");
            return;
        }
        if (s.isRegistered(s.bp) || !Blueprints.isPending(s.bp))
            problems.add("the order was registered at once instead of waiting for its worker");
        poll(helper, 0, 250,
            () -> s.isRegistered(s.bp) && (s.bp.getBlocksPlaced() > 0 || s.bp.buildProgress > 0),
            () -> {
                if (Blueprints.isPending(s.bp))
                    problems.add("the started site is still listed as a blueprint");
                if (Blueprints.all().contains(s.bp))
                    problems.add("the started site is still in the blueprint list");
                finish(helper, s, problems);
            },
            () -> {
                problems.add("construction never started: registered " + s.isRegistered(s.bp) + ", pending "
                    + Blueprints.isPending(s.bp) + ", blocks " + s.bp.getBlocksPlaced() + ", progress " + s.bp.buildProgress
                    + ", worker at " + s.worker.blockPosition().toShortString());
                finish(helper, s, problems);
            });
    }

    /**
     * An enemy builds on the ground while the worker is still on its way (command-placed, which the ghost does not
     * stop): when the worker is let go and arrives, the site is checked again and is no longer valid - the blueprint is
     * cancelled, nothing is built, nothing was paid, and the worker is off the dead order.
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void a_site_that_became_invalid_is_cancelled_on_arrival(GameTestHelper helper) {
        Scene s = new Scene(helper, "blocked");
        List<String> problems = new ArrayList<>();
        if (!s.order(s.near(), true)) {
            s.undo();
            helper.fail("the order was refused");
            return;
        }
        BuildingPlacement foeBuilding = s.foeBuilds(s.origin().offset(1, 0, 1));
        if (foeBuilding == null)
            problems.add("the enemy could not build on a ghost's ground");
        s.worker.setNoAi(false);
        poll(helper, 0, 250,
            () -> !Blueprints.isPending(s.bp),
            () -> {
                if (s.isRegistered(s.bp))
                    problems.add("a blueprint on ground the enemy had taken was built anyway");
                if (s.bp.getBlocksPlaced() != 0 || s.bp.buildProgress != 0)
                    problems.add("the cancelled blueprint progressed");
                if (s.pool.getMetal() != 2000 || s.pool.getEnergy() != 2000)
                    problems.add("a cancelled blueprint was paid for: " + s.pool.getMetal() + " metal, " + s.pool.getEnergy() + " energy");
                helper.runAfterDelay(20, () -> {
                    if (((WorkerUnit) s.worker).getBuildRepairGoal().getBuildingTarget() == s.bp)
                        problems.add("the worker is still on the cancelled order");
                    finish(helper, s, problems);
                });
            },
            () -> {
                problems.add("the blueprint on taken ground was neither built nor cancelled");
                finish(helper, s, problems);
            });
    }

    /**
     * Enemy ground units standing on the site hold the blueprint (it does not rise under them and is not cancelled
     * yet); once they leave it is built.
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void enemy_units_on_the_site_make_the_worker_wait(GameTestHelper helper) {
        Scene s = new Scene(helper, "wait");
        List<String> problems = new ArrayList<>();
        if (!s.order(s.near(), false)) {
            s.undo();
            helper.fail("the order was refused");
            return;
        }
        Mob squatter = CombatEngineerGameTests.spawn(s.level, EntityRegistrar.VILLAGER_UNIT.get(), s.foe,
            new BlockPos(s.bp.centrePos.getX(), PAD_Y + 1, s.bp.centrePos.getZ()), s.made);
        if (squatter == null) {
            s.undo();
            helper.fail("could not create the enemy unit");
            return;
        }
        helper.runAfterDelay(100, () -> {
            if (!Blueprints.isPending(s.bp) || s.isRegistered(s.bp))
                problems.add("the site rose (or was dropped) with an enemy unit standing on it");
            squatter.discard();
            poll(helper, 0, 150,
                () -> s.isRegistered(s.bp),
                () -> finish(helper, s, problems),
                () -> {
                    problems.add("the blueprint never rose after the enemy unit left");
                    finish(helper, s, problems);
                });
        });
    }

    /** An enemy that never leaves does not hold a site forever: after the wait (10 s) it is cancelled, unpaid and unbuilt. */
    @GameTest(template = ARENA, timeoutTicks = 450)
    public static void an_occupied_site_is_given_up_after_a_wait(GameTestHelper helper) {
        Scene s = new Scene(helper, "giveup");
        List<String> problems = new ArrayList<>();
        if (!s.order(s.near(), false)) {
            s.undo();
            helper.fail("the order was refused");
            return;
        }
        Mob squatter = CombatEngineerGameTests.spawn(s.level, EntityRegistrar.VILLAGER_UNIT.get(), s.foe,
            new BlockPos(s.bp.centrePos.getX(), PAD_Y + 1, s.bp.centrePos.getZ()), s.made);
        if (squatter == null) {
            s.undo();
            helper.fail("could not create the enemy unit");
            return;
        }
        helper.runAfterDelay(100, () -> {
            if (!Blueprints.isPending(s.bp))
                problems.add("the blueprint was dropped before the wait was over");
            poll(helper, 0, 320,
                () -> !Blueprints.isPending(s.bp),
                () -> {
                    if (s.isRegistered(s.bp))
                        problems.add("the site rose under the enemy unit");
                    if (s.pool.getMetal() != 2000 || s.pool.getEnergy() != 2000)
                        problems.add("a cancelled blueprint was paid for");
                    finish(helper, s, problems);
                },
                () -> {
                    problems.add("an occupied blueprint was never given up");
                    finish(helper, s, problems);
                });
        });
    }

    // ---------------------------------------------------------------------------------------------- cancelling

    /**
     * Cancelling a blueprint nobody started owes and refunds nothing: the pool is exactly as before the order, the
     * world is untouched, the ghost is gone from the list and the (frozen, then released) worker drops the dead order.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void cancelling_a_pending_blueprint_is_free(GameTestHelper helper) {
        Scene s = new Scene(helper, "cancel");
        List<String> problems = new ArrayList<>();
        if (!s.order(s.far(), true)) {
            s.undo();
            helper.fail("the order was refused");
            return;
        }
        expectUntouched(s, problems, "before the cancel");
        BuildingServerEvents.cancelBuilding(s.bp, s.owner);
        if (Blueprints.isPending(s.bp) || Blueprints.all().contains(s.bp))
            problems.add("the cancelled blueprint is still pending");
        if (s.isRegistered(s.bp))
            problems.add("cancelling registered the blueprint");
        if (s.changedBlocks() != 0)
            problems.add("cancelling changed the world");
        if (s.pool.getMetal() != 2000 || s.pool.getEnergy() != 2000)
            problems.add("cancelling a never-started blueprint changed the pool: " + s.pool.getMetal() + " metal, "
                + s.pool.getEnergy() + " energy (it must owe and refund nothing)");
        if (BuildingUtils.isBuildingBuildable(false, s.bp))
            problems.add("a cancelled blueprint is still buildable");
        s.worker.setNoAi(false);
        poll(helper, 0, 40,
            () -> ((WorkerUnit) s.worker).getBuildRepairGoal().getBuildingTarget() == null,
            () -> finish(helper, s, problems),
            () -> {
                problems.add("the worker never dropped the cancelled order");
                finish(helper, s, problems);
            });
    }

    /**
     * A blueprint lives while some worker holds it: when its only worker dies it goes with it (nothing built or paid to
     * undo); while a second worker holds it, the first dying changes nothing; when that one is given other orders it goes.
     */
    @GameTest(template = ARENA, timeoutTicks = 260)
    public static void a_blueprint_goes_when_its_last_worker_does(GameTestHelper helper) {
        Scene s = new Scene(helper, "orphan");
        List<String> problems = new ArrayList<>();
        if (!s.order(s.far(), true)) {
            s.undo();
            helper.fail("the order was refused");
            return;
        }
        Mob second = CombatEngineerGameTests.spawn(s.level, EntityRegistrar.VILLAGER_UNIT.get(), s.owner,
            new BlockPos(s.cx - 8, PAD_Y + 1, s.cz - 6), s.made);
        if (second == null) {
            s.undo();
            helper.fail("could not create the second worker");
            return;
        }
        second.setNoAi(true);
        ((WorkerUnit) second).getBuildRepairGoal().setBuildingTarget(s.bp);
        if (((WorkerUnit) second).getBuildRepairGoal().getBuildingTarget() != s.bp)
            problems.add("(test setup) the second worker could not be put on the blueprint");
        s.worker.discard();   // the first worker dies
        helper.runAfterDelay(100, () -> {
            if (!Blueprints.isPending(s.bp))
                problems.add("the blueprint went while a second worker still held it");
            // given another order, the second worker lets go
            Unit.fullResetBehaviours((Unit) second);
            ((WorkerUnit) second).getBuildRepairGoal().stopBuilding();
            poll(helper, 0, 80,
                () -> !Blueprints.isPending(s.bp),
                () -> finish(helper, s, problems),
                () -> {
                    problems.add("the blueprint outlived every worker that held it");
                    finish(helper, s, problems);
                });
        });
    }

    // ---------------------------------------------------------------------------------------------- who sees it

    /**
     * The ghost goes out through Audience.team only: ordering sends the ghost under the TEAM policy for the
     * owner, and no building-sync under the SEEN policy at the site (the fog-filtered route that used to tell every
     * player whose fog showed a corner); cancelling sends one TEAM remove. And TEAM reaches the owner, their ally and a
     * spectator but not an enemy - not even one whose fog shows the spot, which is who the SEEN policy would reach.
     * The enemy's client is never sent the data, rather than sent and hidden at render time.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void an_enemy_client_is_never_sent_a_blueprint(GameTestHelper helper) {
        Scene s = new Scene(helper, "audience");
        List<String> problems = new ArrayList<>();
        List<Object[]> sends = new ArrayList<>();
        boolean ordered;
        Audience.accountant = (policy, owner, positional, x, z) -> sends.add(new Object[] { policy, owner, positional, x, z });
        try {
            ordered = s.order(s.far(), true);
        } finally {
            Audience.accountant = null;
        }
        if (!ordered) {
            s.undo();
            helper.fail("the order was refused");
            return;
        }
        int team = 0, leaks = 0;
        for (Object[] r : sends) {
            if (!s.owner.equals(r[1]))
                continue;
            Policy policy = (Policy) r[0];
            if (policy == Policy.TEAM)
                team++;
            boolean atSite = (boolean) r[2] && (int) r[3] == s.bp.centrePos.getX() && (int) r[4] == s.bp.centrePos.getZ();
            if (atSite && policy != Policy.TEAM)
                leaks++;
        }
        if (team < 1)
            problems.add("ordering sent the owner's side no TEAM packet: nobody was shown the ghost");
        if (leaks != 0)
            problems.add("ordering sent " + leaks + " packets about the site under a policy that can reach an enemy");

        sends.clear();
        Audience.accountant = (policy, owner, positional, x, z) -> sends.add(new Object[] { policy, owner, positional, x, z });
        try {
            Blueprints.cancel(s.bp, null);
        } finally {
            Audience.accountant = null;
        }
        if (sends.size() != 1 || sends.get(0)[0] != Policy.TEAM || !s.owner.equals(sends.get(0)[1]))
            problems.add("cancelling should send one TEAM remove for the owner, sent " + sends.size());

        // who TEAM reaches: a1 + ally a2 + spectator, against b1, whose fog shows the spot
        AudienceGameTests.FakeFacts f = new AudienceGameTests.FakeFacts();
        f.side.put("a1", 0);
        f.side.put("a2", 0);
        f.side.put("b1", 1);
        f.spectators.add("spec");
        List<String> online = List.of("a1", "a2", "b1", "spec");
        Set<String> teamGets = Audience.recipients(Policy.TEAM, "a1", online, f, p -> true);
        if (teamGets.contains("b1"))
            problems.add("an enemy whose fog shows the site is in the ghost's audience: " + teamGets);
        if (!teamGets.containsAll(List.of("a1", "a2", "spec")))
            problems.add("the owner, an ally or a spectator is missing from the ghost's audience: " + teamGets);
        if (!Audience.recipients(Policy.SEEN, "a1", online, f, p -> true).contains("b1"))
            problems.add("(control) the SEEN policy no longer reaches an enemy that sees the spot");
        finish(helper, s, problems);
    }

    /**
     * A side's own pending sites keep their ground for each other: the owner (and an ally) placing over a ghost is
     * refused with the blueprint message, an enemy is not, and the ground is free again once the ghost is cancelled.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void a_sides_own_blueprints_reserve_their_footprint(GameTestHelper helper) {
        Scene s = new Scene(helper, "reserve");
        List<String> problems = new ArrayList<>();
        if (!s.order(s.far(), true)) {
            s.undo();
            helper.fail("the order was refused");
            return;
        }
        String ally = s.owner + "_ally";
        BlockPos over = s.origin().offset(1, 0, 1);
        var tower = Defences.VILLAGERS.raidTower();
        String own = BuildingValidators.getPlacementValidityError(s.level, tower, over, s.owner, Rotation.NONE, false, false, true);
        if (!OVERLAP.equals(err(own)))
            problems.add("the owner placing over their own ghost got " + own);
        AlliancesServerEvents.addAlliance(s.owner, ally);
        try {
            String allied = BuildingValidators.getPlacementValidityError(s.level, tower, over, ally, Rotation.NONE, false, false, true);
            if (!OVERLAP.equals(err(allied)))
                problems.add("an ally placing over the ghost got " + allied);
        } finally {
            AlliancesServerEvents.removeAlliance(s.owner, ally);
        }
        String enemy = BuildingValidators.getPlacementValidityError(s.level, tower, over, s.foe, Rotation.NONE, false, false, true);
        if (OVERLAP.equals(err(enemy)))
            problems.add("an enemy was held off by a ghost they cannot see");
        Blueprints.cancel(s.bp, null);
        String after = BuildingValidators.getPlacementValidityError(s.level, tower, over, s.owner, Rotation.NONE, false, false, true);
        if (OVERLAP.equals(err(after)))
            problems.add("a cancelled ghost still reserves its ground");
        finish(helper, s, problems);
    }

    private static String err(String e) {
        return e == null ? "" : e;
    }

    // ------------------------------------------------------------------------------------------------------ bots

    /**
     * A Tidewrought bot whose workers cannot reach the site yet (frozen) has ordered its Wreck Harbour as a blueprint:
     * it counts that order as its capitol, so three more thinks order no second one and register nothing. (The bots
     * still raise their bases through the ordinary skirmish tests, which now go through the same blueprint path.)
     */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void a_bot_does_not_order_a_second_capitol_while_its_first_is_a_blueprint(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_bp_bot_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlockPos centre = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 250;
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++) {
                level.setBlock(new BlockPos(centre.getX() + dx, y - 1, centre.getZ() + dz), Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(new BlockPos(centre.getX() + dx, y, centre.getZ() + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        BlockPos home = new BlockPos(centre.getX() - 5, y, centre.getZ() - 5);
        Resources pool = new Resources(owner, 0, 2000, 2000);
        ResourcesServerEvents.resourcesList.add(pool);
        List<Entity> workers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Mob w = CombatEngineerGameTests.spawn(level, EntityRegistrar.SHIPWRIGHT_UNIT.get(), owner,
                home.offset(-2 + i, 1, -2), workers);
            if (w != null)
                w.setNoAi(true);
        }
        BotPlayer brain = new BotPlayer(owner, Factions.TIDEWROUGHT, BotPlayer.Difficulty.MEDIUM, home);
        brain.enemyFilter = o -> false;
        Runnable undo = () -> {
            for (BuildingPlacement b : new ArrayList<>(Blueprints.pendingOf(owner)))
                Blueprints.cancel(b, null);
            for (BuildingPlacement b : new ArrayList<>(BuildingServerEvents.getBuildings()))
                if (owner.equals(b.ownerName))
                    BuildingServerEvents.getBuildings().remove(b);
            workers.forEach(Entity::discard);
            ResourcesServerEvents.resourcesList.remove(pool);
            for (int dx = -8; dx <= 8; dx++)
                for (int dz = -8; dz <= 8; dz++)
                    for (int dy = -1; dy <= 3; dy++)
                        level.setBlock(new BlockPos(centre.getX() + dx, y + dy, centre.getZ() + dz), Blocks.AIR.defaultBlockState(), 2);
        };
        if (workers.size() != 3) {
            undo.run();
            helper.fail("could not create three Shipwrights");
            return;
        }
        for (int t = 5; t <= 65; t += 20)
            helper.runAfterDelay(t, () -> {
                try {
                    brain.think(level, level.getGameTime());
                } catch (Exception e) {
                    undo.run();
                    helper.fail("the Tidewrought brain threw while its capitol was a blueprint: " + e);
                }
            });
        helper.runAfterDelay(85, () -> {
            try {
                List<String> problems = new ArrayList<>();
                List<BuildingPlacement> pending = Blueprints.pendingOf(owner);
                if (pending.size() != 1)
                    problems.add("the bot has " + pending.size() + " pending blueprints; want its one capitol");
                else if (pending.get(0).getBuilding() != Buildings.WRECK_HARBOUR)
                    problems.add("the bot's blueprint is a " + pending.get(0).getBuilding().name + ", not its Wreck Harbour");
                for (BuildingPlacement b : BuildingServerEvents.getBuildings())
                    if (owner.equals(b.ownerName))
                        problems.add("the bot registered " + b.getBuilding().name + " with no worker on the site");
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", problems));
            } finally {
                undo.run();
            }
        });
    }
}
