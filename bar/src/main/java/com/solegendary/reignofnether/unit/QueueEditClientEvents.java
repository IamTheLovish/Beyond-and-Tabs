package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.unit.command.QueueEditKeys;
import com.solegendary.reignofnether.unit.command.QueueMode;
import com.solegendary.reignofnether.unit.packets.QueueEditServerboundPacket;
import com.solegendary.reignofnether.util.ArrayUtil;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Client side of BAR's queue editing (rules in unit/command/QueueEditKeys): the skip key (N) and Ctrl+skip key send a
 * QueueEditServerboundPacket for the selected own units; the insert key (Space) held while giving an order makes it
 * a front-of-queue insert. Called from HudClientEvents.onKeyRelease (before the card buttons, so one press is one
 * action) and from UnitClientEvents.queueModeFromKeys.
 */
public final class QueueEditClientEvents {

    private QueueEditClientEvents() { }

    /** The queue mode for an order given now (Shift appends; Ctrl or the held insert key put it in front). */
    public static QueueMode queueModeFromKeys() {
        return QueueEditKeys.modeFor(Keybindings.shiftMod.isDown(), Keybindings.ctrlMod.isDown(),
            Keybindings.queueInsert.isDown(), !UnitClientEvents.getSelectedUnits().isEmpty());
    }

    /** A released key: true if queue editing used it (the caller then skips its other meanings). */
    public static boolean onKeyRelease(int key) {
        if (Keybindings.queueInsert.matches(key) && QueueEditKeys.consumeInsertRelease())
            return true;
        if (!Keybindings.queueSkip.matches(key) || !OrthoviewClientEvents.isEnabled())
            return false;
        List<LivingEntity> units = UnitClientEvents.actionableSelectedUnits();
        boolean queued = false;
        for (LivingEntity le : units)
            if (UnitQueueLinesClient.entryCount(le.getId()) >= 2) {   // a current order and one waiting
                queued = true;
                break;
            }
        QueueEditKeys.Edit edit = QueueEditKeys.onSkipKey(Keybindings.ctrlMod.isDown(), !units.isEmpty(),
            HudClientEvents.isCardKey(key), queued);
        if (edit == QueueEditKeys.Edit.NONE)
            return false;
        PacketHandler.INSTANCE.sendToServer(new QueueEditServerboundPacket(edit, ArrayUtil.livingEntityListToIdArray(units)));
        return true;
    }
}
