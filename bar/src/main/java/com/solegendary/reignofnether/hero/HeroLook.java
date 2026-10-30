package com.solegendary.reignofnether.hero;

/**
 * How a hero is told apart in a fight (design-draft-heroes-better-2026-10-30 #4; lovish, Oct 30: "Ring + crest,
 * 1.25x"): the rules the client draws by, in common code so the server's GameTests hold them (HeroMarkerGameTests).
 * The drawing is HeroMarkerClientEvents (a player-colour ring round the feet, a crest over the head) and the hero
 * renderers' scale (HeroRenderers.HERO_SCALE).
 */
public final class HeroLook {
    private HeroLook() { }

    /** Every faction hero is drawn this much bigger than the unit its body is built on (was 1.1). */
    public static final float SCALE = 1.25f;

    /** The ring and the crest: on heroes only, and only in the RTS view (never in first person). */
    public static boolean showMarker(boolean isHero, boolean rtsView) {
        return isHero && rtsView;
    }

    /** The ring's radius round a hero whose hitbox is {@code width} wide: just outside its drawn (scaled) body. */
    public static float ringRadius(float width) {
        return width * SCALE / 2 + 0.3f;
    }

    /** Blocks above the hero's feet the crest floats, for a hitbox {@code height} tall: clear of the drawn head. */
    public static float crestHeight(float height) {
        return height * SCALE + 0.45f;
    }
}
