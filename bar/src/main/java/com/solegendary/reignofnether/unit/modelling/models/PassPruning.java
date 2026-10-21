package com.solegendary.reignofnether.unit.modelling.models;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The team and glow passes of a generated body draw a handful of parts, but vanilla still walks the whole tree for
 * them: ModelPart.render pushes a pose (two matrix copies), translates and rotates, and pops, for every one of the
 * 240-650 parts, whether or not it draws (SplitPasses only skips the cubes). PR #84's bench: the Halberdier's team
 * pass drew 1104 vertices a unit and cost 11.3 ms per 500 units, its body pass 5736 vertices and 17.4 ms - the walk,
 * not the vertices, is the cost, and the second and third pass paid it again.
 *
 * This hides, for the length of a pass, every subtree that holds none of the pass's parts: a part outside the keep
 * list whose parent is on a kept part's path to the root is made invisible (ModelPart.render returns before the push
 * for an invisible part), and its own visibility is put back after. The parts to hide are worked out once per
 * (model, keep list) - the tree's parentage is read from {@link ModelPart#getAllParts()} alone (each part's own
 * descendant stream), so this needs no mixin and runs in the CI bench - and held weakly by the model. What is drawn
 * is identical: by construction a keep list names every part with texels in the pass's texture, so a cut subtree
 * has nothing to draw.
 */
@OnlyIn(Dist.CLIENT)
public final class PassPruning {
    private PassPruning() { }

    /** The subtree roots a pass hides, and their visibility before the pass. */
    static final class Cut {
        final ModelPart[] parts;
        final boolean[] saved;

        Cut(List<ModelPart> parts) {
            this.parts = parts.toArray(new ModelPart[0]);
            this.saved = new boolean[this.parts.length];
        }

        void apply() {
            for (int i = 0; i < parts.length; i++) {
                saved[i] = parts[i].visible;
                parts[i].visible = false;
            }
        }

        void restore() {
            for (int i = 0; i < parts.length; i++)
                parts[i].visible = saved[i];
        }
    }

    private static final Map<Object, Map<List<ModelPart>, Cut>> CUTS = new WeakHashMap<>();

    /** Hide every subtree the pass does not need; pair with {@link #restore} in a finally. */
    public static void cut(SplitPasses split, List<ModelPart> keep) {
        Cut cut = cutFor(split, keep);
        if (cut != null)
            cut.apply();
    }

    public static void restore(SplitPasses split, List<ModelPart> keep) {
        Cut cut = cutFor(split, keep);
        if (cut != null)
            cut.restore();
    }

    private static Cut cutFor(SplitPasses split, List<ModelPart> keep) {
        if (!(split instanceof HierarchicalModel<?> model))
            return null;
        Map<List<ModelPart>, Cut> perList = CUTS.get(split);
        if (perList == null) {
            perList = new IdentityHashMap<>(4);
            CUTS.put(split, perList);
        }
        Cut cut = perList.get(keep);
        if (cut == null) {
            cut = compute(model.root(), keep);
            perList.put(keep, cut);
        }
        return cut;
    }

    /**
     * The parts to hide for a keep list: every part not on a kept part's path to the root whose parent is. The
     * tree's parentage comes from each part's own descendant stream: a part's direct children are the descendants
     * no other descendant also lists. Once per model and list.
     */
    static Cut compute(ModelPart root, List<ModelPart> keep) {
        List<ModelPart> all = root.getAllParts().toList();
        Map<ModelPart, List<ModelPart>> descendants = new IdentityHashMap<>(all.size() * 2);
        for (ModelPart q : all)
            descendants.put(q, q.getAllParts().toList());   // q itself first, then its subtree
        Map<ModelPart, List<ModelPart>> children = new IdentityHashMap<>(all.size() * 2);
        Map<ModelPart, ModelPart> parent = new IdentityHashMap<>(all.size() * 2);
        for (ModelPart q : all) {
            List<ModelPart> desc = descendants.get(q);
            Set<ModelPart> covered = Collections.newSetFromMap(new IdentityHashMap<>());
            for (ModelPart r : desc)
                if (r != q)
                    for (ModelPart d : descendants.get(r))
                        if (d != r)
                            covered.add(d);
            List<ModelPart> direct = new ArrayList<>();
            for (ModelPart c : desc)
                if (c != q && !covered.contains(c))
                    direct.add(c);
            children.put(q, direct);
            for (ModelPart c : direct)
                parent.put(c, q);
        }
        Set<ModelPart> closure = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ModelPart k : keep) {
            ModelPart p = k;
            while (p != null && closure.add(p))   // up to the root, stopping where an earlier path already reached
                p = parent.get(p);
        }
        List<ModelPart> cut = new ArrayList<>();
        if (!closure.contains(root)) {
            cut.add(root);   // nothing to draw at all: hide the whole body for this pass
            return new Cut(cut);
        }
        for (ModelPart q : all) {
            if (!closure.contains(q))
                continue;
            for (ModelPart c : children.get(q))
                if (!closure.contains(c))
                    cut.add(c);
        }
        return new Cut(cut);
    }
}
