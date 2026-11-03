package com.solegendary.reignofnether.commands.rtsapi;

import com.solegendary.reignofnether.mixin.ObjectiveCriteriaAccessor;

import net.minecraft.world.scores.criteria.ObjectiveCriteria;

public class ResourceObjectiveCriteria {
	
	public static final ObjectiveCriteria FOOD =  ObjectiveCriteriaAccessor.registerCustomCriteria("resources.food", true, ObjectiveCriteria.RenderType.INTEGER);
	public static final ObjectiveCriteria WOOD = ObjectiveCriteriaAccessor.registerCustomCriteria("resources.wood", true, ObjectiveCriteria.RenderType.INTEGER);
	public static final ObjectiveCriteria ORE = ObjectiveCriteriaAccessor.registerCustomCriteria("resources.ore", true, ObjectiveCriteria.RenderType.INTEGER);
	// how many units a player has (was "resources.population": no population - lovish, Oct 29)
	public static final ObjectiveCriteria UNITS = ObjectiveCriteriaAccessor.registerCustomCriteria("resources.units", true, ObjectiveCriteria.RenderType.INTEGER);
	
	public static void init() {
	}
}
