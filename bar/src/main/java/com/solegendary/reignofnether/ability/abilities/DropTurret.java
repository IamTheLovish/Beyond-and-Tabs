package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.brass.DeployedTurretUnit;
import com.solegendary.reignofnether.unit.units.brass.SteamHaulerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Steam Hauler: <b>Drop Turret</b> (design/big_units.md 1.7, "Field Foundry"). Aim a spot within
 * {@link SteamHaulerUnit#TURRET_RANGE} blocks: the hauler's crane sets down the Guildmaster's Deployed Turret there - free
 * (the hauler carries the parts), but it adds {@link SteamHaulerUnit#TURRET_HEAT} heat and is refused while venting.
 * {@link #CD_SECONDS} s cooldown; the turret lives its usual {@link DeployedTurretUnit#LIFETIME_TICKS} ticks.
 */
public class DropTurret extends Ability {

    public static final int CD_SECONDS = 25;

    public DropTurret() {
        super(UnitAction.DROP_TURRET, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, SteamHaulerUnit.TURRET_RANGE, 0, false, true);
        this.autoRule(AutoRule.MANUAL);   // autocast: a placed turret: a chosen spot, heat
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Drop Turret",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/crossbow_standby.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.DROP_TURRET,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.DROP_TURRET),
            null,
            List.of(
                FormattedCharSequence.forward("Drop Turret  (" + CD_SECONDS + " s cooldown, +" + SteamHaulerUnit.TURRET_HEAT + " heat)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Sets an auto-turret within " + (int) SteamHaulerUnit.TURRET_RANGE + " blocks for "
                    + DeployedTurretUnit.LIFETIME_TICKS / 20 + " s, free. Not while venting.", Style.EMPTY)
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        use(level, unitUsing, targetEntity.getOnPos());
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (!(level instanceof ServerLevel sl) || !(unitUsing instanceof SteamHaulerUnit hauler))
            return;
        if (hauler.dropTurret(sl, targetBp) != null) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(hauler.getId(), this.action, this.cooldownMax);
        }
    }
}
