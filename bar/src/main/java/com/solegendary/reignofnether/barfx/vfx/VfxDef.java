package com.solegendary.reignofnether.barfx.vfx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One effect, as read from {@code assets/reignofnether/vfx/<id>.json} by {@link VfxParser} (design/vfx.md has the
 * format and a worked example). An effect is a {@link Timeline} (wind-up, peak, fade) and a list of layers, each with
 * its own window on that timeline:
 * <ul>
 *   <li>{@link Ground} - a sigil laid on the terrain (conforms to the blocks under it), spinning, growing;</li>
 *   <li>{@link Ring} - flat rings in the air, optionally a stack of counter-rotating rings rising above each other;</li>
 *   <li>{@link Beam} - a vertical shaft (scrolling light, a bright core) with a helix of sparkles and rising bands;</li>
 *   <li>{@link Emitter} - physical particles: embers (gravity, drag, wind), sparks, smoke, glints, heat shimmer;</li>
 *   <li>{@link Light} - light the effect casts: a pool on the ground and a block-light boost on nearby units.</li>
 * </ul>
 * A {@code "type": "projectile"} file is a {@link Projectile} visual instead (core, ribbon trail, shed particles, a
 * moving light) bound to entity types. Plain data, immutable after parsing, no Minecraft classes (JUnit-tested).
 */
public final class VfxDef {
    public final String id;
    public final Timeline timeline;
    public final List<Layer> layers;
    /** Light the effect casts, or null. */
    public final Light light;
    /** Radius (blocks, before scale) that must be near the camera for the effect to be drawn - frustum-ish culling. */
    public final float cullRadius;
    /** Non-null for a projectile visual. */
    public final Projectile projectile;

    public VfxDef(String id, Timeline timeline, List<Layer> layers, Light light, float cullRadius, Projectile projectile) {
        this.id = id;
        this.timeline = timeline;
        this.layers = Collections.unmodifiableList(new ArrayList<>(layers));
        this.light = light;
        this.cullRadius = cullRadius;
        this.projectile = projectile;
    }

    public boolean isProjectile() {
        return projectile != null;
    }

    // ------------------------------------------------------------------ timeline

    /** Wind-up (builds), peak (holds at full), fade (dies away), in seconds. Total = the sum. */
    public static final class Timeline {
        public final float windup, peak, fade;

        public Timeline(float windup, float peak, float fade) {
            this.windup = Math.max(0, windup);
            this.peak = Math.max(0, peak);
            this.fade = Math.max(0, fade);
        }

        public float total() {
            return windup + peak + fade;
        }
    }

    // ------------------------------------------------------------------ layers

    /** What every layer shares: its window on the timeline, fades, colour, size growth, height. */
    public abstract static class Layer {
        /** Window in seconds from the effect's start; end < 0 = to the end of the effect. */
        public float start = 0, end = -1;
        /** Fade in / out at the window's edges (seconds); < 0 = the timeline's wind-up / fade. */
        public float fadeIn = -1, fadeOut = -1;
        public int color = 0xFFFFFF;
        public float alpha = 1;
        /** Grows from growFrom x its size to full size over {@code grow} s from its start (0 = no growth). */
        public float grow = 0, growFrom = 0;
        /** The growth curve (VfxEasing; "ease" in JSON) - cubic out: fast, then settling. */
        public VfxEasing ease = VfxEasing.CUBIC_OUT;
        /** Height above the anchor (blocks, scaled with the effect). */
        public float y = 0;
        /** Snap the anchor to the ground under it (ground sigils, pillars rising from the ground). */
        public boolean onGround = false;
    }

    /** A sigil laid on the terrain: the tile drawn on a grid that follows the ground's height. */
    public static final class Ground extends Layer {
        public int tile = VfxAtlas.SIGIL_STAR;
        public float radius = 3;
        /** Degrees per second. */
        public float spin = 0;
        /** Brightness flicker 0..1 (embers in the cracks, not a strobe). */
        public float flicker = 0;
        /** Translucent (darkens: char, soot) instead of additive light. */
        public boolean dark = false;
    }

