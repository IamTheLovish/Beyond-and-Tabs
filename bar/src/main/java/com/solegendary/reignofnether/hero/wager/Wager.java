package com.solegendary.reignofnether.hero.wager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * The one server path for asking Lovish for something (design "Asking him for things"): the book's Wager button, and
 * later typed chat and voice, all call {@link #cast} with the page's text, so odds and lines are the same however the
 * ask came in. Checks the cooldown, a page and the ink (the hero's mana), parses the request, rolls the verdict, runs
 * the phenomenon (or the refusal's fizzle and, sometimes, the yapping), writes the reply into the book and logs it.
 */
public final class Wager {
    private Wager() { }

    /** The debug toggle (/wagerbook on): while Lovish has no body of his own, any faction hero can carry the book. */
    public static volatile boolean enabled = false;

    /** How often a refusal turns into Lovish yapping (a neutral effect). Numbers for lovish. */
    public static final double YAP_CHANCE = 0.25;

    public enum Outcome { CAST, COOLDOWN, NO_PAGES, NO_INK, NOT_A_HERO }

    /** What happened: the parse, the verdict, the phenomenon's name, the reply written into the book, the yapping. */
    public record Result(Outcome outcome, @Nullable WagerParser.Parsed parsed, @Nullable WagerVerdict.Result verdict,
                         String phenomenon, String reply, boolean yapped, String neutralEffect) { }

    public static Result cast(ServerLevel level, Mob hero, String page) {
        return cast(level, hero, page, null, new Random(), WagerClock.MATCH);
    }

    public static Result cast(ServerLevel level, Mob hero, String page, @Nullable Vec3 aim, Random rng, WagerClock clock) {
        return new Result(Outcome.NOT_A_HERO, null, null, "", "", false, "");
    }
}
