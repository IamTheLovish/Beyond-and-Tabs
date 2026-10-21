package com.solegendary.reignofnether.unit.units.tide;

import com.github.alexthe666.alexsmobs.entity.EntityAlligatorSnappingTurtle;
import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.AttackGround;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientboundPacket;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.tide.TidalUnit;
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
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Tidewrought T2 artillery - the <b>Mortar Galley</b> (design/tidewrought_plan.md, slice 3): Alex's Mobs' alligator
 * snapping turtle grown {@link #SCALE}x (MortarGalleyRenderer over AM's RenderAlligatorSnappingTurtle) carrying a
 * mortar on its shell, on the RTS unit brain. Amphibious heavy artillery: the Bombard Crew's shell
 * ({@link BombardShell}: high arc, splash, dents enemy buildings, never breaks terrain, passes over friends) from
 * further away ({@link #attackRange} against 22) and harder, with a wider dead zone ({@link #MIN_RANGE}) and a slow
 * walk - a thick shell and 110 HP make it the line's anchor rather than its glass cannon. Attack Ground shells a spot.
 * <p>
 * The turtle's own habits are switched off as the Mudskipper Sloop's are: registerGoals replaces AM's brain wholesale
 * (lunging bite, waiting in ambush, water seeking, tempting, breeding), it cannot be fed, bred or sheared, and its
 * "wait with the mouth open" state is pinned off. AM's tick still snaps at an adjacent target (a cosmetic bite with
 * the mouth animation): TidesServerEvents cancels any hit whose direct source is the galley, so its only weapon is the
 * shell, and its chase timer is held at zero so AM never drops the target. AM's wall-climbing navigator is replaced by a plain ground one
 * (RoN's paths assume a walker), its water travel by the Sloop's tamer one, and {@link #getTarget} reads RoN's target
 * goal instead of AM's chase timer.
 */
public class MortarGalleyUnit extends EntityAlligatorSnappingTurtle implements Unit, AttackerUnit, RangedAttackerUnit, TidalUnit, com.solegendary.reignofnether.unit.interfaces.IndirectFire {
    final static public float attackRange = 30.0F;   // Bombard Crew 22, Ghast 30: it shells the line from far behind

    public static final Abilities ABILITIES = new Abilities();
    static {
        ABILITIES.add(new AttackGround(MortarGalleyUnit.attackRange), Keybindings.abilitySlot1);
    }

    public Goal getAttackGoal() { return attackGoal; }
    public Goal getAttackBuildingGoal() { return attackBuildingGoal; }
    public void setAttackMoveTarget(@Nullable BlockPos bp) { this.attackMoveTarget = bp; }
    public void setFollowTarget(@Nullable LivingEntity target) { this.followTarget = target; }

    private EnemySearchBehaviour attackSearchBehaviour = EnemySearchBehaviour.NONE;
    public EnemySearchBehaviour getEnemySearchBehaviour() { return attackSearchBehaviour; }
    public void setEnemySearchBehaviour(EnemySearchBehaviour behaviour) { attackSearchBehaviour = behaviour; }

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
    public ArrayList<Checkpoint> getCheckpoints() { return checkpoints; };

    public GarrisonGoal getGarrisonGoal() { return null; }
    public boolean canGarrison() { return getGarrisonGoal() != null; }

    UsePortalGoal usePortalGoal;
    public UsePortalGoal getUsePortalGoal() { return usePortalGoal; }
    public boolean canUsePortal() { return getUsePortalGoal() != null; }

	public Abilities getAbilities() {return abilities;}
    public List<ItemStack> getItems() {return items;};
    public MoveToTargetBlockGoal getMoveGoal() {return moveGoal;}
    public SelectedTargetGoal<? extends LivingEntity> getTargetGoal() {return targetGoal;}
    public ReturnResourcesGoal getReturnResourcesGoal() {return returnResourcesGoal;}
    public int getMaxResources() {return maxResources;}

    private MoveToTargetBlockGoal moveGoal;
    private SelectedTargetGoal<? extends LivingEntity> targetGoal;
    private ReturnResourcesGoal returnResourcesGoal;
    private UnitBowAttackGoal<? extends LivingEntity> attackGoal;
    private RangedAttackBuildingGoal<?> attackBuildingGoal;
    private RangedAttackGroundGoal<?> attackGroundGoal;
    @Override public RangedAttackGroundGoal<?> getRangedAttackGroundGoal() {
        return attackGroundGoal;
    }

    public LivingEntity getFollowTarget() { return followTarget; }
    public boolean getHoldPosition() { return holdPosition; }
    public void setHoldPosition(boolean holdPosition) { this.holdPosition = holdPosition; }

    // if true causes moveGoal and attackGoal to work together to allow attack moving
    // moves to a block but will chase/attack nearby monsters in range up to a certain distance away
    private LivingEntity followTarget = null; // if nonnull, continuously moves to the target
    private boolean holdPosition = false;
    private BlockPos attackMoveTarget = null;

    // which player owns this unit? this format ensures its synched to client without having to use packets
    public String getOwnerName() { return this.entityData.get(ownerDataAccessor); }
    public void setOwnerName(String name) { this.entityData.set(ownerDataAccessor, name); }
    public static final EntityDataAccessor<String> ownerDataAccessor =
            SynchedEntityData.defineId(MortarGalleyUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(MortarGalleyUnit.class, EntityDataSerializers.INT);

    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
        SynchedEntityData.defineId(MortarGalleyUnit.class, EntityDataSerializers.STRING);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ownerDataAccessor, "");
        this.entityData.define(scenarioRoleDataAccessor, -1);
        this.entityData.define(onDeathCommandDataAccessor, "");
    }

    @Nullable
    public ResourceCost getCost() {return ResourceCosts.MORTAR_GALLEY;}
    public boolean getWillRetaliate() {return willRetaliate;}
    public boolean getAggressiveWhenIdle() {return aggressiveWhenIdle && !isVehicle();}
    public BlockPos getAttackMoveTarget() { return attackMoveTarget; }
    public boolean canAttackBuildings() {return getAttackBuildingGoal() != null;}
    // endregion

    /** Body and hitbox scale over AM's wild turtle (1.25 x 0.65), so the galley reads as a heavy from the RTS camera. */
    public static final float SCALE = 1.5f;

    final static public float attackDamage = 12.0f;      // per shell, to every enemy in the splash (Bombard 9)
    final static public float attacksPerSecond = 0.2f;   // one shell every 5 s
    final static public float aggroRange = 30;
    /** Too close to lob a shell at: a target nearer than this is not fired on (the Bombard's dead zone is 6). */
    public static final float MIN_RANGE = 8.0f;
    @Override public float getMinRange() { return MIN_RANGE; }
    final static public boolean willRetaliate = true; // will attack when hurt by an enemy
    final static public boolean aggressiveWhenIdle = true;

    final static public float maxHealth = 110.0f;
    final static public float armorValue = 6.0f;          // the shell
    final static public float movementSpeed = 0.18f;      // Bombard 0.22: the water (Tides) is what makes it keep up
    public int maxResources = 100;

    public int fogRevealDuration = 0; // set > 0 for the client who is attacked by this unit
    public int getFogRevealDuration() { return fogRevealDuration; }
    public void setFogRevealDuration(int duration) { fogRevealDuration = duration; }

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    public MortarGalleyUnit(EntityType<? extends Animal> entityType, Level level) {
        super(entityType, level);
        updateAbilityButtons();
    }

    @Override
    public boolean removeWhenFarAway(double d) { return false; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.ATTACK_DAMAGE, 1.0f)   // AM's bite reads this; the bite is cancelled (see the class comment)
                .add(Attributes.MOVEMENT_SPEED, MortarGalleyUnit.movementSpeed)
                .add(Attributes.MAX_HEALTH, MortarGalleyUnit.maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, MortarGalleyUnit.armorValue)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), 20)   // like the Bombard, it needs spotters for its reach
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0.25)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    @Override // prevent vanilla logic for picking up items
    protected void pickUpItem(@NotNull ItemEntity pItemEntity) { }

    @Override
    public LivingEntity getTarget() {
        // RoN's target, not AM's (which hides the target while its chase timer runs); goals only exist server side
        return this.targetGoal == null ? null : this.targetGoal.getTarget();
    }

    @Override
    public void setTarget(@Nullable LivingEntity pTarget) {
        super.setTarget(pTarget);
        if (pTarget != null && getRangedAttackGroundGoal() != null)
            getRangedAttackGroundGoal().stop();
    }

    // AM's ambush state ("wait with the mouth open") stops the turtle moving: a unit always answers its orders
    @Override
    public void setWaiting(boolean waiting) { }

    // no feeding, breeding or shearing the moss: it is a unit, not livestock
    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean isFood(@NotNull ItemStack stack) { return false; }

    /** The goals running now, for a GameTest failure message (goalSelector is protected). */
    public String runningGoalsForTest() {
        return this.goalSelector.getRunningGoals().map(w -> w.getGoal().getClass().getSimpleName())
            .collect(java.util.stream.Collectors.joining("+"));
    }

    // a plain ground navigator: AM's climbs walls, which RoN's surface paths do not expect
    @Override
    protected @NotNull PathNavigation createNavigation(@NotNull Level level) {
        return new GroundPathNavigation(this, level);
    }

    @Override
    public boolean onClimbable() { return false; }

    @Override
    public boolean canBreatheUnderwater() { return true; }   // amphibious (design/tidewrought_plan.md section 6)

    public void tick() {
        this.setCanPickUpLoot(false);
        // AM gives up a chase after 40 ticks on a target more than 10 blocks off by clearing its target and attacker
        // and hiding them for 50 ticks: an artillery piece always shoots from further. Its timer never gets there.
        this.chaseTime = 0;
        super.tick();
        Unit.tick(this);
        AttackerUnit.tick(this);
        if (attackGroundGoal != null)
            attackGroundGoal.tick();
    }

    /** In water: the Mudskipper Sloop's tamer travel (land friction with a 0.85 drag). On land: vanilla travel. */
    @Override
    public void travel(@NotNull Vec3 travelVector) {
        if (this.isControlledByLocalInstance() && this.isInWater()) {
            this.moveRelative(this.getSpeed() * 0.5f, travelVector);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.85));
            this.calculateEntityAnimation(false);
            return;
        }
        super.travel(travelVector);
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
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag pCompound) {
        super.readAdditionalSaveData(pCompound);
        this.readUnitSaveData(pCompound);
    }

    public void initialiseGoals() {
        this.usePortalGoal = new UsePortalGoal(this);
        this.moveGoal = new MoveToTargetBlockGoal(this, false, 0);
        this.targetGoal = new SelectedTargetGoal<>(this, true, false);
        this.attackGoal = new UnitBowAttackGoal<>(this);
        this.returnResourcesGoal = new ReturnResourcesGoal(this);
        this.attackBuildingGoal = new RangedAttackBuildingGoal<>(this, this.attackGoal);
        this.attackGroundGoal = new RangedAttackGroundGoal<>(this, false, this.attackGoal);
    }

    @Override
    public void resetBehaviours() {
        if (this.attackGoal != null)
            this.attackGoal.stop();
        if (this.attackGroundGoal != null)
            this.attackGroundGoal.stop();
    }

    @Override
    protected void registerGoals() {
        initialiseGoals();
        this.goalSelector.addGoal(2, usePortalGoal);
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, attackGoal);
        this.goalSelector.addGoal(2, attackBuildingGoal);
        this.goalSelector.addGoal(2, attackGroundGoal);
        this.targetSelector.addGoal(2, targetGoal);
        this.goalSelector.addGoal(3, moveGoal);
        this.goalSelector.addGoal(4, new RandomLookAroundUnitGoal(this));
    }

    // the galley "holds a bow" only because UnitBowAttackGoal needs one to fire (AM's turtle draws no held item);
    // the shell is a BombardShell
    @Override
    public void setupEquipmentAndUpgradesServer() {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
    }

    @Override
    public void performUnitRangedAttack(LivingEntity pTarget, float velocity) {
        // lead part of the way, as the Bombard: a full lead would let any turn dodge it
        Vec3 v = pTarget.getDeltaMovement();
        double lead = BombardShell.flightTicks(Math.sqrt(distanceToSqr(pTarget))) * 0.6;
        if (fireShell(pTarget.getX() + v.x * lead, pTarget.getY(), pTarget.getZ() + v.z * lead)
                && !level().isClientSide() && pTarget instanceof Unit unit)
            FogOfWarClientboundPacket.revealRangedUnit(unit.getOwnerName(), this.getId());
    }

    // Attack Ground and building targets
    @Override
    public void performUnitRangedAttack(double x, double y, double z, float velocity) {
        fireShell(x, y, z);
    }

    /**
     * Lobs one shell at (x, y, z) unless it is inside {@link #MIN_RANGE}. Returns whether it fired. Public for the
     * game test (fires without waiting on the attack goal).
     */
    public boolean fireShell(double x, double y, double z) {
        if (level().isClientSide())
            return false;
        double dx = x - getX(), dz = z - getZ();
        if (dx * dx + dz * dz < MIN_RANGE * MIN_RANGE)
            return false;
        BombardShell shell = new BombardShell(level(), this, getUnitAttackDamage());
        shell.setPos(getX(), getY() + getBbHeight() + 0.4, getZ());
        shell.aimAt(x, y, z);
        level().addFreshEntity(shell);
        this.playSound(SoundEvents.GENERIC_EXPLODE, 1.4F, 1.3F + getRandom().nextFloat() * 0.2F);
        if (level() instanceof ServerLevel sl)
            sl.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() + 0.5, getZ(), 5, 0.2, 0.15, 0.2, 0.02);
        getMainHandItem().setDamageValue(0);
        return true;
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor pLevel, @NotNull DifficultyInstance pDifficulty, @NotNull MobSpawnType pReason, @Nullable SpawnGroupData pSpawnData, @Nullable CompoundTag pDataTag) {
        return pSpawnData;   // skips AM's random turtle scale and moss: every galley is the same size
    }

    // never a baby, never bred (the turtle is an AgeableMob)
    @Override
    public @Nullable AgeableMob getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob other) {
        return null;
    }
}
