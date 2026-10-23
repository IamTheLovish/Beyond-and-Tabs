package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
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
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Ironhide Horde (Magma Hurler): <b>Eruption</b> (second-t3-design.md). Aim at the ground: a ring of
 * {@link #RADIUS} blocks is drawn there in smoke and dripping lava for {@link #TELEGRAPH_TICKS} ticks (the danger is
 * shown before it lands - BAR's rule, and the game-feel checklist's red telegraph), then the ground erupts: every enemy
 * in the ring takes {@link #DAMAGE} and is thrown {@link #LIFT} up (a titan, whose knockback resistance is 1, only
 * takes the damage), and the spot glows with lava for {@link #GLOW_TICKS} ticks. No stun, no terrain damage: the ring
 * is fixed at cast time, so what the enemy sees is what is hit and a quick unit steps out. {@link #CD_SECONDS} s.
 */
public class Eruption extends Ability {

    public static final int CD_SECONDS = 45;
    public static final float RANGE = 22f;
    public static final float RADIUS = 5f;
    public static final float DAMAGE = 45f;
    /** Upward velocity of the knock-up (blocks a tick; about 1.3 blocks of height), scaled by 1 - knockback resistance. */
    public static final double LIFT = 0.9;
    /** 1.2 s of warning. */
    public static final int TELEGRAPH_TICKS = 24;
    /** 8 s of lava glow after it goes off. */
    public static final int GLOW_TICKS = 160;

    public Eruption() {
        super(UnitAction.ERUPTION, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, RADIUS, false, true);
        this.autoRule(AutoRule.CROWD);   // autocast: a ring pays off on a clump, not a straggler
        this.showRangeLine = true;
        this.showRadiusCircle = true;
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Eruption",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/magma_cream.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.ERUPTION,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.ERUPTION),
            null,
            List.of(
                FormattedCharSequence.forward("Eruption  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Marks a " + (int) RADIUS + "-block ring. After 1.2 s it erupts: "
                    + (int) DAMAGE + " damage and a knock-up to enemies in it, then glows for 8 s.", Style.EMPTY),
                FormattedCharSequence.forward("The ring shows first: step out of it.", Style.EMPTY.withItalic(true))
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
        if (level.isClientSide() || targetBp == null || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        Vec3 centre = new Vec3(targetBp.getX() + 0.5, targetBp.getY() + 1.0, targetBp.getZ() + 0.5);
        String owner = unitUsing.getOwnerName();

        // the ring now and halfway, so it reads as a danger zone that is about to go rather than a puff
        telegraph(sl, centre);
        sl.playSound(null, BlockPos.containing(centre), SoundEvents.LAVA_AMBIENT, SoundSource.HOSTILE, 3f, 0.6f);
        TaskSchedulerServerEvents.schedule(TELEGRAPH_TICKS / 2, () -> telegraph(sl, centre));
        TaskSchedulerServerEvents.schedule(TELEGRAPH_TICKS, () -> {
            if (self.isAlive())   // a Hurler killed mid-cast never finishes the spell
                erupt(sl, self, owner, centre);
        });

        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    static void telegraph(ServerLevel sl, Vec3 centre) {
        // ~one smoke puff per block of circumference, a lava drip every other: enough to read the ring, cheap for a dozen casts
        int points = (int) Math.ceil(2 * Math.PI * RADIUS);
        for (int i = 0; i < points; i++) {
            double a = i * 2 * Math.PI / points;
            double x = centre.x + Math.cos(a) * RADIUS, z = centre.z + Math.sin(a) * RADIUS;
            sl.sendParticles(ParticleTypes.SMOKE, x, centre.y + 0.1, z, 1, 0.1, 0.02, 0.1, 0.01);
            if (i % 2 == 0)
                sl.sendParticles(ParticleTypes.DRIPPING_LAVA, x, centre.y + 0.6, z, 1, 0.1, 0.1, 0.1, 0);
        }
    }

    /**
     * The eruption: {@link #DAMAGE} and a knock-up to every hostile unit within {@link #RADIUS} of {@code centre}, then
     * the lava glow. Returns how many were caught. Public for the game test.
     */
    public static int erupt(ServerLevel sl, LivingEntity self, String owner, Vec3 centre) {
        int hit = 0;
        List<LivingEntity> near = UnitGrid.near(sl, centre.x, centre.z, RADIUS + 1, new ArrayList<>());
        for (LivingEntity le : near) {
            if (le == self || !le.isAlive() || !(le instanceof Unit u) || le.level() != sl)
                continue;
            String other = u.getOwnerName();
            if (owner.equals(other) || AlliancesServerEvents.isAllied(owner, other))
                continue;
            double dx = le.getX() - centre.x, dz = le.getZ() - centre.z;
            if (dx * dx + dz * dz > RADIUS * RADIUS || Math.abs(le.getY() + le.getBbHeight() / 2 - centre.y) > 4)
                continue;
            // indirect magic, not mobAttack: RoN rewrites mob-attack damage to the attacker's melee damage
            le.hurt(sl.damageSources().indirectMagic(self, self), DAMAGE);
            double lift = LIFT * (1.0 - Math.min(1.0, le.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)));
            if (le.isAlive() && lift > 0) {
                Vec3 v = le.getDeltaMovement();
                le.setDeltaMovement(v.x, Math.max(v.y, lift), v.z);
                le.hurtMarked = true;
            }
            hit++;
        }
        sl.sendParticles(ParticleTypes.LAVA, centre.x, centre.y + 0.2, centre.z, 24, RADIUS * 0.5, 0.2, RADIUS * 0.5, 0);
        sl.sendParticles(ParticleTypes.FLAME, centre.x, centre.y + 0.3, centre.z, 24, RADIUS * 0.5, 0.5, RADIUS * 0.5, 0.05);
        com.solegendary.reignofnether.barfx.BarFx.heavyImpact(sl, centre, 3f);
        sl.playSound(null, BlockPos.containing(centre), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.8f, 0.6f);
        sl.playSound(null, BlockPos.containing(centre), SoundEvents.LAVA_POP, SoundSource.HOSTILE, 3f, 0.8f);
        // the lava glow: a pool that stays lit for GLOW_TICKS (a particle every second, nothing per tick)
        for (int t = 20; t <= GLOW_TICKS; t += 20)
            TaskSchedulerServerEvents.schedule(t, () ->
                sl.sendParticles(ParticleTypes.LAVA, centre.x, centre.y + 0.2, centre.z, 8, RADIUS * 0.5, 0.1, RADIUS * 0.5, 0));
        return hit;
    }
}
