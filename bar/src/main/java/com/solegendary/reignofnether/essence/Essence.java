package com.solegendary.reignofnether.essence;

import com.solegendary.reignofnether.ability.abilities.AwakenThicket;
import com.solegendary.reignofnether.mana.DecoyServerEvents;
import com.solegendary.reignofnether.unit.DragonRaiseServerEvents;
import com.solegendary.reignofnether.unit.units.brass.ClockworkDroneUnit;
import com.solegendary.reignofnether.unit.units.brass.DeployedTurretUnit;
import com.solegendary.reignofnether.unit.units.monsters.ZombieUnit;
import com.solegendary.reignofnether.unit.units.neutral.BeeUnit;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Essence, the casters' second resource (design/essence_casters.md; lovish approved draft 2, Oct 29 2026). Every death
 * drops a floating wisp ({@link EssenceServerEvents}) worth {@link #essenceFor} the unit's metal; a carrier pulls the
 * wisps near it in and holds at most {@link #CAPACITY}; its abilities spend it ({@link #trySpend}).
 * <p>
 * A carrier is any unit whose class implements {@link EssenceCarrier} (the race casters) or that carries
 * {@link #CARRIER_TAG}. Its essence is an int in its persistent data, like veterancy experience: saved with the unit,
 * never on the client (a caster PR that shows it on the HUD adds the sync it needs). Server side only.
 */
public final class Essence {

    /** The most essence one carrier holds (lovish's draft: 30). */
    public static final int CAPACITY = 30;
    /** One essence per this much metal of a dead unit's cost (lovish's draft: 25), at least 1. */
    public static final float METAL_PER_ESSENCE = 25f;
    /** Makes any unit a carrier (tags are saved with the entity). */
    public static final String CARRIER_TAG = "bt_essence_carrier";
    /** A summon: its death drops no wisp, so summons cannot farm essence. */
    public static final String NO_ESSENCE_TAG = "bt_no_essence";
    static final String KEY = "bt_essence";

    private Essence() { }

    /** The essence a unit of {@code metal} cost is worth: 1 per 25 metal, rounded down, at least 1. */
    public static int essenceFor(float metal) {
        return Math.max(1, (int) Math.floor(metal / METAL_PER_ESSENCE));
    }

    public static boolean isCarrier(Entity e) {
        return e instanceof EssenceCarrier || (e != null && e.getTags().contains(CARRIER_TAG));
    }

    /** Flags (or unflags) a unit as a carrier at run time; a class implementing {@link EssenceCarrier} always is one. */
    public static void setCarrier(Entity e, boolean carrier) {
        if (carrier)
            e.addTag(CARRIER_TAG);
        else
            e.removeTag(CARRIER_TAG);
    }

    /** The essence {@code carrier} holds (0 for anything that is not a carrier). */
    public static int get(LivingEntity carrier) {
        return isCarrier(carrier) ? carrier.getPersistentData().getInt(KEY) : 0;
    }

    /** How much more essence {@code carrier} can take (0 for a non-carrier). */
    public static int room(LivingEntity carrier) {
        return isCarrier(carrier) ? Math.max(0, CAPACITY - get(carrier)) : 0;
    }

    /** Adds up to {@code amount} (never past {@link #CAPACITY}) and returns how much was taken; 0 for a non-carrier. */
    public static int add(LivingEntity carrier, int amount) {
        int took = Math.min(Math.max(0, amount), room(carrier));
        if (took > 0)
            carrier.getPersistentData().putInt(KEY, get(carrier) + took);
        return took;
    }

    /**
     * Spends {@code amount}: true and taken, or false with nothing changed (short of essence, or not a carrier). A
     * caster checks every other condition of its cast first and spends last, so a refusal never costs essence.
     */
    public static boolean trySpend(LivingEntity carrier, int amount) {
        if (amount < 0 || !isCarrier(carrier))
            return false;
        int have = get(carrier);
        if (have < amount)
            return false;
        carrier.getPersistentData().putInt(KEY, have - amount);
        return true;
    }

    /** Marks a summon (call it on every unit a caster conjures): its death drops no wisp. */
    public static void markSummoned(Entity e) {
        e.addTag(NO_ESSENCE_TAG);
    }

    /**
     * Summoned or temporary: dies without a wisp. The marked ones (hero summons in HeroCore.addSummon, an infested
     * building's limited-life silverfish), and the summons that existed before essence by their own markers - a Bone
     * Dragon's risen, an awakened treant (Awaken Thicket and the World Tree Walker's grove), a Mirror Mage's decoy, a
     * necromancer's zombie or a summoned bee - and the free machines by class: a deployed turret, an Iron Leviathan
     * Engine's clockwork drone (replaced every wave for nothing, so it could be farmed).
     */
    public static boolean isSummoned(Entity e) {
        if (e == null)
            return false;
        if (e.getTags().contains(NO_ESSENCE_TAG) || DragonRaiseServerEvents.isRisen(e) || AwakenThicket.isAwakened(e)
                || DecoyServerEvents.isDecoy(e) || e instanceof DeployedTurretUnit || e instanceof ClockworkDroneUnit)
            return true;
        return (e instanceof ZombieUnit z && z.isSummoned()) || (e instanceof BeeUnit b && b.isSummoned());
    }
}
