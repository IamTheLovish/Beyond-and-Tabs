package com.solegendary.reignofnether.maps.gen;

/**
 * A generated map's lobby options ride in the top byte of the map seed (MatchSetup.mapSeed), so the lobby, the
 * skirmish packet, the world save and the preview carry them with no new field - and a seed written in a bug report
 * names the whole map. The low 56 bits are the generator's seed; the lobby shows their last four hex digits.
 * <p>
 * Top byte: bit 7 set = options present (a seed without it - an old save, seed 0, a random long from another map -
 * plays {@link GenOptions#DEFAULT}); bits 0-1 the shape (any, round, square); bits 2-3 the modifiers (on, off,
 * surprise).
 */
public final class GenSeed {
    private GenSeed() { }

    static final long LOW = 0x00FFFFFFFFFFFFFFL;
    static final int SHIFT = 56;
    static final long PRESENT = 0x80L;

    /** Lobby order of the shape setting. */
    public static final GenOptions.Shape[] SHAPES = { GenOptions.Shape.ANY, GenOptions.Shape.ROUND, GenOptions.Shape.SQUARE };
    /** Lobby order of the modifiers setting (on is the default). */
    public static final GenOptions.Modifiers[] MODIFIERS = { GenOptions.Modifiers.ON, GenOptions.Modifiers.OFF,
        GenOptions.Modifiers.SURPRISE };

    /** The seed with these lobby choices (indices into SHAPES and MODIFIERS) packed into its top byte. */
    public static long pack(long seed, int shape, int modifiers) {
        long opt = PRESENT | (Math.floorMod(shape, SHAPES.length) & 3) | ((long) (Math.floorMod(modifiers, MODIFIERS.length) & 3) << 2);
        return (seed & LOW) | (opt << SHIFT);
    }

    /** The generator's own seed: the low 56 bits (the options never re-roll the landscape on their own). */
    public static long generatorSeed(long packed) {
        return packed & LOW;
    }

    public static int shapeIndex(long packed) {
        long top = packed >>> SHIFT;
        return (top & PRESENT) == 0 ? 0 : (int) Math.min(SHAPES.length - 1, top & 3);
    }

    public static int modifiersIndex(long packed) {
        long top = packed >>> SHIFT;
        return (top & PRESENT) == 0 ? 0 : (int) Math.min(MODIFIERS.length - 1, (top >> 2) & 3);
    }

    /** The options a packed seed asks for: the lobby's shape and modifiers on the default balancer and tolerance. */
    public static GenOptions options(long packed) {
        return GenOptions.DEFAULT.withShape(SHAPES[shapeIndex(packed)]).withModifiers(MODIFIERS[modifiersIndex(packed)]);
    }
}
