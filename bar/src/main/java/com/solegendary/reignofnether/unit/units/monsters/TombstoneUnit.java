package com.solegendary.reignofnether.unit.units.monsters;

import com.solegendary.reignofnether.registrars.AttributeRegistrar;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The Tombcaller's <b>tombstone</b> (design/essence_casters.md, "Gravebound Gravecaller" and Revision 1; lovish on Oct
 * 29: "tombstones that spread creep and summon units passively", then "tone down"). A summoned, stationary unit raised
 * by Raise Tombstone: it stands {@link #LIFETIME_SECONDS} s or until killed, spreads grave creep round itself (radius
 * growing a block every {@link #CREEP_TICKS_PER_BLOCK} ticks up to {@link #CREEP_MAX_RADIUS}, fading over
 * {@link #CREEP_FADE_SECONDS} s once it falls) and raises a Risen Skeleton every {@link #RAISE_EVERY_SECONDS} s, at most
 * {@link #MAX_SKELETONS} standing. The rules live in essence/TombcallerServerEvents; this holds the stone's numbers
 * (every tombstone number is here, for lovish to tune in one place) and its saved clock.
 * <p>
 * A unit, not a building: it takes damage at the unit rate (lovish: 150 HP "at unit damage rate"), with none of the
 * totem's ranged and magic resist. It is a totem underneath for its placeholder look, with the totem's aura and range
 * circle switched off (the creep is its area). Its times are game ticks in its saved data, so a reload keeps them.
 */
public class TombstoneUnit extends AbstractTotem {

    // ------------------------------------------------------------- every tombstone number (lovish's taste call)
    public static final float HEALTH = 150f;
    public static final int LIFETIME_SECONDS = 90;
    public static final float CREEP_MAX_RADIUS = 8f;
    /** The creep grows one block every 2 s. */
    public static final int CREEP_TICKS_PER_BLOCK = 40;
    public static final int CREEP_FADE_SECONDS = 10;
    public static final int RAISE_EVERY_SECONDS = 20;
    public static final int SKELETON_LIFETIME_SECONDS = 30;
    public static final int MAX_SKELETONS = 1;
    /** Gravebound friends on the creep regenerate this much a second. */
    public static final float CREEP_REGEN_PER_SECOND = 2f;
    /** Enemy units on the creep move this much slower. */
    public static final float CREEP_ENEMY_SLOW = 0.10f;
    /** A wisp on its Tombcaller's creep flies to it from this far (8 elsewhere). */
    public static final double CREEP_PULL_RANGE = 12.0;

    static final String KEY_BORN = "bt_tomb_born", KEY_END = "bt_tomb_end", KEY_NEXT_RAISE = "bt_tomb_next_raise",
        KEY_CASTER = "bt_tomb_caster";

    public TombstoneUnit(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
        // the server events end it by its saved game time; the totem's own tick count (which a reload restarts) is a
        // late fallback only
        this.lifeTimeTicks = LIFETIME_SECONDS * 20 + 40;
        this.auraDurationTicks = Integer.MAX_VALUE;   // no totem aura or beacon hum
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractTotem.createAttributes()
            .add(Attributes.MAX_HEALTH, HEALTH)
            .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
            .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    /** No totem range circle: the creep shows the stone's area. */
    @Override
    public void updateHighlightBps(Level level) { }

    /** Starts the stone's clock at {@code now} for {@code caster} (before it joins). */
    public void begin(LivingEntity caster, long now) {
        getPersistentData().putLong(KEY_BORN, now);
        getPersistentData().putLong(KEY_END, now + LIFETIME_SECONDS * 20L);
        getPersistentData().putLong(KEY_NEXT_RAISE, now + RAISE_EVERY_SECONDS * 20L);
        if (caster != null)
            getPersistentData().putUUID(KEY_CASTER, caster.getUUID());
    }

    public long born() {
        return getPersistentData().getLong(KEY_BORN);
    }

    /** The game time it crumbles. */
    public long end() {
        return getPersistentData().getLong(KEY_END);
    }

    public long nextRaise() {
        return getPersistentData().getLong(KEY_NEXT_RAISE);
    }

    public void setNextRaise(long tick) {
        getPersistentData().putLong(KEY_NEXT_RAISE, tick);
    }

    @Nullable
    public UUID casterId() {
        return getPersistentData().hasUUID(KEY_CASTER) ? getPersistentData().getUUID(KEY_CASTER) : null;
    }

    /** Makes the stone {@code ticks} older (its birth, end and next raise all move back): game tests. */
    public void backdate(long ticks) {
        getPersistentData().putLong(KEY_BORN, born() - ticks);
        getPersistentData().putLong(KEY_END, end() - ticks);
        setNextRaise(nextRaise() - ticks);
    }

    /** Its creep's radius at game time {@code now}: a block every 2 s from its birth, at most 8. */
    public float creepRadius(long now) {
        return Math.max(0f, Math.min(CREEP_MAX_RADIUS, (now - born()) / (float) CREEP_TICKS_PER_BLOCK));
    }
}
