package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.tide.FogBankServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Tidewrought (Abyssal Hydra): <b>Brine Mist</b> (second-t3-design.md). A sea-fog cloud {@link #DIAMETER} blocks across
 * rolls out round the Hydra for {@link #LIFETIME_TICKS} ticks (6 s). It is the Admiral's Fog Bank on a smaller, shorter
 * scale (FogBankServerEvents): friendly units inside are hidden from enemies who are not right beside them or who
 * attack, and enemy ranged shots at units inside miss {@link FogBankServerEvents#MIST_MISS_CHANCE} of the time. The
 * design says "enemy ranged accuracy -50%"; this is the nearest honest version - it spoils projectile shots at the
 * cloud's units, not direct-hit rays and spells. {@link #CD_SECONDS} s cooldown, no energy. Not telegraphed (it hurts
 * nobody): the cloud itself is the signal.
 */
public class BrineMist extends Ability {

    public static final int CD_SECONDS = 25;
    public static final int RADIUS = 4;
    public static final int DIAMETER = RADIUS * 2;
    public static final int LIFETIME_TICKS = 6 * 20;

    public BrineMist() {
        super(UnitAction.BRINE_MIST, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, RADIUS, false, true);
        this.autoRule(AutoRule.SELF_IN_COMBAT);
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Brine Mist",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/nautilus_shell.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.BRINE_MIST),
            null,
            List.of(
                FormattedCharSequence.forward("Brine Mist  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("A sea-fog cloud " + DIAMETER + " blocks across for " + LIFETIME_TICKS / 20
                    + " s. Friendly units inside are hidden unless an enemy is right beside them or they attack, and "
                    + "enemy shots at them miss half the time.", Style.EMPTY),
                FormattedCharSequence.forward("The Hydra rolls the sea in with it.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof Mob self) || !(level instanceof ServerLevel sl))
            return;
        if (cast(sl, unitUsing) == null)
            return;
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    /** Rolls the mist out round {@code hydra} (one per Hydra: a recast moves it). Null for an ownerless caster. Public for the game test. */
    public static FogBankServerEvents.Bank cast(ServerLevel sl, Unit hydra) {
        String owner = hydra.getOwnerName();
        if (owner == null || owner.isBlank() || !(hydra instanceof Mob self))
            return null;
        FogBankServerEvents.Bank b = FogBankServerEvents.raise(sl, owner, self.getId(),
            self.getX(), self.getY(), self.getZ(), RADIUS, LIFETIME_TICKS, true);
        sl.playSound(null, self.blockPosition(), SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundSource.NEUTRAL, 3f, 0.7f);
        // cover is decided on the cover pass (every 5 ticks); run one now so the friends vanish as the mist rolls in
        com.solegendary.reignofnether.blocks.ThicketCover.refresh(sl.getServer());
        return b;
    }
}
