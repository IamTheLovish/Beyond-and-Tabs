// Beyond and Tabs one-click installer for Windows.
//
// What it does, in order:
//  1. Creates %LOCALAPPDATA%\BeyondAndTabs
//  2. Downloads Prism Launcher (portable) and unpacks it there
//  3. Reads the live modpack (the packwiz pack the game's builds publish) for its Minecraft and Forge versions
//  4. Creates a Prism instance that updates itself from that pack before every launch (packwiz), so a tester always
//     runs the same build as the host's server - the mods are fetched on the first launch
//  5. Puts a "Beyond and Tabs" shortcut on the Desktop and in the Start Menu
//  6. Opens Prism Launcher so you can sign in with your Microsoft account and play
//
// Re-running it is safe: anything already in place is kept, anything missing is fetched.
package main

import (
	"archive/zip"
	"bufio"
	"crypto/rand"
	"crypto/sha1"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"
)

const (
	appName      = "Beyond and Tabs"
	instanceName = "BeyondAndTabs-test"
	prismVersion = "11.1.1"
	prismZipURL  = "https://github.com/PrismLauncher/PrismLauncher/releases/download/" + prismVersion + "/PrismLauncher-Windows-MSVC-Portable-" + prismVersion + ".zip"
	mrpackURL    = "https://github.com/IamTheLovish/Beyond-and-Tabs/releases/download/test-pack/BeyondAndTabs-test.mrpack"
	// the live pack (published by publish-pack.yml from main's builds) and packwiz's bootstrap, which updates the
	// instance from it before every launch (Prism's pre-launch command)
	packTomlURL  = "https://raw.githubusercontent.com/IamTheLovish/beyond-and-tabs-pack/main/windows/pack.toml"
	bootstrapURL = "https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest/download/packwiz-installer-bootstrap.jar"
	preLaunch    = `PreLaunchCommand=\"$INST_JAVA\" -jar packwiz-installer-bootstrap.jar ` + packTomlURL
)

var httpClient = &http.Client{Timeout: 10 * time.Minute}

type mrIndex struct {
	Name         string `json:"name"`
	VersionID    string `json:"versionId"`
	Dependencies struct {
		Minecraft string `json:"minecraft"`
		Forge     string `json:"forge"`
	} `json:"dependencies"`
	Files []struct {
		Path   string            `json:"path"`
		Hashes map[string]string `json:"hashes"`
		Env    map[string]string `json:"env"`
		DL     []string          `json:"downloads"`
		Size   int64             `json:"fileSize"`
	} `json:"files"`
}

func main() {
	fmt.Printf("=== %s installer ===\n\n", appName)
	if err := run(); err != nil {
		fmt.Printf("\nSomething went wrong: %v\n", err)
		fmt.Println("\nCheck your internet connection and run the installer again.")
		fmt.Print("Press Enter to close.")
		bufio.NewReader(os.Stdin).ReadString('\n')
		os.Exit(1)
	}
}

