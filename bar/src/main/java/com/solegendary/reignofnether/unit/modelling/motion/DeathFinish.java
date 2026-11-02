package com.solegendary.reignofnether.unit.modelling.motion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.barfx.BarFx;
import com.solegendary.reignofnether.unit.modelling.animations.Life;
import net.minecraft.util.Mth;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * How a fallen unit's body leaves the field, per faction (lovish, Oct 29: "units can have different animations for
 * dying: white angel troops could pulverize into light, death could crumble"). Layered on the death-pose contract:
 * the body still falls away from the killing blow ({@link DeathFall}) and lies still; then, at its faction's
 * {@link Style#start}, the finish plays for {@link Style#ticks} - the body fades, rises or sinks, crumbles, strobes,
 * glows - with one burst of the faction's particles (BarFxClient.deathFinish), and the body is gone at the end.
 *
 * Data, not code: assets/reignofnether/anim/death_finishes.json, one entry per faction (BarFx.F_* order; "none" for
 * anything without a faction), loaded once. A missing or broken file logs one WARN and every body sinks as before
 * ({@link #SINK}). Pure apart from that one read: the timeline functions take a style and a time and return numbers.
 */
public final class DeathFinish {

    private DeathFinish() {}

    /** One faction's finish. Times in ticks of deathTime; colors RGB. */
    public record Style(String style, int start, int ticks, float fade, float rise, float squash, float flicker,
                        boolean glow, int color, int color2, int particles) {

        /** deathTime at which the body is gone. */
        public int end() {
            return start + ticks;
        }
    }

    /** The plain sink every body had before the finishes (and the fallback): held to 80, sunk 0.6 blocks by 100. */
    public static final Style SINK = new Style("sink", DeathFall.HOLD_END, DeathFall.SINK_TICKS, 0f, -DeathFall.SINK_BLOCKS,
        0f, 0f, false, 0, 0, 0);

    /** The JSON keys in BarFx.F_* order (F_NONE = 0 .. F_BRASS = 7). */
    public static final String[] KEYS = {"none", "sunforged", "gravebound", "horde", "verdant", "tidewrought",
        "stormcallers", "brassworks"};
    public static final String RESOURCE = "/assets/reignofnether/anim/death_finishes.json";
    /** The earliest a finish may start: the server removes the unit at 20, the fall lands by 18. */
    public static final int EARLIEST_START = 24;

    private static Style[] styles = null;

    /** The finish of a BarFx.F_* faction (F_NONE and anything out of range: the "none" entry). */
    public static Style of(int faction) {
        if (styles == null)
            styles = load();
        return faction >= 0 && faction < styles.length ? styles[faction] : styles[BarFx.F_NONE];
    }

    private static Style[] load() {
        try (InputStream in = DeathFinish.class.getResourceAsStream(RESOURCE)) {
            if (in == null)
                throw new IllegalStateException("not in the jar: " + RESOURCE);
            return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            ReignOfNether.LOGGER.warn("[DeathFinish] {} unreadable, every body sinks: {}", RESOURCE, String.valueOf(e));
            Style[] s = new Style[KEYS.length];
            java.util.Arrays.fill(s, SINK);
            return s;
        }
    }

    /** Parses the finishes file; a faction missing from it sinks ({@link #SINK}). Throws on a malformed entry. */
    public static Style[] parse(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        Style[] out = new Style[KEYS.length];
        for (int i = 0; i < KEYS.length; i++) {
            JsonElement e = root.get(KEYS[i]);
            out[i] = e == null ? SINK : style(e.getAsJsonObject());
        }
        return out;
    }

    private static Style style(JsonObject o) {
        int start = Math.max(EARLIEST_START, o.get("start").getAsInt());
        int ticks = Math.max(1, o.get("ticks").getAsInt());
        return new Style(o.get("style").getAsString(), start, ticks,
            Mth.clamp(o.get("fade").getAsFloat(), 0f, 1f), Mth.clamp(o.get("rise").getAsFloat(), -2f, 2f),
            Mth.clamp(o.get("squash").getAsFloat(), 0f, 0.95f), Mth.clamp(o.get("flicker").getAsFloat(), 0f, 1f),
            o.get("glow").getAsBoolean(), rgb(o.get("color").getAsString()), rgb(o.get("color2").getAsString()),
            Mth.clamp(o.get("particles").getAsInt(), 0, 64));
    }

    static int rgb(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        return Integer.parseInt(h, 16) & 0xFFFFFF;
    }

    /** How far into its finish a body is at deathTime t: 0 before, 1 at the end. */
    public static float progress(Style s, float t) {
        return Mth.clamp((t - s.start) / s.ticks, 0f, 1f);
    }

    /** The body's opacity at t: 1 until the finish, down by fade, strobing by flicker (2 strobes a tick). */
    public static float alpha(Style s, float t) {
        float u = progress(s, t);
        float a = 1f - s.fade * Life.smooth(u);
        if (s.flicker > 0f && u > 0f && u < 1f && ((int) Math.floor(t * 2f) & 1) == 1)
            a *= 1f - s.flicker;
        return Mth.clamp(a, 0f, 1f);
    }

    /** How far the body has moved up at t, blocks (negative: sunk). */
    public static float lift(Style s, float t) {
        return s.rise * Life.smooth(progress(s, t));
    }

    /** The body's height scale at t: 1 down to 1 - squash (crumbling). */
    public static float heightScale(Style s, float t) {
        return 1f - s.squash * Life.smooth(progress(s, t));
    }

    /**
     * The body's overall scale at t: a body that fades away entirely also shrinks away over the last quarter of its
     * finish, so what the fade cannot reach (armour and items drawn by layers) goes with it.
     */
    public static float scale(Style s, float t) {
        if (s.fade < 1f)
            return 1f;
        return 1f - Life.smooth((progress(s, t) - 0.75f) / 0.25f);
    }

    /** The particle count for a burst at an RTS zoom: all of it up close, none at the strategic zoom. */
    public static int particles(Style s, float zoom, boolean orthoview) {
        if (!orthoview || zoom <= 30f)
            return s.particles;
        if (zoom >= 70f)
            return 0;
        return Math.round(s.particles * (1f - (zoom - 30f) / 40f));
    }
}
