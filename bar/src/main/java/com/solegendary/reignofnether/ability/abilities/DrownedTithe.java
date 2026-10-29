package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.tide.TideSeerServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MyMath;

import net.minecraft.core.BlockPos;
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
 * Tidewrought essence caster (the Tide Seer; design/essence_casters.md, Revision 1 numbers): <b>Drowned Tithe</b>, the
 * mid spell. Aim a spot within {@link #RANGE} blocks: a whirlpool of radius {@link #RADIUS} swirls there for
 * {@link #DURATION_TICKS} ticks; every enemy ground unit inside is {@link #SLOW} slower (-40%) and takes
 * {@link #DAMAGE_PER_SECOND} magic damage a second (TideSeerServerEvents). {@link #ESSENCE_COST} essence,
 * {@link #CD_SECONDS} s cooldown. Refused - nothing spent, no cooldown - when no enemy ground unit stands in the circle
 * or the caster is short of essence. Autocast: on a crowd (3 enemies in the circle).
 */
public class DrownedTithe extends Ability {

    public static final int ESSENCE_COST = 10;
    public static final int CD_SECONDS = 25;
    public static final int RADIUS = 5;
    public static final int DURATION_TICKS = 8 * 20;
    /** The speed taken off (a MULTIPLY_TOTAL modifier of -0.4). */
    public static final double SLOW = 0.4;
    /** Revision 1 (scaled to T1 health, 30-80): 3 a second, 24 over the pool's life, down from the draft's 8. */
    public static final float DAMAGE_PER_SECOND = 3f;
    /** Not in lovish's draft: how far the caster throws it, a placeholder for him to tune. */
    public static final int RANGE = 10;
    public static final String MSG_NO_TARGET = "hud.essence.reignofnether.tithe_no_target";

    private static final List<LivingEntity> scratch = new ArrayList<>();   // grid query results, reused (server thread)

    public DrownedTithe() {
        super(UnitAction.DROWNED_TITHE, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, RADIUS, false, true);
        this.autoRule(AutoRule.CROWD);
        this.autocastEssence = ESSENCE_COST;
        this.showRangeCircle = true;
        this.showRadiusCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Drowned Tithe",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/heart_of_the_sea.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.DROWNED_TITHE,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.DROWNED_TITHE),
            null,
            List.of(
                FormattedCharSequence.forward("Drowned Tithe  (" + ESSENCE_COST + " essence, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("A whirlpool of radius " + RADIUS + " up to " + RANGE + " blocks away swirls for "
                    + DURATION_TICKS / 20 + " s: enemy ground units in it are " + Math.round(SLOW * 100) + "% slower and take "
                    + (int) DAMAGE_PER_SECOND + " damage a second.", Style.EMPTY),
                FormattedCharSequence.forward("What the sea takes, it takes again.", Style.EMPTY.withItalic(true))
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
        // the autocast aims at a unit's feet (the air block it stands in), a click at the ground under it
        if (level.getBlockState(at).isAir())
            at = at.below();
        if (cast(sl, self, unitUsing.getOwnerName(), at, false) == null)
            return;
        this.setToMaxCooldown(unitUsing);
        AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
    }

    /**
     * The rule: finds an enemy ground unit in the circle round {@code centre} (the aimed ground block), then spends the
     * essence, then opens the pool - so a refusal never costs essence. Returns the pool, or null when refused. Public for
     * the bot and the game test (no cooldown handling here).
     */
    public static TideSeerServerEvents.Whirlpool cast(ServerLevel sl, LivingEntity caster, String owner, BlockPos centre, boolean quiet) {
        if (owner == null || owner.isBlank() || centre == null)
            return null;
        Vec3 c = new Vec3(centre.getX() + 0.5, centre.getY() + 1, centre.getZ() + 0.5);
        boolean any = false;
        for (LivingEntity le : UnitGrid.near(sl, c.x, c.z, RADIUS + 1, scratch)) {
            double dx = le.getX() - c.x, dz = le.getZ() - c.z;
            if (TideSeerServerEvents.isEnemyGround(owner, le) && dx * dx + dz * dz <= RADIUS * RADIUS
                    && Math.abs(le.getY() - c.y) <= 4) {
                any = true;
                break;
            }
        }
        if (!any) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_NO_TARGET);
            return null;
        }
        if (!Essence.trySpend(caster, ESSENCE_COST)) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, Empower.MSG_SHORT);
            return null;
        }
        sl.playSound(null, centre.above(), SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundSource.NEUTRAL, 2f, 0.7f);
        sl.playSound(null, centre.above(), SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 1.5f, 0.6f);
        return TideSeerServerEvents.addWhirlpool(sl, c, owner, caster, RADIUS, DURATION_TICKS, SLOW, DAMAGE_PER_SECOND);
    }
}
