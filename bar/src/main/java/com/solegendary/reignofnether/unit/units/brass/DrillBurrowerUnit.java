package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.Checkpoint;
import com.solegendary.reignofnether.unit.EnemySearchBehaviour;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.Relationship;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Brassworks T2 raider - the <b>Drill Burrower</b> (design/brassworks_plan.md, slice 2): a segmented brass drill-worm
 * on the vanilla silverfish body at 2.5x (DrillBurrowerRenderer, its own skin; hitbox 1 x 0.75). On the surface it
 * grinds at whatever is in front of it; its order is <b>Burrow</b> ({@link com.solegendary.reignofnether.ability.abilities.Burrow}):
 * it bores into the ground and is gone for {@link #BURROW_TICKS} - invisible to enemies, untargetable
 * (Concealment.isConcealed asks {@link #isBurrowed}) and unhurt ({@link #isInvulnerableTo}) - while a dust trail creeps
 * toward the spot and, for the last {@link #TELEGRAPH_TICKS}, the ground cracks where it will come up. Then it bursts
 * out there, throwing every enemy within {@link #KNOCK_RADIUS} into the air for {@link #KNOCK_DAMAGE}: a raider that
 * surfaces behind the line among the artillery.
 * <p>
 * A {@link com.solegendary.reignofnether.brass.Machine}: every bite adds {@link #HEAT_PER_SHOT} heat (brass/Heat); at
 * 100 it vents for 6 s. A Tinker can plate it (never a barrel). One particle packet every 5 ticks while digging, one
 * grid query when it surfaces; nothing per tick otherwise.
 */
public class DrillBurrowerUnit extends Silverfish implements Unit, AttackerUnit, com.solegendary.reignofnether.brass.Machine {
    public static final Abilities ABILITIES = new Abilities();
    static {
        ABILITIES.add(new com.solegendary.reignofnether.ability.abilities.Burrow(), Keybindings.abilitySlot1);
    }

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

    public GarrisonGoal getGarrisonGoal() { return null; }
    public boolean canGarrison() { return getGarrisonGoal() != null; }

    UnitItemGoal itemGoal;
    @Override public UnitItemGoal getItemGoal() { return itemGoal; }

    UsePortalGoal usePortalGoal;
    public UsePortalGoal getUsePortalGoal() { return usePortalGoal; }
    public boolean canUsePortal() { return getUsePortalGoal() != null; }

    public Abilities getAbilities() {return abilities;}
    public List<ItemStack> getItems() {return items;};
    public MoveToTargetBlockGoal getMoveGoal() {return moveGoal;}
    public SelectedTargetGoal<? extends LivingEntity> getTargetGoal() {return targetGoal;}
    public Goal getAttackBuildingGoal() {return attackBuildingGoal;}
    public Goal getAttackGoal() {return attackGoal;}
    public ReturnResourcesGoal getReturnResourcesGoal() {return returnResourcesGoal;}
    public int getMaxResources() {return maxResources;}

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
    public String getOwnerName() { return this.entityData.get(ownerDataAccessor); }
    public void setOwnerName(String name) { this.entityData.set(ownerDataAccessor, name); }
    public static final EntityDataAccessor<String> ownerDataAccessor =
            SynchedEntityData.defineId(DrillBurrowerUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(DrillBurrowerUnit.class, EntityDataSerializers.INT);

    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
        SynchedEntityData.defineId(DrillBurrowerUnit.class, EntityDataSerializers.STRING);


    // ---- Brassworks Machine (brass/Heat): heat and the vent are synced for the steam, the upgrade for the render layer
    public static final EntityDataAccessor<Integer> heatDataAccessor =
            SynchedEntityData.defineId(DrillBurrowerUnit.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> ventDataAccessor =
            SynchedEntityData.defineId(DrillBurrowerUnit.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Byte> upgradeDataAccessor =
            SynchedEntityData.defineId(DrillBurrowerUnit.class, EntityDataSerializers.BYTE);
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
    @Override public boolean isRangedMachine() { return false; }
    public static final int HEAT_PER_SHOT = 10;

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
    public boolean getWillRetaliate() {return willRetaliate;}
    public boolean getAggressiveWhenIdle() {return aggressiveWhenIdle && !isVehicle();}

    @Nullable
    public ResourceCost getCost() {return ResourceCosts.DRILL_BURROWER;}
    public boolean canAttackBuildings() {return getAttackBuildingGoal() != null;}

    public void setAttackMoveTarget(@Nullable BlockPos bp) { this.attackMoveTarget = bp; }
    public void setFollowTarget(@Nullable LivingEntity target) { this.followTarget = target; }

    // endregion

    // a T2 raider a step under the Stag Lancer: 85 HP, 6 a bite every 2 s, fast; its value is where it surfaces
    final static public float attackDamage = 6.0f;
    final static public float attacksPerSecond = 0.5f;
    final static public float maxHealth = 85.0f;
    final static public float armorValue = 3.0f;
    final static public float movementSpeed = 0.30f;
    final static public float attackRange = 2.0f; // only used by ranged units or melee building attackers
    final static public float aggroRange = 10;
    final static public boolean willRetaliate = true; // will attack when hurt by an enemy
    final static public boolean aggressiveWhenIdle = true;
    final static public float rangedDamageResist = 0.0f;

    /** The dig: how long it is under, how long the surfacing spot is telegraphed, and the burst when it comes up. */
    public static final int BURROW_TICKS = 60;
    public static final int TELEGRAPH_TICKS = 40;
    public static final double KNOCK_RADIUS = 3.0;
    public static final float KNOCK_DAMAGE = 6.0f;
    public static final double KNOCK_UP = 0.7;

    private int burrowTicks = 0;
    private Vec3 burrowFrom = null, burrowTo = null;
    private static final List<LivingEntity> scratch = new ArrayList<>();   // the burst's query, reused (server thread only)

    final static public int maxResources = 100;

    private AbstractMeleeAttackUnitGoal attackGoal;
    private MeleeAttackBuildingGoal attackBuildingGoal;

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    public DrillBurrowerUnit(EntityType<? extends Silverfish> entityType, Level level) {
        super(entityType, level);
        updateAbilityButtons();
    }

    @Override
    public boolean removeWhenFarAway(double d) { return false; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.ARMOR, armorValue)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), rangedDamageResist)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    public void tick() {
        this.setCanPickUpLoot(false);
        super.tick();
        Unit.tick(this);
        AttackerUnit.tick(this);
        com.solegendary.reignofnether.brass.Heat.tick(this, this);
        if (burrowTicks > 0 && !level().isClientSide())
            tickBurrow();
    }

    /** Is this entity a Drill Burrower under the ground right now (enemies must not see, target or hurt it)? */
    public static boolean isBurrowed(net.minecraft.world.entity.Entity e) {
        return e instanceof DrillBurrowerUnit d && d.burrowTicks > 0;
    }

    public int getBurrowTicks() { return burrowTicks; }

    /**
     * Starts a dig toward {@code at} (pulled in to Burrow.RANGE). False when it is already under or the spot is on top of
     * it. Public for the ability, the bot and the game test.
     */
    public boolean startBurrow(Vec3 at) {
        if (level().isClientSide() || burrowTicks > 0 || at == null)
            return false;
        Vec3 flat = new Vec3(at.x - getX(), 0, at.z - getZ());
        double dist = flat.length();
        if (dist < 1.0)
            return false;
        int range = com.solegendary.reignofnether.ability.abilities.Burrow.RANGE;
        if (dist > range)
            at = position().add(flat.scale(range / dist));
        Unit.fullResetBehaviours(this);   // the dig is an order: the AI must not walk the empty spot about
        burrowFrom = position();
        burrowTo = new Vec3(at.x, at.y, at.z);
        burrowTicks = BURROW_TICKS;
        setInvisible(true);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                getX(), getY() + 0.2, getZ(), 20, 0.5, 0.2, 0.5, 0.1);
            sl.playSound(null, blockPosition(), SoundEvents.GRAVEL_BREAK, SoundSource.NEUTRAL, 1.2f, 0.6f);
        }
        return true;
    }

    /** Under the ground: the trail and the telegraph every 5 ticks, then the surfacing burst. */
    private void tickBurrow() {
        burrowTicks--;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (!(level() instanceof ServerLevel sl) || burrowFrom == null || burrowTo == null) {
            if (burrowTicks <= 0)
                setInvisible(false);
            return;
        }
        if (burrowTicks > 0) {
            if (burrowTicks % 5 == 0) {
                double t = 1.0 - (double) burrowTicks / BURROW_TICKS;
                double x = burrowFrom.x + (burrowTo.x - burrowFrom.x) * t, z = burrowFrom.z + (burrowTo.z - burrowFrom.z) * t;
                int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                    x, y + 0.1, z, 4, 0.3, 0.05, 0.3, 0.05);
                if (burrowTicks <= TELEGRAPH_TICKS) {
                    int ty = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(burrowTo.x), (int) Math.floor(burrowTo.z));
                    sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultBlockState()),
                        burrowTo.x, ty + 0.1, burrowTo.z, 8, 0.8, 0.05, 0.8, 0.1);
                }
            }
            return;
        }
        surface(sl);
    }

    /** Bursts up at the dig's end: moved there, visible again, every enemy round it hurt and thrown up. */
    private void surface(ServerLevel sl) {
        int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(burrowTo.x), (int) Math.floor(burrowTo.z));
        teleportTo(burrowTo.x, y, burrowTo.z);
        setInvisible(false);
        burrowFrom = burrowTo = null;
        for (LivingEntity le : UnitGrid.near(sl, getX(), getZ(), KNOCK_RADIUS, scratch)) {
            if (le == this || !le.isAlive() || le.level() != sl || !(le instanceof Unit u) || le.distanceToSqr(this) > KNOCK_RADIUS * KNOCK_RADIUS)
                continue;
            String other = u.getOwnerName();
            if (getOwnerName().equals(other) || AlliancesServerEvents.isAllied(getOwnerName(), other)
                    || UnitServerEvents.getUnitToEntityRelationship(this, le) == Relationship.FRIENDLY)
                continue;
            le.hurt(damageSources().generic(), KNOCK_DAMAGE);
            double resist = le.getAttribute(Attributes.KNOCKBACK_RESISTANCE) != null ? le.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) : 0;
            double up = KNOCK_UP * Math.max(0.2, 1 - resist);   // even a golem leaves the ground a little
            le.setDeltaMovement(le.getDeltaMovement().x, up, le.getDeltaMovement().z);
            le.hurtMarked = true;   // push the velocity to clients now
        }
        scratch.clear();
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
            getX(), getY() + 0.3, getZ(), 30, 1.0, 0.4, 1.0, 0.2);
        sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 6, 0.5, 0.2, 0.5, 0.05);
        sl.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 0.8f, 1.3f);
    }

    /** Under the ground nothing reaches it (the void and /kill still do). */
    @Override
    public boolean isInvulnerableTo(@NotNull DamageSource source) {
        if (burrowTicks > 0 && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
            return true;
        return super.isInvulnerableTo(source);
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
        setInvisible(false);   // a dig is never saved half-done: it comes back above ground
    }

    public void initialiseGoals() {
        this.usePortalGoal = new UsePortalGoal(this);
        this.moveGoal = new MoveToTargetBlockGoal(this, false, 0);
        this.targetGoal = new SelectedTargetGoal<>(this, true, true);
        this.attackGoal = new MeleeAttackUnitGoal(this, false);
        this.attackBuildingGoal = new MeleeAttackBuildingGoal(this);
        this.returnResourcesGoal = new ReturnResourcesGoal(this);
    }



    @Override
    protected void registerGoals() {
        initialiseGoals();
        this.goalSelector.addGoal(2, usePortalGoal);

        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, attackGoal);
        this.goalSelector.addGoal(2, attackBuildingGoal);
        this.goalSelector.addGoal(2, returnResourcesGoal);
        this.targetSelector.addGoal(2, targetGoal);
        this.goalSelector.addGoal(3, moveGoal);
        this.goalSelector.addGoal(4, new RandomLookAroundUnitGoal(this));
    }

    /** A bite while venting (or from under the ground) lands nothing; otherwise it adds heat. */
    @Override
    public boolean doHurtTarget(@NotNull net.minecraft.world.entity.Entity target) {
        if (!com.solegendary.reignofnether.brass.Heat.canFire(this) || burrowTicks > 0)
            return false;
        boolean hit = super.doHurtTarget(target);
        com.solegendary.reignofnether.brass.Heat.onShot(this, this);
        return hit;
    }

    /** Overclocked machines swing faster (brass/Heat). */
    @Override
    public float getAttackCooldownMultiplier() {
        return AttackerUnit.super.getAttackCooldownMultiplier() * com.solegendary.reignofnether.brass.Heat.cooldownScale(this);
    }

    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        return InteractionResult.PASS;
    }

    // a guild machine, not an arthropod
    @Override
    public @NotNull MobType getMobType() {
        return MobType.UNDEFINED;
    }

    @Override
    protected SoundEvent getAmbientSound() { return null; }

    // brass, not chitin: struck plate when hurt, a collapsing engine when it falls
    @Override
    protected SoundEvent getHurtSound(@NotNull DamageSource source) { return SoundEvents.COPPER_HIT; }
    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.COPPER_BREAK; }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor pLevel, @NotNull DifficultyInstance pDifficulty, @NotNull MobSpawnType pReason, @Nullable SpawnGroupData pSpawnData, @Nullable CompoundTag pDataTag) {
        return pSpawnData;
    }
}
