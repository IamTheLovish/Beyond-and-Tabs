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
		"-like '*bubble.ps1*'",              // ...unless it is the bubble's own shortcut (that would only reopen the bubble)
		"'launcher.exe'",                    // then the launcher next to the bubble folder (what's new, your build, PLAY)
		"'--launch'",
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
	ps := bubbleShortcutScript(`C:\B`, `C:\P\lovish.ico`, `C:\L\launcher.exe`, `C:\P\prismlauncher.exe`)
	for _, want := range []string{
		"'" + shortcutName + ".lnk'", // lovish, Oct 28: one Desktop icon, "lovish", and it brings the bubble back
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
}

func TestTheBubbleIsTheOnlyDesktopIcon(t *testing.T) {
	// lovish, Oct 28: "the launcher should be called lovish, not Beyond and Tabs bubble" - one Desktop icon
	if bubbleShortcut != shortcutName {
		t.Fatalf("the bubble's Desktop shortcut is %q, lovish asked for %q", bubbleShortcut, shortcutName)
	}
	ps := bubbleShortcutScript(`C:\B`, `C:\P\lovish.ico`, `C:\L\launcher.exe`, `C:\P\prismlauncher.exe`)
	for _, want := range []string{
		// only a "lovish" shortcut this installer made becomes the bubble one: missing, the launcher's, the earlier
		// installers' (this Prism, -l the instance) or already the bubble's - never lovish's own (it starts his server)
		"(-not (Test-Path -LiteralPath $lnk))",
		"$t.TargetPath -eq $exe",
		`($t.TargetPath -eq $prism) -and ($t.Arguments -eq '-l "` + instanceName + `"')`,
		"$t.Arguments -like '*bubble.ps1*'",
		// the "Beyond and Tabs bubble" shortcut earlier installers made goes, when it is the bubble's
		"'Beyond and Tabs bubble.lnk'",
		"Remove-Item",
	} {
		if !strings.Contains(ps, want) {
			t.Fatalf("bubble shortcut script lacks %q:\n%s", want, ps)
		}
	}
	// the play shortcut step leaves the Desktop to the bubble: the launcher "lovish" lives in the Start Menu
	sc := shortcutScript(`C:\L\launcher.exe`, `C:\P\prismlauncher.exe`, `C:\P`, `C:\P\lovish.ico`)
	if !strings.Contains(sc, "GetFolderPath('StartMenu')") {
		t.Fatalf("the launcher shortcut is not in the Start Menu:\n%s", sc)
	}
	if strings.Contains(sc, "foreach ($dir in @([Environment]::GetFolderPath('Desktop'), (Join-Path") {
		t.Fatalf("the launcher step still writes a Desktop shortcut:\n%s", sc)
	}
	if got := bubbleShortcutScript(`C:\Users\O'Neil\B`, `i`, `e`, `p`); !strings.Contains(got, `O''Neil`) {
		t.Fatalf("a ' in the path is not escaped:\n%s", got)
	}
}
