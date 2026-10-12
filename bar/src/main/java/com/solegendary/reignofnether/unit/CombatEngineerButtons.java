package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.buildings.defence.Defences;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.hud.buttons.Button;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A combat engineer's build menu (design/combat_engineers.md): its faction's field defences only - the buildings
 * {@link CombatEngineers#mayStart} allows - instead of the whole faction menu a worker gets. Client side only: called
 * from the engineers' {@code getAbilityButtons()} behind the same {@code FMLEnvironment.dist == CLIENT} check the
 * workers use for {@code Faction.getBuildingButtons()}. The HUD asks for the card every frame, so the buttons are built
 * once per faction and reused (like the faction's own list); none of them has a hotkey (every build-menu key is taken,
 * design/defences.md), so they can never clash with a card key.
 */
public final class CombatEngineerButtons {
    private CombatEngineerButtons() { }

    private static final Map<Faction, List<Button>> CACHE = new HashMap<>();

    public static List<Button> forFaction(Faction faction) {
        if (faction == null)
            return List.of();
        List<Button> buttons = CACHE.get(faction);
        if (buttons == null) {
            buttons = new ArrayList<>();
            Defences.Set set = Defences.of(faction);
            if (set != null)
                for (Building b : set.all())
                    if (CombatEngineers.mayStart(b))
                        buttons.add(b.getBuildButton(null));
            CACHE.put(faction, buttons);
        }
        return buttons;
    }
}
