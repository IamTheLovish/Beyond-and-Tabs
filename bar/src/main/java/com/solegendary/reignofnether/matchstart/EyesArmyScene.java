package com.solegendary.reignofnether.matchstart;

import com.mojang.blaze3d.platform.NativeImage;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.orthoview.StrategicViewClientEvents;
import com.solegendary.reignofnether.perf.AnimProfile;
import com.solegendary.reignofnether.perf.FrameParts;
import com.solegendary.reignofnether.perf.UnitTypeCost;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.modelling.instancing.UnitInstancing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FrameTimer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 21_army: what a big army's bodies cost a frame, by army size and unit type, and - once the instanced unit bodies
 * exist (unit/modelling/instancing) - the old and the instanced renderer side by side in ONE run, and a proof that the
 * instanced frame shows the same picture (lovish, Nov 6: "Big-army performance"; the Oct 22 instancing plan's gate).
 * <p>
 * The scene (eyes.yml input {@code army}; after 02 nothing else of the tour runs):
 * <ul>
 * <li>a 30 x 20 block of units one block apart under the camera - every one in view at zoom 30 - seven types, a column
 *     each in turn: three generated bodies (Halberdier = vindicator_unit, Crossbow Levy = pillager_unit, Squire Runner =
 *     militia_unit), a hand-made keyframe body (royal_guard_unit) and three on vanilla models (zombie, skeleton,
 *     piglin grunt). Rows alternate between our units and the bot's, so both team colours are drawn. All {@code NoAI}
 *     (they stand: no fight, no drift between the slices);</li>
 * <li>the army grows 100 -> 300 -> 600 (the cells nearest the camera first), and at each size the frame is measured at
 *     zoom 30 (the default, detail on) and zoom 60 (the crowd band under the icon switch): slices of {@link #SLICE_S}
 *     s after {@link #SETTLE_S} s, with the instanced path OFF, ON, ON, OFF (ABBA, so a drift over the run cancels);
 *     without the instanced path the ON slices are left out;</li>
 * <li>per slice: frame p50 / p95, the unit sections of [FrameParts] (unitBodies, the instanced draw, entityShadows)
 *     and the HUD and minimap as the frame's yardsticks (eyes runs compare by ratio inside one run, never raw ms
 *     across runs: CI renders in software), units drawn and instanced per frame, and per type ms/frame and us/unit
 *     ({@link UnitTypeCost});</li>
 * <li>the parity check at 600 units, zoom 30, the world paused (a menu-less pause screen, so nothing moves between
 *     frames): two old frames (the floor: what differs with nothing changed), a frame with every unit hidden (the
 *     control: the check must see the army - else it proves nothing), two instanced frames and a last old one. The
 *     verdict and the rule it applies were fixed before the first run ({@link #PARITY_DELTA}, {@link #PARITY_SLACK}).
 *     Stills: 21_army_old, 21_army_instanced, 21_army_diff (|old - instanced| x 8) and 21_army_z60.</li>
 * </ul>
 * Wall-clock timeline (the shader lane's client runs a few ticks a second).
 */
final class EyesArmyScene {
    private EyesArmyScene() { }

    static final boolean ONLY = Boolean.getBoolean("bat.eyes.army");

    private static final String[] TYPES = { "vindicator_unit", "pillager_unit", "militia_unit", "royal_guard_unit",
            "zombie_unit", "skeleton_unit", "grunt_unit" };
    private static final int COLS = 30, ROWS = 20;
    private static final int[] COUNTS = { 100, 300, 600 };
    private static final int ZOOM_NORMAL = 30, ZOOM_CROWD = 60;
    private static final int[] ZOOMS = { ZOOM_NORMAL, ZOOM_CROWD };
    private static final double SETTLE_S = 3, SLICE_S = 6;
    /**
     * The first slice of an army size starts only once the view draws the army: at least this share of the units the
     * client knows, per frame (run 42189253540's first 100-unit slice measured 4.7 villagers - the camera was not there
     * yet). Waits at most {@link #READY_MAX_S} more seconds, then measures anyway and says so. Fixed before any result,
     * the same for the old and the instanced path.
     */
    private static final double READY_SHARE = 0.8, READY_MAX_S = 30;
    private static long readyFrames0 = 0, readyUnits0 = 0;
    private static final int SUMMONS_PER_TICK = 25;
    /** A pixel differs when a channel moved by more than this (of 255): the rounding of light and edges stays under. */
    static final int PARITY_DELTA = 16;
    /** The instanced frame may differ from the old one by the floor (old vs old, new vs new, old vs old later) plus this
     *  share of the frame's pixels - and the control (no units) must differ by at least {@link #CONTROL_MIN}. */
    static final double PARITY_SLACK = 0.001, CONTROL_MIN = 0.01;

    private static final int[] SECTIONS_FIXED = { FrameParts.UNIT_BODIES, FrameParts.ENTITY_SHADOWS, FrameParts.HUD,
            FrameParts.MINIMAP };

    private static int phase = 0;
    private static long phaseNanos = 0;
    private static String me, enemy;
    private static Vec3 centre;
    private static final List<int[]> cells = new ArrayList<>();   // x, z, column, row - nearest the centre first
    private static int summoned = 0, countIndex = 0;

    // the measurement
    private static final List<Boolean> plan = new ArrayList<>();      // per slice: instanced on?
    private static final List<Integer> planZoom = new ArrayList<>();
    private static int slice = -1;
    private static boolean measuring = false;
    private static long sliceNanos = 0, frames0 = 0, units0 = 0, inst0 = 0;
    private static final long[] ns0 = new long[8];
    private static Map<Object, long[]> types0 = new IdentityHashMap<>();
    private static final List<Double> frames = new ArrayList<>();
    private static int frameCursor = -1;
    /** Per slice: count, zoom, reached, strategic, on, frames, p50, p95, unitsPerFrame, instPerFrame, then sections. */
    private static final List<double[]> results = new ArrayList<>();
    private static final List<String> typeLines = new ArrayList<>();

    // the parity check
    private static boolean listening = false;
    private static boolean hideUnits = false;
    private static int captureStep = -1, captureWait = 0;
    private static final String[] CAPTURE = { "old1", "old2", "hidden", "new1", "new2", "old3", "newNoTr" };
    private static final NativeImage[] captured = new NativeImage[CAPTURE.length];
    private static boolean captureDone = false;

    private static double seconds() {
        return (System.nanoTime() - phaseNanos) / 1e9;
    }

    private static void next(int p) {
        phase = p;
        phaseNanos = System.nanoTime();
    }

    /** One client tick of the scene; true when it is over (the caller ends the tour). */
    static boolean tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            ReignOfNether.LOGGER.warn("[Eyes] 21_army: no level or player - skipped");
            return true;
        }
        switch (phase) {
            case 0 -> setUp(mc);
            case 1 -> {   // the camera moved: wait for the ground (the server summons only on loaded ground)
                boolean loaded = EyesDirector.surfaceY(mc, (int) Math.floor(centre.x), (int) Math.floor(centre.z)) != Integer.MIN_VALUE;
                if ((!loaded && seconds() < 25) || seconds() < 4)
                    return false;
                centre = EyesDirector.ground(mc, (int) Math.floor(centre.x), (int) Math.floor(centre.z), (int) centre.y);
                planCells();
                ReignOfNether.LOGGER.info("[Eyes] 21_army: a {} x {} block of {} types round {}, enemy '{}', instanced path {}",
                        COLS, ROWS, TYPES.length, EyesDirector.fmt(centre), enemy,
                        UnitInstancing.available() ? "available" : "not available (old renderer only)");
                next(2);
            }
            case 2 -> {   // summon up to the next army size
                int target = COUNTS[countIndex];
                int end = Math.min(target, summoned + SUMMONS_PER_TICK);
                for (; summoned < end; summoned++)
                    summon(mc, summoned);
                if (summoned >= target)
                    next(3);
            }
            case 3 -> {   // wait for them
                int n = knownArmy();
                if (n < COUNTS[countIndex] - 10 && seconds() < 40)
                    return false;
                if (seconds() < 4)
                    return false;
                ReignOfNether.LOGGER.info("[Eyes] 21_army: {} units summoned, {} of them known to the client", summoned, n);
                planSlices();
                startSlice(mc, 0);
                next(4);
            }
            case 4 -> {   // the slices of this army size
                if (measure(mc))
                    return false;
                countIndex++;
                if (countIndex < COUNTS.length) {
                    next(2);
                    return false;
                }
                summary();
                EyesDirector.setZoom(ZOOM_CROWD);
                OrthoviewClientEvents.centreCameraOnPosInstant(centre);
                next(5);
            }
            case 5 -> {   // still at the crowd zoom, then back to the default for the parity check
                if (seconds() < 4)
                    return false;
                EyesDirector.shot(mc, "21_army_z60");
                EyesDirector.setZoom(ZOOM_NORMAL);
                OrthoviewClientEvents.centreCameraOnPosInstant(centre);
                if (UnitInstancing.available()) {
                    UnitInstancing.setOverride(true);
                    UnitInstancing.startVertexParity(3);
                }
                next(6);
            }
            case 6 -> {   // the same-frame vertex check (instanced only), then freeze the world for the pixel check
                if (seconds() < 4)
                    return false;
                if (UnitInstancing.available()) {
                    String report = UnitInstancing.vertexParityReport();
                    if (report == null && seconds() < 20)
                        return false;
                    ReignOfNether.LOGGER.info("[Eyes] 21_army vertex parity: {}", report == null ? "no verdict in 20 s - FAIL" : report);
                }
                UnitInstancing.setOverride(false);
                mc.options.hideGui = true;
                mc.setScreen(new Freeze());
                next(7);
            }
            case 7 -> {   // paused: capture the frames
                if (seconds() < 2 || !mc.isPaused())
                    return seconds() > 15 ? parityGiveUp(mc, "the world never paused") : false;
                if (captureStep < 0) {
                    listen();
                    captureDone = false;
                    captureStep = 0;
                    captureWait = 10;
                    setMode(CAPTURE[0]);
                }
                if (!captureDone)
                    return seconds() > 40 ? parityGiveUp(mc, "the frames were not captured in 40 s") : false;
                parityVerdict(mc);
                unfreeze(mc);
                return true;
            }
            default -> {
                return true;
            }
        }
        return false;
    }

    private static void setUp(Minecraft mc) {
        if (!OrthoviewClientEvents.isEnabled())
            OrthoviewClientEvents.toggleEnable();
        me = mc.player.getName().getString();
        enemy = EyesDirector.findEnemyName(mc);
        BlockPos home = EyesDirector.capitolCentre != null ? EyesDirector.capitolCentre : EyesDirector.findCapitol(mc);
        // beside our base, across the line to the map's middle (as 18_overlays): out of a bot attack's way
        double len = Math.sqrt((double) home.getX() * home.getX() + (double) home.getZ() * home.getZ());
        double dirX = 0, dirZ = 1;
        if (len > 32) {
            dirX = home.getZ() / len;
            dirZ = -home.getX() / len;
        }
        centre = new Vec3(Math.floor(home.getX() + dirX * 70) + 0.5, home.getY(), Math.floor(home.getZ() + dirZ * 70) + 0.5);
        EyesDirector.setZoom(ZOOM_NORMAL);
        OrthoviewClientEvents.centreCameraOnPosInstant(centre);
        summoned = 0;
        countIndex = 0;
        results.clear();
        typeLines.clear();
        next(1);
    }

    /** The grid's cells, nearest the centre first: each army size is the block's middle. */
    private static void planCells() {
        cells.clear();
        int x0 = (int) Math.floor(centre.x) - COLS / 2, z0 = (int) Math.floor(centre.z) - ROWS / 2;
        for (int col = 0; col < COLS; col++)
            for (int row = 0; row < ROWS; row++)
                cells.add(new int[] { x0 + col, z0 + row, col, row });
        double cx = centre.x, cz = centre.z;
        cells.sort(Comparator.comparingDouble(c -> (c[0] + 0.5 - cx) * (c[0] + 0.5 - cx) + (c[1] + 0.5 - cz) * (c[1] + 0.5 - cz)));
    }

    /** Unit k: its cell; the type by column, the owner by row (ours, the bot's, ours ...), standing still (NoAI). */
    private static void summon(Minecraft mc, int k) {
        int[] c = cells.get(k);
        int y = EyesDirector.surfaceY(mc, c[0], c[1]);
        if (y == Integer.MIN_VALUE)
            y = (int) centre.y;
        String owner = c[3] % 2 == 0 ? me : enemy;
        EyesDirector.runCommand(mc, String.format(Locale.ROOT, "rtsapi-summon \"%s\" %s:%s %d %d %d {NoAI:1b}",
                owner.replace("\"", ""), ReignOfNether.MOD_ID, TYPES[c[2] % TYPES.length], c[0], y, c[1]));
    }

    private static int knownArmy() {
        int n = 0;
        double r2 = (COLS * COLS + ROWS * ROWS) / 4.0 + 16;
        for (LivingEntity le : UnitClientEvents.getAllUnits())
            if (le instanceof Unit && le.isAlive() && le.position().distanceToSqr(centre.x, le.getY(), centre.z) <= r2)
                n++;
        return n;
    }

    // ---- the measurement ----

    private static void planSlices() {
        plan.clear();
        planZoom.clear();
        boolean ab = UnitInstancing.available();
        for (int zoom : ZOOMS) {
            boolean[] modes = ab ? new boolean[] { false, true, true, false } : new boolean[] { false, false };
            for (boolean on : modes) {
                plan.add(on);
                planZoom.add(zoom);
            }
        }
    }

    private static int[] sections() {
        int inst = UnitInstancing.frameSection();
        if (inst < 0)
            return SECTIONS_FIXED;
        int[] s = new int[SECTIONS_FIXED.length + 1];
        System.arraycopy(SECTIONS_FIXED, 0, s, 0, SECTIONS_FIXED.length);
        s[SECTIONS_FIXED.length] = inst;
        return s;
    }

    private static void startSlice(Minecraft mc, int i) {
        slice = i;
        measuring = false;
        sliceNanos = System.nanoTime();
        UnitInstancing.setOverride(plan.get(i));
        EyesDirector.setZoom(planZoom.get(i));
        OrthoviewClientEvents.centreCameraOnPosInstant(centre);
        readyFrames0 = FrameParts.framesLifetime();
        readyUnits0 = AnimProfile.viewUnitsLifetime();
    }

    /** The first slice of a size: does the view draw the army yet (units per frame since the last look)? */
    private static boolean armyInView() {
        long f = FrameParts.framesLifetime() - readyFrames0;
        if (f < 2)
            return false;
        double perFrame = (double) (AnimProfile.viewUnitsLifetime() - readyUnits0) / f;
        readyFrames0 = FrameParts.framesLifetime();
        readyUnits0 = AnimProfile.viewUnitsLifetime();
        return perFrame >= READY_SHARE * knownArmy();
    }

    /** True while this army size's slices run. */
    private static boolean measure(Minecraft mc) {
        double s = (System.nanoTime() - sliceNanos) / 1e9;
        if (!measuring) {
            if (s < SETTLE_S)
                return true;
            if (slice == 0 && !armyInView()) {
                if (s < SETTLE_S + READY_MAX_S)
                    return true;
                ReignOfNether.LOGGER.info("[Eyes] 21_army: the view drew under {}% of the {} known units for {} s; "
                    + "measuring anyway", Math.round(READY_SHARE * 100), knownArmy(), Math.round(s));
            }
            measuring = true;
            sliceNanos = System.nanoTime();
            frames0 = FrameParts.framesLifetime();
            units0 = AnimProfile.viewUnitsLifetime();
            inst0 = UnitInstancing.instancedLifetime();
            int[] sec = sections();
            for (int k = 0; k < sec.length; k++)
                ns0[k] = FrameParts.frameSectionLifetimeNs(sec[k]);
            types0 = UnitTypeCost.lifetimeAll();
            frames.clear();
            frameCursor = mc.getFrameTimer().getLogEnd();
            return true;
        }
        sampleFrames(mc);
        if (s < SLICE_S)
            return true;
        endSlice();
        if (slice + 1 < plan.size()) {
            startSlice(mc, slice + 1);
            return true;
        }
        UnitInstancing.setOverride(null);
        return false;
    }

    private static void sampleFrames(Minecraft mc) {
        FrameTimer timer = mc.getFrameTimer();
        long[] log = timer.getLog();
        int end = timer.getLogEnd();
        for (int guard = 0; frameCursor != end && guard < log.length; guard++) {
            if (log[frameCursor] > 0)
                frames.add(log[frameCursor] / 1e6);
            frameCursor = timer.wrapIndex(frameCursor + 1);
        }
    }

    private static void endSlice() {
        long n = Math.max(1, FrameParts.framesLifetime() - frames0);
        double[] ms = frames.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        int[] sec = sections();
        double[] row = new double[10 + sec.length];
        row[0] = COUNTS[countIndex];
        row[1] = planZoom.get(slice);
        row[2] = OrthoviewClientEvents.getZoom();
        row[3] = StrategicViewClientEvents.isStrategicView() ? 1 : 0;
        row[4] = plan.get(slice) ? 1 : 0;
        row[5] = n;
        row[6] = pct(ms, 0.5);
        row[7] = pct(ms, 0.95);
        row[8] = (double) (AnimProfile.viewUnitsLifetime() - units0) / n;
        row[9] = (double) (UnitInstancing.instancedLifetime() - inst0) / n;
        StringBuilder parts = new StringBuilder();
        for (int k = 0; k < sec.length; k++) {
            row[10 + k] = (FrameParts.frameSectionLifetimeNs(sec[k]) - ns0[k]) / 1e6 / n;
            parts.append(k == 0 ? "" : ", ").append(FrameParts.name(sec[k])).append(' ').append(f2(row[10 + k]));
        }
        results.add(row);
        // per type: what each body costs in this slice
        Map<Object, long[]> now = UnitTypeCost.lifetimeAll();
        List<Object[]> types = new ArrayList<>();
        for (Map.Entry<Object, long[]> e : now.entrySet()) {
            long[] was = types0.getOrDefault(e.getKey(), new long[2]);
            long dns = e.getValue()[0] - was[0], dn = e.getValue()[1] - was[1];
            if (dn > 0)
                types.add(new Object[] { e.getKey(), dns, dn });
        }
        types.sort((a, b) -> Long.compare((long) b[1], (long) a[1]));
        StringBuilder byType = new StringBuilder();
        for (Object[] t : types)
            byType.append(byType.length() == 0 ? "" : "; ").append(UnitTypeCost.name(t[0], AnimProfile::typeName))
                    .append(String.format(Locale.ROOT, " %.1f/frame %.2f ms %.1f us", (long) t[2] / (double) n,
                            (long) t[1] / 1e6 / n, (long) t[1] / 1e3 / (long) t[2]));
        String label = label(row);
        typeLines.add(label + " | " + byType);
        ReignOfNether.LOGGER.info("[Eyes] 21_army slice {}/{}: {} (reached zoom {}, {}), {} frames: p50 {} ms, p95 {} ms, "
                        + "{} units drawn/frame, {} instanced/frame | ms/frame: {} | by type (units/frame, ms/frame, us/unit): {}",
                slice + 1, plan.size(), label, Math.round(row[2]), row[3] > 0 ? "strategic" : "normal", n, f1(row[6]),
                f1(row[7]), f1(row[8]), f1(row[9]), parts, byType);
    }

    private static String label(double[] row) {
        return String.format(Locale.ROOT, "%d units, zoom %d, instanced %s", (int) row[0], (int) row[1],
                row[4] > 0 ? "on" : "off");
    }

    /** Per army size, zoom and mode: the medians; then per size and zoom the on/off ratio of the unit sections. */
    private static void summary() {
        int[] sec = sections();
        for (int count : COUNTS)
            for (int zoom : ZOOMS) {
                double[] off = null, on = null;
                for (int mode = 0; mode <= 1; mode++) {
                    List<double[]> rows = new ArrayList<>();
                    for (double[] r : results)
                        if ((int) r[0] == count && (int) r[1] == zoom && (int) r[4] == mode)
                            rows.add(r);
                    if (rows.isEmpty())
                        continue;
                    double[] med = new double[10 + sec.length];
                    for (int c = 0; c < med.length; c++)
                        med[c] = median(rows, c);
                    StringBuilder parts = new StringBuilder();
                    for (int k = 0; k < sec.length; k++)
                        parts.append(k == 0 ? "" : ", ").append(FrameParts.name(sec[k])).append(' ').append(f2(med[10 + k]));
                    double bodies = unitCost(med, sec);
                    ReignOfNether.LOGGER.info("[Eyes] 21_army summary {} units, zoom {}, instanced {} (medians of {} slices): "
                                    + "frame p50 {} ms, p95 {} ms, {} units drawn/frame, {} instanced/frame | ms/frame: {} | "
                                    + "unit bodies in all {} ms, {} us per drawn unit", count, zoom, mode == 1 ? "on" : "off",
                            rows.size(), f1(med[6]), f1(med[7]), f1(med[8]), f1(med[9]), parts, f2(bodies),
                            f1(med[8] > 0 ? bodies * 1000 / med[8] : 0));
                    if (mode == 0)
                        off = med;
                    else
                        on = med;
                }
                if (off != null && on != null) {
                    double a = unitCost(off, sec), b = unitCost(on, sec);
                    ReignOfNether.LOGGER.info("[Eyes] 21_army ratio {} units, zoom {}: unit bodies off {} ms -> on {} ms "
                                    + "(x{} faster), frame p50 {} -> {} ms", count, zoom, f2(a), f2(b),
                            f2(b > 0 ? a / b : 0), f1(off[6]), f1(on[6]));
                }
            }
        for (String t : typeLines)
            ReignOfNether.LOGGER.info("[Eyes] 21_army types {}", t);
    }

    /** unitBodies plus the instanced draw's own section (when the build has one). */
    private static double unitCost(double[] med, int[] sec) {
        double sum = 0;
        for (int k = 0; k < sec.length; k++)
            if (sec[k] == FrameParts.UNIT_BODIES || sec[k] == UnitInstancing.frameSection())
                sum += med[10 + k];
        return sum;
    }

    // ---- the parity check ----

    /** A pause screen that draws nothing: the world stops (the integrated server and the client's level) and stays seen. */
    static final class Freeze extends Screen {
        Freeze() {
            super(Component.literal("eyes freeze"));
        }

        @Override
        public boolean isPauseScreen() {
            return true;
        }

        @Override
        public void renderBackground(GuiGraphics graphics) {
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        }
    }

    private static void listen() {
        if (listening)
            return;
        listening = true;
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, RenderLivingEvent.Pre.class, e -> {
            if (hideUnits && e.getEntity() instanceof Unit)
                e.setCanceled(true);
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.RenderTickEvent.class, EyesArmyScene::onFrame);
    }

    /** After every frame while capturing: wait, grab, then set up the next frame's mode. */
    private static void onFrame(TickEvent.RenderTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || captureStep < 0 || captureDone)
            return;
        String name = CAPTURE[captureStep];
        boolean wantsNew = name.startsWith("new");
        if (wantsNew && !UnitInstancing.available()) {   // nothing to compare with: skip the instanced frames
            captureStep++;
            captureWait = 10;
            setMode(CAPTURE[captureStep]);
            return;
        }
        if (captureWait-- > 0)
            return;
        Minecraft mc = Minecraft.getInstance();
        try {
            captured[captureStep] = Screenshot.takeScreenshot(mc.getMainRenderTarget());
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("[Eyes] 21_army parity: capture {} failed", name, e);
        }
        captureStep++;
        if (captureStep >= CAPTURE.length) {
            hideUnits = false;
            UnitInstancing.setOverride(false);
            UnitInstancing.setTranslucentOff(false);
            captureDone = true;
            return;
        }
        setMode(CAPTURE[captureStep]);
        captureWait = CAPTURE[captureStep].endsWith("2") ? 3 : 10;   // a twin a few frames on; a new mode settles longer
    }

    private static void setMode(String name) {
        hideUnits = name.equals("hidden");
        UnitInstancing.setOverride(name.startsWith("new"));
        UnitInstancing.setTranslucentOff(name.equals("newNoTr"));   // diagnostic: translucent bodies the old way
    }

    private static boolean parityGiveUp(Minecraft mc, String why) {
        ReignOfNether.LOGGER.error("[Eyes] 21_army parity: FAIL - {}", why);
        unfreeze(mc);
        return true;
    }

    private static void unfreeze(Minecraft mc) {
        captureStep = -1;
        hideUnits = false;
        UnitInstancing.setOverride(null);
        UnitInstancing.setTranslucentOff(false);
        if (mc.screen instanceof Freeze)
            mc.setScreen(null);
        mc.options.hideGui = false;
    }

    /** Pixels whose largest channel change exceeds PARITY_DELTA, of the frame; -1 if either frame is missing. */
    static long differing(NativeImage a, NativeImage b) {
        if (a == null || b == null || a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight())
            return -1;
        long n = 0;
        for (int y = 0; y < a.getHeight(); y++)
            for (int x = 0; x < a.getWidth(); x++)
                if (maxDelta(a.getPixelRGBA(x, y), b.getPixelRGBA(x, y)) > PARITY_DELTA)
                    n++;
        return n;
    }

    static int maxDelta(int p, int q) {
        int d = 0;
        for (int s = 0; s < 24; s += 8)   // the three colour channels (alpha is always opaque in the main target)
            d = Math.max(d, Math.abs(((p >> s) & 0xFF) - ((q >> s) & 0xFF)));
        return d;
    }

    private static void parityVerdict(Minecraft mc) {
        NativeImage old1 = captured[0], old2 = captured[1], hidden = captured[2], new1 = captured[3], new2 = captured[4],
                old3 = captured[5];
        if (old1 == null) {
            ReignOfNether.LOGGER.error("[Eyes] 21_army parity: FAIL - no old frame captured");
            return;
        }
        long px = (long) old1.getWidth() * old1.getHeight();
        long floorOld = differing(old1, old2), control = differing(old1, hidden), drift = differing(old1, old3);
        boolean controlOk = control >= CONTROL_MIN * px;
        save(mc, old1, "21_army_old");
        String controlText = String.format(Locale.ROOT, "control (no units) %d px (%.2f%%) - %s", control, 100.0 * control / px,
                controlOk ? "the check sees the army" : "the check does NOT see the army");
        if (!UnitInstancing.available()) {
            ReignOfNether.LOGGER.info("[Eyes] 21_army parity: old renderer only (no instanced path in this build) - floor "
                    + "old vs old {} px, old vs old later {} px, {}", floorOld, drift, controlText);
            if (!controlOk)
                ReignOfNether.LOGGER.error("[Eyes] 21_army parity: FAIL - {}", controlText);
            closeAll();
            return;
        }
        long parity = differing(old1, new1), floorNew = differing(new1, new2);
        long floor = Math.max(Math.max(floorOld, floorNew), drift);
        long allowed = floor + (long) Math.floor(PARITY_SLACK * px);
        boolean pass = controlOk && parity >= 0 && floor >= 0 && parity <= allowed;
        save(mc, new1, "21_army_instanced");
        saveDiff(mc, old1, new1, "21_army_diff");
        diagnostics(mc);
        ReignOfNether.LOGGER.info("[Eyes] 21_army parity: {} - old vs instanced {} px ({}%) differ by more than {}/255, "
                        + "allowed {} (floor {}: old vs old {}, instanced vs instanced {}, old vs old later {}; + {}% of {} px); {}",
                pass ? "PASS" : "FAIL", parity, f2(100.0 * parity / px), PARITY_DELTA, allowed, floor, floorOld, floorNew,
                drift, f2(100 * PARITY_SLACK), px, controlText);
        closeAll();
    }

    /**
     * Not the verdict: where the frames part, so a FAIL names its cause. The other frames saved, and old vs a frame
     * drawn instanced except the translucent bodies (PlayerModel units, which go the old way in it).
     */
    private static void diagnostics(Minecraft mc) {
        NativeImage old1 = captured[0], old2 = captured[1], new2 = captured[4], old3 = captured[5], noTr = captured[6];
        save(mc, old2, "21_army_old2");
        save(mc, old3, "21_army_old3");
        save(mc, new2, "21_army_instanced2");
        saveDiff(mc, old1, old2, "21_army_diff_old_old");
        if (noTr != null) {
            save(mc, noTr, "21_army_instanced_tr_old");
            saveDiff(mc, old1, noTr, "21_army_diff_tr_old");
        }
        ReignOfNether.LOGGER.info("[Eyes] 21_army parity diagnostic (not the verdict): old vs instanced with the translucent "
                + "bodies the old way {} px differ by more than {}/255", differing(old1, noTr), PARITY_DELTA);
    }

    private static void closeAll() {
        for (int i = 0; i < captured.length; i++)
            if (captured[i] != null) {
                captured[i].close();
                captured[i] = null;
            }
    }

    private static File shotFile(Minecraft mc, String name) {
        File dir = new File(mc.gameDirectory, "screenshots");
        dir.mkdirs();
        return new File(dir, "bat_eyes_" + name + ".png");
    }

    private static void save(Minecraft mc, NativeImage img, String name) {
        if (img == null)
            return;
        try {
            img.writeToFile(shotFile(mc, name));
            ReignOfNether.LOGGER.info("[Eyes] clip-mark {} {}", name, System.currentTimeMillis());
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("[Eyes] 21_army: saving {} failed", name, e);
        }
    }

    /** |a - b| per channel, x8, on black: where the two frames part. */
    private static void saveDiff(Minecraft mc, NativeImage a, NativeImage b, String name) {
        if (a == null || b == null || a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight())
            return;
        try (NativeImage d = new NativeImage(a.getWidth(), a.getHeight(), false)) {
            for (int y = 0; y < a.getHeight(); y++)
                for (int x = 0; x < a.getWidth(); x++) {
                    int p = a.getPixelRGBA(x, y), q = b.getPixelRGBA(x, y), out = 0xFF000000;
                    for (int s = 0; s < 24; s += 8)
                        out |= Math.min(255, 8 * Math.abs(((p >> s) & 0xFF) - ((q >> s) & 0xFF))) << s;
                    d.setPixelRGBA(x, y, out);
                }
            d.writeToFile(shotFile(mc, name));
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("[Eyes] 21_army: saving {} failed", name, e);
        }
    }

    private static double median(List<double[]> rows, int col) {
        double[] v = rows.stream().mapToDouble(r -> r[col]).sorted().toArray();
        return v.length % 2 == 1 ? v[v.length / 2] : (v[v.length / 2 - 1] + v[v.length / 2]) / 2;
    }

    private static double pct(double[] sorted, double p) {
        return sorted.length == 0 ? 0 : sorted[(int) Math.round((sorted.length - 1) * p)];
    }

    private static String f1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String f2(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
