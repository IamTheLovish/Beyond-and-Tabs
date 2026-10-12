package com.solegendary.reignofnether.unit.units.engineers;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.TearDown;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.keybinds.Keybindings;
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
 * (it has to walk up to the enemy's walls); its trick is <b>Tear Down</b> - an enemy wall segment, gate or revealed
 * trap ripped down at once for half its metal.
 */
public class WreckerUnit extends GruntUnit implements CombatEngineer {

    public static final Abilities ABILITIES = new Abilities();
    static {
        ABILITIES.add(new TearDown(), Keybindings.hotkey1);   // V: Q is the Central Portal on the Horde build menu
    }

    final static public float maxHealth = 120.0f;
    final static public float movementSpeed = 0.29f;
    final static public float attackDamage = 2.0f;   // the engineers' tool swing (CombatEngineerUnit), not a Grunt's slap

    // GruntUnit's constructor fills this through updateAbilityButtons() before this class' field initializers would
    // run, so no initializer on purpose (one would wipe it again) - BonewrightUnit's rule
    private Abilities wreckerAbilities;

    public WreckerUnit(EntityType<? extends Piglin> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void updateAbilityButtons() {
        super.updateAbilityButtons();
        // a re-run (client cooldown sync) keeps the previous extras (commander-style runtime additions) and instances
        wreckerAbilities = Abilities.cloneKeepingExtras(ABILITIES, wreckerAbilities);
    }

    @Override
    public Abilities getAbilities() {
        if (wreckerAbilities == null)
            updateAbilityButtons();
        return wreckerAbilities;
    }

    @Override
    public Ability getEngineerAbility() {
        List<Ability> list = ABILITIES.get();
        return list.isEmpty() ? null : list.get(0);
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

    /** Its ability, then the field-defence build menu (never the Horde's whole menu: CombatEngineers.mayStart). */
    @Override
    public List<Button> getAbilityButtons() {
        List<Button> buttons = new ArrayList<>(getAbilities().getButtons(this));
        if (FMLEnvironment.dist == Dist.CLIENT)
            buttons.addAll(CombatEngineerButtons.forFaction(Factions.getFaction(this)));
        return buttons;
    }
}
