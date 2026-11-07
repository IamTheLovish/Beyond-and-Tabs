package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.hero.HeroFx;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Sunforged (Cathedral Warden): <b>Bell Toll</b> (second-t3-design.md). The Warden bell winds up for
 * {@link #TELEGRAPH_TICKS} ticks (1 s: a ring of {@link #RADIUS} blocks shows where it will ring), then tolls:
 * every enemy unit inside is slowed for {@link #SLOW_TICKS} ticks (5 s). The ring is fixed at cast time. The design
 * asks a 40% slow; this is Slowness III (45%), the nearest the effect system offers. {@link #CD_SECONDS} s cooldown, no energy.
 */
public class BellToll extends Ability {

    public static final int CD_SECONDS = 30;
    public static final float RADIUS = 12f;
    public static final int TELEGRAPH_TICKS = 20;
    public static final int SLOW_TICKS = 5 * 20;
    /** Slowness III: 45% (the design asks 40%). */
    public static final int SLOW_AMPLIFIER = 2;

    public BellToll() {
        super(UnitAction.BELL_TOLL, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, RADIUS, false, true);
        this.autoRule(AutoRule.SELF_IN_COMBAT);
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Bell Toll",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/bell.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.BELL_TOLL),
            null,
            List.of(
                FormattedCharSequence.forward("Bell Toll  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Marks a " + (int) RADIUS + "-block ring round the Warden. After 1 s the bell tolls: "
                    + "enemy units in it are slowed for " + SLOW_TICKS / 20 + " s.", Style.EMPTY),
                FormattedCharSequence.forward("The ring shows first: step out of it.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof Mob self) || !(level instanceof ServerLevel sl))
            return;
        Vec3 centre = self.position();
        telegraph(sl, centre);
        sl.playSound(null, self.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 2f, 0.6f);
        TaskSchedulerServerEvents.schedule(TELEGRAPH_TICKS / 2, () -> telegraph(sl, centre));
        TaskSchedulerServerEvents.schedule(TELEGRAPH_TICKS, () -> {
            if (self.isAlive())   // killed mid-windup: the bell never tolls
                toll(sl, self, centre);
        });
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    static void telegraph(ServerLevel sl, Vec3 centre) {
        int points = (int) Math.ceil(2 * Math.PI * RADIUS);
        for (int i = 0; i < points; i++) {
            double a = i * 2 * Math.PI / points;
            sl.sendParticles(ParticleTypes.CRIT, centre.x + Math.cos(a) * RADIUS, centre.y + 0.2,
                centre.z + Math.sin(a) * RADIUS, 1, 0.1, 0.05, 0.1, 0.02);
        }
    }

    /** The toll: slows every enemy unit within {@link #RADIUS} of {@code centre}. Returns how many were caught. Public for the game test. */
    public static int toll(ServerLevel sl, Mob self, Vec3 centre) {
        int hit = 0;
        for (LivingEntity le : HeroFx.enemies(sl, self, centre, RADIUS)) {
            if (Math.abs(le.getY() + le.getBbHeight() / 2 - centre.y) > 6)
                continue;
            HeroFx.slow(le, SLOW_TICKS, SLOW_AMPLIFIER);
            hit++;
        }
        HeroFx.ring(sl, ParticleTypes.NOTE, centre, RADIUS);
        sl.playSound(null, BlockPos.containing(centre), SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 3f, 0.7f);
        return hit;
    }
}
