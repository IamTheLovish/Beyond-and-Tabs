package com.solegendary.reignofnether.unit.units.monsters;

import com.solegendary.reignofnether.ability.abilities.BileLob;
import com.solegendary.reignofnether.ability.abilities.CarrionBurst;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.TitanMass;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Gravebound T3 experimental, the second of the Stronghold (slot T): the <b>Plague Abomination</b> (second-t3-design.md,
 * slice T3b-3). A stitched bone-and-flesh giant: slow, tough, melee. The Bone Dragon is the Gravebound's air striker;
 * the Abomination is the ground attrition and area-denial answer - it walks into a clump and rots it.
 * <ul>
 *   <li><b>Rot Aura</b> (passive): every {@link #ROT_EVERY_TICKS} ticks each enemy unit within {@link #ROT_RADIUS} gets
 *   one more wither stack (at most {@link #ROT_MAX_STACKS}; the stack is the wither effect's amplifier). Buildings are
 *   unaffected (only units are touched).</li>
 *   <li><b>Bile Lob</b> ({@link BileLob}, tap, 20 s): a telegraphed acid pool that burns whoever stands in it.</li>
 *   <li><b>Carrion Burst</b> ({@link CarrionBurst}, tap, 40 s): 20% of its own HP raises skeletons round it.</li>
 *   <li><b>Mass</b> ({@link TitanMass}): never pushed. Arrival announced to every player.</li>
 * </ul>
 * Countered by (never by a crippled stat): kiting with ranged units (it is slow), fire, cleansing, bursting it before
 * the stacks build. It has no anti-air at all. Loses to a focused equal-cost T2 counter army.
 * Placeholder look: the zoglin body at {@link #SCALE}x (Lane B draws the stitched body later, slice T3b-10).
 */
public class PlagueAbominationUnit extends ZoglinUnit {

    final static public float attackDamage = 14.0f;   // duel: at 20 it beat the equal-metal T2 mix on time twice (rule c: a T3 loses to its counter mix)
    final static public float attacksPerSecond = 0.5f;
    final static public float maxHealth = 1300.0f;   // the design's number; 900/950/1000/1050/1100/1150/1250/1400 are taken (no two T3s share an HP total)
    final static public float movementSpeed = 0.20f;  // slow, like the other bulk/attrition T3s
    final static public float attackRange = 3;
    final static public float aggroRange = 12;
    /** The body: the zoglin grown this many times (placeholder; the entity's box is scaled the same). */
    public static final float SCALE = 2.6f;

    /** Rot Aura. */
    public static final double ROT_RADIUS = 6.0;
    public static final int ROT_EVERY_TICKS = 40;
    public static final int ROT_MAX_STACKS = 3;
    /** Long enough to bridge two pulses plus a pulse of slack. */
    public static final int ROT_EFFECT_TICKS = 100;

    /** The production item's name (EntityRegistrar.getEntityType). */
    public static final String ITEM_NAME = "Plague Abomination";
    static final String KEY_ANNOUNCED = "bt_t3_announced";
    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused (server thread)

    public PlagueAbominationUnit(EntityType<? extends Zoglin> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * ZoglinUnit rebuilds its list from the shared static set here (also from its own constructor, before this class's
     * fields exist); add this unit's own abilities on top, one instance each so cooldowns survive refreshes.
     */
    @Override
    public void updateAbilityButtons() {
        super.updateAbilityButtons();
        if (getAbilities() == null)
            return;
        if (bileLob == null)
            bileLob = new BileLob();
        if (carrionBurst == null)
            carrionBurst = new CarrionBurst();
        for (var a : getAbilities().get())
            if (a == bileLob)
                return;
        getAbilities().add(bileLob);
        getAbilities().add(carrionBurst);
    }
    private BileLob bileLob;
    private CarrionBurst carrionBurst;

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.GRAVE_T3B; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, 2)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0);
    }

    // bone and flesh, but a giant: daylight slows it (like the Behemoth) instead of setting it alight
    @Override
    protected boolean isSunBurnTick() { return false; }

    @Override
    public SunlightEffect getSunlightEffect() { return SunlightEffect.SLOWNESS_I; }

    // mass: never pushed, shoves small bodies aside (TitanMass)
    @Override
    public boolean isPushable() { return TitanMass.isPushable(); }

    @Override
    protected void doPush(@NotNull Entity other) { TitanMass.doPush(this, other); }

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
                net.minecraft.network.chat.Component.translatable("entity.reignofnether.plague_abomination_unit"));
        }
        if (tickCount % ROT_EVERY_TICKS == 0)
            rot(sl);
    }

    /**
     * Rot Aura pulse: one more wither stack (to {@link #ROT_MAX_STACKS}) on every enemy unit within {@link #ROT_RADIUS}.
     * Returns how many were touched. Public for the game test.
     */
    public int rot(ServerLevel sl) {
        String owner = getOwnerName();
        if (owner == null || owner.isEmpty())
            return 0;
        int touched = 0;
        scratch.clear();
        for (LivingEntity le : UnitGrid.near(sl, getX(), getZ(), ROT_RADIUS + 1, scratch)) {
            if (le == this || !le.isAlive() || !(le instanceof Unit u) || le.level() != sl)
                continue;
            String other = u.getOwnerName();
            if (other == null || other.isEmpty() || owner.equals(other) || AlliancesServerEvents.isAllied(owner, other))
                continue;
            double dx = le.getX() - getX(), dz = le.getZ() - getZ();
            if (dx * dx + dz * dz > ROT_RADIUS * ROT_RADIUS || Math.abs(le.getY() - getY()) > 4)
                continue;
            MobEffectInstance cur = le.getEffect(MobEffects.WITHER);
            int amp = cur == null ? 0 : Math.min(ROT_MAX_STACKS - 1, cur.getAmplifier() + 1);
            le.addEffect(new MobEffectInstance(MobEffects.WITHER, ROT_EFFECT_TICKS, amp, false, true));   // false at the cap (same stack, same time): still in the aura
            touched++;
        }
        scratch.clear();
        sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 0.3, getZ(), 12, ROT_RADIUS * 0.4, 0.2, ROT_RADIUS * 0.4, 0.01);
        return touched;
    }
}
