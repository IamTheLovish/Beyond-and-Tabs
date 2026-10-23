# One-click Windows installer

`BeyondAndTabs-Setup.exe` is a small Go program (no bundled payload) that sets a tester up in one run:

1. downloads Prism Launcher 11.1.1 (portable) into `%LOCALAPPDATA%\BeyondAndTabs\PrismLauncher`
2. downloads `BeyondAndTabs-test.mrpack` from the `test-pack` release of this repo
3. writes a Prism instance (`BeyondAndTabs-test`) for Minecraft 1.20.1 + Forge, copies the pack overrides
   (our jar) and downloads every Modrinth mod listed in the pack, checking each sha1
4. puts a "lovish" shortcut (lovish's cat as the icon, `brand/cat.ico`) on the Desktop and in the Start Menu (`prismlauncher.exe -l BeyondAndTabs-test`), removing the old "Beyond and Tabs" one; the instance icon in Prism is the cat too (`brand/cat.png` -> `icons/lovish_cat.png`)
5. opens Prism Launcher, which asks for the Microsoft account and then launches

Re-running it is safe and is the "repair / update to the current pack" path: existing files are kept, missing or
changed mods are fetched.

Build locally: `GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -ldflags="-s -w" -o BeyondAndTabs-Setup.exe .`
`rsrc_windows_amd64.syso` carries the exe icon (`icon.ico`, the cat); CI regenerates it each build with `go run github.com/akavel/rsrc@v0.10.2 -ico icon.ico -arch amd64`. `go test ./...` covers the branding.

`.github/workflows/installer.yml` builds it on every change under `installer/` and uploads it to the `installer`
release. The download page is `docs/index.html` (GitHub Pages, served from `/docs` on `main`).
