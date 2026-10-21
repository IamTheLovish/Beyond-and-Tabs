package com.solegendary.reignofnether.unit.units.monsters;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import com.solegendary.reignofnether.unit.modelling.animations.Life;
import net.minecraft.util.Mth;

/**
 * Client only. The vanilla phantom's skeleton (wings, tail, head) driven by our own animation, so it can belong to
 * a non-Phantom entity (PhantomModel's own animation calls Phantom-only methods).
 */
public class BoneDragonModel extends EntityModel<BoneDragonUnit> {

    private final ModelPart root, body, head, leftWing, leftTip, rightWing, rightTip, tail, tailTip;

    private final float bodyY, headPitch0, headYaw0;     // the phantom skeleton's rest pose, kept under our offsets

    private static final float BEAT = 48f;     // ticks per wing beat: slow and heavy, it is much bigger than a phantom
    private static final float DOWN = 0.6f;    // the power downstroke takes 60% of the beat, the folded upstroke 40%
    private static final float BREATH = 110f;  // ticks per breath: a big body breathes slowly and deeply

    public BoneDragonModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.leftWing = body.getChild("left_wing_base");
        this.leftTip = leftWing.getChild("left_wing_tip");
        this.rightWing = body.getChild("right_wing_base");
        this.rightTip = rightWing.getChild("right_wing_tip");
        this.tail = body.getChild("tail_base");
        this.tailTip = tail.getChild("tail_tip");
        this.bodyY = body.y;
        this.headPitch0 = head.xRot;
        this.headYaw0 = head.yRot;
    }

    @Override
    public void setupAnim(BoneDragonUnit entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        // design/ANIMATION-STANDARD.md, creatures: it breathes, its wings beat with a slow power stroke and a quick
        // folded recovery, the tips lag the arms, the tail is a chain that the beat rolls down, the head leads.
        int id = entity.getId();
        float beat = Life.wingBeat(ageInTicks, id, BEAT, DOWN);           // -1 top .. 1 bottom
        float up = Life.upstroke(ageInTicks, id, BEAT, DOWN);             // 0..1 while recovering
        float tipBeat = Life.wingBeat(ageInTicks - 3f, id, BEAT, DOWN);   // the tips follow 3 ticks behind

        leftWing.zRot = Life.deg(4f + 18f * beat);
        leftTip.zRot = Life.deg(10f * tipBeat + 34f * up);                // the tip folds on the upstroke
        rightWing.zRot = -leftWing.zRot;
        rightTip.zRot = -leftTip.zRot;

        // breathing: the ribcage swells a little on the in-breath, the head lifts with it
        float br = Life.breath(ageInTicks, id, BREATH);
        float swell = 1f + 0.018f * br;
        body.xScale = swell;
        body.yScale = 1f + 0.012f * br;
        // the body rises on each downstroke and sinks on the recovery: the beat carries the weight
        body.y = bodyY - 1.2f * beat;

        // the head leads: it looks toward where the body turns, steadied against the bob, and lifts on the in-breath
        head.yRot = headYaw0 + Mth.clamp(netHeadYaw, -35f, 35f) * 0.7f * Mth.DEG_TO_RAD;
        head.xRot = headPitch0 + Mth.clamp(headPitch, -25f, 25f) * 0.5f * Mth.DEG_TO_RAD + Life.deg(2f * br) + Life.deg(1.5f * beat);

        // the tail: a chain - the beat's lift reaches the base a little late and the tip later still, plus a slow
        // side-to-side sway that rolls from base to tip
        tail.xRot = -Life.deg(5f + 6f * Life.wingBeat(ageInTicks - 4f, id, BEAT, DOWN));
        tailTip.xRot = -Life.deg(4f + 8f * Life.wingBeat(ageInTicks - 8f, id, BEAT, DOWN));
        tail.yRot = Life.deg(7f * Life.chain(ageInTicks, id, 90f, 0, 0f));
        tailTip.yRot = Life.deg(10f * Life.chain(ageInTicks, id, 90f, 1, 9f));
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
