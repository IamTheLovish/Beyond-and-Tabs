package com.solegendary.reignofnether.unit.units.tide;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientboundPacket;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.Checkpoint;
import com.solegendary.reignofnether.unit.EnemySearchBehaviour;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;

import net.minecraft.client.resources.language.I18n;
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

import static com.solegendary.reignofnether.util.MiscUtil.fcs;

/**
 * Tidewrought skirmisher - the <b>Harpooner</b> (design/tidewrought_plan.md, slice 2): a whaler on the pillager body
 * (its own skin, HarpoonerRenderer) throwing a barbed harpoon on a line. Every {@link #PULL_EVERY}th throw is a
 * <b>Harpoon pull</b>: the line goes taut and drags the target up to {@link #PULL_BLOCKS} blocks toward the Harpooner
 * (never closer than {@link #PULL_STOP} blocks) - a Bombard Crew's dead zone, a Cutlass Raider's Ambush or the rest of
 * the line finish the catch. Friends are never pulled, and nothing too big to haul ({@link #canPull}: Ravagers,
 * golems, treants, experimentals) is.
 * <p>
 * The harpoon is hit-scan (no projectile entity) like the Wisp Choir's bolt: one hurt() through RoN's damage rules
 * (ranged armour applies) and a short particle line - four packets a throw. The pull is one velocity change on the
 * target (a hop toward the thrower, vanilla physics does the rest; synced by the vanilla motion packet), so it costs
 * nothing per tick. It cannot hurt buildings: a harpoon catches crews, not walls.
 * <p>
 * A thin copy of BombardCrewUnit on the bow-cooldown goal (which accepts a trident as its "technically ranged"
 * weapon), not a PillagerUnit subclass, for the same reasons as the Bombard Crew.
 */
