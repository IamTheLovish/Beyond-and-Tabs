package com.solegendary.reignofnether.environment.edge;

import com.solegendary.reignofnether.fogofwar.FogTintingBlockColor;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The baked fringe quads of one top height (1 for dirt / sand / gravel, 15/16 for dirt_path and farmland): every
 * piece x rotation x variant baked once per resource reload, and the {@link ModelData} of each overlay key (its quad
 * list) built the first time a block asks for that key and shared after - the chunk mesher only looks things up.
 * Created with the wrappers (ModifyBakingResult) and filled at BakingCompleted (EdgeOverlayModelEvents): the block
 * atlas is uploaded by then, so the quads carry the current atlas's sprites; until then {@link #ready} is false and
 * the blocks draw no fringe.
 */
final class EdgeOverlayQuads {

    /** The overlay quads a block draws on its top in the cutout layer; absent = none. */
    static final ModelProperty<List<BakedQuad>> OVERLAY = new ModelProperty<>();

    private static final String[][] SPRITES = {
            {"edge_a", "edge_b", "edge_c"},
            {"outer_a", "outer_b"},
            {"inner_a", "inner_b"},
    };

    // [piece][rot][variant]
    private final BakedQuad[][][] quads = new BakedQuad[EdgeOverlayRules.PIECES][4][];
    private final ConcurrentHashMap<Integer, ModelData> byKey = new ConcurrentHashMap<>();
    private final float top;
    private volatile boolean ready = false;

    EdgeOverlayQuads(float top) {
        this.top = top;
    }

    boolean ready() {
        return ready;
    }

    /** Bakes every piece; sprite: "edge_a" -> its sprite in the block atlas. */
    void bake(Function<String, TextureAtlasSprite> sprite) {
        for (int piece = 0; piece < EdgeOverlayRules.PIECES; piece++) {
            for (int rot = 0; rot < 4; rot++) {
                int vs = EdgeOverlayRules.variants(piece);
                quads[piece][rot] = new BakedQuad[vs];
                for (int v = 0; v < vs; v++)
                    quads[piece][rot][v] = bake(piece, rot, sprite.apply(SPRITES[piece][v]), top);
            }
        }
        byKey.clear();
        ready = true;
    }

    /** The model data of a non-zero overlay key (cached: one instance per key). */
    ModelData data(int key) {
        ModelData d = byKey.get(key);
        if (d != null) return d;
        int[] pieces = EdgeOverlayRules.pieces(key);
        BakedQuad[] list = new BakedQuad[pieces.length];
        for (int i = 0; i < pieces.length; i++) {
            int p = pieces[i];
            list[i] = quads[EdgeOverlayRules.piece(p)][EdgeOverlayRules.rot(p)][EdgeOverlayRules.variant(p)];
        }
        d = ModelData.builder().with(OVERLAY, List.of(list)).build();
        ModelData prev = byKey.putIfAbsent(key, d);
        return prev != null ? prev : d;
    }

    /**
     * One up-facing quad in the BLOCK vertex format (position, colour, uv, light, normal: 8 ints a vertex). Tint index
     * {@link FogTintingBlockColor#EDGE_GRASS_TINT_INDEX}: the grass colour at the block, fog included; shade on, like
     * the top face it lies on.
     */
    private static BakedQuad bake(int piece, int rot, TextureAtlasSprite sprite, float top) {
        float[] q = EdgeOverlayRules.quad(piece, rot);
        float y = top + EdgeOverlayRules.lift(rot + (piece == EdgeOverlayRules.PIECE_EDGE ? 0 : 4));
        int[] v = new int[32];
        for (int i = 0; i < 4; i++) {
            int o = i * 8;
            v[o] = Float.floatToRawIntBits(q[i * 4]);
            v[o + 1] = Float.floatToRawIntBits(y);
            v[o + 2] = Float.floatToRawIntBits(q[i * 4 + 1]);
            v[o + 3] = -1;                                              // white: the tint colours it
            v[o + 4] = Float.floatToRawIntBits(sprite.getU(q[i * 4 + 2] * 16f));
            v[o + 5] = Float.floatToRawIntBits(sprite.getV(q[i * 4 + 3] * 16f));
            v[o + 6] = 0;                                               // light: filled by the lighter
            v[o + 7] = 0x7F << 8;                                       // normal (0, 1, 0)
        }
        return new BakedQuad(v, FogTintingBlockColor.EDGE_GRASS_TINT_INDEX, Direction.UP, sprite, true);
    }
}
