package com.solegendary.reignofnether.barfx.vfx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads an effect definition (design/vfx.md) from JSON into a {@link VfxDef}. Lenient by design - a typo in one
 * layer must not cost the whole effect - but never silent: every problem is added to {@code errors} (the client logs
 * them once per reload, VfxDefTest fails on any in the shipped files). Unknown keys are reported too, so a misspelt
 * "radius" is caught instead of quietly falling back to the default. Pure Java + Gson.
 */
public final class VfxParser {
    private VfxParser() { }

    public static VfxDef parse(String id, String json, List<String> errors) {
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (Exception e) {
            errors.add(id + ": not JSON (" + e.getMessage() + ")");
            return null;
        }
        if (!root.isJsonObject()) {
            errors.add(id + ": the top level must be an object");
            return null;
        }
        return parse(id, root.getAsJsonObject(), errors);
    }

    public static VfxDef parse(String id, JsonObject o, List<String> errors) {
        String where = id;
        if (!VfxIds.valid(id))
            errors.add(id + ": invalid id (lower-case letters, digits, _ and / only)");
        known(o, where, errors, "type", "timeline", "layers", "light", "cull_radius", "comment",
              "entities", "core", "trail", "emitter", "hide_model");
        String type = str(o, "type", "effect");
        if (type.equals("projectile"))
            return new VfxDef(id, new VfxDef.Timeline(0, 1, 0), List.of(), light(o, "light", where, errors),
                f(o, "cull_radius", 4, where, errors), projectile(o, where, errors));
        if (!type.equals("effect"))
            errors.add(where + ": type must be \"effect\" or \"projectile\", not \"" + type + "\"");

        VfxDef.Timeline tl = new VfxDef.Timeline(0.3f, 1, 0.5f);
        if (o.has("timeline")) {
            JsonObject t = obj(o, "timeline", where, errors);
            if (t != null) {
                known(t, where + ".timeline", errors, "windup", "peak", "fade");
                tl = new VfxDef.Timeline(f(t, "windup", 0.3f, where, errors), f(t, "peak", 1, where, errors),
                    f(t, "fade", 0.5f, where, errors));
            }
        }
        if (tl.total() <= 0) {
            errors.add(where + ": the timeline is empty (windup + peak + fade must be > 0)");
            tl = new VfxDef.Timeline(0, 0.05f, 0);
        }
        if (tl.total() > 60)
            errors.add(where + ": timeline longer than 60 s - effects are moments; use a refresh for standing ones");

        List<VfxDef.Layer> layers = new ArrayList<>();
        if (o.has("layers")) {
            JsonArray arr = o.get("layers").isJsonArray() ? o.getAsJsonArray("layers") : null;
            if (arr == null)
                errors.add(where + ": layers must be an array");
            else
                for (int i = 0; i < arr.size(); i++) {
                    String lw = where + ".layers[" + i + "]";
                    if (!arr.get(i).isJsonObject()) { errors.add(lw + ": must be an object"); continue; }
                    VfxDef.Layer l = layer(arr.get(i).getAsJsonObject(), tl, lw, errors);
                    if (l != null) layers.add(l);
                }
        }
        VfxDef.Light light = light(o, "light", where, errors);
        float cull = f(o, "cull_radius", autoCull(layers, light), where, errors);
        return new VfxDef(id, tl, layers, light, cull, null);
    }

    /** The default cull radius: the widest layer or light, plus margin. */
    static float autoCull(List<VfxDef.Layer> layers, VfxDef.Light light) {
        float r = 2;
        for (VfxDef.Layer l : layers) {
            if (l instanceof VfxDef.Ground g) r = Math.max(r, g.radius);
            else if (l instanceof VfxDef.Ring g) r = Math.max(r, g.radius);
            else if (l instanceof VfxDef.Beam b) r = Math.max(r, Math.max(b.helixRadius, b.bandRadius));
            else if (l instanceof VfxDef.Emitter e) r = Math.max(r, e.radius + 2);
        }
        if (light != null) r = Math.max(r, light.radius * 0.5f);
        return r + 2;
    }

    // ------------------------------------------------------------------ layers

    private static final String[] COMMON = { "type", "start", "end", "fade_in", "fade_out", "color", "alpha", "grow",
            "grow_from", "ease", "y", "on_ground", "comment" };