public class HarpoonerUnit extends Pillager implements Unit, AttackerUnit, RangedAttackerUnit {
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
            SynchedEntityData.defineId(HarpoonerUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(HarpoonerUnit.class, EntityDataSerializers.INT);
    
    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
        SynchedEntityData.defineId(HarpoonerUnit.class, EntityDataSerializers.STRING);

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
    public ResourceCost getCost() {return ResourceCosts.HARPOONER;}

    public void setAttackMoveTarget(@Nullable BlockPos bp) { this.attackMoveTarget = bp; }
    public void setFollowTarget(@Nullable LivingEntity target) { this.followTarget = target; }

    // endregion

    final static public float attackDamage = 5.0f;
    final static public float attacksPerSecond = 0.5f;  // a throw every 2 s: 2.5 dps, under a Pillager or a Thornbow
    final static public float maxHealth = 40.0f;
    final static public float armorValue = 0.0f;
    final static public float movementSpeed = 0.26f;
    final static public float attackRange = 12.0F;      // a little shorter than a Thornbow (13): the line has to reach
    final static public float aggroRange = 12;
    final static public boolean willRetaliate = true; // will attack when hurt by an enemy
    final static public boolean aggressiveWhenIdle = true;

    /** Every this many throws is a pull (the 1st, 4th, 7th...: an opening catch, then one every 6 s). */
    public static final int PULL_EVERY = 3;
    /** How far a pull drags its target toward the Harpooner, at most. */
    public static final double PULL_BLOCKS = 6.0;
    /** A pull never drags the target closer than this to the Harpooner. */
    public static final double PULL_STOP = 2.0;
    /** Nothing wider or taller than this is hauled in (Ravager 1.95 wide, Iron Golem 2.7 tall, treants, experimentals). */
    public static final float MAX_PULL_WIDTH = 1.5f, MAX_PULL_HEIGHT = 2.5f;
    /** Blocks travelled per unit of starting horizontal speed over a 0.42 hop (~11 ticks in the air, 0.91 drag a tick). */
    static final double AIR_GLIDE = 7.0;

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

    /** Throws made since the last pull; a throw at 0 pulls. Not saved: a reloaded Harpooner just opens with a pull. */
    private int throwsSincePull = 0;

    public HarpoonerUnit(EntityType<? extends Pillager> entityType, Level level) {
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
                .add(Attributes.MOVEMENT_SPEED, HarpoonerUnit.movementSpeed)
                .add(Attributes.MAX_HEALTH, HarpoonerUnit.maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, HarpoonerUnit.armorValue)
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

    // the harpoon: UnitBowAttackGoal accepts a trident as its ranged weapon, and it reads as one from the RTS camera
    @Override
    public void setupEquipmentAndUpgradesServer() {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
    }

    @Override
    public void performUnitRangedAttack(LivingEntity pTarget, float velocity) {
        if (pTarget == null || level().isClientSide() || this.hasEffect(MobEffectRegistrar.DISARM.get()))
            return;
        boolean pull = throwsSincePull == 0;
        throwsSincePull = (throwsSincePull + 1) % PULL_EVERY;
        harpoon(pTarget, pull);
        if (pTarget instanceof Unit unit)
            FogOfWarClientboundPacket.revealRangedUnit(unit.getOwnerName(), this.getId());
    }

    /**
     * One throw at {@code target}: the hit, its line of particles, and a {@link #pull} if {@code pull}. Returns whether
     * it hurt. Public for the game test.
     */
    public boolean harpoon(LivingEntity target, boolean pull) {
        if (!(level() instanceof ServerLevel sl))
            return false;
        // a projectile hit with the Harpooner as both projectile and owner: the mixin takes the damage from
        // getUnitAttackDamage and applies ranged armour (as the Wisp Choir's bolt)
        boolean hurt = target.hurt(damageSources().mobProjectile(this, this), getUnitAttackDamage());
        Vec3 from = new Vec3(getX(), getEyeY() - 0.2, getZ());
        Vec3 to = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ());
        for (int i = 1; i <= 3; i++) {
            Vec3 p = from.lerp(to, i / 4.0);
            sl.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
        sl.playSound(null, blockPosition(), SoundEvents.TRIDENT_THROW, SoundSource.NEUTRAL, 1.0f, 1.1f);
        if (pull && target.isAlive())
            pull(target);
        return hurt;
    }

    /** Can this be hauled in at all: alive, not a friend (own or allied), not too big. */
    public boolean canPull(LivingEntity target) {
        if (target == null || target == this || !target.isAlive() || target.level() != level())
            return false;
        if (UnitServerEvents.getUnitToEntityRelationship(this, target) == Relationship.FRIENDLY)
            return false;
        return target.getBbWidth() <= MAX_PULL_WIDTH && target.getBbHeight() <= MAX_PULL_HEIGHT;
    }

    /**
     * The pull: a hop toward the Harpooner sized to land the target {@link #PULL_BLOCKS} closer (or {@link #PULL_STOP}
     * blocks away, whichever is further). Knockback resistance (a commander's, a Brute's) shortens it as it would a
     * shove. Returns whether it pulled. Public for the game test.
     */
    public boolean pull(LivingEntity target) {
        if (!canPull(target))
            return false;
        double dx = getX() - target.getX(), dz = getZ() - target.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        double drag = Math.min(PULL_BLOCKS, dist - PULL_STOP);
        if (drag <= 0.25)
            return false;
        double kbRes = target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        double v = drag / AIR_GLIDE * Math.max(0.25, 1.0 - kbRes);
        target.setDeltaMovement(dx / dist * v, 0.42, dz / dist * v);
        target.hasImpulse = true;
        target.hurtMarked = true;   // sends the vanilla motion packet, so clients see the haul at once
        if (target instanceof Mob mob)
            mob.getNavigation().stop();
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SPLASH, target.getX(), target.getY() + 0.5, target.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
            sl.playSound(null, target.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 1.5f, 0.7f);
        }
        return true;
    }

    @Override
    public List<FormattedCharSequence> getAttackDamageStatTooltip() {
        return List.of(
                fcs(I18n.get("unitstats.reignofnether.attack_damage"), true),
                fcs(I18n.get("unitstats.reignofnether.harpoon_pull", PULL_EVERY, (int) PULL_BLOCKS))
        );
    }

    // a whaler, not an illager: healing heals and raid/bad-omen logic leaves it alone
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
