package com.solegendary.reignofnether.research;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every research item registered in ProductionItems has the lang entry ResearchClient.addResearch reads for the
 * "Upgrade completed: %s" notice: research.reignofnether.<registry path>. Oct 19 playtest (C1, BAC-018): the Tier 3
 * notice showed the raw key research.reignofnether.research_tier_3. No Minecraft types: the registry is read from the
 * source, the same way TooltipFormatTest reads it.
 */
class ResearchLangKeysTest {

    private static final Pattern REGISTER = Pattern.compile(
        "register\\(ResourceLocation\\.fromNamespaceAndPath\\(ReignOfNether\\.MOD_ID, \"([a-z0-9_]+)\"\\),\\s*new\\s+([A-Za-z0-9_.]+)\\(");

    private static Path src(String p) {
        Path a = Path.of(p);
        return Files.exists(a) ? a : Path.of("bar/" + p);   // run from bar/ (Gradle) or the repo root
    }

    private static String lang() throws IOException {
        try (InputStream in = ResearchLangKeysTest.class.getResourceAsStream("/assets/reignofnether/lang/en_us.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Registry paths of the production items whose class lives in research/researchItems. */
    static List<String> researchIds() throws IOException {
        Set<String> classes = new TreeSet<>();
        try (Stream<Path> files = Files.list(src("src/main/java/com/solegendary/reignofnether/research/researchItems"))) {
            files.forEach(f -> classes.add(f.getFileName().toString().replace(".java", "")));
        }
        String items = Files.readString(src("src/main/java/com/solegendary/reignofnether/building/production/ProductionItems.java"));
        List<String> ids = new ArrayList<>();
        Matcher m = REGISTER.matcher(items);
        while (m.find()) {
            String cls = m.group(2).substring(m.group(2).lastIndexOf('.') + 1);
            if (classes.contains(cls))
                ids.add(m.group(1));
        }
        return ids;
    }

    @Test
    void theRegistryReadFindsTheResearchItems() throws IOException {
        List<String> ids = researchIds();
        assertTrue(ids.size() >= 20, "only " + ids.size() + " research items found: " + ids);
        assertTrue(ids.contains("research_tier_3"), ids.toString());
    }

    @Test
    void everyResearchItemHasItsCompletedNoticeName() throws IOException {
        String lang = lang();
        List<String> missing = new ArrayList<>();
        for (String id : researchIds())
            if (!lang.contains("\"research.reignofnether." + id + "\""))
                missing.add("research.reignofnether." + id);
        assertEquals(List.of(), missing, "en_us.json lacks these names (shown raw in 'Upgrade completed: %s')");
    }
}
