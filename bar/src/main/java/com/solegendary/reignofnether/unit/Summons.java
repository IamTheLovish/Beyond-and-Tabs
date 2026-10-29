package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.ability.abilities.AwakenThicket;
import com.solegendary.reignofnether.mana.DecoyServerEvents;
import com.solegendary.reignofnether.unit.units.brass.ClockworkDroneUnit;
import com.solegendary.reignofnether.unit.units.brass.DeployedTurretUnit;
import com.solegendary.reignofnether.unit.units.monsters.ZombieUnit;
import com.solegendary.reignofnether.unit.units.neutral.BeeUnit;

import net.minecraft.world.entity.Entity;

/**
 * The one "summoned" rule (design/essence_casters.md Revision 1, Oct 29: summons must not feed their own side's loops).
 * A summoned or temporary unit's death drops no essence wisp (EssenceServerEvents), sends no hero XP orbs
 * (HeroServerEvents) and gives its killer no veterancy experience (VeterancyServerEvents), and Empower refuses it.
 * <p>
 * New summons call {@link #mark} (a saved entity tag). The summons that existed before keep their own markers and are
 * read here, so nothing about them changes elsewhere: a Bone Dragon's or Necropolis' risen ({@code bt_risen}, which also
 * means no wreck and the 8-per-owner cap - not every summon should carry that), an awakened treant, a Mirror Mage's
 * decoy, a necromancer's zombie or a summoned bee, and by class the free machines (a deployed turret, an Iron Leviathan
 * Engine's clockwork drone). Wrecks still follow their own older list (WreckServerEvents.onDeath).
 */
public final class Summons {

    public static final String TAG = "bt_summon";

    private Summons() { }

    /** Marks a summon for its lifetime (saved with it). */
    public static void mark(Entity e) {
        e.addTag(TAG);
    }

    public static boolean isSummoned(Entity e) {
        if (e == null)
            return false;
        if (e.getTags().contains(TAG) || DragonRaiseServerEvents.isRisen(e) || AwakenThicket.isAwakened(e)
                || DecoyServerEvents.isDecoy(e) || e instanceof DeployedTurretUnit || e instanceof ClockworkDroneUnit)
            return true;
        return (e instanceof ZombieUnit z && z.isSummoned()) || (e instanceof BeeUnit b && b.isSummoned());
    }
}
