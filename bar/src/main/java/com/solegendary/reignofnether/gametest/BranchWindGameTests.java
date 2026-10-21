package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.environment.wind.SwayBlocks;
import com.solegendary.reignofnether.environment.wind.SwayFenceBlock;
import com.solegendary.reignofnether.environment.wind.SwayWoodBlock;
import com.solegendary.reignofnether.environment.wind.WindField;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.resources.TreeClearing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Branch wind (environment/wind, design/branch_wind.md): the sway limb blocks are vanilla wood and fences to everything
 * but the renderer - same shapes, felled with the tree for the same wood - and the wind field they move in stays
 * within its bounds.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class BranchWindGameTests {

    /**
     * A tree whose limbs are sway wood and whose twig is a sway fence is one tree to TreeClearing: the scan counts
     * every limb as a log, felling removes them all (block entities included) and pays 5 energy per log.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 100)
    public static void sway_limbs_fell_with_the_tree_for_wood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_wind_" + helper.absolutePos(BlockPos.ZERO).asLong();
        var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 0, 0);
        ResourcesServerEvents.resourcesList.add(pool);
        try {
            BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8)).atY(170);
            for (int dx = -4; dx <= 4; dx++)
                for (int dz = -4; dz <= 4; dz++) {
                    level.setBlock(base.offset(dx, -1, dz), Blocks.DIRT.defaultBlockState(), 2);
                    for (int dy = 0; dy <= 9; dy++)
                        level.setBlock(base.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                }
            SwayWoodBlock wood = SwayBlocks.wood("oak");
            SwayFenceBlock fence = SwayBlocks.fence("oak");
            if (wood == null || fence == null) {
                helper.fail("no sway oak blocks registered");
                return;
            }
            // trunk: 4 vanilla logs; a limb of 3 sway wood blocks east at y 3 (tiers 1-3); a sway fence twig past it
            List<BlockPos> logs = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                level.setBlock(base.above(i), Blocks.OAK_LOG.defaultBlockState(), 2);
                logs.add(base.above(i));
            }
            for (int i = 1; i <= 3; i++) {
                BlockPos p = base.offset(i, 3, 0);
                level.setBlock(p, wood.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y)
                    .setValue(SwayBlocks.SWAY, i), 2);
                logs.add(p);
            }
            BlockPos twig = base.offset(4, 3, 0);
            level.setBlock(twig, fence.defaultBlockState().setValue(FenceBlock.WEST, true).setValue(SwayBlocks.SWAY, 4), 2);
            logs.add(twig);
            var leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1).setValue(LeavesBlock.PERSISTENT, false);
            List<BlockPos> leaves = List.of(base.offset(0, 4, 0), base.offset(3, 4, 0), base.offset(4, 4, 0), base.offset(2, 4, 0));
            for (BlockPos p : leaves)
                level.setBlock(p, leaf, 2);

            // the blocks are logs, carry their block entity, and keep the vanilla shapes
            for (BlockPos p : logs) {
                BlockState bs = level.getBlockState(p);
                if (!bs.is(BlockTags.LOGS)) {
                    helper.fail(bs.getBlock() + " at " + p.toShortString() + " is not in #minecraft:logs");
                    return;
                }
                if (SwayBlocks.isSway(bs)) {
                    BlockEntity be = level.getBlockEntity(p);
                    if (be == null) {
                        helper.fail("no block entity under the sway block at " + p.toShortString());
                        return;
                    }
                    BlockState vanilla = bs.getBlock() instanceof SwayWoodBlock sw
                        ? sw.vanilla.defaultBlockState().setValue(RotatedPillarBlock.AXIS, bs.getValue(RotatedPillarBlock.AXIS))
                        : ((SwayFenceBlock) bs.getBlock()).vanilla.defaultBlockState().setValue(FenceBlock.WEST, true);
                    VoxelShape ours = bs.getCollisionShape(level, p), theirs = vanilla.getCollisionShape(level, p);
                    if (Shapes.joinIsNotEmpty(ours, theirs, net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME)) {
                        helper.fail("the collision shape of " + bs.getBlock() + " differs from " + vanilla.getBlock() + ": " + ours + " vs " + theirs);
                        return;
                    }
                    if (bs.isSolidRender(level, p) != vanilla.isSolidRender(level, p)) {
                        helper.fail(bs.getBlock() + " occludes differently from " + vanilla.getBlock());
                        return;
                    }
                }
            }
            if (!level.getBlockState(twig).getValue(FenceBlock.WEST)) {
                helper.fail("the sway fence twig lost its join to the limb (sway fences must be in #minecraft:fences)");
                return;
            }

            var job = TreeClearing.fellTree(level, owner, base);
            if (job == null) {
                helper.fail("TreeClearing does not see the tree with sway limbs (trunk " + base.toShortString() + ")");
                return;
            }
            job.finish();
            for (BlockPos p : logs)
                if (!level.getBlockState(p).isAir()) {
                    helper.fail(level.getBlockState(p).getBlock() + " at " + p.toShortString() + " is still standing after felling");
                    return;
                }
            for (BlockPos p : logs)
                if (level.getBlockEntity(p) != null) {
                    helper.fail("a sway block entity outlived its block at " + p.toShortString());
                    return;
                }
            for (BlockPos p : leaves)
                if (!level.getBlockState(p).isAir()) {
                    helper.fail("a leaf is still there after felling");
                    return;
                }
            if (job.logsFelled != logs.size())
                helper.fail("felled " + job.logsFelled + " logs, planted " + logs.size() + " (4 trunk + 3 sway wood + 1 sway fence)");
            else if (Math.abs(job.energyCredited - logs.size() * TreeClearing.ENERGY_PER_LOG) > 0.5f)
                helper.fail(logs.size() + " logs should credit " + logs.size() * TreeClearing.ENERGY_PER_LOG + " energy, credited " + job.energyCredited);
            else
                helper.succeed();
        } finally {
            ResourcesServerEvents.resourcesList.remove(pool);
        }
    }

    /**
     * The wind field: a tip never moves more than ~0.15 block even at the strongest match wind, a trunk-tier block (0)
     * or a faded block never moves, further-out tiers move more, and the motion is smooth frame to frame.
     */
    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 20)
    public static void wind_field_stays_subtle_smooth_and_grows_outward(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        double[] a = new double[3], b = new double[3];
        double maxTip = 0, maxStep = 0;
        java.util.Random rng = new java.util.Random(7);
        for (int n = 0; n < 4000; n++) {
            int x = rng.nextInt(1024) - 512, y = 60 + rng.nextInt(40), z = rng.nextInt(1024) - 512;
            double t = rng.nextDouble() * 3600;
            WindField.offset(x, y, z, SwayBlocks.MAX_TIER, t, WindField.MAX_WIND_MUL, 1.0, a);
            double tip = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
            maxTip = Math.max(maxTip, tip);
            WindField.offset(x, y, z, SwayBlocks.MAX_TIER, t + 1 / 20.0, WindField.MAX_WIND_MUL, 1.0, b);
            maxStep = Math.max(maxStep, Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2])));
            double prev = -1;
            for (int tier = 1; tier <= SwayBlocks.MAX_TIER; tier++) {
                WindField.offset(x, y, z, tier, t, 1.0, 1.0, b);
                double m = Math.abs(b[0]) + Math.abs(b[2]);
                if (m < prev - 1e-9)
                    problems.add("tier " + tier + " moves less than tier " + (tier - 1) + " at " + x + "," + z + " t=" + t);
                prev = m;
            }
            WindField.offset(x, y, z, 0, t, 1.0, 1.0, b);
            if (b[0] != 0 || b[1] != 0 || b[2] != 0)
                problems.add("a tier-0 (trunk) block moved");
            WindField.offset(x, y, z, 4, t, 1.0, 0.0, b);
            if (b[0] != 0 || b[1] != 0 || b[2] != 0)
                problems.add("a block beyond the sway range (fade 0) moved");
            if (problems.size() > 5)
                break;
        }
        if (maxTip > 0.16)
            problems.add("a twig tip moved " + maxTip + " block, the rule is under ~0.15");
        if (maxTip < 0.08)
            problems.add("the strongest sway was only " + maxTip + " block - too subtle to see");
        if (maxStep > 0.03)
            problems.add("the sway jumped " + maxStep + " block in one tick");
        if (WindField.maxOffset() > 0.16)
            problems.add("WindField.maxOffset() " + WindField.maxOffset() + " is over the 0.15 rule");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems.subList(0, Math.min(6, problems.size()))));
    }
}
