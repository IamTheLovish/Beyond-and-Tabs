#!/usr/bin/env bash
# Beyond and Tabs - Linux setup in one paste (lovish, Oct 23: "the way I can copy n paste it ... linux might be
# getting in the way"):
#
#   curl -fsSL https://raw.githubusercontent.com/IamTheLovish/Beyond-and-Tabs/main/installer/install.sh | bash
#
# Adds the game to the Prism Launcher you already have (normal or Flatpak); without one it fetches Prism's AppImage.
# The instance updates itself to the newest build before every launch (packwiz), shows lovish's cat, and gets a
# "lovish" app shortcut. Safe to run again (repair / update): your own instance settings are kept.
# Test switches: BAT_NO_DOWNLOAD=1 (offline; assets from BAT_ASSETS), BAT_NO_LAUNCH=1.
set -euo pipefail

INSTANCE="BeyondAndTabs-test"
PACK_URL="https://raw.githubusercontent.com/IamTheLovish/beyond-and-tabs-pack/main/windows/pack.toml"
RAW="https://raw.githubusercontent.com/IamTheLovish/Beyond-and-Tabs/main/installer/brand"
PRISM_APPIMAGE_URL="https://github.com/PrismLauncher/PrismLauncher/releases/download/11.1.1/PrismLauncher-Linux-x86_64.AppImage"
BOOTSTRAP_URL="https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest/download/packwiz-installer-bootstrap.jar"
ICON_KEY="lovish_cat"

say() { printf '%s\n' "$*"; }
fetch() {  # fetch URL DEST [local asset name]
  if [ "${BAT_NO_DOWNLOAD:-}" = 1 ]; then
    if [ -n "${3:-}" ] && [ -f "${BAT_ASSETS:-}/$3" ]; then cp "${BAT_ASSETS}/$3" "$2"; else : > "$2"; fi
    return 0
  fi
  curl -fL --retry 3 -o "$2" "$1"
}

say "=== Beyond and Tabs setup (Linux) ==="
DATA_HOME="${XDG_DATA_HOME:-$HOME/.local/share}"
FLATPAK_DATA="$HOME/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher"

# 1. Which Prism: the one already here (Flatpak, then a normal install), else the AppImage
if [ -d "$FLATPAK_DATA" ]; then
  DATA="$FLATPAK_DATA"; LAUNCH="flatpak run org.prismlauncher.PrismLauncher"
  say "Found Prism Launcher (Flatpak)."
elif [ -d "$DATA_HOME/PrismLauncher" ] && command -v prismlauncher >/dev/null 2>&1; then
  DATA="$DATA_HOME/PrismLauncher"; LAUNCH="prismlauncher"
  say "Found Prism Launcher."
else
  DATA="$DATA_HOME/PrismLauncher"
  APP="$DATA_HOME/BeyondAndTabs/PrismLauncher.AppImage"
  mkdir -p "$(dirname "$APP")"
  if command -v prismlauncher >/dev/null 2>&1; then
    LAUNCH="prismlauncher"
  else
    if [ ! -s "$APP" ]; then say "Downloading Prism Launcher ..."; fetch "$PRISM_APPIMAGE_URL" "$APP"; fi
    chmod +x "$APP"
    LAUNCH="$APP"
  fi
fi

# 2. The instance (kept if it exists: only the cat and the pack updater are made sure of)
INST="$DATA/instances/$INSTANCE"
mkdir -p "$INST/minecraft" "$DATA/icons"
CFG="$INST/instance.cfg"
PRELAUNCH="PreLaunchCommand=\\\"\$INST_JAVA\\\" -jar packwiz-installer-bootstrap.jar $PACK_URL"
if [ ! -f "$CFG" ]; then
  cat > "$CFG" <<EOF
[General]
ConfigVersion=1.3
InstanceType=OneSix
name=$INSTANCE
iconKey=$ICON_KEY
uuid=$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')
AutomaticJava=true
OverrideCommands=true
$PRELAUNCH
EOF
else
  tmp="$CFG.tmp"
  grep -v -e '^iconKey=' -e '^PreLaunchCommand=' -e '^OverrideCommands=' "$CFG" > "$tmp" || true
  awk -v k="iconKey=$ICON_KEY" '{print} /^\[General\]/{print k}' "$tmp" > "$CFG"
  printf 'OverrideCommands=true\n%s\n' "$PRELAUNCH" >> "$CFG"
  rm -f "$tmp"
fi
if [ ! -f "$INST/mmc-pack.json" ]; then
  cat > "$INST/mmc-pack.json" <<'EOF'
{
    "components": [
        {"uid": "org.lwjgl3", "version": "3.3.1", "dependencyOnly": true},
        {"uid": "net.minecraft", "version": "1.20.1", "important": true},
        {"uid": "net.minecraftforge", "version": "47.4.0"}
    ],
    "formatVersion": 1
}
EOF
fi
fetch "$BOOTSTRAP_URL" "$INST/minecraft/packwiz-installer-bootstrap.jar"
fetch "$RAW/cat.png" "$DATA/icons/$ICON_KEY.png" cat.png || say "(could not fetch the cat icon - the game works without it)"

# 3. "lovish" shortcut with the cat (never replaces a "lovish" shortcut that isn't ours)
APPS="$DATA_HOME/applications"
mkdir -p "$APPS"
write_desktop() {
  if [ -f "$1" ] && ! grep -q "$INSTANCE" "$1"; then return 0; fi
  cat > "$1" <<EOF
[Desktop Entry]
Type=Application
Name=lovish
Comment=Play Beyond and Tabs
Exec=$LAUNCH -l "$INSTANCE"
Icon=$DATA/icons/$ICON_KEY.png
Terminal=false
Categories=Game;
EOF
  chmod +x "$1"
}
write_desktop "$APPS/lovish.desktop"
[ -d "$HOME/Desktop" ] && write_desktop "$HOME/Desktop/lovish.desktop"

say ""
say "All done! Open \"lovish\" from your apps menu (or the Desktop)."
say "Add your Microsoft (Minecraft) account when Prism asks, then select \"$INSTANCE\" and click Launch."
say "The first start takes a few minutes; every later launch updates the game automatically."
say "If the AppImage won't start, install FUSE (e.g. sudo apt install libfuse2) or Prism from Flathub, then run this again."
if [ "${BAT_NO_LAUNCH:-}" != 1 ]; then
  nohup $LAUNCH -l "$INSTANCE" >/dev/null 2>&1 &
fi
