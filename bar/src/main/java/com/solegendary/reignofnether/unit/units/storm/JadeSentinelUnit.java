package com.solegendary.reignofnether.unit.units.storm;

import com.solegendary.reignofnether.ability.abilities.GroundSlam;
import com.solegendary.reignofnether.ability.abilities.MirrorPlate;
import com.solegendary.reignofnether.ability.abilities.Thunderclap;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.TitanMass;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stormcallers T3 experimental, the second of Heaven's Gate (slot T): the <b>Jade Sentinel</b> (second-t3-design.md,
 * slice T3b-4). A seated jade guardian-lion statue grown into a walking wall: the slowest Storm unit, melee, with the
 * most HP. The Celestial Dragon is the dynasty's air caster; the Sentinel is the ground tank and projectile wall that
 * lets the casters work behind it.
 * <ul>
 *   <li><b>Mirror Plate</b> ({@link MirrorPlate}, tap, 20 s): for 4 s ranged projectiles striking its front arc are
 *   turned back on their shooter, and it walks at half speed.</li>
 *   <li><b>Thunderclap</b> ({@link Thunderclap}, tap, 30 s): a telegraphed ring of 6 blocks, damage and a 1 s stun.</li>
 *   <li><b>Ley Anchor</b> (passive): friends within {@link #ANCHOR_RADIUS} take {@link #ANCHOR_RANGED_RESIST} less ranged
 *   damage (a fixed modifier, so it never stacks with a second source of itself). The design's +15% energy regen is NOT
 *   built: energy is a player-wide pool with no per-unit regen to raise.</li>
 *   <li><b>Mass</b> ({@link TitanMass}): never pushed. Arrival announced to every player.</li>
 * </ul>
 * Countered by (never by a crippled stat): melee flanking from behind, magic that ignores plate, a long fight (it has
 * no ranged attack and its cooldowns are long), seals and silences. It has nothing against flyers. Loses to a focused
 * equal-cost T2 counter army. Subclasses the Earthshaker for its body and goals but drops its Ground Slam.
 * Placeholder look: the Earthshaker's jade golem at {@link #SCALE}x (Lane B draws the guardian-lion later, slice T3b-10).
 */
public class JadeSentinelUnit extends EarthshakerUnit {

    final static public float attackDamage = 18.0f;
    final static public float attacksPerSecond = 0.4f;
    final static public float maxHealth = 1500.0f;   // the design's number; 900/950/1000/1050/1100/1150/1250/1300/1400 are taken (no two T3s share an HP total)
    final static public float movementSpeed = 0.17f;   // the slowest Storm unit (the Earthshaker walks at 0.21)
    final static public float armorValue = 4.0f;
    final static public float rangedDamageResist = 0.3f;   // stone, as the Earthshaker
    final static public float attackRange = 3;
    final static public float aggroRange = 12;
    /** The body: the Earthshaker's golem grown this many times (placeholder; the entity's box is scaled the same). */
    public static final float SCALE = 2.0f;

    /** The production item's name (EntityRegistrar.getEntityType). */
    public static final String ITEM_NAME = "Jade Sentinel";
    static final String KEY_ANNOUNCED = "bt_t3_announced";
    static final String KEY_MIRROR_UNTIL = "bt_mirror_until";

    /** Ley Anchor. */
    public static final double ANCHOR_RADIUS = 8.0;
    public static final double ANCHOR_RANGED_RESIST = 0.10;
    public static final int ANCHOR_EVERY_TICKS = 20;
    private static final UUID ANCHOR_ID = UUID.fromString("5d1c7a0e-3b9f-4c1a-9e2d-6a4f8b0c7e11");
    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused (server thread)
    /** Friends currently wearing the anchor modifier, so it can come off when they leave or the Sentinel falls. */
    private final List<LivingEntity> anchored = new ArrayList<>();

    private MirrorPlate mirrorPlate;
    private Thunderclap thunderclap;

    public JadeSentinelUnit(EntityType<? extends IronGolem> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * EarthshakerUnit rebuilds its list from its shared static set here (also from its own constructor, before this
     * class's fields exist): drop the Ground Slam, add this unit's own abilities, one instance each so cooldowns survive.
     */
    @Override
    public void updateAbilityButtons() {
        super.updateAbilityButtons();
        if (getAbilities() == null)
            return;
        for (var a : getAbilities().get())
            if (a instanceof GroundSlam)
                getAbilities().remove(a);
        if (mirrorPlate == null)
            mirrorPlate = new MirrorPlate();
        if (thunderclap == null)
            thunderclap = new Thunderclap();
        getAbilities().add(mirrorPlate);
        getAbilities().add(thunderclap);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.STORM_T3B; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.ARMOR, armorValue)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), rangedDamageResist)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    // mass: never pushed, shoves small bodies aside (TitanMass)
    @Override
    public boolean isPushable() { return TitanMass.isPushable(); }

    @Override
    protected void doPush(@NotNull Entity other) { TitanMass.doPush(this, other); }

    // ---- Mirror Plate ----

    /** Raises the plate for {@link MirrorPlate#DURATION_TICKS}; it walks at about half speed meanwhile. */
    public void raiseMirror() {
        getPersistentData().putLong(KEY_MIRROR_UNTIL, level().getGameTime() + MirrorPlate.DURATION_TICKS);
        addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, MirrorPlate.DURATION_TICKS, MirrorPlate.SLOW_AMPLIFIER, false, false));
        if (level() instanceof ServerLevel sl)
            sl.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE,
                net.minecraft.sounds.SoundSource.HOSTILE, 2f, 1.2f);
    }

    public boolean mirrorActive() {
        return level().getGameTime() < getPersistentData().getLong(KEY_MIRROR_UNTIL);
    }

    /** Is a point in the plate's front arc (a 150 degree cone ahead of the body)? */
    public boolean inFrontArc(Vec3 point) {
        Vec3 facing = Vec3.directionFromRotation(0, yBodyRot);
        Vec3 to = new Vec3(point.x - getX(), 0, point.z - getZ());
        if (to.lengthSqr() < 1.0e-4)
            return true;
        return facing.dot(to.normalize()) > Math.cos(Math.toRadians(75));
    }

    /**
     * Mirror Plate: with the plate up, a projectile that strikes the front arc does no damage and is turned back on its
     * shooter (an arrow becomes a fresh arrow owned by the Sentinel, flying back at the shooter; any other projectile has
     * its flight reversed). Anything else (melee, spells, shots from behind) goes through. Server only.
     */
    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (!level().isClientSide() && mirrorActive() && source.is(DamageTypeTags.IS_PROJECTILE)
                && source.getDirectEntity() != null && inFrontArc(source.getDirectEntity().position())) {
            reflect(source);
            return false;
        }
        return super.hurt(source, amount);
    }

    private void reflect(DamageSource source) {
        Entity shot = source.getDirectEntity();
        Entity shooter = source.getEntity();
        ServerLevel sl = (ServerLevel) level();
        sl.sendParticles(ParticleTypes.END_ROD, shot.getX(), shot.getY(), shot.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
        sl.playSound(null, shot.blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_HIT,
            net.minecraft.sounds.SoundSource.HOSTILE, 2f, 1.6f);
        if (!(shot instanceof Projectile p))
            return;   // a non-entity hit (none today): blocked, nothing sent back
        Vec3 back = shooter != null && shooter.isAlive()
            ? shooter.getEyePosition().subtract(shot.position())
            : shot.getDeltaMovement().scale(-1);
        if (back.lengthSqr() < 1.0e-4)
            back = shot.getDeltaMovement().scale(-1);
        if (p instanceof AbstractArrow old) {
            Arrow fresh = new Arrow(sl, this);
            fresh.setPos(shot.getX(), shot.getY(), shot.getZ());
            fresh.setBaseDamage(old.getBaseDamage());
            fresh.shoot(back.x, back.y, back.z, 1.6f, 0f);
            fresh.pickup = AbstractArrow.Pickup.DISALLOWED;
            sl.addFreshEntity(fresh);
            old.discard();
        } else {
            p.setOwner(this);
            p.setDeltaMovement(back.normalize().scale(Math.max(0.5, shot.getDeltaMovement().length())));
            p.hurtMarked = true;
        }
    }

    // ---- Ley Anchor ----

    /**
     * Ley Anchor pulse: every friendly unit within {@link #ANCHOR_RADIUS} (and the Sentinel) wears a fixed
     * {@link #ANCHOR_RANGED_RESIST} ranged-resist modifier; friends that left it lose it. Returns how many wear it.
     * Public for the game test.
     */
    public int anchor(ServerLevel sl) {
        String owner = getOwnerName();
        List<LivingEntity> now = new ArrayList<>();
        if (owner != null && !owner.isEmpty() && isAlive()) {
            now.add(this);
            scratch.clear();
            for (LivingEntity le : UnitGrid.near(sl, getX(), getZ(), ANCHOR_RADIUS + 1, scratch)) {
                if (le == this || !le.isAlive() || !(le instanceof Unit u) || le.level() != sl)
                    continue;
                String other = u.getOwnerName();
                if (other == null || other.isEmpty() || !(owner.equals(other) || AlliancesServerEvents.isAllied(owner, other)))
                    continue;
                double dx = le.getX() - getX(), dz = le.getZ() - getZ();
                if (dx * dx + dz * dz > ANCHOR_RADIUS * ANCHOR_RADIUS || Math.abs(le.getY() - getY()) > 4)
                    continue;
                now.add(le);
            }
            scratch.clear();
        }
        for (LivingEntity old : anchored)
            if (!now.contains(old))
                setAnchorMod(old, false);
        for (LivingEntity le : now)
            setAnchorMod(le, true);
        anchored.clear();
        anchored.addAll(now);
        return now.size();
    }

    private static void setAnchorMod(LivingEntity le, boolean on) {
        AttributeInstance attr = le.getAttribute(AttributeRegistrar.RANGED_DAMAGE_RESIST.get());
        if (attr == null)
            return;
        AttributeModifier cur = attr.getModifier(ANCHOR_ID);
        if (on && cur == null)
            attr.addTransientModifier(new AttributeModifier(ANCHOR_ID, "ley_anchor", ANCHOR_RANGED_RESIST, AttributeModifier.Operation.ADDITION));
        else if (!on && cur != null)
            attr.removeModifier(ANCHOR_ID);
    }

    @Override
    public void remove(@NotNull RemovalReason reason) {
        for (LivingEntity le : anchored)
            setAnchorMod(le, false);
        anchored.clear();
        super.remove(reason);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl) || !isAlive())
            return;
        TitanMass.tick(this);
        String owner = getOwnerName();
        if (tickCount > 5 && !getPersistentData().getBoolean(KEY_ANNOUNCED) && owner != null && !owner.isEmpty()) {
            getPersistentData().putBoolean(KEY_ANNOUNCED, true);
            com.solegendary.reignofnether.unit.T3ServerEvents.announceFielded(owner,
                net.minecraft.network.chat.Component.translatable("entity.reignofnether.jade_sentinel_unit"));
        }
        if (tickCount % ANCHOR_EVERY_TICKS == 0)
            anchor(sl);
        if (tickCount % 4 == 0 && mirrorActive()) {
            // the plate shows: a short arc of light ahead of it
            Vec3 f = Vec3.directionFromRotation(0, yBodyRot);
            for (int i = -2; i <= 2; i++) {
                Vec3 d = f.yRot((float) (i * 0.4)).scale(getBbWidth() * 0.6);
                sl.sendParticles(ParticleTypes.END_ROD, getX() + d.x, getY() + getBbHeight() * 0.5, getZ() + d.z, 1, 0.05, 0.4, 0.05, 0);
            }
        }
    }
}
