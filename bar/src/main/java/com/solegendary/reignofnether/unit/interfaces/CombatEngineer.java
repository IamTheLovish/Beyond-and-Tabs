package com.solegendary.reignofnether.unit.interfaces;

import com.solegendary.reignofnether.ability.Ability;

/**
 * A <b>combat engineer</b> (design/combat_engineers.md): a sturdy T2 builder that marches with the army, reclaims the
 * battlefield, mends what is hurt while the fight is still on and raises field defences. One per faction - six on the
 * illager body ({@code unit/units/engineers/CombatEngineerUnit}) and the Horde's on the piglin body
 * ({@code WreckerUnit}) - so the shared rules can't live in one base class: they live in
 * {@link com.solegendary.reignofnether.unit.CombatEngineers}, and this interface marks the units it applies to.
 */
public interface CombatEngineer extends WorkerUnit {

    /**
     * This engineer's faction ability - the very instance in its ability set, so the cooldown read through
     * {@code Unit.getCooldown(ability)} is the one the HUD shows. Used by the bot and the GameTests.
     */
    Ability getEngineerAbility();
}
