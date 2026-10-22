package com.solegendary.reignofnether.orthoview;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.DefaultedVertexConsumer;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.postfx.ShaderPacks;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit silhouettes through canopies and walls in the RTS view (lovish Oct 22, "all three": trees stay solid, units under
 * them show through - StarCraft II / Age of Empires IV style). A unit hidden behind leaves or blocks is drawn, ONLY where
 * it is hidden, as a flat colour with a bright rim: your own units in your team colour, allies cyan, enemies red.
 * <p>
 * How, and why this way:
 * <ol>
 * <li><b>Which units</b> (client tick, {@link #isUnderCover}): a ray from each unit's chest and head toward the camera
 * (the RTS camera is orthographic: one direction for the whole view) walks the block grid (CanopyRules.rayOccluded,
 * with the height map skipping open sky). Round-robin, every unit re-tested at least every 4 ticks, ~2 short rays each.
 * Only units seen by the local player - fog of war, cloaks and thickets as everywhere else - are ever tested or drawn,
 * so a silhouette never gives away a hidden enemy.</li>
 * <li><b>The capture</b> (LevelRendererSilhouetteMixin round LevelRenderer.renderEntity): while such a unit's own
 * renderer draws it, every textured body part it writes is copied in its flat colour into one batch per texture - no
 * second pose, no layers run twice (the round-1 redraw cost ~1.7 ms per unit on CI's software renderer).</li>
 * <li><b>The draw</b> (RenderLevelStageEvent AFTER_ENTITIES): one draw per texture into our own colour target, whose
 * depth attachment IS the main depth texture, as CanopyRenderTypes.silhouette: flat colour, the body texture's
 * cut-outs, depth test GREATER and no depth write. Only the pixels with a leaf or wall in front survive; a visible
 * unit's pixels fail the test. Only the covered units' screen rectangle is cleared and composited. Under Oculus this works because binding a
 * RenderTarget that is not the main one makes Oculus step aside for that draw (its RenderTarget mixin - the same path
 * vanilla's glowing outline uses with a pack on), and Oculus' gbuffers share that main depth texture.</li>
 * <li><b>The composite</b> ({@link #composite}, called from GameRendererSilhouetteMixin right after
 * GameRenderer.renderLevel returns): a pass over that rectangle blends the target over the finished picture - a translucent
 * fill plus an opaque rim. After renderLevel means after a shader pack's composite and final passes (which would
 * otherwise paint over it) and after the world look grade (WorldLookPass), and before the HUD and strategic icons.</li>
 * </ol>
 * Not drawn in a shader pack's shadow pass (Oculus fires the level stages a second time for it - see ShaderPacks), nor
 * in strategic view for units shown as icons. On by default; Mod Settings -> Display -> "Unit silhouettes" (client
 * config {@code unit_silhouettes}). Its cost shows as the {@code canopy} part of the [FrameParts] line.
 */
@OnlyIn(Dist.CLIENT)
public final class CanopySilhouettes {

    private CanopySilhouettes() {
    }

    private static final Minecraft MC = Minecraft.getInstance();
    private static final ResourceLocation SILHOUETTE_SHADER = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "bat_silhouette");
    private static final ResourceLocation COMPOSITE_SHADER = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "bat_silhouette_composite");

    /** A unit found under cover stays "under cover" this long without a new hit: the next test is at most 4 ticks away. */
    private static final int COVER_HOLD_TICKS = 6;
    /** A ray gives up once it has risen this far above the unit: no canopy or wall in a match is taller. */
    private static final double MAX_RISE = 32;
    private static final float FILL_ALPHA = 0.42f;
    private static final float RIM_ALPHA = 0.95f;

    private static ShaderInstance silhouetteShader = null;
    private static ShaderInstance compositeShader = null;
    private static Uniform outSizeU, fillAlphaU, rimAlphaU, rimPxU;
    private static TextureTarget target = null;
    private static boolean drawnThisFrame = false;
    private static boolean failed = false;

    private static final Int2IntOpenHashMap coverUntil = new Int2IntOpenHashMap();   // entity id -> last tick of cover
    static {
        coverUntil.defaultReturnValue(-1);
    }
    private static int tick = 0;
    private static int nextUnit = 0;

    // ---- settings and queries ---------------------------------------------------------------------------------------

    /** The client config toggle (default on). */
    public static boolean enabled() {
        try {
            return ReignOfNetherClientConfigs.UNIT_SILHOUETTES.get();
        } catch (RuntimeException e) {   // the config is not loaded yet
            return true;
        }
    }

    /** Is this unit hidden behind leaves or blocks from the RTS camera (as of the last few ticks)? */
    public static boolean isUnderCover(LivingEntity e) {
        return coverUntil.get(e.getId()) >= tick;
    }

    static ShaderInstance silhouetteShader() {
        return silhouetteShader;
    }

    /** Seen by the local player: fog of war, cloaks and cover (thickets) exactly as the unit's own draw decides. */
    private static boolean seenByMe(LivingEntity e) {
        return !e.isRemoved() && e.isAlive() && !e.isInvisible() && FogOfWarClientEvents.isInBrightChunk(e)
                && !UnitClientEvents.isHiddenFromLocalPlayer(e);
    }

    // ---- shaders (mod bus, from ClientModEvents) ------------------------------------------------------------------

    public static void registerShaders(RegisterShadersEvent evt) {
        silhouetteShader = null;
        compositeShader = null;
        failed = false;
        try {
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), SILHOUETTE_SHADER,
                    DefaultVertexFormat.POSITION_COLOR_TEX), s -> silhouetteShader = s);
            evt.registerShader(new ShaderInstance(evt.getResourceProvider(), COMPOSITE_SHADER,
                    DefaultVertexFormat.POSITION), s -> {
                compositeShader = s;
                outSizeU = s.getUniform("OutSize");
                fillAlphaU = s.getUniform("FillAlpha");
                rimAlphaU = s.getUniform("RimAlpha");
                rimPxU = s.getUniform("RimPx");
            });
        } catch (Exception e) {   // a missing or broken file, a GLSL error: never take the resource reload down
            ReignOfNether.LOGGER.warn("Unit silhouettes: shaders did not load, silhouettes are off: {}", e.toString());
        }
    }

    // ---- which units are under cover (client tick) ------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        tick++;
        ClientLevel level = MC.level;
        boolean wanted = enabled() || OrthoviewClientEvents.hideLeavesMethod == OrthoviewClientEvents.LeafHideMethod.FADE;
        if (level == null || !OrthoviewClientEvents.isEnabled() || !wanted) {
            if (!coverUntil.isEmpty())
                coverUntil.clear();
            return;
        }
        List<LivingEntity> units = UnitClientEvents.getAllUnits();
        int n = units.size();
        if (n == 0)
            return;
        Vector3f look = MC.gameRenderer.getMainCamera().getLookVector();
        double dx = -look.x(), dy = -look.y(), dz = -look.z();
        if (dy <= 0.05)
            return;   // a level or upward view (first person leftovers): no canopy test makes sense
        // every unit at least every 4 ticks: a quarter of them per tick, never fewer than 48
        int budget = Math.min(n, Math.max(48, (n + 3) / 4));
        if (nextUnit >= n)
            nextUnit = 0;
        for (int i = 0; i < budget; i++) {
            LivingEntity e = units.get((nextUnit + i) % n);
            if (!seenByMe(e)) {
                coverUntil.remove(e.getId());
                continue;
            }
            if (covered(level, e, dx, dy, dz))
                coverUntil.put(e.getId(), tick + COVER_HOLD_TICKS);
            else
                coverUntil.remove(e.getId());
        }
        nextUnit = (nextUnit + budget) % n;
        if (tick % 100 == 0) {
            final int now = tick;
            coverUntil.int2IntEntrySet().removeIf(en -> en.getIntValue() < now);
        }
    }

    private static final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

    private static boolean covered(ClientLevel level, LivingEntity e, double dx, double dy, double dz) {
        CanopyRules.Voxels voxels = (x, y, z) -> {
            // nothing that blocks motion this high in the column (leaves do block motion): open sky, no lookup
            if (y >= level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z))
                return false;
            BlockState s = level.getBlockState(probe.set(x, y, z));
            return !s.isAir() && (LeafFade.isFadeable(s) || s.canOcclude());
        };
        double h = e.getBbHeight();
        return CanopyRules.rayOccluded(e.getX(), e.getY() + h * 0.5, e.getZ(), dx, dy, dz, MAX_RISE, voxels)
                || CanopyRules.rayOccluded(e.getX(), e.getY() + h * 0.92, e.getZ(), dx, dy, dz, MAX_RISE, voxels);
    }

    // ---- capture: the covered units' own draw, teed (LevelRendererSilhouetteMixin) ---------------------------------
    //
    // Round 1 drew every covered unit a second time through its renderer: on CI's software renderer 41 units cost
    // ~70 ms a frame (~1.7 ms each - the posing, the layers, the events all ran twice). Now the vertices the unit's own
    // draw already produces are copied as they are written (what vanilla's glowing outline does with
    // OutlineBufferSource): no second pose, no layers run twice, one draw per texture for all covered units.

    /** Silhouettes drawn per frame at most; the rest of a huge covered crowd is still visible as its own models. */
    public static final int MAX_SILHOUETTES = 160;
    /** Units shorter than this on screen (pixels) get no silhouette: a smudge, not a shape. */
    private static final float MIN_PX = 6f;

    /** One texture's captured vertices this frame, drawn as one silhouette batch. */
    private static final class Capture {
        final RenderType type;
        final BufferBuilder builder = new BufferBuilder(8192);
        final FlatColour flat = new FlatColour();
        boolean building = false;

        Capture(ResourceLocation texture) {
            this.type = CanopyRenderTypes.silhouette(texture);
            this.flat.delegate = builder;
        }
    }

    private static final Map<ResourceLocation, Capture> captures = new HashMap<>();
    private static final List<LivingEntity> captured = new ArrayList<>();
    private static final Map<String, Integer> ownerRgb = new HashMap<>();
    private static boolean frameOpen = false;    // AFTER_SKY .. AFTER_ENTITIES of a view frame with silhouettes on
    private static LivingEntity capturing = null;
    private static int captureRgb = 0xFFFFFF;
    private static float pxPerBlock = 0f;        // the RTS view's scale, from the last frame's projection

    /** LevelRenderer.renderEntity HEAD: is this entity's draw to be copied into the silhouettes? */
    public static void beginEntity(Entity entity) {
        capturing = null;
        if (!frameOpen || !(entity instanceof LivingEntity le) || captured.size() >= MAX_SILHOUETTES)
            return;
        if (!isUnderCover(le) || !seenByMe(le))
            return;
        if (pxPerBlock > 0f && le.getBbHeight() * pxPerBlock < MIN_PX)
            return;
        if (ShaderPacks.renderingShadowPass())
            return;   // the pack's shadow map draws entities through renderEntity too
        capturing = le;
        captureRgb = colourOf(le);
        captured.add(le);
    }

    /** LevelRenderer.renderEntity RETURN. */
    public static void endEntity() {
        capturing = null;
    }

    /**
     * The buffer source the entity's renderer draws into: the level's own, and for a covered unit also our capture -
     * every textured body part (render types that would draw a glowing outline: not text, glints or glow layers) is
     * teed into its texture's silhouette batch in the unit's colour.
     */
    public static MultiBufferSource wrap(MultiBufferSource source) {
        if (capturing == null)
            return source;
        final int rgb = captureRgb;
        return type -> {
            VertexConsumer base = source.getBuffer(type);
            if (type.mode() != VertexFormat.Mode.QUADS || type.outline().isEmpty())
                return base;
            ResourceLocation texture = CanopyRenderTypes.cutoutTexture(type);
            if (texture == null)
                return base;
            Capture c = captures.get(texture);
            if (c == null) {
                c = new Capture(texture);
                captures.put(texture, c);
            }
            if (!c.building) {
                c.builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
                c.building = true;
            }
            c.flat.setRgb(rgb);
            return VertexMultiConsumer.create(c.flat, base);
        };
    }

    private static final Matrix4f clip = new Matrix4f();
    private static final Vector4f corner = new Vector4f();
    // the screen rectangle (framebuffer pixels, GL's bottom-left origin) the silhouettes of this frame cover
    private static int rectX0, rectY0, rectX1, rectY1;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        RenderLevelStageEvent.Stage stage = evt.getStage();
        if (stage != RenderLevelStageEvent.Stage.AFTER_SKY && stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES)
            return;
        // a shader pack's shadow map: silhouettes belong to the view only (Oculus fires the stages again for it)
        if (ShaderPacks.renderingShadowPass())
            return;
        if (stage == RenderLevelStageEvent.Stage.AFTER_SKY) {
            discardCaptures();
            frameOpen = !failed && silhouetteShader != null && compositeShader != null && !coverUntil.isEmpty()
                    && MC.level != null && enabled() && OrthoviewClientEvents.isEnabled()
                    && CanopyRenderTypes.texturesReadable();
            return;
        }
        if (!frameOpen)
            return;
        frameOpen = false;
        int fp = com.solegendary.reignofnether.perf.FrameParts.begin(com.solegendary.reignofnether.perf.FrameParts.CANOPY);
        try {
            if (!captured.isEmpty())
                draw(evt);
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            StackTraceElement[] st = e.getStackTrace();
            ReignOfNether.LOGGER.warn("Unit silhouettes switched off for this session: {} at {}", e.toString(),
                    st.length > 0 ? st[0] : "?");
        } finally {
            discardCaptures();
            com.solegendary.reignofnether.perf.FrameParts.end(fp);
        }
    }

    /** Anything captured but not drawn (an aborted frame) is dropped, ready for the next frame. */
    private static void discardCaptures() {
        for (Capture c : captures.values())
            if (c.building) {
                c.building = false;
                BufferBuilder.RenderedBuffer rb = c.builder.endOrDiscardIfEmpty();
                if (rb != null)
                    rb.release();
            }
        captured.clear();
        ownerRgb.clear();
        capturing = null;
    }

    private static void draw(RenderLevelStageEvent evt) {
        RenderTarget main = MC.getMainRenderTarget();
        if (main.width <= 0 || main.height <= 0)
            return;
        Matrix4f proj = evt.getProjectionMatrix();
        // ortho: one block is m11 * height / 2 pixels tall on screen (the RTS camera's scale for MIN_PX next frame)
        pxPerBlock = Math.abs(proj.m11()) * main.height / 2f;
        if (!screenRect(evt, main))
            return;
        if (!prepareTarget(main))
            return;

        // our target: Oculus sees a non-main target bound and leaves our shaders alone (see the class comment)
        target.bindWrite(true);
        // only the covered units' rectangle is cleared, drawn and composited: a squad under one tree is a few
        // thousand pixels of work, not the whole screen
        RenderSystem.enableScissor(rectX0, rectY0, rectX1 - rectX0, rectY1 - rectY0);
        RenderSystem.clearColor(0f, 0f, 0f, 0f);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        try {
            VertexSorting sorting = RenderSystem.getVertexSorting();
            for (Capture c : captures.values())
                if (c.building) {
                    c.building = false;
                    c.type.end(c.builder, sorting);   // one draw per texture
                }
            drawnThisFrame = true;
        } finally {
            RenderSystem.disableScissor();
            // back to the main target, as vanilla does after its glowing-outline pass
            main.bindWrite(false);
        }
    }

    /** The covered units' screen rectangle (their bounding boxes projected), widened for the rim; false if off screen. */
    private static boolean screenRect(RenderLevelStageEvent evt, RenderTarget main) {
        clip.set(evt.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(evt.getPoseStack().last().pose());
        Vec3 cam = evt.getCamera().getPosition();
        float pt = evt.getPartialTick();
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (LivingEntity e : captured) {
            double ox = Mth.lerp(pt, e.xOld, e.getX()) - e.getX() - cam.x;
            double oy = Mth.lerp(pt, e.yOld, e.getY()) - e.getY() - cam.y;
            double oz = Mth.lerp(pt, e.zOld, e.getZ()) - e.getZ() - cam.z;
            net.minecraft.world.phys.AABB b = e.getBoundingBox().inflate(0.75);   // weapons and capes reach past it
            for (int i = 0; i < 8; i++) {
                corner.set((float) (((i & 1) == 0 ? b.minX : b.maxX) + ox), (float) (((i & 2) == 0 ? b.minY : b.maxY) + oy),
                        (float) (((i & 4) == 0 ? b.minZ : b.maxZ) + oz), 1f).mul(clip);
                if (corner.w <= 0f)
                    continue;
                float sx = (corner.x / corner.w * 0.5f + 0.5f) * main.width;
                float sy = (corner.y / corner.w * 0.5f + 0.5f) * main.height;
                minX = Math.min(minX, sx);
                minY = Math.min(minY, sy);
                maxX = Math.max(maxX, sx);
                maxY = Math.max(maxY, sy);
            }
        }
        int margin = rimPx(main) + 2;
        rectX0 = Mth.clamp((int) Math.floor(minX) - margin, 0, main.width);
        rectY0 = Mth.clamp((int) Math.floor(minY) - margin, 0, main.height);
        rectX1 = Mth.clamp((int) Math.ceil(maxX) + margin, 0, main.width);
        rectY1 = Mth.clamp((int) Math.ceil(maxY) + margin, 0, main.height);
        return rectX1 > rectX0 && rectY1 > rectY0;
    }

    private static int rimPx(RenderTarget main) {
        // a rim of ~1 px at 1080p, 2 px at 1440p and 4K: the same weight on screen
        return Math.max(1, Math.round(main.height / 1080f));
    }

    /** Our colour target at the main target's size, with the main depth texture as its depth attachment. */
    private static boolean prepareTarget(RenderTarget main) {
        if (target == null)
            target = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
        else if (target.width != main.width || target.height != main.height)
            target.resize(main.width, main.height, Minecraft.ON_OSX);
        // attached every frame: the main depth texture is recreated on a resize, or by a shader pack changing its format
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.frameBufferId);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D,
                main.getDepthTextureId(), 0);
        int status = GlStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            failed = true;
            ReignOfNether.LOGGER.warn("Unit silhouettes switched off for this session: the target with the main depth "
                    + "attached is incomplete (status 0x{})", Integer.toHexString(status));
            main.bindWrite(false);
            return false;
        }
        return true;
    }

    private static int colourOf(LivingEntity e) {
        Relationship rel = UnitClientEvents.getPlayerToEntityRelationship(e);
        if (rel != Relationship.OWNED || !(e instanceof Unit u))
            return CanopyRules.silhouetteRgb(rel, 0xFFFFFF);
        String owner = u.getOwnerName();
        Integer rgb = ownerRgb.get(owner);
        if (rgb == null) {
            rgb = CanopyRules.silhouetteRgb(rel, PlayerColors.getPlayerDisplayColorHex(owner) & 0xFFFFFF);
            ownerRgb.put(owner, rgb);
        }
        return rgb;
    }

    // ---- the composite (after GameRenderer.renderLevel) -------------------------------------------------------------

    /** Blends this frame's silhouettes over the finished world picture. Called by GameRendererSilhouetteMixin. */
    public static void composite() {
        if (!drawnThisFrame)
            return;
        drawnThisFrame = false;
        ShaderInstance shader = compositeShader;
        if (shader == null || target == null || failed)
            return;
        RenderTarget main = MC.getMainRenderTarget();
        int fp = com.solegendary.reignofnether.perf.FrameParts.begin(com.solegendary.reignofnether.perf.FrameParts.CANOPY);
        try {
            main.bindWrite(true);
            shader.setSampler("DiffuseSampler", target.getColorTextureId());
            if (outSizeU != null) outSizeU.set((float) main.width, (float) main.height);
            if (fillAlphaU != null) fillAlphaU.set(FILL_ALPHA);
            if (rimAlphaU != null) rimAlphaU.set(RIM_ALPHA);
            if (rimPxU != null) rimPxU.set((float) rimPx(main));
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(() -> shader);
            // only the rectangle the silhouettes were drawn in (clip space)
            float x0 = rectX0 * 2f / main.width - 1f, x1 = rectX1 * 2f / main.width - 1f;
            float y0 = rectY0 * 2f / main.height - 1f, y1 = rectY1 * 2f / main.height - 1f;
            BufferBuilder bb = Tesselator.getInstance().getBuilder();
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            bb.vertex(x0, y0, 0.0).endVertex();
            bb.vertex(x1, y0, 0.0).endVertex();
            bb.vertex(x1, y1, 0.0).endVertex();
            bb.vertex(x0, y1, 0.0).endVertex();
            BufferUploader.drawWithShader(bb.end());
        } catch (RuntimeException e) {
            failed = true;
            ReignOfNether.LOGGER.warn("Unit silhouettes switched off for this session: composite failed: {}", e.toString());
        } finally {
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            com.solegendary.reignofnether.perf.FrameParts.end(fp);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut evt) {
        coverUntil.clear();
        drawnThisFrame = false;
        if (target != null) {
            target.destroyBuffers();   // its depth attachment is the main target's: destroyBuffers only frees our own
            target = null;
        }
    }

    // ---- the flat-colour vertex writer ------------------------------------------------------------------------------

    /**
     * Keeps a vertex's position and texture coordinate and writes it in one colour (what vanilla's outline buffer does
     * for the glowing effect). Not a Sodium VertexBufferWriter, so Embeddium and Oculus hand it plain vertices.
     */
    private static final class FlatColour extends DefaultedVertexConsumer {
        VertexConsumer delegate = null;
        private int r = 255, g = 255, b = 255;
        private double x, y, z;
        private float u, v;

        void setRgb(int rgb) {
            r = (rgb >> 16) & 0xFF;
            g = (rgb >> 8) & 0xFF;
            b = rgb & 0xFF;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
            if (delegate != null)
                delegate.vertex(x, y, z).color(r, g, b, 255).uv(u, v).endVertex();
        }
    }
}
