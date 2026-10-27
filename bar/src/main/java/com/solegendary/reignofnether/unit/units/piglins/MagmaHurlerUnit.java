package com.solegendary.reignofnether.unit.units.piglins;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.AttackGround;
import com.solegendary.reignofnether.ability.abilities.Eruption;
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
import com.solegendary.reignofnether.unit.interfaces.IndirectFire;
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
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Ironhide Horde T3 experimental, the second of the Fortress (slot T): the <b>Magma Hurler</b> (second-t3-design.md,
 * slice T3b-2). A bulk-bodied brute with a furnace on its back that sits behind the line and lobs molten boulders:
 * fire artillery-lite, the anti-swarm answer the Mammoth (a melee flanker) is not. The Mammoth goes round the front,
 * the Hurler holds the middle and melts what clumps up.
 * <ul>
 *   <li><b>Molten Hurl</b> (autocast, indirect, {@link #attackRange} blocks, blind inside {@link #MIN_RANGE}): one
 *   {@link BombardShell} every {@link #attacksPerSecond}-paced attack on a solved arc (the Mortar Crawler's frame): it
 *   bursts for {@link #attackDamage} over a {@link #HURL_SPLASH_RADIUS}-block splash, sets every enemy it hits alight
 *   for {@link #HURL_BURN_TICKS} ticks and leaves the ground burning for as long. It cannot hit air (a shell on the
 *   ground is the whole attack: fliers are its counter). Attack Ground shells a spot.</li>
 *   <li><b>Slag Skin</b> (passive): a melee attacker takes {@link #SLAG_SKIN_DAMAGE} fire damage a hit. Fire itself
 *   does nothing to it (it is already burning), so a Blaze or a Ghast is wasted on it.</li>
 *   <li><b>Eruption</b> ({@link Eruption}, tap, 45 s): a telegraphed {@link Eruption#TELEGRAPH_TICKS}-tick ring at the
 *   aimed spot, then damage and a knock-up to every enemy in it.</li>
 *   <li><b>Mass</b> ({@link TitanMass}): never pushed, shoves small bodies aside. Arrival announced to every player.</li>
 * </ul>
 * Countered by (never by a crippled stat): the Mammoth-style flank (the min range and the slow shells), raiders and
 * cavalry, anything that flies, and slows (Tide, Storm). It loses to a focused equal-cost T2 counter army.
 * Placeholder look: the Headhunter's piglin body, scaled ({@link #SCALE}); Lane B draws the cauldron later.
 */
public class MagmaHurlerUnit extends PiglinBrute implements Unit, AttackerUnit, RangedAttackerUnit, IndirectFire {
    public static final Abilities ABILITIES = new Abilities();
    static {
        ABILITIES.add(new AttackGround(MagmaHurlerUnit.attackRange), Keybindings.abilitySlot1);
        ABILITIES.add(new Eruption(), Keybindings.abilitySlot2);
    }

    /** The production item's name (EntityRegistrar.getEntityType). */
    public static final String ITEM_NAME = "Magma Hurler";

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
            SynchedEntityData.defineId(MagmaHurlerUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(MagmaHurlerUnit.class, EntityDataSerializers.INT);

    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
        SynchedEntityData.defineId(MagmaHurlerUnit.class, EntityDataSerializers.STRING);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ownerDataAccessor, "");
        this.entityData.define(scenarioRoleDataAccessor, -1);
        this.entityData.define(onDeathCommandDataAccessor, "");
    }

    @Nullable
    public ResourceCost getCost() { return ResourceCosts.HORDE_T3B; }
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

    // ---- stats (second-t3-design.md section 3; the cost is HORDE_T3B, the same as every T3: 1840M / 1740E, 20 pop)
    final static public float attackDamage = 30.0f;      // per shell, to every enemy in the splash (centre; half at the rim)
    final static public float attacksPerSecond = 0.25f;  // one boulder every 4 s: slow shells
    final static public float maxHealth = 1100.0f;       // no two T3s share an HP total (lovish, Oct 20): 900/950/1000/1050/1150/1250/1400 are taken
    final static public float armorValue = 2.0f;
    final static public float movementSpeed = 0.20f;     // sits behind the line: slower than a heavy line (0.22), like the other artillery
    final static public float attackRange = 24;          // design: 24 range
    final static public float aggroRange = 24;
    /** Closer than this a target cannot be shelled (the design's "min range 6"): it backs off to fire again. */
    public static final float MIN_RANGE = 6.0f;
    @Override public float getMinRange() { return MIN_RANGE; }
    public static final int SIGHT_RANGE = 22;
    final static public boolean willRetaliate = true;
    final static public boolean aggressiveWhenIdle = true;

    /** Molten Hurl: the splash round the impact, how long a hit unit burns, and how long the ground burns. */
    public static final double HURL_SPLASH_RADIUS = 3.0;
    public static final int HURL_BURN_TICKS = 80;   // 4 s
    /** Slag Skin: fire damage a melee attacker takes per hit. */
    public static final float SLAG_SKIN_DAMAGE = 2.0f;
    /** The body: the piglin grown this many times (placeholder; the entity's box is scaled the same). */
    public static final float SCALE = 1.8f;
    static final String KEY_ANNOUNCED = "bt_t3_announced";

    public int maxResources = 100;
    public int fogRevealDuration = 0; // set > 0 for the client who is attacked by this unit
    public int getFogRevealDuration() { return fogRevealDuration; }
    public void setFogRevealDuration(int duration) { fogRevealDuration = duration; }

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    public MagmaHurlerUnit(EntityType<? extends PiglinBrute> entityType, Level level) {
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

    // already burning: fire does nothing to it (Slag Skin's "fire ignores it")
    @Override
    public boolean fireImmune() { return true; }

    // mass: never pushed, shoves small bodies aside (TitanMass)
    @Override
    public boolean isPushable() { return TitanMass.isPushable(); }

    @Override
    protected void doPush(@NotNull Entity other) { TitanMass.doPush(this, other); }

    /**
     * Slag Skin: whoever hits it in melee burns for it. Melee only (a bolt or a shell from range is not "touching" the
     * skin) and never a fire-immune attacker. Fire damage on the attacker is the plain on-fire source, so it ignores
     * armour and RoN's mob-attack rewrite.
     */
    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide() && source.getDirectEntity() instanceof LivingEntity attacker
                && source.getDirectEntity() == source.getEntity() && attacker != this && attacker.isAlive()
                && attacker instanceof AttackerUnit au && au.getAttackGoal() instanceof AbstractMeleeAttackUnitGoal
                && !attacker.fireImmune())
            attacker.hurt(damageSources().onFire(), SLAG_SKIN_DAMAGE);
        return hurt;
    }

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
    public boolean isConverting() { return false; }
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
                net.minecraft.network.chat.Component.translatable("entity.reignofnether.magma_hurler_unit"));
        }
        // Slag Skin shows: embers off the furnace on its back (a cheap, rare puff)
        if (tickCount % 12 == 0 && level() instanceof ServerLevel sl)
            sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + getBbHeight() * 0.9, getZ(), 1, 0.3, 0.1, 0.3, 0.01);
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
        this.targetGoal = new SelectedTargetGoal<>(this, true, false);   // ground only: it cannot hit air
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

    // it "holds a bow" only because UnitBowAttackGoal needs one to fire; the rounds are molten BombardShells
    @Override
    public void setupEquipmentAndUpgradesServer() {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
    }

    @Override
    public void setupEquipmentAndUpgradesClient() { }

    @Override
    public void performUnitRangedAttack(LivingEntity pTarget, float velocity) {
        // lead part of the way (the Mortar Crawler's rule): the boulder flies 1-2 s
        net.minecraft.world.phys.Vec3 v = pTarget.getDeltaMovement();
        double lead = BombardShell.flightTicksFor(Math.sqrt(distanceToSqr(pTarget))) * 0.6;
        if (hurl(pTarget.getX() + v.x * lead, pTarget.getY(), pTarget.getZ() + v.z * lead) != null
                && !level().isClientSide() && pTarget instanceof Unit unit)
            FogOfWarClientboundPacket.revealRangedUnit(unit.getOwnerName(), this.getId());
    }

    // Attack Ground and building targets
    @Override
    public void performUnitRangedAttack(double x, double y, double z, float velocity) {
        hurl(x, y, z);
    }

    /**
     * Lobs one molten boulder at (x, y, z) unless it is inside {@link #MIN_RANGE}. Returns the shell (null when it did
     * not fire). Public for the game test.
     */
    @Nullable
    public BombardShell hurl(double x, double y, double z) {
        if (!(level() instanceof ServerLevel sl))
            return null;
        double dx = x - getX(), dz = z - getZ();
        if (dx * dx + dz * dz < MIN_RANGE * MIN_RANGE)
            return null;
        BombardShell shell = new BombardShell(sl, this, getUnitAttackDamage()).molten(HURL_SPLASH_RADIUS, HURL_BURN_TICKS);
        shell.setPos(getX(), getEyeY() + 0.5, getZ());
        shell.aimAt(x, y, z);
        sl.addFreshEntity(shell);
        this.playSound(SoundEvents.BLAZE_SHOOT, 2.0F, 0.6F + getRandom().nextFloat() * 0.2F);
        sl.sendParticles(ParticleTypes.LAVA, getX(), getEyeY() + 0.6, getZ(), 6, 0.3, 0.2, 0.3, 0);
        sl.sendParticles(ParticleTypes.FLAME, getX(), getEyeY() + 0.6, getZ(), 6, 0.3, 0.2, 0.3, 0.03);
        getMainHandItem().setDamageValue(0);
        return shell;
    }
}
