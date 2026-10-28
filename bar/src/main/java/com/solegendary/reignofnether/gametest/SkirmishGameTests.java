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
     * A patch stamped on a river bank moves to dry ground (lovish's log, Oct 13 23:00: four bots "blocked on
     * metal_extractor: no valid footprint" all match - the pad leveller leaves water alone, so a wet 7x7 pad never
     * fits a 5x5 extractor). Grass platform at y=200 with a 3-wide, 1-deep river whose near edge is 2 blocks east of
     * the requested centre: the patch must land somewhere its 7x7 pad is dry, within MAX_DRY_RELOCATE, and an
     * extractor must then fit on it. Own platform 400 blocks south of the other patch test's.
     */
    @GameTest(template = ARENA)
    public static void patch_stamped_on_a_river_bank_moves_to_dry_ground(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 200;
        int cx = base.getX(), cz = base.getZ() + 400;
        level.getChunk(cx >> 4, cz >> 4);
        for (int dx = -20; dx <= 20; dx++)
            for (int dz = -20; dz <= 20; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), Blocks.DIRT.defaultBlockState(), 3);
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        for (int dx = 2; dx <= 4; dx++)   // the river: x = cx+2..cx+4, full depth of the platform
            for (int dz = -20; dz <= 20; dz++)
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.WATER.defaultBlockState(), 3);
        MetalPatches.forgetAll();
        MetalPatches.stamp(level, cx, cz);
        List<BlockPos> patches = MetalPatches.getPatches(level);
        BlockPos centre;
        synchronized (patches) {
            centre = patches.isEmpty() ? null : patches.get(patches.size() - 1);
        }
        try {
            if (centre == null) {
                helper.fail("stamp() recorded no patch");
                return;
            }
            if (!level.getBlockState(centre).is(MetalPatches.PATCH_BLOCK)) {
                helper.fail("no patch block at the recorded centre " + centre);
                return;
            }
            int moved = Math.max(Math.abs(centre.getX() - cx), Math.abs(centre.getZ() - cz));
            if (moved == 0 || moved > MetalPatches.MAX_DRY_RELOCATE) {
                helper.fail("a bank patch should move 1.." + MetalPatches.MAX_DRY_RELOCATE + " blocks to dry ground, moved " + moved + " to " + centre);
                return;
            }
            if (!MetalPatches.padDry(level, centre.getX(), centre.getZ(), centre.getY())) {
                helper.fail("the relocated patch's pad is still wet at " + centre);
                return;
            }
            BlockPos origin = new BlockPos(centre.getX() - 2, centre.getY(), centre.getZ() - 2);
            String err = BuildingValidators.getPlacementValidityError(level, Buildings.METAL_EXTRACTOR_VILLAGERS, origin,
                    "tester", Rotation.NONE, false, false, true);
            if (err != null)
                helper.fail("an extractor is refused on the relocated patch at " + centre + ": " + err);
            else
                helper.succeed();
        } finally {
            MetalPatches.forgetAll();
        }
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
        // the pad and the two animals sit just outside the Temple of the Sun's 15-wide footprint (Oct 19; it was 11)
        for (int dx = -10; dx <= 10; dx++)
            for (int dz = -10; dz <= 10; dz++) {
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
        bear.moveTo(cx + 9.5, y + 1, cz + 0.5, 0, 0);
        pig.moveTo(cx - 9.5, y + 1, cz + 0.5, 0, 0);
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

    /** Energy converters only burn energy above half storage, at TierGate's 100 energy per metal, 50 energy/s each. */
    @GameTest(template = ARENA)
    public static void energy_converter_turns_spare_energy_into_metal(GameTestHelper helper) {
        var eco = com.solegendary.reignofnether.resources.EconomyServerEvents.getEconomy("gametest_alchemist");
        eco.conversionCapacity = 50f;
        var res = new com.solegendary.reignofnether.resources.Resources("gametest_alchemist", 0, 0, 0);
        res.addEnergy(900);
        for (int i = 0; i < 20; i++)
            com.solegendary.reignofnether.resources.EconomyServerEvents.convertEnergy(res, eco);
        if (Math.abs(res.getMetal() - 0.5f) > 0.05f || Math.abs(res.getEnergy() - 850f) > 1f)
            helper.fail("one second of one converter: expected ~0.5 metal / 850 energy, got " + res.getMetal()
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
        // this slot's own owner: a pool left under a shared name (a run that failed before its clean-up) took the
        // mine's metal first (EconomyServerEvents.addReclaimedMetal pays the first pool with the name)
        String owner = "gametest_captor_" + helper.absolutePos(BlockPos.ZERO).asLong();
        var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 0, 0);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
        var unit = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        // every way out takes the site, the captor and the pool away (helper.fail throws: nothing after it runs)
        Runnable cleanUp = () -> {
            if (unit != null)
                unit.discard();
            if (point != null)
                point.discard();
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
        };
        if (point == null || unit == null) {
            cleanUp.run();
            helper.fail("could not set up the capture point");
            return;
        }
        // on the site's own plinth (the marker stands one above it), not on the pad: place() builds on the column's
        // solidTop, and anything solid left above the pad (an earlier batch's test on this arena slot, an earlier local
        // run's ruin) lifted the site out of RADIUS of a captor stood at y+1 - "progress 0.0", PR #200 run 40255634660
        unit.moveTo(cx + 2.5, point.getY(), cz + 0.5, 0, 0);
        unit.setOwnerName(owner);
        level.addFreshEntity(unit);
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            for (int i = 0; i < 25; i++)
                com.solegendary.reignofnether.startpos.CapturePointServerEvents.tick(level, 1f);
            if (!owner.equals(com.solegendary.reignofnether.startpos.CapturePointServerEvents.ownerOf(point)))
                problems.add("25 uncontested seconds did not capture the site (progress "
                    + com.solegendary.reignofnether.startpos.CapturePointServerEvents.progressOf(point) + "; site listed "
                    + com.solegendary.reignofnether.startpos.CapturePointServerEvents.getPoints().contains(point)
                    + ", removed " + point.isRemoved() + ", claimant '"
                    + com.solegendary.reignofnether.startpos.CapturePointServerEvents.claimantOf(point) + "', site at "
                    + point.blockPosition().toShortString() + "; captor alive " + unit.isAlive() + ", removed "
                    + unit.isRemoved() + ", owner '" + unit.getOwnerName() + "', at " + unit.blockPosition().toShortString()
                    + ", " + TerrainGameTests.f(Math.sqrt(unit.distanceToSqr(point))) + " from the site, in the unit list "
                    + com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits().contains(unit) + "; units near: "
                    + com.solegendary.reignofnether.unit.UnitGrid.near(level, point.getX(), point.getZ(), 8, new ArrayList<>())
                        .stream().map(le -> le.getType().toShortString() + "/" + ((com.solegendary.reignofnether.unit.interfaces.Unit) le).getOwnerName()
                            + "@" + le.blockPosition().toShortString()).toList() + ")");
            if (pool.getMetal() <= 0)
                problems.add("the captured Ancient Mine paid no metal");
            try {
                TerrainGameTests.finish(helper, problems);
            } finally {
                cleanUp.run();
            }
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
     * Geomancers, the Thunder Sage with Heaven's Judgement and Blink, a Jade Pagoda, 3+ buildings) as a fifth base - and
     * a Brassworks bot as a sixth when a sixth dry seat is found beside the five (its Foundry Hall must be laid; with no
     * seat it sits this one out rather than failing the soak, and its own tests cover the faction). The sides play as
     * teams (Sunforged + Gravebound, Court + Tide, Storm + Brass), and the allied hard bot must have an army coming
     * by the end: a lab placed, and a fighter trained or a lab finished.
     */
    @GameTest(template = ARENA, timeoutTicks = SOAK_TICKS + 600)
    public static void bots_play_each_other_without_errors(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String kingdom = "gametest_soak_kingdom", grave = "gametest_soak_grave", court = "gametest_soak_court",
            tide = "gametest_soak_tide", storm = "gametest_soak_storm", brass = "gametest_soak_brass";
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        // the first three in a row as before; the fourth (Tidewrought) beside them - asking for four in one row made
        // a dry strip 480 blocks long, which an ocean seed often doesn't have
        // the fifth (Stormcallers) base beside the four, the same way (clear of all four). Every dry row is tried until
        // one has both seats beside it (soakFiveStarts) - the first row alone sat on a coast once (red, Oct 16)
        int[] rowsTried = { 0 };
        net.minecraft.world.phys.Vec3[] five = soakFiveStarts(level, origin.getX() + 1500, origin.getZ(), rowsTried);
        if (five == null) {
            helper.fail("no dry ground for five bot bases near x=" + (origin.getX() + 1500) + " (" + rowsTried[0]
                + " dry rows of three found, none with two more dry seats beside them)");
            return;
        }
        net.minecraft.world.phys.Vec3[] row = { five[0], five[1], five[2] };
        net.minecraft.world.phys.Vec3 extra = five[3], extra2 = five[4];
        // the sixth (Brassworks) seat beside the five, if there is one
        net.minecraft.world.phys.Vec3 extra3 = soakExtraStart(level, new net.minecraft.world.phys.Vec3[] { row[0], row[1], row[2], extra, extra2 });
        List<net.minecraft.world.phys.Vec3> startList = new ArrayList<>(List.of(row[0], row[1], row[2], extra, extra2));
        var bots = com.solegendary.reignofnether.bot.BotServerEvents.brains;
        var failures = com.solegendary.reignofnether.bot.BotServerEvents.thinkFailures;
        List<String> names = new ArrayList<>(List.of(kingdom, grave, court, tide, storm));
        List<com.solegendary.reignofnether.faction.Faction> factions = new ArrayList<>(List.of(com.solegendary.reignofnether.faction.Factions.VILLAGERS,
            com.solegendary.reignofnether.faction.Factions.MONSTERS, com.solegendary.reignofnether.faction.Factions.VERDANT_COURT,
            com.solegendary.reignofnether.faction.Factions.TIDEWROUGHT, com.solegendary.reignofnether.faction.Factions.STORMCALLERS));
        final boolean brassSeated = extra3 != null;
        if (brassSeated) {
            startList.add(extra3);
            names.add(brass);
            factions.add(com.solegendary.reignofnether.faction.Factions.BRASSWORKS);
        }
        net.minecraft.world.phys.Vec3[] starts = startList.toArray(new net.minecraft.world.phys.Vec3[0]);
        final int count = names.size();
        // the Sunforged bot plays on hard: it starts with its metal pool full (STARTING_METAL = the default storage,
        // so every bot starts "floating"), and the soak measures how it spends that down - see the checks at the end
        for (int i = 0; i < count; i++) {
            failures.remove(names.get(i));
            com.solegendary.reignofnether.player.PlayerServerEvents.startRTSBot(level, names.get(i), starts[i],
                factions.get(i), 0);
            bots.put(names.get(i), new com.solegendary.reignofnether.bot.BotPlayer(names.get(i), factions.get(i),
                i == 0 ? com.solegendary.reignofnether.bot.BotPlayer.Difficulty.HARD
                       : com.solegendary.reignofnether.bot.BotPlayer.Difficulty.MEDIUM, BlockPos.containing(starts[i])));
        }
        for (String n : names)
            if (!com.solegendary.reignofnether.player.PlayerServerEvents.isRTSPlayer(n))
                helper.fail("startRTSBot did not add " + n + " to the match");
        // a team lineup, not a free-for-all: the hard Sunforged bot plays as the Gravebound's ally (lovish's Oct 13
        // 2v2 had the ally hard bot train 30 workers and not one fighter for 13 minutes), the Court with the Tide,
        // the Stormcallers with the Brassworks. An allied bot must still raise its lab and field an army (checked
        // at the end), and nothing in the brain may treat an ally as a target or a threat
        List<String[]> teams = new ArrayList<>(List.of(new String[] { kingdom, grave }, new String[] { court, tide }));
        if (brassSeated)
            teams.add(new String[] { storm, brass });
        for (String[] team : teams)
            com.solegendary.reignofnether.alliance.AlliancesServerEvents.addAlliance(team[0], team[1]);
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
        int[] maxBuildings = new int[count], maxUnits = new int[count], labs = new int[count], builtLabs = new int[count];
        // per bot: the highest army value (summed metal cost of its fighters) seen, and the metal fill at the end
        float[] maxArmy = new float[count], metalFill = new float[count], energyFill = new float[count];
        boolean[] courtHall = { false }, tideHarbour = { false }, stormPagoda = { false }, brassHall = { false };
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
            if (brassSeated)
                for (var bp : soakBuildings(brass))
                    if (bp.getBuilding() == Buildings.FOUNDRY_HALL)
                        brassHall[0] = true;
            for (int i = 0; i < count; i++) {
                List<com.solegendary.reignofnether.building.BuildingPlacement> bs = soakBuildings(names.get(i));
                List<net.minecraft.world.entity.LivingEntity> us = soakUnits(names.get(i));
                maxBuildings[i] = Math.max(maxBuildings[i], bs.size());
                maxUnits[i] = Math.max(maxUnits[i], us.size());
                var kit = com.solegendary.reignofnether.bot.BotPlayer.kitFor(factions.get(i));
                int n = 0, nBuilt = 0;
                for (var bp : bs)
                    if (kit != null && bp.getBuilding() == kit.armyBuilding()) {
                        n++;
                        if (bp.isBuilt)
                            nBuilt++;
                    }
                labs[i] = Math.max(labs[i], n);
                builtLabs[i] = Math.max(builtLabs[i], nBuilt);
                float v = 0;
                for (var le : us)
                    if (le instanceof com.solegendary.reignofnether.unit.interfaces.AttackerUnit
                            && !(le instanceof com.solegendary.reignofnether.unit.interfaces.WorkerUnit)
                            && le instanceof com.solegendary.reignofnether.unit.interfaces.Unit u && u.getCost() != null)
                        v += u.getCost().metal();
                maxArmy[i] = Math.max(maxArmy[i], v);
                var eco = com.solegendary.reignofnether.resources.EconomyServerEvents.getEconomy(names.get(i));
                for (var r : com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList)
                    if (r.ownerName.equals(names.get(i))) {
                        metalFill[i] = r.getMetal() / Math.max(1f, eco.metalStorage);
                        energyFill[i] = r.getEnergy() / Math.max(1f, eco.energyStorage);
                    }
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
            if (brassSeated && !brassHall[0])
                problems.add(brass + " never laid its Foundry Hall");
            // the hard bot started floating (metal at the storage cap): four minutes on it must have spent that down
            // under BotTuning.FLOAT_FRAC of storage, or at least turned it into a second army lab
            // (0.75, not FLOAT_FRAC itself: the Oct 11 main run ended at exactly 0.60 after the bot's workers died to
            // wildlife - the measurement is the [Soak] notice, this only catches a bot that never spends at all)
            if (metalFill[0] >= 0.75f && labs[0] < 2)
                problems.add(kingdom + " (hard) still floats: metal at " + String.format("%.2f", metalFill[0])
                    + " of storage with " + labs[0] + " lab(s) after " + SOAK_TICKS + " ticks (army state "
                    + (bots.get(kingdom) == null ? "?" : bots.get(kingdom).army.state) + ")");
            // the ally hard bot has an army on the way: a lab placed, and by four minutes either a fighter trained
            // (army value above a Militia's 50 metal at some sample) or at least a lab finished (the Oct 13 ally bot:
            // 13 minutes, 30 workers, no lab, army 0; its log now says why - BotPlayer.logNoArmy)
            if (labs[0] < 1 || (maxArmy[0] < 50 && builtLabs[0] < 1))
                problems.add(kingdom + " (hard, allied) raised no army in " + SOAK_TICKS + " ticks: labs " + labs[0]
                    + " (built " + builtLabs[0] + "), army value max " + (int) maxArmy[0] + ", lab slot shift "
                    + (bots.get(kingdom) == null ? "?" : bots.get(kingdom).labSlotShift) + ", blocked "
                    + (bots.get(kingdom) == null ? "?" : bots.get(kingdom).lastBlockedReason));
            for (int i = 0; i < count; i++) {
                String n = names.get(i);
                Throwable err = failures.get(n);
                if (err != null)
                    problems.add(n + " think threw " + err);
                // capitol + at least two more: a bot that stops after its capitol has stalled (the old bad-patch bug)
                if (maxBuildings[i] < 3 || maxUnits[i] <= startUnits[i])
                    problems.add(n + " stalled (buildings " + maxBuildings[i] + ", units "
                        + startUnits[i] + " -> " + maxUnits[i] + ", blocked "
                        + (bots.get(n) == null ? "?" : bots.get(n).lastBlockedReason) + ")");
            }
            // (the CI workflow folds these lines into a notice, so the numbers are readable through the API)
            for (int i = 0; i < count; i++)
                ReignOfNether.LOGGER.info("[Soak] after {} ticks: {} {} buildings {} labs {} units {} (start {}) armyValue max {} metalFill end {} energyFill end {} army {} placeFailures {} lastFailed {} lastPlaced t{} sitesCancelled {} blocked {}",
                    SOAK_TICKS, names.get(i).substring(14), i == 0 ? "hard" : "medium", maxBuildings[i], labs[i], maxUnits[i],
                    startUnits[i], (int) maxArmy[i], String.format("%.2f", metalFill[i]), String.format("%.2f", energyFill[i]),
                    bots.get(names.get(i)) == null ? "?" : bots.get(names.get(i)).army.state,
                    bots.get(names.get(i)) == null ? "?" : bots.get(names.get(i)).placeFailures,
                    bots.get(names.get(i)) == null || bots.get(names.get(i)).lastFailed == null ? "-" : bots.get(names.get(i)).lastFailed.structureName,
                    bots.get(names.get(i)) == null ? "?" : bots.get(names.get(i)).lastPlacedAt - t0,
                    bots.get(names.get(i)) == null ? "?" : bots.get(names.get(i)).siteWatch.cancelled,
                    bots.get(names.get(i)) == null ? "?" : bots.get(names.get(i)).lastBlockedReason);
            // clean up: leave the match, forget the brains, drop the armies (buildings stay, ownerless and far away)
            List<net.minecraft.world.entity.LivingEntity> leftovers = new ArrayList<>();
            for (String n : names)
                leftovers.addAll(soakUnits(n));
            for (String n : names) {
                com.solegendary.reignofnether.player.PlayerServerEvents.defeat(n, "gametest finished");
                bots.remove(n);
                failures.remove(n);
            }
            for (String[] team : teams)
                com.solegendary.reignofnether.alliance.AlliancesServerEvents.removeAlliance(team[0], team[1]);
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

    /** The seed is random per CI run, so one line of x can be all ocean: a few parallel lines are tried. */
    static int[] soakLines(int z) {
        return new int[] { z, z + 480, z - 480, z + 960, z - 960, z + 1440, z - 1440, z + 1920 };
    }

    /** {@code count} dry, fairly flat base sites 160 blocks apart in a row along z from (bx, bz); null if any is not. */
    static net.minecraft.world.phys.Vec3[] soakRow(ServerLevel level, int bx, int bz, int count) {
        net.minecraft.world.phys.Vec3[] out = new net.minecraft.world.phys.Vec3[count];
        for (int k = 0; k < count; k++) {
            out[k] = soakDrySpot(level, bx, bz + 160 * k);
            if (out[k] == null)
                return null;
        }
        return out;
    }

    /**
     * A dry row of {@code count} base sites ({@link #soakRow}) near (x, z): every candidate row (16 steps of 96 blocks
     * along each of {@link #soakLines}) is tried, the same scan {@link #soakFiveStarts} makes, so a test that wants only
     * a row finds ground wherever the soak does. Trying just the first step of each line failed the tier-2 bot test every
     * run (the world is the same each time, and that strip is water or cliff); null only if no candidate row is dry.
     */
    static net.minecraft.world.phys.Vec3[] soakAnyRow(ServerLevel level, int x, int z, int count) {
        for (int bz : soakLines(z))
            for (int step = 0; step < 16; step++) {
                net.minecraft.world.phys.Vec3[] row = soakRow(level, x + step * 96, bz, count);
                if (row != null)
                    return row;
            }
        return null;
    }

    /**
     * Five base sites for the soak: a dry row of three ({@link #soakRow}) and two more seats beside it
     * ({@link #soakExtraStart}). Every candidate row (16 steps of 96 blocks along each of {@link #soakLines}) is
     * tried until one has both seats - stopping at the first row failed a build when that row sat on a coast (Oct 16,
     * "no dry ground for five bot bases near x=1528"). {@code rowsTried[0]} counts the dry rows found, for the
     * failure message.
     */
    static net.minecraft.world.phys.Vec3[] soakFiveStarts(ServerLevel level, int x, int z, int[] rowsTried) {
        for (int bz : soakLines(z))
            for (int step = 0; step < 16; step++) {
                net.minecraft.world.phys.Vec3[] row = soakRow(level, x + step * 96, bz, 3);
                if (row == null)
                    continue;
                rowsTried[0]++;
                net.minecraft.world.phys.Vec3 extra = soakExtraStart(level, row);
                if (extra == null)
                    continue;
                net.minecraft.world.phys.Vec3 extra2 = soakExtraStart(level,
                    new net.minecraft.world.phys.Vec3[] { row[0], row[1], row[2], extra });
                if (extra2 != null)
                    return new net.minecraft.world.phys.Vec3[] { row[0], row[1], row[2], extra, extra2 };
            }
        return null;
    }

    /**
     * One more dry base site next to a row from {@link #soakFiveStarts}: tried beside each base (160 blocks off to either
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
                // generate it, or the heightmap reads the void (forests: ignore leaves, or tree tops read as 10-block
                // cliffs) - bounded (GameTestChunks): getChunk could park the server thread for good on CI
                GameTestChunks.loadNowOrFail(level, (x + dx) >> 4, (z + dz) >> 4);
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
    // Its own batch (code_review CR-15): in the default batches it shared the server with ~100 other tests, whose
    // units (often 1000-2000 of them, e.g. the bot soak armies) every per-unit system below also walked - so the
    // "400-unit" figures moved with whatever else happened to be alive, and every branch that added tests got slower.
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "stress_8v8")
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
        // async FORCED tickets and a bounded wait: setChunkForced generated each chunk synchronously on the server
        // thread with no way out (CI hangs, see GameTestChunks)
        GameTestChunks.holdAndAwaitOrFail(level, chunks, "8v8 stress");

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

                // static defences and game enders (design/code_review.md round 2): per owner STRESS_GATES gates,
                // STRESS_TRAPS traps, STRESS_TOWERS raid towers, a game ender and a charged Ward Spire, inside its army's
                // cluster. Built placements that are never registered (BuildingServerEvents.getBuildings is untouched)
                // and never stamped: what is timed is the per-tick scan each one runs, against the 400 units
                List<com.solegendary.reignofnether.building.BuildingPlacement> structures = new ArrayList<>();
                List<com.solegendary.reignofnether.building.buildings.defence.WallGate.GatePlacement> gates = new ArrayList<>();
                List<com.solegendary.reignofnether.building.buildings.defence.Trap.TrapPlacement> traps = new ArrayList<>();
                List<com.solegendary.reignofnether.building.buildings.defence.DefenceTower.DefenceTowerPlacement> towers = new ArrayList<>();
                List<com.solegendary.reignofnether.building.gameender.GameEnderPlacement> enders = new ArrayList<>();
                for (int o = 0; o < STRESS_OWNERS; o++)
                    stressStructures(level, "gametest_stress_" + o, baseX + (o % 4) * 20, (int) y, baseZ + (o / 4) * 20,
                        o, rng, structures, gates, traps, towers, enders);
                for (var ge : enders)
                    ge.pool = structures;   // aim and look for wards among the stress structures only

                // the real code paths (budgeted), then the pre-grid/pre-index ways as a logged reference only
                String[] names = { "unit grid rebuild", "wreck reclaim", "momentum", "formation", "capture points",
                    "queue lines", "player panel", "builder lookup x200", "bot enemy scans x64",
                    "defences+game enders", "REF builder lookup x200 (old scan)", "REF bot enemy scans x64 (old scan)",
                    "REF synched owner+health x10k", "REF plain-field owner+health x10k" };
                // CR-11 reference: the owner and health reads every per-unit pass makes (through SynchedEntityData),
                // against the same reads from plain arrays - the floor a per-unit owner field could reach
                List<net.minecraft.world.entity.LivingEntity> readers = new ArrayList<>();
                for (var e : spawned)
                    if (e instanceof net.minecraft.world.entity.LivingEntity le && e instanceof com.solegendary.reignofnether.unit.interfaces.Unit)
                        readers.add(le);
                String[] ownerField = new String[readers.size()];
                float[] healthField = new float[readers.size()];
                for (int i = 0; i < readers.size(); i++) {
                    ownerField[i] = ((com.solegendary.reignofnether.unit.interfaces.Unit) readers.get(i)).getOwnerName();
                    healthField[i] = readers.get(i).getHealth();
                }
                int budgeted = 10;
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
                    () -> {   // one tick's worth of every structure's scan (each really runs every SCAN_TICKS: worst case)
                        int n = 0;
                        for (var g : gates)
                            n += g.enemyNear(level) ? 1 : 0;
                        for (var t : traps)
                            n += t.foeOnPlate(level) ? 1 : 0;
                        for (var t : towers)
                            n += t.pickTarget(level) != null ? 1 : 0;
                        for (var ge : enders) {   // a strike being aimed and resolved, the rare expensive part
                            var target = ge.pickTarget();
                            if (target != null && ge.interceptor(target.centrePos, target.ownerName) != null)
                                n++;
                        }
                        stressSink += n;
                    },
                    () -> stressNaiveBuilderScans(200),
                    () -> stressNaiveEnemyScans(64, 12),
                    () -> {
                        int n = 0;
                        for (int r = 0; r < 25; r++)
                            for (var le : readers) {
                                String o = ((com.solegendary.reignofnether.unit.interfaces.Unit) le).getOwnerName();
                                n += (o == null ? 0 : o.length()) + (int) le.getHealth();
                            }
                        stressSink += n;
                    },
                    () -> {
                        int n = 0;
                        for (int r = 0; r < 25; r++)
                            for (int i = 0; i < ownerField.length; i++) {
                                String o = ownerField[i];
                                n += (o == null ? 0 : o.length()) + (int) healthField[i];
                            }
                        stressSink += n;
                    },
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
                long heroes = 0;
                for (var e : spawned)
                    if (e instanceof com.solegendary.reignofnether.hero.FactionHero)
                        heroes++;
                ReignOfNether.LOGGER.info("[Stress] {} units ({} in allUnits, {} heroes), {} wrecks, {} sites, {} gates, {} traps, {} towers, {} game enders, avg per pass over {} passes: {}| total={}ms (budget {}ms)",
                    units, com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits().size(), heroes,
                    STRESS_WRECKS, STRESS_POINTS, gates.size(), traps.size(), towers.size(), enders.size(), STRESS_ITERATIONS, sb,
                    String.format(java.util.Locale.ROOT, "%.3f", sum), STRESS_BUDGET_MS);
                if (units < STRESS_OWNERS * STRESS_PER_OWNER * 9 / 10)
                    helper.fail("only " + units + " stress units spawned");
                else if (heroes < STRESS_OWNERS || gates.size() < STRESS_OWNERS * STRESS_GATES
                        || traps.size() < STRESS_OWNERS * STRESS_TRAPS || towers.size() < STRESS_OWNERS * STRESS_TOWERS
                        || enders.size() < STRESS_OWNERS)
                    helper.fail("stress scenario incomplete: " + heroes + " heroes, " + gates.size() + " gates, "
                        + traps.size() + " traps, " + towers.size() + " towers, " + enders.size() + " game enders");
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
                GameTestChunks.releaseAll(level, chunks);
            }
            helper.succeed();
        });
    }

    /**
     * Owners 0-2 Sunforged, 3-5 Horde, 6-7 Gravebound; per owner 10 workers, then melee and ranged fighters, one of
     * them the faction hero.
     */
    static net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> stressUnitType(int owner, int i) {
        int kind = i < 10 ? 0 : i % 2 == 0 ? 1 : 2;   // worker, melee, ranged
        int faction = owner < 3 ? 0 : owner < 6 ? 1 : 2;
        if (i == 11)   // every side fields its hero (hero slice 2): the per-unit passes walk it like any other unit
            return faction == 0 ? com.solegendary.reignofnether.registrars.EntityRegistrar.DAWN_PALADIN_UNIT.get()
                : faction == 1 ? com.solegendary.reignofnether.registrars.EntityRegistrar.HORDE_WARCHIEF_UNIT.get()
                : com.solegendary.reignofnether.registrars.EntityRegistrar.GRAVECALLER_UNIT.get();
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

    static final int STRESS_GATES = 4, STRESS_TRAPS = 6, STRESS_TOWERS = 3;

    /**
     * One owner's static defences and game-ender pair for the benchmark, scattered over its 24x24 cluster at height y:
     * built, never stamped, never registered. Sunforged / Horde / Gravebound sets like the armies.
     */
    static void stressStructures(ServerLevel level, String owner, int cx, int y, int cz, int o, java.util.Random rng,
                                 List<com.solegendary.reignofnether.building.BuildingPlacement> all,
                                 List<com.solegendary.reignofnether.building.buildings.defence.WallGate.GatePlacement> gates,
                                 List<com.solegendary.reignofnether.building.buildings.defence.Trap.TrapPlacement> traps,
                                 List<com.solegendary.reignofnether.building.buildings.defence.DefenceTower.DefenceTowerPlacement> towers,
                                 List<com.solegendary.reignofnether.building.gameender.GameEnderPlacement> enders) {
        var set = o < 3 ? com.solegendary.reignofnether.building.buildings.defence.Defences.VILLAGERS
            : o < 6 ? com.solegendary.reignofnether.building.buildings.defence.Defences.PIGLINS
            : com.solegendary.reignofnether.building.buildings.defence.Defences.MONSTERS;
        List<Building> kinds = new ArrayList<>();
        for (int i = 0; i < STRESS_GATES; i++)
            kinds.add(set.gate());
        for (int i = 0; i < STRESS_TRAPS; i++)
            kinds.add(i % 2 == 0 ? set.trapA() : set.trapB());
        for (int i = 0; i < STRESS_TOWERS; i++)
            kinds.add(set.raidTower());
        kinds.add(o % 2 == 0 ? Buildings.SUN_SPIRE : Buildings.NECROPOLIS);
        kinds.add(o < 3 ? Buildings.WARD_SPIRE_VILLAGERS : o < 6 ? Buildings.WARD_SPIRE_PIGLINS : Buildings.WARD_SPIRE_MONSTERS);
        for (Building b : kinds) {
            BlockPos at = new BlockPos(cx + rng.nextInt(24) - 12, y, cz + rng.nextInt(24) - 12);
            var bp = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(b, level, at, Rotation.NONE, owner, false);
            if (bp == null)
                continue;
            bp.isBuilt = true;
            all.add(bp);
            if (bp instanceof com.solegendary.reignofnether.building.buildings.defence.WallGate.GatePlacement g)
                gates.add(g);
            else if (bp instanceof com.solegendary.reignofnether.building.buildings.defence.Trap.TrapPlacement t)
                traps.add(t);
            else if (bp instanceof com.solegendary.reignofnether.building.buildings.defence.DefenceTower.DefenceTowerPlacement t)
                towers.add(t);
            else if (bp instanceof com.solegendary.reignofnether.building.gameender.GameEnderPlacement ge)
                enders.add(ge);
            else if (bp instanceof com.solegendary.reignofnether.building.gameender.WardSpirePlacement w)
                w.charges = com.solegendary.reignofnether.building.gameender.WardSpirePlacement.MAX_CHARGES;
        }
    }

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
        live.add(Factions.BRASSWORKS);    // and the Brassworks since theirs
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
        // "Random" reaches all seven (the Stormcallers and the Brassworks joined in their slice 1)
        var rng = new java.util.Random(5);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 700; i++)
            seen.add(Factions.randomLive(rng).getName());
        if (!seen.contains("tidewrought") || seen.size() != 7)
            helper.fail("random should reach the seven live factions, saw " + seen);
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
        else if (stormKit.t2Lab() != Buildings.CELESTIAL_OBSERVATORY || stormKit.t3Building() != Buildings.HEAVENS_GATE)   // slice 4: T2 and T3
            helper.fail("the Stormcallers kit's T2 lab is not the Celestial Observatory, or its T3 lab is not Heaven's Gate");
        if (com.solegendary.reignofnether.bot.BotPlayer.vaultFor(storm) != Buildings.STORAGE_VAULT_STORM)
            helper.fail("the Stormcallers bot does not build the Mana Cistern");
        // the Mana Cistern banks energy, not metal (design/stormcallers_plan.md 3.3)
        if (Buildings.STORAGE_VAULT_STORM.getEnergyStorage() <= Buildings.STORAGE_VAULT_VILLAGERS.getEnergyStorage()
                || Buildings.STORAGE_VAULT_STORM.getMetalStorage() >= Buildings.STORAGE_VAULT_VILLAGERS.getMetalStorage()
                || Buildings.STORAGE_VAULT_VILLAGERS.getEnergyStorage() != com.solegendary.reignofnether.building.buildings.shared.StorageVault.ENERGY_STORAGE)
            helper.fail("the Mana Cistern's storage split is wrong, or it changed the other vaults");
        // Tier 2 and Tier 3 sit in the capitol's usual slots (slice 4: Tier 3 leads to Heaven's Gate)
        var pagodaItems = Buildings.JADE_PAGODA.productions.get();
        if (!pagodaItems.contains(com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_2)
                || !pagodaItems.contains(com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_3))
            helper.fail("the Jade Pagoda does not offer Tier 2 and Tier 3 research");
        if (Factions.getFaction(storm.key) != storm || !"stormcallers".equals(storm.getName()))
            helper.fail("the Stormcallers do not resolve by their key");
        if (ReignOfNetherRegistries.FACTIONS.getId(storm) < ReignOfNetherRegistries.FACTIONS.getId(Factions.TIDEWROUGHT))
            helper.fail("the Stormcallers were registered before the Tidewrought and shifted its id");
        // "Random" reaches all seven; skirmish code 6 starts them now
        var rng = new java.util.Random(6);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 700; i++)
            seen.add(Factions.randomLive(rng).getName());
        if (!seen.contains("stormcallers") || seen.size() != 7)
            helper.fail("random should reach the seven live factions, saw " + seen);
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
     * three, 3 Random, 4 the Court, 5 the Tidewrought, 6 the Stormcallers, 7 the Brassworks), and the server only ever starts a live faction from
     * any code (Random, preview, unknown all roll a live one). Pure static data, safe in parallel.
     */
    @GameTest(template = ARENA)
    public static void skirmish_faction_codes_round_trip_for_every_faction(GameTestHelper helper) {
        if (SkirmishServerboundPacket.RANDOM_CODE != 3)
            helper.fail("the Random code moved off 3");
        var fixed = List.of(Factions.VILLAGERS, Factions.MONSTERS, Factions.PIGLINS, Factions.RANDOM,
            Factions.VERDANT_COURT, Factions.TIDEWROUGHT, Factions.STORMCALLERS, Factions.BRASSWORKS);
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
        for (int code : new int[]{ -1, 3, 5, 6, 7, 99 })
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
     * Puts attacker i back on its own foe when its target goal has drifted to something else (a retaliation, an aggro
     * pick of a neighbouring test's unit), counting the strays for {@link #missReport}. Nothing once the foe is dead.
     */
    private static void keepOnFoe(net.minecraft.world.entity.Mob atk, net.minecraft.world.entity.Mob foe, int i,
                                  int[] strays, String[] lastStray, boolean countStrays) {
        var goal = ((com.solegendary.reignofnether.unit.interfaces.Unit) atk).getTargetGoal();
        net.minecraft.world.entity.LivingEntity cur = goal == null ? null : goal.getTarget();
        if (cur == foe || !foe.isAlive())
            return;
        if (cur != null && countStrays) {
            strays[i]++;
            lastStray[i] = describe(cur);
        }
        ((com.solegendary.reignofnether.unit.interfaces.AttackerUnit) atk).setUnitAttackTarget(foe);
    }

    /** "x never hit its target ..." with what the attacker was doing, so a flake says why instead of only that. */
    private static String missReport(String name, net.minecraft.world.entity.Mob atk, net.minecraft.world.entity.Mob foe,
                                     String owner, int strays, String lastStray,
                                     List<? extends net.minecraft.world.entity.Entity> ignore) {
        var goal = ((com.solegendary.reignofnether.unit.interfaces.Unit) atk).getTargetGoal();
        return name + " never hit its target " + describe(foe) + String.format(" (dist %.1f, foe hp %.1f%s;", atk.distanceTo(foe),
                foe.getHealth(), foe.isAlive() ? "" : " dead")
            + " attacker " + (atk.isAlive() ? String.format("hp %.1f", atk.getHealth()) : "dead")
            + String.format(" at %.1f/%.1f/%.1f", atk.getX(), atk.getY(), atk.getZ())
            + ", targeting " + describe(goal == null ? null : goal.getTarget())
            + ", last hurt " + describe(atk.getLastHurtMob()) + ", hurt by " + describe(atk.getLastHurtByMob())
            + ", foe hurt by " + describe(foe.getLastHurtByMob())
            + ", strays " + strays + (lastStray == null ? "" : " last " + lastStray)
            + ", nearest stranger " + nearestStranger(atk, owner, 30, ignore) + ")";
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
                    keepOnFoe(atk, foe, i, strays, lastStray, tick > 3);
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
                for (int i = 0; i < attackers.size(); i++)
                    if (!landed[i])
                        problems.add(missReport(net.minecraft.world.entity.EntityType.getKey(types.get(i)).getPath(),
                            attackers.get(i), foes.get(i), owner, strays[i], lastStray[i], foes));
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
        else if (wolfOnGrunt == grunt)   // since Oct 11 wildlife hunts no unit at all (workers died to monkeys/foxes)
            helper.fail("a wild wolf was allowed to target an ordinary unit");
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
     * lab), Verdant, and no ability of its own: builder abilities went in every faction on 2026-10-26 (PR #253, 66d0fdb3,
     * which took its Awaken Thicket). The treant-waking helper stays (the World Tree Walker's grove uses it): it wakes a
     * tagged, owned Sentinel Treant that is dismissed again.
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
            if (druid.getAbilities() != null && !druid.getAbilities().get().isEmpty())
                helper.fail("the Elder Druid has abilities (builders have none since #253): "
                    + druid.getAbilities().get().stream().map(a -> a.getClass().getSimpleName()).toList());
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
     * Archers shoot buildings from range (lovish Oct 20: with no building attack they were sent to the building and died
     * under its defences): the Thornbow, Skeleton, Stray and Headhunter carry a RangedAttackBuildingGoal, and a shot at a
     * point launches a projectile they own.
     */
    @GameTest(template = ARENA, timeoutTicks = 40)
    public static void archers_shoot_buildings_from_range(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var types = List.<net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob>>of(
            com.solegendary.reignofnether.registrars.EntityRegistrar.THORNBOW_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.SKELETON_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.STRAY_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.HEADHUNTER_UNIT.get());
        BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
        for (int i = 0; i < types.size(); i++) {
            var mob = types.get(i).create(level);
            if (!(mob instanceof com.solegendary.reignofnether.unit.interfaces.AttackerUnit au)
                    || !(mob instanceof com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit ru)) {
                helper.fail("could not create " + types.get(i).getDescriptionId() + " as a ranged attacker");
                return;
            }
            String name = types.get(i).getDescriptionId();
            if (!au.canAttackBuildings() || !(au.getAttackBuildingGoal()
                    instanceof com.solegendary.reignofnether.unit.goals.RangedAttackBuildingGoal<?>)) {
                helper.fail(name + " should attack buildings with a RangedAttackBuildingGoal, has " + au.getAttackBuildingGoal());
                return;
            }
            mob.moveTo(at.getX() + 0.5 + i * 3, at.getY(), at.getZ() + 0.5, 0, 0);
            ((com.solegendary.reignofnether.unit.interfaces.Unit) mob).setOwnerName("gametest_archer_bld");
            level.addFreshEntity(mob);
            var box = mob.getBoundingBox().inflate(4);
            int before = level.getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class, box,
                a -> a.getOwner() == mob).size();
            ru.performUnitRangedAttack(mob.getX() + 8, mob.getY() + 1, mob.getZ(), 20);
            int after = level.getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class, box,
                a -> a.getOwner() == mob).size();
            if (after <= before) {
                helper.fail(name + " fired nothing at a point (performUnitRangedAttack(x, y, z) is a no-op)");
                return;
            }
            mob.discard();
        }
        helper.succeed();
    }

    /**
     * Water goes in last (lovish Oct 20: extractors leaked water while being built and jammed their builders): for every
     * faction's extractor that holds water, construction offers no water-carrying block while any dry block is unplaced.
     */
    /**
     * A hero dying mid-fight records it for revival without writing the world's saved data to disk on the server thread
     * (it flushed every dirty save file per death): the save data holds the fallen heroes and is only marked dirty.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void hero_death_marks_its_save_without_a_disk_flush(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var fallen = com.solegendary.reignofnether.hero.HeroServerEvents.fallenHeroes;
        var data = com.solegendary.reignofnether.unit.HeroUnitSaveData.getInstance(level);
        level.getDataStorage().save();   // start clean
        if (data.isDirty()) {
            helper.fail("the hero save data should be clean after a flush");
            return;
        }
        com.solegendary.reignofnether.unit.UnitServerEvents.saveFallenHeroUnits(level);
        if (!data.isDirty())
            helper.fail("a hero death should mark the hero save data dirty for the next autosave");
        else if (data.heroUnits.size() != fallen.size())
            helper.fail("the hero save data holds " + data.heroUnits.size() + " heroes, the fallen list " + fallen.size());
        else
            helper.succeed();
    }

    /**
     * The bot soak's builders stalled one block short (Heartwood Hall 1272/1273, the Tidewrought wind generator): a
     * block the world changes after it is set - leaves recompute "distance" from the nearest log - never read as placed
     * again, so construction re-placed it forever. Lays both structures block by block the way builders do (bottom up,
     * with block updates), lets the world settle, and requires every block to still count as placed.
     */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void built_blocks_still_count_after_the_world_settles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 230;
        List<com.solegendary.reignofnether.building.BuildingPlacement> built = new ArrayList<>();
        var types = List.<Building>of(Buildings.HEARTWOOD_HALL, Buildings.WIND_GENERATOR_TIDE);
        for (int t = 0; t < types.size(); t++) {
            BlockPos origin = new BlockPos(base.getX() + 120 + t * 40, y, base.getZ() + 120);
            var placement = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(types.get(t),
                level, origin, Rotation.NONE, "gametest_settle", false);
            List<com.solegendary.reignofnether.building.BuildingBlock> blocks = new ArrayList<>();
            int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE,
                minY = Integer.MAX_VALUE;
            for (var b : placement.getBlocks())
                if (!b.getBlockState().isAir()) {
                    blocks.add(b);
                    BlockPos p = b.getBlockPos();
                    minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
                    minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
                    minY = Math.min(minY, p.getY());
                }
            for (int x = minX - 1; x <= maxX + 1; x++)
                for (int z = minZ - 1; z <= maxZ + 1; z++)
                    level.setBlock(new BlockPos(x, minY - 1, z), Blocks.STONE.defaultBlockState(), 3);
            blocks.sort(java.util.Comparator.comparingInt(b -> b.getBlockPos().getY()));
            for (var b : blocks)
                level.setBlockAndUpdate(b.getBlockPos(), b.getBlockState());
            built.add(placement);
        }
        helper.runAfterDelay(60, () -> {
            List<String> drifted = new ArrayList<>();
            for (var placement : built) {
                int n = 0;
                for (var b : placement.getBlocks())
                    if (!b.getBlockState().isAir() && !b.isPlaced(level)) {
                        if (n++ < 4)
                            drifted.add(placement.getBuilding().structureName + " " + b.getBlockPos().toShortString()
                                + " wants " + b.getBlockState() + " has " + level.getBlockState(b.getBlockPos()));
                    }
                if (n > 4)
                    drifted.add(placement.getBuilding().structureName + " +" + (n - 4) + " more");
            }
            if (drifted.isEmpty())
                helper.succeed();
            else
                helper.fail("blocks that no longer count as placed once the world settled: " + String.join("; ", drifted));
        });
    }

    /**
     * Construction offers the lowest unplaced layer only, so a placed block that drifted at a low layer must not hide
     * a never-placed block above it - the block that was missing when the soak's buildings sat one short.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void never_placed_blocks_are_queued_before_drifted_ones(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var placement = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(Buildings.HEARTWOOD_HALL,
            level, helper.absolutePos(new BlockPos(4, 1, 4)), Rotation.NONE, "gametest_queue_order", false);
        com.solegendary.reignofnether.building.BuildingBlock low = null, high = null;
        for (var b : placement.getBlocks()) {
            if (b.getBlockState().isAir())
                continue;
            if (low == null || b.getBlockPos().getY() < low.getBlockPos().getY())
                low = b;
            if (high == null || b.getBlockPos().getY() > high.getBlockPos().getY())
                high = b;
        }
        if (low == null || high == null || low.getBlockPos().getY() == high.getBlockPos().getY()) {
            helper.fail("the Heartwood Hall should have blocks on more than one layer");
            return;
        }
        ArrayList<com.solegendary.reignofnether.building.BuildingBlock> unplaced = new ArrayList<>(List.of(low, high));
        var offered = com.solegendary.reignofnether.building.BuildingPlacement.neverPlacedFirst(unplaced,
            Set.of(low.getBlockPos()));
        if (offered.size() != 1 || offered.get(0) != high) {
            helper.fail("a drifted placed block at y " + low.getBlockPos().getY() + " still competes with the never-placed"
                + " block at y " + high.getBlockPos().getY() + ": offered " + offered.size());
            return;
        }
        if (com.solegendary.reignofnether.building.BuildingPlacement.neverPlacedFirst(unplaced,
                Set.of(low.getBlockPos(), high.getBlockPos())).size() != 2) {
            helper.fail("with every block placed once, the drifted ones should all be offered again");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void extractor_water_is_placed_after_the_basin(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var types = List.of(com.solegendary.reignofnether.building.Buildings.METAL_EXTRACTOR_VILLAGERS,
            com.solegendary.reignofnether.building.Buildings.METAL_EXTRACTOR_MONSTERS,
            com.solegendary.reignofnether.building.Buildings.METAL_EXTRACTOR_PIGLINS,
            com.solegendary.reignofnether.building.Buildings.METAL_EXTRACTOR_VERDANT,
            com.solegendary.reignofnether.building.Buildings.METAL_EXTRACTOR_TIDE,
            com.solegendary.reignofnether.building.Buildings.METAL_EXTRACTOR_STORM,
            com.solegendary.reignofnether.building.Buildings.METAL_EXTRACTOR_BRASS);
        int withWater = 0;
        for (var type : types) {
            var placement = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(type, level,
                helper.absolutePos(new BlockPos(4, 1, 4)), net.minecraft.world.level.block.Rotation.NONE, "gametest_water_last", false);
            java.util.ArrayList<com.solegendary.reignofnether.building.BuildingBlock> all = new java.util.ArrayList<>();
            int fluids = 0;
            for (var b : placement.getBlocks())
                if (!b.getBlockState().isAir()) {
                    all.add(b);
                    if (com.solegendary.reignofnether.building.BuildingPlacement.carriesFluid(b.getBlockState()))
                        fluids++;
                }
            if (fluids == 0 || fluids == all.size())
                continue;
            withWater++;
            for (var b : com.solegendary.reignofnether.building.BuildingPlacement.waterLast(all))
                if (com.solegendary.reignofnether.building.BuildingPlacement.carriesFluid(b.getBlockState())) {
                    helper.fail(type.name + ": water is offered for placement while dry blocks are unplaced");
                    return;
                }
        }
        if (withWater == 0)
            helper.fail("no extractor holds water - the Angel Fountain and the Tide-Pool Pump should");
        else
            helper.succeed();
    }

    /**
     * Experimentals above 1024 HP keep their designed health: vanilla clamps generic.max_health to 1024, and until
     * MaxHealthCapMixin every one of these played at 1024/1024 (lovish's Oct 19 clips: the World Tree Walker).
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void experimentals_keep_their_health_above_1024(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var types = List.<net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.LivingEntity>>of(
            com.solegendary.reignofnether.registrars.EntityRegistrar.WORLD_TREE_WALKER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.LEVIATHAN_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.WAR_MAMMOTH_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.CELESTIAL_DRAGON_UNIT.get());
        float[] designed = {
            com.solegendary.reignofnether.unit.units.verdant.WorldTreeWalkerUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.tide.LeviathanUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.piglins.WarMammothUnit.maxHealth,
            com.solegendary.reignofnether.unit.units.storm.CelestialDragonUnit.maxHealth};
        for (int i = 0; i < types.size(); i++) {
            var e = types.get(i).create(level);
            if (e == null) {
                helper.fail("could not create " + types.get(i).getDescriptionId());
                return;
            }
            if (Math.abs(e.getMaxHealth() - designed[i]) > 0.01f || Math.abs(e.getHealth() - designed[i]) > 0.01f) {
                helper.fail(types.get(i).getDescriptionId() + " should spawn at " + designed[i] + " HP, has "
                    + e.getHealth() + " / " + e.getMaxHealth());
                return;
            }
            e.discard();
        }
        helper.succeed();
    }

    /**
     * Units step apart (Separation, lovish Oct 19: "all the units just merge together"): six Leafblades summoned on one
     * spot stand at least half a block apart a few seconds later.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void units_summoned_on_one_spot_step_apart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(8, 2, 8));
        List<net.minecraft.world.entity.Mob> crowd = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            var lb = com.solegendary.reignofnether.registrars.EntityRegistrar.LEAFBLADE_UNIT.get().create(level);
            if (lb == null) {
                helper.fail("could not create a Leafblade");
                return;
            }
            lb.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
            lb.setOwnerName("gametest_separation");
            level.addFreshEntity(lb);
            crowd.add(lb);
        }
        helper.runAfterDelay(120, () -> {
            double closest = Double.MAX_VALUE;
            for (int i = 0; i < crowd.size(); i++)
                for (int j = i + 1; j < crowd.size(); j++) {
                    var a = crowd.get(i);
                    var b = crowd.get(j);
                    double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
                    closest = Math.min(closest, Math.sqrt(dx * dx + dz * dz));
                }
            for (var m : crowd)
                m.discard();
            if (closest < 0.5)
                helper.fail("six units summoned on one spot are still stacked: closest pair " + String.format("%.2f", closest) + " blocks apart");
            else
                helper.succeed();
        });
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

    /**
     * Faction-coloured projectiles: the SHOT / IMPACT flags carry the shooter's faction (F_*, bits 4-6) next to the
     * detail the byte already held (W_* weapon class, AT_* where; bits 0-3), through the packet codec, for every
     * faction and every detail value; every live faction has its own shot tint and the lookup by wire id gives it
     * back; F_NONE stays untinted. (The packet did not grow: the faction rides in spare bits of the flags byte.)
     */
    @GameTest(template = ARENA)
    public static void shot_fx_packet_carries_faction_and_detail(GameTestHelper helper) {
        byte[] factions = { com.solegendary.reignofnether.barfx.BarFx.F_NONE, com.solegendary.reignofnether.barfx.BarFx.F_SUNFORGED,
            com.solegendary.reignofnether.barfx.BarFx.F_GRAVEBOUND, com.solegendary.reignofnether.barfx.BarFx.F_HORDE,
            com.solegendary.reignofnether.barfx.BarFx.F_VERDANT, com.solegendary.reignofnether.barfx.BarFx.F_TIDE,
            com.solegendary.reignofnether.barfx.BarFx.F_STORM, com.solegendary.reignofnether.barfx.BarFx.F_BRASS };
        byte[] details = { com.solegendary.reignofnether.barfx.BarFx.W_BY_KIND, com.solegendary.reignofnether.barfx.BarFx.W_GUN,
            com.solegendary.reignofnether.barfx.BarFx.AT_BUILDING, 15 };
        // pack / unpack agree, and neither field leaks into the other
        for (byte f : factions)
            for (byte d : details) {
                byte flags = com.solegendary.reignofnether.barfx.BarFx.shotFlags(d, f);
                if (com.solegendary.reignofnether.barfx.BarFx.shotFaction(flags) != f
                        || com.solegendary.reignofnether.barfx.BarFx.shotDetail(flags) != d) {
                    helper.fail("faction " + f + " detail " + d + " packed to " + flags + ", unpacked as faction "
                        + com.solegendary.reignofnether.barfx.BarFx.shotFaction(flags) + " detail "
                        + com.solegendary.reignofnether.barfx.BarFx.shotDetail(flags));
                    return;
                }
            }
        // the plain overloads still mean "untinted, detail as given": a W_GUN shot sounds like a gun, hits a building
        if (com.solegendary.reignofnether.barfx.BarFx.shotFaction(com.solegendary.reignofnether.barfx.BarFx.W_GUN)
                != com.solegendary.reignofnether.barfx.BarFx.F_NONE
                || com.solegendary.reignofnether.barfx.BarFx.shotDetail(com.solegendary.reignofnether.barfx.BarFx.AT_BUILDING)
                != com.solegendary.reignofnether.barfx.BarFx.AT_BUILDING) {
            helper.fail("an unpacked flags byte (the old W_* / AT_* values) no longer reads as itself");
            return;
        }
        // every live faction has its own tint, reachable by the id its shots carry; neutral has none
        var live = List.of(Factions.VILLAGERS, Factions.MONSTERS, Factions.PIGLINS, Factions.VERDANT_COURT,
            Factions.TIDEWROUGHT, Factions.STORMCALLERS, Factions.BRASSWORKS);
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (var f : live) {
            var t = FactionTraits.of(f);
            if (t.shotTint == 0 || FactionTraits.shotTintOf(t.deathDebris) != t.shotTint || !seen.add(t.shotTint)) {
                helper.fail(f.getName() + " has shot tint " + Integer.toHexString(t.shotTint) + ", by wire id "
                    + Integer.toHexString(FactionTraits.shotTintOf(t.deathDebris)) + (seen.contains(t.shotTint) ? " (shared)" : ""));
                return;
            }
        }
        if (FactionTraits.NEUTRAL.shotTint != 0 || FactionTraits.shotTintOf(com.solegendary.reignofnether.barfx.BarFx.F_NONE) != 0
                || FactionTraits.shotTintOf(-1) != 0 || FactionTraits.shotTintOf(99) != 0) {
            helper.fail("neutral / unknown factions should be untinted");
            return;
        }
        // and the whole thing survives the packet codec
        List<com.solegendary.reignofnether.barfx.BarFx.Event> sent = new ArrayList<>();
        for (byte f : factions) {
            sent.add(new com.solegendary.reignofnether.barfx.BarFx.Event(com.solegendary.reignofnether.barfx.BarFx.SHOT,
                com.solegendary.reignofnether.barfx.BarFx.K_ARROW,
                com.solegendary.reignofnether.barfx.BarFx.shotFlags(com.solegendary.reignofnether.barfx.BarFx.W_GUN, f), 1, 2, 3, 4, 5, 6));
            sent.add(new com.solegendary.reignofnether.barfx.BarFx.Event(com.solegendary.reignofnether.barfx.BarFx.IMPACT,
                com.solegendary.reignofnether.barfx.BarFx.K_MELEE,
                com.solegendary.reignofnether.barfx.BarFx.shotFlags(com.solegendary.reignofnether.barfx.BarFx.AT_BUILDING, f), 1, 2, 3, 0, 1, 0));
        }
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            new com.solegendary.reignofnether.barfx.BarFxClientboundPacket(sent).encode(buf);
            var got = new com.solegendary.reignofnether.barfx.BarFxClientboundPacket(buf).events();
            if (got.size() != sent.size()) {
                helper.fail("decoded " + got.size() + " events, sent " + sent.size());
                return;
            }
            for (int i = 0; i < got.size(); i++) {
                var e = got.get(i);
                byte f = factions[i / 2];
                int want = i % 2 == 0 ? com.solegendary.reignofnether.barfx.BarFx.W_GUN : com.solegendary.reignofnether.barfx.BarFx.AT_BUILDING;
                if (e.type != sent.get(i).type || com.solegendary.reignofnether.barfx.BarFx.shotFaction(e.flags) != f
                        || com.solegendary.reignofnether.barfx.BarFx.shotDetail(e.flags) != want) {
                    helper.fail("event " + i + " came back as type " + e.type + " faction "
                        + com.solegendary.reignofnether.barfx.BarFx.shotFaction(e.flags) + " detail "
                        + com.solegendary.reignofnether.barfx.BarFx.shotDetail(e.flags) + ", sent faction " + f + " detail " + want);
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
                // a lid for the clearing: arenas sit in generated terrain, and a gravel or sand layer overhead loses its
                // support when the pocket under it is cleared and falls in on the units - gravel in a Jade Guard's cell
                // ("by inWall (eyes in gravel)") shattered a mirror-image decoy on three CI runs (Oct 21-22, mirror_images_
                // decoys_draw_fire_deal_nothing_and_fade). A falling block just above the pocket becomes stone, at once,
                // before its block tick can fire; whatever lies above that is then supported again.
                // The lowest block over the pocket, not just the one at y 5: the arena's own placement can already
                // have dropped the gravel at y 5 (an entity in flight, landing after this clearing), leaving the next
                // gravel up resting on air - gravel in a possessed Bombard's eye block (aim 0.02 away) held its shot
                // ("set up but never fired", shard 1, Oct 28). The air over an arena runs high: a gravel layer at y 32-33
                // began falling as a possessed Bombard's test started and landed two deep in front of it (shard-1
                // stress 40729344872), so the search goes up to y 64
                for (int y = 5; y <= 64; y++) {
                    BlockPos above = helper.absolutePos(new BlockPos(x, y, z));
                    var state = level.getBlockState(above);
                    if (state.isAir())
                        continue;
                    if (state.getBlock() instanceof net.minecraft.world.level.block.FallingBlock)
                        level.setBlock(above, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
                    break;
                }
            }
        // and the gravel already falling (it would land in the cleared pocket)
        BlockPos lo = helper.absolutePos(new BlockPos(x0, 2, z0)), hi = helper.absolutePos(new BlockPos(x1, 64, z1));
        for (var falling : level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,
                new net.minecraft.world.phys.AABB(lo, hi).expandTowards(1, 1, 1)))
            falling.discard();
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
    // own batch: once on CI (622f2dd) the unit "never hid again" - a neighbouring test's bell or enemy in the same
    // batch can keep it revealed, so it runs alone
    @GameTest(template = ARENA, timeoutTicks = 100 + com.solegendary.reignofnether.ability.abilities.HolyBell.REVEAL_TICKS,
            batch = "holy_bell_alone")
    public static void holy_bell_breaks_thicket_cover(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_bell_thicket", foeOwner = "gametest_bell_thicket_foe";
        // a lid first: on terrain with gravel overhead, clearing the pocket let the gravel fall through the thicket
        // (it is replaceable) and fill its spot - "the thicket's spot holds gravel" on three runs in a row (Oct 19)
        for (int x = 2; x <= 6; x++)
            for (int z = 10; z <= 14; z++)
                level.setBlock(helper.absolutePos(new BlockPos(x, 5, z)), Blocks.STONE.defaultBlockState(), 2);
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
                // red twice on CI (Oct 15: "in friendly thicket false") - say whether the bush went or the unit moved
                if (!com.solegendary.reignofnether.blocks.ThicketCover.isHidden(friend))
                    helper.fail("the unit never hid again after the Holy Bell reveal ran out ("
                        + com.solegendary.reignofnether.blocks.ThicketCover.diagnose(level, friend)
                        + "; the thicket's spot " + spot.toShortString() + " holds " + level.getBlockState(spot)
                        + ", the unit stands " + String.format(java.util.Locale.ROOT, "%.2f",
                            Math.sqrt(friend.distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5)))
                        + " from it at y " + String.format(java.util.Locale.ROOT, "%.2f", friend.getY())
                        + ", moving " + friend.getDeltaMovement() + ", on fire " + friend.isOnFire() + ")");
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
        int[] strays = new int[attackers.size()];
        String[] lastStray = new String[attackers.size()];
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
                // hold each order: a retaliation or an aggro pick of a neighbouring test's unit (ranged reach and aggro
                // cross the arena) must not pull an attacker off its Villager; missReport says if one did
                for (int i = 0; i < attackers.size(); i++)
                    if (!landed[i])
                        keepOnFoe(attackers.get(i), foes.get(i), i, strays, lastStray, true);
            });
        helper.runAfterDelay(300, () -> {
            List<String> problems = new ArrayList<>();
            for (int i = 0; i < attackers.size(); i++)
                if (!landed[i])
                    problems.add(missReport(net.minecraft.world.entity.EntityType.getKey(reg.get(i)).getPath(),
                        attackers.get(i), foes.get(i), owner, strays[i], lastStray[i], foes));
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
        // (deployed_turret_unit: the Brassworks Guildmaster's Deploy Turret summon - registered to the faction so its
        // kills/colour/ownership read as Brassworks, but DeployTurret spawns it and pays its 40 metal)
        // (clockwork_drone_unit: launched by the Iron Leviathan Engine, never trained)
        Set<String> noProdOk = Set.of("silverfish_unit", "armoured_hoglin_unit", "deployed_turret_unit", "clockwork_drone_unit");
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
    // mirrors tools/check_building_icons.py PLACEHOLDERS
    private static final java.util.Set<String> PLACEHOLDER_ICONS = java.util.Set.of(
        "textures/block/cobblestone.png", "textures/block/stone_bricks.png", "textures/block/amethyst_block.png",
        "textures/block/polished_granite.png", "textures/block/fletching_table_front.png",
        "textures/block/mossy_stone_bricks.png", "textures/block/spawner.png", "textures/block/reinforced_deepslate_side.png",
        "textures/block/deepslate_bricks.png", "textures/block/deepslate_tiles.png",
        "textures/block/red_glazed_terracotta.png", "textures/block/chiseled_nether_bricks.png",
        "textures/block/chiseled_polished_blackstone.png", "textures/block/obsidian.png");

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
                // the plain block faces the faction labs / towers / capitols used to ship with (the Horde's Fortress
                // was a nether-brick square in lovish's clip) - each has a drawn icon now (tools/gen_hud_icons.py)
                if (PLACEHOLDER_ICONS.contains(b.icon.getPath()))
                    gaps.add(id + ": placeholder block face " + b.icon + " - draw one in tools/gen_hud_icons.py");
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
        for (String skin : List.of("royal_architect_body_unit", "embalmer_unit", "bonewright_unit", "war_mammoth_unit",
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
        int[] strays = new int[attackers.size()];
        String[] lastStray = new String[attackers.size()];
        for (int t = 4; t < 300; t++)
            helper.runAfterDelay(t, () -> {
                for (int i = 0; i < attackers.size(); i++)
                    if (foes.get(i).getLastHurtByMob() == attackers.get(i) || foes.get(i).getHealth() < foes.get(i).getMaxHealth())
                        landed[i] = true;
                // hold each order: a retaliation or an aggro pick of a neighbouring test's unit (ranged reach and aggro
                // cross the arena) must not pull an attacker off its Villager; missReport says if one did
                for (int i = 0; i < attackers.size(); i++)
                    if (!landed[i])
                        keepOnFoe(attackers.get(i), foes.get(i), i, strays, lastStray, true);
            });
        helper.runAfterDelay(300, () -> {
            List<String> problems = new ArrayList<>();
            for (int i = 0; i < attackers.size(); i++)
                if (!landed[i])
                    problems.add(missReport(net.minecraft.world.entity.EntityType.getKey(reg.get(i)).getPath(),
                        attackers.get(i), foes.get(i), owner, strays[i], lastStray[i], foes));
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
     * tidal.
     */
    @GameTest(template = ARENA)
    public static void tidewrought_harbourmaster_is_a_t2_worker(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_harbourmaster_" + helper.absolutePos(BlockPos.ZERO).asLong();
        openGround(helper, 1, 1, 14, 14);
        var hm = com.solegendary.reignofnether.registrars.EntityRegistrar.HARBOURMASTER_UNIT.get().create(level);
        var ship = com.solegendary.reignofnether.registrars.EntityRegistrar.SHIPWRIGHT_UNIT.get().create(level);
        var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 0, 0);
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
     * A flyer never pushes into blocks (FlyingUnitMoveControl): a Sky Corsair ordered through a 10-high wall climbs
     * over it instead of sitting "in a wall" - the production_buildings_place_every_unit flake was a corsair that
     * drifted into its drydock's superstructure because the ported ghast control had lost vanilla's collision sweep.
     * Own sky pad at 290: the climb (wall top + the 4-high body) stays under the 312 pad and above the 285 pad's units.
     */
    @GameTest(template = ARENA, timeoutTicks = 220)
    public static void flyer_climbs_over_a_wall_instead_of_pushing_into_it(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pad = skyPad(helper, 290);
        final int gy = pad.getY();   // standing height
        List<BlockPos> wall = new ArrayList<>();
        for (int dz = 2; dz <= 14; dz++)
            for (int y = gy; y <= gy + 9; y++)
                wall.add(new BlockPos(pad.getX() + 8, y, pad.getZ() + dz));
        for (BlockPos b : wall)
            level.setBlock(b, Blocks.STONE.defaultBlockState(), 3);
        var corsair = com.solegendary.reignofnether.registrars.EntityRegistrar.SKY_CORSAIR_UNIT.get().create(level);
        if (corsair == null) {
            helper.fail("could not create the Sky Corsair");
            return;
        }
        corsair.moveTo(pad.getX() + 3.5, gy + 8, pad.getZ() + 8.5, 90, 0);
        corsair.setOwnerName("gametest_flyer_wall");
        level.addFreshEntity(corsair);
        BlockPos target = new BlockPos(pad.getX() + 13, gy - 1, pad.getZ() + 8);   // the glass block: a ground target
        Runnable clean = () -> {
            corsair.discard();
            for (BlockPos b : wall)
                level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
        };
        helper.runAfterDelay(3, () -> corsair.getMoveGoal().setMoveTarget(target));
        final int[] inWallAt = { -1 };
        for (int t = 10; t <= 180; t += 5) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (inWallAt[0] < 0 && corsair.isInWall())
                    inWallAt[0] = tick;
            });
        }
        helper.runAfterDelay(185, () -> {
            double x = corsair.getX(), y = corsair.getY();
            try {
                if (inWallAt[0] >= 0)
                    helper.fail("the corsair was in a wall at tick " + inWallAt[0] + " (now " + String.format("%.1f/%.1f", x, y) + ")");
                else if (x < pad.getX() + 8.5)
                    helper.fail(String.format("the corsair did not get past the wall in 180 ticks: x %.1f y %.1f (wall at x %d, top %d)",
                        x, y, pad.getX() + 8, gy + 9));
                else
                    helper.succeed();
            } finally {
                clean.run();
            }
        });
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
        // the order a player gives (UnitActionItem: right-click an enemy = setUnitAttackTargetForced). An unforced target is
        // re-picked every RETARGET_TICKS (AttackerUnit.retargetToClosestUnit) to the nearest enemy, and in this test that is
        // the NEIGHBOURING row's Villager (5 blocks off, nearer than the galley's own at 14): a shell fired in such a window
        // lands on the wrong Villager, "mortar_galley_unit never hit its target ... strays 21" (CI Oct 21-22, 3 reds)
        helper.runAfterDelay(3, () -> {
            for (int i = 0; i < attackers.size(); i++)
                ((com.solegendary.reignofnether.unit.interfaces.AttackerUnit) attackers.get(i)).setUnitAttackTargetForced(foes.get(i));
        });
        boolean[] landed = new boolean[attackers.size()];
        int[] strays = new int[attackers.size()];
        String[] lastStray = new String[attackers.size()];
        for (int t = 4; t < 400; t++)
            helper.runAfterDelay(t, () -> {
                for (int i = 0; i < attackers.size(); i++)
                    if (!foes.get(i).isAlive() || foes.get(i).getHealth() < foes.get(i).getMaxHealth())
                        landed[i] = true;
                // hold each order: a retaliation or an aggro pick of a neighbouring test's unit (ranged reach and aggro
                // cross the arena) must not pull an attacker off its Villager; missReport says if one did
                for (int i = 0; i < attackers.size(); i++)
                    if (!landed[i])
                        keepOnFoe(attackers.get(i), foes.get(i), i, strays, lastStray, true);
            });
        helper.runAfterDelay(400, () -> {
            List<String> problems = new ArrayList<>(setup);
            for (int i = 0; i < attackers.size(); i++)
                if (!landed[i])
                    problems.add(missReport(net.minecraft.world.entity.EntityType.getKey(reg.get(i)).getPath(),
                        attackers.get(i), foes.get(i), owner, strays[i], lastStray[i], foes));
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
        // the ground: glass over the whole column level with the structure's floor, open sky above. The glass runs
        // two blocks past the column on every side (into air only - never over a neighbouring test's blocks): the
        // target below is the column's corner cell, and a unit that arrives at a run overshoots it - a Leafblade
        // stopped 0.8 past the corner, off the pad, and fell 70 blocks to its death (grove_frees_its_units, main
        // e11ea62 re-run). The pads of these six tests sit 10 apart in y, so a neighbour's clearing can only ever
        // remove a margin, never the pad; only the column itself is cleared to the sky
        for (int dx = -2; dx < 18; dx++)
            for (int dz = -2; dz < 18; dz++) {
                boolean inside = dx >= 0 && dx <= 15 && dz >= 0 && dz <= 15;
                for (int y = y0 - 1; y <= y0; y++) {
                    BlockPos p = new BlockPos(cx + dx, y, cz + dz);
                    if (inside || level.getBlockState(p).isAir())
                        level.setBlock(p, Blocks.GLASS.defaultBlockState(), 2);
                }
                if (!inside)
                    continue;
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
        // the extractor is the biggest of the Court's economy buildings (root claws round a living-wood core); the
        // generator, converter and vault (tools/eco_verdant.py) are solid 3x3s that pass the pocket audit
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
        int[] strays = new int[attackers.size()];
        String[] lastStray = new String[attackers.size()];
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
                // hold each order: a retaliation or an aggro pick of a neighbouring test's unit (ranged reach and aggro
                // cross the arena) must not pull an attacker off its Villager; missReport says if one did
                for (int i = 0; i < attackers.size(); i++)
                    if (!landed[i])
                        keepOnFoe(attackers.get(i), foes.get(i), i, strays, lastStray, true);
            });
        helper.runAfterDelay(300, () -> {
            List<String> problems = new ArrayList<>();
            for (int i = 0; i < attackers.size(); i++)
                if (!landed[i])
                    problems.add(missReport(net.minecraft.world.entity.EntityType.getKey(reg.get(i)).getPath(),
                        attackers.get(i), foes.get(i), owner, strays[i], lastStray[i], foes));
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
     * not).
     */
    @GameTest(template = ARENA)
    public static void ley_master_is_a_t2_worker(GameTestHelper helper) {
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
        // nobody but the test casts: the friendly scribe would otherwise autocast Seal at the enemy two blocks away and
        // the pool would read 415, not 440 (CI 38505615622)
        for (var e : all)
            com.solegendary.reignofnether.ability.AutocastState.setAll((com.solegendary.reignofnether.unit.interfaces.Unit) e, false);
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
            // the copies stay on the pad: a Jade Guard copy with its AI on walks off toward whatever the neighbouring
            // tests field, and a stray blow shatters it - "did not last half their lifetime: 1" (Oct 19, cause unseen).
            // Nothing below needs them to move; what hit one, if anything does, is in DecoyServerEvents.lastShatter()
            for (var d : decoys)
                if (d instanceof net.minecraft.world.entity.Mob m)
                    m.setNoAi(true);
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
                helper.fail("the decoys did not last half their lifetime: " + com.solegendary.reignofnether.mana.DecoyServerEvents.countOf(owner)
                        + "; last shatter: " + com.solegendary.reignofnether.mana.DecoyServerEvents.lastShatter());
        });
        helper.runAfterDelay(5 + com.solegendary.reignofnether.mana.DecoyServerEvents.LIFETIME_TICKS + 30, () -> {
            try {
                if (com.solegendary.reignofnether.mana.DecoyServerEvents.countOf(owner) != 0)
                    helper.fail("the decoys did not fade after their lifetime: " + com.solegendary.reignofnether.mana.DecoyServerEvents.countOf(owner)
                            + "; last shatter: " + com.solegendary.reignofnether.mana.DecoyServerEvents.lastShatter());
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
        "celestial_observatory", "heavens_gate",
        "metal_extractor_brass", "metal_extractor_t2_brass", "wind_generator_brass", "energy_converter_brass", "storage_vault_brass",
        "foundry_hall", "machine_shop", "assembly_works", "great_forge");

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
                if (!com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder.isWater(kind)
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

    // ------------------------------------------------------------------ Stormcallers slice 4 (the rest of T2, Heaven's Gate, the Celestial Dragon)

    /**
     * Stormcallers T3: the Jade Pagoda offers Tier 3; Heaven's Gate is a T3 lab that the server refuses from a Geomancer
     * alone and accepts from a Ley Master; it raises the Celestial Dragon only once its owner has Tier 3; the bot kit
     * techs to it; and the dragon costs the standard experimental price, dies with the T3 death blast and carries
     * Tempest. The Observatory trains the three slice-4 T2 units.
     */
    @GameTest(template = ARENA)
    public static void stormcallers_t3_lab_and_tier_3(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_storm_t3_" + helper.absolutePos(BlockPos.ZERO).asLong();
        var lab = Buildings.HEAVENS_GATE;
        var dragonItem = com.solegendary.reignofnether.building.production.ProductionItems.CELESTIAL_DRAGON;
        if (!Buildings.JADE_PAGODA.productions.get().contains(com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_3))
            helper.fail("the Jade Pagoda does not offer Tier 3");
        if (!Factions.STORMCALLERS.equals(lab.getFaction()))
            helper.fail("Heaven's Gate is not a Stormcallers building");
        if (!com.solegendary.reignofnether.unit.T2Workers.isT3Lab(lab))
            helper.fail("Heaven's Gate is not registered as a T3 lab");
        if (!lab.productions.get().contains(dragonItem))
            helper.fail("Heaven's Gate does not raise the Celestial Dragon");
        var obs = Buildings.CELESTIAL_OBSERVATORY.productions.get();
        if (!obs.contains(com.solegendary.reignofnether.building.production.ProductionItems.SEER)
                || !obs.contains(com.solegendary.reignofnether.building.production.ProductionItems.LANTERN_FORTRESS)
                || !obs.contains(com.solegendary.reignofnether.building.production.ProductionItems.PHOENIX))
            helper.fail("the Celestial Observatory does not train the Seer, the Lantern Fortress and the Phoenix");
        var kit = com.solegendary.reignofnether.bot.BotPlayer.kitFor(Factions.STORMCALLERS);
        if (kit == null || kit.t3Building() != lab || kit.t3Unit() != dragonItem)
            helper.fail("the Stormcallers bot kit does not tech to Heaven's Gate and the Celestial Dragon");
        var cost = com.solegendary.reignofnether.resources.ResourceCosts.CELESTIAL_DRAGON;
        var wtwCost = com.solegendary.reignofnether.resources.ResourceCosts.WORLD_TREE_WALKER;
        if (cost.metal() != wtwCost.metal() || cost.energy() != wtwCost.energy() || cost.population != wtwCost.population)
            helper.fail("the Celestial Dragon does not cost the standard experimental price: " + cost.metal() + "m vs " + wtwCost.metal() + "m");
        if (com.solegendary.reignofnether.barfx.BarFx.tierOf(cost.metal()) != 3)
            helper.fail("the Celestial Dragon would not die with the T3 death blast");

        BlockPos base = helper.absolutePos(new BlockPos(10, 2, 10));
        var geo = com.solegendary.reignofnether.registrars.EntityRegistrar.GEOMANCER_UNIT.get().create(level);
        var ley = com.solegendary.reignofnether.registrars.EntityRegistrar.LEY_MASTER_UNIT.get().create(level);
        var dragon = com.solegendary.reignofnether.registrars.EntityRegistrar.CELESTIAL_DRAGON_UNIT.get().create(level);
        if (geo == null || ley == null || dragon == null) {
            helper.fail("could not create the units");
            return;
        }
        geo.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);
        ley.moveTo(base.getX() + 2.5, base.getY(), base.getZ() + 0.5, 0, 0);
        level.addFreshEntity(geo);
        level.addFreshEntity(ley);
        com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(owner);
        // two ticks later, not in the same tick as addFreshEntity: buildersMayPlace looks the builders up by id, and a
        // unit added to a chunk the entity manager has not finished loading is not in that lookup yet (CI 38512391670,
        // 38512850549, 38515818335 under eight parallel suites: "Heaven's Gate refused a Ley Master among the builders")
        helper.runAfterDelay(2, () -> {
        try {
            if (!Factions.STORMCALLERS.equals(Factions.getFaction(dragon)))
                helper.fail("the Celestial Dragon is not Stormcallers");
            if (dragon.getAbilities().get().stream().noneMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.Tempest))
                helper.fail("the Celestial Dragon has no Tempest");
            if (dragon.getMaxHealth() < 1000 || dragon.getMaxHealth() > 1200)
                helper.fail("Celestial Dragon health " + dragon.getMaxHealth());
            if (com.solegendary.reignofnether.unit.T2Workers.buildersMayPlace(level, lab, new int[] { geo.getId() }))
                helper.fail("Heaven's Gate may be placed by a Geomancer alone");
            if (!com.solegendary.reignofnether.unit.T2Workers.buildersMayPlace(level, lab, new int[] { geo.getId(), ley.getId() }))
                helper.fail("Heaven's Gate refused a Ley Master among the builders");
            var placed = com.solegendary.reignofnether.building.BuildingServerEvents.placeBuilding(lab, base.offset(-80, 0, -80),
                Rotation.NONE, owner, new int[] { geo.getId() }, false, false, false, true);
            if (placed != null) {
                com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(placed);
                helper.fail("placeBuilding accepted a Heaven's Gate from a Geomancer");
            }
            var bp = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(lab, level,
                helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, owner, false);
            if (!(bp instanceof com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement pp)) {
                helper.fail("Heaven's Gate is not a production placement: " + bp);
                return;
            }
            com.solegendary.reignofnether.research.ResearchServerEvents.addResearch(owner,
                com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_2);
            if (dragonItem.canProduce(pp))
                helper.fail("the Celestial Dragon can be raised with only Tier 2");
            if (pp.startProductionItem(dragonItem) || !pp.productionQueue.isEmpty())
                helper.fail("the server queued a Celestial Dragon without Tier 3");
            com.solegendary.reignofnether.research.ResearchServerEvents.addResearch(owner,
                com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_3);
            if (!dragonItem.canProduce(pp))
                helper.fail("the Celestial Dragon still refused after Tier 3: " + dragonItem.getProduceErrorMsg(pp));
            helper.succeed();
        } finally {
            com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(owner);
            geo.discard();
            ley.discard();
            dragon.discard();
        }
        });
    }

    /**
     * The Phoenix's rebirth (OnceRevive): the first lethal hit leaves it alive as an ember - its once-flag set, 30
     * health, no target, no dive - and after its ember time it rises at half health; the second lethal hit kills it
     * for good. The Seer, made alongside, strips a cloaked Shade Ranger's cover within its reach and not a friend's.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void phoenix_rises_once_from_its_ember_and_the_seer_reveals(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_storm_phoenix_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        openGround(helper, 0, 0, 15, 15);
        var pool = manaPool(owner, 500);
        com.solegendary.reignofnether.mana.ManaServerEvents.bindTraits(owner, FactionTraits.STORMCALLERS);
        var phoenix = com.solegendary.reignofnether.registrars.EntityRegistrar.PHOENIX_UNIT.get().create(level);
        var seer = com.solegendary.reignofnether.registrars.EntityRegistrar.SEER_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);          // the killer (never added)
        var hidden = com.solegendary.reignofnether.registrars.EntityRegistrar.SHADE_RANGER_UNIT.get().create(level);   // a cloaked foe
        var hiddenFriend = com.solegendary.reignofnether.registrars.EntityRegistrar.SHADE_RANGER_UNIT.get().create(level);
        if (phoenix == null || seer == null || foe == null || hidden == null || hiddenFriend == null) {
            helper.fail("could not create the Phoenix test units");
            dropManaPool(owner, pool);
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(7, 2, 7));
        phoenix.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        seer.moveTo(at.getX() + 4.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foe.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 6.5, 0, 0);
        hidden.moveTo(at.getX() + 4.5, at.getY(), at.getZ() + 5.5, 0, 0);
        hiddenFriend.moveTo(at.getX() + 4.5, at.getY(), at.getZ() - 4.5, 0, 0);
        phoenix.setOwnerName(owner);
        seer.setOwnerName(owner);
        hiddenFriend.setOwnerName(owner);
        foe.setOwnerName(foeOwner);
        hidden.setOwnerName(foeOwner);
        for (var e : java.util.List.of(phoenix, seer, hidden, hiddenFriend)) {
            e.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            level.addFreshEntity(e);   // the ember rises from its own tick; the Seer finds cloaks through the unit grid
        }
        Runnable cleanup = () -> {
            phoenix.discard();
            seer.discard();
            foe.discard();
            hidden.discard();
            hiddenFriend.discard();
            dropManaPool(owner, pool);
        };
        float[] diveEnergy = new float[1];
        helper.runAfterDelay(5, () -> {
            try {
                if (!phoenix.isFlyingUnit())
                    helper.fail("the Phoenix is not a flyer");
                // a dive is a spell: it pays its energy and lands as a projectile hit plus a burn
                float f0 = foe.getHealth();
                foe.invulnerableTime = 0;
                if (!phoenix.dive(foe) || foe.getHealth() >= f0 || !foe.isOnFire())
                    helper.fail("the fire dive did not hurt and burn its target");
                diveEnergy[0] = 500f - pool.getEnergy();
                if (Math.abs(diveEnergy[0] - com.solegendary.reignofnether.unit.units.storm.PhoenixUnit.ENERGY_PER_DIVE) > 0.01f)
                    helper.fail("the dive did not charge its energy: " + diveEnergy[0]);
                foe.clearFire();
                // the lethal hit: cancelled, the Phoenix is an ember
                phoenix.invulnerableTime = 0;
                phoenix.hurt(level.damageSources().indirectMagic(foe, foe), 10_000f);
                if (!phoenix.isAlive() || phoenix.isRemoved())
                    helper.fail("the Phoenix died of its first lethal hit");
                if (!phoenix.isEmber() || !phoenix.hasRisen() || !com.solegendary.reignofnether.unit.OnceRevive.isSpent(phoenix))
                    helper.fail("the Phoenix is not an ember after its first death");
                if (Math.abs(phoenix.getHealth() - com.solegendary.reignofnether.unit.units.storm.PhoenixUnit.EMBER_HEALTH) > 0.01f)
                    helper.fail("the ember has " + phoenix.getHealth() + " health");
                if (phoenix.getTarget() != null || phoenix.dive(foe))
                    helper.fail("the ember still fights");
                if (phoenix.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) > 0.0001)
                    helper.fail("the ember still moves");
                // the Seer: the cloaked foe in its reach is stripped of its cover, the cloaked friend is not
                hidden.setInvisible(true);   // a cloak is the invisible flag (ShadeRangerUnit.isCloaked)
                hiddenFriend.setInvisible(true);
                int lit = seer.reveal(level, foeOwner);
                if (lit != 1)
                    helper.fail("the Seer revealed " + lit + " units, not the one cloaked foe");
                if (com.solegendary.reignofnether.unit.Concealment.isConcealed(hidden))
                    helper.fail("the cloaked foe is still concealed after the Seer's sweep");
                if (!com.solegendary.reignofnether.unit.Concealment.isConcealed(hiddenFriend))
                    helper.fail("the Seer stripped a friend's cloak");
            } catch (Throwable t) {
                cleanup.run();
                throw t;
            }
        });
        helper.runAfterDelay(5 + com.solegendary.reignofnether.unit.units.storm.PhoenixUnit.EMBER_TICKS + 10, () -> {
            try {
                if (phoenix.isEmber() || !phoenix.isAlive())
                    helper.fail("the ember did not rise: ember " + phoenix.isEmber() + ", alive " + phoenix.isAlive());
                float want = phoenix.getMaxHealth() * com.solegendary.reignofnether.unit.units.storm.PhoenixUnit.REBIRTH_HEALTH_FRACTION;
                if (phoenix.getHealth() < want - 0.01f)
                    helper.fail("the Phoenix rose with " + phoenix.getHealth() + " health, not " + want);
                if (phoenix.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) <= 0.0001)
                    helper.fail("the risen Phoenix cannot move");
                // the second lethal hit is final
                phoenix.invulnerableTime = 0;
                phoenix.hurt(level.damageSources().indirectMagic(foe, foe), 10_000f);
                if (phoenix.isAlive() && !phoenix.isDeadOrDying())
                    helper.fail("the Phoenix survived a second lethal hit");
                helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    /**
     * The Lantern Fortress's dome: raised by the toggle, it pays its first second, soaks a projectile on a friend inside
     * entirely (the dome's capacity drops by the hit) and not on an enemy inside; starved of energy it collapses on its
     * next second and the Fortress is barred from raising another until the collapse delay has passed, after which the
     * toggle brings it back.
     */
    @GameTest(template = ARENA, timeoutTicks = 260)
    public static void lantern_dome_soaks_projectiles_on_friends_and_collapses_when_starved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_storm_dome_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        openGround(helper, 0, 0, 15, 15);
        var pool = manaPool(owner, 500);
        com.solegendary.reignofnether.mana.ManaServerEvents.bindTraits(owner, FactionTraits.STORMCALLERS);
        var fortress = com.solegendary.reignofnether.registrars.EntityRegistrar.LANTERN_FORTRESS_UNIT.get().create(level);
        var friend = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        var foeInside = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        var shooter = com.solegendary.reignofnether.registrars.EntityRegistrar.FIRE_ADEPT_UNIT.get().create(level);      // the enemy archer
        var ourShooter = com.solegendary.reignofnether.registrars.EntityRegistrar.FIRE_ADEPT_UNIT.get().create(level);   // ours, for the foe inside
        if (fortress == null || friend == null || foeInside == null || shooter == null || ourShooter == null) {
            helper.fail("could not create the Lantern Dome test units");
            dropManaPool(owner, pool);
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(7, 2, 7));
        fortress.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        friend.moveTo(at.getX() + 2.5, at.getY(), at.getZ() + 0.5, 0, 0);
        foeInside.moveTo(at.getX() - 2.5, at.getY(), at.getZ() + 0.5, 0, 0);
        shooter.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 7.5, 0, 0);
        ourShooter.moveTo(at.getX() + 0.5, at.getY(), at.getZ() - 6.5, 0, 0);
        fortress.setOwnerName(owner);
        friend.setOwnerName(owner);
        ourShooter.setOwnerName(owner);
        foeInside.setOwnerName(foeOwner);
        shooter.setOwnerName(foeOwner);
        // only the Fortress joins the level (its tick keeps the dome); the others' AI must not fight between the shots
        fortress.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        level.addFreshEntity(fortress);
        Runnable cleanup = () -> {
            fortress.discard();
            friend.discard();
            foeInside.discard();
            shooter.discard();
            ourShooter.discard();
            dropManaPool(owner, pool);
        };
        com.solegendary.reignofnether.mana.LanternDomeServerEvents.Dome[] dome = new com.solegendary.reignofnether.mana.LanternDomeServerEvents.Dome[1];
        float dmg = com.solegendary.reignofnether.unit.units.storm.FireAdeptUnit.attackDamage;
        float perSecond = com.solegendary.reignofnether.mana.LanternDomeServerEvents.ENERGY_PER_SECOND;
        int delay = com.solegendary.reignofnether.mana.LanternDomeServerEvents.COLLAPSE_DELAY_TICKS;
        helper.runAfterDelay(5, () -> {
            try {
                if (fortress.getAbilities().get().stream().noneMatch(a -> a instanceof com.solegendary.reignofnether.ability.abilities.LanternDome))
                    helper.fail("the Lantern Fortress has no Lantern Dome");
                fortress.setDomeWanted(true);
                dome[0] = fortress.keepDome(level);
                if (dome[0] == null)
                    dome[0] = com.solegendary.reignofnether.mana.LanternDomeServerEvents.domeOf(fortress);   // its own tick may have raised it
                if (dome[0] == null) {
                    helper.fail("the toggle raised no dome");
                    return;
                }
                if (Math.abs(pool.getEnergy() - (500f - perSecond)) > 0.01f)
                    helper.fail("the dome did not pay its first second: " + pool.getEnergy());
                friend.invulnerableTime = 0;
                float f0 = friend.getHealth(), cap0 = dome[0].remaining;
                shooter.fireball(friend);
                if (friend.getHealth() < f0 - 0.01f)
                    helper.fail("a friend inside the dome took " + (f0 - friend.getHealth()) + " of a " + dmg + " projectile");
                if (dome[0].remaining >= cap0)
                    helper.fail("the dome soaked nothing: " + dome[0].remaining + " of " + cap0 + " left");
                foeInside.invulnerableTime = 0;
                float e0 = foeInside.getHealth();
                ourShooter.fireball(foeInside);
                float foeTaken = e0 - foeInside.getHealth();
                if (foeTaken <= 0 || Math.abs(foeTaken - dmg) > 0.6f)
                    helper.fail("an enemy inside the dome took " + foeTaken + " of a " + dmg + " projectile (the dome is not its)");
                friend.clearFire();
                foeInside.clearFire();
                pool.addEnergy(-pool.getEnergy());   // starved: the next second cannot be paid at all
            } catch (Throwable t) {
                cleanup.run();
                throw t;
            }
        });
        helper.runAfterDelay(5 + 25, () -> {
            try {
                if (dome[0] != null && dome[0].isActive())
                    helper.fail("a starved dome kept standing");
                if (!fortress.domeBlocked())
                    helper.fail("the Fortress is not barred from a new dome after the collapse");
                pool.addEnergy(500f);
                if (fortress.keepDome(level) != null || com.solegendary.reignofnether.mana.LanternDomeServerEvents.domeOf(fortress) != null)
                    helper.fail("a dome rose during the collapse delay");
            } catch (Throwable t) {
                cleanup.run();
                throw t;
            }
        });
        helper.runAfterDelay(5 + 25 + delay + 25, () -> {
            try {
                var again = com.solegendary.reignofnether.mana.LanternDomeServerEvents.domeOf(fortress);
                if (again == null)
                    again = fortress.keepDome(level);
                if (again == null || !again.isActive())
                    helper.fail("the toggle did not raise the dome again after the collapse delay");
                else
                    helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    /**
     * The Celestial Dragon is grounded by wind: three wind-type hits adding up to the threshold within the window bring
     * it down - no longer a flyer (melee can reach it), half speed, +50% damage taken, Tempest refused - and after the
     * grounded time it takes off again. An ordinary hit of the same size does not count toward the threshold.
     */
    @GameTest(template = ARENA, timeoutTicks = 240)
    public static void celestial_dragon_is_grounded_by_wind_damage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_storm_wind_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        openGround(helper, 0, 0, 15, 15);
        var pool = manaPool(owner, 2000);
        com.solegendary.reignofnether.mana.ManaServerEvents.bindTraits(owner, FactionTraits.STORMCALLERS);
        var dragon = com.solegendary.reignofnether.registrars.EntityRegistrar.CELESTIAL_DRAGON_UNIT.get().create(level);
        var monk = com.solegendary.reignofnether.registrars.EntityRegistrar.WIND_MONK_UNIT.get().create(level);     // wind-type AA (never added)
        var archer = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);   // an ordinary attacker
        if (dragon == null || monk == null || archer == null) {
            helper.fail("could not create the grounding test units");
            dropManaPool(owner, pool);
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(7, 2, 7));
        dragon.moveTo(at.getX() + 0.5, at.getY() + 3, at.getZ() + 0.5, 0, 0);
        monk.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 6.5, 0, 0);
        archer.moveTo(at.getX() + 6.5, at.getY(), at.getZ() + 0.5, 0, 0);
        dragon.setOwnerName(owner);
        monk.setOwnerName(foeOwner);
        archer.setOwnerName(foeOwner);
        dragon.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        level.addFreshEntity(dragon);   // its tick brings it down and up again
        Runnable cleanup = () -> {
            dragon.discard();
            monk.discard();
            archer.discard();
            dropManaPool(owner, pool);
        };
        float threshold = com.solegendary.reignofnether.unit.units.storm.CelestialDragonUnit.WIND_THRESHOLD;
        helper.runAfterDelay(5, () -> {
            try {
                if (!(monk instanceof com.solegendary.reignofnether.unit.interfaces.WindAntiAir))
                    helper.fail("the Wind Monk is not wind-type anti-air");
                if (!dragon.isFlyingUnit())
                    helper.fail("the Celestial Dragon is not a flyer");
                float speedUp = (float) dragon.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
                // an ordinary hit of the threshold's size changes nothing
                dragon.invulnerableTime = 0;
                dragon.hurt(level.damageSources().indirectMagic(archer, archer), threshold);
                if (dragon.isGrounded())
                    helper.fail("an ordinary hit grounded the dragon");
                // three wind hits adding up past the threshold ground it
                float part = threshold / 3f + 0.5f;
                for (int i = 0; i < 3; i++) {
                    dragon.invulnerableTime = 0;
                    dragon.hurt(level.damageSources().indirectMagic(monk, monk), part);
                    if (i < 2 && dragon.isGrounded())
                        helper.fail("the dragon was grounded after only " + (i + 1) + " wind hits");
                }
                if (!dragon.isGrounded())
                    helper.fail("three wind hits past the threshold did not ground the dragon");
                if (dragon.isFlyingUnit())
                    helper.fail("a grounded dragon still counts as a flyer (melee could not reach it)");
                float speedDown = (float) dragon.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
                if (Math.abs(speedDown - speedUp * 0.5f) > 0.001f)
                    helper.fail("a grounded dragon moves at " + speedDown + ", not half of " + speedUp);
                // +50% damage taken while down
                dragon.invulnerableTime = 0;
                float h0 = dragon.getHealth();
                dragon.hurt(level.damageSources().indirectMagic(archer, archer), 20f);
                float taken = h0 - dragon.getHealth();
                if (Math.abs(taken - 20f * com.solegendary.reignofnether.unit.units.storm.CelestialDragonUnit.GROUNDED_DAMAGE_MULT) > 0.6f)
                    helper.fail("a grounded dragon took " + taken + " of a 20 hit");
                // no Tempest while grounded: refused, nothing paid
                float e0 = pool.getEnergy();
                if (com.solegendary.reignofnether.ability.abilities.Tempest.cast(level, dragon, owner, archer.blockPosition(), true) != null
                        || pool.getEnergy() != e0)
                    helper.fail("a grounded dragon cast Tempest");
            } catch (Throwable t) {
                cleanup.run();
                throw t;
            }
        });
        helper.runAfterDelay(5 + com.solegendary.reignofnether.unit.units.storm.CelestialDragonUnit.GROUNDED_TICKS + 10, () -> {
            try {
                if (dragon.isGrounded() || !dragon.isFlyingUnit())
                    helper.fail("the dragon did not take off again after its grounded time");
                helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    /**
     * Tempest: the cast pays its up-front cost and the storm's first second; the storm strikes a foe in the clump inside
     * it (never the friend standing among them, never a foe outside), pays again a second later, and fizzles once the
     * pool cannot pay a second at all.
     */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void tempest_strikes_clumped_foes_never_friends_and_fizzles_when_starved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_storm_tempest_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        openGround(helper, 0, 0, 15, 15);
        var pool = manaPool(owner, 1000);
        com.solegendary.reignofnether.mana.ManaServerEvents.bindTraits(owner, FactionTraits.STORMCALLERS);
        var vill = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get();
        var dragon = com.solegendary.reignofnether.registrars.EntityRegistrar.CELESTIAL_DRAGON_UNIT.get().create(level);   // never added: it must not fight
        var f1 = vill.create(level);
        var f2 = vill.create(level);
        var f3 = vill.create(level);
        var friend = vill.create(level);    // ours, in the middle of the clump
        var farFoe = vill.create(level);    // a foe well outside the storm
        List<net.minecraft.world.entity.Mob> all = new ArrayList<>();
        for (net.minecraft.world.entity.Mob e : java.util.Arrays.<net.minecraft.world.entity.Mob>asList(dragon, f1, f2, f3, friend, farFoe))
            if (e != null)
                all.add(e);
        Runnable cleanup = () -> {
            for (var e : all) e.discard();
            dropManaPool(owner, pool);
        };
        if (all.size() != 6) {
            helper.fail("could not create the Tempest test units");
            cleanup.run();
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 7));
        dragon.moveTo(at.getX() + 0.5, at.getY() + 4, at.getZ() + 0.5, -90, 0);
        // the clump 4 blocks east of the dragon (inside the storm from its first tick), the far foe 12 blocks off
        f1.moveTo(at.getX() + 4.5, at.getY(), at.getZ() + 0.5, 0, 0);
        f2.moveTo(at.getX() + 5.5, at.getY(), at.getZ() + 1.5, 0, 0);
        f3.moveTo(at.getX() + 4.5, at.getY(), at.getZ() + 2.5, 0, 0);
        friend.moveTo(at.getX() + 5.5, at.getY(), at.getZ() + 0.5, 0, 0);
        farFoe.moveTo(at.getX() + 12.5, at.getY(), at.getZ() + 7.5, 0, 0);
        dragon.setOwnerName(owner);
        friend.setOwnerName(owner);
        for (var e : java.util.List.of(f1, f2, f3, farFoe))
            e.setOwnerName(foeOwner);
        for (var e : java.util.List.of(f1, f2, f3, friend, farFoe)) {
            e.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            level.addFreshEntity(e);   // the storm finds its targets through the unit grid
        }
        float up = com.solegendary.reignofnether.mana.TempestServerEvents.ENERGY_UPFRONT;
        float perSecond = com.solegendary.reignofnether.mana.TempestServerEvents.ENERGY_PER_SECOND;
        com.solegendary.reignofnether.mana.TempestServerEvents.Storm[] storm = new com.solegendary.reignofnether.mana.TempestServerEvents.Storm[1];
        float[] hp = new float[5];
        helper.runAfterDelay(5, () -> {
            try {
                var foes = java.util.List.of(f1, f2, f3, friend, farFoe);
                for (int i = 0; i < foes.size(); i++)
                    hp[i] = foes.get(i).getHealth();
                BlockPos aim = BlockPos.containing(at.getX() + 5, at.getY(), at.getZ() + 1);
                storm[0] = com.solegendary.reignofnether.ability.abilities.Tempest.cast(level, dragon, owner, aim, true);
                if (storm[0] == null) {
                    helper.fail("Tempest raised no storm: " + com.solegendary.reignofnether.mana.ManaServerEvents.lastRefusal());
                    return;
                }
                if (Math.abs(pool.getEnergy() - (1000f - up - perSecond)) > 0.01f)
                    helper.fail("Tempest did not pay its up-front cost and first second: " + pool.getEnergy());
            } catch (Throwable t) {
                cleanup.run();
                throw t;
            }
        });
        helper.runAfterDelay(5 + 25, () -> {
            try {
                if (storm[0] == null)
                    return;
                if (!storm[0].isActive() || storm[0].strikes < 1)
                    helper.fail("the storm struck " + storm[0].strikes + " times in its first second");
                if (Math.abs(pool.getEnergy() - (1000f - up - 2 * perSecond)) > 0.01f)
                    helper.fail("the storm did not pay its second second: " + pool.getEnergy());
                boolean clumpHurt = f1.getHealth() < hp[0] || f2.getHealth() < hp[1] || f3.getHealth() < hp[2];
                if (!clumpHurt)
                    helper.fail("no foe in the clump was struck");
                if (friend.getHealth() < hp[3])
                    helper.fail("the storm struck a friend");
                if (farFoe.getHealth() < hp[4])
                    helper.fail("the storm reached a foe outside it");
                pool.addEnergy(-pool.getEnergy());   // starved
            } catch (Throwable t) {
                cleanup.run();
                throw t;
            }
        });
        helper.runAfterDelay(5 + 25 + 25, () -> {
            try {
                if (storm[0] != null && storm[0].isActive())
                    helper.fail("a starved Tempest kept rolling");
                else
                    helper.succeed();
            } finally {
                cleanup.run();
            }
        });
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
     * Team resign vote (ResignVoteServerEvents.Vote, the pure tally): the caller's vote is a yes; it passes on a strict
     * majority of the voters (2 of 2, 2 of 3, 3 of 4, 5 of 8); it fails as soon as enough have said no that the
     * majority is out of reach, or when the window runs out; changing your answer while it is open is allowed, a
     * non-voter's answer is ignored. No players or level involved, so nothing here can be seen by another test.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void resign_vote_tally_needs_a_majority_of_living_humans(GameTestHelper helper) {
        java.util.function.BiFunction<Integer, Integer, com.solegendary.reignofnether.player.ResignVoteServerEvents.Vote> vote =
            (n, end) -> {
                java.util.Set<String> team = new java.util.LinkedHashSet<>();
                for (int i = 0; i < n; i++)
                    team.add("gametest_resign_" + i);
                return new com.solegendary.reignofnether.player.ResignVoteServerEvents.Vote("gametest_resign_0", team, end);
            };
        // majority sizes
        if (vote.apply(2, 600).needed() != 2 || vote.apply(3, 600).needed() != 2 || vote.apply(4, 600).needed() != 3
                || vote.apply(8, 600).needed() != 5) {
            helper.fail("majority sizes wrong: " + vote.apply(2, 600).needed() + "/" + vote.apply(3, 600).needed()
                + "/" + vote.apply(4, 600).needed() + "/" + vote.apply(8, 600).needed());
            return;
        }
        // 2 humans: the caller alone is 1 of 2, open; the other's yes passes it
        var two = vote.apply(2, 600);
        if (two.result(0) != com.solegendary.reignofnether.player.ResignVoteServerEvents.Result.OPEN) {
            helper.fail("a fresh 2-voter vote should be open, not " + two.result(0));
            return;
        }
        two.cast("gametest_resign_1", true);
        if (two.result(0) != com.solegendary.reignofnether.player.ResignVoteServerEvents.Result.PASSED) {
            helper.fail("2 of 2 yes should pass");
            return;
        }
        // 3 humans: one no keeps it open (2 could still say yes), a second no makes the majority unreachable
        var three = vote.apply(3, 600);
        three.cast("gametest_resign_1", false);
        if (three.result(0) != com.solegendary.reignofnether.player.ResignVoteServerEvents.Result.OPEN) {
            helper.fail("1 yes / 1 no of 3 should still be open");
            return;
        }
        three.cast("gametest_resign_2", false);
        if (three.result(0) != com.solegendary.reignofnether.player.ResignVoteServerEvents.Result.FAILED) {
            helper.fail("1 yes / 2 no of 3 can no longer pass");
            return;
        }
        // changing your mind: a no turned into a yes passes 2 of 3
        var change = vote.apply(3, 600);
        change.cast("gametest_resign_1", false);
        change.cast("gametest_resign_1", true);
        if (change.yes.size() != 2 || !change.no.isEmpty()
                || change.result(0) != com.solegendary.reignofnether.player.ResignVoteServerEvents.Result.PASSED) {
            helper.fail("a changed answer should count once, as the latest answer");
            return;
        }
        // a stranger (a bot, an enemy) cannot vote
        var stranger = vote.apply(4, 600);
        if (stranger.cast("gametest_resign_bot", true) || stranger.yes.size() != 1) {
            helper.fail("a non-voter's answer was counted");
            return;
        }
        // the window: open at tick 599, failed at tick 600 with no majority
        if (stranger.result(599) != com.solegendary.reignofnether.player.ResignVoteServerEvents.Result.OPEN
                || stranger.result(600) != com.solegendary.reignofnether.player.ResignVoteServerEvents.Result.FAILED) {
            helper.fail("the vote should fail exactly when its window ends");
            return;
        }
        helper.succeed();
    }

    /**
     * The voters of a resign vote are the living human members of the caller's team: bots never vote, a defeated
     * ally (no longer an RTS player) does not count, and a player with no living human ally resigns alone (the
     * caller's team is just itself). Uses its own names in rtsPlayers and the alliance map, removed in finally.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void resign_vote_voters_are_the_living_human_allies(GameTestHelper helper) {
        String me = "gametest_rv_me", human = "gametest_rv_human", gone = "gametest_rv_gone", bot = "gametest_rv_bot";
        var rtsPlayers = com.solegendary.reignofnether.player.PlayerServerEvents.rtsPlayers;
        // humans have non-negative ids (RTSPlayer.isBot is id < 0); getNewPlayer takes an explicit id
        var meP = com.solegendary.reignofnether.player.RTSPlayer.getNewPlayer(me, Factions.VILLAGERS, 1_000_001, 0, true, -1L);
        var humanP = com.solegendary.reignofnether.player.RTSPlayer.getNewPlayer(human, Factions.VILLAGERS, 1_000_002, 0, true, -1L);
        var botP = com.solegendary.reignofnether.player.RTSPlayer.getNewBot(bot, Factions.MONSTERS);
        synchronized (rtsPlayers) {
            rtsPlayers.add(meP);
            rtsPlayers.add(humanP);
            rtsPlayers.add(botP);
        }
        com.solegendary.reignofnether.alliance.AlliancesServerEvents.addAlliance(me, human);
        com.solegendary.reignofnether.alliance.AlliancesServerEvents.addAlliance(me, gone);
        com.solegendary.reignofnether.alliance.AlliancesServerEvents.addAlliance(me, bot);
        try {
            var team = com.solegendary.reignofnether.player.ResignVoteServerEvents.livingHumanTeam(me);
            if (!team.contains(me) || !team.contains(human))
                helper.fail("the caller and its living human ally must both vote: " + team);
            else if (team.contains(bot))
                helper.fail("bots do not vote: " + team);
            else if (team.contains(gone))
                helper.fail("a defeated ally is not a voter: " + team);
            else if (team.size() != 2)
                helper.fail("expected exactly 2 voters: " + team);
            else {
                // with the human ally gone the caller is alone: the vote path resigns at once
                synchronized (rtsPlayers) {
                    rtsPlayers.remove(humanP);
                }
                var alone = com.solegendary.reignofnether.player.ResignVoteServerEvents.livingHumanTeam(me);
                if (alone.size() != 1 || !alone.contains(me))
                    helper.fail("alone with bots the team is just the caller: " + alone);
                else
                    helper.succeed();
            }
        } finally {
            synchronized (rtsPlayers) {
                rtsPlayers.remove(meP);
                rtsPlayers.remove(humanP);
                rtsPlayers.remove(botP);
            }
            com.solegendary.reignofnether.alliance.AlliancesServerEvents.removeAlliance(me, human);
            com.solegendary.reignofnether.alliance.AlliancesServerEvents.removeAlliance(me, gone);
            com.solegendary.reignofnether.alliance.AlliancesServerEvents.removeAlliance(me, bot);
        }
    }

    /**
     * Player panel eco bars: the stored / storage figures ride on the same ally-only gate as the income, so an
     * enemy's line never carries them, and the fill is clamped to 0-1 (an overflowing pool shows a full bar).
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void player_panel_eco_bars_only_for_allies(GameTestHelper helper) {
        String me = "gametest_ecobar_me", ally = "gametest_ecobar_ally", foe = "gametest_ecobar_foe";
        List<com.solegendary.reignofnether.player.PlayerPanelServerEvents.Row> rows = List.of(
            new com.solegendary.reignofnether.player.PlayerPanelServerEvents.Row(foe, "", true, 9, 90, 0.5f, 500, 1000, 900, 1000),
            new com.solegendary.reignofnether.player.PlayerPanelServerEvents.Row(ally, "", true, 3, 30, 1f, 1200, 1000, 250, 1000),
            new com.solegendary.reignofnether.player.PlayerPanelServerEvents.Row(me, "", true, 2, 20, 0.25f, 100, 1000, 0, 1000));
        com.solegendary.reignofnether.alliance.AlliancesServerEvents.addAlliance(me, ally);
        var meSide = com.solegendary.reignofnether.player.RTSPlayer.getNewBot(me, Factions.VILLAGERS);
        var rtsPlayers = com.solegendary.reignofnether.player.PlayerServerEvents.rtsPlayers;
        synchronized (rtsPlayers) {
            rtsPlayers.add(meSide);
        }
        try {
            var list = com.solegendary.reignofnether.player.PlayerPanelServerEvents.entriesFor(me, rows);
            var mine = list.get(0);
            var allyE = list.get(1);
            var foeE = list.get(2);
            if (mine.metalStorage() != 1000 || mine.metalStored() != 100 || Math.abs(mine.metalFill() - 0.1f) > 1e-4f)
                helper.fail("own line should carry stored/storage: " + mine);
            else if (Math.abs(allyE.metalFill() - 1f) > 1e-4f || Math.abs(allyE.energyFill() - 0.25f) > 1e-4f)
                helper.fail("ally fill should be clamped to 1 / read 0.25: " + allyE.metalFill() + " " + allyE.energyFill());
            else if (foeE.metalStorage() != 0 || foeE.metalStored() != 0 || foeE.energyStorage() != 0 || foeE.metalFill() != 0)
                helper.fail("the enemy line leaked stored / storage: " + foeE);
            else
                helper.succeed();
        } finally {
            synchronized (rtsPlayers) {
                rtsPlayers.remove(meSide);
            }
            com.solegendary.reignofnether.alliance.AlliancesServerEvents.removeAlliance(me, ally);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // lovish (Horde, Repeat on his Fortress): "the mammoth unit and other units bugged when being made - sometimes
    // they wouldn't spawn". Production completed (cost spent, queue advanced), nothing was logged, and no unit the
    // player could see appeared. What the code did: the outdoor and rally spawn points name a cell at the building's
    // FLOOR level - a solid block - so ProductionPlacement.safeSpawnPoint always searched rings out from inside the
    // ground, and its scoring made a cave or a ledge up to 8 below the lab's corner as cheap as (and tried before) the
    // ground 1 above it: the unit was placed under the base. A body wider than a block (the War Mammoth is 3.3
    // across) searched only 1 up, so beside raised ground it fell back to the one-cell rule and was buried in the
    // wall. And EntityType.spawn hands back an entity that is not in the world when any mod's FinalizeSpawn handler
    // cancels the spawn, which produceUnit took for a success. These tests stamp every production building at every
    // rotation and have it train every unit it offers through the real ProductionItem.onComplete path (never a direct
    // entity create), with and without a rally point across the base, and check 40 ticks later that the unit is
    // alive, owned, not in a wall, not pocketed and near its building; the spawn search is checked over a cave and
    // against raised ground; and a unit that is not placed is refunded and reported. Eight buckets by structure name,
    // each on its own row of pads far south of the grid, one building per pad, 4 chunks apart.

    private static final int SPAWN_BUCKETS = 8;
    private static final int SPAWN_CHECK_TICKS = 40;

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void production_buildings_place_every_unit_0(GameTestHelper helper) { productionBuildingsPlaceEveryUnit(helper, 0); }
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void production_buildings_place_every_unit_1(GameTestHelper helper) { productionBuildingsPlaceEveryUnit(helper, 1); }
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void production_buildings_place_every_unit_2(GameTestHelper helper) { productionBuildingsPlaceEveryUnit(helper, 2); }
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void production_buildings_place_every_unit_3(GameTestHelper helper) { productionBuildingsPlaceEveryUnit(helper, 3); }
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void production_buildings_place_every_unit_4(GameTestHelper helper) { productionBuildingsPlaceEveryUnit(helper, 4); }
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void production_buildings_place_every_unit_5(GameTestHelper helper) { productionBuildingsPlaceEveryUnit(helper, 5); }
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void production_buildings_place_every_unit_6(GameTestHelper helper) { productionBuildingsPlaceEveryUnit(helper, 6); }
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void production_buildings_place_every_unit_7(GameTestHelper helper) { productionBuildingsPlaceEveryUnit(helper, 7); }

    /** A unit item a building trains in a plain game: not a hero (gated to one per owner) and not a hero revive. */
    private static boolean plainUnitItem(com.solegendary.reignofnether.building.production.ProductionItem item) {
        return item instanceof com.solegendary.reignofnether.building.production.IUnitProductionItem
            && !(item instanceof com.solegendary.reignofnether.building.production.HeroProductionItem)
            && !(item instanceof com.solegendary.reignofnether.building.production.ReviveHeroProductionItem)
            && com.solegendary.reignofnether.registrars.EntityRegistrar.getEntityType(item.getItemName()) != null;
    }

    /**
     * Stone to 9 deep at and below the structure's ground layer (its y=0 is the foundation, in the ground), open air
     * above, {@code margin} cells past the footprint, then the structure stamped over it (flag 2: no neighbour
     * updates, nothing pops off). The ground is deep so a spawn point sunk into it (the Central Portal's is 5 under
     * its floor) meets ground, as in a real base, not the void.
     */
    private static void stampOnPad(ServerLevel level, com.solegendary.reignofnether.building.BuildingPlacement bp, int margin) {
        BlockPos min = bp.minCorner, max = bp.maxCorner;
        int gy = min.getY();
        var stone = Blocks.STONE.defaultBlockState();
        var air = Blocks.AIR.defaultBlockState();
        for (int x = min.getX() - margin; x <= max.getX() + margin; x++)
            for (int z = min.getZ() - margin; z <= max.getZ() + margin; z++) {
                for (int y = gy - 8; y <= gy; y++)
                    level.setBlock(new BlockPos(x, y, z), stone, 2);
                for (int y = gy + 1; y <= max.getY() + 6; y++)
                    level.setBlock(new BlockPos(x, y, z), air, 2);
            }
        for (var b : bp.getBlocks())
            if (b.getBlockPos().getY() > gy || !b.getBlockState().isAir())   // a sparse ground layer keeps the terrain
                level.setBlock(b.getBlockPos(), b.getBlockState(), 2);
    }

    /**
     * Force-loads (or releases) the chunks of a pad (the footprint and {@code reach} blocks round it), so what spawns
     * there ticks like a base's units: a chunk only setBlock loaded is a border chunk, whose entities neither tick nor
     * show up in queries once it has dropped out a moment later - the first run of these tests lost Ghasts that flew
     * two chunks and saw nothing of a Leviathan spawned on the rally side of a big lab.
     */
    private static void forcePad(ServerLevel level, BlockPos min, BlockPos max, int reach, boolean force) {
        // async FORCED tickets and a bounded wait (GameTestChunks): setChunkForced generated each chunk synchronously
        // on the server thread with no way out (CI hangs). The pad is still loaded when this returns (the stamp
        // and the job timeline count on it); a chunk that never loads fails the test after 60 s instead
        List<long[]> chunks = new ArrayList<>();
        for (int cx = (min.getX() - reach) >> 4; cx <= (max.getX() + reach) >> 4; cx++)
            for (int cz = (min.getZ() - reach) >> 4; cz <= (max.getZ() + reach) >> 4; cz++)
                chunks.add(new long[] { cx, cz });
        if (force)
            GameTestChunks.holdAndAwaitOrFail(level, chunks, "spawn pad");
        else
            GameTestChunks.releaseAll(level, chunks);
    }

    /** Whether every chunk of a pad ({@link #forcePad}) is entity-ticking: its blocks collide and its units tick. */
    private static boolean padTicking(ServerLevel level, BlockPos min, BlockPos max, int reach) {
        for (int cx = (min.getX() - reach) >> 4; cx <= (max.getX() + reach) >> 4; cx++)
            for (int cz = (min.getZ() - reach) >> 4; cz <= (max.getZ() + reach) >> 4; cz++)
                if (!level.isPositionEntityTicking(new BlockPos(cx << 4, min.getY(), cz << 4)))
                    return false;
        return true;
    }

    /** "building/rotation(rally)" for a failure message. */
    private static String jobName(Building building, Rotation rotation, boolean rally) {
        return building.structureName + "/" + rotation.name().toLowerCase() + (rally ? "(rally)" : "");
    }

    /** Horizontal distance from a unit to the nearest cell of a footprint. */
    private static double distToFootprint(net.minecraft.world.entity.Entity e, BlockPos min, BlockPos max) {
        double dx = Math.max(0, Math.max(min.getX() - e.getX(), e.getX() - (max.getX() + 1)));
        double dz = Math.max(0, Math.max(min.getZ() - e.getZ(), e.getZ() - (max.getZ() + 1)));
        return Math.sqrt(dx * dx + dz * dz);
    }

    private record SpawnedUnit(String job, String item, net.minecraft.world.entity.Mob mob, BlockPos min, BlockPos max, String atSpawn) { }

    /** Where and how a unit stands right after spawning, for a failure message. */
    private static String spawnState(net.minecraft.world.entity.Mob m) {
        return String.format("spawned at %.1f/%.1f/%.1f%s%s%s hp %.0f/%.0f", m.getX(), m.getY(), m.getZ(),
            m.isInWall() ? " in-wall" : "", com.solegendary.reignofnether.unit.UnitUnstick.isEncased(m) ? " encased" : "",
            com.solegendary.reignofnether.unit.UnitUnstick.isOnStandableGround(m) ? "" : " unstandable",
            m.getHealth(), m.getMaxHealth());
    }

    /** What a unit "in a wall" is in: its box, eye height, velocity, and the suffocating blocks at its eye level. */
    private static String wallState(net.minecraft.world.entity.Mob m) {
        var box = m.getBoundingBox();
        double eye = m.getEyeY();
        StringBuilder in = new StringBuilder();
        for (BlockPos p : BlockPos.betweenClosed((int) Math.floor(box.minX), (int) Math.floor(eye), (int) Math.floor(box.minZ),
                (int) Math.floor(box.maxX), (int) Math.floor(eye), (int) Math.floor(box.maxZ))) {
            var bs = m.level().getBlockState(p);
            if (bs.isSuffocating(m.level(), p))
                in.append(' ').append(bs.getBlock().getName().getString()).append('@').append(p.toShortString());
        }
        return String.format("box %.1f/%.1f/%.1f..%.1f/%.1f/%.1f, eye y %.2f, velocity %.3f/%.3f/%.3f, suffocating at eye level:%s",
            box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, eye,
            m.getDeltaMovement().x, m.getDeltaMovement().y, m.getDeltaMovement().z, in.length() == 0 ? " none" : in);
    }

    /** Why a unit is gone: removal reason, last damage, its box and the hot blocks round it, for a failure message. */
    private static String goneState(net.minecraft.world.entity.Mob m) {
        var src = m.getLastDamageSource();
        var box = m.getBoundingBox();
        StringBuilder hot = new StringBuilder();
        var scan = box.inflate(1.0);
        for (BlockPos p : BlockPos.betweenClosed((int) Math.floor(scan.minX), (int) Math.floor(scan.minY), (int) Math.floor(scan.minZ),
                (int) Math.floor(scan.maxX), (int) Math.floor(scan.maxY), (int) Math.floor(scan.maxZ))) {
            var bs = m.level().getBlockState(p);
            if (!bs.getFluidState().isEmpty() || bs.is(net.minecraft.tags.BlockTags.FIRE))
                hot.append(' ').append(bs.getBlock().getName().getString()).append('@').append(p.toShortString());
        }
        return (m.isRemoved() ? "removed " + m.getRemovalReason() : "dead") + ", hp " + m.getHealth()
            + (src == null ? ", no damage" : ", last damage " + src.getMsgId())
            + String.format(", box %.1f/%.1f/%.1f..%.1f/%.1f/%.1f", box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)
            + ", hot blocks within 1:" + (hot.length() == 0 ? " none" : hot);
    }

    /** One (building, rotation, rally) of the every-unit test, driven a tick at a time by {@link #productionBuildingsPlaceEveryUnit}. */
    private static final class SpawnJob {
        final Building building;
        final Rotation rotation;
        final boolean rally;
        final int sx, sz;
        final String name;
        com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement pp;
        int stampedAt = -1, checkAt = -1;
        boolean done;
        final List<SpawnedUnit> units = new ArrayList<>();

        SpawnJob(Building building, Rotation rotation, boolean rally, int sx, int sz) {
            this.building = building; this.rotation = rotation; this.rally = rally; this.sx = sx; this.sz = sz;
            this.name = jobName(building, rotation, rally);
        }
    }

    private static void productionBuildingsPlaceEveryUnit(GameTestHelper helper, int bucket) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_spawn_" + bucket;
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y0 = 200, margin = 12;   // the spawn search reaches 10 cells past a footprint
        // one job every jobTicks, each bucket starting a little later than the one before: the fewer units these
        // tests keep alive at once, the less they weigh on the stress benchmark that runs beside them
        final int jobTicks = 5, start = 1 + bucket * 20, lastTick = 560;
        // rows 1024 apart per bucket: no two tests' rows can meet whatever their arenas' positions
        int chunkX = base.getX() >> 4, chunkZ = (base.getZ() + 9000 + bucket * 1024) >> 4;
        List<SpawnJob> jobs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Building building : ReignOfNetherRegistries.BUILDING) {
            if (!(building instanceof com.solegendary.reignofnether.building.production.ProductionBuilding pb)
                    || building instanceof AbstractBridge || building.structureName == null || !seen.add(building.structureName))
                continue;
            if (pb.productions.get().stream().noneMatch(SkirmishGameTests::plainUnitItem))
                continue;
            if (Math.floorMod(building.structureName.hashCode(), SPAWN_BUCKETS) != bucket)
                continue;
            for (Rotation r : Rotation.values()) {
                int j = jobs.size();
                jobs.add(new SpawnJob(building, r, false, ((chunkX + j * 4) << 4) + 8, (chunkZ << 4) + 8));
                j = jobs.size();
                jobs.add(new SpawnJob(building, r, true, ((chunkX + j * 4) << 4) + 8, (chunkZ << 4) + 8));
            }
        }
        if (jobs.isEmpty()) {
            helper.succeed();   // the names hash into the other buckets
            return;
        }
        List<String> problems = new ArrayList<>();
        int[] produced = { 0 }, next = { 0 }, nextStart = { start };
        boolean[] finished = { false };
        Runnable finish = () -> {
            if (finished[0])
                return;
            finished[0] = true;
            for (SpawnJob job : jobs) {
                for (SpawnedUnit su : job.units)
                    if (!su.mob().isRemoved())
                        su.mob().discard();
                if (job.pp != null)
                    forcePad(level, job.pp.minCorner, job.pp.maxCorner, 24, false);
                if (!job.done)
                    problems.add(job.name + ": never finished");
            }
            if (produced[0] == 0)
                problems.add("bucket " + bucket + " produced nothing at all");
            if (!problems.isEmpty()) {
                int n = problems.size();
                helper.fail(n + " problem(s): " + String.join("; ", problems.subList(0, Math.min(n, 25))));
            } else
                helper.succeed();
        };
        // every tick: start the next job when due (force-load and stamp its pad), make the units of a stamped job
        // once its pad is entity-ticking (a chunk only setBlock loaded is a border chunk: its entities neither tick
        // nor show in queries), check them 40 ticks later and clear the pad
        for (int t = 1; t <= lastTick; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (finished[0])
                    return;
                if (next[0] < jobs.size() && tick >= nextStart[0]) {
                    SpawnJob job = jobs.get(next[0]++);
                    nextStart[0] = tick + jobTicks;
                    var bp = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(job.building, level,
                        new BlockPos(job.sx, y0, job.sz), job.rotation, owner, false);
                    if (!(bp instanceof com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement pp)
                            || bp.getBlocks().isEmpty()) {
                        problems.add(job.name + ": no production placement");
                        job.done = true;
                    } else if (bp.maxCorner.getX() - bp.minCorner.getX() > 40 || bp.maxCorner.getZ() - bp.minCorner.getZ() > 40) {
                        problems.add(job.name + ": footprint too big for its pad");
                        job.done = true;
                    } else {
                        forcePad(level, bp.minCorner, bp.maxCorner, 24, true);
                        stampOnPad(level, bp, margin);
                        pp.isBuilt = true;
                        job.pp = pp;
                        job.stampedAt = tick;
                    }
                }
                boolean allDone = true;
                for (SpawnJob job : jobs) {
                    if (job.done)
                        continue;
                    allDone = false;
                    if (job.pp == null)
                        continue;
                    if (job.checkAt < 0) {
                        var pp = job.pp;
                        if (!padTicking(level, pp.minCorner, pp.maxCorner, 24)) {
                            if (tick - job.stampedAt > 200) {
                                problems.add(job.name + ": its pad never became entity-ticking");
                                job.done = true;
                            }
                            continue;
                        }
                        if (job.rally)   // across the base: a cell on the ground 9 past the far corner
                            pp.setRallyPoint(new BlockPos(pp.maxCorner.getX() + 9, pp.minCorner.getY() + 1, pp.maxCorner.getZ() + 9));
                        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(pp.minCorner, pp.maxCorner.offset(1, 1, 1)).inflate(24);
                        Set<Integer> known = new HashSet<>();
                        for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box))
                            known.add(m.getId());
                        var pb = (com.solegendary.reignofnether.building.production.ProductionBuilding) job.building;
                        for (var item : pb.productions.get()) {
                            if (!plainUnitItem(item))
                                continue;
                            item.onComplete.accept(level, pp);   // the real path: ActiveProduction.complete -> onComplete -> produceUnit
                            boolean found = false;
                            for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box)) {
                                if (!known.add(m.getId()) || !(m instanceof com.solegendary.reignofnether.unit.interfaces.Unit u)
                                        || !owner.equals(u.getOwnerName()))
                                    continue;
                                found = true;
                                job.units.add(new SpawnedUnit(job.name, item.getItemName(), m, pp.minCorner, pp.maxCorner, spawnState(m)));
                            }
                            if (!found) {
                                // where produceUnit would have put it
                                var type = com.solegendary.reignofnether.registrars.EntityRegistrar.getEntityType(item.getItemName());
                                BlockPos wanted = job.rally && !pp.getRallyPoints().isEmpty()
                                    ? pp.getClosestGroundPos(pp.getRallyPoints().get(0), (int) pb.spawnRadiusOffset)
                                    : pp.getDefaultOutdoorSpawnPoint();
                                BlockPos safe = com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement.safeSpawnPoint(level, wanted, type);
                                problems.add(job.name + ": " + item.getItemName() + " produced nothing (footprint " + pp.minCorner.toShortString() + ".."
                                    + pp.maxCorner.toShortString() + ", wanted " + wanted.toShortString() + " -> safe " + (safe == null ? "null" : safe.toShortString()) + ")");
                            }
                            produced[0]++;
                        }
                        job.checkAt = tick + SPAWN_CHECK_TICKS;
                    } else if (tick >= job.checkAt) {
                        for (SpawnedUnit su : job.units) {
                            var m = su.mob();
                            var u = (com.solegendary.reignofnether.unit.interfaces.Unit) m;
                            String where = String.format("%.1f/%.1f/%.1f", m.getX(), m.getY(), m.getZ());
                            String tag = su.job() + ": " + su.item() + " at " + where + " ";
                            if (!m.isAlive() || m.isRemoved())
                                problems.add(tag + "is gone (" + goneState(m) + "; " + su.atSpawn() + ")");
                            else {
                                if (!owner.equals(u.getOwnerName()))
                                    problems.add(tag + "has owner " + u.getOwnerName());
                                if (m.isInWall())   // CI 38594088866: the Sky Corsair again, after the flyer sweep - say what it is in
                                    problems.add(tag + "is in a wall (" + wallState(m) + "; " + su.atSpawn() + ")");
                                if (!u.isFlyingUnit() && com.solegendary.reignofnether.unit.UnitUnstick.isPocketed(level,
                                        com.solegendary.reignofnether.unit.UnitUnstick.standCell(m)))
                                    problems.add(tag + "is pocketed");
                                // a flyer idling on its rally order drifts: twice the reach of a walker. A walker that
                                // reached the rally point (9 past the far corner on both axes) stands 12.0 from the
                                // footprint, so the limit is 14, not 12: fast units (Jade Qilin, Clockwork Spider)
                                // that got there within the 40 ticks failed at 12.1-12.3 on several branches
                                double d = distToFootprint(m, su.min(), su.max());
                                if (d > (u.isFlyingUnit() ? 24 : 14) || m.getY() < su.min().getY() - 1 || m.getY() > su.max().getY() + 16)
                                    problems.add(tag + String.format("is %.1f from its building", d));
                            }
                            m.discard();
                        }
                        job.units.clear();
                        forcePad(level, job.pp.minCorner, job.pp.maxCorner, 24, false);
                        job.done = true;
                    }
                }
                if (allDone || tick == lastTick)
                    finish.run();
            });
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // lovish's Oct 19 LAN playtest (video): a Mossback Bison fresh from the Circle of Elders stood beside the lab's
    // corner for 20+ s and ignored every move order (the order lines pointed away, it never took a step) while every
    // other unit walked. The every-unit test above checks that a trained unit is alive, free and near its lab - never
    // that it can LEAVE. This one trains every body wider than a block (the wide footprints, and the beasts built on
    // Alex's Mobs bodies) through the real ProductionItem.onComplete path, gives it a plain move order 12 blocks out
    // from its lab over open ground and wants it at least 6 of them nearer within 12 s. One (building, unit) per pad,
    // rotations cycled across the set; the Circle of Elders' bison at all four. The game's default pathfinding is
    // used as it is (the rtsPathfinding gamerule is off on an ordinary world, as in that LAN game, and in CI).

    private static final int WALK_ORDER_DELAY = 20;     // after the spawn: past the lab's own rally order (10 ticks)
    private static final int WALK_TICKS = 240;
    private static final int WALK_ORDER_DIST = 12;
    private static final double WALK_PROGRESS = 6.0;
    private static final int WALK_JOB_TICKS = 10, WALK_PAD_MARGIN = 26, WALK_PAD_REACH = 32, WALK_LAST_TICK = 2300;

    /** One (building, rotation, wide unit) of {@link #wide_units_walk_away_from_their_lab}. */
    private static final class WalkJob {
        final Building building;
        final Rotation rotation;
        final com.solegendary.reignofnether.building.production.ProductionItem item;
        final int sx, sz;
        final String name;
        com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement pp;
        int stampedAt = -1, spawnedAt = -1, orderedAt = -1;
        net.minecraft.world.entity.Mob mob;
        String atSpawn = "";
        BlockPos target;
        double startDist;
        boolean done;

        WalkJob(Building building, Rotation rotation, com.solegendary.reignofnether.building.production.ProductionItem item, int sx, int sz) {
            this.building = building; this.rotation = rotation; this.item = item; this.sx = sx; this.sz = sz;
            this.name = jobName(building, rotation, false) + " " + item.getItemName();
        }
    }

    /** {@link #stampOnPad} (margin 12), with a further ring of flat ground out to {@link #WALK_PAD_MARGIN} to walk on. */
    private static void stampWalkPad(ServerLevel level, com.solegendary.reignofnether.building.BuildingPlacement bp) {
        stampOnPad(level, bp, 12);
        BlockPos min = bp.minCorner, max = bp.maxCorner;
        int gy = min.getY();
        var stone = Blocks.STONE.defaultBlockState();
        var air = Blocks.AIR.defaultBlockState();
        for (int x = min.getX() - WALK_PAD_MARGIN; x <= max.getX() + WALK_PAD_MARGIN; x++)
            for (int z = min.getZ() - WALK_PAD_MARGIN; z <= max.getZ() + WALK_PAD_MARGIN; z++) {
                if (x >= min.getX() - 12 && x <= max.getX() + 12 && z >= min.getZ() - 12 && z <= max.getZ() + 12)
                    continue;
                level.setBlock(new BlockPos(x, gy - 1, z), stone, 2);
                level.setBlock(new BlockPos(x, gy, z), stone, 2);
                for (int y = gy + 1; y <= gy + 5; y++)
                    level.setBlock(new BlockPos(x, y, z), air, 2);
            }
    }

    private static double horizontalDist(net.minecraft.world.entity.Entity e, BlockPos cell) {
        double dx = e.getX() - (cell.getX() + 0.5), dz = e.getZ() - (cell.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Why a unit did not walk: its navigation, path and move goal, for a failure message. */
    private static String walkState(net.minecraft.world.entity.Mob m) {
        var u = (com.solegendary.reignofnether.unit.interfaces.Unit) m;
        var path = m.getNavigation().getPath();
        return "navigation " + m.getNavigation().getClass().getSimpleName() + ", navDone " + m.getNavigation().isDone()
            + ", path " + (path == null ? "none" : path.getNodeCount() + " nodes, next " + path.getNextNodeIndex()
                + (path.canReach() ? "" : ", can't reach"))
            + ", move target " + (u.getMoveGoal() == null ? "no move goal" : u.getMoveGoal().getMoveTarget())
            + ", hold " + u.getHoldPosition() + ", rtsPathfinding " + com.solegendary.reignofnether.unit.UnitServerEvents.rtsPathfinding;
    }

    @GameTest(template = ARENA, timeoutTicks = 2400)
    public static void wide_units_walk_away_from_their_lab(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_walk_away";
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y0 = 200;
        // its own row, past the every-unit test's eight (z + 9000 .. + 16168) and short of the biome bases (z + 21000);
        // pads 8 chunks apart, so no two pads' held chunks meet
        int chunkX = base.getX() >> 4, chunkZ = (base.getZ() + 18500) >> 4;
        List<WalkJob> jobs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int cycled = 0;
        boolean bisonAtCircle = false;
        for (Building building : ReignOfNetherRegistries.BUILDING) {
            if (!(building instanceof com.solegendary.reignofnether.building.production.ProductionBuilding pb)
                    || building instanceof AbstractBridge || building.structureName == null || !seen.add(building.structureName))
                continue;
            for (var item : pb.productions.get()) {
                if (!plainUnitItem(item))
                    continue;
                var type = com.solegendary.reignofnether.registrars.EntityRegistrar.getEntityType(item.getItemName());
                if (type == null || type.getWidth() <= 1.0f)
                    continue;
                boolean everyRotation = building == Buildings.CIRCLE_OF_ELDERS
                    && item.getItemName().equals(com.solegendary.reignofnether.building.production.ProductionItems.MOSSBACK_BISON.getItemName());
                bisonAtCircle |= everyRotation;
                for (int r = 0; r < (everyRotation ? 4 : 1); r++) {
                    Rotation rotation = Rotation.values()[everyRotation ? r : cycled % 4];
                    int j = jobs.size();
                    jobs.add(new WalkJob(building, rotation, item, ((chunkX + j * 8) << 4) + 8, (chunkZ << 4) + 8));
                }
                cycled++;
            }
        }
        if (!bisonAtCircle) {
            helper.fail("the Circle of Elders no longer trains the Mossback Bison as a wide body - the test setup is wrong");
            return;
        }
        List<String> problems = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int[] next = { 0 }, nextStart = { 1 };
        boolean[] finished = { false };
        java.util.function.Consumer<WalkJob> close = job -> {
            if (job.mob != null && !job.mob.isRemoved())
                job.mob.discard();
            if (job.pp != null)
                forcePad(level, job.pp.minCorner, job.pp.maxCorner, WALK_PAD_REACH, false);
            job.done = true;
        };
        Runnable finish = () -> {
            if (finished[0])
                return;
            finished[0] = true;
            for (WalkJob job : jobs)
                if (!job.done) {
                    problems.add(job.name + ": never finished");
                    close.accept(job);
                }
            if (!skipped.isEmpty())
                ReignOfNether.LOGGER.info("[GameTest] wide_units_walk_away_from_their_lab skipped (flyers): {}", skipped);
            if (!problems.isEmpty()) {
                int n = problems.size();
                helper.fail(n + " of " + jobs.size() + " wide unit(s) did not walk away from their lab: "
                    + String.join("; ", problems.subList(0, Math.min(n, 12))));
            } else
                helper.succeed();
        };
        for (int t = 1; t <= WALK_LAST_TICK; t++) {
            final int tick = t;
            helper.runAfterDelay(t, () -> {
                if (finished[0])
                    return;
                if (next[0] < jobs.size() && tick >= nextStart[0]) {
                    WalkJob job = jobs.get(next[0]++);
                    nextStart[0] = tick + WALK_JOB_TICKS;
                    var bp = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(job.building, level,
                        new BlockPos(job.sx, y0, job.sz), job.rotation, owner, false);
                    if (!(bp instanceof com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement pp)
                            || bp.getBlocks().isEmpty()) {
                        problems.add(job.name + ": no production placement");
                        job.done = true;
                    } else {
                        forcePad(level, bp.minCorner, bp.maxCorner, WALK_PAD_REACH, true);
                        stampWalkPad(level, bp);
                        pp.isBuilt = true;
                        job.pp = pp;
                        job.stampedAt = tick;
                    }
                }
                boolean allDone = next[0] >= jobs.size();
                for (WalkJob job : jobs) {
                    if (job.done || job.pp == null)
                        continue;
                    allDone = false;
                    var pp = job.pp;
                    if (job.spawnedAt < 0) {
                        if (!padTicking(level, pp.minCorner, pp.maxCorner, WALK_PAD_REACH)) {
                            if (tick - job.stampedAt > 200) {
                                problems.add(job.name + ": its pad never became entity-ticking");
                                close.accept(job);
                            }
                            continue;
                        }
                        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(pp.minCorner, pp.maxCorner.offset(1, 1, 1)).inflate(WALK_PAD_REACH);
                        Set<Integer> known = new HashSet<>();
                        for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box))
                            known.add(m.getId());
                        job.item.onComplete.accept(level, pp);   // the real path: ActiveProduction.complete -> onComplete -> produceUnit
                        var wanted = com.solegendary.reignofnether.registrars.EntityRegistrar.getEntityType(job.item.getItemName());
                        for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box))
                            if (!known.contains(m.getId()) && m instanceof com.solegendary.reignofnether.unit.interfaces.Unit u
                                    && owner.equals(u.getOwnerName()) && (job.mob == null || m.getType() == wanted))
                                job.mob = m;   // the trained body itself, not a rider or mount made with it
                        job.spawnedAt = tick;
                        if (job.mob == null) {
                            problems.add(job.name + ": produced nothing");
                            close.accept(job);
                        } else
                            job.atSpawn = spawnState(job.mob);
                        continue;
                    }
                    var m = job.mob;
                    if (!m.isAlive() || m.isRemoved()) {
                        problems.add(job.name + ": is gone (" + goneState(m) + "; " + job.atSpawn + ")");
                        close.accept(job);
                        continue;
                    }
                    if (job.orderedAt < 0) {
                        if (tick < job.spawnedAt + WALK_ORDER_DELAY)
                            continue;
                        if (((com.solegendary.reignofnether.unit.interfaces.Unit) m).isFlyingUnit()) {
                            skipped.add(job.name);   // a flyer has no footprint to walk out of
                            close.accept(job);
                            continue;
                        }
                        // straight out from the footprint's centre through where the unit stands, on the pad's ground
                        double cx = (pp.minCorner.getX() + pp.maxCorner.getX() + 1) / 2.0;
                        double cz = (pp.minCorner.getZ() + pp.maxCorner.getZ() + 1) / 2.0;
                        double dx = m.getX() - cx, dz = m.getZ() - cz, len = Math.sqrt(dx * dx + dz * dz);
                        if (len < 0.5) { dx = -1; dz = -1; len = Math.sqrt(2); }
                        job.target = new BlockPos((int) Math.floor(m.getX() + dx / len * WALK_ORDER_DIST), pp.minCorner.getY() + 1,
                            (int) Math.floor(m.getZ() + dz / len * WALK_ORDER_DIST));
                        job.startDist = horizontalDist(m, job.target);
                        job.orderedAt = tick;
                        com.solegendary.reignofnether.unit.UnitServerEvents.addActionItem(owner,
                            com.solegendary.reignofnether.unit.UnitAction.MOVE, -1, new int[] { m.getId() }, job.target, BlockPos.ZERO);
                        continue;
                    }
                    double d = horizontalDist(m, job.target);
                    if (job.startDist - d >= WALK_PROGRESS) {
                        close.accept(job);
                    } else if (tick - job.orderedAt >= WALK_TICKS) {
                        problems.add(String.format("%s: ordered %.1f out to %s, got %.1f nearer in %d ticks, now at %.1f/%.1f/%.1f (%s; %s)",
                            job.name, job.startDist, job.target.toShortString(), job.startDist - d, WALK_TICKS,
                            m.getX(), m.getY(), m.getZ(), walkState(m), job.atSpawn));
                        close.accept(job);
                    }
                }
                if (allDone || tick == WALK_LAST_TICK)
                    finish.run();
            });
        }
    }

    /**
     * The spawn search over the ground a real base stands on: a spawn point named at the lab's floor level (a solid
     * block) with a wide cave 3 under it goes to the ground 1 above, never into the cave (the old scoring tried the
     * cave first: that is a unit that "never spawned"); a War Mammoth's body is put where it fits, on the ground
     * beside raised terrain 3 up (the old wide-body search looked 1 up only and then buried half of it); and the one
     * named in the air on a slope's downhill side lands on the ground under it.
     */
    @GameTest(template = ARENA)
    public static void spawn_search_prefers_the_ground_over_a_cave_under_the_building(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        final int y = 300;   // high in its own column, over nothing: the ground layer named by the spawn point
        var stone = Blocks.STONE.defaultBlockState();
        var air = Blocks.AIR.defaultBlockState();
        // y-4 cave floor, y-3/y-2 the cave (the whole column: far cells reachable, so it is not a pocket), y-1/y ground
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++) {
                BlockPos c = corner.offset(dx, 0, dz);
                level.setBlock(c.atY(y - 4), stone, 2);
                level.setBlock(c.atY(y - 3), air, 2);
                level.setBlock(c.atY(y - 2), air, 2);
                level.setBlock(c.atY(y - 1), stone, 2);
                level.setBlock(c.atY(y), stone, 2);
                for (int dy = 1; dy <= 8; dy++)
                    level.setBlock(c.atY(y + dy), air, 2);
            }
        BlockPos floor = corner.offset(8, 0, 8).atY(y);
        if (!com.solegendary.reignofnether.unit.UnitUnstick.isStandable(level, floor.below(3))
                || com.solegendary.reignofnether.unit.UnitUnstick.isPocketed(level, floor.below(3)))
            helper.fail("the test cave is not an open standable space - the setup is wrong");
        BlockPos narrow = com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement.safeSpawnPoint(level, floor, null);
        if (!narrow.equals(floor.above()))
            helper.fail("a unit spawned from the floor cell goes to " + narrow.subtract(corner).toShortString()
                + " instead of the ground above it (" + floor.above().subtract(corner).toShortString() + ")");
        var mammoth = com.solegendary.reignofnether.registrars.EntityRegistrar.WAR_MAMMOTH_UNIT.get();
        if (mammoth.getWidth() <= 1.0f)
            helper.fail("the War Mammoth is not a wide body: " + mammoth.getWidth());
        BlockPos wide = com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement.safeSpawnPoint(level, floor, mammoth);
        if (wide == null || !wide.equals(floor.above()))
            helper.fail("a War Mammoth spawned from the floor cell goes to " + (wide == null ? "nowhere" : wide.subtract(corner).toShortString()));
        // raised ground: the columns x >= 6 are 3 higher, the named cell is under it; the mammoth belongs on top
        for (int dx = 6; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++)
                for (int dy = 1; dy <= 3; dy++)
                    level.setBlock(corner.offset(dx, 0, dz).atY(y + dy), stone, 2);
        BlockPos raised = com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement.safeSpawnPoint(level, floor, mammoth);
        if (raised == null || !com.solegendary.reignofnether.unit.UnitUnstick.isStandable(level, raised)
                || !com.solegendary.reignofnether.unit.UnitUnstick.bodyFits(level, raised, mammoth.getWidth(), mammoth.getHeight()))
            helper.fail("beside raised ground the War Mammoth goes to " + (raised == null ? "nowhere" : raised.subtract(corner).toShortString()) + ", where it does not fit");
        else if (raised.getY() != y + 4 && raised.getY() != y + 1)
            helper.fail("beside raised ground the War Mammoth goes to " + raised.subtract(corner).toShortString() + " - not on the ground");
        // a cell named in the air (x = 2 is 3 lower than the raised ground at x = 6): the ground under it
        BlockPos inAir = corner.offset(2, 0, 8).atY(y + 3);
        BlockPos down = com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement.safeSpawnPoint(level, inAir, null);
        if (!down.equals(inAir.below(2)))
            helper.fail("a unit spawned from a cell in the air goes to " + down.subtract(corner).toShortString()
                + " instead of the ground under it (" + inAir.below(2).subtract(corner).toShortString() + ")");
        helper.succeed();
    }

    /** The owner's resource pool for a test (made if missing), with metal and energy zeroed. */
    private static com.solegendary.reignofnether.resources.Resources testPool(String owner) {
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
        pool.wood = 0;
        return pool;
    }

    /**
     * A stamped, finished Central Portal (unregistered, ticked by the test) with a Grunt queued at the front of its
     * queue and ready to complete on the next tick, for the two tests below.
     */
    @javax.annotation.Nullable
    private static com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement readyPortal(
            GameTestHelper helper, String owner, int padY) {
        ServerLevel level = helper.getLevel();
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        var bp = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(Buildings.CENTRAL_PORTAL, level,
            new BlockPos(corner.getX() + 2, padY, corner.getZ() + 3), Rotation.NONE, owner, false);
        if (!(bp instanceof com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement pp)) {
            helper.fail("the Central Portal is not a production placement: " + bp);
            return null;
        }
        stampOnPad(level, bp, 2);
        pp.isBuilt = true;
        var grunt = com.solegendary.reignofnether.building.production.ProductionItems.GRUNT;
        if (!pp.startProductionItem(grunt) || pp.productionQueue.size() != 1) {
            helper.fail("the server refused to queue a Grunt: queue " + pp.productionQueue.size());
            return null;
        }
        pp.productionQueue.get(0).ticksLeft = 0;
        return pp;
    }

    /**
     * The safety net: a unit that is gone within a second of being produced for any reason but a fight (here killed
     * outright, as a cancelled or buried spawn would be) has what was paid for it refunded and its owner told; one
     * that lives is not refunded.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void unit_lost_right_after_production_is_refunded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_spawn_refund_" + helper.absolutePos(BlockPos.ZERO).asLong();
        var pool = testPool(owner);
        var pp = readyPortal(helper, owner, 75);
        if (pp == null)
            return;
        var active = pp.productionQueue.get(0);
        active.metalSpent = 120f;
        active.energySpent = 45f;
        pp.tick(level);   // completes the Grunt: ProductionItem.tick -> ActiveProduction.complete -> produceUnit
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(pp.minCorner, pp.maxCorner.offset(1, 1, 1)).inflate(16);
        net.minecraft.world.entity.Mob grunt = null;
        for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box))
            if (m instanceof com.solegendary.reignofnether.unit.interfaces.Unit u && owner.equals(u.getOwnerName()))
                grunt = m;
        if (grunt == null) {
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
            helper.fail("the Central Portal produced no Grunt");
            return;
        }
        if (pp.spawnWatchCount() != 1)
            helper.fail("the produced Grunt is not being watched: " + pp.spawnWatchCount());
        final var g = grunt;
        for (int t = 1; t <= com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement.SPAWN_WATCH_TICKS + 2; t++)
            helper.runAfterDelay(t, () -> pp.tick(level));
        helper.runAfterDelay(5, g::kill);   // generic kill: no attacker, as a buried or cancelled spawn
        helper.runAfterDelay(com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement.SPAWN_WATCH_TICKS + 4, () -> {
            try {
                if (pp.spawnWatchCount() != 0)
                    helper.fail("the watch did not end: " + pp.spawnWatchCount());
                if (Math.round(pool.getMetal()) != 120 || pool.wood != 45)
                    helper.fail("the lost Grunt was not refunded: " + pool.getMetal() + "m " + pool.wood + "e (expected 120m 45e)");
                helper.succeed();
            } finally {
                for (var w : com.solegendary.reignofnether.resources.WreckServerEvents.getWrecks())
                    if (!w.isRemoved() && box.contains(w.position()))
                        w.discard();
                g.discard();
                com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
            }
        });
    }

    /**
     * BAR's Repeat on a lab (lovish's Fortress had it on): a completed unit re-queues itself at the back with a fresh
     * build time, the unit is placed each time, and a unit that lives is not refunded by the safety net.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void repeat_queue_requeues_and_places_the_unit_each_time(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_spawn_repeat_" + helper.absolutePos(BlockPos.ZERO).asLong();
        var pool = testPool(owner);
        var pp = readyPortal(helper, owner, 90);
        if (pp == null)
            return;
        var grunt = com.solegendary.reignofnether.building.production.ProductionItems.GRUNT;
        int fullTicks = grunt.getCost(false, owner).ticks;
        pp.repeatQueue = true;
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(pp.minCorner, pp.maxCorner.offset(1, 1, 1)).inflate(16);
        List<net.minecraft.world.entity.Mob> grunts = new ArrayList<>();
        Runnable collect = () -> {
            for (var m : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box))
                if (m instanceof com.solegendary.reignofnether.unit.interfaces.Unit u && owner.equals(u.getOwnerName()) && !grunts.contains(m))
                    grunts.add(m);
        };
        Runnable cleanup = () -> {
            grunts.forEach(net.minecraft.world.entity.Entity::discard);
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
        };
        for (int round = 1; round <= 2; round++) {
            pp.tick(level);
            collect.run();
            if (grunts.size() != round) {
                cleanup.run();
                helper.fail("round " + round + ": " + grunts.size() + " Grunt(s) placed");
                return;
            }
            if (pp.productionQueue.size() != 1 || pp.productionQueue.get(0).item != grunt
                    || pp.productionQueue.get(0).ticksLeft < fullTicks - 1) {
                cleanup.run();
                helper.fail("round " + round + ": Repeat did not re-queue the Grunt with a fresh build time: queue "
                    + pp.productionQueue.size() + (pp.productionQueue.isEmpty() ? "" : ", ticks " + pp.productionQueue.get(0).ticksLeft + "/" + fullTicks));
                return;
            }
            pp.productionQueue.get(0).ticksLeft = 0;
        }
        for (int t = 1; t <= com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement.SPAWN_WATCH_TICKS + 2; t++)
            helper.runAfterDelay(t, () -> {
                // a third completes on its own as the test ticks the building; the two that live are never refunded
                pp.tick(level);
                collect.run();
            });
        helper.runAfterDelay(com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement.SPAWN_WATCH_TICKS + 4, () -> {
            try {
                if (grunts.size() < 3)
                    helper.fail("Repeat stopped: " + grunts.size() + " Grunts after three completions");
                for (var g : grunts)
                    if (!g.isAlive())
                        helper.fail("a repeated Grunt died: " + describe(g));
                if (pool.getMetal() != 0 || pool.wood != 0)
                    helper.fail("living Grunts were refunded: " + pool.getMetal() + "m " + pool.wood + "e");
                helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }
    /** A bot brain with a known enemy site, for the army tests: no capitol, no economy - just BotArmy.run. */
    private static com.solegendary.reignofnether.bot.BotPlayer armyBrain(ServerLevel level, String owner, BlockPos home, BlockPos enemySite) {
        var brain = new com.solegendary.reignofnether.bot.BotPlayer(owner, Factions.VILLAGERS,
            com.solegendary.reignofnether.bot.BotPlayer.Difficulty.HARD, home);
        // only this test's raider counts as an enemy: the other tests' units share the world (one parked a Leafblade
        // 16 blocks above this pad's base once and flipped the brain to DEFEND)
        brain.enemyFilter = o -> o.startsWith("gametest_bot_raider");
        // (seeded, not put: the sightings pass forgets any site with no building behind it, and the test has none)
        brain.army.seedSite(enemySite, Buildings.TOWN_CENTRE, level.getGameTime());
        return brain;
    }

    /** n Vindicators of one owner on a sky pad round a centre, spread a block apart, all Hold fire. */
    private static List<net.minecraft.world.entity.LivingEntity> padVindicators(ServerLevel level, String owner, BlockPos centre, int n, int dx) {
        List<net.minecraft.world.entity.LivingEntity> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            var v = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
            if (v == null)
                continue;
            v.moveTo(centre.getX() + 0.5 + dx + (i % 4), centre.getY(), centre.getZ() + 0.5 + (i / 4), 0, 0);
            v.setOwnerName(owner);
            com.solegendary.reignofnether.unit.EngagementStance.set(v, com.solegendary.reignofnether.unit.EngagementStance.HOLD_FIRE);
            level.addFreshEntity(v);
            out.add(v);
        }
        return out;
    }

    /**
     * BotArmy: a hard bot's wave launches exactly when its army value crosses the difficulty's threshold. Ten
     * Vindicators (950 metal) are under BotTuning.HARD's 1100 at minute 0, so the brain stays in BUILD_UP and no
     * unit is sent at the known enemy site; two more (1140) cross it, the state flips to ATTACK with that site as the
     * wave's target, and a few ticks later every fighter carries it as its attack-move target. Own sky pad (the
     * army step scans the unit grid round home, which is flat: the pad's height keeps other arenas' units out).
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void bot_army_attacks_when_its_value_crosses_the_threshold(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_bot_wave";
        BlockPos pad = skyPad(helper, 120);
        BlockPos home = pad.offset(8, 0, 8);
        BlockPos enemySite = home.offset(70, 0, 0);
        var brain = armyBrain(level, owner, home, enemySite);
        List<net.minecraft.world.entity.LivingEntity> army = padVindicators(level, owner, home, 10, -2);
        if (army.size() != 10) {
            helper.fail("could not create 10 vindicators");
            return;
        }
        helper.runAfterDelay(3, () -> {
            brain.army.run(level, level.getGameTime(), army, army, List.of(), 0);
            if (brain.army.state != com.solegendary.reignofnether.bot.BotArmy.State.BUILD_UP || brain.army.waveTarget != null)
                helper.fail("950 metal of army should not launch a hard wave (threshold "
                    + com.solegendary.reignofnether.bot.BotTuning.HARD.attackValue(0) + "): state " + brain.army.state
                    + " target " + brain.army.waveTarget + " intruder " + describe(brain.army.lastIntruder)
                    + (brain.army.lastIntruder == null ? "" : " at " + brain.army.lastIntruder.blockPosition().subtract(home).toShortString()));
            army.addAll(padVindicators(level, owner, home, 2, 3));
        });
        helper.runAfterDelay(6, () -> {
            brain.army.run(level, level.getGameTime(), army, army, List.of(), 0);
            if (brain.army.state != com.solegendary.reignofnether.bot.BotArmy.State.ATTACK || !enemySite.equals(brain.army.waveTarget))
                helper.fail("1140 metal of army should launch a hard wave at the known site: state " + brain.army.state
                    + " target " + brain.army.waveTarget + " value " + brain.army.armyValue);
        });
        helper.runAfterDelay(12, () -> {
            // a group's attack-move spreads into the move formation (fddf04c): each fighter's target is its own cell
            // round the site, not the site itself - twelve men at spacing 1 fill a 5x3 block, so "at the site" is
            // within WAVE_SPREAD of it (the site is 70 blocks from home, so this still proves the wave went there)
            int sent = 0;
            String where = "";
            for (var le : army)
                if (le instanceof com.solegendary.reignofnether.unit.interfaces.AttackerUnit au) {
                    BlockPos t = au.getAttackMoveTarget();
                    if (t != null && t.distSqr(enemySite) <= WAVE_SPREAD * WAVE_SPREAD)
                        sent++;
                    else
                        where = String.valueOf(t);
                }
            int n = army.size();
            army.forEach(net.minecraft.world.entity.Entity::discard);
            if (sent != n)
                helper.fail("the wave should attack-move every fighter at the site: " + sent + " of " + n + " (one target: " + where + ")");
            else
                helper.succeed();
        });
    }

    /** How far a group's attack-move or patrol cell may sit from the clicked spot (UnitFormations spread). */
    static final int WAVE_SPREAD = 8;

    /**
     * Fight and Patrol spread (fddf04c, lovish Oct 14 02:38: "patrol makes all selected units focus one focal
     * point"): six Vindicators patrol-clicked on one spot each get their own cell of the move formation round it -
     * six distinct far ends, all within WAVE_SPREAD of the spot - and the same order on a single unit still patrols to
     * the spot itself. Own sky pad.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void patrol_group_spreads_into_the_move_formation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_patrol_spread";
        BlockPos pad = skyPad(helper, 165);   // between the bot wave pad (120/150) and the next at 185
        BlockPos home = pad.offset(8, 0, 8);
        BlockPos spot = home.offset(20, 0, 0);
        List<net.minecraft.world.entity.LivingEntity> group = padVindicators(level, owner, home, 6, -2);
        List<net.minecraft.world.entity.LivingEntity> solo = padVindicators(level, owner, home.offset(0, 0, 6), 1, 0);
        if (group.size() != 6 || solo.size() != 1) {
            helper.fail("could not create the vindicators");
            return;
        }
        helper.runAfterDelay(3, () -> {
            try {
                int[] ids = group.stream().mapToInt(net.minecraft.world.entity.Entity::getId).toArray();
                new com.solegendary.reignofnether.unit.UnitActionItem(owner,
                        com.solegendary.reignofnether.unit.UnitAction.PATROL, -1, ids, spot, BlockPos.ZERO).action(level);
                new com.solegendary.reignofnether.unit.UnitActionItem(owner,
                        com.solegendary.reignofnether.unit.UnitAction.PATROL, -1, new int[] { solo.get(0).getId() }, spot, BlockPos.ZERO).action(level);
                java.util.Set<BlockPos> ends = new java.util.HashSet<>();
                String bad = "";
                for (var le : group) {
                    BlockPos end = com.solegendary.reignofnether.unit.PatrolServerEvents.patrolEnd(le.getId());
                    if (end == null || end.distSqr(spot) > WAVE_SPREAD * WAVE_SPREAD)
                        bad = String.valueOf(end);
                    else
                        ends.add(end);
                }
                BlockPos soloEnd = com.solegendary.reignofnether.unit.PatrolServerEvents.patrolEnd(solo.get(0).getId());
                if (!bad.isEmpty())
                    helper.fail("a patrolling unit's far end is missing or far from the spot: " + bad);
                else if (ends.size() != 6)
                    helper.fail("six units patrol-clicked on one spot should get six cells, got " + ends.size() + ": " + ends);
                else if (!spot.equals(soloEnd))
                    helper.fail("a single unit should patrol to the clicked spot itself, got " + soloEnd);
                else
                    helper.succeed();
            } finally {
                group.forEach(net.minecraft.world.entity.Entity::discard);
                solo.forEach(net.minecraft.world.entity.Entity::discard);
                group.forEach(le -> com.solegendary.reignofnether.unit.PatrolServerEvents.clear(le.getId()));
                solo.forEach(le -> com.solegendary.reignofnether.unit.PatrolServerEvents.clear(le.getId()));
            }
        });
    }

    /**
     * BotArmy: a base under attack pulls the army back. A hard bot's wave is out (state ATTACK on a far site) when
     * three enemy Vindicators (285 metal, over BotTuning.HARD's defendValue) step within BASE_RADIUS of its home:
     * the next army step flips to DEFEND and attack-moves every fighter onto the intruder, not the far site. Own sky
     * pad; the intruders hold fire so nothing dies while the orders land.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void bot_army_pulls_back_to_defend_its_base(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_bot_defend", raider = "gametest_bot_raider";
        BlockPos pad = skyPad(helper, 150);
        BlockPos home = pad.offset(8, 0, 8);
        BlockPos farSite = home.offset(90, 0, 0);
        var brain = armyBrain(level, owner, home, farSite);
        brain.army.state = com.solegendary.reignofnether.bot.BotArmy.State.ATTACK;
        brain.army.waveTarget = farSite;
        List<net.minecraft.world.entity.LivingEntity> army = padVindicators(level, owner, home, 6, -4);
        List<net.minecraft.world.entity.LivingEntity> foes = padVindicators(level, raider, home, 3, 3);
        if (army.size() != 6 || foes.size() != 3) {
            helper.fail("could not create the vindicators");
            return;
        }
        helper.runAfterDelay(3, () -> {
            brain.army.run(level, level.getGameTime(), army, army, List.of(), 5);
            if (brain.army.state != com.solegendary.reignofnether.bot.BotArmy.State.DEFEND)
                helper.fail("285 metal of enemy at home should pull the wave back: state " + brain.army.state);
        });
        helper.runAfterDelay(9, () -> {
            int home_ = 0;
            String where = "";
            for (var le : army)
                if (le instanceof com.solegendary.reignofnether.unit.interfaces.AttackerUnit au) {
                    BlockPos t = au.getAttackMoveTarget();
                    if (t != null && t.distSqr(home) <= com.solegendary.reignofnether.bot.BotArmy.BASE_RADIUS * com.solegendary.reignofnether.bot.BotArmy.BASE_RADIUS
                            && !farSite.equals(t))
                        home_++;
                    else
                        where = String.valueOf(t);
                }
            int n = army.size();
            army.forEach(net.minecraft.world.entity.Entity::discard);
            foes.forEach(net.minecraft.world.entity.Entity::discard);
            if (home_ != n)
                helper.fail("every fighter should be attack-moving onto the intruder at home: " + home_ + " of " + n + " (one target: " + where + ")");
            else
                helper.succeed();
        });
    }

    // ------------------------------------------------------------------ Brassworks Artificers slice 1 (design/brassworks_plan.md)

    /**
     * Brassworks slice 1: the faction is live - playable, in "Random", started from its skirmish code 7, with a capitol,
     * a worker and a bot kit that agree and its units registered to it - and the six other live factions are untouched
     * (same capitols, workers, bot kits and commander kits). It is registered after the Stormcallers so no older id
     * moved, and it wears its own brass, soot and furnace-orange traits with its own commander kit (Rail Lance,
     * Overclock), debris, nanolathe and quick-build extractor. Every T1 item of the Machine Shop and the Foundry Hall
     * resolves to an entity type that can be made and belongs to the faction (the "every T1 item produces" rule), the
     * machines carry Heat, and every Brassworks structure is in the server's tree (the client's is the same file,
     * written by tools/gen_structures.py in one go). Static data only, safe in parallel.
     */
    @GameTest(template = ARENA)
    public static void brassworks_are_live_and_the_six_other_factions_are_intact(GameTestHelper helper) {
        var brass = Factions.BRASSWORKS;
        if (brass == null) {
            helper.fail("the Brassworks are not registered");
            return;
        }
        var others = List.of(Factions.VILLAGERS, Factions.MONSTERS, Factions.PIGLINS, Factions.VERDANT_COURT,
            Factions.TIDEWROUGHT, Factions.STORMCALLERS);
        List<Building> capitols = List.of(Buildings.TOWN_CENTRE, Buildings.MAUSOLEUM, Buildings.CENTRAL_PORTAL,
            Buildings.HEARTWOOD_HALL, Buildings.WRECK_HARBOUR, Buildings.JADE_PAGODA);
        List<net.minecraft.world.entity.EntityType<?>> workers = List.of(
            com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.ZOMBIE_VILLAGER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.GRUNT_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.SEEDSHAPER_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.SHIPWRIGHT_UNIT.get(),
            com.solegendary.reignofnether.registrars.EntityRegistrar.GEOMANCER_UNIT.get());
        var dguns = List.of(com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.SUNFIRE,
            com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.SOULREAPER,
            com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.BLOODSTORM,
            com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.THORNBURST,
            com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.BROADSIDE,
            com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.JUDGEMENT);
        var signatures = List.of(com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.RALLY,
            com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.DREAD,
            com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.WAR_HORN,
            com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.WILDSTRIDE,
            com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.RIPTIDE,
            com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.BLINK);
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
            if (t.dgunKind != dguns.get(i) || t.commanderAbility != signatures.get(i))
                helper.fail(f.getName() + "'s commander kit changed");
        }
        if (brass.preview || !brass.playable || !Factions.isLive(brass) || !Factions.PLAYABLE_FACTIONS.contains(brass.key))
            helper.fail("the Brassworks are not a live, playable faction");
        if (Factions.CLASSIC_FACTIONS.contains(brass.key) || Factions.SURVIVAL_FACTIONS.contains(brass.key))
            helper.fail("the Brassworks leaked into the classic or survival list before they have a cube map / wave");
        if (ReignOfNetherRegistries.BUILDING.get(brass.capitolBuilding) != Buildings.FOUNDRY_HALL)
            helper.fail("the Brassworks' capitol is not the Foundry Hall: " + brass.capitolBuilding);
        if (!net.minecraft.world.entity.EntityType.getKey(com.solegendary.reignofnether.registrars.EntityRegistrar.TINKER_UNIT.get())
                .equals(brass.workerEntityType))
            helper.fail("the Brassworks' worker is not the Tinker: " + brass.workerEntityType);
        var brassKit = com.solegendary.reignofnether.bot.BotPlayer.kitFor(brass);
        if (brassKit == null || brassKit.capitol() != Buildings.FOUNDRY_HALL || brassKit.armyBuilding() != Buildings.MACHINE_SHOP
                || brassKit.extractor() != Buildings.METAL_EXTRACTOR_BRASS || brassKit.wind() != Buildings.WIND_GENERATOR_BRASS
                || brassKit.worker() != com.solegendary.reignofnether.building.production.ProductionItems.TINKER)
            helper.fail("the Brassworks bot kit is missing or builds the wrong capitol / lab / economy");
        if (com.solegendary.reignofnether.bot.BotPlayer.vaultFor(brass) != Buildings.STORAGE_VAULT_BRASS)
            helper.fail("the Brassworks bot does not build the Strongbox");
        // Tier 2 and Tier 3 sit in the capitol's usual slots
        var hallItems = Buildings.FOUNDRY_HALL.productions.get();
        if (!hallItems.contains(com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_2)
                || !hallItems.contains(com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_3))
            helper.fail("the Foundry Hall does not offer Tier 2 and Tier 3 research");
        if (Factions.getFaction(brass.key) != brass || !"brassworks".equals(brass.getName()))
            helper.fail("the Brassworks do not resolve by their key");
        if (ReignOfNetherRegistries.FACTIONS.getId(brass) < ReignOfNetherRegistries.FACTIONS.getId(Factions.STORMCALLERS))
            helper.fail("the Brassworks were registered before the Stormcallers and shifted its id");
        // "Random" reaches all seven; skirmish code 7 starts them
        var rng = new java.util.Random(7);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 700; i++)
            seen.add(Factions.randomLive(rng).getName());
        if (!seen.contains("brassworks") || seen.size() != 7)
            helper.fail("random should reach the seven live factions, saw " + seen);
        if (SkirmishServerboundPacket.codeOf(brass) != 7 || SkirmishServerboundPacket.factionOf(7, rng) != brass)
            helper.fail("skirmish code 7 does not start the Brassworks");
        // every T1 item of the lab and the capitol produces: it names an entity type that can be made, is a Unit of this
        // faction, and the machines among them carry Heat (the crews don't)
        var level = helper.getLevel();
        List<com.solegendary.reignofnether.building.production.ProductionItem> items = new ArrayList<>();
        items.addAll(Buildings.MACHINE_SHOP.productions.get());
        items.addAll(Buildings.FOUNDRY_HALL.productions.get());
        int units = 0, machines = 0;
        for (var item : items) {
            if (!(item instanceof com.solegendary.reignofnether.building.production.IUnitProductionItem))
                continue;
            var type = com.solegendary.reignofnether.registrars.EntityRegistrar.getEntityType(item.getItemName());
            if (type == null) {
                helper.fail("the Brassworks item " + item.getItemName() + " names no entity type");
                continue;
            }
            if (!brass.key.equals(Factions.ENTITY_FACTION.get(net.minecraft.world.entity.EntityType.getKey(type))))
                helper.fail(net.minecraft.world.entity.EntityType.getKey(type) + " is not registered to the Brassworks");
            var e = type.create(level);
            if (!(e instanceof com.solegendary.reignofnether.unit.interfaces.Unit u) || u.getCost() == null || u.getCost().metal() <= 0)
                helper.fail(item.getItemName() + " does not make a priced Unit");
            else {
                units++;
                if (e instanceof com.solegendary.reignofnether.brass.Machine m) {
                    machines++;
                    if (m.heat() != 0 || m.ventTicks() != 0 || m.upgrade() != com.solegendary.reignofnether.brass.Heat.UPGRADE_NONE)
                        helper.fail(item.getItemName() + " is made already hot or upgraded");
                } else if (e instanceof com.solegendary.reignofnether.unit.units.brass.IronWardenUnit
                        || e instanceof com.solegendary.reignofnether.unit.units.brass.SteamRunnerUnit
                        || e instanceof com.solegendary.reignofnether.unit.units.brass.FlakCartUnit
                        || e instanceof com.solegendary.reignofnether.unit.units.brass.MortarCrawlerUnit)
                    helper.fail(item.getItemName() + " lost its Heat (Machine)");
            }
            if (e != null)
                e.discard();
        }
        if (units != 9)
            helper.fail("the Foundry Hall and the Machine Shop should train nine units, found " + units);
        if (machines != 6)
            helper.fail("six of the T1 units are machines (Gyro Scout, Steam Runner, Iron Warden, Flak Cart, Mortar Crawler, Shield Projector), found " + machines);
        // the Flak Cart is wind-type anti-air; the Deployed Turret is a machine of the faction but never trained
        var cart = com.solegendary.reignofnether.registrars.EntityRegistrar.FLAK_CART_UNIT.get().create(level);
        if (!(cart instanceof com.solegendary.reignofnether.unit.interfaces.WindAntiAir))
            helper.fail("the Flak Cart is not marked as wind-type anti-air");
        if (cart != null)
            cart.discard();
        if (!brass.key.equals(Factions.ENTITY_FACTION.get(net.minecraft.world.entity.EntityType.getKey(
                com.solegendary.reignofnether.registrars.EntityRegistrar.DEPLOYED_TURRET_UNIT.get()))))
            helper.fail("the Deployed Turret is not registered to the Brassworks");
        for (var item : Buildings.MACHINE_SHOP.productions.get())
            if (com.solegendary.reignofnether.registrars.EntityRegistrar.getEntityType(item.getItemName())
                    == com.solegendary.reignofnether.registrars.EntityRegistrar.DEPLOYED_TURRET_UNIT.get())
                helper.fail("the Machine Shop trains the Guildmaster's turret");
        // every Brassworks structure is in the server's tree
        for (String nbt : List.of("metal_extractor_brass", "metal_extractor_t2_brass", "wind_generator_brass",
                "energy_converter_brass", "storage_vault_brass", "foundry_hall", "machine_shop"))
            if (com.solegendary.reignofnether.building.BuildingBlockData.getBuildingNbt(nbt,
                    level.getServer().getResourceManager()) == null)
                helper.fail("the Brassworks structure " + nbt + " is missing from data/");
        // its own look and kit, never another faction's
        if (!FactionTraits.hasExplicit(brass))
            helper.fail("the Brassworks have no explicit FactionTraits entry");
        var t = FactionTraits.of(brass);
        for (var f : others) {
            var o = FactionTraits.of(f);
            if (t == o || t.wreckBlock == o.wreckBlock || t.scaffoldPole == o.scaffoldPole
                    || (t.bannerCloth == o.bannerCloth && t.bannerTrim == o.bannerTrim) || t.accentArgb == o.accentArgb
                    || t.deathDebris == o.deathDebris || t.nanoTint == o.nanoTint)
                helper.fail("the Brassworks borrow " + f.getName() + "'s look");
        }
        if (t.dgunKind != com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.RAIL
                || t.commanderAbility != com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.OVERCLOCK)
            helper.fail("the Guildmaster's kit should be Rail Lance + Overclock, is " + t.dgunKind + " + " + t.commanderAbility);
        if (t.mana || t.tidal || t.formation || t.momentum || t.metalExtractor.get() != Buildings.METAL_EXTRACTOR_BRASS)
            helper.fail("the Brassworks picked up another faction's mechanic, or have the wrong quick-build extractor");
        if (com.solegendary.reignofnether.barfx.BarFx.F_BRASS != 7 || com.solegendary.reignofnether.barfx.BarFx.deathFaction(
                com.solegendary.reignofnether.barfx.BarFx.deathFlags(com.solegendary.reignofnether.barfx.BarFx.F_BRASS, 2)) != 7)
            helper.fail("the Brassworks' death-FX faction value does not survive the 3-bit wire field");
        // the Guildmaster: a Tinker made commander carries the Rail Lance, Overclock and Deploy Turret
        var tinker = com.solegendary.reignofnether.registrars.EntityRegistrar.TINKER_UNIT.get().create(level);
        if (tinker == null) {
            helper.fail("could not create a Tinker");
            return;
        }
        tinker.setOwnerName("gametest_brass_live_" + helper.absolutePos(BlockPos.ZERO).asLong());
        com.solegendary.reignofnether.player.CommanderServerEvents.makeCommander(tinker);
        boolean rail = false, overclock = false, turret = false;
        for (var a : tinker.getAbilities().get()) {
            rail |= a instanceof com.solegendary.reignofnether.ability.abilities.CommanderDGun
                && com.solegendary.reignofnether.ability.abilities.CommanderDGun.kindFor(tinker)
                    == com.solegendary.reignofnether.ability.abilities.CommanderDGun.Kind.RAIL;
            overclock |= a instanceof com.solegendary.reignofnether.ability.abilities.CommanderAbility
                && com.solegendary.reignofnether.ability.abilities.CommanderAbility.kindFor(tinker)
                    == com.solegendary.reignofnether.ability.abilities.CommanderAbility.Kind.OVERCLOCK;
            turret |= a instanceof com.solegendary.reignofnether.ability.abilities.DeployTurret;
        }
        tinker.discard();
        if (!rail || !overclock || !turret)
            helper.fail("the Guildmaster lacks the Rail Lance (" + rail + "), Overclock (" + overclock + "), Deploy Turret ("
                + turret + ")");
        helper.succeed();
    }

    /**
     * Heat (brass/Heat): a Flak Cart fired to 100 heat vents - it dry-fires (its burst hurts nobody), takes 25% more
     * damage, and after the vent its heat is 0 and it fires again; Overclock makes it fire faster. The cart has no
     * owner pool: Heat is per machine, nothing is paid. Own arena, unique owners.
     */
    @GameTest(template = ARENA, timeoutTicks = 260)
    public static void machine_heat_vents_at_100_and_recovers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_brass_heat_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        openGround(helper, 0, 0, 15, 15);
        var cart = com.solegendary.reignofnether.registrars.EntityRegistrar.FLAK_CART_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        if (cart == null || foe == null) {
            helper.fail("could not create the Heat test units");
            return;
        }
        BlockPos a = helper.absolutePos(new BlockPos(2, 2, 7)), b = helper.absolutePos(new BlockPos(8, 2, 7));
        cart.moveTo(a.getX() + 0.5, a.getY(), a.getZ() + 0.5, -90, 0);
        foe.moveTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5, 90, 0);
        cart.setOwnerName(owner);
        foe.setOwnerName(foeOwner);
        foe.setHoldPosition(true);
        level.addFreshEntity(cart);
        level.addFreshEntity(foe);
        helper.runAfterDelay(2, () -> {
            try {
                if (cart.heatPerShot() != com.solegendary.reignofnether.unit.units.brass.FlakCartUnit.HEAT_PER_SHOT || cart.heat() != 0)
                    helper.fail("a fresh Flak Cart should be cold with " + com.solegendary.reignofnether.unit.units.brass.FlakCartUnit.HEAT_PER_SHOT + " heat a shot");
                // a cold burst lands
                float before = foe.getHealth();
                cart.gust(foe);
                com.solegendary.reignofnether.brass.Heat.onShot(cart, cart);
                if (foe.getHealth() >= before)
                    helper.fail("a cold Flak Cart's burst should hurt");
                if (cart.heat() != com.solegendary.reignofnether.unit.units.brass.FlakCartUnit.HEAT_PER_SHOT)
                    helper.fail("one burst should add " + com.solegendary.reignofnether.unit.units.brass.FlakCartUnit.HEAT_PER_SHOT + " heat, heat is " + cart.heat());
                // push it to 100: it vents
                com.solegendary.reignofnether.brass.Heat.addHeat(cart, cart, com.solegendary.reignofnether.brass.Heat.MAX);
                if (!com.solegendary.reignofnether.brass.Heat.isVenting(cart) || com.solegendary.reignofnether.brass.Heat.canFire(cart)
                        || cart.ventTicks() != com.solegendary.reignofnether.brass.Heat.VENT_TICKS)
                    helper.fail("a machine at 100 heat should be venting for " + com.solegendary.reignofnether.brass.Heat.VENT_TICKS + " ticks");
                // the ranged attack path dry-fires while venting
                float mid = foe.getHealth();
                cart.performUnitRangedAttack(foe, 1f);
                if (foe.getHealth() < mid)
                    helper.fail("a venting Flak Cart's attack should hurt nobody");
                // and the machine takes 25% more: 8 generic damage lands as 10
                cart.setHealth(cart.getMaxHealth());
                cart.hurt(level.damageSources().generic(), 8f);
                float lost = cart.getMaxHealth() - cart.getHealth();
                if (Math.abs(lost - 8f * com.solegendary.reignofnether.brass.Heat.VENT_DAMAGE_TAKEN) > 0.6f)
                    helper.fail("a venting machine should take 8 x " + com.solegendary.reignofnether.brass.Heat.VENT_DAMAGE_TAKEN + " damage, lost " + lost);
                cart.setHealth(cart.getMaxHealth());
            } catch (RuntimeException ex) {
                cart.discard();
                foe.discard();
                throw ex;
            }
        });
        helper.runAfterDelay(2 + com.solegendary.reignofnether.brass.Heat.VENT_TICKS + 5, () -> {
            try {
                if (com.solegendary.reignofnether.brass.Heat.isVenting(cart) || cart.heat() != 0 || !com.solegendary.reignofnether.brass.Heat.canFire(cart))
                    helper.fail("after the vent the machine should be cold and firing again: heat " + cart.heat() + ", vent " + cart.ventTicks());
                // damage is back to normal
                cart.hurt(level.damageSources().generic(), 8f);
                float lost = cart.getMaxHealth() - cart.getHealth();
                if (Math.abs(lost - 8f) > 0.6f)
                    helper.fail("a cooled machine should take plain damage again, lost " + lost);
                float before = foe.getHealth();
                cart.performUnitRangedAttack(foe, 1f);
                if (foe.getHealth() >= before)
                    helper.fail("a cooled Flak Cart's burst should hurt again");
                // Overclock: faster, hotter
                float plain = cart.getAttackCooldownMultiplier();
                com.solegendary.reignofnether.brass.Heat.overclock(cart);
                if (!com.solegendary.reignofnether.brass.Heat.isOverclocked(cart)
                        || cart.getAttackCooldownMultiplier() >= plain * 0.99f)
                    helper.fail("an overclocked machine should fire faster: " + cart.getAttackCooldownMultiplier() + " vs " + plain);
                int h = cart.heat();
                com.solegendary.reignofnether.brass.Heat.onShot(cart, cart);
                if (cart.heat() - h != Math.round(cart.heatPerShot() * com.solegendary.reignofnether.brass.Heat.OVERCLOCK_HEAT))
                    helper.fail("an overclocked shot should add x" + com.solegendary.reignofnether.brass.Heat.OVERCLOCK_HEAT + " heat, added " + (cart.heat() - h));
                helper.succeed();
            } finally {
                cart.discard();
                foe.discard();
            }
        });
    }

    /**
     * The Sapper's charge (ability/abilities/PlantCharge, brass/ChargeServerEvents): planted against an enemy Machine Shop
     * (fully built in the arena), it does nothing during its fuse and then takes blocks off the building worth about
     * 120 damage; planting is refused from too far, on an own building and on open ground. Nobody else is hurt.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void sapper_charge_damages_a_building(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_brass_sapper_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        openGround(helper, 0, 0, 15, 15);
        var blocks = Buildings.MACHINE_SHOP.getRelativeBlockData(level);
        BlockPos origin = com.solegendary.reignofnether.player.PlayerServerEvents.getBuildingOriginPos(
            helper.absolutePos(new BlockPos(7, 2, 7)), blocks);
        var shop = com.solegendary.reignofnether.building.BuildingServerEvents.placeBuilding(Buildings.MACHINE_SHOP, origin,
            Rotation.NONE, foeOwner, new int[0], false, false, true, true);
        if (shop == null) {
            helper.fail("the Machine Shop could not be placed in the arena");
            return;
        }
        // finish it on the spot: every block of the blueprint in the world
        for (var bb : shop.getBlocks())
            level.setBlock(bb.getBlockPos(), bb.getBlockState(), 3);
        // blocks set from outside the build queue are not counted by the placement: re-scan them (the reload path
        // does the same), or getBlocksPlaced stays 0 and the charge has nothing to take
        shop.initPlacedBlocks();
        shop.isBuilt = true;
        var sapper = com.solegendary.reignofnether.registrars.EntityRegistrar.SAPPER_UNIT.get().create(level);
        var bystander = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        if (sapper == null || bystander == null) {
            com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(shop);
            helper.fail("could not create the Sapper test units");
            return;
        }
        // the Sapper two blocks off the shop's -x face, the bystander beside it
        BlockPos min = shop.minCorner;
        sapper.moveTo(min.getX() - 1.5, helper.absolutePos(new BlockPos(0, 2, 0)).getY(), min.getZ() + 4.5, 0, 0);
        bystander.moveTo(min.getX() - 1.5, helper.absolutePos(new BlockPos(0, 2, 0)).getY(), min.getZ() + 2.5, 0, 0);
        sapper.setOwnerName(owner);
        bystander.setOwnerName(foeOwner);
        bystander.setHoldPosition(true);
        sapper.setHoldPosition(true);
        // the test is about the charge's blast; an idle Sapper punching the enemy bystander two blocks away is its
        // ordinary idle aggression, not the charge (red on main 2aa772e: "bystander lost 3.0 hp ... from sapper_unit")
        com.solegendary.reignofnether.unit.EngagementStance.set(sapper, com.solegendary.reignofnether.unit.EngagementStance.HOLD_FIRE);
        level.addFreshEntity(sapper);
        level.addFreshEntity(bystander);
        int[] placedBefore = { 0 };
        helper.runAfterDelay(2, () -> {
            try {
                placedBefore[0] = shop.getBlocksPlaced();
                if (placedBefore[0] < shop.getBlocksTotal() * 0.9)
                    helper.fail("the test shop should stand complete, " + placedBefore[0] + " of " + shop.getBlocksTotal() + " blocks placed");
                // refused: open ground, and from too far (the far corner is 9+ blocks away)
                if (com.solegendary.reignofnether.ability.abilities.PlantCharge.plant(level, sapper, owner, helper.absolutePos(new BlockPos(1, 2, 1)), true))
                    helper.fail("a charge on open ground should be refused");
                sapper.moveTo(min.getX() - 6.5, sapper.getY(), min.getZ() + 4.5, 0, 0);
                if (com.solegendary.reignofnether.ability.abilities.PlantCharge.plant(level, sapper, owner, shop.centrePos, true))
                    helper.fail("a charge from 6 blocks away should be refused");
                sapper.moveTo(min.getX() - 1.5, sapper.getY(), min.getZ() + 4.5, 0, 0);
                // refused on an own building: the shop seen as the Sapper's own
                if (com.solegendary.reignofnether.ability.abilities.PlantCharge.plant(level, sapper, foeOwner, shop.centrePos, true))
                    helper.fail("a charge on an own building should be refused");
                if (!com.solegendary.reignofnether.ability.abilities.PlantCharge.plant(level, sapper, owner, shop.centrePos, true))
                    helper.fail("a charge on an enemy building in reach should be planted");
            } catch (RuntimeException ex) {
                sapper.discard();
                bystander.discard();
                com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(shop);
                throw ex;
            }
        });
        // during the fuse nothing happens
        helper.runAfterDelay(2 + com.solegendary.reignofnether.brass.ChargeServerEvents.FUSE_TICKS - 10, () -> {
            if (shop.getBlocksPlaced() != placedBefore[0])
                helper.fail("the charge went off before its fuse ran out");
        });
        helper.runAfterDelay(2 + com.solegendary.reignofnether.brass.ChargeServerEvents.FUSE_TICKS + 10, () -> {
            try {
                int lost = placedBefore[0] - shop.getBlocksPlaced();
                // the charge over the shop's health per block: most of a T1 lab, never the whole building (one charge must not level it)
                int expect = (int) Math.floor(com.solegendary.reignofnether.brass.ChargeServerEvents.CHARGE_DAMAGE / (shop.getHealthPerBlock() / 2d));
                // (a few picks can land on blueprint cells the placement does not count - air, or blocks outside its block
                // map - so the floor is 80 % of the nominal count, CI saw 83 of 86)
                if (lost < Math.max(1, (int) (expect * 0.8)) || lost > expect + 3)
                    helper.fail("the charge should take about " + expect + " blocks off the shop, took " + lost);
                if (shop.isDestroyedServerside || shop.getHealth() <= 0)
                    helper.fail("one charge levelled the Machine Shop: health " + shop.getHealth());
                // name who was hurt, by how much and by what: on the PC runner (run 39143268859) this failed with no
                // clue whether the charge, a neighbouring arena or a fall did it
                for (var hurt : new net.minecraft.world.entity.LivingEntity[] { bystander, sapper })
                    if (hurt.getHealth() < hurt.getMaxHealth()) {
                        var src = hurt.getLastDamageSource();
                        // a stray arrow from a neighbouring arena is not the charge: on the faster PC runners
                        // (Oct 18) the sapper took 3 hp from an ownerless arrow every run while nothing in this test
                        // shoots. The charge hurts by blast, never by projectile, so only non-projectile damage counts
                        if (src != null && src.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE))
                            continue;
                        helper.fail("the charge hurt a unit: only the building is damaged ("
                            + (hurt == sapper ? "sapper" : "bystander") + " lost " + (hurt.getMaxHealth() - hurt.getHealth())
                            + " hp, last damage " + (src == null ? "unknown" : src.getMsgId()
                                + (src.getEntity() == null ? "" : " from " + src.getEntity().getType().toShortString()))
                            + ")");
                    }
                helper.succeed();
            } finally {
                sapper.discard();
                bystander.discard();
                com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(shop);
            }
        });
    }

    /**
     * Deploy Turret (ability/abilities/DeployTurret): the Guildmaster pays 40 metal and a turret stands at the aim, a
     * machine of its owner that shoots an enemy walking past; a short pool refuses it (nothing spawned, nothing paid);
     * after its lifetime it is gone without a death (no wreck entity near where it stood). Own arena, unique owners.
     */
    @GameTest(template = ARENA, timeoutTicks = 500)
    public static void deploy_turret_fights_then_expires(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_brass_turret_" + helper.absolutePos(BlockPos.ZERO).asLong(), foeOwner = owner + "_foe";
        openGround(helper, 0, 0, 15, 15);
        var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 0, 0);
        pool.addMetal(20);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
        var master = com.solegendary.reignofnether.registrars.EntityRegistrar.TINKER_UNIT.get().create(level);
        var foe = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        if (master == null || foe == null) {
            helper.fail("could not create the Deploy Turret test units");
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
            return;
        }
        BlockPos c = helper.absolutePos(new BlockPos(3, 2, 7));
        master.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, 0, 0);
        foe.moveTo(c.getX() + 8.5, c.getY(), c.getZ() + 0.5, 0, 0);
        master.setOwnerName(owner);
        foe.setOwnerName(foeOwner);
        foe.setHoldPosition(true);
        master.setHoldPosition(true);
        com.solegendary.reignofnether.player.CommanderServerEvents.makeCommander(master);
        level.addFreshEntity(master);
        level.addFreshEntity(foe);
        com.solegendary.reignofnether.unit.units.brass.DeployedTurretUnit[] turret = { null };
        helper.runAfterDelay(2, () -> {
            try {
                BlockPos aim = c.offset(3, 0, 0);
                if (com.solegendary.reignofnether.ability.abilities.DeployTurret.deploy(level, master, owner, aim, true) != null
                        || Math.abs(pool.getMetal() - 20) > 0.01)
                    helper.fail("a 20-metal pool should refuse the 40-metal turret with nothing paid");
                pool.addMetal(80);
                turret[0] = com.solegendary.reignofnether.ability.abilities.DeployTurret.deploy(level, master, owner, aim, true);
                if (turret[0] == null) {
                    helper.fail("the turret was not deployed");
                    return;
                }
                if (Math.abs(pool.getMetal() - 60) > 0.01)
                    helper.fail("the turret should cost " + com.solegendary.reignofnether.ability.abilities.DeployTurret.METAL_COST + " metal, pool has " + pool.getMetal());
                if (!owner.equals(turret[0].getOwnerName()) || !(turret[0] instanceof com.solegendary.reignofnether.brass.Machine)
                        || turret[0].getCost() == null || turret[0].getCost().population != 0)
                    helper.fail("the turret should be its owner's machine with no population");
                if (turret[0].distanceToSqr(aim.getX() + 0.5, aim.getY(), aim.getZ() + 0.5) > 4)
                    helper.fail("the turret should stand at the aim");
                turret[0].setUnitAttackTarget(foe);
            } catch (RuntimeException ex) {
                master.discard();
                foe.discard();
                if (turret[0] != null) turret[0].discard();
                com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
                throw ex;
            }
        });
        boolean[] hit = { false };
        for (int t = 5; t < 200; t += 5)
            helper.runAfterDelay(t, () -> {
                if (foe.getHealth() < foe.getMaxHealth())
                    hit[0] = true;
            });
        helper.runAfterDelay(200, () -> {
            if (!hit[0])
                helper.fail("the turret never shot the foe standing 5 blocks from it");
            if (turret[0] != null && !turret[0].isAlive())
                helper.fail("the turret died before its time");
            foe.discard();   // the turret has nothing left to shoot: it waits out its life
        });
        helper.runAfterDelay(2 + com.solegendary.reignofnether.unit.units.brass.DeployedTurretUnit.LIFETIME_TICKS + 15, () -> {
            try {
                if (turret[0] == null)
                    return;
                if (turret[0].isAlive() && !turret[0].isRemoved())
                    helper.fail("the turret should have fallen apart after " + com.solegendary.reignofnether.unit.units.brass.DeployedTurretUnit.LIFETIME_TICKS + " ticks");
                // it fell apart, it did not die: no wreck display stands where it stood
                var wrecks = level.getEntitiesOfClass(net.minecraft.world.entity.Display.BlockDisplay.class,
                    new net.minecraft.world.phys.AABB(turret[0].blockPosition()).inflate(2));
                if (!wrecks.isEmpty())
                    helper.fail("the expired turret left a wreck");
                helper.succeed();
            } finally {
                master.discard();
                if (turret[0] != null) turret[0].discard();
                com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
            }
        });
    }

    /**
     * HUD: a T3 lab's command card keeps a production grid. lovish's clip (Horde, Fortress selected) showed only a
     * row of small tiles and he read it as "no production grid": the tiles are the grid - three researches and the
     * experimental. Once every research is done the research tiles hide, so each T3 lab must keep at least one
     * unit production item (its experimental), and no two of a lab's items may share a hotkey, or one key press
     * would start both. Pure registry data, no world needed.
     */
    @GameTest(template = ARENA)
    public static void every_t3_lab_keeps_a_unit_production_with_distinct_hotkeys(GameTestHelper helper) {
        List<com.solegendary.reignofnether.building.production.ProductionBuilding> labs = List.of(
            Buildings.CASTLE, Buildings.STRONGHOLD, Buildings.FORTRESS, Buildings.HEART_OF_THE_WILD,
            Buildings.ABYSSAL_SHIPYARD, Buildings.HEAVENS_GATE);
        List<String> problems = new ArrayList<>();
        for (var lab : labs) {
            List<com.solegendary.reignofnether.building.production.ProductionItem> items = lab.productions.get();
            boolean unit = items.stream().anyMatch(i -> i instanceof com.solegendary.reignofnether.building.production.IUnitProductionItem);
            if (!unit)
                problems.add(lab.name + " has no unit production item (its card empties once research is done)");
            Set<Integer> keys = new HashSet<>();
            for (var hk : lab.productions.hotkeys())
                if (hk != null && !keys.add(hk.getKey()))
                    problems.add(lab.name + " binds two production items to " + hk.buttonLabel);
        }
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // clip 5 (lovish, Stormcallers vs easy bot): sails, the command card's keys, attack-move lines, hover production
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Every wind generator declares a sail hub (WindGenerator.SAIL_HUBS) that is a horizontal hub log inside its own
     * footprint, on the axis the rotor faces, with open air on the rotor's side - and, for every rotation, the hub the
     * renderer computes (origin + hub.rotate(rotation) + (0, 1, 0)) is exactly where the placed structure puts that
     * log. lovish's clip: the Stormcallers' sails floated beside and above their towers.
     */
    @GameTest(template = ARENA)
    public static void every_sail_hub_sits_inside_its_mill(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        int mills = 0;
        for (Building building : ReignOfNetherRegistries.BUILDING) {
            if (!(building instanceof com.solegendary.reignofnether.building.buildings.shared.WindGenerator mill))
                continue;
            if (com.solegendary.reignofnether.building.buildings.shared.WindGenerator.NO_SAILS.contains(mill.variant))
                continue;   // a boiler, not a mill
            mills++;
            var hub = mill.getSailHub();
            if (hub == null) {
                problems.add(building.structureName + ": no SAIL_HUBS entry");
                continue;
            }
            var rel = building.getRelativeBlockData(level);
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            com.solegendary.reignofnether.building.BuildingBlock hubBlock = null;
            Set<BlockPos> solid = new HashSet<>();
            for (var bb : rel) {
                if (bb.getBlockState().isAir())
                    continue;
                BlockPos p = bb.getBlockPos();
                solid.add(p);
                minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
                minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
                if (p.equals(hub.pos()))
                    hubBlock = bb;
            }
            BlockPos h = hub.pos();
            if (h.getX() < minX || h.getX() > maxX || h.getZ() < minZ || h.getZ() > maxZ) {
                problems.add(building.structureName + ": hub " + h + " is outside the footprint");
                continue;
            }
            var axisProp = net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS;
            if (hubBlock == null || !hubBlock.getBlockState().hasProperty(axisProp)
                    || hubBlock.getBlockState().getValue(axisProp) != hub.outward().getAxis()) {
                problems.add(building.structureName + ": the block at hub " + h + " is "
                    + (hubBlock == null ? "air" : hubBlock.getBlockState()) + ", not a log on the " + hub.outward().getAxis() + " axis");
                continue;
            }
            if (solid.contains(h.relative(hub.outward())))
                problems.add(building.structureName + ": the rotor side of the hub is inside the mill");
            BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4));
            for (Rotation r : Rotation.values()) {
                var abs = com.solegendary.reignofnether.building.BuildingUtils.getAbsoluteBlockData(rel, level, origin, r);
                BlockPos rr = h.rotate(r);
                BlockPos expected = origin.offset(rr.getX(), rr.getY() + 1, rr.getZ());
                boolean found = false;
                for (var bb : abs)
                    if (bb.getBlockPos().equals(expected) && bb.getBlockState().getBlock() == hubBlock.getBlockState().getBlock()) {
                        found = true;
                        break;
                    }
                if (!found)
                    problems.add(building.structureName + " rotated " + r + ": no hub log at the renderer's hub " + expected);
            }
        }
        // design/lively_buildings.md: only the Sunforged and Horde mills keep a rotor; the rest move another way
        // (WindGenerator.MOTIONS, every_moving_part_sits_on_its_anchor)
        if (mills < 2)
            problems.add("only " + mills + " sail mills registered");
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }

    /**
     * No two buttons shown together on a unit's command card share a key: its orders (CommandCard, the same list the
     * HUD lays out), its abilities and - for a worker - its faction's whole build menu, primary keys and aliases.
     * lovish's clip: the Geomancer's build menu had the Lotus Academy on Y while Patrol was Y too.
     */
    // ability pairs that share a key because their buttons are never shown together: the Militia's bow / sword swap
    // shows one at a time, the Ravager's vexes need an Evoker rider and its ground attack a Pillager one
    static final Set<String> SHARED_KEY_ON_PURPOSE = Set.of(
        "ability WeaponSwapBow|ability WeaponSwapSword", "ability WeaponSwapSword|ability WeaponSwapBow",
        "ability CastSummonVexes|ability AttackGround", "ability AttackGround|ability CastSummonVexes");

    @GameTest(template = ARENA)
    public static void no_two_buttons_on_a_command_card_share_a_hotkey(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
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
                boolean worker = e instanceof com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
                boolean attacker = e instanceof com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
                java.util.Map<Integer, String> taken = new java.util.HashMap<>();
                List<String> clashes = new ArrayList<>();
                java.util.function.BiConsumer<com.solegendary.reignofnether.keybinds.Keybinding, String> claim = (kb, what) -> {
                    if (kb == null)
                        return;
                    for (int code : new int[] { kb.getKey(), kb.getAliasKey() }) {
                        if (code == -1)
                            continue;
                        String prev = taken.putIfAbsent(code, what);
                        if (prev != null && !prev.equals(what) && !SHARED_KEY_ON_PURPOSE.contains(prev + "|" + what))
                            clashes.add(prev + " and " + what + " both on " + kb.buttonLabel + " (" + code + ")");
                    }
                };
                for (var order : com.solegendary.reignofnether.hud.CommandCard.orders(attacker, worker, unit.canGarrison(), false))
                    claim.accept(order.key(), "order " + order);
                var abilities = unit.getAbilities();
                if (abilities != null) {
                    var list = abilities.get();
                    var keys = abilities.effectiveHotkeys();
                    for (int i = 0; i < list.size(); i++) {
                        claim.accept(keys.get(i),
                            "ability " + list.get(i).getClass().getSimpleName());
                    }
                }
                // the build menu's keys are BAR's grid keys (Q W E R / A S D F / Z X C V): live only while a build tab is
                // open, and then they win over the orders and abilities on the same keys (BuildTabs.onKey), so they
                // share keys with the card on purpose. Inside a tab they stay unique: BuildGroupGameTests.
                for (String c : clashes)
                    problems.add(key.getPath() + ": " + c);
            } catch (Exception ex) {
                problems.add(key.getPath() + ": threw " + ex);
            } finally {
                if (e != null)
                    e.discard();
            }
        }
        if (checked < 60)
            problems.add("only " + checked + " unit types were checked - the registry scan is broken");
        if (!problems.isEmpty())
            helper.fail(problems.size() + " clash(es): " + String.join("; ", problems.subList(0, Math.min(25, problems.size()))));
        else
            helper.succeed();
    }

    /**
     * An attack-move line (Attack armed, then a left- or right-drag) gives every unit its own slot on the line and
     * an ATTACK_MOVE to it - the same slots and assignment the move formation uses (FormationDragMove), the order the
     * client sends per slot (UnitClientEvents.resolveFormationMove).
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void attack_move_line_gives_each_unit_its_own_slot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_attack_line";
        List<net.minecraft.world.entity.LivingEntity> units = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            var u = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
            if (u == null) {
                helper.fail("could not create a vindicator unit");
                return;
            }
            BlockPos at = helper.absolutePos(new BlockPos(3 + i * 2, 2, 3));
            u.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
            u.setOwnerName(owner);
            level.addFreshEntity(u);
            units.add(u);
        }
        helper.runAfterDelay(2, () -> {
            List<String> problems = new ArrayList<>();
            BlockPos start = helper.absolutePos(new BlockPos(2, 1, 12));
            BlockPos end = helper.absolutePos(new BlockPos(12, 1, 12));
            List<BlockPos> slots = com.solegendary.reignofnether.unit.FormationDragMove.computeSlots(level, start, end, units.size());
            if (slots.size() != units.size() || new HashSet<>(slots).size() != slots.size())
                problems.add("expected " + units.size() + " distinct slots, got " + slots);
            var pairs = com.solegendary.reignofnether.unit.FormationDragMove.assign(units, slots);
            Set<Integer> assigned = new HashSet<>();
            for (var pair : pairs) {
                if (!assigned.add(pair.getFirst().getId()))
                    problems.add("unit " + pair.getFirst().getId() + " got two slots");
                new com.solegendary.reignofnether.unit.UnitActionItem(owner,
                    com.solegendary.reignofnether.unit.UnitAction.ATTACK_MOVE, -1, new int[] { pair.getFirst().getId() },
                    pair.getSecond(), new BlockPos(0, 0, 0)).action(level);
            }
            if (assigned.size() != units.size())
                problems.add(assigned.size() + " of " + units.size() + " units were given a slot");
            for (var pair : pairs) {
                var au = (com.solegendary.reignofnether.unit.interfaces.AttackerUnit) pair.getFirst();
                if (!pair.getSecond().equals(au.getAttackMoveTarget()))
                    problems.add("unit " + pair.getFirst().getId() + " attack-moves to " + au.getAttackMoveTarget()
                        + ", not its slot " + pair.getSecond());
            }
            for (var u : units)
                u.discard();
            if (!problems.isEmpty())
                helper.fail(String.join("; ", problems));
            else
                helper.succeed();
        });
    }

    /**
     * The hover card's production line comes from the server only for a building the asking player can see: an
     * unseen lab answers nothing even while it trains, a seen one its front item and progress, an idle one nothing.
     */
    @GameTest(template = ARENA)
    public static void enemy_production_info_only_for_visible_buildings(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_hover_lab";
        var bp = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(Buildings.LOTUS_ACADEMY,
            level, helper.absolutePos(new BlockPos(2, 1, 2)), Rotation.NONE, owner, false);
        if (!(bp instanceof com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement pp)) {
            helper.fail("the Lotus Academy did not make a production placement");
            return;
        }
        List<String> problems = new ArrayList<>();
        if (com.solegendary.reignofnether.building.BuildingHoverInfo.describe(pp, true) != null)
            problems.add("an idle lab reported production");
        var item = com.solegendary.reignofnether.building.production.ProductionItems.BLADE_DANCER;
        var active = new com.solegendary.reignofnether.building.production.ActiveProduction(item, false, owner);
        float total = item.getCost(false, owner).ticks;
        active.ticksLeft = total / 2f;
        pp.productionQueue.add(active);
        if (com.solegendary.reignofnether.building.BuildingHoverInfo.describe(pp, false) != null)
            problems.add("an unseen lab's production was described");
        var info = com.solegendary.reignofnether.building.BuildingHoverInfo.describe(pp, true);
        if (info == null)
            problems.add("a seen, training lab described nothing");
        else {
            ResourceLocation expected = ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(item);
            if (expected != null && !expected.toString().equals(info.itemKey()))
                problems.add("item key " + info.itemKey() + ", expected " + expected);
            if (total > 0 && Math.abs(info.progress() - 0.5f) > 0.05f)
                problems.add("progress " + info.progress() + ", expected 0.5");
            if (info.queued() != 1)
                problems.add("queued " + info.queued() + ", expected 1");
        }
        if (com.solegendary.reignofnether.building.BuildingHoverInfo.describe(null, true) != null)
            problems.add("no building described something");
        pp.productionQueue.clear();
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }

    // ------------------------------------------------------------------------------------------------------------
    // reload: units and match records survive a save and reload (design/persistence.md). A Fox Courier loaded from
    // NBT threw "this.goal is null" (Fox.setTargetGoals on goals its registerGoals never made) and the chunk loader
    // dropped it - the unit silently vanished on every chunk reload. These run every unit type down the NBT path.
    // ------------------------------------------------------------------------------------------------------------

    static final int RELOAD_SHARDS = 8;

    /** Every unit EntityType we register, in a stable order (sorted by id), as the crash audit scans them. */
    static List<net.minecraft.world.entity.EntityType<?>> reloadUnitTypes(ServerLevel level) {
        List<net.minecraft.world.entity.EntityType<?>> out = new ArrayList<>();
        for (var type : net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES) {
            ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (key == null || !ReignOfNether.MOD_ID.equals(key.getNamespace()))
                continue;
            // the Guildmaster's Deploy Turret is a 20 s summon that is discarded on load by design (like decoys)
            // and the Iron Leviathan Engine's clockwork drones fall apart without their engine (on load, or spawned alone)
            if ("deployed_turret_unit".equals(key.getPath()) || "clockwork_drone_unit".equals(key.getPath()))
                continue;
            net.minecraft.world.entity.Entity e = null;
            try {
                e = type.create(level);
                if (e instanceof com.solegendary.reignofnether.unit.interfaces.Unit)
                    out.add(type);
            } catch (Exception ignored) {
                // the crash audit reports types that can't even be built
            } finally {
                if (e != null)
                    e.discard();
            }
        }
        out.sort(java.util.Comparator.comparing(t -> String.valueOf(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(t))));
        return out;
    }

    static String rootCause(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c)
            c = c.getCause();
        StackTraceElement[] st = c.getStackTrace();
        String at = "";
        for (StackTraceElement el : st)
            if (el.getClassName().startsWith("com.solegendary")) {
                at = " at " + el.getClassName().substring(el.getClassName().lastIndexOf('.') + 1) + "." + el.getMethodName() + ":" + el.getLineNumber();
                break;
            }
        return c + at;
    }

    /**
     * One shard of the unit types: create each, give it an owner, a non-default stance, kills and a veterancy rank,
     * save it with its id and load it back through EntityType.create(tag, level) - exactly what a chunk load does -
     * then tick it 5 times and add it to the level. Nothing may throw, the copy must be alive, keep all of that, and
     * end up with the same goals as a fresh unit (a vanilla readAdditionalSaveData must not bolt its own AI back on).
     */
    static void unitsSurviveReload(GameTestHelper helper, int shard) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_reload_" + shard;
        var all = reloadUnitTypes(level);
        if (all.size() < 60) {
            helper.fail("only " + all.size() + " unit types found - the registry scan is broken");
            return;
        }
        var stance = com.solegendary.reignofnether.unit.EngagementStance.HOLD_FIRE;
        List<String> gaps = new ArrayList<>();
        List<net.minecraft.world.entity.Entity> added = new ArrayList<>();
        int slot = 0;
        for (int i = shard; i < all.size(); i += RELOAD_SHARDS) {
            var type = all.get(i);
            String id = String.valueOf(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(type));
            net.minecraft.world.entity.Entity orig = null;
            try {
                orig = type.create(level);
                if (!(orig instanceof net.minecraft.world.entity.Mob mob) || !(orig instanceof com.solegendary.reignofnether.unit.interfaces.Unit unit)) {
                    gaps.add(id + ": not a Mob unit");
                    continue;
                }
                BlockPos at = helper.absolutePos(new BlockPos(2 + (slot % 4) * 4, 2, 2 + (slot / 4) * 4));
                slot++;
                mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
                unit.setOwnerName(owner);
                com.solegendary.reignofnether.unit.EngagementStance.set(mob, stance);
                com.solegendary.reignofnether.unit.UnitKills.set(mob, 7);
                com.solegendary.reignofnether.unit.VeterancyServerEvents.award(mob, 2.5f);
                int goals = mob.goalSelector.getAvailableGoals().size();
                int targets = mob.targetSelector.getAvailableGoals().size();
                net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                if (!orig.save(tag)) {
                    gaps.add(id + ": Entity.save refused (the type can't be serialised, so it is never written to disk)");
                    continue;
                }
                orig.discard();
                net.minecraft.world.entity.Entity loaded;
                try {
                    loaded = net.minecraft.world.entity.EntityType.create(tag, level).orElse(null);
                } catch (Throwable t) {
                    gaps.add(id + ": NBT load threw " + rootCause(t));
                    continue;
                }
                if (!(loaded instanceof net.minecraft.world.entity.Mob lm) || !(loaded instanceof com.solegendary.reignofnether.unit.interfaces.Unit lu)) {
                    gaps.add(id + ": NBT load gave " + loaded);
                    continue;
                }
                try {
                    for (int k = 0; k < 5; k++)
                        loaded.tick();
                } catch (Throwable t) {
                    gaps.add(id + ": tick after load threw " + rootCause(t));
                    loaded.discard();
                    continue;
                }
                if (!loaded.isAlive() || loaded.isRemoved())
                    gaps.add(id + ": dead/removed after reload");
                if (!owner.equals(lu.getOwnerName()))
                    gaps.add(id + ": owner '" + lu.getOwnerName() + "'");
                if (com.solegendary.reignofnether.unit.EngagementStance.of(lm) != stance)
                    gaps.add(id + ": stance " + com.solegendary.reignofnether.unit.EngagementStance.of(lm));
                if (com.solegendary.reignofnether.unit.UnitKills.get(lm) != 7)
                    gaps.add(id + ": kills " + com.solegendary.reignofnether.unit.UnitKills.get(lm));
                if (com.solegendary.reignofnether.unit.VeterancyServerEvents.getRank(lm) != 2)
                    gaps.add(id + ": rank " + com.solegendary.reignofnether.unit.VeterancyServerEvents.getRank(lm));
                if (com.solegendary.reignofnether.unit.VeterancyServerEvents.getSyncedRank(lm) != 2)
                    gaps.add(id + ": rank health modifier lost (synced rank " + com.solegendary.reignofnether.unit.VeterancyServerEvents.getSyncedRank(lm) + ")");
                int lg = lm.goalSelector.getAvailableGoals().size(), lt = lm.targetSelector.getAvailableGoals().size();
                if (lg != goals || lt != targets)
                    gaps.add(id + ": goals changed on reload (" + goals + "/" + targets + " -> " + lg + "/" + lt + ")");
                level.addFreshEntity(loaded);
                added.add(loaded);
            } catch (Throwable t) {
                gaps.add(id + ": threw " + rootCause(t));
                if (orig != null)
                    orig.discard();
            }
        }
        helper.runAfterDelay(5, () -> {
            for (var e : added)
                if (!e.isAlive() || e.isRemoved())
                    gaps.add(String.valueOf(net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(e.getType())) + ": gone 5 ticks after joining the level");
            added.forEach(net.minecraft.world.entity.Entity::discard);
            if (!gaps.isEmpty())
                helper.fail("units that don't survive a reload: " + gaps);
            else
                helper.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void units_survive_reload_0(GameTestHelper helper) { unitsSurviveReload(helper, 0); }
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void units_survive_reload_1(GameTestHelper helper) { unitsSurviveReload(helper, 1); }
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void units_survive_reload_2(GameTestHelper helper) { unitsSurviveReload(helper, 2); }
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void units_survive_reload_3(GameTestHelper helper) { unitsSurviveReload(helper, 3); }
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void units_survive_reload_4(GameTestHelper helper) { unitsSurviveReload(helper, 4); }
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void units_survive_reload_5(GameTestHelper helper) { unitsSurviveReload(helper, 5); }
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void units_survive_reload_6(GameTestHelper helper) { unitsSurviveReload(helper, 6); }
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void units_survive_reload_7(GameTestHelper helper) { unitsSurviveReload(helper, 7); }

    /** The entries of a MatchStateSaveData snapshot that belong to one owner (tests run in parallel). */
    static net.minecraft.nbt.CompoundTag onlyOwner(net.minecraft.nbt.CompoundTag snap, String owner) {
        net.minecraft.nbt.CompoundTag out = new net.minecraft.nbt.CompoundTag();
        for (String key : snap.getAllKeys()) {
            net.minecraft.nbt.ListTag src = snap.getList(key, net.minecraft.nbt.Tag.TAG_COMPOUND), dst = new net.minecraft.nbt.ListTag();
            for (int i = 0; i < src.size(); i++)
                if (owner.equals(src.getCompound(i).getString("owner")))
                    dst.add(src.getCompound(i).copy());
            out.put(key, dst);
        }
        return out;
    }

    /** Through the SavedData shell and real NBT bytes, as a world save and load would. */
    static net.minecraft.nbt.CompoundTag throughDisk(net.minecraft.nbt.CompoundTag tag) throws java.io.IOException {
        var data = com.solegendary.reignofnether.persistence.MatchStateSaveData.load(tag);
        net.minecraft.nbt.CompoundTag written = data.save(new net.minecraft.nbt.CompoundTag());
        return bytesRoundTrip(written);
    }

    static net.minecraft.nbt.CompoundTag bytesRoundTrip(net.minecraft.nbt.CompoundTag tag) throws java.io.IOException {
        var bytes = new java.io.ByteArrayOutputStream();
        net.minecraft.nbt.NbtIo.write(tag, new java.io.DataOutputStream(bytes));
        return net.minecraft.nbt.NbtIo.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
    }

    /**
     * Tidepools: a normal pool and a trail pool are saved, forgotten as a restart forgets them (blocks stay), and
     * restored with owner, cells, radius, trail flag and the rest of their lifetime.
     */
    @GameTest(template = ARENA)
    public static void tidepools_survive_reload(GameTestHelper helper) throws java.io.IOException {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_reload_tide";
        openGround(helper, 1, 1, 14, 14);
        try {
            var pool = com.solegendary.reignofnether.tide.TidepoolServerEvents.raise(level, owner, helper.absolutePos(new BlockPos(7, 2, 7)), 2, 400);
            var source = net.minecraft.world.entity.EntityType.MARKER.create(level);
            var trail = com.solegendary.reignofnether.tide.TidepoolServerEvents.raiseTrail(level, owner, source, helper.absolutePos(new BlockPos(2, 2, 12)), 0, 300);
            if (pool == null || trail == null) {
                helper.fail("raise refused: " + com.solegendary.reignofnether.tide.TidepoolServerEvents.lastRefusal());
                return;
            }
            BlockPos cell = pool.positions().get(0), trailCell = trail.positions().get(0);
            long left = pool.ticksLeft();
            int cells = pool.cellCount();
            var tag = throughDisk(onlyOwner(com.solegendary.reignofnether.persistence.MatchStateSaveData.snapshot(), owner));
            com.solegendary.reignofnether.tide.TidepoolServerEvents.forget(pool);
            com.solegendary.reignofnether.tide.TidepoolServerEvents.forget(trail);
            if (com.solegendary.reignofnether.tide.TidepoolServerEvents.poolAt(level, cell) != null
                    || !com.solegendary.reignofnether.tide.TidepoolServerEvents.isTidepool(level, cell)) {
                helper.fail("forget should drop the record and keep the water");
                return;
            }
            com.solegendary.reignofnether.persistence.MatchStateSaveData.restore(level.getServer(), tag);
            com.solegendary.reignofnether.persistence.MatchStateSaveData.restore(level.getServer(), tag);   // idempotent
            var back = com.solegendary.reignofnether.tide.TidepoolServerEvents.poolAt(level, cell);
            var backTrail = com.solegendary.reignofnether.tide.TidepoolServerEvents.poolAt(level, trailCell);
            int pools = com.solegendary.reignofnether.tide.TidepoolServerEvents.poolsOf(owner).size();
            if (back == null || backTrail == null)
                helper.fail("pools not restored (normal " + back + ", trail " + backTrail + ")");
            else if (!owner.equals(back.owner) || back.cellCount() != cells || back.radius != 2 || back.isTrail())
                helper.fail("restored pool differs: owner " + back.owner + ", cells " + back.cellCount() + "/" + cells + ", radius " + back.radius + ", trail " + back.isTrail());
            else if (Math.abs(back.ticksLeft() - left) > 2)
                helper.fail("lifetime not kept: " + left + " ticks left before, " + back.ticksLeft() + " after");
            else if (!backTrail.isTrail())
                helper.fail("the trail pool came back as a normal pool (it would take an owner's pool slot)");
            else if (pools != 2)
                helper.fail("restoring twice made " + pools + " pools, expected 2");
            else
                helper.succeed();
        } finally {
            com.solegendary.reignofnether.tide.TidepoolServerEvents.clearOwner(owner);
        }
    }

    /**
     * Thickets need no save of their own: owner and cut damage ride the block entity's NBT, and the cover registry
     * (ThicketBlockEntity.LIVE) is rebuilt by onLoad as each chunk loads. Simulate an unload and a load from NBT.
     */
    @GameTest(template = ARENA)
    public static void thicket_owner_and_registry_survive_reload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_reload_thicket";
        openGround(helper, 1, 1, 6, 6);
        BlockPos spot = helper.absolutePos(new BlockPos(3, 2, 3));
        if (!com.solegendary.reignofnether.blocks.ThicketBlock.place(level, spot, owner, true)) {
            helper.fail("could not place a thicket");
            return;
        }
        net.minecraft.world.level.block.entity.BlockEntity re = null;
        try {
            if (!(level.getBlockEntity(spot) instanceof com.solegendary.reignofnether.blocks.ThicketBlockEntity be)) {
                helper.fail("no thicket block entity");
                return;
            }
            var tag = bytesRoundTrip(be.saveWithFullMetadata());
            be.onChunkUnloaded();
            if (com.solegendary.reignofnether.blocks.ThicketBlockEntity.countOwned(level, owner) != 0) {
                helper.fail("an unloaded thicket still counts");
                return;
            }
            re = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(spot, level.getBlockState(spot), tag);
            if (!(re instanceof com.solegendary.reignofnether.blocks.ThicketBlockEntity loaded)) {
                helper.fail("NBT load gave " + re);
                return;
            }
            loaded.setLevel(level);
            loaded.onLoad();   // what the chunk loader does for every block entity it loads
            if (!owner.equals(loaded.getOwner()) || loaded.getHits() != be.getHits())
                helper.fail("owner/hits not kept: '" + loaded.getOwner() + "' " + loaded.getHits());
            else if (com.solegendary.reignofnether.blocks.ThicketBlockEntity.countOwned(level, owner) != 1)
                helper.fail("the loaded thicket is not back in the cover registry");
            else
                helper.succeed();
        } catch (java.io.IOException e) {
            helper.fail("NBT round trip failed: " + e);
        } finally {
            com.solegendary.reignofnether.blocks.ThicketBlockEntity.removeAllOwned(level, owner);
            if (re != null)
                re.setRemoved();
        }
    }

    /**
     * Capture points live in their marker's persistent data: kind, owner, progress, claimant and camp timer survive the
     * NBT path and the site re-registers when the marker joins. Hired wolves re-count against the camp cap.
     */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void capture_point_and_hired_wolves_survive_reload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_reload_cp";
        var marker = net.minecraft.world.entity.EntityType.MARKER.create(level);
        var wolf = com.solegendary.reignofnether.registrars.EntityRegistrar.WOLF_UNIT.get().create(level);
        if (marker == null || wolf == null) {
            helper.fail("could not create the marker / wolf");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(7, 2, 7));
        marker.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        marker.addTag(com.solegendary.reignofnether.startpos.CapturePointServerEvents.TAG);
        var pd = marker.getPersistentData();
        pd.putInt(com.solegendary.reignofnether.startpos.CapturePointServerEvents.KEY_KIND, com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.CAMP.ordinal());
        // owned by a name no unit has, so the live once-a-second pass neither drains nor rewards it
        pd.putString(com.solegendary.reignofnether.startpos.CapturePointServerEvents.KEY_OWNER, owner);
        pd.putFloat(com.solegendary.reignofnether.startpos.CapturePointServerEvents.KEY_PROGRESS, 0.65f);
        pd.putString(com.solegendary.reignofnether.startpos.CapturePointServerEvents.KEY_CLAIMANT, owner);
        pd.putInt(com.solegendary.reignofnether.startpos.CapturePointServerEvents.KEY_CAMP_TIMER, 41);
        wolf.moveTo(at.getX() + 3.5, at.getY(), at.getZ() + 0.5, 0, 0);
        wolf.setOwnerName(owner);
        wolf.getPersistentData().putString(com.solegendary.reignofnether.startpos.CapturePointServerEvents.KEY_HIRED, owner);
        List<net.minecraft.world.entity.Entity> cleanup = new ArrayList<>();
        try {
            var mt = new net.minecraft.nbt.CompoundTag();
            var wt = new net.minecraft.nbt.CompoundTag();
            if (!marker.save(mt) || !wolf.save(wt)) {
                helper.fail("Entity.save refused");
                return;
            }
            marker.discard();
            wolf.discard();
            var m2 = net.minecraft.world.entity.EntityType.create(bytesRoundTrip(mt), level).orElse(null);
            var w2 = net.minecraft.world.entity.EntityType.create(bytesRoundTrip(wt), level).orElse(null);
            if (m2 == null || w2 == null) {
                helper.fail("NBT load dropped the marker or the wolf");
                return;
            }
            cleanup.add(m2);
            cleanup.add(w2);
            if (!owner.equals(com.solegendary.reignofnether.startpos.CapturePointServerEvents.ownerOf(m2))
                    || Math.abs(com.solegendary.reignofnether.startpos.CapturePointServerEvents.progressOf(m2) - 0.65f) > 1e-4
                    || com.solegendary.reignofnether.startpos.CapturePointServerEvents.kindOf(m2) != com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.CAMP
                    || !owner.equals(m2.getPersistentData().getString(com.solegendary.reignofnether.startpos.CapturePointServerEvents.KEY_CLAIMANT))
                    || m2.getPersistentData().getInt(com.solegendary.reignofnether.startpos.CapturePointServerEvents.KEY_CAMP_TIMER) != 41) {
                helper.fail("capture point state lost on reload: " + m2.getPersistentData());
                return;
            }
            level.addFreshEntity(m2);
            level.addFreshEntity(w2);
            com.solegendary.reignofnether.startpos.CapturePointServerEvents.rehire(w2);   // a second join must not double-count
            if (!com.solegendary.reignofnether.startpos.CapturePointServerEvents.getPoints().contains(m2))
                helper.fail("the reloaded site did not re-register");
            else if (com.solegendary.reignofnether.startpos.CapturePointServerEvents.hiredCount(owner) != 1)
                helper.fail("hired wolves counted " + com.solegendary.reignofnether.startpos.CapturePointServerEvents.hiredCount(owner) + " after a reload, expected 1");
            else
                helper.succeed();
        } catch (java.io.IOException e) {
            helper.fail("NBT round trip failed: " + e);
        } finally {
            cleanup.forEach(net.minecraft.world.entity.Entity::discard);
        }
    }

    /** Match history graphs and award totals are saved with the RTSPlayer and carry on sampling identically. */
    @GameTest(template = ARENA)
    public static void match_history_and_awards_survive_reload(GameTestHelper helper) throws java.io.IOException {
        var p = com.solegendary.reignofnether.player.RTSPlayer.getNewPlayer("gametest_reload_history", Factions.VILLAGERS, 424242, 0, true, -1L);
        p.damageDealt = 1234.5f;
        p.metalProduced = 987.25f;
        p.metalReclaimed = 55.5f;
        // past MAX_SAMPLES, so the saved history has compacted once and doubled its interval
        for (int i = 0; i < 70; i++)
            p.history.offer(i + 1, (i + 1) * 10, (i + 1) * 100);
        var data = com.solegendary.reignofnether.player.RTSPlayerSaveData.load(new net.minecraft.nbt.CompoundTag());
        data.rtsPlayers.add(p);
        var back = com.solegendary.reignofnether.player.RTSPlayerSaveData.load(bytesRoundTrip(data.save(new net.minecraft.nbt.CompoundTag())));
        if (back.rtsPlayers.size() != 1) {
            helper.fail("loaded " + back.rtsPlayers.size() + " players");
            return;
        }
        var q = back.rtsPlayers.get(0);
        if (q.damageDealt != p.damageDealt || q.metalProduced != p.metalProduced || q.metalReclaimed != p.metalReclaimed) {
            helper.fail("award totals lost: " + q.damageDealt + "/" + q.metalProduced + "/" + q.metalReclaimed);
            return;
        }
        // one more sample on both: an exact restore keeps recording on the same stride
        for (int i = 0; i < 3; i++) {
            p.history.offer(500, 600, 700);
            q.history.offer(500, 600, 700);
        }
        if (q.history.size() != p.history.size() || q.history.getIntervalTicks() != p.history.getIntervalTicks()
                || !java.util.Arrays.equals(q.history.metalSamples(), p.history.metalSamples())
                || !java.util.Arrays.equals(q.history.energySamples(), p.history.energySamples())
                || !java.util.Arrays.equals(q.history.armySamples(), p.history.armySamples()))
            helper.fail("history differs after reload: size " + q.history.size() + "/" + p.history.size() + ", interval "
                + q.history.getIntervalTicks() + "/" + p.history.getIntervalTicks());
        else
            helper.succeed();
    }

    /**
     * BAR camera bindings (design/camera.md): every rebindable key has a name in Controls, no camera binding defaults to
     * Shift (in BAR Shift only queues orders - lovish: Shift+drag lines swung the camera), building ghosts rotate on
     * [ and ], and Tab is the overview. Pure data, no world state, safe to run in parallel.
     */
    @GameTest(template = ARENA)
    public static void camera_keybindings_are_named_bar_defaults_and_never_shift(GameTestHelper helper) {
        com.google.gson.JsonObject lang = enUsLang();
        if (lang.size() == 0) {
            helper.fail("could not read assets/reignofnether/lang/en_us.json from the mod file");
            return;
        }
        List<String> gaps = new ArrayList<>();
        for (com.solegendary.reignofnether.keybinds.Keybinding kb : com.solegendary.reignofnether.keybinds.Keybindings.all()) {
            String tk = kb.getTranslationKey();
            if (tk == null)
                continue;
            if (!lang.has(tk))
                gaps.add(tk + ": no en_us name");
            if (com.solegendary.reignofnether.keybinds.Keybindings.CATEGORY_CAMERA.equals(kb.getCategory())
                    && (kb.getDefaultKey() == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT
                        || kb.getDefaultKey() == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT))
                gaps.add(tk + ": camera binding on Shift");
        }
        if (!lang.has(com.solegendary.reignofnether.keybinds.Keybindings.CATEGORY_CAMERA))
            gaps.add("camera key category has no en_us name");
        if (com.solegendary.reignofnether.keybinds.Keybindings.rotateBuildingCCW.getDefaultKey() != org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_BRACKET
                || com.solegendary.reignofnether.keybinds.Keybindings.rotateBuildingCW.getDefaultKey() != org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_BRACKET)
            gaps.add("building ghost rotation is not on [ and ]");
        if (com.solegendary.reignofnether.keybinds.Keybindings.overview.getDefaultKey() != org.lwjgl.glfw.GLFW.GLFW_KEY_TAB)
            gaps.add("overview is not on Tab");
        if (!gaps.isEmpty())
            helper.fail(String.join("; ", gaps));
        else
            helper.succeed();
    }

    /**
     * Multiplayer (design/multiplayer.md): a lobby match with two humans, started through the real lobby start
     * (MultiplayerLobby.startMatch) with two fake ServerPlayers. Each must become an RTS player with their own
     * capitol, colour and resources, on separate starts on the ring, not allied; player B may not order player A's
     * unit (UnitActionServerboundPacket.accept, PacketAuth.controllableIds); and B's defeat makes A the winner of
     * THIS match (MultiplayerMatch scopes the victory check, so other tests' bots elsewhere in the world don't
     * matter). The bases sit ~1500 blocks out on the far side from the bot soak, and the start waits 100 ticks so
     * it doesn't join the soak's battlefield (bases started within a few seconds form one).
     */
    @GameTest(template = ARENA, timeoutTicks = 500)
    public static void two_human_lobby_match_seats_both_and_scopes_commands_and_victory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String nameA = "gametest_mp_a", nameB = "gametest_mp_b";
        var fakeA = net.minecraftforge.common.util.FakePlayerFactory.get(level,
            new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(nameA.getBytes()), nameA));
        var fakeB = net.minecraftforge.common.util.FakePlayerFactory.get(level,
            new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(nameB.getBytes()), nameB));
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));

        // the ring itself: two sides sit opposite each other, equally far from the centre
        List<int[]> ring = com.solegendary.reignofnether.matchstart.MultiplayerLobby.ringTargets(BlockPos.ZERO, 2, 220, 0);
        if (ring.get(0)[0] != 220 || ring.get(1)[0] != -220 || ring.get(0)[1] != 0 || ring.get(1)[1] != 0) {
            helper.fail("a two-side ring should put the sides opposite: " + ring.get(0)[0] + "," + ring.get(0)[1]
                + " / " + ring.get(1)[0] + "," + ring.get(1)[1]);
            return;
        }
        if (com.solegendary.reignofnether.matchstart.MultiplayerLobby.ringRadius(8, 1) * 2 * Math.sin(Math.PI / 8) < 149) {
            helper.fail("eight sides on the ring must stay 150 blocks from their neighbours");
            return;
        }

        helper.runAfterDelay(100, () -> {
            // dry ground for both bases: try a few centres out past x+1500 on the far side from the soak
            BlockPos centre = null;
            // three columns of six: siteOk now reads real ground (it loads the chunk), so an ocean can wet a whole column
            for (int k = 0; k < 18 && centre == null; k++) {
                int cx = origin.getX() + 1500 + 500 * (k / 6), cz = origin.getZ() - 1500 - 500 * (k % 6);
                BlockPos c = new BlockPos(cx, com.solegendary.reignofnether.resources.MetalPatches.solidTop(level, cx, cz), cz);
                boolean dry = true;
                for (var p : com.solegendary.reignofnether.matchstart.MultiplayerLobby.startPositions(level, c, 2, 1, 0))
                    if (!com.solegendary.reignofnether.matchstart.MultiplayerLobby.siteOk(level, (int) Math.floor(p.x), (int) Math.floor(p.z)))
                        dry = false;
                if (dry)
                    centre = c;
            }
            if (centre == null) {
                helper.fail("no dry ground for two lobby bases near x=" + (origin.getX() + 1500));
                return;
            }
            var players = com.solegendary.reignofnether.player.PlayerServerEvents.players;
            players.add(fakeA);
            players.add(fakeB);
            var seatA = new com.solegendary.reignofnether.matchstart.MultiplayerLobby.Seat(900001, false);
            seatA.name = nameA;
            seatA.uuid = fakeA.getUUID();
            seatA.faction = 0;   // Sunforged
            seatA.team = 0;
            var seatB = new com.solegendary.reignofnether.matchstart.MultiplayerLobby.Seat(900002, false);
            seatB.name = nameB;
            seatB.uuid = fakeB.getUUID();
            seatB.faction = 1;   // Gravebound
            seatB.team = 1;
            var match = com.solegendary.reignofnether.matchstart.MultiplayerLobby.startMatch(level, List.of(seatA, seatB),
                0, 1, 1, centre, new java.util.Random(7));
            List<String> problems = new ArrayList<>();
            var rA = com.solegendary.reignofnether.player.PlayerServerEvents.getRTSPlayer(nameA);
            var rB = com.solegendary.reignofnether.player.PlayerServerEvents.getRTSPlayer(nameB);
            if (rA == null || rB == null)
                problems.add("both humans should be RTS players (A " + (rA != null) + ", B " + (rB != null) + ")");
            else if (rA.startPosColorId == rB.startPosColorId)
                problems.add("the two humans got the same colour");
            if (!match.participants.containsAll(List.of(nameA, nameB)) || !match.humans.containsAll(List.of(nameA, nameB)))
                problems.add("the match should list both humans: " + match.participants);
            if (com.solegendary.reignofnether.alliance.AlliancesServerEvents.isAllied(nameA, nameB))
                problems.add("opponents on different teams must not be allied");
            for (String n : List.of(nameA, nameB))
                if (com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.stream().noneMatch(r -> n.equals(r.ownerName)))
                    problems.add(n + " has no resources");

            helper.runAfterDelay(10, () -> {
                // one capitol each, on separate starts
                BlockPos capA = null, capB = null;
                int capsA = 0, capsB = 0;
                for (var b : com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings()) {
                    if (!b.isCapitol)
                        continue;
                    if (nameA.equals(b.ownerName)) { capsA++; capA = b.originPos; }
                    if (nameB.equals(b.ownerName)) { capsB++; capB = b.originPos; }
                }
                if (capsA != 1 || capsB != 1)
                    problems.add("each human should have one capitol (A " + capsA + ", B " + capsB + ")");
                else if (capA.distSqr(capB) < 150 * 150)
                    problems.add("the two capitols are only " + (int) Math.sqrt(capA.distSqr(capB)) + " blocks apart");

                // B may not order A's units
                net.minecraft.world.entity.LivingEntity unitA = null;
                for (var le : com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits())
                    if (le instanceof com.solegendary.reignofnether.unit.interfaces.Unit u && nameA.equals(u.getOwnerName()))
                        unitA = le;
                if (unitA == null) {
                    problems.add("A has no units to test commands on");
                } else {
                    int[] ids = new int[] { unitA.getId() };
                    if (com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket.accept(fakeB, nameA,
                            com.solegendary.reignofnether.unit.UnitAction.MOVE, -1, ids, unitA.blockPosition(), BlockPos.ZERO, false))
                        problems.add("B's order in A's name was accepted");
                    if (com.solegendary.reignofnether.network.PacketAuth.controllableIds(level, nameB, ids).length != 0)
                        problems.add("B may control A's unit");
                    if (com.solegendary.reignofnether.network.PacketAuth.controllableIds(level, nameA, ids).length != 1)
                        problems.add("A may not control its own unit");
                }

                // B is beaten: A wins this match
                com.solegendary.reignofnether.player.PlayerServerEvents.defeat(nameB, "gametest");
                boolean aWon;
                synchronized (com.solegendary.reignofnether.player.PlayerServerEvents.postGameRtsPlayers) {
                    aWon = com.solegendary.reignofnether.player.PlayerServerEvents.postGameRtsPlayers.stream()
                        .anyMatch(p -> p != null && nameA.equals(p.name));
                }
                if (com.solegendary.reignofnether.player.PlayerServerEvents.isRTSPlayer(nameB))
                    problems.add("B is still in the match after its defeat");
                if (!aWon || !match.isOver())
                    problems.add("A should have won when B was defeated (won " + aWon + ", match over " + match.isOver() + ")");

                // clean up: A leaves through a match of its own (so its defeat can't crown anyone else), and the fake
                // players leave the online list
                new com.solegendary.reignofnether.matchstart.MultiplayerMatch(java.util.Set.of(nameA), java.util.Set.of(nameA)).register();
                com.solegendary.reignofnether.player.PlayerServerEvents.defeat(nameA, "gametest");
                com.solegendary.reignofnether.matchstart.MultiplayerMatch leftover = com.solegendary.reignofnether.matchstart.MultiplayerMatch.of(nameA);
                if (leftover != null)
                    leftover.end();
                players.remove(fakeA);
                players.remove(fakeB);
                if (!problems.isEmpty())
                    helper.fail(String.join("; ", problems));
                else
                    helper.succeed();
            });
        });
    }
    // ------------------------------------------------------------------ qol-speed: economy pace, batch commands, trees

    /**
     * Economy pace (EconomyPace): one multiplier on income AND on production speed. A built wind generator's
     * income and a queued Grunt's progress per tick, at Relaxed / Standard / Fast for this test's own owner (a
     * per-owner override: the match pace is world-wide and tests run in parallel).
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void economy_pace_scales_income_and_build_speed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_pace_" + helper.absolutePos(BlockPos.ZERO).asLong();
        var pool = testPool(owner);
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 215;
        int cx = base.getX(), cz = base.getZ();
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -5; dz <= 5; dz++) {
                level.setBlock(new BlockPos(cx + dx, y - 1, cz + dz), Blocks.DIRT.defaultBlockState(), 2);
                level.setBlock(new BlockPos(cx + dx, y, cz + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            }
        var wind = com.solegendary.reignofnether.building.BuildingServerEvents.placeBuilding(
            Buildings.WIND_GENERATOR_VILLAGERS, new BlockPos(cx - 1, y, cz - 1), Rotation.NONE, owner, new int[0],
            false, false, true, true);
        try {
            if (wind == null) {
                helper.fail("placeBuilding returned null for the wind generator");
                return;
            }
            wind.isBuilt = true;
            var eco = com.solegendary.reignofnether.resources.EconomyServerEvents.getEconomy(owner);
            float[] income = new float[3];
            for (int code = 0; code < 3; code++) {
                com.solegendary.reignofnether.resources.EconomyPace.setTestOverride(owner, code);
                com.solegendary.reignofnether.resources.EconomyServerEvents.recalculateIncomeAndStorage();
                income[code] = eco.energyIncome;
            }
            if (income[1] <= 0) {
                helper.fail("a built wind generator gives no energy income at Standard pace");
                return;
            }
            for (int code : new int[] { 0, 2 }) {
                float want = income[1] * com.solegendary.reignofnether.resources.EconomyPace.multiplier(code);
                if (Math.abs(income[code] - want) > 0.01f) {
                    helper.fail(com.solegendary.reignofnether.resources.EconomyPace.name(code) + " income should be "
                        + want + " (Standard " + income[1] + "), got " + income[code]);
                    return;
                }
            }
        } finally {
            if (wind != null) {
                com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(wind);
                wind.forceChunk(false);
            }
        }

        // production: the same queued Grunt advances pace-times as fast per tick (resources plentiful, no stall)
        var pp = readyPortal(helper, owner, 78);
        if (pp == null) {
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
            com.solegendary.reignofnether.resources.EconomyPace.setTestOverride(owner, null);
            return;
        }
        var active = pp.productionQueue.get(0);
        float[] progress = new float[3];
        for (int code = 0; code < 3; code++) {
            com.solegendary.reignofnether.resources.EconomyPace.setTestOverride(owner, code);
            pool.ore = 900;
            pool.wood = 900;
            active.ticksLeft = 10000;
            for (int i = 0; i < 10; i++)
                pp.tick(level);
            progress[code] = 10000 - (float) active.ticksLeft;
        }
        com.solegendary.reignofnether.resources.EconomyPace.setTestOverride(owner, null);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
        pp.productionQueue.clear();
        if (progress[1] <= 0) {
            helper.fail("the Grunt made no progress at Standard pace");
            return;
        }
        for (int code : new int[] { 0, 2 }) {
            float want = progress[1] * com.solegendary.reignofnether.resources.EconomyPace.multiplier(code);
            if (Math.abs(progress[code] - want) > 0.05f * want) {
                helper.fail(com.solegendary.reignofnether.resources.EconomyPace.name(code) + " production should advance "
                    + want + " ticks in 10 (Standard " + progress[1] + "), got " + progress[code]);
                return;
            }
        }
        helper.succeed();
    }

    /** Three stamped, finished, unregistered placements of {@code building} stacked on pads (ticked by the test). */
    private static List<com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement> stampedRow(
            GameTestHelper helper, Building building, String owner, int firstPadY) {
        ServerLevel level = helper.getLevel();
        BlockPos corner = helper.absolutePos(BlockPos.ZERO);
        List<com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement> out = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            var bp = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(building, level,
                new BlockPos(corner.getX() + 2, firstPadY + i * 26, corner.getZ() + 3), Rotation.NONE, owner, false);
            if (!(bp instanceof com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement pp)) {
                helper.fail(building.name + " is not a production placement: " + bp);
                return null;
            }
            stampOnPad(level, bp, 2);
            pp.isBuilt = true;
            out.add(pp);
        }
        return out;
    }

    /**
     * Multi-building command card, refits (BuildingBatchServerboundPacket.applyEach): the Tier 2 extractor refit on
     * three selected extractors starts on each building that fits in what is banked - here two of three - and each
     * started refit then pays its own cost; with nothing banked only the first starts (a single click never asks
     * for money up front in the flow economy).
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void batch_refit_charges_each_building_and_skips_unaffordable(GameTestHelper helper) {
        String owner = "gametest_batch_refit_" + helper.absolutePos(BlockPos.ZERO).asLong();
        var pool = testPool(owner);
        var item = com.solegendary.reignofnether.building.production.ProductionItems.UPGRADE_EXTRACTOR;
        var cost = item.getCost(false, owner);
        // the refit needs Tier 2 on the server too (resources/TierGate)
        com.solegendary.reignofnether.research.ResearchServerEvents.addResearch(owner,
            com.solegendary.reignofnether.building.production.ProductionItems.RESEARCH_TIER_2);
        try {
            var row = stampedRow(helper, Buildings.METAL_EXTRACTOR_VILLAGERS, owner, 90);
            if (row == null)
                return;
            pool.ore = Math.round(cost.metal() * 2 + 1);
            pool.wood = Math.round(cost.energy() * 2 + 1);
            var started = com.solegendary.reignofnether.building.BuildingBatchServerboundPacket.applyEach(row, item);
            if (started.size() != 2 || row.get(2).productionQueue.size() != 0
                    || row.get(0).productionQueue.size() != 1 || row.get(1).productionQueue.size() != 1) {
                helper.fail("with two refits banked, two of three extractors should start the refit; started "
                    + started.size() + ", queues " + row.get(0).productionQueue.size() + "/"
                    + row.get(1).productionQueue.size() + "/" + row.get(2).productionQueue.size());
                return;
            }
            float before = pool.getMetal();
            for (int i = 0; i < 10; i++)
                for (var pp : row)
                    pp.tick(helper.getLevel());
            float spentA = row.get(0).productionQueue.isEmpty() ? 0 : row.get(0).productionQueue.get(0).metalSpent;
            float spentB = row.get(1).productionQueue.isEmpty() ? 0 : row.get(1).productionQueue.get(0).metalSpent;
            if (spentA <= 0 || spentB <= 0 || Math.abs((before - pool.getMetal()) - (spentA + spentB)) > 0.5f) {
                helper.fail("each started refit should pay its own way: spent " + spentA + " + " + spentB
                    + ", pool dropped " + (before - pool.getMetal()));
                return;
            }
            for (var pp : row)
                pp.productionQueue.clear();
            pool.ore = 0;
            pool.oreFrac = 0;
            pool.wood = 0;
            var broke = com.solegendary.reignofnether.building.BuildingBatchServerboundPacket.applyEach(row, item);
            if (broke.size() != 1 || broke.get(0) != row.get(0))
                helper.fail("with nothing banked only the first extractor should start its refit, started " + broke.size());
            else
                helper.succeed();
            for (var pp : row)
                pp.productionQueue.clear();
        } finally {
            com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(owner);
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
        }
    }

    /**
     * Multi-building command card, units (BuildingBatchServerboundPacket.queueRoundRobin): six Grunt clicks on three
     * selected portals land 0,1,2,0,1,2 - two each - and a full factory is skipped for the next one.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void batch_queue_round_robins_across_factories(GameTestHelper helper) {
        String owner = "gametest_batch_rr_" + helper.absolutePos(BlockPos.ZERO).asLong();
        var pool = testPool(owner);
        var grunt = com.solegendary.reignofnether.building.production.ProductionItems.GRUNT;
        try {
            var row = stampedRow(helper, Buildings.CENTRAL_PORTAL, owner, 70);
            if (row == null)
                return;
            int cursor = 0;
            StringBuilder order = new StringBuilder();
            for (int click = 0; click < 6; click++) {
                int at = com.solegendary.reignofnether.building.BuildingBatchServerboundPacket.queueRoundRobin(row, grunt, cursor);
                order.append(at);
                if (at < 0)
                    break;
                cursor = at + 1;
            }
            if (!order.toString().equals("012012")) {
                helper.fail("six clicks should go round-robin 012012, went " + order);
                return;
            }
            // factory 1 full: the click meant for it goes on to factory 2
            var p1 = row.get(1);
            for (int i = 0; i < 64 && p1.startProductionItem(grunt); i++)
                ;
            int at = com.solegendary.reignofnether.building.BuildingBatchServerboundPacket.queueRoundRobin(row, grunt, 1);
            if (at != 2)
                helper.fail("a full factory should be skipped for the next one; the click went to " + at);
            else
                helper.succeed();
            for (var pp : row)
                pp.productionQueue.clear();
        } finally {
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
        }
    }

    /** A 5-log oak with a natural (non-persistent, in-reach) canopy on {@code base}; returns the canopy cells. */
    private static List<BlockPos> plantTree(ServerLevel level, BlockPos base) {
        var log = Blocks.OAK_LOG.defaultBlockState();
        var leaf = Blocks.OAK_LEAVES.defaultBlockState()
            .setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE, 1)
            .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, false);
        level.setBlock(base.below(), Blocks.DIRT.defaultBlockState(), 2);
        for (int i = 0; i < 5; i++)
            level.setBlock(base.above(i), log, 2);
        List<BlockPos> leaves = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                for (int dy = 3; dy <= 5; dy++) {
                    if (dx == 0 && dz == 0 && dy < 5)
                        continue;   // the trunk
                    BlockPos p = base.offset(dx, dy, dz);
                    level.setBlock(p, leaf, 2);
                    leaves.add(p);
                }
        return leaves;
    }

    /** A flat grass pad with air above it. */
    private static void grassPad(ServerLevel level, BlockPos centre, int half) {
        for (int dx = -half; dx <= half; dx++)
            for (int dz = -half; dz <= half; dz++) {
                level.setBlock(centre.offset(dx, -1, dz), Blocks.DIRT.defaultBlockState(), 2);
                level.setBlock(centre.offset(dx, 0, dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                for (int dy = 1; dy <= 10; dy++)
                    level.setBlock(centre.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
            }
    }

    /**
     * Trees are natural reclaim (TreeClearing): a building site fells the tree in its 2-block margin - logs and
     * leaves gone, no item dropped, 5 energy per log to the owner - and leaves a tree outside the margin alone. The
     * placement ghost's scan reports the same tree and energy.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void building_site_fells_trees_for_energy(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_trees_" + helper.absolutePos(BlockPos.ZERO).asLong();
        var pool = testPool(owner);
        BlockPos centre = helper.absolutePos(new BlockPos(8, 1, 8)).atY(170);
        grassPad(level, centre, 14);
        var site = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(
            Buildings.WIND_GENERATOR_VILLAGERS, level, centre, Rotation.NONE, owner, false);
        try {
            if (site == null) {
                helper.fail("could not make a wind generator placement");
                return;
            }
            int groundY = centre.getY() + 1;
            BlockPos inMargin = new BlockPos(site.minCorner.getX() - 2, groundY, (site.minCorner.getZ() + site.maxCorner.getZ()) / 2);
            BlockPos outside = new BlockPos(site.maxCorner.getX() + 7, groundY, site.maxCorner.getZ() + 7);
            List<BlockPos> leaves = plantTree(level, inMargin);
            plantTree(level, outside);

            var ghost = com.solegendary.reignofnether.resources.TreeClearing.scanFootprint(level, site.minCorner, site.maxCorner);
            if (ghost.trees.size() != 1 || ghost.logs != 5 || Math.round(ghost.energy()) != 25) {
                helper.fail("the ghost scan should find 1 tree / 5 logs / 25 energy, found " + ghost.trees.size()
                    + " / " + ghost.logs + " / " + ghost.energy() + " (site " + site.minCorner.toShortString() + " .. "
                    + site.maxCorner.toShortString() + ", tree " + inMargin.toShortString() + ")");
                return;
            }
            var job = com.solegendary.reignofnether.resources.TreeClearing.clearForBuilding(level, site);
            if (job == null) {
                helper.fail("placing the site queued no felling");
                return;
            }
            job.finish();
            for (int i = 0; i < 5; i++)
                if (!level.getBlockState(inMargin.above(i)).isAir()) {
                    helper.fail("a log of the tree in the margin is still standing at +" + i);
                    return;
                }
            for (BlockPos p : leaves)
                if (!level.getBlockState(p).isAir()) {
                    helper.fail("a leaf of the felled tree is still there");
                    return;
                }
            if (!level.getBlockState(outside).is(Blocks.OAK_LOG)) {
                helper.fail("the tree outside the 2-block margin was felled too");
                return;
            }
            if (Math.abs(pool.getEnergy() - 25f) > 0.5f || Math.abs(job.energyCredited - 25f) > 0.5f) {
                helper.fail("5 logs should credit 25 energy, pool has " + pool.getEnergy() + " (credited " + job.energyCredited + ")");
                return;
            }
            var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(inMargin).inflate(8));
            if (!drops.isEmpty())
                helper.fail("felling dropped " + drops.size() + " item(s)");
            else
                helper.succeed();
        } finally {
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
        }
    }

    /**
     * Area reclaim includes trees: a reclaim circle over a tree hands it to the worker (a move to the trunk plus a
     * fell order); once the worker is in reach the tree comes down for its owner's energy.
     */
    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void area_reclaim_includes_trees(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_tree_reclaim_" + helper.absolutePos(BlockPos.ZERO).asLong();
        var pool = testPool(owner);
        BlockPos centre = helper.absolutePos(new BlockPos(8, 1, 8)).atY(185);
        grassPad(level, centre, 10);
        BlockPos base = centre.offset(3, 1, 0);
        plantTree(level, base);
        var worker = com.solegendary.reignofnether.registrars.EntityRegistrar.VILLAGER_UNIT.get().create(level);
        if (worker == null) {
            helper.fail("could not create a worker");
            return;
        }
        worker.moveTo(centre.getX() - 4 + 0.5, centre.getY() + 1, centre.getZ() + 0.5, 0, 0);
        worker.setOwnerName(owner);
        level.addFreshEntity(worker);
        int handed = com.solegendary.reignofnether.unit.AreaCommands.issue(level, owner,
            com.solegendary.reignofnether.unit.AreaCommands.MODE_RECLAIM, centre.above(), 8, new int[] { worker.getId() }, false);
        if (handed != 1 || !com.solegendary.reignofnether.resources.TreeClearing.hasOrder(worker.getId(), base)) {
            helper.fail("the reclaim circle should hand the tree to the worker; handed " + handed);
            com.solegendary.reignofnether.unit.UnitServerEvents.clearQueuedActions(worker.getId());
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
            worker.discard();
            return;
        }
        // in reach: the next order check fells it
        worker.teleportTo(base.getX() - 2 + 0.5, base.getY(), base.getZ() + 0.5);
        com.solegendary.reignofnether.resources.TreeClearing.checkOrders(level);
        helper.runAfterDelay(5, () -> {
            try {
                if (!level.getBlockState(base).isAir() || !level.getBlockState(base.above(4)).isAir())
                    helper.fail("the worker in reach did not fell the tree");
                else if (Math.abs(pool.getEnergy() - 25f) > 0.5f)
                    helper.fail("the felled tree should credit 25 energy, pool has " + pool.getEnergy());
                else
                    helper.succeed();
            } finally {
                com.solegendary.reignofnether.unit.UnitServerEvents.clearQueuedActions(worker.getId());
                com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
                worker.discard();
            }
        });
    }

    // ---------------------------------------------------------------------------------------------------------------
    // building levels, annexes and decommission (design/building_upgrades.md). lovish: "make buildings able to be
    // upgraded ... to replace the con turret", "reclaim buildings that aren't necessary to use resources elsewhere"
    // ---------------------------------------------------------------------------------------------------------------

    /** Runs a placement's production queue synchronously (no world ticks) until it is empty or {@code limit} steps. */
    private static int runQueue(com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement pp, int limit) {
        int n = 0;
        while (!pp.productionQueue.isEmpty() && n++ < limit) {
            var a = pp.productionQueue.get(0);
            if (a.item.tick(pp, a))
                pp.productionQueue.remove(0);
        }
        return n;
    }

    /**
     * A lab's in-place upgrade: L1 -> L2 -> L3 through its own queue charges exactly the level's metal and energy,
     * raises the production build power to 175% / 250% (a unit then advances 1.75 ticks per tick), the HP to x1.5 /
     * x2, refuses L3 before L2 is done, and the saved code restores the level on a fresh placement (save / late join).
     */
    @GameTest(template = ARENA)
    public static void lab_level_up_speeds_production_and_charges_its_cost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_lab_level_" + helper.absolutePos(BlockPos.ZERO).asLong();
        var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 1000, 600);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
        // placements never put in the world: enough for the queue and the structure swap, invisible to other tests
        var bp = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(Buildings.BARRACKS, level,
            helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, owner, false);
        try {
            if (!(bp instanceof com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement pp)) {
                helper.fail("the Barracks is not a production placement: " + bp);
                return;
            }
            pp.isBuilt = true;
            List<String> problems = new ArrayList<>();
            if (pp.getBuildingLevel() != 1 || Math.abs(pp.getProductionBuildPower() - 1f) > 1e-3)
                problems.add("a new Barracks is level " + pp.getBuildingLevel() + " at " + pp.getProductionBuildPower());
            float m0 = pool.getMetal(), e0 = pool.getEnergy();
            if (!pp.startProductionItem(com.solegendary.reignofnether.building.production.ProductionItems.UPGRADE_LAB_L2)) {
                helper.fail("the Level 2 upgrade was refused");
                return;
            }
            if (pp.startProductionItem(com.solegendary.reignofnether.building.production.ProductionItems.UPGRADE_LAB_L3))
                problems.add("Level 3 was accepted before Level 2 finished");
            runQueue(pp, 5000);
            float paidM = m0 - pool.getMetal(), paidE = e0 - pool.getEnergy();
            var c2 = com.solegendary.reignofnether.building.BuildingLevels.UPGRADE_L2_COST;
            if (pp.getBuildingLevel() != 2)
                problems.add("after the upgrade the Barracks is level " + pp.getBuildingLevel() + " (" + pp.currentStructureName + ")");
            if (Math.abs(paidM - c2.metal()) > 1 || Math.abs(paidE - c2.energy()) > 1)
                problems.add("Level 2 cost " + paidM + " metal / " + paidE + " energy, expected " + c2.metal() + " / " + c2.energy());
            if (Math.abs(pp.getProductionBuildPower() - 1.75f) > 1e-3)
                problems.add("L2 production build power " + pp.getProductionBuildPower() + ", expected 1.75");
            if (pp.getMaxHealth() != (int) Math.round(Buildings.BARRACKS.maxHealth * 1.5))
                problems.add("L2 max HP " + pp.getMaxHealth() + ", expected x1.5 of " + Buildings.BARRACKS.maxHealth);
            // a unit at L2 advances 1.75 ticks of its production per tick
            if (pp.startProductionItem(com.solegendary.reignofnether.building.production.ProductionItems.VINDICATOR)) {
                var a = pp.productionQueue.get(0);
                float t0 = a.ticksLeft;
                a.item.tick(pp, a);
                if (Math.abs((t0 - a.ticksLeft) - 1.75f) > 1e-3)
                    problems.add("a unit advanced " + (t0 - a.ticksLeft) + " ticks in one L2 tick, expected 1.75");
                pp.productionQueue.clear();
            } else
                problems.add("the L2 Barracks refused a Vindicator");
            // L2 -> L3
            pool.addEnergy(800);
            if (!pp.startProductionItem(com.solegendary.reignofnether.building.production.ProductionItems.UPGRADE_LAB_L3))
                problems.add("the Level 3 upgrade was refused at Level 2");
            runQueue(pp, 5000);
            if (pp.getBuildingLevel() != 3 || Math.abs(pp.getProductionBuildPower() - 2.5f) > 1e-3)
                problems.add("after L3: level " + pp.getBuildingLevel() + " at " + pp.getProductionBuildPower() + ", expected 3 at 2.5");
            if (pp.getMaxHealth() != (int) Math.round(Buildings.BARRACKS.maxHealth * 2.0))
                problems.add("L3 max HP " + pp.getMaxHealth());
            if (pp.startProductionItem(com.solegendary.reignofnether.building.production.ProductionItems.UPGRADE_LAB_L2))
                problems.add("an L3 lab accepted the Level 2 order again");
            // the saved / synced code restores the level on a fresh placement (BuildingServerEvents load, late joiners)
            int code = pp.getUpgradeLevel();
            var fresh = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(Buildings.BARRACKS, level,
                helper.absolutePos(new BlockPos(2, 2, 2)), Rotation.NONE, owner, false);
            com.solegendary.reignofnether.building.BuildingLevels.restore(fresh, code);
            if (fresh.getBuildingLevel() != 3)
                problems.add("upgrade code " + code + " restored level " + fresh.getBuildingLevel() + ", expected 3");
            if (com.solegendary.reignofnether.building.BuildingLevels.levelOfStructure("graveyard_l2") != 2
                    || com.solegendary.reignofnether.building.BuildingLevels.levelOfStructure("overflowing_graveyard") != 1)
                problems.add("levelOfStructure misreads structure names");
            if (!problems.isEmpty())
                helper.fail(String.join("; ", problems));
            else
                helper.succeed();
        } finally {
            if (bp instanceof com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement pp)
                pp.productionQueue.clear();
            com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
        }
    }

    /** Stamps a building on a stone pad (flag 2: nothing pops off), as a finished building in the server's list. */
    private static com.solegendary.reignofnether.building.BuildingPlacement stampFinished(ServerLevel level, Building b,
                                                                                          BlockPos origin, String owner) {
        var bp = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(b, level, origin, Rotation.NONE, owner, false);
        if (bp == null)
            return null;
        for (int x = bp.minCorner.getX() - 1; x <= bp.maxCorner.getX() + 1; x++)
            for (int z = bp.minCorner.getZ() - 1; z <= bp.maxCorner.getZ() + 1; z++)
                level.setBlock(new BlockPos(x, bp.minCorner.getY() - 1, z), Blocks.STONE.defaultBlockState(), 2);
        for (var blk : bp.getBlocks())
            if (!blk.getBlockState().isAir())
                level.setBlock(blk.getBlockPos(), blk.getBlockState(), 2);
        bp.initPlacedBlocks();
        bp.isBuilt = true;
        com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().add(bp);
        return bp;
    }

    private static void clearStamped(ServerLevel level, com.solegendary.reignofnether.building.BuildingPlacement bp) {
        if (bp == null)
            return;
        com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().remove(bp);
        for (var blk : bp.getBlocks())
            level.setBlock(blk.getBlockPos(), Blocks.AIR.defaultBlockState(), 2);
    }

    /**
     * Annexes: one beside a lab attaches to it and adds 40% build power, a second adds another 40%, a third spot is
     * refused (slots full) - and an annex is its own building: destroying it leaves the lab standing, one annex down.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void lab_annex_adds_build_power_and_dies_on_its_own(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_annex_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 222;   // this test's own height
        int cx = base.getX(), cz = base.getZ();
        var lab = stampFinished(level, Buildings.BARRACKS, new BlockPos(cx, y, cz), owner);
        var annexA = stampFinished(level, Buildings.LAB_ANNEX_VILLAGERS, new BlockPos(cx + 2, y, lab.minCorner.getZ() - 5), owner);
        var annexB = stampFinished(level, Buildings.LAB_ANNEX_VILLAGERS, new BlockPos(cx + 2, y, lab.maxCorner.getZ() + 3), owner);
        Runnable cleanup = () -> {
            clearStamped(level, annexA);
            clearStamped(level, annexB);
            clearStamped(level, lab);
        };
        List<String> problems = new ArrayList<>();
        try {
            if (com.solegendary.reignofnether.building.buildings.shared.LabAnnex.hostOf(annexA) != lab
                    || com.solegendary.reignofnether.building.buildings.shared.LabAnnex.hostOf(annexB) != lab)
                problems.add("the annexes did not attach to the Barracks beside them");
            lab.annexCacheTick = -1;
            float two = lab.getProductionBuildPower();
            if (Math.abs(two - (1f + 2 * com.solegendary.reignofnether.building.BuildingLevels.ANNEX_BUILD_POWER)) > 1e-3)
                problems.add("an L1 lab with two annexes builds at " + two + ", expected 1.8");
            String third = com.solegendary.reignofnether.building.buildings.shared.LabAnnex.placementError(level, owner,
                new BlockPos(lab.maxCorner.getX() + 3, y, cz), new BlockPos(lab.maxCorner.getX() + 5, y + 5, cz + 2));
            if (!"building.reignofnether.annex_slots_full".equals(third))
                problems.add("a third annex spot answered " + third + ", expected slots full");
            String lonely = com.solegendary.reignofnether.building.buildings.shared.LabAnnex.placementError(level, owner,
                new BlockPos(cx + 40, y, cz + 40), new BlockPos(cx + 42, y + 5, cz + 42));
            if (!"building.reignofnether.annex_needs_lab".equals(lonely))
                problems.add("an annex far from any lab answered " + lonely);
            // kill annex A: the server's building tick takes it down; the lab stays
            annexA.destroyRandomBlocks(10000);
        } catch (Exception ex) {
            problems.add("threw " + ex);
        }
        helper.runAfterDelay(20, () -> {
            try {
                var buildings = com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings();
                if (buildings.contains(annexA))
                    problems.add("the destroyed annex is still a building");
                if (!buildings.contains(lab))
                    problems.add("killing the annex took the lab with it");
                lab.annexCacheTick = -1;
                float one = lab.getProductionBuildPower();
                if (Math.abs(one - (1f + com.solegendary.reignofnether.building.BuildingLevels.ANNEX_BUILD_POWER)) > 1e-3)
                    problems.add("after losing an annex the lab builds at " + one + ", expected 1.4");
                if (!problems.isEmpty())
                    helper.fail(String.join("; ", problems));
                else
                    helper.succeed();
            } finally {
                cleanup.run();
            }
        });
    }

    /**
     * Decommission: an own Level 2 Barracks dismantles over time and returns 60% of the metal put into it (its cost
     * plus the Level 2 upgrade), its blocks come down and the building is gone - no wreck. The last building left
     * can't be decommissioned, and neither can a building that isn't the sender's (PacketAuth on the action).
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void decommission_returns_the_metal_share_and_removes_the_building(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_decom_" + helper.absolutePos(BlockPos.ZERO).asLong();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 226;   // this test's own height
        int cx = base.getX(), cz = base.getZ();
        var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 0, 0);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
        var sold = stampFinished(level, Buildings.BARRACKS, new BlockPos(cx, y, cz), owner);
        var kept = stampFinished(level, Buildings.BARRACKS, new BlockPos(cx, y, cz + 14), owner);
        List<String> problems = new ArrayList<>();
        BlockPos probe = null;
        try {
            com.solegendary.reignofnether.building.BuildingLevels.levelUp(sold);
            if (sold.getBuildingLevel() != 2)
                problems.add("the Barracks did not reach level 2 for the test");
            for (var blk : sold.getBlocks())
                if (!blk.getBlockState().isAir() && level.getBlockState(blk.getBlockPos()).equals(blk.getBlockState())) {
                    probe = blk.getBlockPos();
                    break;
                }
            int expected = Math.round((Buildings.BARRACKS.cost.metal()
                + com.solegendary.reignofnether.building.BuildingLevels.UPGRADE_L2_COST.metal())
                * com.solegendary.reignofnether.building.BuildingReclaim.REFUND_SHARE);
            if (com.solegendary.reignofnether.building.BuildingReclaim.refundFor(sold) != expected)
                problems.add("refundFor says " + com.solegendary.reignofnether.building.BuildingReclaim.refundFor(sold) + ", expected " + expected);
            if (com.solegendary.reignofnether.network.PacketAuth.mayActFor("gametest_someone_else", owner))
                problems.add("another player may act for the owner");
            if (!com.solegendary.reignofnether.building.BuildingReclaim.start(sold, owner)) {
                helper.fail("the decommission was refused: " + com.solegendary.reignofnether.building.BuildingReclaim.refusal(sold, false));
                cleanupDecom(level, sold, kept, pool);
                return;
            }
            // 2 minutes of work in one go (the real step is 10 ticks of the server tick)
            for (int i = 0; i < 120 && com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().contains(sold); i++)
                com.solegendary.reignofnether.building.BuildingReclaim.tick(1f);
            if (com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().contains(sold))
                problems.add("the building is still standing after the work (progress " + sold.reclaimProgress + ")");
            if (Math.abs(pool.getMetal() - expected) > 1.01f)
                problems.add("decommissioning returned " + pool.getMetal() + " metal, expected " + expected);
            if (pool.getEnergy() > 0)
                problems.add("decommissioning returned energy: " + pool.getEnergy());
            if (probe != null && !level.getBlockState(probe).isAir())
                problems.add("a block of the decommissioned building is still standing at " + probe);
            if (!com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().contains(kept))
                problems.add("the other Barracks went too");
            if (!"hud.reignofnether.decommission.last_building".equals(
                    com.solegendary.reignofnether.building.BuildingReclaim.refusal(kept, false)))
                problems.add("the last building may be decommissioned");
        } catch (Exception ex) {
            problems.add("threw " + ex);
        }
        cleanupDecom(level, sold, kept, pool);
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }

    private static void cleanupDecom(ServerLevel level, com.solegendary.reignofnether.building.BuildingPlacement a,
                                     com.solegendary.reignofnether.building.BuildingPlacement b,
                                     com.solegendary.reignofnether.resources.Resources pool) {
        clearStamped(level, a);
        clearStamped(level, b);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
    }

    /**
     * Every levelled building's L2 / L3 structure ships in both trees (data/ for the server, assets/ for the client),
     * keeps its footprint, and - stamped on a flat pad - has no cell a unit can walk into but not out of, and a
     * spawn point units can leave (the pocket rules of structures_have_no_unit_pockets).
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void lab_level_structures_ship_and_have_no_pockets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var resources = level.getServer().getResourceManager();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y0 = 200, margin = 3;
        int chunkX = base.getX() >> 4, chunkZ = (base.getZ() + 6000 + 5 * 64) >> 4;   // past the four pocket buckets
        List<String> problems = new ArrayList<>();
        int i = 0, checked = 0;
        for (Building building : ReignOfNetherRegistries.BUILDING) {
            if (building.maxLevel <= 1)
                continue;
            for (int lv = 2; lv <= building.maxLevel; lv++) {
                String name = building.getLevelStructureName(lv);
                if (BuildingBlockData.getBuildingNbt(name, resources) == null)
                    problems.add("data/" + ReignOfNether.MOD_ID + "/structures/" + name + ".nbt missing");
                if (!modFileHas("assets", ReignOfNether.MOD_ID, "structures", name + ".nbt"))
                    problems.add("assets/" + ReignOfNether.MOD_ID + "/structures/" + name + ".nbt missing");
                int sx = ((chunkX + i * 4) << 4) + 8, sz = (chunkZ << 4) + 8;
                i++;
                var bp = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(building, level,
                    new BlockPos(sx, y0, sz), Rotation.NONE, "gametest_level_pockets", false);
                if (bp == null || bp.getBlocks().isEmpty()) {
                    problems.add(name + ": no placement");
                    continue;
                }
                BlockPos fMin = bp.minCorner, fMax = bp.maxCorner;
                bp.changeStructure(name);
                if (bp.getBuildingLevel() != lv) {
                    problems.add(name + ": the placement did not take the structure (level " + bp.getBuildingLevel() + ")");
                    continue;
                }
                BlockPos min = bp.minCorner, max = bp.maxCorner;
                if (min.getX() != fMin.getX() || min.getZ() != fMin.getZ() || max.getX() != fMax.getX() || max.getZ() != fMax.getZ())
                    problems.add(name + ": the footprint changed (an in-place upgrade would hit its neighbours)");
                int gy = min.getY();
                for (int x = min.getX() - margin; x <= max.getX() + margin; x++)
                    for (int z = min.getZ() - margin; z <= max.getZ() + margin; z++) {
                        level.setBlock(new BlockPos(x, gy - 1, z), Blocks.STONE.defaultBlockState(), 2);
                        level.setBlock(new BlockPos(x, gy, z), Blocks.STONE.defaultBlockState(), 2);
                        for (int y = gy + 1; y <= max.getY() + 3; y++)
                            level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                for (var blk : bp.getBlocks())
                    if (blk.getBlockPos().getY() > gy || !blk.getBlockState().isAir())
                        level.setBlock(blk.getBlockPos(), blk.getBlockState(), 2);
                checked++;
                PocketAudit audit = new PocketAudit(level, min, max, gy + 1, max.getY() + 2);
                for (BlockPos c : audit.entered())
                    if (!audit.escapes(c)) {
                        problems.add(name + ": a unit can walk into " + audit.rel(c) + " but not out");
                        break;
                    }
                if (bp instanceof com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement pp) {
                    BlockPos spawn = com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement
                        .safeSpawnPoint(level, pp.getIndoorSpawnPoint(level), null);
                    byte kind = com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder.classify(level,
                        spawn.getX(), spawn.getY(), spawn.getZ());
                    if (!com.solegendary.reignofnether.unit.pathfinding.WalkabilityBuilder.isWater(kind) && !audit.escapes(spawn))
                        problems.add(name + ": units spawn at " + audit.rel(spawn) + " and can't leave");
                }
            }
        }
        if (checked < 14)
            problems.add("only " + checked + " level structures were checked (7 labs x L2/L3 expected)");
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }

    /**
     * HUD declutter (design/hud.md): the stats table's Army tab and the spectator observer bar ride on the roster
     * packet. Army value, units, kills, losses, tier and faction powers go through the same ally / observer gate as
     * the economy (an enemy's army never reaches a player still in the match), each line says whether it is the
     * recipient or an ally, and allies share a team index. Unique names, no global counts: safe in parallel.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void stats_table_army_figures_follow_the_ally_gate(GameTestHelper helper) {
        String me = "gametest_stats_me", ally = "gametest_stats_ally", foe = "gametest_stats_foe";
        List<com.solegendary.reignofnether.player.PlayerPanelServerEvents.Row> rows = List.of(
            new com.solegendary.reignofnether.player.PlayerPanelServerEvents.Row(foe, "", true, 9, 90, 0.5f, 0, 0, 0, 0,
                1500, 40, 7, 3, 3, 2),
            new com.solegendary.reignofnether.player.PlayerPanelServerEvents.Row(ally, "", true, 3, 30, 1f, 0, 0, 0, 0,
                800, 22, 4, 5, 2, 1),
            new com.solegendary.reignofnether.player.PlayerPanelServerEvents.Row(me, "", true, 2, 20, 0.25f, 0, 0, 0, 0,
                300, 12, 1, 2, 1, 0));
        com.solegendary.reignofnether.alliance.AlliancesServerEvents.addAlliance(me, ally);
        var meSide = com.solegendary.reignofnether.player.RTSPlayer.getNewBot(me, Factions.VILLAGERS);
        var rtsPlayers = com.solegendary.reignofnether.player.PlayerServerEvents.rtsPlayers;
        synchronized (rtsPlayers) {
            rtsPlayers.add(meSide);
        }
        try {
            List<String> problems = new ArrayList<>();
            var list = com.solegendary.reignofnether.player.PlayerPanelServerEvents.entriesFor(me, rows);
            var watched = com.solegendary.reignofnether.player.PlayerPanelServerEvents.entriesFor("gametest_stats_watcher", rows);
            var mine = list.get(0);
            var allyE = list.get(1);
            var foeE = list.get(2);
            if (!mine.isSelf() || mine.isAlly() || !allyE.isAlly() || allyE.isSelf() || foeE.isAlly() || foeE.isSelf())
                problems.add("relation flags wrong: self " + mine.isSelf() + " ally " + allyE.isAlly() + " foe " + foeE.isAlly());
            if (mine.armyValue() != 300 || mine.units() != 12 || allyE.kills() != 4 || allyE.losses() != 5
                    || allyE.tier() != 2 || allyE.powersReady() != 1)
                problems.add("own / allied army figures missing: " + mine + " / " + allyE);
            if (foeE.armyValue() != 0 || foeE.units() != 0 || foeE.kills() != 0 || foeE.losses() != 0
                    || foeE.tier() != 0 || foeE.powersReady() != 0)
                problems.add("the enemy line leaked army figures: " + foeE);
            if (mine.team() != allyE.team() || mine.team() == foeE.team())
                problems.add("teams: me " + mine.team() + " ally " + allyE.team() + " foe " + foeE.team());
            var watchedFoe = watched.stream().filter(e -> e.name().equals(foe)).findFirst().orElse(null);
            if (watchedFoe == null || watchedFoe.armyValue() != 1500 || watchedFoe.powersReady() != 2
                    || watchedFoe.isSelf() || watchedFoe.isAlly())
                problems.add("an observer should see every side's army: " + watchedFoe);
            // the kill feed's tally behind the Army tab's kills / losses
            String k = "gametest_stats_killer", v = "gametest_stats_victim";
            com.solegendary.reignofnether.hud.KillFeedServerEvents.count(k, v);
            com.solegendary.reignofnether.hud.KillFeedServerEvents.count(k, v);
            if (com.solegendary.reignofnether.hud.KillFeedServerEvents.kills(k) < 2
                    || com.solegendary.reignofnether.hud.KillFeedServerEvents.losses(v) < 2
                    || com.solegendary.reignofnether.hud.KillFeedServerEvents.losses(k) != 0)
                problems.add("kill tally: kills " + com.solegendary.reignofnether.hud.KillFeedServerEvents.kills(k)
                    + " losses " + com.solegendary.reignofnether.hud.KillFeedServerEvents.losses(v));
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        } finally {
            synchronized (rtsPlayers) {
                rtsPlayers.remove(meSide);
            }
            com.solegendary.reignofnether.alliance.AlliancesServerEvents.removeAlliance(me, ally);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // lively buildings (design/lively_buildings.md): moving parts, biome-aware palettes, the Geothermal Plant
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Every energy building moves: a mill with a rotor is in SAIL_HUBS (every_sail_hub_sits_inside_its_mill), every
     * other one is in NO_SAILS and declares a WindGenerator.Motion that sits on its anchor in the structure - a WHEEL
     * on a horizontal axle with open air outside it and room above the ground, a BELL in an empty cell under a solid
     * beam with nothing beside it to swing into, PETALS on a leaf block, VANES on an upright hub log over an empty ring.
     * The offline twin is tools/check_sail_hubs.py.
     */
    @GameTest(template = ARENA)
    public static void every_moving_part_sits_on_its_anchor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        int motions = 0;
        var axisProp = net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS;
        for (Building building : ReignOfNetherRegistries.BUILDING) {
            if (!(building instanceof com.solegendary.reignofnether.building.buildings.shared.WindGenerator mill))
                continue;
            boolean sails = com.solegendary.reignofnether.building.buildings.shared.WindGenerator.SAIL_HUBS.containsKey(mill.variant);
            boolean noSails = com.solegendary.reignofnether.building.buildings.shared.WindGenerator.NO_SAILS.contains(mill.variant);
            if (sails == noSails)
                problems.add(building.structureName + ": must be in exactly one of SAIL_HUBS and NO_SAILS");
            var m = mill.getMotion();
            if (noSails && m == null)
                problems.add(building.structureName + ": flies no sails and has no moving part");
            if (m == null)
                continue;
            motions++;
            java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> solid = new java.util.HashMap<>();
            for (var bb : building.getRelativeBlockData(level))
                if (!bb.getBlockState().isAir())
                    solid.put(bb.getBlockPos(), bb.getBlockState());
            BlockPos p = m.pos();
            var st = solid.get(p);
            String at = building.structureName + " " + m.kind() + " at " + p.toShortString();
            switch (m.kind()) {
                case WHEEL -> {
                    if (st == null || !st.hasProperty(axisProp) || st.getValue(axisProp) != m.outward().getAxis())
                        problems.add(at + ": not a horizontal axle on the " + m.outward().getAxis() + " axis (" + st + ")");
                    if (solid.containsKey(p.relative(m.outward())))
                        problems.add(at + ": the wheel would cut into the building");
                    if (m.size() > p.getY() + 0.45f)
                        problems.add(at + ": a " + m.size() + "-block wheel digs into the ground");
                }
                case BELL -> {
                    if (st != null)
                        problems.add(at + ": the bell's cell holds " + st);
                    if (!solid.containsKey(p.above()))
                        problems.add(at + ": nothing to hang the bell from");
                    for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL)
                        if (solid.containsKey(p.relative(d)))
                            problems.add(at + ": the bell would swing into " + p.relative(d).toShortString());
                }
                case PETALS -> {
                    if (st == null || !st.is(net.minecraft.tags.BlockTags.LEAVES))
                        problems.add(at + ": petals fall from " + st + ", not leaves");
                }
                case VANES -> {
                    if (st == null || !st.hasProperty(axisProp) || st.getValue(axisProp) != net.minecraft.core.Direction.Axis.Y)
                        problems.add(at + ": not an upright hub log (" + st + ")");
                    for (int dx = -1; dx <= 1; dx++)
                        for (int dz = -1; dz <= 1; dz++)
                            if ((dx != 0 || dz != 0) && solid.containsKey(p.offset(dx, 0, dz)))
                                problems.add(at + ": the vanes would sweep through " + p.offset(dx, 0, dz).toShortString());
                }
            }
        }
        if (motions < 5)
            problems.add("only " + motions + " moving parts declared");
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }

    /**
     * The drawn statues (BuildingParts.STATUES, StatueRenderClientEvents - the White Angel capitol's marble angel,
     * lovish Oct 20): a row is found by its structure and by each of its levels ("_l2" .. "_l9") and by nothing else
     * (not "_l1", "_l10", a longer name or another building), so a level-up never loses the statue; its scale is
     * positive and its culling box reaches past its wings; the wings open level by level, half open on L1 and spread
     * on L6, ease without a jump from the level before after a level-up, and their fold (with the slow breath) stays
     * inside 0 .. WING_FOLD_MAX. The structure side (the anchor is air on the plinth at every level) is checked offline
     * by tools/check_parts.py, since the capitol's structures are not in the game yet.
     */
    @GameTest(template = ARENA)
    public static void statue_rows_follow_levels_and_wings_unfold(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (com.solegendary.reignofnether.building.BuildingParts.Statue s
                : com.solegendary.reignofnether.building.BuildingParts.STATUES) {
            String base = s.structure();
            if (com.solegendary.reignofnether.building.BuildingParts.statueFor(base) != s)
                problems.add(base + ": not found by its own name");
            for (int lv = 2; lv <= 9; lv++)
                if (com.solegendary.reignofnether.building.BuildingParts.statueFor(base + "_l" + lv) != s)
                    problems.add(base + "_l" + lv + ": the level loses the statue");
            for (String other : new String[] { base + "_l1", base + "_l10", base + "_lx", base + "x", base + "_l2x" })
                if (com.solegendary.reignofnether.building.BuildingParts.statueFor(other) != null)
                    problems.add(other + ": matched a statue it is not");
            if (!(s.scale() > 0f) || s.height() < 8f)
                problems.add(base + ": scale " + s.scale() + ", height " + s.height());
            if (s.halfSpan() <= 0f || s.anchor().getY() <= 0)
                problems.add(base + ": half span " + s.halfSpan() + ", anchor " + s.anchor().toShortString());
        }
        if (com.solegendary.reignofnether.building.BuildingParts.statueFor(null) != null
                || com.solegendary.reignofnether.building.BuildingParts.statueFor("barracks_l2") != null)
            problems.add("a statue for a building without one");
        float max = com.solegendary.reignofnether.building.BuildingParts.WING_FOLD_MAX;
        float prevOpen = -1f;
        for (int lv = 1; lv <= 6; lv++) {
            float open = com.solegendary.reignofnether.building.BuildingParts.wingOpenAt(lv);
            if (open <= prevOpen)
                problems.add("the wings do not open further at L" + lv + " (" + open + " after " + prevOpen + ")");
            prevOpen = open;
            float settled = com.solegendary.reignofnether.building.BuildingParts.statueWingFold(lv, Float.MAX_VALUE, 0f);
            float justUp = com.solegendary.reignofnether.building.BuildingParts.statueWingFold(lv, 0f, 0f);
            float before = com.solegendary.reignofnether.building.BuildingParts.statueWingFold(lv - 1, Float.MAX_VALUE, 0f);
            if (lv > 1 && Math.abs(justUp - before) > 1e-3f)
                problems.add("L" + lv + ": the wings jump at the level-up (" + before + " -> " + justUp + ")");
            float last = justUp;
            for (int t = 0; t <= com.solegendary.reignofnether.building.BuildingParts.WING_UNFOLD_TICKS; t += 10) {
                float f = com.solegendary.reignofnether.building.BuildingParts.statueWingFold(lv, t, 0f);
                if (f > last + 1e-3f)
                    problems.add("L" + lv + ": the wings fold back while unfolding at tick " + t);
                last = f;
            }
            for (int t = 0; t < com.solegendary.reignofnether.building.BuildingParts.WING_BREATH_TICKS; t += 25) {
                float f = com.solegendary.reignofnether.building.BuildingParts.statueWingFold(lv, Float.MAX_VALUE, t);
                if (f < 0f || f > max || Math.abs(f - settled) > 2.5f)
                    problems.add("L" + lv + ": the breath leaves the fold range at tick " + t + " (" + f + ")");
            }
        }
        if (Math.abs(com.solegendary.reignofnether.building.BuildingParts.wingOpenAt(6) - 1f) > 1e-5f)
            problems.add("the wings are not fully spread at L6");
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }

    /**
     * The extractor rigs' drawn parts (design/moving_buildings.md slice 1, PartRenderClientEvents) keep their room in
     * every structure BuildingParts declares, so a structure edit cannot bury one: the sheave wheel's disc (every cell
     * within its clear radius of the hub, in the wheel's plane) is air and the wheel drawn fits inside it; the axle
     * block it turns on stands beside the hub; the skip's shaft and the chain's run above it to the hub are air; a
     * drum stands with its rope's column air up to the hub; the pennant pole stands with the pennant's layer round it
     * air (the 5x5 round a rod's tip, or over a pole's top face). It also pins the renderer's anchor maths to
     * the game's own structure rotation (BuildingUtils.getAbsoluteBlockData) for all four rotations, the rows to the
     * structure names the Gravebound extractor really carries, and the hoist's stroke (parks at the foot,
     * reaches the top, tips only there, comes down empty, never jumps). The offline twin is tools/check_parts.py (the
     * Java table equals gen_structures.py's GRAVE_RIG_ANCHORS).
     */
    @GameTest(template = ARENA)
    public static void every_extractor_rig_part_has_room_to_move(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        BlockPos origin = new BlockPos(1000, 64, -1000);   // maths only: nothing is placed there
        for (com.solegendary.reignofnether.building.BuildingParts.Rig rig
                : com.solegendary.reignofnether.building.BuildingParts.RIGS) {
            String at = rig.structure() + " rig";
            var rel = BuildingBlockData.getBuildingBlocksFromNbt(rig.structure(), level);
            if (rel == null || rel.isEmpty()) {
                problems.add(at + ": no structure " + rig.structure());
                continue;
            }
            java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> solid = new java.util.HashMap<>();
            for (var bb : rel)
                if (!bb.getBlockState().isAir())
                    solid.put(bb.getBlockPos(), bb.getBlockState());
            java.util.Set<BlockPos> clear = new java.util.LinkedHashSet<>();
            // the wheel's disc: in the hub's layer across the axis, every cell whose nearest point is inside the radius
            boolean alongX = rig.axis() == net.minecraft.core.Direction.Axis.X;
            float hu = alongX ? rig.hubZ() : rig.hubX(), hv = rig.hubY(), rr = rig.clearRadius();
            int layer = net.minecraft.util.Mth.floor(alongX ? rig.hubX() : rig.hubZ());
            for (int u = net.minecraft.util.Mth.floor(hu - rr) - 1; u <= net.minecraft.util.Mth.floor(hu + rr) + 1; u++)
                for (int v = net.minecraft.util.Mth.floor(hv - rr) - 1; v <= net.minecraft.util.Mth.floor(hv + rr) + 1; v++) {
                    float du = Math.max(Math.max(u - hu, 0f), hu - (u + 1));
                    float dv = Math.max(Math.max(v - hv, 0f), hv - (v + 1));
                    if (du * du + dv * dv < rr * rr)
                        clear.add(alongX ? new BlockPos(layer, v, u) : new BlockPos(u, v, layer));
                }
            if (rig.wheelRadius() + com.solegendary.reignofnether.building.BuildingParts.RIM_OUTSET > rr)
                problems.add(at + ": the wheel drawn (radius " + rig.wheelRadius() + ") is wider than its clear disc (" + rr + ")");
            BlockPos hubCell = new BlockPos(net.minecraft.util.Mth.floor(rig.hubX()), net.minecraft.util.Mth.floor(rig.hubY()),
                net.minecraft.util.Mth.floor(rig.hubZ()));
            BlockPos toAxle = rig.axle().subtract(hubCell);
            boolean beside = toAxle.getY() == 0 && (alongX ? Math.abs(toAxle.getX()) == 1 && toAxle.getZ() == 0
                : toAxle.getX() == 0 && Math.abs(toAxle.getZ()) == 1);
            if (!beside)
                problems.add(at + ": the axle " + rig.axle().toShortString() + " is not beside the hub along the " + rig.axis() + " axis");
            if (!solid.containsKey(rig.axle()))
                problems.add(at + ": no axle block at " + rig.axle().toShortString() + " for the sheave wheel");
            // the skip's shaft, and the chain's run above it up to the hub
            BlockPos b = rig.skipBottom(), t = rig.skipTop();
            if (b.getX() != t.getX() || b.getZ() != t.getZ() || t.getY() <= b.getY() || t.getY() >= hubCell.getY())
                problems.add(at + ": the skip's shaft " + b.toShortString() + " - " + t.toShortString() + " is not a column under the hub");
            for (int y = b.getY(); y <= hubCell.getY(); y++)
                clear.add(new BlockPos(b.getX(), y, b.getZ()));
            // the winding drum and its rope's back fall
            if (rig.drum() != null) {
                if (!solid.containsKey(rig.drum()))
                    problems.add(at + ": no winding drum at " + rig.drum().toShortString());
                for (int y = rig.drum().getY() + 1; y <= hubCell.getY(); y++)
                    clear.add(new BlockPos(rig.drum().getX(), y, rig.drum().getZ()));
            }
            // the pennant (it streams with the world's wind, any way round, up to 1.7 blocks out): the 5x5 layer round a
            // rod's tip, or over a pole's top face
            BlockPos pole = rig.pole();
            if (!solid.containsKey(pole))
                problems.add(at + ": no pennant pole at " + pole.toShortString());
            int flagY = rig.flagAbove() ? pole.getY() + 1 : pole.getY();
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    if (rig.flagAbove() || dx != 0 || dz != 0)
                        clear.add(new BlockPos(pole.getX() + dx, flagY, pole.getZ() + dz));
            for (BlockPos c : clear)
                if (solid.containsKey(c))
                    problems.add(at + ": " + c.toShortString() + " must stay air for the moving parts, holds " + solid.get(c));
            // the renderer's rotation maths against the game's own (cells, and a point staying inside its cell)
            int axleIdx = -1, poleIdx = -1;
            for (int i = 0; i < rel.size(); i++) {
                if (rel.get(i).getBlockPos().equals(rig.axle()))
                    axleIdx = i;
                if (rel.get(i).getBlockPos().equals(pole))
                    poleIdx = i;
            }
            if (axleIdx < 0 || poleIdx < 0)
                continue;
            for (Rotation rot : Rotation.values()) {
                var abs = com.solegendary.reignofnether.building.BuildingUtils.getAbsoluteBlockData(rel, level, origin, rot);
                for (int idx : new int[] { axleIdx, poleIdx }) {
                    BlockPos r0 = rel.get(idx).getBlockPos();
                    BlockPos mine = new BlockPos(
                        origin.getX() + com.solegendary.reignofnether.building.BuildingParts.cellX(rot, r0.getX(), r0.getZ()),
                        origin.getY() + 1 + r0.getY(),
                        origin.getZ() + com.solegendary.reignofnether.building.BuildingParts.cellZ(rot, r0.getX(), r0.getZ()));
                    if (!mine.equals(abs.get(idx).getBlockPos()))
                        problems.add(at + " " + rot + ": " + r0.toShortString() + " maps to " + mine.toShortString()
                            + ", the game puts it at " + abs.get(idx).getBlockPos().toShortString());
                }
                double px = com.solegendary.reignofnether.building.BuildingParts.pointX(rot, rig.hubX(), rig.hubZ());
                double pz = com.solegendary.reignofnether.building.BuildingParts.pointZ(rot, rig.hubX(), rig.hubZ());
                if (Math.floor(px) != com.solegendary.reignofnether.building.BuildingParts.cellX(rot, hubCell.getX(), hubCell.getZ())
                        || Math.floor(pz) != com.solegendary.reignofnether.building.BuildingParts.cellZ(rot, hubCell.getX(), hubCell.getZ()))
                    problems.add(at + " " + rot + ": the hub point leaves its cell (" + px + ", " + pz + ")");
            }
            if (rig.strokeTicks() < 30 || rig.strokeTicks() > 50)
                problems.add(at + ": a stroke of " + rig.strokeTicks() + " ticks (about 2 s wanted)");
        }
        // the rows are the structures the Gravebound extractor really wears, tier 1 and tier 2 (the Sunforged one is the
        // Angel Fountain since Oct 19: no machinery, no row)
        for (var mex : List.of(Buildings.METAL_EXTRACTOR_MONSTERS))
            for (int tier = 0; tier <= 1; tier++)
                if (com.solegendary.reignofnether.building.BuildingParts.rigFor(mex.getUpgradedStructureName(tier)) == null)
                    problems.add(mex.getUpgradedStructureName(tier) + ": no rig declared");
        // the hoist's stroke
        float prev = 0f;
        boolean top = false;
        for (int i = 0; i < 1000 && problems.size() < 20; i++) {
            float f = i / 1000f;
            float lift = com.solegendary.reignofnether.building.BuildingParts.skipLift(f);
            float tip = com.solegendary.reignofnether.building.BuildingParts.skipTip(f);
            float fill = com.solegendary.reignofnether.building.BuildingParts.loadFill(f);
            if (lift < 0f || lift > 1f || tip < 0f || tip > 1f || fill < 0f || fill > 1f)
                problems.add("stroke out of range at " + f + ": lift " + lift + ", tip " + tip + ", load " + fill);
            if (tip > 0f && lift < 1f)
                problems.add("the skip tips below the top at " + f);
            if (Math.abs(lift - prev) > 0.02f)
                problems.add("the skip jumps at " + f + " (" + prev + " -> " + lift + ")");
            if (fill > 0f && f >= com.solegendary.reignofnether.building.BuildingParts.TIP_END)
                problems.add("the skip comes down loaded at " + f);
            top |= lift >= 1f;
            prev = lift;
        }
        if (!top || com.solegendary.reignofnether.building.BuildingParts.skipLift(0.9999f) != 0f)
            problems.add("the skip does not run foot to top and back to the foot in a stroke");
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }

    /**
     * lovish's "villages that change with the biome", slice 1 (BiomePalette): every faction's energy building and the
     * Geothermal Plant, placed in a snowy biome, take snow caps where they have bare tops (the Verdant spirit tree's
     * crown at least) - and keep their footprint, block count and every block's air/solid/full-cube shape, and stay
     * pocket-free by structures_have_no_unit_pockets' rules. Each is placed in its own chunk, 15000 blocks south of the
     * grid, whose biomes the test fills with snowy plains first. The chunks are ADJACENT (they share their generation
     * neighbourhood): chunks spread apart cost enough worldgen to slow the 8v8 stress benchmark running beside this.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void snowy_biome_caps_buildings_without_pockets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y0 = 200, margin = 3;
        int chunkX = base.getX() >> 4, chunkZ = (base.getZ() + 15000) >> 4;
        var snowy = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
            .getHolderOrThrow(net.minecraft.world.level.biome.Biomes.SNOWY_PLAINS);
        List<Building> sample = List.of(Buildings.WIND_GENERATOR_VILLAGERS, Buildings.WIND_GENERATOR_MONSTERS,
            Buildings.WIND_GENERATOR_PIGLINS, Buildings.WIND_GENERATOR_VERDANT, Buildings.WIND_GENERATOR_TIDE,
            Buildings.WIND_GENERATOR_STORM, Buildings.WIND_GENERATOR_BRASS, Buildings.GEOTHERMAL_PLANT_VILLAGERS);
        List<String> problems = new ArrayList<>();
        int verdantSnow = 0, totalSnow = 0;
        for (int i = 0; i < sample.size(); i++) {
            Building building = sample.get(i);
            int cx = chunkX + i;
            net.minecraft.world.level.chunk.LevelChunk chunk = GameTestChunks.loadNowOrFail(level, cx, chunkZ);   // bounded
            chunk.fillBiomesFromNoise((x, y, z, sampler) -> snowy, level.getChunkSource().randomState().sampler());
            chunk.setUnsaved(true);
            BlockPos origin = new BlockPos((cx << 4) + 5, y0, (chunkZ << 4) + 5);
            if (com.solegendary.reignofnether.building.BiomePalette.familyAt(level, origin.above())
                    != com.solegendary.reignofnether.building.BiomePalette.Family.SNOWY) {
                problems.add(building.structureName + ": the filled chunk doesn't read as snowy");
                continue;
            }
            var plain = com.solegendary.reignofnether.building.BuildingUtils.getAbsoluteBlockData(
                building.getRelativeBlockData(level), level, origin, Rotation.NONE);
            var bp = com.solegendary.reignofnether.building.BuildingUtils.getNewBuildingPlacement(building, level,
                origin, Rotation.NONE, "gametest_snowy", false);
            if (bp == null || bp.getBlocks().size() != plain.size()) {
                problems.add(building.structureName + ": the snowy placement has " + (bp == null ? "no" : bp.getBlocks().size())
                    + " blocks, the plain one " + plain.size());
                continue;
            }
            int snow = 0;
            for (int k = 0; k < plain.size(); k++) {
                var a = plain.get(k);
                var b = bp.getBlocks().get(k);
                var as = a.getBlockState();
                var bs = b.getBlockState();
                if (!a.getBlockPos().equals(b.getBlockPos()) || as.isAir() != bs.isAir()
                        || as.isCollisionShapeFullBlock(level, a.getBlockPos()) != bs.isCollisionShapeFullBlock(level, b.getBlockPos())
                        || as.getCollisionShape(level, a.getBlockPos()).isEmpty() != bs.getCollisionShape(level, b.getBlockPos()).isEmpty()) {
                    problems.add(building.structureName + ": " + as + " became " + bs + " - a swap must keep the shape");
                    break;
                }
                if (bs.is(Blocks.SNOW_BLOCK) && !as.is(Blocks.SNOW_BLOCK))
                    snow++;
            }
            totalSnow += snow;
            if (building == Buildings.WIND_GENERATOR_VERDANT)
                verdantSnow = snow;
            // stamp it on a stone pad and run the pocket rules (as structures_have_no_unit_pockets does)
            BlockPos min = bp.minCorner, max = bp.maxCorner;
            int gy = min.getY();
            for (int x = min.getX() - margin; x <= max.getX() + margin; x++)
                for (int z = min.getZ() - margin; z <= max.getZ() + margin; z++) {
                    level.setBlock(new BlockPos(x, gy - 1, z), Blocks.STONE.defaultBlockState(), 2);
                    level.setBlock(new BlockPos(x, gy, z), Blocks.STONE.defaultBlockState(), 2);
                    for (int y = gy + 1; y <= max.getY() + 3; y++)
                        level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            for (var b : bp.getBlocks())
                if (b.getBlockPos().getY() > gy || !b.getBlockState().isAir())
                    level.setBlock(b.getBlockPos(), b.getBlockState(), 2);
            PocketAudit audit = new PocketAudit(level, min, max, gy + 1, max.getY() + 2);
            for (BlockPos c : audit.entered())
                if (!audit.escapes(c)) {
                    problems.add(building.structureName + " (snowy): a unit can walk into " + audit.rel(c) + " but not out");
                    break;
                }
            outer:
            for (int x = min.getX(); x <= max.getX(); x++)
                for (int z = min.getZ(); z <= max.getZ(); z++)
                    for (int y = gy + 1; y <= gy + 2; y++) {
                        BlockPos c = new BlockPos(x, y, z);
                        if (com.solegendary.reignofnether.unit.UnitUnstick.isWalkable(level, c)
                                && !audit.escapes(c) && !audit.doorBeside(c)) {
                            problems.add(building.structureName + " (snowy): ground cell " + audit.rel(c) + " has no way out");
                            break outer;
                        }
                    }
        }
        if (verdantSnow == 0)
            problems.add("the Verdant spirit tree took no snow on its crown in a snowy biome");
        if (totalSnow < 2)
            problems.add("only " + totalSnow + " snow caps across the sample");
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }

    /** A 9x9 stone pad round a 5x5 footprint whose origin is o (footprint x/z in [o, o+4], foundation at o.y + 1),
     *  air above, and the 3x3 under the footprint's middle set to centre (lava, water or stone). */
    private static void geothermalPad(ServerLevel level, BlockPos o, net.minecraft.world.level.block.state.BlockState centre) {
        for (int x = -2; x <= 6; x++)
            for (int z = -2; z <= 6; z++) {
                level.setBlock(o.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(o.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 2);
                for (int y = 1; y <= 10; y++)
                    level.setBlock(o.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        for (int x = 1; x <= 3; x++)
            for (int z = 1; z <= 3; z++)
                level.setBlock(o.offset(x, 0, z), centre, 2);
    }

    /** Where the Geothermal Plant tests build their pads: chunks 9, 10 and 11 of the snowy test's row, one pad per
     *  chunk (a 9x9 pad from x + 2 fits), so all of these tests share one stretch of generated world. */
    private static BlockPos geothermalSite(GameTestHelper helper, int slot) {
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        int chunkX = base.getX() >> 4, chunkZ = (base.getZ() + 15000) >> 4;
        return new BlockPos(((chunkX + 9 + slot) << 4) + 4, 200, (chunkZ << 4) + 4);
    }

    /** The Geothermal Plant is refused on dry ground and over water ("Must be placed over lava"), while a Wind
     *  Generator on the same dry pad is fine (so it's the lava rule refusing it, not the pad). */
    @GameTest(template = ARENA)
    public static void geothermal_plant_is_refused_off_lava(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos dry = geothermalSite(helper, 0);
        BlockPos wet = geothermalSite(helper, 1);
        geothermalPad(level, dry, Blocks.STONE.defaultBlockState());
        geothermalPad(level, wet, Blocks.WATER.defaultBlockState());
        List<String> problems = new ArrayList<>();
        String err = BuildingValidators.getPlacementValidityError(level, Buildings.GEOTHERMAL_PLANT_VILLAGERS, dry,
            "gametest_geo", Rotation.NONE, false, true, true);
        if (!com.solegendary.reignofnether.building.buildings.shared.GeothermalPlant.MUST_BE_LAVA.equals(err))
            problems.add("on dry ground: " + err);
        err = BuildingValidators.getPlacementValidityError(level, Buildings.GEOTHERMAL_PLANT_VILLAGERS, wet,
            "gametest_geo", Rotation.NONE, false, true, true);
        if (!com.solegendary.reignofnether.building.buildings.shared.GeothermalPlant.MUST_BE_LAVA.equals(err))
            problems.add("over water: " + err);
        err = BuildingValidators.getPlacementValidityError(level, Buildings.WIND_GENERATOR_VILLAGERS, dry.offset(1, 0, 1),
            "gametest_geo", Rotation.NONE, false, true, true);
        if (err != null)
            problems.add("a Wind Generator on the dry pad was refused too (" + err + ")");
        if (!problems.isEmpty())
            helper.fail("Geothermal Plant: " + String.join("; ", problems));
        else
            helper.succeed();
    }

    /** The Geothermal Plant is accepted over a lava pool (every faction's, every rotation), and only a T2 constructor
     *  may raise it (T2Workers.needsT2Builder: refused with no T2 builder among the builders). */
    @GameTest(template = ARENA)
    public static void geothermal_plant_is_accepted_over_lava(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos o = geothermalSite(helper, 2);
        geothermalPad(level, o, Blocks.LAVA.defaultBlockState());
        List<String> problems = new ArrayList<>();
        int plants = 0;
        for (Building b : ReignOfNetherRegistries.BUILDING) {
            if (!(b instanceof com.solegendary.reignofnether.building.buildings.shared.GeothermalPlant))
                continue;
            plants++;
            String err = BuildingValidators.getPlacementValidityError(level, b, o, "gametest_geo", Rotation.NONE,
                false, true, true);
            if (err != null)
                problems.add(ReignOfNetherRegistries.BUILDING.getKey(b) + " over lava: " + err);
            if (!com.solegendary.reignofnether.unit.T2Workers.needsT2Builder(b)
                    || com.solegendary.reignofnether.unit.T2Workers.buildersMayPlace(level, b, new int[0]))
                problems.add(ReignOfNetherRegistries.BUILDING.getKey(b) + " may be placed without a T2 constructor");
        }
        // a quarter turn about the origin swings the 5x5 to x in [origin - 4, origin]: from o + 4 it covers the same pad
        BlockPos turned = o.offset(4, 0, 0);
        String err = BuildingValidators.getPlacementValidityError(level, Buildings.GEOTHERMAL_PLANT_TIDE, turned,
            "gametest_geo", Rotation.CLOCKWISE_90, false, true, true);
        if (err != null)
            problems.add("rotated over lava: " + err);
        if (plants < 7)
            problems.add("only " + plants + " Geothermal Plants registered (one per faction)");
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }

    // ---------------------------------------------------------------------------------------- pre-made maps

    /**
     * design/maps.md: every pre-made map is fair for every start slot, for every player count - each slot's patches
     * (those in its wedge) are worth the same within 5%, every slot is the same distance from the centre, stands at
     * the same terrain height, lies inside its team's start box, and no two patches crowd each other's extractor.
     */
    @GameTest(template = ARENA)
    public static void premade_maps_are_fair_for_every_slot(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        for (var preset : com.solegendary.reignofnether.maps.MapPreset.values()) {
            if (!preset.handmade())   // a generated map is fair by measure, not by copy (MapGenGameTests)
                continue;
            for (int n : new int[] { 2, 3, 4, 6, 8, 16 }) {
                int[] teams = new int[n];
                for (int i = 0; i < n; i++)
                    teams[i] = i < (n + 1) / 2 ? 0 : 1;
                var layout = com.solegendary.reignofnether.maps.MapLayout.build(preset, teams);
                String tag = preset + " n=" + n + ": ";
                float min = Float.MAX_VALUE, max = 0;
                for (int s = 0; s < n; s++) {
                    float v = 0;
                    for (var m : layout.mexes)
                        if (layout.slotAt(m.dx(), m.dz()) == s)
                            v += m.value();
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                }
                if (min <= 0 || max > min * 1.05f)
                    problems.add(tag + "slot mex value " + min + ".." + max);
                double dmin = Double.MAX_VALUE, dmax = 0;
                var sample = new com.solegendary.reignofnether.maps.MapLayout.Sample();
                Set<Integer> heights = new HashSet<>();
                for (var s : layout.slots) {
                    double d = Math.hypot(s.dx(), s.dz());
                    dmin = Math.min(dmin, d);
                    dmax = Math.max(dmax, d);
                    layout.sample(s.dx(), s.dz(), sample);
                    heights.add(sample.height);
                    if (!layout.boxOf(s.team()).contains(s.dx(), s.dz()))
                        problems.add(tag + "slot " + s.index() + " outside its team's box");
                }
                if (dmax - dmin > 2)
                    problems.add(tag + "distance to centre " + dmin + ".." + dmax);
                if (heights.size() != 1)
                    problems.add(tag + "slot heights " + heights);
                for (int i = 0; i < layout.mexes.size(); i++)
                    for (int j = i + 1; j < layout.mexes.size(); j++) {
                        var a = layout.mexes.get(i);
                        var b = layout.mexes.get(j);
                        if (Math.abs(a.dx() - b.dx()) <= 5 && Math.abs(a.dz() - b.dz()) <= 5)
                            problems.add(tag + "patches crowd at " + a + " / " + b);
                    }
                for (var m : layout.mexes) {
                    layout.sample(m.dx(), m.dz(), sample);
                    var surf = sample.surface;
                    if (surf == com.solegendary.reignofnether.maps.MapLayout.Surface.WATER
                            || surf == com.solegendary.reignofnether.maps.MapLayout.Surface.LAVA
                            || surf == com.solegendary.reignofnether.maps.MapLayout.Surface.VOID)
                        problems.add(tag + "patch on " + surf + " at " + m);
                    // the whole 7x7 pad MetalPatches.stamp levels, not only the centre column (maps spec M1: 20 of
                    // Highlands' 96 patches at 8 players had an 8-block drop inside the pad and this test passed)
                    String fault = MapMetalGameTests.padFault(layout, m.dx(), m.dz(), sample);
                    if (fault != null)
                        problems.add(tag + "patch " + m + " has " + fault + " in its pad");
                }
            }
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(problems.size() + " problem(s): " + String.join("; ", problems.subList(0, Math.min(8, problems.size()))));
    }

    /**
     * Metal over the whole map (lovish, Oct 16: "Metal extractors ... should be all over the map I feel like I have
     * alot of the map that is empty"): on every pre-made map, for 2, 4 and 8 players, the mean distance from open ground
     * (no void, lava or water) to its nearest patch stays under 50 blocks. Before MapLayout.spread it was 54-95 on the
     * open maps (Verdant Valley and Nether Rift 1v1: ~95); with it 25-49.
     */
    @GameTest(template = ARENA)
    public static void premade_maps_spread_metal_over_the_open_ground(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        var sample = new com.solegendary.reignofnether.maps.MapLayout.Sample();
        for (var preset : com.solegendary.reignofnether.maps.MapPreset.values()) {
            if (!preset.handmade())   // the generated map's own fill rules are tested in MapGenGameTests
                continue;
            for (int n : new int[] { 2, 4, 8 }) {
                int[] teams = new int[n];
                for (int i = 0; i < n; i++)
                    teams[i] = i < (n + 1) / 2 ? 0 : 1;
                var layout = com.solegendary.reignofnether.maps.MapLayout.build(preset, teams);
                double sum = 0;
                int land = 0;
                int r = layout.radius - 8;
                for (int x = -r; x <= r; x += 4)
                    for (int z = -r; z <= r; z += 4) {
                        if (x * x + z * z > r * r)
                            continue;
                        layout.sample(x, z, sample);
                        var su = sample.surface;
                        if (su == com.solegendary.reignofnether.maps.MapLayout.Surface.VOID
                                || su == com.solegendary.reignofnether.maps.MapLayout.Surface.LAVA
                                || su == com.solegendary.reignofnether.maps.MapLayout.Surface.WATER)
                            continue;
                        double d = Double.MAX_VALUE;
                        for (var m : layout.mexes)
                            d = Math.min(d, Math.hypot(m.dx() - x, m.dz() - z));
                        sum += d;
                        land++;
                    }
                double mean = land == 0 ? 0 : sum / land;
                if (mean > 50)
                    problems.add(preset + " n=" + n + ": open ground " + Math.round(mean) + " blocks from metal on average");
            }
        }
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    /**
     * design/maps.md: every map spawns its own capture kinds, and they do what they say - a held Lava Vent pays
     * energy, a held Ancient Forge discounts research (not units), a held Oasis heals its owner's units, and Bells
     * and Watchtowers give vision.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void map_capture_sites_spawn_with_their_effect(GameTestHelper helper) {
        java.util.Map<com.solegendary.reignofnether.maps.MapPreset, Set<com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind>> expected = java.util.Map.of(
            com.solegendary.reignofnether.maps.MapPreset.ARCHIPELAGO, Set.of(com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.BELL, com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.WATCHTOWER),
            com.solegendary.reignofnether.maps.MapPreset.HIGHLANDS, Set.of(com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.WATCHTOWER, com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.FORGE),
            com.solegendary.reignofnether.maps.MapPreset.NETHER_RIFT, Set.of(com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.VENT, com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.MINE),
            com.solegendary.reignofnether.maps.MapPreset.END_SPIRES, Set.of(com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.WATCHTOWER, com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.SHRINE),
            com.solegendary.reignofnether.maps.MapPreset.VERDANT_VALLEY, Set.of(com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.CAMP, com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.SHRINE),
            com.solegendary.reignofnether.maps.MapPreset.DESERT_MESA, Set.of(com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.OASIS, com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.MINE));
        List<String> problems = new ArrayList<>();
        for (var e : expected.entrySet()) {
            var layout = com.solegendary.reignofnether.maps.MapLayout.build(e.getKey(), new int[] { 0, 0, 1, 1 });
            Set<com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind> kinds = new HashSet<>();
            for (var s : layout.sites)
                kinds.add(s.kind());
            if (!kinds.equals(e.getValue()))
                problems.add(e.getKey() + " sites " + kinds + " (expected " + e.getValue() + ")");
            if (layout.sites.size() != 4 + 1)
                problems.add(e.getKey() + " has " + layout.sites.size() + " sites for 4 players (one per boundary + centre)");
        }

        // the effects, on three real sites high above this test's arena
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        int y = 224;
        String owner = "gametest_mapsite_owner";
        int[][] offs = { { -8, 0 }, { 0, 0 }, { 8, 0 } };
        for (int[] o : offs)
            for (int dx = -4; dx <= 4; dx++)
                for (int dz = -4; dz <= 4; dz++)
                    level.setBlock(new BlockPos(base.getX() + o[0] + dx, y, base.getZ() + o[1] + dz), Blocks.STONE.defaultBlockState(), 3);
        var vent = com.solegendary.reignofnether.startpos.CapturePointServerEvents.place(level, base.getX() - 8, base.getZ(),
            com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.VENT);
        var forge = com.solegendary.reignofnether.startpos.CapturePointServerEvents.place(level, base.getX(), base.getZ(),
            com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.FORGE);
        var oasis = com.solegendary.reignofnether.startpos.CapturePointServerEvents.place(level, base.getX() + 8, base.getZ(),
            com.solegendary.reignofnether.startpos.CapturePointServerEvents.Kind.OASIS);
        var pool = new com.solegendary.reignofnether.resources.Resources(owner, 0, 0, 0);
        com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.add(pool);
        var unit = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (vent == null || forge == null || oasis == null || unit == null) {
            helper.fail("could not set up the map sites");
            return;
        }
        if (com.solegendary.reignofnether.startpos.CapturePointServerEvents.researchCostMultiplier(owner) != 1f)
            problems.add("research discount before the forge is held");
        for (var site : List.of(vent, forge, oasis))
            site.getPersistentData().putString(com.solegendary.reignofnether.startpos.CapturePointServerEvents.KEY_OWNER, owner);
        unit.moveTo(base.getX() + 10.5, y + 1, base.getZ() + 0.5, 0, 0);
        unit.setOwnerName(owner);
        level.addFreshEntity(unit);
        unit.setHealth(unit.getMaxHealth() * 0.5f);
        float hp0 = unit.getHealth();
        helper.runAfterDelay(3, () -> {
            try {
                for (int i = 0; i < 3; i++)
                    com.solegendary.reignofnether.startpos.CapturePointServerEvents.tick(level, 1f);
                if (pool.getEnergy() <= 0)
                    problems.add("a held Lava Vent paid no energy");
                if (Math.abs(com.solegendary.reignofnether.startpos.CapturePointServerEvents.researchCostMultiplier(owner)
                        - (1f - com.solegendary.reignofnether.startpos.CapturePointServerEvents.FORGE_DISCOUNT)) > 1e-4)
                    problems.add("a held Ancient Forge gave no research discount");
                if (unit.getHealth() <= hp0)
                    problems.add("a held Oasis did not heal (" + hp0 + " -> " + unit.getHealth() + ")");
                if (com.solegendary.reignofnether.startpos.CapturePointServerEvents.sightOf(vent) != 0
                        || com.solegendary.reignofnether.startpos.CapturePointServerEvents.WATCHTOWER_SIGHT <= com.solegendary.reignofnether.startpos.CapturePointServerEvents.BELL_SIGHT)
                    problems.add("vision kinds wrong");
            } finally {
                unit.discard();
                vent.discard();
                forge.discard();
                oasis.discard();
                com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.remove(pool);
            }
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }

    /**
     * design/maps.md section 5: in the start-position phase a click outside your team's box is refused, a click
     * inside is kept (snapped clear of patches), bots are placed at once, and the timeout places everyone left
     * inside their box and starts the match. When every human has placed, the phase ends early.
     */
    @GameTest(template = ARENA)
    public static void start_phase_places_everyone_in_their_box(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // far from every other test's arena, patches and sites: only the box rules are under test here
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        int cx = base.getX() + 40000, cz = base.getZ() + 40000;
        int[] teams = { 0, 0, 1, 1 };
        var layout = com.solegendary.reignofnether.maps.MapLayout.build(com.solegendary.reignofnether.maps.MapPreset.HIGHLANDS, teams);
        List<String> problems = new ArrayList<>();
        int[] done = { 0 };
        List<com.solegendary.reignofnether.maps.StartPhase.Pick> picks = new ArrayList<>(List.of(
            new com.solegendary.reignofnether.maps.StartPhase.Pick("gametest_sp_a", false, 0, 0, 0xFF0000, null),
            new com.solegendary.reignofnether.maps.StartPhase.Pick("gametest_sp_b", false, 0, 1, 0x00FF00, null),
            new com.solegendary.reignofnether.maps.StartPhase.Pick("AI 1", true, 1, 2, 0x0000FF, null),
            new com.solegendary.reignofnether.maps.StartPhase.Pick("gametest_sp_c", false, 1, 3, 0xFFFF00, null)));
        var phase = new com.solegendary.reignofnether.maps.StartPhase(level, cx, cz, layout, picks, 45, ph -> done[0]++);
        if (!picks.get(2).placed)
            problems.add("the bot was not placed at once");
        var enemySlot = layout.slots.get(3);
        if (phase.place("gametest_sp_a", cx + enemySlot.dx(), cz + enemySlot.dz()))
            problems.add("a click in the enemy's box was accepted");
        var mySlot = layout.slots.get(0);
        // ten blocks towards the centre from slot 0: inside team 0's box
        int px = cx + (int) Math.round(mySlot.dx() * 0.93), pz = cz + (int) Math.round(mySlot.dz() * 0.93);
        if (!phase.place("gametest_sp_a", px, pz))
            problems.add("a click inside the own box was refused");
        else if (!phase.inBox(0, picks.get(0).x, picks.get(0).z))
            problems.add("the kept spot left the box");
        for (int t = 0; t < 45 * 20 + 5 && !phase.isFinished(); t++)
            phase.tick();
        if (!phase.isFinished() || done[0] != 1)
            problems.add("the timeout did not end the phase (finished " + phase.isFinished() + ", starts " + done[0] + ")");
        for (var p : picks)
            if (!p.placed || !phase.inBox(p.team, p.x, p.z))
                problems.add(p.name + " ended outside the box at [" + p.x + ", " + p.z + "]");
        if (picks.get(1).x != cx + layout.slots.get(1).dx() || picks.get(1).z != cz + layout.slots.get(1).dz())
            problems.add("an unplaced human was not put on their slot");

        // everyone places and presses Ready: the phase ends a few seconds later, long before the clock (placing
        // alone no longer ends it - tester Joisco's Ready rule, gametest/SpawnReadyGameTests)
        List<com.solegendary.reignofnether.maps.StartPhase.Pick> picks2 = new ArrayList<>(List.of(
            new com.solegendary.reignofnether.maps.StartPhase.Pick("gametest_sp_d", false, 0, 0, 0xFF0000, null),
            new com.solegendary.reignofnether.maps.StartPhase.Pick("AI 2", true, 1, 1, 0x0000FF, null)));
        var layout2 = com.solegendary.reignofnether.maps.MapLayout.build(com.solegendary.reignofnether.maps.MapPreset.DESERT_MESA, new int[] { 0, 1 });
        int[] done2 = { 0 };
        var phase2 = new com.solegendary.reignofnether.maps.StartPhase(level, cx, cz, layout2, picks2, 45, ph -> done2[0]++);
        var s0 = layout2.slots.get(0);
        if (!phase2.place("gametest_sp_d", cx + s0.dx(), cz + s0.dz() + 6))
            problems.add("phase 2: the click was refused");
        for (int t = 0; t < com.solegendary.reignofnether.maps.StartPhase.EARLY_END_TICKS + 2; t++)
            phase2.tick();
        if (phase2.isFinished())
            problems.add("placing without Ready ended the phase");
        if (!phase2.setReady("gametest_sp_d", true))
            problems.add("phase 2: Ready was refused after placing");
        for (int t = 0; t < com.solegendary.reignofnether.maps.StartPhase.EARLY_END_TICKS + 2; t++)
            phase2.tick();
        if (!phase2.isFinished() || done2[0] != 1)
            problems.add("all placed and ready did not end the phase early");
        if (problems.isEmpty())
            helper.succeed();
        else
            helper.fail(String.join("; ", problems));
    }

    // ------------------------------------------------------------------ hero-possession (design/hero_mode.md)

    /** A Vindicator unit for the possession tests, on the pad at (x, z), facing +X (yaw -90). */
    static com.solegendary.reignofnether.unit.units.villagers.VindicatorUnit possessionTestUnit(GameTestHelper helper,
                                                                                                String owner, int x, int z) {
        ServerLevel level = helper.getLevel();
        var u = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (u == null)
            return null;
        BlockPos at = helper.absolutePos(new BlockPos(x, 2, z));
        u.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, -90, 0);
        u.setOwnerName(owner);
        level.addFreshEntity(u);
        return u;
    }

    /**
     * Possession claims a unit (PossessionServer.claim): it is marked possessed and held by the claimant, an RTS
     * order that includes it skips it and becomes the possessor's MOVE objective (divertOrders), forward input walks
     * it (the mixin skips its AI and vanilla travel moves it), and releasing frees it. Unique names: parallel-safe.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void possession_claims_a_unit_and_drives_it(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = "gametest_poss_claim";
        java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(me.getBytes());
        // its own flat strip high above the arena (the pad is cut out of generated terrain: a bump in the walk
        // would read as "possession doesn't move the unit")
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        final int y = 212;
        for (int dx = -7; dx <= 7; dx++)
            for (int dz = -2; dz <= 2; dz++)
                level.setBlock(new BlockPos(base.getX() + dx, y, base.getZ() + dz), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        var unit = com.solegendary.reignofnether.registrars.EntityRegistrar.VINDICATOR_UNIT.get().create(level);
        if (unit == null) {
            helper.fail("could not create a vindicator unit");
            return;
        }
        unit.moveTo(base.getX() - 4.5, y + 1, base.getZ() + 0.5, -90, 0);
        unit.setOwnerName(me);
        level.addFreshEntity(unit);
        Runnable clearStrip = () -> {
            for (int dx = -7; dx <= 7; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    level.setBlock(new BlockPos(base.getX() + dx, y, base.getZ() + dz), Blocks.AIR.defaultBlockState(), 2);
        };
        helper.runAfterDelay(3, () -> {
            String why = com.solegendary.reignofnether.hero.PossessionServer.claim(level, me, uuid, unit.getId(), true);
            if (why != null) {
                unit.discard();
                clearStrip.run();
                helper.fail("the owner could not possess their own unit: " + why);
                return;
            }
            var p = com.solegendary.reignofnether.hero.PossessionServer.of(uuid);
            List<String> problems = new ArrayList<>();
            if (!com.solegendary.reignofnether.hero.PossessionServer.isPossessed(unit) || p == null || p.unitId != unit.getId())
                problems.add("the claim did not stick");
            int other = unit.getId() + 1_000_000;
            int[] left = com.solegendary.reignofnether.hero.PossessionServer.divertOrders(level,
                com.solegendary.reignofnether.unit.UnitAction.MOVE, new int[] { unit.getId(), other },
                unit.blockPosition().offset(5, 0, 0), -1);
            if (left.length != 1 || left[0] != other)
                problems.add("an RTS order still reached the possessed unit (" + left.length + " ids kept)");
            if (p != null && p.objective != com.solegendary.reignofnether.hero.PossessionServer.Objective.MOVE)
                problems.add("the move order did not become a MOVE objective: " + (p == null ? null : p.objective));
            double x0 = unit.getX(), z0 = unit.getZ();
            for (int t = 0; t <= 20; t += 5)
                helper.runAfterDelay(t + 1, () -> com.solegendary.reignofnether.hero.PossessionServer.applyInput(
                    uuid, unit.getId(), 1, 0, -90, 0, false, false));
            helper.runAfterDelay(26, () -> {
                double moved = unit.getX() - x0;
                var held = com.solegendary.reignofnether.hero.PossessionServer.of(uuid);
                if (moved < 0.5)
                    problems.add("forward input did not walk the unit toward +X (moved " + String.format("%.2f", moved)
                        + ", driven " + (held == null ? "released" : held.drivenTicks) + " ticks, speed "
                        + String.format("%.3f", unit.getSpeed()) + ", zza " + unit.zza + ", onGround " + unit.onGround()
                        + ", yRot " + unit.getYRot() + ", dz " + String.format("%.2f", unit.getZ() - z0)
                        + ", motion " + unit.getDeltaMovement() + ", blocked " + unit.horizontalCollision + ")");
                com.solegendary.reignofnether.hero.PossessionServer.release(uuid, null);
                if (com.solegendary.reignofnether.hero.PossessionServer.isPossessed(unit)
                        || com.solegendary.reignofnether.hero.PossessionServer.of(uuid) != null)
                    problems.add("release left the unit possessed");
                unit.discard();
                clearStrip.run();
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", problems));
            });
        });
    }

    /**
     * Possession is refused for an enemy unit, and a Hero-role player (HeroRoles) may take their commander's hero
     * but an ordinary unit only once that commander lends their units (/rts-possession allow).
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void possession_refuses_an_enemy_unit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String me = "gametest_poss_me", foe = "gametest_poss_foe", hero = "gametest_poss_heroplayer";
        java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(me.getBytes());
        var foeUnit = possessionTestUnit(helper, foe, 8, 4);
        var guard = com.solegendary.reignofnether.registrars.EntityRegistrar.ROYAL_GUARD_UNIT.get().create(level);
        if (foeUnit == null || guard == null) {
            helper.fail("could not create the test units");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(8, 2, 12));
        guard.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        guard.setOwnerName(foe);
        level.addFreshEntity(guard);
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            String why = com.solegendary.reignofnether.hero.PossessionServer.claim(level, me, uuid, foeUnit.getId(), true);
            if (why == null) {
                problems.add("an enemy unit was possessed");
                com.solegendary.reignofnether.hero.PossessionServer.release(uuid, null);
            }
            if (com.solegendary.reignofnether.hero.PossessionServer.isPossessed(foeUnit))
                problems.add("the enemy unit is marked possessed after a refused claim");
            com.solegendary.reignofnether.hero.HeroRoles.assign(hero, List.of(foe));
            try {
                if (com.solegendary.reignofnether.hero.PossessionServer.mayPossess(hero, guard) != null)
                    problems.add("a Hero-role player may not take their commander's hero");
                if (com.solegendary.reignofnether.hero.PossessionServer.mayPossess(hero, foeUnit) == null)
                    problems.add("a Hero-role player took an ordinary unit that was not lent");
                com.solegendary.reignofnether.hero.HeroRoles.setLends(foe, true);
                if (com.solegendary.reignofnether.hero.PossessionServer.mayPossess(hero, foeUnit) != null)
                    problems.add("a lent unit could not be possessed by the Hero-role teammate");
                if (com.solegendary.reignofnether.hero.PossessionServer.mayPossess(me, guard) == null)
                    problems.add("an outsider may possess someone else's hero");
            } finally {
                com.solegendary.reignofnether.hero.HeroRoles.setLends(foe, false);
                com.solegendary.reignofnether.hero.HeroRoles.unassign(hero);
                foeUnit.discard();
                guard.discard();
            }
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }

    /** Input, attacks and abilities from anyone but the possessor are dropped; a second claim on a held unit fails. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void possession_drops_input_from_a_non_possessor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_poss_driver", intruder = "gametest_poss_intruder";
        java.util.UUID a = java.util.UUID.nameUUIDFromBytes(owner.getBytes());
        java.util.UUID b = java.util.UUID.nameUUIDFromBytes(intruder.getBytes());
        var unit = possessionTestUnit(helper, owner, 12, 4);
        if (unit == null) {
            helper.fail("could not create a vindicator unit");
            return;
        }
        helper.runAfterDelay(3, () -> {
            List<String> problems = new ArrayList<>();
            String why = com.solegendary.reignofnether.hero.PossessionServer.claim(level, owner, a, unit.getId(), true);
            if (why != null)
                problems.add("the owner's claim was refused: " + why);
            if (com.solegendary.reignofnether.hero.PossessionServer.applyInput(b, unit.getId(), 1, 0, 0, 0, true, false))
                problems.add("movement from a non-possessor was accepted");
            if (com.solegendary.reignofnether.hero.PossessionServer.attack(b, unit.getId(), 0, 0))
                problems.add("an attack from a non-possessor was accepted");
            if (com.solegendary.reignofnether.hero.PossessionServer.ability(b, unit.getId(), 0, 0, 0))
                problems.add("an ability from a non-possessor was accepted");
            if (com.solegendary.reignofnether.hero.PossessionServer.claim(level, intruder, b, unit.getId(), true) == null)
                problems.add("a second player took a unit somebody already holds");
            if (!com.solegendary.reignofnether.hero.PossessionServer.applyInput(a, unit.getId(), 0, 0, 0, 0, false, false))
                problems.add("the possessor's own input was dropped");
            if (com.solegendary.reignofnether.hero.PossessionServer.applyInput(a, unit.getId() + 1, 0, 0, 0, 0, false, false))
                problems.add("input naming another unit than the one held was accepted");
            com.solegendary.reignofnether.hero.PossessionServer.release(a, null);
            unit.discard();
            if (problems.isEmpty())
                helper.succeed();
            else
                helper.fail(String.join("; ", problems));
        });
    }

    /** The possessed unit's death releases its possessor (PossessionServer.onLivingDeath). */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void possession_ends_when_the_unit_dies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String owner = "gametest_poss_doomed";
        java.util.UUID uuid = java.util.UUID.nameUUIDFromBytes(owner.getBytes());
        var unit = possessionTestUnit(helper, owner, 12, 12);
        if (unit == null) {
            helper.fail("could not create a vindicator unit");
            return;
        }
        helper.runAfterDelay(3, () -> {
            String why = com.solegendary.reignofnether.hero.PossessionServer.claim(level, owner, uuid, unit.getId(), true);
            if (why != null) {
                unit.discard();
                helper.fail("claim refused: " + why);
                return;
            }
            unit.kill();
            helper.runAfterDelay(2, () -> {
                boolean still = com.solegendary.reignofnether.hero.PossessionServer.of(uuid) != null
                    || com.solegendary.reignofnether.hero.PossessionServer.isPossessed(unit.getId());
                com.solegendary.reignofnether.hero.PossessionServer.release(uuid, null);
                if (!unit.isRemoved())
                    unit.discard();
                if (still)
                    helper.fail("the possessor still holds a dead unit");
                else
                    helper.succeed();
            });
        });
    }

    /**
     * A Hero-role seat in a lobby match (MultiplayerLobby.startMatch) gets no base: no RTS player, no capitol, not a
     * match participant - and is attached to their team's commander (HeroRoles). One commander base, ~1500 blocks
     * south-west of the test grid (the other lobby test is north-east, the bot soak east); started after the soak's
     * battlefield has formed so it joins as a late base.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void hero_role_seat_gets_no_base(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String nameA = "gametest_hr_cmdr", nameH = "gametest_hr_hero";
        var fakeA = net.minecraftforge.common.util.FakePlayerFactory.get(level,
            new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(nameA.getBytes()), nameA));
        var fakeH = net.minecraftforge.common.util.FakePlayerFactory.get(level,
            new com.mojang.authlib.GameProfile(java.util.UUID.nameUUIDFromBytes(nameH.getBytes()), nameH));
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        helper.runAfterDelay(200, () -> {
            BlockPos centre = null;
            for (int k = 0; k < 6 && centre == null; k++) {
                int cx = origin.getX() - 1500, cz = origin.getZ() + 1500 + 500 * k;
                BlockPos c = new BlockPos(cx, com.solegendary.reignofnether.resources.MetalPatches.solidTop(level, cx, cz), cz);
                boolean dry = true;
                for (var p : com.solegendary.reignofnether.matchstart.MultiplayerLobby.startPositions(level, c, 1, 1, 0))
                    if (!com.solegendary.reignofnether.matchstart.MultiplayerLobby.siteOk(level, (int) Math.floor(p.x), (int) Math.floor(p.z)))
                        dry = false;
                if (dry)
                    centre = c;
            }
            if (centre == null) {
                helper.fail("no dry ground for a lobby base near x=" + (origin.getX() - 1500));
                return;
            }
            var players = com.solegendary.reignofnether.player.PlayerServerEvents.players;
            players.add(fakeA);
            players.add(fakeH);
            var seatA = new com.solegendary.reignofnether.matchstart.MultiplayerLobby.Seat(900101, false);
            seatA.name = nameA;
            seatA.uuid = fakeA.getUUID();
            seatA.faction = 0;
            seatA.team = 0;
            var seatH = new com.solegendary.reignofnether.matchstart.MultiplayerLobby.Seat(900102, false);
            seatH.name = nameH;
            seatH.uuid = fakeH.getUUID();
            seatH.faction = 1;
            seatH.team = 0;
            seatH.heroRole = true;
            var match = com.solegendary.reignofnether.matchstart.MultiplayerLobby.startMatch(level, List.of(seatA, seatH),
                0, 1, 1, centre, new java.util.Random(11));
            List<String> problems = new ArrayList<>();
            if (com.solegendary.reignofnether.player.PlayerServerEvents.isRTSPlayer(nameH))
                problems.add("the Hero-role player became an RTS player");
            if (!com.solegendary.reignofnether.player.PlayerServerEvents.isRTSPlayer(nameA))
                problems.add("the commander did not become an RTS player");
            if (match.participants.contains(nameH) || match.humans.contains(nameH))
                problems.add("the Hero-role player is a match participant: " + match.participants);
            if (!com.solegendary.reignofnether.hero.HeroRoles.isHeroRole(nameH)
                    || !com.solegendary.reignofnether.hero.HeroRoles.commandersOf(nameH).contains(nameA))
                problems.add("the Hero-role player was not attached to their commander: "
                    + com.solegendary.reignofnether.hero.HeroRoles.commandersOf(nameH));
            if (com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList.stream().anyMatch(r -> nameH.equals(r.ownerName)))
                problems.add("the Hero-role player got resources");
            helper.runAfterDelay(10, () -> {
                int heroCapitols = 0, cmdrCapitols = 0;
                for (var b : com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings()) {
                    if (nameH.equals(b.ownerName))
                        heroCapitols++;
                    if (b.isCapitol && nameA.equals(b.ownerName))
                        cmdrCapitols++;
                }
                if (heroCapitols != 0)
                    problems.add("the Hero-role player owns " + heroCapitols + " building(s)");
                if (cmdrCapitols != 1)
                    problems.add("the commander should have one capitol, has " + cmdrCapitols);
                // clean up: the commander leaves through a match of their own, the fakes leave the online list
                com.solegendary.reignofnether.player.PlayerServerEvents.defeat(nameA, "gametest");
                com.solegendary.reignofnether.matchstart.MultiplayerMatch leftover = com.solegendary.reignofnether.matchstart.MultiplayerMatch.of(nameA);
                if (leftover != null)
                    leftover.end();
                match.end();
                com.solegendary.reignofnether.hero.HeroRoles.unassign(nameH);
                players.remove(fakeA);
                players.remove(fakeH);
                if (problems.isEmpty())
                    helper.succeed();
                else
                    helper.fail(String.join("; ", problems));
            });
        });
    }
}
