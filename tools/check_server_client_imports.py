#!/usr/bin/env python3
"""Server code must not import client-only classes (improve-004, Oct 22 2026).

CommandsServerEvents called net.minecraft.client.resources.language.I18n.get on the server (unit action with an owner,
invalid rotation, resource add). On a dedicated server that class is absent: NoClassDefFoundError, an Error that
Brigadier does not catch, so the command (and the tick) dies. GameTests cannot show it - the GameTest server runs from
the full dev jar where client classes exist - so this is a static guard.

Checked files (the server/common side):
  - any class named *ServerEvents*, *Server.java, or *ServerboundPacket (packets run their handler on the server)
  - any class with @Mod.EventBusSubscriber that is not Dist.CLIENT-only
  - everything under commands/ except commands/lang/ (the client-side command bar)
A checked file fails when it imports net.minecraft.client.* or com.mojang.blaze3d.* and is not marked Dist.CLIENT.

ServerboundPacket classes keep client-side static helpers (they send the packet from the client) next to the server
handler; those are listed in CLIENT_HELPER_PACKETS with the reason, and their handler must stay free of client calls.
Use Component.translatable(...) (the client translates it) instead of I18n on the server.

Usage: python3 tools/check_server_client_imports.py      (exit 1 on a failure)
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "bar/src/main/java/com/solegendary/reignofnether")

CLIENT_IMPORT = re.compile(r"^\s*import\s+(net\.minecraft\.client\.[\w.]+|com\.mojang\.blaze3d\.[\w.]+)\s*;", re.M)
SUBSCRIBER = re.compile(r"@Mod\.EventBusSubscriber")

# packet file (relative to SRC) -> why its client import is fine: only static client-side senders use it
CLIENT_HELPER_PACKETS = {
    "guiscreen/TopdownGuiServerboundPacket.java": "closeTopdownGui() client sender; handler is server-only",
    "ability/BuildingAbilityServerboundPacket.java": "doAbility() client sender; handler only sends a clientbound packet",
    "player/PlayerServerboundPacket.java": "static client senders (teleportPlayer, startRTS ...); handler is server-only",
    "fogofwar/FogOfWarServerboundPacket.java": "setServerFog() client sender; handler is server-only",
}


def is_server_side(rel, text):
    name = os.path.basename(rel)
    if "Dist.CLIENT" in text:
        return False
    if rel.startswith("mixin/"):
        return False
    if rel.startswith("commands/lang/"):  # the typed command bar: client-side (CommandBarClientEvents), uses Minecraft
        return False
    if rel.startswith("commands/"):
        return True
    if "ServerEvents" in name or name.endswith("Server.java") or name.endswith("ServerboundPacket.java"):
        return True
    return bool(SUBSCRIBER.search(text))


def main():
    problems, checked = [], 0
    for dirpath, _, files in os.walk(SRC):
        for f in files:
            if not f.endswith(".java"):
                continue
            path = os.path.join(dirpath, f)
            rel = os.path.relpath(path, SRC).replace(os.sep, "/")
            text = open(path, encoding="utf-8").read()
            if not is_server_side(rel, text):
                continue
            checked += 1
            if rel in CLIENT_HELPER_PACKETS:
                continue
            for m in CLIENT_IMPORT.finditer(text):
                line = text.count("\n", 0, m.start()) + 1
                problems.append(f"{rel}:{line} imports {m.group(1)} - absent on a dedicated server "
                                f"(NoClassDefFoundError). Use Component.translatable / server-side classes.")
    if checked < 50:
        problems.append(f"only {checked} server-side files found under {SRC} - the file selection no longer matches")
    for p in problems:
        print("FAIL:", p)
    if problems:
        sys.exit(1)
    print(f"server client-import guard: ok ({checked} server-side files)")


if __name__ == "__main__":
    main()
