package com.solegendary.reignofnether.unit.units.monsters;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingDamage;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientboundPacket;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.sounds.Sfx;
import com.solegendary.reignofnether.time.NightUtils;
import com.solegendary.reignofnether.time.TimeUtils;
import com.solegendary.reignofnether.unit.FireBreath;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.ability.abilities.DragonDive;
import com.solegendary.reignofnether.unit.controls.DragonMoveControl;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.piglins.GhastUnit;

import net.minecraft.client.resources.language.I18n;
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
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static com.solegendary.reignofnether.util.MiscUtil.fcs;

/**
 * Gravebound T3 experimental - the <b>Bone Dragon</b>: the <b>Air striker</b> (claude/t3-roles.md, section 4). A
 * skeletal wyrm on the Ghast unit's flying RTS brain (movement, targeting, the ranged goal), drawn by
 * {@code BoneDragonRenderer}. The strategy it opens: hit anywhere, fast - over walls, onto the economy, onto a
 * commander. The enemy's moment: "where will it strike next" - spread anti-air or lose the back line.
 * <ul>
 *   <li><b>Fast</b> ({@link #movementSpeed}): the fastest T3 in the air; raids and leaves before the army turns.</li>
 *   <li><b>Blue fire breath</b> ({@link #startBreath}, the rules in {@link FireBreath}; lovish, Nov 5: "the bone dragon
 *   should get a blue fire breath attack from the air not the current ghast sound effects and projectiles", "bone
 *   dragon can use this fire breath on the ground as well"): every attack - on a unit, a building or the ground - is a
 *   breath of {@link FireBreath#TICKS} ticks that burns every {@link FireBreath#PULSE_TICKS}
 *   ({@link #breathPulse}): every enemy unit and building in its cone takes its attack damage per SECOND of breath
 *   (buildings by BuildingDamage's unit rate) and withers ({@link #WITHER_TICKS}). From the air the cone runs from the
 *   jaws down onto a line of fire it strafes across the target; landed, it is a forward cone along the ground. Its
 *   hits land on groups; a spread army takes one unit's worth. No ghast fireball, no ghast sound.</li>
 *   <li><b>Night play</b>: at night (or near a Gravebound night source) the breath does x{@link #NIGHT_DAMAGE_MULT}.
 *   A Gravebound player times the strike.</li>
 *   <li><b>Withering Fog</b> (the faction power): a lingering line of grave-fog, area denial for a unit that holds
 *   no ground. Kills rise as skeletons for 20 s (DragonRaiseServerEvents).</li>
 * </ul>
 * Countered by: anti-air and flak (it flies low and wears no armour: Sky Watch, Flak, Windcallers shred it), a
 * commander's anti-air shot, and archers under it. It beats everything without anti-air. Arrival announced to all.
 */
public class BoneDragonUnit extends GhastUnit {

    final static public float attackDamage = 20.0f;
    final static public float attacksPerSecond = 0.3f;
    final static public float maxHealth = 900.0f;
    final static public float movementSpeed = 0.3f;    // 0.2 -> 0.3 (t3-roles): an air striker outruns the army it raids (the Celestial Dragon's 0.28, the Corsair's 0.3)
    final static public float attackRange = 12;       // 22 -> 12: the breath's reach; it closes on its prey (the fireball for buildings fires from the same)
    final static public float aggroRange = 18;        // 22 -> 18: engages what it sees, keeps the sky-bolt dragon's habit of picking fights
    public static final float SCALE = 3.5f;

    /** The wither a burn leaves, and the night bonus. */
    public static final int WITHER_TICKS = 3 * 20;
    public static final float NIGHT_DAMAGE_MULT = 1.3f;
    /** Where the jaws are, from the feet: ahead and up (the rest pose of BoneDragonModel at the renderer's 1.4x). The
     *  server's cone starts here; the client draws from the real jaw bone (BoneDragonRenderer). */
    public static final double JAWS_AHEAD = 5.0, JAWS_UP = 2.8;
    /** A breath whose aim gets this much farther from the jaws than the cone reaches stops (its target got away). */
    static final double BREATH_LOST = 4.0;
    static final String KEY_ANNOUNCED = "bt_t3_announced";

