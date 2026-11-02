package com.solegendary.reignofnether.compat;

import dev.tr7zw.entityculling.EntityCullingModBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Entity Culling's tick culling skips a client tick of any entity that was not drawn since its last tick, so at under
 * 20 fps every second tick of a unit was skipped: its yBodyRot and yBodyRotO held and the frames replayed the same
 * turn (167 held pairs in eyes 41050396210). Units' types must go on its tick whitelist when they join the client.
 */
class EntityCullingCompatTest {

    @AfterEach
    void clear() {
        EntityCullingModBase.instance = null;
    }

    @Test
    void every_unit_kind_goes_on_entity_cullings_tick_whitelist() {
        EntityCullingModBase.instance = new EntityCullingModBase();
        Object grunt = new Object(), dragon = new Object(), squad = new Object();
        for (Object kind : new Object[] { grunt, dragon, squad, grunt })
            assertTrue(EntityCullingCompat.keepClientTicks(kind), "the hook did not reach Entity Culling's tick whitelist");
        assertTrue(EntityCullingModBase.instance.tickCullWhitelists.contains(grunt), "grunt's ticks can still be culled");
        assertTrue(EntityCullingModBase.instance.tickCullWhitelists.contains(dragon), "dragon's ticks can still be culled");
        assertTrue(EntityCullingModBase.instance.tickCullWhitelists.contains(squad), "squad's ticks can still be culled");
    }

    @Test
    void a_unit_joining_before_entity_culling_is_set_up_is_kept_once_it_is() {
        Object early = new Object();
        assertFalse(EntityCullingCompat.keepClientTicks(early), "no Entity Culling instance yet, nothing to fill");
        EntityCullingModBase.instance = new EntityCullingModBase();
        Object late = new Object();
        assertTrue(EntityCullingCompat.keepClientTicks(late));
        assertTrue(EntityCullingModBase.instance.tickCullWhitelists.contains(early), "a unit seen before set-up was lost");
        assertTrue(EntityCullingModBase.instance.tickCullWhitelists.contains(late));
    }
}
