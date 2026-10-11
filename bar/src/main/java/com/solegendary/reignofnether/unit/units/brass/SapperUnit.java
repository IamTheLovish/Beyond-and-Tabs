package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.ability.abilities.PlantCharge;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Brassworks T1 demolition - the <b>Sapper</b> (design/brassworks_plan.md, slice 1): a dwarf in a soot apron with a
 * satchel of charges on the illager body (its own skin, SapperRenderer). A weak melee fighter whose worth is
 * <b>Plant Charge</b> ({@link PlantCharge}): a timed charge on an enemy building. Extends VindicatorUnit for the melee
 * brain and the attack animation sync, as the Leafblade and the Blade Dancer do.
 */
public class SapperUnit extends VindicatorUnit {

    final static public float attackDamage = 3.0f;
    final static public float attacksPerSecond = 0.6f;
    final static public float maxHealth = 34.0f;   // a worker's build, not a fighter's
    final static public float movementSpeed = 0.29f;
    final static public float attackRange = 2;
    final static public float aggroRange = 6;
    final static public float rangedDamageResist = 0.0f;

    public SapperUnit(EntityType<? extends Vindicator> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.SAPPER; }

    // one Plant Charge per Sapper, kept across refreshes so its cooldown (keyed by the ability) survives them. No
    // initializer on purpose: the first refresh runs inside VindicatorUnit's constructor, before field initializers
    private PlantCharge plantCharge;

    /** The Sapper's Plant Charge (null only during construction). Public for the game test and the bot. */
    public PlantCharge getPlantCharge() { return plantCharge; }

    /** VindicatorUnit rebuilds its abilities from its shared static set here; add this unit's Plant Charge on top. */
    @Override
    public void updateAbilityButtons() {
        super.updateAbilityButtons();
        if (getAbilities() == null)
            return;
        if (plantCharge == null)
            plantCharge = new PlantCharge();
        if (!getAbilities().get().contains(plantCharge))
            getAbilities().add(plantCharge, com.solegendary.reignofnether.keybinds.Keybindings.abilitySlot1);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.ARMOR, 0)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), rangedDamageResist)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    // a dwarf of the guild, not an illager: healing heals and raid/bad-omen logic leaves it alone
    @Override
    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    // a mattock (an iron pickaxe); visual only - damage is RoN's attack attribute, and cosmeticWeapon pins the item's
    // own damage to +0 so it can never leak into the melee hit
    @Override
    public void setupEquipmentAndUpgradesServer() {
        if (hasAnyEnchant())
            return;
        this.setItemSlot(EquipmentSlot.MAINHAND, com.solegendary.reignofnether.unit.interfaces.AttackerUnit.cosmeticWeapon(Items.IRON_PICKAXE));
        this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    }
}
