package com.solegendary.reignofnether.unit.units.verdant;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.GaleWings;
import com.solegendary.reignofnether.ability.abilities.SeedRain;
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
 * Verdant Court T3 experimental, the second of the Heart of the Wild (slot T): the <b>Thornwing Roc</b>
 * (second-t3-design.md, slice T3b-7). A bark-feathered giant raptor: an AIR unit and the fastest Verdant body. The World
 * Tree Walker is the slow ground zoner; the Roc is the fast flyer that reaches what the Walker cannot.
 * <p>
 * It is built on the Celestial Dragon's flight (the Stormcallers' T3 flyer): Alex's Mobs' spectre body, the bee's
 * gravity-free flight ({@link #travel}), {@link FlyingMoveToTargetGoal}, {@link FlyingUnitMoveControl} and a
 * {@link FlyingPathNavigation} - a flyer to every anti-air unit, over walls and water. Ground and air units both
 * target it with the usual flyer range bonus ({@link #getAttackerRangeBonus}); it targets both ({@code SelectedTargetGoal(true, true)}).
 * <ul>
 *   <li><b>Feather Volley</b> (autocast, {@link #attackRange} blocks, ground and air): {@link #FEATHERS} feather bolts
 *   of {@link #FEATHER_DAMAGE}. The bolts home, so they land (an instant hit with a drawn trail, as the Thunder
 *   Dragon's lightning): the three arrive as one blow of their sum.</li>
 *   <li><b>Gale Wings</b> ({@link GaleWings}, tap, 20 s): a wing-beat cone knocks ground units back about 4 blocks and
 *   pushes flyers.</li>
 *   <li><b>Seed Rain</b> ({@link SeedRain}, tap, 35 s): a telegraphed 14-block seed line; after 1.5 s thickets root the
 *   walkers on it for 1 s and slow them for 3 s.</li>
 *   <li><b>Fire</b> burns the bark: every kind of fire hits it {@link #FIRE_MULTIPLIER}x (the Walker's counter).</li>
 * </ul>
 * Countered by anti-air (wind, wisps, flak), fire and archers; no bonus against buildings, so it cannot siege. Loses to
 * the equal-cost anti-air mix. Placeholder look: the Thunder Dragon's spectre model in a bark-brown skin at
 * {@link #SCALE}x (Lane B draws the real raptor, slice T3b-10). Arrival is announced to every player.
 */
public class ThornwingRocUnit extends EntitySpectre implements Unit, AttackerUnit, RangedAttackerUnit {
    public static final Abilities ABILITIES = new Abilities();
    static {
        ABILITIES.add(new GaleWings(), Keybindings.abilitySlot1);
        ABILITIES.add(new SeedRain(), Keybindings.abilitySlot2);
    }

    /** The production item's name (EntityRegistrar.getEntityType). */
    public static final String ITEM_NAME = "Thornwing Roc";

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
            SynchedEntityData.defineId(ThornwingRocUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(ThornwingRocUnit.class, EntityDataSerializers.INT);

    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
            SynchedEntityData.defineId(ThornwingRocUnit.class, EntityDataSerializers.STRING);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ownerDataAccessor, "");
        this.entityData.define(scenarioRoleDataAccessor, -1);
        this.entityData.define(onDeathCommandDataAccessor, "");
    }

    @Nullable
    public ResourceCost getCost() { return ResourceCosts.VERDANT_T3B; }
    public boolean getWillRetaliate() { return willRetaliate; }
    public boolean getAggressiveWhenIdle() { return aggressiveWhenIdle && !isVehicle(); }
    public BlockPos getAttackMoveTarget() { return attackMoveTarget; }
    public boolean canAttackBuildings() { return false; }   // feathers find units; no bonus against buildings (design)
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

    // ---- stats (second-t3-design.md section 3; the cost is VERDANT_T3B, the same as every T3: 1840M / 1740E, 20 pop)
    final static public float attackDamage = 12.0f;      // per volley: three feathers of 4 - 12 every 1.6 s = 7.5 DPS, just under the Sun Colossus' 7.7
    final static public float attacksPerSecond = 0.625f;
    final static public float maxHealth = 800.0f;        // the HP total kept for this flyer (the Zeppelin has 850)
    final static public float armorValue = 0.0f;
    final static public float movementSpeed = 0.38f;     // the fastest Verdant body: Stag Lancer 0.36, Fox Courier 0.34
    final static public float attackRange = 16;          // design: Feather Volley reaches 16
    final static public float aggroRange = 16;
    public static final int SIGHT_RANGE = 24;
    final static public boolean willRetaliate = true;
    final static public boolean aggressiveWhenIdle = true;

    /** Feather Volley: how many feathers and the damage of one. */
    public static final int FEATHERS = 3;
    public static final float FEATHER_DAMAGE = attackDamage / FEATHERS;
    /** Fire hits the bark this much harder (the World Tree Walker's counter). */
    public static final float FIRE_MULTIPLIER = 2.0f;
    /** The body: the spectre model drawn this many times (placeholder; the entity's box is sized to match in the registrar). */
    public static final float SCALE = 2.2f;
    static final int ATTACK_WINDUP_TICKS = 8;
    static final String KEY_ANNOUNCED = "bt_t3_announced";

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    public ThornwingRocUnit(EntityType<? extends EntitySpectre> entityType, Level level) {
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

    // AM's spectre is "invulnerable to everything but magic"; a Roc takes arrows and gusts like any other flyer
    @Override
    public boolean isInvulnerableTo(@NotNull DamageSource source) {
        return this.isRemoved() || (this.isInvulnerable() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.isCreativePlayer());
    }

    /** The counter: every kind of fire hits the bark {@link #FIRE_MULTIPLIER}x. */
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
        updateRotation(yRotBefore);
        if (!(level() instanceof ServerLevel sl) || !isAlive())
            return;
        String owner = getOwnerName();
        if (tickCount > 5 && !getPersistentData().getBoolean(KEY_ANNOUNCED) && owner != null && !owner.isEmpty()) {
            getPersistentData().putBoolean(KEY_ANNOUNCED, true);
            com.solegendary.reignofnether.unit.T3ServerEvents.announceFielded(owner,
                net.minecraft.network.chat.Component.translatable("entity.reignofnether.thornwing_roc_unit"));
        }
        // loose leaves off the bark feathers
        if (tickCount % 10 == 0)
            sl.sendParticles(ParticleTypes.FALLING_SPORE_BLOSSOM, getX(), getY() + getBbHeight() * 0.8, getZ(), 1, 0.8, 0.2, 0.8, 0.0);
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

    /** The horizontal unit vector the Roc faces: toward its target while it has one, else its heading. The abilities aim along it. */
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

    /** The height of the ground under the Roc (the abilities draw and test their lines there). */
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
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag pCompound) {
        super.readAdditionalSaveData(pCompound);
        this.readUnitSaveData(pCompound);
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

    /** The bee's flight (BeeUnit.travel): no gravity, so the Roc hovers where its last order left it. */
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
        this.targetGoal = new SelectedTargetGoal<>(this, true, true);   // ground and air
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
        if (volley(pTarget) > 0 && pTarget instanceof Unit unit)
            FogOfWarClientboundPacket.revealRangedUnit(unit.getOwnerName(), this.getId());
    }

    /**
     * Feather Volley at {@code target}: {@link #FEATHERS} homing feathers, each a drawn trail, landing as ONE blow of
     * their summed damage (the mod lets a unit be hit once per tick). Indirect magic, not mobAttack: RoN rewrites
     * mob-attack damage to the attacker's melee damage. Returns how many feathers landed (0 when the target is gone
     * or out of reach). Public for the game test.
     */
    public int volley(LivingEntity target) {
        if (!(level() instanceof ServerLevel sl) || target == null || !target.isAlive())
            return 0;
        double reach = attackRange * 1.5;
        if (target.distanceToSqr(this) > reach * reach)
            return 0;
        Vec3 from = new Vec3(getX(), getY() + getBbHeight() * 0.5, getZ());
        Vec3 to = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ());
        Vec3 side = facing();
        side = new Vec3(-side.z, 0, side.x);
        for (int i = 0; i < FEATHERS; i++) {
            double off = (i - (FEATHERS - 1) / 2.0) * 0.8;
            HeroFx.trail(sl, ParticleTypes.CRIT, from.add(side.scale(off)), to);
        }
        sl.playSound(null, blockPosition(), SoundEvents.PHANTOM_FLAP, net.minecraft.sounds.SoundSource.NEUTRAL, 1.2f, 1.6f);
        target.invulnerableTime = 0;
        target.hurt(sl.damageSources().indirectMagic(this, this), FEATHER_DAMAGE * FEATHERS);
        return FEATHERS;
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
