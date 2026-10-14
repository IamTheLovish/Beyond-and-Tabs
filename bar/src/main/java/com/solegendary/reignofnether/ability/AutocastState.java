package com.solegendary.reignofnether.ability;

import com.solegendary.reignofnether.hero.PossessionServer;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Arrays;

/**
 * A unit's per-ability autocast switches. RoN held ONE autocast ability per unit (a field in every unit class, never
 * saved); here every ability of a unit is on or off by itself: on by default for every {@link AutoRule} but MANUAL
 * (and for a hero passive that asks, {@link Ability#isDefaultAutocast}), and the player flips one with the ability's
 * button (the legacy *_AUTOCAST_ENABLE / DISABLE orders, or the generic AUTOCAST_ENABLE / DISABLE order with the
 * ability's action in the order's block position).
 * <p>
 * Only the player's overrides are stored, as an int array in the entity's persistent data (the UpgradeVisuals
 * precedent: no field in 100+ unit classes), each entry {@code action.ordinal() * 2 + (on ? 1 : 0)} keyed by the
 * ability's {@link UnitAction} - stable across a save, a sync and both sides, where the Ability instances are not.
 * Written with the unit's save data, carried by the batched unit sync ({@code Field.AUTOCAST}) to the players who
 * know the unit privately, and applied on the client by the same order prediction a click runs. Server-safe.
 */
public final class AutocastState {
    private AutocastState() { }

    /** The persistent-data / save key. */
    public static final String KEY = "barAutocast";
    private static final int[] NONE = new int[0];

    /** Whether {@code unit} casts {@code ability} by itself: the player's override, else the ability's default. */
    public static boolean isOn(Unit unit, Ability ability) {
        if (ability == null || ability.action == null)
            return false;
        int[] ov = overrides(unit);
        int ord = ability.action.ordinal();
        for (int v : ov)
            if ((v >> 1) == ord)
                return (v & 1) == 1;
        return ability.isDefaultAutocast() || ability.canAutocast();
    }

    /** Sets the switch for one ability (an override, even when it equals the default: the player chose it). */
    public static void set(Unit unit, Ability ability, boolean on) {
        if (ability == null || ability.action == null)
            return;
        set(unit, ability.action, on);
    }

    public static void set(Unit unit, UnitAction abilityAction, boolean on) {
        int[] ov = overrides(unit);
        int ord = abilityAction.ordinal();
        int entry = ord * 2 + (on ? 1 : 0);
        for (int i = 0; i < ov.length; i++)
            if ((ov[i] >> 1) == ord) {
                if (ov[i] == entry)
                    return;
                int[] copy = ov.clone();
                copy[i] = entry;
                setOverrides(unit, copy);
                return;
            }
        int[] grown = Arrays.copyOf(ov, ov.length + 1);
        grown[ov.length] = entry;
        setOverrides(unit, grown);
    }

    /**
     * Every ability of the unit on or off at once (an override per ability). The game tests use it on bystanders whose
     * energy they count: CI 38505615622 lost the Ground Slam test to a friendly Ink Scribe autocasting Seal (25 energy)
     * at the enemy two blocks away, a tick before the test read the pool.
     */
    public static void setAll(Unit unit, boolean on) {
        for (Ability a : unit.getAbilities().get())
            set(unit, a, on);
    }

    /** Applies a generic AUTOCAST_ENABLE / AUTOCAST_DISABLE order: the ability's action ordinal rides in {@code x}. */
    public static void applyOrder(Unit unit, UnitAction order, int abilityActionOrdinal) {
        UnitAction[] all = UnitAction.values();
        if (abilityActionOrdinal < 0 || abilityActionOrdinal >= all.length)
            return;
        UnitAction target = all[abilityActionOrdinal];
        for (Ability a : unit.getAbilities().get())
            if (a.action == target) {
                set(unit, a, order == UnitAction.AUTOCAST_ENABLE);
                return;
            }
    }

    /** The stored overrides (shared array: do not modify). */
    public static int[] overrides(Unit unit) {
        CompoundTag data = ((Entity) unit).getPersistentData();
        return data.contains(KEY) ? data.getIntArray(KEY) : NONE;
    }

    public static void setOverrides(Unit unit, int[] overrides) {
        CompoundTag data = ((Entity) unit).getPersistentData();
        if (overrides == null || overrides.length == 0)
            data.remove(KEY);
        else
            data.putIntArray(KEY, overrides);
    }

    public static void save(Unit unit, CompoundTag tag) {
        int[] ov = overrides(unit);
        if (ov.length > 0)
            tag.putIntArray(KEY, ov);
    }

    public static void load(Unit unit, CompoundTag tag) {
        if (tag.contains(KEY))
            setOverrides(unit, tag.getIntArray(KEY));
    }

    /**
     * A unit that never casts by itself: a hero ({@link HeroUnit}, a commander), or one a player is possessing. The
     * HUD hides the autocast mark on these; the AutoCaster skips them.
     */
    public static boolean isHero(LivingEntity le) {
        if (le instanceof HeroUnit || CommanderServerEvents.isCommander(le))
            return true;
        if (le.level().isClientSide())
            return CommanderServerEvents.looksLikeCommander(le);
        return PossessionServer.isPossessed(le);
    }
}
