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
import com.solegendary.reignofnether.unit.units.verdant.ThornwingRocUnit;

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
 * Verdant (Thornwing Roc): <b>Seed Rain</b> (second-t3-design.md). The Roc scatters seeds along a {@link #LENGTH}-block
 * line in front of it (its target's way, else its heading) and the line is marked with particles for
 * {@link #TELEGRAPH_TICKS} ticks (1.5 s). Then thickets burst up: every enemy unit that WALKS on the line is rooted
 * {@link #ROOT_TICKS} ticks (1 s) and slowed {@link #SLOW_TICKS} ticks (3 s). Flyers are above the thickets and are not
 * touched. The line is fixed at cast time. {@link #CD_SECONDS} s cooldown, no energy.
 * <p>
 * Honest version: the root and slow are RoN's effects ({@link HeroFx#root} = Slowness VII, then Slowness III), not
 * placed thicket blocks - the line shows leaf and spore particles instead.
 */
public class SeedRain extends Ability {

    public static final int CD_SECONDS = 35;
    public static final float LENGTH = 14f;
    public static final double HALF_WIDTH = 1.5;
    public static final int TELEGRAPH_TICKS = 30;
    public static final int ROOT_TICKS = 20;
    public static final int SLOW_TICKS = 60;
    public static final int SLOW_AMPLIFIER = 2;   // Slowness III

    public SeedRain() {
        super(UnitAction.SEED_RAIN, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, LENGTH, false, true);
        this.autoRule(AutoRule.MANUAL);   // aimed along the Roc's facing: the player picks the moment
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Seed Rain",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/wheat_seeds.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.SEED_RAIN),
            null,
            List.of(
                FormattedCharSequence.forward("Seed Rain  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Scatters seeds along a " + (int) LENGTH + "-block line in front of the Roc. After 1.5 s "
                    + "thickets burst up: enemies walking on the line are rooted for " + ROOT_TICKS / 20 + " s and slowed for "
                    + SLOW_TICKS / 20 + " s. Flyers are not touched.", Style.EMPTY),
                FormattedCharSequence.forward("The line shows first: step off it.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof ThornwingRocUnit roc) || !(level instanceof ServerLevel sl))
            return;
        Vec3 from = roc.position();
        Vec3 dir = roc.facing();
        Vec3 to = from.add(dir.x * LENGTH, 0, dir.z * LENGTH);
        double groundY = roc.groundY();
        telegraph(sl, from, to, groundY);
        sl.playSound(null, roc.blockPosition(), SoundEvents.AZALEA_LEAVES_BREAK, SoundSource.NEUTRAL, 3f, 0.6f);
        TaskSchedulerServerEvents.schedule(TELEGRAPH_TICKS / 2, () -> telegraph(sl, from, to, groundY));
        TaskSchedulerServerEvents.schedule(TELEGRAPH_TICKS, () -> {
            if (roc.isAlive())   // killed mid-cast: the seeds never sprout
                sprout(sl, roc, from, to, groundY);
        });
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(roc.getId(), this.action, this.cooldownMax);
    }

    static void telegraph(ServerLevel sl, Vec3 from, Vec3 to, double groundY) {
        Vec3 d = to.subtract(from);
        double len = Math.sqrt(d.x * d.x + d.z * d.z);
        Vec3 dir = new Vec3(d.x / len, 0, d.z / len);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        int steps = (int) (len * 2);
        for (int i = 0; i <= steps; i++) {
            double t = len * i / steps;
            for (int s = -1; s <= 1; s += 2) {
                sl.sendParticles(ParticleTypes.CRIT, from.x + dir.x * t + side.x * HALF_WIDTH * s, groundY + 0.2,
                    from.z + dir.z * t + side.z * HALF_WIDTH * s, 1, 0.05, 0.02, 0.05, 0.0);
            }
            if (i % 3 == 0)
                sl.sendParticles(ParticleTypes.FALLING_SPORE_BLOSSOM, from.x + dir.x * t, groundY + 3, from.z + dir.z * t, 2, 0.8, 0.5, 0.8, 0.0);
        }
    }

    /** True if the walker at {@code p} stands on the fixed line from -> to (horizontal, {@link #HALF_WIDTH} plus its half width). */
    public static boolean onLine(Vec3 from, Vec3 to, Vec3 p, double bodyHalfWidth) {
        double dx = to.x - from.x, dz = to.z - from.z;
        double len2 = Math.max(1e-6, dx * dx + dz * dz);
        double t = Math.max(0, Math.min(1, ((p.x - from.x) * dx + (p.z - from.z) * dz) / len2));
        double cx = from.x + dx * t, cz = from.z + dz * t;
        double w = HALF_WIDTH + bodyHalfWidth;
        return (p.x - cx) * (p.x - cx) + (p.z - cz) * (p.z - cz) <= w * w;
    }

    /** The thickets: roots and slows every enemy WALKER on the line. Returns how many were caught. Public for the game test. */
    public static int sprout(ServerLevel sl, Mob self, Vec3 from, Vec3 to, double groundY) {
        int hit = 0;
        Vec3 mid = from.add(to).scale(0.5);
        for (LivingEntity le : HeroFx.enemies(sl, self, mid, LENGTH / 2 + HALF_WIDTH + 2)) {
            if (le instanceof Unit u && u.isFlyingUnit())
                continue;
            if (Math.abs(le.getY() - groundY) > 4 || !onLine(from, to, le.position(), le.getBbWidth() / 2))
                continue;
            HeroFx.slow(le, SLOW_TICKS, SLOW_AMPLIFIER);
            HeroFx.root(le, ROOT_TICKS);
            sl.sendParticles(ParticleTypes.COMPOSTER, le.getX(), le.getY() + 0.5, le.getZ(), 12, 0.4, 0.5, 0.4, 0.05);
            hit++;
        }
        Vec3 d = to.subtract(from);
        double len = Math.sqrt(d.x * d.x + d.z * d.z);
        for (int i = 0; i <= (int) len; i++)
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, from.x + d.x / len * i, groundY + 0.5, from.z + d.z / len * i, 3, HALF_WIDTH * 0.6, 0.4, HALF_WIDTH * 0.6, 0.0);
        sl.playSound(null, BlockPos.containing(mid), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.NEUTRAL, 3f, 0.7f);
        return hit;
    }
}
