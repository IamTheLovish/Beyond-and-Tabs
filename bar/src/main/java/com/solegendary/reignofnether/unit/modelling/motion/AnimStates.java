package com.solegendary.reignofnether.unit.modelling.motion;

import com.solegendary.reignofnether.config.ReignOfNetherClientConfigs;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The living army's shared motion state, world side (design/living_army.md, section 3): one slot of
 * {@link AnimState#STRIDE} floats per unit in one pooled slab, stepped once per client tick by {@link Kinematics} and
 * {@link Idle} from what the client sees of the unit. Rigs and the motion pass read it ({@link #base}, {@link #slab});
 * nothing here runs per frame. Slots unseen for {@link #FORGET_TICKS} ticks return to the pool.
 *
 * Hits arrive through mixin/LivingEntityDamageMotionMixin ({@link #onDamage}): the damage event the server sends
 * every client carries the source's position, so the flinch knows where the blow came from.
 */
@OnlyIn(Dist.CLIENT)
public final class AnimStates {

    private AnimStates() {}

    public static final int FORGET_TICKS = 100;
    private static final float GLANCE_RANGE = 16f, GLANCE_YAW_MAX = 70f, GLANCE_PITCH_MAX = 30f;
    private static final int ALLY_HIT_TICKS = 40;

    private static float[] slab = new float[AnimState.STRIDE * 256];
    private static final Int2IntOpenHashMap SLOT = new Int2IntOpenHashMap();
    private static final IntArrayList FREE = new IntArrayList();
    private static int used = 0;
    private static int tick = 0;
    private static boolean toggle = true;
    private static ClientLevel lastLevel = null;

    static {
        SLOT.defaultReturnValue(-1);
    }

    /** The living army is on: the config's master switch and the parade's A/B toggle. */
    public static boolean enabled() {
        if (!toggle)
            return false;
        try {
            return ReignOfNetherClientConfigs.LIVING_ARMY.get();
        } catch (RuntimeException e) {   // the config is not loaded yet
            return true;
        }
    }

    /** The parade's A/B toggle (Numpad 0, /parade motion on|off). */
    public static void setToggle(boolean on) {
        toggle = on;
    }

    public static boolean toggle() {
        return toggle;
    }

    /** The slot base of this entity in {@link #slab()}, or -1 if it has none (not a unit, or not seen yet). */
    public static int base(int entityId) {
        return SLOT.get(entityId);
    }

    public static float[] slab() {
        return slab;
    }

    /** Keeps a unit's slot from being forgotten while it is out of the level (a body {@link Corpses} holds). */
    public static void touch(int entityId) {
        int b = SLOT.get(entityId);
        if (b >= 0)
            slab[b + AnimState.SEEN_TICK] = tick;
    }

    /** The client tick the slab was last stepped at. */
    public static int tick() {
        return tick;
    }

    private static int claim(int id) {
        int b;
        if (!FREE.isEmpty())
            b = FREE.removeInt(FREE.size() - 1);
        else {
            b = used * AnimState.STRIDE;
            used++;
            if (b + AnimState.STRIDE > slab.length)
                slab = java.util.Arrays.copyOf(slab, slab.length * 2);
        }
        SLOT.put(id, b);
        return b;
    }

    private static void clear() {
        SLOT.clear();
        FREE.clear();
        used = 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level != lastLevel) {
            clear();
            lastLevel = level;
        }
        if (level == null || Minecraft.getInstance().isPaused())
            return;
        tick++;
        for (Entity e : level.entitiesForRendering())
            if (e instanceof LivingEntity le && e instanceof Unit)
                stepUnit(le);
        if (tick % 20 == 0)
            forgetUnseen();
    }

    private static void stepUnit(LivingEntity le) {
        int id = le.getId();
        int b = SLOT.get(id);
        boolean fresh = b < 0;
        if (fresh)
            b = claim(id);
        float[] s = slab;
        boolean fighting = le.swinging || (le instanceof Mob m && m.isAggressive());
        Kinematics.step(s, b, (float) (le.getX() - le.xo), (float) (le.getY() - le.yo), (float) (le.getZ() - le.zo),
            le.yBodyRot - le.yBodyRotO, le.getHealth(), le.getMaxHealth(), fighting, fresh);
        Kinematics.look(s, b, le.yHeadRot, le.getXRot(),
            le instanceof RangedAttackerUnit && le instanceof Mob m2 && m2.isAggressive(), fresh);
        if (fresh) {
            // its own tempo and a phase of its own: neighbours never breathe in step (Desync, Life.offset)
            s[b + AnimState.TEMPO] = Desync.tempo(id);
            s[b + AnimState.BREATH_PHASE] = s[b + AnimState.BREATH_PREV] =
                com.solegendary.reignofnether.unit.modelling.animations.Life.advance(0f,
                    com.solegendary.reignofnether.unit.modelling.animations.Life.offset(id) / Kinematics.breathPeriod(0f));
        }
        s[b + AnimState.SEEN_TICK] = tick;
        // after this tick's health: the killing blow's size class is already judged (Kinematics.step)
        if (le.deathTime > 0 || s[b + AnimState.DEATH_KIND] != DeathFall.ALIVE)
            DeathFall.latch(s, b, id, le.deathTime);
        boolean moving = s[b + AnimState.SPEED] >= Kinematics.STOP_SPEED;
        // the scheduler counts in the unit's own ticks: the pose reads its envelopes by the same clock (tickCount)
        if (Idle.step(s, b, id, le.tickCount, moving, fighting) == Idle.GLANCE)
            pickGlance(le, s, b);
    }

    private static void forgetUnseen() {
        ObjectIterator<Int2IntMap.Entry> it = SLOT.int2IntEntrySet().fastIterator();
        while (it.hasNext()) {
            Int2IntMap.Entry en = it.next();
            if (tick - slab[en.getIntValue() + AnimState.SEEN_TICK] > FORGET_TICKS) {
                FREE.add(en.getIntValue());
                it.remove();
            }
        }
    }

    /**
     * A damage event reached the client for `le`: stamp the hit's direction (mixin/LivingEntityDamageMotionMixin) and,
     * when a unit struck it by hand (the direct source is the attacker itself, not an arrow or a spell), that unit's
     * contact recoil - in the same call, so the target flinches and the attacker jolts on the same frame.
     */
    public static void onDamage(LivingEntity le, DamageSource src) {
        if (src == null)
            return;
        Entity direct = src.getDirectEntity();
        if (direct instanceof LivingEntity && direct instanceof Unit && direct != le && direct == src.getEntity()) {
            int ab = SLOT.get(direct.getId());
            if (ab >= 0)
                Kinematics.onContact(slab, ab);
        }
        int b = SLOT.get(le.getId());
        if (b < 0)
            return;
        Vec3 p = src.getSourcePosition();
        if (p == null && src.getEntity() != null)
            p = src.getEntity().position();
        float dir = p == null ? 0f : Kinematics.hitDir(p.x - le.getX(), p.z - le.getZ(), le.yBodyRot);
        Kinematics.onHit(slab, b, dir);
    }

    private static int side(LivingEntity e) {
        Relationship r = UnitClientEvents.getPlayerToEntityRelationship(e);
        return r == Relationship.OWNED || r == Relationship.FRIENDLY ? 1 : r == Relationship.HOSTILE ? -1 : 0;
    }

    /**
     * Where a glance looks (lovish, Oct 27: at real things): the nearest enemy in sight, else an ally hit in the last
     * {@link #ALLY_HIT_TICKS} ticks, else the unit's own hero, else a seeded look-off. Runs only when a glance starts.
     */
    private static void pickGlance(LivingEntity le, float[] s, int b) {
        int mySide = side(le);
        String owner = le instanceof Unit u ? u.getOwnerName() : null;
        LivingEntity enemy = null, ally = null, hero = null;
        double de = Double.MAX_VALUE, da = Double.MAX_VALUE, dh = Double.MAX_VALUE;
        AABB box = le.getBoundingBox().inflate(GLANCE_RANGE);
        for (Entity e : le.level().getEntities(le, box, x -> x instanceof LivingEntity && x instanceof Unit && x.isAlive())) {
            LivingEntity o = (LivingEntity) e;
            double d = o.distanceToSqr(le);
            int os = side(o);
            if (mySide != 0 && os == -mySide) {
                if (d < de) { de = d; enemy = o; }
            } else if (os == mySide && mySide != 0) {
                int ob = SLOT.get(o.getId());
                if (ob >= 0 && slab[ob + AnimState.HIT_AGE] < ALLY_HIT_TICKS && d < da) { da = d; ally = o; }
                if (o instanceof HeroUnit && owner != null && o instanceof Unit ou && owner.equals(ou.getOwnerName())
                    && d < dh) { dh = d; hero = o; }
            }
        }
        LivingEntity target = enemy != null ? enemy : ally != null ? ally : hero;
        float yaw, pitch;
        if (target != null) {
            double dx = target.getX() - le.getX(), dz = target.getZ() - le.getZ();
            double dy = target.getEyeY() - le.getEyeY();
            float lookYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
            yaw = Mth.wrapDegrees(lookYaw - le.yBodyRot);
            pitch = (float) (-Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        } else {
            int n = (int) s[b + AnimState.IDLE_COUNT];
            yaw = (Desync.hash01(le.getId(), 5000 + n) * 2f - 1f) * 60f;
            pitch = (Desync.hash01(le.getId(), 6000 + n) * 2f - 1f) * 10f;
        }
        s[b + AnimState.GLANCE_YAW] = Mth.clamp(yaw, -GLANCE_YAW_MAX, GLANCE_YAW_MAX);
        s[b + AnimState.GLANCE_PITCH] = Mth.clamp(pitch, -GLANCE_PITCH_MAX, GLANCE_PITCH_MAX);
    }
}
