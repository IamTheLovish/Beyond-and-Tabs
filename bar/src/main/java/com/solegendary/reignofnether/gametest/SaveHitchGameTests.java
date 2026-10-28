package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingSaveData;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.NetherZoneSaveData;
import com.solegendary.reignofnether.building.custombuilding.CustomBuildingSaveData;
import com.solegendary.reignofnether.building.custombuilding.CustomBuildingServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayerSaveData;
import com.solegendary.reignofnether.research.ResearchSaveData;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesSaveData;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.survival.SurvivalSaveData;
import com.solegendary.reignofnether.survival.SurvivalServerEvents;
import com.solegendary.reignofnether.unit.HeroUnitSaveData;
import com.solegendary.reignofnether.unit.TargetResourcesSaveData;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Plan 014, the save hitch (overnight review Oct 26): three 30 s timers (units, buildings, resources) and a 60 s one
 * (players) each called level.getDataStorage().save() - a synchronous gzip write of EVERY dirty saved-data file -
 * on the server thread mid-fight, and the same full write ran on every research, nether-zone change, player join,
 * defeat and match reset (a reset wrote it seven times). The rule these tests hold: during play the RTS saves only
 * mark their data dirty; Minecraft's own world save (autosave every 5 min, /save-all, pause, stop) copies the live
 * state in and writes it once, in step with the chunks it describes.
 * <p>
 * Each test is written from that rule, not from the code: the first fails while any mid-match save still writes to
 * disk (a write clears the dirty flag), the second fails while a world save can miss live state that no save call
 * copied since the last timer.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class SaveHitchGameTests {

    private record Path(String name, Runnable save, Supplier<SavedData> data) {}

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 100)
    public static void a_mid_match_save_marks_its_data_dirty_and_writes_nothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<Path> paths = List.of(
            new Path("buildings", () -> BuildingServerEvents.saveBuildings(level), () -> BuildingSaveData.getInstance(level)),
            new Path("nether zones", () -> BuildingServerEvents.saveNetherZones(level), () -> NetherZoneSaveData.getInstance(level)),
            new Path("gather targets", () -> UnitServerEvents.saveGatherTargets(level), () -> TargetResourcesSaveData.getInstance(level)),
            new Path("fallen heroes", () -> UnitServerEvents.saveFallenHeroUnits(level), () -> HeroUnitSaveData.getInstance(level)),
            new Path("resources", () -> ResourcesServerEvents.saveResources(level), () -> ResourcesSaveData.getInstance(level)),
            new Path("rts players", PlayerServerEvents::saveRTSPlayers, () -> RTSPlayerSaveData.getInstance(level)),
            new Path("research", ResearchServerEvents::saveResearch, () -> ResearchSaveData.getInstance(level)),
            new Path("survival", () -> SurvivalServerEvents.saveData(level), () -> SurvivalSaveData.getInstance(level)),
            new Path("custom buildings", () -> CustomBuildingServerEvents.saveCustomBuildings(level), () -> CustomBuildingSaveData.getInstance(level))
        );
        List<String> wrote = new ArrayList<>();
        for (Path p : paths) {
            p.save().run();
            // a synchronous write is the only thing that clears the flag the save just set
            if (!p.data().get().isDirty())
                wrote.add(p.name());
        }
        if (!wrote.isEmpty())
            helper.fail("these mid-match saves wrote every saved-data file to disk on the server thread: " + wrote);
        else
            helper.succeed();
    }

    @GameTest(template = SkirmishGameTests.ARENA, timeoutTicks = 100)
    public static void a_world_save_writes_live_rts_state_no_save_call_copied(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String probe = "save_hitch_probe";
        // live state changed with no save call after it - the way income and spending change resources every tick
        Resources live = new Resources(probe, 4011, 4012, 4013, 4014);
        ResourcesServerEvents.resourcesList.add(live);
        try {
            level.save(null, false, false);   // what the 5-minute autosave and /save-all do for each level
            ResourcesSaveData data = ResourcesSaveData.getInstance(level);
            Resources saved = data.resources.stream().filter(r -> r.ownerName.equals(probe)).findFirst().orElse(null);
            if (saved == null)
                helper.fail("the world save wrote resources without the live player " + probe + " in them");
            else if (saved.food != 4011 || saved.wood != 4012 || saved.ore != 4013 || saved.emerald != 4014)
                helper.fail("the world save wrote stale resources for " + probe + ": " + saved.food + "/" + saved.wood
                    + "/" + saved.ore + "/" + saved.emerald);
            else if (data.isDirty())
                helper.fail("the world save left the resources dirty: the snapshot ran after the data was written");
            else
                helper.succeed();
        } finally {
            // leave the world as it was found: the probe out of the live list and out of the save data
            ResourcesServerEvents.resourcesList.remove(live);
            ResourcesServerEvents.saveResources(level);
        }
    }
}
