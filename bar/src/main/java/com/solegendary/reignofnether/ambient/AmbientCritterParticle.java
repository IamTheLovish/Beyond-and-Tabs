package com.solegendary.reignofnether.ambient;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * A critter of the ambient life (AmbientLifeClientEvents): a butterfly flutters a meandering path a little above the
 * grass, wings flapping (two sprites); a firefly drifts slowly at knee height, its glow pulsing. Client only, no
 * physics, no entity: a particle that keeps itself above the ground it flies over and fades in and out. Counted while
 * alive so the spawner keeps under its caps.
 */
@OnlyIn(Dist.CLIENT)
public class AmbientCritterParticle extends TextureSheetParticle {

    public static int butterflies = 0, fireflies = 0;

    // butterfly wing tints over a white sprite: cabbage white, brimstone yellow, orange tip, blue, a rare red admiral
    private static final float[][] WINGS = { { 0.96f, 0.96f, 0.92f }, { 1.0f, 0.92f, 0.45f }, { 1.0f, 0.65f, 0.25f },
        { 0.55f, 0.7f, 1.0f }, { 0.85f, 0.25f, 0.2f } };

    private final boolean firefly;
    private final SpriteSet sprites;
    private float heading, turn;
    private final float hover, phase;
    private boolean counted = true;

    AmbientCritterParticle(ClientLevel level, double x, double y, double z, boolean firefly, SpriteSet sprites) {
        super(level, x, y, z);
        this.firefly = firefly;
        this.sprites = sprites;
        this.hasPhysics = false;
        this.gravity = 0;
        this.heading = random.nextFloat() * Mth.TWO_PI;
        this.phase = random.nextFloat() * Mth.TWO_PI;
        if (firefly) {
            fireflies++;
            this.lifetime = 120 + random.nextInt(140);
            this.quadSize = 0.07f + random.nextFloat() * 0.03f;
            this.hover = 0.5f + random.nextFloat() * 1.3f;
            this.rCol = 0.85f;
            this.gCol = 1.0f;
            this.bCol = 0.45f;
        } else {
            butterflies++;
            this.lifetime = 240 + random.nextInt(260);
            this.quadSize = 0.16f + random.nextFloat() * 0.06f;
            this.hover = 0.4f + random.nextFloat() * 1.4f;
            float[] w = WINGS[random.nextFloat() < 0.06f ? 4 : random.nextInt(4)];
            this.rCol = w[0];
            this.gCol = w[1];
            this.bCol = w[2];
        }
        this.alpha = 0;
        setSpriteFromAge(sprites);
        pickFrame();
    }

    private void pickFrame() {
        if (!firefly)
            setSprite(sprites.get((age / 2) % 2, 1));
        else
            setSprite(sprites.get(0, 1));
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        // wander: the heading turns smoothly, now and then a sharper swerve
        turn = turn * 0.9f + (random.nextFloat() - 0.5f) * (firefly ? 0.08f : 0.25f);
        if (!firefly && random.nextFloat() < 0.03f)
            turn += (random.nextFloat() - 0.5f) * 1.2f;
        heading += turn;
        double speed = firefly ? 0.012 : 0.06 + 0.03 * Mth.sin(age * 0.4f + phase);
        double ground = level.getHeight(Heightmap.Types.WORLD_SURFACE, Mth.floor(x), Mth.floor(z));   // the client has only WORLD_SURFACE and MOTION_BLOCKING
        double target = ground + hover + (firefly ? 0.25 * Mth.sin(age * 0.05f + phase) : 0.35 * Mth.sin(age * 0.3f + phase));
        double dy = Mth.clamp((target - y) * 0.15, -0.08, 0.08);
        move(Mth.cos(heading) * speed, dy, Mth.sin(heading) * speed);
        // fade in over 1 s, out over the last 1.5 s; a firefly's glow pulses
        float in = Math.min(1f, age / 20f), out = Math.min(1f, (lifetime - age) / 30f);
        float glow = firefly ? 0.35f + 0.65f * Math.max(0f, Mth.sin(age * 0.12f + phase)) : 1f;
        alpha = in * out * glow;
        pickFrame();
    }

    @Override
    public void remove() {
        if (counted) {
            counted = false;
            if (firefly)
                fireflies = Math.max(0, fireflies - 1);
            else
                butterflies = Math.max(0, butterflies - 1);
        }
        super.remove();
    }

    /**
     * Our own quad rather than SingleQuadParticle.render (that path drew the #334 birds pure black under the pack's
     * particle optimiser, eyes runs 40739251237 / 40744639247): camera-facing, vanilla's corner order and uv, light from
     * getLightColor (the world's for a butterfly, full bright for a firefly). The fog-of-war mixin only covers
     * SingleQuadParticle.render, so the check is here too.
     */
    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        if (alpha <= 0.002f)
            return;
        if (FogOfWarClientEvents.isEnabled()
            && !FogOfWarClientEvents.isInBrightChunk(new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z))))
            return;
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(partialTicks, xo, x) - cam.x);
        float py = (float) (Mth.lerp(partialTicks, yo, y) - cam.y);
        float pz = (float) (Mth.lerp(partialTicks, zo, z) - cam.z);
        org.joml.Quaternionf q = new org.joml.Quaternionf(camera.rotation());
        float size = getQuadSize(partialTicks);
        int light = getLightColor(partialTicks);
        float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
        float[][] corners = { { -1, -1, u1, v1 }, { -1, 1, u1, v0 }, { 1, 1, u0, v0 }, { 1, -1, u0, v1 } };
        for (float[] c : corners) {
            org.joml.Vector3f v = new org.joml.Vector3f(c[0], c[1], 0).rotate(q).mul(size).add(px, py, pz);
            buffer.vertex(v.x(), v.y(), v.z()).uv(c[2], c[3]).color(rCol, gCol, bCol, alpha).uv2(light).endVertex();
        }
    }

    @Override
    public int getLightColor(float partialTick) {
        return firefly ? LightTexture.FULL_BRIGHT : super.getLightColor(partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @OnlyIn(Dist.CLIENT)
    public static class ButterflyProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public ButterflyProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new AmbientCritterParticle(level, x, y, z, false, sprites);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class FireflyProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public FireflyProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new AmbientCritterParticle(level, x, y, z, true, sprites);
        }
    }
}
