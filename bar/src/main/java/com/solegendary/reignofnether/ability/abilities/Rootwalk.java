package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.CombatEngineers;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Verdant Rootwarden (combat engineer, design/combat_engineers.md): <b>Rootwalk</b>. Aim at an own building whose
 * nearest block is within {@link #RANGE}: the Rootwarden sinks into the roots for {@link #SINK_TICKS} ticks and rises
 * beside that building, on the side facing where it came from. Hurt while sinking - or ordered to move - and the walk
 * fails with half the cooldown spent. {@link #CD_SECONDS} s cooldown, free.
 * <p>
 * The Court is the mobility faction and its terrain lives (the Seedshaper owns the thickets), so its engineer's trick is
 * moving its repair power: a raided outpost far behind the army gets a mender in two seconds, or a cornered engineer
 * gets out. Counter: the sink is telegraphed with roots at both ends - hit the Rootwarden to cancel it, or be waiting
 * where it rises. Own buildings only (an ally's base is not its root network). Ticked by CombatEngineerServerEvents.
 */
public class Rootwalk extends Ability {

    public static final int CD_SECONDS = 40;
    public static final float RANGE = 40f;
    public static final int SINK_TICKS = 40;
    /** Moving more than this from where it began sinking cancels the walk (a new order). */
    static final double LEASH = 1.5;
    static final int FX_EVERY_TICKS = 5;
    public static final String MSG_BAD_TARGET = "hud.reignofnether.rootwalk_bad_target";
    public static final String MSG_TOO_FAR = "hud.reignofnether.engineer_too_far";
    public static final String MSG_INTERRUPTED = "hud.reignofnether.rootwalk_interrupted";

    /** One Rootwarden sinking toward a building. Public for the game test. */
    public static final class Walk {
        public final LivingEntity warden;
        public final String owner;
        public final BuildingPlacement building;
        final Rootwalk ability;
        final Vec3 start;
        final long arriveAt;
        /** where the roots show at the far end (the arrival cell is worked out again on arrival) */
        final BlockPos telegraph;
        float lastHealth;

        Walk(LivingEntity warden, String owner, BuildingPlacement building, Rootwalk ability, long arriveAt, BlockPos telegraph) {
            this.warden = warden;
            this.owner = owner;
            this.building = building;
            this.ability = ability;
            this.start = warden.position();
            this.arriveAt = arriveAt;
            this.telegraph = telegraph;
            this.lastHealth = warden.getHealth();
        }
    }

    static final List<Walk> ACTIVE = new ArrayList<>();

    public Rootwalk() {
        super(UnitAction.ROOTWALK, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, false, true);
        this.showRangeCircle = false;   // a 40-block circle says nothing useful on screen
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Rootwalk",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/rooted_dirt.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.ROOTWALK,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.ROOTWALK),
            null,
            List.of(
                FormattedCharSequence.forward("Rootwalk  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Aim at one of your buildings within " + (int) RANGE + " blocks: the Rootwarden sinks for "
                    + SINK_TICKS / 20 + " s and rises beside it.", Style.EMPTY),
                FormattedCharSequence.forward("Hurt or moved while sinking, it fails (half the cooldown).", Style.EMPTY),
                FormattedCharSequence.forward("Every root in the Court's soil is a road.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        use(level, unitUsing, targetEntity.getOnPos());
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        if (begin(sl, self, unitUsing.getOwnerName(), BuildingUtils.findBuilding(false, targetBp), this, false) != null) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /**
     * Starts the sink toward {@code b} if it is an own building in range; null (HUD message unless quiet) when refused.
     * {@code ability} (may be null in tests) gets half its cooldown back-charged if the walk is interrupted. No cooldown
     * handling here. Public for the bot and the game test.
     */
    @Nullable
    public static Walk begin(ServerLevel sl, LivingEntity warden, String owner, @Nullable BuildingPlacement b,
                             @Nullable Rootwalk ability, boolean quiet) {
        if (owner == null || owner.isBlank())
            return null;
        if (b == null || b.isDestroyedServerside || !owner.equals(b.ownerName) || b.getLevel() != sl) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_BAD_TARGET);
            return null;
        }
        if (CombatEngineers.distanceToBuilding(warden, b) > RANGE) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_TOO_FAR);
            return null;
        }
        ACTIVE.removeIf(w -> w.warden == warden);
        if (warden instanceof Unit u)
            Unit.fullResetBehaviours(u);   // it stands and sinks; any new order afterwards cancels (LEASH)
        if (warden instanceof Mob mob)
            mob.getNavigation().stop();
        Walk walk = new Walk(warden, owner, b, ability, sl.getGameTime() + SINK_TICKS, arrivalCell(sl, warden, b));
        ACTIVE.add(walk);
        sl.playSound(null, warden.blockPosition(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.NEUTRAL, 1.2f, 0.7f);
        return walk;
    }

    /** The Rootwarden's live walk, or null. Public for the game test. */
    @Nullable
    public static Walk walkOf(LivingEntity warden) {
        for (int i = 0, n = ACTIVE.size(); i < n; i++)
            if (ACTIVE.get(i).warden == warden)
                return ACTIVE.get(i);
        return null;
    }

    /** One tick of every sinking Rootwarden: interrupts, telegraph, arrival. From CombatEngineerServerEvents. */
    public static void tick(long gameTime) {
        if (ACTIVE.isEmpty())
            return;
        Iterator<Walk> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Walk w = it.next();
            LivingEntity warden = w.warden;
            if (!warden.isAlive() || warden.isRemoved() || w.building.isDestroyedServerside
                    || !(warden.level() instanceof ServerLevel sl)) {
                it.remove();
                continue;
            }
            float health = warden.getHealth();
            boolean hurt = health < w.lastHealth - 0.01f;
            w.lastHealth = health;
            if (hurt || warden.position().distanceToSqr(w.start) > LEASH * LEASH) {
                it.remove();
                interrupt(sl, w);
                continue;
            }
            if (gameTime >= w.arriveAt) {
                it.remove();
                arrive(sl, w);
                continue;
            }
            if (gameTime % FX_EVERY_TICKS == 0) {
                BlockPos dest = w.telegraph;
                BlockParticleOption roots = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState());
                sl.sendParticles(roots, warden.getX(), warden.getY() + 0.2, warden.getZ(), 6, 0.35, 0.1, 0.35, 0.05);
                sl.sendParticles(roots, dest.getX() + 0.5, dest.getY() + 0.2, dest.getZ() + 0.5, 6, 0.35, 0.1, 0.35, 0.05);
                sl.sendParticles(ParticleTypes.COMPOSTER, dest.getX() + 0.5, dest.getY() + 0.6, dest.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.01);
            }
        }
    }

    /** The walk failed: half the cooldown is spent (the full one was charged at the cast). */
    static void interrupt(ServerLevel sl, Walk w) {
        HudClientboundPacket.showTempMessageI18n(w.owner, MSG_INTERRUPTED);
        sl.sendParticles(ParticleTypes.SMOKE, w.warden.getX(), w.warden.getY() + 0.5, w.warden.getZ(), 6, 0.3, 0.3, 0.3, 0.01);
        if (w.ability != null && w.warden instanceof Unit u) {
            float half = w.ability.cooldownMax / 2f;
            w.ability.setCooldown(half, false, u);
            AbilityClientboundPacket.sendSetCooldownPacket(w.warden.getId(), w.ability.action, half);
        }
    }

    /** Rises beside the building and stands there (any old order is gone: it walked through the roots). */
    static void arrive(ServerLevel sl, Walk w) {
        BlockPos dest = arrivalCell(sl, w.warden, w.building);
        LivingEntity warden = w.warden;
        warden.teleportTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5);
        if (warden instanceof Mob mob)
            mob.getNavigation().stop();
        if (warden instanceof Unit u)
            Unit.fullResetBehaviours(u);
        BlockParticleOption roots = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState());
        sl.sendParticles(roots, warden.getX(), warden.getY() + 0.3, warden.getZ(), 16, 0.4, 0.3, 0.4, 0.1);
        sl.playSound(null, dest, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.NEUTRAL, 1.2f, 0.8f);
    }

    /**
     * Where a Rootwarden rises beside {@code b}: one block outside the footprint on the side facing the warden, at the
     * building's floor, moved to the nearest cell a unit can stand on and walk out of (ProductionPlacement's spawn rule,
     * which already knows labs' plinths, bowls and roofs). Public for the game test.
     */
    public static BlockPos arrivalCell(ServerLevel sl, LivingEntity warden, BuildingPlacement b) {
        int minX = b.minCorner.getX(), maxX = b.maxCorner.getX(), minZ = b.minCorner.getZ(), maxZ = b.maxCorner.getZ();
        double dx = warden.getX() - (minX + maxX + 1) / 2.0, dz = warden.getZ() - (minZ + maxZ + 1) / 2.0;
        int x, z;
        if (Math.abs(dx) >= Math.abs(dz)) {
            x = dx < 0 ? minX - 1 : maxX + 1;
            z = Mth.clamp(Mth.floor(warden.getZ()), minZ, maxZ);
        } else {
            z = dz < 0 ? minZ - 1 : maxZ + 1;
            x = Mth.clamp(Mth.floor(warden.getX()), minX, maxX);
        }
        return ProductionPlacement.safeSpawnPoint(sl, new BlockPos(x, b.minCorner.getY(), z));
    }

    public static void clearAll() {
        ACTIVE.clear();
    }
}
