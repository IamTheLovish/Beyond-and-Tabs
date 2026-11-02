package com.solegendary.reignofnether.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plan 014, the save hitch: DimensionDataStorage.save() writes every dirty saved-data file synchronously, so a call
 * to it on the server thread mid-match is a hitch (Oct 26 review: 13 call sites, three of them on 30 s timers). The
 * one place allowed to call it is persistence/RtsSaves, for the server stopping and an explicit scenario save. This
 * test keeps a new one from creeping back in; game tests may still write the world on purpose.
 */
class RtsSavesSourceTest {

    private static final Pattern FULL_WRITE = Pattern.compile("getDataStorage\\(\\)\\s*\\.\\s*save\\(\\s*\\)");
    private static final Pattern TIMER = Pattern.compile("SAVE_TICKS_MAX");

    @Test
    void only_RtsSaves_writes_all_saved_data_at_once() throws IOException {
        List<String> bad = scan(FULL_WRITE);
        assertTrue(bad.isEmpty(), "synchronous full saved-data writes outside persistence/RtsSaves: " + bad);
    }

    @Test
    void no_periodic_save_timer_is_left() throws IOException {
        List<String> bad = scan(TIMER);
        assertTrue(bad.isEmpty(), "periodic save timers (Minecraft's world save writes RTS data now): " + bad);
    }

    private static List<String> scan(Pattern pattern) throws IOException {
        List<String> bad = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Paths.get("src/main/java"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String unix = p.toString().replace('\\', '/');
                if (unix.endsWith("/persistence/RtsSaves.java") || unix.contains("/gametest/"))
                    continue;
                String[] lines = Files.readString(p, StandardCharsets.UTF_8).split("\n");
                for (int i = 0; i < lines.length; i++) {
                    String code = lines[i].replaceFirst("//.*$", "");
                    Matcher m = pattern.matcher(code);
                    if (m.find())
                        bad.add(p.getFileName() + ":" + (i + 1));
                }
            }
        }
        return bad;
    }
}
