package com.solegendary.reignofnether.unit.modelling.models;

import com.solegendary.reignofnether.unit.modelling.animations.TitheBinderDrive;
import com.solegendary.reignofnether.unit.modelling.animations.TitheBinderParts;
import com.solegendary.reignofnether.unit.modelling.animations.TitheBinderRig;
import com.solegendary.reignofnether.unit.modelling.models.gen.TitheBinderModel;
import com.solegendary.reignofnether.unit.modelling.motion.OwnMotion;
import com.solegendary.reignofnether.unit.units.monsters.TitheBinderUnit;
import com.solegendary.reignofnether.unit.units.monsters.TitheLedger;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The Tithe Binder (tithe_binder_unit) over its generated body (TitheBinderModel, from
 * design/models/gravebound_tithe_binder.json by tools/dsl_to_game.py; the generator is tools/gen_tithe_binder.py),
 * moved by its own rig: TitheBinderRenderer steps the unit's {@link TitheBinderDrive} once per game tick and hands it
 * over with the frame's partialTick ({@link #frame}); setupAnim lays the blended pose on the rest pose through
 * {@link TitheBinderRig#apply} (bones, the docked slots, the hidden lantern core). Flying weapons and every effect are
 * the renderer's. The living army's motion pass leaves this body alone ({@link OwnMotion}): the rig owns it.
 *
 * Far LOD (tools/gen_tithe_binder.py far_lod): when the camera is far, every weapon draws its tactical stand-in
 * (weapon_k_far) instead of its own cubes, and the flail's ball is hidden.
 */
@OnlyIn(Dist.CLIENT)
public class TitheBinderUnitModel extends TitheBinderModel<TitheBinderUnit> implements OwnMotion {

    private final ModelPart[] weapons = new ModelPart[TitheLedger.MAX_WEAPONS + 1];
    private final ModelPart[] weaponsFar = new ModelPart[TitheLedger.MAX_WEAPONS + 1];
    /** Per weapon: every part of its subtree but the far stand-in (their cubes are skipped when far). */
    private final ModelPart[][] weaponsNear = new ModelPart[TitheLedger.MAX_WEAPONS + 1][];
    private final ModelPart flailBall;
    private final TitheBinderRig.Bones rig;

    // this draw (set by the renderer just before it, taken by setupAnim)
    private TitheBinderDrive drive;
    private float partialTick;
    private boolean far;

    public TitheBinderUnitModel(ModelPart root) {
        super(root);
        for (int k = 1; k <= TitheLedger.MAX_WEAPONS; k++) {
            ModelPart w = bone("weapon_" + k);
            ModelPart f = bone("weapon_" + k + "_far");
            weapons[k] = w;
            weaponsFar[k] = f;
            weaponsNear[k] = w == null ? new ModelPart[0]
                : w.getAllParts().filter(p -> p != f).toArray(ModelPart[]::new);
        }
        flailBall = bone("weapon_6_ball");
        rig = TitheBinderParts.bones(this::bone);
    }

    /** The renderer's hand-over for one draw: the unit's drive (stepped to this tick), partialTick, near / far. */
    public void frame(TitheBinderDrive drive, float partialTick, boolean far) {
        this.drive = drive;
        this.partialTick = partialTick;
        this.far = far;
    }

    /** Shows weapon_1..weapon_count, hides the rest (1-10): the rest pose, while there is no drive. */
    public void showWeapons(int count) {
        for (int k = 1; k <= TitheLedger.MAX_WEAPONS; k++)
            if (weapons[k] != null)
                weapons[k].visible = k <= count;
    }

    /** Weapon k (1-10), or null. */
    public ModelPart weapon(int k) {
        return k >= 1 && k <= TitheLedger.MAX_WEAPONS ? weapons[k] : null;
    }

    /** Weapon k (1-10) draws its far stand-in (true) or its own cubes (false). */
    public void weaponLod(int k, boolean far) {
        for (ModelPart p : weaponsNear[k])
            p.skipDraw = far;
        if (weaponsFar[k] != null)
            weaponsFar[k].visible = far;
    }

    @Override
    public void setupAnim(TitheBinderUnit entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        restPose();
        TitheBinderDrive d = this.drive;
        this.drive = null;
        if (d != null && d.ready())
            TitheBinderRig.apply(d.blend(partialTick), rig);
        else
            showWeapons(entity.getWeapons());
        for (int k = 1; k <= TitheLedger.MAX_WEAPONS; k++)
            weaponLod(k, far);
        if (flailBall != null)
            flailBall.visible = !far;
    }
}
