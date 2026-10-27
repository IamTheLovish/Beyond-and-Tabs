package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitChunkLoader;
import com.solegendary.reignofnether.unit.UnitQueueSync;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.unit.packets.UnitSyncClientboundPacket;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * lovish, multiplayer on a dedicated server (Oct 22): "worker queues get cancelled when losing line of sight of them".
 * A worker's build queue belongs to the server: it must run on whatever the owner's camera, vision and client are
 * doing, and the owner's client must be able to see it again when the worker comes back into view.
 *
 * What the code showed (the PR body has the trail): no server code ties a worker's queue to the owner's sight except
 * the fog queue (a site queued where the owner cannot see yet is walked to, then placed). That path had three holes:
 * the site's chunks were held by nothing (the RTS camera is the player entity, so chunks near it load and the rest
 * unload as it scrolls away, and a target in an unloaded chunk is "no path"); the worker with only a fog site left
 * counted as idle between repaths (so the next queued order, the idle-worker list and autocast could take it); and
 * the queue lines the owner sees showed only the current site. These tests pin the server's side of all three.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class WorkerQueueFogGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static VillagerUnit worker(ServerLevel level, String owner, double x, double y, double z) {
        VillagerUnit w = EntityRegistrar.VILLAGER_UNIT.get().create(level);
        if (w == null)
            return null;
        w.moveTo(x, y, z, 0, 0);
        w.setOwnerName(owner);
        level.addFreshEntity(w);
        return w;
    }

    /** The queue-line entries the server would send {@code owner} for this unit: {type, x, y, z, entityId}. */
    static List<int[]> lines(String owner, int unitId) {
        List<int[]> out = new ArrayList<>();
        IntArrayList data = UnitQueueSync.buildPayloads(Set.of(owner)).get(owner);
        if (data == null)
            return out;
        int i = 0;
        while (i + 1 < data.size()) {
            int id = data.getInt(i++), n = data.getInt(i++);
            for (int k = 0; k < n; k++) {
                int[] e = new int[UnitQueueSync.INTS_PER_ENTRY];
                for (int j = 0; j < e.length; j++)
                    e[j] = data.getInt(i++);
                if (id == unitId)
                    out.add(e);
            }
        }
        return out;
    }

    static String describe(List<int[]> lines) {
        StringBuilder sb = new StringBuilder("[");
        for (int[] e : lines)
            sb.append(sb.length() > 1 ? ", " : "").append(e[0]).append('@').append(e[1]).append('/').append(e[2]).append('/').append(e[3]);
        return sb.append(']').toString();
    }

    static boolean isBuildLine(int[] e, BlockPos at) {
        return e[0] == UnitQueueSync.TYPE_BUILD && e[1] == at.getX() && e[2] == at.getY() && e[3] == at.getZ();
    }

    /**
     * Three shift-queued sites, and the worker's client loses it (the leave packet a client gets when the server stops
     * showing it) while the owner has no player online at all: the queue is untouched, the worker keeps working its
     * head, and as each site goes the worker steps down to the next, to the end. The queue lines (what the owner's
     * client draws) list the current site and both queued ones.
     */
    @GameTest(template = ARENA, timeoutTicks = 900)
    public static void a_shift_queue_runs_on_when_the_owners_client_loses_the_worker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_wq_sight_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlockPos o = ArenaReady.pad(helper, 301);   // its own floor, as the other movement tests have
        int cx = o.getX() + 8, cz = o.getZ() + 8;
        final int y = o.getY() + 1;   // grass top; the sites' origin level
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        VillagerUnit worker = worker(level, owner, cx + 0.5, y + 1, cz - 7.5);
        if (worker == null) {
            helper.fail("could not create the worker");
            return;
        }
        List<BuildingPlacement> sites = new ArrayList<>();
        Runnable cleanup = () -> {
            BuildingServerEvents.getBuildings().removeAll(sites);
            worker.discard();
        };
        final boolean[] done = { false };
        final int[] stage = { 0 };
        final long[] since = { 0 };
        final boolean[] placed = { false };
        var brg = worker.getBuildRepairGoal();
        // the tick hooks are registered here, not inside the whenTicking body: GameTestInfo keeps them in a fastutil
        // map it walks while running them, and adding one from inside a hook crashed the server tick (CI run 39726502691)
        helper.runAtTickTime(850, () -> {
            if (done[0])
                return;
            done[0] = true;
            String state = "stage " + stage[0] + ", placed " + placed[0] + ", target "
                + (brg.getBuildingTarget() == null ? "none" : brg.getBuildingTarget().originPos.toShortString())
                + ", queue " + brg.queuedBuildings.size() + ", building " + brg.isBuilding();
            cleanup.run();
            helper.fail("the queue did not run through: " + state);
        });
        ArenaReady.whenTicking(helper, List.of(worker), 100, start -> {
            int[] ids = { worker.getId() };
            var wind = Buildings.WIND_GENERATOR_VILLAGERS;
            for (int x : new int[] { -6, 0, 6 }) {
                BuildingPlacement p = BuildingServerEvents.placeBuilding(wind, new BlockPos(cx + x, y, cz), Rotation.NONE,
                    owner, ids, x != -6, false, true, true);
                if (p == null) {
                    cleanup.run();
                    helper.fail("site at " + x + " was refused");
                    return;
                }
                sites.add(p);
            }
            placed[0] = true;
        });
        helper.onEachTick(() -> {
            if (done[0] || !placed[0])
                return;
            BuildingPlacement a = sites.get(0), b = sites.get(1), c = sites.get(2);
                long now = helper.getTick();
                switch (stage[0]) {
                    case 0 -> {
                        if (!brg.queuedBuildings.equals(List.of(a, b, c)) && brg.getBuildingTarget() == a) {
                            done[0] = true;
                            cleanup.run();
                            helper.fail("expected the queue [a, b, c], got " + brg.queuedBuildings.size() + " entries");
                            return;
                        }
                        if (brg.getBuildingTarget() != a || !brg.isBuilding())
                            return;   // still walking to the first site
                        List<int[]> ls = lines(owner, worker.getId());
                        long builds = ls.stream().filter(e -> e[0] == UnitQueueSync.TYPE_BUILD).count();
                        if (builds != 3 || !isBuildLine(ls.get(0), a.centrePos) || !isBuildLine(ls.get(1), b.centrePos)
                                || !isBuildLine(ls.get(2), c.centrePos)) {
                            done[0] = true;
                            cleanup.run();
                            helper.fail("the owner's queue lines should list the current site and both queued ones, got " + describe(ls));
                            return;
                        }
                        // the client loses the worker: the server tells every client it is gone from view
                        UnitSyncClientboundPacket.sendLeavePacket(worker);
                        since[0] = now;
                        stage[0] = 1;
                    }
                    case 1 -> {
                        if (brg.getBuildingTarget() != a || !brg.queuedBuildings.equals(List.of(a, b, c))) {
                            done[0] = true;
                            String s = "target " + brg.getBuildingTarget() + ", queue " + brg.queuedBuildings.size();
                            cleanup.run();
                            helper.fail("the worker's queue changed after its client lost it: " + s);
                            return;
                        }
                        if (now - since[0] < 40)
                            return;
                        BuildingServerEvents.getBuildings().remove(a);   // site a is done (or gone): on to b
                        stage[0] = 2;
                    }
                    case 2 -> {
                        if (brg.getBuildingTarget() != b)
                            return;
                        if (!brg.queuedBuildings.equals(List.of(b, c))) {
                            done[0] = true;
                            cleanup.run();
                            helper.fail("after the first site the queue should be [b, c], got " + brg.queuedBuildings.size());
                            return;
                        }
                        BuildingServerEvents.getBuildings().remove(b);
                        stage[0] = 3;
                    }
                    case 3 -> {
                        if (brg.getBuildingTarget() != c)
                            return;
                        BuildingServerEvents.getBuildings().remove(c);
                        stage[0] = 4;
                    }
                    default -> {
                        if (brg.getBuildingTarget() != null)
                            return;
                        done[0] = true;
                        boolean empty = brg.queuedBuildings.isEmpty();
                        cleanup.run();
                        if (!empty)
                            helper.fail("the queue should be empty after the last site, got " + brg.queuedBuildings.size());
                        else
                            helper.succeed();
                    }
                }
        });
    }

    /**
     * Two sites queued where the owner cannot see (no player of that name, so the fog says "unexplored"): the worker
     * is sent to explore them. Its fog queue is an order it still has, so it is not idle even when its navigation has
     * just finished (WorkerUnit.isIdle: an idle worker is handed the next shift-queued order, which resets it and
     * drops the fog queue), the sites' chunks are held loaded for it (UnitChunkLoader.holdSite: without that the
     * target of its path can be an unloaded chunk once the owner's camera has scrolled away, which is "no path"), and
     * the owner's queue lines show both sites.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void a_site_queued_in_the_fog_keeps_its_worker_busy_and_its_chunks_held(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_wq_fog_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlockPos o = ArenaReady.pad(helper, 301);
        int cx = o.getX() + 8, cz = o.getZ() + 8;
        final int y = o.getY() + 1;
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        VillagerUnit worker = worker(level, owner, cx + 0.5, y + 1, cz - 7.5);
        if (worker == null) {
            helper.fail("could not create the worker");
            return;
        }
        Runnable cleanup = () -> {
            worker.getExploreBuildLocationGoal().reset();
            worker.discard();
        };
        final long[] checkAt = { -1 };
        var fog = worker.getExploreBuildLocationGoal().getFogQueuedBuildings();
        // hooks registered here, not from inside a hook (see the first test)
        helper.onEachTick(() -> {
            if (checkAt[0] < 0 || helper.getTick() < checkAt[0])
                return;
            checkAt[0] = Long.MAX_VALUE;
            String problem = null;
            worker.getNavigation().stop();   // between two repaths the navigation reads "done"
            if (WorkerUnit.isIdle(worker))
                problem = "a worker with sites queued in the fog counted as idle";
            else if (!UnitChunkLoader.isHeld(level, fog.get(0).centrePos))
                problem = "the chunks of the site the worker is heading for are not held";
            else if (fog.size() != 2)
                problem = "the fog queue changed to " + fog.size() + " sites";
            cleanup.run();
            if (problem != null)
                helper.fail(problem);
            else
                helper.succeed();
        });
        ArenaReady.whenTicking(helper, List.of(worker), 100, start -> {
            int[] ids = { worker.getId() };
            for (int x : new int[] { -3, 3 })
                BuildingServerEvents.placeBuilding(Buildings.WIND_GENERATOR_VILLAGERS, new BlockPos(cx + x, y, cz),
                    Rotation.NONE, owner, ids, true, false, false, false);
            if (fog.size() != 2) {
                cleanup.run();
                helper.fail("both sites should wait in the worker's fog queue, got " + fog.size());
                return;
            }
            Set<BlockPos> centres = new HashSet<>();
            for (BuildingPlacement p : fog)
                centres.add(p.centrePos);
            List<int[]> ls = lines(owner, worker.getId());
            Set<BlockPos> drawn = new HashSet<>();
            for (int[] e : ls)
                if (e[0] == UnitQueueSync.TYPE_BUILD)
                    drawn.add(new BlockPos(e[1], e[2], e[3]));
            if (!drawn.equals(centres)) {
                cleanup.run();
                helper.fail("the owner's queue lines should list both fog sites " + centres + ", got " + describe(ls));
                return;
            }
            // judge after the worker has run a second of its own goals (the hold is refreshed once a second)
            checkAt[0] = helper.getTick() + 45;
        });
    }
}
