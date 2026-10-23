package com.solegendary.reignofnether.gametest;

import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.hud.custombutton.CustomButton;
import com.solegendary.reignofnether.hud.custombutton.CustomButtonActionServerboundPacket;
import com.solegendary.reignofnether.hud.custombutton.CustomButtonActions;
import com.solegendary.reignofnether.hud.custombutton.CustomButtonServerEvents;
import com.solegendary.reignofnether.network.PacketAuth;
import com.solegendary.reignofnether.player.PlayerAction;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.PlayerServerboundPacket;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.sandbox.SandboxServer;
import com.solegendary.reignofnether.startpos.BattlefieldSetup;
import com.solegendary.reignofnether.startpos.StartPos;
import com.solegendary.reignofnether.startpos.StartPosAction;
import com.solegendary.reignofnether.startpos.StartPosServerEvents;
import com.solegendary.reignofnether.startpos.StartPosServerboundPacket;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Multiplayer security (design/multiplayer.md): what a joined, non-host player's packets can and cannot do. Each test
 * plays a modified client: it hands the server the packet the real client would never send, through the same
 * {@code accept(sender)} the network handler calls. Tests that touch match-wide state (the RTS roster, the start
 * slots, the custom-button table) each have a batch of their own, so none runs beside another.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class SandboxSecurityGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    // ------------------------------------------------------------------------------------------ helpers

    private static ServerPlayer fake(ServerLevel level, String name) {
        return FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, path);
    }

    /** The faction keys a modified client may put in a start packet: the sandbox "faction", neutral, and a key no faction has. */
    private static Map<String, ResourceLocation> forbiddenFactionKeys() {
        Map<String, ResourceLocation> keys = new LinkedHashMap<>();
        keys.put("faction none", Factions.NONE.key);
        keys.put("faction neutral", Factions.NEUTRAL.key);
        keys.put("an unknown faction key", rl("no_such_faction_at_all"));
        return keys;
    }

    /** START_RTS exactly as it is on the wire, decoded the way the server decodes it. */
    private static PlayerServerboundPacket startRtsPacket(ServerPlayer sender, ResourceLocation factionKey, BlockPos at) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeEnum(PlayerAction.START_RTS);
        buf.writeResourceLocation(factionKey);
        buf.writeInt(sender.getId());
        buf.writeDouble(at.getX() + 0.5);
        buf.writeDouble(at.getY());
        buf.writeDouble(at.getZ() + 0.5);
        return new PlayerServerboundPacket(buf);
    }

    /** Takes every trace of a player's start out of the world: roster, resources, units, a queued battlefield. */
    private static void forgetStart(String name) {
        MatchLifecycleCases.forget(name);
        PlayerServerEvents.startedAtTick.remove(name);
        for (LivingEntity e : new ArrayList<>(UnitServerEvents.getAllUnits()))
            if (e instanceof Unit u && name.equals(u.getOwnerName()))
                e.discard();
        BattlefieldSetup.discardPending();
        PlayerServerEvents.saveRTSPlayers();
    }

    // ------------------------------------------------------------------------------------------ (a) START_RTS

    /**
     * Sandbox is the host's: START_RTS_SANDBOX is host-only, but START_RTS carried the faction in the packet and
     * decoded "none", "neutral" and any unknown key into a sandbox (NONE) player - whom every owner check lets act
     * for everyone. A joiner who is not the host/an op must never become an RTS player of one of those.
     */
    @GameTest(template = ARENA, batch = "sandbox_sec_start", timeoutTicks = 40)
    public static void a_joiner_cannot_start_as_a_sandbox_player_through_start_rts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String name = "gt_sec_joiner";
        ServerPlayer joiner = fake(level, name);
        PlayerServerEvents.players.add(joiner);
        BlockPos at = helper.absolutePos(new BlockPos(8, 2, 8));
        List<String> problems = new ArrayList<>();
        try {
            if (PacketAuth.isHostOrOp(joiner, 2)) {
                helper.fail("test setup: the fake joiner is a host or an op");
                return;
            }
            for (Map.Entry<String, ResourceLocation> key : forbiddenFactionKeys().entrySet()) {
                try {
                    startRtsPacket(joiner, key.getValue(), at).accept(joiner);
                } catch (RuntimeException e) {
                    problems.add(key.getKey() + ": the start threw " + e);
                }
                if (SandboxServer.isSandboxPlayer(name))
                    problems.add(key.getKey() + ": the joiner became a sandbox player");
                if (PlayerServerEvents.isRTSPlayer(name))
                    problems.add(key.getKey() + ": the joiner was put into play");
                forgetStart(name);
                PacketAuth.forget(joiner.getUUID());
            }
        } finally {
            forgetStart(name);
            PlayerServerEvents.players.remove(joiner);
            PacketAuth.forget(joiner.getUUID());
        }
        TerrainGameTests.finish(helper, problems);
    }

    // ------------------------------------------------------------------------------------------ (b) RESERVE

    /** The same hole through the start-position block: a RESERVE for "none"/"neutral"/unknown by a joiner is refused. */
    @GameTest(template = ARENA, batch = "sandbox_sec_startpos", timeoutTicks = 40)
    public static void a_joiner_cannot_reserve_a_start_position_as_none(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String name = "gt_sec_reserver";
        ServerPlayer joiner = fake(level, name);
        BlockPos slot = helper.absolutePos(new BlockPos(8, 2, 8));
        StartPos startPos = new StartPos(slot, 1);
        StartPosServerEvents.startPoses.add(startPos);
        List<String> problems = new ArrayList<>();
        try {
            if (PacketAuth.isHostOrOp(joiner, 2)) {
                helper.fail("test setup: the fake joiner is a host or an op");
                return;
            }
            // control: an honest reservation of a real faction by the same joiner still works
            reserve(joiner, Factions.VILLAGERS.key, slot);
            if (!name.equals(startPos.playerName) || startPos.faction != Factions.VILLAGERS)
                problems.add("control: a joiner's reservation of a playable faction was refused (player '" + startPos.playerName
                    + "', faction " + startPos.faction.key + ")");
            startPos.reset();
            for (Map.Entry<String, ResourceLocation> key : forbiddenFactionKeys().entrySet()) {
                try {
                    reserve(joiner, key.getValue(), slot);
                } catch (RuntimeException e) {
                    problems.add(key.getKey() + ": the reservation threw " + e);
                }
                if (!startPos.playerName.isEmpty() || startPos.faction != Factions.NONE)
                    problems.add(key.getKey() + ": the joiner reserved the start position (player '" + startPos.playerName
                        + "', faction " + startPos.faction.key + ")");
                startPos.reset();
                PacketAuth.forget(joiner.getUUID());
            }
        } finally {
            StartPosServerEvents.startPoses.remove(startPos);
            PacketAuth.forget(joiner.getUUID());
        }
        TerrainGameTests.finish(helper, problems);
    }

    private static void reserve(ServerPlayer sender, ResourceLocation factionKey, BlockPos slot) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeEnum(StartPosAction.RESERVE);
        buf.writeBlockPos(slot);
        buf.writeResourceLocation(factionKey);
        buf.writeUtf(sender.getName().getString());
        new StartPosServerboundPacket(buf).accept(sender);
    }

    // ------------------------------------------------------------------------------------------ (c) placement checks

    /**
     * "Anyone is a sandbox player" switched placement validation off for everybody: while the host sat in sandbox, a
     * normal player's foundation could overlap another building. The check follows the PLACING owner, as the auth
     * check of BuildingServerboundPacket already does; the sandbox player keeps their freedom.
     */
    @GameTest(template = ARENA, batch = "sandbox_sec_place", timeoutTicks = 60)
    public static void a_sandbox_host_does_not_switch_off_other_players_placement_checks(GameTestHelper helper) {
        String normal = "gt_sec_builder", host = "gt_sec_host";
        BlockPos c = MatchLifecycleCases.pad(helper, 284);
        int y = c.getY();
        MatchLifecycleCases.side(normal, 1_100_501);
        ResourcesServerEvents.assignResources(normal);
        var wind = Buildings.WIND_GENERATOR_VILLAGERS;
        List<BuildingPlacement> placed = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        try {
            BlockPos first = new BlockPos(c.getX() - 4, y, c.getZ() - 4), next = first.east();
            BuildingPlacement a = BuildingServerEvents.placeBuilding(wind, first, Rotation.NONE, normal, new int[0], false, false, true, true);
            if (a == null) {
                helper.fail("test setup: could not place the first building");
                return;
            }
            placed.add(a);
            // control, nobody in sandbox: a foundation one block over overlaps the first and is refused
            BuildingPlacement b = BuildingServerEvents.placeBuilding(wind, next, Rotation.NONE, normal, new int[0], false, false, false, true);
            if (b != null) {
                placed.add(b);
                helper.fail("test setup: an overlapping foundation was accepted even with no sandbox player");
                return;
            }
            // the host starts a sandbox
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewPlayer(host, Factions.NONE, 1_100_502, 0, true, -1L));
            if (!SandboxServer.isSandboxPlayer(host) || SandboxServer.isSandboxPlayer(normal)) {
                helper.fail("test setup: the sandbox roster is wrong");
                return;
            }
            BuildingPlacement b2 = BuildingServerEvents.placeBuilding(wind, next, Rotation.NONE, normal, new int[0], false, false, false, true);
            if (b2 != null) {
                placed.add(b2);
                problems.add("with the host in sandbox, a normal player's overlapping foundation was accepted");
            }
            // the sandbox player themself still places freely (overlap allowed)
            BuildingPlacement b3 = BuildingServerEvents.placeBuilding(wind, new BlockPos(c.getX() + 3, y, c.getZ() + 3),
                Rotation.NONE, host, new int[0], false, false, true, true);
            if (b3 != null)
                placed.add(b3);
            BuildingPlacement b4 = b3 == null ? null : BuildingServerEvents.placeBuilding(wind, new BlockPos(c.getX() + 4, y, c.getZ() + 3),
                Rotation.NONE, host, new int[0], false, false, false, true);
            if (b4 != null)
                placed.add(b4);
            else
                problems.add("the sandbox player's own overlapping placement was refused (sandbox lost its freedom)");
        } finally {
            for (BuildingPlacement p : placed)
                BuildingServerEvents.getBuildings().remove(p);
            MatchLifecycleCases.forget(normal, host);
        }
        TerrainGameTests.finish(helper, problems);
    }

    // ------------------------------------------------------------------------------------------ (d) TELEPORT

    private static PlayerServerboundPacket teleport(ServerPlayer sender, double x, double y, double z) {
        return new PlayerServerboundPacket(PlayerAction.TELEPORT, sender.getId(), x, y, z);
    }

    /**
     * TELEPORT moves the sender's camera body, and used to take the raw doubles from the packet (only an ortho-view
     * player was clamped): NaN, infinity or 1e9 put any creative/spectator joiner outside the world or into the void.
     */
    @GameTest(template = ARENA, batch = "sandbox_sec_teleport", timeoutTicks = 40)
    public static void teleport_never_leaves_the_world_border_or_the_height_range(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String name = "gt_sec_tele";
        ServerPlayer p = fake(level, name);
        p.setGameMode(GameType.CREATIVE);
        PlayerServerEvents.players.add(p);
        BlockPos home = helper.absolutePos(new BlockPos(8, 2, 8));
        double hx = home.getX() + 0.5, hy = home.getY(), hz = home.getZ() + 0.5;
        WorldBorder border = level.getWorldBorder();
        List<String> problems = new ArrayList<>();
        try {
            double[][] bad = {
                { Double.NaN, hy, hz }, { hx, Double.NaN, hz }, { hx, hy, Double.NaN },
                { Double.POSITIVE_INFINITY, hy, hz }, { hx, hy, Double.NEGATIVE_INFINITY },
                { 1e9, hy, hz }, { hx, hy, -1e9 }, { hx, 1e9, hz }, { hx, -1e9, hz },
            };
            for (double[] t : bad) {
                p.setPos(hx, hy, hz);
                PacketAuth.forget(p.getUUID());
                String what = "teleport to [" + t[0] + ", " + t[1] + ", " + t[2] + "]";
                try {
                    teleport(p, t[0], t[1], t[2]).accept(p);
                } catch (RuntimeException e) {
                    problems.add(what + " threw " + e);
                }
                double x = p.getX(), py = p.getY(), z = p.getZ();
                if (!Double.isFinite(x) || !Double.isFinite(py) || !Double.isFinite(z))
                    problems.add(what + " left the player at a non-finite position [" + x + ", " + py + ", " + z + "]");
                else if (x < border.getMinX() || x > border.getMaxX() || z < border.getMinZ() || z > border.getMaxZ())
                    problems.add(what + " put the player outside the world border at [" + x + ", " + z + "]");
                else if (py < level.getMinBuildHeight() || py > level.getMaxBuildHeight() + 64)
                    problems.add(what + " put the player at height " + py + ", outside the world's height range");
            }
            // control: an honest jump inside the world still goes where it was asked
            p.setPos(hx, hy, hz);
            PacketAuth.forget(p.getUUID());
            teleport(p, hx + 3, hy + 5, hz - 2).accept(p);
            if (Math.abs(p.getX() - (hx + 3)) > 0.01 || Math.abs(p.getY() - (hy + 5)) > 0.01 || Math.abs(p.getZ() - (hz - 2)) > 0.01)
                problems.add("control: an honest teleport did not land where asked: [" + p.getX() + ", " + p.getY() + ", " + p.getZ() + "]");
        } finally {
            p.setPos(hx, hy, hz);
            PlayerServerEvents.players.remove(p);
            PacketAuth.forget(p.getUUID());
        }
        TerrainGameTests.finish(helper, problems);
    }

    /** TELEPORT was exempt from the packet rate limit: a modified client could send thousands a second. */
    @GameTest(template = ARENA, batch = "sandbox_sec_teleport_flood", timeoutTicks = 40)
    public static void a_teleport_flood_is_rate_limited(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String name = "gt_sec_flood";
        ServerPlayer p = fake(level, name);
        p.setGameMode(GameType.CREATIVE);
        PlayerServerEvents.players.add(p);
        BlockPos home = helper.absolutePos(new BlockPos(8, 2, 8));
        double hx = home.getX() + 0.5, hy = home.getY(), hz = home.getZ() + 0.5;
        List<String> problems = new ArrayList<>();
        try {
            PacketAuth.forget(p.getUUID());
            int n = 600;
            double lastX = hx;
            for (int i = 0; i < n; i++) {
                lastX = hx + 0.01 * (i + 1);   // every packet names its own spot
                teleport(p, lastX, hy, hz).accept(p);
            }
            if (Math.abs(p.getX() - lastX) < 1e-6)
                problems.add("all " + n + " teleport packets sent in one instant were processed: no rate limit");
        } finally {
            p.setPos(hx, hy, hz);
            PlayerServerEvents.players.remove(p);
            PacketAuth.forget(p.getUUID());
        }
        TerrainGameTests.finish(helper, problems);
    }

    // ------------------------------------------------------------------------------------------ (e) custom buttons

    /** A button action that only records that it ran. */
    private record Recording(List<String> log, String tag) implements CustomButtonActions.CustomButtonAction {
        @Override
        public com.mojang.serialization.Codec<? extends CustomButtonActions.CustomButtonAction> codec() {
            return null;
        }

        @Override
        public void execute(Entity entity) {
            log.add(tag);
        }

        @Override
        public void execute(BuildingPlacement building) {
            log.add(tag);
        }
    }

    /**
     * A datapack button runs functions at permission level 2, and its packet carried no checks: any joiner (a
     * spectator, someone not in the match) could run them, at any rate, and a right-click ran the LEFT-click actions.
     * Now only a player in the match (or the host/an op) may, rate-limited, and the click side is respected.
     */
    @GameTest(template = ARENA, batch = "sandbox_sec_button", timeoutTicks = 60)
    public static void custom_button_actions_need_a_player_in_the_match_and_respect_the_click_side(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String outsider = "gt_sec_button_out", member = "gt_sec_button_in";
        ServerPlayer out = fake(level, outsider), in = fake(level, member);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("gametest", "sec_button");
        List<String> log = new ArrayList<>();
        CustomButton button = new CustomButton(id, "gt_sec_button", rl("textures/hud/icon_frame.png"), 0, 0,
            List.of(new Recording(log, "left")), List.of(new Recording(log, "right")), false, true);
        CustomButtonServerEvents.customButtons.put(id, button);
        MatchLifecycleCases.side(member, 1_100_511);
        List<String> problems = new ArrayList<>();
        try {
            if (PacketAuth.isHostOrOp(out, 2) || PacketAuth.isHostOrOp(in, 2)) {
                helper.fail("test setup: a fake player is a host or an op");
                return;
            }
            new CustomButtonActionServerboundPacket(id, true).accept(out);
            new CustomButtonActionServerboundPacket(id, false).accept(out);
            if (!log.isEmpty())
                problems.add("a player who is not in the match ran a custom button's actions: " + log);
            log.clear();

            new CustomButtonActionServerboundPacket(id, false).accept(in);
            if (!log.equals(List.of("right")))
                problems.add("a right click on a button should run only its right-click actions, ran " + log);
            log.clear();
            new CustomButtonActionServerboundPacket(id, true).accept(in);
            if (!log.equals(List.of("left")))
                problems.add("a left click on a button should run only its left-click actions, ran " + log);
            log.clear();

            PacketAuth.forget(in.getUUID());
            for (int i = 0; i < 500; i++)
                new CustomButtonActionServerboundPacket(id, true).accept(in);
            if (log.size() >= 500)
                problems.add("500 button clicks in one instant all ran: no rate limit");
        } finally {
            CustomButtonServerEvents.customButtons.remove(id);
            MatchLifecycleCases.forget(member);
            PacketAuth.forget(out.getUUID());
            PacketAuth.forget(in.getUUID());
        }
        TerrainGameTests.finish(helper, problems);
    }
}
