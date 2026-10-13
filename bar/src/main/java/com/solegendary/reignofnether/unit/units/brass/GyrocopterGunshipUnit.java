package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientboundPacket;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.Checkpoint;
import com.solegendary.reignofnether.unit.EnemySearchBehaviour;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec2;
import com.solegendary.reignofnether.unit.controls.FlyingUnitMoveControl;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.Relationship;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;


/**
 * Brassworks T2 gunship - the <b>Gyrocopter Gunship</b> (design/brassworks_plan.md, slice 2): a soot-and-brass rotor
 * gunship on the vanilla parrot body at 1.6x (GyrocopterGunshipRenderer, its own skin), flying on the Gyro Scout's
 * plumbing (FlyingUnitMoveControl, FlyingPathNavigation, the bee's travel). Its rotary gun fires hit-scan rounds, and
 * every {@link #STRAFE_COOLDOWN_TICKS} it makes a <b>strafing run</b> instead of a shot (the Phoenix's dive as a gun
 * run): it lunges {@link #STRAFE_LENGTH} blocks through its target and rakes every enemy within {@link #STRAFE_WIDTH}
 * of that line for {@link #STRAFE_DAMAGE} ({@link #strafe}) - one grid query and a few tracers per run, no entity. Can't
 * hurt buildings.
 * <p>
 * A {@link com.solegendary.reignofnether.brass.Machine}: every round adds {@link #HEAT_PER_SHOT} heat, a run
 * {@link #STRAFE_HEAT}; at 100 it vents for 6 s (dry-fires, no runs, takes 25% more damage). Overclocked it fires
 * faster; a Tinker can plate it or fit a longer barrel. Anti-air brings it down like any flyer.
 */
public class GyrocopterGunshipUnit extends Parrot implements Unit, AttackerUnit, RangedAttackerUnit, com.solegendary.reignofnether.brass.Machine {
    public static final Abilities ABILITIES = new Abilities();

    //region
    @Override
    public void updateAbilityButtons() {
        abilities = ABILITIES.clone();
    }
    Object2ObjectArrayMap<Ability, Float> cooldowns = Unit.createCooldownMap();
    Object2ObjectArrayMap<Ability, Integer> charges = new Object2ObjectArrayMap<>();
    @Override public Object2ObjectArrayMap<Ability, Float> getAbilityCooldowns() { return cooldowns; }
    @Override public boolean hasAutocast(Ability ability) { return autocast == ability; }
    @Override public void setAutocast(Ability autocast) { this.autocast = autocast; }
    @Override public Object2ObjectArrayMap<Ability, Integer> getAbilityCharges() { return charges; }

    Ability autocast;


    private int eatingTicksLeft = 0;
    public void setEatingTicksLeft(int amount) { eatingTicksLeft = amount; }
    public int getEatingTicksLeft() { return eatingTicksLeft; }
    private BlockPos anchorPos = new BlockPos(0,0,0);
    public void setAnchor(BlockPos bp) { anchorPos = bp; }
    public BlockPos getAnchor() { return anchorPos; }

    private final ArrayList<Checkpoint> checkpoints = new ArrayList<>();
    public ArrayList<Checkpoint> getCheckpoints() { return checkpoints; };

    GarrisonGoal garrisonGoal;
    public GarrisonGoal getGarrisonGoal() { return garrisonGoal; }
    public boolean canGarrison() { return getGarrisonGoal() != null; }

    UnitItemGoal itemGoal;
    @Override public UnitItemGoal getItemGoal() { return itemGoal; }

    MoveToTargetBlockGoal usePortalGoal;
    public MoveToTargetBlockGoal getUsePortalGoal() { return usePortalGoal; }
    public boolean canUsePortal() { return getUsePortalGoal() != null; }

	public Abilities getAbilities() { return abilities; }
    public List<ItemStack> getItems() { return items; }

    public MoveToTargetBlockGoal getMoveGoal() { return moveGoal; }
    public SelectedTargetGoal<? extends LivingEntity> getTargetGoal() { return targetGoal; }
    public Goal getAttackGoal() { return attackGoal; }
    public ReturnResourcesGoal getReturnResourcesGoal() { return returnResourcesGoal; }
    public int getMaxResources() { return maxResources; }

    private EnemySearchBehaviour attackSearchBehaviour = EnemySearchBehaviour.NONE;
    public EnemySearchBehaviour getEnemySearchBehaviour() { return attackSearchBehaviour; }
    public void setEnemySearchBehaviour(EnemySearchBehaviour behaviour) { attackSearchBehaviour = behaviour; }

    private MoveToTargetBlockGoal moveGoal;
    private SelectedTargetGoal<? extends LivingEntity> targetGoal;
    private ReturnResourcesGoal returnResourcesGoal;

