package com.solegendary.reignofnether.unit.units.storm;

import com.github.alexthe666.alexsmobs.entity.EntityElephant;
import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.LanternDome;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.mana.LanternDomeServerEvents;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.Checkpoint;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
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
 * Stormcallers T2 area shield - the <b>Lantern Fortress</b> (design/stormcallers_plan.md, slice 4; the T2 table): Alex's
 * Mobs' elephant (LanternFortressRenderer: AM's model and animations in our own red-lacquer caparison skin with a
 * lantern howdah, tools/gen_storm_skins.py) walking under the army as a slow, tough body that does not fight. Its
 * worth is <b>Lantern Dome</b> ({@link LanternDome}, {@link LanternDomeServerEvents}): a toggle that keeps a dome of
 * lantern light over it, soaking projectile damage to friends inside, channelled from the energy pool; starved or
 * spent, the dome collapses and this unit is barred from raising another for a few seconds ({@link #blockDome}) -
 * while the toggle stays on, the tick here raises it again once the bar lifts.
 * <p>
 * AM's elephant is stripped the Mudskipper way: registerGoals replaces its brain (taming, riding, the trunk's item
 * grabs, charging, caravans, eating, wandering), it cannot be fed, tamed, ridden or bred, it never carries a chest or
 * a carpet, never sits or rears, never despawns as a trader, and walks on a plain ground navigator (RoN's paths).
 */
public class LanternFortressUnit extends EntityElephant implements Unit {
    public static final Abilities ABILITIES = new Abilities();
    static {
        ABILITIES.add(new LanternDome(), Keybindings.abilitySlot1);
    }

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

    public GarrisonGoal getGarrisonGoal() { return null; }   // too big for any garrison
    public boolean canGarrison() { return false; }

    UsePortalGoal usePortalGoal;
    public UsePortalGoal getUsePortalGoal() { return usePortalGoal; }
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

    // which player owns this unit? this format ensures its synched to client without having to use packets
    public String getOwnerName() { return this.entityData.get(ownerDataAccessor); }
    public void setOwnerName(String name) { this.entityData.set(ownerDataAccessor, name); }
    public static final EntityDataAccessor<String> ownerDataAccessor =
            SynchedEntityData.defineId(LanternFortressUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(LanternFortressUnit.class, EntityDataSerializers.INT);

    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
            SynchedEntityData.defineId(LanternFortressUnit.class, EntityDataSerializers.STRING);

    // the Lantern Dome toggle, synced so the button shows its state (the Brute's shield stance pattern)
    public static final EntityDataAccessor<Boolean> domeWantedAccessor =
            SynchedEntityData.defineId(LanternFortressUnit.class, EntityDataSerializers.BOOLEAN);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ownerDataAccessor, "");
        this.entityData.define(scenarioRoleDataAccessor, -1);
        this.entityData.define(onDeathCommandDataAccessor, "");
        this.entityData.define(domeWantedAccessor, false);
    }

    @Nullable
    public ResourceCost getCost() {return ResourceCosts.LANTERN_FORTRESS;}

    public void setFollowTarget(@Nullable LivingEntity target) { this.followTarget = target; }

    // endregion

    // a slow, heavy walker in the T2 tank bracket (the Earthshaker's 160 HP with more body and no attack), paid for
    // its dome; knockback-proof like the other big bodies
    final static public float maxHealth = 220.0f;
    final static public float armorValue = 2.0f;
    final static public float movementSpeed = 0.20f;
    final static public double rangedResist = 0.2d;

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();
    /** Game tick until which no dome may be raised (after a collapse); server side. */
    private long domeBlockedUntil = Long.MIN_VALUE;

    public LanternFortressUnit(EntityType<? extends EntityElephant> entityType, Level level) {
        super(entityType, level);
        updateAbilityButtons();
    }

    @Override
    public boolean removeWhenFarAway(double d) { return false; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, armorValue)
                .add(Attributes.ATTACK_DAMAGE, 0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), rangedResist)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    // ---- the dome toggle

    public boolean isDomeWanted() { return this.entityData.get(domeWantedAccessor); }

    /** Flips the toggle (both sides set it, the server's value is what syncs); off lowers the dome at once. */
    public void setDomeWanted(boolean on) {
        this.entityData.set(domeWantedAccessor, on);
        if (!on && !level().isClientSide())
            LanternDomeServerEvents.lower(this);
    }

    /** Is this walker barred from raising a dome right now (the collapse delay)? */
    public boolean domeBlocked() {
        return level().getGameTime() < domeBlockedUntil;
    }

    /** Bars a new dome until {@code untilTick} (LanternDomeServerEvents after a collapse). */
    public void blockDome(long untilTick) {
        domeBlockedUntil = untilTick;
    }

    /**
     * The toggle's keeper: with the dome wanted and none standing, tries to raise one every second (quietly: a toggle
     * must not spam the HUD while the pool is dry). Returns the dome raised, or null. Public for the game test.
     */
    @Nullable
    public LanternDomeServerEvents.Dome keepDome(ServerLevel sl) {
        if (!isDomeWanted() || !isAlive() || domeBlocked() || LanternDomeServerEvents.domeOf(this) != null)
            return null;
        return LanternDomeServerEvents.raise(sl, this, getOwnerName());
    }

    @Override
    protected @NotNull PathNavigation createNavigation(@NotNull Level level) {
        return new GroundPathNavigation(this, level);   // RoN's paths, not AM's teleport-free advanced navigator
    }

    @Override // prevent vanilla logic for picking up items
    protected void pickUpItem(@NotNull ItemEntity pItemEntity) { }

    // the trunk never reaches for dropped items (ITargetsDroppedItems)
    @Override
    public boolean canTargetItem(@NotNull ItemStack stack) { return false; }

    @Override
    public LivingEntity getTarget() {
        // goals only exist server side (Mob registers them there)
        return this.targetGoal == null ? null : this.targetGoal.getTarget();
    }

    // no feeding, taming, riding, dyeing or chests: it is a unit, not a mount
    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean isFood(@NotNull ItemStack stack) { return false; }

    @Override
    protected boolean canAddPassenger(@NotNull Entity passenger) { return false; }

    // an elephant of the dynasty, not a pet: nothing it does depends on an owner player
    @Override
    public boolean isTame() { return false; }

    public void tick() {
        this.setCanPickUpLoot(false);
        super.tick();
        // AM's tick rears the elephant up now and then, sits it on a tame command and may chest it: a walking fortress
        // keeps all four feet down and carries lanterns, not cargo
        if (isStanding())
            setStanding(false);
        if (isSitting())
            setOrderedToSit(false);
        if (isChested())
            setChested(false);
        if (isTusked())
            setTusked(false);
        if (isTrader())
            setTrader(false);
        Unit.tick(this);
        if (!(level() instanceof ServerLevel sl) || !isAlive())
            return;
        // staggered by entity id: a column of Fortresses never all ask the pool on the same tick
        if ((tickCount + getId()) % 20 == 0)
            keepDome(sl);
    }

    @Override
    public void die(@NotNull DamageSource source) {
        if (!level().isClientSide())
            LanternDomeServerEvents.lower(this);
        super.die(source);
    }

    @Override
    public void remove(@NotNull RemovalReason pReason) {
        if (this.level() instanceof ServerLevel serverLevel) {
            LanternDomeServerEvents.lower(this);
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
        pCompound.putBoolean("bt_dome_wanted", isDomeWanted());
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag pCompound) {
        super.readAdditionalSaveData(pCompound);
        this.readUnitSaveData(pCompound);
        this.entityData.set(domeWantedAccessor, pCompound.getBoolean("bt_dome_wanted"));
        this.setOrderedToSit(false);   // AM saves a sit command; a unit never sits
        this.setTame(false);
    }

    public void initialiseGoals() {
        this.usePortalGoal = new UsePortalGoal(this);
        this.moveGoal = new MoveToTargetBlockGoal(this, false, 0);
        this.targetGoal = new SelectedTargetGoal<>(this, true, true);
    }

    @Override
    protected void registerGoals() {
        initialiseGoals();
        this.goalSelector.addGoal(2, usePortalGoal);
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.targetSelector.addGoal(2, targetGoal);
        this.goalSelector.addGoal(3, moveGoal);
        this.goalSelector.addGoal(4, new RandomLookAroundUnitGoal(this));
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor pLevel, @NotNull DifficultyInstance pDifficulty, @NotNull MobSpawnType pReason, @Nullable SpawnGroupData pSpawnData, @Nullable CompoundTag pDataTag) {
        return pSpawnData;   // AM's would roll a tusked bull or a trader's elephant
    }

    // never a baby, never bred (the elephant is an AgeableMob)
    @Override
    public @Nullable AgeableMob getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob other) {
        return null;
    }
}
