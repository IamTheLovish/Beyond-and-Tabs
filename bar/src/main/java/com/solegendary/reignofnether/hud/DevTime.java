package com.solegendary.reignofnether.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.solegendary.reignofnether.ReignOfNether;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * "Built in ~69 h · 546 commits": the total active development time, shown bottom-right on the title screen (lovish,
 * Oct 12: "show the time it took working on the game all together").
 *
 * The numbers are computed once per build by CI (.github/workflows/build.yml, "Count the development time") from the
 * git commit times - commits under 30 minutes apart count their real gap, and every work session is credited 15
 * minutes before its first commit (claude/dev-time-counter.md) - and baked into the jar as
 * assets/reignofnether/devtime.json. No git or network at runtime; a local build without the file simply shows nothing.
 */
public final class DevTime {
    private DevTime() { }

    private static boolean loaded = false;
    private static String line = null;

    /** The title-screen line, or null when this build carries no dev-time file. */
    public static String line() {
        if (!loaded) {
            loaded = true;
            line = read();
        }
        return line;
    }

    private static String read() {
        try (InputStream in = DevTime.class.getResourceAsStream("/assets/reignofnether/devtime.json")) {
            if (in == null)
                return null;
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            double hours = o.get("hours").getAsDouble();
            int commits = o.has("commits") ? o.get("commits").getAsInt() : 0;
            return format(hours, commits);
        } catch (Exception e) {
            ReignOfNether.LOGGER.warn("[DevTime] could not read devtime.json", e);
            return null;
        }
    }

    /** "Built in ~69 h · 546 commits" (whole hours; commits left out when unknown). Public for the GameTest. */
    public static String format(double hours, int commits) {
        if (!(hours > 0))
            return null;
        String h = String.format(Locale.ROOT, "Built in ~%d h", Math.max(1, Math.round(hours)));
        return commits > 0 ? h + " · " + commits + " commits" : h;
    }
}
