package com.solegendary.reignofnether.unit.units.engineers;

import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.CombatEngineerButtons;
import com.solegendary.reignofnether.unit.CombatEngineers;
import com.solegendary.reignofnether.unit.interfaces.CombatEngineer;
import com.solegendary.reignofnether.unit.units.piglins.GruntUnit;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Ironhide Horde combat engineer - the <b>Wrecker</b> (design/combat_engineers.md): a piglin in a riveted rust apron
 * with a scrap pack and a crowbar, trained at the Flame Sanctuary. On the Grunt's body (the Bonewright's pattern: a
 * GruntUnit subclass keeps the piglin plumbing - magma walking, no zombification, the portrait framing) with the engineer
 * rules of {@link CombatEngineers} on top, since the six illager engineers' base class can't be its parent. Sturdy
 * (it has to walk up to the enemy's walls); no ability of its own (removed 2026-10-26).
 */
public class WreckerUnit extends GruntUnit implements CombatEngineer {

    final static public float maxHealth = 120.0f;
    final static public float movementSpeed = 0.29f;
    final static public float attackDamage = 2.0f;   // the engineers' tool swing (CombatEngineerUnit), not a Grunt's slap

    public WreckerUnit(EntityType<? extends Piglin> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.WRECKER; }

    /** {@link CombatEngineers#BUILD_POWER}: never a commander (commanders are the starting workers). */
    @Override
    public float getBuildPower() {
        return CombatEngineers.BUILD_POWER;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return GruntUnit.createAttributes()
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide())
            CombatEngineers.tickFieldRepair(this);
    }

    /** The field-defence build menu (never the Horde's whole menu: CombatEngineers.mayStart). */
    @Override
    public List<Button> getAbilityButtons() {
        List<Button> buttons = new ArrayList<>(getAbilities().getButtons(this));
        if (FMLEnvironment.dist == Dist.CLIENT)
            buttons.addAll(CombatEngineerButtons.forFaction(Factions.getFaction(this)));
        return buttons;
    }
}
