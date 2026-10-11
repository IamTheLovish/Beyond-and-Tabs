package com.solegendary.reignofnether.mana;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.faction.FactionTraits;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.EconomyServerEvents;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesClientboundPacket;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Stormcallers' Mana core (design/stormcallers_plan.md section 3). <b>Energy is mana</b>: there is no second
 * resource, every spell is paid from the existing energy pool, and every Stormcaller ability pays through this class
 * so the cost, Overcharge and Mana Well rules are never re-implemented per ability. Server side only.
 *
 * <h2>API for spell / unit code</h2>
 * <pre>
 *   // one-shot spell (Heaven's Judgement, Blink, Seal, ...): pays cost x costMult at once, or refuses
 *   float power = ManaServerEvents.tryCast(caster, owner, 150);      // 0 = refused, nothing paid
 *   if (power &lt;= 0) return;                                          // HUD "not enough energy" already shown;
 *                                                                    // do NOT start the cooldown
 *   damage *= power;                                                 // x1.5 while overcharged
 *   ManaServerEvents.tryCastQuiet(caster, owner, 25);                // same, no HUD warning (autocasts, bots)
 *   ManaServerEvents.lastRefusal();                                  // why the last call refused (Refusal.langKey)
 *
 *   // caster attacks (Fire Adept, Wind Monk, ...): pays what the pool has, never refuses
 *   float paid = ManaServerEvents.channelShot(attacker, owner, 2);   // fraction 0-1 of the shot's cost
 *   cooldown = Math.round(baseCooldown * ManaServerEvents.shotCooldownScale(paid)); // starved = half rate
 *   damage *= ManaServerEvents.spellPowerMultiplier(owner);
 *
 *   // sustained spells (Ward, Lantern Dome, Tempest's drain)
 *   ManaServerEvents.Channel ch = ManaServerEvents.openChannel(caster, owner, 12); // pays the first second
 *   if (ch == null) return;                                          // refused (lastRefusal)
 *   ... once per second from the spell's own tick:
 *   float f = ManaServerEvents.channel(ch);                          // fraction of this second's drain paid
 *   if (!ch.isActive()) collapse();                                  // stopped itself at 0 energy
 *   ch.stop();                                                       // spell ended on its own
 *
 *   // multipliers (Overcharge; both 1 for non-Stormcallers and for anyone not overcharged)
 *   ManaServerEvents.spellCostMultiplier(owner);   // 2.0 while overcharged
 *   ManaServerEvents.spellPowerMultiplier(owner);  // 1.5 while overcharged
 *   ManaServerEvents.spellPowerAt(owner, pos);     // location factor: 1.25 inside an own/allied Mana Well
 *   ManaServerEvents.isMana(owner);                // the owner's faction has FactionTraits.mana
 * </pre>
 *
 * <h2>How spells pay</h2>
 * Every entry point ends in {@link EconomyServerEvents#drawSpell}: energy is taken from the pool <b>at once</b>
 * (spells are never throttled by the stall, so they are paid before construction asks for its share) and the draw
 * is added to the player's energy demand, so the next tick's stall drops and construction/production slow evenly -
 * heavy casting stalls building exactly as BAR stalls. The draw also shows as energy expense and, separately, as
 * the HUD tooltip's "mana drain". No per-tick work: one subtraction per cast or shot, one per channel per second.
 * <p>
 * Owners without a resource pool: casts are refused ({@link Refusal#NO_POOL}, the D-gun's rule); shots and
 * channels are free (fraction 1), like {@link EconomyServerEvents#requestFlow} treats neutral consumers.
 *
 * <h2>Overcharge</h2>
 * A per-player toggle, only for {@link #isMana} players ({@link ManaServerboundPacket}, the HUD chip on the energy
 * bar and the Overcharge keybind). While on: spells cost x{@link #OVERCHARGE_COST} for x{@link #OVERCHARGE_POWER}
 * power, the player's stall (build speed) is capped at {@link #OVERCHARGE_BUILD_CAP} ({@link #capBuildSpeed}, read
 * in EconomyServerEvents after the normal stall maths), their energy converters pause ({@link #convertersPaused}),
 * and it switches itself off the moment their energy hits 0 ({@link #checkAutoOff}) so a broke player is never
 * soft-locked at half build speed. Synced in the existing EconomyClientboundPacket (4x/s), never saved; cleared on
 * defeat ({@link #clearOwner}) and reset ({@link #clearAll}).
 *
 * <h2>Converters</h2>
 * {@link #converterThreshold} reads {@link FactionTraits#converterThreshold}: 0.5 for everyone, 0.75 for the
 * Stormcallers, so a Mana Condenser never eats the spell reserve.
 *
 * <h2>Mana Wells and Ley Links</h2>
 * The well registry ({@link #registerWell} / {@link #unregisterWell} / {@link #insideWell}) is fed by the Ley Master's
 * Mana Well (slice 3, {@link ManaWellServerEvents}); {@link #spellPowerAt} / {@link #spellCostAt} read it: x1.25 power
 * and x0.8 cost within {@link #WELL_RADIUS} of an own or allied well, at most {@link #WELLS_PER_PLAYER} per player.
 * Ley links live in {@link LeyNetwork}.
 *
 * Faction lookups are cached per owner (not per call); owners without an RTSPlayer are not cached (they may join
 * later) and read as {@link FactionTraits#NEUTRAL}. {@link #bindTraits} lets GameTests and scenarios give such an
 * owner a faction.
 */
public class ManaServerEvents {

    /** Overcharge: spells cost this much more ... */
    public static final float OVERCHARGE_COST = 2.0f;
    /** ... for this much more power (each spell documents what scales: damage, radius or duration). */
    public static final float OVERCHARGE_POWER = 1.5f;
    /** Overcharge: the player's build speed (stall) never goes above this while it is on. */
    public static final float OVERCHARGE_BUILD_CAP = 0.5f;
    /** A starved caster attack still fires at this fraction of its rate (shotCooldownScale). */
    public static final float MIN_SHOT_RATE = 0.5f;

    /** Mana Well (slice 3): radius, power and discount for spells cast inside an own/allied well. */
    public static final int WELL_RADIUS = 10;
    public static final float WELL_POWER = 1.25f;
    public static final float WELL_DISCOUNT = 0.8f;
    public static final int WELLS_PER_PLAYER = 4;

    /** Why the last call refused; langKey is null for NONE. Shown with HudClientboundPacket.showTempMessageI18n. */
    public enum Refusal {
        NONE(null),
        NO_POOL("hud.mana.reignofnether.no_pool"),
        NOT_ENOUGH_ENERGY("hud.mana.reignofnether.not_enough_energy"),
        NOT_MANA("hud.mana.reignofnether.not_mana"),
        WELL_CAP("hud.mana.reignofnether.well_cap"),
        LINK_INVALID("hud.mana.reignofnether.link_invalid"),
        LINK_CAP("hud.mana.reignofnether.link_cap"),
        CHANNEL_STOPPED("hud.mana.reignofnether.channel_stopped");

        public final String langKey;
        Refusal(String langKey) { this.langKey = langKey; }
    }

    /**
     * A sustained spell's drain. Hold it in the spell's record and call {@link #channel} once per second; it stops
     * itself when a second can't be paid at all. Not registered anywhere: dropping the reference is enough.
     */
    public static final class Channel {
        public final String owner;
        public final float energyPerSecond;
        /** may be null (a building or an area spell) - only used for the Mana Well lookup */
        public final LivingEntity source;
        private float fraction = 1f;
        private boolean active = true;

        private Channel(LivingEntity source, String owner, float energyPerSecond) {
            this.source = source;
            this.owner = owner;
            this.energyPerSecond = Math.max(0, energyPerSecond);
        }

        public boolean isActive() { return active; }
        /** fraction (0-1) of the last second's drain that was paid: below 1 the spell weakens, 0 = collapsed */
        public float fraction() { return active ? fraction : 0f; }
        /** power to apply right now (Overcharge may be toggled mid-channel) */
        public float power() { return castPower(owner, source == null ? null : source.blockPosition()); }
        public void stop() { active = false; fraction = 0f; }
    }

    private static final Set<String> overcharged = new HashSet<>();
    private static final Map<String, FactionTraits> traitsCache = new HashMap<>();
    private static final Map<String, List<BlockPos>> wells = new HashMap<>();
    private static Refusal lastRefusal = Refusal.NONE;

    // ------------------------------------------------------------------ faction

    /** The owner's faction traits, cached; NEUTRAL (uncached) while the owner has no RTSPlayer. */
    public static FactionTraits traitsOf(String owner) {
        if (owner == null || owner.isEmpty())
            return FactionTraits.NEUTRAL;
        FactionTraits t = traitsCache.get(owner);
        if (t != null)
            return t;
        RTSPlayer p = PlayerServerEvents.getRTSPlayer(owner);
        if (p == null)
            return FactionTraits.NEUTRAL;
        t = FactionTraits.of(p.faction);
        traitsCache.put(owner, t);
        return t;
    }

    /** Give an owner without an RTSPlayer (GameTests, scenarios) a faction's traits; null forgets the binding. */
    public static void bindTraits(String owner, FactionTraits traits) {
        if (traits == null)
            traitsCache.remove(owner);
        else
            traitsCache.put(owner, traits);
    }

    /** Does this owner play a Mana faction (the Stormcallers)? */
    public static boolean isMana(String owner) {
        return traitsOf(owner).mana;
    }

    // ------------------------------------------------------------------ multipliers

    public static float spellCostMultiplier(String owner) {
        return isOvercharged(owner) ? OVERCHARGE_COST : 1f;
    }

    public static float spellPowerMultiplier(String owner) {
        return isOvercharged(owner) ? OVERCHARGE_POWER : 1f;
    }

    /**
     * Location power factor for a spell cast at pos: {@link #WELL_POWER} inside an own or allied Mana Well (slice 3,
     * the Ley Master's ManaWellServerEvents), else 1. Wells don't stack.
     */
    public static float spellPowerAt(String owner, BlockPos pos) {
        return insideWell(owner, pos) ? WELL_POWER : 1f;
    }

    /** Location cost factor: {@link #WELL_DISCOUNT} inside a well, else 1 (see {@link #spellPowerAt}). */
    public static float spellCostAt(String owner, BlockPos pos) {
        return insideWell(owner, pos) ? WELL_DISCOUNT : 1f;
    }

    /** Everything that scales a spell cast by owner at pos (pos may be null): Overcharge x location. */
    public static float castPower(String owner, BlockPos pos) {
        return spellPowerMultiplier(owner) * (pos == null ? 1f : spellPowerAt(owner, pos));
    }

    private static float castCost(String owner, BlockPos pos, float baseEnergy) {
        return Math.max(0, baseEnergy) * spellCostMultiplier(owner) * (pos == null ? 1f : spellCostAt(owner, pos));
    }

    /** Attack cooldown multiplier for a shot that was paid {@code fraction} of its cost: 1 when paid, 2 when starved. */
    public static float shotCooldownScale(float fraction) {
        return 1f / Math.max(MIN_SHOT_RATE, Math.min(1f, fraction));
    }

    public static Refusal lastRefusal() {
        return lastRefusal;
    }

    private static boolean hasPool(String owner) {
        for (Resources r : ResourcesServerEvents.resourcesList)
            if (r.ownerName.equals(owner))
                return true;
        return false;
    }

    // ------------------------------------------------------------------ paying

    /** {@link #tryCastQuiet}, plus the usual "not enough energy" HUD warning when refused for energy. */
    public static float tryCast(LivingEntity caster, String owner, float energyCost) {
        float power = tryCastQuiet(caster, owner, energyCost);
        if (power <= 0 && lastRefusal == Refusal.NOT_ENOUGH_ENERGY)
            ResourcesClientboundPacket.warnInsufficientResources(owner, true, false, true, true);
        return power;
    }

    /**
     * One-shot spell: pays {@code energyCost x costMult} at once, or refuses and pays nothing.
     * @param caster may be null (a building or a scripted cast); only its position is read, for Mana Wells
     * @return the power multiplier to apply to the spell (>= 1), or 0 if refused - then {@link #lastRefusal} says why
     *         and the caller must not start its cooldown
     */
    public static float tryCastQuiet(LivingEntity caster, String owner, float energyCost) {
        lastRefusal = Refusal.NONE;
        if (owner == null || owner.isEmpty() || !hasPool(owner)) {
            lastRefusal = Refusal.NO_POOL;
            return 0f;
        }
        BlockPos pos = caster == null ? null : caster.blockPosition();
        float cost = castCost(owner, pos, energyCost);
        if (cost > 0 && EconomyServerEvents.drawSpell(owner, cost, true) <= 0) {
            lastRefusal = Refusal.NOT_ENOUGH_ENERGY;
            return 0f;
        }
        return castPower(owner, pos);
    }

    /**
     * A caster attack's per-shot cost: pays what the pool has, never refuses.
     * @return the fraction (0-1) of {@code energyPerShot x costMult} paid; feed it to {@link #shotCooldownScale}.
     *         Owners without a pool fire for free (1).
     */
    public static float channelShot(LivingEntity attacker, String owner, float energyPerShot) {
        if (owner == null || owner.isEmpty() || !hasPool(owner))
            return 1f;
        float cost = castCost(owner, attacker == null ? null : attacker.blockPosition(), energyPerShot);
        if (cost <= 0)
            return 1f;
        return Math.min(1f, EconomyServerEvents.drawSpell(owner, cost, false) / cost);
    }

    /**
     * Start a sustained spell: pays its first second now. Refused (null, nothing paid) when that second can't be paid
     * in full - a channel never starts already starved.
     */
    public static Channel openChannel(LivingEntity source, String owner, float energyPerSecond) {
        lastRefusal = Refusal.NONE;
        if (owner == null || owner.isEmpty() || !hasPool(owner)) {
            lastRefusal = Refusal.NO_POOL;
            return null;
        }
        Channel ch = new Channel(source, owner, energyPerSecond);
        float cost = castCost(owner, source == null ? null : source.blockPosition(), ch.energyPerSecond);
        if (cost > 0 && EconomyServerEvents.drawSpell(owner, cost, true) <= 0) {
            lastRefusal = Refusal.NOT_ENOUGH_ENERGY;
            return null;
        }
        return ch;
    }

    /**
     * Pay one second of a channel (call once per second, not per tick). Pays what the pool has; when nothing at all
     * can be paid the channel stops ({@link Channel#isActive} false, {@link #lastRefusal} NOT_ENOUGH_ENERGY).
     * @return the fraction (0-1) of this second's drain that was paid; 0 for a stopped channel
     */
    public static float channel(Channel ch) {
        lastRefusal = Refusal.NONE;
        if (ch == null || !ch.active) {
            lastRefusal = Refusal.CHANNEL_STOPPED;
            return 0f;
        }
        if (!hasPool(ch.owner)) {   // pool-less owners channel for free, like shots
            ch.fraction = 1f;
            return 1f;
        }
        float cost = castCost(ch.owner, ch.source == null ? null : ch.source.blockPosition(), ch.energyPerSecond);
        if (cost <= 0) {
            ch.fraction = 1f;
            return 1f;
        }
        float paid = EconomyServerEvents.drawSpell(ch.owner, cost, false);
        ch.fraction = Math.min(1f, paid / cost);
        if (paid <= 0) {
            ch.stop();
            lastRefusal = Refusal.NOT_ENOUGH_ENERGY;
        }
        return ch.fraction;
    }

    // ------------------------------------------------------------------ Overcharge

    public static boolean isOvercharged(String owner) {
        return owner != null && overcharged.contains(owner);
    }

    /**
     * Switch Overcharge on or off. Only Mana players may switch it on, and not with an empty pool (it would turn
     * itself straight off again). Switching off always works.
     * @return NONE if the state is now what was asked, else why not
     */
    public static Refusal setOvercharge(String owner, boolean on) {
        if (owner == null || owner.isEmpty())
            return Refusal.NOT_MANA;
        if (!on) {
            overcharged.remove(owner);
            return Refusal.NONE;
        }
        if (!isMana(owner))
            return Refusal.NOT_MANA;
        Resources res = null;
        for (Resources r : ResourcesServerEvents.resourcesList)
            if (r.ownerName.equals(owner))
                res = r;
        if (res == null)
            return Refusal.NO_POOL;
        if (res.getEnergy() < 1f)
            return Refusal.NOT_ENOUGH_ENERGY;
        overcharged.add(owner);
        return Refusal.NONE;
    }

    /** The packet's / keybind's toggle; shows the owner why when it is refused. */
    public static Refusal toggleOvercharge(String owner) {
        Refusal r = setOvercharge(owner, !isOvercharged(owner));
        if (r != Refusal.NONE && r.langKey != null)
            HudClientboundPacket.showTempMessageI18n(owner, r.langKey);
        return r;
    }

    /** The owner's stall after Overcharge: capped at {@link #OVERCHARGE_BUILD_CAP} while it is on. */
    public static float capBuildSpeed(String owner, float stall) {
        return isOvercharged(owner) ? Math.min(stall, OVERCHARGE_BUILD_CAP) : stall;
    }

    /** Converters don't run for an overcharged player: every spare point of energy is for spells. */
    public static boolean convertersPaused(String owner) {
        return isOvercharged(owner);
    }

    /** Energy fill level (0-1) above which this owner's converters run (FactionTraits.converterThreshold). */
    public static float converterThreshold(String owner) {
        return traitsOf(owner).converterThreshold;
    }

    /**
     * Overcharge switches itself off the moment the owner's energy hits 0 (called from the economy tick for
     * overcharged owners only). Tells the owner, so the build speed coming back is not a mystery.
     * @return true if it was switched off
     */
    public static boolean checkAutoOff(String owner, float energy) {
        if (energy >= 1f || !overcharged.remove(owner))
            return false;
        HudClientboundPacket.showTempMessageI18n(owner, "hud.mana.reignofnether.overcharge_auto_off");
        return true;
    }

    // ------------------------------------------------------------------ Mana Wells (the registry; the wells themselves are ManaWellServerEvents)

    /** Register a Mana Well's centre; refused past {@link #WELLS_PER_PLAYER}. */
    public static Refusal registerWell(String owner, BlockPos centre) {
        List<BlockPos> list = wells.computeIfAbsent(owner, k -> new ArrayList<>(WELLS_PER_PLAYER));
        if (list.contains(centre))
            return Refusal.NONE;
        if (list.size() >= WELLS_PER_PLAYER)
            return Refusal.WELL_CAP;
        list.add(centre.immutable());
        return Refusal.NONE;
    }

    public static void unregisterWell(String owner, BlockPos centre) {
        List<BlockPos> list = wells.get(owner);
        if (list != null)
            list.remove(centre);
    }

    public static int wellCount(String owner) {
        List<BlockPos> list = wells.get(owner);
        return list == null ? 0 : list.size();
    }

    /** Is pos within {@link #WELL_RADIUS} of an own or allied well? At most 4 distance checks per player. */
    public static boolean insideWell(String owner, BlockPos pos) {
        if (owner == null || pos == null || wells.isEmpty())
            return false;
        if (nearOwnWell(owner, pos))
            return true;
        for (String ally : AlliancesServerEvents.getAllAllies(owner))
            if (nearOwnWell(ally, pos))
                return true;
        return false;
    }

    private static boolean nearOwnWell(String owner, BlockPos pos) {
        List<BlockPos> list = wells.get(owner);
        if (list == null)
            return false;
        for (BlockPos c : list)
            if (c.distSqr(pos) <= WELL_RADIUS * WELL_RADIUS)
                return true;
        return false;
    }

    // ------------------------------------------------------------------ lifecycle

    /** Defeat: Overcharge off, wells (and their markers) and ley links forgotten. */
    public static void clearOwner(String owner) {
        overcharged.remove(owner);
        traitsCache.remove(owner);
        wells.remove(owner);
        ManaWellServerEvents.clearOwner(owner);
        LeyNetwork.clearOwner(owner);
    }

    /** /rts-reset: nothing about Mana outlives a match. */
    public static void clearAll() {
        overcharged.clear();
        traitsCache.clear();
        wells.clear();
        ManaWellServerEvents.clearAll();
        LeyNetwork.clearAll();
    }
}