    public BlockPos getAttackMoveTarget() { return attackMoveTarget; }
    public LivingEntity getFollowTarget() { return followTarget; }
    public boolean getHoldPosition() { return holdPosition; }
    public void setHoldPosition(boolean holdPosition) { this.holdPosition = holdPosition; }

    // if true causes moveGoal and attackGoal to work together to allow attack moving
    // moves to a block but will chase/attack nearby monsters in range up to a certain distance away
    private BlockPos attackMoveTarget = null;
    private LivingEntity followTarget = null; // if nonnull, continuously moves to the target
    private boolean holdPosition = false;

    // which player owns this unit? this format ensures its synched to client without having to use packets
    public String getOwnerName() {
        return this.entityData.get(ownerDataAccessor);
    }

    public void setOwnerName(String name) {
        this.entityData.set(ownerDataAccessor, name);
    }

    public static final EntityDataAccessor<String> ownerDataAccessor =
            SynchedEntityData.defineId(GyrocopterGunshipUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(GyrocopterGunshipUnit.class, EntityDataSerializers.INT);
    
    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
        SynchedEntityData.defineId(GyrocopterGunshipUnit.class, EntityDataSerializers.STRING);


    // ---- Brassworks Machine (brass/Heat): heat and the vent are synced for the steam, the upgrade for the render layer
    public static final EntityDataAccessor<Integer> heatDataAccessor =
            SynchedEntityData.defineId(GyrocopterGunshipUnit.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> ventDataAccessor =
            SynchedEntityData.defineId(GyrocopterGunshipUnit.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Byte> upgradeDataAccessor =
            SynchedEntityData.defineId(GyrocopterGunshipUnit.class, EntityDataSerializers.BYTE);
    private int overclockTicks = 0;
    @Override public int heat() { return this.entityData.get(heatDataAccessor); }
    @Override public void setHeat(int heat) { this.entityData.set(heatDataAccessor, heat); }
    @Override public int ventTicks() { return this.entityData.get(ventDataAccessor); }
    @Override public void setVentTicks(int ticks) { this.entityData.set(ventDataAccessor, ticks); }
    @Override public byte upgrade() { return this.entityData.get(upgradeDataAccessor); }
    @Override public void setUpgrade(byte upgrade) { this.entityData.set(upgradeDataAccessor, upgrade); }
    @Override public int overclockTicks() { return overclockTicks; }
    @Override public void setOverclockTicks(int ticks) { overclockTicks = ticks; }
    @Override public int heatPerShot() { return HEAT_PER_SHOT; }
    @Override public boolean isRangedMachine() { return true; }
    public static final int HEAT_PER_SHOT = 8;

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ownerDataAccessor, "");
        this.entityData.define(scenarioRoleDataAccessor, -1);
        this.entityData.define(onDeathCommandDataAccessor, "");
        this.entityData.define(heatDataAccessor, 0);
        this.entityData.define(ventDataAccessor, 0);
        this.entityData.define(upgradeDataAccessor, (byte) 0);
    }

    // combat stats
    public boolean getWillRetaliate() { return willRetaliate; }
    public boolean getAggressiveWhenIdle() { return aggressiveWhenIdle && !isVehicle(); }
    @Nullable
    public ResourceCost getCost() {return ResourceCosts.GYROCOPTER_GUNSHIP;}

    public void setAttackMoveTarget(@Nullable BlockPos bp) { this.attackMoveTarget = bp; }
    public void setFollowTarget(@Nullable LivingEntity target) { this.followTarget = target; }

    // endregion

    // a flyer by the Phoenix (80 HP, 3 dps): 5 a round every 1.7 s from 8 blocks, 90 HP; the strafing run is its
    // burst - 8 to every enemy on a 10-block line every 8 s - and the heat that run costs is what grounds its guns
    final static public float attackDamage = 5.0f;
    final static public float attacksPerSecond = 0.6f;
    final static public float attackRange = 8.0f;
    final static public float aggroRange = 10.0f;
    final static public boolean willRetaliate = true;
    final static public boolean aggressiveWhenIdle = true;

    final static public float maxHealth = 90.0f;
    final static public float armorValue = 0.0f;
    final static public float movementSpeed = 0.32f;

    /** The strafing run: how often, how far it lunges, how wide the raked line is, what it does and costs. */
    public static final int STRAFE_COOLDOWN_TICKS = 160;
    public static final double STRAFE_LENGTH = 10.0;
    public static final double STRAFE_WIDTH = 1.75;
    public static final float STRAFE_DAMAGE = 8.0f;
    public static final int STRAFE_HEAT = 20;
    static final int ATTACK_WINDUP_TICKS = 4;

    private int strafeCooldown = 0;
    private static final List<LivingEntity> scratch = new ArrayList<>();   // run hit query, reused (server thread only)

    public int maxResources = 100;

    public int fogRevealDuration = 0; // set > 0 for the client who is attacked by this unit
    public int getFogRevealDuration() { return fogRevealDuration; }
    public void setFogRevealDuration(int duration) { fogRevealDuration = duration; }

    private UnitRangedAttackGoal<? extends LivingEntity> attackGoal;

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();


    public GyrocopterGunshipUnit(EntityType<? extends Parrot> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new FlyingUnitMoveControl(this);
        this.navigation = new FlyingPathNavigation(this, level());
        this.setVariant(Parrot.Variant.GRAY);   // ignored by the renderer (one skin), kept so the variant never rolls
        this.setSilent(true);
        updateAbilityButtons();
    }

    public boolean canAttackBuildings() { return false; }
    public Goal getAttackBuildingGoal() { return null; }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.MOVEMENT_SPEED, GyrocopterGunshipUnit.movementSpeed)
                .add(Attributes.FLYING_SPEED, GyrocopterGunshipUnit.movementSpeed)
                .add(Attributes.MAX_HEALTH, GyrocopterGunshipUnit.maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, GyrocopterGunshipUnit.armorValue)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    /** Overclocked machines fire faster (brass/Heat). */
    @Override
    public float getAttackCooldownMultiplier() {
        return AttackerUnit.super.getAttackCooldownMultiplier() * com.solegendary.reignofnether.brass.Heat.cooldownScale(this);
    }

