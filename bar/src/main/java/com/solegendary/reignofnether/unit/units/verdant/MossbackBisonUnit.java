package com.solegendary.reignofnether.unit.units.verdant;

import com.github.alexthe666.alexsmobs.entity.EntityBison;
import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.ability.abilities.Eject;
import com.solegendary.reignofnether.ability.abilities.Embark;
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
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Verdant Court T2 beast - the <b>Mossback Bison</b> (design/big_units.md 1.4): Alex's Mobs' bison drawn x{@link #SCALE}
 * with a living grove on its back (MossbackBisonRenderer), on the RTS unit brain (the Stag Lancer's pattern: the AM
 * animal's own goals are replaced wholesale, its attack is a plain melee hit, never sheared, bred or fed by hand).
 * <p>
 * Signature: <b>Hollow Grove</b> - the Court's mobility at T2. It carries up to {@link #SEATS} friendly infantry
 * ({@link Embark} loads every eligible friend within {@link #EMBARK_RANGE} blocks; Eject unloads them). Riders sit in
 * the grove on its back: they take x{@link #RIDER_DAMAGE_MULT} damage (BigUnitServerEvents), heal
 * {@link #RIDER_HEAL_PER_SECOND} HP a second, and ranged riders keep shooting from up there. The bison keeps control
 * (no rider steers it), and if it dies they spill out where it fell. Four Thornbows or Shade Rangers dropped behind a
 * line at cavalry speed is the raid that wins if nobody scouts it; killing the carrier first is the answer.
 * Cost: a heal pass over at most four riders once a second.
 */
public class MossbackBisonUnit extends EntityBison implements Unit, AttackerUnit {
    public static final Abilities ABILITIES = new Abilities();
    static {
        ABILITIES.add(new Embark(), Keybindings.abilitySlot1);
        ABILITIES.add(new Eject(), Keybindings.abilitySlot2);
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
            SynchedEntityData.defineId(MossbackBisonUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(MossbackBisonUnit.class, EntityDataSerializers.INT);

    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
        SynchedEntityData.defineId(MossbackBisonUnit.class, EntityDataSerializers.STRING);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ownerDataAccessor, "");
        this.entityData.define(scenarioRoleDataAccessor, -1);
        this.entityData.define(onDeathCommandDataAccessor, "");
    }

    @Nullable
    public ResourceCost getCost() {return ResourceCosts.MOSSBACK_BISON;}
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

    final static public float attackDamage = 7.0f;
    final static public float attacksPerSecond = 0.7f;
    final static public float attackRange = 2.5f; // only used by ranged units or melee building attackers
    final static public float aggroRange = 12;
    final static public boolean willRetaliate = true; // will attack when hurt by an enemy
    final static public boolean aggressiveWhenIdle = true;

    // duel pass: T2 bodies are 100-380 HP (480 was scaled off the War Mammoth)
    final static public float maxHealth = 280.0f;
    final static public float armorValue = 0.0f;
    final static public float movementSpeed = 0.27f;   // faster than any T2 infantry it carries
    final static public float rangedDamageResist = 0.2f;
    public int maxResources = 100;

    public static final float SCALE = 1.3f;
    public static final int SEATS = 4;
    public static final double EMBARK_RANGE = 6.0;
    public static final float RIDER_DAMAGE_MULT = 0.5f;
    public static final float RIDER_HEAL_PER_SECOND = 2f;
    /** Largest body that fits in the grove: infantry, not golems, beasts or flyers. */
    public static final float MAX_RIDER_WIDTH = 1.0f, MAX_RIDER_HEIGHT = 2.5f;
    /** Seat height above the bison's feet (its rear back, x SCALE) and the four seats on it (model blocks: x, back). */
    static final double SEAT_HEIGHT = 2.1 * SCALE;
    private static final double[][] SEAT_XZ = { { 0.42, 0.25 }, { -0.42, 0.25 }, { 0.42, 0.95 }, { -0.42, 0.95 } };
    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused (server thread)

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    public MossbackBisonUnit(EntityType<? extends Animal> entityType, Level level) {
        super(entityType, level);

        updateAbilityButtons();
    }

    @Override
    public boolean removeWhenFarAway(double d) { return false; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.ATTACK_DAMAGE, MossbackBisonUnit.attackDamage)
                .add(Attributes.MOVEMENT_SPEED, MossbackBisonUnit.movementSpeed)
                .add(Attributes.MAX_HEALTH, MossbackBisonUnit.maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, MossbackBisonUnit.armorValue)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), rangedDamageResist)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    @Override // prevent vanilla logic for picking up items
    protected void pickUpItem(ItemEntity pItemEntity) { }
    @Override
    public void customServerAiStep() { }   // public: EntityBison widened it
    @Override
    public LivingEntity getTarget() {
        // goals only exist server side (Mob registers them there); the animal's own tick asks for its target too
        return this.targetGoal == null ? null : this.targetGoal.getTarget();
    }

    // no feeding, breeding, shearing or riding by hand: it is a unit, not livestock
    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        return InteractionResult.PASS;
    }

    // only cosmetic animations: AM's bison gores its target on a tick of its attack
    // animation (damage on top of RoN's attack goal), charges, and grazes grass away
    @Override
    public void setAnimation(Animation animation) {
        if (animation == NO_ANIMATION)
            super.setAnimation(animation);
    }

    // a plain melee hit: the bison's own version only starts an animation (see the class comment)
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
        if (level() instanceof ServerLevel && isAlive() && tickCount % 20 == 0 && isVehicle())
            healRiders();
    }

    /** True if {@code le} may board: a friendly, unmounted, unburdened unit small enough for the grove. */
    public boolean canCarry(LivingEntity le) {
        if (le == this || !le.isAlive() || !(le instanceof Unit u) || le.isPassenger() || le.isVehicle())
            return false;
        if (le.getBbWidth() > MAX_RIDER_WIDTH || le.getBbHeight() > MAX_RIDER_HEIGHT || le.isNoGravity())
            return false;
        String o = u.getOwnerName();
        return o != null && o.equals(getOwnerName());
    }

    /** Embark: boards every eligible friend within EMBARK_RANGE, nearest first, up to the free seats. Returns how many. */
    public int embarkNearby() {
        if (!(level() instanceof ServerLevel sl))
            return 0;
        int free = SEATS - getPassengers().size();
        if (free <= 0)
            return 0;
        List<LivingEntity> near = new ArrayList<>();
        for (LivingEntity le : UnitGrid.near(sl, getX(), getZ(), EMBARK_RANGE, scratch))
            if (canCarry(le) && le.distanceToSqr(this) <= EMBARK_RANGE * EMBARK_RANGE)
                near.add(le);
        scratch.clear();
        near.sort((a, b) -> Double.compare(a.distanceToSqr(this), b.distanceToSqr(this)));
        int boarded = 0;
        for (LivingEntity le : near) {
            if (boarded >= free)
                break;
            if (le instanceof Unit u)
                Unit.fullResetBehaviours(u);
            if (le.startRiding(this, true))
                boarded++;
        }
        if (boarded > 0)
            com.solegendary.reignofnether.sounds.Sfx.BEAST_EMBARK.play(sl, position());   // a bellow and the grove rustling
        return boarded;
    }

    /** Riders heal RIDER_HEAL_PER_SECOND each second they ride. */
    public void healRiders() {
        for (Entity e : getPassengers())
            if (e instanceof LivingEntity le && le.isAlive() && le.getHealth() < le.getMaxHealth())
                le.heal(RIDER_HEAL_PER_SECOND);
    }

    @Override
    protected boolean canAddPassenger(@NotNull Entity passenger) {
        return getPassengers().size() < SEATS;
    }

    // the bison steers itself (its own move goal), never a rider: a mob passenger would otherwise take the reins
    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        return null;
    }

    @Override
    public double getPassengersRidingOffset() {
        return SEAT_HEIGHT;
    }

    /** Seats round the grove on its back, turning with the body. */
    @Override
    protected void positionRider(@NotNull Entity passenger, @NotNull Entity.MoveFunction move) {
        int i = getPassengers().indexOf(passenger);
        if (i < 0)
            return;
        double[] seat = SEAT_XZ[Math.min(i, SEAT_XZ.length - 1)];
        double yaw = Math.toRadians(yBodyRot);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);   // forward
        double rx = Math.cos(yaw), rz = Math.sin(yaw);    // to the side
        double lx = seat[0] * SCALE, back = seat[1] * SCALE;
        move.accept(passenger, getX() + rx * lx - fx * back, getY() + getPassengersRidingOffset() + passenger.getMyRidingOffset(),
            getZ() + rz * lx - fz * back);
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

    // never a baby, never bred (an AgeableMob)
    @Override
    public @Nullable AgeableMob getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob other) {
        return null;
    }
}
