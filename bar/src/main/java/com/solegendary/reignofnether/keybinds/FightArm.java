package com.solegendary.reignofnether.keybinds;

import com.solegendary.reignofnether.unit.UnitAction;

/**
 * The armed state of Fight / Attack-move (F). One press of F, or one click on its button, flips it exactly once: off
 * becomes armed, armed becomes off, and an F press while a different order is armed (Move, Patrol...) swaps to Fight
 * rather than clearing. No Minecraft types: FightKeyGameTests checks the rule.
 *
 * Why it is its own rule: lovish (Oct 22): "I have to double click twice with F to attack move". Two key-release
 * handlers answered the same F (the command card in HudClientEvents and the unit inventory's item slot 3 in
 * ItemClientEvents), so one press ran two things and the second undid or replaced the first.
 */
public final class FightArm {

    private FightArm() { }

    /** The left-click action after one press of Fight while {@code current} is armed (null = nothing armed). */
    public static UnitAction press(UnitAction current) {
        return current == UnitAction.ATTACK_MOVE ? null : UnitAction.ATTACK_MOVE;
    }
}
