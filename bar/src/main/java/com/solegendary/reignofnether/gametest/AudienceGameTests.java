package com.solegendary.reignofnether.gametest;

import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.hero.HeroClientboundPacket;
import com.solegendary.reignofnether.hero.HeroRoles;
import com.solegendary.reignofnether.network.Audience;
import com.solegendary.reignofnether.network.Audience.Policy;
import com.solegendary.reignofnether.network.AudienceResync;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchClientboundPacket;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesClientboundPacket;
import com.solegendary.reignofnether.sounds.SoundAction;
import com.solegendary.reignofnether.sounds.SoundClientboundPacket;
import com.solegendary.reignofnether.startpos.CapturePointServerEvents;
import com.solegendary.reignofnether.unit.UnitKills;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.unit.packets.UnitKillsClientboundPacket;
import com.solegendary.reignofnether.unit.packets.UnitSyncAbilityClientboundPacket;
import com.solegendary.reignofnether.unit.packets.UnitSyncClientboundPacket;
import com.solegendary.reignofnether.unit.packets.UnitSyncMobEffectsClientboundPacket;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * Rewrite 3, the networking audience policy (network/Audience, design/ron_rewrite_plan.md): recipient sets per policy
 * for an owner, an ally, an enemy, a spectator and a Hero-role player of each side (pure rules over fake facts, like
 * MapDrawRules.recipients); the live facts reading alliances, Hero roles and shared control; what AudienceResync sends
 * when a unit comes into view; R2-10 (capture points no longer tag every joining entity); and the 8v8 measurement:
 * how many deliveries the narrowed audiences save over sending everything to everyone. Unique names everywhere, and
 * every state change is undone in the same callback, so parallel tests never see any of it.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class AudienceGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** A small world for the pure rules: seats on sides, Hero roles, spectators, sandbox editors, shared control. */
    static final class FakeFacts implements Audience.Facts {
        final Map<String, Integer> side = new HashMap<>();
        final Map<String, List<String>> heroes = new HashMap<>();
        final Set<String> spectators = new HashSet<>();
        final Set<String> sandbox = new HashSet<>();
        final Set<String> sharesControl = new HashSet<>();

        @Override
        public boolean allied(String a, String b) {
            Integer sa = side.get(a), sb = side.get(b);
            return sa != null && sa.equals(sb) && !a.equals(b);
        }

        @Override
        public List<String> commandersOf(String player) {
            return heroes.getOrDefault(player, List.of());
        }

        @Override
        public boolean spectator(String player) {
            return spectators.contains(player) || sandbox.contains(player);
        }

        @Override
        public boolean mayCommand(String player, String owner) {
            return player.equals(owner) || sandbox.contains(player) || (allied(player, owner) && sharesControl.contains(owner));
        }
    }

    static void expect(List<String> problems, String what, Set<String> got, String... want) {
        Set<String> w = new LinkedHashSet<>(List.of(want));
        if (!got.equals(w))
            problems.add(what + ": got " + got + ", want " + w);
    }

    /**
     * Every policy's recipients for a unit of a1, with a1 + a2 against b1 + b2, a Hero-role player for each side, a
     * spectator and a sandbox editor online, and only b1's fog showing the spot.
     */
    @GameTest(template = ARENA)
    public static void audience_recipients_per_policy(GameTestHelper helper) {
        FakeFacts f = new FakeFacts();
        f.side.put("a1", 0);
        f.side.put("a2", 0);
        f.side.put("b1", 1);
        f.side.put("b2", 1);
        f.heroes.put("heroA", List.of("a1"));
        f.heroes.put("heroB", List.of("b1"));
        f.spectators.add("spec");
        f.sandbox.add("sand");
        f.sharesControl.add("a1");
        List<String> online = List.of("a1", "a2", "b1", "b2", "heroA", "heroB", "spec", "sand");
        Predicate<String> b1Sees = p -> p.equals("b1");
        List<String> problems = new ArrayList<>();

        expect(problems, "OWNER", Audience.recipients(Policy.OWNER, "a1", online, f, b1Sees), "a1");
        expect(problems, "COMMANDERS (a1 shares control)", Audience.recipients(Policy.COMMANDERS, "a1", online, f, b1Sees),
            "a1", "a2", "sand");
        expect(problems, "ALLIES", Audience.recipients(Policy.ALLIES, "a1", online, f, b1Sees), "a1", "a2", "heroA");
        expect(problems, "TEAM", Audience.recipients(Policy.TEAM, "a1", online, f, b1Sees),
            "a1", "a2", "heroA", "spec", "sand");
        expect(problems, "SEEN (b1 sees it)", Audience.recipients(Policy.SEEN, "a1", online, f, b1Sees),
            "a1", "a2", "b1", "heroA", "spec", "sand");
        expect(problems, "SEEN (nobody else sees it)", Audience.recipients(Policy.SEEN, "a1", online, f, p -> false),
            "a1", "a2", "heroA", "spec", "sand");
        expect(problems, "SPECTATORS", Audience.recipients(Policy.SPECTATORS, "a1", online, f, b1Sees), "spec", "sand");
        expect(problems, "ALL", Audience.recipients(Policy.ALL, "a1", online, f, b1Sees), online.toArray(new String[0]));

        // the enemy side's Hero-role player: what their team's fog shows, never the side's private state
        Predicate<String> sideBSees = p -> p.equals("b1") || p.equals("b2") || p.equals("heroB");
        expect(problems, "SEEN (side b's fog shows it)", Audience.recipients(Policy.SEEN, "a1", online, f, sideBSees),
            online.toArray(new String[0]));
        expect(problems, "TEAM ignores sight", Audience.recipients(Policy.TEAM, "a1", online, f, sideBSees),
            "a1", "a2", "heroA", "spec", "sand");
        // and the other way round: heroB is on b2's side (b2 is allied to heroB's commander)
        expect(problems, "ALLIES of b2", Audience.recipients(Policy.ALLIES, "b2", online, f, b1Sees), "b1", "b2", "heroB");

        f.sharesControl.clear();
        expect(problems, "COMMANDERS (no shared control)", Audience.recipients(Policy.COMMANDERS, "a1", online, f, b1Sees),
            "a1", "sand");
        // neutral things have no side: spectators, plus whoever sees them
        expect(problems, "ALLIES of a neutral", Audience.recipients(Policy.ALLIES, "", online, f, b1Sees));
        expect(problems, "TEAM of a neutral", Audience.recipients(Policy.TEAM, "", online, f, b1Sees), "spec", "sand");
        expect(problems, "SEEN of a neutral", Audience.recipients(Policy.SEEN, "", online, f, b1Sees), "b1", "spec", "sand");

        List<String> owners = List.of("a1", "a2", "b1", "b2");
        expect(problems, "knownOwners(heroA)", Audience.knownOwners("heroA", owners, f), "a1", "a2");
        expect(problems, "knownOwners(b2)", Audience.knownOwners("b2", owners, f), "b1", "b2");
        expect(problems, "knownOwners(spec)", Audience.knownOwners("spec", owners, f), "a1", "a2", "b1", "b2");

        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * The live facts read the real registries: alliances (and a Hero serving one side's commander is on its allies'
     * side too), Hero roles, allied control, and "no seat and no Hero role" = spectator. Unique names; undone after.
     */
    @GameTest(template = ARENA)
    public static void audience_live_facts_follow_alliances_and_hero_roles(GameTestHelper helper) {
        String a = "gt_aud_" + UUID.randomUUID().toString().substring(0, 8), b = a + "_ally", c = a + "_enemy",
            hero = a + "_hero";
        List<String> problems = new ArrayList<>();
        Audience.Facts live = Audience.LIVE;
        AlliancesServerEvents.addAlliance(a, b);
        HeroRoles.assign(hero, List.of(a));
        try {
            if (!live.allied(a, b) || !live.allied(b, a) || live.allied(a, c))
                problems.add("alliances not read: a~b " + live.allied(a, b) + ", a~c " + live.allied(a, c));
            if (!live.commandersOf(hero).equals(List.of(a)) || !live.commandersOf(c).isEmpty())
                problems.add("Hero roles not read: " + live.commandersOf(hero));
            if (!Audience.onSide(hero, a, live) || !Audience.onSide(hero, b, live) || Audience.onSide(hero, c, live))
                problems.add("a Hero serving a must be on a's side, and its ally b's, not c's");
            // a side's audience and its shared vision must never disagree (FogOfWarServerEvents.teamOwnersFor). The
            // side itself (onSide), not knownOwners: a and b hold no seat here, so the live facts call them spectators
            for (String viewer : List.of(a, b, hero)) {
                Set<String> fog = new HashSet<>(com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents
                    .teamOwnersFor(viewer, List.of(a, b, c)));
                Set<String> side = new HashSet<>();
                for (String o : List.of(a, b, c))
                    if (Audience.onSide(viewer, o, live))
                        side.add(o);
                if (!fog.equals(side))
                    problems.add(viewer + ": fog shares " + fog + " but the audience side is " + side);
            }
            if (!live.spectator(c))
                problems.add("a player with no seat and no Hero role must count as a spectator");
            if (live.spectator(hero))
                problems.add("a Hero-role player is not a spectator: they play under their team's fog");
            if (live.mayCommand(b, a))
                problems.add("an ally commands a's units without shared control");
            AlliancesServerEvents.playersWithAlliedControl.add(a);
            try {
                if (!live.mayCommand(b, a) || live.mayCommand(c, a))
                    problems.add("shared control: ally " + live.mayCommand(b, a) + ", enemy " + live.mayCommand(c, a));
            } finally {
                AlliancesServerEvents.playersWithAlliedControl.remove(a);
            }
        } finally {
            AlliancesServerEvents.removeAlliance(a, b);
            HeroRoles.unassign(hero);
        }
        if (live.allied(a, b) || HeroRoles.isHeroRole(hero))
            problems.add("test state not undone");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    static int count(List<Object> sent, Class<?> type) {
        int n = 0;
        for (Object o : sent)
            if (type.isInstance(o))
                n++;
        return n;
    }

    static void expectResync(List<String> problems, String what, List<Object> sent, Runnable run, int effects, int kills,
                             int hero, int cooldowns) {
        sent.clear();
        run.run();
        int e = count(sent, UnitSyncMobEffectsClientboundPacket.class), k = count(sent, UnitKillsClientboundPacket.class);
        int h = count(sent, HeroClientboundPacket.class), c = count(sent, UnitSyncAbilityClientboundPacket.class);
        if (e != effects || k != kills || h != hero || c != cooldowns || sent.size() != e + k + h + c)
            problems.add(what + ": " + e + " effects, " + k + " kills, " + h + " hero, " + c + " cooldowns, " + sent.size()
                + " in all; want " + effects + " / " + kills + " / " + hero + " / " + cooldowns);
    }

    /**
     * What a player gets when a unit comes into their knowledge (AudienceResync.onUnitKnown), for a hero with a status
     * effect and three kills: a client that only just loaded it gets effects and kills (it asks for the rest itself);
     * an enemy who sees it again also gets its XP, ranks and mana but not its skill points or charges; its own side
     * gets those too; a view change (a defeat: now a spectator) adds the cooldowns. Sent through the sink, no
     * connection needed.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void audience_resync_sends_what_a_newly_visible_unit_needs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var hero = EntityRegistrar.DAWN_PALADIN_UNIT.get().create(level);
        if (hero == null) {
            helper.fail("could not create a hero");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(6, 2, 6));
        hero.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        ((Unit) hero).setOwnerName("gt_aud_resync_" + UUID.randomUUID().toString().substring(0, 8));
        level.addFreshEntity(hero);
        ServerPlayer viewer = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "gt_aud_resync_viewer"));
        List<Object> sent = new ArrayList<>();
        BiConsumer<ServerPlayer, Object> before = AudienceResync.sink;
        AudienceResync.sink = (sp, packet) -> sent.add(packet);
        List<String> problems = new ArrayList<>();
        try {
            hero.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 0));
            UnitKills.set(hero, 3);
            int effects = hero.getActiveEffects().size();
            int ranks = Math.min(4, ((HeroUnit) hero).getHeroAbilities().size());
            expectResync(problems, "just loaded", sent,
                () -> AudienceResync.onUnitKnown(viewer, hero, true, false, false), effects, 1, 0, 0);
            expectResync(problems, "an enemy sees it again", sent,
                () -> AudienceResync.onUnitKnown(viewer, hero, false, false, false), effects, 1, 3 + ranks, 0);
            expectResync(problems, "its own side sees it again", sent,
                () -> AudienceResync.onUnitKnown(viewer, hero, false, true, false), effects, 1, 5 + ranks, 0);
            expectResync(problems, "a view change", sent,
                () -> AudienceResync.onUnitKnown(viewer, hero, true, true, true), effects, 1, 5 + ranks, 1);
            UnitKills.set(hero, 0);
            expectResync(problems, "no kills, nothing to send for them", sent,
                () -> AudienceResync.onUnitKnown(viewer, hero, true, false, false), effects, 0, 0, 0);
        } finally {
            AudienceResync.sink = before;
            hero.discard();
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * R2-10: the capture-point join handler asked every joining entity for its persistent data, which makes Forge
     * create (and from then on save) an empty ForgeData tag on each. Called directly on entities that are never added,
     * so no other join handler is involved: a plain entity stays untagged, a camp's hired wolf is still re-counted.
     */
    @GameTest(template = ARENA)
    public static void capture_points_leave_unrelated_entities_untagged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entity marker = EntityType.MARKER.create(level);
        LivingEntity wolf = EntityRegistrar.WOLF_UNIT.get().create(level);
        if (marker == null || wolf == null) {
            helper.fail("could not create the probe entities");
            return;
        }
        String owner = "gt_aud_hire_" + UUID.randomUUID().toString().substring(0, 8);
        try {
            CapturePointServerEvents.onJoin(new EntityJoinLevelEvent(marker, level));
            CompoundTag saved = marker.saveWithoutId(new CompoundTag());
            if (saved.contains("ForgeData")) {
                helper.fail("a plain entity joining got a ForgeData tag: " + saved.get("ForgeData"));
                return;
            }
            wolf.getPersistentData().putString(CapturePointServerEvents.KEY_HIRED, owner);
            CapturePointServerEvents.onJoin(new EntityJoinLevelEvent(wolf, level));
            if (CapturePointServerEvents.hiredCount(owner) != 1)
                helper.fail("a reloaded hired wolf was not re-counted: " + CapturePointServerEvents.hiredCount(owner));
            else
                helper.succeed();
        } finally {
            marker.discard();
            wolf.discard();   // never added; discarded so the camp's count for this unique owner drops to 0
        }
    }

    // ------------------------------------------------------------------------------------- 8v8 measurement

    static final int AUD_SIDES = 2, AUD_SEATS = 8, AUD_UNITS = 25, AUD_FRONT = 5;

    /** One unit of the 8v8 layout, as the vision model sees it. */
    record Viewer(int side, int x, int z, int sight) { }

    /**
     * 8v8 with two Hero-role players and two spectators online (20 clients): 16 armies of 25 units (the stress test's
     * mix: 10 workers, fighters and the faction hero), each side's eight clusters on its own half and five fighters of
     * every army out at the front. Then one round of the per-unit packet families goes through the real send sites
     * (hero mana, status effects, worker loads, a sound per unit, cooldowns) plus one stockpile change and one research
     * per player; {@link Audience#accountant} records which policy each send used, and the pure rules count the
     * recipients with team vision (a side sees a spot when one of its units has it in sight range). Before this
     * rewrite each of these went to all 20. Logged as a "[Stress]" line (a CI notice). Fails if a family's send site
     * changes policy, or the narrowed audiences save less than a quarter. Spawned, measured and removed inside one
     * callback, like the stress test.
     */
    @GameTest(template = ARENA, timeoutTicks = 1000, batch = "audience_8v8")
    public static void audience_cuts_8v8_deliveries(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        int baseX = origin.getX(), baseZ = origin.getZ() - 3000;   // north of the grid: the stress test is west
        final double y = 150;   // floating, never ticked: no terrain dependence
        // the layout below spans x baseX +-57, z baseZ-1 .. baseZ+28: its chunks are force-loaded meanwhile
        List<long[]> chunks = new ArrayList<>();
        for (int cx = (baseX - 60) >> 4; cx <= (baseX + 60) >> 4; cx++)
            for (int cz = (baseZ - 4) >> 4; cz <= (baseZ + 32) >> 4; cz++)
                chunks.add(new long[] { cx, cz });
        // async FORCED tickets and a bounded wait: setChunkForced generated each chunk synchronously on the server
        // thread with no way out (CI hangs, see GameTestChunks)
        GameTestChunks.holdAndAwaitOrFail(level, chunks, "8v8 audience");

        // not a fixed 40 ticks: the chunks are FULL here, their entity sections may still be loading
        // progress-based: gives up after 200 ticks with no further chunk ready, or 900 in all (timeoutTicks 1000)
        GameTestChunks.whenEntitiesReady(helper, level, chunks, 40, 200, 900, "8v8 audience", () -> {
            Random rng = new Random(83);
            List<LivingEntity> spawned = new ArrayList<>();
            Map<LivingEntity, Integer> sideOf = new HashMap<>();
            List<String> owners = new ArrayList<>();
            FakeFacts facts = new FakeFacts();
            String tag = "aud8_" + UUID.randomUUID().toString().substring(0, 6);
            try {
                for (int s = 0; s < AUD_SIDES; s++) {
                    for (int o = 0; o < AUD_SEATS; o++) {
                        String owner = tag + "_s" + s + "_" + o;
                        owners.add(owner);
                        facts.side.put(owner, s);
                        int dir = s == 0 ? -1 : 1;
                        int cx = baseX + dir * (16 + (o % 4) * 12), cz = baseZ + (o / 4) * 16 + 4;
                        for (int i = 0; i < AUD_UNITS; i++) {
                            var e = SkirmishGameTests.stressUnitType(o, i).create(level);
                            if (e == null)
                                continue;
                            boolean front = i >= AUD_UNITS - AUD_FRONT;   // fighters meet at the front, workers stay home
                            double x = front ? baseX + dir * (2 + rng.nextDouble() * 8) : cx + rng.nextDouble() * 10 - 5;
                            double z = front ? baseZ + rng.nextDouble() * 28 : cz + rng.nextDouble() * 10 - 5;
                            e.moveTo(x, y, z, rng.nextFloat() * 360, 0);
                            ((Unit) e).setOwnerName(owner);
                            level.addFreshEntity(e);
                            spawned.add(e);
                            sideOf.put(e, s);
                        }
                    }
                    facts.heroes.put(tag + "_hero" + s, List.of(tag + "_s" + s + "_0"));
                }
                facts.spectators.add(tag + "_spec0");
                facts.spectators.add(tag + "_spec1");
                // the send sites find units by id (Audience.entity): a unit in a chunk that isn't loaded yet is hidden
                if (spawned.isEmpty() || Audience.entity(spawned.get(spawned.size() - 1).getId()) == null) {
                    helper.fail("the spawned units are not visible to the level (chunks still loading?)");
                    return;
                }
                List<String> online = new ArrayList<>(owners);
                online.addAll(facts.heroes.keySet());
                online.addAll(facts.spectators);

                List<Viewer> viewers = new ArrayList<>();
                for (LivingEntity le : spawned)
                    viewers.add(new Viewer(sideOf.get(le), le.getBlockX(), le.getBlockZ(), ((Unit) le).getSightRange()));

                // one round of the packet families through the real send sites, recording each send's policy
                String[] family = { "" };
                List<Object[]> sends = new ArrayList<>();
                Audience.accountant = (policy, owner, positional, x, z) ->
                    sends.add(new Object[] { family[0], policy, owner, positional, x, z });
                try {
                    family[0] = "hero mana";
                    for (LivingEntity le : spawned)
                        if (le instanceof HeroUnit hero)
                            HeroClientboundPacket.setMana(le.getId(), hero.getMana());
                    family[0] = "status effects";
                    for (LivingEntity le : spawned)
                        UnitSyncMobEffectsClientboundPacket.addEffectClientside(le,
                            new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
                    family[0] = "worker loads";
                    for (LivingEntity le : spawned)
                        if (le instanceof WorkerUnit)
                            UnitSyncClientboundPacket.sendSyncResourcesPacket((Unit) le);
                    family[0] = "sounds";
                    for (LivingEntity le : spawned)
                        SoundClientboundPacket.playSoundAtPos(SoundAction.LEVEL_UP, le.blockPosition());
                    family[0] = "cooldowns";
                    for (LivingEntity le : spawned)
                        for (Ability ability : ((Unit) le).getAbilities().get())
                            if (ability.action != null) {
                                AbilityClientboundPacket.sendSetCooldownPacket(le.getId(), ability.action, 20);
                                break;
                            }
                    family[0] = "stockpiles";
                    for (String owner : owners)
                        ResourcesClientboundPacket.addSubtractResources(new Resources(owner, 1, 0, 0));
                    family[0] = "research";
                    for (String owner : owners)
                        ResearchClientboundPacket.addResearch(owner, "reignofnether:audience_probe");
                } finally {
                    Audience.accountant = null;
                }

                // count deliveries: all 20 before; the recorded policy's recipients now, with team vision
                Map<String, int[]> byFamily = new LinkedHashMap<>();   // family -> {sends, before, after}
                Map<String, Set<Policy>> policiesOf = new HashMap<>();
                Map<Policy, Integer> byPolicy = new EnumMap<>(Policy.class);
                int before = 0, after = 0, seenSendsHidingEnemy = 0;
                for (Object[] rec : sends) {
                    String fam = (String) rec[0];
                    Policy policy = (Policy) rec[1];
                    String owner = (String) rec[2];
                    int x = (int) rec[4], z = (int) rec[5];
                    boolean[] sideSees = new boolean[AUD_SIDES];
                    for (Viewer v : viewers) {
                        long dx = v.x() - x, dz = v.z() - z;
                        if (!sideSees[v.side()] && dx * dx + dz * dz <= (long) v.sight() * v.sight())
                            sideSees[v.side()] = true;
                    }
                    Predicate<String> canSee = p -> {
                        Integer s = facts.side.get(p);
                        if (s == null && !facts.commandersOf(p).isEmpty())
                            s = facts.side.get(facts.commandersOf(p).get(0));
                        return s == null || sideSees[s];   // spectators: no side, see everything
                    };
                    int n = Audience.recipients(policy, owner, online, facts, canSee).size();
                    if (policy == Policy.SEEN && !owner.isEmpty() && n < online.size())
                        seenSendsHidingEnemy++;
                    int[] row = byFamily.computeIfAbsent(fam, k -> new int[3]);
                    row[0]++;
                    row[1] += online.size();
                    row[2] += n;
                    policiesOf.computeIfAbsent(fam, k -> new HashSet<>()).add(policy);
                    byPolicy.merge(policy, 1, Integer::sum);
                    before += online.size();
                    after += n;
                }

                StringBuilder sb = new StringBuilder();
                for (var e : byFamily.entrySet()) {
                    int[] r = e.getValue();
                    sb.append(String.format(Locale.ROOT, "%s %d sends %d->%d (%.1f per send); ", e.getKey(), r[0], r[1], r[2],
                        r[0] == 0 ? 0 : (double) r[2] / r[0]));
                }
                double cut = before == 0 ? 0 : 100.0 * (before - after) / before;
                ReignOfNether.LOGGER.info("[Stress] [Audience 8v8] {} units, {} clients: {} sends, deliveries {} -> {} ({}% fewer); {}policies {}",
                    spawned.size(), online.size(), sends.size(), before, after, String.format(Locale.ROOT, "%.1f", cut), sb, byPolicy);

                // each family's send site keeps its policy (the decisions of rewrite 3, see network/Audience)
                Map<String, Policy> want = new LinkedHashMap<>();
                want.put("hero mana", Policy.SEEN);
                want.put("status effects", Policy.SEEN);
                want.put("worker loads", Policy.SEEN);
                want.put("sounds", Policy.SEEN);
                want.put("cooldowns", Policy.TEAM);
                want.put("stockpiles", Policy.TEAM);
                want.put("research", Policy.OWNER);
                List<String> wrong = new ArrayList<>();
                for (var w : want.entrySet())
                    if (!Set.of(w.getValue()).equals(policiesOf.get(w.getKey())))
                        wrong.add(w.getKey() + " used " + policiesOf.get(w.getKey()) + ", want " + w.getValue());

                if (spawned.size() < AUD_SIDES * AUD_SEATS * AUD_UNITS * 9 / 10)
                    helper.fail("only " + spawned.size() + " units spawned");
                else if (!wrong.isEmpty())
                    helper.fail("send sites changed audience: " + String.join("; ", wrong));
                else if (seenSendsHidingEnemy == 0)
                    helper.fail("no unit was hidden from the other side: the layout has no fog to test");
                else if (cut < 25)
                    helper.fail(String.format(Locale.ROOT, "the audiences only saved %.1f%% of 8v8 deliveries (%d -> %d)",
                        cut, before, after));
                else
                    helper.succeed();
            } finally {
                for (LivingEntity e : spawned)
                    e.discard();
                GameTestChunks.releaseAll(level, chunks);
            }
        });
    }
}
