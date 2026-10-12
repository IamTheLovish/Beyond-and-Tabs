package com.solegendary.reignofnether.onboarding;

import com.solegendary.reignofnether.bot.BotPlayer;
import com.solegendary.reignofnether.faction.Faction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The First Battle's sparring partner: a real RTS bot (it is in the match, has a commander and a capitol, can be
 * defeated) whose brain never thinks, so it never builds, trains or attacks. Put into BotServerEvents.brains under the
 * bot's name - the bot loop only rebuilds a brain that is missing, so this one stays (OnboardingServer re-attaches it
 * after a reload before the first bot tick).
 */
public class PassiveBot extends BotPlayer {

    public PassiveBot(String name, Faction faction, BlockPos home) {
        super(name, faction, Difficulty.EASY, home);
    }

    @Override
    public void think(ServerLevel level, long gameTime) {
        // deliberately nothing: the student learns at their own pace
    }
}
