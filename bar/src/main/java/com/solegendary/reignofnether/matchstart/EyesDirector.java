package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.SmartSelect;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.unit.units.villagers.MilitiaUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * "Eyes" (lovish, Oct 12: "add yourself some eyes"): a scripted tour that only runs when the client is started with
 * -Dbat.eyes=true, as .github/workflows/eyes.yml does on a CI runner (real client, Xvfb + Mesa, nothing on anyone's
 * PC). It photographs the title screen, starts a small Quick Battle against one bot, photographs the RTS view at
 * three zoom levels and a minute into the match, then (Oct 13, "eyes tour 2") stages what the first tour never
 * showed - the build menu, an army, a drag box, a fight at both zooms, the capitol at two minutes - and quits. The
 * workflow publishes the PNGs to the `eyes` branch so the developer AI can look at what a player sees - HUD layout,
 * icons, skins, terrain, projectile tints, selection rings - instead of guessing.
 *
 * Scenes 00-05 are unchanged so old comparisons stay valid. The staged scenes, and how each is driven:
 * <ul>
 * <li>06_build_menu: an owned worker is selected the way a click does it (UnitClientEvents.addSelectedUnit +
 *     HudClientEvents.setHudSelectedEntity); the worker's card IS the build menu (its ability buttons carry the
 *     faction's building buttons, VillagerUnit.getAbilityButtons).</li>
 * <li>07_army_close: units are spawned with the game's own op command, /rtsapi-summon &lt;owner&gt; &lt;entity&gt;
 *     &lt;pos&gt;, sent from the client exactly as a typed chat command is (ClientPacketListener.sendCommand). The
 *     skirmish world is created with cheats on (QuickBattle: LevelSettings allowCommands = true), so the singleplayer
 *     player is op and the server runs it. Spawning needs server authority, which is why it is a command and not a
 *     client call. The enemy owner is the bot's live roster name (PlayerClientEvents.rtsPlayers).</li>
 * <li>08_box_select: a left drag is replayed through the same Forge screen events a real mouse produces
 *     (ScreenEvent.MouseButtonPressed / MouseDragged / MouseButtonReleased, Pre then Post, in GUI coordinates), so
 *     CursorClientEvents runs its box, SmartSelect filters it and the release selects it. If every press is refused
 *     (a panel under it) or the release selects nothing, SmartSelect.filterBox is applied directly to the owned units
 *     round the army and the selection set by hand, so the HUD still shows a selected army; the log says which.</li>
 * <li>09_fight / 10_fight_strategic: attack-move is the packet a right-click with Attack armed sends
 *     (UnitClientEvents.sendUnitCommandManual(ATTACK_MOVE, ids, target)); the shot waits for contact - a projectile
 *     in the level, a death on either side, or the armies' centroids within 8 blocks (20 s cap, the log says which) -
 *     then one more second with the camera centred between the two armies; 10 follows two seconds later.</li>
 * <li>11_capitol_two_minutes: the capitol (BuildingClientEvents, isCapitol) at the default zoom once the match clock
 *     (counted from the 02 shot, labelled 0:30) passes 2:00. 11b_capitol_selected (Oct 22, slim HUD): the same
 *     capitol selected, 30 ticks later - the building's icon block, health bar, Repeat toggle and production queue.</li>
 * <li>12_river_crossing (Oct 14, terrain_intelligence.md): a pad with a 4-wide, 1-deep water strip is laid with the
 *     op fill command 30 blocks west of the first fight, a dozen of the player's units are summoned on its west bank
 *     and eight enemies on its east bank, and the dozen is attack-moved across; photographed at contact and six
 *     seconds later. The log line counts who stands in the water and on which bank - the army should cross in a
 *     line and fight on the far bank, not in the river.</li>
 * <li>17_fog (Oct 21, perf plan R1, EyesFogScene): the last scene - fog of war on, a dozen marching out of our base
 *     into the dark; the fog re-mesh A/B (surface band vs whole column) and the fog edge before and after a full
 *     re-mesh.</li>
 * <li>18_overlays (Oct 21, perf plan R2, EyesOverlaysScene; its own run, eyes.yml input `overlays`): 500 units and
 *     1,700 buildings, stills of the underlines and boxes at a mid zoom and at strategic zoom, and the frame and
 *     per-handler times at zoom 30, 60 (crowd) and 200 - the before / after of the overlay loops and unit LOD.</li>
 * </ul>
 * Every scene has a tick budget and a "gave up" line; the staged steps are wrapped so an exception logs and moves on
 * instead of killing the run. Each scene logs one "[Eyes] scene: ..." line describing what is on screen.
 *
 * Inert in every normal game: the property is never set by a launcher.
 */
public final class EyesDirector {
    private EyesDirector() { }

    public static final boolean ENABLED = Boolean.getBoolean("bat.eyes");

    private static int step = 0;
    private static int ticks = 0;
    private static int totalTicks = 0;
    private static int titleSince = -1;   // tick (within step 0) at which the title screen first showed
    // a hard stop so a stuck tour never holds a CI runner (20 min at 20 tps)
    private static final int GIVE_UP_TICKS = 20 * 60 * 20;

