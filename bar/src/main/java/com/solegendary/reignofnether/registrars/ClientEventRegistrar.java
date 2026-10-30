package com.solegendary.reignofnether.registrars;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.attackwarnings.AttackWarningClientEvents;
import com.solegendary.reignofnether.attackwarnings.AttackWarningServerEvents;
import com.solegendary.reignofnether.blocks.BlockClientEvents;
import com.solegendary.reignofnether.blocks.BlockServerEvents;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.custombuilding.CustomBuildingClientEvents;
import com.solegendary.reignofnether.building.custombuilding.CustomBuildingServerEvents;
import com.solegendary.reignofnether.commands.CommandsServerEvents;
import com.solegendary.reignofnether.debug.RtsDebugClientEvents;
import com.solegendary.reignofnether.debug.RtsDebugNavmesh;
import com.solegendary.reignofnether.debug.RtsDebugPathPreview;
import com.solegendary.reignofnether.config.ConfigClientEvents;
import com.solegendary.reignofnether.config.ConfigVanillaServerEvents;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.fogofwar.PlayerChunksClientEvents;
import com.solegendary.reignofnether.gamemode.GameModeServerEvents;
import com.solegendary.reignofnether.gamerules.GameruleServerEvents;
import com.solegendary.reignofnether.guiscreen.TopdownGuiClientEvents;
import com.solegendary.reignofnether.healthbars.HealthBarClientEvents;
import com.solegendary.reignofnether.hero.HeroClientEvents;
import com.solegendary.reignofnether.hero.HeroServerEvents;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.hud.TextInputClientEvents;
import com.solegendary.reignofnether.hud.custombutton.CustomButtonServerEvents;
import com.solegendary.reignofnether.hud.playerdisplay.PlayerDisplayClientEvents;
import com.solegendary.reignofnether.hud.TitleClientEvents;
import com.solegendary.reignofnether.items.ItemClientEvents;
import com.solegendary.reignofnether.items.ItemServerEvents;
import com.solegendary.reignofnether.matchstart.MatchEndClientEvents;
import com.solegendary.reignofnether.matchstart.MatchStartClientEvents;
import com.solegendary.reignofnether.minimap.MinimapClientEvents;
import com.solegendary.reignofnether.orthoview.CameraFadeClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.ResourcesClientEvents;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.rtsmap.RTSMapInfoServerEvents;
import com.solegendary.reignofnether.sandbox.SandboxClientEvents;
import com.solegendary.reignofnether.scenario.ScenarioClientEvents;
import com.solegendary.reignofnether.scenario.ScenarioServerEvents;
import com.solegendary.reignofnether.sounds.SoundClientEvents;
import com.solegendary.reignofnether.startpos.StartPosClientEvents;
import com.solegendary.reignofnether.startpos.StartPosServerEvents;
import com.solegendary.reignofnether.survival.SurvivalClientEvents;
import com.solegendary.reignofnether.survival.SurvivalServerEvents;
import com.solegendary.reignofnether.taskscheduler.TaskSchedulerServerEvents;
import com.solegendary.reignofnether.time.TimeClientEvents;
import com.solegendary.reignofnether.time.TimeServerEvents;
import com.solegendary.reignofnether.debug.RtsDebugServerEvents;
import com.solegendary.reignofnether.tutorial.TutorialClientEvents;
import com.solegendary.reignofnether.tutorial.TutorialServerEvents;
import com.solegendary.reignofnether.unit.NonUnitClientEvents;
import com.solegendary.reignofnether.unit.NonUnitServerEvents;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.worldborder.WorldBorderClientEvents;
import com.solegendary.reignofnether.worldborder.WorldBorderServerEvents;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

public class ClientEventRegistrar {
    private final IEventBus vanillaEventBus = MinecraftForge.EVENT_BUS;

    public ClientEventRegistrar() { }

    /**
     * Register client only events. This method must only be called when it is certain that the mod is
     * is executing code on the client side and not the dedicated server.
     */

