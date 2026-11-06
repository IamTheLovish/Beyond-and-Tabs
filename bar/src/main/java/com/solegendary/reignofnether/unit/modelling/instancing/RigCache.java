package com.solegendary.reignofnether.unit.modelling.instancing;

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
 * The rigs, one per part tree a unit renderer draws (a renderer holds one model, so this is bounded by the renderers),
 * and what was learned about each: the parts seen moving. A folded part found away from its baked pose becomes a bone
 * and the tree is built again ({@link #promote}); a tree that keeps doing that past {@link #MAX_REBUILDS} is left to the
 * old path for good (its parts move too freely to be worth folding) - the [Instancing] line names it.
 * <p>
 * Held weakly by the root part (ModelPart has identity equality), and every part of a rig maps back to its entry, so a
 * call that draws one of its parts outside an instanced pass is recognised ({@link #memberOf}). Pure: no GL; a rebuilt
 * rig's GPU buffers are freed by the GL side from {@link #takeRetired}.
 */
@OnlyIn(Dist.CLIENT)
public final class RigCache {
    /** Builds after the first one before a tree is given up on. */
    public static final int MAX_REBUILDS = 12;

    public static final class Entry {
        public final ModelPart root;
        Rig rig;
        final Set<ModelPart> forced = Collections.newSetFromMap(new IdentityHashMap<>());
        int rebuilds = 0;
        boolean unstable = false;
        String why = null;

        Entry(ModelPart root) {
            this.root = root;
        }

        /** The current rig, or null when the tree is left to the old path. */
        public Rig rig() {
            return unstable ? null : rig;
        }

        public int rebuilds() {
            return rebuilds;
        }

        public String why() {
            return why;
        }
    }

    private final Map<ModelPart, Entry> byRoot = new WeakHashMap<>();
    private final Map<ModelPart, Entry> members = new WeakHashMap<>();
    private final List<Rig> retired = new ArrayList<>();

    /** The entry for a tree drawn from {@code root} (built on first use); null if {@code root} is inside another rig. */
    public Entry entry(ModelPart root) {
        Entry e = byRoot.get(root);
        if (e != null)
            return e;
        Entry owner = members.get(root);
        if (owner != null && owner.root != root)
            return null;   // a part of another rig drawn on its own: not a tree of its own
        e = new Entry(root);
        byRoot.put(root, e);
        build(e);
        return e;
    }

    private void build(Entry e) {
        try {
            Rig old = e.rig;
            e.rig = Rig.build(e.root, e.forced);
            if (old != null)
                retired.add(old);
            for (ModelPart p : e.rig.parts)
                members.put(p, e);
        } catch (RuntimeException ex) {
            e.unstable = true;
            e.why = "build failed: " + ex;
        }
    }

    /** Part {@code moved} of the entry's rig left its baked pose: make it a bone and build again. The new rig, or null. */
    public Rig promote(Entry e, ModelPart moved) {
        return promote(e, List.of(moved));
    }

    /** Parts {@code moved} left their baked poses: make them bones, one build for them all. The new rig, or null. */
    public Rig promote(Entry e, List<ModelPart> moved) {
        if (e.unstable)
            return null;
        e.forced.addAll(moved);
        if (++e.rebuilds > MAX_REBUILDS) {
            e.unstable = true;
            e.why = "parts kept moving (" + e.forced.size() + " bones learned)";
            if (e.rig != null)
                retired.add(e.rig);
            e.rig = null;
            return null;
        }
        build(e);
        return e.rig();
    }

    /** The entry whose tree holds {@code part}, or null. */
    public Entry memberOf(ModelPart part) {
        return members.get(part);
    }

    /** Rigs replaced since the last call (their GPU buffers can go once the frame that used them is drawn). */
    public List<Rig> takeRetired() {
        if (retired.isEmpty())
            return Collections.emptyList();
        List<Rig> out = new ArrayList<>(retired);
        retired.clear();
        return out;
    }

    /** Every entry (the [Instancing] line lists the trees given up on). */
    public List<Entry> entries() {
        return new ArrayList<>(byRoot.values());
    }
}
