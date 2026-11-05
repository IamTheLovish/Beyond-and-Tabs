package com.solegendary.reignofnether.environment.lawn;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The sheet's sprites and the remapped grass-top quads of one resource reload, shared by every wrapped grass model.
 * Created with the wrappers (ModifyBakingResult) and filled at BakingCompleted (GiantLawnModelEvents), when the block
 * atlas is uploaded; until then, and for a pack without the sheet (any of grass_block_top_g1..g64 missing from the atlas, a 4x4-sheet pack included),
 * {@link #ready} is false and the lawn draws its own tops.
 * <p>
 * The cache is keyed by the base model's quad LIST (a SimpleBakedModel hands out the same list every call, the
 * weighted grass model one of its six variants'): for each list with a lawn top, its CELLS (64) copies with that quad on each
 * cell's sprite, built once - the chunk mesher only looks them up. BakingCompleted warms it for every variant; a list
 * met later (another mod's model under ours) is built on first sight, and a model that makes new quads every call is
 * served uncached past {@link #MAX_LISTS} so the cache cannot grow without bound.
 */
final class GiantLawnQuads {

    /** The sheet cell (1..CELLS) this block's top shows; absent = the block draws its own top. */
    static final ModelProperty<Integer> CELL = new ModelProperty<>();

    static final int MAX_LISTS = 1024;

    private final ModelData[] cellData = new ModelData[GiantLawnCells.CELLS + 1];
    private final ConcurrentHashMap<List<BakedQuad>, List<BakedQuad>[]> lists = new ConcurrentHashMap<>();
    // index 1..CELLS; null = no sheet in this pack
    private volatile TextureAtlasSprite[] sheet = null;

    GiantLawnQuads() {
        for (int c = 1; c <= GiantLawnCells.CELLS; c++)
            cellData[c] = ModelData.builder().with(CELL, c).build();
    }

    boolean ready() {
        return sheet != null;
    }

    /** The shared model data of a cell (one instance per cell). */
    ModelData data(int cell) {
        return cellData[cell];
    }

    /** Looks the sheet's sprites up in the uploaded atlas; stays disabled when any is the missing sprite. Returns
     * whether the sheet is on. */
    boolean bake(Function<ResourceLocation, TextureAtlasSprite> atlas) {
        TextureAtlasSprite[] s = new TextureAtlasSprite[GiantLawnCells.CELLS + 1];
        for (int c = 1; c <= GiantLawnCells.CELLS; c++) {
            s[c] = atlas.apply(new ResourceLocation("minecraft", GiantLawnCells.sheetSprite(c)));
            if (s[c] == null || s[c].contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
                lists.clear();
                sheet = null;
                return false;
            }
        }
        lists.clear();
        sheet = s;
        return true;
    }

    private static boolean isLawnTop(BakedQuad q) {
        if (q.getDirection() != Direction.UP)
            return false;
        ResourceLocation name = q.getSprite().contents().name();
        return name.getNamespace().equals("minecraft") && GiantLawnCells.isLawnTop(name.getPath());
    }

    /** in with its lawn-top quads on the cell's sprite; in itself when it has none or the sheet is off. */
    List<BakedQuad> swap(List<BakedQuad> in, int cell) {
        TextureAtlasSprite[] s = sheet;
        if (s == null || in.isEmpty() || cell < 1 || cell > GiantLawnCells.CELLS)
            return in;
        boolean any = false;
        for (int i = 0; i < in.size(); i++)
            if (isLawnTop(in.get(i))) { any = true; break; }
        if (!any)
            return in;
        List<BakedQuad>[] cached = lists.get(in);
        if (cached == null) {
            cached = build(in, s);
            if (lists.size() < MAX_LISTS) {
                List<BakedQuad>[] prev = lists.putIfAbsent(List.copyOf(in), cached);
                if (prev != null) cached = prev;
            }
        }
        return cached[cell];
    }

    @SuppressWarnings("unchecked")
    private static List<BakedQuad>[] build(List<BakedQuad> in, TextureAtlasSprite[] s) {
        List<BakedQuad>[] out = new List[GiantLawnCells.CELLS + 1];
        for (int c = 1; c <= GiantLawnCells.CELLS; c++) {
            List<BakedQuad> l = new ArrayList<>(in.size());
            for (BakedQuad q : in)
                l.add(isLawnTop(q) ? onSprite(q, s[c]) : q);
            out[c] = List.copyOf(l);
        }
        return out;
    }

    /** The same quad - vertices, tint index, direction, shade - with its UVs moved from its sprite onto to. */
    private static BakedQuad onSprite(BakedQuad q, TextureAtlasSprite to) {
        TextureAtlasSprite from = q.getSprite();
        int[] v = GiantLawnCells.remapUVs(q.getVertices(),
                from.getU0(), from.getU1(), from.getV0(), from.getV1(),
                to.getU0(), to.getU1(), to.getV0(), to.getV1());
        if (v == null)
            return q;
        // the 5-argument constructor keeps ambient occlusion on, as the vanilla grass_block model has it
        return new BakedQuad(v, q.getTintIndex(), q.getDirection(), to, q.isShade());
    }
}
