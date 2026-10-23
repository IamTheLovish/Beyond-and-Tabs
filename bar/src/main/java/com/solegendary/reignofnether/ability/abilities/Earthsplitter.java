package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.piglins.WorldbreakerUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

/**
 * Ironhide Horde (Worldbreaker): <b>Earthsplitter</b> (hero-and-tier4-design.md, slice H5a). Aim at the ground: a
 * {@link #LENGTH}-block line toward the spot is drawn in red for {@link #TELEGRAPH_TICKS} ticks (0.6 s, the danger shows
 * before it lands), then the ground cracks along it: every enemy ground unit on the line takes {@link #DAMAGE} and is
 * slowed for {@link #SLOW_TICKS} ticks (1 s), and every enemy wall or building on the line is dented for
 * {@link #BUILDING_DAMAGE}. The line is fixed at cast time, so a quick unit steps off it. Never touches a flyer.
 * Un-hosted, the AutoCaster casts it at an enemy when at least {@link #AUTO_MIN_ON_LINE} enemies stand on the line.
 */
public class Earthsplitter extends Ability {

    public static final int CD_SECONDS = 14;
    public static final float LENGTH = 10f;
    public static final double HALF_WIDTH = 1.5;
    /** 0.6 s of warning. */
    public static final int TELEGRAPH_TICKS = 12;
    public static final float DAMAGE = 70f;
    /** 1 s slow (Slowness III). */
    public static final int SLOW_TICKS = 20;
    public static final int SLOW_AMPLIFIER = 2;
    public static final float BUILDING_DAMAGE = 120f;
    /** The AutoCaster's rule: enemies on the line before it casts by itself. */
    public static final int AUTO_MIN_ON_LINE = 3;

    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1.0f, 0.1f, 0.1f), 1.4f);

    public Earthsplitter() {
        super(UnitAction.EARTHSPLITTER, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, LENGTH, 0, true, false);
        this.autoRule(AutoRule.ENEMY_IN_RANGE);   // gated by autocastTargetOk: 3 or more enemies on the line
        this.showRangeLine = true;
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Earthsplitter",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/netherite_axe.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.EARTHSPLITTER,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.EARTHSPLITTER),
            null,
            List.of(
                FormattedCharSequence.forward("Earthsplitter  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Cracks a " + (int) LENGTH + "-block line toward the spot after 0.6 s: "
                    + (int) DAMAGE + " damage and a 1 s slow to enemies on it, walls and buildings too. Flyers are not touched.", Style.EMPTY),
                FormattedCharSequence.forward("The red line shows first: step off it.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (targetEntity != null)
            cast(level, unitUsing, targetEntity.position());
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (targetBp != null)
            cast(level, unitUsing, Vec3.atBottomCenterOf(targetBp));
    }

    /** The unit vector from {@code self} toward {@code at}, flat; the way it faces if the spot is on top of it. */
    public static Vec3 lineDir(LivingEntity self, Vec3 at) {
        Vec3 flat = new Vec3(at.x - self.getX(), 0, at.z - self.getZ());
        if (flat.lengthSqr() < 0.25)
            return Vec3.directionFromRotation(0, self.getYRot()).multiply(1, 0, 1).normalize();
        return flat.normalize();
    }

    void cast(Level level, Unit unitUsing, Vec3 at) {
        if (level.isClientSide() || !(unitUsing instanceof WorldbreakerUnit wb) || !(level instanceof ServerLevel sl) || !wb.isAlive())
            return;
        Vec3 from = wb.position();
        Vec3 dir = lineDir(wb, at);
        String owner = wb.getOwnerName();
        telegraph(sl, from, dir);
        sl.playSound(null, wb.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 3f, 0.5f);
        TaskSchedulerServerEvents.schedule(TELEGRAPH_TICKS / 2, () -> telegraph(sl, from, dir));
        TaskSchedulerServerEvents.schedule(TELEGRAPH_TICKS, () -> {
            if (wb.isAlive())   // killed mid-cast: the ground never cracks
                crack(sl, wb, owner, from, dir);
        });
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(wb.getId(), this.action, this.cooldownMax);
    }

    /** Whether {@code le} stands on the line from {@code from} along {@code dir}. */
    public static boolean onLine(LivingEntity le, Vec3 from, Vec3 dir) {
        double rx = le.getX() - from.x, rz = le.getZ() - from.z;
        double along = rx * dir.x + rz * dir.z;
        double perp = Math.abs(rx * dir.z - rz * dir.x);
        return along >= 0 && along <= LENGTH && perp <= HALF_WIDTH + le.getBbWidth() / 2;
    }

    /** Enemy ground units on the line (the autocast's count and the crack's targets). */
    public static List<LivingEntity> unitsOnLine(ServerLevel sl, String owner, Vec3 from, Vec3 dir) {
        List<LivingEntity> out = WorldbreakerUnit.groundFoes(sl, owner, from.x + dir.x * LENGTH / 2, from.z + dir.z * LENGTH / 2,
            LENGTH / 2 + HALF_WIDTH + 1);
        out.removeIf(le -> !onLine(le, from, dir) || Math.abs(le.getY() - from.y) > 5);
        return out;
    }

    /** The AutoCaster's gate: at least {@link #AUTO_MIN_ON_LINE} enemies on the line toward the chosen target. */
    @Override
    public boolean autocastTargetOk(Unit caster, LivingEntity target) {
        if (!(caster instanceof LivingEntity self) || !(self.level() instanceof ServerLevel sl) || !WorldbreakerUnit.isGroundTarget(target))
            return false;
        return unitsOnLine(sl, caster.getOwnerName(), self.position(), lineDir(self, target.position())).size() >= AUTO_MIN_ON_LINE;
    }

    static void telegraph(ServerLevel sl, Vec3 from, Vec3 dir) {
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        int steps = (int) (LENGTH * 2);
        for (int i = 0; i <= steps; i++) {
            double d = LENGTH * i / steps;
            double x = from.x + dir.x * d, z = from.z + dir.z * d;
            double y = from.y + 0.2;
            for (int s = -1; s <= 1; s += 2)
                sl.sendParticles(RED, x + side.x * HALF_WIDTH * s, y, z + side.z * HALF_WIDTH * s, 1, 0.03, 0.02, 0.03, 0.0);
        }
    }

    /**
     * The crack: {@link #DAMAGE} and a {@link #SLOW_TICKS}-tick slow to every enemy ground unit on the line, a dent to
     * every enemy building on it. Returns how many units were hit. Public for the game test.
     */
    public static int crack(ServerLevel sl, LivingEntity self, String owner, Vec3 from, Vec3 dir) {
        int hit = 0;
        for (LivingEntity le : unitsOnLine(sl, owner, from, dir)) {
            // indirect magic, not mobAttack: RoN rewrites mob-attack damage to the attacker's melee damage
            le.hurt(sl.damageSources().indirectMagic(self, self), DAMAGE);
            if (le.isAlive())
                le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, SLOW_AMPLIFIER));
            hit++;
        }
        Vec3 end = new Vec3(from.x + dir.x * LENGTH, from.y, from.z + dir.z * LENGTH);
        for (BuildingPlacement bp : WorldbreakerUnit.foeBuildingsNear(sl, owner, from, end, HALF_WIDTH))
            bp.destroyRandomBlocks(BUILDING_DAMAGE, self);
        for (int i = 0; i <= LENGTH; i += 2) {
            double x = from.x + dir.x * i, z = from.z + dir.z * i;
            sl.sendParticles(ParticleTypes.EXPLOSION, x, from.y + 0.3, z, 1, 0.3, 0.1, 0.3, 0);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, x, from.y + 0.3, z, 3, 0.4, 0.1, 0.4, 0.02);
        }
        com.solegendary.reignofnether.barfx.BarFx.heavyImpact(sl, new Vec3(from.x + dir.x * LENGTH / 2, from.y, from.z + dir.z * LENGTH / 2), 4f);
        sl.playSound(null, self.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.8f, 0.5f);
        return hit;
    }
}
