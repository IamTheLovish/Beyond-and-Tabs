package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 20_anim (Oct 22 2026, lovish 20:23: "you should find a way to test animations in the cloud somewhere you can
 * simulate them and see them with your own eyes"): ANY unit's animation, played by the real game on a CI runner and
 * photographed frame by frame from fixed cameras, so a Claude session can LOOK at it (tools/anim_eyes.py turns the
 * frames into a review sheet). The Java animations (setupAnim, the dragon rig, keyframe definitions) are written
 * against live entity state - limb swing, swing progress, yaw rate, death time - so the only true render of them is
 * the real client with a real entity doing the thing; this scene makes the entity do it:
 * <ul>
 *   <li>idle   - stands; <b>walk</b> (also: fly) - an order to a point far away, photographed at cruise;</li>
 *   <li>attack - a dummy enemy (AI off) beside it, an attack-move order, a fresh dummy whenever one dies;</li>
 *   <li>turn   - three legs of a route east, north, east (a left turn, then a right one: a flier banks into each);</li>
 *   <li>death  - killed through the damage path at the start of the window.</li>
 * </ul>
 * Cameras (first-person free camera, GUI hidden, locked to the unit's interpolated position every frame so it never
 * jitters, relative to its body heading so a turning flier is seen from the same side all along): side (from its
 * right), front (a three-quarter from the front right) and rear (from behind, slightly right - the view that shows a
 * bank). Each (clip, view) is a fresh unit on a flat pad, 60 blocks out of our base, so a run is independent.
 * <p>
 * Input: -Dbat.eyes.anim=unit:clip[:views[:dist[:ticks]]],... e.g. bone_dragon_unit:turn:rear+side
 * (views joined with +, default side+front+rear; dist = camera distance in blocks, 0/absent = from the unit's size;
 * ticks = the recording window, default per clip; tools/anim_eyes.py parses the same string - keep both in step).
 * eyes.yml input `anim`; its own run only. A frame is captured when
 * the game clock passes the next of ~36 evenly spaced times of the window (never by wall clock: a software-rendered
 * client draws 5-15 frames a second and ticks at its own pace); every capture is logged with its exact game tick,
 * the unit's swing progress, stride and yaw ("[Eyes] anim-frame ..."), which is all tools/anim_eyes.py needs.
 * Debug tooling only: inert unless the property is set, which only the eyes workflow does.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID, value = Dist.CLIENT)
public final class EyesAnimScene {
    private EyesAnimScene() { }

    private static final String SPEC = System.getProperty("bat.eyes.anim", "");
    /** An animation run (eyes.yml input `anim`): the tour is replaced by this scene after the 02 still. */
    static final boolean ANIM_ONLY = !SPEC.isBlank();

    private record Run(String unit, String clip, List<String> views, double dist, int rec) { }

    private static final int FRAMES = 36;                // captures per (clip, view)
    private static final int PAD_R = 26;                 // the pad is 53 x 53
    private static final double WALL_RUN = 150;          // s: a run that long is cut off and the scene goes on
    private static final List<Run> RUNS = parse(SPEC);

    private static List<Run> parse(String spec) {
        List<Run> out = new ArrayList<>();
        for (String entry : spec.split(",")) {
            if (entry.isBlank())
                continue;
            String[] p = entry.trim().split(":");
            // "reignofnether:bone_dragon_unit:turn" has a namespace: drop it, the summon adds our own
            int i = 0;
            String unit = p[i++];
            if (unit.equals(ReignOfNether.MOD_ID) && p.length > 1)
                unit = p[i++];
            String clip = i < p.length ? p[i++] : "idle";
            if (clip.equals("fly"))
                clip = "walk";
            List<String> views = new ArrayList<>();
            for (String v : (i < p.length ? p[i++] : "side+front+rear").split("\\+"))
                if (v.equals("side") || v.equals("front") || v.equals("rear"))
                    views.add(v);
            if (views.isEmpty())
                views = List.of("side", "front", "rear");
            double dist = 0;
            int rec = 0;
            try {
                if (i < p.length)
                    dist = Double.parseDouble(p[i++]);
                if (i < p.length)
                    rec = Integer.parseInt(p[i++]);
            } catch (NumberFormatException e) {
                ReignOfNether.LOGGER.warn("[Eyes] anim: bad distance / ticks in '{}' - defaults used", entry);
            }
            if (rec <= 0)
                rec = switch (clip) {
                    case "walk" -> 80;
                    case "attack" -> 100;
                    case "turn" -> 240;
                    case "death" -> 44;
                    default -> 120;
                };
            out.add(new Run(unit, clip, views, dist, rec));
        }
        return out;
    }

