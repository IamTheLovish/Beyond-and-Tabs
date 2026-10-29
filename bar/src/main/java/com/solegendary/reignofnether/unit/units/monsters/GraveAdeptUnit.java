package com.solegendary.reignofnether.unit.units.monsters;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.essence.EssenceCarrier;
import com.solegendary.reignofnether.resources.ResourceCost;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The Tombcaller's <b>Grave Adept</b> (placeholder name, so it is not taken for the Necromancer hero;
 * design/essence_casters.md "Gravebound Gravecaller" and Revision 1; lovish: "they can summon a necromancer that can
 * use exp to grow in strength"). Summoned by Call the Necromancer, one per Tombcaller, it stays until killed.
 * <p>
 * A plain unit, not a hero (a summoned hero that dies pays enemy heroes a decisive-kill burst): it holds its own XP.
 * The deaths of enemy units within {@link #XP_RANGE} feed it what they would pay a hero; Empower feeds it
 * {@link #EMPOWER_XP}. It levels at {@link #LEVEL_XP}: each level +{@link #HP_PER_LEVEL} health and
 * +{@link #DAMAGE_PER_LEVEL} damage and one more skeleton per raise. From level 1 it raises skeletons every
 * {@link #RAISE_EVERY_SECONDS} s while it stands on its owner's grave creep; from level {@link #BOLT_LEVEL} it casts a
 * soul bolt; at level {@link #ESSENCE_RAISE_LEVEL} it raises off the creep too, paying {@link #ESSENCE_PER_SKELETON}
 * essence of its own per skeleton (it is a carrier: Revision 1's "raises from essence, not corpses"). When it dies,
 * its XP bursts out as orbs to the killer's heroes. The rules live in essence/GraveAdeptServerEvents; every Adept number
 * is here (placeholders for lovish where the draft named none).
 * <p>
 * A Wraith underneath for its placeholder look, with no abilities of its own.
 */
public class GraveAdeptUnit extends WraithUnit implements EssenceCarrier {

    // ------------------------------------------------------------------- every Grave Adept number
    /** Revision 1: levels 1-5 at these XP totals (hero XP units: a pop-2 enemy death within reach is worth 70). */
    public static final int[] LEVEL_XP = { 120, 300, 540, 840, 1200 };
    public static final int MAX_LEVEL = 5;
    public static final double HP_PER_LEVEL = 0.15;
    public static final double DAMAGE_PER_LEVEL = 0.10;
    /** Placeholder: a sturdier body than the Tombcaller's 40, it is the fight's prize. */
    final static public float maxHealth = 60.0f;
    public static final int XP_RANGE = 16;
    /** Revision 1: Empower on the Adept gives this much XP, not a level. */
    public static final int EMPOWER_XP = 120;
    public static final int RAISE_EVERY_SECONDS = 15;
    /** Placeholder: as long as a tombstone's Risen Skeleton. */
    public static final int SKELETON_LIFETIME_SECONDS = 30;
    public static final int ESSENCE_RAISE_LEVEL = 5;
    public static final int ESSENCE_PER_SKELETON = 2;
    public static final int BOLT_LEVEL = 3;
    /** Placeholders: a short-range bolt on the T1-health scale (Revision 1: Brine Bolt is 25). */
    public static final float BOLT_DAMAGE = 15f;
    public static final int BOLT_RANGE = 6;
    public static final int BOLT_EVERY_SECONDS = 3;

    static final String KEY_XP = "bt_adept_xp", KEY_CASTER = "bt_adept_caster", KEY_NEXT_RAISE = "bt_adept_next_raise",
        KEY_NEXT_BOLT = "bt_adept_next_bolt";
    private static final ResourceCost FREE = ResourceCost.Unit(0, 0, 0, 0, 0);

    private Abilities noAbilities = new Abilities();
    @Nullable
    private UUID casterId = null;

    public GraveAdeptUnit(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void updateAbilityButtons() {
        noAbilities = new Abilities();
    }

    @Override
    public Abilities getAbilities() {
        return noAbilities;
    }

    /** A summon: free and no population (Call the Necromancer pays in essence). */
    @Override
    public ResourceCost getCost() {
        return FREE;
    }

    public static AttributeSupplier.Builder createAttributes() {
        // none of the Wraith's 50% evasion: the Adept is the prize of the fight, its health is what protects it
        return WraithUnit.createAttributes()
            .add(Attributes.MAX_HEALTH, GraveAdeptUnit.maxHealth)
            .add(com.solegendary.reignofnether.registrars.AttributeRegistrar.EVASION_CHANCE.get(), 0);
    }

    public int xp() {
        return getPersistentData().getInt(KEY_XP);
    }

    /** Only GraveAdeptServerEvents.addXp moves it (the level's health and damage follow there). */
    public void setXpRaw(int xp) {
        getPersistentData().putInt(KEY_XP, Math.max(0, xp));
    }

    /** The level {@code xp} reaches: 0 below the first threshold, at most {@link #MAX_LEVEL}. */
    public static int levelFor(int xp) {
        int level = 0;
        for (int t : LEVEL_XP)
            if (xp >= t)
                level++;
        return Math.min(level, MAX_LEVEL);
    }

    public int level() {
        return levelFor(xp());
    }

    public void setCaster(LivingEntity caster) {
        getPersistentData().putUUID(KEY_CASTER, caster.getUUID());
        casterId = caster.getUUID();
    }

    @Nullable
    public UUID casterId() {
        if (casterId == null && getPersistentData().hasUUID(KEY_CASTER))
            casterId = getPersistentData().getUUID(KEY_CASTER);
        return casterId;
    }

    public long nextRaise() {
        return getPersistentData().getLong(KEY_NEXT_RAISE);
    }

    public void setNextRaise(long tick) {
        getPersistentData().putLong(KEY_NEXT_RAISE, tick);
    }

    public long nextBolt() {
        return getPersistentData().getLong(KEY_NEXT_BOLT);
    }

    public void setNextBolt(long tick) {
        getPersistentData().putLong(KEY_NEXT_BOLT, tick);
    }
}
