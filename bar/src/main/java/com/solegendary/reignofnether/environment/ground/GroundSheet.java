package com.solegendary.reignofnether.environment.ground;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * One ground block's sheet in one resource reload (a row of GroundSheetRules.TABLE): its sprites and its swapped top
 * quads, shared by every block-state model of that block. Created with the wrappers (ModifyBakingResult) and filled at
 * BakingCompleted (GiantGroundModelEvents), when the block atlas is uploaded; until then, and for a pack without a
 * whole sheet ({@code <top>_g1..4 / 16 / 64}), {@link #size} is 0 and the block draws its own top.
 * <p>
 * The cache is keyed by the base model's quad LIST (a SimpleBakedModel hands out the same list every call, a weighted
 * model one of its variants'): for each list with a top quad, its N*N copies with that quad on each cell's sprite,
 * built once - the chunk mesher only looks them up. BakingCompleted warms it for every variant; a list met later
 * (another mod's model under ours) is built on first sight, and a model that makes new quads every call is served
 * uncached past {@link #MAX_LISTS} so the cache cannot grow without bound.
 */
final class GroundSheet {

    static final int MAX_LISTS = 1024;

    /** This reload's sheet: blocks per side and the sprites by cell (index 1..size*size). */
    private record Baked(int size, TextureAtlasSprite[] sprites) {
    }

    final GroundSheetRules.Entry entry;
    private final BooleanSupplier option;
    private final ConcurrentHashMap<List<BakedQuad>, List<BakedQuad>[]> lists = new ConcurrentHashMap<>();
    private volatile Baked baked = null;

    GroundSheet(GroundSheetRules.Entry entry, BooleanSupplier option) {
        this.entry = entry;
        this.option = option;
    }

    /** Blocks per side of this reload's sheet; 0 = no sheet in the pack (or not baked yet). */
    int size() {
        Baked b = baked;
        return b == null ? 0 : b.size;
    }

    /** The sheet is in the pack and its client option is on. */
    boolean on() {
        return baked != null && option.getAsBoolean();
    }

    private static boolean isMissing(TextureAtlasSprite s) {
        return s == null || s.contents().name().equals(MissingTextureAtlasSprite.getLocation());
    }

    /** Looks the sheet up in the uploaded atlas: on when the pack has a whole 2x2, 4x4 or 8x8 sheet (see
     * GroundSheetRules.sheetSize), off otherwise. Returns the blocks per side, 0 = off. */
    int bake(Function<ResourceLocation, TextureAtlasSprite> atlas) {
        lists.clear();
        Function<String, TextureAtlasSprite> sprite = path -> atlas.apply(new ResourceLocation("minecraft", path));
        int n = GroundSheetRules.sheetSize(entry.top(), path -> !isMissing(sprite.apply(path)));
        if (n == 0) {
            baked = null;
            return 0;
        }
        TextureAtlasSprite[] s = new TextureAtlasSprite[n * n + 1];
        for (int c = 1; c <= n * n; c++)
            s[c] = sprite.apply(GroundSheetRules.sheetSprite(entry.top(), c));
        baked = new Baked(n, s);
        return n;
    }

    private boolean isTop(BakedQuad q) {
        if (q.getDirection() != Direction.UP)
            return false;
        ResourceLocation name = q.getSprite().contents().name();
        return name.getNamespace().equals("minecraft") && GroundSheetRules.isTop(entry.top(), name.getPath());
    }

    /** in with its top quads on the cell's sprite; in itself when it has none, the sheet is off or the cell is not
     * this sheet's (a mesh built across a reload that changed the size). */
    List<BakedQuad> swap(List<BakedQuad> in, int cell) {
        Baked b = baked;
        if (b == null || in.isEmpty() || cell < 1 || cell > b.size * b.size)
            return in;
        boolean any = false;
        for (int i = 0; i < in.size(); i++)
            if (isTop(in.get(i))) { any = true; break; }
        if (!any)
            return in;
        List<BakedQuad>[] cached = lists.get(in);
        if (cached == null || cached.length != b.sprites.length) {
            cached = build(in, b.sprites);
            if (lists.size() < MAX_LISTS) {
                List<BakedQuad>[] prev = lists.putIfAbsent(List.copyOf(in), cached);
                if (prev != null && prev.length == cached.length) cached = prev;
            }
        }
        return cached[cell];
    }

    @SuppressWarnings("unchecked")
    private List<BakedQuad>[] build(List<BakedQuad> in, TextureAtlasSprite[] s) {
        List<BakedQuad>[] out = new List[s.length];
        for (int c = 1; c < s.length; c++) {
            List<BakedQuad> l = new ArrayList<>(in.size());
            for (BakedQuad q : in)
                l.add(isTop(q) ? onSprite(q, s[c]) : q);
            out[c] = List.copyOf(l);
        }
        return out;
    }

    /** The same quad - vertices, tint index, direction, shade, ambient occlusion - with its UVs laid by position on
     * to (GroundSheetRules.projectUp). */
    private static BakedQuad onSprite(BakedQuad q, TextureAtlasSprite to) {
        int[] v = GroundSheetRules.projectUp(q.getVertices(), to.uvShrinkRatio(),
                to.getU0(), to.getU1(), to.getV0(), to.getV1());
        if (v == null)
            return q;
        // the 6-argument constructor: the 5-argument one forces ambient occlusion on whatever the model said
        return new BakedQuad(v, q.getTintIndex(), q.getDirection(), to, q.isShade(), q.hasAmbientOcclusion());
    }
}
