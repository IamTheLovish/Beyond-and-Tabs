package com.solegendary.reignofnether.unit.units.piglins;

import com.github.alexthe666.alexsmobs.entity.EntityRhinoceros;
import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.BreachCharge;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.Checkpoint;
import com.solegendary.reignofnether.unit.EnemySearchBehaviour;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import com.github.alexthe666.citadel.animation.Animation;
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
 * Ironhide Horde T2 beast - the <b>Gorehorn Rhino</b> (design/big_units.md 1.2): Alex's Mobs' rhinoceros drawn
 * x{@link #SCALE} under rust-red iron plate and a spike rack (GorehornRhinoRenderer), on the RTS unit brain (the
 * Stag Lancer's pattern: the AM animal's own goals are replaced wholesale, its attack is a plain melee hit so RoN's
 * damage rules apply, never bred, never fed by hand).
 * <p>
 * Signature: <b>Breach Charge</b> ({@link BreachCharge}). A {@link #BREACH_WINDUP_TICKS}-tick wind-up draws the lane on
 * the ground (the rhino paws the earth; anyone in the lane can step aside), then the rhino travels up to
 * {@link #BREACH_LENGTH} blocks along it, one block a tick with vanilla collision. Every enemy unit it passes within
 * {@link #BREACH_HIT_RADIUS} takes {@link #BREACH_DAMAGE} once and is flung to the side it stood on; the first
 * building it runs into stops it and, if it is an enemy's, takes {@link #BREACH_BUILDING_DAMAGE} - the Horde's T2 gate
 * breaker. Unlike the War Mammoth's instant Trample it moves the rhino (engage or escape) and it breaks buildings.
 * Cost: one grid query per charge tick while charging (14 ticks every 25 s), nothing otherwise.
 */
public class GorehornRhinoUnit extends EntityRhinoceros implements Unit, AttackerUnit {
    public static final Abilities ABILITIES = new Abilities();

    //region
    @Override
    public void updateAbilityButtons() {
        abilities = ABILITIES.clone();
        if (breach == null)
            breach = new BreachCharge();   // one per rhino, kept across refreshes so its cooldown survives them
        abilities.add(breach, Keybindings.abilitySlot1);
    }
    private BreachCharge breach;
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
            SynchedEntityData.defineId(GorehornRhinoUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(GorehornRhinoUnit.class, EntityDataSerializers.INT);

    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
        SynchedEntityData.defineId(GorehornRhinoUnit.class, EntityDataSerializers.STRING);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ownerDataAccessor, "");
        this.entityData.define(scenarioRoleDataAccessor, -1);
        this.entityData.define(onDeathCommandDataAccessor, "");
    }

    @Nullable
    public ResourceCost getCost() {return ResourceCosts.GOREHORN_RHINO;}
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

    final static public float attackDamage = 10.0f;
    final static public float attacksPerSecond = 0.6f;
    final static public float attackRange = 2.5f; // only used by ranged units or melee building attackers
    final static public float aggroRange = 12;
    final static public boolean willRetaliate = true; // will attack when hurt by an enemy
    final static public boolean aggressiveWhenIdle = true;

    // duel pass: 520/14 beat its equal-metal T2 mix 25x; T2 bodies are 100-380 HP
    final static public float maxHealth = 300.0f;
    final static public float armorValue = 0.0f;
    final static public float movementSpeed = 0.24f;
    public int maxResources = 100;

    public static final float SCALE = 1.35f;
    public static final float BREACH_LENGTH = 14f;
    public static final int BREACH_WINDUP_TICKS = 10;
    public static final double BREACH_STEP = 1.0;
    public static final double BREACH_HIT_RADIUS = 1.8;
    public static final float BREACH_DAMAGE = 16f;
    public static final float BREACH_BUILDING_DAMAGE = 120f;

    /** The charge's state: wind-up ticks left, then steps left; 0/0 = not charging. Not saved (a reload cancels it). */
    public int breachWindup = 0, breachSteps = 0;
    @Nullable public Vec3 breachDir = null;
    /** Enemies already hit by the current charge (each once). */
    private final IntOpenHashSet breachHit = new IntOpenHashSet();
    /** What the last charge did (game test): units hit, and the building it crashed into (or null). */
    public int lastBreachHits = 0;
    @Nullable public BuildingPlacement lastBreachBuilding = null;
    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused (server thread)

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    public GorehornRhinoUnit(EntityType<? extends Animal> entityType, Level level) {
        super(entityType, level);

        updateAbilityButtons();
    }

    @Override
    public boolean removeWhenFarAway(double d) { return false; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.ATTACK_DAMAGE, GorehornRhinoUnit.attackDamage)
                .add(Attributes.MOVEMENT_SPEED, GorehornRhinoUnit.movementSpeed)
                .add(Attributes.MAX_HEALTH, GorehornRhinoUnit.maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, GorehornRhinoUnit.armorValue)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.BUILDING_DAMAGE_BONUS.get(), 1.5);
    }

    @Override // prevent vanilla logic for picking up items
    protected void pickUpItem(ItemEntity pItemEntity) { }
    @Override
    protected void customServerAiStep() { }
    @Override
    public LivingEntity getTarget() {
        // goals only exist server side (Mob registers them there); the rhino's own tick asks for its target too
        return this.targetGoal == null ? null : this.targetGoal.getTarget();
    }

    // no feeding, breeding, potions or riding by hand: it is a unit, not livestock
    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        return InteractionResult.PASS;
    }

    // only cosmetic animations: AM's rhino slashes and flings its target on a tick of
    // those animations (damage on top of RoN's attack goal) and grazing turns grass to dirt and heals it
    @Override
    public void setAnimation(Animation animation) {
        if (animation == NO_ANIMATION || animation == ANIMATION_FLICK_EARS)
            super.setAnimation(animation);
    }

    // a plain melee hit: the rhino's own version only starts an animation (see the class comment)
    @Override
    public boolean doHurtTarget(@NotNull Entity target) {
        boolean hit = target.hurt(this.damageSources().mobAttack(this), (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE));
        if (hit)
            this.doEnchantDamageEffects(this, target);
        return hit;
    }

    public void tick() {
        this.setCanPickUpLoot(false);
        super.tick();
        Unit.tick(this);
        AttackerUnit.tick(this);
        if (level() instanceof ServerLevel sl && isAlive() && (breachWindup > 0 || breachSteps > 0))
            tickBreach(sl);
    }

    /**
     * Orders a Breach Charge toward {@code at}: locks the lane and starts the wind-up. False if already charging or the
     * spot is on top of the rhino. Public for the game test.
     */
    public boolean startBreach(Vec3 at) {
        if (breachWindup > 0 || breachSteps > 0)
            return false;
        Vec3 flat = new Vec3(at.x - getX(), 0, at.z - getZ());
        if (flat.lengthSqr() < 0.25)
            return false;
        Unit.fullResetBehaviours(this);   // the charge is an order: the AI must not drag the rhino back mid-charge
        breachDir = flat.normalize();
        breachWindup = BREACH_WINDUP_TICKS;
        breachSteps = (int) Math.ceil(BREACH_LENGTH / BREACH_STEP);
        breachHit.clear();
        lastBreachHits = 0;
        lastBreachBuilding = null;
        setYRot((float) Math.toDegrees(Math.atan2(breachDir.z, breachDir.x)) - 90f);
        setYHeadRot(getYRot());
        setYBodyRot(getYRot());
        if (level() instanceof ServerLevel sl) {
            drawLane(sl);
            sl.playSound(null, blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.NEUTRAL, 1.5f, 0.7f);
        }
        return true;
    }

    /** One tick of the charge: wind-up (lane telegraph), then a step with hits. Public for the game test. */
    public void tickBreach(ServerLevel sl) {
        if (breachDir == null) {
            breachWindup = breachSteps = 0;
            return;
        }
        getNavigation().stop();
        if (breachWindup > 0) {
            breachWindup--;
            if (breachWindup % 4 == 0)
                drawLane(sl);
            sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.2, getZ(), 1, 0.4, 0.05, 0.4, 0.01);
            return;
        }
        breachSteps--;
        double x0 = getX(), z0 = getZ();
        move(MoverType.SELF, new Vec3(breachDir.x * BREACH_STEP, -0.1, breachDir.z * BREACH_STEP));
        setDeltaMovement(0, getDeltaMovement().y, 0);
        hitAlongCharge(sl);
        double dx = getX() - x0, dz = getZ() - z0;
        if (dx * dx + dz * dz < BREACH_STEP * BREACH_STEP * 0.25) {
            crash(sl);   // something solid: a building takes the hit, a cliff just stops it
            breachSteps = 0;
        }
        sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 2, 0.5, 0.1, 0.5, 0.02);
        if (breachSteps <= 0)
            breachDir = null;
    }

    /** Every enemy unit within BREACH_HIT_RADIUS not yet hit this charge: damage and a fling to its side of the lane. */
    void hitAlongCharge(ServerLevel sl) {
        String owner = getOwnerName();
        double r = BREACH_HIT_RADIUS + getBbWidth() * 0.5;
        for (LivingEntity le : UnitGrid.near(sl, getX(), getZ(), r, scratch)) {
            if (le == this || !le.isAlive() || le.level() != sl || !(le instanceof Unit u) || breachHit.contains(le.getId()))
                continue;
            String o = u.getOwnerName();
            if (o == null || o.isEmpty() || o.equals(owner) || AlliancesServerEvents.isAllied(owner, o))
                continue;
            double ex = le.getX() - getX(), ez = le.getZ() - getZ();
            if (ex * ex + ez * ez > r * r || Math.abs(le.getY() - getY()) > 3)
                continue;
            breachHit.add(le.getId());
            le.hurt(damageSources().mobAttack(this), BREACH_DAMAGE);
            // fling to the side it stood on (the rhino ploughs through, it doesn't carry them along)
            double side = Math.signum(ex * -breachDir.z + ez * breachDir.x);
            if (side == 0)
                side = 1;
            le.push(-breachDir.z * side * 1.1, 0.35, breachDir.x * side * 1.1);
            le.hurtMarked = true;
            lastBreachHits++;
        }
        scratch.clear();
    }

    /** Ran into something: the building just ahead, if it is an enemy's, takes BREACH_BUILDING_DAMAGE. Public for the game test. */
    @Nullable
    public BuildingPlacement crash(ServerLevel sl) {
        if (breachDir == null)
            return null;
        double reach = getBbWidth() * 0.5 + 0.8;
        BlockPos ahead = BlockPos.containing(getX() + breachDir.x * reach, getY() + 0.5, getZ() + breachDir.z * reach);
        BuildingPlacement bp = BuildingUtils.findBuilding(false, ahead);
        if (bp == null)
            bp = BuildingUtils.findBuilding(false, ahead.above());
        sl.playSound(null, blockPosition(), SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.NEUTRAL, 2f, 0.6f);
        sl.sendParticles(ParticleTypes.EXPLOSION, ahead.getX() + 0.5, ahead.getY() + 0.5, ahead.getZ() + 0.5, 1, 0, 0, 0, 0);
        String owner = getOwnerName();
        if (bp == null || bp.ownerName == null || bp.ownerName.equals(owner) || AlliancesServerEvents.isAllied(owner, bp.ownerName))
            return null;
        bp.destroyRandomBlocks(BREACH_BUILDING_DAMAGE, this);
        lastBreachBuilding = bp;
        return bp;
    }

    /** The telegraph: a dotted line of dust down the locked lane. */
    void drawLane(ServerLevel sl) {
        if (breachDir == null)
            return;
        for (double d = 1.5; d <= BREACH_LENGTH; d += 1.5)
            sl.sendParticles(ParticleTypes.CRIT, getX() + breachDir.x * d, getY() + 0.15, getZ() + breachDir.z * d, 1, 0.1, 0, 0.1, 0);
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

    // never a baby, never bred (the rhino is an AgeableMob)
    @Override
    public @Nullable AgeableMob getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob other) {
        return null;
    }
}
