package com.solegendary.reignofnether.hero.ai;

import com.solegendary.reignofnether.hero.FactionHero;
import com.solegendary.reignofnether.hero.FactionHeroAbility;
import com.solegendary.reignofnether.hero.HeroKits;

import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * One hero's v2 AI state (design/hero_mode.md "Hero AI"): its {@link OrderLock}, the last {@link Perception} scan, the
 * last {@link FightEval} reading, its {@link DecisionLog} and the top-level state. Kept on the hero's HeroCore, so it
 * lives and dies with the hero (nothing to clean up on a world unload). The decisions themselves are HeroBot's (it
 * extends v1's brain rather than replacing it): HeroBot.think once a second, HeroBot.everyTick every tick for the
 * cheap part - the order check and a combo's follow-up.
 */
public final class HeroBrain {

    /**
     * The heroes v2 drives; every other hero keeps v1 until lovish has played this one (lesson L005: build it for one
     * hero and prove it in game before rolling it out). Rolling out = adding the kit's id here.
     */
    public static final Set<String> ROLLED_OUT = Set.of(HeroKits.DAWN_PALADIN.id);

    public static boolean drives(FactionHero hero) {
        return ROLLED_OUT.contains(hero.kit().id);
    }

    /**
     * OFF: possessed, the AI switched off, or not yet looked at. OBEY: carrying out the player's order. FIGHT: no
     * order, enemies seen. FLEE: near death and threatened, running. REST: near death, out of reach, waiting.
     */
    public enum State { OFF, IDLE, OBEY, FIGHT, FLEE, REST }

    public final OrderLock lock = new OrderLock();
    public final Perception.Snapshot seen = new Perception.Snapshot();
    public final DecisionLog log;
    public FightEval.Reading reading = FightEval.START;
    public State state = State.OFF;

    // a combo in flight: the follow-up to cast the moment the gap closer has landed
    @Nullable public FactionHeroAbility followUp;
    @Nullable public LivingEntity followUpTarget;
    public long followUpUntil;
    /** The hero's cast goal was busy last tick (HeroBot.everyTick: the order's walk resumes the tick a cast is done). */
    public boolean wasCasting;
    /** Where the current flight runs to (a bot's order that sweeps the hero along mid-flight is turned back to it). */
    @Nullable public net.minecraft.core.BlockPos fleeTo;
    /** The game tick the hero went to REST (a bot's hero goes back to its order after HeroBot.BOT_REST_TICKS). */
    public long restSince;

    public HeroBrain(int heroId) {
        this.log = new DecisionLog(heroId);
    }

    public boolean active() {
        return state != State.OFF;
    }

    public boolean fleeing() {
        return state == State.FLEE || state == State.REST;
    }

    public void clearCombo() {
        followUp = null;
        followUpTarget = null;
    }

    /** The player took the hero (possession) or switched the AI off: the brain lets go of everything it started and saw. */
    public void off() {
        state = State.OFF;
        clearCombo();
        seen.clear();   // no hold on units that may die or unload while the player has the hero
    }

    public void record(long tick, DecisionLog.Why why, int slot, int targetId) {
        log.record(tick, state, why, slot, targetId, reading.ratio());
    }
}
