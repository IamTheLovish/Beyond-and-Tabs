package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.mana.ManaServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Stormcallers Gravity Monk: <b>Singularity</b> (design/stormcallers_plan.md, slice 3). Aim at a spot within
 * {@link #RANGE} blocks: for {@link #TELEGRAPH_TICKS} ticks a ring of violet motes marks it (the danger first), then every
 * enemy within {@link #RADIUS} is hopped toward the centre (the Harpooner's pull maths - one velocity change, the vanilla
 * motion packet) and slowed for {@link #SLOW_TICKS} ticks, so the Judgement, a Frost Field or a Rocket Battery volley
 * lands on a clump. Friends are never pulled, nor flyers, nor anything too wide or tall to haul ({@link #canPull}: T3s
 * and golems stand where they are - they are only slowed). {@link #ENERGY_COST} energy through the Mana core (an
 * Overcharged or well-fed cast pulls over a wider ring: radius x power); {@link #CD_SECONDS} s cooldown. Refused =
 * nothing paid, no cooldown.
 */
public class Singularity extends Ability {

    public static final int CD_SECONDS = 25;
    public static final int RANGE = 12;
    public static final float RADIUS = 5f;
    public static final int ENERGY_COST = 50;
    public static final int TELEGRAPH_TICKS = 20;
    public static final int SLOW_TICKS = 2 * 20;
    public static final int SLOW_AMPLIFIER = 1;   // Slowness II
    /** Nothing wider or taller than this is pulled (it is still slowed): the Tentacle Grab's cap. */
    public static final float MAX_PULL_WIDTH = 1.5f, MAX_PULL_HEIGHT = 2.5f;
    /** A pull never lands the target closer to the centre than this (they'd stack inside each other). */
    public static final double PULL_STOP = 0.6;
    /** Blocks travelled per unit of starting horizontal speed over a 0.42 hop (the Harpooner's pull model). */
    static final double AIR_GLIDE = 7.0;

    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results (server thread)

    public Singularity() {
        super(UnitAction.SINGULARITY, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, RADIUS, false, true);
        this.showRangeCircle = true;
        this.showRadiusCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Singularity",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/ender_pearl.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.SINGULARITY,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.SINGULARITY),
            null,
            List.of(
                FormattedCharSequence.forward("Singularity  (" + ENERGY_COST + " energy, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("After 1 s, every enemy within " + (int) RADIUS + " blocks of a spot up to " + RANGE
                    + " blocks away is dragged into it and slowed for " + SLOW_TICKS / 20 + " s.", Style.EMPTY),
                FormattedCharSequence.forward("Big beasts and flyers are only slowed. Follow it with the Judgement.", Style.EMPTY),
                FormattedCharSequence.forward("All things fall toward the centre.", Style.EMPTY.withItalic(true))
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
        if (cast(sl, self, unitUsing.getOwnerName(), targetBp, false)) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /**
     * Pays and schedules the vortex at {@code aim} (pulled in to {@link #RANGE}). Returns false when refused (nothing
     * paid). Public for the bot and the game test (no cooldown handling here).
     */
    public static boolean cast(ServerLevel sl, LivingEntity self, String owner, BlockPos aim, boolean quiet) {
        if (owner == null || aim == null)
            return false;
        double dx = aim.getX() + 0.5 - self.getX(), dz = aim.getZ() + 0.5 - self.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > RANGE + 1) {   // too far: the vortex opens at the end of the monk's reach along the line
            dx *= RANGE / len;
            dz *= RANGE / len;
        }
        int x = (int) Math.floor(self.getX() + dx), z = (int) Math.floor(self.getZ() + dz);
        int y = com.solegendary.reignofnether.mana.Blink.groundY(sl, x, z, aim.getY() + 1);   // the floor by the aimed block, not the heightmap
        float power = quiet ? ManaServerEvents.tryCastQuiet(self, owner, ENERGY_COST) : ManaServerEvents.tryCast(self, owner, ENERGY_COST);
        if (power <= 0)
            return false;
        Vec3 centre = new Vec3(x + 0.5, y, z + 0.5);
        double radius = RADIUS * power;
        telegraph(sl, centre, radius);
        sl.playSound(null, BlockPos.containing(centre), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 2f, 0.5f);
        int now = sl.getServer().getTickCount();
        sl.getServer().tell(new TickTask(now + TELEGRAPH_TICKS / 2, () -> telegraph(sl, centre, radius)));
        sl.getServer().tell(new TickTask(now + TELEGRAPH_TICKS, () -> {
            if (self.isAlive())   // a monk killed mid-cast never closes the vortex
                pull(sl, owner, centre, radius);
        }));
        return true;
    }

    static void telegraph(ServerLevel sl, Vec3 c, double radius) {
        // sixteen motes on the ring and a few at the centre: enough to read the circle, cheap for a dozen monks
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x + Math.cos(a) * radius, c.y + 0.3, c.z + Math.sin(a) * radius, 1, 0.05, 0.05, 0.05, 0.0);
        }
        sl.sendParticles(ParticleTypes.PORTAL, c.x, c.y + 0.8, c.z, 6, 0.3, 0.3, 0.3, 0.2);
    }

    /** Can this be hauled at all: not a friend, not a flyer, not too big (big ones are still slowed). */
    public static boolean canPull(LivingEntity target) {
        if (target instanceof Unit u && u.isFlyingUnit())
            return false;
        return target.getBbWidth() <= MAX_PULL_WIDTH && target.getBbHeight() <= MAX_PULL_HEIGHT;
    }

    /**
     * The vortex closes: every enemy unit of {@code owner} within {@code radius} of {@code centre} is slowed and, if
     * it can be pulled, hopped toward the centre. Returns how many were caught. Public for the game test.
     */
    public static int pull(ServerLevel sl, String owner, Vec3 centre, double radius) {
        int caught = 0;
        double r2 = radius * radius;
        for (LivingEntity le : UnitGrid.near(sl, centre.x, centre.z, radius, scratch)) {
            if (!le.isAlive() || !(le instanceof Unit u) || le.level() != sl)
                continue;
            String other = u.getOwnerName();
            if (owner.equals(other) || AlliancesServerEvents.isAllied(owner, other))
                continue;
            double dx = centre.x - le.getX(), dz = centre.z - le.getZ();
            double d2 = dx * dx + dz * dz;
            if (d2 > r2 || Math.abs(le.getY() - centre.y) > 4)
                continue;
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, SLOW_AMPLIFIER));
            caught++;
            if (!canPull(le))
                continue;
            double dist = Math.sqrt(d2);
            double drag = dist - PULL_STOP;
            if (drag <= 0.25 || dist < 1e-3)
                continue;
            double kbRes = le.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            double v = drag / AIR_GLIDE * Math.max(0.25, 1.0 - kbRes);
            le.setDeltaMovement(dx / dist * v, 0.42, dz / dist * v);
            le.hasImpulse = true;
            le.hurtMarked = true;   // the vanilla motion packet: clients see the haul at once
            if (le instanceof Mob mob)
                mob.getNavigation().stop();
        }
        scratch.clear();
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, centre.x, centre.y + 1, centre.z, 40, radius * 0.5, 0.5, radius * 0.5, 0.3);
        sl.sendParticles(ParticleTypes.SONIC_BOOM, centre.x, centre.y + 1, centre.z, 1, 0, 0, 0, 0);
        sl.playSound(null, BlockPos.containing(centre), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.NEUTRAL, 0.8f, 1.6f);
        sl.playSound(null, BlockPos.containing(centre), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1.2f, 0.6f);
        return caught;
    }
}
