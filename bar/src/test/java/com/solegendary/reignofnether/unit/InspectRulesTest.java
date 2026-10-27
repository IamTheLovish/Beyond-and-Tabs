package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.unit.InspectRules.Info;
import com.solegendary.reignofnether.unit.InspectRules.Tag;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** What an inspected unit / building card shows for own, allied, enemy and neutral things (InspectRules). */
class InspectRulesTest {

    private static final Set<Info> PUBLIC = EnumSet.of(Info.PORTRAIT, Info.NAME, Info.HEALTH, Info.STATS, Info.BUILDING_TYPE);
    private static final Set<Info> PLANS = EnumSet.of(Info.ABILITIES, Info.ITEMS, Info.PRODUCTION_QUEUE, Info.RALLY, Info.AUTOCAST);

    @Test
    void own_things_show_everything_and_take_orders() {
        for (Info i : Info.values())
            assertTrue(InspectRules.shows(i, Relationship.OWNED, false, false), i.name());
        assertTrue(InspectRules.canCommand(Relationship.OWNED, false, false));
        assertEquals(Tag.NONE, InspectRules.tag(Relationship.OWNED));
    }

    @Test
    void enemy_and_neutral_show_public_info_only_and_no_command_card() {
        for (Relationship rel : new Relationship[] { Relationship.HOSTILE, Relationship.NEUTRAL }) {
            for (Info i : PUBLIC)
                assertTrue(InspectRules.shows(i, rel, false, false), rel + " " + i);
            for (Info i : PLANS)
                assertFalse(InspectRules.shows(i, rel, false, false), rel + " " + i);
            assertFalse(InspectRules.shows(Info.COMMAND_CARD, rel, false, false), rel.name());
            assertFalse(InspectRules.canCommand(rel, false, false), rel.name());
        }
        assertEquals(Tag.ENEMY, InspectRules.tag(Relationship.HOSTILE));
        assertEquals("Enemy", Tag.ENEMY.label);
        assertEquals(Tag.NEUTRAL, InspectRules.tag(Relationship.NEUTRAL));
    }

    @Test
    void ally_shows_plans_but_takes_orders_only_with_shared_control() {
        for (Info i : PUBLIC)
            assertTrue(InspectRules.shows(i, Relationship.FRIENDLY, false, false), i.name());
        for (Info i : PLANS)
            assertTrue(InspectRules.shows(i, Relationship.FRIENDLY, false, false), i.name());
        assertFalse(InspectRules.shows(Info.COMMAND_CARD, Relationship.FRIENDLY, false, false));
        assertTrue(InspectRules.shows(Info.COMMAND_CARD, Relationship.FRIENDLY, true, false));
        assertEquals(Tag.ALLY, InspectRules.tag(Relationship.FRIENDLY));
        assertEquals("Ally", Tag.ALLY.label);
    }

    @Test
    void control_all_mobs_cheat_commands_anything() {
        assertTrue(InspectRules.canCommand(Relationship.HOSTILE, false, true));
        assertTrue(InspectRules.shows(Info.ABILITIES, Relationship.HOSTILE, false, true));
    }

    @Test
    void only_visible_things_can_be_inspected() {
        assertTrue(InspectRules.mayInspect(true, false, false));
        assertFalse(InspectRules.mayInspect(false, false, false), "under fog");
        assertFalse(InspectRules.mayInspect(true, true, false), "cloaked / in a thicket");
        assertFalse(InspectRules.mayInspect(true, false, true), "a rider");
    }

    @Test
    void an_inspected_thing_never_rides_in_a_group() {
        assertTrue(InspectRules.keepInSelection(1, false), "a lone inspected enemy stays");
        assertFalse(InspectRules.keepInSelection(2, false), "an enemy in a group of 2 is dropped");
        assertTrue(InspectRules.keepInSelection(2, true), "own units in a group stay");
    }

    @Test
    void own_unit_is_owned_even_when_the_alliance_table_lists_self() {
        // the AC-3/AC-4 game tests used the server's unit-to-unit relation, which calls own units FRIENDLY: own
        // cards then lost their command card and own units dropped out of groups
        assertEquals(Relationship.OWNED, InspectRules.relation("me", "me", false));
        assertEquals(Relationship.OWNED, InspectRules.relation("me", "me", true));
        assertEquals(Relationship.FRIENDLY, InspectRules.relation("me", "pal", true));
        assertEquals(Relationship.HOSTILE, InspectRules.relation("me", "foe", false));
        assertEquals(Relationship.NEUTRAL, InspectRules.relation("me", "", false));
        assertTrue(InspectRules.shows(Info.COMMAND_CARD, InspectRules.relation("me", "me", true), false, false));
        assertTrue(InspectRules.keepInSelection(2, InspectRules.canCommand(InspectRules.relation("me", "me", false), false, false)));
    }
}
