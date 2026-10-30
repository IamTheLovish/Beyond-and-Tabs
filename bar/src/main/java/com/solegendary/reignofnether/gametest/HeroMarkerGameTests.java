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
            var show = look.getMethod("showMarker", boolean.class, boolean.class);
            if (!(boolean) show.invoke(null, true, true))
                problems.add("no marker on a hero in the RTS view");
            if ((boolean) show.invoke(null, false, true))
                problems.add("a marker on a unit that is not a hero");
            if ((boolean) show.invoke(null, true, false))
                problems.add("a marker on a hero outside the RTS view (first person)");
            var badge = look.getMethod("showPointBadge", boolean.class, int.class);
            if (!(boolean) badge.invoke(null, true, 1))
                problems.add("no unspent-point badge on the viewer's own hero holding a point");
            if ((boolean) badge.invoke(null, true, 0) || (boolean) badge.invoke(null, false, 2))
                problems.add("an unspent-point badge on a hero with no point, or on another player's hero");
            var ring = look.getMethod("ringRadius", float.class);
            for (float width : new float[] { 0.6f, 1.0f, 1.4f }) {
                float r = (float) ring.invoke(null, width), body = width * scale / 2;
                if (r <= body || r > body + 1)
                    problems.add(String.format("the ring (radius %.2f) does not sit just outside a drawn body %.2f wide", r, width * scale));
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
}
