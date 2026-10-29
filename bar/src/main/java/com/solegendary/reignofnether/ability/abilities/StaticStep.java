package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
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
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Stormcallers essence caster (the Spark Sage; design/essence_casters.md, "Last three casters"): <b>Static Step</b>, the
 * cheap spell. A blink of up to {@link #RANGE} blocks toward the aimed spot, on the Thunder Sage's Blink rules
 * ({@link Blink#landingSpot}: the spot needs line of sight and solid ground with room for the body, and the blink
 * shortens along the line before it gives up). {@link #ESSENCE_COST} essence, {@link #CD_SECONDS} s cooldown. Refused -
 * nothing spent, no cooldown - when there is no visible standable spot or the caster is short of essence.
 * <p>
 * Aimed at the Sage's own feet it steps {@link #RANGE} blocks straight away from the nearest enemy within
 * {@link #ESCAPE_SCAN}: that is also the autocast (SELF_IN_COMBAT orders at the caster's own block), which fires only
 * when the Sage is under {@link #ESCAPE_HEALTH} of its health with an enemy within {@link #ESCAPE_TRIGGER} blocks.
 */
public class StaticStep extends Ability {

    public static final int ESSENCE_COST = 5;
    public static final int CD_SECONDS = 12;
    public static final int RANGE = 8;
    /** The autocast's trigger: under half health with an enemy this close. */
    public static final float ESCAPE_HEALTH = 0.5f;
    public static final double ESCAPE_TRIGGER = 6;
    /** How far the self-aimed step looks for the enemy to step away from. */
    public static final double ESCAPE_SCAN = 10;
    public static final String MSG_NO_SPOT = "hud.essence.reignofnether.static_step_no_spot";

    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused (server thread)

    public StaticStep() {
        super(UnitAction.STATIC_STEP, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, 0, false, false);
        this.autoRule(AutoRule.SELF_IN_COMBAT);
        this.autocastEssence = ESSENCE_COST;
        this.showRangeCircle = true;
    }

    /** The escape autocast: only when hurt below half with an enemy close by. */
    @Override
    public boolean canAutocastNow(ServerLevel level, Unit unit) {
        if (!super.canAutocastNow(level, unit) || !(unit instanceof LivingEntity self))
            return false;
        if (self.getHealth() >= self.getMaxHealth() * ESCAPE_HEALTH)
            return false;
        return nearestEnemy(level, self, unit.getOwnerName(), ESCAPE_TRIGGER) != null;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Static Step",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/phantom_membrane.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.STATIC_STEP,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.STATIC_STEP),
            null,
            List.of(
                FormattedCharSequence.forward("Static Step  (" + ESSENCE_COST + " essence, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Blink up to " + RANGE + " blocks to a spot in sight with ground to stand on.", Style.EMPTY),
                FormattedCharSequence.forward("Aim at the Sage itself to step away from the nearest enemy (it does so by itself when badly hurt).", Style.EMPTY),
                FormattedCharSequence.forward("Gone in a crackle, before the blow lands.", Style.EMPTY.withItalic(true))
            ),
            this,
            unit);
    }

    @Override
    public void use(Level level, Unit unitUsing, LivingEntity targetEntity) {
        if (targetEntity != null)
            use(level, unitUsing, targetEntity.blockPosition());
    }

    @Override
    public void use(Level level, Unit unitUsing, BlockPos targetBp) {
        if (level.isClientSide() || !(unitUsing instanceof LivingEntity self) || !(level instanceof ServerLevel sl) || targetBp == null)
            return;
        Vec3 aim;
        if (targetBp.getX() == self.getBlockX() && targetBp.getZ() == self.getBlockZ()) {
            aim = escapeAim(sl, self, unitUsing.getOwnerName());
            if (aim == null) {
                HudClientboundPacket.showTempMessageI18n(unitUsing.getOwnerName(), MSG_NO_SPOT);
                return;
            }
        } else {
            aim = Vec3.atBottomCenterOf(targetBp.above());
        }
        if (!cast(sl, self, unitUsing.getOwnerName(), aim, false))
            return;
        this.setToMaxCooldown(unitUsing);
        if (self.level().getEntity(self.getId()) == self)
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    /**
     * The rule: finds the landing spot toward {@code aim} (at most {@link #RANGE} blocks), then spends the essence, then
     * blinks - so a refusal never costs essence. Returns whether it blinked. Public for the bot and the game test (no
     * cooldown handling here).
     */
    public static boolean cast(ServerLevel sl, LivingEntity caster, String owner, Vec3 aim, boolean quiet) {
        if (owner == null || owner.isBlank() || aim == null)
            return false;
        Vec3 spot = Blink.landingSpot(sl, caster, aim, RANGE);
        if (spot == null) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_NO_SPOT);
            return false;
        }
        if (!Essence.trySpend(caster, ESSENCE_COST)) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, Empower.MSG_SHORT);
            return false;
        }
        if (caster instanceof Unit u)
            Unit.fullResetBehaviours(u);   // the blink is an order: drop the old path first, or it walks straight back
        Blink.blinkTo(sl, caster, spot);
        return true;
    }

    /** Where the self-aimed step goes: {@link #RANGE} blocks straight away from the nearest enemy, or null if none. */
    @Nullable
    public static Vec3 escapeAim(ServerLevel sl, LivingEntity self, String owner) {
        LivingEntity foe = nearestEnemy(sl, self, owner, ESCAPE_SCAN);
        if (foe == null)
            return null;
        double dx = self.getX() - foe.getX(), dz = self.getZ() - foe.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < 1e-3) {
            dx = 1;
            dz = 0;
            d = 1;
        }
        return new Vec3(self.getX() + dx / d * RANGE, self.getY(), self.getZ() + dz / d * RANGE);
    }

    @Nullable
    static LivingEntity nearestEnemy(ServerLevel sl, LivingEntity self, String owner, double reach) {
        if (owner == null || owner.isBlank())
            return null;
        LivingEntity best = null;
        double bestD = reach * reach;
        for (LivingEntity le : UnitGrid.near(sl, self.getX(), self.getZ(), reach, scratch)) {
            if (le == self || !le.isAlive() || !(le instanceof Unit u) || !Blink.isEnemy(owner, u))
                continue;
            double dx = le.getX() - self.getX(), dz = le.getZ() - self.getZ(), d = dx * dx + dz * dz;
            if (d <= bestD && Math.abs(le.getY() - self.getY()) <= 6) {
                bestD = d;
                best = le;
            }
        }
        scratch.clear();
        return best;
    }
}
