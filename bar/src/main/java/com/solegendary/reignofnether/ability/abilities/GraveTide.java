package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.essence.TombcallerServerEvents;
import com.solegendary.reignofnether.hero.HeroFx;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.sounds.SoundAction;
import com.solegendary.reignofnether.sounds.SoundClientboundPacket;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Tombcaller: <b>Grave Tide</b> (design/essence_casters.md "Gravebound Gravecaller" and Revision 1; lovish: essence
 * "buffs to make their units regenerate and move quickly"). Every Gravebound unit of its owner or an ally within
 * {@link #RADIUS} blocks of the Tombcaller, or anywhere on its grave creep, runs {@link #SPEED_BONUS} faster and
 * regenerates {@link #REGEN_PER_SECOND} HP a second for {@link #DURATION_SECONDS} s ({@link #CREEP_DURATION_SECONDS} s
 * for those on the creep) - to rush in, or to fall back and mend. The Tombcaller itself counts. Revision 1 set the
 * regen at the Bloom Priestess's scale (2 HP/s, not 4). {@link #ESSENCE_COST} essence, {@link #CD_SECONDS} s; refused -
 * nothing spent, no cooldown - when short of essence. Autocast while fighting.
 */
public class GraveTide extends Ability {

    public static final int ESSENCE_COST = 5;
    public static final int CD_SECONDS = 12;
    public static final int RADIUS = 8;
    public static final float SPEED_BONUS = 0.30f;
    public static final float REGEN_PER_SECOND = 2f;
    public static final int DURATION_SECONDS = 8;
    public static final int CREEP_DURATION_SECONDS = 12;

    public GraveTide() {
        super(UnitAction.GRAVE_TIDE, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, RADIUS, false, false);
        this.autoRule(AutoRule.SELF_IN_COMBAT);
        this.autocastEssence = ESSENCE_COST;   // the AutoCaster's essence gate (essence base Revision 1)
        this.showRangeCircle = false;
        this.showRadiusCircle = true;
    }

    /** The autocast waits for the essence: no order, so no "not enough essence" every second while it fights. */
    @Override
    public boolean canAutocastNow(ServerLevel level, Unit unit) {
        return super.canAutocastNow(level, unit) && unit instanceof LivingEntity le && Essence.get(le) >= ESSENCE_COST;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Grave Tide",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/soul_fire_0.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.GRAVE_TIDE),
            () -> toggleAutocast(unit),
            List.of(
                FormattedCharSequence.forward("Grave Tide  (" + ESSENCE_COST + " essence, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Gravebound units within " + RADIUS + " blocks, or on this Tombcaller's grave creep, move "
                    + Math.round(SPEED_BONUS * 100) + "% faster and regenerate " + (int) REGEN_PER_SECOND + " HP/s for "
                    + DURATION_SECONDS + " s (" + CREEP_DURATION_SECONDS + " s on the creep).", Style.EMPTY),
                FormattedCharSequence.forward("The dead do not tire.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        useNow(level, unitUsing);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        useNow(level, unitUsing);
    }

    private void useNow(Level level, Unit unitUsing) {
        if (level.isClientSide() || !(level instanceof ServerLevel sl) || !(unitUsing instanceof LivingEntity self) || !isOffCooldown(unitUsing))
            return;
        if (!cast(sl, self, unitUsing.getOwnerName(), false).isEmpty()) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /**
     * The rule: spends the essence, then hastens every Gravebound friend in reach (the caster always is). Returns the
     * units it reached (empty: refused). Public for the bot and the game test (no cooldown handling here).
     */
    public static List<LivingEntity> cast(ServerLevel sl, LivingEntity caster, String owner, boolean quiet) {
        if (owner == null || owner.isEmpty() || !(caster instanceof Mob self))
            return List.of();
        // nothing else can refuse it: the Tombcaller itself is always within reach
        if (!Essence.trySpend(caster, ESSENCE_COST)) {
            if (!quiet)
                TombcallerServerEvents.warn(sl, owner, Empower.MSG_SHORT);
            return List.of();
        }
        List<LivingEntity> reached = new ArrayList<>();
        for (LivingEntity le : HeroFx.friends(sl, self, caster.position(), RADIUS))
            if (!reached.contains(le))
                reached.add(le);
        for (LivingEntity le : TombcallerServerEvents.unitsOnCreepOf(caster))
            if (!reached.contains(le))
                reached.add(le);
        if (!reached.contains(caster))
            reached.add(caster);
        long now = sl.getGameTime();
        List<LivingEntity> hastened = new ArrayList<>();
        for (LivingEntity le : reached) {
            if (!TombcallerServerEvents.isGraveboundFriend(le, owner))
                continue;
            int seconds = TombcallerServerEvents.onCreepOf(caster, le.position()) ? CREEP_DURATION_SECONDS : DURATION_SECONDS;
            TombcallerServerEvents.hasten(le, now + seconds * 20L);
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, le.getX(), le.getY() + 0.3, le.getZ(), 6, 0.3, 0.1, 0.3, 0.02);
            hastened.add(le);
        }
        for (int k = 0; k < 24; k++) {
            double a = Math.PI * 2 * k / 24;
            sl.sendParticles(ParticleTypes.SOUL, caster.getX() + Math.cos(a) * RADIUS, caster.getY() + 0.2, caster.getZ() + Math.sin(a) * RADIUS,
                1, 0, 0.05, 0, 0.01);
        }
        SoundClientboundPacket.playSoundAtPos(SoundAction.WRAITH_FEAR, caster.blockPosition());
        return hastened;
    }
}