    /**
     * The Dive (ability DragonDive, #145 - lovish: descend to the ground to fight, like BAR's land command, on a LONG
     * cooldown so diving in is punished and is not spammed). These are PROPOSALS sent to lovish for his numbers:
     * the cooldown (s), the farthest spot it dives to, the impact's radius and damage (ground enemies only), how long it
     * stays down, how long before the button may take it off early, and how long a dive may take before it gives up.
     */
    public static final int DIVE_CD_SECONDS = 90;
    public static final int DIVE_RANGE = 16;
    public static final float IMPACT_RADIUS = 4.5f;
    public static final float IMPACT_DAMAGE = 60f;
    public static final int LAND_TICKS = 10 * 20;
    public static final int MIN_LAND_TICKS = 4 * 20;
    static final int DIVE_MAX_TICKS = 5 * 20;
    public static final double IMPACT_KNOCKBACK = 0.8;

    /** The game time it landed at, or -1 in the air: synced, so the button and the rig know it is down. */
    public static final EntityDataAccessor<Integer> landedAtAccessor =
        SynchedEntityData.defineId(BoneDragonUnit.class, EntityDataSerializers.INT);
    private Vec3 diveTo = null;
    private int diveTicks = 0;
    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused (server thread)

    /**
     * The running breath, told to the clients so they draw the very cone the server burns (FireBreath): its target
     * (entity id, -1 for a building or the ground), the aim's base point (the target's spot, kept up to date each burn),
     * the sweep's offset, whether it is the landed cone, and the breath's number - set LAST, so a client reads the
     * others first: a new number starts the client's breath, 0 ends it.
     */
    public static final EntityDataAccessor<Integer> breathTargetAccessor =
        SynchedEntityData.defineId(BoneDragonUnit.class, EntityDataSerializers.INT);
    public static final EntityDataAccessor<Vector3f> breathAimAccessor =
        SynchedEntityData.defineId(BoneDragonUnit.class, EntityDataSerializers.VECTOR3);
    public static final EntityDataAccessor<Vector3f> breathOffsetAccessor =
        SynchedEntityData.defineId(BoneDragonUnit.class, EntityDataSerializers.VECTOR3);
    public static final EntityDataAccessor<Boolean> breathLandedAccessor =
        SynchedEntityData.defineId(BoneDragonUnit.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<Integer> breathSeqAccessor =
        SynchedEntityData.defineId(BoneDragonUnit.class, EntityDataSerializers.INT);

    // the breath, server side: its tick (-1 = none), its target (null: a building or the ground), aim base and offset
    private int breathTick = -1;
    private LivingEntity breathTarget = null;
    private Vec3 breathBase = Vec3.ZERO, breathOffset = Vec3.ZERO;
    private boolean breathLanded = false;
    private int breathSeq = 0;
    private static final List<BuildingPlacement> burnt = new ArrayList<>();   // buildings a burn reached (server thread)

    /** Client: the breath's own tick (counted in client ticks by BreathFx; -1 = not breathing). */
    public int clientBreathTick = -1;
    /** Client: the jaws (x, y, z) and their forward direction, from the posed model (BoneDragonRenderer), and when. */
    public final double[] clientJaws = new double[6];
    public long clientJawsAt = Long.MIN_VALUE;
    /** Client: where BreathFx last drew the jaws (NaN before the first): it spawns along the path between ticks. */
    public final double[] clientJawsLast = { Double.NaN, Double.NaN, Double.NaN };
    /** Client: the dragons breathing now (BreathFx draws them; entries leave when the breath ends or the entity goes). */
    public static final java.util.Set<BoneDragonUnit> CLIENT_BREATHING =
        java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(landedAtAccessor, -1);
        this.entityData.define(breathTargetAccessor, -1);
        this.entityData.define(breathAimAccessor, new Vector3f());
        this.entityData.define(breathOffsetAccessor, new Vector3f());
        this.entityData.define(breathLandedAccessor, false);
        this.entityData.define(breathSeqAccessor, 0);
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (breathSeqAccessor.equals(key) && level() != null && level().isClientSide()) {
            if (this.entityData.get(breathSeqAccessor) != 0) {
                clientBreathTick = 0;
                CLIENT_BREATHING.add(this);
            } else {
                clientBreathTick = -1;
            }
        }
    }

    public BoneDragonUnit(EntityType<? extends Ghast> entityType, Level level) {
        super(entityType, level);
        // #145 (lovish: the flight is wrong): a dragon's nose, turn and brake, not the ghast's nudges (DragonFlight)
        this.moveControl = new DragonMoveControl(this);
    }

