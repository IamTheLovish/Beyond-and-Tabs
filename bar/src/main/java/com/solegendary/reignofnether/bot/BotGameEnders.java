package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.gameender.GameEnderPlacement;
import com.solegendary.reignofnether.building.gameender.WardSpirePlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.unit.T2Workers;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.brass.SteamHaulerUnit;
import com.solegendary.reignofnether.unit.units.piglins.GorehornRhinoUnit;
import com.solegendary.reignofnether.unit.units.tide.ShellbackTurtleUnit;
import com.solegendary.reignofnether.unit.units.verdant.MossbackBisonUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The bot's side of the big units and game enders (design/big_units.md), kept out of BotPlayer:
 * <ul>
 *   <li><b>Prophylaxis</b>: any bot that can (Tier 2, not easy) answers an enemy game ender - every start is announced,
 *   so every bot knows - with a Ward Spire beside its capitol, one per enemy game ender standing.</li>
 *   <li><b>Its own game ender</b>: only a hard bot, only while floating metal with its T3 lab up and Tier 3, at most one,
 *   built near home by a T2 constructor (the server's rule). A bot that is not floating has better uses for 5500 metal.</li>
 *   <li><b>Beasts in a fight</b>: the Gorehorn Rhino's Breach Charge down a lane that catches two enemies or more; the
 *   Mossback Bison embarks friendly infantry next to it when it has free seats and no enemy is close (and ejects
 *   them once one is); the Shellback Siege-Turtle moors when an enemy is in its battery's band and nothing is at its
 *   shell, and weighs anchor when nothing is left in range or melee reaches it; the Steam Hauler drops a turret
 *   toward the nearest enemy in reach when it isn't hot. The Jade Qilin is passive.</li>
 * </ul>
 * Every faction has its game ender (beasts-2): a hard bot builds whichever its faction has.
 * One scan of the building list per bot think for the enemy game enders (a few hundred entries at 8v8, every few
 * seconds) and one grid query per beast whose ability is ready.
 */
public final class BotGameEnders {
    private BotGameEnders() { }

    /** The faction's game ender, or null for a faction without one (neutral). */
    @Nullable
    public static Building gameEnderFor(Faction f) {
        if (Factions.VILLAGERS.equals(f))
            return Buildings.SUN_SPIRE;
        if (Factions.MONSTERS.equals(f))
            return Buildings.NECROPOLIS;
        if (Factions.PIGLINS.equals(f))
            return Buildings.DOOM_FORGE;
        if (Factions.VERDANT_COURT.equals(f))
            return Buildings.WORLD_SEED;
        if (Factions.TIDEWROUGHT.equals(f))
            return Buildings.MAELSTROM_ENGINE;
        if (Factions.STORMCALLERS.equals(f))
            return Buildings.HEAVENS_BELL;
        if (Factions.BRASSWORKS.equals(f))
            return Buildings.EARTHCRACKER;
        return null;
    }

    /** The faction's Ward Spire. */
    @Nullable
    public static Building wardFor(Faction f) {
        if (Factions.VILLAGERS.equals(f)) return Buildings.WARD_SPIRE_VILLAGERS;
        if (Factions.MONSTERS.equals(f)) return Buildings.WARD_SPIRE_MONSTERS;
        if (Factions.PIGLINS.equals(f)) return Buildings.WARD_SPIRE_PIGLINS;
        if (Factions.VERDANT_COURT.equals(f)) return Buildings.WARD_SPIRE_VERDANT;
        if (Factions.TIDEWROUGHT.equals(f)) return Buildings.WARD_SPIRE_TIDE;
        if (Factions.STORMCALLERS.equals(f)) return Buildings.WARD_SPIRE_STORM;
        if (Factions.BRASSWORKS.equals(f)) return Buildings.WARD_SPIRE_BRASS;
        return null;
    }

    /** Game enders (built or still a site) standing for players hostile to {@code owner}. */
    public static int enemyGameEnders(String owner, List<BuildingPlacement> all) {
        int n = 0;
        for (BuildingPlacement bp : all)
            if (bp instanceof GameEnderPlacement && !bp.isDestroyedServerside && bp.ownerName != null && !bp.ownerName.isBlank()
                    && !bp.ownerName.equals(owner) && !AlliancesServerEvents.isAllied(owner, bp.ownerName))
                n++;
        return n;
    }

    /**
     * How many Ward Spires a bot should have: one per enemy game ender, none without Tier 2 or on easy (an easy bot is
     * meant to lose to one). Public for the game test.
     */
    public static int wardsWanted(BotPlayer.Difficulty difficulty, boolean tier2, int enemyEnders) {
        if (difficulty.easyLike() || !tier2)
            return 0;
        return Math.min(3, enemyEnders);
    }

    /** Whether a bot should start its own game ender now. Public for the game test. */
    public static boolean wantsGameEnder(BotPlayer.Difficulty difficulty, boolean floating, boolean t3LabBuilt, boolean tier3, int ownEnders) {
        return difficulty == BotPlayer.Difficulty.HARD && floating && t3LabBuilt && tier3 && ownEnders == 0;
    }

    /**
     * One build decision (BotPlayer's economy step calls it in its priority chain): a Ward Spire if an enemy game ender
     * stands and we are short of wards, else our own game ender if {@link #wantsGameEnder}. True if a site was placed.
     */
    static boolean think(BotPlayer bot, ServerLevel level, List<BuildingPlacement> mine, List<LivingEntity> workers) {
        if (workers.isEmpty())
            return false;
        boolean tier2 = ResearchServerEvents.playerHasResearch(bot.name, ProductionItems.RESEARCH_TIER_2);
        Building ward = wardFor(bot.faction);
        if (ward != null) {
            int have = bot.count(mine, ward);
            if (have < wardsWanted(bot.difficulty, tier2, enemyGameEnders(bot.name, BuildingServerEvents.getBuildings()))) {
                // beside the capitol: what a game ender aims at first
                BlockPos at = bot.home;
                for (BuildingPlacement bp : mine)
                    if (bp.isCapitol)
                        at = bp.centrePos;
                if (bot.placeNear(level, ward, at.offset(6 + have * 4, 0, -6), workers, 2))
                    return true;
            }
        }
        Building ender = gameEnderFor(bot.faction);
        if (ender == null || bot.kit == null || bot.kit.t3Building() == null)
            return false;
        boolean labUp = false;
        for (BuildingPlacement bp : mine)
            if (bp.getBuilding() == bot.kit.t3Building() && bp.isBuilt)
                labUp = true;
        boolean tier3 = ResearchServerEvents.playerHasResearch(bot.name, ProductionItems.RESEARCH_TIER_3);
        if (!wantsGameEnder(bot.difficulty, bot.floating(), labUp, tier3, bot.count(mine, ender)))
            return false;
        // only a T2 constructor may place it (T2Workers.needsT2Builder); the rest join the site through assignBuilders
        List<LivingEntity> t2 = new ArrayList<>();
        for (LivingEntity w : workers)
            if (T2Workers.isT2Worker(w))
                t2.add(w);
        if (t2.isEmpty())
            return false;
        return bot.placeNear(level, ender, bot.layoutSlot(BotPlayer.Layout.ARMY, 5), t2, 2);
    }

    /** Enemy units a Breach Charge lane must catch to be worth its 25 s cooldown. */
    static final int BREACH_MIN_FOES = 2;
    private static final List<LivingEntity> scan = new ArrayList<>();

    /** The beasts' abilities in a fight (BotPlayer.castFightAbilities; the duel simulator uses it too). */
    static void castBeastAbilities(BotPlayer bot, ServerLevel level, List<LivingEntity> mine) {
        for (LivingEntity le : mine) {
            if (le instanceof GorehornRhinoUnit rhino && rhino.isAlive())
                rhinoBreach(bot, level, rhino);
            else if (le instanceof MossbackBisonUnit bison && bison.isAlive() && le.tickCount % 20 == 0)
                bisonGrove(bot, level, bison);
            else if (le instanceof ShellbackTurtleUnit turtle && turtle.isAlive() && le.tickCount % 20 == 0)
                turtleMoor(bot, level, turtle);
            else if (le instanceof SteamHaulerUnit hauler && hauler.isAlive() && le.tickCount % 20 == 0)
                haulerTurret(bot, level, hauler);
        }
    }

    /**
     * Whether a turtle should be moored, from the enemies round it: yes when one is in the battery's band and none is at
     * the shell; no when none is in range (march on) or one has reached the shell (bite back). Public for the game test.
     */
    public static boolean turtleWantsMoor(int inBand, int atShell) {
        return inBand > 0 && atShell == 0;
    }

    static void turtleMoor(BotPlayer bot, ServerLevel level, ShellbackTurtleUnit turtle) {
        int inBand = 0, atShell = 0;
        double min2 = ShellbackTurtleUnit.BATTERY_MIN_RANGE * ShellbackTurtleUnit.BATTERY_MIN_RANGE;
        double max2 = ShellbackTurtleUnit.BATTERY_RANGE * ShellbackTurtleUnit.BATTERY_RANGE;
        for (LivingEntity o : UnitGrid.near(level, turtle.getX(), turtle.getZ(), ShellbackTurtleUnit.BATTERY_RANGE, scan)) {
            if (!bot.isEnemyUnit(o))
                continue;
            double d = o.distanceToSqr(turtle);
            if (d < min2)
                atShell++;
            else if (d <= max2)
                inBand++;
        }
        scan.clear();
        if (turtleWantsMoor(inBand, atShell) != turtle.isMoorWanted())
            turtle.toggleMoor();
    }

    static void haulerTurret(BotPlayer bot, ServerLevel level, SteamHaulerUnit hauler) {
        if (hauler.heat() >= com.solegendary.reignofnether.brass.Heat.HOT || hauler.getAbilities() == null)
            return;   // a turret now would vent it mid-fight
        com.solegendary.reignofnether.ability.Ability drop = null;
        for (var a : hauler.getAbilities().get())
            if (a instanceof com.solegendary.reignofnether.ability.abilities.DropTurret && a.isOffCooldown(hauler))
                drop = a;
        if (drop == null)
            return;
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity o : UnitGrid.near(level, hauler.getX(), hauler.getZ(), 14, scan)) {
            if (!bot.isEnemyUnit(o))
                continue;
            double d = o.distanceToSqr(hauler);
            if (d < best) {
                best = d;
                nearest = o;
            }
        }
        scan.clear();
        if (nearest == null)
            return;
        // between the hauler and the enemy: the ability pulls the spot in to its reach
        drop.use(level, hauler, nearest.blockPosition());
    }

    static void rhinoBreach(BotPlayer bot, ServerLevel level, GorehornRhinoUnit rhino) {
        if (rhino.breachWindup > 0 || rhino.breachSteps > 0 || rhino.getAbilities() == null)
            return;
        com.solegendary.reignofnether.ability.Ability breach = null;
        for (var a : rhino.getAbilities().get())
            if (a instanceof com.solegendary.reignofnether.ability.abilities.BreachCharge && a.isOffCooldown(rhino))
                breach = a;
        if (breach == null)
            return;
        double reach = GorehornRhinoUnit.BREACH_LENGTH;
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity o : UnitGrid.near(level, rhino.getX(), rhino.getZ(), reach, scan)) {
            if (!bot.isEnemyUnit(o))
                continue;
            double d = o.distanceToSqr(rhino);
            if (d < best && d >= 9 && d <= reach * reach) {
                best = d;
                nearest = o;
            }
        }
        if (nearest == null) {
            scan.clear();
            return;
        }
        // count the foes the lane toward the nearest one would catch
        Vec3 dir = new Vec3(nearest.getX() - rhino.getX(), 0, nearest.getZ() - rhino.getZ()).normalize();
        double half = GorehornRhinoUnit.BREACH_HIT_RADIUS + rhino.getBbWidth() * 0.5;
        int caught = 0;
        for (LivingEntity o : scan) {
            if (!bot.isEnemyUnit(o))
                continue;
            double rx = o.getX() - rhino.getX(), rz = o.getZ() - rhino.getZ();
            double along = rx * dir.x + rz * dir.z, across = Math.abs(rx * -dir.z + rz * dir.x);
            if (along > 0 && along <= reach && across <= half)
                caught++;
        }
        scan.clear();
        if (caught >= BREACH_MIN_FOES)
            breach.use(level, rhino, nearest);
    }

    static void bisonGrove(BotPlayer bot, ServerLevel level, MossbackBisonUnit bison) {
        boolean foeClose = false;
        for (LivingEntity o : UnitGrid.near(level, bison.getX(), bison.getZ(), 10, scan))
            if (bot.isEnemyUnit(o) && o.distanceToSqr(bison) <= 100) {
                foeClose = true;
                break;
            }
        scan.clear();
        if (foeClose) {
            if (bison.isVehicle())
                bison.ejectPassengers();   // in the fight the riders are worth more on the ground
        } else if (bison.getPassengers().size() < MossbackBisonUnit.SEATS) {
            bison.embarkNearby();          // on the march: carry the infantry beside it
        }
    }
}
