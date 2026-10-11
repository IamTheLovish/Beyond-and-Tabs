package com.solegendary.reignofnether.brass;

/**
 * A Brassworks war-machine (design/brassworks_plan.md section 3): anything that carries Heat and can take a Tinker's
 * Field Upgrade. The Iron Warden, Steam Runner, Flak Cart, Mortar Crawler and the Guildmaster's Deployed Turret shoot
 * and heat up; the Gyro Scout and the Shield Projector are machines that never shoot ({@link #heatPerShot} 0) but can
 * still be plated. Crews (Rifle Squad, Sapper, Tinker, the Guildmaster) are people, not machines.
 * <p>
 * Heat (0-{@link Heat#MAX}) and the vent countdown are synced entity data so the client can draw the steam; the
 * upgrade byte drives the render layer. Overclock ticks are server-only. The static rules live in {@link Heat}.
 */
public interface Machine {
    int heat();
    void setHeat(int heat);
    int ventTicks();
    void setVentTicks(int ticks);
    /** Heat added per attack; 0 = a machine that never heats. */
    int heatPerShot();
    /** 0 = none, {@link Heat#UPGRADE_PLATING} or {@link Heat#UPGRADE_BARREL}. */
    byte upgrade();
    void setUpgrade(byte upgrade);
    int overclockTicks();
    void setOverclockTicks(int ticks);
    /** True for machines that shoot (a Long Barrel makes sense); melee machines are refused the barrel. */
    boolean isRangedMachine();
}
