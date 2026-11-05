package com.solegendary.reignofnether.environment.ground;

import net.minecraft.client.renderer.block.model.BakedQuad;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * The lawn's floating layers in one resource reload - the clover (three blocks in four) and the flowers (~6%, in
 * drifts) - and the top lists with them appended. Keyed by the top's quad LIST (after GroundSheet's swap: one list per
 * base model variant and sheet slot), each with a lazily filled slot per (clover pick, flower pick) pair: built on
 * first use, then only looked up, so the chunk mesher allocates nothing per block. Past {@link #MAX_LISTS} keys (a
 * model that makes new lists every call) a list is built uncached so the cache cannot grow without bound.
 */
final class TopLayers {

    /** Keys: up to 4 grass models x 256 sheet slots (4 variants of an 8 x 8 sheet), with room to spare. */
    static final int MAX_LISTS = 4096;

    final FloatingLayer clover = FloatingLayer.clover();
    final FloatingLayer flowers = FloatingLayer.flowers();
    private final ConcurrentHashMap<List<BakedQuad>, AtomicReferenceArray<List<BakedQuad>>> with =
            new ConcurrentHashMap<>();

    /** Forgets the lists of the last reload (the sprites changed). */
    void clear() {
        with.clear();
    }

    private int index(int cloverPick, int flowerPick) {
        return cloverPick * (flowers.picks + 1) + flowerPick;
    }

    /** in with the clover pick's and the flower pick's quads appended (0 = none; a layer that is off adds nothing);
     * in itself when nothing is added. */
    List<BakedQuad> with(List<BakedQuad> in, int cloverPick, int flowerPick) {
        BakedQuad c = clover.quad(cloverPick);
        BakedQuad f = flowers.quad(flowerPick);
        if (c == null && f == null)
            return in;
        AtomicReferenceArray<List<BakedQuad>> slots = with.get(in);
        if (slots == null) {
            if (with.size() >= MAX_LISTS)
                return build(in, c, f);
            slots = new AtomicReferenceArray<>((clover.picks + 1) * (flowers.picks + 1));
            AtomicReferenceArray<List<BakedQuad>> prev = with.putIfAbsent(List.copyOf(in), slots);
            if (prev != null) slots = prev;
        }
        int i = index(c == null ? 0 : cloverPick, f == null ? 0 : flowerPick);
        List<BakedQuad> out = slots.get(i);
        if (out == null) {
            out = build(in, c, f);
            if (!slots.compareAndSet(i, null, out))
                out = slots.get(i);
        }
        return out;
    }

    /** The picks' quads alone (for a base model that draws nothing in the cutout layer); empty when none is on. */
    List<BakedQuad> only(int cloverPick, int flowerPick) {
        return with(List.of(), cloverPick, flowerPick);
    }

    private static List<BakedQuad> build(List<BakedQuad> in, BakedQuad c, BakedQuad f) {
        List<BakedQuad> l = new ArrayList<>(in.size() + 2);
        l.addAll(in);
        if (c != null) l.add(c);
        if (f != null) l.add(f);
        return List.copyOf(l);
    }
}
