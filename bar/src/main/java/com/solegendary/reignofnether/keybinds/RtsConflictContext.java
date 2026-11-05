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
    // BAR's build-tab keys Z / X / C / V (BuildCategoryKeys): while the build menu shows they open a tab instead of the
    // ability and order slots on the same keys (BuildTabs.onKey), so the overlap is intentional
    BUILDER,
    // BAR's grid keys Q W E R / A S D F / Z X C V (BuildGroup.SLOT_KEYS): live only while a build tab is open, then they
    // win over the orders, the tab keys and a camera tap on the same keys (BuildTabs.onKey), so the overlap is intentional
    BUILD_GRID;

    @Override
    public boolean isActive() {
        return KeyConflictContext.IN_GAME.isActive();
    }

    @Override
    public boolean conflicts(IKeyConflictContext other) {
        return other == this;
    }
}