    @Override
    public LivingEntity getTarget() {
        return this.targetGoal == null ? null : this.targetGoal.getTarget();
    }

    public void tick() {
        this.setCanPickUpLoot(false);
        super.tick();
        Unit.tick(this);
        AttackerUnit.tick(this);
        com.solegendary.reignofnether.brass.Heat.tick(this, this);
        if (strafeCooldown > 0)
            strafeCooldown--;
    }

    // no taming, no cookies: an RTS unit answers to its owner's orders only
    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean isFood(@NotNull ItemStack stack) { return false; }

    /** The bee's flight (BeeUnit.travel): no gravity, so the gunship hovers where its last order left it. */
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

    // range bonus an attacker gets against this flyer so it can't hover out of reach (the Thunder Dragon's rule)
    public double getAttackerRangeBonus(Mob attacker) {
        Vec2 attackerPos = new Vec2((float) attacker.getX(), (float) attacker.getZ());
        Vec2 myPos = new Vec2((float) this.getX(), (float) this.getZ());
        double horizDist = Math.sqrt(attackerPos.distanceToSqr(myPos));
        double vertiDist = Math.max(0, this.getY() - attacker.getY());
        if (horizDist < 4)
            return (int) (vertiDist * 0.5f);
        else
            return (int) (vertiDist * 0.25f);
    }

    @Override
    public void remove(@NotNull RemovalReason pReason) {
        if (this.level() instanceof ServerLevel serverLevel) {
            String command = this.getOnDeathCommand();
            if (command != null && !command.isEmpty()) {
                CommandSourceStack source;
                source = serverLevel.getServer()
                    .createCommandSourceStack()
                    .withEntity(this)
                    .withPosition(this.position())
                    .withLevel(serverLevel)
                    .withPermission(2);
                serverLevel.getServer().getCommands().performPrefixedCommand(source, command);
            }
        }
        super.remove(pReason);
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag pCompound) {
        super.addAdditionalSaveData(pCompound);
        this.addUnitSaveData(pCompound);
        com.solegendary.reignofnether.brass.Heat.save(this, pCompound);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag pCompound) {
        super.readAdditionalSaveData(pCompound);
        this.readUnitSaveData(pCompound);
        com.solegendary.reignofnether.brass.Heat.load(this, this, pCompound);
    }

    public void initialiseGoals() {
        this.usePortalGoal = new FlyingUsePortalGoal(this);
        this.moveGoal = new FlyingMoveToTargetGoal(this, 0);
        this.targetGoal = new SelectedTargetGoal<>(this, true, true);
        this.itemGoal = new UnitItemGoal(this);
        this.attackGoal = new UnitRangedAttackGoal<>(this, ATTACK_WINDUP_TICKS);
        this.returnResourcesGoal = new ReturnResourcesGoal(this);
    }

    @Override
    public void resetBehaviours() {
        if (this.attackGoal != null)
            this.attackGoal.stop();
    }

    @Override
    protected void registerGoals() {
        initialiseGoals();
        this.goalSelector.addGoal(2, usePortalGoal);
        this.goalSelector.addGoal(2, attackGoal);
        this.goalSelector.addGoal(2, returnResourcesGoal);
        this.targetSelector.addGoal(2, targetGoal);
        this.goalSelector.addGoal(3, moveGoal);
    }

