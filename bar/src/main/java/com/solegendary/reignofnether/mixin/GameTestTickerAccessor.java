package com.solegendary.reignofnether.mixin;

import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestTicker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Collection;

/**
 * Read access to the game tests running now (gametest/GameTestArenas): which test's arena a point stands in, so a rule
 * that reaches across the 20 blocks between two arenas (a hero's kill XP) can tell a neighbour's arena from its own.
 * Vanilla keeps the list private. Read-only; empty whenever no game test runs, so play never sees it.
 */
@Mixin(GameTestTicker.class)
public interface GameTestTickerAccessor {
    @Accessor("testInfos")
    Collection<GameTestInfo> reignofnether$getTestInfos();
}
