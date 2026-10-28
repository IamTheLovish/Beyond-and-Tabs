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
import com.solegendary.reignofnether.unit.controls.DragonMoveControl;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.piglins.GhastUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused (server thread)

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
        for (var a : getAbilities().get())
            if (a == fog)
                return;
        getAbilities().add(fog);
    }
    private com.solegendary.reignofnether.ability.abilities.WitheringFog fog;

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
        if (!level().isClientSide() && tickCount > 5 && !getPersistentData().getBoolean(KEY_ANNOUNCED)
                && getOwnerName() != null && !getOwnerName().isEmpty()) {
            getPersistentData().putBoolean(KEY_ANNOUNCED, true);
            com.solegendary.reignofnether.unit.T3ServerEvents.announceFielded(getOwnerName(),
                net.minecraft.network.chat.Component.translatable("entity.reignofnether.bone_dragon_unit"));
        }
    }
}