    public void registerClientEvents() {
        vanillaEventBus.register(OrthoviewClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.orthoview.SpectatorFreeFly.class);   // watchers: free flight
        // the frame-time profile (inbox/spec-5 section 3): reads only
        vanillaEventBus.register(com.solegendary.reignofnether.perf.FrameProfile.class);
        // the animation profile ([Anim]: the Life layer's share of a unit's render, per frame): reads only
        vanillaEventBus.register(com.solegendary.reignofnether.perf.AnimProfile.class);
        vanillaEventBus.register(com.solegendary.reignofnether.unit.modelling.renderers.UnitLod.class);
        vanillaEventBus.register(com.solegendary.reignofnether.unit.modelling.motion.AnimStates.class);
        vanillaEventBus.register(com.solegendary.reignofnether.unit.modelling.motion.Corpses.class);   // bodies held
        vanillaEventBus.register(com.solegendary.reignofnether.unit.modelling.motion.LivingArmyToggle.class);
        // the client's own systems per frame ([FrameParts]), the automatic client profile ([FrameAuto]) and GC pauses ([GC])
        vanillaEventBus.register(com.solegendary.reignofnether.perf.FrameWindow.class);
        vanillaEventBus.register(com.solegendary.reignofnether.perf.FrameAuto.class);
        com.solegendary.reignofnether.perf.GcLog.install();
        vanillaEventBus.register(com.solegendary.reignofnether.orthoview.StrategicViewClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.resources.TreeClearingClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.config.DisplaySettings.class);
        vanillaEventBus.register(TopdownGuiClientEvents.class);
        vanillaEventBus.register(ConfigClientEvents.class);
        vanillaEventBus.register(BuildingClientEvents.class); // being first fixes a bug with drawBuildingToPlace()
        vanillaEventBus.register(UnitClientEvents.class);
        vanillaEventBus.register(HealthBarClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.healthbars.BuildProgressBarClientEvents.class);
        vanillaEventBus.register(SandboxClientEvents.class);
        vanillaEventBus.register(HudClientEvents.class); // ensure this is first so cursor is rendered above hud
        vanillaEventBus.register(AttackWarningClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.hud.NotificationClientEvents.class);
        // after every other LOWEST-priority Render.Post listener, so the banner is the top layer
        vanillaEventBus.register(com.solegendary.reignofnether.hud.MatchBanner.class);
        vanillaEventBus.register(com.solegendary.reignofnether.resources.WreckClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.hud.IdleFactories.class);
        vanillaEventBus.register(com.solegendary.reignofnether.hud.IdleArmy.class);
        vanillaEventBus.register(CursorClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.cursor.CursorDiagClient.class);
        vanillaEventBus.register(MinimapClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.minimap.MapDrawClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.mana.ManaClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.resources.EconomySliderClientEvents.class);   // the economy bar's sliders
        vanillaEventBus.register(TimeClientEvents.class);
        vanillaEventBus.register(BlockClientEvents.class);
        vanillaEventBus.register(FogOfWarClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.fogofwar.FogRemesh.class);   // first-person flush, [FogRemesh] line
        vanillaEventBus.register(ResourcesClientEvents.class);
        vanillaEventBus.register(PlayerClientEvents.class);
        vanillaEventBus.register(TutorialClientEvents.class);
        vanillaEventBus.register(TitleClientEvents.class);
        vanillaEventBus.register(WorldBorderClientEvents.class);
        vanillaEventBus.register(SurvivalClientEvents.class);
        vanillaEventBus.register(StartPosClientEvents.class);
        vanillaEventBus.register(MatchStartClientEvents.class);
        vanillaEventBus.register(MatchEndClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.matchstart.MatchMenuClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.player.ResignVoteClientEvents.class);
        vanillaEventBus.register(NonUnitClientEvents.class);
        vanillaEventBus.register(HeroClientEvents.class);
        vanillaEventBus.register(CustomBuildingClientEvents.class);
        vanillaEventBus.register(SoundClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.sounds.ZoomVolume.class);   // BAR: quieter zoomed out
        vanillaEventBus.register(PlayerDisplayClientEvents.class);
        vanillaEventBus.register(ScenarioClientEvents.class);
        vanillaEventBus.register(TextInputClientEvents.class);
        vanillaEventBus.register(RtsDebugClientEvents.class);
        vanillaEventBus.register(RtsDebugNavmesh.class);
        vanillaEventBus.register(RtsDebugPathPreview.class);
        vanillaEventBus.register(PlayerChunksClientEvents.class);
        vanillaEventBus.register(CameraFadeClientEvents.class);
        vanillaEventBus.register(ItemClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.barfx.BarFxClientEvents.class);
        // the world look: haze + grade + vignette after the world, before the hand and HUD (postfx/WorldLookPass)
        vanillaEventBus.register(com.solegendary.reignofnether.postfx.WorldLookClientEvents.class);
        // frame times over a fixed number of frames: the shader pack's cost (eyes, /rts-shaderbench)
        vanillaEventBus.register(com.solegendary.reignofnether.postfx.ShaderBench.class);
        vanillaEventBus.register(com.solegendary.reignofnether.commands.lang.CommandBarClientEvents.class);

        // to allow singleplayer integrated server to work
        vanillaEventBus.register(GameruleServerEvents.class);
        vanillaEventBus.register(BlockServerEvents.class);
        vanillaEventBus.register(TutorialServerEvents.class);
        vanillaEventBus.register(PlayerServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.bot.BotServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.unit.PatrolServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.building.StewardServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.unit.GuardServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.unit.UnitDressServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.resources.MetalPatchesServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.startpos.BattlefieldSetup.class);
        vanillaEventBus.register(com.solegendary.reignofnether.startpos.BaseWildlifeControl.class);
        vanillaEventBus.register(com.solegendary.reignofnether.startpos.BattlefieldWall.class);
        // pre-made maps and the start-position phase (design/maps.md)
        vanillaEventBus.register(com.solegendary.reignofnether.maps.MapBuilder.class);
        vanillaEventBus.register(com.solegendary.reignofnether.maps.StartPhase.class);
        vanillaEventBus.register(com.solegendary.reignofnether.maps.MapMatch.class);
        vanillaEventBus.register(com.solegendary.reignofnether.maps.MapClient.class);
        // the one RTS hint bar (banner + tips): after HudClientEvents, so it stacks from this frame's alertBottom
        vanillaEventBus.register(com.solegendary.reignofnether.hud.HintBar.class);
        vanillaEventBus.register(com.solegendary.reignofnether.startpos.ArenaStructureClearing.class);
        vanillaEventBus.register(com.solegendary.reignofnether.player.CommanderServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.ability.abilities.BastionAegis.class);
        vanillaEventBus.register(com.solegendary.reignofnether.ability.abilities.WitheringFog.class);
        vanillaEventBus.register(com.solegendary.reignofnether.ability.abilities.SunriseSortie.class);
        vanillaEventBus.register(com.solegendary.reignofnether.ability.abilities.LeafDash.class);
        vanillaEventBus.register(com.solegendary.reignofnether.ability.abilities.AwakenThicket.class);
        vanillaEventBus.register(com.solegendary.reignofnether.ability.abilities.StagLeap.class);
        vanillaEventBus.register(com.solegendary.reignofnether.ability.abilities.BoulderToss.class);
        vanillaEventBus.register(com.solegendary.reignofnether.player.CommanderClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.building.PatchQuickBuildClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.building.AreaMexClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.unit.AreaCommandClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.hud.playerdisplay.PlayerPanelClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.hud.playerdisplay.SpectatorView.class);   // spectator: follow a side (F8)
        vanillaEventBus.register(com.solegendary.reignofnether.hud.playerdisplay.ObserverBarClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.hud.HudPanels.class);   // collapsible panels, I / = keys
        vanillaEventBus.register(com.solegendary.reignofnether.player.PlayerPanelServerEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.building.BuildingLineClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.building.BlueprintClientEvents.class);   // the ghosts of sites no worker has started (building/Blueprints)
        vanillaEventBus.register(com.solegendary.reignofnether.building.PlacementGridClientEvents.class);   // after BuildingClientEvents: reads the ghost validity it set this frame
        vanillaEventBus.register(com.solegendary.reignofnether.building.WindmillRenderClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.keybinds.KeybindsClientEvents.class);   // saved keys -> new defaults, once
        vanillaEventBus.register(com.solegendary.reignofnether.building.BuildingAmbienceClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.ambient.mist.MistClientEvents.class);   // valley mist
        vanillaEventBus.register(com.solegendary.reignofnether.building.BannerRenderClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.building.ScaffoldRenderClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.building.PartRenderClientEvents.class);   // drawn extractor rigs
        vanillaEventBus.register(com.solegendary.reignofnether.environment.wind.BranchWindClient.class);   // tree limbs in the wind
        vanillaEventBus.register(com.solegendary.reignofnether.building.StatueRenderClientEvents.class);   // drawn statues
        vanillaEventBus.register(com.solegendary.reignofnether.resources.MetalPatchMarkerClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.matchstart.QuickBattle.class);
        // the loading veil from Start Battle to the start boxes (spec 3, PR 3)
        vanillaEventBus.register(com.solegendary.reignofnether.matchstart.MatchLoadingScreen.class);
        vanillaEventBus.register(com.solegendary.reignofnether.matchstart.EyesDirector.class);   // CI screenshots, -Dbat.eyes only
        vanillaEventBus.register(com.solegendary.reignofnether.matchstart.LobbyClientEvents.class);
        vanillaEventBus.register(com.solegendary.reignofnether.hero.PossessionClientEvents.class);   // hero mode
        vanillaEventBus.register(com.solegendary.reignofnether.hero.PossessionHudClient.class);   // hero slice 3: damage numbers
        // onboarding (design/onboarding.md): First Battle entry + panel, first-time tips. After QuickBattle, so the
        // title hook finds the Skirmish button to share its row with
        vanillaEventBus.register(com.solegendary.reignofnether.onboarding.OnboardingClient.class);
        vanillaEventBus.register(com.solegendary.reignofnether.onboarding.OnboardingTips.class);
        vanillaEventBus.register(com.solegendary.reignofnether.onboarding.FirstLaunchClientEvents.class);   // no accessibility screen, full screen once
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
        // Every server-side handler the dedicated server gets, for the integrated server too. The hand-copied list
        // above had drifted: wrecks, momentum, formations, veterancy, tidepools, ley links, the capture points and a
        // dozen more were registered on dedicated servers only, so none of them ran in singleplayer (lovish's
        // skirmishes). EventBus.register ignores a class that is already registered, so the duplicates above are
        // harmless and the client-first order (HUD before cursor) is kept.
        new ServerEventRegistrar().registerServerEvents();
    }
}
