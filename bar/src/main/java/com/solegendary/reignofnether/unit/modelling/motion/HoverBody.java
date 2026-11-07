package com.solegendary.reignofnether.unit.modelling.motion;

/**
 * A body that hovers (the Light Grid's drones, design/ai_faction.md): its death is not a man's topple over the feet
 * but a drop from its hover height and a crash ({@link DeathFall#poseHover}). A generated rig (SplitPasses) whose
 * model implements this gets that death from {@link BoneMaps} - and with it the kept body ({@link Corpses}) and its
 * faction's finish - like every jointed human, with no code of its own.
 */
public interface HoverBody {
    /** The hull's bottom above the feet (the ground) at rest, model px: how far the death drops it. */
    float hoverPx();

    /** The hull's half width, model px: how far a tipped hull's low edge reaches below its centre. */
    float hullHalfPx();
}
