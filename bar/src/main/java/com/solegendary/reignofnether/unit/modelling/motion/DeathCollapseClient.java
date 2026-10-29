package com.solegendary.reignofnether.unit.modelling.motion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.config.DisplaySettings;
import com.solegendary.reignofnether.hero.PossessionClientEvents;
import com.solegendary.reignofnether.orthoview.StrategicViewClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.AbstractTotem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Quaternionf;

/**
 * Combat feel, part 3 (lovish approved Oct 28), the client half of {@link DeathCollapse}: a dying unit's body tips over
 * the way the killing blow pushed it and its bones flop, instead of vanilla's red flash and stiff tip-over.
 *
 * <ul>
 *   <li><b>When:</b> a unit's first frames of dying ({@code deathTime} at most {@link #CLAIM_TICKS}) claim a slot of the
 *   pool ({@link DeathCollapse#MAX_CONCURRENT}); past the cap, a totem (a standing structure, it does not tip), with the setting off, airborne (a flyer shot down), the
 *   possessed body seen from inside, or a unit first seen already dying, the plain vanilla death plays.</li>
 *   <li><b>Direction:</b> the killing blow's, from the living army's hit stamp ({@link AnimStates}: the damage event
 *   the server sends every client carries where the blow came from); without a fresh one, a seeded direction.</li>
 *   <li><b>How it reaches the body:</b> mixin/LivingEntityRendererMixin zeroes vanilla's flip angle and its red death
 *   overlay for a collapsing unit ({@link #collapsing}); {@link MotionPass#apply} hands a dying unit to {@link #apply},
 *   which tips the whole body on the pose stack about its feet and puts the bones on the parts through the living
 *   army's bone maps ({@link BoneMaps}, {@link MotionPass#place}) - every vanilla humanoid, illager, golem, villager,
 *   spider, and the torso and head of our generated and keyframe rigs. A body with no bone map still tips and sinks.</li>
 *   <li><b>The last moment:</b> the server removes a dead unit after 20 ticks, before the 1.4 s collapse ends; the
 *   client keeps the removed entity ({@link #onLeave}) and draws it itself until it has sunk away
 *   ({@link #onRenderLevel}).</li>
 * </ul>
 * Cost: a few dozen float operations per bone per physics step, at most 40 bodies; render thread only.
 */
@OnlyIn(Dist.CLIENT)
public final class DeathCollapseClient {

    private DeathCollapseClient() {}

    /** A unit first drawn dying later than this many ticks into its death plays the plain death (it was off screen). */
    static final int CLAIM_TICKS = 3;
    /** The hit stamp counts as the killing blow when it is at most this many ticks old. */
    private static final int FRESH_HIT_TICKS = 12;

    private static final DeathCollapsePool POOL = new DeathCollapsePool(DeathCollapse.MAX_CONCURRENT);
    /** Per pool slot: the client tick the death began at. */
    private static final float[] START = new float[DeathCollapse.MAX_CONCURRENT];
    /** Per pool slot: the entity, kept so it can still be drawn after the server removed it (null while it lives). */
    private static final LivingEntity[] HELD = new LivingEntity[DeathCollapse.MAX_CONCURRENT];
    private static final float[] OUT = new float[DeathCollapse.OUT];
    private static final Quaternionf Q = new Quaternionf();
    private static int ticks = 0;
    private static ClientLevel lastLevel = null;
    private static boolean failed = false;
    /** Counts for the log line (the eyes tour and playtest logs say whether collapses ran and whether the cap hit). */
    private static int started = 0, overCap = 0, held = 0, loggedStarted = 0;

    private static boolean enabled() {
        return !failed && DisplaySettings.deathCollapse();
    }

    /**
     * Is this dying unit collapsing? Claims its slot on its first frames. Cheap for the living (one field read); the
     * flip and overlay hooks and the motion pass all ask, every frame, so it must stay so.
     */
    public static boolean collapsing(LivingEntity e) {
        if (e.deathTime <= 0)
            return false;
        int id = e.getId();
        if (POOL.base(id) >= 0)
            return true;
        if (e.deathTime > CLAIM_TICKS || !enabled() || !(e instanceof Unit) || e instanceof AbstractTotem || !nearGround(e))
            return false;
        if (PossessionClientEvents.isPossessing() && PossessionClientEvents.getPossessedId() == id
            && Minecraft.getInstance().options.getCameraType().isFirstPerson())
            return false;
        int b = POOL.claim(id);
        if (b < 0) {
            overCap++;
            return false;   // the cap: the plain death plays
        }
        started++;
        begin(e, b);
        return true;
    }

    /**
     * On the ground or just above it (the killing blow's knockback can lift a body for a tick, and the client's
     * on-ground flag lags the server's): a flyer shot down mid-air keeps the plain death and falls as it always did.
     */
    private static boolean nearGround(LivingEntity e) {
        return e.onGround() || !e.level().noCollision(e, e.getBoundingBox().move(0, -1.2, 0));
    }