    // ---- the staged scenes (06-11) ----
    // the match clock: totalTicks at which the match was at 0:00 (the 02 shot is labelled 0:30)
    private static int matchZeroTotalTick = -1;
    static BlockPos capitolCentre = null;          // (package: EyesFogScene)
    private static Vec3 armyCentre = null;        // where the player's army was spawned
    private static Vec3 enemyCentre = null;       // where the enemy army was spawned
    private static String enemyName = null;
    private static int shotAt = -1;               // tick (within the current step) at which its photograph is due
    private static boolean feedbackOff = false;   // sendCommandFeedback switched off before the first command
    private static int boxPhase = 0, boxPhaseStart = 0;   // 08's press / drag / shot / release / settle / shot
    private static int boxCandidate = 0;          // which press point the drag is trying
    private static double boxX0, boxY0, boxX1, boxY1;   // the drag box in GUI coordinates
    private static boolean boxPressed = false;
    private static int[] armyIds = new int[0];    // the units ordered into the fight
    private static int mineAtOrder = 0, enemyAtOrder = 0;   // units alive per side when the attack-move went out
    private static final int ZOOM_NORMAL = 30;    // OrthoviewClientEvents.ZOOM_DEFAULT
    private static int closeUpAt = -1;            // 07b-s: tick of the close-up's next step
    private static int closeUpPhase = 0;          // still, walking, fighting
    private static int closeUpRetried = -1;       // the subject (CLOSE_UPS index) already restarted once
    private static final int ZOOM_STRATEGIC = 85; // past StrategicViewClientEvents.STRATEGIC_ZOOM_ENTER (70), below
                                                  // the big-unit switch (70 * 1.35): icons, but a golem keeps its model

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (!ENABLED || evt.phase != TickEvent.Phase.END)
            return;
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        if (++totalTicks > GIVE_UP_TICKS) {
            ReignOfNether.LOGGER.error("[Eyes] gave up at step {}", step);
            mc.stop();
            return;
        }
        // options.txt overrideWidth was ignored on the runner (shots stayed 854x480): size the window ourselves
        if (totalTicks == 40)
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1600, 900);
        // a breadcrumb every 30 s, so a stuck tour shows where it is stuck in the CI log
        if (totalTicks % (20 * 30) == 0)
            ReignOfNether.LOGGER.info("[Eyes] step {} screen {} level {}", step,
                    mc.screen == null ? "none" : mc.screen.getClass().getName(), mc.level != null);
        switch (step) {
            case 0 -> {   // title screen settled: photograph it, then start a small battle
                // another mod's first-launch screen (warnings, onboarding) can sit in front of the title screen:
                // photograph it once so we can see what it was, then go to the title screen ourselves
                if (!(mc.screen instanceof TitleScreen) && mc.screen != null && ticks == 20 * 90) {
                    shot(mc, "00_unexpected_screen");
                    mc.setScreen(new TitleScreen());
                    titleSince = ticks;
                }
                if (mc.screen instanceof TitleScreen && titleSince < 0)
                    titleSince = ticks;
                // give the title screen a few seconds to render (and its fade-in to finish) before photographing it
                if (mc.screen instanceof TitleScreen && ticks > 200 && ticks - titleSince > 20 * 4) {
                    shot(mc, "01_title");
                    logPacks(mc);
                    SkirmishSetupScreen.Settings s = new SkirmishSetupScreen.Settings();
                    s.faction = 0;
                    s.team = 0;
                    s.arena = 0;
                    s.metal = 1;
                    s.map = 0;
                    s.seed = EYES_SEED;          // the same world every run, so stills compare run to run (eyes_diff.py)
                    // the start-position phase runs (01b-01f photograph its markers); the director places our start
                    // itself, three seconds before the match, so the later scenes see the same seating as before
                    s.choosePositions = true;
                    // a fixed sun: stills taken minutes apart compare run to run (the lobby's default is a slow cycle)
                    s.timeOfDay = com.solegendary.reignofnether.match.TimeOfDay.MORNING.ordinal();
                    s.dayCycle = com.solegendary.reignofnether.match.DayCycle.OFF.ordinal();
                    // Settings already holds the default 1v1 bot; adding another made it a 1v2 ("Enemies 2")
                    if (s.bots.isEmpty())
                        s.bots.add(new SkirmishSetupScreen.Bot(1));
                    s.bots.get(0).faction = 1;
                    QuickBattle.launch(mc.screen, s);
                    next();
                }
            }
            case 1 -> {   // the start-position phase first (01b-01f), then the match half a minute in
                if (!startBoxesDone) {
                    sceneStartBoxes(mc);
                    return;
                }
                if (lookOffAt < 0 && mc.level != null && mc.player != null && OrthoviewClientEvents.isEnabled() && ticks > 20 * 30) {
                    shot(mc, "02_rts_default");
                    matchZeroTotalTick = totalTicks - 20 * 30;
                    groundCensus(mc, "0:30");
                    // the same frame with the world look OFF: the pass's exact before/after, whatever the map
                    com.solegendary.reignofnether.postfx.WorldLookPass.lookOverride = com.solegendary.reignofnether.config.DisplaySettings.LOOK_OFF;
                    lookOffAt = ticks + 5;
                } else if (lookOffAt > 0 && ticks >= lookOffAt) {
                    shot(mc, "02b_rts_default_look_off");
                    com.solegendary.reignofnether.postfx.WorldLookPass.lookOverride = null;
                    lookOffAt = -1;
                    if (SCARS_ONLY) {   // a scars run (eyes.yml input `scars`): only the aftermath scene, then the end
                        step = STEP_SCARS;
                        ticks = 0;
                        return;
                    }
                    if (FOG_ONLY) {     // a fog run (eyes.yml input `fog`): only the fog scene, then the end
                        step = STEP_FOG;
                        ticks = 0;
                        return;
                    }
                    if (OVERLAYS_ONLY) {   // an overlays run (eyes.yml input `overlays`): only scene 18, then the end
                        step = STEP_OVERLAYS;
                        ticks = 0;
                        return;
                    }
                    if (!STRUCTURES.isEmpty()) {   // a structure photo run: only the structures, then the end
                        step = STEP_STRUCTURES;
                        ticks = 0;
                        return;
                    }
                    OrthoviewClientEvents.zoomCam(-1000);   // all the way in
                    next();
                }
            }
            case 2 -> {
                if (ticks > 20 * 4) {
                    shot(mc, "03_rts_close");
                    OrthoviewClientEvents.zoomCam(1000);    // all the way out
                    next();
                }
            }
            case 3 -> {
                if (ticks > 20 * 6) {
                    shot(mc, "04_rts_far");
                    OrthoviewClientEvents.zoomCam(-1000);
                    OrthoviewClientEvents.zoomCam(40);      // back to a middle zoom
                    next();
                }
            }
            case 4 -> {   // a minute of play: the bot builds, the HUD fills
                if (ticks > 20 * 60) {
                    shot(mc, "05_rts_one_minute");
                    groundCensus(mc, "1:00");
                    next();
                }
            }
            case 5 -> {   // the first tour ended here; the staged scenes follow
                if (ticks > 20 * 2)
                    next();
            }
            case 6, 7, 8, 9, 10, 11, 12, 13 -> staged(mc);
            case 14 -> firstPerson(mc);
            case STEP_SCARS -> {
                try {
                    sceneScars(mc);
                } catch (Exception e) {
                    ReignOfNether.LOGGER.error("[Eyes] 16 scars failed at tick {}, going on to the fog scene", ticks, e);
                    afterScars();
                }
            }
            case STEP_FOG -> {
                boolean done;
                try {
                    done = EyesFogScene.tick(mc);
                } catch (Exception e) {
                    ReignOfNether.LOGGER.error("[Eyes] 17_fog failed at tick {}, ending the tour", ticks, e);
                    done = true;
                }
                if (done) {
                    step = 15;
                    ticks = 0;
                }
            }
            case STEP_OVERLAYS -> {
                boolean done;
                try {
                    done = EyesOverlaysScene.tick(mc);
                } catch (Exception e) {
                    ReignOfNether.LOGGER.error("[Eyes] 18_overlays failed at tick {}, ending the tour", ticks, e);
                    done = true;
                }
                if (done) {
                    step = 15;
                    ticks = 0;
                }
            }
            case STEP_STRUCTURES -> {
                try {
                    sceneStructures(mc);
                } catch (Exception e) {
                    ReignOfNether.LOGGER.error("[Eyes] 15 structures failed at tick {}, ending the tour", ticks, e);
                    step = 15;
                    ticks = 0;
                }
            }
            case 15 -> {
                if (ticks > 20 * 2) {
                    com.solegendary.reignofnether.unit.modelling.renderers.MipmappedTextures.reportForEyes();
                    ReignOfNether.LOGGER.info("[Eyes] tour complete");
                    mc.stop();
                    next();
                }
            }
            default -> { }
        }
    }

    // ---- 01b-01f: the start-position phase (inbox/spec-3 PR 1: the start markers) ----
    private static boolean startBoxesDone = false;
    private static long boxesPhaseNanos = -1;   // System.nanoTime() when the phase was first seen active
    private static int boxesCue = 0;            // the next cue of the scene's timeline (CUES index) to fire
    private static boolean boxesPlaced = false; // our start was placed by the scene (01e is worth taking)
    private static int startX, startZ;          // our start: our slot until we place, where we place after
    private static int[] otherStart = null;     // a bot's placed start: the pillar the ground still looks at

    /**
     * The scene's timeline in SECONDS of wall clock since the phase was first seen. Client ticks are no clock here: on
     * the shader lane's runner the client managed 4-6 ticks a second (eyes run 39066637227: 60 ticks took 10-26 s)
     * while the server ran its 20, so a tick-scheduled scene photographed 01b-01d with 30, 16 and 7 s left on the
     * banner and placed our start 20 s after the phase had seated everyone (01e/01f showed the built capitol, no
     * pillar). The whole scene is 15 s; the phase is 45 s, and it was first seen ~20 s in at worst.
     */
    private static final double[] CUES = { 0, 1.0, 2.5, 4.5, 6.5, 6.6, 7.6, 9.5, 11.2, 11.3, 12.5, 13.5, 15.0, 15.2 };

    /**
     * The start-position phase as the player sees it: our own box with its fill, rim and fence at zoom 36, 60 and 100
     * (01b-01d) - the camera is put over our slot first, since the server's own cut there (StartPhase.begin) landed
     * 12 s after the phase in the plain run and 01b-01d photographed the map's unlit centre; then the bot's pillar from
     * the ground in the Minecraft view, 20 blocks away (01f; bots are placed at once, so nothing has to end for it);
     * then our start placed at our slot - its pillar, disc and capitol ghost (01e) - LAST, because placing and pressing
     * Ready (both sent together) ends the phase three seconds later (StartPhase.EARLY_END_TICKS). Placing at the slot is where the clock would have seated
     * us, so the later scenes see the same seating as before. The RTS view and HUD are then restored and {@code ticks}
     * reset, so 02 still reads "0:30" after the seating. Every still logs the banner's seconds and the markers' state.
     * Nothing here is a hard failure: a phase that never shows, or ends under the scene, is logged and the tour goes on.
     */
    private static void sceneStartBoxes(Minecraft mc) {
        if (mc.level == null || mc.player == null)
            return;
        try {
            if (boxesPhaseNanos < 0) {
                if (com.solegendary.reignofnether.maps.MapClient.isPhaseActive() && OrthoviewClientEvents.isEnabled()) {
                    boxesPhaseNanos = System.nanoTime();
                    boxesCue = 0;
                } else if (ticks > 20 * 90) {
                    ReignOfNether.LOGGER.warn("[Eyes] 01b_start_boxes: gave up after {} ticks (the start-position phase never showed)",
                            ticks);
                    endStartBoxes(mc);
                }
                return;
            }
            double t = (System.nanoTime() - boxesPhaseNanos) / 1e9;
            if (boxesCue >= CUES.length || t < CUES[boxesCue])
                return;
            int cue = boxesCue++;
            // the phase can end under us (the clock, or every human placed): finish cleanly, in the RTS view
            if (!com.solegendary.reignofnether.maps.MapClient.isPhaseActive() && cue < 12) {
                ReignOfNether.LOGGER.warn("[Eyes] 01b_start_boxes: the phase ended before cue {} ({} s in); the rest is skipped", cue, (int) t);
                endStartBoxes(mc);
                return;
            }
            switch (cue) {
                case 0 -> {   // over our own slot, zoomed in: the fill, rim and fence of our box
                    int[] mine = com.solegendary.reignofnether.maps.MapClient.myStart();
                    startX = mine == null ? mc.player.getBlockX() : mine[0];
                    startZ = mine == null ? mc.player.getBlockZ() : mine[1];
                    otherStart = com.solegendary.reignofnether.maps.MapClient.otherPlacedStart();
                    ReignOfNether.LOGGER.info("[Eyes] 01b_start_boxes: the start phase is on with {} s left (client tick {}, camera at {}); our slot {}, {}; a bot's start {}; {}",
                            com.solegendary.reignofnether.maps.MapClient.secondsLeft(), ticks, fmt(mc.player.position()),
                            startX, startZ, otherStart == null ? "none yet" : otherStart[0] + ", " + otherStart[1],
                            com.solegendary.reignofnether.maps.MapClient.markersStatus());
                    setZoom(36);
                    lookAtStart(mc);
                }
                case 1 -> lookAtStart(mc);   // again: the server's own cut to the slot may land in between
                case 2 -> {
                    boxesShot(mc, "01b_start_boxes_z36");
                    setZoom(60);
                }
                case 3 -> {
                    boxesShot(mc, "01c_start_boxes_z60");
                    setZoom(100);
                }
                case 4 -> boxesShot(mc, "01d_start_boxes_z100");
                case 5 -> {   // the ground view: a person 20 blocks south of the bot's pillar, looking at it
                    if (otherStart == null)
                        otherStart = com.solegendary.reignofnether.maps.MapClient.otherPlacedStart();
                    if (otherStart == null) {
                        ReignOfNether.LOGGER.warn("[Eyes] 01f: no placed start but ours to look at; the ground still is skipped");
                        boxesCue = 9;
                        return;
                    }
                    if (OrthoviewClientEvents.isEnabled())
                        OrthoviewClientEvents.toggleEnable();
                    mc.options.hideGui = true;
                    standBefore(mc, otherStart[0], otherStart[1]);
                }
                case 6, 7 -> standBefore(mc, otherStart[0], otherStart[1]);   // the chunks round it arrive meanwhile
                case 8 -> {
                    double d = Math.hypot(mc.player.getX() - (otherStart[0] + 0.5), mc.player.getZ() - (otherStart[1] + 0.5));
                    ReignOfNether.LOGGER.info("[Eyes] 01f: eye at {} y {} looking at the start {}, {} ({} blocks away; ground there {}); {} s left; {}",
                            fmt(mc.player.position()), (int) mc.player.getEyeY(), otherStart[0], otherStart[1], (int) d,
                            surfaceY(mc, otherStart[0], otherStart[1]), com.solegendary.reignofnether.maps.MapClient.secondsLeft(),
                            com.solegendary.reignofnether.maps.MapClient.markersStatus());
                    shot(mc, "01f_start_boxes_ground");
                }
                case 9 -> {   // back into the RTS view, the HUD back on (its own cut up to the camera height first)
                    if (!OrthoviewClientEvents.isEnabled())
                        OrthoviewClientEvents.toggleEnable();
                    mc.options.hideGui = false;
                    setZoom(36);
                }
                case 10 -> lookAtStart(mc);   // over our slot, once the view's own teleport has landed
                case 11 -> {   // our start, at our slot: pillar, disc, capitol ghost; the phase ends 3 s after
                    int left = com.solegendary.reignofnether.maps.MapClient.secondsLeft();
                    if (left < 5) {
                        ReignOfNether.LOGGER.warn("[Eyes] 01e: only {} s left, not placing (the clock seats us at the slot anyway)", left);
                        return;
                    }
                    com.solegendary.reignofnether.maps.StartPhaseServerboundPacket.send(startX, startZ);
                    // placing no longer starts the match (the Ready rule): Ready right behind it, in packet order
                    com.solegendary.reignofnether.maps.StartPhaseServerboundPacket.sendReady(true);
                    boxesPlaced = true;
                    ReignOfNether.LOGGER.info("[Eyes] 01e: start placed at {}, {} with {} s left; the phase ends 3 s later", startX, startZ, left);
                }
                case 12 -> {
                    if (boxesPlaced)
                        boxesShot(mc, "01e_start_placed_z36");
                }
                case 13 -> endStartBoxes(mc);
                default -> { }
            }
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("[Eyes] 01b_start_boxes failed at cue {} (tick {}), skipping the rest of it", boxesCue - 1, ticks, e);
            endStartBoxes(mc);
        }
    }

    /** A still of the phase with its evidence line: the banner's seconds, the zoom, the markers' state. */
    private static void boxesShot(Minecraft mc, String name) {
        ReignOfNether.LOGGER.info("[Eyes] {}: {} s left, zoom {}, camera at {}; {}", name,
                com.solegendary.reignofnether.maps.MapClient.secondsLeft(), OrthoviewClientEvents.getZoom(),
                fmt(mc.player.position()), com.solegendary.reignofnether.maps.MapClient.markersStatus());
        shot(mc, name);
    }

    /** The RTS camera over our start (a cut through the server, as the match start itself moves it). */
    private static void lookAtStart(Minecraft mc) {
        int y = surfaceY(mc, startX, startZ);
        if (y == Integer.MIN_VALUE)
            y = (int) Math.max(mc.level.getMinBuildHeight() + 2, mc.player.getY() - 30);
        OrthoviewClientEvents.centreCameraOnPosInstant(new Vec3(startX + 0.5, y, startZ + 0.5));
    }

    /** Restores the RTS view and HUD and lets step 1 go on (02 is 30 s of ticks from here). */
    private static void endStartBoxes(Minecraft mc) {
        if (!OrthoviewClientEvents.isEnabled())
            OrthoviewClientEvents.toggleEnable();
        mc.options.hideGui = false;
        startBoxesDone = true;
        ticks = 0;
    }

    /**
     * Puts the player on the ground 20 blocks south of a start, eyes on its pillar's lower third. A client-side move:
     * the eyes player owns the integrated server, which spares its owner the moved-too-quickly snap-back.
     */
    private static void standBefore(Minecraft mc, int sx, int sz) {
        double tx = sx + 0.5, tz = sz + 0.5;
        double px = tx, pz = tz + 20;
        int ground = surfaceY(mc, (int) Math.floor(px), (int) Math.floor(pz));
        if (ground == Integer.MIN_VALUE)
            ground = surfaceY(mc, sx, sz);
        if (ground == Integer.MIN_VALUE)
            ground = mc.player.getBlockY();
        int ty = surfaceY(mc, sx, sz);
        if (ty == Integer.MIN_VALUE)
            ty = ground;
        double py = ground;
        double dx = tx - px, dz = tz - pz, dy = (ty + 4) - (py + mc.player.getEyeHeight());
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
        mc.player.moveTo(px, py, pz, yaw, pitch);
        mc.player.setDeltaMovement(Vec3.ZERO);
    }

    /**
     * 14_first_person: the RTS view off, the camera at a person's height looking at the capitol from 14 blocks - the
     * ground truth for the realistic look. Shader packs drive fog, sky and exposure from the eye's altitude and
     * position, not only from the projection (claude/inbox-shader-research-oct15.md); the RTS eye sits 30-100 blocks up
     * with an orthographic projection, so the same grass in this still and in 03_rts_close tells "the pack / the
     * shader" apart from "the RTS camera". Last scene: the tour ends right after it, the RTS view is not restored.
     */
    private static void firstPerson(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            next();
            return;
        }
        try {
            if (ticks == 1) {
                if (capitolCentre == null)
                    capitolCentre = findCapitol(mc);
                ReignOfNether.LOGGER.info("[Eyes] 14_first_person: RTS eye at y {} (ground {})", (int) mc.player.getY(),
                        surfaceY(mc, mc.player.getBlockX(), mc.player.getBlockZ()));
                if (OrthoviewClientEvents.isEnabled())
                    OrthoviewClientEvents.toggleEnable();
                mc.options.hideGui = true;    // the world only, as a screenshot with F1
            }
            if (ticks == 5 || ticks == 40) {   // again later: the server's own position updates may land in between
                double cx = capitolCentre.getX() + 0.5, cz = capitolCentre.getZ() + 0.5;
                double px = cx - 10, pz = cz - 10;
                double py = surfaceY(mc, (int) Math.floor(px), (int) Math.floor(pz)) + 4;   // eye ~5.6 above the grass
                double dx = cx - px, dz = cz - pz, dy = capitolCentre.getY() - (py + mc.player.getEyeHeight());
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float pitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
                mc.player.moveTo(px, py, pz, yaw, pitch);
                mc.player.setDeltaMovement(Vec3.ZERO);
            }
            if (ticks == 20 * 5) {
                ReignOfNether.LOGGER.info("[Eyes] 14_first_person: eye at {} y {} looking at the capitol {}",
                        fmt(mc.player.position()), (int) mc.player.getEyeY(), capitolCentre.toShortString());
                shot(mc, "14_first_person");
                step = STEP_SCARS;   // the aftermath scene is the last one now (16, a world that remembers)
                ticks = 0;
            }
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("[Eyes] 14_first_person failed at tick {}, skipping it", ticks, e);
            next();
        }
    }

    /**
     * Which resource packs are on, and whether OUR block textures really reached the block atlas: grass_block_top is
     * 128 wide from resourcepacks/BeyondAndTabs-Realistic, 16 from vanilla. From 5fc9ca5 to 87a0760 every tour loaded
     * an empty pack folder and a still was taken as proof it worked ("Never twice", claude/improvement-loop.md).
     */
    private static void logPacks(Minecraft mc) {
        try {
            var atlas = mc.getModelManager().getAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
            var grass = atlas.getSprite(new net.minecraft.resources.ResourceLocation("minecraft", "block/grass_block_top"));
            ReignOfNether.LOGGER.info("[Eyes] resource packs {}; block/grass_block_top is {}x{} in the block atlas",
                    mc.getResourcePackRepository().getSelectedIds(), grass.contents().width(), grass.contents().height());
        } catch (Exception e) {
            ReignOfNether.LOGGER.warn("[Eyes] could not read the resource packs / block atlas", e);
        }
    }

    /** The staged scenes: any exception is logged and the scene skipped, never thrown into the tick loop. */
    private static void staged(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            if (ticks > 20 * 30)
                giveUp("step " + step, "no level or player");
            return;
        }
        try {
            switch (step) {
                case 6 -> sceneBuildMenu(mc);
                case 7 -> sceneArmy(mc);
                case 8 -> sceneBoxSelect(mc);
                case 9 -> sceneFight(mc);
                case 10 -> sceneFightStrategic(mc);
                case 11 -> sceneCapitol(mc);
                case 12 -> sceneRiverCrossing(mc);
                case 13 -> sceneDome(mc);
                default -> next();
            }
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("[Eyes] step {} failed at tick {}, skipping it", step, ticks, e);
            next();
        }
    }

    // ---- 16: scars (a world that remembers, claude/world-that-remembers.md slice 1; eyes.yml input `scars`) ----
    // The ground memory field is server state drawn by a client mesh, so the stills prove what a player sees of it: a
    // flat pad with the ground's awkward shapes (steps rising 1-4, a slab row, snow layers, a dirt path, a pond) is laid
    // beside our base, photographed clean, hit by a scripted battle - a barrage of blasts, artillery walking a line,
    // three T3 deaths, a fallen building, a game ender's crater - through the SAME server calls BarFx makes
    // (GroundMemoryServer.blast / heavyImpact / death / collapse / groundImpact: stamp, sync pass, packet, client mesh),
    // and photographed again:
    //   16_scars_before_rts   the clean pad, RTS default zoom
    //   16_scars_rts          the aftermath, the same camera
    //   16_scars_rts_close    zoomed in on the steps, slabs and snow (a decal that floats shows here)
    //   16_scars_rts_crater   zoomed in on the game ender's crater and the dirt path beside it
    //   16_scars_rts_far      strategic zoom: does the battlefield still read
    //   16_scars_fp_crater    first person, a person's eye height, looking into the big crater
    //   16_scars_fp_steps     first person at the steps, slab row and snow
    // The timeline is wall-clock seconds, not ticks (the shader lane's client runs 4-6 ticks a second, see CUES).
    static final boolean SCARS_ONLY = Boolean.getBoolean("bat.eyes.scars");
    static final int STEP_SCARS = 21;
    static final int STEP_FOG = 22;               // 17_fog, EyesFogScene: after the scars, the last scene of the full tour
    static final boolean FOG_ONLY = Boolean.getBoolean("bat.eyes.fog");   // eyes.yml input `fog`: 02, then 17 only
    static final int STEP_OVERLAYS = 23;          // 18_overlays, EyesOverlaysScene: its own run only (input `overlays`)
    static final boolean OVERLAYS_ONLY = Boolean.getBoolean("bat.eyes.overlays");
    private static final int SCARS_HALF = 16;     // the pad is 33 x 33
    private static int scarsPhase = 0;
    private static long scarsNanos = 0;
    private static BlockPos scarsSite = null;     // the pad's centre, on the ground (the first free block above the grass)
    private static volatile boolean scarsPadReady = false, scarsStamped = false;
    private static volatile String scarsError = null;
    private static volatile int scarsMarkedChunks = 0;

    private static double scarsSeconds() {
        return (System.nanoTime() - scarsNanos) / 1e9;
    }

    private static void scarsNext(int phase) {
        scarsPhase = phase;
        scarsNanos = System.nanoTime();
    }

    private static void scarsEnd(String why) {
        ReignOfNether.LOGGER.warn("[Eyes] 16 scars: ending early ({})", why);
        afterScars();
    }

    /** The full tour goes on to 17_fog (EyesFogScene, the last scene); a scars-only run ends here. */
    private static void afterScars() {
        step = SCARS_ONLY ? 15 : STEP_FOG;
        ticks = 0;
    }

    /** 44 blocks from our capitol along the first dry axis: the 33-wide pad clears the base (and its buildings, whose blocks the pad would take). */
    private static BlockPos scarsSite(Minecraft mc) {
        if (capitolCentre == null)
            capitolCentre = findCapitol(mc);
        int[][] axes = { { -1, 1 }, { 1, 1 }, { -1, -1 }, { 1, -1 } };
        int[] axis = axes[0];
        for (int[] a : axes)
            if (dryAround(mc, capitolCentre.getX() + a[0] * 44, capitolCentre.getZ() + a[1] * 44)) {
                axis = a;
                break;
            }
        int x = capitolCentre.getX() + axis[0] * 44, z = capitolCentre.getZ() + axis[1] * 44;
        int y = surfaceY(mc, x, z);
        return new BlockPos(x, y == Integer.MIN_VALUE ? capitolCentre.getY() : y, z);
    }

    private static BlockPos scarsAt(int dx, int y, int dz) {
        return new BlockPos(scarsSite.getX() + dx, y, scarsSite.getZ() + dz);
    }

    /** On the server thread: the flat grass pad (33 x 33, trees and plants cleared) with its awkward shapes. */
    private static void layScarsPad(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        final int gy = scarsSite.getY();   // the first free block above the ground; the grass block is at gy - 1
        server.execute(() -> {
            try {
                var level = server.overworld();
                var grass = net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState();
                var dirt = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
                var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                for (int dx = -SCARS_HALF; dx <= SCARS_HALF; dx++)
                    for (int dz = -SCARS_HALF; dz <= SCARS_HALF; dz++) {
                        level.setBlock(scarsAt(dx, gy - 1, dz), grass, 2);
                        for (int y = gy - 4; y < gy - 1; y++)
                            level.setBlock(scarsAt(dx, y, dz), dirt, 2);
                        for (int y = gy; y <= gy + 24; y++) {
                            BlockPos p = scarsAt(dx, y, dz);
                            if (!level.getBlockState(p).isAir())
                                level.setBlock(p, air, 2);
                        }
                    }
                // a staircase rising east, four steps of one block (the fourth is a cliff top for a blast at its foot)
                for (int dx = 6; dx <= 9; dx++)
                    for (int dz = -4; dz <= -1; dz++)
                        for (int y = gy; y < gy + (dx - 5); y++)
                            level.setBlock(scarsAt(dx, y, dz), y == gy + (dx - 6) ? grass : dirt, 2);
                // a row of slabs, bottom and top halves alternating
                for (int dx = 6; dx <= 9; dx++)
                    level.setBlock(scarsAt(dx, gy, 3), net.minecraft.world.level.block.Blocks.STONE_SLAB.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, dx % 2 == 0
                            ? net.minecraft.world.level.block.state.properties.SlabType.BOTTOM
                            : net.minecraft.world.level.block.state.properties.SlabType.TOP), 2);
                // snow, one to four layers
                for (int dx = 6; dx <= 9; dx++)
                    level.setBlock(scarsAt(dx, gy, 7), net.minecraft.world.level.block.Blocks.SNOW.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, dx - 5), 2);
                // a dirt path
                for (int dx = -9; dx <= -3; dx++)
                    level.setBlock(scarsAt(dx, gy - 1, 6), net.minecraft.world.level.block.Blocks.DIRT_PATH.defaultBlockState(), 2);
                // a pond (flush with the grass, so its water stays put)
                for (int dx = -11; dx <= -8; dx++)
                    for (int dz = -9; dz <= -6; dz++)
                        level.setBlock(scarsAt(dx, gy - 1, dz), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState(), 2);
                scarsPadReady = true;
            } catch (Exception e) {
                scarsError = e.toString();
                ReignOfNether.LOGGER.error("[Eyes] 16 scars: the pad failed", e);
            }
        });
    }

    /** On the server thread: the battle's marks, through the same calls BarFx makes. */
    private static void stampScarsBattle(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        final int gy = scarsSite.getY();
        server.execute(() -> {
            try {
                var level = server.overworld();
                java.util.Random rnd = new java.util.Random(1021);
                // a barrage over the northern half: blasts of radius 2-4 (grenades, rockets, fireballs)
                for (int i = 0; i < 26; i++) {
                    double dx = -14 + rnd.nextDouble() * 22, dz = -14 + rnd.nextDouble() * 12;
                    float r = 2f + rnd.nextInt(3);
                    com.solegendary.reignofnether.worldmemory.GroundMemoryServer.blast(level, scarsSite.getX() + dx,
                        gy, scarsSite.getZ() + dz, r, i % 3 == 0 ? com.solegendary.reignofnether.barfx.BarFx.K_MAGIC
                            : com.solegendary.reignofnether.barfx.BarFx.K_TNT);
                }
                // artillery walking a line across the pad, west to east
                for (int i = 0; i < 9; i++)
                    com.solegendary.reignofnether.worldmemory.GroundMemoryServer.heavyImpact(level,
                        scarsSite.getX() - 14 + i * 3.4, gy, scarsSite.getZ() + 4 + Math.sin(i * 0.9) * 2.5, 3f + (i % 2));
                // shells and fireballs that landed without a blast of their own
                for (int i = 0; i < 12; i++)
                    com.solegendary.reignofnether.worldmemory.GroundMemoryServer.groundImpact(level,
                        scarsSite.getX() - 12 + rnd.nextDouble() * 24, gy, scarsSite.getZ() - 12 + rnd.nextDouble() * 24,
                        i % 2 == 0 ? com.solegendary.reignofnether.barfx.BarFx.K_FIREBALL : com.solegendary.reignofnether.barfx.BarFx.K_BIG_FIREBALL, 1.4f);
                // three T3 deaths and a golem
                for (int i = 0; i < 3; i++)
                    com.solegendary.reignofnether.worldmemory.GroundMemoryServer.death(level, scarsSite.getX() - 6 + i * 5, gy,
                        scarsSite.getZ() - 5 + (i % 2) * 3, com.solegendary.reignofnether.barfx.BarFx.D_FLESH, 3, 1.8f);
                com.solegendary.reignofnether.worldmemory.GroundMemoryServer.death(level, scarsSite.getX() + 2, gy,
                    scarsSite.getZ() + 10, com.solegendary.reignofnether.barfx.BarFx.D_CONSTRUCT, 2, 1.4f);
                // the foot of the stairs, the slab row and the snow take hits (a decal on a step shows or floats here)
                com.solegendary.reignofnether.worldmemory.GroundMemoryServer.blast(level, scarsSite.getX() + 5.5, gy, scarsSite.getZ() - 2.5, 4f,
                    com.solegendary.reignofnether.barfx.BarFx.K_TNT);
                com.solegendary.reignofnether.worldmemory.GroundMemoryServer.blast(level, scarsSite.getX() + 6.5, gy, scarsSite.getZ() + 5.5, 3f,
                    com.solegendary.reignofnether.barfx.BarFx.K_TNT);
                // a fallen building, and a game ender's crater
                com.solegendary.reignofnether.worldmemory.GroundMemoryServer.collapse(level, scarsSite.getX() - 3, gy, scarsSite.getZ() - 11, 5f);
                com.solegendary.reignofnether.worldmemory.GroundMemoryServer.blast(level, scarsSite.getX() - 4, gy, scarsSite.getZ() + 11, 7f,
                    com.solegendary.reignofnether.barfx.BarFx.K_BIG_FIREBALL);
                scarsMarkedChunks = com.solegendary.reignofnether.worldmemory.GroundMemoryServer.markedChunks(level);
                scarsStamped = true;
            } catch (Exception e) {
                scarsError = e.toString();
                ReignOfNether.LOGGER.error("[Eyes] 16 scars: the battle failed", e);
            }
        });
    }

    /** Logs what the heightmaps, the top-block finder and the field say about the awkward columns, on both sides (a still shows a decal missing; this says why). */
    private static void probeScars(Minecraft mc) {
        final int[][] cols = { { -6, 6 }, { 6, 7 }, { 7, 7 }, { 9, 7 }, { 7, 3 }, { 8, 3 }, { 7, -2 }, { 0, 0 }, { -4, 11 } };
        final String[] names = { "dirt path", "snow 1", "snow 2", "snow 4", "top slab", "bottom slab", "stairs", "flat grass", "game ender centre" };
        for (int i = 0; i < cols.length; i++)
            ReignOfNether.LOGGER.info("[Eyes] 16 probe client {}: {}", names[i],
                com.solegendary.reignofnether.worldmemory.GroundSurface.probe(mc.level, scarsSite.getX() + cols[i][0], scarsSite.getZ() + cols[i][1]));
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            try {
                var level = server.overworld();
                for (int i = 0; i < cols.length; i++) {
                    int x = scarsSite.getX() + cols[i][0], z = scarsSite.getZ() + cols[i][1];
                    var mem = com.solegendary.reignofnether.worldmemory.GroundMemoryCapability.of(level.getChunkAt(new BlockPos(x, 0, z)));
                    int cell = com.solegendary.reignofnether.worldmemory.GroundMemory.index(x & 15, z & 15);
                    ReignOfNether.LOGGER.info("[Eyes] 16 probe server {}: {}, scorch {} crater {}", names[i],
                        com.solegendary.reignofnether.worldmemory.GroundSurface.probe(level, x, z),
                        mem == null ? -1 : mem.scorch(cell), mem == null ? -1 : mem.crater(cell));
                }
            } catch (Exception e) {
                ReignOfNether.LOGGER.error("[Eyes] 16 probe failed", e);
            }
        });
    }

    /** Stands the player on the pad at (dx, dz), eyes at a person's height, looking at (ax, ay, az) offsets from the site. */
    private static void scarsStand(Minecraft mc, double dx, double dz, double aimX, double aimY, double aimZ) {
        double px = scarsSite.getX() + 0.5 + dx, pz = scarsSite.getZ() + 0.5 + dz, py = scarsSite.getY();
        double ex = scarsSite.getX() + 0.5 + aimX - px, ez = scarsSite.getZ() + 0.5 + aimZ - pz;
        double ey = (scarsSite.getY() + aimY) - (py + mc.player.getEyeHeight());
        float yaw = (float) Math.toDegrees(Math.atan2(-ex, ez));
        float pitch = (float) Math.toDegrees(Math.atan2(-ey, Math.sqrt(ex * ex + ez * ez)));
        mc.player.moveTo(px, py, pz, yaw, pitch);
        mc.player.setDeltaMovement(Vec3.ZERO);
        mc.options.hideGui = true;
    }

    private static void sceneScars(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) {
            scarsEnd("no level, player or integrated server");
            return;
        }
        if (scarsError != null) {
            scarsEnd(scarsError);
            return;
        }
        switch (scarsPhase) {
            case 0 -> {   // the site: chunks load under the camera, then the pad goes down
                if (ticks == 1) {
                    scarsSite = scarsSite(mc);
                    OrthoviewClientEvents.centreCameraOnPosInstant(Vec3.atBottomCenterOf(scarsSite));
                    ReignOfNether.LOGGER.info("[Eyes] 16 scars: pad at {} (capitol {}), fog of war on the client: {}",
                        scarsSite.toShortString(), capitolCentre.toShortString(),
                        com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents.isEnabled());
                    scarsNext(0);
                    scarsPhase = 0;
                }
                if (scarsSeconds() < 3)
                    return;
                layScarsPad(mc);
                scarsNext(1);
            }
            case 1 -> {   // the pad is down: the RTS view over it, then a few seconds for the chunks to re-mesh
                if (!scarsPadReady) {
                    if (scarsSeconds() > 40)
                        scarsEnd("the server never laid the pad");
                    return;
                }
                if (!OrthoviewClientEvents.isEnabled())
                    OrthoviewClientEvents.toggleEnable();
                mc.options.hideGui = true;
                setZoom(ZOOM_NORMAL);
                OrthoviewClientEvents.centreCameraOnPosInstant(Vec3.atBottomCenterOf(scarsSite));
                scarsNext(2);
            }
            case 2 -> {
                if (scarsSeconds() < 6)
                    return;
                shot(mc, "16_scars_before_rts");
                stampScarsBattle(mc);
                scarsNext(3);
            }
            case 3 -> {   // the sync pass runs every half second; the mesh builds a region a frame
                if (!scarsStamped) {
                    if (scarsSeconds() > 40)
                        scarsEnd("the server never stamped the battle");
                    return;
                }
                if (scarsSeconds() < 8)
                    return;
                ReignOfNether.LOGGER.info("[Eyes] 16 scars: {} chunks marked on the server; client: {}", scarsMarkedChunks,
                    com.solegendary.reignofnether.worldmemory.GroundMemoryClient.status());
                shot(mc, "16_scars_rts");
                probeScars(mc);
                setZoom(15);
                OrthoviewClientEvents.centreCameraOnPosInstant(new Vec3(scarsSite.getX() + 7.5, scarsSite.getY(), scarsSite.getZ() + 0.5));
                scarsNext(4);
            }
            case 4 -> {
                if (scarsSeconds() < 4)
                    return;
                shot(mc, "16_scars_rts_close");
                setZoom(15);
                OrthoviewClientEvents.centreCameraOnPosInstant(new Vec3(scarsSite.getX() - 4, scarsSite.getY(), scarsSite.getZ() + 11));
                scarsNext(8);
            }
            case 8 -> {   // the game ender's crater, close
                if (scarsSeconds() < 4)
                    return;
                shot(mc, "16_scars_rts_crater");
                setZoom(ZOOM_STRATEGIC);
                OrthoviewClientEvents.centreCameraOnPosInstant(Vec3.atBottomCenterOf(scarsSite));
                scarsNext(5);
            }
            case 5 -> {
                if (scarsSeconds() < 5)
                    return;
                shot(mc, "16_scars_rts_far");
                setZoom(ZOOM_NORMAL);
                if (OrthoviewClientEvents.isEnabled())
                    OrthoviewClientEvents.toggleEnable();
                scarsStand(mc, -4, 3, -4, 0, 11);   // from the middle of the pad, down on the game ender's crater
                scarsNext(6);
            }
            case 6 -> {
                if (scarsSeconds() > 1.5 && scarsSeconds() < 2.5)
                    scarsStand(mc, -4, 3, -4, 0, 11);   // again: the server's own position update may land in between
                if (scarsSeconds() < 5)
                    return;
                shot(mc, "16_scars_fp_crater");
                scarsStand(mc, 1, -8, 8, 0.5, 3);      // from the south-west of the stairs, across them to the slabs and snow
                scarsNext(7);
            }
            case 7 -> {
                if (scarsSeconds() > 1.5 && scarsSeconds() < 2.5)
                    scarsStand(mc, 1, -8, 8, 0.5, 3);
                if (scarsSeconds() < 5)
                    return;
                shot(mc, "16_scars_fp_steps");
                ReignOfNether.LOGGER.info("[Eyes] 16 scars: done, client: {}",
                    com.solegendary.reignofnether.worldmemory.GroundMemoryClient.status());
                afterScars();
            }
            default -> scarsEnd("unknown phase " + scarsPhase);
        }
    }


    // ---- 15: structure photographs (eyes.yml input `structure`, -Dbat.eyes.structures=a,b,...) ----
    // lovish, Oct 20 2026: new building designs are judged with real textures, lighting and glass - in the real game,
    // with the building-block mods and optionally the shader lane - not only in offline previews. Each entry is a
    // structure name in our jar (data/reignofnether/structures/<name>.nbt - so a branch's new design is photographed
    // from that branch's build) or an absolute path to a .nbt file (the workflow passes files from its checkout).
    // Each is stamped on a fresh flat pad near our capitol, on its arena-centre side and clear of the arena wall
    // (EyesPhotoBooth.chooseAxis), and photographed five ways:
    //   15_<name>_rts       the RTS camera at the default zoom (what a player sees all match)
    //   15_<name>_rts_close the RTS camera zoomed in to the building's size
    //   15_<name>_34        a first-person 3/4 view from the south-west (the side the RTS camera sees), ~30 degrees
    //                       down, from ~1.5x the building's largest size
    //   15_<name>_close     first person, standing 5 blocks from the footprint's middle (south-west first), eye height,
    //                       looking slightly down at the ground there, at noon: plants judged up close (round r34: the
    //                       _34 still is a far shot and its tufts are sub-pixel)
    //   15_<name>_dusk      the same 3/4 view at dusk (time 13000): lanterns, glow and glass against a dark sky
    //   15_<name>_golden_rts the RTS still at golden hour (EyesPhotoBooth.GOLDEN_TIME, sun ~15 degrees up in the west):
    //                       lovish's villa reference (Oct 28) - warm low light on the faces the RTS camera sees
    //   15_<name>_rts_t<T>, 15_<name>_34_t<T>
    //                       with -Dbat.eyes.times=T,... (eyes.yml input `times`): the RTS and 3/4 views again at each
    //                       day time T - the shader look's times of day (design/own_shader_pack.md: Morning 5000, Dusk
    //                       11800, Night 18000 beside the noon stills)
    //   15_<name>_rts_army  back at noon: two of our militia standing still on each of the structure's four
    //                       sides, three blocks out - whichever sides lie in its shadow show whether a unit there still
    //                       reads from the RTS camera (#386 r5, the shader pack's shadow fill); removed afterwards
    //   15_<name>_mist_rts  last, only with -Dbat.eyes.mist=true (eyes.yml input `mist`): the RTS still's framing at
    //                       dawn (MistMath.EYES_DAWN_TIME), once the valley mist has gathered and drifted ~8 s - the
    //                       mist pilot (lovish Oct 28: "can we make the fog more animated and alive or rich")
    // In order: rts, (bench), rts_close, 34, close, dusk, golden_rts, the extra times, rts_army, mist_rts; then the
    // server's time before the dusk still is restored.
    // After the noon RTS still the camera holds while ShaderBench times BENCH_FRAMES drawn frames: one
    // "[ShaderBench] 15_<name>_rts" line per structure, the frame-cost ratio between eyes lanes.
    // Ambient life (birds, butterflies) is off for the whole run (EyesPhotoBooth.ambientLifeOff): it flew through the
    // measured crops and changed their numbers run to run. The valley mist obeys it too, unless the run asked for it
    // (MistMath.allowed): then it shows in every still whose hour has mist (dusk, golden hour, the dawn still).
    // Placed as plain blocks (StructureTemplate.placeInWorld), not as a building: renderer-drawn parts (sails, rigs)
    // and the building's HP bar are not in these stills. The timeline is wall-clock seconds, not ticks: the shader
    // lane's client runs 4-6 ticks a second (see CUES).
    static final int STEP_STRUCTURES = 20;
    static final List<String> STRUCTURES = parseStructures(System.getProperty("bat.eyes.structures", ""));
    static final List<Long> EXTRA_TIMES = parseTimes(System.getProperty("bat.eyes.times", ""));
    static final int BENCH_FRAMES = 60;            // the shader lane draws ~5 frames a second on the runner
    private static int timeIndex = 0;
    private static boolean benchStarted = false;
    private static int structureIndex = 0;
    private static int structurePhase = 0;
    private static long phaseNanos = 0;
    private static volatile boolean structurePlaced = false;
    private static volatile String structureError = null;
    private static net.minecraft.core.Vec3i structureSize = null;
    private static BlockPos structureOrigin = null;
    private static BlockPos siteCentre = null;      // the pad's centre on the ground; every structure uses the same site
    private static int[] lastCleared = null;       // x0, z0, x1, z1, y0, y1 of the previous structure's box
    private static volatile long dayTimeBefore = -1;   // the server's time before the dusk still, restored after it
    /** -Dbat.eyes.mist=true: one more still per structure, 15_<name>_mist_rts at dawn (EyesDirector phase 12). */
    static final boolean MIST_STILL = Boolean.getBoolean("bat.eyes.mist");
    private static long mistSinceNanos = -1;   // when the client sky reached dawn in phase 12
    private static int mistSinceFrames = 0;
    static final long SHADE_ARMY_TIME = 6000;          // noon: the shadow the shade-army still is judged in
    static final String SHADE_ARMY_TAG = "bat_eyes_shade";

    static List<String> parseStructures(String raw) {
        List<String> out = new ArrayList<>();
        for (String s : raw.split(","))
            if (!s.isBlank())
                out.add(s.trim());
        return out;
    }

    static List<Long> parseTimes(String raw) {
        List<Long> out = new ArrayList<>();
        for (String s : raw.split(","))
            if (!s.isBlank()) {
                try {
                    out.add(Math.floorMod(Long.parseLong(s.trim()), 24000L));
                } catch (NumberFormatException e) {
                    ReignOfNether.LOGGER.warn("[Eyes] bat.eyes.times: '{}' is not a day time - skipped", s.trim());
                }
            }
        return out;
    }

    /** The still name for an entry: a path's file name without .nbt, so 15_<name>_* reads the same either way. */
    static String structureLabel(String entry) {
        String n = entry.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        return n.endsWith(".nbt") ? n.substring(0, n.length() - 4) : n;
    }

    private static double phaseSeconds() {
        return (System.nanoTime() - phaseNanos) / 1e9;
    }

    /** Frames drawn since the phase began: under a shader pack on a CI runner the game draws ~1 a second. */
    private static int phaseFrames() {
        return frames - phaseFrames0;
    }

    private static void nextPhase(int phase) {
        structurePhase = phase;
        phaseNanos = System.nanoTime();
        phaseFrames0 = frames;
    }

    private static int frames = 0, phaseFrames0 = 0;

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent evt) {
        if (ENABLED && evt.phase == TickEvent.Phase.END)
            frames++;
    }

    /**
     * The structure photos' wait: the camera aimed again every tick for the first 3.5 s (turning the RTS view on, or a
     * zoom, moves it a tick or more later - and at ~1 frame a second, run 40712072482, a one-second window of
     * wall clock can pass with no tick in it), then the still only once both `seconds` have passed and enough frames
     * have been drawn for the placed blocks' chunks to be meshed (that run's shader stills showed unmeshed voids where
     * the samplers stood).
     */
    private static boolean structureSettled(Minecraft mc, double seconds) {
        if (phaseSeconds() < 3.5)
            OrthoviewClientEvents.centreCameraOnPosInstant(structureMiddle(0.3));
        return phaseSeconds() >= seconds && phaseFrames() >= STRUCTURE_FRAMES;
    }

    private static final int STRUCTURE_FRAMES = 25;

    /** Where the RTS camera looks at the still, against where it was told to: the evidence line for a still that misses. */
    private static void logAim(Minecraft mc, String name) {
        logAim(mc, name, structureMiddle(0.3), OrthoviewClientEvents.getCamRotX(), OrthoviewClientEvents.getCamRotY());
    }

    /** The same evidence for any camera: {@code want} the aim point, yaw / pitch the view's (degrees, Minecraft's). */
    private static void logAim(Minecraft mc, String name, Vec3 want, double yawDeg, double pitchDeg) {
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        double[] look = EyesPhotoBooth.lookPoint(eye.x, eye.y, eye.z, yawDeg, pitchDeg, want.y);
        ReignOfNether.LOGGER.info("[Eyes] {}: aimed at {} - the camera at {} looks at {}, {} ({} frames, {} s in the phase)",
            name, String.format("%.0f, %.0f, %.0f", want.x, want.y, want.z), String.format("%.0f, %.0f, %.0f", eye.x, eye.y, eye.z),
            String.format("%.0f", look[0]), String.format("%.0f", look[1]), phaseFrames(),
            String.format("%.1f", phaseSeconds()));
        // what the structure's box holds, on the client and on the server: a still that shows plain ground where the
        // structure should stand tells which side lost it (meadow / ground samplers, run 40725608295)
        if (structureOrigin != null && structureSize != null && mc.level != null) {
            BlockPos o = structureOrigin;
            net.minecraft.core.Vec3i sz = structureSize;
            ReignOfNether.LOGGER.info("[Eyes] {}: the structure's box holds {} non-air blocks on the client", name,
                nonAir(mc.level, o, sz));
            var server = mc.getSingleplayerServer();
            if (server != null)
                server.execute(() -> ReignOfNether.LOGGER.info("[Eyes] {}: ... and {} on the server", name,
                    nonAir(server.overworld(), o, sz)));
        }
    }

    private static int nonAir(net.minecraft.world.level.Level level, BlockPos o, net.minecraft.core.Vec3i sz) {
        int n = 0;
        var pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < sz.getX(); x++)
            for (int z = 0; z < sz.getZ(); z++)
                for (int y = 0; y < sz.getY(); y++)
                    if (!level.getBlockState(pos.set(o.getX() + x, o.getY() + y, o.getZ() + z)).isAir())
                        n++;
        return n;
    }

    private static void sceneStructures(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) {
            ReignOfNether.LOGGER.warn("[Eyes] 15 structures: no level, player or integrated server - skipped");
            step = 15;
            ticks = 0;
            return;
        }
        if (structureIndex >= STRUCTURES.size()) {
            EyesPhotoBooth.setAmbientLifeOff(false);
            step = 15;   // tour complete
            ticks = 0;
            return;
        }
        String entry = STRUCTURES.get(structureIndex);
        String label = "15_" + structureLabel(entry);
        if (ticks == 1 && structurePhase == 0) {
            // no birds or butterflies in the stills: round r34 had four birds in meadow_sampler_rts_close's measured
            // crop, and the dark share and star counts changed run to run with them
            if (!EyesPhotoBooth.ambientLifeOff()) {
                EyesPhotoBooth.setAmbientLifeOff(true);
                ReignOfNether.LOGGER.info("[Eyes] 15 structures: ambient life off for the structure photos");
            }
            if (siteCentre == null) {
                siteCentre = structureSite(mc);
                lightTheSite(mc);   // main's #349: fog off on the server and noon, once, with the force-fog log line
            }
            // a photo booth, not a match: the site is 45 blocks out from the capitol, beyond its vision, and fog of
            // war darkened every structure still to near black (meadow sampler run 40667744923, Oct 28)
            // ...and on the SERVER too: its fog filter never sends a fogged column's block changes, so the placed
            // structure would stay off the client - the stills showed the map's plain ground where it stood, runs
            // 40725608295 / 40725612987 (logAim's block census on both sides checks it)
            if (com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents.isEnabled()) {
                ReignOfNether.LOGGER.info("[Eyes] {}: fog of war off for the structure photos", label);
                com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents.setEnabled(false);
            }
            var fogServer = mc.getSingleplayerServer();
            fogServer.execute(() -> com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.setEnabled(false));
            // noon for the day stills, so every round is lit the same: the match is ~2 min after sunrise here, and
            // the shader lane's low golden light turned the grass khaki (hue 48 against 103 in the plain lane, run
            // 40712072482). The dusk still keeps its own low sun, and the time is restored after it
            var noonServer = mc.getSingleplayerServer();
            noonServer.execute(() -> noonServer.overworld().setDayTime(6000));
            structurePlaced = false;
            structureError = null;
            OrthoviewClientEvents.centreCameraOnPosInstant(Vec3.atBottomCenterOf(siteCentre));   // loads its chunks
            placeStructure(mc, entry);
            nextPhase(1);
            return;
        }
        switch (structurePhase) {
            case 1 -> {   // the server stamps it; give the chunks a moment to re-mesh
                if (structureError != null) {
                    ReignOfNether.LOGGER.error("[Eyes] {}: {} - skipped", label, structureError);
                    nextStructure(mc);
                    return;
                }
                if (!structurePlaced) {
                    if (phaseSeconds() > 30) {
                        ReignOfNether.LOGGER.error("[Eyes] {}: the server never placed it - skipped", label);
                        nextStructure(mc);
                    }
                    return;
                }
                if (!OrthoviewClientEvents.isEnabled())
                    OrthoviewClientEvents.toggleEnable();
                mc.options.hideGui = true;   // the building, not the HUD
                setZoom(ZOOM_NORMAL);
                OrthoviewClientEvents.centreCameraOnPosInstant(structureMiddle(0.3));
                ReignOfNether.LOGGER.info("[Eyes] {}: '{}' {}x{}x{} placed at {} (pad centre {})", label, entry,
                    structureSize.getX(), structureSize.getY(), structureSize.getZ(),
                    structureOrigin.toShortString(), siteCentre.toShortString());
                nextPhase(2);
            }
            case 2 -> {
                // again once the camera has settled: turning the RTS view on moves the camera to its own base height
                // a tick later, and an aim taken from the old height lands ~20 blocks off along the view (meadow and
                // ground samplers, run 40703946521, Oct 28)
                if (!structureSettled(mc, 5))
                    return;
                logAim(mc, label + "_rts");
                shot(mc, label + "_rts");
                benchStarted = false;
                nextPhase(6);
            }
            case 6 -> {   // the camera holds still while the bench times its frames (after the screenshot's own frame)
                if (phaseSeconds() < 2)
                    return;
                if (!benchStarted) {
                    benchStarted = true;
                    com.solegendary.reignofnether.postfx.ShaderBench.start(label + "_rts", BENCH_FRAMES);
                    return;
                }
                if (com.solegendary.reignofnether.postfx.ShaderBench.running() && phaseSeconds() < 120)
                    return;
                if (com.solegendary.reignofnether.postfx.ShaderBench.running())
                    ReignOfNether.LOGGER.warn("[Eyes] {}: the frame bench did not finish in 120 s", label);
                // the view height the whole building needs under the RTS tilt: its footprint plus part of its height
                // (run 39527167948: 1.3x the footprint cropped the 20-tall Temple of the Sun top and bottom)
                int big = Math.max(structureSize.getX(), structureSize.getZ());
                setZoom(Math.min(ZOOM_NORMAL - 4, Math.max(12, 1.25f * (big + 0.6f * structureSize.getY()))));
                OrthoviewClientEvents.centreCameraOnPosInstant(structureMiddle(0.3));
                nextPhase(3);
            }
            case 3 -> {
                if (!structureSettled(mc, 4))
                    return;
                logAim(mc, label + "_rts_close");
                shot(mc, label + "_rts_close");
                if (OrthoviewClientEvents.isEnabled())
                    OrthoviewClientEvents.toggleEnable();
                standThreeQuarter(mc);
                nextPhase(4);
            }
            case 4 -> {
                if (phaseSeconds() > 1.5 && phaseSeconds() < 2.5)
                    standThreeQuarter(mc);   // again: the server's own position update may land in between
                if (phaseSeconds() < 5)
                    return;
                shot(mc, label + "_34");
                // the close still: noon again (the day ran on through the stills before) and the camera among the plants
                var noon = mc.getSingleplayerServer();
                noon.execute(() -> noon.overworld().setDayTime(6000));
                closeCam = planClose(mc, label);
                standClose(mc);
                nextPhase(10);
            }
            case 10 -> {   // 15_<name>_close: the RTS stills' wait (5 s and 25 drawn frames), standing there every tick
                standClose(mc);
                if (phaseSeconds() < 5 || phaseFrames() < STRUCTURE_FRAMES)
                    return;
                logAim(mc, label + "_close", new Vec3(closeCam[5], closeCam[6], closeCam[7]), mc.player.getYRot(),
                    mc.player.getXRot());
                shot(mc, label + "_close");
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    dayTimeBefore = server.overworld().getDayTime();
                    server.overworld().setDayTime(13000);
                });
                standThreeQuarter(mc);
                nextPhase(5);
            }
            case 5 -> {   // the client eases its sky toward the server's time (ClientLevelMixin.tickTime): wait for it
                standThreeQuarter(mc);
                long t = mc.level.getDayTime() % 24000;
                boolean there = Math.abs(t - 13000) < 150;
                if (!(there && phaseSeconds() > 3) && phaseSeconds() < 40)
                    return;
                if (!there)
                    ReignOfNether.LOGGER.warn("[Eyes] {}_dusk: the client sky is at {} not 13000 after 40 s", label, t);
                shot(mc, label + "_dusk");
                // then the golden-hour RTS still: the low afternoon sun of lovish's villa reference (Oct 28)
                var server = mc.getSingleplayerServer();
                server.execute(() -> server.overworld().setDayTime(EyesPhotoBooth.GOLDEN_TIME));
                if (!OrthoviewClientEvents.isEnabled())
                    OrthoviewClientEvents.toggleEnable();
                // turning the RTS view back on brings the HUD back (round r35's golden stills showed it, run 40810627885)
                mc.options.hideGui = true;
                setZoom(ZOOM_NORMAL);
                OrthoviewClientEvents.centreCameraOnPosInstant(structureMiddle(0.3));
                nextPhase(11);
            }
            case 11 -> {   // 15_<name>_golden_rts: the RTS still's framing at GOLDEN_TIME, once the client sky is there
                long t = mc.level.getDayTime() % 24000;
                boolean there = Math.abs(t - EyesPhotoBooth.GOLDEN_TIME) < 150;
                boolean settled = structureSettled(mc, 5);   // called every tick: it re-aims for the first 3.5 s
                if (!(there && settled) && phaseSeconds() < 40)
                    return;
                if (!there)
                    ReignOfNether.LOGGER.warn("[Eyes] {}_golden_rts: the client sky is at {} not {} after 40 s", label, t,
                        EyesPhotoBooth.GOLDEN_TIME);
                mc.options.hideGui = true;
                logAim(mc, label + "_golden_rts");
                shot(mc, label + "_golden_rts");
                // then the extra day times (if asked for) and the shade army
                if (!EXTRA_TIMES.isEmpty()) {
                    timeIndex = 0;
                    startExtraTime(mc);
                    return;
                }
                startShadeArmy(mc);
            }
            case 7 -> {   // an extra day time: the RTS view once the client's sky is there
                long want = EXTRA_TIMES.get(timeIndex);
                long t = mc.level.getDayTime() % 24000;
                boolean there = Math.abs(t - want) < 150;
                if (!(there && phaseSeconds() > 4) && phaseSeconds() < 40)
                    return;
                if (!there)
                    ReignOfNether.LOGGER.warn("[Eyes] {}_rts_t{}: the client sky is at {} after 40 s", label, want, t);
                shot(mc, label + "_rts_t" + want);
                if (OrthoviewClientEvents.isEnabled())
                    OrthoviewClientEvents.toggleEnable();
                standThreeQuarter(mc);
                nextPhase(8);
            }
            case 8 -> {   // ... and the 3/4 view
                if (phaseSeconds() > 1.5 && phaseSeconds() < 2.5)
                    standThreeQuarter(mc);
                if (phaseSeconds() < 5)
                    return;
                shot(mc, label + "_34_t" + EXTRA_TIMES.get(timeIndex));
                if (++timeIndex < EXTRA_TIMES.size()) {
                    startExtraTime(mc);
                    return;
                }
                startShadeArmy(mc);
            }
            case 9 -> {   // the shade army: noon's sky back on the client and the units standing
                long t = mc.level.getDayTime() % 24000;
                boolean there = Math.abs(t - SHADE_ARMY_TIME) < 150;
                if (!(there && phaseSeconds() > 5) && phaseSeconds() < 40)
                    return;
                if (!there)
                    ReignOfNether.LOGGER.warn("[Eyes] {}_rts_army: the client sky is at {} after 40 s", label, t);
                shot(mc, label + "_rts_army");
                runCommand(mc, "kill @e[tag=" + SHADE_ARMY_TAG + "]");
                if (MIST_STILL) {
                    // then the mist pilot's still: the same RTS framing (the view on, default zoom) in the thick of dawn
                    var server = mc.getSingleplayerServer();
                    server.execute(() -> server.overworld().setDayTime(
                        com.solegendary.reignofnether.ambient.mist.MistMath.EYES_DAWN_TIME));
                    mistSinceNanos = -1;
                    mc.options.hideGui = true;
                    setZoom(ZOOM_NORMAL);
                    OrthoviewClientEvents.centreCameraOnPosInstant(structureMiddle(0.3));
                    nextPhase(12);
                    return;
                }
                restoreDayTime(mc);
                nextStructure(mc);
            }
            case 12 -> {   // 15_<name>_mist_rts: dawn, once the client sky is there and the mist has had ~8 s to gather
                long t = mc.level.getDayTime() % 24000;
                long dawn = com.solegendary.reignofnether.ambient.mist.MistMath.EYES_DAWN_TIME;
                boolean there = Math.abs(t - dawn) < 150;
                boolean settled = structureSettled(mc, 5);   // called every tick: it re-aims for the first 3.5 s
                if (there && mistSinceNanos < 0) {
                    mistSinceNanos = System.nanoTime();
                    mistSinceFrames = frames;
                }
                double mistSeconds = mistSinceNanos < 0 ? 0 : (System.nanoTime() - mistSinceNanos) / 1e9;
                int alive = com.solegendary.reignofnether.ambient.mist.MistParticle.alive;
                // ~8 s of drawn frames at dawn so the wisps have spawned, faded in and drifted; a slow (shader) client
                // spawns slower - give it up to 20 s for most of the r2 cap (120 at zoom 30)
                boolean gathered = mistSeconds >= 8 && frames - mistSinceFrames >= 8 && (alive >= 80 || mistSeconds >= 20);
                if (!(there && settled && gathered) && phaseSeconds() < 60)
                    return;
                if (!there)
                    ReignOfNether.LOGGER.warn("[Eyes] {}_mist_rts: the client sky is at {} not {} after 60 s", label, t, dawn);
                mc.options.hideGui = true;
                logAim(mc, label + "_mist_rts");
                ReignOfNether.LOGGER.info("[Eyes] {}_mist_rts: {} mist wisps alive, {} s at dawn, density {}, mist {}",
                    label, alive, String.format("%.1f", mistSeconds),
                    com.solegendary.reignofnether.ambient.mist.MistMath.density(t),
                    com.solegendary.reignofnether.ambient.mist.MistPass.drawing() ? "drawn as a volume"
                        : com.solegendary.reignofnether.ambient.mist.MistClientEvents.mayShow() ? "showing as sprites" : "NOT showing");
                shot(mc, label + "_mist_rts");
                restoreDayTime(mc);
                nextStructure(mc);
            }
            default -> nextStructure(mc);
        }
    }

    /** Back to the server's time before the dusk still (or early morning if it was never read). */
    private static void restoreDayTime(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        long back = dayTimeBefore < 0 ? 1000 : dayTimeBefore;
        server.execute(() -> server.overworld().setDayTime(back));
    }

    /**
     * Noon again, the RTS camera back over the structure, and two of our militia (standing still, tagged for removal)
     * three blocks out from the middle of each side: the sides in the structure's shadow show units in shade.
     */
    private static void startShadeArmy(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> server.overworld().setDayTime(SHADE_ARMY_TIME));
        String me = mc.player.getName().getString().replace("\"", "");
        int x0 = structureOrigin.getX(), z0 = structureOrigin.getZ();
        int x1 = x0 + structureSize.getX() - 1, z1 = z0 + structureSize.getZ() - 1;
        int mx = (x0 + x1) / 2, mz = (z0 + z1) / 2;
        int[][] spots = { { mx - 1, z0 - 3 }, { mx + 1, z0 - 3 }, { mx - 1, z1 + 3 }, { mx + 1, z1 + 3 },
                { x0 - 3, mz - 1 }, { x0 - 3, mz + 1 }, { x1 + 3, mz - 1 }, { x1 + 3, mz + 1 } };
        for (int[] at : spots) {
            Vec3 g = ground(mc, at[0], at[1], structureOrigin.getY());
            runCommand(mc, String.format("rtsapi-summon \"%s\" %s:militia_unit %d %d %d {NoAI:1b,Tags:[\"%s\"]}", me,
                    ReignOfNether.MOD_ID, at[0], (int) g.y, at[1], SHADE_ARMY_TAG));
        }
        if (!OrthoviewClientEvents.isEnabled())
            OrthoviewClientEvents.toggleEnable();
        mc.options.hideGui = true;
        setZoom(ZOOM_NORMAL);
        OrthoviewClientEvents.centreCameraOnPosInstant(structureMiddle(0.3));
        nextPhase(9);
    }

    /** The server to the next extra day time, and the RTS camera back over the structure at the default zoom. */
    private static void startExtraTime(Minecraft mc) {
        long want = EXTRA_TIMES.get(timeIndex);
        var server = mc.getSingleplayerServer();
        server.execute(() -> server.overworld().setDayTime(want));
        if (!OrthoviewClientEvents.isEnabled())
            OrthoviewClientEvents.toggleEnable();
        mc.options.hideGui = true;
        setZoom(ZOOM_NORMAL);
        OrthoviewClientEvents.centreCameraOnPosInstant(structureMiddle(0.3));
        nextPhase(7);
    }

    private static void nextStructure(Minecraft mc) {
        structureIndex++;
        structurePhase = 0;
        ticks = 0;
        if (!OrthoviewClientEvents.isEnabled())
            OrthoviewClientEvents.toggleEnable();
    }

    /** A point inside the placed structure: its footprint's centre, at a fraction of its height. */
    private static Vec3 structureMiddle(double heightFraction) {
        return new Vec3(structureOrigin.getX() + structureSize.getX() / 2.0,
            structureOrigin.getY() + structureSize.getY() * heightFraction,
            structureOrigin.getZ() + structureSize.getZ() / 2.0);
    }

    /**
     * The 3/4 view: from the south-west (-x, +z: the faces the RTS camera sees, design/lively_buildings.md), 30 degrees
     * above the horizontal, 1.5x the building's largest size from its middle (at least 12 blocks), looking at it.
     */
    private static void standThreeQuarter(Minecraft mc) {
        int big = Math.max(structureSize.getX(), Math.max(structureSize.getY(), structureSize.getZ()));
        double d = Math.max(12, 1.5 * big);
        Vec3 aim = structureMiddle(0.4);
        double h = d * Math.cos(Math.toRadians(30)), up = d * Math.sin(Math.toRadians(30));
        double px = aim.x - h / Math.sqrt(2), pz = aim.z + h / Math.sqrt(2);
        double eyeY = aim.y + up;
        int ground = surfaceY(mc, (int) Math.floor(px), (int) Math.floor(pz));
        if (ground != Integer.MIN_VALUE)
            eyeY = Math.max(eyeY, ground + 2.5);   // never inside a hill on the far side of the pad
        double py = eyeY - mc.player.getEyeHeight();
        double dx = aim.x - px, dz = aim.z - pz, dy = aim.y - eyeY;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
        mc.player.moveTo(px, py, pz, yaw, pitch);
        mc.player.setDeltaMovement(Vec3.ZERO);
        mc.options.hideGui = true;
    }

    /** The close still's camera {eyeX, eyeY, eyeZ, yaw, pitch, aimX, aimY, aimZ} (planClose), held for its phase. */
    private static double[] closeCam = null;

    /**
     * The close still's camera: standing CLOSE_DISTANCE from the footprint's middle on the ground there, a person's
     * eye height up, looking at the ground in the middle (EyesPhotoBooth.closeCamera). South-west first; a spot whose
     * eye would be inside a block (a trunk, a canopy, a tall plant's top, a wall) gives way to the next direction.
     */
    private static double[] planClose(Minecraft mc, String label) {
        double mx = structureOrigin.getX() + structureSize.getX() / 2.0, mz = structureOrigin.getZ() + structureSize.getZ() / 2.0;
        int gm = surfaceY(mc, (int) Math.floor(mx), (int) Math.floor(mz));
        double ay = gm == Integer.MIN_VALUE ? structureOrigin.getY() : gm;
        double[] first = null;
        int[] firstDir = null;
        for (int[] dir : EyesPhotoBooth.CLOSE_DIRECTIONS) {
            double[] spot = EyesPhotoBooth.closeSpot(mx, mz, dir);
            int bx = (int) Math.floor(spot[0]), bz = (int) Math.floor(spot[1]);
            int g = surfaceY(mc, bx, bz);
            double[] cam = EyesPhotoBooth.closeCamera(spot[0], spot[1], g == Integer.MIN_VALUE ? ay : g, mx, ay, mz);
            if (first == null) {
                first = cam;
                firstDir = dir;
            }
            if (g == Integer.MIN_VALUE)
                continue;
            int eyeCell = (int) Math.floor(cam[1]);
            boolean open = mc.level.getBlockState(new BlockPos(bx, eyeCell, bz)).isAir();
            for (int y = g; y < eyeCell && open; y++)
                open = !mc.level.getBlockState(new BlockPos(bx, y, bz)).blocksMotion();
            if (open)
                return closeAt(label, cam, dir, mx, ay, mz, "");
        }
        return closeAt(label, first, firstDir, mx, ay, mz, " - no direction had its eye in open air, the first is used");
    }

    private static double[] closeAt(String label, double[] cam, int[] dir, double mx, double ay, double mz, String note) {
        ReignOfNether.LOGGER.info("[Eyes] {}_close: standing at {} (direction {}, {} from the middle), eye {} over the "
            + "aim, pitch {} down{}", label, String.format("%.1f, %.1f, %.1f", cam[0], cam[1], cam[2]), dir[0], dir[1],
            String.format("%.2f", cam[1] - ay), String.format("%.0f", cam[4]), note);
        return new double[] { cam[0], cam[1], cam[2], cam[3], cam[4], mx, ay, mz };
    }

    private static void standClose(Minecraft mc) {
        mc.player.moveTo(closeCam[0], closeCam[1] - mc.player.getEyeHeight(), closeCam[2], (float) closeCam[3],
            (float) closeCam[4]);
        mc.player.setDeltaMovement(Vec3.ZERO);
        mc.options.hideGui = true;
    }

    /**
     * Once, before the first structure: fog of war off and the sun at noon. Since fa069d4 (Oct 28) every new match turns
     * fog on, and the site (45 blocks out, past the capitol's vision) then shows the pre-match fog snapshot: run
     * 40767177721's stills of brass_t1_greybox and brass_l3_greybox show a stale wall and dark grass, no building. Noon
     * (day time 6000) is the light building colour is judged in (design/building_tiers.md, hard rules); the dusk still
     * still sets 13000 and puts this time back after it.
     */
    private static void lightTheSite(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null)
            return;
        server.execute(() -> {
            if (com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isEnabled())
                com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.setEnabled(false);
            server.overworld().setDayTime(6000);
            ReignOfNether.LOGGER.info("[Eyes] 15 structures: fog of war {}, day time 6000 (noon)",
                com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isEnabled() ? "STILL ON (force-fog rule)" : "off");
        });
    }

    /**
     * 45 blocks from our capitol along a diagonal, on the ground: the axis nearest the arena's centre whose pad keeps
     * clear of the arena wall, on dry ground, with the RTS camera's own spot inside the border (EyesPhotoBooth.chooseAxis).
     */
    private static BlockPos structureSite(Minecraft mc) {
        if (capitolCentre == null)
            capitolCentre = findCapitol(mc);
        // the RTS camera stands back from what it looks at (behind and above it): a site on the capitol's outer side
        // put that camera spot outside the world border, the border clamped it, and every 15_* RTS still showed the
        // ground beside the structure (meadow / ground samplers, runs 40679410746 and 40691285711, Oct 28). A site
        // must be dry AND leave the camera's own spot inside the border.
        // ...and its pad must keep clear of the arena wall: round r34's site (-261, 55, the capitol's outer side) laid
        // its pad across the ring (centre 3, 10, radius 280), cleared the wall there, and the shader stills showed dark
        // diagonal smears on the ring's footprint. The axis toward the arena's centre goes first now.
        var border = mc.level.getWorldBorder();
        double yR = Math.toRadians(OrthoviewClientEvents.getCamRotX());
        double xR = Math.toRadians(OrthoviewClientEvents.getCamRotY());
        double fx = -Math.sin(yR) * Math.cos(xR), fy = -Math.sin(xR), fz = Math.cos(yR) * Math.cos(xR);
        double back = Math.abs(fy) < 1e-3 ? 0 : (mc.player.getEyeY() - capitolCentre.getY()) / fy;
        double[] ring = com.solegendary.reignofnether.startpos.BattlefieldWall.lastRing();
        // no wall: the border's centre (a match border is centred on its arena) and no ring check
        double rx = ring != null ? ring[0] : border.getCenterX(), rz = ring != null ? ring[1] : border.getCenterZ();
        double rr = ring != null ? ring[2] : 0;
        int capX = capitolCentre.getX(), capZ = capitolCentre.getZ();
        int[] axis = EyesPhotoBooth.chooseAxis(capX, capZ, rx, rz, rr, a -> {
            int ax = capX + a[0] * EyesPhotoBooth.SITE_OFFSET, az = capZ + a[1] * EyesPhotoBooth.SITE_OFFSET;
            double cx = ax + fx * back, cz = az + fz * back;
            boolean camInside = cx > border.getMinX() + 2 && cx < border.getMaxX() - 2
                && cz > border.getMinZ() + 2 && cz < border.getMaxZ() - 2;
            return camInside && dryAround(mc, ax, az);
        });
        boolean found = axis != null;
        if (!found)
            axis = EyesPhotoBooth.siteAxes(capX, capZ, rx, rz)[0];
        int sx = capX + axis[0] * EyesPhotoBooth.SITE_OFFSET, sz = capZ + axis[1] * EyesPhotoBooth.SITE_OFFSET;
        String wall = ring == null ? "no arena wall" : String.format("the wall's ring round %.0f, %.0f, radius %.0f: a %d-wide "
            + "pad here keeps %.0f blocks from it", rx, rz, rr, EyesPhotoBooth.PLANNED_FOOTPRINT,
            EyesPhotoBooth.ringDistance(EyesPhotoBooth.padBox(sx, sz, EyesPhotoBooth.PLANNED_FOOTPRINT,
                EyesPhotoBooth.PLANNED_FOOTPRINT), rx, rz, rr));
        ReignOfNether.LOGGER.info("[Eyes] 15 structures: site axis ({}, {}) from the capitol {} ({}){}", axis[0], axis[1],
            capitolCentre.toShortString(), wall, found ? ""
                : " - no axis was clear of the wall, dry and kept the camera inside the border: the one nearest the centre");
        Vec3 g = ground(mc, sx, sz, capitolCentre.getY());
        return BlockPos.containing(g);
    }

    /**
     * On the server thread: reads the template (our jar's data/ by name, or a .nbt file by path), clears the previous
     * structure's box, lays a flat pad (stone under grass, 8 blocks round the footprint, air above) and stamps it with
     * its footprint centred on the site. Sets structurePlaced or structureError.
     */
    private static void placeStructure(Minecraft mc, String entry) {
        var server = mc.getSingleplayerServer();
        BlockPos site = siteCentre;
        server.execute(() -> {
            try {
                var level = server.overworld();
                var manager = server.getStructureManager();
                net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate template;
                if (entry.endsWith(".nbt")) {
                    java.nio.file.Path p = java.nio.file.Path.of(entry);
                    if (!java.nio.file.Files.isRegularFile(p)) {
                        structureError = "no file " + p;
                        return;
                    }
                    try (var in = java.nio.file.Files.newInputStream(p)) {
                        template = manager.readStructure(net.minecraft.nbt.NbtIo.readCompressed(in));
                    }
                } else {
                    var found = manager.get(new net.minecraft.resources.ResourceLocation(ReignOfNether.MOD_ID, entry));
                    if (found.isEmpty()) {
                        structureError = "no structure data/" + ReignOfNether.MOD_ID + "/structures/" + entry + ".nbt in this build";
                        return;
                    }
                    template = found.get();
                }
                var size = template.getSize();
                if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
                    structureError = "empty structure (size " + size + ")";
                    return;
                }
                // unknown block ids load as air (BuildingBlockData.unknownBlockIds) - name them in the log
                if (entry.endsWith(".nbt"))
                    try (var in = java.nio.file.Files.newInputStream(java.nio.file.Path.of(entry))) {
                        var unknown = com.solegendary.reignofnether.building.BuildingBlockData.unknownBlockIds(
                            net.minecraft.nbt.NbtIo.readCompressed(in));
                        if (!unknown.isEmpty())
                            ReignOfNether.LOGGER.warn("[Eyes] 15_{}: names unregistered blocks {} - they load as air",
                                structureLabel(entry), unknown);
                    }
                int y = site.getY();
                BlockPos origin = new BlockPos(site.getX() - size.getX() / 2, y, site.getZ() - size.getZ() / 2);
                int[] pad = EyesPhotoBooth.padBox(site.getX(), site.getZ(), size.getX(), size.getZ());
                int x0 = pad[0], z0 = pad[1], x1 = pad[2], z1 = pad[3];
                int top = y + size.getY() + 12;
                // the pad clears everything in its box: it must keep off the arena wall (round r34 cut the wall)
                double[] ring = com.solegendary.reignofnether.startpos.BattlefieldWall.lastRing();
                if (ring == null)
                    ReignOfNether.LOGGER.info("[Eyes] 15_{}: pad x {}..{}, z {}..{} - no arena wall to keep clear of",
                        structureLabel(entry), x0, x1, z0, z1);
                else {
                    double gap = EyesPhotoBooth.ringDistance(pad, ring[0], ring[1], ring[2]);
                    String line = String.format("[Eyes] 15_%s: pad x %d..%d, z %d..%d keeps %.1f blocks from the arena "
                        + "wall's ring (centre %.0f, %.0f, radius %.0f)", structureLabel(entry), x0, x1, z0, z1, gap,
                        ring[0], ring[1], ring[2]);
                    if (gap >= EyesPhotoBooth.MIN_RING_CLEARANCE)
                        ReignOfNether.LOGGER.info(line);
                    else
                        ReignOfNether.LOGGER.warn(line + " - under " + (int) EyesPhotoBooth.MIN_RING_CLEARANCE
                            + ": the pad cuts the wall, its stills show the cut");
                }
                var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                if (lastCleared != null)
                    fillBox(level, lastCleared[0], lastCleared[4], lastCleared[1], lastCleared[2], lastCleared[5], lastCleared[3], air);
                fillBox(level, x0, y - 2, z0, x1, y - 2, z1, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                fillBox(level, x0, y - 1, z0, x1, y - 1, z1, net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState());
                fillBox(level, x0, y, z0, x1, top, z1, air);
                lastCleared = new int[] { x0, z0, x1, z1, y, top };
                template.placeInWorld(level, origin, origin,
                    new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
                    level.getRandom(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                structureSize = size;
                structureOrigin = origin;
                structurePlaced = true;
            } catch (Exception e) {
                structureError = e.toString();
            }
        });
    }

    private static void fillBox(net.minecraft.server.level.ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1,
                                net.minecraft.world.level.block.state.BlockState state) {
        var pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                for (int y = y0; y <= y1; y++)
                    level.setBlock(pos.set(x, y, z), state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }

    // ---- 13: a barrier drawn as geometry (barfx/DomeFx): an Evoker's Bastion Aegis under skeleton fire ----
    private static Vec3 domeCentre = null;
    private static int domeShot = 0;

    private static void sceneDome(Minecraft mc) {
        if (ticks == 1) {
            UnitClientEvents.clearSelectedUnits();
            if (capitolCentre == null)
                capitolCentre = findCapitol(mc);
            if (enemyName == null)
                enemyName = findEnemyName(mc);
            // 30 east of the capitol and 30 south: clear of 07-10's field (south) and 12's river (west); a laid pad
            domeCentre = ground(mc, capitolCentre.getX() + 30, capitolCentre.getZ() + 30, capitolCentre.getY());
            int cx = (int) Math.floor(domeCentre.x), cz = (int) Math.floor(domeCentre.z), y = (int) domeCentre.y;
            runCommand(mc, String.format("fill %d %d %d %d %d %d minecraft:stone", cx - 14, y - 2, cz - 14, cx + 14, y - 2, cz + 14));
            runCommand(mc, String.format("fill %d %d %d %d %d %d minecraft:grass_block", cx - 14, y - 1, cz - 14, cx + 14, y - 1, cz + 14));
            runCommand(mc, String.format("fill %d %d %d %d %d %d minecraft:air", cx - 14, y, cz - 14, cx + 14, y + 8, cz + 14));
            domeShot = 0;
            setZoom(ZOOM_NORMAL);
            OrthoviewClientEvents.centreCameraOnPosInstant(domeCentre);
            ReignOfNether.LOGGER.info("[Eyes] 13_dome: pad round {}", fmt(domeCentre));
            return;
        }
        if (ticks == 20) {   // an Evoker with four militia round it; six skeletons 11 blocks east, facing west
            String me = mc.player.getName().getString();
            String[] mine = { "militia_unit", "militia_unit", "evoker_unit", "militia_unit", "militia_unit" };
            String[] theirs = { "skeleton_unit", "skeleton_unit", "skeleton_unit", "skeleton_unit", "skeleton_unit", "skeleton_unit" };
            int sent = summonGridFacingX(mc, me, mine, domeCentre, 270)
                + summonGridFacingX(mc, enemyName, theirs, domeCentre.add(11, 0, 0), 90);
            ReignOfNether.LOGGER.info("[Eyes] 13_dome: sent {} summon commands", sent);
            return;
        }
        if (ticks == 50) {   // the cast, as the ability button sends it (Bastion Aegis: a dome where the Evoker stands)
            List<LivingEntity> mine = unitsNear(mc, domeCentre, 6, true);
            LivingEntity evoker = null;
            for (LivingEntity le : mine)
                if (le instanceof com.solegendary.reignofnether.unit.units.villagers.EvokerUnit)
                    evoker = le;
            if (evoker == null) {
                giveUp("13_dome", "no evoker on the pad");
                return;
            }
            UnitClientEvents.sendUnitCommandManual(UnitAction.BASTION_AEGIS, -1, new int[] { evoker.getId() }, evoker.blockPosition());
            ReignOfNether.LOGGER.info("[Eyes] 13_dome: Bastion Aegis cast by {} at {}", evoker.getId(), evoker.blockPosition().toShortString());
            return;
        }
        if (ticks == 50 + 14) {   // the rise is 0.35 s: well up by now
            ReignOfNether.LOGGER.info("[Eyes] 13_dome: domes on the client {}", com.solegendary.reignofnether.barfx.BarFxClient.liveDomes());
            shot(mc, "13_dome");
            domeShot = 1;
            return;
        }
        if (domeShot == 1 && (projectiles(mc) > 0 || ticks >= 50 + 20 * 7)) {
            shotAt = ticks + 6;   // a moment after the first arrows: the ripples should be on the skin
            domeShot = 2;
            return;
        }
        if (domeShot == 2 && ticks == shotAt) {
            ReignOfNether.LOGGER.info("[Eyes] 13_dome_hit: domes on the client {}, projectiles {}",
                com.solegendary.reignofnether.barfx.BarFxClient.liveDomes(), projectiles(mc));
            shot(mc, "13_dome_hit");
            next();
            return;
        }
        if (ticks > 20 * 30)
            giveUp("13_dome", "never got to the photographs");
    }

    // ---- 06: the build menu (an owned worker's card) ----
    private static void sceneBuildMenu(Minecraft mc) {
        if (shotAt < 0) {
            if (ticks > 20 * 10) {
                giveUp("06_build_menu", "no owned worker in the client roster");
                return;
            }
            if (ticks % 20 != 1)
                return;   // the roster may still be syncing: look once a second
            LivingEntity worker = null;
            for (LivingEntity le : new ArrayList<>(UnitClientEvents.getAllUnits()))
                if (le instanceof WorkerUnit && le instanceof Unit && !le.isPassenger() && le.isAlive()
                        && UnitClientEvents.getPlayerToEntityRelationship(le) == Relationship.OWNED) {
                    worker = le;
                    break;
                }
            if (worker == null)
                return;
            UnitClientEvents.clearSelectedUnits();
            BuildingClientEvents.clearSelectedBuildings();
            UnitClientEvents.addSelectedUnit(worker);
            HudClientEvents.setHudSelectedEntity(worker);
            setZoom(ZOOM_NORMAL);
            OrthoviewClientEvents.centreCameraOnPosInstant(worker.position());
            int buttons = worker instanceof Unit u ? u.getAbilityButtons().size() : -1;
            ReignOfNether.LOGGER.info("[Eyes] 06_build_menu: selected {} (id {}), its card shows {} buttons (abilities + buildings)",
                    worker.getType().toShortString(), worker.getId(), buttons);
            shotAt = ticks + 30;   // settle (camera teleport, zoom spring), then photograph
            return;
        }
        if (ticks == shotAt) {
            if (UnitClientEvents.getSelectedUnits().isEmpty())
                giveUp("06_build_menu", "the worker left the selection before the shot");
            else {
                shot(mc, "06_build_menu");
                next();
            }
        }
    }

    // ---- 07: two armies, by the game's own summon command ----
    private static void sceneArmy(Minecraft mc) {
        if (ticks == 1) {
            UnitClientEvents.clearSelectedUnits();
            capitolCentre = findCapitol(mc);
            enemyName = findEnemyName(mc);
            // the player's army 22 blocks south of the capitol's centre (off its apron), the enemy 18 further (first
            // flight: at 25 the militia never closed the gap before the cap)
            // the first dry axis out of the capitol, south first (the run for c8da258 put the whole fight in a lake
            // south of the capitol - 07/08 are about the army, the river scene is 12's job)
            int[][] axes = { { 0, 1 }, { 1, 0 }, { -1, 0 }, { 0, -1 } };
            int[] axis = axes[0];
            for (int[] a : axes)
                if (dryAround(mc, capitolCentre.getX() + a[0] * 22, capitolCentre.getZ() + a[1] * 22)
                        && dryAround(mc, capitolCentre.getX() + a[0] * 40, capitolCentre.getZ() + a[1] * 40)) {
                    axis = a;
                    break;
                }
            armyCentre = ground(mc, capitolCentre.getX() + axis[0] * 22, capitolCentre.getZ() + axis[1] * 22, capitolCentre.getY());
            enemyCentre = ground(mc, capitolCentre.getX() + axis[0] * 40, capitolCentre.getZ() + axis[1] * 40, capitolCentre.getY());
            String me = mc.player.getName().getString();
            // Sunforged: a mixed dozen with crossbows (tinted bolts), a witch (thrown potions) and a golem (big unit)
            String[] mine = { "militia_unit", "militia_unit", "militia_unit", "militia_unit",
                    "pillager_unit", "pillager_unit", "pillager_unit", "pillager_unit",
                    "vindicator_unit", "vindicator_unit", "witch_unit", "iron_golem_unit" };
            // Gravebound: bows (arrows), spiders, a creeper
            String[] theirs = { "zombie_unit", "zombie_unit", "zombie_unit", "zombie_unit",
                    "skeleton_unit", "skeleton_unit", "skeleton_unit", "skeleton_unit",
                    "spider_unit", "spider_unit", "stray_unit", "creeper_unit" };
            // yaw 0 faces +z (south, towards the enemy), 180 faces north: the two grids face each other
            // yaw 0 = +z: each army faces along the axis towards the other
            int yaw = (int) Math.round(Math.toDegrees(Math.atan2(-axis[0], axis[1])));
            int sent = summonGrid(mc, me, mine, armyCentre, yaw) + summonGrid(mc, enemyName, theirs, enemyCentre, yaw + 180);
            setZoom(ZOOM_NORMAL);
            OrthoviewClientEvents.centreCameraOnPosInstant(armyCentre);
            ReignOfNether.LOGGER.info("[Eyes] 07_army_close: sent {} summon commands; me '{}' at {}, enemy '{}' at {}, capitol {}, axis {}/{}",
                    sent, me, fmt(armyCentre), enemyName, fmt(enemyCentre), capitolCentre.toShortString(), axis[0], axis[1]);
            return;
        }
        if (shotAt < 0) {
            int mine = unitsNear(mc, armyCentre, 12, true).size();
            boolean ready = mine >= 12 && ticks > 40;
            if (ready || ticks > 20 * 12) {
                if (!ready)
                    ReignOfNether.LOGGER.warn("[Eyes] 07_army_close: only {} of 12 owned units arrived in 12 s, photographing anyway", mine);
                // one veteran in the front row: the max-health modifier VeterancyServerEvents.award adds (its UUID,
                // MULTIPLY_TOTAL) is what the client reads the rank from, so vanilla /attribute makes a rank-2 veteran
                // and the still shows the gold notches above its health bar (HealthBarClientEvents, Oct 14).
                // 1.20.1's operation words are add | multiply_base | multiply - "multiply" IS MULTIPLY_TOTAL; the first
                // two eyes runs sent "multiply_total", which the parser refused in red over 07_army_close, so no
                // veteran was ever made (the 592/610 that "proved" it was one hurt levy)
                var front = unitsNear(mc, armyCentre, 12, true);
                if (!front.isEmpty()) {
                    var vet = front.get(0);
                    // our own command (VeterancyServerEvents.onRegisterCommands): the real award path. The vanilla
                    // /attribute line before it ran without an error on run 38588686686 and still moved nothing
                    runCommand(mc, "rts-veteran " + vet.getStringUUID() + " 2");
                    ReignOfNether.LOGGER.info("[Eyes] 07_army_close: veteran rank 2 on {}", vet.getName().getString());
                }
                OrthoviewClientEvents.centreCameraOnPosInstant(armyCentre);
                shotAt = ticks + 15;   // settle after the recentre
            }
            return;
        }
        if (ticks == shotAt) {
            List<LivingEntity> mine = unitsNear(mc, armyCentre, 12, true), theirs = unitsNear(mc, enemyCentre, 12, false);
            // the rosters by type and the spread of each grid: the first flight's shot showed one side clumped on one
            // block and the other looking like livestock, which the counts alone could not tell apart
            ReignOfNether.LOGGER.info("[Eyes] 07_army_close: mine {} within 12 of {} spread {}: {}; enemy {} within 12 of {} spread {}: {}; {}",
                    mine.size(), fmt(armyCentre), spread(mine), roster(mine),
                    theirs.size(), fmt(enemyCentre), spread(theirs), roster(theirs), battleLine(mc));
            shot(mc, "07_army_close");
            // 07b-s: close-ups of the bodies made through the concept -> game pipeline (tools/dsl_to_game.py), each
            // zoomed all the way in: a still with its HUD portrait, a walk across the frame, then two enemy ghouls at
            // its feet (the paladin chops, the medic throws)
            closeUpSubject = 0;
            closeUpId = -1;
            closeUpRetried = -1;
            standInDue = -1;
            advanceCloseUps(mc);
            return;
        }
        if (standInDue > 0) {
            // a stand-in was summoned for the current subject: look for it every few ticks until it is due
            if (ticks < standInDue && ticks % 5 != 0)
                return;
            advanceCloseUps(mc);
            return;
        }
        if (closeUpAt > 0) {
            var unit = mc.level.getEntity(closeUpId);
            String[] names = CLOSE_UPS[closeUpSubject];
            // follow it while it walks and fights, a little to one side (the cursor rests at the screen's centre and
            // its hover card covered the unit; centred, a selected unit sat under the HUD panel)
            // every tick, the still too: a single centring raced the zoom spring (tilt follows zoom, and the teleport
            // lands frames later with a spot for the old tilt - the 07b paladin sat under the HUD panel), and a unit
            // standing in a live fight drifts in the 5 s before its still
            if (unit != null)
                placeOnScreen(mc, closeUpMiddle(unit), CLOSE_NDC_X, CLOSE_NDC_Y);
            if (ticks != closeUpAt)
                return;
            // the camera follows through a server teleport (centreCameraOnPosInstant), so on a slow client (the shader
            // lane draws ~1 frame a second) it trails a walking subject: the walk stills of the first shader run had
            // their subject at the top edge or gone (framing check, run 38736735370). Wait for the frame to catch up,
            // a quarter second at a time, up to 2 s; the still is taken either way.
            if (unit != null && framingFault(mc, unit) != null && framingWaits < 8) {
                framingWaits++;
                closeUpAt = ticks + 5;
                return;
            }
            waitedTicks = framingWaits * 5;   // for the framing line
            framingWaits = 0;
            if (unit == null && closeUpPhase == 0 && closeUpRetried != closeUpSubject) {
                // lost before its FIRST still (the shader lane's slow runner: the medic died mid-zoom and 07e-g were
                // all skipped, run 38779063245) - start this subject again once: another of its kind, or a stand-in
                ReignOfNether.LOGGER.warn("[Eyes] {}: the subject died before its still - trying another one", names[1]);
                closeUpRetried = closeUpSubject;
                closeUpAt = -1;
                UnitClientEvents.clearSelectedUnits();
                advanceCloseUps(mc);
                return;
            }
            if (unit == null || closeUpPhase >= 2) {
                if (unit != null) {
                    checkFraming(mc, unit, names[3]);
                    shot(mc, names[3]);
                }
                else
                    ReignOfNether.LOGGER.warn("[Eyes] {}: the subject was gone before its still (phase {})", names[1], closeUpPhase);
                closeUpAt = -1;
                UnitClientEvents.clearSelectedUnits();
                closeUpSubject++;
                advanceCloseUps(mc);
                return;
            }
            if (closeUpPhase == 0) {
                ReignOfNether.LOGGER.info("[Eyes] {}: zoom {} (target reached when it equals the zoom above)", names[1],
                        OrthoviewClientEvents.getZoom());
                checkFraming(mc, unit, names[1]);
                shot(mc, names[1]);
                UnitClientEvents.clearSelectedUnits();   // the portrait is in the still; the rings would hide the walk
                BlockPos to = BlockPos.containing(unit.getX() + 7, unit.getY(), unit.getZ());
                UnitClientEvents.sendUnitCommandManual(UnitAction.MOVE, -1, new int[] { closeUpId }, to);
                ReignOfNether.LOGGER.info("[Eyes] {}: moving it to {}", names[2], to.toShortString());
                closeUpAt = ticks + 40;
            } else {
                checkFraming(mc, unit, names[2]);
                shot(mc, names[2]);
                // the ghouls are training dummies (AI off, like the foes of ReactionGameTests): they never hit back, so
                // the subject is drawn clean for the whole clip. With fighting ghouls, vanilla's red hurt tint and the
                // hit particles covered the body in about half of the 10 fps frames of every attack clip (run
                // 39295387553 for 5169d6d) - and the body is what these clips exist to show. The dummies still die
                // under the blows, so the clip keeps its kill. rtsapi-summon loads the entity from the nbt it is given
                if (enemyName != null)
                    for (int k = 0; k < 2; k++)
                        runCommand(mc, String.format("rtsapi-summon \"%s\" %s:zombie_unit %d %d %d {NoAI:1b}", enemyName.replace("\"", ""),
                                ReignOfNether.MOD_ID, (int) Math.floor(unit.getX()) + 2, (int) Math.floor(unit.getY()),
                                (int) Math.floor(unit.getZ()) - 1 + 2 * k));
                ReignOfNether.LOGGER.info("[Eyes] {}: two ghouls of '{}' at its feet, AI off (dummies)", names[3], enemyName);
                closeUpAt = ticks + 100;   // several swings / throws inside the clip
            }
            closeUpPhase++;
        }
    }

    // 07b-s: { entity class name (":bow": a squire swapped to the bow), still, walk, fight, entity id for a stand-in }
    // per generated body
    private static final String[][] CLOSE_UPS = {
        { "IronGolemUnit", "07b_paladin_close", "07c_paladin_walk", "07d_paladin_attack", "iron_golem_unit" },
        { "WitchUnit", "07e_medic_close", "07f_medic_walk", "07g_medic_throw", "witch_unit" },
        { "VindicatorUnit", "07h_halberdier_close", "07i_halberdier_walk", "07j_halberdier_attack", "vindicator_unit" },
        { "PillagerUnit", "07k_levy_close", "07l_levy_walk", "07m_levy_shoot", "pillager_unit" },
        { "MilitiaUnit", "07n_squire_close", "07o_squire_run", "07p_squire_attack", "militia_unit" },
        // another squire, swapped to the bow on the integrated server first (the side-on aim, the drawn arrow), made a
        // captain (the banner on the helm)
        { "MilitiaUnit:bow", "07q_squire_bow_close", "07r_squire_bow_run", "07s_squire_shoot", "militia_unit" },
        // the dragon rig (Lane B, PR #79): flight beat, glide and bite need to be seen moving, not judged from stills
        { "BoneDragonUnit", "07t_bone_dragon_close", "07u_bone_dragon_fly", "07v_bone_dragon_bite", "bone_dragon_unit" },
        // the quadruped rig (Lane B, PR #76): the Mossback Bison's coat, grove and gait need to be seen moving, not
        // judged from stills (07t-v are the dragon's on claude/dragon-rig, 07z the mesh A/B)
        { "MossbackBisonUnit", "07w_mossback_bison_close", "07x_mossback_bison_walk", "07y_mossback_bison_attack", "mossback_bison_unit" },
    };
    private static int closeUpSubject = 0;
    private static int lookOffAt = -1;               // 02b: tick of the look-off still (the world-look A/B)
    private static int framingWaits = 0;             // close-up stills deferred so far for a badly framed subject
    private static int waitedTicks = 0;              // how long the current still waited for its framing
    // the tour's world seed: any fixed value; changing it re-bases every eyes_diff comparison (approve a new set)
    private static final long EYES_SEED = 4242424242L;
    private static int closeUpId = -1;
    private static int standInDue = -1;   // tick by which a summoned stand-in must have reached the client; -1 = none

    // The armies of 07 stand 18 blocks apart and the fight between them is real: while 07b-m ran (about 50 s) the
    // four squires charged and died, so 07n-s found no squire and six stills were never taken (eyes run 38697962581
    // for 3ae95b6: 29 stills, red). The close-ups are about the bodies, not the battle, so a missing subject gets a
    // stand-in of the same unit summoned behind the line, and the log says so in a WARN line.

    /** Starts the close-up at closeUpSubject or the first after it that can start; ends 07 when none is left. */
    private static void advanceCloseUps(Minecraft mc) {
        List<LivingEntity> army = unitsNear(mc, armyCentre, 20, true);
        for (; closeUpSubject < CLOSE_UPS.length; closeUpSubject++) {
            int started = startCloseUp(mc, army);
            if (started > 0) {
                standInDue = -1;
                return;
            }
            if (started < 0)
                return;   // a stand-in is on its way: looked for again from the scene's tick
        }
        standInDue = -1;
        setZoom(ZOOM_NORMAL);
        OrthoviewClientEvents.centreCameraOnPosInstant(armyCentre);
        next();
    }

    /**
     * Summons one unit of the current subject for the player three blocks behind the army's centre (away from the
     * enemy): behind the grid's back row, and inside the 7 x 7 that dryAround checked, so not in water.
     */
    private static boolean summonStandIn(Minecraft mc, String[] names) {
        Vec3 back = enemyCentre == null ? new Vec3(0, 0, -1)
                : armyCentre.subtract(enemyCentre).multiply(1, 0, 1).normalize();
        int x = (int) Math.floor(armyCentre.x + back.x * 3), z = (int) Math.floor(armyCentre.z + back.z * 3);
        Vec3 at = ground(mc, x, z, (int) armyCentre.y);
        String me = mc.player.getName().getString().replace("\"", "");
        boolean sent = runCommand(mc, String.format("rtsapi-summon \"%s\" %s:%s %d %d %d", me, ReignOfNether.MOD_ID,
                names[4], x, (int) at.y, z));
        ReignOfNether.LOGGER.warn("[Eyes] {}: no {} left in the army (the battle took them); {} a stand-in at {} {} {}",
                names[1], names[0], sent ? "summoned" : "could NOT summon", x, (int) at.y, z);
        return sent;
    }

    /**
     * Zooms all the way in on the army's unit of the current close-up subject and selects it: 1 when it started,
     * -1 while a summoned stand-in is awaited, 0 when this subject is skipped.
     */
    private static int startCloseUp(Minecraft mc, List<LivingEntity> army) {
        String[] names = CLOSE_UPS[closeUpSubject];
        boolean bow = names[0].endsWith(":bow");
        String cls = bow ? names[0].substring(0, names[0].length() - 4) : names[0];
        LivingEntity subject = null;
        for (LivingEntity le : army)
            if (le.getClass().getSimpleName().equals(cls) && le.isAlive() && le.getId() != closeUpId) {
                subject = le;
                break;
            }
        if (subject == null) {
            if (standInDue < 0 && summonStandIn(mc, names)) {
                standInDue = ticks + 100;   // five seconds for the command and the entity to reach the client (both squire rows
                                         // timing out together on a slow runner would be 29 stills again)
                return -1;
            }
            if (standInDue > 0 && ticks < standInDue)
                return -1;
            ReignOfNether.LOGGER.warn("[Eyes] {}: no {} in the army, and no stand-in arrived: skipped", names[1], names[0]);
            standInDue = -1;
            return 0;
        }
        // past the player's floor of 10 blocks of view height (a unit was ~11% of the screen there): ~5 for a 2-block
        // body, ~7 for the golem-sized paladin - at the close tilt (55 degrees) the body fills about a third of the
        // screen height
        float closeZoom = Math.max(5f, subject.getBbHeight() * 2.6f);
        setZoom(closeZoom);
        placeOnScreen(mc, closeUpMiddle(subject), CLOSE_NDC_X, CLOSE_NDC_Y);
        ReignOfNether.LOGGER.info("[Eyes] {}: zooming in to {} on {} at {}", names[1], closeZoom,
                subject.getName().getString(), fmt(subject.position()));
        closeUpId = subject.getId();
        if (bow && mc.getSingleplayerServer() != null) {
            int id = closeUpId;
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                for (var lvl : server.getAllLevels())
                    if (lvl.getEntity(id) instanceof MilitiaUnit squire) {
                        squire.swapWeapons(true);
                        // and a captain's banner, as the Castle's Promote Illager gives (CaptainBannerLayer)
                        squire.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
                                net.minecraft.world.entity.raid.Raid.getLeaderBannerInstance());
                    }
            });
        }
        // selected, so the still also shows its HUD portrait
        UnitClientEvents.clearSelectedUnits();
        UnitClientEvents.addSelectedUnit(subject);
        closeUpPhase = 0;
        closeUpAt = ticks + 100;  // the zoom eases in over a few seconds
        return 1;
    }

    // ---- 08: a drag box over the army, through the real screen-event path ----
    private static void sceneBoxSelect(Minecraft mc) {
        if (armyCentre == null) {
            giveUp("08_box_select", "no army was staged");
            return;
        }
        if (ticks > 20 * 20) {
            giveUp("08_box_select", "stuck in phase " + boxPhase);
            return;
        }
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        // the box is centred on the screen (the army is, after 07's recentre); corners tried in turn, since a panel
        // under the press (onboarding, HUD) refuses it
        double[][] starts = { { 0.72, 0.28 }, { 0.72, 0.72 }, { 0.5, 0.25 }, { 0.3, 0.72 }, { 0.5, 0.5 } };
        int t = ticks - boxPhaseStart;
        switch (boxPhase) {
            case 0 -> {   // the press: a corner every other tick until one is accepted
                if (t % 2 != 1)
                    return;
                if (boxCandidate >= starts.length) {
                    ReignOfNether.LOGGER.warn("[Eyes] 08_box_select: no press was accepted at any corner");
                    boxPhase(4);
                    return;
                }
                double[] s = starts[boxCandidate++];
                boxX0 = Math.round(w * s[0]);
                boxY0 = Math.round(h * s[1]);
                boxX1 = w - boxX0;   // the mirrored corner, so the box is centred whichever corner it starts from
                boxY1 = h - boxY0;
                boxPressed = mousePress(mc, boxX0, boxY0);
                if (boxPressed)
                    boxPhase(1);
                else
                    ReignOfNether.LOGGER.info("[Eyes] 08_box_select: press at ({}, {}) refused, trying the next corner", boxX0, boxY0);
            }
            case 1 -> {   // the drag, in six steps over 24 ticks (frames in between light the units under the box)
                if (t >= 4 && t % 4 == 0) {
                    double f = Math.min(1.0, t / 24.0);
                    mouseDrag(mc, boxX0 + (boxX1 - boxX0) * f, boxY0 + (boxY1 - boxY0) * f);
                }
                if (t >= 24)
                    boxPhase(2);
            }
            case 2 -> {   // the box still down: photograph it with its highlights
                if (t == 15) {
                    ReignOfNether.LOGGER.info("[Eyes] 08_box_drag: box ({}, {})-({}, {}) lights {} units", boxX0, boxY0,
                            boxX1, boxY1, UnitClientEvents.getPreselectedUnits().size());
                    shot(mc, "08_box_drag");
                    boxPhase(3);
                }
            }
            case 3 -> {   // release, let the selection and the HUD settle
                if (t == 1)
                    mouseRelease(mc, boxX1, boxY1);
                if (t == 20)
                    boxPhase(4);
            }
            case 4 -> {   // what did the box select? nothing: the Smart Select rules by hand
                String how = "drag box";
                if (UnitClientEvents.getSelectedUnits().isEmpty()) {
                    List<LivingEntity> near = unitsNear(mc, armyCentre, 14, true);
                    List<LivingEntity> picked = SmartSelect.filterBox(near, UnitClientEvents.getSelectedUnits(),
                            SmartSelect.Mods.NONE, SmartSelect.DEFAULT);
                    UnitClientEvents.clearSelectedUnits();
                    for (LivingEntity le : picked)
                        UnitClientEvents.addSelectedUnit(le);
                    HudClientEvents.setLowestCdHudEntity();
                    how = boxPressed ? "drag box selected nothing; SmartSelect.filterBox fallback"
                            : "no press accepted; SmartSelect.filterBox fallback";
                }
                List<LivingEntity> sel = UnitClientEvents.getSelectedUnits();
                armyIds = new int[sel.size()];
                for (int i = 0; i < sel.size(); i++)
                    armyIds[i] = sel.get(i).getId();
                ReignOfNether.LOGGER.info("[Eyes] 08_box_select: {} selected ({}), card shows {}", sel.size(), how,
                        HudClientEvents.hudSelectedEntity == null ? "nothing" : HudClientEvents.hudSelectedEntity.getType().toShortString());
                boxPhase(5);
            }
            case 5 -> {
                if (t == 20) {
                    if (UnitClientEvents.getSelectedUnits().isEmpty())
                        giveUp("08_box_select", "nothing selectable round the army");
                    else {
                        shot(mc, "08_box_select");
                        next();
                    }
                }
            }
            default -> next();
        }
    }

    private static void boxPhase(int phase) {
        boxPhase = phase;
        boxPhaseStart = ticks;
    }

    // ---- 09: attack-move into the enemy, photograph once shots fly ----
    private static void sceneFight(Minecraft mc) {
        if (armyCentre == null || enemyCentre == null) {
            giveUp("09_fight", "no armies were staged");
            return;
        }
        if (ticks == 1) {
            if (armyIds.length == 0) {
                List<LivingEntity> near = unitsNear(mc, armyCentre, 14, true);
                armyIds = new int[near.size()];
                for (int i = 0; i < near.size(); i++)
                    armyIds[i] = near.get(i).getId();
            }
            if (armyIds.length == 0) {
                giveUp("09_fight", "no army to order");
                return;
            }
            UnitClientEvents.sendUnitCommandManual(UnitAction.ATTACK_MOVE, -1, armyIds, BlockPos.containing(enemyCentre));
            String me = mc.player.getName().getString();
            mineAtOrder = countOwned(mc, me, true);
            enemyAtOrder = enemyName == null ? 0 : countOwned(mc, enemyName, false);
            ReignOfNether.LOGGER.info("[Eyes] 09_fight: attack-move {} units to {} ({} mine, {} enemy alive)", armyIds.length,
                    fmt(enemyCentre), mineAtOrder, enemyAtOrder);
            return;
        }
        // contact: a shot in the air, a death on either side, or the two armies' centroids within 8 blocks; 20 s cap
        // (the first flight's 8 s cap expired with the armies still 25 blocks apart). The shot is one second later,
        // so shots and impacts are mid-air.
        if (shotAt < 0 && ticks > 1) {
            String why = null;
            String me = mc.player.getName().getString();
            Vec3 mid = armyCentre.add(enemyCentre).scale(0.5);
            Vec3 a = centroid(unitsNear(mc, mid, 60, true)), b = centroid(unitsNear(mc, mid, 60, false));
            double gap = a == null || b == null ? -1 : Math.sqrt(a.distanceToSqr(b));
            if (projectiles(mc) > 0)
                why = "a projectile is in the air";
            else if (countOwned(mc, me, true) < mineAtOrder || (enemyName != null && countOwned(mc, enemyName, false) < enemyAtOrder))
                why = "a unit died";
            else if (gap >= 0 && gap < 8)
                why = "the armies' centroids are within 8 blocks";
            else if (ticks >= 20 * 20)
                why = "20 s cap, no contact";
            if (why == null)
                return;
            ReignOfNether.LOGGER.info("[Eyes] 09_fight: contact after {} s ({}); armies {} blocks apart", ticks / 20, why,
                    gap < 0 ? "?" : String.format("%.1f", gap));
            OrthoviewClientEvents.centreCameraOnPosInstant(fightCentre(mc));
            shotAt = ticks + 20;
            return;
        }
        if (shotAt > 0 && ticks == shotAt) {
            ReignOfNether.LOGGER.info("[Eyes] 09_fight: {}", battleLine(mc));
            shot(mc, "09_fight");
            next();
            return;
        }
        if (ticks > 20 * 25)
            giveUp("09_fight", "never got to the photograph");
    }

    // ---- 10: the same fight from strategic zoom ----
    private static void sceneFightStrategic(Minecraft mc) {
        if (armyCentre == null || enemyCentre == null) {
            giveUp("10_fight_strategic", "no armies were staged");
            return;
        }
        if (ticks == 1) {
            setZoom(ZOOM_STRATEGIC);
            OrthoviewClientEvents.centreCameraOnPosInstant(fightCentre(mc));
            return;
        }
        if (ticks == 20 * 2) {   // two seconds after 09: the fight is still on, the zoom spring has settled
            ReignOfNether.LOGGER.info("[Eyes] 10_fight_strategic: zoom {} (icons above {}); {}", OrthoviewClientEvents.getZoom(),
                    com.solegendary.reignofnether.orthoview.StrategicViewClientEvents.STRATEGIC_ZOOM_ENTER, battleLine(mc));
            shot(mc, "10_fight_strategic");
            next();
        }
    }

    // ---- 11: the capitol at the default zoom, two minutes in ----
    private static void sceneCapitol(Minecraft mc) {
        if (ticks == 1) {
            UnitClientEvents.clearSelectedUnits();
            setZoom(ZOOM_NORMAL);
            if (capitolCentre == null)
                capitolCentre = findCapitol(mc);
            OrthoviewClientEvents.centreCameraOnPosInstant(new Vec3(capitolCentre.getX() + 0.5, capitolCentre.getY(), capitolCentre.getZ() + 0.5));
            return;
        }
        if (capitolSelAt > 0) {
            capitolSelected(mc);
            return;
        }
        // the match clock runs from the 02 shot (labelled 0:30); -1 if that shot never happened (then shoot at once)
        int matchTick = matchZeroTotalTick < 0 ? -1 : totalTicks - matchZeroTotalTick;
        boolean late = ticks > 20 * 90;   // budget: never wait more than 90 s for the clock
        if (ticks > 40 && (matchTick < 0 || matchTick >= 20 * 120 || late)) {
            if (late)
                ReignOfNether.LOGGER.warn("[Eyes] 11_capitol_two_minutes: gave up waiting for 2:00 (match tick {}), photographing now", matchTick);
            ReignOfNether.LOGGER.info("[Eyes] 11_capitol_two_minutes: match {}:{} capitol {} at zoom {}; {}",
                    Math.max(0, matchTick) / 1200, String.format("%02d", (Math.max(0, matchTick) / 20) % 60),
                    capitolCentre.toShortString(), OrthoviewClientEvents.getZoom(), battleLine(mc));
            shot(mc, "11_capitol_two_minutes");
            groundCensus(mc, "2:00");
            capitolSelAt = ticks + 2;   // 11b: the capitol selected - the building's HUD block, its queue and toggles
        }
    }

    private static int capitolSelAt = -1, capitolSelShotAt = -1;

    /** 11b: select our capitol and photograph the HUD's building block (icon + health bar + Repeat toggle + queue). */
    private static void capitolSelected(Minecraft mc) {
        if (ticks == capitolSelAt) {
            String me = mc.player.getName().getString();
            for (var b : new ArrayList<>(BuildingClientEvents.getBuildings())) {
                if (b instanceof com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement p
                        && p.isCapitol && me.equals(p.ownerName)) {
                    UnitClientEvents.clearSelectedUnits();
                    BuildingClientEvents.clearSelectedBuildings();
                    BuildingClientEvents.addSelectedBuilding(b);
                    break;
                }
            }
            capitolSelShotAt = ticks + 30;   // settle, then photograph
        } else if (ticks == capitolSelShotAt) {
            shot(mc, "11b_capitol_selected");
            BuildingClientEvents.clearSelectedBuildings();
            capitolSelAt = -1;
            capitolSelShotAt = -1;
            next();
        }
    }

    // ---- 12: a river crossing - the army must cross in a line and fight on the far bank, not in the water ----
    // (terrain_intelligence.md: the Oct 13 clips had whole armies fighting chest-deep in rivers)
    private static Vec3 riverCentre = null;      // the middle of the water strip
    private static Vec3 riverMine = null, riverTheirs = null;
    private static int riverMineAtOrder = 0, riverTheirsAtOrder = 0;
    private static int riverShot = 0;            // 0 = waiting for contact, 1 = contact shot taken
    private static final int RIVER_HALF_W = 2;   // the strip is 4 wide (x), 1 deep, the pad's full depth (z)
    private static final int RIVER_PAD = 12;     // the pad runs this far either side of the strip and north/south

    private static void sceneRiverCrossing(Minecraft mc) {
        if (ticks == 1) {
            UnitClientEvents.clearSelectedUnits();
            if (capitolCentre == null)
                capitolCentre = findCapitol(mc);
            if (enemyName == null)
                enemyName = findEnemyName(mc);
            // 30 blocks west of the first fight's ground (07-10), so its survivors and wrecks stay out of frame
            riverCentre = ground(mc, capitolCentre.getX() - 30, capitolCentre.getZ() + 30, capitolCentre.getY());
            int cx = (int) Math.floor(riverCentre.x), cz = (int) Math.floor(riverCentre.z), y = (int) riverCentre.y;
            // the pad, laid with the game's own fill command as the duel pads are laid with setBlock: a solid floor
            // two down, grass one down, air above; then the strip itself a block lower, filled with water
            int x0 = cx - RIVER_HALF_W - RIVER_PAD, x1 = cx + RIVER_HALF_W - 1 + RIVER_PAD;
            int z0 = cz - RIVER_PAD, z1 = cz + RIVER_PAD;
            runCommand(mc, String.format("fill %d %d %d %d %d %d minecraft:stone", x0, y - 2, z0, x1, y - 2, z1));
            runCommand(mc, String.format("fill %d %d %d %d %d %d minecraft:grass_block", x0, y - 1, z0, x1, y - 1, z1));
            runCommand(mc, String.format("fill %d %d %d %d %d %d minecraft:air", x0, y, z0, x1, y + 3, z1));
            runCommand(mc, String.format("fill %d %d %d %d %d %d minecraft:water", cx - RIVER_HALF_W, y - 1, z0,
                    cx + RIVER_HALF_W - 1, y - 1, z1));
            riverMine = new Vec3(cx - RIVER_HALF_W - 7 + 0.5, y, cz + 0.5);     // 7 blocks off the west bank
            riverTheirs = new Vec3(cx + RIVER_HALF_W + 6 + 0.5, y, cz + 0.5);   // 6 blocks off the east bank
            riverShot = 0;
            setZoom(ZOOM_NORMAL);
            OrthoviewClientEvents.centreCameraOnPosInstant(riverCentre);
            ReignOfNether.LOGGER.info("[Eyes] 12_river_crossing: pad {}..{} x {}..{} at y {}, water x {}..{} (1 deep); mine at {}, enemy at {}",
                    x0, x1, z0, z1, y, cx - RIVER_HALF_W, cx + RIVER_HALF_W - 1, fmt(riverMine), fmt(riverTheirs));
            return;
        }
        if (ticks == 20) {   // the fills have run: summon the two groups facing each other across the water
            String me = mc.player.getName().getString();
            String[] mine = { "militia_unit", "militia_unit", "militia_unit", "militia_unit", "militia_unit", "militia_unit",
                    "vindicator_unit", "vindicator_unit", "pillager_unit", "pillager_unit", "pillager_unit", "pillager_unit" };
            String[] theirs = { "zombie_unit", "zombie_unit", "zombie_unit", "zombie_unit",
                    "skeleton_unit", "skeleton_unit", "spider_unit", "spider_unit" };
            // yaw 270 faces +x (east, across the river), 90 faces west
            int sent = summonGridFacingX(mc, me, mine, riverMine, 270) + summonGridFacingX(mc, enemyName, theirs, riverTheirs, 90);
            ReignOfNether.LOGGER.info("[Eyes] 12_river_crossing: sent {} summon commands", sent);
            return;
        }
        if (ticks == 60) {   // the attack-move across the water, as a right-click with Attack armed sends it
            List<LivingEntity> army = unitsNear(mc, riverMine, 8, true);
            if (army.isEmpty()) {
                giveUp("12_river_crossing", "no army arrived on the west bank");
                return;
            }
            int[] ids = new int[army.size()];
            for (int i = 0; i < army.size(); i++)
                ids[i] = army.get(i).getId();
            UnitClientEvents.sendUnitCommandManual(UnitAction.ATTACK_MOVE, -1, ids, BlockPos.containing(riverTheirs));
            String me = mc.player.getName().getString();
            riverMineAtOrder = unitsNear(mc, riverMine, 8, true).size();
            riverTheirsAtOrder = unitsNear(mc, riverTheirs, 8, false).size();
            ReignOfNether.LOGGER.info("[Eyes] 12_river_crossing: attack-move {} units ({} mine, {} enemy near the banks) to {}; {}",
                    ids.length, riverMineAtOrder, riverTheirsAtOrder, fmt(riverTheirs), riverLine(mc, me));
            return;
        }
        if (ticks > 60 && riverShot == 0 && shotAt < 0) {
            // contact: a projectile in the air, a death on either side, the centroids within 8 blocks; 25 s cap
            // (a wading crossing is slower than the open-field march of 09)
            String why = null;
            String me = mc.player.getName().getString();
            Vec3 a = centroid(unitsNear(mc, riverCentre, 40, true)), b = centroid(unitsNear(mc, riverCentre, 40, false));
            double gap = a == null || b == null ? -1 : Math.sqrt(a.distanceToSqr(b));
            if (projectiles(mc) > 0)
                why = "a projectile is in the air";
            else if (unitsNear(mc, riverCentre, 40, true).size() < riverMineAtOrder
                    || unitsNear(mc, riverCentre, 40, false).size() < riverTheirsAtOrder)
                why = "a unit died";
            else if (gap >= 0 && gap < 8)
                why = "the groups' centroids are within 8 blocks";
            else if (ticks >= 60 + 20 * 25)
                why = "25 s cap, no contact";
            if (why == null)
                return;
            ReignOfNether.LOGGER.info("[Eyes] 12_river_crossing: contact after {} s ({}); groups {} blocks apart; {}",
                    (ticks - 60) / 20, why, gap < 0 ? "?" : String.format("%.1f", gap), riverLine(mc, me));
            OrthoviewClientEvents.centreCameraOnPosInstant(riverCentre);
            shotAt = ticks + 10;
            return;
        }
        if (shotAt > 0 && ticks == shotAt) {
            String me = mc.player.getName().getString();
            if (riverShot == 0) {
                ReignOfNether.LOGGER.info("[Eyes] 12_river_crossing (contact): {}", riverLine(mc, me));
                shot(mc, "12_river_crossing");
                riverShot = 1;
                shotAt = ticks + 20 * 6;   // six seconds on: the fight should be on the far bank, not in the water
                return;
            }
            ReignOfNether.LOGGER.info("[Eyes] 12_river_crossing_6s: {}", riverLine(mc, me));
            shot(mc, "12_river_crossing_6s");
            next();
            return;
        }
        if (ticks > 20 * 50)
            giveUp("12_river_crossing", "never got to the photographs");
    }

    /** One line of where everyone stands relative to the water: in it, on the near bank, on the far bank. */
    private static String riverLine(Minecraft mc, String me) {
        int mineWet = 0, mineNear = 0, mineFar = 0, theirsWet = 0, theirsAll = 0;
        double westEdge = Math.floor(riverCentre.x) - RIVER_HALF_W, eastEdge = Math.floor(riverCentre.x) + RIVER_HALF_W;
        for (LivingEntity le : unitsNear(mc, riverCentre, 40, true)) {
            if (le.isInWater()) mineWet++;
            else if (le.getX() < westEdge) mineNear++;
            else if (le.getX() >= eastEdge) mineFar++;
        }
        for (LivingEntity le : unitsNear(mc, riverCentre, 40, false)) {
            theirsAll++;
            if (le.isInWater()) theirsWet++;
        }
        return String.format("mine: %d in the water, %d on the near bank, %d on the far bank (spread %s); enemy '%s': %d alive, %d in the water; projectiles %d",
                mineWet, mineNear, mineFar, spread(unitsNear(mc, riverCentre, 40, true)), enemyName, theirsAll, theirsWet, projectiles(mc));
    }

    /** As summonGrid, but the grid's rows run along z and its columns along x, for groups that face along x. */
    private static int summonGridFacingX(Minecraft mc, String owner, String[] units, Vec3 centre, int yaw) {
        int[] cols = { -2, -1, 1, 2 };
        int sent = 0;
        for (int i = 0; i < units.length; i++) {
            int col = i % 4, row = i / 4;
            int z = (int) Math.floor(centre.z) + cols[col];
            int x = (int) Math.floor(centre.x) - 2 + row * 2;
            String cmd = String.format("rtsapi-summon \"%s\" %s:%s %d %d %d {Rotation:[%df,0f]}", owner.replace("\"", ""),
                    ReignOfNether.MOD_ID, units[i], x, (int) centre.y, z, yaw);
            if (runCommand(mc, cmd))
                sent++;
        }
        return sent;
    }

    // ---- helpers for the staged scenes ----

    private static void giveUp(String scene, String why) {
        ReignOfNether.LOGGER.warn("[Eyes] {}: gave up after {} s ({})", scene, ticks / 20, why);
        next();
    }

    /** Zoom target in blocks of view height; below the player's floor (10) only for the close-ups, restored by any other. */
    static void setZoom(float zoom) {
        OrthoviewClientEvents.setScriptedZoom(zoom);
    }

    // 07b-s: where the subject's middle sits on screen (NDC, y up) - left of the cursor parked at the screen's centre (its
    // hover card) and inside the free band between the top bar and the selection panel / minimap (measured on the
    // 1600x900 stills: HUD panels cover y > 590 at the bottom left and the minimap the bottom right)
    private static final double CLOSE_NDC_X = -0.25, CLOSE_NDC_Y = 0.30;

    /**
     * Moves the camera so that pos is drawn at (ndcX, ndcY) for the CURRENT yaw, tilt and zoom - a screen-space offset,
     * right at any yaw. The old fixed world offset (+2.5, 0, +2.5) only meant "to the right" at the default yaw.
     */
    private static void placeOnScreen(Minecraft mc, Vec3 pos, double ndcX, double ndcY) {
        double yaw = Math.toRadians(OrthoviewClientEvents.getCamRotX());
        double pitch = Math.toRadians(OrthoviewClientEvents.getCamRotY());
        double viewH = OrthoviewClientEvents.getZoom();
        double aspect = (double) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        double right = ndcX * viewH * aspect / 2;                              // blocks along screen-right
        double away = ndcY * viewH / 2 / Math.max(0.2, Math.sin(pitch));       // ground blocks per screen-up (ortho)
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);                       // screen-right on the ground
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);                        // the view's heading on the ground
        OrthoviewClientEvents.centreCameraOnPosInstant(
                new Vec3(pos.x - rx * right - fx * away, pos.y, pos.z - rz * right - fz * away));
    }

    // the frame's free band for a close-up subject (NDC, y up): below the top bar (y < 120 of 900 px) and above the
    // selection panel / minimap (y > 590); and the smallest share of the frame's height it may fill - a close-up at
    // zoom 5-7 fills ~20-30%, the normal RTS zoom (30) ~5%
    private static final double FREE_NDC_TOP = 0.73, FREE_NDC_BOTTOM = -0.31, MIN_FILL = 0.12;

    /**
     * "Never twice" guard (improvement-loop.md): the 4ff5cb5 close-ups were shot at normal zoom with the subject under
     * the HUD panel, and nothing noticed until a human looked. Measures where the subject really is in the frame being
     * photographed - from the camera that drew it, not from placeOnScreen's own arithmetic - and WARNs when it is small,
     * off the frame or under the HUD.
     */
    private static void checkFraming(Minecraft mc, Entity e, String still) {
        double[] f = new double[4];
        String fault = framingFault(mc, e, f);
        String line = String.format("%s framing: middle at (%.2f, %.2f), fills %.0f%% of the frame height, zoom %.1f%s",
                still, f[0], f[1], 100 * f[2], f[3], waitedTicks > 0 ? " (after waiting " + waitedTicks + " ticks)" : "");
        if (fault == null)
            ReignOfNether.LOGGER.info("[Eyes] {}", line);
        else
            ReignOfNether.LOGGER.warn("[Eyes] {} - {} (framing check)", line, fault);
    }

    private static String framingFault(Minecraft mc, Entity e) {
        return framingFault(mc, e, new double[4]);
    }

    /** null when the subject is well framed, else why not; out = { middle x, middle y (NDC), fill, zoom }. */
    private static String framingFault(Minecraft mc, Entity e, double[] out) {
        var cam = mc.gameRenderer.getMainCamera();
        double viewH = OrthoviewClientEvents.getZoom();
        double viewW = viewH * mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        double[] mid = ndc(cam, closeUpMiddle(e), viewW, viewH);
        double[] foot = ndc(cam, e.position(), viewW, viewH);
        double[] head = ndc(cam, e.position().add(0, e.getBbHeight(), 0), viewW, viewH);
        double fill = Math.abs(head[1] - foot[1]) / 2;
        out[0] = mid[0];
        out[1] = mid[1];
        out[2] = fill;
        out[3] = viewH;
        return Math.abs(mid[0]) > 0.95 || Math.abs(mid[1]) > 0.95 ? "off the frame"
                : mid[1] > FREE_NDC_TOP || mid[1] < FREE_NDC_BOTTOM ? "under the HUD"
                : fill < MIN_FILL ? "too small for a close-up" : null;
    }

    /** A world point's place on an orthographic frame, NDC (x right, y up), from the camera's own axes. */
    private static double[] ndc(net.minecraft.client.Camera cam, Vec3 p, double viewW, double viewH) {
        Vec3 c = cam.getPosition();
        double dx = p.x - c.x, dy = p.y - c.y, dz = p.z - c.z;
        var left = cam.getLeftVector();
        var up = cam.getUpVector();
        double x = -(dx * left.x() + dy * left.y() + dz * left.z());
        double y = dx * up.x() + dy * up.y() + dz * up.z();
        return new double[] { x / (viewW / 2), y / (viewH / 2) };
    }

    private static Vec3 closeUpMiddle(Entity e) {
        return e.position().add(0, e.getBbHeight() / 2, 0);
    }

    static BlockPos findCapitol(Minecraft mc) {
        String me = mc.player.getName().getString();
        for (BuildingPlacement b : BuildingClientEvents.getBuildings())
            if (b.isCapitol && me.equals(b.ownerName))
                return b.centrePos;
        ReignOfNether.LOGGER.warn("[Eyes] no owned capitol in the client's building list; using the camera's column");
        return mc.player.blockPosition();
    }

    static String findEnemyName(Minecraft mc) {
        String me = mc.player.getName().getString();
        for (RTSPlayer p : new ArrayList<>(PlayerClientEvents.rtsPlayers))
            if (p.name != null && !p.name.equals(me))
                return p.name;
        ReignOfNether.LOGGER.warn("[Eyes] no enemy in the client's roster; assuming the skirmish bot's default name");
        return "Bot1 (medium)";   // BotServerEvents.addBot: "Bot" + number + " (" + difficulty + ")"
    }

    /** The top of the ground at x, z from the client's heightmap, or fallbackY if that chunk is not here yet. */
    /** No water on the surface of the 7 x 7 columns round (x, z): a grid of twelve plus its walk-in fits there dry. */
    private static boolean dryAround(Minecraft mc, int x, int z) {
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                int y = surfaceY(mc, x + dx, z + dz);
                if (y != Integer.MIN_VALUE && !mc.level.getFluidState(new BlockPos(x + dx, y - 1, z + dz)).isEmpty())
                    return false;
            }
        return true;
    }

    /**
     * The first free y above the ground at x, z on the CLIENT, or Integer.MIN_VALUE when the column isn't loaded. The
     * client is only sent the MOTION_BLOCKING and WORLD_SURFACE heightmaps: MOTION_BLOCKING_NO_LEAVES read back the
     * build floor everywhere, so every pad and army of the tour stood on the fallback - the capitol's centre height,
     * ~4 blocks up (Oct 15 log: armies at y 72 over ground at 68; 12's water spilled out of a raised pad; and
     * dryAround never saw water). MOTION_BLOCKING stops on leaves, so step down through a canopy.
     */
    static int surfaceY(Minecraft mc, int x, int z) {
        int y = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int floor = mc.level.getMinBuildHeight() + 1;
        if (y <= floor)
            return Integer.MIN_VALUE;
        while (y > floor) {   // down through a canopy: leaves, trunk, air, and undergrowth / vines / snow layers
            var below = mc.level.getBlockState(new BlockPos(x, y - 1, z));
            boolean passable = below.isAir() || below.is(net.minecraft.tags.BlockTags.LEAVES)
                    || below.is(net.minecraft.tags.BlockTags.LOGS)
                    || (!below.blocksMotion() && below.getFluidState().isEmpty());
            if (!passable)
                break;
            y--;
        }
        return y;
    }

    static Vec3 ground(Minecraft mc, int x, int z, int fallbackY) {
        int y = surfaceY(mc, x, z);
        if (y == Integer.MIN_VALUE) {
            ReignOfNether.LOGGER.warn("[Eyes] no client height at {} {}; using {}", x, z, fallbackY);
            y = fallbackY;
        }
        return new Vec3(x + 0.5, y, z + 0.5);
    }

    /**
     * Summons the units in a 4 x 3 grid round the centre, one op command each, facing yaw (0 = south, 180 = north);
     * returns the count sent. The command's pos is a whole block, so "1.5-block spacing" is columns at -2, -1, +1, +2
     * and rows 2 apart. The first flight's 2-block grid ended up on one block; the 07 line now logs the spread.
     */
    private static int summonGrid(Minecraft mc, String owner, String[] units, Vec3 centre, int yaw) {
        int[] cols = { -2, -1, 1, 2 };
        int sent = 0;
        for (int i = 0; i < units.length; i++) {
            int col = i % 4, row = i / 4;
            int x = (int) Math.floor(centre.x) + cols[col];
            int z = (int) Math.floor(centre.z) - 2 + row * 2;
            Vec3 at = ground(mc, x, z, (int) centre.y);
            // StringArgumentType.string(): the owner is quoted, since a bot's name has spaces ("Bot1 (medium)")
            String cmd = String.format("rtsapi-summon \"%s\" %s:%s %d %d %d {Rotation:[%df,0f]}", owner.replace("\"", ""),
                    ReignOfNether.MOD_ID, units[i], x, (int) at.y, z, yaw);
            if (runCommand(mc, cmd))
                sent++;
        }
        return sent;
    }

    /** "type x n, ..." for a group of units, so the log says what was summoned (fighters, not livestock). */
    private static String roster(List<LivingEntity> units) {
        java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
        for (LivingEntity le : units)
            counts.merge(le.getType().toShortString(), 1, Integer::sum);
        return counts.isEmpty() ? "nobody" : counts.toString();
    }

    /** The x and z extent of a group ("7 x 5 blocks"): one block means they are standing on one spot. */
    private static String spread(List<LivingEntity> units) {
        if (units.isEmpty())
            return "-";
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (LivingEntity le : units) {
            minX = Math.min(minX, le.getX());
            maxX = Math.max(maxX, le.getX());
            minZ = Math.min(minZ, le.getZ());
            maxZ = Math.max(maxZ, le.getZ());
        }
        return String.format("%.1f x %.1f blocks", maxX - minX, maxZ - minZ);
    }

    static Vec3 centroid(List<LivingEntity> units) {
        if (units.isEmpty())
            return null;
        double x = 0, y = 0, z = 0;
        for (LivingEntity le : units) {
            x += le.getX();
            y += le.getY();
            z += le.getZ();
        }
        return new Vec3(x / units.size(), y / units.size(), z / units.size());
    }

    /** Sends a command as the client player types it (without the slash): the server runs it with the player's op. */
    static boolean runCommand(Minecraft mc, String cmd) {
        try {
            // the server echoes every /summon and /fill as a chat line ("Summoned ... for Bot1 at ..."), and those
            // lines were still over the river in run 38524157524 although the chat panel is cleared before each
            // still: feedback arrives from the server after the clear. Switch it off before the director's first
            // command instead - a player's screen never shows this chat
            if (!feedbackOff) {
                feedbackOff = true;
                mc.player.connection.sendCommand("gamerule sendCommandFeedback false");
            }
            mc.player.connection.sendCommand(cmd);
            return true;
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("[Eyes] command failed: /{}", cmd, e);
            return false;
        }
    }

    /** Living units within range of a point: the player's own fighters (no workers) when owned, else hostile ones. */
    static List<LivingEntity> unitsNear(Minecraft mc, Vec3 centre, double range, boolean owned) {
        List<LivingEntity> out = new ArrayList<>();
        if (centre == null)
            return out;
        for (LivingEntity le : new ArrayList<>(UnitClientEvents.getAllUnits())) {
            if (!(le instanceof Unit) || !le.isAlive() || le.isPassenger())
                continue;
            if (le.position().distanceToSqr(centre) > range * range)
                continue;
            Relationship rel = UnitClientEvents.getPlayerToEntityRelationship(le);
            if (owned && (rel != Relationship.OWNED || le instanceof WorkerUnit))
                continue;
            if (!owned && rel != Relationship.HOSTILE)
                continue;
            out.add(le);
        }
        return out;
    }

    private static int countOwned(Minecraft mc, String owner, boolean fightersOnly) {
        int n = 0;
        for (LivingEntity le : new ArrayList<>(UnitClientEvents.getAllUnits()))
            if (le instanceof Unit u && le.isAlive() && owner.equals(u.getOwnerName()) && !(fightersOnly && le instanceof WorkerUnit))
                n++;
        return n;
    }

    /** Projectiles in flight: an arrow stuck in the ground stays a Projectile for a minute and read as "contact". */
    private static int projectiles(Minecraft mc) {
        int n = 0;
        for (Entity e : mc.level.entitiesForRendering())
            if (e instanceof Projectile && e.isAlive() && (e.getX() != e.xo || e.getY() != e.yo || e.getZ() != e.zo))
                n++;
        return n;
    }

    /** The midpoint of the two armies' live positions (where the fight is), or the spawn midpoint. */
    private static Vec3 fightCentre(Minecraft mc) {
        Vec3 mid = armyCentre.add(enemyCentre).scale(0.5);
        List<LivingEntity> all = new ArrayList<>(unitsNear(mc, mid, 60, true));
        all.addAll(unitsNear(mc, mid, 60, false));
        Vec3 c = centroid(all);
        return c == null ? mid : c;
    }

    /** One line of what the fight looks like right now: units alive per side, projectiles in the air, selection. */
    private static String battleLine(Minecraft mc) {
        String me = mc.player.getName().getString();
        return String.format("my fighters %d (all my units %d), enemy '%s' units %d, projectiles in the air %d, %d selected",
                countOwned(mc, me, true), countOwned(mc, me, false), enemyName,
                enemyName == null ? 0 : countOwned(mc, enemyName, false), projectiles(mc),
                UnitClientEvents.getSelectedUnits().size());
    }

    static String fmt(Vec3 v) {
        return v == null ? "?" : String.format("[%d, %d, %d]", (int) Math.floor(v.x), (int) Math.floor(v.y), (int) Math.floor(v.z));
    }

    // ---- synthetic mouse: the Forge screen events a real press / drag / release produce, in GUI coordinates ----

    /** A left press at x, y; false when a Pre listener refused it (a panel or the HUD under the point). */
    private static boolean mousePress(Minecraft mc, double x, double y) {
        if (!(mc.screen instanceof TopdownGui screen))
            return false;
        // the HUD hit-test reads the last rendered mouse position: point it at the press
        HudClientEvents.mouseX = (int) x;
        HudClientEvents.mouseY = (int) y;
        if (MinecraftForge.EVENT_BUS.post(new ScreenEvent.MouseButtonPressed.Pre(screen, x, y, GLFW.GLFW_MOUSE_BUTTON_1)))
            return false;
        if (HudClientEvents.isMouseOverAnyButtonOrHud())
            return false;   // the Post listeners would ignore it (CursorClientEvents.onMouseClick)
        MinecraftForge.EVENT_BUS.post(new ScreenEvent.MouseButtonPressed.Post(screen, x, y, GLFW.GLFW_MOUSE_BUTTON_1, false));
        return true;
    }

    private static void mouseDrag(Minecraft mc, double x, double y) {
        if (!(mc.screen instanceof TopdownGui screen))
            return;
        if (!MinecraftForge.EVENT_BUS.post(new ScreenEvent.MouseDragged.Pre(screen, x, y, GLFW.GLFW_MOUSE_BUTTON_1, 0, 0)))
            MinecraftForge.EVENT_BUS.post(new ScreenEvent.MouseDragged.Post(screen, x, y, GLFW.GLFW_MOUSE_BUTTON_1, 0, 0));
    }

    private static void mouseRelease(Minecraft mc, double x, double y) {
        if (!(mc.screen instanceof TopdownGui screen))
            return;
        if (!MinecraftForge.EVENT_BUS.post(new ScreenEvent.MouseButtonReleased.Pre(screen, x, y, GLFW.GLFW_MOUSE_BUTTON_1)))
            MinecraftForge.EVENT_BUS.post(new ScreenEvent.MouseButtonReleased.Post(screen, x, y, GLFW.GLFW_MOUSE_BUTTON_1, false));
    }

    /**
     * Logs which blocks form the visible ground within 24 blocks of the camera's column (the start base), counted by
     * type, so a change on screen (eyes, Oct 13: grey tiles appearing round the capitol in the first minute) can be
     * named from the log instead of guessed. Runs on the integrated server's thread.
     */
    private static void groundCensus(Minecraft mc, String when) {
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null)
            return;
        int cx = mc.player.getBlockX(), cz = mc.player.getBlockZ();
        server.execute(() -> {
            var level = server.overworld();
            java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
            for (int x = cx - 24; x <= cx + 24; x++)
                for (int z = cz - 24; z <= cz + 24; z++) {
                    var top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            new net.minecraft.core.BlockPos(x, 0, z)).below();
                    String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(top).getBlock()).toString();
                    counts.merge(id, 1, Integer::sum);
                }
            ReignOfNether.LOGGER.info("[Eyes] ground {} round [{}, {}]: {}", when, cx, cz, counts);
        });
    }

    private static void next() {
        step++;
        ticks = 0;
        shotAt = -1;
    }

    static void shot(Minecraft mc, String name) {
        // the eyes video (tools/eyes_record.sh + eyes_cut.py) cuts a short clip around every still from this mark
        ReignOfNether.LOGGER.info("[Eyes] clip-mark {} {}", name, System.currentTimeMillis());
        // the director's own /summon and /fill feedback lines were sitting over the fight in every still and clip
        // (run 38511368547); a player's screen has no such chat
        if (mc.gui != null)
            mc.gui.getChat().clearMessages(false);
        try {
            Screenshot.grab(mc.gameDirectory, "bat_eyes_" + name + ".png", mc.getMainRenderTarget(),
                    msg -> ReignOfNether.LOGGER.info("[Eyes] {}: {}", name, msg.getString()));
        } catch (Exception e) {
            ReignOfNether.LOGGER.error("[Eyes] screenshot {} failed", name, e);
        }
    }
}
