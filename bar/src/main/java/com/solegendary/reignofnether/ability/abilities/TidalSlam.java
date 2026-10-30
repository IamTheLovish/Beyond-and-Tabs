package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.ExperimentalAbilityText;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Leviathan: <b>Tidal Slam</b> (design/tidewrought_plan.md, slice 4). The Leviathan rears up and brings both claws down
 * in a wall of water: for {@link #TELEGRAPH_TICKS} ticks a ring of spray marks the {@link #HALF_ANGLE_DEG}-degree-each-way
 * arc in front of it out to {@link #RADIUS} blocks (so the enemy can step out or round to its back), then every
 * hostile unit in the arc takes {@link #DAMAGE} and is thrown back. Friends are never touched. {@link #CD_SECONDS} s
 * cooldown, telegraphed and blind behind - the T3 rules forbid instant wins.
 * <p>
 * The arc and its facing are fixed when the slam starts (towards the Leviathan's target, else the nearest enemy, else
 * straight ahead), so the telegraph tells the truth.
 */
public class TidalSlam extends Ability {

    public static final int CD_SECONDS = 50;
    public static final int TELEGRAPH_TICKS = 20;
    public static final float RADIUS = 8f;
    public static final float DAMAGE = 25f;
    /** Half the arc: 110 degrees each way is a wide 220-degree front with a blind spot behind. */
    public static final double HALF_ANGLE_DEG = 110;
    public static final double KNOCKBACK = 1.6;

    public TidalSlam() {
        super(UnitAction.TIDAL_SLAM, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, 0, 0, false, false);
        this.autoRule(AutoRule.SELF_IN_COMBAT);   // autocast: an arc in front of the leviathan when it is fighting
    }

    /** Tooltip body. Public for the tooltip game test. */
    public String description() {
        return "After a 1 s warning, a wall of water crashes down in a wide arc in front: enemies take " + (int) DAMAGE + " damage and are thrown back.";
    }

    /** Tooltip range line, from the ability's own values. Public for the tooltip game test. */
    public String rangeLine() {
        return ExperimentalAbilityText.selfLine(RADIUS);
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Tidal Slam",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/prismarine_bricks.png"),
            hotkey,
            () -> false,
            () -> false,
            () -> true,
            () -> UnitClientEvents.sendUnitCommand(UnitAction.TIDAL_SLAM),
            null,
            List.of(
                FormattedCharSequence.forward("Tidal Slam  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward(rangeLine(), Style.EMPTY),
                FormattedCharSequence.forward(description(), Style.EMPTY),
                FormattedCharSequence.forward("The spray rises first, and nothing behind it is hit.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        String owner = unitUsing.getOwnerName();
        Vec3 centre = self.position();
        Vec3 dir = facing(sl, self, owner);
        telegraph(sl, centre, dir);
        sl.playSound(null, self.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.5f, 0.6f);
        sl.playSound(null, self.blockPosition(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.HOSTILE, 3f, 0.5f);
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        sl.getServer().tell(new TickTask(sl.getServer().getTickCount() + TELEGRAPH_TICKS, () -> slam(sl, self, owner, centre, dir)));
    }

    /** The warning: spray along the rim of the arc and up its two edges (a few dozen particles, once). */
    static void telegraph(ServerLevel sl, Vec3 centre, Vec3 dir) {
        double half = Math.toRadians(HALF_ANGLE_DEG);
        double base = Math.atan2(dir.z, dir.x);
        for (int i = 0; i <= 24; i++) {
            double a = base - half + (2 * half) * i / 24.0;
            sl.sendParticles(ParticleTypes.SPLASH, centre.x + Math.cos(a) * RADIUS, centre.y + 0.2, centre.z + Math.sin(a) * RADIUS,
                4, 0.2, 0.1, 0.2, 0.05);
        }
        for (double a : new double[] { base - half, base + half })
            for (int d = 2; d < RADIUS; d += 2)
                sl.sendParticles(ParticleTypes.BUBBLE, centre.x + Math.cos(a) * d, centre.y + 0.3, centre.z + Math.sin(a) * d,
                    3, 0.15, 0.1, 0.15, 0.02);
    }

    /** The flat unit direction the slam faces: its target, else the nearest enemy within reach, else ahead. */
    public static Vec3 facing(ServerLevel sl, LivingEntity self, String owner) {
        Vec3 to = null;
        if (self instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive())
            to = mob.getTarget().position();
        if (to == null && owner != null) {
            double best = (RADIUS + 1) * (RADIUS + 1);
            for (LivingEntity le : UnitGrid.near(sl, self.getX(), self.getZ(), RADIUS + 1, new ArrayList<>())) {
                if (le == self || !le.isAlive() || !isEnemy(owner, le))
                    continue;
                double d = le.distanceToSqr(self);
                if (d < best) {
                    best = d;
                    to = le.position();
                }
            }
        }
        Vec3 flat = to == null ? Vec3.ZERO : new Vec3(to.x - self.getX(), 0, to.z - self.getZ());
        if (flat.lengthSqr() < 0.01)
            flat = Vec3.directionFromRotation(0, self.getYRot());
        return flat.lengthSqr() < 0.0001 ? new Vec3(1, 0, 0) : flat.normalize();
    }

    /**
     * The crash: hurts and throws back every hostile unit in the arc (within {@link #RADIUS} of {@code centre}, no more
     * than {@link #HALF_ANGLE_DEG} off {@code dir}) and returns them. A Leviathan killed during the warning never
     * slams. Public for the game test and the bot.
     */
    public static List<LivingEntity> slam(ServerLevel sl, LivingEntity self, String owner, Vec3 centre, Vec3 dir) {
        List<LivingEntity> hit = new ArrayList<>();
        if (!self.isAlive() || owner == null || owner.isEmpty())
            return hit;
        double cosHalf = Math.cos(Math.toRadians(HALF_ANGLE_DEG));
        // UnitGrid hands back a shared candidate list; copy the hits out before hurting anything (a death can rebucket)
        for (LivingEntity le : UnitGrid.near(sl, centre.x, centre.z, RADIUS + 2, new ArrayList<>())) {
            if (le == self || !le.isAlive() || !isEnemy(owner, le))
                continue;
            if (Math.abs(le.getY() - centre.y) > 4)
                continue;
            double dx = le.getX() - centre.x, dz = le.getZ() - centre.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist - le.getBbWidth() / 2 > RADIUS)
                continue;
            // a unit under the Leviathan's own body is in the arc whichever way it faces
            if (dist > self.getBbWidth() / 2 && (dx * dir.x + dz * dir.z) / dist < cosHalf)
                continue;
            hit.add(le);
        }
        for (LivingEntity le : hit) {
            le.hurt(sl.damageSources().indirectMagic(self, self), DAMAGE);
            if (!le.isAlive())
                continue;
            double dx = le.getX() - centre.x, dz = le.getZ() - centre.z;
            double len = Math.sqrt(dx * dx + dz * dz);
            double px = len > 0.3 ? dx / len : dir.x, pz = len > 0.3 ? dz / len : dir.z;
            // LivingEntity.knockback pushes against (x, z) and respects knockback resistance (golems, treants)
            le.knockback(KNOCKBACK, -px, -pz);
            le.hurtMarked = true;
        }
        // the look: a breaking wave across the arc, a few bursts rather than one per block
        double half = Math.toRadians(HALF_ANGLE_DEG);
        double base = Math.atan2(dir.z, dir.x);
        for (int ray = -4; ray <= 4; ray++) {
            double a = base + half * ray / 4.0;
            for (int i = 2; i <= (int) RADIUS; i += 2)
                sl.sendParticles(ParticleTypes.SPLASH, centre.x + Math.cos(a) * i, centre.y + 0.6, centre.z + Math.sin(a) * i,
                    6, 0.4, 0.3, 0.4, 0.15);
        }
        sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, centre.x + dir.x * 3, centre.y + 0.5, centre.z + dir.z * 3,
            16, 1.2, 0.4, 1.2, 0.05);
        sl.playSound(null, BlockPos.containing(centre), SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 3f, 0.5f);
        sl.playSound(null, BlockPos.containing(centre), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.5f, 0.6f);
        com.solegendary.reignofnether.barfx.BarFx.heavyImpact(sl, centre, 3f);
        return hit;
    }

    static boolean isEnemy(String owner, LivingEntity le) {
        if (!(le instanceof Unit u))
            return false;
        String o = u.getOwnerName();
        return o != null && !o.isEmpty() && !owner.equals(o) && !AlliancesServerEvents.isAllied(owner, o);
    }
}
