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

- **Window:** a page served from `127.0.0.1` on a random free port (a random token in the path), opened as an Edge app
  window (`msedge.exe --app=URL --window-size=1200,640`; Edge from Program Files (x86), Program Files, the user's
  LocalAppData or the App Paths registry key). No Edge: the default browser opens it and the page says so. If no
  browser can be opened, an error box shows the address.
- **Look:** styled on the Beyond All Reason launcher; background `brand/launcher-bg.jpg` (embedded), page
  `launcher.html` (embedded).
- **Data:** your build = sha + UTC date-time from the game jar's name in the instance's `mods` folder; GitHub (public
  API, no login, at most 3 calls a launch): main's head, then `compare/{last played}...{main}` (merge commits "Merge pull
  request #N" -> #N + title) or, before the first PLAY, the last 8 merged PRs; then the open PRs ("Coming soon", tagged
  draft / ready). Per-PR test status would cost one call per PR, so it is not shown. GitHub unreachable: amber status
  line, PLAY still works.
- **PLAY** writes `%LOCALAPPDATA%\BeyondAndTabs\launcher-state.json` (main's head sha + time) and starts
  `prismlauncher.exe -l BeyondAndTabs-test`; the instance's packwiz pre-launch command updates the game. There is no
  "Update" checkbox: Prism runs the pre-launch command without a shell, so it can't be skipped per launch without
  rewriting `instance.cfg`.
- **Buttons:** Open log (the instance's `logs` folder), Open game folder, Troubleshooting
  (`pack/INSTALL-for-testers.txt` on GitHub), Report a bug (new issue).
- **Stops** when the page stops its 5 s heartbeat (20 s grace), after PLAY, or after 30 minutes at most.
- **Off switch / manual way:** start Prism Launcher yourself (`%LOCALAPPDATA%\BeyondAndTabs\PrismLauncher\prismlauncher.exe`)
  and launch `BeyondAndTabs-test`; to drop the launcher, delete `launcher.exe` and point the shortcut back at Prism.
- **Headless check:** `BeyondAndTabs-Setup.exe --launch --no-window` starts the server, prints the URL and opens
  nothing; `GET <url>api/state` returns the page's JSON, `POST <url>api/quit` stops it.
