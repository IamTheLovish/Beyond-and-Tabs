package com.solegendary.reignofnether.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.barfx.BarFx;
import com.solegendary.reignofnether.registrars.SoundRegistrar;
import com.solegendary.reignofnether.sounds.Sfx;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The audio pass (design/audio.md): every sound event the code registers has a sounds.json entry, every file a
 * sounds.json entry names ships in the jar, every Sfx has its own "sfx.&lt;key&gt;" entry, and the server side of
 * Sfx (per-place gap, the SFX event codec, collapse faction packing) behaves. No world needed beyond the arena; no
 * global counts (the per-place gap test uses its own test position and a sound nothing else plays server-side).
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class AudioGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static JsonObject soundsJson() throws Exception {
        try (InputStream in = AudioGameTests.class.getResourceAsStream("/assets/" + ReignOfNether.MOD_ID + "/sounds.json")) {
            if (in == null)
                return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    static boolean ships(String path) {
        try (InputStream in = AudioGameTests.class.getResourceAsStream(path)) {
            return in != null;
        } catch (Exception e) {
            return false;
        }
    }

    @GameTest(template = ARENA)
    public static void sounds_json_covers_every_registered_event(GameTestHelper helper) {
        JsonObject json;
        try {
            json = soundsJson();
        } catch (Exception e) {
            helper.fail("sounds.json does not parse: " + e);
            return;
        }
        if (json == null) {
            helper.fail("sounds.json is not on the classpath");
            return;
        }
        List<String> problems = new ArrayList<>();
        // 1) every event the code registers (SoundRegistrar: named fields, announcer, UI and sfx loops) has an entry
        for (RegistryObject<SoundEvent> ro : SoundRegistrar.SOUND_EVENTS.getEntries()) {
            String id = ro.getId().getPath();
            if (!json.has(id))
                problems.add("no sounds.json entry for registered event " + id);
        }
        // 2) every file an entry names ships (sounds/<path>.ogg); "type": "event" entries point at events, not files
        for (var entry : json.entrySet()) {
            JsonObject def = entry.getValue().getAsJsonObject();
            if (!def.has("sounds"))
                continue;
            for (JsonElement el : def.getAsJsonArray("sounds")) {
                String name;
                if (el.isJsonObject()) {
                    JsonObject o = el.getAsJsonObject();
                    if (o.has("type") && "event".equals(o.get("type").getAsString()))
                        continue;
                    name = o.get("name").getAsString();
                } else
                    name = el.getAsString();
                int colon = name.indexOf(':');
                String ns = colon < 0 ? "minecraft" : name.substring(0, colon);
                if (!ns.equals(ReignOfNether.MOD_ID))
                    continue;
                String path = "/assets/" + ns + "/sounds/" + name.substring(colon + 1) + ".ogg";
                if (!ships(path))
                    problems.add(entry.getKey() + " names a missing file " + path);
            }
        }
        // 3) every Sfx has its own entry with at least one variation, and the registrar resolves it
        for (Sfx s : Sfx.values()) {
            String id = "sfx." + s.key;
            if (!json.has(id) || json.getAsJsonObject(id).getAsJsonArray("sounds").isEmpty())
                problems.add("Sfx " + s + " has no sounds.json entry " + id);
            if (SoundRegistrar.sfx(s) == null)
                problems.add("Sfx " + s + " is not registered");
        }
        if (!problems.isEmpty()) {
            helper.fail(problems.size() + " audio problems, first: " + String.join("; ", problems.subList(0, Math.min(5, problems.size()))));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void sfx_server_gap_is_per_place(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos here = helper.absolutePos(new BlockPos(2, 2, 2));
        // STARTPOS_TICK is client-only in the game, so nothing else in a parallel test sends it from the server
        Sfx s = Sfx.STARTPOS_TICK;
        double x = Math.floor(here.getX() / 8.0) * 8 + 1, z = Math.floor(here.getZ() / 8.0) * 8 + 1;   // inside one cell
        boolean first = s.play(level, x, here.getY(), z, 1f, 1f);
        boolean again = s.play(level, x + 5, here.getY(), z + 5, 1f, 1f);   // same 8-block cell
        boolean elsewhere = s.play(level, x + 40, here.getY(), z, 1f, 1f);
        if (!first || again || !elsewhere) {
            helper.fail("per-place gap: first " + first + " (want true), same cell " + again + " (want false), 40 blocks away "
                + elsewhere + " (want true)");
            return;
        }
        if (s.play(null, 0, 0, 0, 1f, 1f)) {
            helper.fail("a null level must not queue a sound");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void sfx_event_survives_the_barfx_codec(GameTestHelper helper) {
        // the SFX event carries the Sfx ordinal in the kind byte: the last one must come back unsigned and intact
        Sfx last = Sfx.values()[Sfx.values().length - 1];
        var sent = List.of(new BarFx.Event(BarFx.SFX, (byte) last.ordinal(), BarFx.SFX_GLOBAL, 1, 2, 3, 0.7f, 1.2f, 0));
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        new com.solegendary.reignofnether.barfx.BarFxClientboundPacket(sent).encode(buf);
        var got = new com.solegendary.reignofnether.barfx.BarFxClientboundPacket(buf).events();
        if (got.size() != 1 || Sfx.byId(got.get(0).kind & 0xFF) != last || got.get(0).flags != BarFx.SFX_GLOBAL
            || got.get(0).a != 0.7f || got.get(0).b != 1.2f) {
            helper.fail("SFX event did not round-trip: " + got.size() + " events");
            return;
        }
        // collapse flags: bit 0 = no shake, bits 1-3 = faction; every faction must read back, with either shake
        for (byte f = BarFx.F_NONE; f <= BarFx.F_BRASS; f++) {
            for (int shake = 0; shake < 2; shake++) {
                byte flags = (byte) (shake | ((f & 7) << 1));
                if (BarFx.collapseFaction(flags) != f) {
                    helper.fail("collapse faction " + f + " packed with shake " + shake + " read back as " + BarFx.collapseFaction(flags));
                    return;
                }
            }
            if (f > BarFx.F_NONE && (Sfx.destroyed(f) == null || Sfx.built(f) == null || Sfx.death(f) == null || Sfx.stinger(f) == null)) {
                helper.fail("faction " + f + " lacks a built/destroyed/death/stinger sound");
                return;
            }
        }
        helper.succeed();
    }
}
