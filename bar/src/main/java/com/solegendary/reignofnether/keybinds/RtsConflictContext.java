package com.solegendary.reignofnether.keybinds;

import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyConflictContext;

// Splits the mod's bindings into disjoint "contexts" so the Controls UI
// doesn't flag intentional default-key overlaps (LEFT for both panPlusX
// and rotCCW; M for minimapToggle/move) as conflicts.
public enum RtsConflictContext implements IKeyConflictContext {
    PAN,
    ROTATE,
    COMMAND,
    // BAR overview on Tab, sharing the key with Ctrl+Tab selection cycling
    OVERVIEW,
    // building-ghost rotation on [ / ], sharing "[" with a build-menu slot
    BUILD_GHOST,
    // HUD panel toggles that share a key with a command-card slot (the stats table on I, ability slot 8): the card's
    // button wins while it shows (HudClientEvents.isCardKey), so the overlap is intentional
    HUD_PANEL,
    // BAR's build-category keys Z / X / C / V (BuildCategoryKeys): while a builder is selected they win over the ability
    // and order slots on the same keys (HudClientEvents.onBuildCategoryKey), so the overlap is intentional
    BUILDER;

    @Override
    public boolean isActive() {
        return KeyConflictContext.IN_GAME.isActive();
    }

    @Override
    public boolean conflicts(IKeyConflictContext other) {
        return other == this;
    }
}
