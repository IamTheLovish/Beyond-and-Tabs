package com.solegendary.reignofnether.entities;

import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.util.MiscUtil;
import com.solegendary.reignofnether.util.ParticleUtil;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

public abstract class AbstractMagicProjectile extends AbstractHurtingProjectile {

    private static final int MAX_TICKS = 60;
    private final SimpleParticleType particleType;

    public AbstractMagicProjectile(EntityType<? extends AbstractHurtingProjectile> pEntityType, Level pLevel, SimpleParticleType particleType) {
        super(pEntityType, pLevel);
        this.particleType = particleType;
    }

    public AbstractMagicProjectile(EntityType<? extends AbstractHurtingProjectile> pEntityType,
                                   LivingEntity pShooter, double offsetX, double offsetY, double offsetZ,
                                   Level pLevel, SimpleParticleType particleType) {
        super(pEntityType, pShooter, offsetX, offsetY, offsetZ, pLevel);
        this.particleType = particleType;
    }

    @Override
    public void tick() {
        setDeltaMovement(getDeltaMovement().normalize());
        super.tick();
        if (tickCount > MAX_TICKS)
            this.discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult pResult) {
        if (!this.level().isClientSide && this.getOwner() instanceof AttackerUnit aUnit) {
            pResult.getEntity().hurt(damageSources().mobProjectile(this, (LivingEntity) this.getOwner()), aUnit.getUnitAttackDamage());
            impactEffect();
            discard();
        }
    }

    /**
     * The data-driven impact effect (assets/reignofnether/vfx/<id>.json, design/vfx.md) this projectile plays where it
     * lands, or null for the old burst of its trail particle. Overridden by projectiles on the vfx visual layer.
     */
    protected String impactVfx() {
        return null;
    }

    /** The impact: the vfx effect when there is one (fog-filtered BarFx event), else the old particle burst. */
    protected void impactEffect() {
        String id = impactVfx();
        if (id != null)
            com.solegendary.reignofnether.barfx.vfx.Vfx.at(level(), id, position());
        else
            ParticleUtil.addParticleExplosion(particleType, 10, level(), position());
    }

    @Override
    protected boolean shouldBurn() {
        return false;
    }

    @Override
    protected ParticleOptions getTrailParticle() {
        return particleType;
    }


    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource pSource, float pAmount) {
        return false;
    }
}
