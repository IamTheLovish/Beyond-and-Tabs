package com.solegendary.reignofnether.registrars;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.attackwarnings.AttackWarningServerEvents;
import com.solegendary.reignofnether.blocks.BlockServerEvents;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.custombuilding.CustomBuildingServerEvents;
import com.solegendary.reignofnether.commands.CommandsServerEvents;
import com.solegendary.reignofnether.config.ConfigVanillaServerEvents;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.gamemode.GameModeServerEvents;
import com.solegendary.reignofnether.gamerules.GameruleServerEvents;
import com.solegendary.reignofnether.hero.HeroServerEvents;
import com.solegendary.reignofnether.hud.custombutton.CustomButtonServerEvents;
import com.solegendary.reignofnether.items.ItemServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.rtsmap.RTSMapInfoServerEvents;
import com.solegendary.reignofnether.scenario.ScenarioServerEvents;
import com.solegendary.reignofnether.startpos.StartPosServerEvents;
import com.solegendary.reignofnether.survival.SurvivalServerEvents;
import com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents;
import com.solegendary.reignofnether.time.TimeServerEvents;
import com.solegendary.reignofnether.debug.RtsDebugServerEvents;
import com.solegendary.reignofnether.unit.NonUnitServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.worldborder.WorldBorderServerEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/*
    This class is required to make sure that we don't accidentally try to load any client-side-only classes
      on a dedicated server.
    It is a rather convoluted way of doing it, but I haven't found a simpler way to do it which is robust
 */

public class ServerEventRegistrar {
    private final IEventBus vanillaEventBus = MinecraftForge.EVENT_BUS;

    public ServerEventRegistrar() { }

