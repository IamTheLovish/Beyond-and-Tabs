package com.solegendary.reignofnether.unit.units.tide;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.AttackGround;
import com.solegendary.reignofnether.ability.abilities.BrineMist;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientboundPacket;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.tide.TidalUnit;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.Checkpoint;
import com.solegendary.reignofnether.unit.EnemySearchBehaviour;
import com.solegendary.reignofnether.unit.TitanMass;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.hero.HeroFx;

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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Tidewrought T3 experimental, the second of the Abyssal Shipyard (slot T): the <b>Abyssal Hydra</b>
 * (second-t3-design.md, slice T3b-6). A three-headed sea serpent on a barnacled mound: ground, no melee, an anti-swarm
 * and anti-air shooter that is happiest in the water. The Leviathan is the amphibious flanker; the Hydra is the
 * ranged anchor of a fight beside the river.
 * <ul>
 *   <li><b>Three Heads</b> (autocast, {@link #attackRange} blocks, hits air): every attack each of its
 *   {@link #HEADS} heads picks its own target (its target first, then the nearest others, one head each; with fewer
 *   targets the heads share them) and spits for {@link #HEAD_DAMAGE}.</li>
 *   <li><b>Brine Mist</b> ({@link BrineMist}, tap, 25 s): a fog cloud 8 across for 6 s - friends inside are hidden,
 *   enemy shots at them miss half the time.</li>
 *   <li><b>Regrow</b> (passive): in water or on a tidepool it heals 3 HP/s (the Tides pass' 2 HP/s plus
 *   {@link #tidesRegenBonus}) and is faster on top of the faction's ({@link #WET_SPEED_BONUS}); a <i>severed head</i> (one
 *   hit above {@link #SEVER_DAMAGE}) silences one head for {@link #SEVER_TICKS} ticks (5 s).</li>
 *   <li><b>Siege</b>: artillery and siege hit it {@link #SIEGE_MULTIPLIER}x (the Leviathan's rule, LeviathanUnit.isSiegeHit).
 *   Never drowns. <b>Mass</b> ({@link TitanMass}): never pushed. Arrival announced to every player.</li>
 * </ul>
 * Countered by burst and artillery, fighting away from water, shock that kills one head; weak on dry land and against
 * spread single-target damage. Loses to a focused equal-cost T2 counter army. Placeholder look: the iron golem in a
 * deep-sea skin at {@link #SCALE}x (Lane B draws the heads, slice T3b-10).
 */
public class AbyssalHydraUnit extends IronGolem implements Unit, AttackerUnit, RangedAttackerUnit, TidalUnit {
    public static final Abilities ABILITIES = new Abilities();
    static {
        ABILITIES.add(new AttackGround(AbyssalHydraUnit.attackRange), Keybindings.abilitySlot1);
        ABILITIES.add(new BrineMist(), Keybindings.abilitySlot2);
    }

    /** The production item's name (EntityRegistrar.getEntityType). */
    public static final String ITEM_NAME = "Abyssal Hydra";

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
            SynchedEntityData.defineId(AbyssalHydraUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(AbyssalHydraUnit.class, EntityDataSerializers.INT);

    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
        SynchedEntityData.defineId(AbyssalHydraUnit.class, EntityDataSerializers.STRING);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ownerDataAccessor, "");
        this.entityData.define(scenarioRoleDataAccessor, -1);
        this.entityData.define(onDeathCommandDataAccessor, "");
    }

    @Nullable
    public ResourceCost getCost() { return ResourceCosts.TIDE_T3B; }
    public boolean getWillRetaliate() { return willRetaliate; }
    public boolean getAggressiveWhenIdle() { return aggressiveWhenIdle && !isVehicle(); }
    public BlockPos getAttackMoveTarget() { return attackMoveTarget; }
    public boolean canAttackBuildings() { return getAttackBuildingGoal() != null; }
    public Goal getAttackGoal() { return attackGoal; }
    public Goal getAttackBuildingGoal() { return attackBuildingGoal; }
    private RangedAttackBuildingGoal<?> attackBuildingGoal;   // ranged units shoot buildings from range (lovish Oct 20)
    public void setAttackMoveTarget(@Nullable BlockPos bp) { this.attackMoveTarget = bp; }
    public void setFollowTarget(@Nullable LivingEntity target) { this.followTarget = target; }

    private EnemySearchBehaviour attackSearchBehaviour = EnemySearchBehaviour.NONE;
    public EnemySearchBehaviour getEnemySearchBehaviour() { return attackSearchBehaviour; }
    public void setEnemySearchBehaviour(EnemySearchBehaviour behaviour) { attackSearchBehaviour = behaviour; }

    private UnitBowAttackGoal<? extends LivingEntity> attackGoal;
    private RangedAttackGroundGoal<?> attackGroundGoal;
    @Override public RangedAttackGroundGoal<?> getRangedAttackGroundGoal() { return attackGroundGoal; }
    // endregion

    // ---- stats (second-t3-design.md section 3; the cost is TIDE_T3B, the same as every T3: 1840M / 1740E, 20 pop)
    final static public float attackDamage = 15.0f;      // per volley: three heads of 5 (7.5 DPS at one volley / 2 s: just under the Sun Colossus' 7.7, spread over up to three targets)
    final static public float attacksPerSecond = 0.5f;
    final static public float maxHealth = 1200.0f;       // no two T3s share an HP total (lovish, Oct 20): the design's 1100 is taken, 1200 is free (the Leviathan moved off it)
    final static public float armorValue = 2.0f;         // scaled hide
    final static public float movementSpeed = 0.20f;     // on land; x(1 + 0.2 + WET_SPEED_BONUS) in the water
    final static public float attackRange = 14;          // design: each head picks a target within 14
    final static public float aggroRange = 14;
    public static final int SIGHT_RANGE = 20;
    final static public boolean willRetaliate = true;
    final static public boolean aggressiveWhenIdle = true;

    /** Three Heads: how many, and the damage of one head's spit. */
    public static final int HEADS = 3;
    public static final float HEAD_DAMAGE = attackDamage / HEADS;
    /** Regrow: extra Tides speed in water on top of the faction's +20% (x2.0 wet), and the extra regen (3 HP/s wet in all). */
    public static final double WET_SPEED_BONUS = 0.80;
    public static final float WET_REGEN_BONUS = 0.5f;
    /** Siege / artillery hit this much harder (design: 1.5x, like the Leviathan fortress). */
    public static final float SIEGE_MULTIPLIER = 1.5f;
    /** A severed head: a single hit above this much damage silences one head for this long. */
    public static final float SEVER_DAMAGE = 120f;
    public static final int SEVER_TICKS = 5 * 20;
    /** The body: the iron golem grown this many times (placeholder; the entity's box is scaled the same). */
    public static final float SCALE = 2.0f;
    static final String KEY_ANNOUNCED = "bt_t3_announced";

    public int maxResources = 100;
    public int fogRevealDuration = 0; // set > 0 for the client who is attacked by this unit
    public int getFogRevealDuration() { return fogRevealDuration; }
    public void setFogRevealDuration(int duration) { fogRevealDuration = duration; }

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();
    /** Game time each head is silenced until (a severed head); 0 = whole. Not saved: a head regrows in 5 s. */
    private final long[] headDownUntil = new long[HEADS];

    public AbyssalHydraUnit(EntityType<? extends IronGolem> entityType, Level level) {
        super(entityType, level);
        updateAbilityButtons();
    }

    @Override
    public void resetBehaviours() {
        if (this.attackGoal != null)
            this.attackGoal.stop();
        if (this.attackGroundGoal != null)
            this.attackGroundGoal.stop();
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
                .add(AttributeRegistrar.SIGHT_RANGE.get(), SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    @Override
    public float tidesRegenBonus() { return WET_REGEN_BONUS; }

    @Override
    public double tidesSpeedBonus() { return WET_SPEED_BONUS; }

    @Override
    public boolean canBreatheUnderwater() { return true; }

    /** Never drowns or chokes in a wall; artillery and siege hit {@link #SIEGE_MULTIPLIER}x; a huge single hit severs a head. */
    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (LeviathanUnit.isBreathingDamage(source))
            return false;
        if (LeviathanUnit.isSiegeHit(source))
            amount *= SIEGE_MULTIPLIER;
        boolean hurt = super.hurt(source, amount);
        if (hurt && amount > SEVER_DAMAGE && level() instanceof ServerLevel sl)
            severHead(sl);
        return hurt;
    }

    /** Silences one whole head for {@link #SEVER_TICKS}. Returns false when all are already down. Public for the game test. */
    public boolean severHead(ServerLevel sl) {
        long now = sl.getGameTime();
        for (int i = 0; i < HEADS; i++) {
            if (headDownUntil[i] <= now) {
                headDownUntil[i] = now + SEVER_TICKS;
                sl.sendParticles(ParticleTypes.SPLASH, getX(), getY() + getBbHeight() * 0.9, getZ(), 20, 0.5, 0.3, 0.5, 0.2);
                this.playSound(SoundEvents.GUARDIAN_HURT, 2.0F, 0.6F);
                return true;
            }
        }
        return false;
    }

    /** How many heads can fire right now. */
    public int headsUp() {
        long now = level().getGameTime();
        int n = 0;
        for (long t : headDownUntil)
            if (t <= now)
                n++;
        return n;
    }

    /** In the water: surface travel (land friction with a 0.85 drag), so wet speed scales like land speed (the Leviathan's rule). */
    @Override
    public void travel(@NotNull Vec3 travelVector) {
        if (this.isControlledByLocalInstance() && this.isInWater()) {
            this.moveRelative(this.getSpeed() * 0.5f, travelVector);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.85));
            this.calculateEntityAnimation(false);
            return;
        }
        super.travel(travelVector);
    }

    // mass: never pushed, shoves small bodies aside (TitanMass)
    @Override
    public boolean isPushable() { return TitanMass.isPushable(); }

    @Override
    protected void doPush(@NotNull Entity other) { TitanMass.doPush(this, other); }

    @Override
    public void setTarget(@Nullable LivingEntity pTarget) {
        super.setTarget(pTarget);
        if (pTarget != null && getRangedAttackGroundGoal() != null)
            getRangedAttackGroundGoal().stop();
    }

    @Override
    public boolean isLeftHanded() { return false; }
    @Override // prevent vanilla logic for picking up items
    protected void pickUpItem(ItemEntity pItemEntity) { }
    @Override
    protected void customServerAiStep() { }
    @Override
    public LivingEntity getTarget() {
        return this.targetGoal != null ? this.targetGoal.getTarget() : null;
    }

    public void tick() {
        this.setCanPickUpLoot(false);
        super.tick();
        Unit.tick(this);
        AttackerUnit.tick(this);
        if (attackGroundGoal != null)
            attackGroundGoal.tick();
        if (!(level() instanceof ServerLevel) || !isAlive())
            return;
        TitanMass.tick(this);   // two standing titans on one spot step apart
        String owner = getOwnerName();
        if (tickCount > 5 && !getPersistentData().getBoolean(KEY_ANNOUNCED) && owner != null && !owner.isEmpty()) {
            getPersistentData().putBoolean(KEY_ANNOUNCED, true);
            com.solegendary.reignofnether.unit.T3ServerEvents.announceFielded(owner,
                net.minecraft.network.chat.Component.translatable("entity.reignofnether.abyssal_hydra_unit"));
        }
        // the heads drip: a rare drop of brine off the necks
        if (tickCount % 20 == 0 && level() instanceof ServerLevel sl)
            sl.sendParticles(ParticleTypes.DRIPPING_WATER, getX(), getY() + getBbHeight() * 0.95, getZ(), 1, 0.3, 0.1, 0.3, 0.01);
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
        this.targetGoal = new SelectedTargetGoal<>(this, true, false);   // ground and air: the heads hit both
        this.garrisonGoal = new GarrisonGoal(this);
        this.itemGoal = new UnitItemGoal(this);
        this.attackGoal = new UnitBowAttackGoal<>(this);
        this.attackBuildingGoal = new RangedAttackBuildingGoal<>(this, this.attackGoal);
        this.attackGroundGoal = new RangedAttackGroundGoal<>(this, false, this.attackGoal);
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
        this.goalSelector.addGoal(2, attackGroundGoal);
        this.targetSelector.addGoal(2, targetGoal);
        this.goalSelector.addGoal(3, moveGoal);
        this.goalSelector.addGoal(4, new RandomLookAroundUnitGoal(this));
    }

    // it "holds a bow" only because UnitBowAttackGoal needs one to fire; the spit is not an arrow
    @Override
    public void setupEquipmentAndUpgradesServer() {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
    }

    @Override
    public void setupEquipmentAndUpgradesClient() { }

    @Override
    public void performUnitRangedAttack(LivingEntity pTarget, float velocity) {
        if (volley(pTarget) > 0 && !level().isClientSide() && pTarget instanceof Unit unit)
            FogOfWarClientboundPacket.revealRangedUnit(unit.getOwnerName(), this.getId());
    }

    // building targets and Attack Ground: one spit (an arrow flown at the spot), so it can still bring down a wall
    @Override
    public void performUnitRangedAttack(double x, double y, double z, float velocity) {
        if (!(level() instanceof ServerLevel sl))
            return;
        net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(sl, this);
        arrow.setPos(getX(), getEyeY(), getZ());
        arrow.setBaseDamage(HEAD_DAMAGE);
        arrow.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
        double dx = x - getX(), dy = y - arrow.getY(), dz = z - getZ();
        arrow.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.1, dz, 1.6f, 0);
        sl.addFreshEntity(arrow);
        this.playSound(SoundEvents.GUARDIAN_ATTACK, 2.0F, 0.8F);
        getMainHandItem().setDamageValue(0);
    }

    /**
     * Three Heads at {@code primary}: each head that is up picks its own target - the primary first, then the nearest
     * other enemies within {@link #attackRange} (air included), one head each - and spits {@link #HEAD_DAMAGE}. With fewer
     * targets than heads the heads share them (round robin). Returns how many heads fired (0 if the primary is gone or
     * every head is down). Public for the game test.
     */
    public int volley(LivingEntity primary) {
        return volley(primary, e -> true);
    }

    /** As {@link #volley(LivingEntity)}, but only enemies passing {@code allowed} can take a head (the game test keeps a volley inside its own arena). */
    public int volley(LivingEntity primary, java.util.function.Predicate<LivingEntity> allowed) {
        if (!(level() instanceof ServerLevel sl) || primary == null || !primary.isAlive())
            return 0;
        int up = headsUp();
        if (up == 0)
            return 0;
        double r2 = (double) attackRange * attackRange;
        List<LivingEntity> targets = new ArrayList<>();
        targets.add(primary);
        List<LivingEntity> others = HeroFx.enemies(sl, this, position(), attackRange);
        others.remove(primary);
        others.removeIf(e -> !allowed.test(e));
        others.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(this)));
        for (LivingEntity e : others)
            if (targets.size() < up && e.isAlive())
                targets.add(e);
        Vec3 from = new Vec3(getX(), getY() + getBbHeight() * 0.8, getZ());
        // heads that share a target land as ONE hit of their summed damage (the mod lets a unit hit a target once per tick)
        java.util.LinkedHashMap<LivingEntity, Float> dealt = new java.util.LinkedHashMap<>();
        int fired = 0;
        for (int i = 0; i < up; i++) {
            LivingEntity t = targets.get(i % targets.size());
            if (!t.isAlive() || t.distanceToSqr(this) > r2 * 1.5)
                continue;
            Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0);
            HeroFx.trail(sl, ParticleTypes.BUBBLE_POP, from, to);
            dealt.merge(t, HEAD_DAMAGE, Float::sum);
            fired++;
        }
        for (var e : dealt.entrySet()) {
            e.getKey().invulnerableTime = 0;
            e.getKey().hurt(sl.damageSources().indirectMagic(this, this), e.getValue());
        }
        this.playSound(SoundEvents.GUARDIAN_ATTACK, 1.5F, 1.2F);
        getMainHandItem().setDamageValue(0);
        return fired;
    }
}