func run() error {
	base := os.Getenv("LOCALAPPDATA")
	if base == "" {
		return errors.New("LOCALAPPDATA is not set")
	}
	root := filepath.Join(base, "BeyondAndTabs")
	prismDir := filepath.Join(root, "PrismLauncher")
	prismExe := filepath.Join(prismDir, "prismlauncher.exe")
	instDir := filepath.Join(prismDir, "instances", instanceName)
	mcDir := filepath.Join(instDir, "minecraft")
	tmp := filepath.Join(root, "tmp")
	for _, d := range []string{root, prismDir, mcDir, tmp} {
		if err := os.MkdirAll(d, 0o755); err != nil {
			return err
		}
	}
	fmt.Println("Install folder:", root)

	// 1. Prism Launcher
	if exists(prismExe) {
		fmt.Println("\n[1/4] Prism Launcher already installed - skipping download.")
	} else {
		fmt.Println("\n[1/4] Downloading Prism Launcher " + prismVersion + " ...")
		zipPath := filepath.Join(tmp, "prism.zip")
		if err := download(prismZipURL, zipPath, ""); err != nil {
			return fmt.Errorf("downloading Prism Launcher: %w", err)
		}
		fmt.Println("      Unpacking ...")
		if err := unzip(zipPath, prismDir); err != nil {
			return fmt.Errorf("unpacking Prism Launcher: %w", err)
		}
		os.Remove(zipPath)
	}
	// portable mode + sensible defaults; skips most of the first-run wizard
	writeIfMissing(filepath.Join(prismDir, "portable.txt"), "Portable mode: all launcher data stays in this folder.\n")
	writeIfMissing(filepath.Join(prismDir, "prismlauncher.cfg"), strings.Join([]string{
		"[General]",
		"ConfigVersion=1.3",
		"AutomaticJavaDownload=true",
		"AutomaticJavaSwitch=true",
		"Language=en_US",
		"ApplicationTheme=system",
		"IconTheme=pe_colored",
		"SelectedInstance=" + instanceName,
		"",
	}, "\n"))

	// 2. The live pack: its Minecraft and Forge versions
	fmt.Println("\n[2/3] Reading the " + appName + " modpack ...")
	tomlPath := filepath.Join(tmp, "pack.toml")
	mc, forge := "1.20.1", "47.4.0"
	if err := download(packTomlURL, tomlPath, ""); err != nil {
		fmt.Println("      (could not read the pack, using Minecraft", mc, "+ Forge", forge, ":", err, ")")
	} else if m, f := packVersions(tomlPath); m != "" && f != "" {
		mc, forge = m, f
	}
	fmt.Printf("      Minecraft %s, Forge %s\n", mc, forge)

	// 3. Instance: updates itself from the pack before every launch
	fmt.Println("\n[3/3] Setting up the game instance ...")
	// an instance from the old installer has the frozen .mrpack's mods: move them aside so packwiz installs a clean set
	// (an old copy of the game's own jar next to the new one would stop Forge from starting)
	if exists(filepath.Join(mcDir, "mods")) && !exists(filepath.Join(mcDir, "packwiz.json")) {
		old := filepath.Join(mcDir, "mods.old-"+time.Now().Format("20060102-150405"))
		if err := os.Rename(filepath.Join(mcDir, "mods"), old); err == nil {
			fmt.Println("      Moved the old mods to", old)
		}
	}
	if err := writeInstance(instDir, mc, forge); err != nil {
		return err
	}
	if err := download(bootstrapURL, filepath.Join(mcDir, "packwiz-installer-bootstrap.jar"), ""); err != nil {
		return fmt.Errorf("downloading the pack updater: %w", err)
	}
	os.RemoveAll(tmp)

	// Shortcuts
	fmt.Println("\nCreating shortcuts ...")
	if err := shortcuts(prismExe, prismDir); err != nil {
		fmt.Println("      (could not create shortcuts:", err, ")")
	}

	fmt.Println("\nAll done!")
	fmt.Println("Prism Launcher will open now. Add your Microsoft (Minecraft) account when it asks,")
	fmt.Println("then select \"" + instanceName + "\" and click Launch. The first start takes a few minutes")
	fmt.Println("while Java, Minecraft and the mods are fetched (a small updater window shows the mods).")
	fmt.Println("Every later launch updates the game to the newest build automatically.")
	fmt.Println("\nNext time, use the \"" + appName + "\" shortcut on your Desktop.")
	time.Sleep(2 * time.Second)
	cmd := exec.Command(prismExe)
	cmd.Dir = prismDir
	return cmd.Start()
}

// ---------- helpers ----------

func exists(p string) bool { _, err := os.Stat(p); return err == nil }

func writeIfMissing(p, content string) {
	if !exists(p) {
		os.WriteFile(p, []byte(content), 0o644)
	}
}

func sha1Of(p string) string {
	f, err := os.Open(p)
	if err != nil {
		return ""
	}
	defer f.Close()
	h := sha1.New()
	io.Copy(h, f)
	return hex.EncodeToString(h.Sum(nil))
}

