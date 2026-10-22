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
 * @param reserved  areas the fill must leave alone (the hero jungle, lovish Oct 21 21:47: "reserve jungle areas
 *                  later"). {@link ReservedZones#NONE} today: nothing is reserved, nothing reads differently.
 */
public record MetalOptions(float richness, int rimInset, ReservedZones reserved) {

    /** rimInset meaning "derive it": the extractor clearance. */
    public static final int AUTO_RIM = 0;
    public static final float MIN_RICHNESS = 0.3f, MAX_RICHNESS = 2.5f;

    /** The map every build drew before these options existed. */
    public static final MetalOptions DEFAULT = new MetalOptions(1f, AUTO_RIM, ReservedZones.NONE);

    public MetalOptions {
        richness = Math.max(MIN_RICHNESS, Math.min(MAX_RICHNESS, richness));
        rimInset = Math.max(AUTO_RIM, rimInset);
        if (reserved == null)
            reserved = ReservedZones.NONE;
    }

    public static MetalOptions ofRichness(float richness) {
        return new MetalOptions(richness, AUTO_RIM, ReservedZones.NONE);
    }

    /** Blocks from the arena's edge inside which the fill places nothing: the extractor clearance or the larger value asked for. */
    @Override
    public int rimInset() {
        return Math.max(ExtractorClearance.blocks(), rimInset);
    }

    public MetalOptions withRimInset(int inset) {
        return new MetalOptions(richness, inset, reserved);
    }

    public MetalOptions withReserved(ReservedZones zones) {
        return new MetalOptions(richness, rimInset, zones);
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
