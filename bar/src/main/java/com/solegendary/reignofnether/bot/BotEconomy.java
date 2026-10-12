package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingLevels;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingReclaim;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.BuildingValidators;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.shared.GeothermalPlant;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.resources.TreeClearing;
import com.solegendary.reignofnether.unit.T2Workers;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * The bot's answers to tonight's economy systems (design/bot.md "Map economy"): geothermal plants on lava (T2),
 * felling trees for energy when it is starved, decommissioning obsolete T1 buildings late when a pool sits at its
 * cap, and the upgrade-or-new-lab rule for lab levels (design/building_upgrades.md). Every rule here is a cheap
 * check that runs once per think; the only world scans (lava, trees) are rate-limited and bounded.
 */
public class BotEconomy {

    final BotPlayer bot;

    BotEconomy(BotPlayer bot) {
        this.bot = bot;
    }

    // ---------------------------------------------------------------------------------- lab levels vs another lab

    /**
     * How much more per point of production a level may cost than a new lab before the bot builds the lab instead.
     * A new lab's price is only its metal; its real price is builder time (minutes of a worker on hundreds of
     * blocks), ground, and a second target to defend - so a level, which needs none of those, is worth a premium.
     * With 6 both levels win once there are two labs (L2: 90 m for +0.75 = 120 m a point, L3: 240 m a point, a
     * 45 m lab: 270 m a point with the premium), which is the doc's "two labs, then levels" made explicit.
     */
    static final float LEVEL_PREMIUM = 6f;

    /** Metal per point of production a new lab of this kit costs (its build power is 1.0). */
    float newLabMetalPerPoint() {
        Building lab = bot.kit.armyBuilding() == Buildings.PORTAL_BASIC ? Buildings.PORTAL_MILITARY : bot.kit.armyBuilding();
        return lab.cost == null ? 45 : Math.max(1, lab.cost.metal());
    }

    /** Metal per point of production the next level of a lab at {@code level} buys. */
    static float levelMetalPerPoint(int level) {
        var cost = BuildingLevels.upgradeCost(level);   // level -> level + 1
        float gain = BuildingLevels.buildPowerAt(level + 1) - BuildingLevels.buildPowerAt(level);
        return cost == null || gain <= 0 ? Float.MAX_VALUE : cost.metal() / gain;
    }

    /**
     * The upgrade-or-new-lab rule: with two labs (a second door and queue first), the lowest lab's next level when it
     * beats a new lab by {@link #LEVEL_PREMIUM}, the bot can pay for it (metal a quarter full, or half the level's
     * metal in the pool - the flow economy pays as it goes) and it is not defending its base (the lab makes nothing
     * while it rebuilds itself: a developing move, a mistake under pressure).
     */
    boolean levelBeatsNewLab(BuildingPlacement lab, float metalStored) {
        int level = lab.getBuildingLevel();
        if (level >= lab.getBuilding().maxLevel)
            return false;
        var cost = BuildingLevels.upgradeCost(level);
        boolean affordable = bot.metalFill >= 0.25f || (cost != null && metalStored >= cost.metal() * 0.5f);
        return affordable && bot.army.state != BotArmy.State.DEFEND
            && levelMetalPerPoint(level) <= newLabMetalPerPoint() * LEVEL_PREMIUM;
    }

    // ---------------------------------------------------------------------------------------- geothermal on lava

    /** The faction's Geothermal Plant (one instance per faction, design/lively_buildings.md). */
    static Building geothermalFor(Faction f) {
        if (Factions.VILLAGERS.equals(f)) return Buildings.GEOTHERMAL_PLANT_VILLAGERS;
        if (Factions.MONSTERS.equals(f)) return Buildings.GEOTHERMAL_PLANT_MONSTERS;
        if (Factions.PIGLINS.equals(f)) return Buildings.GEOTHERMAL_PLANT_PIGLINS;
        if (Factions.VERDANT_COURT.equals(f)) return Buildings.GEOTHERMAL_PLANT_VERDANT;
        if (Factions.TIDEWROUGHT.equals(f)) return Buildings.GEOTHERMAL_PLANT_TIDE;
        if (Factions.STORMCALLERS.equals(f)) return Buildings.GEOTHERMAL_PLANT_STORM;
        if (Factions.BRASSWORKS.equals(f)) return Buildings.GEOTHERMAL_PLANT_BRASS;
        return null;
    }

