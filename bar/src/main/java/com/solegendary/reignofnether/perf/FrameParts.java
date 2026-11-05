package com.solegendary.reignofnether.perf;

import net.minecraft.util.profiling.ProfilerFiller;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Names the client cost of our own systems, the client twin of {@link Sys} (Oct 21 perf plan, PR 1). lovish's log
 * showed the client at 120 fps with no units and 33-55 fps with 600, with 17-23 ms a frame even at strategic zoom where
 * no unit body is drawn, but the frame profile only said how long a frame took, not which of our handlers ate it. Each
 * of our per-frame handlers is a section here; {@link FrameWindow} writes a {@code [FrameParts]} line every 10 s:
 * <ul>
 *   <li>the frame sections (outlines and underlines on units, building boxes, the strategic icons, the minimap, the
 *       HUD, the unit bodies' draw time from {@link AnimProfile}, the selection and range rings): milliseconds per
 *       frame, the worst frame, calls per frame;</li>
 *   <li>the client-tick section ({@code Unit.tick} on the client): milliseconds per client tick;</li>
 *   <li>the section re-mesh requests per second (fog of war marks whole columns dirty).</li>
 * </ul>
 * Every section is also a {@code bat:<name>} section in vanilla's client profiler, so an F3+L report and the
 * {@link FrameAuto} capture name them.
 *
 * Unlike {@link Sys} this is always on while a world is open: a handful of sections per frame is a few hundred
 * nanoseconds, and the per-unit client tick section is 24 ns x units x 20 Hz. Outside a world, or on any thread but
 * the client's, {@code begin} costs one volatile read. No client classes are named here (the facade is reached from
 * Unit.tick, which a dedicated server runs too); {@link FrameWindow} owns the client side.
 *
 * Use: {@code int t = FrameParts.begin(FrameParts.HUD); try { ... } finally { FrameParts.end(t); }}.
 */
public final class FrameParts {
    private FrameParts() { }

    // frame sections (ids below FRAME_SECTIONS), timed per rendered frame
    public static final int UNIT_OVERLAYS = 0;     // UnitClientEvents.onRenderLevel: selection outlines, underlines
    public static final int BUILDING_BOXES = 1;    // BuildingClientEvents.onRenderLevel: boxes, ghost, fog blocks
    public static final int STRATEGIC_ICONS = 2;   // StrategicViewClientEvents: the icon overlay at far zoom
    public static final int MINIMAP = 3;           // MinimapClientEvents.onRenderOverlay
    public static final int HUD = 4;               // HudClientEvents.onDrawScreen: the RTS HUD
    public static final int UNIT_BODIES = 5;       // the units' own render in the view (AnimProfile: posing + drawing)
    public static final int UNIT_RINGS = 6;        // StrategicViewClientEvents.onRenderLevel: selection and range rings
    // Oct 22: lovish's capture had 13.6% of the frame "unspecified" in after_translucent_blocks - the handlers below
    // were not measured. Each is one handler group, so the next [FrameParts] / [FrameAuto] line names the cost.
    public static final int BUILDING_PARTS = 7;    // PartRenderClientEvents: animated building parts (real geometry)
    public static final int BUILDING_DECOR = 8;    // statues, windmills, banners, scaffolds
    public static final int BARS = 9;              // health bars, build / production progress bars
    public static final int WORLD_FX = 10;         // BarFx glow/smoke/decals, ground memory
    public static final int WORLD_MARKERS = 11;    // metal/resource markers, wrecks, map + start markers, grids, lines
    // Oct 22 (unit render LOD): with a shader pack every unit is drawn a second time into the pack's shadow map.
    // That draw is filed here instead of under unitBodies, which is now the view's draw alone (the same as before
    // without a pack), so the shadow cost and what the LOD saves there show on their own.
    public static final int UNIT_SHADOW = 12;      // the unit bodies drawn into the shader pack's shadow map
    // Oct 23: worldMarkers split by handler (nested inside it, so worldMarkers itself is the dispatch + gating rest):
    // the sum of the three is the old worldMarkers number
    public static final int MARKERS_RANGE = 13;    // BlockClientEvents: range indicator highlights
    public static final int MARKERS_METAL = 14;    // MetalPatchMarkerClientEvents: rings and gems
    public static final int MARKERS_LINE = 15;
    public static final int MARKERS_GRID = 16;
    public static final int MARKERS_CHUNKS = 17;
    public static final int MARKERS_MAP = 18;
    public static final int MARKERS_DRAW = 19;
    public static final int MARKERS_FLOATTEXT = 20;
    public static final int MARKERS_WRECK = 21;
    public static final int MARKERS_STARTPOS = 22;
    public static final int MARKERS_GATE = 23;     // the shadow-pass query (reflective Iris call) in MapClient and BlockClientEvents
    public static final int MARKERS_MAPWORLD = 24; // MapClient.renderWorld
    public static final int MARKERS_RANGE_REFRESH = 25;  // BlockClientEvents.refreshRangeSubsets
    public static final int MARKERS_RANGE_UNITS = 26;    // BlockClientEvents: the rangeUnits loop
    // Oct 31: lovish's Oct 29 profiles had vanilla's level>entities "unspecified" at 4.2-4.4 ms a frame with 500+ units,
    // the unit bodies only 1.2-2.3 ms of it. EntityShadowMixin draws every entity's round shadow at full strength in the
    // RTS view (units, items, orbs): a block box walked per entity. Timed here so the next log says what it costs.
    public static final int ENTITY_SHADOWS = 27;   // EntityRenderDispatcher.renderShadow, every entity (EntityShadowMixin)
    public static final int FRAME_SECTIONS = 28;
    // client-tick sections, timed per client tick
    public static final int UNIT_TICK = 28;        // Unit.tick on the client
    public static final int SECTIONS = 29;

