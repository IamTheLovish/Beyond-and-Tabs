package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.abilities.AwakenThicket;
import com.solegendary.reignofnether.ability.abilities.Empower;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.essence.EssenceServerEvents;
import com.solegendary.reignofnether.mana.DecoyServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.WreckServerEvents;
import com.solegendary.reignofnether.unit.DragonRaiseServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.VeterancyServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.ZombieUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Essence casters PR 1 (design/essence_casters.md, lovish approved draft 2 on Oct 29: "essence wisps and Empower
 * first, then one caster per race"). Written before the code, from his numbers:
 * <ul>
 *   <li>every unit that dies, either side, drops a floating essence wisp worth 1 essence per 25 metal of its cost
 *       (at least 1); summoned and temporary units (risen, awakened, decoys, summons) drop none;</li>
 *   <li>a wisp floats where it was dropped for 45 s, then fades; at most 200 exist (the oldest go first);</li>
 *   <li>an essence carrier pulls the wisps within 8 blocks to itself and holds at most 30 essence, saved with it;</li>
 *   <li>Empower: 15 essence, a friendly unit within 6 blocks gains one veterancy rank (max 3); refused - nothing
 *       spent, no cooldown - at rank 3, when short of essence, or on an enemy or a unit out of reach.</li>
 * </ul>
 * Every test keeps its units and wisps in its own arena, records what it sees in its early callbacks and judges only
 * in its last one, which always cleans up (wisps, wrecks, units) whatever the verdict. The 200-wisp cap test runs in
 * its own batch: it would evict the other tests' wisps.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class EssenceGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "essence";

    private static String owner(GameTestHelper helper, String what) {
        return "gametest_essence_" + what + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    /** Live wisps within {@code r} blocks of {@code at} (horizontally) and 3 blocks above or below it. */
    private static List<Entity> wispsNear(Vec3 at, double r) {
        List<Entity> found = new ArrayList<>();
        for (Entity w : EssenceServerEvents.getWisps()) {
            if (w.isRemoved())
                continue;
            double dx = w.getX() - at.x, dz = w.getZ() - at.z;
            if (dx * dx + dz * dz <= r * r && Math.abs(w.getY() - at.y) <= 3)
                found.add(w);
        }
        return found;
    }

    private static int valueNear(Vec3 at) {
        int sum = 0;
        for (Entity w : wispsNear(at, 1.5))
            sum += EssenceServerEvents.valueOf(w);
        return sum;
    }

    /** Wisps and wrecks the units of a test left where they fell: gone, whatever the verdict. */
    private static void clearDrops(List<Vec3> spots) {
        for (Vec3 at : spots) {
            wispsNear(at, 2).forEach(Entity::discard);
            for (Entity w : WreckServerEvents.getWrecks())
                if (!w.isRemoved() && w.position().distanceToSqr(at) < 2 * 2)
                    w.discard();
        }
    }

    private static void discardAll(List<? extends Entity> entities) {
        for (Entity e : entities)
            if (e != null && !e.isRemoved())
                e.discard();
    }

    private static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /** A unit that stands where it is put: no AI, so it never walks off its spot or into a wisp. */
    private static Mob still(GameTestHelper helper, EntityType<? extends Mob> type, String owner, int x, int z) {
        Mob m = HeroGameTests.spawnUnit(helper, type, owner, x, z);
        if (m != null)
            m.setNoAi(true);
        return m;
    }

    /** A carrier holding {@code essence}: a plain villager flagged as one (no caster unit exists before PR 2). */
    private static Mob carrier(GameTestHelper helper, String owner, int x, int z, int essence) {
        Mob m = still(helper, EntityRegistrar.VILLAGER_UNIT.get(), owner, x, z);
        if (m == null)
            return null;
        Essence.setCarrier(m, true);
        if (essence > 0)
            Essence.add(m, essence);
        return m;
    }

    // ------------------------------------------------------------------------------------------------ the drop

    /**
     * Each death drops one wisp worth 1 essence per 25 metal of the unit's cost: a villager (50 metal) 2, a vindicator
     * (95) 3, an iron golem (265) 10 - for two different owners (both sides drop). The formula's edges are checked on
     * {@link Essence#essenceFor} too: below 25 metal (and free) units still drop 1.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = BATCH)
    public static void essence_wisp_drop_is_one_per_25_metal_of_cost(GameTestHelper helper) {
        String a = owner(helper, "dropa"), b = owner(helper, "dropb");
        List<Mob> units = new ArrayList<>();
        units.add(still(helper, EntityRegistrar.VILLAGER_UNIT.get(), a, 5, 5));
        units.add(still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), b, 9, 5));
        units.add(still(helper, EntityRegistrar.IRON_GOLEM_UNIT.get(), a, 7, 10));
        List<Vec3> spots = new ArrayList<>();
        int[] want = new int[units.size()];
        for (int i = 0; i < units.size(); i++) {
            Mob m = units.get(i);
            if (m == null) {
                discardAll(units);
                helper.fail("could not spawn the three units");
                return;
            }
            spots.add(m.position());
            want[i] = Math.max(1, (int) Math.floor(((Unit) m).getCost().metal() / 25f));
        }
        helper.runAfterDelay(3, () -> units.forEach(Mob::kill));
        helper.runAfterDelay(8, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (Essence.METAL_PER_ESSENCE != 25f)
                    problems.add("lovish set 1 essence per 25 metal, the constant says " + Essence.METAL_PER_ESSENCE);
                int[][] edges = { { 0, 1 }, { 10, 1 }, { 24, 1 }, { 25, 1 }, { 49, 1 }, { 50, 2 }, { 60, 2 }, { 300, 12 } };
                for (int[] e : edges)
                    if (Essence.essenceFor(e[0]) != e[1])
                        problems.add(e[0] + " metal should be worth " + e[1] + " essence, essenceFor says " + Essence.essenceFor(e[0]));
                for (int i = 0; i < units.size(); i++) {
                    List<Entity> here = wispsNear(spots.get(i), 1.5);
                    String name = EntityType.getKey(units.get(i).getType()).getPath();
                    if (here.size() != 1)
                        problems.add("the dead " + name + " left " + here.size() + " wisps, expected 1");
                    else if (EssenceServerEvents.valueOf(here.get(0)) != want[i])
                        problems.add("the dead " + name + " (" + ((Unit) units.get(i)).getCost().metal() + " metal) left a wisp worth "
                            + EssenceServerEvents.valueOf(here.get(0)) + ", expected " + want[i]);
                    else if (!EssenceServerEvents.isWisp(here.get(0)))
                        problems.add("the " + name + "'s wisp does not answer isWisp");
                }
                if (want[2] < 2)
                    problems.add("the iron golem should be worth several essence (" + want[2] + "): the test lost its point");
            } finally {
                clearDrops(spots);
                discardAll(units);
            }
            finish(helper, problems);
        });
    }

    /**
     * Summons cannot farm essence: a risen skeleton, an awakened treant, a decoy, a necromancer's summoned zombie and a
     * unit marked with {@link Essence#markSummoned} die and leave no wisp; a normal unit dying beside them does.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = BATCH)
    public static void essence_summons_and_temporary_units_drop_no_wisp(GameTestHelper helper) {
        String owner = owner(helper, "summon");
        Mob risen = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 3, 4);
        Mob awakened = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 6, 4);
        Mob decoy = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 9, 4);
        Mob zombie = still(helper, EntityRegistrar.ZOMBIE_UNIT.get(), owner, 12, 4);
        Mob marked = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 4, 11);
        Mob normal = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 11, 11);
        List<Mob> units = new ArrayList<>(java.util.Arrays.asList(risen, awakened, decoy, zombie, marked, normal));
        if (units.contains(null)) {
            discardAll(units.stream().filter(java.util.Objects::nonNull).toList());
            helper.fail("could not spawn the six units");
            return;
        }
        // tagged after they joined: the decoy and treant registries discard a tagged unit that joins unregistered (a save leftover)
        risen.addTag(DragonRaiseServerEvents.TAG);
        awakened.addTag(AwakenThicket.TAG);
        decoy.addTag(DecoyServerEvents.TAG);
        ((ZombieUnit) zombie).setIsSummoned(true);
        Essence.markSummoned(marked);
        List<Vec3> spots = new ArrayList<>();
        for (Mob m : units)
            spots.add(m.position());
        helper.runAfterDelay(2, () -> units.forEach(Mob::kill));
        helper.runAfterDelay(8, () -> {
            List<String> problems = new ArrayList<>();
            try {
                String[] names = { "risen", "awakened", "decoy", "summoned zombie", "markSummoned" };
                for (int i = 0; i < names.length; i++) {
                    if (!Essence.isSummoned(units.get(i)))
                        problems.add("the " + names[i] + " unit does not count as summoned");
                    int n = wispsNear(spots.get(i), 1.5).size();
                    if (n > 0)
                        problems.add("the " + names[i] + " unit dropped " + n + " wisp(s): summons must not farm essence");
                }
                if (Essence.isSummoned(normal))
                    problems.add("a normal unit counts as summoned");
                if (wispsNear(spots.get(5), 1.5).size() != 1)
                    problems.add("the normal unit beside them left " + wispsNear(spots.get(5), 1.5).size() + " wisps, expected 1");
            } finally {
                clearDrops(spots);
                discardAll(units);
            }
            finish(helper, problems);
        });
    }

    // ---------------------------------------------------------------------------------------- the wisp's life

    /**
     * A wisp floats where it was dropped (it does not fall or drift) and fades after 45 s: one aged to 30 ticks short
     * of the end is still there 10 ticks later and gone 60 ticks later, while a fresh one beside it stays.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void essence_wisp_floats_in_place_and_fades_after_45_seconds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos o = helper.absolutePos(new BlockPos(4, 3, 4));
        Entity old = EssenceServerEvents.spawnWisp(level, o.getX() + 0.5, o.getY() + 0.5, o.getZ() + 0.5, 2);
        Entity fresh = EssenceServerEvents.spawnWisp(level, o.getX() + 6.5, o.getY() + 0.5, o.getZ() + 4.5, 2);
        List<Entity> mine = new ArrayList<>();
        if (old != null) mine.add(old);
        if (fresh != null) mine.add(fresh);
        if (old == null || fresh == null) {
            discardAll(mine);
            helper.fail("spawnWisp made no wisp");
            return;
        }
        Vec3 freshAt = fresh.position();
        EssenceServerEvents.backdate(old, EssenceServerEvents.LIFETIME_TICKS - 30);
        boolean[] aliveAt10 = { false };
        helper.runAfterDelay(10, () -> aliveAt10[0] = !old.isRemoved());
        helper.runAfterDelay(60, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (EssenceServerEvents.LIFETIME_TICKS != 45 * 20)
                    problems.add("lovish set 45 s, LIFETIME_TICKS is " + EssenceServerEvents.LIFETIME_TICKS);
                if (!aliveAt10[0])
                    problems.add("a wisp 20 ticks short of 45 s was already gone");
                if (!old.isRemoved())
                    problems.add("a wisp past 45 s is still there (age " + (level.getGameTime() - EssenceServerEvents.bornOf(old)) + " ticks)");
                if (old.isRemoved() && EssenceServerEvents.getWisps().contains(old))
                    problems.add("a faded wisp is still in the wisp list");
                if (fresh.isRemoved())
                    problems.add("a 60-tick-old wisp faded");
                else if (fresh.position().distanceTo(freshAt) > 0.05)
                    problems.add("a wisp with no carrier near moved " + String.format("%.2f", fresh.position().distanceTo(freshAt)) + " blocks: it should float in place");
            } finally {
                discardAll(mine);
            }
            finish(helper, problems);
        });
    }

    /**
     * At most 200 wisps on the map, the oldest go first: 205 dropped in a row leave the newest 200 (and any older wisp
     * from an earlier test goes before them). Its own batch: the cap would evict the other tests' wisps.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "essence_cap")
    public static void essence_wisps_cap_at_200_and_the_oldest_go_first(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int extra = 5, total = EssenceServerEvents.MAX_WISPS + extra;
        List<Entity> mine = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            BlockPos p = helper.absolutePos(new BlockPos(1 + i % 14, 3 + (i / 196), 1 + (i / 14) % 14));
            Entity w = EssenceServerEvents.spawnWisp(level, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 1);
            if (w != null)
                mine.add(w);
        }
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (EssenceServerEvents.MAX_WISPS != 200)
                    problems.add("lovish set 200 wisps at most, MAX_WISPS is " + EssenceServerEvents.MAX_WISPS);
                if (mine.size() != total) {
                    problems.add("spawnWisp made " + mine.size() + " of " + total + " wisps");
                } else {
                    long live = EssenceServerEvents.getWisps().stream().filter(w -> !w.isRemoved()).count();
                    if (live != EssenceServerEvents.MAX_WISPS)
                        problems.add(live + " live wisps after " + total + " drops, expected " + EssenceServerEvents.MAX_WISPS);
                    int oldGone = 0, newAlive = 0;
                    for (int i = 0; i < extra; i++)
                        if (mine.get(i).isRemoved())
                            oldGone++;
                    for (int i = extra; i < total; i++)
                        if (!mine.get(i).isRemoved())
                            newAlive++;
                    if (oldGone != extra)
                        problems.add("only " + oldGone + " of the " + extra + " oldest wisps went");
                    if (newAlive != EssenceServerEvents.MAX_WISPS)
                        problems.add("only " + newAlive + " of the newest " + EssenceServerEvents.MAX_WISPS + " wisps are left");
                }
            } finally {
                discardAll(mine);
                EssenceServerEvents.getWisps().removeIf(Entity::isRemoved);
            }
            finish(helper, problems);
        });
    }

    // ---------------------------------------------------------------------------------------------- gathering

    /**
     * A carrier pulls the wisps within 8 blocks to itself and takes their essence: one 7 blocks off flies in (3
     * essence), one 9.5 blocks off stays where it floats, and a plain unit 2 blocks from a third wisp gathers nothing.
     * The carrier stands in a corner so no neighbouring arena's wisp is within its reach.
     */
    @GameTest(template = ARENA, timeoutTicks = 120, batch = BATCH)
    public static void essence_carrier_gathers_wisps_within_8_blocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "gather");
        Mob carrier = carrier(helper, owner, 2, 2, 0);
        Mob plain = still(helper, EntityRegistrar.VILLAGER_UNIT.get(), owner, 13, 3);
        if (carrier == null || plain == null) {
            discardAll(java.util.Arrays.asList(carrier, plain));
            helper.fail("could not spawn the carrier and the plain unit");
            return;
        }
        Vec3 c = carrier.position();
        Entity near = EssenceServerEvents.spawnWisp(level, c.x + 7, c.y + 1, c.z, 3);
        Entity far = EssenceServerEvents.spawnWisp(level, c.x + 6.7, c.y + 1, c.z + 6.7, 4);
        Entity byPlain = EssenceServerEvents.spawnWisp(level, plain.getX(), plain.getY() + 1, plain.getZ() + 2, 5);
        List<Entity> all = new ArrayList<>(java.util.Arrays.asList(carrier, plain, near, far, byPlain));
        if (near == null || far == null || byPlain == null) {
            discardAll(all.stream().filter(java.util.Objects::nonNull).toList());
            helper.fail("spawnWisp made no wisp");
            return;
        }
        Vec3 farAt = far.position(), byPlainAt = byPlain.position();
        int[] gatheredAt = { -1 };
        for (int t = 5; t <= 100; t += 5) {
            int tick = t;
            helper.runAfterDelay(t, () -> {
                if (gatheredAt[0] < 0 && near.isRemoved())
                    gatheredAt[0] = tick;
            });
        }
        helper.runAfterDelay(105, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (EssenceServerEvents.GATHER_RANGE != 8.0)
                    problems.add("lovish set an 8-block gather, GATHER_RANGE is " + EssenceServerEvents.GATHER_RANGE);
                if (!Essence.isCarrier(carrier))
                    problems.add("setCarrier did not make the villager a carrier");
                if (Essence.isCarrier(plain))
                    problems.add("a plain villager counts as a carrier");
                if (gatheredAt[0] < 0)
                    problems.add("the wisp 7 blocks from the carrier was never gathered");
                if (Essence.get(carrier) != 3)
                    problems.add("the carrier holds " + Essence.get(carrier) + " essence, expected the near wisp's 3");
                if (far.isRemoved())
                    problems.add("the wisp 9.5 blocks away was taken: beyond the 8-block reach");
                else if (far.position().distanceTo(farAt) > 0.05)
                    problems.add("the wisp 9.5 blocks away moved " + String.format("%.2f", far.position().distanceTo(farAt)) + " blocks");
                if (byPlain.isRemoved() || byPlain.position().distanceTo(byPlainAt) > 0.05)
                    problems.add("a plain unit gathered (or moved) the wisp beside it");
                if (Essence.get(plain) != 0)
                    problems.add("a plain unit holds " + Essence.get(plain) + " essence");
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    /**
     * A carrier holds at most 30: with 25 it takes 5 of a 12-essence wisp, and the wisp keeps the other 7 and floats on
     * (essence is never lost to a full carrier). The API refuses an overspend without side effects, spends exactly,
     * and add() on a non-carrier takes nothing.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void essence_carrier_holds_at_most_30_and_the_rest_stays_in_the_wisp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "cap30");
        Mob carrier = carrier(helper, owner, 2, 2, 25);
        Mob bank = carrier(helper, owner, 13, 13, 0);
        Mob plain = still(helper, EntityRegistrar.VILLAGER_UNIT.get(), owner, 13, 2);
        List<Entity> all = new ArrayList<>(java.util.Arrays.asList(carrier, bank, plain));
        if (all.contains(null)) {
            discardAll(all.stream().filter(java.util.Objects::nonNull).toList());
            helper.fail("could not spawn the carriers");
            return;
        }
        Entity wisp = EssenceServerEvents.spawnWisp(level, carrier.getX() + 3, carrier.getY() + 1, carrier.getZ() + 1, 12);
        if (wisp == null) {
            discardAll(all);
            helper.fail("spawnWisp made no wisp");
            return;
        }
        all.add(wisp);
        int startEssence = Essence.get(carrier);
        helper.runAfterDelay(80, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (Essence.CAPACITY != 30)
                    problems.add("lovish set 30 essence at most, CAPACITY is " + Essence.CAPACITY);
                if (startEssence != 25)
                    problems.add("add(25) on an empty carrier gave " + startEssence);
                if (Essence.get(carrier) != 30)
                    problems.add("the carrier holds " + Essence.get(carrier) + " essence, expected the 30 cap");
                if (wisp.isRemoved())
                    problems.add("the 12-essence wisp vanished into a carrier with room for 5: 7 essence lost");
                else if (EssenceServerEvents.valueOf(wisp) != 7)
                    problems.add("the wisp kept " + EssenceServerEvents.valueOf(wisp) + " essence, expected 12 - 5 = 7");
                // the API, on a carrier no wisp is near
                int got = Essence.add(bank, 40);
                if (got != 30 || Essence.get(bank) != 30)
                    problems.add("add(40) on an empty carrier took " + got + " and holds " + Essence.get(bank) + ", expected 30 and 30");
                if (Essence.add(bank, 1) != 0 || Essence.room(bank) != 0)
                    problems.add("a full carrier took more or reports room " + Essence.room(bank));
                if (Essence.trySpend(bank, 31) || Essence.get(bank) != 30)
                    problems.add("an overspend of 31 was not refused cleanly (holds " + Essence.get(bank) + ")");
                if (!Essence.trySpend(bank, 10) || Essence.get(bank) != 20 || Essence.room(bank) != 10)
                    problems.add("spending 10 of 30 left " + Essence.get(bank) + " (room " + Essence.room(bank) + "), expected 20 (room 10)");
                if (Essence.add(plain, 5) != 0 || Essence.get(plain) != 0)
                    problems.add("a non-carrier took essence");
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    /** Essence is saved with its carrier, and a wisp with its value and age: both come back from a save and a load. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void essence_survives_a_save_and_reload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "reload");
        Mob carrier = carrier(helper, owner, 13, 13, 17);
        Entity wisp = EssenceServerEvents.spawnWisp(level, helper.absolutePos(BlockPos.ZERO).getX() + 3.5,
            helper.absolutePos(BlockPos.ZERO).getY() + 4.5, helper.absolutePos(BlockPos.ZERO).getZ() + 12.5, 5);
        List<Entity> all = new ArrayList<>();
        if (carrier != null) all.add(carrier);
        if (wisp != null) all.add(wisp);
        if (carrier == null || wisp == null) {
            discardAll(all);
            helper.fail("could not spawn the carrier or the wisp");
            return;
        }
        EssenceServerEvents.backdate(wisp, 100);
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            try {
                CompoundTag ct = new CompoundTag();
                if (!carrier.save(ct)) {
                    problems.add("the carrier refused to save");
                } else {
                    Entity back = EntityType.create(SkirmishGameTests.bytesRoundTrip(ct), level).orElse(null);
                    if (!(back instanceof LivingEntity le))
                        problems.add("the saved carrier did not load");
                    else if (!Essence.isCarrier(le) || Essence.get(le) != 17)
                        problems.add("after a reload the carrier is " + (Essence.isCarrier(le) ? "" : "no longer a carrier and ")
                            + "holds " + Essence.get(le) + " essence, expected 17");
                }
                long born = EssenceServerEvents.bornOf(wisp);
                CompoundTag wt = new CompoundTag();
                if (!wisp.save(wt)) {
                    problems.add("the wisp refused to save");
                } else {
                    wisp.discard();
                    Entity back = EntityType.create(SkirmishGameTests.bytesRoundTrip(wt), level).orElse(null);
                    if (back == null) {
                        problems.add("the saved wisp did not load");
                    } else {
                        all.add(back);
                        level.addFreshEntity(back);
                        if (!EssenceServerEvents.isWisp(back) || EssenceServerEvents.valueOf(back) != 5)
                            problems.add("the reloaded wisp is worth " + EssenceServerEvents.valueOf(back) + ", expected 5");
                        if (born == 0 || EssenceServerEvents.bornOf(back) != born)
                            problems.add("the reloaded wisp's age restarted (born " + born + " -> " + EssenceServerEvents.bornOf(back) + ")");
                        if (!EssenceServerEvents.getWisps().contains(back))
                            problems.add("the reloaded wisp did not rejoin the wisp list (its fade and the cap would skip it)");
                    }
                }
            } catch (java.io.IOException ex) {
                problems.add("NBT round trip threw " + ex);
            } finally {
                discardAll(all);
                EssenceServerEvents.getWisps().removeIf(Entity::isRemoved);
            }
            finish(helper, problems);
        });
    }

    // ---------------------------------------------------------------------------------------------- Empower

    /** Casts Empower from {@code caster} on {@code target} the way an order does (the ability's own use()). */
    private static void empower(GameTestHelper helper, Empower ability, Mob caster, Mob target) {
        ability.use(helper.getLevel(), (Unit) caster, target);
    }

    /** Empower: 15 essence, a friend 3 blocks away gains one rank (+10% max health), the caster's cooldown starts. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void empower_spends_15_essence_and_ranks_an_ally_up(GameTestHelper helper) {
        String owner = owner(helper, "emp");
        Mob caster = carrier(helper, owner, 4, 4, 30);
        Mob target = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 7, 4);
        List<Entity> all = new ArrayList<>(java.util.Arrays.asList(caster, target));
        if (caster == null || target == null) {
            discardAll(all.stream().filter(java.util.Objects::nonNull).toList());
            helper.fail("could not spawn the caster and its friend");
            return;
        }
        Empower ability = new Empower();
        float hpBefore = target.getMaxHealth();
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (Empower.ESSENCE_COST != 15 || Empower.RANGE != 6)
                    problems.add("lovish set 15 essence within 6 blocks, Empower has " + Empower.ESSENCE_COST + " within " + Empower.RANGE);
                if (ability.action != UnitAction.EMPOWER)
                    problems.add("Empower's order is " + ability.action);
                empower(helper, ability, caster, target);
                if (VeterancyServerEvents.getRank(target) != 1)
                    problems.add("the friend is rank " + VeterancyServerEvents.getRank(target) + " after Empower, expected 1");
                if (Math.abs(target.getMaxHealth() - hpBefore * (1 + VeterancyServerEvents.HP_PER_RANK)) > 0.05f)
                    problems.add("the friend's max health went " + hpBefore + " -> " + target.getMaxHealth() + ", expected +10%");
                if (Essence.get(caster) != 15)
                    problems.add("the caster holds " + Essence.get(caster) + " essence after Empower, expected 30 - 15 = 15");
                if (((Unit) caster).getCooldown(ability) <= 0)
                    problems.add("Empower set no cooldown");
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    /** At rank 3 (the most) Empower is refused: no essence spent, no cooldown, the rank and experience unchanged. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void empower_refuses_a_rank_3_unit_without_spending(GameTestHelper helper) {
        String owner = owner(helper, "emp3");
        Mob caster = carrier(helper, owner, 4, 10, 30);
        Mob target = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 7, 10);
        List<Entity> all = new ArrayList<>(java.util.Arrays.asList(caster, target));
        if (caster == null || target == null) {
            discardAll(all.stream().filter(java.util.Objects::nonNull).toList());
            helper.fail("could not spawn the caster and its friend");
            return;
        }
        VeterancyServerEvents.award(target, VeterancyServerEvents.MAX_RANK);
        Empower ability = new Empower();
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                float xp = VeterancyServerEvents.getXp(target);
                if (VeterancyServerEvents.getRank(target) != VeterancyServerEvents.MAX_RANK)
                    problems.add("setup: the friend should be rank 3, is " + VeterancyServerEvents.getRank(target));
                empower(helper, ability, caster, target);
                if (Essence.get(caster) != 30)
                    problems.add("Empower on a rank 3 unit spent essence (" + Essence.get(caster) + " left of 30)");
                if (((Unit) caster).getCooldown(ability) > 0)
                    problems.add("a refused Empower set the cooldown");
                if (VeterancyServerEvents.getXp(target) != xp)
                    problems.add("a refused Empower still added experience (" + xp + " -> " + VeterancyServerEvents.getXp(target) + ")");
                if (Empower.cast(helper.getLevel(), caster, owner, target, true))
                    problems.add("cast() reports success on a rank 3 unit");
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    /** Short of essence (14 of 15) Empower is refused: nothing spent, no cooldown, no rank. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void empower_refuses_when_short_of_essence(GameTestHelper helper) {
        String owner = owner(helper, "empshort");
        Mob caster = carrier(helper, owner, 10, 4, 14);
        Mob target = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 13, 4);
        List<Entity> all = new ArrayList<>(java.util.Arrays.asList(caster, target));
        if (caster == null || target == null) {
            discardAll(all.stream().filter(java.util.Objects::nonNull).toList());
            helper.fail("could not spawn the caster and its friend");
            return;
        }
        Empower ability = new Empower();
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (Essence.get(caster) != 14)
                    problems.add("setup: the caster should hold 14 essence, holds " + Essence.get(caster));
                empower(helper, ability, caster, target);
                if (Essence.get(caster) != 14)
                    problems.add("a refused Empower changed the caster's essence to " + Essence.get(caster));
                if (((Unit) caster).getCooldown(ability) > 0)
                    problems.add("a refused Empower set the cooldown");
                if (VeterancyServerEvents.getRank(target) != 0 || VeterancyServerEvents.getXp(target) != 0)
                    problems.add("the friend ranked up without the essence (rank " + VeterancyServerEvents.getRank(target) + ")");
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    /** Empower takes a friend within 6 blocks only: an enemy beside the caster, or a friend 8 blocks off, is refused. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void empower_refuses_an_enemy_or_a_unit_out_of_reach(GameTestHelper helper) {
        String owner = owner(helper, "empreach"), foe = owner(helper, "empfoe");
        Mob caster = carrier(helper, owner, 4, 13, 30);
        Mob enemy = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 6, 13);
        Mob farFriend = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 12, 13);
        List<Entity> all = new ArrayList<>(java.util.Arrays.asList(caster, enemy, farFriend));
        if (all.contains(null)) {
            discardAll(all.stream().filter(java.util.Objects::nonNull).toList());
            helper.fail("could not spawn the caster, the enemy and the far friend");
            return;
        }
        Empower ability = new Empower();
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                empower(helper, ability, caster, enemy);
                if (VeterancyServerEvents.getRank(enemy) != 0)
                    problems.add("Empower ranked up an enemy");
                empower(helper, ability, caster, farFriend);
                if (VeterancyServerEvents.getRank(farFriend) != 0)
                    problems.add("Empower reached a friend " + String.format("%.1f", caster.distanceTo(farFriend)) + " blocks away (reach 6)");
                if (Essence.get(caster) != 30)
                    problems.add("refused casts spent essence (" + Essence.get(caster) + " left of 30)");
                if (((Unit) caster).getCooldown(ability) > 0)
                    problems.add("a refused Empower set the cooldown");
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }
}
