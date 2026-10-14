package com.solegendary.reignofnether.ability;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import static com.solegendary.reignofnether.unit.UnitClientEvents.sendUnitCommand;

public class Ability {
    public final UnitAction action; // null for worker building production items (handled specially in BuildingClientEvents)
    public float cooldownMax;
    public float range; // if <= 0, is melee
    public float radius; // if <= 0, is single target
    public final boolean canTargetEntities;
    public boolean oneClickOneUse; // if true, a group of units/buildings will use their abilities one by one
    public UnitAction autocastEnableAction = null;
    public UnitAction autocastDisableAction = null;
    public int maxCharges = 1;
    private boolean defaultAutocast = false;
    /**
     * When a unit casts this by itself ({@link AutoRule}; the developer's rule: every non-hero unit uses its abilities
     * on its own). Null until asked: then {@link #inferAutoRule()} from the shape, unless the constructor set one with
     * {@link #autoRule(AutoRule)}. Read by the AutoCaster (the cast) and AutocastState (the per-unit default).
     */
    private AutoRule autoRule = null;
    private boolean autoRuleExplicit = false;
    /**
     * The unit's own tick does the casting (RoN's potion / web / vex / fear goals, the Blade Dancer's Blink Strike, the
     * Kraken Caller's grab): the AutoCaster leaves it alone, the per-unit on/off bit still gates it. True by itself
     * for an ability with the legacy enable/disable actions.
     */
    public boolean autocastByUnit = false;
    /**
     * Energy the cast pays through the Mana core (0 = free). The AutoCaster only casts when the owner's pool holds this
     * plus the AutoCaster's energy reserve, so a line of casters never eats the commander's D-gun or spams refusals.
     */
    public float autocastEnergy = 0;
    public void setAutocast(boolean value, Unit unit) { AutocastState.set(unit, this, value); }
    public void setAutocast(boolean value, BuildingPlacement placement) { placement.setAutocast(value ? this : null); }
    public boolean isAutocasting(Unit unit) { return unit.hasAutocast(this); }
    public boolean isAutocasting(BuildingPlacement placement) { return placement.hasAutocast(this); }

    /** Sets the rule explicitly (constructor use); returns this. */
    public Ability autoRule(AutoRule rule) {
        this.autoRule = rule;
        this.autoRuleExplicit = rule != null;
        return this;
    }

    public AutoRule getAutoRule() {
        if (autoRule == null)
            autoRule = inferAutoRule();
        return autoRule;
    }

    /** Whether the author set the rule, or it was inferred from the shape (the lead's review list). */
    public boolean isAutoRuleExplicit() {
        return autoRuleExplicit;
    }

    /** Autocastable at all: a rule other than MANUAL. Hero abilities never are. */
    public boolean canAutocast() {
        return getAutoRule() != AutoRule.MANUAL;
    }

    /**
     * The rule from the shape alone, for abilities whose author set none:
     * <ul>
     *   <li>a {@link HeroAbility}, or no cooldown (toggles, mounts, trades, enchants, equips, worker orders) - MANUAL;</li>
     *   <li>a class named like a heal (Heal, Mend, Regen, Bloom) - ALLY_HURT; like a summon (Summon, Raise) - SUMMON_WHEN_FREE;</li>
     *   <li>targets an entity at range - ENEMY_IN_RANGE; aimed at the ground with a radius - CROWD; aimed at the ground
     *       without one (a lane, a skillshot) - ENEMY_IN_RANGE, cast at the enemy's feet;</li>
     *   <li>no range (self-centred, with or without a radius) - SELF_IN_COMBAT.</li>
     * </ul>
     */
    protected AutoRule inferAutoRule() {
        if (this instanceof HeroAbility || cooldownMax <= 0)
            return AutoRule.MANUAL;
        String name = getClass().getSimpleName();
        if (name.contains("Heal") || name.contains("Mend") || name.contains("Regen") || name.contains("Bloom"))
            return AutoRule.ALLY_HURT;
        if (name.contains("Summon") || name.contains("Raise"))
            return AutoRule.SUMMON_WHEN_FREE;
        if (range > 0) {
            if (canTargetEntities)
                return AutoRule.ENEMY_IN_RANGE;
            return radius > 0 ? AutoRule.CROWD : AutoRule.ENEMY_IN_RANGE;
        }
        return AutoRule.SELF_IN_COMBAT;
    }

    /** Summons of this ability alive for {@code unit} (SUMMON_WHEN_FREE casts while this is under {@link #summonCap()}). */
    public int liveSummons(net.minecraft.server.level.ServerLevel level, Unit unit) {
        return 0;
    }

    public int summonCap() {
        return 1;
    }

    /** One more gate for the AutoCaster, after the rule found a target: an ability's own refusal (a reserve, a stance). */
    public boolean canAutocastNow(net.minecraft.server.level.ServerLevel level, Unit unit) {
        return true;
    }

