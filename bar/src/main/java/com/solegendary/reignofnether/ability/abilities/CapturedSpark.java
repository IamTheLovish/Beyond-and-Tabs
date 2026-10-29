package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.barfx.LightningFx;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.mana.Blink;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitGrid;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Stormcallers essence caster (the Spark Sage; design/essence_casters.md, "Last three casters", revision-scaled):
 * <b>Captured Spark</b>, the mid spell. Chain lightning at an enemy unit within {@link #RANGE} blocks: it strikes that
 * unit, then jumps to the nearest enemy not yet struck within {@link #JUMP_RADIUS} blocks of the last one, until it has
 * struck {@link #STRIKES} units (the design's "4 jumps": 4 strikes, 30 x 4 = the draft's 120) or nobody is in reach.
 * {@link #DAMAGE} indirect magic each, no falloff, never a friend, never the same unit twice; a particle bolt
 * ({@link LightningFx#bolt}) on every link, as the Tesla Walker's arc. Units only, not buildings.
 * {@link #ESSENCE_COST} essence, {@link #CD_SECONDS} s cooldown. Refused - nothing spent, no cooldown - when the target
 * is not an enemy unit in range or the caster is short of essence. Autocast: the nearest enemy in range.
 */
public class CapturedSpark extends Ability {

    public static final int ESSENCE_COST = 10;
    public static final int CD_SECONDS = 25;
    public static final float DAMAGE = 30f;
    public static final int STRIKES = 4;
    public static final double JUMP_RADIUS = 6;
    /** Not in the design (only the jumps were): how far the Sage throws the first bolt, a placeholder for lovish. */
    public static final int RANGE = 10;
    /** A link this far above or below the last one still counts (a slope, not a cliff top). */
    static final double MAX_DY = 6;
    public static final String MSG_BAD_TARGET = "hud.essence.reignofnether.spark_bad_target";

    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused (server thread)

    public CapturedSpark() {
        super(UnitAction.CAPTURED_SPARK, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, (float) JUMP_RADIUS, true, true);
        this.autoRule(AutoRule.ENEMY_IN_RANGE);
        this.autocastEssence = ESSENCE_COST;
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Captured Spark",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/lightning_rod.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.CAPTURED_SPARK,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.CAPTURED_SPARK),
            null,
            List.of(
                FormattedCharSequence.forward("Captured Spark  (" + ESSENCE_COST + " essence, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Chain lightning at an enemy up to " + RANGE + " blocks away: " + (int) DAMAGE
                    + " damage, then it jumps to the next enemy within " + (int) JUMP_RADIUS + ", " + STRIKES + " strikes in all.", Style.EMPTY),
                FormattedCharSequence.forward("The dead gave up their spark; the living get it back.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl))
            return;
        if (cast(sl, self, unitUsing.getOwnerName(), targetEntity, false) == 0)
            return;
        this.setToMaxCooldown(unitUsing);
        if (self.level().getEntity(self.getId()) == self)
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    /** A ground click (or a hidden target, cast at its feet): the spark needs a unit to strike, so it does nothing. */
    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (!level.isClientSide())
            HudClientboundPacket.showTempMessageI18n(unitUsing.getOwnerName(), MSG_BAD_TARGET);
    }

    /**
     * The rule: checks the first target (an enemy unit within {@link #RANGE}), then spends the essence, then strikes
     * the chain - so a refusal never costs essence. Returns how many units were struck (0 = refused). Public for the
     * bot and the game test (no cooldown handling here).
     */
    public static int cast(ServerLevel sl, LivingEntity caster, String owner, LivingEntity first, boolean quiet) {
        if (true) return 0;   // STUB (tests first)
        if (owner == null || owner.isBlank())
            return 0;
        if (first == null || first == caster || !first.isAlive() || first.level() != sl || !(first instanceof Unit fu)
                || !Blink.isEnemy(owner, fu) || !inRange(caster, first)) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_BAD_TARGET);
            return 0;
        }
        if (!Essence.trySpend(caster, ESSENCE_COST)) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, Empower.MSG_SHORT);
            return 0;
        }
        List<LivingEntity> struck = new ArrayList<>(STRIKES);
        Vec3 from = new Vec3(caster.getX(), caster.getEyeY() + 0.3, caster.getZ());
        LivingEntity current = first;
        while (current != null && struck.size() < STRIKES) {
            struck.add(current);
            Vec3 to = new Vec3(current.getX(), current.getY() + current.getBbHeight() * 0.6, current.getZ());
            LightningFx.bolt(sl, from.x, from.y, from.z, to.x, to.y, to.z);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 6, 0.3, 0.3, 0.3, 0.1);
            // indirect magic, not mobAttack: RoN rewrites mob-attack damage to the attacker's melee damage
            current.hurt(sl.damageSources().indirectMagic(caster, caster), DAMAGE);
            from = to;
            current = nextLink(sl, owner, current, struck);
        }
        sl.playSound(null, caster.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.NEUTRAL, 0.8f, 1.6f);
        return struck.size();
    }

    static boolean inRange(LivingEntity caster, LivingEntity target) {
        double dx = target.getX() - caster.getX(), dz = target.getZ() - caster.getZ();
        double reach = RANGE + target.getBbWidth() / 2;
        return dx * dx + dz * dz <= reach * reach && Math.abs(target.getY() - caster.getY()) <= MAX_DY;
    }

    /** The nearest live enemy unit of {@code owner} within {@link #JUMP_RADIUS} of {@code last} not yet struck, or null. */
    static LivingEntity nextLink(ServerLevel sl, String owner, LivingEntity last, List<LivingEntity> struck) {
        LivingEntity best = null;
        double bestD = JUMP_RADIUS * JUMP_RADIUS;
        for (LivingEntity le : UnitGrid.near(sl, last.getX(), last.getZ(), JUMP_RADIUS, scratch)) {
            if (!le.isAlive() || le.level() != sl || !(le instanceof Unit u) || struck.contains(le) || !Blink.isEnemy(owner, u))
                continue;
            if (Math.abs(le.getY() - last.getY()) > MAX_DY)
                continue;
            double dx = le.getX() - last.getX(), dz = le.getZ() - last.getZ(), d = dx * dx + dz * dz;
            if (d <= bestD) {
                bestD = d;
                best = le;
            }
        }
        scratch.clear();
        return best;
    }
}
