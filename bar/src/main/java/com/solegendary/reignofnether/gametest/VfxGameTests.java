package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.barfx.BarFx;
import com.solegendary.reignofnether.barfx.BarFxClientboundPacket;
import com.solegendary.reignofnether.barfx.vfx.VfxDef;
import com.solegendary.reignofnether.barfx.vfx.VfxIds;
import com.solegendary.reignofnether.barfx.vfx.VfxParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The vfx foundation on the real server (design/vfx.md): the effect definitions and the atlas ship in the jar and
 * parse without a problem there (the JUnit VfxDefTest reads the source tree; this reads what players get), and a VFX
 * event survives the BarFx packet codec exactly - the id hash and the followed entity id are float-exact, the attach
 * mode and the faction ride in their bytes. No world beyond the arena, no global state.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class VfxGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static String read(String path) {
        try (InputStream in = VfxGameTests.class.getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    @GameTest(template = ARENA)
    public static void vfx_definitions_ship_and_parse(GameTestHelper helper) {
        List<String> ids = new ArrayList<>(VfxIds.SHIPPED);
        ids.add("soul_bolt");   // the projectile visual (not suggested by /bat vfx)
        List<String> problems = new ArrayList<>();
        for (String id : ids) {
            String json = read("/assets/" + ReignOfNether.MOD_ID + "/vfx/" + id + ".json");
            if (json == null) { problems.add(id + ".json is not in the jar"); continue; }
            List<String> errors = new ArrayList<>();
            VfxDef def = VfxParser.parse(id, json, errors);
            if (def == null || !errors.isEmpty()) problems.add(id + ": " + errors);
        }
        if (read("/assets/" + ReignOfNether.MOD_ID + "/textures/vfx/vfx_atlas.png") == null)
            problems.add("textures/vfx/vfx_atlas.png is not in the jar (tools/gen_vfx_textures.py)");
        if (!problems.isEmpty()) {
            helper.fail(String.join("; ", problems));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void vfx_event_survives_the_packet_codec(GameTestHelper helper) {
        List<BarFx.Event> sent = new ArrayList<>();
        int[] entityIds = { 0, 1, 4242, (1 << 24) - 1 };
        for (String id : VfxIds.SHIPPED)
            for (int eid : entityIds)
                sent.add(new BarFx.Event(BarFx.VFX, BarFx.VFX_FOLLOW, BarFx.F_SUNFORGED, 10.5f, 64f, -3.25f,
                    VfxIds.hash(id), eid, 1.5f));
        sent.add(new BarFx.Event(BarFx.VFX, BarFx.VFX_AT_POINT, BarFx.F_NONE, 1, 2, 3, VfxIds.hash("sun_pillar"), -1, 0.5f));
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            new BarFxClientboundPacket(sent).encode(buf);
            List<BarFx.Event> got = new BarFxClientboundPacket(buf).events();
            if (got.size() != sent.size()) {
                helper.fail("decoded " + got.size() + " events, sent " + sent.size());
                return;
            }
            for (int i = 0; i < got.size(); i++) {
                BarFx.Event a = sent.get(i), b = got.get(i);
                if (b.type != BarFx.VFX || b.kind != a.kind || b.flags != a.flags
                        || ((int) b.a & VfxIds.MASK) != ((int) a.a & VfxIds.MASK) || (int) b.b != (int) a.b || b.c != a.c) {
                    helper.fail("event " + i + " came back changed: hash " + (int) b.a + " vs " + (int) a.a
                        + ", entity " + (int) b.b + " vs " + (int) a.b + ", attach " + b.kind + " vs " + a.kind);
                    return;
                }
            }
        } finally {
            buf.release();
        }
        helper.succeed();
    }
}