    /** How far round home the bot looks for lava, the column step of the look, and how often it looks again. */
    static final int LAVA_RANGE = 64, LAVA_STEP = 3;
    static final long LAVA_SCAN_TICKS = 20 * 60;
    /** Lava pools near home (the maps' 3x3 vents, a natural pool, the rift's edge), nearest first; at most four. */
    final List<BlockPos> lava = new ArrayList<>();
    long lavaScannedAt = -1;

    /**
     * One bounded look for lava round home: a column every LAVA_STEP blocks (a 3x3 vent can't slip between), one
     * heightmap read each, ~1800 columns a minute. Spots closer than 8 blocks to one already found are the same pool.
     */
    void scanLava(ServerLevel level) {
        lava.clear();
        BlockPos home = bot.home;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -LAVA_RANGE; dx <= LAVA_RANGE; dx += LAVA_STEP)
            for (int dz = -LAVA_RANGE; dz <= LAVA_RANGE; dz += LAVA_STEP) {
                if (dx * dx + dz * dz > LAVA_RANGE * LAVA_RANGE)
                    continue;
                int x = home.getX() + dx, z = home.getZ() + dz;
                if (!level.hasChunk(x >> 4, z >> 4))
                    continue;   // never load a chunk for this
                p.set(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1, z);
                if (!GeothermalPlant.isLava(level.getBlockState(p)))
                    continue;
                boolean known = false;
                for (BlockPos q : lava)
                    if (Math.abs(q.getX() - x) < 8 && Math.abs(q.getZ() - z) < 8) {
                        known = true;
                        break;
                    }
                if (!known)
                    lava.add(p.immutable());
            }
        lava.sort((a, b) -> Double.compare(a.distSqr(home), b.distSqr(home)));
        while (lava.size() > 4)
            lava.remove(lava.size() - 1);
    }

    /**
     * BAR's T2 energy: once Tier 2 is in and a T2 constructor is out, a Geothermal Plant on the nearest free lava
     * pool near home (120 energy/s for 280 metal: worth a field of wind). One site at a time, at most one per pool.
     * Returns true when it placed one.
     */
    boolean placeGeothermal(ServerLevel level, long gameTime, boolean tier2, List<BuildingPlacement> buildings,
                            List<LivingEntity> workers) {
        Building geo = geothermalFor(bot.faction);
        if (!tier2 || geo == null || bot.metalFill < 0.1f)
            return false;
        LivingEntity builder = null;
        for (LivingEntity w : workers)
            if (T2Workers.isT2Worker(w) && (builder == null || w.distanceToSqr(bot.home.getCenter()) < builder.distanceToSqr(bot.home.getCenter())))
                builder = w;
        if (builder == null)
            return false;
        int have = 0;
        for (BuildingPlacement bp : buildings)
            if (bp.getBuilding() == geo) {
                if (!bp.isBuilt)
                    return false;   // one site at a time
                have++;
            }
        if (lavaScannedAt < 0 || gameTime - lavaScannedAt >= LAVA_SCAN_TICKS) {
            lavaScannedAt = gameTime;
            scanLava(level);
        }
        if (have >= lava.size())
            return false;
        for (BlockPos l : lava) {
            if (BuildingUtils.findBuilding(false, l) != null)
                continue;   // a plant (ours or theirs) already taps it
            // the 5x5's centre column over the lava: origin = centre - 2 (the pool may sit a block under the column top)
            for (int dy : new int[] { 0, -1 })
                for (int[] o : new int[][] { {-2, -2}, {-1, -2}, {-2, -1}, {-3, -2}, {-2, -3}, {-1, -1}, {-3, -3} }) {
                    BlockPos origin = new BlockPos(l.getX() + o[0], l.getY() + dy, l.getZ() + o[1]);
                    if (!BuildingValidators.isPlacementValid(level, geo, origin, bot.name, Rotation.NONE, false, false, true))
                        continue;
                    if (BuildingServerEvents.placeBuilding(geo, origin, Rotation.NONE, bot.name, new int[] { builder.getId() },
                            false, false, false, true) != null) {
                        bot.lastPlacedAt = gameTime;
                        return true;
                    }
                }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------- trees for energy

    /** Energy fill under which the bot sends a worker to fell trees (5 energy a log, straight into the pool). */
    static final float TREE_STARVED = 0.15f;
    static final int TREE_RANGE = 32;
    static final long TREE_EVERY_TICKS = 20 * 15;
    long treesAt = -1;

    /**
     * Energy-starved (under {@link #TREE_STARVED} with metal to spare, so energy really is the bottleneck): one idle
     * worker walks to the nearest few trees within TREE_RANGE of home and fells them - the same orders area reclaim
     * gives (a MOVE plus a TreeClearing order). At most once every 15 s: the scan is a bounded square of heightmap
     * reads. The maps grow trees only in reclaim lanes, so this mostly finds a forest band beside the base.
     */
    void fellTrees(ServerLevel level, long gameTime, List<LivingEntity> workers) {
        if (bot.energyFill >= TREE_STARVED || bot.metalFill < 0.1f || workers.size() < 3
                || (treesAt >= 0 && gameTime - treesAt < TREE_EVERY_TICKS))
            return;
        treesAt = gameTime;
        // from the back of the list: placeNear sorts the workers nearest-first to the site it just laid, so the
        // last idle one is the least likely to have just been handed that site
        LivingEntity feller = null;
        for (int i = workers.size() - 1; i >= 0 && feller == null; i--) {
            LivingEntity w = workers.get(i);
            if (w instanceof Unit u && u.isIdle() && !com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(w)
                    && !T2Workers.isT2Worker(w))
                feller = w;
        }
        if (feller == null)
            return;
        List<BlockPos> trees = TreeClearing.treesIn(level, bot.home, TREE_RANGE, 12, 3);
        if (trees.isEmpty())
            return;
        int[] ids = new int[] { feller.getId() };
        for (int i = 0; i < trees.size(); i++) {
            BlockPos t = trees.get(i);
            // the first walk now, the rest queued behind it (as area reclaim deals trees out)
            if (i == 0)
                UnitServerEvents.addActionItem(bot.name, UnitAction.MOVE, -1, ids, t, t);
            else
                UnitServerEvents.addActionItem(bot.name, UnitAction.MOVE, -1, ids, t, t, true);
            TreeClearing.orderFell(bot.name, feller.getId(), t);
        }
    }

    // ------------------------------------------------------------------------------- decommission obsolete T1

    /** Not before this economy minute, and a pool pinned at its cap this long (the vault watch's clock). */
    static final long DECOMMISSION_MINUTE = 12;
    static final long DECOMMISSION_CAP_TICKS = 30 * 20;
    /** Wind generators kept whatever happens (a raid on the plant must not black the base out). */
    static final int KEEP_WINDS = 4;

    /**
     * Late, with Tier 2 and a Geothermal Plant (or two converters) standing, and a pool pinned at its cap for 30 s:
     * the T1 wind field is obsolete clutter - its energy goes nowhere - so one wind generator at a time (the
     * furthest from home first, it is the most exposed) is decommissioned for 60% of its metal back and the ground.
     * Labs are never sold: a capped bot is short of spending, and selling a door would make that worse.
     */
    boolean decommissionObsolete(long gameTime, long minutes, boolean tier2, List<BuildingPlacement> buildings) {
        if (!tier2 || minutes < DECOMMISSION_MINUTE || bot.atCapSince < 0 || gameTime - bot.atCapSince < DECOMMISSION_CAP_TICKS)
            return false;
        Building geo = geothermalFor(bot.faction);
        int winds = 0, plants = 0, converters = 0;
        BuildingPlacement furthest = null;
        for (BuildingPlacement bp : buildings) {
            if (bp.decommissioning)
                return false;   // one at a time
            Building b = bp.getBuilding();
            if (b == geo && bp.isBuilt)
                plants++;
            else if (b == bot.kit.converter() && bp.isBuilt)
                converters++;
            else if (b == bot.kit.wind() && bp.isBuilt) {
                winds++;
                if (furthest == null || bp.originPos.distSqr(bot.home) > furthest.originPos.distSqr(bot.home))
                    furthest = bp;
            }
        }
        if (furthest == null || winds <= KEEP_WINDS || (plants < 1 && converters < 2) || bot.energyFill < 0.9f)
            return false;
        return BuildingReclaim.start(furthest, bot.name);
    }
}
