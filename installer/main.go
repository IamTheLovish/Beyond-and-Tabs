// Beyond and Tabs one-click installer for Windows.
//
// What it does, in order:
//  1. Creates %LOCALAPPDATA%\BeyondAndTabs
//  2. Downloads Prism Launcher (portable) and unpacks it there
//  3. Downloads the tester modpack (.mrpack) published by the game's author
//  4. Creates a ready-to-play Prism instance: Minecraft 1.20.1 + Forge + every mod
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
	mrpackURL    = "https://github.com/christiansaab-hue/Beyond-and-Tabs/releases/download/test-pack/BeyondAndTabs-test.mrpack"
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

	// 2. Modpack
	fmt.Println("\n[2/4] Downloading the " + appName + " modpack ...")
	packPath := filepath.Join(tmp, "pack.mrpack")
	if err := download(mrpackURL, packPath, ""); err != nil {
		return fmt.Errorf("downloading modpack: %w", err)
	}
	idx, err := readIndex(packPath)
	if err != nil {
		return fmt.Errorf("reading modpack: %w", err)
	}
	fmt.Printf("      %s (%s) - Minecraft %s, Forge %s\n", idx.Name, idx.VersionID, idx.Dependencies.Minecraft, idx.Dependencies.Forge)

	// 3. Instance files
	fmt.Println("\n[3/4] Setting up the game instance ...")
	if err := writeInstance(instDir, idx); err != nil {
		return err
	}
	if err := extractOverrides(packPath, mcDir); err != nil {
		return fmt.Errorf("copying pack files: %w", err)
	}
	// keep a copy of the pack index next to the instance for reference
	os.MkdirAll(filepath.Join(instDir, "mrpack"), 0o755)
	copyFile(packPath, filepath.Join(instDir, "mrpack", "BeyondAndTabs-test.mrpack"))

	// 4. Mods
	need := 0
	for _, f := range idx.Files {
		if f.Env["client"] != "unsupported" {
			need++
		}
	}
	fmt.Printf("\n[4/4] Downloading %d mods ...\n", need)
	n := 0
	for _, f := range idx.Files {
		if f.Env["client"] == "unsupported" {
			continue
		}
		n++
		dest := filepath.Join(mcDir, filepath.FromSlash(f.Path))
		name := filepath.Base(dest)
		if exists(dest) && sha1Of(dest) == f.Hashes["sha1"] {
			fmt.Printf("      (%2d/%d) %s - ok\n", n, need, name)
			continue
		}
		fmt.Printf("      (%2d/%d) %s\n", n, need, name)
		var last error
		for _, u := range f.DL {
			if last = download(u, dest, f.Hashes["sha1"]); last == nil {
				break
			}
		}
		if last != nil {
			return fmt.Errorf("downloading %s: %w", name, last)
		}
	}
	os.RemoveAll(tmp)

	// Shortcuts
	fmt.Println("\nCreating shortcuts ...")
	if err := shortcuts(prismExe, prismDir); err != nil {
		fmt.Println("      (could not create shortcuts:", err, ")")
	}

	fmt.Println("\nAll done!")
	fmt.Println("Prism Launcher will open now. Add your Microsoft (Minecraft) account when it asks,")
	fmt.Println("then select \"" + instanceName + "\" and click Launch. The first start takes a minute")
	fmt.Println("while Java and Minecraft itself are fetched.")
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

func writeInstance(instDir string, idx *mrIndex) error {
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
			"ManagedPack=true",
			"ManagedPackType=modrinth",
			"ManagedPackName=" + idx.Name,
			"ManagedPackVersionName=" + idx.VersionID,
			"",
		}, "\n")
		if err := os.WriteFile(cfgPath, []byte(cfg), 0o644); err != nil {
			return err
		}
	}
	pack := map[string]any{
		"formatVersion": 1,
		"components": []map[string]any{
			{"uid": "org.lwjgl3", "version": "3.3.1", "cachedName": "LWJGL 3", "cachedVersion": "3.3.1", "cachedVolatile": true, "dependencyOnly": true},
			{"uid": "net.minecraft", "version": idx.Dependencies.Minecraft, "cachedName": "Minecraft", "cachedVersion": idx.Dependencies.Minecraft, "important": true,
				"cachedRequires": []map[string]any{{"uid": "org.lwjgl3", "suggests": "3.3.1"}}},
			{"uid": "net.minecraftforge", "version": idx.Dependencies.Forge, "cachedName": "Forge", "cachedVersion": idx.Dependencies.Forge,
				"cachedRequires": []map[string]any{{"uid": "net.minecraft", "equals": idx.Dependencies.Minecraft}}},
		},
	}
	j, _ := json.MarshalIndent(pack, "", "    ")
	return os.WriteFile(filepath.Join(instDir, "mmc-pack.json"), j, 0o644)
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
