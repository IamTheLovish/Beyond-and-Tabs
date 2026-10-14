package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.verdant.MossbackBisonUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Mossback Bison: <b>Embark</b> (design/big_units.md 1.4, "Hollow Grove"). Loads every eligible friendly infantry
 * unit within {@link MossbackBisonUnit#EMBARK_RANGE} blocks into the grove on its back, up to its free seats
 * ({@link MossbackBisonUnit#SEATS}). The unit's Eject button unloads them all. No cooldown: loading is a movement
 * order, its cost is the time the riders spend walking to the bison.
 */
public class Embark extends Ability {

    public Embark() {
        super(UnitAction.EMBARK, 0, 0, 0, false, false);
        this.autoRule(AutoRule.MANUAL);   // autocast: a transport order
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        LivingEntity entity = (LivingEntity) unit;
        return new AbilityButton("Embark",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/moss_block.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> entity.getPassengers().size() < MossbackBisonUnit.SEATS,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.EMBARK),
            null,
            List.of(
                FormattedCharSequence.forward("Embark", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Loads up to " + MossbackBisonUnit.SEATS + " friendly infantry within "
                    + (int) MossbackBisonUnit.EMBARK_RANGE + " blocks into the grove: they take half damage, heal "
                    + (int) MossbackBisonUnit.RIDER_HEAL_PER_SECOND + " HP/s and archers keep shooting.", Style.EMPTY),
                FormattedCharSequence.forward("Eject drops them all.", Style.EMPTY)
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (!level.isClientSide() && unitUsing instanceof MossbackBisonUnit bison)
            bison.embarkNearby();
    }
}
