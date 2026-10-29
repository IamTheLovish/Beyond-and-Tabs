package com.solegendary.reignofnether.hero.wager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.solegendary.reignofnether.ReignOfNether;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Wager Book's keyword table (data/reignofnether/wager/intents.json): about twenty intents with their keywords,
 * the size words that raise or lower the asked tier, and Lovish's lines. A plain JSON file so lovish can edit the
 * vocabulary without touching code; read once from the jar (the server and the GameTests see the same file).
 */
public final class WagerIntents {

    /** Where an intent's phenomenon lands: on the hero, on the aimed spot, or at his feet as a summon. */
    public enum Aim { SELF, SPOT, SUMMON }

    /** One intent: its id, its size (ties go to the smaller ask), its base agree chance, its aim and its keywords. */
    public record Intent(String id, int size, float base, Aim aim, List<String> keywords) { }

    public static final String NONSENSE = "nonsense";
    static final String PATH = "/data/reignofnether/wager/intents.json";

    private static WagerIntents loaded;

    public final List<Intent> intents;
    public final Set<String> sizeUp;
    public final Set<String> sizeDown;
    public final int defaultTier;
    private final Map<String, Intent> byId = new HashMap<>();
    private final Map<String, List<String>> lines;

    WagerIntents(List<Intent> intents, Set<String> sizeUp, Set<String> sizeDown, int defaultTier, Map<String, List<String>> lines) {
        this.intents = Collections.unmodifiableList(intents);
        this.sizeUp = Collections.unmodifiableSet(sizeUp);
        this.sizeDown = Collections.unmodifiableSet(sizeDown);
        this.defaultTier = defaultTier;
        this.lines = lines;
        for (Intent i : intents)
            byId.put(i.id(), i);
        if (!byId.containsKey(NONSENSE))
            throw new IllegalStateException("wager intents: no '" + NONSENSE + "' intent");
    }

    /** The table from the jar, read on first use. A broken file fails loudly: a silent empty table would refuse everything. */
    public static synchronized WagerIntents get() {
        if (loaded == null)
            loaded = load();
        return loaded;
    }

    public Intent byId(String id) { return byId.get(id); }

    public Intent nonsense() { return byId.get(NONSENSE); }

    /** Lovish's lines for one moment (agree, refuse, refuse_low, twist, nonsense, legendary, yap); never empty. */
    public List<String> lines(String key) {
        List<String> l = lines.get(key);
        return l == null || l.isEmpty() ? List.of("...") : l;
    }

    static WagerIntents load() {
        try (InputStream in = WagerIntents.class.getResourceAsStream(PATH)) {
            if (in == null)
                throw new IllegalStateException("wager intents: " + PATH + " is missing from the jar");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            List<Intent> intents = new ArrayList<>();
            for (JsonElement e : root.getAsJsonArray("intents")) {
                JsonObject o = e.getAsJsonObject();
                intents.add(new Intent(o.get("id").getAsString(), o.get("size").getAsInt(), o.get("base").getAsFloat(),
                    Aim.valueOf(o.get("aim").getAsString().toUpperCase(Locale.ROOT)), strings(o.getAsJsonArray("keywords"), true)));
            }
            Map<String, List<String>> lines = new HashMap<>();
            JsonObject l = root.getAsJsonObject("lines");
            for (String key : l.keySet())
                lines.put(key, Collections.unmodifiableList(strings(l.getAsJsonArray(key), false)));
            return new WagerIntents(intents, new HashSet<>(strings(root.getAsJsonArray("size_up"), true)),
                new HashSet<>(strings(root.getAsJsonArray("size_down"), true)), root.get("default_tier").getAsInt(), lines);
        } catch (java.io.IOException ex) {
            ReignOfNether.LOGGER.error("wager intents: could not read {}", PATH, ex);
            throw new IllegalStateException("wager intents: " + PATH, ex);
        }
    }

    /** Keywords are matched lower-case; lines keep the case they were written in. */
    private static List<String> strings(JsonArray a, boolean lower) {
        List<String> out = new ArrayList<>();
        if (a != null)
            for (JsonElement e : a)
                out.add(lower ? e.getAsString().toLowerCase(Locale.ROOT) : e.getAsString());
        return out;
    }
}
