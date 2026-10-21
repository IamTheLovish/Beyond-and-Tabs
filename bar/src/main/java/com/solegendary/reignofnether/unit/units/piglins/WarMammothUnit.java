package com.solegendary.reignofnether.unit.units.piglins;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.TitanMass;
import com.solegendary.reignofnether.unit.UnitDamage;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.RavagerUnit;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Ironhide Horde T3 experimental - the <b>War Mammoth</b>: the <b>Flanker</b> (claude/t3-roles.md, section 4), our
 * BAR Marauder. A titan on the Siege Ox's body and brain, drawn at {@link #SCALE}x. The strategy it opens: go round the
 * front, hit the economy, snipe the commander - the Horde's game is aggression and this is the unit that ends a game by
 * running round the side. The enemy's moment: "it's in my base" - pull units back or lose the back line.
 * <ul>
 *   <li><b>Fast for its size</b> ({@link #movementSpeed}): faster than line infantry (0.25), caught by raiders and
 *   cavalry (0.31+). It marches with an army, it never trails one.</li>
 *   <li><b>Mass</b> ({@link TitanMass}): never pushed by other bodies, and small bodies in its way are shoved aside -
 *   it walks through a line instead of being wedged by it.</li>
 *   <li><b>Trample</b> ({@link com.solegendary.reignofnether.ability.abilities.Trample}): an aimed charge of up to
 *   {@link #TRAMPLE_LENGTH} blocks, {@link #TRAMPLE_STEP} a tick after a {@link #TRAMPLE_WINDUP_TICKS}-tick wind-up
 *   that draws the lane; every enemy it passes within {@link #TRAMPLE_HIT_RADIUS} of its body takes
 *   {@link #TRAMPLE_DAMAGE} once and is flung to its side. It is how the Mammoth gets behind a line or out of a trap -
 *   it moves the Mammoth, unlike the old instant lunge. A solid obstacle (a wall, a building) stops it: the Mammoth is
 *   a flanker, not the Horde's gate breaker (that is the Gorehorn Rhino).</li>
 *   <li><b>Cleave</b>: half of every tusk blow lands on the enemies within {@link #CLEAVE_RADIUS} of the target - its
 *   hits land on groups (the Sun Colossus' rule).</li>
 * </ul>
 * Countered by (never by a crippled stat): wall lines and towers at home (the charge stops at a wall, towers outrange
 * the tusks), slows and roots (the Thornbow, Rootquake, Frost Field), being caught in a choke by massed T2, and
 * anti-flank play - a few units kept at home. It loses to a focused equal-cost T2 army; it wins against a spread
 * economy, artillery and an undefended back line. Arrival announced to every player.
 * <p>
 * Why the explicit {@link #aiStep}: vanilla Ravager resets its walking speed every tick and RavagerUnit's aiStep
 * answered with <i>its own</i> 0.25 - so the Mammoth never walked at the 0.17 its stats said. Now the stat is the truth.
 * Placeholder look: the ravager model, scaled.
 */
public class WarMammothUnit extends RavagerUnit {

    final static public float attackDamage = 18.0f;
    final static public float attacksPerSecond = 0.5f;
    final static public float maxHealth = 1150.0f;   // 1100 -> 1150: no two T3s share an HP total (lovish, Oct 20); the flanker keeps enough mass to push through a line and out again
    final static public float movementSpeed = 0.27f;  // 0.17 -> 0.27 (t3-roles): the Marauder - faster than line infantry (0.25), caught by raiders (0.31)
    final static public float attackRange = 3;
    final static public float aggroRange = 12;
    public static final float SCALE = 1.7f;
    public static final float CLEAVE_RADIUS = 2.5f;
    public static final float CLEAVE_SPLASH = 0.5f;

    /** The charge: how far, how fast, the wind-up that draws the lane, who it hits and for how much. */
    public static final float TRAMPLE_LENGTH = 16f;
    public static final int TRAMPLE_WINDUP_TICKS = 6;
    public static final double TRAMPLE_STEP = 1.1;
    public static final double TRAMPLE_HIT_RADIUS = 2.0;
    public static final float TRAMPLE_DAMAGE = 24f;
    static final String KEY_ANNOUNCED = "bt_t3_announced";

    /** The charge's state: wind-up ticks left, then steps left; 0/0 = not charging. Not saved (a reload cancels it). */
    public int trampleWindup = 0, trampleSteps = 0;
    @Nullable public Vec3 trampleDir = null;
    /** Enemies already hit by the current charge (each once). */
    private final IntOpenHashSet trampleHit = new IntOpenHashSet();
    /** What the last charge did (game test): units hit, and how far it ran. */
    public int lastTrampleHits = 0;
    public double lastTrampleDistance = 0;
    private double trampleStartX, trampleStartZ;
    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused (server thread)

    public WarMammothUnit(EntityType<? extends Ravager> entityType, Level level) {
        super(entityType, level);
    }

    /** RavagerUnit rebuilds its ability list from the shared static set here; add the Mammoth's own on top. */
    @Override
    public void updateAbilityButtons() {
        super.updateAbilityButtons();
        if (getAbilities() == null)
            return;
        for (var a : getAbilities().get())
            if (a instanceof com.solegendary.reignofnether.ability.abilities.Trample)
                return;
        getAbilities().add(new com.solegendary.reignofnether.ability.abilities.Trample());
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.WAR_MAMMOTH; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.ARMOR, 0)
                .add(Attributes.ATTACK_KNOCKBACK, 3.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    /** Its own walking speed every tick (see the class comment), not the Ravager's. */
    @Override
    public void aiStep() {
        super.aiStep();
        if (this.isAlive() && this.getAttribute(Attributes.MOVEMENT_SPEED) != null)
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(movementSpeed);
    }

    // mass: never pushed, shoves small bodies aside (TitanMass)
    @Override
    public boolean isPushable() {
        return TitanMass.isPushable();
    }

    @Override
    protected void doPush(@NotNull Entity other) {
        TitanMass.doPush(this, other);
    }

    /** The cleave: a landed blow also hits every enemy within {@link #CLEAVE_RADIUS} of the target for half damage. */
    @Override
    public boolean doHurtTarget(@NotNull Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && level() instanceof ServerLevel sl
                && UnitDamage.meleeSplash(sl, this, target, CLEAVE_RADIUS, getUnitAttackDamage() * CLEAVE_SPLASH) > 0)
            sl.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 1, target.getZ(), 3, 1.0, 0.2, 1.0, 0);
        return hit;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl) || !isAlive())
            return;
        if (tickCount > 5 && !getPersistentData().getBoolean(KEY_ANNOUNCED)
                && getOwnerName() != null && !getOwnerName().isEmpty()) {
            getPersistentData().putBoolean(KEY_ANNOUNCED, true);
            com.solegendary.reignofnether.player.PlayerServerEvents.sendMessageToAllPlayers(
                "server.reignofnether.t3_arrived", true, getOwnerName(),
                net.minecraft.network.chat.Component.translatable("entity.reignofnether.war_mammoth_unit"));
        }
        if (trampleWindup > 0 || trampleSteps > 0)
            tickTrample(sl);
    }

    public boolean isTrampling() {
        return trampleWindup > 0 || trampleSteps > 0;
    }

    /**
     * Orders a Trample toward {@code at}: locks the lane (as far as the spot, at most {@link #TRAMPLE_LENGTH}) and
     * starts the wind-up. False if already charging or the spot is on top of the Mammoth. Public for the ability, the
     * bot and the game test.
     */
    public boolean startTrample(Vec3 at) {
        if (isTrampling() || at == null)
            return false;
        Vec3 flat = new Vec3(at.x - getX(), 0, at.z - getZ());
        double dist = flat.length();
        if (dist < 1.0)
            return false;
        Unit.fullResetBehaviours(this);   // the charge is an order: the AI must not drag the Mammoth back mid-charge
        trampleDir = flat.scale(1.0 / dist);
        trampleWindup = TRAMPLE_WINDUP_TICKS;
        trampleSteps = (int) Math.ceil(Math.min(dist, TRAMPLE_LENGTH) / TRAMPLE_STEP);
        trampleHit.clear();
        lastTrampleHits = 0;
        lastTrampleDistance = 0;
        trampleStartX = getX();
        trampleStartZ = getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-trampleDir.x, trampleDir.z));   // yaw 0 = +z, as Vec3.directionFromRotation reads it
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        if (level() instanceof ServerLevel sl) {
            drawLane(sl);
            sl.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3f, 0.6f);
        }
        return true;
    }

    /** One tick of the charge: wind-up (lane telegraph), then a step with hits. Public for the game test. */
    public void tickTrample(ServerLevel sl) {
        if (trampleDir == null) {
            trampleWindup = trampleSteps = 0;
            return;
        }
        getNavigation().stop();
        if (trampleWindup > 0) {
            trampleWindup--;
            if (trampleWindup % 3 == 0)
                drawLane(sl);
            sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.2, getZ(), 2, 0.6, 0.05, 0.6, 0.01);
            return;
        }
        trampleSteps--;
        double x0 = getX(), z0 = getZ();
        move(MoverType.SELF, new Vec3(trampleDir.x * TRAMPLE_STEP, -0.1, trampleDir.z * TRAMPLE_STEP));
        setDeltaMovement(0, getDeltaMovement().y, 0);
        hitAlongTrample(sl);
        double dx = getX() - x0, dz = getZ() - z0;
        if (dx * dx + dz * dz < TRAMPLE_STEP * TRAMPLE_STEP * 0.25) {
            // something solid: a wall line is the flanker's counter, the charge ends there
            sl.sendParticles(ParticleTypes.CLOUD, getX() + trampleDir.x * 1.5, getY() + 0.5, getZ() + trampleDir.z * 1.5, 8, 0.6, 0.4, 0.6, 0.05);
            trampleSteps = 0;
        }
        sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 2, 0.6, 0.1, 0.6, 0.02);
        if (trampleSteps <= 0) {
            double ex = getX() - trampleStartX, ez = getZ() - trampleStartZ;
            lastTrampleDistance = Math.sqrt(ex * ex + ez * ez);
            com.solegendary.reignofnether.barfx.BarFx.heavyImpact(sl, position(), 2.5f);
            trampleDir = null;
        }
    }

    /** Every enemy unit within TRAMPLE_HIT_RADIUS of the body not yet hit this charge: damage and a fling to its side of the lane. */
    void hitAlongTrample(ServerLevel sl) {
        String owner = getOwnerName();
        if (owner == null || trampleDir == null)
            return;
        double r = TRAMPLE_HIT_RADIUS + getBbWidth() * 0.5;
        for (LivingEntity le : UnitGrid.near(sl, getX(), getZ(), r, scratch)) {
            if (le == this || !le.isAlive() || le.level() != sl || !(le instanceof Unit u) || trampleHit.contains(le.getId()))
                continue;
            String o = u.getOwnerName();
            if (o == null || o.isEmpty() || o.equals(owner) || AlliancesServerEvents.isAllied(owner, o))
                continue;
            double ex = le.getX() - getX(), ez = le.getZ() - getZ();
            if (ex * ex + ez * ez > r * r || Math.abs(le.getY() - getY()) > 3)
                continue;
            trampleHit.add(le.getId());
            // an explicit amount: RoN rewrites a unit's MOB_ATTACK to its full tusk blow (UnitDamage)
            UnitDamage.hurtExplicit(le, damageSources().mobAttack(this), TRAMPLE_DAMAGE);
            // flung to the side it stood on (the Mammoth ploughs through, it doesn't carry them along)
            double side = Math.signum(ex * -trampleDir.z + ez * trampleDir.x);
            if (side == 0)
                side = 1;
            le.push(-trampleDir.z * side * 1.2, 0.4, trampleDir.x * side * 1.2);
            le.hurtMarked = true;
            lastTrampleHits++;
        }
        scratch.clear();
    }

    /** The telegraph: a dotted line of dust down the locked lane, as far as the charge will run. */
    void drawLane(ServerLevel sl) {
        if (trampleDir == null)
            return;
        double len = trampleSteps * TRAMPLE_STEP;
        for (double d = 1.5; d <= len; d += 1.5)
            sl.sendParticles(ParticleTypes.CRIT, getX() + trampleDir.x * d, getY() + 0.15, getZ() + trampleDir.z * d, 1, 0.1, 0, 0.1, 0);
    }
}