    /** How fast (degrees per tick) a hovering dragon turns to face what it attacks. */
    static final float HOVER_TURN = 6f;

    /**
     * The ghast snaps its yaw to its target or its velocity every tick (a 180 in one tick); the dragon's body faces
     * along its flight while it flies ({@link DragonMoveControl#heading}, itself turned at a capped rate) and, hovering,
     * turns toward what it attacks at {@link #HOVER_TURN} degrees per tick - the rig's bank reads the turn either way.
     * Neither changes what the breath or the fireball hit: both aim at the target, not along the body.
     */
    @Override
    public void updateRotation() {
        if (level().isClientSide() || !(this.moveControl instanceof DragonMoveControl dc)) {
            this.yBodyRot = this.getYRot();
            return;
        }
        float yaw;
        if (dc.isCruising()) {
            yaw = dc.heading();
        } else {
            yaw = getYRot();
            LivingEntity target = getTarget();
            BlockPos ground = getRangedAttackGroundGoal() == null ? null : getRangedAttackGroundGoal().getGroundTarget();
            double x = 0, z = 0;
            if (target != null) {
                x = target.getX() - getX();
                z = target.getZ() - getZ();
            } else if (ground != null) {
                x = ground.getX() + 0.5 - getX();
                z = ground.getZ() + 0.5 - getZ();
            }
            if (Math.abs(x) > 0.05 || Math.abs(z) > 0.05)
                yaw = Mth.approachDegrees(yaw, -((float) Mth.atan2(x, z)) * Mth.RAD_TO_DEG, HOVER_TURN);
            dc.setHeading(yaw);
        }
        this.setYRot(yaw);
        this.yBodyRot = yaw;
    }

    /**
     * GhastUnit rebuilds its abilities from the shared static set here; add this unit's own faction power on top - one
     * fog per dragon, kept across refreshes so its cooldown survives them (the Gorehorn Rhino's rule).
     */
    @Override
    public void updateAbilityButtons() {
        super.updateAbilityButtons();
        if (getAbilities() == null)
            return;
        if (fog == null)
            fog = new com.solegendary.reignofnether.ability.abilities.WitheringFog();
        if (dive == null)
            dive = new DragonDive();
        boolean hasFog = false, hasDive = false;
        for (var a : getAbilities().get()) {
            hasFog |= a == fog;
            hasDive |= a == dive;
        }
        if (!hasFog)
            getAbilities().add(fog);
        if (!hasDive)
            getAbilities().add(dive);
    }
    private com.solegendary.reignofnether.ability.abilities.WitheringFog fog;
    private DragonDive dive;

    // ---- the Dive -----------------------------------------------------------------------------------------------

    /** Down on the ground after a dive (both sides). */
    public boolean isLanded() {
        return this.entityData.get(landedAtAccessor) >= 0;
    }

    /** Stooping onto its spot (server side). */
    public boolean isDiving() {
        return diveTo != null;
    }

    /** Where the running dive comes down (server side; null when not diving). For the game test. */
    public Vec3 diveTarget() {
        return diveTo;
    }

    /** Down long enough that the Dive button may take it off early (both sides: the landing time is synced). */
    public boolean canTakeOff() {
        int at = this.entityData.get(landedAtAccessor);
        return at >= 0 && (int) level().getGameTime() - at >= MIN_LAND_TICKS;
    }

    /** A landed dragon is no flyer: ground melee reaches it, as the Celestial Dragon grounded by wind. */
    @Override
    public boolean isFlyingUnit() {
        return !isLanded();
    }

    /**
     * Stoop onto the ground at {@code at} (at most {@link #DIVE_RANGE} blocks away flat; farther, the spot that far
     * toward it). False if it is already diving or down, or dead. Public for the game test; the button is DragonDive.
     */
    public boolean startDive(Vec3 at) {
        if (!(level() instanceof ServerLevel sl) || !isAlive() || isLanded() || isDiving()
                || !(this.moveControl instanceof DragonMoveControl dc))
            return false;
        Vec3 flat = new Vec3(at.x - getX(), 0, at.z - getZ());
        if (flat.length() > DIVE_RANGE)
            flat = flat.normalize().scale(DIVE_RANGE);
        double x = getX() + flat.x, z = getZ() + flat.z;
        // the ground under the dragon's own height at the spot: scanned down, so a roof over it (a cave, an
        // overhang) is not taken for the ground (#300 review: r1's heightmap "landed" in mid-air under a roof)
        Integer gy = groundBelow(sl, x, z);
        if (gy == null)
            return false;
        diveTo = new Vec3(x, gy, z);
        diveTicks = 0;
        if (getMoveGoal() != null)
            getMoveGoal().stopMoving();
        dc.setDive(diveTo);
        sl.playSound(null, blockPosition(), SoundEvents.PHANTOM_SWOOP, SoundSource.HOSTILE, 4f, 0.5f);
        return true;
    }