func download(url, dest, wantSha1 string) error {
	req, _ := http.NewRequest("GET", url, nil)
	req.Header.Set("User-Agent", "BeyondAndTabs-Setup/1.0 (github.com/christiansaab-hue/Beyond-and-Tabs)")
	resp, err := httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return fmt.Errorf("HTTP %s", resp.Status)
	}
	os.MkdirAll(filepath.Dir(dest), 0o755)
	part := dest + ".part"
	f, err := os.Create(part)
	if err != nil {
		return err
	}
	h := sha1.New()
	pw := &progress{total: resp.ContentLength}
	_, err = io.Copy(io.MultiWriter(f, h, pw), resp.Body)
	f.Close()
	pw.done()
	if err != nil {
		os.Remove(part)
		return err
	}
	if wantSha1 != "" && hex.EncodeToString(h.Sum(nil)) != wantSha1 {
		os.Remove(part)
		return errors.New("checksum mismatch")
	}
	os.Remove(dest)
	return os.Rename(part, dest)
}

type progress struct {
	total, n int64
	lastPct  int
	shown    bool
}

func (p *progress) Write(b []byte) (int, error) {
	p.n += int64(len(b))
	if p.total > 2_000_000 { // only show a bar for bigger files
		pct := int(p.n * 100 / p.total)
		if pct/5 != p.lastPct/5 {
			fmt.Printf("\r      %3d%%  %5.1f / %5.1f MB", pct, float64(p.n)/1e6, float64(p.total)/1e6)
			p.shown = true
		}
		p.lastPct = pct
	}
	return len(b), nil
}
func (p *progress) done() {
	if p.shown {
		fmt.Println()
	}
}

func unzip(zipPath, dest string) error {
	r, err := zip.OpenReader(zipPath)
	if err != nil {
		return err
	}
	defer r.Close()
	for _, f := range r.File {
		out := filepath.Join(dest, filepath.FromSlash(f.Name))
		if !strings.HasPrefix(out, filepath.Clean(dest)+string(os.PathSeparator)) {
			return fmt.Errorf("bad path in zip: %s", f.Name)
		}
		if f.FileInfo().IsDir() {
			os.MkdirAll(out, 0o755)
			continue
		}
		os.MkdirAll(filepath.Dir(out), 0o755)
		rc, err := f.Open()
		if err != nil {
			return err
		}
		w, err := os.Create(out)
		if err != nil {
			rc.Close()
			return err
		}
		_, err = io.Copy(w, rc)
		w.Close()
		rc.Close()
		if err != nil {
			return err
		}
	}
	return nil
}

func readIndex(packPath string) (*mrIndex, error) {
	r, err := zip.OpenReader(packPath)
	if err != nil {
		return nil, err
	}
	defer r.Close()
	for _, f := range r.File {
		if f.Name == "modrinth.index.json" {
			rc, err := f.Open()
			if err != nil {
				return nil, err
			}
			defer rc.Close()
			var idx mrIndex
			if err := json.NewDecoder(rc).Decode(&idx); err != nil {
				return nil, err
			}
			return &idx, nil
		}
	}
	return nil, errors.New("modrinth.index.json not found in pack")
}

// extractOverrides copies overrides/ and client-overrides/ from the pack into the minecraft dir.
func extractOverrides(packPath, mcDir string) error {
	r, err := zip.OpenReader(packPath)
	if err != nil {
		return err
	}
	defer r.Close()
	for _, f := range r.File {
		var rel string
		switch {
		case strings.HasPrefix(f.Name, "overrides/"):
			rel = strings.TrimPrefix(f.Name, "overrides/")
		case strings.HasPrefix(f.Name, "client-overrides/"):
			rel = strings.TrimPrefix(f.Name, "client-overrides/")
		default:
			continue
		}
		if rel == "" || f.FileInfo().IsDir() {
			continue
		}
		out := filepath.Join(mcDir, filepath.FromSlash(rel))
		if !strings.HasPrefix(out, filepath.Clean(mcDir)+string(os.PathSeparator)) {
			return fmt.Errorf("bad path in pack: %s", f.Name)
		}
		os.MkdirAll(filepath.Dir(out), 0o755)
		rc, err := f.Open()
		if err != nil {
			return err
		}
		w, err := os.Create(out)
		if err != nil {
			rc.Close()
			return err
		}
		_, err = io.Copy(w, rc)
		w.Close()
		rc.Close()
		if err != nil {
			return err
		}
		fmt.Println("      " + rel)
	}
	return nil
}

