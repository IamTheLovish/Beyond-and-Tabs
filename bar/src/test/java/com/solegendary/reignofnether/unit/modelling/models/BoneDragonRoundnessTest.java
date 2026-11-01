package com.solegendary.reignofnether.unit.modelling.models;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Bone Dragon is not built of square beams (lovish after a match, Oct 30: "the model is really blocky").
 * Reads the generated design (design/models/gravebound_bone_dragon.json, what tools/dsl_to_game.py turns into
 * BoneDragonModel): every bone-coloured BEAM (a box at least 4x as long as it is thick, its section square) and every
 * bone-coloured JOINT (a cube of 2.5 px or more) must have a turned twin centred on it in the same bone - a box rolled
 * 45 degrees about the beam's axis, a cube turned 45 degrees - so its section is an octagon, not a square.
 */
class BoneDragonRoundnessTest {

    private static final Path DESIGN = Paths.get("..", "design", "models", "gravebound_bone_dragon.json");

    private record Box(String bone, String color, double[] centre, double[] size, double[] rotation) {
        boolean turned() {
            return rotation != null && Arrays.stream(rotation).anyMatch(a -> Math.abs(a) > 1e-3);
        }
    }

    @Test
    void every_beam_and_joint_has_a_turned_twin() throws IOException {
        List<Box> boxes = new ArrayList<>();
        try (Reader r = Files.newBufferedReader(DESIGN)) {
            JsonObject model = JsonParser.parseReader(r).getAsJsonObject();
            for (JsonElement b : model.getAsJsonArray("bones")) {
                JsonObject bone = b.getAsJsonObject();
                for (JsonElement c : bone.getAsJsonArray("cubes")) {
                    JsonObject cube = c.getAsJsonObject();
                    double[] from = vec(cube.getAsJsonArray("from")), size = vec(cube.getAsJsonArray("size"));
                    double[] centre = { from[0] + size[0] / 2, from[1] + size[1] / 2, from[2] + size[2] / 2 };
                    // a turned box's "from" is unturned about its pivot; the beams and joints are turned about their
                    // own centres (gen_dragon's bar / knob / slab), so from + size / 2 is the centre either way
                    boxes.add(new Box(bone.get("name").getAsString(), cube.get("color").getAsString(), centre, size,
                        cube.has("rotation") ? vec(cube.getAsJsonArray("rotation")) : null));
                }
            }
        }
        List<String> square = new ArrayList<>();
        int checked = 0;
        for (Box b : boxes) {
            if (!b.color().startsWith("bone"))
                continue;
            double[] s = b.size().clone();
            Arrays.sort(s);
            boolean beam = s[0] >= 1.0 && s[2] >= 4 * s[0] && s[1] <= 1.25 * s[0];
            boolean joint = s[0] >= 2.5 && s[2] <= 1.05 * s[0];
            if (!beam && !joint)
                continue;
            checked++;
            boolean twin = false;
            for (Box o : boxes)
                if (o != b && o.bone().equals(b.bone()) && o.color().equals(b.color()) && dist(o.centre(), b.centre()) < 0.05
                        && (o.turned() != b.turned() || (o.turned() && !Arrays.equals(o.rotation(), b.rotation())))) {
                    twin = true;
                    break;
                }
            if (!twin)
                square.add(String.format("%s %s %s at %s", b.bone(), b.color(), Arrays.toString(b.size()),
                    Arrays.toString(b.centre())));
        }
        assertTrue(checked > 20, "only " + checked + " beams and joints found in " + DESIGN + ": the reader is wrong");
        assertTrue(square.isEmpty(), square.size() + " of " + checked + " beams / joints are bare square boxes, e.g. "
            + square.subList(0, Math.min(5, square.size())));
    }

    private static double[] vec(JsonArray a) {
        return new double[] { a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble() };
    }

    private static double dist(double[] a, double[] b) {
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }
}
