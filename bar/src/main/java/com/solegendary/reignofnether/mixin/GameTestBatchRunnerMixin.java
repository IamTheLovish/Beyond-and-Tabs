package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.gametest.GameTestLayout;
import net.minecraft.gametest.framework.GameTestBatchRunner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Lays the game-test arenas out {@link GameTestLayout#GAP} blocks apart instead of vanilla's 5 (in a row) and 6 (between
 * rows), so no hero XP rule reaches from one test's arena into another's. Only the game-test framework runs this.
 */
@Mixin(GameTestBatchRunner.class)
public abstract class GameTestBatchRunnerMixin {

    @ModifyConstant(method = "createStructuresForBatch", constant = @Constant(intValue = 5))
    private int reignofnether$columnGap(int vanilla) {
        return GameTestLayout.GAP;
    }

    @ModifyConstant(method = "createStructuresForBatch", constant = @Constant(intValue = 6))
    private int reignofnether$rowGap(int vanilla) {
        return GameTestLayout.GAP;
    }
}
