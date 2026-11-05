package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.piglins.WorldbreakerUnit;
import com.solegendary.reignofnether.unit.units.tide.BombardShell;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Ironhide Horde (Worldbreaker): <b>Hurl</b> (hero-and-tier4-design.md, slice H5a). Aim at the ground within
 * {@link #RANGE} blocks: the Worldbreaker lobs a molten boulder (the Magma Hurler's {@link BombardShell}, ground only)
 * that bursts for {@link #DAMAGE} over {@link #SPLASH_RADIUS} blocks and sets every enemy it hits alight, then leaves the
 * ground burning for {@link #BURN_GROUND_TICKS} ticks (5 s): enemy ground units standing in it are re-ignited every
 * half second. Un-hosted, the AutoCaster lobs it at the densest enemy group in range (at least 3 inside the splash).
 */
public class WorldbreakerHurl extends Ability {

    public static final int CD_SECONDS = 12;
    public static final float RANGE = 18f;
    public static final double SPLASH_RADIUS = 3.0;
    public static final float DAMAGE = 70f;
    /** How long a hit unit burns. */
    public static final int BURN_TICKS = 100;
    /** How long the ground burns: 5 s, a pulse every 10 ticks. */
    public static final int BURN_GROUND_TICKS = 100;
    public static final int AUTO_MIN_IN_SPLASH = 3;

    public WorldbreakerHurl() {
        super(UnitAction.WORLDBREAKER_HURL, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, (float) SPLASH_RADIUS, true, false);
        this.autoRule(AutoRule.ENEMY_IN_RANGE);   // gated by autocastTargetOk: the densest group
        this.showRangeLine = true;
        this.showRadiusCircle = true;
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Hurl",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/fire_charge.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.WORLDBREAKER_HURL,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.WORLDBREAKER_HURL),
            null,
            List.of(
                FormattedCharSequence.forward("Hurl  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Lobs a molten boulder up to " + (int) RANGE + " blocks: " + (int) DAMAGE + " damage in "
                    + (int) SPLASH_RADIUS + " blocks, sets enemies alight and leaves the ground burning for 5 s. Flyers are not touched.", Style.EMPTY)
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

    void cast(Level level, Unit unitUsing, Vec3 at) {
        if (level.isClientSide() || !(unitUsing instanceof WorldbreakerUnit wb) || !(level instanceof ServerLevel sl) || !wb.isAlive())
            return;
        lob(sl, wb, at);
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(wb.getId(), this.action, this.cooldownMax);
    }

    /** Lobs the boulder at {@code at} (clamped to {@link #RANGE}) and schedules the burning ground. Returns the shell. Public for the game test. */
    public static BombardShell lob(ServerLevel sl, WorldbreakerUnit wb, Vec3 at) {
        double dx = at.x - wb.getX(), dz = at.z - wb.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        double k = flat > RANGE ? RANGE / flat : 1.0;
        double x = wb.getX() + dx * k, z = wb.getZ() + dz * k, y = at.y;
        BombardShell shell = new BombardShell(sl, wb, DAMAGE).molten(SPLASH_RADIUS, BURN_TICKS).groundOnly();
        shell.setPos(wb.getX(), wb.getEyeY() + 0.5, wb.getZ());
        shell.aimAt(x, y, z);
        sl.addFreshEntity(shell);
        sl.playSound(null, wb.blockPosition(), SoundEvents.BLAZE_SHOOT, net.minecraft.sounds.SoundSource.HOSTILE, 2.5f, 0.5f);
        sl.sendParticles(ParticleTypes.LAVA, wb.getX(), wb.getEyeY() + 0.6, wb.getZ(), 6, 0.3, 0.2, 0.3, 0);
        // the shell lands after its flight: the ground burns from then on
        int flight = BombardShell.flightTicksFor(Math.sqrt((x - wb.getX()) * (x - wb.getX()) + (z - wb.getZ()) * (z - wb.getZ())));
        String owner = wb.getOwnerName();
        TaskSchedulerServerEvents.schedule(flight + 1, () -> scorch(sl, owner, x, y, z));
        return shell;
    }

    /**
     * Burning ground at (x, y, z): every 10 ticks for {@link #BURN_GROUND_TICKS} ticks the pool shows flames and
     * re-ignites every enemy ground unit standing in it. Public for the game test.
     */
    public static void scorch(ServerLevel sl, String owner, double x, double y, double z) {
        for (int t = 10; t <= BURN_GROUND_TICKS; t += 10) {
            TaskSchedulerServerEvents.schedule(t, () -> {
                sl.sendParticles(ParticleTypes.FLAME, x, y + 0.2, z, 6, SPLASH_RADIUS * 0.4, 0.1, SPLASH_RADIUS * 0.4, 0.02);
                sl.sendParticles(ParticleTypes.LAVA, x, y + 0.2, z, 1, SPLASH_RADIUS * 0.4, 0.1, SPLASH_RADIUS * 0.4, 0);
                for (LivingEntity le : WorldbreakerUnit.groundFoes(sl, owner, x, z, SPLASH_RADIUS))
                    if (Math.abs(le.getY() - y) < 2.5)
                        le.setRemainingFireTicks(Math.max(le.getRemainingFireTicks(), 40));
            });
        }
    }

    /** The AutoCaster's gate: the target stands in the densest group in range, and at least {@link #AUTO_MIN_IN_SPLASH} are in its splash. */
    @Override
    public boolean autocastTargetOk(Unit caster, LivingEntity target) {
        if (!(caster instanceof LivingEntity self) || !(self.level() instanceof ServerLevel sl) || !WorldbreakerUnit.isGroundTarget(target))
            return false;
        String owner = caster.getOwnerName();
        int mine = WorldbreakerUnit.groundFoes(sl, owner, target.getX(), target.getZ(), SPLASH_RADIUS).size();
        if (mine < AUTO_MIN_IN_SPLASH)
            return false;
        for (LivingEntity other : WorldbreakerUnit.groundFoes(sl, owner, self.getX(), self.getZ(), RANGE))
            if (other != target && WorldbreakerUnit.groundFoes(sl, owner, other.getX(), other.getZ(), SPLASH_RADIUS).size() > mine)
                return false;   // a denser group is in range: that one is the target
        return true;
    }
}
