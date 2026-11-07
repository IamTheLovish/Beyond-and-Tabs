package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.hero.HeroFx;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.UnitGrid;
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
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Stormcallers (Jade Sentinel): <b>Thunderclap</b> (second-t3-design.md). The Sentinel raises a fist: a ring of
 * {@link #RADIUS} blocks round where it stands crackles for {@link #TELEGRAPH_TICKS} ticks (1 s - the danger shows
 * first), then the ring cracks: every enemy unit inside takes {@link #DAMAGE} and is stunned for {@link #STUN_TICKS}
 * ticks (1 s, under the design's 1.5 s cap). The ring is fixed at cast time, so a quick unit steps out.
 * {@link #CD_SECONDS} s cooldown, no energy.
 */
public class Thunderclap extends Ability {

    public static final int CD_SECONDS = 30;
    public static final float RADIUS = 6f;
    public static final float DAMAGE = 12f;
    public static final int STUN_TICKS = 20;
    public static final int TELEGRAPH_TICKS = 20;

    public Thunderclap() {
        super(UnitAction.THUNDERCLAP, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, RADIUS, false, true);
        this.autoRule(AutoRule.SELF_IN_COMBAT);
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Thunderclap",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/lightning_rod.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.THUNDERCLAP),
            null,
            List.of(
                FormattedCharSequence.forward("Thunderclap  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Marks a " + (int) RADIUS + "-block ring round the Sentinel. After 1 s it cracks: "
                    + (int) DAMAGE + " damage and a " + STUN_TICKS / 20 + " s stun to enemy units in it.", Style.EMPTY),
                FormattedCharSequence.forward("The ring shows first: step out of it.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        Vec3 centre = self.position();
        String owner = unitUsing.getOwnerName();
        telegraph(sl, centre);
        sl.playSound(null, self.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.5f, 1.6f);
        TaskSchedulerServerEvents.schedule(TELEGRAPH_TICKS / 2, () -> telegraph(sl, centre));
        TaskSchedulerServerEvents.schedule(TELEGRAPH_TICKS, () -> {
            if (self.isAlive())   // killed mid-cast: the clap never lands
                clap(sl, self, owner, centre);
        });
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    static void telegraph(ServerLevel sl, Vec3 centre) {
        int points = (int) Math.ceil(2 * Math.PI * RADIUS);
        for (int i = 0; i < points; i++) {
            double a = i * 2 * Math.PI / points;
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, centre.x + Math.cos(a) * RADIUS, centre.y + 0.2,
                centre.z + Math.sin(a) * RADIUS, 1, 0.1, 0.05, 0.1, 0.02);
        }
    }

    /**
     * The clap: {@link #DAMAGE} and a {@link #STUN_TICKS} stun to every hostile unit within {@link #RADIUS} of
     * {@code centre}. Returns how many were caught. Public for the game test.
     */
    public static int clap(ServerLevel sl, LivingEntity self, String owner, Vec3 centre) {
        int hit = 0;
        for (LivingEntity le : UnitGrid.near(sl, centre.x, centre.z, RADIUS + 1, new ArrayList<>())) {
            if (le == self || !le.isAlive() || !(le instanceof Unit u) || le.level() != sl)
                continue;
            String other = u.getOwnerName();
            if (owner.equals(other) || AlliancesServerEvents.isAllied(owner, other))
                continue;
            double dx = le.getX() - centre.x, dz = le.getZ() - centre.z;
            if (dx * dx + dz * dz > RADIUS * RADIUS || Math.abs(le.getY() + le.getBbHeight() / 2 - centre.y) > 4)
                continue;
            le.hurt(sl.damageSources().indirectMagic(self, self), DAMAGE);   // not mobAttack: RoN rewrites that to melee damage
            if (le.isAlive())
                HeroFx.stun(le, STUN_TICKS);
            hit++;
        }
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, centre.x, centre.y + 0.5, centre.z, 40, RADIUS * 0.5, 0.4, RADIUS * 0.5, 0.2);
        sl.sendParticles(ParticleTypes.EXPLOSION, centre.x, centre.y + 0.3, centre.z, 3, RADIUS * 0.4, 0.1, RADIUS * 0.4, 0);
        sl.playSound(null, BlockPos.containing(centre), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 2f, 1.2f);
        return hit;
    }
}
