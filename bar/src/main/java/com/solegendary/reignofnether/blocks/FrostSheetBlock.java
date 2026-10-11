package com.solegendary.reignofnether.blocks;

import com.solegendary.reignofnether.mana.FrostServerEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/**
 * Stormcallers <b>frost sheet</b> cell (design/stormcallers_plan.md 3.7): a 1/16-high skin of ice on top of the ground,
 * laid by the Frost Adept's Frost Field. Not vanilla ice - a full block that would replace terrain, melt into water and
 * break pathing. The TidepoolBlock recipe:
 * <ul>
 *   <li>No collision, no occlusion, replaceable: units walk on the ground under it (the pathfinder classifies the cell
 *       as plain land) and any building or placed block simply overwrites it.</li>
 *   <li>Only ever placed by {@link FrostServerEvents} (no block item, no loot), which owns lifetime, caps and the slow.
 *       The block only knows two things: fire or lava next to it melts it, and a scheduled fail-safe tick (saved with the
 *       chunk) removes it even when the server restarted and forgot the field - the terrain is never changed for good.</li>
 * </ul>
 * Costs nothing per tick: everything rides vanilla callbacks (neighbour update, scheduled tick, client animateTick).
 */
public class FrostSheetBlock extends Block {

    static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

    public FrostSheetBlock(Properties props) {
        super(props);
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return SHAPE;   // outline / clicking only
    }

    @Override
    public @NotNull VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return true;
    }

    // neighbouring cells of one field draw as one sheet: no inner side faces
    @Override
    public boolean skipRendering(@NotNull BlockState state, @NotNull BlockState adjacent, @NotNull Direction dir) {
        return adjacent.is(this) || super.skipRendering(state, adjacent, dir);
    }

    @Override
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel sl && fireNextTo(level, pos))
            FrostServerEvents.meltCell(sl, pos);
    }

    @Override
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block block,
                                @NotNull BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (level instanceof ServerLevel sl && fireNextTo(level, pos))
            FrostServerEvents.meltCell(sl, pos);
    }

    static boolean fireNextTo(Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockState n = level.getBlockState(pos.relative(d));
            if (n.getBlock() instanceof BaseFireBlock || n.is(Blocks.LAVA) || n.is(Blocks.MAGMA_BLOCK))
                return true;
        }
        return false;
    }

    /**
     * The fail-safe tick scheduled when the cell was laid (lifetime + a margin). Scheduled block ticks are saved with the
     * chunk, so this also runs after a restart; a cell of a field that is still live is re-armed.
     */
    @Override
    public void tick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        long left = FrostServerEvents.ticksLeftForCell(level, pos);
        if (left > 0) {
            level.scheduleTick(pos, this, (int) Math.min(Integer.MAX_VALUE, left + FrostServerEvents.FAILSAFE_MARGIN_TICKS));
            return;
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    @Override
    public void animateTick(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        // a snowflake now and then (client only): reads as frost from the RTS camera
        if (random.nextInt(18) == 0)
            level.addParticle(ParticleTypes.SNOWFLAKE, pos.getX() + random.nextDouble(), pos.getY() + 0.1,
                    pos.getZ() + random.nextDouble(), 0, 0.02, 0);
    }
}
