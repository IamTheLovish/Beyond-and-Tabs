package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlockData;
import com.solegendary.reignofnether.building.BuildingValidators;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.shared.AbstractBridge;
import com.solegendary.reignofnether.faction.FactionTraits;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.matchstart.SkirmishServerboundPacket;
import com.solegendary.reignofnether.player.PlayerPalette;
import com.solegendary.reignofnether.resources.MetalPatches;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Headless smoke tests that CI runs on a real dedicated server ({@code ./gradlew runGameTestServer}). They cover the
 * class of bug that only shows up in-game: a building the server cannot load, a validator that refuses a freshly
 * stamped metal patch, state leaking between matches. Each test runs inside the {@code gametest_arena} template
 * (a 16x16 grass pad); template coordinates are relative to the arena's corner.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class SkirmishGameTests {

    static final String ARENA = "gametest_arena";

    /** Every registered building's blueprint must load on the SERVER (data/), not only on the client (assets/). */
    @GameTest(template = ARENA)
    public static void every_building_blueprint_loads_on_the_server(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> missing = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Building b : ReignOfNetherRegistries.BUILDING) {
            if (b instanceof AbstractBridge || b.structureName == null || !seen.add(b.structureName))
                continue;
            if (BuildingBlockData.getBuildingNbt(b.structureName, level.getServer().getResourceManager()) == null)
                missing.add(b.structureName);
        }
        if (!missing.isEmpty())
            helper.fail("blueprints missing from data/reignofnether/structures: " + missing);
        helper.succeed();
    }

    /**
     * Stamping a patch must leave ground an extractor can sit on, for every faction's extractor and windmill.
     * The game-test arena is carved out of generated terrain, so the test builds its own grass platform high
     * above the terrain (y=200) where the heightmaps and the real ground agree, just like an open field.
     */
    @GameTest(template = ARENA)
    public static void extractor_and_windmill_fit_on_a_stamped_patch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 200;
        int cx = base.getX(), cz = base.getZ();
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        MetalPatches.forgetAll();
        MetalPatches.stamp(level, cx, cz);
        BlockPos centre = new BlockPos(cx, y, cz);
        if (!level.getBlockState(centre).is(MetalPatches.PATCH_BLOCK))
            helper.fail("stamp() did not leave a patch block at " + centre + " (solidTop says "
                + MetalPatches.solidTop(level, cx, cz) + ")");
        BlockPos origin = new BlockPos(cx - 2, y, cz - 2);   // origin = ground block
        for (Building extractor : List.of(Buildings.METAL_EXTRACTOR_VILLAGERS, Buildings.METAL_EXTRACTOR_MONSTERS,
                Buildings.METAL_EXTRACTOR_PIGLINS, Buildings.METAL_EXTRACTOR_VERDANT)) {
            String err = BuildingValidators.getPlacementValidityError(level, extractor, origin, "tester",
                Rotation.NONE, false, false, true);
            if (err != null)   // the Legion's extractor is exempt from the nether-terrain rule on purpose
                helper.fail(extractor.structureName + " refused on a fresh patch: " + err);
        }
        BlockPos windOrigin = new BlockPos(cx + 5, y, cz + 5);
        for (Building wind : List.of(Buildings.WIND_GENERATOR_VILLAGERS, Buildings.WIND_GENERATOR_MONSTERS,
                Buildings.WIND_GENERATOR_PIGLINS, Buildings.WIND_GENERATOR_VERDANT)) {
            String err = BuildingValidators.getPlacementValidityError(level, wind, windOrigin, "tester",
                Rotation.NONE, false, false, true);
            if (err != null)
                helper.fail(wind.structureName + " refused on flat grass: " + err);
        }
        MetalPatches.forgetAll();
        helper.succeed();
    }

    /**
     * Every faction's home patches take that faction's extractor once its capitol stands (lovish's clip 15-51: a home
     * patch beside the Heartwood Hall refused with "Too close to another building"). For each live faction: a flat
     * pad, the home ring stamped round a start as a forming match stamps it, the capitol laid centred on the start as
     * a readied start lays it, then every patch must accept the extractor - and a windmill on a patch is refused, so
     * nothing can crowd a patch later either. A home ring needs ~45 blocks of ground, more than a test's own column,
     * so the pads sit 3000 blocks north of the grid (the bot soak is 1500 east), chunk-aligned, one per faction.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void home_patches_accept_every_factions_extractor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 240, half = com.solegendary.reignofnether.startpos.StartAreaClearing.HOME_RING_RADIUS + 4;
        int chunkX = base.getX() >> 4, chunkZ = (base.getZ() - 3000) >> 4;
        String owner = "gametest_home_patches";
        List<com.solegendary.reignofnether.building.BuildingPlacement> placed = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        int i = 0;
        try {
            for (ResourceLocation key : new ArrayList<>(Factions.PLAYABLE_FACTIONS)) {
                var f = Factions.getFaction(key);
                if (!Factions.isLive(f))
                    continue;
                Building capitol = ReignOfNetherRegistries.BUILDING.get(f.capitolBuilding);
                Building extractor = FactionTraits.of(f).metalExtractor.get();
                if (capitol == null || extractor == null)
                    continue;
                int sx = ((chunkX + i * 4) << 4) + 24, sz = (chunkZ << 4) + 24;   // pad spans 3x3 chunks
                i++;
                for (int dx = -half; dx <= half; dx++)
                    for (int dz = -half; dz <= half; dz++) {
                        level.setBlock(new BlockPos(sx + dx, y - 1, sz + dz), Blocks.DIRT.defaultBlockState(), 2);
                        level.setBlock(new BlockPos(sx + dx, y, sz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                    }
                BlockPos start = new BlockPos(sx, y, sz);
                List<BlockPos> ring = com.solegendary.reignofnether.startpos.StartAreaClearing.stampHomeRingAlone(
                    level, start, 0, 6);
                if (ring.size() != 6) {
                    problems.add(f.getName() + ": stamped " + ring.size() + "/6 home patches");
                    continue;
                }
                var blocks = capitol.getRelativeBlockData(level);
                BlockPos capOrigin = com.solegendary.reignofnether.player.PlayerServerEvents.getBuildingOriginPos(start, blocks);
                var cap = com.solegendary.reignofnether.building.BuildingServerEvents.placeBuilding(capitol, capOrigin,
                    Rotation.NONE, owner, new int[0], false, false, true, true);
                if (cap == null) {
                    problems.add(f.getName() + ": capitol could not be laid");
                    continue;
                }
                placed.add(cap);
                for (BlockPos c : ring) {
                    String err = BuildingValidators.getPlacementValidityError(level, extractor, c.offset(-2, 0, -2),
                        owner, Rotation.NONE, false, false, true);
                    if (err != null)
                        problems.add(f.getName() + ": home patch " + c.toShortString() + " refused "
                            + extractor.structureName + " (" + err + ")");
                }
                if (i == 1) {   // the guard itself, once: a windmill standing on a patch is refused
                    BlockPos c = ring.get(0);
                    String err = BuildingValidators.getPlacementValidityError(level, Buildings.WIND_GENERATOR_VILLAGERS,
                        c.offset(-1, 0, -1), owner, Rotation.NONE, false, false, true);
                    if (!"building.reignofnether.blocks_metal_patch".equals(err))
                        problems.add("a windmill on a patch gave " + err + ", not blocks_metal_patch");
                }
            }
            if (i == 0)
                problems.add("no live faction to test");
        } finally {
            for (var cap : placed)
                com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(cap);
        }
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }

    /**
     * Wild clutter leaves bases: an Alex's Mobs grizzly next to a capitol is removed within a few seconds by the base
     * sweep, while a pig (peaceful ambience) stays. Its own column at its own height, so the sweep (42 blocks across,
     * +-20 high) never reaches another test's mobs.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void wildlife_is_cleared_from_bases_but_pigs_stay(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 290;
        int cx = base.getX(), cz = base.getZ();
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        Building capitol = Buildings.TOWN_CENTRE;
        var blocks = capitol.getRelativeBlockData(level);
        BlockPos origin = com.solegendary.reignofnether.player.PlayerServerEvents.getBuildingOriginPos(
            new BlockPos(cx, y, cz), blocks);
        var cap = com.solegendary.reignofnether.building.BuildingServerEvents.placeBuilding(capitol, origin,
            Rotation.NONE, "gametest_wild_base", new int[0], false, false, true, true);
        var bear = com.solegendary.reignofnether.compat.AlexsMobsCompat.grizzlyBear().create(level);
        var pig = net.minecraft.world.entity.EntityType.PIG.create(level);
        if (cap == null || bear == null || pig == null) {
            if (cap != null)
                com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(cap);
            helper.fail("could not set up the base (" + cap + "), grizzly (" + bear + ") or pig (" + pig + ")");
            return;
        }
        bear.moveTo(cx + 7.5, y + 1, cz + 0.5, 0, 0);
        pig.moveTo(cx - 7.5, y + 1, cz + 0.5, 0, 0);
        if (bear instanceof net.minecraft.world.entity.Mob m)
            m.setNoAi(true);   // stays put: the sweep, not wandering, decides
        pig.setNoAi(true);
        level.addFreshEntity(bear);
        level.addFreshEntity(pig);
        if (!com.solegendary.reignofnether.startpos.BaseWildlifeControl.isClutter(bear))
            helper.fail("a grizzly is not counted as wild clutter");
        if (com.solegendary.reignofnether.startpos.BaseWildlifeControl.isClutter(pig))
            helper.fail("a pig is counted as wild clutter");
        helper.runAfterDelay(100, () -> {
            try {
                if (bear.isAlive() && !bear.isRemoved())
                    helper.fail("the grizzly beside the capitol is still there after 5 s");
                if (pig.isRemoved())
                    helper.fail("the pig beside the capitol was removed");
                helper.succeed();
            } finally {
                pig.discard();
                bear.discard();
                com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(cap);
            }
        });
    }

    /**
     * A placed extractor or windmill must survive its first ticks as a construction site. Both name no starting
     * block types, so RoN used to queue nothing, see zero blocks placed and destroy the site at once (the windmill
     * that exploded the moment it was placed). Places one of each for real and checks they are still standing.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void placed_extractor_and_windmill_survive_as_sites(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 210;
        int cx = base.getX(), cz = base.getZ();   // its own column: an offset column lands over whichever arena the grid puts there (it broke the stamped-patch test)
        for (int dx = -10; dx <= 10; dx++)
            for (int dz = -10; dz <= 10; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        MetalPatches.forgetAll();
        MetalPatches.stamp(level, cx, cz);
        String owner = "gametest_owner";
        var mex = com.solegendary.reignofnether.building.BuildingServerEvents.placeBuilding(
            Buildings.METAL_EXTRACTOR_VILLAGERS, new BlockPos(cx - 2, y, cz - 2), Rotation.NONE, owner, new int[0],
            false, false, true, true);
        var wind = com.solegendary.reignofnether.building.BuildingServerEvents.placeBuilding(
            Buildings.WIND_GENERATOR_VILLAGERS, new BlockPos(cx + 5, y, cz + 5), Rotation.NONE, owner, new int[0],
            false, false, true, true);
        var conv = com.solegendary.reignofnether.building.BuildingServerEvents.placeBuilding(
            Buildings.ENERGY_CONVERTER_VILLAGERS, new BlockPos(cx - 7, y, cz + 5), Rotation.NONE, owner, new int[0],
            false, false, true, true);
        if (mex == null || wind == null || conv == null)
            helper.fail("placeBuilding returned null (mex " + mex + ", wind " + wind + ", converter " + conv + ")");
        helper.runAfterDelay(40, () -> {
            for (var placement : List.of(mex, wind, conv)) {
                if (placement.isDestroyedServerside
                        || !com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().contains(placement))
                    helper.fail(placement.getBuilding().structureName + " was destroyed right after being placed");
            }
            MetalPatches.forgetAll();
            helper.succeed();
        });
    }

    /** A commander carries its faction's signature ability, and the Horde's War Horn buffs an ally in range. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void commander_war_horn_buffs_allies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var commander = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get().create(level);
        var ally = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get().create(level);
        if (commander == null || ally == null) {
            helper.fail("could not create grunt units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(6, 2, 6));
        commander.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        ally.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5, 0, 0);
        commander.setOwnerName("gametest_horde");
        ally.setOwnerName("gametest_horde");
        level.addFreshEntity(commander);
        level.addFreshEntity(ally);
        com.solegendary.reignofnether.player.CommanderServerEvents.makeCommander(commander);
        com.solegendary.reignofnether.ability.abilities.CommanderAbility horn = null;
        for (var a : commander.getAbilities().get())
            if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderAbility ca)
                horn = ca;
        if (horn == null) {
            helper.fail("commander has no CommanderAbility");
            return;
        }
        final var ability = horn;
        helper.runAfterDelay(5, () -> {
            ability.use(level, commander, commander.blockPosition());
            if (!ally.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST))
                helper.fail("War Horn did not give the ally Strength");
            if (ability.isOffCooldown(commander))
                helper.fail("War Horn did not go on cooldown");
            commander.discard();
            ally.discard();
            helper.succeed();
        });
    }

    /** Liveries are visual only: a dressed unit wears its faction kit but gains no armour (balance pillar). */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void liveries_give_zero_armour(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var soldier = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (soldier == null) {
            helper.fail("could not create a vindicator unit");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(9, 2, 9));
        soldier.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        soldier.setOwnerName("gametest_kingdom");
        double baseArmour = soldier.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
        level.addFreshEntity(soldier);
        helper.runAfterDelay(90, () -> {
            if (soldier.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty())
                helper.fail("unit was never dressed");
            double armour = soldier.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
            if (armour > baseArmour + 0.001)
                helper.fail("livery added armour: " + baseArmour + " -> " + armour);
            soldier.discard();
            helper.succeed();
        });
    }

    /** The colour palette is what the lobby, packets and saves agree on: ids unique, nonzero, textures named. */
    @GameTest(template = ARENA)
    public static void colour_palette_is_consistent(GameTestHelper helper) {
        Set<Integer> ids = new HashSet<>();
        for (PlayerPalette.Entry e : PlayerPalette.ENTRIES) {
            if (e.mapColorId() <= 0 || !ids.add(e.mapColorId()))
                helper.fail("palette colour " + e.name() + " has a bad or duplicate map colour id " + e.mapColorId());
            if (e.name().isBlank() || !e.name().equals(e.name().toLowerCase()))
                helper.fail("palette colour name must be a lowercase texture key: " + e.name());
        }
        if (PlayerPalette.firstFree(ids) == 0)
            helper.fail("firstFree returned 0 with a full palette");
        helper.succeed();
    }

    /** BAR reclaim: a fallen unit leaves a wreck, and a worker standing at it strips metal into its owner's pool. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void wrecks_drop_and_workers_reclaim_metal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_reclaimer";
        com.solegendary.reignofnether.resources.Resources pool = null;
        for (var r : com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList)
            if (r.ownerName.equals(owner))
                pool = r;
        if (pool == null) {
            pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 0, 0);
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
        }
        pool.ore = 0;
        pool.oreFrac = 0;
        final var res = pool;

        var soldier = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        var worker = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        if (soldier == null || worker == null) {
            helper.fail("could not create units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(4, 2, 12));
        soldier.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        soldier.setOwnerName("gametest_fallen");
        worker.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5, 0, 0);
        worker.setOwnerName(owner);
        level.addFreshEntity(soldier);
        level.addFreshEntity(worker);
        helper.runAfterDelay(5, () -> soldier.kill());
        helper.runAfterDelay(15, () -> {
            var near = com.solegendary.reignofnether.resources.WreckServerEvents.getWrecks().stream()
                .filter(w -> !w.isRemoved() && w.distanceToSqr(worker) < 16).toList();
            if (near.isEmpty()) {
                helper.fail("the fallen vindicator left no wreck");
                return;
            }
            float before = com.solegendary.reignofnether.resources.WreckServerEvents.metalOf(near.get(0));
            com.solegendary.reignofnether.resources.WreckServerEvents.tickReclaim(level, 2f);
            float after = near.get(0).isRemoved() ? 0
                : com.solegendary.reignofnether.resources.WreckServerEvents.metalOf(near.get(0));
            if (res.getMetal() <= 0)
                helper.fail("worker at the wreck reclaimed no metal (wreck " + before + " -> " + after + ")");
            if (after >= before)
                helper.fail("wreck did not lose metal: " + before + " -> " + after);
            if (com.solegendary.reignofnether.barfx.BarFx.lastNanoTime(worker) < 0)
                helper.fail("reclaiming worker sent no nanolathe beam");
            near.forEach(net.minecraft.world.entity.Entity::discard);
            worker.discard();
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(res);
            helper.succeed();
        });
    }

    /**
     * Area reclaim: a circle over four wrecks and two workers deals the wrecks out nearest-first, two each, the first
     * started at once (worker busy on it) and the second shift-queued; a wreck outside the circle is left alone.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void area_reclaim_splits_wrecks_between_workers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_area_reclaim";
        var a = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        var b = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        if (a == null || b == null) {
            helper.fail("could not create workers");
            return;
        }
        BlockPos centre = helper.absolutePos(new BlockPos(8, 2, 8));
        int y = centre.getY(), z = centre.getZ();
        a.moveTo(helper.absolutePos(new BlockPos(2, 2, 8)).getX() + 0.5, y, z + 0.5, 0, 0);
        b.moveTo(helper.absolutePos(new BlockPos(13, 2, 8)).getX() + 0.5, y, z + 0.5, 0, 0);
        a.setOwnerName(owner);
        b.setOwnerName(owner);
        level.addFreshEntity(a);
        level.addFreshEntity(b);
        List<net.minecraft.world.entity.Entity> wrecks = new ArrayList<>();
        for (int x : new int[] { 4, 6, 9, 11 })   // A is nearest 4 then 6; B nearest 11 then 9
            wrecks.add(com.solegendary.reignofnether.resources.WreckServerEvents.spawnWreck(level,
                helper.absolutePos(new BlockPos(x, 2, 8)).getX() + 0.5, y, z + 0.5, 100f, null));
        var outside = com.solegendary.reignofnether.resources.WreckServerEvents.spawnWreck(level,
            centre.getX() + 0.5, y, helper.absolutePos(new BlockPos(8, 2, 15)).getZ() + 0.5, 100f, null);
        helper.runAfterDelay(3, () -> {
            try {
                if (wrecks.contains(null) || outside == null) {
                    helper.fail("test wrecks did not spawn");
                    return;
                }
                int handed = com.solegendary.reignofnether.unit.AreaCommands.issue(level, owner,
                    com.solegendary.reignofnether.unit.AreaCommands.MODE_RECLAIM, centre, 6,
                    new int[] { a.getId(), b.getId() }, false);
                if (handed != 4)
                    helper.fail("expected the 4 wrecks inside the circle to be handed out, got " + handed);
                var ta = com.solegendary.reignofnether.resources.WreckServerEvents.getReclaimTarget(a);
                var tb = com.solegendary.reignofnether.resources.WreckServerEvents.getReclaimTarget(b);
                if (ta != wrecks.get(0) || tb != wrecks.get(3))
                    helper.fail("each worker should start on the wreck nearest to it");
                if (com.solegendary.reignofnether.unit.interfaces.WorkerUnit.isIdle(a))
                    helper.fail("a worker with a reclaim order counts as idle (its queue would skip ahead)");
                var qa = com.solegendary.reignofnether.unit.UnitServerEvents.getQueuedActions(a.getId());
                var qb = com.solegendary.reignofnether.unit.UnitServerEvents.getQueuedActions(b.getId());
                if (qa.size() != 1 || qa.get(0).getTargetUnitId() != wrecks.get(1).getId()
                        || qa.get(0).getAction() != com.solegendary.reignofnether.unit.UnitAction.RECLAIM)
                    helper.fail("worker A should have its second-nearest wreck queued, queue size " + qa.size());
                if (qb.size() != 1 || qb.get(0).getTargetUnitId() != wrecks.get(2).getId())
                    helper.fail("worker B should have its second-nearest wreck queued, queue size " + qb.size());
                helper.succeed();
            } finally {
                com.solegendary.reignofnether.unit.UnitServerEvents.clearQueuedActions(a.getId());
                com.solegendary.reignofnether.unit.UnitServerEvents.clearQueuedActions(b.getId());
                com.solegendary.reignofnether.resources.WreckServerEvents.clearReclaimTarget(a);
                com.solegendary.reignofnether.resources.WreckServerEvents.clearReclaimTarget(b);
                for (var w : wrecks)
                    if (w != null)
                        w.discard();
                if (outside != null)
                    outside.discard();
                a.discard();
                b.discard();
            }
        });
    }

    /**
     * Area attack: a circle over four enemy units and two fighters deals the enemies out nearest-first (each fighter
     * starts on the one nearest to it), then each fighter queues the rest of the circle so it doesn't idle; an own
     * unit inside the circle and an enemy outside it are never targeted.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void area_attack_splits_enemies_between_fighters(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_area_attack";
        String enemy = "gametest_area_attack_foe";
        var type = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get();
        List<com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit> all = new ArrayList<>();
        BlockPos centre = helper.absolutePos(new BlockPos(8, 2, 8));
        int y = centre.getY(), z = centre.getZ();
        // fighters at x=2 and x=13, enemies at 4, 6, 9, 11, an own unit at 7 and an enemy outside the circle
        int[][] spots = { { 2, 8 }, { 13, 8 }, { 4, 8 }, { 6, 8 }, { 9, 8 }, { 11, 8 }, { 7, 8 }, { 8, 15 } };
        for (int i = 0; i < spots.length; i++) {
            var u = type.create(level);
            if (u == null) {
                helper.fail("could not create units");
                return;
            }
            BlockPos p = helper.absolutePos(new BlockPos(spots[i][0], 2, spots[i][1]));
            u.moveTo(p.getX() + 0.5, y, p.getZ() + 0.5, 0, 0);
            u.setOwnerName(i < 2 || i == 6 ? owner : enemy);
            level.addFreshEntity(u);
            all.add(u);
        }
        var a = all.get(0);
        var b = all.get(1);
        helper.runAfterDelay(3, () -> {
            try {
                int handed = com.solegendary.reignofnether.unit.AreaCommands.issue(level, owner,
                    com.solegendary.reignofnether.unit.AreaCommands.MODE_AUTO, centre, 6,
                    new int[] { a.getId(), b.getId() }, false);
                // 4 enemies, each fighter: its 2-target share + the other 2 as follow-ups
                if (handed != 8)
                    helper.fail("expected 8 attack orders (4 enemies x 2 fighters), got " + handed);
                if (a.getTargetGoal().getTarget() != all.get(2) || b.getTargetGoal().getTarget() != all.get(5))
                    helper.fail("each fighter should start on the enemy nearest to it");
                var qa = com.solegendary.reignofnether.unit.UnitServerEvents.getQueuedActions(a.getId());
                var qb = com.solegendary.reignofnether.unit.UnitServerEvents.getQueuedActions(b.getId());
                if (qa.size() != 3 || qa.get(0).getTargetUnitId() != all.get(3).getId()
                        || qa.get(0).getAction() != com.solegendary.reignofnether.unit.UnitAction.ATTACK)
                    helper.fail("fighter A should queue its second-nearest enemy next, queue size " + qa.size());
                if (qb.size() != 3 || qb.get(0).getTargetUnitId() != all.get(4).getId())
                    helper.fail("fighter B should queue its second-nearest enemy next, queue size " + qb.size());
                for (var q : List.of(qa, qb))
                    for (var item : q)
                        if (item.getTargetUnitId() == all.get(6).getId() || item.getTargetUnitId() == all.get(7).getId())
                            helper.fail("an own unit or an enemy outside the circle was targeted");
                helper.succeed();
            } finally {
                for (var u : all) {
                    com.solegendary.reignofnether.unit.UnitServerEvents.clearQueuedActions(u.getId());
                    u.discard();
                }
            }
        });
    }

    /**
     * Player panel: the roster each player receives lists itself, then its allies, then enemies, and only its own
     * and its allies' lines carry income / commander health (an enemy's economy must never be sent). An observer
     * (not in the match: a spectator, or a finished player watching on) gets every side's numbers.
     */
    @GameTest(template = ARENA)
    public static void player_panel_hides_enemy_economy(GameTestHelper helper) {
        String me = "gametest_panel_me", ally = "gametest_panel_ally", foe = "gametest_panel_foe";
        List<com.solegendary.reignofnether.player.PlayerPanelServerEvents.Row> rows = List.of(
            new com.solegendary.reignofnether.player.PlayerPanelServerEvents.Row(foe, "", true, 9, 90, 0.5f),
            new com.solegendary.reignofnether.player.PlayerPanelServerEvents.Row(ally, "", true, 3, 30, 1f),
            new com.solegendary.reignofnether.player.PlayerPanelServerEvents.Row(me, "", true, 2, 20, 0.25f));
        com.solegendary.reignofnether.alliance.AlliancesServerEvents.addAlliance(me, ally);
        // "me" is a side still in the match (an observer would be shown everything)
        var meSide = com.solegendary.reignofnether.player.RTSPlayer.getNewBot(me, Factions.VILLAGERS);
        var rtsPlayers = com.solegendary.reignofnether.player.PlayerServerEvents.rtsPlayers;
        synchronized (rtsPlayers) {
            rtsPlayers.add(meSide);
        }
        try {
            var list = com.solegendary.reignofnether.player.PlayerPanelServerEvents.entriesFor(me, rows);
            var watched = com.solegendary.reignofnether.player.PlayerPanelServerEvents.entriesFor("gametest_panel_watcher", rows);
            if (list.size() != 3 || !list.get(0).name().equals(me) || !list.get(1).name().equals(ally)
                    || !list.get(2).name().equals(foe))
                helper.fail("expected order me, ally, foe");
            else if (!list.get(0).hasAllyData() || !list.get(1).hasAllyData() || list.get(1).metalIncome() != 3)
                helper.fail("own and allied lines should carry income");
            else if (list.get(2).hasAllyData() || list.get(2).metalIncome() != 0 || list.get(2).commanderHp() >= 0)
                helper.fail("the enemy line leaked economy / commander data");
            else if (watched.size() != 3 || !watched.get(0).hasAllyData() || !watched.get(1).hasAllyData()
                    || !watched.get(2).hasAllyData())
                helper.fail("an observer should see every side's economy");
            else
                helper.succeed();
        } finally {
            synchronized (rtsPlayers) {
                rtsPlayers.remove(meSide);
            }
            com.solegendary.reignofnether.alliance.AlliancesServerEvents.removeAlliance(me, ally);
        }
    }

    /**
     * Nanolathe feedback: a worker standing at its own construction site sends NANO beam events while the site is
     * unfinished. Checks the per-worker record BarFx keeps for its rate limit, so it never depends on global counts.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void builders_send_nano_beams(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 230;
        // in this test's own column, at its own height: an offset (it was -40/+40) lands in whichever arena the grid
        // puts there, and once the test count shifted the grid this platform sat over the stamped-patch test's centre
        // and broke its heightmap (solidTop 230)
        int cx = base.getX(), cz = base.getZ();
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        String owner = "gametest_nano";
        BlockPos origin = new BlockPos(cx, y, cz);
        var site = com.solegendary.reignofnether.building.BuildingServerEvents.placeBuilding(
            Buildings.WIND_GENERATOR_VILLAGERS, origin, Rotation.NONE, owner, new int[0], false, false, true, true);
        var worker = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        if (site == null || worker == null) {
            helper.fail("could not set up the site (" + site + ") or the worker (" + worker + ")");
            return;
        }
        worker.moveTo(cx - 0.5, y + 1, cz + 0.5, 0, 0);   // just outside the footprint, within build range
        worker.setOwnerName(owner);
        level.addFreshEntity(worker);
        worker.getBuildRepairGoal().setBuildingTarget(site);
        helper.succeedWhen(() -> {
            if (com.solegendary.reignofnether.barfx.BarFx.lastNanoTime(worker) < 0)
                helper.fail("worker at its site sent no nano beam (building " + worker.getBuildRepairGoal().isBuilding()
                    + ", placed " + site.getBlocksPlaced() + "/" + site.getBlocksTotal() + ")");
            worker.discard();
        });
    }

    /** The commander D-gun: hits the enemy on the line, spares the friend beside it, charges energy, cools down. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void commander_dgun_hits_the_line_only(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_dgunner";
        var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 0, 0);
        pool.addEnergy(500);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
        var commander = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get().create(level);
        var enemy = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get().create(level);
        var friend = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get().create(level);
        var offline = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get().create(level);
        if (commander == null || enemy == null || friend == null || offline == null) {
            helper.fail("could not create grunt units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 2));
        commander.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        enemy.moveTo(at.getX() + 6.5, at.getY(), at.getZ() + 0.5, 0, 0);     // on the line (+x)
        friend.moveTo(at.getX() + 4.5, at.getY(), at.getZ() + 0.5, 0, 0);    // on the line, but ours
        offline.moveTo(at.getX() + 6.5, at.getY(), at.getZ() + 6.5, 0, 0);   // enemy, well off the line
        commander.setOwnerName(owner);
        friend.setOwnerName(owner);
        enemy.setOwnerName("gametest_target");
        offline.setOwnerName("gametest_target");
        for (var e : List.of(commander, enemy, friend, offline))
            level.addFreshEntity(e);
        com.solegendary.reignofnether.player.CommanderServerEvents.makeCommander(commander);
        com.solegendary.reignofnether.ability.abilities.CommanderDGun found = null;
        for (var a : commander.getAbilities().get())
            if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderDGun d)
                found = d;
        if (found == null) {
            helper.fail("commander has no D-gun");
            return;
        }
        final var dgun = found;
        helper.runAfterDelay(5, () -> {
            float friendHp = friend.getHealth(), offHp = offline.getHealth();
            dgun.use(level, commander, BlockPos.containing(at.getX() + 12, at.getY(), at.getZ()));
            if (enemy.isAlive() && enemy.getMaxHealth() - enemy.getHealth() < 40)
                helper.fail("D-gun barely hurt the enemy on its line: " + enemy.getHealth() + "/" + enemy.getMaxHealth());
            if (friend.getHealth() < friendHp)
                helper.fail("D-gun hurt a friendly unit");
            if (offline.getHealth() < offHp)
                helper.fail("D-gun hurt a unit far off its line");
            if (pool.getEnergy() > 500 - com.solegendary.reignofnether.ability.abilities.CommanderDGun.ENERGY_COST + 0.01)
                helper.fail("D-gun did not charge energy: " + pool.getEnergy());
            if (dgun.isOffCooldown(commander))
                helper.fail("D-gun did not go on cooldown");
            for (var e : List.of(commander, enemy, friend, offline))
                e.discard();
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
            helper.succeed();
        });
    }

    /** Horde Momentum: a straight charge builds the damage bonus, and stopping drops it. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void horde_momentum_builds_on_a_straight_charge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var grunt = com.solegendary.reignofnether.registrars.EntityRegistrar.BRUTE_UNIT.get().create(level);
        if (grunt == null) {
            helper.fail("could not create a brute unit");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 8));
        grunt.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        grunt.setOwnerName("gametest_horde_charge");
        level.addFreshEntity(grunt);
        var dmgAttr = com.solegendary.reignofnether.registrars.AttributeRegistrar.ATTACK_DAMAGE.get();
        helper.runAfterDelay(2, () -> {
            double base = grunt.getAttributeValue(dmgAttr);
            com.solegendary.reignofnether.unit.MomentumServerEvents.sampleAll(level);
            for (int i = 1; i <= 6; i++) {
                grunt.teleportTo(grunt.getX() + 1.0, grunt.getY(), grunt.getZ());
                com.solegendary.reignofnether.unit.MomentumServerEvents.sampleAll(level);
            }
            float m = com.solegendary.reignofnether.unit.MomentumServerEvents.getMomentum(grunt);
            if (m < 0.99f)
                helper.fail("six straight steps built only " + m + " momentum");
            if (grunt.getAttributeValue(dmgAttr) <= base + 0.001)
                helper.fail("full momentum gave no damage bonus (" + base + ")");
            com.solegendary.reignofnether.unit.MomentumServerEvents.sampleAll(level);   // standing still
            if (com.solegendary.reignofnether.unit.MomentumServerEvents.getMomentum(grunt) > 0)
                helper.fail("momentum did not drop when the grunt stopped");
            if (Math.abs(grunt.getAttributeValue(dmgAttr) - base) > 0.001)
                helper.fail("damage bonus stuck after stopping");
            grunt.discard();
            helper.succeed();
        });
    }

    /** Sunforged Formation: a crossbow with two melee guards beside it is in formation; alone it is not. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void sunforged_formation_needs_two_guards(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_kingdom_line";
        var bow = com.solegendary.reignofnether.registrars.EntityRegistrar.PILLAGER_UNIT.get().create(level);
        var lone = com.solegendary.reignofnether.registrars.EntityRegistrar.PILLAGER_UNIT.get().create(level);
        var g1 = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        var g2 = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (bow == null || lone == null || g1 == null || g2 == null) {
            helper.fail("could not create kingdom units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(12, 2, 3));
        bow.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        g1.moveTo(at.getX() + 2.5, at.getY(), at.getZ() + 0.5, 0, 0);
        g2.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5, 0, 0);
        lone.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 12.5, 0, 0);
        for (var e : List.of(bow, lone, g1, g2)) {
            e.setOwnerName(owner);
            level.addFreshEntity(e);
        }
        helper.runAfterDelay(3, () -> {
            com.solegendary.reignofnether.unit.FormationServerEvents.update(level);
            var ronAttack = com.solegendary.reignofnether.registrars.AttributeRegistrar.ATTACK_DAMAGE.get();
            if (!com.solegendary.reignofnether.unit.FormationServerEvents.isInFormation(bow))
                helper.fail("crossbow with two guards beside it is not in formation");
            if (bow.getAttributeValue(ronAttack) <= lone.getAttributeValue(ronAttack) + 0.001)
                helper.fail("formation gave the crossbow no attack bonus");
            if (com.solegendary.reignofnether.unit.FormationServerEvents.isInFormation(lone))
                helper.fail("a lone crossbow counts as in formation");
            for (var e : List.of(bow, lone, g1, g2))
                e.discard();
            helper.succeed();
        });
    }

    /** Raise Dead: needs population room; with it, a wreck rises as a Ghoul and pays from the wreck first. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void raise_dead_turns_wrecks_into_ghouls(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_lich";
        var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 0, 0);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
        var lich = com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_UNIT.get().create(level);
        if (lich == null) {
            helper.fail("could not create a zombie unit");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(13, 2, 13));
        lich.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        lich.setOwnerName(owner);
        level.addFreshEntity(lich);
        var wreck = com.solegendary.reignofnether.resources.WreckServerEvents.spawnWreck(level, at.getX() - 1.5,
            at.getY(), at.getZ() + 0.5, 200f, com.solegendary.reignofnether.faction.Factions.MONSTERS);
        helper.runAfterDelay(3, () -> {
            if (wreck == null || wreck.isRemoved()) {
                helper.fail("test wreck did not spawn");
                return;
            }
            int cap = com.solegendary.reignofnether.unit.UnitServerEvents.maxPopulation;   // no supply now: test the unit cap
            com.solegendary.reignofnether.unit.UnitServerEvents.maxPopulation = 0;
            int none = com.solegendary.reignofnether.ability.abilities.RaiseDead.raise(level, lich, owner);
            com.solegendary.reignofnether.unit.UnitServerEvents.maxPopulation = cap;
            if (none != 0)
                helper.fail("raised a Ghoul past the unit cap");
            com.solegendary.reignofnether.research.ResearchServerEvents.addCheat(owner, "foodforthought");
            int popBefore = com.solegendary.reignofnether.unit.UnitServerEvents.getCurrentPopulation(owner);
            int raised = com.solegendary.reignofnether.ability.abilities.RaiseDead.raise(level, lich, owner);
            com.solegendary.reignofnether.research.ResearchServerEvents.removeCheat(owner, "foodforthought");
            if (raised != 1)
                helper.fail("expected one Ghoul from one wreck, got " + raised);
            float left = com.solegendary.reignofnether.resources.WreckServerEvents.metalOf(wreck);
            if (left > 200f - com.solegendary.reignofnether.resources.ResourceCosts.ZOMBIE.metal() + 0.01f)
                helper.fail("the Ghoul was not paid from the wreck (" + left + " left)");
            if (pool.getMetal() < -0.01f || pool.getMetal() > 0.01f)
                helper.fail("pool paid although the wreck covered the cost: " + pool.getMetal());
            for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, lich.getBoundingBox().inflate(6)))
                if (e instanceof com.solegendary.reignofnether.unit.interfaces.Unit u && owner.equals(u.getOwnerName()))
                    e.discard();
            wreck.discard();
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
            helper.succeed();
        });
    }

    /** Energy converters only burn energy above half storage, at 50 energy per metal. */
    @GameTest(template = ARENA)
    public static void energy_converter_turns_spare_energy_into_metal(GameTestHelper helper) {
        var eco = com.solegendary.reignofnether.resources.EconomyServerEvents.getEconomy("gametest_alchemist");
        eco.conversionCapacity = 50f;
        var res = new com.solegendary.reignofnether.resources.Resources("gametest_alchemist", 0, 0, 0);
        res.addEnergy(900);
        for (int i = 0; i < 20; i++)
            com.solegendary.reignofnether.resources.EconomyServerEvents.convertEnergy(res, eco);
        if (Math.abs(res.getMetal() - 1f) > 0.05f || Math.abs(res.getEnergy() - 850f) > 1f)
            helper.fail("one second of one converter: expected ~1 metal / 850 energy, got " + res.getMetal()
                + " / " + res.getEnergy());
        var low = new com.solegendary.reignofnether.resources.Resources("gametest_alchemist", 0, 0, 0);
        low.addEnergy(400);
        com.solegendary.reignofnether.resources.EconomyServerEvents.convertEnergy(low, eco);
        if (low.getMetal() > 0)
            helper.fail("converter ran below half energy storage");
        eco.conversionCapacity = 0;
        helper.succeed();
    }

    /**
     * BAR storage: a completed vault raises its owner's caps by 2000 metal / 3000 energy, and when it dies whatever
     * it held above the new cap is lost. Places a real vault, marks it built, recalculates, then destroys it - all
     * inside this one call, so no server tick (or another test's recalc) runs in between.
     */
    @GameTest(template = ARENA)
    public static void storage_vault_raises_cap_and_its_loss_trims_excess(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 205;
        int cx = base.getX(), cz = base.getZ();
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        String owner = "gametest_vault_owner";
        var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 0, 0);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
        var vault = com.solegendary.reignofnether.building.BuildingServerEvents.placeBuilding(
            Buildings.STORAGE_VAULT_VILLAGERS, new BlockPos(cx - 1, y, cz - 1), Rotation.NONE, owner, new int[0],
            false, false, true, true);
        try {
            if (vault == null) {
                helper.fail("placeBuilding returned null for the vault");
                return;
            }
            vault.isBuilt = true;
            com.solegendary.reignofnether.resources.EconomyServerEvents.recalculateIncomeAndStorage();
            var eco = com.solegendary.reignofnether.resources.EconomyServerEvents.getEconomy(owner);
            float baseM = com.solegendary.reignofnether.resources.EconomyServerEvents.DEFAULT_METAL_STORAGE;
            float baseE = com.solegendary.reignofnether.resources.EconomyServerEvents.DEFAULT_ENERGY_STORAGE;
            if (Math.abs(eco.metalStorage - (baseM + 2000f)) > 0.5f || Math.abs(eco.energyStorage - (baseE + 3000f)) > 0.5f) {
                helper.fail("a built vault should raise storage to " + (baseM + 2000f) + " / " + (baseE + 3000f)
                    + ", got " + eco.metalStorage + " / " + eco.energyStorage);
                return;
            }
            pool.addMetal(2500);
            pool.addEnergy(3500);
            com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(vault);
            vault.destroy(level);
            if (Math.abs(pool.getMetal() - baseM) > 0.5f || Math.abs(pool.getEnergy() - baseE) > 0.5f)
                helper.fail("losing the vault should trim the pool to the new cap " + baseM + " / " + baseE
                    + ", got " + pool.getMetal() + " / " + pool.getEnergy());
            else if (eco.metalStorage > baseM + 0.5f)
                helper.fail("storage did not drop with the vault: " + eco.metalStorage);
            else
                helper.succeed();
        } finally {
            if (vault != null)
                com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(vault);
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
        }
    }

    /** A fresh pool registered for an overflow test, with its storage and fill set (no tick runs in between). */
    private static com.solegendary.reignofnether.resources.Resources overflowPool(String name, float storage, float metal) {
        var pool = new com.solegendary.reignofnether.resources.Resources(name, 0, 0, 0);
        pool.addMetal(metal);
        var eco = com.solegendary.reignofnether.resources.EconomyServerEvents.getEconomy(name);
        eco.metalStorage = storage;
        eco.energyStorage = storage;
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
        return pool;
    }

    /** BAR overflow: income that doesn't fit goes to allies with room, split evenly - never to an enemy. */
    @GameTest(template = ARENA)
    public static void overflow_goes_to_allies_with_room_not_enemies(GameTestHelper helper) {
        String me = "gametest_ovf_me", ally = "gametest_ovf_ally", ally2 = "gametest_ovf_ally2", foe = "gametest_ovf_foe";
        var pools = List.of(overflowPool(me, 1000, 1000), overflowPool(ally, 1000, 500),
            overflowPool(ally2, 1000, 0), overflowPool(foe, 1000, 0));
        com.solegendary.reignofnether.alliance.AlliancesServerEvents.addAlliance(me, ally);
        com.solegendary.reignofnether.alliance.AlliancesServerEvents.addAlliance(me, ally2);
        try {
            float shared = com.solegendary.reignofnether.resources.EconomyServerEvents.shareOverflow(me, 10f, true);
            if (Math.abs(shared - 10f) > 0.01f)
                helper.fail("both allies had room, all 10 should be shared, got " + shared);
            else if (Math.abs(pools.get(1).getMetal() - 505f) > 0.01f || Math.abs(pools.get(2).getMetal() - 5f) > 0.01f)
                helper.fail("overflow should split evenly: ally " + pools.get(1).getMetal() + ", ally2 " + pools.get(2).getMetal());
            else if (pools.get(3).getMetal() > 0.001f)
                helper.fail("the enemy received overflow: " + pools.get(3).getMetal());
            else if (pools.get(0).getMetal() > 1000.001f)
                helper.fail("the sender went over its cap: " + pools.get(0).getMetal());
            else
                helper.succeed();
        } finally {
            com.solegendary.reignofnether.alliance.AlliancesServerEvents.removeAlliance(me, ally);
            com.solegendary.reignofnether.alliance.AlliancesServerEvents.removeAlliance(me, ally2);
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.removeAll(pools);
        }
    }

    /** Overflow is only wasted when every ally is full too; an ally with a little room takes exactly that much. */
    @GameTest(template = ARENA)
    public static void overflow_is_wasted_only_when_every_ally_is_full(GameTestHelper helper) {
        String me = "gametest_waste_me", ally = "gametest_waste_ally";
        var pools = List.of(overflowPool(me, 1000, 1000), overflowPool(ally, 1000, 1000));
        com.solegendary.reignofnether.alliance.AlliancesServerEvents.addAlliance(me, ally);
        try {
            float shared = com.solegendary.reignofnether.resources.EconomyServerEvents.shareOverflow(me, 10f, true);
            if (shared > 0.001f) {
                helper.fail("ally was full but took " + shared);
                return;
            }
            pools.get(1).addMetal(-3f);   // 3 metal of room
            shared = com.solegendary.reignofnether.resources.EconomyServerEvents.shareOverflow(me, 10f, true);
            if (Math.abs(shared - 3f) > 0.01f || Math.abs(pools.get(1).getMetal() - 1000f) > 0.01f)
                helper.fail("ally with 3 room should take exactly 3 (7 wasted), took " + shared
                    + ", ally now " + pools.get(1).getMetal());
            else
                helper.succeed();
        } finally {
            com.solegendary.reignofnether.alliance.AlliancesServerEvents.removeAlliance(me, ally);
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.removeAll(pools);
        }
    }

    /** Veterancy: killing an equal-cost enemy is one rank (+10% max health); ranks cap at 3. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void veterans_rank_up_on_kills(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var vet = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (vet == null || foe == null) {
            helper.fail("could not create vindicator units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(8, 2, 14));
        vet.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5, 0, 0);
        vet.setOwnerName("gametest_veteran");
        foe.setOwnerName("gametest_victim");
        level.addFreshEntity(vet);
        level.addFreshEntity(foe);
        helper.runAfterDelay(3, () -> {
            double baseHp = vet.getMaxHealth();
            boolean hurt = foe.hurt(level.damageSources().indirectMagic(vet, vet), 10000f);   // mobAttack is rewritten to melee damage
            if (!foe.isDeadOrDying()) {   // the real kill path didn't land: report why, then test the rule directly
                helper.fail("foe survived a 10000 hit (hurt=" + hurt + ", hp=" + foe.getHealth() + ", invulnerable="
                    + foe.isInvulnerable() + ", invTime=" + foe.invulnerableTime + ")");
                return;
            }
            if (com.solegendary.reignofnether.unit.VeterancyServerEvents.getRank(vet) != 1)
                helper.fail("one equal-cost kill should be rank 1, got xp "
                    + com.solegendary.reignofnether.unit.VeterancyServerEvents.getXp(vet) + " (vet alive=" + vet.isAlive()
                    + ", vet cost=" + (vet.getCost() == null ? "null" : vet.getCost().metal()) + ", foe cost="
                    + (foe.getCost() == null ? "null" : foe.getCost().metal()) + ")");
            if (vet.getMaxHealth() < baseHp * 1.09)
                helper.fail("rank 1 gave no health: " + baseHp + " -> " + vet.getMaxHealth());
            com.solegendary.reignofnether.unit.VeterancyServerEvents.award(vet, 10f);
            if (com.solegendary.reignofnether.unit.VeterancyServerEvents.getRank(vet) != 3)
                helper.fail("rank should cap at 3");
            vet.discard();
            foe.discard();
            helper.succeed();
        });
    }

    /** The Horde's War Mammoth exists, belongs to the Horde, and obeys T3 rule (a): 4-6x a T2 heavy's cost. */
    @GameTest(template = ARENA)
    public static void war_mammoth_is_a_proper_t3(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var mammoth = com.solegendary.reignofnether.registrars.EntityRegistrar.WAR_MAMMOTH_UNIT.get().create(level);
        if (mammoth == null) {
            helper.fail("could not create the War Mammoth");
            return;
        }
        var f = com.solegendary.reignofnether.faction.Factions.getFaction(mammoth);
        if (f == null || !f.equals(com.solegendary.reignofnether.faction.Factions.PIGLINS))
            helper.fail("War Mammoth is not a Horde unit: " + f);
        float ratio = (float) com.solegendary.reignofnether.resources.ResourceCosts.WAR_MAMMOTH.metal()
            / com.solegendary.reignofnether.resources.ResourceCosts.RAVAGER.metal();
        if (ratio < 4f || ratio > 6f)
            helper.fail("T3 rule (a): War Mammoth should cost 4-6x the Siege Ox, is " + ratio + "x");
        if (mammoth.getMaxHealth() < 1000)
            helper.fail("War Mammoth health " + mammoth.getMaxHealth());
        if (mammoth.getAbilities().get().stream().noneMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.Trample))
            helper.fail("War Mammoth has no Trample");
        var ox = com.solegendary.reignofnether.registrars.EntityRegistrar.RAVAGER_UNIT.get().create(level);
        if (ox != null && ox.getAbilities().get().stream().anyMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.Trample))
            helper.fail("Trample leaked onto the ordinary Siege Ox");
        if (ox != null)
            ox.discard();
        mammoth.discard();
        helper.succeed();
    }

    /** The Sun Colossus is a Sunforged T3 with the Solar Lance, and the lance hits only hostiles in its lane. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void sun_colossus_lance_hits_its_lane(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var col = com.solegendary.reignofnether.registrars.EntityRegistrar.SUN_COLOSSUS_UNIT.get().create(level);
        var golem = com.solegendary.reignofnether.registrars.EntityRegistrar.IRON_GOLEM_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (col == null || golem == null || foe == null) {
            helper.fail("could not create units");
            return;
        }
        var f = com.solegendary.reignofnether.faction.Factions.getFaction(col);
        if (f == null || !f.equals(com.solegendary.reignofnether.faction.Factions.VILLAGERS))
            helper.fail("Sun Colossus is not Sunforged: " + f);
        if (col.getAbilities().get().stream().noneMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.SolarLance))
            helper.fail("Sun Colossus has no Solar Lance");
        if (golem.getAbilities().get().stream().anyMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.SolarLance))
            helper.fail("Solar Lance leaked onto the ordinary Iron Golem");
        golem.discard();
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 10));
        col.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() + 9.5, at.getY(), at.getZ() + 0.5, 0, 0);
        col.setOwnerName("gametest_sun");
        foe.setOwnerName("gametest_shade");
        level.addFreshEntity(col);
        level.addFreshEntity(foe);
        helper.runAfterDelay(3, () -> {
            var from = col.position().add(0, 1.5, 0);
            var dir = new net.minecraft.world.phys.Vec3(1, 0, 0);
            int hit = com.solegendary.reignofnether.ability.abilities.SolarLance.fire(level, col, "gametest_sun",
                from, from.add(dir.scale(com.solegendary.reignofnether.ability.abilities.SolarLance.LENGTH)), dir);
            if (hit < 1 || (foe.isAlive() && foe.getMaxHealth() - foe.getHealth() < 30))
                helper.fail("Solar Lance did not land on the enemy in its lane (hit " + hit + ", hp " + foe.getHealth() + ")");
            col.discard();
            foe.discard();
            helper.succeed();
        });
    }

    /** The Gravebound Bone Dragon exists, is Gravebound, flies (a Ghast brain), and follows T3 rule (a). */
    @GameTest(template = ARENA)
    public static void bone_dragon_is_a_proper_t3(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var dragon = com.solegendary.reignofnether.registrars.EntityRegistrar.BONE_DRAGON_UNIT.get().create(level);
        if (dragon == null) {
            helper.fail("could not create the Bone Dragon");
            return;
        }
        var f = com.solegendary.reignofnether.faction.Factions.getFaction(dragon);
        if (f == null || !f.equals(com.solegendary.reignofnether.faction.Factions.MONSTERS))
            helper.fail("Bone Dragon is not Gravebound: " + f);
        if (!(dragon instanceof net.minecraft.world.entity.FlyingMob))
            helper.fail("Bone Dragon does not fly");
        float ratio = (float) com.solegendary.reignofnether.resources.ResourceCosts.BONE_DRAGON.metal()
            / com.solegendary.reignofnether.resources.ResourceCosts.RAVAGER.metal();
        if (ratio < 4f || ratio > 6f)
            helper.fail("T3 rule (a): Bone Dragon should cost 4-6x the Siege Ox, is " + ratio + "x");
        if (dragon.getMaxHealth() < 800)
            helper.fail("Bone Dragon health " + dragon.getMaxHealth());
        dragon.discard();
        helper.succeed();
    }

    /** Bone Dragon kills rise: needs population room, leaves no wreck, crumbles on time. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void dragon_risen_skeletons_are_free_and_temporary(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_wyrm";
        BlockPos at = helper.absolutePos(new BlockPos(14, 2, 2));
        int cap = com.solegendary.reignofnether.unit.UnitServerEvents.maxPopulation;   // no supply now: test the unit cap
        com.solegendary.reignofnether.unit.UnitServerEvents.maxPopulation = 0;
        var none = com.solegendary.reignofnether.unit.DragonRaiseServerEvents.raise(level, owner, at);
        com.solegendary.reignofnether.unit.UnitServerEvents.maxPopulation = cap;
        if (none != null)
            helper.fail("raised a skeleton past the unit cap");
        com.solegendary.reignofnether.research.ResearchServerEvents.addCheat(owner, "foodforthought");
        var risen = com.solegendary.reignofnether.unit.DragonRaiseServerEvents.raise(level, owner, at);
        com.solegendary.reignofnether.research.ResearchServerEvents.removeCheat(owner, "foodforthought");
        if (risen == null) {
            helper.fail("no skeleton rose with population room");
            return;
        }
        var where = risen.position();   // tests run in parallel: look for a wreck HERE, not at the global count
        ((net.minecraft.world.entity.LivingEntity) risen).kill();
        helper.runAfterDelay(2, () -> {
            if (com.solegendary.reignofnether.resources.WreckServerEvents.getWrecks().stream()
                    .anyMatch(w -> !w.isRemoved() && w.position().distanceToSqr(where) < 4))
                helper.fail("a risen skeleton left a wreck (free metal)");
            helper.succeed();
        });
    }

    /** T2 constructors: one per faction, double build power and health, recognised as T2 workers. */
    @GameTest(template = ARENA)
    public static void t2_constructors_build_faster(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var types = List.of(com.solegendary.reignofnether.registrars.EntityRegistrar.ROYAL_ARCHITECT_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.EMBALMER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.BONEWRIGHT_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.ELDER_DRUID_UNIT.get());
        var factions = List.of(com.solegendary.reignofnether.faction.Factions.VILLAGERS,
            com.solegendary.reignofnether.faction.Factions.MONSTERS, com.solegendary.reignofnether.faction.Factions.PIGLINS,
            com.solegendary.reignofnether.faction.Factions.VERDANT_COURT);
        for (int i = 0; i < types.size(); i++) {
            var e = types.get(i).create(level);
            if (e == null) {
                helper.fail("could not create T2 constructor " + i);
                return;
            }
            if (!(((Object) e) instanceof com.solegendary.reignofnether.unit.interfaces.WorkerUnit w) || w.getBuildPower() < 1.99f)
                helper.fail(e.getType() + " is not a double-speed worker");
            if (!com.solegendary.reignofnether.unit.T2Workers.isT2Worker(e))
                helper.fail(e.getType() + " not recognised as a T2 worker");
            if (!factions.get(i).equals(com.solegendary.reignofnether.faction.Factions.getFaction((com.solegendary.reignofnether.unit.interfaces.Unit) e)))
                helper.fail(e.getType() + " has the wrong faction");
            if (((net.minecraft.world.entity.LivingEntity) e).getMaxHealth() < 49)
                helper.fail(e.getType() + " health " + ((net.minecraft.world.entity.LivingEntity) e).getMaxHealth());
            e.discard();
        }
        helper.succeed();
    }

    /** Capture points: an uncontested side takes a site over 20 s; an Ancient Mine then pays its owner metal. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void capture_point_is_taken_and_pays(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        // straight above this test's own arena: an offset column (it was -60/+60) can land over another test's arena,
        // and a stone pad at y=220 there broke extractor_and_windmill_fit_on_a_stamped_patch's heightmap (solidTop)
        int cx = base.getX(), cz = base.getZ(), y = 220;
        for (int dx = -6; dx <= 6; dx++)
            for (int dz = -6; dz <= 6; dz++)
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.STONE.defaultBlockState(), 3);
        var point = com.solegendary.reignofnether.startpos.CapturePointServerEvents.place(level, cx, cz,
            com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.MINE);
        String owner = "gametest_captor";
        var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 0, 0);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
        var unit = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (point == null || unit == null) {
            helper.fail("could not set up the capture point");
            return;
        }
        unit.moveTo(cx + 2.5, y + 1, cz + 0.5, 0, 0);
        unit.setOwnerName(owner);
        level.addFreshEntity(unit);
        helper.runAfterDelay(3, () -> {
            for (int i = 0; i < 25; i++)
                com.solegendary.reignofnether.startpos.CapturePointServerEvents.tick(level, 1f);
            if (!owner.equals(com.solegendary.reignofnether.startpos.CapturePointServerEvents.ownerOf(point)))
                helper.fail("25 uncontested seconds did not capture the site (progress "
                    + com.solegendary.reignofnether.startpos.CapturePointServerEvents.progressOf(point) + ")");
            if (pool.getMetal() <= 0)
                helper.fail("the captured Ancient Mine paid no metal");
            unit.discard();
            point.discard();
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
            helper.succeed();
        });
    }

    static final int SOAK_TICKS = 20 * 60 * 4;   // four game-minutes of bot-vs-bot play

    /**
     * Soak: a Sunforged (villagers), a Gravebound (monsters), a Verdant Court and a Tidewrought bot play each other
     * headless for four game-minutes. Each must grow (lay buildings or train units) and no brain may throw. The bases sit
     * ~1500 blocks from the test grid on dry ground so their battlefield (lanes, patches, ring wall <= 520 blocks)
     * never reaches the other tests' arenas. The Court rides in this soak rather than its own: bases started in the
     * same few seconds form ONE battlefield (BattlefieldSetup), so a second soak elsewhere would join this one into
     * an arena spanning the test grid. It also checks the Court's start through startRTSBot: three Seedshapers, the
     * first one the Grove Warden carrying Thornburst and Wildstride, and its capitol is a Heartwood Hall - and the
     * Tidewrought's the same way (three Shipwrights, the Admiral with Broadside and Riptide, a Wreck Harbour), which
     * also proves a Tidewrought bot builds (3+ buildings like every side) on a dry map - and the Stormcallers' (three
     * Geomancers, the Thunder Sage with Heaven's Judgement and Blink, a Jade Pagoda, 3+ buildings) as a fifth base.
     */
    @GameTest(template = ARENA, timeoutTicks = SOAK_TICKS + 600)
    public static void bots_play_each_other_without_errors(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String kingdom = "gametest_soak_kingdom", grave = "gametest_soak_grave", court = "gametest_soak_court",
            tide = "gametest_soak_tide", storm = "gametest_soak_storm";
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        // the first three in a row as before; the fourth (Tidewrought) beside them - asking for four in one row made
        // a dry strip 480 blocks long, which an ocean seed often doesn't have
        net.minecraft.world.phys.Vec3[] row = soakStarts(level, origin.getX() + 1500, origin.getZ(), 3);
        net.minecraft.world.phys.Vec3 extra = row == null ? null : soakExtraStart(level, row);
        // the fifth (Stormcallers) base beside the four, the same way (clear of all four)
        net.minecraft.world.phys.Vec3 extra2 = extra == null ? null
            : soakExtraStart(level, new net.minecraft.world.phys.Vec3[] { row[0], row[1], row[2], extra });
        if (row == null || extra == null || extra2 == null) {
            helper.fail("no dry ground for five bot bases near x=" + (origin.getX() + 1500));
            return;
        }
        net.minecraft.world.phys.Vec3[] starts = { row[0], row[1], row[2], extra, extra2 };
        var bots = com.solegendary.reignofnether.bot.BotServerEvents.brains;
        var failures = com.solegendary.reignofnether.bot.BotServerEvents.thinkFailures;
        List<String> names = List.of(kingdom, grave, court, tide, storm);
        var factions = List.of(com.solegendary.reignofnether.faction.Factions.VILLAGERS,
            com.solegendary.reignofnether.faction.Factions.MONSTERS, com.solegendary.reignofnether.faction.Factions.VERDANT_COURT,
            com.solegendary.reignofnether.faction.Factions.TIDEWROUGHT, com.solegendary.reignofnether.faction.Factions.STORMCALLERS);
        final int count = names.size();
        for (int i = 0; i < count; i++) {
            failures.remove(names.get(i));
            com.solegendary.reignofnether.player.PlayerServerEvents.startRTSBot(level, names.get(i), starts[i],
                factions.get(i), 0);
            bots.put(names.get(i), new com.solegendary.reignofnether.bot.BotPlayer(names.get(i), factions.get(i),
                com.solegendary.reignofnether.bot.BotPlayer.Difficulty.MEDIUM, BlockPos.containing(starts[i])));
        }
        for (String n : names)
            if (!com.solegendary.reignofnether.player.PlayerServerEvents.isRTSPlayer(n))
                helper.fail("startRTSBot did not add " + n + " to the match");
        List<String> problemsAtStart = new ArrayList<>();
        // the Court's start through the real bot start path: Seedshapers, the first promoted to the Grove Warden
        // (checked a moment later, once the spawned entities have joined the unit lists)
        helper.runAfterDelay(10, () -> {
            List<net.minecraft.world.entity.LivingEntity> courtStart = soakUnits(court);
            long seedshapers = courtStart.stream().filter(e -> e.getType()
                == com.solegendary.reignofnether.registrars.EntityRegistrar.SEEDSHAPER_UNIT.get()).count();
            if (seedshapers != 3 || courtStart.size() != 3)
                problemsAtStart.add("the Verdant bot should start with 3 Seedshapers, got " + courtStart.size() + " units ("
                    + seedshapers + " Seedshapers)");
            boolean thornburst = false, wildstride = false;
            for (var e : courtStart)
                if (com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(e)
                        && e instanceof com.solegendary.reignofnether.unit.interfaces.Unit u)
                    for (var a : u.getAbilities().get()) {
                        if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderDGun)
                            thornburst = true;
                        if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderAbility
                                && com.solegendary.reignofnether.ability.abilities.CommanderAbility.kindFor(u)
                                == com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.WILDSTRIDE)
                            wildstride = true;
                    }
            if (!thornburst || !wildstride)
                problemsAtStart.add("the Grove Warden is missing its D-gun (" + thornburst + ") or Wildstride (" + wildstride + ")");
            // and the Tidewrought's: three Shipwrights, the first the Admiral with Broadside and Riptide
            List<net.minecraft.world.entity.LivingEntity> tideStart = soakUnits(tide);
            long shipwrights = tideStart.stream().filter(e -> e.getType()
                == com.solegendary.reignofnether.registrars.EntityRegistrar.SHIPWRIGHT_UNIT.get()).count();
            if (shipwrights != 3 || tideStart.size() != 3)
                problemsAtStart.add("the Tidewrought bot should start with 3 Shipwrights, got " + tideStart.size() + " units ("
                    + shipwrights + " Shipwrights)");
            boolean broadside = false, riptide = false;
            for (var e : tideStart)
                if (com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(e)
                        && e instanceof com.solegendary.reignofnether.unit.interfaces.Unit u)
                    for (var a : u.getAbilities().get()) {
                        if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderDGun)
                            broadside = true;
                        if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderAbility
                                && com.solegendary.reignofnether.ability.abilities.CommanderAbility.kindFor(u)
                                == com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.RIPTIDE)
                            riptide = true;
                    }
            if (!broadside || !riptide)
                problemsAtStart.add("the Admiral is missing its D-gun (" + broadside + ") or Riptide (" + riptide + ")");
            // and the Stormcallers': three Geomancers, the first the Thunder Sage with Heaven's Judgement and Blink
            List<net.minecraft.world.entity.LivingEntity> stormStart = soakUnits(storm);
            long geomancers = stormStart.stream().filter(e -> e.getType()
                == com.solegendary.reignofnether.registrars.EntityRegistrar.GEOMANCER_UNIT.get()).count();
            if (geomancers != 3 || stormStart.size() != 3)
                problemsAtStart.add("the Stormcallers bot should start with 3 Geomancers, got " + stormStart.size() + " units ("
                    + geomancers + " Geomancers)");
            boolean judgement = false, blink = false;
            for (var e : stormStart)
                if (com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(e)
                        && e instanceof com.solegendary.reignofnether.unit.interfaces.Unit u)
                    for (var a : u.getAbilities().get()) {
                        if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderDGun
                                && com.solegendary.reignofnether.ability.abilities.CommanderDGun.kindFor(u)
                                == com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.JUDGEMENT)
                            judgement = true;
                        if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderAbility
                                && com.solegendary.reignofnether.ability.abilities.CommanderAbility.kindFor(u)
                                == com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.BLINK)
                            blink = true;
                    }
            if (!judgement || !blink)
                problemsAtStart.add("the Thunder Sage is missing Heaven's Judgement (" + judgement + ") or Blink (" + blink + ")");
        });
        int[] startUnits = new int[count];
        for (int i = 0; i < count; i++)
            startUnits[i] = soakUnits(names.get(i)).size();
        int[] maxBuildings = new int[count], maxUnits = new int[count];
        boolean[] courtHall = { false }, tideHarbour = { false }, stormPagoda = { false };
        // when the soak fails, say how each side's units left (death cause, place, time) and when a side left the
        // match - CI only shows the failure message, so this is the only way to see a stall's cause
        long t0 = level.getGameTime();
        List<String> leaves = java.util.Collections.synchronizedList(new ArrayList<>());
        long[] leftMatchAt = new long[count];
        java.util.function.Consumer<net.minecraftforge.event.entity.living.LivingDeathEvent> onDeath = evt -> {
            if (evt.getEntity().level() != level || !(evt.getEntity() instanceof com.solegendary.reignofnether.unit.interfaces.Unit u)
                    || !names.contains(u.getOwnerName()) || leaves.size() >= 12)
                return;
            var le = evt.getEntity();
            var src = evt.getSource();
            leaves.add(u.getOwnerName().substring(14) + " " + net.minecraft.world.entity.EntityType.getKey(le.getType()).getPath()
                + " died t" + (level.getGameTime() - t0) + " " + src.getMsgId()
                + (src.getEntity() != null ? " by " + net.minecraft.world.entity.EntityType.getKey(src.getEntity().getType()).getPath() : "")
                + " at " + le.blockPosition().toShortString()
                + " cmdr=" + com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(le));
        };
        java.util.function.Consumer<net.minecraftforge.event.entity.EntityLeaveLevelEvent> onLeave = evt -> {
            if (evt.getLevel() != level || !(evt.getEntity() instanceof com.solegendary.reignofnether.unit.interfaces.Unit u)
                    || !names.contains(u.getOwnerName()) || leaves.size() >= 12)
                return;
            var e = evt.getEntity();
            if (e.getRemovalReason() == net.minecraft.world.entity.Entity.RemovalReason.KILLED)
                return;   // reported by onDeath
            leaves.add(u.getOwnerName().substring(14) + " " + net.minecraft.world.entity.EntityType.getKey(e.getType()).getPath()
                + " left t" + (level.getGameTime() - t0) + " " + e.getRemovalReason() + " at " + e.blockPosition().toShortString());
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,
            false, net.minecraftforge.event.entity.living.LivingDeathEvent.class, onDeath);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGHEST,
            false, net.minecraftforge.event.entity.EntityLeaveLevelEvent.class, onLeave);
        Runnable sample = () -> {
            for (int i = 0; i < count; i++)
                if (leftMatchAt[i] == 0 && !com.solegendary.reignofnether.player.PlayerServerEvents.isRTSPlayer(names.get(i)))
                    leftMatchAt[i] = level.getGameTime() - t0;
            for (var bp : soakBuildings(court))
                if (bp.getBuilding() == Buildings.HEARTWOOD_HALL)
                    courtHall[0] = true;
            for (var bp : soakBuildings(tide))
                if (bp.getBuilding() == Buildings.WRECK_HARBOUR)
                    tideHarbour[0] = true;
            for (var bp : soakBuildings(storm))
                if (bp.getBuilding() == Buildings.JADE_PAGODA)
                    stormPagoda[0] = true;
            for (int i = 0; i < count; i++) {
                maxBuildings[i] = Math.max(maxBuildings[i], soakBuildings(names.get(i)).size());
                maxUnits[i] = Math.max(maxUnits[i], soakUnits(names.get(i)).size());
            }
        };
        // a fresh Runnable per sample: GameTestInfo keys its delayed tasks by the Runnable itself, so scheduling the
        // same instance 239 times kept only the last one (the "max" counts were really just the final state)
        for (int t = 20; t < SOAK_TICKS; t += 20)
            helper.runAfterDelay(t, new Runnable() {
                @Override
                public void run() {
                    sample.run();
                }
            });
        helper.runAfterDelay(SOAK_TICKS, () -> {
            sample.run();
            List<String> problems = new ArrayList<>(problemsAtStart);
            if (!courtHall[0])
                problems.add(court + " never laid its Heartwood Hall");
            if (!tideHarbour[0])
                problems.add(tide + " never laid its Wreck Harbour");
            if (!stormPagoda[0])
                problems.add(storm + " never laid its Jade Pagoda");
            for (int i = 0; i < count; i++) {
                String n = names.get(i);
                Throwable err = failures.get(n);
                if (err != null)
                    problems.add(n + " think threw " + err);
                // capitol + at least two more: a bot that stops after its capitol has stalled (the old bad-patch bug)
                if (maxBuildings[i] < 3 || maxUnits[i] <= startUnits[i])
                    problems.add(n + " stalled (buildings " + maxBuildings[i] + ", units "
                        + startUnits[i] + " -> " + maxUnits[i] + ")");
            }
            for (int i = 0; i < count; i++)
                ReignOfNether.LOGGER.info("[Soak] after {} ticks: {} buildings {} units {} (start {})",
                    SOAK_TICKS, names.get(i), maxBuildings[i], maxUnits[i], startUnits[i]);
            // clean up: leave the match, forget the brains, drop the armies (buildings stay, ownerless and far away)
            List<net.minecraft.world.entity.LivingEntity> leftovers = new ArrayList<>();
            for (String n : names)
                leftovers.addAll(soakUnits(n));
            for (String n : names) {
                com.solegendary.reignofnether.player.PlayerServerEvents.defeat(n, "gametest finished");
                bots.remove(n);
                failures.remove(n);
            }
            leftovers.forEach(net.minecraft.world.entity.Entity::discard);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(onDeath);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(onLeave);
            if (!problems.isEmpty()) {
                StringBuilder diag = new StringBuilder(" | left match at t: ");
                for (int i = 0; i < count; i++)
                    diag.append(names.get(i).substring(14)).append('=').append(leftMatchAt[i]).append(' ');
                diag.append("| starts:");
                for (var st : starts)
                    diag.append(' ').append(BlockPos.containing(st).toShortString());
                diag.append(" | unit exits: ").append(leaves);
                helper.fail(String.join("; ", problems) + diag);
            }
            helper.succeed();
        });
    }

    /** Two dry, fairly flat base sites 160 blocks apart, searched eastward from (x, z); null if none found. */
    static net.minecraft.world.phys.Vec3[] soakStarts(ServerLevel level, int x, int z) {
        return soakStarts(level, x, z, 2);
    }

    /** {@code count} dry, fairly flat base sites 160 blocks apart in a row along z; null if none found. */
    static net.minecraft.world.phys.Vec3[] soakStarts(ServerLevel level, int x, int z, int count) {
        // the seed is random per CI run, so one line of x can be all ocean: try a few parallel lines before giving up
        for (int bz : new int[] { z, z + 480, z - 480, z + 960, z - 960, z + 1440, z - 1440, z + 1920 })
            for (int step = 0; step < 16; step++) {
                int bx = x + step * 96;
                net.minecraft.world.phys.Vec3[] out = new net.minecraft.world.phys.Vec3[count];
                boolean ok = true;
                for (int k = 0; k < count && ok; k++) {
                    out[k] = soakDrySpot(level, bx, bz + 160 * k);
                    ok = out[k] != null;
                }
                if (ok)
                    return out;
            }
        return null;
    }

    /**
     * One more dry base site next to a row from {@link #soakStarts}: tried beside each base (160 blocks off to either
     * side, then diagonally) so it stays in the same small battlefield, at least 150 blocks from every other base.
     */
    static net.minecraft.world.phys.Vec3 soakExtraStart(ServerLevel level, net.minecraft.world.phys.Vec3[] row) {
        int[][] offsets = { {160, 0}, {-160, 0}, {160, 80}, {-160, 80}, {160, -80}, {-160, -80}, {0, -160}, {0, 160},
            {240, 0}, {-240, 0} };
        for (int[] o : offsets)
            for (var base : row) {
                var spot = soakDrySpot(level, (int) base.x + o[0], (int) base.z + o[1]);
                if (spot == null)
                    continue;
                boolean clear = true;
                for (var other : row)
                    if (Math.hypot(other.x - spot.x, other.z - spot.z) < 150)
                        clear = false;
                if (clear)
                    return spot;
            }
        return null;
    }

    static net.minecraft.world.phys.Vec3 soakDrySpot(ServerLevel level, int x, int z) {
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int dx = -12; dx <= 12; dx += 12)
            for (int dz = -12; dz <= 12; dz += 12) {
                level.getChunk((x + dx) >> 4, (z + dz) >> 4);   // generate it, or the heightmap reads the void (forests: ignore leaves, or tree tops read as 10-block cliffs)
                int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
                if (!level.getBlockState(new BlockPos(x + dx, y - 1, z + dz)).getFluidState().isEmpty())
                    return null;
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        if (maxY - minY > 10)
            return null;
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new net.minecraft.world.phys.Vec3(x + 0.5, y, z + 0.5);
    }

    static List<net.minecraft.world.entity.LivingEntity> soakUnits(String owner) {
        List<net.minecraft.world.entity.LivingEntity> out = new ArrayList<>();
        for (var le : com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits())
            if (le instanceof com.solegendary.reignofnether.unit.interfaces.Unit u && owner.equals(u.getOwnerName())
                    && le.isAlive())
                out.add(le);
        return out;
    }

    static List<com.solegendary.reignofnether.building.BuildingPlacement> soakBuildings(String owner) {
        List<com.solegendary.reignofnether.building.BuildingPlacement> out = new ArrayList<>();
        for (var bp : com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings())
            if (owner.equals(bp.ownerName) && !bp.isDestroyedServerside)
                out.add(bp);
        return out;
    }

    /** Each faction's bot kit points at its real T2 lab, and that lab trains the kit's T2 constructor and T2 army. */
    @GameTest(template = ARENA)
    public static void bot_kits_use_their_t2_lab(GameTestHelper helper) {
        for (var f : List.of(com.solegendary.reignofnether.faction.Factions.VILLAGERS,
                com.solegendary.reignofnether.faction.Factions.MONSTERS, com.solegendary.reignofnether.faction.Factions.PIGLINS,
                com.solegendary.reignofnether.faction.Factions.VERDANT_COURT)) {
            var kit = com.solegendary.reignofnether.bot.BotPlayer.kitFor(f);
            if (!(kit.t2Lab() instanceof com.solegendary.reignofnether.building.production.ProductionBuilding lab)) {
                helper.fail(f.getName() + ": T2 lab " + kit.t2Lab() + " is not a production building");
                return;
            }
            if (com.solegendary.reignofnether.unit.T2Workers.isT3Lab(lab))
                helper.fail(f.getName() + ": the T2 lab is a T3 lab");
            var items = lab.productions.get();
            if (!items.contains(kit.t2Worker()))
                helper.fail(f.getName() + ": " + lab.structureName + " does not train the kit's T2 constructor");
            for (var item : kit.t2Army())
                if (!items.contains(item))
                    helper.fail(f.getName() + ": " + lab.structureName + " does not train " + item);
        }
        helper.succeed();
    }

    /** The server refuses a T3 lab placed by T1 workers only; a T2 constructor among the builders passes. */
    @GameTest(template = ARENA)
    public static void t3_lab_needs_a_t2_builder_on_the_server(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 2, 8));
        var t1 = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        var t2 = com.solegendary.reignofnether.registrars.EntityRegistrar.ROYAL_ARCHITECT_UNIT.get().create(level);
        if (t1 == null || t2 == null) {
            helper.fail("could not create the workers");
            return;
        }
        t1.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);
        t2.moveTo(base.getX() + 2.5, base.getY(), base.getZ() + 0.5, 0, 0);
        level.addFreshEntity(t1);
        level.addFreshEntity(t2);
        int[] t1Only = { t1.getId() }, withT2 = { t1.getId(), t2.getId() };
        for (Building lab : List.of(Buildings.CASTLE, Buildings.STRONGHOLD, Buildings.FORTRESS)) {
            if (com.solegendary.reignofnether.unit.T2Workers.buildersMayPlace(level, lab, t1Only))
                helper.fail(lab.structureName + " may be placed by a T1 worker alone");
            if (!com.solegendary.reignofnether.unit.T2Workers.buildersMayPlace(level, lab, withT2))
                helper.fail(lab.structureName + " refused with a T2 constructor among the builders");
        }
        if (!com.solegendary.reignofnether.unit.T2Workers.buildersMayPlace(level, Buildings.ARCANE_TOWER, t1Only))
            helper.fail("a T2 lab must not need a T2 constructor");
        // the real placement path: a T1-only Castle is refused before it ever reaches the terrain checks
        String owner = "gametest_t3_owner";
        BlockPos pos = base.offset(80, 0, -80);
        var placed = com.solegendary.reignofnether.building.BuildingServerEvents.placeBuilding(Buildings.CASTLE, pos,
            Rotation.NONE, owner, t1Only, false, false, false, true);
        if (placed != null) {
            com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(placed);
            helper.fail("placeBuilding accepted a Castle from a T1 worker");
        }
        t1.discard();
        t2.discard();
        helper.succeed();
    }

    /** Ground column for the slope test: dirt under a grass top at topY, solid down to bottomY. */
    private static void groundColumn(ServerLevel level, int x, int z, int bottomY, int topY) {
        for (int y = bottomY; y < topY; y++)
            level.setBlock(new BlockPos(x, y, z), Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x, topY, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
    }

    /**
     * BAR forgives gentle slopes: a 1-block step under a (3x3) wind generator footprint must be accepted from either
     * side - terrain poking into the bottom layer from the low side, a 1-block gap from the high side - and a tuft of
     * grass in the way doesn't matter. A 4-block cliff must still be refused from the top (hanging over the drop)
     * and from the bottom (clipping the cliff face). Origin = the ground block, the footprint is x/z in [o, o+2].
     */
    @GameTest(template = ARENA)
    public static void wind_generator_accepts_a_step_but_not_a_cliff(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int g = 245;
        // its own column (the four pads span cx-2..cx+12, inside this arena): the old -40/-40 offset dropped dirt
        // columns at y=238-246 over whichever arena the grid put there, above any platform test's pad
        int cx = base.getX() - 6, cz = base.getZ() - 6;
        final int step = 8;   // pads 7 wide, one air column between them
        Building wind = Buildings.WIND_GENERATOR_VILLAGERS;

        // 1) step, placed on the LOW side: columns x+1..x+2 are one block higher (inside the bottom layer)
        int ax = cx, az = cz;
        for (int dx = -2; dx <= 4; dx++)
            for (int dz = -2; dz <= 4; dz++)
                groundColumn(level, ax + dx, az + dz, g - 3, dx >= 1 ? g + 1 : g);
        level.setBlock(new BlockPos(ax, g + 1, az + 1), Blocks.GRASS.defaultBlockState(), 2);
        String err = BuildingValidators.getPlacementValidityError(level, wind, new BlockPos(ax, g, az), "tester",
            Rotation.NONE, false, false, true);
        if (err != null)
            helper.fail("1-block step refused from the low side: " + err);

        // 2) step, placed on the HIGH side: only column x is high, x+1..x+2 have a 1-block gap under them
        int bx = cx + step, bz = cz;
        for (int dx = -2; dx <= 4; dx++)
            for (int dz = -2; dz <= 4; dz++)
                groundColumn(level, bx + dx, bz + dz, g - 3, dx <= 0 ? g + 1 : g);
        err = BuildingValidators.getPlacementValidityError(level, wind, new BlockPos(bx, g + 1, bz), "tester",
            Rotation.NONE, false, false, true);
        if (err != null)
            helper.fail("1-block step refused from the high side: " + err);

        // 3) 4-block cliff, placed on TOP: column x+2 hangs over a 4-block drop
        int ccx = cx, ccz = cz + step;
        for (int dx = -2; dx <= 4; dx++)
            for (int dz = -2; dz <= 4; dz++)
                groundColumn(level, ccx + dx, ccz + dz, g - 7, dx <= 1 ? g : g - 4);
        err = BuildingValidators.getPlacementValidityError(level, wind, new BlockPos(ccx, g, ccz), "tester",
            Rotation.NONE, false, false, true);
        if (err == null)
            helper.fail("wind generator accepted hanging over a 4-block cliff");

        // 4) the same cliff from the BOTTOM: the footprint would be buried 4 blocks into the cliff face
        int dxo = cx + step, dzo = cz + step;
        for (int dx = -2; dx <= 4; dx++)
            for (int dz = -2; dz <= 4; dz++)
                groundColumn(level, dxo + dx, dzo + dz, g - 7, dx >= 1 ? g : g - 4);
        err = BuildingValidators.getPlacementValidityError(level, wind, new BlockPos(dxo, g - 4, dzo), "tester",
            Rotation.NONE, false, false, true);
        if (err == null)
            helper.fail("wind generator accepted at the foot of a 4-block cliff");

        helper.succeed();
    }

    /** Crypt Tide: the eruption roots and hurts an enemy inside the ring, and leaves a friend inside it alone. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void crypt_tide_roots_foes_not_friends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_crypt_tide";
        var wraith = com.solegendary.reignofnether.registrars.EntityRegistrar.WRAITH_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get().create(level);
        var friend = com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_UNIT.get().create(level);
        if (wraith == null || foe == null || friend == null) {
            helper.fail("could not create crypt tide units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(8, 2, 8));
        wraith.moveTo(at.getX() - 5.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5, 0, 0);
        friend.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5, 0, 0);
        wraith.setOwnerName(owner);
        friend.setOwnerName(owner);
        foe.setOwnerName("gametest_crypt_tide_foe");
        for (var e : List.<net.minecraft.world.entity.LivingEntity>of(wraith, foe, friend))
            level.addFreshEntity(e);
        helper.runAfterDelay(3, () -> {
            float foeHp = foe.getHealth(), friendHp = friend.getHealth();
            var centre = new net.minecraft.world.phys.Vec3(at.getX() + 0.5, foe.getY(), at.getZ() + 0.5);
            int hit = com.solegendary.reignofnether.ability.abilities.CryptTide.erupt(level, wraith, owner, centre);
            if (hit < 1)
                helper.fail("Crypt Tide caught no enemy");
            if (!foe.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN))
                helper.fail("Crypt Tide did not root the enemy");
            if (foe.isAlive() && foe.getHealth() >= foeHp)
                helper.fail("Crypt Tide did not hurt the enemy");
            if (friend.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN))
                helper.fail("Crypt Tide rooted a friendly unit");
            if (friend.getHealth() < friendHp)
                helper.fail("Crypt Tide hurt a friendly unit");
            for (var e : List.<net.minecraft.world.entity.LivingEntity>of(wraith, foe, friend))
                e.discard();
            helper.succeed();
        });
    }

    /** War-Drums: the speed modifier goes on the drummer and its friends nearby, never on an enemy beside them. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void war_drums_speed_friends_only(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_war_drums";
        var drummer = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get().create(level);
        var friend = com.solegendary.reignofnether.registrars.EntityRegistrar.BRUTE_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get().create(level);
        if (drummer == null || friend == null || foe == null) {
            helper.fail("could not create war drums units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
        drummer.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        friend.moveTo(at.getX() + 4.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 4.5, 0, 0);
        drummer.setOwnerName(owner);
        friend.setOwnerName(owner);
        foe.setOwnerName("gametest_war_drums_foe");
        for (var e : List.<net.minecraft.world.entity.LivingEntity>of(drummer, friend, foe))
            level.addFreshEntity(e);
        var speed = net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED;
        helper.runAfterDelay(3, () -> {
            double friendBase = friend.getAttributeValue(speed), foeBase = foe.getAttributeValue(speed);
            var drummed = com.solegendary.reignofnether.ability.abilities.WarDrums.rally(level, drummer, owner);
            if (!drummed.contains(friend) || !com.solegendary.reignofnether.ability.abilities.WarDrums.isDrummed(friend))
                helper.fail("War-Drums did not reach a friendly unit 4 blocks away");
            if (!com.solegendary.reignofnether.ability.abilities.WarDrums.isDrummed(drummer))
                helper.fail("War-Drums did not buff the drummer itself");
            if (friend.getAttributeValue(speed) <= friendBase + 0.0001)
                helper.fail("War-Drums gave the friend no speed: " + friendBase + " -> " + friend.getAttributeValue(speed));
            if (drummed.contains(foe) || com.solegendary.reignofnether.ability.abilities.WarDrums.isDrummed(foe)
                    || foe.getAttributeValue(speed) > foeBase + 0.0001)
                helper.fail("War-Drums buffed an enemy unit");
            for (var e : List.<net.minecraft.world.entity.LivingEntity>of(drummer, friend, foe))
                e.discard();
            helper.succeed();
        });
    }

    /** Bastion Aegis: a projectile hit on a friend inside the dome is soaked by the dome, not the friend. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void bastion_aegis_soaks_a_projectile_for_a_friend(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_bastion_aegis";
        var evoker = com.solegendary.reignofnether.registrars.EntityRegistrar.EVOKER_UNIT.get().create(level);
        var friend = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (evoker == null || friend == null) {
            helper.fail("could not create aegis units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(11, 2, 11));
        evoker.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        friend.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5, 0, 0);
        evoker.setOwnerName(owner);
        friend.setOwnerName(owner);
        level.addFreshEntity(evoker);
        level.addFreshEntity(friend);
        helper.runAfterDelay(3, () -> {
            var dome = com.solegendary.reignofnether.ability.abilities.BastionAegis.raise(level, evoker, owner, evoker.position());
            float hp = friend.getHealth();
            // a thrown projectile with no shooter: plain projectile damage, untouched by RoN's unit damage rewrite
            friend.hurt(level.damageSources().thrown(null, null), 10f);
            if (friend.getHealth() < hp - 0.01f)
                helper.fail("the dome let a projectile hit through: " + hp + " -> " + friend.getHealth());
            if (dome.remaining > com.solegendary.reignofnether.ability.abilities.BastionAegis.CAPACITY - 9.99f)
                helper.fail("the dome did not pay for the hit: " + dome.remaining + " left");
            com.solegendary.reignofnether.ability.abilities.BastionAegis.dispel(dome);
            evoker.discard();
            friend.discard();
            helper.succeed();
        });
    }

    /** Totem of the Pack: a friend dying by the totem calls up a spectral wolf for the owner; an enemy dying there doesn't. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void totem_of_the_pack_answers_friends_not_foes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_pack_totem";
        var bonewright = com.solegendary.reignofnether.registrars.EntityRegistrar.BONEWRIGHT_UNIT.get().create(level);
        var grunt = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get().create(level);
        var friend = com.solegendary.reignofnether.registrars.EntityRegistrar.BRUTE_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (bonewright == null || grunt == null || friend == null || foe == null) {
            helper.fail("could not create totem units");
            return;
        }
        // the totem lives on the Bonewright's own ability set, not on the shared Grunt one
        if (bonewright.getAbilities().get().stream().noneMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.TotemOfThePack))
            helper.fail("the Bonewright has no Totem of the Pack");
        if (grunt.getAbilities().get().stream().anyMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.TotemOfThePack))
            helper.fail("a plain Grunt got the Bonewright's totem");
        grunt.discard();
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 12));
        bonewright.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        friend.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() + 0.5, at.getY(), at.getZ() - 2.5, 0, 0);
        bonewright.setOwnerName(owner);
        friend.setOwnerName(owner);
        foe.setOwnerName("gametest_pack_totem_foe");
        for (var e : List.<net.minecraft.world.entity.LivingEntity>of(bonewright, friend, foe))
            level.addFreshEntity(e);
        helper.runAfterDelay(3, () -> {
            var totem = com.solegendary.reignofnether.ability.abilities.TotemOfThePack.plant(level, bonewright, owner, bonewright.position());
            var wolf = com.solegendary.reignofnether.ability.abilities.TotemOfThePack.answerDeath(level, friend);
            var none = com.solegendary.reignofnether.ability.abilities.TotemOfThePack.answerDeath(level, foe);
            if (wolf == null)
                helper.fail("no spectral wolf rose for a friend dying by the totem");
            else if (!(wolf instanceof com.solegendary.reignofnether.unit.interfaces.Unit wu) || !owner.equals(wu.getOwnerName())
                    || !com.solegendary.reignofnether.ability.abilities.TotemOfThePack.isSpectral(wolf))
                helper.fail("the wolf is not a spectral wolf of the totem's owner");
            if (none != null) {
                none.discard();
                helper.fail("an enemy dying by the totem called up a wolf");
            }
            if (wolf != null)
                wolf.discard();
            com.solegendary.reignofnether.ability.abilities.TotemOfThePack.dispel(totem);
            for (var e : List.<net.minecraft.world.entity.LivingEntity>of(bonewright, friend, foe))
                e.discard();
            helper.succeed();
        });
    }

    /** Magma Rupture: the eruption burns and hurts an enemy on the crack, and leaves a friend on it alone. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void magma_rupture_burns_foes_not_friends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_magma_rupture";
        var blaze = com.solegendary.reignofnether.registrars.EntityRegistrar.BLAZE_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        var friend = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get().create(level);
        if (blaze == null || foe == null || friend == null) {
            helper.fail("could not create magma rupture units");
            return;
        }
        if (blaze.getAbilities().get().stream().noneMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.MagmaRupture))
            helper.fail("the Blaze has no Magma Rupture");
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 6));
        blaze.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() + 5.5, at.getY(), at.getZ() + 0.5, 0, 0);
        friend.moveTo(at.getX() + 8.5, at.getY(), at.getZ() + 0.5, 0, 0);
        blaze.setOwnerName(owner);
        friend.setOwnerName(owner);
        foe.setOwnerName("gametest_magma_rupture_foe");
        for (var e : List.<net.minecraft.world.entity.LivingEntity>of(blaze, foe, friend))
            level.addFreshEntity(e);
        helper.runAfterDelay(3, () -> {
            float foeHp = foe.getHealth(), friendHp = friend.getHealth();
            var from = new net.minecraft.world.phys.Vec3(at.getX() + 0.5, foe.getY(), at.getZ() + 0.5);
            var to = from.add(com.solegendary.reignofnether.ability.abilities.MagmaRupture.LENGTH, 0, 0);
            int hit = com.solegendary.reignofnether.ability.abilities.MagmaRupture.erupt(level, blaze, owner, from, to);
            if (hit < 1)
                helper.fail("Magma Rupture caught no enemy");
            if (foe.getRemainingFireTicks() <= 0)
                helper.fail("Magma Rupture did not set the enemy on fire");
            if (foe.isAlive() && foe.getHealth() >= foeHp)
                helper.fail("Magma Rupture did not hurt the enemy");
            if (friend.getRemainingFireTicks() > 0 || friend.getHealth() < friendHp)
                helper.fail("Magma Rupture burned a friendly unit");
            for (var e : List.<net.minecraft.world.entity.LivingEntity>of(blaze, foe, friend))
                e.discard();
            helper.succeed();
        });
    }

    /** Holy Bell: enemies in range glow, friends in range don't. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void holy_bell_reveals_foes_not_friends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_holy_bell";
        var architect = com.solegendary.reignofnether.registrars.EntityRegistrar.ROYAL_ARCHITECT_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_UNIT.get().create(level);
        var friend = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (architect == null || foe == null || friend == null) {
            helper.fail("could not create holy bell units");
            return;
        }
        if (architect.getAbilities().get().stream().noneMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.HolyBell))
            helper.fail("the Royal Architect has no Holy Bell");
        BlockPos at = helper.absolutePos(new BlockPos(13, 2, 13));
        architect.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() - 9.5, at.getY(), at.getZ() + 0.5, 0, 0);
        friend.moveTo(at.getX() + 0.5, at.getY(), at.getZ() - 4.5, 0, 0);
        architect.setOwnerName(owner);
        friend.setOwnerName(owner);
        foe.setOwnerName("gametest_holy_bell_foe");
        for (var e : List.<net.minecraft.world.entity.LivingEntity>of(architect, foe, friend))
            level.addFreshEntity(e);
        helper.runAfterDelay(3, () -> {
            // an arena-sized pulse (see HolyBell.ring with a radius): the full 24 blocks reach the neighbouring tests
            var revealed = com.solegendary.reignofnether.ability.abilities.HolyBell.ring(level, architect, owner, 11);
            if (!revealed.contains(foe) || !foe.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING))
                helper.fail("Holy Bell did not reveal an enemy 10 blocks away");
            if (revealed.contains(friend) || friend.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING))
                helper.fail("Holy Bell marked a friendly unit");
            if (architect.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING))
                helper.fail("Holy Bell marked the ringer");
            for (var e : List.<net.minecraft.world.entity.LivingEntity>of(architect, foe, friend))
                e.discard();
            helper.succeed();
        });
    }

    /** Holy Bell: the fog lifts around a rung-out enemy for the caster AND the caster's ally, never for the foe. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void holy_bell_reveals_to_allies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_bell_ally_me", ally = "gametest_bell_ally_ally", foeOwner = "gametest_bell_ally_foe";
        var architect = com.solegendary.reignofnether.registrars.EntityRegistrar.ROYAL_ARCHITECT_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_UNIT.get().create(level);
        if (architect == null || foe == null) {
            helper.fail("could not create holy bell units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(13, 2, 13));
        architect.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() - 7.5, at.getY(), at.getZ() + 0.5, 0, 0);
        architect.setOwnerName(owner);
        foe.setOwnerName(foeOwner);
        level.addFreshEntity(architect);
        level.addFreshEntity(foe);
        com.solegendary.reignofnether.alliance.AlliancesServerEvents.addAlliance(owner, ally);
        helper.runAfterDelay(3, () -> {
            try {
                com.solegendary.reignofnether.ability.abilities.HolyBell.ring(level, architect, owner, 9);
                if (!com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isUnitRevealedTo(foe.getId(), owner))
                    helper.fail("Holy Bell did not lift the caster's fog around the enemy");
                else if (!com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isUnitRevealedTo(foe.getId(), ally))
                    helper.fail("Holy Bell did not lift the ally's fog around the enemy");
                else if (com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isUnitRevealedTo(foe.getId(), foeOwner))
                    helper.fail("Holy Bell revealed the enemy to its own owner");
                else
                    helper.succeed();
            } finally {
                com.solegendary.reignofnether.alliance.AlliancesServerEvents.removeAlliance(owner, ally);
                architect.discard();
                foe.discard();
            }
        });
    }

    /** Withering Fog: a second in the cloud hurts and weakens an enemy inside, and leaves a friend inside alone. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void withering_fog_withers_foes_not_friends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_withering_fog";
        var dragon = com.solegendary.reignofnether.registrars.EntityRegistrar.BONE_DRAGON_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        var friend = com.solegendary.reignofnether.registrars.EntityRegistrar.SKELETON_UNIT.get().create(level);
        if (dragon == null || foe == null || friend == null) {
            helper.fail("could not create withering fog units");
            return;
        }
        if (dragon.getAbilities().get().stream().noneMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.WitheringFog))
            helper.fail("the Bone Dragon has no Withering Fog");
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 3));
        dragon.moveTo(at.getX() + 0.5, at.getY() + 4, at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() + 6.5, at.getY(), at.getZ() + 0.5, 0, 0);
        friend.moveTo(at.getX() + 10.5, at.getY(), at.getZ() + 0.5, 0, 0);
        dragon.setOwnerName(owner);
        friend.setOwnerName(owner);
        foe.setOwnerName("gametest_withering_fog_foe");
        for (var e : List.<net.minecraft.world.entity.LivingEntity>of(dragon, foe, friend))
            level.addFreshEntity(e);
        helper.runAfterDelay(3, () -> {
            float foeHp = foe.getHealth(), friendHp = friend.getHealth();
            var from = new net.minecraft.world.phys.Vec3(at.getX() + 0.5, foe.getY() + 0.5, at.getZ() + 0.5);
            var to = from.add(com.solegendary.reignofnether.ability.abilities.WitheringFog.LENGTH, 0, 0);
            var cloud = com.solegendary.reignofnether.ability.abilities.WitheringFog.breathe(level, dragon, owner, from, to);
            // one pulse by hand: the test must not depend on the server tick lining up with the cloud's second
            int hit = com.solegendary.reignofnether.ability.abilities.WitheringFog.pulse(cloud);
            com.solegendary.reignofnether.ability.abilities.WitheringFog.dispel(cloud);
            if (hit < 1)
                helper.fail("Withering Fog touched no enemy");
            if (!foe.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS))
                helper.fail("Withering Fog did not weaken the enemy");
            if (foe.isAlive() && foe.getHealth() >= foeHp)
                helper.fail("Withering Fog did not hurt the enemy");
            if (friend.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS) || friend.getHealth() < friendHp)
                helper.fail("Withering Fog hurt a friendly unit");
            for (var e : List.<net.minecraft.world.entity.LivingEntity>of(dragon, foe, friend))
                e.discard();
            helper.succeed();
        });
    }

    /**
     * Sunrise Sortie: the Lord Marshal (and its melee escort) charges forward along the aimed line, hurts the enemy
     * it runs through, and leaves a friend standing on the path alone. The charge is stepped by hand.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void sunrise_sortie_charges_through_foes_not_friends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_sunrise_sortie";
        var marshal = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        var escort = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        var friend = com.solegendary.reignofnether.registrars.EntityRegistrar.PILLAGER_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (marshal == null || escort == null || friend == null || foe == null) {
            helper.fail("could not create sunrise sortie units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 7));
        marshal.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        escort.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5, 0, 0);
        friend.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() + 6.5, at.getY(), at.getZ() + 0.5, 0, 0);
        marshal.setOwnerName(owner);
        escort.setOwnerName(owner);
        friend.setOwnerName(owner);
        foe.setOwnerName("gametest_sunrise_sortie_foe");
        for (var e : List.<net.minecraft.world.entity.LivingEntity>of(marshal, escort, friend, foe))
            level.addFreshEntity(e);
        com.solegendary.reignofnether.player.CommanderServerEvents.makeCommander(marshal);
        if (marshal.getAbilities().get().stream().noneMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.SunriseSortie))
            helper.fail("the Sunforged commander has no Sunrise Sortie");
        helper.runAfterDelay(3, () -> {
            float foeHp = foe.getHealth(), friendHp = friend.getHealth();
            double x0 = marshal.getX();
            var charge = com.solegendary.reignofnether.ability.abilities.SunriseSortie.begin(level, marshal, owner,
                new net.minecraft.world.phys.Vec3(1, 0, 0));
            // stepped by hand: the test must not depend on the server tick running the charge
            com.solegendary.reignofnether.ability.abilities.SunriseSortie.cancel(charge);
            for (int i = 0; i < com.solegendary.reignofnether.ability.abilities.SunriseSortie.DURATION_TICKS; i++)
                com.solegendary.reignofnether.ability.abilities.SunriseSortie.step(charge);
            try {
                if (!charge.riders.contains(escort))
                    helper.fail("the melee escort did not join the sortie");
                if (charge.riders.contains(friend))
                    helper.fail("a ranged unit was swept into the melee sortie");
                if (marshal.getX() - x0 < 8)
                    helper.fail("the Marshal only charged " + (marshal.getX() - x0) + " blocks");
                if (foe.isAlive() && foe.getHealth() >= foeHp)
                    helper.fail("Sunrise Sortie did not hurt the enemy on its path");
                if (friend.getHealth() < friendHp)
                    helper.fail("Sunrise Sortie hurt a friendly unit on its path");
            } finally {
                for (var e : List.<net.minecraft.world.entity.LivingEntity>of(marshal, escort, friend, foe))
                    e.discard();
            }
            helper.succeed();
        });
    }

    /** Soul Wisps: Gravebound workers by the Embalmer reclaim faster, the wisps lash an enemy in reach and never a friend. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void soul_wisps_boost_reclaim_and_hit_foes_not_friends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_soul_wisps";
        var embalmer = com.solegendary.reignofnether.registrars.EntityRegistrar.EMBALMER_UNIT.get().create(level);
        var digger = com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_VILLAGER_UNIT.get().create(level);
        var farDigger = com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_VILLAGER_UNIT.get().create(level);
        var friend = com.solegendary.reignofnether.registrars.EntityRegistrar.PILLAGER_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (embalmer == null || digger == null || farDigger == null || friend == null || foe == null) {
            helper.fail("could not create soul wisps units");
            return;
        }
        // the wisps live on the Embalmer's own ability set, not on the shared Gravedigger one
        if (embalmer.getAbilities().get().stream().noneMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.SoulWisps))
            helper.fail("the Embalmer has no Soul Wisps");
        if (digger.getAbilities().get().stream().anyMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.SoulWisps))
            helper.fail("a plain Gravedigger got the Embalmer's wisps");
        BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
        embalmer.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        digger.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5, 0, 0);
        farDigger.moveTo(at.getX() + 10.5, at.getY(), at.getZ() + 10.5, 0, 0);
        // the friend stands closer than the foe: a pulse that took the nearest unit instead of the nearest enemy fails
        friend.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5, 0, 0);
        foe.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 4.5, 0, 0);
        embalmer.setOwnerName(owner);
        digger.setOwnerName(owner);
        farDigger.setOwnerName(owner);
        friend.setOwnerName(owner);
        foe.setOwnerName("gametest_soul_wisps_foe");
        for (var e : List.<net.minecraft.world.entity.LivingEntity>of(embalmer, digger, farDigger, friend, foe))
            level.addFreshEntity(e);
        helper.runAfterDelay(3, () -> {
            float before = com.solegendary.reignofnether.ability.abilities.SoulWisps.reclaimMultiplier(digger);
            float foeHp = foe.getHealth(), friendHp = friend.getHealth(), diggerHp = digger.getHealth();
            var w = com.solegendary.reignofnether.ability.abilities.SoulWisps.summon(level, embalmer, owner);
            try {
                if (com.solegendary.reignofnether.ability.abilities.SoulWisps.reclaimMultiplier(digger) <= before)
                    helper.fail("Soul Wisps did not speed up a Gravebound worker's reclaim");
                if (com.solegendary.reignofnether.ability.abilities.SoulWisps.reclaimMultiplier(embalmer) <= 1f)
                    helper.fail("Soul Wisps did not speed up the Embalmer's own reclaim");
                if (com.solegendary.reignofnether.ability.abilities.SoulWisps.reclaimMultiplier(farDigger) != 1f)
                    helper.fail("Soul Wisps sped up a worker far out of reach");
                var hit = com.solegendary.reignofnether.ability.abilities.SoulWisps.pulse(w);
                if (hit != foe)
                    helper.fail("the wisps did not lash the enemy in reach");
                if (foe.isAlive() && foe.getHealth() >= foeHp)
                    helper.fail("the wisps did not hurt the enemy");
                if (friend.getHealth() < friendHp || digger.getHealth() < diggerHp)
                    helper.fail("the wisps hurt a friendly unit");
            } finally {
                com.solegendary.reignofnether.ability.abilities.SoulWisps.dispel(w);
                for (var e : List.<net.minecraft.world.entity.LivingEntity>of(embalmer, digger, farDigger, friend, foe))
                    e.discard();
            }
            helper.succeed();
        });
    }

    /**
     * Map drawing (Alt-drag lines / labels): the server allows 10 drawings per 5 s per player and relays each one to
     * the sender and their allies only. Uses its own limiter instance and unique names, so it can't collide with
     * other tests or real players.
     */
    @GameTest(template = ARENA)
    public static void map_drawing_is_rate_limited_and_ally_only(GameTestHelper helper) {
        var rules = new com.solegendary.reignofnether.minimap.MapDrawRules();
        java.util.UUID p = java.util.UUID.randomUUID(), other = java.util.UUID.randomUUID();
        long t0 = 1_000_000L;
        for (int i = 0; i < com.solegendary.reignofnether.minimap.MapDrawRules.MAX_STROKES; i++)
            if (!rules.tryConsume(p, t0 + i * 10L))
                helper.fail("drawing " + (i + 1) + " of 10 inside the window was refused");
        if (rules.tryConsume(p, t0 + 200))
            helper.fail("an 11th drawing inside 5 s was allowed");
        if (!rules.tryConsume(other, t0 + 200))
            helper.fail("one player's spam used up another player's budget");
        if (!rules.tryConsume(p, t0 + com.solegendary.reignofnether.minimap.MapDrawRules.WINDOW_MS + 1))
            helper.fail("drawing still refused after the 5 s window slid past the first stroke");

        String a = "gt_draw_" + p.toString().substring(0, 8), b = a + "_ally", c = a + "_enemy";
        com.solegendary.reignofnether.alliance.AlliancesServerEvents.addAlliance(a, b);
        try {
            Set<String> to = com.solegendary.reignofnether.minimap.MapDrawRules.recipients(a, List.of(c, b, a),
                com.solegendary.reignofnether.alliance.AlliancesServerEvents::isAllied);
            if (!to.contains(a) || !to.contains(b))
                helper.fail("drawing not relayed to the sender and their ally: " + to);
            if (to.contains(c))
                helper.fail("drawing leaked to an enemy: " + to);
        } finally {
            com.solegendary.reignofnether.alliance.AlliancesServerEvents.removeAlliance(a, b);
        }

        if (com.solegendary.reignofnether.minimap.MapDrawRules.isValidStroke(new int[] { 0 }, new int[] { 0 }))
            helper.fail("a one-point stroke was accepted");
        if (com.solegendary.reignofnether.minimap.MapDrawRules.isValidStroke(new int[] { 0, 500 }, new int[] { 0, 0 }))
            helper.fail("a 500-block segment was accepted");
        String label = com.solegendary.reignofnether.minimap.MapDrawRules.sanitiseLabel("§cattack here now please hurry!!");
        if (label.length() > com.solegendary.reignofnether.minimap.MapDrawRules.MAX_LABEL_CHARS || label.contains("§"))
            helper.fail("label not sanitised: '" + label + "'");
        helper.succeed();
    }

    // ------------------------------------------------------------------ 8v8 stress benchmark

    static final int STRESS_OWNERS = 8, STRESS_PER_OWNER = 50, STRESS_WRECKS = 40, STRESS_POINTS = 18;
    static final int STRESS_WARMUP = 10, STRESS_ITERATIONS = 40;
    /** Average budget for one pass of every system below, in ms. Most of them run once a second, so this is roomy. */
    static final double STRESS_BUDGET_MS = 8.0;

    /**
     * 8v8 scale: 400 units for eight test-only owners (three Sunforged, three Horde, two Gravebound armies whose
     * clusters overlap, so there are fights everywhere), {@link #STRESS_WRECKS} wrecks and {@link #STRESS_POINTS}
     * capture sites. Every scaling-sensitive server system is then run directly, {@link #STRESS_ITERATIONS} times,
     * and timed. The per-system averages are logged ("[Stress]") so a regression shows up in the CI log even while
     * the total stays under budget. Placed ~3000 blocks west of the test grid (the soak test goes east), spawned,
     * measured and removed inside one callback, so the units never tick and no other test ever sees them.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void stress_8v8_server_systems_stay_in_budget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        int baseX = origin.getX() - 3000, baseZ = origin.getZ();
        final double y = 150;   // floating in the air: no terrain dependence; these units are never ticked
        // eight overlapping 24x24 clusters on a 4x2 grid 20 blocks apart; their chunks are force-loaded meanwhile
        List<long[]> chunks = new ArrayList<>();
        for (int o = 0; o < STRESS_OWNERS; o++) {
            int cx = baseX + (o % 4) * 20, cz = baseZ + (o / 4) * 20;
            for (int dx = -16; dx <= 16; dx += 16)
                for (int dz = -16; dz <= 16; dz += 16) {
                    long[] c = { (cx + dx) >> 4, (cz + dz) >> 4 };
                    if (chunks.stream().noneMatch(k -> k[0] == c[0] && k[1] == c[1]))
                        chunks.add(c);
                }
        }
        for (long[] c : chunks)
            level.setChunkForced((int) c[0], (int) c[1], true);

        helper.runAfterDelay(40, () -> {
            var rng = new java.util.Random(8);
            List<net.minecraft.world.entity.Entity> spawned = new ArrayList<>();
            List<com.solegendary.reignofnether.resources.Resources> pools = new ArrayList<>();
            try {
                for (int o = 0; o < STRESS_OWNERS; o++) {
                    String owner = "gametest_stress_" + o;
                    var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 0, 0);
                    com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
                    pools.add(pool);
                    int cx = baseX + (o % 4) * 20, cz = baseZ + (o / 4) * 20;
                    for (int i = 0; i < STRESS_PER_OWNER; i++) {
                        var e = stressUnitType(o, i).create(level);
                        if (e == null)
                            continue;
                        e.moveTo(cx + rng.nextDouble() * 24 - 12, y, cz + rng.nextDouble() * 24 - 12, rng.nextFloat() * 360, 0);
                        ((com.solegendary.reignofnether.unit.interfaces.Unit) e).setOwnerName(owner);
                        level.addFreshEntity(e);
                        spawned.add(e);
                    }
                }
                for (int i = 0; i < STRESS_WRECKS; i++) {
                    var w = com.solegendary.reignofnether.resources.WreckServerEvents.spawnWreck(level,
                        baseX - 12 + rng.nextDouble() * 84, y, baseZ - 12 + rng.nextDouble() * 44, 100000f, null);
                    if (w != null)
                        spawned.add(w);
                }
                for (int i = 0; i < STRESS_POINTS; i++) {
                    var m = net.minecraft.world.entity.EntityType.MARKER.create(level);
                    if (m == null)
                        continue;
                    m.moveTo(baseX - 12 + rng.nextDouble() * 84, y, baseZ - 12 + rng.nextDouble() * 44);
                    m.addTag(com.solegendary.reignofnether.startpos.CapturePointServerEvents.TAG);
                    level.addFreshEntity(m);
                    spawned.add(m);
                }

                // the real code paths (budgeted), then the pre-grid/pre-index ways as a logged reference only
                String[] names = { "unit grid rebuild", "wreck reclaim", "momentum", "formation", "capture points",
                    "queue lines", "player panel", "builder lookup x200", "bot enemy scans x64",
                    "REF builder lookup x200 (old scan)", "REF bot enemy scans x64 (old scan)" };
                int budgeted = 9;
                var buildings = com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings();
                List<net.minecraft.world.entity.LivingEntity> scan = new ArrayList<>();
                Runnable[] systems = {
                    () -> {   // paid once per tick by the first grid query; forced here so every pass pays it
                        com.solegendary.reignofnether.unit.UnitGrid.invalidate();
                        com.solegendary.reignofnether.unit.UnitGrid.near(level, baseX, baseZ, 1, scan);
                    },
                    () -> com.solegendary.reignofnether.resources.WreckServerEvents.tickReclaim(level, 0.05f),
                    () -> com.solegendary.reignofnether.unit.MomentumServerEvents.sampleAll(level),
                    () -> com.solegendary.reignofnether.unit.FormationServerEvents.update(level),
                    () -> com.solegendary.reignofnether.startpos.CapturePointServerEvents.tick(level, 0.05f),
                    com.solegendary.reignofnether.unit.UnitQueueSync::buildPayloads,
                    com.solegendary.reignofnether.player.PlayerPanelServerEvents::buildRows,
                    () -> {   // every building asks for its builders every tick; one index rebuild per tick (a death)
                        com.solegendary.reignofnether.building.BuilderIndex.invalidate();
                        int n = 0;
                        for (int b = 0; b < 200; b++)
                            n += com.solegendary.reignofnether.building.BuilderIndex.buildersOf(
                                buildings.isEmpty() ? null : buildings.get(b % buildings.size())).size();
                        stressSink += n;
                    },
                    () -> stressGridEnemyScans(level, 64, 12, scan),
                    () -> stressNaiveBuilderScans(200),
                    () -> stressNaiveEnemyScans(64, 12),
                };
                long[] total = new long[systems.length];
                for (int it = 0; it < STRESS_WARMUP + STRESS_ITERATIONS; it++) {
                    for (var pool : pools)   // keep reclaim paying (full storage would skip its work)
                        pool.ore = 0;
                    for (int s = 0; s < systems.length; s++) {
                        long t0 = System.nanoTime();
                        systems[s].run();
                        long dt = System.nanoTime() - t0;
                        if (it >= STRESS_WARMUP)
                            total[s] += dt;
                    }
                }
                // the grid must find exactly what the full scan finds
                int viaGrid = stressGridEnemyScans(level, 64, 12, scan), viaScan = stressNaiveEnemyScans(64, 12);
                if (viaGrid != viaScan)
                    helper.fail("UnitGrid found " + viaGrid + " enemies in reach, the full scan " + viaScan);
                double sum = 0;
                StringBuilder sb = new StringBuilder();
                for (int s = 0; s < systems.length; s++) {
                    double ms = total[s] / 1e6 / STRESS_ITERATIONS;
                    if (s < budgeted)
                        sum += ms;
                    sb.append(String.format(java.util.Locale.ROOT, "%s=%.3fms ", names[s], ms));
                }
                int units = 0;
                for (var e : spawned)
                    if (e instanceof net.minecraft.world.entity.LivingEntity)
                        units++;
                ReignOfNether.LOGGER.info("[Stress] {} units, {} wrecks, {} sites, avg per pass over {} passes: {}| total={}ms (budget {}ms)",
                    units, STRESS_WRECKS, STRESS_POINTS, STRESS_ITERATIONS, sb,
                    String.format(java.util.Locale.ROOT, "%.3f", sum), STRESS_BUDGET_MS);
                if (units < STRESS_OWNERS * STRESS_PER_OWNER * 9 / 10)
                    helper.fail("only " + units + " stress units spawned");
                else if (sum > STRESS_BUDGET_MS)
                    helper.fail(String.format(java.util.Locale.ROOT, "8v8 server systems took %.2f ms per pass (budget %.1f ms): %s",
                        sum, STRESS_BUDGET_MS, sb));
            } finally {
                for (var e : spawned)
                    e.discard();
                com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.removeAll(pools);
                // let the systems drop their now-removed entries (wreck list, capture sites, momentum/formation state)
                com.solegendary.reignofnether.resources.WreckServerEvents.tickReclaim(level, 0f);
                com.solegendary.reignofnether.startpos.CapturePointServerEvents.tick(level, 0f);
                com.solegendary.reignofnether.unit.MomentumServerEvents.sampleAll(level);
                com.solegendary.reignofnether.unit.FormationServerEvents.update(level);
                for (long[] c : chunks)
                    level.setChunkForced((int) c[0], (int) c[1], false);
            }
            helper.succeed();
        });
    }

    /** Owners 0-2 Sunforged, 3-5 Horde, 6-7 Gravebound; per owner 10 workers, then melee and ranged fighters. */
    static net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> stressUnitType(int owner, int i) {
        int kind = i < 10 ? 0 : i % 2 == 0 ? 1 : 2;   // worker, melee, ranged
        int faction = owner < 3 ? 0 : owner < 6 ? 1 : 2;
        switch (faction * 3 + kind) {
            case 0: return com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get();
            case 1: return com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get();
            case 2: return com.solegendary.reignofnether.registrars.EntityRegistrar.PILLAGER_UNIT.get();
            case 3: return com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get();
            case 4: return com.solegendary.reignofnether.registrars.EntityRegistrar.BRUTE_UNIT.get();
            case 5: return com.solegendary.reignofnether.registrars.EntityRegistrar.HEADHUNTER_UNIT.get();
            case 6: return com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_VILLAGER_UNIT.get();
            case 7: return com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_UNIT.get();
            default: return com.solegendary.reignofnether.registrars.EntityRegistrar.SKELETON_UNIT.get();
        }
    }

    static int stressSink;   // keeps the JIT from discarding the reference scans below

    /** The pre-index BuildingPlacement.getBuilders body, run once per building as every tick did: the old cost. */
    static void stressNaiveBuilderScans(int buildings) {
        int n = 0;
        for (int b = 0; b < buildings; b++) {
            List<com.solegendary.reignofnether.unit.interfaces.WorkerUnit> builders = new ArrayList<>();
            for (var le : com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits())
                if (le instanceof com.solegendary.reignofnether.unit.interfaces.WorkerUnit wu) {
                    var goal = wu.getBuildRepairGoal();
                    if (goal != null && goal.getBuildingTarget() != null && goal.isBuilding())
                        builders.add(wu);
                }
            n += builders.size();
        }
        stressSink += n;
    }

    /** The same check as {@link #stressNaiveEnemyScans}, the way BotPlayer does it now: through UnitGrid. */
    static int stressGridEnemyScans(ServerLevel level, int casters, double range,
                                     List<net.minecraft.world.entity.LivingEntity> scan) {
        var all = com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits();
        int n = 0;
        for (int c = 0; c < casters && c < all.size(); c++) {
            var le = all.get(all.size() - 1 - (c * 5) % all.size());
            if (!(le instanceof com.solegendary.reignofnether.unit.interfaces.Unit u))
                continue;
            String name = u.getOwnerName();
            for (var other : com.solegendary.reignofnether.unit.UnitGrid.near(level, le.getX(), le.getZ(), range, scan)) {
                if (!(other instanceof com.solegendary.reignofnether.unit.interfaces.Unit ou) || !other.isAlive()
                        || other.distanceToSqr(le) > range * range)
                    continue;
                String o = ou.getOwnerName();
                if (o != null && !o.equals(name)
                        && !com.solegendary.reignofnether.alliance.AlliancesServerEvents.isAllied(name, o))
                    n++;
            }
        }
        stressSink += n;
        return n;
    }

    /** A bot's "three enemies within reach?" check over every unit, for {@code casters} units (the old way). */
    static int stressNaiveEnemyScans(int casters, double range) {
        var all = com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits();
        int n = 0;
        for (int c = 0; c < casters && c < all.size(); c++) {
            var le = all.get(all.size() - 1 - (c * 5) % all.size());
            if (!(le instanceof com.solegendary.reignofnether.unit.interfaces.Unit u))
                continue;
            String name = u.getOwnerName();
            for (var other : all) {
                if (!(other instanceof com.solegendary.reignofnether.unit.interfaces.Unit ou) || !other.isAlive()
                        || other.distanceToSqr(le) > range * range)
                    continue;
                String o = ou.getOwnerName();
                if (o != null && !o.equals(name)
                        && !com.solegendary.reignofnether.alliance.AlliancesServerEvents.isAllied(name, o))
                    n++;
            }
        }
        stressSink += n;
        return n;
    }

    /**
     * Results-screen graphs: each sample records the owner's metal/energy income and army value (metal cost of their
     * living units), the sample count grows one per call, and a long match stays capped with a doubled interval.
     * Uses its own owner name and a private RTSPlayer, so parallel tests can't change its numbers.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void match_history_samples_income_and_army(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_historian";
        var a = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        var b = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (a == null || b == null) {
            helper.fail("could not create vindicator units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
        a.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        b.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5, 0, 0);
        a.setOwnerName(owner);
        b.setOwnerName(owner);
        level.addFreshEntity(a);
        level.addFreshEntity(b);
        var eco = com.solegendary.reignofnether.resources.EconomyServerEvents.getEconomy(owner);
        helper.runAfterDelay(3, () -> {
            eco.metalIncome = 3f;
            eco.energyIncome = 25f;
            var player = com.solegendary.reignofnether.player.RTSPlayer.getNewBot(owner,
                com.solegendary.reignofnether.faction.Factions.VILLAGERS);
            var hist = player.history;
            var players = List.of(player);
            for (int i = 1; i <= 3; i++) {
                com.solegendary.reignofnether.player.MatchHistory.sampleAll(players);
                if (hist.size() != i)
                    helper.fail("sample " + i + " not recorded, size " + hist.size());
            }
            float expectedArmy = a.getCost().metal() + b.getCost().metal();
            if (Math.abs(hist.metal[2] - 3f) > 0.01f || Math.abs(hist.energy[2] - 25f) > 0.01f)
                helper.fail("income sample wrong: " + hist.metal[2] + " metal/s, " + hist.energy[2] + " energy/s");
            if (expectedArmy <= 0 || Math.abs(hist.army[2] - expectedArmy) > 0.5f)
                helper.fail("army value " + hist.army[2] + ", expected " + expectedArmy);

            // a dead unit no longer counts
            a.discard();
            com.solegendary.reignofnether.player.MatchHistory.sampleAll(players);
            if (Math.abs(hist.army[3] - b.getCost().metal()) > 0.5f)
                helper.fail("army value after a loss " + hist.army[3] + ", expected " + b.getCost().metal());

            // a very long match: capped, interval doubled, the first sample kept
            for (int i = 0; i < 500; i++)
                hist.offer(1f, 2f, 3f);
            int max = com.solegendary.reignofnether.player.MatchHistory.MAX_SAMPLES;
            if (hist.size() > max || hist.size() < max / 2)
                helper.fail("history not capped: " + hist.size() + " samples");
            if (hist.getIntervalTicks() <= com.solegendary.reignofnether.player.MatchHistory.SAMPLE_TICKS)
                helper.fail("interval did not grow on compaction: " + hist.getIntervalTicks());
            if (Math.abs(hist.metal[0] - 3f) > 0.01f)
                helper.fail("compaction lost the first sample: " + hist.metal[0]);
            // the timeline must still span the whole match (samples * interval ~ calls * SAMPLE_TICKS)
            long span = (long) (hist.size() - 1) * hist.getIntervalTicks();
            long real = (long) (504 - 1) * com.solegendary.reignofnether.player.MatchHistory.SAMPLE_TICKS;
            if (span > real || span < real - hist.getIntervalTicks())
                helper.fail("compacted timeline " + span + " ticks does not match " + real);

            eco.metalIncome = 0;
            eco.energyIncome = 0;
            b.discard();
            helper.succeed();
        });
    }

    /**
     * The Verdant Court going live must not disturb the three original factions: each is still playable with the same
     * capitol and worker, and every live faction (the Court included) has a capitol, a worker and a bot kit that agree.
     * The Court is playable and in the random pool but not a classic (title screen / survival) or survival faction,
     * and it is registered after the originals so their registry ids are unchanged. Static registration only.
     */
    @GameTest(template = ARENA)
    public static void verdant_court_is_live_and_the_original_three_are_unchanged(GameTestHelper helper) {
        var old = List.of(Factions.VILLAGERS, Factions.MONSTERS, Factions.PIGLINS);
        var verdant = Factions.VERDANT_COURT;
        List<Building> oldCapitols = List.of(Buildings.TOWN_CENTRE, Buildings.MAUSOLEUM, Buildings.CENTRAL_PORTAL);
        List<net.minecraft.world.entity.EntityType<?>> oldWorkers = List.of(
            com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_VILLAGER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get());
        for (int i = 0; i < old.size(); i++) {
            var f = old.get(i);
            if (!Factions.CLASSIC_FACTIONS.contains(f.key))
                helper.fail(f.getName() + " dropped out of the classic list");
            if (ReignOfNetherRegistries.BUILDING.get(f.capitolBuilding) != oldCapitols.get(i))
                helper.fail(f.getName() + "'s capitol changed to " + f.capitolBuilding);
            if (!net.minecraft.world.entity.EntityType.getKey(oldWorkers.get(i)).equals(f.workerEntityType))
                helper.fail(f.getName() + "'s worker changed to " + f.workerEntityType);
        }
        List<com.solegendary.reignofnether.faction.Faction> live = new ArrayList<>(old);
        live.add(verdant);
        live.add(Factions.TIDEWROUGHT);   // live since its slice 1: "Random" must reach it too
        live.add(Factions.STORMCALLERS);  // and the Stormcallers since theirs
        for (var f : live) {
            if (!Factions.isLive(f)) {
                helper.fail(f.getName() + " is not a live faction");
                return;
            }
            if (Factions.getFaction(f.key) != f)
                helper.fail(f.getName() + " does not resolve by its key");
            if (!Factions.PLAYABLE_FACTIONS.contains(f.key))
                helper.fail(f.getName() + " is not in the playable list");
            Building capitol = ReignOfNetherRegistries.BUILDING.get(f.capitolBuilding);
            if (capitol == null || !capitol.isCapitol)
                helper.fail(f.getName() + ": capitol " + f.capitolBuilding + " is not a registered capitol");
            if (net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(f.workerEntityType) == null)
                helper.fail(f.getName() + ": worker " + f.workerEntityType + " is not a registered entity");
            var kit = com.solegendary.reignofnether.bot.BotPlayer.kitFor(f);
            if (kit == null || kit.capitol() != capitol)
                helper.fail(f.getName() + ": the bot kit builds a different capitol than the faction starts with");
        }
        if (verdant.preview)
            helper.fail("the Verdant Court is still marked as a preview");
        if (ReignOfNetherRegistries.BUILDING.get(verdant.capitolBuilding) != Buildings.HEARTWOOD_HALL)
            helper.fail("the Verdant Court's capitol is not the Heartwood Hall");
        if (Factions.CLASSIC_FACTIONS.contains(verdant.key) || Factions.SURVIVAL_FACTIONS.contains(verdant.key))
            helper.fail("the Verdant Court leaked into the classic or survival list before it has a cube map / wave");
        if (ReignOfNetherRegistries.FACTIONS.getId(verdant) < ReignOfNetherRegistries.FACTIONS.getId(Factions.NONE))
            helper.fail("the Verdant Court was registered before the original factions and shifted their ids");
        // "Random" only ever lands on a live faction, and the Court is in the pool
        var rng = new java.util.Random(4);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 400; i++) {
            var f = Factions.randomLive(rng);
            if (!Factions.isLive(f))
                helper.fail("random picked a faction that is not live: " + f.getName());
            seen.add(f.getName());
        }
        if (!seen.contains(verdant.getName()) || seen.size() != live.size())
            helper.fail("random should reach every live faction, saw " + seen);
        // every unit the Court trains belongs to the Court (Factions.getFaction(unit) is how its look is chosen)
        for (var type : List.of(com.solegendary.reignofnether.registrars.EntityRegistrar.SEEDSHAPER_UNIT.get(),
                com.solegendary.reignofnether.registrars.EntityRegistrar.FOX_COURIER_UNIT.get(),
                com.solegendary.reignofnether.registrars.EntityRegistrar.LEAFBLADE_UNIT.get(),
                com.solegendary.reignofnether.registrars.EntityRegistrar.THORNBOW_UNIT.get(),
                com.solegendary.reignofnether.registrars.EntityRegistrar.SENTINEL_TREANT_UNIT.get()))
            if (!verdant.key.equals(Factions.ENTITY_FACTION.get(net.minecraft.world.entity.EntityType.getKey(type))))
                helper.fail(net.minecraft.world.entity.EntityType.getKey(type) + " is not registered to the Verdant Court");
        helper.succeed();
    }

    /**
     * Faction look/mechanics come from one FactionTraits entry per faction. Each live faction and the Verdant preview
     * must have its own entry, Verdant must not borrow Sunforged's D-gun, wreck or scaffold (the old "otherwise
     * villagers" fall-through, design/verdant_court_plan.md), and a faction without an entry gets the neutral one.
     * Static data only, no world state.
     */
    @GameTest(template = ARENA)
    public static void every_faction_has_its_own_traits_and_verdant_is_not_sunforged(GameTestHelper helper) {
        var factions = List.of(Factions.VILLAGERS, Factions.MONSTERS, Factions.PIGLINS, Factions.VERDANT_COURT,
            Factions.TIDEWROUGHT);
        for (var f : factions)
            if (!FactionTraits.hasExplicit(f))
                helper.fail(f.getName() + " has no explicit FactionTraits entry");
        var sun = FactionTraits.of(Factions.VILLAGERS);
        var grave = FactionTraits.of(Factions.MONSTERS);
        var horde = FactionTraits.of(Factions.PIGLINS);
        var verdant = FactionTraits.of(Factions.VERDANT_COURT);
        var neutral = FactionTraits.NEUTRAL;
        if (verdant.dgunKind != com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.THORNBURST)
            helper.fail("Verdant Court's D-gun should be Thornburst, is " + verdant.dgunKind);
        if (com.solegendary.reignofnether.resources.WreckServerEvents.lookFor(Factions.VERDANT_COURT)
                == com.solegendary.reignofnether.resources.WreckServerEvents.lookFor(Factions.VILLAGERS))
            helper.fail("Verdant Court leaves Sunforged wrecks");
        if (verdant.scaffoldPole == sun.scaffoldPole || verdant.scaffoldRail == sun.scaffoldRail
                || verdant.scaffoldDecor == sun.scaffoldDecor)
            helper.fail("Verdant Court builds with Sunforged scaffold materials");
        // slice 1: Wildstride, a bot kit and a quick-build extractor; the other factions' mechanics stay off
        if (verdant.commanderAbility != com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.WILDSTRIDE)
            helper.fail("Verdant Court's signature should be Wildstride, is " + verdant.commanderAbility);
        if (com.solegendary.reignofnether.bot.BotPlayer.kitFor(Factions.VERDANT_COURT) == null)
            helper.fail("Verdant Court has no bot kit");
        if (verdant.metalExtractor.get() != Buildings.METAL_EXTRACTOR_VERDANT)
            helper.fail("Verdant Court's quick-build extractor is " + verdant.metalExtractor.get());
        if (verdant.formation || verdant.momentum)
            helper.fail("Verdant Court picked up Formation or Momentum");
        // the live three keep what the old if/else chains gave them
        if (sun.dgunKind != com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.SUNFIRE
                || grave.dgunKind != com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.SOULREAPER
                || horde.dgunKind != com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.BLOODSTORM)
            helper.fail("a live faction's D-gun kind changed");
        if (!sun.formation || grave.formation || horde.formation || !horde.momentum || sun.momentum || grave.momentum)
            helper.fail("Formation must stay Sunforged-only and Momentum Horde-only");
        if (sun.reclaimMultiplier != 1f || grave.reclaimMultiplier != 2f || horde.reclaimMultiplier != 1.5f)
            helper.fail("a live faction's reclaim multiplier changed");
        if (sun.wreckBlock != Blocks.IRON_BLOCK.defaultBlockState() || grave.wreckBlock != Blocks.BONE_BLOCK.defaultBlockState()
                || horde.wreckBlock != Blocks.EXPOSED_CUT_COPPER.defaultBlockState())
            helper.fail("a live faction's wreck look changed");
        for (var f : List.of(Factions.VILLAGERS, Factions.MONSTERS, Factions.PIGLINS))
            if (com.solegendary.reignofnether.bot.BotPlayer.kitFor(f) == null)
                helper.fail(f.getName() + " lost its bot kit");
        // no entry -> neutral, never Sunforged
        if (FactionTraits.of(Factions.NEUTRAL) != neutral || FactionTraits.of(null) != neutral)
            helper.fail("a faction without an entry did not get the neutral traits");
        if (neutral.dgunKind == sun.dgunKind || neutral.formation || neutral.livery != null || neutral.wreckBlock == sun.wreckBlock)
            helper.fail("the neutral traits fall back to Sunforged behaviour");
        helper.succeed();
    }

    /**
     * Tidewrought slice 1 (design/tidewrought_plan.md): the faction is live - playable, in "Random", with a capitol, a
     * worker and a bot kit that agree, its units registered to it - and the four other live factions are untouched
     * (same capitols, workers, bot kits, D-guns and signatures). It is registered after the Court so no older id moved,
     * and it wears its own teal-and-brass traits with its own commander kit (Broadside, Riptide), debris, nanolathe and
     * quick-build extractor. Static data only, safe in parallel.
     */
    @GameTest(template = ARENA)
    public static void tidewrought_is_live_and_the_four_other_factions_are_intact(GameTestHelper helper) {
        var tide = Factions.TIDEWROUGHT;
        if (tide == null) {
            helper.fail("the Tidewrought is not registered");
            return;
        }
        var others = List.of(Factions.VILLAGERS, Factions.MONSTERS, Factions.PIGLINS, Factions.VERDANT_COURT);
        List<Building> capitols = List.of(Buildings.TOWN_CENTRE, Buildings.MAUSOLEUM, Buildings.CENTRAL_PORTAL, Buildings.HEARTWOOD_HALL);
        List<net.minecraft.world.entity.EntityType<?>> workers = List.of(
            com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_VILLAGER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.SEEDSHAPER_UNIT.get());
        var dguns = List.of(com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.SUNFIRE,
            com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.SOULREAPER,
            com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.BLOODSTORM,
            com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.THORNBURST);
        var signatures = List.of(com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.RALLY,
            com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.DREAD,
            com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.WAR_HORN,
            com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.WILDSTRIDE);
        for (int i = 0; i < others.size(); i++) {
            var f = others.get(i);
            if (!Factions.isLive(f) || !Factions.PLAYABLE_FACTIONS.contains(f.key))
                helper.fail(f.getName() + " is no longer a live, playable faction");
            if (ReignOfNetherRegistries.BUILDING.get(f.capitolBuilding) != capitols.get(i))
                helper.fail(f.getName() + "'s capitol changed to " + f.capitolBuilding);
            if (!net.minecraft.world.entity.EntityType.getKey(workers.get(i)).equals(f.workerEntityType))
                helper.fail(f.getName() + "'s worker changed to " + f.workerEntityType);
            var kit = com.solegendary.reignofnether.bot.BotPlayer.kitFor(f);
            if (kit == null || kit.capitol() != capitols.get(i))
                helper.fail(f.getName() + " lost its bot kit or its kit builds another capitol");
            var t = FactionTraits.of(f);
            if (t.dgunKind != dguns.get(i) || t.commanderAbility != signatures.get(i) || t.tidal)
                helper.fail(f.getName() + "'s commander kit changed or it turned tidal");
        }
        if (tide.preview || !tide.playable || !Factions.isLive(tide) || !Factions.PLAYABLE_FACTIONS.contains(tide.key))
            helper.fail("the Tidewrought is not a live, playable faction");
        if (Factions.CLASSIC_FACTIONS.contains(tide.key) || Factions.SURVIVAL_FACTIONS.contains(tide.key))
            helper.fail("the Tidewrought leaked into the classic or survival list before it has a cube map / wave");
        if (ReignOfNetherRegistries.BUILDING.get(tide.capitolBuilding) != Buildings.WRECK_HARBOUR)
            helper.fail("the Tidewrought's capitol is not the Wreck Harbour: " + tide.capitolBuilding);
        if (!net.minecraft.world.entity.EntityType.getKey(com.solegendary.reignofnether.registrars.EntityRegistrar.SHIPWRIGHT_UNIT.get())
                .equals(tide.workerEntityType))
            helper.fail("the Tidewrought's worker is not the Shipwright: " + tide.workerEntityType);
        var tideKit = com.solegendary.reignofnether.bot.BotPlayer.kitFor(tide);
        if (tideKit == null || tideKit.capitol() != Buildings.WRECK_HARBOUR || tideKit.armyBuilding() != Buildings.SLIPWAY)
            helper.fail("the Tidewrought bot kit is missing or builds the wrong capitol / lab");
        else if (tideKit.t2Lab() != Buildings.DRYDOCK || tideKit.t3Building() != Buildings.ABYSSAL_SHIPYARD)   // slices 3 and 4
            helper.fail("the Tidewrought kit's T2 lab is not the Drydock, or its T3 lab is not the Abyssal Shipyard");
        if (Factions.getFaction(tide.key) != tide || !"tidewrought".equals(tide.getName()))
            helper.fail("the Tidewrought does not resolve by its key");
        if (ReignOfNetherRegistries.FACTIONS.getId(tide) < ReignOfNetherRegistries.FACTIONS.getId(Factions.VERDANT_COURT))
            helper.fail("the Tidewrought was registered before the Verdant Court and shifted its id");
        // "Random" reaches all six (the Stormcallers joined in their slice 1)
        var rng = new java.util.Random(5);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 600; i++)
            seen.add(Factions.randomLive(rng).getName());
        if (!seen.contains("tidewrought") || seen.size() != 6)
            helper.fail("random should reach the six live factions, saw " + seen);
        // every unit it trains belongs to it (that is how its look and Tides are chosen)
        for (var type : List.of(com.solegendary.reignofnether.registrars.EntityRegistrar.SHIPWRIGHT_UNIT.get(),
                com.solegendary.reignofnether.registrars.EntityRegistrar.GULL_SPOTTER_UNIT.get(),
                com.solegendary.reignofnether.registrars.EntityRegistrar.CUTLASS_RAIDER_UNIT.get(),
                com.solegendary.reignofnether.registrars.EntityRegistrar.REEF_GUARD_UNIT.get(),
                com.solegendary.reignofnether.registrars.EntityRegistrar.BOMBARD_CREW_UNIT.get(),
                com.solegendary.reignofnether.registrars.EntityRegistrar.TIDE_PRIEST_UNIT.get()))
            if (!tide.key.equals(Factions.ENTITY_FACTION.get(net.minecraft.world.entity.EntityType.getKey(type))))
                helper.fail(net.minecraft.world.entity.EntityType.getKey(type) + " is not registered to the Tidewrought");
        // its own look and kit, never another faction's
        if (!FactionTraits.hasExplicit(tide))
            helper.fail("the Tidewrought has no explicit FactionTraits entry");
        var t = FactionTraits.of(tide);
        for (var f : others) {
            var o = FactionTraits.of(f);
            if (t == o || t.wreckBlock == o.wreckBlock || t.scaffoldPole == o.scaffoldPole
                    || (t.bannerCloth == o.bannerCloth && t.bannerTrim == o.bannerTrim) || t.deathDebris == o.deathDebris
                    || t.nanoTint == o.nanoTint)
                helper.fail("the Tidewrought borrows " + f.getName() + "'s look");
        }
        if (t.dgunKind != com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.BROADSIDE
                || t.commanderAbility != com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.RIPTIDE)
            helper.fail("the Admiral's kit should be Broadside + Riptide, is " + t.dgunKind + " + " + t.commanderAbility);
        if (!t.tidal || t.formation || t.momentum || t.metalExtractor.get() != Buildings.METAL_EXTRACTOR_TIDE)
            helper.fail("the Tidewrought lost Tides, picked up another faction's mechanic, or has the wrong quick-build extractor");
        helper.succeed();
    }

    /**
     * Stormcallers slice 1 (design/stormcallers_plan.md): the faction is live - playable, in "Random", started from its
     * skirmish code 6, with a capitol, a worker and a bot kit that agree and its units registered to it - and the five
     * other live factions are untouched (same capitols, workers, bot kits, D-guns and signatures, none of them Mana). It
     * is registered after the Tidewrought so no older id moved, and it wears its own red-lacquer, jade and gold traits
     * with its own commander kit (Heaven's Judgement, Blink), debris, nanolathe and quick-build extractor. Static data
     * only, safe in parallel.
     */
    @GameTest(template = ARENA)
    public static void stormcallers_are_live_and_the_five_other_factions_are_intact(GameTestHelper helper) {
        var storm = Factions.STORMCALLERS;
        if (storm == null) {
            helper.fail("the Stormcallers are not registered");
            return;
        }
        var others = List.of(Factions.VILLAGERS, Factions.MONSTERS, Factions.PIGLINS, Factions.VERDANT_COURT,
            Factions.TIDEWROUGHT);
        List<Building> capitols = List.of(Buildings.TOWN_CENTRE, Buildings.MAUSOLEUM, Buildings.CENTRAL_PORTAL,
            Buildings.HEARTWOOD_HALL, Buildings.WRECK_HARBOUR);
        List<net.minecraft.world.entity.EntityType<?>> workers = List.of(
            com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_VILLAGER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.SEEDSHAPER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.SHIPWRIGHT_UNIT.get());
        var dguns = List.of(com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.SUNFIRE,
            com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.SOULREAPER,
            com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.BLOODSTORM,
            com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.THORNBURST,
            com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.BROADSIDE);
        var signatures = List.of(com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.RALLY,
            com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.DREAD,
            com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.WAR_HORN,
            com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.WILDSTRIDE,
            com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.RIPTIDE);
        for (int i = 0; i < others.size(); i++) {
            var f = others.get(i);
            if (!Factions.isLive(f) || !Factions.PLAYABLE_FACTIONS.contains(f.key))
                helper.fail(f.getName() + " is no longer a live, playable faction");
            if (ReignOfNetherRegistries.BUILDING.get(f.capitolBuilding) != capitols.get(i))
                helper.fail(f.getName() + "'s capitol changed to " + f.capitolBuilding);
            if (!net.minecraft.world.entity.EntityType.getKey(workers.get(i)).equals(f.workerEntityType))
                helper.fail(f.getName() + "'s worker changed to " + f.workerEntityType);
            var kit = com.solegendary.reignofnether.bot.BotPlayer.kitFor(f);
            if (kit == null || kit.capitol() != capitols.get(i))
                helper.fail(f.getName() + " lost its bot kit or its kit builds another capitol");
            var t = FactionTraits.of(f);
            if (t.dgunKind != dguns.get(i) || t.commanderAbility != signatures.get(i) || t.mana)
                helper.fail(f.getName() + "'s commander kit changed or it turned Mana");
        }
        if (storm.preview || !storm.playable || !Factions.isLive(storm) || !Factions.PLAYABLE_FACTIONS.contains(storm.key))
            helper.fail("the Stormcallers are not a live, playable faction");
        if (Factions.CLASSIC_FACTIONS.contains(storm.key) || Factions.SURVIVAL_FACTIONS.contains(storm.key))
            helper.fail("the Stormcallers leaked into the classic or survival list before they have a cube map / wave");
        if (ReignOfNetherRegistries.BUILDING.get(storm.capitolBuilding) != Buildings.JADE_PAGODA)
            helper.fail("the Stormcallers' capitol is not the Jade Pagoda: " + storm.capitolBuilding);
        if (!net.minecraft.world.entity.EntityType.getKey(com.solegendary.reignofnether.registrars.EntityRegistrar.GEOMANCER_UNIT.get())
                .equals(storm.workerEntityType))
            helper.fail("the Stormcallers' worker is not the Geomancer: " + storm.workerEntityType);
        var stormKit = com.solegendary.reignofnether.bot.BotPlayer.kitFor(storm);
        if (stormKit == null || stormKit.capitol() != Buildings.JADE_PAGODA || stormKit.armyBuilding() != Buildings.LOTUS_ACADEMY
                || stormKit.extractor() != Buildings.METAL_EXTRACTOR_STORM || stormKit.wind() != Buildings.WIND_GENERATOR_STORM)
            helper.fail("the Stormcallers bot kit is missing or builds the wrong capitol / lab / economy");
        else if (stormKit.t2Lab() != Buildings.CELESTIAL_OBSERVATORY || stormKit.t3Building() != null)   // slice 3: T2, no T3 yet
            helper.fail("the Stormcallers kit's T2 lab is not the Celestial Observatory, or it points at a T3 lab slice 4 does not have");
        if (com.solegendary.reignofnether.bot.BotPlayer.vaultFor(storm) != Buildings.STORAGE_VAULT_STORM)
            helper.fail("the Stormcallers bot does not build the Mana Cistern");
        // the Mana Cistern banks energy, not metal (design/stormcallers_plan.md 3.3)
        if (Buildings.STORAGE_VAULT_STORM.getEnergyStorage() <= Buildings.STORAGE_VAULT_VILLAGERS.getEnergyStorage()
                || Buildings.STORAGE_VAULT_STORM.getMetalStorage() >= Buildings.STORAGE_VAULT_VILLAGERS.getMetalStorage()
                || Buildings.STORAGE_VAULT_VILLAGERS.getEnergyStorage() != com.solegendary.reignofnether.building.buildings.shared.StorageVault.ENERGY_STORAGE)
            helper.fail("the Mana Cistern's storage split is wrong, or it changed the other vaults");
        // no Tier 3 offered, and Tier 2 sits in the capitol's usual slot (it only refits extractors for now)
        var pagodaItems = Buildings.JADE_PAGODA.productions.get();
        if (pagodaItems.contains(com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_3))
            helper.fail("the Jade Pagoda offers Tier 3 research that leads nowhere");
        if (Factions.getFaction(storm.key) != storm || !"stormcallers".equals(storm.getName()))
            helper.fail("the Stormcallers do not resolve by their key");
        if (ReignOfNetherRegistries.FACTIONS.getId(storm) < ReignOfNetherRegistries.FACTIONS.getId(Factions.TIDEWROUGHT))
            helper.fail("the Stormcallers were registered before the Tidewrought and shifted its id");
        // "Random" reaches all six; skirmish code 6 starts them now
        var rng = new java.util.Random(6);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 600; i++)
            seen.add(Factions.randomLive(rng).getName());
        if (!seen.contains("stormcallers") || seen.size() != 6)
            helper.fail("random should reach the six live factions, saw " + seen);
        if (SkirmishServerboundPacket.codeOf(storm) != 6 || SkirmishServerboundPacket.factionOf(6, rng) != storm)
            helper.fail("skirmish code 6 does not start the Stormcallers");
        // every unit it trains belongs to it (that is how its look and Mana rules are chosen)
        for (var type : List.of(com.solegendary.reignofnether.registrars.EntityRegistrar.GEOMANCER_UNIT.get(),
                com.solegendary.reignofnether.registrars.EntityRegistrar.PAPER_CRANE_UNIT.get(),
                com.solegendary.reignofnether.registrars.EntityRegistrar.BLADE_DANCER_UNIT.get(),
                com.solegendary.reignofnether.registrars.EntityRegistrar.FIRE_ADEPT_UNIT.get(),
                com.solegendary.reignofnether.registrars.EntityRegistrar.JADE_GUARD_UNIT.get(),
                com.solegendary.reignofnether.registrars.EntityRegistrar.WIND_MONK_UNIT.get()))
            if (!storm.key.equals(Factions.ENTITY_FACTION.get(net.minecraft.world.entity.EntityType.getKey(type))))
                helper.fail(net.minecraft.world.entity.EntityType.getKey(type) + " is not registered to the Stormcallers");
        // its own look and kit, never another faction's
        if (!FactionTraits.hasExplicit(storm))
            helper.fail("the Stormcallers have no explicit FactionTraits entry");
        var t = FactionTraits.of(storm);
        for (var f : others) {
            var o = FactionTraits.of(f);
            if (t == o || t.wreckBlock == o.wreckBlock || t.scaffoldPole == o.scaffoldPole
                    || (t.bannerCloth == o.bannerCloth && t.bannerTrim == o.bannerTrim) || t.accentArgb == o.accentArgb
                    || t.deathDebris == o.deathDebris || t.nanoTint == o.nanoTint)
                helper.fail("the Stormcallers borrow " + f.getName() + "'s look");
        }
        if (t.dgunKind != com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.JUDGEMENT
                || t.commanderAbility != com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.BLINK)
            helper.fail("the Thunder Sage's kit should be Heaven's Judgement + Blink, is " + t.dgunKind + " + " + t.commanderAbility);
        if (!t.mana || t.tidal || t.formation || t.momentum || t.metalExtractor.get() != Buildings.METAL_EXTRACTOR_STORM)
            helper.fail("the Stormcallers lost Mana, picked up another faction's mechanic, or have the wrong quick-build extractor");
        // the Wind Monk carries the wind-AA marker the Celestial Dragon will read, as do the other wind-type AA units
        var level = helper.getLevel();
        for (var type : List.of(com.solegendary.reignofnether.registrars.EntityRegistrar.WIND_MONK_UNIT.get(),
                com.solegendary.reignofnether.registrars.EntityRegistrar.WISP_CHOIR_UNIT.get(),
                com.solegendary.reignofnether.registrars.EntityRegistrar.FLAK_SWIVEL_UNIT.get(),
                com.solegendary.reignofnether.registrars.EntityRegistrar.WINDCALLER_UNIT.get())) {
            var e = type.create(level);
            if (!(e instanceof com.solegendary.reignofnether.unit.interfaces.WindAntiAir))
                helper.fail(net.minecraft.world.entity.EntityType.getKey(type) + " is not marked as wind-type anti-air");
            if (e != null)
                e.discard();
        }
        helper.succeed();
    }

    /**
     * The skirmish faction codes (SkirmishServerboundPacket.CODE_PATHS) behind the lobby's faction picker: every live
     * or preview faction has a code and maps back to itself, the historic codes keep their meaning (0-2 the first
     * three, 3 Random, 4 the Court, 5 the Tidewrought, 6 the Stormcallers), and the server only ever starts a live faction from
     * any code (Random, preview, unknown all roll a live one). Pure static data, safe in parallel.
     */
    @GameTest(template = ARENA)
    public static void skirmish_faction_codes_round_trip_for_every_faction(GameTestHelper helper) {
        if (SkirmishServerboundPacket.RANDOM_CODE != 3)
            helper.fail("the Random code moved off 3");
        var fixed = List.of(Factions.VILLAGERS, Factions.MONSTERS, Factions.PIGLINS, Factions.RANDOM,
            Factions.VERDANT_COURT, Factions.TIDEWROUGHT, Factions.STORMCALLERS);
        for (int c = 0; c < fixed.size(); c++)
            if (SkirmishServerboundPacket.factionForCode(c) != fixed.get(c))
                helper.fail("code " + c + " no longer names " + fixed.get(c).getName());
        var rng = new java.util.Random(7);
        for (var f : ReignOfNetherRegistries.FACTIONS) {
            if (!Factions.isLive(f) && !f.preview)
                continue;
            int code = SkirmishServerboundPacket.codeOf(f);
            if (code < 0)
                helper.fail(f.getName() + " has no skirmish faction code, so the picker can't offer it");
            if (SkirmishServerboundPacket.factionForCode(code) != f)
                helper.fail(f.getName() + "'s code " + code + " does not map back to it");
            var started = SkirmishServerboundPacket.factionOf(code, rng);
            if (Factions.isLive(f) ? started != f : !Factions.isLive(started))
                helper.fail("code " + code + " (" + f.getName() + ") started as " + started.getName());
        }
        for (int code : new int[]{ -1, 3, 5, 6, 99 })
            for (int i = 0; i < 50; i++)
                if (!Factions.isLive(SkirmishServerboundPacket.factionOf(code, rng))) {
                    helper.fail("code " + code + " started a faction that is not live");
                    return;
                }
        if (SkirmishServerboundPacket.factionForCode(99) != Factions.NONE
                || SkirmishServerboundPacket.codeOf(Factions.NEUTRAL) != -1)
            helper.fail("unknown codes / factions without a code are not reported as such");
        helper.succeed();
    }

    /** A Horde commander's D-gun / War-Drums must stay on the commander: Grunts used to share one static ability set. */
    @GameTest(template = ARENA)
    public static void commander_abilities_do_not_leak_to_plain_grunts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var commander = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get().create(level);
        var plain = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get().create(level);
        if (commander == null || plain == null) {
            helper.fail("could not create grunts");
            return;
        }
        com.solegendary.reignofnether.player.CommanderServerEvents.ensureAbility(commander);
        boolean commanderHas = false;
        for (var a : commander.getAbilities().get())
            if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderDGun)
                commanderHas = true;
        if (!commanderHas)
            helper.fail("the commander grunt did not get its D-gun");
        for (var a : plain.getAbilities().get())
            if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderDGun)
                helper.fail("a plain grunt got the commander's D-gun");
        commander.discard();
        plain.discard();
        helper.succeed();
    }

    /** Alex's Mobs (and Citadel under it) must be loaded with our mod: its entity types are registered and spawnable. */
    @GameTest(template = ARENA)
    public static void alexs_mobs_is_loaded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var types = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES;
        for (String path : new String[] {"grizzly_bear", "elephant", "shoebill"}) {   // shoebill: the Paper Crane's body
            ResourceLocation id = com.solegendary.reignofnether.compat.AlexsMobsCompat.id(path);
            // containsKey, not getValue: the entity registry is defaulted and returns a pig for unknown ids
            if (!types.containsKey(id)) {
                helper.fail("Alex's Mobs entity type not registered: " + id);
                return;
            }
            net.minecraft.world.entity.Entity e = types.getValue(id).create(level);
            if (e == null) {
                helper.fail("could not create " + id);
                return;
            }
            e.discard();
        }
        // the compile-linked reference must point at the same registered type as the name lookup
        if (com.solegendary.reignofnether.compat.AlexsMobsCompat.grizzlyBear()
                != types.getValue(com.solegendary.reignofnether.compat.AlexsMobsCompat.id("grizzly_bear")))
            helper.fail("AMEntityRegistry.GRIZZLY_BEAR differs from the registry entry");
        helper.succeed();
    }

    /**
     * Ability-leak audit: for EVERY unit type the mod registers, an ability added at runtime to one instance (what
     * CommanderServerEvents.ensureAbility and the T3 faction powers do) must not show up on a sibling made before or
     * after it - i.e. getAbilities() must never hand out the type's shared static set.
     */
    @GameTest(template = ARENA)
    public static void runtime_abilities_never_leak_between_siblings_of_any_unit_type(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> leaks = new ArrayList<>();
        int checked = 0;
        for (var type : net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES) {
            ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (key == null || !ReignOfNether.MOD_ID.equals(key.getNamespace()))
                continue;
            net.minecraft.world.entity.Entity a = null, b = null, c = null;
            try {
                a = type.create(level);
                b = type.create(level);
                if (!(a instanceof com.solegendary.reignofnether.unit.interfaces.Unit ua)
                        || !(b instanceof com.solegendary.reignofnether.unit.interfaces.Unit ub)
                        || ua.getAbilities() == null || ub.getAbilities() == null)
                    continue;
                var marker = new com.solegendary.reignofnether.ability.abilities.CommanderDGun();
                ua.getAbilities().add(marker);
                c = type.create(level);
                checked++;
                if (ub.getAbilities().get().contains(marker))
                    leaks.add(key.getPath() + " (existing sibling)");
                if (c instanceof com.solegendary.reignofnether.unit.interfaces.Unit uc && uc.getAbilities() != null
                        && uc.getAbilities().get().contains(marker))
                    leaks.add(key.getPath() + " (new sibling)");
                if (!ua.getAbilities().get().contains(marker))
                    leaks.add(key.getPath() + " (getAbilities() returns a fresh copy each call: runtime adds are lost)");
            } catch (Exception e) {
                // a type that cannot be built off-world is not what this test is about; it shows up elsewhere
            } finally {
                for (var e : new net.minecraft.world.entity.Entity[] {a, b, c})
                    if (e != null)
                        e.discard();
            }
        }
        if (checked < 20)
            helper.fail("only " + checked + " unit types were checked - the registry scan is broken");
        if (!leaks.isEmpty())
            helper.fail("runtime abilities leak between unit instances: " + leaks);
        helper.succeed();
    }

    /**
     * Every worker type that can be (or carry) a commander keeps the commander's runtime abilities - same instances,
     * so their cooldowns too - when updateAbilityButtons() re-clones from the static set (the client does that after
     * every cooldown sync), and its siblings never get them.
     */
    @GameTest(template = ARENA)
    public static void commander_abilities_survive_ability_button_refresh(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<net.minecraft.world.entity.EntityType<?>> types = List.of(
            com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.ROYAL_ARCHITECT_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_VILLAGER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.EMBALMER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.BONEWRIGHT_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.SEEDSHAPER_UNIT.get());
        List<String> problems = new ArrayList<>();
        for (var type : types) {
            var commander = type.create(level);
            var sibling = type.create(level);
            if (!(commander instanceof com.solegendary.reignofnether.unit.interfaces.Unit cu)
                    || !(sibling instanceof com.solegendary.reignofnether.unit.interfaces.Unit su)) {
                problems.add(type.getDescriptionId() + " could not be created");
                continue;
            }
            com.solegendary.reignofnether.player.CommanderServerEvents.ensureAbility(commander);
            com.solegendary.reignofnether.ability.Ability dgun = null;
            for (var a : cu.getAbilities().get())
                if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderDGun)
                    dgun = a;
            int before = cu.getAbilities().get().size();
            cu.updateAbilityButtons();
            cu.updateAbilityButtons();
            if (dgun == null)
                problems.add(type.getDescriptionId() + ": no D-gun after ensureAbility");
            else if (!cu.getAbilities().get().contains(dgun))
                problems.add(type.getDescriptionId() + ": updateAbilityButtons() wiped the commander's D-gun");
            if (cu.getAbilities().get().size() != before)
                problems.add(type.getDescriptionId() + ": ability count changed on refresh " + before + " -> "
                    + cu.getAbilities().get().size());
            for (var a : su.getAbilities().get())
                if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderDGun)
                    problems.add(type.getDescriptionId() + ": a plain sibling got the commander's D-gun");
            commander.discard();
            sibling.discard();
        }
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        helper.succeed();
    }

    /**
     * BAR's commander blast: when a commander dies, nothing happens for the 1 s telegraph, then friend and foe within
     * 8 blocks take the blast, and a unit outside it does not.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void commander_death_blast_hits_friend_and_foe_after_the_telegraph(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var reg = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get();
        var commander = reg.create(level);
        var friend = reg.create(level);
        var foe = reg.create(level);
        var outside = reg.create(level);
        if (commander == null || friend == null || foe == null || outside == null) {
            helper.fail("could not create grunt units");
            return;
        }
        // the commander stands in the middle of the 16x16 arena so the 8-block blast stays inside it (tests run in
        // parallel next to each other); the outsider sits in a corner, ~9.9 blocks away
        BlockPos at = helper.absolutePos(new BlockPos(8, 2, 8));
        commander.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        friend.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() + 0.5, at.getY(), at.getZ() - 3.5, 0, 0);
        outside.moveTo(at.getX() + 7.5, at.getY(), at.getZ() + 7.5, 0, 0);
        commander.setOwnerName("gametest_comblast");
        friend.setOwnerName("gametest_comblast");
        foe.setOwnerName("gametest_comblast_foe");
        outside.setOwnerName("gametest_comblast_foe");
        for (var e : List.of(commander, friend, foe, outside))
            level.addFreshEntity(e);
        com.solegendary.reignofnether.player.CommanderServerEvents.makeCommander(commander);
        final float[] hp = new float[3];
        final int delay = com.solegendary.reignofnether.player.CommanderServerEvents.COM_BLAST_DELAY_TICKS;
        helper.runAfterDelay(5, () -> {
            hp[0] = friend.getHealth();
            hp[1] = foe.getHealth();
            hp[2] = outside.getHealth();
            commander.kill();
        });
        // half-way through the telegraph: nobody hurt yet
        helper.runAfterDelay(5 + delay / 2, () -> {
            if (friend.getHealth() < hp[0] || foe.getHealth() < hp[1])
                helper.fail("the commander blast went off before its telegraph finished");
        });
        helper.runAfterDelay(5 + delay + 5, () -> {
            // 60 damage: either dead or well hurt (worker scuffles do a few points at most)
            if (friend.isAlive() && hp[0] - friend.getHealth() < 30)
                helper.fail("the commander blast did not hurt the friendly unit: " + friend.getHealth() + " (was " + hp[0] + ")");
            if (foe.isAlive() && hp[1] - foe.getHealth() < 30)
                helper.fail("the commander blast did not hurt the enemy unit: " + foe.getHealth() + " (was " + hp[1] + ")");
            if (!outside.isAlive() || outside.getHealth() < hp[2])
                helper.fail("the commander blast hurt a unit outside its radius");
            for (var e : List.of(commander, friend, foe, outside))
                e.discard();
            helper.succeed();
        });
    }

    /**
     * The Verdant Court's start, the way a readied player start does it (PlayerServerEvents.startRTS without the
     * player): its worker type spawns and the first one becomes the Grove Warden with Thornburst and Wildstride, and its
     * capitol foundation is laid at the start position and survives as a site. Also checks the Court's refit extractor
     * blueprint loads on the server (it is not a building's own structure, so the blueprint test can't see it), so a
     * Verdant player researching Tier 2 and refitting an extractor never meets a missing structure. Runs on its own
     * grass platform high above the arena.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void verdant_capitol_places_and_the_grove_warden_spawns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var verdant = Factions.VERDANT_COURT;
        String owner = "gametest_verdant_start";
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        // its own column at its own height: the old -60/+60 offset at y=220 put this pad over whichever arena the grid
        // placed there - the same bug the nano and capture point tests had (a pad above the stamped-patch test breaks
        // its heightmap, and the capture point test's own pad is at y=220)
        final int y = 260;
        int cx = base.getX(), cz = base.getZ();
        for (int dx = -9; dx <= 9; dx++)
            for (int dz = -9; dz <= 9; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        if (com.solegendary.reignofnether.building.BuildingBlockData.getBuildingNbt("metal_extractor_t2_verdant",
                level.getServer().getResourceManager()) == null)
            helper.fail("the Verdant refit extractor (metal_extractor_t2_verdant) is missing from data/");
        var workerType = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(verdant.workerEntityType);
        net.minecraft.world.entity.Entity warden = workerType == null ? null : workerType.create(level);
        if (!(warden instanceof com.solegendary.reignofnether.unit.units.verdant.SeedshaperUnit seed)) {
            helper.fail("the Verdant worker type does not make a Seedshaper: " + verdant.workerEntityType);
            return;
        }
        seed.setOwnerName(owner);
        seed.moveTo(cx + 0.5, y + 1, cz - 7.5, 0, 0);
        com.solegendary.reignofnether.player.CommanderServerEvents.makeCommander(seed);
        level.addFreshEntity(seed);
        boolean dgun = false, wild = false;
        for (var a : seed.getAbilities().get()) {
            dgun |= a instanceof com.solegendary.reignofnether.ability.abilities.CommanderDGun;
            wild |= a instanceof com.solegendary.reignofnether.ability.abilities.CommanderAbility;
        }
        if (!dgun || !wild)
            helper.fail("the Grove Warden lacks its D-gun (" + dgun + ") or signature (" + wild + ")");
        Building capitol = ReignOfNetherRegistries.BUILDING.get(verdant.capitolBuilding);
        var blocks = capitol.getRelativeBlockData(level);
        BlockPos origin = com.solegendary.reignofnether.player.PlayerServerEvents.getBuildingOriginPos(
            new BlockPos(cx, y, cz), blocks);
        var placement = com.solegendary.reignofnether.building.BuildingServerEvents.placeBuilding(capitol, origin,
            Rotation.NONE, owner, new int[] { seed.getId() }, false, false, true, true);
        if (placement == null) {
            seed.discard();
            helper.fail("the Heartwood Hall could not be placed at a readied start");
            return;
        }
        helper.runAfterDelay(40, () -> {
            try {
                if (placement.isDestroyedServerside
                        || !com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().contains(placement))
                    helper.fail("the Heartwood Hall site was destroyed right after being placed");
                if (!placement.getBuilding().isCapitol || placement.getBuilding() != Buildings.HEARTWOOD_HALL)
                    helper.fail("the placed capitol is " + placement.getBuilding().name);
                helper.succeed();
            } finally {
                com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(placement);
                seed.discard();
            }
        });
    }

    /**
     * A glass floor over this test's whole arena footprint at height y (its own column, so it never lands over another
     * test's arena). For tests whose units fight on their own: the grid packs arenas ~21 blocks apart, well inside an
     * archer's aggro range, so on the arena floor a unit picks (or is shot by) a neighbouring test's units - the flaky
     * verdant_units_are_made_and_attack and shade_ranger_cloaks_when_idle_and_reveals_on_fire. Each caller takes its
     * own height, far enough from every other pad (3D distance) to be out of its units' aggro range. Glass, because no
     * mob spawns on it (a night-time zombie, or neutralAggro wildlife, would be one more stranger to target).
     */
    private static BlockPos skyPad(GameTestHelper helper, int y) {
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++)
                helper.getLevel().setBlock(new BlockPos(corner.getX() + dx, y, corner.getZ() + dz), Blocks.GLASS.defaultBlockState(), 3);
        return new BlockPos(corner.getX(), y + 1, corner.getZ());   // standing height at the arena's corner
    }

    /** "type#id(owner)" for a failure message; "none" for null. */
    private static String describe(net.minecraft.world.entity.Entity e) {
        if (e == null)
            return "none";
        String owner = e instanceof com.solegendary.reignofnether.unit.interfaces.Unit u ? u.getOwnerName() : "wild";
        return net.minecraft.world.entity.EntityType.getKey(e.getType()).getPath() + "#" + e.getId() + "(" + owner + ")";
    }

    /** The closest living thing within range not owned by owner (and not in ignore), with its distance - or "none". */
    private static String nearestStranger(net.minecraft.world.entity.Entity from, String owner, double range,
                                          List<? extends net.minecraft.world.entity.Entity> ignore) {
        net.minecraft.world.entity.LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (var e : from.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                from.getBoundingBox().inflate(range))) {
            if (e == from || ignore.contains(e)
                    || (e instanceof com.solegendary.reignofnether.unit.interfaces.Unit u && owner.equals(u.getOwnerName())))
                continue;
            double d = e.distanceTo(from);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best == null ? "none" : describe(best) + String.format(" at %.1f", bestD);
    }

    /**
     * Every Verdant unit can be made and fights: each one, ordered onto its own enemy Villager, lands a blow (melee or
     * arrow) within a few seconds, and the Thornbow's arrows root what they hit. One row per attacker, on a sky pad of
     * this test's own (see skyPad: on the arena floor the Thornbow, aggro 13 from 2 blocks off the edge, was picking
     * and being shot by a neighbouring test's units - "Thornbow never hit its target"). The order is re-asserted every
     * tick and any stray target is reported, so a future failure says what the unit was doing instead.
     */
    @GameTest(template = ARENA, timeoutTicks = 340)
    public static void verdant_units_are_made_and_attack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_verdant_atk", foeOwner = "gametest_verdant_atk_foe";
        BlockPos pad = skyPad(helper, 285);   // 25+ above the capitol test's pad (260) and 27 below the shade ranger's (312)
        List<net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob>> types = List.of(
            com.solegendary.reignofnether.registrars.EntityRegistrar.SEEDSHAPER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.FOX_COURIER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.LEAFBLADE_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.THORNBOW_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.SENTINEL_TREANT_UNIT.get());
        List<net.minecraft.world.entity.Mob> attackers = new ArrayList<>(), foes = new ArrayList<>();
        for (int i = 0; i < types.size(); i++) {
            var atk = types.get(i).create(level);
            var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
            if (!(atk instanceof com.solegendary.reignofnether.unit.interfaces.AttackerUnit) || foe == null) {
                helper.fail("could not create " + net.minecraft.world.entity.EntityType.getKey(types.get(i)) + " as an attacker");
                for (var e : attackers) e.discard();
                for (var e : foes) e.discard();
                return;
            }
            boolean ranged = atk instanceof com.solegendary.reignofnether.unit.units.verdant.ThornbowUnit;
            // rows 3 apart in the middle of the pad, clear of its edges (x 4..11.5, z 2..14)
            atk.moveTo(pad.getX() + (ranged ? 4.5 : 6.5), pad.getY(), pad.getZ() + 2.5 + i * 3, -90, 0);
            foe.moveTo(pad.getX() + (ranged ? 11.5 : 8.5), pad.getY(), pad.getZ() + 2.5 + i * 3, 90, 0);
            ((com.solegendary.reignofnether.unit.interfaces.Unit) atk).setOwnerName(owner);
            foe.setOwnerName(foeOwner);
            level.addFreshEntity(atk);
            level.addFreshEntity(foe);
            attackers.add(atk);
            foes.add(foe);
        }
        List<net.minecraft.world.entity.Mob> all = new ArrayList<>(attackers);
        all.addAll(foes);
        boolean[] landed = new boolean[attackers.size()];
        boolean[] rooted = { false };
        boolean[] done = { false };
        int[] strays = new int[attackers.size()];
        String[] lastStray = new String[attackers.size()];
        int thornbow = 3;
        final int last = 300;
        for (int t = 3; t <= last; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (done[0])
                    return;
                for (int i = 0; i < attackers.size(); i++) {
                    var atk = attackers.get(i);
                    var foe = foes.get(i);
                    // keep each attacker on its own Villager: a retaliation or an aggro pick must not pull it away
                    var goal = ((com.solegendary.reignofnether.unit.interfaces.Unit) atk).getTargetGoal();
                    net.minecraft.world.entity.LivingEntity cur = goal == null ? null : goal.getTarget();
                    if (cur != foe && foe.isAlive()) {
                        if (cur != null && tick > 3) {
                            strays[i]++;
                            lastStray[i] = describe(cur);
                        }
                        ((com.solegendary.reignofnether.unit.interfaces.AttackerUnit) atk).setUnitAttackTarget(foe);
                    }
                    // a blow on any of this test's Villagers counts (foeOwner is unique to this test)
                    if ((atk.getLastHurtMob() instanceof com.solegendary.reignofnether.unit.interfaces.Unit u
                            && foeOwner.equals(u.getOwnerName())) || foe.getLastHurtByMob() == atk)
                        landed[i] = true;
                }
                var mark = foes.get(thornbow).getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
                if (mark != null && mark.getAmplifier()
                        >= com.solegendary.reignofnether.unit.units.verdant.ThornbowUnit.ROOT_AMPLIFIER)
                    rooted[0] = true;
                boolean allLanded = true;
                for (boolean b : landed)
                    allLanded &= b;
                if (allLanded && rooted[0]) {
                    done[0] = true;
                    for (var e : all) e.discard();
                    helper.succeed();
                    return;
                }
                if (tick < last)
                    return;
                done[0] = true;
                List<String> problems = new ArrayList<>();
                for (int i = 0; i < attackers.size(); i++) {
                    if (landed[i])
                        continue;
                    var atk = attackers.get(i);
                    var foe = foes.get(i);
                    var goal = ((com.solegendary.reignofnether.unit.interfaces.Unit) atk).getTargetGoal();
                    problems.add(net.minecraft.world.entity.EntityType.getKey(types.get(i)).getPath()
                        + " never hit its target " + describe(foe) + String.format(" (dist %.1f, foe hp %.1f%s;", atk.distanceTo(foe),
                            foe.getHealth(), foe.isAlive() ? "" : " dead")
                        + " attacker " + (atk.isAlive() ? String.format("hp %.1f", atk.getHealth()) : "dead")
                        + String.format(" at %.1f/%.1f/%.1f", atk.getX(), atk.getY(), atk.getZ())
                        + ", targeting " + describe(goal == null ? null : goal.getTarget())
                        + ", last hurt " + describe(atk.getLastHurtMob()) + ", hurt by " + describe(atk.getLastHurtByMob())
                        + ", foe hurt by " + describe(foe.getLastHurtByMob())
                        + ", strays " + strays[i] + (lastStray[i] == null ? "" : " last " + lastStray[i])
                        + ", nearest stranger " + nearestStranger(atk, owner, 30, foes) + ")");
                }
                if (!rooted[0]) {
                    var foe = foes.get(thornbow);
                    var slow = foe.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
                    problems.add("the Thornbow's arrows did not root their target (hit " + landed[thornbow] + ", foe "
                        + (foe.isAlive() ? "alive" : "dead") + ", slowness " + (slow == null ? "none" : "amp " + slow.getAmplifier()) + ")");
                }
                for (var e : all) e.discard();
                helper.fail(String.join("; ", problems));
            });
        }
    }

    /**
     * The Grove Warden: Thornburst hits and roots the enemy in its cone, spares the friend in it and the enemy behind the
     * commander, charges energy and cools down; Wildstride speeds a nearby ally. Leaf Dash carries a Leafblade most of
     * its length and cuts the enemy it passes.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void verdant_thornburst_wildstride_and_leaf_dash(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_verdant_warden", foeOwner = "gametest_verdant_target";
        var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 0, 0);
        pool.addEnergy(500);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
        var seed = com.solegendary.reignofnether.registrars.EntityRegistrar.SEEDSHAPER_UNIT.get();
        var vill = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get();
        var warden = seed.create(level);
        var inCone = vill.create(level);       // ahead, off the centre line: a line D-gun would miss it, the cone not
        var friend = vill.create(level);       // ahead, ours
        var behind = vill.create(level);       // behind the commander
        var blade = com.solegendary.reignofnether.registrars.EntityRegistrar.LEAFBLADE_UNIT.get().create(level);
        var onPath = vill.create(level);       // in the Leafblade's way
        if (warden == null || inCone == null || friend == null || behind == null || blade == null || onPath == null) {
            helper.fail("could not create the Verdant test units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 4));
        warden.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        inCone.moveTo(at.getX() + 5.5, at.getY(), at.getZ() + 2.5, 0, 0);    // ~24 degrees off +x, 5.7 blocks
        friend.moveTo(at.getX() + 4.5, at.getY(), at.getZ() + 0.5, 0, 0);
        behind.moveTo(at.getX() - 2.5, at.getY(), at.getZ() + 0.5, 0, 0);
        BlockPos lane = helper.absolutePos(new BlockPos(2, 2, 11));
        blade.moveTo(lane.getX() + 0.5, lane.getY(), lane.getZ() + 0.5, -90, 0);
        onPath.moveTo(lane.getX() + 4.5, lane.getY(), lane.getZ() + 0.5, 0, 0);
        warden.setOwnerName(owner);
        friend.setOwnerName(owner);
        blade.setOwnerName(owner);
        inCone.setOwnerName(foeOwner);
        behind.setOwnerName(foeOwner);
        onPath.setOwnerName(foeOwner);
        List<net.minecraft.world.entity.Mob> all = List.of(warden, inCone, friend, behind, blade, onPath);
        for (var e : all)
            level.addFreshEntity(e);
        com.solegendary.reignofnether.player.CommanderServerEvents.makeCommander(warden);
        com.solegendary.reignofnether.ability.abilities.CommanderDGun dgun = null;
        com.solegendary.reignofnether.ability.abilities.CommanderAbility wild = null;
        for (var a : warden.getAbilities().get()) {
            if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderDGun d) dgun = d;
            if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderAbility c) wild = c;
        }
        com.solegendary.reignofnether.ability.abilities.LeafDash dash = null;
        for (var a : blade.getAbilities().get())
            if (a instanceof com.solegendary.reignofnether.ability.abilities.LeafDash d) dash = d;
        if (dgun == null || wild == null || dash == null) {
            helper.fail("missing ability: Thornburst " + dgun + ", Wildstride " + wild + ", Leaf Dash " + dash);
            for (var e : all) e.discard();
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
            return;
        }
        final var thornburst = dgun;
        final var wildstride = wild;
        helper.runAfterDelay(5, () -> {
            try {
                float friendHp = friend.getHealth(), behindHp = behind.getHealth(), pathHp = onPath.getHealth();
                thornburst.use(level, warden, BlockPos.containing(at.getX() + 12, at.getY(), at.getZ()));
                if (inCone.isAlive() && inCone.getMaxHealth() - inCone.getHealth() < 40)
                    helper.fail("Thornburst barely hurt the enemy in its cone: " + inCone.getHealth());
                if (inCone.isAlive() && !inCone.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN))
                    helper.fail("Thornburst did not root the enemy it hit");
                if (friend.getHealth() < friendHp)
                    helper.fail("Thornburst hurt a friendly unit");
                if (behind.getHealth() < behindHp)
                    helper.fail("Thornburst hurt an enemy behind the commander");
                if (pool.getEnergy() > 500 - com.solegendary.reignofnether.ability.abilities.CommanderDGun.ENERGY_COST + 0.01)
                    helper.fail("Thornburst did not charge energy: " + pool.getEnergy());
                if (thornburst.isOffCooldown(warden))
                    helper.fail("Thornburst did not go on cooldown");

                wildstride.use(level, warden, warden.blockPosition());
                if (!friend.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED))
                    helper.fail("Wildstride did not speed the nearby ally");
                if (behind.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED))
                    helper.fail("Wildstride sped an enemy");

                double x0 = blade.getX();
                var d = com.solegendary.reignofnether.ability.abilities.LeafDash.begin(level, blade, owner,
                    new net.minecraft.world.phys.Vec3(lane.getX() + 12.5, lane.getY(), lane.getZ() + 0.5), null);
                if (d == null) {
                    helper.fail("Leaf Dash refused to start");
                    return;
                }
                // stepped by hand: the test must not depend on the server tick running the dash
                com.solegendary.reignofnether.ability.abilities.LeafDash.cancel(d);
                for (int i = 0; i < com.solegendary.reignofnether.ability.abilities.LeafDash.DURATION_TICKS; i++)
                    com.solegendary.reignofnether.ability.abilities.LeafDash.step(d);
                if (blade.getX() - x0 < com.solegendary.reignofnether.ability.abilities.LeafDash.LENGTH - 2)
                    helper.fail("the Leafblade only dashed " + (blade.getX() - x0) + " blocks");
                if (onPath.isAlive() && onPath.getHealth() >= pathHp)
                    helper.fail("Leaf Dash did not cut the enemy on its path");
                helper.succeed();
            } finally {
                for (var e : all)
                    e.discard();
                com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
            }
        });
    }

    /**
     * Wildlife never hunts a commander: neutralAggro points every wild hunter (Alex's Mobs roadrunners, rattlesnakes...)
     * at the closest unit, and a commander killed by one lost a 1v1 to a bird (the flaky bot soak). A wild wolf may
     * still go for an ordinary unit, and an enemy unit may still target the commander.
     */
    @GameTest(template = ARENA)
    public static void wildlife_does_not_hunt_commanders(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var reg = com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get();
        var commander = reg.create(level);
        var grunt = reg.create(level);
        var foe = reg.create(level);
        var wolf = net.minecraft.world.entity.EntityType.WOLF.create(level);
        if (commander == null || grunt == null || foe == null || wolf == null) {
            helper.fail("could not create the units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(8, 2, 8));
        commander.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        grunt.moveTo(at.getX() + 2.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5, 0, 0);
        wolf.moveTo(at.getX() - 2.5, at.getY(), at.getZ() + 0.5, 0, 0);
        commander.setOwnerName("gametest_wild_cmdr");
        grunt.setOwnerName("gametest_wild_cmdr");
        foe.setOwnerName("gametest_wild_foe");
        for (var e : List.<net.minecraft.world.entity.Entity>of(commander, grunt, foe, wolf))
            level.addFreshEntity(e);
        com.solegendary.reignofnether.player.CommanderServerEvents.makeCommander(commander);
        wolf.setTarget(commander);
        net.minecraft.world.entity.LivingEntity wolfOnCommander = wolf.getTarget();
        wolf.setTarget(grunt);
        net.minecraft.world.entity.LivingEntity wolfOnGrunt = wolf.getTarget();
        // a unit's own target goes through its RoN target goal (which wants real RTS players), so ask the guard
        // itself: the target-change event for an enemy unit on the commander must not be cancelled
        var evt = new net.minecraftforge.event.entity.living.LivingChangeTargetEvent(foe, commander,
            net.minecraftforge.event.entity.living.LivingChangeTargetEvent.LivingTargetType.MOB_TARGET);
        boolean foeBlocked = net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(evt);
        for (var e : List.<net.minecraft.world.entity.Entity>of(commander, grunt, foe, wolf))
            e.discard();
        if (wolfOnCommander == commander)
            helper.fail("a wild wolf was allowed to target a commander");
        else if (wolfOnGrunt != grunt)
            helper.fail("a wild wolf could not target an ordinary unit (the guard is too broad)");
        else if (foeBlocked)
            helper.fail("an enemy unit could not target the commander");
        else
            helper.succeed();
    }

    /**
     * Verdant Court T2 (slice 4): the Circle of Elders trains the Elder Druid and the three T2 units, and refuses all of
     * them until its owner has Tier 2 - checked on the server when production starts, not only by the build button. The
     * Court's bot kit points at it. (Tier 3 at the Heartwood Hall is checked by verdant_t3_lab_and_tier_3.)
     */
    @GameTest(template = ARENA)
    public static void verdant_t2_lab_trains_only_after_tier_2(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_verdant_t2";
        var items = List.<com.solegendary.reignofnether.building.production.ProductionItem>of(
            com.solegendary.reignofnether.building.production.ProductionItems.ELDER_DRUID,
            com.solegendary.reignofnether.building.production.ProductionItems.STAG_LANCER,
            com.solegendary.reignofnether.building.production.ProductionItems.SHADE_RANGER,
            com.solegendary.reignofnether.building.production.ProductionItems.ELDER_TREANT);
        var lab = Buildings.CIRCLE_OF_ELDERS;
        for (var item : items)
            if (!lab.productions.get().contains(item))
                helper.fail("the Circle of Elders does not train " + item.getItemName());
        var kit = com.solegendary.reignofnether.bot.BotPlayer.kitFor(Factions.VERDANT_COURT);
        if (kit == null || kit.t2Lab() != lab || kit.t2Worker() != com.solegendary.reignofnether.building.production.ProductionItems.ELDER_DRUID)
            helper.fail("the Verdant bot kit does not use the Circle of Elders and the Elder Druid");
        // a placement that is never put in the world: enough for the produce gate, and invisible to parallel tests
        var bp = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(lab, level,
            helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, owner, false);
        if (!(bp instanceof com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement pp)) {
            helper.fail("the Circle of Elders is not a production placement: " + bp);
            return;
        }
        com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(owner);
        try {
            for (var item : items)
                if (item.canProduce(pp))
                    helper.fail(item.getItemName() + " can be trained without Tier 2");
            if (pp.startProductionItem(com.solegendary.reignofnether.building.production.ProductionItems.STAG_LANCER) || !pp.productionQueue.isEmpty())
                helper.fail("the server queued a Stag Lancer without Tier 2");
            com.solegendary.reignofnether.research.ResearchServerEvents.addResearch(owner,
                com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_2);
            for (var item : items)
                if (!item.canProduce(pp))
                    helper.fail(item.getItemName() + " still refused after Tier 2: " + item.getProduceErrorMsg(pp));
            helper.succeed();
        } finally {
            com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(owner);
        }
    }

    /**
     * The Elder Druid is the Court's T2 constructor: double build power and health, a T2 worker (so it may raise a T3
     * lab), Verdant, with Awaken Thicket - which a plain Seedshaper must not get. Awaken Thicket wakes a tagged, owned
     * Sentinel Treant that is dismissed again.
     */
    @GameTest(template = ARENA)
    public static void elder_druid_is_a_t2_worker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_elder_druid";
        var druid = com.solegendary.reignofnether.registrars.EntityRegistrar.ELDER_DRUID_UNIT.get().create(level);
        var seed = com.solegendary.reignofnether.registrars.EntityRegistrar.SEEDSHAPER_UNIT.get().create(level);
        if (druid == null || seed == null) {
            helper.fail("could not create the workers");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(8, 2, 8));
        druid.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        seed.moveTo(at.getX() + 2.5, at.getY(), at.getZ() + 0.5, 0, 0);
        druid.setOwnerName(owner);
        seed.setOwnerName(owner);
        level.addFreshEntity(druid);
        level.addFreshEntity(seed);
        net.minecraft.world.entity.LivingEntity treant = null;
        try {
            if (druid.getBuildPower() < 1.99f)
                helper.fail("the Elder Druid builds at " + druid.getBuildPower());
            if (!com.solegendary.reignofnether.unit.T2Workers.isT2Worker(druid))
                helper.fail("the Elder Druid is not a T2 worker");
            if (com.solegendary.reignofnether.unit.T2Workers.isT2Worker(seed))
                helper.fail("a plain Seedshaper counts as a T2 worker");
            if (!Factions.VERDANT_COURT.equals(Factions.getFaction(druid)))
                helper.fail("the Elder Druid is not Verdant Court");
            if (druid.getMaxHealth() < 2 * seed.getMaxHealth() - 0.01f)
                helper.fail("the Elder Druid has " + druid.getMaxHealth() + " health, the Seedshaper " + seed.getMaxHealth());
            if (druid.getAbilities().get().stream().noneMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.AwakenThicket))
                helper.fail("the Elder Druid lacks Awaken Thicket");
            if (seed.getAbilities().get().stream().anyMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.AwakenThicket))
                helper.fail("a plain Seedshaper got Awaken Thicket");
            if (!com.solegendary.reignofnether.unit.T2Workers.buildersMayPlace(level, Buildings.CASTLE, new int[] { druid.getId() }))
                helper.fail("a T3 lab refused an Elder Druid builder");
            treant = com.solegendary.reignofnether.ability.abilities.AwakenThicket.awaken(level, owner, at.offset(4, -1, 0));
            if (treant == null)
                helper.fail("Awaken Thicket woke nothing");
            else if (!(treant instanceof com.solegendary.reignofnether.unit.units.verdant.SentinelTreantUnit st) || !owner.equals(st.getOwnerName())
                    || !com.solegendary.reignofnether.ability.abilities.AwakenThicket.isAwakened(treant))
                helper.fail("the awakened treant is not an owned, tagged Sentinel Treant: " + treant);
            else
                helper.succeed();
        } finally {
            if (treant != null)
                com.solegendary.reignofnether.ability.abilities.AwakenThicket.dismiss(treant);
            druid.discard();
            seed.discard();
        }
    }

    /**
     * The Shade Ranger cloaks after standing still for 3 s (invisible, and an enemy's target search skips it) and is
     * revealed the moment it fires. The enemy is never added to the level, so the ranger can't see it and break cover.
     * It stands on a sky pad of this test's own (see skyPad): on the arena floor its 24-block aggro reached the units of
     * neighbouring tests, and an engaged ranger never cloaks - the intermittent "did not cloak" failure. Polls for the
     * cloak with margin, and a failure says what kept it visible (target, still ticks, drift, nearest stranger).
     */
    @GameTest(template = ARENA, timeoutTicks = 260)
    public static void shade_ranger_cloaks_when_idle_and_reveals_on_fire(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_shade_owner";
        var ranger = com.solegendary.reignofnether.registrars.EntityRegistrar.SHADE_RANGER_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (ranger == null || foe == null) {
            helper.fail("could not create the units");
            return;
        }
        BlockPos pad = skyPad(helper, 312);   // the highest pad: 27 above verdant_units_are_made_and_attack's (285)
        double sx = pad.getX() + 8.5, sz = pad.getZ() + 8.5;   // the middle of the pad
        ranger.moveTo(sx, pad.getY(), sz, 0, 0);
        foe.moveTo(sx + 3, pad.getY(), sz, 0, 0);
        ranger.setOwnerName(owner);
        foe.setOwnerName("gametest_shade_foe");
        level.addFreshEntity(ranger);
        final int cloakBy = 5 + com.solegendary.reignofnether.unit.units.verdant.ShadeRangerUnit.CLOAK_DELAY_TICKS + 20;
        final int last = cloakBy + 120;   // margin: a stray tick of movement on landing restarts the 3 s count
        boolean[] seenVisible = { false };
        boolean[] done = { false };
        helper.runAfterDelay(5, () -> {
            if (ranger.isInvisible()) {
                done[0] = true;
                ranger.discard();
                helper.fail("the Shade Ranger cloaked straight away");
                return;
            }
            // control: while visible, the enemy's target search finds it
            seenVisible[0] = com.solegendary.reignofnether.util.MiscUtil.findClosestAttackableEntity(foe, 8, level) == ranger;
        });
        for (int t = cloakBy; t <= last; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (done[0])
                    return;
                boolean cloaked = ranger.isInvisible()
                    && com.solegendary.reignofnether.unit.units.verdant.ShadeRangerUnit.isCloaked(ranger);
                if (!cloaked && tick < last)
                    return;
                done[0] = true;
                try {
                    if (!cloaked) {
                        var goal = ranger.getTargetGoal();
                        helper.fail("the Shade Ranger did not cloak after standing still (" + tick + " ticks: alive "
                            + ranger.isAlive() + ", invisible " + ranger.isInvisible() + ", still ticks "
                            + ranger.getStillTicks() + ", targeting " + describe(goal == null ? null : goal.getTarget())
                            + String.format(", drifted %.2f", Math.hypot(ranger.getX() - sx, ranger.getZ() - sz))
                            + ", passenger " + ranger.isPassenger() + ", hurt by " + describe(ranger.getLastHurtByMob())
                            + ", nearest stranger " + nearestStranger(ranger, owner, 30, List.of()) + ")");
                        return;
                    }
                    if (seenVisible[0] && com.solegendary.reignofnether.util.MiscUtil.findClosestAttackableEntity(foe, 8, level) == ranger)
                        helper.fail("an enemy can still pick the cloaked Shade Ranger as a target");
                    ranger.performUnitRangedAttack(foe, 1f);
                    if (ranger.isInvisible())
                        helper.fail("the Shade Ranger stayed cloaked after firing");
                    else
                        helper.succeed();
                } finally {
                    ranger.discard();
                    foe.discard();
                }
            });
        }
    }

    /**
     * The Elder Treant's boulder hurts the enemies where it lands and spares friends; the treant aims at an enemy in
     * range on its own (its target pick never returns a friend).
     */
    @GameTest(template = ARENA)
    public static void elder_treant_boulder_damages_foes_not_friends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_treant_owner", foeOwner = "gametest_treant_foe";
        var reg = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get();
        var treant = com.solegendary.reignofnether.registrars.EntityRegistrar.ELDER_TREANT_UNIT.get().create(level);
        var friend = reg.create(level);
        var foe = reg.create(level);
        if (treant == null || friend == null || foe == null) {
            helper.fail("could not create the units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 8));
        treant.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        friend.moveTo(at.getX() + 8.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() + 9.5, at.getY(), at.getZ() + 1.5, 0, 0);
        treant.setOwnerName(owner);
        friend.setOwnerName(owner);
        foe.setOwnerName(foeOwner);
        List<net.minecraft.world.entity.Mob> all = List.of(treant, friend, foe);
        for (var e : all)
            level.addFreshEntity(e);
        helper.runAfterDelay(2, () -> {
            try {
                var mark = treant.boulderTarget(level);
                if (mark != foe)
                    helper.fail("the Elder Treant aimed its boulder at " + mark + ", not the enemy in range");
                float friendHp = friend.getHealth(), foeHp = foe.getHealth();
                var hit = com.solegendary.reignofnether.ability.abilities.BoulderToss.impact(level, treant, owner,
                    new net.minecraft.world.phys.Vec3(at.getX() + 9, at.getY(), at.getZ() + 1));
                if (!hit.contains(foe) || (foe.isAlive() && foe.getHealth() >= foeHp))
                    helper.fail("the boulder did not hurt the enemy where it landed");
                if (hit.contains(friend) || friend.getHealth() < friendHp)
                    helper.fail("the boulder hurt a friendly unit");
                helper.succeed();
            } finally {
                for (var e : all)
                    e.discard();
            }
        });
    }


    // ------------------------------------------------------------------ Verdant Court slice 2

    /**
     * The Moonwell Bearer's pulse heals a hurt friendly unit, never a hurt enemy standing just as close, and never
     * itself.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void verdant_moonwell_heals_friend_not_foe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_moonwell", foeOwner = "gametest_moonwell_foe";
        var bearer = com.solegendary.reignofnether.registrars.EntityRegistrar.MOONWELL_BEARER_UNIT.get().create(level);
        var vill = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get();
        var friend = vill.create(level);
        var foe = vill.create(level);
        if (bearer == null || friend == null || foe == null) {
            helper.fail("could not create the Moonwell test units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(8, 2, 8));
        bearer.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        friend.moveTo(at.getX() + 2.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() - 1.5, at.getY(), at.getZ() + 0.5, 0, 0);
        bearer.setOwnerName(owner);
        friend.setOwnerName(owner);
        foe.setOwnerName(foeOwner);
        List<net.minecraft.world.entity.Mob> all = List.of(bearer, friend, foe);
        for (var e : all)
            level.addFreshEntity(e);
        helper.runAfterDelay(5, () -> {
            try {
                friend.setHealth(friend.getMaxHealth() * 0.4f);
                foe.setHealth(foe.getMaxHealth() * 0.4f);
                bearer.setHealth(bearer.getMaxHealth() * 0.5f);
                float friendHp = friend.getHealth(), foeHp = foe.getHealth(), selfHp = bearer.getHealth();
                int healed = bearer.pulse(level);
                if (healed != 1)
                    helper.fail("the pulse should heal exactly the one hurt friend, healed " + healed);
                if (friend.getHealth() <= friendHp)
                    helper.fail("the hurt friend was not healed: " + friendHp + " -> " + friend.getHealth());
                if (foe.getHealth() > foeHp)
                    helper.fail("the Moonwell Bearer healed an enemy");
                if (bearer.getHealth() > selfHp)
                    helper.fail("the Moonwell Bearer healed itself");
                float expect = bearer.isNightPower()
                        ? com.solegendary.reignofnether.unit.units.verdant.MoonwellBearerUnit.NIGHT_HEAL
                        : com.solegendary.reignofnether.unit.units.verdant.MoonwellBearerUnit.DAY_HEAL;
                if (Math.abs(friend.getHealth() - friendHp - expect) > 0.01f)
                    helper.fail("healed " + (friend.getHealth() - friendHp) + ", expected " + expect);
                helper.succeed();
            } finally {
                for (var e : all)
                    e.discard();
            }
        });
    }

    /**
     * A Vine Snare ignores its owner's units, then roots the first enemy unit to walk into it (Slowness VII) and is
     * spent: of two enemies dropped on it at once, exactly one is rooted.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void verdant_vine_snare_roots_first_enemy_and_is_spent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_snare", foeOwner = "gametest_snare_foe";
        BlockPos spot = helper.absolutePos(new BlockPos(7, 2, 7));   // mid-arena, away from neighbours' stamps
        // a clean, open spot on solid ground whatever a neighbouring test stamped here (CI once found it occupied)
        level.setBlock(spot.below(), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        for (int dy = 0; dy < 3; dy++)
            level.setBlock(spot.above(dy), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        if (!com.solegendary.reignofnether.ability.abilities.PlantVineSnare.plant(level, owner, spot, false)) {
            helper.fail("the Vine Snare could not be planted at " + spot + " (found " + level.getBlockState(spot)
                    + ", " + com.solegendary.reignofnether.blocks.VineSnareBlockEntity.countOwned(level, owner) + " owned)");
            return;
        }
        if (!(level.getBlockEntity(spot) instanceof com.solegendary.reignofnether.blocks.VineSnareBlockEntity be)
                || !owner.equals(be.getOwner())) {
            helper.fail("the planted Vine Snare has no owner: " + level.getBlockEntity(spot));
            level.removeBlock(spot, false);
            return;
        }
        var vill = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get();
        var friend = vill.create(level);
        var foeA = vill.create(level);
        var foeB = vill.create(level);
        if (friend == null || foeA == null || foeB == null) {
            helper.fail("could not create the snare test units");
            level.removeBlock(spot, false);
            return;
        }
        friend.setOwnerName(owner);
        foeA.setOwnerName(foeOwner);
        foeB.setOwnerName(foeOwner);
        friend.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
        level.addFreshEntity(friend);
        List<net.minecraft.world.entity.Mob> all = List.of(friend, foeA, foeB);
        var snareBlock = com.solegendary.reignofnether.registrars.BlockRegistrar.VINE_SNARE.get();
        helper.runAfterDelay(10, () -> {
            boolean ok = true;
            if (!level.getBlockState(spot).is(snareBlock)) {
                helper.fail("the snare was sprung by its owner's own unit");
                ok = false;
            } else if (friend.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)) {
                helper.fail("the snare rooted a friendly unit");
                ok = false;
            }
            if (!ok) {
                for (var e : all) e.discard();
                level.removeBlock(spot, false);
                return;
            }
            friend.moveTo(spot.getX() + 3.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
            foeA.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.4, 0, 0);
            foeB.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.6, 0, 0);
            level.addFreshEntity(foeA);
            level.addFreshEntity(foeB);
        });
        helper.runAfterDelay(25, () -> {
            try {
                int rooted = 0;
                for (var foe : List.of(foeA, foeB)) {
                    var root = foe.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
                    if (root != null && root.getAmplifier() >= com.solegendary.reignofnether.blocks.VineSnareBlock.ROOT_AMPLIFIER)
                        rooted++;
                }
                if (level.getBlockState(spot).is(snareBlock))
                    helper.fail("the snare is still there after enemies walked into it");
                else if (rooted != 1)
                    helper.fail("the snare should root exactly the first enemy, rooted " + rooted);
                else
                    helper.succeed();
            } finally {
                for (var e : all)
                    e.discard();
                if (level.getBlockState(spot).is(snareBlock))
                    level.removeBlock(spot, false);
            }
        });
    }

    /**
     * The Hive Keeper's swarm: three bees owned by the keeper's player that fly at a nearby enemy and sting it. On a sky
     * pad of its own (see skyPad): the bees pick their own target, and on the arena floor a neighbouring test's unit
     * (the keeper stood 3 blocks off the edge) is one more candidate.
     */
    @GameTest(template = ARENA, timeoutTicks = 220)
    public static void verdant_hive_keeper_bees_sting_a_foe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_hive", foeOwner = "gametest_hive_foe";
        var keeper = com.solegendary.reignofnether.registrars.EntityRegistrar.HIVE_KEEPER_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        if (keeper == null || foe == null) {
            helper.fail("could not create the Hive Keeper test units");
            return;
        }
        // 12 above the capitol test's pad (260), 13 below verdant_units_are_made_and_attack's (285): bee aggro is 10
        BlockPos pad = skyPad(helper, 272);
        BlockPos at = pad.offset(5, 0, 8);
        keeper.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() + 5.5, at.getY(), at.getZ() + 0.5, 0, 0);
        keeper.setOwnerName(owner);
        foe.setOwnerName(foeOwner);
        level.addFreshEntity(keeper);
        level.addFreshEntity(foe);
        List<com.solegendary.reignofnether.unit.units.neutral.BeeUnit> bees = new ArrayList<>();
        boolean[] stung = { false };
        helper.runAfterDelay(3, () -> {
            bees.addAll(keeper.releaseSwarm(level));
            keeper.discard();   // from here on only the bees can hurt the foe
        });
        for (int t = 4; t < 200; t++)
            helper.runAfterDelay(t, () -> {
                if (foe.getLastHurtByMob() instanceof com.solegendary.reignofnether.unit.units.neutral.BeeUnit b
                        && owner.equals(b.getOwnerName()))
                    stung[0] = true;
            });
        helper.runAfterDelay(200, () -> {
            // also sweep up any bee the keeper released on its own tick before the manual release
            var area = new net.minecraft.world.phys.AABB(at).inflate(24);
            List<com.solegendary.reignofnether.unit.units.neutral.BeeUnit> all = new ArrayList<>(bees);
            for (var b : level.getEntitiesOfClass(com.solegendary.reignofnether.unit.units.neutral.BeeUnit.class, area))
                if (owner.equals(b.getOwnerName()) && !all.contains(b))
                    all.add(b);
            int released = bees.size();
            boolean owned = true, summoned = true;
            for (var b : bees) {
                owned &= owner.equals(b.getOwnerName());
                summoned &= b.isSummoned();
            }
            for (var b : all)
                b.discard();
            foe.discard();
            if (released != com.solegendary.reignofnether.unit.units.verdant.HiveKeeperUnit.SWARM_SIZE)
                helper.fail("the Hive Keeper released " + released + " bees, expected "
                        + com.solegendary.reignofnether.unit.units.verdant.HiveKeeperUnit.SWARM_SIZE);
            else if (!owned || !summoned)
                helper.fail("the swarm's bees must be summoned and owned by the keeper's player");
            else if (!stung[0]) {
                StringBuilder why = new StringBuilder();
                for (var b : bees)
                    why.append(" ").append(describe(b)).append(String.format(" hp %.1f", b.getHealth())).append(" targeting ")
                        .append(describe(b.getTargetGoal() == null ? null : b.getTargetGoal().getTarget()))
                        .append(String.format(" at %.1f from the foe;", b.distanceTo(foe)));
                helper.fail("no bee from the swarm stung the enemy (foe hurt by " + describe(foe.getLastHurtByMob())
                    + ";" + why + ")");
            }
            else
                helper.succeed();
        });
    }

    // ------------------------------------------------------------------ Verdant Court T3

    /**
     * Verdant Court T3: the Heartwood Hall offers Tier 3; the Heart of the Wild is a T3 lab that the server refuses
     * from a Seedshaper alone and accepts from an Elder Druid; it grows the World Tree Walker only once its owner has
     * Tier 3; the bot kit techs to it; and the walker obeys T3 rule (a) (4-6x the Elder Treant), dies with the T3 death
     * blast and carries Rootquake, which the Elder Treant must not get.
     */
    @GameTest(template = ARENA)
    public static void verdant_t3_lab_and_tier_3(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_verdant_t3";
        var lab = Buildings.HEART_OF_THE_WILD;
        var walkerItem = com.solegendary.reignofnether.building.production.ProductionItems.WORLD_TREE_WALKER;
        if (!Buildings.HEARTWOOD_HALL.productions.get().contains(com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_3))
            helper.fail("the Heartwood Hall does not offer Tier 3");
        if (!com.solegendary.reignofnether.unit.T2Workers.isT3Lab(lab))
            helper.fail("the Heart of the Wild is not registered as a T3 lab");
        if (!lab.productions.get().contains(walkerItem))
            helper.fail("the Heart of the Wild does not grow the World Tree Walker");
        var kit = com.solegendary.reignofnether.bot.BotPlayer.kitFor(Factions.VERDANT_COURT);
        if (kit == null || kit.t3Building() != lab || kit.t3Unit() != walkerItem)
            helper.fail("the Verdant bot kit does not tech to the Heart of the Wild and the World Tree Walker");
        float ratio = (float) com.solegendary.reignofnether.resources.ResourceCosts.WORLD_TREE_WALKER.metal()
            / com.solegendary.reignofnether.resources.ResourceCosts.ELDER_TREANT.metal();
        if (ratio < 4f || ratio > 6f)
            helper.fail("T3 rule (a): the World Tree Walker should cost 4-6x the Elder Treant, is " + ratio + "x");
        if (com.solegendary.reignofnether.barfx.BarFx.tierOf(com.solegendary.reignofnether.resources.ResourceCosts.WORLD_TREE_WALKER.metal()) != 3)
            helper.fail("the World Tree Walker would not die with the T3 death blast");

        BlockPos base = helper.absolutePos(new BlockPos(10, 2, 10));
        var seed = com.solegendary.reignofnether.registrars.EntityRegistrar.SEEDSHAPER_UNIT.get().create(level);
        var druid = com.solegendary.reignofnether.registrars.EntityRegistrar.ELDER_DRUID_UNIT.get().create(level);
        var walker = com.solegendary.reignofnether.registrars.EntityRegistrar.WORLD_TREE_WALKER_UNIT.get().create(level);
        var treant = com.solegendary.reignofnether.registrars.EntityRegistrar.ELDER_TREANT_UNIT.get().create(level);
        if (seed == null || druid == null || walker == null || treant == null) {
            helper.fail("could not create the units");
            return;
        }
        seed.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);
        druid.moveTo(base.getX() + 2.5, base.getY(), base.getZ() + 0.5, 0, 0);
        level.addFreshEntity(seed);
        level.addFreshEntity(druid);
        com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(owner);
        try {
            if (!Factions.VERDANT_COURT.equals(Factions.getFaction(walker)))
                helper.fail("the World Tree Walker is not Verdant Court");
            if (walker.getAbilities().get().stream().noneMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.Rootquake))
                helper.fail("the World Tree Walker has no Rootquake");
            if (treant.getAbilities().get().stream().anyMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.Rootquake))
                helper.fail("Rootquake leaked onto the Elder Treant");
            if (walker.getMaxHealth() < 1000)
                helper.fail("World Tree Walker health " + walker.getMaxHealth());
            if (com.solegendary.reignofnether.unit.T2Workers.buildersMayPlace(level, lab, new int[] { seed.getId() }))
                helper.fail("the Heart of the Wild may be placed by a Seedshaper alone");
            if (!com.solegendary.reignofnether.unit.T2Workers.buildersMayPlace(level, lab, new int[] { seed.getId(), druid.getId() }))
                helper.fail("the Heart of the Wild refused an Elder Druid among the builders");
            // the real placement path: a Seedshaper-only Heart of the Wild is refused before the terrain checks
            var placed = com.solegendary.reignofnether.building.BuildingServerEvents.placeBuilding(lab, base.offset(-80, 0, 80),
                Rotation.NONE, owner, new int[] { seed.getId() }, false, false, false, true);
            if (placed != null) {
                com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(placed);
                helper.fail("placeBuilding accepted a Heart of the Wild from a Seedshaper");
            }
            // the produce gate on a placement that is never put in the world (invisible to parallel tests)
            var bp = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(lab, level,
                helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, owner, false);
            if (!(bp instanceof com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement pp)) {
                helper.fail("the Heart of the Wild is not a production placement: " + bp);
                return;
            }
            com.solegendary.reignofnether.research.ResearchServerEvents.addResearch(owner,
                com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_2);
            if (walkerItem.canProduce(pp))
                helper.fail("the World Tree Walker can be grown with only Tier 2");
            if (pp.startProductionItem(walkerItem) || !pp.productionQueue.isEmpty())
                helper.fail("the server queued a World Tree Walker without Tier 3");
            com.solegendary.reignofnether.research.ResearchServerEvents.addResearch(owner,
                com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_3);
            if (!walkerItem.canProduce(pp))
                helper.fail("the World Tree Walker still refused after Tier 3: " + walkerItem.getProduceErrorMsg(pp));
            helper.succeed();
        } finally {
            com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(owner);
            seed.discard();
            druid.discard();
            walker.discard();
            treant.discard();
        }
    }

    /**
     * The World Tree Walker's shade heals a hurt friendly Verdant unit within 6 blocks by 2 HP, never a hurt enemy
     * just as close nor a friend out of the shade; and its arrival is announced (the once-only flag is set).
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void world_tree_walker_heals_friend_in_shade_not_foe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_wtw_shade", foeOwner = "gametest_wtw_shade_foe";
        var walker = com.solegendary.reignofnether.registrars.EntityRegistrar.WORLD_TREE_WALKER_UNIT.get().create(level);
        var lb = com.solegendary.reignofnether.registrars.EntityRegistrar.LEAFBLADE_UNIT.get();
        var friend = lb.create(level);
        var far = lb.create(level);
        var foe = lb.create(level);
        if (walker == null || friend == null || far == null || foe == null) {
            helper.fail("could not create the units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(8, 2, 8));
        walker.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        friend.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() - 2.5, at.getY(), at.getZ() + 0.5, 0, 0);
        far.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 10.5, 0, 0);
        walker.setOwnerName(owner);
        friend.setOwnerName(owner);
        far.setOwnerName(owner);
        foe.setOwnerName(foeOwner);
        List<net.minecraft.world.entity.Mob> all = List.of(walker, friend, far, foe);
        for (var e : all)
            level.addFreshEntity(e);
        helper.runAfterDelay(10, () -> {
            try {
                // set and measure within one tick, so the walker's own once-a-second shade can't muddle the numbers
                friend.setHealth(friend.getMaxHealth() * 0.4f);
                far.setHealth(far.getMaxHealth() * 0.4f);
                foe.setHealth(foe.getMaxHealth() * 0.4f);
                float friendHp = friend.getHealth(), farHp = far.getHealth(), foeHp = foe.getHealth();
                int healed = walker.shade(level);
                if (healed != 1)
                    helper.fail("the shade should heal exactly the one friend in it, healed " + healed);
                if (Math.abs(friend.getHealth() - friendHp - com.solegendary.reignofnether.unit.units.verdant.WorldTreeWalkerUnit.SHADE_HEAL) > 0.01f)
                    helper.fail("the friend in the shade healed " + (friend.getHealth() - friendHp));
                if (far.getHealth() > farHp)
                    helper.fail("a friend 10 blocks away was healed by the shade");
                if (foe.getHealth() > foeHp)
                    helper.fail("the World Tree Walker's shade healed an enemy");
                if (!walker.getPersistentData().getBoolean("bt_t3_announced"))
                    helper.fail("the World Tree Walker's arrival was not announced");
                helper.succeed();
            } finally {
                for (var e : all)
                    e.discard();
            }
        });
    }

    /** The World Tree Walker's weakness: fire hurts it twice as much as the same plain damage. */
    @GameTest(template = ARENA)
    public static void world_tree_walker_takes_double_fire_damage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var walker = com.solegendary.reignofnether.registrars.EntityRegistrar.WORLD_TREE_WALKER_UNIT.get().create(level);
        if (walker == null) {
            helper.fail("could not create the World Tree Walker");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(14, 2, 14));
        walker.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        walker.setOwnerName("gametest_wtw_fire");
        level.addFreshEntity(walker);
        try {
            float hp0 = walker.getHealth();
            walker.invulnerableTime = 0;
            walker.hurt(level.damageSources().generic(), 10f);
            float plain = hp0 - walker.getHealth();
            float hp1 = walker.getHealth();
            walker.invulnerableTime = 0;
            walker.hurt(level.damageSources().inFire(), 10f);
            float fire = hp1 - walker.getHealth();
            if (plain <= 0)
                helper.fail("plain damage did not land: " + plain);
            else if (Math.abs(fire - 2 * plain) > 0.01f)
                helper.fail("fire dealt " + fire + ", plain damage " + plain + " - fire should deal double");
            else
                helper.succeed();
        } finally {
            walker.discard();
        }
    }

    /**
     * A T3's melee splash (World Tree Walker slam, Sun Colossus cleave) through UnitDamage.meleeSplash: lands on a foe
     * standing by the target for {@code share} of what the same unit's full hit does to an identical body (RoN's
     * actuallyHurt override used to rewrite it to the full hit), and never on a friend by the target. Every body is
     * no-AI and discarded in the same tick; the reference body stands well outside the splash radius.
     */
    static void t3SplashCheck(GameTestHelper helper, net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> type,
                              String tag, float share) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_splash_" + tag, foeOwner = "gametest_splash_" + tag + "_foe";
        var big = type.create(level);
        var vind = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get();
        var target = vind.create(level);
        var neighbour = vind.create(level);
        var friend = vind.create(level);
        var reference = vind.create(level);
        if (big == null || target == null || neighbour == null || friend == null || reference == null) {
            helper.fail("could not create the units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
        big.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, -90, 0);
        target.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5, 90, 0);
        neighbour.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 2.0, 90, 0);     // 1.5 from the target
        friend.moveTo(at.getX() + 5.0, at.getY(), at.getZ() + 0.5, 90, 0);        // 1.5 from the target, a friend
        reference.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 12.5, 90, 0);   // 12 from the target
        ((com.solegendary.reignofnether.unit.interfaces.Unit) big).setOwnerName(owner);
        ((com.solegendary.reignofnether.unit.interfaces.Unit) friend).setOwnerName(owner);
        ((com.solegendary.reignofnether.unit.interfaces.Unit) target).setOwnerName(foeOwner);
        ((com.solegendary.reignofnether.unit.interfaces.Unit) neighbour).setOwnerName(foeOwner);
        ((com.solegendary.reignofnether.unit.interfaces.Unit) reference).setOwnerName(foeOwner);
        List<net.minecraft.world.entity.Mob> all = List.of(big, target, neighbour, friend, reference);
        for (var e : all) {
            e.setNoAi(true);
            level.addFreshEntity(e);
        }
        helper.runAfterDelay(2, () -> {
            try {
                for (var e : all)
                    e.invulnerableTime = 0;
                float refHp = reference.getHealth();
                reference.hurt(level.damageSources().mobAttack(big), 1f);   // RoN makes this the unit's full hit
                float full = refHp - reference.getHealth();
                float nHp = neighbour.getHealth(), fHp = friend.getHealth(), tHp = target.getHealth();
                boolean hit = big.doHurtTarget(target);
                float splash = nHp - neighbour.getHealth();
                if (full <= 0)
                    helper.fail(tag + ": its full hit did no damage to the reference body");
                else if (!hit || target.getHealth() >= tHp)
                    helper.fail(tag + ": the blow on the target did not land");
                else if (Math.abs(splash - full * share) > 0.05f)
                    helper.fail(tag + ": the splash dealt " + splash + " to the neighbour, expected " + share + " x " + full
                        + " = " + full * share);
                else if (friend.getHealth() < fHp)
                    helper.fail(tag + ": the splash hurt a friendly unit beside the target");
                else
                    helper.succeed();
            } finally {
                for (var e : all)
                    e.discard();
            }
        });
    }

    /** The World Tree Walker's slam splashes half its blow on a foe beside the target, not the whole blow. */
    @GameTest(template = ARENA)
    public static void world_tree_walker_slam_splash_is_half_a_blow(GameTestHelper helper) {
        t3SplashCheck(helper, com.solegendary.reignofnether.registrars.EntityRegistrar.WORLD_TREE_WALKER_UNIT.get(), "wtw",
            com.solegendary.reignofnether.unit.units.verdant.WorldTreeWalkerUnit.SLAM_SPLASH);
    }

    /** The Sun Colossus' cleave hits a second foe by the target for half the blow, and never a friend. */
    @GameTest(template = ARENA)
    public static void sun_colossus_cleave_hits_second_foe_not_friend(GameTestHelper helper) {
        t3SplashCheck(helper, com.solegendary.reignofnether.registrars.EntityRegistrar.SUN_COLOSSUS_UNIT.get(), "colossus",
            com.solegendary.reignofnether.unit.units.villagers.SunColossusUnit.CLEAVE_SPLASH);
    }

    /**
     * Rootquake erupts on the enemies within 8 blocks - 20 damage and rooted (Slowness VII) - and leaves friends in the
     * circle and enemies outside it alone.
     */
    @GameTest(template = ARENA)
    public static void rootquake_hits_foes_not_friends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_rootquake", foeOwner = "gametest_rootquake_foe";
        var walker = com.solegendary.reignofnether.registrars.EntityRegistrar.WORLD_TREE_WALKER_UNIT.get().create(level);
        var vind = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get();
        var friend = vind.create(level);
        var foe = vind.create(level);
        var farFoe = vind.create(level);
        if (walker == null || friend == null || foe == null || farFoe == null) {
            helper.fail("could not create the units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
        walker.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        friend.moveTo(at.getX() + 4.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 5.5, 0, 0);
        farFoe.moveTo(at.getX() + 12.5, at.getY(), at.getZ() + 12.5, 0, 0);
        walker.setOwnerName(owner);
        friend.setOwnerName(owner);
        foe.setOwnerName(foeOwner);
        farFoe.setOwnerName(foeOwner);
        List<net.minecraft.world.entity.Mob> all = List.of(walker, friend, foe, farFoe);
        for (var e : all)
            level.addFreshEntity(e);
        helper.runAfterDelay(2, () -> {
            try {
                float friendHp = friend.getHealth(), foeHp = foe.getHealth(), farHp = farFoe.getHealth();
                var hit = com.solegendary.reignofnether.ability.abilities.Rootquake.quake(level, walker, owner, walker.position());
                var slow = net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN;
                if (!hit.contains(foe) || (foe.isAlive() && foe.getHealth() >= foeHp))
                    helper.fail("Rootquake did not hurt the enemy in its circle");
                if (foe.getEffect(slow) == null || foe.getEffect(slow).getAmplifier()
                        < com.solegendary.reignofnether.ability.abilities.Rootquake.ROOT_AMPLIFIER)
                    helper.fail("Rootquake did not root the enemy in its circle");
                if (hit.contains(friend) || friend.getHealth() < friendHp || friend.getEffect(slow) != null)
                    helper.fail("Rootquake touched a friendly unit");
                if (hit.contains(farFoe) || farFoe.getHealth() < farHp)
                    helper.fail("Rootquake reached an enemy 17 blocks away");
                helper.succeed();
            } finally {
                for (var e : all)
                    e.discard();
            }
        });
    }

    /**
     * The death-FX faction field (3 bits since the Verdant Court) carries every live faction's debris tint and every
     * cost tier through the packet codec intact, and each live faction has its own tint.
     */
    @GameTest(template = ARENA)
    public static void death_fx_packet_carries_all_four_factions(GameTestHelper helper) {
        byte[] tints = {
            com.solegendary.reignofnether.barfx.BarFx.F_SUNFORGED, com.solegendary.reignofnether.barfx.BarFx.F_GRAVEBOUND,
            com.solegendary.reignofnether.barfx.BarFx.F_HORDE, com.solegendary.reignofnether.barfx.BarFx.F_VERDANT,
            com.solegendary.reignofnether.barfx.BarFx.F_TIDE };
        var live = List.of(Factions.VILLAGERS, Factions.MONSTERS, Factions.PIGLINS, Factions.VERDANT_COURT,
            Factions.TIDEWROUGHT);
        for (int i = 0; i < live.size(); i++)
            if (FactionTraits.of(live.get(i)).deathDebris != tints[i]) {
                helper.fail(live.get(i).getName() + " has debris tint " + FactionTraits.of(live.get(i)).deathDebris
                        + ", expected " + tints[i]);
                return;
            }
        List<com.solegendary.reignofnether.barfx.BarFx.Event> sent = new ArrayList<>();
        for (byte f : tints)
            for (int tier = 0; tier <= 3; tier++)
                sent.add(new com.solegendary.reignofnether.barfx.BarFx.Event(com.solegendary.reignofnether.barfx.BarFx.DEATH,
                        com.solegendary.reignofnether.barfx.BarFx.D_FLESH,
                        com.solegendary.reignofnether.barfx.BarFx.deathFlags(f, tier), 1, 2, 3, 0.6f, 1.8f, 0));
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            new com.solegendary.reignofnether.barfx.BarFxClientboundPacket(sent).encode(buf);
            var got = new com.solegendary.reignofnether.barfx.BarFxClientboundPacket(buf).events();
            if (got.size() != sent.size()) {
                helper.fail("decoded " + got.size() + " events, sent " + sent.size());
                return;
            }
            int k = 0;
            for (byte f : tints)
                for (int tier = 0; tier <= 3; tier++, k++) {
                    var e = got.get(k);
                    int df = com.solegendary.reignofnether.barfx.BarFx.deathFaction(e.flags);
                    int dt = com.solegendary.reignofnether.barfx.BarFx.deathTier(e.flags);
                    if (df != f || dt != tier) {
                        helper.fail("faction " + f + " tier " + tier + " came back as faction " + df + " tier " + dt);
                        return;
                    }
                }
        } finally {
            buf.release();
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ Verdant Court T2 extras (Wisp Choir, Bloom Priestess)

    /**
     * A Wisp Choir bolt hurts a flying unit (an Owl Watcher) far more than a unit on the ground (a villager): the
     * flyer multiplier is applied through RoN's damage pipeline, and both are real hits.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void verdant_wisp_choir_hits_flyers_harder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_wisps", foeOwner = "gametest_wisps_foe";
        var choir = com.solegendary.reignofnether.registrars.EntityRegistrar.WISP_CHOIR_UNIT.get().create(level);
        var owl = com.solegendary.reignofnether.registrars.EntityRegistrar.OWL_WATCHER_UNIT.get().create(level);
        var walker = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        if (choir == null || owl == null || walker == null) {
            helper.fail("could not create the Wisp Choir test units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(8, 2, 8));
        choir.moveTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 0, 0);
        owl.moveTo(at.getX() + 4.5, at.getY() + 2, at.getZ() + 0.5, 0, 0);
        walker.moveTo(at.getX() - 3.5, at.getY(), at.getZ() + 0.5, 0, 0);
        choir.setOwnerName(owner);
        owl.setOwnerName(foeOwner);
        walker.setOwnerName(foeOwner);
        List<net.minecraft.world.entity.Mob> all = List.of(choir, owl, walker);
        for (var e : all)
            level.addFreshEntity(e);
        helper.runAfterDelay(5, () -> {
            try {
                if (!com.solegendary.reignofnether.unit.units.verdant.WispChoirUnit.isFlyer(owl)
                        || com.solegendary.reignofnether.unit.units.verdant.WispChoirUnit.isFlyer(walker)) {
                    helper.fail("flyer check: owl " + com.solegendary.reignofnether.unit.units.verdant.WispChoirUnit.isFlyer(owl)
                            + ", villager " + com.solegendary.reignofnether.unit.units.verdant.WispChoirUnit.isFlyer(walker));
                    return;
                }
                owl.setHealth(owl.getMaxHealth());
                walker.setHealth(walker.getMaxHealth());
                float owlHp = owl.getHealth(), walkerHp = walker.getHealth();
                choir.bolt(owl);
                choir.bolt(walker);
                float toFlyer = owlHp - owl.getHealth(), toGround = walkerHp - walker.getHealth();
                if (toGround <= 0)
                    helper.fail("the bolt did no damage to the ground unit");
                else if (toFlyer < toGround * 4)
                    helper.fail("the bolt should hurt a flyer much more than a walker: " + toFlyer + " vs " + toGround);
                else if (choir.getUnitAttackDamage() != com.solegendary.reignofnether.unit.units.verdant.WispChoirUnit.attackDamage)
                    helper.fail("the bolt multiplier leaked out of the hit: base damage now " + choir.getUnitAttackDamage());
                else
                    helper.succeed();
            } finally {
                for (var e : all)
                    e.discard();
            }
        });
    }

    /**
     * The Bloom Priestess' pulse heals a hurt friendly unit and strips its Slowness (a Vine Snare root), and leaves a
     * hurt, slowed enemy just as close untouched.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void verdant_bloom_priestess_heals_and_cleanses_friend_not_foe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_priestess", foeOwner = "gametest_priestess_foe";
        var priestess = com.solegendary.reignofnether.registrars.EntityRegistrar.BLOOM_PRIESTESS_UNIT.get().create(level);
        var vill = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get();
        var friend = vill.create(level);
        var foe = vill.create(level);
        if (priestess == null || friend == null || foe == null) {
            helper.fail("could not create the Bloom Priestess test units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(8, 2, 8));
        priestess.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        friend.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() - 2.5, at.getY(), at.getZ() + 0.5, 0, 0);
        priestess.setOwnerName(owner);
        friend.setOwnerName(owner);
        foe.setOwnerName(foeOwner);
        List<net.minecraft.world.entity.Mob> all = List.of(priestess, friend, foe);
        for (var e : all)
            level.addFreshEntity(e);
        helper.runAfterDelay(5, () -> {
            try {
                friend.setHealth(friend.getMaxHealth() * 0.4f);
                foe.setHealth(foe.getMaxHealth() * 0.4f);
                var slow = net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN;
                friend.addEffect(new net.minecraft.world.effect.MobEffectInstance(slow, 200, 6));
                foe.addEffect(new net.minecraft.world.effect.MobEffectInstance(slow, 200, 6));
                float friendHp = friend.getHealth(), foeHp = foe.getHealth();
                int touched = priestess.pulse(level);
                float expect = com.solegendary.reignofnether.unit.units.verdant.BloomPriestessUnit.PULSE_HEAL;
                if (touched != 1)
                    helper.fail("the pulse should touch exactly the one friend, touched " + touched);
                else if (Math.abs(friend.getHealth() - friendHp - expect) > 0.01f)
                    helper.fail("friend healed " + (friend.getHealth() - friendHp) + ", expected " + expect);
                else if (friend.hasEffect(slow))
                    helper.fail("the pulse did not cleanse the friend's Slowness");
                else if (foe.getHealth() > foeHp)
                    helper.fail("the Bloom Priestess healed an enemy");
                else if (!foe.hasEffect(slow))
                    helper.fail("the Bloom Priestess cleansed an enemy");
                else
                    helper.succeed();
            } finally {
                for (var e : all)
                    e.discard();
            }
        });
    }

    /** Bloom, cast through the priestess' own ability, heals a hurt friend in reach for its full burst and goes on cooldown. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void verdant_bloom_burst_heals(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_bloom";
        var priestess = com.solegendary.reignofnether.registrars.EntityRegistrar.BLOOM_PRIESTESS_UNIT.get().create(level);
        var friend = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        if (priestess == null || friend == null) {
            helper.fail("could not create the Bloom test units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(8, 2, 8));
        priestess.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        friend.moveTo(at.getX() + 6.5, at.getY(), at.getZ() + 0.5, 0, 0);   // 6 blocks: in reach of Bloom (8)
        priestess.setOwnerName(owner);
        friend.setOwnerName(owner);
        List<net.minecraft.world.entity.Mob> all = List.of(priestess, friend);
        for (var e : all)
            level.addFreshEntity(e);
        helper.runAfterDelay(5, () -> {
            try {
                com.solegendary.reignofnether.ability.Ability bloom = null;
                for (var a : priestess.getAbilities().get())
                    if (a instanceof com.solegendary.reignofnether.ability.abilities.Bloom)
                        bloom = a;
                if (bloom == null) {
                    helper.fail("the Bloom Priestess has no Bloom ability");
                    return;
                }
                if (!bloom.isOffCooldown(priestess)) {
                    helper.fail("Bloom starts on cooldown");
                    return;
                }
                friend.setHealth(friend.getMaxHealth() * 0.3f);
                float before = friend.getHealth();
                float expect = Math.min(friend.getMaxHealth() - before, com.solegendary.reignofnether.ability.abilities.Bloom.HEAL);
                bloom.use(level, priestess, priestess.blockPosition());
                float healed = friend.getHealth() - before;
                if (Math.abs(healed - expect) > 0.01f)
                    helper.fail("Bloom healed " + healed + ", expected " + expect);
                else if (bloom.isOffCooldown(priestess))
                    helper.fail("Bloom did not go on cooldown");
                else
                    helper.succeed();
            } finally {
                for (var e : all)
                    e.discard();
            }
        });
    }

    // ------------------------------------------------------------------ Verdant Living Terrain (slice 3)

    /** Clears a box of the arena to open ground: stone at y=1 (template coords), air above up to y=4. */
    static void openGround(GameTestHelper helper, int x0, int z0, int x1, int z1) {
        ServerLevel level = helper.getLevel();
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                level.setBlock(helper.absolutePos(new BlockPos(x, 1, z)), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                for (int y = 2; y <= 4; y++)
                    level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
    }

    /**
     * A unit standing in its own side's grown thicket is hidden: an enemy 8 blocks away can't pick it as a target.
     * Once an enemy unit comes within 4 blocks it is revealed and targetable again. The targeting enemy is never
     * added to the level (so it can't act); a second enemy, added, walks up close.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void verdant_thicket_hides_until_an_enemy_is_close(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_thicket_hide", foeOwner = "gametest_thicket_hide_foe";
        openGround(helper, 2, 2, 13, 6);
        BlockPos spot = helper.absolutePos(new BlockPos(3, 2, 4));
        if (!com.solegendary.reignofnether.blocks.ThicketBlock.place(level, spot, owner, true)) {
            helper.fail("could not place a thicket at " + spot);
            return;
        }
        var vill = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get();
        var friend = vill.create(level);
        var scout = vill.create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (friend == null || scout == null || foe == null) {
            helper.fail("could not create the thicket test units");
            com.solegendary.reignofnether.blocks.ThicketBlockEntity.removeAllOwned(level, owner);
            return;
        }
        friend.setOwnerName(owner);
        scout.setOwnerName(foeOwner);
        foe.setOwnerName(foeOwner);
        friend.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
        foe.moveTo(spot.getX() + 8.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
        level.addFreshEntity(friend);
        List<net.minecraft.world.entity.Mob> all = List.of(friend, scout, foe);
        Runnable cleanup = () -> {
            for (var e : all)
                e.discard();
            com.solegendary.reignofnether.blocks.ThicketBlockEntity.removeAllOwned(level, owner);
        };
        boolean[] seenVisible = { false };
        helper.runAfterDelay(3, () -> {
            // control, before any cover pass could hide it: the 8-block enemy can target the friend at all
            seenVisible[0] = !com.solegendary.reignofnether.blocks.ThicketCover.isHidden(friend)
                    && com.solegendary.reignofnether.util.MiscUtil.findClosestAttackableEntity(foe, 10, level) == friend;
        });
        helper.runAfterDelay(10, () -> {
            com.solegendary.reignofnether.blocks.ThicketCover.refresh(level.getServer());
            if (!com.solegendary.reignofnether.blocks.ThicketCover.isHidden(friend) || !friend.isInvisible()) {
                helper.fail("a unit in its own grown thicket with no enemy near is not hidden");
                cleanup.run();
                return;
            }
            if (com.solegendary.reignofnether.util.MiscUtil.findClosestAttackableEntity(foe, 10, level) == friend) {
                helper.fail("an enemy 8 blocks away can still target the unit hidden in the thicket");
                cleanup.run();
                return;
            }
            scout.moveTo(spot.getX() + 3.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
            level.addFreshEntity(scout);
        });
        helper.runAfterDelay(15, () -> {
            try {
                com.solegendary.reignofnether.blocks.ThicketCover.refresh(level.getServer());
                if (com.solegendary.reignofnether.blocks.ThicketCover.isHidden(friend) || friend.isInvisible())
                    helper.fail("the unit stayed hidden with an enemy 3 blocks away");
                else if (seenVisible[0] && com.solegendary.reignofnether.util.MiscUtil.findClosestAttackableEntity(foe, 10, level) != friend)
                    helper.fail("the revealed unit still can't be targeted");
                else
                    helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    // ------------------------------------------------------------------ cover vs. orders (Concealment)

    /**
     * A direct ATTACK order on a cloaked enemy is refused by the server (a modified client can still send the id):
     * the attacker gets no target and walks to where the ranger stands instead. Control first: the same order on the
     * visible ranger does make it the target. Everything runs inside one callback so the ranger's own tick can't
     * change its cloak in between.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void cloaked_enemy_direct_attack_order_is_refused(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_cloak_order", foeOwner = "gametest_cloak_order_foe";
        var ranger = com.solegendary.reignofnether.registrars.EntityRegistrar.SHADE_RANGER_UNIT.get().create(level);
        var attacker = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (ranger == null || attacker == null) {
            helper.fail("could not create the cloak order units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
        ranger.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        attacker.moveTo(at.getX() + 10.5, at.getY(), at.getZ() + 0.5, 0, 0);
        ranger.setOwnerName(foeOwner);
        attacker.setOwnerName(owner);
        level.addFreshEntity(ranger);
        level.addFreshEntity(attacker);
        helper.runAfterDelay(3, () -> {
            try {
                int[] ids = { attacker.getId() };
                ranger.setInvisible(false);
                new com.solegendary.reignofnether.unit.UnitActionItem(owner,
                        com.solegendary.reignofnether.unit.UnitAction.ATTACK, ranger.getId(), ids).action(level);
                if (attacker.getTargetGoal().getTarget() != ranger) {
                    helper.fail("control: an attack order on the visible ranger did not target it");
                    return;
                }
                ranger.setInvisible(true);   // cloaked (ShadeRangerUnit.isCloaked: alive + invisible)
                if (!com.solegendary.reignofnether.unit.Concealment.isHiddenFrom(ranger, owner)
                        || com.solegendary.reignofnether.unit.Concealment.isHiddenFrom(ranger, foeOwner)) {
                    helper.fail("Concealment: a cloaked ranger must be hidden from its enemy and only its enemy");
                    return;
                }
                new com.solegendary.reignofnether.unit.UnitActionItem(owner,
                        com.solegendary.reignofnether.unit.UnitAction.ATTACK, ranger.getId(), ids).action(level);
                if (attacker.getTargetGoal().getTarget() != null)
                    helper.fail("the server let a direct attack order lock onto a cloaked enemy");
                else if (attacker.getMoveGoal().getMoveTarget() == null)
                    helper.fail("the refused attack order did not become a move to the cloaked enemy's position");
                else
                    helper.succeed();
            } finally {
                ranger.discard();
                attacker.discard();
            }
        });
    }

    /**
     * Holy Bell breaks thicket cover: a unit hidden in its own thicket is out in the open after the bell (and a cover
     * pass doesn't re-hide it while the reveal lasts), then hides again once the reveal is over. The ringer is never
     * added to the level, so it can't reveal the bush just by standing near it.
     */
    @GameTest(template = ARENA, timeoutTicks = 100 + com.solegendary.reignofnether.ability.abilities.HolyBell.REVEAL_TICKS)
    public static void holy_bell_breaks_thicket_cover(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_bell_thicket", foeOwner = "gametest_bell_thicket_foe";
        openGround(helper, 2, 10, 6, 14);
        BlockPos spot = helper.absolutePos(new BlockPos(4, 2, 12));
        if (!com.solegendary.reignofnether.blocks.ThicketBlock.place(level, spot, owner, true)) {
            helper.fail("could not place a thicket at " + spot);
            return;
        }
        var friend = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        var architect = com.solegendary.reignofnether.registrars.EntityRegistrar.ROYAL_ARCHITECT_UNIT.get().create(level);
        if (friend == null || architect == null) {
            helper.fail("could not create the bell/thicket units");
            com.solegendary.reignofnether.blocks.ThicketBlockEntity.removeAllOwned(level, owner);
            return;
        }
        friend.setOwnerName(owner);
        architect.setOwnerName(foeOwner);
        friend.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
        architect.moveTo(spot.getX() + 4.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
        level.addFreshEntity(friend);
        boolean[] done = { false };
        Runnable cleanup = () -> {
            done[0] = true;
            friend.discard();
            architect.discard();
            com.solegendary.reignofnether.blocks.ThicketBlockEntity.removeAllOwned(level, owner);
        };
        helper.runAfterDelay(10, () -> {
            com.solegendary.reignofnether.blocks.ThicketCover.refresh(level.getServer());
            if (!com.solegendary.reignofnether.blocks.ThicketCover.isHidden(friend) || !friend.isInvisible()) {
                helper.fail("precondition: the unit in its own thicket is not hidden");
                cleanup.run();
                return;
            }
            // arena-sized (6 blocks round a ringer 4 off): a full 24-block pulse strips the neighbouring tests' cover
            var rung = com.solegendary.reignofnether.ability.abilities.HolyBell.ring(level, architect, foeOwner, 6);
            com.solegendary.reignofnether.blocks.ThicketCover.refresh(level.getServer());
            if (!rung.contains(friend)) {
                helper.fail("Holy Bell skipped a unit hidden in a thicket");
                cleanup.run();
            } else if (com.solegendary.reignofnether.blocks.ThicketCover.isHidden(friend) || friend.isInvisible()
                    || com.solegendary.reignofnether.unit.Concealment.isHiddenFrom(friend, foeOwner)) {
                helper.fail("the thicket kept the unit hidden through the Holy Bell reveal");
                cleanup.run();
            }
        });
        helper.runAfterDelay(10 + com.solegendary.reignofnether.ability.abilities.HolyBell.REVEAL_TICKS / 2, () -> {
            if (done[0])
                return;
            com.solegendary.reignofnether.blocks.ThicketCover.refresh(level.getServer());
            if (com.solegendary.reignofnether.blocks.ThicketCover.isHidden(friend) || friend.isInvisible()) {
                helper.fail("the unit re-hid in the thicket halfway through the Holy Bell reveal");
                cleanup.run();
            }
        });
        helper.runAfterDelay(20 + com.solegendary.reignofnether.ability.abilities.HolyBell.REVEAL_TICKS, () -> {
            if (done[0])
                return;
            try {
                com.solegendary.reignofnether.blocks.ThicketCover.refresh(level.getServer());
                if (!com.solegendary.reignofnether.blocks.ThicketCover.isHidden(friend))
                    helper.fail("the unit never hid again after the Holy Bell reveal ran out");
                else
                    helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    /** Hidden is not invulnerable: the Elder Treant's boulder (splash) still hurts a unit hidden in a thicket. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void splash_damage_hits_thicket_hidden_unit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_splash_hidden", foeOwner = "gametest_splash_hidden_foe";
        openGround(helper, 8, 8, 12, 12);
        BlockPos spot = helper.absolutePos(new BlockPos(10, 2, 10));
        if (!com.solegendary.reignofnether.blocks.ThicketBlock.place(level, spot, owner, true)) {
            helper.fail("could not place a thicket at " + spot);
            return;
        }
        var hidden = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        var treant = com.solegendary.reignofnether.registrars.EntityRegistrar.ELDER_TREANT_UNIT.get().create(level);
        if (hidden == null || treant == null) {
            helper.fail("could not create the splash test units");
            com.solegendary.reignofnether.blocks.ThicketBlockEntity.removeAllOwned(level, owner);
            return;
        }
        hidden.setOwnerName(owner);
        treant.setOwnerName(foeOwner);
        hidden.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
        // never added to the level: it's only the boulder's thrower, and can't wander up and see into the bush
        treant.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() - 12.5, 0, 0);
        level.addFreshEntity(hidden);
        helper.runAfterDelay(10, () -> {
            try {
                com.solegendary.reignofnether.blocks.ThicketCover.refresh(level.getServer());
                if (!com.solegendary.reignofnether.blocks.ThicketCover.isHidden(hidden)) {
                    helper.fail("precondition: the unit in its own thicket is not hidden");
                    return;
                }
                float hp = hidden.getHealth();
                var hit = com.solegendary.reignofnether.ability.abilities.BoulderToss.impact(level, treant, foeOwner,
                        new net.minecraft.world.phys.Vec3(hidden.getX(), hidden.getY(), hidden.getZ()));
                if (!hit.contains(hidden) || (hidden.isAlive() && hidden.getHealth() >= hp))
                    helper.fail("splash damage skipped a unit hidden in a thicket - hidden must not mean invulnerable");
                else
                    helper.succeed();
            } finally {
                hidden.discard();
                treant.discard();
                com.solegendary.reignofnether.blocks.ThicketBlockEntity.removeAllOwned(level, owner);
            }
        });
    }

    /** Fire set beside a thicket burns it away within a second or two (and it does not come back). */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void verdant_thicket_burns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_thicket_fire";
        openGround(helper, 5, 5, 10, 10);
        BlockPos spot = helper.absolutePos(new BlockPos(7, 2, 7));
        if (!com.solegendary.reignofnether.blocks.ThicketBlock.place(level, spot, owner, true)) {
            helper.fail("could not place a thicket at " + spot);
            return;
        }
        var thicket = com.solegendary.reignofnether.registrars.BlockRegistrar.THICKET.get();
        level.setBlock(spot.east(), net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState(), 3);
        helper.runAfterDelay(40, () -> {
            boolean gone = !level.getBlockState(spot).is(thicket);
            for (BlockPos p : new BlockPos[] { spot, spot.east() })
                level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            com.solegendary.reignofnether.blocks.ThicketBlockEntity.removeAllOwned(level, owner);
            if (!gone)
                helper.fail("the thicket did not burn with fire right beside it");
            else
                helper.succeed();
        });
    }

    /** Seedshaper planting stops at the per-player cap: six 3x3 patches leave exactly MAX_PLANTED thickets. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void verdant_thicket_cap_enforced(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_thicket_cap";
        openGround(helper, 1, 1, 14, 8);
        int[][] centres = { { 2, 2 }, { 6, 2 }, { 10, 2 }, { 2, 6 }, { 6, 6 }, { 10, 6 } };
        int planted = 0;
        try {
            for (int[] c : centres)
                planted += com.solegendary.reignofnether.ability.abilities.PlantThicket.plantPatch(level, owner,
                        helper.absolutePos(new BlockPos(c[0], 2, c[1])), false);
            int owned = com.solegendary.reignofnether.blocks.ThicketBlockEntity.countOwned(level, owner);
            int max = com.solegendary.reignofnether.blocks.ThicketBlockEntity.MAX_PLANTED;
            if (owned != max || planted != max)
                helper.fail("planted " + planted + " / owned " + owned + " thickets over 6 patches, expected the cap " + max);
            else
                helper.succeed();
        } finally {
            com.solegendary.reignofnether.blocks.ThicketBlockEntity.removeAllOwned(level, owner);
        }
    }

    /** The Grove Warden's Overgrowth grows thickets around the spot and roots the enemy inside, not the friend. */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void verdant_overgrowth_roots_foes_not_friends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_overgrowth", foeOwner = "gametest_overgrowth_foe";
        openGround(helper, 5, 5, 11, 11);
        BlockPos centre = helper.absolutePos(new BlockPos(8, 1, 8));   // the clicked ground block
        var vill = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get();
        var friend = vill.create(level);
        var foe = vill.create(level);
        if (friend == null || foe == null) {
            helper.fail("could not create the Overgrowth test units");
            return;
        }
        friend.setOwnerName(owner);
        foe.setOwnerName(foeOwner);
        friend.moveTo(centre.getX() - 1.5, centre.getY() + 1, centre.getZ() + 0.5, 0, 0);
        foe.moveTo(centre.getX() + 2.5, centre.getY() + 1, centre.getZ() + 0.5, 0, 0);
        level.addFreshEntity(friend);
        level.addFreshEntity(foe);
        helper.runAfterDelay(5, () -> {
            try {
                int rooted = com.solegendary.reignofnether.ability.abilities.Overgrowth.cast(level, owner, centre);
                var root = foe.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
                int grown = com.solegendary.reignofnether.blocks.ThicketBlockEntity.countOwned(level, owner);
                if (root == null || root.getAmplifier() < com.solegendary.reignofnether.ability.abilities.Overgrowth.ROOT_AMPLIFIER)
                    helper.fail("Overgrowth did not root the enemy (rooted " + rooted + ")");
                else if (friend.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN))
                    helper.fail("Overgrowth rooted a friendly unit");
                else if (grown < 20)
                    helper.fail("Overgrowth grew only " + grown + " thickets on open ground");
                else
                    helper.succeed();
            } finally {
                friend.discard();
                foe.discard();
                com.solegendary.reignofnether.blocks.ThicketBlockEntity.removeAllOwned(level, owner);
            }
        });
    }

    /**
     * The Storm Oak (Verdant T2 defence): a built oak whose owner has Tier 2 strikes an enemy in range - the one standing
     * in a group rather than a closer loner - never its owner's unit, then waits out its cooldown; without Tier 2 it
     * stays dormant. The placement is never put in the world (invisible to parallel tests) and the strike is limited
     * to this test's foe, so another test's units nearby can't be picked.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void verdant_storm_oak_strikes_foe_not_friend_and_respects_cooldown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_storm_oak", foeOwner = "gametest_storm_oak_foe";
        var bp = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(Buildings.STORM_OAK, level,
            helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, owner, false);
        if (!(bp instanceof com.solegendary.reignofnether.building.buildings.placements.StormOakPlacement oak)) {
            helper.fail("the Storm Oak is not a StormOakPlacement: " + bp);
            return;
        }
        if (!Factions.VERDANT_COURT.equals(Buildings.STORM_OAK.getFaction()))
            helper.fail("the Storm Oak is not a Verdant Court building");
        if (com.solegendary.reignofnether.bot.BotPlayer.t2TowerFor(Factions.VERDANT_COURT) != Buildings.STORM_OAK)
            helper.fail("the Verdant bot does not raise Storm Oaks");
        oak.isBuilt = true;
        var treant = com.solegendary.reignofnether.registrars.EntityRegistrar.SENTINEL_TREANT_UNIT.get();
        var loner = treant.create(level);
        var packA = treant.create(level);
        var packB = treant.create(level);
        var friend = com.solegendary.reignofnether.registrars.EntityRegistrar.SENTINEL_TREANT_UNIT.get().create(level);
        if (loner == null || packA == null || packB == null || friend == null) {
            helper.fail("could not create the Storm Oak test units");
            return;
        }
        BlockPos c = oak.centrePos;
        int gy = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
        friend.moveTo(c.getX() + 0.5, gy, c.getZ() + 2.5, 0, 0);       // the closest unit of all: must never be hit
        loner.moveTo(c.getX() + 4.5, gy, c.getZ() + 0.5, 0, 0);        // a closer enemy on its own
        packA.moveTo(c.getX() + 0.5, gy, c.getZ() + 9.5, 0, 0);        // two enemies together, farther away
        packB.moveTo(c.getX() + 1.5, gy, c.getZ() + 9.5, 0, 0);
        friend.setOwnerName(owner);
        for (var e : List.of(loner, packA, packB))
            e.setOwnerName(foeOwner);
        List<net.minecraft.world.entity.Mob> all = List.of(friend, loner, packA, packB);
        for (var e : all)
            level.addFreshEntity(e);
        // all in this one tick, then discarded: the treants (enemies of every other test's units) must never live long
        // enough for a neighbouring test's archer to pick one as a target
        com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(owner);
        {
            try {
                float friendHp = friend.getHealth();
                if (oak.strike(level, foeOwner) != null)
                    helper.fail("the Storm Oak struck without Tier 2");
                com.solegendary.reignofnether.research.ResearchServerEvents.addResearch(owner,
                    com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_2);
                // the friend is the only unit the oak may see: nothing to strike
                if (oak.strike(level, owner) != null)
                    helper.fail("the Storm Oak struck its owner's unit");
                var hit = oak.strike(level, foeOwner);
                if (hit == null) {
                    helper.fail("the Storm Oak struck nothing with three enemies in range");
                    return;
                }
                if (hit != packA && hit != packB)
                    helper.fail("the Storm Oak struck the lone enemy instead of the group");
                if (hit.getHealth() >= hit.getMaxHealth())
                    helper.fail("the bolt did no damage");
                if (friend.getHealth() < friendHp)
                    helper.fail("the bolt hurt a friendly unit");
                // cooldown: no second bolt until COOLDOWN_TICKS have passed
                hit.invulnerableTime = 0;
                float hp = hit.getHealth();
                if (oak.strike(level, foeOwner) != null || hit.getHealth() < hp)
                    helper.fail("the Storm Oak struck again while on cooldown");
                for (int t = 0; t < com.solegendary.reignofnether.building.buildings.placements.StormOakPlacement.COOLDOWN_TICKS - 1; t++)
                    oak.tickStrike(level);
                if (oak.strike(level, foeOwner) != null)
                    helper.fail("the Storm Oak struck before its cooldown ended");
                oak.tickStrike(level);
                for (var e : List.of(loner, packA, packB))
                    e.invulnerableTime = 0;
                if (oak.strike(level, foeOwner) == null)
                    helper.fail("the Storm Oak did not strike again after its cooldown");
                helper.succeed();
            } finally {
                com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(owner);
                for (var e : all)
                    e.discard();
            }
        }
    }

    // ------------------------------------------------------------------ Tidewrought tidepools (Tides core)

    /**
     * A raise fills only air above a sturdy floor: it never replaces a block (stone, a poppy), never sits on a metal
     * patch, and never goes inside a building's footprint. The building is a placement that is in the server's list
     * only for the synchronous raise (no tick passes), so parallel tests never see it.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void tidepool_raise_fills_only_open_ground(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_tide_raise_" + helper.absolutePos(BlockPos.ZERO).asLong();
        openGround(helper, 1, 1, 14, 14);
        BlockPos centre = helper.absolutePos(new BlockPos(7, 2, 7));
        BlockPos stone = helper.absolutePos(new BlockPos(7, 2, 6));
        BlockPos poppy = helper.absolutePos(new BlockPos(8, 2, 7));
        BlockPos patchFloor = helper.absolutePos(new BlockPos(6, 1, 8));
        level.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(poppy.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);   // so the poppy survives neighbour updates
        level.setBlock(poppy, Blocks.POPPY.defaultBlockState(), 3);
        level.setBlock(patchFloor, MetalPatches.PATCH_BLOCK.defaultBlockState(), 3);
        var placement = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(
            Buildings.WIND_GENERATOR_VILLAGERS, level, helper.absolutePos(new BlockPos(9, 1, 3)), Rotation.NONE, owner, false);
        var buildings = com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings();
        com.solegendary.reignofnether.tide.TidepoolServerEvents.Pool pool;
        buildings.add(placement);
        try {
            pool = com.solegendary.reignofnether.tide.TidepoolServerEvents.raise(level, owner, centre, 3, 400);
        } finally {
            buildings.remove(placement);
        }
        try {
            if (pool == null) {
                helper.fail("the raise was refused: " + com.solegendary.reignofnether.tide.TidepoolServerEvents.lastRefusal());
                return;
            }
            var tide = com.solegendary.reignofnether.registrars.BlockRegistrar.TIDEPOOL.get();
            int insideColumns = 0;
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++) {
                    if (dx * dx + dz * dz > 9)
                        continue;
                    BlockPos col = centre.offset(dx, 0, dz);
                    if (placement.isPosInsideBuilding(col)) {
                        insideColumns++;
                        for (int y = 0; y <= 2; y++)
                            if (level.getBlockState(col.above(y)).is(tide))
                                helper.fail("a tidepool cell was raised inside a building footprint at " + col.above(y));
                    }
                }
            if (insideColumns == 0)
                helper.fail("test setup: the building footprint does not overlap the disc");
            for (BlockPos c : pool.positions()) {
                if (!level.getBlockState(c).is(tide))
                    helper.fail("a registered cell is not a tidepool block at " + c);
                if (!level.getBlockState(c.below()).isFaceSturdy(level, c.below(), net.minecraft.core.Direction.UP))
                    helper.fail("a tidepool cell has no sturdy floor at " + c);
                if (level.getBlockState(c.below()).is(MetalPatches.PATCH_BLOCK))
                    helper.fail("a tidepool cell was raised on a metal patch at " + c);
            }
            if (!level.getBlockState(stone).is(Blocks.STONE) || !level.getBlockState(poppy).is(Blocks.POPPY))
                helper.fail("the raise replaced a block (stone or poppy)");
            if (level.getBlockState(patchFloor.above()).is(tide))
                helper.fail("a tidepool covers the metal patch");
            if (!level.getBlockState(centre).getFluidState().isEmpty())
                helper.fail("a tidepool cell has a fluid state");
            if (pool.cellCount() < 15)
                helper.fail("too few cells for an r3 disc on open ground: " + pool.cellCount());
            helper.succeed();
        } finally {
            com.solegendary.reignofnether.tide.TidepoolServerEvents.clearOwner(owner);
        }
    }

    /**
     * A pool dries up after its lifetime (the 1 Hz expiry pass), and a cell its registry doesn't know - as after a
     * restart - removes itself through its scheduled fail-safe tick.
     */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void tidepool_expires_and_orphan_cells_dry_up(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_tide_expiry_" + helper.absolutePos(BlockPos.ZERO).asLong();
        openGround(helper, 1, 1, 9, 9);
        var pool = com.solegendary.reignofnether.tide.TidepoolServerEvents.raise(level, owner,
            helper.absolutePos(new BlockPos(3, 2, 3)), 1, 20);
        if (pool == null || pool.cellCount() != 5) {
            helper.fail("an r1 raise on open ground should make 5 cells: " + (pool == null ? "refused" : pool.cellCount()));
            com.solegendary.reignofnether.tide.TidepoolServerEvents.clearOwner(owner);
            return;
        }
        var tide = com.solegendary.reignofnether.registrars.BlockRegistrar.TIDEPOOL.get();
        BlockPos orphan = helper.absolutePos(new BlockPos(7, 2, 7));
        level.setBlock(orphan, tide.defaultBlockState(), 3);
        level.scheduleTick(orphan, tide, 5);
        helper.runAfterDelay(30, () -> {
            try {
                com.solegendary.reignofnether.tide.TidepoolServerEvents.tickExpiry(level);
                if (pool.isActive())
                    helper.fail("the pool is still active after its lifetime");
                for (BlockPos c : pool.positions())
                    if (level.getBlockState(c).is(tide))
                        helper.fail("an expired pool left a cell at " + c);
                if (level.getBlockState(orphan).is(tide))
                    helper.fail("an orphan cell (no registry entry) did not dry up on its fail-safe tick");
                helper.succeed();
            } finally {
                com.solegendary.reignofnether.tide.TidepoolServerEvents.clearOwner(owner);
            }
        });
    }

    /**
     * Raising a 5th pool evicts the owner's oldest; another owner's pool is untouched. Trail pools have their own cap
     * per source and never evict the owner's normal pools.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void tidepool_cap_evicts_the_owners_oldest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long key = helper.absolutePos(BlockPos.ZERO).asLong();
        String owner = "gametest_tide_cap_" + key, other = "gametest_tide_cap_other_" + key;
        openGround(helper, 1, 1, 14, 14);
        var tide = com.solegendary.reignofnether.registrars.BlockRegistrar.TIDEPOOL.get();
        var source = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        try {
            var otherPool = com.solegendary.reignofnether.tide.TidepoolServerEvents.raise(level, other,
                helper.absolutePos(new BlockPos(12, 2, 2)), 0, 400);
            List<com.solegendary.reignofnether.tide.TidepoolServerEvents.Pool> mine = new ArrayList<>();
            for (int i = 0; i < 5; i++)
                mine.add(com.solegendary.reignofnether.tide.TidepoolServerEvents.raise(level, owner,
                    helper.absolutePos(new BlockPos(2 + 2 * i, 2, 2)), 0, 400));
            if (otherPool == null || mine.contains(null) || source == null) {
                helper.fail("a cap-test raise was refused: " + com.solegendary.reignofnether.tide.TidepoolServerEvents.lastRefusal());
                return;
            }
            if (mine.get(0).isActive() || level.getBlockState(mine.get(0).centre).is(tide))
                helper.fail("the owner's oldest pool survived a 5th raise");
            for (int i = 1; i < 5; i++)
                if (!mine.get(i).isActive())
                    helper.fail("pool " + i + " was evicted instead of the oldest");
            if (com.solegendary.reignofnether.tide.TidepoolServerEvents.poolsOf(owner).size()
                    != com.solegendary.reignofnether.tide.TidepoolServerEvents.POOLS_PER_PLAYER)
                helper.fail("the owner has " + com.solegendary.reignofnether.tide.TidepoolServerEvents.poolsOf(owner).size() + " pools");
            if (!otherPool.isActive())
                helper.fail("another owner's pool was evicted");
            // trail pools: own cap per source, oldest trail pool evicted, normal pools untouched
            List<com.solegendary.reignofnether.tide.TidepoolServerEvents.Pool> trail = new ArrayList<>();
            int cap = com.solegendary.reignofnether.tide.TidepoolServerEvents.TRAIL_POOLS_PER_SOURCE;
            for (int i = 0; i <= cap; i++)
                trail.add(com.solegendary.reignofnether.tide.TidepoolServerEvents.raiseTrail(level, owner, source,
                    helper.absolutePos(new BlockPos(1 + i, 2, 8)), 0, 400));
            if (trail.contains(null)) {
                helper.fail("a trail raise was refused");
                return;
            }
            if (trail.get(0).isActive())
                helper.fail("the oldest trail pool survived past the trail cap");
            for (int i = 1; i < 5; i++)
                if (!mine.get(i).isActive())
                    helper.fail("a trail pool evicted the owner's normal pool " + i);
            helper.succeed();
        } finally {
            com.solegendary.reignofnether.tide.TidepoolServerEvents.clearOwner(owner);
            com.solegendary.reignofnether.tide.TidepoolServerEvents.clearOwner(other);
            if (source != null)
                source.discard();
        }
    }

    /**
     * A tidal unit on a tidepool gets the Tides speed modifier and regenerates; a non-tidal unit on the same cell gets
     * neither; the modifier goes when the tidal unit steps off. The pool cell is KIND_TIDEPOOL for the pathfinder,
     * walkable for every mobility class and cheapest for TIDAL. Units are never added to the level, so they can't
     * act or be targeted by other tests; tidal-ness comes from the entity tag (the faction has no units yet).
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void tides_buff_tidal_unit_on_a_pool_but_not_others(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_tides_buff_" + helper.absolutePos(BlockPos.ZERO).asLong();
        openGround(helper, 1, 1, 10, 6);
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
        var pool = com.solegendary.reignofnether.tide.TidepoolServerEvents.raise(level, owner, at, 1, 400);
        var vind = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get();
        var tidal = vind.create(level);
        var plain = vind.create(level);
        try {
            if (pool == null || tidal == null || plain == null) {
                helper.fail("could not set up the Tides buff test");
                return;
            }
            tidal.addTag(com.solegendary.reignofnether.tide.TidesServerEvents.TIDAL_TAG);
            tidal.setOwnerName(owner);
            plain.setOwnerName(owner);
            for (var u : List.of(tidal, plain)) {
                u.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
                u.setHealth(u.getMaxHealth() - 6);
            }
            float tidalHp = tidal.getHealth(), plainHp = plain.getHealth();
            com.solegendary.reignofnether.tide.TidesServerEvents.update(tidal);
            com.solegendary.reignofnether.tide.TidesServerEvents.update(plain);
            com.solegendary.reignofnether.tide.TidesServerEvents.update(tidal);   // a second pass must not stack
            if (!com.solegendary.reignofnether.tide.TidesServerEvents.hasSpeedBonus(tidal))
                helper.fail("a tidal unit on a tidepool has no speed modifier");
            if (tidal.getHealth() <= tidalHp)
                helper.fail("a tidal unit on a tidepool did not regenerate");
            if (com.solegendary.reignofnether.tide.TidesServerEvents.hasSpeedBonus(plain) || plain.getHealth() != plainHp)
                helper.fail("a non-tidal unit on a tidepool got the Tides buff");
            if (com.solegendary.reignofnether.unit.pathfinding.MobilityClass.of(tidal) != com.solegendary.reignofnether.unit.pathfinding.MobilityClass.TIDAL)
                helper.fail("a tidal unit is not MobilityClass.TIDAL");
            if (com.solegendary.reignofnether.unit.pathfinding.MobilityClass.of(plain) == com.solegendary.reignofnether.unit.pathfinding.MobilityClass.TIDAL)
                helper.fail("a non-tidal unit is MobilityClass.TIDAL");
            byte kind = com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder.classify(level, at.getX(), at.getY(), at.getZ());
            if (kind != com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder.KIND_TIDEPOOL)
                helper.fail("a tidepool cell classifies as kind " + kind + ", not KIND_TIDEPOOL");
            for (var mc : com.solegendary.reignofnether.unit.pathfinding.MobilityClass.values())
                if (Float.isInfinite(mc.costFor(mc, kind)))
                    helper.fail("a tidepool is not walkable for " + mc);
            var TIDAL = com.solegendary.reignofnether.unit.pathfinding.MobilityClass.TIDAL;
            var HUMANOID = com.solegendary.reignofnether.unit.pathfinding.MobilityClass.HUMANOID;
            if (TIDAL.costFor(TIDAL, kind) >= HUMANOID.costFor(HUMANOID, kind))
                helper.fail("a tidepool is not cheaper for TIDAL than for HUMANOID");
            // step off onto dry ground: the modifier goes
            tidal.moveTo(at.getX() + 5.5, at.getY(), at.getZ() + 0.5, 0, 0);
            com.solegendary.reignofnether.tide.TidesServerEvents.update(tidal);
            if (com.solegendary.reignofnether.tide.TidesServerEvents.hasSpeedBonus(tidal))
                helper.fail("the Tides speed modifier stayed after leaving the pool");
            helper.succeed();
        } finally {
            com.solegendary.reignofnether.tide.TidepoolServerEvents.clearOwner(owner);
            if (tidal != null)
                tidal.discard();
            if (plain != null)
                plain.discard();
        }
    }

    /**
     * Counterplay: fire placed beside a cell evaporates it at once, a fire attack's evaporate() dries the rest, and a
     * drained pool is gone within DRAIN_TICKS (what a constructor's reclaim order does).
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void tidepool_fire_evaporates_and_drain_dries(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_tide_fire_" + helper.absolutePos(BlockPos.ZERO).asLong();
        openGround(helper, 1, 1, 13, 10);
        var tide = com.solegendary.reignofnether.registrars.BlockRegistrar.TIDEPOOL.get();
        BlockPos centre = helper.absolutePos(new BlockPos(5, 2, 5));
        BlockPos edge = centre.offset(0, 0, 2), fire = centre.offset(0, 0, 3);
        var pool = com.solegendary.reignofnether.tide.TidepoolServerEvents.raise(level, owner, centre, 2, 400);
        var drained = com.solegendary.reignofnether.tide.TidepoolServerEvents.raise(level, owner,
            helper.absolutePos(new BlockPos(11, 2, 5)), 1, 400);
        if (pool == null || drained == null || !level.getBlockState(edge).is(tide)) {
            helper.fail("could not raise the fire-test pools");
            com.solegendary.reignofnether.tide.TidepoolServerEvents.clearOwner(owner);
            return;
        }
        level.setBlock(fire, Blocks.FIRE.defaultBlockState(), 3);
        boolean edgeGone = !level.getBlockState(edge).is(tide);
        boolean centreKept = level.getBlockState(centre).is(tide);
        level.setBlock(fire, Blocks.AIR.defaultBlockState(), 3);
        if (!edgeGone)
            helper.fail("fire beside a tidepool cell did not evaporate it");
        if (!centreKept)
            helper.fail("fire beside one cell evaporated a cell it doesn't touch");
        int dried = com.solegendary.reignofnether.tide.TidepoolServerEvents.evaporate(level, centre, 2);
        if (dried < 10)
            helper.fail("evaporate() dried only " + dried + " cells of an r2 pool");
        for (BlockPos c : pool.positions())
            if (level.getBlockState(c).is(tide))
                helper.fail("evaporate() left a cell at " + c);
        if (!com.solegendary.reignofnether.tide.TidepoolServerEvents.drain(drained) || !drained.isDraining()
                || drained.ticksLeft() > com.solegendary.reignofnether.tide.TidepoolServerEvents.DRAIN_TICKS) {
            helper.fail("drain() did not start drying the pool");
            com.solegendary.reignofnether.tide.TidepoolServerEvents.clearOwner(owner);
            return;
        }
        helper.runAfterDelay(com.solegendary.reignofnether.tide.TidepoolServerEvents.DRAIN_TICKS + 5, () -> {
            try {
                com.solegendary.reignofnether.tide.TidepoolServerEvents.tickExpiry(level);
                if (drained.isActive())
                    helper.fail("a drained pool is still active");
                for (BlockPos c : drained.positions())
                    if (level.getBlockState(c).is(tide))
                        helper.fail("a drained pool left a cell at " + c);
                helper.succeed();
            } finally {
                com.solegendary.reignofnether.tide.TidepoolServerEvents.clearOwner(owner);
            }
        });
    }

    // ------------------------------------------------------------------ Tidewrought slice 1 (playable T1)

    /**
     * The Tidewrought's start, the way a readied player start does it (PlayerServerEvents.startRTS without the player):
     * its worker type spawns, the first one becomes the Admiral with Broadside and Riptide, and its capitol foundation
     * is laid at the start position and survives as a site. Also checks every "_tide" economy blueprint (and the refit
     * extractor, which no building owns) loads on the server. Runs on its own sand platform high above the arena.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void tidewrought_capitol_places_and_the_admiral_spawns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var tide = Factions.TIDEWROUGHT;
        String owner = "gametest_tide_start_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 200;
        int cx = base.getX() + 60, cz = base.getZ() - 60;   // clear of the other platform tests (the Court's is at -60/+60, y 220)
        for (int dx = -9; dx <= 9; dx++)
            for (int dz = -9; dz <= 9; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), Blocks.SANDSTONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.SAND.defaultBlockState(), 3);
            }
        for (String nbt : List.of("metal_extractor_tide", "metal_extractor_t2_tide", "wind_generator_tide",
                "energy_converter_tide", "wreck_harbour", "slipway"))
            if (com.solegendary.reignofnether.building.BuildingBlockData.getBuildingNbt(nbt,
                    level.getServer().getResourceManager()) == null)
                helper.fail("the Tidewrought structure " + nbt + " is missing from data/");
        var workerType = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(tide.workerEntityType);
        net.minecraft.world.entity.Entity first = workerType == null ? null : workerType.create(level);
        if (!(first instanceof com.solegendary.reignofnether.unit.units.tide.ShipwrightUnit admiral)) {
            helper.fail("the Tidewrought worker type does not make a Shipwright: " + tide.workerEntityType);
            return;
        }
        admiral.setOwnerName(owner);
        admiral.moveTo(cx + 0.5, y + 1, cz - 7.5, 0, 0);
        com.solegendary.reignofnether.player.CommanderServerEvents.makeCommander(admiral);
        level.addFreshEntity(admiral);
        boolean dgun = false, riptide = false;
        for (var a : admiral.getAbilities().get()) {
            dgun |= a instanceof com.solegendary.reignofnether.ability.abilities.CommanderDGun;
            riptide |= a instanceof com.solegendary.reignofnether.ability.abilities.CommanderAbility
                && com.solegendary.reignofnether.ability.abilities.CommanderAbility.kindFor(admiral)
                    == com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.RIPTIDE;
        }
        if (!dgun || !riptide)
            helper.fail("the Admiral lacks its D-gun (" + dgun + ") or Riptide (" + riptide + ")");
        if (!com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(admiral) || !admiral.canBreatheUnderwater())
            helper.fail("the Admiral is not tagged as the commander, or can drown");
        Building capitol = ReignOfNetherRegistries.BUILDING.get(tide.capitolBuilding);
        var blocks = capitol.getRelativeBlockData(level);
        BlockPos origin = com.solegendary.reignofnether.player.PlayerServerEvents.getBuildingOriginPos(
            new BlockPos(cx, y, cz), blocks);
        var placement = com.solegendary.reignofnether.building.BuildingServerEvents.placeBuilding(capitol, origin,
            Rotation.NONE, owner, new int[] { admiral.getId() }, false, false, true, true);
        if (placement == null) {
            admiral.discard();
            helper.fail("the Wreck Harbour could not be placed at a readied start");
            return;
        }
        helper.runAfterDelay(40, () -> {
            try {
                if (placement.isDestroyedServerside
                        || !com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().contains(placement))
                    helper.fail("the Wreck Harbour site was destroyed right after being placed");
                if (!placement.getBuilding().isCapitol || placement.getBuilding() != Buildings.WRECK_HARBOUR)
                    helper.fail("the placed capitol is " + placement.getBuilding().name);
                helper.succeed();
            } finally {
                com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(placement);
                admiral.discard();
            }
        });
    }

    /**
     * Every Tidewrought unit can be made: the four fighters (Shipwright, Cutlass Raider, Reef Guard, Bombard Crew),
     * each ordered onto its own enemy Villager, land a blow (melee or a mortar shell) within the window; the Gull Spotter
     * is a flying scout with no attack and the Tide Priest a non-fighting support carrying Raise Tidepool. Every unit
     * is tidal (Tides) and cannot drown. One row of the arena per attacker; the Bombard stands outside its dead zone.
     */
    @GameTest(template = ARENA, timeoutTicks = 320)
    public static void tidewrought_units_are_made_and_attack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_tide_atk_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        List<net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob>> reg = List.of(
            com.solegendary.reignofnether.registrars.EntityRegistrar.SHIPWRIGHT_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.CUTLASS_RAIDER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.REEF_GUARD_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.BOMBARD_CREW_UNIT.get());
        List<net.minecraft.world.entity.Mob> attackers = new ArrayList<>(), foes = new ArrayList<>();
        // the non-fighters: made, owned, tidal - never added to the level
        var gull = com.solegendary.reignofnether.registrars.EntityRegistrar.GULL_SPOTTER_UNIT.get().create(level);
        var priest = com.solegendary.reignofnether.registrars.EntityRegistrar.TIDE_PRIEST_UNIT.get().create(level);
        if (gull == null || !gull.isScout() || !gull.isFlying()
                || ((Object) gull) instanceof com.solegendary.reignofnether.unit.interfaces.AttackerUnit)
            helper.fail("the Gull Spotter is not an unarmed flying scout");
        if (priest == null || ((Object) priest) instanceof com.solegendary.reignofnether.unit.interfaces.AttackerUnit
                || priest.getAbilities().get().stream().noneMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.RaiseTidepool))
            helper.fail("the Tide Priest is not a non-fighting support with Raise Tidepool");
        for (net.minecraft.world.entity.Mob e : java.util.Arrays.<net.minecraft.world.entity.Mob>asList(gull, priest))
            if (e instanceof com.solegendary.reignofnether.unit.interfaces.Unit u) {
                u.setOwnerName(owner);
                if (!com.solegendary.reignofnether.tide.TidesServerEvents.isTidal(e) || !e.canBreatheUnderwater())
                    helper.fail(net.minecraft.world.entity.EntityType.getKey(e.getType()) + " is not tidal or can drown");
            }
        if (gull != null) gull.discard();
        if (priest != null) priest.discard();
        openGround(helper, 0, 0, 15, 15);
        for (int i = 0; i < reg.size(); i++) {
            var atk = reg.get(i).create(level);
            var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
            if (!(atk instanceof com.solegendary.reignofnether.unit.interfaces.AttackerUnit) || foe == null) {
                helper.fail("could not create " + net.minecraft.world.entity.EntityType.getKey(reg.get(i)) + " as an attacker");
                for (var e : attackers) e.discard();
                for (var e : foes) e.discard();
                return;
            }
            boolean ranged = atk instanceof com.solegendary.reignofnether.unit.units.tide.BombardCrewUnit;
            BlockPos row = helper.absolutePos(new BlockPos(ranged ? 1 : 4, 2, 1 + i * 4));
            atk.moveTo(row.getX() + 0.5, row.getY(), row.getZ() + 0.5, -90, 0);
            foe.moveTo(row.getX() + (ranged ? 11.5 : 2.5), row.getY(), row.getZ() + 0.5, 90, 0);
            ((com.solegendary.reignofnether.unit.interfaces.Unit) atk).setOwnerName(owner);
            foe.setOwnerName(foeOwner);
            if (!com.solegendary.reignofnether.tide.TidesServerEvents.isTidal(atk) || !atk.canBreatheUnderwater())
                helper.fail(net.minecraft.world.entity.EntityType.getKey(reg.get(i)) + " is not tidal or can drown");
            level.addFreshEntity(atk);
            level.addFreshEntity(foe);
            attackers.add(atk);
            foes.add(foe);
        }
        helper.runAfterDelay(3, () -> {
            for (int i = 0; i < attackers.size(); i++)
                ((com.solegendary.reignofnether.unit.interfaces.AttackerUnit) attackers.get(i)).setUnitAttackTarget(foes.get(i));
        });
        boolean[] landed = new boolean[attackers.size()];
        for (int t = 4; t < 300; t++)
            helper.runAfterDelay(t, () -> {
                for (int i = 0; i < attackers.size(); i++) {
                    var atk = attackers.get(i);
                    var hit = atk.getLastHurtMob();
                    if (hit instanceof com.solegendary.reignofnether.unit.interfaces.Unit u && foeOwner.equals(u.getOwnerName()))
                        landed[i] = true;
                    if (foes.get(i).getLastHurtByMob() == atk || foes.get(i).getHealth() < foes.get(i).getMaxHealth())
                        landed[i] = true;
                }
            });
        helper.runAfterDelay(300, () -> {
            List<String> problems = new ArrayList<>();
            for (int i = 0; i < attackers.size(); i++)
                if (!landed[i])
                    problems.add(net.minecraft.world.entity.EntityType.getKey(reg.get(i)).getPath() + " never hit its target");
            for (var e : attackers) e.discard();
            for (var e : foes) e.discard();
            if (!problems.isEmpty())
                helper.fail(String.join("; ", problems));
            helper.succeed();
        });
    }

    /**
     * Tide Priest: Raise Tidepool raises a pool (registered to the priest's owner, cells on the ground near the aimed
     * spot), pays its energy and starts its cooldown; with no energy left a second cast is refused and raises nothing.
     * The priest is never added to the level.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void tide_priest_raises_a_pool(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_tide_priest_" + helper.absolutePos(BlockPos.ZERO).asLong();
        openGround(helper, 1, 1, 14, 14);
        var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 0, 0);
        pool.addEnergy(com.solegendary.reignofnether.ability.abilities.RaiseTidepool.ENERGY_COST + 10);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
        var priest = com.solegendary.reignofnether.registrars.EntityRegistrar.TIDE_PRIEST_UNIT.get().create(level);
        try {
            if (priest == null) {
                helper.fail("could not create a Tide Priest");
                return;
            }
            priest.setOwnerName(owner);
            BlockPos at = helper.absolutePos(new BlockPos(3, 2, 7));
            priest.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
            com.solegendary.reignofnether.ability.abilities.RaiseTidepool raise = null;
            for (var a : priest.getAbilities().get())
                if (a instanceof com.solegendary.reignofnether.ability.abilities.RaiseTidepool r)
                    raise = r;
            if (raise == null) {
                helper.fail("the Tide Priest has no Raise Tidepool");
                return;
            }
            BlockPos aim = helper.absolutePos(new BlockPos(9, 1, 7));   // the ground block, 6 blocks off
            raise.use(level, priest, aim);
            var pools = com.solegendary.reignofnether.tide.TidepoolServerEvents.poolsOf(owner);
            if (pools.isEmpty()) {
                helper.fail("Raise Tidepool raised nothing: " + com.solegendary.reignofnether.tide.TidepoolServerEvents.lastRefusal());
                return;
            }
            var p = pools.get(0);
            if (p.cellCount() < 9 || p.centre.distSqr(aim.above()) > 4)
                helper.fail("the pool is too small or in the wrong place: " + p.cellCount() + " cells at " + p.centre);
            var tide = com.solegendary.reignofnether.registrars.BlockRegistrar.TIDEPOOL.get();
            if (!level.getBlockState(aim.above()).is(tide))
                helper.fail("no tidepool cell on the aimed ground");
            if (pool.getEnergy() > 10.01)
                helper.fail("Raise Tidepool did not charge its energy: " + pool.getEnergy());
            if (raise.isOffCooldown(priest))
                helper.fail("Raise Tidepool did not go on cooldown");
            // out of energy: refused, nothing raised (the registry cap is not the limit here)
            var refused = com.solegendary.reignofnether.ability.abilities.RaiseTidepool.cast(level, priest,
                helper.absolutePos(new BlockPos(9, 1, 12)));
            if (refused != null || com.solegendary.reignofnether.tide.TidepoolServerEvents.poolsOf(owner).size() != 1)
                helper.fail("a cast without the energy for it still raised a pool");
            helper.succeed();
        } finally {
            com.solegendary.reignofnether.tide.TidepoolServerEvents.clearOwner(owner);
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
            if (priest != null)
                priest.discard();
        }
    }

    /**
     * The Admiral's Broadside: the walking cannonade hits the enemy on its path (D-gun damage), spares the friend on the
     * same path and the enemy behind the Admiral, charges energy and cools down. The shots land over several ticks, so
     * the check runs after the last one.
     */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void tidewrought_broadside_hits_foes_not_friends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_tide_broadside_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        openGround(helper, 0, 0, 15, 15);
        var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 0, 0);
        pool.addEnergy(500);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
        var vill = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get();
        var admiral = com.solegendary.reignofnether.registrars.EntityRegistrar.SHIPWRIGHT_UNIT.get().create(level);
        var onPath = vill.create(level);      // 5 blocks down the line
        var friend = vill.create(level);      // 7.5 blocks down the line, ours
        var behind = vill.create(level);      // behind the Admiral
        if (admiral == null || onPath == null || friend == null || behind == null) {
            helper.fail("could not create the Broadside test units");
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
            return;
        }
        // the Admiral at the west edge so the 15-block cannonade (+ its 2-block bursts) stays out of the next arena
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 7));
        admiral.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, -90, 0);
        onPath.moveTo(at.getX() + 5.5, at.getY(), at.getZ() + 0.5, 0, 0);
        friend.moveTo(at.getX() + 8.0, at.getY(), at.getZ() + 0.5, 0, 0);
        behind.moveTo(at.getX() - 1.5, at.getY(), at.getZ() + 0.5, 0, 0);
        admiral.setOwnerName(owner);
        friend.setOwnerName(owner);
        onPath.setOwnerName(foeOwner);
        behind.setOwnerName(foeOwner);
        List<net.minecraft.world.entity.Mob> all = List.of(admiral, onPath, friend, behind);
        for (var e : all)
            level.addFreshEntity(e);
        com.solegendary.reignofnether.player.CommanderServerEvents.makeCommander(admiral);
        com.solegendary.reignofnether.ability.abilities.CommanderDGun dgun = null;
        for (var a : admiral.getAbilities().get())
            if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderDGun d)
                dgun = d;
        if (dgun == null || com.solegendary.reignofnether.ability.abilities.CommanderDGun.kindFor(admiral)
                != com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.BROADSIDE) {
            helper.fail("the Admiral has no Broadside D-gun");
            for (var e : all) e.discard();
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
            return;
        }
        final var broadside = dgun;
        float[] hp = new float[3];
        helper.runAfterDelay(5, () -> {
            for (var e : List.of(onPath, friend, behind))
                e.moveTo(e.getX(), at.getY(), e.getZ(), e.getYRot(), 0);   // pinned where the test put them
            hp[0] = onPath.getHealth(); hp[1] = friend.getHealth(); hp[2] = behind.getHealth();
            broadside.use(level, admiral, BlockPos.containing(at.getX() + 12, at.getY(), at.getZ()));
            if (pool.getEnergy() > 500 - com.solegendary.reignofnether.ability.abilities.CommanderDGun.ENERGY_COST + 0.01)
                helper.fail("Broadside did not charge energy: " + pool.getEnergy());
            if (broadside.isOffCooldown(admiral))
                helper.fail("Broadside did not go on cooldown");
        });
        int last = 5 + (com.solegendary.reignofnether.ability.abilities.CommanderDGun.BROADSIDE_SHOTS - 1)
            * com.solegendary.reignofnether.ability.abilities.CommanderDGun.BROADSIDE_DELAY + 15;
        helper.runAfterDelay(last, () -> {
            try {
                if (onPath.isAlive() && hp[0] - onPath.getHealth() < 40)
                    helper.fail("Broadside barely hurt the enemy on its path: " + onPath.getHealth() + " of " + hp[0]);
                if (friend.getHealth() < hp[1])
                    helper.fail("Broadside hurt a friendly unit");
                if (behind.getHealth() < hp[2])
                    helper.fail("Broadside hurt an enemy behind the Admiral");
                helper.succeed();
            } finally {
                for (var e : all)
                    e.discard();
                com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
            }
        });
    }

    /**
     * The Admiral's Riptide: of two enemies equally far into the cone, the one standing on a tidepool is thrown back
     * clearly harder than the one on dry ground; a friend in the cone is neither hurt nor shoved; an enemy behind the
     * Admiral is untouched. Velocities are read right after the cast, before a tick moves anyone.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void tidewrought_riptide_knocks_foes_back_harder_in_a_pool(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_tide_riptide_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        openGround(helper, 0, 0, 15, 15);
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 7));
        BlockPos wetSpot = at.offset(4, 0, 2), drySpot = at.offset(4, 0, -2);
        var poolRaised = com.solegendary.reignofnether.tide.TidepoolServerEvents.raise(level, owner, wetSpot, 1, 400);
        var vill = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get();
        var admiral = com.solegendary.reignofnether.registrars.EntityRegistrar.SHIPWRIGHT_UNIT.get().create(level);
        var wet = vill.create(level);
        var dry = vill.create(level);
        var friend = vill.create(level);
        var behind = vill.create(level);
        List<net.minecraft.world.entity.Mob> all = new ArrayList<>();
        for (net.minecraft.world.entity.Mob e : java.util.Arrays.<net.minecraft.world.entity.Mob>asList(admiral, wet, dry, friend, behind))
            if (e != null)
                all.add(e);
        if (poolRaised == null || all.size() != 5) {
            helper.fail("could not set up the Riptide test (pool " + poolRaised + ")");
            for (var e : all) e.discard();
            com.solegendary.reignofnether.tide.TidepoolServerEvents.clearOwner(owner);
            return;
        }
        admiral.setOwnerName(owner);
        friend.setOwnerName(owner);
        wet.setOwnerName(foeOwner);
        dry.setOwnerName(foeOwner);
        behind.setOwnerName(foeOwner);
        Runnable place = () -> {
            admiral.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, -90, 0);
            wet.moveTo(wetSpot.getX() + 0.5, wetSpot.getY(), wetSpot.getZ() + 0.5, 0, 0);
            dry.moveTo(drySpot.getX() + 0.5, drySpot.getY(), drySpot.getZ() + 0.5, 0, 0);
            friend.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5, 0, 0);
            behind.moveTo(at.getX() - 2.5, at.getY(), at.getZ() + 0.5, 0, 0);
            for (var e : all)
                e.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        };
        place.run();
        for (var e : all)
            level.addFreshEntity(e);
        com.solegendary.reignofnether.player.CommanderServerEvents.makeCommander(admiral);
        com.solegendary.reignofnether.ability.abilities.CommanderAbility sig = null;
        for (var a : admiral.getAbilities().get())
            if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderAbility c)
                sig = c;
        if (sig == null) {
            helper.fail("the Admiral has no signature ability");
            for (var e : all) e.discard();
            com.solegendary.reignofnether.tide.TidepoolServerEvents.clearOwner(owner);
            return;
        }
        final var riptide = sig;
        helper.runAfterDelay(5, () -> {
            try {
                place.run();
                if (!com.solegendary.reignofnether.tide.TidesServerEvents.isWet(wet) || com.solegendary.reignofnether.tide.TidesServerEvents.isWet(dry))
                    helper.fail("test setup: the wet foe is not on the pool or the dry one is");
                float friendHp = friend.getHealth(), behindHp = behind.getHealth();
                riptide.use(level, admiral, at.offset(6, -1, 0));
                double vWet = wet.getDeltaMovement().horizontalDistance(), vDry = dry.getDeltaMovement().horizontalDistance();
                if (vDry < 0.3)
                    helper.fail("Riptide barely moved the dry enemy: " + vDry);
                if (vWet < vDry * 1.4)
                    helper.fail("Riptide did not throw the enemy in the pool harder: wet " + vWet + " vs dry " + vDry);
                if (wet.getHealth() >= wet.getMaxHealth() || dry.getHealth() >= dry.getMaxHealth())
                    helper.fail("Riptide did not hurt the enemies in its cone");
                if (wet.getX() - admiral.getX() < 0 || wet.getDeltaMovement().x <= 0)
                    helper.fail("Riptide pushed the enemy the wrong way");
                if (friend.getHealth() < friendHp || friend.getDeltaMovement().horizontalDistance() > 0.05)
                    helper.fail("Riptide hurt or shoved a friendly unit");
                if (behind.getHealth() < behindHp || behind.getDeltaMovement().horizontalDistance() > 0.05)
                    helper.fail("Riptide hit an enemy behind the Admiral");
                if (riptide.isOffCooldown(admiral))
                    helper.fail("Riptide did not go on cooldown");
                helper.succeed();
            } finally {
                for (var e : all)
                    e.discard();
                com.solegendary.reignofnether.tide.TidepoolServerEvents.clearOwner(owner);
            }
        });
    }

    /**
     * Cutlass Raider Ambush and Reef Guard regen, through the real Tides pass: a Raider stepping off a tidepool is primed
     * (40% more damage on its next hit, gone once the window closes); on the pool a Reef Guard heals more per pass than a
     * Raider. Units are never added to the level; tidal-ness comes from their faction.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void tidewrought_cutlass_ambush_and_reef_guard_regen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_tide_ambush_" + helper.absolutePos(BlockPos.ZERO).asLong();
        openGround(helper, 1, 1, 12, 8);
        BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
        var p = com.solegendary.reignofnether.tide.TidepoolServerEvents.raise(level, owner, at, 1, 400);
        var raider = com.solegendary.reignofnether.registrars.EntityRegistrar.CUTLASS_RAIDER_UNIT.get().create(level);
        var guard = com.solegendary.reignofnether.registrars.EntityRegistrar.REEF_GUARD_UNIT.get().create(level);
        try {
            if (p == null || raider == null || guard == null) {
                helper.fail("could not set up the Ambush / regen test");
                return;
            }
            raider.setOwnerName(owner);
            guard.setOwnerName(owner);
            for (var u : List.of(raider, guard)) {
                u.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
                u.setHealth(u.getMaxHealth() - 10);
            }
            float base = raider.getUnitAttackDamage();
            if (raider.isAmbushPrimed())
                helper.fail("a fresh Cutlass Raider starts with its Ambush primed");
            float rHp = raider.getHealth(), gHp = guard.getHealth();
            com.solegendary.reignofnether.tide.TidesServerEvents.update(raider);
            com.solegendary.reignofnether.tide.TidesServerEvents.update(guard);
            float rHeal = raider.getHealth() - rHp, gHeal = guard.getHealth() - gHp;
            if (rHeal <= 0 || gHeal <= rHeal)
                helper.fail("on a pool the Reef Guard should out-heal the Raider: guard +" + gHeal + ", raider +" + rHeal);
            // step off the pool: the pass sees it and primes the Ambush
            raider.moveTo(at.getX() + 6.5, at.getY(), at.getZ() + 0.5, 0, 0);
            com.solegendary.reignofnether.tide.TidesServerEvents.update(raider);
            if (!raider.isAmbushPrimed())
                helper.fail("stepping off a tidepool did not prime the Ambush");
            float primed = raider.getUnitAttackDamage();
            if (Math.abs(primed - base * (1 + com.solegendary.reignofnether.unit.units.tide.CutlassRaiderUnit.AMBUSH_BONUS)) > 0.01)
                helper.fail("the Ambush hit does " + primed + ", expected " + base + " x1.4");
            // the window closes on its own
            raider.onLeftWater(level.getGameTime() - com.solegendary.reignofnether.unit.units.tide.CutlassRaiderUnit.AMBUSH_TICKS - 1);
            if (raider.isAmbushPrimed() || raider.getUnitAttackDamage() != base)
                helper.fail("the Ambush outlived its window");
            helper.succeed();
        } finally {
            com.solegendary.reignofnether.tide.TidepoolServerEvents.clearOwner(owner);
            if (raider != null) raider.discard();
            if (guard != null) guard.discard();
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // crash audit: every registered unit and building must have the data the HUD, renderers and saves look up.
    // A bot's Bonewright crashed lovish's client (Oct 10) because one lookup table missed a unit added that day;
    // these scan the registries so the next unit or building added in parallel can't slip through the same way.
    // ------------------------------------------------------------------------------------------------------------

    /** A file inside our own mod jar (assets/ is not loaded by a dedicated server's resource manager). */
    static boolean modFileHas(String... path) {
        var modFile = net.minecraftforge.fml.ModList.get().getModFileById(ReignOfNether.MOD_ID);
        return modFile != null && java.nio.file.Files.exists(modFile.getFile().findResource(path));
    }

    static com.google.gson.JsonObject enUsLang() {
        var modFile = net.minecraftforge.fml.ModList.get().getModFileById(ReignOfNether.MOD_ID);
        if (modFile == null)
            return new com.google.gson.JsonObject();
        try (var reader = java.nio.file.Files.newBufferedReader(
                modFile.getFile().findResource("assets", ReignOfNether.MOD_ID, "lang", "en_us.json"))) {
            return com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            return new com.google.gson.JsonObject();
        }
    }

    /**
     * Every unit entity type we register can be built off-world and has a cost, an abilities object, a faction when it
     * is trainable, a production item when its faction registered it, a lang name and a mob-head icon (the HUD's unit
     * buttons, control groups and item shop all blit textures/mobheads/&lt;name&gt;.png).
     */
    @GameTest(template = ARENA)
    public static void every_unit_type_has_cost_faction_abilities_icon_and_name(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        com.google.gson.JsonObject lang = enUsLang();
        if (lang.size() == 0)
            helper.fail("could not read assets/reignofnether/lang/en_us.json from the mod file");
        // registered to a faction without a production item on purpose: spawned by abilities/research, not trained
        Set<String> noProdOk = Set.of("silverfish_unit", "armoured_hoglin_unit");
        List<String> gaps = new ArrayList<>();
        int checked = 0;
        for (var type : net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES) {
            ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (key == null || !ReignOfNether.MOD_ID.equals(key.getNamespace()))
                continue;
            net.minecraft.world.entity.Entity e = null;
            try {
                e = type.create(level);
                if (!(e instanceof com.solegendary.reignofnether.unit.interfaces.Unit unit))
                    continue;
                checked++;
                String id = key.getPath();
                if (unit.getCost() == null)
                    gaps.add(id + ": getCost() is null");
                if (unit.getAbilities() == null)
                    gaps.add(id + ": getAbilities() is null");
                if (Factions.getFaction(unit) == null)
                    gaps.add(id + ": faction is null");
                @SuppressWarnings("unchecked")
                var prod = e instanceof net.minecraft.world.entity.Mob
                    ? com.solegendary.reignofnether.building.production.ProductionItems.getProductionItem(
                        (net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob>) type)
                    : null;
                boolean factionRegistered = Factions.ENTITY_FACTION.containsKey(key);
                if (prod != null && !factionRegistered)
                    gaps.add(id + ": trainable but registered to no faction");
                if (factionRegistered && prod == null && !noProdOk.contains(id))
                    gaps.add(id + ": registered to a faction but has no ProductionItem (ProductionItems.ALL / EntityRegistrar.getEntityType)");
                if (!lang.has("entity." + ReignOfNether.MOD_ID + "." + id))
                    gaps.add(id + ": no lang entry entity." + ReignOfNether.MOD_ID + "." + id);
                String icon = com.solegendary.reignofnether.util.MiscUtil.getEntityIconName(e);
                if (!modFileHas("assets", ReignOfNether.MOD_ID, "textures", "mobheads", icon + ".png"))
                    gaps.add(id + ": no mob head textures/mobheads/" + icon + ".png");
            } catch (Exception ex) {
                gaps.add(key.getPath() + ": threw " + ex);
            } finally {
                if (e != null)
                    e.discard();
            }
        }
        if (checked < 60)
            helper.fail("only " + checked + " unit types were checked - the registry scan is broken");
        if (!gaps.isEmpty())
            helper.fail("unit types with missing data: " + gaps);
        helper.succeed();
    }

    /**
     * Every icon the build bar and production queue blit exists: a building's icon from our own namespace ships in the
     * mod file, a vanilla one names a texture vanilla actually has (no "waxed_" blocks - they share the unwaxed
     * texture, which is how the Drydock's icon became the missing-texture "?" in lovish's clip), and every unit
     * production item's mob head (textures/mobheads/&lt;unit&gt;.png, what its queue/start buttons use) is in the jar.
     * Research items build their icons client-side, so they are covered by the lang/entity checks instead.
     */
    @GameTest(template = ARENA)
    public static void every_building_and_production_item_icon_ships(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> gaps = new ArrayList<>();
        int checked = 0;
        for (Building b : ReignOfNetherRegistries.BUILDING) {
            checked++;
            ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(b);
            String id = key == null ? String.valueOf(b.name) : key.getPath();
            if (b.icon == null) {
                gaps.add(id + ": no icon");
                continue;
            }
            if (ReignOfNether.MOD_ID.equals(b.icon.getNamespace())) {
                String[] parts = ("assets/" + ReignOfNether.MOD_ID + "/" + b.icon.getPath()).split("/");
                if (!modFileHas(parts))
                    gaps.add(id + ": icon " + b.icon + " not in the mod file");
            } else if ("minecraft".equals(b.icon.getNamespace())) {
                if (!b.icon.getPath().startsWith("textures/") || !b.icon.getPath().endsWith(".png"))
                    gaps.add(id + ": vanilla icon " + b.icon + " is not a texture path");
                if (b.icon.getPath().contains("waxed_"))
                    gaps.add(id + ": vanilla icon " + b.icon + " - waxed blocks have no texture of their own");
            }
        }
        for (com.solegendary.reignofnether.building.production.ProductionItem item : ReignOfNetherRegistries.PRODUCTION_ITEM) {
            if (!(item instanceof com.solegendary.reignofnether.building.production.IUnitProductionItem))
                continue;
            ResourceLocation key = ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(item);
            String id = key == null ? item.getItemName() : key.getPath();
            net.minecraft.world.entity.EntityType<?> type = null;
            try {
                type = com.solegendary.reignofnether.registrars.EntityRegistrar.getEntityType(item.getItemName());
            } catch (Exception ignored) { }
            if (type == null)
                continue;   // not a trained unit by name (every faction unit is covered by the unit-type test above)
            checked++;
            net.minecraft.world.entity.Entity e = null;
            try {
                e = type.create(level);
                if (e == null) {
                    gaps.add(id + ": entity type " + type + " could not be created");
                    continue;
                }
                String icon = com.solegendary.reignofnether.util.MiscUtil.getEntityIconName(e);
                if (!modFileHas("assets", ReignOfNether.MOD_ID, "textures", "mobheads", icon + ".png"))
                    gaps.add(id + ": no mob head textures/mobheads/" + icon + ".png");
            } finally {
                if (e != null)
                    e.discard();
            }
        }
        if (checked < 120)
            helper.fail("only " + checked + " icons were checked - the registry scans are broken");
        if (!gaps.isEmpty())
            helper.fail("missing icons: " + gaps);
        helper.succeed();
    }

    /**
     * Results-screen bookkeeping (lovish's clip: Units always equalled Military, and the damage award ignored the 37
     * buildings the winner levelled): a spawned worker counts as a worker, a spawned soldier as military, each once in
     * the total; building damage is credited to the attacker's owner, never for hitting your own. The RTSPlayer is
     * registered and removed within one server-thread call, so no other test (they run in parallel) ever sees it.
     */
    @GameTest(template = ARENA)
    public static void unit_scores_split_workers_from_military_and_building_damage_is_credited(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_scorekeeper";
        var worker = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        var soldier = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (worker == null || soldier == null) {
            helper.fail("could not create the test units");
            return;
        }
        var player = com.solegendary.reignofnether.player.RTSPlayer.getNewBot(owner,
            com.solegendary.reignofnether.faction.Factions.VILLAGERS);
        var players = com.solegendary.reignofnether.player.PlayerServerEvents.rtsPlayers;
        players.add(player);
        try {
            com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement.recordUnitProduced(owner, worker);
            com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement.recordUnitProduced(owner, soldier);
            com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement.recordUnitProduced(owner, soldier);
            var sl = player.scores.getScoreList();
            int total = sl.get(com.solegendary.reignofnether.player.RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED);
            int workers = sl.get(com.solegendary.reignofnether.player.RTSPlayerScoresEnum.WORKER_UNITS_PRODUCED);
            int military = sl.get(com.solegendary.reignofnether.player.RTSPlayerScoresEnum.MILITARY_UNITS_PRODUCED);
            if (total != 3 || workers != 1 || military != 2)
                helper.fail("unit scores total/workers/military = " + total + "/" + workers + "/" + military + ", expected 3/1/2");

            com.solegendary.reignofnether.player.PlayerServerEvents.creditDamage(owner, "gametest_victim", 40f);
            com.solegendary.reignofnether.player.PlayerServerEvents.creditDamage(owner, owner, 25f);      // own building
            com.solegendary.reignofnether.player.PlayerServerEvents.creditDamage(owner, null, 2.5f);      // a neutral
            if (Math.abs(player.damageDealt - 42.5f) > 0.01f)
                helper.fail("damage credited " + player.damageDealt + ", expected 42.5 (own-building hits must not count)");
        } finally {
            players.remove(player);
            worker.discard();
            soldier.discard();
        }
        helper.succeed();
    }

    /**
     * The T2 workers' and T3s' own skins (tools/gen_t2t3_skins.py) are referenced by client-only renderers this server
     * test can't load, so check the files those renderers name ship in the mod: a typo would only show up in game as
     * the purple-and-black missing texture. Also the War Mammoth's "carrying passengers" portrait (HudClientEvents
     * blits &lt;name&gt;_half.png for a unit that is a vehicle).
     */
    @GameTest(template = ARENA)
    public static void t2_worker_and_t3_skins_ship_in_the_mod(GameTestHelper helper) {
        List<String> missing = new ArrayList<>();
        for (String skin : List.of("royal_architect_unit", "embalmer_unit", "bonewright_unit", "war_mammoth_unit",
                "sun_colossus_unit", "bone_dragon_unit"))
            if (!modFileHas("assets", ReignOfNether.MOD_ID, "textures", "entities", skin + ".png"))
                missing.add("textures/entities/" + skin + ".png");
        if (!modFileHas("assets", ReignOfNether.MOD_ID, "textures", "mobheads", "war_mammoth_half.png"))
            missing.add("textures/mobheads/war_mammoth_half.png");
        if (!missing.isEmpty())
            helper.fail("skins missing from the mod file: " + missing);
        helper.succeed();
    }

    /**
     * Every registered building: its blueprint (and upgraded blueprint, and both bridge blueprints) exists in data/ for
     * the server AND assets/ for the client's ghost preview; it has a name, icon, portrait block and lang name; and its
     * save key round-trips (BuildingSaveData writes the registry key and reads it back).
     */
    @GameTest(template = ARENA)
    public static void every_building_has_blueprints_names_icons_and_a_save_key(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var resources = level.getServer().getResourceManager();
        com.google.gson.JsonObject lang = enUsLang();
        if (lang.size() == 0)
            helper.fail("could not read assets/reignofnether/lang/en_us.json from the mod file");
        List<String> gaps = new ArrayList<>();
        int checked = 0;
        for (Building b : ReignOfNetherRegistries.BUILDING) {
            checked++;
            ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(b);
            String id = key == null ? String.valueOf(b.name) : key.getPath();
            if (key == null || ReignOfNetherRegistries.BUILDING.get(key) != b)
                gaps.add(id + ": registry key does not round-trip (saves would drop it)");
            if (b.name == null || b.name.isEmpty())
                gaps.add(id + ": no name");
            if (b.icon == null)
                gaps.add(id + ": no icon");
            if (b.portraitBlock == null)
                gaps.add(id + ": no portrait block (the building portrait would NPE)");
            if (key != null && !lang.has("buildings." + key.getNamespace() + "." + key.getPath())
                    && !lang.has("buildings." + b.getFaction().getName() + "." + key.getNamespace() + "." + key.getPath()))
                gaps.add(id + ": no lang name");
            Set<String> structures = new HashSet<>();
            if (b instanceof AbstractBridge bridge) {
                structures.add(bridge.getDiagonalStructureName());
                structures.add(bridge.getOrthogonalStructureName());
            } else {
                for (int lvl = 0; lvl <= 2; lvl++)
                    structures.add(b.getUpgradedStructureName(lvl));
            }
            for (String s : structures) {
                if (s == null || s.isEmpty()) {
                    gaps.add(id + ": empty structure name");
                    continue;
                }
                if (BuildingBlockData.getBuildingNbt(s, resources) == null)
                    gaps.add(id + ": data/" + ReignOfNether.MOD_ID + "/structures/" + s + ".nbt missing");
                if (!modFileHas("assets", ReignOfNether.MOD_ID, "structures", s + ".nbt"))
                    gaps.add(id + ": assets/" + ReignOfNether.MOD_ID + "/structures/" + s + ".nbt missing");
            }
        }
        if (checked < 60)
            helper.fail("only " + checked + " buildings were checked - the registry scan is broken");
        if (!gaps.isEmpty())
            helper.fail("buildings with missing data: " + gaps);
        helper.succeed();
    }

    /**
     * Clip 15-56: a vanilla village (with a Supplementaries palisade) generated across the battlefield. Skirmish
     * worlds are created with structures off; worlds that have them anyway get every village piece in the arena
     * reverted to grass. Builds a little "village house" (sunken cobblestone floor, plank walls, a stocked chest,
     * a path, a villager) on a grass platform high above the terrain and runs the clearing on its box.
     */
    @GameTest(template = ARENA)
    public static void arena_structure_clearing_leaves_no_village(GameTestHelper helper) {
        if (com.solegendary.reignofnether.startpos.ArenaStructureClearing.skirmishWorldOptions(42L).generateStructures())
            helper.fail("skirmish worlds must be created with structure generation off");
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 212;
        int cx = base.getX(), cz = base.getZ();
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -5; dz <= 5; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        // the house: floor sunk into the soil (replacing the grass), plank walls and roof
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.COBBLESTONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y + 4, cz + dz), Blocks.OAK_PLANKS.defaultBlockState(), 3);
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2)
                    for (int up = 1; up <= 3; up++)
                        level.setBlock(new BlockPos(cx + dx, y + up, cz + dz), Blocks.OAK_PLANKS.defaultBlockState(), 3);
            }
        BlockPos chest = new BlockPos(cx, y + 1, cz);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        if (level.getBlockEntity(chest) instanceof net.minecraft.world.Container c)
            c.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 16));
        for (int dz = -5; dz <= 5; dz++)
            level.setBlock(new BlockPos(cx + 4, y, cz + dz), Blocks.DIRT_PATH.defaultBlockState(), 3);
        var villager = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
        if (villager == null) {
            helper.fail("could not create a villager");
            return;
        }
        villager.setNoAi(true);
        villager.moveTo(cx - 4 + 0.5, y + 1, cz + 0.5);
        level.addFreshEntity(villager);

        var box = new net.minecraft.world.level.levelgen.structure.BoundingBox(cx - 5, y - 3, cz - 5, cx + 5, y + 6, cz + 5);
        com.solegendary.reignofnether.startpos.ArenaStructureClearing.clearBox(level, box, cx + 0.5f, cz + 0.5f, 64f);
        var aabb = new net.minecraft.world.phys.AABB(cx - 6, y - 3, cz - 6, cx + 7, y + 7, cz + 7);
        com.solegendary.reignofnether.startpos.ArenaStructureClearing.sweepStrays(level, aabb, cx + 0.5f, cz + 0.5f, 64f);

        List<String> left = new ArrayList<>();
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -5; dz <= 5; dz++) {
                BlockPos g = new BlockPos(cx + dx, y, cz + dz);
                if (!level.getBlockState(g).is(Blocks.GRASS_BLOCK))
                    left.add("ground at " + dx + "," + dz + " is " + level.getBlockState(g));
                for (int up = 1; up <= 5; up++)
                    if (!level.getBlockState(g.above(up)).isAir())
                        left.add(level.getBlockState(g.above(up)) + " at " + dx + "," + up + "," + dz);
            }
        // (another test's battlefield may have refused the villager outright - either way none may be left)
        if (!level.getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, aabb).isEmpty())
            left.add("the village's villager");
        if (!level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, aabb).isEmpty())
            left.add("loot spilled from the chest");
        if (!left.isEmpty())
            helper.fail("village remains after clearing: " + left.subList(0, Math.min(8, left.size())));
        helper.succeed();
    }

    static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, path);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Tidewrought slice 2: Harpooner, Flak Swivel, Mudskipper Sloop, the Admiral's Fog Bank
    // ------------------------------------------------------------------------------------------------------------

    /**
     * The Harpooner's pull: a pulling throw hurts an enemy 9 blocks off and drags it at least 3 blocks closer; a friend
     * just as far is refused (canPull) and never shoved. The Harpooner is never added to the level (so it can't throw
     * on its own); the foe and the friend are, so vanilla physics carries out the haul.
     */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void tidewrought_harpooner_pulls_a_foe_not_a_friend(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_tide_harpoon_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        openGround(helper, 1, 1, 14, 13);
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 7));
        var harpooner = com.solegendary.reignofnether.registrars.EntityRegistrar.HARPOONER_UNIT.get().create(level);
        var vill = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get();
        var foe = vill.create(level);
        var friend = vill.create(level);
        if (harpooner == null || foe == null || friend == null) {
            helper.fail("could not create the Harpooner test units");
            return;
        }
        harpooner.setOwnerName(owner);
        friend.setOwnerName(owner);
        foe.setOwnerName(foeOwner);
        harpooner.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, -90, 0);
        foe.moveTo(at.getX() + 9.5, at.getY(), at.getZ() + 0.5, 0, 0);
        friend.moveTo(at.getX() + 7.5, at.getY(), at.getZ() + 5.5, 0, 0);
        level.addFreshEntity(foe);
        level.addFreshEntity(friend);
        List<net.minecraft.world.entity.Mob> all = List.of(harpooner, foe, friend);
        double[] before = new double[2];
        boolean[] done = { false };
        helper.runAfterDelay(5, () -> {
            before[0] = foe.distanceTo(harpooner);
            before[1] = friend.distanceTo(harpooner);
            float foeHp = foe.getHealth();
            friend.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            String problem = null;
            if (harpooner.canPull(friend) || harpooner.pull(friend)) {
                problem = "the Harpooner would pull a friendly unit";
            } else if (friend.getDeltaMovement().horizontalDistance() > 0.01) {
                problem = "a refused pull still shoved the friend";
            } else if (!harpooner.canPull(foe)) {
                problem = "the Harpooner refuses to pull an enemy villager";
            } else {
                harpooner.harpoon(foe, true);
                if (foe.getHealth() >= foeHp)
                    problem = "the harpoon did not hurt the enemy";
                else if (foe.getDeltaMovement().x >= -0.2)
                    problem = "the pull did not throw the enemy toward the Harpooner: " + foe.getDeltaMovement();
            }
            if (problem != null) {
                // helper.fail throws: clean up first
                done[0] = true;
                for (var e : all)
                    e.discard();
                helper.fail(problem);
            }
        });
        helper.runAfterDelay(35, () -> {
            if (done[0])
                return;
            try {
                double foeNow = foe.distanceTo(harpooner), friendNow = friend.distanceTo(harpooner);
                if (foeNow > before[0] - 3)
                    helper.fail("the pulled enemy only came from " + before[0] + " to " + foeNow + " blocks");
                else if (foeNow < com.solegendary.reignofnether.unit.units.tide.HarpoonerUnit.PULL_STOP - 1)
                    helper.fail("the pull overshot onto the Harpooner: " + foeNow);
                else if (Math.abs(friendNow - before[1]) > 0.5)
                    helper.fail("the friend moved from " + before[1] + " to " + friendNow);
                else
                    helper.succeed();
            } finally {
                for (var e : all)
                    e.discard();
            }
        });
    }

    /**
     * The Flak Swivel's burst hurts a flyer (a Gull Spotter) far more than a ground unit, and the multiplier does not
     * leak out of the hit. The crew is never added to the level, so it can't open fire on its own (an earlier hit's
     * invulnerability frames would swallow the measured one).
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void tidewrought_flak_swivel_hits_flyers_much_harder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_tide_flak_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        var flak = com.solegendary.reignofnether.registrars.EntityRegistrar.FLAK_SWIVEL_UNIT.get().create(level);
        var gull = com.solegendary.reignofnether.registrars.EntityRegistrar.GULL_SPOTTER_UNIT.get().create(level);
        var walker = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        if (flak == null || gull == null || walker == null) {
            helper.fail("could not create the Flak Swivel test units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(8, 2, 8));
        flak.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        gull.moveTo(at.getX() + 4.5, at.getY() + 2, at.getZ() + 0.5, 0, 0);
        walker.moveTo(at.getX() - 3.5, at.getY(), at.getZ() + 0.5, 0, 0);
        flak.setOwnerName(owner);
        gull.setOwnerName(foeOwner);
        walker.setOwnerName(foeOwner);
        level.addFreshEntity(gull);
        level.addFreshEntity(walker);
        List<net.minecraft.world.entity.Mob> all = List.of(flak, gull, walker);
        helper.runAfterDelay(5, () -> {
            try {
                if (com.solegendary.reignofnether.unit.units.tide.FlakSwivelUnit.multiplierAgainst(gull) <= 1
                        || com.solegendary.reignofnether.unit.units.tide.FlakSwivelUnit.multiplierAgainst(walker) >= 1) {
                    helper.fail("flyer check: gull x" + com.solegendary.reignofnether.unit.units.tide.FlakSwivelUnit.multiplierAgainst(gull)
                            + ", villager x" + com.solegendary.reignofnether.unit.units.tide.FlakSwivelUnit.multiplierAgainst(walker));
                    return;
                }
                gull.setHealth(gull.getMaxHealth());
                walker.setHealth(walker.getMaxHealth());
                float gullHp = gull.getHealth(), walkerHp = walker.getHealth();
                flak.burst(gull);
                flak.burst(walker);
                float toFlyer = gullHp - gull.getHealth(), toGround = walkerHp - walker.getHealth();
                if (toGround <= 0)
                    helper.fail("the burst did no damage to the ground unit");
                else if (toFlyer < toGround * 4)
                    helper.fail("the burst should hurt a flyer much more than a walker: " + toFlyer + " vs " + toGround);
                else if (flak.getUnitAttackDamage() != com.solegendary.reignofnether.unit.units.tide.FlakSwivelUnit.attackDamage)
                    helper.fail("the burst multiplier leaked out of the hit: base damage now " + flak.getUnitAttackDamage());
                else
                    helper.succeed();
            } finally {
                for (var e : all)
                    e.discard();
            }
        });
    }

    /**
     * The Mudskipper Sloop on a tidepool gets the Tides speed modifier with its own bonus on top - faster than it is on
     * land by at least half, and a bigger step than a Cutlass Raider's on the same pool - and loses it on dry ground.
     * Units are never added to the level; tidal-ness comes from their faction.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void tidewrought_mudskipper_is_faster_on_a_tidepool(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_tide_mudskipper_" + helper.absolutePos(BlockPos.ZERO).asLong();
        openGround(helper, 1, 1, 12, 8);
        BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
        var p = com.solegendary.reignofnether.tide.TidepoolServerEvents.raise(level, owner, at, 1, 400);
        var sloop = com.solegendary.reignofnether.registrars.EntityRegistrar.MUDSKIPPER_SLOOP_UNIT.get().create(level);
        var raider = com.solegendary.reignofnether.registrars.EntityRegistrar.CUTLASS_RAIDER_UNIT.get().create(level);
        try {
            if (p == null || sloop == null || raider == null) {
                helper.fail("could not set up the Mudskipper test (pool " + p + ")");
                return;
            }
            sloop.setOwnerName(owner);
            raider.setOwnerName(owner);
            var speed = net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED;
            for (var u : List.<net.minecraft.world.entity.LivingEntity>of(sloop, raider))
                u.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
            double sloopDry = sloop.getAttributeValue(speed), raiderDry = raider.getAttributeValue(speed);
            if (com.solegendary.reignofnether.unit.pathfinding.MobilityClass.of(sloop) != com.solegendary.reignofnether.unit.pathfinding.MobilityClass.TIDAL)
                helper.fail("the Mudskipper Sloop is not MobilityClass.TIDAL");
            com.solegendary.reignofnether.tide.TidesServerEvents.update(sloop);
            com.solegendary.reignofnether.tide.TidesServerEvents.update(raider);
            double sloopWet = sloop.getAttributeValue(speed), raiderWet = raider.getAttributeValue(speed);
            if (sloopWet < sloopDry * 1.5)
                helper.fail("the Mudskipper Sloop on a tidepool is only x" + (sloopWet / sloopDry) + " as fast");
            if (sloopWet / sloopDry <= raiderWet / raiderDry)
                helper.fail("the Sloop's tidepool bonus (x" + (sloopWet / sloopDry) + ") is no bigger than a Raider's (x"
                        + (raiderWet / raiderDry) + ")");
            sloop.moveTo(at.getX() + 6.5, at.getY(), at.getZ() + 0.5, 0, 0);
            com.solegendary.reignofnether.tide.TidesServerEvents.update(sloop);
            if (Math.abs(sloop.getAttributeValue(speed) - sloopDry) > 1e-6)
                helper.fail("the Sloop kept its tidepool speed on dry ground: " + sloop.getAttributeValue(speed));
            helper.succeed();
        } finally {
            com.solegendary.reignofnether.tide.TidepoolServerEvents.clearOwner(owner);
            if (sloop != null) sloop.discard();
            if (raider != null) raider.discard();
        }
    }

    /**
     * The Admiral's Fog Bank: an Admiral has it, and casting it hides a friendly unit inside the cloud from an enemy 8
     * blocks away (invisible, and the enemy's target search skips it); a Holy Bell rung by that enemy's side then
     * strips the cover like a thicket's. The enemy and the bell ringer are never added to the level (they can't act);
     * an arena-sized bell keeps the reveal out of the neighbouring tests.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void tidewrought_fog_bank_hides_friends_and_holy_bell_reveals(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_tide_fog_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        openGround(helper, 1, 1, 14, 8);
        BlockPos spot = helper.absolutePos(new BlockPos(4, 2, 4));
        var ship = com.solegendary.reignofnether.registrars.EntityRegistrar.SHIPWRIGHT_UNIT.get();
        var admiral = ship.create(level);
        var friend = ship.create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        var architect = com.solegendary.reignofnether.registrars.EntityRegistrar.ROYAL_ARCHITECT_UNIT.get().create(level);
        if (admiral == null || friend == null || foe == null || architect == null) {
            helper.fail("could not create the Fog Bank test units");
            return;
        }
        admiral.setOwnerName(owner);
        friend.setOwnerName(owner);
        foe.setOwnerName(foeOwner);
        architect.setOwnerName(foeOwner);
        admiral.moveTo(spot.getX() - 2.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
        friend.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
        foe.moveTo(spot.getX() + 8.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
        architect.moveTo(spot.getX() + 4.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
        level.addFreshEntity(admiral);
        level.addFreshEntity(friend);
        com.solegendary.reignofnether.player.CommanderServerEvents.makeCommander(admiral);
        List<net.minecraft.world.entity.Mob> all = List.of(admiral, friend, foe, architect);
        Runnable cleanup = () -> {
            for (var e : all)
                e.discard();
            com.solegendary.reignofnether.tide.FogBankServerEvents.clearOwner(owner);
            com.solegendary.reignofnether.blocks.ThicketCover.refresh(level.getServer());
        };
        com.solegendary.reignofnether.ability.abilities.FogBank fog = null;
        for (var a : admiral.getAbilities().get())
            if (a instanceof com.solegendary.reignofnether.ability.abilities.FogBank f)
                fog = f;
        if (fog == null) {
            helper.fail("the Admiral has no Fog Bank");
            cleanup.run();
            return;
        }
        final var fogBank = fog;
        helper.runAfterDelay(5, () -> {
            try {
                // control: before the fog the 8-block enemy can target the friend
                if (com.solegendary.reignofnether.blocks.ThicketCover.isHidden(friend)
                        || com.solegendary.reignofnether.util.MiscUtil.findClosestAttackableEntity(foe, 10, level) != friend) {
                    helper.fail("precondition: the enemy cannot see the friend before the fog");
                    return;
                }
                fogBank.use(level, admiral, spot.below());   // also runs a cover pass
                if (fogBank.isOffCooldown(admiral))
                    helper.fail("Fog Bank did not go on cooldown");
                if (!com.solegendary.reignofnether.blocks.ThicketCover.isHidden(friend) || !friend.isInvisible()
                        || !com.solegendary.reignofnether.unit.Concealment.isHiddenFrom(friend, foeOwner)) {
                    helper.fail("a friendly unit inside the Fog Bank is not hidden");
                    return;
                }
                if (com.solegendary.reignofnether.util.MiscUtil.findClosestAttackableEntity(foe, 10, level) == friend) {
                    helper.fail("an enemy 8 blocks away can still target the unit in the fog");
                    return;
                }
                if (com.solegendary.reignofnether.unit.Concealment.isHiddenFrom(friend, owner))
                    helper.fail("the fog hides the unit from its own side");
                // the bell: arena-sized (6 blocks round a ringer 4 off), as in holy_bell_breaks_thicket_cover
                var rung = com.solegendary.reignofnether.ability.abilities.HolyBell.ring(level, architect, foeOwner, 6);
                com.solegendary.reignofnether.blocks.ThicketCover.refresh(level.getServer());
                if (!rung.contains(friend))
                    helper.fail("Holy Bell skipped a unit hidden in the fog");
                else if (com.solegendary.reignofnether.blocks.ThicketCover.isHidden(friend) || friend.isInvisible()
                        || com.solegendary.reignofnether.unit.Concealment.isHiddenFrom(friend, foeOwner))
                    helper.fail("the Fog Bank kept the unit hidden through the Holy Bell reveal");
                else
                    helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    /**
     * The slice-2 Slipway units are tidal, can't drown and land hits through their real attack goals: the Harpooner
     * (bow-cooldown goal on its trident) and the Flak Swivel (ranged goal) from 9 blocks, the Mudskipper Sloop (Alex's
     * Mobs body, melee goal) from close by.
     */
    @GameTest(template = ARENA, timeoutTicks = 320)
    public static void tidewrought_slice2_units_attack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_tide2_atk_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        List<net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob>> reg = List.of(
            com.solegendary.reignofnether.registrars.EntityRegistrar.HARPOONER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.FLAK_SWIVEL_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.MUDSKIPPER_SLOOP_UNIT.get());
        List<net.minecraft.world.entity.Mob> attackers = new ArrayList<>(), foes = new ArrayList<>();
        openGround(helper, 0, 0, 15, 15);
        for (int i = 0; i < reg.size(); i++) {
            var atk = reg.get(i).create(level);
            var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
            if (!(atk instanceof com.solegendary.reignofnether.unit.interfaces.AttackerUnit) || foe == null) {
                helper.fail("could not create " + net.minecraft.world.entity.EntityType.getKey(reg.get(i)) + " as an attacker");
                for (var e : attackers) e.discard();
                for (var e : foes) e.discard();
                return;
            }
            boolean ranged = !(atk instanceof com.solegendary.reignofnether.unit.units.tide.MudskipperSloopUnit);
            BlockPos row = helper.absolutePos(new BlockPos(ranged ? 2 : 4, 2, 2 + i * 5));
            atk.moveTo(row.getX() + 0.5, row.getY(), row.getZ() + 0.5, -90, 0);
            foe.moveTo(row.getX() + (ranged ? 9.5 : 3.0), row.getY(), row.getZ() + 0.5, 90, 0);
            ((com.solegendary.reignofnether.unit.interfaces.Unit) atk).setOwnerName(owner);
            foe.setOwnerName(foeOwner);
            if (!com.solegendary.reignofnether.tide.TidesServerEvents.isTidal(atk) || !atk.canBreatheUnderwater())
                helper.fail(net.minecraft.world.entity.EntityType.getKey(reg.get(i)) + " is not tidal or can drown");
            level.addFreshEntity(atk);
            level.addFreshEntity(foe);
            attackers.add(atk);
            foes.add(foe);
        }
        helper.runAfterDelay(3, () -> {
            for (int i = 0; i < attackers.size(); i++)
                ((com.solegendary.reignofnether.unit.interfaces.AttackerUnit) attackers.get(i)).setUnitAttackTarget(foes.get(i));
        });
        boolean[] landed = new boolean[attackers.size()];
        for (int t = 4; t < 300; t++)
            helper.runAfterDelay(t, () -> {
                for (int i = 0; i < attackers.size(); i++)
                    if (foes.get(i).getLastHurtByMob() == attackers.get(i) || foes.get(i).getHealth() < foes.get(i).getMaxHealth())
                        landed[i] = true;
            });
        helper.runAfterDelay(300, () -> {
            List<String> problems = new ArrayList<>();
            for (int i = 0; i < attackers.size(); i++)
                if (!landed[i])
                    problems.add(net.minecraft.world.entity.EntityType.getKey(reg.get(i)).getPath() + " never hit its target");
            for (var e : attackers) e.discard();
            for (var e : foes) e.discard();
            if (!problems.isEmpty())
                helper.fail(String.join("; ", problems));
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ Tidewrought slice 3 (T2: the Drydock)

    /**
     * Tidewrought T2 (slice 3): the Drydock trains the Harbourmaster and the T2 units and refuses all of them until its
     * owner has Tier 2 - checked on the server when production starts, not only by the build button. The bot kit points
     * at it (and its T2 tower is the Lighthouse Battery), and the Wreck Harbour still offers no Tier 3.
     */
    @GameTest(template = ARENA)
    public static void tidewrought_drydock_trains_only_after_tier_2(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_tide_t2_" + helper.absolutePos(BlockPos.ZERO).asLong();
        var items = List.<com.solegendary.reignofnether.building.production.ProductionItem>of(
            com.solegendary.reignofnether.building.production.ProductionItems.HARBOURMASTER,
            com.solegendary.reignofnether.building.production.ProductionItems.KRAKEN_CALLER,
            com.solegendary.reignofnether.building.production.ProductionItems.MORTAR_GALLEY,
            com.solegendary.reignofnether.building.production.ProductionItems.SKY_CORSAIR);
        var lab = Buildings.DRYDOCK;
        if (!Factions.TIDEWROUGHT.equals(lab.getFaction()) || !Factions.TIDEWROUGHT.equals(Buildings.LIGHTHOUSE_BATTERY.getFaction()))
            helper.fail("the Drydock or the Lighthouse Battery is not a Tidewrought building");
        for (var item : items)
            if (!lab.productions.get().contains(item))
                helper.fail("the Drydock does not train " + item.getItemName());
        var kit = com.solegendary.reignofnether.bot.BotPlayer.kitFor(Factions.TIDEWROUGHT);
        if (kit == null || kit.t2Lab() != lab || kit.t2Worker() != com.solegendary.reignofnether.building.production.ProductionItems.HARBOURMASTER)
            helper.fail("the Tidewrought bot kit does not use the Drydock and the Harbourmaster");
        else
            for (var item : kit.t2Army())
                if (!lab.productions.get().contains(item))
                    helper.fail("the Tidewrought bot's T2 army has " + item.getItemName() + ", which the Drydock does not train");
        if (kit != null && (kit.t2Army().isEmpty() || !kit.t2Army().contains(com.solegendary.reignofnether.building.production.ProductionItems.KRAKEN_CALLER)))
            helper.fail("the Tidewrought bot's T2 army has no Kraken Caller");
        if (com.solegendary.reignofnether.bot.BotPlayer.t2TowerFor(Factions.TIDEWROUGHT) != Buildings.LIGHTHOUSE_BATTERY)
            helper.fail("the Tidewrought bot does not raise Lighthouse Batteries");
        // a placement that is never put in the world: enough for the produce gate, and invisible to parallel tests
        var bp = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(lab, level,
            helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, owner, false);
        if (!(bp instanceof com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement pp)) {
            helper.fail("the Drydock is not a production placement: " + bp);
            return;
        }
        com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(owner);
        try {
            for (var item : items)
                if (item.canProduce(pp))
                    helper.fail(item.getItemName() + " can be trained without Tier 2");
            if (pp.startProductionItem(com.solegendary.reignofnether.building.production.ProductionItems.KRAKEN_CALLER) || !pp.productionQueue.isEmpty())
                helper.fail("the server queued a Kraken Caller without Tier 2");
            com.solegendary.reignofnether.research.ResearchServerEvents.addResearch(owner,
                com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_2);
            for (var item : items)
                if (!item.canProduce(pp))
                    helper.fail(item.getItemName() + " still refused after Tier 2: " + item.getProduceErrorMsg(pp));
            helper.succeed();
        } finally {
            com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(owner);
        }
    }

    /**
     * The Harbourmaster is the Tidewrought T2 constructor: double build power and health, a T2 worker, Tidewrought and
     * tidal, with Raise Great Tidepool (which a plain Shipwright must not get). The great pool is wider and lasts longer
     * than the Tide Priest's, charges its energy, and a cast without the energy raises nothing.
     */
    @GameTest(template = ARENA)
    public static void tidewrought_harbourmaster_is_a_t2_worker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_harbourmaster_" + helper.absolutePos(BlockPos.ZERO).asLong();
        openGround(helper, 1, 1, 14, 14);
        var hm = com.solegendary.reignofnether.registrars.EntityRegistrar.HARBOURMASTER_UNIT.get().create(level);
        var ship = com.solegendary.reignofnether.registrars.EntityRegistrar.SHIPWRIGHT_UNIT.get().create(level);
        var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 0, 0);
        pool.addEnergy(com.solegendary.reignofnether.ability.abilities.RaiseGreatTidepool.ENERGY_COST + 10);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
        try {
            if (hm == null || ship == null) {
                helper.fail("could not create the workers");
                return;
            }
            hm.setOwnerName(owner);
            ship.setOwnerName(owner);
            BlockPos at = helper.absolutePos(new BlockPos(2, 2, 7));
            hm.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
            if (hm.getBuildPower() < 1.99f)
                helper.fail("the Harbourmaster builds at " + hm.getBuildPower());
            if (!com.solegendary.reignofnether.unit.T2Workers.isT2Worker(hm))
                helper.fail("the Harbourmaster is not a T2 worker");
            if (com.solegendary.reignofnether.unit.T2Workers.isT2Worker(ship))
                helper.fail("a plain Shipwright counts as a T2 worker");
            if (!Factions.TIDEWROUGHT.equals(Factions.getFaction(hm)) || !com.solegendary.reignofnether.tide.TidesServerEvents.isTidal(hm))
                helper.fail("the Harbourmaster is not a tidal Tidewrought unit");
            if (hm.getMaxHealth() < 2 * ship.getMaxHealth() - 0.01f)
                helper.fail("the Harbourmaster has " + hm.getMaxHealth() + " health, the Shipwright " + ship.getMaxHealth());
            if (ship.getAbilities().get().stream().anyMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.RaiseGreatTidepool))
                helper.fail("a plain Shipwright got Raise Great Tidepool");
            com.solegendary.reignofnether.ability.abilities.RaiseGreatTidepool great = null;
            for (var a : hm.getAbilities().get())
                if (a instanceof com.solegendary.reignofnether.ability.abilities.RaiseGreatTidepool g)
                    great = g;
            if (great == null) {
                helper.fail("the Harbourmaster has no Raise Great Tidepool");
                return;
            }
            BlockPos aim = helper.absolutePos(new BlockPos(9, 1, 7));   // the ground block, 7 blocks off
            great.use(level, hm, aim);
            var pools = com.solegendary.reignofnether.tide.TidepoolServerEvents.poolsOf(owner);
            if (pools.isEmpty()) {
                helper.fail("Raise Great Tidepool raised nothing: " + com.solegendary.reignofnether.tide.TidepoolServerEvents.lastRefusal());
                return;
            }
            var p = pools.get(0);
            // radius 3 on open ground is 29 cells (the priest's radius 2 is 13); a few may sit under an arena edge
            if (p.radius != com.solegendary.reignofnether.ability.abilities.RaiseGreatTidepool.RADIUS || p.cellCount() <= 13)
                helper.fail("the great pool is not bigger than a priest's: radius " + p.radius + ", " + p.cellCount() + " cells");
            if (p.ticksLeft() <= com.solegendary.reignofnether.tide.TidepoolServerEvents.DEFAULT_LIFETIME_TICKS)
                helper.fail("the great pool does not outlast a priest's: " + p.ticksLeft() + " ticks left");
            if (pool.getEnergy() > 10.01)
                helper.fail("Raise Great Tidepool did not charge its energy: " + pool.getEnergy());
            if (great.isOffCooldown(hm))
                helper.fail("Raise Great Tidepool did not go on cooldown");
            var refused = com.solegendary.reignofnether.ability.abilities.RaiseGreatTidepool.cast(level, hm,
                helper.absolutePos(new BlockPos(4, 1, 12)));
            if (refused != null || com.solegendary.reignofnether.tide.TidepoolServerEvents.poolsOf(owner).size() != 1)
                helper.fail("a cast without the energy for it still raised a pool");
            helper.succeed();
        } finally {
            com.solegendary.reignofnether.tide.TidepoolServerEvents.clearOwner(owner);
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
            if (hm != null)
                hm.discard();
            if (ship != null)
                ship.discard();
        }
    }

    /**
     * Tentacle Grab: a friend is never grabbed (and the ability stays ready); an enemy is hurt, rooted and dragged
     * toward the Kraken Caller; an enemy standing on a tidepool is rooted for longer and hurt harder. Everything runs in
     * the tick the units are added and they are discarded at once, so no neighbouring test's unit can pick them.
     */
    @GameTest(template = ARENA)
    public static void tidewrought_kraken_caller_grabs_a_foe_not_a_friend(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_kraken_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        openGround(helper, 1, 1, 14, 14);
        var caller = com.solegendary.reignofnether.registrars.EntityRegistrar.KRAKEN_CALLER_UNIT.get().create(level);
        var villager = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get();
        var friend = villager.create(level);
        var foe = villager.create(level);
        var wetFoe = villager.create(level);
        if (caller == null || friend == null || foe == null || wetFoe == null) {
            helper.fail("could not create the Kraken Caller test units");
            return;
        }
        int gy = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
        BlockPos c = helper.absolutePos(new BlockPos(2, 2, 7));
        caller.moveTo(c.getX() + 0.5, gy, c.getZ() + 0.5, 0, 0);
        friend.moveTo(c.getX() + 9.5, gy, c.getZ() + 0.5, 0, 0);
        foe.moveTo(c.getX() + 9.5, gy, c.getZ() - 4.5, 0, 0);
        wetFoe.moveTo(c.getX() + 9.5, gy, c.getZ() + 5.5, 0, 0);
        caller.setOwnerName(owner);
        friend.setOwnerName(owner);
        foe.setOwnerName(foeOwner);
        wetFoe.setOwnerName(foeOwner);
        List<net.minecraft.world.entity.Mob> all = List.of(caller, friend, foe, wetFoe);
        for (var e : all)
            level.addFreshEntity(e);
        try {
            com.solegendary.reignofnether.ability.abilities.TentacleGrab grab = null;
            for (var a : caller.getAbilities().get())
                if (a instanceof com.solegendary.reignofnether.ability.abilities.TentacleGrab g)
                    grab = g;
            if (grab == null) {
                helper.fail("the Kraken Caller has no Tentacle Grab");
                return;
            }
            // a friend: nothing happens and nothing is spent
            float friendHp = friend.getHealth();
            grab.use(level, caller, friend);
            if (friend.getHealth() < friendHp || friend.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)
                    || friend.getDeltaMovement().x < -0.01 || !grab.isOffCooldown(caller))
                helper.fail("Tentacle Grab touched a friend (or went on cooldown for it)");
            if (com.solegendary.reignofnether.ability.abilities.TentacleGrab.grab(level, caller, friend))
                helper.fail("TentacleGrab.grab accepted a friend");
            // an enemy on dry ground: hurt, rooted, hauled toward the caller (-x)
            float foeHp = foe.getHealth();
            grab.use(level, caller, foe);
            var root = foe.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
            if (root == null || root.getAmplifier() < com.solegendary.reignofnether.ability.abilities.TentacleGrab.ROOT_AMPLIFIER)
                helper.fail("the grabbed enemy is not rooted");
            if (foe.getHealth() >= foeHp)
                helper.fail("the grab did no damage");
            if (foe.getDeltaMovement().x >= -0.05)
                helper.fail("the grabbed enemy is not dragged toward the caller: " + foe.getDeltaMovement());
            if (grab.isOffCooldown(caller))
                helper.fail("Tentacle Grab did not go on cooldown");
            // an enemy on a tidepool: rooted for longer, hurt harder
            var tide = com.solegendary.reignofnether.tide.TidepoolServerEvents.raise(level, owner, wetFoe.blockPosition(), 1, 200);
            if (tide == null || !com.solegendary.reignofnether.tide.TidesServerEvents.isWet(wetFoe)) {
                helper.fail("could not put the second enemy on a tidepool: " + com.solegendary.reignofnether.tide.TidepoolServerEvents.lastRefusal());
                return;
            }
            float wetHp = wetFoe.getHealth();
            if (!com.solegendary.reignofnether.ability.abilities.TentacleGrab.grab(level, caller, wetFoe))
                helper.fail("the grab refused an enemy on a tidepool");
            var wetRoot = wetFoe.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
            if (wetRoot == null || root == null || wetRoot.getDuration() <= root.getDuration())
                helper.fail("the root on a tidepool is not longer: " + (wetRoot == null ? "none" : wetRoot.getDuration()));
            if (wetHp - wetFoe.getHealth() <= foeHp - foe.getHealth())
                helper.fail("the grab on a tidepool does not hurt more");
            helper.succeed();
        } finally {
            com.solegendary.reignofnether.tide.TidepoolServerEvents.clearOwner(owner);
            for (var e : all)
                e.discard();
        }
    }

    /**
     * The Lighthouse Battery: an enemy hidden in its own Fog Bank (ThicketCover) stays hidden while the beam points
     * away, is stripped of its cover - and stays uncovered through the next cover pass - when the beam sweeps over it,
     * and can then be shot. Without Tier 2 the tower is dormant. The placement is never put in the world and every
     * call is limited to this test's foe.
     */
    @GameTest(template = ARENA)
    public static void tidewrought_lighthouse_reveals_a_hidden_enemy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_lighthouse_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        openGround(helper, 1, 1, 14, 14);
        var bp = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(Buildings.LIGHTHOUSE_BATTERY, level,
            helper.absolutePos(new BlockPos(1, 2, 1)), Rotation.NONE, owner, false);
        if (!(bp instanceof com.solegendary.reignofnether.building.buildings.placements.LighthouseBatteryPlacement lh)) {
            helper.fail("the Lighthouse Battery is not a LighthouseBatteryPlacement: " + bp);
            return;
        }
        lh.isBuilt = true;
        if (lh.getSightRange() < 40)
            helper.fail("the built Lighthouse Battery only sees " + lh.getSightRange());
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        if (foe == null) {
            helper.fail("could not create the hidden enemy");
            return;
        }
        int gy = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
        foe.moveTo(lh.centrePos.getX() + 10.5, gy, lh.centrePos.getZ() + 6.5, 0, 0);
        foe.setOwnerName(foeOwner);
        level.addFreshEntity(foe);
        com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(owner);
        try {
            com.solegendary.reignofnether.tide.FogBankServerEvents.raise(level, foeOwner, foe.getId(), foe.getX(), foe.getY(), foe.getZ());
            com.solegendary.reignofnether.blocks.ThicketCover.refresh(level.getServer());
            if (!com.solegendary.reignofnether.blocks.ThicketCover.isHidden(foe)
                    || !com.solegendary.reignofnether.unit.Concealment.isHiddenFrom(foe, owner)) {
                helper.fail("precondition: the enemy is not hidden in its Fog Bank");
                return;
            }
            float at = lh.angleTo(foe.getX(), foe.getZ());
            // dormant without Tier 2
            if (lh.revealSweep(level, at, foeOwner) != 0 || !com.solegendary.reignofnether.blocks.ThicketCover.isHidden(foe))
                helper.fail("the Lighthouse revealed without Tier 2");
            com.solegendary.reignofnether.research.ResearchServerEvents.addResearch(owner,
                com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_2);
            // the beam pointing the other way leaves it hidden, and the gun cannot see it
            if (lh.revealSweep(level, at + 180f, foeOwner) != 0 || !com.solegendary.reignofnether.blocks.ThicketCover.isHidden(foe))
                helper.fail("the beam revealed an enemy behind it");
            if (lh.shoot(level, foeOwner) != null)
                helper.fail("the Lighthouse shot a hidden enemy");
            // the beam on it: revealed now and through the next cover pass
            if (lh.revealSweep(level, at, foeOwner) != 1)
                helper.fail("the beam did not light the hidden enemy");
            com.solegendary.reignofnether.blocks.ThicketCover.refresh(level.getServer());
            if (com.solegendary.reignofnether.blocks.ThicketCover.isHidden(foe)
                    || com.solegendary.reignofnether.unit.Concealment.isHiddenFrom(foe, owner)) {
                helper.fail("the enemy is still hidden after the beam swept it");
                return;
            }
            float hp = foe.getHealth();
            if (lh.shoot(level, foeOwner) != foe || foe.getHealth() >= hp)
                helper.fail("the Lighthouse did not shoot the revealed enemy");
            helper.succeed();
        } finally {
            com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(owner);
            com.solegendary.reignofnether.tide.FogBankServerEvents.clearOwner(foeOwner);
            foe.discard();
            com.solegendary.reignofnether.blocks.ThicketCover.refresh(level.getServer());
        }
    }

    /**
     * The Sky Corsair's stick of bombs: three shells along its heading through the target splash the enemy standing
     * there and never the friend standing right beside it. The bombs land a second later, so the check waits.
     */
    @GameTest(template = ARENA, timeoutTicks = 140)
    public static void tidewrought_sky_corsair_bombs_foes_not_friends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_corsair_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        openGround(helper, 1, 1, 14, 14);
        var corsair = com.solegendary.reignofnether.registrars.EntityRegistrar.SKY_CORSAIR_UNIT.get().create(level);
        var villager = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get();
        var foe = villager.create(level);
        var friend = villager.create(level);
        if (corsair == null || foe == null || friend == null) {
            helper.fail("could not create the Sky Corsair test units");
            return;
        }
        int gy = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
        BlockPos c = helper.absolutePos(new BlockPos(4, 2, 7));
        corsair.moveTo(c.getX() + 0.5, gy + 8, c.getZ() + 0.5, 0, 0);
        foe.moveTo(c.getX() + 4.5, gy, c.getZ() + 0.5, 0, 0);
        friend.moveTo(c.getX() + 4.5, gy, c.getZ() + 1.7, 0, 0);   // 1.2 blocks off the foe: inside every splash
        corsair.setOwnerName(owner);
        friend.setOwnerName(owner);
        foe.setOwnerName(foeOwner);
        List<net.minecraft.world.entity.Mob> all = List.of(corsair, foe, friend);
        for (var e : all)
            level.addFreshEntity(e);
        if (!com.solegendary.reignofnether.unit.units.verdant.WispChoirUnit.isFlyer(corsair)) {
            for (var e : all) e.discard();
            helper.fail("the Sky Corsair is not a flyer to anti-air");
        }
        float friendHp = friend.getHealth(), foeHp = foe.getHealth();
        int dropped = corsair.dropBombs(foe.getX(), foe.getY(), foe.getZ());
        if (dropped != com.solegendary.reignofnether.unit.units.tide.SkyCorsairUnit.BOMBS) {
            for (var e : all) e.discard();   // before the fail: helper.fail throws
            helper.fail("the Sky Corsair dropped " + dropped + " bombs");
            return;
        }
        helper.runAfterDelay(100, () -> {
            try {
                if (friend.getHealth() < friendHp)
                    helper.fail("the bombs hurt a friendly unit: " + friend.getHealth() + "/" + friendHp);
                else if (foe.isAlive() && foe.getHealth() >= foeHp)
                    helper.fail("the bombs missed the enemy under them");
                else
                    helper.succeed();
            } finally {
                for (var e : all)
                    e.discard();
            }
        });
    }

    /**
     * The Drydock's fighting units are tidal, can't drown and land hits through their real attack goals: the Kraken
     * Caller (bow-cooldown goal, brine lash) from 8 blocks, the Mortar Galley (Alex's Mobs turtle body, shell) from 14
     * - outside its dead zone - and the Sky Corsair (flying, bombs) from 8 above and 4 across.
     */
    @GameTest(template = ARENA, timeoutTicks = 420)
    public static void tidewrought_t2_units_attack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_tide3_atk_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        List<net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob>> reg = List.of(
            com.solegendary.reignofnether.registrars.EntityRegistrar.KRAKEN_CALLER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.MORTAR_GALLEY_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.SKY_CORSAIR_UNIT.get());
        double[] reach = { 8.5, 14.5, 4.5 };
        List<net.minecraft.world.entity.Mob> attackers = new ArrayList<>(), foes = new ArrayList<>();
        List<String> setup = new ArrayList<>();
        openGround(helper, 0, 0, 15, 15);
        int gy = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
        for (int i = 0; i < reg.size(); i++) {
            var atk = reg.get(i).create(level);
            var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
            if (!(atk instanceof com.solegendary.reignofnether.unit.interfaces.AttackerUnit) || foe == null) {
                for (var e : attackers) e.discard();   // before the fail: helper.fail throws
                for (var e : foes) e.discard();
                helper.fail("could not create " + net.minecraft.world.entity.EntityType.getKey(reg.get(i)) + " as an attacker");
                return;
            }
            boolean flyer = atk instanceof com.solegendary.reignofnether.unit.units.tide.SkyCorsairUnit;
            BlockPos row = helper.absolutePos(new BlockPos(0, 2, 2 + i * 5));
            atk.moveTo(row.getX() + 0.5, gy + (flyer ? 8 : 0), row.getZ() + 0.5, -90, 0);
            foe.moveTo(row.getX() + reach[i], gy, row.getZ() + 0.5, 90, 0);
            ((com.solegendary.reignofnether.unit.interfaces.Unit) atk).setOwnerName(owner);
            foe.setOwnerName(foeOwner);
            if (!com.solegendary.reignofnether.tide.TidesServerEvents.isTidal(atk))
                setup.add(net.minecraft.world.entity.EntityType.getKey(reg.get(i)) + " is not tidal");
            if (!flyer && !atk.canBreatheUnderwater())
                setup.add(net.minecraft.world.entity.EntityType.getKey(reg.get(i)) + " can drown");
            level.addFreshEntity(atk);
            level.addFreshEntity(foe);
            attackers.add(atk);
            foes.add(foe);
        }
        helper.runAfterDelay(3, () -> {
            for (int i = 0; i < attackers.size(); i++)
                ((com.solegendary.reignofnether.unit.interfaces.AttackerUnit) attackers.get(i)).setUnitAttackTarget(foes.get(i));
        });
        boolean[] landed = new boolean[attackers.size()];
        for (int t = 4; t < 400; t++)
            helper.runAfterDelay(t, () -> {
                for (int i = 0; i < attackers.size(); i++)
                    if (!foes.get(i).isAlive() || foes.get(i).getHealth() < foes.get(i).getMaxHealth())
                        landed[i] = true;
            });
        helper.runAfterDelay(400, () -> {
            List<String> problems = new ArrayList<>(setup);
            for (int i = 0; i < attackers.size(); i++)
                if (!landed[i])
                    problems.add(net.minecraft.world.entity.EntityType.getKey(reg.get(i)).getPath() + " never hit its target");
            for (var e : attackers) e.discard();
            for (var e : foes) e.discard();
            if (!problems.isEmpty())
                helper.fail(String.join("; ", problems));
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ Tidewrought slice 4 (T3)

    /**
     * Tidewrought T3: the Wreck Harbour offers Tier 3; the Abyssal Shipyard is a T3 lab that the server refuses from a
     * Shipwright alone and accepts from a Harbourmaster; it raises the Leviathan only once its owner has Tier 3; the
     * bot kit techs to it; and the Leviathan costs the standard experimental price, dies with the T3 death blast and
     * carries Tidal Slam.
     */
    @GameTest(template = ARENA)
    public static void tidewrought_t3_lab_and_tier_3(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_tide_t3_" + helper.absolutePos(BlockPos.ZERO).asLong();
        var lab = Buildings.ABYSSAL_SHIPYARD;
        var levItem = com.solegendary.reignofnether.building.production.ProductionItems.LEVIATHAN;
        if (!Buildings.WRECK_HARBOUR.productions.get().contains(com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_3))
            helper.fail("the Wreck Harbour does not offer Tier 3");
        if (!Factions.TIDEWROUGHT.equals(lab.getFaction()))
            helper.fail("the Abyssal Shipyard is not a Tidewrought building");
        if (!com.solegendary.reignofnether.unit.T2Workers.isT3Lab(lab))
            helper.fail("the Abyssal Shipyard is not registered as a T3 lab");
        if (!lab.productions.get().contains(levItem))
            helper.fail("the Abyssal Shipyard does not raise the Leviathan");
        var kit = com.solegendary.reignofnether.bot.BotPlayer.kitFor(Factions.TIDEWROUGHT);
        if (kit == null || kit.t3Building() != lab || kit.t3Unit() != levItem)
            helper.fail("the Tidewrought bot kit does not tech to the Abyssal Shipyard and the Leviathan");
        var levCost = com.solegendary.reignofnether.resources.ResourceCosts.LEVIATHAN;
        var wtwCost = com.solegendary.reignofnether.resources.ResourceCosts.WORLD_TREE_WALKER;
        if (levCost.metal() != wtwCost.metal() || levCost.energy() != wtwCost.energy() || levCost.population != wtwCost.population)
            helper.fail("the Leviathan does not cost the standard experimental price: " + levCost.metal() + "m vs " + wtwCost.metal() + "m");
        if (com.solegendary.reignofnether.barfx.BarFx.tierOf(levCost.metal()) != 3)
            helper.fail("the Leviathan would not die with the T3 death blast");

        BlockPos base = helper.absolutePos(new BlockPos(10, 2, 10));
        var ship = com.solegendary.reignofnether.registrars.EntityRegistrar.SHIPWRIGHT_UNIT.get().create(level);
        var hm = com.solegendary.reignofnether.registrars.EntityRegistrar.HARBOURMASTER_UNIT.get().create(level);
        var lev = com.solegendary.reignofnether.registrars.EntityRegistrar.LEVIATHAN_UNIT.get().create(level);
        if (ship == null || hm == null || lev == null) {
            helper.fail("could not create the units");
            return;
        }
        ship.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);
        hm.moveTo(base.getX() + 2.5, base.getY(), base.getZ() + 0.5, 0, 0);
        level.addFreshEntity(ship);
        level.addFreshEntity(hm);
        com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(owner);
        try {
            if (!Factions.TIDEWROUGHT.equals(Factions.getFaction(lev)))
                helper.fail("the Leviathan is not Tidewrought");
            if (lev.getAbilities().get().stream().noneMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.TidalSlam))
                helper.fail("the Leviathan has no Tidal Slam");
            if (lev.getMaxHealth() < 1000)
                helper.fail("Leviathan health " + lev.getMaxHealth());
            if (com.solegendary.reignofnether.unit.T2Workers.buildersMayPlace(level, lab, new int[] { ship.getId() }))
                helper.fail("the Abyssal Shipyard may be placed by a Shipwright alone");
            if (!com.solegendary.reignofnether.unit.T2Workers.buildersMayPlace(level, lab, new int[] { ship.getId(), hm.getId() }))
                helper.fail("the Abyssal Shipyard refused a Harbourmaster among the builders");
            // the real placement path: a Shipwright-only Abyssal Shipyard is refused before the terrain checks
            var placed = com.solegendary.reignofnether.building.BuildingServerEvents.placeBuilding(lab, base.offset(-80, 0, -80),
                Rotation.NONE, owner, new int[] { ship.getId() }, false, false, false, true);
            if (placed != null) {
                com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(placed);
                helper.fail("placeBuilding accepted an Abyssal Shipyard from a Shipwright");
            }
            // the produce gate on a placement that is never put in the world (invisible to parallel tests)
            var bp = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(lab, level,
                helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, owner, false);
            if (!(bp instanceof com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement pp)) {
                helper.fail("the Abyssal Shipyard is not a production placement: " + bp);
                return;
            }
            com.solegendary.reignofnether.research.ResearchServerEvents.addResearch(owner,
                com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_2);
            if (levItem.canProduce(pp))
                helper.fail("the Leviathan can be raised with only Tier 2");
            if (pp.startProductionItem(levItem) || !pp.productionQueue.isEmpty())
                helper.fail("the server queued a Leviathan without Tier 3");
            com.solegendary.reignofnether.research.ResearchServerEvents.addResearch(owner,
                com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_3);
            if (!levItem.canProduce(pp))
                helper.fail("the Leviathan still refused after Tier 3: " + levItem.getProduceErrorMsg(pp));
            helper.succeed();
        } finally {
            com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(owner);
            ship.discard();
            hm.discard();
            lev.discard();
        }
    }

    /**
     * The Leviathan is made, is a tidal Tidewrought unit that paths as TIDAL (its WaterAnimal body must not read
     * AQUATIC), announces its arrival, and its claws land on an enemy in reach. On its own sky pad, so no neighbouring
     * test's unit is in its aggro range.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void tidewrought_leviathan_is_made_and_attacks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_leviathan_atk_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        BlockPos pad = skyPad(helper, 240);
        var lev = com.solegendary.reignofnether.registrars.EntityRegistrar.LEVIATHAN_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (lev == null || foe == null) {
            helper.fail("could not create the Leviathan or its foe");
            return;
        }
        lev.moveTo(pad.getX() + 4.5, pad.getY(), pad.getZ() + 8.5, -90, 0);
        foe.moveTo(pad.getX() + 10.5, pad.getY(), pad.getZ() + 8.5, 90, 0);
        lev.setOwnerName(owner);
        foe.setOwnerName(foeOwner);
        List<String> setup = new ArrayList<>();
        if (!com.solegendary.reignofnether.tide.TidesServerEvents.isTidal(lev))
            setup.add("the Leviathan is not tidal");
        if (com.solegendary.reignofnether.unit.pathfinding.MobilityClass.of(lev) != com.solegendary.reignofnether.unit.pathfinding.MobilityClass.TIDAL)
            setup.add("the Leviathan paths as " + com.solegendary.reignofnether.unit.pathfinding.MobilityClass.of(lev) + ", not TIDAL");
        if (!lev.canBreatheUnderwater())
            setup.add("the Leviathan can drown");
        if (lev.getBbWidth() < 3f)
            setup.add("the Leviathan's hitbox is only " + lev.getBbWidth() + " wide");
        level.addFreshEntity(lev);
        level.addFreshEntity(foe);
        helper.runAfterDelay(3, () -> lev.setUnitAttackTarget(foe));
        boolean[] landed = { false };
        for (int t = 4; t < 300; t++)
            helper.runAfterDelay(t, () -> {
                if (!foe.isAlive() || foe.getHealth() < foe.getMaxHealth())
                    landed[0] = true;
            });
        helper.runAfterDelay(300, () -> {
            List<String> problems = new ArrayList<>(setup);
            if (!landed[0])
                problems.add("the Leviathan never hit its target: " + unitState(level, lev));
            if (!lev.getPersistentData().getBoolean("bt_t3_announced"))
                problems.add("the Leviathan's arrival was not announced");
            lev.discard();
            foe.discard();
            if (!problems.isEmpty())
                helper.fail(String.join("; ", problems));
            helper.succeed();
        });
    }

    /**
     * The Leviathan's wake: after walking 3+ blocks it lays a trail pool behind its body (owned by its player, marked
     * as its trail); the registry keeps at most 6 trail pools per Leviathan, evicting its oldest; and its trail never
     * evicts the owner's normal pool. The Leviathan is never added to the level - the trail only needs its id.
     */
    @GameTest(template = ARENA)
    public static void tidewrought_leviathan_trail_pools_are_capped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_leviathan_trail_" + helper.absolutePos(BlockPos.ZERO).asLong();
        openGround(helper, 1, 1, 14, 14);
        var lev = com.solegendary.reignofnether.registrars.EntityRegistrar.LEVIATHAN_UNIT.get().create(level);
        if (lev == null) {
            helper.fail("could not create the Leviathan");
            return;
        }
        lev.setOwnerName(owner);
        try {
            BlockPos start = helper.absolutePos(new BlockPos(5, 2, 13));
            lev.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, -90, 0);
            if (lev.trail(level) != null)
                helper.fail("the first trail pass laid a pool before the Leviathan moved");
            lev.moveTo(start.getX() + 1.5, start.getY(), start.getZ() + 0.5, -90, 0);
            if (lev.trail(level) != null)
                helper.fail("a 1-block step laid a trail pool");
            lev.moveTo(start.getX() + 4.5, start.getY(), start.getZ() + 0.5, -90, 0);
            var wake = lev.trail(level);
            if (wake == null) {
                helper.fail("a 4-block walk laid no trail pool: " + com.solegendary.reignofnether.tide.TidepoolServerEvents.lastRefusal());
                return;
            }
            if (!wake.isTrail() || wake.trailSource != lev.getId() || !owner.equals(wake.owner))
                helper.fail("the wake pool is not the Leviathan's trail");
            if (wake.centre.getX() + 0.5 >= lev.getX() - lev.getBbWidth() / 2)
                helper.fail("the wake pool is under the Leviathan, not behind it: pool x " + wake.centre.getX() + ", Leviathan x " + lev.getX());
            if (wake.ticksLeft() > com.solegendary.reignofnether.unit.units.tide.LeviathanUnit.TRAIL_LIFETIME_TICKS)
                helper.fail("the wake lasts " + wake.ticksLeft() + " ticks");
            com.solegendary.reignofnether.tide.TidepoolServerEvents.clearOwner(owner);

            // a normal pool of the same owner, then 8 trail pools on fresh ground: 6 survive, the normal pool too
            var normal = com.solegendary.reignofnether.tide.TidepoolServerEvents.raise(level, owner,
                helper.absolutePos(new BlockPos(12, 2, 12)), 1, 600);
            if (normal == null) {
                helper.fail("could not raise the normal pool: " + com.solegendary.reignofnether.tide.TidepoolServerEvents.lastRefusal());
                return;
            }
            int[][] spots = { {3, 3}, {3, 7}, {3, 11}, {7, 3}, {7, 7}, {7, 11}, {11, 3}, {11, 7} };
            var first = lev.trailAt(level, helper.absolutePos(new BlockPos(spots[0][0], 2, spots[0][1])));
            for (int i = 1; i < spots.length; i++)
                if (lev.trailAt(level, helper.absolutePos(new BlockPos(spots[i][0], 2, spots[i][1]))) == null)
                    helper.fail("trail pool " + i + " was refused: " + com.solegendary.reignofnether.tide.TidepoolServerEvents.lastRefusal());
            int trails = 0;
            for (var p : com.solegendary.reignofnether.tide.TidepoolServerEvents.poolsOf(owner))
                if (p.isTrail() && p.trailSource == lev.getId())
                    trails++;
            if (trails != com.solegendary.reignofnether.tide.TidepoolServerEvents.TRAIL_POOLS_PER_SOURCE)
                helper.fail("the Leviathan has " + trails + " trail pools, the cap is "
                    + com.solegendary.reignofnether.tide.TidepoolServerEvents.TRAIL_POOLS_PER_SOURCE);
            if (first == null || first.isActive())
                helper.fail("the oldest trail pool was not evicted at the cap");
            if (!normal.isActive())
                helper.fail("the Leviathan's trail evicted its owner's normal pool");
            helper.succeed();
        } finally {
            com.solegendary.reignofnether.tide.TidepoolServerEvents.clearOwner(owner);
            lev.discard();
        }
    }

    /**
     * Tidal Slam crashes on the enemies in the arc in front of the Leviathan - damage and a throw away from it - and
     * leaves the friend in the arc and the enemy behind it (inside the radius, outside the arc) alone.
     */
    @GameTest(template = ARENA)
    public static void tidewrought_tidal_slam_hits_foes_not_friends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_tidal_slam_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        openGround(helper, 0, 0, 15, 15);
        var lev = com.solegendary.reignofnether.registrars.EntityRegistrar.LEVIATHAN_UNIT.get().create(level);
        var vind = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get();
        var friend = vind.create(level);
        var foe = vind.create(level);
        var behind = vind.create(level);
        if (lev == null || friend == null || foe == null || behind == null) {
            helper.fail("could not create the units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(7, 2, 7));
        lev.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, -90, 0);
        foe.moveTo(at.getX() + 5.5, at.getY(), at.getZ() + 0.5, 90, 0);
        friend.moveTo(at.getX() + 4.5, at.getY(), at.getZ() + 3.5, 90, 0);
        behind.moveTo(at.getX() - 5.5, at.getY(), at.getZ() + 0.5, -90, 0);
        lev.setOwnerName(owner);
        friend.setOwnerName(owner);
        foe.setOwnerName(foeOwner);
        behind.setOwnerName(foeOwner);
        List<net.minecraft.world.entity.Mob> all = List.of(lev, friend, foe, behind);
        for (var e : all)
            level.addFreshEntity(e);
        helper.runAfterDelay(2, () -> {
            try {
                float friendHp = friend.getHealth(), foeHp = foe.getHealth(), behindHp = behind.getHealth();
                var hit = com.solegendary.reignofnether.ability.abilities.TidalSlam.slam(level, lev, owner, lev.position(),
                    new net.minecraft.world.phys.Vec3(1, 0, 0));
                if (!hit.contains(foe) || (foe.isAlive() && foe.getHealth() >= foeHp))
                    helper.fail("Tidal Slam did not hurt the enemy in its arc");
                if (foe.isAlive() && foe.getDeltaMovement().x <= 0)
                    helper.fail("Tidal Slam did not throw the enemy away from the Leviathan: " + foe.getDeltaMovement());
                if (hit.contains(friend) || friend.getHealth() < friendHp)
                    helper.fail("Tidal Slam touched a friendly unit");
                if (hit.contains(behind) || behind.getHealth() < behindHp)
                    helper.fail("Tidal Slam hit the enemy behind the Leviathan, outside its arc");
                if (hit.contains(lev))
                    helper.fail("Tidal Slam hit the Leviathan itself");
                var slam = lev.getAbilities().get().stream()
                    .filter(a -> a instanceof com.solegendary.reignofnether.ability.abilities.TidalSlam).findFirst().orElse(null);
                if (slam == null || slam.cooldownMax != com.solegendary.reignofnether.ability.abilities.TidalSlam.CD_SECONDS * 20)
                    helper.fail("the Leviathan's Tidal Slam is missing or its cooldown is not "
                        + com.solegendary.reignofnether.ability.abilities.TidalSlam.CD_SECONDS + " s");
                helper.succeed();
            } finally {
                for (var e : all)
                    e.discard();
            }
        });
    }

    /**
     * The Leviathan rides a WaterAnimal body: standing on dry land for 3 s costs it no air and no health; drowning,
     * drying-out and in-wall damage never land; and its weakness holds - a siege attacker (an Iron Golem's building
     * bonus) and an explosion count as artillery/siege, and the golem's blow hits ~1.75x as hard as a plain soldier's.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void tidewrought_leviathan_never_drowns_on_land_and_fears_siege(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_leviathan_dry_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlockPos pad = skyPad(helper, 225);   // its own pad: no neighbouring test's unit may hurt it while it stands
        var lev = com.solegendary.reignofnether.registrars.EntityRegistrar.LEVIATHAN_UNIT.get().create(level);
        var golem = com.solegendary.reignofnether.registrars.EntityRegistrar.IRON_GOLEM_UNIT.get().create(level);
        var soldier = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (lev == null || golem == null || soldier == null) {
            helper.fail("could not create the units");
            return;
        }
        lev.moveTo(pad.getX() + 8.5, pad.getY(), pad.getZ() + 8.5, 0, 0);
        lev.setOwnerName(owner);
        golem.setOwnerName(owner + "_foe");
        soldier.setOwnerName(owner + "_foe");
        level.addFreshEntity(lev);
        int air0 = lev.getAirSupply();
        helper.runAfterDelay(60, () -> {
            try {
                var src = level.damageSources();
                if (lev.getHealth() < lev.getMaxHealth())
                    helper.fail("the Leviathan lost health standing on dry land: " + lev.getHealth() + " / " + lev.getMaxHealth()
                        + " (last hurt by " + lev.getLastDamageSource() + ")");
                if (lev.getAirSupply() < air0)
                    helper.fail("the Leviathan ran out of air on dry land: " + lev.getAirSupply() + " of " + air0);
                for (var s : List.of(src.drown(), src.dryOut(), src.inWall())) {
                    lev.invulnerableTime = 0;
                    float hp = lev.getHealth();
                    if (lev.hurt(s, 10f) || lev.getHealth() < hp)
                        helper.fail("the Leviathan took " + s.getMsgId() + " damage");
                }
                if (!com.solegendary.reignofnether.unit.units.tide.LeviathanUnit.isSiegeHit(src.mobAttack(golem))
                        || !com.solegendary.reignofnether.unit.units.tide.LeviathanUnit.isSiegeHit(src.explosion(null, null)))
                    helper.fail("a siege golem's blow or an explosion does not count as siege");
                if (com.solegendary.reignofnether.unit.units.tide.LeviathanUnit.isSiegeHit(src.mobAttack(soldier))
                        || com.solegendary.reignofnether.unit.units.tide.LeviathanUnit.isSiegeHit(src.generic()))
                    helper.fail("a plain soldier's blow counts as siege");
                lev.invulnerableTime = 0;
                float hp0 = lev.getHealth();
                lev.hurt(src.mobAttack(soldier), 10f);
                float plain = hp0 - lev.getHealth();
                lev.invulnerableTime = 0;
                float hp1 = lev.getHealth();
                lev.hurt(src.mobAttack(golem), 10f);
                float siege = hp1 - lev.getHealth();
                if (plain <= 0)
                    helper.fail("the plain blow did not land: " + plain);
                // the shell's armour soaks a slightly bigger share of a bigger blow, so the health lost is a bit under
                // the full 1.75x (CI: 7.8 vs 4.68 = 1.67x); without the weakness it would be 1x
                else if (siege < plain * 1.5f)
                    helper.fail("the siege blow dealt " + siege + ", the plain one " + plain + " - siege should deal ~"
                        + com.solegendary.reignofnether.unit.units.tide.LeviathanUnit.SIEGE_MULTIPLIER + "x");
                helper.succeed();
            } finally {
                lev.discard();
                golem.discard();
                soldier.discard();
            }
        });
    }

    /**
     * A 5x body leaving a lab: the one-cell spawn rule would stand the 3.5-wide Leviathan beside a wall with half its
     * body inside it, so ProductionPlacement.safeSpawnPoint (given the entity type) moves the spawn to where the whole
     * body fits; and a Leviathan that does end up encased, with an order, is freed by UnitUnstick within a few seconds.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void tidewrought_leviathan_spawns_clear_and_is_unstuck(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_leviathan_free_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlockPos pad = skyPad(helper, 250);   // standing height 251
        var type = com.solegendary.reignofnether.registrars.EntityRegistrar.LEVIATHAN_UNIT.get();
        // a wall five high along x = 9, z 4..12 (too tall to be stood on by either search): a spawn cell at x = 8 is
        // standable, but a 3.5-wide body there is not
        for (int z = 4; z <= 12; z++)
            for (int y = 0; y < 5; y++)
                level.setBlock(pad.offset(9, y, z), Blocks.GLASS.defaultBlockState(), 2);
        BlockPos byWall = pad.offset(8, 0, 8);
        BlockPos safe = com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement.safeSpawnPoint(level, byWall, type);
        if (com.solegendary.reignofnether.unit.UnitUnstick.bodyFits(level, byWall, type.getWidth(), type.getHeight()))
            helper.fail("the test wall does not block a Leviathan beside it");
        if (!com.solegendary.reignofnether.unit.UnitUnstick.bodyFits(level, safe, type.getWidth(), type.getHeight())
                || !com.solegendary.reignofnether.unit.UnitUnstick.isStandable(level, safe))
            helper.fail("safeSpawnPoint put the Leviathan where its body does not fit: " + safe.subtract(pad).toShortString());
        if (safe.getY() != byWall.getY())
            helper.fail("safeSpawnPoint moved the Leviathan off the floor: " + safe.subtract(pad).toShortString());

        var lev = type.create(level);
        if (lev == null) {
            helper.fail("could not create the Leviathan");
            return;
        }
        // encased: centred a block past the wall's far face, so the glass runs through its body
        lev.moveTo(pad.getX() + 9.5 + 1.0, pad.getY(), pad.getZ() + 8.5, 0, 0);
        lev.setOwnerName(owner);
        level.addFreshEntity(lev);
        if (!com.solegendary.reignofnether.unit.UnitUnstick.isEncased(lev)) {
            String s = unitState(level, lev);
            lev.discard();
            helper.fail("the Leviathan placed into the wall is not encased - the test setup is wrong: " + s);
            return;
        }
        lev.getMoveGoal().setMoveTarget(pad.offset(13, -1, 2));
        final int last = 160;
        boolean[] done = { false };
        for (int t = 1; t <= last; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (done[0])
                    return;
                if (lev.isAlive() && !com.solegendary.reignofnether.unit.UnitUnstick.isEncased(lev)) {
                    done[0] = true;
                    lev.discard();
                    helper.succeed();
                    return;
                }
                if (tick < last)
                    return;
                done[0] = true;
                String s = unitState(level, lev);
                lev.discard();
                helper.fail("the encased Leviathan is still stuck after 8 s: " + s);
            });
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // lovish: "my workers got stuck while playing verdant". The Heartwood Hall spawned every Seedshaper inside its 3x3
    // oak trunk (the default indoor spawn point is the footprint's centre), and the Circle of Elders / Heart of the
    // Wild spawned theirs on the moonwell cauldron, which they dropped into and couldn't step out of. These tests stamp
    // each finished Verdant structure on open ground and check that nothing it produces, builds next to it or is caught
    // inside it stays trapped. Each runs on a glass pad in its own arena column at its own height in the 120-190 band
    // no other test uses, and every unit belongs to one owner, so the units of these tests never fight each other.

    private static final String FREE_OWNER = "gametest_verdant_free";

    private static String unitState(ServerLevel level, net.minecraft.world.entity.Mob m) {
        var u = (com.solegendary.reignofnether.unit.interfaces.Unit) m;
        return describe(m) + String.format(" at %.1f/%.1f/%.1f", m.getX(), m.getY(), m.getZ())
            + (m.isAlive() ? "" : " dead")
            + " encased " + com.solegendary.reignofnether.unit.UnitUnstick.isEncased(m)
            + " standable " + com.solegendary.reignofnether.unit.UnitUnstick.isOnStandableGround(m)
            + " target " + (u.getMoveGoal() == null ? null : u.getMoveGoal().getMoveTarget())
            + " navDone " + m.getNavigation().isDone() + nextNode(level, m);
    }

    /** ", next node x/y/z (block there, block below)" of the unit's current path, for a failure message. */
    private static String nextNode(ServerLevel level, net.minecraft.world.entity.Mob m) {
        var path = m.getNavigation().getPath();
        if (path == null || path.isDone())
            return "";
        BlockPos n = path.getNextNodePos();
        return ", next node " + n.toShortString() + " (" + level.getBlockState(n) + " on " + level.getBlockState(n.below())
            + ", rts " + com.solegendary.reignofnether.unit.UnitServerEvents.rtsPathfinding + ")";
    }

    /**
     * Stamps {@code building} (finished, Rotation.NONE) one block in from the arena corner on a glass pad at padY, with
     * a Seedshaper standing where a solid block of it goes (built over) and one at its edge (a builder), produces one
     * {@code produced} there if it trains units (spawn must be standable and clear), then orders them all to the far
     * corner of the column (outside the footprint, 8-12 blocks off) and expects every one to arrive.
     */
    private static void verdantStructureFreesUnits(GameTestHelper helper, Building building, int padY,
            @javax.annotation.Nullable net.minecraft.world.entity.EntityType<? extends com.solegendary.reignofnether.unit.interfaces.Unit> produced) {
        ServerLevel level = helper.getLevel();
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        int cx = corner.getX(), cz = corner.getZ();
        var bp = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(building, level,
            new BlockPos(cx + 1, padY, cz + 1), Rotation.NONE, FREE_OWNER, false);
        if (bp == null) {
            helper.fail("could not make a " + building.name + " placement");
            return;
        }
        int y0 = bp.minCorner.getY();
        if (bp.minCorner.getX() < cx || bp.maxCorner.getX() > cx + 15 || bp.minCorner.getZ() < cz || bp.maxCorner.getZ() > cz + 15) {
            helper.fail(building.name + "'s footprint " + bp.minCorner + ".." + bp.maxCorner + " does not fit the arena column");
            return;
        }
        // the ground: glass over the whole column level with the structure's floor, open sky above
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++) {
                level.setBlock(new BlockPos(cx + dx, y0 - 1, cz + dz), Blocks.GLASS.defaultBlockState(), 2);
                level.setBlock(new BlockPos(cx + dx, y0, cz + dz), Blocks.GLASS.defaultBlockState(), 2);
                for (int y = y0 + 1; y <= y0 + 22; y++) {
                    BlockPos p = new BlockPos(cx + dx, y, cz + dz);
                    if (!level.getBlockState(p).isAir())
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        // a solid block of the structure at standing height with a solid one above it (a trunk): the encased unit's spot
        java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> states = new java.util.HashMap<>();
        for (var b : bp.getBlocks())
            states.put(b.getBlockPos(), b.getBlockState());
        BlockPos encasedAt = null;
        for (var e : states.entrySet()) {
            BlockPos p = e.getKey();
            var above = states.get(p.above());
            if (p.getY() == y0 + 1 && e.getValue().isCollisionShapeFullBlock(level, p)
                    && above != null && above.isCollisionShapeFullBlock(level, p.above())) {
                encasedAt = p;
                break;
            }
        }
        List<net.minecraft.world.entity.Mob> units = new ArrayList<>();
        var builder = com.solegendary.reignofnether.registrars.EntityRegistrar.SEEDSHAPER_UNIT.get().create(level);
        if (builder == null) {
            helper.fail("could not create a Seedshaper");
            return;
        }
        int midZ = (bp.minCorner.getZ() + bp.maxCorner.getZ()) / 2;
        builder.moveTo(cx + 0.5, y0 + 1, midZ + 0.5, 0, 0);   // just outside the footprint's -x edge, in build range
        units.add(builder);
        if (encasedAt != null) {
            var trapped = com.solegendary.reignofnether.registrars.EntityRegistrar.SEEDSHAPER_UNIT.get().create(level);
            if (trapped != null) {
                trapped.moveTo(encasedAt.getX() + 0.5, encasedAt.getY(), encasedAt.getZ() + 0.5, 0, 0);
                units.add(trapped);
            }
        }
        for (var m : units) {
            ((com.solegendary.reignofnether.unit.interfaces.Unit) m).setOwnerName(FREE_OWNER);
            level.addFreshEntity(m);
        }
        // the finished structure, stamped over the units (flag 2: no neighbour updates, so nothing pops off mid-stamp)
        for (var e : states.entrySet())
            level.setBlock(e.getKey(), e.getValue(), 2);

        String spawnProblem = null;
        if (produced != null) {
            if (!(bp instanceof com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement pp)) {
                helper.fail(building.name + " is not a production placement");
                for (var m : units) m.discard();
                return;
            }
            var e = pp.produceUnit(level, produced, FREE_OWNER, true);
            if (!(e instanceof net.minecraft.world.entity.Mob m)) {
                helper.fail(building.name + " produced nothing");
                for (var u : units) u.discard();
                return;
            }
            units.add(m);
            if (com.solegendary.reignofnether.unit.UnitUnstick.isEncased(m)
                    || !com.solegendary.reignofnether.unit.UnitUnstick.isOnStandableGround(m))
                spawnProblem = building.name + " spawned its unit where it can't stand: " + unitState(level, m);
        }
        if (spawnProblem != null) {
            for (var m : units) m.discard();
            helper.fail(spawnProblem);
            return;
        }
        // the far corner on the -x side: outside every footprint (they start at x+1), across the building from the trunk
        BlockPos target = new BlockPos(cx, y0, cz + 15);
        final BlockPos trappedAt = encasedAt;
        boolean[] done = { false };
        final int last = 600;
        for (int t = 1; t <= last; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (done[0])
                    return;
                // ordered at 40 and again at 60 ticks: after the produced unit's own delayed rally order has fired
                if (tick == 40 || tick == 60)
                    for (var m : units)
                        ((com.solegendary.reignofnether.unit.interfaces.Unit) m).getMoveGoal().setMoveTarget(target);
                boolean all = tick > 40;
                for (var m : units) {
                    double dx = m.getX() - (target.getX() + 0.5), dz = m.getZ() - (target.getZ() + 0.5);
                    all &= m.isAlive() && dx * dx + dz * dz <= 2.0 * 2.0;
                }
                if (all) {
                    done[0] = true;
                    for (var m : units) m.discard();
                    helper.succeed();
                    return;
                }
                if (tick < last)
                    return;
                done[0] = true;
                List<String> states2 = new ArrayList<>();
                for (var m : units)
                    states2.add(unitState(level, m));
                for (var m : units) m.discard();
                helper.fail("not every unit got out of the " + building.name + " to " + target.toShortString()
                    + " (encased spot " + trappedAt + "): " + String.join("; ", states2));
            });
        }
    }

    @GameTest(template = ARENA, timeoutTicks = 700)
    public static void heartwood_hall_frees_its_seedshapers(GameTestHelper helper) {
        verdantStructureFreesUnits(helper, Buildings.HEARTWOOD_HALL, 125,
            com.solegendary.reignofnether.registrars.EntityRegistrar.SEEDSHAPER_UNIT.get());
    }

    @GameTest(template = ARENA, timeoutTicks = 700)
    public static void grove_frees_its_units(GameTestHelper helper) {
        verdantStructureFreesUnits(helper, Buildings.GROVE, 135,
            com.solegendary.reignofnether.registrars.EntityRegistrar.LEAFBLADE_UNIT.get());
    }

    @GameTest(template = ARENA, timeoutTicks = 700)
    public static void circle_of_elders_frees_its_elder_druids(GameTestHelper helper) {
        verdantStructureFreesUnits(helper, Buildings.CIRCLE_OF_ELDERS, 145,
            com.solegendary.reignofnether.registrars.EntityRegistrar.ELDER_DRUID_UNIT.get());
    }

    @GameTest(template = ARENA, timeoutTicks = 700)
    public static void heart_of_the_wild_frees_its_units(GameTestHelper helper) {
        verdantStructureFreesUnits(helper, Buildings.HEART_OF_THE_WILD, 155,
            com.solegendary.reignofnether.registrars.EntityRegistrar.SEEDSHAPER_UNIT.get());
    }

    @GameTest(template = ARENA, timeoutTicks = 700)
    public static void storm_oak_frees_its_builders(GameTestHelper helper) {
        verdantStructureFreesUnits(helper, Buildings.STORM_OAK, 165, null);
    }

    @GameTest(template = ARENA, timeoutTicks = 700)
    public static void verdant_economy_buildings_free_their_builders(GameTestHelper helper) {
        // the extractor is the biggest of the Court's economy buildings (root claws round a living-wood core); the wind
        // generator and converter use the same pad, roots and rules
        verdantStructureFreesUnits(helper, Buildings.METAL_EXTRACTOR_VERDANT, 175, null);
    }

    /** A unit caught inside solid blocks with a move order is put back on open ground within a few seconds. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void encased_unit_with_an_order_is_unstuck(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = skyPad(helper, 185);
        var seed = com.solegendary.reignofnether.registrars.EntityRegistrar.SEEDSHAPER_UNIT.get().create(level);
        if (seed == null) {
            helper.fail("could not create a Seedshaper");
            return;
        }
        BlockPos at = pad.offset(8, 0, 8);
        seed.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        seed.setOwnerName(FREE_OWNER);
        level.addFreshEntity(seed);
        level.setBlock(at, Blocks.OAK_LOG.defaultBlockState(), 2);
        level.setBlock(at.above(), Blocks.OAK_LOG.defaultBlockState(), 2);
        seed.getMoveGoal().setMoveTarget(pad.offset(2, -1, 2));
        // two strikes 2 s apart: freed within 4 s of being encased, 7 s allowed
        final int last = 140;
        boolean[] done = { false };
        for (int t = 1; t <= last; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (done[0])
                    return;
                if (seed.isAlive() && !com.solegendary.reignofnether.unit.UnitUnstick.isEncased(seed)
                        && com.solegendary.reignofnether.unit.UnitUnstick.isOnStandableGround(seed)) {
                    done[0] = true;
                    seed.discard();
                    helper.succeed();
                    return;
                }
                if (tick < last)
                    return;
                done[0] = true;
                String s = unitState(level, seed);
                seed.discard();
                helper.fail("the encased Seedshaper is still stuck after 7 s: " + s);
            });
        }
    }

    /** A worker ordered to a site it can't reach (walled in) drops the order and goes idle instead of standing forever. */
    @GameTest(template = ARENA, timeoutTicks = com.solegendary.reignofnether.unit.goals.BuildRepairGoal.GIVE_UP_TICKS + 200)
    public static void build_order_to_an_unreachable_site_ends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = skyPad(helper, 195);   // standing height 196
        String owner = "gametest_unreachable_site";
        var site = com.solegendary.reignofnether.building.BuildingServerEvents.placeBuilding(
            Buildings.WIND_GENERATOR_VERDANT, pad.offset(11, -1, 11), Rotation.NONE, owner, new int[0], false, false, true, true);
        var worker = com.solegendary.reignofnether.registrars.EntityRegistrar.SEEDSHAPER_UNIT.get().create(level);
        if (site == null || worker == null) {
            helper.fail("could not set up the site (" + site + ") or the worker (" + worker + ")");
            return;
        }
        // a glass yard round the worker: walls two high and a roof, far (7+ blocks) from the site. 5x5 inside, more
        // cells than UnitUnstick.POCKET_MAX_CELLS: an enclosure, not a pocket the unstick would lift it out of
        BlockPos at = pad.offset(3, 0, 3);
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) {
                level.setBlock(at.offset(dx, 2, dz), Blocks.GLASS.defaultBlockState(), 2);
                if (Math.abs(dx) == 3 || Math.abs(dz) == 3) {
                    level.setBlock(at.offset(dx, 0, dz), Blocks.GLASS.defaultBlockState(), 2);
                    level.setBlock(at.offset(dx, 1, dz), Blocks.GLASS.defaultBlockState(), 2);
                }
            }
        worker.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        worker.setOwnerName(owner);
        level.addFreshEntity(worker);
        worker.getBuildRepairGoal().setBuildingTarget(site);
        if (worker.getBuildRepairGoal().getBuildingTarget() != site) {
            worker.discard();
            com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(site);
            helper.fail("the worker did not take the build order");
            return;
        }
        helper.succeedWhen(() -> {
            if (worker.getBuildRepairGoal().getBuildingTarget() != null || !com.solegendary.reignofnether.unit.interfaces.WorkerUnit.isIdle(worker))
                helper.fail("the walled-in worker still holds its build order (target "
                    + worker.getBuildRepairGoal().getBuildingTarget() + ", idle "
                    + com.solegendary.reignofnether.unit.interfaces.WorkerUnit.isIdle(worker) + ", "
                    + unitState(level, worker) + ")");
            worker.discard();
            com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(site);
        });
    }

    // ------------------------------------------------------------------ Stormcallers Mana core (mana/ManaServerEvents)
    // Every test uses its own pool under a unique owner and runs all its steps inside one call, so no economy tick (or
    // another test) lands in between; owners without an RTSPlayer get their faction through ManaServerEvents.bindTraits.

    private static com.solegendary.reignofnether.resources.Resources manaPool(String owner, float energy) {
        var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 0, 0);
        pool.addEnergy(energy);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
        return pool;
    }

    private static void dropManaPool(String owner, com.solegendary.reignofnether.resources.Resources pool) {
        com.solegendary.reignofnether.mana.ManaServerEvents.clearOwner(owner);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
        var eco = com.solegendary.reignofnether.resources.EconomyServerEvents.getEconomy(owner);
        eco.conversionCapacity = 0;
        eco.stall = 1f;
    }

    /** tryCast refuses (and pays nothing) when the pool is short, pays exactly the cost when it can, power 1. */
    @GameTest(template = ARENA)
    public static void mana_cast_pays_energy_and_refuses_when_short(GameTestHelper helper) {
        String owner = "gametest_mana_cast";
        var pool = manaPool(owner, 100);
        com.solegendary.reignofnether.mana.ManaServerEvents.bindTraits(owner, FactionTraits.STORMCALLERS);
        try {
            float refused = com.solegendary.reignofnether.mana.ManaServerEvents.tryCastQuiet(null, owner, 150);
            if (refused != 0f || Math.abs(pool.getEnergy() - 100f) > 0.01f)
                helper.fail("a 150 cast on 100 energy was not refused cleanly: power " + refused + ", pool " + pool.getEnergy());
            if (com.solegendary.reignofnether.mana.ManaServerEvents.lastRefusal()
                    != com.solegendary.reignofnether.mana.ManaServerEvents.Refusal.NOT_ENOUGH_ENERGY)
                helper.fail("refusal reason was " + com.solegendary.reignofnether.mana.ManaServerEvents.lastRefusal());
            float power = com.solegendary.reignofnether.mana.ManaServerEvents.tryCastQuiet(null, owner, 60);
            if (Math.abs(power - 1f) > 0.001f || Math.abs(pool.getEnergy() - 40f) > 0.01f)
                helper.fail("a 60 cast on 100 energy: expected power 1 / 40 left, got " + power + " / " + pool.getEnergy());
            if (com.solegendary.reignofnether.mana.ManaServerEvents.tryCastQuiet(null, "gametest_mana_nobody", 1) != 0f
                    || com.solegendary.reignofnether.mana.ManaServerEvents.lastRefusal()
                    != com.solegendary.reignofnether.mana.ManaServerEvents.Refusal.NO_POOL)
                helper.fail("an owner without a pool could cast");
            // a starved caster shot pays what is there and fires at half rate, never refuses
            float paid = com.solegendary.reignofnether.mana.ManaServerEvents.channelShot(null, owner, 80);
            if (Math.abs(paid - 0.5f) > 0.01f || pool.getEnergy() > 0.01f
                    || Math.abs(com.solegendary.reignofnether.mana.ManaServerEvents.shotCooldownScale(0f) - 2f) > 0.001f)
                helper.fail("starved shot: paid fraction " + paid + ", pool " + pool.getEnergy());
        } finally {
            dropManaPool(owner, pool);
        }
        helper.succeed();
    }

    /**
     * Spells are paid before construction and count as demand: with a 600 e/s channel running, the stall drops and a
     * building site asking for 500 energy gets less than an identical site of a player without the channel. When the
     * pool is empty the channel stops itself.
     */
    @GameTest(template = ARENA)
    public static void mana_channel_is_paid_before_construction_and_stalls_it(GameTestHelper helper) {
        String owner = "gametest_mana_channel", control = "gametest_mana_control";
        var pool = manaPool(owner, 1000);
        var ctrlPool = manaPool(control, 1000);
        com.solegendary.reignofnether.mana.ManaServerEvents.bindTraits(owner, FactionTraits.STORMCALLERS);
        try {
            var eco = com.solegendary.reignofnether.resources.EconomyServerEvents.getEconomy(owner);
            var ctrlEco = com.solegendary.reignofnether.resources.EconomyServerEvents.getEconomy(control);
            var ch = com.solegendary.reignofnether.mana.ManaServerEvents.openChannel(null, owner, 600);
            if (ch == null || Math.abs(pool.getEnergy() - 400f) > 0.01f) {
                helper.fail("channel did not open paying its first second: " + pool.getEnergy());
                return;
            }
            com.solegendary.reignofnether.resources.EconomyServerEvents.updateStall(pool, eco);
            com.solegendary.reignofnether.resources.EconomyServerEvents.updateStall(ctrlPool, ctrlEco);
            if (Math.abs(eco.stall - 400f / 600f) > 0.01f)
                helper.fail("the channel's draw did not count as demand: stall " + eco.stall);
            float before = pool.getEnergy(), ctrlBefore = ctrlPool.getEnergy();
            float g = com.solegendary.reignofnether.resources.EconomyServerEvents.requestFlow(owner, 0, 1000, 0.5f);
            float cg = com.solegendary.reignofnether.resources.EconomyServerEvents.requestFlow(control, 0, 1000, 0.5f);
            float got = before - pool.getEnergy(), ctrlGot = ctrlBefore - ctrlPool.getEnergy();
            if (Math.abs(cg - 1f) > 0.001f || Math.abs(ctrlGot - 500f) > 0.5f)
                helper.fail("control site should get its full 500 energy, got " + ctrlGot);
            if (got >= ctrlGot - 100f || g > 0.7f)
                helper.fail("the site next to a channel got " + got + " energy (grant " + g + "), control " + ctrlGot);
            // next second: pays what is left, then stops at an empty pool
            float f = com.solegendary.reignofnether.mana.ManaServerEvents.channel(ch);
            if (f <= 0f || f >= 1f || !ch.isActive() || pool.getEnergy() > 0.01f)
                helper.fail("partly paid second: fraction " + f + ", active " + ch.isActive() + ", pool " + pool.getEnergy());
            float f2 = com.solegendary.reignofnether.mana.ManaServerEvents.channel(ch);
            if (f2 != 0f || ch.isActive())
                helper.fail("the channel kept running on an empty pool");
        } finally {
            dropManaPool(owner, pool);
            dropManaPool(control, ctrlPool);
        }
        helper.succeed();
    }

    /**
     * Overcharge (Stormcallers only): spells cost x2 for x1.5 power, the player's build speed is capped at 50%, their
     * converters pause (and run only above 75% once it is off), and it switches itself off at 0 energy.
     */
    @GameTest(template = ARENA)
    public static void overcharge_doubles_cost_empowers_caps_build_and_pauses_converters(GameTestHelper helper) {
        String owner = "gametest_mana_overcharge";
        var pool = manaPool(owner, 1000);
        com.solegendary.reignofnether.mana.ManaServerEvents.bindTraits(owner, FactionTraits.STORMCALLERS);
        try {
            var eco = com.solegendary.reignofnether.resources.EconomyServerEvents.getEconomy(owner);
            eco.energyStorage = 1000f;
            eco.metalStorage = 1000f;
            var ok = com.solegendary.reignofnether.mana.ManaServerEvents.Refusal.NONE;
            if (com.solegendary.reignofnether.mana.ManaServerEvents.setOvercharge(owner, true) != ok
                    || !com.solegendary.reignofnether.mana.ManaServerEvents.isOvercharged(owner)) {
                helper.fail("a Stormcaller could not overcharge");
                return;
            }
            if (com.solegendary.reignofnether.mana.ManaServerEvents.spellCostMultiplier(owner) != 2f
                    || com.solegendary.reignofnether.mana.ManaServerEvents.spellPowerMultiplier(owner) != 1.5f)
                helper.fail("overcharge multipliers wrong");
            float power = com.solegendary.reignofnether.mana.ManaServerEvents.tryCastQuiet(null, owner, 50);
            if (Math.abs(power - 1.5f) > 0.001f || Math.abs(pool.getEnergy() - 900f) > 0.01f)
                helper.fail("overcharged 50 cast: expected power 1.5 / 900 left, got " + power + " / " + pool.getEnergy());
            // build speed capped although the bank could pay everything
            com.solegendary.reignofnether.resources.EconomyServerEvents.updateStall(pool, eco);
            if (Math.abs(eco.stall - 0.5f) > 0.001f)
                helper.fail("overcharged build speed should be capped at 50%, was " + eco.stall);
            float g = com.solegendary.reignofnether.resources.EconomyServerEvents.requestFlow(owner, 0, 100, 0.1f);
            if (g > 0.5001f)
                helper.fail("an overcharged player's site was granted " + g);
            // converters pause while overcharged ...
            eco.conversionCapacity = 50f;
            float metal = pool.getMetal();
            com.solegendary.reignofnether.resources.EconomyServerEvents.convertEnergy(pool, eco);
            if (pool.getMetal() > metal)
                helper.fail("converters ran while overcharged");
            // ... and once it is off, a Stormcaller converter runs above 75% only
            com.solegendary.reignofnether.mana.ManaServerEvents.setOvercharge(owner, false);
            com.solegendary.reignofnether.resources.EconomyServerEvents.convertEnergy(pool, eco);
            if (pool.getMetal() <= metal)
                helper.fail("converter did not run at ~89% energy after overcharge was switched off");
            pool.addEnergy(700f - pool.getEnergy());
            metal = pool.getMetal();
            com.solegendary.reignofnether.resources.EconomyServerEvents.convertEnergy(pool, eco);
            if (pool.getMetal() > metal)
                helper.fail("a Stormcaller converter ran at 70% energy (threshold is 75%)");
            com.solegendary.reignofnether.resources.EconomyServerEvents.updateStall(pool, eco);
            if (eco.stall < 0.999f)
                helper.fail("build speed stayed capped after overcharge was switched off: " + eco.stall);
            // auto-off at 0 energy, and no switching it back on while broke
            com.solegendary.reignofnether.mana.ManaServerEvents.setOvercharge(owner, true);
            if (com.solegendary.reignofnether.mana.ManaServerEvents.checkAutoOff(owner, pool.getEnergy()))
                helper.fail("overcharge switched off with 700 energy left");
            pool.addEnergy(-pool.getEnergy());
            if (!com.solegendary.reignofnether.mana.ManaServerEvents.checkAutoOff(owner, pool.getEnergy())
                    || com.solegendary.reignofnether.mana.ManaServerEvents.isOvercharged(owner))
                helper.fail("overcharge did not switch itself off at 0 energy");
            if (com.solegendary.reignofnether.mana.ManaServerEvents.setOvercharge(owner, true) == ok)
                helper.fail("overcharge switched on with an empty pool");
        } finally {
            dropManaPool(owner, pool);
        }
        helper.succeed();
    }

    /**
     * Non-Stormcallers see no change: they can't overcharge, their multipliers are 1, their build speed isn't capped,
     * their converters keep the 50% threshold, and only the Stormcallers' traits carry mana.
     */
    @GameTest(template = ARENA)
    public static void mana_rules_leave_other_factions_unchanged(GameTestHelper helper) {
        String owner = "gametest_mana_sunforged";
        var pool = manaPool(owner, 600);
        com.solegendary.reignofnether.mana.ManaServerEvents.bindTraits(owner, FactionTraits.SUNFORGED);
        try {
            var eco = com.solegendary.reignofnether.resources.EconomyServerEvents.getEconomy(owner);
            eco.energyStorage = 1000f;
            eco.metalStorage = 1000f;
            if (com.solegendary.reignofnether.mana.ManaServerEvents.setOvercharge(owner, true)
                    != com.solegendary.reignofnether.mana.ManaServerEvents.Refusal.NOT_MANA
                    || com.solegendary.reignofnether.mana.ManaServerEvents.isOvercharged(owner))
                helper.fail("a Sunforged player could overcharge");
            if (com.solegendary.reignofnether.mana.ManaServerEvents.isMana(owner)
                    || com.solegendary.reignofnether.mana.ManaServerEvents.spellCostMultiplier(owner) != 1f
                    || com.solegendary.reignofnether.mana.ManaServerEvents.spellPowerMultiplier(owner) != 1f
                    || com.solegendary.reignofnether.mana.ManaServerEvents.capBuildSpeed(owner, 1f) != 1f)
                helper.fail("a Sunforged player picked up a Mana rule");
            eco.conversionCapacity = 50f;
            com.solegendary.reignofnether.resources.EconomyServerEvents.convertEnergy(pool, eco);
            if (pool.getMetal() <= 0)
                helper.fail("a Sunforged converter stopped running at 60% energy");
            for (var f : List.of(Factions.VILLAGERS, Factions.MONSTERS, Factions.PIGLINS, Factions.VERDANT_COURT,
                    Factions.TIDEWROUGHT)) {
                var t = FactionTraits.of(f);
                if (t.mana || t.converterThreshold != com.solegendary.reignofnether.resources.EconomyServerEvents.CONVERSION_THRESHOLD)
                    helper.fail(f.getName() + " picked up the Mana flag or a converter threshold change");
            }
            var storm = FactionTraits.of(Factions.STORMCALLERS);
            if (!storm.mana || Math.abs(storm.converterThreshold - 0.75f) > 0.001f)
                helper.fail("the Stormcallers lost their Mana traits");
            if (FactionTraits.NEUTRAL.mana)
                helper.fail("neutral traits carry mana");
        } finally {
            dropManaPool(owner, pool);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ Stormcallers slice 1 (playable T1)

    /**
     * The Stormcallers' start, the way a readied player start does it (PlayerServerEvents.startRTS without the player):
     * its worker type spawns, the first one becomes the Thunder Sage with Heaven's Judgement and Blink, and its capitol
     * foundation is laid at the start position and survives as a site. Also checks every "_storm" economy blueprint (and
     * the refit extractor, which no building owns) and both new buildings load on the server. Runs on its own stone
     * platform high above its own arena.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void stormcallers_capitol_places_and_the_thunder_sage_spawns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var storm = Factions.STORMCALLERS;
        String owner = "gametest_storm_start_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 225;
        int cx = base.getX(), cz = base.getZ();   // its own column (an offset column would land over another arena)
        for (int dx = -9; dx <= 9; dx++)
            for (int dz = -9; dz <= 9; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        for (String nbt : List.of("metal_extractor_storm", "metal_extractor_t2_storm", "wind_generator_storm",
                "energy_converter_storm", "storage_vault_storm", "jade_pagoda", "lotus_academy"))
            if (com.solegendary.reignofnether.building.BuildingBlockData.getBuildingNbt(nbt,
                    level.getServer().getResourceManager()) == null)
                helper.fail("the Stormcallers structure " + nbt + " is missing from data/");
        var workerType = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(storm.workerEntityType);
        net.minecraft.world.entity.Entity first = workerType == null ? null : workerType.create(level);
        if (!(first instanceof com.solegendary.reignofnether.unit.units.storm.GeomancerUnit sage)) {
            helper.fail("the Stormcallers worker type does not make a Geomancer: " + storm.workerEntityType);
            return;
        }
        sage.setOwnerName(owner);
        sage.moveTo(cx + 0.5, y + 1, cz - 7.5, 0, 0);
        com.solegendary.reignofnether.player.CommanderServerEvents.makeCommander(sage);
        level.addFreshEntity(sage);
        boolean judgement = false, blink = false;
        for (var a : sage.getAbilities().get()) {
            judgement |= a instanceof com.solegendary.reignofnether.ability.abilities.CommanderDGun
                && com.solegendary.reignofnether.ability.abilities.CommanderDGun.kindFor(sage)
                    == com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.JUDGEMENT;
            blink |= a instanceof com.solegendary.reignofnether.ability.abilities.CommanderAbility
                && com.solegendary.reignofnether.ability.abilities.CommanderAbility.kindFor(sage)
                    == com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.BLINK;
        }
        if (!judgement || !blink)
            helper.fail("the Thunder Sage lacks Heaven's Judgement (" + judgement + ") or Blink (" + blink + ")");
        if (!com.solegendary.reignofnether.player.CommanderServerEvents.isCommander(sage))
            helper.fail("the Thunder Sage is not tagged as the commander");
        Building capitol = ReignOfNetherRegistries.BUILDING.get(storm.capitolBuilding);
        var blocks = capitol.getRelativeBlockData(level);
        BlockPos origin = com.solegendary.reignofnether.player.PlayerServerEvents.getBuildingOriginPos(
            new BlockPos(cx, y, cz), blocks);
        var placement = com.solegendary.reignofnether.building.BuildingServerEvents.placeBuilding(capitol, origin,
            Rotation.NONE, owner, new int[] { sage.getId() }, false, false, true, true);
        if (placement == null) {
            sage.discard();
            helper.fail("the Jade Pagoda could not be placed at a readied start");
            return;
        }
        helper.runAfterDelay(40, () -> {
            try {
                if (placement.isDestroyedServerside
                        || !com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().contains(placement))
                    helper.fail("the Jade Pagoda site was destroyed right after being placed");
                if (!placement.getBuilding().isCapitol || placement.getBuilding() != Buildings.JADE_PAGODA)
                    helper.fail("the placed capitol is " + placement.getBuilding().name);
                helper.succeed();
            } finally {
                com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(placement);
                sage.discard();
            }
        });
    }

    /**
     * Every Stormcallers unit can be made: the five fighters (Geomancer, Blade Dancer, Fire Adept, Jade Guard, Wind
     * Monk), each ordered onto its own enemy Villager, land a blow within the window; the Paper Crane is a flying scout
     * with no attack. One row of the arena per attacker; the casters stand at range. Their owner has no energy pool, so
     * caster shots are free here (ManaServerEvents.channelShot) and Blink Strike never autocasts.
     */
    @GameTest(template = ARENA, timeoutTicks = 320)
    public static void stormcallers_units_are_made_and_attack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_storm_atk_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        List<net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob>> reg = List.of(
            com.solegendary.reignofnether.registrars.EntityRegistrar.GEOMANCER_UNIT.get(), com.solegendary.reignofnether.registrars.EntityRegistrar.BLADE_DANCER_UNIT.get(), com.solegendary.reignofnether.registrars.EntityRegistrar.FIRE_ADEPT_UNIT.get(), com.solegendary.reignofnether.registrars.EntityRegistrar.JADE_GUARD_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.WIND_MONK_UNIT.get());
        List<net.minecraft.world.entity.Mob> attackers = new ArrayList<>(), foes = new ArrayList<>();
        // the scout: made, owned, flying and unarmed - never added to the level
        var crane = com.solegendary.reignofnether.registrars.EntityRegistrar.PAPER_CRANE_UNIT.get().create(level);
        if (crane == null || !crane.isScout() || !crane.isFlying()
                || ((Object) crane) instanceof com.solegendary.reignofnether.unit.interfaces.AttackerUnit)
            helper.fail("the Paper Crane is not an unarmed flying scout");
        if (crane != null) {
            crane.setOwnerName(owner);
            if (crane.canTargetItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COD)))
                helper.fail("the Paper Crane still wants to fish items");
            crane.discard();
        }
        openGround(helper, 0, 0, 15, 15);
        for (int i = 0; i < reg.size(); i++) {
            var atk = reg.get(i).create(level);
            var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
            if (!(atk instanceof com.solegendary.reignofnether.unit.interfaces.AttackerUnit) || foe == null) {
                helper.fail("could not create " + net.minecraft.world.entity.EntityType.getKey(reg.get(i)) + " as an attacker");
                for (var e : attackers) e.discard();
                for (var e : foes) e.discard();
                return;
            }
            boolean ranged = atk instanceof com.solegendary.reignofnether.unit.units.storm.FireAdeptUnit
                || atk instanceof com.solegendary.reignofnether.unit.units.storm.WindMonkUnit;
            BlockPos row = helper.absolutePos(new BlockPos(ranged ? 1 : 4, 2, 1 + i * 3));
            atk.moveTo(row.getX() + 0.5, row.getY(), row.getZ() + 0.5, -90, 0);
            foe.moveTo(row.getX() + (ranged ? 10.5 : 2.5), row.getY(), row.getZ() + 0.5, 90, 0);
            ((com.solegendary.reignofnether.unit.interfaces.Unit) atk).setOwnerName(owner);
            foe.setOwnerName(foeOwner);
            level.addFreshEntity(atk);
            level.addFreshEntity(foe);
            attackers.add(atk);
            foes.add(foe);
        }
        helper.runAfterDelay(3, () -> {
            for (int i = 0; i < attackers.size(); i++)
                ((com.solegendary.reignofnether.unit.interfaces.AttackerUnit) attackers.get(i)).setUnitAttackTarget(foes.get(i));
        });
        boolean[] landed = new boolean[attackers.size()];
        for (int t = 4; t < 300; t++)
            helper.runAfterDelay(t, () -> {
                for (int i = 0; i < attackers.size(); i++) {
                    var atk = attackers.get(i);
                    var hit = atk.getLastHurtMob();
                    if (hit instanceof com.solegendary.reignofnether.unit.interfaces.Unit u && foeOwner.equals(u.getOwnerName()))
                        landed[i] = true;
                    if (foes.get(i).getLastHurtByMob() == atk || foes.get(i).getHealth() < foes.get(i).getMaxHealth())
                        landed[i] = true;
                }
            });
        helper.runAfterDelay(300, () -> {
            List<String> problems = new ArrayList<>();
            for (int i = 0; i < attackers.size(); i++)
                if (!landed[i])
                    problems.add(net.minecraft.world.entity.EntityType.getKey(reg.get(i)).getPath() + " never hit its target");
            for (var e : attackers) e.discard();
            for (var e : foes) e.discard();
            if (!problems.isEmpty())
                helper.fail(String.join("; ", problems));
            helper.succeed();
        });
    }

    /**
     * Heaven's Judgement: after its telegraph the column hurts the enemy standing in it (D-gun damage), spares the friend
     * in it and the enemy just outside its radius, charges 150 energy through the Mana core and cools down; nothing lands
     * before the telegraph ends. With too little energy it is refused - nothing paid, no cooldown, nothing scheduled. The
     * Thunder Sage's Blink in the same arena: it teleports to a spot in sight, pays 60, and refuses on an empty pool.
     */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void heavens_judgement_hits_foes_in_the_column_not_friends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_storm_judgement_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        openGround(helper, 0, 0, 15, 15);
        var pool = manaPool(owner, 500);
        com.solegendary.reignofnether.mana.ManaServerEvents.bindTraits(owner, FactionTraits.STORMCALLERS);
        var vill = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get();
        var sage = com.solegendary.reignofnether.registrars.EntityRegistrar.GEOMANCER_UNIT.get().create(level);
        var inside = vill.create(level);    // in the column
        var friend = vill.create(level);    // in the column, ours
        var outside = vill.create(level);   // 4.5 blocks from its centre
        var later = vill.create(level);     // waits out of reach, then stands in the column for the refused cast
        List<net.minecraft.world.entity.Mob> all = new ArrayList<>();
        for (net.minecraft.world.entity.Mob e : java.util.Arrays.<net.minecraft.world.entity.Mob>asList(sage, inside, friend, outside, later))
            if (e != null)
                all.add(e);
        if (all.size() != 5) {
            helper.fail("could not create the Judgement test units");
            for (var e : all) e.discard();
            dropManaPool(owner, pool);
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 7));
        BlockPos aim = helper.absolutePos(new BlockPos(10, 1, 7));   // the ground block the column stands on
        Runnable place = () -> {
            sage.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, -90, 0);
            inside.moveTo(aim.getX() + 0.5, aim.getY() + 1, aim.getZ() + 1.0, 0, 0);
            friend.moveTo(aim.getX() + 0.5, aim.getY() + 1, aim.getZ() - 0.5, 0, 0);
            outside.moveTo(aim.getX() + 0.5, aim.getY() + 1, aim.getZ() + 5.0, 0, 0);
            later.moveTo(aim.getX() + 0.5, aim.getY() + 1, aim.getZ() - 6.5, 0, 0);
            for (var e : all)
                e.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        };
        sage.setOwnerName(owner);
        friend.setOwnerName(owner);
        inside.setOwnerName(foeOwner);
        outside.setOwnerName(foeOwner);
        later.setOwnerName(foeOwner);
        place.run();
        for (var e : all)
            level.addFreshEntity(e);
        com.solegendary.reignofnether.player.CommanderServerEvents.makeCommander(sage);
        com.solegendary.reignofnether.ability.abilities.CommanderDGun dgun = null;
        com.solegendary.reignofnether.ability.abilities.CommanderAbility sig = null;
        for (var a : sage.getAbilities().get()) {
            if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderDGun d)
                dgun = d;
            if (a instanceof com.solegendary.reignofnether.ability.abilities.CommanderAbility c)
                sig = c;
        }
        if (dgun == null || sig == null || com.solegendary.reignofnether.ability.abilities.CommanderDGun.kindFor(sage)
                != com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.JUDGEMENT) {
            helper.fail("the Thunder Sage has no Heaven's Judgement or no signature");
            for (var e : all) e.discard();
            dropManaPool(owner, pool);
            return;
        }
        final var judgement = dgun;
        final var blink = sig;
        float[] hp = new float[4];
        float cost = com.solegendary.reignofnether.ability.abilities.CommanderDGun.ENERGY_COST;
        helper.runAfterDelay(5, () -> {
            place.run();
            hp[0] = inside.getHealth(); hp[1] = friend.getHealth(); hp[2] = outside.getHealth(); hp[3] = later.getHealth();
            judgement.use(level, sage, aim);
            if (Math.abs(pool.getEnergy() - (500 - cost)) > 0.01f)
                helper.fail("Heaven's Judgement did not charge " + cost + " energy: " + pool.getEnergy());
            if (judgement.isOffCooldown(sage))
                helper.fail("Heaven's Judgement did not go on cooldown");
        });
        // the telegraph: nobody is hurt before the bolt falls
        helper.runAfterDelay(5 + com.solegendary.reignofnether.ability.abilities.CommanderDGun.JUDGEMENT_DELAY - 3, () -> {
            if (inside.getHealth() < hp[0])
                helper.fail("Heaven's Judgement struck before its telegraph ended");
        });
        int struck = 5 + com.solegendary.reignofnether.ability.abilities.CommanderDGun.JUDGEMENT_DELAY + 6;
        helper.runAfterDelay(struck, () -> {
            if (inside.isAlive() && hp[0] - inside.getHealth() < 20)
                helper.fail("Heaven's Judgement barely hurt the enemy in its column: " + inside.getHealth() + " of " + hp[0]);
            if (friend.getHealth() < hp[1])
                helper.fail("Heaven's Judgement hurt a friendly unit");
            if (outside.getHealth() < hp[2])
                helper.fail("Heaven's Judgement hurt an enemy outside its radius");
            // refused: too little energy - nothing paid, no cooldown, nothing scheduled
            judgement.setCooldown(0, sage);
            pool.addEnergy(100f - pool.getEnergy());
            later.moveTo(aim.getX() + 0.5, aim.getY() + 1, aim.getZ() + 0.5, 0, 0);
            hp[3] = later.getHealth();
            judgement.use(level, sage, aim);
            if (Math.abs(pool.getEnergy() - 100f) > 0.01f)
                helper.fail("a refused Judgement still took energy: " + pool.getEnergy());
            if (!judgement.isOffCooldown(sage))
                helper.fail("a refused Judgement started its cooldown");
        });
        helper.runAfterDelay(struck + com.solegendary.reignofnether.ability.abilities.CommanderDGun.JUDGEMENT_DELAY + 6, () -> {
            try {
                if (later.getHealth() < hp[3])
                    helper.fail("a refused Judgement still struck");
                // Blink: 6 blocks along the open ground, paid 60
                pool.addEnergy(200f - pool.getEnergy());
                double x0 = sage.getX(), zBefore = sage.getZ();
                blink.use(level, sage, BlockPos.containing(sage.getX(), sage.getY() - 1, sage.getZ() + 6));
                if (Math.abs(sage.getZ() - zBefore - 6) > 1.0 || Math.abs(sage.getX() - x0) > 1.0)
                    helper.fail("Blink did not land on the aimed spot: " + sage.position());
                if (Math.abs(pool.getEnergy() - (200f - com.solegendary.reignofnether.ability.abilities.CommanderAbility.BLINK_ENERGY)) > 0.01f)
                    helper.fail("Blink did not charge its energy: " + pool.getEnergy());
                if (blink.isOffCooldown(sage))
                    helper.fail("Blink did not go on cooldown");
                // ... and on an empty pool it refuses and stays put
                blink.setCooldown(0, sage);
                pool.addEnergy(-pool.getEnergy());
                double z0 = sage.getZ();
                blink.use(level, sage, BlockPos.containing(sage.getX(), sage.getY() - 1, sage.getZ() - 6));
                if (Math.abs(sage.getZ() - z0) > 0.01 || !blink.isOffCooldown(sage))
                    helper.fail("Blink moved or cooled down without the energy for it");
                helper.succeed();
            } finally {
                for (var e : all)
                    e.discard();
                dropManaPool(owner, pool);
            }
        });
    }

    /**
     * Fire Adept: each fireball is a spell - with energy it pays 2 and keeps its pace; on an empty pool it still fires,
     * at half rate (its attack cooldown doubles). The fireball burns the target and never sets a fire block.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void fire_adept_fires_slower_without_energy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_storm_adept_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        openGround(helper, 0, 0, 15, 15);
        var pool = manaPool(owner, 100);
        com.solegendary.reignofnether.mana.ManaServerEvents.bindTraits(owner, FactionTraits.STORMCALLERS);
        var adept = com.solegendary.reignofnether.registrars.EntityRegistrar.FIRE_ADEPT_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        if (adept == null || foe == null) {
            helper.fail("could not create the Fire Adept test units");
            dropManaPool(owner, pool);
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 7));
        adept.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, -90, 0);
        foe.moveTo(at.getX() + 8.5, at.getY(), at.getZ() + 0.5, 90, 0);
        adept.setOwnerName(owner);
        foe.setOwnerName(foeOwner);
        level.addFreshEntity(foe);
        helper.runAfterDelay(5, () -> {
            try {
                float base = adept.getAttackCooldown();
                float hp = foe.getHealth();
                adept.fireball(foe);
                if (foe.getHealth() >= hp)
                    helper.fail("the fireball did no damage");
                if (foe.getRemainingFireTicks() <= 0)
                    helper.fail("the fireball did not set its target burning");
                if (level.getBlockState(foe.blockPosition()).is(Blocks.FIRE) || level.getBlockState(foe.blockPosition().below()).is(Blocks.FIRE))
                    helper.fail("the fireball lit a fire block");
                float paidCd = adept.getAttackCooldown();
                if (Math.abs(pool.getEnergy() - (100f - com.solegendary.reignofnether.unit.units.storm.FireAdeptUnit.ENERGY_PER_SHOT)) > 0.01f)
                    helper.fail("a fireball did not cost its energy: " + pool.getEnergy());
                if (Math.abs(paidCd - base) > 0.5f)
                    helper.fail("a paid fireball changed the pace: " + base + " -> " + paidCd);
                // starved: still fires, at half rate
                pool.addEnergy(-pool.getEnergy());
                foe.setHealth(foe.getMaxHealth());
                foe.invulnerableTime = 0;
                hp = foe.getHealth();
                adept.fireball(foe);
                float starvedCd = adept.getAttackCooldown();
                if (foe.getHealth() >= hp)
                    helper.fail("a starved Fire Adept stopped hurting");
                if (Math.abs(starvedCd - base * 2f) > 0.5f)
                    helper.fail("a starved Fire Adept should fire at half rate: cooldown " + base + " -> " + starvedCd);
                helper.succeed();
            } finally {
                foe.discard();
                adept.discard();
                dropManaPool(owner, pool);
            }
        });
    }

    /**
     * Jade Guard: a projectile from an enemy shooter is cut by 40% and 30% of it flies back at the shooter; a friendly
     * shooter's projectile is never reflected onto it; melee passes the mirror untouched.
     */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void jade_guard_reflects_projectiles_to_the_shooter_not_friends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_storm_guard_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        openGround(helper, 0, 0, 15, 15);
        var guard = com.solegendary.reignofnether.registrars.EntityRegistrar.JADE_GUARD_UNIT.get().create(level);
        var shooter = com.solegendary.reignofnether.registrars.EntityRegistrar.FIRE_ADEPT_UNIT.get().create(level);      // the enemy archer
        var friendly = com.solegendary.reignofnether.registrars.EntityRegistrar.FIRE_ADEPT_UNIT.get().create(level);     // ours
        List<net.minecraft.world.entity.Mob> all = new ArrayList<>();
        for (net.minecraft.world.entity.Mob e : java.util.Arrays.<net.minecraft.world.entity.Mob>asList(guard, shooter, friendly))
            if (e != null)
                all.add(e);
        if (all.size() != 3) {
            helper.fail("could not create the Jade Guard test units");
            for (var e : all) e.discard();
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(7, 2, 7));
        guard.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        shooter.moveTo(at.getX() + 6.5, at.getY(), at.getZ() + 0.5, 90, 0);
        friendly.moveTo(at.getX() - 5.5, at.getY(), at.getZ() + 0.5, -90, 0);
        guard.setOwnerName(owner);
        friendly.setOwnerName(owner);
        shooter.setOwnerName(foeOwner);
        // only the Guard joins the level: the two shooters' AI must not fight between the measured shots
        level.addFreshEntity(guard);
        float dmg = com.solegendary.reignofnether.unit.units.storm.FireAdeptUnit.attackDamage;
        helper.runAfterDelay(5, () -> {
            guard.invulnerableTime = 0;
            float g0 = guard.getHealth(), s0 = shooter.getHealth();
            shooter.fireball(guard);
            float taken = g0 - guard.getHealth(), back = s0 - shooter.getHealth();
            float wantTaken = dmg * (1 - com.solegendary.reignofnether.unit.units.storm.JadeGuardUnit.MIRROR_CUT);
            float wantBack = dmg * com.solegendary.reignofnether.unit.units.storm.JadeGuardUnit.MIRROR_REFLECT;
            if (taken <= 0 || Math.abs(taken - wantTaken) > 0.6f)
                helper.fail("the Jade Guard took " + taken + " from a " + dmg + " projectile (expected ~" + wantTaken + ")");
            if (Math.abs(back - wantBack) > 0.6f)
                helper.fail("the shooter took " + back + " back from the mirror (expected ~" + wantBack + ")");
            guard.clearFire();
        });
        helper.runAfterDelay(30, () -> {
            try {
                guard.clearFire();
                guard.invulnerableTime = 0;
                float f0 = friendly.getHealth();
                friendly.fireball(guard);
                if (friendly.getHealth() < f0)
                    helper.fail("the Jade Guard reflected a friendly projectile onto its own side");
                // melee is not mirrored
                guard.clearFire();
                guard.invulnerableTime = 0;
                float g0 = guard.getHealth();
                float s0 = shooter.getHealth();
                guard.hurt(level.damageSources().mobAttack(shooter), 4f);
                if (guard.getHealth() >= g0)
                    helper.fail("the melee test hit did not land");
                if (shooter.getHealth() < s0)
                    helper.fail("the Jade Guard mirrored a melee blow");
                helper.succeed();
            } finally {
                for (var e : all)
                    e.discard();
            }
        });
    }

    /**
     * Blade Dancer: Blink Strike's autocast picks the enemy archer 3-9 blocks away (not the closer melee unit), lands
     * beside it, makes it the Dancer's target, pays 25 quietly and cools down; with only the reserve left it holds.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void blade_dancer_blinks_onto_an_enemy_archer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_storm_dancer_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        openGround(helper, 0, 0, 15, 15);
        var pool = manaPool(owner, 500);
        com.solegendary.reignofnether.mana.ManaServerEvents.bindTraits(owner, FactionTraits.STORMCALLERS);
        var dancer = com.solegendary.reignofnether.registrars.EntityRegistrar.BLADE_DANCER_UNIT.get().create(level);
        var archer = com.solegendary.reignofnether.registrars.EntityRegistrar.FIRE_ADEPT_UNIT.get().create(level);
        var brawler = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        List<net.minecraft.world.entity.Mob> all = new ArrayList<>();
        for (net.minecraft.world.entity.Mob e : java.util.Arrays.<net.minecraft.world.entity.Mob>asList(dancer, archer, brawler))
            if (e != null)
                all.add(e);
        if (all.size() != 3) {
            helper.fail("could not create the Blade Dancer test units");
            for (var e : all) e.discard();
            dropManaPool(owner, pool);
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 7));
        dancer.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, -90, 0);
        archer.moveTo(at.getX() + 7.5, at.getY(), at.getZ() + 0.5, 90, 0);
        brawler.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 4.5, 0, 0);
        dancer.setOwnerName(owner);
        archer.setOwnerName(foeOwner);
        brawler.setOwnerName(foeOwner);
        // the Dancer stays out of the level so its own tick can't autocast before the test does
        level.addFreshEntity(archer);
        level.addFreshEntity(brawler);
        helper.runAfterDelay(5, () -> {
            try {
                var strike = dancer.getBlinkStrike();
                if (strike == null) {
                    helper.fail("the Blade Dancer has no Blink Strike");
                    return;
                }
                if (!strike.autocast(level, dancer, dancer, new ArrayList<>()))
                    helper.fail("Blink Strike did not autocast onto the archer");
                if (dancer.distanceTo(archer) > 2.6f)
                    helper.fail("the Dancer landed " + dancer.distanceTo(archer) + " blocks from the archer");
                if (dancer.getTargetGoal().getTarget() != archer)
                    helper.fail("the Dancer did not take the archer as its target");
                if (Math.abs(pool.getEnergy() - (500f - com.solegendary.reignofnether.ability.abilities.BlinkStrike.ENERGY_COST)) > 0.01f)
                    helper.fail("Blink Strike did not charge its energy: " + pool.getEnergy());
                if (strike.isOffCooldown(dancer))
                    helper.fail("Blink Strike did not go on cooldown");
                // the reserve: off cooldown again, but only the Thunder Sage's reserve left - it holds
                strike.setCooldown(0, dancer);
                pool.addEnergy(com.solegendary.reignofnether.ability.abilities.BlinkStrike.AUTOCAST_RESERVE - pool.getEnergy());
                dancer.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, -90, 0);
                if (strike.autocast(level, dancer, dancer, new ArrayList<>()))
                    helper.fail("Blink Strike autocast into the commander's energy reserve");
                helper.succeed();
            } finally {
                for (var e : all)
                    e.discard();
                dropManaPool(owner, pool);
            }
        });
    }

    // ------------------------------------------------------------------ Stormcallers slice 2

    /**
     * Seal: a sealed enemy Frost Adept's Frost Field order (the UnitActionItem path players and allies use) is refused -
     * no field, no energy spent - while our own Adept's identical order goes through; a sealed Blade Dancer's Blink Strike
     * autocast holds; the seal charges 25, cools down, and lifts after its 6 s; a second Seal is refused on a friend and
     * on a unit standing in a friendly Ward.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void seal_blocks_an_enemys_abilities_for_its_duration_not_a_friends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_storm_seal_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        openGround(helper, 0, 0, 15, 15);
        var pool = manaPool(owner, 500);
        var foePool = manaPool(foeOwner, 500);
        com.solegendary.reignofnether.mana.ManaServerEvents.bindTraits(owner, FactionTraits.STORMCALLERS);
        com.solegendary.reignofnether.mana.ManaServerEvents.bindTraits(foeOwner, FactionTraits.STORMCALLERS);
        var scribe = com.solegendary.reignofnether.registrars.EntityRegistrar.INK_SCRIBE_UNIT.get().create(level);
        var foeAdept = com.solegendary.reignofnether.registrars.EntityRegistrar.FROST_ADEPT_UNIT.get().create(level);
        var ourAdept = com.solegendary.reignofnether.registrars.EntityRegistrar.FROST_ADEPT_UNIT.get().create(level);
        var foeDancer = com.solegendary.reignofnether.registrars.EntityRegistrar.BLADE_DANCER_UNIT.get().create(level);
        var bait = com.solegendary.reignofnether.registrars.EntityRegistrar.FIRE_ADEPT_UNIT.get().create(level);   // the Dancer's archer
        List<net.minecraft.world.entity.Mob> all = new ArrayList<>();
        for (net.minecraft.world.entity.Mob e : java.util.Arrays.<net.minecraft.world.entity.Mob>asList(scribe, foeAdept, ourAdept, foeDancer, bait))
            if (e != null)
                all.add(e);
        Runnable cleanup = () -> {
            for (var e : all) e.discard();
            com.solegendary.reignofnether.mana.FrostServerEvents.clearOwner(owner);
            com.solegendary.reignofnether.mana.FrostServerEvents.clearOwner(foeOwner);
            dropManaPool(owner, pool);
            dropManaPool(foeOwner, foePool);
        };
        if (all.size() != 5) {
            helper.fail("could not create the Seal test units");
            cleanup.run();
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 7));
        scribe.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, -90, 0);
        foeAdept.moveTo(at.getX() + 6.5, at.getY(), at.getZ() + 0.5, 90, 0);
        ourAdept.moveTo(at.getX() + 0.5, at.getY(), at.getZ() - 5.5, 0, 0);
        foeDancer.moveTo(at.getX() + 6.5, at.getY(), at.getZ() + 5.5, 0, 0);
        bait.moveTo(at.getX() + 12.5, at.getY(), at.getZ() + 5.5, 0, 0);
        scribe.setOwnerName(owner);
        ourAdept.setOwnerName(owner);
        bait.setOwnerName(owner);
        foeAdept.setOwnerName(foeOwner);
        foeDancer.setOwnerName(foeOwner);
        // the two Adepts join the level (UnitActionItem looks its units up there); the Scribe, Dancer and bait stay out
        level.addFreshEntity(foeAdept);
        level.addFreshEntity(ourAdept);
        BlockPos foeAim = helper.absolutePos(new BlockPos(10, 1, 7)), ourAim = helper.absolutePos(new BlockPos(5, 1, 1));
        helper.runAfterDelay(5, () -> {
            com.solegendary.reignofnether.ability.abilities.Seal seal = null;
            for (var a : scribe.getAbilities().get())
                if (a instanceof com.solegendary.reignofnether.ability.abilities.Seal sl)
                    seal = sl;
            if (seal == null) {
                helper.fail("the Ink Scribe has no Seal");
                cleanup.run();
                return;
            }
            seal.use(level, scribe, foeAdept);
            if (!com.solegendary.reignofnether.mana.SealServerEvents.isSealed(foeAdept))
                helper.fail("Seal did not seal the enemy Adept");
            if (Math.abs(pool.getEnergy() - (500f - com.solegendary.reignofnether.ability.abilities.Seal.ENERGY_COST)) > 0.01f)
                helper.fail("Seal did not charge its energy: " + pool.getEnergy());
            if (seal.isOffCooldown(scribe))
                helper.fail("Seal did not go on cooldown");
            // the sealed Adept's Frost Field order is refused; ours goes through
            new com.solegendary.reignofnether.unit.UnitActionItem(foeOwner, com.solegendary.reignofnether.unit.UnitAction.FROST_FIELD,
                -1, new int[] { foeAdept.getId() }, foeAim, foeAim).action(level);
            if (com.solegendary.reignofnether.mana.FrostServerEvents.fieldCount(foeOwner) != 0 || Math.abs(foePool.getEnergy() - 500f) > 0.01f)
                helper.fail("a sealed Adept still cast Frost Field (fields " + com.solegendary.reignofnether.mana.FrostServerEvents.fieldCount(foeOwner)
                    + ", energy " + foePool.getEnergy() + ")");
            new com.solegendary.reignofnether.unit.UnitActionItem(owner, com.solegendary.reignofnether.unit.UnitAction.FROST_FIELD,
                -1, new int[] { ourAdept.getId() }, ourAim, ourAim).action(level);
            if (com.solegendary.reignofnether.mana.FrostServerEvents.fieldCount(owner) != 1)
                helper.fail("an unsealed friend's Frost Field order did not go through: " + com.solegendary.reignofnether.mana.FrostServerEvents.fieldCount(owner));
            // a sealed Dancer's Blink Strike autocast holds (the bait archer is 6 blocks off, in its reach)
            com.solegendary.reignofnether.mana.SealServerEvents.seal(level, foeDancer, 1f);
            if (foeDancer.getBlinkStrike() == null || foeDancer.getBlinkStrike().autocast(level, foeDancer, foeDancer, new ArrayList<>()))
                helper.fail("a sealed Blade Dancer still autocast Blink Strike");
            // never a friend, never a warded unit
            seal.setCooldown(0, scribe);
            seal.use(level, scribe, ourAdept);
            if (com.solegendary.reignofnether.mana.SealServerEvents.isSealed(ourAdept) || !seal.isOffCooldown(scribe))
                helper.fail("Seal landed on a friend or cooled down for it");
            com.solegendary.reignofnether.mana.SealServerEvents.unseal(foeDancer);
            var ward = com.solegendary.reignofnether.mana.WardServerEvents.raise(level, foeAdept, foeOwner, foeDancer.position());
            if (ward == null || com.solegendary.reignofnether.ability.abilities.Seal.cast(level, scribe, owner, foeDancer, true))
                helper.fail("Seal landed on an enemy standing in its own side's Ward");
            if (ward != null)
                com.solegendary.reignofnether.mana.WardServerEvents.dispel(ward);
        });
        helper.runAfterDelay(5 + com.solegendary.reignofnether.mana.SealServerEvents.DURATION_TICKS + 10, () -> {
            try {
                if (com.solegendary.reignofnether.mana.SealServerEvents.isSealed(foeAdept))
                    helper.fail("the seal did not lift after its duration");
                else
                    helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    /**
     * Frost Field: the Adept freezes a disc of frost-sheet cells (paying 50), the slow pass slows the enemy standing on
     * it and not the friend beside it, a fourth field evicts the owner's oldest (cap 3), and an expired field melts back
     * to air - no permanent terrain change.
     */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void frost_field_slows_foes_not_friends_and_melts_away(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_storm_frost_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        openGround(helper, 0, 0, 15, 15);
        var pool = manaPool(owner, 500);
        com.solegendary.reignofnether.mana.ManaServerEvents.bindTraits(owner, FactionTraits.STORMCALLERS);
        var adept = com.solegendary.reignofnether.registrars.EntityRegistrar.FROST_ADEPT_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        var friend = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        if (adept == null || foe == null || friend == null) {
            helper.fail("could not create the Frost Field test units");
            dropManaPool(owner, pool);
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 7));
        BlockPos aim = helper.absolutePos(new BlockPos(8, 1, 7));   // the ground block the field is centred on
        adept.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, -90, 0);
        foe.moveTo(aim.getX() + 0.5, aim.getY() + 1, aim.getZ() + 0.5, 0, 0);
        friend.moveTo(aim.getX() + 1.5, aim.getY() + 1, aim.getZ() + 0.5, 0, 0);
        adept.setOwnerName(owner);
        friend.setOwnerName(owner);
        foe.setOwnerName(foeOwner);
        level.addFreshEntity(foe);
        level.addFreshEntity(friend);
        var block = com.solegendary.reignofnether.registrars.BlockRegistrar.FROST_SHEET.get();
        com.solegendary.reignofnether.mana.FrostServerEvents.Field[] shortLived = new com.solegendary.reignofnether.mana.FrostServerEvents.Field[1];
        helper.runAfterDelay(5, () -> {
            var field = com.solegendary.reignofnether.ability.abilities.FrostField.cast(level, adept, owner, aim, true);
            if (field == null || field.cellCount() < 5) {
                helper.fail("Frost Field laid no sheet: " + com.solegendary.reignofnether.mana.FrostServerEvents.lastRefusal());
                return;
            }
            if (Math.abs(pool.getEnergy() - (500f - com.solegendary.reignofnether.ability.abilities.FrostField.ENERGY_COST)) > 0.01f)
                helper.fail("Frost Field did not charge its energy: " + pool.getEnergy());
            if (!level.getBlockState(aim.above()).is(block))
                helper.fail("the field's centre cell is not a frost sheet");
            foe.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            friend.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            com.solegendary.reignofnether.mana.FrostServerEvents.slowPass(level);
            if (!foe.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN))
                helper.fail("the enemy on the ice was not slowed");
            if (friend.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN))
                helper.fail("the friend on the ice was slowed");
            // the cap: three fields live at most, the oldest evicted
            for (int i = 0; i < 3; i++)
                com.solegendary.reignofnether.mana.FrostServerEvents.raise(level, owner, helper.absolutePos(new BlockPos(3 + i * 4, 2, 2)), 0, 400);
            if (com.solegendary.reignofnether.mana.FrostServerEvents.fieldCount(owner) != com.solegendary.reignofnether.mana.FrostServerEvents.FIELDS_PER_PLAYER)
                helper.fail("the per-player field cap did not hold: " + com.solegendary.reignofnether.mana.FrostServerEvents.fieldCount(owner));
            if (field.isActive() || level.getBlockState(aim.above()).is(block))
                helper.fail("the evicted field did not melt");
            // a short-lived field for the expiry check
            com.solegendary.reignofnether.mana.FrostServerEvents.clearOwner(owner);
            shortLived[0] = com.solegendary.reignofnether.mana.FrostServerEvents.raise(level, owner, aim.above(), 1, 20);
            if (shortLived[0] == null)
                helper.fail("the short-lived field was refused");
        });
        helper.runAfterDelay(40, () -> {
            try {
                com.solegendary.reignofnether.mana.FrostServerEvents.tickExpiry(level);
                if (shortLived[0] != null && (shortLived[0].isActive() || level.getBlockState(aim.above()).is(block)))
                    helper.fail("an expired field did not melt back to air");
                else if (com.solegendary.reignofnether.mana.FrostServerEvents.fieldCount(owner) != 0)
                    helper.fail("fields remain after expiry: " + com.solegendary.reignofnether.mana.FrostServerEvents.fieldCount(owner));
                else
                    helper.succeed();
            } finally {
                adept.discard();
                foe.discard();
                friend.discard();
                com.solegendary.reignofnether.mana.FrostServerEvents.clearOwner(owner);
                dropManaPool(owner, pool);
            }
        });
    }

    /**
     * Rocket Battery: a volley at a ground spot 10 blocks away (Attack Ground) lands on the enemy standing there within
     * the flight time, costs no energy, and a spot inside the dead zone is refused.
     */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void rocket_battery_volley_hits_its_ground_target(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_storm_rocket_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        openGround(helper, 0, 0, 15, 15);
        var pool = manaPool(owner, 100);
        com.solegendary.reignofnether.mana.ManaServerEvents.bindTraits(owner, FactionTraits.STORMCALLERS);
        var battery = com.solegendary.reignofnether.registrars.EntityRegistrar.ROCKET_BATTERY_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        if (battery == null || foe == null) {
            helper.fail("could not create the Rocket Battery test units");
            dropManaPool(owner, pool);
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 7));
        BlockPos aim = helper.absolutePos(new BlockPos(12, 1, 7));
        battery.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, -90, 0);
        foe.moveTo(aim.getX() + 0.5, aim.getY() + 1, aim.getZ() + 0.5, 90, 0);
        battery.setOwnerName(owner);
        foe.setOwnerName(foeOwner);
        level.addFreshEntity(battery);
        level.addFreshEntity(foe);
        float[] hp = new float[1];
        helper.runAfterDelay(5, () -> {
            hp[0] = foe.getHealth();
            if (battery.fireVolley(at.getX() + 3.5, at.getY(), at.getZ() + 0.5))
                helper.fail("the battery fired inside its dead zone");
            if (!battery.fireVolley(aim.getX() + 0.5, aim.getY() + 1, aim.getZ() + 0.5))
                helper.fail("the battery refused a volley at 10 blocks");
            if (Math.abs(pool.getEnergy() - 100f) > 0.01f)
                helper.fail("a rocket volley cost energy: " + pool.getEnergy());
        });
        for (int t = 6; t < 100; t++)
            helper.runAfterDelay(t, () -> {
                foe.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);   // it stays on the aimed spot
                foe.moveTo(aim.getX() + 0.5, aim.getY() + 1, aim.getZ() + 0.5, 90, 0);
            });
        helper.runAfterDelay(100, () -> {
            try {
                if (foe.isAlive() && foe.getHealth() >= hp[0])
                    helper.fail("no rocket of the volley hurt the enemy on the aimed spot");
                else
                    helper.succeed();
            } finally {
                battery.discard();
                foe.discard();
                dropManaPool(owner, pool);
            }
        });
    }

    /**
     * Ward: the Thunder Sage's sigil cuts an enemy projectile to a friend inside by 30% and not one to an enemy inside,
     * pays its first second at the cast and one more each second after, and collapses within a second once the pool
     * can't pay at all.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void ward_softens_projectiles_on_friends_inside_and_drains_energy_each_second(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_storm_ward_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        openGround(helper, 0, 0, 15, 15);
        var pool = manaPool(owner, 500);
        com.solegendary.reignofnether.mana.ManaServerEvents.bindTraits(owner, FactionTraits.STORMCALLERS);
        var sage = com.solegendary.reignofnether.registrars.EntityRegistrar.GEOMANCER_UNIT.get().create(level);
        var friend = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        var foeInside = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        var shooter = com.solegendary.reignofnether.registrars.EntityRegistrar.FIRE_ADEPT_UNIT.get().create(level);      // the enemy archer
        var ourShooter = com.solegendary.reignofnether.registrars.EntityRegistrar.FIRE_ADEPT_UNIT.get().create(level);   // ours, for the foe inside
        List<net.minecraft.world.entity.Mob> all = new ArrayList<>();
        for (net.minecraft.world.entity.Mob e : java.util.Arrays.<net.minecraft.world.entity.Mob>asList(sage, friend, foeInside, shooter, ourShooter))
            if (e != null)
                all.add(e);
        if (all.size() != 5) {
            helper.fail("could not create the Ward test units");
            for (var e : all) e.discard();
            dropManaPool(owner, pool);
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(7, 2, 7));
        sage.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        friend.moveTo(at.getX() + 2.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foeInside.moveTo(at.getX() - 1.5, at.getY(), at.getZ() + 0.5, 0, 0);
        shooter.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 7.5, 0, 0);
        ourShooter.moveTo(at.getX() + 0.5, at.getY(), at.getZ() - 6.5, 0, 0);
        sage.setOwnerName(owner);
        friend.setOwnerName(owner);
        ourShooter.setOwnerName(owner);
        foeInside.setOwnerName(foeOwner);
        shooter.setOwnerName(foeOwner);
        // only the Sage joins the level: the others' AI must not fight between the measured shots
        level.addFreshEntity(sage);
        com.solegendary.reignofnether.player.CommanderServerEvents.makeCommander(sage);
        com.solegendary.reignofnether.mana.WardServerEvents.Ward[] ward = new com.solegendary.reignofnether.mana.WardServerEvents.Ward[1];
        float dmg = com.solegendary.reignofnether.unit.units.storm.FireAdeptUnit.attackDamage;
        float perSecond = com.solegendary.reignofnether.mana.WardServerEvents.ENERGY_PER_SECOND;
        helper.runAfterDelay(5, () -> {
            com.solegendary.reignofnether.ability.abilities.Ward w = null;
            for (var a : sage.getAbilities().get())
                if (a instanceof com.solegendary.reignofnether.ability.abilities.Ward ww)
                    w = ww;
            if (w == null) {
                helper.fail("the Thunder Sage has no Ward");
                return;
            }
            w.use(level, sage, (BlockPos) null);
            ward[0] = com.solegendary.reignofnether.mana.WardServerEvents.wards().isEmpty() ? null
                : com.solegendary.reignofnether.mana.WardServerEvents.wards().get(com.solegendary.reignofnether.mana.WardServerEvents.wards().size() - 1);
            if (ward[0] == null || ward[0].caster != sage) {
                helper.fail("Ward raised nothing");
                return;
            }
            if (Math.abs(pool.getEnergy() - (500f - perSecond)) > 0.01f)
                helper.fail("Ward did not pay its first second: " + pool.getEnergy());
            if (w.isOffCooldown(sage))
                helper.fail("Ward did not go on cooldown");
            friend.invulnerableTime = 0;
            float f0 = friend.getHealth();
            shooter.fireball(friend);
            float taken = f0 - friend.getHealth();
            if (taken <= 0 || Math.abs(taken - dmg * (1 - com.solegendary.reignofnether.mana.WardServerEvents.REDUCTION)) > 0.6f)
                helper.fail("a friend inside the ward took " + taken + " of a " + dmg + " projectile");
            foeInside.invulnerableTime = 0;
            float e0 = foeInside.getHealth();
            ourShooter.fireball(foeInside);
            float foeTaken = e0 - foeInside.getHealth();
            if (foeTaken <= 0 || Math.abs(foeTaken - dmg) > 0.6f)
                helper.fail("an enemy inside the ward took " + foeTaken + " of a " + dmg + " projectile (the ward is not its)");
            friend.clearFire();
            foeInside.clearFire();
        });
        helper.runAfterDelay(5 + 25, () -> {
            // one second in: a second drain was paid by the ward's tick (our Fire Adept's one fireball paid its 2 too)
            float shot = com.solegendary.reignofnether.unit.units.storm.FireAdeptUnit.ENERGY_PER_SHOT;
            if (Math.abs(pool.getEnergy() - (500f - 2 * perSecond - shot)) > 0.01f)
                helper.fail("Ward did not drain a second after its first: " + pool.getEnergy());
            pool.addEnergy(-pool.getEnergy());   // starved
        });
        helper.runAfterDelay(5 + 25 + 25, () -> {
            try {
                if (ward[0] != null && ward[0].isActive())
                    helper.fail("a starved Ward kept standing");
                else
                    helper.succeed();
            } finally {
                if (ward[0] != null)
                    com.solegendary.reignofnether.mana.WardServerEvents.dispel(ward[0]);
                for (var e : all)
                    e.discard();
                dropManaPool(owner, pool);
            }
        });
    }

    /**
     * Ley Link: five finished Wind-Chime Towers in a row on their own platform; linking them into one network raises
     * each tower's income by 10% per other tower in it, capped at +30% (the fifth is one too many to count), the once-a
     * -second income pass sees it, a building with three links takes no fourth (LINK_CAP), a pair too far apart is
     * refused (LINK_INVALID), and a tower that falls takes its links with it.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void ley_link_raises_linked_wind_income_and_respects_caps(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_storm_ley_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 235;
        int cx = base.getX(), cz = base.getZ();   // its own column, high over its own arena
        for (int dx = -20; dx <= 20; dx++)
            for (int dz = -7; dz <= 7; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        var pool = manaPool(owner, 100);
        com.solegendary.reignofnether.mana.ManaServerEvents.bindTraits(owner, FactionTraits.STORMCALLERS);
        List<com.solegendary.reignofnether.building.BuildingPlacement> winds = new ArrayList<>();
        Runnable cleanup = () -> {
            for (var w : winds)
                com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(w);
            dropManaPool(owner, pool);
        };
        // five towers 6 blocks apart: neighbours are 6 apart, the ends 24 (over MAX_DISTANCE 16)
        for (int i = 0; i < 5; i++) {
            var w = com.solegendary.reignofnether.building.BuildingServerEvents.placeBuilding(Buildings.WIND_GENERATOR_STORM,
                new BlockPos(cx - 14 + i * 6, y, cz - 2), Rotation.NONE, owner, new int[0], false, false, true, true);
            if (w == null) {
                helper.fail("wind generator " + i + " could not be placed");
                cleanup.run();
                return;
            }
            w.isBuilt = true;
            winds.add(w);
        }
        helper.runAfterDelay(5, () -> {
            try {
                var a = winds.get(0); var b = winds.get(1); var c = winds.get(2); var d = winds.get(3); var e = winds.get(4);
                float base1 = a.getEnergyIncome();
                if (base1 <= 0) {
                    helper.fail("a built wind generator has no income");
                    return;
                }
                if (com.solegendary.reignofnether.mana.LeyNetwork.link(owner, a.centrePos, true, e.centrePos, true)
                        != com.solegendary.reignofnether.mana.ManaServerEvents.Refusal.LINK_INVALID)
                    helper.fail("a link between towers 24 blocks apart was accepted");
                if (!com.solegendary.reignofnether.ability.abilities.LeyLink.link(level, owner, a, b, true))
                    helper.fail("the first link was refused");
                if (Math.abs(a.getEnergyIncome() - base1 * 1.1f) > 0.01f || Math.abs(b.getEnergyIncome() - base1 * 1.1f) > 0.01f)
                    helper.fail("a pair of linked towers should earn +10% each: " + a.getEnergyIncome() + " / " + b.getEnergyIncome());
                com.solegendary.reignofnether.ability.abilities.LeyLink.link(level, owner, b, c, true);
                com.solegendary.reignofnether.ability.abilities.LeyLink.link(level, owner, c, d, true);
                if (Math.abs(a.getEnergyIncome() - base1 * 1.3f) > 0.01f)
                    helper.fail("a tower with three others in its network should earn +30%: " + a.getEnergyIncome());
                com.solegendary.reignofnether.ability.abilities.LeyLink.link(level, owner, d, e, true);
                if (Math.abs(a.getEnergyIncome() - base1 * 1.3f) > 0.01f)
                    helper.fail("the resonance cap did not hold at four others: " + a.getEnergyIncome());
                // the income pass sees it: the owner's income is the five towers' boosted incomes
                com.solegendary.reignofnether.resources.EconomyServerEvents.recalculateIncomeAndStorage();
                float want = 0;
                for (var w : winds)
                    want += w.getEnergyIncome();
                float got = com.solegendary.reignofnether.resources.EconomyServerEvents.getEconomy(owner).energyIncome;
                if (Math.abs(got - want) > 0.01f || want <= base1 * 5)
                    helper.fail("the income pass did not pick up the resonance: " + got + " vs " + want);
                // per-building cap: c has b and d; a third link (a-c, 12 apart) fits, a fourth (c-e, 12) does not
                if (!com.solegendary.reignofnether.ability.abilities.LeyLink.link(level, owner, a, c, true))
                    helper.fail("a third link on a tower was refused");
                if (com.solegendary.reignofnether.mana.LeyNetwork.link(owner, c.centrePos, true, e.centrePos, true)
                        != com.solegendary.reignofnether.mana.ManaServerEvents.Refusal.LINK_CAP)
                    helper.fail("a fourth link on one tower was accepted");
                // linking a linked pair again unlinks it
                com.solegendary.reignofnether.ability.abilities.LeyLink.link(level, owner, a, c, true);
                if (com.solegendary.reignofnether.mana.LeyNetwork.isLinked(owner, a.centrePos, c.centrePos))
                    helper.fail("re-linking a linked pair did not unlink it");
                // a tower that falls takes its links with it: b dies, a is alone again
                com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(b);
                b.destroy(level);
                if (com.solegendary.reignofnether.mana.LeyNetwork.linksOf(owner, b.centrePos) != 0 || Math.abs(a.getEnergyIncome() - base1) > 0.01f)
                    helper.fail("a destroyed tower kept its links: a earns " + a.getEnergyIncome() + " (base " + base1 + ")");
                helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    // ------------------------------------------------------------------ Stormcallers slice 3 (T2: the Celestial Observatory)

    /**
     * Stormcallers T2 (slice 3): the Celestial Observatory trains the Ley Master and the T2 units and refuses all of them
     * until its owner has Tier 2 - checked on the server when production starts, not only by the build button. The bot
     * kit points at it, and its T2 army is trained there.
     */
    @GameTest(template = ARENA)
    public static void celestial_observatory_trains_only_after_tier_2(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_storm_t2_" + helper.absolutePos(BlockPos.ZERO).asLong();
        var items = List.<com.solegendary.reignofnether.building.production.ProductionItem>of(
            com.solegendary.reignofnether.building.production.ProductionItems.LEY_MASTER,
            com.solegendary.reignofnether.building.production.ProductionItems.THUNDER_DRAGON,
            com.solegendary.reignofnether.building.production.ProductionItems.EARTHSHAKER,
            com.solegendary.reignofnether.building.production.ProductionItems.GRAVITY_MONK,
            com.solegendary.reignofnether.building.production.ProductionItems.MIRROR_MAGE);
        var lab = Buildings.CELESTIAL_OBSERVATORY;
        if (!Factions.STORMCALLERS.equals(lab.getFaction()))
            helper.fail("the Celestial Observatory is not a Stormcallers building");
        for (var item : items)
            if (!lab.productions.get().contains(item))
                helper.fail("the Observatory does not train " + item.getItemName());
        var kit = com.solegendary.reignofnether.bot.BotPlayer.kitFor(Factions.STORMCALLERS);
        if (kit == null || kit.t2Lab() != lab || kit.t2Worker() != com.solegendary.reignofnether.building.production.ProductionItems.LEY_MASTER)
            helper.fail("the Stormcallers bot kit does not use the Observatory and the Ley Master");
        else
            for (var item : kit.t2Army())
                if (!lab.productions.get().contains(item))
                    helper.fail("the Stormcallers bot's T2 army has " + item.getItemName() + ", which the Observatory does not train");
        if (kit != null && (kit.t2Army().isEmpty() || !kit.t2Army().contains(com.solegendary.reignofnether.building.production.ProductionItems.GRAVITY_MONK)))
            helper.fail("the Stormcallers bot's T2 army has no Gravity Monk");
        // a placement that is never put in the world: enough for the produce gate, and invisible to parallel tests
        var bp = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(lab, level,
            helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, owner, false);
        if (!(bp instanceof com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement pp)) {
            helper.fail("the Observatory is not a production placement: " + bp);
            return;
        }
        com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(owner);
        try {
            for (var item : items)
                if (item.canProduce(pp))
                    helper.fail(item.getItemName() + " can be trained without Tier 2");
            if (pp.startProductionItem(com.solegendary.reignofnether.building.production.ProductionItems.THUNDER_DRAGON) || !pp.productionQueue.isEmpty())
                helper.fail("the server queued a Thunder Dragon without Tier 2");
            com.solegendary.reignofnether.research.ResearchServerEvents.addResearch(owner,
                com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_2);
            for (var item : items)
                if (!item.canProduce(pp))
                    helper.fail(item.getItemName() + " still refused after Tier 2: " + item.getProduceErrorMsg(pp));
            helper.succeed();
        } finally {
            com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(owner);
        }
    }

    /**
     * The Ley Master is the Stormcallers T2 constructor: double build power and health, a T2 worker (a plain Geomancer is
     * not), with Mana Well (which a Geomancer must not get). A well costs 80, boosts the power and cuts the cost of a
     * spell cast inside its 10 blocks (not one cast outside), a fifth well is refused at the cap (nothing paid), and a
     * cast on an own well takes it down.
     */
    @GameTest(template = ARENA)
    public static void ley_master_is_a_t2_worker_and_its_well_empowers_spells_in_radius(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_ley_master_" + helper.absolutePos(BlockPos.ZERO).asLong();
        openGround(helper, 0, 0, 15, 15);
        var pool = manaPool(owner, 1000);
        com.solegendary.reignofnether.mana.ManaServerEvents.bindTraits(owner, FactionTraits.STORMCALLERS);
        var master = com.solegendary.reignofnether.registrars.EntityRegistrar.LEY_MASTER_UNIT.get().create(level);
        var geo = com.solegendary.reignofnether.registrars.EntityRegistrar.GEOMANCER_UNIT.get().create(level);
        var far = com.solegendary.reignofnether.registrars.EntityRegistrar.FIRE_ADEPT_UNIT.get().create(level);   // a caster 14 blocks off
        Runnable cleanup = () -> {
            if (master != null) master.discard();
            if (geo != null) geo.discard();
            if (far != null) far.discard();
            dropManaPool(owner, pool);   // ManaServerEvents.clearOwner takes the wells and their markers with it
        };
        try {
            if (master == null || geo == null || far == null) {
                helper.fail("could not create the Ley Master test units");
                return;
            }
            master.setOwnerName(owner);
            geo.setOwnerName(owner);
            far.setOwnerName(owner);
            BlockPos at = helper.absolutePos(new BlockPos(2, 2, 7));
            master.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
            far.moveTo(at.getX() + 13.5, at.getY(), at.getZ() + 7.5, 0, 0);   // sqrt(13^2+7^2) ~ 14.8 from the well
            if (master.getBuildPower() < 1.99f)
                helper.fail("the Ley Master builds at " + master.getBuildPower());
            if (!com.solegendary.reignofnether.unit.T2Workers.isT2Worker(master))
                helper.fail("the Ley Master is not a T2 worker");
            if (com.solegendary.reignofnether.unit.T2Workers.isT2Worker(geo))
                helper.fail("a plain Geomancer counts as a T2 worker");
            if (!Factions.STORMCALLERS.equals(Factions.getFaction(master)))
                helper.fail("the Ley Master is not a Stormcallers unit");
            if (master.getMaxHealth() < 2 * geo.getMaxHealth() - 0.01f)
                helper.fail("the Ley Master has " + master.getMaxHealth() + " health, the Geomancer " + geo.getMaxHealth());
            if (geo.getAbilities().get().stream().anyMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.ManaWell))
                helper.fail("a plain Geomancer got Mana Well");
            com.solegendary.reignofnether.ability.abilities.ManaWell well = null;
            for (var a : master.getAbilities().get())
                if (a instanceof com.solegendary.reignofnether.ability.abilities.ManaWell w)
                    well = w;
            if (well == null) {
                helper.fail("the Ley Master has no Mana Well");
                return;
            }
            BlockPos aim = helper.absolutePos(new BlockPos(3, 1, 7));   // the ground block beside it
            well.use(level, master, aim);
            if (com.solegendary.reignofnether.mana.ManaServerEvents.wellCount(owner) != 1) {
                helper.fail("Mana Well raised nothing: " + com.solegendary.reignofnether.mana.ManaServerEvents.lastRefusal());
                return;
            }
            if (Math.abs(pool.getEnergy() - (1000 - com.solegendary.reignofnether.ability.abilities.ManaWell.ENERGY_COST)) > 0.01f)
                helper.fail("Mana Well did not charge its energy: " + pool.getEnergy());
            if (well.isOffCooldown(master))
                helper.fail("Mana Well did not go on cooldown");
            var wells = com.solegendary.reignofnether.mana.ManaWellServerEvents.wellsOf(owner);
            if (wells.size() != 1 || wells.get(0).centre.distSqr(aim) > 4)
                helper.fail("the well record is not where it was aimed: " + wells);
            // inside the well: power x1.25 and the cost x0.8; outside: neither
            float before = pool.getEnergy();
            float power = com.solegendary.reignofnether.mana.ManaServerEvents.tryCastQuiet(master, owner, 100);
            float paid = before - pool.getEnergy();
            if (Math.abs(power - com.solegendary.reignofnether.mana.ManaServerEvents.WELL_POWER) > 0.001f
                    || Math.abs(paid - 100 * com.solegendary.reignofnether.mana.ManaServerEvents.WELL_DISCOUNT) > 0.01f)
                helper.fail("a spell inside the well got power " + power + " and paid " + paid);
            before = pool.getEnergy();
            power = com.solegendary.reignofnether.mana.ManaServerEvents.tryCastQuiet(far, owner, 100);
            paid = before - pool.getEnergy();
            if (Math.abs(power - 1f) > 0.001f || Math.abs(paid - 100) > 0.01f)
                helper.fail("a spell outside the well got power " + power + " and paid " + paid);
            // the cap: three more go up (10 apart so they don't dismantle one another), the fifth is refused unpaid
            well.setCooldown(0, master);
            for (int i = 1; i <= 3; i++) {
                master.moveTo(at.getX() + 1.5 + i * 3, at.getY(), at.getZ() + 2.5, 0, 0);   // beside each new well, inside its 8-block reach
                if (!com.solegendary.reignofnether.ability.abilities.ManaWell.cast(level, master, owner, helper.absolutePos(new BlockPos(3 + i * 3, 1, 7)), true))
                    helper.fail("well " + (i + 1) + " was refused: " + com.solegendary.reignofnether.mana.ManaServerEvents.lastRefusal());
            }
            if (com.solegendary.reignofnether.mana.ManaServerEvents.wellCount(owner) != com.solegendary.reignofnether.mana.ManaServerEvents.WELLS_PER_PLAYER)
                helper.fail("the owner has " + com.solegendary.reignofnether.mana.ManaServerEvents.wellCount(owner) + " wells, not the cap");
            before = pool.getEnergy();
            if (com.solegendary.reignofnether.ability.abilities.ManaWell.cast(level, master, owner, helper.absolutePos(new BlockPos(6, 1, 12)), true)
                    || Math.abs(pool.getEnergy() - before) > 0.01f)
                helper.fail("a fifth well was raised or paid for over the cap");
            // a cast on an own well takes it down, for free
            master.moveTo(aim.getX() + 0.5, at.getY(), aim.getZ() + 0.5, 0, 0);
            if (!com.solegendary.reignofnether.ability.abilities.ManaWell.cast(level, master, owner, aim, true)
                    || com.solegendary.reignofnether.mana.ManaServerEvents.wellCount(owner) != com.solegendary.reignofnether.mana.ManaServerEvents.WELLS_PER_PLAYER - 1
                    || Math.abs(pool.getEnergy() - before) > 0.01f)
                helper.fail("casting on an own well did not take it down for free: " + com.solegendary.reignofnether.mana.ManaServerEvents.wellCount(owner) + " wells, energy " + pool.getEnergy());
            helper.succeed();
        } finally {
            cleanup.run();
        }
    }

    /**
     * The Thunder Dragon's chain lightning: the strike hurts its target and arcs to up to three more enemies within five
     * blocks of the last link - never a friend standing among them, never a fifth foe, never one out of reach - each
     * link weaker than the last; the strike charges 10 energy.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void thunder_dragon_chain_hits_up_to_three_more_foes_not_friends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_storm_dragon_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        openGround(helper, 0, 0, 15, 15);
        var pool = manaPool(owner, 500);
        com.solegendary.reignofnether.mana.ManaServerEvents.bindTraits(owner, FactionTraits.STORMCALLERS);
        var vill = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get();
        var dragon = com.solegendary.reignofnether.registrars.EntityRegistrar.THUNDER_DRAGON_UNIT.get().create(level);
        var first = vill.create(level);
        var f2 = vill.create(level);
        var f3 = vill.create(level);
        var f4 = vill.create(level);
        var fifth = vill.create(level);    // a fifth foe in the chain's reach: the chain stops at three jumps
        var friend = vill.create(level);   // ours, closest to the first link of all
        var farFoe = vill.create(level);   // 8 blocks from any link
        List<net.minecraft.world.entity.Mob> all = new ArrayList<>();
        for (net.minecraft.world.entity.Mob e : java.util.Arrays.<net.minecraft.world.entity.Mob>asList(dragon, first, f2, f3, f4, fifth, friend, farFoe))
            if (e != null)
                all.add(e);
        Runnable cleanup = () -> {
            for (var e : all) e.discard();
            dropManaPool(owner, pool);
        };
        if (all.size() != 8) {
            helper.fail("could not create the chain lightning test units");
            cleanup.run();
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 7));
        dragon.moveTo(at.getX() + 0.5, at.getY() + 4, at.getZ() + 0.5, -90, 0);
        // a line of foes 2 blocks apart along +x from the first, the friend 1 block from the first, the far foe 8 off
        first.moveTo(at.getX() + 6.5, at.getY(), at.getZ() + 0.5, 0, 0);
        f2.moveTo(at.getX() + 8.5, at.getY(), at.getZ() + 0.5, 0, 0);
        f3.moveTo(at.getX() + 10.5, at.getY(), at.getZ() + 0.5, 0, 0);
        f4.moveTo(at.getX() + 12.5, at.getY(), at.getZ() + 0.5, 0, 0);
        fifth.moveTo(at.getX() + 12.5, at.getY(), at.getZ() + 3.5, 0, 0);
        friend.moveTo(at.getX() + 6.5, at.getY(), at.getZ() + 1.5, 0, 0);
        farFoe.moveTo(at.getX() + 6.5, at.getY(), at.getZ() - 7.5, 0, 0);
        dragon.setOwnerName(owner);
        friend.setOwnerName(owner);
        for (var e : java.util.List.of(first, f2, f3, f4, fifth, farFoe))
            e.setOwnerName(foeOwner);
        for (var e : all) {
            e.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            level.addFreshEntity(e);   // the chain finds its links through the unit grid, which only knows units in the level
        }
        float[] hp = new float[7];
        helper.runAfterDelay(5, () -> {
            try {
                if (!dragon.isFlyingUnit())
                    helper.fail("the Thunder Dragon is not a flyer");
                var foes = java.util.List.of(first, f2, f3, f4, fifth, friend, farFoe);
                for (int i = 0; i < foes.size(); i++)
                    hp[i] = foes.get(i).getHealth();
                int hits = dragon.strike(first);
                if (hits != 1 + com.solegendary.reignofnether.unit.units.storm.ThunderDragonUnit.CHAIN_JUMPS)
                    helper.fail("the chain hit " + hits + " units, not " + (1 + com.solegendary.reignofnether.unit.units.storm.ThunderDragonUnit.CHAIN_JUMPS));
                if (Math.abs(pool.getEnergy() - (500 - com.solegendary.reignofnether.unit.units.storm.ThunderDragonUnit.ENERGY_PER_STRIKE)) > 0.01f)
                    helper.fail("the strike did not charge its energy: " + pool.getEnergy());
                float d1 = hp[0] - first.getHealth(), d2 = hp[1] - f2.getHealth(), d3 = hp[2] - f3.getHealth(), d4 = hp[3] - f4.getHealth();
                if (d1 <= 0 || d2 <= 0 || d3 <= 0 || d4 <= 0)
                    helper.fail("a link was not hurt: " + d1 + ", " + d2 + ", " + d3 + ", " + d4);
                if (!(d1 > d2 && d2 > d3 && d3 > d4))
                    helper.fail("the chain did not weaken along its links: " + d1 + ", " + d2 + ", " + d3 + ", " + d4);
                if (fifth.getHealth() < hp[4])
                    helper.fail("the chain jumped a fourth time");
                if (friend.getHealth() < hp[5])
                    helper.fail("the chain arced to a friend");
                if (farFoe.getHealth() < hp[6])
                    helper.fail("the chain reached a foe out of its jump radius");
                helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    /**
     * The Earthshaker's Ground Slam: nothing is hurt during the telegraph; when the ground heaves, enemies on the line
     * take damage and are rooted (Slowness VII, they cannot walk) and a friend on the line is untouched, as is an enemy
     * beside it. The cast charges 60 energy and a refused cast (no energy) pays nothing and starts no cooldown.
     */
    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void earthshaker_ground_slam_roots_foes_on_the_line_not_friends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_storm_slam_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        openGround(helper, 0, 0, 15, 15);
        var pool = manaPool(owner, 500);
        com.solegendary.reignofnether.mana.ManaServerEvents.bindTraits(owner, FactionTraits.STORMCALLERS);
        // the bystanders are Ink Scribes: they never attack, so nothing but the slam can hurt anyone during the telegraph
        var scribe = com.solegendary.reignofnether.registrars.EntityRegistrar.INK_SCRIBE_UNIT.get();
        var golem = com.solegendary.reignofnether.registrars.EntityRegistrar.EARTHSHAKER_UNIT.get().create(level);
        var onLine = scribe.create(level);
        var friend = scribe.create(level);
        var beside = scribe.create(level);
        List<net.minecraft.world.entity.Mob> all = new ArrayList<>();
        for (net.minecraft.world.entity.Mob e : java.util.Arrays.<net.minecraft.world.entity.Mob>asList(golem, onLine, friend, beside))
            if (e != null)
                all.add(e);
        Runnable cleanup = () -> {
            for (var e : all) e.discard();
            dropManaPool(owner, pool);
        };
        if (all.size() != 4) {
            helper.fail("could not create the Ground Slam test units");
            cleanup.run();
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 7));
        BlockPos aim = helper.absolutePos(new BlockPos(10, 1, 7));
        Runnable place = () -> {
            golem.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, -90, 0);
            onLine.moveTo(at.getX() + 5.5, at.getY(), at.getZ() + 0.5, 0, 0);
            friend.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5, 0, 0);
            beside.moveTo(at.getX() + 5.5, at.getY(), at.getZ() + 4.5, 0, 0);   // 4 blocks off the line
            for (var e : all)
                e.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        };
        golem.setOwnerName(owner);
        friend.setOwnerName(owner);
        onLine.setOwnerName(foeOwner);
        beside.setOwnerName(foeOwner);
        place.run();
        // the golem stays out of the level (it would walk up and punch the enemy during the telegraph); the hits
        // helper only needs its position, and the villagers join so the unit grid knows them
        for (var e : all)
            if (e != golem)
                level.addFreshEntity(e);
        com.solegendary.reignofnether.ability.abilities.GroundSlam found = null;
        for (var a : golem.getAbilities().get())
            if (a instanceof com.solegendary.reignofnether.ability.abilities.GroundSlam s)
                found = s;
        if (found == null) {
            helper.fail("the Earthshaker has no Ground Slam");
            cleanup.run();
            return;
        }
        final var slam = found;
        float[] hp = new float[3];
        helper.runAfterDelay(5, () -> {
            place.run();
            hp[0] = onLine.getHealth(); hp[1] = friend.getHealth(); hp[2] = beside.getHealth();
            slam.use(level, golem, aim);
            if (Math.abs(pool.getEnergy() - (500 - com.solegendary.reignofnether.ability.abilities.GroundSlam.ENERGY_COST)) > 0.01f)
                helper.fail("Ground Slam did not charge its energy: " + pool.getEnergy());
            if (slam.isOffCooldown(golem))
                helper.fail("Ground Slam did not go on cooldown");
        });
        helper.runAfterDelay(5 + com.solegendary.reignofnether.ability.abilities.GroundSlam.TELEGRAPH_TICKS - 3, () -> {
            if (onLine.getHealth() < hp[0])
                helper.fail("Ground Slam struck before its telegraph ended");
        });
        helper.runAfterDelay(5 + com.solegendary.reignofnether.ability.abilities.GroundSlam.TELEGRAPH_TICKS + 4, () -> {
            try {
                if (onLine.getHealth() >= hp[0])
                    helper.fail("Ground Slam did not hurt the enemy on its line");
                var slow = onLine.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
                if (slow == null || slow.getAmplifier() < com.solegendary.reignofnether.ability.abilities.GroundSlam.ROOT_AMPLIFIER)
                    helper.fail("the enemy on the line was not rooted: " + slow);
                if (friend.getHealth() < hp[1] || friend.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN))
                    helper.fail("Ground Slam hurt or rooted a friend on its line");
                if (beside.getHealth() < hp[2] || beside.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN))
                    helper.fail("Ground Slam reached an enemy beside its line");
                // refused: no energy - nothing paid, no cooldown
                slam.setCooldown(0, golem);
                pool.addEnergy(10f - pool.getEnergy());
                slam.use(level, golem, aim);
                if (Math.abs(pool.getEnergy() - 10f) > 0.01f || !slam.isOffCooldown(golem))
                    helper.fail("a refused Ground Slam took energy or started its cooldown");
                helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    /**
     * The Gravity Monk's Singularity: when the vortex closes, every enemy within its radius is thrown toward the centre
     * (its motion points inward, it is slowed) while a friend inside and an enemy outside the ring are left alone; a
     * golem-sized enemy is slowed but not moved. The cast charges 50 energy.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void gravity_monk_singularity_pulls_foes_together_not_friends(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_storm_gravity_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        openGround(helper, 0, 0, 15, 15);
        var pool = manaPool(owner, 500);
        com.solegendary.reignofnether.mana.ManaServerEvents.bindTraits(owner, FactionTraits.STORMCALLERS);
        var vill = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get();
        var monk = com.solegendary.reignofnether.registrars.EntityRegistrar.GRAVITY_MONK_UNIT.get().create(level);
        var east = vill.create(level);     // 3 blocks east of the centre
        var west = vill.create(level);     // 3 blocks west
        var friend = vill.create(level);   // ours, 2 blocks south
        var outside = vill.create(level);  // 7 blocks north
        var big = com.solegendary.reignofnether.registrars.EntityRegistrar.IRON_GOLEM_UNIT.get().create(level);   // too wide to pull
        List<net.minecraft.world.entity.Mob> all = new ArrayList<>();
        for (net.minecraft.world.entity.Mob e : java.util.Arrays.<net.minecraft.world.entity.Mob>asList(monk, east, west, friend, outside, big))
            if (e != null)
                all.add(e);
        Runnable cleanup = () -> {
            for (var e : all) e.discard();
            dropManaPool(owner, pool);
        };
        if (all.size() != 6) {
            helper.fail("could not create the Singularity test units");
            cleanup.run();
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 7));
        BlockPos centre = helper.absolutePos(new BlockPos(9, 1, 7));
        double cx = centre.getX() + 0.5, cz = centre.getZ() + 0.5;
        monk.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, -90, 0);
        east.moveTo(cx + 3, at.getY(), cz, 0, 0);
        west.moveTo(cx - 3, at.getY(), cz, 0, 0);
        friend.moveTo(cx, at.getY(), cz + 2, 0, 0);
        outside.moveTo(cx, at.getY(), cz - 7, 0, 0);
        big.moveTo(cx + 2, at.getY(), cz - 2, 0, 0);
        monk.setOwnerName(owner);
        friend.setOwnerName(owner);
        for (var e : java.util.List.of(east, west, outside, big))
            e.setOwnerName(foeOwner);
        for (var e : all) {
            e.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            level.addFreshEntity(e);
        }
        helper.runAfterDelay(5, () -> {
            try {
                for (var e : all)
                    e.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                double bigX = big.getX();
                // the closed vortex itself (the cast only schedules it after the telegraph)
                int caught = com.solegendary.reignofnether.ability.abilities.Singularity.pull(level, owner, new net.minecraft.world.phys.Vec3(cx, at.getY(), cz),
                    com.solegendary.reignofnether.ability.abilities.Singularity.RADIUS);
                if (caught != 3)
                    helper.fail("the vortex caught " + caught + " units, not the two foes and the golem");
                if (east.getDeltaMovement().x >= -0.05 || west.getDeltaMovement().x <= 0.05)
                    helper.fail("the foes were not thrown toward the centre: east " + east.getDeltaMovement() + ", west " + west.getDeltaMovement());
                if (!east.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN) || !big.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN))
                    helper.fail("the foes inside were not slowed");
                if (big.getDeltaMovement().horizontalDistanceSqr() > 1e-4 || Math.abs(big.getX() - bigX) > 0.01)
                    helper.fail("a golem-sized foe was pulled: " + big.getDeltaMovement());
                if (friend.getDeltaMovement().horizontalDistanceSqr() > 1e-4 || friend.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN))
                    helper.fail("the friend inside was pulled or slowed");
                if (outside.getDeltaMovement().horizontalDistanceSqr() > 1e-4 || outside.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN))
                    helper.fail("a foe outside the ring was pulled or slowed");
                // the cast pays 50 and schedules the vortex
                if (!com.solegendary.reignofnether.ability.abilities.Singularity.cast(level, monk, owner, centre, true)
                        || Math.abs(pool.getEnergy() - (500 - com.solegendary.reignofnether.ability.abilities.Singularity.ENERGY_COST)) > 0.01f)
                    helper.fail("Singularity did not cast or did not charge its energy: " + pool.getEnergy());
                helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    /**
     * The Mirror Mage's Mirror Images: three decoys of the chosen friend appear (the cast pays 80), tagged, of its type
     * and side, counted in no one's population; an enemy sees them as hostile targets; a decoy's attack lands no damage
     * and the first hit on a decoy shatters it; a commander is never mirrored; the rest fade after their 10 s.
     */
    @GameTest(template = ARENA, timeoutTicks = 260)
    public static void mirror_images_decoys_draw_fire_deal_nothing_and_fade(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_storm_mirror_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        openGround(helper, 0, 0, 15, 15);
        var pool = manaPool(owner, 500);
        com.solegendary.reignofnether.mana.ManaServerEvents.bindTraits(owner, FactionTraits.STORMCALLERS);
        var mage = com.solegendary.reignofnether.registrars.EntityRegistrar.MIRROR_MAGE_UNIT.get().create(level);
        var guard = com.solegendary.reignofnether.registrars.EntityRegistrar.JADE_GUARD_UNIT.get().create(level);
        var sage = com.solegendary.reignofnether.registrars.EntityRegistrar.GEOMANCER_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        List<net.minecraft.world.entity.Mob> all = new ArrayList<>();
        for (net.minecraft.world.entity.Mob e : java.util.Arrays.<net.minecraft.world.entity.Mob>asList(mage, guard, sage, foe))
            if (e != null)
                all.add(e);
        List<net.minecraft.world.entity.LivingEntity> made = new ArrayList<>();
        Runnable cleanup = () -> {
            for (var e : all) e.discard();
            for (var d : made) d.discard();
            com.solegendary.reignofnether.mana.DecoyServerEvents.clearOwner(owner);
            dropManaPool(owner, pool);
        };
        if (all.size() != 4) {
            helper.fail("could not create the Mirror Images test units");
            cleanup.run();
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(2, 2, 7));
        mage.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, -90, 0);
        guard.moveTo(at.getX() + 5.5, at.getY(), at.getZ() + 0.5, 0, 0);
        sage.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 4.5, 0, 0);
        foe.moveTo(at.getX() + 11.5, at.getY(), at.getZ() + 0.5, 0, 0);
        mage.setOwnerName(owner);
        guard.setOwnerName(owner);
        sage.setOwnerName(owner);
        foe.setOwnerName(foeOwner);
        // the foe stays out of the level: in it, the guard and the decoys would fight it and the decoy count would
        // change under the later checks (a decoy shatters at the first blow it takes)
        for (var e : all)
            if (e != foe)
                level.addFreshEntity(e);
        com.solegendary.reignofnether.player.CommanderServerEvents.makeCommander(sage);
        helper.runAfterDelay(5, () -> {
            int popBefore = com.solegendary.reignofnether.unit.UnitServerEvents.getCurrentPopulation(owner);
            // never a commander
            if (com.solegendary.reignofnether.ability.abilities.MirrorImages.cast(level, mage, owner, sage, true) != null
                    || Math.abs(pool.getEnergy() - 500) > 0.01f) {
                helper.fail("Mirror Images copied the commander or paid for the attempt");
                cleanup.run();
                return;
            }
            var decoys = com.solegendary.reignofnether.ability.abilities.MirrorImages.cast(level, mage, owner, guard, true);
            if (decoys == null || decoys.size() != com.solegendary.reignofnether.ability.abilities.MirrorImages.COPIES) {
                helper.fail("Mirror Images made " + (decoys == null ? "nothing" : decoys.size() + " decoys"));
                cleanup.run();
                return;
            }
            made.addAll(decoys);
            if (Math.abs(pool.getEnergy() - (500 - com.solegendary.reignofnether.ability.abilities.MirrorImages.ENERGY_COST)) > 0.01f)
                helper.fail("Mirror Images did not charge its energy: " + pool.getEnergy());
            for (var d : decoys)
                if (!com.solegendary.reignofnether.mana.DecoyServerEvents.isDecoy(d) || d.getType() != guard.getType()
                        || !(d instanceof com.solegendary.reignofnether.unit.interfaces.Unit du) || !owner.equals(du.getOwnerName()))
                    helper.fail("a decoy is not a tagged copy of the Jade Guard on our side: " + d);
            if (com.solegendary.reignofnether.mana.DecoyServerEvents.countOf(owner) != 3)
                helper.fail("the decoy count is " + com.solegendary.reignofnether.mana.DecoyServerEvents.countOf(owner));
            // the enemy sees a hostile unit (aggro), the population sees nothing
            if (com.solegendary.reignofnether.unit.UnitServerEvents.getUnitToEntityRelationship((com.solegendary.reignofnether.unit.interfaces.Unit) foe, decoys.get(0))
                    != com.solegendary.reignofnether.unit.Relationship.HOSTILE)
                helper.fail("an enemy does not see a decoy as hostile");
            if (com.solegendary.reignofnether.unit.UnitServerEvents.getCurrentPopulation(owner) != popBefore)
                helper.fail("decoys count toward population: " + com.solegendary.reignofnether.unit.UnitServerEvents.getCurrentPopulation(owner) + " vs " + popBefore);
            // a decoy's blow lands no damage
            float foeHp = foe.getHealth();
            foe.hurt(level.damageSources().mobAttack(decoys.get(0)), 6f);
            if (foe.getHealth() < foeHp)
                helper.fail("a decoy's attack hurt the enemy");
            // the first hit shatters a decoy; the real guard takes the hit normally
            net.minecraft.world.entity.LivingEntity shattered = decoys.get(1);
            shattered.hurt(level.damageSources().mobAttack(foe), 1f);
            if (!shattered.isRemoved() || com.solegendary.reignofnether.mana.DecoyServerEvents.countOf(owner) != 2)
                helper.fail("one hit did not shatter a decoy (" + com.solegendary.reignofnether.mana.DecoyServerEvents.countOf(owner) + " left)");
            float guardHp = guard.getHealth();
            guard.hurt(level.damageSources().mobAttack(foe), 1f);
            if (guard.isRemoved() || guard.getHealth() >= guardHp)
                helper.fail("the real Jade Guard behaved like a decoy");
        });
        helper.runAfterDelay(5 + com.solegendary.reignofnether.mana.DecoyServerEvents.LIFETIME_TICKS / 2, () -> {
            if (com.solegendary.reignofnether.mana.DecoyServerEvents.countOf(owner) != 2)
                helper.fail("the decoys did not last half their lifetime: " + com.solegendary.reignofnether.mana.DecoyServerEvents.countOf(owner));
        });
        helper.runAfterDelay(5 + com.solegendary.reignofnether.mana.DecoyServerEvents.LIFETIME_TICKS + 30, () -> {
            try {
                if (com.solegendary.reignofnether.mana.DecoyServerEvents.countOf(owner) != 0)
                    helper.fail("the decoys did not fade after their lifetime: " + com.solegendary.reignofnether.mana.DecoyServerEvents.countOf(owner));
                for (var d : made)
                    if (!d.isRemoved())
                        helper.fail("a decoy is still in the world after its lifetime");
                helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    // ------------------------------------------------------------------ clip 3: queues, formations, defeat cleanup

    /**
     * A production queue holds ProductionPlacement.MAX_QUEUE items; the next one is refused ("Queue full" to the owner)
     * and NOTHING already queued is lost (lovish: "I can't queue more than that or it bugs or cancels").
     */
    @GameTest(template = ARENA)
    public static void production_queue_caps_with_a_message_not_a_cancel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_prod_cap_" + helper.absolutePos(BlockPos.ZERO).asLong();
        var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 0, 0);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
        // a placement that is never put in the world: enough for the queue, and invisible to parallel tests
        var bp = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(Buildings.BARRACKS, level,
            helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, owner, false);
        try {
            if (!(bp instanceof com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement pp)) {
                helper.fail("the Barracks is not a production placement: " + bp);
                return;
            }
            var item = com.solegendary.reignofnether.building.production.ProductionItems.VINDICATOR;
            int max = com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement.MAX_QUEUE;
            for (int i = 0; i < max; i++)
                if (!pp.startProductionItem(item)) {
                    helper.fail("item " + (i + 1) + " of " + max + " was refused: " + item.getProduceErrorMsg(pp));
                    return;
                }
            if (pp.startProductionItem(item))
                helper.fail("item " + (max + 1) + " was accepted past the cap");
            if (pp.productionQueue.size() != max)
                helper.fail("hitting the cap changed the queue: " + pp.productionQueue.size() + " items, expected " + max);
            helper.succeed();
        } finally {
            if (bp instanceof com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement pp)
                pp.productionQueue.clear();
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
        }
    }

    /**
     * Shift-queued build orders (what a Shift+drag line or grid sends, one placement per site): every valid site lands
     * in the builder's queue in order, a plain order already under way stays its head instead of being dropped (the
     * old reset cancelled it), and past BuildRepairGoal.MAX_QUEUE a site is refused and never placed.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void shift_queued_sites_line_up_behind_the_current_one(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_build_queue_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 210;   // this test's own column, its own height (see builders_send_nano_beams)
        int cx = base.getX(), cz = base.getZ();
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        var worker = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        if (worker == null) {
            helper.fail("could not create the worker");
            return;
        }
        worker.moveTo(cx + 0.5, y + 1, cz - 7.5, 0, 0);
        worker.setOwnerName(owner);
        level.addFreshEntity(worker);
        List<com.solegendary.reignofnether.building.BuildingPlacement> sites = new ArrayList<>();
        Runnable cleanup = () -> {
            com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().removeAll(sites);
            worker.discard();
        };
        helper.runAfterDelay(3, () -> {
            try {
                int[] ids = { worker.getId() };
                var wind = Buildings.WIND_GENERATOR_VILLAGERS;
                java.util.function.BiFunction<Integer, Boolean, com.solegendary.reignofnether.building.BuildingPlacement> place =
                    (x, queue) -> com.solegendary.reignofnether.building.BuildingServerEvents.placeBuilding(
                        wind, new BlockPos(cx + x, y, cz), Rotation.NONE, owner, ids, queue, false, true, true);
                var brg = worker.getBuildRepairGoal();
                var a = place.apply(-6, false);
                if (a != null) sites.add(a);
                if (a == null || brg.getBuildingTarget() != a) {
                    helper.fail("the plain order did not set the worker's site (" + a + " vs " + brg.getBuildingTarget() + ")");
                    return;
                }
                var b = place.apply(0, true);
                var c = place.apply(6, true);
                if (b != null) sites.add(b);
                if (c != null) sites.add(c);
                if (b == null || c == null) {
                    helper.fail("a queued site was refused: " + b + ", " + c);
                    return;
                }
                if (brg.getBuildingTarget() != a)
                    helper.fail("shift-queueing dropped the site under way (target now " + brg.getBuildingTarget() + ")");
                if (!brg.queuedBuildings.equals(List.of(a, b, c)))
                    helper.fail("expected the queue [a, b, c], got " + brg.queuedBuildings.size() + " entries");
                // fill the queue to the cap (duplicates are fine for the count) and ask for one more
                int max = com.solegendary.reignofnether.unit.goals.BuildRepairGoal.MAX_QUEUE;
                while (brg.queuedBuildings.size() < max)
                    brg.queuedBuildings.add(c);
                var d = place.apply(-3, true);
                if (d != null) {
                    sites.add(d);
                    helper.fail("a site was placed for a builder whose queue is full");
                }
                boolean dPlaced = false;
                for (var s : com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings())
                    if (s.originPos.equals(new BlockPos(cx - 3, y, cz)))
                        dPlaced = true;
                if (dPlaced)
                    helper.fail("the refused site is still in the building list");
                if (brg.queuedBuildings.size() != max)
                    helper.fail("the refused order changed the queue: " + brg.queuedBuildings.size());
                helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    /**
     * When a side is defeated its base must not stand on as live targets (lovish's clip: "You are victorious!" over a
     * bot's capitol at 310/400). Within a few seconds every building it owned is gone from the match, a neutral wreck
     * with half the building's metal sits where it stood, and its units are dead.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void defeated_sides_base_becomes_wrecks_and_its_units_die(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_defeat_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 215;
        int cx = base.getX(), cz = base.getZ();
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        var side = com.solegendary.reignofnether.player.RTSPlayer.getNewBot(owner, Factions.VILLAGERS);
        var rtsPlayers = com.solegendary.reignofnether.player.PlayerServerEvents.rtsPlayers;
        synchronized (rtsPlayers) {
            rtsPlayers.add(side);
        }
        com.solegendary.reignofnether.resources.ResourcesServerEvents.assignResources(owner);
        var wind = Buildings.WIND_GENERATOR_VILLAGERS;
        var a = com.solegendary.reignofnether.building.BuildingServerEvents.placeBuilding(wind, new BlockPos(cx - 5, y, cz),
            Rotation.NONE, owner, new int[0], false, false, true, true);
        var b = com.solegendary.reignofnether.building.BuildingServerEvents.placeBuilding(wind, new BlockPos(cx + 5, y, cz),
            Rotation.NONE, owner, new int[0], false, false, true, true);
        var soldier = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        List<com.solegendary.reignofnether.building.BuildingPlacement> sites = new ArrayList<>();
        if (a != null) sites.add(a);
        if (b != null) sites.add(b);
        Runnable cleanup = () -> {
            com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().removeAll(sites);
            synchronized (rtsPlayers) {
                rtsPlayers.remove(side);
            }
            com.solegendary.reignofnether.player.PlayerServerEvents.postGameRtsPlayers.remove(side);
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.removeIf(r -> r.ownerName.equals(owner));
            if (soldier != null) soldier.discard();
            for (var w : new ArrayList<>(com.solegendary.reignofnether.resources.WreckServerEvents.getWrecks()))
                if (!w.isRemoved() && Math.abs(w.getY() - y) < 4 && w.distanceToSqr(cx + 0.5, y, cz + 0.5) < 12 * 12)
                    w.discard();
        };
        if (a == null || b == null || soldier == null) {
            cleanup.run();
            helper.fail("could not set up the base (" + a + ", " + b + ") or the unit (" + soldier + ")");
            return;
        }
        soldier.moveTo(cx + 0.5, y + 1, cz + 6.5, 0, 0);
        soldier.setOwnerName(owner);
        level.addFreshEntity(soldier);
        helper.runAfterDelay(3, () -> {
            com.solegendary.reignofnether.player.PlayerServerEvents.defeat(owner, "gametest");
            if (com.solegendary.reignofnether.player.PlayerServerEvents.isRTSPlayer(owner)) {
                cleanup.run();
                helper.fail("defeat() left the side in the match");
            }
        });
        helper.runAfterDelay(80, () -> {   // 2 buildings: both come down at the first STEP_TICKS step
            try {
                List<String> problems = new ArrayList<>();
                if (com.solegendary.reignofnether.player.DefeatCleanupServerEvents.isCleaningUp(owner))
                    problems.add("the cleanup is still running after 4 s");
                var buildings = com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings();
                if (buildings.contains(a) || buildings.contains(b))
                    problems.add("a defeated side's building is still in the match");
                for (var site : List.of(a, b)) {
                    boolean wreck = false;
                    for (var w : com.solegendary.reignofnether.resources.WreckServerEvents.getWrecks())
                        if (!w.isRemoved() && w.distanceToSqr(site.centrePos.getX() + 0.5, y, site.centrePos.getZ() + 0.5) < 3 * 3
                                && com.solegendary.reignofnether.resources.WreckServerEvents.metalOf(w)
                                    >= wind.cost.metal() * com.solegendary.reignofnether.player.DefeatCleanupServerEvents.BUILDING_WRECK_SHARE - 0.01f)
                            wreck = true;
                    if (!wreck)
                        problems.add("no wreck with half the building's metal where the site at " + site.originPos.toShortString() + " stood");
                }
                if (soldier.isAlive() && !soldier.isRemoved())
                    problems.add("the defeated side's unit is still alive");
                if (!problems.isEmpty())
                    helper.fail(String.join("; ", problems));
                else
                    helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    // ---------------------------------------------------------------------------------------------------------------
    // lovish: Verdant workers "got stuck by some stone that the building architecture had". Pockets: cells of a finished
    // structure a unit can stand on but never walk out of by the pathfinder's own rules - a hollow between a pillar and
    // a rim, a slot under a chain, a doorway with a lantern in it. These tests stamp every registered building on a
    // flat pad, flood-fill the grid's moves (UnitUnstick.isWalkable / bodyBlocked, GridNeighbors: 8 flat, 4 cardinal
    // step-ups needing headroom over the origin, step-downs, drops of up to MAX_FALL_DROP) and fail with the building
    // and the cell for: (a) a cell the outside can walk INTO but not out of, (b) a production building's spawn point
    // (ProductionPlacement.safeSpawnPoint) that can't reach the outside, (c) for the structures tools/gen_structures.py
    // draws, a ground-level cell with no way out and no door on it (Reign of Nether's own halls and keeps are closed
    // rooms by design; the grid treats their doors as walls, and nothing spawns or is rescued into them any more).
    // tools/audit_structure_pockets.py is the offline twin for checking a structure before a CI round trip. Four
    // buckets by name so no single test stamps ~90 structures in one tick; each stamps on its own pad 6000 blocks
    // south of the grid, 4 chunks apart.

    /** Structures drawn by tools/gen_structures.py: our own art, so a closed ground cell in one is a bug, not a room. */
    private static final Set<String> GENERATED_STRUCTURES = Set.of(
        "metal_extractor", "metal_extractor_t2", "wind_generator", "energy_converter", "storage_vault",
        "metal_extractor_dark", "metal_extractor_t2_dark", "wind_generator_dark", "energy_converter_dark", "storage_vault_dark",
        "metal_extractor_nether", "metal_extractor_t2_nether", "wind_generator_nether", "energy_converter_nether", "storage_vault_nether",
        "metal_extractor_verdant", "metal_extractor_t2_verdant", "wind_generator_verdant", "energy_converter_verdant", "storage_vault_verdant",
        "metal_extractor_tide", "metal_extractor_t2_tide", "wind_generator_tide", "energy_converter_tide", "storage_vault_tide",
        "metal_extractor_storm", "metal_extractor_t2_storm", "wind_generator_storm", "energy_converter_storm", "storage_vault_storm",
        "villager_house", "haunted_house", "heartwood_hall", "grove", "circle_of_elders", "heart_of_the_wild", "storm_oak",
        "wreck_harbour", "slipway", "drydock", "lighthouse_battery", "abyssal_shipyard", "jade_pagoda", "lotus_academy",
        "celestial_observatory");

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void structures_have_no_unit_pockets_0(GameTestHelper helper) { structuresHaveNoUnitPockets(helper, 0); }
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void structures_have_no_unit_pockets_1(GameTestHelper helper) { structuresHaveNoUnitPockets(helper, 1); }
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void structures_have_no_unit_pockets_2(GameTestHelper helper) { structuresHaveNoUnitPockets(helper, 2); }
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void structures_have_no_unit_pockets_3(GameTestHelper helper) { structuresHaveNoUnitPockets(helper, 3); }

    private static final int POCKET_BUCKETS = 4;

    private static void structuresHaveNoUnitPockets(GameTestHelper helper, int bucket) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y0 = 200, margin = 3;
        int chunkX = base.getX() >> 4, chunkZ = (base.getZ() + 6000 + bucket * 64) >> 4;
        List<String> problems = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int i = 0, checked = 0;
        for (Building building : ReignOfNetherRegistries.BUILDING) {
            if (building instanceof AbstractBridge || building.structureName == null || !seen.add(building.structureName))
                continue;
            if (Math.floorMod(building.structureName.hashCode(), POCKET_BUCKETS) != bucket)
                continue;
            int sx = ((chunkX + i * 4) << 4) + 8, sz = (chunkZ << 4) + 8;
            i++;
            var bp = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(building, level,
                new BlockPos(sx, y0, sz), Rotation.NONE, "gametest_pockets", false);
            if (bp == null || bp.getBlocks().isEmpty()) {
                problems.add(building.structureName + ": no placement");
                continue;
            }
            BlockPos min = bp.minCorner, max = bp.maxCorner;
            if (max.getX() - min.getX() > 40 || max.getZ() - min.getZ() > 40) {
                problems.add(building.structureName + ": footprint too big for its pad");
                continue;
            }
            // the pad: stone at the structure's ground layer (its y=0 is the foundation, in the ground) and below,
            // air above; the structure stamped over it (flag 2: no neighbour updates, nothing pops off)
            int gy = min.getY();
            for (int x = min.getX() - margin; x <= max.getX() + margin; x++)
                for (int z = min.getZ() - margin; z <= max.getZ() + margin; z++) {
                    level.setBlock(new BlockPos(x, gy - 1, z), Blocks.STONE.defaultBlockState(), 2);
                    level.setBlock(new BlockPos(x, gy, z), Blocks.STONE.defaultBlockState(), 2);
                    for (int y = gy + 1; y <= max.getY() + 3; y++)
                        level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            for (var b : bp.getBlocks())
                if (b.getBlockPos().getY() > gy || !b.getBlockState().isAir())   // a sparse ground layer keeps the terrain
                    level.setBlock(b.getBlockPos(), b.getBlockState(), 2);
            checked++;
            PocketAudit audit = new PocketAudit(level, min, max, gy + 1, max.getY() + 2);
            // (a) walked into, never out
            for (BlockPos c : audit.entered())
                if (!audit.escapes(c)) {
                    problems.add(building.structureName + ": a unit can walk into " + audit.rel(c) + " but not out");
                    break;
                }
            // (b) the spawn point
            if (bp instanceof com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement pp) {
                BlockPos spawn = com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement
                    .safeSpawnPoint(level, pp.getIndoorSpawnPoint(level), null);
                byte kind = com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder.classify(level,
                    spawn.getX(), spawn.getY(), spawn.getZ());
                if (kind != com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder.KIND_WATER
                        && !audit.escapes(spawn))
                    problems.add(building.structureName + ": units spawn at " + audit.rel(spawn) + " and can't leave");
            }
            // (c) closed ground cells of our own structures
            if (GENERATED_STRUCTURES.contains(building.structureName)) {
                for (int x = min.getX(); x <= max.getX(); x++)
                    for (int z = min.getZ(); z <= max.getZ(); z++)
                        for (int y = gy + 1; y <= gy + 2; y++) {
                            BlockPos c = new BlockPos(x, y, z);
                            if (com.solegendary.reignofnether.unit.UnitUnstick.isWalkable(level, c)
                                    && !audit.escapes(c) && !audit.doorBeside(c)) {
                                problems.add(building.structureName + ": ground cell " + audit.rel(c) + " has no way out");
                                x = max.getX() + 1; z = max.getZ() + 1;   // one per structure is enough
                                break;
                            }
                        }
            }
        }
        if (checked == 0)
            problems.add("bucket " + bucket + " holds no structure");
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }

    /**
     * Reachability over a stamped structure by the grid's moves for a 1-wide, 2-tall unit. {@link #escapes} answers
     * whether a cell can reach the open pad outside the footprint; {@link #entered} is every cell the outside can
     * reach. Memoised per cell; a structure has a few hundred walkable cells, so the worst case is well under a tick.
     */
    private static final class PocketAudit {
        final ServerLevel level;
        final BlockPos min, max;
        final int loY, hiY;
        final java.util.Map<Long, Boolean> escapeMemo = new java.util.HashMap<>();

        PocketAudit(ServerLevel level, BlockPos min, BlockPos max, int loY, int hiY) {
            this.level = level; this.min = min; this.max = max; this.loY = loY; this.hiY = hiY;
        }

        String rel(BlockPos c) {
            return (c.getX() - min.getX()) + "/" + (c.getY() - min.getY()) + "/" + (c.getZ() - min.getZ());
        }

        boolean outside(BlockPos c) {
            return c.getX() < min.getX() || c.getX() > max.getX() || c.getZ() < min.getZ() || c.getZ() > max.getZ();
        }

        boolean inBounds(BlockPos c) {
            return c.getX() >= min.getX() - 3 && c.getX() <= max.getX() + 3 && c.getZ() >= min.getZ() - 3
                && c.getZ() <= max.getZ() + 3 && c.getY() >= loY && c.getY() <= hiY;
        }

        boolean doorBeside(BlockPos c) {
            for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL)
                for (int dy = 0; dy <= 1; dy++)
                    if (level.getBlockState(c.relative(d).above(dy)).getBlock() instanceof net.minecraft.world.level.block.DoorBlock)
                        return true;
            return false;
        }

        /** The grid's moves out of a cell (GridAStar's neighbour loop and fall pass for a 1-wide, 2-tall body). */
        List<BlockPos> moves(BlockPos c) {
            List<BlockPos> out = new ArrayList<>(8);
            int x = c.getX(), y = c.getY(), z = c.getZ();
            boolean riseOk = !com.solegendary.reignofnether.unit.UnitUnstick.bodyBlocked(level, new BlockPos(x, y + 2, z));
            for (int d = 0; d < 8; d++) {
                int dx = com.solegendary.reignofnether.unit.pathfinding.GridNeighbors.DX[d];
                int dz = com.solegendary.reignofnether.unit.pathfinding.GridNeighbors.DZ[d];
                int nx = x + dx, nz = z + dz;
                boolean diag = dx != 0 && dz != 0;
                if (walkable(nx, y, nz) && !diagonalBlocked(x, y, z, dx, dz)) { out.add(new BlockPos(nx, y, nz)); continue; }
                if (!diag && riseOk && walkable(nx, y + 1, nz)) { out.add(new BlockPos(nx, y + 1, nz)); continue; }
                if (walkable(nx, y - 1, nz) && !diagonalBlocked(x, y - 1, z, dx, dz)) { out.add(new BlockPos(nx, y - 1, nz)); continue; }
                if (blocked(nx, y, nz) || blocked(nx, y + 1, nz) || blocked(nx, y - 1, nz) || diagonalBlocked(x, y, z, dx, dz))
                    continue;
                for (int fy = y - 2; fy >= y - com.solegendary.reignofnether.unit.pathfinding.PathfinderConfig.MAX_FALL_DROP; fy--) {
                    if (blocked(nx, fy + 1, nz))
                        break;
                    if (walkable(nx, fy, nz)) { out.add(new BlockPos(nx, fy, nz)); break; }
                }
            }
            return out;
        }

        boolean walkable(int x, int y, int z) {
            return com.solegendary.reignofnether.unit.UnitUnstick.isWalkable(level, new BlockPos(x, y, z));
        }

        boolean blocked(int x, int y, int z) {
            return com.solegendary.reignofnether.unit.UnitUnstick.bodyBlocked(level, new BlockPos(x, y, z));
        }

        boolean diagonalBlocked(int x, int toY, int z, int dx, int dz) {
            return dx != 0 && dz != 0 && (!walkable(x + dx, toY, z) || !walkable(x, toY, z + dz));
        }

        /** Whether the open pad outside the footprint can be reached from {@code start}. */
        boolean escapes(BlockPos start) {
            Boolean memo = escapeMemo.get(start.asLong());
            if (memo != null)
                return memo;
            Set<Long> visited = new HashSet<>();
            java.util.ArrayDeque<BlockPos> stack = new java.util.ArrayDeque<>();
            visited.add(start.asLong());
            stack.push(start);
            boolean found = false;
            while (!stack.isEmpty() && !found) {
                BlockPos c = stack.pop();
                if (outside(c) || Boolean.TRUE.equals(escapeMemo.get(c.asLong()))) { found = true; break; }
                for (BlockPos n : moves(c))
                    if (inBounds(n) && visited.add(n.asLong()))
                        stack.push(n);
            }
            escapeMemo.put(start.asLong(), found);
            if (!found)   // nothing reachable from a trapped cell gets out either
                for (long v : visited)
                    escapeMemo.put(v, false);
            return found;
        }

        /** Every cell inside the footprint the outside ring can walk to. */
        List<BlockPos> entered() {
            Set<Long> visited = new HashSet<>();
            java.util.ArrayDeque<BlockPos> stack = new java.util.ArrayDeque<>();
            for (int x = min.getX() - 1; x <= max.getX() + 1; x++)
                for (int z = min.getZ() - 1; z <= max.getZ() + 1; z++) {
                    BlockPos c = new BlockPos(x, loY, z);
                    if (outside(c) && walkable(x, loY, z) && visited.add(c.asLong()))
                        stack.push(c);
                }
            List<BlockPos> inside = new ArrayList<>();
            while (!stack.isEmpty()) {
                BlockPos c = stack.pop();
                if (!outside(c))
                    inside.add(c);
                for (BlockPos n : moves(c))
                    if (inBounds(n) && visited.add(n.asLong()))
                        stack.push(n);
            }
            return inside;
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // HUD pass 4 (bar-hud-4): BAR fire state, kill counters and the kill feed
    // ------------------------------------------------------------------------------------------------------------

    /** Two Vindicators of different owners, a few blocks apart, both set to the given stances. */
    private static com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit[] stancePair(
            GameTestHelper helper, String ownerA, String ownerB,
            com.solegendary.reignofnether.unit.EngagementStance stanceA,
            com.solegendary.reignofnether.unit.EngagementStance stanceB, double gap) {
        ServerLevel level = helper.getLevel();
        var a = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        var b = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (a == null || b == null)
            return null;
        BlockPos at = helper.absolutePos(new BlockPos(5, 2, 8));
        a.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        b.moveTo(at.getX() + 0.5 + gap, at.getY(), at.getZ() + 0.5, 0, 0);
        a.setOwnerName(ownerA);
        b.setOwnerName(ownerB);
        com.solegendary.reignofnether.unit.EngagementStance.set(a, stanceA);
        com.solegendary.reignofnether.unit.EngagementStance.set(b, stanceB);
        level.addFreshEntity(a);
        level.addFreshEntity(b);
        return new com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit[] { a, b };
    }

    /**
     * Hold fire: an idle-aggressive Vindicator with an enemy three blocks away, then hit by it, never picks a target
     * (idle aggro, retaliation and retargeting are all off). The enemy holds fire too so it cannot start the fight.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void hold_fire_never_acquires_a_target(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var pair = stancePair(helper, "gametest_holdfire_a", "gametest_holdfire_b",
            com.solegendary.reignofnether.unit.EngagementStance.HOLD_FIRE,
            com.solegendary.reignofnether.unit.EngagementStance.HOLD_FIRE, 3);
        if (pair == null) {
            helper.fail("could not create vindicator units");
            return;
        }
        var a = pair[0];
        var b = pair[1];
        helper.runAfterDelay(20, () -> a.hurt(level.damageSources().indirectMagic(b, b), 1f));
        helper.runAfterDelay(90, () -> {
            var tg = a.getTargetGoal().getTarget();
            if (tg != null || a.getTarget() != null)
                helper.fail("a Hold fire unit acquired " + describe(tg != null ? tg : a.getTarget())
                    + " (stance now " + com.solegendary.reignofnether.unit.EngagementStance.of(a) + ")");
            if (com.solegendary.reignofnether.unit.EngagementStance.of(a) != com.solegendary.reignofnether.unit.EngagementStance.HOLD_FIRE)
                helper.fail("stance did not stick: " + com.solegendary.reignofnether.unit.EngagementStance.of(a));
            a.discard();
            b.discard();
            helper.succeed();
        });
    }

    /**
     * Return fire: the same pair, the unit does nothing about an enemy standing in aggro range, but once that enemy
     * hits it, it turns on its attacker.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void return_fire_acquires_its_attacker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var pair = stancePair(helper, "gametest_returnfire_a", "gametest_returnfire_b",
            com.solegendary.reignofnether.unit.EngagementStance.RETURN_FIRE,
            com.solegendary.reignofnether.unit.EngagementStance.HOLD_FIRE, 4);
        if (pair == null) {
            helper.fail("could not create vindicator units");
            return;
        }
        var a = pair[0];
        var b = pair[1];
        boolean[] done = { false };
        helper.runAfterDelay(40, () -> {
            if (a.getTargetGoal().getTarget() != null) {
                done[0] = true;
                helper.fail("a Return fire unit acquired " + describe(a.getTargetGoal().getTarget()) + " unprovoked");
                return;
            }
            a.hurt(level.damageSources().indirectMagic(b, b), 1f);
        });
        for (int t = 44; t <= 120; t += 4) {
            helper.runAfterDelay(t, () -> {
                if (done[0])
                    return;
                if (a.getTargetGoal().getTarget() == b) {
                    done[0] = true;
                    a.discard();
                    b.discard();
                    helper.succeed();
                }
            });
        }
        helper.runAfterDelay(124, () -> {
            if (done[0])
                return;
            done[0] = true;
            helper.fail("a Return fire unit did not turn on its attacker (target " + describe(a.getTargetGoal().getTarget())
                + ", last damage source " + (a.getLastDamageSource() == null ? "null" : describe(a.getLastDamageSource().getEntity()))
                + ", idle " + a.isIdle() + ")");
        });
    }

    /** UnitKills: one enemy kill is one on the killer's counter, and both it and the stance go into the save data. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void kill_counter_increments_on_a_kill(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var pair = stancePair(helper, "gametest_kills_a", "gametest_kills_b",
            com.solegendary.reignofnether.unit.EngagementStance.HOLD_FIRE,
            com.solegendary.reignofnether.unit.EngagementStance.HOLD_FIRE, 2);
        if (pair == null) {
            helper.fail("could not create vindicator units");
            return;
        }
        var a = pair[0];
        var b = pair[1];
        helper.runAfterDelay(3, () -> {
            int before = com.solegendary.reignofnether.unit.UnitKills.get(a);
            b.hurt(level.damageSources().indirectMagic(a, a), 10000f);
            if (!b.isDeadOrDying()) {
                helper.fail("foe survived a 10000 hit (hp " + b.getHealth() + ")");
                return;
            }
            int after = com.solegendary.reignofnether.unit.UnitKills.get(a);
            if (after != before + 1)
                helper.fail("one enemy kill should add one: " + before + " -> " + after);
            // save round trip
            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
            a.addAdditionalSaveData(tag);
            if (!tag.contains(com.solegendary.reignofnether.unit.UnitKills.KEY)
                    || tag.getInt(com.solegendary.reignofnether.unit.UnitKills.KEY) != after)
                helper.fail("kills not in the unit's save data: " + tag.get(com.solegendary.reignofnether.unit.UnitKills.KEY));
            com.solegendary.reignofnether.unit.EngagementStance.set(a, com.solegendary.reignofnether.unit.EngagementStance.RETURN_FIRE);
            tag = new net.minecraft.nbt.CompoundTag();
            a.addAdditionalSaveData(tag);
            if (tag.getInt(com.solegendary.reignofnether.unit.EngagementStance.KEY)
                    != com.solegendary.reignofnether.unit.EngagementStance.RETURN_FIRE.ordinal())
                helper.fail("stance not in the unit's save data: " + tag.get(com.solegendary.reignofnether.unit.EngagementStance.KEY));
            a.discard();
            b.discard();
            helper.succeed();
        });
    }

    /**
     * Kill feed: a kill queues a line naming both types and owners, and the recipient test admits the killer's
     * owner (and a spectator with fog off) for it. The packet itself needs a connected client, so this checks the
     * server half right up to the per-player gate; recentEntries is filtered by this test's own owner names.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void kill_feed_line_queued_for_the_viewer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String ownerA = "gametest_feed_a", ownerB = "gametest_feed_b";
        var pair = stancePair(helper, ownerA, ownerB,
            com.solegendary.reignofnether.unit.EngagementStance.HOLD_FIRE,
            com.solegendary.reignofnether.unit.EngagementStance.HOLD_FIRE, 2);
        if (pair == null) {
            helper.fail("could not create vindicator units");
            return;
        }
        var a = pair[0];
        var b = pair[1];
        helper.runAfterDelay(3, () -> {
            b.hurt(level.damageSources().indirectMagic(a, a), 10000f);
            if (!b.isDeadOrDying()) {
                helper.fail("foe survived a 10000 hit (hp " + b.getHealth() + ")");
                return;
            }
            com.solegendary.reignofnether.hud.KillFeedClientboundPacket.Entry line = null;
            for (var e : com.solegendary.reignofnether.hud.KillFeedServerEvents.recentEntries())
                if (ownerA.equals(e.killerOwner()) && ownerB.equals(e.victimOwner()))
                    line = e;
            if (line == null) {
                helper.fail("no kill feed line queued for the kill");
                return;
            }
            String vindicator = a.getType().getDescriptionId();
            if (!vindicator.equals(line.killerKey()) || !vindicator.equals(line.victimKey()) || line.building())
                helper.fail("kill feed line names the wrong things: " + line);
            var viewer = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(ownerA.getBytes()), ownerA));
            if (!com.solegendary.reignofnether.hud.KillFeedServerEvents.canSee(viewer, line.killerOwner(), line.victimOwner(),
                    b.getBlockX(), b.getBlockZ()))
                helper.fail("the killer's owner must receive its own kill");
            var stranger = net.minecraftforge.common.util.FakePlayerFactory.get(level,
                new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes("gametest_feed_c".getBytes()), "gametest_feed_c"));
            boolean fog = com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isFogActiveFor(stranger);
            boolean sees = com.solegendary.reignofnether.hud.KillFeedServerEvents.canSee(stranger, line.killerOwner(),
                line.victimOwner(), b.getBlockX(), b.getBlockZ());
            if (!fog && !sees)
                helper.fail("with fog off a third player sees every kill");
            a.discard();
            b.discard();
            helper.succeed();
        });
    }
}
