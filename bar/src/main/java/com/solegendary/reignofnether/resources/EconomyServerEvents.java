package com.solegendary.reignofnether.resources;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.mana.ManaServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.sandbox.SandboxServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BAR-style flow economy (serverside).
 *
 * Resources: METAL (stored in Resources.ore) and ENERGY (stored in Resources.wood).
 *
 * Nothing is paid upfront. Every "consumer" (a building under construction, a unit being trained, a research in
 * progress) calls {@link #requestFlow} each tick with its full cost and the fraction of progress df it wants to make.
 * The consumer may advance by df * granted and has already paid cost * df * granted when the call returns.
 *
 * Stall: per player we sum the demand of all consumers during a tick. At the end of the tick (after income) we
 * compute stall = min(1, metal / metalDemand, energy / energyDemand) and every consumer is throttled by it during the
 * next tick, so when a player overspends ALL of their production slows down evenly, exactly like BAR. A consumer is
 * additionally clamped to what is really left in the pool, so resources never go negative even in the tick
 * where demand first appears.
 *
 * Income: +2 metal/s and +20 energy/s while the player owns at least one completed capitol (commander-style income),
 * plus whatever every completed building returns from Building.getMetalIncome()/getEnergyIncome().
 * Income is capped by storage: 1000 metal / 1000 energy base, +500/+500 while a completed capitol stands (BAR's
 * start storage + commander storage = 1500/1500), more from storage buildings (vaults +2000/+3000, stockpiles).
 * Overflow (BAR): income that doesn't fit is split evenly among allies with room; only what nobody on the team
 * can hold is wasted. When storage shrinks (a vault or capitol dies) whatever was held in the lost storage is lost.
 *
 * Building income mapping (workers no longer gather, so the old resource buildings became passive income):
 *   - Capitols (Town Centre, Mausoleum, Central Portal): the base +2 metal/s +20 energy/s (once per player)
 *   - Farms (Wheat Farm, Pumpkin Farm, Netherwart Farm - food was RoN's main resource): +1.5 metal/s, +5 energy/s
 *   - Stockpiles (Oak/Spruce stockpile - resource drop-offs): +0.5 metal/s, +500 metal and +500 energy storage
 *   - Civilian Portal (piglin resource drop-off): +1 metal/s, +10 energy/s
 *   - Population buildings (Villager House, Haunted House, Sculk Catalyst, Pocket Portal): +5 energy/s
 * New extractor/generator buildings only need to set Building.metalIncome/energyIncome (or override the getters).
 *
 * Spells (Stormcallers Mana, mana/ManaServerEvents) pay through {@link #drawSpell}: taken at once, never throttled by
 * the stall, and counted as energy demand - so heavy casting stalls the caster's construction like BAR stalls.
 * Overcharge caps the player's stall at 50% and pauses their converters ({@link #updateStall}, {@link #convertEnergy}).
 */
public class EconomyServerEvents {

    public static final float BASE_METAL_INCOME = 2f;    // per second, while a completed capitol is owned
    public static final float BASE_ENERGY_INCOME = 20f;  // per second, while a completed capitol is owned
    public static final float DEFAULT_METAL_STORAGE = 1000f;
    public static final float DEFAULT_ENERGY_STORAGE = 1000f;
    // playtest (lovish, Verdant vs 3 bots): both bars pinned at 1000 within a minute. Storage only - income is left
    // alone so the pacing stays slow. BAR starts at 1000 + the commander's 500 of each.
    public static final float CAPITOL_METAL_STORAGE = 500f;
    public static final float CAPITOL_ENERGY_STORAGE = 500f;
    // energy converters: energy spent per metal made, and the energy fill level above which they run
    // (a last resort since the tier gate: 100 energy per metal, see resources/TierGate)
    public static final float CONVERSION_RATIO = TierGate.CONVERTER_ENERGY_PER_METAL;
    public static final float CONVERSION_THRESHOLD = 0.5f;

    private static final int TICKS_PER_SECOND = 20;
    private static final int SYNC_INTERVAL_TICKS = 5; // 4x per second
    private static final int INCOME_RECALC_TICKS = 20;

    public static class PlayerEconomy {
        public final String ownerName;
        public float metalStorage = DEFAULT_METAL_STORAGE;
        public float energyStorage = DEFAULT_ENERGY_STORAGE;
        public float metalIncome = 0; // per second
        public float energyIncome = 0; // per second
        public float metalExpense = 0; // per second, measured over the last second
        public float energyExpense = 0; // per second, measured over the last second
        // fraction (0-1) of the requested progress every consumer receives this tick; 1 = no stall
        public float stall = 1f;
        // energy/s the player's completed converters can turn into metal
        public float conversionCapacity = 0;
        // extractors (resources/TierGate): the metal/s they make at full output (part of metalIncome), the energy/s they
        // drain to make it, and the share of that drain energy could pay (1 = all; smoothed, for the HUD) - metalIncome
        // is the nominal figure, {@link #effectiveMetalIncome} what the starved extractors really make
        public float extractorMetal = 0;
        public float extractorUpkeep = 0;
        public float extractorEfficiency = 1f;
        // metal per second the converters made, measured over the last second (shown as income, like BAR)
        public float metalConverted = 0;
        private float metalConvertedWindow = 0;
        // energy per second the converters drained, measured over the last second. Already part of energyExpense;
        // sent separately so the HUD tooltip can explain why energy sits at the 50% conversion threshold
        public float energyConverted = 0;
        private float energyConvertedWindow = 0;
        // energy per second drained by spells (drawSpell), measured over the last second. Already part of
        // energyExpense; sent separately for the HUD tooltip's "mana drain" line
        public float manaDrain = 0;
        private float manaDrainWindow = 0;
        // metal actually banked this window from income+converters / from reclaim, flushed once per second into
        // the RTSPlayer's match totals for the end-of-match awards (avoids a player lookup every tick)
        private float metalProducedWindow = 0;
        private float metalReclaimedWindow = 0;
        // overflow (BAR): per second sent to allies / thrown away at full storage, measured over the last second.
        // The HUD's WASTING label shows only the wasted part; "sharing" shows what went to allies.
        public float metalShared = 0, energyShared = 0;
        public float metalWasted = 0, energyWasted = 0;
        private float metalSharedWindow = 0, energySharedWindow = 0;
        private float metalWastedWindow = 0, energyWastedWindow = 0;
        // income that didn't fit this tick, offered to allies once every player has banked (two passes, so an
        // ally's room is measured after its own income)
        private float metalOverflowTick = 0, energyOverflowTick = 0;

        // the two economy-bar sliders (BAR): converters run above this energy fill (-1 = the faction's default,
        // FactionTraits.converterThreshold), and metal above this fill is shared with allies (1 = only the overflow
        // at full storage, as before). Per player, set through setSlider, synced back in the economy packet
        public float converterLevel = -1f;
        public float shareLevel = 1f;
        private long lastSliderTick = -1000;

        // demand registered during the current tick
        private float metalDemand = 0;
        private float energyDemand = 0;
        // spending accumulated over the current 1s window
        private float metalSpentWindow = 0;
        private float energySpentWindow = 0;
        private int windowTicks = 0;

        public PlayerEconomy(String ownerName) {
            this.ownerName = ownerName;
        }

        /** Metal/s the player's extractors really make: nominal income minus what energy starvation takes off them. */
        public float effectiveMetalIncome() {
            return metalIncome - extractorMetal * (1f - extractorEfficiency);
        }
    }

    private static final Map<String, PlayerEconomy> economies = new HashMap<>();
    private static long tickCount = 0;

    public static PlayerEconomy getEconomy(String ownerName) {
        return economies.computeIfAbsent(ownerName, PlayerEconomy::new);
    }

    /**
     * Metal from reclaim (wrecks) goes straight into the pool, capped by storage like income.
     * @return how much was actually accepted (0 if the owner has no pool or storage is full)
     */
    public static float addReclaimedMetal(String ownerName, float amount) {
        if (amount <= 0)
            return 0;
        for (Resources res : ResourcesServerEvents.resourcesList) {
            if (!res.ownerName.equals(ownerName))
                continue;
            float room = getEconomy(ownerName).metalStorage - res.getMetal();
            float got = Math.max(0, Math.min(amount, room));
            if (got > 0) {
                res.addMetal(got);
                getEconomy(ownerName).metalReclaimedWindow += got;
            }
            return got;
        }
        return 0;
    }

    /**
     * Energy from reclaim (felled trees, TreeClearing) goes straight into the pool, capped by storage like income.
     * @return how much was actually accepted
     */
    public static float addReclaimedEnergy(String ownerName, float amount) {
        if (amount <= 0 || ownerName == null)
            return 0;
        for (Resources res : ResourcesServerEvents.resourcesList) {
            if (!res.ownerName.equals(ownerName))
                continue;
            float room = getEconomy(ownerName).energyStorage - res.getEnergy();
            float got = Math.max(0, Math.min(amount, room));
            if (got > 0)
                res.addEnergy(got);
            return got;
        }
        return 0;
    }

    /**
     * Energy converters, BAR-style: only the energy above {@link #CONVERSION_THRESHOLD} of storage is converted,
     * so they never starve construction, and the metal made is capped by metal storage. Public for the game test.
     * The threshold is per faction (FactionTraits.converterThreshold: the Stormcallers keep 75% as a spell reserve),
     * and an overcharged player's converters don't run at all.
     */
    public static void convertEnergy(Resources res, PlayerEconomy eco) {   // the threshold is the player's slider (converterLevel)
        if (eco.conversionCapacity <= 0 || ManaServerEvents.convertersPaused(res.ownerName))
            return;
        float energy = EconomyMath.energyToConvert(res.getEnergy(), eco.energyStorage, converterLevel(res.ownerName, eco),
            eco.metalStorage - res.getMetal(), eco.conversionCapacity / TICKS_PER_SECOND, CONVERSION_RATIO);
        if (energy <= 0)
            return;
        res.addEnergy(-energy);
        res.addMetal(energy / CONVERSION_RATIO);
        eco.energySpentWindow += energy;           // converters show up as energy expense ...
        eco.energyConvertedWindow += energy;
        eco.metalConvertedWindow += energy / CONVERSION_RATIO;   // ... and as metal income
        eco.metalProducedWindow += energy / CONVERSION_RATIO;
    }

    /**
     * Pays one tick of the player's extractor energy drain (BAR: mohos need energy) from the pool and returns the share
     * of it that could be paid, 0-1. The extractors make that share of their metal this tick, so a T2 economy on too
     * little energy slows down and recovers as soon as energy does. The whole drain counts as energy demand, so the
     * next tick's stall slows construction alongside instead of leaving the extractors the crumbs. Public for the
     * GameTest (it runs on its own pool and economy).
     */
    public static float payExtractorUpkeep(Resources res, PlayerEconomy eco) {
        if (eco.extractorUpkeep <= 0) {
            eco.extractorEfficiency = 1f;
            return 1f;
        }
        float want = eco.extractorUpkeep / TICKS_PER_SECOND;
        eco.energyDemand += want;
        float paid = Math.min(want, Math.max(0, res.getEnergy()));
        if (paid > 0) {
            res.addEnergy(-paid);
            eco.energySpentWindow += paid;
        }
        float share = paid / want;
        eco.extractorEfficiency += (share - eco.extractorEfficiency) * EXTRACTOR_SHARE_SMOOTHING;
        return share;
    }

    /** How fast the HUD's extractor efficiency follows the per-tick share (the income itself uses the exact share). */
    public static final float EXTRACTOR_SHARE_SMOOTHING = 0.1f;

    public static float getStall(String ownerName) {
        PlayerEconomy eco = economies.get(ownerName);
        return eco == null ? 1f : eco.stall;
    }

    private static Resources getResources(String ownerName) {
        for (Resources res : ResourcesServerEvents.resourcesList)
            if (res.ownerName.equals(ownerName))
                return res;
        return null;
    }

    /**
     * Ask to advance a consumer by fraction df (0-1) of its total cost.
     *
     * @param ownerName  the paying player
     * @param metalCost  the consumer's TOTAL metal cost
     * @param energyCost the consumer's TOTAL energy cost
     * @param df         the fraction of the whole job the consumer wants to complete this tick
     * @return the multiplier (0-1) to apply to df. The resources for df * multiplier have already been deducted,
     *         i.e. metalCost * df * multiplier metal and energyCost * df * multiplier energy were spent.
     *         Players without a resource pool (neutral/AI owners) and free consumers always get 1.
     */
    public static float requestFlow(String ownerName, float metalCost, float energyCost, float df) {
        if (df <= 0)
            return 1f;
        metalCost = Math.max(0, metalCost);
        energyCost = Math.max(0, energyCost);
        if (metalCost <= 0 && energyCost <= 0)
            return 1f;
        if (ownerName == null || ownerName.isEmpty())
            return 1f;
        Resources res = getResources(ownerName);
        if (res == null)
            return 1f;

        PlayerEconomy eco = getEconomy(ownerName);
        float wantMetal = metalCost * df;
        float wantEnergy = energyCost * df;
        eco.metalDemand += wantMetal;
        eco.energyDemand += wantEnergy;

        float granted = EconomyMath.granted(eco.stall, res.getMetal(), wantMetal, res.getEnergy(), wantEnergy);
        if (granted <= 0)
            return 0f;

        float paidMetal = wantMetal * granted;
        float paidEnergy = wantEnergy * granted;
        res.addMetal(-paidMetal);
        res.addEnergy(-paidEnergy);
        eco.metalSpentWindow += paidMetal;
        eco.energySpentWindow += paidEnergy;
        return granted;
    }

    /**
     * Pay for a spell (mana/ManaServerEvents is the only caller - abilities go through it). Unlike {@link #requestFlow}
     * the draw is not throttled by the stall: spells are paid first, before construction asks for its share. The draw
     * counts as energy demand, so the next tick's stall drops and the caster's construction and production slow
     * evenly (BAR stall), and it shows as energy expense and mana drain on the HUD. Never takes the pool below 0.
     *
     * @param allOrNothing true = pay the full amount or nothing (one-shot casts); false = pay what is there (shots,
     *                     channels)
     * @return the energy actually taken (0 when refused or the owner has no pool)
     */
    public static float drawSpell(String ownerName, float energy, boolean allOrNothing) {
        if (energy <= 0 || ownerName == null || ownerName.isEmpty())
            return 0;
        Resources res = getResources(ownerName);
        if (res == null)
            return 0;
        float have = Math.max(0, res.getEnergy());
        if (allOrNothing && have + 0.0001f < energy)
            return 0;
        float paid = Math.min(have, energy);
        PlayerEconomy eco = getEconomy(ownerName);
        // the whole wish counts as demand, even when the pool could only pay part of it: a starved channel or shot
        // must keep the stall down, or construction would grab the trickle of income the spell is waiting for
        eco.energyDemand += energy;
        if (paid <= 0)
            return 0;
        res.addEnergy(-paid);
        eco.energySpentWindow += paid;
        eco.manaDrainWindow += paid;
        return paid;
    }

    /**
     * Stall for the next tick, from the demand registered this tick (consumers and spells), then the Overcharge cap;
     * resets the demand. Called once per player per tick by {@link #onServerTickEnd}; public for the GameTests,
     * which run it on their own pools only.
     */
    public static void updateStall(Resources res, PlayerEconomy eco) {
        float stall = EconomyMath.stall(res.getMetal(), eco.metalDemand, res.getEnergy(), eco.energyDemand);
        eco.stall = ManaServerEvents.capBuildSpeed(res.ownerName, stall);
        eco.metalDemand = 0;
        eco.energyDemand = 0;
    }

    // refund resources (eg. a cancelled build or production), as whole numbers so the HUD can animate it
    public static void refund(String ownerName, float metal, float energy) {
        if (ownerName == null || ownerName.isEmpty())
            return;
        int m = Math.round(metal);
        int e = Math.round(energy);
        if (m > 0 || e > 0)
            ResourcesServerEvents.addSubtractResources(new Resources(ownerName, 0, Math.max(0, e), Math.max(0, m)));
    }

    // recompute income and storage from the buildings each player owns. Public for the GameTest.
    public static void recalculateIncomeAndStorage() {
        Map<String, Boolean> ownsCapitol = new HashMap<>();
        Map<String, float[]> totals = new HashMap<>(); // metalIncome, energyIncome, metalStorage, energyStorage, conversion, extractorMetal, extractorUpkeep

        for (BuildingPlacement bpl : BuildingServerEvents.getBuildings()) {
            if (!bpl.isBuilt || bpl.ownerName == null || bpl.ownerName.isEmpty())
                continue;
            if (bpl.isCapitol)
                ownsCapitol.put(bpl.ownerName, true);
            float[] t = totals.computeIfAbsent(bpl.ownerName, k -> new float[7]);
            t[0] += bpl.getMetalIncome();
            float upkeep = bpl.getEnergyUpkeep();
            if (upkeep > 0)
                t[5] += bpl.getMetalIncome();   // only extractors that drain energy are throttled by it
            t[6] += upkeep;
            t[1] += bpl.getEnergyIncome();
            t[2] += bpl.getMetalStorage();
            t[3] += bpl.getEnergyStorage();
            t[4] += bpl.getBuilding().getEnergyConversion();
        }
        for (Resources res : ResourcesServerEvents.resourcesList) {
            PlayerEconomy eco = getEconomy(res.ownerName);
            float[] t = totals.getOrDefault(res.ownerName, new float[7]);
            boolean capitol = ownsCapitol.getOrDefault(res.ownerName, false);
            // the match's economy pace (EconomyPace, design/economy_pace.md) scales every income source here, so
            // the HUD's income figure, the bots' income rules and the banking below all see the paced number
            float pace = EconomyPace.of(res.ownerName);
            eco.metalIncome = ((capitol ? BASE_METAL_INCOME : 0) + t[0]) * pace;
            eco.energyIncome = ((capitol ? BASE_ENERGY_INCOME : 0) + t[1]) * pace;
            float metalStorage = DEFAULT_METAL_STORAGE + (capitol ? CAPITOL_METAL_STORAGE : 0) + t[2];
            float energyStorage = DEFAULT_ENERGY_STORAGE + (capitol ? CAPITOL_ENERGY_STORAGE : 0) + t[3];
            setStorage(res, eco, metalStorage, energyStorage);
            eco.conversionCapacity = t[4] * pace;   // converters are income too (same metal per energy, faster)
            // extractors' energy drain scales with the pace like every income, so the ratio holds at any pace
            eco.extractorMetal = t[5] * pace;
            eco.extractorUpkeep = t[6] * pace;
        }
    }

    /**
     * Change a player's storage. When it shrinks, what was held in the lost storage is lost with it (BAR): the pool
     * drops by up to the storage lost, but never below the new cap - so a refund sitting briefly above the cap is
     * not eaten by an unrelated loss. Sandbox pools (999999) are left alone.
     */
    private static void setStorage(Resources res, PlayerEconomy eco, float metalStorage, float energyStorage) {
        float metalLost = eco.metalStorage - metalStorage, energyLost = eco.energyStorage - energyStorage;
        eco.metalStorage = metalStorage;
        eco.energyStorage = energyStorage;
        if (res == null || (metalLost <= 0 && energyLost <= 0) || SandboxServer.isSandboxPlayer(res.ownerName))
            return;
        float metalGone = EconomyMath.lostFromPool(res.getMetal(), metalStorage, metalLost);
        float energyGone = EconomyMath.lostFromPool(res.getEnergy(), energyStorage, energyLost);
        if (metalGone > 0)
            res.addMetal(-metalGone);
        if (energyGone > 0)
            res.addEnergy(-energyGone);
    }

    /**
     * A completed storage building died: drop its storage now (the 1 s recalc agrees, but the loss should land the
     * moment it blows up) and trim what it held.
     * @return {metal, energy} actually lost, for the burst FX
     */
    public static float[] onStorageLost(String ownerName, float metalStorage, float energyStorage) {
        Resources res = getResources(ownerName);
        if (res == null)
            return new float[2];
        PlayerEconomy eco = getEconomy(ownerName);
        float metalBefore = res.getMetal(), energyBefore = res.getEnergy();
        setStorage(res, eco, Math.max(DEFAULT_METAL_STORAGE, eco.metalStorage - metalStorage),
            Math.max(DEFAULT_ENERGY_STORAGE, eco.energyStorage - energyStorage));
        return new float[] { metalBefore - res.getMetal(), energyBefore - res.getEnergy() };
    }

    private static float room(String ownerName, boolean metal) {
        Resources res = getResources(ownerName);
        if (res == null)
            return 0;
        PlayerEconomy eco = getEconomy(ownerName);
        return metal ? eco.metalStorage - res.getMetal() : eco.energyStorage - res.getEnergy();
    }

    /** Slider limits: a converter threshold of 0 would drain the pool to nothing, so it keeps a sliver. */
    public static final float CONVERTER_MIN = 0.05f;
    public static final float SHARE_MIN = 0.0f;
    /** Ticks between two accepted slider changes from one player (a drag sends faster than that; the last one wins). */
    public static final int SLIDER_MIN_TICKS = 2;
    /** Metal per second one player can hand to allies through the share slider (the overflow at 100% is not limited). */
    public static final float SHARE_RATE_PER_SECOND = 300f;

    /** Forgets the rate limit's last change for this player (the GameTest sets several levels inside one tick). */
    public static void clearSliderLimit(String owner) {
        getEconomy(owner).lastSliderTick = -1000;
    }

    /** The energy fill above which this player's converters run: their slider, or the faction's default. */
    public static float converterLevel(String owner, PlayerEconomy eco) {
        return eco.converterLevel >= 0f ? eco.converterLevel : ManaServerEvents.converterThreshold(owner);
    }

    /**
     * The serverbound slider packet's rule (ServerPlayer name only, never another player's economy): energy = the
     * converter threshold, else the metal share level. Refused when the value is not a number, when the player has no
     * pool (observers, neutral owners) or when it comes faster than {@link #SLIDER_MIN_TICKS}; otherwise clamped and
     * stored. Public for the GameTest.
     * @return the stored level, or -1 when refused
     */
    public static float setSlider(String owner, boolean energy, float level) {
        if (owner == null || owner.isEmpty() || !EconomyMath.isRealLevel(level) || getResources(owner) == null)
            return -1f;
        PlayerEconomy eco = getEconomy(owner);
        if (EconomyMath.sliderTooFast(tickCount, eco.lastSliderTick, SLIDER_MIN_TICKS))
            return -1f;
        eco.lastSliderTick = tickCount;
        if (energy)
            eco.converterLevel = EconomyMath.clampLevel(level, CONVERTER_MIN);
        else
            eco.shareLevel = EconomyMath.clampLevel(level, SHARE_MIN);
        return energy ? eco.converterLevel : eco.shareLevel;
    }

    /**
     * The metal share slider (BAR): everything above {@code shareLevel} of storage goes to allies, at most
     * {@link #SHARE_RATE_PER_SECOND}, split between the allies that are below THEIR own share level (an ally that
     * shares above 50% takes nothing past 50%: no two players pass the same metal back and forth). Allies full or
     * at their level are skipped. Public for the GameTest.
     * @return the metal given away
     */
    public static float shareAboveLevel(String ownerName) {
        Resources res = getResources(ownerName);
        if (res == null)
            return 0;
        PlayerEconomy eco = getEconomy(ownerName);
        float excess = EconomyMath.shareExcess(res.getMetal(), eco.shareLevel, eco.metalStorage,
            SHARE_RATE_PER_SECOND / TICKS_PER_SECOND);
        if (excess <= 0)
            return 0;
        Set<String> allies = AlliancesServerEvents.getAllAllies(ownerName);
        if (allies.isEmpty())
            return 0;
        List<String> names = new ArrayList<>(allies);
        float[] rooms = new float[names.size()];
        for (int i = 0; i < rooms.length; i++)
            rooms[i] = shareRoom(names.get(i));
        float given = giveToAllies(names, EconomyMath.splitAmongAllies(excess, rooms), true);
        if (given > 0)
            res.addMetal(-given);
        return given;
    }

    /** How much metal an ally takes from a share: up to storage, or up to its own share level when it shares too. */
    private static float shareRoom(String ally) {
        Resources res = getResources(ally);
        if (res == null)
            return 0;
        PlayerEconomy eco = getEconomy(ally);
        float cap = eco.shareLevel >= 0.999f ? eco.metalStorage : eco.shareLevel * eco.metalStorage;
        return Math.max(0, cap - res.getMetal());
    }

    /**
     * BAR overflow: hand income that didn't fit in the owner's storage to allies with room, split evenly between
     * them (a second pass offers what a nearly-full ally couldn't take to the others). Enemies never receive
     * anything - only {@link AlliancesServerEvents#getAllAllies} are considered. Public for the GameTest.
     * @return how much the allies took; the rest is waste
     */
    public static float shareOverflow(String ownerName, float amount, boolean metal) {
        if (amount <= 0)
            return 0;
        Set<String> allies = AlliancesServerEvents.getAllAllies(ownerName);
        if (allies.isEmpty())
            return 0;
        List<String> names = new ArrayList<>(allies);
        float[] rooms = new float[names.size()];
        for (int i = 0; i < rooms.length; i++)
            rooms[i] = room(names.get(i), metal);
        return giveToAllies(names, EconomyMath.splitAmongAllies(amount, rooms), metal);
    }

    /** Pays out what EconomyMath.splitAmongAllies decided; returns the total handed over. */
    private static float giveToAllies(List<String> names, float[] gives, boolean metal) {
        float total = 0;
        for (int i = 0; i < gives.length; i++) {
            if (gives[i] <= 0)
                continue;
            Resources res = getResources(names.get(i));
            if (metal)
                res.addMetal(gives[i]);
            else
                res.addEnergy(gives[i]);
            total += gives[i];
        }
        return total;
    }

    // called once per server tick (END phase, after every building and unit has ticked) by ResourcesServerEvents
    public static void onServerTickEnd() {
        tickCount += 1;
        Wind.tick(tickCount);
        if (tickCount % INCOME_RECALC_TICKS == 1)
            recalculateIncomeAndStorage();

        for (Resources res : ResourcesServerEvents.resourcesList) {
            PlayerEconomy eco = getEconomy(res.ownerName);

            // income, capped at storage; what doesn't fit is offered to allies below, after everyone has banked
            float energyIn = eco.energyIncome / TICKS_PER_SECOND;
            eco.metalOverflowTick = 0;
            eco.energyOverflowTick = 0;
            // energy first: the extractors' drain is paid from the pool the income just filled, and the metal they make
            // is what that drain could buy (resources/TierGate)
            if (energyIn > 0) {
                float banked = EconomyMath.banked(energyIn, eco.energyStorage, res.getEnergy());
                if (banked > 0)
                    res.addEnergy(banked);
                eco.energyOverflowTick = energyIn - banked;
            }
            float extractorShare = payExtractorUpkeep(res, eco);
            float metalIn = (eco.metalIncome - eco.extractorMetal * (1f - extractorShare)) / TICKS_PER_SECOND;
            if (metalIn > 0) {
                float banked = EconomyMath.banked(metalIn, eco.metalStorage, res.getMetal());
                if (banked > 0) {
                    res.addMetal(banked);
                    eco.metalProducedWindow += banked;
                }
                eco.metalOverflowTick = metalIn - banked;
            }

            // Overcharge switches itself off at 0 energy (one set lookup for everyone else)
            if (ManaServerEvents.isOvercharged(res.ownerName))
                ManaServerEvents.checkAutoOff(res.ownerName, res.getEnergy());

            convertEnergy(res, eco);

            // stall for the next tick, from the demand registered this tick
            updateStall(res, eco);

            // expense per second over a rolling 1 second window
            eco.windowTicks += 1;
            if (eco.windowTicks >= TICKS_PER_SECOND) {
                eco.metalExpense = eco.metalSpentWindow * TICKS_PER_SECOND / eco.windowTicks;
                eco.energyExpense = eco.energySpentWindow * TICKS_PER_SECOND / eco.windowTicks;
                eco.metalConverted = eco.metalConvertedWindow * TICKS_PER_SECOND / eco.windowTicks;
                eco.energyConverted = eco.energyConvertedWindow * TICKS_PER_SECOND / eco.windowTicks;
                eco.manaDrain = eco.manaDrainWindow * TICKS_PER_SECOND / eco.windowTicks;
                eco.manaDrainWindow = 0;
                eco.metalSpentWindow = 0;
                eco.energySpentWindow = 0;
                eco.metalConvertedWindow = 0;
                eco.energyConvertedWindow = 0;
                eco.metalShared = eco.metalSharedWindow * TICKS_PER_SECOND / eco.windowTicks;
                eco.energyShared = eco.energySharedWindow * TICKS_PER_SECOND / eco.windowTicks;
                eco.metalWasted = eco.metalWastedWindow * TICKS_PER_SECOND / eco.windowTicks;
                eco.energyWasted = eco.energyWastedWindow * TICKS_PER_SECOND / eco.windowTicks;
                eco.metalSharedWindow = eco.energySharedWindow = 0;
                eco.metalWastedWindow = eco.energyWastedWindow = 0;
                eco.windowTicks = 0;
                if (eco.metalProducedWindow > 0 || eco.metalReclaimedWindow > 0) {
                    RTSPlayer rtsPlayer = PlayerServerEvents.getRTSPlayer(res.ownerName);
                    if (rtsPlayer != null) {
                        rtsPlayer.metalProduced += eco.metalProducedWindow;
                        rtsPlayer.metalReclaimed += eco.metalReclaimedWindow;
                    }
                    eco.metalProducedWindow = 0;
                    eco.metalReclaimedWindow = 0;
                }
            }
        }
        // second pass: overflow to allies (BAR), measured after every player has banked its own income this tick.
        // Shared/wasted land in the window that is flushed next tick at the latest - close enough for a 1 s average
        for (Resources res : ResourcesServerEvents.resourcesList) {
            PlayerEconomy eco = getEconomy(res.ownerName);
            if (eco.shareLevel < 0.999f)
                eco.metalSharedWindow += shareAboveLevel(res.ownerName);   // the share slider (what is above the player's level)
            if (eco.metalOverflowTick > 0) {
                float shared = shareOverflow(res.ownerName, eco.metalOverflowTick, true);
                eco.metalSharedWindow += shared;
                eco.metalWastedWindow += eco.metalOverflowTick - shared;
                eco.metalOverflowTick = 0;
            }
            if (eco.energyOverflowTick > 0) {
                float shared = shareOverflow(res.ownerName, eco.energyOverflowTick, false);
                eco.energySharedWindow += shared;
                eco.energyWastedWindow += eco.energyOverflowTick - shared;
                eco.energyOverflowTick = 0;
            }
        }
        if (tickCount % SYNC_INTERVAL_TICKS == 0)
            syncAll();
    }

    public static void syncAll() {
        for (Resources res : ResourcesServerEvents.resourcesList) {
            PlayerEconomy eco = getEconomy(res.ownerName);
            EconomyClientboundPacket.sync(res, eco);
        }
    }

    public static void reset() {
        economies.clear();
    }
}
