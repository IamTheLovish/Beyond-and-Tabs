package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;

import net.minecraft.world.entity.LivingEntity;

/**
 * The role table the bot composes its army with - the same roles tools/unit_stats.py's ROSTER gives each unit
 * (scout, raider, siege/artillery, anti-air), kept in sync by hand. Each faction's scout is trained at its capitol,
 * its siege answer lives in the lab named here (T1 or T2), and its raiders are the fast units a raid squad prefers.
 * Anti-air is {@link BotPlayer#queueAntiAir} (the Court, the Tidewrought and the Stormcallers have a dedicated AA
 * unit; the others' kits mix in flyer-capable units already).
 */
public final class BotRoles {

    private BotRoles() { }

    /** The faction's cheapest, fastest scout (every capitol trains one), or null. */
    public static ProductionItem scoutFor(Faction faction) {
        if (Factions.VILLAGERS.equals(faction)) return ProductionItems.SCOUT_DOG;
        if (Factions.MONSTERS.equals(faction)) return ProductionItems.BAT;
        if (Factions.PIGLINS.equals(faction)) return ProductionItems.STRIDER;
        if (Factions.VERDANT_COURT.equals(faction)) return ProductionItems.FOX_COURIER;
        if (Factions.TIDEWROUGHT.equals(faction)) return ProductionItems.GULL_SPOTTER;
        if (Factions.STORMCALLERS.equals(faction)) return ProductionItems.PAPER_CRANE;
        return null;
    }

    /**
     * The unit the bot adds against static defences (role siege/artillery in the roster), or null: every faction's T1
     * siege lobber (design/siege_t1.md, lovish Oct 29). It used to be the Ravager, Creeper, Ghast and Elder Treant at
     * the T2 labs, the Bombard Crew and the Rocket Battery at the T1 labs, and nothing for the Brassworks.
     */
    public static ProductionItem siegeFor(Faction faction) {
        if (Factions.VILLAGERS.equals(faction)) return ProductionItems.SUNFIRE_MANGONEL;
        if (Factions.MONSTERS.equals(faction)) return ProductionItems.SKULL_HURLER;
        if (Factions.PIGLINS.equals(faction)) return ProductionItems.BOULDER_CHUCKER;
        if (Factions.VERDANT_COURT.equals(faction)) return ProductionItems.BURL_SLINGER;
        if (Factions.TIDEWROUGHT.equals(faction)) return ProductionItems.POWDER_MONKEY;
        if (Factions.STORMCALLERS.equals(faction)) return ProductionItems.THUNDERSTONE_MONK;
        if (Factions.BRASSWORKS.equals(faction)) return ProductionItems.STEAM_LOBBER;
        return null;
    }

    /** Which lab trains {@link #siegeFor}: the T1 army building (every faction's lobber) or the T2 lab. */
    public static boolean siegeIsT2(Faction faction) {
        return false;
    }

    /** True for a unit of the siege role (what {@link #siegeFor} produces and the units it used to: the T2 wall-breakers, the long artillery). */
    public static boolean isSiege(LivingEntity le) {
        return le instanceof com.solegendary.reignofnether.unit.units.villagers.RavagerUnit
            || le instanceof com.solegendary.reignofnether.unit.units.monsters.CreeperUnit
            || le instanceof com.solegendary.reignofnether.unit.units.piglins.GhastUnit
            || le instanceof com.solegendary.reignofnether.unit.units.verdant.ElderTreantUnit
            || le instanceof com.solegendary.reignofnether.unit.units.tide.BombardCrewUnit
            || le instanceof com.solegendary.reignofnether.unit.units.tide.MortarGalleyUnit
            || le instanceof com.solegendary.reignofnether.unit.units.storm.RocketBatteryUnit
            || le instanceof com.solegendary.reignofnether.unit.units.shared.SiegeLobberUnit;
    }

    /** True for a scout-role unit: it never joins the army and is never sent on a raid. */
    public static boolean isScout(LivingEntity le) {
        return le instanceof com.solegendary.reignofnether.unit.units.villagers.ScoutDogUnit
            || le instanceof com.solegendary.reignofnether.unit.units.villagers.ScoutCatUnit
            || le instanceof com.solegendary.reignofnether.unit.units.monsters.BatUnit
            || le instanceof com.solegendary.reignofnether.unit.units.piglins.StriderUnit
            || le instanceof com.solegendary.reignofnether.unit.units.verdant.FoxCourierUnit
            || le instanceof com.solegendary.reignofnether.unit.units.verdant.OwlWatcherUnit
            || le instanceof com.solegendary.reignofnether.unit.units.tide.GullSpotterUnit
            || le instanceof com.solegendary.reignofnether.unit.units.storm.PaperCraneUnit;
    }

    /** A static defence of any faction: seeing one near a target is what makes the bot add siege to its mix. */
    public static boolean isDefence(Building b) {
        return b == Buildings.WATCHTOWER || b == Buildings.DARK_WATCHTOWER || b == Buildings.STORM_OAK
            || b == Buildings.LIGHTHOUSE_BATTERY || b == Buildings.RAIL_BATTERY;
    }
}
