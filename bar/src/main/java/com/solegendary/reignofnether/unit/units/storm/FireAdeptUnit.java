package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
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
import com.solegendary.reignofnether.mana.ManaServerEvents;

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
import net.minecraft.util.FormattedCharSequence;
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
 * Stormcallers T1 skirmisher - the <b>Fire Adept</b> (design/stormcallers_plan.md, slice 1): a fire mage on the pillager
 * body (its own skin, FireAdeptRenderer) with a fan of flame. Every {@link #attacksPerSecond}-paced shot is a small
 * fireball that hits {@link #attackDamage} and sets the target burning for {@link #BURN_SECONDS} s - entity fire only, no
 * terrain fire (the hit is resolved here, nothing lands on a block), and any tidepool cells round the target boil off
 * (TidepoolServerEvents.evaporate, like the other fire attacks).
 * <p>
 * Each shot is a spell: {@link #ENERGY_PER_SHOT} energy through {@link ManaServerEvents#channelShot}. A starved Adept still
 * fires, at half rate ({@link #getAttackCooldownMultiplier} scales by the last shot's payment) - the faction's casters
 * weaken when the economy stalls instead of falling silent. Hit-scan like the Flak Swivel it copies (the fireball is a
 * BarFx K_FIREBALL shot drawn by clients), so there is no projectile entity to tick at 8v8 scale; the damage is a
 * mobProjectile hit, so RoN's ranged armour applies and the Jade Guard's mirror sees it as a projectile.
 */
public class FireAdeptUnit extends Pillager implements Unit, AttackerUnit, RangedAttackerUnit {
    public static final Abilities ABILITIES = new Abilities();

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
            SynchedEntityData.defineId(FireAdeptUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(FireAdeptUnit.class, EntityDataSerializers.INT);
    
    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
        SynchedEntityData.defineId(FireAdeptUnit.class, EntityDataSerializers.STRING);

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
    public ResourceCost getCost() {return ResourceCosts.FIRE_ADEPT;}

    public void setAttackMoveTarget(@Nullable BlockPos bp) { this.attackMoveTarget = bp; }
    public void setFollowTarget(@Nullable LivingEntity target) { this.followTarget = target; }

    // endregion

    // the plan's fireball: 6 damage every 2 s at 16 blocks (the Pillager's reach) = 3 dps, plus the burn (vanilla fire,
    // 1 a second); paid in energy, and at half rate when the pool is dry. Priced between the Harpooner and the Pillager
    final static public float attackDamage = 6.0f;
    final static public float attacksPerSecond = 0.5f;
    final static public float attackRange = 16.0f;
    final static public float aggroRange = 16.0f;
    final static public boolean willRetaliate = true;
    final static public boolean aggressiveWhenIdle = true;

    final static public float maxHealth = 45.0f;
    final static public float armorValue = 0.0f;
    final static public float movementSpeed = 0.26f;

    /** Energy per fireball (a spell: ManaServerEvents.channelShot) and how long the target burns. */
    public static final float ENERGY_PER_SHOT = 2f;
    public static final int BURN_SECONDS = 2;
    static final int ATTACK_WINDUP_TICKS = 6;

    public int maxResources = 100;

    public int fogRevealDuration = 0; // set > 0 for the client who is attacked by this unit
    public int getFogRevealDuration() { return fogRevealDuration; }
    public void setFogRevealDuration(int duration) { fogRevealDuration = duration; }

    private UnitRangedAttackGoal<? extends LivingEntity> attackGoal;

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    /** Attack cooldown scale from the last shot's payment: 1 paid, up to 2 starved (ManaServerEvents.shotCooldownScale). */
    private float manaCooldownScale = 1f;

    public FireAdeptUnit(EntityType<? extends Pillager> entityType, Level level) {
        super(entityType, level);
        updateAbilityButtons();
    }

    public boolean canAttackBuildings() { return false; }   // a skirmisher, like the Flak Swivel it copies
    public Goal getAttackBuildingGoal() { return null; }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.MOVEMENT_SPEED, FireAdeptUnit.movementSpeed)
                .add(Attributes.MAX_HEALTH, FireAdeptUnit.maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, FireAdeptUnit.armorValue)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    /** Starved shots come at half rate: the scale of the last shot's payment on top of the usual effects. */
    @Override
    public float getAttackCooldownMultiplier() {
        return AttackerUnit.super.getAttackCooldownMultiplier() * manaCooldownScale;
    }

    @Override
    public LivingEntity getTarget() {
        return this.targetGoal == null ? null : this.targetGoal.getTarget();
    }

    public void tick() {
        this.setCanPickUpLoot(false);
        super.tick();
        Unit.tick(this);
        AttackerUnit.tick(this);
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
        this.garrisonGoal = new GarrisonGoal(this);
        this.itemGoal = new UnitItemGoal(this);
        this.attackGoal = new UnitRangedAttackGoal<>(this, ATTACK_WINDUP_TICKS);
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

    // the flame fan's stand-in, visual only (damage is RoN's attack attribute, the fireball is hit-scan)
    @Override
    public void setupEquipmentAndUpgradesServer() {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BLAZE_ROD));
    }

    @Override
    public void performUnitRangedAttack(LivingEntity pTarget, float velocity) {
        if (pTarget == null || level().isClientSide() || this.hasEffect(MobEffectRegistrar.DISARM.get()))
            return;
        fireball(pTarget);
        if (pTarget instanceof Unit unit)
            FogOfWarClientboundPacket.revealRangedUnit(unit.getOwnerName(), this.getId());
    }

    /**
     * One fireball at {@code target}: pays its energy (setting the next shot's pace), hits, burns, evaporates tidepool
     * cells round it. Returns whether it hurt. Public for the game test.
     */
    public boolean fireball(LivingEntity target) {
        if (!(level() instanceof ServerLevel sl))
            return false;
        // the shot is a spell: what the pool can pay sets the pace of the next one (starved = half rate). The goal
        // re-reads the cooldown from getAttackCooldown, so resetting it now applies the new scale to this very wait
        manaCooldownScale = ManaServerEvents.shotCooldownScale(ManaServerEvents.channelShot(this, getOwnerName(), ENERGY_PER_SHOT));
        if (attackGoal != null)
            attackGoal.setToMaxAttackCooldown();
        // a projectile hit with the Adept as both projectile and owner: the mixin takes the damage from
        // getUnitAttackDamage and applies ranged armour
        boolean hurt = target.hurt(damageSources().mobProjectile(this, this), getUnitAttackDamage());
        if (hurt && target.isAlive())
            target.setSecondsOnFire(BURN_SECONDS);   // the entity burns; no fire block is ever placed
        Vec3 from = new Vec3(getX(), getEyeY() - 0.1, getZ());
        Vec3 to = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ());
        com.solegendary.reignofnether.barfx.BarFx.shot(sl, from, to, com.solegendary.reignofnether.barfx.BarFx.K_FIREBALL);
        com.solegendary.reignofnether.barfx.BarFx.impact(sl, to, to.subtract(from), com.solegendary.reignofnether.barfx.BarFx.K_FIREBALL,
            com.solegendary.reignofnether.barfx.BarFx.AT_UNIT, 0.6f);
        sl.sendParticles(ParticleTypes.FLAME, to.x, to.y, to.z, 4, 0.2, 0.2, 0.2, 0.02);
        com.solegendary.reignofnether.tide.TidepoolServerEvents.evaporate(sl, target.blockPosition(), 1);
        sl.playSound(null, blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.NEUTRAL, 0.7f, 1.5f);
        return hurt;
    }

    // a mage of the dynasty, not an illager: healing heals and raid/bad-omen logic leaves it alone
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
