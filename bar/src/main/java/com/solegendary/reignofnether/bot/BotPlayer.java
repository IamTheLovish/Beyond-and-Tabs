package com.solegendary.reignofnether.bot;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingValidators;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.resources.MetalPatches;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.util.MiscUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A skirmish opponent. Each bot owns an RTSPlayer (PlayerServerEvents.startRTSBot gives it workers and resources)
 * and this brain plays the match for it: a capitol, workers, metal extractors on the map's metal patches, wind
 * generators, houses ahead of the population cap, an army building, then waves of fighters at the nearest enemy.
 * Decisions are re-derived from world state every second, so it recovers cleanly after a reload.
 */
public class BotPlayer {

    /** PASSIVE plays EASY's economy and base but never leaves home to fight (BotArmy gates every wave, raid, scout and site squad). */
    public enum Difficulty {
        EASY, MEDIUM, HARD, PASSIVE;   // the order is the lobby cycle (index 0..3 in the setup screens and packets)

        /** The name on the lobby cycle button. */
        public String label() { return name().charAt(0) + name().substring(1).toLowerCase(); }

        /** Easy and Passive share the easy economy and tech rules. */
        public boolean easyLike() { return this == EASY || this == PASSIVE; }
    }

    /**
     * What each faction uses for each job. T2 lives in its own lab (needs Tier 2 research): it trains the T2 army
     * and the T2 constructor, the only worker that may raise the T3 lab, which makes the experimental.
     */
    public record Kit(Building capitol, Building house, Building farm, Building extractor, Building wind, Building armyBuilding,
               Building tower, ProductionItem worker, List<ProductionItem> army,
               Building t2Lab, ProductionItem t2Worker, List<ProductionItem> t2Army,
               Building t3Building, ProductionItem t3Unit, Building converter) { }

    /**
     * The faction's kit, or null if bots cannot play it yet. Every faction is listed explicitly: the old version
     * returned the villager kit for anything unknown, so a Verdant Court bot would have built Sunforged buildings.
     */
    public static Kit kitFor(Faction faction) {
        if (faction == null)
            return null;
        // the Horde's army lab is a Basic Portal the bot refits to a Military Portal (think() queues the refit): the
        // old kit named the Bastion, which only researches, so a Horde bot never trained a single Brute
        if (faction.equals(Factions.PIGLINS))
            return new Kit(Buildings.CENTRAL_PORTAL, Buildings.PORTAL_POCKET, null,   // no farms any more (Factions)
                    Buildings.METAL_EXTRACTOR_PIGLINS, Buildings.WIND_GENERATOR_PIGLINS, Buildings.PORTAL_BASIC,
                    null, ProductionItems.GRUNT, List.of(ProductionItems.BRUTE, ProductionItems.HEADHUNTER),
                    Buildings.FLAME_SANCTUARY, ProductionItems.BONEWRIGHT,
                    List.of(ProductionItems.BLAZE, ProductionItems.WITHER_SKELETON, ProductionItems.MAGMA_CUBE, ProductionItems.GHAST,
                            ProductionItems.GOREHORN_RHINO),   // big-units T2 beast
                    Buildings.FORTRESS, ProductionItems.WAR_MAMMOTH, Buildings.ENERGY_CONVERTER_PIGLINS);
        if (faction.equals(Factions.MONSTERS))
            return new Kit(Buildings.MAUSOLEUM, null, null,   // no house or farm any more (Factions)
                    Buildings.METAL_EXTRACTOR_MONSTERS, Buildings.WIND_GENERATOR_MONSTERS, Buildings.GRAVEYARD,
                    Buildings.DARK_WATCHTOWER, ProductionItems.ZOMBIE_VILLAGER, List.of(ProductionItems.ZOMBIE, ProductionItems.SKELETON),
                    Buildings.DUNGEON, ProductionItems.EMBALMER,
                    List.of(ProductionItems.CREEPER, ProductionItems.WRAITH, ProductionItems.WARDEN, ProductionItems.OSSUARY_BEHEMOTH),
                    Buildings.STRONGHOLD, ProductionItems.BONE_DRAGON, Buildings.ENERGY_CONVERTER_MONSTERS);
        if (faction.equals(Factions.VILLAGERS))
            return new Kit(Buildings.TOWN_CENTRE, null, null,   // no house or farm any more (Factions)
                Buildings.METAL_EXTRACTOR_VILLAGERS, Buildings.WIND_GENERATOR_VILLAGERS, Buildings.BARRACKS,
                Buildings.WATCHTOWER, ProductionItems.VILLAGER, List.of(ProductionItems.VINDICATOR, ProductionItems.PILLAGER),
                Buildings.ARCANE_TOWER, ProductionItems.ROYAL_ARCHITECT,
                List.of(ProductionItems.WITCH, ProductionItems.IRON_GOLEM, ProductionItems.RAVAGER, ProductionItems.ORIFLAMME_ELEPHANT),
                Buildings.CASTLE, ProductionItems.SUN_COLOSSUS, Buildings.ENERGY_CONVERTER_VILLAGERS);
        // the Verdant Court has no farm, house or tower yet - every use of those below checks for null; it techs to its
        // Circle of Elders and then, like the others, to the Heart of the Wild and the World Tree Walker
        if (faction.equals(Factions.VERDANT_COURT))
            return new Kit(Buildings.HEARTWOOD_HALL, null, null,
                Buildings.METAL_EXTRACTOR_VERDANT, Buildings.WIND_GENERATOR_VERDANT, Buildings.GROVE,
                null, ProductionItems.SEEDSHAPER,
                List.of(ProductionItems.LEAFBLADE, ProductionItems.THORNBOW, ProductionItems.SENTINEL_TREANT,
                        ProductionItems.HIVE_KEEPER, ProductionItems.MOONWELL_BEARER),
                Buildings.CIRCLE_OF_ELDERS, ProductionItems.ELDER_DRUID,
                List.of(ProductionItems.STAG_LANCER, ProductionItems.SHADE_RANGER, ProductionItems.ELDER_TREANT,
                        ProductionItems.BLOOM_PRIESTESS, ProductionItems.MOSSBACK_BISON),   // the Wisp Choir is queued only against flyers (queueAntiAir)
                Buildings.HEART_OF_THE_WILD, ProductionItems.WORLD_TREE_WALKER, Buildings.ENERGY_CONVERTER_VERDANT);
        // the Tidewrought have no farm, house or tower - every use of those checks for null. Tier 2 brings the Drydock:
        // the Harbourmaster and a T2 army that leans on the Kraken Caller (it grabs priority targets: tideKrakens) with
        // Mortar Galleys behind and a Sky Corsair now and then; Tide Priests ride along (tideHabits). Tier 3: the
        // Harbourmaster lays down the Abyssal Shipyard and it raises Leviathans (tideLeviathans slams with them)
        if (faction.equals(Factions.TIDEWROUGHT))
            return new Kit(Buildings.WRECK_HARBOUR, null, null,
                Buildings.METAL_EXTRACTOR_TIDE, Buildings.WIND_GENERATOR_TIDE, Buildings.SLIPWAY,
                null, ProductionItems.SHIPWRIGHT,
                List.of(ProductionItems.CUTLASS_RAIDER, ProductionItems.REEF_GUARD, ProductionItems.REEF_GUARD,
                        ProductionItems.BOMBARD_CREW, ProductionItems.TIDE_PRIEST, ProductionItems.HARPOONER,
                        ProductionItems.MUDSKIPPER_SLOOP),   // the Flak Swivel only against flyers (queueAntiAir)
                Buildings.DRYDOCK, ProductionItems.HARBOURMASTER,
                List.of(ProductionItems.KRAKEN_CALLER, ProductionItems.KRAKEN_CALLER, ProductionItems.MORTAR_GALLEY,
                        ProductionItems.MORTAR_GALLEY, ProductionItems.SKY_CORSAIR,
                        ProductionItems.SHELLBACK_TURTLE),   // beasts-2 T2 beast
                Buildings.ABYSSAL_SHIPYARD, ProductionItems.LEVIATHAN, Buildings.ENERGY_CONVERTER_TIDE);
        // the Stormcallers have no farm, house or tower - every use of those checks for null - so the bot plays an
        // economy and a T1 army (Jade Guards in front, Fire Adepts behind, Blade Dancers diving their archers, a Frost
        // Adept, a Rocket Battery and an Ink Scribe now and then; Wind Monks only against flyers). Tier 2 brings the
        // Celestial Observatory (slice 3): the Ley Master and a T2 army of Earthshakers in front, a Thunder Dragon, a
        // Gravity Monk and a Mirror Mage. The Thunder Sage's spells, Overcharge, Seal, Frost Field, Ward, the Geomancers'
        // ley links and the T2 spells (Singularity into Judgement, Mirror Images, Ground Slam, Mana Wells) are stormHabits.
        // Slice 4: a Seer, a Lantern Fortress and a Phoenix join the T2 mix (stormDomes keeps the Fortress's dome up in a
        // fight); Tier 3: the Ley Master raises Heaven's Gate and it makes Celestial Dragons (stormTempest casts with them)
        if (faction.equals(Factions.STORMCALLERS))
            return new Kit(Buildings.JADE_PAGODA, null, null,
                Buildings.METAL_EXTRACTOR_STORM, Buildings.WIND_GENERATOR_STORM, Buildings.LOTUS_ACADEMY,
                null, ProductionItems.GEOMANCER,
                List.of(ProductionItems.JADE_GUARD, ProductionItems.JADE_GUARD, ProductionItems.FIRE_ADEPT,
                        ProductionItems.FIRE_ADEPT, ProductionItems.BLADE_DANCER, ProductionItems.FROST_ADEPT,
                        ProductionItems.ROCKET_BATTERY, ProductionItems.INK_SCRIBE),   // the Wind Monk only against flyers (queueAntiAir)
                Buildings.CELESTIAL_OBSERVATORY, ProductionItems.LEY_MASTER,
                List.of(ProductionItems.EARTHSHAKER, ProductionItems.EARTHSHAKER, ProductionItems.THUNDER_DRAGON,
                        ProductionItems.GRAVITY_MONK, ProductionItems.MIRROR_MAGE, ProductionItems.LANTERN_FORTRESS,
                        ProductionItems.PHOENIX, ProductionItems.SEER, ProductionItems.JADE_QILIN),   // beasts-2 T2 beast
                Buildings.HEAVENS_GATE, ProductionItems.CELESTIAL_DRAGON, Buildings.ENERGY_CONVERTER_STORM);
        // the Brassworks have no farm, house or tower - every use of those checks for null - so the bot plays an economy
        // and a T1 army (Iron Wardens in front, Rifle Squads behind, Steam Runners raiding, a Mortar Crawler, a Shield
        // Projector and a Sapper now and then; Flak Carts only against flyers). Tier 2 (slice 2) brings the Assembly
        // Works: the Engineer-Captain and a T2 army of Steam Juggernauts in front, a Tesla Walker, a Clockwork Spider, a
        // Gunship, a Drill Burrower and a Repair Crawler. Tier 3 (slice 4): the Engineer-Captain raises the Great Forge and
        // it casts Iron Leviathan Engines (the shared fieldExperimentals; the engine launches its own drones).
        // The Guildmaster's Overclock and Deploy Turret, the Tinkers' Field Upgrade and the Burrowers' dives are brassHabits.
        if (faction.equals(Factions.BRASSWORKS))
            return new Kit(Buildings.FOUNDRY_HALL, null, null,
                Buildings.METAL_EXTRACTOR_BRASS, Buildings.WIND_GENERATOR_BRASS, Buildings.MACHINE_SHOP,
                null, ProductionItems.TINKER,
                List.of(ProductionItems.IRON_WARDEN, ProductionItems.IRON_WARDEN, ProductionItems.RIFLE_SQUAD,
                        ProductionItems.RIFLE_SQUAD, ProductionItems.STEAM_RUNNER, ProductionItems.MORTAR_CRAWLER,
                        ProductionItems.SHIELD_PROJECTOR, ProductionItems.SAPPER),   // the Flak Cart only against flyers (queueAntiAir)
                Buildings.ASSEMBLY_WORKS, ProductionItems.ENGINEER_CAPTAIN,
                List.of(ProductionItems.STEAM_JUGGERNAUT, ProductionItems.STEAM_JUGGERNAUT, ProductionItems.TESLA_WALKER,
                        ProductionItems.CLOCKWORK_SPIDER, ProductionItems.GYROCOPTER_GUNSHIP, ProductionItems.DRILL_BURROWER,
                        ProductionItems.REPAIR_CRAWLER, ProductionItems.STEAM_HAULER),   // beasts-2 T2 beast
                Buildings.GREAT_FORGE, ProductionItems.IRON_LEVIATHAN_ENGINE, Buildings.ENERGY_CONVERTER_BRASS);
        return null;
    }

    /** The faction's static T2 defence the bot raises after Tier 2, or null (the Court's and the Tidewrought's so far). */
    public static Building t2TowerFor(Faction faction) {
        if (Factions.VERDANT_COURT.equals(faction))
            return Buildings.STORM_OAK;
        if (Factions.TIDEWROUGHT.equals(faction))
            return Buildings.LIGHTHOUSE_BATTERY;
        if (Factions.BRASSWORKS.equals(faction))
            return Buildings.RAIL_BATTERY;   // slice 3
        return null;
    }

    /** The faction's storage building (BAR storage), or null. Outside the Kit so adding it touched no kit. */
    public static Building vaultFor(Faction faction) {
        if (Factions.VILLAGERS.equals(faction)) return Buildings.STORAGE_VAULT_VILLAGERS;
        if (Factions.MONSTERS.equals(faction)) return Buildings.STORAGE_VAULT_MONSTERS;
        if (Factions.PIGLINS.equals(faction)) return Buildings.STORAGE_VAULT_PIGLINS;
        if (Factions.VERDANT_COURT.equals(faction)) return Buildings.STORAGE_VAULT_VERDANT;
        if (Factions.TIDEWROUGHT.equals(faction)) return Buildings.STORAGE_VAULT_TIDE;
        if (Factions.STORMCALLERS.equals(faction)) return Buildings.STORAGE_VAULT_STORM;
        if (Factions.BRASSWORKS.equals(faction)) return Buildings.STORAGE_VAULT_BRASS;
        return null;
    }

    // game time since when metal or energy has sat at the storage cap without a break (-1 = not at cap)
    long atCapSince = -1;
    static final long VAULT_AFTER_TICKS = 20 * 20;   // 20 s at the cap and the bot raises a vault

    public final String name;
    public final Faction faction;
    public final Difficulty difficulty;
    /** null when bots can't play this faction (kitFor); think() then does nothing. */
    final Kit kit;
    int capitolFailures = 0;
    /** Consecutive thinks with no worker and no finished capitol, and how often the start was re-seated (lostStart). */
    int lostStartThinks = 0, startReseeds = 0;
    static final int LOST_START_THINKS = 10, MAX_START_RESEEDS = 2;
    static final long LOST_START_WINDOW = 20 * 60 * 3;   // only in a bot's first three minutes (from startedAtTick)
    final Random rng = new Random();
    BlockPos home;
    long startedAt = -1;

    /** The difficulty's knobs (design/bot.md) and the army state machine. */
    public final BotTuning tuning;
    public final BotArmy army;
    /** Capture sites on the pre-made maps: take, hold, defend, retake (design/bot.md "Capture sites"). */
    public final BotSites sites;
    /** Geothermal on lava, trees for energy, decommission, the upgrade-or-new-lab rule (design/bot.md "Map economy"). */
    public final BotEconomy economy;
    /** Build sites that never rise: reported, cancelled and (the capitol) re-laid a step away (BotSiteWatch). */
    public final BotSiteWatch siteWatch;

    public BotPlayer(String name, Faction faction, Difficulty difficulty, BlockPos home) {
        this.name = name;
        this.faction = faction;
        this.difficulty = difficulty;
        this.kit = kitFor(faction);
        this.home = home;
        this.tuning = BotTuning.of(difficulty);
        this.army = new BotArmy(this, tuning);
        this.sites = new BotSites(this);
        this.economy = new BotEconomy(this);
        this.siteWatch = new BotSiteWatch(this);
    }

    // playtest (Oct 10): two hard bots with 9 workers and one build site at a time were crushed by a player with
    // 80+ workers. Workers now grow with the clock and with income (BAR players keep making constructors as their
    // eco grows), and harder bots run several build sites at once - more while metal floats.
    long minutesIn = 0;
    /** Metal income per second at the last think (recalculateIncomeAndStorage's figure, converters included). */
    float metalIncome = 0;
    /** Stored metal / energy as a fraction of storage at the last think (the game tests set them). */
    public float metalFill = 0, energyFill = 0;
    /** Tier 3 researched, at the last think (the Ancient Forge is worth less once it is). */
    boolean hasTier3 = false;
    /** A built army lab stood at the last think. */
    boolean labBuilt = false;
    int targetWorkers() {
        // floating with sites waiting is a build-power problem (the flow economy only pays as blocks go up), so a
        // floating bot wants four more constructors on top of the clock and income rule
        int want = (int) Math.min(tuning.workerCap(), tuning.workerBase() + minutesIn * tuning.workerPerMinute() / 2
            + (tuning.workerPerIncome() > 0 ? (int) (metalIncome / tuning.workerPerIncome()) : 0) + (floating() ? 4 : 0));
        // no lab standing past minute 3: constructors scale with the economy, and an economy with no army behind it
        // is 30 workers waiting to be farmed (the Oct 13 ally bot) - a few over the base, and the metal stays for the lab
        if (!labBuilt && minutesIn >= 3)
            want = Math.min(want, tuning.workerBase() + 3);
        return want;
    }
    int maxConcurrentSites() {
        return tuning.sitesBase() + (int) (minutesIn / 8) + (floating() ? tuning.floatSites() : 0);
    }
    /** Floating: stored metal above {@link BotTuning#FLOAT_FRAC} of storage. A hard bot treats this as an alarm. */
    boolean floating() { return metalFill > BotTuning.FLOAT_FRAC; }
    /**
     * Army labs wanted now: one per labsPerMinutes, two more while floating, up to maxLabs. Two more than the clock,
     * not "one more than we have": a soak run with the latter added a lab every think the pool stayed full and had
     * six labs and no energy by minute 4. Not before minute 3 either: every bot starts with its pool at the cap
     * (STARTING_METAL is the default storage), and the first minutes belong to extractors and wind.
     */
    int targetLabs(int labs, long minutes) {
        int want = 1 + (int) (minutes / tuning.labsPerMinutes());
        if (floating() && minutes >= 3)
            want += 2;
        return Math.min(tuning.maxLabs(), want);
    }
    /** The Horde's lab is a Basic Portal refitted to a Military Portal (the Bastion only researches), so count both. */
    boolean isLab(BuildingPlacement bp) {
        if (bp.decommissioning)
            return false;   // being taken apart: no units, no levels, and it no longer counts toward the labs wanted
        Building b = bp.getBuilding();
        return b == kit.armyBuilding() || (kit.armyBuilding() == Buildings.PORTAL_BASIC && b == Buildings.PORTAL_MILITARY);
    }
    // ---- the tier gate (resources/TierGate): a bot techs when it is ready, not just when the clock says so ----

