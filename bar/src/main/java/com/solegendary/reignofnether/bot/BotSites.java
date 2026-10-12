package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.startpos.CapturePointServerEvents;
import com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind;
import com.solegendary.reignofnether.unit.EngagementStance;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The bot's capture-site play on the pre-made maps (design/maps.md section 3, design/bot.md "Capture sites"): a
 * squad takes the site worth the most to this bot right now, holds it, comes back for it when it is being
 * neutralized, and retakes it when it is lost. BARbarian has no capture sites, so the priorities follow its
 * expansion rule instead: worth over distance, and never into a fight the squad would lose.
 * <p>
 * Worth by kind and by what the bot is short of: an Ancient Mine is metal (more while metal is short), a Ley Nexus
 * or a Lava Vent is energy (much more while energy is starved), an Ancient Forge is a race to tech (high until
 * Tier 3 is done), a Watchtower or Sunken Bell is sight (prophylaxis: see the push before it lands), a Mercenary Camp
 * is free wolves, an Oasis only heals near it. Score = worth / (1 + distance / 40). A site under threat needs a
 * squad worth 1.5x the enemy value seen round it, and is skipped when that would be more than half the army.
 * <p>
 * Cost: one pass over the site list every {@link #STEP_TICKS} (a few dozen sites at most on a 16-slot map) and one
 * grid query per site that is a real candidate. Units on site duty ({@link #holders}) are left out of the rally and
 * the waves (BotArmy), but a base defence still pulls them home.
 */
public class BotSites {

    /** One decision every 5 s: capture takes 20 s, a faster cadence only re-sends the same squad. */
    static final long STEP_TICKS = 100;
    /** Sites further than this from home are another player's business (130 was the old claimSite reach). */
    static final double RANGE = 150;
    /** Sites this far above or below home are not on our map (and, in the game tests, not on our pad). */
    static final double HEIGHT_BAND = 40;
    /** Fighters on a site that is being taken (a fresh claim) and on one already held. */
    static final int TAKE_SQUAD = 3, HOLD_SQUAD = 2, MAX_SQUAD = 8;
    /** Enemy units within this of a site count as its threat. */
    static final double THREAT_RADIUS = 16;

    final BotPlayer bot;
    long lastStepAt = -1;
    /** Site marker entity id -> the ids of the fighters sent to it. */
    final Map<Integer, Set<Integer>> squads = new HashMap<>();
    /** Every fighter on site duty (the union of {@link #squads}): BotArmy keeps them out of the rally and the waves. */
    public final Set<Integer> holders = new HashSet<>();
    /** The site the last step sent a squad to (null when none): for the game tests and the log. */
    public Entity lastTarget = null;
    private final List<LivingEntity> scratch = new ArrayList<>();
    private final List<LivingEntity> pick = new ArrayList<>();

    BotSites(BotPlayer bot) {
        this.bot = bot;
    }

    /** What a site of this kind is worth to the bot now (see the class comment). */
    float worth(Kind kind) {
        boolean energyStarved = bot.energyFill < 0.3f, metalShort = bot.metalFill < 0.3f;
        return switch (kind) {
            case MINE -> metalShort ? 1.3f : 1.0f;
            case SHRINE, VENT -> energyStarved ? 1.5f : 0.9f;
            case FORGE -> bot.hasTier3 ? 0.6f : 1.2f;
            case WATCHTOWER -> 0.9f;
            case BELL, CAMP -> 0.7f;
            case OASIS -> 0.5f;
        };
    }

    boolean friendly(String owner) {
        return !owner.isEmpty() && (owner.equals(bot.name) || AlliancesServerEvents.isAllied(bot.name, owner));
    }

    /** Enemy metal value within THREAT_RADIUS of the site (one grid query). */
    float threatAt(ServerLevel level, Entity site) {
        float v = 0;
        double r2 = THREAT_RADIUS * THREAT_RADIUS;
        for (LivingEntity le : UnitGrid.near(level, site.getX(), site.getZ(), THREAT_RADIUS, scratch))
            if (bot.isEnemyUnit(le) && le.distanceToSqr(site) <= r2)
                v += BotArmy.metal(le);
        return v;
    }

    /**
     * One step, from BotArmy while it builds up or attacks. {@code fighters} are the bot's fighters (scouts out),
     * {@code raiders} the raid squad's ids (left alone). Returns true when it gave an order.
     */
    public boolean step(ServerLevel level, long gameTime, List<LivingEntity> fighters, Set<Integer> raiders, long minutes) {
        BlockPos home = bot.home;
        if (home == null || (lastStepAt >= 0 && gameTime - lastStepAt < STEP_TICKS))
            return false;
        lastStepAt = gameTime;
        lastTarget = null;
        // forget the dead and the sites that are gone
        Set<Integer> alive = new HashSet<>();
        for (LivingEntity le : fighters)
            alive.add(le.getId());
        holders.clear();
        Iterator<Map.Entry<Integer, Set<Integer>>> it = squads.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Set<Integer>> e = it.next();
            Entity site = level.getEntity(e.getKey());
            e.getValue().retainAll(alive);
            if (site == null || site.isRemoved() || e.getValue().isEmpty()) {
                it.remove();
                continue;
            }
            holders.addAll(e.getValue());
        }
        if (minutes < 2 || fighters.size() < 2 || CapturePointServerEvents.getPoints().isEmpty())
            return false;

        float armyValue = BotArmy.valueOf(fighters);
        // the best site to send to: a held site being neutralized first (defend), then the best one to take or retake
        Entity best = null;
        float bestScore = 0, bestThreat = 0;
        for (Entity site : CapturePointServerEvents.getPoints()) {
            if (site.isRemoved() || site.level() != level || Math.abs(site.getY() - home.getY()) > HEIGHT_BAND)
                continue;
            double dx = site.getX() - home.getX(), dz = site.getZ() - home.getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > RANGE)
                continue;
            String owner = CapturePointServerEvents.ownerOf(site);
            boolean ours = friendly(owner);
            Set<Integer> squad = squads.get(site.getId());
            int have = squad == null ? 0 : squad.size();
            float score;
            if (ours) {
                String claimant = CapturePointServerEvents.claimantOf(site);
                boolean draining = !claimant.isEmpty() && !friendly(claimant) && CapturePointServerEvents.progressOf(site) < 1f;
                if (draining)
                    score = worth(CapturePointServerEvents.kindOf(site)) * 3f;   // defend what we hold
                else if (have < HOLD_SQUAD && holders.size() < Math.max(2, fighters.size() / 4))
                    score = worth(CapturePointServerEvents.kindOf(site)) * 0.5f;   // top up the garrison
                else
                    continue;
            } else {
                score = worth(CapturePointServerEvents.kindOf(site)) * (owner.isEmpty() ? 1f : 1.2f);   // retake: deny it too
            }
            score /= (float) (1 + d / 40);
            if (score <= bestScore)
                continue;
            float threat = threatAt(level, site);
            if (threat * 1.5f > armyValue * 0.5f)
                continue;   // the squad it would take is more than half the army: not worth the fight
            best = site;
            bestScore = score;
            bestThreat = threat;
        }
        if (best == null)
            return false;

        Set<Integer> squad = squads.computeIfAbsent(best.getId(), k -> new HashSet<>());
        boolean held = friendly(CapturePointServerEvents.ownerOf(best));
        int want = held ? HOLD_SQUAD : TAKE_SQUAD;
        // enough value to win the fight there: fighters until the squad is worth 1.5x the threat
        float squadValue = 0;
        for (LivingEntity le : fighters)
            if (squad.contains(le.getId()))
                squadValue += BotArmy.metal(le);
        pick.clear();
        for (LivingEntity le : fighters)
            if (!holders.contains(le.getId()) && !raiders.contains(le.getId()) && !BotRoles.isSiege(le)
                    && le instanceof Unit u && u.isIdle())
                pick.add(le);
        BlockPos at = best.blockPosition();
        pick.sort((a, b) -> Double.compare(a.blockPosition().distSqr(at), b.blockPosition().distSqr(at)));
        for (LivingEntity le : pick) {
            if (squad.size() >= MAX_SQUAD || (squad.size() >= want && squadValue >= bestThreat * 1.5f))
                break;
            squad.add(le.getId());
            squadValue += BotArmy.metal(le);
        }
        if (squad.isEmpty()) {
            squads.remove(best.getId());
            return false;
        }
        holders.addAll(squad);
        // every squad member not standing on the site (capture counts units within RADIUS) walks back onto it
        pick.clear();
        double r = CapturePointServerEvents.RADIUS - 1.5;
        for (LivingEntity le : fighters)
            if (squad.contains(le.getId()) && le.distanceToSqr(best) > r * r)
                pick.add(le);
        lastTarget = best;
        if (pick.isEmpty())
            return false;
        bot.army.stance(pick, EngagementStance.FIRE_AT_WILL);
        bot.order(pick, UnitAction.ATTACK_MOVE, at);
        return true;
    }
}
