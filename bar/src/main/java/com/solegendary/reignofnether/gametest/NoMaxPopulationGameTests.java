package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.abilities.RaiseDead;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.resources.WreckServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.ZombieUnit;

import com.mojang.serialization.Dynamic;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * lovish, Oct 29: "there should be no population in this game" - "like no max population" - "beyond all reason
 * doesnt use supply or unit cap". Production is never held back by population: there is no cap to set (the old
 * gamerule {@code maxPopulation} is gone, and an old save's value is ignored) and no building provides supply. A
 * unit's population is only an internal size weight now, never shown or paid. Written before the code.
 *
 * The one limit that stays is a summon's own: at most 8 Lich Regent ghouls alive per player (lovish, Oct 29).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class NoMaxPopulationGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    /** Whether a gamerule of this name is registered at all. */
    private static boolean gameruleExists(String name) {
        boolean[] found = {false};
        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public <T extends GameRules.Value<T>> void visit(GameRules.Key<T> key, GameRules.Type<T> type) {
                if (key.getId().equals(name))
                    found[0] = true;
            }
        });
        return found[0];
    }

    /**
     * Production with resources is never refused, and an old save's cap is ignored. A level.dat that stored
     * {@code maxPopulation 0} (no room at all under the old rule) loads without the rule; then an ordinary lab - a
     * Villager Barracks, not a graveyard (GraveyardGameTests covers those) - takes all twelve Vindicators and every one
     * walks out. On main the rule loads back and the gamerule still exists. Runs in one tick (the queue is ticked by
     * hand), with the barracks gone whatever happens.
     */
    @GameTest(template = ARENA)
    public static void no_max_population_production_goes_past_an_old_cap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        if (gameruleExists("maxPopulation"))
            problems.add("the gamerule maxPopulation still exists (lovish: no max population)");
        CompoundTag oldSave = new CompoundTag();
        oldSave.putString("maxPopulation", "0");
        try {
            GameRules loaded = new GameRules(new Dynamic<>(NbtOps.INSTANCE, oldSave));
            if (loaded.createTag().contains("maxPopulation"))
                problems.add("an old save's maxPopulation 0 loaded back as a rule");
        } catch (RuntimeException ex) {
            problems.add("an old save's maxPopulation threw on load: " + ex);
        }
        String owner = "gametest_nomaxpop_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Resources pool = new Resources(owner, 0, 100000, 100000);
        ResourcesServerEvents.resourcesList.add(pool);
        ProductionPlacement lab = null;
        try {
            BuildingPlacement bp = BuildingUtils.getNewBuildingPlacement(Buildings.BARRACKS, level,
                helper.absolutePos(new BlockPos(4, 1, 4)), Rotation.NONE, owner, false);
            if (!(bp instanceof ProductionPlacement pp)) {
                helper.fail("setup: the Barracks placement is " + bp);
                return;
            }
            lab = pp;
            for (int x = lab.minCorner.getX() - 1; x <= lab.maxCorner.getX() + 1; x++)
                for (int z = lab.minCorner.getZ() - 1; z <= lab.maxCorner.getZ() + 1; z++)
                    level.setBlock(new BlockPos(x, lab.minCorner.getY() - 1, z), Blocks.STONE.defaultBlockState(), 2);
            for (var blk : lab.getBlocks())
                level.setBlock(blk.getBlockPos(), blk.getBlockState(), 2);
            lab.initPlacedBlocks();
            lab.isBuilt = true;
            BuildingServerEvents.getBuildings().add(lab);
            int accepted = 0;
            for (int i = 0; i < GraveyardGameTests.QUEUED; i++)
                if (lab.startProductionItem(ProductionItems.VINDICATOR))
                    accepted++;
            int ticks = 0;
            while (!lab.productionQueue.isEmpty() && ticks++ < 40000) {
                var a = lab.productionQueue.get(0);
                if (a.item.tick(lab, a))
                    lab.productionQueue.remove(0);
            }
            int out = ownUnits(level, lab, owner).size();
            if (accepted != GraveyardGameTests.QUEUED)
                problems.add("the Barracks accepted " + accepted + " of " + GraveyardGameTests.QUEUED
                    + " Vindicators with resources");
            if (!lab.productionQueue.isEmpty())
                problems.add("the queue stalled with " + lab.productionQueue.size() + " left after " + ticks + " ticks");
            if (out != accepted)
                problems.add(out + " Vindicators walked out of " + accepted + " accepted");
        } finally {
            if (lab != null) {
                lab.productionQueue.clear();
                BuildingServerEvents.getBuildings().remove(lab);
                for (LivingEntity e : ownUnits(level, lab, owner))
                    e.discard();
                for (var blk : lab.getBlocks())
                    level.setBlock(blk.getBlockPos(), Blocks.AIR.defaultBlockState(), 2);
            }
            ResourcesServerEvents.resourcesList.remove(pool);
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    private static List<LivingEntity> ownUnits(ServerLevel level, ProductionPlacement lab, String owner) {
        return new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, lab.getAABB().inflate(24),
            e -> e instanceof Unit u && owner.equals(u.getOwnerName())));
    }

    /**
     * lovish, Oct 29: "beyond all reason doesnt use supply or unit cap". No building provides population any more -
     * capitols, portals, the Sculk Catalyst and the old houses keep their other roles (energy, production) and lose
     * only the supply line. Every registered building of every faction, as costed from the config.
     */
    @GameTest(template = ARENA)
    public static void no_max_population_no_building_provides_population(GameTestHelper helper) {
        List<String> supplying = new ArrayList<>();
        int seen = 0;
        for (Building b : ReignOfNetherRegistries.BUILDING) {
            seen++;
            if (b.cost != null && b.cost.population != 0)
                supplying.add(b.name + " (" + b.cost.population + ")");
        }
        if (seen == 0)
            helper.fail("setup: no buildings registered");
        else if (!supplying.isEmpty())
            helper.fail(supplying.size() + " buildings still provide population: " + String.join(", ", supplying));
        else
            helper.succeed();
    }

    /**
     * lovish, Oct 29: at most 8 Lich Regent ghouls alive per player (no lifetime) - with population no limit at all,
     * this is their only one, and bots cast Raise Dead on cooldown. Ten wrecks of one ghoul's metal each round the
     * caster: casts raise 3, 3, 2 and then nothing; the refused cast drains no wreck, and the ability (the path the bot
     * takes too) sets no cooldown. One ghoul gone, the next cast raises exactly one. (Proven red first on PR #374.)
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void raise_dead_ghouls_cap_at_8_per_player(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_ghoulcap_" + helper.absolutePos(BlockPos.ZERO).asLong();
        Mob lich = HeroGameTests.spawnUnit(helper, EntityRegistrar.VINDICATOR_UNIT.get(), owner, 8, 8);
        if (lich == null) {
            helper.fail("could not spawn the caster");
            return;
        }
        lich.setNoAi(true);
        float each = ResourceCosts.ZOMBIE.metal();   // one ghoul empties one wreck, so no cast can come back to it
        List<Entity> wrecks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2 / 10;
            BlockPos w = helper.absolutePos(new BlockPos(8, 2, 8));
            Entity wreck = WreckServerEvents.spawnWreck(level, w.getX() + 0.5 + Math.cos(a) * 5, w.getY(),
                w.getZ() + 0.5 + Math.sin(a) * 5, each, Factions.MONSTERS);
            if (wreck != null)
                wrecks.add(wreck);
        }
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            try {
                if (RaiseDead.MAX_GHOULS != 8)
                    problems.add("lovish set 8 ghouls per player, MAX_GHOULS is " + RaiseDead.MAX_GHOULS);
                if (wrecks.size() != 10)
                    problems.add("setup: " + wrecks.size() + " of 10 wrecks spawned");
                int[] casts = new int[4];
                for (int i = 0; i < casts.length; i++)
                    casts[i] = RaiseDead.raise(level, lich, owner);
                List<ZombieUnit> ghouls = new ArrayList<>();
                for (ZombieUnit z : level.getEntitiesOfClass(ZombieUnit.class, new AABB(lich.blockPosition()).inflate(12)))
                    if (z.isAlive() && owner.equals(z.getOwnerName()))
                        ghouls.add(z);
                int total = casts[0] + casts[1] + casts[2] + casts[3];
                if (total != 8 || casts[3] != 0)
                    problems.add("casts raised " + casts[0] + ", " + casts[1] + ", " + casts[2] + ", " + casts[3]
                        + " ghouls (" + ghouls.size() + " alive), expected 3, 3, 2, 0: at most 8 per player");
                if (RaiseDead.liveGhouls(owner) != ghouls.size())
                    problems.add("liveGhouls says " + RaiseDead.liveGhouls(owner) + ", " + ghouls.size() + " stand");
                int full = 0;
                for (Entity w : wrecks)
                    if (!w.isRemoved() && WreckServerEvents.metalOf(w) >= each - 0.01f)
                        full++;
                if (full != wrecks.size() - Math.min(total, wrecks.size()))
                    problems.add(full + " wrecks are untouched after " + total + " ghouls: a refused raise drained one");
                // the ability's own path (the bot casts it on cooldown): refused at the cap, no cooldown
                RaiseDead ability = new RaiseDead();
                ability.use(level, (Unit) lich, lich.blockPosition());
                if (((Unit) lich).getCooldown(ability) > 0)
                    problems.add("Raise Dead at the cap still set its cooldown (a refused cast)");
                // one gone: room for exactly one more
                if (!ghouls.isEmpty()) {
                    ghouls.get(0).discard();
                    int again = RaiseDead.raise(level, lich, owner);
                    if (again != 1)
                        problems.add("with one ghoul gone the next cast raised " + again + ", expected 1");
                }
            } finally {
                AABB box = new AABB(helper.absolutePos(new BlockPos(-4, -2, -4)), helper.absolutePos(new BlockPos(20, 20, 20)));
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box))
                    if (e instanceof Unit u && owner.equals(u.getOwnerName()))
                        e.discard();
                for (Entity w : wrecks)
                    w.discard();
            }
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }
}
