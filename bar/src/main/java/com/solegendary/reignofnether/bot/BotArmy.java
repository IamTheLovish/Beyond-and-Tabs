package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.buildings.shared.MetalExtractor;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.resources.MetalPatches;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.EngagementStance;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The bot's army brain: a small state machine run once per think (about once a second) after the economy step.
 * Everything is measured in army value (the summed metal cost of living fighters, as the match history does), not
 * unit counts, so a T2 army of ten counts like a T1 army of thirty.
 * <pre>
 *   BUILD_UP  fighters gather at a rally point ahead of home; raids and scouting go out from here; when the army's
 *             value crosses the difficulty's threshold (growing with the clock) and the wave cooldown is up, a wave
 *             marches on the best known enemy site                                -> ATTACK
 *   ATTACK    the wave attack-moves to its target, stragglers and reinforcements rejoin it, a fallen target is
 *             replaced by the next known site; a wave that has lost 40%+ of its value and is outweighed on the spot
 *             by retreatRatio pulls back                                           -> RETREAT (or BUILD_UP when done)
 *   RETREAT   the army walks home on Return fire for up to 20 s, then regroups    -> BUILD_UP
 *   DEFEND    (from any state) enemy value at home above defendValue, one of our buildings taking damage, or the
 *             commander being hit, pulls the whole army back onto the intruder; 5 s after the last one is gone
 *             -> BUILD_UP. The attacker of a hit building or commander is the intruder even when the home scan
 *             cannot see it: a flyer above HEIGHT_BAND, artillery past BASE_RADIUS (lovish's Oct 13 2v2: a Celestial
 *             Dragon razed the Sunforged base while forty of its fighters stood in rows beside it, because the hit
 *             only counted once an intruder had been found in the flat scan). A flying intruder is met on the
 *             ground under it (an attack-move at a block in the air is a walk to nowhere).
 * </pre>
 * A single scout at home is met by three idle fighters instead of recalling a wave (the old bot's whole army ran
 * home for any one enemy within 45 blocks, which is why it never pressured anyone).
 * <p>
 * Minimum army: raids, scout errands and capture-site squads wait until {@link #MIN_FIGHTERS} fighters are alive.
 * Before that every fighter the labs make would walk out alone and die (the ally hard bot of Oct 13 lost 33 units
 * for 0 kills with its army value pinned at 0), and a wave only ever goes above the difficulty's threshold anyway.
 *
 * No map hack: a wave only targets enemy buildings one of the bot's units or buildings has come within sight of
 * ({@link #knownSites}, refreshed every 5 s), and the scout is what finds them. The fallback after
 * {@link BotTuning#blindAttackMinute} marches on the nearest enemy building regardless, because start positions
 * are public in the lobby and a bot that never finds the enemy is no opponent.
 *
 * Cost per think: one grid query at home, one more at the army's centre only while a wave is out, and the
 * sightings pass (a few hundred flat distances) every 5 s.
 */
public class BotArmy {

    public enum State { BUILD_UP, ATTACK, DEFEND, RETREAT }

    /** The nearest enemy inside the base on the last army step (null if none) - diagnostics for tests and logs. */
    public LivingEntity lastIntruder;
    /** Why the last step launched no wave (one short phrase for the army log; "" when a wave is out). */
    public String noWaveReason = "";

    /** Enemy units within this many blocks of home count as a threat to the base. */
    public static final double BASE_RADIUS = 45;
    /** How far a bot unit or building "sees" an enemy building for {@link #knownSites} (a unit's sight, roughly). */
    static final double SIGHT = 28;
    /** The circle round the army's centre the retreat check weighs both sides in. */
    static final double FIGHT_RADIUS = 24;
    /** Units more than this far above or below the point of interest are not part of the fight there. */
    static final double HEIGHT_BAND = 24;
    /** The rally point sits this far ahead of home toward the enemy. */
    static final int RALLY_DIST = 30;
    /** A wave whose value has fallen under this share of what it set out with may retreat. */
    static final float RETREAT_LOSS = 0.6f;
    /** A base that floats army past this multiple of the threshold launches regardless of the wave cooldown. */
    static final float OVERFLOW_WAVE = 2.5f;
    /** No raid, scout errand or site squad until this many fighters are alive (see the class comment). */
    public static final int MIN_FIGHTERS = 6;
    /** A commander hit by an enemy within this many ticks counts as under attack. */
    static final int COMMANDER_HIT_TICKS = 60;
    /** A wave whose centre is this far from home is at the enemy's door and may keep going through a home alarm. */
    static final double AWAY_RADIUS = BASE_RADIUS * 1.5;

    final BotPlayer bot;
    final BotTuning tuning;

    public State state = State.BUILD_UP;
    long stateSince = 0;
    /** Game times of the last wave, raid, scout errand, rally order, army order, sightings pass, threat, squad; -1 = never. */
    public long lastAttackAt = -1;
    long lastRaidAt = -1, lastScoutAt = -1, lastRallyAt = -1, lastOrderAt = -1, lastSightingAt = -1, lastThreatAt = -1,
        lastSquadAt = -1, raidSince = -1;
    float waveStartValue = 0;
    public BlockPos waveTarget = null;
    /** The army's metal value at the last think (the game tests and the log read it). */
    public float armyValue = 0;
    /** Enemy buildings our units or buildings have seen, by origin: the only targets a wave or raid may pick. */
    public final Map<BlockPos, Building> knownSites = new LinkedHashMap<>();
    /** The nearest enemy building to home we have not seen yet (what the scout heads for), from the last sightings pass. */
    BlockPos nearestUnseen = null;
    /** True once a known site is a static defence: the labs then add siege to the mix. */
    public boolean defencesSeen = false;
    final Map<BuildingPlacement, Integer> lastHealth = new IdentityHashMap<>();
    int scoutId = -1;
    double scoutAngle = 0;
    final Set<Integer> raidIds = new HashSet<>();
    /** Reused per think: fighters (the army minus scouts) and the idle ones among them. */
    final List<LivingEntity> fighters = new ArrayList<>(), idle = new ArrayList<>();

    public BotArmy(BotPlayer bot, BotTuning tuning) {
        this.bot = bot;
        this.tuning = tuning;
    }

    /** The metal a unit cost, or 0 for one without a cost. */
    static float metal(LivingEntity le) {
        if (!(le instanceof Unit u))
            return 0;
        ResourceCost cost = u.getCost();
        return cost == null ? 0 : cost.metal();
    }

    /** Summed metal cost of the given units (scouts excluded, as the army counts them). */
    public static float valueOf(List<LivingEntity> units) {
        float v = 0;
        for (LivingEntity le : units)
            if (!BotRoles.isScout(le))
                v += metal(le);
        return v;
    }

    /**
     * Records an enemy site as seen, as the sightings pass would, and counts that pass as just run (the next one is
     * 5 s away). For the game tests, which hand the brain a site without an enemy player behind it.
     */
    public void seedSite(BlockPos origin, Building building, long gameTime) {
        knownSites.put(origin, building);
        lastSightingAt = gameTime;
    }

    void enter(State next, long gameTime) {
        if (state != next) {
            state = next;
            stateSince = gameTime;
        }
    }

    /** The army step of a think (see the class comment). {@code army} is the bot's living AttackerUnits. */
    public void run(ServerLevel level, long gameTime, List<LivingEntity> mine, List<LivingEntity> army,
                    List<BuildingPlacement> buildings, long minutes) {
        BlockPos home = bot.home;
        if (home == null)
            return;
        if (lastSightingAt < 0 || gameTime - lastSightingAt >= 100) {
            lastSightingAt = gameTime;
            updateKnownSites(mine, buildings);
        }
        fighters.clear();
        float value = 0;
        for (LivingEntity le : army) {
            if (BotRoles.isScout(le))
                continue;
            fighters.add(le);
            value += metal(le);
        }
        armyValue = value;
        scout(level, gameTime, mine, buildings, home);

        // 1) the base: enemy value within BASE_RADIUS of home, and whether any of our buildings is taking damage
        float threat = 0;
        LivingEntity intruder = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity other : UnitGrid.near(level, home.getX() + 0.5, home.getZ() + 0.5, BASE_RADIUS + 1, bot.scan)) {
            // (the grid is flat: a unit far above or below - a flyer, a cave, another arena in the test world - is
            // not at the base)
            if (!bot.isEnemyUnit(other) || Math.abs(other.getY() - home.getY()) > HEIGHT_BAND)
                continue;
            double dx = other.getX() - home.getX() - 0.5, dz = other.getZ() - home.getZ() - 0.5;
            double d = dx * dx + dz * dz;
            if (d > BASE_RADIUS * BASE_RADIUS)
                continue;
            threat += metal(other);
            if (d < best) {
                best = d;
                intruder = other;
            }
        }
        // a building losing health or the commander taking a hit is an alarm on its own, and whoever did it is the
        // intruder when the flat scan found none (a flyer above the band, artillery or a flyer beyond BASE_RADIUS)
        BuildingPlacement hitBuilding = buildingsHit(buildings);
        LivingEntity attacker = hitBuilding == null ? null : enemyOrNull(hitBuilding.lastAttacker);
        if (attacker == null)
            attacker = commanderAttacker(mine);
        boolean hit = hitBuilding != null || attacker != null;
        if (intruder == null && attacker != null) {
            intruder = attacker;
            threat += metal(attacker);
        }
        lastIntruder = intruder;
        if (intruder != null) {
            lastThreatAt = gameTime;
            if (intruder instanceof Unit iu && iu.isFlyingUnit())
                bot.flyerAtHome(gameTime);   // the labs answer with anti-air (queueAntiAir) without waiting for the scan
            // a real push (or anything at all once it draws blood): everyone home. A wave already on the enemy's
            // doorstep keeps going only if it is big enough to end the game first - and only while it is actually
            // away; an army standing at home is a home guard whatever its state says
            boolean push = threat >= Math.min(tuning.defendValue(), Math.max(60f, value * 0.5f)) || hit;
            boolean away = state == State.ATTACK && value >= tuning.attackValue(minutes) * OVERFLOW_WAVE
                && !fighters.isEmpty() && !centreWithin(fighters, home, AWAY_RADIUS);
            if (push && !away) {
                enter(State.DEFEND, gameTime);
                if (lastOrderAt < 0 || gameTime - lastOrderAt >= 40) {
                    lastOrderAt = gameTime;
                    stance(fighters, EngagementStance.FIRE_AT_WILL);
                    // only the fighters not already in a fight: a new order resets a unit's behaviours (its target,
                    // a melee windup in progress, its path), and this repeats every 2 s for as long as the intruder
                    // is near - the whole home guard dropped its fight every 40 ticks (lovish's Oct 22 1v1, "units
                    // weren't attacking each other"). A unit whose fight ends picks up the next order 2 s later.
                    List<LivingEntity> free = new ArrayList<>(fighters.size());
                    for (LivingEntity le : fighters)
                        if (!inFight(le))
                            free.add(le);
                    bot.order(free, UnitAction.ATTACK_MOVE, meetingPoint(level, intruder));
                }
                return;
            }
            if (state != State.DEFEND && (lastSquadAt < 0 || gameTime - lastSquadAt >= 60)) {
                // a lone scout or raider: the three nearest idle fighters see it off, the rest stay on their job
                lastSquadAt = gameTime;
                BlockPos at = meetingPoint(level, intruder);
                collectIdle(fighters, at, 3, true);
                bot.order(idle, UnitAction.ATTACK_MOVE, at);
            }
        }

        // 1b) capture sites (BotSites): a squad takes, holds, defends and retakes the map's sites. Its fighters are
        // then left out of the rally and the waves (a held site stays held); a base defence above still takes them
        if (state == State.BUILD_UP || state == State.ATTACK) {
            bot.sites.step(level, gameTime, fighters, raidIds, minutes);
            if (!bot.sites.holders.isEmpty())
                fighters.removeIf(le -> bot.sites.holders.contains(le.getId()));
        }

        // 2) the state machine
        switch (state) {
            case DEFEND -> {
                if (gameTime - lastThreatAt >= 100)
                    enter(State.BUILD_UP, gameTime);
            }
            case RETREAT -> {
                BlockPos rally = rallyPoint(level, home);
                if (gameTime - stateSince >= 20 * 20 || fighters.isEmpty() || centreWithin(fighters, rally, 10)) {
                    stance(fighters, EngagementStance.FIRE_AT_WILL);
                    enter(State.BUILD_UP, gameTime);
                }
            }
            case ATTACK -> attack(level, gameTime, home, value, minutes);
            case BUILD_UP -> buildUp(level, gameTime, home, value, minutes);
        }
    }

    void buildUp(ServerLevel level, long gameTime, BlockPos home, float value, long minutes) {
        int threshold = tuning.attackValue(minutes);
        boolean cooling = lastAttackAt >= 0 && gameTime - lastAttackAt < tuning.attackCooldownTicks();
        boolean ready = value >= threshold && !cooling;
        boolean overflow = value >= threshold * OVERFLOW_WAVE;
        if ((ready || overflow) && !fighters.isEmpty()) {
            BlockPos target = pickTarget(home, minutes);
            if (target != null) {
                noWaveReason = "";
                waveTarget = target;
                waveStartValue = value;
                lastAttackAt = gameTime;
                lastOrderAt = gameTime;
                enter(State.ATTACK, gameTime);
                stance(fighters, EngagementStance.FIRE_AT_WILL);
                bot.order(fighters, UnitAction.ATTACK_MOVE, target);
                return;
            }
            noWaveReason = "no target known (" + knownSites.size() + " sites seen, blind attack from minute "
                + tuning.blindAttackMinute() + ")";
        } else if (fighters.isEmpty())
            noWaveReason = "no fighters alive";
        else if (value < threshold)
            noWaveReason = "value " + (int) value + " under the wave threshold " + threshold + " (" + fighters.size()
                + " fighters, " + bot.sites.holders.size() + " on site duty, " + raidIds.size() + " raiding)";
        else
            noWaveReason = "wave cooldown, " + (tuning.attackCooldownTicks() - (gameTime - lastAttackAt)) / 20 + " s left";
        raid(level, gameTime, home);
        // gather idle fighters at the rally point (not the raid squad while its raid is young)
        if ((lastRallyAt < 0 || gameTime - lastRallyAt >= 160) && !fighters.isEmpty()) {
            lastRallyAt = gameTime;
            BlockPos rally = rallyPoint(level, home);
            idle.clear();
            boolean raiding = raidSince >= 0 && gameTime - raidSince < 20 * 60;
            for (LivingEntity le : fighters)
                if (le instanceof Unit u && u.isIdle() && le.blockPosition().distSqr(rally) > 12 * 12
                        && !(raiding && raidIds.contains(le.getId())))
                    idle.add(le);
            bot.order(idle, UnitAction.ATTACK_MOVE, rally);
        }
    }

    void attack(ServerLevel level, long gameTime, BlockPos home, float value, long minutes) {
        if (fighters.isEmpty()) {
            enter(State.BUILD_UP, gameTime);
            return;
        }
        if (waveTarget == null || !stands(waveTarget)) {
            waveTarget = pickTarget(home, minutes);
            if (waveTarget == null) {
                enter(State.BUILD_UP, gameTime);
                return;
            }
            lastOrderAt = gameTime;
            bot.order(fighters, UnitAction.ATTACK_MOVE, waveTarget);
            return;
        }
        // losing badly: the wave has bled past RETREAT_LOSS and is outweighed on the spot
        if (tuning.retreatRatio() > 0 && value < waveStartValue * RETREAT_LOSS) {
            double cx = 0, cz = 0;
            for (LivingEntity le : fighters) { cx += le.getX(); cz += le.getZ(); }
            cx /= fighters.size();
            cz /= fighters.size();
            float ours = 0, theirs = 0;
            double cy = fighters.get(0).getY();
            for (LivingEntity other : UnitGrid.near(level, cx, cz, FIGHT_RADIUS, bot.scan)) {
                double dx = other.getX() - cx, dz = other.getZ() - cz;
                if (dx * dx + dz * dz > FIGHT_RADIUS * FIGHT_RADIUS || Math.abs(other.getY() - cy) > HEIGHT_BAND)
                    continue;
                if (bot.isEnemyUnit(other))
                    theirs += metal(other);
                else if (other instanceof Unit ou && bot.name.equals(ou.getOwnerName()))
                    ours += metal(other);
            }
            if (theirs > ours * tuning.retreatRatio()) {
                enter(State.RETREAT, gameTime);
                stance(fighters, EngagementStance.RETURN_FIRE);
                bot.order(fighters, UnitAction.MOVE, rallyPoint(level, home));
                return;
            }
        }
        // stragglers and reinforcements rejoin the wave
        if (lastOrderAt < 0 || gameTime - lastOrderAt >= 200) {
            lastOrderAt = gameTime;
            idle.clear();
            for (LivingEntity le : fighters)
                if (le instanceof Unit u && u.isIdle() && le.blockPosition().distSqr(waveTarget) > 14 * 14)
                    idle.add(le);
            bot.order(idle, UnitAction.ATTACK_MOVE, waveTarget);
        }
    }

    /**
     * A raid: every raidCooldown, the fastest idle fighters attack-move to the best known enemy extractor (nearest,
     * weighted by its patch's value) that is not next to a known defence, so an opponent who leaves expansions bare pays for it (what BARbarian's raiders do).
     */
    void raid(ServerLevel level, long gameTime, BlockPos home) {
        if (tuning.raidCooldownTicks() <= 0 || (lastRaidAt >= 0 && gameTime - lastRaidAt < tuning.raidCooldownTicks()))
            return;
        if (fighters.size() < MIN_FIGHTERS)
            return;   // (not a "tried": the next raid is wanted as soon as the army exists)
        BlockPos target = null;
        double bestD = Double.MAX_VALUE;
        for (Map.Entry<BlockPos, Building> e : knownSites.entrySet()) {
            if (!(e.getValue() instanceof MetalExtractor))
                continue;
            boolean covered = false;
            for (Map.Entry<BlockPos, Building> d : knownSites.entrySet())
                if ((BotRoles.isDefence(d.getValue()) || d.getValue().isCapitol) && d.getKey().distSqr(e.getKey()) < 30 * 30) {
                    covered = true;
                    break;
                }
            // a rich patch is the better raid (design/maps.md: 1.5x and 2.0x patches): distance over the patch's
            // value, as the expansion rule weighs our own patches (the extractor's centre is its origin + 2)
            BlockPos o = e.getKey();
            double dist = Math.sqrt(o.distSqr(home)) / MetalPatches.valueNear(level, o.getX() + 2, o.getY(), o.getZ() + 2);
            if (!covered && dist < bestD) {
                bestD = dist;
                target = e.getKey();
            }
        }
        if (target == null)
            return;
        lastRaidAt = gameTime;   // tried: a bare extractor out of reach is not retried every think
        idle.clear();
        for (LivingEntity le : fighters)
            if (le instanceof Unit u && u.isIdle() && !BotRoles.isSiege(le))
                idle.add(le);
        if (idle.size() < tuning.raidSize())
            return;
        idle.sort((a, b) -> Double.compare(b.getAttributeValue(Attributes.MOVEMENT_SPEED), a.getAttributeValue(Attributes.MOVEMENT_SPEED)));
        while (idle.size() > tuning.raidSize())
            idle.remove(idle.size() - 1);
        raidIds.clear();
        for (LivingEntity le : idle)
            raidIds.add(le.getId());
        raidSince = gameTime;
        stance(idle, EngagementStance.FIRE_AT_WILL);
        bot.order(idle, UnitAction.ATTACK_MOVE, target);
    }

    /**
     * Scouting: one scout-role unit (the faction's cheapest and fastest, trained at the capitol every scoutEvery
     * ticks when the last one is dead) walks to the nearest enemy building we have not seen yet, else sweeps a ring
     * 100 blocks out round home. What it passes within SIGHT of becomes a known site.
     */
    void scout(ServerLevel level, long gameTime, List<LivingEntity> mine, List<BuildingPlacement> buildings, BlockPos home) {
        if (tuning.scoutEveryTicks() <= 0)
            return;
        LivingEntity scout = null;
        Entity e = scoutId < 0 ? null : level.getEntity(scoutId);
        if (e instanceof LivingEntity le && le.isAlive() && le instanceof Unit u && bot.name.equals(u.getOwnerName()))
            scout = le;
        if (scout == null) {
            scoutId = -1;
            for (LivingEntity le : mine)
                // (not the Owl Watcher: verdantHabits parks it over the home choke as a picket)
                if (BotRoles.isScout(le) && !(le instanceof com.solegendary.reignofnether.unit.units.verdant.OwlWatcherUnit)) {
                    scout = le;
                    scoutId = le.getId();
                    break;
                }
        }
        if (scout == null) {
            if (lastScoutAt >= 0 && gameTime - lastScoutAt < tuning.scoutEveryTicks())
                return;
            // a scout runs on Hold fire and dies to the first tower it meets: not one after another from a base with
            // no army behind it (blindAttackMinute finds the enemy for the first wave regardless)
            if (fighters.size() < MIN_FIGHTERS)
                return;
            ProductionItem item = BotRoles.scoutFor(bot.faction);
            if (item == null)
                return;
            for (BuildingPlacement bp : buildings)
                if (bp.isBuilt && bp.getBuilding().isCapitol && bp instanceof ProductionPlacement pp && pp.productionQueue.size() < 2) {
                    for (var q : pp.productionQueue)
                        if (q.item == item)
                            return;
                    if (pp.startProductionItem(item))
                        lastScoutAt = gameTime;
                    return;
                }
            return;
        }
        if (!(scout instanceof Unit su) || !su.isIdle())
            return;
        BlockPos to = nearestUnseen;
        if (to == null) {
            scoutAngle += 1.3;
            to = BotPlayer.ground(level, home.getX() + (int) (Math.cos(scoutAngle) * 100), home.getZ() + (int) (Math.sin(scoutAngle) * 100));
        }
        stance(List.of(scout), EngagementStance.HOLD_FIRE);   // a scout runs, it does not pick fights
        bot.order(List.of(scout), UnitAction.MOVE, to);
    }

    /**
     * Sightings: every enemy building within SIGHT of one of our units (sampled down to ~16) or buildings becomes a
     * known site; sites whose building is gone are forgotten; the nearest unseen one is where the scout goes next.
     */
    void updateKnownSites(List<LivingEntity> mine, List<BuildingPlacement> buildings) {
        List<BlockPos> eyes = new ArrayList<>(24);
        int stride = Math.max(1, mine.size() / 16);
        for (int i = 0; i < mine.size(); i += stride)
            eyes.add(mine.get(i).blockPosition());
        Entity sc = scoutId < 0 ? null : (mine.isEmpty() ? null : mine.get(0).level().getEntity(scoutId));
        if (sc != null)
            eyes.add(sc.blockPosition());
        int bstride = Math.max(1, buildings.size() / 8);
        for (int i = 0; i < buildings.size(); i += bstride)
            eyes.add(buildings.get(i).originPos);
        Set<BlockPos> live = new HashSet<>();
        BlockPos unseen = null;
        double unseenD = Double.MAX_VALUE;
        BlockPos home = bot.home;
        for (BuildingPlacement bp : BuildingServerEvents.getBuildings()) {
            if (bp.isDestroyedServerside || bp.ownerName == null || bp.ownerName.isEmpty() || bp.ownerName.equals(bot.name)
                    || AlliancesServerEvents.isAllied(bot.name, bp.ownerName) || !PlayerServerEvents.isRTSPlayer(bp.ownerName))
                continue;
            live.add(bp.originPos);
            if (knownSites.containsKey(bp.originPos))
                continue;
            boolean seen = false;
            for (BlockPos eye : eyes) {
                double dx = eye.getX() - bp.originPos.getX(), dz = eye.getZ() - bp.originPos.getZ();
                if (dx * dx + dz * dz <= SIGHT * SIGHT) {
                    seen = true;
                    break;
                }
            }
            if (seen) {
                knownSites.put(bp.originPos, bp.getBuilding());
                if (BotRoles.isDefence(bp.getBuilding()))
                    defencesSeen = true;
            } else if (home != null) {
                double d = bp.originPos.distSqr(home);
                if (d < unseenD) {
                    unseenD = d;
                    unseen = bp.originPos;
                }
            }
        }
        knownSites.keySet().removeIf(p -> !live.contains(p));
        nearestUnseen = unseen;
    }

    /** True while an enemy building still stands at the position. */
    static boolean stands(BlockPos pos) {
        BuildingPlacement bp = BuildingUtils.findBuilding(false, pos);
        return bp != null && !bp.isDestroyedServerside;
    }

    /**
     * The wave's target: the nearest known enemy capitol, else the nearest known site; none known and the match past
     * blindAttackMinute, the nearest enemy building outright (see the class comment).
     */
    BlockPos pickTarget(BlockPos home, long minutes) {
        BlockPos best = null, bestCap = null;
        double bestD = Double.MAX_VALUE, bestCapD = Double.MAX_VALUE;
        for (Map.Entry<BlockPos, Building> e : knownSites.entrySet()) {
            double d = e.getKey().distSqr(home);
            if (d < bestD) { bestD = d; best = e.getKey(); }
            if (e.getValue().isCapitol && d < bestCapD) { bestCapD = d; bestCap = e.getKey(); }
        }
        if (bestCap != null)
            return bestCap;
        if (best != null)
            return best;
        return minutes >= tuning.blindAttackMinute() ? bot.nearestEnemyBuilding(home) : null;
    }

    /** RALLY_DIST blocks from home toward the nearest known enemy site (or the nearest enemy building), on the ground. */
    BlockPos rallyPoint(ServerLevel level, BlockPos home) {
        BlockPos toward = pickTarget(home, Long.MAX_VALUE);
        if (toward == null)
            return home;
        double dx = toward.getX() - home.getX(), dz = toward.getZ() - home.getZ();
        double len = Math.max(1, Math.hypot(dx, dz));
        return BotPlayer.ground(level, home.getX() + (int) (dx / len * RALLY_DIST), home.getZ() + (int) (dz / len * RALLY_DIST));
    }

    static boolean centreWithin(List<LivingEntity> units, BlockPos at, double r) {
        double cx = 0, cz = 0;
        for (LivingEntity le : units) { cx += le.getX(); cz += le.getZ(); }
        cx = cx / units.size() - at.getX() - 0.5;
        cz = cz / units.size() - at.getZ() - 0.5;
        return cx * cx + cz * cz <= r * r;
    }

    /**
     * A standing building of ours with less health than at the last think (a hit, not a new site), or null. With
     * several, the one whose attacker is known (its lastAttacker, set by the building's own damage path), so the
     * defence has someone to meet.
     */
    BuildingPlacement buildingsHit(List<BuildingPlacement> buildings) {
        BuildingPlacement hit = null;
        for (BuildingPlacement bp : buildings) {
            int hp = bp.getHealth();
            Integer last = lastHealth.put(bp, hp);
            if (last != null && bp.isBuilt && hp < last && (hit == null || (hit.lastAttacker == null && bp.lastAttacker != null)))
                hit = bp;
        }
        if (lastHealth.size() > buildings.size() * 2 + 8)
            lastHealth.keySet().removeIf(bp -> bp.isDestroyedServerside || !buildings.contains(bp));
        return hit;
    }

    /** The entity as a living enemy unit of this bot, or null (a wild mob, our own or an ally's unit is no intruder). */
    LivingEntity enemyOrNull(Entity e) {
        return e instanceof LivingEntity le && bot.isEnemyUnit(le) ? le : null;
    }

    /** The enemy unit that hit one of our commanders within COMMANDER_HIT_TICKS, or null. */
    LivingEntity commanderAttacker(List<LivingEntity> mine) {
        for (LivingEntity le : mine) {
            if (!com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(le))
                continue;
            LivingEntity by = le.getLastHurtByMob();
            if (by != null && le.tickCount - le.getLastHurtByMobTimestamp() <= COMMANDER_HIT_TICKS)
                return enemyOrNull(by);
        }
        return null;
    }

    /**
     * Where the army goes to meet an intruder: its own block, or for a flyer the ground under it - an attack-move at
     * a block in the air is a path to nowhere, and the fighters that can shoot up (the mixed kits' archers and
     * pikemen, the AA units queueAntiAir adds) only do so from underneath.
     */
    static BlockPos meetingPoint(ServerLevel level, LivingEntity intruder) {
        if (intruder instanceof Unit u && u.isFlyingUnit())
            return BotPlayer.ground(level, intruder.blockPosition().getX(), intruder.blockPosition().getZ());
        return intruder.blockPosition();
    }

    /** Fills {@link #idle} with up to n idle fighters nearest to the position. */
    void collectIdle(List<LivingEntity> from, BlockPos near, int n, boolean nearestFirst) {
        idle.clear();
        for (LivingEntity le : from)
            if (le instanceof Unit u && u.isIdle())
                idle.add(le);
        if (nearestFirst)
            idle.sort((a, b) -> Double.compare(a.blockPosition().distSqr(near), b.blockPosition().distSqr(near)));
        while (idle.size() > n)
            idle.remove(idle.size() - 1);
    }

    /** Sets a fire state on the units (applied at once, like a player's toggle; skipped for units already in it). */
    /** Fighting right now: a live unit target, or a building it is hitting. */
    static boolean inFight(LivingEntity le) {
        if (le instanceof net.minecraft.world.entity.Mob m && m.getTarget() != null && m.getTarget().isAlive())
            return true;
        return le instanceof com.solegendary.reignofnether.unit.interfaces.AttackerUnit au
            && com.solegendary.reignofnether.unit.interfaces.AttackerUnit.isAttackingBuilding(au);
    }

    void stance(List<LivingEntity> units, EngagementStance s) {
        int n = 0;
        for (LivingEntity le : units)
            if (EngagementStance.of(le) != s)
                n++;
        if (n == 0)
            return;
        int[] ids = new int[n];
        int i = 0;
        for (LivingEntity le : units)
            if (EngagementStance.of(le) != s)
                ids[i++] = le.getId();
        BlockPos at = bot.home == null ? BlockPos.ZERO : bot.home;
        UnitServerEvents.addActionItem(bot.name, s.action(), -1, ids, at, at);
    }
}