    /**
     * Register server only events. This method must only be called when it is certain that the mod is
     * is executing code on the server side and not the client.
     */
    public void registerServerEvents() {
        vanillaEventBus.register(GameruleServerEvents.class);
        vanillaEventBus.register(BlockServerEvents.class);
        vanillaEventBus.register(PlayerServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.bot.BotServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.unit.PatrolServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.building.StewardServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.unit.GuardServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.unit.UnitDressServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.resources.MetalPatchesServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.resources.WreckServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.resources.TreeClearing.class);
        vanillaEventBus.register(com.solegendary.reignofnether.building.BuildingReclaim.class);   // Decommission (design/building_upgrades.md)
        vanillaEventBus.register(com.solegendary.reignofnether.building.FactionAuraServerEvents.class);   // faction terraform (design/biome_bases.md)
        vanillaEventBus.register(com.solegendary.reignofnether.unit.UnitQueueSync.class);
        vanillaEventBus.register(com.solegendary.reignofnether.unit.MomentumServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.unit.FormationServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.unit.UnitGrid.class);
        vanillaEventBus.register(com.solegendary.reignofnether.unit.VeterancyServerEvents.class);
        // BAR HUD pass 4: per-unit kill counters, the kill feed and the wreck metal labels
        vanillaEventBus.register(com.solegendary.reignofnether.unit.UnitKills.class);
        vanillaEventBus.register(com.solegendary.reignofnether.hud.KillFeedServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.resources.WreckSyncServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.unit.DragonRaiseServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.startpos.CapturePointServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.startpos.BattlefieldSetup.class);
        vanillaEventBus.register(com.solegendary.reignofnether.startpos.BaseWildlifeControl.class);
        vanillaEventBus.register(com.solegendary.reignofnether.startpos.BattlefieldWall.class);
        vanillaEventBus.register(com.solegendary.reignofnether.startpos.ArenaPregen.class);
        // pre-made maps and the start-position phase (design/maps.md)
        vanillaEventBus.register(com.solegendary.reignofnether.maps.MapBuilder.class);
        vanillaEventBus.register(com.solegendary.reignofnether.maps.StartPhase.class);
        vanillaEventBus.register(com.solegendary.reignofnether.maps.MapMatch.class);
        vanillaEventBus.register(com.solegendary.reignofnether.startpos.ArenaStructureClearing.class);
        vanillaEventBus.register(com.solegendary.reignofnether.player.CommanderServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.player.DefeatCleanupServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.player.ResignVoteServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.matchstart.MultiplayerLobby.class);
        vanillaEventBus.register(com.solegendary.reignofnether.match.MatchServerEvents.class);   // match lifecycle (rewrite 5)
        // hero mode: possession of units and the team hero (design/hero_mode.md)
        vanillaEventBus.register(com.solegendary.reignofnether.hero.PossessionServer.class);
        // the First Battle tutorial (design/onboarding.md)
        vanillaEventBus.register(com.solegendary.reignofnether.onboarding.OnboardingServer.class);
        vanillaEventBus.register(com.solegendary.reignofnether.player.PlayerPanelServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.ability.abilities.BastionAegis.class);
        vanillaEventBus.register(com.solegendary.reignofnether.ability.abilities.TotemOfThePack.class);
        vanillaEventBus.register(com.solegendary.reignofnether.ability.abilities.WitheringFog.class);
        vanillaEventBus.register(com.solegendary.reignofnether.ability.abilities.SunriseSortie.class);
        vanillaEventBus.register(com.solegendary.reignofnether.ability.abilities.LeafDash.class);
        vanillaEventBus.register(com.solegendary.reignofnether.ability.abilities.PlantVineSnare.class);
        vanillaEventBus.register(com.solegendary.reignofnether.ability.abilities.PlantThicket.class);
        vanillaEventBus.register(com.solegendary.reignofnether.blocks.ThicketCover.class);
        vanillaEventBus.register(com.solegendary.reignofnether.ability.abilities.SoulWisps.class);
        vanillaEventBus.register(com.solegendary.reignofnether.ability.abilities.AwakenThicket.class);
        vanillaEventBus.register(com.solegendary.reignofnether.ability.abilities.StagLeap.class);
        vanillaEventBus.register(com.solegendary.reignofnether.ability.abilities.BoulderToss.class);
        // Tidewrought Tides core: tidepool registry/expiry/drain + /rts-tidepool, and the wet buff
        vanillaEventBus.register(com.solegendary.reignofnether.tide.TidepoolServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.tide.TidesServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.tide.FogBankServerEvents.class);
        // Stormcallers units: the Jade Guard's Jade Mirror
        vanillaEventBus.register(com.solegendary.reignofnether.unit.units.storm.StormUnitEvents.class);
        // Brassworks: venting machines take more damage; the Shield Projector's bubbles
        vanillaEventBus.register(com.solegendary.reignofnether.brass.BrassUnitEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.brass.ShieldServerEvents.class);
        // Stormcallers slice 2: the Thunder Sage's Ward, the Frost Adept's frost sheets, the Geomancer's ley-link beams
        vanillaEventBus.register(com.solegendary.reignofnether.mana.WardServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.mana.FrostServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.mana.LeyNetwork.class);
        // Stormcallers slice 3: the Ley Master's Mana Wells, the Mirror Mage's decoys
        vanillaEventBus.register(com.solegendary.reignofnether.mana.ManaWellServerEvents.class);
        // wells, ley links and tidepools saved with the world (design/persistence.md)
        vanillaEventBus.register(com.solegendary.reignofnether.persistence.MatchStateSaveData.class);
        vanillaEventBus.register(com.solegendary.reignofnether.mana.DecoyServerEvents.class);
        // Stormcallers slice 4: the Lantern Fortress's dome, the Celestial Dragon's Tempest, the Phoenix's rebirth
        vanillaEventBus.register(com.solegendary.reignofnether.mana.LanternDomeServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.mana.TempestServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.unit.OnceRevive.class);
        vanillaEventBus.register(ConfigVanillaServerEvents.class);
        vanillaEventBus.register(UnitServerEvents.class);
        vanillaEventBus.register(BuildingServerEvents.class);
        vanillaEventBus.register(AttackWarningServerEvents.class);
        vanillaEventBus.register(ResourcesServerEvents.class);
        vanillaEventBus.register(RtsDebugServerEvents.class);
        vanillaEventBus.register(FogOfWarServerEvents.class);
        vanillaEventBus.register(ResearchServerEvents.class);
        vanillaEventBus.register(SurvivalServerEvents.class);
        vanillaEventBus.register(GameModeServerEvents.class);
        vanillaEventBus.register(RTSMapInfoServerEvents.class);
        vanillaEventBus.register(StartPosServerEvents.class);
        vanillaEventBus.register(AlliancesServerEvents.class);
        vanillaEventBus.register(HeroServerEvents.class);
        vanillaEventBus.register(NonUnitServerEvents.class);
        vanillaEventBus.register(TimeServerEvents.class);
        vanillaEventBus.register(CustomBuildingServerEvents.class);
        vanillaEventBus.register(CommandsServerEvents.class);
        vanillaEventBus.register(ScenarioServerEvents.class);
        vanillaEventBus.register(WorldBorderServerEvents.class);
        vanillaEventBus.register(CustomButtonServerEvents.class);
        vanillaEventBus.register(ItemServerEvents.class);
        vanillaEventBus.register(TaskSchedulerServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.barfx.BarFxServerEvents.class);
    }
}
