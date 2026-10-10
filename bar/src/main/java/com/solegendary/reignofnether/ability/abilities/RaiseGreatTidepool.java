package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.tide.TidepoolServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MyMath;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Tidewrought Harbourmaster: <b>Raise Great Tidepool</b> (design/tidewrought_plan.md, slice 3). The Tide Priest's Raise
 * Tidepool writ large: a radius-{@link #RADIUS} disc (29 cells against the priest's 13) that lasts {@link #LIFETIME_TICKS}
 * ticks (45 s against 30). Aimed within {@link #RANGE} blocks, {@link #ENERGY_COST} energy, {@link #CD_SECONDS} s
 * cooldown.
 * <p>
 * Everything about placement goes through the same registry as the priest's ({@link RaiseTidepool#castPool} ->
 * {@link TidepoolServerEvents#raise}): open ground only, never inside a building or on a metal patch, the shared
 * {@link TidepoolServerEvents#POOLS_PER_PLAYER} pools per player (a Great Tidepool counts as one) and the per-level
 * cell budget. A refused raise costs nothing and starts no cooldown; the owner sees why on the HUD.
 */
public class RaiseGreatTidepool extends Ability {

    public static final int CD_SECONDS = 35;
    public static final int RANGE = 12;
    public static final int RADIUS = 3;
    public static final int LIFETIME_TICKS = 45 * 20;
    public static final int ENERGY_COST = 75;

    public RaiseGreatTidepool() {
        super(UnitAction.RAISE_GREAT_TIDEPOOL, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, RANGE, RADIUS, false, true);
        this.showRangeCircle = true;
        this.showRadiusCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Raise Great Tidepool",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/heart_of_the_sea.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.RAISE_GREAT_TIDEPOOL,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.RAISE_GREAT_TIDEPOOL),
            null,
            List.of(
                FormattedCharSequence.forward("Raise Great Tidepool  (" + ENERGY_COST + " energy, " + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("A wide tidepool rises on open ground up to " + RANGE + " blocks away for "
                    + LIFETIME_TICKS / 20 + " s - twice the Tide Priest's.", Style.EMPTY),
                FormattedCharSequence.forward("Tidewrought on it are faster and heal. Enemies can drain it.", Style.EMPTY),
                FormattedCharSequence.forward("The harbour follows the fleet.", Style.EMPTY.withItalic(true))
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
        if (cast(sl, unitUsing, at) != null) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /** Raises the great pool round the clicked ground block, or returns null (refused, nothing paid). Public for tests and the bot. */
    public static TidepoolServerEvents.Pool cast(ServerLevel sl, Unit caster, BlockPos centre) {
        return RaiseTidepool.castPool(sl, caster, centre, RADIUS, LIFETIME_TICKS, ENERGY_COST);
    }
}
