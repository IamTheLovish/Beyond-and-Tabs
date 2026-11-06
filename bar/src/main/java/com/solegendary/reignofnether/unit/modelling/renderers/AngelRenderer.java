package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.unit.BlowTiming;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.modelling.animations.AngelDrive;
import com.solegendary.reignofnether.unit.modelling.animations.AngelRig;
import com.solegendary.reignofnether.unit.modelling.layers.TeamColourLayer;
import com.solegendary.reignofnether.unit.modelling.models.AngelBody;
import com.solegendary.reignofnether.unit.modelling.models.MenderAngelUnitModel;
import com.solegendary.reignofnether.unit.modelling.models.SeraphArcherUnitModel;
import com.solegendary.reignofnether.unit.modelling.models.SwordSeraphUnitModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.MenderAngelModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.SeraphArcherModel;
import com.solegendary.reignofnether.unit.modelling.models.gen.SwordSeraphModel;
import com.solegendary.reignofnether.unit.units.villagers.AngelUnit;
import com.solegendary.reignofnether.unit.units.villagers.MenderAngelUnit;
import com.solegendary.reignofnether.unit.units.villagers.SeraphArcherUnit;
import com.solegendary.reignofnether.unit.units.villagers.SwordSeraphUnit;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * The Angel Caller's three angels on their generated bodies (tools/gen_angels.py b4 -> tools/dsl_to_game.py), the
 * Dome Cleric's wiring - TeamColourLayer, the _glow pass, setDetail - at the design sheet's render scales (Mender
 * 0.92 / Archer 1.00 / Sword 1.15; .scratch/angels/design-sheet.md), animated by {@link AngelRig}: each unit carries
 * an {@link AngelDrive} (AngelUnit.clientDrive, made on the first draw) that this renderer feeds from the entity and
 * steps once per game tick to its tickCount; the frame draws the last two steps blended by partialTick. Until Nov 6
 * a tinted vanilla vex.
 * <p>
 * The rig plays its own death (no sideways flip), its arrival (the drive starts it on a young angel), the white light
 * of both (the white overlay) and their fade (translucent while the rig's alpha is under 1).
 */
@OnlyIn(Dist.CLIENT)
public class AngelRenderer<T extends AngelUnit, M extends EntityModel<T> & AngelBody> extends MobRenderer<T, M> {
    private final ResourceLocation texture;
    private final float scale;
    private final AngelRig.Kind kind;
    private final AngelDrive.Timing timing;

    public AngelRenderer(EntityRendererProvider.Context context, M model, String name, float scale, AngelRig.Kind kind,
                         AngelDrive.Timing timing) {
        super(context, model, 0.4f * scale);
        this.texture = GeneratedBodies.texture(name);
        this.scale = scale;
        this.kind = kind;
        this.timing = timing;
        this.addLayer(new TeamColourLayer<>(this));
        this.addLayer(GeneratedBodies.glow(this, name));
    }

    /** The Mender Angel: the release is the heal, every {@link MenderAngelUnit#HEAL_INTERVAL_TICKS} while it mends. */
    public static AngelRenderer<MenderAngelUnit, MenderAngelUnitModel> mender(EntityRendererProvider.Context ctx) {
        return new AngelRenderer<>(ctx, new MenderAngelUnitModel(ctx.bakeLayer(MenderAngelModel.LAYER_LOCATION)),
            "mender_angel_unit", 0.92f, AngelRig.Kind.MENDER,
            new AngelDrive.Timing(0, 0, MenderAngelUnit.HEAL_INTERVAL_TICKS));
    }

    /**
     * The Seraph Archer: its release follows the wind-up cue by the wind-up's ticks less one (UnitRangedAttackGoal
     * counts the cue's tick), the next one a shot cooldown after it.
     */
    public static AngelRenderer<SeraphArcherUnit, SeraphArcherUnitModel> archer(EntityRendererProvider.Context ctx) {
        int lead = SeraphArcherUnit.ATTACK_WINDUP_TICKS - 1;
        int cooldown = (int) (20f / SeraphArcherUnit.attacksPerSecond);
        return new AngelRenderer<>(ctx, new SeraphArcherUnitModel(ctx.bakeLayer(SeraphArcherModel.LAYER_LOCATION)),
            "seraph_archer_unit", 1.00f, AngelRig.Kind.ARCHER, new AngelDrive.Timing(lead, 0, cooldown + lead));
    }

    /** The Sword Seraph: its blow is held {@link BlowTiming#SWORD_SERAPH} ticks after the swing, the cut's contact. */
    public static AngelRenderer<SwordSeraphUnit, SwordSeraphUnitModel> sword(EntityRendererProvider.Context ctx) {
        return new AngelRenderer<>(ctx, new SwordSeraphUnitModel(ctx.bakeLayer(SwordSeraphModel.LAYER_LOCATION)),
            "sword_seraph_unit", 1.15f, AngelRig.Kind.SWORD, new AngelDrive.Timing(0, BlowTiming.SWORD_SERAPH, 0));
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture;
    }

    @Override
    protected void scale(T entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(scale, scale, scale);
    }

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light) {
        this.model.setDetail(GeneratedBodies.wantsDetail(entity));
        this.model.frame(drive(entity).blend(partialTick));
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    /** No sideways death flip: the rig plays its own death (back to the light). */
    @Override
    protected float getFlipDegrees(T entity) {
        return 0f;
    }

    /** The arrival's and the death's light: the rig's white overlay. */
    @Override
    protected float getWhiteOverlayProgress(T entity, float partialTick) {
        AngelDrive d = entity.clientDrive;
        return d == null || !d.ready() ? 0f : (float) d.blended().white;
    }

    /** Translucent while the rig fades the body (arriving, dying), the generated body's own type otherwise. */
    @Nullable
    @Override
    protected RenderType getRenderType(T entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        AngelDrive d = entity.clientDrive;
        if (bodyVisible && d != null && d.ready() && d.blended().alpha < 0.999)
            return RenderType.entityTranslucent(texture);
        return super.getRenderType(entity, bodyVisible, translucent, glowing);
    }

    // ------------------------------------------------------------------ the drive

    /** The unit's drive, made on the first draw, stepped to the entity's tick (reads the unit only when it steps). */
    AngelDrive drive(T e) {
        AngelDrive d = e.clientDrive;
        if (d == null) {
            d = new AngelDrive(kind, e.getId(), timing);
            e.clientDrive = d;
        }
        if (d.behind(e.tickCount)) {
            read(e, d.signals);
            d.catchUp(e.tickCount);
        }
        return d;
    }

    /** The unit as the drive's signals: forward speed, turn, the synced cues, the swing, hurt and death. */
    static void read(AngelUnit e, AngelDrive.Signals s) {
        double yaw = Math.toRadians(e.yBodyRot);
        double dx = e.getX() - e.xo, dz = e.getZ() - e.zo;
        s.speed = Math.max(0.0, -dx * Math.sin(yaw) + dz * Math.cos(yaw));    // along the facing (-sin, cos)
        s.yawRate = Mth.wrapDegrees(e.yBodyRot - e.yBodyRotO);
        s.windupSeq = e.getWindupCue();
        s.contactSeq = e.getContactCue();
        s.swinging = e.swinging;
        s.hurtTime = e.hurtTime;
        s.dying = e.deathTime > 0;
        Entity t = e.getCueTargetId() < 0 ? null : e.level().getEntity(e.getCueTargetId());
        // the archer goes on shooting a living target; the Mender heals again only while its patient is hurt
        if (t instanceof LivingEntity le && le.isAlive())
            s.engaged = e instanceof AttackerUnit || le.getHealth() < le.getMaxHealth();
        else
            s.engaged = false;
    }
}
