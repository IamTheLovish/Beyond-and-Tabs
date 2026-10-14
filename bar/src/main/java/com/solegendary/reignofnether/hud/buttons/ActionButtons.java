package com.solegendary.reignofnether.hud.buttons;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.goals.BuildRepairGoal;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.util.LanguageUtil;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

import static com.solegendary.reignofnether.unit.UnitClientEvents.sendUnitCommand;

// static list of generic unit actions (build, attack, move, stop, etc.)
public class ActionButtons {

    public static Button buildRepair;
    public static Button gather;
    public static Button attack;
    public static Button fight;
    public static Button move;
    public static Button stop;
    public static Button patrol;
    public static Button guard;
    public static Button hold;
    public static Button garrison;
    public static Button ungarrison;
    public static Button stance;   // BAR fire state cycle (StanceButton)

    public static void updateButtons() {
        buildRepair = new Button(
                "Build/Repair",
                Button.itemIconSize,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/shovel.png"),
                Keybindings.build,
                () -> CursorClientEvents.getLeftClickAction() == UnitAction.BUILD_REPAIR ||
                        HudClientEvents.hudSelectedEntity instanceof WorkerUnit workerUnit &&
                                workerUnit.getBuildRepairGoal() != null &&
                                workerUnit.getBuildRepairGoal().autocastRepair,
                () -> false,
                () -> true,
                () -> CursorClientEvents.setLeftClickAction(UnitAction.BUILD_REPAIR),
                () -> {
                    LivingEntity hudEntity = HudClientEvents.hudSelectedEntity;
                    if (hudEntity instanceof WorkerUnit workerUnit) {
                        BuildRepairGoal goal = workerUnit.getBuildRepairGoal();
                        if (goal != null) {
                            if (goal.autocastRepair)
                                UnitClientEvents.sendUnitCommand(UnitAction.DISABLE_AUTOCAST_BUILD_REPAIR);
                            else
                                UnitClientEvents.sendUnitCommand(UnitAction.ENABLE_AUTOCAST_BUILD_REPAIR);
                        }
                    }
                },
                List.of(
                        FormattedCharSequence.forward(LanguageUtil.getTranslation("hud.actionbuttons.reignofnether.build_repair"), Style.EMPTY),
                        FormattedCharSequence.forward(LanguageUtil.getTranslation("hud.actionbuttons.reignofnether.build_repair_autocast"), Style.EMPTY)
                )
        );
        gather = new Button(
                "Gather",
                Button.itemIconSize,
                null, // changes depending on the gather target
                null, // no key: G is Guard (BAR); the gather target is a click toggle
                () -> UnitClientEvents.getSelectedUnitResourceTarget() != ResourceName.NONE,
                () -> false,
                () -> true,
                () -> sendUnitCommand(UnitAction.TOGGLE_GATHER_TARGET),
                null,
                null
        );
        // BAR's Attack (A, X still works): armed, a left click on a target attacks it and on the ground attack-moves;
        // a left- or right-DRAG lays an attack-move line formation (UnitClientEvents), Shift keeps it armed to queue.
        // Lit while armed so it reads as a state, not a one-shot.
        attack = new Button(
                "Attack",
                Button.itemIconSize,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/sword.png"),
                Keybindings.attack,
                () -> CursorClientEvents.getLeftClickAction() == UnitAction.ATTACK,
                () -> false,
                () -> true,
                () -> CursorClientEvents.setLeftClickAction(
                        CursorClientEvents.getLeftClickAction() == UnitAction.ATTACK ? null : UnitAction.ATTACK),
                null,
                List.of(FormattedCharSequence.forward(LanguageUtil.getTranslation("hud.actionbuttons.reignofnether.attack"), Style.EMPTY),
                        FormattedCharSequence.forward(LanguageUtil.getTranslation("hud.actionbuttons.reignofnether.attack.tooltip"), Style.EMPTY))
        );
        attack.isActive = attack.isSelected;
        // BAR's Fight (F): attack-move, armed. A click fights to the spot (the units advance engaging everything on the
        // way), a drag lays a fight line; the plain click now spreads the group in a formation too (UnitActionItem).
        fight = new Button(
                "Fight",
                Button.itemIconSize,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/crossed_swords.png"),
                Keybindings.fight,
                () -> CursorClientEvents.getLeftClickAction() == UnitAction.ATTACK_MOVE,
                () -> false,
                () -> true,
                () -> CursorClientEvents.setLeftClickAction(
                        CursorClientEvents.getLeftClickAction() == UnitAction.ATTACK_MOVE ? null : UnitAction.ATTACK_MOVE),
                null,
                List.of(FormattedCharSequence.forward(LanguageUtil.getTranslation("hud.actionbuttons.reignofnether.fight"), Style.EMPTY),
                        FormattedCharSequence.forward(LanguageUtil.getTranslation("hud.actionbuttons.reignofnether.fight.tooltip"), Style.EMPTY))
        );
        fight.isActive = fight.isSelected;
        // BAR's Move (M): armed, a left click moves there and a left-drag lays a move line - the same as a right click
        move = new Button(
                "Move",
                Button.itemIconSize,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/boots.png"),
                Keybindings.move,
                () -> CursorClientEvents.getLeftClickAction() == UnitAction.MOVE,
                () -> false,
                () -> true,
                () -> CursorClientEvents.setLeftClickAction(
                        CursorClientEvents.getLeftClickAction() == UnitAction.MOVE ? null : UnitAction.MOVE),
                null,
                List.of(FormattedCharSequence.forward(LanguageUtil.getTranslation("hud.actionbuttons.reignofnether.move"), Style.EMPTY),
                        FormattedCharSequence.forward(LanguageUtil.getTranslation("hud.actionbuttons.reignofnether.move.tooltip"), Style.EMPTY))
        );
        move.isActive = move.isSelected;
        patrol = new Button(
                "Patrol",
                Button.itemIconSize,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/compass.png"),
                Keybindings.patrol,
                () -> CursorClientEvents.getLeftClickAction() == UnitAction.PATROL,
                () -> false,
                () -> true,
                () -> CursorClientEvents.setLeftClickAction(UnitAction.PATROL),
                null,
                List.of(FormattedCharSequence.forward(LanguageUtil.getTranslation("hud.actionbuttons.reignofnether.patrol"), Style.EMPTY),
                        FormattedCharSequence.forward(LanguageUtil.getTranslation("hud.actionbuttons.reignofnether.patrol.tooltip"), Style.EMPTY))
        );
        guard = new Button(
                "Guard",
                Button.itemIconSize,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/chestplate.png"),
                Keybindings.guard,
                () -> CursorClientEvents.getLeftClickAction() == UnitAction.GUARD,
                () -> false,
                () -> true,
                () -> CursorClientEvents.setLeftClickAction(UnitAction.GUARD),
                null,
                List.of(FormattedCharSequence.forward(LanguageUtil.getTranslation("hud.actionbuttons.reignofnether.guard"), Style.EMPTY),
                        FormattedCharSequence.forward(LanguageUtil.getTranslation("hud.actionbuttons.reignofnether.guard.tooltip"), Style.EMPTY))
        );
        stop = new Button(
                "Stop",
                Button.itemIconSize,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/barrier.png"),
                Keybindings.stop,
                () -> false, // except if currently clicked on
                () -> false,
                () -> true,
                () -> sendUnitCommand(UnitAction.STOP),
                null,
                List.of(FormattedCharSequence.forward(LanguageUtil.getTranslation("hud.actionbuttons.reignofnether.stop"), Style.EMPTY))
        );
        hold = new Button(
                "Hold Position",
                Button.itemIconSize,
                // a shield, not the chestplate Guard already shows (the two read as one button in the clip)
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/icons/items/shield.png"),
                Keybindings.hold,
                () -> {
                    LivingEntity entity = HudClientEvents.hudSelectedEntity;
                    return entity instanceof Unit unit && unit.getHoldPosition();
                },
                () -> false,
                () -> true,
                () -> sendUnitCommand(UnitAction.HOLD),
                null,
                List.of(FormattedCharSequence.forward(LanguageUtil.getTranslation("hud.actionbuttons.reignofnether.hold_position"), Style.EMPTY))
        );
        // BAR state toggles light their frame while on (same treatment as the stance and repeat buttons)
        hold.isActive = hold.isSelected;
        stance = new StanceButton();
        garrison = new Button(
                "Garrison",
                Button.itemIconSize,
                ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/ladder.png"),
                Keybindings.garrison,
                () -> CursorClientEvents.getLeftClickAction() == UnitAction.GARRISON,
                () -> false,
                () -> true,
                () -> CursorClientEvents.setLeftClickAction(UnitAction.GARRISON),
                null,
                List.of(FormattedCharSequence.forward(LanguageUtil.getTranslation("hud.actionbuttons.reignofnether.garrison"), Style.EMPTY))
        );
        ungarrison = new Button(
                "Ungarrison",
                Button.itemIconSize,
                ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/oak_trapdoor.png"),
                Keybindings.garrison,
                () -> false,
                () -> false,
                () -> true,
                () -> sendUnitCommand(UnitAction.UNGARRISON),
                null,
                List.of(FormattedCharSequence.forward(LanguageUtil.getTranslation("hud.actionbuttons.reignofnether.ungarrison"), Style.EMPTY))
        );
    }

    static {
        updateButtons();
    }
}
