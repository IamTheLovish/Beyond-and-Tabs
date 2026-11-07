package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.brass.Heat;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.brass.BrassZeppelinUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Brassworks (Brass Zeppelin): <b>Ballast Dump</b> (second-t3-design.md). The Zeppelin drops its weight: for
 * {@link #DURATION_TICKS} ticks (5 s) it flies {@link #SPEED_BONUS} faster and cannot fire (no flak, no bombs), and it
 * vents steam as the Iron Leviathan Engine does - the Brassworks' {@link Heat} vent, so it also takes
 * {@link Heat#VENT_DAMAGE_TAKEN}x damage while it lasts. {@link #CD_SECONDS} s cooldown, no energy. The speed itself is
 * {@link BrassZeppelinUnit#tick}'s: it follows the vent, so a reload mid-dump keeps both.
 */
public class BallastDump extends Ability {

    public static final int CD_SECONDS = 40;
    public static final int DURATION_TICKS = 5 * 20;
    public static final double SPEED_BONUS = 0.4;

    public BallastDump() {
        super(UnitAction.BALLAST_DUMP, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, 0, false, true);
        this.autoRule(AutoRule.MANUAL);   // an escape: the player picks the moment
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Ballast Dump",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/iron_ingot.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.BALLAST_DUMP),
            null,
            List.of(
                FormattedCharSequence.forward("Ballast Dump  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Drops the ballast: +" + (int) (SPEED_BONUS * 100) + "% speed for " + DURATION_TICKS / 20
                    + " s, but no flak and no bombs, and the steam vent makes it take 25% more damage.", Style.EMPTY),
                FormattedCharSequence.forward("Run for it - or lose your air cover.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof BrassZeppelinUnit zep) || !(level instanceof ServerLevel sl))
            return;
        if (!dump(sl, zep))
            return;
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(zep.getId(), this.action, this.cooldownMax);
    }

    /** Starts the dump: the vent for {@link #DURATION_TICKS} (it also stops the guns) and the steam. False if it is venting already. Public for the game test. */
    public static boolean dump(ServerLevel sl, BrassZeppelinUnit zep) {
        if (Heat.isVenting(zep))
            return false;
        zep.setHeat(Heat.MAX);
        zep.setVentTicks(DURATION_TICKS);
        zep.applyBallastSpeed();
        sl.sendParticles(ParticleTypes.CLOUD, zep.getX(), zep.getY() + zep.getBbHeight() * 0.5, zep.getZ(), 24, 0.8, 0.4, 0.8, 0.08);
        sl.sendParticles(ParticleTypes.FALLING_DRIPSTONE_LAVA, zep.getX(), zep.getY(), zep.getZ(), 10, 0.8, 0.1, 0.8, 0.0);
        sl.playSound(null, zep.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 2f, 0.6f);
        sl.playSound(null, zep.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.NEUTRAL, 1f, 0.5f);
        return true;
    }
}
