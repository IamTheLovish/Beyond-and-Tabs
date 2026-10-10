package com.solegendary.reignofnether.unit.units.tide;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.TentacleGrab;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientboundPacket;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Pillager;
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
 * Tidewrought T2 controller - the <b>Kraken Caller</b> (design/tidewrought_plan.md, slice 3): a deep-sea mystic on the
 * pillager body (its own skin, KrakenCallerRenderer) whose staff calls the sea's arms. Its own attack is a weak
 * hit-scan lash of brine ({@link #attackDamage}); its worth is <b>Tentacle Grab</b> ({@link TentacleGrab}): one enemy
 * within 12 blocks is dragged toward the caller and rooted, harder in water or on a tidepool. The caller grabs its own
 * attack target whenever the ability is ready (one cheap check every {@link #AUTO_GRAB_TICKS} ticks), and the player -
 * or the bot - can aim it at a priority target instead (artillery, a healer, a fleeing commander).
 * <p>
 * A thin copy of HarpoonerUnit (itself a copy of BombardCrewUnit on the bow-cooldown goal, holding a trident as its
 * "technically ranged" weapon), not a PillagerUnit subclass, for the same reasons. It cannot hurt buildings.
 */
public class KrakenCallerUnit extends Pillager implements Unit, AttackerUnit, RangedAttackerUnit {
    public static final Abilities ABILITIES = new Abilities();
    static {
        ABILITIES.add(new TentacleGrab(), Keybindings.abilitySlot1);
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
            SynchedEntityData.defineId(KrakenCallerUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(KrakenCallerUnit.class, EntityDataSerializers.INT);
    
    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
        SynchedEntityData.defineId(KrakenCallerUnit.class, EntityDataSerializers.STRING);

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
    public ResourceCost getCost() {return ResourceCosts.KRAKEN_CALLER;}

    public void setAttackMoveTarget(@Nullable BlockPos bp) { this.attackMoveTarget = bp; }
    public void setFollowTarget(@Nullable LivingEntity target) { this.followTarget = target; }

    // endregion

    final static public float attackDamage = 4.0f;
    final static public float attacksPerSecond = 0.5f;  // 2 dps: the lash is a nuisance, the grab is the weapon
    final static public float maxHealth = 55.0f;
    final static public float armorValue = 0.0f;
    final static public float movementSpeed = 0.25f;
    final static public float attackRange = 10.0F;      // inside its grab reach (12), so its target is always grabbable
    final static public float aggroRange = 12;
    final static public boolean willRetaliate = true; // will attack when hurt by an enemy
    final static public boolean aggressiveWhenIdle = true;

    /** How often the caller checks whether it can grab its own attack target (the check is a field read or two). */
    public static final int AUTO_GRAB_TICKS = 10;

    public int maxResources = 100;

    public int fogRevealDuration = 0; // set > 0 for the client who is attacked by this unit

    public int getFogRevealDuration() {
        return fogRevealDuration;
    }

    public void setFogRevealDuration(int duration) {
        fogRevealDuration = duration;
    }

    private UnitBowAttackGoal<? extends LivingEntity> attackGoal;

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    public KrakenCallerUnit(EntityType<? extends Pillager> entityType, Level level) {
        super(entityType, level);
        updateAbilityButtons();
    }

    public boolean canAttackBuildings() { return false; }
    public Goal getAttackBuildingGoal() { return null; }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MOVEMENT_SPEED, KrakenCallerUnit.movementSpeed)
                .add(Attributes.MAX_HEALTH, KrakenCallerUnit.maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, KrakenCallerUnit.armorValue)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    public void tick() {
        this.setCanPickUpLoot(true);
        super.tick();
        Unit.tick(this);
        AttackerUnit.tick(this);
        if (!level().isClientSide() && tickCount % AUTO_GRAB_TICKS == 0)
            autoGrab();
    }

    /**
     * The always-on autocast: when Tentacle Grab is ready and the caller is fighting something in reach, grab it. A
     * manual or bot cast puts the ability on cooldown first, so a chosen priority target is never overridden.
     */
    void autoGrab() {
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || this.hasEffect(MobEffectRegistrar.DISARM.get()))
            return;
        for (Ability a : getAbilities().get())
            if (a instanceof TentacleGrab grab && grab.isOffCooldown(this) && TentacleGrab.inReach(this, target)) {
                grab.use(level(), this, target);
                return;
            }
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
    }

    @Override
    public void resetBehaviours() {
        if (this.attackGoal != null)
            this.attackGoal.stop();
    }

    @Override
    protected void registerGoals() {
        initialiseGoals();
        this.goalSelector.addGoal(2, usePortalGoal);
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, attackGoal);
        this.goalSelector.addGoal(2, returnResourcesGoal);
        this.goalSelector.addGoal(2, garrisonGoal);
        this.targetSelector.addGoal(2, targetGoal);
        this.goalSelector.addGoal(3, moveGoal);
        this.goalSelector.addGoal(4, new RandomLookAroundUnitGoal(this));
    }

    // the caller's staff: UnitBowAttackGoal accepts a trident as its ranged weapon, and it reads as a sea-staff from above
    @Override
    public void setupEquipmentAndUpgradesServer() {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
    }

    @Override
    public void performUnitRangedAttack(LivingEntity pTarget, float velocity) {
        if (pTarget == null || level().isClientSide() || this.hasEffect(MobEffectRegistrar.DISARM.get()))
            return;
        lash(pTarget);
        if (pTarget instanceof Unit unit)
            FogOfWarClientboundPacket.revealRangedUnit(unit.getOwnerName(), this.getId());
    }

    /**
     * The brine lash: a hit-scan hurt through RoN's damage rules (a projectile with the caller as its own source, so
     * ranged armour applies, as the Harpooner's harpoon) and a short line of splash particles. Returns whether it hurt.
     */
    public boolean lash(LivingEntity target) {
        if (!(level() instanceof ServerLevel sl))
            return false;
        boolean hurt = target.hurt(damageSources().mobProjectile(this, this), getUnitAttackDamage());
        Vec3 from = new Vec3(getX(), getEyeY() - 0.2, getZ());
        Vec3 to = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ());
        for (int i = 1; i <= 3; i++) {
            Vec3 p = from.lerp(to, i / 4.0);
            sl.sendParticles(ParticleTypes.SPLASH, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.0);
        }
        sl.playSound(null, blockPosition(), SoundEvents.PLAYER_SPLASH, SoundSource.NEUTRAL, 0.6f, 1.4f);
        return hurt;
    }

    // a mystic, not an illager: healing heals and raid/bad-omen logic leaves it alone
    @Override
    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;   // amphibious (design/tidewrought_plan.md section 6)
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor pLevel, DifficultyInstance pDifficulty, MobSpawnType pReason, @Nullable SpawnGroupData pSpawnData, @Nullable CompoundTag pDataTag) {
        return pSpawnData;
    }
}
