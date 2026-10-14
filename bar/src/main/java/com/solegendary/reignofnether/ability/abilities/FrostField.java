package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.mana.FrostServerEvents;
import com.solegendary.reignofnether.mana.ManaServerEvents;
import com.solegendary.reignofnether.resources.EconomyServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MyMath;

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
 * Stormcallers Frost Adept: <b>Frost Field</b> (design/stormcallers_plan.md, slice 2 - "Freeze Ground"). Aim a spot
 * within {@link #RANGE} blocks: the ground freezes into a temporary frost sheet of radius {@link #RADIUS} for
 * {@link #LIFETIME_TICKS} ticks (x1.5 overcharged) that slows enemy units standing on it (mana/FrostServerEvents - caps,
 * cleanup and the slow live there). {@link #ENERGY_COST} energy through the Mana core, {@link #CD_SECONDS} s cooldown.
 * Refused (no open ground, no energy): nothing paid, cooldown untouched.
 */
public class FrostField extends Ability {

    public static final int CD_SECONDS = 20;
    public static final int RANGE = 10;
    public static final int RADIUS = 2;
    public static final int LIFETIME_TICKS = 15 * 20;
    public static final int ENERGY_COST = 50;

    public FrostField() {
        super(UnitAction.FROST_FIELD, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, RADIUS, false, true);
        this.autoRule(AutoRule.CROWD);   // autocast: a slowing sheet: saved for a clump, paid from the pool
        this.autocastEnergy = ENERGY_COST;
        this.showRangeCircle = true;
        this.showRadiusCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Frost Field",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/packed_ice.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.FROST_FIELD,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.FROST_FIELD),
            null,
            List.of(
                FormattedCharSequence.forward("Frost Field  (" + ENERGY_COST + " energy, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Freezes the ground " + RADIUS + " blocks round a spot up to " + RANGE
                    + " away for " + LIFETIME_TICKS / 20 + " s.", Style.EMPTY),
                FormattedCharSequence.forward("Enemies on the ice are slowed. Fire melts it.", Style.EMPTY),
                FormattedCharSequence.forward("The choke was open a moment ago.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (targetEntity != null)
            use(level, unitUsing, targetEntity.getOnPos());
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl) || targetBp == null)
            return;
        BlockPos at = MyMath.getXZRangeLimitedBlockPos(self.blockPosition(), targetBp, RANGE);
        at = new BlockPos(at.getX(), targetBp.getY(), at.getZ());
        if (cast(sl, self, unitUsing.getOwnerName(), at, false) != null) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /**
     * Freezes the ground round {@code ground} (the clicked ground block): checks there is open ground first, then pays,
     * then lays the field (refunded in the rare case the level's cell budget refuses it). Returns the field or null.
     * Public for the bot and the game test (no cooldown handling here).
     */
    public static FrostServerEvents.Field cast(ServerLevel sl, LivingEntity adept, String owner, BlockPos ground, boolean quiet) {
        if (owner == null || owner.isBlank())
            return null;
        // the sheet sits in the first air block above the floor; aim one up so a click on the ground itself counts
        BlockPos centre = ground.above();
        if (FrostServerEvents.collectCells(sl, centre, RADIUS).isEmpty()) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, FrostServerEvents.REFUSE_NO_GROUND);
            return null;
        }
        float power = quiet ? ManaServerEvents.tryCastQuiet(adept, owner, ENERGY_COST) : ManaServerEvents.tryCast(adept, owner, ENERGY_COST);
        if (power <= 0)
            return null;
        FrostServerEvents.Field f = FrostServerEvents.raise(sl, owner, centre, RADIUS, Math.round(LIFETIME_TICKS * power));
        if (f == null) {
            EconomyServerEvents.refund(owner, 0, ENERGY_COST * ManaServerEvents.spellCostMultiplier(owner));
            return null;
        }
        sl.playSound(null, centre, SoundEvents.GLASS_PLACE, SoundSource.NEUTRAL, 2f, 0.6f);
        sl.playSound(null, adept.blockPosition(), SoundEvents.POWDER_SNOW_PLACE, SoundSource.NEUTRAL, 2f, 0.8f);
        sl.sendParticles(ParticleTypes.SNOWFLAKE, adept.getX(), adept.getY() + 1.6, adept.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
        return f;
    }
}
