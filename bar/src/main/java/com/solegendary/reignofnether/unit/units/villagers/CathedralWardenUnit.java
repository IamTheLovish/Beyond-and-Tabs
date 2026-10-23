package com.solegendary.reignofnether.unit.units.villagers;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.AttackGround;
import com.solegendary.reignofnether.ability.abilities.BellToll;
import com.solegendary.reignofnether.ability.abilities.Sanctify;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientboundPacket;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
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
 * Sunforged T3 experimental, the second of the Castle (slot T): the <b>Cathedral Warden</b> (second-t3-design.md,
 * slice T3b-5). A walking bell-tower with a gilded howdah: slow, the least HP of the ground T3s, no melee at all, only
 * a short sunbeam gun. The Sun Colossus is the lane-holding anchor; the Warden is the support behind it and the
 * faction's only T3 anti-air.
 * <ul>
 *   <li><b>Sunbeam Volley</b> (autocast, {@link #attackRange} blocks, hits air): every attack fires {@link #VOLLEY_RAYS}
 *   thin rays of {@link #RAY_DAMAGE} at up to {@link #VOLLEY_TARGETS} enemies in range (its target first, then the nearest
 *   others; with fewer targets the rays are shared out between them). The lowest damage of any T3.</li>
 *   <li><b>Sanctify</b> ({@link Sanctify}, tap, 25 s): friends within {@link Sanctify#RADIUS} heal 25% of their max HP and
 *   are cleansed of wither, burning and slow.</li>
 *   <li><b>Bell Toll</b> ({@link BellToll}, tap, 30 s): a telegraphed 1 s windup, then enemies within 12 are slowed
 *   for 5 s.</li>
 *   <li><b>Mass</b> ({@link TitanMass}): never pushed. Arrival announced to every player.</li>
 * </ul>
 * Countered by (never by a crippled stat): flanking it (no melee, slow), fast raiders, artillery, focus fire - it dies
 * fast alone. It beats air-heavy armies and attrition fights it can sustain. Loses to a focused equal-cost T2 counter
 * army. Placeholder look: the Sun Colossus' gilded golem at {@link #SCALE}x (Lane B draws the belfry, slice T3b-10).
 */
public class CathedralWardenUnit extends IronGolem implements Unit, AttackerUnit, RangedAttackerUnit {
    public static final Abilities ABILITIES = new Abilities();
    static {
        ABILITIES.add(new AttackGround(CathedralWardenUnit.attackRange), Keybindings.abilitySlot1);
        ABILITIES.add(new Sanctify(), Keybindings.abilitySlot2);
        ABILITIES.add(new BellToll(), Keybindings.abilitySlot3);
    }

    /** The production item's name (EntityRegistrar.getEntityType). */
    public static final String ITEM_NAME = "Cathedral Warden";

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
            SynchedEntityData.defineId(CathedralWardenUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(CathedralWardenUnit.class, EntityDataSerializers.INT);

    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
        SynchedEntityData.defineId(CathedralWardenUnit.class, EntityDataSerializers.STRING);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ownerDataAccessor, "");
        this.entityData.define(scenarioRoleDataAccessor, -1);
        this.entityData.define(onDeathCommandDataAccessor, "");
    }

    @Nullable
    public ResourceCost getCost() { return ResourceCosts.SUN_T3B; }
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

    // ---- stats (second-t3-design.md section 3; the cost is SUN_T3B, the same as every T3: 1840M / 1740E, 20 pop)
    final static public float attackDamage = 12.0f;      // per volley: 6 rays of 2, shared out between up to 6 targets (6 DPS: below the Sun Colossus it supports; only the summoning World Tree Walker is lower)
    final static public float attacksPerSecond = 0.5f;   // one volley every 2 s
    final static public float maxHealth = 875.0f;        // no two T3s share an HP total (lovish, Oct 20): 900-1500 in 50s are taken; the design's 900 is one of them
    final static public float armorValue = 0.0f;
    final static public float movementSpeed = 0.17f;     // slow: it walks behind the line (the Colossus walks at 0.25)
    final static public float attackRange = 18;          // design: 18 range
    final static public float aggroRange = 18;
    public static final int SIGHT_RANGE = 22;
    final static public boolean willRetaliate = true;
    final static public boolean aggressiveWhenIdle = true;

    /** Sunbeam Volley: rays per volley, the most enemies one volley picks. */
    public static final int VOLLEY_RAYS = 6;
    public static final int VOLLEY_TARGETS = 6;
    public static final float RAY_DAMAGE = attackDamage / VOLLEY_RAYS;
    /** The body: the Sun Colossus golem grown this many times (placeholder; the entity's box is scaled the same). */
    public static final float SCALE = 1.7f;
    static final String KEY_ANNOUNCED = "bt_t3_announced";

    public int maxResources = 100;
    public int fogRevealDuration = 0; // set > 0 for the client who is attacked by this unit
    public int getFogRevealDuration() { return fogRevealDuration; }
    public void setFogRevealDuration(int duration) { fogRevealDuration = duration; }

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    public CathedralWardenUnit(EntityType<? extends IronGolem> entityType, Level level) {
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
                net.minecraft.network.chat.Component.translatable("entity.reignofnether.cathedral_warden_unit"));
        }
        // the belfry glows: a rare gold mote off the howdah
        if (tickCount % 20 == 0 && level() instanceof ServerLevel sl)
            sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() * 0.95, getZ(), 1, 0.3, 0.1, 0.3, 0.01);
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
        this.targetGoal = new SelectedTargetGoal<>(this, true, false);   // ground and air: the sunbeam hits both
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

    // it "holds a bow" only because UnitBowAttackGoal needs one to fire; the rays are not arrows
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

    // building targets and Attack Ground: one bolt of light (an arrow flown at the spot), so it can still bring down a wall
    @Override
    public void performUnitRangedAttack(double x, double y, double z, float velocity) {
        if (!(level() instanceof ServerLevel sl))
            return;
        net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(sl, this);
        arrow.setPos(getX(), getEyeY(), getZ());
        arrow.setBaseDamage(attackDamage / 3.0);
        arrow.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
        double dx = x - getX(), dy = y - arrow.getY(), dz = z - getZ();
        arrow.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.1, dz, 1.6f, 0);
        sl.addFreshEntity(arrow);
        this.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0F, 1.4F);
        getMainHandItem().setDamageValue(0);
    }

    /**
     * Sunbeam Volley at {@code primary}: the primary target plus the nearest other enemies in range, up to
     * {@link #VOLLEY_TARGETS}, share {@link #VOLLEY_RAYS} rays of {@link #RAY_DAMAGE} (round robin, the primary first).
     * Hits air and ground. Returns how many rays fired (0 if the primary is gone). Public for the game test.
     */
    public int volley(LivingEntity primary) {
        if (!(level() instanceof ServerLevel sl) || primary == null || !primary.isAlive())
            return 0;
        double r2 = (double) attackRange * attackRange;
        List<LivingEntity> targets = new ArrayList<>();
        targets.add(primary);
        List<LivingEntity> others = HeroFx.enemies(sl, this, position(), attackRange);
        others.remove(primary);
        others.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(this)));
        for (LivingEntity e : others)
            if (targets.size() < VOLLEY_TARGETS)
                targets.add(e);
        Vec3 from = new Vec3(getX(), getY() + getBbHeight() * 0.8, getZ());
        int fired = 0;
        for (int i = 0; i < VOLLEY_RAYS; i++) {
            LivingEntity t = targets.get(i % targets.size());
            if (!t.isAlive() || t.distanceToSqr(this) > r2 * 1.5)
                continue;
            Vec3 to = t.position().add(0, t.getBbHeight() * 0.5, 0);
            HeroFx.trail(sl, ParticleTypes.END_ROD, from, to);
            t.invulnerableTime = 0;   // thin rays: several land in one volley
            t.hurt(sl.damageSources().indirectMagic(this, this), RAY_DAMAGE);
            fired++;
        }
        this.playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5F, 1.8F);
        getMainHandItem().setDamageValue(0);
        return fired;
    }
}
