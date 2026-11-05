package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.abilities.Empower;
import com.solegendary.reignofnether.ability.abilities.Hex;
import com.solegendary.reignofnether.barfx.BarFx;
import com.solegendary.reignofnether.barfx.RuneFx;
import com.solegendary.reignofnether.barfx.RunePool;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Rune magic (lovish, Nov 5: "I want to add rune magic effects that ive seen in other minecraft mods and games"): every
 * essence caster's spell draws its faction's rune circle under the caster (barfx/RuneFx), started at the one call every
 * caster spell passes - Essence.trySpend, which a caster makes last, so a spend is a cast. The circle is a BarFx event
 * like a shot or a dome: sent once, to the players whose fog shows the caster, never to the fogged.
 * <ul>
 *   <li>six casters of six factions each cast a real spell (Empower; the Voodoo Shaman also Hex): each cast queues
 *       exactly one RUNE start for that caster, in its own faction's style, lasting {@link RuneFx#CAST_TICKS}; the real
 *       per-player send rule (BarFx.eventsFor) gives it to a fogged player whose unit sees the caster and not to a
 *       fogged player whose units are far away (who does get it with fog off: the control);</li>
 *   <li>the circle lives exactly the cast time: live on every tick from the cast to {@code CAST_TICKS - 1}, gone at
 *       {@code CAST_TICKS}, on the server (RuneFx.isLive) and in the client's pool (RunePool.alive, same rule in
 *       seconds); a caster that dies mid-cast ends its circle at once (a RUNE end event);</li>
 *   <li>the caps: at most {@link RuneFx#MAX_LIVE} circles live per level on the server, {@link RunePool#MAX} in the
 *       client's pool (which never grows).</li>
 * </ul>
 * The test server runs with fog off for everyone, so the send rule is called with the vision the fog tick computes
 * (FogOfWarServerEvents.recomputeVisionFor + isColumnVisibleUnderFog), like FogHidesEnemiesGameTests.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class RuneFxGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "rune_fx";

    /** One caster per faction that has one (the Light Grid has no essence caster yet). */
    static final List<Supplier<? extends EntityType<? extends Mob>>> CASTERS = List.of(
        EntityRegistrar.ANGEL_CALLER_UNIT, EntityRegistrar.TOMBCALLER_UNIT, EntityRegistrar.VOODOO_SHAMAN_UNIT,
        EntityRegistrar.THORNWEAVER_UNIT, EntityRegistrar.SPARK_SAGE_UNIT, EntityRegistrar.SOUL_ENGINEER_UNIT);

    static Mob spawn(GameTestHelper helper, EntityType<? extends Mob> type, String owner, int x, int z) {
        Mob m = type.create(helper.getLevel());
        if (m == null)
            return null;
        BlockPos p = CommandQueueGameTests.at(helper, x, z);
        m.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
        ((Unit) m).setOwnerName(owner);
        m.setNoAi(true);
        helper.getLevel().addFreshEntity(m);
        return m;
    }

    /** The queued RUNE events about {@code id} (BarFx.queued: this tick's events, before the end-of-tick send). */
    static List<BarFx.Event> runes(ServerLevel level, int id) {
        List<BarFx.Event> out = new ArrayList<>();
        for (BarFx.Event e : BarFx.queued(level))
            if (e.type == BarFx.RUNE && Math.round(e.c) == id)
                out.add(e);
        return out;
    }

    static int count(List<BarFx.Event> events, int type) {
        int n = 0;
        for (BarFx.Event e : events)
            if (e.type == type)
                n++;
        return n;
    }

    /** The vision the fog tick stored for {@code p} (fog is off globally in the test server). */
    static BarFx.Sight storedVision(ServerPlayer p) {
        return (x, z) -> FogOfWarServerEvents.isColumnVisibleUnderFog(p, Mth.floor(x), Mth.floor(z));
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void rune_cast_reaches_who_sees_the_caster_and_not_the_fogged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = CommandQueueGameTests.owner(helper, "rn"), seer = CommandQueueGameTests.owner(helper, "rs"),
            blind = CommandQueueGameTests.owner(helper, "rb"), foe = CommandQueueGameTests.owner(helper, "rf");
        ServerPlayer seerP = CommandQueueGameTests.fakePlayer(level, seer), blindP = CommandQueueGameTests.fakePlayer(level, blind);
        BlockPos o = helper.absolutePos(BlockPos.ZERO);
        // park both viewers' own bodies far away: only their units' sight may cover the casters
        seerP.moveTo(o.getX() + 4000, o.getY(), o.getZ() + 4000, 0, 0);
        blindP.moveTo(o.getX() - 4000, o.getY(), o.getZ() - 4000, 0, 0);

        List<Mob> made = new ArrayList<>();
        List<Mob> casters = new ArrayList<>(), allies = new ArrayList<>();
        for (int i = 0; i < CASTERS.size(); i++) {
            Mob c = spawn(helper, CASTERS.get(i).get(), me, 2, 2 + 2 * i);
            Mob a = CommandQueueGameTests.fighter(helper, me, 4, 2 + 2 * i);
            if (c == null || a == null) {
                made.forEach(Mob::discard);
                if (c != null) c.discard();
                if (a != null) a.discard();
                helper.fail("could not create caster " + i);
                return;
            }
            casters.add(c);
            allies.add(a);
            made.add(c);
            made.add(a);
        }
        Mob hexTarget = CommandQueueGameTests.fighter(helper, foe, 8, 6);
        Mob eye = CommandQueueGameTests.fighter(helper, seer, 6, 7);
        Mob farEye = hexTarget == null || eye == null ? null : CommandQueueGameTests.fighter(helper, blind, 2, 2);
        if (hexTarget == null || eye == null || farEye == null) {
            made.forEach(Mob::discard);
            if (hexTarget != null) hexTarget.discard();
            if (eye != null) eye.discard();
            helper.fail("could not create the viewers' units");
            return;
        }
        made.add(hexTarget);
        made.add(eye);
        made.add(farEye);
        TestCleanup.entities(helper, made);
        int sight = ((Unit) eye).getSightRange();
        // the blind viewer's only unit stands well outside sight of every caster
        farEye.moveTo(o.getX() + 2 + sight + 40, o.getY() + 2, o.getZ() + 2, 0, 0);

        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (sight < 8 || sight > FogOfWarServerEvents.MAX_SIGHT_BLOCKS - 8) {
                    helper.fail("unexpected sight range " + sight + " for the seer's unit (the layout needs 8+)");
                    return;
                }
                FogOfWarServerEvents.recomputeVisionFor(seerP);
                FogOfWarServerEvents.recomputeVisionFor(blindP);
                // vision is stored; now bring both bodies over the arena, inside BarFx.SEND_RANGE (an RTS camera's body
                // follows the camera), so only the fog can keep a rune from the blind viewer
                seerP.moveTo(o.getX() + 8, o.getY() + 30, o.getZ() + 8, 0, 0);
                blindP.moveTo(o.getX() + 8, o.getY() + 30, o.getZ() + 8, 0, 0);
                Set<Integer> styles = new HashSet<>();
                for (int i = 0; i < casters.size(); i++) {
                    Mob c = casters.get(i);
                    String who = c.getType().getDescriptionId();
                    // the setup the rule below depends on: the seer's vision covers the caster, the blind one's doesn't
                    if (!storedVision(seerP).canSee(c.getX(), c.getZ()))
                        problems.add(who + ": setup - the seer's unit 'sees' no caster column (sight " + sight + ")");
                    if (storedVision(blindP).canSee(c.getX(), c.getZ()))
                        problems.add(who + ": setup - the blind viewer's far unit covers the caster");
                    if (!runes(level, c.getId()).isEmpty())
                        problems.add(who + ": a rune was queued before any cast");
                    Essence.add(c, Empower.ESSENCE_COST);
                    if (!Empower.cast(level, c, me, allies.get(i), true)) {
                        problems.add(who + ": Empower was refused (essence " + Essence.get(c) + ")");
                        continue;
                    }
                    List<BarFx.Event> rs = runes(level, c.getId());
                    if (rs.size() != 1) {
                        problems.add(who + ": one cast queued " + rs.size() + " rune events, not 1");
                        continue;
                    }
                    BarFx.Event e = rs.get(0);
                    if ((e.flags & 3) != BarFx.RUNE_START || (e.flags & BarFx.RUNE_FOLLOW) == 0)
                        problems.add(who + ": the rune event is not a following start (flags " + e.flags + ")");
                    if (Math.round(e.b) != RuneFx.CAST_TICKS)
                        problems.add(who + ": the rune lasts " + e.b + " ticks, not the cast time " + RuneFx.CAST_TICKS);
                    if (e.kind != BarFx.factionOf(c) || e.kind == BarFx.F_NONE)
                        problems.add(who + ": rune style " + e.kind + ", the caster's faction is " + BarFx.factionOf(c));
                    styles.add((int) e.kind);
                    if (Math.abs(e.x - c.getX()) > 0.01 || Math.abs(e.z - c.getZ()) > 0.01)
                        problems.add(who + ": the rune is not under its caster");
                    // the real per-player send rule
                    int seen = count(BarFx.eventsFor(seerP, rs, true, storedVision(seerP)), BarFx.RUNE);
                    int fogged = count(BarFx.eventsFor(blindP, rs, true, storedVision(blindP)), BarFx.RUNE);
                    int fogOff = count(BarFx.eventsFor(blindP, rs, false, storedVision(blindP)), BarFx.RUNE);
                    if (seen != 1)
                        problems.add(who + ": the player who sees the caster gets " + seen + " rune events, not 1");
                    if (fogged != 0)
                        problems.add(who + ": the fogged player gets " + fogged + " rune events, not 0");
                    if (fogOff != 1)
                        problems.add(who + ": control - with fog off the far player gets " + fogOff + ", not 1");
                }
                if (problems.isEmpty() && styles.size() != CASTERS.size())
                    problems.add("six factions' casters drew only " + styles.size() + " rune styles " + styles);
                // a second spell kind: the Voodoo Shaman hexes an enemy (a new cast while its circle lives: one new start)
                Mob shaman = casters.get(2);
                int before = runes(level, shaman.getId()).size();
                Essence.add(shaman, Hex.ESSENCE_COST);
                if (!Hex.cast(level, shaman, me, hexTarget, true))
                    problems.add("Hex was refused (essence " + Essence.get(shaman) + ")");
                else if (runes(level, shaman.getId()).size() - before != 1)
                    problems.add("a Hex cast queued " + (runes(level, shaman.getId()).size() - before) + " rune events, not 1");
            } finally {
                FogOfWarServerEvents.onPlayerDisconnect(seerP.getUUID());
                FogOfWarServerEvents.onPlayerDisconnect(blindP.getUUID());
            }
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void rune_circle_lives_exactly_the_cast_time_and_ends_with_its_caster(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = CommandQueueGameTests.owner(helper, "rl");
        Mob sage = spawn(helper, EntityRegistrar.SPARK_SAGE_UNIT.get(), me, 2, 2);
        Mob doomed = spawn(helper, EntityRegistrar.SOUL_ENGINEER_UNIT.get(), me, 2, 8);
        Mob a1 = CommandQueueGameTests.fighter(helper, me, 4, 2), a2 = CommandQueueGameTests.fighter(helper, me, 4, 8);
        List<Mob> made = new ArrayList<>();
        for (Mob m : new Mob[] { sage, doomed, a1, a2 })
            if (m != null)
                made.add(m);
        if (made.size() != 4) {
            made.forEach(Mob::discard);
            helper.fail("could not create the units");
            return;
        }
        TestCleanup.entities(helper, made);
        List<String> problems = new ArrayList<>();
        long[] castAt = { -1 };
        boolean[] sawLastTick = { false }, sawGone = { false };

        helper.runAfterDelay(2, () -> {
            Essence.add(sage, Empower.ESSENCE_COST);
            if (!Empower.cast(level, sage, me, a1, true)) {
                helper.fail("Empower was refused");
                return;
            }
            castAt[0] = level.getGameTime();
            if (!RuneFx.isLive(level, sage.getId()))
                problems.add("no live circle on the tick of the cast");
            // a caster that dies mid-cast ends its circle at once
            Essence.add(doomed, Empower.ESSENCE_COST);
            if (!Empower.cast(level, doomed, me, a2, true))
                problems.add("the second Empower was refused");
            else if (!RuneFx.isLive(level, doomed.getId()))
                problems.add("no live circle for the second caster");
            doomed.die(level.damageSources().genericKill());   // LivingDeathEvent, as a killing blow posts it
            if (RuneFx.isLive(level, doomed.getId()))
                problems.add("a dead caster's circle is still live");
            List<BarFx.Event> rs = runes(level, doomed.getId());
            int ends = 0;
            for (BarFx.Event e : rs)
                if ((e.flags & 3) == BarFx.RUNE_END)
                    ends++;
            if (ends != 1)
                problems.add("the caster's death queued " + ends + " rune end events, not 1");
        });
        helper.onEachTick(() -> {
            if (castAt[0] < 0)
                return;
            long age = level.getGameTime() - castAt[0];
            boolean live = RuneFx.isLive(level, sage.getId());
            if (age < RuneFx.CAST_TICKS && !live)
                problems.add("gone " + age + " ticks into a " + RuneFx.CAST_TICKS + "-tick cast");
            if (age >= RuneFx.CAST_TICKS && live)
                problems.add("still live " + age + " ticks into a " + RuneFx.CAST_TICKS + "-tick cast");
            if (age == RuneFx.CAST_TICKS - 1 && live)
                sawLastTick[0] = true;
            if (age == RuneFx.CAST_TICKS && !live)
                sawGone[0] = true;
        });
        helper.runAfterDelay(2 + RuneFx.CAST_TICKS + 4, () -> {
            if (!sawLastTick[0])
                problems.add("never saw the circle live on its last tick (" + (RuneFx.CAST_TICKS - 1) + ")");
            if (!sawGone[0])
                problems.add("never saw the circle gone on tick " + RuneFx.CAST_TICKS);
            // the client's pool: the same lifetime, counted in seconds of its own clock
            RunePool pool = new RunePool();
            float born = 10f;
            int slot = pool.start(sage.getId(), 0, 0, 0, 1.6f, BarFx.F_STORM, true, born, RuneFx.CAST_TICKS);
            if (slot < 0)
                problems.add("the client pool refused a circle");
            for (int t = 0; t <= RuneFx.CAST_TICKS; t++) {
                boolean alive = pool.alive(sage.getId(), born + t / 20f);
                if (t < RuneFx.CAST_TICKS && !alive)
                    problems.add("client: gone at tick " + t + " of " + RuneFx.CAST_TICKS);
                if (t == RuneFx.CAST_TICKS && alive)
                    problems.add("client: still drawn at tick " + t + " of " + RuneFx.CAST_TICKS);
            }
            pool.end(sage.getId(), born + 0.2f);
            if (pool.alive(sage.getId(), born + 0.2f + RunePool.END_FADE + 0.01f))
                problems.add("client: an ended circle outlived its " + RunePool.END_FADE + " s fade");
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems.subList(0, Math.min(6, problems.size()))));
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void rune_circles_are_capped_on_the_server_and_in_the_client_pool(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.runAfterDelay(1, () -> {
            List<String> problems = new ArrayList<>();
            Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3)));
            int extra = 10, started = 0;
            int free = RuneFx.MAX_LIVE - RuneFx.liveCount(level);   // other tests of the batch may hold live circles
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < RuneFx.MAX_LIVE + extra; i++) {
                int id = BarFx.nextDomeId();   // never an entity id
                ids.add(id);
                if (RuneFx.start(level, id, at, BarFx.F_SUNFORGED, RuneFx.CAST_RADIUS, RuneFx.CAST_TICKS, false))
                    started++;
            }
            int queued = 0;
            for (int id : ids)
                queued += runes(level, id).size();
            if (started != free)
                problems.add(started + " of " + (RuneFx.MAX_LIVE + extra) + " circles started with " + free + " free under the cap "
                    + RuneFx.MAX_LIVE);
            if (queued != started)
                problems.add(queued + " rune events queued for " + started + " started circles");
            if (RuneFx.liveCount(level) > RuneFx.MAX_LIVE)
                problems.add(RuneFx.liveCount(level) + " circles live, over the cap " + RuneFx.MAX_LIVE);
            int live = RuneFx.liveCount(level);
            for (int id : ids)
                RuneFx.stop(level, id);
            if (live - RuneFx.liveCount(level) != started)
                problems.add("stopping " + started + " circles freed " + (live - RuneFx.liveCount(level)));
            // the client pool: full at MAX, the newest replaces the one closest to its end, and it never grows
            RunePool pool = new RunePool();
            int refused = 0;
            for (int i = 0; i < RunePool.MAX + extra; i++)
                if (pool.start(i + 1, 0, 0, 0, 1.6f, BarFx.F_HORDE, false, i * 0.01f, RuneFx.CAST_TICKS) < 0)
                    refused++;
            if (pool.count() != RunePool.MAX)
                problems.add("client pool holds " + pool.count() + ", its cap is " + RunePool.MAX);
            if (refused != 0)
                problems.add("client pool refused " + refused + " new circles instead of replacing the oldest");
            if (!pool.alive(RunePool.MAX + extra, RunePool.MAX * 0.01f + 0.1f))
                problems.add("client pool dropped the newest circle");
            if (pool.alive(1, RunePool.MAX * 0.01f + 0.1f))
                problems.add("client pool kept the oldest circle past its cap");
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }
}
