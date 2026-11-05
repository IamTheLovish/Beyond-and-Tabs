package com.solegendary.reignofnether.unit.units.monsters;

import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.shared.SiegeLobberUnit;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The Soul Pyre, Gravebound's T2 siege unit (design/defences_tiers.md; lovish, Nov 5: "we can do moving massive flame
 * pit that conjurs magic and souls that can be a tier 2 seige unit"). Trained at the Dungeon. The counter audit
 * (DefenceCounterGameTests) found Gravebound the one faction that could not out-reach its own T1-lab / Tier 2 towers;
 * the Pyre lobs a conjured soul from {@link SoulPyreTuning#ATTACK_RANGE} blocks, beyond the anti-siege tower's 20.
 * <p>
 * The T1 siege lobber's frame (SiegeLobberUnit: the bow-paced goal, the nearest-block building goal, Attack Ground,
 * the lobbed BombardShell that hits defences at the full rate, FieldArtillery's no-air and set-up) with every number
 * from {@link SoulPyreTuning} and a soul-fire launch. The body is a placeholder (the lobber's illager, its own skin)
 * until the animation lane draws the pit.
 */
public class SoulPyreUnit extends SiegeLobberUnit {

    final static public float attackDamage = SoulPyreTuning.DAMAGE;
    final static public float attacksPerSecond = SoulPyreTuning.ATTACKS_PER_SECOND;
    final static public float maxHealth = SoulPyreTuning.MAX_HEALTH;
    final static public float armorValue = SoulPyreTuning.ARMOR;
    final static public float movementSpeed = SoulPyreTuning.MOVEMENT_SPEED;
    final static public float attackRange = SoulPyreTuning.ATTACK_RANGE;
    final static public float aggroRange = SoulPyreTuning.ATTACK_RANGE;

    /**
     * Entity events for the body (the animation lane draws the pit against them; client side they land in
     * {@link #handleEntityEvent} as {@link #bodyEvent} and {@link #bodyEventTick}): a soul starts to gather, is thrown,
     * fizzles (the Pyre moved, or its target died, mid-conjure), and the set-up starts and is done.
     */
    public static final byte EVENT_CONJURE = 80, EVENT_RELEASE = 81, EVENT_FIZZLE = 82, EVENT_SET_UP_START = 83,
        EVENT_SET_UP_DONE = 84;

    /** The last body event this client saw, and the client tick it came on (-1: none yet). */
    public byte bodyEvent = 0;
    public int bodyEventTick = -1;

    // the conjure in progress (server): ticks left, the unit target or the point, where the Pyre stood
    private int conjureLeft = 0;
    @Nullable private LivingEntity conjureTarget = null;
    @Nullable private Vec3 conjurePoint = null;
    @Nullable private Vec3 conjureFrom = null;
    private boolean wasSetUp = true;

    public SoulPyreUnit(EntityType<? extends Pillager> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * The telegraph, part of the Pyre's designed weakness (design/defences_tiers.md): a shot is not thrown at once but
     * gathers for {@link SoulPyreTuning#CONJURE_TICKS} first, then flies at where its target stands at the release. The
     * attack goal's cooldown already started, so the cycle stays one soul every 1 / ATTACKS_PER_SECOND seconds.
     */
    @Override
    public void performUnitRangedAttack(LivingEntity pTarget, float velocity) {
        if (pTarget != null && canStartConjure(pTarget.getX(), pTarget.getZ()))
            startConjure(pTarget, null);
    }

    // Attack Ground and building targets: the same gathering, at a point
    @Override
    public void performUnitRangedAttack(double x, double y, double z, float velocity) {
        if (canStartConjure(x, z))
            startConjure(null, new Vec3(x, y, z));
    }

    /** Would a soul thrown now at (x, z) leave at all: server, nothing gathering, set up, outside the dead zone? */
    private boolean canStartConjure(double x, double z) {
        if (level().isClientSide() || conjureLeft > 0 || !com.solegendary.reignofnether.unit.SetUp.isSetUp(this))
            return false;
        double dx = x - getX(), dz = z - getZ();
        return dx * dx + dz * dz >= getMinRange() * getMinRange();
    }

    private void startConjure(@Nullable LivingEntity target, @Nullable Vec3 point) {
        conjureLeft = SoulPyreTuning.CONJURE_TICKS;
        conjureTarget = target;
        conjurePoint = point;
        conjureFrom = position();
        level().broadcastEntityEvent(this, EVENT_CONJURE);
    }

    /** True while a soul is gathering (for the game test and the AI). */
    public boolean isConjuring() {
        return conjureLeft > 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide() || !isAlive())
            return;
        boolean setUp = com.solegendary.reignofnether.unit.SetUp.isSetUp(this);
        if (setUp != wasSetUp)
            level().broadcastEntityEvent(this, setUp ? EVENT_SET_UP_DONE : EVENT_SET_UP_START);
        wasSetUp = setUp;
        if (conjureLeft <= 0)
            return;
        boolean moved = conjureFrom != null && position().distanceToSqr(conjureFrom) > 0.04;
        boolean lost = conjureTarget != null && (!conjureTarget.isAlive() || conjureTarget.isRemoved());
        if (moved || lost) {
            endConjure();
            level().broadcastEntityEvent(this, EVENT_FIZZLE);
            return;
        }
        if (--conjureLeft > 0)
            return;
        LivingEntity target = conjureTarget;
        Vec3 point = conjurePoint;
        endConjure();
        level().broadcastEntityEvent(this, EVENT_RELEASE);
        if (target != null)
            super.performUnitRangedAttack(target, 1.6f);   // the lobber's throw at where it stands now, and the fog reveal
        else if (point != null)
            super.performUnitRangedAttack(point.x, point.y, point.z, 1.6f);
    }

    private void endConjure() {
        conjureLeft = 0;
        conjureTarget = null;
        conjurePoint = null;
        conjureFrom = null;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id >= EVENT_CONJURE && id <= EVENT_SET_UP_DONE) {
            bodyEvent = id;
            bodyEventTick = tickCount;
            return;
        }
        super.handleEntityEvent(id);
    }

    @Override
    @Nullable
    public ResourceCost getCost() { return ResourceCosts.SOUL_PYRE; }

    @Override public float getMinRange() { return SoulPyreTuning.MIN_RANGE; }
    @Override public int getSetUpTicks() { return SoulPyreTuning.SET_UP_TICKS; }
    @Override protected float shellSplash() { return SoulPyreTuning.SPLASH_RADIUS; }
    @Override protected int shellFlightTicks() { return SoulPyreTuning.FLIGHT_TICKS; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MOVEMENT_SPEED, SoulPyreUnit.movementSpeed)
                .add(Attributes.MAX_HEALTH, SoulPyreUnit.maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, SoulPyreUnit.armorValue)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), SoulPyreTuning.SIGHT_RANGE)   // below its range: it needs spotters
                .add(AttributeRegistrar.BUILDING_DAMAGE_BONUS.get(), SoulPyreTuning.BUILDING_DAMAGE_BONUS)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    /** A soul torn out of the pit: a low roar and a plume of soul fire, not the lobber's puff. */
    @Override
    protected void launchFx(ServerLevel sl) {
        this.playSound(SoundEvents.SOUL_ESCAPE, 1.6F, 0.6F + getRandom().nextFloat() * 0.2F);
        this.playSound(SoundEvents.BLAZE_SHOOT, 0.6F, 0.5F);
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getEyeY() + 0.4, getZ(), 10, 0.3, 0.2, 0.3, 0.03);
        sl.sendParticles(ParticleTypes.SOUL, getX(), getEyeY() + 0.6, getZ(), 3, 0.2, 0.2, 0.2, 0.02);
    }
}
