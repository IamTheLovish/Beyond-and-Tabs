package com.solegendary.reignofnether.unit.units.monsters;

import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.resources.WreckServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Gravebound T2 beast - the <b>Ossuary Behemoth</b> (design/big_units.md 1.3): a hog-shaped heap of the battlefield's
 * bones on the zoglin's body, drawn x{@link #BASE_SCALE} and growing. Its signature is <b>Grave Feast</b>: once a second
 * it eats the nearest wreck within {@link #FEAST_RANGE} blocks - any faction's, wrecks belong to nobody - and gains a
 * <b>Bone Stack</b> (at most {@link #MAX_STACKS}): +{@link #HP_PER_STACK} max HP (and that much healed),
 * +{@link #DAMAGE_PER_STACK} damage and a bigger body. The eaten wreck's metal is gone, so it also denies the enemy's
 * reclaim. "Every wreck is an army": left alone in a long war it becomes a 530 HP, 16-damage brute; killed early, or starved by
 * workers who reclaim first, it is a below-par T2 body.
 * <p>
 * Stacks are synced (the renderer scales with them, the hitbox grows) and saved; the stat bonuses are attribute
 * modifiers with fixed ids, re-applied idempotently. Bone, not flesh: daylight slows it instead of burning it. Cost at
 * 8v8: one walk over the wreck list (at most 150) per behemoth per second, nothing when it is full.
 */
public class OssuaryBehemothUnit extends ZoglinUnit {

    final static public float attackDamage = 7.0f;
    final static public float attacksPerSecond = 0.7f;
    final static public float maxHealth = 260.0f;   // duel pass: T2 bodies are 100-380 HP
    final static public float movementSpeed = 0.22f;
    final static public float attackRange = 2.5f;
    final static public float aggroRange = 12;

    public static final float BASE_SCALE = 1.5f;
    public static final float SCALE_PER_STACK = 0.1f;
    public static final int MAX_STACKS = 6;
    public static final float HP_PER_STACK = 45f;
    public static final float DAMAGE_PER_STACK = 1.5f;
    public static final double FEAST_RANGE = 6.0;
    static final int FEAST_EVERY_TICKS = 20;

    private static final UUID HP_ID = UUID.fromString("6b1f3c5e-0a2d-4f7e-9b13-2c4d5e6f7a81");
    private static final UUID DMG_ID = UUID.fromString("6b1f3c5e-0a2d-4f7e-9b13-2c4d5e6f7a82");
    private static final UUID RON_DMG_ID = UUID.fromString("6b1f3c5e-0a2d-4f7e-9b13-2c4d5e6f7a83");
    static final String KEY_STACKS = "bt_bone_stacks";

    public static final EntityDataAccessor<Integer> stacksDataAccessor =
        SynchedEntityData.defineId(OssuaryBehemothUnit.class, EntityDataSerializers.INT);

    public OssuaryBehemothUnit(EntityType<? extends Zoglin> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(stacksDataAccessor, 0);
    }

    public int getStacks() {
        return this.entityData.get(stacksDataAccessor);
    }

    /** The render scale: grows a tenth of the zoglin per stack. */
    public float getScale() {
        return BASE_SCALE + SCALE_PER_STACK * getStacks();
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (stacksDataAccessor.equals(key))
            refreshDimensions();
    }

    // the registered size is the zoglin at BASE_SCALE; every stack grows the hitbox with the body
    @Override
    public @NotNull EntityDimensions getDimensions(@NotNull Pose pose) {
        return super.getDimensions(pose).scale(getScale() / BASE_SCALE);
    }

    @Nullable
    @Override
    public ResourceCost getCost() { return ResourceCosts.OSSUARY_BEHEMOTH; }

    public static AttributeSupplier.Builder createAttributes() {
        return Unit.createDefaultAttributes()
                .add(Attributes.ATTACK_DAMAGE, attackDamage)
                .add(Attributes.MOVEMENT_SPEED, movementSpeed)
                .add(Attributes.MAX_HEALTH, maxHealth)
                .add(Attributes.FOLLOW_RANGE, Unit.getFollowRange())
                .add(Attributes.ARMOR, 0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D)
                .add(AttributeRegistrar.ATTACK_DAMAGE.get(), attackDamage)
                .add(AttributeRegistrar.ATTACKS_PER_SECOND.get(), attacksPerSecond)
                .add(AttributeRegistrar.ATTACK_RANGE.get(), attackRange)
                .add(AttributeRegistrar.AGGRO_RANGE.get(), aggroRange)
                .add(AttributeRegistrar.SIGHT_RANGE.get(), Unit.DEFAULT_SIGHT_RANGE)
                .add(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(), 0)
                .add(AttributeRegistrar.BUILDING_DAMAGE_BONUS.get(), 0.5);
    }

    // bone, not flesh: daylight makes it sluggish (like the Warden) instead of setting it alight like a zoglin
    @Override
    protected boolean isSunBurnTick() {
        return false;
    }

    @Override
    public SunlightEffect getSunlightEffect() {
        return SunlightEffect.SLOWNESS_I;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel sl && isAlive() && tickCount % FEAST_EVERY_TICKS == 0 && getStacks() < MAX_STACKS)
            feast(sl);
    }

    /**
     * Grave Feast: eats the nearest wreck within FEAST_RANGE and gains a stack. Returns the eaten wreck, or null.
     * Public for the game test.
     */
    @Nullable
    public Entity feast(ServerLevel sl) {
        if (getStacks() >= MAX_STACKS)
            return null;
        Entity best = null;
        double bestD = FEAST_RANGE * FEAST_RANGE;
        var wrecks = WreckServerEvents.getWrecks();
        for (int i = 0, n = wrecks.size(); i < n; i++) {
            Entity w = wrecks.get(i);
            if (w.isRemoved() || w.level() != sl)
                continue;
            double d = w.distanceToSqr(this);
            if (d < bestD) {
                bestD = d;
                best = w;
            }
        }
        if (best == null)
            return null;
        sl.sendParticles(ParticleTypes.SOUL, best.getX(), best.getY() + 0.3, best.getZ(), 10, 0.3, 0.2, 0.3, 0.02);
        WreckServerEvents.drain(sl, best, WreckServerEvents.metalOf(best) + 1);   // eaten whole: nothing left to reclaim
        setStacks(getStacks() + 1);
        heal(HP_PER_STACK);
        com.solegendary.reignofnether.sounds.Sfx.BEAST_DEVOUR.play(sl, position());   // bones ground down
        sl.sendParticles(ParticleTypes.ASH, getX(), getY() + getBbHeight() * 0.8, getZ(), 20, 0.6, 0.4, 0.6, 0.02);
        return best;
    }

    /** Sets the stack count and re-applies the stat bonuses (idempotent: fixed modifier ids). */
    public void setStacks(int stacks) {
        int s = Math.max(0, Math.min(MAX_STACKS, stacks));
        this.entityData.set(stacksDataAccessor, s);
        applyBonus(Attributes.MAX_HEALTH, HP_ID, s * HP_PER_STACK);
        applyBonus(Attributes.ATTACK_DAMAGE, DMG_ID, s * DAMAGE_PER_STACK);
        applyBonus(AttributeRegistrar.ATTACK_DAMAGE.get(), RON_DMG_ID, s * DAMAGE_PER_STACK);
    }

    private void applyBonus(Attribute attribute, UUID id, double amount) {
        AttributeInstance inst = getAttribute(attribute);
        if (inst == null)
            return;
        if (inst.getModifier(id) != null)
            inst.removeModifier(id);
        if (amount > 0)
            inst.addPermanentModifier(new AttributeModifier(id, "Bone Stacks", amount, AttributeModifier.Operation.ADDITION));
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt(KEY_STACKS, getStacks());
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(KEY_STACKS))
            setStacks(tag.getInt(KEY_STACKS));
    }
}