    static VfxDef.Layer layer(JsonObject o, VfxDef.Timeline tl, String where, List<String> errors) {
        String type = str(o, "type", "");
        VfxDef.Layer l;
        switch (type) {
            case "ground" -> {
                VfxDef.Ground g = new VfxDef.Ground();
                known(o, where, errors, COMMON, "tile", "radius", "spin", "flicker", "dark");
                g.onGround = true;
                g.tile = tile(o, "tile", g.tile, where, errors);
                g.radius = pos(o, "radius", g.radius, where, errors);
                g.spin = f(o, "spin", g.spin, where, errors);
                g.flicker = clamp01(f(o, "flicker", g.flicker, where, errors));
                g.dark = bool(o, "dark", false);
                l = g;
            }
            case "ring" -> {
                VfxDef.Ring r = new VfxDef.Ring();
                known(o, where, errors, COMMON, "tile", "radius", "spin", "stack", "spacing", "radius_step", "alternate",
                      "tilt", "rise");
                r.tile = tile(o, "tile", r.tile, where, errors);
                r.radius = pos(o, "radius", r.radius, where, errors);
                r.spin = f(o, "spin", r.spin, where, errors);
                r.stack = Math.max(1, Math.min(12, (int) f(o, "stack", 1, where, errors)));
                r.spacing = f(o, "spacing", r.spacing, where, errors);
                r.radiusStep = f(o, "radius_step", 0, where, errors);
                r.alternate = bool(o, "alternate", true);
                r.tilt = f(o, "tilt", 0, where, errors);
                r.rise = f(o, "rise", 0, where, errors);
                l = r;
            }
            case "beam" -> {
                VfxDef.Beam b = new VfxDef.Beam();
                known(o, where, errors, COMMON, "radius", "height", "scroll", "core", "core_color", "helix", "bands");
                b.radius = pos(o, "radius", b.radius, where, errors);
                b.height = pos(o, "height", b.height, where, errors);
                b.scroll = f(o, "scroll", b.scroll, where, errors);
                b.core = clamp01(f(o, "core", b.core, where, errors));
                b.coreColor = color(o, "core_color", 0xFFFFFF, where, errors);
                if (o.has("helix")) {
                    JsonObject h = obj(o, "helix", where, errors);
                    if (h != null) {
                        String hw = where + ".helix";
                        known(h, hw, errors, "strands", "radius", "turns", "speed", "points", "size", "color");
                        b.strands = Math.max(0, Math.min(6, (int) f(h, "strands", 2, hw, errors)));
                        b.helixRadius = pos(h, "radius", b.helixRadius, hw, errors);
                        b.turns = f(h, "turns", b.turns, hw, errors);
                        b.helixSpeed = f(h, "speed", b.helixSpeed, hw, errors);
                        b.helixPoints = Math.max(2, Math.min(128, (int) f(h, "points", b.helixPoints, hw, errors)));
                        b.glintSize = pos(h, "size", b.glintSize, hw, errors);
                        b.helixColor = color(h, "color", 0xFFFFFF, hw, errors);
                    }
                }
                if (o.has("bands")) {
                    JsonObject h = obj(o, "bands", where, errors);
                    if (h != null) {
                        String hw = where + ".bands";
                        known(h, hw, errors, "count", "radius", "speed", "tile");
                        b.bands = Math.max(0, Math.min(12, (int) f(h, "count", 3, hw, errors)));
                        b.bandRadius = pos(h, "radius", b.bandRadius, hw, errors);
                        b.bandSpeed = f(h, "speed", b.bandSpeed, hw, errors);
                        b.bandTile = tile(h, "tile", b.bandTile, hw, errors);
                    }
                }
                l = b;
            }
            case "emitter" -> l = emitter(o, where, errors, true);
            default -> {
                errors.add(where + ": unknown layer type \"" + type + "\" (ground, ring, beam, emitter)");
                return null;
            }
        }
        common(o, l, tl, where, errors);
        return l;
    }

