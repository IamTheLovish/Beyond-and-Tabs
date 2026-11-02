package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.network.PacketTableHash;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.ServerStatusPing;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The live channel's handshake version is made from the live packet table (security audit F3, Oct 26): the real
 * PacketHandler registered every packet through its table, in index order, and the version Forge compares is the mod
 * version plus that table's hash. The hash rule itself is pinned by the JUnit PacketTableHashTest.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class ProtocolVersionGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    @GameTest(template = ARENA, batch = "protocol_version", timeoutTicks = 20)
    public static void the_handshake_version_is_the_hash_of_the_registered_packet_table(GameTestHelper helper) {
        List<String> table = PacketHandler.table();
        String version = PacketHandler.protocolVersion();
        List<String> problems = new ArrayList<>();
        if (table.size() < 100)
            problems.add("only " + table.size() + " packets in the table (main has 100)");
        for (int i = 0; i < table.size(); i++)
            if (!table.get(i).startsWith(i + ":"))
                problems.add("entry " + i + " is out of order: " + table.get(i));
        String unitAction = UnitActionServerboundPacket.class.getName();
        if (table.stream().noneMatch(e -> e.contains(":" + unitAction + ":PLAY_TO_SERVER")))
            problems.add("UnitActionServerboundPacket is not in the table as PLAY_TO_SERVER");
        if (!version.endsWith("+packets." + PacketTableHash.hash(table)))
            problems.add("the handshake version '" + version + "' is not made from the table's hash " + PacketTableHash.hash(table));
        if (!version.equals(PacketHandler.protocolVersion()) || !PacketTableHash.hash(table).equals(PacketTableHash.hash(PacketHandler.table())))
            problems.add("the version changed between two reads");
        TerrainGameTests.finish(helper, problems);
    }

    /**
     * What Forge itself advertises for reignofnether:main (the server list ping and the login handshake read the same
     * NetworkInstance version, captured once when the channel was created) is PacketHandler's table version, and this
     * jar accepts it. The channel used to be made in PacketHandler's static initialiser, before init() computed the
     * hash, so Forge advertised "...+packets.unregistered" while the accept predicates wanted the hash: every client
     * rejected every server, single player included.
     */
    @GameTest(template = ARENA, batch = "protocol_version", timeoutTicks = 20)
    public static void the_channel_advertises_the_table_version_and_accepts_it(GameTestHelper helper) {
        List<String> problems = new ArrayList<>();
        ResourceLocation main = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "main");
        Map<ResourceLocation, ServerStatusPing.ChannelData> channels = new ServerStatusPing().getRemoteChannels();
        ServerStatusPing.ChannelData data = channels.get(main);
        String expected = PacketHandler.protocolVersion();
        if (data == null)
            problems.add("Forge advertises no channel " + main + " (channels: " + channels.keySet() + ")");
        else if (!expected.equals(data.version()))
            problems.add("Forge advertises '" + data.version() + "' for " + main + " but PacketHandler's version is '"
                + expected + "'");
        if (expected.endsWith("+packets.unregistered"))
            problems.add("PacketHandler's version was never computed: " + expected);
        if (!NetworkRegistry.checkListPingCompatibilityForClient(channels))
            problems.add("this jar rejects its own server's channel list (Forge logs which channel)");
        TerrainGameTests.finish(helper, problems);
    }
}
