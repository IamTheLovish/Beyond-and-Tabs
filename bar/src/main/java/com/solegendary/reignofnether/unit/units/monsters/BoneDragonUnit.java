package com.solegendary.reignofnether.unit.units.monsters;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientboundPacket;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.time.NightUtils;
import com.solegendary.reignofnether.time.TimeUtils;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.ability.abilities.DragonDive;
import com.solegendary.reignofnether.unit.controls.DragonMoveControl;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.piglins.GhastUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Gravebound T3 experimental - the <b>Bone Dragon</b>: the <b>Air striker</b> (claude/t3-roles.md, section 4). A
 * skeletal wyrm on the Ghast unit's flying RTS brain (movement, targeting, the ranged goal), drawn by
 * {@code BoneDragonRenderer}. The strategy it opens: hit anywhere, fast - over walls, onto the economy, onto a
 * commander. The enemy's moment: "where will it strike next" - spread anti-air or lose the back line.
 * <ul>
 *   <li><b>Fast</b> ({@link #movementSpeed}): the fastest T3 in the air; raids and leaves before the army turns.</li>
 *   <li><b>Soul breath</b> ({@link #breathe}): its attack on units is a cone of soul-fire {@link #BREATH_LENGTH} blocks
 *   long and {@link #BREATH_HALF_ANGLE_DEG} degrees to each side - every enemy in it takes the dragon's attack damage
 *   and withers ({@link #WITHER_TICKS}). Its hits land on groups; a spread army takes one unit's worth. Buildings it
 *   still cracks with the ghast's fireball (3x to buildings), so a raid on an economy is real.</li>
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

    /** The breath cone: length, half-angle, the wither it leaves, and the night bonus. */
    public static final double BREATH_LENGTH = 12.0;
    public static final double BREATH_HALF_ANGLE_DEG = 32.0;
    public static final int WITHER_TICKS = 3 * 20;
    public static final float NIGHT_DAMAGE_MULT = 1.3f;
    /** The breath reaches the ground from the dragon's altitude: how far below (and above) it the cone counts. */
    static final double BREATH_HEIGHT_WINDOW = 20.0;
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

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(landedAtAccessor, -1);
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
            le.hurt(sl.damageSources().mobAttack(this), IMPACT_DAMAGE);
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

    /** On a unit the dragon breathes; the fireball (GhastUnit) is kept for buildings and attack-ground. */
    @Override
    public void performUnitRangedAttack(LivingEntity target, float velocity) {
        if (target == null || level().isClientSide() || this.hasEffect(MobEffectRegistrar.DISARM.get()))
            return;
        if (!(level() instanceof ServerLevel sl))
            return;
        breathe(sl, new Vec3(target.getX() - getX(), 0, target.getZ() - getZ()));
        if (target instanceof Unit unit)
            FogOfWarClientboundPacket.revealRangedUnit(unit.getOwnerName(), this.getId());
        showShootingFace();
    }

    /**
     * One breath along the flat direction {@code dir} (from the dragon's position): every hostile unit within
     * {@link #BREATH_LENGTH} and {@link #BREATH_HALF_ANGLE_DEG} of it takes {@link #getUnitAttackDamage} as soul-fire
     * (indirect magic from the dragon, so its kills still rise) and withers. Returns how many it hit. Public for the
     * game test.
     */
    public int breathe(ServerLevel sl, Vec3 dir) {
        String owner = getOwnerName();
        if (owner == null || owner.isEmpty())
            return 0;
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        if (flat.lengthSqr() < 1.0e-4)
            flat = Vec3.directionFromRotation(0, getYRot());
        Vec3 d = flat.normalize();
        double cosHalf = Math.cos(Math.toRadians(BREATH_HALF_ANGLE_DEG));
        float dmg = getUnitAttackDamage();
        List<LivingEntity> hit = new ArrayList<>();
        // UnitGrid hands back a shared candidate list; copy the hits out before hurting anything (a death can rebucket)
        for (LivingEntity le : UnitGrid.near(sl, getX(), getZ(), BREATH_LENGTH + 2, scratch)) {
            if (le == this || !le.isAlive() || le.level() != sl || !(le instanceof Unit u))
                continue;
            String o = u.getOwnerName();
            if (o == null || o.isEmpty() || o.equals(owner) || AlliancesServerEvents.isAllied(owner, o))
                continue;
            if (Math.abs(le.getY() - getY()) > BREATH_HEIGHT_WINDOW)
                continue;
            double ex = le.getX() - getX(), ez = le.getZ() - getZ();
            double dist = Math.sqrt(ex * ex + ez * ez);
            if (dist - le.getBbWidth() / 2 > BREATH_LENGTH)
                continue;
            // straight under the jaws counts whichever way it faces; past that, inside the cone
            if (dist > 1.5 && (ex * d.x + ez * d.z) / dist < cosHalf)
                continue;
            hit.add(le);
        }
        scratch.clear();
        for (LivingEntity le : hit) {
            le.hurt(sl.damageSources().indirectMagic(this, this), dmg);
            if (le.isAlive())
                le.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_TICKS, 0));
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
        }
        // the breath itself: a sheet of soul-fire from the jaws down the cone (a few rays, not one per block)
        Vec3 mouth = position().add(0, getBbHeight() * 0.4, 0);
        double half = Math.toRadians(BREATH_HALF_ANGLE_DEG);
        double base = Math.atan2(d.z, d.x);
        for (int ray = -2; ray <= 2; ray++) {
            double a = base + half * ray / 2.0;
            for (int i = 2; i <= (int) BREATH_LENGTH; i += 2) {
                double t = i / BREATH_LENGTH;
                sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, mouth.x + Math.cos(a) * i, mouth.y - (mouth.y - getY()) * t - t * 2.0,
                    mouth.z + Math.sin(a) * i, 2, 0.3, 0.3, 0.3, 0.01);
            }
        }
        sl.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.HOSTILE, 3f, 0.7f);
        return hit.size();
    }

    @Override
    public void tick() {
        super.tick();
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
