package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.barfx.BarFx;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientboundPacket;
import com.solegendary.reignofnether.mana.ManaServerEvents;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.Checkpoint;
import com.solegendary.reignofnether.unit.EnemySearchBehaviour;
import com.solegendary.reignofnether.unit.OnceRevive;
import com.solegendary.reignofnether.unit.controls.FlyingUnitMoveControl;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MiscUtil;

import com.github.alexthe666.alexsmobs.effect.AMEffectRegistry;
import com.github.alexthe666.alexsmobs.entity.EntitySunbird;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;

import net.minecraft.commands.CommandSourceStack;
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
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Stormcallers T2 flyer - the <b>Phoenix</b> (design/stormcallers_plan.md, slice 4; the T2 table): Alex's Mobs' sunbird
 * body (a single FlyingAnimal, no parts) in a red-gold fire-bird skin (PhoenixRenderer, tools/gen_storm_skins.py) on the
 * Thunder Dragon's flight rules: the bee's gravity-free flight, FlyingMoveToTargetGoal and FlyingUnitMoveControl, a
 * flyer to every AA unit.
 * <p>
 * Its attack is a <b>fire dive</b> every {@link #attacksPerSecond}-paced strike: {@link #attackDamage} as a mob
 * projectile (ranged armour applies, the Jade Mirror reflects it, a dome soaks it) plus {@link #BURN_SECONDS} s of
 * burning, a trail of flame along the dive; no fire block is ever placed (TidepoolServerEvents.evaporate at the target,
 * like every other fire attack). Each dive is a spell: {@link #ENERGY_PER_DIVE} energy through
 * {@link ManaServerEvents#channelShot}; a starved Phoenix dives at half rate like the Adepts.
 * <p>
 * <b>Rebirth</b> ({@link OnceRevive}): the first lethal hit is cancelled - no wreck, no kill credit - and the Phoenix
 * falls to the ground as an <b>ember</b> for {@link #EMBER_TICKS} ticks: speed 0, {@link #EMBER_HEALTH} HP, no attack,
 * deaf to every order but stop. Killing the ember denies the rebirth (an ordinary death: wreck, credit, blast). If it
 * lasts, it rises at {@link #REBIRTH_HEALTH_FRACTION} of its health and never again. The ember still counts as an alive
 * unit for selection, population and win checks: it is the same entity.
 * <p>
 * AM's sunbird is stripped: its goals, breeding and feeding are gone, its scorching aura (a slow-fall blessing on players
 * and a curse on whoever hits it) is switched off and scrubbed, its beacon-orbiting travel replaced by the bee's.
 */
public class PhoenixUnit extends EntitySunbird implements Unit, AttackerUnit, RangedAttackerUnit, OnceRevive.Revivable {
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

    private MoveToTargetBlockGoal moveGoal;
    private SelectedTargetGoal<? extends LivingEntity> targetGoal;

    public LivingEntity getFollowTarget() { return followTarget; }
    public boolean getHoldPosition() { return holdPosition; }
    public void setHoldPosition(boolean holdPosition) { this.holdPosition = holdPosition; }

    private LivingEntity followTarget = null; // if nonnull, continuously moves to the target
    private boolean holdPosition = false;
    private BlockPos attackMoveTarget = null;

    // which player owns this unit? this format ensures its synched to client without having to use packets
    public String getOwnerName() { return this.entityData.get(ownerDataAccessor); }
    public void setOwnerName(String name) { this.entityData.set(ownerDataAccessor, name); }
    public static final EntityDataAccessor<String> ownerDataAccessor =
            SynchedEntityData.defineId(PhoenixUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(PhoenixUnit.class, EntityDataSerializers.INT);

    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
            SynchedEntityData.defineId(PhoenixUnit.class, EntityDataSerializers.STRING);

    // the ember state, synced so the renderer can dim the bird
    public static final EntityDataAccessor<Boolean> emberAccessor =
            SynchedEntityData.defineId(PhoenixUnit.class, EntityDataSerializers.BOOLEAN);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ownerDataAccessor, "");
        this.entityData.define(scenarioRoleDataAccessor, -1);
        this.entityData.define(onDeathCommandDataAccessor, "");
        this.entityData.define(emberAccessor, false);
    }

    @Nullable
    public ResourceCost getCost() {return ResourceCosts.PHOENIX;}
    public boolean getWillRetaliate() {return willRetaliate;}
    public boolean getAggressiveWhenIdle() {return aggressiveWhenIdle && !isVehicle();}
    public BlockPos getAttackMoveTarget() { return attackMoveTarget; }
    public boolean canAttackBuildings() { return false; }   // its fire is for flesh; it leaves walls alone
    public Goal getAttackGoal() { return attackGoal; }
    public Goal getAttackBuildingGoal() { return null; }
    public void setAttackMoveTarget(@Nullable BlockPos bp) { this.attackMoveTarget = bp; }
    public void setFollowTarget(@Nullable LivingEntity target) { this.followTarget = target; }

    private EnemySearchBehaviour attackSearchBehaviour = EnemySearchBehaviour.NONE;
    public EnemySearchBehaviour getEnemySearchBehaviour() { return attackSearchBehaviour; }
    public void setEnemySearchBehaviour(EnemySearchBehaviour behaviour) { attackSearchBehaviour = behaviour; }

    public int fogRevealDuration = 0; // set > 0 for the client who is attacked by this unit
    public int getFogRevealDuration() { return fogRevealDuration; }
    public void setFogRevealDuration(int duration) { fogRevealDuration = duration; }

    private UnitRangedAttackGoal<? extends LivingEntity> attackGoal;

    // endregion

    // 6 + a 2 s burn every 2 s (3 dps + burn) on a quick 80 HP body: a flanker that comes back once, priced a little
    // under the Thunder Dragon
    final static public float attackDamage = 6.0f;
    final static public float attacksPerSecond = 0.5f;
    final static public float attackRange = 4.0f;
    final static public float aggroRange = 12.0f;
    final static public boolean willRetaliate = true;
    final static public boolean aggressiveWhenIdle = true;

    final static public float maxHealth = 80.0f;
    final static public float armorValue = 0.0f;
    final static public float movementSpeed = 0.34f;

    /** Energy per dive (a spell: ManaServerEvents.channelShot) and the burn it leaves. */
    public static final float ENERGY_PER_DIVE = 4f;
    public static final int BURN_SECONDS = 2;
    /** The ember: how long it smoulders, its health while it does, and the health it rises with. */
    public static final int EMBER_TICKS = 6 * 20;
    public static final float EMBER_HEALTH = 30f;
    public static final float REBIRTH_HEALTH_FRACTION = 0.5f;
    static final int ATTACK_WINDUP_TICKS = 6;
    static final java.util.UUID EMBER_SLOW_ID = java.util.UUID.fromString("5f4e9e2a-7c1b-4d7e-9a3c-2b6d8e1f0a11");
    static final AttributeModifier EMBER_SLOW = new AttributeModifier(EMBER_SLOW_ID, "phoenix ember", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL);

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    /** Attack cooldown scale from the last dive's payment: 1 paid, up to 2 starved (ManaServerEvents.shotCooldownScale). */
    private float manaCooldownScale = 1f;
    /** Game tick the ember rises again (server side; Long.MIN_VALUE = not an ember). */
    private long riseTick = Long.MIN_VALUE;

    public PhoenixUnit(EntityType<? extends EntitySunbird> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new FlyingUnitMoveControl(this);
        this.navigation = new FlyingPathNavigation(this, level);
        updateAbilityButtons();
    }

    @Override
    public boolean removeWhenFarAway(double d) { return false; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.FLYING_SPEED, movementSpeed)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, armorValue)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), 20)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    /** Starved strikes come at half rate: the scale of the last strike's payment on top of the usual effects. */
    @Override
    public float getAttackCooldownMultiplier() {
        return AttackerUnit.super.getAttackCooldownMultiplier() * manaCooldownScale;
    }

    // AM's sunbird curses whoever strikes it (a sunlight-burn effect) - a unit's attackers get no curse
    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && source.getEntity() instanceof LivingEntity attacker && attacker.hasEffect(AMEffectRegistry.SUNBIRD_CURSE.get()))
            attacker.removeEffect(AMEffectRegistry.SUNBIRD_CURSE.get());
        return hit;
    }

    // never scorching: AM's shine billboard and its burn-the-undead aura stay off
    @Override
    public boolean isScorching() { return false; }

    @Override
    public void setScorching(boolean scorching) { }

    @Override
    public float getDamageAfterMagicAbsorb(@NotNull DamageSource pSource, float pDamage) {
        pDamage = super.getDamageAfterMagicAbsorb(pSource, pDamage);
        if (MiscUtil.isMagicDamage(pSource))
            pDamage *= (1 - getUnitMagicArmorPercentage());
        return pDamage;
    }

    @Override
    public LivingEntity getTarget() {
        // an ember has no target: the attack goal never starts (goals only exist server side)
        return this.targetGoal == null || isEmber() ? null : this.targetGoal.getTarget();
    }

    // ---- the ember

    public boolean isEmber() { return this.entityData.get(emberAccessor); }

    /** Has this Phoenix already risen once (its next death is final)? */
    public boolean hasRisen() { return OnceRevive.isSpent(this); }

    @Override
    public float reviveHealth() { return EMBER_HEALTH; }

    /** The first lethal hit: fall as an ember (OnceRevive, server side). */
    @Override
    public void onCheatDeath(DamageSource source) {
        if (!(level() instanceof ServerLevel sl))
            return;
        this.entityData.set(emberAccessor, true);
        riseTick = sl.getGameTime() + EMBER_TICKS;
        AttributeInstance ms = getAttribute(Attributes.MOVEMENT_SPEED);
        if (ms != null && ms.getModifier(EMBER_SLOW_ID) == null)
            ms.addTransientModifier(EMBER_SLOW);
        if (moveGoal != null)
            moveGoal.stopMoving();
        if (targetGoal != null)
            targetGoal.setTarget(null);
        resetBehaviours();
        setDeltaMovement(0, -0.2, 0);
        sl.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.5, getZ(), 40, 0.6, 0.4, 0.6, 0.05);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.5, getZ(), 20, 0.6, 0.4, 0.6, 0.02);
        sl.playSound(null, blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 1.5f, 0.6f);
    }

    /** The ember rises: half health, its wings back. Public for the game test. */
    public void rise(ServerLevel sl) {
        if (!isEmber())
            return;
        this.entityData.set(emberAccessor, false);
        riseTick = Long.MIN_VALUE;
        AttributeInstance ms = getAttribute(Attributes.MOVEMENT_SPEED);
        if (ms != null)
            ms.removeModifier(EMBER_SLOW_ID);
        setHealth(Math.max(getHealth(), getMaxHealth() * REBIRTH_HEALTH_FRACTION));
        setDeltaMovement(0, 0.6, 0);
        sl.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.5, getZ(), 60, 0.8, 0.8, 0.8, 0.12);
        sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.0, getZ(), 12, 0.5, 0.5, 0.5, 0.05);
        sl.playSound(null, blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.NEUTRAL, 1.5f, 0.7f);
        sl.playSound(null, blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.NEUTRAL, 1.5f, 1.2f);
    }

    // an ember cannot be ordered about (stop still works): it lies where it fell
    @Override
    public boolean ignoreNonStopCommands() {
        return isEmber();
    }

    public void tick() {
        this.setCanPickUpLoot(false);
        float yRotBefore = this.getYRot();
        super.tick();
        Unit.tick(this);
        AttackerUnit.tick(this);
        updateRotation(yRotBefore);
        if (!(level() instanceof ServerLevel sl))
            return;
        if (isEmber()) {
            // on the ground, still: gravity-free flight would leave it hanging where it died
            if (!onGround())
                setDeltaMovement(0, Math.max(-0.5, getDeltaMovement().y - 0.08), 0);
            else
                setDeltaMovement(Vec3.ZERO);
            if (tickCount % 4 == 0)
                sl.sendParticles(ParticleTypes.SMALL_FLAME, getX(), getY() + 0.3, getZ(), 2, 0.4, 0.1, 0.4, 0.01);
            if (riseTick != Long.MIN_VALUE && sl.getGameTime() >= riseTick)
                rise(sl);
        }
        // AM's tick blesses players within ~15 blocks every 100 ticks (slow falling): scrub it the same tick
        if (tickCount % 100 == 0)
            for (net.minecraft.server.level.ServerPlayer p : sl.players())
                if (p.hasEffect(AMEffectRegistry.SUNBIRD_BLESSING.get()) && p.distanceToSqr(this) < 40 * 40)
                    p.removeEffect(AMEffectRegistry.SUNBIRD_BLESSING.get());
    }

    /** Faces the target while it has one, else where it is flying, else keeps its heading (the Corsair's rule). */
    void updateRotation(float yRotBefore) {
        LivingEntity target = this.getTarget();
        Vec3 dMove = this.getDeltaMovement();
        double x = 0, z = 0;
        if (target != null) {
            x = target.getX() - this.getX();
            z = target.getZ() - this.getZ();
        } else if (dMove.horizontalDistanceSqr() > 0.0025) {
            x = dMove.x();
            z = dMove.z();
        }
        if (Math.abs(x) > 0.05f || Math.abs(z) > 0.05f)
            this.setYRot(-((float) Mth.atan2(x, z)) * 57.295776F);
        else
            this.setYRot(yRotBefore);
        this.yBodyRot = this.getYRot();
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
        if (isEmber())
            pCompound.putInt("bt_ember_left", (int) Math.max(1, riseTick - level().getGameTime()));
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag pCompound) {
        super.readAdditionalSaveData(pCompound);
        this.readUnitSaveData(pCompound);
        // an ember that was saved keeps smouldering for what was left (its once-flag is in the persistent data)
        if (pCompound.contains("bt_ember_left")) {
            this.entityData.set(emberAccessor, true);
            riseTick = level().getGameTime() + pCompound.getInt("bt_ember_left");
            AttributeInstance ms = getAttribute(Attributes.MOVEMENT_SPEED);
            if (ms != null && ms.getModifier(EMBER_SLOW_ID) == null)
                ms.addTransientModifier(EMBER_SLOW);
        }
    }

    // no tempting, feeding or breeding: an RTS unit answers to its owner's orders only
    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean isFood(@NotNull ItemStack stack) { return false; }

    @Override
    protected void pickUpItem(@NotNull net.minecraft.world.entity.item.ItemEntity pItemEntity) { }

    // a bird of fire: quiet in flight, a blaze's cries when hurt
    @Override
    protected SoundEvent getAmbientSound() { return null; }
    @Override
    protected SoundEvent getHurtSound(@NotNull DamageSource source) { return SoundEvents.BLAZE_HURT; }
    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.BLAZE_DEATH; }

    /** The bee's flight (BeeUnit.travel): no gravity, so the dragon hovers where its last order left it. */
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

    public void initialiseGoals() {
        this.usePortalGoal = new FlyingUsePortalGoal(this);
        this.moveGoal = new FlyingMoveToTargetGoal(this, 0);
        this.targetGoal = new SelectedTargetGoal<>(this, true, true);
        this.attackGoal = new UnitRangedAttackGoal<>(this, ATTACK_WINDUP_TICKS);
    }

    @Override
    protected void registerGoals() {
        initialiseGoals();
        this.goalSelector.addGoal(2, usePortalGoal);
        this.goalSelector.addGoal(2, attackGoal);
        this.targetSelector.addGoal(2, targetGoal);
        this.goalSelector.addGoal(3, moveGoal);
    }

    @Override
    public void resetBehaviours() {
        if (this.attackGoal != null)
            this.attackGoal.stop();
    }

    @Override
    public void performUnitRangedAttack(LivingEntity pTarget, float velocity) {
        if (pTarget == null || level().isClientSide() || isEmber() || this.hasEffect(MobEffectRegistrar.DISARM.get()))
            return;
        if (dive(pTarget) && pTarget instanceof Unit unit)
            FogOfWarClientboundPacket.revealRangedUnit(unit.getOwnerName(), this.getId());
    }

    /**
     * One fire dive at {@code target}: pays its energy (setting the next dive's pace), hits, burns, evaporates tidepool
     * water at the target and draws the dive. Returns whether the hit landed. Public for the game test.
     */
    public boolean dive(LivingEntity target) {
        if (!(level() instanceof ServerLevel sl) || isEmber())
            return false;
        // the dive is a spell: what the pool can pay sets the pace of the next one (starved = half rate). The goal
        // re-reads the cooldown from getAttackCooldown, so resetting it now applies the new scale to this very wait
        manaCooldownScale = ManaServerEvents.shotCooldownScale(ManaServerEvents.channelShot(this, getOwnerName(), ENERGY_PER_DIVE));
        if (attackGoal != null)
            attackGoal.setToMaxAttackCooldown();
        // a projectile hit with the Phoenix as both projectile and owner: the mixin takes the damage from
        // getUnitAttackDamage and applies ranged armour
        boolean hurt = target.hurt(damageSources().mobProjectile(this, this), getUnitAttackDamage());
        if (hurt && target.isAlive())
            target.setSecondsOnFire(BURN_SECONDS);   // the entity burns; no fire block is ever placed
        Vec3 from = new Vec3(getX(), getY() + getBbHeight() * 0.5, getZ());
        Vec3 to = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ());
        BarFx.shot(sl, from, to, BarFx.K_FIREBALL, this);
        BarFx.impact(sl, to, to.subtract(from), BarFx.K_FIREBALL, BarFx.AT_UNIT, 0.6f, this);
        sl.sendParticles(ParticleTypes.FLAME, to.x, to.y, to.z, 6, 0.3, 0.3, 0.3, 0.03);
        com.solegendary.reignofnether.tide.TidepoolServerEvents.evaporate(sl, target.blockPosition(), 1);
        sl.playSound(null, blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.NEUTRAL, 0.8f, 1.2f);
        return hurt;
    }

    // range bonus an attacker gets against this flyer so it can't hover out of reach (the Bee's rule: half the height
    // when nearly under it, a quarter otherwise) - archers can answer it, melee can't, AA does it best
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
    public void setupEquipmentAndUpgradesClient() { }

    @Override
    public void setupEquipmentAndUpgradesServer() { }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor pLevel, @NotNull DifficultyInstance pDifficulty, @NotNull MobSpawnType pReason, @Nullable SpawnGroupData pSpawnData, @Nullable CompoundTag pDataTag) {
        return pSpawnData;
    }
}
