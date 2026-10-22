
package com.solegendary.reignofnether.healthbars;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.config.DisplaySettings;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class HealthBarClientEvents {

    private static final ResourceLocation GUI_BARS_TEXTURES = ResourceLocation.fromNamespaceAndPath(
                ReignOfNether.MOD_ID, "textures/hud/healthbars.png");

    private static final List<LivingEntity> renderedEntities = new ArrayList<>();
    private static final Minecraft MC = Minecraft.getInstance();

    /**
     * Which units get a bar (Mod Settings -> Display -> Health bars; DisplaySettings.healthBarMode). Clip study
     * Oct 13 (c1, e1): forty units in one river tile drew forty overlapping bars and the fight was unreadable. BAR
     * only shows a bar on a damaged, hovered or selected unit, so: Always = every unit; Damaged (default) = selected,
     * hovered, or hurt - our own hurt units always (we can pull them back), anyone else's only while they are still
     * being hit (HURT_RECENT_TICKS since the last drop in health, tracked client-side in BarState); Selected only =
     * selected or hovered. Buildings are not touched: their bars come from BuildProgressBarClientEvents.
     *
     * The selection and hover sets are rebuilt once per frame into reused open-addressing sets (the pattern from
     * StrategicViewClientEvents), so the per-entity test in RenderLivingEvent.Post allocates nothing.
     */
    // ~3 s: BarStates.tick runs on both PlayerTickEvent phases, so its counter moves twice per game tick
    private static final int HURT_RECENT_TICKS = 120;
    private static final Set<LivingEntity> SELECTED_SCRATCH = new it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<>();
    private static final Set<LivingEntity> PRESELECTED_SCRATCH = new it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet<>();
    private static int barMode = DisplaySettings.BARS_DAMAGED;
    private static String myName = "";

    public enum RenderMode {
        GUI_ICON,
        GUI_PORTRAIT,
        IN_WORLD_ORTHOVIEW,
        IN_WORLD_FIRST_PERSON
    }

    @SubscribeEvent
    public static void entityRender(RenderLivingEvent.Post<? extends LivingEntity, ? extends EntityModel<?>> evt) {
        // a shader pack draws every entity a second time into its shadow map, and that draw fires this event too:
        // the unit was queued twice (its bar drawn twice, doubled alpha) and units off screen but inside the shadow
        // map's reach got bars of their own. Only the view's draw queues a bar.
        if (com.solegendary.reignofnether.postfx.ShaderPacks.renderingShadowPass())
            return;
        prepareRenderInWorld(evt.getEntity());
    }
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        int fp = com.solegendary.reignofnether.perf.FrameParts.begin(com.solegendary.reignofnether.perf.FrameParts.BARS);
        try {
            if (com.solegendary.reignofnether.postfx.ShaderPacks.skipOverlays()) return;   // shader pack shadow map: overlays cast no shadow
            if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
                return;

            Camera camera = MC.gameRenderer.getMainCamera();
            renderBarsInWorld(evt.getPartialTick(), evt.getPoseStack(), camera);
        } finally {
            com.solegendary.reignofnether.perf.FrameParts.end(fp);
        }
    }
    @SubscribeEvent
    public static void playerTick(PlayerTickEvent evt) {
        if (!evt.player.level().isClientSide)
            return;
        BarStates.tick();
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent evt) {
        if (evt.phase != TickEvent.Phase.START)
            return;
        barMode = DisplaySettings.healthBarMode();
        SELECTED_SCRATCH.clear();
        PRESELECTED_SCRATCH.clear();
        if (MC.level == null || MC.player == null) {
            myName = "";
            return;
        }
        myName = MC.player.getName().getString();
        if (barMode != DisplaySettings.BARS_ALWAYS) {
            SELECTED_SCRATCH.addAll(UnitClientEvents.getSelectedUnits());
            PRESELECTED_SCRATCH.addAll(UnitClientEvents.getPreselectedUnits());
        }
    }

    /** The bar rule above, for one living entity this frame; a non-unit mob follows the Damaged rule. */
    private static boolean wantsBar(LivingEntity entity) {
        if (barMode == DisplaySettings.BARS_ALWAYS)
            return true;
        if (SELECTED_SCRATCH.contains(entity) || PRESELECTED_SCRATCH.contains(entity)
                || entity == HudClientEvents.hudSelectedEntity)
            return true;
        if (barMode == DisplaySettings.BARS_SELECTED)
            return false;
        boolean hurt = entity.getHealth() < entity.getMaxHealth();
        if (!hurt)
            return entity.getAbsorptionAmount() > 0;
        if (entity instanceof Unit unit && !myName.isEmpty() && myName.equals(unit.getOwnerName()))
            return true;
        BarState state = BarStates.peekHealth(entity);
        return state != null && BarStates.tickCount() - state.lastHurtTick < HURT_RECENT_TICKS;
    }

    private static boolean shouldShowHealthBar(Entity entity, Minecraft client) {
        // a unit hidden from this viewer (Shade Ranger cloak, Verdant thicket cover) shows no bar, gear or not
        if (entity instanceof Unit && entity.isInvisible() && client.player != null && entity.isInvisibleTo(client.player))
            return false;
        return entity instanceof LivingEntity && !(entity instanceof ArmorStand) &&
                   (!entity.isInvisibleTo(client.player) || entity.isCurrentlyGlowing() || entity.isOnFire() ||
                   entity instanceof Creeper && ((Creeper) entity).isPowered() ||
                   hasAnyEquippedItem(entity)) &&
                 entity != client.player &&
                !entity.isSpectator();
    }

    private static boolean hasAnyEquippedItem(Entity entity) {
        for (var stack : entity.getAllSlots())
            if (!stack.isEmpty())
                return true;
        return false;
    }

    private static void prepareRenderInWorld(LivingEntity entity) {
        Minecraft MC = Minecraft.getInstance();

        if (!shouldShowHealthBar(entity, MC))
            return;

        // the state is kept for every visible unit even when no bar is drawn, so the last-hit tick is known
        BarStates.getState(entity, BarState.BarStateType.HEALTH);
        if (!wantsBar(entity))
            return;
        if (entity.getAbsorptionAmount() > 0)
            BarStates.getState(entity, BarState.BarStateType.ABSORB);

        renderedEntities.add(entity);
    }

    private static void renderBarsInWorld(float partialTick, PoseStack matrix, Camera camera) {
        Minecraft MC = Minecraft.getInstance();

        if (camera == null)
            camera = MC.getEntityRenderDispatcher().camera;

        if (camera == null) {
            renderedEntities.clear();
            return;
        }

        if (renderedEntities.isEmpty())
            return;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE,
                GL11.GL_ZERO);

        for (LivingEntity entity : renderedEntities) {
            float scaleToGui = 0.025f;
            boolean sneaking = entity.isCrouching();
            float height = entity.getBbHeight() + 0.6F - (sneaking ? 0.25F : 0.0F);

            double x = Mth.lerp((double) partialTick, entity.xo, entity.getX());
            double y = Mth.lerp((double) partialTick, entity.yo, entity.getY());
            double z = Mth.lerp((double) partialTick, entity.zo, entity.getZ());

            Vec3 camPos = camera.getPosition();
            double camX = camPos.x();
            double camY = camPos.y();
            double camZ = camPos.z();

            matrix.pushPose();
            matrix.translate(x - camX, (y + height) - camY, z - camZ);
            matrix.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
            matrix.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
            matrix.scale(-scaleToGui, -scaleToGui, scaleToGui);

            // calculate bar width based in entity max health (1hp : 2px)
            int barWidth;
            if (entity instanceof Unit)
                barWidth = (int) entity.getMaxHealth();
            else
                barWidth = (int) entity.getMaxHealth() * 2;

            barWidth = Math.min(barWidth, 120);
            barWidth = Math.max(barWidth, 20);

            renderForEntity(matrix, entity, 0, 0, barWidth,
                    OrthoviewClientEvents.isEnabled() ? RenderMode.IN_WORLD_ORTHOVIEW : RenderMode.IN_WORLD_FIRST_PERSON);

            renderAbsorbForEntity(matrix, entity, 0, 0, barWidth,
                    OrthoviewClientEvents.isEnabled() ? RenderMode.IN_WORLD_ORTHOVIEW : RenderMode.IN_WORLD_FIRST_PERSON);
            // veterancy (BAR): one gold notch above the bar per rank, read from the synced max-health modifier -
            // the first visible half of "smart fighting: baseline + veterancy" (claude/combat-reactions.md)
            int rank = com.solegendary.reignofnether.unit.VeterancyServerEvents.getSyncedRank(entity);
            if (rank > 0 && entity instanceof Unit)
                renderVeterancyNotches(matrix, barWidth, rank,
                        OrthoviewClientEvents.isEnabled() ? RenderMode.IN_WORLD_ORTHOVIEW : RenderMode.IN_WORLD_FIRST_PERSON);
            // a braced shield unit (unit/reactions/Brace): a steel-blue rim round the bar for the duration
            if (entity instanceof Unit && com.solegendary.reignofnether.unit.reactions.ReactionClientEvents.isBraced(entity.getId()))
                renderBraceRim(matrix, barWidth,
                        OrthoviewClientEvents.isEnabled() ? RenderMode.IN_WORLD_ORTHOVIEW : RenderMode.IN_WORLD_FIRST_PERSON);

            matrix.popPose();
        }

        RenderSystem.disableBlend();

        renderedEntities.clear();
    }

    public static void renderForEntity(PoseStack matrix, LivingEntity entity, double x, double y,
                                       float width, RenderMode renderMode) {
        BarState state = BarStates.getState(entity, BarState.BarStateType.HEALTH);

        float percent = Math.min(1, Math.min(state.amount, entity.getMaxHealth()) / entity.getMaxHealth());
        float percent2 = Math.min(state.previousAmountDisplay, entity.getMaxHealth()) / entity.getMaxHealth();

        render(matrix, percent, percent2, x, y, width, renderMode, BarState.BarStateType.HEALTH);
    }

    public static void renderAbsorbForEntity(PoseStack matrix, LivingEntity entity, double x, double y,
                                       float width, RenderMode renderMode) {
        if (entity.getAbsorptionAmount() > 0) {
            BarState state = BarStates.getState(entity, BarState.BarStateType.ABSORB);

            float percent = Math.min(1, Math.min(state.amount, MiscUtil.getMaxAbsorptionAmount(entity)) / MiscUtil.getMaxAbsorptionAmount(entity));
            float percent2 = Math.min(state.previousAmountDisplay, MiscUtil.getMaxAbsorptionAmount(entity)) / MiscUtil.getMaxAbsorptionAmount(entity);

            render(matrix, percent, percent2, x, y, width, renderMode, BarState.BarStateType.ABSORB,
                    0, 1f, 1f,
                    0, 0.5f, 0.5f
            );
        }
    }

    public static void renderManaForEntity(PoseStack matrix, HeroUnit heroUnit, double x, double y,
                                           float width, RenderMode renderMode) {
        BarState state = BarStates.getState((LivingEntity) heroUnit, BarState.BarStateType.MANA);

        float percent = Math.min(1, Math.min(state.amount, heroUnit.getMaxMana()) / heroUnit.getMaxMana());
        float percent2 = Math.min(state.previousAmountDisplay, heroUnit.getMaxMana()) / heroUnit.getMaxMana();

        render(matrix, percent, percent2, x, y, width, renderMode, BarState.BarStateType.MANA,
                0.3f, 0.3f, 1f,
                0.15f, 0.15f, 0.5f
        );
    }

    public static void renderForBuilding(PoseStack matrix, BuildingPlacement building, double x, double y,
                                         float width, RenderMode renderMode) {
        float health = building.getHealth();
        if (health > building.getMaxHealth())
            health = building.getMaxHealth();
        float percent = health / (float) building.getMaxHealth();
        render(matrix, percent, percent, x, y, width, renderMode, BarState.BarStateType.HEALTH);
    }

    /** A plain progress bar in one colour on the usual grey track (construction / production progress). */
    public static void renderProgress(PoseStack matrix, float percent, double x, double y, float width,
                                      RenderMode renderMode, float r, float g, float b) {
        percent = Math.max(0, Math.min(1, percent));
        render(matrix, percent, percent, x, y, width, renderMode, BarState.BarStateType.HEALTH,
                r, g, b, r * 0.5f, g * 0.5f, b * 0.5f);
    }

    private static void render(PoseStack matrix, float percent, float percent2, double x, double y,
                               float width, RenderMode renderMode, BarState.BarStateType barStateType) {
        // base colour on percentage health remaining (green @ 100%, yellow @ 50%, red @ 0%)
        float r = Math.max(0, Math.min(1, 2-percent*2));
        float g = Math.max(0, Math.min(1, percent*2));
        float b = 0;
        float r2 = r * 0.5f;
        float g2 = g * 0.5f;
        float b2 = b * 0.5f;
        render(matrix, percent, percent2, x, y, width, renderMode, barStateType, r, g, b, r2, g2, b2);
    }

    private static void render(PoseStack matrix, float percent, float percent2, double x, double y,
                               float width, RenderMode renderMode, BarState.BarStateType barStateType, float r, float g, float b, float r2, float g2, float b2) {
        int zOffset = 0;
        Matrix4f m4f = matrix.last().pose();
        if (!(barStateType == BarState.BarStateType.ABSORB && (renderMode == RenderMode.IN_WORLD_ORTHOVIEW || renderMode == RenderMode.IN_WORLD_FIRST_PERSON)))
            drawBar(m4f, x, y, width, 1, 0.35f,0.35f,0.35f, zOffset++, renderMode);
        drawBar(m4f, x, y, width, percent2, r2,g2,b2, zOffset++, renderMode);
        drawBar(m4f, x, y, width, percent, r,g,b, zOffset, renderMode);
    }

    /** The Brace rim: a 2-px steel-blue frame round the whole bar (the reaction's one visible sign until the model layer). */
    static void renderBraceRim(PoseStack matrix, float barWidth, RenderMode renderMode) {
        float h = renderMode == RenderMode.IN_WORLD_FIRST_PERSON ? 6f : 10f;
        float half = barWidth / 2, t = 2f;
        Matrix4f m4f = matrix.last().pose();
        float r = 0.55f, g = 0.82f, b = 1.0f;
        drawQuad(m4f, -half - t, -t, barWidth + 2 * t, t, r, g, b, -0.3f);          // top
        drawQuad(m4f, -half - t, h, barWidth + 2 * t, t, r, g, b, -0.3f);           // bottom
        drawQuad(m4f, -half - t, 0, t, h, r, g, b, -0.3f);                          // left
        drawQuad(m4f, half, 0, t, h, r, g, b, -0.3f);                               // right
    }

    /** Gold notches above the bar's left end, one per veteran rank (1-3), sized to the bar's own height. */
    static void renderVeterancyNotches(PoseStack matrix, float barWidth, int rank, RenderMode renderMode) {
        // eyes 08_box_select (a3de2d3): 4-unit notches were ~2 screen px at the default zoom - unreadable. A notch is
        // now as tall as the bar and 8 wide (three of them cover 28 of a 50-wide bar), with a dark backing so the
        // gold reads on grass and sand alike
        float h = renderMode == RenderMode.IN_WORLD_FIRST_PERSON ? 5f : 8f;   // the bar is 6 / 10 tall
        float w = 8f;
        float gap = 2f;
        float half = barWidth / 2;
        Matrix4f m4f = matrix.last().pose();
        for (int i = 0; i < rank; i++) {
            float x0 = -half + i * (w + gap);
            drawQuad(m4f, x0 - 1f, -h - 3f, w + 2f, h + 2f, 0.12f, 0.1f, 0.06f, -0.2f);   // backing
            drawQuad(m4f, x0, -h - 2f, w, h, 1.0f, 0.85f, 0.42f, -0.3f);                 // the notch (sun gold)
        }
    }

    /**
     * A flat quad of one exact colour (x/y in bar space; +y is down the bar). Untextured on purpose: the first
     * version tinted a texel of the bar strip, and the eyes still (run for 7f4fee9) showed the "sun gold" as a muddy
     * bronze - the strip's grey multiplied the colour down.
     */
    private static void drawQuad(Matrix4f m4f, float x0, float y0, float w, float h, float r, float g, float b, float z) {
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder buffer = tessellator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(m4f, x0, y0, z).color(r, g, b, 1f).endVertex();
        buffer.vertex(m4f, x0, y0 + h, z).color(r, g, b, 1f).endVertex();
        buffer.vertex(m4f, x0 + w, y0 + h, z).color(r, g, b, 1f).endVertex();
        buffer.vertex(m4f, x0 + w, y0, z).color(r, g, b, 1f).endVertex();
        tessellator.end();
    }

    private static void drawBar(Matrix4f matrix4f, double x, double y, float width, float percent,
                                float r, float g, float b, int zOffset, RenderMode renderMode) {
        float c = 0.00390625F;
        int u = 0;
        int v = 6 * 5 * 2 + 5;
        int uw = Mth.ceil(92 * percent);
        int vh = 5;

        double size = percent * width;
        double h = 10;

        if (renderMode == RenderMode.GUI_ICON)
            h = 4;
        else if (renderMode == RenderMode.GUI_PORTRAIT)
            h = 12;
        else if (renderMode == RenderMode.IN_WORLD_FIRST_PERSON)
            h = 6;
        else if (renderMode == RenderMode.IN_WORLD_ORTHOVIEW)
            h = 10;

        RenderSystem.setShaderColor(r, g, b, 1);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, GUI_BARS_TEXTURES);
        RenderSystem.enableBlend();

        float half = width / 2;

        float zOffsetAmount = renderMode == RenderMode.IN_WORLD_FIRST_PERSON || renderMode == RenderMode.IN_WORLD_ORTHOVIEW ? -0.1F : 0.1F;

        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder buffer = tessellator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        buffer.vertex(matrix4f, (float) (-half + x), (float) y, zOffset * zOffsetAmount)
                .uv(u * c, v * c).endVertex();
        buffer.vertex(matrix4f, (float) (-half + x), (float) (h + y), zOffset * zOffsetAmount)
                .uv(u * c, (v + vh) * c).endVertex();
        buffer.vertex(matrix4f, (float) (-half + size + x), (float) (h + y), zOffset * zOffsetAmount)
                .uv((u + uw) * c, (v + vh) * c).endVertex();
        buffer.vertex(matrix4f, (float) (-half + size + x), (float) y, zOffset * zOffsetAmount)
                .uv(((u + uw) * c), v * c).endVertex();
        tessellator.end();

        // reset color
        RenderSystem.setShaderColor(1,1,1,1);
    }
}