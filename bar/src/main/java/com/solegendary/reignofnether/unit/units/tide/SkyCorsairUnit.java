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
import com.solegendary.reignofnether.unit.controls.FlyingUnitMoveControl;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MiscUtil;

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
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Tidewrought T2 bomber - the <b>Sky Corsair</b> (design/tidewrought_plan.md, slice 3): a pirate airship (a Ghast
 * body for its flight plumbing, sized at {@link #SCALE} of a wild one, drawn as a balloon carrying a timber hull by
 * SkyCorsairModel / SkyCorsairRenderer, with a cloud wake astern while under way). It flies over the fight on the Ghast's flight plumbing (FlyingMoveToTargetGoal +
 * FlyingUnitMoveControl, ten blocks above the ground) and, once nearly overhead ({@link #attackRange} is a 3D distance,
 * so with its altitude it has to close to about 8 blocks flat), drops a stick of {@link #BOMBS} bombs in a line along
 * its heading through the target, {@link #BOMB_SPACING} blocks apart.
 * <p>
 * Each bomb is a {@link BombardShell} let go from the hull: the same splash ({@link BombardShell#SPLASH_RADIUS}, half
 * damage at the rim), the same building dent, no terrain damage, and friends - its own side's and allies' units and
 * buildings - are never hurt or stopped by it. It cannot fire at what is right under its own tail: nothing, a bomber
 * releases early. Counterplay is anti-air: it is a flyer to every AA unit (Wisp Choir, Flak Swivel x3) and ground
 * archers reach it a little further the higher it is (the Bee's range bonus), while melee never can.
 */
public class SkyCorsairUnit extends Ghast implements Unit, AttackerUnit, RangedAttackerUnit {
    final static public float attackRange = 13; // 3D: at its 10-block altitude that is ~8 blocks flat

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

    public GarrisonGoal getGarrisonGoal() { return null; }
    public boolean canGarrison() { return getGarrisonGoal() != null; }

    MoveToTargetBlockGoal usePortalGoal;
    public MoveToTargetBlockGoal getUsePortalGoal() { return usePortalGoal; }
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
            SynchedEntityData.defineId(SkyCorsairUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(SkyCorsairUnit.class, EntityDataSerializers.INT);

    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
        SynchedEntityData.defineId(SkyCorsairUnit.class, EntityDataSerializers.STRING);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ownerDataAccessor, "");
        this.entityData.define(scenarioRoleDataAccessor, -1);
        this.entityData.define(onDeathCommandDataAccessor, "");
    }

    @Nullable
    public ResourceCost getCost() {return ResourceCosts.SKY_CORSAIR;}
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

    private UnitBowAttackGoal<? extends LivingEntity> attackGoal;
    private RangedAttackBuildingGoal<?> attackBuildingGoal;

    // endregion

    /** Drawn and sized at this fraction of a wild ghast (4 x 4): a 2 x 2 hull, SkyCorsairRenderer and EntityRegistrar agree. */
    public static final float SCALE = 0.5f;

    final static public float attackDamage = 10.0f;     // per bomb, to every enemy in its splash
    final static public float attacksPerSecond = 0.2f;  // one stick of bombs every 5 s
    final static public float aggroRange = 16;
    final static public boolean willRetaliate = true; // will attack when hurt by an enemy
    final static public boolean aggressiveWhenIdle = true;

    final static public float maxHealth = 70.0f;
    final static public float armorValue = 0.0f;
    final static public float movementSpeed = 0.26f;   // Ghast 0.22: a raider of the skies, not a siege balloon
    public int maxResources = 100;

    /** Bombs per run, and the gap between their aim points along the corsair's heading. */
    public static final int BOMBS = 3;
    public static final double BOMB_SPACING = 2.5;

    public int fogRevealDuration = 0; // set > 0 for the client who is attacked by this unit
    public int getFogRevealDuration() { return fogRevealDuration; }
    public void setFogRevealDuration(int duration) { fogRevealDuration = duration; }

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    /**
     * Vanilla Ghast returns a fixed 2.6 here, written for its 4-block body. A scaled body (the Sky Corsair is 2.0 tall) puts that eye ABOVE
     * the hitbox, so isInWall sampled the blocks over the unit's head: suffocation damage under any low overhang,
     * and the "in a wall" failures of production_buildings_place_every_unit for a corsair that stood legally beside
     * its drydock (CI 38594088866). The Ghast's own ratio, 0.65 of the height, keeps the eye inside the body.
     */
    @Override
    protected float getStandingEyeHeight(net.minecraft.world.entity.Pose pose, net.minecraft.world.entity.EntityDimensions dimensions) {
        return dimensions.height * 0.65f;
    }

    public SkyCorsairUnit(EntityType<? extends Ghast> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new FlyingUnitMoveControl(this);
        updateAbilityButtons();
    }

    @Override
    public float getDamageAfterMagicAbsorb(DamageSource pSource, float pDamage) {
        pDamage = super.getDamageAfterMagicAbsorb(pSource, pDamage);
        if (MiscUtil.isMagicDamage(pSource))
            pDamage *= (1 - getUnitMagicArmorPercentage());
        return pDamage;
    }

    @Override
    public boolean removeWhenFarAway(double d) { return false; }

    // a unit, not a wild ghast: peaceful difficulty never deletes it
    @Override
    protected boolean shouldDespawnInPeaceful() { return false; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.ATTACK_DAMAGE, SkyCorsairUnit.attackDamage)
                .add(Attributes.MOVEMENT_SPEED, SkyCorsairUnit.movementSpeed)
                .add(Attributes.MAX_HEALTH, SkyCorsairUnit.maxHealth)
                .add(Attributes.ARMOR, SkyCorsairUnit.armorValue)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), 24)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    @Override // prevent vanilla logic for picking up items
    protected void pickUpItem(@NotNull ItemEntity pItemEntity) { }

    // a creaking hull and rigging rather than a ghast's wail
    @Override
    protected SoundEvent getAmbientSound() { return null; }
    @Override
    protected SoundEvent getHurtSound(@NotNull DamageSource source) { return SoundEvents.WOOD_BREAK; }
    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR; }

    @Override
    public LivingEntity getTarget() {
        return this.targetGoal == null ? null : this.targetGoal.getTarget();
    }

    public void tick() {
        this.setCanPickUpLoot(false);
        super.tick();
        Unit.tick(this);
        AttackerUnit.tick(this);
        updateRotation();
        if (level().isClientSide())
            clientTrail();
    }

    /**
     * The wake: while under way, a puff of cloud astern every other tick, drifting back along the course under the
     * hull, so the ship visibly steams across the sky and its heading reads from the ground; a wisp of galley smoke
     * from the stern castle now and then even when it hovers. Client side only, a few particles a second per ship.
     */
    private void clientTrail() {
        Vec3 v = getDeltaMovement();
        boolean underWay = v.horizontalDistanceSqr() > 1.0e-4;
        if (underWay && tickCount % 2 == 0) {
            Vec3 astern = Vec3.directionFromRotation(0f, yBodyRot).scale(-1.3);
            level().addParticle(ParticleTypes.CLOUD,
                getX() + astern.x, getY() + 0.3, getZ() + astern.z, -v.x * 0.4, -0.01, -v.z * 0.4);
        }
        if (tickCount % 25 == 0) {
            Vec3 stern = Vec3.directionFromRotation(0f, yBodyRot).scale(-0.5);
            level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                getX() + stern.x, getY() + 0.7, getZ() + stern.z, 0.0, 0.03, 0.0);
        }
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

    /** Faces the target while it has one, else where it is flying (the Ghast's rule: its body has no turn of its own). */
    public void updateRotation() {
        LivingEntity target = this.getTarget();
        Vec3 dMove = this.getDeltaMovement();
        double x = 0, z = 0;
        if (target != null) {
            x = target.getX() - this.getX();
            z = target.getZ() - this.getZ();
        } else if (dMove.horizontalDistanceSqr() > 0) {
            x = dMove.x();
            z = dMove.z();
        }
        if (Math.abs(x) > 0.05f || Math.abs(z) > 0.05f) {
            this.setYRot(-((float) Mth.atan2(x, z)) * 57.295776F);
            this.yBodyRot = this.getYRot();
        }
    }

    public void initialiseGoals() {
        this.usePortalGoal = new FlyingUsePortalGoal(this);
        this.moveGoal = new FlyingMoveToTargetGoal(this, 0);
        this.targetGoal = new SelectedTargetGoal<>(this, true, true);
        this.attackGoal = new UnitBowAttackGoal<>(this);
        this.attackBuildingGoal = new RangedAttackBuildingGoal<>(this, this.attackGoal);
    }

    @Override
    protected void registerGoals() {
        initialiseGoals();
        this.goalSelector.addGoal(2, usePortalGoal);
        this.goalSelector.addGoal(2, attackBuildingGoal);
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
        if (dropBombs(pTarget.getX(), pTarget.getY(), pTarget.getZ()) > 0 && pTarget instanceof Unit unit)
            FogOfWarClientboundPacket.revealRangedUnit(unit.getOwnerName(), this.getId());
    }

    // building targets
    @Override
    public void performUnitRangedAttack(double x, double y, double z, float velocity) {
        dropBombs(x, y, z);
    }

    /**
     * Lets go a stick of {@link #BOMBS} bombs aimed along the corsair's heading through (x, y, z): the middle one on
     * the point, the others {@link #BOMB_SPACING} blocks before and past it. Returns how many it dropped (0 when
     * disarmed or client side). Public for the game test (bombs without waiting on the attack goal).
     */
    public int dropBombs(double x, double y, double z) {
        if (level().isClientSide() || this.hasEffect(MobEffectRegistrar.DISARM.get()))
            return 0;
        double dx = x - getX(), dz = z - getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.5) {   // straight overhead: the line runs along the way it faces
            Vec3 look = getLookAngle();
            dx = look.x;
            dz = look.z;
            len = Math.max(1e-3, Math.sqrt(dx * dx + dz * dz));
        }
        double ux = dx / len, uz = dz / len;
        int dropped = 0;
        for (int i = 0; i < BOMBS; i++) {
            double off = (i - (BOMBS - 1) / 2.0) * BOMB_SPACING;
            BombardShell bomb = new BombardShell(level(), this, getUnitAttackDamage());
            bomb.setPos(getX(), getY() - 0.3, getZ());
            bomb.aimAt(x + ux * off, y, z + uz * off);
            level().addFreshEntity(bomb);
            dropped++;
        }
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() - 0.2, getZ(), 6, 0.4, 0.1, 0.4, 0.01);
            sl.playSound(null, blockPosition(), SoundEvents.DISPENSER_LAUNCH, SoundSource.NEUTRAL, 1.6f, 0.6f);
        }
        getMainHandItem().setDamageValue(0);
        return dropped;
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

    // it "holds a bow" only because UnitBowAttackGoal needs one to fire; the bombs are BombardShells
    @Override
    public void setupEquipmentAndUpgradesServer() {
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
    }
}