    @Override
    public void performUnitRangedAttack(LivingEntity pTarget, float velocity) {
        if (pTarget == null || level().isClientSide() || this.hasEffect(MobEffectRegistrar.DISARM.get()))
            return;
        if (!com.solegendary.reignofnether.brass.Heat.canFire(this))
            return;   // venting: the goal keeps its cadence, the gun is silent
        if (strafeCooldown <= 0) {
            strafe(pTarget);
            strafeCooldown = STRAFE_COOLDOWN_TICKS;
            com.solegendary.reignofnether.brass.Heat.addHeat(this, this, STRAFE_HEAT);
        } else {
            fire(pTarget);
            com.solegendary.reignofnether.brass.Heat.onShot(this, this);
        }
        if (pTarget instanceof Unit unit)
            FogOfWarClientboundPacket.revealRangedUnit(unit.getOwnerName(), this.getId());
    }

    /** One round at {@code target}: a hit-scan projectile hit and its tracer. Public for the game test. */
    public boolean fire(LivingEntity target) {
        if (!(level() instanceof ServerLevel sl))
            return false;
        boolean hurt = target.hurt(damageSources().mobProjectile(this, this), getUnitAttackDamage());
        Vec3 from = new Vec3(getX(), getY() + 0.2, getZ());
        Vec3 to = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ());
        com.solegendary.reignofnether.barfx.BarFx.shot(sl, from, to, com.solegendary.reignofnether.barfx.BarFx.K_ARROW,
            com.solegendary.reignofnether.barfx.BarFx.W_GUN, this);   // the gun's report (sounds.SfxClient)
        return hurt;
    }

    /**
     * The strafing run through {@code target}: every enemy unit within {@link #STRAFE_WIDTH} of the flat line from the
     * gunship through the target (out to {@link #STRAFE_LENGTH}) takes {@link #STRAFE_DAMAGE} as a projectile hit, and
     * the gunship lunges along it (an impulse the bee travel bleeds off over ~{@link #STRAFE_LENGTH} blocks). Friends
     * are never hit. Returns how many it raked. Public for the game test.
     */
    public int strafe(LivingEntity target) {
        if (!(level() instanceof ServerLevel sl) || target == null)
            return 0;
        Vec3 start = new Vec3(getX(), 0, getZ());
        Vec3 flat = new Vec3(target.getX() - getX(), 0, target.getZ() - getZ());
        if (flat.lengthSqr() < 0.01)
            flat = Vec3.directionFromRotation(0, getYRot());
        Vec3 dir = new Vec3(flat.x, 0, flat.z).normalize();
        double midX = getX() + dir.x * STRAFE_LENGTH * 0.5, midZ = getZ() + dir.z * STRAFE_LENGTH * 0.5;
        int hits = 0;
        for (LivingEntity le : UnitGrid.near(sl, midX, midZ, STRAFE_LENGTH * 0.5 + STRAFE_WIDTH, scratch)) {
            if (le == this || !le.isAlive() || le.level() != sl || !(le instanceof Unit u))
                continue;
            String other = u.getOwnerName();
            if (getOwnerName().equals(other) || com.solegendary.reignofnether.alliance.AlliancesServerEvents.isAllied(getOwnerName(), other)
                    || UnitServerEvents.getUnitToEntityRelationship(this, le) == Relationship.FRIENDLY)
                continue;
            Vec3 rel = new Vec3(le.getX(), 0, le.getZ()).subtract(start);
            double along = rel.dot(dir);
            if (along < -0.5 || along > STRAFE_LENGTH)
                continue;
            double offSq = rel.lengthSqr() - along * along;
            if (offSq > STRAFE_WIDTH * STRAFE_WIDTH)
                continue;
            le.hurt(damageSources().mobProjectile(this, this), STRAFE_DAMAGE);
            Vec3 at = new Vec3(le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ());
            com.solegendary.reignofnether.barfx.BarFx.shot(sl, new Vec3(getX(), getY() + 0.2, getZ()), at,
                com.solegendary.reignofnether.barfx.BarFx.K_ARROW, com.solegendary.reignofnether.barfx.BarFx.W_GUN, this);
            hits++;
        }
        scratch.clear();
        setDeltaMovement(dir.x * STRAFE_LENGTH * 0.09, getDeltaMovement().y, dir.z * STRAFE_LENGTH * 0.09);
        sl.sendParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 6, 0.3, 0.2, 0.3, 0.05);
        sl.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 0.8f, 1.5f);
        return hits;
    }

    @Override
    public void setupEquipmentAndUpgradesServer() { }


    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor pLevel, DifficultyInstance pDifficulty, MobSpawnType pReason, @Nullable SpawnGroupData pSpawnData, @Nullable CompoundTag pDataTag) {
        return pSpawnData;
    }
}