    /** Flat rings in the air: {@code stack} of them, {@code spacing} apart, each {@code radiusStep} smaller. */
    public static final class Ring extends Layer {
        public int tile = VfxAtlas.RING_GLYPH;
        public float radius = 2;
        public float spin = 30;
        public int stack = 1;
        public float spacing = 1, radiusStep = 0;
        /** Each ring turns the other way to the one below it. */
        public boolean alternate = true;
        /** Tilt from horizontal (degrees) - a gentle wobble reads as a physical disc, not a sticker. */
        public float tilt = 0;
        /** Rings rise this many blocks over the layer's window (a rune ladder climbing). */
        public float rise = 0;
    }

    /** A vertical shaft of light with an optional helix of sparkles and bands climbing it. */
    public static final class Beam extends Layer {
        public float radius = 0.8f, height = 30;
        /** Texture lengths per second the shaft scrolls upward. */
        public float scroll = 1.5f;
        /** Width of the bright camera-facing core, fraction of radius (0 = none). */
        public float core = 0.35f;
        public int coreColor = 0xFFFFFF;
        /** Helix strands (0 = none). */
        public int strands = 0;
        public float helixRadius = 1.2f, turns = 5, helixSpeed = 1, glintSize = 0.3f;
        public int helixPoints = 32;
        public int helixColor = 0xFFFFFF;
        /** Rising bands (thin rings) climbing the shaft. */
        public int bands = 0;
        public float bandRadius = 1.5f, bandSpeed = 6;
        public int bandTile = VfxAtlas.RING_THIN;
    }

    /** Particles with physics. */
    public static final class Emitter extends Layer {
        public enum Kind { EMBER, SPARK, SMOKE, GLINT, HEAT }

        public enum Shape { POINT, DISC, RING, COLUMN, SPHERE }

        public Kind kind = Kind.EMBER;
        public Shape shape = Shape.DISC;
        /** Particles per second while the window is open, and a burst at its start (before the quality setting). */
        public float rate = 20;
        public int burst = 0;
        public float radius = 1, height = 0;
        /** Base velocity (blocks/s) plus random spread (blocks/s, any direction) plus outward / swirl speeds. */
        public float vx = 0, vy = 1, vz = 0, spread = 0.5f, outward = 0, swirl = 0;
        /** Physics: gravity (blocks/s^2, positive pulls down; negative = buoyant), linear drag (1/s), wind response. */
        public float gravity = 4, drag = 1, wind = 0.5f;
        public float lifeMin = 1, lifeMax = 2, sizeMin = 0.1f, sizeMax = 0.2f;
        /** Size multiplier at the end of life (smoke grows, embers shrink), reached along sizeEase. */
        public float sizeEnd = 1;
        public VfxEasing sizeEase = VfxEasing.LINEAR;
        /** Colour at the end of life (embers cool from yellow-white to deep red). */
        public int colorEnd = -1;
    }

    /** Light cast by the effect (or a projectile): a pool on the ground plus a block-light boost for nearby units. */
    public static final class Light {
        public int color = 0xFFD9A0;
        public float radius = 8, intensity = 1, y = 1, flicker = 0;
        /** Window on the timeline like a layer (projectile lights ignore it). */
        public float start = 0, end = -1, fadeIn = -1, fadeOut = -1;
    }

    /** A projectile's look: hot core, ribbon trail, shed particles, light; the model can be hidden. */
    public static final class Projectile {
        public final List<String> entities = new ArrayList<>();
        public int coreTile = VfxAtlas.GLOW;
        public float coreSize = 0.6f, pulse = 0.15f;
        public int coreColor = 0xFFFFFF;
        public float innerSize = 0.25f;
        public int innerColor = 0xFFFFFF;
        public int trailSamples = 12;
        public float trailWidth = 0.3f, trailAlpha = 0.8f, trailStep = 0.15f;
        public int trailColor = 0xFFFFFF, trailColorEnd = -1;
        /** Shed particles (window ignored: they stream while it flies), or null. */
        public Emitter emitter;
        public boolean hideModel = true;
    }
}
