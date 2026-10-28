package main

// lovish, Oct 23 00:00: the installer's launcher must look like his own - the cat photo as the icon and the
// name "lovish" - instead of the default Prism block and "Beyond and Tabs".

import (
	"bytes"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestBrandAssetsAreEmbedded(t *testing.T) {
	if !bytes.HasPrefix(catPNG, []byte("\x89PNG\r\n\x1a\n")) {
		t.Fatalf("brand/cat.png is not an embedded PNG (%d bytes)", len(catPNG))
	}
	if len(catICO) < 6 || !bytes.Equal(catICO[:4], []byte{0, 0, 1, 0}) {
		t.Fatalf("brand/cat.ico is not an embedded .ico (%d bytes)", len(catICO))
	}
}

func TestNewInstanceUsesTheCatIcon(t *testing.T) {
	cfg := instanceConfig("0123456789abcdef")
	if !strings.Contains(cfg, "\niconKey="+catIconKey+"\n") {
		t.Fatalf("new instance.cfg should use iconKey=%s:\n%s", catIconKey, cfg)
	}
	if strings.Contains(cfg, "iconKey=default") {
		t.Fatal("new instance.cfg still has the default icon")
	}
	for _, want := range []string{"name=" + instanceName, "OverrideCommands=true", preLaunch} {
		if !strings.Contains(cfg, want) {
			t.Fatalf("instance.cfg lost %q:\n%s", want, cfg)
		}
	}
}

func TestExistingInstanceGetsTheCatIcon(t *testing.T) {
	old := "[General]\nname=BeyondAndTabs-test\niconKey=default\nuuid=x\n"
	got := withCatIcon(old)
	if !strings.Contains(got, "\niconKey="+catIconKey+"\n") || strings.Contains(got, "iconKey=default") {
		t.Fatalf("an old instance keeps its default icon:\n%s", got)
	}
	if strings.Count(got, "iconKey=") != 1 {
		t.Fatalf("iconKey duplicated:\n%s", got)
	}
	noKey := "[General]\nname=BeyondAndTabs-test\n"
	if got := withCatIcon(noKey); !strings.Contains(got, "iconKey="+catIconKey) {
		t.Fatalf("a cfg without iconKey gets none:\n%s", got)
	}
	// running the installer twice changes nothing more
	if twice := withCatIcon(withCatIcon(old)); twice != withCatIcon(old) {
		t.Fatalf("not idempotent:\n%s", twice)
	}
}

func TestShortcutIsNamedLovishWithTheCat(t *testing.T) {
	ps := shortcutScript(`C:\L\launcher.exe`, `C:\P\prismlauncher.exe`, `C:\P`, `C:\P\lovish.ico`)
	if !strings.Contains(ps, "'"+shortcutName+".lnk'") {
		t.Fatalf("shortcut is not named %q:\n%s", shortcutName, ps)
	}
	if shortcutName != "lovish" {
		t.Fatalf("shortcut name is %q, lovish asked for \"lovish\"", shortcutName)
	}
	if !strings.Contains(ps, `$s.IconLocation = 'C:\P\lovish.ico,0'`) {
		t.Fatalf("shortcut does not use the cat icon:\n%s", ps)
	}
	if !strings.Contains(ps, `-l "`+instanceName+`"`) {
		t.Fatalf("shortcut no longer launches the instance:\n%s", ps)
	}
	// the old "Beyond and Tabs" shortcut from earlier installs is removed, so testers don't get two
	if !strings.Contains(ps, "'"+appName+".lnk'") || !strings.Contains(ps, "Remove-Item") {
		t.Fatalf("old %q shortcut is not cleaned up:\n%s", appName, ps)
	}
}

func TestShortcutNeverReplacesSomeoneElsesLovishShortcut(t *testing.T) {
	// lovish's own desktop "lovish" shortcut starts his server - only a "lovish" shortcut that opens THIS Prism is
	// rewritten, and the old "Beyond and Tabs" one goes only when it opens this Prism
	ps := shortcutScript(`C:\L\launcher.exe`, `C:\P\prismlauncher.exe`, `C:\P`, `C:\P\lovish.ico`)
	if !strings.Contains(ps, "(-not (Test-Path -LiteralPath $lnk)) -or ($ws.CreateShortcut($lnk).TargetPath -eq $exe)") {
		t.Fatalf("an existing lovish shortcut could be overwritten:\n%s", ps)
	}
	if !strings.Contains(ps, "$old.TargetPath -eq $exe") {
		t.Fatalf("the old shortcut is removed even when it isn't ours:\n%s", ps)
	}
}

func TestInstallBrandWritesTheIcons(t *testing.T) {
	dir := t.TempDir()
	ico, err := installBrand(dir)
	if err != nil {
		t.Fatal(err)
	}
	png, err := os.ReadFile(filepath.Join(dir, "icons", catIconKey+".png"))
	if err != nil || !bytes.Equal(png, catPNG) {
		t.Fatalf("Prism icon not written to icons/%s.png: %v", catIconKey, err)
	}
	if got, err := os.ReadFile(ico); err != nil || !bytes.Equal(got, catICO) {
		t.Fatalf("shortcut icon not written to %s: %v", ico, err)
	}
	if filepath.Dir(ico) != dir {
		t.Fatalf("shortcut icon should live in the Prism folder, got %s", ico)
	}
}
