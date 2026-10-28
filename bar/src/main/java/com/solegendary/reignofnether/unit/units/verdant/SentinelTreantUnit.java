package com.solegendary.reignofnether.unit.units.verdant;

import com.solegendary.reignofnether.ability.abilities.CryptTide;
import com.solegendary.reignofnether.ability.abilities.TakeRoot;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.IronGolemUnit;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Verdant Court tank - the <b>Sentinel Treant</b> (design/verdant_court_plan.md, slice 1): a walking oak on the Iron
 * Golem's body and brain (the Sun Colossus extends it the same way), drawn with its own bark-and-moss texture by
 * SentinelTreantRenderer. Tougher than the Kingdom's golem and soaks arrows, but hits softer and walks slower: it holds
 * the line while Thornbows root what comes at it.
 * <p>
 * <b>Take Root</b> ({@link TakeRoot}, vehicles-design 2026-10-28 3.4, the Rooted Treant): roots in place over
 * {@link #ROOT_TIMES}, can't move or slam while rooted, takes x{@link #ROOTED_TAKEN_MULT} damage, and every
 * {@link #LINE_EVERY} ticks bursts a {@link #LINE_LENGTH}-block line of root spikes along the ground toward the nearest
 * enemy, hurting every enemy unit on it (the Evoker's fang line, reskinned). Stats and building bonus are unchanged.
 */
public class SentinelTreantUnit extends IronGolemUnit {

    final static public float attackDamage = 10.0f;   // 8 -> 10 with 190M -> 170M (balance-pass): it lost to every raider 1.6-1.8x
    final static public float attacksPerSecond = 0.4f;
    final static public float maxHealth = 140.0f;
    final static public float movementSpeed = 0.20f;
    final static public float attackRange = 3;
    final static public float aggroRange = 10;
    final static public float rangedDamageResist = 0.4f;

    // ---- Take Root (vehicles-design 2026-10-28, 3.4: the Rooted Treant) -------------------------------------------------

    /** Root and uproot time in ticks. lovish's open question 5 is still open: option B (1.5 s / 1 s) until he picks. */
    public record RootTimes(int rootTicks, int uprootTicks) { }
    public static final RootTimes ROOT_TIMES = new RootTimes(30, 20);
    public static final float ROOTED_TAKEN_MULT = 0.7f;        // rooted it takes 30% less damage
    public static final float LINE_LENGTH = 8f;                // the root-spike line, from the Treant toward its target
    public static final float LINE_HALF_WIDTH = 0.9f;
    public static final float LINE_DAMAGE = 8f;                // to every enemy on the line
    public static final int LINE_EVERY = 40;
    public static final int LINE_TELEGRAPH_TICKS = 6;          // dirt trail and rumble before the spikes hit
    static final int LINE_RECHECK = 10;                        // no enemy in reach: look again in 0.5 s
    static final UUID ROOT_UUID = UUID.fromString("8f2d6c14-3b7a-4e95-b0c1-5a9e7d2f4c86");

    /** Synced so the button lights while rooted or rooting. */
    public static final EntityDataAccessor<Boolean> rootWantedDataAccessor =
            SynchedEntityData.defineId(SentinelTreantUnit.class, EntityDataSerializers.BOOLEAN);

    /** The toggle: true while the player wants it rooted. */
    private boolean rootWanted = false;
    /** Planted: set when rooting finishes, cleared when uprooting finishes. */
    private boolean rooted = false;
    /** Ticks left in the current root or uproot; 0 = settled. */
    private int transitionLeft = 0;
    private int lineCooldown = 0, pendingStrike = 0;
    private double pendingDx = 0, pendingDz = 0;
    /** Spike lines struck so far (game test). */
    public int linesFired = 0;

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(rootWantedDataAccessor, false);
    }

    public boolean isRootWanted() { return rootWanted; }
    public boolean isRootWantedClient() { return this.entityData.get(rootWantedDataAccessor); }
    /** Fully rooted: the spike lines fire. */
    public boolean isRooted() { return rooted && transitionLeft == 0; }
    /** Rooting, rooted or uprooting: it can't move. */
    public boolean isImmobile() { return rootWanted || rooted || transitionLeft > 0; }

    /**
     * The Take Root button: root if walking, uproot if rooted. Cancelling a root in progress frees it at once (it never
     * planted); a press while uprooting is ignored. Public for the ability and the game test.
     */
    public void toggleRoot() {
        if (!rootWanted) {
            if (rooted)
                return;   // already uprooting
            rootWanted = true;
            transitionLeft = ROOT_TIMES.rootTicks();
            Unit.fullResetBehaviours(this);   // a standing order: no chase drags it off the spot
        } else if (!rooted) {
            rootWanted = false;
            transitionLeft = 0;
        } else if (transitionLeft == 0) {
            rootWanted = false;
            transitionLeft = ROOT_TIMES.uprootTicks();
        } else {
            return;
        }
        this.entityData.set(rootWantedDataAccessor, rootWanted);
        level().playSound(null, blockPosition(), rootWanted ? SoundEvents.ROOTED_DIRT_PLACE : SoundEvents.ROOTED_DIRT_BREAK, SoundSource.NEUTRAL, 1.5f, 0.6f);
    }

    /** One tick of the roots and the spike line (server). Public for the game test. */
    public void tickRoot(ServerLevel sl) {
        if (transitionLeft > 0 && --transitionLeft == 0) {
            rooted = rootWanted;
            lineCooldown = 0;
            pendingStrike = 0;
            if (rooted)
                sl.playSound(null, blockPosition(), SoundEvents.ROOTED_DIRT_HIT, SoundSource.NEUTRAL, 2f, 0.5f);
        }
        setRootModifier(isImmobile());
        if (isImmobile()) {
            getNavigation().stop();
            setDeltaMovement(0, getDeltaMovement().y, 0);
        }
        if (!isRooted())
            return;
        if (pendingStrike > 0) {
            if (--pendingStrike == 0)
                strikeRootLine(sl, pendingDx, pendingDz);
            return;
        }
        if (--lineCooldown > 0)
            return;
        LivingEntity target = lineTarget(sl);
        if (target == null) {
            lineCooldown = LINE_RECHECK;
            return;
        }
        lineCooldown = LINE_EVERY - LINE_TELEGRAPH_TICKS;
        pendingDx = target.getX() - getX();
        pendingDz = target.getZ() - getZ();
        pendingStrike = LINE_TELEGRAPH_TICKS;
        telegraphLine(sl, pendingDx, pendingDz);
    }

    void setRootModifier(boolean on) {
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null)
            return;
        boolean has = speed.getModifier(ROOT_UUID) != null;
        if (on && !has)
            speed.addTransientModifier(new AttributeModifier(ROOT_UUID, "treant_roots", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
        else if (!on && has)
            speed.removeModifier(ROOT_UUID);
    }

    boolean isEnemy(LivingEntity le) {
        return le != this && le.isAlive() && le instanceof Unit
            && UnitServerEvents.getUnitToEntityRelationship(this, le) == Relationship.HOSTILE;
    }

    /** Its current target if that is an enemy in reach, else the nearest enemy unit in reach. */
    @Nullable
    LivingEntity lineTarget(ServerLevel sl) {
        double r2 = LINE_LENGTH * LINE_LENGTH;
        LivingEntity t = getTarget();
        if (t != null && isEnemy(t) && horizontalDistSqr(t) <= r2)
            return t;
        LivingEntity best = null;
        double bestD = r2;
        for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(LINE_LENGTH, 2, LINE_LENGTH))) {
            double d = horizontalDistSqr(le);
            if (d <= bestD && isEnemy(le)) {
                best = le;
                bestD = d;
            }
        }
        return best;
    }

    double horizontalDistSqr(LivingEntity le) {
        double dx = le.getX() - getX(), dz = le.getZ() - getZ();
        return dx * dx + dz * dz;
    }

    /** The warning before a line: the Evoker's fang line (SetFangsLine's spacing) as visual-only spikes, and a dirt trail. */
    void telegraphLine(ServerLevel sl, double dx, double dz) {
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4)
            return;
        double ux = dx / len, uz = dz / len;
        float yRot = (float) Mth.atan2(uz, ux);
        BlockParticleOption dirt = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState());
        for (int k = 0; 1.25 * (k + 1) <= LINE_LENGTH; k++) {
            double d = 1.25 * (k + 1);
            double x = getX() + ux * d, z = getZ() + uz * d;
            EvokerFangs fang = new EvokerFangs(sl, x, getY(), z, yRot, k / 2, this);
            fang.addTag(CryptTide.VISUAL_FANG_TAG);   // EvokerFangsMixin skips its bite: strikeRootLine deals the damage
            sl.addFreshEntity(fang);
            sl.sendParticles(dirt, x, getY() + 0.1, z, 6, 0.3, 0.05, 0.3, 0.05);
        }
        sl.playSound(null, blockPosition(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.HOSTILE, 2f, 0.4f);
    }

    /**
     * One line of root spikes from the Treant along (dx, dz), LINE_LENGTH long: LINE_DAMAGE to every enemy unit on it,
     * never a friend. Returns how many it hurt. Public for the game test.
     */
    public int strikeRootLine(ServerLevel sl, double dx, double dz) {
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4)
            return 0;
        double ux = dx / len, uz = dz / len;
        linesFired++;
        int hits = 0;
        for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(LINE_LENGTH + 1, 2, LINE_LENGTH + 1))) {
            if (!isEnemy(le))
                continue;
            double ex = le.getX() - getX(), ez = le.getZ() - getZ();
            double along = ex * ux + ez * uz, across = Math.abs(ex * uz - ez * ux);
            if (along <= 0 || along > LINE_LENGTH || across > LINE_HALF_WIDTH + le.getBbWidth() * 0.5)
                continue;
            if (le.hurt(damageSources().mobAttack(this), LINE_DAMAGE))
                hits++;
        }
        BlockParticleOption roots = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.MANGROVE_ROOTS.defaultBlockState());
        for (double d = 1; d <= LINE_LENGTH; d += 1)
            sl.sendParticles(roots, getX() + ux * d, getY() + 0.4, getZ() + uz * d, 4, 0.2, 0.3, 0.2, 0.1);
        return hits;
    }

    /** Rooted, the bark closes up: every hit is cut to ROOTED_TAKEN_MULT. */
    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        return super.hurt(source, rooted ? amount * ROOTED_TAKEN_MULT : amount);
    }

    /** Rooted (or rooting) it doesn't slam: the spike line does the fighting. */
    @Override
    public boolean doHurtTarget(@NotNull Entity target) {
        if (isImmobile())
            return false;
        return super.doHurtTarget(target);
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel sl && isAlive())
            tickRoot(sl);
    }

    @Override
    public void updateAbilityButtons() {
        super.updateAbilityButtons();
        if (getAbilities() == null)
            return;
        for (var a : getAbilities().get())
            if (a == TakeRoot.INSTANCE)
                return;
        getAbilities().add(TakeRoot.INSTANCE, Keybindings.abilitySlot2);
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag pCompound) {
        super.addAdditionalSaveData(pCompound);
        pCompound.putBoolean("bt_root_wanted", rootWanted);
        pCompound.putBoolean("bt_rooted", rooted);
        pCompound.putInt("bt_root_left", transitionLeft);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag pCompound) {
        super.readAdditionalSaveData(pCompound);
        rootWanted = pCompound.getBoolean("bt_root_wanted");
        rooted = pCompound.getBoolean("bt_rooted");
        transitionLeft = Math.max(0, Math.min(Math.max(ROOT_TIMES.rootTicks(), ROOT_TIMES.uprootTicks()), pCompound.getInt("bt_root_left")));
        this.entityData.set(rootWantedDataAccessor, rootWanted);
        setRootModifier(isImmobile());
    }

    public SentinelTreantUnit(EntityType<? extends IronGolem> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.SENTINEL_TREANT; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.ARMOR, 0)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), rangedDamageResist)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.BUILDING_DAMAGE_BONUS.get(), com.solegendary.reignofnether.raid.RaidTuning.vehicleBuildingBonus(attackDamage, attacksPerSecond));   // T1 vehicle: breaks buildings (RaidTuning)
    }

    // creaking wood rather than clanking iron
    @Override
    protected SoundEvent getHurtSound(DamageSource pDamageSource) {
        return SoundEvents.WOOD_BREAK;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.AZALEA_LEAVES_BREAK;
    }
}
