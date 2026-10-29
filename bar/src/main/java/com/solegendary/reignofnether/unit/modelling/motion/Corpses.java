package com.solegendary.reignofnether.unit.modelling.motion;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The bodies of the fallen, held by the client (the death-pose contract, {@link DeathFall}): the server removes a dead
 * unit 20 ticks into its death, as vanilla does; a unit whose fall was drawn ({@link AnimState#DEATH_DRAWN} - so only
 * one the camera saw die) is kept here when the client removes it, lies where it fell until
 * {@link DeathFall#HOLD_END} and sinks into the ground by {@link DeathFall#END}. Render only: the body is out of the
 * level, so nothing can select, target, count or collide with it, and no packet or server state is involved.
 *
 * Drawn through the entity dispatcher after the level's entities (its own renderer, layers, fog of war and light);
 * the health bar and the HUD skip it ({@link #drawing}). At most {@link #MAX} at once - the oldest goes first - and
 * none past {@link #MAX_DIST} or outside the view. Its clock is its deathTime, advanced here once a tick, so the
 * fall's last ticks play on if the removal came before they did. No allocation per frame or tick. An error switches
 * the hold off for the session with one WARN line; deaths then end as vanilla's do.
 */
@OnlyIn(Dist.CLIENT)
public final class Corpses {

    private Corpses() {}

    public static final int MAX = 96;
    private static final double MAX_DIST = 160;

    private static final LivingEntity[] HELD = new LivingEntity[MAX];
    private static int count = 0;
    private static boolean drawing = false;
    private static boolean disabled = false;
    private static ClientLevel lastLevel = null;

    /** True while a held body is being drawn: the health bar and the HUD's per-unit hooks leave it alone. */
    public static boolean drawing() {
        return drawing;
    }

    public static int count() {
        return count;
    }

    /** This unit's body will be held when it is removed: its fall was drawn (vanilla's death smoke is then left out). */
    public static boolean keepsBody(Entity e) {
        if (disabled || !(e instanceof LivingEntity le) || !(e instanceof Unit) || le.deathTime <= 0
            || !AnimStates.enabled())
            return false;
        int b = AnimStates.base(e.getId());
        return b >= 0 && AnimStates.slab()[b + AnimState.DEATH_DRAWN] != 0f;
    }

    /** The client is removing `e` (mixin/ClientLevelCorpseMixin): keep its body if it fell by DeathFall. */
    public static void onRemove(Entity e) {
        if (!keepsBody(e))
            return;
        LivingEntity le = (LivingEntity) e;
        for (int i = 0; i < count; i++)
            if (HELD[i] == le)
                return;
        if (count == MAX)
            drop(0);
        // frozen where it fell: no interpolation left, no stride, no swing, no red
        le.xo = le.xOld = le.getX();
        le.yo = le.yOld = le.getY();
        le.zo = le.zOld = le.getZ();
        le.yRotO = le.getYRot();
        le.yBodyRotO = le.yBodyRot;
        le.yHeadRotO = le.yHeadRot;
        le.walkAnimation.update(0f, 1f);
        le.walkAnimation.update(0f, 1f);
        le.swinging = false;
        le.attackAnim = le.oAttackAnim = 0f;
        le.hurtTime = 0;
        HELD[count++] = le;
    }

    private static void drop(int i) {
        System.arraycopy(HELD, i + 1, HELD, i, count - i - 1);
        HELD[--count] = null;
    }

    private static void clear() {
        for (int i = 0; i < count; i++)
            HELD[i] = null;
        count = 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level != lastLevel || !AnimStates.enabled() || disabled) {
            clear();
            lastLevel = level;
        }
        if (level == null || Minecraft.getInstance().isPaused())
            return;
        for (int i = count - 1; i >= 0; i--) {
            LivingEntity le = HELD[i];
            le.deathTime++;
            if (le.deathTime >= DeathFall.END)
                drop(i);
            else
                AnimStates.touch(le.getId());
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || count == 0 || disabled)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;
        Vec3 cam = evt.getCamera().getPosition();
        Frustum frustum = evt.getFrustum();
        float partial = evt.getPartialTick();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = evt.getPoseStack();
        boolean drew = false;
        drawing = true;
        try {
            for (int i = 0; i < count; i++) {
                LivingEntity le = HELD[i];
                double dx = le.getX() - cam.x, dy = le.getY() - cam.y, dz = le.getZ() - cam.z;
                if (dx * dx + dy * dy + dz * dz > MAX_DIST * MAX_DIST
                    || (frustum != null && !frustum.isVisible(le.getBoundingBoxForCulling())))
                    continue;
                double sink = DeathFall.sinkBlocks(le.deathTime + partial);
                dispatcher.render(le, dx, dy - sink, dz, le.getYRot(), partial, pose, buffers,
                    dispatcher.getPackedLightCoords(le, partial));
                drew = true;
            }
            if (drew)
                buffers.endBatch();
        } catch (RuntimeException | LinkageError e) {
            disabled = true;
            clear();
            ReignOfNether.LOGGER.warn("[Corpses] disabled for the session: {}", String.valueOf(e));
        } finally {
            drawing = false;
        }
    }
}
