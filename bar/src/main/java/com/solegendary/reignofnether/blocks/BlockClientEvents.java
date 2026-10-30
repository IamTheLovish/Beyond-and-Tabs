package com.solegendary.reignofnether.blocks;


import com.mojang.datafixers.util.Pair;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.addon.NightSourceAddon;
import com.solegendary.reignofnether.building.addon.RangeIndicatorAddon;
import com.solegendary.reignofnether.building.buildings.placements.SculkCatalystPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.items.ItemClientEvents;
import com.solegendary.reignofnether.registrars.BlockRegistrar;
import com.solegendary.reignofnether.research.ResearchClient;
import com.solegendary.reignofnether.research.researchItems.ResearchSculkAmplifiers;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.util.MiscUtil;
import com.solegendary.reignofnether.util.MyRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Set;

public class BlockClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    // Almost no building or unit carries a range indicator, but the frame used to walk all 1,700 buildings and 500 units
    // (scene 18: 0.87 ms). The few that do are found every 5 ticks, or when either list changes size.
    private static final com.solegendary.reignofnether.perf.RefreshGate RANGE_GATE =
        new com.solegendary.reignofnether.perf.RefreshGate(5);
    private static final ArrayList<BuildingPlacement> rangeBuildings = new ArrayList<>();
    private static final ArrayList<LivingEntity> rangeUnits = new ArrayList<>();

    private static void refreshRangeSubsets() {
        java.util.List<BuildingPlacement> all = BuildingClientEvents.getBuildings();
        ArrayList<LivingEntity> units = UnitClientEvents.getAllUnits();
        long t = MC.level == null ? 0 : MC.level.getGameTime();
        if (!RANGE_GATE.due(t, all.size() * 31 + units.size()))
            return;
        rangeBuildings.clear();
        for (BuildingPlacement b : all)
            if (b.getBuilding().getActiveAddon(RangeIndicatorAddon.class) != null)
                rangeBuildings.add(b);
        rangeUnits.clear();
        for (LivingEntity le : units)
            if (le instanceof RangeIndicator)
                rangeUnits.add(le);
    }

    private static com.mojang.blaze3d.vertex.VertexConsumer frameBuffer = null;

    // each range building's circle, as a mesh rebuilt every RangeCircleMesh.REFRESH_TICKS ticks or when its highlight
    // set changes (lovish's Oct 29 game: drawing every face the old way was 1.9-2.1 ms of each late-game frame)
    private static final java.util.Map<BuildingPlacement, RangeCircleMesh> CIRCLE_MESHES = new java.util.HashMap<>();

    private static RangeCircleMesh circleMesh(BuildingPlacement building, Set<BlockPos> bps, long now) {
        RangeCircleMesh mesh = CIRCLE_MESHES.get(building);
        if (mesh == null || !mesh.fresh(bps, bps.size(), now)) {
            mesh = new RangeCircleMesh(bps, bp -> BlockUtils.getSnowLayers(building.level.getBlockState(bp.above())), bps, now);
            CIRCLE_MESHES.put(building, mesh);
        }
        return mesh;
    }

    /** The view pose moved by minus the camera entity's eye, as MyRenderer.drawSolidBox does per box; pop it after. */
    private static com.mojang.blaze3d.vertex.PoseStack cameraPose(com.mojang.blaze3d.vertex.PoseStack ps) {
        net.minecraft.world.entity.Entity cam = MC.getCameraEntity();
        ps.pushPose();
        if (cam != null)
            ps.translate(-cam.getX(), -(cam.getY() + cam.getEyeHeight()), -cam.getZ());
        return ps;
    }

    /**
     * The translucent buffer, fetched when the first highlight is drawn. Fetching it every frame whether or not
     * anything drew made the buffer source end (flush) whatever batch was open and begin an empty one: most of this
     * handler's 1.2 ms in scene 18, where no hero or tower has a range to show.
     */
    private static com.mojang.blaze3d.vertex.VertexConsumer vc() {
        if (frameBuffer == null)
            frameBuffer = MC.renderBuffers().bufferSource().getBuffer(RenderType.entityTranslucent(WHITE));
        return frameBuffer;
    }

    private static final ResourceLocation WHITE = ResourceLocation.parse("forge:textures/white.png");

    public static NightCircleMode nightCircleMode = NightCircleMode.NO_OVERLAPS;

    // deals with block rendering jobs like range indicators
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;
        int fp = com.solegendary.reignofnether.perf.FrameParts.begin(com.solegendary.reignofnether.perf.FrameParts.WORLD_MARKERS);
        int fp2 = 0;
        try {
            int fg = com.solegendary.reignofnether.perf.FrameParts.begin(com.solegendary.reignofnether.perf.FrameParts.MARKERS_GATE);
            boolean skip;
            try {
                skip = com.solegendary.reignofnether.postfx.ShaderPacks.skipOverlays();
            } finally {
                com.solegendary.reignofnether.perf.FrameParts.end(fg);
            }
            if (skip) return;   // shader pack shadow map: overlays cast no shadow
            fp2 = com.solegendary.reignofnether.perf.FrameParts.begin(com.solegendary.reignofnether.perf.FrameParts.MARKERS_RANGE);
            frameBuffer = null;   // fetched on the first highlight drawn: see vc()
            if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                return;
            }
            int fr = com.solegendary.reignofnether.perf.FrameParts.begin(com.solegendary.reignofnether.perf.FrameParts.MARKERS_RANGE_REFRESH);
            try {
                refreshRangeSubsets();
            } finally {
                com.solegendary.reignofnether.perf.FrameParts.end(fr);
            }
            // draw range indicators for buildings with abilities and monster night sources: each circle from its
            // cached mesh (RangeCircleMesh), only when it is in view, all through one pose
            long now = MC.level == null ? 0 : MC.level.getGameTime();
            com.mojang.blaze3d.vertex.PoseStack ps = null;
            for (BuildingPlacement building : rangeBuildings) {
                RangeIndicatorAddon ria = building.getBuilding().getActiveAddon(RangeIndicatorAddon.class);
                if (ria == null)
                    continue;
                boolean selected = BuildingClientEvents.getSelectedBuildings().contains(building);
                if (!selected && ria.showOnlyWhenSelected(building))
                    continue;
                RangeCircleMesh mesh = circleMesh(building, ria.getHighlightBps(building), now);
                if (mesh.faces == 0 || !evt.getFrustum().isVisible(mesh.bounds)
                        || !FogOfWarClientEvents.isBuildingInBrightChunk(building))
                    continue;
                if (ps == null)
                    ps = cameraPose(evt.getPoseStack());
                if (selected)
                    mesh.emit(ps.last().pose(), ps.last().normal(), vc(), 0f, 0.8f, 0f, 0.3f);
                else
                    mesh.emit(ps.last().pose(), ps.last().normal(), vc(), 0f, 0f, 0f, 0.6f);
            }
            if (ps != null)
                ps.popPose();
            if (now % 200 == 0)
                CIRCLE_MESHES.keySet().retainAll(rangeBuildings);

            if (tempMouseCircleOrigin != null && MC.level != null) {
                Set<BlockPos> bps = MiscUtil.CircleUtil.getCircle(tempMouseCircleOrigin.getFirst(), tempMouseCircleOrigin.getSecond());
                for (BlockPos bp : bps) {
                    int snowLayers = BlockUtils.getSnowLayers(MC.level.getBlockState(bp.above()));
                    float yOffset = snowLayers * 0.125f;
                    MyRenderer.drawBlockFace(evt.getPoseStack(), vc(), Direction.UP, yOffset, bp, 0f, 0.8f, 0f, 0.3f);
                }
            }
            int fu = com.solegendary.reignofnether.perf.FrameParts.begin(com.solegendary.reignofnether.perf.FrameParts.MARKERS_RANGE_UNITS);
            try {
                for (LivingEntity le : rangeUnits) {
                    if (!(le instanceof RangeIndicator ri) || le.isRemoved()) continue;
                    if (!FogOfWarClientEvents.isBlockVisible(le.getOnPos())) continue;
                    boolean selected = UnitClientEvents.getSelectedUnits().contains(le);
                    if (selected || !ri.showOnlyWhenSelected()) {
                        for (BlockPos bp : ri.getHighlightBps()) {
                            int snowLayers = BlockUtils.getSnowLayers(le.level().getBlockState(bp.above()));
                            float yOffset = snowLayers * 0.125f;
                            MyRenderer.drawBlockFace(evt.getPoseStack(), vc(), Direction.UP, yOffset, bp, 0f, 0.8f, 0f, 0.3f);
                        }
                    }
                }
            } finally {
                com.solegendary.reignofnether.perf.FrameParts.end(fu);
            }
            if (HudClientEvents.hudSelectedEntity != null && MC.level != null &&
                    (ItemClientEvents.actionableUnitItem != null || ItemClientEvents.hoveredInvItem != null)) {
                RangeIndicator ri = ItemClientEvents.actionableUnitItem;
                if (ri == null) ri = ItemClientEvents.hoveredInvItem;
                for (BlockPos bp : ri.getHighlightBps()) {
                    int snowLayers = BlockUtils.getSnowLayers(MC.level.getBlockState(bp.above()));
                    float yOffset = snowLayers * 0.125f;
                    MyRenderer.drawBlockFace(evt.getPoseStack(), vc(), Direction.UP, yOffset, bp, 0f, 0.8f, 0f, 0.3f);
                }
            }

            if (MC.player == null || MC.level == null) return;
            ItemStack heldItem = MC.player.getMainHandItem();
            if (!heldItem.is(BlockRegistrar.SPIDER_FRIENDLY_BARRIER.get().asItem()))
                return;

            BlockPos playerPos = MC.player.blockPosition();
            BlockPos min = playerPos.offset(-10, -10, -10);
            BlockPos max = playerPos.offset(10, 10, 10);

            for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
                if (MC.level.getBlockState(pos).getBlock() instanceof SpiderFriendlyBarrierBlock) {
                    MyRenderer.drawBlockOutline(evt.getPoseStack(), pos, 0.6f);
                }
            }
        } finally {
            com.solegendary.reignofnether.perf.FrameParts.end(fp2);
            com.solegendary.reignofnether.perf.FrameParts.end(fp);
        }
    }

    // maintain a mapping of night sources for easy culling calcs
    private static final int NIGHT_SOURCES_UPDATE_TICKS_MAX = 50;
    private static int nightSourcesUpdateTicks = NIGHT_SOURCES_UPDATE_TICKS_MAX;
    public static ArrayList<Pair<BlockPos, Integer>> nightSourceOrigins = new ArrayList<>();
    // temp circle for stuff like placing a night source building, mousing over a catalyst with sonic boom, etc.
    public static Pair<BlockPos, Integer> tempMouseCircleOrigin = null;
    public static final int VISIBLE_BORDER_ADJ = 2; // shrink a bit so borderlines themselves are safe to walk on

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;

        nightSourcesUpdateTicks -= 1;

        NightSourceAddon nsa0 = null;
        Building building = BuildingClientEvents.getBuildingToPlace();
        if (building != null) {
            nsa0 = building.getActiveAddon(NightSourceAddon.class);
        }
        if (nightSourcesUpdateTicks % 2 == 0) {
            if (nsa0 != null) {
                tempMouseCircleOrigin = new Pair<>(CursorClientEvents.getPreselectedBlockPos(), nsa0.getDefaultNightRange() - VISIBLE_BORDER_ADJ);
            } else if (BuildingClientEvents.getPreselectedBuilding() instanceof SculkCatalystPlacement scp &&
                        CursorClientEvents.getLeftClickAction() == UnitAction.CAST_SONIC_BOOM &&
                        ResearchClient.hasResearch(ProductionItems.RESEARCH_SCULK_AMPLIFIERS) &&
                        MC.player != null && scp.ownerName.equals(MC.player.getName().getString())) {
                tempMouseCircleOrigin = new Pair<>(scp.centrePos, ResearchSculkAmplifiers.SPLIT_BOOM_RANGE);
            } else {
                tempMouseCircleOrigin = null;
            }
        }

        if (nightSourcesUpdateTicks <= 0) {
            nightSourcesUpdateTicks = NIGHT_SOURCES_UPDATE_TICKS_MAX;

            nightSourceOrigins.clear();

            // get list of night source centre:range pairs
            for (BuildingPlacement bpl : BuildingClientEvents.getBuildings()) {
                NightSourceAddon nsa;
                if (!bpl.isExploredClientside || (nsa = bpl.getBuilding().getActiveAddon(NightSourceAddon.class)) == null || nsa.getNightRange(bpl) <= 0) {
                    continue;
                }
                nightSourceOrigins.add(new Pair<>(bpl.centrePos, nsa.getNightRange(bpl) - VISIBLE_BORDER_ADJ));
            }
        }
    }
}
