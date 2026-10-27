package com.solegendary.reignofnether.building;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.hud.HudPanels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws the exit arrow (ExitArrow) of a production building: on the placement ghost (following [ / ] rotation) and on
 * your own production buildings while selected or hovered. Never for enemies' buildings or buildings that produce no
 * units, never with the HUD hidden or in clean view. The spot is ProductionPlacement.exitArrowPos - the spawn code's
 * own calls - so it cannot disagree with where units appear; it is recomputed every {@link #REFRESH_TICKS}, not per
 * frame. Called from BuildingClientEvents.renderLevel (RTS view, after translucent blocks).
 * <p>
 * The dry run that finds a building's spawn rule needs a ServerLevel: on a client joined to a remote server there is
 * none, and the arrow is not drawn there (logged once).
 */
public final class ExitArrowClientEvents {

    private ExitArrowClientEvents() { }

    private static final Minecraft MC = Minecraft.getInstance();
    static final int REFRESH_TICKS = 20;
    /** Lift over the ground cell's floor: clear of the top face, so no z-fighting. */
    static final float LIFT = 0.06f;
    /** Bright, team-neutral: warm white fill over a dark edge (team colours are red/blue/green/yellow...). */
    static final float R = 1.0f, G = 0.97f, B = 0.85f, A = 0.9f;
    static final float EDGE_A = 0.6f, EDGE_PAD = 0.12f;

    /** One arrow, cached until {@code until} (game time). */
    private static final class Arrow {
        long until = Long.MIN_VALUE;
        boolean show;
        float x, y, z, dx, dz;
    }

    private static final Map<BuildingPlacement, Arrow> PLACED = new WeakHashMap<>();
    private static final Arrow GHOST = new Arrow();
    private static Building ghostBuilding;
    private static BlockPos ghostOrigin;
    private static Rotation ghostRotation;

    private static final float[] DIR = new float[2];
    private static final float[] PTS = new float[14];
    private static final Arrow[] DRAW = new Arrow[8];
    private static boolean warnedRemote = false;

    public static void render(PoseStack pose, BlockPos ghostOriginPos, boolean ghostShown) {
        if (MC.level == null || MC.player == null || HudPanels.hidden() || HudPanels.cleanView)
            return;
        ServerLevel sl = serverLevel();
        if (sl == null)
            return;
        String me = MC.player.getName().getString();
        long now = MC.level.getGameTime();
        int n = 0;

        Building toPlace = BuildingClientEvents.getBuildingToPlace();
        if (ghostShown && toPlace instanceof ProductionBuilding && ghostOriginPos != null) {
            Rotation rot = BuildingClientEvents.getBuildingRotation();
            if (toPlace != ghostBuilding || !ghostOriginPos.equals(ghostOrigin) || rot != ghostRotation) {
                ghostBuilding = toPlace;
                ghostOrigin = ghostOriginPos;
                ghostRotation = rot;
                BuildingPlacement ghost = toPlace.createBuildingPlacement(MC.level, ghostOriginPos, rot, me);
                compute(GHOST, ghost, sl, false, now);
            }
            if (GHOST.show)
                DRAW[n++] = GHOST;
        }
        for (BuildingPlacement b : BuildingClientEvents.getSelectedBuildings()) {
            if (n >= DRAW.length - 1)
                break;
            Arrow a = placed(b, me, sl, now);
            if (a != null)
                DRAW[n++] = a;
        }
        BuildingPlacement hovered = BuildingClientEvents.getPreselectedBuilding();
        if (hovered != null && !BuildingClientEvents.getSelectedBuildings().contains(hovered)) {
            Arrow a = placed(hovered, me, sl, now);
            if (a != null)
                DRAW[n++] = a;
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

    /** Your own production building's arrow, refreshed every REFRESH_TICKS; null if it shows none. */
    private static Arrow placed(BuildingPlacement b, String me, ServerLevel sl, long now) {
        if (!(b instanceof ProductionPlacement) || !me.equals(b.ownerName))
            return null;
        Arrow a = PLACED.get(b);
        if (a == null) {
            a = new Arrow();
            PLACED.put(b, a);
        }
        if (now >= a.until || now < a.until - REFRESH_TICKS)
            compute(a, b, sl, true, now);
        return a.show ? a : null;
    }

    private static void compute(Arrow a, BuildingPlacement b, ServerLevel sl, boolean safe, long now) {
        a.until = now + REFRESH_TICKS;
        a.show = false;
        if (!(b instanceof ProductionPlacement pp))
            return;
        ProductionPlacement.ExitSpec spec = pp.exitSpec(sl);
        if (spec == null)
            return;
        BlockPos p = pp.exitArrowPos(sl, spec, safe);
        if (p == null)
            return;
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

    /** The integrated server's level for this dimension (single player, or the host of a LAN game); null otherwise. */
    private static ServerLevel serverLevel() {
        MinecraftServer server = MC.getSingleplayerServer();
        ServerLevel sl = server == null ? null : server.getLevel(MC.level.dimension());
        if (sl == null && !warnedRemote) {
            warnedRemote = true;
            ReignOfNether.LOGGER.warn("Exit arrows are not drawn: this client has no integrated server to find spawn points with");
        }
        return sl;
    }
}
