package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.player.CommanderAntiAir;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The commander's anti-air shot on its card (player/CommanderAntiAir; lovish Oct 21 03:23: players should see it as part
 * of the commander). Passive, like Kite: the commander's own weapon runs it; this entry shows what it is - named after the
 * faction's way of doing it (Sunbolt, Soul Lash, Throwing Axe, Thorn Burst, Harpoon, Sky Spark, Flak Pistol) - and is
 * the switch: click to turn it off or on (AutocastState). One shared instance for every commander.
 */
public class SkywardShotAbility extends Ability {

    public static final SkywardShotAbility INSTANCE = new SkywardShotAbility();

    private SkywardShotAbility() {
        super(UnitAction.SKYWARD_SHOT, 0, 0, 0, false, false);
        this.autoRule(AutoRule.SELF_IN_COMBAT);   // "can autocast" = the switch exists; CommanderAntiAir runs it
        this.autocastByUnit = true;
    }

    /** Whether this unit carries the shot (every commander, from CommanderServerEvents.ensureAbility). */
    public static boolean has(Unit unit) {
        if (unit.getAbilities() == null)
            return false;
        for (var a : unit.getAbilities().get())
            if (a == INSTANCE)
                return true;
        return false;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        CommanderAntiAir.Style style = CommanderAntiAir.styleOf(Factions.getFaction(unit));
        String key = "ability.reignofnether.skyward_shot." + style.name();
        float dps = CommanderAntiAir.DAMAGE * 20f / CommanderAntiAir.COOLDOWN_TICKS;
        return new AbilityButton(Component.translatable(key).getString(),
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/" + style.icon() + ".png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> this.toggleAutocast(unit),
            null,
            List.of(
                FormattedCharSequence.forward(Component.translatable(key).getString() + "  ("
                    + Component.translatable("ability.reignofnether.skyward_shot.passive").getString() + ")", Style.EMPTY.withBold(true)),
                Component.translatable("ability.reignofnether.skyward_shot.tooltip1", Math.round(dps), CommanderAntiAir.RANGE)
                    .getVisualOrderText(),
                Component.translatable("ability.reignofnether.skyward_shot.tooltip2").getVisualOrderText(),
                Component.translatable("ability.reignofnether.skyward_shot.tooltip3").withStyle(Style.EMPTY.withItalic(true))
                    .getVisualOrderText()
            ),
            this,
            unit);
    }
}
