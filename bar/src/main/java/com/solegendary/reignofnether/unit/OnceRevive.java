package com.solegendary.reignofnether.unit;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * "Rises once": a unit whose first lethal hit is cancelled (design/stormcallers_plan.md, the Phoenix; the Gravebound's
 * Deathless Champion is designed on the same rule). The entity implements {@link Revivable}; the {@link LivingDeathEvent}
 * handler here runs at HIGHEST priority, so when it cancels the death none of the ordinary death handlers run at all -
 * no wreck (WreckServerEvents), no kill credit or veterancy (VeterancyServerEvents), no death blast, no commander
 * blast, no unit-list removal: the unit never died. Its health is set to {@link Revivable#reviveHealth()} inside the
 * handler (vanilla's die() was skipped, but hurt() already took the health to 0 and baseTick would start the death
 * animation otherwise), and {@link Revivable#onCheatDeath} lets the unit enter whatever weakened state its design
 * calls for. The once-flag ({@link #KEY_SPENT}) lives in the entity's persistent data, so it survives a save and a
 * second lethal hit is an ordinary death with all of the above.
 * <p>
 * Cost at 8v8: one instanceof per death event, nothing per tick.
 */
public final class OnceRevive {
    private OnceRevive() { }

    /** Persistent-data flag: this unit has already risen once. */
    public static final String KEY_SPENT = "bt_revived_once";

    /** A unit that rises once. Implement on the entity class. */
    public interface Revivable {
        /** Health the unit is left with as the death is cancelled (must be > 0). */
        float reviveHealth();

        /** May this death be cheated (false for the void, /kill, a discard)? */
        default boolean canRevive(DamageSource source) {
            return !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
        }

        /** Called once the death is cancelled: enter the weakened state (an ember, a kneeling champion). */
        void onCheatDeath(DamageSource source);
    }

    /** Has this unit already used its revival? */
    public static boolean isSpent(LivingEntity le) {
        return le.getPersistentData().getBoolean(KEY_SPENT);
    }

    /** Marks the revival as used (public for a unit that wants to forfeit it, and for the game test). */
    public static void markSpent(LivingEntity le) {
        le.getPersistentData().putBoolean(KEY_SPENT, true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent evt) {
        LivingEntity le = evt.getEntity();
        if (le.level().isClientSide() || !(le instanceof Revivable r) || isSpent(le) || !r.canRevive(evt.getSource()))
            return;
        markSpent(le);
        evt.setCanceled(true);
        le.setHealth(Math.max(1f, r.reviveHealth()));
        le.clearFire();   // a burning unit would be killed again next tick, before it ever rose
        le.deathTime = 0;
        r.onCheatDeath(evt.getSource());
    }
}
