package com.solegendary.reignofnether.unit.units.villagers;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.CallAngel;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.Checkpoint;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.controls.FlyingUnitMoveControl;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Angel Caller's summons (CallAngel; lovish, Oct 29 2026): the Mender Angel, the Seraph Archer and the Sword
 * Seraph share this body - the vex's model drawn bright and tinted per kind by AngelRenderer until real angel art comes -
 * and its rules:
 * <ul>
 *   <li>it flies on the bee's flight plumbing (FlyingMoveToTargetGoal, FlyingUnitMoveControl, no gravity: it hovers
 *       where it stops) and is a flyer to every anti-air unit;</li>
 *   <li>it fights under its caster's owner and takes orders like any unit; idle and more than {@link #LEASH} blocks from
 *       its caster it flies back to it;</li>
 *   <li>it lives {@link #ticksLeft} ticks and then fades (a discard: no death, no wreck, no kill credit, no wisp); it is
 *       marked summoned too (Essence.markSummoned), so a kill drops no wisp either;</li>
 *   <li>it is free (no cost, no population): the caster paid essence for it.</li>
 * </ul>
 */
public abstract class AngelUnit extends PathfinderMob implements Unit {

    /** An idle angel further than this from its caster flies back to it. */
    public static final double LEASH = 10.0;
    private static final String LIFE_KEY = "bt_angel_life", SUMMONER_KEY = "bt_angel_summoner";

    //region
    private static final Abilities ABILITIES = new Abilities();
    @Override
    public void updateAbilityButtons() {
        abilities = ABILITIES.clone();
    }
    Object2ObjectArrayMap<Ability, Float> cooldowns = Unit.createCooldownMap();
    Object2ObjectArrayMap<Ability, Integer> charges = new Object2ObjectArrayMap<>();
    @Override public Object2ObjectArrayMap<Ability, Float> getAbilityCooldowns() { return cooldowns; }
    @Override public Object2ObjectArrayMap<Ability, Integer> getAbilityCharges() { return charges; }

    private int eatingTicksLeft = 0;
    public void setEatingTicksLeft(int amount) { eatingTicksLeft = amount; }
    public int getEatingTicksLeft() { return eatingTicksLeft; }
    private BlockPos anchorPos = new BlockPos(0,0,0);
    public void setAnchor(BlockPos bp) { anchorPos = bp; }
    public BlockPos getAnchor() { return anchorPos; }

    private final ArrayList<Checkpoint> checkpoints = new ArrayList<>();
    public ArrayList<Checkpoint> getCheckpoints() { return checkpoints; }

    public GarrisonGoal getGarrisonGoal() { return null; }
    public boolean canGarrison() { return false; }

    MoveToTargetBlockGoal usePortalGoal;
    public MoveToTargetBlockGoal getUsePortalGoal() { return usePortalGoal; }
    public boolean canUsePortal() { return getUsePortalGoal() != null; }

    public Abilities getAbilities() {return abilities;}
    public List<ItemStack> getItems() {return items;}
    public MoveToTargetBlockGoal getMoveGoal() {return moveGoal;}
    public SelectedTargetGoal<? extends LivingEntity> getTargetGoal() {return targetGoal;}
    public ReturnResourcesGoal getReturnResourcesGoal() {return null;}
    public int getMaxResources() {return 0;}

    protected MoveToTargetBlockGoal moveGoal;
    protected SelectedTargetGoal<? extends LivingEntity> targetGoal;

    public LivingEntity getFollowTarget() { return followTarget; }
    public boolean getHoldPosition() { return holdPosition; }
    public void setHoldPosition(boolean holdPosition) { this.holdPosition = holdPosition; }
    public void setFollowTarget(@Nullable LivingEntity target) { this.followTarget = target; }

    private LivingEntity followTarget = null; // if nonnull, continuously moves to the target
    private boolean holdPosition = false;

    // which player owns this unit? this format ensures its synched to client without having to use packets
    public String getOwnerName() { return this.entityData.get(ownerDataAccessor); }
    public void setOwnerName(String name) { this.entityData.set(ownerDataAccessor, name); }
    public static final EntityDataAccessor<String> ownerDataAccessor =
            SynchedEntityData.defineId(AngelUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(AngelUnit.class, EntityDataSerializers.INT);

    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
            SynchedEntityData.defineId(AngelUnit.class, EntityDataSerializers.STRING);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ownerDataAccessor, "");
        this.entityData.define(scenarioRoleDataAccessor, -1);
        this.entityData.define(onDeathCommandDataAccessor, "");
    }

    // free: the caster paid essence for it (no population, no wreck - like the Engine's drones)
    @Nullable
    public ResourceCost getCost() {return ResourceCosts.DEPLOYED_TURRET;}

    // endregion

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    /** Ticks until it fades; 0 = not bound to a caster yet (it never fades on its own). Server side. */
    private int lifeTicks = 0;
    @Nullable private UUID summoner = null;

    protected AngelUnit(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new FlyingUnitMoveControl(this);
        this.navigation = new FlyingPathNavigation(this, level());
        updateAbilityButtons();
    }

    /** Which of the three it is. */
    public abstract CallAngel.Kind kind();

    /** Ties it to the caster that called it, for {@code ticks} ticks. */
    public void bind(LivingEntity caster, int ticks) {
        this.summoner = caster.getUUID();
        this.lifeTicks = ticks;
    }

    public int ticksLeft() { return lifeTicks; }

    @Nullable
    public UUID getSummoner() { return summoner; }

    /** The living angel of {@code kind} that {@code caster} called, or null. Server side. */
    @Nullable
    public static AngelUnit liveFor(LivingEntity caster, CallAngel.Kind kind) {
        UUID id = caster.getUUID();
        for (LivingEntity le : UnitServerEvents.getAllUnits())
            if (le instanceof AngelUnit a && a.kind() == kind && a.isAlive() && !a.isRemoved() && id.equals(a.summoner))
                return a;
        return null;
    }

    @Override
    public boolean removeWhenFarAway(double d) { return false; }

    @Override
    public void tick() {
        this.setCanPickUpLoot(false);
        super.tick();
        Unit.tick(this);
        if (!(level() instanceof ServerLevel sl) || !isAlive())
            return;
        if (lifeTicks > 0 && --lifeTicks <= 0) {
            fade(sl);
            return;
        }
        // once a second (staggered by id): an idle angel that drifted off goes back to its caster
        if ((tickCount + getId()) % 20 == 0 && summoner != null && isIdle()
                && sl.getEntity(summoner) instanceof LivingEntity caster && caster.isAlive()
                && distanceToSqr(caster) > LEASH * LEASH)
            getMoveGoal().setMoveTarget(caster.getOnPos());
        if (tickCount % 5 == 0)
            sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.4, getZ(), 1, 0.2, 0.2, 0.2, 0.0);
    }

    /** Its time is up: it returns to the light in a flash. A discard - no death event, no wreck, no wisp. */
    public void fade(ServerLevel sl) {
        sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.5, getZ(), 16, 0.3, 0.5, 0.3, 0.08);
        sl.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 0.8f, 1.4f);
        discard();
    }

    // wings: no gravity and no fall damage, it hovers where its last order left it
    @Override
    public boolean causeFallDamage(float dist, float mult, @NotNull DamageSource source) { return false; }

    @Override
    protected void checkFallDamage(double y, boolean onGround, @NotNull BlockState state, @NotNull BlockPos pos) { }

    @Override // prevent vanilla logic for picking up items
    protected void pickUpItem(@NotNull ItemEntity pItemEntity) { }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM; }
    @Override
    protected SoundEvent getHurtSound(@NotNull DamageSource source) { return SoundEvents.ALLAY_HURT; }
    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.ALLAY_DEATH; }
    @Override
    public int getAmbientSoundInterval() { return 400; }

    /** The bee's flight (BeeUnit.travel, as the Wisp Choir): no gravity, so the angel hovers where it stops. */
    @Override
    public void travel(@NotNull Vec3 pTravelVector) {
        if (this.isControlledByLocalInstance()) {
            if (this.isInWater()) {
                this.moveRelative(0.02F, pTravelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.8));
            } else if (this.isInLava()) {
                this.moveRelative(0.02F, pTravelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
            } else {
                BlockPos ground = this.getBlockPosBelowThatAffectsMyMovement();
                final float f0 = 0.91f;
                float f = f0;
                if (this.onGround())
                    f = this.level().getBlockState(ground).getFriction(this.level(), ground, this) * f0;
                float f1 = 0.16277137F / (f * f * f);
                this.moveRelative(this.onGround() ? 0.1F * f1 : 0.02F, pTravelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(f));
            }
        }
        this.calculateEntityAnimation(false);
    }

    @Override
    protected @NotNull PathNavigation createNavigation(@NotNull Level pLevel) {
        return new FlyingPathNavigation(this, pLevel);
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag pCompound) {
        super.addAdditionalSaveData(pCompound);
        this.addUnitSaveData(pCompound);
        pCompound.putInt(LIFE_KEY, lifeTicks);
        if (summoner != null)
            pCompound.putUUID(SUMMONER_KEY, summoner);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag pCompound) {
        super.readAdditionalSaveData(pCompound);
        this.readUnitSaveData(pCompound);
        this.lifeTicks = pCompound.getInt(LIFE_KEY);
        this.summoner = pCompound.hasUUID(SUMMONER_KEY) ? pCompound.getUUID(SUMMONER_KEY) : null;
    }

    /** The shared goals; a kind adds its attack goal (or none) in {@link #registerGoals}. */
    public void initialiseGoals() {
        this.usePortalGoal = new FlyingUsePortalGoal(this);
        this.moveGoal = new FlyingMoveToTargetGoal(this, 0);
        this.targetGoal = new SelectedTargetGoal<>(this, true, true);
    }

    @Override
    protected void registerGoals() {
        initialiseGoals();
        this.goalSelector.addGoal(2, usePortalGoal);
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.targetSelector.addGoal(2, targetGoal);
        this.goalSelector.addGoal(3, moveGoal);
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor pLevel, @NotNull DifficultyInstance pDifficulty, @NotNull MobSpawnType pReason, @Nullable SpawnGroupData pSpawnData, @Nullable CompoundTag pDataTag) {
        return pSpawnData;
    }
}
