package com.solegendary.reignofnether.compat;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Entity Culling (tr7zw, in the tester pack) hides an entity when every ray from the CAMERA'S POSITION to its box
 * hits a block. In the RTS view the projection is orthographic: that position is a far-off point behind and above
 * the scene, not where the view rays come from, so units by a wall or in a dip were "occluded" by blocks the player
 * sees past - their selection diamonds kept moving with nobody inside (eyes scenes 12 and 13, Oct 15 audit).
 * While the RTS view is on, this switches Entity Culling off through its own public toggle
 * ({@code EntityCullingVersionlessBase.enabled}, read once by reflection - no compile dependency; absent or renamed,
 * nothing happens) and restores the player's setting when the view is left. With the toggle off its renderer reports
 * nothing culled and its tracing thread idles.
 */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID, value = Dist.CLIENT)
public final class EntityCullingCompat {

    private static final String TOGGLE_CLASS = "dev.tr7zw.entityculling.versionless.EntityCullingVersionlessBase";

    private static boolean looked = false;
    private static Field toggle = null;       // null: the mod is absent or has no usable toggle
    private static boolean applied = false;   // whether our RTS-view override is in force
    private static boolean saved = true;      // the toggle's value before we turned it off

    private EntityCullingCompat() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        boolean rtsView = OrthoviewClientEvents.enabled;
        if (rtsView == applied)
            return;
        applied = rtsView;
        Field f = toggle();
        if (f == null)
            return;
        try {
            if (rtsView) {
                saved = f.getBoolean(null);
                f.setBoolean(null, false);
            } else {
                f.setBoolean(null, saved);
            }
        } catch (IllegalAccessException | IllegalArgumentException e) {
            ReignOfNether.LOGGER.warn("Entity Culling: could not switch its culling for the RTS view: {}", e.toString());
            toggle = null;
        }
    }

    /** Keeps an entity type's client ticks from Entity Culling's tick culling. Red step: not wired yet. */
    static boolean keepClientTicks(Object type) {
        return false;
    }

    private static Field toggle() {
        if (looked)
            return toggle;
        looked = true;
        if (!ModList.get().isLoaded("entityculling"))
            return null;
        try {
            Field f = Class.forName(TOGGLE_CLASS).getField("enabled");
            int mod = f.getModifiers();
            if (f.getType() == boolean.class && Modifier.isStatic(mod) && !Modifier.isFinal(mod)) {
                toggle = f;
                ReignOfNether.LOGGER.info("Entity Culling found: its culling is switched off while the RTS view is on");
            } else {
                ReignOfNether.LOGGER.warn("Entity Culling found, but its toggle is not a static boolean - left alone");
            }
        } catch (ClassNotFoundException | NoSuchFieldException | SecurityException e) {
            ReignOfNether.LOGGER.warn("Entity Culling found, but not its toggle ({}) - units by walls may vanish in "
                + "the RTS view", e.toString());
        }
        return toggle;
    }
}
