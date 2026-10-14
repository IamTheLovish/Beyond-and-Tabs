package com.solegendary.reignofnether.commands.lang;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The words the command language knows that come from the game rather than the grammar: unit types, buildings,
 * abilities, and the synonyms that map a player's word onto one of them ("anti-air" -> the faction's AA turret).
 * Pure data, so the parser can be tested without a world; the client builds the real one from the registries and
 * the lang file (GameVocabulary), a test builds a small fixed one. Every entry is lower case; a plural is accepted
 * for every unit type ("halberdier" / "halberdiers").
 */
public final class Vocabulary {
    private final Set<String> units = new HashSet<>();
    private final Set<String> buildings = new HashSet<>();
    private final Set<String> abilities = new HashSet<>();
    private final Map<String, String> synonyms = new HashMap<>();   // spoken phrase -> canonical entry
    private final Map<String, String> plurals = new HashMap<>();    // plural -> singular unit type

    public Vocabulary units(Collection<String> names) {
        for (String n : names) {
            String s = norm(n);
            units.add(s);
            plurals.put(plural(s), s);
        }
        return this;
    }

    public Vocabulary buildings(Collection<String> names) {
        for (String n : names)
            buildings.add(norm(n));
        return this;
    }

    public Vocabulary abilities(Collection<String> names) {
        for (String n : names)
            abilities.add(norm(n));
        return this;
    }

    /** {@code spoken} means {@code canonical} (a unit, building or ability already added). */
    public Vocabulary synonym(String spoken, String canonical) {
        synonyms.put(norm(spoken), norm(canonical));
        return this;
    }

    public Set<String> units() { return Collections.unmodifiableSet(units); }
    public Set<String> buildings() { return Collections.unmodifiableSet(buildings); }
    public Set<String> abilities() { return Collections.unmodifiableSet(abilities); }

    /** The canonical unit type a phrase names (singular, plural or synonym), or null. */
    public String unit(String phrase) {
        String s = resolve(phrase);
        if (units.contains(s))
            return s;
        String single = plurals.get(s);
        return single != null && units.contains(single) ? single : null;
    }

    public String building(String phrase) {
        String s = resolve(phrase);
        return buildings.contains(s) ? s : null;
    }

    public String ability(String phrase) {
        String s = resolve(phrase);
        return abilities.contains(s) ? s : null;
    }

    private String resolve(String phrase) {
        String s = norm(phrase);
        return synonyms.getOrDefault(s, s);
    }

    /** Every phrase the vocabulary accepts (for the "did you mean" search and a recogniser's grammar). */
    public List<String> phrases() {
        Set<String> all = new HashSet<>(units);
        all.addAll(plurals.keySet());
        all.addAll(buildings);
        all.addAll(abilities);
        all.addAll(synonyms.keySet());
        return all.stream().sorted().toList();
    }

    static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 \\-]", " ").replaceAll("\\s+", " ").trim();
    }

    static String plural(String s) {
        if (s.endsWith("s") || s.endsWith("x") || s.endsWith("ch") || s.endsWith("sh"))
            return s + "es";
        if (s.endsWith("y") && s.length() > 1 && "aeiou".indexOf(s.charAt(s.length() - 2)) < 0)
            return s.substring(0, s.length() - 1) + "ies";
        if (s.endsWith("man"))
            return s.substring(0, s.length() - 3) + "men";
        return s + "s";
    }
}
