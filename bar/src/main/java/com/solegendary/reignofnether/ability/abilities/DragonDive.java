package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.ability.ExperimentalAbilityText;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.BoneDragonUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Bone Dragon: <b>Dive</b> (#145, lovish: "an option to let it descend to the ground to fight as well as keep flying
 * ... like in BAR ... a cooldown on this that's long to punish players that dive in and prevents people from abusing
 * the dive mechanic"). The dragon flies by default. Aimed at the ground: it stoops onto the spot (up to
 * {@link BoneDragonUnit#DIVE_RANGE} blocks away) and the impact hits every ground enemy within
 * {@link BoneDragonUnit#IMPACT_RADIUS} for {@link BoneDragonUnit#IMPACT_DAMAGE}; it stays down
 * {@link BoneDragonUnit#LAND_TICKS} ticks as a ground unit - it breathes on what is round it, and melee can reach it -
 * then flies again. On the ground the same button takes it off early, once it has been down
 * {@link BoneDragonUnit#MIN_LAND_TICKS} ticks (the toggle ignores the cooldown). The cooldown, {@link #CD_SECONDS} s,
 * starts at the dive. The dive itself lives on the dragon (its tick drives it); this is the button and the order.
 * Manual: a dive into an army is the player's call.
 */
public class DragonDive extends Ability {

    public static final int CD_SECONDS = BoneDragonUnit.DIVE_CD_SECONDS;

    public DragonDive() {
        super(UnitAction.DRAGON_DIVE, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, BoneDragonUnit.DIVE_RANGE,
            BoneDragonUnit.IMPACT_RADIUS, false, true);
        this.autoRule(AutoRule.MANUAL);
        this.showRangeCircle = true;
        this.showRadiusCircle = true;
    }

    /** Tooltip body. Public for the tooltip game test. */
    public String description() {
        return "Dives onto the spot: the impact deals " + (int) BoneDragonUnit.IMPACT_DAMAGE + " to ground enemies within "
            + (int) BoneDragonUnit.IMPACT_RADIUS + " blocks. It fights on the ground for " + BoneDragonUnit.LAND_TICKS / 20
            + " s, where melee can reach it, then flies again.";
    }

    /** Tooltip range line, from the ability's own range. Public for the tooltip game test. */
    public String rangeLine() {
        return ExperimentalAbilityText.rangeLine(range);
    }

    private static boolean landed(Unit unit) {
        return unit instanceof BoneDragonUnit d && d.isLanded();
    }

    /** On the ground, after its minimum time there, the button is the take-off - whatever the cooldown says. */
    @Override
    public boolean canBypassCooldown(Unit unit) {
        return unit instanceof BoneDragonUnit d && d.canTakeOff();
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Dive",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/phantom_membrane.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.DRAGON_DIVE,
            () -> false,
            () -> !landed(unit) || canBypassCooldown(unit),
            () -> {
                if (landed(unit))
                    UnitClientEvents.sendUnitCommand(UnitAction.DRAGON_DIVE);   // on the ground: take off
                else
                    CursorClientEvents.setLeftClickAction(UnitAction.DRAGON_DIVE);
            },
            null,
            List.of(
                FormattedCharSequence.forward("Dive  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward(rangeLine(), Style.EMPTY),
                FormattedCharSequence.forward(description(), Style.EMPTY),
                FormattedCharSequence.forward("On the ground, press again to take off (after " + BoneDragonUnit.MIN_LAND_TICKS / 20 + " s).", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (targetEntity != null)
            order(level, unitUsing, targetEntity.position());
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        order(level, unitUsing, targetBp == null ? null : Vec3.atBottomCenterOf(targetBp.above()));
    }

    void order(Level level, Unit unitUsing, Vec3 at) {
        if (level.isClientSide() || !(unitUsing instanceof BoneDragonUnit dragon))
            return;
        if (dragon.isLanded()) {
            dragon.takeOffEarly();      // the toggle: refused (stays down) before MIN_LAND_TICKS
            return;
        }
        if (at == null || !isOffCooldown(unitUsing) || !dragon.startDive(at))
            return;   // diving already, or nowhere to dive: not a cast, no cooldown
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(dragon.getId(), this.action, this.cooldownMax);
    }
}
