package com.solegendary.reignofnether.orthoview;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.building.buildings.monsters.Dungeon;
import com.solegendary.reignofnether.building.buildings.monsters.Stronghold;
import com.solegendary.reignofnether.building.buildings.neutral.CapturableBeacon;
import com.solegendary.reignofnether.building.buildings.piglins.FlameSanctuary;
import com.solegendary.reignofnether.building.buildings.piglins.Fortress;
import com.solegendary.reignofnether.building.buildings.shared.AbstractStockpile;
import com.solegendary.reignofnether.building.buildings.shared.EnergyConverter;
import com.solegendary.reignofnether.building.buildings.shared.MetalExtractor;
import com.solegendary.reignofnether.building.buildings.shared.StorageVault;
import com.solegendary.reignofnether.building.buildings.verdant.CircleOfElders;
import com.solegendary.reignofnether.building.buildings.verdant.HeartOfTheWild;
import com.solegendary.reignofnether.building.buildings.verdant.StormOak;
import com.solegendary.reignofnether.building.buildings.shared.WindGenerator;
import com.solegendary.reignofnether.building.buildings.villagers.ArcaneTower;
import com.solegendary.reignofnether.building.buildings.villagers.Castle;
import com.solegendary.reignofnether.building.production.IUnitProductionItem;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.startpos.CapturePointsClient;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.buildings.shared.AbstractBridge;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.hud.HudPanels;
import com.solegendary.reignofnether.player.CommanderServerEvents;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.UnitIconTier;
import com.solegendary.reignofnether.unit.interfaces.*;
import com.solegendary.reignofnether.util.MyRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * BAR-style strategic zoom (Beyond and Tabs).
 *
 * Past STRATEGIC_ZOOM_ENTER every visible unit is drawn as a team-coloured icon whose shape shows its role
 * (circle = worker/builder, square = melee, triangle = ranged, diamond = hero, star = commander) and buildings as
 * team-coloured footprint plates. Unit models (and with them their per-unit health bars, which are drawn from
 * RenderLivingEvent.Post) are skipped, which is what makes huge battles cheap to look at.
 *
 * As in BAR (Spring icontypes) the icon's size follows the unit's tier (UnitIconTier: workers 0.9x, T1 1x,
 * T2 1.25x, T3 and heroes 1.6x, then the player's Icon size setting), and big units - T3, heroes, the commander,
 * anything 1.5+ blocks wide - keep their model until BIG_UNIT_ZOOM_MULT times further out, so a Colossus doesn't
 * pop into a dot at the same zoom as the chaff round it. Every icon sits on a 1 px dark outline so it stays
 * readable on snow and bright grass; selected units carry a white ring outside that, preselected a grey one.
 *
 * Each plate also carries a small role glyph (factory, T2/T3 lab, mex with its tier, wind, converter, defence,
 * capitol, capture site) so a base doesn't read as a sheet of identical tiles; see drawGlyph.
 *
 * Also draws, at any zoom: an attack range ring for the HUD card unit and the hovered unit (never one per unit of
 * a group), and team-coloured selection rings under selected units (normal zoom only; the icons replace them in
 * strategic zoom).
 */
public class StrategicViewClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();

    // icons must take over BEFORE vanilla entity culling starts hiding far mobs, or there's a dead zoom window
    // where units are invisible but icons haven't kicked in yet (the "units going invisible" bug)
    public static final float STRATEGIC_ZOOM_ENTER = 70f;
    public static final float STRATEGIC_ZOOM_EXIT = 62f; // hysteresis so it doesn't flicker at the threshold
    /**
     * Big units (UnitIconTier.isBig) switch to icons this much further out than the rest: 70 -> 94.5 at the
     * default threshold, with the same 8-zoom hysteresis. Safe because EntityMixin.shouldRenderAtSqrDistance
     * disables vanilla's distance cull in orthoview (and a 1.5-wide unit's own cull would be 96+ blocks anyway),
     * so there is no window where a big unit is neither model nor icon; and the big switch never arms before the
     * small one (see updateState), so the two never cross.
     */
    public static final float BIG_UNIT_ZOOM_MULT = 1.35f;
    private static final int RING_SEGMENTS = 48;

    public static boolean rangeRingsEnabled = true;
    public static boolean selectionPlatesEnabled = true;

    private static boolean strategic = false;
    private static boolean strategicBig = false;   // the big units' own, later switch; implies strategic

    public static boolean isStrategicView() {
        return strategic;
    }

    /** In strategic view, a unit that still draws as a model this frame (big and not yet past the big switch). */
    public static boolean keepsModel(LivingEntity entity) {
        return strategic && !strategicBig && UnitIconTier.isBig(entity);
    }

    private static void updateState() {
        if (!OrthoviewClientEvents.isEnabled() || MC.level == null) {
            strategic = false;
            strategicBig = false;
            return;
        }
        // Mod Settings -> Display: always / never icons, or the player's own zoom threshold (at most the default
        // STRATEGIC_ZOOM_ENTER, which must stay below vanilla's far-mob culling)
        int mode = com.solegendary.reignofnether.config.DisplaySettings.iconMode();
        if (mode != com.solegendary.reignofnether.config.DisplaySettings.ICONS_BY_ZOOM) {
            strategic = mode == com.solegendary.reignofnether.config.DisplaySettings.ICONS_ALWAYS;
            strategicBig = strategic;
            return;
        }
        float enter = Math.min(STRATEGIC_ZOOM_ENTER, com.solegendary.reignofnether.config.DisplaySettings.iconZoomThreshold());
        // a crowd's models are what the frame pays for (lovish's Oct 23 log: 120 fps at <60 units, 55-75 at 450-575):
        // the switch comes in as the army grows, never past close zoom (UnitLodRules.iconEnterZoom); the A/B toggle
        // unit_render_lod turns it off. Hysteresis and the big-unit switch follow the scaled value.
        if (com.solegendary.reignofnether.unit.modelling.renderers.UnitLod.enabled())
            enter = com.solegendary.reignofnether.unit.modelling.renderers.UnitLodRules.iconEnterZoom(enter, UnitClientEvents.getAllUnits().size());
        float hysteresis = STRATEGIC_ZOOM_ENTER - STRATEGIC_ZOOM_EXIT;
        float exit = enter - hysteresis;
        float zoom = OrthoviewClientEvents.getZoom();
        if (strategic && zoom < exit)
            strategic = false;
        else if (!strategic && zoom > enter)
            strategic = true;
        // the big units' switch sits BIG_UNIT_ZOOM_MULT further out with the same hysteresis band, and can only
        // be armed while the small one is: leaving strategic view always brings every model back together
        float bigEnter = enter * BIG_UNIT_ZOOM_MULT;
        float bigExit = bigEnter - hysteresis;
        if (!strategic)
            strategicBig = false;
        else if (strategicBig && zoom < bigExit)
            strategicBig = false;
        else if (!strategicBig && zoom > bigEnter)
            strategicBig = true;
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent evt) {
        if (evt.phase != TickEvent.Phase.START)
            return;
        updateState();
        if (strategic)
            syncHudSelectedEntity();
    }

    // HudClientEvents picks hudSelectedEntity inside RenderLivingEvent.Post, which no longer fires for units whose
    // model we skip, so mirror that logic here every frame while in strategic view.
    private static void syncHudSelectedEntity() {
        LivingEntity hudEntity = HudClientEvents.hudSelectedEntity;
        if (hudEntity != null && hudEntity.isRemoved())
            HudClientEvents.setHudSelectedEntity(null);

        ArrayList<LivingEntity> units = UnitClientEvents.getSortedSelectedUnits();
        if (units.isEmpty()) {
            HudClientEvents.setHudSelectedEntity(null);
        } else if (HudClientEvents.hudSelectedEntity == null || units.size() == 1 ||
                !units.contains(HudClientEvents.hudSelectedEntity)) {
            HudClientEvents.setHudSelectedEntity(units.get(0));
        }
        if (HudClientEvents.hudSelectedEntity == null) {
            HudClientEvents.portraitRendererUnit.model = null;
            HudClientEvents.portraitRendererUnit.renderer = null;
        }
    }

    // skip full unit models in strategic view (the HUD-selected unit still renders so its portrait keeps working,
    // and big units keep theirs until the later big switch - keepsModel)
    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Pre<? extends LivingEntity, ? extends EntityModel<?>> evt) {
        if (!strategic)
            return;
        if (skipsModel(evt.getEntity()))
            evt.setCanceled(true);
    }

    /** This entity's model is not drawn this frame (UnitLodRules.skipUnitModel): icon view, not the portrait's unit, not a big one yet. */
    public static boolean skipsModel(net.minecraft.world.entity.Entity entity) {
        if (!strategic || !(entity instanceof Unit) || !(entity instanceof LivingEntity living))
            return false;
        return com.solegendary.reignofnether.unit.modelling.renderers.UnitLodRules.skipUnitModel(
            true, strategicBig, true, UnitIconTier.isBig(living), living == HudClientEvents.hudSelectedEntity);
    }

    // ------------------------------------------------------------------------------------------------------------
    // strategic icon overlay (drawn as part of the in-game GUI, so it sits under the RTS HUD)
    // ------------------------------------------------------------------------------------------------------------

    private static Camera cam;
    private static Vec3 camPos;
    private static Vector3f camLeft;
    private static Vector3f camUp;
    private static float guiW;
    private static float guiH;
    private static float pixelsPerBlock;
    private static float iconScale = 1f;   // Mod Settings -> Display -> Icon size
    private static final int IDLE_AMBER = 0xFFFFB428;
    private static int idlePulse = 0xFF;   // alpha of the idle marker this frame, 0xA0-0xFF at 1 Hz

    private static float projX(double x, double y, double z) {
        double rx = x - camPos.x, ry = y - camPos.y, rz = z - camPos.z;
        double right = -(rx * camLeft.x() + ry * camLeft.y() + rz * camLeft.z());
        return (float) (guiW / 2 + right * pixelsPerBlock);
    }

    private static float projY(double x, double y, double z) {
        double rx = x - camPos.x, ry = y - camPos.y, rz = z - camPos.z;
        double up = rx * camUp.x() + ry * camUp.y() + rz * camUp.z();
        return (float) (guiH / 2 - up * pixelsPerBlock);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre evt) {
        if (!strategic || MC.level == null || MC.player == null || !OrthoviewClientEvents.isEnabled())
            return;
        int sys = com.solegendary.reignofnether.perf.FrameParts.begin(com.solegendary.reignofnether.perf.FrameParts.STRATEGIC_ICONS);
        try {
            drawStrategicOverlay(evt.getGuiGraphics(), evt.getPartialTick());
        } catch (Exception e) {
            // never let an overlay bug take down the frame
            e.printStackTrace();
        } finally {
            com.solegendary.reignofnether.perf.FrameParts.end(sys);
        }
    }

    /** Commanders are tagged serverside, but tags don't sync - their translatable custom name does. */
    private static boolean isCommander(LivingEntity entity) {
        return CommanderServerEvents.looksLikeCommander(entity);
    }

    private static int teamColour(LivingEntity entity) {
        if (entity instanceof Unit unit && PlayerClientEvents.isRTSPlayer(unit.getOwnerName()))
            return PlayerColors.getPlayerDisplayColorHex(unit.getOwnerName()) & 0xFFFFFF;
        return PlayerColors.COLOR_GRAY.hexCode & 0xFFFFFF;
    }

    /** {@link #teamColour} through this frame's owner cache: the icon overlay only (it clears the cache each frame). */
    private static int iconColour(LivingEntity entity) {
        return entity instanceof Unit unit ? COLOURS.unit(unit.getOwnerName()) : PlayerColors.COLOR_GRAY.hexCode & 0xFFFFFF;
    }

    // per-frame lookups the overlay used to repeat per icon and per plate (perf plan R2): owner colours (cleared each
    // frame, so a colour or team change shows on the next one), the selected buildings as a set, and the plate opacity
    // setting; plus the plate's projected corners, filled in place for each building instead of two new arrays
    private static final com.solegendary.reignofnether.player.OwnerColours COLOURS = new com.solegendary.reignofnether.player.OwnerColours();
    private static final Set<BuildingPlacement> SELECTED_BUILDINGS = new it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<>();
    private static final float[] PLATE_XS = new float[4], PLATE_YS = new float[4];
    private static int plateOpacityPct = 100;

    private static void drawStrategicOverlay(GuiGraphics gg, float partialTick) {
        cam = MC.gameRenderer.getMainCamera();
        camPos = cam.getPosition();
        camLeft = cam.getLeftVector();
        camUp = cam.getUpVector();
        guiW = MC.getWindow().getGuiScaledWidth();
        guiH = MC.getWindow().getGuiScaledHeight();
        pixelsPerBlock = guiH / OrthoviewClientEvents.getZoom();
        iconScale = com.solegendary.reignofnether.config.DisplaySettings.iconScale();   // once per frame
        plateOpacityPct = plateOpacitySetting();                                        // once per frame
        COLOURS.clear();
        idlePulse = 0xA0 + (int) (0x5F * (0.5 + 0.5 * Math.sin((System.currentTimeMillis() % 1000L) / 1000.0 * Math.PI * 2)));

        gg.flush();
        Matrix4f mat = gg.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        // ---- buildings: team-coloured footprint plates ----
        Set<BuildingPlacement> selectedBuildings = SELECTED_BUILDINGS;   // identity, as List.contains was
        selectedBuildings.clear();
        selectedBuildings.addAll(BuildingClientEvents.getSelectedBuildings());
        // the hovered building keeps its feedback here too: the in-world wire boxes are off in strategic view now
        BuildingPlacement hovered = com.solegendary.reignofnether.hud.HudClientEvents.isMouseOverAnyButtonOrHud()
            ? null : BuildingClientEvents.getPreselectedBuilding();
        for (BuildingPlacement building : BuildingClientEvents.getBuildings()) {
            if (!building.isExploredClientside || building.getBuilding() instanceof AbstractBridge)
                continue;
            double y = building.minCorner.getY() + 1;
            double x0 = building.minCorner.getX(), z0 = building.minCorner.getZ();
            double x1 = building.maxCorner.getX() + 1, z1 = building.maxCorner.getZ() + 1;
            float[] xs = PLATE_XS, ys = PLATE_YS;
            xs[0] = projX(x0, y, z0); xs[1] = projX(x1, y, z0); xs[2] = projX(x1, y, z1); xs[3] = projX(x0, y, z1);
            ys[0] = projY(x0, y, z0); ys[1] = projY(x1, y, z0); ys[2] = projY(x1, y, z1); ys[3] = projY(x0, y, z1);
            if (offScreen(xs, ys) || underHud(xs, ys))
                continue;
            int rgb = COLOURS.display(building.ownerName);
            if (!FogOfWarClientEvents.isBuildingInBrightChunk(building))
                rgb = darken(rgb, 0.5f);
            boolean selected = selectedBuildings.contains(building);
            quad(bb, mat, xs, ys, (plateAlpha(0x70) << 24) | rgb);
            boolean hover = !selected && building == hovered;
            int outline = selected ? 0xF0FFFFFF : hover ? 0xA0FFFFFF : (0xD0 << 24) | darken(rgb, 0.45f);
            float w = selected ? 1.5f : hover ? 1.25f : 1f;
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                line(bb, mat, xs[i], ys[i], xs[j], ys[j], w, outline);
            }
            // role glyph in the middle of the plate, so a base reads as factories / eco / defences at a glance
            Role role = roleOf(building.getBuilding());
            if (role != Role.NONE) {
                float plate = plateSize(xs, ys);
                if (plate >= MIN_GLYPH_PLATE) {
                    float gcx = (xs[0] + xs[1] + xs[2] + xs[3]) * 0.25f;
                    float gcy = (ys[0] + ys[1] + ys[2] + ys[3]) * 0.25f;
                    float gs = Mth.clamp(plate * 0.3f, 3f, 8f) * iconScale;
                    int tier = role == Role.MEX ? mexTier(building) : 0;
                    drawGlyph(bb, mat, role, tier, gcx, gcy, gs, true, 0);
                    drawGlyph(bb, mat, role, tier, gcx, gcy, gs, false, rgb);
                    // a levelled lab carries one gold pip per level under its glyph, for everyone who sees the
                    // plate (design/building_upgrades.md: upgraded buildings stand out to the opponent)
                    if (building.getBuilding().maxLevel > 1) {
                        int lv = building.getBuildingLevel();
                        if (lv > 1)
                            drawLevelPips(bb, mat, lv, gcx, gcy + gs * 1.55f, gs);
                    }
                }
            }
        }

        // ---- capture sites (vanilla marker entities serverside, synced as a list): owner-tinted plate + flag ----
        for (CapturePointsClient.Site site : CapturePointsClient.getSites()) {
            double y = site.y();
            double x0 = site.x() - 2, z0 = site.z() - 2, x1 = site.x() + 3, z1 = site.z() + 3;
            float ax = projX(x0, y, z0), bx = projX(x1, y, z0), cx = projX(x1, y, z1), dx = projX(x0, y, z1);
            float ay = projY(x0, y, z0), by = projY(x1, y, z0), cy = projY(x1, y, z1), dy = projY(x0, y, z1);
            float gcx = (ax + bx + cx + dx) * 0.25f, gcy = (ay + by + cy + dy) * 0.25f;
            if (gcx < -20 || gcx > guiW + 20 || gcy < -20 || gcy > guiH + 20 || underHud(gcx, gcy))
                continue;
            int rgb = site.owner().isEmpty() ? 0xB4B4B4 : PlayerColors.getPlayerDisplayColorHex(site.owner()) & 0xFFFFFF;
            int plateCol = (plateAlpha(0x60) << 24) | rgb;
            tri(bb, mat, ax, ay, bx, by, cx, cy, plateCol);
            tri(bb, mat, ax, ay, cx, cy, dx, dy, plateCol);
            int edge = (0xD0 << 24) | darken(rgb, 0.45f);
            line(bb, mat, ax, ay, bx, by, 1f, edge);
            line(bb, mat, bx, by, cx, cy, 1f, edge);
            line(bb, mat, cx, cy, dx, dy, 1f, edge);
            line(bb, mat, dx, dy, ax, ay, 1f, edge);
            float plate = Math.min((float) Math.hypot(bx - ax, by - ay), (float) Math.hypot(dx - ax, dy - ay));
            float gs = Mth.clamp(plate * 0.35f, 3.5f, 8f);
            drawGlyph(bb, mat, Role.CAPTURE, 0, gcx, gcy, gs, true, 0);
            drawGlyph(bb, mat, Role.CAPTURE, 0, gcx, gcy, gs, false, rgb);
        }

        // ---- units: role icons ----
        // reused every frame (open-addressing sets: clear() keeps the table, no per-entry nodes), so a big
        // selection doesn't allocate two hash sets and a list on every strategic-view frame
        Set<LivingEntity> selected = SELECTED_SCRATCH;
        Set<LivingEntity> preselected = PRESELECTED_SCRATCH;
        List<LivingEntity> drawLast = DRAW_LAST_SCRATCH;
        selected.clear();
        preselected.clear();
        drawLast.clear();
        selected.addAll(UnitClientEvents.getSelectedUnits());
        preselected.addAll(UnitClientEvents.getPreselectedUnits());
        for (LivingEntity entity : UnitClientEvents.getAllUnits()) {
            if (selected.contains(entity) || preselected.contains(entity))
                drawLast.add(entity);
            else
                drawUnitIcon(bb, mat, entity, partialTick, false, false);
        }
        for (LivingEntity entity : drawLast)
            drawUnitIcon(bb, mat, entity, partialTick, selected.contains(entity), preselected.contains(entity));
        selected.clear();   // don't hold on to entities between frames (or after leaving the world)
        preselected.clear();
        drawLast.clear();
        selectedBuildings.clear();

        BufferUploader.drawWithShader(bb.end());

        // building icons on top of their plates, once the plate is big enough to carry one - only for buildings
        // without a role glyph (houses, farms, markets...), which would otherwise be anonymous
        for (BuildingPlacement building : BuildingClientEvents.getBuildings()) {
            if (!building.isExploredClientside || building.getBuilding() instanceof AbstractBridge)
                continue;
            if (roleOf(building.getBuilding()) != Role.NONE)
                continue;
            net.minecraft.resources.ResourceLocation icon = building.getBuilding().icon;
            if (icon == null)
                continue;
            double y = building.minCorner.getY() + 1;
            double cx = (building.minCorner.getX() + building.maxCorner.getX() + 1) / 2.0;
            double cz = (building.minCorner.getZ() + building.maxCorner.getZ() + 1) / 2.0;
            float sx = projX(cx, y, cz);
            float sy = projY(cx, y, cz);
            float plate = Math.min(Math.abs(projX(building.maxCorner.getX() + 1, y, cz) - projX(building.minCorner.getX(), y, cz)),
                                   Math.abs(projY(cx, y, building.maxCorner.getZ() + 1) - projY(cx, y, building.minCorner.getZ())));
            int size = (int) Mth.clamp(plate * 0.7f, 6f, 16f);
            if (plate < 8 || sx < -20 || sx > guiW + 20 || sy < -20 || sy > guiH + 20 || underHud(sx, sy))
                continue;
            gg.blit(icon, (int) (sx - size / 2f), (int) (sy - size / 2f), size, size, 0, 0, 16, 16, 16, 16);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------------------------------------------------
    // building role glyphs
    // ------------------------------------------------------------------------------------------------------------

    private enum Role { NONE, CAPITOL, FACTORY, LAB2, LAB3, MEX, WIND, CONVERTER, DEFENCE, CAPTURE, STORAGE }

    private static final float MIN_GLYPH_PLATE = 7f;   // px; below this the plate itself is all you can read

    /**
     * A plate fill's alpha scaled by the hud_plate_opacity setting (Mod Settings > HUD, 10-100%; design/hud.md
     * auto-tidy): plates over a dense base can hide the terrain, so a player can thin them. Outlines and glyphs stay.
     */
    private static int plateAlpha(int base) {
        return Math.max(0, Math.min(255, base * plateOpacityPct / 100));
    }

    private static int plateOpacitySetting() {
        try {
            return com.solegendary.reignofnether.config.ReignOfNetherClientConfigs.HUD_PLATE_OPACITY.get();
        } catch (IllegalStateException ignored) {
            return 100;
        }
    }
    private static final int GLYPH_SHADOW = 0xC8000000;
    private static final int GLYPH_WHITE = 0xF2FFFFFF;

    // a building's role never changes, and Building objects are per-type singletons, so classify once
    private static final IdentityHashMap<Building, Role> roleCache = new IdentityHashMap<>();
    // a mex's tier is read from its blocks (see MetalExtractor.getUpgradeLevel), so cache it and re-read once a second
    private static final IdentityHashMap<BuildingPlacement, Integer> mexTierCache = new IdentityHashMap<>();
    private static long mexTierCacheTime = Long.MIN_VALUE;

    private static Role roleOf(Building b) {
        Role r = roleCache.get(b);
        if (r == null) {
            if (roleCache.size() > 512)   // custom buildings could in theory mint many types; keep it bounded
                roleCache.clear();
            r = classify(b);
            roleCache.put(b, r);
        }
        return r;
    }

    private static Role classify(Building b) {
        if (b.isCapitol)
            return Role.CAPITOL;
        if (b.capturable || b instanceof CapturableBeacon)
            return Role.CAPTURE;
        if (b instanceof MetalExtractor)
            return Role.MEX;
        if (b instanceof WindGenerator)
            return Role.WIND;
        if (b instanceof EnergyConverter)
            return Role.CONVERTER;
        if (b instanceof StorageVault)   // all five faction faces share the class
            return Role.STORAGE;
        if (b instanceof Castle || b instanceof Stronghold || b instanceof Fortress
                || b instanceof HeartOfTheWild
                || b instanceof com.solegendary.reignofnether.building.buildings.tide.AbyssalShipyard
                || b instanceof com.solegendary.reignofnether.building.buildings.storm.HeavensGate
                || b instanceof com.solegendary.reignofnether.building.buildings.brass.GreatForge)   // T3 labs (the first three also garrisonable)
            return Role.LAB3;
        if (b instanceof ArcaneTower || b instanceof Dungeon || b instanceof FlameSanctuary
                || b instanceof CircleOfElders
                || b instanceof com.solegendary.reignofnether.building.buildings.tide.Drydock
                || b instanceof com.solegendary.reignofnether.building.buildings.storm.CelestialObservatory
                || b instanceof com.solegendary.reignofnether.building.buildings.brass.AssemblyWorks)
            return Role.LAB2;
        if (b instanceof GarrisonableBuildingAddon || b instanceof StormOak   // watchtowers, bastion, the Court's bolt tree,
                || b instanceof com.solegendary.reignofnether.building.buildings.tide.LighthouseBattery)   // the Tidewrought's lighthouse
            return Role.DEFENCE;
        if (b instanceof ProductionBuilding pb && !(b instanceof AbstractStockpile))
            for (ProductionItem item : pb.productions.get())
                if (item instanceof IUnitProductionItem)
                    return Role.FACTORY;
        return Role.NONE;
    }

    private static int mexTier(BuildingPlacement building) {
        long now = MC.level != null ? MC.level.getGameTime() : 0;
        if (now - mexTierCacheTime >= 20 || now < mexTierCacheTime) {
            mexTierCache.clear();
            mexTierCacheTime = now;
        }
        Integer t = mexTierCache.get(building);
        if (t == null) {
            t = building.getUpgradeLevel() > 0 ? 1 : 0;
            mexTierCache.put(building, t);
        }
        return t;
    }

    /** {@code level} small gold diamonds in a row centred on (cx, cy), each on a dark shadow. */
    private static void drawLevelPips(BufferBuilder bb, Matrix4f mat, int level, float cx, float cy, float s) {
        float r = Math.max(1.5f, s * 0.28f), step = r * 2.6f;
        float x = cx - step * (level - 1) / 2f;
        for (int i = 0; i < level; i++, x += step) {
            diamond(bb, mat, x, cy, r + 1f, GLYPH_SHADOW);   // triangles straight into the buffer: no arrays per frame
            diamond(bb, mat, x, cy, r, 0xFFFFC83C);
        }
    }

    private static void diamond(BufferBuilder bb, Matrix4f mat, float x, float y, float r, int argb) {
        tri(bb, mat, x, y - r, x + r, y, x - r, y, argb);
        tri(bb, mat, x - r, y, x + r, y, x, y + r, argb);
    }

    private static float plateSize(float[] xs, float[] ys) {
        float a = (float) Math.hypot(xs[1] - xs[0], ys[1] - ys[0]);
        float b = (float) Math.hypot(xs[3] - xs[0], ys[3] - ys[0]);
        return Math.min(a, b);
    }

    /**
     * Draws one role glyph centred on (cx, cy), half-size s. Called twice per building: first as a dark shadow
     * (fatter strokes) and then in colour, which keeps it crisp on any team tint. Plain triangles in the same batch
     * as the plates, so 200+ buildings cost a few thousand vertices and no texture binds.
     *   capitol   gold star                    factory   crossed swords (army)
     *   T2/T3 lab two / three bars on a disc   mex       disc with a rim: iron (T1) or copper-gold (T2)
     *   wind      three-blade rotor            converter lightning bolt
     *   defence   crosshair                    capture   flag in the owner's colour
     *   storage   a banded strongbox (gold)
     */
    private static void drawGlyph(BufferBuilder bb, Matrix4f mat, Role role, int tier, float cx, float cy, float s,
                                  boolean shadow, int ownerRgb) {
        float grow = shadow ? 1.1f : 0f;   // shadow pass: everything a little bigger
        float lw = Math.max(1f, s * 0.32f) + grow;
        int white = shadow ? GLYPH_SHADOW : GLYPH_WHITE;
        switch (role) {
            case CAPITOL -> drawShape(bb, mat, Shape.STAR, cx, cy, s * 1.15f + grow, shadow ? GLYPH_SHADOW : 0xFFFFC83C);
            case FACTORY -> {
                float d = s * 0.85f;
                line(bb, mat, cx - d, cy - d, cx + d, cy + d, lw, white);
                line(bb, mat, cx + d, cy - d, cx - d, cy + d, lw, white);
                // hilts
                float h = s * 0.45f;
                line(bb, mat, cx - d - h * 0.2f, cy + d - h, cx - d + h, cy + d + h * 0.2f, lw * 0.8f, white);
                line(bb, mat, cx + d + h * 0.2f, cy + d - h, cx + d - h, cy + d + h * 0.2f, lw * 0.8f, white);
            }
            case LAB2, LAB3 -> {
                drawShape(bb, mat, Shape.CIRCLE, cx, cy, s * 1.05f + grow, shadow ? GLYPH_SHADOW : 0xE0281E3C);
                if (!shadow) {
                    int bars = role == Role.LAB3 ? 3 : 2;
                    int col = role == Role.LAB3 ? 0xFFFF8CFF : 0xFFB4DCFF;   // T3 magenta, T2 pale blue
                    float bw = s * 0.22f, gap = s * 0.2f;
                    float total = bars * bw + (bars - 1) * gap;
                    float x = cx - total / 2;
                    for (int i = 0; i < bars; i++) {
                        rect(bb, mat, x, cy - s * 0.6f, x + bw, cy + s * 0.6f, col);
                        x += bw + gap;
                    }
                }
            }
            case MEX -> {
                if (shadow) {
                    drawShape(bb, mat, Shape.CIRCLE, cx, cy, s + grow, GLYPH_SHADOW);
                } else {
                    int rim = tier > 0 ? 0xFFE8A040 : 0xFFA8A8B0;   // T2 copper-gold, T1 iron
                    drawShape(bb, mat, Shape.CIRCLE, cx, cy, s, rim);
                    drawShape(bb, mat, Shape.CIRCLE, cx, cy, s * (tier > 0 ? 0.55f : 0.68f), 0xFF34343C);
                    drawShape(bb, mat, Shape.CIRCLE, cx, cy, s * 0.25f, rim);
                }
            }
            case WIND -> {
                int col = shadow ? GLYPH_SHADOW : 0xFFFFF0A0;
                float r = s * 1.05f + grow;
                for (int i = 0; i < 3; i++) {
                    double a = -Math.PI / 2 + Math.PI * 2 * i / 3;
                    float tx = cx + (float) Math.cos(a) * r, ty = cy + (float) Math.sin(a) * r;
                    float wx = (float) Math.cos(a + Math.PI / 2) * r * 0.32f;
                    float wy = (float) Math.sin(a + Math.PI / 2) * r * 0.32f;
                    tri(bb, mat, cx, cy, tx + wx, ty + wy, tx, ty, col);
                }
                drawShape(bb, mat, Shape.CIRCLE, cx, cy, s * 0.25f + grow, col);
            }
            case CONVERTER -> {
                int col = shadow ? GLYPH_SHADOW : 0xFFFFE040;
                float g = grow;
                // a two-piece lightning bolt
                tri(bb, mat, cx + s * 0.35f + g, cy - s * 1.1f - g, cx - s * 0.65f - g, cy + s * 0.15f + g, cx + s * 0.1f, cy + s * 0.05f, col);
                tri(bb, mat, cx - s * 0.35f - g, cy + s * 1.1f + g, cx + s * 0.65f + g, cy - s * 0.15f - g, cx - s * 0.1f, cy - s * 0.05f, col);
            }
            case DEFENCE -> {
                int col = shadow ? GLYPH_SHADOW : 0xFFFF6E5A;
                float r = s * 0.8f;
                float thin = lw * 0.75f;
                ringLines(bb, mat, cx, cy, r, thin, col);
                line(bb, mat, cx - s * 1.15f, cy, cx - s * 0.35f, cy, thin, col);
                line(bb, mat, cx + s * 0.35f, cy, cx + s * 1.15f, cy, thin, col);
                line(bb, mat, cx, cy - s * 1.15f, cx, cy - s * 0.35f, thin, col);
                line(bb, mat, cx, cy + s * 0.35f, cx, cy + s * 1.15f, thin, col);
            }
            case CAPTURE -> {
                float px = cx - s * 0.55f;
                line(bb, mat, px, cy - s * 1.1f, px, cy + s * 1.1f, Math.max(1f, s * 0.2f) + grow, shadow ? GLYPH_SHADOW : GLYPH_WHITE);
                int col = shadow ? GLYPH_SHADOW : 0xFF000000 | brighten(ownerRgb, 0.15f);
                tri(bb, mat, px - grow * 0.5f, cy - s * 1.1f - grow, px + s * 1.25f + grow, cy - s * 0.55f, px - grow * 0.5f, cy + grow, col);
            }
            case STORAGE -> {
                // a strongbox: body, a darker lid band and a lock plate - reads as "bank" next to the mex disc
                float w = s * 1.0f + grow, h = s * 0.8f + grow;
                rect(bb, mat, cx - w, cy - h, cx + w, cy + h, shadow ? GLYPH_SHADOW : 0xFFE8B040);
                if (!shadow) {
                    rect(bb, mat, cx - w, cy - h * 0.25f, cx + w, cy + h * 0.05f, 0xFF7A5418);
                    rect(bb, mat, cx - s * 0.2f, cy - h * 0.4f, cx + s * 0.2f, cy + h * 0.3f, 0xFF34343C);
                }
            }
            default -> { }
        }
    }

    private static void ringLines(BufferBuilder bb, Matrix4f mat, float cx, float cy, float r, float w, int argb) {
        int n = 14;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            line(bb, mat, cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r,
                    cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, w, argb);
        }
    }

    /**
     * Clip study Oct 13 (b4; 21-19-44 0:46): enemy base icons sat on the minimap and the right-side buttons. The
     * overlay is meant to be under the HUD (it draws from RenderGuiEvent.Pre, the RTS panels from the screen's
     * Render.Post), so as a hard guarantee nothing of it is drawn on a HUD element at all: a unit icon, glyph or
     * capture plate whose centre, or a building plate any of whose corners, lands on a panel, button or the
     * minimap (as laid out last frame, HudClientEvents.isPointOverHud) is skipped. A few rect tests per icon.
     */
    private static boolean underHud(float sx, float sy) {
        return !HudPanels.hidden() && HudClientEvents.isPointOverHud((int) sx, (int) sy);
    }

    private static boolean underHud(float[] xs, float[] ys) {
        float cx = 0, cy = 0;
        for (int i = 0; i < xs.length; i++) {
            if (underHud(xs[i], ys[i]))
                return true;
            cx += xs[i];
            cy += ys[i];
        }
        return underHud(cx / xs.length, cy / ys.length);
    }

    private static boolean offScreen(float[] xs, float[] ys) {
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < xs.length; i++) {
            minX = Math.min(minX, xs[i]);
            maxX = Math.max(maxX, xs[i]);
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
        }
        return maxX < -8 || minX > guiW + 8 || maxY < -8 || minY > guiH + 8;
    }

    private static void drawUnitIcon(BufferBuilder bb, Matrix4f mat, LivingEntity entity, float partialTick,
                                     boolean isSelected, boolean isPreselected) {
        if (!entity.isAlive() || entity.isRemoved() || entity.isPassenger())
            return;
        if (MC.level != null && !MC.level.getWorldBorder().isWithinBounds(entity.getOnPos()))
            return;
        if (!FogOfWarClientEvents.isInBrightChunk(entity))
            return;
        // cloaked / thicket-hidden: no icon for enemies (owner and allies still see it, EntityMixin.isInvisibleTo)
        if (entity.isInvisible() && (MC.player == null || entity.isInvisibleTo(MC.player)))
            return;

        double x = Mth.lerp(partialTick, entity.xo, entity.getX());
        double y = Mth.lerp(partialTick, entity.yo, entity.getY());
        double z = Mth.lerp(partialTick, entity.zo, entity.getZ());
        float sx = projX(x, y, z);
        float sy = projY(x, y, z);
        if (sx < -10 || sx > guiW + 10 || sy < -10 || sy > guiH + 10 || underHud(sx, sy))
            return;

        // size by tier (BAR icontypes): workers 0.9x, T1 1x, T2 1.25x, T3 / heroes / commander 1.6x, times the
        // player's Icon size setting. Big units before the big switch are still models (keepsModel): no icon.
        boolean commander = isCommander(entity);
        int tier = UnitIconTier.tierOf(entity, commander);
        if (!strategicBig && UnitIconTier.isBig(entity, tier))
            return;
        float r = ICON_BASE_R * UnitIconTier.iconSize(tier) * iconScale;
        int fill = 0xFF000000 | iconColour(entity);
        if (isSelected)
            fill = 0xFF000000 | brighten(fill & 0xFFFFFF, 0.25f);

        Shape shape;
        if (commander)
            shape = Shape.STAR;
        else if (entity instanceof HeroUnit)
            shape = Shape.DIAMOND;
        else if (entity instanceof WorkerUnit)
            shape = Shape.CIRCLE;
        else if (entity instanceof RangedAttackerUnit)
            shape = Shape.TRIANGLE;
        else if (entity instanceof AttackerUnit)
            shape = Shape.SQUARE;
        else
            shape = Shape.CIRCLE;

        // ring outside the dark edge: white for selected, grey for preselected; the commander's gold ring (the
        // player's life) takes that colour instead of adding a second ring
        float ringW = isSelected ? SELECT_RING_W : isPreselected ? PRESELECT_RING_W : 0f;
        int ringCol = isSelected ? 0xFFFFFFFF : 0xFFC8C8C8;
        if (commander) {
            ringW = COMMANDER_RING_W;
            drawShape(bb, mat, Shape.CIRCLE, sx, sy, r + ICON_OUTLINE_W + ringW,
                    isSelected || isPreselected ? ringCol : 0xFFFFC83C);
        } else if (ringW > 0f) {
            drawShape(bb, mat, shape, sx, sy, r + ICON_OUTLINE_W + ringW, ringCol);
        }
        // the dark edge under every icon, so a pale team colour still reads on snow or bright grass
        drawShape(bb, mat, shape, sx, sy, r + ICON_OUTLINE_W, ICON_OUTLINE);
        drawShape(bb, mat, shape, sx, sy, r, fill);

        // an owned army unit that has stood still for 10 s (hud/IdleArmy) carries a pulsing amber diamond on its
        // top-right edge on a dark shadow: hundreds of same-size icons all looked busy, and rows of idle units sat
        // there for minutes (playtest clip). Two triangles each, in the batch already open - no extra draw call.
        if (idlePulse > 0 && com.solegendary.reignofnether.hud.IdleArmy.isIdle(entity)) {
            float ir = Math.max(1.6f, r * 0.5f);
            diamond(bb, mat, sx + r * 0.95f, sy - r * 0.95f, ir + 1f, ICON_OUTLINE);
            diamond(bb, mat, sx + r * 0.95f, sy - r * 0.95f, ir, (idlePulse << 24) | (IDLE_AMBER & 0xFFFFFF));
        }

        // an upgraded unit (unit/UpgradeVisuals: any researched look) wears a notch in its faction's glow colour on the
        // icon's top edge - BAR's veteran chevrons, one tick, since icons replace the glowing / trimmed model. Two
        // triangles, the dark one under it for contrast on a pale icon.
        if (entity instanceof Unit u && u.getUpgradeMask() != 0) {
            int glow = com.solegendary.reignofnether.faction.FactionTraits.of(
                    com.solegendary.reignofnether.faction.Factions.getFaction(u)).upgradeGlow;
            int notch = 0xFF000000 | (glow == 0 ? 0xFFFFFF : glow);
            float top = sy - r * 0.85f, w = Math.max(1.5f, r * 0.45f), h = Math.max(2f, r * 0.6f);
            tri(bb, mat, sx, top - h - ICON_OUTLINE_W, sx + w + ICON_OUTLINE_W, top + 0.5f, sx - w - ICON_OUTLINE_W, top + 0.5f, ICON_OUTLINE);
            tri(bb, mat, sx, top - h, sx + w, top, sx - w, top, notch);
        }

        // compact health bar under damaged units (replaces the per-unit world health bars)
        float hp = entity.getHealth() / Math.max(1f, entity.getMaxHealth());
        if (hp < 0.999f) {
            float bw = r * 2 + 2;
            float bx = sx - bw / 2;
            float by = sy + r + ICON_OUTLINE_W + ringW + 1;
            rect(bb, mat, bx - 0.5f, by - 0.5f, bx + bw + 0.5f, by + 2f, 0xC0000000);
            int hpCol = hp > 0.6f ? 0xFF3CD23C : hp > 0.3f ? 0xFFE6C832 : 0xFFE03C32;
            rect(bb, mat, bx, by, bx + bw * Mth.clamp(hp, 0f, 1f), by + 1.5f, hpCol);
        }
    }

    private enum Shape { SQUARE, TRIANGLE, CIRCLE, DIAMOND, STAR }

    /** Icon radius (gui px) of a T1 unit at Icon size 1; UnitIconTier.iconSize scales it per tier. */
    private static final float ICON_BASE_R = 3.6f;
    /** The dark edge under every icon shape; 1.2 nominal so the square (drawn at 0.85 r) still gets a full px. */
    private static final float ICON_OUTLINE_W = 1.2f;
    private static final int ICON_OUTLINE = 0xE6101010;
    private static final float SELECT_RING_W = 1.4f, PRESELECT_RING_W = 1f, COMMANDER_RING_W = 2.2f;

    private static final float[] STAR_X = new float[10], STAR_Y = new float[10];
    private static final Set<LivingEntity> SELECTED_SCRATCH = new it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<>();
    private static final Set<LivingEntity> PRESELECTED_SCRATCH = new it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<>();
    private static final List<LivingEntity> DRAW_LAST_SCRATCH = new ArrayList<>();

    private static void drawShape(BufferBuilder bb, Matrix4f mat, Shape shape, float cx, float cy, float r, int argb) {
        switch (shape) {
            case SQUARE -> rect(bb, mat, cx - r * 0.85f, cy - r * 0.85f, cx + r * 0.85f, cy + r * 0.85f, argb);
            case DIAMOND -> {
                tri(bb, mat, cx, cy - r, cx + r, cy, cx - r, cy, argb);
                tri(bb, mat, cx - r, cy, cx + r, cy, cx, cy + r, argb);
            }
            case TRIANGLE -> tri(bb, mat, cx, cy - r * 1.1f, cx + r * 1.05f, cy + r * 0.8f, cx - r * 1.05f, cy + r * 0.8f, argb);
            case STAR -> {   // five points, two fans
                int n = 10;
                float[] px = STAR_X, py = STAR_Y;   // scratch: no arrays per star per frame
                for (int i = 0; i < n; i++) {
                    double a = -Math.PI / 2 + Math.PI * i / 5;
                    float rr = (i % 2 == 0) ? r : r * 0.45f;
                    px[i] = cx + (float) Math.cos(a) * rr;
                    py[i] = cy + (float) Math.sin(a) * rr;
                }
                for (int i = 0; i < n; i++)
                    tri(bb, mat, cx, cy, px[i], py[i], px[(i + 1) % n], py[(i + 1) % n], argb);
            }
            case CIRCLE -> {
                int n = 14;
                float r2 = r * 0.92f;
                for (int i = 0; i < n; i++) {
                    double a0 = Math.PI * 2 * i / n;
                    double a1 = Math.PI * 2 * (i + 1) / n;
                    tri(bb, mat, cx, cy,
                            cx + (float) Math.cos(a0) * r2, cy + (float) Math.sin(a0) * r2,
                            cx + (float) Math.cos(a1) * r2, cy + (float) Math.sin(a1) * r2, argb);
                }
            }
        }
    }

    private static void vtx(BufferBuilder bb, Matrix4f mat, float x, float y, int argb) {
        bb.vertex(mat, x, y, 0).color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF).endVertex();
    }

    private static void tri(BufferBuilder bb, Matrix4f mat, float x1, float y1, float x2, float y2, float x3, float y3, int argb) {
        vtx(bb, mat, x1, y1, argb);
        vtx(bb, mat, x2, y2, argb);
        vtx(bb, mat, x3, y3, argb);
    }

    private static void rect(BufferBuilder bb, Matrix4f mat, float x0, float y0, float x1, float y1, int argb) {
        tri(bb, mat, x0, y0, x1, y0, x1, y1, argb);
        tri(bb, mat, x0, y0, x1, y1, x0, y1, argb);
    }

    private static void quad(BufferBuilder bb, Matrix4f mat, float[] xs, float[] ys, int argb) {
        tri(bb, mat, xs[0], ys[0], xs[1], ys[1], xs[2], ys[2], argb);
        tri(bb, mat, xs[0], ys[0], xs[2], ys[2], xs[3], ys[3], argb);
    }

    private static void line(BufferBuilder bb, Matrix4f mat, float x0, float y0, float x1, float y1, float w, int argb) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-4f)
            return;
        float nx = -dy / len * w / 2, ny = dx / len * w / 2;
        // two triangles straight into the buffer (no temp arrays: this runs for every plate edge and glyph stroke)
        tri(bb, mat, x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, argb);
        tri(bb, mat, x0 + nx, y0 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny, argb);
    }

    private static int darken(int rgb, float f) {
        int r = (int) (((rgb >> 16) & 0xFF) * f);
        int g = (int) (((rgb >> 8) & 0xFF) * f);
        int b = (int) ((rgb & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }

    private static int brighten(int rgb, float f) {
        int r = (int) Mth.lerp(f, (rgb >> 16) & 0xFF, 255);
        int g = (int) Mth.lerp(f, (rgb >> 8) & 0xFF, 255);
        int b = (int) Mth.lerp(f, rgb & 0xFF, 255);
        return (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------------------------------------------------
    // in-world readability extras: range rings and selection plates
    // ------------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (com.solegendary.reignofnether.postfx.ShaderPacks.skipOverlays()) return;   // shader pack shadow map: overlays cast no shadow
        if (!OrthoviewClientEvents.isEnabled() || MC.level == null)
            return;
        int sys = com.solegendary.reignofnether.perf.FrameParts.begin(com.solegendary.reignofnether.perf.FrameParts.UNIT_RINGS);
        try {
            renderLevel(evt);
        } finally {
            com.solegendary.reignofnether.perf.FrameParts.end(sys);
        }
    }

    private static void renderLevel(RenderLevelStageEvent evt) {
        // in strategic view the icons carry the selection, except for big units still drawn as models
        if (evt.getStage() == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS && selectionPlatesEnabled
                && (!strategic || !strategicBig)) {
            drawSelectionPlates(evt.getPoseStack(), evt.getPartialTick(), evt.getFrustum());
            drawIdleRings(evt.getPoseStack(), evt.getPartialTick(), evt.getFrustum());
        }
        else if (evt.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS && rangeRingsEnabled)
            drawRangeRings(evt.getPoseStack(), evt.getPartialTick());
    }

    private static void drawSelectionPlates(PoseStack poseStack, float partialTick,
                                            @javax.annotation.Nullable net.minecraft.client.renderer.culling.Frustum frustum) {
        List<LivingEntity> selectedUnits = UnitClientEvents.getSelectedUnits();
        if (selectedUnits.isEmpty())
            return;
        Vec3 cp = MC.gameRenderer.getMainCamera().getPosition();
        RenderType type = MyRenderer.LINES_UNDER_ENTITIES;
        VertexConsumer vc = MC.renderBuffers().bufferSource().getBuffer(type);
        for (LivingEntity entity : selectedUnits) {
            if (entity.isPassenger())
                continue;
            if (strategic && !keepsModel(entity))
                continue;   // its icon carries the selection ring
            double radius = Math.max(0.5, entity.getBbWidth() * 0.75) + 0.15;
            // off screen: no rings (perf plan R2 - a big selection drew every ring every frame, wherever it was); the
            // box covers the outer ring and a tick of movement either way
            if (frustum != null && !frustum.isVisible(entity.getBoundingBox().inflate(radius + 0.6, 0.6, radius + 0.6)))
                continue;
            if (!FogOfWarClientEvents.isInBrightChunk(entity))
                continue;
            int rgb = teamColour(entity);
            float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
            double x = Mth.lerp(partialTick, entity.xo, entity.getX()) - cp.x;
            double y = Mth.lerp(partialTick, entity.yo, entity.getY()) + 0.05 - cp.y;
            double z = Mth.lerp(partialTick, entity.zo, entity.getZ()) - cp.z;
            ring(poseStack, vc, x, y, z, radius, 24, r, g, b, 0.95f);
            ring(poseStack, vc, x, y, z, radius - 0.07, 24, r, g, b, 0.95f);
            ring(poseStack, vc, x, y, z, radius + 0.07, 24, 1f, 1f, 1f, 0.6f);
        }
        MC.renderBuffers().bufferSource().endBatch(type);
    }

    /**
     * Normal zoom: a small pulsing amber ring under each idle army unit that is drawn as a model (and not selected -
     * the selection ring already marks those). One batch for all of them, 10 line segments a unit.
     */
    private static void drawIdleRings(PoseStack poseStack, float partialTick,
                                      @javax.annotation.Nullable net.minecraft.client.renderer.culling.Frustum frustum) {
        List<LivingEntity> idle = com.solegendary.reignofnether.hud.IdleArmy.get();
        if (idle.isEmpty())
            return;
        Vec3 cp = MC.gameRenderer.getMainCamera().getPosition();
        float a = 0.55f + 0.4f * (float) (0.5 + 0.5 * Math.sin((System.currentTimeMillis() % 1000L) / 1000.0 * Math.PI * 2));
        RenderType type = MyRenderer.LINES_UNDER_ENTITIES;
        VertexConsumer vc = MC.renderBuffers().bufferSource().getBuffer(type);
        Set<LivingEntity> selectedUnits = IDLE_SELECTED_SCRATCH;
        selectedUnits.clear();
        selectedUnits.addAll(UnitClientEvents.getSelectedUnits());
        int drawn = 0;
        for (LivingEntity entity : idle) {
            if (drawn >= MAX_IDLE_RINGS)
                break;
            if (entity.isRemoved() || entity.isPassenger() || !FogOfWarClientEvents.isInBrightChunk(entity)
                    || (strategic && !keepsModel(entity)) || selectedUnits.contains(entity))
                continue;
            // off screen: no ring, and it doesn't use up the MAX_IDLE_RINGS budget (perf plan R2)
            double ir = Math.max(0.4, entity.getBbWidth() * 0.55);
            if (frustum != null && !frustum.isVisible(entity.getBoundingBox().inflate(ir + 0.6, 0.6, ir + 0.6)))
                continue;
            double x = Mth.lerp(partialTick, entity.xo, entity.getX()) - cp.x;
            double y = Mth.lerp(partialTick, entity.yo, entity.getY()) + 0.05 - cp.y;
            double z = Mth.lerp(partialTick, entity.zo, entity.getZ()) - cp.z;
            ring(poseStack, vc, x, y, z, ir, 10, 1f, 0.71f, 0.16f, a);
            drawn++;
        }
        selectedUnits.clear();
        MC.renderBuffers().bufferSource().endBatch(type);
    }

    private static final int MAX_IDLE_RINGS = 400;
    private static final Set<LivingEntity> IDLE_SELECTED_SCRATCH = new it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<>();

    /**
     * Attack range rings. Clip study Oct 13 (b5; 21-19-44 1:06, 21-37-00 0:30): a 12-unit selection drew ten
     * overlapping red rings and told the player nothing. BAR draws a ring for the unit you are looking at, so this
     * draws at most two: the HUD card unit (the single selected unit, or the group's card unit) and the hovered unit.
     * Never one per unit of a group.
     */
    private static void drawRangeRings(PoseStack poseStack, float partialTick) {
        LivingEntity card = HudClientEvents.hudSelectedEntity;
        List<LivingEntity> preselected = UnitClientEvents.getPreselectedUnits();
        LivingEntity hovered = preselected.size() == 1 ? preselected.get(0) : null;
        if (card == null && hovered == null)
            return;
        Vec3 cp = MC.gameRenderer.getMainCamera().getPosition();
        RenderType type = MyRenderer.LINES_NO_DEPTH_TEST;
        VertexConsumer vc = MC.renderBuffers().bufferSource().getBuffer(type);
        if (card != null)
            rangeRing(poseStack, vc, card, cp, partialTick);
        if (hovered != null && hovered != card)
            rangeRing(poseStack, vc, hovered, cp, partialTick);
        MC.renderBuffers().bufferSource().endBatch(type);
    }

    private static void rangeRing(PoseStack poseStack, VertexConsumer vc, LivingEntity entity, Vec3 cp, float partialTick) {
        if (!(entity instanceof AttackerUnit attacker) || entity.isRemoved() || !FogOfWarClientEvents.isInBrightChunk(entity))
            return;
        float range = attacker.getAttackRange();
        if (range < 2.5f)
            return;
        double x = Mth.lerp(partialTick, entity.xo, entity.getX()) - cp.x;
        double y = Mth.lerp(partialTick, entity.yo, entity.getY()) + 0.15 - cp.y;
        double z = Mth.lerp(partialTick, entity.zo, entity.getZ()) - cp.z;
        ring(poseStack, vc, x, y, z, range, RING_SEGMENTS, 1f, 0.35f, 0.3f, 0.7f);
    }

    // cos and sin of every ring vertex's angle (Math.PI * 2 * i / segments - the same doubles the loop used to compute),
    // per segment count: three 24-segment rings under each selected unit took two cos / sin pairs a segment, every frame
    private static final double[][][] RING_TRIG = new double[RING_SEGMENTS + 1][][];

    private static double[][] ringTrig(int segments) {
        double[][] t = segments < RING_TRIG.length ? RING_TRIG[segments] : null;
        if (t == null) {
            t = new double[2][segments + 1];
            for (int i = 0; i <= segments; i++) {
                double angle = Math.PI * 2 * i / segments;
                t[0][i] = Math.cos(angle);
                t[1][i] = Math.sin(angle);
            }
            if (segments < RING_TRIG.length)
                RING_TRIG[segments] = t;
        }
        return t;
    }

    private static void ring(PoseStack poseStack, VertexConsumer vc, double cx, double cy, double cz, double radius,
                             int segments, float r, float g, float b, float a) {
        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        double[][] trig = ringTrig(segments);
        double[] cos = trig[0], sin = trig[1];
        for (int i = 0; i < segments; i++) {
            float x0 = (float) (cx + cos[i] * radius), z0 = (float) (cz + sin[i] * radius);
            float x1 = (float) (cx + cos[i + 1] * radius), z1 = (float) (cz + sin[i + 1] * radius);
            float nx = x1 - x0, nz = z1 - z0;
            float len = (float) Math.sqrt(nx * nx + nz * nz);
            if (len > 0) {
                nx /= len;
                nz /= len;
            }
            vc.vertex(pose, x0, (float) cy, z0).color(r, g, b, a).normal(normal, nx, 0, nz).endVertex();
            vc.vertex(pose, x1, (float) cy, z1).color(r, g, b, a).normal(normal, nx, 0, nz).endVertex();
        }
    }
}
