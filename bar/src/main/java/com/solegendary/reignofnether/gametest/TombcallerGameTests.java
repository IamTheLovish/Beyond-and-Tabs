package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutocastState;
import com.solegendary.reignofnether.ability.abilities.Empower;
import com.solegendary.reignofnether.ability.abilities.GraveTide;
import com.solegendary.reignofnether.ability.abilities.RaiseTombstone;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.essence.EssenceServerEvents;
import com.solegendary.reignofnether.essence.TombcallerServerEvents;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.VeterancyServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.RisenSkeletonUnit;
import com.solegendary.reignofnether.unit.units.monsters.SkeletonUnit;
import com.solegendary.reignofnether.unit.units.monsters.TombcallerUnit;
import com.solegendary.reignofnether.unit.units.monsters.TombstoneUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The Gravebound essence caster, the Tombcaller (design/essence_casters.md "Gravebound Gravecaller" from lovish's idea,
 * Revision 1, and his tombstone answer on Oct 29: "tone down"). Written before the code, from those numbers:
 * <ul>
 *   <li>trained at the Dungeon (the Gravebound T2 lab) for a cost, an essence carrier that gathers the wisps near it -
 *       from 12 blocks instead of 8 when the wisp lies on its own grave creep;</li>
 *   <li>Raise Tombstone (10 essence, 25 s, at most 3 per caster): a summoned 150 HP stone within 10 blocks that takes
 *       damage at the unit rate and stands 90 s; its creep grows a block every 2 s up to 8 and fades over 10 s after
 *       it falls; every 20 s it raises a Risen Skeleton (30 s, summoned), one at a time; on the creep Gravebound friends
 *       regenerate 2 HP/s and enemies move 10% slower;</li>
 *   <li>Grave Tide (5 essence, 12 s): Gravebound friends within 8, or on its creep, +30% speed and 2 HP/s for 8 s (12
 *       s on the creep);</li>
 *   <li>the shared Empower; every ability refuses - nothing spent, no cooldown - when it cannot act, and its cooldown
 *       holds after a cast.</li>
 * </ul>
 * Every test unit stands still (no AI) with its autocast off. Each test records what it sees in its early callbacks
 * (which never throw) and judges in its last, which always cleans up - stones, skeletons, wisps, units - whatever the
 * verdict. The Grave Adept (Call the Necromancer) is its own PR.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class TombcallerGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "tombcaller";
    /** A stone old enough that its creep is full grown (8 blocks at a block per 40 ticks). */
    static final int FULL_GROWN = 8 * 40;

    private static String owner(GameTestHelper helper, String what) {
        return "gametest_tombcaller_" + what + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    private static void discardAll(List<? extends Entity> entities) {
        for (Entity e : entities)
            if (e != null && !e.isRemoved())
                e.discard();
    }

    /** Stones, their skeletons, and any skeleton of {@code owner} left near them: gone. */
    private static void clearStones(ServerLevel level, List<TombstoneUnit> stones, String owner) {
        for (TombstoneUnit s : stones) {
            discardAll(TombcallerServerEvents.risenOf(s));
            for (SkeletonUnit sk : level.getEntitiesOfClass(SkeletonUnit.class, s.getBoundingBox().inflate(6)))
                if (owner.equals(sk.getOwnerName()))
                    sk.discard();
            if (!s.isRemoved())
                s.discard();
        }
    }

    private static void clearWispsNear(Vec3 at, double r) {
        for (Entity w : EssenceServerEvents.getWisps())
            if (!w.isRemoved() && w.position().distanceTo(at) < r)
                w.discard();
    }

    private static int wispsNear(Vec3 at, double r) {
        int n = 0;
        for (Entity w : EssenceServerEvents.getWisps())
            if (!w.isRemoved() && w.position().distanceTo(at) < r)
                n++;
        return n;
    }

    private static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /** A unit that stands where it is put, never casting by itself. */
    private static Mob still(GameTestHelper helper, EntityType<? extends Mob> type, String owner, int x, int z) {
        Mob m = HeroGameTests.spawnUnit(helper, type, owner, x, z);
        if (m != null) {
            m.setNoAi(true);
            AutocastState.setAll((Unit) m, false);
        }
        return m;
    }

    /**
     * A Tombcaller holding {@code essence}. Flagged a carrier as well, so the ability tests judge the abilities alone
     * (that the unit is a carrier by itself is the training and gathering tests' business).
     */
    private static Mob caller(GameTestHelper helper, String owner, int x, int z, int essence) {
        Mob m = still(helper, EntityRegistrar.TOMBCALLER_UNIT.get(), owner, x, z);
        if (m == null)
            return null;
        Essence.setCarrier(m, true);
        Essence.add(m, essence);
        return m;
    }

    /**
     * A tombstone of {@code caster}'s for {@code owner}, raised directly (not through the ability), {@code age} ticks
     * old; with {@code raises} false it never raises a skeleton during the test.
     */
    private static TombstoneUnit stone(GameTestHelper helper, LivingEntity caster, String owner, int x, int z, int age, boolean raises) {
        ServerLevel level = helper.getLevel();
        TombstoneUnit s = EntityRegistrar.TOMBSTONE_UNIT.get().create(level);
        if (s == null)
            return null;
        BlockPos at = helper.absolutePos(new BlockPos(x, 2, z));
        s.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        s.setOwnerName(owner);
        Essence.markSummoned(s);
        s.begin(caster, level.getGameTime());
        if (age > 0)
            s.backdate(age);
        if (!raises)
            s.setNextRaise(level.getGameTime() + 100_000);
        level.addFreshEntity(s);
        return s;
    }

    private static <T extends Ability> T ability(Mob caller, Class<T> type) {
        for (Ability a : ((Unit) caller).getAbilities().get())
            if (type.isInstance(a))
                return type.cast(a);
        return null;
    }

    /** Movement speed over its base: 1.0 untouched, 0.9 slowed 10%, 1.3 hastened 30%. */
    private static double speedFactor(LivingEntity le) {
        double base = le.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        return base <= 0 ? 0 : le.getAttributeValue(Attributes.MOVEMENT_SPEED) / base;
    }

    private static List<Mob> spawned(List<Mob> units) {
        return units.stream().filter(Objects::nonNull).toList();
    }

    private static Vec3 cell(GameTestHelper helper, double x, double z) {
        BlockPos at = helper.absolutePos(new BlockPos(0, 2, 0));
        return new Vec3(at.getX() + x + 0.5, at.getY(), at.getZ() + z + 0.5);
    }

    // ------------------------------------------------------------------------------------------- the unit itself

    /**
     * The Dungeon trains the Tombcaller: its item names the unit registered to Gravebound, completing it (the real
     * onComplete -> produceUnit path) makes an owned Tombcaller that is an essence carrier, costs its ResourceCosts entry
     * (metal and population above 0), has its 40 health, and carries Raise Tombstone, Grave Tide and Empower with the
     * draft's essence costs and cooldowns. The placement is never registered; the unit is removed at the end.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = BATCH)
    public static void tombcaller_trains_at_the_dungeon_with_its_cost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "train");
        List<String> problems = new ArrayList<>();
        List<Entity> made = new ArrayList<>();
        try {
            Building dungeon = Buildings.DUNGEON;
            if (!(dungeon instanceof ProductionBuilding lab) || !lab.productions.get().contains(ProductionItems.TOMBCALLER))
                problems.add("the Dungeon (the Gravebound T2 lab) does not train the Tombcaller");
            if (!Factions.MONSTERS.key.equals(Factions.ENTITY_FACTION.get(EntityType.getKey(EntityRegistrar.TOMBCALLER_UNIT.get()))))
                problems.add("the Tombcaller is not registered to Gravebound");
            if (EntityRegistrar.getEntityType(ProductionItems.TOMBCALLER.getItemName()) != EntityRegistrar.TOMBCALLER_UNIT.get())
                problems.add("the Tombcaller's item name does not resolve to its entity type");
            ResourceCost cost = ResourceCosts.TOMBCALLER;
            if (cost.metal() <= 0 || cost.population <= 0)
                problems.add("the Tombcaller costs " + cost.metal() + " metal and " + cost.population + " population: it must cost something");
            BlockPos corner = helper.absolutePos(BlockPos.ZERO);
            BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(dungeon, level,
                new BlockPos(corner.getX() + 2, corner.getY() + 1, corner.getZ() + 3), Rotation.NONE, owner, false);
            if (!(bp instanceof ProductionPlacement pp)) {
                problems.add("the Dungeon is not a production placement");
            } else {
                pp.isBuilt = true;
                ProductionItems.TOMBCALLER.onComplete.accept(level, pp);
                Mob unit = CombatEngineerGameTests.findProduced(level, pp, EntityRegistrar.TOMBCALLER_UNIT.get(), owner);
                if (unit == null) {
                    problems.add("completing the Tombcaller item produced no unit");
                } else {
                    made.add(unit);
                    if (!(unit instanceof TombcallerUnit))
                        problems.add("the produced unit is a " + unit.getClass().getSimpleName());
                    if (!Essence.isCarrier(unit))
                        problems.add("the Tombcaller is not an essence carrier");
                    if (((Unit) unit).getCost() != cost)
                        problems.add("its cost is not ResourceCosts.TOMBCALLER");
                    if (Math.abs(unit.getMaxHealth() - 40f) > 0.5f || TombcallerUnit.maxHealth != 40f)
                        problems.add("max health " + unit.getMaxHealth() + " (maxHealth " + TombcallerUnit.maxHealth + "), expected 40");
                    List<UnitAction> actions = new ArrayList<>();
                    for (Ability a : ((Unit) unit).getAbilities().get())
                        actions.add(a.action);
                    List<UnitAction> want = List.of(UnitAction.RAISE_TOMBSTONE, UnitAction.GRAVE_TIDE, UnitAction.CALL_NECROMANCER,
                        UnitAction.EMPOWER);
                    if (!actions.equals(want))
                        problems.add("its abilities are " + actions + ", expected " + want);
                    if (RaiseTombstone.ESSENCE_COST != 10 || RaiseTombstone.CD_SECONDS != 25 || RaiseTombstone.MAX_STONES != 3)
                        problems.add("Raise Tombstone costs " + RaiseTombstone.ESSENCE_COST + " essence, " + RaiseTombstone.CD_SECONDS
                            + " s, " + RaiseTombstone.MAX_STONES + " stones; the draft set 10, 25 s, 3");
                    if (GraveTide.ESSENCE_COST != 5 || GraveTide.CD_SECONDS != 12)
                        problems.add("Grave Tide costs " + GraveTide.ESSENCE_COST + " essence, " + GraveTide.CD_SECONDS + " s; the draft set 5, 12 s");
                    RaiseTombstone raise = ability(unit, RaiseTombstone.class);
                    GraveTide tide = ability(unit, GraveTide.class);
                    // bots train it and it casts by itself (lovish: "bots build and use all 7 casters"): its spells
                    // autocast by default, Empower (the player's pick of who earns a rank) does not
                    var botKit = com.solegendary.reignofnether.bot.BotPlayer.kitFor(Factions.MONSTERS);
                    if (botKit == null || !botKit.t2Army().contains(ProductionItems.TOMBCALLER))
                        problems.add("the Gravebound bots do not train the Tombcaller");
                    for (Ability a : ((Unit) unit).getAbilities().get()) {
                        boolean on = AutocastState.isOn((Unit) unit, a);
                        if (a instanceof Empower ? on : !on)
                            problems.add(a.getClass().getSimpleName() + "'s autocast is " + (on ? "on" : "off") + " by default");
                    }
                    if (raise == null || tide == null || ability(unit, Empower.class) == null)
                        problems.add("an ability is missing");
                    else if (raise.cooldownMax != 25 * 20 || tide.cooldownMax != 12 * 20)
                        problems.add("cooldowns in ticks " + raise.cooldownMax + " / " + tide.cooldownMax + ", expected 500 / 240");
                }
            }
        } catch (RuntimeException ex) {
            problems.add("threw " + ex);
        } finally {
            discardAll(made);
        }
        finish(helper, problems);
    }

    /** A Tombcaller is a carrier by itself: a 4-essence wisp 5 blocks from it flies in and its essence is 4. */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void tombcaller_gathers_essence_wisps(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "gather");
        // in a corner, so no neighbouring arena's wisp is within its 8 blocks
        Mob caller = still(helper, EntityRegistrar.TOMBCALLER_UNIT.get(), owner, 2, 2);
        if (caller == null) {
            helper.fail("could not spawn the Tombcaller");
            return;
        }
        Entity wisp = EssenceServerEvents.spawnWisp(level, caller.getX() + 5, caller.getY() + 1, caller.getZ(), 4);
        if (wisp == null) {
            caller.discard();
            helper.fail("spawnWisp made no wisp");
            return;
        }
        helper.runAfterDelay(80, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (!Essence.isCarrier(caller))
                    problems.add("the Tombcaller is not an essence carrier");
                if (Essence.get(caller) != 4)
                    problems.add("the Tombcaller holds " + Essence.get(caller) + " essence, expected the wisp's 4");
                if (!wisp.isRemoved())
                    problems.add("the wisp 5 blocks away was never gathered");
            } finally {
                caller.discard();
                if (!wisp.isRemoved())
                    wisp.discard();
            }
            finish(helper, problems);
        });
    }

    /** The Tombcaller's Empower: 15 essence, a friend 3 blocks away gains a rank. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void tombcaller_empowers_a_friend(GameTestHelper helper) {
        String owner = owner(helper, "emp");
        Mob caller = caller(helper, owner, 4, 4, 30);
        Mob friend = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 7, 4);
        List<Mob> all = new ArrayList<>(Arrays.asList(caller, friend));
        if (caller == null || friend == null) {
            discardAll(spawned(all));
            helper.fail("could not spawn the Tombcaller and its friend");
            return;
        }
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                Empower empower = ability(caller, Empower.class);
                if (empower == null)
                    throw new IllegalStateException("the Tombcaller has no Empower");
                empower.use(helper.getLevel(), (Unit) caller, friend);
                if (VeterancyServerEvents.getRank(friend) != 1)
                    problems.add("the friend is rank " + VeterancyServerEvents.getRank(friend) + " after Empower, expected 1");
                if (Essence.get(caller) != 15)
                    problems.add("the Tombcaller holds " + Essence.get(caller) + " essence after Empower, expected 15");
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }

    // ------------------------------------------------------------------------------------------ Raise Tombstone

    /**
     * Raise Tombstone aimed at the ground 6 blocks off: one stone stands there for the caster's owner, marked summoned,
     * with 150 of 150 health, crumbling 90 s after the cast and remembering its Tombcaller; 10 of 30 essence spent, the
     * 25 s cooldown starts and a second cast at once is refused. 10 magic damage takes 10 health: the unit rate, with
     * none of a totem's resist.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void tombcaller_raise_tombstone_places_a_150_hp_stone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "raise");
        Mob caller = caller(helper, owner, 3, 3, 30);
        if (caller == null) {
            helper.fail("could not spawn the Tombcaller");
            return;
        }
        List<TombstoneUnit> stones = new ArrayList<>();
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                RaiseTombstone raise = ability(caller, RaiseTombstone.class);
                if (raise == null)
                    throw new IllegalStateException("the Tombcaller has no Raise Tombstone");
                long now = level.getGameTime();
                raise.use(level, (Unit) caller, helper.absolutePos(new BlockPos(9, 1, 3)));
                stones.addAll(TombcallerServerEvents.stonesOf(caller));
                for (TombstoneUnit s : level.getEntitiesOfClass(TombstoneUnit.class, caller.getBoundingBox().inflate(12)))
                    if (!stones.contains(s) && owner.equals(s.getOwnerName()))
                        stones.add(s);   // cleaned up even if stonesOf missed it
                if (TombcallerServerEvents.stonesOf(caller).size() != 1) {
                    problems.add("the caster keeps " + TombcallerServerEvents.stonesOf(caller).size() + " stones after one cast, expected 1");
                } else {
                    TombstoneUnit s = TombcallerServerEvents.stonesOf(caller).get(0);
                    Vec3 want = cell(helper, 9, 3);
                    if (Math.abs(s.getX() - want.x) > 0.6 || Math.abs(s.getZ() - want.z) > 0.6 || Math.abs(s.getY() - want.y) > 0.6)
                        problems.add("the stone stands at " + s.position() + ", not on the aimed cell " + want);
                    if (!owner.equals(s.getOwnerName()))
                        problems.add("the stone belongs to " + s.getOwnerName());
                    if (!Essence.isSummoned(s))
                        problems.add("the stone is not marked summoned: its death would pay essence");
                    if (Math.abs(s.getMaxHealth() - 150f) > 0.5f || Math.abs(s.getHealth() - 150f) > 0.5f || TombstoneUnit.HEALTH != 150f)
                        problems.add("the stone has " + s.getHealth() + " of " + s.getMaxHealth() + " health, lovish set 150");
                    if (TombstoneUnit.LIFETIME_SECONDS != 90 || Math.abs(s.end() - (now + 90 * 20)) > 1)
                        problems.add("the stone crumbles at " + s.end() + ", expected 90 s after the cast (" + (now + 1800) + ")");
                    if (!caller.getUUID().equals(s.casterId()))
                        problems.add("the stone does not remember its Tombcaller");
                    s.invulnerableTime = 0;
                    float before = s.getHealth();
                    s.hurt(level.damageSources().magic(), 10f);
                    if (Math.abs((before - s.getHealth()) - 10f) > 0.05f)
                        problems.add("10 magic damage took " + (before - s.getHealth()) + " health from the stone, expected 10 (unit rate)");
                }
                if (Essence.get(caller) != 20)
                    problems.add("the Tombcaller holds " + Essence.get(caller) + " essence after Raise Tombstone, expected 20");
                float cd = ((Unit) caller).getCooldown(raise);
                if (cd < 25 * 20 - 2)
                    problems.add("Raise Tombstone's cooldown is " + cd + " ticks after the cast, expected 500");
                raise.use(level, (Unit) caller, helper.absolutePos(new BlockPos(6, 1, 8)));
                if (Essence.get(caller) != 20 || TombcallerServerEvents.stonesOf(caller).size() > 1)
                    problems.add("a second Raise Tombstone during the cooldown went off (essence " + Essence.get(caller) + ")");
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                clearStones(level, stones, owner);
                caller.discard();
            }
            finish(helper, problems);
        });
    }

    /**
     * Raise Tombstone refuses without spending or starting its cooldown: short of essence (9 of 10); with 30 essence
     * 12 blocks off (past its 10), in mid-air (no ground), and with the caster's three stones already standing.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void tombcaller_raise_tombstone_refuses_without_spending(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "raiseno");
        Mob poor = caller(helper, owner, 2, 2, 9);
        Mob rich = caller(helper, owner, 2, 13, 30);
        List<Mob> callers = new ArrayList<>(Arrays.asList(poor, rich));
        if (callers.contains(null)) {
            discardAll(spawned(callers));
            helper.fail("could not spawn the two Tombcallers");
            return;
        }
        List<TombstoneUnit> stones = new ArrayList<>(Arrays.asList(
            stone(helper, rich, owner, 5, 13, 0, false), stone(helper, rich, owner, 7, 13, 0, false), stone(helper, rich, owner, 5, 11, 0, false)));
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                RaiseTombstone raise = ability(poor, RaiseTombstone.class);
                if (raise == null)
                    throw new IllegalStateException("the Tombcaller has no Raise Tombstone");
                if (stones.contains(null))
                    throw new IllegalStateException("could not raise the three stones");
                raise.use(level, (Unit) poor, helper.absolutePos(new BlockPos(6, 1, 2)));
                if (Essence.get(poor) != 9 || ((Unit) poor).getCooldown(raise) > 0 || !TombcallerServerEvents.stonesOf(poor).isEmpty())
                    problems.add("Raise Tombstone short of essence was not refused cleanly (essence " + Essence.get(poor) + ")");
                if (TombcallerServerEvents.stonesOf(rich).size() != 3)
                    problems.add("setup: the rich caster keeps " + TombcallerServerEvents.stonesOf(rich).size() + " stones, expected 3");
                // the cap first, then out of reach and in the air with one stone gone (so the cap is not what refuses)
                raise.use(level, (Unit) rich, helper.absolutePos(new BlockPos(9, 1, 11)));
                if (Essence.get(rich) != 30 || ((Unit) rich).getCooldown(raise) > 0 || TombcallerServerEvents.stonesOf(rich).size() != 3)
                    problems.add("a fourth stone was not refused cleanly (essence " + Essence.get(rich) + ", stones "
                        + TombcallerServerEvents.stonesOf(rich).size() + ")");
                // the autocast does not even try at the cap (no "already keeps three" every second), and does again below it
                if (raise.canAutocastNow(level, (Unit) rich))
                    problems.add("the autocast would order a fourth stone at the cap");
                stones.get(2).discard();
                if (!raise.canAutocastNow(level, (Unit) rich))
                    problems.add("with two stones and 30 essence the autocast would not cast");
                String[] what = { "12 blocks off", "in mid-air" };
                BlockPos[] at = { helper.absolutePos(new BlockPos(14, 1, 13)), helper.absolutePos(new BlockPos(4, 8, 9)) };
                for (int i = 0; i < at.length; i++) {
                    raise.use(level, (Unit) rich, at[i]);
                    if (Essence.get(rich) != 30 || ((Unit) rich).getCooldown(raise) > 0)
                        problems.add("Raise Tombstone " + what[i] + " was not refused cleanly (essence " + Essence.get(rich) + ")");
                }
                for (TombstoneUnit s : level.getEntitiesOfClass(TombstoneUnit.class, rich.getBoundingBox().inflate(16)))
                    if (owner.equals(s.getOwnerName()) && !stones.contains(s)) {
                        stones.add(s);
                        problems.add("a refused Raise Tombstone left a stone at " + s.blockPosition().toShortString());
                    }
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                clearStones(level, stones.stream().filter(Objects::nonNull).toList(), owner);
                discardAll(callers);
            }
            finish(helper, problems);
        });
    }

    // --------------------------------------------------------------------------------------- the stone and its creep

    /**
     * A fresh stone's creep covers nothing; 80 ticks older it reaches 2 blocks (a cell 1.5 off is on it, one 3 off is
     * not); much older it stops at 8 (7.5 on, 8.5 off). Past its 90 s the stone crumbles by itself, and leaves no wisp.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void tombstone_creep_grows_a_block_every_2_seconds_and_the_stone_crumbles_after_90(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "grow");
        TombstoneUnit s = stone(helper, null, owner, 8, 8, 0, false);
        if (s == null) {
            helper.fail("could not raise the stone");
            return;
        }
        List<String> early = new ArrayList<>();
        Vec3[] stoneAt = { null };
        helper.runAfterDelay(2, () -> {
            try {
                if (TombstoneUnit.CREEP_MAX_RADIUS != 8f || TombstoneUnit.CREEP_TICKS_PER_BLOCK != 40 || TombstoneUnit.LIFETIME_SECONDS != 90)
                    early.add("the draft set creep to 8 blocks at a block per 2 s and a 90 s stone");
                Vec3 c = s.position();
                if (TombcallerServerEvents.onCreep(level, c.add(1, 0, 0), owner))
                    early.add("a fresh stone's creep already reaches a block");
                s.backdate(80);
                if (!TombcallerServerEvents.onCreep(level, c.add(1.5, 0, 0), owner))
                    early.add("an 80-tick-old stone's creep does not reach 1.5 blocks (radius " + s.creepRadius(level.getGameTime()) + ")");
                if (TombcallerServerEvents.onCreep(level, c.add(3, 0, 0), owner))
                    early.add("an 80-tick-old stone's creep reaches 3 blocks");
                s.backdate(2000);
                if (!TombcallerServerEvents.onCreep(level, c.add(0, 0, 7.5), owner))
                    early.add("a full-grown creep does not reach 7.5 blocks");
                if (TombcallerServerEvents.onCreep(level, c.add(0, 0, 8.5), owner))
                    early.add("the creep grew past 8 blocks");
                if (TombcallerServerEvents.onCreep(level, c.add(0, 0, 3), "gametest_tombcaller_someone_else"))
                    early.add("the creep counts as another owner's");
                stoneAt[0] = c;
                s.backdate(90 * 20);   // its 90 s are up
            } catch (RuntimeException ex) {
                early.add("threw " + ex);
            }
        });
        helper.runAfterDelay(8, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                if (s.isAlive() && !s.isRemoved())
                    problems.add("the stone still stands after its 90 s");
                if (stoneAt[0] != null && wispsNear(stoneAt[0], 2) > 0)
                    problems.add("the crumbled stone dropped an essence wisp: summons must not pay essence");
            } finally {
                if (stoneAt[0] != null)
                    clearWispsNear(stoneAt[0], 2);
                clearStones(level, List.of(s), owner);
            }
            finish(helper, problems);
        });
    }

    /**
     * On a full-grown creep a hurt Gravebound spider of the owner regenerates 2 HP a second, while a hurt friendly
     * vindicator (not Gravebound) on it and a hurt spider off it do not; an enemy on it moves 10% slower and gets its
     * speed back once moved off, while an enemy off it is untouched. Speeds are judged against the attribute's base.
     */
    @GameTest(template = ARENA, timeoutTicks = 80, batch = BATCH)
    public static void tombstone_creep_heals_gravebound_friends_and_slows_enemies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "creep"), foe = owner(helper, "creepfoe");
        TombstoneUnit s = stone(helper, null, owner, 8, 8, FULL_GROWN, false);
        Mob spiderOn = still(helper, EntityRegistrar.SPIDER_UNIT.get(), owner, 11, 8);
        Mob spiderOff = still(helper, EntityRegistrar.SPIDER_UNIT.get(), owner, 0, 0);
        Mob vindOn = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 8, 11);
        Mob enemyOn = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 5, 8);
        Mob enemyOff = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foe, 15, 15);
        List<Mob> all = new ArrayList<>(Arrays.asList(spiderOn, spiderOff, vindOn, enemyOn, enemyOff));
        if (s == null || all.contains(null)) {
            discardAll(spawned(all));
            if (s != null)
                s.discard();
            helper.fail("could not raise the stone and the five units");
            return;
        }
        for (Mob m : List.of(spiderOn, spiderOff, vindOn))
            m.setHealth(m.getMaxHealth() - 10);
        List<String> early = new ArrayList<>();
        float[] hpAt10 = new float[3];
        double[] slowOnAt30 = { 0 }, offAt30 = { 0 };
        helper.runAfterDelay(10, () -> {
            hpAt10[0] = spiderOn.getHealth();
            hpAt10[1] = spiderOff.getHealth();
            hpAt10[2] = vindOn.getHealth();
        });
        helper.runAfterDelay(30, () -> {
            slowOnAt30[0] = speedFactor(enemyOn);
            offAt30[0] = speedFactor(enemyOff);
            BlockPos away = helper.absolutePos(new BlockPos(15, 2, 0));
            enemyOn.teleportTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);   // 10.6 blocks off: off the creep
        });
        helper.runAfterDelay(50, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                if (TombstoneUnit.CREEP_REGEN_PER_SECOND != 2f || TombstoneUnit.CREEP_ENEMY_SLOW != 0.10f)
                    problems.add("the draft set 2 HP/s and 10% slower on the creep");
                float healed = spiderOn.getHealth() - hpAt10[0];
                if (healed < 3f || healed > 5f)
                    problems.add("the Gravebound spider on the creep healed " + healed + " in 2 s, expected about 4");
                if (spiderOff.getHealth() - hpAt10[1] > 0.01f)
                    problems.add("a spider off the creep healed " + (spiderOff.getHealth() - hpAt10[1]));
                if (vindOn.getHealth() - hpAt10[2] > 0.01f)
                    problems.add("a friendly vindicator (not Gravebound) on the creep healed " + (vindOn.getHealth() - hpAt10[2]));
                if (Math.abs(slowOnAt30[0] - 0.9) > 0.005)
                    problems.add("an enemy on the creep moves at " + slowOnAt30[0] + " of its speed, expected 0.9");
                if (Math.abs(offAt30[0] - 1.0) > 0.005)
                    problems.add("an enemy off the creep moves at " + offAt30[0] + " of its speed");
                if (Math.abs(speedFactor(enemyOn) - 1.0) > 0.005)
                    problems.add("the enemy moved off the creep still moves at " + speedFactor(enemyOn) + " of its speed");
                if (Math.abs(speedFactor(spiderOn) - 1.0) > 0.005)
                    problems.add("the creep changed a friend's speed (" + speedFactor(spiderOn) + ")");
            } finally {
                discardAll(all);
                clearStones(level, List.of(s), owner);
            }
            finish(helper, problems);
        });
    }

    /**
     * A stone raises a Risen Skeleton when its time comes: a RisenSkeletonUnit for its owner, marked summoned, crumbling
     * 30 s later, beside the stone, costing no population (the owner's population stays 0), and the next raise is 20 s
     * on. When the next raise comes with that one still standing, a second rises beside it (lovish: every 20 s,
     * whatever still stands); one killed drops no wisp.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void tombstone_raises_a_pop_free_skeleton_every_20_seconds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "risen");
        TombstoneUnit s = stone(helper, null, owner, 8, 8, 0, false);
        if (s == null) {
            helper.fail("could not raise the stone");
            return;
        }
        List<String> early = new ArrayList<>();
        List<Mob> first = new ArrayList<>();
        Vec3[] killedAt = { null };
        helper.runAfterDelay(2, () -> s.setNextRaise(level.getGameTime()));
        helper.runAfterDelay(4, () -> {
            try {
                long now = level.getGameTime();
                first.addAll(TombcallerServerEvents.risenOf(s));
                if (first.size() != 1) {
                    early.add("the stone raised " + first.size() + " skeletons when its time came, expected 1");
                } else {
                    Mob m = first.get(0);
                    if (!(m instanceof RisenSkeletonUnit) || !owner.equals(((Unit) m).getOwnerName()))
                        early.add("the risen unit is a " + m.getClass().getSimpleName() + " of " + ((Unit) m).getOwnerName());
                    if (!Essence.isSummoned(m) || !TombcallerServerEvents.isRisen(m))
                        early.add("the Risen Skeleton is not marked summoned / risen");
                    if (((Unit) m).getCost().population != 0)
                        early.add("the Risen Skeleton costs " + ((Unit) m).getCost().population + " population, lovish set none");
                    long left = TombcallerServerEvents.risenEnd(m) - now;
                    if (left < 30 * 20 - 4 || left > 30 * 20)
                        early.add("the Risen Skeleton crumbles in " + left + " ticks, expected 600");
                    if (m.distanceTo(s) > 3)
                        early.add("the skeleton rose " + String.format("%.1f", m.distanceTo(s)) + " blocks from the stone");
                }
                if (UnitServerEvents.getCurrentPopulation(owner) != 0)
                    early.add("with a skeleton up the owner's population is " + UnitServerEvents.getCurrentPopulation(owner) + ", expected 0");
                if (s.nextRaise() - now < 20 * 20 - 4)
                    early.add("the stone's next raise is " + (s.nextRaise() - now) + " ticks off, expected 20 s");
                s.setNextRaise(now);   // time again, with one standing
            } catch (RuntimeException ex) {
                early.add("threw " + ex);
            }
        });
        helper.runAfterDelay(7, () -> {
            try {
                List<Mob> now = TombcallerServerEvents.risenOf(s);
                if (now.size() != first.size() + 1)
                    early.add("the next raise with one standing left " + now.size() + " skeletons, expected " + (first.size() + 1));
                for (Mob m : now)
                    if (!first.contains(m))
                        first.add(m);
                if (!first.isEmpty()) {
                    killedAt[0] = first.get(0).position();
                    first.get(0).kill();
                }
            } catch (RuntimeException ex) {
                early.add("killing the skeleton threw " + ex);
            }
        });
        helper.runAfterDelay(11, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                if (TombstoneUnit.RAISE_EVERY_SECONDS != 20 || TombstoneUnit.SKELETON_LIFETIME_SECONDS != 30)
                    problems.add("lovish set a skeleton every 20 s, 30 s each");
                if (killedAt[0] != null && wispsNear(killedAt[0], 2) > 0)
                    problems.add("the killed Risen Skeleton dropped an essence wisp");
            } finally {
                if (killedAt[0] != null)
                    clearWispsNear(killedAt[0], 2);
                discardAll(first);
                clearStones(level, List.of(s), owner);
            }
            finish(helper, problems);
        });
    }

    /**
     * Killing a stone does not lift its creep at once: it fades over 10 s. A cell 5 blocks off is still on it just after
     * the kill and off it 6 s later, when a cell 2 off still is; 10 s on nothing is left. The dead stone drops no wisp.
     */
    @GameTest(template = ARENA, timeoutTicks = 260, batch = BATCH)
    public static void tombstone_creep_fades_over_10_seconds_after_the_stone_dies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "fade");
        TombstoneUnit s = stone(helper, null, owner, 8, 8, FULL_GROWN, false);
        if (s == null) {
            helper.fail("could not raise the stone");
            return;
        }
        Vec3 c = s.position(), at5 = c.add(5, 0, 0), at2 = c.add(0, 0, 2);
        List<String> early = new ArrayList<>();
        helper.runAfterDelay(2, () -> {
            try {
                if (!TombcallerServerEvents.onCreep(level, at5, owner))
                    early.add("setup: a cell 5 off a full-grown stone is not on its creep");
                s.kill();
            } catch (RuntimeException ex) {
                early.add("killing the stone threw " + ex);
            }
        });
        helper.runAfterDelay(4, () -> {
            if (!TombcallerServerEvents.onCreep(level, at5, owner))
                early.add("the creep vanished with the stone instead of fading");
            if (wispsNear(c, 2) > 0)
                early.add("the killed stone dropped an essence wisp");
        });
        helper.runAfterDelay(122, () -> {
            if (TombcallerServerEvents.onCreep(level, at5, owner))
                early.add("6 s after the stone fell the creep still reaches 5 blocks: not fading");
            if (!TombcallerServerEvents.onCreep(level, at2, owner))
                early.add("6 s after the stone fell the creep no longer reaches 2 blocks: gone too soon");
        });
        helper.runAfterDelay(215, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                if (TombstoneUnit.CREEP_FADE_SECONDS != 10)
                    problems.add("the draft set a 10 s fade, CREEP_FADE_SECONDS is " + TombstoneUnit.CREEP_FADE_SECONDS);
                if (TombcallerServerEvents.onCreep(level, at2, owner) || TombcallerServerEvents.onCreep(level, c, owner))
                    problems.add("10 s after the stone fell its creep is still there");
            } finally {
                clearWispsNear(c, 2);
                clearStones(level, List.of(s), owner);
            }
            finish(helper, problems);
        });
    }

    /**
     * On its own creep a Tombcaller pulls wisps from 12 blocks: a wisp 11 blocks off on the creep of its stone flies in,
     * one 11 blocks off away from the creep stays where it floats.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void tombcaller_pulls_wisps_from_12_blocks_on_its_creep(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "pull");
        Mob caller = still(helper, EntityRegistrar.TOMBCALLER_UNIT.get(), owner, 2, 2);
        if (caller == null) {
            helper.fail("could not spawn the Tombcaller");
            return;
        }
        Essence.setCarrier(caller, true);
        TombstoneUnit s = stone(helper, caller, owner, 6, 2, FULL_GROWN, false);
        Entity onCreep = EssenceServerEvents.spawnWisp(level, caller.getX() + 11, caller.getY() + 1, caller.getZ(), 3);
        Entity offCreep = EssenceServerEvents.spawnWisp(level, caller.getX(), caller.getY() + 1, caller.getZ() + 11, 4);
        List<Entity> wisps = new ArrayList<>();
        if (onCreep != null) wisps.add(onCreep);
        if (offCreep != null) wisps.add(offCreep);
        if (s == null || wisps.size() != 2) {
            discardAll(wisps);
            caller.discard();
            if (s != null)
                s.discard();
            helper.fail("could not raise the stone or the wisps");
            return;
        }
        Vec3 offAt = offCreep.position();
        helper.runAfterDelay(80, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (TombstoneUnit.CREEP_PULL_RANGE != 12.0)
                    problems.add("lovish set a 12-block pull on the creep, CREEP_PULL_RANGE is " + TombstoneUnit.CREEP_PULL_RANGE);
                if (!onCreep.isRemoved())
                    problems.add("the wisp 11 blocks off on the creep was not pulled in");
                if (Essence.get(caller) != 3)
                    problems.add("the Tombcaller holds " + Essence.get(caller) + " essence, expected the creep wisp's 3");
                if (offCreep.isRemoved() || offCreep.position().distanceTo(offAt) > 0.05)
                    problems.add("the wisp 11 blocks off away from the creep was taken or moved");
            } finally {
                discardAll(wisps);
                clearStones(level, List.of(s), owner);
                caller.discard();
            }
            finish(helper, problems);
        });
    }

    // ----------------------------------------------------------------------------------------------- Grave Tide

    /**
     * Grave Tide: a hurt Gravebound spider 3 blocks off runs 30% faster for 8 s and regenerates 2 HP/s; one far off but
     * on the caster's creep is hastened for 12 s; the Tombcaller itself is hastened; a spider 11 off and off the creep, a
     * friendly vindicator (not Gravebound) and an enemy spider beside it are not. 5 of 30 essence spent, the 12 s
     * cooldown starts and a second cast at once is refused. After the 8 s the near spider's speed is its own again
     * while the creep spider's still lasts.
     */
    @GameTest(template = ARENA, timeoutTicks = 220, batch = BATCH)
    public static void tombcaller_grave_tide_hastens_and_heals_gravebound_friends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "tide"), foe = owner(helper, "tidefoe");
        Mob caller = caller(helper, owner, 2, 2, 30);
        Mob near = still(helper, EntityRegistrar.SPIDER_UNIT.get(), owner, 5, 2);
        Mob onCreep = still(helper, EntityRegistrar.SPIDER_UNIT.get(), owner, 13, 9);
        Mob farOff = still(helper, EntityRegistrar.SPIDER_UNIT.get(), owner, 2, 13);
        Mob vind = still(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 4, 4);
        Mob enemy = still(helper, EntityRegistrar.SPIDER_UNIT.get(), foe, 3, 5);
        List<Mob> all = new ArrayList<>(Arrays.asList(caller, near, onCreep, farOff, vind, enemy));
        TombstoneUnit s = caller == null ? null : stone(helper, caller, owner, 12, 12, FULL_GROWN, false);
        if (s == null || all.contains(null)) {
            discardAll(spawned(all));
            if (s != null)
                s.discard();
            helper.fail("could not spawn the Tombcaller, its stone and the five units");
            return;
        }
        near.setHealth(near.getMaxHealth() - 10);
        List<String> early = new ArrayList<>();
        float[] nearHp = { 0 };
        helper.runAfterDelay(2, () -> {
            try {
                GraveTide tide = ability(caller, GraveTide.class);
                if (tide == null)
                    throw new IllegalStateException("the Tombcaller has no Grave Tide");
                if (GraveTide.SPEED_BONUS != 0.30f || GraveTide.REGEN_PER_SECOND != 2f || GraveTide.DURATION_SECONDS != 8
                        || GraveTide.CREEP_DURATION_SECONDS != 12 || GraveTide.RADIUS != 8)
                    early.add("the draft set +30% and 2 HP/s for 8 s (12 on the creep) within 8");
                long now = level.getGameTime();
                tide.use(level, (Unit) caller, caller.blockPosition());
                nearHp[0] = near.getHealth();
                if (Math.abs(speedFactor(near) - 1.3) > 0.005)
                    early.add("the spider 3 blocks off moves at " + speedFactor(near) + " of its speed, expected 1.3");
                if (Math.abs(TombcallerServerEvents.tideEnd(near) - (now + 8 * 20)) > 1)
                    early.add("the near spider's tide ends at " + TombcallerServerEvents.tideEnd(near) + ", expected 8 s on (" + (now + 160) + ")");
                if (Math.abs(speedFactor(onCreep) - 1.3) > 0.005 || Math.abs(TombcallerServerEvents.tideEnd(onCreep) - (now + 12 * 20)) > 1)
                    early.add("the spider on the creep moves at " + speedFactor(onCreep) + " until " + TombcallerServerEvents.tideEnd(onCreep)
                        + ", expected 1.3 for 12 s (" + (now + 240) + ")");
                if (TombcallerServerEvents.tideEnd(caller) <= now)
                    early.add("the Tombcaller did not hasten itself");
                for (Mob m : List.of(farOff, vind, enemy)) {
                    String what = m == farOff ? "a spider 11 off and off the creep" : m == vind ? "a friendly vindicator (not Gravebound)" : "an enemy spider";
                    if (TombcallerServerEvents.tideEnd(m) > 0 || Math.abs(speedFactor(m) - 1.0) > 0.005)
                        early.add(what + " was hastened");
                }
                if (Essence.get(caller) != 25)
                    early.add("the Tombcaller holds " + Essence.get(caller) + " essence after Grave Tide, expected 25");
                float cd = ((Unit) caller).getCooldown(tide);
                if (cd < 12 * 20 - 2)
                    early.add("Grave Tide's cooldown is " + cd + " ticks after the cast, expected 240");
                tide.use(level, (Unit) caller, caller.blockPosition());
                if (Essence.get(caller) != 25)
                    early.add("a second Grave Tide during the cooldown spent essence (" + Essence.get(caller) + ")");
            } catch (RuntimeException ex) {
                early.add("casting threw " + ex);
            }
        });
        helper.runAfterDelay(42, () -> {
            float healed = near.getHealth() - nearHp[0];
            if (healed < 3f || healed > 5f)
                early.add("the hastened spider healed " + healed + " in 2 s, expected about 4");
        });
        helper.runAfterDelay(175, () -> {
            List<String> problems = new ArrayList<>(early);
            try {
                if (Math.abs(speedFactor(near) - 1.0) > 0.005)
                    problems.add("8.6 s after Grave Tide the near spider still moves at " + speedFactor(near) + " of its speed");
                if (Math.abs(speedFactor(onCreep) - 1.3) > 0.005)
                    problems.add("the creep spider's 12 s tide ended early (" + speedFactor(onCreep) + ")");
            } finally {
                discardAll(all);
                clearStones(level, List.of(s), owner);
            }
            finish(helper, problems);
        });
    }

    /** Short of essence (4 of 5) Grave Tide is refused: nothing spent, no cooldown, nobody hastened. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = BATCH)
    public static void tombcaller_grave_tide_refuses_when_short(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "tideno");
        Mob caller = caller(helper, owner, 4, 4, 4);
        Mob friend = still(helper, EntityRegistrar.SPIDER_UNIT.get(), owner, 6, 4);
        List<Mob> all = new ArrayList<>(Arrays.asList(caller, friend));
        if (all.contains(null)) {
            discardAll(spawned(all));
            helper.fail("could not spawn the Tombcaller and its friend");
            return;
        }
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                GraveTide tide = ability(caller, GraveTide.class);
                if (tide == null)
                    throw new IllegalStateException("the Tombcaller has no Grave Tide");
                tide.use(level, (Unit) caller, caller.blockPosition());
                if (Essence.get(caller) != 4 || ((Unit) caller).getCooldown(tide) > 0)
                    problems.add("Grave Tide short of essence was not refused cleanly (essence " + Essence.get(caller) + ")");
                if (TombcallerServerEvents.tideEnd(friend) > 0 || Math.abs(speedFactor(friend) - 1.0) > 0.005)
                    problems.add("a refused Grave Tide hastened the friend");
            } catch (RuntimeException ex) {
                problems.add("threw " + ex);
            } finally {
                discardAll(all);
            }
            finish(helper, problems);
        });
    }
}
