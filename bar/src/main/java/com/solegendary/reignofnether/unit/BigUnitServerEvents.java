package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.verdant.MossbackBisonUnit;
import com.solegendary.reignofnether.unit.units.villagers.OriflammeElephantUnit;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Damage rules of the T2 beasts (design/big_units.md), in one hurt hook:
 * <ul>
 *   <li><b>Oriflamme</b> (Oriflamme Elephant): a unit within {@link OriflammeElephantUnit#AURA_RADIUS} of a friendly or
 *   allied elephant deals x{@link OriflammeElephantUnit#AURA_DAMAGE_MULT} damage and takes
 *   x{@link OriflammeElephantUnit#AURA_TAKEN_MULT}. Several elephants never stack: the aura is on or off.</li>
 *   <li><b>Hollow Grove</b> (Mossback Bison): a rider takes x{@link MossbackBisonUnit#RIDER_DAMAGE_MULT}.</li>
 * </ul>
 * The live elephants are kept in a small list (they add themselves while alive), so the hook costs nothing when none
 * are fielded and a distance check per elephant otherwise - never a scan of every unit.
 */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID)
public class BigUnitServerEvents {

    private static final List<OriflammeElephantUnit> ELEPHANTS = new ArrayList<>();

    /** Called by each elephant's server tick while it lives (idempotent). */
    public static void trackElephant(OriflammeElephantUnit e) {
        if (!ELEPHANTS.contains(e))
            ELEPHANTS.add(e);
    }

    /** The Oriflamme's damage-dealt multiplier for {@code attacker}: AURA_DAMAGE_MULT inside a friendly aura, else 1. */
    public static float auraDamageMult(LivingEntity attacker) {
        return inFriendlyAura(attacker) ? OriflammeElephantUnit.AURA_DAMAGE_MULT : 1f;
    }

    /** The Oriflamme's damage-taken multiplier for {@code victim}: AURA_TAKEN_MULT inside a friendly aura, else 1. */
    public static float auraTakenMult(LivingEntity victim) {
        return inFriendlyAura(victim) ? OriflammeElephantUnit.AURA_TAKEN_MULT : 1f;
    }

    static boolean inFriendlyAura(LivingEntity le) {
        if (ELEPHANTS.isEmpty() || !(le instanceof Unit u))
            return false;
        String owner = u.getOwnerName();
        if (owner == null || owner.isEmpty())
            return false;
        double r2 = (double) OriflammeElephantUnit.AURA_RADIUS * OriflammeElephantUnit.AURA_RADIUS;
        for (int i = ELEPHANTS.size() - 1; i >= 0; i--) {
            OriflammeElephantUnit e = ELEPHANTS.get(i);
            if (e.isRemoved() || !e.isAlive()) {
                ELEPHANTS.remove(i);
                continue;
            }
            if (e.level() != le.level())
                continue;
            String eo = e.getOwnerName();
            if (!owner.equals(eo) && !AlliancesServerEvents.isAllied(owner, eo))
                continue;
            if (e.distanceToSqr(le) <= r2)
                return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent evt) {
        LivingEntity victim = evt.getEntity();
        if (victim.level().isClientSide())
            return;
        float mult = 1f;
        if (victim.getVehicle() instanceof MossbackBisonUnit)
            mult *= MossbackBisonUnit.RIDER_DAMAGE_MULT;
        if (!ELEPHANTS.isEmpty()) {
            mult *= auraTakenMult(victim);
            Entity src = evt.getSource().getEntity();
            if (src instanceof LivingEntity attacker && attacker != victim)
                mult *= auraDamageMult(attacker);
        }
        if (mult != 1f)
            evt.setAmount(evt.getAmount() * mult);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        ELEPHANTS.clear();
    }
}
