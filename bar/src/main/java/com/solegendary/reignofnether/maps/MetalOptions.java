package com.solegendary.reignofnether.maps;

/**
 * The knobs of a pre-made map's open-ground metal (design/maps.md, maps spec M1). Pure data, so the lobby preview, the
 * server's build and the game tests all ask {@link MapLayout#build(MapPreset, int[], long, MetalOptions)} the same
 * question and get the same patches.
 *
 * @param richness  the lobby's metal richness (0.65 lean, 1.0 normal, 1.5 rich): scales how many open-ground patches
 *                  the fill places per slot. 1.0 is the map every build drew before the setting reached pre-made maps.
 * @param rimInset  how far inside the arena's edge the open-ground fill may place a patch. {@link #DEFAULT_RIM_INSET}
 *                  keeps the old limit (a bare band round the wall, lovish Oct 21 20:55); a smaller value lets a
 *                  border band fill, down to {@link #MIN_RIM_INSET} (the patch pad and the wall's walkway). Off by
 *                  default until lovish says which "edgeline" he means.
 * @param reserved  areas the fill must leave alone (the hero jungle, lovish Oct 21 21:47: "reserve jungle areas
 *                  later"). {@link ReservedZones#NONE} today: nothing is reserved, nothing reads differently.
 */
public record MetalOptions(float richness, int rimInset, ReservedZones reserved) {

    public static final int DEFAULT_RIM_INSET = 22, MIN_RIM_INSET = 12;
    public static final float MIN_RICHNESS = 0.3f, MAX_RICHNESS = 2.5f;

    /** The map every build drew before these options existed. */
    public static final MetalOptions DEFAULT = new MetalOptions(1f, DEFAULT_RIM_INSET, ReservedZones.NONE);

    public MetalOptions {
        richness = Math.max(MIN_RICHNESS, Math.min(MAX_RICHNESS, richness));
        rimInset = Math.max(MIN_RIM_INSET, rimInset);
        if (reserved == null)
            reserved = ReservedZones.NONE;
    }

    public static MetalOptions ofRichness(float richness) {
        return new MetalOptions(richness, DEFAULT_RIM_INSET, ReservedZones.NONE);
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
