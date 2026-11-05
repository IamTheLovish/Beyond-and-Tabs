package com.solegendary.reignofnether.unit.modelling.motion;

import com.solegendary.reignofnether.unit.modelling.animations.Quadruped;
import com.solegendary.reignofnether.unit.modelling.models.RoyalGuardModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.HalberdierModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.IllagerModel;
import net.minecraft.client.model.IronGolemModel;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.WitchModel;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which bodies the living army moves, and how (design/living_army.md, section 1; slice 6): every vanilla family the
 * roster draws with (humanoid, illager, golem, witch, spider) gets its torso, head and limbs; the keyframe-clip units
 * and our generated custom rigs get breath-and-flinch only on their own hierarchy (a rig that breathes by itself is
 * not given a second breath); a body the round does not cover (a wolf, a creature model) is left alone.
 */
class BoneMapsTest {

    @Test
    void humanoidAndIllagerBodiesAreWhole() {
        BoneMap h = BoneMaps.forModel(new HumanoidModel<>(LayerDefinition.create(
            HumanoidModel.createMesh(CubeDeformation.NONE, 0f), 64, 64).bakeRoot()));
        assertNotNull(h);
        for (int bone : new int[] {Motion.BODY, Motion.HEAD, Motion.ARM_R, Motion.ARM_L, Motion.LEG_R, Motion.LEG_L})
            assertNotNull(h.parts[bone], "humanoid bone " + bone);
        BoneMap il = BoneMaps.forModel(new IllagerModel<>(IllagerModel.createBodyLayer().bakeRoot()));
        assertNotNull(il);
        assertNotNull(il.parts[Motion.ARMS], "the illager's crossed arms");
        assertFalse(il.flinchOnly);
    }

    @Test
    void golemWitchAndSpiderJoin() {
        BoneMap golem = BoneMaps.forModel(new IronGolemModel<>(IronGolemModel.createBodyLayer().bakeRoot()));
        assertNotNull(golem, "iron golem");
        assertNotNull(golem.parts[Motion.BODY]);
        assertNotNull(golem.parts[Motion.ARM_R]);
        assertNotNull(golem.parts[Motion.LEG_L]);
        BoneMap witch = BoneMaps.forModel(new WitchModel<>(WitchModel.createBodyLayer().bakeRoot()));
        assertNotNull(witch, "witch");
        assertNotNull(witch.parts[Motion.HEAD]);
        assertNotNull(witch.parts[Motion.ARMS]);
        BoneMap spider = BoneMaps.forModel(new SpiderModel<>(SpiderModel.createSpiderBodyLayer().bakeRoot()));
        assertNotNull(spider, "spider");
        assertNotNull(spider.parts[Motion.BODY]);
        assertNotNull(spider.parts[Motion.HEAD]);
    }

    @Test
    void keyframeUnitsBreatheAndFlinchOnTheirOwnHierarchy() {
        BoneMap guard = BoneMaps.forModel(new RoyalGuardModel<>(RoyalGuardModel.createBodyLayer().bakeRoot()));
        assertNotNull(guard, "Royal Guard (keyframe clips)");
        assertTrue(guard.flinchOnly);
        assertTrue(guard.hierarchical);
        assertFalse(guard.noBreath);
        assertNotNull(guard.parts[Motion.HEAD]);
    }

    @Test
    void customRigsGetFlinchButNoSecondBreath() {
        BoneMap hal = BoneMaps.forModel(new HalberdierModel<Entity>(HalberdierModel.createBodyLayer().bakeRoot()));
        assertNotNull(hal, "a generated body");
        assertTrue(hal.flinchOnly);
        assertTrue(hal.hierarchical);
        assertTrue(hal.noBreath, "it breathes by itself");
    }

    @Test
    void bodiesOutsideTheRoundAreLeftAlone() {
        assertNull(BoneMaps.forModel(new WolfModel<>(WolfModel.createBodyLayer().bakeRoot())));
    }

    @Test
    void theBisonRunsOnTheSharedExertion() {
        assertEquals(0.3f, Quadruped.exertion(0.3f, -1, new float[AnimState.STRIDE]), 0f, "no slot: its own");
        float[] slab = new float[AnimState.STRIDE * 2];
        slab[AnimState.STRIDE + AnimState.EXERTION] = 0.8f;
        assertEquals(0.8f, Quadruped.exertion(0.3f, AnimState.STRIDE, slab), 0f, "a slot: the shared one");
    }

    private static ModelPart part(Map<String, ModelPart> children) {
        return new ModelPart(List.of(), children);
    }

    @Test
    void aRigsTorsoBendsOnlyWhenItCarriesTheHeadAndNotTheLegs() {
        BoneMap guard = BoneMaps.forModel(new RoyalGuardModel<>(RoyalGuardModel.createBodyLayer().bakeRoot()));
        assertNull(guard.parts[Motion.BODY], "the Royal Guard's head and arms are its body's siblings: a bent body leaves them");
        BoneMap hal = BoneMaps.forModel(new HalberdierModel<Entity>(HalberdierModel.createBodyLayer().bakeRoot()));
        assertNotNull(hal.parts[Motion.BODY], "the Halberdier's body is its pelvis (bending it swings the legs and feet): its chest is the torso");
        assertTrue(BoneMaps.bendsAlone(hal.parts[Motion.BODY], hal.parts[Motion.HEAD]), "a torso that carries the head, no legs");
        ModelPart head = part(Map.of());
        assertTrue(BoneMaps.bendsAlone(part(Map.of("head", head)), head), "a torso that carries the head");
        assertFalse(BoneMaps.bendsAlone(part(Map.of("head", head, "left_thigh", part(Map.of()))), head), "a pelvis");
        assertFalse(BoneMaps.bendsAlone(part(Map.of("x", part(Map.of("legR", part(Map.of()))), "head", head)), head),
            "legs deeper down still count");
        assertFalse(BoneMaps.bendsAlone(part(Map.of()), head), "a torso beside the head");
    }
}
