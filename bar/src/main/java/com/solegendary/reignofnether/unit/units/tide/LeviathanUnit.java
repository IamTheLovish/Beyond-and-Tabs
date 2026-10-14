package com.solegendary.reignofnether.unit.units.tide;

import com.github.alexthe666.alexsmobs.entity.EntityLobster;
import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.TidalSlam;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.tide.TidalUnit;
import com.solegendary.reignofnether.tide.TidepoolServerEvents;
import com.solegendary.reignofnether.unit.Checkpoint;
import com.solegendary.reignofnether.unit.EnemySearchBehaviour;
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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Tidewrought T3 experimental - the <b>Leviathan</b> (design/tidewrought_plan.md, slice 4): Alex's Mobs' lobster grown
 * {@link #SCALE}x into a crab-beast with a barnacled fortress on its back (LeviathanRenderer over AM's ModelLobster with
 * an abyssal shell skin and a fortress layer), on the RTS unit brain. An amphibious siege fortress:
 * <ul>
 * <li>slow on land ({@link #movementSpeed}), fast in water or on a tidepool: its Tides bonus is
 * {@link #WET_SPEED_BONUS} on top of the faction's (TidalUnit.tidesSpeedBonus, applied by the Tides pass) - x2.5;</li>
 * <li>its wake: every {@link #TRAIL_STEP} blocks it walks it leaves a small tidepool behind it (radius
 * {@link #TRAIL_RADIUS}, {@link #TRAIL_LIFETIME_TICKS} ticks) through TidepoolServerEvents.raiseTrail, so the
 * registry's own per-source cap of 6 holds and the trail never evicts a priest's pools. The pools land behind its
 * body, so they speed the escorts that follow it, not the Leviathan itself;</li>
 * <li>claws: a heavy melee pinch with a 7-block reach (its body is 3.5 wide);</li>
 * <li><b>Tidal Slam</b> ({@link TidalSlam}): a telegraphed wide-arc wave that hurts and throws back every enemy in
 * front of it.</li>
 * </ul>
 * T3 rules (design-factions.md): the standard experimental price (the World Tree Walker's, ~1840 metal); clear
 * weaknesses - <b>artillery and siege hit its fortress {@link #SIEGE_MULTIPLIER}x</b> (explosions, Bombard and Mortar
 * shells, any attacker with 20+ range, any siege-bonus attacker such as golems and hoglins), it is melee-only so it
 * has no anti-air and Sky Corsairs or Bone Dragons pick it apart, and it crawls on land; Tidal Slam is telegraphed and
 * blind behind, no instant win. Arrival announced to all.
 * <p>
 * The lobster is a WaterAnimal: AM already skips its air bookkeeping (handleAirSupply is empty, canBreatheUnderwater
 * is true), so it neither drowns nor dries out, and {@link #hurt} refuses drowning, drying-out and in-wall damage
 * outright (a 3.5-block body brushing a wall must not choke - UnitUnstick frees it instead). It is not a fish, so it
 * never flops. MobilityClass gives it TIDAL (the tidal check runs before the WaterAnimal-means-AQUATIC rule). AM's
 * semi-aquatic navigator is replaced by a plain ground one (RoN's paths assume a walker), its bottom-walking water
 * travel by the Sloop's surface one (FloatGoal keeps it up), and its brain (water seeking, wandering, bucketing) by
 * the RTS goals.
 */
public class LeviathanUnit extends EntityLobster implements Unit, AttackerUnit, TidalUnit {
    public static final Abilities ABILITIES = new Abilities();
    static {
        ABILITIES.add(new TidalSlam(), Keybindings.abilitySlot1);
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
            SynchedEntityData.defineId(LeviathanUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(LeviathanUnit.class, EntityDataSerializers.INT);

    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
        SynchedEntityData.defineId(LeviathanUnit.class, EntityDataSerializers.STRING);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ownerDataAccessor, "");
        this.entityData.define(scenarioRoleDataAccessor, -1);
        this.entityData.define(onDeathCommandDataAccessor, "");
    }

    @Nullable
    public ResourceCost getCost() {return ResourceCosts.LEVIATHAN;}
    public boolean getWillRetaliate() {return willRetaliate;}
    public boolean getAggressiveWhenIdle() {return aggressiveWhenIdle && !isVehicle();}
    public BlockPos getAttackMoveTarget() { return attackMoveTarget; }
    public boolean canAttackBuildings() {return getAttackBuildingGoal() != null;}
    // endregion

    /** Body and hitbox scale over AM's wild lobster (0.7 x 0.4); EntityRegistrar sizes the type to match. */
    public static final float SCALE = 5.0f;

    final static public float attackDamage = 20.0f;
    final static public float attacksPerSecond = 0.4f;   // 8 dps: a fortress, not a duellist (Sun Colossus 7.7)
    final static public float attackRange = 4f;          // only used by ranged units or melee building attackers
    final static public float aggroRange = 12;
    final static public boolean willRetaliate = true;    // will attack when hurt by an enemy
    final static public boolean aggressiveWhenIdle = true;

    final static public float maxHealth = 1200.0f;
    final static public float armorValue = 6.0f;          // the shell
    final static public float movementSpeed = 0.12f;      // the slowest walker on the field: it belongs in the water
    final static public float rangedResist = 0.3f;
    public int maxResources = 100;

    /** Extra Tides speed on water or a tidepool, on top of the faction's +20%: x2.5 wet. */
    public static final double WET_SPEED_BONUS = 1.30;
    /** The weakness: artillery and siege crack the fortress on its back (see {@link #isSiegeHit}). */
    public static final float SIEGE_MULTIPLIER = 1.75f;
    /** An attacker with at least this range counts as artillery (the HUD role card's rule, UnitInfoCard). */
    public static final float ARTILLERY_RANGE = 20f;

    /** The wake: one small pool every this many blocks walked, behind the body. */
    public static final double TRAIL_STEP = 3.0;
    public static final int TRAIL_RADIUS = 1;
    public static final int TRAIL_LIFETIME_TICKS = 10 * 20;
    static final int TRAIL_PASS_TICKS = 10;
    static final String KEY_ANNOUNCED = "bt_t3_announced";

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();
    // where the last trail pool was laid from (NaN = not yet: the first pass only records the spot)
    private double trailX = Double.NaN, trailZ = Double.NaN;

    public LeviathanUnit(EntityType<? extends EntityLobster> entityType, Level level) {
        super(entityType, level);
        updateAbilityButtons();
    }

    @Override
    public boolean removeWhenFarAway(double d) { return false; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.ATTACK_DAMAGE, LeviathanUnit.attackDamage)
                .add(Attributes.MOVEMENT_SPEED, LeviathanUnit.movementSpeed)
                .add(Attributes.MAX_HEALTH, LeviathanUnit.maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, LeviathanUnit.armorValue)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), rangedResist)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.BUILDING_DAMAGE_BONUS.get(), 0.5);
    }

    @Override
    public double tidesSpeedBonus() {
        return WET_SPEED_BONUS;
    }

    @Override // prevent vanilla logic for picking up items
    protected void pickUpItem(@NotNull ItemEntity pItemEntity) { }

    @Override
    public LivingEntity getTarget() {
        // goals only exist server side (Mob registers them there)
        return this.targetGoal == null ? null : this.targetGoal.getTarget();
    }

    // no bucketing: it is a unit, not a catch
    @Override
    protected @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        return InteractionResult.PASS;
    }

    // a plain ground navigator: AM's semi-aquatic one heads for water and walks lake beds, RoN's paths are surface paths
    @Override
    protected @NotNull PathNavigation createNavigation(@NotNull Level level) {
        return new GroundPathNavigation(this, level);
    }

    @Override
    public boolean canBreatheUnderwater() { return true; }

    /**
     * Never drowns, dries out or chokes in a wall (see the class comment); artillery and siege hit
     * {@link #SIEGE_MULTIPLIER}x - the fortress weakness.
     */
    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (isBreathingDamage(source))
            return false;
        if (isSiegeHit(source))
            amount *= SIEGE_MULTIPLIER;
        return super.hurt(source, amount);
    }

    /** Drowning, drying out (a water mob on land) and suffocating in a block: none of them touch the Leviathan. */
    public static boolean isBreathingDamage(DamageSource source) {
        return source.is(DamageTypes.DROWN) || source.is(DamageTypes.DRY_OUT) || source.is(DamageTypes.IN_WALL);
    }

    /**
     * Artillery or siege: an explosion, a Bombard / Mortar shell, an attacker with at least {@link #ARTILLERY_RANGE}
     * range, or one with a building-damage (siege) bonus of 1 or more (golems, hoglins, the Sun Colossus).
     */
    public static boolean isSiegeHit(DamageSource source) {
        if (source.is(DamageTypeTags.IS_EXPLOSION) || source.getDirectEntity() instanceof BombardShell)
            return true;
        Entity attacker = source.getEntity();
        if (attacker instanceof AttackerUnit au && au.getAttackRange() >= ARTILLERY_RANGE)
            return true;
        if (attacker instanceof LivingEntity le) {
            AttributeInstance siege = le.getAttribute(AttributeRegistrar.BUILDING_DAMAGE_BONUS.get());
            return siege != null && siege.getValue() >= 1.0;
        }
        return false;
    }

    // AM's pinch (with its claw animation), on the vanilla attack path RoN's melee goals call; spray where it lands
    @Override
    public boolean doHurtTarget(@NotNull Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && level() instanceof ServerLevel sl)
            sl.sendParticles(ParticleTypes.SPLASH, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                10, 0.4, 0.3, 0.4, 0.1);
        return hit;
    }

    public void tick() {
        this.setCanPickUpLoot(false);
        super.tick();
        Unit.tick(this);
        AttackerUnit.tick(this);
        if (!(level() instanceof ServerLevel sl) || !isAlive())
            return;
        String owner = getOwnerName();
        if (tickCount > 5 && !getPersistentData().getBoolean(KEY_ANNOUNCED) && owner != null && !owner.isEmpty()) {
            getPersistentData().putBoolean(KEY_ANNOUNCED, true);
            com.solegendary.reignofnether.player.PlayerServerEvents.sendMessageToAllPlayers(
                "server.reignofnether.t3_arrived", true, owner,
                net.minecraft.network.chat.Component.translatable("entity.reignofnether.leviathan_unit"));
        }
        // staggered by entity id so a few Leviathans never all raise pools on the same tick
        if ((tickCount + getId()) % TRAIL_PASS_TICKS == 0)
            trail(sl);
    }

    /**
     * The wake: once the Leviathan has walked {@link #TRAIL_STEP} blocks since the last pool, lays one behind its
     * body (opposite to the way it went). Returns the pool, or null if it laid none (not far enough yet, no open
     * ground - real water, a building - or the level's cell budget is spent). Public for the game test.
     */
    @Nullable
    public TidepoolServerEvents.Pool trail(ServerLevel sl) {
        if (Double.isNaN(trailX)) {
            trailX = getX();
            trailZ = getZ();
            return null;
        }
        double dx = getX() - trailX, dz = getZ() - trailZ;
        double d2 = dx * dx + dz * dz;
        if (d2 < TRAIL_STEP * TRAIL_STEP)
            return null;
        double d = Math.sqrt(d2);
        trailX = getX();
        trailZ = getZ();
        double back = getBbWidth() / 2 + TRAIL_RADIUS + 1.0;   // clear of its own body
        return trailAt(sl, BlockPos.containing(getX() - dx / d * back, getY() + 0.01, getZ() - dz / d * back));
    }

    /** Raises one trail pool owned by this Leviathan's player at {@code at} (the registry caps them at 6 per Leviathan). */
    @Nullable
    public TidepoolServerEvents.Pool trailAt(ServerLevel sl, BlockPos at) {
        String owner = getOwnerName();
        if (owner == null || owner.isEmpty())
            return null;
        return TidepoolServerEvents.raiseTrail(sl, owner, this, at, TRAIL_RADIUS, TRAIL_LIFETIME_TICKS);
    }

    /**
     * In water: the Mudskipper Sloop's surface travel (land friction with a 0.85 drag), so wet speed scales like land
     * speed and the Tides bonus is what makes it fast - AM's lobster travel walks the lake bed, off RoN's surface
     * paths. On land: vanilla travel.
     */
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
    public SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor pLevel, @NotNull DifficultyInstance pDifficulty, @NotNull MobSpawnType pReason, @Nullable SpawnGroupData pSpawnData, @Nullable CompoundTag pDataTag) {
        return pSpawnData;   // skips AM's random shell colour: every Leviathan wears the abyssal skin
    }
}
