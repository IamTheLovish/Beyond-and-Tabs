package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.BoneGraft;
import com.solegendary.reignofnether.ability.abilities.Bulwark;
import com.solegendary.reignofnether.ability.abilities.MendingRite;
import com.solegendary.reignofnether.ability.abilities.Overdrive;
import com.solegendary.reignofnether.ability.abilities.Rootwalk;
import com.solegendary.reignofnether.ability.abilities.SalvageHook;
import com.solegendary.reignofnether.ability.abilities.TearDown;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.WreckServerEvents;
import com.solegendary.reignofnether.unit.CombatEngineers;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.CombatEngineer;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.unit.units.engineers.PioneerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The bots' combat engineers (design/combat_engineers.md "Bots"). Kept out of BotPlayer, which only calls
 * {@link #think} from one line once it has sorted its units and buildings. Per think it:
 * <ol>
 *   <li>takes the engineers out of the worker list, so the economy rules never hand one an extractor or a lab to place
 *       (the server refuses an engineer that) and the worker count stays a count of workers;</li>
 *   <li>keeps a few in training at the T2 lab once the army is worth escorting: none on easy, one on medium, two on
 *       hard, one more per {@link #ARMY_PER_EXTRA} fighters, at most {@link #MAX_ENGINEERS};</li>
 *   <li>every {@link #ORDER_TICKS}: an idle engineer reclaims the nearest wreck near the army, else walks up a few
 *       blocks behind it - its field repair (CombatEngineers.tickFieldRepair) then mends whatever is hurt around it;</li>
 *   <li>now and then, while the army gathers, has them raise an anti-raid tower at the rally point (BAR: the builders
 *       that follow the army throw up a defence where it stands);</li>
 *   <li>casts each engineer's faction ability when its trigger holds. The cheap tests come first and the scans run only
 *       for an engineer off cooldown, so a quiet army costs a few checks.</li>
 * </ol>
 * Server thread only (BotPlayer.think).
 */
public final class BotCombatEngineers {
    private BotCombatEngineers() { }

    /** No engineers before the army has this many units: they escort an army, they don't replace one. */
    static final int MIN_ARMY = 6;
    static final int ARMY_PER_EXTRA = 12;
    static final int MAX_ENGINEERS = 3;
    /** Orders (reclaim, follow, the forward tower) at most this often per bot. */
    static final int ORDER_TICKS = 40;
    /** Where an engineer stands: this far behind the army's centre, toward home. */
    static final double TRAIL = 6;
    /** An engineer this far from its follow point (and not already walking there) is sent after the army. */
    static final double LAG = 10;
    /** Wrecks an idle engineer goes for: within this of itself and of the army's centre. */
    static final double WRECK_REACH = 14;
    /** A forward tower at most this often (hard) / twice as often apart (medium). */
    static final long TOWER_EVERY_TICKS = 20 * 120;
    /** One forward tower per rally point: none if an own anti-raid tower already stands within this of it. */
    static final double TOWER_COVER = 14;

    /** Per bot: when it last ordered, when it last laid a tower, its engineers this think, and a reused unit scan. */
    static final class State {
        long lastOrderAt = -1, lastTowerAt = -1;
        final List<LivingEntity> engineers = new ArrayList<>();
        final List<LivingEntity> scan = new ArrayList<>();
        final List<LivingEntity> lagging = new ArrayList<>();
    }

    // keyed by the brain itself, weakly: a bot removed at the end of a match takes its state with it
    private static final Map<BotPlayer, State> STATES = new WeakHashMap<>();

    /** The faction's combat engineer (design/combat_engineers.md), or null for a faction without one. */
    @Nullable
    public static ProductionItem engineerFor(@Nullable Faction faction) {
        if (faction == null) return null;
        if (faction.equals(Factions.VILLAGERS)) return ProductionItems.FIELD_MASON;
        if (faction.equals(Factions.MONSTERS)) return ProductionItems.BONESETTER;
        if (faction.equals(Factions.PIGLINS)) return ProductionItems.WRECKER;
        if (faction.equals(Factions.VERDANT_COURT)) return ProductionItems.ROOTWARDEN;
        if (faction.equals(Factions.TIDEWROUGHT)) return ProductionItems.SALVAGER;
        if (faction.equals(Factions.STORMCALLERS)) return ProductionItems.JADE_MENDER;
        if (faction.equals(Factions.BRASSWORKS)) return ProductionItems.PIONEER;
        return null;
    }

    /**
     * The hook (BotPlayer.think). Removes this bot's engineers from {@code workers} - always, even when the rest is
     * skipped - then trains, orders and casts as described on the class. {@code army} is the bot's living fighters
     * (AttackerUnits that are not workers), {@code buildings} its own standing buildings.
     */
    public static void think(BotPlayer bot, ServerLevel level, long gameTime, List<LivingEntity> workers,
                             List<LivingEntity> army, List<BuildingPlacement> buildings) {
        State st = STATES.computeIfAbsent(bot, b -> new State());
        st.engineers.clear();
        for (int i = workers.size() - 1; i >= 0; i--) {
            LivingEntity le = workers.get(i);
            if (le instanceof CombatEngineer) {
                st.engineers.add(le);
                workers.remove(i);
            }
        }
        if (bot.kit == null || bot.home == null)
            return;
        train(bot, st.engineers.size(), army, buildings);
        if (st.engineers.isEmpty())
            return;
        for (int i = 0, n = st.engineers.size(); i < n; i++)
            useAbility(bot, level, st, st.engineers.get(i), buildings);
        if (st.lastOrderAt >= 0 && gameTime - st.lastOrderAt < ORDER_TICKS)
            return;
        st.lastOrderAt = gameTime;
        order(bot, level, st, army);
        forwardTower(bot, level, gameTime, st, buildings);
    }

    /** Queues the next engineer at the first built T2 lab when the bot has fewer (trained and queued) than it wants. */
    static void train(BotPlayer bot, int have, List<LivingEntity> army, List<BuildingPlacement> buildings) {
        if (bot.difficulty == BotPlayer.Difficulty.EASY || bot.kit.t2Lab() == null || army.size() < MIN_ARMY)
            return;
        ProductionItem item = engineerFor(bot.faction);
        if (item == null || !ResearchServerEvents.playerHasResearch(bot.name, ProductionItems.RESEARCH_TIER_2))
            return;
        int want = Math.min(MAX_ENGINEERS,
            (bot.difficulty == BotPlayer.Difficulty.HARD ? 2 : 1) + army.size() / ARMY_PER_EXTRA);
        ProductionPlacement lab = null;
        for (BuildingPlacement bp : buildings) {
            if (bp.getBuilding() != bot.kit.t2Lab() || !(bp instanceof ProductionPlacement pp))
                continue;
            for (var queued : pp.productionQueue)
                if (queued.item == item)
                    have++;
            if (lab == null && bp.isBuilt && !bp.decommissioning)
                lab = pp;
        }
        if (lab != null && have < want)
            lab.startProductionItem(item);
    }

    /**
     * The army's centre (scouts left out: one scout across the map would drag it), moved {@link #TRAIL} blocks toward
     * home and put on the ground; home when there is no army.
     */
    static BlockPos followPoint(BotPlayer bot, ServerLevel level, List<LivingEntity> army) {
        double sx = 0, sz = 0;
        int n = 0;
        for (LivingEntity le : army) {
            if (BotRoles.isScout(le))
                continue;
            sx += le.getX();
            sz += le.getZ();
            n++;
        }
        if (n == 0)
            return bot.home;
        double cx = sx / n, cz = sz / n;
        double dx = bot.home.getX() + 0.5 - cx, dz = bot.home.getZ() + 0.5 - cz;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > TRAIL) {
            cx += dx / len * TRAIL;
            cz += dz / len * TRAIL;
        }
        return BotPlayer.ground(level, (int) Math.floor(cx), (int) Math.floor(cz));
    }

    /**
     * Idle engineers: the nearest wreck within {@link #WRECK_REACH} of the engineer and of the army is reclaimed (an
     * explicit RECLAIM order, as sendReclaimer gives workers); otherwise an engineer far from the follow point walks
     * there, in one MOVE order for all of them. Busy ones (building, repairing, reclaiming, sinking into the roots or
     * channelling a rite) are left alone: a MOVE would cancel what they are doing.
     */
    static void order(BotPlayer bot, ServerLevel level, State st, List<LivingEntity> army) {
        BlockPos follow = followPoint(bot, level, army);
        st.lagging.clear();
        for (int i = 0, n = st.engineers.size(); i < n; i++) {
            LivingEntity le = st.engineers.get(i);
            // (Unit.isIdle covers a worker's jobs too: building, repairing, an explicit reclaim order, walking)
            if (!(le instanceof Unit u) || !u.isIdle() || Rootwalk.walkOf(le) != null || MendingRite.riteOf(le) != null)
                continue;
            Entity wreck = wreckFor(le, follow);
            if (wreck != null) {
                UnitServerEvents.addActionItem(bot.name, UnitAction.RECLAIM, wreck.getId(), new int[] { le.getId() },
                    wreck.blockPosition(), wreck.blockPosition());
                continue;
            }
            BlockPos mt = u.getMoveGoal() == null ? null : u.getMoveGoal().getMoveTarget();
            if (le.distanceToSqr(follow.getX() + 0.5, le.getY(), follow.getZ() + 0.5) > LAG * LAG
                    && (mt == null || mt.distSqr(follow) > 6 * 6))
                st.lagging.add(le);
        }
        bot.order(st.lagging, UnitAction.MOVE, follow);
        st.lagging.clear();
    }

    /** The nearest wreck with metal within {@link #WRECK_REACH} of {@code le} and of {@code follow}, or null. */
    @Nullable
    static Entity wreckFor(LivingEntity le, BlockPos follow) {
        List<Entity> wrecks = WreckServerEvents.getWrecks();
        Entity best = null;
        double bestD = WRECK_REACH * WRECK_REACH;
        for (int i = 0, n = wrecks.size(); i < n; i++) {
            Entity w = wrecks.get(i);
            if (w.isRemoved() || w.level() != le.level() || WreckServerEvents.metalOf(w) <= 0)
                continue;
            if (w.distanceToSqr(follow.getX() + 0.5, w.getY(), follow.getZ() + 0.5) > WRECK_REACH * WRECK_REACH)
                continue;
            double d = w.distanceToSqr(le);
            if (d < bestD) {
                bestD = d;
                best = w;
            }
        }
        return best;
    }

    /**
     * While the army gathers at its rally point (BUILD_UP), every {@link #TOWER_EVERY_TICKS} (medium: twice that) the
     * engineers raise the faction's anti-raid tower there - one per rally point, and not while metal is nearly empty.
     * A field defence, so the engineers may place it themselves.
     */
    static void forwardTower(BotPlayer bot, ServerLevel level, long gameTime, State st, List<BuildingPlacement> buildings) {
        long every = bot.difficulty == BotPlayer.Difficulty.HARD ? TOWER_EVERY_TICKS : TOWER_EVERY_TICKS * 2;
        if (bot.army.state != BotArmy.State.BUILD_UP || bot.metalFill < 0.15f
                || (st.lastTowerAt >= 0 && gameTime - st.lastTowerAt < every))
            return;
        Building tower = BotDefences.raidTowerFor(bot.faction);
        if (tower == null || !CombatEngineers.mayStart(tower))
            return;
        BlockPos rally = bot.army.rallyPoint(level, bot.home);
        if (rally.equals(bot.home))
            return;   // no idea where the enemy is yet: a "forward" tower at home is BotDefences' job
        for (BuildingPlacement bp : buildings)
            if (bp.getBuilding() == tower && bp.centrePos.distSqr(rally) <= TOWER_COVER * TOWER_COVER)
                return;
        st.lastTowerAt = gameTime;   // tried: whether or not the ground takes it, not again for a while
        // a few blocks to the side of the rally point, so the army's own gathering spot stays clear
        bot.placeNear(level, tower, rally.offset(4, 0, 4), new ArrayList<>(st.engineers), Math.min(2, st.engineers.size()));
    }

    // ------------------------------------------------------------------ the seven abilities

    /** The engineer's faction ability, when it is off cooldown and its trigger holds. Sealed units don't cast. */
    static void useAbility(BotPlayer bot, ServerLevel level, State st, LivingEntity le, List<BuildingPlacement> buildings) {
        if (!(le instanceof CombatEngineer ce) || !(le instanceof Unit u) || !le.isAlive() || BotPlayer.sealed(le))
            return;
        Ability a = ce.getEngineerAbility();
        if (a == null || !a.isOffCooldown(u))
            return;
        if (a instanceof Bulwark) {
            BuildingPlacement b = buildingToBrace(bot, level, le, buildings);
            if (b != null)
                a.use(level, u, b.centrePos);
        } else if (a instanceof BoneGraft) {
            LivingEntity patient = CombatEngineers.mostDamagedFriend(level, le, bot.name, BoneGraft.RANGE);
            if (patient != null && patient.getHealth() < patient.getMaxHealth() * 0.6f
                    && BoneGraft.nearestWreck(le, BoneGraft.RANGE) != null)
                a.use(level, u, patient);
        } else if (a instanceof TearDown) {
            tearDown(bot, level, le, u, a);
        } else if (a instanceof Rootwalk) {
            BuildingPlacement b = rootwalkTarget(bot, level, st, le, buildings);
            if (b != null)
                a.use(level, u, b.centrePos);
        } else if (a instanceof SalvageHook) {
            Entity wreck = wreckToHook(le);
            if (wreck != null)
                a.use(level, u, wreck.blockPosition());
        } else if (a instanceof MendingRite) {
            if (MendingRite.riteOf(le) != null || bot.energyFill < 0.25f)
                return;
            LivingEntity patient = CombatEngineers.mostDamagedFriend(level, le, bot.name, MendingRite.RANGE);
            if (patient != null && patient.getHealth() < patient.getMaxHealth() * 0.5f)
                a.use(level, u, patient);
        } else if (a instanceof Overdrive) {
            if (le instanceof PioneerUnit p && !p.isOverdriven() && !p.isVenting() && isWorking(le))
                a.use(level, u, le.blockPosition());
        }
    }

    /** Building, repairing or stripping a wreck right now: the work Overdrive speeds up. */
    static boolean isWorking(LivingEntity le) {
        if (WreckServerEvents.isReclaiming(le) || BoneGraft.nearestWreck(le, WreckServerEvents.RECLAIM_RANGE) != null)
            return true;
        return le instanceof WorkerUnit w && w.getBuildRepairGoal() != null && w.getBuildRepairGoal().getBuildingTarget() != null
            && w.getBuildRepairGoal().isBuilding();
    }

    /** An own building within Bulwark's reach that is under 85% health with an enemy near it, not braced yet. */
    @Nullable
    static BuildingPlacement buildingToBrace(BotPlayer bot, ServerLevel level, LivingEntity mason, List<BuildingPlacement> buildings) {
        for (BuildingPlacement b : buildings) {
            if (!b.isBuilt || b.isDestroyedServerside || b.getHealth() >= b.getMaxHealth() * 0.85f
                    || CombatEngineers.distanceToBuilding(mason, b) > Bulwark.RANGE || Bulwark.braceOn(b) != null)
                continue;
            if (bot.nearestEnemyUnit(level, b.centrePos, 16) != null)
                return b;
        }
        return null;
    }

    /**
     * The nearest enemy wall, gate or revealed trap within 12 blocks of the Wrecker: torn down if in reach, else - while
     * the army is on the attack - the Wrecker walks up to it (the next think tears it down). Only what the Wrecker
     * could see: a trap its owner hides is not attackable and is skipped (no fog leak).
     */
    static void tearDown(BotPlayer bot, ServerLevel level, LivingEntity wrecker, Unit u, Ability a) {
        BuildingPlacement best = null;
        double bestD = 12;
        for (BuildingPlacement b : BuildingServerEvents.getBuildings()) {
            if (b.isDestroyedServerside || !TearDown.isTearable(b.getBuilding()) || b.getLevel() != level
                    || b.ownerName == null || b.ownerName.isBlank() || CombatEngineers.isFriendly(bot.name, b.ownerName)
                    || !b.isAttackable())
                continue;
            double d = CombatEngineers.distanceToBuilding(wrecker, b);
            if (d < bestD) {
                bestD = d;
                best = b;
            }
        }
        if (best == null)
            return;
        if (bestD <= TearDown.REACH) {
            a.use(level, u, best.centrePos);
        } else if (bot.army.state == BotArmy.State.ATTACK && u.isIdle()) {
            BlockPos at = BotPlayer.ground(level, best.centrePos.getX(), best.centrePos.getZ());
            UnitServerEvents.addActionItem(bot.name, UnitAction.MOVE, -1, new int[] { wrecker.getId() }, at, at);
        }
    }

    /**
     * Where a Rootwarden walks: out of trouble when it is hurt with an enemy close (the own building in reach farthest
     * from it), else - while the army gathers - to a damaged own building too far to walk to quickly, so its field
     * repair takes the job over on arrival.
     */
    @Nullable
    static BuildingPlacement rootwalkTarget(BotPlayer bot, ServerLevel level, State st, LivingEntity warden, List<BuildingPlacement> buildings) {
        boolean hurt = warden.getHealth() < warden.getMaxHealth() * 0.45f;
        boolean threatened = hurt && bot.nearestEnemyUnit(level, warden.blockPosition(), 8) != null;
        boolean calm = bot.army.state == BotArmy.State.BUILD_UP;
        if (!threatened && !calm)
            return null;
        BuildingPlacement best = null;
        double bestScore = -1;
        for (BuildingPlacement b : buildings) {
            if (!b.isBuilt || b.isDestroyedServerside || b.getLevel() != level)
                continue;
            double d = CombatEngineers.distanceToBuilding(warden, b);
            if (d > Rootwalk.RANGE || d < 16)
                continue;
            double score;
            if (threatened)
                score = d;   // as far as the roots reach
            else if (b.getBuilding().repairable && b.getHealth() < b.getMaxHealth() * 0.6f)
                score = b.getMaxHealth() - b.getHealth();   // the worst hurt
            else
                continue;
            if (score > bestScore) {
                bestScore = score;
                best = b;
            }
        }
        return best;
    }

    /** The richest wreck 6-16 blocks out that no line holds yet (a closer one is simply walked to). */
    @Nullable
    static Entity wreckToHook(LivingEntity salvager) {
        List<Entity> wrecks = WreckServerEvents.getWrecks();
        Entity best = null;
        float bestMetal = WreckServerEvents.MIN_COST * WreckServerEvents.WRECK_SHARE;
        double min2 = 6 * 6, max2 = SalvageHook.RANGE * SalvageHook.RANGE;
        for (int i = 0, n = wrecks.size(); i < n; i++) {
            Entity w = wrecks.get(i);
            if (w.isRemoved() || w.level() != salvager.level() || SalvageHook.isOnLine(w))
                continue;
            double d = w.distanceToSqr(salvager);
            float metal = WreckServerEvents.metalOf(w);
            if (d < min2 || d > max2 || metal < bestMetal)
                continue;
            bestMetal = metal;
            best = w;
        }
        return best;
    }
}
