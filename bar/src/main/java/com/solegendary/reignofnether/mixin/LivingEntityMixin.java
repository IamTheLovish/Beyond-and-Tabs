package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.items.UnitInventory;
import com.solegendary.reignofnether.items.UnitItems;
import com.solegendary.reignofnether.registrars.BlockRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.registrars.ParticleRegistrar;
import com.solegendary.reignofnether.resources.ResourceSources;
import com.solegendary.reignofnether.sounds.SoundAction;
import com.solegendary.reignofnether.sounds.SoundClientboundPacket;
import com.solegendary.reignofnether.survival.SurvivalServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.unit.units.villagers.MilitiaUnit;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnitProfession;
import com.solegendary.reignofnether.util.ParticleUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {

    public LivingEntityMixin(EntityType<?> pEntityType, Level pLevel) {
        super(pEntityType, pLevel);
    }

    @Inject(
            method = "tick",
            at = @At("TAIL")
    )
    public void tick(CallbackInfo ci) {
        if (this.level().isClientSide())
            if (this.hasEffect(MobEffects.LEVITATION))
                ParticleUtil.spawnFlyingCloudParticles(this);
    }

    @Inject(
            method = "onChangedBlock",
            at = @At("TAIL")
    )
    protected void onChangedBlock(BlockPos pPos, CallbackInfo ci) {
        LivingEntity le = (LivingEntity) (Object) this;

        if (!this.level().isClientSide() && le instanceof Unit unit) {
            boolean canFrost = false;
            boolean canMagma = false;
            boolean canNetherrack = false;

            if (SurvivalServerEvents.isEnabled() && SurvivalServerEvents.ENEMY_OWNER_NAME.equals(unit.getOwnerName())) {
                canFrost = true;
                canNetherrack = true;
            }
            if (le instanceof UnitInventory inv) {
                if (inv.isHolding(UnitItems.FROST_WALKER_BOOTS))
                    canFrost = true;
                if (inv.isHolding(UnitItems.MAGMA_WALKER_BOOTS))
                    canMagma = true;
            }
            liquidWalkerOnEntityMoved(le, pPos, 1, canFrost, canMagma, canNetherrack);
        }
    }

    private void liquidWalkerOnEntityMoved(LivingEntity pLiving, BlockPos pPos, int pWalkerLevel,
                                           boolean doFrost, boolean doMagma, boolean doNetherrack) {
        if (!doFrost && !doMagma && !doNetherrack)
            return;
        if (!pLiving.onGround())
            return;
        Level pLevel = pLiving.level();

        float f = (float) Math.min(16, 2 + pWalkerLevel);
        int r = (int) f;
        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
        Vec3 livingPos = pLiving.position();

        for (BlockPos blockpos : BlockPos.betweenClosed(pPos.offset(-r, -1, -r), pPos.offset(r, -1, r))) {
            if (!blockpos.closerToCenterThan(livingPos, f))
                continue;

            // only convert liquid that has open air above it
            above.set(blockpos.getX(), blockpos.getY() + 1, blockpos.getZ());
            if (!pLevel.getBlockState(above).isAir())
                continue;

            BlockState state = pLevel.getBlockState(blockpos);
            Block block = state.getBlock();

            // cheap early-out: only source liquid blocks are ever converted
            if (block != Blocks.WATER && block != Blocks.LAVA)
                continue;
            if (state.getValue(LiquidBlock.LEVEL) != 0)
                continue;

            if (doFrost && block == Blocks.WATER)
                tryReplaceLiquid(pLiving, pLevel, blockpos, state, FluidTags.WATER,
                        Blocks.FROSTED_ICE);
            else if (doMagma && block == Blocks.LAVA)
                tryReplaceLiquid(pLiving, pLevel, blockpos, state, FluidTags.LAVA,
                        BlockRegistrar.TEMPORARY_WALKABLE_MAGMA_BLOCK.get());
            else if (doNetherrack && block == Blocks.LAVA)
                tryReplaceLiquid(pLiving, pLevel, blockpos, state, FluidTags.LAVA,
                        Blocks.NETHERRACK);
        }
    }

    private void tryReplaceLiquid(LivingEntity pLiving, Level pLevel, BlockPos pos, BlockState liquidState,
                                  TagKey<Fluid> fluidTag, Block replacement) {
        BlockState newState = replacement.defaultBlockState();
        if (liquidState.getFluidState().is(fluidTag) &&
                newState.canSurvive(pLevel, pos) &&
                pLevel.isUnobstructed(newState, pos, CollisionContext.empty()) &&
                !ForgeEventFactory.onBlockPlace(pLiving,
                        BlockSnapshot.create(pLevel.dimension(), pLevel, pos), Direction.UP)) {

            pLevel.setBlockAndUpdate(pos, newState);
            pLevel.scheduleTick(pos, replacement, Mth.nextInt(pLiving.getRandom(), 60, 120));
        }
    }

    @Shadow public float getDamageAfterArmorAbsorb(DamageSource pDamageSource, float pDamageAmount) { return 0f; }
    @Shadow public float getDamageAfterMagicAbsorb(DamageSource pDamageSource, float pDamageAmount) { return 0f; }
    @Shadow public float getAbsorptionAmount() { return 0f; }
    @Shadow public void setAbsorptionAmount(float pAbsorptionAmount) { }
    @Shadow public CombatTracker getCombatTracker() { return null; }
    @Shadow public float getHealth() { return 0f; }
    @Shadow public void setHealth(float pHealth) { }

    private static final float CRITICAL_HIT_MULTIPLIER = 3f;

    // Unit attackers that have already hit this entity in the current game tick (ids), see unitHitsStack
    @Unique private long ron$unitHitTick = Long.MIN_VALUE;
    @Unique private int[] ron$unitHitIds;
    @Unique private int ron$unitHitCount;

    /**
     * RTS damage is additive (as in BAR): every unit that hits a target deals its full damage, however many hit it in
     * the same moment. Vanilla's hurt window (invulnerableTime > 10 swallows any hit not bigger than the last one) used
     * to eat about a third of focus-fire hits, because Unit.tick only clears it in the target's own tick - two hits
     * between two of its ticks collapsed into one. So a hit whose source is a unit (melee, its projectiles, abilities
     * sourced to it) opens the window first - unless that same unit already hit this entity this tick, which keeps a
     * single attacker from double-dipping (one swing applied twice, a volley of one ability on one target).
     * Non-unit damage (fall, fire, lava, players' own hits) never clears the window, so it keeps its vanilla immunity
     * after any hit. Cost: one instanceof on every hurt; a tiny per-entity id list only for entities units hit.
     */
    @Inject(
            method = "hurt",
            at = @At("HEAD")
    )
    private void unitHitsStack(DamageSource pSource, float pAmount, CallbackInfoReturnable<Boolean> cir) {
        Entity attacker = pSource.getEntity();
        if (!(attacker instanceof Unit) || attacker == (Object) this || this.level().isClientSide())
            return;
        long now = this.level().getGameTime();
        if (now != ron$unitHitTick) {
            ron$unitHitTick = now;
            ron$unitHitCount = 0;
        }
        int id = attacker.getId();
        for (int i = 0; i < ron$unitHitCount; i++)
            if (ron$unitHitIds[i] == id)
                return;   // second hit by the same unit this tick: vanilla's window applies
        if (ron$unitHitIds == null)
            ron$unitHitIds = new int[4];
        else if (ron$unitHitCount == ron$unitHitIds.length)
            ron$unitHitIds = java.util.Arrays.copyOf(ron$unitHitIds, ron$unitHitCount * 2);   // a big scrum: rare
        ron$unitHitIds[ron$unitHitCount++] = id;
        this.invulnerableTime = 0;
    }

    @Inject(
            method = "actuallyHurt",
            at = @At("HEAD"),
            cancellable = true
    )
    protected void actuallyHurt(DamageSource pDamageSource, float pDamageAmount, CallbackInfo ci) {


        // ensure projectiles from units do the damage of the unit, not the item,
        // and that armour and anti-armour effects are considered through absorption
        if ((pDamageSource.is(DamageTypeTags.IS_PROJECTILE) ||
            (!pDamageSource.is(DamageTypeTags.WITCH_RESISTANT_TO) &&
            !pDamageSource.is(DamageTypeTags.BYPASSES_SHIELD) &&
            !pDamageSource.is(DamageTypeTags.BYPASSES_ARMOR) &&
            !pDamageSource.is(DamageTypeTags.BYPASSES_RESISTANCE) &&
            pDamageSource.is(DamageTypes.MOB_ATTACK))) &&
            pDamageSource.getEntity() instanceof AttackerUnit attackerUnit) {

            ci.cancel();

            boolean isHuntableAnimal = ResourceSources.isHuntableAnimal((LivingEntity) (Object) this);

            // secondary damage (a slam's splash, a cleave) passes its own amount through UnitDamage.hurtExplicit:
            // keep it rather than rewriting it to the unit's full hit; armour, resist and absorption still apply
            boolean explicitAmount = com.solegendary.reignofnether.unit.UnitDamage.isExplicitAmount();
            float dmg = explicitAmount ? pDamageAmount : attackerUnit.getUnitAttackDamage();
            boolean isMelee = pDamageSource.is(DamageTypes.MOB_ATTACK) && !pDamageSource.is(DamageTypeTags.IS_PROJECTILE);
            if (isMelee && !explicitAmount && !(pDamageSource.getEntity() instanceof WorkerUnit))
                dmg += AttackerUnit.getWeaponDamageModifier(attackerUnit);
            // water is a decision (unit/Terrain): a wading attacker hits for 0.75x, melee, projectile or splash
            dmg *= com.solegendary.reignofnether.unit.Terrain.damageMultiplier(pDamageSource.getEntity());

            if (isHuntableAnimal) {
                if (pDamageSource.getEntity() instanceof MilitiaUnit)
                    dmg = 1f;
                else if (pDamageSource.getEntity() instanceof VillagerUnit vUnit &&
                        vUnit.getUnitProfession() == VillagerUnitProfession.HUNTER) {
                    dmg = vUnit.isVeteran() ? 2f : 1.5f;
                } else if (!(pDamageSource.getEntity() instanceof WorkerUnit)) {
                    dmg *= 0.5f;
                }
            }

            if (this instanceof Unit unit) {
                dmg *= (1 - unit.getUnitPhysicalArmorPercentage());
                if (pDamageSource.is(DamageTypeTags.IS_PROJECTILE))
                    dmg *= (1 - unit.getUnitRangedArmorPercentage());
                dmg *= (1 - unit.getUnitResistPercentage());

                if (!this.level().isClientSide() && !explicitAmount && getRandom().nextFloat() < attackerUnit.getCriticalChance()) {
                    dmg *= CRITICAL_HIT_MULTIPLIER;
                    SoundClientboundPacket.playSoundAtPos(SoundAction.CRITICAL_HIT, this.blockPosition());
                    ParticleUtil.addParticleExplosion(ParticleRegistrar.FLOATING_CRIT.get(), 10,
                            ((Entity) attackerUnit).level(), this.getEyePosition());
                }
            }

            if (!this.isInvulnerableTo(pDamageSource)) {
                dmg = ForgeHooks.onLivingHurt((LivingEntity) (Object) this, pDamageSource, dmg);
                if (dmg <= 0.0F) {
                    return;
                }
                dmg = this.getDamageAfterMagicAbsorb(pDamageSource, dmg);
                float f1 = Math.max(dmg - this.getAbsorptionAmount(), 0.0F);
                this.setAbsorptionAmount(this.getAbsorptionAmount() - (dmg - f1));
                float f = dmg - f1;
                if (f > 0.0F && f < 3.4028235E37F) {
                    Entity entity = pDamageSource.getEntity();
                    if (entity instanceof ServerPlayer) {
                        ServerPlayer serverplayer = (ServerPlayer)entity;
                        serverplayer.awardStat(Stats.DAMAGE_DEALT_ABSORBED, Math.round(f * 10.0F));
                    }
                }
                ForgeHooks.onLivingDamage((LivingEntity) (Object) this, pDamageSource, f1);
                if (f1 != 0.0F) {
                    this.setHealth(this.getHealth() - f1);
                    this.getCombatTracker().recordDamage(pDamageSource, f1);
                    this.gameEvent(GameEvent.ENTITY_DAMAGE);
                }
            }
        }
    }

    /**
     * Water is a decision (unit/Terrain): a wading ground unit moves at half its LAND speed. Vanilla's water branch of
     * travel() already makes a mob 5-7x slower than on land through this friction (0.8), so the rule is set here, as
     * the friction that gives half speed, rather than as a modifier on the speed attribute stacked on the crawl.
     * Units that override getWaterSlowDown themselves (Drowned, dogs, cats, Strider, Steam Runner at 0.94) bypass this,
     * which is right: those are the unit's own wading rule.
     */
    @Inject(
            method = "getWaterSlowDown",
            at = @At("HEAD"),
            cancellable = true
    )
    private void bt$wadingFriction(CallbackInfoReturnable<Float> cir) {
        LivingEntity le = (LivingEntity) (Object) this;
        if (le instanceof Unit)
            cir.setReturnValue(com.solegendary.reignofnether.unit.Terrain.waterFriction(le, 0.8f));
    }

    @Shadow public boolean hasEffect(MobEffect pEffect) { return true; }
    @Shadow public MobEffectInstance getEffect(MobEffect pEffect) { return null; }

    @Shadow public abstract RandomSource getRandom();

    @Inject(
            method = "baseTick",
            at = @At("TAIL")
    )
    public void baseTick(CallbackInfo ci) {
        if (!this.level().isClientSide && this.remainingFireTicks > 0 && !fireImmune() && hasEffect(MobEffectRegistrar.INTENSE_HEAT.get())) {
            int amp = Math.min(39, getEffect(MobEffectRegistrar.INTENSE_HEAT.get()).getAmplifier());
            int fireTicks = (this.remainingFireTicks + 10);
            if (fireTicks % (80 - (amp * 2)) == 0) {
                this.hurt(this.damageSources().onFire(), 1.0F);
            }
        }
    }

    @Inject(
            method = "isPushable",
            at = @At("HEAD"),
            cancellable = true
    )
    public void isPushable(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity le = (LivingEntity) (Object) this;
        if (le.hasEffect(MobEffectRegistrar.PHASING.get())) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "push",
            at = @At("HEAD"),
            cancellable = true
    )
    public void push(Entity entity, CallbackInfo ci) {
        LivingEntity le = (LivingEntity) (Object) this;
        if (le.hasEffect(MobEffectRegistrar.PHASING.get())) {
            ci.cancel();
        }
    }

    @Inject(
            method = "doPush",
            at = @At("HEAD"),
            cancellable = true
    )
    public void doPush(Entity entity, CallbackInfo ci) {
        LivingEntity le = (LivingEntity) (Object) this;
        if (le.hasEffect(MobEffectRegistrar.PHASING.get())) {
            ci.cancel();
        }
    }
}
