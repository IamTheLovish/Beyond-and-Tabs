package com.solegendary.reignofnether.commands.lang;

import com.solegendary.reignofnether.commands.lang.Intent.Group;
import com.solegendary.reignofnether.commands.lang.Intent.Place;
import com.solegendary.reignofnether.commands.lang.Intent.Verb;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The command language (claude/voice-commands.md, layer 1): a sentence like "idle workers, stop and build anti-air
 * near you" becomes a list of {@link Intent}s the client executes through the same calls the hotkeys use. Grammar:
 * <pre>
 *   sentence := clause (("and" | "then" | ",") clause)*
 *   clause   := [group [","]] verb [object] [place]
 * </pre>
 * A clause without a group takes the previous clause's group (or the current selection); every clause after the
 * first is queued behind it. Every word must be known - a grammar word, or a unit, building or ability from the
 * {@link Vocabulary} - or the whole sentence is refused with the nearest accepted phrase, so nothing is ever
 * guessed. Pure Java: no Minecraft classes, fully testable headless.
 */
public final class CommandLanguage {
    private CommandLanguage() { }

    /** What a sentence parsed to, or why it did not (with a suggestion the player can say instead). */
    public record Result(List<Intent> intents, String error, String suggestion) {
        public boolean ok() { return error == null; }
        static Result of(List<Intent> intents) { return new Result(intents, null, null); }
        static Result fail(String error, String suggestion) { return new Result(List.of(), error, suggestion); }
    }

    // ---- phrase tables (longest phrase matched first) ----

    private static final Map<String, Group> GROUPS = new LinkedHashMap<>();
    private static final Map<String, Verb> VERBS = new LinkedHashMap<>();
    private static final Map<String, Place.Kind> PLACES = new LinkedHashMap<>();
    private static final Map<String, Integer> NUMBERS = new LinkedHashMap<>();
    private static final List<String> RESOURCES = List.of("metal", "energy", "wood", "trees", "ore", "food");
    private static final List<String> DIRECTIONS = List.of("north", "south", "east", "west");
    private static final Map<String, String> REFERENCES = new LinkedHashMap<>();
    /** Sentences shown as "did you mean" when nothing closer is known. */
    static final List<String> EXAMPLES = List.of(
        "select all workers", "idle workers build anti-air here", "workers stop and build anti-air near you",
        "soldiers attack move there", "everyone retreat home", "group one patrol here", "select units here",
        "the hero hold", "workers repair at the capitol", "soldiers guard the capitol", "save as group two",
        "focus group three", "autocast on heal");

    static {
        put(GROUPS, Group.of(Group.Kind.ALL_WORKERS), "all workers", "every worker", "all builders", "all the workers");
        put(GROUPS, Group.of(Group.Kind.IDLE_WORKERS), "idle workers", "idle builders", "free workers", "the idle workers");
        put(GROUPS, Group.of(Group.Kind.WORKERS), "workers", "builders", "constructors", "the workers");
        put(GROUPS, Group.of(Group.Kind.SOLDIERS), "soldiers", "army", "fighters", "the army", "the soldiers", "troops", "all soldiers");
        put(GROUPS, Group.of(Group.Kind.EVERYONE), "everyone", "everybody", "all units", "every unit", "all");
        put(GROUPS, Group.SELECTION, "selection", "selected units", "these", "these units", "this group", "you", "you lot");
        put(GROUPS, Group.of(Group.Kind.UNITS_HERE), "units here", "these here", "units under the cursor", "units at the cursor");
        put(GROUPS, Group.of(Group.Kind.HERO), "the hero", "hero", "my hero", "commander");

        put(VERBS, Verb.SELECT, "select", "pick", "grab", "take");
        put(VERBS, Verb.STOP, "stop", "halt", "stop what you are doing", "stop what you're doing", "stand down");
        put(VERBS, Verb.HOLD, "hold", "hold position", "hold here", "stay");
        put(VERBS, Verb.ATTACK_MOVE, "attack move", "attack-move", "fight", "attack", "push", "engage");
        put(VERBS, Verb.MOVE, "move", "go", "come", "walk", "head");
        put(VERBS, Verb.PATROL, "patrol");
        put(VERBS, Verb.RETREAT, "retreat", "fall back", "pull back", "run", "withdraw");
        put(VERBS, Verb.GUARD, "guard", "defend", "protect", "escort");
        put(VERBS, Verb.BUILD, "build", "construct", "place", "put down", "put up");
        put(VERBS, Verb.REPAIR, "repair", "fix", "mend");
        put(VERBS, Verb.RECLAIM, "reclaim", "salvage", "scrap");
        put(VERBS, Verb.GATHER, "gather", "harvest", "collect", "mine", "chop");
        put(VERBS, Verb.AUTOCAST_ON, "autocast on", "enable autocast", "turn on autocast", "auto cast on", "autocast");
        put(VERBS, Verb.AUTOCAST_OFF, "autocast off", "disable autocast", "turn off autocast", "auto cast off", "stop casting");
        put(VERBS, Verb.USE_ABILITY, "use", "cast", "fire", "activate");
        put(VERBS, Verb.SAVE_GROUP, "save as group", "save group", "save as", "make group", "set group", "bind to group");
        put(VERBS, Verb.FOCUS, "focus", "focus on", "look at", "camera to", "show me", "jump to", "centre on", "center on");

        put(PLACES, Place.Kind.HERE, "here", "to me", "at the cursor", "at my cursor", "over here", "to here", "right here");
        put(PLACES, Place.Kind.THERE, "there", "over there", "to there", "at the ping", "where i pinged", "at the mark");
        put(PLACES, Place.Kind.NEAR_YOU, "near you", "near their position", "near them", "nearby", "where you are", "where they are", "near your position", "next to you", "close by");
        put(PLACES, Place.Kind.AT_CAPITOL, "at the capitol", "to the capitol", "at the capital", "to the capital", "at the town centre", "at the town center", "at our capitol");
        put(PLACES, Place.Kind.AT_FRONT, "at the front", "to the front", "at the frontline", "to the frontline", "at the front line", "to the front line", "at the enemy");
        put(PLACES, Place.Kind.HOME, "home", "back home", "to base", "back to base", "to the base", "back");

        String[] words = { "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten" };
        for (int i = 0; i < words.length; i++) {
            NUMBERS.put(words[i], i);
            NUMBERS.put(Integer.toString(i), i);
        }
        REFERENCES.put("the capitol", "capitol");
        REFERENCES.put("the capital", "capitol");
        REFERENCES.put("capitol", "capitol");
        REFERENCES.put("me", "cursor");
        REFERENCES.put("here", "cursor");
        REFERENCES.put("the cursor", "cursor");
        REFERENCES.put("you", "group");
        REFERENCES.put("them", "group");
        REFERENCES.put("their position", "group");
        REFERENCES.put("the front", "front");
        REFERENCES.put("home", "home");
        REFERENCES.put("base", "home");
        REFERENCES.put("the base", "home");
    }

