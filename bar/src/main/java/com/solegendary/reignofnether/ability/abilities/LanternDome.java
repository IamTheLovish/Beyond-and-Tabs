package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.mana.LanternDomeServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.storm.LanternFortressUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Predicate;

import static com.solegendary.reignofnether.unit.UnitClientEvents.sendUnitCommand;

/**
 * Stormcallers Lantern Fortress: <b>Lantern Dome</b> (design/stormcallers_plan.md, slice 4) - a toggle, the Brute's
 * Shield Stance pattern. On: the Fortress carries a dome of lantern light {@link LanternDomeServerEvents#RADIUS} blocks
 * round it that soaks up to {@link LanternDomeServerEvents#CAPACITY} projectile damage to friends inside, channelled at
 * {@link LanternDomeServerEvents#ENERGY_PER_SECOND} energy a second (the Fortress's tick raises and re-raises it;
 * starved or spent, it collapses and returns after {@link LanternDomeServerEvents#COLLAPSE_DELAY_TICKS}). Off: the dome
 * comes down and the drain stops. No cooldown of its own: the collapse delay is the only one.
 */
public class LanternDome extends Ability {

    public LanternDome() {
        super(UnitAction.LANTERN_DOME, 0, 0, LanternDomeServerEvents.RADIUS, false);
        this.showRadiusCircle = true;
    }

    // one click flips every selected Fortress to the state the HUD's one does NOT show (a mixed selection comes together)
    private static final Predicate<LivingEntity> TOGGLE_CHECK = le ->
            HudClientEvents.hudSelectedEntity instanceof LanternFortressUnit hudUnit &&
            le instanceof LanternFortressUnit unit &&
            hudUnit.isDomeWanted() == unit.isDomeWanted();

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        if (!(unit instanceof LanternFortressUnit fortress))
            return null;
        return new AbilityButton("Lantern Dome",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/lantern.png"),
            hotkey,
            fortress::isDomeWanted,
            () -> false,
            () -> true,
            () -> sendUnitCommand(UnitAction.LANTERN_DOME, TOGGLE_CHECK),
            null,
            List.of(
                FormattedCharSequence.forward("Lantern Dome  (toggle, " + (int) LanternDomeServerEvents.ENERGY_PER_SECOND + " energy/s)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("A dome of lantern light " + (int) LanternDomeServerEvents.RADIUS + " blocks round the Fortress soaks up to "
                    + (int) LanternDomeServerEvents.CAPACITY + " projectile damage to friends inside.", Style.EMPTY),
                FormattedCharSequence.forward("Drains energy every second; starved or spent it collapses and rises again after "
                    + LanternDomeServerEvents.COLLAPSE_DELAY_TICKS / 20 + " s.", Style.EMPTY),
                FormattedCharSequence.forward("Melee and spells pass through it.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (!(unitUsing instanceof LanternFortressUnit fortress))
            return;
        fortress.setDomeWanted(!fortress.isDomeWanted());
        fortress.updateAbilityButtons();
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        use(level, unitUsing, (BlockPos) null);
    }

    @Override
    public boolean shouldResetBehaviours() { return false; }
}
