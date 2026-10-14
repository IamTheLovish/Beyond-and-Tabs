package com.solegendary.reignofnether.gametest;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.commands.lang.CommandLanguage;
import com.solegendary.reignofnether.commands.lang.Vocabulary;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The command language (claude/voice-commands.md, layer 1) parsed against a small fixed vocabulary: forty
 * sentences, each with the exact intents it must produce, plus the refusals - an unknown word, a thing that is not
 * a building - with a "did you mean" that is itself a sentence the parser accepts. No world needed; the arena
 * template is only the test harness.
 */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public class CommandLanguageGameTests {

    static final String ARENA = SkirmishGameTests.ARENA;

    static Vocabulary vocab() {
        return new Vocabulary()
            .units(List.of("Halberdier", "Culverin", "Squire Runner", "Levy"))
            .buildings(List.of("Barracks", "Sun Spire", "Wind Generator", "Metal Extractor"))
            .abilities(List.of("Heal", "Sun Ward", "Leaf Dash"))
            .synonym("anti-air", "Sun Spire").synonym("aa", "Sun Spire").synonym("windmill", "Wind Generator")
            .synonym("mex", "Metal Extractor").synonym("extractor", "Metal Extractor");
    }

    /** sentence -> the intents' toString, joined with "; " */
    static final String[][] ACCEPTED = {
        { "select all workers", "ALL_WORKERS SELECT" },
        { "idle workers build anti-air here", "IDLE_WORKERS BUILD sun spire @HERE" },
        { "workers, stop and build anti-air near you", "WORKERS STOP; WORKERS BUILD sun spire @NEAR_YOU [queued]" },
        { "soldiers attack move there", "SOLDIERS ATTACK_MOVE @THERE" },
        { "everyone retreat home", "EVERYONE RETREAT @HOME" },
        { "group one patrol here", "CONTROL_GROUP(1) PATROL @HERE" },
        { "select units here", "UNITS_HERE SELECT" },
        { "the hero hold", "HERO HOLD" },
        { "workers repair at the capitol", "WORKERS REPAIR @AT_CAPITOL" },
        { "soldiers guard the capitol", "SOLDIERS GUARD @AT_CAPITOL" },
        { "save as group two", "SELECTION SAVE_GROUP 2" },
        { "focus group three", "CONTROL_GROUP(3) FOCUS" },
        { "autocast on heal", "SELECTION AUTOCAST_ON heal" },
        { "halberdiers fight at the front", "UNIT_TYPE(halberdier) ATTACK_MOVE @AT_FRONT" },
        { "culverins hold position", "UNIT_TYPE(culverin) HOLD" },
        { "select the squire runners", "UNIT_TYPE(squire runner) SELECT" },
        { "workers go build a windmill north of the capitol", "WORKERS BUILD wind generator @DIRECTION_OF(north of capitol)" },
        { "stop", "SELECTION STOP" },
        { "move here", "SELECTION MOVE @HERE" },
        { "everybody fall back to base", "EVERYONE RETREAT @HOME" },
        { "group 4 attack", "CONTROL_GROUP(4) ATTACK_MOVE" },
        { "select group two", "CONTROL_GROUP(2) SELECT" },
        { "build an extractor there", "SELECTION BUILD metal extractor @THERE" },
        { "idle workers gather metal", "IDLE_WORKERS GATHER metal" },
        { "use sun ward", "SELECTION USE_ABILITY sun ward" },
        { "autocast off leaf dash", "SELECTION AUTOCAST_OFF leaf dash" },
        { "levies, hold here", "UNIT_TYPE(levy) HOLD" },
        { "workers stop what you're doing and build anti-air near their position", "WORKERS STOP; WORKERS BUILD sun spire @NEAR_YOU [queued]" },
        { "select halberdiers and attack move there then hold", "UNIT_TYPE(halberdier) SELECT; UNIT_TYPE(halberdier) ATTACK_MOVE @THERE [queued]; UNIT_TYPE(halberdier) HOLD [queued]" },
        { "please stop", "SELECTION STOP" },
        { "guard", "SELECTION GUARD" },
        { "focus on the hero", "HERO FOCUS" },
        { "come here", "SELECTION MOVE @HERE" },
        { "soldiers go there", "SOLDIERS MOVE @THERE" },
        { "selection retreat", "SELECTION RETREAT" },
        { "control group 3 move to the front", "CONTROL_GROUP(3) MOVE @AT_FRONT" },
        { "reclaim here", "SELECTION RECLAIM @HERE" },
        { "these units patrol south of here", "SELECTION PATROL @DIRECTION_OF(south of cursor)" },
        { "select all", "EVERYONE SELECT" },
        { "Workers, build AA here, then repair at the capitol", "WORKERS BUILD sun spire @HERE; WORKERS REPAIR @AT_CAPITOL [queued]" },
    };

    static final String[] REFUSED = { "workers dance", "build a castle here", "", "halberdiers", "use fireball", "save as group" };

    @GameTest(template = ARENA)
    public static void command_language_parses_forty_sentences_and_refuses_the_unknown(GameTestHelper helper) {
        Vocabulary v = vocab();
        List<String> problems = new ArrayList<>();
        for (String[] row : ACCEPTED) {
            CommandLanguage.Result r = CommandLanguage.parse(row[0], v);
            if (!r.ok()) {
                problems.add("\"" + row[0] + "\" refused: " + r.error());
                continue;
            }
            String got = String.join("; ", r.intents().stream().map(Object::toString).toList());
            if (!got.equals(row[1]))
                problems.add("\"" + row[0] + "\" -> " + got + " (wanted " + row[1] + ")");
        }
        for (String s : REFUSED) {
            CommandLanguage.Result r = CommandLanguage.parse(s, v);
            if (r.ok()) {
                problems.add("\"" + s + "\" was accepted: " + r.intents());
                continue;
            }
            if (r.suggestion() == null || !CommandLanguage.parse(r.suggestion(), v).ok())
                problems.add("\"" + s + "\" suggested \"" + r.suggestion() + "\", which does not parse");
        }
        if (!problems.isEmpty())
            helper.fail(String.join("; ", problems));
        else
            helper.succeed();
    }
}