    @SafeVarargs
    private static <T> void put(Map<String, T> table, T value, String... phrases) {
        for (String p : phrases)
            table.put(p, value);
    }

    // ---- parsing ----

    public static Result parse(String sentence, Vocabulary vocab) {
        if (sentence == null || sentence.isBlank())
            return Result.fail("nothing to do", EXAMPLES.get(0));
        List<String> tokens = tokenize(sentence);
        if (tokens.isEmpty())
            return Result.fail("nothing to do", EXAMPLES.get(0));
        List<List<String>> clauses = splitClauses(tokens);
        List<Intent> out = new ArrayList<>();
        Group carried = Group.SELECTION;
        Group pending = null;   // "workers, stop": a clause that is only a group names who the next clause is for
        for (List<String> t : clauses) {
            if (t.isEmpty())
                continue;
            Parsed p = parseClause(t, vocab, pending != null ? pending : carried, !out.isEmpty());
            if (p.groupOnly != null) {
                pending = p.groupOnly;
                continue;
            }
            if (p.error != null)
                return Result.fail(p.error, suggest(sentence, vocab));
            out.add(p.intent);
            carried = p.intent.group();
            pending = null;
        }
        if (pending != null && out.isEmpty())
            return Result.fail("what should they do?", suggest(sentence, vocab));
        if (out.isEmpty())
            return Result.fail("nothing to do", suggest(sentence, vocab));
        return Result.of(out);
    }

    private record Parsed(Intent intent, String error, Group groupOnly) {
        static Parsed err(String e) { return new Parsed(null, e, null); }
        static Parsed ok(Intent i) { return new Parsed(i, null, null); }
        static Parsed group(Group g) { return new Parsed(null, null, g); }
    }

