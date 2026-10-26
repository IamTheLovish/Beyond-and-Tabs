package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.fogofwar.FogExploration;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * "Dark but visible" fog (lovish, Oct 22): ground never in sight keeps a faint map shape instead of black. The pure
 * parts of it: which tint a column gets in each of the three states, and that a chunk's first sighting - and only
 * that - asks for a re-mesh. No world, no renderer.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class FogExplorationGameTests {

    private static int brightness(int rgb) {
        return (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3;
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void fog_tint_is_none_when_visible_explored_when_seen_before_and_darker_when_never_explored(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        int explored = 0x7882A0, unexplored = FogExploration.TINT_UNEXPLORED;
        if (FogExploration.pickTint(true, true, explored, unexplored) != 0 || FogExploration.pickTint(true, false, explored, unexplored) != 0)
            problems.add("a visible column must get no tint");
        if (FogExploration.pickTint(false, true, explored, unexplored) != explored)
            problems.add("an explored column out of sight must get the Fog style's tint");
        if (FogExploration.pickTint(false, false, explored, unexplored) != unexplored)
            problems.add("a never-explored column must get the unexplored tint");
        // ~15% of full brightness: clearly darker than the explored tint, still not black
        int b = brightness(unexplored);
        if (b < 255 * 10 / 100 || b > 255 * 22 / 100)
            problems.add("the unexplored tint is " + b + "/255 bright, expected about 15%");
        if (b * 2 > brightness(explored))
            problems.add("the unexplored tint is not much darker than the explored one");
        // grass already tinted by the explored tint ends up at the unexplored one (BiomeColorsMixin stays as it is)
        int grass = 0x5FA03A;
        int viaMixin = multiply(grass, explored);
        int dimmed = FogExploration.dimToUnexplored(viaMixin, explored, unexplored);
        int direct = multiply(grass, unexplored);
        for (int shift = 0; shift <= 16; shift += 8)
            if (Math.abs(((dimmed >> shift) & 0xFF) - ((direct >> shift) & 0xFF)) > 2)
                problems.add("a dimmed biome colour differs from tinting it directly: " + Integer.toHexString(dimmed) + " vs "
                    + Integer.toHexString(direct));
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    private static int multiply(int c, int tint) {
        int r = ((c >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255;
        int g = ((c >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255;
        int bl = (c & 0xFF) * (tint & 0xFF) / 255;
        return (r << 16) | (g << 8) | bl;
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void a_chunk_asks_for_one_remesh_when_first_explored_and_not_again(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        LongOpenHashSet known = new LongOpenHashSet();
        List<ChunkPos> a = List.of(new ChunkPos(0, 0), new ChunkPos(1, 0));
        if (FogExploration.firstExplored(known, a).size() != 2)
            problems.add("two chunks seen for the first time must both ask for a re-mesh");
        if (!FogExploration.firstExplored(known, a).isEmpty())
            problems.add("the same chunks in sight again must not re-mesh");
        List<ChunkPos> first = FogExploration.firstExplored(known, List.of(new ChunkPos(1, 0), new ChunkPos(-3, 7)));
        if (first.size() != 1 || first.get(0).x != -3 || first.get(0).z != 7)
            problems.add("only the one new chunk (-3, 7) should ask for a re-mesh, got " + first);
        if (!known.contains(ChunkPos.asLong(-3, 7)) || known.size() != 3)
            problems.add("the explored set did not grow to the three chunks seen");
        // the live set the mesher reads: first sighting listed once, then explored; cleared with a new fog
        FogExploration.clear();
        try {
            if (FogExploration.isExplored(5, 5))
                problems.add("a cleared set already knows chunk (5, 5)");
            if (FogExploration.markExplored(List.of(new ChunkPos(5, 5))).size() != 1 || !FogExploration.isExplored(5, 5))
                problems.add("the live set did not record chunk (5, 5) on its first sighting");
            if (!FogExploration.markExplored(List.of(new ChunkPos(5, 5))).isEmpty())
                problems.add("the live set re-listed chunk (5, 5)");
        } finally {
            FogExploration.clear();
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
