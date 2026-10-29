package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.maps.TreeScatter;
import com.solegendary.reignofnether.maps.TreeTemplates;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps spec M6: our own tree templates as MapBuilder writes them (TreeTemplates.place). Every shipped template, in
 * every turn and mirrored, on a grass floor high over this test's own arena: it loads from the jar, its trunk stands on
 * the ground, its leaves are natural (not persistent: TreeClearing counts them as a tree to fell), and a trunk on a
 * one-block step gets its foot filled instead of floating. Own holder; stays inside its own 16 x 16 footprint, so it
 * runs beside every other test.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class MapTreeGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void map_tree_templates_load_and_stand_on_the_ground(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos o = helper.absolutePos(BlockPos.ZERO);
        int x = o.getX() + 7, z = o.getZ() + 7, g = 230;
        List<String> problems = new ArrayList<>();
        int n = 0;
        for (TreeScatter.Kind kind : TreeScatter.Kind.values())
            for (int v = 1; v <= kind.variants; v++) {
                TreeScatter.Tree t = new TreeScatter.Tree(0, 0, kind, v, n % 4, n % 2 == 1, 0);
                n++;
                clear(level, x, z, g);
                floor(level, x, z, g, false);
                int written = TreeTemplates.place(level, t, x, z, g, Blocks.DIRT.defaultBlockState());
                String tag = t.template() + " turn " + t.rotation() + (t.mirror() ? " mirrored" : "") + ": ";
                if (written < 0) {
                    problems.add(tag + "not in the jar");
                    continue;
                }
                if (written < 10)
                    problems.add(tag + "only " + written + " blocks");
                int logs = 0;
                BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
                for (int dx = -7; dx <= 7; dx++)
                    for (int dz = -7; dz <= 7; dz++)
                        for (int y = 1; y <= 16; y++) {
                            BlockState s = level.getBlockState(p.set(x + dx, g + y, z + dz));
                            if (s.is(BlockTags.LOGS)) {
                                logs++;
                                if (y == 1 && level.getBlockState(p.set(x + dx, g, z + dz)).isAir())
                                    problems.add(tag + "a trunk floats at " + dx + "," + dz);
                            }
                            if (s.getBlock() instanceof LeavesBlock && s.getValue(LeavesBlock.PERSISTENT))
                                problems.add(tag + "persistent leaves (TreeClearing would not see a tree)");
                        }
                boolean wooded = kind.wood > 0;
                if (wooded && logs == 0)
                    problems.add(tag + "no logs");
            }
        // a trunk on a step: the floor one block lower under half the template -> the foot is filled, not floating
        clear(level, x, z, g);
        floor(level, x, z, g, true);
        TreeTemplates.place(level, new TreeScatter.Tree(0, 0, TreeScatter.Kind.OAK, 1, 0, false, 0), x, z, g,
            Blocks.DIRT.defaultBlockState());
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                if (level.getBlockState(p.set(x + dx, g + 1, z + dz)).is(BlockTags.LOGS)
                        && level.getBlockState(p.set(x + dx, g, z + dz)).isAir())
                    problems.add("oak_1 on a step: the trunk floats at " + dx + "," + dz);
        clear(level, x, z, g);
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(problems.size() + " problem(s): " + String.join("; ", problems.subList(0, Math.min(8, problems.size()))));
    }

    /** A grass floor at y g (with a one-block step down for dx < 0 when asked), dirt under it. */
    private static void floor(ServerLevel level, int x, int z, int g, boolean step) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -7; dx <= 7; dx++)
            for (int dz = -7; dz <= 7; dz++) {
                int top = step && dx < 0 ? g - 1 : g;
                level.setBlock(p.set(x + dx, top - 1, z + dz), Blocks.DIRT.defaultBlockState(), 2);
                level.setBlock(p.set(x + dx, top, z + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            }
    }

    private static void clear(ServerLevel level, int x, int z, int g) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -7; dx <= 7; dx++)
            for (int dz = -7; dz <= 7; dz++)
                for (int y = -2; y <= 17; y++)
                    level.setBlock(p.set(x + dx, g + y, z + dz), Blocks.AIR.defaultBlockState(), 2);
    }
}
