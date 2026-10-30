package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.unit.interfaces.KeyframeAnimated;
import com.solegendary.reignofnether.unit.units.heroes.DawnPaladinUnit;
import com.solegendary.reignofnether.unit.units.verdant.MossbackBisonUnit;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;

/**
 * When a melee blow lands, in ticks after the swing: the frame its body's weapon visibly strikes (lovish, Oct 30: "hit
 * on the strike"; design-draft-combat-feel-2026-10-30.md, gain 1). The plain melee path swung and struck in one tick,
 * so the target's flinch, the hit spark and sound and the attacker's contact recoil (#375) all came before the arm got
 * there. HeldBlow holds the damage to this tick; the swing and the cooldown still start on the swing's tick.
 * <p>
 * By body family, from the clips the bodies draw (a body keyed for its own clip, the Halberdier, answers through
 * KeyedSwing instead):
 * <ul>
 *   <li>vanilla's 6-tick arm swing - humanoid (zombies, piglins, wither skeletons, the enderman), illager and the
 *   villager-model bodies on the Vindicator base: the downstroke peaks at attackTime 0.5, tick 3;</li>
 *   <li>the generated chop (HumanActs.chop, the Dawn Paladin): the axe passes the downstroke near t 0.6, tick 4;</li>
 *   <li>the Mossback Bison's horn toss (animations/Quadruped): the drive key at P 0.55 of its 16-tick clip, tick 9;</li>
 *   <li>0 for everything else: bodies whose attack clip starts on the damage event itself (the iron golem, ravager,
 *   hoglin, zoglin and warden play entity event 4 from doHurtTarget, so holding the damage would hold the clip too),
 *   keyframe rigs (their wind-up goal already times the blow) and creatures with no attack clip of ours.</li>
 * </ul>
 */
public final class BlowTiming {

    public static final int SWING = 3;
    public static final int CHOP = 4;
    public static final int BISON = 9;

    private BlowTiming() { }

    /** Ticks from the swing to the strike for this attacker's body, or 0 to land the blow with the swing. */
    public static int hitTicks(Mob attacker) {
        if (attacker instanceof KeyframeAnimated)
            return 0;
        if (attacker instanceof MossbackBisonUnit)
            return BISON;
        if (attacker instanceof DawnPaladinUnit)
            return CHOP;
        if (attacker instanceof AbstractIllager || attacker instanceof Zombie || attacker instanceof AbstractPiglin
            || attacker instanceof AbstractSkeleton || attacker instanceof EnderMan)
            return SWING;
        return 0;
    }
}
