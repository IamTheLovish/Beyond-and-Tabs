package com.solegendary.reignofnether.unit.units.villagers;

import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.T3ServerEvents;
import com.solegendary.reignofnether.unit.TitanMass;
import com.solegendary.reignofnether.unit.UnitDamage;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Sunforged Kingdom T3 experimental - the <b>Sun Colossus</b>: the <b>Anchor with reach</b> (claude/t3-roles.md,
 * section 4). A gilded paladin-statue on the Iron Golem's body and brain, drawn at {@link #SCALE}x. The strategy it
 * opens: hold a lane, push a front - "nothing goes through that lane". The enemy's moment: the lane is closed; go round
 * it or bring artillery.
 * <ul>
 *   <li><b>Marches with the army</b> ({@link #movementSpeed}): the pace of the heavy line it anchors, never behind it.</li>
 *   <li><b>Solar Lance</b> ({@link com.solegendary.reignofnether.ability.abilities.SolarLance}): the sweeping sunbeam -
 *   after a visible charge a 20-block line hits everything in it. The reach that makes a lane its own.</li>
 *   <li><b>Dawn Aegis</b> (passive, {@link T3ServerEvents}): the shield of light - friendly and allied units within
 *   {@link #AEGIS_RADIUS} blocks take x{@link #AEGIS_TAKEN_MULT} damage. Never itself; never stacks with a second
 *   Colossus, and a unit already under an Oriflamme keeps the stronger of the two (no buff piles).</li>
 *   <li><b>Cleave</b>: half of every blow lands on the enemies within {@link #CLEAVE_RADIUS} of the target.</li>
 *   <li><b>Mass</b> ({@link TitanMass}): never pushed, shoves small bodies aside.</li>
 * </ul>
 * Countered by: flanking past it (it holds one lane, not a map), artillery (outranges the lance and chips the army
 * under the aegis), kiting ranged units it can't catch, and anti-air it lacks. It beats mass T1/T2 charges and clumps.
 * Arrival announced to every player. Placeholder look: the iron golem model, scaled.
 */
public class SunColossusUnit extends IronGolemUnit {

    final static public float attackDamage = 22.0f;
    final static public float attacksPerSecond = 0.35f;
    final static public float maxHealth = 1000.0f;   // 1000 -> 1200 (additive focus fire made its duel vs the T2 mix hopeless) -> 1000 (balance-pass: at 1200 it beat the equal-metal T2 mix 2-0 once, at 1100 it drew 1-1 twice - T3 rule (c) says it loses)
    final static public float movementSpeed = 0.24f;  // 0.16 -> 0.24 (t3-roles): an anchor marches with its army (line infantry 0.25), it never trails it
    final static public float attackRange = 4;
    final static public float aggroRange = 12;
    public static final float SCALE = 2.2f;
    // the cleave (t3-splash): a single-target 7.7-DPS body facing a seven-unit counter mix killed one or two of them
    // and lost 7-8x in the duel sim however much HP it had; half of each blow now lands on the foes round the target,
    // as the World Tree Walker's slam does
    public static final float CLEAVE_RADIUS = 2.5f;
    public static final float CLEAVE_SPLASH = 0.5f;
    /** Dawn Aegis: the shield of light on the army round it. */
    public static final float AEGIS_RADIUS = 7f;
    public static final float AEGIS_TAKEN_MULT = 0.8f;
    static final int AEGIS_FX_TICKS = 40;
    static final String KEY_ANNOUNCED = "bt_t3_announced";

    public SunColossusUnit(EntityType<? extends IronGolem> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.SUN_COLOSSUS; }

    /** IronGolemUnit rebuilds its abilities from the shared static set here; add the Colossus' own on top. */
    @Override
    public void updateAbilityButtons() {
        super.updateAbilityButtons();
        if (getAbilities() == null)
            return;
        // a game ender does not plant a shield and halve its speed (the Paladin's Brace rides on the shared set)
        getAbilities().remove(com.solegendary.reignofnether.ability.abilities.BraceAbility.INSTANCE);
        for (var a : getAbilities().get())
            if (a instanceof com.solegendary.reignofnether.ability.abilities.SolarLance)
                return;
        getAbilities().add(new com.solegendary.reignofnether.ability.abilities.SolarLance());
    }

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
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0.2)
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

    /** The cleave: a landed blow also hits every enemy within {@link #CLEAVE_RADIUS} of the target for half damage. */
    @Override
    public boolean doHurtTarget(@NotNull Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && level() instanceof ServerLevel sl
                && UnitDamage.meleeSplash(sl, this, target, CLEAVE_RADIUS, getUnitAttackDamage() * CLEAVE_SPLASH) > 0)
            sl.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 1, target.getZ(), 3, 1.0, 0.2, 1.0, 0);
        return hit;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl) || !isAlive())
            return;
        if (tickCount > 5 && !getPersistentData().getBoolean(KEY_ANNOUNCED)
                && getOwnerName() != null && !getOwnerName().isEmpty()) {
            getPersistentData().putBoolean(KEY_ANNOUNCED, true);
            com.solegendary.reignofnether.player.PlayerServerEvents.sendMessageToAllPlayers(
                "server.reignofnether.t3_arrived", true, getOwnerName(),
                net.minecraft.network.chat.Component.translatable("entity.reignofnether.sun_colossus_unit"));
        }
        T3ServerEvents.trackColossus(this);
        if ((tickCount + getId()) % AEGIS_FX_TICKS == 0)
            aegisFx(sl);
    }

    /** The aegis' edge, drawn now and then in gold so both sides can see where the shield of light reaches. */
    void aegisFx(ServerLevel sl) {
        for (int i = 0; i < 12; i++) {
            double a = i * (Math.PI * 2 / 12) + (tickCount % 360) * 0.02;
            sl.sendParticles(ParticleTypes.WAX_ON, getX() + Math.cos(a) * AEGIS_RADIUS, getY() + 0.3, getZ() + Math.sin(a) * AEGIS_RADIUS,
                1, 0.1, 0.1, 0.1, 0.0);
        }
    }
}
