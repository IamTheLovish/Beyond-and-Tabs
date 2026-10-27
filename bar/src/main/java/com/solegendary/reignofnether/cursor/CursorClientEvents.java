package com.solegendary.reignofnether.cursor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import com.mojang.blaze3d.systems.RenderSystem;
import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.FogPlacement;
import com.solegendary.reignofnether.blocks.RangeIndicator;
import com.solegendary.reignofnether.building.addon.RangeIndicatorAddon;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.items.ItemClientEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.minimap.MinimapClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.resources.ResourceSources;
import com.solegendary.reignofnether.sandbox.SandboxAction;
import com.solegendary.reignofnether.unit.NonUnitClientEvents;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.SmartSelect;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.util.MiscUtil;
import com.solegendary.reignofnether.util.MyMath;
import com.solegendary.reignofnether.util.MyRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.solegendary.reignofnether.util.MiscUtil.fcs;
import static net.minecraft.util.Mth.floor;
import static net.minecraft.world.level.BlockGetter.traverseBlocks;

/**
 * Handler that implements and manages screen-to-world translations of the cursor and block/entity selection
 */
public class CursorClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();

    private static boolean leftClickDown = false;
    private static boolean rightClickDown = false;

    // pos of block moused over or inside a box select
    // currently blocks are not fully selectable
    private static BlockPos preselectedBlockPos = new BlockPos(0, 0, 0);
    // pos of cursor exactly on: first non-air block, last frame, near to screen, far from screen
    private static Vector3d cursorWorldPos = new Vector3d(0, 0, 0);
    private static Vector3d cursorWorldPosLast = new Vector3d(0, 0, 0);
    // the height of the last block the cursor ray hit: a miss lands on this plane (FogPlacement); NaN = none yet
    private static double lastGroundY = Double.NaN;
    // pos of cursor on screen for box selections
    private static Vec2 cursorLeftClickDownPos = new Vec2(-1, -1);
    private static Vec2 cursorLeftClickDragPos = new Vec2(-1, -1);
    private static Vec2 cursorRightClickDownPos = new Vec2(-1, -1);
    private static Vec2 cursorRightClickDragPos = new Vec2(-1, -1);
    private static BlockPos rightClickStartBp = new BlockPos(0, 0, 0);
    private static final int DRAG_THRESHOLD = 10;
    private static final double FORMATION_DRAG_SCREEN_PX = 30;
    // action that is performed on the next left click
    private static UnitAction leftClickAction = null;
    private static SandboxAction leftClickSandboxAction = null;

    // ---- Smart Select (SmartSelect, after BAR's widget) ----
    // the left press started a drag box: false when Alt was down at the press, which is the map-draw / Alt-click
    // gesture (MapDrawClientEvents) and never a box. Alt pressed AFTER the box started is the combat-only filter.
    private static boolean boxArmed = false;
    // the modifiers the box was last evaluated with: the release selects exactly what the highlight showed
    private static SmartSelect.Mods lastBoxMods = SmartSelect.Mods.NONE;
    // Z and Space held during a box are its filters, not the ability slot / "jump to last alert" / shop keys they
    // also are: their next release is swallowed (consumeBoxModifierRelease, from HudClientEvents.onKeyRelease)
    private static final java.util.Set<Integer> boxModifierKeys = new java.util.HashSet<>();

    /** The box's predicates on the client: the synced idle-worker set and the HUD's unit-type names. */
    private static final SmartSelect.Rules BOX_RULES = new SmartSelect.Rules() {
        @Override
        public boolean isMobile(LivingEntity le) {
            return SmartSelect.DEFAULT.isMobile(le);
        }

        @Override
        public boolean isCombat(LivingEntity le) {
            return SmartSelect.DEFAULT.isCombat(le);
        }

        @Override
        public boolean isWorker(LivingEntity le) {
            return SmartSelect.DEFAULT.isWorker(le);
        }

        @Override
        public boolean isIdle(LivingEntity le) {
            // an owned worker's idleness is what the server says it is (the idle-worker cycler's list); any other unit
            // answers from its client-side goals, which mirror every order the player gave
            if (le instanceof WorkerUnit && UnitClientEvents.getPlayerToEntityRelationship(le) == Relationship.OWNED)
                return UnitClientEvents.idleWorkerIds.contains(le.getId());
            return SmartSelect.DEFAULT.isIdle(le);
        }

        @Override
        public Object typeKey(LivingEntity le) {
            // the grouping Ctrl+Tab and the selection summary use (villager professions, jockeys, captains)
            return HudClientEvents.getModifiedEntityName(le);
        }
    };

    private static boolean isRawKeyDown(int key) {
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(MC.getWindow().getWindow(), key);
    }

    /** The Smart Select modifiers held right now; Z and Space are remembered so their release stays quiet. */
    private static SmartSelect.Mods currentBoxMods() {
        // Z counts only while a box drag is in progress (SmartSelect.Mods.held), never as a stray builder-key press
        boolean dragging = leftClickDown && boxArmed;
        boolean z = dragging && isRawKeyDown(GLFW.GLFW_KEY_Z);
        boolean space = isRawKeyDown(GLFW.GLFW_KEY_SPACE);
        if (z) boxModifierKeys.add(GLFW.GLFW_KEY_Z);
        if (space) boxModifierKeys.add(GLFW.GLFW_KEY_SPACE);
        return SmartSelect.Mods.held(dragging, Keybindings.shiftMod.isDown(), Keybindings.ctrlMod.isDown(),
                Keybindings.altMod.isDown(), z, space);
    }

    /**
     * True (once) when this key was held as a box filter during the current or last drag: the HUD's key handling
     * then skips it, so Space over a box never jumps to the last alert and Z never fires an ability slot.
     */
    public static boolean consumeBoxModifierRelease(int key) {
        return boxModifierKeys.remove(key);
    }

    /** Can the local player select and order this unit (own, allied under shared control, or sandbox all-mobs)? */
    private static boolean isControllable(LivingEntity le) {
        return UnitClientEvents.getPlayerToEntityRelationship(le) == Relationship.OWNED ||
                NonUnitClientEvents.canControlAllMobs() ||
                AlliancesClient.canControlAlly(le);
    }

    public static Vector3d getCursorWorldPos() {
        return cursorWorldPos;
    }

    public static BlockPos getPreselectedBlockPos() {
        return preselectedBlockPos;
    }

    public static UnitAction getLeftClickAction() {
        return leftClickAction;
    }

    public static SandboxAction getLeftClickSandboxAction() {
        return leftClickSandboxAction;
    }

    public static boolean isRightClickDown() {
        return rightClickDown;
    }

    public static boolean isRightDragActive() {
        // BAR starts a formation line after ~30 screen pixels so a sloppy right-click stays a plain move; positions here
        // are GUI-scaled, so convert (never below the old 10 GUI px)
        double guiScale = Math.max(1, net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale());
        float t = (float) Math.max(DRAG_THRESHOLD, FORMATION_DRAG_SCREEN_PX / guiScale);
        return rightClickDown && cursorRightClickDownPos.distanceToSqr(cursorRightClickDragPos) > t * t;
    }

    public static Vec2 getCursorRightClickDownPos() {
        return cursorRightClickDownPos;
    }

    public static Vec2 getCursorRightClickDragPos() {
        return cursorRightClickDragPos;
    }

    public static BlockPos getRightClickStartBp() {
        return rightClickStartBp;
    }

    public static void setLeftClickAction(UnitAction actionName) {
        if (actionName != null)
            leftClickSandboxAction = null;
        if (actionName != null &&
                List.of(UnitAction.STARTRTS_VILLAGERS,
                        UnitAction.STARTRTS_MONSTERS,
                        UnitAction.STARTRTS_PIGLINS).contains(actionName))
            leftClickAction = actionName;
        else if (UnitClientEvents.getSelectedUnits().size() > 0 ||
                BuildingClientEvents.getSelectedBuildings().size() > 0)
            leftClickAction = actionName;
        else if (actionName == null)
            leftClickAction = null;

        RangeIndicatorAddon hudSelectedRIA;
        if (HudClientEvents.hudSelectedEntity instanceof RangeIndicator ri) {
            ri.updateHighlightBps(MC.level);
        } else if (HudClientEvents.hudSelectedPlacement != null &&
                (hudSelectedRIA = HudClientEvents.hudSelectedPlacement.getBuilding().getActiveAddon(RangeIndicatorAddon.class)) != null) {
            hudSelectedRIA.updateHighlightBps(HudClientEvents.hudSelectedPlacement);
        }
        if (actionName != null)
            ItemClientEvents.resetActions();
    }

    public static void setLeftClickSandboxAction(SandboxAction actionName) {
        if (actionName != null)
            leftClickAction = null;
        leftClickSandboxAction = actionName;
    }

    private static final ResourceLocation TEXTURE_CURSOR = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/cursors/customcursor.png");
    private static final ResourceLocation TEXTURE_HAND = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/cursors/customcursor_hand.png");
    private static final ResourceLocation TEXTURE_HAND_GRAB = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/cursors/customcursor_hand_grab.png");
    private static final ResourceLocation TEXTURE_SWORD = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/cursors/customcursor_sword.png");
    private static final ResourceLocation TEXTURE_CROSS = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/cursors/customcursor_cross.png");
    private static final ResourceLocation TEXTURE_SHOVEL = ResourceLocation.fromNamespaceAndPath("reignofnether", "textures/cursors/customcursor_shovel.png");

    // RTS cursor position in window pixels after rts_cursor_speed (the DISABLED-mode cursor is virtual)
    private static double virtualX = 0;
    private static double virtualY = 0;
    private static boolean virtualValid = false;

    // scales this frame's raw mouse delta by rts_cursor_speed and writes it back to GLFW. Vanilla's own xpos/ypos
    // catch up on the next move event, so hit-tests lag at most one mouse move behind the drawn cursor.
    private static void updateVirtualCursor(long window) {
        double[] x = new double[1];
        double[] y = new double[1];
        GLFW.glfwGetCursorPos(window, x, y);
        double speed = ReignOfNetherClientConfigs.RTS_CURSOR_SPEED.get();
        if (virtualValid && speed != 1.0) {
            double nx = CursorMath.clamp(CursorMath.scaleDelta(virtualX, x[0], speed), MC.getWindow().getScreenWidth());
            double ny = CursorMath.clamp(CursorMath.scaleDelta(virtualY, y[0], speed), MC.getWindow().getScreenHeight());
            if (nx != x[0] || ny != y[0])
                GLFW.glfwSetCursorPos(window, nx, ny);
            x[0] = nx;
            y[0] = ny;
        }
        virtualX = x[0];
        virtualY = y[0];
        virtualValid = true;
    }

    // fractional GUI-scaled cursor position
    private static float virtualGuiX() {
        return CursorMath.windowToGui(virtualX, MC.getWindow().getScreenWidth(), MC.getWindow().getGuiScaledWidth());
    }

    private static float virtualGuiY() {
        return CursorMath.windowToGui(virtualY, MC.getWindow().getScreenHeight(), MC.getWindow().getGuiScaledHeight());
    }

    @SubscribeEvent
    public static void onDrawScreen(ScreenEvent.Render evt) {
        long window = MC.getWindow().getWindow();

        if (!OrthoviewClientEvents.isEnabled() || !(evt.getScreen() instanceof TopdownGui)) {
            if (GLFW.glfwRawMouseMotionSupported())
                GLFW.glfwSetInputMode(window, GLFW.GLFW_RAW_MOUSE_MOTION, GLFW.GLFW_TRUE);
            GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            SystemCursors.reset(window); // menus get the OS arrow back
            CursorDiagClient.setRtsState(false, false, 0, 0);
            virtualValid = false;
            return;
        }
        if (MC.player == null || MC.level == null) return;

        // rts_system_cursor: the OS cursor (user's own pointer settings, no lag) instead of the locked virtual one
        final boolean systemCursor = CursorModePolicy.useSystemCursor(ReignOfNetherClientConfigs.RTS_SYSTEM_CURSOR.get(), true);
        if (!systemCursor)
            SystemCursors.dispose(window); // setting turned off while playing

        // ************************************
        // Manage cursor icons based on actions
        // ************************************

        // hides default cursor and locks it to the window to allow edge panning
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, CursorModePolicy.glfwCursorMode(systemCursor));
        // raw input skips Windows' pointer acceleration (which made box-select drags speed up);
        // rts_cursor_speed scales it instead. The system cursor wants the user's own settings, so no raw motion.
        if (GLFW.glfwRawMouseMotionSupported())
            GLFW.glfwSetInputMode(window, GLFW.GLFW_RAW_MOUSE_MOTION,
                    CursorModePolicy.wantRawMotion(systemCursor) ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
        if (systemCursor)
            virtualValid = false; // pick, box and edge-pan read vanilla's xpos/ypos, which is the OS position
        else
            updateVirtualCursor(window);

        // blitting like this will cause it to be rendered 1 frame behind realtime (this hopefully shouldn't be noticeable...)
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // draw at edge of screen even if mouse is off it, from the fractional position so it moves in
        // window pixels rather than GUI-scaled steps
        float cursorDrawX = CursorMath.clampDraw(
                systemCursor ? CursorMath.windowToGui(MC.mouseHandler.xpos(), MC.getWindow().getScreenWidth(), MC.getWindow().getGuiScaledWidth()) : virtualGuiX(),
                MC.getWindow().getGuiScaledWidth(), 5);
        float cursorDrawY = CursorMath.clampDraw(
                systemCursor ? CursorMath.windowToGui(MC.mouseHandler.ypos(), MC.getWindow().getScreenHeight(), MC.getWindow().getGuiScaledHeight()) : virtualGuiY(),
                MC.getWindow().getGuiScaledHeight(), 5);
        CursorDiagClient.setRtsState(true, systemCursor, cursorDrawX, cursorDrawY);
        ResourceLocation texture;

        int cursorXoffset = 0;
        int cursorYoffset = 0;

        // Alt over an active box is its combat filter, not the grab hand
        if (Keybindings.altMod.isDown() && ((leftClickDown && !boxArmed) || rightClickDown)) {
            RenderSystem.setShaderTexture(0, TEXTURE_HAND_GRAB);
            texture = TEXTURE_HAND_GRAB;
        } else if (Keybindings.altMod.isDown()) {
            RenderSystem.setShaderTexture(0, TEXTURE_HAND);
            texture = TEXTURE_HAND;
        } else if (leftClickAction != null && (leftClickAction.equals(UnitAction.ATTACK) || leftClickAction.equals(UnitAction.ATTACK_MOVE))) {
            RenderSystem.setShaderTexture(0, TEXTURE_SWORD);
            texture = TEXTURE_SWORD;
        } else if ((leftClickAction != null && leftClickAction.equals(UnitAction.BUILD_REPAIR))
                || (leftClickAction == null && leftClickSandboxAction == null && isBuilderOverGhost())) {
            RenderSystem.setShaderTexture(0, TEXTURE_SHOVEL);
            texture = TEXTURE_SHOVEL;
        } else if (leftClickAction != null || leftClickSandboxAction != null || ItemClientEvents.hasLeftClickAction()) {
            RenderSystem.setShaderTexture(0, TEXTURE_CROSS);
            texture = TEXTURE_CROSS;
            cursorXoffset = -8;
            cursorYoffset = -8;
        } else {
            RenderSystem.setShaderTexture(0, TEXTURE_CURSOR);
            texture = TEXTURE_CURSOR;
        }
        if (systemCursor) {
            // the OS draws it: same sprite as a hardware cursor, hotspot where the sprite's offset puts its tip
            SystemCursors.use(window, texture, -cursorXoffset, -cursorYoffset);
        } else {
            evt.getGuiGraphics().pose().pushPose();
            evt.getGuiGraphics().pose().translate(cursorDrawX, cursorDrawY, 2500);
            evt.getGuiGraphics().blit(texture,
                    cursorXoffset,
                    cursorYoffset,
                    16,
                    16, 16,
                    16, 16,
                    16, 16
            );
            evt.getGuiGraphics().pose().popPose();
        }

        // ***********************************************
        // Convert cursor on-screen 2d pos to world 3d pos
        // ***********************************************
        cursorWorldPosLast = new Vector3d(
                cursorWorldPos.x,
                cursorWorldPos.y,
                cursorWorldPos.z
        );
        cursorWorldPos = MiscUtil.screenPosToWorldPos(MC, evt.getMouseX(), evt.getMouseY());

        // calc near and far cursorWorldPos to get a cursor line vector
        Vector3d lookVector = MiscUtil.getPlayerLookVector(MC);
        Vector3d cursorWorldPosNear = MyMath.addVector3d(cursorWorldPos, lookVector, -200);
        Vector3d cursorWorldPosFar = MyMath.addVector3d(cursorWorldPos, lookVector, 200);

        // only spend time doing refining calcs for if we actually moved the cursor (or if this is the first time)
        if (cursorWorldPos.x != cursorWorldPosLast.x || cursorWorldPos.y != cursorWorldPosLast.y || cursorWorldPos.z != cursorWorldPosLast.z) {

            // a miss (ground the client was never sent reads as air) used to return the ray's far end, 200 blocks
            // underground, and the building ghost vanished there: land on the plane of the last real hit instead
            // (FogPlacement). Nothing of the hidden chunk is read, so nothing leaks.
            Vec3 hitPos = cursorRayHit(cursorWorldPosNear, cursorWorldPosFar);
            if (hitPos != null) {
                lastGroundY = hitPos.y;
            } else {
                if (Double.isNaN(lastGroundY) && MC.level != null)
                    lastGroundY = MC.level.getSeaLevel();
                double[] p = FogPlacement.rayToPlaneY(cursorWorldPosNear.x, cursorWorldPosNear.y, cursorWorldPosNear.z,
                        lookVector.x, lookVector.y, lookVector.z, lastGroundY);
                hitPos = p != null ? new Vec3(p[0], p[1], p[2])
                        : new Vec3(cursorWorldPosLast.x, cursorWorldPosLast.y, cursorWorldPosLast.z);
            }
            cursorWorldPos = new Vector3d(hitPos.x, hitPos.y, hitPos.z);
            preselectedBlockPos = new BlockPos((int) Math.floor(hitPos.x), (int) Math.floor(hitPos.y), (int) Math.floor(hitPos.z));

            boolean usingPosAbove = false;

            // if we clipped a non-solid block (eg. tall grass) search adjacent blocks for a next-best match
            if (!MiscUtil.isSolidBlocking(MC.level, preselectedBlockPos)) {
                preselectedBlockPos = getRefinedBlockPos(preselectedBlockPos, cursorWorldPosNear);
                // disallow selecting a block just below a fluid block
                if (!MC.level.getBlockState(preselectedBlockPos.above()).getFluidState().isEmpty()) {
                    preselectedBlockPos = preselectedBlockPos.above();
                    usingPosAbove = true;
                }
            }

            if (!usingPosAbove &&
                    !BuildingUtils.isPosInsideAnyBuilding(true, preselectedBlockPos) &&
                    BuildingUtils.isPosInsideAnyBuilding(true, preselectedBlockPos.above()) &&
                    ResourceSources.getFromBlockPos(preselectedBlockPos, MC.level) == null)
                preselectedBlockPos = preselectedBlockPos.above();
            else if (ResourceSources.GATHERABLE_PLANTS.contains(MC.level.getBlockState(preselectedBlockPos.above()).getBlock()))
                preselectedBlockPos = preselectedBlockPos.above();
        }

        // ****************************************
        // Find entity moused over and/or selected
        // ****************************************
        List<LivingEntity> nearbyEntities = MiscUtil.getEntitiesWithinRange(cursorWorldPos, 30, LivingEntity.class, MC.level);
        nearbyEntities.sort(Comparator.comparing((le) -> {
            int priority = 0;
            if (le instanceof HeroUnit)
                priority += 5;
            if (le instanceof Unit unit) {
                priority += 1;
                priority += unit.getAbilities().get().size();
                priority += unit.getCost().population;
            }
            return -priority;
        }));

        UnitClientEvents.clearPreselectedUnits();

        for (LivingEntity entity : nearbyEntities) {
            // don't let the player select themselves
            if (entity.getId() == MC.player.getId())
                continue;

            // inflate by set amount to improve click accuracy
            AABB entityaabb = entity.getBoundingBox().inflate(0.1);
            if (entity instanceof Unit unit) {
                entityaabb = unit.getInflatedSelectionBox();
            } else if (entity instanceof Chicken) {
                entityaabb = entityaabb.inflate(0.2f, 0, 0.2f);
                entityaabb.setMaxY(entityaabb.maxY + 0.6f);
            }

            if (MyMath.rayIntersectsAABBCustom(cursorWorldPosNear, MiscUtil.getPlayerLookVector(MC), entityaabb)) {
                UnitClientEvents.addPreselectedUnit(entity);
                if (UnitClientEvents.getPreselectedUnits().size() > 0)
                    break; // only allow one moused-over unit at a time
            }
        }

        // **********************
        // Find items moused over
        // **********************
        List<ItemEntity> nearbyItems = MiscUtil.getEntitiesWithinRange(cursorWorldPos, 30, ItemEntity.class, MC.level);

        ItemClientEvents.clearPreselectedItems();
        ItemClientEvents.clearRenderedItems();
        for (ItemEntity itemEntity : nearbyItems) {
            ItemClientEvents.addRenderedItem(itemEntity);
            // inflate by set amount to improve click accuracy
            AABB entityaabb = itemEntity.getBoundingBox().inflate(0.25);
            entityaabb.setMaxY(entityaabb.maxY + 0.75d);
            if (MyMath.rayIntersectsAABBCustom(cursorWorldPosNear, MiscUtil.getPlayerLookVector(MC), entityaabb)) {
                ItemClientEvents.addPreselectedItem(itemEntity);
            }
        }

        // *******************************
        // Calculate box-preselected units
        // *******************************
        // weird bug when downPos == dragPos makes random entities get selected by this algorithm
        float dist = cursorLeftClickDownPos.distanceToSqr(cursorLeftClickDragPos);

        if (leftClickDown && boxArmed && dist > 0) {

            // can't use AABB here as it's always axis-aligned (ie. no camera-rotation)
            // instead, improvise our own quad
            // https://math.stackexchange.com/questions/1472049/check-if-a-point-is-inside-a-rectangular-shaped-area-3d

            List<LivingEntity> entitiesInRange = MiscUtil.getEntitiesWithinRange(cursorWorldPos, 100, LivingEntity.class, MC.level);

            ArrayList<Vec3> uvwp = MyMath.prepIsPointInsideRect3d(MC,
                    (int) cursorLeftClickDownPos.x, (int) cursorLeftClickDownPos.y, // top left
                    (int) cursorLeftClickDownPos.x, (int) cursorLeftClickDragPos.y, // bottom left
                    (int) cursorLeftClickDragPos.x, (int) cursorLeftClickDragPos.y // bottom right
            );
            // everything under the box, then the Smart Select rules on the part the player can control: the
            // highlight is exactly what the release will select (SmartSelect). A box over nothing of the player's
            // keeps the old look (every unit under it lit) and, as before, changes nothing on release.
            List<LivingEntity> inBox = new ArrayList<>();
            List<LivingEntity> controllable = new ArrayList<>();
            for (LivingEntity entity : entitiesInRange) {
                if (MyMath.isPointInsideRect3d(uvwp, entity.getBoundingBox().getCenter()) &&
                        entity.getId() != MC.player.getId() &&
                        UnitClientEvents.isPreselectable(entity)) {
                    inBox.add(entity);
                    if (isControllable(entity))
                        controllable.add(entity);
                }
            }
            lastBoxMods = currentBoxMods();
            List<LivingEntity> show = controllable.isEmpty() ? inBox
                    : SmartSelect.filterBox(controllable, UnitClientEvents.getSelectedUnits(), lastBoxMods, BOX_RULES);
            UnitClientEvents.clearPreselectedUnits();
            for (LivingEntity entity : show)
                UnitClientEvents.addPreselectedUnit(entity);
        } else if (!leftClickDown) {
            // keys not held any more were released outside the HUD's key handling: forget them
            boxModifierKeys.removeIf(k -> !isRawKeyDown(k));
        }
    }


    public static boolean isBoxSelecting() {
        return cursorLeftClickDownPos.x >= 0 &&
                cursorLeftClickDownPos.y >= 0 &&
                cursorLeftClickDragPos.x >= 0 &&
                cursorLeftClickDragPos.y >= 0;
    }

    // draw box selection rectangle
    @SubscribeEvent
    public static void renderOverlay(RenderGuiOverlayEvent.Post evt) {

        if (leftClickDown && boxArmed) {
            // the dragged corner follows the fractional cursor; the anchor corner is the clicked integer pos.
            // fill() only takes ints, so draw a whole-pixel rect and scale it to the fractional size.
            float x1 = cursorLeftClickDownPos.x, y1 = cursorLeftClickDownPos.y;
            float x2 = virtualValid ? virtualGuiX() : cursorLeftClickDragPos.x;
            float y2 = virtualValid ? virtualGuiY() : cursorLeftClickDragPos.y;
            float w = Math.abs(x2 - x1), h = Math.abs(y2 - y1);
            int iw = Math.max(1, Math.round(w)), ih = Math.max(1, Math.round(h));
            PoseStack pose = evt.getGuiGraphics().pose();
            pose.pushPose();
            pose.translate(Math.min(x1, x2), Math.min(y1, y2), 0);
            pose.scale(w / iw, h / ih, 1);
            evt.getGuiGraphics().fill(0, 0, iw, ih,
                    0x0341e868); //ARGB(hex); note that alpha ranges between ~0-16, not 0-255
            pose.popPose();
        }
    }

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Post evt) {

        if (!OrthoviewClientEvents.isEnabled() ||
                MinimapClientEvents.isPointInsideMinimap(evt.getMouseX(), evt.getMouseY()) ||
                HudClientEvents.isMouseOverAnyButtonOrHud())
            return;

        // select a moused over entity by left clicking it
        // a left press that issued (or is laying out) an order is not a selection: no box under an attack-move line
        if (evt.getButton() == GLFW.GLFW_MOUSE_BUTTON_1 && !UnitClientEvents.isLeftPressCommand()) {
            cursorLeftClickDownPos = new Vec2(floor(evt.getMouseX()), floor(evt.getMouseY()));
            cursorLeftClickDragPos = new Vec2(floor(evt.getMouseX()), floor(evt.getMouseY()));
            leftClickDown = true;
            // Alt at the press is the map-draw / Alt-click gesture, never a box; Alt later filters the box
            boxArmed = !Keybindings.altMod.isDown();
            lastBoxMods = SmartSelect.Mods.NONE;
        }
        if (evt.getButton() == GLFW.GLFW_MOUSE_BUTTON_2) {
            cursorRightClickDownPos = new Vec2(floor(evt.getMouseX()), floor(evt.getMouseY()));
            cursorRightClickDragPos = new Vec2(floor(evt.getMouseX()), floor(evt.getMouseY()));
            rightClickStartBp = getPreselectedBlockPos();
            rightClickDown = true;
        }
    }

    @SubscribeEvent
    public static void onMouseDrag(ScreenEvent.MouseDragged.Pre evt) {
        if (!OrthoviewClientEvents.isEnabled())
            return;

        if (cursorLeftClickDownPos.x >= 0 || cursorLeftClickDownPos.y >= 0)
            cursorLeftClickDragPos = new Vec2(floor(evt.getMouseX()), floor(evt.getMouseY()));

        if (rightClickDown)
            cursorRightClickDragPos = new Vec2(floor(evt.getMouseX()), floor(evt.getMouseY()));
    }

    @SubscribeEvent
    public static void onMouseRelease(ScreenEvent.MouseButtonReleased.Post evt) {
        if (!OrthoviewClientEvents.isEnabled()) return;

        // select a moused over entity by left clicking it
        if (evt.getButton() == GLFW.GLFW_MOUSE_BUTTON_1) {
            leftClickDown = false;

            // enact box selection, excluding non-unit mobs
            // for single-click selection, see UnitClientEvents
            // except if attack-moving or no owned units are preselected (to prevent deselection)
            // the preselection is already the Smart Select result of the last frame (onDrawScreen): what was lit is
            // what is selected, combined with the selection as the Shift / Ctrl seen then ask
            ArrayList<LivingEntity> preselectedUnit = UnitClientEvents.getPreselectedUnits();
            if (boxArmed && preselectedUnit.size() > 0 && !cursorLeftClickDownPos.equals(cursorLeftClickDragPos)) {

                // only act if there is at least 1 owned entity so we don't deselect things by box selecting only non-owned entities
                ArrayList<LivingEntity> box = new ArrayList<>();
                for (LivingEntity unit : preselectedUnit)
                    if (isControllable(unit))
                        box.add(unit);

                if (!box.isEmpty()) {
                    List<LivingEntity> result = SmartSelect.combine(box, UnitClientEvents.getSelectedUnits(), lastBoxMods);
                    if (!lastBoxMods.shift() && !lastBoxMods.ctrl()) {
                        UnitClientEvents.clearSelectedUnits();
                        for (LivingEntity unit : result)
                            UnitClientEvents.addSelectedUnit(unit);
                    } else {
                        // Ctrl's removals and Shift's additions, without re-adding what is already selected (a
                        // selected unit that has since walked into the dark would not pass addSelectedUnit again)
                        java.util.Set<Integer> keep = new java.util.HashSet<>();
                        for (LivingEntity unit : result)
                            keep.add(unit.getId());
                        java.util.Set<Integer> have = new java.util.HashSet<>();
                        for (LivingEntity unit : UnitClientEvents.getSelectedUnits())
                            have.add(unit.getId());
                        UnitClientEvents.removeSelectedUnits(e -> !keep.contains(e.getId()));
                        for (LivingEntity unit : result)
                            if (!have.contains(unit.getId()))
                                UnitClientEvents.addSelectedUnit(unit);
                    }
                    HudClientEvents.setLowestCdHudEntity();
                }

            }
            boxArmed = false;
            cursorLeftClickDownPos = new Vec2(-1, -1);
            cursorLeftClickDragPos = new Vec2(-1, -1);
        }
        if (evt.getButton() == GLFW.GLFW_MOUSE_BUTTON_2) {
            rightClickDown = false;
            cursorRightClickDownPos = new Vec2(-1, -1);
            cursorRightClickDragPos = new Vec2(-1, -1);
        }
    }

    // prevent moused over blocks being outlined in the usual way (ie. by raytracing from player to block)
    /**
     * A builder is selected and the cursor is on a friendly blueprint ghost (and on nothing real in front of it):
     * a right-click sends it to build/assist that ghost (UnitClientEvents), so the cursor shows the build shovel.
     */
    public static boolean isBuilderOverGhost() {
        return HudClientEvents.hudSelectedEntity instanceof WorkerUnit
                && UnitClientEvents.getPreselectedUnits().isEmpty()
                && BuildingClientEvents.getPreselectedBuilding() == null
                && com.solegendary.reignofnether.building.BlueprintClientEvents.ghostUnderCursor() != null;
    }

    @SubscribeEvent
    public static void onHighlightBlockEvent(RenderHighlightEvent.Block evt) {
        if (MC.level != null && OrthoviewClientEvents.isEnabled())
            evt.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (com.solegendary.reignofnether.postfx.ShaderPacks.skipOverlays()) return;   // shader pack shadow map: overlays cast no shadow
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS ||
                HudClientEvents.isMouseOverAnyButtonOrHud())
            return;
        if (MC.level != null && OrthoviewClientEvents.isEnabled()) {

            BuildingPlacement preSelBuilding = BuildingClientEvents.getPreselectedBuilding();
            // don't draw block outline if we've selected a builder unit and are mousing over a building (unless leftClick action is MOVE)
            boolean buildingTargetedByWorker = (HudClientEvents.hudSelectedEntity instanceof WorkerUnit &&
                    preSelBuilding != null &&
                    CursorClientEvents.getLeftClickAction() != UnitAction.MOVE &&
                    (BuildingClientEvents.getPlayerToBuildingRelationship(preSelBuilding) == Relationship.OWNED ||
                            CursorClientEvents.getLeftClickAction() == UnitAction.BUILD_REPAIR));
            // same for attacker
            boolean buildingTargetedByAttacker = (HudClientEvents.hudSelectedEntity instanceof AttackerUnit &&
                    preSelBuilding != null &&
                    CursorClientEvents.getLeftClickAction() != UnitAction.MOVE &&
                    (BuildingClientEvents.getPlayerToBuildingRelationship(preSelBuilding) != Relationship.OWNED ||
                            CursorClientEvents.getLeftClickAction() == UnitAction.ATTACK));

            // do we own any of the selected buildings or entities?
            // will be false if there are none selected in the first place
            boolean ownAnySelected = false;
            ArrayList<BuildingPlacement> selBuildings = BuildingClientEvents.getSelectedBuildings();
            for (BuildingPlacement building : selBuildings)
                if (building != null && BuildingClientEvents.getPlayerToBuildingRelationship(building) == Relationship.OWNED)
                    ownAnySelected = true;
            for (LivingEntity entity : UnitClientEvents.getSelectedUnits()) {
                if (UnitClientEvents.getPlayerToEntityRelationship(entity) == Relationship.OWNED) {
                    ownAnySelected = true;
                    break;
                }
            }
            boolean isLeftClickActionStartRTS = leftClickAction != null &&
                    List.of(UnitAction.STARTRTS_VILLAGERS,
                            UnitAction.STARTRTS_MONSTERS,
                            UnitAction.STARTRTS_PIGLINS).contains(leftClickAction);

            if ((!OrthoviewClientEvents.isCameraMovingByMouse() &&
                    !leftClickDown && ownAnySelected &&
                    UnitClientEvents.getPreselectedUnits().size() == 0 &&
                    BuildingClientEvents.getPreselectedBuilding() == null &&
                    ItemClientEvents.getPreselectedItems().isEmpty() &&
                    !buildingTargetedByWorker && !buildingTargetedByAttacker && !isBuilderOverGhost()) || isLeftClickActionStartRTS || getLeftClickSandboxAction() != null) {

                ResourceLocation rl = ResourceLocation.parse("forge:textures/white.png");
                var vertexConsumer = MC.renderBuffers().bufferSource().getBuffer(RenderType.entityTranslucent(rl));
                if (MiscUtil.isSnowLayerBlock(MC.level.getBlockState(getPreselectedBlockPos().offset(0, 1, 0)).getBlock())) {
                    AABB aabb = new AABB(preselectedBlockPos);
                    aabb = aabb.setMaxY(aabb.maxY + 0.13f);
                    MyRenderer.drawSolidBox(evt.getPoseStack(), vertexConsumer, aabb, null, 1, 1, 1, rightClickDown ? 0.3f : 0.15f, ResourceLocation.parse("forge:textures/white.png"));
                    aabb = new AABB(preselectedBlockPos).move(0, 0.13, 0);
                    MyRenderer.drawLineBox(evt.getPoseStack(), aabb, 1.0f, 1.0f, 1.0f, rightClickDown ? 1.0f : 0.5f);
                } else {
                    MyRenderer.drawBox(evt.getPoseStack(), vertexConsumer, preselectedBlockPos, 1, 1, 1, rightClickDown ? 0.3f : 0.15f);
                    MyRenderer.drawBlockOutline(evt.getPoseStack(), preselectedBlockPos, rightClickDown ? 1.0f : 0.5f);
                }
            }
        }
    }

    // returns the exact spot that the cursorWorldPos ray meets a solid block
    public static Vec3 getRefinedCursorWorldPos(Vector3d cursorWorldPosNear, Vector3d cursorWorldPosFar) {
        Vec3 vectorNear = new Vec3(cursorWorldPosNear.x, cursorWorldPosNear.y, cursorWorldPosNear.z);
        Vec3 vectorFar = new Vec3(cursorWorldPosFar.x, cursorWorldPosFar.y, cursorWorldPosFar.z);

        // clip() returns the point of clip, not the clipped block giving off-by-one errors so move slightly to compensate
        HitResult hitResult = null;
        if (MC.level != null) {
            hitResult = clip(MC.level, new ClipContext(vectorNear, vectorFar, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, null));
        }

        if (hitResult != null)
            return hitResult.getLocation().add(new Vec3(-0.001, -0.001, -0.001));
        return new Vec3(0, 0, 0);
    }

    /** As getRefinedCursorWorldPos, but null when the ray hits no block (a miss, not the ray's far end). */
    public static Vec3 cursorRayHit(Vector3d cursorWorldPosNear, Vector3d cursorWorldPosFar) {
        if (MC.level == null)
            return null;
        Vec3 vectorNear = new Vec3(cursorWorldPosNear.x, cursorWorldPosNear.y, cursorWorldPosNear.z);
        Vec3 vectorFar = new Vec3(cursorWorldPosFar.x, cursorWorldPosFar.y, cursorWorldPosFar.z);
        BlockHitResult hitResult = clip(MC.level, new ClipContext(vectorNear, vectorFar, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, null));
        if (hitResult == null || hitResult.getType() == HitResult.Type.MISS)
            return null;
        return hitResult.getLocation().add(new Vec3(-0.001, -0.001, -0.001));
    }

    // from ClientLevel.clip() but added exception for leaf blocks
    private static BlockHitResult clip(ClientLevel level, ClipContext pContext) {
        return traverseBlocks(pContext.getFrom(), pContext.getTo(), pContext, (clipContext, blockPos) -> {
            BlockState blockstate = level.getBlockState(blockPos);
            FluidState fluidstate = level.getFluidState(blockPos);
            Vec3 vec3 = clipContext.getFrom();
            Vec3 vec31 = clipContext.getTo();
            VoxelShape voxelshape = clipContext.getBlockShape(blockstate, level, blockPos);
            BlockHitResult blockhitresult = level.clipWithInteractionOverride(vec3, vec31, blockPos, voxelshape, blockstate);
            VoxelShape voxelshape1 = clipContext.getFluidShape(fluidstate, level, blockPos);
            BlockHitResult blockhitresult1 = voxelshape1.clip(vec3, vec31, blockPos);
            double d0 = blockhitresult == null ? Double.MAX_VALUE : clipContext.getFrom().distanceToSqr(blockhitresult.getLocation());
            double d1 = blockhitresult1 == null ? Double.MAX_VALUE : clipContext.getFrom().distanceToSqr(blockhitresult1.getLocation());
            BlockHitResult result = d0 <= d1 ? blockhitresult : blockhitresult1;

            if (result != null) {
                BlockState bs = level.getBlockState(result.getBlockPos());
                if (OrthoviewClientEvents.shouldHideLeaves() && level.getBlockState(result.getBlockPos()).getBlock() instanceof LeavesBlock)
                    result = null;
                else if (!MiscUtil.isSolidBlocking(level, result.getBlockPos()) && bs.getFluidState().isEmpty())
                    result = null;
                else if (MiscUtil.isSnowLayerBlock(bs.getBlock()))
                    result = null;
            }
            return result;

        }, (p_151372_) -> {
            Vec3 vec3 = p_151372_.getFrom().subtract(p_151372_.getTo());
            return BlockHitResult.miss(p_151372_.getTo(), Direction.getNearest(vec3.x, vec3.y, vec3.z), new BlockPos((int) p_151372_.getTo().x, (int) p_151372_.getTo().y, (int) p_151372_.getTo().z));
        });
    }

    private static BlockPos getRefinedBlockPos(BlockPos bp, Vector3d cursorWorldPosNear) {
        ArrayList<BlockPos> blocks = new ArrayList<>();

        blocks.add(bp);
        blocks.add(bp.north());
        blocks.add(bp.south());
        blocks.add(bp.east());
        blocks.add(bp.west());
        blocks.add(bp.north().east());
        blocks.add(bp.south().east());
        blocks.add(bp.north().west());
        blocks.add(bp.south().west());

        blocks.add(bp.above());
        blocks.add(bp.above().north());
        blocks.add(bp.above().south());
        blocks.add(bp.above().east());
        blocks.add(bp.above().west());
        blocks.add(bp.above().north().east());
        blocks.add(bp.above().south().east());
        blocks.add(bp.above().north().west());
        blocks.add(bp.above().south().west());

        blocks.add(bp.below());
        blocks.add(bp.below().north());
        blocks.add(bp.below().south());
        blocks.add(bp.below().east());
        blocks.add(bp.below().west());
        blocks.add(bp.below().north().east());
        blocks.add(bp.below().south().east());
        blocks.add(bp.below().north().west());
        blocks.add(bp.below().south().west());

        BlockPos bestBp = bp;
        double smallestDist = 10000;
        Vector3d lookVector = MiscUtil.getPlayerLookVector(MC);

        for (BlockPos block : blocks) {
            double dist = new Vec3(block.getX(), block.getY(), block.getZ())
                    .distanceTo(new Vec3(cursorWorldPosNear.x, cursorWorldPosNear.y, cursorWorldPosNear.z));

            if (MC.level != null) {
                // if we have a worker selected then include resource blocks that would otherwise be ignored like plants
                boolean isBlockSelectableResource = false;
                if (HudClientEvents.hudSelectedEntity instanceof Unit workerUnit)
                    isBlockSelectableResource = ResourceSources.getFromBlockPos(block, MC.level) != null;

                BlockState bs = MC.level.getBlockState(block);
                if ((MiscUtil.isSolidBlocking(MC.level, block) || isBlockSelectableResource) &&
                        (!(bs.getBlock() instanceof LeavesBlock) || !OrthoviewClientEvents.shouldHideLeaves()) &&
                        !(MiscUtil.isSnowLayerBlock(bs.getBlock())) &&
                        MyMath.rayIntersectsAABBCustom(cursorWorldPosNear, lookVector, new AABB(block)) &&
                        dist < smallestDist) {
                    smallestDist = dist;
                    bestBp = block;
                }
            }
        }
        return bestBp;
    }
}


