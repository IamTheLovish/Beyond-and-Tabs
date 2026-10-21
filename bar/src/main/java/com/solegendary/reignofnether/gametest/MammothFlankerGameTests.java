package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.abilities.Trample;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.defence.DefenceTower;
import com.solegendary.reignofnether.building.buildings.defence.Defences;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.piglins.WarMammothUnit;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The War Mammoth as the Horde's flanker (project doc claude/t3-roles.md; unit/units/piglins/WarMammothUnit): the
 * charge itself, then the two strategy scenarios the role is proven by - a flank round a thin front that razes the
 * economy behind it, and the same mammoth into a wall line with anti-T3 emplacements behind it, where it loses.
 * Own holder: the CI guard keeps new tests out of SkirmishGameTests. The scenarios run on their own force-loaded pads
 * far south of the test grid (the mammoth needs forty blocks of run-up), with the game's default pathfinding as it is
 * on this server.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class MammothFlankerGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;
    static final int PAD_Y = 200;          // the floor; feet at PAD_Y + 1
    static final int PAD_REACH = 24;       // chunks held round a pad

    static String owner(GameTestHelper helper, String tag) {
        return "gametest_" + tag + "_" + helper.absolutePos(BlockPos.ZERO).asLong();
    }

    /**
     * Trample: the wind-up hurts and moves nobody while the lane shows; the run hits every enemy on the lane once (as a
     * blow of its own: the damage lands) and flings it sideways, never a friend on the lane or an enemy beside it, and
     * carries the mammoth down the lane; a wall stops a second charge short (no building is broken). The ability
     * starts it and spends its cooldown.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void trample_runs_the_lane_flings_what_it_hits_and_stops_at_a_wall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "trample"), foeOwner = owner + "_foe";
        List<String> problems = new ArrayList<>();
        SkirmishGameTests.openGround(helper, 0, 0, 15, 15);
        var vill = EntityRegistrar.VILLAGER_UNIT.get();
        var mammoth = (WarMammothUnit) BigUnitGameTests.spawn(helper, EntityRegistrar.WAR_MAMMOTH_UNIT.get(), owner, 2.5, 8.5);
        Mob foeA = BigUnitGameTests.spawn(helper, vill, foeOwner, 7.5, 8.5), foeB = BigUnitGameTests.spawn(helper, vill, foeOwner, 10.5, 9.5);
        Mob friend = BigUnitGameTests.spawn(helper, vill, owner, 9.0, 8.5), off = BigUnitGameTests.spawn(helper, vill, foeOwner, 8.5, 14.5);
        List<Mob> all = new ArrayList<>();
        for (Mob m : new Mob[] { mammoth, foeA, foeB, friend, off })
            if (m != null)
                all.add(m);
        try {
            if (all.size() != 5) {
                helper.fail("could not create the Trample test units");
                return;
            }
            float a0 = foeA.getHealth(), b0 = foeB.getHealth(), f0 = friend.getHealth(), o0 = off.getHealth();
            double x0 = mammoth.getX();
            BlockPos aim = helper.absolutePos(new BlockPos(14, 2, 8));
            Trample trample = null;
            for (var a : mammoth.getAbilities().get())
                if (a instanceof Trample t)
                    trample = t;
            if (trample == null) {
                helper.fail("the War Mammoth has no Trample");
                return;
            }
            if (trample.range < WarMammothUnit.CHARGE_LENGTH - 0.01f || !trample.canTargetEntities)
                problems.add("Trample should be aimed up to " + WarMammothUnit.CHARGE_LENGTH + " blocks at a spot or an enemy");
            trample.use(level, mammoth, aim);
            if (!mammoth.isCharging())
                problems.add("the ability did not start the charge");
            if (mammoth.getCooldown(trample) <= 0)
                problems.add("the charge did not spend Trample's cooldown");
            if (mammoth.startCharge(Vec3.atBottomCenterOf(aim)))
                problems.add("a second charge started while charging");
            for (int t = 0; t < WarMammothUnit.CHARGE_WINDUP_TICKS; t++)
                mammoth.tickCharge(level);
            if (foeA.getHealth() < a0 || mammoth.getX() - x0 > 0.5)
                problems.add("the mammoth moved or hit during its wind-up");
            for (int t = 0; t < 40 && mammoth.isCharging(); t++)
                mammoth.tickCharge(level);
            if (foeA.getHealth() >= a0 || foeB.getHealth() >= b0)
                problems.add("the charge should hit both enemies on the lane (hits " + mammoth.lastChargeHits + ", "
                    + a0 + "->" + foeA.getHealth() + ", " + b0 + "->" + foeB.getHealth() + ")");
            if (foeA.isAlive() && a0 - foeA.getHealth() < WarMammothUnit.CHARGE_DAMAGE * 0.5f)
                problems.add("the charge's blow should land as its own " + WarMammothUnit.CHARGE_DAMAGE + " (took " + (a0 - foeA.getHealth()) + ")");
            if (Math.abs(foeA.getDeltaMovement().z) < 0.3 && Math.abs(foeB.getDeltaMovement().z) < 0.3)
                problems.add("the enemies run through should be flung sideways (velocities " + foeA.getDeltaMovement() + ", " + foeB.getDeltaMovement() + ")");
            if (friend.getHealth() < f0)
                problems.add("the charge hit a friend on the lane");
            if (off.getHealth() < o0)
                problems.add("the charge hit an enemy beside the lane");
            // the lane runs off the arena's east edge into whatever terrain is there, so only the run itself is checked
            if (mammoth.getX() - x0 < 8)
                problems.add("the mammoth should have run down the lane (moved " + (mammoth.getX() - x0) + ")");
            // a wall: a second mammoth's charge stops at it and breaks nothing
            for (int y = 2; y <= 5; y++)
                for (int z = 4; z <= 13; z++)
                    level.setBlock(helper.absolutePos(new BlockPos(9, y, z)), Blocks.STONE.defaultBlockState(), 3);
            var second = (WarMammothUnit) BigUnitGameTests.spawn(helper, EntityRegistrar.WAR_MAMMOTH_UNIT.get(), owner, 3.5, 8.5);
            if (second == null) {
                helper.fail("could not create the second mammoth");
                return;
            }
            all.add(second);
            second.startCharge(Vec3.atBottomCenterOf(aim));
            for (int t = 0; t < 40 && second.isCharging(); t++)
                second.tickCharge(level);
            double wallX = helper.absolutePos(new BlockPos(9, 2, 8)).getX();
            if (second.getX() + second.getBbWidth() * 0.5 > wallX + 0.05)
                problems.add("the wall should stop the charge (mammoth at " + second.getX() + ", wall at " + wallX + ")");
            if (second.isCharging() || !second.lastChargeCrashed)
                problems.add("a charge that hit a wall should end as a crash");
            if (!level.getBlockState(helper.absolutePos(new BlockPos(9, 2, 8))).is(Blocks.STONE))
                problems.add("the charge broke the wall");
            for (int y = 2; y <= 5; y++)
                for (int z = 4; z <= 13; z++)
                    level.setBlock(helper.absolutePos(new BlockPos(9, y, z)), Blocks.AIR.defaultBlockState(), 3);
        } finally {
            for (Mob m : all)
                m.discard();
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    // ---- the strategy scenarios ------------------------------------------------------------------------------------

    /** A floating stone pad: floor at PAD_Y, air to PAD_Y + 8. Its chunks must be held already. */
    static void pad(ServerLevel level, int ox, int oz, int w, int d) {
        var stone = Blocks.STONE.defaultBlockState();
        var air = Blocks.AIR.defaultBlockState();
        for (int x = ox - 1; x <= ox + w; x++)
            for (int z = oz - 1; z <= oz + d; z++) {
                boolean floor = x >= ox && x < ox + w && z >= oz && z < oz + d;
                level.setBlock(new BlockPos(x, PAD_Y, z), floor ? stone : air, 2);
                level.setBlock(new BlockPos(x, PAD_Y - 1, z), floor ? stone : air, 2);
                for (int dy = 1; dy <= 8; dy++)
                    level.setBlock(new BlockPos(x, PAD_Y + dy, z), air, 2);
            }
    }

    /** The chunks of a pad plus PAD_REACH round it, as {cx, cz} pairs for GameTestChunks. */
    static List<long[]> padChunks(int ox, int oz, int w, int d) {
        List<long[]> chunks = new ArrayList<>();
        for (int cx = (ox - PAD_REACH) >> 4; cx <= (ox + w + PAD_REACH) >> 4; cx++)
            for (int cz = (oz - PAD_REACH) >> 4; cz <= (oz + d + PAD_REACH) >> 4; cz++)
                chunks.add(new long[] { cx, cz });
        return chunks;
    }

    static Mob spawnAt(ServerLevel level, net.minecraft.world.entity.EntityType<? extends Mob> type, String owner, double x, double z) {
        Mob m = type.create(level);
        if (m == null)
            return null;
        m.moveTo(x, PAD_Y + 1, z, 0, 0);
        if (m instanceof Unit u)
            u.setOwnerName(owner);
        level.addFreshEntity(m);
        return m;
    }

    /** A finished, registered building whose minCorner sits at (x, PAD_Y, z) - the structure's own origin offset undone. */
    static BuildingPlacement place(ServerLevel level, Building b, int x, int z, String owner) {
        BuildingPlacement probe = BuildingUtils.getNewBuildingPlacement(b, level, new BlockPos(x, PAD_Y, z), Rotation.NONE, owner, false);
        if (probe == null)
            return null;
        BlockPos origin = new BlockPos(x - (probe.minCorner.getX() - x), PAD_Y - (probe.minCorner.getY() - PAD_Y), z - (probe.minCorner.getZ() - z));
        return RegressionGameTests.stamp(level, b, origin, owner);
    }

    static boolean destroyed(BuildingPlacement bp) {
        return bp.isDestroyedServerside || !BuildingServerEvents.getBuildings().contains(bp) || bp.getBlocksPlaced() <= 0;
    }

    static void cleanUp(ServerLevel level, List<Mob> mobs, List<BuildingPlacement> buildings, List<long[]> chunks) {
        for (Mob m : mobs) {
            UnitServerEvents.clearQueuedActions(m.getId());
            m.discard();
        }
        for (BuildingPlacement bp : buildings)
            RegressionGameTests.clear(level, bp);
        GameTestChunks.releaseAll(level, chunks);
    }

    /**
     * The flank: a thin Horde front (four Brutes) across the middle of a pad, two wind mills behind it. A War Mammoth
     * ordered onto the far mill runs past the front - its right of way and plough carry it through the Brutes, its
     * pace leaves them behind - and razes the mill, alive, before the front can do much about it; the front is not
     * what it went for, so most of the Brutes still stand. This is the strategy the flanker opens; a T3 that crawled
     * at 0.17 and stopped for every bite never got there (lovish Oct 21).
     */
    @GameTest(template = ARENA, timeoutTicks = 2400)
    public static void mammoth_flank_razes_the_economy_behind_a_thin_front(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "flank"), foeOwner = owner + "_foe";
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int w = 60, d = 28;
        int ox = ((base.getX() >> 4) << 4) + 8, oz = (((base.getZ() + 33000) >> 4) << 4) + 8;
        List<long[]> chunks = padChunks(ox, oz, w, d);
        GameTestChunks.holdAndAwaitOrFail(level, chunks, "flank pad");
        pad(level, ox, oz, w, d);
        List<Mob> mobs = new ArrayList<>();
        List<BuildingPlacement> buildings = new ArrayList<>();
        BuildingPlacement nearMill = place(level, Buildings.WIND_GENERATOR_PIGLINS, ox + 40, oz + 11, foeOwner);
        BuildingPlacement farMill = place(level, Buildings.WIND_GENERATOR_PIGLINS, ox + 50, oz + 11, foeOwner);
        if (nearMill == null || farMill == null) {
            cleanUp(level, mobs, buildings, chunks);
            helper.fail("could not place the wind mills");
            return;
        }
        buildings.add(nearMill);
        buildings.add(farMill);
        int mid = oz + d / 2;
        // one tick callback with phases, registered here: a callback that registers another from inside a test tick
        // modifies the test's tick map while it is being walked
        Mob[] mammoth = { null };
        List<Mob> front = new ArrayList<>();
        float[] hp0 = { 0 };
        int[] t = { 0 }, orderedAt = { -1 };
        boolean[] done = { false };
        helper.onEachTick(() -> {
            if (done[0])
                return;
            t[0]++;
            if (mammoth[0] == null) {
                if (!padReady(level, chunks, t[0], 800, "flank pad"))
                    return;
                mammoth[0] = spawnAt(level, EntityRegistrar.WAR_MAMMOTH_UNIT.get(), owner, ox + 4.5, mid + 0.5);
                for (int i = 0; i < 4; i++) {
                    Mob b = spawnAt(level, EntityRegistrar.BRUTE_UNIT.get(), foeOwner, ox + 22.5, mid - 4.5 + i * 3);
                    if (b != null) {
                        front.add(b);
                        mobs.add(b);
                    }
                }
                if (mammoth[0] == null || front.size() != 4) {
                    done[0] = true;
                    cleanUp(level, mobs, buildings, chunks);
                    helper.fail("could not create the flank units");
                    return;
                }
                mobs.add(mammoth[0]);
                hp0[0] = mammoth[0].getHealth();
                orderedAt[0] = t[0] + 20;
                return;
            }
            Mob m = mammoth[0];
            if (t[0] < orderedAt[0])
                return;
            if (t[0] == orderedAt[0]) {
                UnitServerEvents.addActionItem(owner, UnitAction.ATTACK_BUILDING, -1, new int[] { m.getId() },
                    farMill.centrePos, BlockPos.ZERO);
                return;
            }
            boolean razed = destroyed(farMill);
            if (!razed && t[0] - orderedAt[0] < 1400 && m.isAlive())
                return;
            done[0] = true;
            List<String> problems = new ArrayList<>();
            int standing = 0;
            for (Mob b : front)
                if (b.isAlive())
                    standing++;
            var goal = ((com.solegendary.reignofnether.unit.interfaces.AttackerUnit) m).getAttackBuildingGoal();
            boolean holdsTarget = goal instanceof com.solegendary.reignofnether.unit.goals.MeleeAttackBuildingGoal mg && mg.getBuildingTarget() != null;
            String state = String.format(Locale.ROOT, "%d ticks after the order: mammoth at pad %.1f/%.1f (mill at %d/%d), %.0f/%.0f HP, alive %b, holds its building target %b, nav done %b; far mill %d/%d HP destroyed %b; near mill destroyed %b; %d of 4 Brutes standing",
                t[0] - orderedAt[0], m.getX() - ox, m.getZ() - oz, farMill.centrePos.getX() - ox, farMill.centrePos.getZ() - oz, m.getHealth(), hp0[0], m.isAlive(),
                holdsTarget, m.getNavigation().isDone(), farMill.getHealth(), farMill.getMaxHealth(), razed, destroyed(nearMill), standing);
            ReignOfNether.LOGGER.info("[Mammoth flank] {}", state);
            if (!razed)
                problems.add("the mammoth did not raze the mill behind the front (" + state + ")");
            if (!m.isAlive())
                problems.add("the mammoth died on the flank (" + state + ")");
            else if (m.getHealth() < hp0[0] * 0.5f)
                problems.add("the flank cost the mammoth more than half its health (" + state + ")");
            if (standing < 2)
                problems.add("the front should mostly still stand - the mammoth went past it, not through it (" + state + ")");
            cleanUp(level, mobs, buildings, chunks);
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }

    /**
     * The counter: a line of fortified walls across a pad with three anti-T3 emplacements behind it and the mill
     * behind those, worth less than the mammoth. The mammoth ordered to break the wall in front of it dies to the
     * emplacements before it is through, and the mill never takes a hit - a wall line with towers at home is what the
     * enemy builds when the flanker arrives, and it beats a mammoth that charges it alone.
     */
    @GameTest(template = ARENA, timeoutTicks = 2000)
    public static void mammoth_into_a_wall_line_with_anti_t3_towers_loses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = owner(helper, "wallline"), foeOwner = owner + "_foe";
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int w = 48, d = 24, wallX = 20;
        int ox = ((base.getX() >> 4) << 4) + 8, oz = (((base.getZ() + 34000) >> 4) << 4) + 8;
        List<long[]> chunks = padChunks(ox, oz, w, d);
        GameTestChunks.holdAndAwaitOrFail(level, chunks, "wall line pad");
        pad(level, ox, oz, w, d);
        List<Mob> mobs = new ArrayList<>();
        List<BuildingPlacement> buildings = new ArrayList<>();
        Defences.Set set = Defences.PIGLINS;
        Building wall = Defences.PIGLINS_FORT.fortifiedWall();
        BuildingPlacement first = place(level, wall, ox + wallX, oz, foeOwner);
        if (first == null) {
            cleanUp(level, mobs, buildings, chunks);
            helper.fail("could not place the wall");
            return;
        }
        buildings.add(first);
        int depth = first.maxCorner.getZ() - first.minCorner.getZ() + 1;
        int defenceMetal = wall.cost.metal();
        for (int z = oz + depth; z < oz + d; z += depth) {
            BuildingPlacement seg = place(level, wall, ox + wallX, z, foeOwner);
            if (seg == null) {
                cleanUp(level, mobs, buildings, chunks);
                helper.fail("could not place a wall segment at row " + (z - oz));
                return;
            }
            buildings.add(seg);
            defenceMetal += wall.cost.metal();
        }
        // no seam: every row of the pad is walled at the wall's columns
        for (int z = oz; z < oz + d; z++)
            if (level.getBlockState(new BlockPos(ox + wallX, PAD_Y + 1, z)).isAir()
                    && level.getBlockState(new BlockPos(ox + wallX + 1, PAD_Y + 1, z)).isAir()) {
                cleanUp(level, mobs, buildings, chunks);
                helper.fail("the wall line has a seam at row " + (z - oz));
                return;
            }
        ResearchServerEvents.addResearch(foeOwner, ProductionItems.RESEARCH_TIER_3);
        List<DefenceTower.DefenceTowerPlacement> towers = new ArrayList<>();
        for (int z : new int[] { oz + 1, oz + 9, oz + 17 }) {
            BuildingPlacement bp = place(level, set.antiT3(), ox + wallX + 5, z, foeOwner);
            if (!(bp instanceof DefenceTower.DefenceTowerPlacement tower)) {
                cleanUp(level, mobs, buildings, chunks);
                helper.fail("the anti-T3 emplacement did not place: " + bp);
                return;
            }
            tower.onlyFoe = owner;
            towers.add(tower);
            buildings.add(tower);
            defenceMetal += set.antiT3().cost.metal();
        }
        BuildingPlacement mill = place(level, Buildings.WIND_GENERATOR_PIGLINS, ox + wallX + 14, oz + 9, foeOwner);
        if (mill == null) {
            cleanUp(level, mobs, buildings, chunks);
            helper.fail("could not place the mill");
            return;
        }
        buildings.add(mill);
        int mammothMetal = ResourceCosts.WAR_MAMMOTH.metal();
        final int defenceCost = defenceMetal;
        BuildingPlacement target = buildings.get(Math.min(buildings.size() - 1, d / 2 / depth));   // the segment in front of the mammoth
        int millHp0 = mill.getHealth();
        Mob[] mammoth = { null };
        int[] t = { 0 }, orderedAt = { -1 };
        boolean[] done = { false };
        helper.onEachTick(() -> {
            if (done[0])
                return;
            t[0]++;
            if (mammoth[0] == null) {
                if (!padReady(level, chunks, t[0], 800, "wall line pad"))
                    return;
                mammoth[0] = spawnAt(level, EntityRegistrar.WAR_MAMMOTH_UNIT.get(), owner, ox + 4.5, oz + d / 2.0);
                if (mammoth[0] == null) {
                    done[0] = true;
                    cleanUp(level, mobs, buildings, chunks);
                    helper.fail("could not create the mammoth");
                    return;
                }
                mobs.add(mammoth[0]);
                orderedAt[0] = t[0] + 20;
                return;
            }
            Mob m = mammoth[0];
            if (t[0] < orderedAt[0])
                return;
            if (t[0] == orderedAt[0]) {
                UnitServerEvents.addActionItem(owner, UnitAction.ATTACK_BUILDING, -1, new int[] { m.getId() },
                    target.centrePos, BlockPos.ZERO);
                return;
            }
            boolean dead = !m.isAlive();
            if (!dead && t[0] - orderedAt[0] < 1100)
                return;
            done[0] = true;
            List<String> problems = new ArrayList<>();
            int wallsLeft = 0;
            for (BuildingPlacement bp : buildings)
                if (bp.getBuilding() == wall && !destroyed(bp))
                    wallsLeft++;
            String state = String.format(Locale.ROOT, "%d ticks after the order: mammoth %.0f HP alive %b at pad x %.1f (wall at %d), %d wall segments left, mill %d/%d HP, defences %d metal vs the mammoth's %d",
                t[0] - orderedAt[0], m.getHealth(), m.isAlive(), m.getX() - ox, wallX, wallsLeft, mill.getHealth(), millHp0, defenceCost, mammothMetal);
            ReignOfNether.LOGGER.info("[Mammoth wall line] {}", state);
            if (!dead)
                problems.add("the mammoth should have died to the wall line's emplacements (" + state + ")");
            if (destroyed(mill) || mill.getHealth() < millHp0)
                problems.add("the mill behind the wall line was hit (" + state + ")");
            if (m.getX() - ox > wallX + 2)
                problems.add("the mammoth got past the wall line (" + state + ")");
            if (defenceCost >= mammothMetal)
                problems.add("the wall line should cost less than the mammoth it stops (" + state + ")");
            cleanUp(level, mobs, buildings, chunks);
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }

    /**
     * Whether every held chunk of a pad is entity-ticking (its units tick and show up in queries) - the check
     * GameTestChunks.whenEntitiesReady makes, inline so a test's single tick callback can wait on it; past
     * {@code capTicks} it fails the test with where the pad stood.
     */
    static boolean padReady(ServerLevel level, List<long[]> chunks, int tick, int capTicks, String what) {
        int notReady = 0;
        long[] first = null;
        for (long[] c : chunks) {
            int cx = (int) c[0], cz = (int) c[1];
            if (!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(cx, cz))
                    || !level.isPositionEntityTicking(new BlockPos((cx << 4) + 8, PAD_Y, (cz << 4) + 8))) {
                notReady++;
                if (first == null)
                    first = c;
            }
        }
        if (notReady == 0)
            return true;
        if (tick >= capTicks)
            throw new net.minecraft.gametest.framework.GameTestAssertException(notReady + " of " + chunks.size() + " " + what
                + " chunks never had their entities loaded (first " + first[0] + "," + first[1] + ") in " + tick + " ticks");
        return false;
    }
}