    /**
     * Whether the bot may start Tier 2: it has a finished army lab, has fielded {@code t2MinArmy} fighters (the army comes
     * first - the tier gate's whole point) and its income can pay the research: TierGate.BOT_MIN_METAL_INCOME and
     * BOT_MIN_ENERGY_INCOME. {@link BotTuning#T2_PATIENCE_MINUTES} after the earliest minute it goes anyway with a lab
     * and half the army, so a map with few patches cannot stop it for ever.
     */
    boolean t2Ready(long minutes, int fighters, float energyIncome, List<BuildingPlacement> buildings) {
        boolean labBuilt = false;
        for (BuildingPlacement bp : buildings)
            if (bp.isBuilt && isLab(bp))
                labBuilt = true;
        if (!labBuilt)
            return false;
        if (minutes >= tuning.t2Minute() + BotTuning.T2_PATIENCE_MINUTES)
            return fighters >= tuning.t2MinArmy() / 2;
        return fighters >= tuning.t2MinArmy()
            && metalIncome >= com.solegendary.reignofnether.resources.TierGate.BOT_MIN_METAL_INCOME
            && energyIncome >= com.solegendary.reignofnether.resources.TierGate.BOT_MIN_ENERGY_INCOME;
    }

    /**
     * The energy income (per second) the bot wants right now for its next tech step, or 0 for none: the research's
     * before Tier 2 once it is within two minutes of its earliest start, the refits' after it.
     */
    float techEnergyTarget(boolean tier2, List<BuildingPlacement> buildings) {
        if (!tier2)
            return minutesIn + 2 >= tuning.t2Minute() ? com.solegendary.reignofnether.resources.TierGate.BOT_MIN_ENERGY_INCOME : 0f;
        int refitted = 0;
        for (BuildingPlacement bp : buildings)
            if (bp.isBuilt && bp.getUpgradeLevel() > 0 && bp.getBuilding() instanceof com.solegendary.reignofnether.building.buildings.shared.MetalExtractor)
                refitted++;
        int homeExtractors = 0;
        for (BuildingPlacement bp : buildings)
            if (bp.isBuilt && bp.getBuilding() instanceof com.solegendary.reignofnether.building.buildings.shared.MetalExtractor
                    && bp.originPos.distSqr(home) < 45 * 45)
                homeExtractors++;
        return refitted >= homeExtractors ? 0f
            : com.solegendary.reignofnether.resources.TierGate.botEnergyIncomeForRefit(refitted + 1);
    }

    /**
     * Energy income at the AVERAGE wind: every finished wind generator counted at Wind.MEAN instead of what the wind blows
     * right now (it swings 4-24, so the live figure would hold a bot's tech back for minutes at a time on a calm spell),
     * and with {@code rising} the generators still being built too, so one shortfall is one wind site, not twelve.
     */
    float averageEnergyIncome(float energyIncome, List<BuildingPlacement> buildings, boolean rising) {
        float avg = energyIncome;
        for (BuildingPlacement bp : buildings) {
            if (bp.getBuilding() != kit.wind())
                continue;
            if (bp.isBuilt)
                avg += com.solegendary.reignofnether.resources.Wind.MEAN - bp.getEnergyIncome();
            else if (rising)
                avg += com.solegendary.reignofnether.resources.Wind.MEAN;
        }
        return avg;
    }

    float plannedEnergyIncome(float energyIncome, List<BuildingPlacement> buildings) {
        return averageEnergyIncome(energyIncome, buildings, true);
    }

    // ---- building levels (design/building_upgrades.md): with two labs, level them up instead of spamming more ----

    /** The labs that can still level up (built, below their max level, not a Basic Portal awaiting its refit). */
    boolean anyLabCanLevel(List<BuildingPlacement> buildings) {
        for (BuildingPlacement bp : buildings)
            if (bp.isBuilt && isLab(bp) && bp.getBuilding().maxLevel > 1 && bp.getBuildingLevel() < bp.getBuilding().maxLevel)
                return true;
        return false;
    }

    /** Two labs and room to level: the next lab is a level, not a new building (lovish: less clutter). */
    boolean levelInsteadOfLab(int labs, List<BuildingPlacement> buildings) {
        return labs >= 2 && anyLabCanLevel(buildings);
    }

    /**
     * One lab upgrade at a time: the lowest-level lab gets the next level's order (it waits behind the units already
     * queued, and the lab makes nothing while it rebuilds itself) when BotEconomy's upgrade-or-new-lab rule says the
     * level beats another lab and the bot can pay for it. Returns true when it queued one. Public for the game test.
     */
    public boolean queueLabLevel(List<BuildingPlacement> buildings) {
        if (countLabs(buildings) < 2)
            return false;
        ProductionPlacement best = null;
        for (BuildingPlacement bp : buildings) {
            if (!bp.isBuilt || !isLab(bp) || !(bp instanceof ProductionPlacement pp) || bp.getBuilding().maxLevel <= 1)
                continue;
            for (var ap : pp.productionQueue)
                if (ap.item instanceof com.solegendary.reignofnether.research.researchItems.UpgradeLabLevel)
                    return false;   // one at a time
            if (bp.getBuildingLevel() >= bp.getBuilding().maxLevel)
                continue;
            if (best == null || bp.getBuildingLevel() < best.getBuildingLevel())
                best = pp;
        }
        com.solegendary.reignofnether.resources.Resources res = pool();
        if (best == null || !economy.levelBeatsNewLab(best, res == null ? 0 : res.getMetal()))
            return false;
        return best.startProductionItem(best.getBuildingLevel() == 1 ? ProductionItems.UPGRADE_LAB_L2 : ProductionItems.UPGRADE_LAB_L3);
    }

    private Building annex = null;
    private boolean annexLooked = false;

    /** This bot's faction annex (the LabAnnex whose host is its army lab), or null. */
    Building annexFor() {
        if (!annexLooked) {
            annexLooked = true;
            for (Building b : com.solegendary.reignofnether.api.ReignOfNetherRegistries.BUILDING)
                if (b instanceof com.solegendary.reignofnether.building.buildings.shared.LabAnnex la
                        && (la.hostLab == kit.armyBuilding()
                            || (kit.armyBuilding() == Buildings.PORTAL_BASIC && la.hostLab == Buildings.PORTAL_MILITARY)))
                    annex = b;
        }
        return annex;
    }

    /** A levelled lab short of annexes (one at L2, two at L3), or null. */
    BuildingPlacement labWantingAnnex(List<BuildingPlacement> buildings) {
        Building a = annexFor();
        if (a == null)
            return null;
        for (BuildingPlacement lab : buildings) {
            if (!lab.isBuilt || !isLab(lab) || lab.getBuilding().maxLevel <= 1 || lab.getBuildingLevel() < 2)
                continue;
            int want = lab.getBuildingLevel() >= 3 ? 2 : 1, have = 0;
            for (BuildingPlacement o : buildings)
                if (o.getBuilding() == a && com.solegendary.reignofnether.building.buildings.shared.LabAnnex.hostOf(o) == lab)
                    have++;
            if (have < want)
                return lab;
        }
        return null;
    }

    int countLabs(List<BuildingPlacement> buildings) {
        int n = 0;
        for (BuildingPlacement bp : buildings)
            if (isLab(bp))
                n++;
        return n;
    }

    /**
     * The bot's build-order clock in "economy minutes": real minutes times the match's economy pace (EconomyPace).
     * Every timing rule (labs, extractors, T2, attack waves) was tuned at Standard; on Relaxed the economy behind
     * those rules arrives later, so the clock runs slower with it, and faster on Fast.
     */
    long paceMinutes(long gameTime) {
        return (long) ((gameTime - startedAt) * com.solegendary.reignofnether.resources.EconomyPace.of(name) / (20 * 60));
    }

    // ------------------------------------------------------------------ the think step, about once a second

    /**
     * True when this think re-seated the bot's starting workers (see think step 0). Logs what it found, so a
     * playtest log says why a start was lost (no units at all = they never joined or died; the home chunk's state).
     */
    boolean lostStart(ServerLevel level, long gameTime, List<LivingEntity> workers, BuildingPlacement capitol, int unitCount) {
        if (!workers.isEmpty() || (capitol != null && capitol.isBuilt) || home == null) {
            lostStartThinks = 0;
            return false;
        }
        // only a bot seated through the real start (startRTSBot stamps startedAtTick), early in its match: hand-built
        // test sides and bots later wiped out by an enemy are left alone
        Long seatedAt = com.solegendary.reignofnether.player.PlayerServerEvents.startedAtTick.get(name);
        if (seatedAt == null || startReseeds >= MAX_START_RESEEDS || gameTime - seatedAt > LOST_START_WINDOW)
            return false;
        // halfway: reload and hold the home chunks first. Units that left with an unloading chunk are not dead (no
        // defeat, UnitServerEvents) but drop out of the unit list, so nothing would ever ask for their chunk again;
        // holding it brings them back and the bot simply carries on
        if (++lostStartThinks == LOST_START_THINKS / 2) {
            com.solegendary.reignofnether.unit.UnitChunkLoader.holdArea(level, home);
            com.solegendary.reignofnether.ReignOfNether.LOGGER.warn("[Bot] {} has no workers and no capitol: reloading its home chunks at {}",
                name, home);
        }
        if (lostStartThinks < LOST_START_THINKS)
            return false;
        lostStartThinks = 0;
        startReseeds++;
        boolean ticking = level.isPositionEntityTicking(home);
        int added = com.solegendary.reignofnether.player.PlayerServerEvents.spawnBotStartUnits(level, name, faction,
            net.minecraft.world.phys.Vec3.atBottomCenterOf(home), true);
        com.solegendary.reignofnether.ReignOfNether.LOGGER.warn(
            "[Bot] {} lost its start (no workers, capitol {}, {} other units, home {} entity-ticking {}): re-seated {} workers ({}/{})",
            name, capitol == null ? "none" : "unbuilt", unitCount, home, ticking, added, startReseeds, MAX_START_RESEEDS);
        return true;
    }

    public void think(ServerLevel level, long gameTime) {
        if (kit == null)
            return;
        if (startedAt < 0)
            startedAt = gameTime;
        minutesIn = paceMinutes(gameTime);
        List<LivingEntity> mine = new ArrayList<>(), workers = new ArrayList<>(), army = new ArrayList<>();
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (!(le instanceof Unit u) || !name.equals(u.getOwnerName()) || !le.isAlive())
                continue;
            mine.add(le);
            if (le instanceof WorkerUnit) workers.add(le);
            else if (le instanceof AttackerUnit) army.add(le);
        }
        List<BuildingPlacement> buildings = new ArrayList<>();
        BuildingPlacement capitol = null;
        int unbuilt = 0;
        for (BuildingPlacement bp : BuildingServerEvents.getBuildings()) {
            if (!name.equals(bp.ownerName) || bp.isDestroyedServerside)
                continue;
            buildings.add(bp);
            if (bp.getBuilding().isCapitol)
                capitol = bp;
            if (!bp.isBuilt)
                unbuilt++;
        }
        // sites ordered but not started (no worker has reached them yet: building/Blueprints) are sites like any other for
        // the bot: they count toward the allowance and the layout, get builders, and are watched and cancelled when stuck.
        // Without this the next think would not see its own capitol order and lay another
        for (BuildingPlacement bp : com.solegendary.reignofnether.building.Blueprints.pendingOf(name)) {
            buildings.add(bp);
            if (bp.getBuilding().isCapitol)
                capitol = bp;
            unbuilt++;
        }
        if (capitol != null)
            home = capitol.originPos;
        BotCombatEngineers.think(this, level, gameTime, workers, army, buildings);   // takes them out of workers (design/combat_engineers.md)

        // 0) a lost start: no worker and no finished capitol means nothing can ever be built or trained again, and
        // the bot would sit at +0 income with an untouched pool for the whole match (lovish's Oct 12 Watch Battle:
        // three of four hard bots did exactly that). Early on, re-seat the three starting workers at home.
        if (lostStart(level, gameTime, workers, capitol, mine.size()))
            return;

        // 1) a capitol above all else. A player's readied start lays its capitol regardless of terrain; a bot gets
        // the same treatment if the ground round its home refuses every site (hills, trees, water), instead of
        // idling for the whole match
        if (capitol == null) {
            // (a capitol site that never rose was cancelled by the site watch: the next one is wanted a step away)
            BlockPos capitolWant = siteWatch.capitolWant(home);
            if (!workers.isEmpty() && !placeNear(level, kit.capitol(), capitolWant, workers, 3)) {
                capitolFailures++;
                if (capitolFailures == 1 || capitolFailures % 20 == 0) {
                    BlockPos surface = MiscUtil.getHighestNonAirBlock(level, capitolWant, true);
                    String why = BuildingValidators.getPlacementValidityError(level, kit.capitol(), surface, name,
                        Rotation.NONE, false, false, true);
                    com.solegendary.reignofnether.ReignOfNether.LOGGER.warn("[Bot] {} cannot place its capitol near {}: {}",
                        name, capitolWant, why);
                    // (placeNear already logged the block; keep the validator's own words for the soak summary)
                    lastBlockedReason = kit.capitol().structureName + ": cannot place near " + capitolWant.toShortString() + ": " + why;
                }
                if (capitolFailures >= 3) {
                    BlockPos surface = MiscUtil.getHighestNonAirBlock(level, capitolWant, true);
                    int take = Math.min(3, workers.size());
                    int[] ids = new int[take];
                    for (int i = 0; i < take; i++)
                        ids[i] = workers.get(i).getId();
                    BuildingServerEvents.placeBuilding(kit.capitol(), surface, Rotation.NONE, name, ids,
                        false, false, true, true);
                    com.solegendary.reignofnether.ReignOfNether.LOGGER.info("[Bot] {} laid its capitol by decree at {}", name, surface);
                }
            }
            return;
        }

        // the economy snapshot every step below reads: income, and how full the two pools are
        var eco = com.solegendary.reignofnether.resources.EconomyServerEvents.getEconomy(name);
        com.solegendary.reignofnether.resources.Resources res = pool();
        metalIncome = eco.effectiveMetalIncome() + eco.metalConverted;
        metalFill = res == null ? 0 : res.getMetal() / Math.max(1f, eco.metalStorage);
        energyFill = res == null ? 0 : res.getEnergy() / Math.max(1f, eco.energyStorage);

        // 2) workers from the capitol
        trainWorkers(capitol, workers.size());

        // 3) build sites need builders: any unbuilt or damaged building gets the nearest idle worker
        assignBuilders(level, buildings, workers);

        // 3a) a site that has not risen a block in 45 s with a builder free for it is stuck (the Oct 13 soak: a
        // Tidewrought capitol and two extractors sat unbuilt for four minutes, filled the bot's site allowance and
        // it never placed, trained or logged again). The watch says why, cancels the site for its refund and, for
        // the capitol, lays the next one a step away; the think step starts over on the next second
        if (siteWatch.think(level, gameTime, buildings, workers))
            return;

        // 3b) reclaim: idle workers walk to the nearest wrecks in our territory (BAR players do this by habit)
        sendReclaimer(workers);

        // storage watch (every think, whether or not a site is free): how long a pool has been pinned at its cap
        if (atStorageCap()) {
            if (atCapSince < 0)
                atCapSince = gameTime;
        } else
            atCapSince = -1;

