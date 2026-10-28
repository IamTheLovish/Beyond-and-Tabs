package com.solegendary.reignofnether.ambient;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * One bird of a flock (AmbientLifeClientEvents.spawnFlock): flies a straight line high over the ground at a steady
 * pace, wings beating then gliding (AmbientLifeMath.wingFrame; sprites bird_0..2, tools/bird_sprites.py), its sprite
 * rolled on screen to its heading (AmbientLifeMath.screenRoll), and a soft shadow drawn flat on the ground below it,
 * thrown away from the sun (AmbientLifeMath.shadowOffsetX) - from the RTS camera the shadow is what tells the bird is
 * high up. Client only, no physics; counted while alive.
 *
 * The spawner passes the velocity as (dx, dz) and the lifetime in ticks as dy.
 */
@OnlyIn(Dist.CLIENT)
public class AmbientBirdParticle extends TextureSheetParticle {

    public static int birds = 0;

    private final SpriteSet sprites;
    private final int phase;
    private boolean counted = true;
    private double groundY;

    AmbientBirdParticle(ClientLevel level, double x, double y, double z, double vx, double lifetime, double vz,
                        SpriteSet sprites) {
        super(level, x, y, z);
        birds++;
        this.sprites = sprites;
        this.hasPhysics = false;
        this.gravity = 0;
        this.xd = vx;
        this.yd = 0;
        this.zd = vz;
        this.lifetime = Math.max(20, (int) lifetime);
        this.phase = random.nextInt(AmbientLifeMath.FLAP_CYCLE);
        this.quadSize = 0.42f + random.nextFloat() * 0.08f;
        float shade = 0.9f + random.nextFloat() * 0.15f;
        this.rCol = shade;
        this.gCol = shade;
        this.bCol = shade;
        this.alpha = 0;
        this.groundY = groundAt(x, z, y);
        this.roll = this.oRoll = rollNow();
        setSprite(sprites.get(1, 3));
    }

    private double groundAt(double x, double z, double fallback) {
        int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        return g <= level.getMinBuildHeight() ? fallback - 20 : g;
    }

    private float rollNow() {
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        return (float) AmbientLifeMath.screenRoll(xd, zd, cam.getYRot());
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        // a gentle rise and fall with the beats; the flight line itself stays straight
        double bob = 0.02 * Mth.sin((age + phase) * 0.13f);
        x += xd;
        y += bob;
        z += zd;
        setPos(x, y, z);
        if (age % 4 == 0)
            groundY = groundAt(x, z, groundY);
        roll = rollNow();
        if (Math.abs(roll - oRoll) > Math.PI)          // no spin through the long way round when the camera turns
            oRoll = roll;
        float in = Math.min(1f, age / 30f), out = Math.min(1f, (lifetime - age) / 30f);
        alpha = in * out;
        setSprite(sprites.get(AmbientLifeMath.wingFrame(age + phase), 3));
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        super.render(buffer, camera, partialTicks);
        // the shadow: a flat quad on the ground under the bird, offset away from the sun, both windings (no culling
        // question from any camera angle)
        double bx = Mth.lerp(partialTicks, xo, x), by = Mth.lerp(partialTicks, yo, y), bz = Mth.lerp(partialTicks, zo, z);
        double h = by - groundY;
        if (h < 1 || h > 80)
            return;
        double sx = bx + AmbientLifeMath.shadowOffsetX(h, level.getSunAngle(partialTicks));
        Vec3 cam = camera.getPosition();
        // a quarter block up: at the ground itself the short grass hid it (critic r31: "shadows invisible")
        float cx = (float) (sx - cam.x), cy = (float) (groundY + 0.25 - cam.y), cz = (float) (bz - cam.z);
        float s = quadSize * 1.1f;
        // heading in the ground plane: the shadow's head points along the flight
        double len = Math.sqrt(xd * xd + zd * zd);
        float fx = len > 1e-6 ? (float) (xd / len) : 0, fz = len > 1e-6 ? (float) (zd / len) : 1;
        float rx = -fz, rz = fx;                                   // a horizontal perpendicular
        TextureAtlasSprite sh = sprites.get(3, 3);
        float u0 = sh.getU0(), u1 = sh.getU1(), v0 = sh.getV0(), v1 = sh.getV1();
        float a = alpha * 0.9f;
        int light = getLightColor(partialTicks);
        // corners: head-left, head-right, tail-right, tail-left (texture top = head)
        float[][] c = {
            { cx + (fx - rx) * s, cz + (fz - rz) * s, u0, v0 },
            { cx + (fx + rx) * s, cz + (fz + rz) * s, u1, v0 },
            { cx + (-fx + rx) * s, cz + (-fz + rz) * s, u1, v1 },
            { cx + (-fx - rx) * s, cz + (-fz - rz) * s, u0, v1 } };
        for (int i = 0; i < 4; i++)
            vert(buffer, c[i], cy, a, light);
        for (int i = 3; i >= 0; i--)
            vert(buffer, c[i], cy, a, light);
    }

    private static void vert(VertexConsumer b, float[] c, float y, float a, int light) {
        b.vertex(c[0], y, c[1]).uv(c[2], c[3]).color(1f, 1f, 1f, a).uv2(light).endVertex();
    }

    @Override
    public void remove() {
        if (counted) {
            counted = false;
            birds = Math.max(0, birds - 1);
        }
        super.remove();
    }

    @Override
    public boolean shouldCull() {
        return false;                                 // the shadow is far below the bird's own box
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new AmbientBirdParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
