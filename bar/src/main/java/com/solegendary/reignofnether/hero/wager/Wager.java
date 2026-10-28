package com.solegendary.reignofnether.hero.wager;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.hero.FactionHero;
import com.solegendary.reignofnether.hero.HeroFx;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

/**
 * The one server path for asking Lovish for something (design "Asking him for things"): the book's Wager button, and
 * later typed chat and voice, all call {@link #cast} with the page's text, so odds and lines are the same however the
 * ask came in. Checks the cooldown, a page and the ink (the hero's mana), parses the request, rolls the verdict, runs
 * the phenomenon (or the refusal's fizzle and, sometimes, the yapping), writes the reply into the book and logs it.
 * <p>
 * Costs (design "Ink"): every wager that is read spends a page and starts the cooldown; a granted or twisted one also
 * spends ink by the granted tier; a refusal refunds the ink (it never takes it). Nonsense costs a page and no ink.
 */
public final class Wager {
    private Wager() { }

    /** The debug toggle (/wagerbook on): while Lovish has no body of his own, any faction hero can carry the book. */
    public static volatile boolean enabled = false;

    /** How often a refusal turns into Lovish yapping (a neutral effect). Numbers for lovish. */
    public static final double YAP_CHANCE = 0.25;
    /** "Asked in the last 60 s" lowers the agree chance (WagerVerdict). */
    public static final int RECENT_TICKS = 60 * 20;
    /** How far an aimed phenomenon looks for an enemy when the ask came without an aim point. */
    public static final double AUTO_AIM_RANGE = 14;

    public enum Outcome { CAST, COOLDOWN, NO_PAGES, NO_INK, NOT_A_HERO }

    /** What happened: the parse, the verdict, the phenomenon's name, the reply written into the book, the yapping. */
    public record Result(Outcome outcome, @Nullable WagerParser.Parsed parsed, @Nullable WagerVerdict.Result verdict,
                         String phenomenon, String reply, boolean yapped, String neutralEffect) {
        static Result refused(Outcome why, String reply) {
            return new Result(why, null, null, "", reply, false, "");
        }
    }

    public static int levelOf(Mob hero) {
        return hero instanceof HeroUnit h ? Math.max(1, h.getHeroLevel()) : 1;
    }

    public static Result cast(ServerLevel level, Mob hero, String page) {
        return cast(level, hero, page, null, new Random(), WagerClock.MATCH);
    }