    /** Whether the AutoCaster may pick {@code target} (an enemy or ally the rule accepted) for this ability. */
    public boolean autocastTargetOk(Unit caster, LivingEntity target) {
        return true;
    }
    protected Keybinding defaultHotkey = Keybindings.abilitySlot1;

    public boolean showRangeLine = false;
    public boolean showRadiusCircle = false;
    public boolean showRangeCircle = true;

    public Ability(UnitAction action, int cooldownMax, float range, float radius, boolean canTargetEntities) {
        this.action = action;
        this.cooldownMax = cooldownMax;
        this.range = range;
        this.radius = radius;
        this.canTargetEntities = canTargetEntities;
        this.oneClickOneUse = false;
    }

    public Ability(UnitAction action, int cooldownMax, float range, float radius, boolean canTargetEntities, boolean oneClickOneUse) {
        this.action = action;
        this.cooldownMax = cooldownMax;
        this.range = range;
        this.radius = radius;
        this.canTargetEntities = canTargetEntities;
        this.oneClickOneUse = oneClickOneUse;
    }

    public boolean usesCharges() {
        return maxCharges > 1;
    }

    protected void toggleAutocast(Unit unit) {
        if (!((Entity) unit).level().isClientSide())
            return;

        if (isAutocasting(unit) && autocastDisableAction != null) {
            sendUnitCommand(autocastDisableAction);
        } else if (!isAutocasting(unit) && autocastEnableAction != null) {
            sendUnitCommand(autocastEnableAction);
        }
    }

    protected void toggleAutocast(BuildingPlacement placement) {
        if (!placement.level.isClientSide())
            return;

        if (isAutocasting(placement) && autocastDisableAction != null) {
            sendUnitCommand(autocastDisableAction);
        } else if (!isAutocasting(placement) && autocastEnableAction != null) {
            sendUnitCommand(autocastEnableAction);
        }
    }

    public boolean isCasting(Unit unit) { return false; }

    public float getCooldown(Unit unit) { return unit.getCooldown(this); }
    public float getCooldown(BuildingPlacement placement) { return placement.getCooldown(this); }

    public boolean isOffCooldown(Unit unit) { return getCooldown(unit) <= 0 || (usesCharges() && getCharges(unit) > 0); }
    public boolean isOffCooldown(BuildingPlacement placement) { return getCooldown(placement) <= 0 || (usesCharges() && getCharges(placement) > 0); }

    public void setToMaxCooldown(Unit unit) {
        unit.setCooldown(this, cooldownMax);
        if (usesCharges() && unit.getAbilityCharges(this) > 0)
            unit.setCharges(this, unit.getAbilityCharges(this) - 1);
    }

    public void setToMaxCooldown(BuildingPlacement building) {
        building.setCooldown(this, cooldownMax);
        if (usesCharges() && building.getCharges(this) > 0)
            building.setCharges(this, building.getCharges(this) - 1);
    }

    public void setCooldown(float cooldown, Unit unit) {
        this.setCooldown(cooldown, true, unit);
    }

    public void setCooldown(float cooldown, boolean useCharge, Unit unit) {
        if (((Entity) unit).level().isClientSide() && cooldown > 0) {
            HudClientEvents.setLowestCdHudEntity();
        }
        unit.setCooldown(this, Math.min(cooldown, cooldownMax));
        if (useCharge && usesCharges() && unit.getAbilityCharges(this) > 0)
            unit.setCharges(this, unit.getAbilityCharges(this) - 1);
    }

    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) { }

    public void use(Level level, BuildingPlacement buildingUsing, LivingEntity targetEntity) { }

    public void use(Level level, Unit unitUsing, BlockPos targetBp) { }

    public void use(Level level, BuildingPlacement buildingUsing, BlockPos targetBp) { }

    // assigns a default hotkey
    public AbilityButton getButton(BuildingPlacement placement) {
        return getButton(defaultHotkey, placement);
    }

    public AbilityButton getButton(Unit unit) {
        return getButton(defaultHotkey, unit);
    }

    public AbilityButton getButton(Keybinding hotkey, BuildingPlacement placement) {
        return null;
    }
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return null;
    }

    public boolean canBypassCooldown(Unit unit) { return usesCharges() && getCharges(unit) > 0; }
    public boolean canBypassCooldown(BuildingPlacement buildingPlacement) { return usesCharges() && getCharges(buildingPlacement) > 0; }

    public boolean shouldResetBehaviours() { return true; }

    protected void setDefaultAutocast(boolean b) {
        defaultAutocast = b;
    }

    public boolean isDefaultAutocast() {
        return defaultAutocast;
    }

    public int getCharges(Unit unit) {
        return unit.getAbilityCharges(this);
    }

    public int getCharges(BuildingPlacement placement) {
        return placement.getCharges(this);
    }

    public void setCharges(Unit unit, int charges) {
        unit.setCharges(this, charges);
    }
}
