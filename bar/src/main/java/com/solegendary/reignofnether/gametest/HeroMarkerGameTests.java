package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Heroes stand out in a fight (design-draft-heroes-better-2026-10-30 #4; lovish, Oct 30: "Ring + crest, 1.25x"). A hero
 * was its troop body in a new skin at 1.1x (HeroRenderers.HERO_SCALE) and nothing in the RTS view marked it. The rules
 * the client draws by live in hero/HeroLook (common code, so a server test can hold them): heroes at 1.25x; a
 * player-colour ring round the feet, just outside the drawn body, and a crest over the head - for heroes only, and only
 * in the RTS view. The drawing itself is client-only (HeroMarkerClientEvents) and is proven by eyes clips. HeroLook is
 * looked up by name, so this test compiles on main, where it does not exist yet (tests first).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class HeroMarkerGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void hero_marker_rules_scale_ring_and_rts_view_only(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        try {
            Class<?> look = Class.forName("com.solegendary.reignofnether.hero.HeroLook");
            float scale = look.getField("SCALE").getFloat(null);
            if (Math.abs(scale - 1.25f) > 1e-4)
                problems.add("heroes are drawn at " + scale + "x, not 1.25x");
            var show = look.getMethod("showMarker", boolean.class, boolean.class, boolean.class, boolean.class);
            // (is a hero, RTS view, in the viewer's sight, cloaked from the viewer)
            if (!(boolean) show.invoke(null, true, true, true, false))
                problems.add("no marker on a hero in the RTS view, in sight");
            if ((boolean) show.invoke(null, false, true, true, false))
                problems.add("a marker on a unit that is not a hero");
            if ((boolean) show.invoke(null, true, false, true, false))
                problems.add("a marker on a hero outside the RTS view (first person)");
            if ((boolean) show.invoke(null, true, true, false, false))
                problems.add("a marker on a hero under the fog of war (it gives away its spot)");
            if ((boolean) show.invoke(null, true, true, true, true))
                problems.add("a marker on a hero cloaked from the viewer");
            if ((boolean) show.invoke(null, true, true, false, true))
                problems.add("a marker on a hero both under the fog and cloaked from the viewer");
            var badge = look.getMethod("showPointBadge", boolean.class, int.class);
            if (!(boolean) badge.invoke(null, true, 1))
                problems.add("no unspent-point badge on the viewer's own hero holding a point");
            if (!(boolean) badge.invoke(null, true, 5))
                problems.add("no unspent-point badge on the viewer's own hero holding five points");
            if ((boolean) badge.invoke(null, true, 0) || (boolean) badge.invoke(null, false, 2) || (boolean) badge.invoke(null, false, 0))
                problems.add("an unspent-point badge on a hero with no point, or on another player's hero");
            var ring = look.getMethod("ringRadius", float.class);
            var crest = look.getMethod("crestHeight", float.class);
            for (float width : new float[] { 0.6f, 1.0f, 1.4f, 2.0f }) {
                // the drawn body is the hitbox times the scale: the ring sits a quarter to half a block outside it (a
                // ring measured on the unscaled hitbox falls inside that window for none of these widths)
                float r = (float) ring.invoke(null, width), body = width * scale / 2;
                if (r - body < 0.25f || r - body > 0.5f)
                    problems.add(String.format("the ring (radius %.2f) does not sit just outside a drawn body %.2f wide", r, width * scale));
            }
            for (float height : new float[] { 1.8f, 2.4f }) {
                // over the drawn head (hitbox height times the scale), and not lost far above it
                float c = (float) crest.invoke(null, height), head = height * scale;
                if (c <= head || c > head + 1.5f)
                    problems.add(String.format("the crest floats at %.2f over the feet, not just over a drawn head at %.2f", c, head));
            }
        } catch (ClassNotFoundException e) {
            problems.add("there is no hero/HeroLook: heroes are 1.1x and unmarked");
        } catch (ReflectiveOperationException e) {
            problems.add("hero/HeroLook is missing a rule: " + e);
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * A hero the game cloaks (a Verdant thicket's cover, a cloak spell) carries no marker for its enemies. The RTS viewer
     * is a spectator, and vanilla's isInvisibleTo lets a spectator see invisible bodies, so
     * UnitClientEvents.isHiddenFromLocalPlayer is false for it: eyes run 41097850239 (PR 443's scene) logged the enemy
     * Arch-Engineer in its grown thicket as concealed on the server yet "hidden from us false, in sight true" on our
     * client - a ring and crest would have shown where it hides. The rule asks only what the client knows: the body is
     * invisible, and whether the viewer owns it or is allied to its owner (those keep the marker on their own cloaked
     * hero).
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void hero_marker_hides_an_enemy_hero_the_game_cloaks(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        try {
            Class<?> look = Class.forName("com.solegendary.reignofnether.hero.HeroLook");
            var cloaked = look.getMethod("cloakedFromViewer", boolean.class, boolean.class);
            // (the body is invisible, the viewer owns it or is allied to its owner)
            if (!(boolean) cloaked.invoke(null, true, false))
                problems.add("an enemy hero the game cloaks is not cloaked from an RTS viewer (its marker would show it)");
            if ((boolean) cloaked.invoke(null, true, true))
                problems.add("the viewer's own or allied cloaked hero counts as cloaked from them (it would lose its marker)");
            if ((boolean) cloaked.invoke(null, false, false) || (boolean) cloaked.invoke(null, false, true))
                problems.add("a hero that is not cloaked counts as cloaked from the viewer");
        } catch (ClassNotFoundException e) {
            problems.add("there is no hero/HeroLook: heroes are 1.1x and unmarked");
        } catch (ReflectiveOperationException e) {
            problems.add("hero/HeroLook has no cloak rule: the marker asks isHiddenFromLocalPlayer, false for an RTS "
                + "viewer, so it shows an enemy hero the game cloaks (" + e + ")");
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
