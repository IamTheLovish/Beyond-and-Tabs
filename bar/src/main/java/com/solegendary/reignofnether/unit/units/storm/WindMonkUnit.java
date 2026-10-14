package com.solegendary.reignofnether.unit.units.storm;

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
import com.solegendary.reignofnether.unit.interfaces.WindAntiAir;
import com.solegendary.reignofnether.mana.ManaServerEvents;
import com.solegendary.reignofnether.unit.units.verdant.WispChoirUnit;

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
 * Stormcallers T1 anti-air - the <b>Wind Monk</b> (design/stormcallers_plan.md, slice 1): a saffron-and-jade monk on the
 * pillager body (its own skin, WindMonkRenderer) with empty hands - the fan staff is the wind itself. Each gust does
 * {@link #FLYER_MULT}x its damage to anything in the air and only {@link #GROUND_MULT}x to anything on the ground, and an
 * idle monk picks a flyer over a closer walker (MiscUtil's target priority): the Wisp Choir's anti-air rules, reusing its
 * {@link WispChoirUnit#isFlyer} test, as the Tidewrought Flak Swivel it copies does. It cannot hurt buildings. Marked
 * {@link WindAntiAir}: its gusts will ground the Celestial Dragon (slice 4).
 * <p>
 * Every gust is a spell: {@link #ENERGY_PER_GUST} energy through {@link ManaServerEvents#channelShot}; a starved monk
 * still gusts, at half rate ({@link #getAttackCooldownMultiplier}). The multiplier rides on {@link #getUnitAttackDamage}
 * for the one hurt() call of a gust, so RoN's damage pipeline (ranged armour, crits, weakness) still applies. The gust is
 * hit-scan: a few cloud puffs along the line and a whoosh - four packets - per shot.
 */
public class WindMonkUnit extends Pillager implements Unit, AttackerUnit, RangedAttackerUnit, WindAntiAir {
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
            SynchedEntityData.defineId(WindMonkUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(WindMonkUnit.class, EntityDataSerializers.INT);
    
    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
        SynchedEntityData.defineId(WindMonkUnit.class, EntityDataSerializers.STRING);

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
    public ResourceCost getCost() {return ResourceCosts.WIND_MONK;}

    public void setAttackMoveTarget(@Nullable BlockPos bp) { this.attackMoveTarget = bp; }
    public void setFollowTarget(@Nullable LivingEntity target) { this.followTarget = target; }

    // endregion

    // the Flak Swivel's numbers (3.5 dmg / 0.7 aps / 14 range / 40 HP): 7.35 dps against a flyer, 1.2 against a walker -
    // a little quicker on its feet (no gun to haul), and it pays a point of energy per gust
    final static public float attackDamage = 3.5f;
    final static public float attacksPerSecond = 0.7f;
    final static public float attackRange = 14.0f;
    final static public float aggroRange = 14.0f;
    final static public boolean willRetaliate = true;
    final static public boolean aggressiveWhenIdle = true;

    final static public float maxHealth = 40.0f;
    final static public float armorValue = 0.0f;
    final static public float movementSpeed = 0.27f;

    /** Damage multipliers of a gust against a flying and a grounded target (the Wisp Choir's). */
    public static final float FLYER_MULT = WispChoirUnit.FLYER_MULT, GROUND_MULT = WispChoirUnit.GROUND_MULT;
    /** Energy per gust (a spell: ManaServerEvents.channelShot). */
    public static final float ENERGY_PER_GUST = 1f;
    static final int ATTACK_WINDUP_TICKS = 4;

    public int maxResources = 100;

    public int fogRevealDuration = 0; // set > 0 for the client who is attacked by this unit
    public int getFogRevealDuration() { return fogRevealDuration; }
    public void setFogRevealDuration(int duration) { fogRevealDuration = duration; }

    private UnitRangedAttackGoal<? extends LivingEntity> attackGoal;

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    /** The multiplier of the gust being resolved right now; 1 outside a hurt() call, so the HUD shows base damage. */
    private float burstMult = 1f;
    /** Attack cooldown scale from the last gust's payment: 1 paid, up to 2 starved (ManaServerEvents.shotCooldownScale). */
    private float manaCooldownScale = 1f;

    public WindMonkUnit(EntityType<? extends Pillager> entityType, Level level) {
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
                .add(Attributes.MOVEMENT_SPEED, WindMonkUnit.movementSpeed)
                .add(Attributes.MAX_HEALTH, WindMonkUnit.maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, WindMonkUnit.armorValue)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    /** Starved gusts come at half rate: the scale of the last gust's payment on top of the usual effects. */
    @Override
    public float getAttackCooldownMultiplier() {
        return AttackerUnit.super.getAttackCooldownMultiplier() * manaCooldownScale;
    }

    /** The gust multiplier against this target. Public for the game test. */
    public static float multiplierAgainst(Entity e) {
        return WispChoirUnit.isFlyer(e) ? FLYER_MULT : GROUND_MULT;
    }

    @Override
    public float getUnitAttackDamage() {
        return AttackerUnit.super.getUnitAttackDamage() * burstMult;
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

    // empty hands (the pillager body crosses its arms): the wind is the weapon
    @Override
    public void setupEquipmentAndUpgradesServer() {
        this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    @Override
    public void performUnitRangedAttack(LivingEntity pTarget, float velocity) {
        if (pTarget == null || level().isClientSide() || this.hasEffect(MobEffectRegistrar.DISARM.get()))
            return;
        gust(pTarget);
        // the gust is a spell: paid after it lands, and a short payment slows the next one (the goal re-reads the
        // cooldown from getAttackCooldown, so resetting it now applies the new scale to this very wait)
        manaCooldownScale = ManaServerEvents.shotCooldownScale(ManaServerEvents.channelShot(this, getOwnerName(), ENERGY_PER_GUST));
        if (attackGoal != null)
            attackGoal.setToMaxAttackCooldown();
        if (pTarget instanceof Unit unit)
            FogOfWarClientboundPacket.revealRangedUnit(unit.getOwnerName(), this.getId());
    }

    /** One gust at {@code target}: damage times {@link #multiplierAgainst}, plus its trail. Returns whether it hurt. Public for the game test. */
    public boolean gust(LivingEntity target) {
        if (!(level() instanceof ServerLevel sl))
            return false;
        boolean hurt;
        burstMult = multiplierAgainst(target);
        try {
            // a projectile hit with the monk as both projectile and owner: the mixin then takes the damage from
            // getUnitAttackDamage (multiplied) and applies ranged armour
            hurt = target.hurt(damageSources().mobProjectile(this, this), getUnitAttackDamage());
        } finally {
            burstMult = 1f;
        }
        Vec3 from = new Vec3(getX(), getEyeY() - 0.2, getZ());
        Vec3 to = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ());
        for (int i = 1; i <= 2; i++) {
            Vec3 p = from.lerp(to, i / 3.0);
            sl.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 2, 0.15, 0.15, 0.15, 0.02);
        }
        sl.sendParticles(ParticleTypes.SWEEP_ATTACK, to.x, to.y, to.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.CLOUD, to.x, to.y, to.z, 4, 0.3, 0.3, 0.3, 0.05);
        sl.playSound(null, blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.NEUTRAL, 1.0f, 1.4f);
        return hurt;
    }

    @Override
    public List<FormattedCharSequence> getAttackDamageStatTooltip() {
        return List.of(
                fcs(I18n.get("unitstats.reignofnether.attack_damage"), true),
                fcs(I18n.get("unitstats.reignofnether.attack_damage_vs_flyers", (int) (FLYER_MULT * 100) + "%",
                        (int) (GROUND_MULT * 100) + "%"))
        );
    }

    // a monk of the dynasty, not an illager: healing heals and raid/bad-omen logic leaves it alone
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
