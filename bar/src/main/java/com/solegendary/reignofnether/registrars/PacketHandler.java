package com.solegendary.reignofnether.registrars;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.AbilityClientboundPacket;
import com.solegendary.reignofnether.ability.AbilityServerboundPacket;
import com.solegendary.reignofnether.ability.BuildingAbilityClientboundPacket;
import com.solegendary.reignofnether.ability.BuildingAbilityServerboundPacket;
import com.solegendary.reignofnether.alliance.*;
import com.solegendary.reignofnether.attackwarnings.AttackWarningClientboundPacket;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.custombuilding.CustomBuildingClientboundPacket;
import com.solegendary.reignofnether.building.custombuilding.CustomBuildingServerboundPacket;
import com.solegendary.reignofnether.config.ClientboundSyncResourceCostPacket;
import com.solegendary.reignofnether.fogofwar.*;
import com.solegendary.reignofnether.gamemode.GameModeClientboundPacket;
import com.solegendary.reignofnether.gamemode.GameModeServerboundPacket;
import com.solegendary.reignofnether.gamerules.GameruleClientboundPacket;
import com.solegendary.reignofnether.gamerules.GameruleServerboundPacket;
import com.solegendary.reignofnether.guiscreen.TopdownGuiServerboundPacket;
import com.solegendary.reignofnether.hero.FallenHeroClientboundPacket;
import com.solegendary.reignofnether.hero.HeroClientboundPacket;
import com.solegendary.reignofnether.hero.HeroServerboundPacket;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.hud.custombutton.CustomButtonActionServerboundPacket;
import com.solegendary.reignofnether.hud.custombutton.CustomButtonClientboundPacket;
import com.solegendary.reignofnether.items.ItemClientboundPacket;
import com.solegendary.reignofnether.items.ItemServerboundPacket;
import com.solegendary.reignofnether.items.ItemShopClientboundPacket;
import com.solegendary.reignofnether.minimap.MapDrawClientboundPacket;
import com.solegendary.reignofnether.minimap.MapDrawServerboundPacket;
import com.solegendary.reignofnether.minimap.MapMarkerClientboundPacket;
import com.solegendary.reignofnether.minimap.MapMarkerServerboundPacket;
import com.solegendary.reignofnether.orthoview.CameraClientboundPacket;
import com.solegendary.reignofnether.orthoview.CameraFadeClientboundPacket;
import com.solegendary.reignofnether.player.MatchStatsClientboundPacket;
import com.solegendary.reignofnether.player.PlayerClientboundPacket;
import com.solegendary.reignofnether.player.PlayerServerboundPacket;
import com.solegendary.reignofnether.research.ResearchClientboundPacket;
import com.solegendary.reignofnether.research.ResearchServerboundPacket;
import com.solegendary.reignofnether.resources.ResourcesClientboundPacket;
import com.solegendary.reignofnether.resources.EconomyClientboundPacket;
import com.solegendary.reignofnether.resources.ResourcesServerboundPacket;
import com.solegendary.reignofnether.rtsmap.RTSMapInfoClientboundPacket;
import com.solegendary.reignofnether.rtsmap.RTSMapInfoServerboundPacket;
import com.solegendary.reignofnether.sandbox.SandboxServerboundPacket;
import com.solegendary.reignofnether.scenario.ScenarioClientboundPacket;
import com.solegendary.reignofnether.scenario.ScenarioServerboundPacket;
import com.solegendary.reignofnether.sounds.SoundClientboundPacket;
import com.solegendary.reignofnether.startpos.StartPosClientboundPacket;
import com.solegendary.reignofnether.startpos.StartPosServerboundPacket;
import com.solegendary.reignofnether.survival.SurvivalClientboundPacket;
import com.solegendary.reignofnether.survival.SurvivalServerboundPacket;
import com.solegendary.reignofnether.tutorial.TutorialClientboundPacket;
import com.solegendary.reignofnether.tutorial.TutorialServerboundPacket;
import com.solegendary.reignofnether.unit.packets.*;
import com.solegendary.reignofnether.debug.RtsDebugChunksClientboundPacket;
import com.solegendary.reignofnether.debug.RtsDebugStatsClientboundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import com.solegendary.reignofnether.network.PacketTableHash;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Initialises all of the client-server packet-sending classes