        // 4) expand the economy (a few sites at a time - more for harder bots, more again while floating - so the
        // flow economy isn't buried)
        long minutes = paceMinutes(gameTime);
        boolean tier2 = com.solegendary.reignofnether.research.ResearchServerEvents.playerHasResearch(name,
                com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_2);
        hasTier3 = tier2 && com.solegendary.reignofnether.research.ResearchServerEvents.playerHasResearch(name,
                com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_3);
        int armyBuildings = countLabs(buildings);
        labBuilt = false;
        for (BuildingPlacement bp : buildings)
            if (bp.isBuilt && isLab(bp))
                labBuilt = true;
        if (unbuilt < maxConcurrentSites()) {
            int extractors = count(buildings, kit.extractor()), winds = count(buildings, kit.wind());
            int farms = count(buildings, kit.farm());
            // Each candidate build is tried in priority order and a failed placement FALLS THROUGH to the next one
            // (the soak test caught bots sitting on 850 metal forever because one bad patch blocked every build).
            // One placement per think, so the order IS the priority: a rule that fires every think starves the
            // rules under it (a soak run with "every free patch while floating" ahead of the lab rule built 16
            // extractors and windmills and no lab at all).
            boolean done = workers.isEmpty();
            if (done)
                blocked(gameTime, "build", "no workers alive (" + unbuilt + " unbuilt site(s), capitol "
                    + (capitol.isBuilt ? "built" : "unbuilt") + ")");
            // floating metal: the first answer is another lab (army is pressure), before anything else
            boolean levelInstead = levelInsteadOfLab(armyBuildings, buildings);
            boolean floatLab = floating() && minutes >= 3 && armyBuildings < targetLabs(armyBuildings, minutes) && !levelInstead;
            if (!done && floatLab)
                done = placeLab(level, armyBuildings, workers);
            // floating with levelled labs: an annex beside one (BAR's nano turret, in-faction)
            if (!done && floating()) {
                BuildingPlacement annexLab = labWantingAnnex(buildings);
                if (annexLab != null)
                    done = placeNear(level, annexFor(), annexLab.centrePos, workers, 1);
            }
            // energy starved (under 30% with metal to spare): wind first, or every lab stalls
            if (!done && energyFill < 0.3f && metalFill > 0.2f && winds <= extractors * 2 + 2)
                done = placeNear(level, kit.wind(), layoutSlot(Layout.WIND, winds), workers, 1);
            // the first lab from minute 1, ahead of the economy rules: the army has to start somewhere
            if (!done && armyBuildings < 1 && minutes >= 1)
                done = placeLab(level, 0, workers);
            // extractors: a couple plus one a minute (every free patch there is while floating - after the labs below)
            BlockPos patch = extractors < 2 + minutes ? freePatch(level) : null;
            // the T2 lab first once Tier 2 is researched, after its faction's FINISHED T1 lab - the server refuses it
            // otherwise (resources/TierGate). That is the army lab for every faction but the Horde, whose Flame Sanctuary
            // wants a Hoglin Stables: raised as soon as the research starts, so it stands when the research is done
            Building t1Req = kit.t2Lab() == null ? null : com.solegendary.reignofnether.resources.TierGate.t1LabFor(kit.t2Lab());
            boolean researchingT2 = com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_2.itemIsBeingProduced(false, name);
            if (!done && t1Req != null && t1Req != kit.armyBuilding() && (tier2 || researchingT2) && armyBuildings >= 1
                    && count(buildings, t1Req) < 1)
                done = placeTechBuilding(level, t1Req, armyBuildings + 1, workers, 1);
            if (!done && tier2 && kit.t2Lab() != null && count(buildings, kit.t2Lab()) < 1
                    && t1Req != null && com.solegendary.reignofnether.resources.TierGate.ownsFinished(name, t1Req))
                done = placeTechBuilding(level, kit.t2Lab(), armyBuildings + 2, workers, 3);   // 3 builders: the lab is 504 builder-seconds
            // a Geothermal Plant on lava near home once a T2 constructor is out (T2 energy: a field of wind in one)
            if (!done && count(buildings, kit.t2Lab()) >= 1)
                done = economy.placeGeothermal(level, gameTime, tier2, buildings, workers);
            // steady energy (claude/solar-spec.md): a Solar Collector once a lab stands and the wind field is carrying the base,
            // at most one per two winds and never while energy is plentiful; an Advanced Solar from a T2 constructor at Tier 2
            if (!done && armyBuildings >= 1 && winds >= 4 && metalFill > 0.3f && energyFill < 0.6f) {
                var solar = com.solegendary.reignofnether.building.buildings.shared.SolarGenerator.of(faction,
                    com.solegendary.reignofnether.building.buildings.shared.SolarGenerator.Kind.BASIC);
                int solars = solar == null ? 0 : count(buildings, solar);
                if (solar != null && solars < winds / 2)
                    done = placeNear(level, solar, layoutSlot(Layout.WIND, winds + solars), workers, 1);
            }
            if (!done && tier2 && kit.t2Lab() != null && count(buildings, kit.t2Lab()) >= 1 && metalFill > 0.4f && energyFill < 0.5f) {
                var adv = com.solegendary.reignofnether.building.buildings.shared.SolarGenerator.of(faction,
                    com.solegendary.reignofnether.building.buildings.shared.SolarGenerator.Kind.ADVANCED);
                int advs = adv == null ? 0 : count(buildings, adv);
                List<LivingEntity> t2Builders = new ArrayList<>();
                for (LivingEntity w : workers)
                    if (com.solegendary.reignofnether.unit.T2Workers.isT2Worker(w))
                        t2Builders.add(w);
                if (adv != null && advs < Math.min(4, 1 + minutes / 8) && !t2Builders.isEmpty())
                    done = placeNear(level, adv, layoutSlot(Layout.WIND, winds + 8 + advs * 2), t2Builders, 1);
            }
            // a T2 defence (the Court's Storm Oak) once the T2 lab stands: one, a second from minute 12, both a dozen
            // blocks from home toward the nearest enemy (the second offset to the side so they cover a wider front)
            Building t2Tower = t2TowerFor(faction);
            int t2Towers = t2Tower == null ? 0 : count(buildings, t2Tower);
            if (!done && t2Tower != null && tier2 && count(buildings, kit.t2Lab()) >= 1 && t2Towers < (minutes >= 12 ? 2 : 1)) {
                BlockPos threat = nearestEnemyBuilding(home);
                BlockPos want = home;
                if (threat != null) {
                    double dx = threat.getX() - home.getX(), dz = threat.getZ() - home.getZ();
                    double len = Math.max(1, Math.hypot(dx, dz));
                    int side = t2Towers == 0 ? 0 : 9;
                    want = home.offset((int) (dx / len * 12 - dz / len * side), 0, (int) (dz / len * 12 + dx / len * side));
                }
                done = placeNear(level, t2Tower, want, workers, 1);
            }
            // big-units: a Ward Spire against an enemy game ender; a hard bot floating at Tier 3 builds its own
            if (!done)
                done = BotGameEnders.think(this, level, buildings, workers);
            if (!done && patch != null) {
                done = placeExtractor(level, patch, workers);
                if (!done)
                    badPatches.put(patch, gameTime);   // retry it later, not every think
            }
            // the energy a tech step needs (resources/TierGate): before the research, ~110 energy/s for its drain plus the
            // base's own spending; after it, 20 energy/s for every extractor refit or the refits run on crumbs. Winds still
            // rising count at their average, so a bot does not pile a dozen sites on one shortfall
            float energyTarget = techEnergyTarget(tier2, buildings);
            if (!done && armyBuildings >= 1 && energyTarget > 0 && workers.size() >= 2 && winds < 40
                    && plannedEnergyIncome(eco.energyIncome, buildings) < energyTarget)
                done = placeNear(level, kit.wind(), layoutSlot(Layout.WIND, winds), workers, 1);
            if (!done && winds < 2 + minutes && winds <= extractors * 2 + 2)
                done = placeNear(level, kit.wind(), layoutSlot(Layout.WIND, winds), workers, 1);
            // converters whenever energy floats (BAR players do this): one more each time it is over 80% full
            if (!done && energyFull() && count(buildings, converterFor()) < 1 + (int) (minutes / 4))
                done = placeNear(level, converterFor(), layoutSlot(Layout.CONVERTER, count(buildings, converterFor())), workers, 1);
            // BAR players build storage when they float WITH a big income: pinned at the cap for 20 s with 10+ metal
            // a second coming in -> one vault, and the clock restarts so the next one needs another 20 s at the (now
            // higher) cap. A small-income bot at the cap has an idle-labs problem, not a storage problem
            Building vault = vaultFor(faction);
            if (!done && vault != null && atCapSince >= 0 && gameTime - atCapSince >= VAULT_AFTER_TICKS
                    && metalIncome >= VAULT_MIN_INCOME && count(buildings, vault) < 1 + (int) (minutes / 6)) {
                done = placeNear(level, vault, layoutSlot(Layout.VAULT, count(buildings, vault)), workers, 1);
                if (done)
                    atCapSince = -1;
            }
            if (!done && kit.farm() != null && farms < 1)
                done = placeNear(level, kit.farm(), layoutSlot(Layout.FARM, farms), workers, 1);
            // (no houses: there is no supply any more, like BAR)
            if (!done && armyBuildings < targetLabs(armyBuildings, minutes) && minutes >= 1 && !levelInstead)
                done = placeLab(level, armyBuildings, workers);
            // floating with the labs in hand: more income (every free patch) - never wrong in BAR
            if (!done && floating() && patch == null) {
                patch = freePatch(level);
                if (patch != null) {
                    done = placeExtractor(level, patch, workers);
                    if (!done)
                        badPatches.put(patch, gameTime);
                }
            }
            if (!done && kit.tower() != null && count(buildings, kit.tower()) < 1 + (int) (minutes / 5) && minutes >= 2) {
                // a tower between home and the nearest threat
                BlockPos threat = nearestEnemyBuilding(home);
                BlockPos want = home;
                if (threat != null) {
                    double dx = threat.getX() - home.getX(), dz = threat.getZ() - home.getZ();
                    double len = Math.max(1, Math.hypot(dx, dz));
                    want = home.offset((int) (dx / len * 20), 0, (int) (dz / len * 20));
                }
                placeNear(level, kit.tower(), want, workers, 1);
            }
            // static defences (design/defences.md): an anti-raid tower at each outlying extractor, a wall and gate
            // across the approach - the rules live in BotDefences
            if (!done)
                done = BotDefences.think(this, level, buildings, workers, home, minutes);
        }

        // 4b) tech: research tier 2 at the difficulty's minute (hard: 5 since Oct 24, so the T2 lab stands by ~9-10), then refit
        // home extractors one at a time
        // (not before eight workers: the research holds the capitol's queue for three minutes, and workers only come
        // from the capitol - a hard bot that researched at minute 3 with six workers froze its whole base)
        // ready to tech: the capitol stops training workers so its queue empties and the research can start (a hard bot's
        // worker target keeps growing, so its queue was never empty and it only teched after twelve minutes)
        techPending = !tier2 && minutes >= tuning.t2Minute() && workers.size() >= Math.min(targetWorkers(), 8)
            && t2Ready(minutes, army.size(), averageEnergyIncome(eco.energyIncome, buildings, false), buildings);
        if (techPending && capitol instanceof ProductionPlacement cp && cp.productionQueue.isEmpty())
            cp.startProductionItem(com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_2);
        if (tier2) {
            boolean upgrading = false;
            for (BuildingPlacement bp : buildings)
                if (bp.getBuilding() instanceof com.solegendary.reignofnether.building.buildings.shared.MetalExtractor
                        && bp instanceof ProductionPlacement pp && !pp.productionQueue.isEmpty())
                    upgrading = true;
            int refitted = 0;
            for (BuildingPlacement bp : buildings)
                if (bp.isBuilt && bp.getUpgradeLevel() > 0 && bp.getBuilding() instanceof com.solegendary.reignofnether.building.buildings.shared.MetalExtractor)
                    refitted++;
            // a refit only when the energy can carry one more drill (20 energy/s each): the tier gate's energy rule
            boolean energyCarries = averageEnergyIncome(eco.energyIncome, buildings, false) >= com.solegendary.reignofnether.resources.TierGate.botEnergyIncomeForRefit(refitted + 1);
            if (!upgrading && energyCarries)
                for (BuildingPlacement bp : buildings)
                    if (bp.isBuilt && bp.getUpgradeLevel() == 0 && bp.originPos.distSqr(home) < 45 * 45
                            && bp.getBuilding() instanceof com.solegendary.reignofnether.building.buildings.shared.MetalExtractor
                            && bp instanceof ProductionPlacement pp) {
                        pp.startProductionItem(com.solegendary.reignofnether.building.production.ProductionItems.UPGRADE_EXTRACTOR);
                        break;
                    }
        }

        // 4c) the commander's signature ability when a fight is on near it (same rules as a player: cooldown)
        useCommanderAbility(level, mine);
        useFactionPowers(level, mine);
        useTitanAbilities(level, mine);

        // 4d) level the labs up instead of building a third (design/building_upgrades.md)
        queueLabLevel(buildings);
        // 4e) the map economy: trees when energy-starved, obsolete wind sold late at the cap (BotEconomy)
        economy.fellTrees(level, gameTime, workers);
        economy.decommissionObsolete(gameTime, minutes, tier2, buildings);

        // 5) train fighters: every lab keeps queueDepth units queued (Repeat-style - the queue never runs dry
        // between units) in a mix the role table picks: anti-air against flyers, siege once defences are seen
        for (BuildingPlacement bp : buildings)
            if (bp.isBuilt && isLab(bp) && bp instanceof ProductionPlacement pp) {
                if (bp.getBuilding() == Buildings.PORTAL_BASIC) {
                    // the Horde: a Basic Portal trains nothing until it is refitted to a Military Portal
                    if (pp.productionQueue.isEmpty())
                        pp.startProductionItem(ProductionItems.RESEARCH_PORTAL_FOR_MILITARY);
                    continue;
                }
                pp.setRepeatQueue(true);
                queueAntiAir(pp, mine, gameTime);   // the Tidewrought's anti-air is T1 (the Court's is in its T2 lab)
                while (pp.productionQueue.size() < tuning.queueDepth() && pp.startProductionItem(pickArmyItem(kit.army(), mine, false)))
                    ;   // (startProductionItem false = can't afford population or the queue is full: stop)
            }

        // 5') the Verdant Court's slice-2 habits: snares at the home choke, an owl over it, healers with the army
        verdantHabits(level, mine, army, workers, buildings, gameTime, minutes);
        tideHabits(level, mine, army);
        tideFogBank(level, mine, army);
        tideKrakens(level, mine);
        tideLeviathans(level, mine);
        stormHabits(level, mine, army, gameTime);
        brassHabits(level, mine, gameTime);
        // the faction hero, mid-game: trained (or revived) at a lab that offers it (hero/HeroBot); it fights on its own
        com.solegendary.reignofnether.hero.HeroBot.think(name, faction, buildings, tier2, minutes);

        // 5a) the T2 lab: T2 constructors first (1, hard 2), then keeps T2 units coming. No repeat queue here, or
        // it would loop the constructor too - the queue is topped up to queueDepth by hand instead
        int t2Workers = 0;
        for (LivingEntity w : workers)
            if (com.solegendary.reignofnether.unit.T2Workers.isT2Worker(w))
                t2Workers++;
        for (BuildingPlacement bp : buildings)
            if (bp.isBuilt && bp.getBuilding() == kit.t2Lab() && bp instanceof ProductionPlacement pp) {
                if (t2Workers < targetT2Workers())
                    queueT2Worker(pp);
                queueAntiAir(pp, mine, gameTime);
                while (pp.productionQueue.size() < tuning.queueDepth() && pp.startProductionItem(pickArmyItem(kit.t2Army(), mine, true)))
                    ;
            }

        // 5b) late game: medium/hard bots raise their faction's T3 building and field experimentals
        fieldExperimentals(level, buildings, workers, minutes);