    /** How far down the ground is looked for under a dive's spot (blocks below the dragon). */
    static final int GROUND_SCAN = 64;

    /**
     * The standing height on the first solid block below the dragon's height at (x, z), or null: the spot is inside
     * terrain at the dragon's height (a cliff beside it - #300 review round 2: r2 returned a ledge above it), the first
     * thing below is water or lava (no ground to land on), or there is nothing within GROUND_SCAN.
     */
    private Integer groundBelow(ServerLevel sl, double x, double z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(getY()), Mth.floor(z));
        if (!open(sl, p) || !open(sl, p.above()))
            return null;
        for (int i = 0; i <= GROUND_SCAN && p.getY() > sl.getMinBuildHeight(); i++, p.move(0, -1, 0)) {
            var state = sl.getBlockState(p);
            if (!state.getFluidState().isEmpty())
                return null;
            if (!state.getCollisionShape(sl, p).isEmpty())
                return p.getY() + 1;
        }
        return null;
    }

    private static boolean open(ServerLevel sl, BlockPos p) {
        var state = sl.getBlockState(p);
        return state.getFluidState().isEmpty() && state.getCollisionShape(sl, p).isEmpty();
    }

    // ---- the Dive's state across a save and load (#300 review: a reload must not hand back a fresh dive): the
    // cooldown left, the ticks already spent on the ground, and a running dive's spot - relative, never game times
    static final String KEY_DIVE_CD = "bt_dive_cd", KEY_LANDED_FOR = "bt_dive_landed_for", KEY_DIVE_TO = "bt_dive_to";
    /** A cooldown restored from the save: told to the clients on the first tick (none are tracking yet at load). */
    private boolean resendDiveCooldown = false;

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (dive != null && dive.getCooldown(this) > 0)
            tag.putFloat(KEY_DIVE_CD, dive.getCooldown(this));
        int at = this.entityData.get(landedAtAccessor);
        if (at >= 0)
            tag.putInt(KEY_LANDED_FOR, (int) Math.max(0, level().getGameTime() - at));
        if (diveTo != null) {
            CompoundTag t = new CompoundTag();
            t.putDouble("x", diveTo.x);
            t.putDouble("y", diveTo.y);
            t.putDouble("z", diveTo.z);
            t.putInt("ticks", diveTicks);
            if (this.moveControl instanceof DragonMoveControl dc) {
                t.putDouble("speed", dc.speed());
                t.putDouble("climb", dc.climb());
            }
            tag.put(KEY_DIVE_TO, t);
        }
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(KEY_DIVE_CD) && dive != null) {
            dive.setCooldown(tag.getFloat(KEY_DIVE_CD), false, this);
            resendDiveCooldown = true;
        }
        DragonMoveControl dc = this.moveControl instanceof DragonMoveControl c ? c : null;
        if (dc != null)
            dc.setHeading(getYRot());
        if (tag.contains(KEY_LANDED_FOR)) {
            this.entityData.set(landedAtAccessor, (int) (level().getGameTime() - tag.getInt(KEY_LANDED_FOR)));
            if (dc != null)
                dc.setLanded(true);
        } else if (tag.contains(KEY_DIVE_TO)) {
            CompoundTag t = tag.getCompound(KEY_DIVE_TO);
            diveTo = new Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z"));
            diveTicks = t.getInt("ticks");
            if (dc != null)
                dc.restoreFlight(t.getDouble("speed"), t.getDouble("climb"));
            if (dc != null)
                dc.setDive(diveTo);
        }
    }

    /** Lets it up again if it has been down {@link #MIN_LAND_TICKS}; else it stays. The Dive button on the ground. */
    public void takeOffEarly() {
        if (canTakeOff())
            takeOff();
    }

    private void land(ServerLevel sl) {
        diveTo = null;
        this.entityData.set(landedAtAccessor, (int) sl.getGameTime());
        if (this.moveControl instanceof DragonMoveControl dc)
            dc.setLanded(true);
        impact(sl);
    }

    private void takeOff() {
        this.entityData.set(landedAtAccessor, -1);
        if (this.moveControl instanceof DragonMoveControl dc) {
            dc.setLanded(false);
            // back up to its flying height over where it is, unless an order is already waiting
            if (getMoveGoal() == null || getMoveGoal().getMoveTarget() == null)
                dc.setWantedPosition(getX(), getY() + 10, getZ(), 1.0);
        }
    }

    /**
     * The landing: every ground enemy unit within {@link #IMPACT_RADIUS} (flyers are over it) takes
     * {@link #IMPACT_DAMAGE} from the dragon and is thrown back. Returns how many it hit. Public for the game test.
     */
    public int impact(ServerLevel sl) {
        String owner = getOwnerName();
        List<LivingEntity> hit = new ArrayList<>();
        if (owner != null && !owner.isEmpty()) {
            for (LivingEntity le : UnitGrid.near(sl, getX(), getZ(), IMPACT_RADIUS + 3, scratch)) {
                if (le == this || !le.isAlive() || le.level() != sl || !(le instanceof Unit u) || u.isFlyingUnit())
                    continue;
                String o = u.getOwnerName();
                if (o == null || o.isEmpty() || o.equals(owner) || AlliancesServerEvents.isAllied(owner, o))
                    continue;
                if (Math.abs(le.getY() - getY()) > 3)
                    continue;
                double ex = le.getX() - getX(), ez = le.getZ() - getZ();
                if (Math.sqrt(ex * ex + ez * ez) - le.getBbWidth() / 2 > IMPACT_RADIUS)
                    continue;
                hit.add(le);
            }
            scratch.clear();
        }
        for (LivingEntity le : hit) {
            // explicit: RoN's actuallyHurt mixin turns a unit's MOB_ATTACK into its attack damage (20, x1.3 at night) -
            // the impact is its own number (#300 CI: the foes lost 20, not 60); armour and resists still apply
            com.solegendary.reignofnether.unit.UnitDamage.hurtExplicit(le, sl.damageSources().mobAttack(this), IMPACT_DAMAGE);
            if (le.isAlive())
                le.knockback(IMPACT_KNOCKBACK, getX() - le.getX(), getZ() - le.getZ());
        }
        sl.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 6, IMPACT_RADIUS * 0.4, 0.2, IMPACT_RADIUS * 0.4, 0.0);
        for (int i = 0; i < 16; i++) {
            double a = i / 16.0 * Math.PI * 2;
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX() + Math.cos(a) * IMPACT_RADIUS, getY() + 0.2,
                getZ() + Math.sin(a) * IMPACT_RADIUS, 2, 0.2, 0.1, 0.2, 0.01);
        }
        sl.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 3f, 0.6f);
        return hit.size();
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.BONE_DRAGON; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.ARMOR, 0)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), 26)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0.3f);
    }

    /** Night, or a Gravebound night source: the dragon's hour. Public for the game test. */
    public boolean isNightFor() {
        return !TimeUtils.isDay(level()) || NightUtils.isInRangeOfNightSource(getEyePosition(), level().isClientSide());
    }

    /** The breath's damage: the attack damage, x{@link #NIGHT_DAMAGE_MULT} at night (the HUD's damage stat shows it). */
    @Override
    public float getUnitAttackDamage() {
        float base = super.getUnitAttackDamage();
        return isNightFor() ? base * NIGHT_DAMAGE_MULT : base;
    }

    /** On a unit: a breath at it (it follows the unit for the breath's length). */
    @Override
    public void performUnitRangedAttack(LivingEntity target, float velocity) {
        if (target == null || level().isClientSide() || this.hasEffect(MobEffectRegistrar.DISARM.get()))
            return;
        if (startBreath(target, aimOf(target)) && target instanceof Unit unit)
            FogOfWarClientboundPacket.revealRangedUnit(unit.getOwnerName(), this.getId());
    }

    /** On a building or the ground: a breath at the point - never the ghast's fireball (lovish: no ghast projectiles). */
    @Override
    public void performUnitRangedAttack(double x, double y, double z, float velocity) {
        if (level().isClientSide() || this.hasEffect(MobEffectRegistrar.DISARM.get()))
            return;
        startBreath(null, new Vec3(x, y, z));
    }

    /** Where a breath at this entity aims: its feet on the ground (the fire lands there), its middle in the air. */
    private static Vec3 aimOf(LivingEntity e) {
        boolean flyer = e instanceof Unit u ? u.isFlyingUnit() : !e.onGround();
        return flyer ? e.position().add(0, e.getBbHeight() * 0.5, 0) : e.position();
    }

    /** Breathing now (server side). */
    public boolean isBreathing() {
        return breathTick >= 0;
    }

    /** Breathing now, as the client knows it (the model holds the jaw open, BreathFx draws the fire). */
    public boolean isBreathingClient() {
        return clientBreathTick >= 0 && clientBreathTick < FireBreath.TICKS;
    }

    /**
     * The jaws, server side, for a breath toward {@code aim}: ahead of the body toward it (it turns to what it burns),
     * never past an aim closer than its neck is long (a foe under its chin: the head ducks to it).
     */
    Vec3 jaws(Vec3 aim) {
        double dx = aim.x - getX(), dz = aim.z - getZ(), l = Math.sqrt(dx * dx + dz * dz);
        double ahead = jawsAhead(l);
        if (l < 0.5) {
            Vec3 f = Vec3.directionFromRotation(0, yBodyRot);
            dx = f.x;
            dz = f.z;
            l = Math.sqrt(dx * dx + dz * dz);
        }
        return new Vec3(getX() + dx / l * ahead, getY() + JAWS_UP, getZ() + dz / l * ahead);
    }

    /** How far ahead of the body the jaws are for an aim {@code flat} blocks away (flat): JAWS_AHEAD, less up close. */
    public static double jawsAhead(double flat) {
        return Math.min(JAWS_AHEAD, Math.max(1.0, flat - 1.0));
    }

    /**
     * Starts a breath at {@code aim} (following {@code target} while it lives; null for a building or the ground):
     * the landed cone if the dragon is down, else the airborne one strafing along its heading. False if one is already
     * running, or it has no owner. Public for the game test; the attack goals call it through performUnitRangedAttack.
     */
    public boolean startBreath(@Nullable LivingEntity target, Vec3 aim) {
        if (!(level() instanceof ServerLevel sl) || !isAlive() || isBreathing() || getOwnerName() == null
                || getOwnerName().isEmpty())
            return false;
        aimBreath(target, aim);
        breathTick = 0;
        breathSeq = breathSeq == Integer.MAX_VALUE ? 1 : breathSeq + 1;
        this.entityData.set(breathSeqAccessor, breathSeq);   // last: the client reads the rest before this
        // our own sound (sounds/Sfx, generated from maths), pitched down to a dragon's roar of flame; no ghast
        Vec3 j = jaws(aim);
        Sfx.SHOT_FIRE.play(sl, j.x, j.y, j.z, 1.4f, 0.5f);
        return true;
    }

    /**
     * Points the breath at {@code aim}, following {@code target} (null: a building or the ground): the landed cone if
     * the dragon is down, else the airborne one strafing along its heading. The client reads these every tick.
     */
    private void aimBreath(@Nullable LivingEntity target, Vec3 aim) {
        breathTarget = target;
        breathBase = aim;
        breathLanded = isLanded();
        Vec3 j = jaws(aim);
        double[] off = new double[3];
        if (breathLanded) {
            FireBreath.groundOffset(j.x, j.z, aim.x, aim.z, off);
        } else {
            Vec3 h = Vec3.directionFromRotation(0, yBodyRot);
            FireBreath.airOffset(h.x, h.z, off);
        }
        breathOffset = new Vec3(off[0], off[1], off[2]);
        this.entityData.set(breathTargetAccessor, target == null ? -1 : target.getId());
        this.entityData.set(breathAimAccessor, new Vector3f((float) aim.x, (float) aim.y, (float) aim.z));
        this.entityData.set(breathOffsetAccessor, new Vector3f((float) off[0], (float) off[1], (float) off[2]));
        this.entityData.set(breathLandedAccessor, breathLanded);
    }

    /** Ends the breath (both sides learn it from the breath number going to 0). */
    public void stopBreath() {
        if (breathTick < 0)
            return;
        breathTick = -1;
        breathTarget = null;
        this.entityData.set(breathSeqAccessor, 0);
    }

    /** The breath's aim at its tick t: the base (the target where it is now) swept by the offset. */
    private Vec3 breathAim(int t) {
        double s = FireBreath.sweep(t);
        return new Vec3(FireBreath.along(breathBase.x, breathOffset.x, s), FireBreath.along(breathBase.y, breathOffset.y, s),
            FireBreath.along(breathBase.z, breathOffset.z, s));
    }

    /** One server tick of the breath: follow the target, burn on the pulse ticks, stop at the end or when it is lost. */
    private void tickBreath(ServerLevel sl) {
        if (!isAlive() || this.hasEffect(MobEffectRegistrar.DISARM.get())) {
            stopBreath();
            return;
        }
        // an order (or RoN's own pick) onto another unit mid-breath: the fire swings to it now, not after burning out on
        // the old target (CI 42012754225). The breath's clock runs on, so a new order never buys an extra burn
        LivingEntity ordered = getTargetGoal().getTarget();
        if (ordered != null && ordered != breathTarget && ordered.isAlive() && ordered.level() == sl)
            aimBreath(ordered, aimOf(ordered));
        if (breathTarget != null && breathTarget.isAlive() && breathTarget.level() == sl)
            breathBase = aimOf(breathTarget);
        else
            breathTarget = null;   // dead or gone: the fire plays out where it last was
        Vec3 aim = breathAim(breathTick);
        if (aim.distanceTo(jaws(aim)) > (breathLanded ? FireBreath.GROUND_LENGTH : FireBreath.AIR_MAX) + BREATH_LOST) {
            stopBreath();
            return;
        }
        if (FireBreath.pulse(breathTick)) {
            if (breathTick > 0) {
                this.entityData.set(breathAimAccessor, new Vector3f((float) breathBase.x, (float) breathBase.y, (float) breathBase.z));
                Sfx.SHOT_FIRE.play(sl, aim.x, aim.y, aim.z, 1.0f, 0.55f + getRandom().nextFloat() * 0.15f);
            }
            breathPulse(sl, aim, breathLanded);
        }
        if (++breathTick >= FireBreath.TICKS)
            stopBreath();
    }

    /** {@link #breathPulse(ServerLevel, Vec3, boolean)} in the dragon's own mode (landed or in the air). */
    public int breathPulse(ServerLevel sl, Vec3 aim) {
        return breathPulse(sl, aim, isLanded());
    }

    /**
     * One burn of the breath - THE shared entry, landed and airborne: the cone from the jaws toward {@code aim}
     * (FireBreath.reachesBox, on each body's box) burns every enemy unit in it for its attack damage per second of
     * breath (x1.3 at night) as soul-fire - indirect magic from the dragon, so its kills still rise - and withers it;
     * every enemy building it
     * reaches loses that, at BuildingDamage's unit rate. One grid query and one pass over the buildings per burn, never
     * one per flame drawn. Returns how many units it burnt. Public for the game test.
     */
    public int breathPulse(ServerLevel sl, Vec3 aim, boolean landed) {
        String owner = getOwnerName();
        if (owner == null || owner.isEmpty())
            return 0;
        Vec3 j = jaws(aim);
        Vec3 axis = aim.subtract(j);
        double dist = axis.length();
        if (dist < 1.0e-4) {
            axis = Vec3.directionFromRotation(0, yBodyRot);
            dist = 1;
        } else {
            axis = axis.scale(1 / dist);
        }
        double len = FireBreath.length(landed, dist), tan = FireBreath.tanHalf(landed);
        float dmg = getUnitAttackDamage() * FireBreath.PULSE_TICKS / 20f;
        Vec3 mid = j.add(axis.scale(len / 2));
        double reach = len / 2 + len * tan + 2;
        List<LivingEntity> hit = new ArrayList<>();
        // UnitGrid hands back a shared candidate list; copy the hits out before hurting anything (a death can rebucket)
        for (LivingEntity le : UnitGrid.near(sl, mid.x, mid.z, reach, scratch)) {
            if (le == this || !le.isAlive() || le.level() != sl || !(le instanceof Unit u))
                continue;
            String o = u.getOwnerName();
            if (o == null || o.isEmpty() || o.equals(owner) || AlliancesServerEvents.isAllied(owner, o))
                continue;
            // the body as it is - its box, like a building's. A ball as wide as the unit is tall (0.6 wide, ~2 across)
            // burnt a foe standing a block outside the fire (CI 42026188681)
            AABB box = le.getBoundingBox();
            if (FireBreath.reachesBox(j.x, j.y, j.z, axis.x, axis.y, axis.z, len, tan, box.minX, box.minY, box.minZ,
                    box.maxX, box.maxY, box.maxZ))
                hit.add(le);
        }
        scratch.clear();
        for (LivingEntity le : hit) {
            le.hurt(sl.damageSources().indirectMagic(this, this), dmg);
            if (le.isAlive())
                // no vanilla swirls: at the RTS camera they drew as black pixel blots beside the fire (eyes 42047299879);
                // the burn shows in BreathFx's licks and hit flash, the effect's icon stays
                le.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_TICKS, 0, false, false, true));
        }
        // buildings: collected first, then burnt (a building that falls leaves the list)
        burnt.clear();
        for (BuildingPlacement bp : BuildingServerEvents.getBuildings()) {
            if (bp.getLevel() != sl || bp.ownerName == null || bp.ownerName.isEmpty() || bp.isFriendlyTo(owner)
                    || !bp.isAttackable())
                continue;
            BlockPos lo = bp.minCorner, hi = bp.maxCorner;
            if (hi.getX() + 1 < mid.x - reach || lo.getX() > mid.x + reach || hi.getZ() + 1 < mid.z - reach
                    || lo.getZ() > mid.z + reach)
                continue;
            if (FireBreath.reachesBox(j.x, j.y, j.z, axis.x, axis.y, axis.z, len, tan, lo.getX(), lo.getY(), lo.getZ(),
                    hi.getX() + 1, hi.getY() + 1, hi.getZ() + 1))
                burnt.add(bp);
        }
        for (BuildingPlacement bp : burnt)
            bp.destroyRandomBlocks(BuildingDamage.unit(dmg), this);
        burnt.clear();
        return hit.size();
    }

    // ---- its voice: bone, not a ghast (lovish: no ghast sounds) ------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(@NotNull DamageSource source) {
        return SoundEvents.SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SKELETON_DEATH;
    }

    /** A big body: deep (the ghast's sounds were pitched for a ghast). */
    @Override
    public float getVoicePitch() {
        return 0.55f + getRandom().nextFloat() * 0.1f;
    }

    /** The Ghast plays its sounds at 5 (heard map-wide); a dragon's bones at a big unit's 1.5. */
    @Override
    protected float getSoundVolume() {
        return 1.5f;
    }

    /** Its damage stat: the breath's damage per second, and buildings at BuildingDamage's unit rate (not 300%). */
    @Override
    public List<FormattedCharSequence> getAttackDamageStatTooltip() {
        return List.of(
            fcs(I18n.get("unitstats.reignofnether.attack_damage"), true),
            fcs(I18n.get("unitstats.reignofnether.attack_damage_bonus_buildings", Math.round(BuildingDamage.GLOBAL_MULT * 100) + "%"))
        );
    }

    @Override
    public void tick() {
        super.tick();
        if (breathTick >= 0 && level() instanceof ServerLevel bl)
            tickBreath(bl);
        if (level() instanceof ServerLevel sl && isAlive()) {
            if (resendDiveCooldown && dive != null) {
                resendDiveCooldown = false;
                com.solegendary.reignofnether.ability.AbilityClientboundPacket.sendSetCooldownPacket(getId(), dive.action, dive.getCooldown(this));
            }
            if (diveTo != null) {
                diveTicks++;
                if (onGround() || getY() - diveTo.y < 0.4)
                    land(sl);
                else if (diveTicks > DIVE_MAX_TICKS && this.moveControl instanceof DragonMoveControl dc) {
                    diveTo = null;                       // a roof in the way: give up, fly on
                    dc.setDive(null);
                }
            } else if (isLanded() && sl.getGameTime() - this.entityData.get(landedAtAccessor) >= LAND_TICKS) {
                takeOff();
            }
        }
        if (!level().isClientSide() && tickCount > 5 && !getPersistentData().getBoolean(KEY_ANNOUNCED)
                && getOwnerName() != null && !getOwnerName().isEmpty()) {
            getPersistentData().putBoolean(KEY_ANNOUNCED, true);
            com.solegendary.reignofnether.unit.T3ServerEvents.announceFielded(getOwnerName(),
                net.minecraft.network.chat.Component.translatable("entity.reignofnether.bone_dragon_unit"));
        }
    }
}
