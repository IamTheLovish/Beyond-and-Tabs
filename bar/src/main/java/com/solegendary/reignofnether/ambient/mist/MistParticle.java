package com.solegendary.reignofnether.ambient.mist;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.solegendary.reignofnether.environment.wind.WindField;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * One wisp of the living valley mist (MistClientEvents): a big soft blob (tools/mist_sprites.py, mist_0..3) lying FLAT
 * a little over the ground, drifting slowly along the shared wind (WindField.heading - the way the tree limbs lean),
 * turning and growing slowly, fading in and out over seconds, tinted by the hour (MistMath.colour). Flat rather than
 * camera-facing: a 5-12 block billboard 0.3-2.5 blocks over the ground cuts into the terrain with a hard line, and from
 * the RTS camera's pitch a flat layer reads as mist lying in the valley. A unit running through it pushes it aside a
 * little (MistMath.push). Over a building it thins away (never over a footprint), and it leaves when the mist may not
 * show (rain, strategic zoom, the setting, the eyes rig's measured stills).
 *
 * The spawner passes the peak alpha as dx (MistMath.peakAlpha). Client only, no physics; counted while alive.
 */
@OnlyIn(Dist.CLIENT)
public class MistParticle extends TextureSheetParticle {

    public static int alive = 0;

    private final double peak, speed, start, baseY;
    private final float spin, phase;
    private double pushX, pushZ;
    private float rot, oRot;
    private float leaving = 1;            // 1 while it may stay; falls to 0 over a second when it must go
    private boolean counted = true;

    MistParticle(ClientLevel level, double x, double y, double z, double peakAlpha, SpriteSet sprites) {
        super(level, x, y, z);
        alive++;
        this.hasPhysics = false;
        this.gravity = 0;
        this.peak = peakAlpha;
        this.baseY = y;
        this.lifetime = MistMath.lifetime(random.nextDouble());
        this.start = MistMath.startSize(random.nextDouble());
        this.speed = MistMath.driftSpeed(random.nextDouble());
        this.spin = (random.nextFloat() - 0.5f) * 0.004f;        // a full turn takes minutes
        this.phase = random.nextFloat() * Mth.TWO_PI;
        this.rot = this.oRot = random.nextFloat() * Mth.TWO_PI;
        this.alpha = 0;
        this.quadSize = (float) (start / 2);
        setSprite(sprites.get(random.nextInt(4), 3));
        tint();
    }

    private void tint() {
        float[] c = MistMath.colour(level.getDayTime());
        rCol = c[0];
        gCol = c[1];
        bCol = c[2];
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRot = rot;
        if (age++ >= lifetime || leaving <= 0) {
            remove();
            return;
        }
        // it may not show any more, or the hour has burnt it off (the eyes rig jumps from dawn to noon between stills)
        if (!MistClientEvents.mayShow() || (age % 20 == 0 && MistMath.density(level.getDayTime()) <= 0))
            leaving = Math.min(leaving, 0.999f);
        // drift with the wind (the trees' WindField heading), plus whatever push a running unit gave it, damped
        double[] d = MistMath.drift(WindField.heading(level.getGameTime() / 20.0), speed);
        if (age % 5 == 0)
            stir();
        pushX *= 0.92;
        pushZ *= 0.92;
        x += d[0] + pushX;
        z += d[1] + pushZ;
        // keep its level over lower ground (cold air settles as a sheet), rise over higher ground, never under it
        if (age % 10 == 0) {
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
            double target = Math.max(baseY, ground + 0.3);
            y += Mth.clamp(target - y, -0.2, 0.2);
            // drifted over a building or off natural ground: thin away (never over a footprint)
            if (!MistClientEvents.naturalTop(level, Mth.floor(x), Mth.floor(z)))
                leaving = Math.min(leaving, 0.999f);
        }
        if (leaving < 1)
            leaving -= 0.05f;
        setPos(x, y, z);
        rot += spin;
        quadSize = (float) (MistMath.size(start, age, lifetime) / 2);
        // a slow breath on top of the fade, so a still pool of mist is never quite still
        double breathe = 0.85 + 0.15 * Math.sin(age * 0.02 + phase);
        alpha = (float) (peak * MistMath.fade(age, lifetime) * breathe * Math.max(0, leaving));
        if (age % 40 == 0)
            tint();
    }

    /** A unit running through pushes the wisp aside: the client sees every unit near the camera, no server code. */
    private void stir() {
        double w = quadSize * 2;
        AABB box = new AABB(x - w / 2, y - 3, z - w / 2, x + w / 2, y + 2, z + w / 2);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
            double v = Math.hypot(e.getX() - e.xo, e.getZ() - e.zo);
            double[] p = MistMath.push(x - e.getX(), z - e.getZ(), v, w);
            pushX += p[0];
            pushZ += p[1];
        }
        double m = Math.hypot(pushX, pushZ);
        if (m > MistMath.MAX_PUSH) {
            pushX *= MistMath.MAX_PUSH / m;
            pushZ *= MistMath.MAX_PUSH / m;
        }
    }

    /**
     * A flat quad at the wisp's height, turned by its slow spin, drawn ONCE through MIST_SHEET (culling off there, so
     * one winding shows from above and below). r2 drew both windings at half alpha through vanilla's particle shader,
     * whose 0.1 alpha cut threw nearly every pixel away. Written here rather than through SingleQuadParticle.render
     * (that path drew the #334 birds pure black in the eyes runs); MIST_SHEET's format has no lightmap - the colour
     * carries the light (MistMath.colour).
     */
    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        if (alpha <= 0.002f)
            return;
        Vec3 cam = camera.getPosition();
        float cx = (float) (Mth.lerp(partialTicks, xo, x) - cam.x);
        float cy = (float) (Mth.lerp(partialTicks, yo, y) - cam.y);
        float cz = (float) (Mth.lerp(partialTicks, zo, z) - cam.z);
        float r = Mth.lerp(partialTicks, oRot, rot);
        float s = quadSize;
        float fx = Mth.cos(r) * s, fz = Mth.sin(r) * s;     // one half-axis of the quad; the other is (-fz, fx)
        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        vert(buffer, cx - fx + fz, cy, cz - fz - fx, u0, v0);
        vert(buffer, cx + fx + fz, cy, cz + fz - fx, u1, v0);
        vert(buffer, cx + fx - fz, cy, cz + fz + fx, u1, v1);
        vert(buffer, cx - fx - fz, cy, cz - fz + fx, u0, v1);
    }

    private void vert(VertexConsumer b, float x, float y, float z, float u, float v) {
        b.vertex(x, y, z).uv(u, v).color(rCol, gCol, bCol, alpha).endVertex();
    }

    /**
     * The mist's own sheet: the particle atlas through position_tex_color (it discards only alpha 0, where vanilla's
     * particle shader discards under 0.1 - MistMath.VANILLA_PARTICLE_CUT), blended, no depth write (overlapping soft
     * wisps in three layers add up instead of cutting square holes in each other) and no culling. Forge renders custom
     * particle render types after vanilla's (ForgeHooksClient.makeParticleRenderTypeComparator).
     */
    public static final ParticleRenderType MIST_SHEET = new ParticleRenderType() {
        @Override
        public void begin(BufferBuilder builder, TextureManager textures) {
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        }

        @Override
        public void end(Tesselator tesselator) {
            tesselator.end();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
        }

        @Override
        public String toString() {
            return "BAT_MIST_SHEET";
        }
    };

    /**
     * Full-bright: the colour carries the light (MistMath.colour dims it at night). r1 took the open sky's lightmap,
     * and at dawn that made the mist the grass's own value - invisible from the RTS camera (eyes run 40815085922).
     */
    private static final int SKY_LIGHT = LightTexture.FULL_BRIGHT;

    @Override
    public int getLightColor(float partialTick) {
        return SKY_LIGHT;
    }

    @Override
    public void remove() {
        if (counted) {
            counted = false;
            alive = Math.max(0, alive - 1);
        }
        super.remove();
    }

    @Override
    public boolean shouldCull() {
        return false;                       // a 5-12 block quad round a point-sized box: never pop at the screen edge
    }

    @Override
    public ParticleRenderType getRenderType() {
        return MIST_SHEET;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new MistParticle(level, x, y, z, dx, sprites);
        }
    }
}
