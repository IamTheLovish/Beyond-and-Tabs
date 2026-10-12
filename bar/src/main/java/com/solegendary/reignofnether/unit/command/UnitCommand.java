package com.solegendary.reignofnether.unit.command;

import com.solegendary.reignofnether.unit.UnitAction;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;

/**
 * One order in a unit's command queue (Spring / BAR's Command): what to do - the UnitAction Reign of Nether's
 * dispatch understands - on what (an entity id, or -1, and a block position) and in whose name it was given
 * ({@code owner}; null for an order the server issues itself). Immutable, so one instance is shared by every unit
 * an order was given to; created when an order is issued, never per tick.
 * <p>
 * A building ability riding on an order (UnitActionItem's selectedBuildingPos) is not part of the command: it is not
 * the unit's to queue, so CommandQueues runs it once when the order is given.
 */
public record UnitCommand(UnitAction action, @Nullable String owner, int targetId, BlockPos pos) {

    public UnitCommand {
        if (action == null)
            throw new IllegalArgumentException("a unit command needs an action");
        if (pos == null)
            pos = BlockPos.ZERO;
    }

    public CommandKind kind() {
        return CommandKind.of(action);
    }
}