        // 6-8) the army: defend, build up at the rally point, raid, scout, attack in waves, retreat (BotArmy)
        this.army.run(level, gameTime, mine, army, buildings, minutes);
        // 9) a bot with no army for two minutes says why, once a minute, so the next playtest explains itself
        // (lovish's Oct 13 2v2: the ally hard bot sat at army value 0 for 28 minutes and nothing in the log said so)
        logNoArmy(gameTime, mine.size(), buildings, capitol);
    }

    /** Game time since which the army value has been 0 (-1 = it is not), and the last army log line. */
    long armyZeroSince = -1, lastArmyLogAt = -1;
    static final long NO_ARMY_AFTER_TICKS = 20 * 120, ARMY_LOG_TICKS = 20 * 60;

    /** "[Bot] <name> army: value V, units N, lost L, state S, <why no wave>" every minute after two at value 0. */
    void logNoArmy(long gameTime, int units, List<BuildingPlacement> buildings, BuildingPlacement capitol) {
        if (army.armyValue > 0) {
            armyZeroSince = -1;
            return;
        }
        if (armyZeroSince < 0)
            armyZeroSince = gameTime;
        if (gameTime - armyZeroSince < NO_ARMY_AFTER_TICKS || (lastArmyLogAt >= 0 && gameTime - lastArmyLogAt < ARMY_LOG_TICKS))
            return;
        lastArmyLogAt = gameTime;
        // the first thing that stops fighters from existing: no lab, a lab still unbuilt, a built lab with nothing
        // queued (unaffordable), else the army step's own reason (fighters dying as they come, no target...)
        int labs = 0, built = 0, queued = 0;
        for (BuildingPlacement bp : buildings)
            if (isLab(bp)) {
                labs++;
                if (bp.isBuilt) {
                    built++;
                    if (bp instanceof ProductionPlacement pp)
                        queued += pp.productionQueue.size();
                }
            }
        String why;
        if (capitol == null || !capitol.isBuilt)
            why = "no capitol" + (capitol == null ? "" : " yet (unbuilt)") + (lastBlockedReason == null ? "" : "; last blocked: " + lastBlockedReason);
        else if (labs == 0)
            why = "no army lab placed (slot shift " + labSlotShift + ", sites cancelled " + siteWatch.cancelled + ")"
                + (lastBlockedReason == null ? "" : "; last blocked: " + lastBlockedReason);
        else if (built == 0)
            why = labs + " lab(s) placed, none built" + (lastBlockedReason == null ? "" : "; last blocked: " + lastBlockedReason);
        else if (queued == 0)
            why = built + " lab(s) built with an empty queue (metal " + String.format("%.2f", metalFill) + ", energy "
                + String.format("%.2f", energyFill) + " of storage)";
        else
            why = built + " lab(s) producing; " + (army.noWaveReason.isEmpty() ? "fighters die as they come" : army.noWaveReason);
        com.solegendary.reignofnether.ReignOfNether.LOGGER.info("[Bot] {} army: value {}, units {}, lost {}, state {}, {} s at 0: {}",
            name, (int) army.armyValue, units, com.solegendary.reignofnether.hud.KillFeedServerEvents.losses(name), army.state,
            (gameTime - armyZeroSince) / 20, why);
    }

    /** Metal income per second under which a bot at the storage cap builds labs, not vaults. */
    static final float VAULT_MIN_INCOME = 10f;

    /** This bot's resource pool, or null before it has one. */
    com.solegendary.reignofnether.resources.Resources pool() {
        for (var r : com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList)
            if (r.ownerName.equals(name))
                return r;
        return null;
    }

    /**
     * The next unit for a lab: the role table's siege unit while enemy defences have been seen and fewer than one
     * fighter in five is siege, else a random pick from the kit's (already mixed) list. {@code t2} says which lab
     * is asking, since each faction's siege unit lives in one of the two.
     */
    ProductionItem pickArmyItem(List<ProductionItem> list, List<LivingEntity> mine, boolean t2) {
        ProductionItem siege = BotRoles.siegeFor(faction);
        if (army.defencesSeen && siege != null && BotRoles.siegeIsT2(faction) == t2) {
            int fighters = 0, sieges = 0;
            for (LivingEntity le : mine)
                if (le instanceof AttackerUnit && !(le instanceof WorkerUnit)) {
                    fighters++;
                    if (BotRoles.isSiege(le))
                        sieges++;
                }
            if (sieges * 5 < fighters)
                return siege;
        }
        return list.get(rng.nextInt(list.size()));
    }

    // ------------------------------------------------------------------ helpers

    int targetT2Workers() { return difficulty == Difficulty.HARD ? 2 : 1; }

    /** Queues one T2 constructor at the T2 lab unless one is already in its queue. */
    void queueT2Worker(ProductionPlacement lab) {
        for (var item : lab.productionQueue)
            if (item.item == kit.t2Worker())
                return;
        lab.startProductionItem(kit.t2Worker());
    }

    /** The bot is ready for Tier 2 and waits only for the capitol's queue to empty (set each think by the tech step). */
    boolean techPending = false;

    void trainWorkers(BuildingPlacement capitol, int workerCount) {
        if (techPending || workerCount >= targetWorkers() || !(capitol instanceof ProductionPlacement pp) || !capitol.isBuilt)
            return;
        // (two at a time while metal floats: more constructors is the fastest way to turn a stockpile into buildings)
        if (pp.productionQueue.size() < (floating() ? 2 : 1))
            pp.startProductionItem(kit.worker());
    }

    /**
     * Unbuilt buildings each get the nearest two workers that aren't already building (three for the capitol);
     * then a built building under 80% health gets one idle worker to repair it (after a fight, the way a BAR
     * player's idle constructors are put on repair), one such errand per think.
     */
    void assignBuilders(ServerLevel level, List<BuildingPlacement> buildings, List<LivingEntity> workers) {
        List<LivingEntity> free = new ArrayList<>();
        for (LivingEntity w : workers)
            if (w instanceof WorkerUnit wu && (wu.getBuildRepairGoal() == null || wu.getBuildRepairGoal().getBuildingTarget() == null))
                free.add(w);
        for (BuildingPlacement bp : buildings) {
            if (bp.isBuilt) {
                if (!free.isEmpty() && bp.getHealth() < bp.getMaxHealth() * 0.8f && bp.getBuilding().repairable) {
                    for (int i = 0; i < free.size(); i++)
                        if (free.get(i) instanceof Unit u && u.isIdle()
                                && !com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(free.get(i))) {
                            UnitServerEvents.addActionItem(name, UnitAction.BUILD_REPAIR, -1, new int[] { free.remove(i).getId() },
                                bp.originPos, bp.originPos);
                            break;
                        }
                }
                continue;
            }
            if (free.isEmpty())
                continue;
            free.sort((a, b) -> Double.compare(a.blockPosition().distSqr(bp.originPos), b.blockPosition().distSqr(bp.originPos)));
            int take = Math.min(bp.getBuilding().isCapitol ? 3 : 2, free.size());
            int[] ids = new int[take];
            for (int i = 0; i < take; i++)
                ids[i] = free.remove(0).getId();
            UnitServerEvents.addActionItem(name, UnitAction.BUILD_REPAIR, -1, ids, bp.originPos, bp.originPos);
        }
    }

    /** Reused result list for UnitGrid queries (the think step runs on the server thread, one bot at a time). */
    final List<LivingEntity> scan = new ArrayList<>();

    /** Patches whose extractor placement failed, with the game time of the failure: skipped for two minutes. */
    final java.util.Map<BlockPos, Long> badPatches = new java.util.HashMap<>();
    /**
     * How often each patch failed in all. CI run 38474446599: the kingdom bot failed the same patch 19 times in four
     * minutes ("no valid footprint over the patch at 1689, 59, -312" - a patch stamped where no 5x5 footprint fits);
     * a patch that fails this often is given up for the match rather than tried every two minutes forever.
     */
    final java.util.Map<BlockPos, Integer> patchFailures = new java.util.HashMap<>();
    static final int PATCH_GIVE_UP = 3;

    /**
     * An extractor over a patch: the 5x5 footprint may sit anywhere that still covers the patch block, and the
     * ground may be a block off the recorded centre (terrain edits, a stamp next to a river), so try those first.
     */
    boolean placeExtractor(ServerLevel level, BlockPos patch, List<LivingEntity> workers) {
        // (counted like placeNear: a soak run where every extractor failed used to read "placeFailures 0")
        boolean ok = placeExtractorImpl(level, patch, workers);
        if (ok) {
            placed(level.getGameTime());
            patchFailures.remove(patch);
        } else {
            int n = workers.isEmpty() ? 0 : patchFailures.merge(patch, 1, Integer::sum);   // no worker = not the patch's fault
            placeFailed(level.getGameTime(), kit.extractor(), "no valid footprint over the patch at " + patch.toShortString()
                + (workers.isEmpty() ? " (no workers)" : "") + (n >= PATCH_GIVE_UP ? " - given up" : ""));
        }
        return ok;
    }

    /** The origins placeExtractorImpl tries over a patch centre, in order (public: the footprint game test walks the same list). */
    public static final int[] EXTRACTOR_DY = { 0, -1, 1 };
    public static final int[][] EXTRACTOR_OFFSETS = { {-2, -2}, {-1, -2}, {-2, -1}, {-3, -2}, {-2, -3}, {-1, -1}, {-3, -3}, {-1, -3}, {-3, -1} };

    boolean placeExtractorImpl(ServerLevel level, BlockPos patch, List<LivingEntity> workers) {
        if (workers.isEmpty())
            return false;
        level.getChunk(patch.getX() >> 4, patch.getZ() >> 4);
        for (int dy : EXTRACTOR_DY)
            for (int[] o : EXTRACTOR_OFFSETS) {
                BlockPos origin = new BlockPos(patch.getX() + o[0], patch.getY() + dy, patch.getZ() + o[1]);
                if (!BuildingValidators.isPlacementValid(level, kit.extractor(), origin, name, Rotation.NONE, false, false, true))
                    continue;
                workers.sort((u, v) -> Double.compare(u.blockPosition().distSqr(origin), v.blockPosition().distSqr(origin)));
                return BuildingServerEvents.placeBuilding(kit.extractor(), origin, Rotation.NONE, name,
                        new int[] { workers.get(0).getId() }, false, false, false, true) != null;
            }
        return false;
    }

    /**
     * The best stamped metal patch with no building on it yet: nearest by distance over the patch's value
     * (design/maps.md: 1.0 / 1.5 / 2.0x), so a rich patch beats a plain one at the same distance and a 2.0x patch is
     * worth walking twice as far for - BARbarian's expansion rule. Patches next to enemy buildings are skipped (don't
     * send workers to die), and a patch whose placement failed waits two minutes.
     */
    public BlockPos freePatch(ServerLevel level) {
        long now = level.getGameTime();
        return bestPatch(home, MetalPatches.getPatches(level), MetalPatches::valueOf, p -> {
            if (patchFailures.getOrDefault(p, 0) >= PATCH_GIVE_UP)
                return false;   // no footprint ever fits there: not this match
            Long failed = badPatches.get(p);
            if (failed != null && now - failed < 20 * 120)
                return false;
            for (BuildingPlacement bp : BuildingServerEvents.getBuildings())
                if (!bp.isDestroyedServerside && bp.originPos.distSqr(p) < 10 * 10)
                    return false;
            for (BuildingPlacement bp : com.solegendary.reignofnether.building.Blueprints.pendingOf(name))
                if (bp.originPos.distSqr(p) < 10 * 10)
                    return false;   // an extractor already ordered there
            BlockPos enemy = nearestEnemyBuildingAny(p);
            return enemy == null || enemy.distSqr(p) >= 45 * 45 || enemy.distSqr(p) >= p.distSqr(home);
        });
    }

    /** The richest a patch gets (MapLayout.CENTRE): the bound that lets {@link #bestPatch} skip most patches cheaply. */
    static final double MAX_PATCH_VALUE = 2.0;

    /**
     * The patch with the lowest distance / value from {@code home} that {@code usable} accepts. The usable test is the
     * expensive part (building scans), so a patch that could not beat the best one even at the top value is skipped
     * before it runs. Pure, for the game test.
     */
    public static BlockPos bestPatch(BlockPos home, Iterable<BlockPos> patches, java.util.function.ToDoubleFunction<BlockPos> value,
                                     java.util.function.Predicate<BlockPos> usable) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos p : patches) {
            double dist = Math.sqrt(p.distSqr(home));
            if (dist / MAX_PATCH_VALUE >= bestScore)
                continue;
            double score = dist / Math.max(0.1, value.applyAsDouble(p));
            if (score >= bestScore || !usable.test(p))
                continue;
            bestScore = score;
            best = p;
        }
        return best;
    }

    /**
     * Only the in-fight ability casting of think() - faction powers and Kraken grabs - with no economy, building or
     * army orders. The duel simulator (gametest/DuelGameTests) drives each side through this so its units use their
     * activated abilities the way a bot army would; works without a kit (faction may be null).
     */
    public void castFightAbilities(ServerLevel level, List<LivingEntity> mine) {
        useFactionPowers(level, mine);
        useTitanAbilities(level, mine);
        tideKrakens(level, mine);
        tideLeviathans(level, mine);
        stormTempest(level, mine, null);
        stormDomes(level, mine, null);
        BotGameEnders.castBeastAbilities(this, level, mine);   // big-units: Breach Charge, Hollow Grove
    }

    /** Enemy units a T3's area ability must catch to be worth its 40-60 s cooldown. */
    static final int TITAN_ABILITY_MIN_FOES = 3;

    /**
     * T3 experimentals' own abilities (the Leviathan's Tidal Slam is {@link #tideLeviathans}; the Bone Dragon's
     * Withering Fog is a faction power): Solar Lance aimed through the nearest enemy, Trample charged through it, and
     * Rootquake where the walker stands - each only once {@link #TITAN_ABILITY_MIN_FOES} enemy units would be caught
     * (lane or ring counted as the ability will), so the telegraph still lets them dodge and nothing is spent on a
     * straggler. One grid query per experimental whose ability is off cooldown, nothing otherwise.
     */
    void useTitanAbilities(ServerLevel level, List<LivingEntity> mine) {
        for (LivingEntity le : mine) {
            if (!(le instanceof com.solegendary.reignofnether.unit.units.villagers.SunColossusUnit
                    || le instanceof com.solegendary.reignofnether.unit.units.piglins.WarMammothUnit
                    || le instanceof com.solegendary.reignofnether.unit.units.verdant.WorldTreeWalkerUnit)
                    || !(le instanceof Unit u) || u.getAbilities() == null || !le.isAlive())
                continue;
            for (com.solegendary.reignofnether.ability.Ability a : u.getAbilities().get()) {
                boolean lance = a instanceof com.solegendary.reignofnether.ability.abilities.SolarLance;
                boolean trample = a instanceof com.solegendary.reignofnether.ability.abilities.Trample;
                boolean quake = a instanceof com.solegendary.reignofnether.ability.abilities.Rootquake;
                if (!(lance || trample || quake) || !a.isOffCooldown(u))
                    continue;
                double reach = lance ? com.solegendary.reignofnether.ability.abilities.SolarLance.LENGTH
                        : trample ? com.solegendary.reignofnether.ability.abilities.Trample.LENGTH
                        : com.solegendary.reignofnether.ability.abilities.Rootquake.RADIUS;
                LivingEntity nearest = null;
                double best = Double.MAX_VALUE;
                int inRing = 0;
                for (LivingEntity other : com.solegendary.reignofnether.unit.UnitGrid.near(level, le.getX(), le.getZ(), reach, scan)) {
                    if (!isEnemyUnit(other))
                        continue;
                    double d = other.distanceToSqr(le);
                    if (d > reach * reach)
                        continue;
                    inRing++;
                    if (d < best) {
                        best = d;
                        nearest = other;
                    }
                }
                if (nearest == null)
                    continue;
                if (quake) {
                    if (inRing >= TITAN_ABILITY_MIN_FOES)
                        a.use(level, u, le.blockPosition());
                    continue;
                }
                double dx = nearest.getX() - le.getX(), dz = nearest.getZ() - le.getZ();
                double len = Math.sqrt(dx * dx + dz * dz);
                if (len < 0.01)
                    continue;
                net.minecraft.world.phys.Vec3 dir = new net.minecraft.world.phys.Vec3(dx / len, 0, dz / len);
                int caught;
                if (lance) {
                    net.minecraft.world.phys.Vec3 from = le.position().add(0, 1.5, 0);
                    caught = com.solegendary.reignofnether.ability.abilities.CommanderDGun.hits(le, name, from,
                        from.add(dir.scale(reach)), com.solegendary.reignofnether.ability.abilities.SolarLance.HALF_WIDTH).size();
                } else {
                    // Trample's lane is Trample.use's: ahead of the mammoth's facing, LENGTH long, HALF_WIDTH each side
                    caught = 0;
                    for (LivingEntity other : scan) {
                        if (!isEnemyUnit(other))
                            continue;
                        double rx = other.getX() - le.getX(), rz = other.getZ() - le.getZ();
                        double along = rx * dir.x + rz * dir.z, across = -rx * dir.z + rz * dir.x;
                        if (along >= 0 && along <= reach && Math.abs(across) <= com.solegendary.reignofnether.ability.abilities.Trample.HALF_WIDTH
                                && Math.abs(other.getY() - le.getY()) <= 4)
                            caught++;
                    }
                }
                if (caught < TITAN_ABILITY_MIN_FOES)
                    continue;
                if (trample) {
                    // Trample is an aimed charge: at the unit, so the mammoth runs the whole lane through the group
                    // (Trample.use(entity)), not just to the nearest body
                    a.use(level, u, nearest);
                    continue;
                }
                a.use(level, u, nearest.blockPosition());
            }
        }
    }

    /** Fires the commander's ability once three or more enemy units are within its reach. */
    void useCommanderAbility(ServerLevel level, List<LivingEntity> mine) {
        for (LivingEntity le : mine) {
            if (!com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(le) || !(le instanceof Unit u) || sealed(le))
                continue;
            for (com.solegendary.reignofnether.ability.Ability a : u.getAbilities().get()) {
                if (!(a instanceof com.solegendary.reignofnether.ability.abilities.CommanderAbility) || !a.isOffCooldown(u))
                    continue;
                // Blink is an escape, not an aura: stormHabits uses it when the Thunder Sage is in trouble
                if (com.solegendary.reignofnether.ability.abilities.CommanderAbility.kindFor(u)
                        == com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.BLINK)
                    continue;
                // Riptide is a short cone, not an aura: count foes within its reach and aim it at the nearest one
                boolean riptide = com.solegendary.reignofnether.ability.abilities.CommanderAbility.kindFor(u)
                        == com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.RIPTIDE;
                double reach = riptide ? com.solegendary.reignofnether.ability.abilities.CommanderAbility.RIPTIDE_RANGE : 14;
                int enemies = 0;
                LivingEntity nearest = null;
                double best = Double.MAX_VALUE;
                for (LivingEntity other : com.solegendary.reignofnether.unit.UnitGrid.near(level, le.getX(), le.getZ(), reach, scan)) {
                    if (!(other instanceof Unit ou) || !other.isAlive())
                        continue;
                    double d = other.distanceToSqr(le);
                    if (d > reach * reach)
                        continue;
                    String o = ou.getOwnerName();
                    if (o != null && !o.equals(name) && !AlliancesServerEvents.isAllied(name, o)) {
                        enemies++;
                        if (d < best) {
                            best = d;
                            nearest = other;
                        }
                    }
                }
                if (enemies >= (riptide ? 2 : 3))
                    a.use(level, u, riptide ? nearest.blockPosition() : le.blockPosition());
            }
            useOvergrowth(level, le, u);
            fireDGun(level, le, u);
            for (com.solegendary.reignofnether.ability.Ability a : u.getAbilities().get())
                if (a instanceof com.solegendary.reignofnether.ability.abilities.RaiseDead && a.isOffCooldown(u))
                    a.use(level, u, le.blockPosition());   // does nothing (and keeps its cooldown) without wrecks in reach
        }
    }

    /**
     * Faction powers (Crypt Tide, War-Drums, Bastion Aegis, Magma Rupture, Holy Bell, Withering
     * Fog) on any unit that carries one: same rule as the commander's signature - only once three or more enemy
     * units are within 12 blocks. The ground-targeted ones (Crypt Tide, Magma Rupture, Withering Fog) are aimed at
     * the nearest of them; the rest are cast where the unit stands. The enemy scan only runs for a unit with a
     * power off cooldown, so an idle army costs nothing here. Sunrise Sortie has its own trigger
     * ({@link #useSortie}).
     */
    void useFactionPowers(ServerLevel level, List<LivingEntity> mine) {
        for (LivingEntity le : mine) {
            if (!(le instanceof Unit u) || u.getAbilities() == null || sealed(le))
                continue;
            for (com.solegendary.reignofnether.ability.Ability a : u.getAbilities().get()) {
                if (a instanceof com.solegendary.reignofnether.ability.abilities.SunriseSortie) {
                    if (a.isOffCooldown(u))
                        useSortie(level, le, u, a);
                    continue;
                }
                if (a instanceof com.solegendary.reignofnether.ability.abilities.LeafDash) {
                    if (a.isOffCooldown(u))
                        useLeafDash(level, le, u, a);
                    continue;
                }
                boolean aimed = a instanceof com.solegendary.reignofnether.ability.abilities.CryptTide
                        || a instanceof com.solegendary.reignofnether.ability.abilities.MagmaRupture
                        || a instanceof com.solegendary.reignofnether.ability.abilities.WitheringFog
                        || a instanceof com.solegendary.reignofnether.ability.abilities.StagLeap;
                if (!aimed && !(a instanceof com.solegendary.reignofnether.ability.abilities.WarDrums)
                        && !(a instanceof com.solegendary.reignofnether.ability.abilities.BastionAegis)
                        && !(a instanceof com.solegendary.reignofnether.ability.abilities.HolyBell)
                        && !(a instanceof com.solegendary.reignofnether.ability.abilities.Bloom))
                    continue;
                if (!a.isOffCooldown(u))
                    continue;
                boolean flyer = a instanceof com.solegendary.reignofnether.ability.abilities.WitheringFog;
                int enemies = 0;
                LivingEntity nearest = null;
                double best = Double.MAX_VALUE;
                for (LivingEntity other : com.solegendary.reignofnether.unit.UnitGrid.near(level, le.getX(), le.getZ(), 12, scan)) {
                    if (!(other instanceof Unit ou) || !other.isAlive())
                        continue;
                    // the Bone Dragon flies high over the fight: measure its scan flat, or it never sees one
                    double d = flyer ? Math.pow(other.getX() - le.getX(), 2) + Math.pow(other.getZ() - le.getZ(), 2)
                            : other.distanceToSqr(le);
                    if (d > 12 * 12)
                        continue;
                    String o = ou.getOwnerName();
                    if (o == null || o.equals(name) || AlliancesServerEvents.isAllied(name, o))
                        continue;
                    enemies++;
                    if (d < best) {
                        best = d;
                        nearest = other;
                    }
                }
                if (enemies >= 3)
                    a.use(level, u, aimed ? nearest.blockPosition() : le.blockPosition());
            }
        }
    }

    /**
     * Sunrise Sortie: charge at the nearest enemy within 14 blocks, but only when three or more enemies stand in
     * front along that line (a forward cone toward it) - a charge into one straggler wastes a 50 s cooldown.
     */
    void useSortie(ServerLevel level, LivingEntity le, Unit u, com.solegendary.reignofnether.ability.Ability a) {
        double r = com.solegendary.reignofnether.ability.abilities.SunriseSortie.LENGTH + 2;
        LivingEntity nearest = null;
        double best = r * r;
        for (LivingEntity other : com.solegendary.reignofnether.unit.UnitGrid.near(level, le.getX(), le.getZ(), r, scan)) {
            if (!isEnemyUnit(other))
                continue;
            double d = other.distanceToSqr(le);
            if (d < best) {
                best = d;
                nearest = other;
            }
        }
        if (nearest == null)
            return;
        double fx = nearest.getX() - le.getX(), fz = nearest.getZ() - le.getZ();
        double fl = Math.sqrt(fx * fx + fz * fz);
        if (fl < 0.5)
            return;
        fx /= fl;
        fz /= fl;
        int ahead = 0;
        for (LivingEntity other : scan) {   // still holds the grid query above
            if (!isEnemyUnit(other) || other.distanceToSqr(le) > r * r)
                continue;
            double ox = other.getX() - le.getX(), oz = other.getZ() - le.getZ();
            double ol = Math.sqrt(ox * ox + oz * oz);
            if (ol < 0.5 || (ox * fx + oz * fz) / ol >= 0.7)
                ahead++;
        }
        if (ahead >= 3)
            a.use(level, u, nearest.blockPosition());
    }

    /**
     * Leaf Dash: close on the nearest enemy ranged unit (archers, casters - what a Leafblade is for) within the
     * dash's reach, but only one at least 3 blocks off: next to it, the blade just swings.
     */
    void useLeafDash(ServerLevel level, LivingEntity le, Unit u, com.solegendary.reignofnether.ability.Ability a) {
        double r = com.solegendary.reignofnether.ability.abilities.LeafDash.LENGTH + 1;
        LivingEntity best = null;
        double bestD = r * r;
        for (LivingEntity other : com.solegendary.reignofnether.unit.UnitGrid.near(level, le.getX(), le.getZ(), r, scan)) {
            if (!(other instanceof com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit) || !isEnemyUnit(other))
                continue;
            double d = other.distanceToSqr(le);
            if (d < 9 || d >= bestD || Math.abs(other.getY() - le.getY()) > 3)
                continue;
            bestD = d;
            best = other;
        }
        if (best != null)
            a.use(level, u, best);
    }

    // ------------------------------------------------------------------ Verdant Court habits (slice 2)

    /** The narrowest point on the way from home toward the enemy, and its "across" direction; recomputed every 5 min. */
    BlockPos choke = null;
    double chokeAcrossX = 1, chokeAcrossZ = 0;
    long chokeAt = -1;

    /**
     * For any bot whose units carry them (the Verdant Court today): one Owl Watcher hovers over that
     * choke as a picket, and Moonwell Bearers - not fighters, so they never get army orders - follow the army's
     * centre. Cheap: one centroid pass over the army and a few checks a think; the choke scan is a few dozen
     * heightmap reads every 5 minutes.
     */
    void verdantHabits(ServerLevel level, List<LivingEntity> mine, List<LivingEntity> army, List<LivingEntity> workers,
                       List<BuildingPlacement> buildings, long gameTime, long minutes) {
        // healers follow the army
        if (!army.isEmpty()) {
            double cx = 0, cz = 0;
            for (LivingEntity le : army) { cx += le.getX(); cz += le.getZ(); }
            BlockPos centre = null;
            List<LivingEntity> lagging = new ArrayList<>();
            for (LivingEntity le : mine) {
                // Moonwell Bearers and Bloom Priestesses: healers, not fighters (neither is an AttackerUnit, so the
                // army list never holds them and they would otherwise idle at the lab)
                if (!(le instanceof com.solegendary.reignofnether.unit.units.verdant.MoonwellBearerUnit)
                        && !(le instanceof com.solegendary.reignofnether.unit.units.verdant.BloomPriestessUnit)
                        && !(le instanceof com.solegendary.reignofnether.unit.units.tide.TidePriestUnit)
                        && !(le instanceof com.solegendary.reignofnether.unit.units.storm.InkScribeUnit)    // the Scribe seals from the line
                        && !(le instanceof com.solegendary.reignofnether.unit.units.storm.SeerUnit)         // the Seer watches from it
                        && !(le instanceof com.solegendary.reignofnether.unit.units.storm.LanternFortressUnit))   // the Fortress shields it
                    continue;
                Unit mb = (Unit) le;
                if (centre == null)
                    centre = ground(level, (int) Math.floor(cx / army.size()), (int) Math.floor(cz / army.size()));
                BlockPos mt = mb.getMoveGoal() == null ? null : mb.getMoveGoal().getMoveTarget();
                if (le.distanceToSqr(centre.getX() + 0.5, le.getY(), centre.getZ() + 0.5) > 10 * 10
                        && (mt == null || mt.distSqr(centre) > 8 * 8))
                    lagging.add(le);
            }
            if (centre != null)
                order(lagging, UnitAction.MOVE, centre);
        }
        if (minutes < 2 || home == null)
            return;
        if (choke == null || gameTime - chokeAt > 20 * 60 * 5) {
            chokeAt = gameTime;
            findChoke(level);
        }
        if (choke == null)
            return;
        // one Owl Watcher, parked over the choke
        if (faction.equals(Factions.VERDANT_COURT)) {
            LivingEntity owl = null;
            for (LivingEntity le : mine)
                if (le instanceof com.solegendary.reignofnether.unit.units.verdant.OwlWatcherUnit)
                    owl = le;
            if (owl == null) {
                for (BuildingPlacement bp : buildings)
                    if (bp.isBuilt && bp.getBuilding() == kit.armyBuilding() && bp instanceof ProductionPlacement pp) {
                        boolean queued = false;
                        for (var item : pp.productionQueue)
                            if (item.item == ProductionItems.OWL_WATCHER)
                                queued = true;
                        if (!queued)
                            pp.startProductionItem(ProductionItems.OWL_WATCHER);
                        break;
                    }
            } else if (owl instanceof Unit ou && ou.isIdle()
                    && owl.distanceToSqr(choke.getX() + 0.5, owl.getY(), choke.getZ() + 0.5) > 4 * 4) {
                order(List.of(owl), UnitAction.MOVE, choke.above(3));
            }
        }
    }

    // ------------------------------------------------------------------ Stormcallers habits (slice 1)

    /** Energy the Thunder Sage keeps after a Judgement (hard bots spend deeper), and the Overcharge thresholds. */
    static final float JUDGEMENT_RESERVE_HARD = 150f, JUDGEMENT_RESERVE = 300f;
    static final float OVERCHARGE_ON_FILL = 0.6f, OVERCHARGE_OFF_FILL = 0.2f;
    /** Energy kept before the bot's Ward (8 s of drain plus a Judgement), Seal and Frost Field (the Judgement reserve). */
    static final float WARD_RESERVE = 250f, SEAL_RESERVE = 175f, FROST_RESERVE = 200f;
    /** Energy kept before the T2 spells (slice 3): each leaves a Judgement's worth for the combo that follows. */
    static final float SINGULARITY_RESERVE = 200f, MIRROR_RESERVE = 200f, SLAM_RESERVE = 200f, WELL_RESERVE = 300f;
    /**
     * Slice 4: a Tempest keeps enough for its first seconds of drain and a Judgement after it; a Lantern Dome is raised
     * (toggled on) only with this much in the pool and dropped under {@link #DOME_OFF_ENERGY}, so the dome never starves
     * the Sage's spells. Foes that make a Tempest or a dome worth their drain.
     */
    static final float TEMPEST_RESERVE = 300f, DOME_ON_ENERGY = 200f, DOME_OFF_ENERGY = 60f;
    static final int TEMPEST_MIN_FOES = 3, DOME_MIN_FOES = 2;
    /** Where the bot's last Singularity closes and when: the Judgement and a Frost Field follow it for {@link #COMBO_TICKS}. */
    BlockPos singularityAt = null;
    long singularityTick = -1;
    static final int COMBO_TICKS = 80;

    /**
     * For a Mana bot (the Stormcallers; ManaServerEvents.isMana, never a faction == check in the hot path):
     * <ul>
     *   <li>the Thunder Sage fires Heaven's Judgement at the enemy standing in the densest clump within reach (two or
     *       more foes in one column), paid quietly through the Mana core and only with an energy reserve left over;</li>
     *   <li>it Blinks toward home when it is under 30% health and was hit in the last 3 s;</li>
     *   <li>Overcharge goes on while the army is in a fight (3+ enemies and 4+ of ours within 16 blocks of the army's
     *       centre) and energy is above 60% of storage, and off once the fight is over or energy drops under 20%
     *       (ManaServerEvents also switches it off by itself at 0).</li>
     *   <li>slice 2: it raises its Ward when it is fighting under fire (hit in the last 3 s with 2+ foes in reach); Ink
     *       Scribes seal the priciest enemy caster or commander in reach ({@link #stormSeals}); Frost Adepts freeze the
     *       ground between the army and an enemy group pushing it ({@link #stormFrost}).</li>
     * </ul>
     * Cheap: one centroid pass and one grid query for the army, and a small O(n^2) clump count (n = foes within the
     * Judgement's 14 blocks) only while the D-gun is off cooldown and affordable.
     */
    void stormHabits(ServerLevel level, List<LivingEntity> mine, List<LivingEntity> army, long gameTime) {
        if (!com.solegendary.reignofnether.mana.ManaServerEvents.isMana(name))
            return;
        com.solegendary.reignofnether.resources.Resources res = null;
        for (var r : com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList)
            if (r.ownerName.equals(name))
                res = r;
        if (res == null)
            return;
        float storage = Math.max(1f, com.solegendary.reignofnether.resources.EconomyServerEvents.getEconomy(name).energyStorage);
        // T2 (slice 3) first: a Singularity this think is what the Judgement and the Frost Field below aim at
        stormSingularity(level, mine, res, gameTime);
        for (LivingEntity le : mine)
            if (com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(le) && le instanceof Unit u && !sealed(le))
                stormCommander(level, le, u, res);
        stormSeals(level, mine, res);
        stormFrost(level, mine, army, res);
        stormMirror(level, mine, res);
        stormSlam(level, mine, res);
        stormTempest(level, mine, res);
        stormDomes(level, mine, res);
        // Overcharge: on in a fight with a full pool, off when the fight ends or the pool runs low
        boolean fight = false;
        if (army.size() >= 4) {
            double cx = 0, cz = 0;
            for (LivingEntity f : army) { cx += f.getX(); cz += f.getZ(); }
            cx /= army.size();
            cz /= army.size();
            int foes = 0, ours = 0;
            for (LivingEntity other : com.solegendary.reignofnether.unit.UnitGrid.near(level, cx, cz, 16, scan)) {
                double dx = other.getX() - cx, dz = other.getZ() - cz;
                if (dx * dx + dz * dz > 16 * 16)
                    continue;
                if (isEnemyUnit(other))
                    foes++;
                else if (other instanceof Unit ou && name.equals(ou.getOwnerName()) && other instanceof AttackerUnit && !(other instanceof WorkerUnit))
                    ours++;
            }
            fight = foes >= 3 && ours >= 4;
        }
        boolean on = com.solegendary.reignofnether.mana.ManaServerEvents.isOvercharged(name);
        if (!on && fight && res.getEnergy() > storage * OVERCHARGE_ON_FILL)
            com.solegendary.reignofnether.mana.ManaServerEvents.setOvercharge(name, true);
        else if (on && (!fight || res.getEnergy() < storage * OVERCHARGE_OFF_FILL))
            com.solegendary.reignofnether.mana.ManaServerEvents.setOvercharge(name, false);
    }

    /**
     * Brassworks habits (design/brassworks_plan.md section 1), gated by the faction so nobody else pays the instanceof
     * scans: the Guildmaster <b>Overclocks</b> when 3+ own machines stand within its reach and 2+ foes within 14 of it,
     * and <b>deploys a turret</b> at its feet when it was hit in the last 3 s with 2+ foes within 10 (40 metal, keeping
     * a 150 reserve); an idle Tinker at home <b>plates</b> the nearest bare Iron Warden within 6 (30 metal, 200 reserve).
     * One pass over the bot's units per think; no allocation but the shared scratch list.
     */
    /** A machine at or under this heat counts toward the bot's Overclock (hotter ones would only vent sooner). */
    static final int OVERCLOCK_COOL_HEAT = 40;

    void brassHabits(ServerLevel level, List<LivingEntity> mine, long gameTime) {
        if (!faction.equals(Factions.BRASSWORKS))
            return;
        com.solegendary.reignofnether.resources.Resources res = null;
        for (var r : com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList)
            if (r.ownerName.equals(name))
                res = r;
        if (res == null)
            return;
        for (LivingEntity le : mine) {
            if (!le.isAlive() || !(le instanceof Unit u))
                continue;
            if (com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(le)) {
                int foesNear = 0, foesClose = 0, machines = 0;
                for (LivingEntity other : com.solegendary.reignofnether.unit.UnitGrid.near(level, le.getX(), le.getZ(), 14, scan)) {
                    if (!(other instanceof Unit ou) || !other.isAlive())
                        continue;
                    String o = ou.getOwnerName();
                    if (o == null || o.isEmpty())
                        continue;
                    if (o.equals(name)) {
                        // Overclock timing (slice 4): only cool machines count - overclocking a hot one (or the T2
                        // heavies mid-brawl) just brings its vent forward, so the bot waits for a fresh engagement
                        if (other instanceof com.solegendary.reignofnether.brass.Machine m && m.heatPerShot() > 0
                                && m.heat() <= OVERCLOCK_COOL_HEAT && !com.solegendary.reignofnether.brass.Heat.isVenting(m)
                                && other.distanceToSqr(le) <= com.solegendary.reignofnether.ability.abilities.CommanderAbility.OVERCLOCK_RADIUS
                                    * com.solegendary.reignofnether.ability.abilities.CommanderAbility.OVERCLOCK_RADIUS)
                            machines++;
                    } else if (!AlliancesServerEvents.isAllied(name, o)) {
                        foesNear++;
                        if (other.distanceToSqr(le) <= 100)
                            foesClose++;
                    }
                }
                scan.clear();
                for (var a : u.getAbilities().get()) {
                    if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderAbility ca && a.isOffCooldown(u)
                            && machines >= 3 && foesNear >= 2) {
                        com.solegendary.reignofnether.ability.abilities.CommanderAbility.overclock(level, le, name);
                        a.setToMaxCooldown(u);
                        com.solegendary.reignofnether.ability.AbilityClientboundPacket.sendSetCooldownPacket(le.getId(), ca.action, ca.cooldownMax);
                    } else if (a instanceof com.solegendary.reignofnether.ability.abilities.DeployTurret dt && a.isOffCooldown(u)
                            && foesClose >= 2 && le.getLastHurtByMob() != null && le.tickCount - le.getLastHurtByMobTimestamp() < 60
                            && res.getMetal() >= com.solegendary.reignofnether.ability.abilities.DeployTurret.METAL_COST + 150) {
                        if (com.solegendary.reignofnether.ability.abilities.DeployTurret.deploy(level, le, name, le.blockPosition(), true) != null) {
                            a.setToMaxCooldown(u);
                            com.solegendary.reignofnether.ability.AbilityClientboundPacket.sendSetCooldownPacket(le.getId(), dt.action, dt.cooldownMax);
                        }
                    }
                }
            } else if (le instanceof com.solegendary.reignofnether.unit.units.brass.DrillBurrowerUnit drill
                    && !difficulty.easyLike() && drill.getBurrowTicks() == 0 && le.tickCount % 20 == 0) {
                // slice 2: a Drill Burrower in a fight tunnels under the front line onto the nearest enemy shooter 6-16
                // blocks off (artillery, archers, casters: what a raider is for)
                com.solegendary.reignofnether.ability.Ability burrow = null;
                for (var a : u.getAbilities().get())
                    if (a instanceof com.solegendary.reignofnether.ability.abilities.Burrow && a.isOffCooldown(u))
                        burrow = a;
                if (burrow == null)
                    continue;
                LivingEntity shooter = null;
                double best = Double.MAX_VALUE;
                int range = com.solegendary.reignofnether.ability.abilities.Burrow.RANGE;
                for (LivingEntity other : com.solegendary.reignofnether.unit.UnitGrid.near(level, le.getX(), le.getZ(), range, scan)) {
                    if (!(other instanceof Unit ou) || !other.isAlive() || !(other instanceof com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit))
                        continue;
                    String o = ou.getOwnerName();
                    if (o == null || o.isEmpty() || o.equals(name) || AlliancesServerEvents.isAllied(name, o)
                            || com.solegendary.reignofnether.unit.Concealment.isHiddenFrom(other, name))
                        continue;
                    double d = other.distanceToSqr(le);
                    if (d >= 36 && d <= range * range && d < best) {
                        best = d;
                        shooter = other;
                    }
                }
                scan.clear();
                if (shooter != null && drill.startBurrow(shooter.position())) {
                    burrow.setToMaxCooldown(u);
                    com.solegendary.reignofnether.ability.AbilityClientboundPacket.sendSetCooldownPacket(le.getId(), burrow.action, burrow.cooldownMax);
                }
            }
        }
    }

    /** The Thunder Sage's Judgement and its escape Blink (see stormHabits). */
    void stormCommander(ServerLevel level, LivingEntity sage, Unit u, com.solegendary.reignofnether.resources.Resources res) {
        for (com.solegendary.reignofnether.ability.Ability a : u.getAbilities().get()) {
            if (!a.isOffCooldown(u))
                continue;
            if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderDGun dgun && !difficulty.easyLike()
                    && com.solegendary.reignofnether.ability.abilities.CommanderDGun.kindFor(u)
                        == com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.JUDGEMENT) {
                float cost = com.solegendary.reignofnether.ability.abilities.CommanderDGun.ENERGY_COST
                    * com.solegendary.reignofnether.mana.ManaServerEvents.spellCostMultiplier(name);
                if (res.getEnergy() < cost + (difficulty == Difficulty.HARD ? JUDGEMENT_RESERVE_HARD : JUDGEMENT_RESERVE))
                    continue;
                // a Singularity that just closed in reach is the best column there is (slice 3 combo); else the densest clump
                BlockPos combo = comboSpot(level, sage, com.solegendary.reignofnether.ability.abilities.CommanderDGun.RANGE);
                LivingEntity target = combo != null ? null : densestFoe(level, sage, com.solegendary.reignofnether.ability.abilities.CommanderDGun.RANGE,
                    com.solegendary.reignofnether.ability.abilities.CommanderDGun.JUDGEMENT_RADIUS);
                BlockPos aim = combo != null ? combo : target != null ? target.blockPosition().below() : null;
                if (aim != null && com.solegendary.reignofnether.ability.abilities.CommanderDGun.judgement(level, sage, name, aim, true)) {
                    dgun.setToMaxCooldown(u);
                    com.solegendary.reignofnether.ability.AbilityClientboundPacket.sendSetCooldownPacket(sage.getId(), dgun.action, dgun.cooldownMax);
                }
            } else if (a instanceof com.solegendary.reignofnether.ability.abilities.Ward ward
                    && sage.getLastHurtByMob() != null && sage.tickCount - sage.getLastHurtByMobTimestamp() < 60
                    && res.getEnergy() >= WARD_RESERVE) {
                // under fire: two or more foes within the Judgement's reach make the fight worth a channelled ward
                int foes = 0;
                for (LivingEntity other : com.solegendary.reignofnether.unit.UnitGrid.near(level, sage.getX(), sage.getZ(), 12, scan))
                    if (isEnemyUnit(other) && other.distanceToSqr(sage) <= 12 * 12)
                        foes++;
                if (foes >= 2 && com.solegendary.reignofnether.ability.abilities.Ward.cast(level, sage, name, true) != null) {
                    ward.setToMaxCooldown(u);
                    com.solegendary.reignofnether.ability.AbilityClientboundPacket.sendSetCooldownPacket(sage.getId(), ward.action, ward.cooldownMax);
                }
            } else if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderAbility blink
                    && com.solegendary.reignofnether.ability.abilities.CommanderAbility.kindFor(u)
                        == com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.BLINK
                    && sage.getHealth() < sage.getMaxHealth() * 0.3f && sage.getLastHurtByMob() != null
                    && sage.tickCount - sage.getLastHurtByMobTimestamp() < 60 && home != null) {
                double dx = home.getX() + 0.5 - sage.getX(), dz = home.getZ() + 0.5 - sage.getZ();
                double len = Math.sqrt(dx * dx + dz * dz);
                if (len < 3)
                    continue;   // already home: nowhere better to blink to
                double reach = Math.min(len, com.solegendary.reignofnether.ability.abilities.CommanderAbility.BLINK_RANGE - 1);
                BlockPos to = ground(level, (int) Math.floor(sage.getX() + dx / len * reach), (int) Math.floor(sage.getZ() + dz / len * reach));
                if (com.solegendary.reignofnether.ability.abilities.CommanderAbility.blink(level, sage, name, to, true)) {
                    int cd = com.solegendary.reignofnether.ability.abilities.CommanderAbility.BLINK_CD_SECONDS * 20;
                    u.setCooldown(blink, cd);
                    com.solegendary.reignofnether.ability.AbilityClientboundPacket.sendSetCooldownPacket(sage.getId(), blink.action, cd);
                }
            }
        }
    }

    /**
     * Ink Scribes (slice 2): a Scribe whose Seal is ready seals the most valuable enemy in reach that has something to
     * seal - a commander first of all, else the priciest unit that carries an ability (metal + energy, the Kraken Caller's
     * rule); plain line units are never worth the energy. Hidden and warded units are skipped. One grid query per Scribe
     * off cooldown, nothing otherwise; keeps {@link #SEAL_RESERVE} energy.
     */
    void stormSeals(ServerLevel level, List<LivingEntity> mine, com.solegendary.reignofnether.resources.Resources res) {
        for (LivingEntity le : mine) {
            if (!(le instanceof com.solegendary.reignofnether.unit.units.storm.InkScribeUnit scribe) || !le.isAlive() || sealed(le))
                continue;
            if (res.getEnergy() < com.solegendary.reignofnether.ability.abilities.Seal.ENERGY_COST
                    * com.solegendary.reignofnether.mana.ManaServerEvents.spellCostMultiplier(name) + SEAL_RESERVE)
                return;
            for (com.solegendary.reignofnether.ability.Ability a : scribe.getAbilities().get()) {
                if (!(a instanceof com.solegendary.reignofnether.ability.abilities.Seal seal) || !seal.isOffCooldown(scribe))
                    continue;
                LivingEntity target = sealTarget(level, scribe);
                if (target != null && com.solegendary.reignofnether.ability.abilities.Seal.cast(level, scribe, name, target, true)) {
                    seal.setToMaxCooldown(scribe);
                    com.solegendary.reignofnether.ability.AbilityClientboundPacket.sendSetCooldownPacket(scribe.getId(), seal.action, seal.cooldownMax);
                }
                break;
            }
        }
    }

    /** The enemy a Scribe should seal: a commander, else the priciest unit with an ability in reach; null if none. Public for the game test. */
    public LivingEntity sealTarget(ServerLevel level, LivingEntity scribe) {
        LivingEntity best = null;
        double bestScore = -1;
        int r = com.solegendary.reignofnether.ability.abilities.Seal.RANGE;
        for (LivingEntity other : com.solegendary.reignofnether.unit.UnitGrid.near(level, scribe.getX(), scribe.getZ(), r, scan)) {
            if (!isEnemyUnit(other) || other.distanceToSqr(scribe) > r * r || sealed(other)
                    || com.solegendary.reignofnether.unit.Concealment.isHiddenFrom(other, name)
                    || !com.solegendary.reignofnether.mana.SealServerEvents.canSeal(name, other))
                continue;
            Unit ou = (Unit) other;
            boolean commander = com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(other);
            if (!commander && (ou.getAbilities() == null || ou.getAbilities().isEmpty()))
                continue;   // nothing to seal
            var cost = ou.getCost();
            double score = cost == null ? 0 : cost.metal() + cost.energy();
            if (commander)
                score += 100000;
            if (score > bestScore) {
                bestScore = score;
                best = other;
            }
        }
        return best;
    }

    /**
     * Frost Adepts (slice 2): when an enemy group (3+ within 16 of the army's centre) is pushing the army, an Adept whose
     * Frost Field is ready freezes the ground between the two - a step in front of the foes, on the line to the army -
     * so the push wades through ice under the Fire Adepts' fire. One field per think; keeps {@link #FROST_RESERVE}.
     */
    void stormFrost(ServerLevel level, List<LivingEntity> mine, List<LivingEntity> army, com.solegendary.reignofnether.resources.Resources res) {
        if (army.size() < 3 || res.getEnergy() < com.solegendary.reignofnether.ability.abilities.FrostField.ENERGY_COST
                * com.solegendary.reignofnether.mana.ManaServerEvents.spellCostMultiplier(name) + FROST_RESERVE)
            return;
        LivingEntity adept = null;
        com.solegendary.reignofnether.ability.abilities.FrostField field = null;
        for (LivingEntity le : mine) {
            if (!(le instanceof com.solegendary.reignofnether.unit.units.storm.FrostAdeptUnit fa) || !le.isAlive() || sealed(le))
                continue;
            for (com.solegendary.reignofnether.ability.Ability a : fa.getAbilities().get())
                if (a instanceof com.solegendary.reignofnether.ability.abilities.FrostField f && f.isOffCooldown(fa)) {
                    adept = le;
                    field = f;
                }
            if (adept != null)
                break;
        }
        if (adept == null)
            return;
        // a Singularity that just closed in reach: freeze the clump where it landed (slice 3 combo)
        BlockPos combo = comboSpot(level, adept, com.solegendary.reignofnether.ability.abilities.FrostField.RANGE);
        if (combo != null) {
            if (com.solegendary.reignofnether.ability.abilities.FrostField.cast(level, adept, name, combo, true) != null) {
                field.setToMaxCooldown((Unit) adept);
                com.solegendary.reignofnether.ability.AbilityClientboundPacket.sendSetCooldownPacket(adept.getId(), field.action, field.cooldownMax);
            }
            return;
        }
        double cx = 0, cz = 0;
        for (LivingEntity f : army) { cx += f.getX(); cz += f.getZ(); }
        cx /= army.size();
        cz /= army.size();
        int foes = 0;
        double fx = 0, fz = 0;
        for (LivingEntity other : com.solegendary.reignofnether.unit.UnitGrid.near(level, cx, cz, 16, scan)) {
            if (!isEnemyUnit(other) || ((Unit) other).isFlyingUnit())
                continue;
            double dx = other.getX() - cx, dz = other.getZ() - cz;
            if (dx * dx + dz * dz > 16 * 16)
                continue;
            foes++;
            fx += other.getX();
            fz += other.getZ();
        }
        if (foes < 3)
            return;
        fx /= foes;
        fz /= foes;
        // a step in front of the enemy group toward our line, clamped to the Adept's reach
        double dx = cx - fx, dz = cz - fz, len = Math.max(1, Math.hypot(dx, dz));
        BlockPos at = ground(level, (int) Math.floor(fx + dx / len * 2), (int) Math.floor(fz + dz / len * 2));
        at = MiscUtil.getHighestNonAirBlock(level, com.solegendary.reignofnether.util.MyMath.getXZRangeLimitedBlockPos(
            adept.blockPosition(), at, com.solegendary.reignofnether.ability.abilities.FrostField.RANGE));
        if (com.solegendary.reignofnether.ability.abilities.FrostField.cast(level, adept, name, at, true) != null) {
            field.setToMaxCooldown((Unit) adept);
            com.solegendary.reignofnether.ability.AbilityClientboundPacket.sendSetCooldownPacket(adept.getId(), field.action, field.cooldownMax);
        }
    }

    // ------------------------------------------------------------------ Stormcallers T2 habits (slice 3)

    /** The spot of a Singularity that closed within {@link #COMBO_TICKS} and lies within {@code range} of {@code from}, else null. */
    BlockPos comboSpot(ServerLevel level, LivingEntity from, double range) {
        if (singularityAt == null || singularityTick < 0 || level.getGameTime() - singularityTick > COMBO_TICKS)
            return null;
        double dx = singularityAt.getX() + 0.5 - from.getX(), dz = singularityAt.getZ() + 0.5 - from.getZ();
        return dx * dx + dz * dz <= range * range ? singularityAt : null;
    }

    /**
     * Gravity Monks: a Monk whose Singularity is ready pulls the densest enemy clump in reach (3+ foes within its radius)
     * together; the spot and the time are kept so the Thunder Sage's Judgement and a Frost Adept's field land on the
     * clump right after (the plan's combo). One cast per think; keeps {@link #SINGULARITY_RESERVE}.
     */
    void stormSingularity(ServerLevel level, List<LivingEntity> mine, com.solegendary.reignofnether.resources.Resources res, long gameTime) {
        if (res.getEnergy() < com.solegendary.reignofnether.ability.abilities.Singularity.ENERGY_COST
                * com.solegendary.reignofnether.mana.ManaServerEvents.spellCostMultiplier(name) + SINGULARITY_RESERVE)
            return;
        for (LivingEntity le : mine) {
            if (!(le instanceof com.solegendary.reignofnether.unit.units.storm.GravityMonkUnit monk) || !le.isAlive() || sealed(le))
                continue;
            for (com.solegendary.reignofnether.ability.Ability a : monk.getAbilities().get()) {
                if (!(a instanceof com.solegendary.reignofnether.ability.abilities.Singularity s) || !s.isOffCooldown(monk))
                    continue;
                LivingEntity target = densestFoe(level, monk, com.solegendary.reignofnether.ability.abilities.Singularity.RANGE,
                    com.solegendary.reignofnether.ability.abilities.Singularity.RADIUS);
                if (target == null || clumpSize(level, target, com.solegendary.reignofnether.ability.abilities.Singularity.RADIUS) < 3)
                    break;
                BlockPos at = target.blockPosition();
                if (com.solegendary.reignofnether.ability.abilities.Singularity.cast(level, monk, name, at, true)) {
                    s.setToMaxCooldown(monk);
                    com.solegendary.reignofnether.ability.AbilityClientboundPacket.sendSetCooldownPacket(monk.getId(), s.action, s.cooldownMax);
                    // the vortex closes after its telegraph: the combo spells aim there once it has
                    singularityAt = at;
                    singularityTick = gameTime + com.solegendary.reignofnether.ability.abilities.Singularity.TELEGRAPH_TICKS;
                    return;
                }
                break;
            }
        }
    }

    /** How many enemy units stand within {@code radius} of {@code at} (itself included). */
    int clumpSize(ServerLevel level, LivingEntity at, double radius) {
        int n = 0;
        for (LivingEntity other : com.solegendary.reignofnether.unit.UnitGrid.near(level, at.getX(), at.getZ(), radius, scan))
            if (isEnemyUnit(other) && other.distanceToSqr(at) <= radius * radius)
                n++;
        return n;
    }

    /**
     * Mirror Mages: a Mage whose Mirror Images is ready, with 3+ enemies within 12 blocks of it, mirrors the most
     * expensive mirrorable friend within reach (metal + energy; never the commander, a flyer or a T3 - MirrorImages
     * refuses those), so the enemy's focus fire lands on glass. One cast per think; keeps {@link #MIRROR_RESERVE}.
     */
    void stormMirror(ServerLevel level, List<LivingEntity> mine, com.solegendary.reignofnether.resources.Resources res) {
        if (res.getEnergy() < com.solegendary.reignofnether.ability.abilities.MirrorImages.ENERGY_COST
                * com.solegendary.reignofnether.mana.ManaServerEvents.spellCostMultiplier(name) + MIRROR_RESERVE)
            return;
        for (LivingEntity le : mine) {
            if (!(le instanceof com.solegendary.reignofnether.unit.units.storm.MirrorMageUnit mage) || !le.isAlive() || sealed(le))
                continue;
            for (com.solegendary.reignofnether.ability.Ability a : mage.getAbilities().get()) {
                if (!(a instanceof com.solegendary.reignofnether.ability.abilities.MirrorImages mi) || !mi.isOffCooldown(mage))
                    continue;
                int foes = 0;
                for (LivingEntity other : com.solegendary.reignofnether.unit.UnitGrid.near(level, mage.getX(), mage.getZ(), 12, scan))
                    if (isEnemyUnit(other) && other.distanceToSqr(mage) <= 12 * 12)
                        foes++;
                if (foes < 3)
                    break;
                int r = com.solegendary.reignofnether.ability.abilities.MirrorImages.RANGE;
                LivingEntity best = null;
                double bestScore = -1;
                for (LivingEntity other : com.solegendary.reignofnether.unit.UnitGrid.near(level, mage.getX(), mage.getZ(), r, scan)) {
                    if (other == mage || other.distanceToSqr(mage) > r * r || !(other instanceof Unit ou) || !name.equals(ou.getOwnerName())
                            || !com.solegendary.reignofnether.ability.abilities.MirrorImages.canMirror(name, other))
                        continue;
                    var cost = ou.getCost();
                    double score = cost == null ? 0 : cost.metal() + cost.energy();
                    if (score > bestScore) {
                        bestScore = score;
                        best = other;
                    }
                }
                if (best != null && com.solegendary.reignofnether.ability.abilities.MirrorImages.cast(level, mage, name, best, true) != null) {
                    mi.setToMaxCooldown(mage);
                    com.solegendary.reignofnether.ability.AbilityClientboundPacket.sendSetCooldownPacket(mage.getId(), mi.action, mi.cooldownMax);
                    return;
                }
                break;
            }
        }
    }

    /**
     * Earthshakers: a golem whose Ground Slam is ready slams toward the densest enemy clump within its line (2+ foes
     * within the line's half-width of one another). One cast per think; keeps {@link #SLAM_RESERVE}.
     */
    void stormSlam(ServerLevel level, List<LivingEntity> mine, com.solegendary.reignofnether.resources.Resources res) {
        if (res.getEnergy() < com.solegendary.reignofnether.ability.abilities.GroundSlam.ENERGY_COST
                * com.solegendary.reignofnether.mana.ManaServerEvents.spellCostMultiplier(name) + SLAM_RESERVE)
            return;
        for (LivingEntity le : mine) {
            if (!(le instanceof com.solegendary.reignofnether.unit.units.storm.EarthshakerUnit golem) || !le.isAlive() || sealed(le))
                continue;
            for (com.solegendary.reignofnether.ability.Ability a : golem.getAbilities().get()) {
                if (!(a instanceof com.solegendary.reignofnether.ability.abilities.GroundSlam slam) || !slam.isOffCooldown(golem))
                    continue;
                LivingEntity target = densestFoe(level, golem, com.solegendary.reignofnether.ability.abilities.GroundSlam.LENGTH,
                    com.solegendary.reignofnether.ability.abilities.GroundSlam.HALF_WIDTH * 2);
                if (target != null && com.solegendary.reignofnether.ability.abilities.GroundSlam.cast(level, golem, name, target.blockPosition(), true)) {
                    slam.setToMaxCooldown(golem);
                    com.solegendary.reignofnether.ability.AbilityClientboundPacket.sendSetCooldownPacket(golem.getId(), slam.action, slam.cooldownMax);
                    return;
                }
                break;
            }
        }
    }


    // ------------------------------------------------------------------ Stormcallers slice 4 habits

    /**
     * Celestial Dragons (T3), for any bot that fields them (the duel simulator too, hence {@code res} may be null: then
     * the energy reserve is not checked): a dragon whose Tempest is ready and that is not grounded calls the storm on the
     * enemy standing in the densest clump within its reach once {@link #TEMPEST_MIN_FOES} or more foes stand within the
     * storm's radius of it - the cloud still gathers half a second before the first bolt. Keeps {@link #TEMPEST_RESERVE}
     * over the up-front cost (the drain comes out of the reserve: a Tempest is meant to stall the caster's own side).
     * One grid query per dragon that is off cooldown, nothing otherwise.
     */
    void stormTempest(ServerLevel level, List<LivingEntity> mine, @javax.annotation.Nullable com.solegendary.reignofnether.resources.Resources res) {
        for (LivingEntity le : mine) {
            if (!(le instanceof com.solegendary.reignofnether.unit.units.storm.CelestialDragonUnit dragon) || !le.isAlive()
                    || dragon.isGrounded() || sealed(le))
                continue;
            if (res != null && res.getEnergy() < com.solegendary.reignofnether.mana.TempestServerEvents.ENERGY_UPFRONT
                    * com.solegendary.reignofnether.mana.ManaServerEvents.spellCostMultiplier(name) + TEMPEST_RESERVE)
                return;
            for (com.solegendary.reignofnether.ability.Ability a : dragon.getAbilities().get()) {
                if (!(a instanceof com.solegendary.reignofnether.ability.abilities.Tempest tempest) || !tempest.isOffCooldown(dragon))
                    continue;
                LivingEntity target = densestFoe(level, dragon, com.solegendary.reignofnether.ability.abilities.Tempest.RANGE,
                    com.solegendary.reignofnether.mana.TempestServerEvents.RADIUS);
                if (target == null || clumpAround(level, target, com.solegendary.reignofnether.mana.TempestServerEvents.RADIUS) < TEMPEST_MIN_FOES)
                    break;
                if (com.solegendary.reignofnether.ability.abilities.Tempest.cast(level, dragon, name, target.blockPosition(), true) != null) {
                    tempest.setToMaxCooldown(dragon);
                    com.solegendary.reignofnether.ability.AbilityClientboundPacket.sendSetCooldownPacket(dragon.getId(), tempest.action, tempest.cooldownMax);
                    return;   // one storm per think: a second would drain the pool to nothing
                }
                break;
            }
        }
    }

    /**
     * Lantern Fortresses (the duel simulator too: {@code res} may be null, then the dome is simply kept up in a fight):
     * the dome toggle goes on when {@link #DOME_MIN_FOES} or more foes stand within its radius plus a few blocks and the
     * pool holds {@link #DOME_ON_ENERGY}; off when no foe is near (the drain is the whole cost of the unit) or the pool is
     * under {@link #DOME_OFF_ENERGY}. The Fortress's own tick raises and re-raises the dome while the toggle is on.
     */
    void stormDomes(ServerLevel level, List<LivingEntity> mine, @javax.annotation.Nullable com.solegendary.reignofnether.resources.Resources res) {
        double reach = com.solegendary.reignofnether.mana.LanternDomeServerEvents.RADIUS + 4;
        for (LivingEntity le : mine) {
            if (!(le instanceof com.solegendary.reignofnether.unit.units.storm.LanternFortressUnit fortress) || !le.isAlive())
                continue;
            int foes = clumpAround(level, le, reach);
            boolean on = fortress.isDomeWanted();
            if (!on && foes >= DOME_MIN_FOES && (res == null || res.getEnergy() >= DOME_ON_ENERGY))
                fortress.setDomeWanted(true);
            else if (on && (foes == 0 || (res != null && res.getEnergy() < DOME_OFF_ENERGY)))
                fortress.setDomeWanted(false);
        }
    }

    /** How many enemy units stand within {@code radius} of {@code around} (one grid query, exact distance). */
    int clumpAround(ServerLevel level, LivingEntity around, double radius) {
        int n = 0;
        for (LivingEntity other : com.solegendary.reignofnether.unit.UnitGrid.near(level, around.getX(), around.getZ(), radius, scan))
            if (isEnemyUnit(other) && other.distanceToSqr(around) <= radius * radius)
                n++;
        return n;
    }

    /**
     * The enemy unit within {@code range} of {@code from} with the most other enemies within {@code radius} of it (at
     * least one other: a Judgement is not spent on a lone runner), nearest on a tie; null if none. StormOak's pickTarget
     * rule. The foes list is reused.
     */
    LivingEntity densestFoe(ServerLevel level, LivingEntity from, double range, double radius) {
        stormFoes.clear();
        for (LivingEntity other : com.solegendary.reignofnether.unit.UnitGrid.near(level, from.getX(), from.getZ(), range, scan))
            if (isEnemyUnit(other) && other.distanceToSqr(from) <= range * range)
                stormFoes.add(other);
        LivingEntity best = null;
        int bestCount = 0;
        double bestD = Double.MAX_VALUE, r2 = radius * radius;
        for (int i = 0, n = stormFoes.size(); i < n; i++) {
            LivingEntity a = stormFoes.get(i);
            int count = 0;
            for (int j = 0; j < n; j++)
                if (j != i && a.distanceToSqr(stormFoes.get(j)) <= r2)
                    count++;
            double d = a.distanceToSqr(from);
            if (count > bestCount || (count == bestCount && count > 0 && d < bestD)) {
                best = a;
                bestCount = count;
                bestD = d;
            }
        }
        stormFoes.clear();
        return best;
    }
    private final List<LivingEntity> stormFoes = new ArrayList<>();

    // ------------------------------------------------------------------ Tidewrought habits (slice 1)

    /**
     * For any bot whose units carry Raise Tidepool (the Tidewrought's Tide Priests, which follow the army like the
     * Court's healers - see verdantHabits): when the army has an enemy within 16 blocks of its centre, a priest off
     * cooldown raises a pool there, under the fight, so the line regenerates and the raiders get their Ambush; with
     * no army to cover, a priest under attack raises one at its own feet. Only with an energy reserve (100 + the cost),
     * so a bot never stalls its economy on water. Cheap: one centroid pass and one grid query per priest off cooldown.
     */
    void tideHabits(ServerLevel level, List<LivingEntity> mine, List<LivingEntity> army) {
        BlockPos centre = null;
        boolean fight = false;
        for (LivingEntity le : mine) {
            if (!(le instanceof Unit u) || u.getAbilities() == null || sealed(le))
                continue;
            for (com.solegendary.reignofnether.ability.Ability a : u.getAbilities().get()) {
                if (!(a instanceof com.solegendary.reignofnether.ability.abilities.RaiseTidepool) || !a.isOffCooldown(u))
                    continue;
                boolean canPay = false;
                for (var r : com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList)
                    if (r.ownerName.equals(name))
                        canPay = r.getEnergy() >= com.solegendary.reignofnether.ability.abilities.RaiseTidepool.ENERGY_COST + 100;
                if (!canPay)
                    return;
                if (centre == null && !army.isEmpty()) {
                    double cx = 0, cz = 0;
                    for (LivingEntity f : army) { cx += f.getX(); cz += f.getZ(); }
                    centre = ground(level, (int) Math.floor(cx / army.size()), (int) Math.floor(cz / army.size()));
                    for (LivingEntity other : com.solegendary.reignofnether.unit.UnitGrid.near(level, centre.getX(), centre.getZ(), 16, scan))
                        if (isEnemyUnit(other) && other.distanceToSqr(centre.getX(), other.getY(), centre.getZ()) < 16 * 16) {
                            fight = true;
                            break;
                        }
                }
                BlockPos at = null;
                if (fight && le.distanceToSqr(centre.getX(), le.getY(), centre.getZ()) <=
                        com.solegendary.reignofnether.ability.abilities.RaiseTidepool.RANGE * com.solegendary.reignofnether.ability.abilities.RaiseTidepool.RANGE)
                    at = centre;
                else if (army.isEmpty() && le.getLastHurtByMob() != null && le.tickCount - le.getLastHurtByMobTimestamp() < 60)
                    at = le.getOnPos();
                if (at != null) {
                    a.use(level, u, at);
                    return;   // one pool per think: the per-player cap is 4, don't spend it all at once
                }
            }
        }
    }

    /**
     * Kraken Callers (slice 3), for any bot that fields them: a caller whose Tentacle Grab is ready grabs the most
     * valuable enemy unit in its reach - priced by metal + energy, so artillery, casters and T2/T3 come before the T1
     * line it is already shooting at (its own autocast takes the attack target otherwise) - and a commander first of
     * all. Hidden units are never picked. One grid query per caller that is off cooldown, nothing otherwise.
     */
    void tideKrakens(ServerLevel level, List<LivingEntity> mine) {
        for (LivingEntity le : mine) {
            if (!(le instanceof com.solegendary.reignofnether.unit.units.tide.KrakenCallerUnit kc) || !le.isAlive() || sealed(le))
                continue;
            for (com.solegendary.reignofnether.ability.Ability a : kc.getAbilities().get()) {
                if (!(a instanceof com.solegendary.reignofnether.ability.abilities.TentacleGrab grab) || !grab.isOffCooldown(kc))
                    continue;
                LivingEntity target = krakenTarget(level, kc);
                if (target != null)
                    grab.use(level, kc, target);
                break;
            }
        }
    }

    /** The enemy unit a Kraken Caller should grab: a commander, else the priciest in reach. Public for the game test. */
    public LivingEntity krakenTarget(ServerLevel level, LivingEntity caller) {
        LivingEntity best = null;
        double bestScore = -1;
        int r = com.solegendary.reignofnether.ability.abilities.TentacleGrab.RANGE;
        for (LivingEntity other : com.solegendary.reignofnether.unit.UnitGrid.near(level, caller.getX(), caller.getZ(), r, scan)) {
            if (!isEnemyUnit(other) || !com.solegendary.reignofnether.ability.abilities.TentacleGrab.inReach(caller, other)
                    || com.solegendary.reignofnether.unit.Concealment.isHiddenFrom(other, name))
                continue;
            var cost = ((Unit) other).getCost();
            double score = cost == null ? 0 : cost.metal() + cost.energy();
            if (com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(other))
                score += 100000;
            if (score > bestScore) {
                bestScore = score;
                best = other;
            }
        }
        return best;
    }

    /** Enemy units in a Leviathan's slam arc that make Tidal Slam worth its 50 s cooldown. */
    static final int LEVIATHAN_SLAM_MIN_FOES = 3;

    /**
     * Leviathans (slice 4), for any bot that fields them: a Leviathan whose Tidal Slam is ready slams once
     * {@link #LEVIATHAN_SLAM_MIN_FOES} or more enemy units stand in the arc it would face (the telegraph still gives them
     * a second to step out). One grid query per Leviathan that is off cooldown, nothing otherwise.
     */
    void tideLeviathans(ServerLevel level, List<LivingEntity> mine) {
        for (LivingEntity le : mine) {
            if (!(le instanceof com.solegendary.reignofnether.unit.units.tide.LeviathanUnit lev) || !le.isAlive() || sealed(le))
                continue;
            for (com.solegendary.reignofnether.ability.Ability a : lev.getAbilities().get()) {
                if (!(a instanceof com.solegendary.reignofnether.ability.abilities.TidalSlam slam) || !slam.isOffCooldown(lev))
                    continue;
                if (slamTargets(level, lev) >= LEVIATHAN_SLAM_MIN_FOES)
                    slam.use(level, lev, lev.blockPosition());
                break;
            }
        }
    }

    /** How many enemy units stand in the arc a Leviathan's Tidal Slam would face now. Public for the game test. */
    public int slamTargets(ServerLevel level, LivingEntity leviathan) {
        double r = com.solegendary.reignofnether.ability.abilities.TidalSlam.RADIUS;
        double cosHalf = Math.cos(Math.toRadians(com.solegendary.reignofnether.ability.abilities.TidalSlam.HALF_ANGLE_DEG));
        net.minecraft.world.phys.Vec3 dir = com.solegendary.reignofnether.ability.abilities.TidalSlam.facing(level, leviathan, name);
        int n = 0;
        for (LivingEntity other : com.solegendary.reignofnether.unit.UnitGrid.near(level, leviathan.getX(), leviathan.getZ(), r, scan)) {
            if (!isEnemyUnit(other))
                continue;
            double dx = other.getX() - leviathan.getX(), dz = other.getZ() - leviathan.getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > r || (d > leviathan.getBbWidth() / 2 && (dx * dir.x + dz * dir.z) / d < cosHalf))
                continue;
            n++;
        }
        return n;
    }

    /** Enemy units within this of the army's centre make a fight worth fogging. */
    static final double FOG_BANK_SCAN = 12;

    /**
     * The Admiral's Fog Bank (slice 2), for any bot whose commander carries it: once three or more enemy units are
     * within {@link #FOG_BANK_SCAN} blocks of the army's centre, the Admiral rolls the fog over the army there (clamped
     * to its cast range), so the line vanishes as the enemy closes and only the units that open fire are seen. One
     * centroid pass and one grid query, and only while the ability is off cooldown.
     */
    void tideFogBank(ServerLevel level, List<LivingEntity> mine, List<LivingEntity> army) {
        if (army.size() < 3)
            return;
        for (LivingEntity le : mine) {
            if (!com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(le) || !(le instanceof Unit u)
                    || u.getAbilities() == null || sealed(le))
                continue;
            for (com.solegendary.reignofnether.ability.Ability a : u.getAbilities().get()) {
                if (!(a instanceof com.solegendary.reignofnether.ability.abilities.FogBank) || !a.isOffCooldown(u))
                    continue;
                double cx = 0, cz = 0;
                for (LivingEntity f : army) { cx += f.getX(); cz += f.getZ(); }
                BlockPos centre = ground(level, (int) Math.floor(cx / army.size()), (int) Math.floor(cz / army.size()));
                int enemies = 0;
                for (LivingEntity other : com.solegendary.reignofnether.unit.UnitGrid.near(level, centre.getX() + 0.5, centre.getZ() + 0.5, FOG_BANK_SCAN, scan))
                    if (isEnemyUnit(other) && other.distanceToSqr(centre.getX() + 0.5, other.getY(), centre.getZ() + 0.5) < FOG_BANK_SCAN * FOG_BANK_SCAN)
                        enemies++;
                if (enemies >= 3)
                    a.use(level, u, centre);
                return;
            }
        }
    }

    /**
     * The Grove Warden's Overgrowth once three or more enemy units are within 14 blocks: aimed at their centre, so
     * the root catches the pack and the thickets give the Court's units cover right in the fight.
     */
    void useOvergrowth(ServerLevel level, LivingEntity commander, Unit u) {
        for (com.solegendary.reignofnether.ability.Ability a : u.getAbilities().get()) {
            if (!(a instanceof com.solegendary.reignofnether.ability.abilities.Overgrowth) || !a.isOffCooldown(u))
                continue;
            int enemies = 0;
            double cx = 0, cz = 0, cy = 0;
            for (LivingEntity other : com.solegendary.reignofnether.unit.UnitGrid.near(level, commander.getX(), commander.getZ(), 14, scan)) {
                if (!isEnemyUnit(other) || other.distanceToSqr(commander) > 14 * 14)
                    continue;
                enemies++;
                cx += other.getX();
                cy += other.getY();
                cz += other.getZ();
            }
            if (enemies >= 3)
                a.use(level, u, BlockPos.containing(cx / enemies, cy / enemies - 1, cz / enemies));
            return;
        }
    }

    /**
     * The choke point toward the nearest enemy: of the points 14..30 blocks out along the line from home, the one
     * where the walkable ground across the line (columns within a block of the centre's height, no water) is
     * narrowest. Leaves {@link #choke} null when there is no enemy to face.
     */
    void findChoke(ServerLevel level) {
        BlockPos threat = nearestEnemyBuilding(home);
        if (threat == null)
            return;
        double dx = threat.getX() - home.getX(), dz = threat.getZ() - home.getZ();
        double len = Math.hypot(dx, dz);
        if (len < 30)
            return;
        dx /= len;
        dz /= len;
        double ax = -dz, az = dx;   // across the line
        BlockPos best = null;
        int bestWidth = Integer.MAX_VALUE;
        for (int out = 14; out <= Math.min(30, len - 12); out += 4) {
            BlockPos c = BlockPos.containing(home.getX() + dx * out, 0, home.getZ() + dz * out);
            level.getChunk(c.getX() >> 4, c.getZ() >> 4);
            BlockPos cg = ground(level, c.getX(), c.getZ());
            if (!level.getFluidState(cg).isEmpty() || !level.getFluidState(cg.above()).isEmpty())
                continue;
            int width = 1;
            for (int dir = -1; dir <= 1; dir += 2)
                for (int s = 1; s <= 10; s++) {
                    BlockPos g = ground(level, (int) Math.floor(c.getX() + ax * s * dir), (int) Math.floor(c.getZ() + az * s * dir));
                    if (Math.abs(g.getY() - cg.getY()) > 1 || !level.getFluidState(g).isEmpty() || !level.getFluidState(g.above()).isEmpty())
                        break;
                    width++;
                }
            if (width < bestWidth) {
                bestWidth = width;
                best = cg;
            }
        }
        choke = best;
        chokeAcrossX = ax;
        chokeAcrossZ = az;
    }

    /** The top solid block of a column from the heightmap (a single read, unlike a scan down from build height). */
    static BlockPos ground(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
    }

    /** A sealed unit (Stormcallers Seal) can't use abilities: the bot doesn't try, exactly as a player's order is refused. */
    static boolean sealed(LivingEntity le) {
        return com.solegendary.reignofnether.mana.SealServerEvents.isSealed(le);
    }

    boolean isEnemyUnit(LivingEntity other) {
        if (!(other instanceof Unit ou) || !other.isAlive())
            return false;
        String o = ou.getOwnerName();
        return o != null && !o.equals(name) && !AlliancesServerEvents.isAllied(name, o)
                && (enemyFilter == null || enemyFilter.test(o));
    }

    /** Test hook: when set, only owners it accepts count as enemies (GameTests run side by side in one world, and a
     *  neighbouring test's units must not wander into a bot brain's base scan). Always null in a real match. */
    public java.util.function.Predicate<String> enemyFilter = null;

    /**
     * The D-gun at the nearest enemy within 12 blocks, aimed through it - but only with an energy reserve left
     * over (300+), so a bot never stalls its own economy to shoot. Hard bots save less, easy bots never shoot.
     */
    void fireDGun(ServerLevel level, LivingEntity commander, Unit u) {
        if (difficulty.easyLike())
            return;
        // Heaven's Judgement is a column aimed at the densest clump and paid through the Mana core: stormHabits
        if (com.solegendary.reignofnether.ability.abilities.CommanderDGun.kindFor(u)
                == com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.JUDGEMENT)
            return;
        for (com.solegendary.reignofnether.ability.Ability a : u.getAbilities().get()) {
            if (!(a instanceof com.solegendary.reignofnether.ability.abilities.CommanderDGun) || !a.isOffCooldown(u))
                continue;
            float reserve = difficulty == Difficulty.HARD ? 150 : 300;
            boolean canPay = false;
            for (var r : com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList)
                if (r.ownerName.equals(name))
                    canPay = r.getEnergy() >= com.solegendary.reignofnether.ability.abilities.CommanderDGun.ENERGY_COST + reserve;
            if (!canPay)
                return;
            LivingEntity target = null;
            double best = 12 * 12;
            for (LivingEntity other : com.solegendary.reignofnether.unit.UnitGrid.near(level, commander.getX(), commander.getZ(), 12, scan)) {
                if (!(other instanceof Unit ou) || !other.isAlive())
                    continue;
                String o = ou.getOwnerName();
                if (o == null || o.equals(name) || AlliancesServerEvents.isAllied(name, o))
                    continue;
                double d = other.distanceToSqr(commander);
                if (d < best) {
                    best = d;
                    target = other;
                }
            }
            if (target != null)
                a.use(level, u, target.blockPosition());
            return;
        }
    }

    /** Diagnostics the soak test logs: the last building a placement failed for, how often, and when one last succeeded. */
    public Building lastFailed = null;
    public int placeFailures = 0;
    public long lastPlacedAt = -1;

    // ---- self-reporting: why the bot's next build step could not go ahead (CI's [Soak] line prints it) ----

    /** The last reason a build step was blocked ("step: reason"), or "-" when nothing has been. */
    public String lastBlockedReason = "-";
    long lastBlockedLogAt = -1;
    /** One "[Bot] ... blocked on ..." log line per bot at most this often (the think step runs every second). */
    static final long BLOCKED_LOG_TICKS = 20 * 30;

    /**
     * Records that {@code step} (a building's structure name, or a rule) is blocked for {@code reason}: the reason is
     * kept for the soak summary and logged at most once per {@link #BLOCKED_LOG_TICKS}, so a bot that cannot place a
     * lab for a minute says so once rather than every think. The caller moves on to its next step regardless.
     */
    void blocked(long gameTime, String step, String reason) {
        lastBlockedReason = step + ": " + reason;
        if (lastBlockedLogAt >= 0 && gameTime - lastBlockedLogAt < BLOCKED_LOG_TICKS)
            return;
        lastBlockedLogAt = gameTime;
        com.solegendary.reignofnether.ReignOfNether.LOGGER.warn("[Bot] {} ({}) blocked on {}: {}", name,
            faction == null ? "?" : faction.getName(), step, reason);
    }

    /** A placement that went in: the diagnostics' clock. */
    void placed(long gameTime) {
        lastPlacedAt = gameTime;
    }

    /** A placement that found no site: counted, named, and reported through {@link #blocked}. */
    void placeFailed(long gameTime, Building building, String reason) {
        lastFailed = building;
        placeFailures++;
        blocked(gameTime, building.structureName, reason);
    }

    /**
     * Army-lab slots tried and failed so far: each failure (no valid site round the slot, or a site the watch
     * cancelled because it never rose) moves the next try to the next of the six ARMY slots round home, so a lab
     * whose row faces a river or a hill still goes up on another side. The capitol had this (BotSiteWatch's
     * relocation); the lab did not, and lovish's Oct 13 ally hard bot trained 30 workers and no fighter for 13
     * minutes until two enemy bots wiped it (0 kills, 33 lost). Public for the game test.
     */
    public int labSlotShift = 0;
    /** Origins of sites the watch cancelled, with the game time: no placement lands within BAD_SITE_RADIUS for a while. */
    final java.util.Map<BlockPos, Long> badSites = new java.util.HashMap<>();
    static final long BAD_SITE_TICKS = 20 * 240;
    static final double BAD_SITE_RADIUS = 10;

    /** An army lab at the slot for the {@code index}-th lab, shifted past the slots that failed before. */
    boolean placeLab(ServerLevel level, int index, List<LivingEntity> workers) {
        boolean ok = placeNear(level, kit.armyBuilding(), layoutSlot(Layout.ARMY, index + labSlotShift), workers, 2);
        if (!ok)
            labSlotShift++;
        return ok;
    }

    /** How far the tech buildings' (T2 prerequisite, T2 lab) slot has moved past the sites that failed. */
    public int techSlotShift = 0;

    /**
     * A tech building at the army layout's slot {@code index}, shifted past the slots that had no valid site (the same slot
     * would fail the same way on every think: the Horde's Hoglin Stables sat at "no valid site" for minutes).
     */
    boolean placeTechBuilding(ServerLevel level, Building building, int index, List<LivingEntity> workers, int builders) {
        boolean ok = placeNear(level, building, layoutSlot(Layout.ARMY, index + techSlotShift), workers, builders);
        if (!ok)
            techSlotShift = (techSlotShift + 1) % 8;
        return ok;
    }

    /** A site the watch gave up on: remembered so the next placement goes elsewhere; a lab's slot moves on too. */
    void siteCancelled(BuildingPlacement bp, long gameTime) {
        badSites.put(bp.originPos, gameTime);
        if (isLab(bp))
            labSlotShift++;
    }

    /** True while a cancelled site sits within BAD_SITE_RADIUS of the position (flat), forgotten after BAD_SITE_TICKS. */
    boolean nearBadSite(BlockPos pos, long gameTime) {
        if (badSites.isEmpty())
            return false;
        badSites.values().removeIf(t -> gameTime - t > BAD_SITE_TICKS);
        for (BlockPos bad : badSites.keySet()) {
            double dx = bad.getX() - pos.getX(), dz = bad.getZ() - pos.getZ();
            if (dx * dx + dz * dz <= BAD_SITE_RADIUS * BAD_SITE_RADIUS)
                return true;
        }
        return false;
    }

    /** Tries to place a building near the wanted position, spiralling outward over valid ground. */
    boolean placeNear(ServerLevel level, Building building, BlockPos want, List<LivingEntity> workers, int builders) {
        boolean ok = placeNearImpl(level, building, want, workers, builders);
        if (ok)
            placed(level.getGameTime());
        else
            placeFailed(level.getGameTime(), building, "no valid site within 32 blocks of " + want.toShortString()
                + (workers.isEmpty() ? " (no workers)" : ""));
        return ok;
    }

    boolean placeNearImpl(ServerLevel level, Building building, BlockPos want, List<LivingEntity> workers, int builders) {
        for (int ring = 0; ring <= 8; ring++) {
            for (int attempt = 0; attempt < (ring == 0 ? 1 : 8); attempt++) {
                double a = attempt * Math.PI / 4 + ring;
                int x = want.getX() + (int) (Math.cos(a) * ring * 4);
                int z = want.getZ() + (int) (Math.sin(a) * ring * 4);
                level.getChunk(x >> 4, z >> 4);   // an unloaded column reads as the world floor (every bot capitol failed its first try)
                BlockPos surface = MiscUtil.getHighestNonAirBlock(level, new BlockPos(x, 0, z), true);   // the ground block (leaves skipped, as the player's placement grid does: a bot site on a canopy sat 5 blocks over its builders, soak stalls Oct 21-22); structures sit at origin.y+1
                if (nearBadSite(surface, level.getGameTime()))
                    continue;   // a site the watch cancelled: valid ground the workers never built on
                if (BuildingValidators.isPlacementValid(level, building, surface, name, Rotation.NONE, false, false, true)) {
                    workers.sort((u, v) -> Double.compare(u.blockPosition().distSqr(surface), v.blockPosition().distSqr(surface)));
                    int take = Math.min(builders, workers.size());
                    int[] ids = new int[take];
                    for (int i = 0; i < take; i++)
                        ids[i] = workers.get(i).getId();
                    return BuildingServerEvents.placeBuilding(building, surface, Rotation.NONE, name, ids, false, false, false, true) != null;
                }
            }
        }
        return false;
    }

    int count(List<BuildingPlacement> buildings, Building b) {
        int n = 0;
        for (BuildingPlacement bp : buildings)
            if (bp.getBuilding() == b)
                n++;
        return n;
    }

    enum Layout { WIND, CONVERTER, VAULT, FARM, HOUSE, ARMY }

    /**
     * A tidy BAR-style base instead of buildings dropped at random: "back" is away from the nearest enemy.
     * Wind generators fill rows of six behind the capitol (4-block pitch for their 3x3), converters sit at the
     * far end of the wind field, farms and houses line the two flanks, army buildings face the enemy in a row.
     * placeNear still spirals out from the slot when the ground there is bad, so this never blocks a build.
     */
    BlockPos layoutSlot(Layout kind, int index) {
        BlockPos threat = nearestEnemyBuilding(home);
        double bx = 0, bz = 1;   // default "back" = +z
        if (threat != null) {
            double dx = home.getX() - threat.getX(), dz = home.getZ() - threat.getZ();
            double len = Math.hypot(dx, dz);
            if (len > 1) { bx = dx / len; bz = dz / len; }
        }
        double px = -bz, pz = bx;   // perpendicular ("right")
        double back, side;
        switch (kind) {
            case WIND -> { back = 14 + (index / 6) * 4; side = (index % 6 - 2.5) * 4; }
            case CONVERTER -> { back = 14 + 4 * 3 + 2; side = (index % 2 == 0 ? -1 : 1) * (14 + (index / 2) * 4); }
            // vaults: a row past the converters, deepest in the base (they lose what they hold when they fall)
            case VAULT -> { back = 14 + 4 * 3 + 7; side = (index % 2 == 0 ? 1 : -1) * (2 + (index / 2) * 4); }
            case FARM -> { back = 4 + index * 12; side = 27; }
            case HOUSE -> { back = (index / 2) * 8 - 4; side = (index % 2 == 0 ? -1 : 1) * 15 * (index % 4 < 2 ? 1 : -1); }
            // army labs: a row of three facing the enemy; slots 3-5 (a lab the front row refused: placeLab's
            // shift) sit out on the two flanks and then well behind the wind field, so a front on a river or a hill
            // does not leave the bot without a lab
            default -> {
                int k = index % 3;
                if ((index / 3) % 2 == 0) { back = -12; side = (k - 1) * 11; }
                else if (k < 2) { back = 2; side = (k == 0 ? -1 : 1) * 30; }
                else { back = 36; side = 0; }
            }
        }
        return home.offset((int) Math.round(bx * back + px * side), 0, (int) Math.round(bz * back + pz * side));
    }

    /** The faction's T3 building and the experimental it makes. */
    Building t3Building() {
        return kit.t3Building();
    }

    ProductionItem t3Unit() {
        return kit.t3Unit();
    }

    /**
     * BAR bots go experimental late: from the difficulty's t3Minute (hard 11, so the T3 lab stands by ~18; medium
     * 14) a bot researches Tier 3, has a T2 constructor build its T3 lab near home, then keeps one experimental in
     * production whenever it has 20 population free. Easy bots never do (t3Minute 99).
     */
    void fieldExperimentals(ServerLevel level, List<BuildingPlacement> buildings, List<LivingEntity> workers, long minutes) {
        if (difficulty.easyLike() || minutes < tuning.t3Minute())
            return;
        if (kit.t3Building() == null || kit.t3Unit() == null || kit.t2Lab() == null)
            return;   // a faction without its T3 yet: don't sink the economy into a Tier 3 that unlocks nothing
        // Tier 3 research first (after Tier 2), at the capitol - same gate as a player
        boolean hasT2 = com.solegendary.reignofnether.research.ResearchServerEvents.playerHasResearch(name,
                com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_2);
        boolean hasT3 = com.solegendary.reignofnether.research.ResearchServerEvents.playerHasResearch(name,
                com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_3);
        if (!hasT3) {
            BuildingPlacement cap = null;
            for (BuildingPlacement bp : buildings)
                if (bp.getBuilding().isCapitol && bp.isBuilt)
                    cap = bp;
            if (hasT2 && cap instanceof ProductionPlacement cp && cp.productionQueue.isEmpty())
                cp.startProductionItem(com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_3);
            return;
        }
        BuildingPlacement t3 = null;
        for (BuildingPlacement bp : buildings)
            if (bp.getBuilding() == t3Building())
                t3 = bp;
        if (t3 == null) {
            // only a T2 constructor may raise the T3 lab (the server enforces it): the nearest one to home places
            // it, and other workers join the site through assignBuilders. None yet - train one at the T2 lab
            LivingEntity builder = null;
            for (LivingEntity w : workers)
                if (com.solegendary.reignofnether.unit.T2Workers.isT2Worker(w)
                        && (builder == null || w.blockPosition().distSqr(home) < builder.blockPosition().distSqr(home)))
                    builder = w;
            if (builder != null) {
                placeNear(level, t3Building(), layoutSlot(Layout.ARMY, 3), new ArrayList<>(List.of(builder)), 1);
            } else {
                for (BuildingPlacement bp : buildings)
                    if (bp.isBuilt && bp.getBuilding() == kit.t2Lab() && bp instanceof ProductionPlacement lab)
                        queueT2Worker(lab);
            }
            return;
        }
        if (!t3.isBuilt || !(t3 instanceof ProductionPlacement pp))
            return;
        int pop = UnitServerEvents.getCurrentPopulation(name);
        int popCap = BuildingServerEvents.getTotalPopulationSupply(name);
        boolean queued = false;
        for (var item : pp.productionQueue)
            if (item.item == t3Unit())
                queued = true;
        if (!queued && popCap - pop >= 20)
            pp.startProductionItem(t3Unit());
    }

    Building converterFor() {
        return kit.converter();
    }

    /** Energy over 80% of storage: the bot is floating energy, so a converter pays (what BAR players do). */
    boolean energyFull() {
        var eco = com.solegendary.reignofnether.resources.EconomyServerEvents.getEconomy(name);
        for (var r : com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList)
            if (r.ownerName.equals(name))
                return r.getEnergy() > eco.energyStorage * 0.8f;
        return false;
    }

    /** Metal or energy within 1% of its storage cap. */
    boolean atStorageCap() {
        var eco = com.solegendary.reignofnether.resources.EconomyServerEvents.getEconomy(name);
        for (var r : com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList)
            if (r.ownerName.equals(name))
                return r.getMetal() >= eco.metalStorage * 0.99f || r.getEnergy() >= eco.energyStorage * 0.99f;
        return false;
    }

    /** How far from home a bot's workers go for wrecks (its own territory and the field just outside it). */
    static final double RECLAIM_RANGE = 70;

    /**
     * Reclaim after fights: the nearest wrecks within RECLAIM_RANGE of home each get one idle worker (up to one per
     * eight workers, at least one, never the commander) on an explicit RECLAIM order, which keeps the worker busy on
     * the wreck until it is stripped. A wreck a worker already stands on is skipped. One pass over the wreck list.
     */
    void sendReclaimer(List<LivingEntity> workers) {
        if (home == null || workers.isEmpty())
            return;
        int reclaiming = 0;
        for (LivingEntity le : workers)
            if (com.solegendary.reignofnether.resources.WreckServerEvents.isReclaiming(le))
                reclaiming++;
        int want = Math.max(1, workers.size() / 8);
        if (reclaiming >= want)
            return;
        net.minecraft.world.entity.Entity wreck = null;
        double best = RECLAIM_RANGE * RECLAIM_RANGE;
        for (var w : com.solegendary.reignofnether.resources.WreckServerEvents.getWrecks()) {
            if (w.isRemoved())
                continue;
            double d = w.blockPosition().distSqr(home);
            if (d >= best)
                continue;
            boolean taken = false;
            for (LivingEntity le : workers)
                if (le.distanceToSqr(w) < 9) {
                    taken = true;
                    break;
                }
            if (taken)
                continue;
            best = d;
            wreck = w;
        }
        if (wreck == null)
            return;
        for (LivingEntity le : workers) {
            if (le instanceof Unit u && u.isIdle()
                    && !com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(le)) {
                UnitServerEvents.addActionItem(name, UnitAction.RECLAIM, wreck.getId(), new int[] { le.getId() },
                    wreck.blockPosition(), wreck.blockPosition());
                return;
            }
        }
    }

    void order(List<LivingEntity> units, UnitAction action, BlockPos target) {
        if (units.isEmpty())
            return;
        int[] ids = new int[units.size()];
        for (int i = 0; i < units.size(); i++)
            ids[i] = units.get(i).getId();
        if (orderListener != null)
            orderListener.accept(action, target);
        UnitServerEvents.addActionItem(name, action, -1, ids, target, target);
    }

    /** Test hook: told of every army order this bot gives (action, target); null in play. */
    public java.util.function.BiConsumer<UnitAction, BlockPos> orderListener;

    BlockPos nearestEnemyBuilding(BlockPos from) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        BlockPos bestCapitol = null;
        double bestCapitolD = Double.MAX_VALUE;
        for (BuildingPlacement bp : BuildingServerEvents.getBuildings()) {
            if (bp.isDestroyedServerside || bp.ownerName == null || bp.ownerName.isEmpty()
                    || bp.ownerName.equals(name) || AlliancesServerEvents.isAllied(name, bp.ownerName)
                    || !PlayerServerEvents.isRTSPlayer(bp.ownerName))
                continue;
            double d = bp.originPos.distSqr(from);
            if (d < bestD) { bestD = d; best = bp.originPos; }
            if (bp.getBuilding().isCapitol && d < bestCapitolD) { bestCapitolD = d; bestCapitol = bp.originPos; }
        }
        return bestCapitol != null ? bestCapitol : best;
    }

    /** Nearest enemy building of any type (no capitol preference), or null. */
    BlockPos nearestEnemyBuildingAny(BlockPos from) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BuildingPlacement bp : BuildingServerEvents.getBuildings()) {
            if (bp.isDestroyedServerside || bp.ownerName == null || bp.ownerName.isEmpty()
                    || bp.ownerName.equals(name) || AlliancesServerEvents.isAllied(name, bp.ownerName))
                continue;
            double d = bp.originPos.distSqr(from);
            if (d < bestD) { bestD = d; best = bp.originPos; }
        }
        return best;
    }

    /** Enemy flyers seen at the last scan and when it ran (the scan walks every unit, so it runs every 10 s at most). */
    int enemyFlyers = 0;
    long enemyFlyersAt = -1;

    /** BotArmy: a flyer is attacking home - the labs' anti-air rule counts it now rather than at the next scan. */
    void flyerAtHome(long gameTime) {
        if (enemyFlyers < 1) {
            enemyFlyers = 1;
            enemyFlyersAt = gameTime;
        }
    }

    /**
     * The anti-air answer of the factions that have a dedicated AA unit - the Verdant Court's Wisp Choir (T2 lab) and
     * the Tidewrought's Flak Swivel (Slipway): while the enemy fields flyers (Bone Dragons, ghasts, bats, bees, owls,
     * gulls, a flying Windcaller - WispChoirUnit.isFlyer), keep one AA unit per two of them (at least one, at most six)
     * and queue the next one where it is trained. No flyers, no AA: both are nearly useless against the ground.
     */
    void queueAntiAir(ProductionPlacement pp, List<LivingEntity> mine, long gameTime) {
        ProductionItem aa;
        Building at;
        if (faction.equals(Factions.VERDANT_COURT)) {
            aa = ProductionItems.WISP_CHOIR;
            at = kit.t2Lab();
        } else if (faction.equals(Factions.TIDEWROUGHT)) {
            aa = ProductionItems.FLAK_SWIVEL;
            at = kit.armyBuilding();
        } else if (faction.equals(Factions.STORMCALLERS)) {
            aa = ProductionItems.WIND_MONK;
            at = kit.armyBuilding();
        } else if (faction.equals(Factions.BRASSWORKS)) {
            aa = ProductionItems.FLAK_CART;
            at = kit.armyBuilding();
        } else {
            return;
        }
        if (at == null || pp.getBuilding() != at)
            return;
        if (enemyFlyersAt < 0 || gameTime - enemyFlyersAt >= 20 * 10) {
            enemyFlyersAt = gameTime;
            enemyFlyers = 0;
            for (LivingEntity le : UnitServerEvents.getAllUnits()) {
                if (!(le instanceof Unit u) || !le.isAlive() || !com.solegendary.reignofnether.unit.units.verdant.WispChoirUnit.isFlyer(le))
                    continue;
                String o = u.getOwnerName();
                if (o == null || o.isEmpty() || o.equals(name) || AlliancesServerEvents.isAllied(name, o))
                    continue;
                enemyFlyers++;
            }
        }
        if (enemyFlyers == 0)
            return;
        int want = Math.min(6, (enemyFlyers + 1) / 2);
        int have = 0;
        for (LivingEntity le : mine)
            if (le instanceof com.solegendary.reignofnether.unit.units.verdant.WispChoirUnit
                    || le instanceof com.solegendary.reignofnether.unit.units.tide.FlakSwivelUnit
                    || le instanceof com.solegendary.reignofnether.unit.units.storm.WindMonkUnit
                    || le instanceof com.solegendary.reignofnether.unit.units.brass.FlakCartUnit)
                have++;
        for (var item : pp.productionQueue)
            if (item.item == aa)
                have++;
        if (have < want)
            pp.startProductionItem(aa);
    }

    BlockPos nearestEnemyUnit(ServerLevel level, BlockPos from, double range) {
        BlockPos best = null;
        double bestD = range * range;
        for (LivingEntity le : com.solegendary.reignofnether.unit.UnitGrid.near(level, from.getX() + 0.5, from.getZ() + 0.5, range + 1, scan)) {
            if (!(le instanceof Unit u) || !le.isAlive())
                continue;
            String owner = u.getOwnerName();
            if (owner == null || owner.isEmpty() || owner.equals(name) || AlliancesServerEvents.isAllied(name, owner))
                continue;
            double d = le.blockPosition().distSqr(from);
            if (d < bestD) {
                bestD = d;
                best = le.blockPosition();
            }
        }
        return best;
    }
}
