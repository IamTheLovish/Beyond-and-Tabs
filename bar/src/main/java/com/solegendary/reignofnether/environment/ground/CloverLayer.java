package com.solegendary.reignofnether.environment.ground;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The floating clover layer's baked quads in one resource reload (CloverLayerRules): the 32 quads (8 sheets x 4
 * turns) and, per lawn top list, its 32 copies with one of them appended - built once, so the chunk mesher only looks
 * them up. Created with the wrappers and filled at BakingCompleted; off (and every block draws as before) while the pack
 * lacks any of {@code block/grass_clover_layer_1..8}.
 * <p>
 * Each quad: tint index 0 (the biome grass colour, through GrassTint and the fog wrapper, which leaves tinted quads as
 * they are), shade off and ambient occlusion off (a layer floating over the top must not darken in the corners like the
 * ground under it; Optimum Realism's clover does the same), direction UP so it travels on the top face's cull list.
 */
final class CloverLayer {

    static final int MAX_LISTS = 1024;

    private final ConcurrentHashMap<List<BakedQuad>, List<BakedQuad>[]> with = new ConcurrentHashMap<>();
    // index 1..PICKS; null = off
    private volatile BakedQuad[] quads = null;
    private volatile List<BakedQuad>[] only = null;

    boolean ready() {
        return quads != null;
    }

    private static boolean isMissing(TextureAtlasSprite s) {
        return s == null || s.contents().name().equals(MissingTextureAtlasSprite.getLocation());
    }

    /** Bakes the 32 quads from the uploaded atlas; stays off when a sheet is missing. Returns whether it is on. */
    @SuppressWarnings("unchecked")
    boolean bake(Function<ResourceLocation, TextureAtlasSprite> atlas) {
        with.clear();
        Function<String, TextureAtlasSprite> sprite = path -> atlas.apply(new ResourceLocation("minecraft", path));
        if (!CloverLayerRules.ready(path -> !isMissing(sprite.apply(path)))) {
            quads = null;
            only = null;
            return false;
        }
        BakedQuad[] q = new BakedQuad[CloverLayerRules.PICKS + 1];
        List<BakedQuad>[] o = new List[CloverLayerRules.PICKS + 1];
        for (int p = 1; p <= CloverLayerRules.PICKS; p++) {
            TextureAtlasSprite s = sprite.apply(CloverLayerRules.sprite(CloverLayerRules.layer(p)));
            int[] v = CloverLayerRules.quad(CloverLayerRules.rotation(p), s.getU0(), s.getU1(), s.getV0(), s.getV1());
            q[p] = new BakedQuad(v, 0, Direction.UP, s, false, false);
            o[p] = List.of(q[p]);
        }
        only = o;
        quads = q;
        return true;
    }

    /** The pick's quad alone (for a base model that draws nothing in the cutout layer); empty when off. */
    List<BakedQuad> only(int pick) {
        List<BakedQuad>[] o = only;
        return o == null || pick < 1 || pick > CloverLayerRules.PICKS ? List.of() : o[pick];
    }

    /** in with the pick's quad appended (cached per list); in itself when off. */
    List<BakedQuad> with(List<BakedQuad> in, int pick) {
        BakedQuad[] q = quads;
        if (q == null || pick < 1 || pick > CloverLayerRules.PICKS)
            return in;
        List<BakedQuad>[] cached = with.get(in);
        if (cached == null) {
            cached = build(in, q);
            if (with.size() < MAX_LISTS) {
                List<BakedQuad>[] prev = with.putIfAbsent(List.copyOf(in), cached);
                if (prev != null) cached = prev;
            }
        }
        return cached[pick];
    }

    @SuppressWarnings("unchecked")
    private static List<BakedQuad>[] build(List<BakedQuad> in, BakedQuad[] q) {
        List<BakedQuad>[] out = new List[CloverLayerRules.PICKS + 1];
        for (int p = 1; p <= CloverLayerRules.PICKS; p++) {
            List<BakedQuad> l = new ArrayList<>(in.size() + 1);
            l.addAll(in);
            l.add(q[p]);
            out[p] = List.copyOf(l);
        }
        return out;
    }
}
