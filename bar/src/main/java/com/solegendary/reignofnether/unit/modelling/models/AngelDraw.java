package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.unit.modelling.animations.AngelRig;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Draws one pass of an angel's body (the paint, the team mask, the glow) with the rig's fade
 * ({@link AngelRig#passFactors}): the body at the pose's alpha, the halo at its own halo alpha, the Mender's lantern
 * with the body and, in the glow pass, brightened by the heal. A translucent pass takes the factor as vertex alpha; the
 * additive glow pass (blend ONE, ONE: alpha has no effect) as colour. When every group draws alike - hovering, or the
 * halo fading with the body - it is one plain draw; otherwise each group draws on its own, the other parts skipping
 * their cubes (ModelPart.skipDraw, saved and put back, so a team or glow pass's own pruning is kept).
 */
@OnlyIn(Dist.CLIENT)
final class AngelDraw {

    /** One draw of the whole model at the given colour and alpha (the model's own super.renderToBuffer). */
    interface Draw {
        void draw(float r, float g, float b, float a);
    }

    private static final int REST = 0, HALO = 1, LANTERN = 2;
    private final ModelPart[] all;
    private final int[] group;
    private final boolean[] saved;
    private final double[] f = new double[4];

    AngelDraw(ModelPart root, @Nullable ModelPart halo, @Nullable ModelPart lantern) {
        List<ModelPart> parts = root.getAllParts().toList();
        Set<ModelPart> inHalo = new HashSet<>(), inLantern = new HashSet<>();
        if (halo != null)
            halo.getAllParts().forEach(inHalo::add);
        if (lantern != null)
            lantern.getAllParts().forEach(inLantern::add);
        all = parts.toArray(new ModelPart[0]);
        group = new int[all.length];
        saved = new boolean[all.length];
        for (int i = 0; i < all.length; i++)
            group[i] = inHalo.contains(all[i]) ? HALO : inLantern.contains(all[i]) ? LANTERN : REST;
    }

    void render(@Nullable AngelRig.Pose pose, boolean glowPass, float r, float g, float b, float a, Draw d) {
        if (pose == null) {
            d.draw(r, g, b, a);
            return;
        }
        AngelRig.passFactors(pose, glowPass, f);
        double body = f[AngelRig.F_BODY];
        if (Math.abs(f[AngelRig.F_HALO] - body) < 1e-3 && Math.abs(f[AngelRig.F_LANTERN] - body) < 1e-3
            && f[AngelRig.F_LANTERN_EXTRA] < 1e-3) {
            draw(d, glowPass, r, g, b, a, body);
            return;
        }
        only(REST, d, glowPass, r, g, b, a, body);
        only(HALO, d, glowPass, r, g, b, a, f[AngelRig.F_HALO]);
        only(LANTERN, d, glowPass, r, g, b, a, f[AngelRig.F_LANTERN]);
        if (f[AngelRig.F_LANTERN_EXTRA] > 1e-3)
            only(LANTERN, d, glowPass, r, g, b, a, f[AngelRig.F_LANTERN_EXTRA]);
    }

    private void only(int keep, Draw d, boolean glowPass, float r, float g, float b, float a, double factor) {
        boolean any = false;
        for (int i = 0; i < all.length; i++) {
            saved[i] = all[i].skipDraw;
            if (group[i] != keep)
                all[i].skipDraw = true;
            else
                any |= !saved[i];
        }
        try {
            if (any && factor > 1e-3)
                draw(d, glowPass, r, g, b, a, factor);
        } finally {
            for (int i = 0; i < all.length; i++)
                all[i].skipDraw = saved[i];
        }
    }

    private static void draw(Draw d, boolean glowPass, float r, float g, float b, float a, double factor) {
        float k = (float) factor;
        if (glowPass)
            d.draw(r * k, g * k, b * k, a);
        else
            d.draw(r, g, b, a * k);
    }
}
