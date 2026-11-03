package com.solegendary.reignofnether.unit.units.verdant;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.Empower;
import com.solegendary.reignofnether.ability.abilities.EntanglingRoots;
import com.solegendary.reignofnether.ability.abilities.SeedOfRenewal;
import com.solegendary.reignofnether.ability.abilities.SproutGuardian;
import com.solegendary.reignofnether.essence.EssenceCarrier;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.Checkpoint;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.SupportUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.support.SupportBrain;
import com.solegendary.reignofnether.unit.support.SupportState;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Verdant Court Tier 1 essence caster (trained at the Grove) - the <b>Thornweaver</b> (placeholder name; design/essence_casters.md, lovish
 * approved draft 2 on Oct 29: "one caster per race"). A druid who spends the essence the fallen leave behind
 * ({@link EssenceCarrier}: it pulls the wisps within 8 blocks in and holds up to 30) on three spells - Entangling Roots
 * (5), Seed of Renewal (10) and Sprout Guardian (20) - and the casters' shared Empower (15). It does not fight: the
 * support brain keeps it behind the line, inside its spells' reach.
 * <p>
 * A thin copy of the Bloom Priestess's unit plumbing on the Court's illager body (Vindicator), not a subclass: the bot
 * and the healer tests find priestesses by class. Placeholder look: the Elder Druid's robe (ThornweaverRenderer).
 */
public class ThornweaverUnit extends Vindicator implements Unit, SupportUnit, EssenceCarrier {
    public static final Abilities ABILITIES = new Abilities();
    static {
        ABILITIES.add(new EntanglingRoots(), Keybindings.abilitySlot1);
        ABILITIES.add(new SeedOfRenewal(), Keybindings.abilitySlot2);
        ABILITIES.add(new SproutGuardian(), Keybindings.abilitySlot3);
        ABILITIES.add(new Empower(), Keybindings.abilitySlot4);
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
    public ArrayList<Checkpoint> getCheckpoints() { return checkpoints; };

    GarrisonGoal garrisonGoal;
    public GarrisonGoal getGarrisonGoal() { return garrisonGoal; }
    public boolean canGarrison() { return getGarrisonGoal() != null; }

    UsePortalGoal usePortalGoal;
    public UsePortalGoal getUsePortalGoal() { return usePortalGoal; }
    public boolean canUsePortal() { return getUsePortalGoal() != null; }

    public Abilities getAbilities() {return abilities;}
    public List<ItemStack> getItems() {return items;};
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
            SynchedEntityData.defineId(ThornweaverUnit.class, EntityDataSerializers.STRING);

    // which scenario role does this unit use?
    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(ThornweaverUnit.class, EntityDataSerializers.INT);

    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
        SynchedEntityData.defineId(ThornweaverUnit.class, EntityDataSerializers.STRING);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ownerDataAccessor, "");
        this.entityData.define(scenarioRoleDataAccessor, -1);
        this.entityData.define(onDeathCommandDataAccessor, "");
    }

    @Nullable
    public ResourceCost getCost() {return ResourceCosts.THORNWEAVER;}

    public void setFollowTarget(@Nullable LivingEntity target) { this.followTarget = target; }

    // the support brain (unit/support/SupportBrain, claude/healer-ai-oct21.md): attack orders hold a standoff behind
    // the fighters instead of following the enemy into melee, Fight takes the rear rank, it steps out of melee
    private final SupportState supportState = new SupportState();
    @Override public SupportState getSupportState() { return supportState; }
    // the standoff behind a fight keeps the front inside the sapling's healing circle
    @Override public double getSupportReach() { return SeedOfRenewal.RADIUS; }

    // endregion

    // priced and built like the Bloom Priestess (45 HP, 175 metal), a little sturdier and dearer: its spells reach
    // 8-10 blocks where her pulse reaches 7, so it stands nearer the front
    final static public float maxHealth = 50.0f;
    final static public float armorValue = 0.0f;
    final static public float movementSpeed = 0.26f;   // keeps up with the T2 line, not with Stag Lancers
    final static public double magicDamageResist = 0.3d;

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    public ThornweaverUnit(EntityType<? extends Vindicator> entityType, Level level) {
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
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), magicDamageResist);
    }

    // a living druid, not an illager: healing heals and raid/bad-omen logic leaves it alone
    @Override
    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.VILLAGER_AMBIENT; }
    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.VILLAGER_DEATH; }
    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.VILLAGER_HURT; }
    @Override
    public boolean isLeftHanded() { return false; }
    @Override // prevent vanilla logic for picking up items
    protected void pickUpItem(ItemEntity pItemEntity) { }

    public void tick() {
        this.setCanPickUpLoot(false);
        super.tick();
        Unit.tick(this);
        SupportBrain.tick(this, this);
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
    }

    @Override
    protected void registerGoals() {
        initialiseGoals();
        this.goalSelector.addGoal(2, usePortalGoal);
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.targetSelector.addGoal(2, targetGoal);
        this.goalSelector.addGoal(2, garrisonGoal);
        this.goalSelector.addGoal(3, moveGoal);
        this.goalSelector.addGoal(4, new RandomLookAroundUnitGoal(this));
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor pLevel, DifficultyInstance pDifficulty, MobSpawnType pReason, @Nullable SpawnGroupData pSpawnData, @Nullable CompoundTag pDataTag) {
        return pSpawnData;   // no vindicator axe, no "Johnny"
    }
}
