package com.solegendary.reignofnether.unit.units.tide;

import com.github.alexthe666.alexsmobs.entity.EntityLobster;
import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.DeepDive;
import com.solegendary.reignofnether.ability.abilities.TidalSlam;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.tide.TidalUnit;
import com.solegendary.reignofnether.tide.TidepoolServerEvents;
import com.solegendary.reignofnether.tide.TidesServerEvents;
import com.solegendary.reignofnether.unit.Checkpoint;
import com.solegendary.reignofnether.unit.EnemySearchBehaviour;
import com.solegendary.reignofnether.unit.TitanMass;
import com.solegendary.reignofnether.unit.UnitDamage;
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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Tidewrought T3 experimental - the <b>Leviathan</b>: the <b>Amphibious flanker</b> (claude/t3-roles.md, section 4;
 * design/tidewrought_plan.md, slice 4). Alex's Mobs' lobster grown {@link #SCALE}x into a crab-beast with a barnacled
 * fortress on its back (LeviathanRenderer over AM's ModelLobster with an abyssal shell skin and a fortress layer), on
 * the RTS unit brain. The faction's strategy is the water: it turns every river into a threat. The enemy's moment: a
 * river behind the front is no longer safe - watch the water or hold the shore.
 * <ul>
 * <li>steady on land ({@link #movementSpeed}: a heavy line's pace), very fast in water or on a tidepool: its Tides
 * bonus is {@link #WET_SPEED_BONUS} on top of the faction's (TidalUnit.tidesSpeedBonus, applied by the Tides pass) -
 * x2.5;</li>
 * <li><b>Deep Dive</b> ({@link DeepDive}): from the water it sinks out of sight and swims under the surface up to
 * {@link #DIVE_RANGE} blocks - unseen (Concealment), untargetable and unhurt - and surfaces at the water's edge nearest
 * the aimed spot with a tidal slam ({@link TidalSlam#slam}) on everything round it. Bubbles trace the way and boil at
 * the landing for the last {@link #DIVE_TELEGRAPH_TICKS}: the enemy who watches the river steps back;</li>
 * <li>its wake: every {@link #TRAIL_STEP} blocks it walks it leaves a small tidepool behind it (radius
 * {@link #TRAIL_RADIUS}, {@link #TRAIL_LIFETIME_TICKS} ticks) through TidepoolServerEvents.raiseTrail, so the
 * registry's own per-source cap of 6 holds and the trail never evicts a priest's pools. The pools land behind its
 * body, so they speed the escorts that follow it, not the Leviathan itself;</li>
 * <li>claws: a heavy melee pinch with a 7-block reach (its body is 3.5 wide) that <b>cleaves</b> - half of each pinch
 * lands on the enemies within {@link #CLEAVE_RADIUS} of the target;</li>
 * <li><b>Tidal Slam</b> ({@link TidalSlam}): a telegraphed wide-arc wave that hurts and throws back every enemy in
 * front of it;</li>
 * <li><b>mass</b> ({@link TitanMass}): never pushed, shoves small bodies aside.</li>
 * </ul>
 * Countered by: <b>artillery and siege hit its fortress {@link #SIEGE_MULTIPLIER}x</b> (explosions, Bombard and Mortar
 * shells, any attacker with 20+ range, any siege-bonus attacker such as golems and hoglins), it is melee-only so it
 * has no anti-air and Sky Corsairs or Bone Dragons pick it apart, and a shore held by towers meets it where it must
 * surface; Tidal Slam is telegraphed and blind behind, the dive is shown by its bubbles. It beats a back line that
 * trusted the river, and artillery parked by the water. Arrival announced to all.
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
        ABILITIES.add(new DeepDive(), Keybindings.abilitySlot2);
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

    final static public float maxHealth = 1250.0f;       // 1200 -> 1250: no two T3s share an HP total (lovish, Oct 20); the shell is the point of a fortress that surfaces among enemies
    final static public float armorValue = 6.0f;          // the shell
    final static public float movementSpeed = 0.22f;      // 0.12 -> 0.22 (t3-roles): steady on land - a heavy line's pace, not a crawl; the water is where it is fast
    final static public float rangedResist = 0.3f;
    public int maxResources = 100;

    /** Extra Tides speed on water or a tidepool, on top of the faction's +20%: x2.5 wet. */
    public static final double WET_SPEED_BONUS = 1.30;
    /** The counter: artillery and siege crack the fortress on its back (see {@link #isSiegeHit}). */
    public static final float SIEGE_MULTIPLIER = 1.75f;
    /** An attacker with at least this range counts as artillery (the HUD role card's rule, UnitInfoCard). */
    public static final float ARTILLERY_RANGE = 20f;
    /** The claws' cleave: half of a pinch on the enemies round the target. */
    public static final float CLEAVE_RADIUS = 3.0f;
    public static final float CLEAVE_SPLASH = 0.5f;

    /** The dive: how far, how fast under the surface, the boil at the landing, how close to water it must surface. */
    public static final float DIVE_RANGE = 28f;
    public static final double DIVE_SPEED = 1.0;
    public static final int DIVE_MIN_TICKS = 20;
    public static final int DIVE_TELEGRAPH_TICKS = 20;
    public static final int SHORE_REACH = 2;

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
    /** The dive's state: ticks left under the surface, where it went down and where it rises; 0 = not diving. Not saved. */
    private int diveTicks = 0, diveTotal = 0;
    @Nullable private Vec3 diveFrom = null, diveTo = null;
    /** What the last surfacing did (game test): the units its slam hit. */
    public int lastSurfaceHits = 0;

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
     * {@link #SIEGE_MULTIPLIER}x - the fortress counter.
     */
    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (isBreathingDamage(source))
            return false;
        if (isSiegeHit(source))
            amount *= SIEGE_MULTIPLIER;
        return super.hurt(source, amount);
    }

    /** Under the surface nothing reaches it (the void and /kill still do) - the Drill Burrower's rule. */
    @Override
    public boolean isInvulnerableTo(@NotNull DamageSource source) {
        if (diveTicks > 0 && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
            return true;
        return super.isInvulnerableTo(source);
    }

    // mass: never pushed, shoves small bodies aside (TitanMass)
    @Override
    public boolean isPushable() {
        return TitanMass.isPushable();
    }

    @Override
    protected void doPush(@NotNull Entity other) {
        TitanMass.doPush(this, other);
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

    // AM's pinch (with its claw animation), on the vanilla attack path RoN's melee goals call; spray where it lands,
    // and the cleave: half of the pinch on the enemies round the target (its claws are the width of a doorway)
    @Override
    public boolean doHurtTarget(@NotNull Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SPLASH, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                10, 0.4, 0.3, 0.4, 0.1);
            if (UnitDamage.meleeSplash(sl, this, target, CLEAVE_RADIUS, getUnitAttackDamage() * CLEAVE_SPLASH) > 0)
                sl.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 1, target.getZ(), 3, 1.2, 0.2, 1.2, 0);
        }
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
        if (diveTicks > 0) {
            tickDive(sl);
            return;   // under the surface it lays no wake
        }
        // staggered by entity id so a few Leviathans never all raise pools on the same tick
        if ((tickCount + getId()) % TRAIL_PASS_TICKS == 0)
            trail(sl);
    }

    // ---- Deep Dive ----------------------------------------------------------------------------------------------

    /** Is this entity a Leviathan under the surface right now (enemies must not see, target or hurt it)? */
    public static boolean isSubmerged(Entity e) {
        return e instanceof LeviathanUnit l && l.diveTicks > 0;
    }

    public boolean isDiving() { return diveTicks > 0; }
    public int getDiveTicks() { return diveTicks; }

    /** In the water right now: real water under it, or a tidepool (the Tides pass' own test). */
    public boolean isInTheWater() {
        return isInWater() || TidesServerEvents.isWet(this);
    }

    /**
     * Water (or a tidepool) within {@link #SHORE_REACH} blocks of the column at {@code pos}, from one block under its
     * feet to one above: a river bank, the coast, a pool. A few block-state reads.
     */
    public static boolean nearWater(ServerLevel sl, BlockPos pos) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -SHORE_REACH; dx <= SHORE_REACH; dx++)
            for (int dz = -SHORE_REACH; dz <= SHORE_REACH; dz++)
                for (int dy = -1; dy <= 1; dy++) {
                    p.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    var bs = sl.getBlockState(p);
                    if (bs.getFluidState().is(FluidTags.WATER) || bs.getBlock() instanceof com.solegendary.reignofnether.blocks.TidepoolBlock)
                        return true;
                }
        return false;
    }

    /**
     * Orders a Deep Dive toward {@code at} (pulled in to {@link #DIVE_RANGE}, then back along the line to the nearest
     * spot by water): the Leviathan goes under where it stands and rises there. False when it is not in the water,
     * already under, or the spot (and nothing wet on the way to it) is too close. Public for the ability and the game
     * test.
     */
    public boolean startDive(Vec3 at) {
        if (!(level() instanceof ServerLevel sl) || diveTicks > 0 || at == null || !isInTheWater())
            return false;
        Vec3 flat = new Vec3(at.x - getX(), 0, at.z - getZ());
        double dist = flat.length();
        if (dist < 2.0)
            return false;
        if (dist > DIVE_RANGE) {
            flat = flat.scale(DIVE_RANGE / dist);
            dist = DIVE_RANGE;
        }
        Vec3 dir = flat.scale(1.0 / dist);
        // the water's edge nearest the spot: walk back along the line until a column has water within reach
        Vec3 land = null;
        for (double t = dist; t >= 2.0; t -= 1.0) {
            Vec3 c = position().add(dir.scale(t));
            BlockPos column = BlockPos.containing(c.x, surfaceY(sl, c), c.z);
            if (nearWater(sl, column)) {
                land = new Vec3(c.x, column.getY(), c.z);
                break;
            }
        }
        if (land == null)
            return false;
        Unit.fullResetBehaviours(this);   // the dive is an order: the AI must not walk the empty spot about
        diveFrom = position();
        diveTo = land;
        diveTotal = Math.max(DIVE_MIN_TICKS, (int) Math.ceil(position().distanceTo(land) / DIVE_SPEED));
        diveTicks = diveTotal;
        lastSurfaceHits = 0;
        setInvisible(true);
        getNavigation().stop();
        sl.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.5, getZ(), 40, getBbWidth() * 0.5, 0.3, getBbWidth() * 0.5, 0.1);
        sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, getX(), getY() + 0.3, getZ(), 20, 1.0, 0.3, 1.0, 0.05);
        sl.playSound(null, blockPosition(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.NEUTRAL, 2.5f, 0.5f);
        return true;
    }

    /** Under the surface: the bubble trail along the way, the boil at the landing for the last second, then the rise. */
    private void tickDive(ServerLevel sl) {
        diveTicks--;
        getNavigation().stop();
        setDeltaMovement(0, Math.min(0, getDeltaMovement().y), 0);
        if (diveFrom == null || diveTo == null) {
            if (diveTicks <= 0) {
                diveTicks = 0;
                setInvisible(false);
            }
            return;
        }
        if (diveTicks > 0) {
            if (diveTicks % 4 == 0) {
                double t = 1.0 - (double) diveTicks / diveTotal;
                double x = diveFrom.x + (diveTo.x - diveFrom.x) * t, z = diveFrom.z + (diveTo.z - diveFrom.z) * t;
                double y = diveFrom.y + (diveTo.y - diveFrom.y) * t;   // visual only: no block lookups
                sl.sendParticles(ParticleTypes.BUBBLE_POP, x, y + 0.4, z, 3, 0.6, 0.1, 0.6, 0.02);
                if (diveTicks <= DIVE_TELEGRAPH_TICKS)
                    sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, diveTo.x, diveTo.y + 0.3, diveTo.z, 10, 1.5, 0.2, 1.5, 0.08);
            }
            return;
        }
        surface(sl);
    }

    /**
     * The floor to rise on at {@code at}: the nearest standable cell (two free cells over something solid or water,
     * never inside a block) searched up then down from the aimed height; the heightmap as a fallback.
     */
    static int surfaceY(ServerLevel sl, Vec3 at) {
        int x = (int) Math.floor(at.x), z = (int) Math.floor(at.z), y0 = (int) Math.floor(at.y + 0.01);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int k = 0; k <= 16; k++) {
            int y = y0 + (k % 2 == 0 ? k / 2 : -(k + 1) / 2);   // 0, -1, +1, -2, +2 ...
            if (standable(sl, p, x, y, z))
                return y;
        }
        return sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    private static boolean standable(ServerLevel sl, BlockPos.MutableBlockPos p, int x, int y, int z) {
        p.set(x, y - 1, z);
        var below = sl.getBlockState(p);
        if (below.getCollisionShape(sl, p).isEmpty() && !below.getFluidState().is(FluidTags.WATER))
            return false;
        p.set(x, y, z);
        if (!sl.getBlockState(p).getCollisionShape(sl, p).isEmpty())
            return false;
        p.set(x, y + 1, z);
        return sl.getBlockState(p).getCollisionShape(sl, p).isEmpty();
    }

    /** Rises at the dive's end: moved there, visible again, and the tidal slam on everything round it. */
    private void surface(ServerLevel sl) {
        Vec3 to = diveTo;
        Vec3 dir = new Vec3(to.x - diveFrom.x, 0, to.z - diveFrom.z);
        dir = dir.lengthSqr() < 0.01 ? Vec3.directionFromRotation(0, getYRot()) : dir.normalize();
        diveTicks = 0;
        diveFrom = diveTo = null;
        teleportTo(to.x, surfaceY(sl, to), to.z);
        setInvisible(false);
        lastSurfaceHits = TidalSlam.slam(sl, this, getOwnerName(), position(), dir).size();
        sl.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.5, getZ(), 60, getBbWidth() * 0.6, 0.5, getBbWidth() * 0.6, 0.2);
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
        // a dive is never saved half-done: it comes back on the surface where it went under (the Burrower's rule)
        diveTicks = 0;
        diveFrom = diveTo = null;
        setInvisible(false);
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