func writeInstance(instDir, mc, forge string) error {
	os.MkdirAll(instDir, 0o755)
	cfgPath := filepath.Join(instDir, "instance.cfg")
	if !exists(cfgPath) {
		b := make([]byte, 16)
		rand.Read(b)
		cfg := strings.Join([]string{
			"[General]",
			"ConfigVersion=1.3",
			"InstanceType=OneSix",
			"name=" + instanceName,
			"iconKey=default",
			"uuid=" + hex.EncodeToString(b),
			"AutomaticJava=true",
			"OverrideCommands=true",
			preLaunch,
			"",
		}, "\n")
		if err := os.WriteFile(cfgPath, []byte(cfg), 0o644); err != nil {
			return err
		}
	} else if data, err := os.ReadFile(cfgPath); err == nil && !strings.Contains(string(data), "packwiz-installer-bootstrap") {
		// an instance from the old installer: add the updater, drop the frozen-modpack marker
		var keep []string
		for _, line := range strings.Split(strings.ReplaceAll(string(data), "\r\n", "\n"), "\n") {
			if strings.HasPrefix(line, "ManagedPack") || strings.HasPrefix(line, "PreLaunchCommand=") || strings.HasPrefix(line, "OverrideCommands=") {
				continue
			}
			keep = append(keep, line)
		}
		out := strings.TrimRight(strings.Join(keep, "\n"), "\n") + "\nOverrideCommands=true\n" + preLaunch + "\n"
		if err := os.WriteFile(cfgPath, []byte(out), 0o644); err != nil {
			return err
		}
	}
	pack := map[string]any{
		"formatVersion": 1,
		"components": []map[string]any{
			{"uid": "org.lwjgl3", "version": "3.3.1", "cachedName": "LWJGL 3", "cachedVersion": "3.3.1", "cachedVolatile": true, "dependencyOnly": true},
			{"uid": "net.minecraft", "version": mc, "cachedName": "Minecraft", "cachedVersion": mc, "important": true,
				"cachedRequires": []map[string]any{{"uid": "org.lwjgl3", "suggests": "3.3.1"}}},
			{"uid": "net.minecraftforge", "version": forge, "cachedName": "Forge", "cachedVersion": forge,
				"cachedRequires": []map[string]any{{"uid": "net.minecraft", "equals": mc}}},
		},
	}
	j, _ := json.MarshalIndent(pack, "", "    ")
	return os.WriteFile(filepath.Join(instDir, "mmc-pack.json"), j, 0o644)
}

// packVersions reads [versions] minecraft / forge from a packwiz pack.toml.
func packVersions(path string) (mc, forge string) {
	data, err := os.ReadFile(path)
	if err != nil {
		return "", ""
	}
	inVersions := false
	for _, raw := range strings.Split(string(data), "\n") {
		line := strings.TrimSpace(raw)
		if strings.HasPrefix(line, "[") {
			inVersions = line == "[versions]"
			continue
		}
		if !inVersions {
			continue
		}
		k, v, ok := strings.Cut(line, "=")
		if !ok {
			continue
		}
		v = strings.Trim(strings.TrimSpace(v), `"`)
		switch strings.TrimSpace(k) {
		case "minecraft":
			mc = v
		case "forge":
			forge = v
		}
	}
	return mc, forge
}

func copyFile(src, dst string) error {
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()
	out, err := os.Create(dst)
	if err != nil {
		return err
	}
	defer out.Close()
	_, err = io.Copy(out, in)
	return err
}

func shortcuts(prismExe, prismDir string) error {
	ps := `
$ws = New-Object -ComObject WScript.Shell
foreach ($dir in @([Environment]::GetFolderPath('Desktop'), (Join-Path ([Environment]::GetFolderPath('StartMenu')) 'Programs'))) {
  $s = $ws.CreateShortcut((Join-Path $dir '` + appName + `.lnk'))
  $s.TargetPath = '` + prismExe + `'
  $s.Arguments = '-l "` + instanceName + `"'
  $s.WorkingDirectory = '` + prismDir + `'
  $s.IconLocation = '` + prismExe + `,0'
  $s.Description = 'Play ` + appName + `'
  $s.Save()
}`
	cmd := exec.Command("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps)
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("%v: %s", err, strings.TrimSpace(string(out)))
	}
	return nil
}
