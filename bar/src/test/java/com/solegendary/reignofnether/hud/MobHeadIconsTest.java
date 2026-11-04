package com.solegendary.reignofnether.hud;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * N6 (BAC-015, HANDOFF-lane-c C2): the unit portrait is the unit's mob-head icon (PortraitRendererUnit.iconFor), so an
 * icon copied from another unit shows the wrong unit. Found Nov 4: mossback_bison.png is byte-for-byte stag_lancer.png,
 * so the Mossback Bison's portrait is a stag. The groups below share one image on purpose today (stand-ins borrowed
 * from a related mob); any other shared image is a copy by mistake. A group leaves this list when it gets its own art.
 */
class MobHeadIconsTest {

    private static final Set<Set<String>> KNOWN_SHARED = Set.of(
        Set.of("elder_druid", "thornweaver"), Set.of("tombcaller", "wraith"), Set.of("tombstone", "totem_of_regeneration"),
        Set.of("spark_sage", "wind_monk"), Set.of("mender_angel", "seraph_archer", "sword_seraph", "vex"),
        Set.of("engineer_captain", "soul_engineer"), Set.of("deployed_turret", "ghost_furnace"),
        Set.of("ash_shaman", "horde_warchief"), Set.of("ossuary_behemoth", "zoglin"), Set.of("angel_caller", "evoker"),
        Set.of("armoured_hoglin", "gorehorn_rhino"), Set.of("oriflamme_elephant", "war_mammoth"));

    private static Path dir() {
        Path p = Path.of("src/main/resources/assets/reignofnether/textures/mobheads");
        return Files.exists(p) ? p : Path.of("bar").resolve(p);
    }

    @Test
    void noUnitShowsAnotherUnitsHeadByMistake() throws IOException, NoSuchAlgorithmException {
        Map<String, Set<String>> byHash = new TreeMap<>();
        try (Stream<Path> files = Files.list(dir())) {
            for (Path f : (Iterable<Path>) files.filter(x -> x.toString().endsWith(".png"))::iterator) {
                String h = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f)));
                byHash.computeIfAbsent(h, k -> new TreeSet<>()).add(f.getFileName().toString().replace(".png", ""));
            }
        }
        List<Set<String>> unexpected = new ArrayList<>();
        for (Set<String> group : byHash.values())
            if (group.size() > 1 && !KNOWN_SHARED.contains(group))
                unexpected.add(group);
        assertEquals(List.of(), unexpected, "these icons are the same image: each unit's portrait shows the other unit");
    }
}
