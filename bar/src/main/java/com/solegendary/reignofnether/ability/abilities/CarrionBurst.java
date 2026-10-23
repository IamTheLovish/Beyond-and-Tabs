package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.DragonRaiseServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Gravebound (Plague Abomination): <b>Carrion Burst</b> (second-t3-design.md). The Abomination tears {@link #SACRIFICE}
 * of its maximum HP out of itself (never killing it) and up to {@link #COUNT} skeletons rise in a ring round it for 20 s.
 * They are the Bone Dragon's risen: free, no wreck, and capped by the same per-player limit
 * ({@link DragonRaiseServerEvents#MAX_RISEN}). The design's "from nearby corpses" is simplified to a ring round the
 * caster. {@link #CD_SECONDS} s cooldown.
 */
public class CarrionBurst extends Ability {

    public static final int CD_SECONDS = 40;
    public static final float SACRIFICE = 0.20f;
    public static final int COUNT = 6;
    public static final double RING = 3.5;

    public CarrionBurst() {
        super(UnitAction.CARRION_BURST, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, 0, false, true);
        this.autoRule(AutoRule.SELF_IN_COMBAT);
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Carrion Burst",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/bone.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.CARRION_BURST),
            null,
            List.of(
                FormattedCharSequence.forward("Carrion Burst  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Costs " + (int) (SACRIFICE * 100) + "% of its max HP. " + COUNT
                    + " skeletons rise round it for 20 s.", Style.EMPTY),
                FormattedCharSequence.forward("Shares the risen-skeleton limit with the Bone Dragon.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        burst(sl, self, unitUsing.getOwnerName());
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    /** The sacrifice and the rising; returns how many skeletons rose (fewer near the risen limit). Public for the game test. */
    public static int burst(ServerLevel sl, LivingEntity self, String owner) {
        float cost = self.getMaxHealth() * SACRIFICE;
        self.setHealth(Math.max(1f, self.getHealth() - cost));
        int raised = 0;
        for (int i = 0; i < COUNT; i++) {
            double a = i * 2 * Math.PI / COUNT;
            BlockPos at = BlockPos.containing(self.getX() + Math.cos(a) * RING, self.getY() + 1, self.getZ() + Math.sin(a) * RING);
            if (DragonRaiseServerEvents.raise(sl, owner, at) != null)
                raised++;
        }
        sl.sendParticles(ParticleTypes.SOUL, self.getX(), self.getY() + 1, self.getZ(), 30, RING * 0.5, 0.5, RING * 0.5, 0.05);
        sl.playSound(null, self.blockPosition(), SoundEvents.WITHER_SKELETON_AMBIENT, SoundSource.HOSTILE, 3f, 0.5f);
        return raised;
    }
}
