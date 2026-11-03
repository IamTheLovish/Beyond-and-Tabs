package com.solegendary.reignofnether.unit.units.shared;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.AttackGround;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientboundPacket;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.Checkpoint;
import com.solegendary.reignofnether.unit.EnemySearchBehaviour;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.tide.BombardShell;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.defence.DefenceBuilding;
import com.solegendary.reignofnether.faction.Faction;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The T1 siege lobber (design/siege_t1.md, lovish Oct 29: BAR's Rocko): every faction's T1 lab trains one, the same
 * unit under seven names and skins (the entity types in EntityRegistrar, the items in SiegeLobberProd). It breaks
 * defences: a shell takes {@link SiegeTuning#healthPerShell 54} off a tower, a trap, a wall or a gate and half that off
 * any other building ({@link #hitsAtTheFullRate}), out-reaches the anti-raid tower (15 against 14) but sees only 12, so it
 * snipes a tower when something spots it. Against units it is weak: 4 damage in a 1.5 splash, a slow 2 s lob aimed
 * where the target stands (no lead), a 3-block dead zone, no shots at air, a 1 s set-up after moving.
 * <p>
 * A copy of BombardCrewUnit (itself a thin copy of PillagerUnit on the bow-cooldown goal) with every number from
 * {@link SiegeTuning}; the illager body with a placeholder skin per faction (SiegeLobberRenderer) until Itachi 1 draws them.
 */
public class SiegeLobberUnit extends Pillager implements Unit, AttackerUnit, RangedAttackerUnit, com.solegendary.reignofnether.unit.interfaces.FieldArtillery {
    public static final Abilities ABILITIES = new Abilities();
    static {
        ABILITIES.add(new AttackGround(SiegeLobberUnit.attackRange), Keybindings.abilitySlot1);
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
    public ArrayList<Checkpoint> getCheckpoints() { return checkpoints; };

    GarrisonGoal garrisonGoal;
    public GarrisonGoal getGarrisonGoal() { return garrisonGoal; }
    public boolean canGarrison() { return getGarrisonGoal() != null; }

    UnitItemGoal itemGoal;
    @Override public UnitItemGoal getItemGoal() { return itemGoal; }

    UsePortalGoal usePortalGoal;
    public UsePortalGoal getUsePortalGoal() { return usePortalGoal; }
    public boolean canUsePortal() { return getUsePortalGoal() != null; }

	public Abilities getAbilities() { return abilities; }
    public List<ItemStack> getItems() { return items; }

    public MoveToTargetBlockGoal getMoveGoal() { return moveGoal; }
    public SelectedTargetGoal<? extends LivingEntity> getTargetGoal() { return targetGoal; }
    public Goal getAttackBuildingGoal() { return attackBuildingGoal; }
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
            SynchedEntityData.defineId(SiegeLobberUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(SiegeLobberUnit.class, EntityDataSerializers.INT);
    
    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
        SynchedEntityData.defineId(SiegeLobberUnit.class, EntityDataSerializers.STRING);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ownerDataAccessor, "");
        this.entityData.define(scenarioRoleDataAccessor, -1);
        this.entityData.define(onDeathCommandDataAccessor, "");
    }

    // combat stats
    public boolean getWillRetaliate() { return willRetaliate; }
    public boolean getAggressiveWhenIdle() { return aggressiveWhenIdle && !isVehicle(); }
    @Nullable
    public ResourceCost getCost() {return ResourceCosts.SIEGE_LOBBER;}

    public boolean canAttackBuildings() { return getAttackBuildingGoal() != null; }
    public void setAttackMoveTarget(@Nullable BlockPos bp) { this.attackMoveTarget = bp; }
    public void setFollowTarget(@Nullable LivingEntity target) { this.followTarget = target; }

    // endregion

    final static public float attackDamage = SiegeTuning.DAMAGE;      // per shell, to every enemy in the splash
    final static public float attacksPerSecond = SiegeTuning.ATTACKS_PER_SECOND;
    final static public float maxHealth = SiegeTuning.MAX_HEALTH;
    final static public float armorValue = SiegeTuning.ARMOR;
    final static public float movementSpeed = SiegeTuning.MOVEMENT_SPEED;
    final static public float attackRange = SiegeTuning.ATTACK_RANGE;
    final static public float aggroRange = SiegeTuning.ATTACK_RANGE;
    @Override public float getMinRange() { return SiegeTuning.MIN_RANGE; }
    @Override public int getSetUpTicks() { return SiegeTuning.SET_UP_TICKS; }
    final static public boolean willRetaliate = true; // will attack when hurt by an enemy
    final static public boolean aggressiveWhenIdle = true;

    public int maxResources = 100;

    public int fogRevealDuration = 0; // set > 0 for the client who is attacked by this unit

    public int getFogRevealDuration() {
        return fogRevealDuration;
    }

    public void setFogRevealDuration(int duration) {
        fogRevealDuration = duration;
    }

    private UnitBowAttackGoal<? extends LivingEntity> attackGoal;
    private RangedAttackBuildingGoal<?> attackBuildingGoal;

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    private RangedAttackGroundGoal<?> attackGroundGoal;
    @Override public RangedAttackGroundGoal<?> getRangedAttackGroundGoal() {
        return attackGroundGoal;
    }

    public SiegeLobberUnit(EntityType<? extends Pillager> entityType, Level level) {
        super(entityType, level);
        updateAbilityButtons();
    }

    @Override
    public void setTarget(@Nullable LivingEntity pTarget) {
        super.setTarget(pTarget);
        if (pTarget != null && getRangedAttackGroundGoal() != null)
            getRangedAttackGroundGoal().stop();
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MOVEMENT_SPEED, SiegeLobberUnit.movementSpeed)
                .add(Attributes.MAX_HEALTH, SiegeLobberUnit.maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, SiegeLobberUnit.armorValue)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), SiegeTuning.SIGHT_RANGE)   // below its range: it needs spotters
                .add(AttributeRegistrar.BUILDING_DAMAGE_BONUS.get(), SiegeTuning.BUILDING_DAMAGE_BONUS)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    public void tick() {
        this.setCanPickUpLoot(true);
        super.tick();
        Unit.tick(this);
        AttackerUnit.tick(this);
        if (attackGroundGoal != null)
            attackGroundGoal.tick();
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
        this.garrisonGoal = new GarrisonGoal(this);
        this.itemGoal = new UnitItemGoal(this);
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
        this.goalSelector.addGoal(2, returnResourcesGoal);
        this.goalSelector.addGoal(2, garrisonGoal);
        this.goalSelector.addGoal(2, attackGroundGoal);
        this.targetSelector.addGoal(2, targetGoal);
        this.goalSelector.addGoal(3, moveGoal);
        this.goalSelector.addGoal(4, new RandomLookAroundUnitGoal(this));
    }

    // the crew "holds a bow" only because UnitBowAttackGoal needs one to fire; the shell is a BombardShell
    @Override
    public void setupEquipmentAndUpgradesServer() {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
    }

    @Override
    public void performUnitRangedAttack(LivingEntity pTarget, float velocity) {
        // aimed where the target stands, no lead: the shell is 2 s in the air, so anything that keeps walking is gone
        if (fireShell(pTarget.getX(), pTarget.getY(), pTarget.getZ())
                && !level().isClientSide() && pTarget instanceof Unit unit)
            FogOfWarClientboundPacket.revealRangedUnit(unit.getOwnerName(), this.getId());
    }

    // Attack Ground and building targets
    @Override
    public void performUnitRangedAttack(double x, double y, double z, float velocity) {
        fireShell(x, y, z);
    }

    /**
     * Lobs one shell at (x, y, z) unless it is inside the dead zone or still setting up (unit/SetUp). Returns
     * whether it fired. Public for the game test (fires without waiting on the attack goal).
     */
    public boolean fireShell(double x, double y, double z) {
        if (level().isClientSide())
            return false;
        if (!com.solegendary.reignofnether.unit.SetUp.isSetUp(this))
            return false;   // still setting up after moving (FieldArtillery)
        double dx = x - getX(), dz = z - getZ();
        if (dx * dx + dz * dz < SiegeTuning.MIN_RANGE * SiegeTuning.MIN_RANGE)
            return false;
        BombardShell shell = new BombardShell(level(), this, getUnitAttackDamage()).asLobberShell(SiegeTuning.SPLASH_RADIUS,
            SiegeTuning.FLIGHT_TICKS, BombardShell.BUILDING_DAMAGE_MULT * getBuildingDamageMultiplier());
        shell.setPos(getX(), getEyeY() + 0.3, getZ());
        shell.aimAt(x, y, z);
        level().addFreshEntity(shell);
        this.playSound(SoundEvents.GENERIC_EXPLODE, 0.8F, 1.9F + getRandom().nextFloat() * 0.2F);
        if (level() instanceof ServerLevel sl)
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, getX(), getEyeY() + 0.4, getZ(), 4, 0.15, 0.15, 0.15, 0.02);
        getMainHandItem().setDamageValue(0);
        return true;
    }

    /**
     * Whether a lobber's shell hits this building at the full rate: the shared defence set (towers, traps, walls and
     * gates: DefenceBuilding) and the garrison towers. Everything else - labs, economy, capitols and the factions' own
     * T2 defences (Storm Oak, Lighthouse Battery, Rail Battery) - takes {@link SiegeTuning#OTHER_BUILDING_SHARE}.
     */
    public static boolean hitsAtTheFullRate(BuildingPlacement b) {
        Building building = b.getBuilding();
        return building instanceof DefenceBuilding || building == Buildings.WATCHTOWER
            || building == Buildings.DARK_WATCHTOWER || building == Buildings.BASTION;
    }

    // a crew, not an illager: healing heals and raid/bad-omen logic leaves it alone
    @Override
    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor pLevel, DifficultyInstance pDifficulty, MobSpawnType pReason, @Nullable SpawnGroupData pSpawnData, @Nullable CompoundTag pDataTag) {
        return pSpawnData;
    }
}