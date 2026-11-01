package com.solegendary.reignofnether.building;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.hud.HudPanels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the exit arrow (ExitArrow) of a production building: on the placement ghost (following [ / ] rotation) and on
 * your own production buildings while selected or hovered. Never for enemies' buildings or buildings that produce no
 * units, never with the HUD hidden or in clean view. The spot is ProductionPlacement.exitArrowPos - the spawn code's
 * own calls - so it cannot disagree with where units appear. Called from BuildingClientEvents.renderLevel (RTS view,
 * after translucent blocks).
 * <p>
 * The spawn rule's dry run needs a ServerLevel, so the client never runs it: it asks the server
 * (ExitArrowServerboundPacket) for the rule of the ghost's type (kept per type in SPECS; the ghost's cell is then worked
 * out here from the ClientLevel, so it shows only what the player sees) and for the exit cell of each of its own
 * selected / hovered buildings (kept per origin in ARROWS, refreshed once a second). Works the same in single player.
 */
public final class ExitArrowClientEvents {

    private ExitArrowClientEvents() { }

    private static final Minecraft MC = Minecraft.getInstance();
    /** Lift over the ground cell's floor: clear of the top face, so no z-fighting. */
    static final float LIFT = 0.06f;
    /** Bright, team-neutral: warm white fill over a dark edge (team colours are red/blue/green/yellow...). */
    static final float R = 1.0f, G = 0.97f, B = 0.85f, A = 0.9f;
    static final float EDGE_A = 0.6f, EDGE_PAD = 0.12f;

    /** One arrow to draw. */
    private static final class Arrow {
        boolean show;
        float x, y, z, dx, dz;
    }

    /** A building type's spawn rule as the server sent it (has = it spawns units at all). */
    private record Spec(boolean has, boolean indoors, Vec3i offset) { }

    private static final Map<Building, Spec> SPECS = new HashMap<>();
    /** The server's answer per own building, by origin. */
    private static final Map<BlockPos, Arrow> ARROWS = new HashMap<>();
    private static Level seenLevel;
    private static long lastAsk = Long.MIN_VALUE / 2;
    private static final Arrow GHOST = new Arrow();
    private static Building ghostBuilding;
    private static BlockPos ghostOrigin;
    private static Rotation ghostRotation;

    private static final float[] DIR = new float[2];
    private static final float[] PTS = new float[14];
    private static final Arrow[] DRAW = new Arrow[ExitArrowServerboundPacket.MAX_ORIGINS + 1];
    private static final List<BlockPos> ASK = new ArrayList<>();

    public static void render(PoseStack pose, BlockPos ghostOriginPos, boolean ghostShown) {
        if (MC.level == null || MC.player == null || HudPanels.hidden() || HudPanels.cleanView)
            return;
        if (MC.level != seenLevel) {   // another dimension or server: nothing learned applies
            seenLevel = MC.level;
            SPECS.clear();
            ARROWS.clear();
            ghostBuilding = null;
            GHOST.show = false;
            lastAsk = Long.MIN_VALUE / 2;   // the new world's clock may be behind the old one's (review C-11)
        }
        String me = MC.player.getName().getString();
        long now = MC.level.getGameTime();
        int n = 0;
        ASK.clear();
        boolean somethingNew = false;
        Building askType = null;

        Building toPlace = BuildingClientEvents.getBuildingToPlace();
        if (ghostShown && toPlace instanceof ProductionBuilding && ghostOriginPos != null) {
            Spec spec = SPECS.get(toPlace);
            String key = spec == null ? ExitArrow.askKey(toPlace, ReignOfNetherRegistries.BUILDING::getKey) : null;
            ExitArrow.GhostStep step = ExitArrow.ghostStep(spec != null, spec != null && spec.has(), key);
            if (step == ExitArrow.GhostStep.ASK) {
                askType = toPlace;
                somethingNew = true;
            } else if (step == ExitArrow.GhostStep.NONE && spec == null) {
                SPECS.put(toPlace, new Spec(false, false, Vec3i.ZERO));   // a custom building: no rule the server can look up
            } else if (step == ExitArrow.GhostStep.DRAW) {
                Rotation rot = BuildingClientEvents.getBuildingRotation();
                if (toPlace != ghostBuilding || !ghostOriginPos.equals(ghostOrigin) || rot != ghostRotation) {
                    ghostBuilding = toPlace;
                    ghostOrigin = ghostOriginPos;
                    ghostRotation = rot;
                    BuildingPlacement ghost = toPlace.createBuildingPlacement(MC.level, ghostOriginPos, rot, me);
                    fill(GHOST, (ProductionPlacement) ghost, ((ProductionPlacement) ghost).exitNominalPos(MC.level, spec.indoors(), spec.offset()));
                }
                DRAW[n++] = GHOST;
            }
        }
        for (BuildingPlacement b : BuildingClientEvents.getSelectedBuildings())
            somethingNew |= own(b, me);
        BuildingPlacement hovered = BuildingClientEvents.getPreselectedBuilding();
        if (hovered != null && !BuildingClientEvents.getSelectedBuildings().contains(hovered))
            somethingNew |= own(hovered, me);
        for (BlockPos origin : ASK) {
            Arrow a = ARROWS.get(origin);
            if (a != null && a.show)
                DRAW[n++] = a;
        }
        if (ExitArrow.shouldAsk(askType != null || !ASK.isEmpty(), somethingNew, now - lastAsk)) {
            lastAsk = now;
            ExitArrowServerboundPacket.ask(askType, ASK);
        }
        if (n == 0)
            return;

        Vec3 cam = MC.gameRenderer.getMainCamera().getPosition();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();   // an indoor spawn is under the roof: the arrow is an overlay, like a selection box
        RenderSystem.disableCull();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < n; i++) {
            Arrow a = DRAW[i];
            ExitArrow.outline(a.x, a.z, a.dx, a.dz, EDGE_PAD, PTS);
            emit(bb, m, a.y, 0f, 0f, 0f, EDGE_A);
            ExitArrow.outline(a.x, a.z, a.dx, a.dz, 0f, PTS);
            emit(bb, m, a.y + 0.01f, R, G, B, A);
            DRAW[i] = null;
        }
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    /** Shaft (two triangles) and head (one), from the 7-point outline in PTS. */
    private static void emit(BufferBuilder bb, Matrix4f m, float y, float r, float g, float b, float a) {
        tri(bb, m, y, 0, 1, 5, r, g, b, a);
        tri(bb, m, y, 0, 5, 6, r, g, b, a);
        tri(bb, m, y, 2, 3, 4, r, g, b, a);
    }

