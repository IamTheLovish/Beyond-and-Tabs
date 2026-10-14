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
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Brassworks T1 line - the <b>Iron Warden</b> (design/brassworks_plan.md, slice 1): a small crewed steam golem on the
 * vanilla iron golem model at 0.8x (IronWardenRenderer, its own brass skin from tools/gen_brass_skins.py): heavy, slow,
 * can't be knocked about, and a {@link com.solegendary.reignofnether.brass.Machine} - every swing adds
 * {@link #HEAT_PER_SHOT} heat (brass/Heat); at 100 it vents for 6 s, swinging at nothing and taking 25% more damage. A
 * Tinker can plate it (never a barrel: no gun).
 * <p>
 * Extends vanilla IronGolem with the Unit boilerplate copied, <b>not</b> IronGolemUnit: the Sunforged golem's research
 * hooks and the {@code instanceof IronGolemUnit} checks elsewhere must not see an Artificer (the Earthshaker's rule).
 */
public class IronWardenUnit extends IronGolem implements Unit, AttackerUnit, com.solegendary.reignofnether.brass.Machine {
    public static final Abilities ABILITIES = new Abilities();

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
            SynchedEntityData.defineId(IronWardenUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(IronWardenUnit.class, EntityDataSerializers.INT);

    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
        SynchedEntityData.defineId(IronWardenUnit.class, EntityDataSerializers.STRING);


    // ---- Brassworks Machine (brass/Heat): heat and the vent are synced for the steam, the upgrade for the render layer
    public static final EntityDataAccessor<Integer> heatDataAccessor =
            SynchedEntityData.defineId(IronWardenUnit.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> ventDataAccessor =
            SynchedEntityData.defineId(IronWardenUnit.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Byte> upgradeDataAccessor =
            SynchedEntityData.defineId(IronWardenUnit.class, EntityDataSerializers.BYTE);
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
    public static final int HEAT_PER_SHOT = 9;

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
    public ResourceCost getCost() {return ResourceCosts.IRON_WARDEN;}
    public boolean canAttackBuildings() {return getAttackBuildingGoal() != null;}

    public void setAttackMoveTarget(@Nullable BlockPos bp) { this.attackMoveTarget = bp; }
    public void setFollowTarget(@Nullable LivingEntity target) { this.followTarget = target; }

    // endregion

    // a T1 line body between the Jade Guard (6 dmg, ~60 HP) and the T2 golems: 72 HP, 7 a swing every 1.6 s, immovable,
    // 2 armour of brass; no ranged resist (plating is the Tinker's job) - and it vents after eleven swings
    final static public float attackDamage = 7.0f;
    final static public float attacksPerSecond = 0.6f;
    final static public float maxHealth = 72.0f;
    final static public float armorValue = 2.0f;
    final static public float movementSpeed = 0.23f;
    final static public float attackRange = 2.5f; // only used by ranged units or melee building attackers
    final static public float aggroRange = 10;
    final static public boolean willRetaliate = true; // will attack when hurt by an enemy
    final static public boolean aggressiveWhenIdle = true;
    final static public float rangedDamageResist = 0.0f;

    final static public int maxResources = 100;

    private AbstractMeleeAttackUnitGoal attackGoal;
    private MeleeAttackBuildingGoal attackBuildingGoal;

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    public IronWardenUnit(EntityType<? extends IronGolem> entityType, Level level) {
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

    private LivingEntity lastTarget = null;

    public void tick() {
        this.setCanPickUpLoot(false);
        super.tick();
        Unit.tick(this);
        AttackerUnit.tick(this);
        com.solegendary.reignofnether.brass.Heat.tick(this, this);

        // vanilla golem AI likes to attack friendly illagers - and the guild's crews all wear illager bodies - so
        // force it off unless the order was deliberate (IronGolemUnit's rule)
        if (!level().isClientSide() &&
                !getTargetGoal().forced &&
                getTarget() instanceof Unit unit &&
                AlliancesServerEvents.isAlliedOrOwned(unit.getOwnerName(), getOwnerName()))
            setTarget(lastTarget);

        lastTarget = getTarget();
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

    /** A swing while venting lands nothing; otherwise it adds heat. */
    @Override
    public boolean doHurtTarget(@NotNull net.minecraft.world.entity.Entity target) {
        if (!com.solegendary.reignofnether.brass.Heat.canFire(this))
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

    // no iron-repair by hand: an RTS unit answers to its owner's orders only
    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        return InteractionResult.PASS;
    }

    // brass, not iron: a struck boiler when hurt, a collapsing engine when it falls
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
