# One-click Windows installer

`BeyondAndTabs-Setup.exe` is a small Go program (no bundled payload) that sets a tester up in one run:

1. downloads Prism Launcher 11.1.1 (portable) into `%LOCALAPPDATA%\BeyondAndTabs\PrismLauncher`
2. downloads `BeyondAndTabs-test.mrpack` from the `test-pack` release of this repo
3. writes a Prism instance (`BeyondAndTabs-test`) for Minecraft 1.20.1 + Forge, copies the pack overrides
   (our jar) and downloads every Modrinth mod listed in the pack, checking each sha1
4. copies itself to `%LOCALAPPDATA%\BeyondAndTabs\launcher.exe` and puts a "lovish" shortcut (lovish's cat as the icon, `brand/cat.ico`) on the Desktop and in the Start Menu (`launcher.exe --launch`, see "Launcher" below), removing the old "Beyond and Tabs" one; the instance icon in Prism is the cat too (`brand/cat.png` -> `icons/lovish_cat.png`)
5. opens Prism Launcher, which asks for the Microsoft account and then launches

Re-running it is safe and is the "repair / update to the current pack" path: existing files are kept, missing or
changed mods are fetched.

Build locally: `GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -ldflags="-s -w" -o BeyondAndTabs-Setup.exe .`
`rsrc_windows_amd64.syso` carries the exe icon (`icon.ico`, the cat); CI regenerates it each build with `go run github.com/akavel/rsrc@v0.10.2 -ico icon.ico -arch amd64`. `go test ./...` covers the branding and the launcher (jar names, merge commits, status line, shortcut, GitHub call budget).

`.github/workflows/installer.yml` builds it on every change under `installer/` and uploads it to the `installer`
release. The download page is `docs/index.html` (GitHub Pages, served from `/docs` on `main`).

## Launcher (`--launch`)

The same exe is also the game's launcher. A normal install copies it to `%LOCALAPPDATA%\BeyondAndTabs\launcher.exe`
and points the "lovish" Desktop + Start Menu shortcuts at `launcher.exe --launch` (cat icon, Prism folder as working
dir, console minimised and detached). Shortcuts earlier installers made (this Prism with `-l "BeyondAndTabs-test"`) are
repointed; any other "lovish" shortcut is left alone. A plain run of `BeyondAndTabs-Setup.exe` is still the installer.

- **High-performance GPU:** each time the launcher starts (Windows only), it checks Windows' per-app graphics setting for
  the game's Java (the `JavaPath` in the instance's or Prism's settings, else `PrismLauncher\java\java-runtime-gamma\bin\javaw.exe`).
  If there is no setting yet, it writes `GpuPreference=2;` (High performance) under
  `HKCU\Software\Microsoft\DirectX\UserGpuPreferences`, so laptops run the game on the discrete GPU. A value that already
  exists is never changed. A failed write shows "Could not set high-performance GPU" in the window and the game still starts.
  Off switch / manual way: Settings > System > Display > Graphics (Windows 11), pick the Java (`javaw.exe`) and choose
  a setting; any choice, even "Let Windows decide", stops the launcher from writing. Deleting the value makes the launcher write it again.
- **Window:** a page served from `127.0.0.1` on a random free port (a random token in the path), opened as an Edge app
  window (`msedge.exe --app=URL --user-data-dir=%LOCALAPPDATA%BeyondAndTabsauncher-edge --window-size=<1190x610 logical px x display scale>`; the page scales its 1200x640 stage to any window size; Edge from Program Files (x86), Program Files, the user's
  LocalAppData or the App Paths registry key). No Edge: the default browser opens it and the page says so. If no
  browser can be opened, an error box shows the address.
- **Look:** styled on the Beyond All Reason launcher; background `brand/launcher-bg.jpg` (embedded), page
  `launcher.html` (embedded).
- **Data:** your build = sha + UTC date-time from the game jar's name in the instance's `mods` folder; GitHub (public
  API, no login, at most 4 calls a launch): main's head, the newest tester pack (the last commit on
  `IamTheLovish/beyond-and-tabs-pack` main, "Tester pack <date>-<sha>", published by CI from main's builds), then
  `compare/{last played}...{main}` (merge commits "Merge pull request #N" -> #N + title) or, before the first PLAY, the
  last 8 merged PRs; then the open PRs ("Coming soon", tagged draft / ready). Per-PR test status would cost one call per
  PR, so it is not shown. GitHub unreachable: amber status line, PLAY still works.
- **Status line** compares your game jar with the newest pack (not with main, which is often ahead for ~25 min while CI
  builds the pack): "Up to date: pack <sha> (<date>)" (green), "Update ready: pack <sha> - press PLAY to install it"
  (gold). When main has merges the pack does not have yet, " · main has newer merges still being packed (usually ~25
  min)" is added.
- **PLAY** runs the packwiz update itself before Prism starts: the same command as the instance's `PreLaunchCommand`
  (read from `instance.cfg`, `$INST_JAVA` replaced by the game's Java), in the game's `minecraft` folder, console
  hidden. Its output is written to `%LOCALAPPDATA%\BeyondAndTabs\packwiz-update.log` (overwritten each PLAY). Then the
  launcher re-reads the game jar's name and checks it is the newest pack:
  - match: "Installed pack <sha>." and Prism starts (`prismlauncher.exe -l BeyondAndTabs-test`);
  - no match: nothing starts. The warning reads "Update did not install pack <sha> (you have <sha>): <last packwiz
    output line>". Press PLAY again to launch anyway; that second press says "Launching anyway" and does not run the
    update again;
  - the newest pack unknown (GitHub unreachable): Prism starts with a warning that the install is not checked.
  
  It writes `%LOCALAPPDATA%\BeyondAndTabs\launcher-state.json` (the installed pack's sha + time) before Prism starts.
  Prism's own pre-launch command still runs; after the PLAY update it does nothing. There is no "Update" checkbox:
  Prism runs the pre-launch command without a shell, so it can't be skipped per launch without rewriting `instance.cfg`.
- **Manual update** (without the launcher): in `%LOCALAPPDATA%\BeyondAndTabs\PrismLauncher\instances\BeyondAndTabs-test\minecraft`
  run `"<javaw.exe>" -jar packwiz-installer-bootstrap.jar https://raw.githubusercontent.com/IamTheLovish/beyond-and-tabs-pack/main/windows/pack.toml`
  (`<javaw.exe>` is the instance's `JavaPath`, else `PrismLauncher\java\java-runtime-gamma\bin\javaw.exe`; the
  bootstrap jar is in that folder). Then check the jar name in `mods` ends with the sha of "Tester pack" on the pack repo.
- **Buttons:** Open log (the instance's `logs` folder), Open game folder, Troubleshooting
  (`pack/INSTALL-for-testers.txt` on GitHub), Report a bug (new issue).
- **Stops** when the page stops its 5 s heartbeat (20 s grace), after PLAY, or after 30 minutes at most.
- **Off switch / manual way:** start Prism Launcher yourself (`%LOCALAPPDATA%\BeyondAndTabs\PrismLauncher\prismlauncher.exe`)
  and launch `BeyondAndTabs-test`; to drop the launcher, delete `launcher.exe` and point the shortcut back at Prism.
- **Headless check:** `BeyondAndTabs-Setup.exe --launch --no-window` starts the server, prints the URL and opens
  nothing; `GET <url>api/state` returns the page's JSON, `POST <url>api/quit` stops it.
