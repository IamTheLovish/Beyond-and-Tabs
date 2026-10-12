package com.solegendary.reignofnether.unit.units.brass;

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
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.barfx.LightningFx;

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
 * Brassworks T2 arc-thrower - the <b>Tesla Walker</b> (design/brassworks_plan.md, slice 2): a dwarf crew under a brass
 * tesla coil on the pillager body (its own skin, TeslaWalkerRenderer; empty hands - the coil is on the pack). Its
 * attack is <b>arc lightning</b>: {@link #attackDamage} to the target, then the arc jumps to the nearest not-yet-struck
 * enemy within {@link #JUMP_RADIUS} of the last one hit, x{@link #JUMP_FALLOFF} per jump, until it has struck
 * {@link #CHAIN_TARGETS} distinct enemies ({@link #chain}). Never a friend (own or allied), never the same unit twice.
 * Drawn with {@link LightningFx#bolt} between every link - no LightningBolt entity, no fire, no terrain; the jump
 * search is one UnitGrid query per link into a reused list, the struck set a reused array: nothing allocated per arc.
 * It cannot hurt buildings.
 * <p>
 * A {@link com.solegendary.reignofnether.brass.Machine}: every arc adds {@link #HEAT_PER_SHOT} heat (brass/Heat) - five
 * arcs, then a 6 s vent (it dry-fires and takes 25% more damage). Overclocked it arcs faster; a Tinker can plate it or
 * fit a longer coil (the barrel).
 */
public class TeslaWalkerUnit extends Pillager implements Unit, AttackerUnit, RangedAttackerUnit, com.solegendary.reignofnether.brass.Machine {
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
            SynchedEntityData.defineId(TeslaWalkerUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(TeslaWalkerUnit.class, EntityDataSerializers.INT);
    
    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
        SynchedEntityData.defineId(TeslaWalkerUnit.class, EntityDataSerializers.STRING);


    // ---- Brassworks Machine (brass/Heat): heat and the vent are synced for the steam, the upgrade for the render layer
    public static final EntityDataAccessor<Integer> heatDataAccessor =
            SynchedEntityData.defineId(TeslaWalkerUnit.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Integer> ventDataAccessor =
            SynchedEntityData.defineId(TeslaWalkerUnit.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Byte> upgradeDataAccessor =
            SynchedEntityData.defineId(TeslaWalkerUnit.class, EntityDataSerializers.BYTE);
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
    public static final int HEAT_PER_SHOT = 20;

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
    public ResourceCost getCost() {return ResourceCosts.TESLA_WALKER;}

    public void setAttackMoveTarget(@Nullable BlockPos bp) { this.attackMoveTarget = bp; }
    public void setFollowTarget(@Nullable LivingEntity target) { this.followTarget = target; }

    // endregion

    // the Thunder Dragon's job on foot: 10 to the first target and two jumps at x0.75 (22.6 per arc over three foes),
    // one arc every 2.5 s from 10 blocks; a sturdier crew than the T1s (70 HP, 2 armour), and it vents after five arcs
    final static public float attackDamage = 10.0f;
    final static public float attacksPerSecond = 0.4f;
    final static public float attackRange = 10.0f;
    final static public float aggroRange = 10.0f;
    final static public boolean willRetaliate = true;
    final static public boolean aggressiveWhenIdle = true;

    final static public float maxHealth = 70.0f;
    final static public float armorValue = 2.0f;
    final static public float movementSpeed = 0.24f;

    /** The arc: how many distinct enemies it strikes, how far each jump reaches, and the damage kept per jump. */
    public static final int CHAIN_TARGETS = 3;
    public static final double JUMP_RADIUS = 5.0;
    public static final float JUMP_FALLOFF = 0.75f;
    static final int ATTACK_WINDUP_TICKS = 6;

    public int maxResources = 100;

    public int fogRevealDuration = 0; // set > 0 for the client who is attacked by this unit
    public int getFogRevealDuration() { return fogRevealDuration; }
    public void setFogRevealDuration(int duration) { fogRevealDuration = duration; }

    private UnitRangedAttackGoal<? extends LivingEntity> attackGoal;

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    // the arc's bookkeeping, shared by every walker (server thread only): no allocation per arc
    private static final List<LivingEntity> scratch = new ArrayList<>();
    private static final LivingEntity[] struck = new LivingEntity[CHAIN_TARGETS];

    public TeslaWalkerUnit(EntityType<? extends Pillager> entityType, Level level) {
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
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.MOVEMENT_SPEED, TeslaWalkerUnit.movementSpeed)
                .add(Attributes.MAX_HEALTH, TeslaWalkerUnit.maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, TeslaWalkerUnit.armorValue)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    /** Overclocked machines fire faster (brass/Heat). */
    @Override
    public float getAttackCooldownMultiplier() {
        return AttackerUnit.super.getAttackCooldownMultiplier() * com.solegendary.reignofnether.brass.Heat.cooldownScale(this);
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
        com.solegendary.reignofnether.brass.Heat.tick(this, this);
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

    // empty hands (the pillager body crosses its arms): the coil is on the pack
    @Override
    public void setupEquipmentAndUpgradesServer() {
        this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    @Override
    public void performUnitRangedAttack(LivingEntity pTarget, float velocity) {
        if (pTarget == null || level().isClientSide() || this.hasEffect(MobEffectRegistrar.DISARM.get()))
            return;
        if (!com.solegendary.reignofnether.brass.Heat.canFire(this))
            return;   // venting: the goal keeps its cadence, the coil only crackles
        if (arc(pTarget) > 0) {
            com.solegendary.reignofnether.brass.Heat.onShot(this, this);
            if (pTarget instanceof Unit unit)
                FogOfWarClientboundPacket.revealRangedUnit(unit.getOwnerName(), this.getId());
        }
    }

    /** One arc at {@code target}: the chain plus a thunder crack. Returns how many units it struck. Public for the game test. */
    public int arc(LivingEntity target) {
        if (!(level() instanceof ServerLevel sl))
            return 0;
        Vec3 coil = new Vec3(getX(), getY() + getBbHeight() + 0.3, getZ());
        int hits = chain(sl, this, getOwnerName(), target, getUnitAttackDamage(), coil);
        sl.playSound(null, blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.NEUTRAL, 0.6f, 1.8f);
        return hits;
    }

    /**
     * The arc: {@code damage} to {@code first}, then to the nearest not-yet-struck enemy of {@code owner} within
     * {@link #JUMP_RADIUS} of the last, x{@link #JUMP_FALLOFF} per jump, until {@link #CHAIN_TARGETS} distinct enemies
     * are struck or nobody is in reach. A particle bolt between every link. Friends (own and allied) are never links -
     * not even the first: an arc aimed at a friend does nothing. Returns how many were hit. Public for the game test.
     */
    public static int chain(ServerLevel sl, LivingEntity self, String owner, LivingEntity first, float damage, Vec3 from) {
        if (first == null || !first.isAlive() || isFriend(sl, self, owner, first))
            return 0;
        java.util.Arrays.fill(struck, null);
        LivingEntity current = first;
        float dmg = damage;
        int hits = 0;
        for (int link = 0; link < CHAIN_TARGETS && current != null; link++) {
            struck[link] = current;
            Vec3 to = new Vec3(current.getX(), current.getY() + current.getBbHeight() * 0.6, current.getZ());
            LightningFx.bolt(sl, from.x, from.y, from.z, to.x, to.y, to.z);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 6, 0.3, 0.3, 0.3, 0.1);
            // indirect magic, not mobAttack: RoN rewrites mob-attack damage to the attacker's melee damage
            current.hurt(sl.damageSources().indirectMagic(self, self), dmg);
            hits++;
            dmg *= JUMP_FALLOFF;
            from = to;
            current = nextLink(sl, self, owner, current, link);
        }
        java.util.Arrays.fill(struck, null);   // don't pin dead units in a static slot
        return hits;
    }

    /** Own, allied, or friendly by the unit relationship rules: never a link. */
    private static boolean isFriend(ServerLevel sl, LivingEntity self, String owner, LivingEntity le) {
        if (le instanceof Unit u) {
            String other = u.getOwnerName();
            if (owner.equals(other) || com.solegendary.reignofnether.alliance.AlliancesServerEvents.isAllied(owner, other))
                return true;
        }
        return self instanceof Unit su && UnitServerEvents.getUnitToEntityRelationship(su, le) == Relationship.FRIENDLY;
    }

    /** The nearest enemy unit within {@link #JUMP_RADIUS} of {@code last} that the arc hasn't struck yet, or null. */
    private static LivingEntity nextLink(ServerLevel sl, LivingEntity self, String owner, LivingEntity last, int linksSoFar) {
        LivingEntity best = null;
        double bestD = JUMP_RADIUS * JUMP_RADIUS;
        for (LivingEntity le : UnitGrid.near(sl, last.getX(), last.getZ(), JUMP_RADIUS, scratch)) {
            if (le == self || !le.isAlive() || !(le instanceof Unit) || le.level() != sl)
                continue;
            boolean already = false;
            for (int i = 0; i <= linksSoFar; i++)
                if (struck[i] == le) { already = true; break; }
            if (already || isFriend(sl, self, owner, le))
                continue;
            double d = le.distanceToSqr(last);
            if (d < bestD) {
                bestD = d;
                best = le;
            }
        }
        scratch.clear();
        return best;
    }

    // a dwarf of the guild, not an illager: healing heals and raid/bad-omen logic leaves it alone
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