    static VfxDef.Emitter emitter(JsonObject o, String where, List<String> errors, boolean asLayer) {
        VfxDef.Emitter e = new VfxDef.Emitter();
        known(o, where, errors, COMMON, "particle", "shape", "rate", "burst", "radius", "height", "velocity", "spread",
              "outward", "swirl", "gravity", "drag", "wind", "life", "size", "size_end", "size_ease", "color_end");
        String k = str(o, "particle", "ember").toUpperCase(Locale.ROOT);
        try { e.kind = VfxDef.Emitter.Kind.valueOf(k); }
        catch (IllegalArgumentException ex) { errors.add(where + ": unknown particle \"" + k.toLowerCase(Locale.ROOT) + "\""); }
        String s = str(o, "shape", "disc").toUpperCase(Locale.ROOT);
        try { e.shape = VfxDef.Emitter.Shape.valueOf(s); }
        catch (IllegalArgumentException ex) { errors.add(where + ": unknown shape \"" + s.toLowerCase(Locale.ROOT) + "\""); }
        // defaults by kind: embers buoyant at first then falling is modelled by a light gravity + strong wind response
        switch (e.kind) {
            case EMBER -> { e.gravity = 1.2f; e.drag = 1.2f; e.wind = 0.8f; e.sizeMin = 0.05f; e.sizeMax = 0.11f; e.sizeEnd = 0.4f; }
            case SPARK -> { e.gravity = 14; e.drag = 1.5f; e.wind = 0.1f; e.sizeMin = 0.05f; e.sizeMax = 0.09f; e.lifeMin = 0.3f; e.lifeMax = 0.7f; }
            case SMOKE -> { e.gravity = -0.6f; e.drag = 0.8f; e.wind = 1; e.sizeMin = 0.6f; e.sizeMax = 1.1f; e.sizeEnd = 2.6f; e.lifeMin = 2; e.lifeMax = 3.5f; }
            case GLINT -> { e.gravity = 0; e.drag = 2; e.wind = 0.2f; e.sizeMin = 0.15f; e.sizeMax = 0.3f; }
            case HEAT -> { e.gravity = -1.5f; e.drag = 1; e.wind = 0.5f; e.sizeMin = 1; e.sizeMax = 1.6f; e.sizeEnd = 1.5f; }
        }
        e.rate = Math.max(0, f(o, "rate", asLayer ? 20 : 30, where, errors));
        e.burst = Math.max(0, Math.min(400, (int) f(o, "burst", 0, where, errors)));
        e.radius = Math.max(0, f(o, "radius", e.radius, where, errors));
        e.height = Math.max(0, f(o, "height", 0, where, errors));
        float[] vel = vec3(o, "velocity", new float[] { 0, e.kind == VfxDef.Emitter.Kind.SMOKE ? 0.6f : 1, 0 }, where, errors);
        e.vx = vel[0]; e.vy = vel[1]; e.vz = vel[2];
        e.spread = Math.max(0, f(o, "spread", e.spread, where, errors));
        e.outward = f(o, "outward", 0, where, errors);
        e.swirl = f(o, "swirl", 0, where, errors);
        e.gravity = f(o, "gravity", e.gravity, where, errors);
        e.drag = Math.max(0, f(o, "drag", e.drag, where, errors));
        e.wind = f(o, "wind", e.wind, where, errors);
        float[] life = range(o, "life", e.lifeMin, e.lifeMax, where, errors);
        e.lifeMin = Math.max(0.02f, life[0]); e.lifeMax = Math.max(e.lifeMin, life[1]);
        float[] size = range(o, "size", e.sizeMin, e.sizeMax, where, errors);
        e.sizeMin = Math.max(0.005f, size[0]); e.sizeMax = Math.max(e.sizeMin, size[1]);
        e.sizeEnd = Math.max(0, f(o, "size_end", e.sizeEnd, where, errors));
        e.sizeEase = ease(o, "size_ease", e.sizeEase, where, errors);
        e.colorEnd = o.has("color_end") ? color(o, "color_end", -1, where, errors) : -1;
        return e;
    }

    static void common(JsonObject o, VfxDef.Layer l, VfxDef.Timeline tl, String where, List<String> errors) {
        l.start = time(o, "start", 0, tl, where, errors);
        l.end = o.has("end") ? time(o, "end", -1, tl, where, errors) : -1;
        if (l.end >= 0 && l.end <= l.start)
            errors.add(where + ": end (" + l.end + ") is not after start (" + l.start + ")");
        if (l.start >= tl.total())
            errors.add(where + ": starts at " + l.start + " s, after the effect ends (" + tl.total() + " s)");
        l.fadeIn = o.has("fade_in") ? Math.max(0, f(o, "fade_in", 0, where, errors)) : -1;
        l.fadeOut = o.has("fade_out") ? Math.max(0, f(o, "fade_out", 0, where, errors)) : -1;
        l.color = color(o, "color", l.color, where, errors);
        l.alpha = clamp01(f(o, "alpha", 1, where, errors));
        l.grow = Math.max(0, f(o, "grow", 0, where, errors));
        l.growFrom = clamp01(f(o, "grow_from", 0, where, errors));
        l.ease = ease(o, "ease", l.ease, where, errors);
        l.y = f(o, "y", 0, where, errors);
        if (o.has("on_ground")) l.onGround = bool(o, "on_ground", l.onGround);
    }

