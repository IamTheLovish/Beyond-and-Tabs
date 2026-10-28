package com.solegendary.reignofnether.hero.wager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.List;

/**
 * "Lovish yapping" (lovish's answer to Q7): sometimes a refusal turns into a monologue that casts a random effect that
 * is neither good nor bad - cosmetic weather, a harmless swap, a silly sound, a short neutral status. Nothing here
 * deals damage, heals, pays, spawns or takes anything: it is flavour, not a consolation prize.
 */
public final class NeutralYap {
    private NeutralYap() { }

    public static final List<String> EFFECTS = List.of("confetti", "snow_flurry", "silly_sound", "glow", "slow_fall", "swap");

    /** Casts neutral effect {@code index} (mod the list) around the hero; returns its id. */
    public static String cast(ServerLevel level, Mob hero, int index) {
        return "";
    }
}
