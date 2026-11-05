package com.solegendary.reignofnether.unit.units.piglins;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.Earthsplitter;
import com.solegendary.reignofnether.ability.abilities.Rampage;
import com.solegendary.reignofnether.ability.abilities.WorldbreakerHurl;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.Checkpoint;
import com.solegendary.reignofnether.unit.EnemySearchBehaviour;
import com.solegendary.reignofnether.unit.T4Cap;
import com.solegendary.reignofnether.unit.TitanMass;
import com.solegendary.reignofnether.unit.UnitDamage;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Ironhide Horde <b>Tier 4: the Worldbreaker</b> (hero-and-tier4-design.md section 3 and 5, slice H5a). A Brute grown to
 * {@link #SCALE}x (placeholder body; Lane B draws the real one), trained at the Fortress after the Ascension research,
 * one alive per player ({@link T4Cap}). Ground only, melee: it never targets or hits a flyer (its weakness).
 * <ul>
 *   <li><b>Anvil Swing</b> (passive in the RTS): every {@link #ANVIL_EVERY}rd blow is a {@link #ANVIL_RADIUS}-block cleave
 *   round the target for full damage.</li>
 *   <li><b>Earthsplitter</b> ({@link Earthsplitter}, aimed): a telegraphed crack line, damage and a 1 s slow, walls too.</li>
 *   <li><b>Hurl</b> ({@link WorldbreakerHurl}, aimed): a boulder lob, 3-block splash and 5 s of burning ground.</li>
 *   <li><b>Rampage</b> ({@link Rampage}, aimed): a 3 s charge, units hit are thrown aside, buildings take damage.</li>
 * </ul>
 * Numbers are the Horde's first T3 (War Mammoth: 1150 HP, 18 damage) x the design: HP x1.8, damage x1.4. The hosted
 * hold/steer/charge input waits for H4 (Lane A); un-hosted the AutoCaster uses the three abilities by their rules.
 */
public class WorldbreakerUnit extends PiglinBrute implements Unit, AttackerUnit {
    public static final Abilities ABILITIES = new Abilities();
    static {
        ABILITIES.add(new Earthsplitter(), Keybindings.abilitySlot1);
        ABILITIES.add(new WorldbreakerHurl(), Keybindings.abilitySlot2);
        ABILITIES.add(new Rampage(), Keybindings.abilitySlot3);
    }

    /** The production item's name (EntityRegistrar.getEntityType). */
    public static final String ITEM_NAME = "Worldbreaker";

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
    public ReturnResourcesGoal getReturnResourcesGoal() { return returnResourcesGoal; }
    public int getMaxResources() { return maxResources; }

    private MoveToTargetBlockGoal moveGoal;
    private SelectedTargetGoal<? extends LivingEntity> targetGoal;
    private ReturnResourcesGoal returnResourcesGoal;
    private AbstractMeleeAttackUnitGoal attackGoal;
    private MeleeAttackBuildingGoal attackBuildingGoal;

    public LivingEntity getFollowTarget() { return followTarget; }
    public boolean getHoldPosition() { return holdPosition; }
    public void setHoldPosition(boolean holdPosition) { this.holdPosition = holdPosition; }

    private LivingEntity followTarget = null; // if nonnull, continuously moves to the target
    private boolean holdPosition = false;
    private BlockPos attackMoveTarget = null;

    // which player owns this unit? this format ensures its synched to client without having to use packets
    public String getOwnerName() { return this.entityData.get(ownerDataAccessor); }
    public void setOwnerName(String name) { this.entityData.set(ownerDataAccessor, name); }
    public static final EntityDataAccessor<String> ownerDataAccessor =
            SynchedEntityData.defineId(WorldbreakerUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(WorldbreakerUnit.class, EntityDataSerializers.INT);

    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
        SynchedEntityData.defineId(WorldbreakerUnit.class, EntityDataSerializers.STRING);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ownerDataAccessor, "");
        this.entityData.define(scenarioRoleDataAccessor, -1);
        this.entityData.define(onDeathCommandDataAccessor, "");
    }

    @Nullable
    public ResourceCost getCost() { return ResourceCosts.WORLDBREAKER; }
    public boolean getWillRetaliate() { return willRetaliate; }
    public boolean getAggressiveWhenIdle() { return aggressiveWhenIdle && !isVehicle(); }
    public BlockPos getAttackMoveTarget() { return attackMoveTarget; }
    public boolean canAttackBuildings() { return getAttackBuildingGoal() != null; }
    public Goal getAttackGoal() { return attackGoal; }
    public Goal getAttackBuildingGoal() { return attackBuildingGoal; }
    public void setAttackMoveTarget(@Nullable BlockPos bp) { this.attackMoveTarget = bp; }
    public void setFollowTarget(@Nullable LivingEntity target) { this.followTarget = target; }

    private EnemySearchBehaviour attackSearchBehaviour = EnemySearchBehaviour.NONE;
    public EnemySearchBehaviour getEnemySearchBehaviour() { return attackSearchBehaviour; }
    public void setEnemySearchBehaviour(EnemySearchBehaviour behaviour) { attackSearchBehaviour = behaviour; }
    // endregion

    // ---- stats: the War Mammoth (1150 HP, 18 damage, 0.5 hits a second) x the design (HP x1.8, damage x1.4)
    final static public float attackDamage = 25.0f;      // 18 x 1.4 = 25.2
    final static public float attacksPerSecond = 0.5f;   // the Mammoth's
    final static public float maxHealth = 2070.0f;       // 1150 x 1.8
    final static public float armorValue = 2.0f;
    final static public float movementSpeed = 0.24f;     // a heavy walker: slower than the Mammoth (0.30), a hair over a heavy line (0.22)
    final static public float attackRange = 4;           // melee reach (and building reach) of a body this big
    final static public float aggroRange = 12;
    final static public boolean willRetaliate = true;
    final static public boolean aggressiveWhenIdle = true;
    /** The body: the Brute grown this many times (placeholder; the entity's box is scaled the same). */
    public static final float SCALE = 3.0f;
    static final String KEY_ANNOUNCED = "bt_t3_announced";

    /** Anvil Swing: every this-many blows is a cleave of {@link #ANVIL_RADIUS} blocks round the target, at full damage. */
    public static final int ANVIL_EVERY = 3;
    public static final float ANVIL_RADIUS = 3.0f;

    /** Rampage: the charge lasts at most this many ticks (3 s), runs {@link #RAMPAGE_STEP} blocks a tick. */
    public static final int RAMPAGE_TICKS = 60;
    public static final double RAMPAGE_STEP = 0.5;
    public static final double RAMPAGE_HIT_RADIUS = 2.5;
    public static final float RAMPAGE_DAMAGE = 40f;
    public static final double RAMPAGE_FLING = 1.4, RAMPAGE_LIFT = 0.5;
    public static final float RAMPAGE_BUILDING_DAMAGE = 150f;

    /** Blows landed, for the Anvil Swing's count. */
    public int swings = 0;
    /** The charge's state: ticks left (0 = not charging) and its locked direction. Not saved (a reload cancels it). */
    public int rampageTicks = 0;
    private int stuck = 0;
    @Nullable public Vec3 rampageDir = null;
    private double rampageStopX, rampageStopZ;
    private boolean rampageHasStop = false;
    private final IntOpenHashSet rampageHit = new IntOpenHashSet();
    private final List<BuildingPlacement> rampageBuildings = new ArrayList<>();
    /** What the last charge did (game test): units hit, buildings hit, ticks it ran. */
    public int lastRampageHits = 0, lastRampageBuildings = 0, lastRampageTicks = 0;
    private int rampageRan = 0;

    public int maxResources = 100;
    public int fogRevealDuration = 0; // set > 0 for the client who is attacked by this unit
    public int getFogRevealDuration() { return fogRevealDuration; }
    public void setFogRevealDuration(int duration) { fogRevealDuration = duration; }

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    public WorldbreakerUnit(EntityType<? extends PiglinBrute> entityType, Level level) {
        super(entityType, level);
        updateAbilityButtons();
    }

    @Override
    public boolean removeWhenFarAway(double d) { return false; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.ARMOR, armorValue)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0.1)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    // mass: never pushed, shoves small bodies aside (TitanMass)
    @Override
    public boolean isPushable() { return TitanMass.isPushable(); }

    @Override
    protected void doPush(@NotNull Entity other) { TitanMass.doPush(this, other); }

    @Override
    protected boolean onSoulSpeedBlock() { return false; }

    @Override
    public boolean isLeftHanded() { return false; }
    @Override // prevent vanilla logic for picking up items
    protected void pickUpItem(ItemEntity pItemEntity) { }
    @Override
    public boolean isConverting() { return false; }
    @Override
    protected void customServerAiStep() { }
    @Override
    public LivingEntity getTarget() {
        LivingEntity t = this.targetGoal != null ? this.targetGoal.getTarget() : null;
        return isGroundTarget(t) ? t : null;   // a flyer is never a target
    }

    // ---- ground only: it never targets or hits a flyer (its weakness)

    /** Whether {@code e} is something this body may attack or affect: not a flying unit. */
    public static boolean isGroundTarget(@Nullable Entity e) {
        return !(e instanceof Unit u && u.isFlyingUnit());
    }

    @Override
    public void setUnitAttackTarget(@Nullable LivingEntity target) {
        if (!isGroundTarget(target))
            return;   // a click or the idle scan on a flyer is ignored
        AttackerUnit.super.setUnitAttackTarget(target);
    }

    // ---- Anvil Swing

    /** A blow never lands on a flyer; every {@link #ANVIL_EVERY}rd landed blow also cleaves round the target. */
    @Override
    public boolean doHurtTarget(@NotNull Entity target) {
        if (!isGroundTarget(target))
            return false;
        boolean hit = super.doHurtTarget(target);
        if (hit && level() instanceof ServerLevel sl && ++swings % ANVIL_EVERY == 0)
            anvilSwing(sl, target);
        return hit;
    }

    /** The cleave: full damage to every enemy ground unit within {@link #ANVIL_RADIUS} of {@code target}. Returns how many. Public for the game test. */
    public int anvilSwing(ServerLevel sl, Entity target) {
        int hit = 0;
        for (LivingEntity le : groundFoes(sl, getOwnerName(), target.getX(), target.getZ(), ANVIL_RADIUS)) {
            if (le == target)
                continue;
            if (UnitDamage.hurtExplicit(le, sl.damageSources().mobAttack(this), getUnitAttackDamage()))
                hit++;
        }
        sl.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 1.5, target.getZ(), 3, 1.5, 0.3, 1.5, 0);
        com.solegendary.reignofnether.barfx.BarFx.heavyImpact(sl, target.position(), ANVIL_RADIUS);
        return hit;
    }

    // ---- shared helpers of the abilities

    /** Enemy (not own, not allied) ground units within {@code r} blocks of (x, z). Flyers are never in it. */
    public static List<LivingEntity> groundFoes(ServerLevel sl, String owner, double x, double z, double r) {
        List<LivingEntity> out = new ArrayList<>();
        if (owner == null || owner.isEmpty())
            return out;
        for (LivingEntity le : UnitGrid.near(sl, x, z, r + 1, new ArrayList<>())) {
            if (!le.isAlive() || le.level() != sl || !(le instanceof Unit u) || u.isFlyingUnit())
                continue;
            String o = u.getOwnerName();
            if (o == null || o.isEmpty() || o.equals(owner) || AlliancesServerEvents.isAllied(owner, o))
                continue;
            double dx = le.getX() - x, dz = le.getZ() - z;
            if (dx * dx + dz * dz <= r * r)
                out.add(le);
        }
        return out;
    }

    /** Enemy attackable buildings whose footprint lies within {@code halfWidth} blocks of the segment a -> b (flat). */
    public static List<BuildingPlacement> foeBuildingsNear(ServerLevel sl, String owner, Vec3 a, Vec3 b, double halfWidth) {
        List<BuildingPlacement> out = new ArrayList<>();
        double len = Math.sqrt((b.x - a.x) * (b.x - a.x) + (b.z - a.z) * (b.z - a.z));
        int steps = Math.max(1, (int) Math.ceil(len));
        for (BuildingPlacement bp : BuildingServerEvents.getBuildings()) {
            if (bp.getLevel() != sl || !bp.isAttackable() || bp.ownerName == null || bp.ownerName.isEmpty()
                    || bp.ownerName.equals(owner) || AlliancesServerEvents.isAllied(owner, bp.ownerName))
                continue;
            if (a.y > bp.maxCorner.getY() + 4 || a.y < bp.minCorner.getY() - 4)
                continue;
            for (int i = 0; i <= steps; i++) {
                double px = a.x + (b.x - a.x) * i / steps, pz = a.z + (b.z - a.z) * i / steps;
                if (px >= bp.minCorner.getX() - halfWidth && px <= bp.maxCorner.getX() + 1 + halfWidth
                        && pz >= bp.minCorner.getZ() - halfWidth && pz <= bp.maxCorner.getZ() + 1 + halfWidth) {
                    out.add(bp);
                    break;
                }
            }
        }
        return out;
    }

    // ---- Rampage

    public boolean isRampaging() {
        return rampageTicks > 0;
    }

    /**
     * Orders a Rampage toward {@code at} (flat): locks the lane and runs for at most {@link #RAMPAGE_TICKS} ticks, ending
     * early on arriving at the spot or hitting something solid. False if already charging or the spot is on top of the body.
     * Public for the ability and the game test.
     */
    public boolean startRampage(Vec3 at) {
        if (isRampaging() || at == null)
            return false;
        Vec3 flat = new Vec3(at.x - getX(), 0, at.z - getZ());
        double dist = flat.length();
        if (dist < 1.0)
            return false;
        Unit.fullResetBehaviours(this);   // the charge is an order: the AI must not drag the body back mid-charge
        rampageDir = flat.scale(1.0 / dist);
        rampageTicks = RAMPAGE_TICKS;
        rampageRan = 0;
        rampageHasStop = true;
        rampageStopX = at.x;
        rampageStopZ = at.z;
        rampageHit.clear();
        rampageBuildings.clear();
        lastRampageHits = 0;
        lastRampageBuildings = 0;
        lastRampageTicks = 0;
        float yaw = (float) Math.toDegrees(Math.atan2(-rampageDir.x, rampageDir.z));
        setYRot(yaw);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        if (level() instanceof ServerLevel sl)
            sl.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3f, 0.5f);
        return true;
    }

    /** One tick of the charge. Public for the game test. */
    public void tickRampage(ServerLevel sl) {
        if (rampageDir == null) {
            rampageTicks = 0;
            return;
        }
        getNavigation().stop();
        rampageTicks--;
        rampageRan++;
        double x0 = getX(), z0 = getZ();
        move(MoverType.SELF, new Vec3(rampageDir.x * RAMPAGE_STEP, -0.1, rampageDir.z * RAMPAGE_STEP));
        setDeltaMovement(0, getDeltaMovement().y, 0);
        hitAlongRampage(sl);
        double dx = getX() - x0, dz = getZ() - z0;
        stuck = (dx * dx + dz * dz < RAMPAGE_STEP * RAMPAGE_STEP * 0.25) ? stuck + 1 : 0;
        boolean crashed = stuck >= 3;   // three blocked ticks in a row: a wall, a building, a cliff
        double ax = rampageStopX - getX(), az = rampageStopZ - getZ();
        boolean arrived = rampageHasStop && ax * ax + az * az < 1.5 * 1.5;
        sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 3, 1.2, 0.1, 1.2, 0.02);
        if (crashed || arrived || rampageTicks <= 0) {
            if (crashed) {
                Vec3 ahead = position().add(rampageDir.scale(getBbWidth() * 0.5 + 0.5));
                sl.sendParticles(ParticleTypes.CLOUD, ahead.x, getY() + 0.5, ahead.z, 10, 0.8, 0.5, 0.8, 0.05);
                com.solegendary.reignofnether.barfx.BarFx.heavyImpact(sl, ahead, 3f);
            }
            rampageTicks = 0;
            lastRampageTicks = rampageRan;
            lastRampageBuildings = rampageBuildings.size();
            rampageDir = null;
        }
    }

    /** Every enemy ground unit near the body not yet hit this charge: damage and a fling aside; every enemy building near: one dent. */
    void hitAlongRampage(ServerLevel sl) {
        String owner = getOwnerName();
        if (owner == null || rampageDir == null)
            return;
        double r = RAMPAGE_HIT_RADIUS + getBbWidth() * 0.5;
        for (LivingEntity le : groundFoes(sl, owner, getX(), getZ(), r)) {
            if (rampageHit.contains(le.getId()) || Math.abs(le.getY() - getY()) > 4)
                continue;
            rampageHit.add(le.getId());
            UnitDamage.hurtExplicit(le, sl.damageSources().mobAttack(this), RAMPAGE_DAMAGE);
            double ex = le.getX() - getX(), ez = le.getZ() - getZ();
            // thrown to the side it stood on: no stun, no root, just displaced
            double side = Math.signum(ex * -rampageDir.z + ez * rampageDir.x);
            if (side == 0)
                side = 1;
            double resist = Math.max(0, Math.min(1, le.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)));
            if (resist < 1 && le.isAlive()) {
                le.push(-rampageDir.z * side * RAMPAGE_FLING * (1 - resist), RAMPAGE_LIFT * (1 - resist), rampageDir.x * side * RAMPAGE_FLING * (1 - resist));
                le.hurtMarked = true;
            }
            lastRampageHits++;
        }
        Vec3 here = position();
        for (BuildingPlacement bp : foeBuildingsNear(sl, owner, here, here, r + 1)) {
            if (rampageBuildings.contains(bp))
                continue;
            rampageBuildings.add(bp);
            bp.destroyRandomBlocks(RAMPAGE_BUILDING_DAMAGE, this);
        }
    }

    // ---- tick, goals, saving

    public void tick() {
        this.setCanPickUpLoot(false);
        super.tick();
        Unit.tick(this);
        AttackerUnit.tick(this);
        if (!(level() instanceof ServerLevel sl) || !isAlive())
            return;
        TitanMass.tick(this);   // two standing titans on one spot step apart
        if (targetGoal != null && !isGroundTarget(targetGoal.getTarget()))
            targetGoal.setTarget(null);   // a flyer got through (a retaliation, a forced click): drop it
        String owner = getOwnerName();
        if (tickCount > 5 && !getPersistentData().getBoolean(KEY_ANNOUNCED) && owner != null && !owner.isEmpty()) {
            getPersistentData().putBoolean(KEY_ANNOUNCED, true);
            T4Cap.announceArrived(owner, net.minecraft.network.chat.Component.translatable("entity.reignofnether.worldbreaker_unit"));
        }
        if (rampageTicks > 0)
            tickRampage(sl);
    }

    @Override
    public void remove(@NotNull RemovalReason pReason) {
        if (this.level() instanceof ServerLevel serverLevel) {
            String command = this.getOnDeathCommand();
            if (command != null && !command.isEmpty()) {
                CommandSourceStack source = serverLevel.getServer()
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
        this.targetGoal = new SelectedTargetGoal<>(this, true, true, WorldbreakerUnit::isGroundTarget);   // ground only
        this.garrisonGoal = new GarrisonGoal(this);
        this.itemGoal = new UnitItemGoal(this);
        this.attackGoal = new MeleeAttackUnitGoal(this, false);
        this.attackBuildingGoal = new MeleeAttackBuildingGoal(this);
        this.returnResourcesGoal = new ReturnResourcesGoal(this);
    }

    @Override
    protected void registerGoals() {
        initialiseGoals();
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, usePortalGoal);
        this.goalSelector.addGoal(2, attackGoal);
        this.goalSelector.addGoal(2, returnResourcesGoal);
        this.goalSelector.addGoal(2, garrisonGoal);
        this.targetSelector.addGoal(2, targetGoal);
        this.goalSelector.addGoal(3, moveGoal);
        this.goalSelector.addGoal(4, new RandomLookAroundUnitGoal(this));
    }

    // a hammer for show: the +0 modifier keeps the item's own attack bonus out of the stat sheet
    @Override
    public void setupEquipmentAndUpgradesServer() {
        this.setItemSlot(EquipmentSlot.MAINHAND, AttackerUnit.cosmeticWeapon(Items.NETHERITE_AXE));
    }

    @Override
    public void setupEquipmentAndUpgradesClient() { }
}