    private static void tri(BufferBuilder bb, Matrix4f m, float y, int i, int j, int k, float r, float g, float b, float a) {
        bb.vertex(m, PTS[i * 2], y, PTS[i * 2 + 1]).color(r, g, b, a).endVertex();
        bb.vertex(m, PTS[j * 2], y, PTS[j * 2 + 1]).color(r, g, b, a).endVertex();
        bb.vertex(m, PTS[k * 2], y, PTS[k * 2 + 1]).color(r, g, b, a).endVertex();
    }

    /** Your own production building: queue it to be asked about (at most 8). True if the server has not answered for it yet. */
    private static boolean own(BuildingPlacement b, String me) {
        if (!(b instanceof ProductionPlacement) || !me.equals(b.ownerName) || ASK.size() >= ExitArrowServerboundPacket.MAX_ORIGINS
                || ASK.contains(b.originPos))
            return false;
        ASK.add(b.originPos);
        return !ARROWS.containsKey(b.originPos);
    }

    /** Point an arrow at {@code p}, the exit cell of {@code pp}. */
    private static void fill(Arrow a, ProductionPlacement pp, BlockPos p) {
        BlockPos target = pp.getDefaultOutdoorSpawnPoint();
        ExitArrow.walkDir(p.getX() + 0.5, p.getZ() + 0.5, target.getX() + 0.5, target.getZ() + 0.5,
                (pp.minCorner.getX() + pp.maxCorner.getX() + 1) / 2.0, (pp.minCorner.getZ() + pp.maxCorner.getZ() + 1) / 2.0, DIR);
        a.x = p.getX() + 0.5f;
        a.y = p.getY() + LIFT;
        a.z = p.getZ() + 0.5f;
        a.dx = DIR[0];
        a.dz = DIR[1];
        a.show = true;
    }

    /** The server's answer (client thread, ExitArrowClientboundPacket.handle). */
    public static void onAnswer(ExitArrowClientboundPacket answer) {
        if (MC.level == null)
            return;
        ResourceLocation rl = ResourceLocation.tryParse(answer.typeKey());
        if (rl != null && ReignOfNetherRegistries.BUILDING.containsKey(rl))
            SPECS.put(ReignOfNetherRegistries.BUILDING.get(rl), new Spec(answer.hasSpec(), answer.indoors(), answer.offset()));
        for (ExitArrowClientboundPacket.Entry e : answer.entries()) {
            Arrow a = ARROWS.computeIfAbsent(e.origin(), k -> new Arrow());
            a.show = false;
            if (e.show() && BuildingUtils.findBuilding(true, e.origin()) instanceof ProductionPlacement pp)
                fill(a, pp, e.cell());
        }
    }
}
