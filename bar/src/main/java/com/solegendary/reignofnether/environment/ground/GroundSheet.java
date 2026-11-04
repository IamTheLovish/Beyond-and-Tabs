package com.solegendary.reignofnether.environment.ground;

import com.solegendary.reignofnether.ReignOfNether;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * One ground block's sheet in one resource reload (a row of GroundSheetRules.TABLE): its sprites - every complete
 * variant's, round 64 - and its swapped top quads, shared by every block-state model of that block. Created with the
 * wrappers (ModifyBakingResult) and filled at BakingCompleted (GiantGroundModelEvents), when the block atlas is
 * uploaded; until then, and for a pack without a whole sheet ({@code <top>_g1..4 / 16 / 64}), {@link #size} is 0 and
 * the block draws its own top.
 * <p>
 * A block's top shows one SLOT (GroundSheetRules.slot: its N x N square's hashed variant, then its cell). The cache is
 * keyed by the base model's quad LIST (a SimpleBakedModel hands out the same list every call, a weighted model one of
 * its variants'): for each list with a top quad, its K*N*N copies with that quad on each slot's sprite, built once -
 * the chunk mesher only looks them up. BakingCompleted warms it for every variant; a list met later
 * (another mod's model under ours) is built on first sight, and a model that makes new quads every call is served
 * uncached past {@link #MAX_LISTS} so the cache cannot grow without bound.
 */
final class GroundSheet {

    static final int MAX_LISTS = 1024;

    /** This reload's sheet: blocks per side, the complete variants (their numbers) and the sprites by slot (index
     * 1..variants*size*size: variant index, then cell). */
    private record Baked(int size, int[] variants, TextureAtlasSprite[] sprites) {
    }

    // the partial variant sets already reported (top#k), so a pack being drawn logs each once, not every reload
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

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

    /** The complete sheet variants of this reload (variant numbers, 1 first); empty = no sheet. */
    int[] variants() {
        Baked b = baked;
        return b == null ? new int[0] : b.variants.clone();
    }

    /** The slot (1..K*N*N) of the block at (x, z); 0 when there is no sheet. */
    int slot(int x, int z) {
        Baked b = baked;
        return b == null ? 0 : GroundSheetRules.slot(x, z, b.size, b.variants.length);
    }

    /** The sheet is in the pack and its client option is on. */
    boolean on() {
        return baked != null && option.getAsBoolean();
    }

    private static boolean isMissing(TextureAtlasSprite s) {
        return s == null || s.contents().name().equals(MissingTextureAtlasSprite.getLocation());
    }

    /** Looks the sheet up in the uploaded atlas: on when the pack has a whole 2x2, 4x4 or 8x8 sheet (see
     * GroundSheetRules.sheetSize), off otherwise; then its complete variants (GroundSheetRules.variants - a partial
     * set is left out and reported once). Returns the blocks per side, 0 = off. */
    int bake(Function<ResourceLocation, TextureAtlasSprite> atlas) {
        lists.clear();
        Function<String, TextureAtlasSprite> sprite = path -> atlas.apply(new ResourceLocation("minecraft", path));
        int n = GroundSheetRules.sheetSize(entry.top(), path -> !isMissing(sprite.apply(path)));
        if (n == 0) {
            baked = null;
            return 0;
        }
        GroundSheetRules.Variants v = GroundSheetRules.variants(entry.top(), n, path -> !isMissing(sprite.apply(path)));
        for (int k : v.partial())
            if (REPORTED.add(entry.top() + "#" + k))
                ReignOfNether.LOGGER.warn("[GiantGround] {}: sheet variant {} ignored - not exactly the {} sprites {} .. {}",
                        entry.top(), k, n * n, GroundSheetRules.variantSprite(entry.top(), k, 1),
                        GroundSheetRules.variantSprite(entry.top(), k, n * n));
        int[] variants = v.complete();
        TextureAtlasSprite[] s = new TextureAtlasSprite[variants.length * n * n + 1];
        for (int i = 0; i < variants.length; i++)
            for (int c = 1; c <= n * n; c++)
                s[1 + i * n * n + (c - 1)] = sprite.apply(GroundSheetRules.variantSprite(entry.top(), variants[i], c));
        baked = new Baked(n, variants, s);
        return n;
    }

    private boolean isTop(BakedQuad q) {
        if (q.getDirection() != Direction.UP)
            return false;
        ResourceLocation name = q.getSprite().contents().name();
        return name.getNamespace().equals("minecraft") && GroundSheetRules.isTop(entry.top(), name.getPath());
    }

    /** in with its top quads on the slot's sprite; in itself when it has none, the sheet is off or the slot is not
     * this sheet's (a mesh built across a reload that changed the size). */
    List<BakedQuad> swap(List<BakedQuad> in, int slot) {
        Baked b = baked;
        if (b == null || in.isEmpty() || slot < 1 || slot >= b.sprites.length)
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
        return cached[slot];
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
