package com.solegendary.reignofnether.unit.modelling.instancing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.mixin.instancing.BufferSourceAccessor;
import com.solegendary.reignofnether.mixin.instancing.CompositeRenderTypeAccessor;
import com.solegendary.reignofnether.mixin.instancing.CompositeStateAccessor;
import com.solegendary.reignofnether.mixin.instancing.ShaderStateShardAccessor;
import com.solegendary.reignofnether.perf.AnimProfile;
import com.solegendary.reignofnether.perf.FrameParts;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The instanced unit bodies' runtime (big-army performance, lovish Nov 6): at the one entry every unit model passes -
 * {@link ModelPart#render} (mixin/instancing/InstancingModelPartMixin) - a pass of a unit drawn by the level's entity
 * loop is taken off the CPU path: its bone matrices and its pass's light, overlay and colour go into the frame's
 * {@link InstanceBatch}, and after the game's entity batches every unit of a body and pass is drawn in one instanced
 * call ({@link InstancedBodies}). Nothing about a model, a renderer or a unit type is named here: it is the same for
 * every one.
 * <p>
 * A unit's render is opened by LivingEntityRenderer.render (mixin/instancing/InstancingLivingRendererMixin). Its passes
 * are TAGGED by the code that starts them: the body (the render's own getBuffer), an EyesLayer (glow), a coloured cutout
 * copy (vanilla's helper: profession and clothing layers) and TeamColourLayer's mipmapped team cloth. A tagged
 * top-level part render with a render type whose shader this path reproduces (entity cutout, entity cutout no cull,
 * eyes, and entity translucent while the pass is opaque: PlayerModel bodies) is instanced. Everything else draws the
 * old way, and the unit with it:
 * <ul>
 *   <li>not in the level's entity loop (portraits, the HUD), a shader pack, an outline buffer (glowing), not a unit;</li>
 *   <li>a pass whose render type is not reproduced, or a translucent one with alpha < 1 (phasing, fading corpses) - DEMOTE;</li>
 *   <li>an untagged draw of a part of one of this unit's rigs (a layer re-drawing a bone, e.g. an upgrade glow) - DEMOTE;</li>
 *   <li>a tree whose parts keep moving past the rebuild cap ({@link RigCache}) - DEMOTE.</li>
 * </ul>
 * DEMOTE replays the unit's passes taken so far through the old path, exactly (the same parts, poses, visibility and
 * arguments; the pose stack's top restored), drops their records and lets the rest of the render run untouched - so a
 * surface drawn twice (a layer over its body with LEQUAL depth) always has both draws from the same path.
 * <p>
 * The [Instancing] line (every 10 s) gives instanced and old-path unit renders per frame, the old path's reasons by
 * unit type (lovish's rule: the log names the culprit), and the trees given up on.
 */
@OnlyIn(Dist.CLIENT)
public final class Instancer {
    private Instancer() { }

    // ---- why a unit went the old way ----
    static final int R_NOT_LOOP = 0, R_SHADER_PACK = 1, R_OUTLINE = 2, R_TYPE = 3, R_FOREIGN = 4, R_TREE = 5,
        R_OFF = 6, R_FAILED = 7, R_REASONS = 8;
    static final String[] REASON = { "not-level", "shader-pack", "outline", "render-type", "foreign-draw", "tree", "off",
        "gl-failed" };

    static final RigCache CACHE = new RigCache();
    static final InstanceBatch BATCH = new InstanceBatch();

    /** The level's entity loop is drawing an entity now (LevelRenderer.renderEntity). */
    static int entityLoopDepth = 0;
    /** The path draws this frame (the switch, the shaders, no shader pack), decided once per frame. */
    static boolean frameOn = false;
    /** Why not, when it is off this frame. */
    static int frameOffReason = R_OFF;

    // the unit being drawn
    static boolean unitActive = false;
    static LivingEntity unit = null;
    static MultiBufferSource unitBuffers = null;
    static int unitSeq = 0;
    static boolean demoted = false;
    static int unitPassesTaken = 0;
    static int ordinal = -1;
    static RenderType tagType = null;
    static int tagKind = -1;
    static boolean tagged = false;
    static boolean bypass = false;   // the replay and the vertex check draw through the old path on purpose
    private static final List<RigCache.Entry> unitRigs = new ArrayList<>(8);
    private static final List<Pass> unitPasses = new ArrayList<>(8);

    /** One taken pass, enough to draw it again the old way. */
    static final class Pass {
        ModelPart root;
        Rig rig;
        long[] bits;
        RenderType type;
        final Matrix4f pose = new Matrix4f();
        final Matrix3f normal = new Matrix3f();
        int light, overlay;
        float r, g, b, a;
    }

    private static final List<Pass> passPool = new ArrayList<>();
    private static int passPoolUsed = 0;

    /** Per rig, per frame: the scratch arrays, and the last bone block a unit's pass computed. */
    static final class Scratch {
        final Rig rig;
        final boolean[] vis;
        final long[] bits;
        final Matrix4f[] pose;
        final Matrix3f[] normal;
        final List<ModelPart> moved = new ArrayList<>();
        // the last bone block: its unit, base pose and the bones' poses it was computed from
        int seq = -1, texel = -1;
        final Matrix4f basePose = new Matrix4f();
        final Matrix3f baseNormal = new Matrix3f();
        final float[] bonePoses;

        Scratch(Rig rig) {
            this.rig = rig;
            this.vis = rig.newVisibility();
            this.bits = rig.newSignature();
            this.pose = rig.newPoses();
            this.normal = rig.newNormals();
            this.bonePoses = new float[rig.boneCount * Rig.POSE];
        }
    }

    private static final Map<Rig, Scratch> SCRATCH = new IdentityHashMap<>();
    private static final PoseStack BONE_STACK = new PoseStack();
    private static final PoseStack REPLAY_STACK = new PoseStack();

    // render type -> kind (InstanceBatch.KIND_*), or -1 when this path does not reproduce its shader
    private static final Map<RenderType, Integer> KINDS = new IdentityHashMap<>();

    // counters: lifetime and the 10 s window, per unit type: instanced, and old-path by reason
    private static long instancedLifetime = 0, oldLifetime = 0;
    private static final Map<Object, long[]> BY_TYPE = new IdentityHashMap<>();
    private static long windowFrames0 = 0;
    private static int ticks = 0;
    private static int lastInstancesDrawn = 0;

    // the same-frame vertex check (the eyes army scene)
    private static int parityFrames = 0;
    private static long parityPasses = 0, parityBad = 0;
    private static float parityWorstPos = 0, parityWorstNormal = 0;
    private static String parityFirst = null, parityReport = null;
    private static final VertexCapture PARITY_OLD = new VertexCapture(), PARITY_NEW = new VertexCapture();

    // ---- the frame ----

    /** A frame starts: is the path on, and was the last frame's batch drawn (else switch off: units must never vanish). */
    static void frameStart(boolean wanted) {
        if (!BATCH.isEmpty()) {
            InstancedBodies.fail("a frame's instanced units were never drawn (no AFTER_ENTITIES stage after the entities)");
            BATCH.clear();
        }
        frameOffReason = !wanted ? R_OFF : !InstancedBodies.ready() ? R_FAILED
            : com.solegendary.reignofnether.postfx.ShaderPacks.inUse() ? R_SHADER_PACK : -1;
        frameOn = frameOffReason < 0;
        entityLoopDepth = 0;
        reset();
        if (parityFrames > 0 && --parityFrames == 0)
            parityReport = String.format(Locale.ROOT,
                "%s - %d instanced passes compared with the old path's vertices, %d differ; worst position %.2e blocks, "
                    + "worst normal %.2e%s", parityBad == 0 && parityPasses > 0 ? "PASS" : "FAIL", parityPasses, parityBad,
                parityWorstPos, parityWorstNormal, parityFirst == null ? "" : "; first: " + parityFirst);
    }

    /** After the game's entity batches: draw the frame's instanced units. */
    static void flush() {
        if (BATCH.isEmpty())
            return;
        int t = FrameParts.begin(UnitInstancing.FRAME_SECTION);
        try {
            lastInstancesDrawn = InstancedBodies.draw(BATCH);
        } finally {
            BATCH.clear();
            for (Rig r : CACHE.takeRetired()) {
                InstancedBodies.free(r);
                SCRATCH.remove(r);
            }
            FrameParts.end(t);
        }
    }

    // ---- a unit ----

    /** A unit render inside another's (a mount drawing its rider): the outer one goes the old way, the inner too. */
    static int nested = 0;

    /** LivingEntityRenderer.render starts. */
    public static void beginUnit(LivingEntity entity, MultiBufferSource buffers) {
        if (unitActive) {
            if (!demoted)
                demote(R_FOREIGN);
            nested++;
            return;
        }
        reset();
        if (!(entity instanceof Unit))
            return;
        int why = -1;
        if (!frameOn)
            why = frameOffReason;
        else if (entityLoopDepth <= 0)
            why = R_NOT_LOOP;
        else if (buffers != Minecraft.getInstance().renderBuffers().bufferSource())
            why = R_OUTLINE;
        if (why >= 0) {
            if (why != R_NOT_LOOP)   // portraits and the HUD are not the army's frame cost
                count(entity, why);
            return;
        }
        unitActive = true;
        unit = entity;
        unitBuffers = buffers;
        unitSeq++;
        demoted = false;
        unitPassesTaken = 0;
        ordinal = -1;
        tagged = false;
    }

    /** LivingEntityRenderer.render returns. */
    public static void endUnit() {
        if (nested > 0) {
            nested--;
            return;
        }
        if (unitActive && !demoted && unitPassesTaken > 0)   // a demoted unit was counted when it was demoted
            count(unit, -1);
        reset();
    }

    private static void reset() {
        nested = 0;
        unitActive = false;
        unit = null;
        unitBuffers = null;
        tagged = false;
        unitRigs.clear();
        unitPasses.clear();
        passPoolUsed = 0;
    }

    /** A pass starts with this render type (the body's getBuffer, an EyesLayer, a coloured cutout copy, the team cloth). */
    public static RenderType tag(RenderType type) {
        if (!unitActive || demoted)
            return type;
        tagged = true;
        tagType = type;
        tagKind = kind(type);
        ordinal++;
        return type;
    }

    /** The tagged pass is over (a layer starts, or the tagging code returned). */
    public static void untag() {
        tagged = false;
    }

    /** Level entity loop: an entity's render starts / ends. */
    public static void enterEntity() {
        entityLoopDepth++;
    }

    public static void exitEntity() {
        if (entityLoopDepth > 0)
            entityLoopDepth--;
    }

    /**
     * ModelPart.render's head. True = handled (the caller cancels vanilla's own walk): either taken into the batch, or
     * drawn here the old way with the hook bypassed (so its children are not looked at one by one). False only while no
     * unit is being drawn - the one static read every part render in the game pays.
     */
    public static boolean onPartRender(ModelPart part, PoseStack ps, VertexConsumer consumer, int light, int overlay,
                                       float r, float g, float b, float a) {
        if (!unitActive || bypass)
            return false;
        if (demoted)
            return oldWay(part, ps, consumer, light, overlay, r, g, b, a);
        if (!tagged) {
            RigCache.Entry member = CACHE.memberOf(part);
            if (member != null && unitRigs.contains(member))
                demote(R_FOREIGN);   // a part of this unit's body drawn by an untagged layer: both draws the old way
            return oldWay(part, ps, consumer, light, overlay, r, g, b, a);
        }
        if (!InstanceBatch.instanceable(tagKind, a)) {
            demote(R_TYPE);
            return oldWay(part, ps, consumer, light, overlay, r, g, b, a);
        }
        RigCache.Entry e = CACHE.entry(part);
        Rig rig = e == null ? null : e.rig();
        if (rig == null) {
            demote(R_TREE);
            return oldWay(part, ps, consumer, light, overlay, r, g, b, a);
        }
        Scratch s = scratch(rig);
        if (!rig.signature(s.vis, s.bits)) {
            if (!unitRigs.contains(e))
                unitRigs.add(e);
            return true;   // nothing visible: the old walk would write nothing either
        }
        s.moved.clear();
        rig.movedParts(s.vis, s.moved);
        if (!s.moved.isEmpty()) {
            rig = CACHE.promote(e, s.moved);
            if (rig == null) {
                demote(R_TREE);
                return oldWay(part, ps, consumer, light, overlay, r, g, b, a);
            }
            s = scratch(rig);
            rig.signature(s.vis, s.bits);
        }
        int texel = bones(s, ps.last());
        Rig.Variant variant = rig.variant(s.bits);
        int rgba = InstanceBatch.packColour(r, g, b, a);
        BATCH.add(ordinal, tagKind, tagType, rig, variant, texel, light, overlay, rgba, unitSeq);
        if (!unitRigs.contains(e))
            unitRigs.add(e);
        Pass pass = nextPass();
        pass.root = part;
        pass.rig = rig;
        if (pass.bits == null || pass.bits.length != s.bits.length)
            pass.bits = new long[s.bits.length];
        System.arraycopy(s.bits, 0, pass.bits, 0, s.bits.length);
        pass.type = tagType;
        pass.pose.set(ps.last().pose());
        pass.normal.set(ps.last().normal());
        pass.light = light;
        pass.overlay = overlay;
        pass.r = r;
        pass.g = g;
        pass.b = b;
        pass.a = a;
        unitPasses.add(pass);
        unitPassesTaken++;
        if (parityFrames > 0)
            parity(part, ps, rig, variant, s, light, overlay, r, g, b, a);
        return true;
    }

    /** Vanilla's walk of this part and its subtree, the hook out of the way; then the caller cancels its own. */
    private static boolean oldWay(ModelPart part, PoseStack ps, VertexConsumer consumer, int light, int overlay, float r,
                                  float g, float b, float a) {
        bypass = true;
        try {
            part.render(ps, consumer, light, overlay, r, g, b, a);
        } finally {
            bypass = false;
        }
        return true;
    }

    private static Pass nextPass() {
        if (passPoolUsed == passPool.size())
            passPool.add(new Pass());
        return passPool.get(passPoolUsed++);
    }

    private static Scratch scratch(Rig rig) {
        Scratch s = SCRATCH.get(rig);
        if (s == null) {
            s = new Scratch(rig);
            SCRATCH.put(rig, s);
        }
        return s;
    }

    /** The unit's bone block for this pass: the last one again if nothing moved since (same unit, base and bone poses). */
    private static int bones(Scratch s, PoseStack.Pose base) {
        Rig rig = s.rig;
        if (s.seq == unitSeq && s.basePose.equals(base.pose()) && s.baseNormal.equals(base.normal())
                && sameBonePoses(s))
            return s.texel;
        rig.bones(base, BONE_STACK, s.pose, s.normal);
        s.texel = BATCH.addBones(s.pose, s.normal, rig.boneCount);
        s.seq = unitSeq;
        s.basePose.set(base.pose());
        s.baseNormal.set(base.normal());
        for (int k = 0; k < rig.boneCount; k++)
            Rig.readPose(rig.parts[rig.bonePart[k]], s.bonePoses, k * Rig.POSE);
        return s.texel;
    }

    private static boolean sameBonePoses(Scratch s) {
        Rig rig = s.rig;
        float[] tmp = TMP_POSE;
        for (int k = 0; k < rig.boneCount; k++) {
            Rig.readPose(rig.parts[rig.bonePart[k]], tmp, 0);
            for (int i = 0; i < Rig.POSE; i++)
                if (tmp[i] != s.bonePoses[k * Rig.POSE + i])
                    return false;
        }
        return true;
    }

    private static final float[] TMP_POSE = new float[Rig.POSE];

    /** The unit goes the old way from here: its passes taken so far are drawn again exactly as the old path draws them. */
    private static void demote(int why) {
        demoted = true;
        BATCH.kill(unitSeq);
        if (!unitPasses.isEmpty() && unitBuffers != null) {
            // the type the caller is writing in now: the replay's getBuffer calls re-open the one shared builder for
            // their own types, so ask for this one again after them, or the caller's quads land in the last replayed type
            Optional<RenderType> open = unitBuffers instanceof MultiBufferSource.BufferSource source
                    ? ((BufferSourceAccessor) source).bat$lastState() : Optional.empty();
            bypass = true;
            try {
                for (Pass p : unitPasses)
                    replay(p);
                open.ifPresent(unitBuffers::getBuffer);
            } finally {
                bypass = false;
            }
        }
        count(unit, why);
        unitPasses.clear();
        unitPassesTaken = 0;
    }

    /** One pass through vanilla's walk: the parts that drew then (all visible, skipDraw where they did not), the pose then. */
    private static void replay(Pass p) {
        Rig rig = p.rig;
        ModelPart[] parts = rig.parts;
        boolean[] vis = new boolean[parts.length], skip = new boolean[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vis[i] = parts[i].visible;
            skip[i] = parts[i].skipDraw;
            parts[i].visible = true;
            parts[i].skipDraw = (p.bits[i >>> 6] & (1L << (i & 63))) == 0;
        }
        try {
            REPLAY_STACK.pushPose();
            REPLAY_STACK.last().pose().set(p.pose);
            REPLAY_STACK.last().normal().set(p.normal);
            p.root.render(REPLAY_STACK, unitBuffers.getBuffer(p.type), p.light, p.overlay, p.r, p.g, p.b, p.a);
        } finally {
            REPLAY_STACK.popPose();
            for (int i = 0; i < parts.length; i++) {
                parts[i].visible = vis[i];
                parts[i].skipDraw = skip[i];
            }
        }
    }

    /**
     * Which of this path's two programs reproduces the render type's shader: the entity cutout (with or without culling:
     * the same shader code; the cull is the render type's state) or the eyes; -1 for anything else. The type must be the
     * entity vertex format in quads. Read once per type through accessors (no GL).
     */
    static int kind(RenderType type) {
        Integer k = KINDS.get(type);
        if (k != null)
            return k;
        int kind = -1;
        try {
            if (type.format() == DefaultVertexFormat.NEW_ENTITY && type.mode() == VertexFormat.Mode.QUADS
                    && type instanceof CompositeRenderTypeAccessor composite) {
                RenderStateShard.ShaderStateShard shard = ((CompositeStateAccessor) (Object) composite.bat$state()).bat$shaderState();
                Optional<Supplier<ShaderInstance>> sup = ((ShaderStateShardAccessor) shard).bat$shader();
                ShaderInstance si = sup.isPresent() ? sup.get().get() : null;
                if (si != null && (si == GameRenderer.getRendertypeEntityCutoutNoCullShader()
                        || si == GameRenderer.getRendertypeEntityCutoutShader()))
                    kind = InstanceBatch.KIND_CUTOUT;
                else if (si != null && si == GameRenderer.getRendertypeEntityTranslucentShader())
                    kind = InstanceBatch.KIND_TRANSLUCENT;   // PlayerModel bodies (grunt): the cutout's code and blend
                else if (si != null && si == GameRenderer.getRendertypeEyesShader())
                    kind = InstanceBatch.KIND_EYES;
            }
        } catch (RuntimeException | LinkageError e) {
            kind = -1;
        }
        KINDS.put(type, kind);
        return kind;
    }

    /** Shaders reloaded (F3+T, a resource pack): the shader instances the kinds were read against are new. */
    static void forgetKinds() {
        KINDS.clear();
    }

    // ---- the same-frame vertex check ----

    static void startParity(int frames) {
        parityFrames = frames + 1;   // frameStart counts the first one down before anything is drawn
        parityPasses = 0;
        parityBad = 0;
        parityWorstPos = 0;
        parityWorstNormal = 0;
        parityFirst = null;
        parityReport = null;
    }

    static String parityReport() {
        return parityReport;
    }

    /** What the old path would write for this pass, against what the instanced path draws for it (its CPU twin). */
    private static void parity(ModelPart part, PoseStack ps, Rig rig, Rig.Variant variant, Scratch s, int light,
                               int overlay, float r, float g, float b, float a) {
        PARITY_OLD.clear();
        PARITY_NEW.clear();
        bypass = true;
        try {
            part.render(ps, PARITY_OLD, light, overlay, r, g, b, a);
        } finally {
            bypass = false;
        }
        // the shader's colour is the byte the old writer stores: compare those
        rig.skin(variant, s.pose, s.normal, r, g, b, a, overlay, light, PARITY_NEW);
        parityPasses++;
        String bad = null;
        if (PARITY_OLD.count() != PARITY_NEW.count())
            bad = "vertex count " + PARITY_OLD.count() + " vs " + PARITY_NEW.count();
        for (int v = 0; bad == null && v < PARITY_OLD.count(); v++) {
            float dp = Math.max(Math.abs(PARITY_OLD.x(v) - PARITY_NEW.x(v)), Math.max(Math.abs(PARITY_OLD.y(v) - PARITY_NEW.y(v)),
                Math.abs(PARITY_OLD.z(v) - PARITY_NEW.z(v))));
            float dn = Math.max(Math.abs(PARITY_OLD.nx(v) - PARITY_NEW.nx(v)), Math.max(Math.abs(PARITY_OLD.ny(v) - PARITY_NEW.ny(v)),
                Math.abs(PARITY_OLD.nz(v) - PARITY_NEW.nz(v))));
            parityWorstPos = Math.max(parityWorstPos, dp);
            parityWorstNormal = Math.max(parityWorstNormal, dn);
            if (dp > PARITY_POS || dn > PARITY_NORMAL)
                bad = String.format(Locale.ROOT, "vertex %d off by %.2e (normal %.2e)", v, dp, dn);
            else if (PARITY_OLD.u(v) != PARITY_NEW.u(v) || PARITY_OLD.vv(v) != PARITY_NEW.vv(v))
                bad = "vertex " + v + " uv";
            else if (PARITY_OLD.overlay(v) != PARITY_NEW.overlay(v) || PARITY_OLD.light(v) != PARITY_NEW.light(v))
                bad = "vertex " + v + " overlay/light";
            else if (InstanceBatch.packColour(PARITY_OLD.r(v), PARITY_OLD.g(v), PARITY_OLD.b(v), PARITY_OLD.a(v))
                    != InstanceBatch.packColour(PARITY_NEW.r(v), PARITY_NEW.g(v), PARITY_NEW.b(v), PARITY_NEW.a(v)))
                bad = "vertex " + v + " colour";
        }
        if (bad != null) {
            parityBad++;
            if (parityFirst == null)
                parityFirst = AnimProfile.typeName(unit.getType()) + " pass " + ordinal + ": " + bad;
        }
    }

    /** The check's bounds, fixed before the first run: positions in blocks, normal components. */
    static final float PARITY_POS = 1e-3f, PARITY_NORMAL = 1e-3f;

    // ---- counters ----

    private static void count(LivingEntity e, int why) {
        if (e == null)
            return;
        long[] c = BY_TYPE.get(e.getType());
        if (c == null) {
            c = new long[2 * (R_REASONS + 1)];   // window then lifetime: [0] instanced, [1 + r] old path by reason
            BY_TYPE.put(e.getType(), c);
        }
        int slot = why < 0 ? 0 : 1 + why;
        c[slot]++;
        c[R_REASONS + 1 + slot]++;
        if (why < 0)
            instancedLifetime++;
        else
            oldLifetime++;
    }

    static long instancedLifetime() {
        return instancedLifetime;
    }

    /** The [Instancing] line for the 10 s window, or null when no unit was drawn. Resets the window. */
    static String windowLine() {
        long frames = Math.max(1, FrameParts.framesLifetime() - windowFrames0);
        windowFrames0 = FrameParts.framesLifetime();
        long inst = 0, old = 0;
        long[] byReason = new long[R_REASONS];
        StringBuilder types = new StringBuilder();
        for (Map.Entry<Object, long[]> e : BY_TYPE.entrySet()) {
            long[] c = e.getValue();
            long typeOld = 0;
            for (int r = 0; r < R_REASONS; r++) {
                typeOld += c[1 + r];
                byReason[r] += c[1 + r];
            }
            inst += c[0];
            old += typeOld;
            if (typeOld > 0) {   // the types that went the old way, and why
                types.append(types.length() == 0 ? "" : "; ").append(AnimProfile.typeName(e.getKey())).append(' ')
                    .append(String.format(Locale.ROOT, "%.1f/frame old (", typeOld / (double) frames));
                boolean first = true;
                for (int r = 0; r < R_REASONS; r++)
                    if (c[1 + r] > 0) {
                        types.append(first ? "" : ", ").append(REASON[r]).append(' ').append(c[1 + r]);
                        first = false;
                    }
                types.append(String.format(Locale.ROOT, "), %.1f/frame instanced", c[0] / (double) frames));
            }
            for (int k = 0; k <= R_REASONS; k++)
                c[k] = 0;
        }
        if (inst + old == 0)
            return null;
        StringBuilder reasons = new StringBuilder();
        for (int r = 0; r < R_REASONS; r++)
            if (byReason[r] > 0)
                reasons.append(reasons.length() == 0 ? "" : ", ").append(REASON[r]).append(' ').append(byReason[r]);
        StringBuilder given = new StringBuilder();
        for (RigCache.Entry en : CACHE.entries())
            if (en.why() != null)
                given.append(given.length() == 0 ? "" : "; ").append(en.why());
        return String.format(Locale.ROOT, "[Instancing] %s: %.1f unit renders/frame instanced, %.1f the old way (%s), "
                + "%d drawn in the last frame; old-path types: %s; trees given up: %s", frameOn ? "on" : "off",
            inst / (double) frames, old / (double) frames, reasons.length() == 0 ? "-" : reasons, lastInstancesDrawn,
            types.length() == 0 ? "-" : types, given.length() == 0 ? "-" : given);
    }

    static void logTick() {
        if (++ticks % 200 != 0)
            return;
        String line = windowLine();
        if (line != null)
            ReignOfNether.LOGGER.info(line);
    }
}
