package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.BallastDump;
import com.solegendary.reignofnether.ability.abilities.CarpetBomb;
import com.solegendary.reignofnether.brass.Heat;
import com.solegendary.reignofnether.brass.Machine;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientboundPacket;
import com.solegendary.reignofnether.hero.HeroFx;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.Checkpoint;
import com.solegendary.reignofnether.unit.EnemySearchBehaviour;
import com.solegendary.reignofnether.unit.controls.FlyingUnitMoveControl;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
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
 * Brassworks T3 experimental, the second of the Great Forge (slot T): the <b>Brass Zeppelin</b>
 * (second-t3-design.md, slice T3b-8). A brass-ribbed airship with a gondola: an AIR unit, slow and armoured. The Iron
 * Leviathan Engine is the long-range ground siege; the Zeppelin is the bomber that reaches what it cannot, and the
 * Brassworks' answer to enemy flyers.
 * <p>
 * It is built on the Thornwing Roc's flight (itself the Celestial Dragon's): Alex's Mobs' spectre body, the bee's
 * gravity-free flight ({@link #travel}), {@link FlyingMoveToTargetGoal}, {@link FlyingUnitMoveControl} and a
 * {@link FlyingPathNavigation} - a flyer to every anti-air unit. Only the numbers differ: {@link #movementSpeed} is
 * the slowest of the flyers. It is a Brassworks {@link Machine} (Heat, the vent, the Tinker's Field Upgrade).
 * <ul>
 *   <li><b>Flak Burst</b> (its attack, automatic): a shrapnel burst of {@link #FLAK_DAMAGE} within {@link #FLAK_RADIUS}
 *   blocks of an enemy FLYER up to {@link #attackRange} blocks away. It only ever picks air targets by itself, and
 *   never hurts the ground.</li>
 *   <li><b>Carpet Bomb</b> ({@link CarpetBomb}, tap, 30 s, energy): a red 20-block line, then five bombs down it that hit
 *   ground units and buildings and no flyers.</li>
 *   <li><b>Ballast Dump</b> ({@link BallastDump}, tap, 40 s): 5 s of +40% speed, no firing, and the Engine's steam vent
 *   (+25% damage taken).</li>
 *   <li><b>Gas bag</b>: every kind of fire hits it {@link #FIRE_MULTIPLIER}x.</li>
 * </ul>
 * Countered by anti-air and flak, fast flyers and fire; weak because it is slow and high and every sortie costs energy.
 * Placeholder look: the Thunder Dragon's spectre model in a brass skin stretched into an airship at
 * {@link #SCALE_X} x {@link #SCALE_Y} x {@link #SCALE_Z} (Lane B draws the real airship, slice T3b-10). Arrival is announced to every player.
 */
public class BrassZeppelinUnit extends EntitySpectre implements Unit, AttackerUnit, RangedAttackerUnit, Machine {
    public static final Abilities ABILITIES = new Abilities();
    static {
        ABILITIES.add(new CarpetBomb(), Keybindings.abilitySlot1);
        ABILITIES.add(new BallastDump(), Keybindings.abilitySlot2);
    }

    /** The production item's name (EntityRegistrar.getEntityType). */
    public static final String ITEM_NAME = "Brass Zeppelin";

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
            SynchedEntityData.defineId(BrassZeppelinUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(BrassZeppelinUnit.class, EntityDataSerializers.INT);

    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
            SynchedEntityData.defineId(BrassZeppelinUnit.class, EntityDataSerializers.STRING);

    // ---- Brassworks Machine (brass/Heat): heat and the vent are synced for the steam, the upgrade for the render layer
    public static final EntityDataAccessor<Integer> heatDataAccessor =
            SynchedEntityData.defineId(BrassZeppelinUnit.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> ventDataAccessor =
            SynchedEntityData.defineId(BrassZeppelinUnit.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Byte> upgradeDataAccessor =
            SynchedEntityData.defineId(BrassZeppelinUnit.class, EntityDataSerializers.BYTE);
    private int overclockTicks = 0;
    @Override public int heat() { return this.entityData.get(heatDataAccessor); }
    @Override public void setHeat(int heat) { this.entityData.set(heatDataAccessor, heat); }
    @Override public int ventTicks() { return this.entityData.get(ventDataAccessor); }
    @Override public void setVentTicks(int ticks) { this.entityData.set(ventDataAccessor, ticks); }
    @Override public byte upgrade() { return this.entityData.get(upgradeDataAccessor); }
    @Override public void setUpgrade(byte upgrade) { this.entityData.set(upgradeDataAccessor, upgrade); }
    @Override public int overclockTicks() { return overclockTicks; }
    @Override public void setOverclockTicks(int ticks) { overclockTicks = ticks; }
    /** The flak never heats the Zeppelin: its only vent is the Ballast Dump's. */
    @Override public int heatPerShot() { return 0; }
    @Override public boolean isRangedMachine() { return true; }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(heatDataAccessor, 0);
        this.entityData.define(ventDataAccessor, 0);
        this.entityData.define(upgradeDataAccessor, (byte) 0);
        this.entityData.define(ownerDataAccessor, "");
        this.entityData.define(scenarioRoleDataAccessor, -1);
        this.entityData.define(onDeathCommandDataAccessor, "");
    }

    @Nullable
    public ResourceCost getCost() { return ResourceCosts.BRASS_T3B; }
    public boolean getWillRetaliate() { return willRetaliate; }
    public boolean getAggressiveWhenIdle() { return aggressiveWhenIdle && !isVehicle(); }
    public BlockPos getAttackMoveTarget() { return attackMoveTarget; }
    public boolean canAttackBuildings() { return false; }   // the flak is anti-air; the bombs (Carpet Bomb) are the building damage
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

    // ---- stats (second-t3-design.md section 3; the cost is BRASS_T3B, the same as every T3: 1800M / 200E, 20 pop)
    final static public float attackDamage = 14.0f;      // per burst: 14 every 2 s = 7 DPS against a flyer, just under the Sun Colossus' 7.7
    final static public float attacksPerSecond = 0.5f;
    final static public float maxHealth = 850.0f;        // the HP total reserved for the Zeppelin (the Zeppelin has 800)
    final static public float armorValue = 4.0f;         // armoured: a brass hull (the Engine has 6)
    final static public float movementSpeed = 0.16f;     // slow: the Engine walks at 0.15, the dragons fly at 0.28-0.3, the Zeppelin at 0.38
    final static public float attackRange = 18;          // design: Flak Burst reaches 18
    final static public float aggroRange = 18;
    public static final int SIGHT_RANGE = 24;
    final static public boolean willRetaliate = true;
    final static public boolean aggressiveWhenIdle = true;

    /** Flak Burst: the damage of one burst and its radius round the target. */
    public static final float FLAK_DAMAGE = attackDamage;
    public static final double FLAK_RADIUS = 3.0;
    /** Fire hits the gas bag this much harder (the design's +30%). */
    public static final float FIRE_MULTIPLIER = 1.3f;
    /** Ballast Dump: the speed bonus, as an attribute modifier that follows the vent. */
    static final java.util.UUID BALLAST_UUID = java.util.UUID.fromString("5c1e7a42-3b7d-4f0e-9a51-2d6b8c4e1a77");
    /** The body: the spectre model drawn this many times along its width, height and length (placeholder; the box is sized in the registrar). */
    public static final float SCALE_X = 2.2f, SCALE_Y = 1.8f, SCALE_Z = 3.0f;
    static final int ATTACK_WINDUP_TICKS = 8;
    static final String KEY_ANNOUNCED = "bt_t3_announced";

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    public BrassZeppelinUnit(EntityType<? extends EntitySpectre> entityType, Level level) {
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
                .add(AttributeRegistrar.SIGHT_RANGE.get(), SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    // AM's spectre is "invulnerable to everything but magic"; a Zeppelin takes arrows and gusts like any other flyer
    @Override
    public boolean isInvulnerableTo(@NotNull DamageSource source) {
        return this.isRemoved() || (this.isInvulnerable() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.isCreativePlayer());
    }

    /** The counter: every kind of fire hits the gas bag {@link #FIRE_MULTIPLIER}x. */
    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FIRE))
            amount *= FIRE_MULTIPLIER;
        return super.hurt(source, amount);
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
        // the spectre's tick faces its drift and ghosts through blocks: a hovering Roc keeps its heading and flies
        // round hills like the Corsair
        this.noPhysics = false;
        this.lurePos = null;
        Unit.tick(this);
        AttackerUnit.tick(this);
        Heat.tick(this, this);
        syncBallastSpeed();
        updateRotation(yRotBefore);
        if (!(level() instanceof ServerLevel sl) || !isAlive())
            return;
        String owner = getOwnerName();
        if (tickCount > 5 && !getPersistentData().getBoolean(KEY_ANNOUNCED) && owner != null && !owner.isEmpty()) {
            getPersistentData().putBoolean(KEY_ANNOUNCED, true);
            com.solegendary.reignofnether.unit.T3ServerEvents.announceFielded(owner,
                net.minecraft.network.chat.Component.translatable("entity.reignofnether.brass_zeppelin_unit"));
        }
        // the burners puff a little smoke every second
        if (tickCount % 20 == 0)
            sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + getBbHeight() * 0.9, getZ(), 1, 0.4, 0.1, 0.4, 0.01);
    }

    /** The Ballast Dump's speed, tied to the vent: on while it vents, off when it ends, restored after a reload. */
    void syncBallastSpeed() {
        boolean on = Heat.isVenting(this);
        for (var attr : new net.minecraft.world.entity.ai.attributes.Attribute[] { Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED }) {
            var inst = getAttribute(attr);
            if (inst == null)
                continue;
            boolean has = inst.getModifier(BALLAST_UUID) != null;
            if (on && !has)
                inst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(BALLAST_UUID, "ballast_dump",
                    BallastDump.SPEED_BONUS, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_BASE));
            else if (!on && has)
                inst.removeModifier(BALLAST_UUID);
        }
    }

    /** Applies the dump's speed at once (the next tick would do it too). Public for the Ballast Dump. */
    public void applyBallastSpeed() {
        syncBallastSpeed();
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

    /** The horizontal unit vector the Zeppelin faces: toward its target while it has one, else its heading. The Carpet Bomb aims along it. */
    public Vec3 facing() {
        LivingEntity t = getTarget();
        if (t != null) {
            double dx = t.getX() - getX(), dz = t.getZ() - getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0.05)
                return new Vec3(dx / len, 0, dz / len);
        }
        double yaw = Math.toRadians(getYRot());
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    /** The height of the ground under the Zeppelin (the Carpet Bomb draws its line there). */
    public double groundY() {
        BlockPos.MutableBlockPos bp = blockPosition().mutable();
        for (int i = 0; i < 48 && bp.getY() > level().getMinBuildHeight(); i++) {
            if (!level().getBlockState(bp).getCollisionShape(level(), bp).isEmpty())
                return bp.getY() + 1;
            bp.move(0, -1, 0);
        }
        return getY();
    }

    @Override
    public void remove(@NotNull RemovalReason pReason) {
        if (this.level() instanceof ServerLevel serverLevel) {
            String command = this.getOnDeathCommand();
            if (command != null && !command.isEmpty()) {
                CommandSourceStack source = serverLevel.getServer()
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
        Heat.save(this, pCompound);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag pCompound) {
        super.readAdditionalSaveData(pCompound);
        this.readUnitSaveData(pCompound);
        Heat.load(this, this, pCompound);
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

    // a raptor, not a ghost: a phantom's cries when hurt
    @Override
    protected SoundEvent getAmbientSound() { return null; }
    @Override
    protected SoundEvent getHurtSound(@NotNull DamageSource source) { return SoundEvents.PHANTOM_HURT; }
    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.PHANTOM_DEATH; }

    /** The bee's flight (BeeUnit.travel): no gravity, so the Zeppelin hovers where its last order left it. */
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
        this.targetGoal = new SelectedTargetGoal<>(this, true, true, e -> e instanceof Unit u && u.isFlyingUnit());   // picks FLYERS by itself: the flak is anti-air, the bombs are the player's
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
        if (flak(pTarget) > 0 && pTarget instanceof Unit unit)
            FogOfWarClientboundPacket.revealRangedUnit(unit.getOwnerName(), this.getId());
    }

    /**
     * Flak Burst at {@code target}: a shrapnel burst round it that hurts every enemy FLYER within {@link #FLAK_RADIUS}
     * blocks for {@link #FLAK_DAMAGE} (the target itself first). A ground target, a dead one, one out of reach, and a
     * venting Zeppelin (Ballast Dump: no firing) get nothing. Explosion damage, not mobAttack: RoN rewrites mob-attack
     * damage to the attacker's melee damage. Returns how many flyers it hurt. Public for the game test.
     */
    public int flak(LivingEntity target) {
        if (!(level() instanceof ServerLevel sl) || target == null || !target.isAlive() || !Heat.canFire(this))
            return 0;
        if (!(target instanceof Unit tu) || !tu.isFlyingUnit())
            return 0;
        double reach = attackRange * 1.5;
        if (target.distanceToSqr(this) > reach * reach)
            return 0;
        Vec3 from = new Vec3(getX(), getY() + getBbHeight() * 0.5, getZ());
        Vec3 centre = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ());
        HeroFx.trail(sl, ParticleTypes.SMOKE, from, centre);
        sl.sendParticles(ParticleTypes.EXPLOSION, centre.x, centre.y, centre.z, 2, 0.6, 0.6, 0.6, 0.0);
        sl.sendParticles(ParticleTypes.CRIT, centre.x, centre.y, centre.z, 10, FLAK_RADIUS * 0.4, FLAK_RADIUS * 0.4, FLAK_RADIUS * 0.4, 0.2);
        sl.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0f, 1.8f);
        int hit = 0;
        for (LivingEntity le : HeroFx.enemies(sl, this, centre, FLAK_RADIUS + 1)) {
            if (!(le instanceof Unit u) || !u.isFlyingUnit())
                continue;
            double dx = le.getX() - centre.x, dy = le.getY() + le.getBbHeight() / 2 - centre.y, dz = le.getZ() - centre.z;
            if (dx * dx + dy * dy + dz * dz > FLAK_RADIUS * FLAK_RADIUS)
                continue;
            le.invulnerableTime = 0;
            le.hurt(sl.damageSources().explosion(this, this), FLAK_DAMAGE);
            hit++;
        }
        return hit;
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
