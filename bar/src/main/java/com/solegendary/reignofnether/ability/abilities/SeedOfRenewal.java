package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.AbstractTotem;
import com.solegendary.reignofnether.unit.units.verdant.RenewalSaplingUnit;
import com.solegendary.reignofnether.util.MyMath;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Verdant essence caster (the Thornweaver; design/essence_casters.md, lovish approved draft 2 on Oct 29, Revision 1):
 * <b>Seed of Renewal</b>, the mid spell. Aim a spot within {@link #RANGE} blocks: a flowering sapling sprouts there
 * and, once a second for {@link #LIFETIME_TICKS} ticks, heals every friendly unit within {@link #RADIUS} blocks of it
 * by {@link #HEAL_PER_SECOND} HP (10 pulses, 30 HP to a friend that stays: Revision 1 scaled it to the Bloom
 * Priestess, whose Bloom heals 12 in 8 blocks every 20 s). {@link #ESSENCE_COST} essence, {@link #CD_SECONDS} s
 * cooldown; refused (nothing spent, no cooldown) when the caster is short of essence or there is no ground to grow on.
 * Autocast: at a hurt friend's feet.
 * <p>
 * The sapling is a unit of the caster's owner ({@link RenewalSaplingUnit}, 100 HP, Revision 1: "can be targeted"), so
 * the enemy can answer it by cutting it down. It runs its own pulses and withers by itself; it is marked summoned
 * ({@link Essence#markSummoned}) and tagged {@link #TAG}, so its death never drops essence.
 */
public class SeedOfRenewal extends Ability {

    public static final int ESSENCE_COST = 10;
    public static final int CD_SECONDS = 25;
    public static final int RADIUS = 6;
    public static final float HEAL_PER_SECOND = 3f;
    public static final int PULSE_TICKS = 20;
    public static final int LIFETIME_TICKS = 10 * 20;
    /** Not in lovish's draft: how far the caster plants it, a placeholder for him to tune. */
    public static final int RANGE = 8;
    /** How far above or below the aimed ground the sapling (or a Sprout Guardian) may stand: a hillside, not a cliff. */
    public static final int MAX_DY = 4;
    public static final String TAG = "bt_renewal_sapling";
    public static final String MSG_NO_GROUND = "hud.essence.reignofnether.no_ground";

    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused (server thread)

    public SeedOfRenewal() {
        super(UnitAction.SEED_OF_RENEWAL, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, RADIUS, false, true);
        this.autoRule(AutoRule.ALLY_HURT);
        this.autocastEssence = ESSENCE_COST;   // the autocast waits for the essence, so a dry caster never spams refusals
        this.showRangeCircle = true;
        this.showRadiusCircle = true;
    }

    /**
     * Only a friend the sapling can help: never a sapling or a totem (a pulse never heals one), and only where a sapling
     * can grow by its feet (the cast's own standingCell, at the cell AutoCaster aims at), so an ally wading in water is
     * not ordered every second and refused (PR 362 review).
     */
    @Override
    public boolean autocastTargetOk(Unit caster, LivingEntity target) {
        if (target instanceof AbstractTotem || !(target.level() instanceof ServerLevel sl))
            return false;
        BlockPos feet = target.blockPosition();
        return standingCell(sl, feet.getX(), feet.getZ(), feet.getY() + 1) != null;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Seed of Renewal",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/flowering_azalea_side.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.SEED_OF_RENEWAL,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.SEED_OF_RENEWAL),
            null,
            List.of(
                FormattedCharSequence.forward("Seed of Renewal  (" + ESSENCE_COST + " essence, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Plants a sapling (" + (int) RenewalSaplingUnit.HEALTH + " HP) up to " + RANGE + " blocks away: for "
                    + LIFETIME_TICKS / 20 + " s it heals friendly units within " + RADIUS + " blocks for " + (int) HEAL_PER_SECOND
                    + " HP a second.", Style.EMPTY),
                FormattedCharSequence.forward("Where the old fell, the new take root.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (targetEntity != null)
            use(level, unitUsing, targetEntity.getOnPos());
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl) || targetBp == null)
            return;
        BlockPos at = MyMath.getXZRangeLimitedBlockPos(self.blockPosition(), targetBp, RANGE);
        at = new BlockPos(at.getX(), targetBp.getY(), at.getZ());
        if (cast(sl, self, unitUsing.getOwnerName(), at, false) == null)
            return;
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    public static boolean isSapling(Entity e) {
        return e != null && e.getTags().contains(TAG);
    }

    /**
     * The rule: checks the essence, plants the sapling on the ground by {@code at} (the aimed ground block), then
     * spends - so a refusal (short of essence, no ground there, or no sapling could be made) never costs essence.
     * Returns the sapling, or null when refused. Public for the bot and the game test (no cooldown handling here).
     */
    public static LivingEntity cast(ServerLevel sl, LivingEntity caster, String owner, BlockPos at, boolean quiet) {
        if (owner == null || owner.isBlank() || at == null)
            return null;
        if (Essence.get(caster) < ESSENCE_COST) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, Empower.MSG_SHORT);
            return null;
        }
        BlockPos cell = standingCell(sl, at.getX(), at.getZ(), at.getY() + 1);
        if (cell == null) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_NO_GROUND);
            return null;
        }
        LivingEntity sapling = plant(sl, owner, cell);
        if (sapling == null)
            return null;
        if (!Essence.trySpend(caster, ESSENCE_COST)) {   // checked above; never true unless something spent in between
            sapling.discard();
            return null;
        }
        return sapling;
    }

    /**
     * The open cell nearest {@code refY} (within {@link #MAX_DY} up or down) in column (x, z) that something can stand
     * in: air or a replaceable plant, dry, on a sturdy top face. Null when there is none (water, a cliff). Scanned
     * outwards from the clicked height, not read off the heightmap: a canopy, a roof or the terrain over a game-test
     * arena must not lift the spell onto it (PlantThicket.openSpot has the same reason). Shared with Sprout Guardian.
     */
    public static BlockPos standingCell(ServerLevel sl, int x, int z, int refY) {
        for (int i = 0; i <= 2 * MAX_DY; i++) {
            int dy = i % 2 == 1 ? -(i + 1) / 2 : i / 2;   // 0, -1, +1, -2, +2 ...
            BlockPos p = new BlockPos(x, refY + dy, z);
            BlockState here = sl.getBlockState(p);
            if ((!here.isAir() && !here.canBeReplaced()) || !here.getFluidState().isEmpty())
                continue;
            BlockPos below = p.below();
            if (sl.getBlockState(below).isFaceSturdy(sl, below, Direction.UP))
                return p;
        }
        return null;
    }

    /** A sapling for {@code owner} standing in the block {@code cell} (on the ground below it); null if none spawned. */
    static LivingEntity plant(ServerLevel sl, String owner, BlockPos cell) {
        Entity e = UnitServerEvents.spawnMob(EntityRegistrar.RENEWAL_SAPLING_UNIT.get(), sl, cell.below(), owner);
        if (!(e instanceof RenewalSaplingUnit sapling))
            return null;
        Essence.markSummoned(sapling);
        sapling.addTag(TAG);
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, sapling.getX(), sapling.getY() + 0.6, sapling.getZ(), 12, 0.5, 0.4, 0.5, 0.0);
        sl.playSound(null, cell, SoundEvents.AZALEA_PLACE, SoundSource.NEUTRAL, 1.5f, 0.8f);
        return sapling;
    }

    /**
     * One pulse: {@code amount} HP to every friendly unit (the owner's or an ally's, never a foe) within {@link #RADIUS}
     * of the sapling that is not at full health - never a sapling or a totem (it would mend itself as it is cut down).
     * Returns how many it healed. Called by {@link RenewalSaplingUnit}'s tick.
     */
    public static int pulse(ServerLevel sl, Entity sapling, String owner, float amount) {
        int n = 0;
        double r2 = (double) RADIUS * RADIUS;
        for (LivingEntity le : UnitGrid.near(sl, sapling.getX(), sapling.getZ(), RADIUS, scratch)) {
            if (!le.isAlive() || le.level() != sl || !(le instanceof Unit u) || le instanceof AbstractTotem || le.distanceToSqr(sapling) > r2)
                continue;
            String o = u.getOwnerName();
            if (o == null || !AlliancesServerEvents.isAlliedOrOwned(owner, o) || le.getHealth() >= le.getMaxHealth())
                continue;
            le.heal(amount);
            n++;
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, le.getX(), le.getY() + le.getBbHeight() * 0.8, le.getZ(), 2, 0.3, 0.3, 0.3, 0.01);
        }
        if (n > 0)
            sl.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, sapling.getX(), sapling.getY() + 1.2, sapling.getZ(), 3, 0.6, 0.2, 0.6, 0.0);
        return n;
    }
}