    // ---- the scene's state ----
    private static int phase = 0;
    private static int phaseTicks = 0;
    private static long phaseNanos = 0;
    private static int stepIdx = 0;                  // over all (run, view) pairs
    private static final List<int[]> STEPS = new ArrayList<>();   // { run index, view index }
    private static volatile boolean padPlaced = false;
    private static volatile String padError = null;
    private static Vec3 spot;                        // the pad's centre on the ground
    private static int failed = 0;
    private static int camId = -1;                   // the subject
    private static final List<Integer> dummies = new ArrayList<>();
    private static int leg = 0, legTicks = 0;
    private static int dummyCheckAt = 0;
    // the camera + capture hooks run on the render thread (static fields read by both)
    private static volatile double[] lastPose = null;   // x, feet y, z, yaw, pitch of the last camera
    private static volatile boolean camOn = false;
    private static volatile boolean recording = false;
    private static volatile String camView = "side";
    private static volatile double camDist = 8;
    private static volatile int runNo = 0;
    private static volatile int frameNo = 0;
    private static volatile double nextCapture = 0;
    private static volatile double captureEvery = 3;

    private static double seconds() {
        return (System.nanoTime() - phaseNanos) / 1e9;
    }

    private static void go(int p) {
        phase = p;
        phaseTicks = 0;
        phaseNanos = System.nanoTime();
    }

