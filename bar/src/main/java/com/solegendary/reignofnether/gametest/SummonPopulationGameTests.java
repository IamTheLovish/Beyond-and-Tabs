package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.abilities.AwakenThicket;
import com.solegendary.reignofnether.ability.abilities.RaiseDead;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.hero.HeroFx;
import com.solegendary.reignofnether.mana.DecoyServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.WreckServerEvents;
import com.solegendary.reignofnether.unit.DragonRaiseServerEvents;
import com.solegendary.reignofnether.unit.PopulationTally;
import com.solegendary.reignofnether.unit.Summons;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.UnitSyncBatcher;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.packets.UnitBatchSyncClientboundPacket;
import com.solegendary.reignofnether.unit.units.brass.ClockworkDroneUnit;
import com.solegendary.reignofnether.unit.units.brass.IronLeviathanEngineUnit;
import com.solegendary.reignofnether.unit.units.monsters.SilverfishUnit;
import com.solegendary.reignofnether.unit.units.monsters.ZombieUnit;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Summons take no population (lovish, Oct 29: "ALL summons take no population", Awaken Thicket treants, risen units -
 * the Bone Dragon's, the Necropolis', the Lich Regent's ghouls - and every essence caster summon). One rule,
 * {@link Summons}: the server's count skips a summon, the client counts the population the server syncs for each unit
 * (0 for a summon: summon tags never reach the client), and a raise no longer needs population room - the Bone
 * Dragon's 8-per-owner cap stays. Written before the code. Early callbacks record; the last one judges after cleanup.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class SummonPopulationGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "summon_pop";

    private static String owner(GameTestHelper helper, String what) {
        return "gametest_summonpop_" + what + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    private static Mob still(GameTestHelper helper, EntityType<? extends Mob> type, String owner, int x, int z) {
        Mob m = HeroGameTests.spawnUnit(helper, type, owner, x, z);
        if (m != null)
            m.setNoAi(true);
        return m;
    }

    private static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /** Every unit of {@code owner} in the arena (and just round it): gone, whatever the verdict. */
    private static void clearOwner(GameTestHelper helper, String owner) {
        AABB box = new AABB(helper.absolutePos(new BlockPos(-4, -2, -4)), helper.absolutePos(new BlockPos(20, 20, 20)));
        for (LivingEntity e : helper.getLevel().getEntitiesOfClass(LivingEntity.class, box))
            if (e instanceof Unit u && owner.equals(u.getOwnerName()))
                e.discard();
    }

    /**
     * Each summon path, made for real beside one normal vindicator, and the owner's population is the vindicator's
     * alone: a Bone Dragon's risen skeleton (raised with the unit cap at 0), an awakened treant, a Mirror
     * Mage's decoy, a hero summon, a limited-life silverfish, an engine's drones, a deployed turret and a Lich Regent's
     * ghoul (raised from a wreck, with the unit cap at 0).
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void summon_pop_summons_take_no_population(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "all");
        List<Entity> made = new ArrayList<>();
        Mob normal = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 8, 8);
        Mob decoy = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 3, 3);
        Mob heroSummon = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 3, 6);
        Mob silverfish = still(helper, EntityRegistrar.SILVERFISH_UNIT.get(), owner, 3, 9);
        Mob turret = still(helper, EntityRegistrar.DEPLOYED_TURRET_UNIT.get(), owner, 3, 12);
        for (Entity e : new Entity[] { normal, decoy, heroSummon, silverfish, turret })
            if (e != null)
                made.add(e);
        IronLeviathanEngineUnit engine = EntityRegistrar.IRON_LEVIATHAN_ENGINE_UNIT.get().create(level);   // never added
        if (made.size() != 5 || engine == null) {
            made.forEach(Entity::discard);
            if (engine != null)
                engine.discard();
            helper.fail("could not spawn the test units");
            return;
        }
        DecoyServerEvents.register(level, decoy);
        HeroFx.addSummon(normal, heroSummon, level.getGameTime() + 400);   // the summon path every hero effect takes
        ((SilverfishUnit) silverfish).setLimitedLife();
        int base = ((Unit) normal).getCost().population;
        List<String> early = new ArrayList<>();
        Entity[] risen = { null };
        LivingEntity[] treant = { null };
        int[] drones = { 0 }, ghouls = { 0 };
        Entity[] wreckOut = { null };
        helper.runAfterDelay(2, () -> {
            int cap = UnitServerEvents.maxPopulation;
            UnitServerEvents.maxPopulation = 0;   // no room at all (restored below, synchronously, whatever happens)
            try {
                risen[0] = DragonRaiseServerEvents.raise(level, owner, helper.absolutePos(new BlockPos(12, 2, 3)));
                treant[0] = AwakenThicket.awakenOn(level, owner, helper.absolutePos(new BlockPos(12, 1, 7)), 400);
                engine.moveTo(helper.absolutePos(new BlockPos(12, 2, 12)).getX() + 0.5, helper.absolutePos(BlockPos.ZERO).getY() + 2,
                    helper.absolutePos(new BlockPos(12, 2, 12)).getZ() + 0.5, 0, 0);
                engine.setOwnerName(owner);
                drones[0] = engine.launchDrones(level);
                BlockPos w = helper.absolutePos(new BlockPos(9, 2, 8));
                wreckOut[0] = WreckServerEvents.spawnWreck(level, w.getX() + 0.5, w.getY(), w.getZ() + 0.5, 200f, Factions.MONSTERS);
                ghouls[0] = RaiseDead.raise(level, normal, owner);
            } catch (RuntimeException ex) {
                early.add("a summon path threw " + ex);
            } finally {
                UnitServerEvents.maxPopulation = cap;
            }
        });
        helper.runAfterDelay(4, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                if (risen[0] == null)
                    problems.add("no Bone Dragon skeleton rose with the unit cap at 0 (a summon takes no population)");
                if (treant[0] == null)
                    problems.add("no treant awoke");
                if (drones[0] < 1)
                    problems.add("the engine launched no drones");
                if (ghouls[0] != 1)
                    problems.add("the Lich Regent raised " + ghouls[0] + " ghouls from a wreck with the unit cap at 0, expected 1");
                for (ZombieUnit g : level.getEntitiesOfClass(ZombieUnit.class, new AABB(normal.blockPosition()).inflate(6)))
                    if (owner.equals(g.getOwnerName()) && !Summons.isSummoned(g))
                        problems.add("a Lich Regent ghoul is not marked as a summon");
                int pop = UnitServerEvents.getCurrentPopulation(owner);
                if (pop != base)
                    problems.add("the owner's population is " + pop + " with one vindicator (" + base + ") and only summons beside it");
            } finally {
                clearOwner(helper, owner);
                for (ClockworkDroneUnit d : level.getEntitiesOfClass(ClockworkDroneUnit.class, engine.getBoundingBox().inflate(12)))
                    if (d.getEngine() == engine)
                        d.discard();
                engine.discard();
                if (wreckOut[0] != null)
                    wreckOut[0].discard();
            }
            finish(helper, problems);
        });
    }

    /** No population room needed (the unit cap at 0), but the Bone Dragon's cap of 8 risen per owner holds: the ninth raise is refused. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void summon_pop_risen_cap_of_8_still_holds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "cap");
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            int cap = UnitServerEvents.maxPopulation;
            try {
                int rose = 0;
                Entity ninth;
                UnitServerEvents.maxPopulation = 0;   // no room at all: only the risen cap may refuse
                try {
                    for (int i = 0; i < DragonRaiseServerEvents.MAX_RISEN; i++)
                        if (DragonRaiseServerEvents.raise(level, owner, helper.absolutePos(new BlockPos(2 + (i % 4) * 3, 2, 3 + (i / 4) * 5))) != null)
                            rose++;
                    ninth = DragonRaiseServerEvents.raise(level, owner, helper.absolutePos(new BlockPos(13, 2, 13)));
                } finally {
                    UnitServerEvents.maxPopulation = cap;
                }
                if (DragonRaiseServerEvents.MAX_RISEN != 8)
                    problems.add("the risen cap is " + DragonRaiseServerEvents.MAX_RISEN + ", not 8");
                if (rose != DragonRaiseServerEvents.MAX_RISEN)
                    problems.add("only " + rose + " of " + DragonRaiseServerEvents.MAX_RISEN + " skeletons rose with the unit cap at 0");
                if (ninth != null)
                    problems.add("a ninth skeleton rose past the cap of 8");
                if (UnitServerEvents.getCurrentPopulation(owner) != 0)
                    problems.add(rose + " risen skeletons count " + UnitServerEvents.getCurrentPopulation(owner) + " population");
            } finally {
                clearOwner(helper, owner);
            }
            finish(helper, problems);
        });
    }

    /**
     * The client's count: summon tags stay on the server, so the HUD counts the population the server syncs per unit.
     * A normal vindicator and a summoned one go through the real sync entry (UnitSyncBatcher.capture), the packet's
     * own encode and decode, and the client's tally: the summon is sent as 0 and counted as 0.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void summon_pop_client_counts_the_synced_population(GameTestHelper helper) {
        String owner = owner(helper, "client");
        Mob normal = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 4, 4);
        Mob summon = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 10, 10);
        if (normal == null || summon == null) {
            if (normal != null) normal.discard();
            if (summon != null) summon.discard();
            helper.fail("could not spawn the two vindicators");
            return;
        }
        Summons.mark(summon);
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                List<UnitBatchSyncClientboundPacket.Entry> sent = List.of(
                    UnitSyncBatcher.capture(normal, (Unit) normal), UnitSyncBatcher.capture(summon, (Unit) summon));
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                new UnitBatchSyncClientboundPacket(sent).encode(buf);
                UnitBatchSyncClientboundPacket got = new UnitBatchSyncClientboundPacket(buf);
                Int2IntOpenHashMap synced = new Int2IntOpenHashMap();
                for (UnitBatchSyncClientboundPacket.Entry e : got.getEntries())
                    synced.put(e.id, e.population);
                int cost = ((Unit) normal).getCost().population;
                if (synced.get(normal.getId()) != cost)
                    problems.add("the normal vindicator was synced as " + synced.get(normal.getId()) + " population, expected " + cost);
                if (synced.get(summon.getId()) != 0)
                    problems.add("the summoned vindicator was synced as " + synced.get(summon.getId()) + " population, expected 0");
                int tally = PopulationTally.populationOf(normal, synced) + PopulationTally.populationOf(summon, synced);
                if (tally != cost)
                    problems.add("the client counts " + tally + " population for one vindicator and one summon, expected " + cost);
            } finally {
                normal.discard();
                summon.discard();
            }
            finish(helper, problems);
        });
    }
}