    static final String[] NAMES = { "unitOverlays", "buildingBoxes", "strategicIcons", "minimap", "hud", "unitBodies",
            "unitRings", "buildingParts", "buildingDecor", "bars", "worldFx", "worldMarkers", "unitShadow", "markersRange", "markersMetal", "markersLine", "markersGrid", "markersChunks", "markersMap", "markersDraw", "markersFloatText", "markersWreck", "markersStartPos", "markersGate", "markersMapWorld", "markersRangeRefresh", "markersRangeUnits", "entityShadows", "unitTick" };
    private static final String[] PROFILER_NAMES = new String[SECTIONS];
    static {
        for (int i = 0; i < SECTIONS; i++)
            PROFILER_NAMES[i] = BatSections.PREFIX + "client/" + NAMES[i];
    }

    private static final SysTimer FRAME = new SysTimer(FRAME_SECTIONS, System::nanoTime);
    private static final SysTimer TICK = new SysTimer(SECTIONS - FRAME_SECTIONS, System::nanoTime);
    private static final ProfilerFiller[] PUSHED = new ProfilerFiller[2 * (SysTimer.MAX_DEPTH + 1)];

    private static volatile boolean on = false;
    private static volatile Thread clientThread = null;
    /** Set by FrameWindow (client side): the client's current profiler. */
    static volatile Supplier<ProfilerFiller> profilers = null;

    /** Section re-mesh requests (LevelRenderer.setSectionDirty) since the last [FrameParts] line. */
    static final AtomicLong DIRTY_SECTIONS = new AtomicLong();

    /** Opens a section; give the result to {@link #end}. Returns 0 (and does nothing) unless in a world on the client thread. */
    public static int begin(int id) {
        if (!on || Thread.currentThread() != clientThread)
            return 0;
        boolean tick = id >= FRAME_SECTIONS;
        int token = tick ? TICK.begin(id - FRAME_SECTIONS) : FRAME.begin(id);
        if (token <= 0)
            return 0;
        Supplier<ProfilerFiller> p = profilers;
        ProfilerFiller profiler = p == null ? null : p.get();
        if (profiler != null)
            profiler.push(PROFILER_NAMES[id]);
        int slot = tick ? token + SysTimer.MAX_DEPTH + 1 : token;
        PUSHED[slot] = profiler;
        // the token carries which timer opened it: the tick timer's tokens are offset past the frame timer's
        return slot;
    }

    public static void end(int slot) {
        if (slot <= 0)
            return;
        ProfilerFiller profiler = PUSHED[slot];
        if (profiler != null) {
            PUSHED[slot] = null;
            profiler.pop();
        }
        if (slot > SysTimer.MAX_DEPTH)
            TICK.end(slot - SysTimer.MAX_DEPTH - 1);
        else
            FRAME.end(slot);
    }

    /** Adds time measured elsewhere to a frame section (AnimProfile already clocks each unit body). */
    public static void add(int id, long nanos) {
        if (on && id < FRAME_SECTIONS && Thread.currentThread() == clientThread)
            FRAME.add(id, nanos);
    }

    /**
     * Self nanoseconds frame section {@code id} has spent since the game started (never reset, unlike the 10 s
     * [FrameParts] window): the eyes overlay scene differences two readings round each of its slices. Client thread.
     */
    public static long frameSectionLifetimeNs(int id) {
        return FRAME.lifetimeNs[id];
    }

    /** Frames closed since the game started (the denominator for {@link #frameSectionLifetimeNs}). Client thread. */
    public static long framesLifetime() {
        return FRAME.lifetimeTicks;
    }

    /** The section's name as the [FrameParts] line prints it. */
    public static String name(int id) {
        return NAMES[id];
    }

    /** Counts section re-mesh requests (any thread). */
    public static void dirtySections(int count) {
        DIRTY_SECTIONS.addAndGet(count);
    }

    // ---- FrameWindow's side ---------------------------------------------------------------------------------------

    static void setOn(boolean value) {
        if (value && !on)
            clientThread = Thread.currentThread();
        on = value;
    }

    static SysTimer frameTimer() {
        return FRAME;
    }

    static SysTimer tickTimer() {
        return TICK;
    }
}
