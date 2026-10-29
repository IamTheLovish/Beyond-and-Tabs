package com.solegendary.reignofnether.essence;

import com.solegendary.reignofnether.ability.abilities.GraveTide;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitGrid;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.TombcallerUnit;
import com.solegendary.reignofnether.unit.units.monsters.TombstoneUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Gravebound Tombcaller's lasting effects (design/essence_casters.md "Gravebound Gravecaller", Revision 1 and
 * lovish's "tone down"; every tombstone number is on {@link TombstoneUnit}). Server side only.
 * <ul>
 *   <li><b>Tombstones</b> are watched from the moment they join (a reload included): each crumbles at its saved end
 *       tick and raises a Risen Skeleton (a summon: no population, the essence base's one rule) at its saved raise
 *       tick, every 20 s whatever still stands. A skeleton carries its crumble tick and its stone's UUID, is a summon and vanishes on
 *       time.</li>
 *   <li><b>Grave creep</b> is an area, not blocks: a standing stone's disc (growing with its age) and, once the stone
 *       dies, a fading disc that shrinks to nothing over {@link TombstoneUnit#CREEP_FADE_SECONDS} s. Every
 *       {@link #EFFECT_TICKS} ticks the creep heals the Gravebound units of its owner and allies and slows every
 *       enemy unit on it (one transient speed modifier, taken off the moment the unit is off the creep); overlapping
 *       creeps do not stack. Soul flames on the disc show it (placeholder look).</li>
 *   <li>A Tombcaller pulls the wisps on its <b>own</b> creep from {@link TombstoneUnit#CREEP_PULL_RANGE} blocks
 *       (EssenceServerEvents' reach hook).</li>
 *   <li><b>Grave Tide</b> buffs: a transient speed modifier and a heal every {@link #EFFECT_TICKS} ticks until each
 *       unit's own end tick.</li>
 * </ul>
 */
public final class TombcallerServerEvents {

    private TombcallerServerEvents() { }

    public static final String RISEN_TAG = "bt_tomb_risen";
    static final String RISEN_END = "bt_tomb_risen_end", RISEN_STONE = "bt_tomb_risen_stone";
    /** The creep's and Grave Tide's heals and the creep's slow are applied this often (half a second). */
    static final int EFFECT_TICKS = 10;
    /** How far above or below the stone a unit or wisp can be and still stand on its creep. */
    static final double CREEP_HEIGHT = 3;
    /** A refusal message to the same owner at most this often: an autocast tries once a second. */
    static final int WARN_EVERY_TICKS = 60;
    static final UUID CREEP_SLOW_ID = UUID.fromString("6c2b1e8e-5d8f-4a9b-9b51-2f0d3c7a1e01");
    static final UUID TIDE_SPEED_ID = UUID.fromString("6c2b1e8e-5d8f-4a9b-9b51-2f0d3c7a1e02");

    private static final List<TombstoneUnit> stones = new ArrayList<>();
    private static final List<Fade> fades = new ArrayList<>();
    private static final List<Mob> risen = new ArrayList<>();
    /** Stones whose chunk unloaded, by their UUID: still the caster's until their end tick, or until they come back. */
    private static final Map<UUID, Away> away = new HashMap<>();
    private static final Map<LivingEntity, Long> tides = new IdentityHashMap<>();
    private static final Set<LivingEntity> slowed = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<String, Long> lastWarn = new HashMap<>();
    private static final List<LivingEntity> scratch = new ArrayList<>();

    /** A dead stone's creep, shrinking from the radius it had to nothing. */
    private record Fade(Level level, Vec3 centre, String owner, UUID caster, float radiusAtDeath, long died) {
        float radius(long now) {
            float left = 1f - (now - died) / (TombstoneUnit.CREEP_FADE_SECONDS * 20f);
            return left <= 0 ? 0 : radiusAtDeath * left;
        }
    }

    private record Away(UUID caster, long end) { }

    static {
        EssenceServerEvents.addReach(TombcallerServerEvents::pullReach, TombstoneUnit.CREEP_PULL_RANGE);
    }

    // ------------------------------------------------------------------------------------------ the queries

    /** The tombstones {@code caster} raised that still stand. */
    public static List<TombstoneUnit> stonesOf(LivingEntity caster) {
        List<TombstoneUnit> out = new ArrayList<>();
        UUID id = caster.getUUID();
        for (TombstoneUnit s : stones)
            if (s.isAlive() && !s.isRemoved() && id.equals(s.casterId()))
                out.add(s);
        return out;
    }

    /**
     * How many stones count toward {@code caster}'s cap: those standing, and those whose chunk unloaded that have not
     * yet reached their end (PR 366 review round 1: an unloaded stone let a caster raise a fourth).
     */
    public static int stoneCount(LivingEntity caster) {
        int n = stonesOf(caster).size();
        UUID id = caster.getUUID();
        long now = now(caster.level());
        for (Away a : away.values())
            if (id.equals(a.caster) && a.end > now)
                n++;
        return n;
    }

    /** The Risen Skeletons of {@code stone} still standing. */
    public static List<Mob> risenOf(TombstoneUnit stone) {
        List<Mob> out = new ArrayList<>();
        UUID id = stone.getUUID();
        for (Mob m : risen)
            if (m.isAlive() && !m.isRemoved() && m.getPersistentData().hasUUID(RISEN_STONE)
                    && id.equals(m.getPersistentData().getUUID(RISEN_STONE)))
                out.add(m);
        return out;
    }

    public static boolean isRisen(Entity e) {
        return e != null && e.getTags().contains(RISEN_TAG);
    }

    /** The game time a Risen Skeleton crumbles (0 for anything else). */
    public static long risenEnd(Entity e) {
        return isRisen(e) ? e.getPersistentData().getLong(RISEN_END) : 0;
    }

    private static boolean within(Vec3 centre, double r, Vec3 pos) {
        double dx = pos.x - centre.x, dz = pos.z - centre.z;
        return r > 0 && dx * dx + dz * dz <= r * r && Math.abs(pos.y - centre.y) <= CREEP_HEIGHT;
    }

    private static long now(Level level) {
        return level.getGameTime();
    }

    /** Whether {@code pos} lies on the grave creep (a standing stone's or a fading one's) that {@code caster} raised. */
    public static boolean onCreepOf(LivingEntity caster, Vec3 pos) {
        UUID id = caster.getUUID();
        long now = now(caster.level());
        for (TombstoneUnit s : stones)
            if (s.level() == caster.level() && s.isAlive() && id.equals(s.casterId()) && within(s.position(), s.creepRadius(now), pos))
                return true;
        for (Fade f : fades)
            if (f.level == caster.level() && id.equals(f.caster) && within(f.centre, f.radius(now), pos))
                return true;
        return false;
    }

    /** Whether {@code pos} lies on any grave creep {@code owner} raised. */
    public static boolean onCreep(ServerLevel level, Vec3 pos, String owner) {
        long now = now(level);
        for (TombstoneUnit s : stones)
            if (s.level() == level && s.isAlive() && owner.equals(s.getOwnerName()) && within(s.position(), s.creepRadius(now), pos))
                return true;
        for (Fade f : fades)
            if (f.level == level && owner.equals(f.owner) && within(f.centre, f.radius(now), pos))
                return true;
        return false;
    }

    /** The units standing on {@code caster}'s creep (Grave Tide's second reach). */
    public static List<LivingEntity> unitsOnCreepOf(LivingEntity caster) {
        List<LivingEntity> out = new ArrayList<>();
        if (!(caster.level() instanceof ServerLevel sl))
            return out;
        UUID id = caster.getUUID();
        long now = now(sl);
        for (TombstoneUnit s : stones)
            if (s.level() == sl && s.isAlive() && id.equals(s.casterId()))
                collect(sl, s.position(), s.creepRadius(now), out);
        for (Fade f : fades)
            if (f.level == sl && id.equals(f.caster))
                collect(sl, f.centre, f.radius(now), out);
        return out;
    }

    private static void collect(ServerLevel sl, Vec3 centre, float r, List<LivingEntity> out) {
        if (r <= 0)
            return;
        for (LivingEntity le : UnitGrid.near(sl, centre.x, centre.z, r, scratch))
            if (le.isAlive() && within(centre, r, le.position()) && !out.contains(le))
                out.add(le);
        scratch.clear();
    }

    /** The reach hook: a Tombcaller takes the wisps on its own creep from 12 blocks. */
    static double pullReach(LivingEntity carrier, Entity wisp) {
        return carrier instanceof TombcallerUnit && onCreepOf(carrier, wisp.position()) ? TombstoneUnit.CREEP_PULL_RANGE : 0;
    }

    /** Gravebound, and its owner's or an ally's: the creep heals it and Grave Tide hastens it. */
    public static boolean isGraveboundFriend(LivingEntity le, String owner) {
        return le instanceof Unit u && !(le instanceof TombstoneUnit) && Factions.getFaction(u) == Factions.MONSTERS
            && AlliancesServerEvents.isAlliedOrOwned(u.getOwnerName(), owner);
    }

    /** Another player's unit (not a neutral one) that is not allied: the creep slows it. */
    static boolean isFoe(LivingEntity le, String owner) {
        if (!(le instanceof Unit u))
            return false;
        String o = u.getOwnerName();
        return o != null && !o.isEmpty() && !AlliancesServerEvents.isAlliedOrOwned(o, owner);
    }

    // ------------------------------------------------------------------------------------------ Grave Tide

    /** The game time {@code le}'s Grave Tide ends (0: none on it). */
    public static long tideEnd(LivingEntity le) {
        Long end = tides.get(le);
        return end == null ? 0 : end;
    }

    /** Hastens {@code le} until {@code endTick} (a later end of an earlier tide is kept). */
    public static void hasten(LivingEntity le, long endTick) {
        Long had = tides.get(le);
        tides.put(le, had == null ? endTick : Math.max(had, endTick));
        AttributeInstance speed = le.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(TIDE_SPEED_ID) == null)
            speed.addTransientModifier(new AttributeModifier(TIDE_SPEED_ID, "Grave Tide", GraveTide.SPEED_BONUS,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private static void removeModifier(LivingEntity le, UUID id) {
        AttributeInstance speed = le.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(id) != null)
            speed.removeModifier(id);
    }

    // ------------------------------------------------------------------------------------------ the events

    /** A stone or a Risen Skeleton joining (raised, or coming back with its chunk): watched. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent evt) {
        if (evt.getLevel().isClientSide())
            return;
        if (evt.getEntity() instanceof TombstoneUnit s) {
            away.remove(s.getUUID());
            if (!stones.contains(s))
                stones.add(s);
        }
        else if (evt.getEntity() instanceof Mob m && isRisen(m) && !risen.contains(m))
            risen.add(m);
    }

    /** A stone that falls leaves its creep to fade. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent evt) {
        if (!(evt.getEntity() instanceof TombstoneUnit s) || !(s.level() instanceof ServerLevel sl))
            return;
        float r = s.creepRadius(now(sl));
        if (r > 0)
            fades.add(new Fade(sl, s.position(), s.getOwnerName(), s.casterId(), r, now(sl)));
        stones.remove(s);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.getServer() == null)
            return;
        // every dimension reads the overworld's clock (as the wisps do): the stones' and skeletons' times are game times
        long now = evt.getServer().overworld().getGameTime();
        tickStones(now);
        tickRisen(now);
        tickTides(now);
        fades.removeIf(f -> f.radius(now) <= 0);
        away.values().removeIf(a -> a.end <= now);
        if (now % EFFECT_TICKS == 0)
            tickCreep(now);
    }

    private static void tickStones(long now) {
        for (int i = stones.size() - 1; i >= 0; i--) {
            TombstoneUnit s = stones.get(i);
            if (s.isRemoved() || !s.isAlive()) {
                // a death left its fade in onDeath; an unload stops watching it but it still counts toward the cap
                if (s.isAlive() && s.getRemovalReason() != null && !s.getRemovalReason().shouldDestroy() && s.casterId() != null)
                    away.put(s.getUUID(), new Away(s.casterId(), s.end()));
                stones.remove(i);
                continue;
            }
            if (!(s.level() instanceof ServerLevel sl))
                continue;
            if (now >= s.end()) {
                s.kill();   // its 90 s are up: dies as a summon (no wisp), its creep fades
                continue;
            }
            if (now >= s.nextRaise()) {
                // every 20 s whatever still stands (lovish, Oct 29): a skeleton lives 30 s, so two overlap for 10 s
                s.setNextRaise(now + TombstoneUnit.RAISE_EVERY_SECONDS * 20L);
                raise(sl, s, now);
            }
        }
    }

    /** One Risen Skeleton beside {@code stone}, for its owner, marked a summon before it joins. */
    private static void raise(ServerLevel sl, TombstoneUnit stone, long now) {
        EntityType<? extends Mob> type = EntityRegistrar.SKELETON_UNIT.get();
        Mob m = type.create(sl);
        if (m == null)
            return;
        double ang = sl.random.nextDouble() * Math.PI * 2;
        Vec3 at = new Vec3(stone.getX() + Math.cos(ang) * 1.2, stone.getY(), stone.getZ() + Math.sin(ang) * 1.2);
        BlockPos below = BlockPos.containing(at.x, at.y - 0.5, at.z);
        if (!sl.noCollision(type.getDimensions().makeBoundingBox(at)) || sl.getBlockState(below).getCollisionShape(sl, below).isEmpty())
            at = stone.position();
        m.moveTo(at.x, at.y, at.z, (float) Math.toDegrees(ang), 0);
        ((Unit) m).setOwnerName(stone.getOwnerName());
        Essence.markSummoned(m);
        m.addTag(RISEN_TAG);
        m.getPersistentData().putLong(RISEN_END, now + TombstoneUnit.SKELETON_LIFETIME_SECONDS * 20L);
        m.getPersistentData().putUUID(RISEN_STONE, stone.getUUID());
        sl.addFreshEntity(m);
        sl.sendParticles(ParticleTypes.SOUL, m.getX(), m.getY() + 0.5, m.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
    }

    private static void tickRisen(long now) {
        for (int i = risen.size() - 1; i >= 0; i--) {
            Mob m = risen.get(i);
            if (m.isRemoved() || !m.isAlive()) {
                risen.remove(i);
                continue;
            }
            if (now >= risenEnd(m)) {
                if (m.level() instanceof ServerLevel sl)
                    sl.sendParticles(ParticleTypes.SOUL, m.getX(), m.getY() + 0.8, m.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
                m.discard();
                risen.remove(i);
            }
        }
    }

    private static void tickTides(long now) {
        if (tides.isEmpty())
            return;
        boolean heal = now % EFFECT_TICKS == 0;
        float amount = GraveTide.REGEN_PER_SECOND * EFFECT_TICKS / 20f;
        var it = tides.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            LivingEntity le = e.getKey();
            if (le.isRemoved() || !le.isAlive() || now >= e.getValue()) {
                removeModifier(le, TIDE_SPEED_ID);
                it.remove();
            } else if (heal) {
                le.heal(amount);
            }
        }
    }

    /** The creep's heal and slow, and its look, for every standing and fading disc. */
    private static void tickCreep(long now) {
        Set<LivingEntity> heal = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<LivingEntity> slow = Collections.newSetFromMap(new IdentityHashMap<>());
        for (TombstoneUnit s : stones)
            if (s.isAlive() && s.level() instanceof ServerLevel sl)
                creepAt(sl, s.position(), s.creepRadius(now), s.getOwnerName(), now, heal, slow);
        for (Fade f : fades)
            if (f.level instanceof ServerLevel sl)
                creepAt(sl, f.centre, f.radius(now), f.owner, now, heal, slow);
        float amount = TombstoneUnit.CREEP_REGEN_PER_SECOND * EFFECT_TICKS / 20f;
        for (LivingEntity le : heal)
            le.heal(amount);
        for (LivingEntity le : slowed)
            if (!slow.contains(le))
                removeModifier(le, CREEP_SLOW_ID);
        for (LivingEntity le : slow) {
            AttributeInstance speed = le.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null && speed.getModifier(CREEP_SLOW_ID) == null)
                speed.addTransientModifier(new AttributeModifier(CREEP_SLOW_ID, "Grave creep", -TombstoneUnit.CREEP_ENEMY_SLOW,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        slowed.clear();
        slowed.addAll(slow);
    }

    private static void creepAt(ServerLevel sl, Vec3 c, float r, String owner, long now, Set<LivingEntity> heal, Set<LivingEntity> slow) {
        if (r <= 0 || owner == null || owner.isEmpty())
            return;
        for (LivingEntity le : UnitGrid.near(sl, c.x, c.z, r, scratch)) {
            if (!le.isAlive() || !within(c, r, le.position()))
                continue;
            if (isGraveboundFriend(le, owner)) {
                if (le.getHealth() < le.getMaxHealth())
                    heal.add(le);
            } else if (isFoe(le, owner)) {
                slow.add(le);
            }
        }
        scratch.clear();
        if (now % 20 == 0) {
            // soul flames round the rim and a few inside: the creep's extent at a glance (placeholder look)
            int rim = Math.max(4, Math.round(r * 2));
            for (int k = 0; k < rim; k++) {
                double a = Math.PI * 2 * k / rim;
                sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r, 1, 0.05, 0, 0.05, 0);
            }
            for (int k = 0; k < 3; k++) {
                double a = sl.random.nextDouble() * Math.PI * 2, d = Math.sqrt(sl.random.nextDouble()) * r;
                sl.sendParticles(ParticleTypes.SCULK_SOUL, c.x + Math.cos(a) * d, c.y + 0.2, c.z + Math.sin(a) * d, 1, 0, 0.05, 0, 0.01);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ shared

    /** Tells {@code owner} why a cast was refused, at most once every {@link #WARN_EVERY_TICKS} (autocast retries). */
    public static void warn(ServerLevel sl, String owner, String key) {
        if (owner == null || owner.isEmpty())
            return;
        long now = sl.getGameTime();
        Long last = lastWarn.get(owner);
        if (last != null && now - last < WARN_EVERY_TICKS && now >= last)
            return;
        lastWarn.put(owner, now);
        HudClientboundPacket.showTempMessageI18n(owner, key);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        stones.clear();
        fades.clear();
        risen.clear();
        away.clear();
        tides.clear();
        slowed.clear();
        lastWarn.clear();
    }
}
