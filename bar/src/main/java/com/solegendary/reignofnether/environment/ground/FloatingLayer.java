package com.solegendary.reignofnether.environment.ground;

import com.solegendary.reignofnether.fogofwar.FogTintingBlockColor;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;

/**
 * One floating layer over the lawn top in one resource reload - the clover (CloverLayerRules) or the flowers
 * (FlowerLayerRules): its baked quads by pick, built once at BakingCompleted so the chunk mesher only looks them up
 * (TopLayers appends them to the top's list). Off - and every block draws as before - while the pack lacks any of the
 * layer's sprites.
 * <p>
 * Each quad: shade off and ambient occlusion off (a layer floating over the top must not darken in the corners like
 * the ground under it; Optimum Realism's clover does the same), direction UP so it travels on the top face's cull list.
 * The tint differs:
 * <ul>
 *   <li><b>Clover:</b> tint index 0, the biome grass colour (through GrassTint and the fog wrapper, which leaves tinted
 *   quads as they are) - pale sprites the tint turns bright green.</li>
 *   <li><b>Flowers:</b> untinted, so white and yellow stay white and yellow. An untinted quad (index -1) on any of our
 *   blocks becomes FogTintingBlockColor.FOG_ONLY_TINT_INDEX in the fog wrapper (FogTintingBakedModel): fog of war
 *   only, never the block's own colour - EmbeddiumFogOnlyQuadsMixin keeps Embeddium's grass blending off it. The
 *   flowers carry that index from the start, because the wrapper's rewrite (BakedQuad's 5-argument constructor)
 *   would force ambient occlusion back on and allocate a new quad list for every flowered block; the colour is
 *   exactly what -1 would get.</li>
 * </ul>
 */
final class FloatingLayer {

    /** The tint index of the untinted layer (see the class comment). */
    static final int UNTINTED = FogTintingBlockColor.FOG_ONLY_TINT_INDEX;

    /** Makes a pick's vertices on the sprite's [u0, u1] x [v0, v1]. */
    @FunctionalInterface
    interface Shape {
        int[] vertices(int pick, float u0, float u1, float v0, float v1);
    }

    private final String name;
    final int picks;
    private final Predicate<Predicate<String>> ready;
    private final IntFunction<String> spriteOfPick;
    private final Shape shape;
    private final int tintIndex;
    // index 1..picks; null = off
    private volatile BakedQuad[] quads = null;

    private FloatingLayer(String name, int picks, Predicate<Predicate<String>> ready, IntFunction<String> spriteOfPick,
                          Shape shape, int tintIndex) {
        this.name = name;
        this.picks = picks;
        this.ready = ready;
        this.spriteOfPick = spriteOfPick;
        this.shape = shape;
        this.tintIndex = tintIndex;
    }

    /** The clover: 8 sheets x 4 turns, biome-tinted, at 16.6/16. */
    static FloatingLayer clover() {
        return new FloatingLayer("clover layer", CloverLayerRules.PICKS, CloverLayerRules::ready,
                p -> CloverLayerRules.sprite(CloverLayerRules.layer(p)),
                (p, u0, u1, v0, v1) -> CloverLayerRules.quad(CloverLayerRules.rotation(p), u0, u1, v0, v1), 0);
    }

    /** The flowers: 8 sprites, never turned, untinted, at 16.7/16. */
    static FloatingLayer flowers() {
        return new FloatingLayer("flower layer", FlowerLayerRules.LAYERS, FlowerLayerRules::ready,
                FlowerLayerRules::sprite, (p, u0, u1, v0, v1) -> FlowerLayerRules.quad(u0, u1, v0, v1), UNTINTED);
    }

    String name() {
        return name;
    }

    boolean ready() {
        return quads != null;
    }

    private static boolean isMissing(TextureAtlasSprite s) {
        return s == null || s.contents().name().equals(MissingTextureAtlasSprite.getLocation());
    }

    /** Bakes the quads from the uploaded atlas; stays off when a sprite is missing. Returns whether it is on. */
    boolean bake(Function<ResourceLocation, TextureAtlasSprite> atlas) {
        Function<String, TextureAtlasSprite> sprite = path -> atlas.apply(new ResourceLocation("minecraft", path));
        if (!ready.test(path -> !isMissing(sprite.apply(path)))) {
            quads = null;
            return false;
        }
        BakedQuad[] q = new BakedQuad[picks + 1];
        for (int p = 1; p <= picks; p++) {
            TextureAtlasSprite s = sprite.apply(spriteOfPick.apply(p));
            q[p] = new BakedQuad(shape.vertices(p, s.getU0(), s.getU1(), s.getV0(), s.getV1()), tintIndex,
                    Direction.UP, s, false, false);
        }
        quads = q;
        return true;
    }

    /** The pick's quad; null for 0, a pick out of range, or when the layer is off. */
    BakedQuad quad(int pick) {
        BakedQuad[] q = quads;
        return q == null || pick < 1 || pick > picks ? null : q[pick];
    }
}
