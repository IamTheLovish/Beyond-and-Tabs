package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.barfx.LightningFx;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientboundPacket;
import com.solegendary.reignofnether.mana.ManaServerEvents;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.Checkpoint;
import com.solegendary.reignofnether.unit.EnemySearchBehaviour;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.controls.FlyingUnitMoveControl;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.WindAntiAir;
import com.solegendary.reignofnether.ability.abilities.Tempest;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.mana.TempestServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MiscUtil;

import com.github.alexthe666.alexsmobs.entity.EntitySpectre;
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
import net.minecraft.tags.DamageTypeTags;
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
 * Stormcallers T3 experimental - the <b>Celestial Dragon</b>: the <b>Air caster</b> (claude/t3-roles.md, section 4;
 * design/stormcallers_plan.md, slice 4): a storm serpent, an air unit. Interim body: Alex's Mobs' spectre at twice the
 * Thunder Dragon's size with a serpent tail of our own (CelestialDragonRenderer: the spectre model under a
 * jade-and-gold skin, tools/gen_storm_skins.py, plus a tail layer), on the Thunder Dragon's flight rules. AM's void
 * worm has the right shape but is a boss with multipart children - not before a spike (plan section 7).
 * <p>
 * Already fast, it <i>becomes</i> the storm. Its attack is a <b>sky bolt</b> every {@link #attacksPerSecond}-paced
 * strike: {@link #attackDamage} of indirect magic (RoN's indirectMagic: the Ward softens it, the Jade Mirror does not
 * reflect it) drawn with {@link LightningFx#bolt} - and the bolt <b>chains</b>: up to {@link #CHAIN_MAX} more enemies
 * within {@link #CHAIN_RADIUS} of the one struck take x{@link #CHAIN_FRACTION} of it ({@link #strike}). Its hits land
 * on groups; a spread army takes one bolt. {@link #ENERGY_PER_STRIKE} energy a strike through
 * {@link ManaServerEvents#channelShot}, half rate starved: the Stormcaller player manages the pool to keep it casting.
 * Its power is <b>Tempest</b> ({@link Tempest}, {@link TempestServerEvents}): a roaming storm paid up front and by the
 * second. Countered by wind-type anti-air that grounds it, by starving it of energy (raid the generators), and by
 * spreading out of the chain. It beats clumped armies and everything without anti-air.
 * <p>
 * <b>Grounded by wind</b>: damage from {@link WindAntiAir} units (the Wind Monk, the Windcaller's gust, the Wisp Choir,
 * the Flak Swivel) accumulates in a {@link #WIND_WINDOW_TICKS} window; {@link #WIND_THRESHOLD} of it grounds the dragon
 * for {@link #GROUNDED_TICKS} ticks ({@link #ground}): it sinks to the ground (melee can reach it: {@link #isFlyingUnit}
 * is false while down), takes {@link #GROUNDED_DAMAGE_MULT}x damage, moves at half speed and cannot cast. Telegraph:
 * the storm-cloud motes that trail it thin out as the wind builds, and are gone at the threshold.
 * <p>
 * T3 rules (design-factions.md): the standard experimental price; loses to an equal-cost T2 army with wind-type AA;
 * no instant win. Arrival announced to all.
 */
public class CelestialDragonUnit extends EntitySpectre implements Unit, AttackerUnit, RangedAttackerUnit {
    public static final Abilities ABILITIES = new Abilities();
    static {
        ABILITIES.add(new Tempest(), Keybindings.abilitySlot1);
    }

    //region
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
            SynchedEntityData.defineId(CelestialDragonUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(CelestialDragonUnit.class, EntityDataSerializers.INT);

    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
            SynchedEntityData.defineId(CelestialDragonUnit.class, EntityDataSerializers.STRING);

    // grounded by wind, synced so the HUD button greys and the renderer can droop the serpent
    public static final EntityDataAccessor<Boolean> groundedAccessor =
            SynchedEntityData.defineId(CelestialDragonUnit.class, EntityDataSerializers.BOOLEAN);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ownerDataAccessor, "");
        this.entityData.define(scenarioRoleDataAccessor, -1);
        this.entityData.define(onDeathCommandDataAccessor, "");
        this.entityData.define(groundedAccessor, false);
    }

    @Nullable
    public ResourceCost getCost() {return ResourceCosts.CELESTIAL_DRAGON;}
    public boolean getWillRetaliate() {return willRetaliate;}
    public boolean getAggressiveWhenIdle() {return aggressiveWhenIdle && !isVehicle();}
    public BlockPos getAttackMoveTarget() { return attackMoveTarget; }
    public boolean canAttackBuildings() { return false; }   // lightning finds units; it leaves walls alone
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

    // 20 every 2.5 s (8 dps, the T3 table's band) at 8 blocks on 1100 HP: a big body in the air that archers and AA can
    // reach, bought for its Tempest; the standard experimental price (ReignOfNetherCommonConfigs)
    final static public float attackDamage = 20.0f;
    final static public float attacksPerSecond = 0.4f;
    final static public float attackRange = 8.0f;
    final static public float aggroRange = 12.0f;
    final static public boolean willRetaliate = true;
    final static public boolean aggressiveWhenIdle = true;

    final static public float maxHealth = 1050.0f;   // 1100 -> 1050: no two T3s share an HP total (lovish, Oct 20); a caster in the air lives by its range and speed, not its body
    final static public float armorValue = 0.0f;
    final static public float movementSpeed = 0.28f;

    /**
     * Energy per sky bolt (a spell: ManaServerEvents.channelShot). 5 -> 15 with the chain: a bolt now hits a group, and
     * 6 energy a second sustained (half a wind generator per dragon) is a pool the Stormcaller player has to keep up -
     * starved, it fires at half rate, never silent.
     */
    public static final float ENERGY_PER_STRIKE = 15f;
    /** The chain: how many more enemies a bolt jumps to, how far from the one struck, and for what share of the bolt. */
    public static final int CHAIN_MAX = 3;
    public static final double CHAIN_RADIUS = 5.0;
    public static final float CHAIN_FRACTION = 0.5f;
    /** What the last strike did (game test): the enemies its chain reached. */
    public int lastChainHits = 0;
    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused (server thread)
    /** Wind damage that grounds it, the window it is counted in, how long it stays down and what it suffers there. */
    public static final float WIND_THRESHOLD = 40f;
    public static final int WIND_WINDOW_TICKS = 5 * 20;
    public static final int GROUNDED_TICKS = 8 * 20;
    public static final float GROUNDED_DAMAGE_MULT = 1.5f;
    static final int ATTACK_WINDUP_TICKS = 10;
    static final int CLOUD_FX_TICKS = 5;
    static final String KEY_ANNOUNCED = "bt_t3_announced";
    static final java.util.UUID GROUNDED_SLOW_ID = java.util.UUID.fromString("a2c8d1e4-3f5b-4e6a-8c7d-9e0f1a2b3c4d");
    static final AttributeModifier GROUNDED_SLOW = new AttributeModifier(GROUNDED_SLOW_ID, "celestial dragon grounded", -0.5, AttributeModifier.Operation.MULTIPLY_TOTAL);

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    /** Attack cooldown scale from the last strike's payment: 1 paid, up to 2 starved (ManaServerEvents.shotCooldownScale). */
    private float manaCooldownScale = 1f;
    /** Wind damage taken in the current window, when the window opened, and until when it is grounded (server side). */
    private float windDamage = 0f;
    private long windWindowStart = Long.MIN_VALUE;
    private long groundedUntil = Long.MIN_VALUE;

    public CelestialDragonUnit(EntityType<? extends EntitySpectre> entityType, Level level) {
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
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), 24)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    /** Starved strikes come at half rate: the scale of the last strike's payment on top of the usual effects. */
    @Override
    public float getAttackCooldownMultiplier() {
        return AttackerUnit.super.getAttackCooldownMultiplier() * manaCooldownScale;
    }

    // AM's spectre is "invulnerable to everything but magic"; a dragon takes arrows and gusts like any other flyer
    // (Entity's own rule: only the invulnerable flag, bypassed by the void and creative players)
    @Override
    public boolean isInvulnerableTo(@NotNull DamageSource source) {
        return this.isRemoved() || (this.isInvulnerable() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.isCreativePlayer());
    }

    /**
     * Wind-type hits build toward a grounding ({@link #WIND_THRESHOLD} within {@link #WIND_WINDOW_TICKS}); a grounded
     * dragon takes {@link #GROUNDED_DAMAGE_MULT}x from everything.
     */
    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (isGrounded() && !level().isClientSide())
            amount *= GROUNDED_DAMAGE_MULT;
        boolean hit = super.hurt(source, amount);
        // only hits that landed count (not those inside the invulnerability window)
        if (hit && !level().isClientSide() && amount > 0 && source.getEntity() instanceof WindAntiAir)
            windHit(amount);
        return hit;
    }

    /** Counts a wind hit toward the threshold (the window restarts once it lapses). Public for the game test. */
    public void windHit(float amount) {
        long now = level().getGameTime();
        if (windWindowStart == Long.MIN_VALUE || now - windWindowStart > WIND_WINDOW_TICKS) {
            windWindowStart = now;
            windDamage = 0f;
        }
        windDamage += amount;
        if (windDamage >= WIND_THRESHOLD && !isGrounded())
            ground();
    }

    /** How close the wind is to grounding it, 0-1 (the cloud telegraph thins with this). */
    public float windFraction() {
        if (isGrounded() || windWindowStart == Long.MIN_VALUE || level().getGameTime() - windWindowStart > WIND_WINDOW_TICKS)
            return 0f;
        return Math.min(1f, windDamage / WIND_THRESHOLD);
    }

    public boolean isGrounded() { return this.entityData.get(groundedAccessor); }

    /** Brings the dragon down for {@link #GROUNDED_TICKS}: half speed, +50% damage taken, no Tempest. Public for the bot and the game test. */
    public void ground() {
        if (!(level() instanceof ServerLevel sl))
            return;
        this.entityData.set(groundedAccessor, true);
        groundedUntil = sl.getGameTime() + GROUNDED_TICKS;
        windDamage = 0f;
        windWindowStart = Long.MIN_VALUE;
        AttributeInstance ms = getAttribute(Attributes.MOVEMENT_SPEED);
        if (ms != null && ms.getModifier(GROUNDED_SLOW_ID) == null)
            ms.addTransientModifier(GROUNDED_SLOW);
        setDeltaMovement(getDeltaMovement().x, -0.3, getDeltaMovement().z);
        sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + getBbHeight() * 0.5, getZ(), 40, 1.5, 0.6, 1.5, 0.08);
        sl.playSound(null, blockPosition(), SoundEvents.PHANTOM_HURT, SoundSource.NEUTRAL, 2.0f, 0.5f);
        sl.playSound(null, blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.NEUTRAL, 1.5f, 0.6f);
    }

    /** Back into the air. */
    void takeOff(ServerLevel sl) {
        this.entityData.set(groundedAccessor, false);
        groundedUntil = Long.MIN_VALUE;
        AttributeInstance ms = getAttribute(Attributes.MOVEMENT_SPEED);
        if (ms != null)
            ms.removeModifier(GROUNDED_SLOW_ID);
        setDeltaMovement(getDeltaMovement().x, 0.5, getDeltaMovement().z);
        sl.sendParticles(ParticleTypes.CLOUD, getX(), getY(), getZ(), 20, 1.0, 0.3, 1.0, 0.05);
        sl.playSound(null, blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.NEUTRAL, 2.0f, 0.7f);
    }

    // a grounded dragon is no flyer: melee reaches it (the flying move goal below ignores this and keeps it walking)
    @Override
    public boolean isFlyingUnit() {
        return !isGrounded();
    }

    @Override
    public float getDamageAfterMagicAbsorb(@NotNull DamageSource pSource, float pDamage) {
        pDamage = super.getDamageAfterMagicAbsorb(pSource, pDamage);
        if (MiscUtil.isMagicDamage(pSource))
            pDamage *= (1 - getUnitMagicArmorPercentage());
        return pDamage;
    }

    @Override
    public LivingEntity getTarget() {
        return this.targetGoal == null ? null : this.targetGoal.getTarget();
    }

    public void tick() {
        this.setCanPickUpLoot(false);
        float yRotBefore = this.getYRot();
        super.tick();
        // the spectre's tick faces its drift and ghosts through blocks: a hovering dragon keeps its heading and flies
        // round hills like the Corsair
        this.noPhysics = false;
        this.lurePos = null;
        Unit.tick(this);
        AttackerUnit.tick(this);
        updateRotation(yRotBefore);
        if (!(level() instanceof ServerLevel sl) || !isAlive())
            return;
        String owner = getOwnerName();
        if (tickCount > 5 && !getPersistentData().getBoolean(KEY_ANNOUNCED) && owner != null && !owner.isEmpty()) {
            getPersistentData().putBoolean(KEY_ANNOUNCED, true);
            com.solegendary.reignofnether.unit.T3ServerEvents.announceFielded(owner,
                net.minecraft.network.chat.Component.translatable("entity.reignofnether.celestial_dragon_unit"));
        }
        if (isGrounded()) {
            // down on the ground: the flying move control still pushes toward its altitude, so any climb is cancelled
            // and gravity pulls it to the floor; it walks (half speed) where it was going
            Vec3 dm = getDeltaMovement();
            if (!onGround())
                setDeltaMovement(dm.x, Math.max(-0.5, Math.min(0, dm.y) - 0.08), dm.z);
            else if (dm.y > 0)
                setDeltaMovement(dm.x, 0, dm.z);
            if (tickCount % 5 == 0)
                sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.5, getZ(), 3, 1.0, 0.3, 1.0, 0.01);
            if (sl.getGameTime() >= groundedUntil)
                takeOff(sl);
        } else if (tickCount % CLOUD_FX_TICKS == 0) {
            // the storm cloud that trails it: four motes at rest, thinning to none as the wind nears the threshold
            int motes = Math.round(4 * (1f - windFraction()));
            if (motes > 0)
                sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + getBbHeight() * 0.7, getZ(), motes, 1.2, 0.3, 1.2, 0.0);
        }
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
            TempestServerEvents.Storm storm = TempestServerEvents.stormOf(this);
            if (storm != null)
                TempestServerEvents.dispel(storm);   // the storm dies with its caster (tickStorm would end it next tick anyway)
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
        if (isGrounded())
            pCompound.putInt("bt_grounded_left", (int) Math.max(1, groundedUntil - level().getGameTime()));
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag pCompound) {
        super.readAdditionalSaveData(pCompound);
        this.readUnitSaveData(pCompound);
        if (pCompound.contains("bt_grounded_left")) {
            this.entityData.set(groundedAccessor, true);
            groundedUntil = level().getGameTime() + pCompound.getInt("bt_grounded_left");
            AttributeInstance ms = getAttribute(Attributes.MOVEMENT_SPEED);
            if (ms != null && ms.getModifier(GROUNDED_SLOW_ID) == null)
                ms.addTransientModifier(GROUNDED_SLOW);
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

    // a serpent of the storm, not a ghost: thunder when it strikes, a phantom's cries when hurt
    @Override
    protected SoundEvent getAmbientSound() { return null; }
    @Override
    protected SoundEvent getHurtSound(@NotNull DamageSource source) { return SoundEvents.PHANTOM_HURT; }
    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.PHANTOM_DEATH; }

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
        // a grounded dragon reports itself as no flyer (so melee can reach it) but still walks to its orders
        this.moveGoal = new FlyingMoveToTargetGoal(this, 0) {
            @Override
            public boolean canUse() {
                return moveTarget != null;
            }
        };
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
        if (pTarget == null || level().isClientSide() || this.hasEffect(MobEffectRegistrar.DISARM.get()))
            return;
        if (strike(pTarget) && pTarget instanceof Unit unit)
            FogOfWarClientboundPacket.revealRangedUnit(unit.getOwnerName(), this.getId());
    }

    /**
     * One sky bolt at {@code target}: pays its energy (setting the next strike's pace), then the bolt, then the chain
     * - up to {@link #CHAIN_MAX} other hostile units within {@link #CHAIN_RADIUS} of the target (nearest first) take
     * x{@link #CHAIN_FRACTION} of the bolt. Returns whether the bolt hurt its target. Public for the game test.
     */
    public boolean strike(LivingEntity target) {
        if (!(level() instanceof ServerLevel sl) || target == null || !target.isAlive())
            return false;
        // the strike is a spell: what the pool can pay sets the pace of the next one (starved = half rate). The goal
        // re-reads the cooldown from getAttackCooldown, so resetting it now applies the new scale to this very wait
        manaCooldownScale = ManaServerEvents.shotCooldownScale(ManaServerEvents.channelShot(this, getOwnerName(), ENERGY_PER_STRIKE));
        if (attackGoal != null)
            attackGoal.setToMaxAttackCooldown();
        double x1 = target.getX(), y1 = target.getY() + target.getBbHeight() * 0.6, z1 = target.getZ();
        LightningFx.bolt(sl, getX(), getY() + getBbHeight() * 0.5, getZ(), x1, y1, z1);
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, x1, y1, z1, 8, 0.3, 0.3, 0.3, 0.1);
        // indirect magic, not mobAttack: RoN rewrites mob-attack damage to the attacker's melee damage
        float dmg = getUnitAttackDamage();
        boolean hurt = target.hurt(sl.damageSources().indirectMagic(this, this), dmg);
        sl.playSound(null, blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.NEUTRAL, 0.8f, 1.2f);
        lastChainHits = chain(sl, target, x1, y1, z1, dmg * CHAIN_FRACTION);
        return hurt;
    }

    /**
     * The chain from a struck unit: the nearest {@link #CHAIN_MAX} other hostile units within {@link #CHAIN_RADIUS}
     * of it each take {@code dmg}, a bolt drawn from the struck unit to each. Returns how many it reached.
     */
    int chain(ServerLevel sl, LivingEntity struck, double x1, double y1, double z1, float dmg) {
        String owner = getOwnerName();
        if (owner == null || owner.isEmpty() || dmg <= 0)
            return 0;
        List<LivingEntity> links = new ArrayList<>(CHAIN_MAX);
        double r2 = CHAIN_RADIUS * CHAIN_RADIUS;
        // UnitGrid hands back a shared candidate list; the picks are copied out before anything is hurt
        for (LivingEntity le : UnitGrid.near(sl, struck.getX(), struck.getZ(), CHAIN_RADIUS, scratch)) {
            if (le == struck || le == this || !le.isAlive() || le.level() != sl || !(le instanceof Unit u))
                continue;
            String o = u.getOwnerName();
            if (o == null || o.isEmpty() || o.equals(owner) || AlliancesServerEvents.isAllied(owner, o))
                continue;
            if (le.distanceToSqr(struck) > r2 || Math.abs(le.getY() - struck.getY()) > 4)
                continue;
            links.add(le);
        }
        scratch.clear();
        links.sort(java.util.Comparator.comparingDouble(le -> le.distanceToSqr(struck)));
        int n = 0;
        for (LivingEntity le : links) {
            if (n >= CHAIN_MAX)
                break;
            double x2 = le.getX(), y2 = le.getY() + le.getBbHeight() * 0.6, z2 = le.getZ();
            LightningFx.bolt(sl, x1, y1, z1, x2, y2, z2);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, x2, y2, z2, 4, 0.2, 0.2, 0.2, 0.08);
            le.hurt(sl.damageSources().indirectMagic(this, this), dmg);
            n++;
        }
        return n;
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
