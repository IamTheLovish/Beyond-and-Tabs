package main

// lovish, Oct 23 02:27-02:32: the launcher should "just be a desktop bubble" - his cat in a round bubble on the
// desktop that starts the game when clicked - and the installer should set it up for friends automatically.

import (
	"bytes"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
)

func TestBubbleScriptIsEmbedded(t *testing.T) {
	ps := string(bubblePS)
	for _, want := range []string{
		"'BeyondAndTabsBubble'",             // one bubble at a time
		"'" + bubbleImage + "'",             // the cat it draws is the file installBubble writes
		"'lovish.lnk'",                      // the Desktop play shortcut comes first (lovish's own starts his server)
		`'PrismLauncher\prismlauncher.exe'`, // and the installed Prism is the fallback
		`'-l "` + instanceName + `"'`,       // launching this installer's instance
		"Application]::Run",                 // it is a window that stays up
	} {
		if !strings.Contains(ps, want) {
			t.Fatalf("bubble.ps1 lost %q", want)
		}
	}
}

func TestBubbleScriptParses(t *testing.T) {
	pwsh, err := exec.LookPath("pwsh")
	if err != nil {
		t.Skip("pwsh not installed")
	}
	f := filepath.Join(t.TempDir(), "bubble.ps1")
	if err := os.WriteFile(f, bubblePS, 0o644); err != nil {
		t.Fatal(err)
	}
	check := `$t=$null; $e=$null; [System.Management.Automation.Language.Parser]::ParseFile($env:BUBBLE_PS,[ref]$t,[ref]$e) | Out-Null; $e | ForEach-Object { $_.ToString() }; exit $e.Count`
	cmd := exec.Command(pwsh, "-NoProfile", "-NonInteractive", "-Command", check)
	cmd.Env = append(os.Environ(), "BUBBLE_PS="+f)
	out, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("bubble.ps1 has PowerShell syntax errors: %v\n%s", err, out)
	}
}

func TestInstallBubbleWritesScriptAndCat(t *testing.T) {
	root := t.TempDir()
	dir, err := installBubble(root)
	if err != nil {
		t.Fatal(err)
	}
	if dir != filepath.Join(root, "Bubble") {
		t.Fatalf("bubble folder is %s, want <root>\\Bubble (next to PrismLauncher)", dir)
	}
	if got, err := os.ReadFile(filepath.Join(dir, "bubble.ps1")); err != nil || !bytes.Equal(got, bubblePS) {
		t.Fatalf("bubble.ps1 not written: %v", err)
	}
	if got, err := os.ReadFile(filepath.Join(dir, bubbleImage)); err != nil || !bytes.Equal(got, catPNG) {
		t.Fatalf("the cat is not written next to the bubble: %v", err)
	}
}

func TestBubbleShortcutStartsTheBubbleHidden(t *testing.T) {
	ps := bubbleShortcutScript(`C:\B`, `C:\P\lovish.ico`)
	for _, want := range []string{
		"'" + bubbleShortcut + ".lnk'",
		"-WindowStyle Hidden",
		`-File "C:\B\bubble.ps1"`,
		`$s.IconLocation = 'C:\P\lovish.ico,0'`,
		"Join-Path 'C:\\B' 'bubble.lnk'", // the copy the bubble's "Start with Windows" uses
		"Start-Process",                  // the bubble appears right after installing
	} {
		if !strings.Contains(ps, want) {
			t.Fatalf("bubble shortcut script lost %q:\n%s", want, ps)
		}
	}
	if strings.Contains(ps, "'"+shortcutName+".lnk'") {
		t.Fatal("the bubble step must not touch the lovish play shortcut")
	}
}