public final class PacketHandler {
    private static final String MOD_VERSION = ModList.get()
            .getModContainerById("reignofnether")
            .map(c -> c.getModInfo().getVersion().toString())
            .orElse("dev");

    // The packet table as registered (index, class, direction - PacketTableHash.entry), and the protocol version made
    // from it at the end of init(). The mod version alone is a constant (1.5.0-beta-4), so two builds with different
    // tables passed the handshake and then decoded packets as the wrong ones (security audit F3, Oct 26); Forge reads
    // the version through the supplier below at the handshake, long after init() ran.
    private static final List<String> TABLE = new ArrayList<>();
    private static volatile String protocolVersion = MOD_VERSION + "+packets.unregistered";

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "main"), PacketHandler::protocolVersion,
            v -> protocolVersion.equals(v), v -> protocolVersion.equals(v));


    private PacketHandler() { }

    /** What this jar sends and accepts at the handshake: mod version + hash of the packet table. */
    public static String protocolVersion() {
        return protocolVersion;
    }

    /** The registered packet table, in order (the game test checks the version is made from it). */
    public static List<String> table() {
        return Collections.unmodifiableList(TABLE);
    }

    /** Every packet registers through here, so the table (and the version) cannot miss one - init() checks the count. */
    private static <M> SimpleChannel.MessageBuilder<M> register(Class<M> type, int index, NetworkDirection direction) {
        TABLE.add(PacketTableHash.entry(index, type.getName(), direction.name()));
        return INSTANCE.messageBuilder(type, index, direction);
    }

    public static void init() {
        TABLE.clear();
        int index = 0;

        register(TopdownGuiServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TopdownGuiServerboundPacket::encode).decoder(TopdownGuiServerboundPacket::new)
                .consumerMainThread(TopdownGuiServerboundPacket::handle).add();

        register(UnitActionServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UnitActionServerboundPacket::encode).decoder(UnitActionServerboundPacket::new)
                .consumerMainThread(UnitActionServerboundPacket::handle).add();

        register(BeaconSyncClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BeaconSyncClientboundPacket::encode).decoder(BeaconSyncClientboundPacket::new)
                .consumerMainThread(BeaconSyncClientboundPacket::handle).add();

        register(UnitConvertClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(UnitConvertClientboundPacket::encode).decoder(UnitConvertClientboundPacket::new)
                .consumerMainThread(UnitConvertClientboundPacket::handle).add();

        register(UnitSyncClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(UnitSyncClientboundPacket::encode).decoder(UnitSyncClientboundPacket::new)
                .consumerMainThread(UnitSyncClientboundPacket::handle).add();

        register(UnitSyncWorkerClientBoundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(UnitSyncWorkerClientBoundPacket::encode).decoder(UnitSyncWorkerClientBoundPacket::new)
                .consumerMainThread(UnitSyncWorkerClientBoundPacket::handle).add();

        register(UnitSyncAbilityClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(UnitSyncAbilityClientboundPacket::encode).decoder(UnitSyncAbilityClientboundPacket::new)
                .consumerMainThread(UnitSyncAbilityClientboundPacket::handle).add();

        register(UnitSyncServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(UnitSyncServerboundPacket::encode).decoder(UnitSyncServerboundPacket::new)
                .consumerMainThread(UnitSyncServerboundPacket::handle).add();

        register(UnitAnimationClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(UnitAnimationClientboundPacket::encode).decoder(UnitAnimationClientboundPacket::new)
                .consumerMainThread(UnitAnimationClientboundPacket::handle).add();

        register(UnitPathClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(UnitPathClientboundPacket::encode).decoder(UnitPathClientboundPacket::new)
                .consumerMainThread(UnitPathClientboundPacket::handle).add();

        register(RtsDebugStatsClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RtsDebugStatsClientboundPacket::encode)
                .decoder(RtsDebugStatsClientboundPacket::new)
                .consumerMainThread(RtsDebugStatsClientboundPacket::handle).add();

        register(RtsDebugChunksClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RtsDebugChunksClientboundPacket::encode)
                .decoder(RtsDebugChunksClientboundPacket::new)
                .consumerMainThread(RtsDebugChunksClientboundPacket::handle).add();

        register(UnitIdleWorkerClientBoundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(UnitIdleWorkerClientBoundPacket::encode).decoder(UnitIdleWorkerClientBoundPacket::new)
                .consumerMainThread(UnitIdleWorkerClientBoundPacket::handle).add();

        register(ResearchClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ResearchClientboundPacket::encode).decoder(ResearchClientboundPacket::new)
                .consumerMainThread(ResearchClientboundPacket::handle).add();

        register(ResearchServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ResearchServerboundPacket::encode).decoder(ResearchServerboundPacket::new)
                .consumerMainThread(ResearchServerboundPacket::handle).add();

        register(PlayerServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(PlayerServerboundPacket::encode).decoder(PlayerServerboundPacket::new)
                .consumerMainThread(PlayerServerboundPacket::handle).add();

        register(PlayerClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PlayerClientboundPacket::encode).decoder(PlayerClientboundPacket::new)
                .consumerMainThread(PlayerClientboundPacket::handle).add();

        register(FogOfWarClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FogOfWarClientboundPacket::encode).decoder(FogOfWarClientboundPacket::new)
                .consumerMainThread(FogOfWarClientboundPacket::handle).add();

        register(FogChunksClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FogChunksClientboundPacket::encode).decoder(FogChunksClientboundPacket::new)
                .consumerMainThread(FogChunksClientboundPacket::handle).add();

        register(FogOfWarServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(FogOfWarServerboundPacket::encode).decoder(FogOfWarServerboundPacket::new)
                .consumerMainThread(FogOfWarServerboundPacket::handle).add();

        register(FrozenChunkClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FrozenChunkClientboundPacket::encode).decoder(FrozenChunkClientboundPacket::new)
                .consumerMainThread(FrozenChunkClientboundPacket::handle).add();

        register(BuildingServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BuildingServerboundPacket::encode).decoder(BuildingServerboundPacket::new)
                .consumerMainThread(BuildingServerboundPacket::handle).add();

        register(BuildingClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BuildingClientboundPacket::encode).decoder(BuildingClientboundPacket::new)
                .consumerMainThread(BuildingClientboundPacket::handle).add();

        register(BuildingProductionServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BuildingProductionServerboundPacket::encode).decoder(BuildingProductionServerboundPacket::new)
                .consumerMainThread(BuildingProductionServerboundPacket::handle).add();

        register(BuildingProductionClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BuildingProductionClientboundPacket::encode).decoder(BuildingProductionClientboundPacket::new)
                .consumerMainThread(BuildingProductionClientboundPacket::handle).add();

        register(ResourcesClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ResourcesClientboundPacket::encode).decoder(ResourcesClientboundPacket::new)
                .consumerMainThread(ResourcesClientboundPacket::handle).add();

        register(ResourcesServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ResourcesServerboundPacket::encode)
                .decoder(ResourcesServerboundPacket::new)
                .consumerMainThread(ResourcesServerboundPacket::handle)
                .add();

        register(AbilityClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AbilityClientboundPacket::encode).decoder(AbilityClientboundPacket::new)
                .consumerMainThread(AbilityClientboundPacket::handle).add();

        register(AbilityServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AbilityServerboundPacket::encode).decoder(AbilityServerboundPacket::new)
                .consumerMainThread(AbilityServerboundPacket::handle).add();

        register(BuildingAbilityServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(BuildingAbilityServerboundPacket::encode).decoder(BuildingAbilityServerboundPacket::new)
                .consumerMainThread(BuildingAbilityServerboundPacket::handle).add();

        register(BuildingAbilityClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BuildingAbilityClientboundPacket::encode).decoder(BuildingAbilityClientboundPacket::new)
                .consumerMainThread(BuildingAbilityClientboundPacket::handle).add();

        register(AttackWarningClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AttackWarningClientboundPacket::encode).decoder(AttackWarningClientboundPacket::new)
                .consumerMainThread(AttackWarningClientboundPacket::handle).add();

        register(SoundClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SoundClientboundPacket::encode).decoder(SoundClientboundPacket::new)
                .consumerMainThread(SoundClientboundPacket::handle).add();

        register(TutorialClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TutorialClientboundPacket::encode).decoder(TutorialClientboundPacket::new)
                .consumerMainThread(TutorialClientboundPacket::handle).add();

        register(TutorialServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TutorialServerboundPacket::encode).decoder(TutorialServerboundPacket::new)
                .consumerMainThread(TutorialServerboundPacket::handle).add();

        register(AllianceClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AllianceClientboundPacket::encode)
                .decoder(AllianceClientboundPacket::new)
                .consumerMainThread(AllianceClientboundPacket::handle)
                .add();


        register(AllianceServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(AllianceServerboundPacket::encode)
                .decoder(AllianceServerboundPacket::new)
                .consumerMainThread(AllianceServerboundPacket::handle)
                .add();

        register(GameModeServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(GameModeServerboundPacket::encode)
                .decoder(GameModeServerboundPacket::new)
                .consumerMainThread(GameModeServerboundPacket::handle)
                .add();

        register(GameModeClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GameModeClientboundPacket::encode)
                .decoder(GameModeClientboundPacket::new)
                .consumerMainThread(GameModeClientboundPacket::handle)
                .add();

        register(SurvivalServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SurvivalServerboundPacket::encode)
                .decoder(SurvivalServerboundPacket::new)
                .consumerMainThread(SurvivalServerboundPacket::handle)
                .add();

        register(SurvivalClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SurvivalClientboundPacket::encode)
                .decoder(SurvivalClientboundPacket::new)
                .consumerMainThread(SurvivalClientboundPacket::handle)
                .add();

        register(ClientboundSyncResourceCostPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientboundSyncResourceCostPacket::encode)
                .decoder(ClientboundSyncResourceCostPacket::decode)
                .consumerMainThread(ClientboundSyncResourceCostPacket::handle)
                .add();

        register(SandboxServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SandboxServerboundPacket::encode)
                .decoder(SandboxServerboundPacket::new)
                .consumerMainThread(SandboxServerboundPacket::handle)
                .add();

        register(GameruleServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(GameruleServerboundPacket::encode)
                .decoder(GameruleServerboundPacket::new)
                .consumerMainThread(GameruleServerboundPacket::handle)
                .add();

        register(GameruleClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GameruleClientboundPacket::encode)
                .decoder(GameruleClientboundPacket::new)
                .consumerMainThread(GameruleClientboundPacket::handle)
                .add();

        register(StartPosServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(StartPosServerboundPacket::encode)
                .decoder(StartPosServerboundPacket::new)
                .consumerMainThread(StartPosServerboundPacket::handle)
                .add();

        register(StartPosClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StartPosClientboundPacket::encode)
                .decoder(StartPosClientboundPacket::new)
                .consumerMainThread(StartPosClientboundPacket::handle)
                .add();

        register(HeroClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HeroClientboundPacket::encode)
                .decoder(HeroClientboundPacket::new)
                .consumerMainThread(HeroClientboundPacket::handle)
                .add();

        register(HeroServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(HeroServerboundPacket::encode)
                .decoder(HeroServerboundPacket::new)
                .consumerMainThread(HeroServerboundPacket::handle)
                .add();

        register(FallenHeroClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FallenHeroClientboundPacket::encode)
                .decoder(FallenHeroClientboundPacket::new)
                .consumerMainThread(FallenHeroClientboundPacket::handle)
                .add();

        register(CustomBuildingClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CustomBuildingClientboundPacket::encode)
                .decoder(CustomBuildingClientboundPacket::new)
                .consumerMainThread(CustomBuildingClientboundPacket::handle)
                .add();

        register(CustomBuildingServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CustomBuildingServerboundPacket::encode)
                .decoder(CustomBuildingServerboundPacket::new)
                .consumerMainThread(CustomBuildingServerboundPacket::handle)
                .add();

        register(UnitSyncMobEffectsClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(UnitSyncMobEffectsClientboundPacket::encode)
                .decoder(UnitSyncMobEffectsClientboundPacket::new)
                .consumerMainThread(UnitSyncMobEffectsClientboundPacket::handle)
                .add();

        register(com.solegendary.reignofnether.matchstart.SkirmishServerboundPacket.class,
                        index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.solegendary.reignofnether.matchstart.SkirmishServerboundPacket::encode)
                .decoder(com.solegendary.reignofnether.matchstart.SkirmishServerboundPacket::new)
                .consumerMainThread(com.solegendary.reignofnether.matchstart.SkirmishServerboundPacket::handle)
                .add();
        register(MapMarkerServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MapMarkerServerboundPacket::encode)
                .decoder(MapMarkerServerboundPacket::new)
                .consumerMainThread(MapMarkerServerboundPacket::handle)
                .add();

        register(MapMarkerClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MapMarkerClientboundPacket::encode)
                .decoder(MapMarkerClientboundPacket::new)
                .consumerMainThread(MapMarkerClientboundPacket::handle)
                .add();

        register(ScenarioServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ScenarioServerboundPacket::encode)
                .decoder(ScenarioServerboundPacket::new)
                .consumerMainThread(ScenarioServerboundPacket::handle)
                .add();

        register(ScenarioClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ScenarioClientboundPacket::encode)
                .decoder(ScenarioClientboundPacket::new)
                .consumerMainThread(ScenarioClientboundPacket::handle)
                .add();

        register(MatchStatsClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MatchStatsClientboundPacket::encode)
                .decoder(MatchStatsClientboundPacket::new)
                .consumerMainThread(MatchStatsClientboundPacket::handle)
                .add();

        register(RTSMapInfoClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RTSMapInfoClientboundPacket::encode)
                .decoder(RTSMapInfoClientboundPacket::new)
                .consumerMainThread(RTSMapInfoClientboundPacket::handle)
                .add();

        register(RTSMapInfoServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RTSMapInfoServerboundPacket::encode)
                .decoder(RTSMapInfoServerboundPacket::new)
                .consumerMainThread(RTSMapInfoServerboundPacket::handle)
                .add();

        register(PlayerChunksClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PlayerChunksClientboundPacket::encode)
                .decoder(PlayerChunksClientboundPacket::new)
                .consumerMainThread(PlayerChunksClientboundPacket::handle)
                .add();

        register(HudClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(HudClientboundPacket::encode)
                .decoder(HudClientboundPacket::new)
                .consumerMainThread(HudClientboundPacket::handle)
                .add();

        register(CameraClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CameraClientboundPacket::encode)
                .decoder(CameraClientboundPacket::new)
                .consumerMainThread(CameraClientboundPacket::handle)
                .add();

        register(CameraFadeClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CameraFadeClientboundPacket::encode)
                .decoder(CameraFadeClientboundPacket::new)
                .consumerMainThread(CameraFadeClientboundPacket::handle)
                .add();

        register(FogNeutralUnitClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FogNeutralUnitClientboundPacket::encode)
                .decoder(FogNeutralUnitClientboundPacket::new)
                .consumerMainThread(FogNeutralUnitClientboundPacket::handle)
                .add();

        register(FogBuildingClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FogBuildingClientboundPacket::encode)
                .decoder(FogBuildingClientboundPacket::new)
                .consumerMainThread(FogBuildingClientboundPacket::handle)
                .add();

        // a blueprint's ghost (building/Blueprints): owner, allies and spectators only
        register(BlueprintClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BlueprintClientboundPacket::encode)
                .decoder(BlueprintClientboundPacket::new)
                .consumerMainThread(BlueprintClientboundPacket::handle)
                .add();

        register(CustomButtonActionServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CustomButtonActionServerboundPacket::encode)
                .decoder(CustomButtonActionServerboundPacket::decode)
                .consumerMainThread(CustomButtonActionServerboundPacket::handle)
                .add();

        register(CustomButtonClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CustomButtonClientboundPacket::encode)
                .decoder(CustomButtonClientboundPacket::decode)
                .consumerMainThread(CustomButtonClientboundPacket::handle)
                .add();

        register(ItemServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ItemServerboundPacket::encode)
                .decoder(ItemServerboundPacket::new)
                .consumerMainThread(ItemServerboundPacket::handle)
                .add();

        register(ItemClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ItemClientboundPacket::encode)
                .decoder(ItemClientboundPacket::new)
                .consumerMainThread(ItemClientboundPacket::handle)
                .add();

        register(ItemShopClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ItemShopClientboundPacket::encode)
                .decoder(ItemShopClientboundPacket::new)
                .consumerMainThread(ItemShopClientboundPacket::handle)
                .add();

        // BAR flow economy state (metal/energy, storage, income, expense, stall)
        register(EconomyClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(EconomyClientboundPacket::encode)
                .decoder(EconomyClientboundPacket::new)
                .consumerMainThread(EconomyClientboundPacket::handle)
                .add();
        // metal patch centres for the minimap (sent at match start and on login)
        register(com.solegendary.reignofnether.resources.MetalPatchesClientboundPacket.class,
                        index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.solegendary.reignofnether.resources.MetalPatchesClientboundPacket::encode)
                .decoder(com.solegendary.reignofnether.resources.MetalPatchesClientboundPacket::new)
                .consumerMainThread(com.solegendary.reignofnether.resources.MetalPatchesClientboundPacket::handle)
                .add();
        // batched + delta-compressed periodic unit sync (replaces the per-unit stats/resources/anchor/worker/inventory
        // packets in UnitServerEvents.onWorldTick, see UnitSyncBatcher)
        register(UnitBatchSyncClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(UnitBatchSyncClientboundPacket::encode)
                .decoder(UnitBatchSyncClientboundPacket::new)
                .consumerMainThread(UnitBatchSyncClientboundPacket::handle)
                .add();
        register(com.solegendary.reignofnether.barfx.BarFxClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.solegendary.reignofnether.barfx.BarFxClientboundPacket::encode)
                .decoder(com.solegendary.reignofnether.barfx.BarFxClientboundPacket::new)
                .consumerMainThread(com.solegendary.reignofnether.barfx.BarFxClientboundPacket::handle)
                .add();
        register(com.solegendary.reignofnether.startpos.CapturePointsClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.solegendary.reignofnether.startpos.CapturePointsClientboundPacket::encode)
                .decoder(com.solegendary.reignofnether.startpos.CapturePointsClientboundPacket::decode)
                .consumerMainThread(com.solegendary.reignofnether.startpos.CapturePointsClientboundPacket::handle)
                .add();
        // BAR command queue lines: the owner's units' shift-queues, sent only when they change (UnitQueueSync)
        register(com.solegendary.reignofnether.unit.packets.UnitQueueClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.solegendary.reignofnether.unit.packets.UnitQueueClientboundPacket::encode)
                .decoder(com.solegendary.reignofnether.unit.packets.UnitQueueClientboundPacket::decode)
                .consumerMainThread(com.solegendary.reignofnether.unit.packets.UnitQueueClientboundPacket::handle)
                .add();
        // BAR area reclaim / repair / attack circle (AreaCommandClientEvents -> AreaCommands)
        register(com.solegendary.reignofnether.unit.packets.AreaCommandServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.solegendary.reignofnether.unit.packets.AreaCommandServerboundPacket::encode)
                .decoder(com.solegendary.reignofnether.unit.packets.AreaCommandServerboundPacket::new)
                .consumerMainThread(com.solegendary.reignofnether.unit.packets.AreaCommandServerboundPacket::handle)
                .add();
        // BAR player list: roster + allied income / commander health every 2 s (PlayerPanelServerEvents)
        register(com.solegendary.reignofnether.player.PlayerPanelClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.solegendary.reignofnether.player.PlayerPanelClientboundPacket::encode)
                .decoder(com.solegendary.reignofnether.player.PlayerPanelClientboundPacket::decode)
                .consumerMainThread(com.solegendary.reignofnether.player.PlayerPanelClientboundPacket::handle)
                .add();
        // BAR map drawing: Alt-drag lines and Alt-double-click labels, rate-limited and relayed to allies only
        register(MapDrawServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MapDrawServerboundPacket::encode)
                .decoder(MapDrawServerboundPacket::new)
                .consumerMainThread(MapDrawServerboundPacket::handle)
                .add();
        register(MapDrawClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MapDrawClientboundPacket::encode)
                .decoder(MapDrawClientboundPacket::new)
                .consumerMainThread(MapDrawClientboundPacket::handle)
                .add();
        // Stormcallers Mana: toggle the sender's own Overcharge (state comes back in EconomyClientboundPacket)
        register(com.solegendary.reignofnether.mana.ManaServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.solegendary.reignofnether.mana.ManaServerboundPacket::encode)
                .decoder(com.solegendary.reignofnether.mana.ManaServerboundPacket::new)
                .consumerMainThread(com.solegendary.reignofnether.mana.ManaServerboundPacket::handle)
                .add();
        // BAR HUD pass 4: per-unit kill counters (once a second), the kill feed (every 5 ticks, vision-gated) and the
        // wreck metal for the reclaim labels (once a second, changed heaps only)
        register(com.solegendary.reignofnether.unit.packets.UnitKillsClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.solegendary.reignofnether.unit.packets.UnitKillsClientboundPacket::encode)
                .decoder(com.solegendary.reignofnether.unit.packets.UnitKillsClientboundPacket::new)
                .consumerMainThread(com.solegendary.reignofnether.unit.packets.UnitKillsClientboundPacket::handle)
                .add();
        // combat reactions (claude/combat-reactions.md): a brace on a unit, for the clients tracking it
        register(com.solegendary.reignofnether.unit.reactions.ReactionClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.solegendary.reignofnether.unit.reactions.ReactionClientboundPacket::encode)
                .decoder(com.solegendary.reignofnether.unit.reactions.ReactionClientboundPacket::new)
                .consumerMainThread(com.solegendary.reignofnether.unit.reactions.ReactionClientboundPacket::handle)
                .add();
        register(com.solegendary.reignofnether.resources.WreckSyncClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.solegendary.reignofnether.resources.WreckSyncClientboundPacket::encode)
                .decoder(com.solegendary.reignofnether.resources.WreckSyncClientboundPacket::new)
                .consumerMainThread(com.solegendary.reignofnether.resources.WreckSyncClientboundPacket::handle)
                .add();
        // hover card of a building you don't own: what it is producing, only if you can see it (BuildingHoverInfo)
        register(com.solegendary.reignofnether.building.BuildingHoverServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.solegendary.reignofnether.building.BuildingHoverServerboundPacket::encode)
                .decoder(com.solegendary.reignofnether.building.BuildingHoverServerboundPacket::new)
                .consumerMainThread(com.solegendary.reignofnether.building.BuildingHoverServerboundPacket::handle)
                .add();
        register(com.solegendary.reignofnether.building.BuildingHoverClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.solegendary.reignofnether.building.BuildingHoverClientboundPacket::encode)
                .decoder(com.solegendary.reignofnether.building.BuildingHoverClientboundPacket::new)
                .consumerMainThread(com.solegendary.reignofnether.building.BuildingHoverClientboundPacket::handle)
                .add();
        // the multiplayer skirmish lobby (MultiplayerLobby, design/multiplayer.md): clicks up, the whole lobby down
        register(com.solegendary.reignofnether.matchstart.LobbyServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.solegendary.reignofnether.matchstart.LobbyServerboundPacket::encode)
                .decoder(com.solegendary.reignofnether.matchstart.LobbyServerboundPacket::new)
                .consumerMainThread(com.solegendary.reignofnether.matchstart.LobbyServerboundPacket::handle)
                .add();
        register(com.solegendary.reignofnether.matchstart.LobbyClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.solegendary.reignofnether.matchstart.LobbyClientboundPacket::encode)
                .decoder(com.solegendary.reignofnether.matchstart.LobbyClientboundPacket::new)
                .consumerMainThread(com.solegendary.reignofnether.matchstart.LobbyClientboundPacket::handle)
                .add();
        // BAR multi-building command card: one click on N selected buildings (round-robin queue, refit each, repeat)
        register(com.solegendary.reignofnether.building.BuildingBatchServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.solegendary.reignofnether.building.BuildingBatchServerboundPacket::encode)
                .decoder(com.solegendary.reignofnether.building.BuildingBatchServerboundPacket::new)
                .consumerMainThread(com.solegendary.reignofnether.building.BuildingBatchServerboundPacket::handle)
                .add();
        // pre-made maps and BAR's start-position phase (design/maps.md): map state down, start clicks up
        register(com.solegendary.reignofnether.maps.MapStateClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.solegendary.reignofnether.maps.MapStateClientboundPacket::encode)
                .decoder(com.solegendary.reignofnether.maps.MapStateClientboundPacket::new)
                .consumerMainThread(com.solegendary.reignofnether.maps.MapStateClientboundPacket::handle)
                .add();
        register(com.solegendary.reignofnether.maps.StartPhaseServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.solegendary.reignofnether.maps.StartPhaseServerboundPacket::encode)
                .decoder(com.solegendary.reignofnether.maps.StartPhaseServerboundPacket::new)
                .consumerMainThread(com.solegendary.reignofnether.maps.StartPhaseServerboundPacket::handle)
                .add();
        // hero mode: possession of a unit or the team hero (design/hero_mode.md) - claims and input up, state down
        register(com.solegendary.reignofnether.hero.PossessionServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.solegendary.reignofnether.hero.PossessionServerboundPacket::encode)
                .decoder(com.solegendary.reignofnether.hero.PossessionServerboundPacket::new)
                .consumerMainThread(com.solegendary.reignofnether.hero.PossessionServerboundPacket::handle)
                .add();
        register(com.solegendary.reignofnether.hero.PossessionClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.solegendary.reignofnether.hero.PossessionClientboundPacket::encode)
                .decoder(com.solegendary.reignofnether.hero.PossessionClientboundPacket::new)
                .consumerMainThread(com.solegendary.reignofnether.hero.PossessionClientboundPacket::handle)
                .add();
        // the First Battle tutorial (onboarding/, design/onboarding.md): start / report / skip / quit up, the step down
        register(com.solegendary.reignofnether.onboarding.OnboardingServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.solegendary.reignofnether.onboarding.OnboardingServerboundPacket::encode)
                .decoder(com.solegendary.reignofnether.onboarding.OnboardingServerboundPacket::new)
                .consumerMainThread(com.solegendary.reignofnether.onboarding.OnboardingServerboundPacket::handle)
                .add();
        register(com.solegendary.reignofnether.onboarding.OnboardingClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.solegendary.reignofnether.onboarding.OnboardingClientboundPacket::encode)
                .decoder(com.solegendary.reignofnether.onboarding.OnboardingClientboundPacket::new)
                .consumerMainThread(com.solegendary.reignofnether.onboarding.OnboardingClientboundPacket::handle)
                .add();
        // the economy bar's sliders (converter threshold, metal share level): the sender's own economy only
        register(com.solegendary.reignofnether.resources.EconomySliderServerboundPacket.class, index++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.solegendary.reignofnether.resources.EconomySliderServerboundPacket::encode)
                .decoder(com.solegendary.reignofnether.resources.EconomySliderServerboundPacket::new)
                .consumerMainThread(com.solegendary.reignofnether.resources.EconomySliderServerboundPacket::handle)
                .add();
        // a world that remembers (worldmemory/): the ground memory field's deltas and snapshots, per player, per
        // sync pass, only for the chunk columns the player's fog shows (GroundMemorySync rules)
        register(com.solegendary.reignofnether.worldmemory.GroundMemoryClientboundPacket.class, index++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.solegendary.reignofnether.worldmemory.GroundMemoryClientboundPacket::encode)
                .decoder(com.solegendary.reignofnether.worldmemory.GroundMemoryClientboundPacket::new)
                .consumerMainThread(com.solegendary.reignofnether.worldmemory.GroundMemoryClientboundPacket::handle)
                .add();

        // a packet registered with INSTANCE.messageBuilder directly would be missing from the hash: two jars that
        // differ in it would connect again. Fail at startup (every GameTest run) instead.
        if (TABLE.size() != index)
            throw new IllegalStateException("PacketHandler: " + index + " packets registered but " + TABLE.size()
                + " in the protocol table - register every packet with register(...), not INSTANCE.messageBuilder");
        protocolVersion = PacketTableHash.protocolVersion(MOD_VERSION, TABLE);
        ReignOfNether.LOGGER.info("[Network] protocol {} ({} packets)", protocolVersion, index);
    }
}

