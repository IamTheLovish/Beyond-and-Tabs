package com.solegendary.reignofnether.ability.abilities;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutoRule;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.brass.ChargeServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Brassworks Sapper: <b>Plant Charge</b> (design/brassworks_plan.md section 3.5). Aim at an enemy building: standing
 * within {@link #REACH} of it, the Sapper sets a charge that fizzes {@link ChargeServerEvents#FUSE_TICKS} and then
 * deals {@link ChargeServerEvents#CHARGE_DAMAGE} building damage. Beyond reach the order is refused (the player or
 * the bot walks the Sapper up first). Free; {@link #CD_SECONDS} s cooldown.
 */
public class PlantCharge extends Ability {

    public static final int CD_SECONDS = 15;
    public static final float REACH = 3f;
    public static final String MSG_BAD_TARGET = "hud.brass.reignofnether.charge_bad_target";
    public static final String MSG_TOO_FAR = "hud.brass.reignofnether.charge_too_far";

    public PlantCharge() {
        super(UnitAction.PLANT_CHARGE, CD_SECONDS * ResourceCost.TICKS_PER_SECOND, REACH, 0, false, true);
        this.autoRule(AutoRule.MANUAL);   // autocast: wants a chosen building
        this.showRangeCircle = true;
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, Unit unit) {
        return new AbilityButton("Plant Charge",
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/tnt_side.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.PLANT_CHARGE,
            () -> false,
            () -> true,
            () -> CursorClientEvents.setLeftClickAction(UnitAction.PLANT_CHARGE),
            null,
            List.of(
                FormattedCharSequence.forward("Plant Charge  (" + CD_SECONDS + " s cooldown)", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward("Sets a charge on an enemy building within " + (int) REACH + " blocks: after a "
                    + ChargeServerEvents.FUSE_TICKS / 20 + " s fuse it deals " + (int) ChargeServerEvents.CHARGE_DAMAGE + " building damage.", Style.EMPTY),
                FormattedCharSequence.forward("Only the building is hurt.", Style.EMPTY),
                FormattedCharSequence.forward("Light it and walk away. Briskly.", Style.EMPTY.withItalic(true))
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
        if (plant(sl, self, unitUsing.getOwnerName(), targetBp, false)) {
            this.setToMaxCooldown(unitUsing);
            AbilityClientboundPacket.sendSetCooldownPacket(self.getId(), this.action, this.cooldownMax);
        }
    }

    /**
     * Plants on the enemy building at {@code aim} if the Sapper stands within {@link #REACH} of its nearest block.
     * Returns false when refused. Public for the bot and the game test (no cooldown handling here).
     */
    public static boolean plant(ServerLevel sl, LivingEntity sapper, String owner, BlockPos aim, boolean quiet) {
        if (aim == null || owner == null)
            return false;
        BuildingPlacement b = BuildingUtils.findBuilding(false, aim);
        if (b == null || b.ownerName == null || b.ownerName.equals(owner) || AlliancesServerEvents.isAllied(owner, b.ownerName)
                || !b.isAttackable()) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_BAD_TARGET);
            return false;
        }
        // the charge goes on the building's nearest edge to the Sapper
        BlockPos min = b.minCorner, max = b.maxCorner;
        double cx = Math.max(min.getX(), Math.min(max.getX() + 1, sapper.getX()));
        double cz = Math.max(min.getZ(), Math.min(max.getZ() + 1, sapper.getZ()));
        double dx = cx - sapper.getX(), dz = cz - sapper.getZ();
        if (dx * dx + dz * dz > REACH * REACH) {
            if (!quiet)
                HudClientboundPacket.showTempMessageI18n(owner, MSG_TOO_FAR);
            return false;
        }
        ChargeServerEvents.plant(sl, sapper, b, new Vec3(cx, sapper.getY(), cz));
        return true;
    }
}
