package com.solegendary.reignofnether.compat;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;

import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

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

    /**
     * Units always tick on the client. Entity Culling's tick culling skips the client tick of an entity that has not
     * been drawn since its last tick: under 20 fps the client runs two or more ticks per frame, so every tick after a
     * frame's first was skipped (only the position lerp ran). The unit's body yaw then held for that tick - yBodyRotO
     * and yBodyRot repeated and the frames replayed the same turn (167 held pairs in eyes 41050396210) - and its
     * animation clocks, which read the entity's own tick, stuttered with it. Outside the RTS view only (the toggle above
     * switches the whole mod off in it). Its draw culling of hidden units stays; only their client ticks are kept.
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent evt) {
        if (evt.getLevel().isClientSide() && evt.getEntity() instanceof Unit)
            keepClientTicks(evt.getEntity().getType());
    }

    // its own logger: ReignOfNether's class sets up networking when loaded, which the JUnit test of this cannot
    private static final Logger LOG = LogManager.getLogger();
    private static final String MOD_BASE_CLASS = "dev.tr7zw.entityculling.EntityCullingModBase";
    private static final Set<Object> unitTypes = new HashSet<>();   // every unit type seen, for a late set-up
    private static boolean baseLooked = false;
    private static Field baseInstance = null;    // EntityCullingModBase.instance (static)
    private static Field tickWhitelist = null;   // its Set<EntityType<?>> tickCullWhitelists; null: absent or unusable
    private static Object filled = null;         // the instance whose whitelist already holds every type seen

    /**
     * Puts an entity type on Entity Culling's tick whitelist (a public set its tick hook checks before skipping), by
     * reflection - no compile dependency; absent or renamed, nothing happens. Returns whether the type is on it now.
     */
    @SuppressWarnings("unchecked")
    static boolean keepClientTicks(Object type) {
        unitTypes.add(type);
        if (!baseLooked) {
            baseLooked = true;
            try {
                Class<?> base = Class.forName(MOD_BASE_CLASS);
                Field instance = base.getField("instance");
                Field whitelist = base.getField("tickCullWhitelists");
                if (Modifier.isStatic(instance.getModifiers()) && !Modifier.isStatic(whitelist.getModifiers())
                        && Set.class.isAssignableFrom(whitelist.getType())) {
                    baseInstance = instance;
                    tickWhitelist = whitelist;
                    LOG.info("Entity Culling found: units' client ticks are kept off its tick culling");
                } else {
                    LOG.warn("Entity Culling found, but its tick whitelist is not a set - left alone");
                }
            } catch (ClassNotFoundException | NoSuchFieldException | SecurityException e) {
                // the mod is absent (or renamed its members): nothing to keep
            }
        }
        if (tickWhitelist == null)
            return false;
        try {
            Object mod = baseInstance.get(null);
            if (mod == null)
                return false;   // not set up yet: unitTypes keeps the type for the next join
            Set<Object> set = (Set<Object>) tickWhitelist.get(mod);
            if (set == null)
                return false;
            if (mod != filled) {
                set.addAll(unitTypes);
                filled = mod;
            } else {
                set.add(type);
            }
            return true;
        } catch (IllegalAccessException | IllegalArgumentException | ClassCastException
                 | UnsupportedOperationException e) {
            LOG.warn("Entity Culling: could not keep units' client ticks: {}", e.toString());
            tickWhitelist = null;
            return false;
        }
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
