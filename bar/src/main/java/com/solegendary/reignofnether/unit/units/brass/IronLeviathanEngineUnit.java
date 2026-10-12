package com.solegendary.reignofnether.unit.units.brass;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.AttackGround;
import com.solegendary.reignofnether.brass.Heat;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientboundPacket;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.Checkpoint;
import com.solegendary.reignofnether.unit.EnemySearchBehaviour;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.tide.BombardShell;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.player.Player;
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
 * Brassworks T3 experimental - the <b>Iron Leviathan Engine</b> (design/brassworks_plan.md, slice 4): a walking
 * foundry-fortress. The ravager body grown {@link #SCALE}x in a riveted brass-and-soot skin with a foundry built on its
 * back - a blackstone deck, a furnace keep with a glowing mouth, a brass boiler, a smokestack and the heavy cannon's
 * barrel (IronLeviathanEngineRenderer's layer of block models, the Leviathan's trick). Why the ravager: it is the
 * biggest vanilla quadruped with a flat back to build on, it already reads as the faction's war engine (the Steam
 * Juggernaut is the same body at 0.8x - the engine is its grown-up cousin), and it walks on vanilla's ground paths
 * with no foreign navigation to strip (AM's elephant is the Lantern Fortress's; the iron golem is the Warden's).
 * <ul>
 * <li><b>Heavy cannon</b>: one {@link BombardShell} of {@link #attackDamage} splash every 4 s at range
 * {@link #attackRange} (no dead zone: the barrel can depress), led onto moving targets; it dents buildings like
 * every shell. Attack Ground shells a spot.</li>
 * <li><b>Drones</b>: every {@link #DRONE_INTERVAL_TICKS} it launches {@link #DRONES_PER_WAVE} {@link ClockworkDroneUnit}s
 * from its deck while fewer than {@link #DRONE_CAP} are alive; they guard it, join its fights and fall apart when it
 * dies (each drone checks its engine every tick; the engine also scraps them on removal).</li>
 * <li><b>Overheats</b> - the weakness the design asks for: a {@link com.solegendary.reignofnether.brass.Machine} with
 * {@link #HEAT_PER_SHOT} heat a shell against 4 cooling a second, so the fifth shell of sustained fire (about 16 s)
 * makes it vent: 6 s with no cannon, +25% damage taken (BrassUnitEvents), steam pouring off it.</li>
 * <li>Slow ({@link #movementSpeed}), a huge target, and <b>weak from below</b>: Sappers and Drill Burrowers hit it
 * {@link #BELOW_DAMAGE_MULT}x - its belly is boilers and gears.</li>
 * </ul>
 * T3 rules (claude/design-factions.md): ~5x a T2 heavy (the Steam Juggernaut's 200 HP), the standard experimental price
 * (the World Tree Walker's), no instant win, answerable by an equal-cost T2 army (DuelGameTests' T3 row). Arrival
 * announced to all.
 * <p>
 * Extends vanilla Ravager with the Unit boilerplate copied, <b>not</b> RavagerUnit (the Steam Juggernaut's rule): no
 * roar, no stun, no riders. It "holds" a bow only because UnitBowAttackGoal needs one to fire (the ravager model draws
 * no held item), the Mortar Crawler's trick.
 */
public class IronLeviathanEngineUnit extends Ravager implements Unit, AttackerUnit, RangedAttackerUnit, com.solegendary.reignofnether.brass.Machine {
    public static final Abilities ABILITIES = new Abilities();
    static {
        ABILITIES.add(new AttackGround(IronLeviathanEngineUnit.attackRange), Keybindings.abilitySlot1);
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
            SynchedEntityData.defineId(IronLeviathanEngineUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(IronLeviathanEngineUnit.class, EntityDataSerializers.INT);

    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
        SynchedEntityData.defineId(IronLeviathanEngineUnit.class, EntityDataSerializers.STRING);


    // ---- Brassworks Machine (brass/Heat): heat and the vent are synced for the steam, the upgrade for the render layer
    public static final EntityDataAccessor<Integer> heatDataAccessor =
            SynchedEntityData.defineId(IronLeviathanEngineUnit.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> ventDataAccessor =
            SynchedEntityData.defineId(IronLeviathanEngineUnit.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Byte> upgradeDataAccessor =
            SynchedEntityData.defineId(IronLeviathanEngineUnit.class, EntityDataSerializers.BYTE);
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
    /** 35 a shell against 4 a second of cooling: 35, 54, 73, 92, then the fifth shell (about 16 s of fire) vents it. */
    public static final int HEAT_PER_SHOT = 35;

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
    public ResourceCost getCost() {return ResourceCosts.IRON_LEVIATHAN_ENGINE;}
    public boolean canAttackBuildings() {return getAttackBuildingGoal() != null;}

    public void setAttackMoveTarget(@Nullable BlockPos bp) { this.attackMoveTarget = bp; }
    public void setFollowTarget(@Nullable LivingEntity target) { this.followTarget = target; }

    public int fogRevealDuration = 0; // set > 0 for the client who is attacked by this unit
    public int getFogRevealDuration() { return fogRevealDuration; }
    public void setFogRevealDuration(int duration) { fogRevealDuration = duration; }

    // endregion

    /** Body scale over the wild ravager (1.95 x 2.2); EntityRegistrar sizes the type to match (3.1 x 3.5). */
    public static final float SCALE = 1.6f;

    // 5x the Steam Juggernaut (200 HP, 4 armour): 1000 HP behind 6 armour; the cannon is 38 splash every 4 s (9.5 dps
    // to each body in the splash) plus the drones' chatter - and 6 s of silence after every ~16 s of fire. First duel
    // run at 1100 HP / 45 splash: it split its T3-row duels 1-1 with the equal-cost T2 mix (rule c says it must lose),
    // where every other experimental lost both
    final static public float attackDamage = 38.0f;
    final static public float attacksPerSecond = 0.25f;
    final static public float maxHealth = 1000.0f;
    final static public float armorValue = 6.0f;
    final static public float movementSpeed = 0.15f;   // slow: the Leviathan's crawl on land is 0.12, the Juggernaut 0.21
    final static public float attackRange = 16.0f;
    final static public float aggroRange = 16;
    final static public boolean willRetaliate = true; // will attack when hurt by an enemy
    final static public boolean aggressiveWhenIdle = true;
    final static public float rangedDamageResist = 0.2f;   // riveted plate, but not the Juggernaut's boiler armour

    /** The weakness from below: Sappers and Drill Burrowers strike the boilers and gears under it this much harder. */
    public static final float BELOW_DAMAGE_MULT = 2.0f;

    public static final int DRONE_INTERVAL_TICKS = 30 * 20;
    public static final int DRONES_PER_WAVE = 2;
    public static final int DRONE_CAP = 4;
    static final String KEY_ANNOUNCED = "bt_t3_announced";

    final static public int maxResources = 100;

    private UnitBowAttackGoal<? extends LivingEntity> attackGoal;
    private RangedAttackBuildingGoal<?> attackBuildingGoal;
    private RangedAttackGroundGoal<?> attackGroundGoal;
    @Override public RangedAttackGroundGoal<?> getRangedAttackGroundGoal() { return attackGroundGoal; }

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    // the live drones this engine launched (server only; pruned at each launch, scrapped when the engine goes)
    private final List<ClockworkDroneUnit> drones = new ArrayList<>(DRONE_CAP);
    private int droneTimer = DRONE_INTERVAL_TICKS;

    public IronLeviathanEngineUnit(EntityType<? extends Ravager> entityType, Level level) {
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
                .add(AttributeRegistrar.SIGHT_RANGE.get(), 20)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), rangedDamageResist)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    @Override
    public void setTarget(@Nullable LivingEntity pTarget) {
        super.setTarget(pTarget);
        if (pTarget != null && getRangedAttackGroundGoal() != null)
            getRangedAttackGroundGoal().stop();
    }

    public void tick() {
        this.setCanPickUpLoot(false);
        super.tick();
        Unit.tick(this);
        AttackerUnit.tick(this);
        Heat.tick(this, this);
        if (attackGroundGoal != null)
            attackGroundGoal.tick();
        if (!(level() instanceof ServerLevel sl) || !isAlive())
            return;
        String owner = getOwnerName();
        if (tickCount > 5 && !getPersistentData().getBoolean(KEY_ANNOUNCED) && owner != null && !owner.isEmpty()) {
            getPersistentData().putBoolean(KEY_ANNOUNCED, true);
            com.solegendary.reignofnether.player.PlayerServerEvents.sendMessageToAllPlayers(
                "server.reignofnether.t3_arrived", true, owner,
                net.minecraft.network.chat.Component.translatable("entity.reignofnether.iron_leviathan_engine_unit"));
        }
        if (--droneTimer <= 0) {
            droneTimer = DRONE_INTERVAL_TICKS;
            launchDrones(sl);
        }
        // the foundry breathes: a puff from the smokestack every second (one packet), more while venting (Heat.tick)
        if ((tickCount + getId()) % 20 == 0) {
            double fx = -Math.sin(Math.toRadians(yBodyRot)), fz = Math.cos(Math.toRadians(yBodyRot));   // facing
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX() - fx * 0.9, getY() + getBbHeight() + 1.4, getZ() - fz * 0.9,
                1, 0.1, 0.1, 0.1, 0.01);
        }
    }

    /** Live drones (pruned of the dead). Public for the game test. */
    public int liveDrones() {
        drones.removeIf(d -> d == null || !d.isAlive() || d.isRemoved());
        return drones.size();
    }

    /**
     * One drone wave: up to {@link #DRONES_PER_WAVE} new drones, never more than {@link #DRONE_CAP} alive, launched from
     * the deck with this engine's owner. Returns how many it launched. Public for the game test.
     */
    public int launchDrones(ServerLevel sl) {
        String owner = getOwnerName();
        if (owner == null || owner.isEmpty())
            return 0;
        int room = Math.min(DRONES_PER_WAVE, DRONE_CAP - liveDrones());
        int launched = 0;
        for (int i = 0; i < room; i++) {
            double side = (i % 2 == 0 ? 1 : -1) * 1.2;
            BlockPos at = BlockPos.containing(getX() + side, getY() + getBbHeight(), getZ());
            Entity e = UnitServerEvents.spawnMob(EntityRegistrar.CLOCKWORK_DRONE_UNIT.get(), sl, at.below(), owner);
            if (!(e instanceof ClockworkDroneUnit drone))
                continue;
            drone.moveTo(getX() + side, getY() + getBbHeight() + 0.3, getZ(), getYRot(), 0);
            drone.setEngine(this);
            drones.add(drone);
            launched++;
        }
        if (launched > 0) {
            sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + getBbHeight(), getZ(), 8, 0.6, 0.2, 0.6, 0.04);
            sl.playSound(null, blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.NEUTRAL, 1.0f, 0.6f);
        }
        return launched;
    }

    /** The drones go with the engine: scrapped (no death event, no wreck) when it dies or is removed. */
    private void scrapDrones() {
        for (ClockworkDroneUnit d : drones)
            if (d != null && d.isAlive())
                d.fallApart();
        drones.clear();
    }

    /**
     * A hit from a Sapper or a Drill Burrower - the units that get under the engine. BrassUnitEvents multiplies those
     * by {@link #BELOW_DAMAGE_MULT} in the hurt event (not here in {@code hurt}: RoN's actuallyHurt rewrites a unit's
     * blow to its own attack damage, so only the event sees the final amount - as for the vent's +25%).
     */
    public static boolean isStruckFromBelow(DamageSource source) {
        Entity attacker = source.getEntity();
        return attacker instanceof SapperUnit || attacker instanceof DrillBurrowerUnit;
    }

    @Override
    public void remove(@NotNull RemovalReason pReason) {
        if (this.level() instanceof ServerLevel serverLevel) {
            scrapDrones();
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
    public void die(@NotNull DamageSource source) {
        super.die(source);
        if (!level().isClientSide())
            scrapDrones();
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag pCompound) {
        super.addAdditionalSaveData(pCompound);
        this.addUnitSaveData(pCompound);
        Heat.save(this, pCompound);
        pCompound.putInt("bt_drone_timer", droneTimer);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag pCompound) {
        super.readAdditionalSaveData(pCompound);
        this.readUnitSaveData(pCompound);
        Heat.load(this, this, pCompound);
        if (pCompound.contains("bt_drone_timer"))
            droneTimer = Math.max(1, pCompound.getInt("bt_drone_timer"));
    }

    public void initialiseGoals() {
        this.usePortalGoal = new UsePortalGoal(this);
        this.moveGoal = new MoveToTargetBlockGoal(this, false, 0);
        this.targetGoal = new SelectedTargetGoal<>(this, true, false);
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
        this.goalSelector.addGoal(2, attackBuildingGoal);
        this.goalSelector.addGoal(2, attackGroundGoal);
        this.goalSelector.addGoal(2, returnResourcesGoal);
        this.targetSelector.addGoal(2, targetGoal);
        this.goalSelector.addGoal(3, moveGoal);
        this.goalSelector.addGoal(4, new RandomLookAroundUnitGoal(this));
    }

    // the engine "holds a bow" only because UnitBowAttackGoal needs one to fire; the rounds are BombardShells
    @Override
    public void setupEquipmentAndUpgradesServer() {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        this.setDropChance(EquipmentSlot.MAINHAND, 0f);
    }

    @Override
    public void performUnitRangedAttack(LivingEntity pTarget, float velocity) {
        // lead part of the way (the Mortar Crawler's rule): the shell flies 1-2 s
        Vec3 v = pTarget.getDeltaMovement();
        double lead = BombardShell.flightTicksFor(Math.sqrt(distanceToSqr(pTarget))) * 0.6;
        if (fireCannon(pTarget.getX() + v.x * lead, pTarget.getY(), pTarget.getZ() + v.z * lead)
                && !level().isClientSide() && pTarget instanceof Unit unit)
            FogOfWarClientboundPacket.revealRangedUnit(unit.getOwnerName(), this.getId());
    }

    // Attack Ground and building targets
    @Override
    public void performUnitRangedAttack(double x, double y, double z, float velocity) {
        fireCannon(x, y, z);
    }

    /**
     * One shell from the cannon at (x, y, z), unless venting: adds {@link #HEAT_PER_SHOT} heat (the fifth shell of
     * sustained fire starts the vent). Returns whether it fired. Public for the game test.
     */
    public boolean fireCannon(double x, double y, double z) {
        if (!(level() instanceof ServerLevel sl) || !isAlive())
            return false;
        if (!Heat.canFire(this))
            return false;   // venting: the goal keeps its cadence, the cannon is silent
        Heat.onShot(this, this);
        BombardShell shell = new BombardShell(sl, this, getUnitAttackDamage());
        // the barrel's mouth: over the head end of the deck
        double fx = -Math.sin(Math.toRadians(yBodyRot)), fz = Math.cos(Math.toRadians(yBodyRot));
        shell.setPos(getX() + fx * 1.2, getY() + getBbHeight() + 0.6, getZ() + fz * 1.2);
        shell.aimAt(x, y, z);
        sl.addFreshEntity(shell);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, shell.getX(), shell.getY(), shell.getZ(), 8, 0.3, 0.3, 0.3, 0.04);
        sl.sendParticles(ParticleTypes.FLAME, shell.getX(), shell.getY(), shell.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
        sl.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 1.6f, 0.6f);
        getMainHandItem().setDamageValue(0);
        return true;
    }

    /** Overclocked machines fire faster (brass/Heat). */
    @Override
    public float getAttackCooldownMultiplier() {
        return AttackerUnit.super.getAttackCooldownMultiplier() * Heat.cooldownScale(this);
    }

    // no riding by hand: an RTS unit answers to its owner's orders only
    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        return InteractionResult.PASS;
    }

    // prevents attack commands applying only to passengers (RavagerUnit's rule)
    @Override
    protected void updateControlFlags() {}

    // a blocked hit neither stuns it nor makes it roar
    @Override
    protected void blockedByShield(@NotNull LivingEntity pEntity) { }

    /** No roar: the vanilla roar knocks back and hurts everything round it, friends included. */
    @Override
    public void roar() { }

    /** Vanilla ravagers lerp their own walking speed toward a hunting speed; a unit walks at its own (RavagerUnit's rule). */
    @Override
    public void aiStep() {
        super.aiStep();
        if (this.isAlive() && this.getAttribute(Attributes.MOVEMENT_SPEED) != null)
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(movementSpeed);
    }

    // a guild engine, not a beast of the raid: no illager-only effects
    @Override
    public @NotNull MobType getMobType() {
        return MobType.UNDEFINED;
    }

    // brass, not hide: a struck boiler when hurt, a collapsing foundry when it falls, its own chuffing at rest
    @Override
    protected SoundEvent getHurtSound(@NotNull DamageSource source) { return SoundEvents.ANVIL_LAND; }
    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.ANVIL_DESTROY; }
    @Override
    protected SoundEvent getAmbientSound() { return null; }
    @Override
    protected float getSoundVolume() { return 0.6f; }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor pLevel, @NotNull DifficultyInstance pDifficulty, @NotNull MobSpawnType pReason, @Nullable SpawnGroupData pSpawnData, @Nullable CompoundTag pDataTag) {
        return pSpawnData;
    }
}
