package com.solegendary.reignofnether.unit.units.verdant;

import com.solegendary.reignofnether.ability.abilities.AwakenThicket;
import com.solegendary.reignofnether.ability.abilities.GrowGrove;
import com.solegendary.reignofnether.ability.abilities.Rootquake;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.TitanMass;
import com.solegendary.reignofnether.unit.UnitDamage;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.UnitUnstick;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Verdant Court T3 experimental - the <b>World Tree Walker</b>: the <b>Summoner / zoner</b> (claude/t3-roles.md,
 * section 4). A whole elder tree uprooted and walking, on the Sentinel Treant's golem body drawn {@link #SCALE}x with
 * a bark, moss and leaf-canopy skin (WorldTreeWalkerRenderer). The strategy it opens: make the ground hostile and
 * bring a second army - "the ground itself is fighting me". It may stay slow: its army comes to it, and it is never a
 * lone stump (the Oct 19 "reads as a stump" note):
 * <ul>
 * <li><b>Grow Grove</b> ({@link GrowGrove}): {@link #TREANTS_PER_GROVE} Sentinel Treants grow out of the ground round
 * it and fight for {@link #TREANT_LIFETIME_TICKS} ticks, at most {@link #TREANT_CAP} at once; the bloom heals friends
 * in its shade by {@link #BLOOM_HEAL}. The grove withers with the walker ({@link #witherGrove}).</li>
 * <li><b>Rootquake</b> ({@link Rootquake}): a telegraphed ring that roots and hurts every enemy within 8 blocks.</li>
 * <li>its <b>shade</b> heals friendly Verdant units within {@link #SHADE_RADIUS} blocks by {@link #SHADE_HEAL} HP a
 * second (never itself, never enemies);</li>
 * <li>its fists <b>slam</b>: a hit also jars enemies round the target for {@link #SLAM_SPLASH} of the damage;</li>
 * <li>it sheds leaves and blossom as it walks (cosmetic for now - see {@link #trail});</li>
 * <li><b>mass</b> ({@link TitanMass}): never pushed, shoves small bodies aside.</li>
 * </ul>
 * Countered by: burst and fire before the grove grows (<b>fire does double damage</b> - a burning tree), artillery
 * (the walker is the grove's root: kill it and the treants sink back), and an army that will not fight on its ground.
 * It beats short-range armies that walk into its roots and its grove. Rootquake is telegraphed, no instant win.
 * Arrival announced to all.
 */
public class WorldTreeWalkerUnit extends SentinelTreantUnit {

    final static public float attackDamage = 12.0f;
    final static public float attacksPerSecond = 0.3f;
    final static public float maxHealth = 1400.0f;
    final static public float movementSpeed = 0.18f;   // 0.13 -> 0.15 (lovish Oct 20: "way too slow") -> 0.18 (t3-roles): a summoner may stay back, but not crawl - its grove walks at 0.20
    final static public float attackRange = 4;
    final static public float aggroRange = 12;
    final static public float rangedDamageResist = 0.3f;
    public static final float SCALE = 2.5f;

    public static final float SHADE_RADIUS = 6f;
    public static final float SHADE_HEAL = 2f;          // per second, per unit in the shade
    public static final float FIRE_MULTIPLIER = 2f;     // the counter: a burning tree
    public static final float SLAM_RADIUS = 2.5f;
    public static final float SLAM_SPLASH = 0.5f;
    /** The grove: how many treants a cast grows, how many stand at once, how long each lives, the bloom's heal. */
    public static final int TREANTS_PER_GROVE = 2;
    public static final int TREANT_CAP = 4;
    public static final int TREANT_LIFETIME_TICKS = 45 * 20;
    public static final float GROVE_RADIUS = 4f;
    public static final float BLOOM_HEAL = 12f;
    static final int TRAIL_EVERY_TICKS = 10;
    static final String KEY_ANNOUNCED = "bt_t3_announced";

    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused (server thread)
    private double trailX, trailZ;
    /** The live treants this walker grew (server only; pruned on each count, dismissed when the walker goes). */
    private final List<LivingEntity> treants = new ArrayList<>(TREANT_CAP);

    public WorldTreeWalkerUnit(EntityType<? extends IronGolem> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.WORLD_TREE_WALKER; }

    /**
     * IronGolemUnit rebuilds its abilities from the shared static set here; add Grow Grove and Rootquake on top - one
     * of each per walker, kept across refreshes so their cooldowns survive them (the Gorehorn Rhino's rule).
     */
    @Override
    public void updateAbilityButtons() {
        super.updateAbilityButtons();
        if (getAbilities() == null)
            return;
        if (grove == null)
            grove = new GrowGrove();
        if (quake == null)
            quake = new Rootquake();
        boolean hasGrove = false, hasQuake = false;
        for (var a : getAbilities().get()) {
            if (a == grove)
                hasGrove = true;
            if (a == quake)
                hasQuake = true;
        }
        if (!hasGrove)
            getAbilities().add(grove);
        if (!hasQuake)
            getAbilities().add(quake);
    }
    private GrowGrove grove;
    private Rootquake quake;

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.ARMOR, 0)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), rangedDamageResist)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.BUILDING_DAMAGE_BONUS.get(), 1.0);
    }

    // mass: never pushed, shoves small bodies aside (TitanMass)
    @Override
    public boolean isPushable() {
        return TitanMass.isPushable();
    }

    @Override
    protected void doPush(@NotNull Entity other) {
        TitanMass.doPush(this, other);
    }

    /** The counter: every kind of fire (burning, fire blocks, lava, fireballs) hits it twice as hard. */
    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_FIRE))
            amount *= FIRE_MULTIPLIER;
        return super.hurt(source, amount);
    }

    /** The slam: a landed blow also jars the enemies standing round the target. */
    @Override
    public boolean doHurtTarget(@NotNull Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && level() instanceof ServerLevel sl) {
            // explicit amount: RoN rewrites a unit's MOB_ATTACK damage to its full hit, which made every splash
            // target take the whole 12 instead of SLAM_SPLASH of it (balance.md, t3-splash)
            UnitDamage.meleeSplash(sl, this, target, SLAM_RADIUS, getUnitAttackDamage() * SLAM_SPLASH);
            BlockParticleOption soil = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState());
            sl.sendParticles(soil, target.getX(), target.getY() + 0.1, target.getZ(), 16, 0.9, 0.1, 0.9, 0.1);
        }
        return hit;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl) || !isAlive())
            return;
        TitanMass.tick(this);   // two standing titans on one spot step apart
        String owner = getOwnerName();
        if (tickCount > 5 && !getPersistentData().getBoolean(KEY_ANNOUNCED) && owner != null && !owner.isEmpty()) {
            getPersistentData().putBoolean(KEY_ANNOUNCED, true);
            com.solegendary.reignofnether.unit.T3ServerEvents.announceFielded(owner,
                net.minecraft.network.chat.Component.translatable("entity.reignofnether.world_tree_walker_unit"));
        }
        // stagger the once-a-second shade by entity id so a handful of walkers don't all query on one tick
        if ((tickCount + getId()) % 20 == 0)
            shade(sl);
        if (tickCount % TRAIL_EVERY_TICKS == 0)
            trail(sl);
    }

    /** Live treants of this walker's grove (pruned of the dead and the sunk). Public for the game test. */
    public int liveTreants() {
        treants.removeIf(t -> t == null || !t.isAlive() || t.isRemoved());
        return treants.size();
    }

    /**
     * One bloom: up to {@link #TREANTS_PER_GROVE} treants grow round the walker, never more than {@link #TREANT_CAP}
     * alive, for {@link #TREANT_LIFETIME_TICKS}; friends within {@link #SHADE_RADIUS} are healed {@link #BLOOM_HEAL}.
     * Returns how many treants grew. Public for the game test.
     */
    public int growGrove(ServerLevel sl) {
        String owner = getOwnerName();
        if (owner == null || owner.isEmpty())
            return 0;
        int room = Math.min(TREANTS_PER_GROVE, TREANT_CAP - liveTreants());
        int grown = 0;
        var treantType = EntityRegistrar.SENTINEL_TREANT_UNIT.get();
        // round the trunk, a different pair of spots each bloom; a spot must be standable ground for a treant's body
        // (never a roof, a wall or a trunk - UnitUnstick's search, a column or two around the spot), else the next angle
        for (int k = 0; k < TREANT_CAP * 2 && grown < room; k++) {
            double a = (treants.size() + k) * (Math.PI * 2 / TREANT_CAP) + (tickCount % 7) * 0.3;
            BlockPos at = BlockPos.containing(getX() + Math.cos(a) * GROVE_RADIUS, getY() + 0.5, getZ() + Math.sin(a) * GROVE_RADIUS);
            BlockPos cell = UnitUnstick.findStandPos(sl, at, 2, 2, 2, treantType.getWidth(), treantType.getHeight());
            if (cell == null)
                continue;
            LivingEntity treant = AwakenThicket.awakenOn(sl, owner, cell.below(), TREANT_LIFETIME_TICKS);
            if (treant == null)
                continue;
            treants.add(treant);
            grown++;
        }
        if (grown > 0) {
            bloom(sl, owner);
            sl.playSound(null, blockPosition(), SoundEvents.AZALEA_PLACE, SoundSource.NEUTRAL, 2f, 0.6f);
        }
        return grown;
    }

    /** The bloom's heal: every hurt friend (any faction) within the shade is healed {@link #BLOOM_HEAL}; returns how many. */
    int bloom(ServerLevel sl, String owner) {
        int n = 0;
        double r2 = SHADE_RADIUS * SHADE_RADIUS;
        for (LivingEntity le : UnitGrid.near(sl, getX(), getZ(), SHADE_RADIUS, scratch)) {
            if (le == this || !le.isAlive() || le.getHealth() >= le.getMaxHealth() || !(le instanceof Unit u))
                continue;
            if (le.distanceToSqr(this) > r2)
                continue;
            String o = u.getOwnerName();
            if (o == null || (!owner.equals(o) && !AlliancesServerEvents.isAllied(owner, o)))
                continue;
            le.heal(BLOOM_HEAL);
            n++;
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, le.getX(), le.getY() + le.getBbHeight() * 0.8, le.getZ(), 4, 0.3, 0.3, 0.3, 0.0);
        }
        scratch.clear();
        sl.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, getX(), getY() + getBbHeight() * 0.8, getZ(), 30, SHADE_RADIUS * 0.6, 1.0, SHADE_RADIUS * 0.6, 0.0);
        return n;
    }

    /** The grove goes with the walker: every treant it grew sinks back (no death event, no wreck) when it dies or is removed. */
    void witherGrove() {
        for (LivingEntity t : treants)
            if (t != null && t.isAlive() && !t.isRemoved())
                AwakenThicket.dismiss(t);
        treants.clear();
    }

    @Override
    public void die(@NotNull DamageSource source) {
        super.die(source);
        if (!level().isClientSide())
            witherGrove();
    }

    /**
     * Killed or discarded (a reset, /kill): the grove goes too. Not on a chunk unload or a save (the entity object is
     * only being parked): discarding other entities from inside the unload pass is unsafe, and AwakenThicket already
     * ends every awakened treant that reloads from disk (their timers are not saved), so the grove ends either way.
     */
    @Override
    public void remove(@NotNull RemovalReason reason) {
        if (!level().isClientSide() && reason.shouldDestroy())
            witherGrove();
        super.remove(reason);
    }

    /**
     * Heals every hurt friendly Verdant unit within {@link #SHADE_RADIUS} by {@link #SHADE_HEAL}; returns how many it
     * healed. Called once a second. Public for the game test.
     */
    public int shade(ServerLevel sl) {
        String owner = getOwnerName();
        if (owner == null || owner.isEmpty())
            return 0;
        double r2 = SHADE_RADIUS * SHADE_RADIUS;
        int n = 0;
        for (LivingEntity le : UnitGrid.near(sl, getX(), getZ(), SHADE_RADIUS, scratch)) {
            if (le == this || !le.isAlive() || le.getHealth() >= le.getMaxHealth() || !(le instanceof Unit u))
                continue;
            if (le.distanceToSqr(this) > r2)
                continue;
            String o = u.getOwnerName();
            if (o == null || (!owner.equals(o) && !AlliancesServerEvents.isAllied(owner, o)))
                continue;
            if (!Factions.VERDANT_COURT.equals(Factions.getFaction(u)))
                continue;   // the shade is the Court's own: an allied Kingdom's soldier gets nothing from it
            le.heal(SHADE_HEAL);
            n++;
            if ((tickCount & 1) == 0)   // a leaf now and then on the healed, not a particle storm
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, le.getX(), le.getY() + le.getBbHeight() * 0.8, le.getZ(), 1, 0.25, 0.3, 0.25, 0.0);
        }
        return n;
    }

    /**
     * The foliage it sheds as it walks: azalea leaves and blossom at its feet, only while it is actually moving.
     * TODO(verdant living terrain): when the Thicket block lands, plant a short-lived Thicket patch here instead of
     * particles alone (rate-limited, on the walker's own footprint, never inside a building).
     */
    void trail(ServerLevel sl) {
        double dx = getX() - trailX, dz = getZ() - trailZ;
        if (dx * dx + dz * dz < 0.25)
            return;
        trailX = getX();
        trailZ = getZ();
        BlockParticleOption leaves = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState());
        double spread = getBbWidth() * 0.4;
        sl.sendParticles(leaves, getX(), getY() + 0.2, getZ(), 6, spread, 0.1, spread, 0.05);
        sl.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, getX(), getY() + getBbHeight() * 0.85, getZ(), 2, spread, 0.3, spread, 0.0);
    }

    static boolean isFoe(String owner, LivingEntity le) {
        if (!(le instanceof Unit u))
            return false;
        String o = u.getOwnerName();
        return o != null && !o.isEmpty() && !owner.equals(o) && !AlliancesServerEvents.isAllied(owner, o);
    }
}
