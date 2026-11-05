package com.solegendary.reignofnether.unit.units.monsters;

import com.solegendary.reignofnether.ability.Abilities;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.abilities.EntanglingRoots;
import com.solegendary.reignofnether.ability.abilities.GraveHook;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.barfx.BarFx;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientboundPacket;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.registrars.SoundRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.sounds.SoundClientboundPacket;
import com.solegendary.reignofnether.time.NightUtils;
import com.solegendary.reignofnether.unit.Checkpoint;
import com.solegendary.reignofnether.unit.EnemySearchBehaviour;
import com.solegendary.reignofnether.unit.UnitDamage;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Gravebound T1 caster - the <b>Tithe Binder</b> (design sheet .scratch/unit1/design-sheet.md, designer pass 1, Nov 5
 * 2026): a hooded dead knight whose weapons are chained to his ribs. Trained at the Graveyard. Every foe he fells pays its
 * weapon as a tithe: he starts with his own Grave Hook and binds one more weapon per kill, up to ten (the rules live in
 * {@link TitheLedger}; the credits come from {@link TitheBinderServerEvents}).
 * <ul>
 *   <li><b>Movement:</b> a ground-pathing Monster (the Wraith's precedent): the generated body (TitheBinderModel) hangs
 *       5 px above the ground in its own rest pose, so it reads as hovering while it paths, collides and can be meleed
 *       like any ground unit. A true flyer (FlyingMoveToTargetGoal) was not simpler and would make it melee-proof.</li>
 *   <li><b>Volley:</b> every 60 ticks (a 46-tick cooldown + the 14-tick wind-up of {@link UnitRangedAttackGoal}) it
 *       slings every docked weapon. Weapons are NOT entities: {@link #volley} schedules one damage per weapon at its own
 *       hit tick (TitheLedger.hitDelay: released every 2 ticks, flight 6 + 0.4 d); the hook hits for 6, each bound
 *       weapon for 3, scaled by the Binder's own damage multipliers (veterancy, weakness, strength). A weapon whose
 *       target died first flies on to the nearest enemy within 4 blocks; buildings take half.</li>
 *   <li><b>Grave Hook</b> ({@link GraveHook}): range 12, 25 s; a light target is dragged 4 blocks toward him over 16
 *       ticks and stunned 1.5 s more; a heavy one (max health >= 150 or a hitbox >= 1.4 wide) is rooted 1.5 s and the
 *       Binder is pulled 1 block toward it.</li>
 * </ul>
 * The renderer reads two synced values: the weapon count ({@link #getWeapons}, 1-10, which weapon_N bones show) and the
 * half-bind flag ({@link #hasHalfBind}, the ghost in the next slot).
 */
public class TitheBinderUnit extends Monster implements Unit, AttackerUnit, RangedAttackerUnit {
    public static final Abilities ABILITIES = new Abilities();
    static {
        ABILITIES.add(new GraveHook(), Keybindings.abilitySlot1);
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
    private UnitRangedAttackGoal<? extends LivingEntity> attackGoal;
    private RangedAttackBuildingGoal<?> attackBuildingGoal;
    // the building goal paces its shots on a bow goal's cooldown; this one is never added to the goal selector, only
    // its cooldown is ticked (tick below): the Binder holds no bow
    private UnitBowAttackGoal<? extends LivingEntity> buildingClock;

    public LivingEntity getFollowTarget() { return followTarget; }
    public boolean getHoldPosition() { return holdPosition; }
    public void setHoldPosition(boolean holdPosition) { this.holdPosition = holdPosition; }

    private LivingEntity followTarget = null;
    private boolean holdPosition = false;
    private BlockPos attackMoveTarget = null;

    public String getOwnerName() { return this.entityData.get(ownerDataAccessor); }
    public void setOwnerName(String name) { this.entityData.set(ownerDataAccessor, name); }
    public static final EntityDataAccessor<String> ownerDataAccessor =
            SynchedEntityData.defineId(TitheBinderUnit.class, EntityDataSerializers.STRING);

    public int getScenarioRoleIndex() { return this.entityData.get(scenarioRoleDataAccessor); }
    public void setScenarioRoleIndex(int index) { this.entityData.set(scenarioRoleDataAccessor, index); }
    public static final EntityDataAccessor<Integer> scenarioRoleDataAccessor =
            SynchedEntityData.defineId(TitheBinderUnit.class, EntityDataSerializers.INT);

    public String getOnDeathCommand() { return this.entityData.get(onDeathCommandDataAccessor); }
    public void setOnDeathCommand(String command) { this.entityData.set(onDeathCommandDataAccessor, command); }
    public static final EntityDataAccessor<String> onDeathCommandDataAccessor =
        SynchedEntityData.defineId(TitheBinderUnit.class, EntityDataSerializers.STRING);

    /** How many weapons are bound (1-10): the renderer shows weapon_1..weapon_N. */
    public static final EntityDataAccessor<Integer> weaponsDataAccessor =
        SynchedEntityData.defineId(TitheBinderUnit.class, EntityDataSerializers.INT);
    /** Half a bind waits for its other half: the renderer's ghost in the next slot. */
    public static final EntityDataAccessor<Boolean> halfBindDataAccessor =
        SynchedEntityData.defineId(TitheBinderUnit.class, EntityDataSerializers.BOOLEAN);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(ownerDataAccessor, "");
        this.entityData.define(scenarioRoleDataAccessor, -1);
        this.entityData.define(onDeathCommandDataAccessor, "");
        this.entityData.define(weaponsDataAccessor, TitheLedger.START_WEAPONS);
        this.entityData.define(halfBindDataAccessor, false);
    }

    @Nullable
    public ResourceCost getCost() { return ResourceCosts.TITHE_BINDER; }
    public boolean getWillRetaliate() { return willRetaliate; }
    public boolean getAggressiveWhenIdle() { return aggressiveWhenIdle && !isVehicle(); }
    public BlockPos getAttackMoveTarget() { return attackMoveTarget; }
    public boolean canAttackBuildings() { return getAttackBuildingGoal() != null; }
    public Goal getAttackGoal() { return attackGoal; }
    public Goal getAttackBuildingGoal() { return attackBuildingGoal; }
    public void setAttackMoveTarget(@Nullable BlockPos bp) { this.attackMoveTarget = bp; }
    public void setFollowTarget(@Nullable LivingEntity target) { this.followTarget = target; }

    private EnemySearchBehaviour attackSearchBehaviour = EnemySearchBehaviour.NONE;
    public EnemySearchBehaviour getEnemySearchBehaviour() { return attackSearchBehaviour; }
    public void setEnemySearchBehaviour(EnemySearchBehaviour behaviour) { attackSearchBehaviour = behaviour; }

    public int fogRevealDuration = 0;
    public int getFogRevealDuration() { return fogRevealDuration; }
    public void setFogRevealDuration(int duration) { fogRevealDuration = duration; }

    // endregion

    // design sheet 7.1: the hook's 6 is the unit's attack damage (each bound weapon adds 3, TitheLedger); one volley per
    // 60 ticks = a 46-tick cooldown (20 / 0.4347 = 46.0) + UnitRangedAttackGoal's 14-tick wind-up
    final static public float attackDamage = 6.0f;
    final static public float attacksPerSecond = 0.4347f;
    final static public float attackRange = 10;   // archers (12) out-range it: the clean counter
    final static public float aggroRange = 10;
    final static public boolean willRetaliate = true;
    final static public boolean aggressiveWhenIdle = true;
    final static public float maxHealth = 35.0f;   // Wraith-level: a fragile caster, the snowball must be killable
    final static public float armorValue = 0.0f;
    final static public float movementSpeed = 0.25f;   // slower than archers (0.26) and Wraiths (0.28)

    /** Save keys: the count and the half bind (pending hits and a hook in flight are not saved). */
    static final String KEY_WEAPONS = "bt_tithe_weapons";
    static final String KEY_HALF = "bt_tithe_half";

    public int maxResources = 100;

    private Abilities abilities = ABILITIES.clone();
    private final List<ItemStack> items = new ArrayList<>();

    private final TitheLedger ledger = new TitheLedger();
    /** A weapon in flight: its damage lands at {@code due} (game time) on the volley's current target. */
    private static final class Hit {
        final long due;
        final float damage;
        final Volley volley;
        Hit(long due, float damage, Volley volley) {
            this.due = due;
            this.damage = damage;
            this.volley = volley;
        }
    }
    /** One volley's target: a unit (id and where it was last seen) or a building. */
    private static final class Volley {
        int targetId;
        double x, y, z;
        final BuildingPlacement building;
        Volley(int targetId, double x, double y, double z, @Nullable BuildingPlacement building) {
            this.targetId = targetId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.building = building;
        }
    }
    private final List<Hit> hits = new ArrayList<>();
    private final List<LivingEntity> scratch = new ArrayList<>();

    // Grave Hook in flight (server): the target, the clip tick, and from the wrap on who moves, which way and how far
    private int hookTargetId = -1;
    private int hookAge = -1;
    private LivingEntity hookMover = null;
    private double hookDirX, hookDirZ, hookDistance;
    // the last hook's drag: blocks asked of move() and blocks it really moved (the test prints both when a drag falls short)
    private double hookAsked, hookMoved;
    private int hookFallbacks;
    private String hookTrailNote;

    /** "asked A, moved M, ..." for the last Grave Hook drag or pull (for the game test's failure message). */
    public String lastHookTrail() {
        return String.format(java.util.Locale.ROOT, "asked %.2f, moved %.2f, %d steps placed directly; %s",
            hookAsked, hookMoved, hookFallbacks, hookTrailNote);
    }

    /**
     * TEST-SCOPED CLOCK INJECTION. Null in the game: the ledger runs on the level's game time. A GameTest may set a
     * clock here to drive the ledger's own timers (the bind spacing and the restless decay) without waiting 95 real
     * seconds; it changes no rule and no number, only which clock this one unit's ledger reads. Never saved, never set
     * by game code; the test clears it in its cleanup. Damage and Grave Hook timing always run on game time.
     */
    @Nullable
    private LongSupplier testClock = null;

    public TitheBinderUnit(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        updateAbilityButtons();
    }

    /** See {@link #testClock}: GameTests only. */
    public void setClockForTest(@Nullable LongSupplier clock) {
        this.testClock = clock;
    }

    /** The ledger's clock: game time, or a GameTest's injected clock. */
    long now() {
        return testClock != null ? testClock.getAsLong() : level().getGameTime();
    }

    // ------------------------------------------------------------------------------------------- the count

    /** Bound weapons, 1-10 (synced: valid on both sides). */
    public int getWeapons() {
        return this.entityData.get(weaponsDataAccessor);
    }

    /** Half a bind is waiting (synced). */
    public boolean hasHalfBind() {
        return this.entityData.get(halfBindDataAccessor);
    }

    /** Sets the count (a saved unit, a test's setup), through the ledger's own clamp. Server side. */
    public void setWeapons(int weapons) {
        ledger.restore(weapons, false, now());
        syncLedger();
    }

    /** {@code halves} halves of a bind from a death (TitheBinderServerEvents). Server side. */
    public void credit(int halves) {
        ledger.credit(halves, now());
        syncLedger();
    }

    private void syncLedger() {
        if (this.entityData.get(weaponsDataAccessor) != ledger.weapons())
            this.entityData.set(weaponsDataAccessor, ledger.weapons());
        if (this.entityData.get(halfBindDataAccessor) != ledger.hasHalf())
            this.entityData.set(halfBindDataAccessor, ledger.hasHalf());
    }

    @Override
    public void kill() {
        if (!level().isClientSide()) {
            SoundClientboundPacket.stopSoundWithId(getId());
        }
        super.kill();
    }

    @Override
    protected boolean onSoulSpeedBlock() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double d) { return false; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.ATTACK_DAMAGE, TitheBinderUnit.attackDamage)
                .add(Attributes.MOVEMENT_SPEED, TitheBinderUnit.movementSpeed)
                .add(Attributes.MAX_HEALTH, TitheBinderUnit.maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, TitheBinderUnit.armorValue)
                .add(Attributes.ATTACK_KNOCKBACK, 0f)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    @Override
    public boolean isLeftHanded() { return false; }
    @Override // prevent vanilla logic for picking up items
    protected void pickUpItem(ItemEntity pItemEntity) { }
    @Override
    protected void customServerAiStep() { }
    @Override
    public LivingEntity getTarget() {
        return this.targetGoal == null ? null : this.targetGoal.getTarget();
    }

    @Override
    public void resetBehaviours() {
        if (this.attackGoal != null)
            this.attackGoal.stop();
    }

    public void tick() {
        this.setCanPickUpLoot(false);
        super.tick();
        Unit.tick(this);
        AttackerUnit.tick(this);

        if (level() instanceof ServerLevel sl && isAlive()) {
            if (buildingClock != null)
                buildingClock.tickAttackCooldown();
            tickHits(sl);
            tickHook(sl);
            int event = ledger.tick(now());
            if (event > 0)
                sl.sendParticles(ParticleTypes.SOUL, getX(), getY() + getBbHeight() * 0.6, getZ(), 12, 0.4, 0.4, 0.4, 0.03);
            else if (event < 0)
                sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + getBbHeight() * 0.7, getZ(), 8, 0.5, 0.3, 0.5, 0.01);
            syncLedger();
        }
    }

    // ------------------------------------------------------------------------------------------- the volley

    @Override
    public void performUnitRangedAttack(LivingEntity target, float velocity) {
        if (target == null || !(level() instanceof ServerLevel sl) || this.hasEffect(MobEffectRegistrar.DISARM.get()))
            return;
        volley(sl, new Volley(target.getId(), target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), null),
            Math.sqrt(distanceToSqr(target)));
        if (target instanceof Unit unit)
            FogOfWarClientboundPacket.revealRangedUnit(unit.getOwnerName(), this.getId());
    }

    /** A building's block (RangedAttackBuildingGoal aims at the top of it): the same volley, at half damage. */
    @Override
    public void performUnitRangedAttack(double x, double y, double z, float velocity) {
        if (!(level() instanceof ServerLevel sl) || this.hasEffect(MobEffectRegistrar.DISARM.get()))
            return;
        BuildingPlacement building = BuildingUtils.findBuilding(false, BlockPos.containing(x, y - 0.5, z));
        if (building == null || !building.isAttackable())
            return;
        volley(sl, new Volley(-1, x, y, z, building), Math.sqrt(distanceToSqr(x, y, z)));
    }

    /**
     * Throws every docked weapon (TitheLedger.volleySize) at {@code v}: weapon k's damage is scheduled at its own hit
     * tick (2 k + flight), counted from now - the clip's t14, when the wind-up ends and the first weapon leaves.
     */
    private void volley(ServerLevel sl, Volley v, double distance) {
        long t = sl.getGameTime();
        int n = TitheLedger.volleySize(ledger.weapons());
        for (int k = 0; k < n; k++)
            hits.add(new Hit(t + TitheLedger.hitDelay(k, distance), TitheLedger.weaponDamage(k), v));
    }

    /** The Binder's own damage multipliers (veterancy, weakness, strength) on a weapon's base damage. */
    private float damageScale() {
        return getUnitAttackDamage() / attackDamage;
    }

    private void tickHits(ServerLevel sl) {
        if (hits.isEmpty())
            return;
        long t = sl.getGameTime();
        for (Iterator<Hit> it = hits.iterator(); it.hasNext(); ) {
            Hit h = it.next();
            if (h.due > t)
                continue;
            it.remove();
            land(sl, h);
            if (!isAlive())
                return;
        }
    }

    private void land(ServerLevel sl, Hit h) {
        float damage = h.damage * damageScale();
        Volley v = h.volley;
        Vec3 from = new Vec3(getX(), getY() + getBbHeight() * 0.65, getZ());
        if (v.building != null) {
            if (v.building.getBlocksPlaced() > 0 && v.building.isAttackable()) {
                v.building.destroyRandomBlocks(TitheLedger.buildingDamage(damage) * v.building.getMeleeDamageMult()
                    * getBuildingDamageMultiplier(), this);
                BarFx.shot(sl, from, new Vec3(v.x, v.y, v.z), BarFx.K_MAGIC, this);
            }
            return;
        }
        LivingEntity target = sl.getEntity(v.targetId) instanceof LivingEntity le && le.isAlive() ? le : null;
        if (target == null) {
            target = retarget(sl, v);
            if (target == null) {   // nobody near: the weapon bites the ground where its target fell
                sl.sendParticles(ParticleTypes.SOUL, v.x, v.y, v.z, 3, 0.2, 0.1, 0.2, 0.01);
                return;
            }
            v.targetId = target.getId();
        }
        v.x = target.getX();
        v.y = target.getY() + target.getBbHeight() * 0.6;
        v.z = target.getZ();
        Vec3 to = new Vec3(v.x, v.y, v.z);
        BarFx.shot(sl, from, to, BarFx.K_MAGIC, this);
        // a projectile from the Binder: RoN's damage mixin keeps this amount (UnitDamage.hurtExplicit) and applies the
        // target's armour and ranged resist; LivingDeathEvent sees the Binder as the killer
        boolean hurt = UnitDamage.hurtExplicit(target, damageSources().mobProjectile(this, this), damage);
        if (hurt) {
            ledger.onDamageDealt(now());
            if (target.isAlive())
                TitheBinderServerEvents.recordHit(this, target, sl.getGameTime());
        }
    }

    /** The nearest living enemy unit within TitheLedger.RETARGET_RADIUS of where the volley's target was, or null. */
    @Nullable
    private LivingEntity retarget(ServerLevel sl, Volley v) {
        double r = TitheLedger.RETARGET_RADIUS;
        LivingEntity best = null;
        double bestD = r * r;
        try {
            for (LivingEntity le : UnitGrid.near(sl, v.x, v.z, r, scratch)) {
                if (le == this || !le.isAlive() || !isEnemy(le))
                    continue;
                double dx = le.getX() - v.x, dz = le.getZ() - v.z;
                double d = dx * dx + dz * dz;
                if (d <= bestD) {
                    bestD = d;
                    best = le;
                }
            }
        } finally {
            scratch.clear();
        }
        return best;
    }

    /** A unit of another owner who is not an ally. */
    boolean isEnemy(LivingEntity le) {
        if (!(le instanceof Unit u))
            return false;
        String mine = getOwnerName(), theirs = u.getOwnerName();
        if (mine == null || mine.isEmpty() || theirs == null)
            return false;
        return !AlliancesServerEvents.isAlliedOrOwned(mine, theirs);
    }

    // ------------------------------------------------------------------------------------------- Grave Hook

    /** True while a Grave Hook is out (from the cast to the end of the pull). */
    public boolean isHooking() {
        return hookAge >= 0;
    }

    /**
     * Why Grave Hook cannot take {@code target} now (a HUD message key, "" for a silent refusal), or null when it can:
     * a living enemy unit within reach, no hook already out, and an outcome from the ledger.
     */
    @Nullable
    public String graveHookRefusal(@Nullable LivingEntity target) {
        if (target == null || !target.isAlive() || target == this || !isEnemy(target))
            return GraveHook.MSG_BAD_TARGET;
        double reach = TitheLedger.HOOK_RANGE + target.getBbWidth() / 2 + 0.5;
        if (distanceToSqr(target) > reach * reach)
            return GraveHook.MSG_OUT_OF_RANGE;
        if (isHooking())
            return "";
        if (TitheLedger.hookOutcome(target.getMaxHealth(), target.getBbWidth()) == null)
            return "";
        return null;
    }

    /** Throws the hook at {@code target} (5.7's clip starts now). Returns the refusal, or null when thrown. */
    @Nullable
    public String startGraveHook(LivingEntity target) {
        String why = graveHookRefusal(target);
        if (why != null)
            return why;
        hookTargetId = target.getId();
        hookAge = 0;
        hookMover = null;
        hookAsked = 0;
        hookMoved = 0;
        hookFallbacks = 0;
        hookTrailNote = null;
        return null;
    }

    private void endHook() {
        hookTargetId = -1;
        hookAge = -1;
        hookMover = null;
    }

    private void tickHook(ServerLevel sl) {
        if (hookAge < 0)
            return;
        hookAge++;
        if (!(sl.getEntity(hookTargetId) instanceof LivingEntity target) || !target.isAlive()
                || this.hasEffect(MobEffectRegistrar.STUN.get()) && hookAge < TitheLedger.HOOK_WRAP_TICK) {
            endHook();   // the target died or vanished, or the Binder was stunned before the hook landed
            return;
        }
        if (hookAge == TitheLedger.HOOK_WRAP_TICK) {
            TitheLedger.Hook outcome = TitheLedger.hookOutcome(target.getMaxHealth(), target.getBbWidth());
            double dx = getX() - target.getX(), dz = getZ() - target.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (outcome == null || dist < 1e-4) {
                endHook();
                return;
            }
            Vec3 from = new Vec3(getX(), getY() + getBbHeight() * 0.65, getZ());
            BarFx.shot(sl, from, new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ()), BarFx.K_MAGIC, this);
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, target.getX(), target.getY() + target.getBbHeight() * 0.5,
                target.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
            if (outcome == TitheLedger.Hook.ROOT) {
                // a heavy: rooted where it stands (it still fights), and the chain pulls the Binder to it instead
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, TitheLedger.ROOT_TICKS,
                    EntanglingRoots.ROOT_AMPLIFIER, false, true, true));
                hookMover = this;
                hookDirX = -dx / dist;
                hookDirZ = -dz / dist;
                hookDistance = Math.max(0, Math.min(TitheLedger.BINDER_PULL_BLOCKS, dist - TitheLedger.DRAG_MIN_GAP));
            } else {
                // stunned through the drag and 1.5 s after it
                target.addEffect(new MobEffectInstance(MobEffectRegistrar.STUN.get(),
                    TitheLedger.DRAG_TICKS + TitheLedger.STUN_TICKS, 0, false, true, true));
                hookMover = target;
                hookDirX = dx / dist;
                hookDirZ = dz / dist;
                hookDistance = TitheLedger.dragDistance(dist);
            }
            return;
        }
        int i = hookAge - TitheLedger.HOOK_WRAP_TICK;
        if (i >= 1 && i <= TitheLedger.DRAG_TICKS && hookMover != null && hookMover.isAlive()) {
            double step = (TitheLedger.dragProgress(i) - TitheLedger.dragProgress(i - 1)) * hookDistance;
            if (hookMover instanceof Mob mob) {
                // a stopped mob keeps walking to its old wanted position unless the move control is cleared too (#416)
                mob.getNavigation().stop();
                mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0);
            }
            // as LeafDash / SunriseSortie: a small downward y keeps onGround true so the move can step up a lip (a flat
            // y = 0 move clears onGround and the next step stops at the first lip); walls still stop it
            double x0 = hookMover.getX(), z0 = hookMover.getZ();
            double dx = hookDirX * step, dz = hookDirZ * step;
            boolean onGround0 = hookMover.onGround();
            hookMover.move(MoverType.SELF, new Vec3(dx, onGround0 ? -0.08 : -0.4, dz));
            double moved = Math.sqrt((hookMover.getX() - x0) * (hookMover.getX() - x0) + (hookMover.getZ() - z0) * (hookMover.getZ() - z0));
            if (hookTrailNote == null)   // the first step's state, so a short drag names its cause in one run
                hookTrailNote = String.format(java.util.Locale.ROOT, "step1 asked %.3f moved %.3f onGround %b hcol %b feet %s",
                    step, moved, onGround0, hookMover.horizontalCollision,
                    sl.getBlockState(hookMover.blockPosition()).getBlock().getDescriptionId());
            if (moved < step * 0.5 && sl.noCollision(hookMover, hookMover.getBoundingBox().move(x0 + dx - hookMover.getX(), 0, z0 + dz - hookMover.getZ()))) {
                // as LeafDash: when move() falls short in open space, place the mover on the step directly (a wall still
                // stops it: noCollision fails there)
                hookMover.setPos(x0 + dx, hookMover.getY(), z0 + dz);
                moved = step;
                hookFallbacks++;
            }
            hookMover.setDeltaMovement(0, Math.min(0, hookMover.getDeltaMovement().y), 0);
            hookAsked += step;
            hookMoved += moved;
            if (hookMover == target && i % 2 == 0)
                sl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 0.1, target.getZ(), 2, 0.2, 0.05, 0.2, 0.0);
        }
        if (i >= TitheLedger.DRAG_TICKS)
            endHook();
    }

    // ------------------------------------------------------------------------------------------- the rest

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
        pCompound.putInt(KEY_WEAPONS, ledger.weapons());
        pCompound.putBoolean(KEY_HALF, ledger.hasHalf());
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag pCompound) {
        super.readAdditionalSaveData(pCompound);
        this.readUnitSaveData(pCompound);
        if (pCompound.contains(KEY_WEAPONS)) {
            ledger.restore(pCompound.getInt(KEY_WEAPONS), pCompound.getBoolean(KEY_HALF), now());
            syncLedger();
        }
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEAD;
    }

    @Override
    protected boolean isSunBurnTick() {
        return NightUtils.isSunBurnTick(this);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (isSunBurnTick())
            this.setSecondsOnFire(8);
    }

    @Override
    public SunlightEffect getSunlightEffect() {
        if (hasItemInSlot(EquipmentSlot.HEAD) && getItemBySlot(EquipmentSlot.HEAD).getItem() != Items.CARVED_PUMPKIN) {
            return SunlightEffect.SLOWNESS_II;
        } else {
            return SunlightEffect.FIRE;
        }
    }
    // placeholder voice: the Wraith's (a hooded dead caster too) until the Binder has its own sounds
    @Override protected SoundEvent getAmbientSound() {
        return SoundRegistrar.WRAITH_AMBIENT.get();
    }
    @Override protected SoundEvent getHurtSound(DamageSource pDamageSource) {
        return SoundRegistrar.WRAITH_HURT.get();
    }
    @Override protected SoundEvent getDeathSound() {
        return SoundRegistrar.WRAITH_DEATH.get();
    }

    public void initialiseGoals() {
        this.usePortalGoal = new UsePortalGoal(this);
        this.moveGoal = new MoveToTargetBlockGoal(this, false, 0);
        this.targetGoal = new SelectedTargetGoal<>(this, true, true);
        this.garrisonGoal = new GarrisonGoal(this);
        this.itemGoal = new UnitItemGoal(this);
        this.attackGoal = new UnitRangedAttackGoal<>(this, TitheLedger.WINDUP_TICKS);
        this.buildingClock = new UnitBowAttackGoal<>(this);
        this.attackBuildingGoal = new RangedAttackBuildingGoal<>(this, this.buildingClock);
        this.returnResourcesGoal = new ReturnResourcesGoal(this);
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

    @Override
    public boolean isPushable() {
        return true;
    }
}
