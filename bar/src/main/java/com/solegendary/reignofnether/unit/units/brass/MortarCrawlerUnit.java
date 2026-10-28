package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.unit.units.tide.BombardShell;

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

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * Brassworks artillery - the <b>Mortar Crawler</b> (design/brassworks_plan.md, slice 1): a dwarf crew hauling a brass
 * mortar carriage on the pillager body (its own skin, MortarCrawlerRenderer). Every {@link #attacksPerSecond}-paced
 * attack lobs one {@link BombardShell} on a solved arc that bursts for {@link #attackDamage} splash (never terrain).
 * Range {@link #attackRange}, blind inside {@link #MIN_RANGE}; Attack Ground shells a spot. A
 * {@link com.solegendary.reignofnether.brass.Machine}: every shell adds {@link #HEAT_PER_SHOT} heat (brass/Heat) - four
 * shells and it vents for 6 s. Overclocked it fires faster; a Tinker can plate it or lengthen its barrel.
 * <p>
 * The Rocket Battery's frame (the bow-style cooldown goal, the solved-arc shell) with one shell a shot.
 */
public class MortarCrawlerUnit extends Pillager implements Unit, AttackerUnit, RangedAttackerUnit, com.solegendary.reignofnether.brass.Machine, com.solegendary.reignofnether.unit.interfaces.FieldArtillery {
    public static final Abilities ABILITIES = new Abilities();
    static {
        ABILITIES.add(new AttackGround(MortarCrawlerUnit.attackRange), Keybindings.abilitySlot1);
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
            SynchedEntityData.defineId(MortarCrawlerUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(MortarCrawlerUnit.class, EntityDataSerializers.INT);
    
    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
        SynchedEntityData.defineId(MortarCrawlerUnit.class, EntityDataSerializers.STRING);


    // ---- Brassworks Machine (brass/Heat): heat and the vent are synced for the steam, the upgrade for the render layer
    public static final EntityDataAccessor<Integer> heatDataAccessor =
            SynchedEntityData.defineId(MortarCrawlerUnit.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> ventDataAccessor =
            SynchedEntityData.defineId(MortarCrawlerUnit.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Byte> upgradeDataAccessor =
            SynchedEntityData.defineId(MortarCrawlerUnit.class, EntityDataSerializers.BYTE);
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
    @Override public boolean isRangedMachine() { return true; }
    public static final int HEAT_PER_SHOT = 25;

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
    public boolean getWillRetaliate() { return willRetaliate; }
    public boolean getAggressiveWhenIdle() { return aggressiveWhenIdle && !isVehicle(); }
    @Nullable
    public ResourceCost getCost() {return ResourceCosts.MORTAR_CRAWLER;}

    public boolean canAttackBuildings() { return getAttackBuildingGoal() != null; }
    public void setAttackMoveTarget(@Nullable BlockPos bp) { this.attackMoveTarget = bp; }
    public void setFollowTarget(@Nullable LivingEntity target) { this.followTarget = target; }

    // endregion

    final static public float attackDamage = 14.0f;     // per shell, to every enemy in its splash
    final static public float attacksPerSecond = 0.2f;  // one shell every 5 s
    final static public float maxHealth = 32.0f;   // -20% (lovish, Oct 24: artillery death ball)
    final static public float armorValue = 1.0f;
    final static public float movementSpeed = 0.21f;
    final static public float attackRange = 22.0F;      // the Bombard Crew's reach
    final static public float aggroRange = 22;
    /** Too close to fire at: a target nearer than this is not fired on (the mortar's dead zone). */
    public static final float MIN_RANGE = 6.0f;
    @Override public float getMinRange() { return MIN_RANGE; }
    /** Shells per shot (one), the spread round the aimed spot and the ticks between two shells of one shot. */
    public static final int VOLLEY_ROCKETS = 1;
    public static final float VOLLEY_SPREAD = 0.0f;
    public static final int VOLLEY_GAP_TICKS = 3;
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

    public MortarCrawlerUnit(EntityType<? extends Pillager> entityType, Level level) {
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
                .add(Attributes.MOVEMENT_SPEED, MortarCrawlerUnit.movementSpeed)
                .add(Attributes.MAX_HEALTH, MortarCrawlerUnit.maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, MortarCrawlerUnit.armorValue)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), 20)   // it needs spotters (Paper Cranes) for its full reach
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    public void tick() {
        this.setCanPickUpLoot(true);
        super.tick();
        Unit.tick(this);
        AttackerUnit.tick(this);
        com.solegendary.reignofnether.brass.Heat.tick(this, this);
        if (attackGroundGoal != null)
            attackGroundGoal.tick();
    }

    /** Overclocked machines fire faster (brass/Heat). */
    @Override
    public float getAttackCooldownMultiplier() {
        // FieldArtillery, not AttackerUnit: it extends AttackerUnit, so AttackerUnit.super would not compile
        return com.solegendary.reignofnether.unit.interfaces.FieldArtillery.super.getAttackCooldownMultiplier()
            * com.solegendary.reignofnether.brass.Heat.cooldownScale(this);
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

    // the crew "holds a bow" only because UnitBowAttackGoal needs one to fire; the rounds are BombardShells
    @Override
    public void setupEquipmentAndUpgradesServer() {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
    }

    @Override
    public void performUnitRangedAttack(LivingEntity pTarget, float velocity) {
        // lead part of the way (the Bombard Crew's rule): the rockets fly 1-2 s
        net.minecraft.world.phys.Vec3 v = pTarget.getDeltaMovement();
        double lead = BombardShell.flightTicksFor(Math.sqrt(distanceToSqr(pTarget))) * 0.6;
        if (fireVolley(pTarget.getX() + v.x * lead, pTarget.getY(), pTarget.getZ() + v.z * lead)
                && !level().isClientSide() && pTarget instanceof Unit unit)
            FogOfWarClientboundPacket.revealRangedUnit(unit.getOwnerName(), this.getId());
    }

    // Attack Ground and building targets
    @Override
    public void performUnitRangedAttack(double x, double y, double z, float velocity) {
        fireVolley(x, y, z);
    }

    /**
     * Fires a volley at (x, y, z) unless it is inside {@link #MIN_RANGE} or still setting up (unit/SetUp): the first
     * rocket now, the rest a few ticks apart (scheduled tasks, nothing per tick), each at its own spot within
     * {@link #VOLLEY_SPREAD} of the aim. Returns whether it fired. Public for the game test.
     */
    public boolean fireVolley(double x, double y, double z) {
        if (level().isClientSide())
            return false;
        if (!com.solegendary.reignofnether.unit.SetUp.isSetUp(this))
            return false;   // still setting up after moving (FieldArtillery)
        double dx = x - getX(), dz = z - getZ();
        if (dx * dx + dz * dz < MIN_RANGE * MIN_RANGE)
            return false;
        if (!com.solegendary.reignofnether.brass.Heat.canFire(this))
            return false;   // venting: no shell
        com.solegendary.reignofnether.brass.Heat.onShot(this, this);
        for (int i = 0; i < VOLLEY_ROCKETS; i++) {
            if (i == 0)
                fireRocket(x, y, z, true);
            else
                com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents.schedule(i * VOLLEY_GAP_TICKS,
                    () -> fireRocket(x, y, z, false));
        }
        this.playSound(SoundEvents.GENERIC_EXPLODE, 0.9F, 1.3F + getRandom().nextFloat() * 0.2F);
        getMainHandItem().setDamageValue(0);
        return true;
    }

    /** One rocket of a volley; the first lands on the aim itself, the rest scatter round it. */
    void fireRocket(double x, double y, double z, boolean first) {
        if (!isAlive() || !(level() instanceof ServerLevel sl))
            return;
        double tx = x, tz = z;
        if (!first) {
            double a = getRandom().nextDouble() * Math.PI * 2, r = Math.sqrt(getRandom().nextDouble()) * VOLLEY_SPREAD;
            tx += Math.cos(a) * r;
            tz += Math.sin(a) * r;
        }
        BombardShell shell = new BombardShell(sl, this, getUnitAttackDamage());
        shell.setPos(getX(), getEyeY() + 0.5, getZ());
        shell.aimAt(tx, y, tz);
        sl.addFreshEntity(shell);
        // the mortar's look: a puff of powder smoke at the muzzle (the shell itself is the round)
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, getX(), getEyeY() + 0.6, getZ(), 6, 0.2, 0.2, 0.2, 0.03);
    }

    // a crew of dwarves, not illagers: healing heals and raid/bad-omen logic leaves it alone
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