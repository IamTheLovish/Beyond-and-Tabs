package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.ability.AutocastState;
import com.solegendary.reignofnether.ability.abilities.CallAngel;
import com.solegendary.reignofnether.ability.abilities.Empower;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.essence.Essence;
import com.solegendary.reignofnether.essence.EssenceServerEvents;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.AngelCallerProd;
import com.solegendary.reignofnether.unit.units.villagers.AngelCallerUnit;
import com.solegendary.reignofnether.unit.units.villagers.AngelUnit;
import com.solegendary.reignofnether.unit.units.villagers.MenderAngelUnit;
import com.solegendary.reignofnether.unit.units.villagers.SeraphArcherUnit;
import com.solegendary.reignofnether.unit.units.villagers.SwordSeraphUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Sunforged essence caster, the Angel Caller (design/essence_casters.md; lovish, Oct 29 2026: "the angels casters
 * can summon three different angels that help: one that heals, one that is a flying archer and another that is a
 * melee engager that can fly"). Written before the code:
 * <ul>
 *   <li>it is trained at the Arcane Tower (the Sunforged T2 lab) for a cost, is an essence carrier that gathers wisps,
 *       and has the three angel calls plus the shared Empower;</li>
 *   <li>Mender Angel 5 essence / 12 s cooldown / lives 20 s, Seraph Archer 10 / 25 s / 25 s, Sword Seraph 20 / 60 s /
 *       30 s; a call spends exactly its essence and sets its cooldown; short of essence, or while that angel still
 *       lives, it is refused - nothing spent, no cooldown; on cooldown the click does nothing;</li>
 *   <li>each angel appears beside the caster under its owner, marked summoned, flies (leaves the ground), does its job
 *       - the Mender heals a hurt friend and not a hurt foe, the Archer shoots an enemy from range, the Sword Seraph
 *       dives onto an enemy and hits it in melee - and fades when its time is up; neither fading nor a kill drops a
 *       wisp.</li>
 * </ul>
 * Casts go through the click path (UnitActionItem, as the AutoCaster's own orders do), with the caster's autocast off
 * and its AI off so it stands still. Every test records in its early callbacks and judges only in its last one, which
 * always cleans up (angels, units, wisps) whatever the verdict. The three angel tests run in batches of their own: an
 * angel looks 12 blocks round itself and would fight a neighbouring arena's units.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class AngelCallerGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final String BATCH = "angel_caller";

    private static String owner(GameTestHelper helper, String what) {
        return "gametest_angel_" + what + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    /** An Angel Caller holding {@code essence} that stands still and never casts by itself. Null if it would not spawn. */
    @Nullable
    private static Mob caller(GameTestHelper helper, String owner, int x, int z, int essence) {
        Mob m = HeroGameTests.spawnUnit(helper, EntityRegistrar.ANGEL_CALLER_UNIT.get(), owner, x, z);
        if (m == null)
            return null;
        m.setNoAi(true);
        AutocastState.setAll((Unit) m, false);
        if (essence > 0)
            Essence.add(m, essence);
        return m;
    }

    /** A unit that stands where it is put. */
    @Nullable
    private static Mob still(GameTestHelper helper, net.minecraft.world.entity.EntityType<? extends Mob> type, String owner, int x, int z) {
        Mob m = HeroGameTests.spawnUnit(helper, type, owner, x, z);
        if (m != null) {
            m.setNoAi(true);
            AutocastState.setAll((Unit) m, false);
        }
        return m;
    }

    @Nullable
    private static CallAngel call(Mob caster, CallAngel.Kind kind) {
        for (Ability a : ((Unit) caster).getAbilities().get())
            if (a instanceof CallAngel c && c.kind == kind)
                return c;
        return null;
    }

    /** The caster's cooldown on {@code kind}'s call (-1 if it has no such call). */
    private static float cooldown(Mob caster, CallAngel.Kind kind) {
        CallAngel c = call(caster, kind);
        return c == null ? -1 : ((Unit) caster).getCooldown(c);
    }

    /** A click on the call's button for this caster, through the order path a player's click (and the AutoCaster) takes. */
    private static void click(ServerLevel level, Mob caster, CallAngel.Kind kind) {
        new UnitActionItem(((Unit) caster).getOwnerName(), kind.action, -1, new int[] { caster.getId() },
            caster.blockPosition(), BlockPos.ZERO).action(level);
    }

    /** Live angels of {@code kind} that {@code caster} called. */
    private static List<AngelUnit> angels(Mob caster, CallAngel.Kind kind) {
        List<AngelUnit> found = new ArrayList<>();
        UUID id = caster.getUUID();
        for (LivingEntity le : UnitServerEvents.getAllUnits())
            if (le instanceof AngelUnit a && a.kind() == kind && a.isAlive() && !a.isRemoved() && id.equals(a.getSummoner()))
                found.add(a);
        return found;
    }

    /** Live wisps within {@code r} blocks of {@code at}. */
    private static List<Entity> wispsNear(Vec3 at, double r) {
        List<Entity> found = new ArrayList<>();
        for (Entity w : EssenceServerEvents.getWisps())
            if (!w.isRemoved() && w.position().distanceToSqr(at) <= r * r)
                found.add(w);
        return found;
    }

    /** Everything a test made, gone: its angels, its units and any wisp near them or near the given spots. */
    private static void cleanUp(@Nullable Mob caster, List<? extends Entity> units, List<Vec3> spots) {
        List<Vec3> near = new ArrayList<>(spots);
        if (caster != null) {
            for (CallAngel.Kind k : CallAngel.Kind.values())
                for (AngelUnit a : angels(caster, k)) {
                    near.add(a.position());
                    a.discard();
                }
            near.add(caster.position());
            caster.discard();
        }
        for (Entity e : units)
            if (e != null) {
                near.add(e.position());
                if (!e.isRemoved())
                    e.discard();
            }
        for (Vec3 at : near)
            wispsNear(at, 4).forEach(Entity::discard);
    }

    private static void finish(GameTestHelper helper, List<String> problems) {
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /** In the air: clear of the ground under it by a block or more, and not standing. */
    private static boolean airborne(Entity angel, double groundY) {
        return !angel.onGround() && angel.getY() - groundY >= 1.0;
    }

    // ------------------------------------------------------------------------------------------------ the unit

    /**
     * The Angel Caller is trained at the Arcane Tower for a real cost and is a Sunforged unit; it is an essence carrier
     * that pulls a wisp 5 blocks away in; it has the three angel calls with lovish's numbers and the shared Empower.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = BATCH)
    public static void angel_caller_is_trained_at_the_arcane_tower_and_gathers_essence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "unit");
        Mob caster = caller(helper, owner, 3, 3, 0);
        if (caster == null) {
            helper.fail("could not spawn an Angel Caller");
            return;
        }
        List<String> problems = new ArrayList<>();
        Entity wisp = EssenceServerEvents.spawnWisp(level, caster.getX() + 5, caster.getY() + 1, caster.getZ(), 4);
        List<Entity> made = new ArrayList<>();
        if (wisp != null)
            made.add(wisp);
        else
            problems.add("spawnWisp made no wisp");
        helper.runAfterDelay(80, () -> {
            try {
                if (!Buildings.ARCANE_TOWER.productions.get().contains(ProductionItems.ANGEL_CALLER))
                    problems.add("the Arcane Tower does not train the Angel Caller");
                if (EntityRegistrar.getEntityType(AngelCallerProd.itemName) != EntityRegistrar.ANGEL_CALLER_UNIT.get())
                    problems.add("the Angel Caller's production item does not make an angel_caller_unit");
                ResourceCost cost = AngelCallerProd.cost;
                if (cost == null || cost.metal() <= 0 || cost.population <= 0 || cost.ticks <= 0)
                    problems.add("the Angel Caller should cost metal, population and time, has " + (cost == null ? "no cost"
                        : cost.metal() + " metal, " + cost.population + " population, " + cost.ticks + " ticks"));
                if (Factions.getFaction((Unit) caster) != Factions.VILLAGERS)
                    problems.add("the Angel Caller is not a Sunforged unit");
                if (caster.getMaxHealth() != AngelCallerUnit.maxHealth)
                    problems.add("max health " + caster.getMaxHealth() + ", the class says " + AngelCallerUnit.maxHealth);
                if (!Essence.isCarrier(caster))
                    problems.add("the Angel Caller is not an essence carrier");
                else if (Essence.get(caster) != 4)
                    problems.add("the Angel Caller holds " + Essence.get(caster) + " essence after a 4-essence wisp 5 blocks away, expected 4");
                int[][] want = { { 5, 12, 20 }, { 10, 25, 25 }, { 20, 60, 30 } };   // lovish's tiers: essence, cooldown s, life s
                for (CallAngel.Kind k : CallAngel.Kind.values()) {
                    int[] w = want[k.ordinal()];
                    if (k.essence != w[0] || k.cooldownSeconds != w[1] || k.lifeSeconds != w[2])
                        problems.add(k.title + " is " + k.essence + " essence / " + k.cooldownSeconds + " s / lives " + k.lifeSeconds
                            + " s, expected " + w[0] + " / " + w[1] + " / " + w[2]);
                    CallAngel c = call(caster, k);
                    if (c == null)
                        problems.add("the Angel Caller has no " + k.title + " call");
                    else if (c.cooldownMax != k.cooldownSeconds * ResourceCost.TICKS_PER_SECOND)
                        problems.add(k.title + "'s cooldown is " + c.cooldownMax + " ticks, expected " + k.cooldownSeconds * 20);
                }
                boolean empower = false;
                for (Ability a : ((Unit) caster).getAbilities().get())
                    if (a instanceof Empower)
                        empower = true;
                if (!empower)
                    problems.add("the Angel Caller has no Empower");
            } finally {
                cleanUp(caster, made, List.of());
            }
            finish(helper, problems);
        });
    }

    /**
     * Each call spends exactly its essence and starts its cooldown; it is refused - nothing spent, no cooldown - when the
     * caster is short of essence and while its angel still lives; on cooldown a click does nothing; the angel comes
     * under the caster's owner, marked summoned with its lifetime, and a killed angel drops no wisp.
     */
    @GameTest(template = ARENA, timeoutTicks = 120, batch = BATCH)
    public static void angel_caller_spends_essence_refuses_when_short_and_keeps_one_of_each(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "spend");
        Mob caster = caller(helper, owner, 3, 3, 4);
        if (caster == null) {
            helper.fail("could not spawn an Angel Caller");
            return;
        }
        List<String> problems = new ArrayList<>();
        List<Vec3> spots = new ArrayList<>();
        CallAngel.Kind M = CallAngel.Kind.MENDER, A = CallAngel.Kind.ARCHER, S = CallAngel.Kind.SWORD;
        AngelUnit[] mender = { null };
        helper.runAfterDelay(3, () -> {
            click(level, caster, M);   // 4 essence: short of 5
            if (Essence.get(caster) != 4 || cooldown(caster, M) != 0 || !angels(caster, M).isEmpty())
                problems.add("a Mender call with 4 essence was not refused cleanly: essence " + Essence.get(caster)
                    + ", cooldown " + cooldown(caster, M) + ", menders " + angels(caster, M).size());
        });
        helper.runAfterDelay(5, () -> {
            Essence.add(caster, 5);   // 9
            click(level, caster, M);
            List<AngelUnit> ms = angels(caster, M);
            if (Essence.get(caster) != 4)
                problems.add("a Mender call with 9 essence left " + Essence.get(caster) + ", expected 4");
            float cd = cooldown(caster, M);
            if (cd < M.cooldownSeconds * 20 - 2 || cd > M.cooldownSeconds * 20)
                problems.add("after a Mender call its cooldown is " + cd + ", expected " + M.cooldownSeconds * 20);
            if (ms.size() != 1) {
                problems.add("a Mender call made " + ms.size() + " Mender Angels, expected 1");
                return;
            }
            AngelUnit a = ms.get(0);
            mender[0] = a;
            if (!(a instanceof MenderAngelUnit))
                problems.add("the Mender call made a " + a.getClass().getSimpleName());
            if (!owner.equals(a.getOwnerName()))
                problems.add("the Mender Angel belongs to '" + a.getOwnerName() + "', not the caster's owner");
            if (!Essence.isSummoned(a))
                problems.add("the Mender Angel is not marked summoned");
            if (a.ticksLeft() < M.lifeSeconds * 20 - 5 || a.ticksLeft() > M.lifeSeconds * 20)
                problems.add("the Mender Angel has " + a.ticksLeft() + " ticks to live, expected " + M.lifeSeconds * 20);
            if (a.distanceTo(caster) > 4)
                problems.add("the Mender Angel appeared " + a.distanceTo(caster) + " blocks from its caster");
        });
        helper.runAfterDelay(7, () -> {
            click(level, caster, M);   // on cooldown: nothing
            if (Essence.get(caster) != 4 || angels(caster, M).size() != 1)
                problems.add("a Mender click on cooldown changed something: essence " + Essence.get(caster) + ", menders "
                    + angels(caster, M).size());
            CallAngel c = call(caster, M);
            if (c != null)
                c.setCooldown(0, (Unit) caster);
            Essence.add(caster, 6);   // 10: enough, but the Mender still lives
            click(level, caster, M);
            if (Essence.get(caster) != 10 || cooldown(caster, M) != 0 || angels(caster, M).size() != 1)
                problems.add("a second Mender while the first lives was not refused cleanly: essence " + Essence.get(caster)
                    + " (expected 10), cooldown " + cooldown(caster, M) + ", menders " + angels(caster, M).size());
            Essence.trySpend(caster, 1);   // 9: short of the Archer's 10
            click(level, caster, A);
            if (Essence.get(caster) != 9 || cooldown(caster, A) != 0 || !angels(caster, A).isEmpty())
                problems.add("an Archer call with 9 essence was not refused cleanly: essence " + Essence.get(caster)
                    + ", cooldown " + cooldown(caster, A) + ", archers " + angels(caster, A).size());
        });
        helper.runAfterDelay(9, () -> {
            if (mender[0] != null && mender[0].isAlive()) {
                spots.add(mender[0].position());
                mender[0].kill();
            }
        });
        helper.runAfterDelay(15, () -> {
            if (mender[0] != null) {
                if (mender[0].isAlive())
                    problems.add("the killed Mender Angel is still alive");
                if (!spots.isEmpty() && !wispsNear(spots.get(0), 3).isEmpty())
                    problems.add("the killed Mender Angel dropped " + wispsNear(spots.get(0), 3).size() + " wisp(s)");
            }
            Essence.add(caster, 21);   // 30
            click(level, caster, A);
            click(level, caster, S);
            if (Essence.get(caster) != 0)
                problems.add("an Archer and a Sword Seraph from 30 essence left " + Essence.get(caster) + ", expected 0");
            for (CallAngel.Kind k : new CallAngel.Kind[] { A, S }) {
                List<AngelUnit> as = angels(caster, k);
                if (as.size() != 1)
                    problems.add("a " + k.title + " call made " + as.size() + " angels, expected 1");
                else if (!Essence.isSummoned(as.get(0)) || !owner.equals(as.get(0).getOwnerName()))
                    problems.add("the " + k.title + " is not a summon of the caster's owner");
                else if (!(k == A ? as.get(0) instanceof SeraphArcherUnit : as.get(0) instanceof SwordSeraphUnit))
                    problems.add("the " + k.title + " call made a " + as.get(0).getClass().getSimpleName());
                float cd = cooldown(caster, k);
                if (cd < k.cooldownSeconds * 20 - 2 || cd > k.cooldownSeconds * 20)
                    problems.add("after a " + k.title + " call its cooldown is " + cd + ", expected " + k.cooldownSeconds * 20);
            }
        });
        helper.runAfterDelay(20, () -> {
            cleanUp(caster, List.of(), spots);
            finish(helper, problems);
        });
    }

    // ------------------------------------------------------------------------------------------------ the angels

    /**
     * The Mender Angel flies, heals a hurt friend (and not a hurt foe beside it), lives its 20 s and fades without a
     * wisp.
     */
    @GameTest(template = ARENA, timeoutTicks = 480, batch = "angel_caller_mender")
    public static void angel_caller_mender_angel_flies_heals_a_friend_and_fades_after_20s(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "mender"), foeOwner = owner(helper, "menderfoe");
        Mob caster = caller(helper, owner, 3, 3, CallAngel.Kind.MENDER.essence);
        Mob friend = still(helper, EntityRegistrar.VILLAGER_UNIT.get(), owner, 6, 4);
        Mob foe = still(helper, EntityRegistrar.VILLAGER_UNIT.get(), foeOwner, 3, 10);
        List<Mob> units = new ArrayList<>(java.util.Arrays.asList(friend, foe));
        if (caster == null || friend == null || foe == null) {
            cleanUp(caster, units, List.of());
            helper.fail("could not spawn the caster, the friend and the foe");
            return;
        }
        friend.setHealth(friend.getMaxHealth() * 0.4f);
        foe.setHealth(foe.getMaxHealth() * 0.4f);
        float friendStart = friend.getHealth(), foeStart = foe.getHealth();
        double groundY = caster.getY();
        int cast = 2, life = CallAngel.Kind.MENDER.lifeSeconds * 20;
        AngelUnit[] angel = { null };
        Vec3[] last = { null };
        int[] grounded = { 0 }, seen = { 0 };
        boolean[] aliveLate = { false }, goneAfter = { false };
        helper.runAfterDelay(cast, () -> {
            click(level, caster, CallAngel.Kind.MENDER);
            List<AngelUnit> as = angels(caster, CallAngel.Kind.MENDER);
            angel[0] = as.isEmpty() ? null : as.get(0);
        });
        for (int t = cast + 10; t <= cast + life + 30; t += 10) {
            int tick = t;
            helper.runAfterDelay(t, () -> {
                AngelUnit a = angel[0];
                if (a == null)
                    return;
                boolean alive = a.isAlive() && !a.isRemoved();
                if (alive) {
                    last[0] = a.position();
                    seen[0]++;
                    if (!airborne(a, groundY))
                        grounded[0]++;
                }
                if (tick >= cast + life - 20 && tick < cast + life - 10 && alive)
                    aliveLate[0] = true;
                if (tick >= cast + life + 20 && !alive)
                    goneAfter[0] = true;
            });
        }
        helper.runAfterDelay(cast + life + 40, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (angel[0] == null)
                    problems.add("the Mender call made no Mender Angel");
                else {
                    if (seen[0] == 0 || grounded[0] > 0)
                        problems.add("the Mender Angel was on the ground in " + grounded[0] + " of " + seen[0] + " looks; it should fly");
                    if (friend.getHealth() < friendStart + MenderAngelUnit.HEAL)
                        problems.add("the hurt friend went from " + friendStart + " to " + friend.getHealth() + " HP; the Mender heals "
                            + MenderAngelUnit.HEAL + " a cast");
                    if (foe.getHealth() > foeStart)
                        problems.add("the Mender healed a foe (" + foeStart + " -> " + foe.getHealth() + ")");
                    if (!aliveLate[0])
                        problems.add("the Mender Angel did not last its " + CallAngel.Kind.MENDER.lifeSeconds + " s");
                    if (!goneAfter[0])
                        problems.add("the Mender Angel was still there 1 s after its " + CallAngel.Kind.MENDER.lifeSeconds + " s");
                    if (last[0] != null && !wispsNear(last[0], 3).isEmpty())
                        problems.add("the faded Mender Angel left a wisp");
                }
            } finally {
                cleanUp(caster, units, last[0] == null ? List.of() : List.of(last[0]));
            }
            finish(helper, problems);
        });
    }

    /**
     * The Seraph Archer flies, shoots an enemy 9 blocks off from range (it is never near it when the enemy is hurt),
     * lives its 25 s and fades without a wisp.
     */
    @GameTest(template = ARENA, timeoutTicks = 580, batch = "angel_caller_archer")
    public static void angel_caller_seraph_archer_flies_shoots_from_range_and_fades_after_25s(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "archer"), foeOwner = owner(helper, "archerfoe");
        Mob caster = caller(helper, owner, 3, 3, CallAngel.Kind.ARCHER.essence);
        Mob foe = still(helper, EntityRegistrar.IRON_GOLEM_UNIT.get(), foeOwner, 3, 12);
        List<Mob> units = new ArrayList<>(java.util.Collections.singletonList(foe));
        if (caster == null || foe == null) {
            cleanUp(caster, units, List.of());
            helper.fail("could not spawn the caster and the target");
            return;
        }
        double groundY = caster.getY();
        int cast = 2, life = CallAngel.Kind.ARCHER.lifeSeconds * 20;
        AngelUnit[] angel = { null };
        Vec3[] last = { null };
        float[] foeHp = { foe.getHealth() };
        double[] nearestHit = { Double.MAX_VALUE };
        double[] height = { Double.MAX_VALUE, -Double.MAX_VALUE };   // lowest, highest over the ground
        int[] grounded = { 0 }, seen = { 0 }, hits = { 0 };
        boolean[] aliveLate = { false }, goneAfter = { false };
        helper.runAfterDelay(cast, () -> {
            click(level, caster, CallAngel.Kind.ARCHER);
            List<AngelUnit> as = angels(caster, CallAngel.Kind.ARCHER);
            angel[0] = as.isEmpty() ? null : as.get(0);
        });
        for (int t = cast + 5; t <= cast + life + 30; t += 5) {
            int tick = t;
            helper.runAfterDelay(t, () -> {
                AngelUnit a = angel[0];
                if (a == null)
                    return;
                boolean alive = a.isAlive() && !a.isRemoved();
                if (alive) {
                    last[0] = a.position();
                    seen[0]++;
                    if (!airborne(a, groundY))
                        grounded[0]++;
                    height[0] = Math.min(height[0], a.getY() - groundY);
                    height[1] = Math.max(height[1], a.getY() - groundY);
                    if (foe.isAlive() && foe.getHealth() < foeHp[0]) {
                        hits[0]++;
                        nearestHit[0] = Math.min(nearestHit[0], a.distanceTo(foe));
                    }
                }
                foeHp[0] = foe.getHealth();
                if (tick >= cast + life - 20 && tick < cast + life - 10 && alive)
                    aliveLate[0] = true;
                if (tick >= cast + life + 20 && !alive)
                    goneAfter[0] = true;
            });
        }
        helper.runAfterDelay(cast + life + 40, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (angel[0] == null)
                    problems.add("the Archer call made no Seraph Archer");
                else {
                    if (seen[0] == 0 || grounded[0] > 0)
                        problems.add("the Seraph Archer was on the ground in " + grounded[0] + " of " + seen[0] + " looks; it should fly");
                    else if (height[0] < SeraphArcherUnit.FLIGHT_HEIGHT - 1 || height[1] > SeraphArcherUnit.FLIGHT_HEIGHT + 1)
                        problems.add("the Seraph Archer flew " + String.format("%.1f", height[0]) + " to " + String.format("%.1f", height[1])
                            + " blocks up; lovish: it flies low, about " + SeraphArcherUnit.FLIGHT_HEIGHT + " blocks up");
                    if (hits[0] < 2)
                        problems.add("the enemy 9 blocks off was hurt in " + hits[0] + " looks while the Seraph Archer lived; it should shoot it");
                    else if (nearestHit[0] < 4.0)
                        problems.add("the Seraph Archer was " + String.format("%.1f", nearestHit[0]) + " blocks from the enemy at a hit: it should shoot from range");
                    if (!aliveLate[0])
                        problems.add("the Seraph Archer did not last its " + CallAngel.Kind.ARCHER.lifeSeconds + " s");
                    if (!goneAfter[0])
                        problems.add("the Seraph Archer was still there 1 s after its " + CallAngel.Kind.ARCHER.lifeSeconds + " s");
                    if (last[0] != null && !wispsNear(last[0], 3).isEmpty())
                        problems.add("the faded Seraph Archer left a wisp");
                }
            } finally {
                cleanUp(caster, units, last[0] == null ? List.of() : List.of(last[0]));
            }
            finish(helper, problems);
        });
    }

    /**
     * lovish (revision answers, Oct 29): the Seraph Archer "flies low (3 blocks up) so melee and ground towers can hit
     * it". A melee enemy (a Vindicator, AI on) beside a Seraph Archer hovering at its flight height - nothing else of
     * the caster's side near it - takes it on and hurts it.
     */
    @GameTest(template = ARENA, timeoutTicks = 280, batch = "angel_caller_archer_melee")
    public static void angel_caller_seraph_archer_flies_low_enough_for_melee_to_hit_it(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "archermelee"), foeOwner = owner(helper, "archermeleefoe");
        Mob caster = caller(helper, owner, 3, 3, CallAngel.Kind.ARCHER.essence);
        List<Mob> units = new ArrayList<>();
        if (caster == null) {
            helper.fail("could not spawn the caster");
            return;
        }
        int cast = 2;
        AngelUnit[] angel = { null };
        Mob[] melee = { null };
        Vec3[] last = { null };
        float[] archerHp = { 0 };
        int[] meleeHits = { 0 };
        helper.runAfterDelay(cast, () -> {
            click(level, caster, CallAngel.Kind.ARCHER);
            List<AngelUnit> as = angels(caster, CallAngel.Kind.ARCHER);
            angel[0] = as.isEmpty() ? null : as.get(0);
            if (angel[0] != null)
                archerHp[0] = angel[0].getHealth();
        });
        // the caster goes (the archer stays: it is bound to nothing but its time) so the Vindicator has one target
        helper.runAfterDelay(cast + 2, () -> {
            if (angel[0] != null)
                angel[0].setPos(caster.getX(), caster.getY() + SeraphArcherUnit.FLIGHT_HEIGHT, caster.getZ());
            caster.discard();
            Mob v = HeroGameTests.spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), foeOwner, 3, 8);
            if (v != null) {
                AutocastState.setAll((Unit) v, false);
                units.add(v);
                melee[0] = v;
            }
        });
        for (int t = cast + 5; t <= 250; t += 5) {
            helper.runAfterDelay(t, () -> {
                AngelUnit a = angel[0];
                Mob v = melee[0];
                if (a == null || v == null)
                    return;
                if (a.isAlive() && !a.isRemoved())
                    last[0] = a.position();
                if (a.getHealth() < archerHp[0] && a.getLastHurtByMob() == v)
                    meleeHits[0]++;
                archerHp[0] = a.getHealth();
            });
        }
        helper.runAfterDelay(255, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (angel[0] == null)
                    problems.add("the Archer call made no Seraph Archer");
                else if (melee[0] == null)
                    problems.add("could not spawn the Vindicator");
                else if (meleeHits[0] == 0)
                    problems.add("a Vindicator 5 blocks from a Seraph Archer hovering " + SeraphArcherUnit.FLIGHT_HEIGHT
                        + " blocks up never hurt it in 12 s; lovish: melee can hit it");
            } finally {
                cleanUp(null, units, last[0] == null ? List.of() : List.of(last[0]));
                if (angel[0] != null && !angel[0].isRemoved())
                    angel[0].discard();
                if (!caster.isRemoved())
                    caster.discard();
            }
            finish(helper, problems);
        });
    }

    /**
     * lovish (revision answers, Oct 29): the low Seraph Archer can be hit by "melee units and ground towers". The
     * Sunforged anti-raid tower and its enhanced twin (ground-only: they never shoot a flyer) shoot an enemy Seraph
     * Archer hovering at its flight height 4 blocks off - and still ignore a Gyrocopter Gunship (an ordinary flyer) 7
     * blocks off on the other side (farther, so the towers that rank by distance never tie it with the archer); the Sky
     * Watch (anti-air) still shoots the archer too. Weapons ticked synchronously, as AntiAirGameTests.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "angel_caller_towers")
    public static void angel_caller_seraph_archer_is_shot_by_ground_raid_towers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "towers"), foe = owner(helper, "towersfoe");
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        int gy = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
        List<Mob> spawned = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        try {
            var towers = List.of(com.solegendary.reignofnether.building.buildings.defence.Defences.VILLAGERS.raidTower(),
                com.solegendary.reignofnether.building.buildings.defence.Defences.VILLAGERS_TOWERS.raidTowerHeavy(),
                com.solegendary.reignofnether.building.buildings.defence.Defences.VILLAGERS_TOWERS.skyWatch());
            for (var tower : towers) {
                var p = AntiAirGameTests.place(helper, tower, owner, foe);
                if (p == null) {
                    problems.add(tower.name + " is not a DefenceTowerPlacement");
                    continue;
                }
                double cx = p.centrePos.getX() + 0.5, cz = p.centrePos.getZ() + 0.5;
                Mob archer = AntiAirGameTests.spawn(level, EntityRegistrar.SERAPH_ARCHER_UNIT.get(), cx + 4, gy + SeraphArcherUnit.FLIGHT_HEIGHT, cz, foe, spawned);
                Mob high = AntiAirGameTests.spawn(level, EntityRegistrar.GYROCOPTER_GUNSHIP_UNIT.get(), cx - 7, gy + 6, cz, foe, spawned);
                if (archer == null || high == null || !((Unit) archer).isFlyingUnit() || !((Unit) high).isFlyingUnit()) {
                    problems.add("could not make a flying Seraph Archer and a Gyrocopter Gunship");
                    break;
                }
                com.solegendary.reignofnether.unit.UnitGrid.invalidate();
                float archerHp = archer.getHealth(), highHp = high.getHealth();
                AntiAirGameTests.tickUntil(level, p, List.of(archer, high), 60, () -> archer.getHealth() < archerHp);
                if (archer.getHealth() >= archerHp)
                    problems.add(tower.name + " (" + tower.kind + ") never shot a Seraph Archer hovering "
                        + SeraphArcherUnit.FLIGHT_HEIGHT + " blocks up 4 blocks off");
                boolean groundOnly = !tower.kind.hitsAir;
                if (groundOnly && high.getHealth() < highHp)
                    problems.add(tower.name + " (ground-only) shot an ordinary flyer");
                archer.discard();
                high.discard();
            }
        } finally {
            for (Mob m : spawned)
                m.discard();
        }
        finish(helper, problems);
    }

    /**
     * The Sword Seraph flies (hovers above the ground while no enemy is near), then dives onto an enemy that appears 7
     * blocks off and hits it in melee, lives its 30 s and fades without a wisp.
     */
    @GameTest(template = ARENA, timeoutTicks = 680, batch = "angel_caller_sword")
    public static void angel_caller_sword_seraph_flies_dives_into_melee_and_fades_after_30s(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "sword"), foeOwner = owner(helper, "swordfoe");
        Mob caster = caller(helper, owner, 3, 3, CallAngel.Kind.SWORD.essence);
        List<Mob> units = new ArrayList<>();
        if (caster == null) {
            helper.fail("could not spawn the caster");
            return;
        }
        double groundY = caster.getY();
        int cast = 2, foeAt = 30, life = CallAngel.Kind.SWORD.lifeSeconds * 20;
        AngelUnit[] angel = { null };
        Mob[] foe = { null };
        Vec3[] last = { null };
        float[] foeHp = { 0 };
        double[] nearestHit = { Double.MAX_VALUE };
        int[] groundedEarly = { 0 }, seenEarly = { 0 }, hits = { 0 };
        boolean[] aliveLate = { false }, goneAfter = { false };
        helper.runAfterDelay(cast, () -> {
            click(level, caster, CallAngel.Kind.SWORD);
            List<AngelUnit> as = angels(caster, CallAngel.Kind.SWORD);
            angel[0] = as.isEmpty() ? null : as.get(0);
        });
        helper.runAfterDelay(foeAt, () -> {
            foe[0] = still(helper, EntityRegistrar.IRON_GOLEM_UNIT.get(), foeOwner, 3, 10);
            if (foe[0] != null) {
                units.add(foe[0]);
                foeHp[0] = foe[0].getHealth();
            }
        });
        for (int t = cast + 5; t <= cast + life + 30; t += 5) {
            int tick = t;
            helper.runAfterDelay(t, () -> {
                AngelUnit a = angel[0];
                if (a == null)
                    return;
                boolean alive = a.isAlive() && !a.isRemoved();
                if (alive) {
                    last[0] = a.position();
                    if (tick < foeAt) {
                        seenEarly[0]++;
                        if (!airborne(a, groundY))
                            groundedEarly[0]++;
                    }
                    Mob f = foe[0];
                    if (f != null && f.isAlive() && f.getHealth() < foeHp[0]) {
                        hits[0]++;
                        nearestHit[0] = Math.min(nearestHit[0], a.distanceTo(f));
                    }
                }
                if (foe[0] != null)
                    foeHp[0] = foe[0].getHealth();
                if (tick >= cast + life - 20 && tick < cast + life - 10 && alive)
                    aliveLate[0] = true;
                if (tick >= cast + life + 20 && !alive)
                    goneAfter[0] = true;
            });
        }
        helper.runAfterDelay(cast + life + 40, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (angel[0] == null)
                    problems.add("the Sword call made no Sword Seraph");
                else {
                    if (seenEarly[0] == 0 || groundedEarly[0] > 0)
                        problems.add("before any enemy came the Sword Seraph was on the ground in " + groundedEarly[0] + " of "
                            + seenEarly[0] + " looks; it should fly");
                    if (foe[0] == null)
                        problems.add("could not spawn the enemy");
                    else if (hits[0] < 2)
                        problems.add("the enemy 7 blocks off was hurt in " + hits[0] + " looks; the Sword Seraph should dive on it and fight");
                    else if (nearestHit[0] > 3.5)
                        problems.add("the Sword Seraph was never closer than " + String.format("%.1f", nearestHit[0])
                            + " blocks to the enemy when it was hurt: a melee engager should hit it up close");
                    if (!aliveLate[0])
                        problems.add("the Sword Seraph did not last its " + CallAngel.Kind.SWORD.lifeSeconds + " s");
                    if (!goneAfter[0])
                        problems.add("the Sword Seraph was still there 1 s after its " + CallAngel.Kind.SWORD.lifeSeconds + " s");
                    if (last[0] != null && !wispsNear(last[0], 3).isEmpty())
                        problems.add("the faded Sword Seraph left a wisp");
                }
            } finally {
                cleanUp(caster, units, last[0] == null ? List.of() : List.of(last[0]));
            }
            finish(helper, problems);
        });
    }
}
