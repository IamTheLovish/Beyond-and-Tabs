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
 * Callers: {@code drawOnly(...)} right before the pass and {@code drawAll()} in a finally right after, so the paint
 * pass of the next unit always sees the whole body.
 */
@OnlyIn(Dist.CLIENT)
public interface SplitPasses {
    List<ModelPart> teamParts();

    List<ModelPart> glowParts();

    /** Every part skips its own cubes except {@code keep}. */
    void drawOnly(List<ModelPart> keep);

    /** Every part draws its cubes again (the normal state). */
    void drawAll();
}
