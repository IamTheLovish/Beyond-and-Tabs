package com.solegendary.reignofnether.maps;

/**
 * The knobs of a pre-made map's open-ground metal (design/maps.md, maps spec M1). Pure data, so the lobby preview, the
 * server's build and the game tests all ask {@link MapLayout#build(MapPreset, int[], long, MetalOptions)} the same
 * question and get the same patches.
 *
 * @param richness  the lobby's metal richness (0.65 lean, 1.0 normal, 1.5 rich): scales how many open-ground patches
 *                  the fill places per slot. 1.0 is the map every build drew before the setting reached pre-made maps.
 * @param rimInset  how far inside the arena's edge the open-ground fill may place a patch. {@link #AUTO_RIM} (the
 *                  default) is {@link ExtractorClearance#blocks()}: close enough to the wall that the rim fills (lovish
 *                  Oct 21 20:55, and Oct 22 "enough blocks away that the extractor doesn't get blocked by the wall"), far
 *                  enough that the biggest extractor of any faction and tier is always buildable. A larger value keeps
 *                  the fill further in; it never goes below the clearance (see {@link #rimInset()}).
 * @param extra     the lobby's "extra metal" level (low / normal / high): how much metal there is beyond the home set and
 *                  the core fill - the extra patches spread over the whole map and the outskirts bonus. Never touches the
 *                  home metal ({@link HomeMetal}). {@link Extra#NORMAL} by default; tune it from the bot ladder.
 * @param reserved  areas the fill must leave alone (the hero jungle, lovish Oct 21 21:47: "reserve jungle areas
 *                  later"). {@link ReservedZones#NONE} today: nothing is reserved, nothing reads differently.
 */
public record MetalOptions(float richness, int rimInset, Extra extra, ReservedZones reserved) {

    /** rimInset meaning "derive it": the extractor clearance. */
    public static final int AUTO_RIM = 0;
    public static final float MIN_RICHNESS = 0.3f, MAX_RICHNESS = 2.5f;

    /** The map every build drew before these options existed. */
    public static final MetalOptions DEFAULT = new MetalOptions(1f, AUTO_RIM, Extra.NORMAL, ReservedZones.NONE);

    public MetalOptions {
        richness = Math.max(MIN_RICHNESS, Math.min(MAX_RICHNESS, richness));
        rimInset = Math.max(AUTO_RIM, rimInset);
        if (extra == null)
            extra = Extra.NORMAL;
        if (reserved == null)
            reserved = ReservedZones.NONE;
    }

    public static MetalOptions ofRichness(float richness) {
        return new MetalOptions(richness, AUTO_RIM, Extra.NORMAL, ReservedZones.NONE);
    }

    /** Blocks from the arena's edge inside which the fill places nothing: the extractor clearance or the larger value asked for. */
    @Override
    public int rimInset() {
        return Math.max(ExtractorClearance.blocks(), rimInset);
    }

    public MetalOptions withRimInset(int inset) {
        return new MetalOptions(richness, inset, extra, reserved);
    }

    public MetalOptions withExtra(Extra level) {
        return new MetalOptions(richness, rimInset, level, reserved);
    }

    /**
     * How much extra metal a map carries (lovish Oct 22: more mexes to contest, a big late-game economy; massive armies
     * are wanted, the pacing comes from the fights). {@code count} and {@code premium} scale two things and nothing else: the number of
     * extra patches (the rim fill and the whole-map spread, see MapLayout.spread) and the premium a patch pays for
     * standing far from the bases and near the rim. NONE is the map before extra metal existed (the game tests compare
     * against it); the lobby offers LOW, NORMAL, HIGH.
     */
    public enum Extra {
        NONE(0f, 0f), LOW(0.5f, 0.5f), NORMAL(1f, 1f), HIGH(2f, 1.25f);

        /** Scales the number of extra patches / the premium a patch pays for distance and the rim. */
        public final float count, premium;

        Extra(float count, float premium) {
            this.count = count;
            this.premium = premium;
        }
    }

    public MetalOptions withReserved(ReservedZones zones) {
        return new MetalOptions(richness, rimInset, extra, zones);
    }

    /**
     * A mask of map areas no patch may stand in (centre-relative coordinates, as every MapLayout record). It is asked
     * for every slot's copy of a patch, so a mask that is not itself n-fold symmetric costs a spot in every slot alike
     * and the slots stay equal; a future zone only has to answer true.
     */
    @FunctionalInterface
    public interface ReservedZones {
        ReservedZones NONE = (dx, dz) -> false;

        boolean reserves(double dx, double dz);
    }
}
