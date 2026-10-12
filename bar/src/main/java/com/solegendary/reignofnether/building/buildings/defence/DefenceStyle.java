package com.solegendary.reignofnether.building.buildings.defence;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.Block;

/**
 * One faction's look for its static defences (design/defences.md): the structure/registry suffix, the portrait blocks
 * and how its towers' shots look and sound. Mechanics and prices are shared (DefenceCosts, the placements) - only this
 * differs, so a Sunforged rampart and a Gravebound bone palisade read the same on the battlefield (BAR readability)
 * while each looks like its faction.
 *
 * @param factionName the faction's key path ("villagers", "verdant_court", ...): Defences maps it to the Faction later,
 *                    because the buildings register before the factions do
 * @param suffix      the id / structure suffix ("villagers", "monsters", ... - every defence id ends in it)
 * @param shot        the tracer particle a tower's shot draws from muzzle to target
 * @param heavyShot   the anti-T3 defence's tracer (bigger, slower weapon)
 */
public record DefenceStyle(String factionName, String suffix, Block wallPortrait, Block gatePortrait, Block towerPortrait,
                           Block antiT3Portrait, Block trapPortrait, ParticleOptions shot, ParticleOptions heavyShot,
                           SoundEvent shotSound, SoundEvent heavySound) { }
