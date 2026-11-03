package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.barfx.LightningFx;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientboundPacket;
import com.solegendary.reignofnether.mana.ManaServerEvents;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.Checkpoint;
import com.solegendary.reignofnether.unit.EnemySearchBehaviour;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.controls.DragonMoveControl;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MiscUtil;

import com.github.alexthe666.alexsmobs.entity.EntitySpectre;
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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Stormcallers T2 super flyer - the <b>Thunder Dragon</b> (design/stormcallers_plan.md, slice 3): Alex's Mobs' spectre
 * body (a single flying entity, no parts - safe for RoN's selection and grid) in a jade-and-gold sky-serpent skin
 * (ThunderDragonRenderer, tools/gen_storm_skins.py paints the spectre's UV outline) on the Sky Corsair's flight rules:
 * the bee's gravity-free flight, FlyingMoveToTargetGoal and DragonMoveControl (a nose, a capped turn, an eased speed),
 * a flyer to every AA unit.
 * <p>
 * Its attack is <b>chain lightning</b> every {@link #attacksPerSecond}-paced strike: {@link #attackDamage} to the target,
 * then the bolt jumps to up to {@link #CHAIN_JUMPS} more enemies within {@link #JUMP_RADIUS} blocks of the last one hit,
 * x{@link #JUMP_FALLOFF} per jump; never a friend, never the same unit twice ({@link #chain}). Each strike is a spell:
 * {@link #ENERGY_PER_STRIKE} energy through {@link ManaServerEvents#channelShot}; a starved dragon strikes at half rate
 * like the Adepts. Magic damage (RoN's indirectMagic: the Ward softens it, the Jade Mirror does not reflect it), drawn
 * with {@link LightningFx#bolt} between every link - no LightningBolt entity, no fire, no terrain. The jump search is one
 * {@link UnitGrid#near} query per link into a shared scratch list: no allocation per strike at 8v8 scale.
 * <p>
 * AM's spectre is stripped: its goals, lure, tempting and feeding are gone, its "immune to everything but magic" rule is
 * overridden (a dragon takes arrows like any flyer), and its through-blocks drift (noPhysics) is switched off so it
 * flies like the Corsair.
 */
public class ThunderDragonUnit extends EntitySpectre implements Unit, AttackerUnit, RangedAttackerUnit {
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
    public ArrayList<Checkpoint> getCheckpoints() { return checkpoints; }

    public GarrisonGoal getGarrisonGoal() { return null; }
    public boolean canGarrison() { return false; }

    MoveToTargetBlockGoal usePortalGoal;
    public MoveToTargetBlockGoal getUsePortalGoal() { return usePortalGoal; }
    public boolean canUsePortal() { return getUsePortalGoal() != null; }

    public Abilities getAbilities() {return abilities;}
    public List<ItemStack> getItems() {return items;}
    public MoveToTargetBlockGoal getMoveGoal() {return moveGoal;}
    public SelectedTargetGoal<? extends LivingEntity> getTargetGoal() {return targetGoal;}
    public ReturnResourcesGoal getReturnResourcesGoal() {return null;}
    public int getMaxResources() {return 0;}

    private MoveToTargetBlockGoal moveGoal;
    private SelectedTargetGoal<? extends LivingEntity> targetGoal;

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
            SynchedEntityData.defineId(ThunderDragonUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(ThunderDragonUnit.class, EntityDataSerializers.INT);

    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
            SynchedEntityData.defineId(ThunderDragonUnit.class, EntityDataSerializers.STRING);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ownerDataAccessor, "");
        this.entityData.define(scenarioRoleDataAccessor, -1);
        this.entityData.define(onDeathCommandDataAccessor, "");
    }

    @Nullable
    public ResourceCost getCost() {return ResourceCosts.THUNDER_DRAGON;}
    public boolean getWillRetaliate() {return willRetaliate;}
    public boolean getAggressiveWhenIdle() {return aggressiveWhenIdle && !isVehicle();}
    public BlockPos getAttackMoveTarget() { return attackMoveTarget; }
    public boolean canAttackBuildings() { return false; }   // lightning finds units; it leaves walls alone
    public Goal getAttackGoal() { return attackGoal; }
    public Goal getAttackBuildingGoal() { return null; }
    public void setAttackMoveTarget(@Nullable BlockPos bp) { this.attackMoveTarget = bp; }
    public void setFollowTarget(@Nullable LivingEntity target) { this.followTarget = target; }

    private EnemySearchBehaviour attackSearchBehaviour = EnemySearchBehaviour.NONE;
    public EnemySearchBehaviour getEnemySearchBehaviour() { return attackSearchBehaviour; }
    public void setEnemySearchBehaviour(EnemySearchBehaviour behaviour) { attackSearchBehaviour = behaviour; }

    public int fogRevealDuration = 0; // set > 0 for the client who is attacked by this unit
    public int getFogRevealDuration() { return fogRevealDuration; }
    public void setFogRevealDuration(int duration) { fogRevealDuration = duration; }

    private UnitRangedAttackGoal<? extends LivingEntity> attackGoal;

    // endregion

    // 14 a strike every 3 s to the first target (4.7 dps) plus up to three fading jumps: a clump eater priced like the
    // Sky Corsair, with 100 HP that any archer or AA can reach
    final static public float attackDamage = 14.0f;
    final static public float attacksPerSecond = 0.333f;
    final static public float attackRange = 10.0f;
    final static public float aggroRange = 12.0f;
    final static public boolean willRetaliate = true;
    final static public boolean aggressiveWhenIdle = true;

    final static public float maxHealth = 100.0f;
    final static public float armorValue = 0.0f;
    final static public float movementSpeed = 0.30f;

    /** Energy per strike (a spell: ManaServerEvents.channelShot), the chain's jumps, reach and falloff. */
    public static final float ENERGY_PER_STRIKE = 10f;
    public static final int CHAIN_JUMPS = 3;
    public static final double JUMP_RADIUS = 5.0;
    public static final float JUMP_FALLOFF = 0.7f;
    static final int ATTACK_WINDUP_TICKS = 8;

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    /** Attack cooldown scale from the last strike's payment: 1 paid, up to 2 starved (ManaServerEvents.shotCooldownScale). */
    private float manaCooldownScale = 1f;

    // the chain's bookkeeping, reused by every dragon (server thread only): no allocation per strike
    private static final List<LivingEntity> scratch = new ArrayList<>();
    private static final LivingEntity[] struck = new LivingEntity[1 + CHAIN_JUMPS];

    public ThunderDragonUnit(EntityType<? extends EntitySpectre> entityType, Level level) {
        super(entityType, level);
        // a nose, a capped turn and an eased speed (DragonFlight), not the ghast's nudges (lovish, Oct 30: "choppy")
        this.moveControl = new DragonMoveControl(this);
        this.navigation = new FlyingPathNavigation(this, level);
        updateAbilityButtons();
    }

    @Override
    public boolean removeWhenFarAway(double d) { return false; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.FLYING_SPEED, movementSpeed)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, armorValue)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), 20)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    /** Starved strikes come at half rate: the scale of the last strike's payment on top of the usual effects. */
    @Override
    public float getAttackCooldownMultiplier() {
        return AttackerUnit.super.getAttackCooldownMultiplier() * manaCooldownScale;
    }

    // AM's spectre is "invulnerable to everything but magic"; a dragon takes arrows and gusts like any other flyer
    // (Entity's own rule: only the invulnerable flag, bypassed by the void and creative players)
    @Override
    public boolean isInvulnerableTo(@NotNull DamageSource source) {
        return this.isRemoved() || (this.isInvulnerable() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.isCreativePlayer());
    }

    @Override
    public float getDamageAfterMagicAbsorb(@NotNull DamageSource pSource, float pDamage) {
        pDamage = super.getDamageAfterMagicAbsorb(pSource, pDamage);
        if (MiscUtil.isMagicDamage(pSource))
            pDamage *= (1 - getUnitMagicArmorPercentage());
        return pDamage;
    }

    @Override
    public LivingEntity getTarget() {
        return this.targetGoal == null ? null : this.targetGoal.getTarget();
    }

    public void tick() {
        this.setCanPickUpLoot(false);
        float yRotBefore = this.getYRot();
        super.tick();
        // the spectre's tick faces its drift and ghosts through blocks: a hovering dragon keeps its heading and flies
        // round hills like the Corsair
        this.noPhysics = false;
        this.lurePos = null;
        Unit.tick(this);
        AttackerUnit.tick(this);
        updateRotation(yRotBefore);
    }

    /** The client's yaw after this tick's lerp toward the server's, before the spectre's tick re-aims it at its drift. */
    private float lerpedYaw = Float.NaN;

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide())
            lerpedYaw = getYRot();
    }

    /**
     * The spectre's tick aims the yaw at the velocity every tick, on both sides (a nose that flicked with each nudge).
     * On the server the body faces along the flight's nose ({@link DragonMoveControl#bodyYaw}: turned at a capped
     * rate) and, hovering, turns toward its target at DragonMoveControl.HOVER_TURN; the client keeps the yaw it lerped
     * from the server's.
     */
    void updateRotation(float yRotBefore) {
        if (level().isClientSide()) {
            this.setYRot(Float.isNaN(lerpedYaw) ? yRotBefore : lerpedYaw);
        } else if (this.moveControl instanceof DragonMoveControl dc) {
            LivingEntity target = this.getTarget();
            double x = target == null ? 0 : target.getX() - this.getX();
            double z = target == null ? 0 : target.getZ() - this.getZ();
            this.setYRot(dc.bodyYaw(yRotBefore, x, z));
        } else {
            this.setYRot(yRotBefore);
        }
        this.yBodyRot = this.getYRot();
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

    // no tempting, feeding or breeding: an RTS unit answers to its owner's orders only
    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean isFood(@NotNull ItemStack stack) { return false; }

    @Override
    protected void pickUpItem(@NotNull net.minecraft.world.entity.item.ItemEntity pItemEntity) { }

    // a dragon of the storm, not a ghost: thunder when it strikes, a phantom's cries when hurt
    @Override
    protected SoundEvent getAmbientSound() { return null; }
    @Override
    protected SoundEvent getHurtSound(@NotNull DamageSource source) { return SoundEvents.PHANTOM_HURT; }
    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.PHANTOM_DEATH; }

    /** The bee's flight (BeeUnit.travel): no gravity, so the dragon hovers where its last order left it. */
    @Override
    public void travel(@NotNull Vec3 pTravelVector) {
        if (this.isControlledByLocalInstance()) {
            if (this.isInWater()) {
                this.moveRelative(0.02F, pTravelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.8));
            } else if (this.isInLava()) {
                this.moveRelative(0.02F, pTravelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
            } else {
                BlockPos ground = this.getBlockPosBelowThatAffectsMyMovement();
                final float f0 = 0.91f;
                float f = f0;
                if (this.onGround())
                    f = this.level().getBlockState(ground).getFriction(this.level(), ground, this) * f0;
                float f1 = 0.16277137F / (f * f * f);
                this.moveRelative(this.onGround() ? 0.1F * f1 : 0.02F, pTravelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                this.setDeltaMovement(this.getDeltaMovement().scale(f));
            }
        }
        this.calculateEntityAnimation(false);
    }

    @Override
    protected @NotNull PathNavigation createNavigation(@NotNull Level pLevel) {
        return new FlyingPathNavigation(this, pLevel);
    }

    public void initialiseGoals() {
        this.usePortalGoal = new FlyingUsePortalGoal(this);
        this.moveGoal = new FlyingMoveToTargetGoal(this, 0);
        this.targetGoal = new SelectedTargetGoal<>(this, true, true);
        this.attackGoal = new UnitRangedAttackGoal<>(this, ATTACK_WINDUP_TICKS);
    }

    @Override
    protected void registerGoals() {
        initialiseGoals();
        this.goalSelector.addGoal(2, usePortalGoal);
        this.goalSelector.addGoal(2, attackGoal);
        this.targetSelector.addGoal(2, targetGoal);
        this.goalSelector.addGoal(3, moveGoal);
    }

    @Override
    public void resetBehaviours() {
        if (this.attackGoal != null)
            this.attackGoal.stop();
    }

    @Override
    public void performUnitRangedAttack(LivingEntity pTarget, float velocity) {
        if (pTarget == null || level().isClientSide() || this.hasEffect(MobEffectRegistrar.DISARM.get()))
            return;
        if (strike(pTarget) > 0 && pTarget instanceof Unit unit)
            FogOfWarClientboundPacket.revealRangedUnit(unit.getOwnerName(), this.getId());
    }

    /**
     * One chain-lightning strike at {@code target}: pays its energy (setting the next strike's pace), then runs the
     * chain. Returns how many units it hit. Public for the game test.
     */
    public int strike(LivingEntity target) {
        if (!(level() instanceof ServerLevel sl))
            return 0;
        // the strike is a spell: what the pool can pay sets the pace of the next one (starved = half rate). The goal
        // re-reads the cooldown from getAttackCooldown, so resetting it now applies the new scale to this very wait
        manaCooldownScale = ManaServerEvents.shotCooldownScale(ManaServerEvents.channelShot(this, getOwnerName(), ENERGY_PER_STRIKE));
        if (attackGoal != null)
            attackGoal.setToMaxAttackCooldown();
        Vec3 mouth = new Vec3(getX(), getY() + getBbHeight() * 0.5, getZ());
        int hits = chain(sl, this, getOwnerName(), target, getUnitAttackDamage(), mouth);
        sl.playSound(null, blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.NEUTRAL, 0.5f, 1.6f);
        return hits;
    }

    /**
     * The chain: {@code damage} to {@code first}, then to up to {@link #CHAIN_JUMPS} more enemies of {@code owner}, each
     * the nearest not-yet-struck one within {@link #JUMP_RADIUS} of the last, at x{@link #JUMP_FALLOFF} per jump; a
     * particle bolt between every link. Friends (own and allied) are never links. Returns how many were hit. Public for
     * the game test.
     */
    public static int chain(ServerLevel sl, LivingEntity self, String owner, LivingEntity first, float damage, Vec3 from) {
        if (first == null || !first.isAlive())
            return 0;
        java.util.Arrays.fill(struck, null);
        LivingEntity current = first;
        float dmg = damage;
        int hits = 0;
        for (int link = 0; link <= CHAIN_JUMPS && current != null; link++) {
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

    /** The nearest enemy unit within {@link #JUMP_RADIUS} of {@code last} that the chain hasn't struck yet, or null. */
    private static LivingEntity nextLink(ServerLevel sl, LivingEntity self, String owner, LivingEntity last, int linksSoFar) {
        LivingEntity best = null;
        double bestD = JUMP_RADIUS * JUMP_RADIUS;
        for (LivingEntity le : UnitGrid.near(sl, last.getX(), last.getZ(), JUMP_RADIUS, scratch)) {
            if (le == self || !le.isAlive() || !(le instanceof Unit u) || le.level() != sl)
                continue;
            boolean already = false;
            for (int i = 0; i <= linksSoFar; i++)
                if (struck[i] == le) { already = true; break; }
            if (already)
                continue;
            String other = u.getOwnerName();
            if (owner.equals(other) || com.solegendary.reignofnether.alliance.AlliancesServerEvents.isAllied(owner, other))
                continue;
            if (self instanceof Unit su && UnitServerEvents.getUnitToEntityRelationship(su, le) == Relationship.FRIENDLY)
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

    // range bonus an attacker gets against this flyer so it can't hover out of reach (the Bee's rule: half the height
    // when nearly under it, a quarter otherwise) - archers can answer it, melee can't, AA does it best
    public double getAttackerRangeBonus(Mob attacker) {
        Vec2 attackerPos = new Vec2((float) attacker.getX(), (float) attacker.getZ());
        Vec2 myPos = new Vec2((float) this.getX(), (float) this.getZ());
        double horizDist = Math.sqrt(attackerPos.distanceToSqr(myPos));
        double vertiDist = Math.max(0, this.getY() - attacker.getY());
        if (horizDist < 4)
            return (int) (vertiDist * 0.5f);
        else
            return (int) (vertiDist * 0.25f);
    }

    @Override
    public void setupEquipmentAndUpgradesClient() { }

    @Override
    public void setupEquipmentAndUpgradesServer() { }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor pLevel, @NotNull DifficultyInstance pDifficulty, @NotNull MobSpawnType pReason, @Nullable SpawnGroupData pSpawnData, @Nullable CompoundTag pDataTag) {
        return pSpawnData;
    }
}
