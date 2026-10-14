package com.solegendary.reignofnether.unit.units.tide;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.Moor;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.tide.TidalUnit;
import com.solegendary.reignofnether.tide.TidepoolServerEvents;
import com.solegendary.reignofnether.unit.Checkpoint;
import com.solegendary.reignofnether.unit.EnemySearchBehaviour;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tidewrought T2 beast - the <b>Shellback Siege-Turtle</b> (design/big_units.md 1.5): a vanilla sea turtle grown
 * x{@link #SCALE} carrying a timber fort, a deck battery and a tide bell on its shell (ShellbackTurtleRenderer) - the
 * island-turtle of sailors' tales as a mobile harbour. Not the Mortar Galley's big brother (that is the faction's
 * artillery, an alligator snapping turtle); the Shellback is where the fleet stands.
 * <p>
 * Signature: <b>Moor</b> ({@link Moor}, a toggle). Anchoring takes {@link #MOOR_TICKS} (so does weighing anchor), and the
 * turtle can't move from the moment it starts until it has fully weighed anchor. Moored it:
 * <ul>
 *   <li>takes x{@link #MOORED_TAKEN_MULT} damage (the shell closes) and stops biting;</li>
 *   <li>raises a radius-{@link #POOL_RADIUS} <b>tidepool</b> round itself (TidepoolServerEvents; re-raised if drained,
 *   drained when it weighs anchor): the Tides bonus - speed and regeneration - for every Tidewrought unit fighting
 *   round it, and dry land the Mortar Galleys can sit on;</li>
 *   <li>fires its <b>deck battery</b> every {@link #BATTERY_EVERY} ticks at the nearest enemy unit between
 *   {@link #BATTERY_MIN_RANGE} and {@link #BATTERY_RANGE} blocks ({@link #BATTERY_DAMAGE}), else at the nearest enemy
 *   building in range ({@link #BATTERY_BUILDING_DAMAGE}) - the "siege" in its name.</li>
 * </ul>
 * Win condition if ignored: a moored Shellback at the edge of a base grinds the buildings down while its pool keeps
 * the fleet round it healed. Counter: it can't move once anchored and needs 2 s to leave; the battery can't depress
 * into the {@link #BATTERY_MIN_RANGE}-block dead zone, so melee that reaches the shell is safe from it; artillery
 * out-ranges it; and any constructor can drain its pool (the Tides counterplay).
 * Cost: one grid query every 2 s per moored turtle, one building scan only when no unit is in range.
 */
public class ShellbackTurtleUnit extends Turtle implements Unit, AttackerUnit, TidalUnit {

    public static final Abilities ABILITIES = new Abilities();

    //region
    @Override
    public void updateAbilityButtons() {
        abilities = ABILITIES.clone();
        if (moor == null)
            moor = new Moor();
        abilities.add(moor, Keybindings.abilitySlot1);
    }
    private Moor moor;
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
    private AbstractMeleeAttackUnitGoal attackGoal;
    private MeleeAttackBuildingGoal attackBuildingGoal;

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
            SynchedEntityData.defineId(ShellbackTurtleUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(ShellbackTurtleUnit.class, EntityDataSerializers.INT);

    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
        SynchedEntityData.defineId(ShellbackTurtleUnit.class, EntityDataSerializers.STRING);


    @Nullable
    public ResourceCost getCost() {return ResourceCosts.SHELLBACK_TURTLE;}
    public boolean getWillRetaliate() {return willRetaliate;}
    public boolean getAggressiveWhenIdle() {return aggressiveWhenIdle && !isVehicle();}
    public BlockPos getAttackMoveTarget() { return attackMoveTarget; }
    public boolean canAttackBuildings() {return getAttackBuildingGoal() != null;}
    public Goal getAttackGoal() { return attackGoal; }
    public Goal getAttackBuildingGoal() { return attackBuildingGoal; }
    public void setAttackMoveTarget(@Nullable BlockPos bp) { this.attackMoveTarget = bp; }
    public void setFollowTarget(@Nullable LivingEntity target) { this.followTarget = target; }

    private EnemySearchBehaviour attackSearchBehaviour = EnemySearchBehaviour.NONE;
    public EnemySearchBehaviour getEnemySearchBehaviour() { return attackSearchBehaviour; }
    public void setEnemySearchBehaviour(EnemySearchBehaviour behaviour) { attackSearchBehaviour = behaviour; }

    // endregion


    final static public float attackDamage = 9.0f;
    final static public float attacksPerSecond = 0.6f;
    final static public float attackRange = 3.0f; // only used by ranged units or melee building attackers
    final static public float aggroRange = 12;
    final static public boolean willRetaliate = true; // will attack when hurt by an enemy
    final static public boolean aggressiveWhenIdle = true;

    final static public float maxHealth = 560.0f;
    final static public float armorValue = 2.0f;
    final static public float movementSpeed = 0.15f;
    public int maxResources = 100;

    public static final float SCALE = 2.4f;
    public static final int MOOR_TICKS = 40;
    public static final float MOORED_TAKEN_MULT = 0.5f;
    public static final int POOL_RADIUS = 3;
    public static final int POOL_LIFETIME_TICKS = 5 * 60 * 20;
    public static final int POOL_CHECK_TICKS = 10 * 20;
    public static final float BATTERY_RANGE = 16f;
    public static final float BATTERY_MIN_RANGE = 4f;
    public static final int BATTERY_EVERY = 40;
    public static final float BATTERY_DAMAGE = 12f;
    public static final float BATTERY_BUILDING_DAMAGE = 30f;
    static final UUID ANCHOR_UUID = UUID.fromString("3c9a7e21-5d40-4b8e-a1f2-6e0d9c4b7a13");

    /** Synced so the renderer can drop the anchor chains while moored. */
    public static final EntityDataAccessor<Boolean> mooredDataAccessor =
            SynchedEntityData.defineId(ShellbackTurtleUnit.class, EntityDataSerializers.BOOLEAN);

    /** True while the player wants it moored (the toggle); the anchor follows over MOOR_TICKS. */
    private boolean moorWanted = false;
    /** 0 = under way, MOOR_TICKS = fully moored; anything above 0 is immobile. */
    private int moorTicks = 0;
    private int batteryCooldown = 0, poolCheck = 0;
    @Nullable private TidepoolServerEvents.Pool pool = null;
    /** What the last battery shot hit (game test): a unit, a building, or neither. */
    @Nullable public LivingEntity lastBatteryUnit = null;
    @Nullable public BuildingPlacement lastBatteryBuilding = null;
    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused (server thread)

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    public ShellbackTurtleUnit(EntityType<? extends Turtle> entityType, Level level) {
        super(entityType, level);
        // the sea turtle's own move control halves its speed on land and steers it home to its beach: a unit walks
        this.moveControl = new MoveControl(this);
        updateAbilityButtons();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ownerDataAccessor, "");
        this.entityData.define(scenarioRoleDataAccessor, -1);
        this.entityData.define(onDeathCommandDataAccessor, "");
        this.entityData.define(mooredDataAccessor, false);
    }

    @Override
    public boolean removeWhenFarAway(double d) { return false; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, armorValue)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0.2)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.BUILDING_DAMAGE_BONUS.get(), 0.5);
    }

    // a walker, not a swimmer homing on its beach (the Mortar Galley's rule)
    @Override
    protected @NotNull PathNavigation createNavigation(@NotNull Level level) {
        return new GroundPathNavigation(this, level);
    }

    @Override // prevent vanilla logic for picking up items
    protected void pickUpItem(ItemEntity pItemEntity) { }
    @Override
    protected void customServerAiStep() { }
    @Override
    public LivingEntity getTarget() {
        return this.targetGoal == null ? null : this.targetGoal.getTarget();
    }

    // no feeding, breeding or eggs: it is a unit, not wildlife
    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean isFood(@NotNull ItemStack stack) { return false; }

    // ---- Moor ---------------------------------------------------------------------------------------------------------

    public boolean isMoorWanted() { return moorWanted; }
    /** Fully anchored: the fort is up (half damage, pool, battery). */
    public boolean isMoored() { return moorTicks >= MOOR_TICKS; }
    /** Anchored, anchoring or weighing anchor: it can't move. */
    public boolean isAnchored() { return moorTicks > 0; }
    public boolean isMooredClient() { return this.entityData.get(mooredDataAccessor); }

    /** The Moor button: anchor if under way, weigh anchor if moored. Public for the ability, the bot and the game test. */
    public void toggleMoor() {
        moorWanted = !moorWanted;
        if (moorWanted)
            Unit.fullResetBehaviours(this);   // a standing order: no chase drags it off the anchor spot
        level().playSound(null, blockPosition(), moorWanted ? SoundEvents.CHAIN_PLACE : SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1.5f, 0.6f);
    }

    /** One tick of the anchor, the pool and the battery (server). Public for the game test. */
    public void tickMoor(ServerLevel sl) {
        boolean wasMoored = isMoored();
        if (moorWanted && moorTicks < MOOR_TICKS)
            moorTicks++;
        else if (!moorWanted && moorTicks > 0)
            moorTicks--;
        setAnchorModifier(isAnchored());
        if (isAnchored()) {
            getNavigation().stop();
            setDeltaMovement(0, getDeltaMovement().y, 0);
        }
        boolean moored = isMoored();
        if (moored != wasMoored) {
            this.entityData.set(mooredDataAccessor, moored);
            if (moored) {
                raisePool(sl);
                sl.playSound(null, blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 1.5f, 0.6f);
            } else if (pool != null) {
                TidepoolServerEvents.drain(pool);   // the harbour weighs anchor with it
                pool = null;
            }
        }
        if (!moored)
            return;
        if (++poolCheck >= POOL_CHECK_TICKS) {
            poolCheck = 0;
            if (pool == null || !pool.isActive() || pool.isDraining())
                raisePool(sl);
        }
        if (--batteryCooldown <= 0) {
            batteryCooldown = BATTERY_EVERY;
            fireBattery(sl);
        }
    }

    void setAnchorModifier(boolean on) {
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null)
            return;
        boolean has = speed.getModifier(ANCHOR_UUID) != null;
        if (on && !has)
            speed.addTransientModifier(new AttributeModifier(ANCHOR_UUID, "shellback_anchor", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
        else if (!on && has)
            speed.removeModifier(ANCHOR_UUID);
    }

    /** Raises (or re-raises) the harbour pool round the turtle. Public for the game test. */
    @Nullable
    public TidepoolServerEvents.Pool raisePool(ServerLevel sl) {
        String owner = getOwnerName();
        if (owner == null || owner.isEmpty())
            return null;
        pool = TidepoolServerEvents.raise(sl, owner, blockPosition(), POOL_RADIUS, POOL_LIFETIME_TICKS);
        return pool;
    }

    @Nullable
    public TidepoolServerEvents.Pool getPool() { return pool; }

    /**
     * One battery shot: the nearest enemy unit between BATTERY_MIN_RANGE and BATTERY_RANGE, else the nearest enemy
     * building in range. Returns true if it hit something. Public for the game test.
     */
    public boolean fireBattery(ServerLevel sl) {
        lastBatteryUnit = null;
        lastBatteryBuilding = null;
        String owner = getOwnerName();
        if (owner == null || owner.isEmpty())
            return false;
        double min2 = BATTERY_MIN_RANGE * BATTERY_MIN_RANGE, max2 = BATTERY_RANGE * BATTERY_RANGE;
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity le : UnitGrid.near(sl, getX(), getZ(), BATTERY_RANGE, scratch)) {
            if (le == this || !le.isAlive() || le.level() != sl || !(le instanceof Unit u))
                continue;
            String o = u.getOwnerName();
            if (o == null || o.isEmpty() || o.equals(owner) || AlliancesServerEvents.isAllied(owner, o))
                continue;
            double d = le.distanceToSqr(this);
            if (d < min2 || d > max2 || d >= bestD)
                continue;
            best = le;
            bestD = d;
        }
        scratch.clear();
        if (best != null) {
            best.hurt(damageSources().mobAttack(this), BATTERY_DAMAGE);
            tracer(sl, best.getX(), best.getY() + best.getBbHeight() * 0.5, best.getZ());
            lastBatteryUnit = best;
            return true;
        }
        BuildingPlacement bestB = null;
        for (BuildingPlacement bp : BuildingServerEvents.getBuildings()) {
            if (bp.isDestroyedServerside || bp.ownerName == null || bp.ownerName.isEmpty() || bp.ownerName.equals(owner)
                    || AlliancesServerEvents.isAllied(owner, bp.ownerName))
                continue;
            double d = bp.centrePos.distToCenterSqr(getX(), getY(), getZ());
            if (d < min2 || d > max2 || d >= bestD)
                continue;
            bestB = bp;
            bestD = d;
        }
        if (bestB == null)
            return false;
        bestB.destroyRandomBlocks(BATTERY_BUILDING_DAMAGE, this);
        tracer(sl, bestB.centrePos.getX() + 0.5, bestB.centrePos.getY() + 0.5, bestB.centrePos.getZ() + 0.5);
        lastBatteryBuilding = bestB;
        return true;
    }

    /** The deck gun's harpoon line and its report. */
    void tracer(ServerLevel sl, double x, double y, double z) {
        double sx = getX(), sy = getY() + getBbHeight(), sz = getZ();
        for (int i = 1; i <= 8; i++) {
            double f = i / 8.0;
            sl.sendParticles(ParticleTypes.CRIT, sx + (x - sx) * f, sy + (y - sy) * f, sz + (z - sz) * f, 1, 0, 0, 0, 0);
        }
        sl.playSound(null, blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.NEUTRAL, 1.5f, 0.6f);
    }

    /** Moored, the shell closes: every hit is cut to MOORED_TAKEN_MULT. */
    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        return super.hurt(source, isMoored() ? amount * MOORED_TAKEN_MULT : amount);
    }

    /** Anchored it doesn't bite: the head is in the shell and the deck battery does the fighting. */
    @Override
    public boolean doHurtTarget(@NotNull Entity target) {
        if (isAnchored())
            return false;
        boolean hit = target.hurt(this.damageSources().mobAttack(this), (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE));
        if (hit)
            this.doEnchantDamageEffects(this, target);
        return hit;
    }

    public void tick() {
        this.setCanPickUpLoot(false);
        super.tick();
        Unit.tick(this);
        AttackerUnit.tick(this);
        if (level() instanceof ServerLevel sl && isAlive())
            tickMoor(sl);
    }

    @Override
    public void remove(@NotNull RemovalReason pReason) {
        if (this.level() instanceof ServerLevel serverLevel) {
            if (pool != null) {
                TidepoolServerEvents.drain(pool);
                pool = null;
            }
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
        pCompound.putBoolean("bt_moor_wanted", moorWanted);
        pCompound.putInt("bt_moor_ticks", moorTicks);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag pCompound) {
        super.readAdditionalSaveData(pCompound);
        this.readUnitSaveData(pCompound);
        moorWanted = pCompound.getBoolean("bt_moor_wanted");
        moorTicks = Math.max(0, Math.min(MOOR_TICKS, pCompound.getInt("bt_moor_ticks")));
        // the pool handle is not saved: a moored turtle re-raises (or re-finds the cells taken) on its next check
    }

    public void initialiseGoals() {
        this.usePortalGoal = new UsePortalGoal(this);
        this.moveGoal = new MoveToTargetBlockGoal(this, false, 0);
        this.targetGoal = new SelectedTargetGoal<>(this, true, true);
        this.attackGoal = new MeleeAttackUnitGoal(this, false);
        this.attackBuildingGoal = new MeleeAttackBuildingGoal(this);
    }

    @Override
    protected void registerGoals() {
        initialiseGoals();
        this.goalSelector.addGoal(2, usePortalGoal);
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, attackGoal);
        this.goalSelector.addGoal(2, attackBuildingGoal);
        this.targetSelector.addGoal(2, targetGoal);
        this.goalSelector.addGoal(3, moveGoal);
        this.goalSelector.addGoal(4, new RandomLookAroundUnitGoal(this));
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor pLevel, DifficultyInstance pDifficulty, MobSpawnType pReason, @Nullable SpawnGroupData pSpawnData, @Nullable CompoundTag pDataTag) {
        return pSpawnData;   // skips the turtle's home beach
    }

    // never a hatchling, never bred
    @Override
    public @Nullable AgeableMob getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob other) {
        return null;
    }
}