    private static Parsed parseClause(List<String> t, Vocabulary vocab, Group carried, boolean queued) {
        int pos = skip(t, 0, "please", "hey", "ok", "okay", "now", "hi", "right");
        Group group = null;
        Match<Group> g = matchGroup(t, pos, vocab);
        if (g != null) {
            group = g.value;
            pos += g.length;
        }
        pos = skip(t, pos, "please", "now", "all", "and", "just");
        if (pos >= t.size())
            return group != null ? Parsed.group(group) : Parsed.err("nothing to do");
        Match<Verb> v = matchTable(t, pos, VERBS);
        if (v == null)
            return Parsed.err("unknown word \"" + t.get(pos) + "\"");
        Verb verb = v.value;
        pos += v.length;
        // "go build ...", "come guard ...": the movement word is only a lead-in when a real order follows it
        if (verb == Verb.MOVE && v.length == 1 && (t.get(pos - 1).equals("go") || t.get(pos - 1).equals("come"))) {
            int p2 = skip(t, pos, "and");
            Match<Verb> v2 = matchTable(t, p2, VERBS);
            if (v2 != null) {
                verb = v2.value;
                pos = p2 + v2.length;
            }
        }
        // "select all workers" / "select group two" / "select the halberdiers": the group follows the verb
        if (verb == Verb.SELECT && group == null) {
            int p2 = skip(t, pos, "the", "my");
            Match<Group> g2 = matchGroup(t, p2, vocab);
            if (g2 != null) {
                group = g2.value;
                pos = p2 + g2.length;
            }
        }
        if (group == null)
            group = carried;
        String object = null;
        Place place = Place.NONE;
        int number = -1;
        if (verb == Verb.BUILD) {
            int p2 = skip(t, pos, "a", "an", "the", "some", "me", "up", "us");
            Match<String> b = matchVocab(t, p2, vocab, VocabKind.BUILDING);
            if (b == null)
                return Parsed.err(p2 < t.size() ? "build what? \"" + t.get(p2) + "\" is not a building" : "build what?");
            object = b.value;
            pos = p2 + b.length;
        } else if (verb == Verb.USE_ABILITY || verb == Verb.AUTOCAST_ON || verb == Verb.AUTOCAST_OFF) {
            int p2 = skip(t, pos, "the", "a", "an", "your", "on", "off", "for", "now");
            Match<String> a = matchVocab(t, p2, vocab, VocabKind.ABILITY);
            if (a == null)
                return Parsed.err(p2 < t.size() ? "\"" + t.get(p2) + "\" is not an ability" : "which ability?");
            object = a.value;
            pos = p2 + a.length;
        } else if (verb == Verb.SAVE_GROUP || verb == Verb.FOCUS) {
            int p2 = skip(t, pos, "group", "control", "the", "on", "number", "as", "to");
            Match<Integer> n = matchNumber(t, p2);
            if (n != null) {
                number = n.value;
                pos = p2 + n.length;
                if (verb == Verb.FOCUS)
                    group = Group.control(number);
            } else if (verb == Verb.SAVE_GROUP) {
                return Parsed.err("save as which group? (one to ten)");
            } else {
                Match<Group> fg = matchGroup(t, p2, vocab);   // "focus on the hero", "focus on idle workers"
                if (fg != null) {
                    group = fg.value;
                    pos = p2 + fg.length;
                }
            }
        } else if (verb == Verb.GATHER) {
            int p2 = skip(t, pos, "the", "some", "more");
            if (p2 < t.size() && RESOURCES.contains(t.get(p2))) {
                object = t.get(p2);
                pos = p2 + 1;
            }
        }
        // place: the phrase as written first ("at the capitol"), then without a leading preposition
        Match<Place> pl = matchPlace(t, pos);
        int p3 = pos;
        if (pl == null) {
            p3 = skip(t, pos, "to", "at", "over", "on", "towards", "toward", "in", "into", "up", "and");
            pl = matchPlace(t, p3);
        }
        if (pl != null) {
            place = pl.value;
            pos = p3 + pl.length;
        } else if (verb == Verb.GUARD && p3 < t.size()) {
            Match<String> ref = matchTable(t, p3, REFERENCES);   // "guard the capitol"
            if (ref != null) {
                place = switch (ref.value) {
                    case "capitol", "home" -> Place.of(Place.Kind.AT_CAPITOL);
                    case "front" -> Place.of(Place.Kind.AT_FRONT);
                    case "cursor" -> Place.of(Place.Kind.HERE);
                    default -> Place.of(Place.Kind.NEAR_YOU);
                };
                pos = p3 + ref.length;
            }
        }
        pos = skip(t, pos, "please", "now", "thanks", "thank", "you", "ok", "okay");
        if (pos < t.size())
            return Parsed.err("unknown word \"" + t.get(pos) + "\"");
        if (verb == Verb.SAVE_GROUP)
            return Parsed.ok(new Intent(group, verb, Integer.toString(number), Place.NONE, queued));
        return Parsed.ok(new Intent(group, verb, object, place, queued));
    }

    /** A grammar group, a control group, or a unit type from the vocabulary. */
    private static Match<Group> matchGroup(List<String> t, int pos, Vocabulary vocab) {
        Match<Group> g = matchTable(t, pos, GROUPS);
        if (g != null)
            return g;
        Match<Integer> cg = matchControlGroup(t, pos);
        if (cg != null)
            return new Match<>(Group.control(cg.value), cg.length);
        Match<String> ut = matchVocab(t, pos, vocab, VocabKind.UNIT);
        if (ut != null)
            return new Match<>(Group.type(ut.value), ut.length);
        return null;
    }

    private enum VocabKind { UNIT, BUILDING, ABILITY }

