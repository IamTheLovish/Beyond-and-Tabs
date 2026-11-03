package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.mixin.GameTestTickerAccessor;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * Which running game test's arena a point stands in. The headless suite runs dozens of tests side by side, their arenas
 * 20 blocks apart, and some game rules reach across that gap: a hero earns XP from any enemy death within 20 blocks
 * (28 for a decisive kill), so WinPays' hero kill at tick 3 paid HeroAutoLearn's witch next door a second point
 * ("points 0, ranks 2": 9 of 31 CI shard-8 jobs, Nov 1-3; flake-stress 41599142411 traced the 350 XP to the Dawn Paladin
 * dying 20 blocks away) and H13's Shaman 450 XP before it (PR 473 moved that test to a batch of its own). Such a rule asks
 * {@link #apart} and ignores a neighbour's arena. An arena is its test's column (x and z only): a hero hung above its
 * arena (WinPaysGameTests) is still in it. In play no game test runs, the list is empty and {@link #apart} is false.
 */
public final class GameTestArenas {

    private GameTestArenas() {
    }

    private static Collection<GameTestInfo> running() {
        return ((GameTestTickerAccessor) (Object) GameTestTicker.SINGLETON).reignofnether$getTestInfos();
    }

    /** The test structure's bounds, or null before its structure block stands (a test still being laid out). */
    @Nullable
    static AABB bounds(GameTestInfo info) {
        try {
            return info.getStructureBounds();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The running test whose arena column holds (x, z) in this level, or null (outside every arena, or no test runs). */
    @Nullable
    public static GameTestInfo arenaAt(Level level, double x, double z) {
        Collection<GameTestInfo> infos = running();
        if (infos.isEmpty())
            return null;
        for (GameTestInfo info : infos) {
            if (info.isDone() || info.getLevel() != level)
                continue;
            AABB box = bounds(info);
            if (box != null && x >= box.minX && x <= box.maxX && z >= box.minZ && z <= box.maxZ)
                return info;
        }
        return null;
    }

    /** True when {@code a} and {@code b} stand in the arenas of two different running game tests; false in play. */
    public static boolean apart(Entity a, Entity b) {
        if (running().isEmpty())
            return false;
        GameTestInfo ia = arenaAt(a.level(), a.getX(), a.getZ());
        if (ia == null)
            return false;
        GameTestInfo ib = arenaAt(b.level(), b.getX(), b.getZ());
        return ib != null && ib != ia;
    }

    /**
     * Flat distance from (x, z) to the nearest point of another running test's arena than {@code self}'s, or
     * {@code Double.MAX_VALUE}: a test that must have a neighbour within reach checks the layout still gives it one.
     */
    public static double nearestOtherArena(GameTestInfo self, double x, double z) {
        double best = Double.MAX_VALUE;
        for (GameTestInfo info : running()) {
            if (info == self || info.isDone() || info.getLevel() != self.getLevel())
                continue;
            AABB box = bounds(info);
            if (box == null)
                continue;
            double dx = Math.max(0, Math.max(box.minX - x, x - box.maxX)), dz = Math.max(0, Math.max(box.minZ - z, z - box.maxZ));
            best = Math.min(best, Math.sqrt(dx * dx + dz * dz));
        }
        return best;
    }
}
