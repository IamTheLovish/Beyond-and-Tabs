package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.verdant.WorldTreeWalkerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * World Tree Walker: <b>Grow Grove</b> (claude/t3-roles.md: the Summoner's second army). The walker drives its roots
 * down and {@link WorldTreeWalkerUnit#TREANTS_PER_GROVE} Sentinel Treants grow out of the ground round it, fighting
 * for its owner for {@link WorldTreeWalkerUnit#TREANT_LIFETIME_TICKS} ticks before they sink back; at most
 * {@link WorldTreeWalkerUnit#TREANT_CAP} stand at once, and the grove withers with the walker. The bloom heals the
 * friends in its shade. {@link #CD_SECONDS} s cooldown; autocast while fighting and under the cap. The treants are
 * the Elder Druid's awakened kind (AwakenThicket): free, no wreck, counted toward population while they stand.
 */
public class GrowGrove extends Ability {

    public static final int CD_SECONDS = 30;

    public GrowGrove() {
        super(UnitAction.GROW_GROVE, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, WorldTreeWalkerUnit.GROVE_RADIUS, false, false);
        this.autoRule(AutoRule.SUMMON_WHEN_FREE);   // autocast: a second army while it fights, up to the cap
        this.showRadiusCircle = true;
        this.showRangeCircle = false;
    }

    @Override
    public int liveSummons(ServerLevel level, Unit unit) {
        return unit instanceof WorldTreeWalkerUnit w ? w.liveTreants() : 0;
    }

    @Override
    public int summonCap() {
        return WorldTreeWalkerUnit.TREANT_CAP;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Grow Grove",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/azalea_top.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.GROW_GROVE),
            null,
            List.of(
                FormattedCharSequence.forward("Grow Grove  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward(WorldTreeWalkerUnit.TREANTS_PER_GROVE + " treants grow out of the ground round the walker and fight for "
                    + WorldTreeWalkerUnit.TREANT_LIFETIME_TICKS / 20 + " s (at most " + WorldTreeWalkerUnit.TREANT_CAP
                    + " at once); the bloom heals friends in its shade by " + (int) WorldTreeWalkerUnit.BLOOM_HEAL + ".", Style.EMPTY),
                FormattedCharSequence.forward("The grove withers with the walker: kill the tree, not the trees.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof WorldTreeWalkerUnit walker) || !(level instanceof ServerLevel sl))
            return;
        if (walker.growGrove(sl) <= 0)
            return;   // nothing grew (the cap, no owner): not a cast, no cooldown
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(walker.getId(), this.action, this.cooldownMax);
    }
}