    static VfxDef.Light light(JsonObject o, String key, String where, List<String> errors) {
        if (!o.has(key)) return null;
        JsonObject j = obj(o, key, where, errors);
        if (j == null) return null;
        String lw = where + "." + key;
        known(j, lw, errors, "color", "radius", "intensity", "y", "flicker", "start", "end", "fade_in", "fade_out", "comment");
        VfxDef.Light li = new VfxDef.Light();
        li.color = color(j, "color", li.color, lw, errors);
        li.radius = Math.min(32, pos(j, "radius", li.radius, lw, errors));
        li.intensity = clamp01(f(j, "intensity", li.intensity, lw, errors));
        li.y = f(j, "y", li.y, lw, errors);
        li.flicker = clamp01(f(j, "flicker", 0, lw, errors));
        li.start = Math.max(0, f(j, "start", 0, lw, errors));
        li.end = f(j, "end", -1, lw, errors);
        li.fadeIn = j.has("fade_in") ? Math.max(0, f(j, "fade_in", 0, lw, errors)) : -1;
        li.fadeOut = j.has("fade_out") ? Math.max(0, f(j, "fade_out", 0, lw, errors)) : -1;
        return li;
    }

    static VfxDef.Projectile projectile(JsonObject o, String where, List<String> errors) {
        VfxDef.Projectile p = new VfxDef.Projectile();
        if (o.has("entities") && o.get("entities").isJsonArray())
            for (JsonElement e : o.getAsJsonArray("entities")) p.entities.add(e.getAsString());
        if (p.entities.isEmpty())
            errors.add(where + ": a projectile visual needs \"entities\": [\"namespace:entity_type\", ...]");
        p.hideModel = bool(o, "hide_model", true);
        if (o.has("core")) {
            JsonObject c = obj(o, "core", where, errors);
            if (c != null) {
                String cw = where + ".core";
                known(c, cw, errors, "tile", "size", "pulse", "color", "inner_size", "inner_color");
                p.coreTile = tile(c, "tile", p.coreTile, cw, errors);
                p.coreSize = pos(c, "size", p.coreSize, cw, errors);
                p.pulse = clamp01(f(c, "pulse", p.pulse, cw, errors));
                p.coreColor = color(c, "color", p.coreColor, cw, errors);
                p.innerSize = Math.max(0, f(c, "inner_size", p.innerSize, cw, errors));
                p.innerColor = color(c, "inner_color", p.innerColor, cw, errors);
            }
        }
        if (o.has("trail")) {
            JsonObject t = obj(o, "trail", where, errors);
            if (t != null) {
                String tw = where + ".trail";
                known(t, tw, errors, "samples", "width", "alpha", "step", "color", "color_end");
                p.trailSamples = Math.max(0, Math.min(48, (int) f(t, "samples", p.trailSamples, tw, errors)));
                p.trailWidth = pos(t, "width", p.trailWidth, tw, errors);
                p.trailAlpha = clamp01(f(t, "alpha", p.trailAlpha, tw, errors));
                p.trailStep = pos(t, "step", p.trailStep, tw, errors);
                p.trailColor = color(t, "color", p.trailColor, tw, errors);
                p.trailColorEnd = t.has("color_end") ? color(t, "color_end", -1, tw, errors) : -1;
            }
        }
        if (o.has("emitter")) {
            JsonObject e = obj(o, "emitter", where, errors);
            if (e != null) {
                p.emitter = emitter(e, where + ".emitter", errors, false);
                p.emitter.color = color(e, "color", 0xFFFFFF, where + ".emitter", errors);
                p.emitter.alpha = clamp01(f(e, "alpha", 1, where + ".emitter", errors));
            }
        }
        return p;
    }

    // ------------------------------------------------------------------ values

