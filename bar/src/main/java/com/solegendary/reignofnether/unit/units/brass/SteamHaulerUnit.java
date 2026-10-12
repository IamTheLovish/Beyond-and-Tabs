package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.DropTurret;
import com.solegendary.reignofnether.brass.Heat;
import com.solegendary.reignofnether.brass.MachineRepair;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.Checkpoint;
import com.solegendary.reignofnether.unit.EnemySearchBehaviour;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
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
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
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
 * Brassworks T2 beast - the <b>Steam Hauler</b> (design/big_units.md 1.7): a traction engine on a great plodding
 * body - the vanilla sniffer grown x{@link #SCALE} under a boiler, a smokestack, a coal hopper, a crane arm and
 * gear-wheel sides (SteamHaulerRenderer), on the RTS unit brain (the sniffer's own brain never ticks). A
 * {@link com.solegendary.reignofnether.brass.Machine} itself: it heats, vents, takes a Tinker's plating and is mended
 * like any machine.
 * <p>
 * Signature: <b>Field Foundry</b>.
 * <ul>
 *   <li><b>Repair</b> ({@link #foundryPulse}, once a second): every friendly machine within {@link #FOUNDRY_RADIUS}
 *   blocks - not just the most damaged one, the way a Tinker works - mends {@link #REPAIR_PER_SECOND} HP. Two haulers
 *   never mend the same machine twice in a second (a machine remembers the tick it was last mended by one). A second
 *   in which it mended anything adds {@link #HEAT_PER_REPAIR_SECOND} heat - more than it cools - so a long repair
 *   run ends in a vent.</li>
 *   <li><b>Drop Turret</b> ({@link DropTurret}): a free Deployed Turret within {@link #TURRET_RANGE} blocks every 25 s,
 *   +{@link #TURRET_HEAT} heat.</li>
 *   <li><b>Heat</b>: at 100 it vents for 6 s (brass/Heat): no repairs, no turrets, 25% more damage taken.</li>
 * </ul>
 * Win condition if ignored: a Brassworks machine line that never wears down and grows a turret screen. Counter: it is
 * slow and big, its repairs run it into vents (hit it then), and Sappers and Drill Burrowers reach it behind the line.
 * Cost: one grid query a second per hauler.
 */
public class SteamHaulerUnit extends Sniffer implements Unit, AttackerUnit, com.solegendary.reignofnether.brass.Machine {

    public static final Abilities ABILITIES = new Abilities();

    //region
    @Override
    public void updateAbilityButtons() {
        abilities = ABILITIES.clone();
        if (drop == null)
            drop = new DropTurret();   // one per hauler, kept across refreshes so its cooldown survives them
        abilities.add(drop, Keybindings.abilitySlot1);
    }
    private DropTurret drop;
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
            SynchedEntityData.defineId(SteamHaulerUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(SteamHaulerUnit.class, EntityDataSerializers.INT);

    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
        SynchedEntityData.defineId(SteamHaulerUnit.class, EntityDataSerializers.STRING);


    @Nullable
    public ResourceCost getCost() {return ResourceCosts.STEAM_HAULER;}
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


    final static public float attackDamage = 6.0f;
    final static public float attacksPerSecond = 0.6f;
    final static public float attackRange = 3.0f; // only used by ranged units or melee building attackers
    final static public float aggroRange = 10;
    final static public boolean willRetaliate = true; // will attack when hurt by an enemy
    final static public boolean aggressiveWhenIdle = true;

    final static public float maxHealth = 480.0f;
    final static public float armorValue = 4.0f;
    final static public float movementSpeed = 0.20f;
    public int maxResources = 100;

    public static final float SCALE = 1.25f;
    public static final double FOUNDRY_RADIUS = 8.0;
    public static final float REPAIR_PER_SECOND = 3f;
    public static final int HEAT_PER_REPAIR_SECOND = 7;
    public static final float TURRET_RANGE = 6f;
    public static final int TURRET_HEAT = 35;

    /** Machine id -> game tick it was last mended by any hauler (the no-stacking rule). Server thread only. */
    private static final Int2LongOpenHashMap LAST_MENDED = new Int2LongOpenHashMap();
    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused (server thread)
    /** Machines the last pulse mended (game test). */
    public int lastMended = 0;

    // ---- Brassworks Machine (brass/Heat): heat and the vent are synced for the steam, the upgrade for the render layer
    public static final EntityDataAccessor<Integer> heatDataAccessor =
            SynchedEntityData.defineId(SteamHaulerUnit.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> ventDataAccessor =
            SynchedEntityData.defineId(SteamHaulerUnit.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Byte> upgradeDataAccessor =
            SynchedEntityData.defineId(SteamHaulerUnit.class, EntityDataSerializers.BYTE);
    private int overclockTicks = 0;
    @Override public int heat() { return this.entityData.get(heatDataAccessor); }
    @Override public void setHeat(int heat) { this.entityData.set(heatDataAccessor, heat); }
    @Override public int ventTicks() { return this.entityData.get(ventDataAccessor); }
    @Override public void setVentTicks(int ticks) { this.entityData.set(ventDataAccessor, ticks); }
    @Override public byte upgrade() { return this.entityData.get(upgradeDataAccessor); }
    @Override public void setUpgrade(byte upgrade) { this.entityData.set(upgradeDataAccessor, upgrade); }
    @Override public int overclockTicks() { return overclockTicks; }
    @Override public void setOverclockTicks(int ticks) { overclockTicks = ticks; }
    @Override public int heatPerShot() { return 0; }   // its ram doesn't heat it; the foundry does
    @Override public boolean isRangedMachine() { return false; }

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    public SteamHaulerUnit(EntityType<? extends Animal> entityType, Level level) {
        super(entityType, level);
        updateAbilityButtons();
    }

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
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0.3)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.BUILDING_DAMAGE_BONUS.get(), 0.5);
    }

    @Override // prevent vanilla logic for picking up items
    protected void pickUpItem(ItemEntity pItemEntity) { }
    @Override
    protected void customServerAiStep() { }   // the sniffer's brain (sniffing, digging seeds) never runs
    @Override
    public LivingEntity getTarget() {
        return this.targetGoal == null ? null : this.targetGoal.getTarget();
    }

    // no feeding or breeding: it is a machine
    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean isFood(@NotNull ItemStack stack) { return false; }

    /** A plain ram (no heat); a venting hauler's ram lands nothing, like every melee machine's. */
    @Override
    public boolean doHurtTarget(@NotNull Entity target) {
        if (!Heat.canFire(this))
            return false;
        boolean hit = target.hurt(this.damageSources().mobAttack(this), (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE));
        if (hit)
            this.doEnchantDamageEffects(this, target);
        return hit;
    }

    /**
     * One second of the Field Foundry: mends every friendly machine in FOUNDRY_RADIUS not already mended by a hauler this
     * second, and heats up if it mended anything. Nothing while venting. Returns how many it mended. Public for the
     * game test.
     */
    public int foundryPulse(ServerLevel sl) {
        lastMended = 0;
        if (Heat.isVenting(this))
            return 0;
        String owner = getOwnerName();
        long now = sl.getGameTime();
        for (LivingEntity le : UnitGrid.near(sl, getX(), getZ(), FOUNDRY_RADIUS, scratch)) {
            if (le == this || le.level() != sl || !MachineRepair.isFriendlyMachine(le, owner) || le.getHealth() >= le.getMaxHealth())
                continue;
            if (le.distanceToSqr(this) > FOUNDRY_RADIUS * FOUNDRY_RADIUS)
                continue;
            long last = LAST_MENDED.getOrDefault(le.getId(), Long.MIN_VALUE);
            if (last != Long.MIN_VALUE && now - last < 20 && now >= last)
                continue;   // another hauler mended it this second
            LAST_MENDED.put(le.getId(), now);
            le.heal(REPAIR_PER_SECOND);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 2, 0.25, 0.2, 0.25, 0.05);
            lastMended++;
        }
        scratch.clear();
        if (LAST_MENDED.size() > 512)
            LAST_MENDED.int2LongEntrySet().removeIf(en -> now - en.getLongValue() > 100);   // forget machines long since mended (or dead)
        if (lastMended > 0)
            Heat.addHeat(this, this, HEAT_PER_REPAIR_SECOND);
        return lastMended;
    }

    /**
     * Sets a free Deployed Turret at {@code aim} (pulled in to TURRET_RANGE). Null while venting or if the spawn
     * failed; otherwise the turret, and the hauler takes TURRET_HEAT heat. Public for the ability, the bot and the game test.
     */
    @Nullable
    public DeployedTurretUnit dropTurret(ServerLevel sl, @Nullable BlockPos aim) {
        if (aim == null || Heat.isVenting(this))
            return null;
        String owner = getOwnerName();
        if (owner == null || owner.isEmpty())
            return null;
        Vec3 at = Vec3.atBottomCenterOf(aim);
        Vec3 d = at.subtract(position());
        if (d.horizontalDistanceSqr() > TURRET_RANGE * TURRET_RANGE)
            at = position().add(new Vec3(d.x, 0, d.z).normalize().scale(TURRET_RANGE));
        Entity e = UnitServerEvents.spawnMob(EntityRegistrar.DEPLOYED_TURRET_UNIT.get(), sl,
            BlockPos.containing(at.x, at.y, at.z).below(), owner);
        if (!(e instanceof DeployedTurretUnit turret))
            return null;
        turret.setYRot(getYRot());
        Heat.addHeat(this, this, TURRET_HEAT);
        sl.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.5, at.z, 10, 0.4, 0.3, 0.4, 0.05);
        sl.playSound(null, BlockPos.containing(at), SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 1.2f, 0.9f);
        return turret;
    }

    public void tick() {
        this.setCanPickUpLoot(false);
        super.tick();
        Unit.tick(this);
        AttackerUnit.tick(this);
        Heat.tick(this, this);
        if (level() instanceof ServerLevel sl && isAlive() && tickCount % 20 == 0)
            foundryPulse(sl);
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
        Heat.save(this, pCompound);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag pCompound) {
        super.readAdditionalSaveData(pCompound);
        this.readUnitSaveData(pCompound);
        Heat.load(this, this, pCompound);
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
        return pSpawnData;
    }

    // never a snifflet, never bred
    @Override
    public @Nullable AgeableMob getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob other) {
        return null;
    }
}