    /** One client tick of the scene (EyesDirector step 25); true when it is over. */
    static boolean tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) {
            ReignOfNether.LOGGER.warn("[Eyes] 20_anim: no level, player or integrated server - skipped");
            return true;
        }
        phaseTicks++;
        switch (phase) {
            case 0 -> setUp(mc);
            case 1 -> waitPad(mc);
            case 2 -> startRun(mc);
            case 3 -> waitUnit(mc);
            case 4 -> pre(mc);
            case 5 -> rec(mc);
            case 6 -> {
                if (phaseTicks >= 12) {   // the last run's units are gone from the server; the next run starts clean
                    stepIdx++;
                    go(stepIdx >= STEPS.size() ? 7 : 2);
                }
            }
            default -> {
                camOn = false;
                recording = false;
                ReignOfNether.LOGGER.info("[Eyes] 20_anim: done, {} of {} runs failed", failed, STEPS.size());
                return true;
            }
        }
        return false;
    }

    private static void setUp(Minecraft mc) {
        if (RUNS.isEmpty()) {
            ReignOfNether.LOGGER.warn("[Eyes] 20_anim: nothing to play (-Dbat.eyes.anim is empty)");
            go(7);
            return;
        }
        for (int r = 0; r < RUNS.size(); r++)
            for (int v = 0; v < RUNS.get(r).views().size(); v++)
                STEPS.add(new int[] { r, v });
        BlockPos home = EyesDirector.capitolCentre != null ? EyesDirector.capitolCentre : EyesDirector.findCapitol(mc);
        double len = Math.sqrt((double) home.getX() * home.getX() + (double) home.getZ() * home.getZ());
        double dirX = len > 32 ? -home.getX() / len : 1, dirZ = len > 32 ? -home.getZ() / len : 0;
        // 60 blocks out of our base towards the map's middle (as the fog and vfx scenes pick their ground)
        spot = EyesDirector.ground(mc, (int) Math.floor(home.getX() + dirX * 60), (int) Math.floor(home.getZ() + dirZ * 60),
                home.getY());
        if (!OrthoviewClientEvents.isEnabled())
            OrthoviewClientEvents.toggleEnable();
        OrthoviewClientEvents.centreCameraOnPosInstant(spot);   // loads the pad's chunks
        mc.options.hideGui = true;
        UnitClientEvents.clearSelectedUnits();
        placePad(mc);
        ReignOfNether.LOGGER.info("[Eyes] 20_anim: {} run(s) on a pad at {}: {}", STEPS.size(), EyesDirector.fmt(spot), SPEC);
        go(1);
    }

    /** A flat pad (stone under grass, air above) so every clip starts level and nothing stands in the way. */
    private static void placePad(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        int cx = (int) Math.floor(spot.x), cz = (int) Math.floor(spot.z), y = (int) spot.y;
        padPlaced = false;
        padError = null;
        server.execute(() -> {
            try {
                var level = server.overworld();
                var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                var pos = new BlockPos.MutableBlockPos();
                for (int x = cx - PAD_R; x <= cx + PAD_R; x++)
                    for (int z = cz - PAD_R; z <= cz + PAD_R; z++) {
                        level.setBlock(pos.set(x, y - 2, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),
                                net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                        level.setBlock(pos.set(x, y - 1, z), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(),
                                net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                        for (int yy = y; yy <= y + 22; yy++)
                            level.setBlock(pos.set(x, yy, z), air, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                    }
                padPlaced = true;
            } catch (Exception e) {
                padError = e.toString();
            }
        });
    }

    private static void waitPad(Minecraft mc) {
        if (padError != null) {
            ReignOfNether.LOGGER.error("[Eyes] 20_anim: the pad failed: {}", padError);
            go(7);
            return;
        }
        if (!padPlaced) {
            if (seconds() > 60) {
                ReignOfNether.LOGGER.error("[Eyes] 20_anim: the server never finished the pad");
                go(7);
            }
            return;
        }
        if (seconds() < 4)   // the chunks re-mesh
            return;
        go(2);
    }

    private static Run current() {
        return RUNS.get(STEPS.get(stepIdx)[0]);
    }

    private static String view() {
        Run r = current();
        return r.views().get(STEPS.get(stepIdx)[1]);
    }

    private static String owner(Minecraft mc) {
        return mc.player.getName().getString().replace("\"", "");
    }

    private static void summon(Minecraft mc, String owner, String unit, double x, double z, int yaw, String nbt) {
        Vec3 at = EyesDirector.ground(mc, (int) Math.floor(x), (int) Math.floor(z), (int) spot.y);
        EyesDirector.runCommand(mc, String.format("rtsapi-summon \"%s\" %s:%s %d %d %d %s", owner, ReignOfNether.MOD_ID, unit,
                (int) Math.floor(x), (int) at.y, (int) Math.floor(z), nbt.isEmpty() ? "{Rotation:[" + yaw + "f,0f]}" : nbt));
    }

    private static void startRun(Minecraft mc) {
        Run r = current();
        camOn = false;
        recording = false;
        camId = -1;
        dummies.clear();
        leg = 0;
        legTicks = 0;
        runNo = stepIdx;
        frameNo = 0;
        camView = view();
        lastPose = null;
        dummyCheckAt = 0;
        if (OrthoviewClientEvents.isEnabled())
            OrthoviewClientEvents.toggleEnable();   // the free first-person camera takes over from here
        // over the pad, looking at its middle: the chunks round the unit stay loaded while it is summoned
        mc.player.moveTo(spot.x - 9, spot.y + 5, spot.z, -90f, 20f);
        mc.player.setDeltaMovement(Vec3.ZERO);
        // facing east (+x): rotation -90
        summon(mc, owner(mc), r.unit(), spot.x, spot.z, -90, "");
        ReignOfNether.LOGGER.info("[Eyes] 20_anim: run {} - {} / {} / {} summoned at {}", stepIdx, r.unit(), r.clip(), camView,
                EyesDirector.fmt(spot));
        go(3);
    }

    private static void waitUnit(Minecraft mc) {
        Run r = current();
        Entity best = null;
        double bestD = 1e18;
        for (Entity e : mc.level.entitiesForRendering())
            if (e instanceof LivingEntity && !e.isRemoved() && e.getType().toShortString().equals(r.unit())) {
                double d = (e.getX() - spot.x) * (e.getX() - spot.x) + (e.getZ() - spot.z) * (e.getZ() - spot.z);
                if (d < 8 * 8 && d < bestD) {
                    best = e;
                    bestD = d;
                }
            }
        if (best == null) {
            if (seconds() > 20) {
                ReignOfNether.LOGGER.error("[Eyes] 20_anim: run {} - no '{}' appeared after 20 s (is it a registered unit? "
                        + "names are the entity ids, e.g. bone_dragon_unit)", stepIdx, r.unit());
                failed++;
                go(6);
            }
            return;
        }
        camId = best.getId();
        double size = Math.max(best.getBbHeight(), best.getBbWidth() * 1.4);
        camDist = r.dist() > 0 ? r.dist() : Math.max(5.0, 2.3 * size);   // the sheet crops the middle 60% of the screen
        camOn = true;
        ReignOfNether.LOGGER.info("[Eyes] 20_anim: run {} - {} is {} (id {}), hitbox {} x {} blocks, camera {} blocks away", stepIdx,
                r.unit(), best.getClass().getSimpleName(), camId, String.format("%.2f", best.getBbWidth()),
                String.format("%.2f", best.getBbHeight()), String.format("%.1f", camDist));
        go(4);
    }

    private static Entity subject(Minecraft mc) {
        return camId < 0 ? null : mc.level.getEntity(camId);
    }

    private static void order(Minecraft mc, UnitAction action, double x, double z) {
        Vec3 at = EyesDirector.ground(mc, (int) Math.floor(x), (int) Math.floor(z), (int) spot.y);
        UnitClientEvents.sendUnitCommandManual(action, -1, new int[] { camId }, BlockPos.containing(at));
    }

    /** The settle before the window: the clip's orders go out on the first tick, then it waits to be in the motion. */
    private static void pre(Minecraft mc) {
        Run r = current();
        Entity e = subject(mc);
        if (e == null && !r.clip().equals("death")) {
            ReignOfNether.LOGGER.error("[Eyes] 20_anim: run {} - the unit is gone before the window", stepIdx);
            failed++;
            go(6);
            return;
        }
        int wait;
        switch (r.clip()) {
            case "walk" -> {
                if (phaseTicks == 1)
                    order(mc, UnitAction.MOVE, spot.x + 70, spot.z);
                wait = 30;
            }
            case "turn" -> {
                if (phaseTicks == 1)
                    order(mc, UnitAction.MOVE, spot.x + 28, spot.z);
                wait = 30;
            }
            case "attack" -> {
                if (phaseTicks == 1)
                    spawnDummy(mc);
                if (phaseTicks == 10)
                    orderAttack(mc);
                wait = 40;
            }
            default -> wait = 40;   // idle, death: let it settle (spawn drop, first idle cycle)
        }
        if (phaseTicks < wait && seconds() < 60)
            return;
        Entity now = subject(mc);
        captureEvery = Math.max(1.0, r.rec() / (double) FRAMES);
        double clock = mc.level.getGameTime();
        nextCapture = clock;
        frameNo = 0;
        recording = true;
        leg = 0;
        legTicks = 0;
        ReignOfNether.LOGGER.info("[Eyes] anim-run {} {} {} {} window {} ticks from game tick {}, a frame every {} ticks, unit at {}",
                stepIdx, r.unit(), r.clip(), camView, r.rec(), (long) clock, String.format("%.1f", captureEvery),
                now == null ? "?" : EyesDirector.fmt(now.position()));
        go(5);
    }

    private static void spawnDummy(Minecraft mc) {
        String enemy = EyesDirector.findEnemyName(mc).replace("\"", "");
        // a training dummy, AI off, 3 blocks ahead of the unit (east): it never hits back, so the subject is drawn clean
        summon(mc, enemy, "zombie_unit", spot.x + 3, spot.z, 90, "{NoAI:1b}");
    }

    private static void orderAttack(Minecraft mc) {
        order(mc, UnitAction.ATTACK_MOVE, spot.x + 3, spot.z);
    }

    private static void rec(Minecraft mc) {
        Run r = current();
        Entity e = subject(mc);
        switch (r.clip()) {
            case "attack" -> {
                // the dummy dies under the blows: a fresh one when none is left near, and the order again
                if (phaseTicks >= dummyCheckAt) {
                    dummyCheckAt = phaseTicks + 12;
                    int alive = 0;
                    for (Entity d : mc.level.entitiesForRendering())
                        if (d instanceof LivingEntity && !d.isRemoved() && d.getId() != camId
                                && d.getType().toShortString().equals("zombie_unit")
                                && d.distanceToSqr(spot.x, spot.y, spot.z) < 14 * 14)
                            alive++;
                    if (alive == 0) {
                        spawnDummy(mc);
                        orderAttack(mc);
                    }
                }
            }
            case "turn" -> {
                legTicks++;
                if (e != null && leg < 2) {
                    double[][] route = { { 28, 0 }, { 28, -28 }, { 56, -28 } };
                    double tx = spot.x + route[leg][0], tz = spot.z + route[leg][1];
                    double dx = e.getX() - tx, dz = e.getZ() - tz;
                    if ((dx * dx + dz * dz < 6 * 6 || legTicks > 110) && leg < route.length - 1) {
                        leg++;
                        legTicks = 0;
                        order(mc, UnitAction.MOVE, spot.x + route[leg][0], spot.z + route[leg][1]);
                        ReignOfNether.LOGGER.info("[Eyes] anim-leg {} {} to {}", stepIdx, leg, String.format("%.0f, %.0f", route[leg][0], route[leg][1]));
                    }
                }
            }
            case "death" -> {
                if (phaseTicks == 1)
                    killSubject(mc);
            }
            default -> { }
        }
        if (phaseTicks >= r.rec() || seconds() > WALL_RUN) {
            recording = false;
            ReignOfNether.LOGGER.info("[Eyes] anim-run-end {} {} frames in {} ticks ({} s wall)", stepIdx, frameNo, phaseTicks,
                    String.format("%.0f", seconds()));
            if (frameNo < 4) {
                ReignOfNether.LOGGER.error("[Eyes] 20_anim: run {} captured only {} frames", stepIdx, frameNo);
                failed++;
            }
            removeUnits(mc);
            camOn = false;
            go(6);
        }
    }

    private static void killSubject(Minecraft mc) {
        int id = camId;
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try {
                Entity se = server.overworld().getEntity(id);
                if (se instanceof LivingEntity le)
                    le.kill();   // the same path as the game's own kills (Unit death handling, the death animation)
            } catch (Exception ex) {
                ReignOfNether.LOGGER.error("[Eyes] 20_anim: kill failed", ex);
            }
        });
    }

    /** Everything this run summoned goes (on the server thread): the subject and any dummies near the pad. */
    private static void removeUnits(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        double sx = spot.x, sy = spot.y, sz = spot.z;
        List<String> names = List.of(current().unit(), "zombie_unit");
        server.execute(() -> {
            try {
                var level = server.overworld();
                List<Entity> gone = new ArrayList<>();
                for (Entity se : level.getAllEntities())
                    if (se instanceof LivingEntity le && !(le instanceof net.minecraft.world.entity.player.Player)
                            && le.distanceToSqr(sx, sy, sz) < 120 * 120
                            && (names.contains(le.getType().toShortString())))
                        gone.add(se);
                for (Entity se : gone)
                    se.discard();
            } catch (Exception ex) {
                ReignOfNether.LOGGER.error("[Eyes] 20_anim: cleanup failed", ex);
            }
        });
    }

    // ---- the render thread: the camera is placed before every frame, the frame is kept right after it ----

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent evt) {
        if (!ANIM_ONLY)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null)
            return;
        try {
            if (evt.phase == TickEvent.Phase.START) {
                if (camOn)
                    placeCamera(mc, evt.renderTickTime);
            } else if (recording) {
                capture(mc, evt.renderTickTime);
            }
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("[Eyes] 20_anim: render hook failed", e);
        }
    }

    /** Heading of the body in degrees (MC: 0 = south, 90 = west), interpolated like the model's own. */
    private static float bodyYaw(Entity e, float partial) {
        return e instanceof LivingEntity le ? Mth.rotLerp(partial, le.yBodyRotO, le.yBodyRot) : e.getYRot();
    }

    private static void placeCamera(Minecraft mc, float partial) {
        Entity e = mc.level.getEntity(camId);
        if (e == null) {   // gone (a death clip): the camera stays where it was
            if (lastPose != null) {
                mc.player.moveTo(lastPose[0], lastPose[1], lastPose[2], (float) lastPose[3], (float) lastPose[4]);
                mc.player.setDeltaMovement(Vec3.ZERO);
            }
            return;
        }
        Vec3 aim = e.getPosition(partial).add(0, e.getBbHeight() * 0.5, 0);
        double yaw = Math.toRadians(bodyYaw(e, partial));
        double hx = -Math.sin(yaw), hz = Math.cos(yaw);        // the way it faces
        double rx = -hz, rz = hx;                              // its right hand
        double right, ahead, up;
        switch (camView) {
            case "front" -> { right = 0.62; ahead = 0.78; up = 0.25; }
            case "rear" -> { right = 0.15; ahead = -1.0; up = 0.30; }
            default -> { right = 1.0; ahead = 0.0; up = 0.18; }   // side: from its right
        }
        double cx = aim.x + (rx * right + hx * ahead) * camDist;
        double cz = aim.z + (rz * right + hz * ahead) * camDist;
        double cy = aim.y + up * camDist;
        double dx = aim.x - cx, dz = aim.z - cz, dy = aim.y - cy;
        float yawDeg = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitchDeg = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
        double feetY = cy - mc.player.getEyeHeight();
        lastPose = new double[] { cx, feetY, cz, yawDeg, pitchDeg };
        mc.player.moveTo(cx, feetY, cz, yawDeg, pitchDeg);
        mc.player.setDeltaMovement(Vec3.ZERO);
    }

    private static void capture(Minecraft mc, float partial) {
        double clock = mc.level.getGameTime() + partial;
        if (clock < nextCapture)
            return;
        nextCapture = clock + captureEvery;
        int idx = frameNo++;
        String file = String.format("bat_eyes_20_anim_r%02d_%s_f%03d.png", runNo, camView, idx);
        Entity e = mc.level.getEntity(camId);
        String state = "gone";
        if (e != null) {
            float swing = e instanceof LivingEntity le ? le.getAttackAnim(partial) : 0f;
            float limb = e instanceof LivingEntity le2 ? le2.walkAnimation.speed() : 0f;
            state = String.format("swing=%.2f limb=%.2f yaw=%.1f pos=%.1f,%.1f,%.1f", swing, limb, bodyYaw(e, partial),
                    e.getX(), e.getY(), e.getZ());
        }
        ReignOfNether.LOGGER.info("[Eyes] anim-frame {} {} {} {} t={} {}", runNo, camView, idx, file,
                String.format("%.2f", clock), state);
        Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), msg -> { });
    }
}