    /** A time: seconds, or a phase name ("windup" = 0, "peak" = the wind-up's end, "fade" = the peak's end, "end"). */
    static float time(JsonObject o, String k, float def, VfxDef.Timeline tl, String where, List<String> errors) {
        if (!o.has(k)) return def;
        JsonElement e = o.get(k);
        if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
            switch (e.getAsString()) {
                case "windup", "start": return 0;
                case "peak": return tl.windup;
                case "fade": return tl.windup + tl.peak;
                case "end": return tl.total();
                default:
                    errors.add(where + "." + k + ": \"" + e.getAsString() + "\" is not a time (seconds, windup, peak, fade, end)");
                    return def;
            }
        }
        return Math.max(0, f(o, k, def, where, errors));
    }

    /** "#RRGGBB", "RRGGBB" or [r, g, b] in 0..1. */
    static int color(JsonObject o, String k, int def, String where, List<String> errors) {
        if (!o.has(k)) return def;
        JsonElement e = o.get(k);
        try {
            if (e.isJsonArray()) {
                JsonArray a = e.getAsJsonArray();
                int r = (int) (clamp01(a.get(0).getAsFloat()) * 255 + .5f);
                int g = (int) (clamp01(a.get(1).getAsFloat()) * 255 + .5f);
                int b = (int) (clamp01(a.get(2).getAsFloat()) * 255 + .5f);
                return (r << 16) | (g << 8) | b;
            }
            String s = e.getAsString().trim();
            if (s.startsWith("#")) s = s.substring(1);
            if (s.length() != 6) throw new NumberFormatException(s);
            return Integer.parseInt(s, 16);
        } catch (Exception ex) {
            errors.add(where + "." + k + ": not a colour (\"#RRGGBB\" or [r, g, b])");
            return def;
        }
    }

    static VfxEasing ease(JsonObject o, String k, VfxEasing def, String where, List<String> errors) {
        if (!o.has(k)) return def;
        VfxEasing e = VfxEasing.byName(str(o, k, ""));
        if (e == null) {
            errors.add(where + "." + k + ": unknown easing \"" + str(o, k, "") + "\" (linear, smoothstep, cubic_out, back_out, ...)");
            return def;
        }
        return e;
    }

    static int tile(JsonObject o, String k, int def, String where, List<String> errors) {
        if (!o.has(k)) return def;
        int t = VfxAtlas.byName(str(o, k, ""));
        if (t < 0) {
            errors.add(where + "." + k + ": unknown tile \"" + str(o, k, "") + "\" (see VfxAtlas.NAMES)");
            return def;
        }
        return t;
    }

    static float f(JsonObject o, String k, float def, String where, List<String> errors) {
        if (!o.has(k)) return def;
        try {
            float v = o.get(k).getAsFloat();
            if (!Float.isFinite(v)) throw new NumberFormatException();
            return v;
        } catch (Exception e) {
            errors.add(where + "." + k + ": not a number");
            return def;
        }
    }

    static float pos(JsonObject o, String k, float def, String where, List<String> errors) {
        float v = f(o, k, def, where, errors);
        if (v <= 0) {
            errors.add(where + "." + k + ": must be > 0");
            return def;
        }
        return v;
    }

    /** A number or [min, max]. */
    static float[] range(JsonObject o, String k, float dMin, float dMax, String where, List<String> errors) {
        if (!o.has(k)) return new float[] { dMin, dMax };
        JsonElement e = o.get(k);
        try {
            if (e.isJsonArray()) {
                float a = e.getAsJsonArray().get(0).getAsFloat(), b = e.getAsJsonArray().get(1).getAsFloat();
                return new float[] { Math.min(a, b), Math.max(a, b) };
            }
            float v = e.getAsFloat();
            return new float[] { v, v };
        } catch (Exception ex) {
            errors.add(where + "." + k + ": not a number or [min, max]");
            return new float[] { dMin, dMax };
        }
    }

    static float[] vec3(JsonObject o, String k, float[] def, String where, List<String> errors) {
        if (!o.has(k)) return def;
        try {
            JsonArray a = o.getAsJsonArray(k);
            return new float[] { a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat() };
        } catch (Exception ex) {
            errors.add(where + "." + k + ": not [x, y, z]");
            return def;
        }
    }

    static JsonObject obj(JsonObject o, String k, String where, List<String> errors) {
        JsonElement e = o.get(k);
        if (e == null || !e.isJsonObject()) {
            errors.add(where + "." + k + ": must be an object");
            return null;
        }
        return e.getAsJsonObject();
    }

    static String str(JsonObject o, String k, String def) {
        try {
            return o.has(k) ? o.get(k).getAsString() : def;
        } catch (Exception e) {
            return def;
        }
    }

    static boolean bool(JsonObject o, String k, boolean def) {
        try {
            return o.has(k) ? o.get(k).getAsBoolean() : def;
        } catch (Exception e) {
            return def;
        }
    }

    static float clamp01(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static void known(JsonObject o, String where, List<String> errors, String[] common, String... more) {
        String[] all = new String[common.length + more.length];
        System.arraycopy(common, 0, all, 0, common.length);
        System.arraycopy(more, 0, all, common.length, more.length);
        known(o, where, errors, all);
    }

    private static void known(JsonObject o, String where, List<String> errors, String... keys) {
        outer:
        for (String k : o.keySet()) {
            for (String n : keys) if (n.equals(k)) continue outer;
            errors.add(where + ": unknown key \"" + k + "\"");
        }
    }
}
