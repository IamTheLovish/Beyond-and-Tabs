package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.hero.HeroFx;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
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
 * Verdant (Thornwing Roc): <b>Gale Wings</b> (second-t3-design.md). A wing-beat cone {@link #LENGTH} blocks long and
 * {@link #HALF_ANGLE_DEG} degrees either side of the way the Roc faces (its target, else its heading): every enemy unit
 * inside is knocked back about {@link #GROUND_PUSH_BLOCKS} blocks if it walks, and flung farther if it flies. No damage,
 * no stun - it opens a flank. {@link #CD_SECONDS} s cooldown, no energy. The push is an impulse (RoN's own
 * {@link HeroFx#knockback}), so a unit with knockback resistance moves less.
 */
public class GaleWings extends Ability {

    public static final int CD_SECONDS = 20;
    public static final float LENGTH = 9f;
    public static final double HALF_ANGLE_DEG = 40;
    /** Impulse strengths tuned so a walker slides about 4 blocks on flat ground; a flyer has no ground friction and goes further. */
    public static final double GROUND_STRENGTH = 1.25;
    public static final double AIR_STRENGTH = 0.9;
    public static final double GROUND_PUSH_BLOCKS = 4;

    public GaleWings() {
        super(UnitAction.GALE_WINGS, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, LENGTH, false, true);
        this.autoRule(AutoRule.MANUAL);   // a repositioning tool: the player picks the moment
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Gale Wings",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/feather.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.GALE_WINGS),
            null,
            List.of(
                FormattedCharSequence.forward("Gale Wings  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("A wing-beat cone " + (int) LENGTH + " blocks long in front of the Roc: enemies in it are "
                    + "knocked back about " + (int) GROUND_PUSH_BLOCKS + " blocks, flyers are pushed away. No damage.", Style.EMPTY),
                FormattedCharSequence.forward("Opens a flank, or breaks a melee rush.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof ThornwingRocUnit roc) || !(level instanceof ServerLevel sl))
            return;
        beat(sl, roc);
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(roc.getId(), this.action, this.cooldownMax);
    }

    /** True if {@code p} lies inside the cone from {@code origin} along the horizontal unit vector {@code dir}. */
    public static boolean inCone(Vec3 origin, Vec3 dir, Vec3 p) {
        double dx = p.x - origin.x, dz = p.z - origin.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > LENGTH)
            return false;
        if (dist < 0.5)
            return true;
        return (dx * dir.x + dz * dir.z) / dist >= Math.cos(Math.toRadians(HALF_ANGLE_DEG));
    }

    /** The beat: pushes every enemy in the cone. Returns how many were caught. Public for the game test. */
    public static int beat(ServerLevel sl, Mob self) {
        Vec3 origin = self.position();
        Vec3 dir = self instanceof ThornwingRocUnit r ? r.facing() : Vec3.directionFromRotation(0, self.getYRot());
        int hit = 0;
        for (LivingEntity le : HeroFx.enemies(sl, self, origin, LENGTH + 1)) {
            if (!inCone(origin, dir, le.position()))
                continue;
            boolean flyer = le instanceof Unit u && u.isFlyingUnit();
            // push along the beat's direction (not radially): the whole cone is blown the same way
            Vec3 from = le.position().subtract(dir.x * 2, 0, dir.z * 2);
            HeroFx.knockback(from, le, flyer ? AIR_STRENGTH : GROUND_STRENGTH);
            hit++;
        }
        for (int i = 1; i <= 6; i++) {
            double d = LENGTH * i / 6.0;
            sl.sendParticles(ParticleTypes.CLOUD, origin.x + dir.x * d, origin.y + 0.4, origin.z + dir.z * d, 6,
                d * Math.tan(Math.toRadians(HALF_ANGLE_DEG)) * 0.5, 0.3, d * Math.tan(Math.toRadians(HALF_ANGLE_DEG)) * 0.5, 0.1);
        }
        sl.playSound(null, self.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.NEUTRAL, 3f, 0.5f);
        sl.playSound(null, self.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.NEUTRAL, 2f, 0.8f);
        return hit;
    }
}
