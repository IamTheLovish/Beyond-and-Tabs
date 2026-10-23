package com.solegendary.reignofnether.blocks;

import com.solegendary.reignofnether.ability.heroAbilities.wildfire.SoulsAflame;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.units.piglins.WildfireUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class UnextinguishableSoulFireBlock extends BaseFireBlock {
    public UnextinguishableSoulFireBlock(BlockBehaviour.Properties p_56653_) {
        super(p_56653_, 2.0F);
    }

    @Override
    public BlockState updateShape(BlockState pState, Direction pFacing, BlockState pFacingState, LevelAccessor pLevel, BlockPos pCurrentPos, BlockPos pFacingPos) {
        return this.defaultBlockState();
    }

    @Override
    public boolean canSurvive(BlockState pState, LevelReader pLevel, BlockPos pPos) {
        return pLevel.getBlockState(pPos.below()).isSolid();
    }

    @Override
    protected boolean canBurn(BlockState pState) {
        return true;
    }

    // Mean lifetime in ticks. This fire used to burn out on a random tick, and at randomTickSpeed 12 each block got one
    // every 4096 / 12 = ~341 ticks on average. The world now runs at vanilla's 3, so the fire keeps its own timer with
    // the same (exponential) lifetime instead of depending on the gamerule.
    private static final double MEAN_LIFETIME_TICKS = 4096.0 / 12.0;

    private static int nextBurnOutDelay(RandomSource random) {
        double u = random.nextDouble();
        return Math.max(1, (int) Math.round(-Math.log(1.0 - u) * MEAN_LIFETIME_TICKS));
    }

    @Override
    public void onPlace(BlockState pState, Level pLevel, BlockPos pPos, BlockState pOldState, boolean pIsMoving) {
        super.onPlace(pState, pLevel, pPos, pOldState, pIsMoving);
        if (!pLevel.isClientSide() && !pOldState.is(this)) {
            pLevel.scheduleTick(pPos, this, nextBurnOutDelay(pLevel.getRandom()));
        }
    }

    @Override
    public void tick(BlockState pState, ServerLevel pLevel, BlockPos pPos, RandomSource pRandom) {
        boolean inRangeOfWildfireUlt = false;
        for (LivingEntity unit : UnitServerEvents.getAllUnits()) {
            if (unit instanceof WildfireUnit wildfireUnit && wildfireUnit.hasEffect(MobEffectRegistrar.SOULS_AFLAME.get()) &&
                pPos.distToCenterSqr(wildfireUnit.position()) <= SoulsAflame.RANGE * SoulsAflame.RANGE) {
                inRangeOfWildfireUlt = true;
                break;
            }
        }
        if (inRangeOfWildfireUlt) {
            pLevel.scheduleTick(pPos, this, nextBurnOutDelay(pRandom));
        } else {
            pLevel.setBlockAndUpdate(pPos, Blocks.FIRE.defaultBlockState());
        }
    }
}
