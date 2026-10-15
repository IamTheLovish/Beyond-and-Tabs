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
 *     (counted from the 02 shot, labelled 0:30) passes 2:00.</li>
 * <li>12_river_crossing (Oct 14, terrain_intelligence.md): a pad with a 4-wide, 1-deep water strip is laid with the
 *     op fill command 30 blocks west of the first fight, a dozen of the player's units are summoned on its west bank
 *     and eight enemies on its east bank, and the dozen is attack-moved across; photographed at contact and six
 *     seconds later. The log line counts who stands in the water and on which bank - the army should cross in a
 *     line and fight on the far bank, not in the river.</li>
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
    private static BlockPos capitolCentre = null;
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
    private static int closeUpAt = -1;            // 07b-d: tick of the paladin close-up's next step
    private static int closeUpPhase = 0;          // 07b still, 07c walking, 07d fighting
    private static int paladinId = -1;
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
                    SkirmishSetupScreen.Settings s = new SkirmishSetupScreen.Settings();
                    s.faction = 0;
                    s.team = 0;
                    s.arena = 0;
                    s.metal = 1;
                    s.map = 0;
                    s.choosePositions = false;
                    // Settings already holds the default 1v1 bot; adding another made it a 1v2 ("Enemies 2")
                    if (s.bots.isEmpty())
                        s.bots.add(new SkirmishSetupScreen.Bot(1));
                    s.bots.get(0).faction = 1;
                    QuickBattle.launch(mc.screen, s);
                    next();
                }
            }
            case 1 -> {   // in the match and in the RTS view, chunks around us loaded
                if (mc.level != null && mc.player != null && OrthoviewClientEvents.isEnabled() && ticks > 20 * 30) {
                    shot(mc, "02_rts_default");
                    matchZeroTotalTick = totalTicks - 20 * 30;
                    groundCensus(mc, "0:30");
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
            case 14 -> {
                if (ticks > 20 * 2) {
                    ReignOfNether.LOGGER.info("[Eyes] tour complete");
                    mc.stop();
                    next();
                }
            }
            default -> { }
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
            // 07b: a close-up of the Paladin - the concept model's own body (RadiantPaladinRenderer, generated from
            // design/models by tools/dsl_to_game.py), zoomed in far enough that its detail parts are drawn
            LivingEntity paladin = null;
            for (LivingEntity le : mine)
                if (le instanceof com.solegendary.reignofnether.unit.units.villagers.IronGolemUnit) {
                    paladin = le;
                    break;
                }
            if (paladin == null) {
                ReignOfNether.LOGGER.warn("[Eyes] 07b_paladin_close: no paladin in the army");
                next();
                return;
            }
            OrthoviewClientEvents.zoomCam(-1000);   // all the way in (ZOOM_MIN): setZoom(12) only got half way
            OrthoviewClientEvents.centreCameraOnPosInstant(paladin.position());
            ReignOfNether.LOGGER.info("[Eyes] 07b_paladin_close: zooming all the way in on {} at {}",
                    paladin.getName().getString(), fmt(paladin.position()));
            paladinId = paladin.getId();
            // selected, so the still also shows its HUD portrait (PortraitRendererModifiers frames it as the golem)
            UnitClientEvents.clearSelectedUnits();
            UnitClientEvents.addSelectedUnit(paladin);
            closeUpPhase = 0;
            closeUpAt = ticks + 100;  // the zoom eases in over a few seconds (the first flight shot at 2 s: half way)
            return;
        }
        if (closeUpAt > 0) {
            // follow the paladin while it walks and fights, so the clips around 07c / 07d keep it in frame
            var pal = mc.level.getEntity(paladinId);
            // a little to one side of it: the cursor rests at the screen's centre and its hover card covered the unit
            if (pal != null && closeUpPhase > 0)
                OrthoviewClientEvents.centreCameraOnPosInstant(pal.position().add(2.5, 0, 2.5));
            if (ticks != closeUpAt)
                return;
            if (pal == null || closeUpPhase >= 2) {
                if (pal != null)
                    shot(mc, "07d_paladin_attack");
                closeUpAt = -1;
                UnitClientEvents.clearSelectedUnits();
                setZoom(ZOOM_NORMAL);
                OrthoviewClientEvents.centreCameraOnPosInstant(armyCentre);
                next();
                return;
            }
            if (closeUpPhase == 0) {
                // the still, then a walk across the frame (the stride, the cape, the tabards)
                shot(mc, "07b_paladin_close");
                UnitClientEvents.clearSelectedUnits();   // the portrait is in 07b; the rings would hide the walk
                BlockPos to = BlockPos.containing(pal.getX() + 7, pal.getY(), pal.getZ());
                UnitClientEvents.sendUnitCommandManual(UnitAction.MOVE, -1, new int[] { paladinId }, to);
                ReignOfNether.LOGGER.info("[Eyes] 07c_paladin_walk: moving the paladin to {}", to.toShortString());
                closeUpAt = ticks + 40;
            } else {
                // mid-stride, then two enemy ghouls at its feet: the chop
                shot(mc, "07c_paladin_walk");
                if (enemyName != null)
                    for (int k = 0; k < 2; k++)
                        runCommand(mc, String.format("rtsapi-summon \"%s\" %s:zombie_unit %d %d %d", enemyName.replace("\"", ""),
                                ReignOfNether.MOD_ID, (int) Math.floor(pal.getX()) + 2, (int) Math.floor(pal.getY()),
                                (int) Math.floor(pal.getZ()) - 1 + 2 * k));
                ReignOfNether.LOGGER.info("[Eyes] 07d_paladin_attack: two ghouls of '{}' at the paladin's feet", enemyName);
                closeUpAt = ticks + 100;   // several swings (the golem's attack cooldown) inside 07d's clip
            }
            closeUpPhase++;
        }
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

    /** targetZoom is private: all the way in (ZOOM_MIN = 10) then out to the wanted value, as case 3 does. */
    private static void setZoom(int zoom) {
        OrthoviewClientEvents.zoomCam(-1000);
        OrthoviewClientEvents.zoomCam(zoom - 10);
    }

    private static BlockPos findCapitol(Minecraft mc) {
        String me = mc.player.getName().getString();
        for (BuildingPlacement b : BuildingClientEvents.getBuildings())
            if (b.isCapitol && me.equals(b.ownerName))
                return b.centrePos;
        ReignOfNether.LOGGER.warn("[Eyes] no owned capitol in the client's building list; using the camera's column");
        return mc.player.blockPosition();
    }

    private static String findEnemyName(Minecraft mc) {
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
                int y = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz) - 1;
                if (!mc.level.getFluidState(new BlockPos(x + dx, y, z + dz)).isEmpty())
                    return false;
            }
        return true;
    }

    private static Vec3 ground(Minecraft mc, int x, int z, int fallbackY) {
        int y = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (y <= mc.level.getMinBuildHeight() + 1)
            y = fallbackY;
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

    private static Vec3 centroid(List<LivingEntity> units) {
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
    private static boolean runCommand(Minecraft mc, String cmd) {
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
    private static List<LivingEntity> unitsNear(Minecraft mc, Vec3 centre, double range, boolean owned) {
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

    private static int projectiles(Minecraft mc) {
        int n = 0;
        for (Entity e : mc.level.entitiesForRendering())
            if (e instanceof Projectile)
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

    private static String fmt(Vec3 v) {
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

    private static void shot(Minecraft mc, String name) {
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
