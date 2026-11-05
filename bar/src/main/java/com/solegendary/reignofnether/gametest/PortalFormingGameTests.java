package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.barfx.BarFx;
import com.solegendary.reignofnether.barfx.FormingTimeline;
import com.solegendary.reignofnether.barfx.PortalForming;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.gameender.GameEnder;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The forming moment (barfx/PortalForming, FormingTimeline, FormingFx): a portal-type building that finishes - a
 * portal of any kind, a game ender - does not switch on at once; one FORMING effect plays over ~1.6 s (a spark at the
 * frame, light filling inward, a swirl settling, a rising charge and a settle). lovish, Nov 5: "our own version" of the
 * idea that a big moment needs a build-up. These tests pin the rules the GameTests can see: which buildings form (read
 * off the registry), once per activation and never for a building that was finished the moment it appeared, the fog
 * of war (a viewer whose fog hides the whole site is sent nothing, so neither sees nor hears it), nothing left in the
 * queue after the tick, and the timeline's shape. The look itself is in the eyes scene 20_forming.
 * <p>
 * Placements here are never put in the world or the building list (a piglin portal's own onBuilt would start a nether
 * conversion zone in the arena); the Sun Spire goes through the real {@code BuildingPlacement.onBuilt}, since a game
 * ender's onBuilt has no side effect beyond the shared one. Events are counted at each placement's own spot, which
 * no other test uses.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class PortalFormingGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** Portal-type, read off the registry and not off the code under test: an id that names a portal, or a game ender. */
    static boolean portalType(ResourceLocation id, Building b) {
        return id.getPath().contains("portal") || b instanceof GameEnder;
    }

    /** FORMING events queued this tick within a block or two of a placement's footprint centre. */
    static int formingAt(ServerLevel level, BuildingPlacement bp) {
        double cx = (bp.minCorner.getX() + bp.maxCorner.getX() + 1) / 2.0, cz = (bp.minCorner.getZ() + bp.maxCorner.getZ() + 1) / 2.0;
        return BarFx.queuedAt(level, BarFx.FORMING, cx, cz, 2.0).size();
    }

    static BuildingPlacement make(GameTestHelper helper, Building b, int x, int z, String owner, long age) {
        BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(b, helper.getLevel(), helper.absolutePos(new BlockPos(x, 2, z)),
            Rotation.NONE, owner, false);
        if (bp != null)
            bp.tickAge = age;
        return bp;
    }

    /**
     * Which buildings form: every registered building whose id names a portal (the piglins' basic, civilian,
     * military, transport and pocket portals, the Central and Infernal Portals, the neutral End and transport
     * portals) and every game ender, and no other building. Then the game path: the Sun Spire finishing
     * (BuildingPlacement.onBuilt) and a basic portal finishing (the shared hook) each queue exactly one FORMING
     * event; a second completion of the same placement queues none; a building finished the moment it appeared (a
     * map's, a fresh save's) and a finished wind generator queue none. The event names its style (portal / game
     * ender), the owner's faction and the footprint, and the next tick's flush leaves nothing behind.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void portal_forming_fires_once_per_activation_for_every_portal_type(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_forming_" + helper.absolutePos(BlockPos.ZERO).asLong();
        List<String> problems = new ArrayList<>();
        int portals = 0, enders = 0, others = 0;
        for (Building b : ReignOfNetherRegistries.BUILDING) {
            ResourceLocation id = ReignOfNetherRegistries.BUILDING.getKey(b);
            if (id == null)
                continue;
            boolean expected = portalType(id, b);
            if (b.formsWhenBuilt() != expected)
                problems.add(id.getPath() + (expected ? " is portal-type but does not form" : " is not portal-type but forms"));
            if (b instanceof GameEnder)
                enders++;
            else if (expected)
                portals++;
            else
                others++;
        }
        if (portals < 9 || enders < 7 || others < 20)
            problems.add("the registry read " + portals + " portals, " + enders + " game enders and " + others
                + " other buildings - too few, the rule read the wrong registry?");

        BuildingPlacement spire = make(helper, Buildings.SUN_SPIRE, 2, 2, owner, PortalForming.MIN_AGE_TICKS);
        BuildingPlacement portal = make(helper, Buildings.PORTAL_BASIC, 2, 14, owner, PortalForming.MIN_AGE_TICKS);
        BuildingPlacement fresh = make(helper, Buildings.SUN_SPIRE, 14, 2, owner, 0);
        BuildingPlacement wind = make(helper, Buildings.WIND_GENERATOR_VILLAGERS, 14, 14, owner, PortalForming.MIN_AGE_TICKS);
        if (spire == null || portal == null || fresh == null || wind == null) {
            helper.fail("could not make the placements: " + spire + " " + portal + " " + fresh + " " + wind);
            return;
        }
        // the Sun Spire through the real completion; the portal through the shared hook (its own onBuilt would start a
        // nether zone in the arena)
        spire.onBuilt();
        if (formingAt(level, spire) != 1)
            problems.add("the Sun Spire finishing queued " + formingAt(level, spire) + " FORMING events, not 1");
        if (PortalForming.onBuilt(spire))
            problems.add("the Sun Spire formed a second time on the same activation");
        spire.isBuilt = false;
        spire.onBuilt();   // the same placement completed again (an RTS-API reset): still the one moment
        if (formingAt(level, spire) != 1)
            problems.add("a second completion of the Sun Spire left " + formingAt(level, spire) + " FORMING events, not 1");

        if (!PortalForming.onBuilt(portal))
            problems.add("a basic portal finishing did not form");
        if (PortalForming.onBuilt(portal))
            problems.add("a basic portal formed twice");
        if (formingAt(level, portal) != 1)
            problems.add("the basic portal queued " + formingAt(level, portal) + " FORMING events, not 1");

        if (PortalForming.onBuilt(fresh) || formingAt(level, fresh) != 0)
            problems.add("a game ender finished the moment it appeared (tick age 0) formed - a map's or a save's would replay");
        if (PortalForming.onBuilt(wind) || formingAt(level, wind) != 0)
            problems.add("a wind generator formed: only portal-type buildings get the moment");

        List<BarFx.Event> sp = BarFx.queuedAt(level, BarFx.FORMING, spire.centrePos.getX() + .5, spire.centrePos.getZ() + .5, 2.0);
        List<BarFx.Event> po = BarFx.queuedAt(level, BarFx.FORMING, portal.centrePos.getX() + .5, portal.centrePos.getZ() + .5, 2.0);
        if (sp.size() == 1) {
            BarFx.Event e = sp.get(0);
            if (e.kind != BarFx.FS_ENDER)
                problems.add("the Sun Spire's forming is not in the game-ender style: " + e.kind);
            if ((e.flags & BarFx.FACTION_MASK) != BarFx.F_SUNFORGED)
                problems.add("the Sun Spire's forming does not carry the Sunforged tint: " + e.flags);
            if (e.a < 1 || e.b < 1)
                problems.add("the Sun Spire's forming has no footprint: half " + e.a + ", height " + e.b);
        }
        if (po.size() == 1 && po.get(0).kind != BarFx.FS_PORTAL)
            problems.add("the portal's forming is not in the portal style: " + po.get(0).kind);
        if (!problems.isEmpty()) {
            helper.fail(String.join("; ", problems));
            return;
        }
        // the tick's flush sends the queue and keeps nothing: one tick on, nothing is left at either spot
        helper.runAfterDelay(1, () -> {
            int left = formingAt(level, spire) + formingAt(level, portal);
            if (left != 0)
                helper.fail(left + " FORMING events still queued a tick later - the flush left them behind");
            else
                helper.succeed();
        });
    }

    /**
     * The fog of war: FORMING goes to a fogged player only when their fog shows some part of the site - its centre or
     * any corner of its footprint, like a collapsing building - so a hidden enemy portal forming is neither shown nor
     * heard (the client plays the sound from this one event). A viewer who sees nothing, or a lit spot well beside the
     * site, is sent nothing. The COLLAPSE rule is the control: the same corner sight sees a collapse on today's code.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void portal_forming_is_hidden_from_a_viewer_whose_fog_hides_the_whole_site(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        float cx = 100.5f, cz = 200.5f, half = 3.5f;
        BarFx.Event forming = new BarFx.Event(BarFx.FORMING, BarFx.FS_PORTAL, BarFx.F_HORDE, cx, 64, cz, half, 6f, 0);
        BarFx.Event collapse = new BarFx.Event(BarFx.COLLAPSE, (byte) 0, (byte) 0, cx, 64, cz, half, 6f, 0);
        BarFx.Sight nothing = (x, z) -> false;
        BarFx.Sight beside = (x, z) -> Math.abs(x - (cx + 20)) < 3 && Math.abs(z - cz) < 3;
        BarFx.Sight oneCorner = (x, z) -> x >= cx + half - .01 && z >= cz + half - .01;   // only the north-east corner's spot
        BarFx.Sight centre = (x, z) -> Math.abs(x - cx) < 1 && Math.abs(z - cz) < 1;
        if (!BarFx.visibleTo(oneCorner, collapse))
            problems.add("control: a corner sight does not see a collapse (the Sight harness is broken)");
        if (BarFx.visibleTo(nothing, forming))
            problems.add("a viewer whose fog shows nothing was sent the forming");
        if (BarFx.visibleTo(beside, forming))
            problems.add("a viewer who sees only a spot 20 blocks beside the portal was sent the forming");
        if (!BarFx.visibleTo(oneCorner, forming))
            problems.add("a viewer who sees a corner of the forming portal was not sent it");
        if (!BarFx.visibleTo(centre, forming))
            problems.add("a viewer who sees the portal's centre was not sent the forming");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * The timeline every client plays (FormingTimeline, pure): 1-2 s long; spark, fill, swirl and settle start in that
     * order with no dead gap between them, the first at 0 and the last ending with the effect; the fill's front moves
     * inward from the frame (1) to the centre (0) and the swirl's radius never grows; each phase emits its pieces as a
     * running total, so any frame rate (144, 30, 7 fps or one step) emits the same count and nothing after the end; a
     * small site still gets every phase; the biggest site stays within the piece budget; done() turns true at the end
     * (FormingFx then drops the entry: nothing is left behind).
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void portal_forming_timeline_spark_fill_swirl_settle_in_1_to_2_seconds(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        float d = FormingTimeline.DURATION;
        if (!(d >= 1f && d <= 2f))
            problems.add("the forming lasts " + d + " s, not 1-2 s");
        FormingTimeline.Phase[] ph = FormingTimeline.Phase.values();
        if (ph.length != 4 || ph[0] != FormingTimeline.Phase.SPARK || ph[3] != FormingTimeline.Phase.SETTLE)
            problems.add("the phases are not spark, fill, swirl, settle");
        if (ph.length > 0 && ph[0].start != 0f)
            problems.add("the spark does not start at once: " + ph[0].start);
        if (ph.length > 0 && Math.abs(ph[ph.length - 1].end - d) > 1e-4)
            problems.add("the settle does not end with the effect: " + ph[ph.length - 1].end + " vs " + d);
        for (int i = 0; i < ph.length; i++) {
            if (!(ph[i].end > ph[i].start))
                problems.add(ph[i] + " ends before it starts");
            if (i > 0 && !(ph[i].start > ph[i - 1].start && ph[i].start <= ph[i - 1].end))
                problems.add(ph[i] + " does not follow " + ph[i - 1] + " without a gap");
        }
        FormingTimeline.Phase fill = FormingTimeline.Phase.FILL, swirl = FormingTimeline.Phase.SWIRL;
        if (Math.abs(FormingTimeline.fillFront(fill.start) - 1f) > 1e-3 || Math.abs(FormingTimeline.fillFront(fill.end)) > 1e-3)
            problems.add("the fill does not run from the frame (1) to the centre (0): " + FormingTimeline.fillFront(fill.start)
                + " -> " + FormingTimeline.fillFront(fill.end));
        float lastFront = 2f, lastSwirl = Float.MAX_VALUE;
        for (int i = 0; i <= 100; i++) {
            float f = FormingTimeline.fillFront(fill.start + (fill.end - fill.start) * i / 100f);
            if (f > lastFront + 1e-5f)
                problems.add("the fill's front moved outward at step " + i);
            lastFront = f;
            float r = FormingTimeline.swirlRadius(swirl.start + (swirl.end - swirl.start) * i / 100f);
            if (r > lastSwirl + 1e-5f)
                problems.add("the swirl widened at step " + i);
            lastSwirl = r;
        }
        float small = FormingTimeline.scaleFor(1f), big = FormingTimeline.scaleFor(40f);
        int bigTotal = 0;
        for (float scale : new float[] { small, 1f, big }) {
            for (FormingTimeline.Phase p : ph) {
                int whole = FormingTimeline.emitted(p, d, scale);
                if (whole <= 0)
                    problems.add(p + " emits nothing at scale " + scale);
                if (scale == big)
                    bigTotal += whole;
                for (float dt : new float[] { 1f / 144f, 1f / 30f, 1f / 7f, d + 1f }) {
                    int sum = 0;
                    for (float t = 0; t < d + 1f; t += dt)
                        sum += FormingTimeline.between(p, t, Math.min(t + dt, d + 1f), scale);
                    if (sum != whole)
                        problems.add(p + " at scale " + scale + " emits " + sum + " at a " + dt + " s frame, " + whole + " in one step");
                }
                if (FormingTimeline.between(p, d, d + 5f, scale) != 0)
                    problems.add(p + " still emits after the end");
            }
        }
        if (bigTotal > FormingTimeline.MAX_PIECES)
            problems.add("the biggest site emits " + bigTotal + " pieces, over the budget " + FormingTimeline.MAX_PIECES);
        if (FormingTimeline.done(d - .01f) || !FormingTimeline.done(d))
            problems.add("done() is not true exactly from the end");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }
}