    private static void begin(LivingEntity e, int b) {
        int id = e.getId();
        float dir;
        float strength = 1f;
        int a = AnimStates.base(id);
        float[] st = AnimStates.slab();
        if (a >= 0 && st[a + AnimState.HIT_AGE] <= FRESH_HIT_TICKS) {
            dir = st[a + AnimState.HIT_DIR] + (float) Math.PI;   // pushed away from where the blow came from
            if (st[a + AnimState.HIT_SIZE] >= AnimState.STAGGER)
                strength = 1.6f;
        } else
            dir = DeathCollapse.rand(id, 99) * (float) (Math.PI * 2);
        if (e.getBbWidth() > 1.4f)
            strength *= 0.7f;   // a big body goes over slower
        float lift = Math.min(8f, e.getBbWidth() * 4.5f);   // half the lying body's thickness, model px
        float sink = 8f + Math.min(24f, e.getBbHeight() * 4f);
        DeathCollapse.start(POOL.slab(), b, dir, strength, id, lift, sink);
        int slot = b / DeathCollapse.STRIDE;
        START[slot] = ticks - (e.deathTime - 1);
        HELD[slot] = null;
    }

    /**
     * The collapse's pose for this frame (MotionPass.apply, after the model's own setupAnim): the whole body tipped about
     * its feet on the pose stack, the bones on the parts (undone by MotionPass.restore when the render returns).
     */
    public static void apply(LivingEntity e, EntityModel<?> model, PoseStack ps, float partial) {
        if (!collapsing(e))
            return;
        try {
            int b = POOL.base(e.getId());
            float[] s = POOL.slab();
            DeathCollapse.advance(s, b, (ticks + partial - START[b / DeathCollapse.STRIDE]) / 20f);
            // model space: y down, the unit faces -z, its right is -x, its feet at y 1.5 (MotionPass.root)
            float dir = DeathCollapse.fallDir(s, b), c = (float) Math.cos(dir), sn = (float) Math.sin(dir);
            float slide = DeathCollapse.slide(s, b);
            ps.translate(-slide * sn, -DeathCollapse.lift(s, b) / 16f, -slide * c);
            float tilt = DeathCollapse.tilt(s, b);
            if (tilt != 0f) {
                // about the horizontal axis square to the fall: +x pitches it forward, -z rolls it to its right
                ps.translate(0f, 1.5f, 0f);
                ps.mulPose(Q.setAngleAxis(tilt, c, 0f, -sn));
                ps.translate(0f, -1.5f, 0f);
            }
            BoneMap map = BoneMaps.forModel(model);
            if (map != null) {
                DeathCollapse.pose(s, b, MotionPass.scratch());
                MotionPass.place(map, ps);
            }
        } catch (RuntimeException ex) {
            MotionPass.restore();
            failed = true;   // the plain death from now on; never take a frame down over it
            ReignOfNether.LOGGER.error("[Collapse] switched off for the session", ex);
        }
    }

    /** How many collapses are running (the eyes tour logs it). */
    public static int running() {
        return POOL.size();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            clear();
            lastLevel = mc.level;
        }
        if (mc.level == null || mc.isPaused())
            return;
        ticks++;
        if (ticks % 200 == 0 && started != loggedStarted) {
            loggedStarted = started;
            ReignOfNether.LOGGER.info("[Collapse] {} deaths collapsed so far ({} running, {} held past removal, {} plain over the cap of {})",
                started, POOL.size(), held, overCap, DeathCollapse.MAX_CONCURRENT);
        }
        // free a slot once its body is gone: removed and not held, or held and sunk away; one whose entity is still in
        // the world keeps its slot (it lies sunk), or vanilla's flip would take over for its last ticks
        for (int i = 0; i < POOL.cap(); i++) {
            int id = POOL.idAt(i);
            if (id == Integer.MIN_VALUE)
                continue;
            float t = (ticks - START[i]) / 20f;
            boolean gone = HELD[i] == null && mc.level.getEntity(id) == null;
            if (gone || (HELD[i] != null && t > DeathCollapse.DURATION) || t > DeathCollapse.DURATION + 30f)
                free(i, id);
        }
    }

    /** The server removed a unit mid-collapse: keep it to draw until it has sunk away. */
    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent evt) {
        if (!evt.getLevel().isClientSide() || !(evt.getEntity() instanceof LivingEntity le))
            return;
        int b = POOL.base(le.getId());
        if (b < 0)
            return;
        int slot = b / DeathCollapse.STRIDE;
        if ((ticks - START[slot]) / 20f < DeathCollapse.DURATION) {
            HELD[slot] = le;
            held++;
        } else
            free(slot, le.getId());
    }

    /** Draws the held bodies (removed by the server, still sinking) with the entities, through their own renderers. */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || POOL.size() == 0)
            return;
        boolean any = false;
        for (LivingEntity e : HELD)
            any |= e != null;
        if (!any || StrategicViewClientEvents.isStrategicView())
            return;   // icons over the map: no bodies to finish
        Minecraft mc = Minecraft.getInstance();
        EntityRenderDispatcher d = mc.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = evt.getCamera().getPosition();
        float partial = evt.getPartialTick();
        PoseStack ps = evt.getPoseStack();
        for (int i = 0; i < HELD.length; i++) {
            LivingEntity e = HELD[i];
            if (e == null)
                continue;
            try {
                d.render(e, e.getX() - cam.x, e.getY() - cam.y, e.getZ() - cam.z, e.getYRot(), partial, ps, buffers,
                    d.getPackedLightCoords(e, partial));
            } catch (RuntimeException ex) {
                ReignOfNether.LOGGER.warn("[Collapse] could not draw a held body ({}): {}", e.getType(), String.valueOf(ex));
                free(i, e.getId());
            }
        }
        buffers.endBatch();
    }

    private static void free(int slot, int id) {
        POOL.release(id);
        HELD[slot] = null;
    }

    private static void clear() {
        POOL.clear();
        java.util.Arrays.fill(HELD, null);
    }
}
