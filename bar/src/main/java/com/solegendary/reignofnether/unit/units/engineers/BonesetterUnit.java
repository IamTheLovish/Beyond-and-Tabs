package com.solegendary.reignofnether.unit.units.engineers;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.abilities.BoneGraft;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Gravebound combat engineer - the <b>Bonesetter</b> (design/combat_engineers.md): a grey-skinned undead surgeon of the
 * crypts in a bone-plated apron with a bone saw, trained at the Dungeon. The cheapest engineer (the Gravebound win with
 * numbers, and their x2 reclaim trait makes it the best reclaimer in the game); its trick is <b>Bone Graft</b> - a
 * wreck's metal torn out to mend a friendly unit. Undead like the rest of its side, so the sun slows it as it slows the
 * army it follows.
 */
public class BonesetterUnit extends CombatEngineerUnit {

    public static final Abilities ABILITIES = new Abilities();
    static {
        ABILITIES.add(new BoneGraft(), Keybindings.hotkey1);   // V: free on the Gravebound build menu
    }

    final static public float maxHealth = 90.0f;
    final static public float movementSpeed = 0.30f;

    public BonesetterUnit(EntityType<? extends Vindicator> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected Abilities engineerAbilities() {
        return ABILITIES;
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.BONESETTER; }

    public static AttributeSupplier.Builder createAttributes() {
        return CombatEngineerUnit.createAttributes()
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.MOVEMENT_SPEED, movementSpeed);
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEAD;
    }

    // the Gravebound night army: daylight slows it (the big T2 bodies get the lighter slowness, as here)
    @Override
    public SunlightEffect getSunlightEffect() {
        return SunlightEffect.SLOWNESS_I;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_VILLAGER_AMBIENT;
    }
    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_VILLAGER_DEATH;
    }
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ZOMBIE_VILLAGER_HURT;
    }
}