    private record Match<T>(T value, int length) { }

    private static <T> Match<T> matchTable(List<String> t, int pos, Map<String, T> table) {
        for (int len = Math.min(5, t.size() - pos); len >= 1; len--) {
            String phrase = String.join(" ", t.subList(pos, pos + len));
            T v = table.get(phrase);
            if (v != null)
                return new Match<>(v, len);
        }
        return null;
    }

    private static Match<String> matchVocab(List<String> t, int pos, Vocabulary vocab, VocabKind kind) {
        for (int len = Math.min(4, t.size() - pos); len >= 1; len--) {
            String phrase = String.join(" ", t.subList(pos, pos + len));
            String v = switch (kind) {
                case UNIT -> vocab.unit(phrase);
                case BUILDING -> vocab.building(phrase);
                case ABILITY -> vocab.ability(phrase);
            };
            if (v != null)
                return new Match<>(v, len);
        }
        return null;
    }

    private static Match<Integer> matchNumber(List<String> t, int pos) {
        if (pos >= t.size())
            return null;
        Integer n = NUMBERS.get(t.get(pos));
        return n == null ? null : new Match<>(n, 1);
    }

    /** "group three", "control group 3", "group number three". */
    private static Match<Integer> matchControlGroup(List<String> t, int pos) {
        int p = pos;
        if (p < t.size() && t.get(p).equals("control"))
            p++;
        if (p >= t.size() || !t.get(p).equals("group"))
            return null;
        p++;
        if (p < t.size() && t.get(p).equals("number"))
            p++;
        Match<Integer> n = matchNumber(t, p);
        return n == null ? null : new Match<>(n.value, p + 1 - pos);
    }

    private static Match<Place> matchPlace(List<String> t, int pos) {
        Match<Place.Kind> k = matchTable(t, pos, PLACES);
        if (k != null)
            return new Match<>(Place.of(k.value), k.length);
        // "<direction> of <reference>"
        if (pos + 2 < t.size() && DIRECTIONS.contains(t.get(pos)) && t.get(pos + 1).equals("of")) {
            Match<String> ref = matchTable(t, pos + 2, REFERENCES);
            if (ref != null)
                return new Match<>(new Place(Place.Kind.DIRECTION_OF, t.get(pos), ref.value), 2 + ref.length);
        }
        return null;
    }

    private static int skip(List<String> t, int pos, String... words) {
        List<String> w = List.of(words);
        while (pos < t.size() && w.contains(t.get(pos)))
            pos++;
        return pos;
    }

    static List<String> tokenize(String s) {
        List<String> out = new ArrayList<>();
        for (String w : s.toLowerCase(java.util.Locale.ROOT).replace(",", " , ").replaceAll("[^a-z0-9,\\-' ]", " ").trim().split("\\s+"))
            if (!w.isEmpty())
                out.add(w);
        return out;
    }

    /** Splits on "and", "then" and commas; a clause that turns out to be only a group names the next clause's group. */
    private static List<List<String>> splitClauses(List<String> tokens) {
        List<List<String>> clauses = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        for (String w : tokens) {
            if (w.equals("and") || w.equals("then") || w.equals(",")) {
                if (!cur.isEmpty())
                    clauses.add(cur);
                cur = new ArrayList<>();
                continue;
            }
            cur.add(w);
        }
        if (!cur.isEmpty())
            clauses.add(cur);
        return clauses;
    }

    // ---- "did you mean" ----

    /** The accepted sentence nearest to what was said, by word edit distance over the examples and the vocabulary. */
    static String suggest(String sentence, Vocabulary vocab) {
        List<String> said = tokenize(sentence);
        said.removeIf(w -> w.equals(","));
        String best = EXAMPLES.get(0);
        int bestD = Integer.MAX_VALUE;
        List<String> candidates = new ArrayList<>(EXAMPLES);
        for (String b : vocab.buildings())
            candidates.add("build " + b + " here");
        for (String u : vocab.units())
            candidates.add("select " + Vocabulary.plural(u));
        for (String a : vocab.abilities())
            candidates.add("use " + a);
        for (String c : candidates) {
            int d = wordDistance(said, tokenize(c));
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    static int wordDistance(List<String> a, List<String> b) {
        int[][] d = new int[a.size() + 1][b.size() + 1];
        for (int i = 0; i <= a.size(); i++) d[i][0] = i;
        for (int j = 0; j <= b.size(); j++) d[0][j] = j;
        for (int i = 1; i <= a.size(); i++)
            for (int j = 1; j <= b.size(); j++) {
                int sub = a.get(i - 1).equals(b.get(j - 1)) ? 0 : (a.get(i - 1).charAt(0) == b.get(j - 1).charAt(0) ? 1 : 2);
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + sub);
            }
        return d[a.size()][b.size()];
    }
}
