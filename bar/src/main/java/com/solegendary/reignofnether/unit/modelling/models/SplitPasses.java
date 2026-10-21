package com.solegendary.reignofnether.unit.modelling.models;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * A body drawn in several passes over the same geometry - the paint, then TeamColourLayer's team mask, then the
 * additive glow - that knows which of its parts carry texels in the second and third textures. A pass draws only
 * those parts' own cubes (ModelPart.skipDraw on the rest: their transforms still apply to their children, their
 * cubes write no vertices) - drawing the whole body again for a mask that is empty there was ~55% of a generated
 * body's vertices (tools/body_budget.py). Implemented by every model tools/dsl_to_game.py generates.
 *
 * Callers: {@link #beginPass} right before the pass and {@link #endPass} in a finally right after, so the paint
 * pass of the next unit always sees the whole body. Those two also hide the subtrees the pass has nothing to draw
 * in ({@link PassPruning}): vanilla walks every part it can see, drawing or not, and the walk - not the vertices -
 * was most of a pass's cost (PR #84's bench).
 */
@OnlyIn(Dist.CLIENT)
public interface SplitPasses {
    List<ModelPart> teamParts();

    List<ModelPart> glowParts();

    /** Every part skips its own cubes except {@code keep}. */
    void drawOnly(List<ModelPart> keep);

    /** Every part draws its cubes again (the normal state). */
    void drawAll();

    /** A pass over {@code keep} only: its subtrees hidden, the kept parts' ancestors drawn without their cubes. */
    default void beginPass(List<ModelPart> keep) {
        PassPruning.cut(this, keep);
        drawOnly(keep);
    }

    /** Back to the whole body: cubes on, the hidden subtrees back to what they were. */
    default void endPass(List<ModelPart> keep) {
        drawAll();
        PassPruning.restore(this, keep);
    }
}
