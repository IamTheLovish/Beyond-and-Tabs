package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.blocks.ThicketBlock;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MyMath;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Verdant essence caster (the Thornweaver; design/essence_casters.md, lovish approved draft 2 on Oct 29): <b>Entangling
 * Roots</b>, the cheap spell. Aim a spot within {@link #RANGE} blocks: every enemy ground unit within {@link #RADIUS}
 * of it is rooted for {@link #ROOT_TICKS} ticks - the Vine Snare's Slowness VII, so it cannot walk but still fights
 * whatever is in reach (and a Bloom Priestess's pulse frees it). {@link #ESSENCE_COST} essence, {@link #CD_SECONDS} s
 * cooldown. Refused - nothing spent, no cooldown, so the AutoCaster's "a refusal is not a cast" holds - when no enemy
 * stands in the circle or the caster is short of essence. Autocast: on a crowd (3 enemies in the circle).
 */
public class EntanglingRoots extends Ability {

    public static final int ESSENCE_COST = 5;
    public static final int CD_SECONDS = 12;
    public static final int RADIUS = 3;
    public static final int ROOT_TICKS = 3 * 20;
    public static final int ROOT_AMPLIFIER = 6;   // -105% speed: cannot walk (Overgrowth, the Vine Snare)
    /** Not in lovish's draft (only the circle was): how far the caster throws it, a placeholder for him to tune. */
    public static final int RANGE = 10;
    /** Enemies this far above or below the aimed spot still count (a hillside, not a cliff top). */
    static final int MAX_DY = 4;
    public static final String MSG_NO_TARGET = "hud.essence.reignofnether.roots_no_target";

    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused (server thread)

    public EntanglingRoots() {
        super(UnitAction.ENTANGLING_ROOTS, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, RADIUS, false, true);
        this.autoRule(AutoRule.CROWD);
        this.showRangeCircle = true;
        this.showRadiusCircle = true;
    }

    /** The autocast waits for the essence, so a dry caster never spams refusals. */
    @Override
    public boolean canAutocastNow(ServerLevel level, Unit unit) {
        return super.canAutocastNow(level, unit) && unit instanceof LivingEntity le && Essence.get(le) >= ESSENCE_COST;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Entangling Roots",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/hanging_roots.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.ENTANGLING_ROOTS,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.ENTANGLING_ROOTS),
            null,
            List.of(
                FormattedCharSequence.forward("Entangling Roots  (" + ESSENCE_COST + " essence, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Enemy ground units within " + RADIUS + " blocks of a spot up to " + RANGE
                    + " away are rooted for " + ROOT_TICKS / 20 + " s: they cannot move, but still fight.", Style.EMPTY),
                FormattedCharSequence.forward("The fallen feed the roots that hold the living.", Style.EMPTY.withItalic(true))
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
        if (cast(sl, self, unitUsing.getOwnerName(), at, false) == 0)
            return;
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    /**
     * The rule: finds the enemy ground units around {@code centre} (the aimed ground block), then spends the essence,
     * then roots them - so a refusal never costs essence. Returns how many were rooted (0 = refused). Public for the
     * bot and the game test (no cooldown handling here).
     */
    public static int cast(ServerLevel sl, LivingEntity caster, String owner, BlockPos centre, boolean quiet) {
        if (owner == null || owner.isBlank() || centre == null)
            return 0;
        double cx = centre.getX() + 0.5, cz = centre.getZ() + 0.5;
        List<LivingEntity> hit = new ArrayList<>();
        for (LivingEntity le : UnitGrid.near(sl, cx, cz, RADIUS, scratch)) {
            if (!le.isAlive() || le.level() != sl || !ThicketBlock.isEnemyOf(owner, le))
                continue;
            double dx = le.getX() - cx, dz = le.getZ() - cz;
            if (dx * dx + dz * dz > RADIUS * RADIUS || Math.abs(le.getY() - (centre.getY() + 1)) > MAX_DY + 1)
                continue;
            hit.add(le);
        }
        if (hit.isEmpty()) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_NO_TARGET);
            return 0;
        }
        if (!Essence.trySpend(caster, ESSENCE_COST)) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, Empower.MSG_SHORT);
            return 0;
        }
        BlockParticleOption roots = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.defaultBlockState());
        for (LivingEntity le : hit) {
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ROOT_TICKS, ROOT_AMPLIFIER));
            if (le instanceof Mob mob)
                mob.getNavigation().stop();
            le.setDeltaMovement(0, Math.min(0, le.getDeltaMovement().y), 0);
            sl.sendParticles(roots, le.getX(), le.getY() + 0.2, le.getZ(), 8, 0.3, 0.1, 0.3, 0.05);
        }
        sl.playSound(null, centre.above(), SoundEvents.ROOTED_DIRT_BREAK, SoundSource.NEUTRAL, 2f, 0.6f);
        sl.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, cx, centre.getY() + 1.2, cz, 10, RADIUS * 0.5, 0.3, RADIUS * 0.5, 0.0);
        return hit.size();
    }
}
