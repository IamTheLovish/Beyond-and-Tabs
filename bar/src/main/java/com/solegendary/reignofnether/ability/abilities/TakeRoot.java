package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.verdant.SentinelTreantUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Verdant Rooted Treant: <b>Take Root</b> (vehicles-design 2026-10-28, section 3.4), a toggle like the Shellback's
 * {@link Moor}. The Sentinel Treant plants its roots ({@link SentinelTreantUnit#ROOT_TIMES}) and, rooted, bursts lines of
 * root spikes along the ground instead of walking up to slam; uprooting takes time too. No cooldown: the root time is
 * the price. The server owns the state (the order only flips it); the button lights while rooted or rooting.
 */
public class TakeRoot extends Ability {

    public static final TakeRoot INSTANCE = new TakeRoot();

    public TakeRoot() {
        super(UnitAction.TAKE_ROOT, 0, 0, 0, false, false);
        this.autoRule(AutoRule.MANUAL);   // autocast: a toggle that roots the Treant
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        SentinelTreantUnit treant = unit instanceof SentinelTreantUnit t ? t : null;
        return new AbilityButton("Take Root",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/hanging_roots.png"),
            hotkey,
            () -> treant != null && treant.isRootWantedClient(),
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.TAKE_ROOT),
            null,
            List.of(
                FormattedCharSequence.forward("Take Root / Uproot", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Roots in " + SentinelTreantUnit.ROOT_TIMES.rootTicks() / 20f + " s: can't move, takes "
                    + Math.round((1 - SentinelTreantUnit.ROOTED_TAKEN_MULT) * 100) + "% less damage and bursts a line of root spikes every "
                    + SentinelTreantUnit.LINE_EVERY / 20 + " s (" + (int) SentinelTreantUnit.LINE_DAMAGE + " to every enemy on it, "
                    + (int) SentinelTreantUnit.LINE_LENGTH + " blocks).", Style.EMPTY),
                FormattedCharSequence.forward("Uprooting takes " + SentinelTreantUnit.ROOT_TIMES.uprootTicks() / 20f + " s.", Style.EMPTY)
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (!level.isClientSide() && unitUsing instanceof SentinelTreantUnit treant)
            treant.toggleRoot();
    }
}
