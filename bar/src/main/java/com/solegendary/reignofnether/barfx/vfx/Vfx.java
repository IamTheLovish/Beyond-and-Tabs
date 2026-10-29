package com.solegendary.reignofnether.barfx.vfx;

import com.solegendary.reignofnether.barfx.BarFx;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Server entry points for data-driven effects (design/vfx.md). Gameplay code names an effect by its asset id
 * ({@code assets/reignofnether/vfx/<id>.json}); the server never reads the definition - it sends the id's hash in one
 * BarFx event (queued per tick, send range, fog of war), and every client that can see the spot plays it.
 * Safe to call anywhere; client-side calls are ignored.
 * <pre>
 *   Vfx.at(level, "soul_bolt_impact", hitPos);           // a fixed spot
 *   Vfx.on(level, "sun_pillar", hero);                   // rides on the hero (ground layers stay under him)
 *   Vfx.on(level, "sun_pillar", hero, 1.5f);             // scaled: radii, heights, emitter areas
 * </pre>
 */
public final class Vfx {
    private Vfx() { }

    public static void at(Level level, String id, Vec3 pos) {
        at(level, id, pos, 1f);
    }

    public static void at(Level level, String id, Vec3 pos, float scale) {
        BarFx.vfx(level, VfxIds.hash(id), pos, scale, null);
    }

    public static void on(Level level, String id, Entity entity) {
        on(level, id, entity, 1f);
    }

    public static void on(Level level, String id, Entity entity, float scale) {
        if (entity == null) return;
        BarFx.vfx(level, VfxIds.hash(id), entity.position(), scale, entity);
    }
}