    public static Result cast(ServerLevel level, Mob hero, String page, @Nullable Vec3 aim, Random rng, WagerClock clock) {
        if (!(hero instanceof FactionHero fh) || !hero.isAlive())
            return Result.refused(Outcome.NOT_A_HERO, "Only a hero can be asked.");
        long now = level.getGameTime();
        int heroLevel = levelOf(hero);
        WagerState book = WagerState.of(hero);
        book.regrow(now, heroLevel);
        if (now < book.cooldownUntil())
            return Result.refused(Outcome.COOLDOWN, "Not yet. The ink is still wet (" + (book.cooldownUntil() - now + 19) / 20 + " s).");
        if (book.pages() <= 0)
            return Result.refused(Outcome.NO_PAGES, "The book is out of pages. One grows back every 30 s.");

        WagerIntents table = WagerIntents.get();
        WagerParser.Parsed parsed = WagerParser.parse(page, table);
        WagerIntents.Intent intent = parsed.intent();
        boolean nonsense = WagerIntents.NONSENSE.equals(intent.id());
        int affordable = WagerVerdict.inkCost(Math.min(parsed.askedTier(), WagerVerdict.unlockedTier(heroLevel)));
        if (!nonsense && fh.getMana() < affordable)
            return Result.refused(Outcome.NO_INK, "Not enough ink: this one needs " + affordable + ".");

        boolean recent = now - book.lastAskAt() < RECENT_TICKS;
        WagerVerdict.Result verdict = nonsense ? new WagerVerdict.Result(WagerVerdict.Kind.AGREE, 1, 1, 0)
            : WagerVerdict.roll(intent, heroLevel, parsed.askedTier(), recent, book.lastTwistIntent(), rng);
        int tier = verdict.tier();
        if (tier >= 4) {
            tier = clock.fairTier(hero.getUUID(), intent.id(), tier, now);
            if (tier >= 4)
                clock.recordLegendary(hero.getUUID(), intent.id(), now);
            verdict = new WagerVerdict.Result(verdict.kind(), tier, verdict.agreeChance(), verdict.twistChance());
        }

        book.setPages(book.pages() - 1);
        book.setCooldownUntil(now + WagerVerdict.cooldownTicks(heroLevel));
        book.setLastAskAt(now);
        book.setLastTwistIntent(verdict.kind() == WagerVerdict.Kind.TWIST ? intent.id() : "");
        book.count(verdict.kind());

        String phenomenon = "";
        boolean yapped = false;
        String neutral = "";
        String reply;
        if (verdict.kind() == WagerVerdict.Kind.REFUSE) {
            yapped = rng.nextDouble() < YAP_CHANCE;
            if (yapped)
                neutral = NeutralYap.cast(level, hero, rng.nextInt(NeutralYap.EFFECTS.size()));
            boolean low = parsed.askedTier() > WagerVerdict.unlockedTier(heroLevel);
            reply = "Refused. " + line(table, low ? "refuse_low" : "refuse", heroLevel, rng);
            if (yapped)
                reply += " ... " + line(table, "yap", heroLevel, rng);
            HeroFx.particles(level, ParticleTypes.SMOKE, hero.position().add(0, 2, 0), 12, 0.3, 0.02);
        } else {
            boolean twist = verdict.kind() == WagerVerdict.Kind.TWIST;
            if (!nonsense)
                fh.setMana(fh.getMana() - WagerVerdict.inkCost(tier));
            phenomenon = Phenomena.cast(level, hero, intent.id(), tier, twist, aim != null ? aim : autoAim(level, hero),
                parsed.quantity());
            if (nonsense)
                reply = line(table, "nonsense", heroLevel, rng) + " (" + phenomenon + ")";
            else if (twist)
                reply = "Granted, mostly: " + phenomenon + ". " + line(table, "twist", heroLevel, rng);
            else
                reply = "Granted: " + phenomenon + ". " + line(table, tier >= 4 ? "legendary" : "agree", heroLevel, rng);
            HeroFx.particles(level, twist ? ParticleTypes.WITCH : ParticleTypes.WAX_ON, hero.position().add(0, 2, 0), 16, 0.3, 0.05);
        }
        // the flare every side hears within 40 blocks: a wager was cast (design "What opponents see")
        HeroFx.sound(level, hero.position(), SoundEvents.BOOK_PAGE_TURN, 2.0f, 0.8f);

        String request = WagerParser.requestLine(page);
        book.write(request, reply);
        ReignOfNether.LOGGER.info("[Wager] owner={} hero={} level={} line='{}' intent={} asked=T{} verdict={} tier={} "
                + "agree={} twist={} phenomenon='{}' yap={} {}", hero instanceof Unit u ? u.getOwnerName() : "?",
            hero.getId(), heroLevel, parsed.line(), intent.id(), parsed.askedTier(), verdict.kind(), verdict.tier(),
            String.format("%.2f", verdict.agreeChance()), String.format("%.2f", verdict.twistChance()), phenomenon,
            yapped, neutral);
        return new Result(Outcome.CAST, parsed, verdict, phenomenon, reply, yapped, neutral);
    }

    /** One of Lovish's lines for the moment, "{level}" filled in. */
    static String line(WagerIntents table, String key, int heroLevel, Random rng) {
        List<String> lines = table.lines(key);
        return lines.get(rng.nextInt(lines.size())).replace("{level}", String.valueOf(heroLevel));
    }

    /** No aim point came with the ask (the RTS book, typed chat): the nearest enemy in reach, else 6 blocks ahead. */
    static Vec3 autoAim(ServerLevel level, Mob hero) {
        LivingEntity e = HeroFx.nearestEnemy(level, hero, hero.position(), AUTO_AIM_RANGE);
        if (e != null)
            return e.position();
        return hero.position().add(Vec3.directionFromRotation(0, hero.getYRot()).scale(6));
    }
}
