package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.CombatEngineers;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Sunforged Field Mason (combat engineer, design/combat_engineers.md): <b>Bulwark</b>. Aim at a friendly building
 * whose nearest block is within {@link #RANGE}: for {@link #DURATION_TICKS} ticks it takes x{@link #DAMAGE_MULT} damage
 * from attackers - every hit that goes through {@code BuildingPlacement.destroyRandomBlocks(amount, attacker)}: melee,
 * shells, charges, siege - <b>while the Mason stays alive and within {@link #TETHER}</b> of it. Game-ender strikes and
 * commander death blasts are not attacks (they call the attacker-less overload) and ignore it: the Ward Spire stays
 * the answer to a game ender.
 * <p>
 * Tempo: holds a gate or a tower through one push while it is repaired. Counter: the gold shimmer and the beam to the
 * Mason show what is braced and by whom - kill or chase off the Mason and the tether breaks, outlast the 12 s, or hit
 * another building. One Bulwark per building and per Mason (a recast moves it). Free; {@link #CD_SECONDS} s cooldown.
 * <p>
 * The live braces sit in an identity map keyed by placement, so the damage hook ({@link #damageMultiplier}, called on
 * every building hit) is one isEmpty check while nobody braces. Ticked by CombatEngineerServerEvents.
 */
public class Bulwark extends Ability {

    public static final int CD_SECONDS = 35;
    public static final int DURATION_TICKS = 12 * 20;
    public static final float RANGE = 8f;
    public static final float TETHER = 12f;
    public static final float DAMAGE_MULT = 0.5f;
    static final int FX_EVERY_TICKS = 10;
    public static final String MSG_BAD_TARGET = "hud.reignofnether.bulwark_bad_target";
    public static final String MSG_TOO_FAR = "hud.reignofnether.engineer_too_far";

    /** One braced building. Public for the game test. */
    public static final class Brace {
        public final BuildingPlacement building;
        public final LivingEntity mason;
        public final long expiresAt;

        Brace(BuildingPlacement building, LivingEntity mason, long expiresAt) {
            this.building = building;
            this.mason = mason;
            this.expiresAt = expiresAt;
        }
    }

    private static final Map<BuildingPlacement, Brace> ACTIVE = new IdentityHashMap<>();

    public Bulwark() {
        super(UnitAction.BULWARK, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, false, true);
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Bulwark",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/chiseled_stone_bricks.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.BULWARK,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.BULWARK),
            null,
            List.of(
                FormattedCharSequence.forward("Bulwark  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("A friendly building within " + (int) RANGE + " blocks takes half damage from attackers for "
                    + DURATION_TICKS / 20 + " s, as long as the Mason stays alive and within " + (int) TETHER + " blocks of it.", Style.EMPTY),
                FormattedCharSequence.forward("Kill or drive off the Mason and the walls are only walls.", Style.EMPTY.withItalic(true))
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
        if (brace(sl, self, unitUsing.getOwnerName(), BuildingUtils.findBuilding(false, targetBp), false) != null) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /** An instant cast at range: the Mason keeps repairing or building what it was. */
    @Override
    public boolean shouldResetBehaviours() {
        return false;
    }

    /**
     * Braces {@code b} for {@code mason} if it is a friendly building in range; null (with a HUD message unless quiet)
     * when refused. No cooldown handling here. Public for the bot and the game test.
     */
    @Nullable
    public static Brace brace(ServerLevel sl, LivingEntity mason, String owner, @Nullable BuildingPlacement b, boolean quiet) {
        if (b == null || b.isDestroyedServerside || !CombatEngineers.isFriendly(owner, b.ownerName)) {
            if (!quiet && owner != null)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_BAD_TARGET);
            return null;
        }
        if (CombatEngineers.distanceToBuilding(mason, b) > RANGE) {
            if (!quiet && owner != null)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_TOO_FAR);
            return null;
        }
        Iterator<Brace> it = ACTIVE.values().iterator();
        while (it.hasNext())
            if (it.next().mason == mason)
                it.remove();   // one Bulwark per Mason: a recast moves it
        Brace brace = new Brace(b, mason, sl.getGameTime() + DURATION_TICKS);
        ACTIVE.put(b, brace);
        sl.playSound(null, b.centrePos, SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.8f, 0.7f);
        fx(sl, brace);
        return brace;
    }

    /**
     * The multiplier for an attack on {@code b}: {@link #DAMAGE_MULT} while a Bulwark holds on it, else 1. Called by
     * {@code BuildingPlacement.destroyRandomBlocks(amount, attacker)} on every building hit, so it costs one isEmpty
     * check while nobody braces.
     */
    public static float damageMultiplier(@Nullable BuildingPlacement b) {
        if (ACTIVE.isEmpty() || b == null)
            return 1f;
        Brace brace = ACTIVE.get(b);
        return brace != null && holds(brace) ? DAMAGE_MULT : 1f;
    }

    /** The live brace on {@code b}, or null. Public for the bot and the game test. */
    @Nullable
    public static Brace braceOn(@Nullable BuildingPlacement b) {
        if (ACTIVE.isEmpty() || b == null)
            return null;
        Brace brace = ACTIVE.get(b);
        return brace != null && holds(brace) ? brace : null;
    }

    /** Alive, in time, within the tether, the building still standing. */
    static boolean holds(Brace brace) {
        BuildingPlacement b = brace.building;
        LivingEntity m = brace.mason;
        return m.isAlive() && !m.isRemoved() && !b.isDestroyedServerside && m.level() == b.getLevel()
            && b.getLevel().getGameTime() < brace.expiresAt && CombatEngineers.distanceToBuilding(m, b) <= TETHER;
    }

    /** Drops the braces that no longer hold; shimmer on the ones that do. From CombatEngineerServerEvents. */
    public static void tick(long gameTime) {
        if (ACTIVE.isEmpty() || gameTime % FX_EVERY_TICKS != 0)
            return;
        Iterator<Brace> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Brace brace = it.next();
            if (!holds(brace)) {
                it.remove();
                continue;
            }
            if (brace.building.getLevel() instanceof ServerLevel sl)
                fx(sl, brace);
        }
    }

    /** Gold motes on the building's corners and a sunlit beam from the Mason to its centre. */
    static void fx(ServerLevel sl, Brace brace) {
        BuildingPlacement b = brace.building;
        double y = b.minCorner.getY() + 1.5;
        for (double[] c : new double[][] { { b.minCorner.getX(), b.minCorner.getZ() }, { b.maxCorner.getX() + 1, b.minCorner.getZ() },
                { b.minCorner.getX(), b.maxCorner.getZ() + 1 }, { b.maxCorner.getX() + 1, b.maxCorner.getZ() + 1 } })
            sl.sendParticles(ParticleTypes.WAX_ON, c[0], y, c[1], 2, 0.2, 0.6, 0.2, 0.01);
        LivingEntity m = brace.mason;
        com.solegendary.reignofnether.barfx.BarFx.beam(sl, new Vec3(m.getX(), m.getY() + m.getBbHeight() * 0.6, m.getZ()),
            new Vec3(b.centrePos.getX() + 0.5, y, b.centrePos.getZ() + 0.5), com.solegendary.reignofnether.barfx.BarFx.N_SUNFORGED,
            FX_EVERY_TICKS + 2);
    }

    /** Server stop / reset: nothing survives a world change. */
    public static void clearAll() {
        ACTIVE.clear();
    }
}
